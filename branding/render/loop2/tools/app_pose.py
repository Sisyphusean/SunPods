
import bpy, math, os
from mathutils import Vector, Euler
S = bpy.context.scene; O = bpy.data.objects
P = APP_POSE
cam = O.get("CAM_App")
if not cam:
    cd = bpy.data.cameras.new("CAM_App"); cam = O.new("CAM_App", cd); bpy.data.collections["COL_Cam"].objects.link(cam)
cam.data.lens = P.get("lens", 70)
for nm, key in (("EMPTY_BudL","L"), ("EMPTY_BudR","R")):
    e = O[nm]; p = P.get(key)
    hide = p is None
    for o in [e] + list(e.children_recursive): o.hide_render = hide
    if p:
        e.location = p["loc"]; e.rotation_euler = Euler([math.radians(a) for a in p["rot"]], "XYZ")
        e.scale = (-p.get("s",1.0) if key == "R" else p.get("s",1.0), p.get("s",1.0), p.get("s",1.0))
T = Vector(P["target"]); cam.location = Vector(P["cam"]); cam.rotation_euler = (T - cam.location).to_track_quat("-Z","Y").to_euler()
rim = O.get("LGT_Rim_App")
if not rim:
    ld = bpy.data.lights.new("LGT_Rim_App", "AREA"); rim = O.new("LGT_Rim_App", ld); bpy.data.collections["COL_Lights"].objects.link(rim)
rim.data.shape = "DISK"; rim.data.size = P.get("rim_size", 4); rim.data.energy = P.get("rim_energy", 900)
rim.location = Vector(P.get("rim_loc", (0, 6, 4))); rim.rotation_euler = (T - rim.location).to_track_quat("-Z","Y").to_euler(); rim.hide_render = False
try: rim.visible_glossy = True
except Exception: pass
