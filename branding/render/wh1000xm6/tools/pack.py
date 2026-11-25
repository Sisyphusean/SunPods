import sys, os
import numpy as np
from PIL import Image

src, dst = sys.argv[1], sys.argv[2]
size = int(sys.argv[3]) if len(sys.argv) > 3 else 480
step = int(sys.argv[4]) if len(sys.argv) > 4 else 1
method = int(sys.argv[5]) if len(sys.argv) > 5 else 6
files = sorted(f for f in os.listdir(src) if f.endswith('.png'))[::step]
frames = [Image.open(os.path.join(src, f)).convert('RGBA').resize((size, size), Image.LANCZOS) for f in files]

# Verify loop continuity and 0 edge clipping
first_a = np.asarray(frames[0], dtype=np.float32)
last_a = np.asarray(frames[-1], dtype=np.float32)
loop_diff = float(np.abs(first_a - last_a).mean())
clipped = 0
for idx, fr in enumerate(frames):
    a = np.asarray(fr)[..., 3]
    if a[0, :].max() > 16 or a[-1, :].max() > 16 or a[:, 0].max() > 16 or a[:, -1].max() > 16:
        clipped += 1
print(f'{os.path.basename(dst)}: frames={len(frames)} clipped={clipped} loop_mean_diff={loop_diff:.3f}')

frames[0].save(dst, save_all=True, append_images=frames[1:], duration=int(1000 / 30 * step), loop=0,
               quality=82, method=method, lossless=False, allow_mixed=False)
print(dst, len(frames), 'frames', os.path.getsize(dst) // 1024, 'KB')

bg = (20, 18, 24, 255)
pick = frames[::max(1, len(frames) // 12)][:12]
cols = 6
sheet = Image.new('RGBA', (cols * size, ((len(pick) + cols - 1) // cols) * size), bg)
for i, f in enumerate(pick):
    tile = Image.new('RGBA', (size, size), bg)
    tile.alpha_composite(f)
    sheet.paste(tile, ((i % cols) * size, (i // cols) * size))
sheet.convert('RGB').save(os.path.splitext(dst)[0] + '_sheet.jpg', quality=85)
