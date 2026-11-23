# Contact sheet for the onboarding clips: for each clip, one row per finish (black original, silver,
# pink), the same moments in time side by side, on the app's light page colour.
#   python clip_sheet.py <out.jpg>
import os, sys, struct
from PIL import Image, ImageDraw, ImageFont

RAW = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'app', 'src', 'main', 'res', 'raw'))
BG = (243, 240, 235)
TILE = 220


def durations(path):
    b = open(path, 'rb').read(); i = 12; d = []
    while i < len(b):
        cid = b[i:i + 4]; sz = struct.unpack('<I', b[i + 4:i + 8])[0]
        if cid == b'ANMF':
            p = b[i + 8:i + 24]; d.append(p[12] | (p[13] << 8) | (p[14] << 16))
        i += 8 + sz + (sz & 1)
    return d


def frames_at(path, times):
    im = Image.open(path); d = durations(path); out = []
    for t in times:
        acc = 0; k = 0
        while k < len(d) - 1 and acc + d[k] <= t:
            acc += d[k]; k += 1
        im.seek(k); f = im.convert('RGBA')
        tile = Image.new('RGBA', f.size, BG + (255,)); tile.alpha_composite(f)
        out.append(tile.convert('RGB').resize((TILE, TILE), Image.LANCZOS))
    return out, sum(d), len(d)


rows = []
for clip, times in (('pair', [0, 900, 1900, 2500, 3600, 4700, 5500, 6300, 6700]),
                    ('open', [0, 600, 1000, 1400, 1800, 2400, 3000, 3600, 3950])):
    for fin in ('', '_silver', '_pink'):
        p = os.path.join(RAW, 'anim_%s%s.webp' % (clip, fin))
        tiles, total, n = frames_at(p, times)
        rows.append(('anim_%s%s.webp  (%d frames, %d ms)' % (clip, fin, n, total), tiles))
W = TILE * 9 + 8 * 4; H = (TILE + 26) * len(rows)
sheet = Image.new('RGB', (W, H), 'white'); d = ImageDraw.Draw(sheet)
try: font = ImageFont.truetype('arial.ttf', 16)
except OSError: font = None
for r, (label, tiles) in enumerate(rows):
    y = r * (TILE + 26); d.text((4, y + 4), label, fill=(40, 40, 40), font=font)
    for c, t in enumerate(tiles): sheet.paste(t, (c * (TILE + 4), y + 24))
sheet.save(sys.argv[1], quality=90)
print(sys.argv[1], sheet.size)
