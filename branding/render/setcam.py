# Run inside live Blender: set CAM = (az, el, roll, dist[, lens]) and optional TARGET first.
import bpy, math
from mathutils import Vector
co = bpy.data.objects["cam"]
az, el, roll, dist = CAM[:4]
if len(CAM) > 4:
    co.data.lens = CAM[4]
t = Vector(globals().get("TARGET", (0, -0.2, -0.4)))
d = Vector((math.cos(math.radians(el)) * math.sin(math.radians(az)),
            -math.cos(math.radians(el)) * math.cos(math.radians(az)),
            math.sin(math.radians(el))))
co.location = t + d * dist
co.rotation_euler = (t - co.location).to_track_quat("-Z", "Y").to_euler()
co.rotation_euler.rotate_axis("Z", math.radians(roll))
print("cam", tuple(round(c, 2) for c in co.location))
