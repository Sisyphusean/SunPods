# Compares Sony and model colour inside the shared silhouette (Glass Blue refs): edge band vs interior,
# composited on white. python glass_stats.py <iterDir> id [id...]   (tools only)
import sys, os
import numpy as np
from PIL import Image
from scipy import ndimage
it = sys.argv[1]
def on_white(p, size):
    im = Image.open(p).convert('RGBA').resize((size, size), Image.LANCZOS)
    b = Image.new('RGBA', im.size, (255, 255, 255, 255)); b.alpha_composite(im)
    return np.asarray(b.convert('RGB')).astype(float)
for k in sys.argv[2:]:
    a = np.asarray(Image.open(f'_fit/refs/{k}_mask512.png').convert('L')) > 127
    b = np.asarray(Image.open(f'{it}/{k}_mask.png').convert('L')) > 127
    n = a.shape[0]
    S = on_white(f'_fit/refs/{k}_rgb.png', n); M = on_white(f'{it}/{k}_model.png', n)
    both = a & b
    d = ndimage.distance_transform_edt(both)
    edge = both & (d <= n * 0.025); mid = both & (d > n * 0.06)
    def st(img, m):
        v = img[m]; return np.median(v, 0).astype(int), np.percentile(v.mean(1), [10, 50, 90]).astype(int)
    print(k, 'edge S', *st(S, edge), 'M', *st(M, edge))
    print(k, 'mid  S', *st(S, mid), 'M', *st(M, mid))
