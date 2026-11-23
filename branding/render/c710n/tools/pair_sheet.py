# Sony (top row) vs model (bottom row) for chosen review ids, composited on white. (Tools only.)
#   python pair_sheet.py <iterDir> <out.jpg> id [id...]
import sys
from PIL import Image
it, out, ks = sys.argv[1], sys.argv[2], sys.argv[3:]
N = 380
def ow(p):
    im = Image.open(p).convert('RGBA').resize((N, N), Image.LANCZOS)
    b = Image.new('RGBA', im.size, (255, 255, 255, 255)); b.alpha_composite(im); return b.convert('RGB')
sh = Image.new('RGB', (N * len(ks), 2 * N), 'white')
for i, k in enumerate(ks):
    sh.paste(ow(f'_fit/refs/{k}_rgb.png'), (i * N, 0)); sh.paste(ow(f'{it}/{k}_model.png'), (i * N, N))
sh.save(out, quality=90)
