# Measures the WF-C710N earbud from Sony's 360 viewer frames (ref_blue/spin360, frames 03..11 show
# the right earbud turning about a vertical axis). Fits the turntable angle of each frame and the
# axis position by visual-hull consistency, then reports the hull's measurements (no geometry is
# exported: the numbers guide the hand-set curves in c710n.js).
#   python spin_hull.py            -> prints fitted angles, IoU per frame, slices; writes _fit/spin_hull.npz
import os, glob, json, sys
import numpy as np
from PIL import Image
from scipy import ndimage as ndi
from scipy.optimize import minimize

HERE = os.path.dirname(os.path.abspath(__file__))
C7 = os.path.normpath(os.path.join(HERE, '..'))
SPIN = os.path.join(C7, 'ref_blue', 'spin360')
OUT = os.path.join(C7, '_fit'); os.makedirs(OUT, exist_ok=True)
DS = 8

def mask_of(p):
    a = np.asarray(Image.open(p).convert('RGB')).astype(int)
    m = (a.min(2) < 242) | ((a.max(2) - a.min(2)) > 14)
    m = ndi.binary_fill_holes(ndi.binary_closing(m, iterations=3))
    lab, n = ndi.label(m); sz = ndi.sum(m, lab, range(1, n + 1)); m = lab == (np.argmax(sz) + 1)
    im = Image.fromarray((m * 255).astype(np.uint8)).resize((m.shape[1] // DS, m.shape[0] // DS), Image.BILINEAR)
    return np.asarray(im) > 127

files = sorted(glob.glob(os.path.join(SPIN, 's360_*.png')))[3:]
masks = [mask_of(p) for p in files]
H, W = masks[0].shape
rows = np.where(np.any(np.stack(masks), axis=(0, 2)))[0]
y0, y1 = rows.min() - 2, rows.max() + 3
# voxel grid in pixel units (DS-scaled), centred on a guessed axis
G = 1.5
xs = np.arange(-90, 90, G); zs = np.arange(-90, 90, G); ys = np.arange(y0, y1, G)
X, Z = np.meshgrid(xs, zs, indexing='ij')

def carve(angles, c):
    keep = np.ones((len(xs), len(zs), len(ys)), bool)
    for m, a in zip(masks, angles):
        u = c + X * np.cos(a) + Z * np.sin(a)
        ui = np.clip(np.round(u).astype(int), 0, W - 1)
        yi = np.clip(np.round(ys).astype(int), 0, H - 1)
        keep &= m[yi[None, None, :], ui[:, :, None]] & ((u >= 0) & (u < W))[:, :, None]
    return keep

def project(keep, a, c):
    img = np.zeros((H, W), bool)
    ii, jj, kk = np.where(keep)
    u = np.round(c + xs[ii] * np.cos(a) + zs[jj] * np.sin(a)).astype(int)
    v = np.round(ys[kk]).astype(int)
    ok = (u >= 0) & (u < W) & (v >= 0) & (v < H)
    img[v[ok], u[ok]] = True
    return ndi.binary_closing(img, iterations=2)

def score(params):
    c = params[0]; angles = np.concatenate([[0.0], params[1:]])
    keep = carve(angles, c)
    ious = []
    for m, a in zip(masks, angles):
        p = project(keep, a, c)
        ious.append((p & m).sum() / max((p | m).sum(), 1))
    return np.mean(ious), ious, keep

if __name__ == '__main__':
    cx = np.mean([np.where(m.any(0))[0].mean() for m in masks])
    best = None
    for step in np.radians([36, 38, 40, 42, 44, 45]):
        for off in (-15, 0, 15):
            p = np.concatenate([[cx + off], step * np.arange(1, len(masks))])
            s = score(p)[0]
            if best is None or s > best[0]: best = (s, p)
    print('coarse', round(best[0], 4), 'step', np.degrees(best[1][2] - best[1][1]).round(1), 'c', best[1][0].round(1))
    res = minimize(lambda q: -score(q)[0], best[1], method='Powell', options={'maxiter': 3000, 'xtol': 1e-3, 'ftol': 1e-5})
    s, ious, keep = score(res.x)
    ang = np.degrees(np.concatenate([[0], res.x[1:]]))
    print('fit mean IoU', round(s, 4), 'axis c', res.x[0].round(2))
    print('angles deg', ang.round(1))
    print('IoU per frame', np.round(ious, 3))
    np.savez(os.path.join(OUT, 'spin_hull.npz'), keep=keep, xs=xs, zs=zs, ys=ys, angles=np.radians(ang), c=res.x[0], ds=DS)
    json.dump({'angles_deg': ang.tolist(), 'axis_c_px': float(res.x[0] * DS), 'iou': ious, 'files': [os.path.basename(f) for f in files]},
              open(os.path.join(OUT, 'spin_fit.json'), 'w'), indent=1)
