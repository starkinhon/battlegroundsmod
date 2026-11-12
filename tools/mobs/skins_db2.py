"""Skins of the Dragon Ball start (Stark 2026-11-10, "uma coisinha em cada estrutura"): Raditz (the opening fight),
the Cell Jr. (Cell's ring), Babidi's guardians Pui Pui, Yakon and Dabura (the ship), and the NPCs Bulma (Capsule
Corp's missions), Master Roshi (Kame House training) and Majin Buu (his house). Same layout and helpers as skins_db.py
(noise in every color). python3 tools/mobs/skins_db2.py"""
import os, shutil

_src = open(os.path.join(os.path.dirname(__file__), 'skins_db.py')).read()
exec(_src[:_src.index('def bandit(')])

SKIN_LIGHT = (232, 190, 150)


def plain(c):
    return lambda f, x, y, w, h: c


# ---------- Raditz: long spiky black hair down the back, Saiyan armor (white plates, brown shoulder straps), dark
# blue suit, red-ish eyes look, a brown tail wrapped at the waist
s = Skin(101)
HAIR = (22, 20, 26)
s.part('head', face(SKIN_LIGHT, HAIR, eye=(30, 20, 20), brow=(15, 12, 18)))
def raditz_hair(f, x, y, w, h):
    if f == 'bottom':
        return None
    if f == 'front':
        return HAIR if y < 2 or (y == 2 and x in (0, 1, 6, 7)) else None
    if f in ('left', 'right'):
        return HAIR if y < 6 or x > 4 else None
    return HAIR
s.part('hat', raditz_hair)
def raditz_body(f, x, y, w, h):
    if f in ('top', 'bottom'):
        return (235, 232, 222)
    if y in (8, 9):
        return (120, 70, 40)          # the tail around the waist
    if f == 'front' and y < 7 and x in (1, 6):
        return (130, 90, 50)          # shoulder straps
    if y >= 10:
        return (40, 45, 90)
    return (235, 232, 222)
s.part('body', raditz_body)
s.part('jacket', lambda f, x, y, w, h: HAIR if f == 'back' and y < 10 else None)   # the hair down the back
for a in ('rarm', 'larm'):
    s.part(a, lambda f, x, y, w, h: (40, 45, 90) if y < 8 else ((235, 232, 222) if y >= 9 else (40, 45, 90)))
for l in ('rleg', 'lleg'):
    s.part(l, leg((40, 45, 90), (235, 232, 222), 4))
s.save('raditz')

# ---------- Cell Jr.: small, light blue-green with dark green spots, black-purple face, orange-pink lips
s = Skin(102)
CJ, SPOT, CJFACE = (130, 190, 170), (40, 100, 60), (70, 60, 80)
def cj_spots(base):
    def fn(f, x, y, w, h):
        return SPOT if (x * 7 + y * 13) % 9 == 0 else base
    return fn
def cj_head(f, x, y, w, h):
    if f == 'front' and 1 <= x <= 6 and 3 <= y <= 7:
        if y == 4 and x in (2, 5):
            return (200, 40, 60)
        if y == 6 and x in (3, 4):
            return (230, 140, 120)
        return CJFACE
    return SPOT if (x + y) % 4 == 0 else CJ
s.part('head', cj_head)
s.part('body', cj_spots(CJ))
for a in ('rarm', 'larm', 'rleg', 'lleg'):
    s.part(a, cj_spots(CJ))
s.save('cell_jr')

# ---------- Pui Pui: purple skin, big round head, armor in white with yellow, red-brown suit
s = Skin(103)
PUR = (150, 90, 170)
s.part('head', face(PUR, PUR, eye=(20, 20, 20), brow=(110, 60, 130), mouth=(80, 30, 60)))
s.part('body', torso((90, 30, 30), vest=((236, 232, 220), (220, 190, 60))))
for a in ('rarm', 'larm'):
    s.part(a, arm((90, 30, 30), PUR, sleeve_rows=5))
for l in ('rleg', 'lleg'):
    s.part(l, leg((90, 30, 30), (236, 232, 220), 4))
