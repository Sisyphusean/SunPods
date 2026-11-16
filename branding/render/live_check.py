# Runs inside the live Blender (via the MCP socket). Imports the SDF shell and renders a quick
# face-on and side check with a plain gloss material, so silhouettes can be compared to the refs.
import bpy, math, os
from mathutils import Vector

HERE = r"C:\Users\peyio\Documents\SonyPodsBattery\branding\render"
for o in list(bpy.data.objects):
    bpy.data.objects.remove(o, do_unlink=True)
for coll in (bpy.data.meshes, bpy.data.materials, bpy.data.lights, bpy.data.cameras):
    for d in list(coll):
        if d.users == 0:
            coll.remove(d)
s = bpy.context.scene
s.render.engine = "CYCLES"
s.cycles.samples = 24
s.cycles.device = "GPU"
s.render.film_transparent = False
s.render.resolution_x = s.render.resolution_y = 560
w = bpy.data.worlds.new("w"); w.use_nodes = True
w.node_tree.nodes["Background"].inputs[0].default_value = (0.1, 0.14, 0.16, 1)
s.world = w

bpy.ops.import_scene.obj(filepath=os.path.join(HERE, "xm5_shell_left.obj"), axis_forward="Y", axis_up="Z")
bud = bpy.context.selected_objects[0]
bud.rotation_euler = (0, 0, 0)
for p in bud.data.polygons:
    p.use_smooth = True
m = bpy.data.materials.new("gloss"); m.use_nodes = True
b = m.node_tree.nodes["Principled BSDF"]
b.inputs["Base Color"].default_value = (0.03, 0.04, 0.045, 1)
b.inputs["Roughness"].default_value = 0.15
bud.data.materials.clear(); bud.data.materials.append(m)

L = bpy.data.lights.new("l", "AREA"); L.energy = 900; L.size = 6
lo = bpy.data.objects.new("l", L); lo.location = (-3, 3, 6); s.collection.objects.link(lo)
lo.rotation_euler = (Vector((0, 0, 0)) - lo.location).to_track_quat("-Z", "Y").to_euler()

cam = bpy.data.cameras.new("c"); cam.type = "ORTHO"; cam.ortho_scale = 4.2
co = bpy.data.objects.new("c", cam); s.collection.objects.link(co); s.camera = co

def shot(name, loc):
    co.location = loc
    co.rotation_euler = (Vector((0, -0.2, -0.3)) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()
    s.render.filepath = os.path.join(HERE, name)
    bpy.ops.render.render(write_still=True)

shot("check_face.png", (0, -0.2, 8))     # looking straight at the face (+Z)
shot("check_side.png", (8, -0.2, -0.3))  # from the side (+X)
print("checked", len(bud.data.vertices))
