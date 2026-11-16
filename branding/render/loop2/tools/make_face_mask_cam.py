
import bpy, json, math, numpy as np
from mathutils import Vector
from mathutils.bvhtree import BVHTree
from bpy_extras.object_utils import world_to_camera_view
S = bpy.context.scene; co = S.camera
FP = json.loads(S["FACE_PX"]); GP = json.loads(S["GRILLE_PX"]); RB = json.loads(S["REF_BBOX"])
az, el = S["MASK_CAM"]; T = Vector(S["BOARD_TARGET"])
loc0, rot0, rx, ry = co.location.copy(), co.rotation_euler.copy(), S.render.resolution_x, S.render.resolution_y
S.render.resolution_x = S.render.resolution_y = 600
a = math.radians(az); dvec = Vector((math.sin(a), -math.cos(a), el)).normalized()
co.location = T + dvec*10.0; co.rotation_euler = (T - co.location).to_track_quat("-Z","Y").to_euler()
bpy.context.view_layer.update(); dg = bpy.context.evaluated_depsgraph_get()
# body-only landmark mapping (ignores the ear tip): ref [left, top, right, bottom] vs our shell
sh_ = bpy.data.objects["SM_Bud_Shell"]; e_ = sh_.evaluated_get(dg); M_ = sh_.matrix_world
P_ = [world_to_camera_view(S, co, M_ @ v.co) for v in e_.data.vertices]
xs = [p.x*600 for p in P_]; ys = [(1-p.y)*600 for p in P_]
oT, oB, oR = min(ys), max(ys), max(xs)
oL = min(x for x, y in zip(xs, ys) if y < oT + 0.55*(oB-oT))
LM = json.loads(S["REF_LM"])  # left, top, right, bottom in ref pixels
OB = (oL, oT, oR, oB)
sx = (oR-oL)/(LM[2]-LM[0])
GC = json.loads(S.get("REF_CHIN_COLS", "[440, 590]")); xo0 = oL + (GC[0]-LM[0])*sx; xo1 = oL + (GC[1]-LM[0])*sx
oB = max(y for x, y in zip(xs, ys) if xo0 <= x <= xo1); OB = (oL, oT, oR, oB)
sy = (oB-oT)/(LM[3]-LM[1]); s = (sx+sy)/2
def to_ours(p): return (oL + (p[0]-LM[0])*sx, oT + (p[1]-LM[1])*sy)
fr = [co.matrix_world @ c for c in co.data.view_frame(scene=S)]  # tr, br, bl, tl
def ray(px, py):
    u, v = px/600, py/600
    top = fr[3].lerp(fr[0], u); bot = fr[2].lerp(fr[1], u); p = top.lerp(bot, v)
    return co.matrix_world.translation, (p - co.matrix_world.translation).normalized()
sh = bpy.data.objects["SM_Bud_Shell"]; bvh = BVHTree.FromObject(sh, dg)
pts = []
for p in FP:
    o_, d_ = ray(*to_ours(p)); hit = bvh.ray_cast(o_, d_)[0]
    if hit is None: hit = bvh.find_nearest(o_ + d_*((T-o_).length))[0]
    pts.append(hit)
fwd = (T - co.location).normalized(); R = co.matrix_world.to_3x3() @ Vector((1,0,0)); U = co.matrix_world.to_3x3() @ Vector((0,1,0))
uv = np.array([(p.dot(R), p.dot(U)) for p in pts])
# grille
gc = bvh.ray_cast(*ray(*to_ours(GP[:2])))[0]; ge = bvh.ray_cast(*ray(*to_ours((GP[0]+GP[2], GP[1]))))[0]
S["GRILLE_HIT"] = tuple(gc); S["GRILLE_R"] = (ge - gc).length
S["MASK_R"] = tuple(R); S["MASK_U"] = tuple(U); S["MASK_F"] = tuple(fwd)
co.location, co.rotation_euler = loc0, rot0; S.render.resolution_x, S.render.resolution_y = rx, ry
# smooth closed outline (Catmull-Rom) then rasterize
n = len(uv); dense = []
for i in range(n):
    p0,p1,p2,p3 = uv[(i-1)%n], uv[i], uv[(i+1)%n], uv[(i+2)%n]
    for t in np.linspace(0,1,max(2, 288//n),endpoint=False):
        t2,t3 = t*t, t*t*t
        dense.append(0.5*((2*p1)+(-p0+p2)*t+(2*p0-5*p1+4*p2-p3)*t2+(-p0+3*p1-3*p2+p3)*t3))
poly = np.array(dense)
lo = uv.min(axis=0) - 0.15; hi = uv.max(axis=0) + 0.15; span = (hi-lo).max(); hi = lo + span
S["MASK_LO"] = tuple(lo); S["MASK_SPAN"] = float(span)
if S.get("MASK_EXTERNAL"):
    open(r"C:\Users\peyio\Documents\SonyPodsBattery\branding\render\loop2\tools\mask_poly.json","w").write(json.dumps({"poly": poly.tolist(), "lo": list(map(float, lo)), "span": float(span)}))
Rz = 64 if S.get("MASK_EXTERNAL") else 2048; gx, gz = np.meshgrid(lo[0]+(np.arange(Rz)+0.5)/Rz*span, lo[1]+(np.arange(Rz)+0.5)/Rz*span)
inside = np.zeros_like(gx, bool); m = len(poly)
for i in range(m):
    x1,y1 = poly[i]; x2,y2 = poly[(i+1)%m]
    inside ^= ((y1 > gz) != (y2 > gz)) & (gx < (x2-x1)*(gz-y1)/(y2-y1+1e-12)+x1)
dmin = np.full(gx.shape, 9.0, np.float32)
for i in range(m):
    x1,y1 = poly[i]; x2,y2 = poly[(i+1)%m]; dx,dy = x2-x1, y2-y1; L2 = dx*dx+dy*dy+1e-12
    t = np.clip(((gx-x1)*dx+(gz-y1)*dy)/L2,0,1); dmin = np.minimum(dmin, np.hypot(gx-(x1+t*dx), gz-(y1+t*dy)))
line = np.clip(dmin/0.05, 0, 1)  # G = distance to outline / 0.05 (shader makes the hairline)
h = Rz//2; mk = inside.astype(np.float32).reshape(h,2,h,2).mean(axis=(1,3)); ln = line.reshape(h,2,h,2).mean(axis=(1,3))
if not S.get("MASK_EXTERNAL"):
    img = bpy.data.images["IMG_FaceMask"]; img.source="GENERATED"
if not S.get("MASK_EXTERNAL"):
    px = np.zeros((h,h,4),np.float32); px[...,0]=mk; px[...,1]=ln; px[...,3]=1; img.pixels = px.ravel(); img.pack()
print("mask ok", OB, round(s,3), "grille r", round(S["GRILLE_R"],3))
