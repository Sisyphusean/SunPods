
import bpy
from mathutils import Vector
win, area, region = bpy.app.driver_namespace["V3D"]
if "SM_Bud_Seam" in bpy.data.objects: bpy.data.objects.remove(bpy.data.objects["SM_Bud_Seam"], do_unlink=True)
shell = bpy.data.objects["SM_Bud_Shell"]; S = bpy.context.scene
nrm = Vector(S.get("SEAM_N", (0, 1, -0.35))).normalized(); ctr = Vector(S.get("SEAM_C", (0, 0.25, 0)))
bpy.ops.object.select_all(action="DESELECT")
bpy.ops.mesh.primitive_circle_add(vertices=160, radius=0.95, location=ctr, rotation=nrm.to_track_quat("Z","Y").to_euler())
s = bpy.context.active_object; s.name = "SM_Bud_Seam"
for c in s.users_collection: c.objects.unlink(s)
bpy.data.collections["COL_Bud"].objects.link(s); bpy.context.view_layer.objects.active = s; s.select_set(True)
bow = S.get("SEAM_BOW", 0.09)
for v in s.data.vertices:
    w = s.matrix_world @ v.co; k = max(0.0, 1 - (w.z / 1.1) ** 2)
    v.co = s.matrix_world.inverted() @ (w + nrm * bow * k)
sw = s.modifiers.new("SW","SHRINKWRAP"); sw.target = shell; sw.wrap_method = "NEAREST_SURFACEPOINT"
with bpy.context.temp_override(window=win, area=area, region=region, object=s, active_object=s, selected_objects=[s], selected_editable_objects=[s]):
    bpy.ops.object.modifier_apply(modifier="SW"); bpy.ops.object.convert(target="CURVE")
s.data.bevel_depth = S.get("SEAM_W", 0.0075); s.data.bevel_resolution = 2
s.data.materials.append(bpy.data.materials["MAT_Bud_Seam"])
