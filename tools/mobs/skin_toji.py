"""Toji Fushiguro's skin (the Jujutsu end-game boss, Stark 2026-11-22), painted on the player skin layout (64x64) like
skins_bosses.py: short black hair, green eyes, the scar at the right corner of his mouth, the black tight short-sleeved
shirt over the muscles, white baggy trousers tied at the ankle, black shoes, and the purple Inventory Curse worm
coiled round his shoulders and neck (the outer layer).
python3 tools/mobs/skin_toji.py"""
import os
import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src/main/resources/assets/battlegroundsmod/textures/entity')

PARTS = {'head': (0, 0, 8, 8, 8), 'hat': (32, 0, 8, 8, 8), 'body': (16, 16, 8, 12, 4), 'jacket': (16, 32, 8, 12, 4),
         'rarm': (40, 16, 4, 12, 4), 'larm': (32, 48, 4, 12, 4), 'rsleeve': (40, 32, 4, 12, 4), 'lsleeve': (48, 48, 4, 12, 4),
         'rleg': (0, 16, 4, 12, 4), 'lleg': (16, 48, 4, 12, 4), 'rpants': (0, 32, 4, 12, 4), 'lpants': (0, 48, 4, 12, 4)}


def faces(u, v, w, h, d):
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h),
            'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + d + d + w, v + d, w, h)}


img = np.zeros((64, 64, 4), np.uint8)
rng = np.random.default_rng(122)


def px(x, y, c, var=0.06):
    if c is None:
        return
    k = 1 + (rng.random() - 0.5) * 2 * var
    img[y, x] = (*np.clip(np.array(c[:3]) * k, 0, 255).astype(int), 255)


def part(name, fn):
    u, v, w, h, d = PARTS[name]
    for f, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
        for y in range(fh):
            for x in range(fw):
                px(x0 + x, y0 + y, fn(f, x, y, fw, fh))


SK, SK2 = (214, 166, 128), (184, 136, 102)
HAIR, HAIR2 = (22, 20, 24), (40, 38, 46)
SHIRT, SHIRT2, SHIRT3 = (26, 26, 30), (44, 44, 52), (16, 16, 18)
PANTS, PANTS2 = (226, 224, 218), (192, 190, 184)
SHOE = (24, 22, 24)
EYE, WHITE = (70, 140, 96), (236, 234, 228)
SCAR = (150, 96, 84)
WORM, WORM2, WORM3 = (122, 76, 150), (156, 104, 184), (88, 50, 112)


def head(f, x, y, w, h):
    if f == 'top':
        return HAIR if (x + y * 3) % 5 else HAIR2
    if f == 'bottom':
        return SK
    if f == 'back':
        return HAIR if y < 5 or (y == 5 and x % 2) else SK
    if f in ('right', 'left'):
        return HAIR if y < 2 or (y < 4 and ((f == 'right' and x < 3) or (f == 'left' and x > 4))) else SK
    # front: messy short fringe, sharp eyebrows, green eyes, the scar at his right mouth corner, a flat mouth
    if y == 0 or (y == 1 and x in (0, 1, 3, 4, 6, 7)):
        return HAIR if (x + y) % 3 else HAIR2
    if y == 2 and x in (1, 2, 5, 6):
        return (36, 30, 30)
    if y == 3 and x in (1, 2, 5, 6):
        return EYE if x in (2, 5) else WHITE
    if y == 4 and x in (3, 4):
        return SK2
    if y == 5 and x == 2:
        return SCAR
    if y == 6 and x == 2:
        return SCAR
    if y == 6 and x in (3, 4, 5):
        return (130, 84, 74)
    return SK


part('head', head)
# the hat layer: a few spikes of hair standing up at the front and the crown
part('hat', lambda f, x, y, w, h: HAIR if (f == 'top' and (x + y) % 3 == 0) or (f == 'front' and y == 0 and x in (1, 4, 6)) else None)


def body(f, x, y, w, h):
    if f == 'top':
        return SHIRT
    if f == 'bottom':
        return PANTS
    if y >= 10:
        # the trousers' waist, a dark cord
        return SHIRT3 if y == 10 else PANTS
    if f == 'front':
        # the tight shirt: pecs and abs in light and shade
        if y in (3, 4) and x in (1, 2, 5, 6):
            return SHIRT2
        if y == 4 and x in (3, 4):
            return SHIRT3
        if y in (6, 8) and x in (2, 5):
            return SHIRT2
        if x in (3, 4) and y >= 5:
            return SHIRT3
        return SHIRT
    if f == 'back':
        return SHIRT2 if y in (2, 3) and x in (1, 2, 5, 6) else SHIRT
    return SHIRT


part('body', body)


# the Inventory Curse: a fat purple worm coiled round his shoulders and neck (outer layer)
def worm(f, x, y, w, h):
    if f == 'top':
        return WORM if (x + y) % 3 else WORM2
    if f == 'front':
        if y == 0 and 1 <= x <= 6:
            return WORM2 if x % 2 else WORM
        if y == 1 and x in (0, 1, 6, 7):
            return WORM3 if x in (0, 7) else WORM
        return None
    if f == 'back':
        if y <= 1:
            return WORM if (x + y) % 2 else WORM2
        return None
    if f in ('right', 'left'):
        return (WORM if y == 0 else WORM3) if y <= 1 else None
    return None


part('jacket', worm)


def arm(left):
    def fn(f, x, y, w, h):
        if f == 'top':
            return SHIRT
        if y < 3:
            return SHIRT if f != 'bottom' else SK
        if f == 'bottom':
            return SK2
        # bare muscular arms: a line of shade down the biceps and the forearm
        if y in (5, 6) and ((f == 'front' and x in (1, 2)) or (f in ('right', 'left') and x == 1)):
            return SK2
        if y == 11:
            return SK2
        return SK
    return fn


part('rarm', arm(False))
part('larm', arm(True))
# the worm's tail over his right shoulder
part('rsleeve', lambda f, x, y, w, h: (WORM if (x + y) % 2 else WORM2) if (f == 'top' or (y == 0 and f != 'bottom')) else None)


def leg(f, x, y, w, h):
    if f == 'top':
        return PANTS
    if f == 'bottom' or y >= 11:
        return SHOE
    if y == 10:
        return PANTS2
    # baggy: folds in the cloth
    if (x + y) % 5 == 0 or (y in (3, 7) and x % 2):
        return PANTS2
    return PANTS


part('rleg', leg)
part('lleg', leg)
Image.fromarray(img, 'RGBA').save(os.path.join(OUT, 'toji.png'))
