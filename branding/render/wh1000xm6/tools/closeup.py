import os, sys, json
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
XM = os.path.normpath(os.path.join(HERE, '..'))
rd, out = sys.argv[1], sys.argv[2]
label = sys.argv[3] if len(sys.argv) > 3 else 'WH-1000XM6'
meta = json.load(open(os.path.join(XM, '_fit', 'refs', 'meta.json')))
where = json.load(open(os.path.join(rd, 'where.json')))
try:
    F = ImageFont.truetype('arial.ttf', 18); FS = ImageFont.truetype('arial.ttf', 15)
except OSError:
    F = FS = None
T = 280
HALF = 68

def sony_crop(k, cx, cy):
    m = meta[k]
    im = Image.open(os.path.join(XM, m['img'])).convert('RGBA')
    bg = Image.new('RGBA', im.size, (255, 255, 255, 255)); bg.alpha_composite(im); im = bg.convert('RGB')
    x0, y0, x1, y1 = m['box']; s = (x1 - x0) / 512
    pad = Image.new('RGB', (im.width + 2000, im.height + 2000), (255, 255, 255)); pad.paste(im, (1000, 1000))
    box = (x0 + (cx - HALF) * s + 1000, y0 + (cy - HALF) * s + 1000, x0 + (cx + HALF) * s + 1000, y0 + (cy + HALF) * s + 1000)
    return pad.crop(tuple(int(round(v)) for v in box)).resize((T, T), Image.LANCZOS)

def model_crop(k, cx, cy):
    im = Image.open(os.path.join(rd, f'{k}_model.png')).convert('RGBA')
    b = Image.new('RGBA', im.size, (255, 255, 255, 255)); b.alpha_composite(im); im = b.convert('RGB')
    s = im.width / 512
    pad = Image.new('RGB', (im.width + 2000, im.height + 2000), (255, 255, 255)); pad.paste(im, (1000, 1000))
    box = ((cx - HALF) * s + 1000, (cy - HALF) * s + 1000, (cx + HALF) * s + 1000, (cy + HALF) * s + 1000)
    return pad.crop(tuple(int(round(v)) for v in box)).resize((T, T), Image.LANCZOS)

rows = [(k, lab, p) for k, parts in where.items() for lab, p in parts if -40 < p[0] < 552 and -40 < p[1] < 552]
cols = 3
nr = (len(rows) + cols - 1) // cols
sheet = Image.new('RGB', (cols * (2 * T + 24), nr * (T + 28) + 36), (235, 235, 235))
d = ImageDraw.Draw(sheet)
d.text((8, 8), f'{label} close-ups: Sony (left) | model at fitted camera (right)', fill=(0, 0, 0), font=F)
for n, (k, what, c) in enumerate(rows):
    r, q = n // cols, n % cols
    x, y = q * (2 * T + 24), 36 + r * (T + 28)
    sheet.paste(sony_crop(k, *c), (x, y + 22)); sheet.paste(model_crop(k, *c), (x + T + 4, y + 22))
    d.text((x + 4, y + 3), f"{k} ({meta[k]['color']}): {what}", fill=(0, 0, 0), font=FS)
sheet.save(out, quality=90)
print(out, sheet.size)
