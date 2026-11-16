
import bpy, numpy as np
S = bpy.context.scene; P = np.array(S["FACE_OUTLINE"]).reshape(-1,2)
cu = bpy.data.curves.new("tmp_cu","CURVE"); cu.dimensions="2D"; cu.resolution_u = 32
sp = cu.splines.new("BEZIER"); sp.bezier_points.add(len(P)-1); sp.use_cyclic_u = True
for bp,(x,y) in zip(sp.bezier_points,P): bp.co=(x,y,0); bp.handle_left_type=bp.handle_right_type="AUTO"
tmp = bpy.data.objects.new("tmp", cu); S.collection.objects.link(tmp)
dg = bpy.context.evaluated_depsgraph_get(); me = tmp.evaluated_get(dg).to_mesh()
adj = {}
for e in me.edges: a,b = e.vertices; adj.setdefault(a,[]).append(b); adj.setdefault(b,[]).append(a)
loop=[0]; prev=None; cur=0
while True:
    nx = [n for n in adj[cur] if n != prev][0]
    if nx == 0: break
    loop.append(nx); prev, cur = cur, nx
poly = np.array([(me.vertices[i].co.x, me.vertices[i].co.y) for i in loop])
tmp.evaluated_get(dg).to_mesh_clear(); bpy.data.objects.remove(tmp); bpy.data.curves.remove(cu)
R = 2048; xs = -1 + (np.arange(R)+0.5)/R*2; zs = -1.15 + (np.arange(R)+0.5)/R*2.3
gx, gz = np.meshgrid(xs, zs); inside = np.zeros_like(gx, bool); n = len(poly)
for i in range(n):
    x1,y1 = poly[i]; x2,y2 = poly[(i+1)%n]
    inside ^= ((y1 > gz) != (y2 > gz)) & (gx < (x2-x1)*(gz-y1)/(y2-y1+1e-12)+x1)
# hairline: distance to outline < w
d = np.full(gx.shape, 9.0, np.float32)
for i in range(n):
    x1,y1 = poly[i]; x2,y2 = poly[(i+1)%n]; dx,dy = x2-x1, y2-y1; L2 = dx*dx+dy*dy+1e-12
    bx0,bx1 = min(x1,x2)-0.02, max(x1,x2)+0.02; bz0,bz1 = min(y1,y2)-0.02, max(y1,y2)+0.02
    ix = np.nonzero((xs>=bx0)&(xs<=bx1))[0]; iz = np.nonzero((zs>=bz0)&(zs<=bz1))[0]
    if len(ix)==0 or len(iz)==0: continue
    sx, sz = gx[np.ix_(iz,ix)], gz[np.ix_(iz,ix)]
    t = np.clip(((sx-x1)*dx+(sz-y1)*dy)/L2, 0, 1); dd = np.hypot(sx-(x1+t*dx), sz-(y1+t*dy))
    d[np.ix_(iz,ix)] = np.minimum(d[np.ix_(iz,ix)], dd)
w = S.get("FACE_LINE_W", 0.008)
line = np.clip(1 - d / w, 0, 1).astype(np.float32)
m = inside.astype(np.float32)
h = R//2
m = m.reshape(h,2,h,2).mean(axis=(1,3)); line = line.reshape(h,2,h,2).mean(axis=(1,3))
img = bpy.data.images.get("IMG_FaceMask")
img.source = "GENERATED"; img.generated_width = img.generated_height = h; img.use_generated_float = True
px = np.zeros((h, h, 4), np.float32); px[...,0]=m; px[...,1]=line; px[...,2]=0; px[...,3]=1
img.pixels = px.ravel(); img.pack(); img.colorspace_settings.name = "Non-Color"
print("mask", m.mean(), line.max())
