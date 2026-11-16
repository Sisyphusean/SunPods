
import bpy, math
def lin(c): return tuple(((x/255+0.055)/1.055)**2.4 for x in c)+(1,)
m = bpy.data.materials["MAT_Bud_Grille"]; nt = m.node_tree; N = nt.nodes; L = nt.links
for n in list(N): N.remove(n)
out = N.new("ShaderNodeOutputMaterial")
met = N.new("ShaderNodeBsdfPrincipled"); met.inputs["Base Color"].default_value = lin((236,186,152)); met.inputs["Metallic"].default_value = 0.7; met.inputs["Roughness"].default_value = 0.32
hole = N.new("ShaderNodeBsdfPrincipled"); hole.inputs["Base Color"].default_value = (0.006,0.005,0.004,1); hole.inputs["Roughness"].default_value = 0.9
ring = N.new("ShaderNodeBsdfPrincipled"); ring.inputs["Base Color"].default_value = lin((240,205,180)); ring.inputs["Metallic"].default_value = 0.9; ring.inputs["Roughness"].default_value = 0.18
tc = N.new("ShaderNodeTexCoord"); sp = N.new("ShaderNodeSeparateXYZ"); L.new(tc.outputs["Object"], sp.inputs[0])
def M(op, a, b=None):
    n = N.new("ShaderNodeMath"); n.operation = op
    for i, v in enumerate((a, b)):
        if v is None: continue
        if isinstance(v, (int, float)): n.inputs[i].default_value = v
        else: L.new(v, n.inputs[i])
    return n.outputs[0]
X, Y = sp.outputs[0], sp.outputs[1]
R = 0.24; P = 2*R/15.5; HR = 0.36*P
r = M("SQRT", M("ADD", M("MULTIPLY", X, X), M("MULTIPLY", Y, Y)))
k = M("ROUND", M("DIVIDE", r, P)); rk = M("MULTIPLY", k, P)
nk = M("MAXIMUM", M("ROUND", M("MULTIPLY", k, 2*math.pi)), 1.0)
th = M("ARCTAN2", Y, X)
thk = M("MULTIPLY", M("ROUND", M("DIVIDE", M("MULTIPLY", th, nk), 2*math.pi)), M("DIVIDE", 2*math.pi, nk))
cx = M("MULTIPLY", rk, M("COSINE", thk)); cy = M("MULTIPLY", rk, M("SINE", thk))
dx = M("SUBTRACT", X, cx); dy = M("SUBTRACT", Y, cy)
d = M("SQRT", M("ADD", M("MULTIPLY", dx, dx), M("MULTIPLY", dy, dy)))
centre = M("LESS_THAN", r, 0.5*P)      # centre hole
dc = M("MINIMUM", d, M("ADD", r, M("MULTIPLY", M("SUBTRACT", 1.0, centre), 9.0)))
holes = M("MULTIPLY", M("LESS_THAN", dc, HR), M("LESS_THAN", r, 0.86*R))
rim = M("MULTIPLY", M("GREATER_THAN", r, 0.965*R), 1.0)
m1 = N.new("ShaderNodeMixShader"); L.new(holes, m1.inputs[0]); L.new(met.outputs[0], m1.inputs[1]); L.new(hole.outputs[0], m1.inputs[2])
m2 = N.new("ShaderNodeMixShader"); L.new(rim, m2.inputs[0]); L.new(m1.outputs[0], m2.inputs[1]); L.new(hole.outputs[0], m2.inputs[2])
L.new(m2.outputs[0], out.inputs[0])
print("grille mat rebuilt")
