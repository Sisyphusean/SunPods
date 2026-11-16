"""SunPods product renders: an unbranded WF-1000XM5-style bud and case, modelled procedurally.

Run: blender -b -P buds.py -- <out_dir> [shot ...]
Shots: pair, left, right, case. Transparent PNGs, studio strip lighting, warm rim to match SunPods amber.
Env: SUNPODS_FAST=1 for quick previews.
"""
import bpy, math, sys, os
from mathutils import Vector, Euler

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = argv[0] if argv else os.path.dirname(os.path.abspath(__file__))
SHOTS = argv[1:] or ["pair", "left", "right", "case"]
FAST = os.environ.get("SUNPODS_FAST") == "1"
SCALE = 0.5 if FAST else 1.0


# ---------------------------------------------------------------- scene

def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    s = bpy.context.scene
    s.render.engine = "CYCLES"
    s.cycles.samples = 64 if FAST else 256
    s.cycles.use_denoising = True
    s.cycles.max_bounces = 12
    s.cycles.use_auto_tile = False
    try:
        prefs = bpy.context.preferences.addons["cycles"].preferences
        prefs.compute_device_type = "OPTIX"
        prefs.get_devices()
        for d in prefs.devices:
            d.use = True
        s.cycles.device = "GPU"
        s.cycles.denoiser = "OPTIX"
    except Exception:
        s.cycles.device = "CPU"
    s.render.film_transparent = True
    s.render.image_settings.file_format = "PNG"
    s.render.image_settings.color_mode = "RGBA"
    s.view_settings.view_transform = "Filmic"
    s.view_settings.look = "High Contrast"
    world = bpy.data.worlds.new("w")
    world.use_nodes = True
    bg = world.node_tree.nodes["Background"]
    bg.inputs[0].default_value = (0.018, 0.018, 0.022, 1)
    bg.inputs[1].default_value = 1.0
    s.world = world
    return s


def node(nt, kind, op=None, **inputs):
    n = nt.nodes.new(kind)
    if op:
        n.operation = op
    for k, v in inputs.items():
        n.inputs[k].default_value = v
    return n


def math_node(nt, op, a=None, b=None):
    n = nt.nodes.new("ShaderNodeMath")
    n.operation = op
    for i, v in enumerate((a, b)):
        if v is None:
            continue
        if isinstance(v, (int, float)):
            n.inputs[i].default_value = v
        else:
            nt.links.new(v, n.inputs[i])
    return n.outputs[0]


def mix(nt, fac, c1, c2):
    n = nt.nodes.new("ShaderNodeMixRGB")
    for sock, v in ((n.inputs[0], fac), (n.inputs[1], c1), (n.inputs[2], c2)):
        if isinstance(v, tuple):
            sock.default_value = v
        else:
            nt.links.new(v, sock)
    return n.outputs[0]


def principled(name, color, rough, metal=0.0, coat=0.0, spec=0.5):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*color, 1)
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    b.inputs["Specular"].default_value = spec
    b.inputs["Clearcoat"].default_value = coat
    b.inputs["Clearcoat Roughness"].default_value = 0.03
    return m, m.node_tree, b


def object_coords(nt):
    return nt.nodes.new("ShaderNodeTexCoord").outputs["Object"]


def micro_surface(nt, bsdf, base_rough, spread, bump_strength, scale=380.0):
    """Fine noise on roughness and normal so the surface reads as moulded, not CG-perfect."""
    noise = node(nt, "ShaderNodeTexNoise", Scale=scale, Detail=6.0, Roughness=0.6)
    nt.links.new(object_coords(nt), noise.inputs["Vector"])
    mr = node(nt, "ShaderNodeMapRange")
    mr.inputs["To Min"].default_value = base_rough - spread
    mr.inputs["To Max"].default_value = base_rough + spread
    nt.links.new(noise.outputs["Fac"], mr.inputs["Value"])
    nt.links.new(mr.outputs["Result"], bsdf.inputs["Roughness"])
    bump = node(nt, "ShaderNodeBump", Strength=bump_strength, Distance=0.002)
    nt.links.new(noise.outputs["Fac"], bump.inputs["Height"])
    nt.links.new(bump.outputs["Normal"], bsdf.inputs["Normal"])


