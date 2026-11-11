"""Skin of the pterodactyl (Stark 2026-11-11): 64x32, painted face by face over the boxes of client/animal/PteroModel.
Dusty brown back, pale belly, leathery wings darker at the edges with the finger bone along the front and veins
running out, the long pale beak, a red crest, yellow eye. Every pixel from noise, no stamped pattern.
python3 tools/mobs/ptero.py  (writes textures/entity/pterodactilo.png)"""
import os, numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src/main/resources/assets/battlegroundsmod/textures/entity')
BOXES = {'body': (0, 0, 4, 3, 8), 'neck': (0, 11, 2, 2, 4), 'head': (12, 11, 3, 3, 3), 'beak': (24, 11, 1, 2, 7),
         'crest': (24, 0, 1, 2, 6), 'tail': (40, 0, 1, 1, 5), 'legs': (40, 8, 3, 1, 4), 'wing': (0, 18, 9, 1, 6),
         'tip': (0, 26, 11, 1, 4)}


def faces(u, v, w, h, d):
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h),
            'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + d + d + w, v + d, w, h)}


rng = np.random.default_rng(23)
img = np.zeros((32, 64, 4), np.uint8)
BACK = np.array((138, 92, 66.0)); BELLY = np.array((214, 190, 150.0))
MEMBRANE = np.array((150, 84, 62.0)); EDGE = np.array((86, 46, 38.0)); BONE = np.array((112, 74, 54.0))
for part, (u, v, w, h, d) in BOXES.items():
    for f, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
        for yy in range(fh):
            for xx in range(fw):
                n = 0.86 + rng.random() * 0.24
                if part in ('wing', 'tip'):
                    # across the wing: front edge (low yy on top face) is the bone, the rest membrane with veins
                    if f in ('top', 'bottom'):
                        t = yy / max(1, fh - 1)
                        c = BONE if yy == 0 else MEMBRANE * (1 - t * 0.35) + EDGE * t * 0.35
                        if part == 'tip' and xx > fw - 3:
                            c = EDGE
                        if yy > 0 and (xx * 7 + yy * 3 + int(rng.integers(0, 3))) % 9 == 0:
                            c = c * 0.75
                        if f == 'bottom':
                            c = c * 0.85 + BELLY * 0.15
                    else:
                        c = EDGE
                elif part == 'beak':
                    c = np.array((226, 208, 160.0)) if f != 'bottom' else np.array((196, 176, 132.0))
                elif part == 'crest':
                    c = np.array((178, 52, 40.0))
                elif part == 'legs':
                    c = np.array((92, 70, 52.0))
                else:
                    t = 0.0 if f == 'top' else 1.0 if f == 'bottom' else (yy + 0.5) / fh
                    c = BACK * (1 - t ** 1.5) + BELLY * t ** 1.5
                img[y0 + yy, x0 + xx] = (*np.clip(c * n, 0, 255).astype(int), 255)
# eyes on the head's sides
u, v, w, h, d = BOXES['head']
fc = faces(u, v, w, h, d)
rx, ry, rw, rh = fc['right']; lx, ly, lw, lh = fc['left']
img[ry + 1, rx] = (240, 205, 50, 255)
img[ly + 1, lx + lw - 1] = (240, 205, 50, 255)
Image.fromarray(img, 'RGBA').save(os.path.join(OUT, 'pterodactilo.png'))
print('ok')
