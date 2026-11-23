# Cuts each review angle out of Sony's image (refs.json): one subject, framed square with a margin,
# as a 512 px picture on white (for the boards) and a 192 px silhouette mask (for the camera fit).
#   python prep_refs.py
import os, json
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

HERE = os.path.dirname(os.path.abspath(__file__))
C7 = os.path.normpath(os.path.join(HERE, '..'))
OUT = os.path.join(C7, '_fit', 'refs'); os.makedirs(OUT, exist_ok=True)
REFS = json.load(open(os.path.join(HERE, 'refs.json')))
MARGIN = 0.10
FIT = 192

def subject_mask(im, kind):
    a = np.asarray(im.convert('RGBA')).astype(int)
    if kind == 'alpha':
        m = a[..., 3] > 110
    else:
        m = (a[..., :3].min(2) < 238) | ((a[..., :3].max(2) - a[..., :3].min(2)) > 14)
        m = ndi.binary_closing(m, iterations=3)
    m = ndi.binary_fill_holes(m)
    lab, n = ndi.label(m)
    if n == 0: return m
    sz = ndi.sum(m, lab, range(1, n + 1))
    keep = [i + 1 for i in range(n) if sz[i] > 0.04 * sz.max()]
    return np.isin(lab, keep)

meta = {}
for group in ('buds', 'case'):
    for r in REFS[group]:
        im = Image.open(os.path.join(C7, r['img'])).convert('RGBA')
        if 'crop' in r: im = im.crop(tuple(r['crop']))
        m = subject_mask(im, r['mask'])
        ys, xs = np.where(m)
        x0, x1, y0, y1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
        side = max(x1 - x0, y1 - y0) * (1 + 2 * MARGIN)
        if r['id'].startswith('spin'): side = 1620   # one pixel scale for the whole turntable
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        box = (cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2)
        # pad generously with white so a box past the image edge stays white
        pad = int(side)
        bg = Image.new('RGBA', (im.size[0] + 2 * pad, im.size[1] + 2 * pad), (255, 255, 255, 255))
        bg.alpha_composite(im, (pad, pad))
        rgb = bg.convert('RGB').crop(tuple(int(round(v + pad)) for v in box))
        rgb.resize((512, 512), Image.LANCZOS).save(os.path.join(OUT, r['id'] + '_rgb.png'))
        mpad = Image.new('L', bg.size, 0); mpad.paste(Image.fromarray((m * 255).astype(np.uint8)), (pad, pad))
        mi = mpad.crop(tuple(int(round(v + pad)) for v in box))
        mi.resize((FIT, FIT), Image.BILINEAR).point(lambda v: 255 if v > 127 else 0).save(os.path.join(OUT, r['id'] + '_mask.png'))
        mi.resize((512, 512), Image.BILINEAR).point(lambda v: 255 if v > 127 else 0).save(os.path.join(OUT, r['id'] + '_mask512.png'))
        meta[r['id']] = {'group': group, 'box': box, 'size': im.size, **r}
        print(r['id'], 'subject bbox', (x0, y0, x1, y1), 'box side', round(side))
json.dump(meta, open(os.path.join(OUT, 'meta.json'), 'w'), indent=1)