def shell_material(seam_z):
    """Glossy face, satin rear body, and a fine recessed parting line between them."""
    m, nt, b = principled("shell", (0.010, 0.010, 0.012), 0.1, coat=1.0, spec=0.6)
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(object_coords(nt), sep.inputs[0])
    z = sep.outputs["Z"]
    face = math_node(nt, "GREATER_THAN", z, seam_z)
    noise = node(nt, "ShaderNodeTexNoise", Scale=420.0, Detail=6.0)
    nt.links.new(object_coords(nt), noise.inputs["Vector"])
    nfac = noise.outputs["Fac"]
    rough_face = math_node(nt, "ADD", math_node(nt, "MULTIPLY", nfac, 0.06), 0.05)
    rough_body = math_node(nt, "ADD", math_node(nt, "MULTIPLY", nfac, 0.1), 0.25)
    rough = mix(nt, face, rough_body, rough_face)
    band = math_node(nt, "LESS_THAN", math_node(nt, "ABSOLUTE", math_node(nt, "SUBTRACT", z, seam_z)), 0.02)
    nt.links.new(mix(nt, band, rough, (0.85, 0.85, 0.85, 1)), b.inputs["Roughness"])
    nt.links.new(face, b.inputs["Clearcoat"])
    nt.links.new(mix(nt, band, (0.010, 0.010, 0.012, 1), (0.0, 0.0, 0.0, 1)), b.inputs["Base Color"])
    height = math_node(nt, "ADD", math_node(nt, "MULTIPLY", band, -1.0), math_node(nt, "MULTIPLY", nfac, 0.02))
    bump = node(nt, "ShaderNodeBump", Strength=0.35, Distance=0.01)
    nt.links.new(height, bump.inputs["Height"])
    nt.links.new(bump.outputs["Normal"], b.inputs["Normal"])
    nt.links.new(bump.outputs["Normal"], b.inputs["Clearcoat Normal"])
    return m


def grille_material(name, color, hole_scale, hole_size, metal=1.0, rough=0.3):
    """Perforated metal: a regular Voronoi dot field cut into the surface."""
    m, nt, b = principled(name, color, rough, metal=metal)
    vor = node(nt, "ShaderNodeTexVoronoi", Scale=hole_scale, Randomness=0.0)
    nt.links.new(object_coords(nt), vor.inputs["Vector"])
    hole = math_node(nt, "LESS_THAN", vor.outputs["Distance"], hole_size)
    nt.links.new(mix(nt, hole, (*color, 1), (0.01, 0.008, 0.007, 1)), b.inputs["Base Color"])
    nt.links.new(math_node(nt, "SUBTRACT", metal, hole), b.inputs["Metallic"])
    bump = node(nt, "ShaderNodeBump", Strength=0.8, Distance=0.004)
    nt.links.new(math_node(nt, "SUBTRACT", 1.0, hole), bump.inputs["Height"])
    nt.links.new(bump.outputs["Normal"], b.inputs["Normal"])
    return m


def silicone_material():
    m, nt, b = principled("silicone", (0.022, 0.022, 0.025), 0.55, spec=0.3)
    b.inputs["Subsurface"].default_value = 0.0
    b.inputs["Subsurface Color"].default_value = (0.08, 0.07, 0.07, 1)
    b.inputs["Sheen"].default_value = 0.15
    micro_surface(nt, b, 0.55, 0.06, 0.04, scale=220.0)
    return m


def shade_smooth(obj):
    for p in obj.data.polygons:
        p.use_smooth = True


def place_on(target, direction):
    d = Vector(direction).normalized()
    hit, loc, normal, _ = target.ray_cast(d * 6, -d)
    return (loc, normal) if hit else (d, d)


def orient(obj, normal):
    obj.rotation_mode = "QUATERNION"
    obj.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(normal)


def disc(name, loc, normal, radius, depth, material, bevel=0.004, verts=64):
    bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=radius, depth=depth, location=loc)
    o = bpy.context.active_object
    o.name = name
    orient(o, normal)
    b = o.modifiers.new("bevel", "BEVEL")
    b.width, b.segments = bevel, 3
    shade_smooth(o)
    o.data.materials.append(material)
    return o


