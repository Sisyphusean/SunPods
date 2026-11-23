# Composes an iteration's review sheets from board.mjs output:
#   <iterDir>/board_<group>.jpg     Sony (left) and the model at the fitted camera (right), per angle
#   <iterDir>/overlay_<group>.jpg   silhouettes: red = Sony only, blue = ours only, grey = both; IoU per angle
#   <iterDir>/refsheet_<group>.jpg  the Sony angles alone, larger
#   <iterDir>/iou_<group>.txt
#   python compose_board.py <iterDir> <buds|case> [label]
import os, sys, json
import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
C7 = os.path.normpath(os.path.join(HERE, '..'))
REFS = os.path.join(C7, '_fit', 'refs')
it, group = sys.argv[1], sys.argv[2]
label = sys.argv[3] if len(sys.argv) > 3 else os.path.basename(os.path.normpath(it))
fits = json.load(open(os.path.join(it, 'fits.json')))
meta = json.load(open(os.path.join(REFS, 'meta.json')))
ids = [k for k in meta if meta[k]['group'] == group and k in fits]
try:
    F = ImageFont.truetype('arial.ttf', 18); FS = ImageFont.truetype('arial.ttf', 15)
except OSError:
    F = FS = None
BG = (255, 255, 255)

def on_white(p, size):
    im = Image.open(p).convert('RGBA')
    b = Image.new('RGBA', im.size, BG + (255,)); b.alpha_composite(im)
    return b.convert('RGB').resize((size, size), Image.LANCZOS)

T = 300
cols = 2 if len(ids) > 8 else 1
rows = (len(ids) + cols - 1) // cols
board = Image.new('RGB', (cols * (2 * T + 30), rows * (T + 28) + 40), (235, 235, 235))
d = ImageDraw.Draw(board)
d.text((8, 8), f'{label}: Sony (left) | model at silhouette-fitted camera (right)', fill=(0, 0, 0), font=F)
ov_T = 240
ocols = 5
orows = (len(ids) + ocols - 1) // ocols
ov = Image.new('RGB', (ocols * (ov_T + 8), orows * (ov_T + 26) + 40), (0, 0, 0))
od = ImageDraw.Draw(ov)
od.text((8, 8), f'{label}: red = Sony only, blue = ours only, grey = both', fill=(255, 255, 255), font=F)
ref_sheet = Image.new('RGB', (ocols * (ov_T + 8), orows * (ov_T + 26) + 40), (255, 255, 255))
rd = ImageDraw.Draw(ref_sheet)
rd.text((8, 8), 'Sony reference angles', fill=(0, 0, 0), font=F)
lines = []
for n, k in enumerate(ids):
    r, c = n // cols, n % cols
    x, y = c * (2 * T + 30), 40 + r * (T + 28)
    board.paste(on_white(os.path.join(REFS, k + '_rgb.png'), T), (x, y + 22))
    board.paste(on_white(os.path.join(it, k + '_model.png'), T), (x + T + 4, y + 22))
    d.text((x + 4, y + 2), f"{k}  ({meta[k]['color']})  IoU {fits[k]['iou']:.3f}", fill=(0, 0, 0), font=FS)
    a = np.asarray(Image.open(os.path.join(REFS, k + '_mask512.png')).convert('L')) > 127
    b = np.asarray(Image.open(os.path.join(it, k + '_mask.png')).convert('L')) > 127
    img = np.zeros(a.shape + (3,), np.uint8)
    img[a & b] = (150, 150, 150); img[a & ~b] = (230, 40, 40); img[~a & b] = (50, 110, 255)
    iou = (a & b).sum() / max((a | b).sum(), 1)
    oc, orr = n % ocols, n // ocols
    ox, oy = oc * (ov_T + 8), 40 + orr * (ov_T + 26)
    ov.paste(Image.fromarray(img).resize((ov_T, ov_T), Image.NEAREST), (ox, oy + 22))
    od.text((ox + 4, oy + 3), f'{k}  IoU {iou:.3f}', fill=(255, 255, 255), font=FS)
    ref_sheet.paste(on_white(os.path.join(REFS, k + '_rgb.png'), ov_T), (ox, oy + 22))
    rd.text((ox + 4, oy + 3), f"{k} ({meta[k]['color']})", fill=(0, 0, 0), font=FS)
    lines.append(f'{k}\t{iou:.4f}')
mean = np.mean([float(l.split('\t')[1]) for l in lines])
lines.append(f'mean\t{mean:.4f}')
board.save(os.path.join(it, f'board_{group}.jpg'), quality=90)
ov.save(os.path.join(it, f'overlay_{group}.jpg'), quality=92)
ref_sheet.save(os.path.join(it, f'refsheet_{group}.jpg'), quality=90)
open(os.path.join(it, f'iou_{group}.txt'), 'w').write('\n'.join(lines) + '\n')
print('\n'.join(lines))
