# Side-by-side sheet: rows of images composited on the app's light page colour.
#   python compare.py out.jpg "label|imgA|imgB|..." "label|imgA|..." ...  (tile height 356)
import sys
from PIL import Image, ImageDraw, ImageFont
BG = (243, 240, 235)
def onbg(p, h):
    im = Image.open(p).convert('RGBA')
    b = Image.new('RGBA', im.size, BG + (255,)); b.alpha_composite(im); b = b.convert('RGB')
    return b.resize((round(b.width * h / b.height), h), Image.LANCZOS)
out, rows = sys.argv[1], sys.argv[2:]
H = 356
tiles = [[onbg(p, H) for p in r.split('|')[1:]] for r in rows]
W = max(sum(t.width for t in row) + 6 * len(row) for row in tiles)
sheet = Image.new('RGB', (W, (H + 26) * len(rows)), 'white')
d = ImageDraw.Draw(sheet)
try: font = ImageFont.truetype('arial.ttf', 17)
except OSError: font = None
for i, (r, row) in enumerate(zip(rows, tiles)):
    y = i * (H + 26); d.text((6, y + 3), r.split('|')[0], fill=(40, 40, 40), font=font); x = 0
    for t in row: sheet.paste(t, (x, y + 24)); x += t.width + 6
sheet.save(out, quality=92)
print(out, sheet.size)
