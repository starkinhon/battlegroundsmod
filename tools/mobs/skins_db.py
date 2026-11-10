"""Skins of the Dragon Ball Earth's regional enemies (Stark 2026-11-10): desert bandits (2 looks), pirates (2 looks)
and the yeti, painted on the player skin layout (64x64) with noise in every color so no two pixels repeat.
python3 tools/mobs/skins_db.py"""
import os, numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src/main/resources/assets/battlegroundsmod/textures/entity')
PARTS = {'head': (0, 0, 8, 8, 8), 'hat': (32, 0, 8, 8, 8), 'body': (16, 16, 8, 12, 4), 'jacket': (16, 32, 8, 12, 4),
         'rarm': (40, 16, 4, 12, 4), 'larm': (32, 48, 4, 12, 4), 'rsleeve': (40, 32, 4, 12, 4), 'lsleeve': (48, 48, 4, 12, 4),
         'rleg': (0, 16, 4, 12, 4), 'lleg': (16, 48, 4, 12, 4)}


def faces(u, v, w, h, d):
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h),
            'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + d + d + w, v + d, w, h)}


class Skin:
    def __init__(self, seed):
        self.img = np.zeros((64, 64, 4), np.uint8)
        self.rng = np.random.default_rng(seed)

    def px(self, x, y, c, var=0.08):
        if c is None:
            return
        k = 1 + (self.rng.random() - 0.5) * 2 * var
        self.img[y, x] = (*np.clip(np.array(c[:3]) * k, 0, 255).astype(int), 255)

    def part(self, name, fn):
        """fn(face, x, y, w, h) -> color or None, x/y inside the face."""
        u, v, w, h, d = PARTS[name]
        for f, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
            for y in range(fh):
                for x in range(fw):
                    self.px(x0 + x, y0 + y, fn(f, x, y, fw, fh))

    def save(self, name):
        Image.fromarray(self.img, 'RGBA').save(os.path.join(OUT, name + '.png'))


def face(skin_c, hair, eye=(40, 30, 25), brow=None, mouth=(120, 60, 50), beard=None, patch=False):
    brow = brow or hair
    def fn(f, x, y, w, h):
        if f == 'top':
            return hair
        if f == 'bottom':
            return skin_c
        if f in ('right', 'left', 'back') and y < (3 if f != 'back' else 5):
            return hair
        if f == 'front':
            if y < 2:
                return hair
            if y == 3 and x in (1, 2, 5, 6):
                return brow
            if y == 4 and x in (1, 2, 5, 6):
                if patch and x in (5, 6):
                    return (15, 15, 15)
                return eye if x in (2, 5) else (235, 235, 230)
            if patch and y == 3 and x in (5, 6, 7):
                return (15, 15, 15)
            if beard and (y >= 6 or (y == 5 and x in (0, 7))):
                return mouth if y == 6 and x in (3, 4) else beard
            if y == 6 and x in (3, 4):
                return mouth
        if patch and f == 'left' and y == 3:
            return (15, 15, 15)
        return skin_c
    return fn


def torso(main, belt=None, belt_rows=(8, 9), stripe=None, vest=None, buckle=None):
    def fn(f, x, y, w, h):
        if belt and f not in ('top', 'bottom') and y in belt_rows:
            if buckle and f == 'front' and x in (3, 4):
                return buckle
            return belt
        if vest and f == 'front' and x in (2, 3, 4, 5) and y < belt_rows[0]:
            return vest[1]
        if vest and f not in ('bottom',):
            c = vest[0]
        else:
            c = main
        if stripe and f not in ('top', 'bottom') and y % 3 == 1:
            return stripe
        return c
    return fn


def arm(sleeve, skin_c, sleeve_rows=4, stripe=None, band=None):
    def fn(f, x, y, w, h):
        if f == 'top':
            return sleeve
        if f == 'bottom':
            return skin_c
        if y < sleeve_rows:
            if stripe and y % 3 == 1:
                return stripe
            return sleeve
        if band and y in (10, 11):
            return band
        return skin_c
    return fn


def leg(pants, boots, boot_rows=4):
    def fn(f, x, y, w, h):
        if f == 'top':
            return pants
        if f == 'bottom' or y >= 12 - boot_rows:
            return boots
        return pants
    return fn


