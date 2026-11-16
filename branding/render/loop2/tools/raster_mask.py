# High-res face-panel mask from the projected outline (mask_poly.json written by make_face_mask_cam in Blender).
# Output face_mask_hr.png: R = panel coverage (antialiased), G = distance to outline / 0.05 units (clipped), B = 0.
import json, sys, numpy as np
from PIL import Image, ImageDraw
from scipy.ndimage import distance_transform_edt
D = r"C:\Users\peyio\Documents\SonyPodsBattery\branding\render\loop2\tools"
R = int(sys.argv[1]) if len(sys.argv) > 1 else 4096
d = json.load(open(D + r"\mask_poly.json")); poly = np.array(d["poly"]); lo = np.array(d["lo"]); span = d["span"]
SS = 2; N = R * SS
pix = (poly - lo) / span * N                       # x -> u (columns), y -> v (rows, bottom-up in Blender)
pix[:, 1] = N - pix[:, 1]                          # image rows are top-down
im = Image.new("L", (N, N), 0); ImageDraw.Draw(im).polygon([tuple(p) for p in pix], fill=255)
b = np.asarray(im) > 127
dist = np.minimum(distance_transform_edt(b), distance_transform_edt(~b)) * (span / N)   # units
cov = np.asarray(im.resize((R, R), Image.BOX)) / 255.0
g = np.clip(dist / 0.05, 0, 1).reshape(R, SS, R, SS).mean(axis=(1, 3))
out = np.zeros((R, R, 3), np.uint8); out[..., 0] = np.round(cov * 255); out[..., 1] = np.round(g * 255)
Image.fromarray(out, "RGB").save(D + r"\face_mask_hr.png")
print("ok", R, float(cov.mean()))
