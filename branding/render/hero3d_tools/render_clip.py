# Onboarding clips (pair, open) in another finish, through the original anim/anim_render.py with
# only the output folder (and optionally the frame range) changed:
#   blender -b anim/xm5_case_anim.blend --python hero3d_tools/render_clip.py -- <black|silver|pink> <pair|open> <out dir> [first last]
import bpy, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
args = sys.argv[sys.argv.index("--") + 1:]
color, clip, out_dir = args[0], args[1], os.path.abspath(args[2])
rng = (int(args[3]), int(args[4])) if len(args) > 4 else None
exec(open(os.path.join(HERE, "finish_blender.py"), encoding="utf-8").read())
apply(color)
if os.environ.get("HERO3D_HIDE_INTERNALS") == "1":
    # The approved case has no tray (its exact boolean evaluates empty), so once the earbuds lift
    # out the USB-C cavity and button well show inside the case. On black they vanish into the
    # black interior; on a light finish they read as black blocks, so here they take the interior
    # colour (object-level slots, the meshes are untouched). The front light keeps its dark core.
    inner = bpy.data.materials["MAT_Case_Inner"].copy(); inner.name = "MAT_Case_Inner_Port"
    for nm in ("SM_Case_USB", "SM_Case_USBTongue", "SM_Case_ButtonWell"):
        o = bpy.data.objects[nm]
        for slot in o.material_slots:
            slot.link = "OBJECT"; slot.material = inner
    print("INTERNALS recoloured")
src_path = os.path.join(HERE, "..", "anim", "anim_render.py")
src = open(src_path, encoding="utf-8").read()
key = 'OUT = os.path.join(ANIM, CLIP + ("_preview" if PREVIEW else ""))'
assert src.count(key) == 1
src = src.replace(key, "OUT = %r" % out_dir)
if rng:
    key2 = "bpy.ops.render.render(animation=True)"
    assert src.count(key2) == 1
    src = src.replace(key2, "S.frame_start, S.frame_end = %d, %d\n%s" % (rng[0], rng[1], key2))
sys.argv = sys.argv[:sys.argv.index("--") + 1] + [clip]
exec(compile(src, src_path, "exec"), {"__file__": src_path, "__name__": "__main__"})
