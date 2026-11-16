"""SunPods product renders: an unbranded WF-1000XM5-style bud and case, modelled procedurally.

Run: blender -b -P buds.py -- <out_dir> [shot ...]
Shots: pair, left, right, case. Transparent PNGs, lit warm to match the SunPods amber.
"""
import bpy, math, sys, os
from mathutils import Vector, Euler

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = argv[0] if argv else os.path.dirname(os.path.abspath(__file__))
SHOTS = argv[1:] or ["pair", "left", "right", "case"]
FAST = os.environ.get("SUNPODS_FAST") == "1"


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    s = bpy.context.scene
    s.render.engine = "CYCLES"
    s.cycles.samples = 48 if FAST else 160
    s.cycles.use_denoising = True
    try:
        prefs = bpy.context.preferences.addons["cycles"].preferences
        prefs.compute_device_type = "CUDA"
        prefs.get_devices()
        for d in prefs.devices:
            d.use = True
        s.cycles.device = "GPU"
    except Exception:
        s.cycles.device = "CPU"
    s.render.film_transparent = True
    s.render.image_settings.file_format = "PNG"
    s.render.image_settings.color_mode = "RGBA"
    s.view_settings.view_transform = "Filmic"
    s.view_settings.look = "Medium High Contrast"
    world = bpy.data.worlds.new("w")
    world.use_nodes = True
    bg = world.node_tree.nodes["Background"]
    bg.inputs[0].default_value = (0.02, 0.02, 0.025, 1)
    bg.inputs[1].default_value = 0.6
    s.world = world
    return s


def mat(name, color, rough, metal=0.0, coat=0.0, spec=0.5):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*color, 1)
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    b.inputs["Specular"].default_value = spec
    b.inputs["Clearcoat"].default_value = coat
    b.inputs["Clearcoat Roughness"].default_value = 0.05
    return m


def shade_smooth(obj):
    for p in obj.data.polygons:
        p.use_smooth = True


def make_bud(mirror=False):
    """Rounded-triangle pebble facing +Z, nozzle and tip angled back and down, copper on the face."""
    sx = -1.0 if mirror else 1.0
    mb = bpy.data.metaballs.new("bud")
    mb.resolution = 0.03
    mb.render_resolution = 0.018
    mb.threshold = 0.6
    for (x, y, z), r, kind, size in [
        ((0.0, 0.05, 0.0), 1.15, "ELLIPSOID", (1.0, 1.0, 0.78)),    # round, full face
        ((0.08 * sx, -0.1, -0.45), 0.9, "BALL", None),               # rear lobe
        ((0.15 * sx, -0.3, -0.85), 0.55, "BALL", None),              # short neck
    ]:
        e = mb.elements.new()
        e.co = (x, y, z)
        e.radius = r
        e.type = kind
        e.stiffness = 1.2
        if size:
            e.size_x, e.size_y, e.size_z = size
    body_obj = bpy.data.objects.new("bud_body", mb)
    bpy.context.collection.objects.link(body_obj)
    bpy.context.view_layer.objects.active = body_obj
    body_obj.select_set(True)
    bpy.ops.object.convert(target="MESH")
    body = bpy.context.active_object
    sm = body.modifiers.new("smooth", "SMOOTH")
    sm.factor, sm.iterations = 0.8, 25
    bpy.ops.object.modifier_apply(modifier="smooth")
    shade_smooth(body)
    body.data.materials.append(mat("gloss", (0.012, 0.012, 0.014), 0.1, coat=1.0, spec=0.6))
    bpy.context.view_layer.update()

    def surface(direction):
        d = Vector(direction).normalized()
        hit, loc, normal, _ = body.ray_cast(d * 5, -d)
        return (loc, normal) if hit else (d, d)

    axis = Vector((0.18 * sx, -0.3, -1.0)).normalized()
    nozzle, _ = surface(axis)
    bpy.ops.mesh.primitive_cylinder_add(vertices=64, radius=0.4, depth=0.5, location=nozzle + axis * 0.12)
    tip = bpy.context.active_object
    tip.rotation_mode = "QUATERNION"
    tip.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(axis)
    bev = tip.modifiers.new("bevel", "BEVEL")
    bev.width = 0.2
    bev.segments = 8
    tip.modifiers.new("sub", "SUBSURF").levels = 2
    shade_smooth(tip)
    tip.data.materials.append(mat("silicone", (0.03, 0.03, 0.034), 0.55, spec=0.3))

    for direction, r in [((0.0, -0.6, 1.0), 0.16), ((0.38 * sx, 0.5, 1.0), 0.045)]:
        loc, normal = surface(direction)
        bpy.ops.mesh.primitive_cylinder_add(vertices=48, radius=r, depth=0.03, location=loc - normal * 0.006)
        dot = bpy.context.active_object
        dot.rotation_mode = "QUATERNION"
        dot.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(normal)
        dot.modifiers.new("bevel", "BEVEL").width = 0.006
        shade_smooth(dot)
        dot.data.materials.append(mat("copper", (0.80, 0.50, 0.33), 0.35, metal=1.0))

    bpy.ops.object.select_all(action="DESELECT")
    parts = [o for o in bpy.context.collection.objects if o.type == "MESH" and not o.get("done")]
    for o in parts:
        o.select_set(True)
    bpy.context.view_layer.objects.active = body
    bpy.ops.object.join()
    bud = bpy.context.active_object
    bud["done"] = True
    return bud


