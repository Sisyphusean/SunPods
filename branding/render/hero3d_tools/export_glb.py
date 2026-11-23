# Export the approved SunPods models to glTF for the real-time Home hero (app/src/main/assets/hero3d).
# Export only: modifiers are applied to copies, curves and text become meshes, nothing is reshaped,
# re-posed or saved back. Materials are exported as named placeholders; index.html rebuilds them.
#   blender -b loop2/xm5_bud_v2.blend      --python hero3d_tools/export_glb.py -- buds <out.glb> [draco]
#   blender -b case_loop/xm5_case.blend    --python hero3d_tools/export_glb.py -- case <out.glb> [draco]
import bpy, sys, os, math, hashlib, json
from mathutils import Euler, Matrix

argv = sys.argv[sys.argv.index("--") + 1:]
KIND, OUT = argv[0], argv[1]
DRACO = "draco" in argv
S = bpy.context.scene
O = bpy.data.objects
# The app-hero pose (render_app_assets.py / render_turntable.py): L rot (8,0,26), R (-6,0,-32) mirrored.
HERO = {"L": {"loc": (1.55, 0, -0.62), "rot": (8, 0, 26)}, "R": {"loc": (-1.55, 0.3, 0.62), "rot": (-6, 0, -32)}}
if KIND == "buds":
    for nm, key in (("EMPTY_BudL", "L"), ("EMPTY_BudR", "R")):
        e, p = O[nm], HERO[key]
        e.location = p["loc"]
        e.rotation_euler = Euler([math.radians(a) for a in p["rot"]], "XYZ")
        e.scale = (-1.0 if key == "R" else 1.0, 1.0, 1.0)
# evaluate at render quality (what the approved Cycles renders used)
for o in bpy.data.objects:
    for m in getattr(o, "modifiers", []):
        if m.type == "SUBSURF": m.levels = m.render_levels
        m.show_viewport = m.show_render
bpy.context.view_layer.update()
dg = bpy.context.evaluated_depsgraph_get()

def renderable(o):
    return o.type in ("MESH", "CURVE", "FONT") and not o.hide_render

def geo_key(me):
    h = hashlib.sha1()
    h.update(("|".join(m.name if m else "-" for m in me.materials) + "#").encode())
    for v in me.vertices:
        h.update(("%.5f,%.5f,%.5f;" % tuple(v.co)).encode())
    for p in me.polygons:
        h.update((",".join(map(str, p.vertices)) + "|%d;" % p.material_index).encode())
    return (len(me.vertices), len(me.polygons), h.hexdigest())

# groups: (export root name, source root object or None, objects)
groups = []
if KIND == "buds":
    for nm in ("EMPTY_BudL", "EMPTY_BudR"):
        e = O[nm]
        groups.append((nm, e, [c for c in e.children_recursive if renderable(c)]))
else:
    lid = O["EMPTY_Lid"]
    lid_kids = [c for c in lid.children_recursive if renderable(c)]
    base = [o for o in S.objects if o.name.startswith("SM_Case") and renderable(o) and o not in lid_kids]
    groups.append(("CASE_Base", None, base))
    groups.append(("EMPTY_Lid", lid, lid_kids))
    groups.append(("EMPTY_BudL", O["EMPTY_BudL"], []))  # anchors: index.html clones the buds from buds.glb
    groups.append(("EMPTY_BudR", O["EMPTY_BudR"], []))

# rebuild every used material as a plain named Principled placeholder (in memory only)
used = set()
for _, _, objs in groups:
    for o in objs:
        for s in o.material_slots:
            if s.material: used.add(s.material)
