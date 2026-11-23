# Solves the WF-C710N body albedos (black, white, pink) from Sony's US product shots, the way
# hero3d_tools/calib_colors.py did for the XM5: Sony shot every colour in one session with the same
# light and framing, so each region has a known target per colour. This page's own lighting is
# measured by rendering with the body albedo at 0 and 0.5 (calib_c7.mjs), giving per region
#     value = albedo * E + S   (linear RGB)    ->    albedo = (Sony - S) / E
#   node calib_c7.mjs && python calib_c7.py
import os, json
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
C7 = os.path.normpath(os.path.join(HERE, '..'))
CAL = os.path.join(C7, '_fit', 'calib')
meta = json.load(open(os.path.join(C7, '_fit', 'refs', 'meta.json')))
pts = json.load(open(os.path.join(CAL, 'points.json')))

def lin(a): a = a / 255.0; return np.where(a <= 0.04045, a / 12.92, ((a + 0.055) / 1.055) ** 2.4)
def load_render(p):
    im = Image.open(p).convert('RGBA'); b = Image.new('RGBA', im.size, (255, 255, 255, 255)); b.alpha_composite(im)
    return lin(np.asarray(b.convert('RGB')).astype(float))
def load_sony(img, crop, box):
    im = Image.open(os.path.join(C7, img)).convert('RGBA')
    if crop: im = im.crop(tuple(crop))
    side = int(box[2] - box[0]); pad = side
    bg = Image.new('RGBA', (im.size[0] + 2 * pad, im.size[1] + 2 * pad), (255, 255, 255, 255)); bg.alpha_composite(im, (pad, pad))
    rgb = bg.convert('RGB').crop(tuple(int(round(v + pad)) for v in box)).resize((512, 512), Image.LANCZOS)
    return lin(np.asarray(rgb).astype(float))
def reg(A, xy, r=9):
    x, y = int(round(xy[0])), int(round(xy[1]))
    return A[y - r:y + r + 1, x - r:x + r + 1].reshape(-1, 3).mean(0)

REG = {'front_R': {'face': ['disc'], 'shell': ['shellTop', 'shellLeft'], 'tip': ['tip']},
       'closed_front': {'caseShell': ['caseFrontL', 'caseFrontR', 'lidFront']}}
IMG = {'front_R': 'ref_%s/sonyus_buds_front.png', 'closed_front': 'ref_%s/sonyus_case_closed.png'}
out = {}
for rid, regions in REG.items():
    m = meta[rid]
    z, h = load_render(os.path.join(CAL, rid + '_cal0.png')), load_render(os.path.join(CAL, rid + '_cal5.png'))
    for name, keys in regions.items():
        S = np.mean([reg(z, pts[rid][k]) for k in keys], 0)
        E = (np.mean([reg(h, pts[rid][k]) for k in keys], 0) - S) / 0.5
        print('%-12s %-10s S %s E %s' % (rid, name, S.round(4), E.round(4)))
        for col in ('black', 'white', 'pink', 'blue'):
            T = np.mean([reg(load_sony(IMG[rid] % col, m.get('crop'), m['box']), pts[rid][k]) for k in keys], 0)
            alb = np.clip((T - S) / np.maximum(E, 1e-4), 0, 1)
            out.setdefault(col, {})[name] = alb.round(4).tolist()
            print('     %-6s Sony %s  albedo %s' % (col, T.round(4), alb.round(3)))
json.dump(out, open(os.path.join(CAL, 'albedo.json'), 'w'), indent=1)
