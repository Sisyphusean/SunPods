# Fit board cameras: grid search azimuth/elevation per Sony 360 frame by silhouette IoU.
# Run inside Blender (text block "fit_cams"). Stores result in scene["MATCH"] as JSON.
import bpy, math, os, json, numpy as np
from mathutils import Vector
S = bpy.context.scene; co = S.camera
RD = r"C:\Users\peyio\Documents\SonyPodsBattery\branding\render"
TMP = os.path.join(RD, "loop2", "_fit"); os.makedirs(TMP, exist_ok=True)
T = Vector(S.get("BOARD_TARGET", (-0.15, 0.25, -0.2)))
ALL = ["02", "03", "04", "05", "06", "07", "08", "09", "10", "11"]
FR = globals().get("FIT_FR", ALL)
N = 48; RES = 96
def norm_mask(m):
    ys, xs = np.nonzero(m)
    if len(xs) == 0: return np.zeros((N, N), bool)
    m = m[ys.min():ys.max() + 1, xs.min():xs.max() + 1]; h, w = m.shape; s_ = max(h, w)
    pad = np.zeros((s_, s_), bool); pad[(s_ - h) // 2:(s_ - h) // 2 + h, (s_ - w) // 2:(s_ - w) // 2 + w] = m
    idx = (np.arange(N) * s_ / N).astype(int); return pad[idx][:, idx]
def loadmask(p, ref):
    im = bpy.data.images.load(p, check_existing=False); w, h = im.size
    a = np.array(im.pixels[:], np.float32).reshape(h, w, 4); bpy.data.images.remove(im)
    return norm_mask(a[..., :3].min(axis=2) < 0.93) if ref else norm_mask(a[..., 3] > 0.5)
refs = {f: loadmask(os.path.join(RD, "ref360", f + ".png"), True) for f in FR}
eng, ft, rx, ry = S.render.engine, S.render.film_transparent, S.render.resolution_x, S.render.resolution_y
smp, dn = S.cycles.samples, S.cycles.use_denoising
# Cycles at 1 sample: Workbench renders crash Blender 3.2 when called from the MCP timer
S.render.engine = "CYCLES"; S.cycles.samples = 1; S.cycles.use_denoising = False
S.render.film_transparent = True; S.render.resolution_x = S.render.resolution_y = RES
loc0, rot0 = co.location.copy(), co.rotation_euler.copy()
hidden = [o for o in S.objects if o.name.startswith("REF_")]
for o in hidden: o.hide_render = True
def sil(az, el):
    a = math.radians(az); d = Vector((math.sin(a), -math.cos(a), el)).normalized()
    co.location = T + d * 12; co.rotation_euler = (T - co.location).to_track_quat("-Z", "Y").to_euler()
    p = os.path.join(TMP, "s.png"); S.render.filepath = p; bpy.ops.render.render(write_still=True)
    return loadmask(p, False)
def iou(a, b): return float((a & b).sum() / max(1, (a | b).sum()))
# Prior = Sony turntable order (face toward -Y, outer/SONY side -X, inner side +Y, +X side).
# A rounded bud matches many outlines, so search only near the prior.
PRIOR = {"02": (350, -0.45), "03": (5, -0.45), "04": (0, -0.45), "05": (300, -0.2), "06": (285, 0.05),
         "07": (210, 0.3), "08": (135, 0.55), "09": (100, 0.2), "10": (5, 0.1), "11": (15, -0.4)}
best = {}
for f in FR:
    pa, pe = PRIOR[f]; best[f] = [pa, pe, -1]
    for daz in range(-30, 31, 6):
        for dl in (-0.2, -0.1, 0.0, 0.1, 0.2):
            az, el = (pa + daz) % 360, round(pe + dl, 2); s = iou(sil(az, el), refs[f])
            if s > best[f][2]: best[f] = [az, el, s]
S.render.engine, S.render.film_transparent, S.render.resolution_x, S.render.resolution_y = eng, ft, rx, ry
S.cycles.samples, S.cycles.use_denoising = smp, dn
co.location, co.rotation_euler = loc0, rot0
old = {m[0]: m for m in json.loads(S.get("MATCH", "[]"))}
for f in FR: old[f] = [f, best[f][0], best[f][1]]
S["MATCH"] = json.dumps([old[f] for f in ALL if f in old])
print("fit", {f: (b[0], b[1], round(b[2], 3)) for f, b in best.items()})