def headwrap(cloth, mask=None, band=None, knot=True):
    """The hat layer: a cloth around the head (top, sides, back), an optional mask over the mouth."""
    def fn(f, x, y, w, h):
        if f == 'bottom':
            return None
        if f == 'top':
            return cloth
        if f == 'front':
            if y < 2:
                return band or cloth
            if mask and y >= 5:
                return mask
            return None
        if f == 'back' and knot and y in (3, 4, 5) and x in (3, 4):
            return band or cloth
        if y < 4:
            return band if band and y == 3 else cloth
        return None
    return fn


def bandit(name, seed, skin_c, cloth, mask, vest, tunic, sash, pants, boots):
    s = Skin(seed)
    s.part('head', face(skin_c, (30, 25, 20), brow=(25, 20, 15)))
    s.part('hat', headwrap(cloth, mask=mask))
    s.part('body', torso(tunic, belt=sash, vest=(vest, tunic)))
    for a in ('rarm', 'larm'):
        s.part(a, arm(tunic, skin_c, sleeve_rows=3, band=(110, 80, 50)))
    for l in ('rleg', 'lleg'):
        s.part(l, leg(pants, boots, 4))
    s.save(name)


bandit('bandido_deserto_0', 21, (190, 140, 100), (214, 194, 150), (200, 180, 140), (110, 70, 40), (226, 220, 200),
       (180, 40, 35), (182, 160, 118), (90, 60, 35))
bandit('bandido_deserto_1', 22, (150, 105, 72), (92, 120, 72), (82, 108, 64), (70, 70, 74), (205, 190, 160),
       (220, 120, 30), (150, 132, 96), (60, 45, 30))


def pirate(name, seed, skin_c, bandana, beard, stripe_a, stripe_b, pants, patch):
    s = Skin(seed)
    s.part('head', face(skin_c, (35, 28, 22), beard=beard, patch=patch))
    s.part('hat', headwrap(bandana, knot=True))
    s.part('body', torso(stripe_a, belt=(30, 25, 22), stripe=stripe_b, buckle=(220, 180, 50)))
    for a in ('rarm', 'larm'):
        s.part(a, arm(stripe_a, skin_c, sleeve_rows=6, stripe=stripe_b))
    for l in ('rleg', 'lleg'):
        s.part(l, leg(pants, (25, 22, 20), 5))
    s.save(name)


pirate('pirata_0', 31, (205, 155, 115), (200, 40, 35), (95, 60, 35), (232, 232, 228), (40, 55, 120), (40, 48, 90), False)
pirate('pirata_1', 32, (175, 125, 90), (30, 28, 30), (40, 30, 25), (150, 35, 40), (230, 220, 205), (90, 88, 86), True)

# the yeti: thick white fur everywhere, a blue-grey face with dark eyes and fangs, blue-grey hands and feet
s = Skin(41)
FUR, SKIN = (236, 238, 242), (150, 172, 192)
def yeti_head(f, x, y, w, h):
    if f == 'front' and 1 <= x <= 6 and 2 <= y <= 7:
        if y == 3 and x in (2, 5):
            return (30, 20, 30)
        if y == 3 and x in (1, 6):
            return (120, 30, 40)
        if y == 6 and 2 <= x <= 5:
            return (235, 235, 225) if x in (2, 5) else (60, 30, 40)
        return SKIN
    return FUR
s.part('head', yeti_head)
s.part('body', lambda f, x, y, w, h: (FUR if not (f == 'front' and 2 <= x <= 5 and 2 <= y <= 9) else (212, 218, 226)))
for a in ('rarm', 'larm'):
    s.part(a, lambda f, x, y, w, h: SKIN if (f == 'bottom' or y >= 11) else FUR)
for l in ('rleg', 'lleg'):
    s.part(l, lambda f, x, y, w, h: SKIN if f == 'bottom' else FUR)
# shaggy fringe on the hat/jacket layers: tufts hanging over the edges
def tufts(f, x, y, w, h):
    if f in ('bottom',):
        return None
    if f == 'top':
        return FUR if (x + y) % 3 else None
    return (226, 230, 236) if (y < 2 or (y + x) % 5 == 0) and not (f == 'front') else None
s.part('hat', tufts)
s.part('jacket', lambda f, x, y, w, h: (226, 230, 236) if f in ('right', 'left', 'back') and (x + y) % 4 == 0 else None)
s.save('yeti')
