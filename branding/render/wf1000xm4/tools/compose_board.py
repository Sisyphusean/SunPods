# Review board for one iteration (tools only): per angle, Sony | model at the fitted camera | the two
# silhouettes overlaid (grey both, red Sony only, blue model only) with the IoU.
#   python compose_board.py <iterDir> [out.jpg]
import os, sys, json
import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
X4 = os.path.normpath(os.path.join(HERE, '..'))
it = sys.argv[1]
out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(it, 'board.jpg')
fits = json.load(open(os.path.join(it, 'fits.json')))
T = 300
sheet = Image.new('RGB', (3 * T + 12, len(fits) * (T + 18)), (235, 235, 235))
d = ImageDraw.Draw(sheet)
for r, (k, f) in enumerate(fits.items()):
    y = r * (T + 18)
    ref = Image.open(os.path.join(X4, '_fit', 'refs', f'{k}_ref.png')).convert('RGB').resize((T, T), Image.LANCZOS)
    m = Image.open(os.path.join(it, f'{k}_model.png')).convert('RGBA')
    bg = Image.new('RGBA', m.size, (255, 255, 255, 255)); bg.alpha_composite(m)
    a = np.asarray(Image.open(os.path.join(X4, '_fit', 'refs', f'{k}_mask.png')).convert('L').resize((512, 512))) > 127
    b = np.asarray(Image.open(os.path.join(it, f'{k}_mask.png')).convert('L').resize((512, 512))) > 127
    ov = np.zeros((512, 512, 3), np.uint8); ov[a & b] = (150, 150, 150); ov[a & ~b] = (230, 40, 40); ov[~a & b] = (50, 110, 255)
    sheet.paste(ref, (0, y + 18)); sheet.paste(bg.convert('RGB').resize((T, T), Image.LANCZOS), (T + 6, y + 18))
    sheet.paste(Image.fromarray(ov).resize((T, T)), (2 * T + 12, y + 18))
    d.text((4, y + 3), f"{k}  IoU {f['iou']:.4f}", fill=(0, 0, 0))
sheet.save(out, quality=90)
print(out)