# ---------------------------------------------------------------- bud
# Proportions follow WF-1000XM5 reference photos: a near-spherical body, glossy outer half,
# matte inner half split by a seam, short angled nozzle with a flat foam tip.

SEAM_Z = 0.06


def body_material():
    """Glossy outer half (+Z), satin inner half, recessed parting line."""
    return shell_material(SEAM_Z)


def foam_material():
    m, nt, b = principled("foam", (0.018, 0.018, 0.02), 0.85, spec=0.2)
    noise = node(nt, "ShaderNodeTexNoise", Scale=900.0, Detail=8.0, Roughness=0.7)
    nt.links.new(object_coords(nt), noise.inputs["Vector"])
    bump = node(nt, "ShaderNodeBump", Strength=0.6, Distance=0.004)
    nt.links.new(noise.outputs["Fac"], bump.inputs["Height"])
    nt.links.new(bump.outputs["Normal"], b.inputs["Normal"])
    return m


def text_on(target, direction, txt, size, material):
    loc, n = place_on(target, direction)
    bpy.ops.object.text_add(location=loc + n * 0.004)
    t = bpy.context.active_object
    t.data.body = txt
    t.data.size = size
    t.data.align_x, t.data.align_y = "CENTER", "CENTER"
    t.data.extrude = 0.003
    t.data.font = bpy.data.fonts.load("C:/Windows/Fonts/arialbd.ttf")
    orient(t, n)
    t.data.materials.append(material)
    bpy.ops.object.convert(target="MESH")
    return bpy.context.active_object


