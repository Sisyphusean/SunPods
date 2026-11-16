"""Generates SunPods logo concept SVGs and a preview board (branding/concepts.html)."""
import math, os

OUT = os.path.dirname(os.path.abspath(__file__))
INK, AMBER, CREAM, FLARE, DUSK = "#15161A", "#FFB547", "#FFF3DC", "#FFFFFF", "#FF7A45"


def circle(x, y, r):
    return f"M{x-r:.2f},{y:.2f}a{r},{r} 0 1,0 {2*r},0a{r},{r} 0 1,0 {-2*r},0Z"


def tile(bg, body, size=108):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}">'
            f'<rect width="{size}" height="{size}" rx="26" fill="{bg}"/>{body}</svg>')


# A. Diamond ring: the moment the earbud finishes covering the sun
def concept_a():
    cx, cy, r = 54, 56, 20
    corona = f'<path d="{circle(cx+2.5, cy-2.5, r+2.2)}" fill="{AMBER}"/>'
    bud = f'<path d="{circle(cx, cy, r)}" fill="{INK}"/>'
    gloss = f'<path d="M{cx-12},{cy-6} A13,13 0 0 1 {cx-4},{cy-13}" stroke="#fff" stroke-opacity=".35" stroke-width="2.4" stroke-linecap="round" fill="none"/>'
    fx, fy = cx + (r+1)*math.cos(math.radians(45)), cy - (r+1)*math.sin(math.radians(45))
    flare = (f'<circle cx="{fx:.1f}" cy="{fy:.1f}" r="5.5" fill="{FLARE}"/>'
             f'<circle cx="{fx:.1f}" cy="{fy:.1f}" r="10" fill="{FLARE}" fill-opacity=".25"/>')
    rays = "".join(
        f'<line x1="{fx+math.cos(math.radians(a))*13:.1f}" y1="{fy-math.sin(math.radians(a))*13:.1f}" '
        f'x2="{fx+math.cos(math.radians(a))*(13+L):.1f}" y2="{fy-math.sin(math.radians(a))*(13+L):.1f}" '
        f'stroke="{AMBER}" stroke-width="3.2" stroke-linecap="round"/>'
        for a, L in [(5, 6), (25, 10), (45, 14), (65, 10), (85, 6)])
    return tile(INK, corona + bud + gloss + rays + flare)


# B. Eclipse in progress: the bud slides over the sun, sound waves leak out of the crescent
def concept_b():
    sun = f'<path d="{circle(62, 50, 22)}" fill="{AMBER}"/>'
    bud = f'<path d="{circle(48, 60, 22)}" fill="{INK}" stroke="{AMBER}" stroke-width="0"/>'
    gloss = f'<path d="M34,54 A15,15 0 0 1 42,44" stroke="#fff" stroke-opacity=".35" stroke-width="2.4" stroke-linecap="round" fill="none"/>'
    waves = "".join(
        f'<path d="M{80+i*6},{36+i*-1} A{24+i*7},{24+i*7} 0 0 1 {82+i*6},{62+i*1}" stroke="{AMBER}" stroke-opacity="{1-i*0.3:.2f}" stroke-width="3.2" stroke-linecap="round" fill="none"/>'
        for i in range(3))
    return tile(CREAM, sun + bud + gloss + waves)


# C. Sunbud character: warm sun with the XM5 bud's gloss and a tiny smile; rays are an EQ
def concept_c():
    cx, cy, r = 54, 56, 21
    rays = ""
    for k, L in enumerate([6, 10, 14, 10, 6, 9, 12, 9]):
        a = 90 - k*45 + 22.5
        x1, y1 = cx + (r+6)*math.cos(math.radians(a)), cy - (r+6)*math.sin(math.radians(a))
        x2, y2 = cx + (r+6+L)*math.cos(math.radians(a)), cy - (r+6+L)*math.sin(math.radians(a))
        rays += f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" stroke="{INK}" stroke-width="4" stroke-linecap="round"/>'
    body = f'<path d="{circle(cx, cy, r)}" fill="{INK}"/>'
    gloss = f'<path d="M{cx-13},{cy-5} A14,14 0 0 1 {cx-5},{cy-14}" stroke="#fff" stroke-opacity=".45" stroke-width="2.6" stroke-linecap="round" fill="none"/>'
    eyes = f'<circle cx="{cx-6}" cy="{cy+1}" r="2.2" fill="{AMBER}"/><circle cx="{cx+7}" cy="{cy+1}" r="2.2" fill="{AMBER}"/>'
    smile = f'<path d="M{cx-6},{cy+8} Q{cx+0.5},{cy+13} {cx+7},{cy+8}" stroke="{AMBER}" stroke-width="2.4" stroke-linecap="round" fill="none"/>'
    return tile(AMBER, rays + body + gloss + eyes + smile)


def wordmark(mark_svg):
    inner = mark_svg.split(">", 1)[1].rsplit("</svg>", 1)[0]
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 360 108">'
            f'<g transform="scale(.8) translate(4,14)">{inner}</g>'
            f'<text x="104" y="68" font-family="Bricolage Grotesque, Outfit, Segoe UI, sans-serif" font-size="46" font-weight="800" fill="{INK}" letter-spacing="-1.5">Sun<tspan fill="{DUSK}">Pods</tspan></text></svg>')


concepts = {"a-diamond-ring": concept_a(), "b-eclipse-sound": concept_b(), "c-sunbud": concept_c()}
for name, svg in concepts.items():
    open(os.path.join(OUT, f"concept-{name}.svg"), "w").write(svg)

cards = ""
labels = {"a-diamond-ring": ("A. Diamond ring", "The bud has just covered the sun. One flare of light escapes. Dramatic, premium, night mode native."),
          "b-eclipse-sound": ("B. Eclipse in progress", "The bud slides across the sun and sound leaks out as waves. Motion in a still mark."),
          "c-sunbud": ("C. Sunbud", "A friendly character: the glossy bud is the sun's face. Playful, memorable, great for empty states.")}
for name, svg in concepts.items():
    t, d = labels[name]
    cards += (f'<div class="card"><div class="row">{svg.replace("<svg ", "<svg width=150 height=150 ")}'
              f'{svg.replace("<svg ", "<svg width=56 height=56 ")}</div>'
              f'<div class="wm">{wordmark(svg).replace("<svg ", "<svg width=300 ")}</div><h3>{t}</h3><p>{d}</p></div>')
html = f'''<html><head><link href="https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:wght@600;800&display=swap" rel="stylesheet">
<style>body{{margin:0;background:#F4EFE6;font-family:"Bricolage Grotesque",sans-serif;display:flex;gap:20px;padding:24px}}
.card{{background:#fff;border-radius:28px;padding:22px;width:330px}}.row{{display:flex;align-items:end;gap:18px}}
.wm{{margin-top:14px}}h3{{margin:10px 0 4px;color:#15161A}}p{{margin:0;color:#555;font-size:14px;line-height:1.4}}</style></head>
<body>{cards}</body></html>'''
open(os.path.join(OUT, "concepts.html"), "w").write(html)
print("ok")
