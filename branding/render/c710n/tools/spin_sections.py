# Section table of the right earbud's body from the 360 visual hull (units of body height H; frame:
# x tip side, y up, z out of the touch face, origin at the face disc centre). The ear tip and nozzle
# are cut away first (a cylinder round the tip axis). Prints, per depth station, the section's centre
# and its radius every 22.5 degrees round that centre: the measured numbers behind BODY_SECTIONS in
# c710n.js (copied by hand, then refined in the review loop). Also writes a section sheet.
#   python spin_sections.py
import os
import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage as ndi

HERE = os.path.dirname(os.path.abspath(__file__))
L = np.load(os.path.join(HERE, '..', '_fit', 'hull_bud_c.npy')).astype(np.float64)
# ear tip and nozzle: a cylinder of radius 0.25 round the tip axis, from the tip front back to the body
P0 = np.array([0.62, -0.26, -1.0]); d = np.array([0.50, 0.02, -0.87]); d /= np.linalg.norm(d)
v = L - P0; along = v @ d; perp = np.linalg.norm(v - np.outer(along, d), axis=1)
tip = (perp < 0.25) & (along > -0.62) & (L[:, 2] < -0.50)
B = L[~tip]
print('points', len(L), 'tip removed', tip.sum())
S = 300
stations = [0.0, -0.03, -0.07, -0.12, -0.18, -0.25, -0.32, -0.40, -0.48, -0.55, -0.61, -0.66, -0.70, -0.735]
angles = np.arange(0, 360, 22.5)
sheet = Image.new('RGB', (7 * 220, 2 * 220), (0, 0, 0)); sd = ImageDraw.Draw(sheet)
rows = []
for n, z in enumerate(stations):
    q = np.abs(B[:, 2] - z) < 0.006
    img = np.zeros((2 * S, 2 * S), bool)
    u = np.round((B[q, 0] + 1) * S).astype(int); w = np.round((1 - B[q, 1]) * S).astype(int)
    ok = (u >= 0) & (u < 2 * S) & (w >= 0) & (w < 2 * S); img[w[ok], u[ok]] = True
    img = ndi.binary_fill_holes(ndi.binary_closing(img, iterations=3))
    lab, k = ndi.label(img)
    if k > 1:
        sz = ndi.sum(img, lab, range(1, k + 1)); img = lab == (np.argmax(sz) + 1)
    yy, xx = np.nonzero(img); X = xx / S - 1; Y = 1 - yy / S
    # a smooth centre path (the measured centroids drift along this line down to z = -0.48)
    cx, cy = -0.13 * z, 0.14 * z
    th = np.degrees(np.arctan2(Y - cy, X - cx)) % 360; r = np.hypot(X - cx, Y - cy)
    sel = [np.abs(((th - a + 180) % 360) - 180) < 2.0 for a in angles]
    rs = np.array([r[m].max() if m.any() else 0.0 for m in sel])
    # where the cut-away tip bit into the section, interpolate round the gap from its neighbours
    bad = rs <= 0
    if z < -0.5:
        for i, a in enumerate(angles):
            ex, ey = np.cos(np.radians(a)), np.sin(np.radians(a))
            ss = np.linspace(0, 0.8, 160); px, py = cx + ex * ss, cy + ey * ss
            pts = np.stack([px, py, np.full_like(px, z)], 1) - P0
            al = pts @ d; pe = np.linalg.norm(pts - np.outer(al, d), axis=1)
            if ((pe < 0.25) & (al > -0.62)).any(): bad[i] = True
    if bad.any():
        good = np.where(~bad)[0]
        if len(good) > 2:
            ang_g = angles[good]; r_g = rs[good]
            rs[bad] = np.interp(angles[bad], np.concatenate([ang_g - 360, ang_g, ang_g + 360]), np.tile(r_g, 3))
    rows.append((z, cx, cy, list(rs), bad))
    t = Image.fromarray((img * 255).astype(np.uint8)).resize((220, 220)).convert('RGB')
    td = ImageDraw.Draw(t); td.text((4, 4), 'z=%.3f' % z, fill=(255, 255, 0))
    sheet.paste(t, ((n % 7) * 220, (n // 7) * 220))
sheet.save(os.path.join(HERE, '..', '_fit', 'sections.png'))
print('// [z, cx, cy, r at 0, 22.5, 45 ... 337.5 deg]')
for z, cx, cy, rs, bad in rows:
    print('[%.3f, %.3f, %.3f, %s],%s' % (z, cx, cy, ', '.join('%.3f' % x for x in rs), '  // filled: ' + ' '.join(str(int(a)) for a in angles[bad]) if bad.any() else ''))
