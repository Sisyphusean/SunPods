# Grille trials (tools only): renders side_L, front_R and front_R_white for each candidate mic setting
# and puts Sony's grille beside each candidate's, cropped at the same place (the base render's grille).
#   python grille_sweep.py <outDir> <candidates.json>
#   candidates.json: {"name": {"mic": {...BUD.mic overrides...}}, ...}
import os, sys, json, subprocess
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
C7 = os.path.normpath(os.path.join(HERE, '..'))
out, cands = sys.argv[1], json.load(open(sys.argv[2]))
meta = json.load(open(os.path.join(C7, '_fit', 'refs', 'meta.json')))
VIEWS = ['side_L', 'front_R', 'front_R_white']
T, HALF = 230, 70
try:
    FS = ImageFont.truetype('arial.ttf', 14)
except OSError:
    FS = None

def render(name, mic):
    d = os.path.join(out, name)
    env = dict(os.environ)
    if mic: env['C7_OVR'] = json.dumps({'BUD': {'mic': mic}})
    subprocess.run(['node', os.path.join(HERE, 'closeup.mjs'), d, *VIEWS], check=True, env=env, cwd=HERE, stdout=subprocess.DEVNULL)
    return d

def sony(k, cx, cy):
    m = meta[k]
    im = Image.open(os.path.join(C7, m['img'])).convert('RGBA')
    if 'crop' in m: im = im.crop(tuple(m['crop']))
    b = Image.new('RGBA', im.size, (255, 255, 255, 255)); b.alpha_composite(im); im = b.convert('RGB')
    x0, y0, x1, y1 = m['box']; s = (x1 - x0) / 512
    pad = Image.new('RGB', (im.width + 4000, im.height + 4000), (255, 255, 255)); pad.paste(im, (2000, 2000))
    box = (x0 + (cx - HALF) * s + 2000, y0 + (cy - HALF) * s + 2000, x0 + (cx + HALF) * s + 2000, y0 + (cy + HALF) * s + 2000)
    return pad.crop(tuple(int(round(v)) for v in box)).resize((T, T), Image.LANCZOS)

def model(d, k, cx, cy):
    im = Image.open(os.path.join(d, f'{k}_model.png')).convert('RGBA')
    b = Image.new('RGBA', im.size, (255, 255, 255, 255)); b.alpha_composite(im); im = b.convert('RGB')
    s = im.width / 512
    pad = Image.new('RGB', (im.width + 2000, im.height + 2000), (255, 255, 255)); pad.paste(im, (1000, 1000))
    box = ((cx - HALF) * s + 1000, (cy - HALF) * s + 1000, (cx + HALF) * s + 1000, (cy + HALF) * s + 1000)
    return pad.crop(tuple(int(round(v)) for v in box)).resize((T, T), Image.LANCZOS)

base = render('_base', None)
where = json.load(open(os.path.join(base, 'where.json')))
names = ['sony'] + list(cands)
sheet = Image.new('RGB', (len(VIEWS) * (T + 6) + 120, len(names) * (T + 6)), (235, 235, 235))
dr = ImageDraw.Draw(sheet)
for r, name in enumerate(names):
    d = None if name == 'sony' else render(name, cands[name]['mic'])
    for c, k in enumerate(VIEWS):
        cx, cy = where[k][1]
        tile = sony(k, cx, cy) if d is None else model(d, k, cx, cy)
        sheet.paste(tile, (120 + c * (T + 6), r * (T + 6)))
    dr.text((4, r * (T + 6) + 4), name, fill=(0, 0, 0), font=FS)
sheet.save(os.path.join(out, 'sweep.jpg'), quality=90)
print(os.path.join(out, 'sweep.jpg'))
