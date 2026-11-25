# Inner side and case close-up board (tools only): Sony's pictures (left) beside the model (right).
#   python extra_board.py <renderDir> <out.jpg>
import os, sys
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__)); X4 = os.path.normpath(os.path.join(HERE, '..'))
rd, out = sys.argv[1], sys.argv[2]
T = 380
def ref(img, box):
    im = Image.open(os.path.join(X4, img)).convert('RGBA'); bg = Image.new('RGBA', im.size, (255, 255, 255, 255)); bg.alpha_composite(im)
    im = bg.convert('RGB').crop(box); k = min(T / im.width, T / im.height); im = im.resize((round(im.width * k), round(im.height * k)), Image.LANCZOS); t = Image.new('RGB', (T, T), (255, 255, 255)); t.paste(im, ((T - im.width) // 2, (T - im.height) // 2)); return t
def model(k):
    im = Image.open(os.path.join(rd, k + '.png')).convert('RGBA'); im = im.crop(im.getchannel('A').getbbox())
    im.thumbnail((T - 20, T - 20), Image.LANCZOS); t = Image.new('RGBA', (T, T), (255, 255, 255, 255)); t.alpha_composite(im, ((T - im.width) // 2, (T - im.height) // 2)); return t.convert('RGB')
ROWS = [
    ('inner side L (help guide drawing)', ('ref_helpguide/i0100_wf1000xm4.png', (8, 262, 118, 378)), 'inner_L'),
    ('inner side R (help guide drawing)', ('ref_helpguide/i0100_wf1000xm4.png', (112, 262, 222, 378)), 'inner_R'),
    ('inner shell (sony.jp exploded view)', ('ref_sony/y_WF-1000XM4_045.jpg', (700, 30, 950, 320)), 'inner_shell'),
    ('case open, black (sony.jp)', ('ref_sony/original_WF-1000XM4_008.jpg', (175, 75, 305, 185)), 'case_open'),
    ('case closed, black (sony.jp)', ('ref_sony/y_WF-1000XM4_024.jpg', (215, 270, 360, 340)), 'case_closed'),
    ('case closed, silver (sony.jp app picture)', ('ref_sony/original_WF-1000XM4_011.jpg', (370, 140, 500, 220)), 'case_silver'),
]
sheet = Image.new('RGB', (2 * (2 * T + 30), 3 * (T + 30)), (235, 235, 235)); d = ImageDraw.Draw(sheet)
for n, (label, (img, box), k) in enumerate(ROWS):
    x, y = (n % 2) * (2 * T + 30), (n // 2) * (T + 30)
    sheet.paste(ref(img, box), (x, y + 24)); sheet.paste(model(k), (x + T + 6, y + 24)); d.text((x + 4, y + 6), label + ' | model', fill=(0, 0, 0))
sheet.save(out, quality=90); print(out)
