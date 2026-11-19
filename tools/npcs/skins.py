# Skins (64x64, player layout) of the master NPCs added on 2026-11-19: Kakashi, Maki, Senhor Kaioh.
# python3 tools/npcs/skins.py <out dir>   (writes npc_kakashi.png, npc_maki.png, npc_kaioh.png, npc_bubbles.png)
import sys, random
from PIL import Image

def rgb(h):
    h = h.lstrip('#'); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))

class Skin:
    def __init__(self, seed):
        self.im = Image.new('RGBA', (64, 64), (0, 0, 0, 0)); self.r = random.Random(seed)
    def px(self, x, y, c, jit=6):
        if isinstance(c, str): c = rgb(c)
        j = self.r.randint(-jit, jit)
        self.im.putpixel((x, y), tuple(max(0, min(255, v + j)) for v in c[:3]) + (255,))
    def rect(self, x, y, w, h, c, jit=6):
        for yy in range(y, y + h):
            for xx in range(x, x + w): self.px(xx, yy, c, jit)
    # a box part: u,v of its texture origin, w,d,h sizes; fill every face with c
    def box(self, u, v, w, h, d, c, jit=6):
        self.rect(u + d, v, w, d, c, jit); self.rect(u + d + w, v, w, d, c, jit)
        self.rect(u, v + d, 2 * d + 2 * w, h, c, jit)
    def face(self, u, v, w, h, d, side):
        # origin (x,y) of a face: top, bottom, right, front, left, back
        return {'top': (u + d, v), 'bottom': (u + d + w, v), 'right': (u, v + d), 'front': (u + d, v + d),
                'left': (u + d + w, v + d), 'back': (u + 2 * d + w, v + d)}[side]

HEAD = (0, 0, 8, 8, 8); HAT = (32, 0, 8, 8, 8); BODY = (16, 16, 8, 12, 4); JACKET = (16, 32, 8, 12, 4)
RARM = (40, 16, 4, 12, 4); LARM = (32, 48, 4, 12, 4); RLEG = (0, 16, 4, 12, 4); LLEG = (16, 48, 4, 12, 4)

def kakashi():
    s = Skin(11); skin = '#E9C3A0'; navy = '#2B3550'; vest = '#5E7046'; hair = '#D8DCE2'; mask = '#283048'
    s.box(*HEAD[:2], 8, 8, 8, skin)
    # hair: top, back, sides upper
    s.box(*HAT[:2], 8, 8, 8, hair, 10)
    fx, fy = s.face(*HAT, 'front'); s.rect(fx, fy + 2, 8, 6, (0, 0, 0), 0)
    for x in range(8):
        for y in range(2, 8): s.im.putpixel((fx + x, fy + y), (0, 0, 0, 0))
    for side in ('left', 'right', 'back'):
        x0, y0 = s.face(*HAT, side)
        for x in range(8):
            for y in range(4 + (x % 3 == 0), 8): s.im.putpixel((x0 + x, y0 + y), (0, 0, 0, 0)) if side != 'back' else None
    hx, hy = s.face(*HEAD, 'front')
    s.rect(hx, hy, 8, 2, hair, 10)                      # bangs
    s.rect(hx, hy + 2, 8, 1, '#3A3F4A', 4)              # headband cloth
    s.rect(hx + 1, hy + 2, 5, 1, '#B8BEC8', 6)          # metal plate
    s.rect(hx + 1, hy + 3, 3, 1, '#2B2F38', 2)          # plate covers his left eye (viewer's left)
    s.px(hx + 5, hy + 3, '#1A1A1A', 0); s.px(hx + 6, hy + 3, '#F2F2F2', 0)  # right eye open
    s.rect(hx, hy + 5, 8, 3, mask, 4); s.rect(hx, hy + 4, 8, 1, mask, 4)
    s.px(hx + 4, hy + 4, skin, 2)                       # nose bridge over the mask
    for side in ('left', 'right', 'back'):
        x0, y0 = s.face(*HEAD, side); s.rect(x0, y0, 8, 3, hair, 10); s.rect(x0, y0 + 3, 8, 1, '#3A3F4A', 4); s.rect(x0, y0 + 5, 8, 3, mask, 4)
    s.box(*BODY[:2], 8, 12, 4, navy)
    s.box(*JACKET[:2], 8, 12, 4, vest, 8)
    jx, jy = s.face(*JACKET, 'front')
    for y in range(12): s.px(jx + 3, jy + y, '#4A5A37', 3); s.px(jx + 4, jy + y, '#4A5A37', 3)
    s.rect(jx, jy + 3, 3, 2, '#55663F', 5); s.rect(jx + 5, jy + 3, 3, 2, '#55663F', 5)   # scroll pouches
    s.rect(jx, jy + 7, 3, 2, '#55663F', 5); s.rect(jx + 5, jy + 7, 3, 2, '#55663F', 5)
    for arm in (RARM, LARM):
        s.box(*arm[:2], 4, 12, 4, navy)
        x0, y0 = arm[0], arm[1] + 4
        s.rect(x0, y0 + 10, 16, 2, '#E9C3A0', 4); s.rect(x0, y0 + 9, 16, 1, '#C8CED8', 4)  # hands, metal glove plate
    for leg in (RLEG, LLEG):
        s.box(*leg[:2], 4, 12, 4, navy); s.rect(leg[0], leg[1] + 4 + 6, 16, 2, '#EDEDED', 2); s.rect(leg[0], leg[1] + 4 + 8, 16, 4, '#2A2D38', 3)
    return s.im

