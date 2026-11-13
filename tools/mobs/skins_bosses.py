"""Skins of the four bosses of 2026-11-12 (Joy Boy, Saitama, Shigaraki, Makima) and Hashirama (2026-11-13, plus his Sage Mode), painted on the player skin layout
(64x64) with a little noise in every colour so no two pixels repeat. Makima uses the slim (3 px) arms.
python3 tools/mobs/skins_bosses.py"""
import os
import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src/main/resources/assets/battlegroundsmod/textures/entity')


def parts(slim):
    a = 3 if slim else 4
    return {'head': (0, 0, 8, 8, 8), 'hat': (32, 0, 8, 8, 8), 'body': (16, 16, 8, 12, 4), 'jacket': (16, 32, 8, 12, 4),
            'rarm': (40, 16, a, 12, 4), 'larm': (32, 48, a, 12, 4), 'rsleeve': (40, 32, a, 12, 4), 'lsleeve': (48, 48, a, 12, 4),
            'rleg': (0, 16, 4, 12, 4), 'lleg': (16, 48, 4, 12, 4), 'rpants': (0, 32, 4, 12, 4), 'lpants': (0, 48, 4, 12, 4)}


def faces(u, v, w, h, d):
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h),
            'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + d + d + w, v + d, w, h)}


class Skin:
    def __init__(self, seed, slim=False):
        self.img = np.zeros((64, 64, 4), np.uint8)
        self.rng = np.random.default_rng(seed)
        self.parts = parts(slim)

    def px(self, x, y, c, var=0.07):
        if c is None:
            return
        k = 1 + (self.rng.random() - 0.5) * 2 * var
        self.img[y, x] = (*np.clip(np.array(c[:3]) * k, 0, 255).astype(int), 255)

    def part(self, name, fn):
        """fn(face, x, y, w, h) -> colour or None; x, y inside the face."""
        u, v, w, h, d = self.parts[name]
        for f, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
            for y in range(fh):
                for x in range(fw):
                    self.px(x0 + x, y0 + y, fn(f, x, y, fw, fh))

    def save(self, name):
        Image.fromarray(self.img, 'RGBA').save(os.path.join(OUT, name + '.png'))


def both(s, a, b, fn):
    s.part(a, fn)
    s.part(b, fn)


# ---------------------------------------------------------------- Joy Boy (Luffy)
s = Skin(101)
SK, HAIR, VEST, SHORT = (222, 168, 120), (24, 20, 22), (196, 34, 38), (52, 86, 160)
STRAW, BAND = (226, 190, 98), (176, 30, 34)


def luffy_head(f, x, y, w, h):
    if f == 'top':
        return HAIR
    if f == 'bottom':
        return SK
    if f == 'back':
        return HAIR if y < 6 else SK
    if f in ('right', 'left'):
        return HAIR if y < 3 or (y < 5 and x < 2 if f == 'right' else y < 5 and x > 5) else SK
    # front: messy fringe, big eyes, the scar under his left eye, a wide grin
    if y < 2 or (y == 2 and x in (0, 2, 3, 5, 7)):
        return HAIR
    if y == 3 and x in (1, 2, 5, 6):
        return (40, 30, 26)
    if y == 4 and x in (1, 2, 5, 6):
        return (24, 20, 22) if x in (2, 5) else (245, 245, 240)
    if y == 5 and x == 2:
        return (150, 60, 50)
    if y == 6 and 2 <= x <= 5:
        return (245, 245, 240) if x in (3, 4) else (120, 40, 40)
    return SK


s.part('head', luffy_head)


def straw_hat(f, x, y, w, h):
    # the crown sits over the hair with the red band; the brim is the hat layer's lowest band at the sides
    if f == 'bottom':
        return None
    if f == 'top':
        return STRAW if (x + y) % 5 else (206, 168, 80)
    if y == 0:
        return STRAW
    if y == 1:
        return BAND
    if y == 2 and f != 'front':
        return (206, 168, 80)
    return None


s.part('hat', straw_hat)


def luffy_body(f, x, y, w, h):
    if f == 'top':
        return VEST
    if f == 'bottom':
        return SHORT
    if y >= 9:
        # the waistband and the shorts' top
        return (230, 214, 160) if y == 9 else SHORT
    if f == 'front':
        # the open red vest, the chest between, the X scar on it
        if x in (0, 1, 6, 7):
            return VEST if not (y == 0 and x in (1, 6)) else (210, 200, 180)
        if (y, x) in ((2, 2), (3, 3), (4, 4), (2, 5), (4, 3), (3, 4)):
            return (176, 96, 80)
        return SK
    return VEST


