# Face-axis profiles of the right earbud from the hull points (spin_measure.py output), in units of
# the body height H: the face-on outline circle (upper half), and rho(theta, depth) tables.
# Measurement only (numbers are copied by hand into c710n.js).
import os
import numpy as np
from scipy.optimize import least_squares
from scipy import ndimage as ndi

HERE = os.path.dirname(os.path.abspath(__file__))
L = np.load(os.path.join(HERE, '..', '_fit', 'hull_bud_frame.npy'))   # x tip side, y up, z out of the face
# keep only surface-ish points: subsample voxels on a 3D grid boundary by binning
R = 400
q = np.round(L * R).astype(int)
q -= q.min(0)
vol = np.zeros(q.max(0) + 1, bool); vol[q[:, 0], q[:, 1], q[:, 2]] = True
surf = vol & ~ndi.binary_erosion(vol)
S = np.argwhere(surf).astype(float)
S = (S + (np.round(L * R).astype(int).min(0))) / R
print('surface points', len(S))
# face-on projection image
img = vol.any(2)
ox, oy = np.round(L * R).astype(int).min(0)[:2]
def outline(c, ths):
    ys, xs = np.nonzero(img)
    P = np.stack([(ys + ox) / R, (xs + oy) / R], 1)   # (x, y) in H units
    d = P - c; th = np.degrees(np.arctan2(d[:, 1], d[:, 0])) % 360; r = np.hypot(d[:, 0], d[:, 1])
    return np.array([r[np.abs(((th - t + 180) % 360) - 180) < 1.5].max() for t in ths])
ths = np.arange(30, 190, 10)
c = np.array([L[:, 0].mean(), L[:, 1].mean()])
sol = least_squares(lambda p: outline(p[:2], ths) - p[2], np.concatenate([c, [0.45]]))
c = sol.x[:2]
print('upper outline circle centre', c.round(4), 'radius', sol.x[2].round(4), 'max resid', np.abs(sol.fun).max().round(4))
print('body top above centre', (L[:, 1].max() - c[1]).round(4), ' bottom below', (c[1] - L[:, 1].min()).round(4))
print('x left of centre', (c[0] - L[:, 0].min()).round(4), ' x right', (L[:, 0].max() - c[0]).round(4))
allth = np.arange(0, 360, 15)
print('face-on outline rho:', ' '.join('%d:%.3f' % (t, r) for t, r in zip(allth, outline(c, allth))))
S[:, 0] -= c[0]; S[:, 1] -= c[1]
np.save(os.path.join(HERE, '..', '_fit', 'hull_surface_c.npy'), S)
th = np.degrees(np.arctan2(S[:, 1], S[:, 0])) % 360; rho = np.hypot(S[:, 0], S[:, 1])
zcols = [0, -0.02, -0.04, -0.06, -0.09, -0.12, -0.16, -0.2, -0.25, -0.3, -0.35, -0.4, -0.45, -0.5, -0.55, -0.6, -0.65, -0.7, -0.75, -0.8]
print('   th ' + ' '.join('%6.2f' % z for z in zcols))
for t0 in range(0, 360, 30):
    s = np.abs(((th - t0 + 180) % 360) - 180) < 4
    row = []
    for z in zcols:
        qq = s & (np.abs(S[:, 2] - z) < 0.008)
        row.append(rho[qq].max() if qq.any() else 0)
    print('  %3d ' % t0 + ' '.join('%6.3f' % r for r in row))
print('z min', S[:, 2].min().round(3))
