"""Trim transparent margins from the Blender renders and copy them into the app at display size."""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
DST = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "drawable-nodpi")

for name, out, max_w in [("buds_pair", "buds_pair", 1400), ("bud_left", "bud_left", 450),
                         ("bud_right", "bud_right", 450), ("case", "bud_case", 540)]:
    im = Image.open(os.path.join(HERE, name + ".png")).convert("RGBA")
    box = im.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox()
    im = im.crop(box)
    pad = int(max(im.size) * 0.04)
    canvas = Image.new("RGBA", (im.width + 2 * pad, im.height + 2 * pad))
    canvas.paste(im, (pad, pad))
    if canvas.width > max_w:
        canvas = canvas.resize((max_w, round(canvas.height * max_w / canvas.width)), Image.LANCZOS)
    path = os.path.join(DST, out + ".png")
    canvas.save(path, optimize=True)
    print(out, canvas.size, os.path.getsize(path))

# close-up preview on the app's dark surface for review
pair = Image.open(os.path.join(HERE, "buds_pair.png")).convert("RGBA")
bg = Image.new("RGBA", pair.size, (31, 32, 38, 255))
bg.alpha_composite(pair)
bg.convert("RGB").save(os.path.join(HERE, "pair_preview.jpg"), quality=90)
