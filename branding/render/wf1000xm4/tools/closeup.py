# Close-up board (tools only): Sony (left) beside the model (right) at the same fitted camera, cropped
# round each feature: the whole earbud body (the round outer disc), the copper microphone, the ear tip,
# the slit microphone and the SONY print.
#   python closeup.py <renderDir> <out.jpg> [label] [features]   features: body,boss,tip,slit,logo
import os, sys, json
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
X4 = os.path.normpath(os.path.join(HERE, '..'))
rd, out = sys.argv[1], sys.argv[2]
label = sys.argv[3] if len(sys.argv) > 3 else ''
want = (sys.argv[4] if len(sys.argv) > 4 else 'body,boss,tip,slit,logo').split(',')
meta = json.load(open(os.path.join(X4, '_fit', 'refs', 'meta.json')))
where = json.load(open(os.path.join(rd, 'where.json')))
try:
    F = ImageFont.truetype('arial.ttf', 18); FS = ImageFont.truetype('arial.ttf', 15)
except OSError:
    F = FS = None
T = 360
FEAT = {'body': (0, 200), 'tip': (1, 90), 'boss': (2, 60), 'slit': (3, 60), 'logo': (4, 75)}   # (point index, crop half-size in 512 board px)
# which features each angle shows
SHOWS = {'face_R': 'body,boss,slit,logo,tip', 'face3q_R': 'body,boss,slit,logo', 'face_R_silver': 'body,boss,slit',
         'side_L': 'body,tip,boss,logo', 'side2_L': 'tip,boss', 'side_L_silver': 'body,tip,logo', 'ang_L': 'body,boss,slit,logo,tip'}

def crop(im, s, cx, cy, half):
    pad = 3000
    bg = Image.new('RGB', (im.width + 2 * pad, im.height + 2 * pad), (255, 255, 255)); bg.paste(im, (pad, pad))
    box = ((cx - half) * s + pad, (cy - half) * s + pad, (cx + half) * s + pad, (cy + half) * s + pad)
    return bg.crop(tuple(int(round(v)) for v in box)).resize((T, T), Image.LANCZOS)

def sony(k, cx, cy, half):
    m = meta[k]
    im = Image.open(os.path.join(X4, m['img'])).convert('RGB')
    x0, y0, x1, y1 = m['box']; s = (x1 - x0) / 512
    sub = crop(im, s, cx + x0 / s, cy + y0 / s, half)
    return sub

def model(k, cx, cy, half):
    im = Image.open(os.path.join(rd, f'{k}_model.png')).convert('RGBA')
    b = Image.new('RGBA', im.size, (255, 255, 255, 255)); b.alpha_composite(im)
    return crop(b.convert('RGB'), im.width / 512, cx, cy, half)

rows = []
for k, pts in where.items():
    for f in SHOWS.get(k, 'body').split(','):
        if f in want: rows.append((k, f, pts[FEAT[f][0]], FEAT[f][1]))
cols = 2
nr = (len(rows) + cols - 1) // cols
sheet = Image.new('RGB', (cols * (2 * T + 30), nr * (T + 30) + 40), (235, 235, 235))
d = ImageDraw.Draw(sheet)
d.text((8, 8), f'{label} close-ups: Sony (left) | model at the same fitted camera (right)', fill=(0, 0, 0), font=F)
for n, (k, what, c, half) in enumerate(rows):
    r, q = n // cols, n % cols
    x, y = q * (2 * T + 30), 40 + r * (T + 30)
    sheet.paste(sony(k, c[0], c[1], half), (x, y + 24)); sheet.paste(model(k, c[0], c[1], half), (x + T + 6, y + 24))
    d.text((x + 4, y + 4), f"{k} ({meta[k]['color']}): {what}", fill=(0, 0, 0), font=FS)
sheet.save(out, quality=90)
print(out, sheet.size)
