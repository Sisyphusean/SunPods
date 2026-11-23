# Clip packing benchmark (tools only): encodes one clip's frames with given WebP settings and reports
# time, size and quality (PSNR against the source frames, composited on the app's dark background).
#   python pack_bench.py <framesDir> <method> [quality] [reference.webp]
import os, sys, time, math
from concurrent.futures import ThreadPoolExecutor
from PIL import Image, ImageChops, ImageStat

src, method = sys.argv[1], int(sys.argv[2])
q = int(sys.argv[3]) if len(sys.argv) > 3 else 82
ref = sys.argv[4] if len(sys.argv) > 4 else None
files = sorted(f for f in os.listdir(src) if f.endswith('.png'))
load = lambda f: Image.open(os.path.join(src, f)).convert('RGBA').resize((480, 480), Image.LANCZOS)
t0 = time.time()
with ThreadPoolExecutor(16) as ex:
    frames = list(ex.map(load, files))
t1 = time.time()
out = os.path.join(os.environ.get('TEMP', '.'), f'pack_m{method}_q{q}.webp')
frames[0].save(out, save_all=True, append_images=frames[1:], duration=33, loop=0, quality=q, method=method, lossless=False, allow_mixed=False)
t2 = time.time()
BG = (20, 18, 24, 255)
def on_bg(im):
    b = Image.new('RGBA', im.size, BG); b.alpha_composite(im.convert('RGBA')); return b.convert('RGB')
def psnr(a, b):
    mse = sum(v * v for v in ImageStat.Stat(ImageChops.difference(a, b)).rms) / 3
    return 99.0 if mse == 0 else 20 * math.log10(255 / math.sqrt(mse))
def quality(path):
    im = Image.open(path); vals = []
    for i in range(0, len(frames), 6):
        im.seek(i); vals.append(psnr(on_bg(im), on_bg(frames[i])))
    return sum(vals) / len(vals), min(vals)
mean, worst = quality(out)
print(f'method {method} q{q}: load {t1 - t0:.1f}s encode {t2 - t1:.1f}s, {os.path.getsize(out) // 1024} KB, PSNR mean {mean:.2f} dB worst {worst:.2f} dB')
if ref:
    rm, rw = quality(ref)
    print(f'reference {os.path.basename(ref)}: {os.path.getsize(ref) // 1024} KB, PSNR mean {rm:.2f} dB worst {rw:.2f} dB')
