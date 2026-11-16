# Render the SunPods home-hero turntable: both buds spin together about their own vertical axes,
# frame 0 = the static hero pose. Writes transparent PNGs to branding/render/app/turntable/.
#   blender -b xm5_bud_v2.blend --python render_turntable.py -- [frames] [samples] [width]
import bpy, os, sys, math
argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
N = int(argv[0]) if argv else 72
samples = int(argv[1]) if len(argv) > 1 else 48
W = int(argv[2]) if len(argv) > 2 else 1000
TOOLS = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(TOOLS, "..", "..", "app", "turntable")); os.makedirs(OUT, exist_ok=True)
S = bpy.context.scene
pr = bpy.context.preferences.addons["cycles"].preferences
try:
    pr.compute_device_type = "OPTIX"
except TypeError:
    pr.compute_device_type = "CUDA"
pr.get_devices()
for d in pr.devices: d.use = True
S.cycles.device = "GPU"
for o in S.objects:
    if o.name.startswith("REF_"): o.hide_render = True
S.render.film_transparent = True; S.cycles.samples = samples; S.cycles.use_denoising = True
HERO = {"lens": float(os.environ.get("TT_LENS", "98")), "L": {"loc": (1.55, 0, -0.62), "rot": (8, 0, 26)}, "R": {"loc": (-1.55, 0.3, 0.62), "rot": (-6, 0, -32)},
        "target": (0, 0.2, 0), "cam": (0, -15.5, 1.6), "rim_loc": (0, 7, 5), "rim_energy": 1100}
exec(open(os.path.join(TOOLS, "app_pose.py")).read(), {"APP_POSE": HERO})
S.camera = bpy.data.objects["CAM_App"]
exec(bpy.data.texts["camfwd"].as_string())
S.render.resolution_x = W; S.render.resolution_y = round(W * 997 / 1400)
EL, ER = bpy.data.objects["EMPTY_BudL"], bpy.data.objects["EMPTY_BudR"]
zL, zR = EL.rotation_euler.z, ER.rotation_euler.z
start = int(argv[3]) if len(argv) > 3 else 0
for k in range(start, N):
    a = math.radians(360.0 * k / N)
    # same world-space spin for both (R is mirrored in X, so its local Z rotation runs the other way)
    EL.rotation_euler.z = zL + a
    ER.rotation_euler.z = zR - a
    S.render.filepath = os.path.join(OUT, "f%03d.png" % k)
    bpy.ops.render.render(write_still=True)
    print("FRAME", k, flush=True)
print("TURNTABLE DONE", OUT)
