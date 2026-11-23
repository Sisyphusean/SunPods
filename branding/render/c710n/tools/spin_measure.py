# Measurements of the right earbud from the fitted turntable (spin_fit.json): re-carves the visual
# hull at full resolution, finds the touch-face plane, and prints the face-on outline and the
# side profiles around the face axis in units of the body height H. Measurement only: the numbers
# are copied by hand into the curve tables in c710n.js.
#   python spin_measure.py
import os, glob, json
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

HERE = os.path.dirname(os.path.abspath(__file__))
C7 = os.path.normpath(os.path.join(HERE, '..'))
SPIN = os.path.join(C7, 'ref_blue', 'spin360')
FIT = json.load(open(os.path.join(C7, '_fit', 'spin_fit.json')))
DS = 4

def mask_of(p):
    a = np.asarray(Image.open(p).convert('RGB')).astype(int)
    m = (a.min(2) < 242) | ((a.max(2) - a.min(2)) > 14)
    m = ndi.binary_fill_holes(ndi.binary_closing(m, iterations=3))
    lab, n = ndi.label(m); sz = ndi.sum(m, lab, range(1, n + 1)); m = lab == (np.argmax(sz) + 1)
    im = Image.fromarray((m * 255).astype(np.uint8)).resize((m.shape[1] // DS, m.shape[0] // DS), Image.BILINEAR)
    return np.asarray(im) > 127

files = [os.path.join(SPIN, f) for f in FIT['files']]
masks = [mask_of(p) for p in files]
H_, W_ = masks[0].shape
ang = np.radians(FIT['angles_deg']); c = FIT['axis_c_px'] / DS
rows = np.where(np.any(np.stack(masks), axis=(0, 2)))[0]
G = 1.0
xs = np.arange(-180, 180, G); zs = np.arange(-180, 180, G); ys = np.arange(rows.min() - 2, rows.max() + 3, G)
X, Z = np.meshgrid(xs, zs, indexing='ij')
keep = np.ones((len(xs), len(zs), len(ys)), bool)
for m, a in zip(masks, ang):
    u = c + X * np.cos(a) + Z * np.sin(a)
    ui = np.clip(np.round(u).astype(int), 0, W_ - 1)
    yi = np.clip(np.round(ys).astype(int), 0, H_ - 1)
    keep &= m[yi[None, None, :], ui[:, :, None]] & ((u >= 0) & (u < W_))[:, :, None]
ii, jj, kk = np.where(keep)
P = np.stack([xs[ii], -ys[kk], zs[jj]], 1)          # right-handed, y up, z towards the camera at frame 03
Hpx = P[:, 1].max() - P[:, 1].min()
print('voxels', len(P), 'body height px', Hpx)
np.save(os.path.join(C7, '_fit', 'hull_pts.npy'), P)

# touch face: the plane z' = max along a horizontal direction n(alpha); pick alpha where the
# extreme slab is flattest (most points within 1 px of the max)
best = None
for al in np.radians(np.arange(-5, 25, 0.5)):
    n = np.array([np.sin(al), 0, np.cos(al)])
    d = P @ n; top = d.max()
    cnt = (d > top - 1.0).sum()
    if best is None or cnt > best[0]: best = (cnt, al, top)
cnt, al, top = best
n = np.array([np.sin(al), 0, np.cos(al)]); yb = np.array([0, 1.0, 0]); xb = np.cross(yb, n)
print('face normal alpha deg', np.degrees(al).round(2), 'points on face', cnt)
L = np.stack([P @ xb, P @ yb, P @ n - top], 1)       # bud frame: x right (tip side), y up, z out of the face
face = L[L[:, 2] > -1.0]
fc = face[:, :2].mean(0); fr = np.sqrt(((face[:, :2] - fc) ** 2).sum(1)).max()
print('face disc centre (x,y)/H', (fc / Hpx).round(4), 'disc radius/H', round(fr / Hpx, 4))
L[:, 0] -= fc[0]; L[:, 1] -= fc[1]
np.save(os.path.join(C7, '_fit', 'hull_bud_frame.npy'), L / Hpx)
ymin, ymax = L[:, 1].min(), L[:, 1].max()
print('body y range /H', (ymin / Hpx).round(3), (ymax / Hpx).round(3), ' depth z min /H', (L[:, 2].min() / Hpx).round(3))
print('x range /H', (L[:, 0].min() / Hpx).round(3), (L[:, 0].max() / Hpx).round(3))
th = np.degrees(np.arctan2(L[:, 1], L[:, 0])) % 360
rho = np.hypot(L[:, 0], L[:, 1])
print('\nface-on outline: max rho/H per theta (all depths), and depth where reached')
for t0 in range(0, 360, 15):
    s = np.abs(((th - t0 + 180) % 360) - 180) < 3
    k = np.argmax(rho[s]); print('  th %3d  rho %.3f  at z %.3f' % (t0, rho[s][k] / Hpx, L[s][k, 2] / Hpx))
print('\nprofiles: rho/H at depth z/H (columns) for each theta (rows)')
zcols = [0, -0.02, -0.05, -0.08, -0.12, -0.16, -0.2, -0.25, -0.3, -0.35, -0.4, -0.45, -0.5, -0.55, -0.6, -0.65, -0.7]
print('   th ' + ' '.join('%6.2f' % z for z in zcols))
for t0 in range(0, 360, 30):
    s = np.abs(((th - t0 + 180) % 360) - 180) < 4
    row = []
    for z in zcols:
        q = s & (np.abs(L[:, 2] / Hpx - z) < 0.012)
        row.append(rho[q].max() / Hpx if q.any() else 0)
    print('  %3d ' % t0 + ' '.join('%6.3f' % r for r in row))
