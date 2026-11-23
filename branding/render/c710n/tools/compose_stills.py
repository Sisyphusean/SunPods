# Frames the raw WF-C710N renders (stills.mjs) like the XM5 app stills and writes them to
# app/src/main/res/drawable-nodpi: the subject is cropped to its alpha, scaled to fit the XM5 file's
# subject box (contain, centred) on a transparent canvas of the XM5 file's size.
#   python compose_stills.py <rawDir> [colours...]
import os, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.normpath(os.path.join(HERE, '..', '..', '..', '..', 'app', 'src', 'main', 'res', 'drawable-nodpi'))
raw = sys.argv[1]
colours = sys.argv[2:] or ['black', 'white', 'pink', 'blue']
# (raw name, out name, canvas size, subject box) - the boxes are the XM5 files' alpha boxes
SPECS = [
    ('budL', 'c710n_bud_left_{c}.png', (360, 303), (29, 22, 305, 261)),
    ('budR', 'c710n_bud_right_{c}.png', (360, 303), (55, 22, 331, 261)),
    ('case', 'c710n_bud_case_{c}.png', (270, 218), (0, 0, 270, 218)),
    ('pair', 'c710n_buds_pair_{c}.png', (1000, 712), (80, 65, 929, 652)),
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
