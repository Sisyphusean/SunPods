# Ear-tip aim trials (tools only): renders the studio views for each candidate nozzle direction and puts
# Sony's ear tip beside each candidate's, cropped at the same place (the base render's tip).
#   python tip_sweep.py <outDir> <deg> [deg...]   (degrees from straight into the ear towards the side)
import os, sys, json, math, subprocess
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
C7 = os.path.normpath(os.path.join(HERE, '..'))
out = sys.argv[1]
cands = [tuple(float(v) for v in a.split(':')) for a in sys.argv[2:]]   # deg[:seatCap]
degs = [c[0] for c in cands]
meta = json.load(open(os.path.join(C7, '_fit', 'refs', 'meta.json')))
VIEWS = ['front_R', 'front_L', 'front_R_white', 'side_L', 'side_L_pink']
T, HALF = 190, 85
UP = 0.174   # the nozzle's upward component, kept
try:
    FS = ImageFont.truetype('arial.ttf', 14)
except OSError:
    FS = None

def render(name, deg):
    d = os.path.join(out, name)
    env = dict(os.environ)
    if deg is not None:
        h = math.sqrt(1 - UP * UP); a = math.radians(deg)
        ovr = {'nozzle': {'dir': [h * math.sin(a), UP, -h * math.cos(a)]}}
        cap = {c[0]: c[1] for c in cands if len(c) > 1}.get(deg)
        if cap: ovr['tip'] = {'seatCap': cap}
        env['C7_OVR'] = json.dumps({'BUD': ovr})
    subprocess.run(['node', os.path.join(HERE, 'closeup.mjs'), d, *VIEWS], check=True, env=env, cwd=HERE, stdout=subprocess.DEVNULL)
    return d

def crop(im, box):
    pad = Image.new('RGB', (im.width + 4000, im.height + 4000), (255, 255, 255)); pad.paste(im, (2000, 2000))
    return pad.crop(tuple(int(round(v + 2000)) for v in box)).resize((T, T), Image.LANCZOS)

def sony(k, cx, cy):
    m = meta[k]
    im = Image.open(os.path.join(C7, m['img'])).convert('RGBA')
    if 'crop' in m: im = im.crop(tuple(m['crop']))
    b = Image.new('RGBA', im.size, (255, 255, 255, 255)); b.alpha_composite(im)
    x0, y0, x1, y1 = m['box']; s = (x1 - x0) / 512
    return crop(b.convert('RGB'), (x0 + (cx - HALF) * s, y0 + (cy - HALF) * s, x0 + (cx + HALF) * s, y0 + (cy + HALF) * s))

def model(d, k, cx, cy):
    im = Image.open(os.path.join(d, f'{k}_model.png')).convert('RGBA')
    b = Image.new('RGBA', im.size, (255, 255, 255, 255)); b.alpha_composite(im)
    s = im.width / 512
    return crop(b.convert('RGB'), ((cx - HALF) * s, (cy - HALF) * s, (cx + HALF) * s, (cy + HALF) * s))

base = render('_base', None)
where = json.load(open(os.path.join(base, 'where.json')))
names = ['sony', 'now (57)'] + [f'{c[0]:g} deg' + (f' cap {c[1]:g}' if len(c) > 1 else '') for c in cands]
sheet = Image.new('RGB', (len(VIEWS) * (T + 6) + 90, len(names) * (T + 6)), (235, 235, 235))
dr = ImageDraw.Draw(sheet)
for r, name in enumerate(names):
    d = None if name == 'sony' else (base if r == 1 else render(f'deg{degs[r - 2]:g}', degs[r - 2]))
    for c, k in enumerate(VIEWS):
        cx, cy = where[k][0]
        sheet.paste(sony(k, cx, cy) if d is None else model(d, k, cx, cy), (90 + c * (T + 6), r * (T + 6)))
    dr.text((4, r * (T + 6) + 4), name, fill=(0, 0, 0), font=FS)
sheet.save(os.path.join(out, 'sweep.jpg'), quality=90)
print(os.path.join(out, 'sweep.jpg'))
