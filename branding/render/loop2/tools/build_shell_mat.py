
import bpy
S = bpy.context.scene
m = bpy.data.materials["MAT_Bud_Shell"]; nt = m.node_tree; N = nt.nodes; L = nt.links
for n in list(N): N.remove(n)
out = N.new("ShaderNodeOutputMaterial")
def P(name, col, rough, coat=0.0):
    b = N.new("ShaderNodeBsdfPrincipled"); b.name = name
    b.inputs["Base Color"].default_value = col; b.inputs["Roughness"].default_value = rough
    b.inputs["Clearcoat"].default_value = coat; b.inputs["Clearcoat Roughness"].default_value = 0.04; return b
gl = P("Gloss", tuple(S.get("SHELL_GLOSS_COL", (0.012,0.014,0.016)))+(1,), S.get("SHELL_GLOSS_R", 0.06), 0.8)
sat = P("Satin", tuple(S.get("SHELL_SAT_COL", (0.02,0.022,0.025)))+(1,), S.get("SHELL_SAT_R", 0.5)); sat.inputs["Specular"].default_value = S.get("SHELL_SAT_SPEC", 0.6); sat.inputs["Sheen"].default_value = S.get("SHELL_SAT_SHEEN", 0.0); sat.inputs["Sheen Tint"].default_value = 0.0
hl = P("Hairline", (0.12,0.13,0.14,1), 0.25)
def mth(op, a, b):
    n = N.new("ShaderNodeMath"); n.operation = op
    for i,v in enumerate((a,b)):
        if isinstance(v,(int,float)): n.inputs[i].default_value = v
        else: L.new(v, n.inputs[i])
    return n.outputs[0]
tc = N.new("ShaderNodeTexCoord"); sep_obj = N.new("ShaderNodeSeparateXYZ"); L.new(tc.outputs["Object"], sep_obj.inputs[0])
def vdot(vec):
    n = N.new("ShaderNodeVectorMath"); n.operation = "DOT_PRODUCT"; L.new(tc.outputs["Object"], n.inputs[0]); n.inputs[1].default_value = vec; return n.outputs["Value"]
lo = S["MASK_LO"]; sp = S["MASK_SPAN"]
u = mth("DIVIDE", mth("SUBTRACT", vdot(S["MASK_R"]), lo[0]), sp); v = mth("DIVIDE", mth("SUBTRACT", vdot(S["MASK_U"]), lo[1]), sp)
cmb = N.new("ShaderNodeCombineXYZ"); L.new(u, cmb.inputs[0]); L.new(v, cmb.inputs[1])
tex = N.new("ShaderNodeTexImage"); tex.image = bpy.data.images[S.get("MASK_IMG", "IMG_FaceMask")]; tex.interpolation = "Cubic"; tex.extension = "CLIP"; L.new(cmb.outputs[0], tex.inputs[0])
sep = N.new("ShaderNodeSeparateRGB"); L.new(tex.outputs["Color"], sep.inputs[0])
geo = N.new("ShaderNodeNewGeometry"); vtn = N.new("ShaderNodeVectorTransform"); vtn.vector_type = "NORMAL"; vtn.convert_from = "WORLD"; vtn.convert_to = "OBJECT"; L.new(geo.outputs["Normal"], vtn.inputs[0]); nd = N.new("ShaderNodeVectorMath"); nd.operation = "DOT_PRODUCT"
L.new(vtn.outputs[0], nd.inputs[0]); nd.inputs[1].default_value = tuple(-x for x in S["MASK_F"])
front = mth("GREATER_THAN", nd.outputs["Value"], 0.12)
nd2 = N.new("ShaderNodeVectorMath"); nd2.operation = "DOT_PRODUCT"; L.new(vtn.outputs[0], nd2.inputs[0]); nd2.inputs[1].default_value = tuple(S.get("PANEL_DIR", [-x for x in S["MASK_F"]]))
facing = mth("GREATER_THAN", nd2.outputs["Value"], S.get("PANEL_FACING", 0.12))
lowz = mth("LESS_THAN", sep_obj.outputs[2], S.get("FACING_ZMIN", -0.5))
facing = mth("MAXIMUM", facing, lowz)
mask = mth("MULTIPLY", sep.outputs["R"], facing)
# hairline of constant width on the surface: projected distance / cos(view-to-normal) compared with W
cosv = mth("MAXIMUM", nd.outputs["Value"], 0.08)
dist = mth("MULTIPLY", sep.outputs["G"], 0.05)
lineraw = mth("MAXIMUM", mth("SUBTRACT", 1.0, mth("DIVIDE", dist, mth("MULTIPLY", cosv, S.get("HAIRLINE_W", 0.007)))), 0.0)
fcut = mth("MAXIMUM", mth("SUBTRACT", 1.0, mth("DIVIDE", mth("ABSOLUTE", mth("SUBTRACT", nd2.outputs["Value"], S.get("PANEL_FACING", 0.12)), 0.0), 0.012)), 0.0)
fcut = mth("MULTIPLY", fcut, mth("MULTIPLY", sep.outputs["R"], mth("GREATER_THAN", S.get("PANEL_FACING", 0.12), 0.13)))
linef = mth("MAXIMUM", mth("MULTIPLY", mth("MULTIPLY", lineraw, front), mth("SUBTRACT", 1.0, mth("LESS_THAN", nd2.outputs["Value"], S.get("PANEL_FACING", 0.12)))), fcut)
line = mth("MULTIPLY", linef, S.get("HAIRLINE_K", 0.35))
bump = N.new("ShaderNodeBump"); bump.invert = True; bump.inputs["Strength"].default_value = S.get("HAIRLINE_BUMP", 1.0); bump.inputs["Distance"].default_value = 0.003
L.new(linef, bump.inputs["Height"])
for b in (gl, sat, hl): L.new(bump.outputs["Normal"], b.inputs["Normal"])
L.new(bump.outputs["Normal"], gl.inputs["Clearcoat Normal"])
m1 = N.new("ShaderNodeMixShader"); L.new(mask, m1.inputs[0]); L.new(gl.outputs[0], m1.inputs[1]); L.new(sat.outputs[0], m1.inputs[2])
m2 = N.new("ShaderNodeMixShader"); L.new(line, m2.inputs[0]); L.new(m1.outputs[0], m2.inputs[1]); L.new(hl.outputs[0], m2.inputs[2])
L.new(m2.outputs[0], out.inputs[0])
print("shell mat rebuilt")
