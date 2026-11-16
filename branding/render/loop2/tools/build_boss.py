
import bpy, bmesh, math
from mathutils import Vector
S = bpy.context.scene
axis = Vector(S["TIP_AXIS"]).normalized(); base = Vector(S["TIP_BASE"])
n = bpy.data.objects["SM_Bud_Nozzle"]; me = n.data
prof = S.get("BOSS_PROF", [(0.0,-0.35),(0.44,-0.35),(0.44,-0.05),(0.40,0.12),(0.33,0.24),(0.29,0.34),(0.28,0.55),(0.0,0.55)])
bm = bmesh.new()
vs = [bm.verts.new((r, 0, z)) for r, z in prof]
for i in range(len(vs)-1): bm.edges.new((vs[i], vs[i+1]))
bmesh.ops.spin(bm, geom=bm.verts[:] + bm.edges[:], cent=(0,0,0), axis=(0,0,1), angle=math.tau, steps=48, use_duplicate=False)
bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-5); bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
bm.to_mesh(me); me.update(); bm.free()
for p in me.polygons: p.use_smooth = True
for m in list(n.modifiers): n.modifiers.remove(m)
s = n.modifiers.new("Subsurf","SUBSURF"); s.levels = 1; s.render_levels = 2
n.location = base; n.rotation_euler = axis.to_track_quat("Z","Y").to_euler(); n.scale = (1,1,1)
print("boss", tuple(round(x,3) for x in n.dimensions))
