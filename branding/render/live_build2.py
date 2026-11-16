# Runs inside the live Blender via the MCP socket.
# Assembles one XM5-style bud from the SDF shell: matte face panel, glossy body, copper grille,
# foam tip with bore. Renders it from a camera given in CAM (azimuth, elevation, roll, distance)
# so it can be lined up against a reference photo. Params come in via the PARAMS dict.
import bpy, math, os
from mathutils import Vector, Euler, Matrix

P = globals().get("PARAMS", {})
HERE = r"C:\Users\peyio\Documents\SonyPodsBattery\branding\render"
SIDE = P.get("side", "right")
SX = 1.0 if SIDE == "left" else -1.0
OUT = P.get("out", "live.png")
RES = P.get("res", 560)
SAMPLES = P.get("samples", 48)
BG = P.get("bg", (0.10, 0.14, 0.16, 1))

for o in list(bpy.data.objects):
    bpy.data.objects.remove(o, do_unlink=True)
for coll in (bpy.data.meshes, bpy.data.materials, bpy.data.lights, bpy.data.cameras, bpy.data.curves):
    for d in list(coll):
        if d.users == 0:
            coll.remove(d)

s = bpy.context.scene
s.render.engine = "CYCLES"
s.cycles.samples = SAMPLES
s.cycles.device = "GPU"
s.cycles.use_denoising = True
s.render.film_transparent = bool(P.get("transparent", False))
s.render.resolution_x = s.render.resolution_y = RES
s.view_settings.view_transform = "Filmic"
s.view_settings.look = "Medium High Contrast"
w = bpy.data.worlds.get("w") or bpy.data.worlds.new("w")
w.use_nodes = True
w.node_tree.nodes["Background"].inputs[0].default_value = BG
w.node_tree.nodes["Background"].inputs[1].default_value = 1.0
s.world = w


def mat(name, color, rough, metal=0.0, coat=0.0, spec=0.5):
    m = bpy.data.materials.new(name); m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*color, 1)
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    b.inputs["Clearcoat"].default_value = coat
    b.inputs["Clearcoat Roughness"].default_value = 0.02
    b.inputs["Specular"].default_value = spec
    return m, m.node_tree, b


def grille(name, color):
    m, nt, b = mat(name, color, 0.42, metal=1.0)
    tc = nt.nodes.new("ShaderNodeTexCoord")
    vor = nt.nodes.new("ShaderNodeTexVoronoi")
    vor.inputs["Scale"].default_value = 34.0
    vor.inputs["Randomness"].default_value = 0.0
    nt.links.new(tc.outputs["Object"], vor.inputs["Vector"])
    lt = nt.nodes.new("ShaderNodeMath"); lt.operation = "LESS_THAN"; lt.inputs[1].default_value = 0.3
    nt.links.new(vor.outputs["Distance"], lt.inputs[0])
    mix = nt.nodes.new("ShaderNodeMixRGB")
    nt.links.new(lt.outputs[0], mix.inputs[0])
    mix.inputs[1].default_value = (*color, 1)
    mix.inputs[2].default_value = (0.02, 0.012, 0.01, 1)
    nt.links.new(mix.outputs[0], b.inputs["Base Color"])
    inv = nt.nodes.new("ShaderNodeMath"); inv.operation = "SUBTRACT"; inv.inputs[0].default_value = 1.0
    nt.links.new(lt.outputs[0], inv.inputs[1])
    bump = nt.nodes.new("ShaderNodeBump"); bump.inputs["Strength"].default_value = 0.7
    bump.inputs["Distance"].default_value = 0.004
    nt.links.new(inv.outputs[0], bump.inputs["Height"])
    nt.links.new(bump.outputs["Normal"], b.inputs["Normal"])
    return m


bpy.ops.import_scene.obj(filepath=os.path.join(HERE, f"xm5_shell_{SIDE}.obj"), axis_forward="Y", axis_up="Z")
bud = bpy.context.selected_objects[0]
bud.name = "bud"
bud.rotation_euler = (0, 0, 0)
import bmesh as _bm
_b = _bm.new(); _b.from_mesh(bud.data)
_b.normal_update()
_top = max(_b.faces, key=lambda f: f.calc_center_median().z)
if _top.normal.z < 0:   # marching-cubes winding came in inside-out
    _bm.ops.reverse_faces(_b, faces=_b.faces)
