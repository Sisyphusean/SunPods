# Everything the app shows of the WH-1000XM5, from the current wh1000xm5.js, in a few minutes (tools only):
#   python render_all.py [colours...]
# 1. stills, the open clips and the pairing clips render at once (three headless Chromes, GPU)
# 2. stills are framed into drawable-nodpi; the first-launch hero pictures go to assets/turntable
# 3. all clips pack in parallel (WebP method 4: same quality as method 6, ~35x faster) into res/raw
# 4. one review sheet: every still and five frames of every clip -> ../review_now.jpg
import os, sys, time, json, shutil, subprocess
from concurrent.futures import ThreadPoolExecutor
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
XM = os.path.normpath(os.path.join(HERE, '..'))
REPO = os.path.normpath(os.path.join(XM, '..', '..', '..'))
APP = os.path.join(REPO, 'app', 'src', 'main')
PACK = os.path.join(REPO, 'branding', 'render', 'anim', 'pack.py')
cols = sys.argv[1:] or ['black', 'silver', 'blue', 'pink']
STILLS, CLIPS = os.path.join(XM, 'stills_now'), os.path.join(XM, 'clips_now')
t0 = time.time()
lap = lambda what: print(f'{time.time() - t0:6.1f}s  {what}', flush=True)

SHEET_ONLY = os.environ.get('XM_SHEET_ONLY') == '1'   # tools: rebuild the review sheet from the last run
if not SHEET_ONLY:
    for d in (STILLS, CLIPS): shutil.rmtree(d, ignore_errors=True)
    # the pairing clips are the longest: their colours split over two Chromes
    half = (len(cols) + 1) // 2
    jobs = [['node', 'stills.mjs', STILLS, *cols], ['node', 'clips.mjs', CLIPS, 'open', *cols],
            ['node', 'clips.mjs', CLIPS, 'pair', *cols[:half]]] + ([['node', 'clips.mjs', CLIPS, 'pair', *cols[half:]]] if cols[half:] else [])
    procs = [subprocess.Popen(j, cwd=HERE, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True) for j in jobs]
    for p, j in zip(procs, jobs):
        if p.wait() != 0: sys.exit(f'{" ".join(j[1:3])} failed:\n{p.stderr.read()[-2000:]}')
    lap('rendered stills and clip frames')

if not SHEET_ONLY:
    subprocess.run([sys.executable, 'compose_stills.py', STILLS, *cols], cwd=HERE, check=True, stdout=subprocess.DEVNULL)
    for c in cols:   # first-launch hero picture: the hero frame itself, at the app's 1000 x 712
        Image.open(os.path.join(STILLS, f'pair_{c}.png')).convert('RGBA').resize((1000, 712), Image.LANCZOS) \
            .save(os.path.join(APP, 'assets', 'turntable', f'wh1000xm5_{c}.webp'), 'WEBP', quality=88, method=6)
    lap('stills and first-launch pictures in the app')

    def pack(job):
        clip, n, c = job
        src = os.path.join(CLIPS, f'{clip}_{c}')
        got = len([f for f in os.listdir(src) if f.endswith('.png')])
        assert got == n, (src, got, n)
        dst = os.path.join(CLIPS, f'wh1000xm5_anim_{clip}_{c}.webp')
        subprocess.run([sys.executable, PACK, src, dst, '480', '1', '4'], check=True, stdout=subprocess.DEVNULL)
        shutil.copyfile(dst, os.path.join(APP, 'res', 'raw', os.path.basename(dst)))
        return f'{os.path.basename(dst)} {os.path.getsize(dst) // 1024} KB'
    with ThreadPoolExecutor(8) as ex:
        sizes = list(ex.map(pack, [(k, n, c) for k, n in (('open', 120), ('pair', 210)) for c in cols]))
    lap('clips packed into the app: ' + ', '.join(sizes))

# review sheet on the app's dark background
BG = (20, 18, 24)
T = 200
rows = []
for c in cols:
    row = [Image.open(os.path.join(APP, 'res', 'drawable-nodpi', f'wh1000xm5_{n}_{c}.png')) for n in ('pair', 'side', 'front', 'case')]
    for clip in ('open', 'pair'):
        im = Image.open(os.path.join(CLIPS, f'wh1000xm5_anim_{clip}_{c}.webp'))
        for k in range(5):
            im.seek(int(k * (im.n_frames - 1) / 4)); row.append(im.convert('RGBA').copy())
    rows.append(row)
sheet = Image.new('RGB', (14 * (T + 4), len(rows) * (T + 4)), BG)
for r, row in enumerate(rows):
    for i, im in enumerate(row):
        im = im.convert('RGBA'); im.thumbnail((T, T), Image.LANCZOS)
        tile = Image.new('RGBA', (T, T), BG + (255,)); tile.alpha_composite(im, ((T - im.width) // 2, (T - im.height) // 2))
        sheet.paste(tile.convert('RGB'), (i * (T + 4), r * (T + 4)))
sheet.save(os.path.join(XM, 'review_now.jpg'), quality=88)
lap('review sheet: ' + os.path.join(XM, 'review_now.jpg'))
