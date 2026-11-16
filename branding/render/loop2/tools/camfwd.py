
import bpy
def camfwd():
    S = bpy.context.scene; co = S.camera; bpy.context.view_layer.update()
    f = co.matrix_world.to_3x3() @ __import__("mathutils").Vector((0,0,-1))
    n = S.world.node_tree.nodes.get("CamFwd")
    if n: n.inputs[0].default_value, n.inputs[1].default_value, n.inputs[2].default_value = f.x, f.y, f.z
bpy.app.driver_namespace["camfwd"] = camfwd
camfwd()