_b.to_mesh(bud.data); _b.free(); bud.data.update()
bpy.context.view_layer.objects.active = bud
cs = bud.modifiers.new("cs", "CORRECTIVE_SMOOTH"); cs.factor, cs.iterations = 0.8, 10
bpy.ops.object.modifier_apply(modifier="cs")
for p in bud.data.polygons:
    p.use_smooth = True

gloss, _, _ = mat("gloss", (0.012, 0.013, 0.015), 0.07, coat=1.0, spec=0.6)
panel, _, _ = mat("panel", (0.018, 0.022, 0.024), 0.5, coat=0.0, spec=0.45)
bud.data.materials.clear()
bud.data.materials.append(gloss)

# Face panel: separate thin matte plate following the domed face, crisp teardrop edge
R1, R2, H, TOP_Y = 1.0, 0.34, 1.3, 0.5
def teardrop(x, y):
    px, py = abs(x), TOP_Y - y
    b = (R1 - R2) / H; a = math.sqrt(1 - b * b)
    k = -b * px + a * py
    if k < 0: return math.hypot(px, py) - R1
    if k > a * H: return math.hypot(px, py - H) - R2
    return px * a + py * b - R1
bud.data.update(); bpy.context.view_layer.update()
import bmesh
bmp = bmesh.new()
N = 140
grid = {}
for i in range(N + 1):
    for j in range(N + 1):
        x = -1.05 + 2.1 * i / N; y = -1.3 + 2.9 * j / N
        if teardrop(x, y) < 0:
            hit, loc, nrm, _ = bud.ray_cast(Vector((x, y, 3)), Vector((0, 0, -1)))
            if hit: grid[i, j] = bmp.verts.new(loc + nrm * 0.004)
for (i, j), v in list(grid.items()):
    q = [grid.get(k) for k in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1))]
    if all(q): bmp.faces.new(q)
# snap the ragged boundary onto the exact outline
bmp.verts.ensure_lookup_table()
for v in bmp.verts:
    if v.is_boundary:
        x, y = v.co.x, v.co.y
        for _ in range(6):
            d = teardrop(x, y); e = 1e-3
            gx = (teardrop(x + e, y) - d) / e; gy = (teardrop(x, y + e) - d) / e
            x -= d * gx; y -= d * gy
        hit, loc, nrm, _ = bud.ray_cast(Vector((x, y, 3)), Vector((0, 0, -1)))
        if hit: v.co = loc + nrm * 0.004
me = bpy.data.meshes.new("panel"); bmp.to_mesh(me); bmp.free()
pn = bpy.data.objects.new("panel", me); s.collection.objects.link(pn)
for p in me.polygons: p.use_smooth = True
me.materials.append(panel)
sol = pn.modifiers.new("t", "SOLIDIFY"); sol.thickness = 0.01; sol.offset = 1
bv = pn.modifiers.new("b", "BEVEL"); bv.width = 0.004; bv.segments = 2

def surf(origin, direction):
    hit, loc, normal, _ = bud.ray_cast(Vector(origin), Vector(direction).normalized())
    return loc, normal

def disc(name, loc, normal, r, depth, material):
    bpy.ops.mesh.primitive_cylinder_add(vertices=96, radius=r, depth=depth, location=loc)
    o = bpy.context.active_object; o.name = name
    o.rotation_mode = "QUATERNION"; o.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(normal)
    bv = o.modifiers.new("b", "BEVEL"); bv.width, bv.segments = min(0.01, depth / 3), 3
    for p in o.data.polygons: p.use_smooth = True
    o.data.materials.append(material)
    return o

# Copper mic grille at the narrow bottom of the face panel, with a thin polished ring
copper = grille("copper", (0.62, 0.38, 0.28))
ring_m, _, _ = mat("ring", (0.62, 0.38, 0.28), 0.3, metal=1.0)
gy = TOP_Y - H + P.get("grille_dy", 0.0)
TOP = 0.012
loc, n = surf((0, gy, 3), (0, 0, -1))
disc("grille", loc + n * 0.02, n, P.get("grille_r", 0.3), 0.012, copper)
bpy.ops.mesh.primitive_torus_add(major_radius=P.get("grille_r", 0.3) + 0.005, minor_radius=0.012, location=loc + n * 0.024)
rg = bpy.context.active_object; rg.rotation_mode = "QUATERNION"
rg.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(n); rg.data.materials.append(ring_m)
for p in rg.data.polygons: p.use_smooth = True

