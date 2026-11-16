
import bpy, bmesh
from mathutils import Matrix, Vector
win, area, region = bpy.app.driver_namespace["V3D"]; S = bpy.context.scene
if "SM_Bud_SONY" in bpy.data.objects: bpy.data.objects.remove(bpy.data.objects["SM_Bud_SONY"], do_unlink=True)
font = bpy.data.fonts.load(S.get("LOGO_FONT", r"C:\Windows\Fonts\ROCKB.TTF"), check_existing=True)
cu = bpy.data.curves.new("SONY_txt", "FONT"); cu.body = "SONY"; cu.font = font; cu.align_x = "CENTER"; cu.align_y = "CENTER"
cu.size = S.get("LOGO_SIZE", 0.25); cu.space_character = S.get("LOGO_SPACE", 1.05); cu.offset = S.get("LOGO_BOLD", 0.0)
t = bpy.data.objects.new("SM_Bud_SONY", cu); bpy.data.collections["COL_Bud"].objects.link(t)
R = Matrix(((0,0,-1),(0,-1,0),(-1,0,0))).transposed()
t.matrix_world = Matrix.Translation(Vector(S.get("LOGO_POS", (-1.2, -0.26, 0.42)))) @ Matrix.Rotation(S.get("LOGO_ROLL", 0.0), 4, Vector((-1,0,0))) @ R.to_4x4() @ Matrix.Diagonal((S.get('LOGO_SX', 1.0), 1.0, 1.0, 1.0))
bpy.ops.object.select_all(action="DESELECT"); bpy.context.view_layer.objects.active = t; t.select_set(True)
with bpy.context.temp_override(window=win, area=area, region=region, object=t, active_object=t, selected_objects=[t], selected_editable_objects=[t]):
    bpy.ops.object.convert(target="MESH")
me = t.data; bm = bmesh.new(); bm.from_mesh(me); bmesh.ops.triangulate(bm, faces=bm.faces)
for i in range(2): bmesh.ops.subdivide_edges(bm, edges=bm.edges, cuts=1, use_grid_fill=True)
bm.to_mesh(me); bm.free()
sw = t.modifiers.new("Flush","SHRINKWRAP"); sw.target = bpy.data.objects["SM_Bud_Shell"]; sw.wrap_method = "PROJECT"
sw.use_project_z = True; sw.use_negative_direction = True; sw.use_positive_direction = True; sw.offset = 0.0025
me.materials.append(bpy.data.materials["MAT_Bud_Logo"])
print("logo", t.dimensions)
