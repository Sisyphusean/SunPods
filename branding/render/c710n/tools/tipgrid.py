# Gridded fixed-window crops round the ear tip: Sony (top) and model renders (rows below) (tools only).
#   python tipgrid.py <out.jpg> <renderDir> [renderDir...]
import sys, os, json
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__)); C7 = os.path.normpath(os.path.join(HERE, '..'))
m = json.load(open(os.path.join(C7, '_fit', 'refs', 'meta.json')))
out, dirs = sys.argv[1], sys.argv[2:]
WIN = {'side_L': (140, 331), 'side_L_pink': (140, 330), 'spin10': (330, 324), 'spin07': (170, 323), 'spin09': (300, 321), 'front_R_white': (360, 336)}
H, T = 120, 400
def grid(c):
    d = ImageDraw.Draw(c)
    for g in range(0, T, 40): d.line([(g, 0), (g, T)], fill=(255, 0, 0)); d.line([(0, g), (T, g)], fill=(255, 0, 0))
    return c
sheet = Image.new('RGB', (len(WIN) * T, (1 + len(dirs)) * T), 'white')
for i, (k, (cx, cy)) in enumerate(WIN.items()):
    r = m[k]; im = Image.open(os.path.join(C7, r['img'])).convert('RGBA')
    if 'crop' in r: im = im.crop(tuple(r['crop']))
    b = Image.new('RGBA', im.size, (255, 255, 255, 255)); b.alpha_composite(im); im = b.convert('RGB')
    x0, y0, x1, y1 = r['box']; s = (x1 - x0) / 512
    pad = Image.new('RGB', (im.width + 4000, im.height + 4000), 'white'); pad.paste(im, (2000, 2000))
    box = tuple(int(round(v + 2000)) for v in (x0 + (cx - H) * s, y0 + (cy - H) * s, x0 + (cx + H) * s, y0 + (cy + H) * s))
    sheet.paste(grid(pad.crop(box).resize((T, T), Image.LANCZOS)), (i * T, 0))
    for j, dd in enumerate(dirs):
        mi = Image.open(os.path.join(dd, f'{k}_model.png')).convert('RGBA'); b = Image.new('RGBA', mi.size, (255, 255, 255, 255)); b.alpha_composite(mi); mi = b.convert('RGB')
        sc = mi.width / 512
        sheet.paste(grid(mi.crop((int((cx - H) * sc), int((cy - H) * sc), int((cx + H) * sc), int((cy + H) * sc))).resize((T, T), Image.LANCZOS)), (i * T, (j + 1) * T))
sheet.save(out, quality=90); print(out)
