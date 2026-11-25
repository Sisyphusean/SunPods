# Packs rendered frames into an animated WebP for the app, and a review sheet on SunPods' dark
# background:  python pack.py <frames dir> <out.webp> [size] [step]
import sys, os
from PIL import Image

src, dst = sys.argv[1], sys.argv[2]
size = int(sys.argv[3]) if len(sys.argv) > 3 else 480
step = int(sys.argv[4]) if len(sys.argv) > 4 else 1
# WebP effort: 6 (default, slowest); 4 encodes about 35x faster at the same quality, ~8% larger
method = int(sys.argv[5]) if len(sys.argv) > 5 else 6
files = sorted(f for f in os.listdir(src) if f.endswith(".png"))[::step]
frames = [Image.open(os.path.join(src, f)).convert("RGBA").resize((size, size), Image.LANCZOS) for f in files]
frames[0].save(dst, save_all=True, append_images=frames[1:], duration=int(1000 / 30 * step), loop=0,
               quality=82, method=method, lossless=False, allow_mixed=False)
print(dst, len(frames), "frames", os.path.getsize(dst) // 1024, "KB")

# Review sheet: up to 12 evenly spaced frames on the app's dark background.
bg = (20, 18, 24, 255)
pick = frames[:: max(1, len(frames) // 12)][:12]
cols = 6
sheet = Image.new("RGBA", (cols * size, ((len(pick) + cols - 1) // cols) * size), bg)
for i, f in enumerate(pick):
    tile = Image.new("RGBA", (size, size), bg)
    tile.alpha_composite(f)
    sheet.paste(tile, ((i % cols) * size, (i // cols) * size))
sheet.convert("RGB").save(os.path.splitext(dst)[0] + "_sheet.jpg", quality=85)