def maki():
    s = Skin(23); skin = '#EDC9A8'; hair = '#2F5D3E'; uni = '#22252E'; frame = '#1C1C1C'
    s.box(*HEAD[:2], 8, 8, 8, skin)
    hx, hy = s.face(*HEAD, 'front')
    s.rect(hx, hy, 8, 2, hair, 8); s.px(hx, hy + 2, hair, 8); s.px(hx + 7, hy + 2, hair, 8)
    s.rect(hx + 1, hy + 3, 6, 1, frame, 0)               # glasses
    s.px(hx + 2, hy + 4, frame, 0); s.px(hx + 5, hy + 4, frame, 0)
    s.px(hx + 1, hy + 4, '#C8D8E0', 0); s.px(hx + 6, hy + 4, '#C8D8E0', 0)
    s.px(hx + 3, hy + 6, '#B07060', 2); s.px(hx + 4, hy + 6, '#B07060', 2)
    for side in ('top', 'left', 'right', 'back'):
        x0, y0 = s.face(*HEAD, side); s.rect(x0, y0, 8, 8 if side in ('top', 'back') else 4, hair, 8)
    bx, by = s.face(*HAT, 'back'); s.rect(bx + 2, by + 3, 4, 5, hair, 8)   # ponytail tie
    s.px(bx + 3, by + 3, '#D0D0D0', 0); s.px(bx + 4, by + 3, '#D0D0D0', 0)
    s.box(*BODY[:2], 8, 12, 4, uni)
    fx, fy = s.face(*BODY, 'front')
    for y in range(12): s.px(fx + 3, fy + y, '#30343F', 2); s.px(fx + 4, fy + y, '#30343F', 2)
    s.px(fx + 3, fy + 1, '#C8A040', 0); s.px(fx + 3, fy + 4, '#C8A040', 0)  # buttons
    s.rect(fx, fy + 7, 8, 1, '#3A2A1E', 3)               # belt for the cursed tools
    for arm in (RARM, LARM):
        s.box(*arm[:2], 4, 12, 4, uni); s.rect(arm[0], arm[1] + 4 + 10, 16, 2, skin, 4)
    for leg in (RLEG, LLEG):
        s.box(*leg[:2], 4, 12, 4, uni); s.rect(leg[0], leg[1] + 4 + 9, 16, 3, '#151515', 2)
    return s.im