# Foam tip on the nozzle: flat-topped cylinder, soft edges, dark open bore
a = Vector((0.1 * SX, -0.45, -0.9))
axis = Vector((0.7 * SX, -0.45, -0.6)).normalized()
end = a + Vector((0.7 * SX, -0.45, -0.6)) * 0.7
tip_len, tip_r = P.get("tip_len", 0.55), P.get("tip_r", 0.47)
foam, fnt, fb = mat("foam", (0.02, 0.021, 0.024), 0.9, spec=0.25)
nz = fnt.nodes.new("ShaderNodeTexNoise"); nz.inputs["Scale"].default_value = 700; nz.inputs["Detail"].default_value = 8
bm = fnt.nodes.new("ShaderNodeBump"); bm.inputs["Strength"].default_value = 0.5; bm.inputs["Distance"].default_value = 0.003
fnt.links.new(nz.outputs["Fac"], bm.inputs["Height"]); fnt.links.new(bm.outputs["Normal"], fb.inputs["Normal"])
bpy.ops.mesh.primitive_cylinder_add(vertices=128, radius=tip_r, depth=tip_len, location=end + axis * (tip_len / 2 - 0.08))
tip = bpy.context.active_object; tip.name = "tip"
tip.rotation_mode = "QUATERNION"; tip.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(axis)
bv = tip.modifiers.new("b", "BEVEL"); bv.width, bv.segments = 0.12, 10
tip.modifiers.new("s", "SUBSURF").levels = 1
for p in tip.data.polygons: p.use_smooth = True
tip.data.materials.append(foam)
bore_m, _, _ = mat("bore", (0.005, 0.005, 0.006), 0.6)
bpy.ops.mesh.primitive_cylinder_add(vertices=64, radius=tip_r * 0.4, depth=0.03, location=end + axis * (tip_len - 0.08 + 0.002))
bo = bpy.context.active_object; bo.rotation_mode = "QUATERNION"
bo.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(axis); bo.data.materials.append(bore_m)

# Studio light: big soft key, long strip for the gloss streak, cool rim
def area(name, loc, energy, color, size, size_y=None):
    L = bpy.data.lights.new(name, "AREA"); L.energy, L.color = energy, color
    if size_y: L.shape, L.size, L.size_y = "RECTANGLE", size, size_y
    else: L.size = size
    o = bpy.data.objects.new(name, L); o.location = loc
    o.rotation_euler = (Vector((0, -0.3, -0.3)) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()
    s.collection.objects.link(o)
for spec in P.get("lights", [
    ("key", (-3, 4, 6), 1100, (1, 0.97, 0.93), 6, None),
    ("strip", (4, 2, 4), 900, (1, 1, 1), 0.6, 7),
    ("rim", (2, -5, -2), 500, (0.75, 0.85, 1.0), 4, None),
    ("fill", (-5, -3, 1), 200, (1, 1, 1), 6, None),
]):
    area(*spec)

# Camera on an orbit around the bud centre
az, el, roll, dist, lens = P.get("cam", (0, 80, 0, 9, 85))
target = Vector(P.get("target", (0, -0.25, -0.3)))
cam = bpy.data.cameras.new("cam"); cam.lens = lens
co = bpy.data.objects.new("cam", cam); s.collection.objects.link(co); s.camera = co
dirv = Vector((math.cos(math.radians(el)) * math.sin(math.radians(az)),
               -math.cos(math.radians(el)) * math.cos(math.radians(az)),
               math.sin(math.radians(el))))
co.location = target + dirv * dist
q = (target - co.location).to_track_quat("-Z", "Y")
co.rotation_euler = q.to_euler()
co.rotation_euler.rotate_axis("Z", math.radians(roll))
if P.get("render"):
    s.render.filepath = os.path.join(HERE, OUT)
    bpy.ops.render.render(write_still=True)
print("built")
