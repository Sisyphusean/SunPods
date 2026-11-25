# Sony's WF-1000XM4 photos as fit references (tools only): each review angle is a square region of a
# sony.jp photo round one subject, saved as a 512 px picture on white (<id>_ref.png) and its silhouette
# (<id>_mask.png, white subject), plus refs/meta.json for board.mjs and closeup.mjs.
#   python prep_refs.py
import os, json
import numpy as np
from PIL import Image
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
X4 = os.path.normpath(os.path.join(HERE, '..'))
OUT = os.path.join(X4, '_fit', 'refs')
os.makedirs(OUT, exist_ok=True)
# id: (photo, square box in the photo (x0, y0, size), subject, colour, starting pose [yaw, pitch, roll])
# The black face view on the sony.jp lineup is the right earbud (SONY on its front shoulder, the copper
# microphone low at the front); the side views show the left earbud from its front.
REFS = {
    'face_R': ('ref_sony/WF-1000XM4.jpg', (172, 46, 205), 'budR', 'black', [-15, 0, 0]),
    'side_L': ('ref_sony/WF-1000XM4.jpg', (395, 46, 205), 'budL', 'black', [90, 0, 0]),
    'ang_L': ('ref_sony/y_WF-1000XM4_007.jpg', (70, 30, 330), 'budL', 'black', [25, 5, 0]),
    'face3q_R': ('ref_sony/original_WF-1000XM4_003.jpg', (20, 60, 200), 'budR', 'black', [-20, 0, 0]),
    'side2_L': ('ref_sony/original_WF-1000XM4_003.jpg', (220, 60, 200), 'budL', 'black', [90, 0, 0]),
    'face_R_silver': ('ref_sony/WF-1000XM4.jpg', (0, 252, 215), 'budR', 'silver', [-15, 0, 0]),
    'side_L_silver': ('ref_sony/WF-1000XM4.jpg', (215, 252, 215), 'budL', 'silver', [90, 0, 0]),
}

meta = {}
for rid, (img, (x0, y0, s), subject, colour, init) in REFS.items():
    im = Image.open(os.path.join(X4, img)).convert('RGB')
    sq = Image.new('RGB', (s, s), (255, 255, 255))
    sq.paste(im.crop((x0, y0, x0 + s, y0 + s)), (0, 0))
    a = np.asarray(sq).astype(int)
    red = (a[..., 0] > 140) & (a[..., 1] < 110) & (a[..., 2] < 110)          # Sony's red callout marks
    ink = (255 - a.min(axis=2)) > (40 if colour == 'black' else 14)
    m = ink & ~red
    m = ndimage.binary_closing(m, iterations=2)
    m = ndimage.binary_fill_holes(m)
    lab, n = ndimage.label(m)
    if n:
        # the subject: the biggest blob touching the middle third; other blobs (the neighbour, text) go
        sizes = ndimage.sum(m, lab, range(1, n + 1))
        keep = 1 + int(np.argmax(sizes))
        m = lab == keep
    # the red callouts cut the outline where they cross the subject: close those gaps
    m = ndimage.binary_closing(m, iterations=3) | m
    m = ndimage.binary_fill_holes(m)
    sq.resize((512, 512), Image.LANCZOS).save(os.path.join(OUT, f'{rid}_ref.png'))
    Image.fromarray((m * 255).astype(np.uint8)).resize((512, 512), Image.LANCZOS).point(lambda v: 255 if v > 127 else 0).save(os.path.join(OUT, f'{rid}_mask.png'))
    meta[rid] = {'img': img, 'box': [x0, y0, x0 + s, y0 + s], 'subject': subject, 'color': colour, 'init': init}
    # Sony's side views are level shots: the fit keeps their pitch at 0 (free, it drifts to a false
    # optimum that rolls the earbud about its face)
    if rid.startswith('side'): meta[rid]['lockPitch'] = 0
    print(rid, subject, colour, 'mask fraction', round(float(m.mean()), 3))
json.dump(meta, open(os.path.join(OUT, 'meta.json'), 'w'), indent=1)
