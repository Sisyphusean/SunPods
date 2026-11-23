# Grille trials side by side (tools only): rows = trial render dirs, columns = views, Sony | model.
#   python grille_sheet.py <out.jpg> <dir> [dir...]
import sys, os, json
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__)); C7 = os.path.normpath(os.path.join(HERE, '..'))
meta = json.load(open(os.path.join(C7, '_fit', 'refs', 'meta.json')))
out, dirs = sys.argv[1], sys.argv[2:]
views = ['side_L', 'side_L_pink', 'front_R_white', 'front_R']
T, H = 240, 60
def sony(k, cx, cy):
    m = meta[k]; im = Image.open(os.path.join(C7, m['img'])).convert('RGBA')
    if 'crop' in m: im = im.crop(tuple(m['crop']))
    b = Image.new('RGBA', im.size, (255, 255, 255, 255)); b.alpha_composite(im); im = b.convert('RGB')
    x0, y0, x1, y1 = m['box']; s = (x1 - x0) / 512
    pad = Image.new('RGB', (im.width + 4000, im.height + 4000), 'white'); pad.paste(im, (2000, 2000))
    return pad.crop(tuple(int(round(v + 2000)) for v in (x0 + (cx - H) * s, y0 + (cy - H) * s, x0 + (cx + H) * s, y0 + (cy + H) * s))).resize((T, T), Image.LANCZOS)
def model(d, k, cx, cy):
    im = Image.open(os.path.join(d, f'{k}_model.png')).convert('RGBA'); b = Image.new('RGBA', im.size, (255, 255, 255, 255)); b.alpha_composite(im); im = b.convert('RGB')
    s = im.width / 512
    pad = Image.new('RGB', (im.width + 2000, im.height + 2000), 'white'); pad.paste(im, (1000, 1000))
    return pad.crop(tuple(int(round(v * s + 1000)) for v in (cx - H, cy - H, cx + H, cy + H))).resize((T, T), Image.LANCZOS)
sheet = Image.new('RGB', (len(views) * (2 * T + 10), len(dirs) * (T + 20)), (230, 230, 230)); dr = ImageDraw.Draw(sheet)
for r, d in enumerate(dirs):
    w = json.load(open(os.path.join(d, 'where.json')))
    for c, k in enumerate(views):
        if k not in w: continue
        gx, gy = w[k][1]; x, y = c * (2 * T + 10), r * (T + 20) + 18
        sheet.paste(sony(k, gx, gy), (x, y)); sheet.paste(model(d, k, gx, gy), (x + T, y))
    dr.text((4, r * (T + 20) + 2), os.path.basename(d), fill=(0, 0, 0))
sheet.save(out, quality=90); print(out)