for m in used:
    src = next((n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED"), None) if m.use_nodes else None
    col = tuple(src.inputs["Base Color"].default_value) if src else (0.5, 0.5, 0.5, 1)
    rough = src.inputs["Roughness"].default_value if src else 0.5
    metal = src.inputs["Metallic"].default_value if src else 0.0
    m.use_nodes = True
    nt = m.node_tree
    for n in list(nt.nodes): nt.nodes.remove(n)
    b = nt.nodes.new("ShaderNodeBsdfPrincipled"); out = nt.nodes.new("ShaderNodeOutputMaterial")
    b.inputs["Base Color"].default_value = col; b.inputs["Roughness"].default_value = rough; b.inputs["Metallic"].default_value = metal
    nt.links.new(b.outputs[0], out.inputs[0])

col = bpy.data.collections.new("EXPORT_HERO3D"); S.collection.children.link(col)
made, meshes, report = [], {}, []
for gname, src_root, objs in groups:
    root = bpy.data.objects.new(gname + ("_X" if gname in O else ""), None); col.objects.link(root)
    root.matrix_world = src_root.matrix_world.copy() if src_root else Matrix.Identity(4)
    made.append(root)
    inv = root.matrix_world.inverted()
    for o in objs:
        ev = o.evaluated_get(dg)
        me = bpy.data.meshes.new_from_object(ev, preserve_all_data_layers=False, depsgraph=dg)
        if not len(me.vertices):  # e.g. SM_Case_Tray: its exact boolean evaluates empty, so it never rendered
            report.append((gname, o.name, 0, 0, "EMPTY_SKIPPED", 0)); bpy.data.meshes.remove(me); continue
        k = geo_key(me)
        if k in meshes:
            bpy.data.meshes.remove(me); me = meshes[k]; shared = True
        else:
            meshes[k] = me; shared = False
            # keep the source smoothing; text/curve meshes are flat shaded already
        ob = bpy.data.objects.new(o.name + "_X", me); col.objects.link(ob)
        ob.parent = root; ob.matrix_parent_inverse = Matrix.Identity(4)
        ob.matrix_basis = inv @ o.matrix_world
        made.append(ob)
        report.append((gname, o.name, len(me.vertices), len(me.polygons), shared, round((inv @ o.matrix_world).determinant(), 4)))

# rename to the source names (sources are renamed first so the export names stay clean)
for ob in made:
    nm = ob.name[:-2] if ob.name.endswith("_X") else ob.name
    if nm in O and O[nm] is not ob: O[nm].name = nm + "_SRC"
    ob.name = nm

# Ambient occlusion baked by Cycles into vertex colours (COLOR_0) of the parts that sit in each
# other's shadow (shell, nozzle, ear tip, nub). The real-time page has no ray traced occlusion,
# so without it the nozzle and the tip root read lighter than in the Cycles renders. Only the
# left bud's meshes are baked; the right bud shares them (it is a mirrored copy).
AO = "ao" in argv
if AO and KIND == "buds":
    exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "case_loop", "tools", "gpu.py"), encoding="utf-8").read())
    # occluders: only the four body parts of the left bud (inlays such as the seam, logo, grille and
    # contacts sit in the shell surface and would bake dark blotches into it)
    BODY = ("SM_Bud_Shell", "SM_Bud_Nozzle", "SM_Bud_Tip", "SM_Bud_Nub")
    for o in S.objects:
        o.hide_render = not (o in made and o.name in BODY)
    S.render.engine = "CYCLES"; S.cycles.samples = 128; S.cycles.use_denoising = False
    if S.world is None: S.world = bpy.data.worlds.new("w")
    S.world.light_settings.distance = float(os.environ.get("HERO3D_AO_DIST", "0.6"))
    for ob in made:
        if ob.name not in BODY:
            continue
        me = ob.data
        while me.vertex_colors: me.vertex_colors.remove(me.vertex_colors[0])
        me.vertex_colors.new(name="AO").active = True
        for o in S.objects: o.select_set(False)
        ob.select_set(True); bpy.context.view_layer.objects.active = ob
        bpy.ops.object.bake(type="AO", target="VERTEX_COLORS")
        vals = [c.color[0] for c in me.vertex_colors["AO"].data]
        print("AO_BAKED", ob.name, "min %.3f mean %.3f" % (min(vals), sum(vals) / len(vals)))

for o in S.objects: o.select_set(False)
for ob in made: ob.select_set(True)
bpy.context.view_layer.objects.active = made[0]
kw = dict(filepath=OUT, export_format="GLB", use_selection=True, export_apply=False, export_texcoords=False,
          export_normals=True, export_colors=AO, export_materials="EXPORT", export_cameras=False,
          export_yup=True, export_animations=False, export_extras=False)
if DRACO:
    kw.update(export_draco_mesh_compression_enable=True, export_draco_mesh_compression_level=7,
              export_draco_position_quantization=16, export_draco_normal_quantization=12)
bpy.ops.export_scene.gltf(**kw)
tv = sum(r[2] for r in report if r[4] is False)
for r in report: print("EXPORTED", *r)
print("UNIQUE_VERTS", tv, "SIZE", os.path.getsize(OUT), OUT)