s.part('body', luffy_body)
both(s, 'rarm', 'larm', lambda f, x, y, w, h: VEST if (f == 'top' or y < 2) else SK)
both(s, 'rleg', 'lleg', lambda f, x, y, w, h: SHORT if f == 'top' or y < 6 else ((150, 100, 60) if f == 'bottom' or y == 11 else SK))
s.save('joy_boy')

# ---------------------------------------------------------------- Saitama
s = Skin(102)
SK, SUIT, GLOVE, CAPE, BELT = (238, 198, 160), (246, 206, 48), (198, 32, 34), (244, 244, 240), (26, 26, 28)


def saitama_head(f, x, y, w, h):
    if f == 'bottom':
        return SK
    if f == 'front':
        # small dot eyes, the bored mouth
        if y == 3 and x in (1, 2, 5, 6):
            return (190, 150, 120)
        if y == 4 and x in (2, 5):
            return (20, 20, 20)
        if y == 4 and x in (1, 6):
            return (250, 250, 248)
        if y == 6 and x in (3, 4):
            return (150, 90, 80)
    # bald, a shine on top
    if f == 'top' and x in (2, 3) and y in (2, 3):
        return (255, 236, 214)
    return SK


s.part('head', saitama_head)


def saitama_body(f, x, y, w, h):
    if f == 'top':
        return SUIT
    if y == 8:
        if f == 'front' and x in (3, 4):
            return (232, 190, 40)
        return BELT
    if f == 'front' and y < 4 and x in (3, 4):
        return (220, 178, 36)
    return SUIT


s.part('body', saitama_body)


def cape(f, x, y, w, h):
    # the white cape: down the back, over the shoulders, the collar's clasps in front
    if f == 'back':
        return CAPE
    if f == 'top':
        return CAPE if y < 2 else None
    if f in ('right', 'left'):
        return CAPE if y < 2 or x >= 2 else None
    if f == 'front' and y == 0 and x in (0, 7):
        return (40, 40, 40)
    return None


s.part('jacket', cape)
both(s, 'rarm', 'larm', lambda f, x, y, w, h: GLOVE if (f == 'bottom' or y >= 8) else SUIT)
both(s, 'rleg', 'lleg', lambda f, x, y, w, h: GLOVE if (f == 'bottom' or y >= 8) else SUIT)
s.save('saitama')

# ---------------------------------------------------------------- Shigaraki
s = Skin(103)
SK, HAIR, COAT, PANTS, RED = (206, 204, 198), (176, 186, 196), (34, 34, 40), (40, 42, 52), (196, 24, 30)
HAND = (222, 220, 212)


def shig_head(f, x, y, w, h):
    if f == 'top':
        return HAIR
    if f == 'bottom':
        return SK
    if f != 'front':
        return HAIR if y < 6 else SK
    # long ragged fringe, red eyes, dry cracked lips, the scar over his eye
    if y < 2 or (y == 2 and x % 2 == 0) or (y == 3 and x in (0, 7)):
        return HAIR
    if y == 4 and x in (1, 2, 5, 6):
        return RED if x in (2, 5) else (236, 232, 226)
    if y == 3 and x == 5:
        return (150, 140, 140)
    if y == 6 and 2 <= x <= 5:
        return (170, 120, 120) if x in (2, 5) else (130, 90, 95)
    return SK


s.part('head', shig_head)


def father(f, x, y, w, h):
    # the hand on his face (the "Father"): pale fingers over the face, the wrist under his chin
    if f == 'front':
        if x in (1, 3, 4, 6) and 1 <= y <= 3:
            return HAND
        if 3 <= y <= 4 and 1 <= x <= 6 and x not in (2, 5):
            return HAND
        if y >= 5 and x in (3, 4):
            return HAND if y < 7 else (196, 194, 186)
        return None
    if f == 'bottom':
        return None
    if f in ('right', 'left', 'back') and y < 3:
        return (186, 194, 204)
    return None


s.part('hat', father)


def shig_body(f, x, y, w, h):
    if f == 'front' and x in (3, 4) and y < 6:
        return (20, 20, 22)
    if f == 'front' and x in (2, 5) and y < 5:
        return (60, 60, 66)
    return COAT