def make_bud(mirror=False):
    sx = -1.0 if mirror else 1.0
    # body: slightly squashed sphere
    bpy.ops.mesh.primitive_uv_sphere_add(segments=96, ring_count=48, radius=1.0)
    body = bpy.context.active_object
    body.scale = (1.0, 0.97, 0.9)
    bpy.ops.object.transform_apply(scale=True)
    # nozzle neck, angled down and back out of the inner half
    axis = Vector((0.12 * sx, -0.38, -1.0)).normalized()
    bpy.ops.mesh.primitive_cylinder_add(vertices=64, radius=0.27, depth=0.7, location=axis * 0.88)
    neck = bpy.context.active_object
    orient(neck, axis)
    bpy.ops.object.select_all(action="DESELECT")
    body.select_set(True)
    neck.select_set(True)
    bpy.context.view_layer.objects.active = body
    bpy.ops.object.join()
    body = bpy.context.active_object
    rm = body.modifiers.new("remesh", "REMESH")
    rm.mode, rm.voxel_size = "VOXEL", 0.014
    bpy.ops.object.modifier_apply(modifier="remesh")
    sm = body.modifiers.new("smooth", "CORRECTIVE_SMOOTH")
    sm.factor, sm.iterations, sm.smooth_type = 0.9, 18, "LENGTH_WEIGHTED"
    bpy.ops.object.modifier_apply(modifier="smooth")
    lap = body.modifiers.new("lap", "LAPLACIANSMOOTH")
    lap.lambda_factor, lap.iterations = 0.6, 6
    bpy.ops.object.modifier_apply(modifier="lap")
    shade_smooth(body)
    body.data.materials.append(body_material())
    bpy.context.view_layer.update()

    copper = grille_material("copper_grille", (0.86, 0.58, 0.42), 38.0, 0.27)
    copper_plain, _, _ = principled("copper", (0.86, 0.58, 0.42), 0.26, metal=1.0)
    gold, _, _ = principled("gold", (1.0, 0.78, 0.40), 0.16, metal=1.0)
    sensor, _, _ = principled("sensor", (0.005, 0.005, 0.006), 0.03, coat=1.0, spec=0.9)
    red, _, _ = principled("marker_red", (0.75, 0.03, 0.03), 0.35, spec=0.4)
    white, _, _ = principled("marker_white", (0.85, 0.85, 0.85), 0.4)
    grey_print, _, _ = principled("marker_grey", (0.12, 0.12, 0.13), 0.5)
    mesh_dark = grille_material("nozzle_mesh", (0.05, 0.05, 0.055), 140.0, 0.3, metal=0.5, rough=0.5)

    # Outer face: copper mic grille near the top, with a fine polished rim
    gdir = (0.05 * sx, 0.5, 1.0)
    loc, n = place_on(body, gdir)
    disc("grille", loc - n * 0.004, n, 0.15, 0.02, copper)
    bpy.ops.mesh.primitive_torus_add(major_radius=0.155, minor_radius=0.01, location=loc)
    rim = bpy.context.active_object
    orient(rim, n)
    shade_smooth(rim)
    rim.data.materials.append(copper_plain)

    # Inner half: gold charging contacts, sensor windows, side marker
    for d in [(0.42 * sx, 0.2, -0.75), (0.25 * sx, 0.42, -0.72)]:
        loc, n = place_on(body, d)
        disc("contact", loc - n * 0.006, n, 0.055, 0.014, gold, bevel=0.001)
    for d, r in [((-0.35 * sx, 0.35, -0.7), 0.06), ((-0.05 * sx, 0.62, -0.6), 0.045)]:
        loc, n = place_on(body, d)
        disc("sensor", loc - n * 0.006, n, r, 0.014, sensor, bevel=0.001)
    mdir = (0.72 * sx, -0.15, -0.45)
    if mirror:
        loc, n = place_on(body, mdir)
        disc("r_marker", loc - n * 0.003, n, 0.09, 0.015, red)
        text_on(body, mdir, "R", 0.11, white)
    else:
        text_on(body, mdir, "L", 0.11, grey_print)

    # Foam tip: flat-topped cylinder with softly rounded edges and an open bore over a mesh
    nozzle_end = axis * 1.12
    bpy.ops.mesh.primitive_cylinder_add(vertices=96, radius=0.36, depth=0.4, location=nozzle_end)
    tip = bpy.context.active_object
    orient(tip, axis)
    bev = tip.modifiers.new("bevel", "BEVEL")
    bev.width, bev.segments = 0.11, 8
    tip.modifiers.new("sub", "SUBSURF").levels = 1
    shade_smooth(tip)
    tip.data.materials.append(foam_material())
    bpy.ops.mesh.primitive_cylinder_add(vertices=64, radius=0.16, depth=0.08, location=nozzle_end + axis * 0.185)
    bore = bpy.context.active_object
    orient(bore, axis)
    shade_smooth(bore)
    bore.data.materials.append(mesh_dark)

    bpy.ops.object.select_all(action="DESELECT")
    for o in bpy.context.collection.objects:
        if o.type == "MESH" and not o.get("done"):
            o.select_set(True)
    bpy.context.view_layer.objects.active = body
    bpy.ops.object.join()
    bud = bpy.context.active_object
    bud["done"] = True
    return bud


# ---------------------------------------------------------------- case
# Rounded box with a flat base and domed lid, lid line about two thirds up, LED below it.

def make_case():
    bpy.ops.mesh.primitive_cube_add(size=2.0)
    case = bpy.context.active_object
    case.scale = (1.5, 1.0, 0.75)
    bpy.ops.object.transform_apply(scale=True)
    bev = case.modifiers.new("bevel", "BEVEL")
    bev.width, bev.segments, bev.limit_method = 0.72, 20, "NONE"
    bpy.ops.object.modifier_apply(modifier="bevel")
    sub = case.modifiers.new("sub", "SUBSURF")
    sub.levels = 2
    bpy.ops.object.modifier_apply(modifier="sub")
    # flatten the base
    for v in case.data.vertices:
        if v.co.z < -0.6:
            v.co.z = -0.6 + (v.co.z + 0.6) * 0.25
    shade_smooth(case)
    m, nt, b = principled("case", (0.016, 0.016, 0.018), 0.62, coat=0.0, spec=0.35)
    micro_surface(nt, b, 0.62, 0.05, 0.1, scale=500.0)
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(object_coords(nt), sep.inputs[0])
    band = math_node(nt, "LESS_THAN", math_node(nt, "ABSOLUTE", math_node(nt, "SUBTRACT", sep.outputs["Z"], 0.22)), 0.01)
    nt.links.new(mix(nt, band, (0.02, 0.02, 0.022, 1), (0.0, 0.0, 0.0, 1)), b.inputs["Base Color"])
    case.data.materials.append(m)
    bpy.context.view_layer.update()
    loc, n = place_on(case, (0, -1, -0.05))
    bpy.ops.mesh.primitive_uv_sphere_add(radius=0.03, location=loc)
    led = bpy.context.active_object
    lm, _, lb = principled("led", (1, 0.75, 0.4), 0.3)
    lb.inputs["Emission"].default_value = (1, 0.7, 0.35, 1)
    lb.inputs["Emission Strength"].default_value = 14
    led.data.materials.append(lm)
    return case