def kaioh():
    s = Skin(37); skin = '#3D7FD8'; dark = '#1E2433'; white = '#EDEDED'
    s.box(*HEAD[:2], 8, 8, 8, skin, 8)
    hx, hy = s.face(*HEAD, 'front')
    s.rect(hx + 1, hy + 3, 6, 2, '#0E0E10', 0); s.px(hx + 2, hy + 3, '#5A6070', 0); s.px(hx + 5, hy + 3, '#5A6070', 0)  # sunglasses
    s.px(hx + 3, hy + 5, '#2E64B0', 2); s.px(hx + 4, hy + 5, '#2E64B0', 2)
    for x in (0, 1, 6, 7): s.px(hx + x, hy + 5 + (x in (0, 7)), '#14181F', 0)                # whiskers
    s.rect(hx + 3, hy + 6, 2, 1, '#2A4F90', 2)
    tx, ty = s.face(*HEAD, 'top'); s.px(tx + 2, ty + 3, '#111111', 0); s.px(tx + 5, ty + 3, '#111111', 0)
    ax, ay = s.face(*HAT, 'top'); s.px(ax + 2, ay + 2, '#111111', 0); s.px(ax + 5, ay + 2, '#111111', 0)  # antennae roots
    s.box(*BODY[:2], 8, 12, 4, dark)
    fx, fy = s.face(*BODY, 'front')
    s.rect(fx + 2, fy + 2, 4, 4, white, 2)               # the 界 badge
    for (x, y) in [(3, 3), (4, 3), (3, 4), (4, 4), (3, 5), (4, 5)]: s.px(fx + x, fy + y, '#1E2433', 0)
    s.rect(fx, fy + 7, 8, 1, '#D8A030', 3)               # sash
    for arm in (RARM, LARM):
        s.box(*arm[:2], 4, 12, 4, dark); s.rect(arm[0], arm[1] + 4 + 9, 16, 3, skin, 6)
    for leg in (RLEG, LLEG):
        s.box(*leg[:2], 4, 12, 4, dark); s.rect(leg[0], leg[1] + 4 + 10, 16, 2, '#6A4A2A', 3)
    return s.im

def bubbles():
    # Senhor Kaioh's monkey: brown fur, a light tan face and belly, round black eyes, big ears on the hat layer
    s = Skin(41); fur = '#7A5232'; tan = '#E2B98A'; dark = '#4E321C'
    s.box(*HEAD[:2], 8, 8, 8, fur, 10)
    hx, hy = s.face(*HEAD, 'front')
    s.rect(hx + 1, hy + 2, 6, 6, tan, 5); s.rect(hx + 2, hy + 1, 4, 1, tan, 5)
    s.rect(hx + 2, hy + 3, 1, 2, '#111111', 0); s.rect(hx + 5, hy + 3, 1, 2, '#111111', 0)
    s.px(hx + 2, hy + 3, '#F0F0F0', 0); s.px(hx + 5, hy + 3, '#F0F0F0', 0)
    s.px(hx + 3, hy + 5, dark, 0); s.px(hx + 4, hy + 5, dark, 0)          # nostrils
    s.rect(hx + 2, hy + 6, 4, 1, '#B07A55', 3)                             # mouth
    for side in ('left', 'right'):                                         # ears on the hat layer
        x0, y0 = s.face(*HAT, side); s.rect(x0 + 2, y0 + 2, 3, 3, tan, 5); s.px(x0 + 3, y0 + 3, '#C08F66', 2)
    s.box(*BODY[:2], 8, 12, 4, fur, 10)
    fx, fy = s.face(*BODY, 'front'); s.rect(fx + 2, fy + 2, 4, 8, tan, 5)
    for arm in (RARM, LARM):
        s.box(*arm[:2], 4, 12, 4, fur, 10); s.rect(arm[0], arm[1] + 4 + 10, 16, 2, tan, 5)
    for leg in (RLEG, LLEG):
        s.box(*leg[:2], 4, 12, 4, fur, 10); s.rect(leg[0], leg[1] + 4 + 10, 16, 2, tan, 5)
    return s.im

if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else '.'
    for name, f in (('npc_kakashi', kakashi), ('npc_maki', maki), ('npc_kaioh', kaioh), ('npc_bubbles', bubbles)):
        f().save(f'{out}/{name}.png')

# The training balloon's icon (16x16): a water balloon with its knot, light from the top left.
def balloon(path):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 5.6, (y - 6.5) / 6.0
            d = dx * dx + dy * dy
            if d <= 1:
                l = 0.5 - 0.35 * dx - 0.45 * dy
                c = (40, 110, 220) if l < 0.35 else (70, 150, 240) if l < 0.7 else (150, 205, 255)
                if l > 1.0: c = (230, 245, 255)
                if d > 0.82: c = (24, 70, 160)
                im.putpixel((x, y), c + (255,))
    for (x, y) in [(7, 13), (8, 13), (7, 14), (8, 14), (6, 15), (9, 15)]:
        im.putpixel((x, y), (24, 70, 160, 255))
    im.save(path)

if __name__ == '__main__' and len(sys.argv) > 2:
    balloon(sys.argv[2])
