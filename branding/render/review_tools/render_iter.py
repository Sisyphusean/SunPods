
import bpy, math, os
from mathutils import Vector
S = bpy.context.scene; co = S.camera
OUT = r"C:\Users\peyio\Documents\SonyPodsBattery\branding\render\loop\iter%02d" % ITER
os.makedirs(OUT, exist_ok=True)
HERO = (Vector((-0.25, 0.0, -0.2)), Vector((-0.35, -1, 0.10)), 10.5)
def shoot(name, T, d, dist):
    co.location = Vector(T) + Vector(d).normalized() * dist
    co.rotation_euler = (Vector(T) - co.location).to_track_quat("-Z", "Y").to_euler()
    S.render.filepath = os.path.join(OUT, name + ".png"); bpy.ops.render.render(write_still=True)
S.cycles.samples = 160; S.render.resolution_x = S.render.resolution_y = 900
shoot("hero_face", *HERO)
shoot("hero_side", (-0.25, 0.15, -0.25), (-1, -0.15, 0.1), 12.5)
S.cycles.samples = 48; S.render.resolution_x = S.render.resolution_y = 520
for k in range(8):
    a = math.radians(45 * k); shoot("turn_%03d" % (45 * k), (-0.25, 0.15, -0.25), (math.sin(a), -math.cos(a), 0.25), 12.5)
co.location = HERO[0] + HERO[1].normalized() * HERO[2]; co.rotation_euler = (HERO[0] - co.location).to_track_quat("-Z", "Y").to_euler()
bpy.ops.wm.save_mainfile()
bpy.ops.wm.save_as_mainfile(filepath=r"C:\Users\peyio\Documents\SonyPodsBattery\branding\render\loop\backup_iter%02d.blend" % ITER, copy=True)
exec(bpy.data.texts["board_iter"].as_string())
bpy.ops.wm.save_mainfile()
print("rendered iter", ITER)
