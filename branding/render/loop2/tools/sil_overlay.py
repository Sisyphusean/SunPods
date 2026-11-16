# Silhouette difference overlays: Sony 360 frame vs our silhouette render at the matched camera.
# Aligned on the upper body only (top edge plus left/right extremes of the top 50% of rows), so a
# different ear tip does not skew the comparison. Red = Sony has body where we don't (add volume),
# blue = we have body where Sony doesn't (remove volume), grey = both. Horizontal guide every 10%.
import sys, numpy as np
from PIL import Image
RD = r"C:\Users\peyio\Documents\SonyPodsBattery\branding\render"
SIL = sys.argv[1]; OUT = sys.argv[2]; frames = sys.argv[3].split(",")
N = 420
def land(m):
    ys, xs = np.nonzero(m); top, bot = ys.min(), ys.max(); cut = int(top + 0.33 * (bot - top))
    sub = np.nonzero(m[top:cut])[1]; return top, sub.min(), sub.max()
tiles = []
for f in frames:
    ref = np.asarray(Image.open(RD + r"\ref360\%s.png" % f).convert("L")) < 237
    ours = np.asarray(Image.open(SIL + r"\sil_%s.png" % f).convert("RGBA"))[..., 3] > 127
    tR, lR, rR = land(ref); tO, lO, rO = land(ours); s = (rR - lR) / (rO - lO)
    H, W = ref.shape
    yy, xx = np.mgrid[0:H, 0:W]
    sx = np.clip(((xx - lR) / s + lO).astype(int), 0, ours.shape[1] - 1); sy = np.clip(((yy - tR) / s + tO).astype(int), 0, ours.shape[0] - 1)
    o = ours[sy, sx] & ((xx - lR) / s + lO >= 0) & ((yy - tR) / s + tO >= 0)
    r = ref
    ys, xs = np.nonzero(r | o); y0, y1, x0, x1 = ys.min(), ys.max(), xs.min(), xs.max(); S_ = max(y1 - y0, x1 - x0) + 10
    cy, cx = (y0 + y1) // 2, (x0 + x1) // 2
    img = np.full((H, W, 3), 255, np.uint8)
    img[r & o] = (175, 175, 175); img[r & ~o] = (220, 40, 40); img[~r & o] = (40, 90, 230)
    ysb = np.nonzero(r)[0]; hb = ysb.max() - tR
    for k in range(1, 10): img[int(tR + k * 0.1 * hb), :, :] = (0, 170, 0)
    crop = Image.fromarray(img).crop((cx - S_ // 2, cy - S_ // 2, cx + S_ // 2, cy + S_ // 2)).resize((N, N), Image.NEAREST)
    tiles.append(np.asarray(crop)); tiles.append(np.zeros((N, 8, 3), np.uint8))
Image.fromarray(np.concatenate(tiles, axis=1)).save(OUT)
print("ok", OUT)