s.part('body', shig_body)
# a hand on each shoulder, like the ones he wears
s.part('jacket', lambda f, x, y, w, h: HAND if (f in ('top',) and y in (1, 2) and x in (0, 1, 6, 7)) else None)
both(s, 'rarm', 'larm', lambda f, x, y, w, h: SK if (f == 'bottom' or y >= 10) else COAT)
both(s, 'rleg', 'lleg', lambda f, x, y, w, h: RED if (f == 'bottom' or y >= 10) else PANTS)
s.save('shigaraki')

# ---------------------------------------------------------------- Makima (slim arms)
s = Skin(104, slim=True)
SK, HAIR, SHIRT, TIE, BLACK = (240, 214, 196), (184, 74, 66), (238, 236, 232), (22, 22, 26), (30, 30, 36)
EYE = (224, 178, 40)


def makima_head(f, x, y, w, h):
    if f == 'top':
        return HAIR
    if f == 'bottom':
        return SK
    if f == 'back':
        return HAIR
    if f in ('right', 'left'):
        return HAIR if y < 5 or (f == 'right' and x < 2) or (f == 'left' and x > 5) else SK
    # parted fringe, the yellow ringed eyes, a faint smile
    if y < 2 or (y == 2 and x in (0, 1, 2, 5, 6, 7)):
        return HAIR
    if y == 3 and x in (1, 2, 5, 6):
        return (120, 50, 46)
    if y == 4 and x in (1, 2, 5, 6):
        return EYE if x in (2, 5) else (200, 150, 30)
    if y == 6 and x in (3, 4):
        return (196, 110, 110)
    return SK


s.part('head', makima_head)


def braid(f, x, y, w, h):
    # the braid down her back on the hat layer
    if f == 'back' and x in (3, 4) and y >= 3:
        return (164, 62, 56) if (y + x) % 2 else HAIR
    if f == 'top' and y >= 6:
        return HAIR
    return None


s.part('hat', braid)


def makima_body(f, x, y, w, h):
    if f == 'top':
        return SHIRT
    if f == 'front':
        if x in (3, 4) and y < 8:
            return TIE
        if y >= 10:
            return BLACK
        return SHIRT
    if y >= 10:
        return BLACK
    return SHIRT


s.part('body', makima_body)
s.part('jacket', lambda f, x, y, w, h: (36, 36, 44) if f == 'front' and y == 9 else None)
both(s, 'rarm', 'larm', lambda f, x, y, w, h: SK if (f == 'bottom' or y >= 11) else SHIRT)
both(s, 'rleg', 'lleg', lambda f, x, y, w, h: (20, 20, 22) if (f == 'bottom' or y >= 10) else BLACK)
s.save('makima')

# ---------------------------------------------------------------- Hashirama (and his Sage Mode: red marks round the eyes)
SK, HAIR, HAIR2, ARMOR, ARMOR2, CLOTH, STRAP = (226, 178, 138), (30, 22, 20), (44, 32, 28), (150, 40, 34), (112, 28, 26), (34, 34, 46), (60, 44, 34)
MARK = (196, 30, 34)


