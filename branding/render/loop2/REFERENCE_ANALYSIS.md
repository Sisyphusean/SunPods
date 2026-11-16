# Reference Analysis: Sony WF-1000XM5 LEFT earbud (black)

**Reference:** Sony official 360 frames ref360\00..11.png (Smoky Pink colourway, shape reference), hero\ref_face.png + ref_side.png (Black, colour reference)
**Existing file:** new scene (xm5_bud.blend is archive only)
**Target:** SunPods branding stills, flat even light, pure white background
**Match goal:** close match from every 360 angle

## 1. Subject
- Type: consumer electronics prop, glossy injection-moulded shell + silicone/foam tip
- Forward axis (our convention): outer face toward -Y; SONY side toward -X; inner (ear) side and nozzle toward +Y; up +Z
- Scale: body about 1.9 H x 1.75 W x 1.75 D (units = cm)

## 2. Silhouette and proportions, by angle
- **Front (03, 04, 10, 11, 05):** outer face is a rounded "guitar pick" / inverted teardrop: broad round top, sides converging to a rounded point at lower centre-left. Face panel gently domed. A soft ridge runs around the face perimeter; the body shell bulges past the face at upper right (lighter crescent band in 03/10). Mic grille sits at the face's lower point. Ear tip pokes out at lower left, about one third of body height.
- **Outer side (06, hero side):** body reads as a near-sphere, slightly taller than deep. A curved parting line separates the face shell from the rear shell, running top to bottom roughly 40 % back from the face. Serif SONY wordmark runs vertically (reads bottom-to-top, letters rotated) on the outer side just behind the face edge. Grille visible at lower right (face tip wraps onto the side). Small pinhole mic on the top seam.
- **Inner side (07, 08):** flatter rear shell. Two gold round contacts near the top, a light grey circular "L" badge below between them, a black circular sensor lower left. Nozzle exits the lower front of the inner side; tip is a large tapered cylinder (wider at the body end? no: wider at the outer mouth, slightly tapered toward body) with a dark mesh disc in its bore.
- **Top/back (09):** egg-shaped crown, rear shell with a smooth S-curved crease where shell panels meet; tip seen from behind at lower right.
- **Bottom (02, 05):** the face point and grille wrap under; tip and nozzle dominate the lower half.
- Sony shoots front frames from slightly below (we see a little of the underside).

## 3. Camera
- Turntable of 10 frames (02-11), subject fills ~30 % of the 900 px frame, telephoto-ish (minimal perspective, ~85-100 mm feel).
- Board cameras: MATCH table in board_iter.py (azimuth, elevation) to be re-fitted to the new model.

## 4. Lighting
- Flat, soft, even studio light; very soft top-left highlight streaks on the gloss; no hard shadows; pure white background.
- Plan: world white at strength ~1, two large soft area lights (upper-left key, right fill), Standard view transform for board, AgX/Filmic only if needed.

## 5. Colour palette (black colourway, from hero shots)
| Role | Hex | Notes |
|------|-----|-------|
| Shell base | #1E2224 | glossy black, very slightly blue-green |
| Shell highlight | #6E7A7E | specular streaks |
| Seam / recess | #0C0E0F | parting line |
| Grille copper | #B88A6E | hex-perforated, flush |
| SONY / contacts gold | #C9A27A | |
| Ear tip | #2A2C2E | matte silicone/foam |
| Tip bore mesh | #0A0A0A | |
| L badge | #8A8C8E | light grey disc, darker L |
| Background | #FFFFFF | |

## 6. Materials
- Shell: Principled, base #1E2224, roughness 0.12-0.18, clearcoat 0.6
- Grille: metallic copper, roughness 0.35, hex hole pattern (flush, never recessed)
- Tip: roughness 0.7, slight sheen/foam noise
- Contacts: gold metal, roughness 0.25

## 7. Hero features (must include)
- [ ] Guitar-pick face with perimeter ridge and shell bulge at upper right
- [ ] Flush copper hex-perforated grille at face tip
- [ ] Serif SONY wordmark on outer side near face edge
- [ ] Parting line between face shell and rear shell
- [ ] Inner side: two gold contacts, L badge, black sensor
- [ ] Nozzle + large tapered tip with mesh, at lower left in front frames
- [ ] Top pinhole mic

## 9. Active skills
blender-director, prop-artist / hard-surface, materials, lookdev, rendering, qa-review

## 10. Plan
Blockout body (UV sphere, scale, proportional edits) -> face panel shape -> nozzle + tip -> parting line + face ridge -> grille -> inner details -> SONY decal -> materials -> lights -> render_iter/board_iter into loop2\iterNN -> self review -> Fable review. 20 iterations.
