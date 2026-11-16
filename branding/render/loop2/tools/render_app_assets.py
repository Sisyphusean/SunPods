# Render SunPods app assets from xm5_bud_v2.blend in background mode:
#   blender -b xm5_bud_v2.blend --python render_app_assets.py -- [hero|left|right|all] [samples]
# Writes transparent PNGs to branding/render/app/.
import bpy, os, sys
argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
which = argv[0] if argv else "all"
samples = int(argv[1]) if len(argv) > 1 else 128
TOOLS = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(TOOLS, "..", "..", "app")); os.makedirs(OUT, exist_ok=True)
pose_code = open(os.path.join(TOOLS, "app_pose.py")).read()
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
POSES = {
    "hero": ({"lens": 98, "L": {"loc": (1.55, 0, -0.62), "rot": (8, 0, 26)}, "R": {"loc": (-1.55, 0.3, 0.62), "rot": (-6, 0, -32)},
              "target": (0, 0.2, 0), "cam": (0, -15.5, 1.6), "rim_loc": (0, 7, 5), "rim_energy": 1100}, (1400, 997), "buds_pair.png"),
    "left": ({"lens": 172, "L": {"loc": (0, 0, 0), "rot": (8, 0, 26)}, "R": None,
              "target": (-0.22, 0.35, -0.12), "cam": (-0.22, -15.5, 1.4), "rim_loc": (0, 7, 5), "rim_energy": 1100}, (734, 618), "bud_left.png"),
    "right": ({"lens": 172, "L": None, "R": {"loc": (0, 0, 0), "rot": (8, 0, -26)},
               "target": (0.22, 0.35, -0.12), "cam": (0.22, -15.5, 1.4), "rim_loc": (0, 7, 5), "rim_energy": 1100}, (734, 618), "bud_right.png"),
}
for key in (["hero", "left", "right"] if which == "all" else [which]):
    pose, (rx, ry), fname = POSES[key]
    exec(pose_code, {"APP_POSE": pose})
    S.camera = bpy.data.objects["CAM_App"]
    exec(bpy.data.texts["camfwd"].as_string())
    S.render.resolution_x, S.render.resolution_y = rx, ry
    S.render.filepath = os.path.join(OUT, fname)
    bpy.ops.render.render(write_still=True)
    print("RENDERED", key, S.render.filepath)