def hashirama(sage):
    s = Skin(105 + sage)

    def head(f, x, y, w, h):
        if f == 'top':
            return HAIR if (x * 3 + y) % 7 else HAIR2
        if f == 'bottom':
            return SK
        if f == 'back':
            return HAIR if (x + y) % 5 else HAIR2
        if f in ('right', 'left'):
            # the long straight hair falls over the ears
            return HAIR if y < 6 or (f == 'right' and x < 3) or (f == 'left' and x > 4) else SK
        # parted in the middle, falling at both sides of the face; calm eyes, a faint smile
        if y < 1 or (y == 1 and x not in (3, 4)) or (y < 7 and x in (0, 7)) or (y == 2 and x in (1, 6)):
            return HAIR
        if y == 3 and x in (1, 2, 5, 6):
            return MARK if sage and x in (1, 6) else (70, 46, 36)
        if y == 4 and x in (1, 2, 5, 6):
            return (36, 24, 20) if x in (2, 5) else (240, 236, 228)
        if sage and ((y == 5 and x in (1, 6)) or (y == 2 and x in (3, 4))):
            return MARK
        if y == 6 and x in (3, 4):
            return (168, 104, 92)
        return SK

    s.part('head', head)
    # the hat layer: the hair's volume behind, long down to the back
    s.part('hat', lambda f, x, y, w, h: (HAIR if (x + y) % 4 else HAIR2) if f in ('back', 'top') or (f in ('right', 'left') and y < 7) else None)

    def body(f, x, y, w, h):
        if f == 'top':
            return ARMOR
        if f == 'bottom':
            return CLOTH
        if y >= 10:
            return ARMOR2 if (x + y) % 2 else ARMOR
        if f == 'front':
            # the red armor's breastplate in plates, the dark collar
            if y < 2 and x in (2, 3, 4, 5):
                return CLOTH
            if y in (3, 6, 9):
                return ARMOR2
            return ARMOR
        if f == 'back':
            # the long hair down the back
            return HAIR if y < 7 and 1 <= x <= 6 else ARMOR
        return ARMOR

    s.part('body', body)
    s.part('jacket', lambda f, x, y, w, h: STRAP if f == 'front' and y == 9 else None)
    both(s, 'rarm', 'larm', lambda f, x, y, w, h: ARMOR if (f == 'top' or y < 4) else (ARMOR2 if y in (8, 9) else CLOTH) if y < 10 else SK)
    both(s, 'rleg', 'lleg', lambda f, x, y, w, h: ARMOR2 if y < 3 else ((50, 40, 34) if (f == 'bottom' or y >= 10) else CLOTH))
    s.save('hashirama_sage' if sage else 'hashirama')


hashirama(0)
hashirama(1)
print('ok')

# ---------------------------------------------------------------- Freeza Poder Total (final form; at 100% the muscles)
# The tail's own texture sits in the free corner x 56-63, y 16-31: white at the top (rows 16-23), purple tip below.
FW, FP, FP2, FE, FL, VEIN = (240, 236, 242), (122, 58, 176), (92, 40, 140), (200, 24, 40), (110, 40, 120), (185, 163, 200)
SHADE = (214, 208, 222)


def freeza_total(full):
    s = Skin(120 + full)

    def head(f, x, y, w, h):
        if f == 'top':
            return FP
        if f == 'bottom':
            return FW
        if f == 'back':
            return FP if y < 4 else FW
        if f in ('right', 'left'):
            return FP if y < 3 else FW
        # the purple dome over the brow, sharp red eyes with dark lines under them, purple lips
        if y < 2:
            return FP
        if y == 2 and x in (0, 7):
            return FP
        if y == 3 and x in (1, 2, 5, 6):
            return FL if x in (1, 6) else (60, 20, 70)
        if y == 4 and x in (1, 2, 5, 6):
            return FE if x in (2, 5) else (250, 246, 250)
        if y == 5 and x in (1, 6):
            return FL
        if y == 6 and x in (3, 4):
            return (120, 50, 150)
        return FW

    s.part('head', head)

    def body(f, x, y, w, h):
        if f in ('top', 'bottom'):
            return FW
        if f == 'front':
            # the purple chest plate, two darker pecs on it
            if 1 <= y <= 4 and 1 <= x <= 6:
                return FP2 if y >= 3 and x in (2, 5) else FP
            if full and y >= 6 and x in (3, 4) and y % 2 == 0:
                return SHADE
            if full and y in (5, 7, 9) and 2 <= x <= 5:
                return SHADE
            return FW
        if f == 'back':
            return SHADE if full and x in (3, 4) else FW
        return FW

    s.part('body', body)

    def arm(f, x, y, w, h):
        # the shoulder dome, the band of the forearm
        if f == 'top' or y < 3:
            return FP
        if 7 <= y <= 9:
            return FP
        if full and f in ('front', 'right', 'left') and y in (4, 5) and x == 1:
            return VEIN
        if full and y == 6 and f == 'front':
            return SHADE
        return FW

    both(s, 'rarm', 'larm', arm)

    def leg(f, x, y, w, h):
        # the knee guard and the purple shin
        if y in (4, 5):
            return FP2
        if 7 <= y <= 11:
            return FP
        if f == 'bottom':
            return FW
        if full and y in (1, 2) and f == 'front':
            return SHADE
        return FW

    both(s, 'rleg', 'lleg', leg)
    # the tail's corner
    for y in range(16, 32):
        for x in range(56, 64):
            s.px(x, y, (FW if (x + y) % 5 else SHADE) if y < 24 else (FP if (x + y) % 4 else FP2))
    s.save('freeza_total_100' if full else 'freeza_total')


freeza_total(0)
freeza_total(1)
print('freeza ok')
