# App stills (buds_pair, bud_left, bud_right) in another finish, through the original
# loop2/tools/render_app_assets.py with only the output folder changed:
#   blender -b loop2/xm5_bud_v2.blend --python hero3d_tools/render_stills.py -- <black|silver|pink> <out dir> [samples]
import bpy, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
args = sys.argv[sys.argv.index("--") + 1:]
color, out_dir = args[0], args[1]
samples = args[2] if len(args) > 2 else "160"
exec(open(os.path.join(HERE, "finish_blender.py"), encoding="utf-8").read())
apply(color)
src_path = os.path.join(HERE, "..", "loop2", "tools", "render_app_assets.py")
src = open(src_path, encoding="utf-8").read()
key = 'OUT = os.path.normpath(os.path.join(TOOLS, "..", "..", "app")); os.makedirs(OUT, exist_ok=True)'
assert src.count(key) == 1
src = src.replace(key, "OUT = %r; os.makedirs(OUT, exist_ok=True)" % os.path.abspath(out_dir))
sys.argv = sys.argv[:sys.argv.index("--") + 1] + ["all", samples]
exec(compile(src, src_path, "exec"), {"__file__": src_path, "__name__": "__main__"})
