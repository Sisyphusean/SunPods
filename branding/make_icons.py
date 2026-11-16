"""Builds the SunPods 'Diamond ring' icon set into app resources and branding/."""
import math, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
INK, AMBER, WHITE = "#FF15161A", "#FFFFB547", "#FFFFFFFF"

cx, cy, r = 54.0, 56.0, 20.0
co_dx, co_dy, co_r = 3.5, -3.5, 23.5
fa = math.radians(45)
fx, fy = cx + (r + 1) * math.cos(fa), cy - (r + 1) * math.sin(fa)
RAYS = [(5, 6), (25, 10), (45, 14), (65, 10), (85, 6)]


def circle(x, y, rr):
    return f"M{x-rr:.2f},{y:.2f}a{rr:.2f},{rr:.2f} 0,1 0,{2*rr:.2f},0a{rr:.2f},{rr:.2f} 0,1 0,{-2*rr:.2f},0Z"


def rays_path():
    out = []
    for a, L in RAYS:
        t = math.radians(a)
        x1, y1 = fx + math.cos(t) * 13, fy - math.sin(t) * 13
        x2, y2 = fx + math.cos(t) * (13 + L), fy - math.sin(t) * (13 + L)
        out.append(f"M{x1:.2f},{y1:.2f}L{x2:.2f},{y2:.2f}")
    return "".join(out)


gloss = f"M{cx-12:.2f},{cy-6:.2f}A13,13 0,0 1,{cx-4:.2f},{cy-13:.2f}"
corona = circle(cx + co_dx, cy + co_dy, co_r)
bud = circle(cx, cy, r)
flare = circle(fx, fy, 5.5)
halo = circle(fx, fy, 10)


def vector(body, w=108, vp=108):
    return (f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{w}dp" android:height="{w}dp" android:viewportWidth="{vp}" android:viewportHeight="{vp}">\n'
            f'{body}</vector>\n')


def p(d, fill=None, stroke=None, width=None, alpha=None, evenodd=False):
    a = f'android:pathData="{d}"'
    if fill: a += f' android:fillColor="{fill}"'
    if evenodd: a += ' android:fillType="evenOdd"'
    if stroke: a += f' android:strokeColor="{stroke}" android:strokeWidth="{width}" android:strokeLineCap="round"'
    if alpha is not None: a += f' android:{"strokeAlpha" if stroke else "fillAlpha"}="{alpha}"'
    return f"        <path {a}/>\n"


def group(body, scale):
    return (f'    <group android:pivotX="54" android:pivotY="54" android:scaleX="{scale}" android:scaleY="{scale}" android:translateX="-3" android:translateY="3">\n'
            f"{body}    </group>\n")


full = (p(corona, AMBER) + p(bud, INK) + p(gloss, stroke=WHITE, width=2.4, alpha=0.35)
        + p(rays_path(), stroke=AMBER, width=3.2) + p(halo, WHITE, alpha=0.25) + p(flare, WHITE))
mono = (p(corona + bud, WHITE, evenodd=True) + p(rays_path(), stroke=WHITE, width=3.2)
        + p(halo, WHITE, alpha=0.35) + p(flare, WHITE))

open(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"), "w").write(vector(group(full, 0.8)))
open(os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"), "w").write(vector(group(mono, 0.8)))
# Tintable small mark for widgets, notifications and the app bar: crop to the art.
mark_body = f'    <group android:translateX="-31" android:translateY="-20">\n{mono}    </group>\n'
open(os.path.join(RES, "drawable", "ic_sunpods_mark.xml"), "w").write(vector(mark_body, w=24, vp=60))
open(os.path.join(RES, "values", "ic_launcher_background.xml"), "w").write(
    '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">#15161A</color>\n</resources>\n')

def sp(d, fill="none", stroke=None, width=None, opacity=None):
    a = f'd="{d}" fill="{fill}"'
    if stroke: a += f' stroke="{stroke}" stroke-width="{width}" stroke-linecap="round"'
    if opacity is not None: a += f' opacity="{opacity}"'
    return f"<path {a}/>"


svg_art = (sp(corona, "#FFB547") + sp(bud, "#15161A") + sp(gloss, stroke="#FFFFFF", width=2.4, opacity=0.35)
           + sp(rays_path(), stroke="#FFB547", width=3.2) + sp(halo, "#FFFFFF", opacity=0.25) + sp(flare, "#FFFFFF"))
open(os.path.join(ROOT, "branding", "sunpods-logo.svg"), "w").write(
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="512" height="512">'
    f'<rect width="108" height="108" fill="#15161A"/>'
    f'<g transform="translate(51,57) scale(.8) translate(-54,-54)">{svg_art}</g></svg>')
print("ok")
raise SystemExit

svg_full = full.replace("android:pathData", "d").replace("android:fillColor", "fill").replace(
    "android:strokeColor", "stroke").replace("android:strokeWidth", "stroke-width").replace(
    "android:strokeLineCap", "stroke-linecap").replace("android:fillAlpha", "fill-opacity").replace(
    "android:strokeAlpha", "stroke-opacity").replace("#FF", "#").replace("<path ", "<path fill=\"none\" ").replace(
    'fill="none" d', 'd')
svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="512" height="512">'
    f'<rect width="108" height="108" fill="#15161A"/>'
       f'<g transform="translate(-3,3) translate(54,54) scale(.8) translate(-54,-54)">{svg_full}</g></svg>')
# paths without an explicit fill must not default to black
svg = svg.replace('<path d=', '<path fill="none" d=')
for tag in ('fill="#', ):
    pass
open(os.path.join(ROOT, "branding", "sunpods-logo.svg"), "w").write(svg)
print("ok")

