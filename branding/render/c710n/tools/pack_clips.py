# Packs the WF-C710N clip frames (clips.mjs) exactly like the XM5 clips (branding/render/anim/pack.py:
# 480 px, 30 fps, loop 0, quality 82, method 6) and copies them to app/src/main/res/raw as
# c710n_anim_<clip>_<colour>.webp.   python pack_clips.py <framesRoot> [colours...]
import os, sys, shutil, subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
C7 = os.path.normpath(os.path.join(HERE, '..'))
REPO = os.path.normpath(os.path.join(C7, '..', '..', '..'))
PACK = os.path.join(REPO, 'branding', 'render', 'anim', 'pack.py')
RAW = os.path.join(REPO, 'app', 'src', 'main', 'res', 'raw')
root = sys.argv[1]
colours = sys.argv[2:] or ['black', 'white', 'pink', 'blue']
for clip, n in (('open', 120), ('pair', 210)):
    for c in colours:
        src = os.path.join(root, f'{clip}_{c}')
        got = len([f for f in os.listdir(src) if f.endswith('.png')])
        assert got == n, (src, got, n)
        dst = os.path.join(root, f'c710n_anim_{clip}_{c}.webp')
        subprocess.run([sys.executable, PACK, src, dst, '480', '1'], check=True)
        shutil.copyfile(dst, os.path.join(RAW, os.path.basename(dst)))
