# Run inside live Blender after the camera is placed: rolls it so the panel's long axis is vertical.
import bpy, math
from bpy_extras.object_utils import world_to_camera_view as w2c
from mathutils import Vector
_s = bpy.context.scene; _co = _s.camera
for _ in range(3):
    bpy.context.view_layer.update()
    _a = w2c(_s, _co, Vector((0, 0.5, 0.5))); _b = w2c(_s, _co, Vector((0, -0.8, 0.45)))
    _co.rotation_euler.rotate_axis("Z", -math.atan2(_a.x - _b.x, _a.y - _b.y))
print("aligned")