def make_case():
    """Smooth pill case. The lid seam is painted in the shader so it always sits on the surface."""
    bpy.ops.mesh.primitive_uv_sphere_add(segments=96, ring_count=48, radius=1.0)
    case = bpy.context.active_object
    case.scale = (2.3, 1.35, 1.12)
    bpy.ops.object.transform_apply(scale=True)
    shade_smooth(case)
    m = mat("matte", (0.02, 0.02, 0.022), 0.42, coat=0.25, spec=0.45)
    nt = m.node_tree
    bsdf = nt.nodes["Principled BSDF"]
    tex = nt.nodes.new("ShaderNodeTexCoord")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    dist = nt.nodes.new("ShaderNodeMath"); dist.operation = "SUBTRACT"; dist.inputs[1].default_value = -0.12
    absn = nt.nodes.new("ShaderNodeMath"); absn.operation = "ABSOLUTE"
    band = nt.nodes.new("ShaderNodeMath"); band.operation = "LESS_THAN"; band.inputs[1].default_value = 0.012
    mix = nt.nodes.new("ShaderNodeMixRGB")
    mix.inputs[1].default_value = (0.02, 0.02, 0.022, 1)
    mix.inputs[2].default_value = (0.0, 0.0, 0.0, 1)
    nt.links.new(tex.outputs["Object"], sep.inputs[0])
    nt.links.new(sep.outputs["Z"], dist.inputs[0])
    nt.links.new(dist.outputs[0], absn.inputs[0])
    nt.links.new(absn.outputs[0], band.inputs[0])
    nt.links.new(band.outputs[0], mix.inputs[0])
    nt.links.new(mix.outputs[0], bsdf.inputs["Base Color"])
    case.data.materials.append(m)
    bpy.context.view_layer.update()
    d = Vector((0, -1, -0.35)).normalized()
    hit, loc, normal, _ = case.ray_cast(d * 6, -d)
    bpy.ops.mesh.primitive_uv_sphere_add(radius=0.04, location=loc)
    led = bpy.context.active_object
    lm = mat("led", (1, 0.75, 0.4), 0.3)
    lm.node_tree.nodes["Principled BSDF"].inputs["Emission"].default_value = (1, 0.7, 0.35, 1)
    lm.node_tree.nodes["Principled BSDF"].inputs["Emission Strength"].default_value = 12
    led.data.materials.append(lm)
    return case


def lights():
    def area(name, loc, energy, color, size, target=(0, 0, 0)):
        l = bpy.data.lights.new(name, "AREA")
        l.energy, l.color, l.size = energy, color, size
        o = bpy.data.objects.new(name, l)
        o.location = loc
        d = Vector(target) - Vector(loc)
        o.rotation_euler = d.to_track_quat("-Z", "Y").to_euler()
        bpy.context.collection.objects.link(o)
    area("key", (-4, -3, 5), 1700, (1.0, 0.86, 0.68), 4)       # warm key, the "sun"
    area("rim", (4, 4, 2), 1100, (1.0, 0.62, 0.30), 2)          # amber rim
    area("fill", (5, -5, -1), 120, (0.7, 0.78, 1.0), 6)        # cool fill keeps blacks readable
    area("top", (0, 0, 7), 600, (1, 1, 1), 8)                  # long highlight across the gloss


def camera(target, distance, elev, azim, lens=70):
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
    bpy.context.scene.camera = o


def render(scene, name, w, h):
    scene.render.resolution_x, scene.render.resolution_y = w, h
    scene.render.filepath = os.path.join(OUT, name + ".png")
    bpy.ops.render.render(write_still=True)


for shot in SHOTS:
    s = reset()
    lights()
    if shot == "pair":
        a = make_bud()
        a.location = (-1.3, 0.9, 0.6)
        a.rotation_euler = Euler((math.radians(75), math.radians(-10), math.radians(-15)))
        b = make_bud(mirror=True)
        b.location = (1.25, -0.6, -0.4)
        b.rotation_euler = Euler((math.radians(85), math.radians(15), math.radians(40)))
        camera((0.0, 0.1, 0.05), 11, 10, 0, lens=60)
        render(s, "buds_pair", 1200, 900)
    elif shot in ("left", "right"):
        bud = make_bud(mirror=(shot == "right"))
        bud.rotation_euler = Euler((math.radians(80), 0, math.radians(-25 if shot == "left" else 25)))
        camera((0, 0, -0.2), 8, 15, 0, lens=70)
        render(s, f"bud_{shot}", 600, 600)
    elif shot == "case":
        make_case()
        camera((0, 0, -0.05), 12, 14, 12, lens=70)
        render(s, "case", 800, 600)
print("SUNPODS_RENDER_DONE")
