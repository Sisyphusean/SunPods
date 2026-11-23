# Shape trials (tools only): fits and renders the review angles for each candidate set of overrides in
# parallel (one headless Chrome each), then one sheet: Sony | each candidate, with IoU per angle.
#   python sweep.py <outDir> <candidates.json> [ids]     candidates: {"name": {"HP": {...}, ...}, ...}
import os, sys, json, subprocess
from concurrent.futures import ThreadPoolExecutor
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
XM = os.path.normpath(os.path.join(HERE, '..'))
out, cands = sys.argv[1], json.load(open(sys.argv[2]))
ids = sys.argv[3] if len(sys.argv) > 3 else 'side_L,tq_L,front'
REFS = os.path.join(XM, '_fit', 'refs')
os.makedirs(out, exist_ok=True)
try:
    FS = ImageFont.truetype('arial.ttf', 14)
except OSError:
    FS = None

def run(name):
    d = os.path.join(out, name)
    env = dict(os.environ, XM_OVR=json.dumps(cands[name]))
    subprocess.run(['node', 'board.mjs', d, ids], cwd=HERE, env=env, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return name, json.load(open(os.path.join(d, 'fits.json')))

with ThreadPoolExecutor(3) as ex:
    res = dict(ex.map(run, list(cands)))
idl = ids.split(',')
T = 260
sheet = Image.new('RGB', (len(idl) * (T + 6) + 150, (len(cands) + 1) * (T + 6)), (235, 235, 235))
dr = ImageDraw.Draw(sheet)
def tile(p):
    im = Image.open(p).convert('RGBA'); b = Image.new('RGBA', im.size, (255, 255, 255, 255)); b.alpha_composite(im)
    return b.convert('RGB').resize((T, T), Image.LANCZOS)
for c, k in enumerate(idl):
    sheet.paste(tile(os.path.join(REFS, k + '_rgb.png')), (150 + c * (T + 6), 0))
dr.text((4, 4), 'Sony', fill=(0, 0, 0), font=FS)
for r, name in enumerate(cands):
    f = res[name]
    mean = sum(f[k]['iou'] for k in idl) / len(idl)
    dr.text((4, (r + 1) * (T + 6) + 4), f'{name}\nmean {mean:.3f}', fill=(0, 0, 0), font=FS)
    for c, k in enumerate(idl):
        sheet.paste(tile(os.path.join(out, name, k + '_model.png')), (150 + c * (T + 6), (r + 1) * (T + 6)))
        dr.text((150 + c * (T + 6) + 4, (r + 1) * (T + 6) + 4), f"{f[k]['iou']:.3f}", fill=(200, 0, 0), font=FS)
    print(name, ' '.join(f"{k} {f[k]['iou']:.3f}" for k in idl), f'mean {mean:.3f}')
sheet.save(os.path.join(out, 'sweep.jpg'), quality=88)
print(os.path.join(out, 'sweep.jpg'))
