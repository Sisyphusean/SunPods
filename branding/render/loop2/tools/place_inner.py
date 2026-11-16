
import bpy, json
from mathutils import Vector
from mathutils.bvhtree import BVHTree
S = bpy.context.scene; dg = bpy.context.evaluated_depsgraph_get(); sh = bpy.data.objects["SM_Bud_Shell"]
bvh = BVHTree.FromObject(sh, dg)
# name: (x, z, ray origin y, ray dir, lift)
spec = json.loads(S["INNER_SPEC"])
for name, (x, z, oy, d, lift) in spec.items():
    o = bpy.data.objects[name]
    hit, nrm, *_ = bvh.ray_cast(Vector((x, oy, z)) if d[1] else Vector((x, z, oy)) if False else Vector((x, oy, z)), Vector(d))
    if hit is None: print("miss", name); continue
    o.location = hit + nrm * lift
    o.rotation_euler = nrm.to_track_quat("Z", "Y").to_euler()
