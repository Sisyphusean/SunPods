# Preview sheets for the WF-C710N assets (branding/render/c710n/preview, gitignored folder):
#   stills_<colour>.jpg        the four app stills on SunPods' dark and light backgrounds, with the XM5's for framing
#   live_<colour>.jpg          live three.js snapshots (test_c710n.mjs): earbuds, case closed, case open
#   vs_sony_<colour>.jpg       Sony's photo beside the final model at the silhouette-fitted camera, per angle
#   clips_<clip>_<colour>.jpg  frame strips of the setup clips (from the packed WebP)
#   python compose_preview.py <iterDir> [stills|live|vs|clips|all]
import os, sys, json
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
C7 = os.path.normpath(os.path.join(HERE, '..'))
REPO = os.path.normpath(os.path.join(C7, '..', '..', '..'))
RES = os.path.join(REPO, 'app', 'src', 'main', 'res')
OUT = os.path.join(C7, 'preview')
os.makedirs(OUT, exist_ok=True)
it = sys.argv[1]
what = sys.argv[2] if len(sys.argv) > 2 else 'all'
COLOURS = ['black', 'white', 'pink', 'blue']
NAMES = {'black': 'Black', 'white': 'White', 'pink': 'Pink', 'blue': 'Glass Blue'}
DARK, LIGHT = (20, 18, 24), (244, 242, 246)
try:
    F = ImageFont.truetype('arial.ttf', 20); FS = ImageFont.truetype('arial.ttf', 15)
except OSError:
    F = FS = None

def on(bg, im, size=None):
    im = im.convert('RGBA')
    if size: im = im.resize(size, Image.LANCZOS)
    b = Image.new('RGBA', im.size, bg + (255,)); b.alpha_composite(im); return b.convert('RGB')

def stills():
    names = ['bud_left', 'bud_right', 'bud_case', 'buds_pair']
    for c in COLOURS:
        rows = []
        for bg in (DARK, LIGHT):
            ims = [Image.open(os.path.join(RES, 'drawable-nodpi', f'c710n_{n}_{c}.png')) for n in names]
            ims[3] = ims[3].resize((425, 303), Image.LANCZOS)
            rows.append([on(bg, im) for im in ims])
        xm5 = [Image.open(os.path.join(RES, 'drawable-nodpi', f'{n}.png')) for n in names]
        xm5[3] = xm5[3].resize((425, 303), Image.LANCZOS)
        rows.append([on(DARK, im) for im in xm5])
        W = sum(im.width for im in rows[0]) + 50; H = 3 * 303 + 120
        sh = Image.new('RGB', (W, H), (60, 60, 66)); d = ImageDraw.Draw(sh)
        d.text((10, 8), f'WF-C710N {NAMES[c]}: app stills (dark, light), XM5 Black below for framing', fill=(255, 255, 255), font=F)
        y = 40
        for r in rows:
            x = 10
            for im in r:
                sh.paste(im, (x, y + (303 - im.height) // 2)); x += im.width + 10
            y += 303 + 25
        sh.save(os.path.join(OUT, f'stills_{c}.jpg'), quality=90)
        print('stills', c)

def live():
    views = ['buds', 'case_closed', 'case_open']
    for c in COLOURS:
        ims = [Image.open(os.path.join(OUT, f'live_{c}_{v}.png')) for v in views]
        sh = Image.new('RGB', (3 * 420 + 40, 340), DARK); d = ImageDraw.Draw(sh)
        d.text((10, 6), f'WF-C710N {NAMES[c]}: live three.js snapshots (sunpods.snapshot(), 412 x 300 view)', fill=(255, 255, 255), font=FS)
        for i, im in enumerate(ims):
            sh.paste(on(DARK, im, (412, 300)), (10 + i * 425, 32))
        sh.save(os.path.join(OUT, f'live_{c}.jpg'), quality=90)
        print('live', c)

def vs():
    meta = json.load(open(os.path.join(C7, '_fit', 'refs', 'meta.json')))
    fits = json.load(open(os.path.join(it, 'fits.json')))
    T = 300
    for c in COLOURS:
        ids = [k for k in meta if meta[k]['color'] == c and k in fits]
        cols = 2
        rows = (len(ids) + cols - 1) // cols
        sh = Image.new('RGB', (cols * (2 * T + 30), rows * (T + 28) + 40), (235, 235, 235)); d = ImageDraw.Draw(sh)
        d.text((8, 8), f'WF-C710N {NAMES[c]}: Sony (left) | final model at the silhouette-fitted camera (right)', fill=(0, 0, 0), font=F)
        for n, k in enumerate(ids):
            r, q = n // cols, n % cols
            x, y = q * (2 * T + 30), 40 + r * (T + 28)
            sh.paste(on((255, 255, 255), Image.open(os.path.join(C7, '_fit', 'refs', k + '_rgb.png')), (T, T)), (x, y + 22))
            sh.paste(on((255, 255, 255), Image.open(os.path.join(it, k + '_model.png')), (T, T)), (x + T + 4, y + 22))
            d.text((x + 4, y + 2), f"{k}  IoU {fits[k]['iou']:.3f}", fill=(0, 0, 0), font=FS)
        sh.save(os.path.join(OUT, f'vs_sony_{c}.jpg'), quality=88)
        print('vs', c, len(ids))

def clips():
    for clip in ('open', 'pair'):
        for c in COLOURS:
            p = os.path.join(RES, 'raw', f'c710n_anim_{clip}_{c}.webp')
            if not os.path.exists(p): continue
            im = Image.open(p); n = im.n_frames
            # every stored frame's display time (PIL merges repeated frames into longer ones)
            durs = []
            for k in range(n):
                im.seek(k); durs.append(im.info.get('duration', 33))
            total = sum(durs)
            picks = [round(total * t / 11) for t in range(12)]
            tiles, acc, k = [], 0, 0
            for t in picks:
                while k < n - 1 and acc + durs[k] <= t: acc += durs[k]; k += 1
                im.seek(k); tiles.append(on(DARK, im.convert('RGBA'), (240, 240)))
            sh = Image.new('RGB', (6 * 240, 2 * 240 + 30), DARK); d = ImageDraw.Draw(sh)
            d.text((8, 6), f'c710n_anim_{clip}_{c}.webp  {n} stored frames, {total} ms, every {total // 11} ms', fill=(255, 255, 255), font=FS)
            for i, t in enumerate(tiles): sh.paste(t, ((i % 6) * 240, 30 + (i // 6) * 240))
            sh.save(os.path.join(OUT, f'clips_{clip}_{c}.jpg'), quality=88)
            print('clip', clip, c, n, total)

for name, fn in (('stills', stills), ('live', live), ('vs', vs), ('clips', clips)):
    if what in (name, 'all'): fn()