# ---------------------------------------------------------------- light and camera

def lights():
    def area(name, loc, energy, color, size, size_y=None, target=(0, 0, 0)):
        l = bpy.data.lights.new(name, "AREA")
        l.energy, l.color = energy, color
        if size_y:
            l.shape = "RECTANGLE"
            l.size, l.size_y = size, size_y
        else:
            l.size = size
        o = bpy.data.objects.new(name, l)
        o.location = loc
        o.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()
        bpy.context.collection.objects.link(o)
    area("strip_left", (-6, -1, 3), 1600, (1.0, 0.95, 0.9), 0.5, 8)
    area("strip_right", (6, -2, 2), 1100, (1.0, 0.8, 0.6), 0.5, 8)
    area("key", (-2, -7, 6), 1300, (1.0, 0.88, 0.72), 5)
    area("rim", (1, 7, 4), 2200, (1.0, 0.62, 0.30), 3)
    area("kick", (0, -2, -6), 250, (0.7, 0.78, 1.0), 6)


def camera(target, distance, elev, azim, lens=70, focus=None, fstop=5.6):
    cam = bpy.data.cameras.new("cam")
    cam.lens = lens
    o = bpy.data.objects.new("cam", cam)
    t = Vector(target)
    o.location = t + Vector((
        distance * math.cos(math.radians(elev)) * math.sin(math.radians(azim)),
        -distance * math.cos(math.radians(elev)) * math.cos(math.radians(azim)),
        distance * math.sin(math.radians(elev)),
    ))
    o.rotation_euler = (t - o.location).to_track_quat("-Z", "Y").to_euler()
    bpy.context.collection.objects.link(o)
    if focus is not None:
        cam.dof.use_dof = True
        cam.dof.focus_object = focus
        cam.dof.aperture_fstop = fstop
    bpy.context.scene.camera = o


def render(scene, name, w, h):
    scene.render.resolution_x, scene.render.resolution_y = int(w * SCALE), int(h * SCALE)
    scene.render.filepath = os.path.join(OUT, name + ".png")
    bpy.ops.render.render(write_still=True)


for shot in SHOTS:
    s = reset()
    lights()
    if shot == "pair":
        a = make_bud()
        a.location = (-1.15, 0.5, 0.35)
        a.rotation_euler = Euler((math.radians(70), math.radians(0), math.radians(-20)))
        b = make_bud(mirror=True)
        b.location = (1.2, -0.3, -0.35)
        b.rotation_euler = Euler((math.radians(-115), math.radians(0), math.radians(25)))
        camera((0.0, 0.1, 0.0), 8.5, 12, 0, lens=60, focus=a, fstop=9.0)
        render(s, "buds_pair", 2000, 1500)
    elif shot in ("left", "right"):
        bud = make_bud(mirror=(shot == "right"))
        bud.rotation_euler = Euler((math.radians(70), 0, math.radians(-20 if shot == "left" else 20)))
        camera((0, 0, -0.1), 7, 12, 0, lens=70)
        render(s, f"bud_{shot}", 900, 900)
    elif shot == "inner":
        bud = make_bud(mirror=True)
        bud.rotation_euler = Euler((math.radians(-115), 0, math.radians(25)))
        camera((0, 0, -0.1), 7, 12, 0, lens=70)
        render(s, "bud_inner", 900, 900)
    elif shot == "case":
        make_case()
        camera((0, 0, -0.05), 10, 14, 12, lens=70)
        render(s, "case", 1200, 900)
print("SUNPODS_RENDER_DONE")
