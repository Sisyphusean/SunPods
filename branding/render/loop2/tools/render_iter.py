# Iteration render: heroes + 8 turns on white, backup .blend, then the comparison board. Set ITER first.
import bpy, math, os
from mathutils import Vector
S = bpy.context.scene; co = S.camera
RD = r"C:\Users\peyio\Documents\SonyPodsBattery\branding\render\loop2"
OUT = os.path.join(RD, "iter%02d" % ITER); os.makedirs(OUT, exist_ok=True)
T = Vector(S.get("BOARD_TARGET", (-0.15, 0.25, -0.2)))
for o in S.objects:
    if o.name.startswith("REF_"): o.hide_render = True
def shoot(name, d, dist):
    co.location = T + Vector(d).normalized() * dist
    co.rotation_euler = (T - co.location).to_track_quat("-Z", "Y").to_euler()
    exec(bpy.data.texts["camfwd"].as_string()); S.render.filepath = os.path.join(OUT, name + ".png"); bpy.ops.render.render(write_still=True)
S.cycles.samples = 128; S.render.resolution_x = S.render.resolution_y = 900
shoot("hero_face", S.get("HERO_FACE", (-0.35, -1, 0.10)), 9.0)
shoot("hero_side", S.get("HERO_SIDE", (-1, -0.15, 0.1)), 9.0)
S.cycles.samples = 40; S.render.resolution_x = S.render.resolution_y = 500
for k in range(8):
    a = math.radians(45 * k); shoot("turn_%03d" % (45 * k), (math.sin(a), -math.cos(a), 0.25), 10.0)
shoot("hero_face_tmp", S.get("HERO_FACE", (-0.35, -1, 0.10)), 9.0); os.remove(os.path.join(OUT, "hero_face_tmp.png"))
bpy.ops.wm.save_mainfile()
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(RD, "backup_iter%02d.blend" % ITER), copy=True)
exec(bpy.data.texts["board_iter"].as_string())
bpy.ops.wm.save_mainfile()
print("rendered iter", ITER)
