Start from scratch: model a Sony WF-1000XM5 LEFT earbud (black colourway) in Blender 3.2 for my SunPods app branding renders. Ignore the old model (branding\render\xm5_bud.blend is archive only, do not build on it).

PRIMARY REFERENCE (most important): Sony's official 360° product images, showing the bud from every angle:
C:\Users\peyio\Documents\SonyPodsBattery\branding\render\ref360\00.png … 11.png
(00 and 01 show both buds; 02–11 are the single left bud turning on a turntable; contact sheet: ref360\_sheet.png)
Secondary: ref360\hero\ref_face.png and ref_side.png (Sony black hero shots).
Study ALL twelve 360 frames before modelling and write a Reference Analysis covering every angle (front, both sides, inner side, top, bottom). The model must match the device from every 360 angle, not just the hero view.

Known facts: the copper mic grille is FLUSH with the plastic (never recessed), hex-perforated; serif SONY wordmark on the outer side near the face edge; inner side has two gold contacts, an "L" badge and a black sensor; the ear tip is large, tapered silicone and shows at lower left in the front frames; Sony's front frames are shot from slightly below.

How to work:
- Use the Blender MCP (blender-mcp, port 9876) only, in small artist steps (primitives, edit-mode ops, modifiers), with a look/screenshot after each step. No geometry generated outside Blender.
- Start with the blender-skills:blender-director skill and follow its reference-image-match workflow; use prop-artist / hard-surface, lookdev, rendering and qa-review as needed.
- Every render is flat, evenly lit, on a pure white background (like Sony's 360 frames).

Iteration model, 20 iterations:
1. One clear goal per iteration.
2. Render, then build a comparison board that pairs EVERY Sony 360 frame (02–11) with our model rendered from a matching camera, plus the two hero pairs. Re-fit the cameras to the new model. Reuse and adapt branding\render\review_tools\render_iter.py and board_iter.py (point their output to branding\render\loop2\iterNN and backups to loop2\backup_iterNN.blend).
3. Review the board yourself against the 360 frames and fix obvious problems first.
4. Then send it to an independent Fable 5.2 reviewer (Agent, model fable). Every review MUST include the board AND the original Sony 360 frames, plus the plugin's references/visual-match-checklist.md as the rubric. Never tell the reviewer what you think completion is: no previous scores, no self-verdicts, only the files and a factual list of what changed.
5. Back up the .blend every iteration.
