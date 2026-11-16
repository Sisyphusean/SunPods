
import bpy, math, os, numpy as np
from mathutils import Vector
S = bpy.context.scene; co = S.camera
RD = r"C:\Users\peyio\Documents\SonyPodsBattery\branding\render"
OUT = os.path.join(RD, "loop", "iter%02d" % ITER); os.makedirs(OUT, exist_ok=True)
MATCH = [("02", 350, -0.45), ("03", 5, -0.45), ("04", 0, -0.45), ("05", 300, -0.2), ("06", 285, 0.05), ("07", 210, 0.3), ("08", 135, 0.55), ("09", 100, 0.2), ("10", 5, 0.1), ("11", 15, -0.4)]
T = Vector((-0.25, 0.15, -0.25)); TS = 300
def load(p, crop=1.0):
    im = bpy.data.images.load(p, check_existing=False); w, h = im.size
    a = np.array(im.pixels[:], dtype=np.float32).reshape(h, w, 4); bpy.data.images.remove(im)
    s = int(min(w, h) * crop); y0 = (h - s) // 2; x0 = (w - s) // 2; a = a[y0:y0 + s, x0:x0 + s]
    idx = (np.arange(TS) * s / TS).astype(int); a = a[idx][:, idx].copy(); a[..., 3] = 1
    return a
w = S.world; bg = next(n for n in w.node_tree.nodes if n.type == "BACKGROUND"); old = tuple(bg.inputs[0].default_value)
bg.inputs[0].default_value = (1, 1, 1, 1); vt = S.view_settings.view_transform; S.view_settings.view_transform = "Standard"
S.cycles.samples = 32; S.render.resolution_x = S.render.resolution_y = TS
loc0, rot0 = co.location.copy(), co.rotation_euler.copy()
pairs = []
for fr, az, el in MATCH:
    a = math.radians(az); d = Vector((math.sin(a), -math.cos(a), el)).normalized()
    co.location = T + d * 10.0; co.rotation_euler = (T - co.location).to_track_quat("-Z", "Y").to_euler()
    p = os.path.join(OUT, "match_%s.png" % fr); S.render.filepath = p; bpy.ops.render.render(write_still=True)
    pairs.append((load(os.path.join(RD, "ref360", fr + ".png"), 0.7), load(p)))
bg.inputs[0].default_value = old; S.view_settings.view_transform = vt; co.location, co.rotation_euler = loc0, rot0
pairs.append((load(r"C:\Users\peyio\Documents\SonyPodsBattery\branding\render\ref360\hero\ref_face.png"), load(os.path.join(OUT, "hero_face.png"))))
pairs.append((load(r"C:\Users\peyio\Documents\SonyPodsBattery\branding\render\ref360\hero\ref_side.png"), load(os.path.join(OUT, "hero_side.png"))))
gap = np.ones((TS, 6, 4), np.float32) * np.array([0.9, 0.2, 0.2, 1], np.float32)
tiles = [np.concatenate([s, m, gap], axis=1) for s, m in pairs]
rows = [np.concatenate(tiles[r * 4:(r + 1) * 4], axis=1) for r in range(3)]
sep = np.ones((6, rows[0].shape[1], 4), np.float32) * np.array([0.9, 0.2, 0.2, 1], np.float32)
sheet = np.concatenate([rows[2], sep, rows[1], sep, rows[0]], axis=0); H, W = sheet.shape[:2]
img = bpy.data.images.get("board") or bpy.data.images.new("board", W, H, alpha=True)
if tuple(img.size) != (W, H): img.scale(W, H)
img.pixels = sheet.ravel(); img.filepath_raw = os.path.join(OUT, "_board_ref_vs_model.png"); img.file_format = "PNG"; img.save()
print("board", W, H)

N = 64
def norm_mask(m):
    ys, xs = np.nonzero(m)
    if len(xs) == 0: return np.zeros((N, N), bool)
    m = m[ys.min():ys.max() + 1, xs.min():xs.max() + 1]; h, w = m.shape; s_ = max(h, w)
    pad = np.zeros((s_, s_), bool); pad[(s_ - h) // 2:(s_ - h) // 2 + h, (s_ - w) // 2:(s_ - w) // 2 + w] = m
    idx = (np.arange(N) * s_ / N).astype(int); return pad[idx][:, idx]
eng, ft = S.render.engine, S.render.film_transparent
S.render.engine = "BLENDER_WORKBENCH"; S.render.film_transparent = True; S.render.resolution_x = S.render.resolution_y = 160
loc0, rot0 = co.location.copy(), co.rotation_euler.copy(); scores = []
for fr, az, el in MATCH:
    a = math.radians(az); d = Vector((math.sin(a), -math.cos(a), el)).normalized()
    co.location = T + d * 12; co.rotation_euler = (T - co.location).to_track_quat("-Z", "Y").to_euler()
    p = os.path.join(OUT, "_sil.png"); S.render.filepath = p; bpy.ops.render.render(write_still=True)
    im = bpy.data.images.load(p, check_existing=False); a4 = np.array(im.pixels[:], np.float32).reshape(160, 160, 4); bpy.data.images.remove(im)
    im = bpy.data.images.load(os.path.join(RD, "ref360", fr + ".png"), check_existing=False); w_, h_ = im.size
    r4 = np.array(im.pixels[:], np.float32).reshape(h_, w_, 4); bpy.data.images.remove(im)
    mm, sm = norm_mask(a4[..., 3] > 0.5), norm_mask(r4[..., :3].min(axis=2) < 0.93)
    scores.append((fr, round(float((mm & sm).sum() / (mm | sm).sum()), 3)))
S.render.engine, S.render.film_transparent = eng, ft; co.location, co.rotation_euler = loc0, rot0
valid = [v for f, v in scores if f != "02"]
with open(os.path.join(OUT, "_silhouette_iou.txt"), "w") as fh:
    fh.write("Silhouette overlap (IoU) model vs Sony 360 frame at matched camera; 1.0 = identical outline\n")
    for f, v in scores: fh.write("frame %s: %.3f\n" % (f, v))
    fh.write("mean (excl. 02, which contains part of the second bud): %.3f\n" % (sum(valid) / len(valid)))
print("IoU", scores, "mean", round(sum(valid) / len(valid), 3))
