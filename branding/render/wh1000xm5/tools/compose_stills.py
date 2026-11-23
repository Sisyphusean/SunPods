# Frames the raw WH-1000XM5 renders (stills.mjs) like the XM5 app stills and writes them to
# app/src/main/res/drawable-nodpi: the subject is cropped to its alpha, scaled to fit the subject box
# (contain, centred) on a transparent canvas of the earbud file's size.
#   python compose_stills.py <rawDir> [colours...]
import os, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.normpath(os.path.join(HERE, '..', '..', '..', '..', 'app', 'src', 'main', 'res', 'drawable-nodpi'))
raw = sys.argv[1]
colours = sys.argv[2:] or ['black', 'silver', 'blue', 'pink']
# (raw name, out name, canvas size, subject box): the side and front views where the single earbuds
# were (360 x 303, the earbud files' size), the closed case like bud_case, the hero like buds_pair
SPECS = [
    ('side', 'wh1000xm5_side_{c}.png', (360, 303), (40, 10, 320, 293)),
    ('front', 'wh1000xm5_front_{c}.png', (360, 303), (40, 10, 320, 293)),
    ('case', 'wh1000xm5_case_{c}.png', (270, 218), (0, 0, 270, 218)),
    ('pair', 'wh1000xm5_pair_{c}.png', (1000, 712), (80, 65, 929, 652)),
]

def place(im, size, box):
    im = im.convert('RGBA')
    im = im.crop(im.getchannel('A').getbbox())
    bw, bh = box[2] - box[0], box[3] - box[1]
    s = min(bw / im.width, bh / im.height)
    w, h = max(1, round(im.width * s)), max(1, round(im.height * s))
    im = im.resize((w, h), Image.LANCZOS)
    out = Image.new('RGBA', size, (0, 0, 0, 0))
    out.alpha_composite(im, (box[0] + (bw - w) // 2, box[1] + (bh - h) // 2))
    return out

for c in colours:
    for key, name, size, box in SPECS:
        out = place(Image.open(os.path.join(raw, f'{key}_{c}.png')), size, box)
        dst = os.path.join(APP, name.format(c=c))
        out.save(dst, optimize=True)
        print(os.path.basename(dst), out.size, out.getchannel('A').getbbox())