s.save('pui_pui')

# ---------- Yakon: black skin with yellow stripes, big wide eyes, white-grey belly
s = Skin(104)
BLK, YEL = (28, 26, 30), (230, 200, 40)
def yakon_head(f, x, y, w, h):
    if f == 'front' and y in (3, 4) and x in (1, 2, 5, 6):
        return (240, 230, 120)
    if f == 'front' and y == 6 and 2 <= x <= 5:
        return (200, 40, 40)
    return YEL if f != 'front' and y % 3 == 0 else BLK
s.part('head', yakon_head)
s.part('body', lambda f, x, y, w, h: (180, 170, 150) if f == 'front' and 2 <= x <= 5 and y < 10 else (YEL if y % 4 == 1 else BLK))
for a in ('rarm', 'larm', 'rleg', 'lleg'):
    s.part(a, lambda f, x, y, w, h: YEL if y % 4 == 1 else BLK)
s.save('yakon')

# ---------- Dabura: red skin, white hair cap, black and gold clothes, white cape on the back
s = Skin(105)
RED = (190, 50, 45)
s.part('head', face(RED, (236, 236, 236), eye=(240, 220, 60), brow=(236, 236, 236), mouth=(90, 20, 20)))
s.part('body', torso((30, 28, 32), belt=(210, 170, 60), vest=((30, 28, 32), (210, 170, 60))))
s.part('jacket', lambda f, x, y, w, h: (236, 236, 230) if f == 'back' else None)
for a in ('rarm', 'larm'):
    s.part(a, arm((30, 28, 32), RED, sleeve_rows=8, band=(210, 170, 60)))
for l in ('rleg', 'lleg'):
    s.part(l, leg((30, 28, 32), (210, 170, 60), 3))
s.save('dabura')

# ---------- Bulma: turquoise hair, pink dress with her name strip, red belt, light skin, boots
s = Skin(106)
BHAIR = (60, 190, 190)
s.part('head', face(SKIN_LIGHT, BHAIR, eye=(40, 110, 170), mouth=(200, 90, 100)))
s.part('hat', lambda f, x, y, w, h: None if f == 'bottom' or (f == 'front' and y > 1) else (BHAIR if (f != 'front' or y < 2) and (f in ('top', 'front') or y < 6) else None))
s.part('body', torso((230, 120, 160), belt=(200, 40, 50), belt_rows=(7, 7)))
for a in ('rarm', 'larm'):
    s.part(a, arm((230, 120, 160), SKIN_LIGHT, sleeve_rows=2))
for l in ('rleg', 'lleg'):
    s.part(l, leg(SKIN_LIGHT, (120, 70, 50), 4))
s.save('npc_bulma')

# ---------- Majin Buu (fat): pink skin, white trousers, black and gold vest, purple cape, the antenna on top
s = Skin(107)
PINK = (240, 150, 190)
def buu_head(f, x, y, w, h):
    if f == 'front' and y == 4 and x in (2, 5):
        return (20, 20, 20)
    if f == 'front' and y == 6 and 2 <= x <= 5:
        return (180, 60, 90)
    if f == 'top' and x in (3, 4) and y in (3, 4):
        return (220, 120, 170)
    return PINK
s.part('head', buu_head)
s.part('body', lambda f, x, y, w, h: (30, 28, 32) if y < 6 and (f != 'front' or x in (0, 1, 6, 7)) else
       ((210, 170, 60) if y in (8, 9) else ((236, 232, 228) if y >= 10 else PINK)))
s.part('jacket', lambda f, x, y, w, h: (120, 60, 160) if f == 'back' else None)
for a in ('rarm', 'larm'):
    s.part(a, lambda f, x, y, w, h: (236, 232, 228) if y >= 10 else PINK)
for l in ('rleg', 'lleg'):
    s.part(l, leg((236, 232, 228), (30, 28, 32), 3))
s.save('npc_boo')

# ---------- Master Roshi: the SubAlma's own skin (same character)
shutil.copy(os.path.join(OUT, 'kame.png'), os.path.join(OUT, 'npc_mestre_kame.png'))
print('ok')
