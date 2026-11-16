"""Builds the WF-1000XM5-style bud shell as a signed distance field and meshes it (OBJ).

Object space: face looks along +Z, Y up. Face outline = teardrop (hull of a big top circle
and a small bottom circle), traced from Sony's front reference view. Units: face width = 2.0.
Writes xm5_shell_<side>.obj next to this file. Run: python xm5_shell.py [left|right]
"""
import os, sys
import numpy as np
from skimage import measure

SIDE = sys.argv[1] if len(sys.argv) > 1 else "left"
SX = 1.0 if SIDE == "left" else -1.0
HERE = os.path.dirname(os.path.abspath(__file__))

# --- face outline: uneven capsule (iq), top circle r1 at y=+0.5, bottom circle r2 at y=-0.55
R1, R2, H = 1.0, 0.34, 1.3
RIM = 0.14   # glossy body extends this far beyond the matte panel outline
TOP_Y = 0.5


def teardrop(x, y):
    """2D SDF of the face outline. Negative inside."""
    px = np.abs(x)
    py = TOP_Y - y                         # measure downward from the top circle centre
    b = (R1 - R2) / H
    a = np.sqrt(max(1.0 - b * b, 0.0))
    k = -b * px + a * py                   # projection along the side tangent
    d_top = np.sqrt(px * px + py * py) - R1
    d_bot = np.sqrt(px * px + (py - H) ** 2) - R2
    d_side = px * a + py * b - R1
    return np.where(k < 0, d_top, np.where(k > a * H, d_bot, d_side))


def smin(a, b, k):
    h = np.clip(0.5 + 0.5 * (b - a) / k, 0, 1)
    return b * (1 - h) + a * h - k * h * (1 - h)


def sd_capsule(p, a, b, r):
    pa = p - a
    ba = b - a
    h = np.clip((pa @ ba) / (ba @ ba), 0, 1)
    return np.linalg.norm(pa - h[..., None] * ba, axis=-1) - r


def field(x, y, z):
    d2 = teardrop(x, y) - RIM
    # shell: rounded slab, broad gently domed face, tight round-over into the sides
    r, front, back = 0.32, 0.5, -0.45
    zc = (front + back) / 2
    hh = (front - back) / 2
    qx = d2 + r
    qz = np.abs(z - zc) - hh + r
    shell = np.sqrt(np.maximum(qx, 0) ** 2 + np.maximum(qz, 0) ** 2) + np.minimum(np.maximum(qx, qz), 0) - r
    # rear lobe that sits in the concha: ellipsoid behind the upper face
    ex, ey, ez = x - 0.1 * SX, y + 0.05, z + 0.45
    lobe = np.sqrt((ex / 1.05) ** 2 + (ey / 1.0) ** 2 + (ez / 0.95) ** 2) - 1.0
    lobe *= 0.6
    dome = (np.sqrt(x * x + (y + 0.1) ** 2 + (z - (front - 5.0)) ** 2) - 5.0)
    shell = -smin(-shell, -dome, 0.15)
    body = smin(shell, lobe, 0.75)
    # nozzle: angled down and back from the lower rear
    p = np.stack([x, y, z], axis=-1)
    a = np.array([0.1 * SX, -0.45, -0.9])
    b = a + np.array([0.7 * SX, -0.45, -0.6]) * 0.7
    nozzle = sd_capsule(p, a, b, 0.36)
    return smin(body, nozzle, 0.3)


def main():
    step = 0.012
    xs = np.arange(-1.6, 1.6, step)
    ys = np.arange(-1.9, 1.7, step)
    zs = np.arange(-2.2, 0.8, step)
    X, Y, Z = np.meshgrid(xs, ys, zs, indexing="ij")
    vol = field(X, Y, Z).astype(np.float32)
    verts, faces, normals, _ = measure.marching_cubes(vol, 0.0, spacing=(step, step, step))
    verts += np.array([xs[0], ys[0], zs[0]])
    out = os.path.join(HERE, f"xm5_shell_{SIDE}.obj")
    with open(out, "w") as f:
        for v in verts:
            f.write(f"v {v[0]:.5f} {v[1]:.5f} {v[2]:.5f}\n")
        for t in faces:
            f.write(f"f {t[0]+1} {t[2]+1} {t[1]+1}\n")
    # nozzle end for the tip, reported for the Blender side
    a = np.array([0.1 * SX, -0.45, -0.9])
    b = a + np.array([0.7 * SX, -0.45, -0.6]) * 0.7
    print(out, len(verts), "verts; nozzle_end", b.round(4).tolist())


if __name__ == "__main__":
    main()
