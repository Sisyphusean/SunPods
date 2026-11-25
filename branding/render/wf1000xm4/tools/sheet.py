# Contact sheet of PNG renders (tools only): python sheet.py <dir> <out.jpg> [tile] [bg r,g,b]
import os, sys
from PIL import Image, ImageDraw

d, out = sys.argv[1], sys.argv[2]
T = int(sys.argv[3]) if len(sys.argv) > 3 else 300
bg = tuple(int(v) for v in sys.argv[4].split(',')) if len(sys.argv) > 4 else (200, 200, 204)
fs = sorted(f for f in os.listdir(d) if f.endswith('.png'))
cols = min(5, len(fs)) or 1
rows = (len(fs) + cols - 1) // cols
sheet = Image.new('RGB', (cols * T, rows * (T + 14)), bg)
dr = ImageDraw.Draw(sheet)
for i, f in enumerate(fs):
    im = Image.open(os.path.join(d, f)).convert('RGBA')
    bb = im.getchannel('A').getbbox()
    if bb: im = im.crop((max(0, bb[0] - 8), max(0, bb[1] - 8), min(im.width, bb[2] + 8), min(im.height, bb[3] + 8)))
    im.thumbnail((T, T), Image.LANCZOS)
    tile = Image.new('RGBA', (T, T), bg + (255,)); tile.alpha_composite(im, ((T - im.width) // 2, (T - im.height) // 2))
    x, y = (i % cols) * T, (i // cols) * (T + 14)
    sheet.paste(tile.convert('RGB'), (x, y + 14)); dr.text((x + 3, y + 1), f[:-4], fill=(0, 0, 0))
sheet.save(out, quality=90)
print(out, sheet.size)
