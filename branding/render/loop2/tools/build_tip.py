
import bpy, bmesh, math
from mathutils import Vector
S = bpy.context.scene
axis = Vector(S["TIP_AXIS"]).normalized(); base = Vector(S["TIP_BASE"]); DIST = S["TIP_DIST"]
L = S.get("TIP_LEN", 0.78); R1 = S.get("TIP_R1", 0.56); R2 = S.get("TIP_R2", 0.47); W = S.get("TIP_WALL", 0.17); RB = S.get("TIP_BORE", 0.16)
h = L/2
# closed section (r, z): open end at z=-h (body side), outer end at z=+h
prof = [(R1-W, -h), (R1-0.03, -h), (R1-0.008, -h+0.008), (R1, -h+0.04),
        (R1-0.02, -h+0.42*L), (R2+0.025, h-0.27*L), (R2, h-0.17*L),
        (R2-0.045, h-0.065*L), (R2-0.12, h-0.014*L), (R2-0.2, h), (RB+0.1, h),
        (RB+0.05, h-0.004), (RB+0.02, h-0.02), (RB+0.004, h-0.05), (RB, h-0.09),
        (RB, -0.02), (RB+0.06, -h+0.30*L), (R1-W-0.02, -h+0.16*L), (R1-W-0.004, -h+0.03), (R1-W, -h+0.008)]
tip = bpy.data.objects["SM_Bud_Tip"]; me = tip.data
bm = bmesh.new()
vs = [bm.verts.new((r, 0, z)) for r, z in prof]
for i in range(len(vs)): bm.edges.new((vs[i], vs[(i+1) % len(vs)]))
bmesh.ops.spin(bm, geom=bm.verts[:] + bm.edges[:], cent=(0,0,0), axis=(0,0,1), angle=math.tau, steps=64, use_duplicate=False)
bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-5)
bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
bm.to_mesh(me); me.update(); bm.free()
for p in me.polygons: p.use_smooth = True
for m in list(tip.modifiers): tip.modifiers.remove(m)
s = tip.modifiers.new("Subsurf", "SUBSURF"); s.levels = 1; s.render_levels = 2
tip.location = base + axis*DIST; tip.rotation_euler = axis.to_track_quat("Z","Y").to_euler(); tip.scale = (1,1,1)
d = bpy.data.objects["SM_Bud_TipMesh"]; d.location = (0, 0, h - 0.24*L); k = RB/0.215*0.99; d.scale = (k, k, 1)
n = bpy.data.objects["SM_Bud_Nozzle"]; n.rotation_euler = tip.rotation_euler
n.location = base + axis*(DIST - h + 0.02); nr = S.get("NOZ_R", 0.3); n.scale = (nr/0.28, nr/0.28, 1)
print("tip", tuple(round(x,3) for x in tip.dimensions))
