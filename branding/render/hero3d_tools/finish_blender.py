# The SunPods finishes for Blender, in the same linear values as app/src/main/assets/hero3d/hero3d.js
# (black = the approved materials; silver and pink solved from Sony's official shots, see
# calib_colors.py). apply(name) recolours the loaded file in memory only; nothing is saved.
import bpy

FINISHES = {
    "black": None,   # the approved materials as they are in the .blend
    "silver": {
        "gloss": (0.881, 0.900, 0.862), "satin": (0.979, 1.000, 0.958), "hair": (0.979, 1.000, 0.958),
        "grille": (0.852, 0.756, 0.373), "logo": (0.792, 0.703, 0.347),
        "tip": (0.310, 0.328, 0.292), "badge": (0.50, 0.50, 0.50), "badgeR": (0.55, 0.06, 0.055),
        "caseShell": (0.971, 1.000, 0.937), "caseInner": (0.874, 0.900, 0.843),
        "caseLogo": (0.67, 0.45, 0.13), "caseLogoEm": (0.455, 0.35, 0.11), "led": (0.10, 0.10, 0.10),
    },
    "pink": {
        "gloss": (0.780, 0.645, 0.644), "satin": (0.867, 0.717, 0.715), "hair": (0.867, 0.717, 0.715),
        "grille": (0.972, 0.665, 0.476), "logo": (0.904, 0.618, 0.443),
        "tip": (0.230, 0.190, 0.190), "badge": (0.50, 0.50, 0.50), "badgeR": (0.55, 0.06, 0.055),
        "caseShell": (0.828, 0.685, 0.690), "caseInner": (0.745, 0.617, 0.621),
        "caseLogo": (1.0, 0.75, 0.42), "caseLogoEm": (0.79, 0.59, 0.35), "led": (0.10, 0.10, 0.10),
    },
}

# The onboarding clips (anim_render.py) use the case scene's own, brighter rig. The same solve
# against Sony's shots, measured in that rig (case front at the open clip's frame 1, earbud face
# at frame 120), gives these; anything not listed keeps the hero value.
CLIP = {
    "silver": {"caseShell": (0.939, 0.969, 0.905), "caseInner": (0.845, 0.872, 0.815),
               "caseLogoEm": (0.05, 0.035, 0.011)},
    "pink": {"gloss": (0.753, 0.623, 0.622), "satin": (0.837, 0.693, 0.691), "hair": (0.837, 0.693, 0.691),
             "tip": (0.222, 0.184, 0.184), "caseShell": (0.698, 0.566, 0.572), "caseInner": (0.628, 0.509, 0.515),
             "caseLogoEm": (0.20, 0.15, 0.09)},
}
for _k, _v in CLIP.items():
    FINISHES[_k + "_clip"] = dict(FINISHES[_k], **_v)


def _rgba(c):
    return (c[0], c[1], c[2], 1.0)


def _set(mat_name, node_name=None, base=None, emission=None):
    m = bpy.data.materials.get(mat_name)
    if not m:
        return False
    nodes = [n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED"]
    n = next((x for x in nodes if x.name == node_name), None) if node_name else nodes[0]
    if base is not None:
        n.inputs["Base Color"].default_value = _rgba(base)
    if emission is not None:
        n.inputs["Emission"].default_value = _rgba(emission)
    return True


def apply(name):
    # "custom": a finish dict from the JSON file in HERO3D_FINISH_JSON (calibration renders)
    if name == "custom":
        import json, os
        f = json.load(open(os.environ["HERO3D_FINISH_JSON"], encoding="utf-8"))
    else:
        f = FINISHES[name]
    if f is None:
        print("FINISH black (approved materials unchanged)")
        return
    _set("MAT_Bud_Shell", "Gloss", f["gloss"])
    _set("MAT_Bud_Shell", "Satin", f["satin"])
    _set("MAT_Bud_Shell", "Hairline", f["hair"])
    _set("MAT_Bud_Grille", "Principled BSDF", f["grille"])          # the copper disc (holes stay dark)
    _set("MAT_Bud_Logo", None, f["logo"], f["logo"])                  # self-lit like the approved black
    _set("MAT_Bud_Tip", None, f["tip"])
    _set("MAT_Bud_Badge", None, f["badge"])                           # left badge disc
    # the right badge shares the left one's mesh, so it gets its own material at object level
    src = bpy.data.materials.get("MAT_Bud_Badge")
    if src:
        red = src.copy(); red.name = "MAT_Bud_BadgeR"
        next(n for n in red.node_tree.nodes if n.type == "BSDF_PRINCIPLED").inputs["Base Color"].default_value = _rgba(f["badgeR"])
        for o in bpy.data.objects:
            if o.name.startswith("SM_Bud_Badge_R"):
                o.material_slots[0].link = "OBJECT"
                o.material_slots[0].material = red
    _set("MAT_Case_Shell", None, f["caseShell"])
    _set("MAT_Case_Inner", None, f["caseInner"])
    _set("MAT_Case_Logo", None, f["caseLogo"], f["caseLogoEm"])
    _set("MAT_Case_LED", None, f["led"])
    print("FINISH", name, "applied")
