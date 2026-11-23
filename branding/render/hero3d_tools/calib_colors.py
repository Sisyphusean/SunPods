# Solves the Platinum Silver and Smoky Pink body albedos for hero3d.js from Sony's official shots.
# Sony shot all three colours in one session (same light), so each region of the earbud and case has
# a known target colour per finish. This page's own lighting is measured by rendering the hero with
# the body albedo at 0 and at 0.5 (calibration finishes, via shoot.mjs), giving per region
#     value = albedo * E + S    (linear RGB)
# and then albedo = (Sony value - S) / E. The same solve on Sony's black shot returns the approved
# black face albedo, which is the check that the absolute match is fair.
#   python calib_colors.py            (writes renders to hero3d_tools/_calib/, prints albedos)
import json, os, subprocess, sys
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
R = os.path.normpath(os.path.join(HERE, '..'))
OUT = os.path.join(HERE, '_calib')
BLACK = {"gloss": [0.008, 0.009, 0.010], "satin": [0.150, 0.162, 0.178], "hair": [0.120, 0.130, 0.140],
         "grille": [0.8388, 0.491, 0.314], "logo": [0.6795, 0.4851, 0.305], "tip": [0.075, 0.077, 0.080],
         "badge": [0.13, 0.135, 0.14], "badgeR": [0.13, 0.135, 0.14], "badgeL": [0.85, 0.86, 0.87],
         "caseShell": [0.0169, 0.0223, 0.0271], "caseInner": [0.012, 0.013, 0.015],
         "caseLogo": [1.0, 0.62, 0.36], "caseLogoEm": [0.6795, 0.4851, 0.305], "led": [0.07, 0.07, 0.072]}
shots = []
for v in (0.0, 0.5):
    f = dict(BLACK)
    for k in ("gloss", "satin", "hair", "tip", "caseShell", "caseInner"): f[k] = [v, v, v]
    nm = "cal%d" % int(v * 10)
    shots += [{"name": nm + "_buds", "color": nm, "model": "buds", "angle": 0, "finish": f},
              {"name": nm + "_case", "color": nm, "model": "case", "angle": 0, "lid": 0, "finish": f}]
os.makedirs(OUT, exist_ok=True)
json.dump(shots, open(os.path.join(OUT, 'shots.json'), 'w'))
if '--skip-render' not in sys.argv:
    subprocess.run(['node', os.path.join(HERE, 'shoot.mjs'), os.path.join(OUT, 'shots.json'), OUT], check=True)

def lin(a): a = a / 255.0; return np.where(a <= 0.04045, a / 12.92, ((a + 0.055) / 1.055) ** 2.4)
def load(p):
    im = Image.open(p).convert('RGBA'); b = Image.new('RGBA', im.size, (255, 255, 255, 255)); b.alpha_composite(im)
    return lin(np.asarray(b.convert('RGB')).astype(float))
def reg(A, boxes): return np.mean([A[y0:y1, x0:x1].reshape(-1, 3).mean(0) for (x0, y0, x1, y1) in boxes], axis=0)
# boxes: ours at 1000x712 hero framing; Sony's at 1534x1083 (same layout for all three colours)
OURS = {'face': [(225, 210, 300, 285), (700, 385, 775, 460)], 'tip': [(430, 310, 480, 390), (510, 470, 570, 530)],
        'case_front': [(380, 400, 460, 470), (500, 400, 580, 470)]}
SONY = {'face': [(300, 440, 380, 520), (1010, 440, 1090, 520)], 'tip': [(560, 560, 620, 640)],
        'case_front': [(600, 580, 720, 700), (800, 580, 920, 700)]}
c0b, c5b = load(os.path.join(OUT, 'cal0_buds.png')), load(os.path.join(OUT, 'cal5_buds.png'))
c0c, c5c = load(os.path.join(OUT, 'cal0_case.png')), load(os.path.join(OUT, 'cal5_case.png'))
sony = {'black': ('ref_silver/_black_02_buds_front_for_compare.png', 'ref_silver/_black_05_case_for_compare.png'),
        'pink': ('ref_pink/p02_buds_front.png', 'ref_pink/p05_case_closed.png'),
        'silver': ('ref_silver/w02_buds_front.png', 'ref_silver/w05_case_closed.png')}
for region, (z, h) in (('face', (c0b, c5b)), ('tip', (c0b, c5b)), ('case_front', (c0c, c5c))):
    S = reg(z, OURS[region]); E = (reg(h, OURS[region]) - S) / 0.5
    print(region, 'S', S.round(4), 'E', E.round(4))
    for col, (bud, case) in sony.items():
        T = reg(load(os.path.join(R, bud if region != 'case_front' else case)), SONY[region])
        print('   ', col, 'Sony', T.round(4), 'albedo', ((T - S) / E).round(3))
