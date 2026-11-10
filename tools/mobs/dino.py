"""Skins of the dinosaurs (Stark 2026-11-10): one 64x64 texture per kind, painted face by face over the boxes of
client/animal/DinoModel (same offsets and sizes): scaly backs with darker stripes, a pale belly, the eyes and teeth on
the head, claws on the feet. Every pixel comes from noise, no stamped pattern.
python3 tools/mobs/dino.py  (writes textures/entity/tiranossauro.png and raptor.png)"""
import os, numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src/main/resources/assets/battlegroundsmod/textures/entity')
# name: (u, v, w, h, d)  -- the same as DinoModel.create()
BOXES = {'body': (0, 0, 6, 6, 12), 'neck': (0, 18, 4, 6, 4), 'head': (36, 0, 5, 4, 6), 'jaw': (36, 10, 4, 2, 5),
         'tail1': (16, 18, 4, 4, 8), 'tail2': (40, 18, 2, 3, 8), 'thigh': (0, 30, 3, 6, 4), 'shin': (14, 30, 2, 5, 2),
         'foot': (22, 30, 3, 1, 4), 'arm': (36, 30, 1, 3, 1)}


def faces(u, v, w, h, d):
    """Vanilla cube unwrap: name -> (x0, y0, width, height)."""
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h),
            'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + d + d + w, v + d, w, h)}


def paint(name, back, belly, stripe, seed):
    rng = np.random.default_rng(seed)
    img = np.zeros((64, 64, 4), np.uint8)
    back, belly, stripe = (np.array(c, float) for c in (back, belly, stripe))
    for part, (u, v, w, h, d) in BOXES.items():
        for f, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
            for yy in range(fh):
                for xx in range(fw):
                    # how far down the side this pixel is (0 = back, 1 = belly)
                    if f == 'top':
                        t = 0.0
                    elif f == 'bottom':
                        t = 1.0
                    else:
                        t = (yy + 0.5) / fh
                    c = back * (1 - t ** 1.6) + belly * t ** 1.6
                    # stripes across the back and sides, broken by noise
                    along = (xx if f in ('right', 'left') else yy if f in ('top', 'bottom') else 0)
                    if part in ('body', 'tail1', 'tail2', 'neck') and t < 0.65 and (along + int(rng.integers(0, 2))) % 4 == 0:
                        c = c * 0.35 + stripe * 0.65
                    c = c * (0.86 + rng.random() * 0.22)
                    img[y0 + yy, x0 + xx] = (*np.clip(c, 0, 255).astype(int), 255)
    # head: yellow eyes with a slit near the front of each side, nostrils, teeth along the lower edge
    u, v, w, h, d = BOXES['head']
    fc = faces(u, v, w, h, d)
    rx, ry, rw, rh = fc['right']; lx, ly, lw, lh = fc['left']
    for ex, ey in ((rx + rw - 2, ry + 1), (lx + 1, ly + 1)):
        img[ey, ex] = (235, 200, 40, 255)
        img[ey, ex + (1 if ex < lx else -1)] = (20, 15, 10, 255)
    fx, fy, fw, fh = fc['front']
    img[fy, fx + 1] = img[fy, fx + fw - 2] = (25, 20, 15, 255)
    for x in range(fx, fx + fw):
        img[fy + fh - 1, x] = (240, 236, 220, 255) if x % 2 == 0 else (200, 190, 170, 255)
    for f in ('right', 'left'):
        x0, y0, fw2, fh2 = fc[f]
        for x in range(x0, x0 + fw2):
            if (x - x0) % 2 == 0:
                img[y0 + fh2 - 1, x] = (240, 236, 220, 255)
    # jaw: teeth on top edge, red mouth inside on the top face
    u, v, w, h, d = BOXES['jaw']
    fc = faces(u, v, w, h, d)
    x0, y0, fw2, fh2 = fc['top']
    img[y0:y0 + fh2, x0:x0 + fw2] = (150, 40, 45, 255)
    for f in ('right', 'left', 'front'):
        x0, y0, fw2, fh2 = fc[f]
        for x in range(x0, x0 + fw2, 2):
            img[y0, x] = (240, 236, 220, 255)
    # dark claws at the toes and on the hands
    u, v, w, h, d = BOXES['foot']
    x0, y0, fw2, fh2 = faces(u, v, w, h, d)['front']
    img[y0:y0 + fh2, x0:x0 + fw2] = (40, 35, 30, 255)
    u, v, w, h, d = BOXES['arm']
    for f, (x0, y0, fw2, fh2) in faces(u, v, w, h, d).items():
        if f != 'top':
            img[y0 + fh2 - 1, x0:x0 + fw2] = (40, 35, 30, 255)
    Image.fromarray(img, 'RGBA').save(os.path.join(OUT, name + '.png'))


# the tyrannosaur of the Dragon World: green-brown with dark bands, cream belly
paint('tiranossauro', (92, 112, 58), (214, 200, 150), (48, 60, 32), 7)
# the raptors: sandy brown with dark stripes, pale belly
paint('raptor', (150, 110, 62), (226, 210, 170), (70, 48, 30), 11)
