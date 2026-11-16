# Board: every Sony 360 frame (02-11) beside our model at the fitted camera, plus the two hero pairs.
# Each tile is cropped square around its subject so sizes compare 1:1. Also writes silhouette IoU.
import bpy, math, os, json, numpy as np
from mathutils import Vector
S = bpy.context.scene; co = S.camera
RD = r"C:\Users\peyio\Documents\SonyPodsBattery\branding\render"
OUT = os.path.join(RD, "loop2", "iter%02d" % ITER); os.makedirs(OUT, exist_ok=True)
MATCH = json.loads(S["MATCH"])
T = Vector(S.get("BOARD_TARGET", (-0.15, 0.25, -0.2))); TS = 360
def load(p, dark_bg=False):
    im = bpy.data.images.load(p, check_existing=False); w, h = im.size
    a = np.array(im.pixels[:], dtype=np.float32).reshape(h, w, 4); bpy.data.images.remove(im); a[..., 3] = 1
    bg = a[2, 2, :3]
    m = np.abs(a[..., :3] - bg).max(axis=2) > 0.12
    ys = np.nonzero(m.sum(axis=1) > 3)[0]; xs = np.nonzero(m.sum(axis=0) > 3)[0]  # ignore stray noise pixels
    if len(xs) and len(ys):
        cy, cx = (ys.min() + ys.max()) / 2, (xs.min() + xs.max()) / 2; s = max(ys.max() - ys.min(), xs.max() - xs.min()) * 1.25
    else: cy, cx, s = h / 2, w / 2, min(h, w)
    K = 4  # supersample then box-average: avoids moire on the grille perforation
    yi = np.clip((cy - s / 2 + (np.arange(TS * K) + 0.5) * s / (TS * K)).astype(int), 0, h - 1); xi = np.clip((cx - s / 2 + (np.arange(TS * K) + 0.5) * s / (TS * K)).astype(int), 0, w - 1)
    return a[yi][:, xi].reshape(TS, K, TS, K, 4).mean(axis=(1, 3)).astype(np.float32)
def aim(az, el, dist):
    a = math.radians(az); d = Vector((math.sin(a), -math.cos(a), el)).normalized()
    co.location = T + d * dist; co.rotation_euler = (T - co.location).to_track_quat("-Z", "Y").to_euler(); exec(bpy.data.texts["camfwd"].as_string())
hid = [o for o in S.objects if o.name.startswith("REF_")]
for o in hid: o.hide_render = True
S.cycles.samples = 64; S.render.resolution_x = S.render.resolution_y = 1000
loc0, rot0 = co.location.copy(), co.rotation_euler.copy()
pairs = []
for fr, az, el in MATCH:
    aim(az, el, 10.0); p = os.path.join(OUT, "match_%s.png" % fr); S.render.filepath = p; bpy.ops.render.render(write_still=True)
    pairs.append((load(os.path.join(RD, "ref360", fr + ".png")), load(p)))
co.location, co.rotation_euler = loc0, rot0
pairs.append((load(os.path.join(RD, "ref360", "hero", "ref_face.png")), load(os.path.join(OUT, "hero_face.png"))))
pairs.append((load(os.path.join(RD, "ref360", "hero", "ref_side.png")), load(os.path.join(OUT, "hero_side.png"))))
red = np.array([0.9, 0.2, 0.2, 1], np.float32)
gap = np.ones((TS, 6, 4), np.float32) * red
tiles = [np.concatenate([s, m, gap], axis=1) for s, m in pairs]
rows = [np.concatenate(tiles[r * 4:(r + 1) * 4], axis=1) for r in range(3)]
sep = np.ones((6, rows[0].shape[1], 4), np.float32) * red
sheet = np.concatenate([rows[2], sep, rows[1], sep, rows[0]], axis=0); H, W = sheet.shape[:2]
img = bpy.data.images.get("board") or bpy.data.images.new("board", W, H, alpha=True)
if tuple(img.size) != (W, H): img.scale(W, H)
img.pixels = sheet.ravel(); img.filepath_raw = os.path.join(OUT, "_board_ref_vs_model.png"); img.file_format = "PNG"; img.save()
# silhouette IoU
N = 64
def norm_mask(m):
    ys, xs = np.nonzero(m)
    if len(xs) == 0: return np.zeros((N, N), bool)
    m = m[ys.min():ys.max() + 1, xs.min():xs.max() + 1]; h, w = m.shape; s_ = max(h, w)
    pad = np.zeros((s_, s_), bool); pad[(s_ - h) // 2:(s_ - h) // 2 + h, (s_ - w) // 2:(s_ - w) // 2 + w] = m
    idx = (np.arange(N) * s_ / N).astype(int); return pad[idx][:, idx]
eng, ft, smp, dn = S.render.engine, S.render.film_transparent, S.cycles.samples, S.cycles.use_denoising
S.render.engine = "CYCLES"; S.cycles.samples = 1; S.cycles.use_denoising = False  # Workbench crashes from MCP timer
S.render.film_transparent = True; S.render.resolution_x = S.render.resolution_y = 160
scores = []
for fr, az, el in MATCH:
    aim(az, el, 12); p = os.path.join(OUT, "_sil.png"); S.render.filepath = p; bpy.ops.render.render(write_still=True)
    im = bpy.data.images.load(p, check_existing=False); a4 = np.array(im.pixels[:], np.float32).reshape(160, 160, 4); bpy.data.images.remove(im)
    im = bpy.data.images.load(os.path.join(RD, "ref360", fr + ".png"), check_existing=False); w_, h_ = im.size
    r4 = np.array(im.pixels[:], np.float32).reshape(h_, w_, 4); bpy.data.images.remove(im)
    mm, sm = norm_mask(a4[..., 3] > 0.5), norm_mask(r4[..., :3].min(axis=2) < 0.93)
    scores.append((fr, round(float((mm & sm).sum() / (mm | sm).sum()), 3)))
S.render.engine, S.render.film_transparent = eng, ft; S.cycles.samples, S.cycles.use_denoising = smp, dn; co.location, co.rotation_euler = loc0, rot0
valid = [v for f, v in scores if f != "02"]
with open(os.path.join(OUT, "_silhouette_iou.txt"), "w") as fh:
    fh.write("Silhouette overlap (IoU) model vs Sony 360 frame at matched camera; 1.0 = identical outline\n")
    for (f, v), (_, az, el) in zip(scores, MATCH): fh.write("frame %s: %.3f  (cam az %d, el %.2f)\n" % (f, v, az, el))
    fh.write("mean (excl. 02, which contains part of the second bud): %.3f\n" % (sum(valid) / len(valid)))
print("board", W, H, "IoU", scores, "mean", round(sum(valid) / len(valid), 3))
