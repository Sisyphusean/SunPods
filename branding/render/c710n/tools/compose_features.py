# Feature sheet for the review packet: each model view (features.mjs) beside the Sony material that
# shows the same feature (crops of Sony's photos, the help guide drawings, one review photo).
#   python compose_features.py <iterDir>   -> <iterDir>/features.jpg
import os, sys
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
C7 = os.path.normpath(os.path.join(HERE, '..'))
it = sys.argv[1]
try:
    F = ImageFont.truetype('arial.ttf', 17)
except OSError:
    F = None
T = 300

def tile(p, crop=None, bg=(255, 255, 255)):
    im = Image.open(p).convert('RGBA')
    if crop: im = im.crop(crop)
    b = Image.new('RGBA', im.size, bg + (255,)); b.alpha_composite(im)
    im = b.convert('RGB'); im.thumbnail((T, T), Image.LANCZOS)
    c = Image.new('RGB', (T, T), bg); c.paste(im, ((T - im.width) // 2, (T - im.height) // 2))
    return c

R = lambda *p: os.path.join(C7, *p)
rows = [
    ('Case back: pairing button, USB-C, hinge (SoundGuys review photo, help guide)',
     [tile(R('ref_extra', 'soundguys_Sony-WF-C710N-charging-port.jpg'), (700, 430, 1400, 790)), tile(R('ref_helpguide', 'i0110_wfc710n.png')),
      tile(os.path.join(it, 'feat_case_back_black.png')), tile(os.path.join(it, 'feat_case_back_blue.png'))]),
    ('Case top and Glass Blue open 3/4 (Sony lifestyle shots)',
     [tile(R('ref_white', 'sonyus_life_table.png'), (330, 640, 650, 940)), tile(os.path.join(it, 'feat_case_top_white.png')),
      tile(R('ref_blue', 'sonyus_life_table.png'), (700, 500, 1100, 900)), tile(os.path.join(it, 'feat_case_open34_blue.png'))]),
    ('Earbud inner side: contacts, IR sensor, L/R mark, vent (Sony 360 frame 07, help guide)',
     [tile(R('ref_blue', 'spin360', 's360_07-42.png'), (840, 380, 2080, 1380)), tile(R('ref_helpguide', 'i0100_wfc710n.png'), (0, 170, 240, 371)),
      tile(os.path.join(it, 'feat_bud_inner_R_black.png')), tile(os.path.join(it, 'feat_bud_inner_L_white.png'))]),
    ('Earbud top (air hole, L tactile dot) and 3/4 (Sony face-on white, UK diagram bud)',
     [tile(R('ref_white', 'sonyus_buds_front.png'), (700, 260, 1150, 560)), tile(os.path.join(it, 'feat_bud_top_L_black.png')),
      tile(R('ref_black', 'sonyuk_09-black-min.png'), (880, 480, 1460, 940)), tile(os.path.join(it, 'feat_bud_34_R_pink.png'))]),
]
W = 4 * (T + 6) + 6
sheet = Image.new('RGB', (W, len(rows) * (T + 30) + 10), (235, 235, 235))
d = ImageDraw.Draw(sheet)
for r, (label, tiles) in enumerate(rows):
    y = 6 + r * (T + 30)
    d.text((8, y), label + '   [Sony/ref | ... | model]', fill=(0, 0, 0), font=F)
    for c, t in enumerate(tiles): sheet.paste(t, (6 + c * (T + 6), y + 24))
sheet.save(os.path.join(it, 'features.jpg'), quality=90)
print(os.path.join(it, 'features.jpg'), sheet.size)
