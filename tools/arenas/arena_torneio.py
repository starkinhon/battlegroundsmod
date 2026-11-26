"""The Tournament of Power arena (Stark 2026-11-26, after his anime frames: "a arena do Torneio do Poder... bem grande, no
vácuo do espaço e bem destrutível").

A giant spinning top floating in space: the flat round stage on top is painted stone drawn in rings (triangles pointing in,
radial stripes, outlined in brown, as in the anime), with the striped pillar in the middle; the rim is striped, and under
it the top narrows down in a cone to the striped stem and its point. Round it: the curved balcony of the gods hanging
above one side, two glowing moons with a painted cap, a small top far away and broken rocks drifting in the void.

Only the outside of the cone is built (a shell 4 blocks thick): nobody sees the inside, and the arena stays light to place.
The stage itself is solid 11 blocks deep, so the craters of the powers show the rock under the paint.
"""
import math
import numpy as np
from arenas_all import arena
from lib import Vox, Noise

P = 'battlegroundsmod:torneio_'
CREME, BEGE, AZUL, AZUL_E, LINHA = P + 'piso_creme', P + 'piso_bege', P + 'piso_azul', P + 'piso_azul_escuro', P + 'linha'
ROCHA, ROCHA_E, LUZ = P + 'rocha', P + 'rocha_escura', P + 'luz'

# Stark 2026-11-26, after the first preview: "aumenta ela umas 3x de tamanho (é pra ser grande)"; then, since 3x was too heavy:
# "faz um 2x só". Everything is drawn at K times the first size: the rings keep their count of teeth and stripes, so the drawing is the same, only bigger.
K = 2
RA = 140 * K        # the stage's radius (560 blocks across; Stark 2026-11-26: 3x was too heavy, "faz um 2x só")
SLAB = 8            # rock under the painted floor kept by the arena file (what /soul torneio refazer puts back)
RIM = 8 * K         # the striped rim's height under the floor
CONE = 98 * K       # from the rim down to the stem
STEM_R = 6 * K
SHELL = 2           # rock behind the cone's painted skin kept by the arena file
# Stark: "ela não pode ser oca, preenche com aquela pedra": the inside of the top is solid rock, made by the dimension's
# own generator (challenge/TorneioGenerator.java, the same cone_bottom), so the arena file only keeps the painted skin
# and the rock just behind it. Both sides must agree on cone_bottom to the block.

def cone_bottom(r):
    """y of the cone's underside under a column at distance r (the same formula as TorneioGenerator.coneBottom)."""
    rr = max(r, STEM_R)
    return int(math.floor(-RIM - CONE * (1 - rr / RA) + 0.5))

def bands():
    """The rings of the drawing from the rim inwards: (kind, outer radius, inner radius, teeth or stripes)."""
    out, r = [], RA - 1.4 * K
    for kind, w, n in (('tri', 16, 56), ('str', 14, 120), ('tri', 15, 44), ('flat', 5, 0), ('str', 12, 96),
                       ('tri', 13, 32), ('str', 11, 64), ('tri', 10, 22), ('str', 8, 40)):
        out.append((kind, r, r - w * K, n))
        r -= (w + 1.3) * K    # the brown line between two rings
    out.append(('core', r, 0, 0))
    return out

BANDS = bands()

def floor(x, z, alt):
    """The painted floor block at (x, z)."""
    r = math.hypot(x, z)
    if r > RA - 1.4 * K:
        return LINHA
    a = (math.atan2(z, x) / (2 * math.pi)) % 1.0
    for i, (kind, r1, r0, n) in enumerate(BANDS):
        if r > r1:
            return LINHA                     # the line just outside this ring
        if r < r0:
            continue
        if kind == 'flat':
            return CREME
        if kind == 'core':
            return BEGE if r > 9 * K else CREME
        u = (a * n) % 1.0
        if kind == 'str':
            k = int(a * n) % 2
            return (AZUL if k else BEGE) if i < 3 else (AZUL_E if k else CREME)
        # triangles: teeth with their base on the outer line and the point inwards, outlined
        W = r1 - r0
        L = 2 * math.pi * r / n             # arc length of one tooth here
        h = r - r0                          # height from the inner line
        s = abs(u - 0.5) * L                # distance from the tooth's centre along the arc
        half = (L / 2) * (h / W)
        d = (half - s) * math.cos(math.atan2(L / 2, W))
        if abs(d) < 1.2:
            return LINHA
        inside = d > 0
        if i == 0:
            return CREME if inside else BEGE
        return (CREME if inside else AZUL) if i % 2 else (BEGE if inside else CREME)
    return CREME

def rim(x, z, y):
    a = (math.atan2(z, x) / (2 * math.pi)) % 1.0
    if y >= -K or y <= -RIM + K - 1:
        return LINHA
    k = int(a * 150) % 3
    return (AZUL, BEGE, AZUL_E)[k]

def cone_skin(x, z, depth):
    """The outside of the cone: rows of stripes between brown lines, like the anime's top."""
    a = (math.atan2(z, x) / (2 * math.pi)) % 1.0
    row = depth / (13.0 * K)
    if row % 1.0 < 0.09:
        return LINHA
    k = int(row)
    n = max(12, int(120 * (1 - depth / CONE)))
    j = int(a * n)
    if k % 2 == 0:
        return (AZUL, BEGE)[j % 2]
    return (CREME, AZUL_E)[j % 2]

def stripes_h(y, size=5):
    k = (y // size) % 3
    if y % size == 0:
        return LINHA
    return (CREME, AZUL, BEGE)[k]

def sphere(v, cx, cy, cz, r, block_at):
    R = int(math.ceil(r))
    for x in range(-R, R + 1):
        for y in range(-R, R + 1):
            for z in range(-R, R + 1):
                d = x * x + y * y + z * z
                if d <= r * r and d > (r - 2.5) ** 2:          # a shell: nobody sees the inside of a moon
                    v.set(cx + x, cy + y, cz + z, block_at(x, y, z))

def moon(v, cx, cy, cz, r):
    """A glowing moon with a painted cap on its top third (the white orbs of the frames)."""
    def at(x, y, z):
        if y > r * 0.42:
            a = (math.atan2(z, x) / (2 * math.pi)) % 1.0
            if abs(y - r * 0.42) < 1.0:
                return LINHA
            return (AZUL, BEGE)[int(a * 24) % 2] if y < r * 0.75 else CREME
        return LUZ
    sphere(v, cx, cy, cz, r, at)

def small_top(v, cx, cy, cz, R):
    """A small copy of the top far away (the little top in the frames)."""
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            r = math.hypot(x, z)
            if r > R:
                continue
            k = int(r / 2.5)
            v.set(cx + x, cy, cz + z, LINHA if r > R - 1 else (CREME, AZUL, BEGE)[k % 3])
            depth = int((R - r) * 1.3) + 1
            for d in range(1, depth + 1):
                if d > 2 and d < depth - 1 and r < R - 2:
                    continue
                v.set(cx + x, cy - d, cz + z, (AZUL, BEGE)[int(((math.atan2(z, x) / (2 * math.pi)) % 1) * 30) % 2])
    for y in range(1, 6):
        for x in range(-1, 2):
            for z in range(-1, 2):
                v.set(cx + x, cy + y, cz + z, stripes_h(y, 2))

def asteroid(v, n, cx, cy, cz, rx, ry, rz, seed, painted):
    R = int(max(rx, ry, rz)) + 2
    for x in range(-R, R + 1):
        for y in range(-R, R + 1):
            for z in range(-R, R + 1):
                d = (x / rx) ** 2 + (y / ry) ** 2 + (z / rz) ** 2
                d += n.f3(cx + x, cy + y, cz + z, 5, 2) * 0.55
                if d > 1:
                    continue
                # the big rocks are a shell 3 blocks thick (the inside never shows; keeps the arena light to place)
                if max(rx, ry, rz) > 10 and d < (1 - 3.0 / min(rx, ry, rz)) ** 2:
                    continue
                b = ROCHA if n.f3(cx + x, cy + y + 50, cz + z, 4, 2) > -0.1 else ROCHA_E
                v.set(cx + x, cy + y, cz + z, b)
    if painted:
        # a broken piece of the stage: the painted floor on its top
        for x in range(-R, R + 1):
            for z in range(-R, R + 1):
                top = None
                for y in range(R, -R - 1, -1):
                    if v.get(cx + x, cy + y, cz + z) in (ROCHA, ROCHA_E):
                        top = y
                        break
                if top is not None and top > 0:
                    v.set(cx + x, cy + top, cz + z, floor(x * 3 + seed, z * 3, False))

@arena('torneio')
def torneio_do_poder():
    v = Vox(); n = Noise(9401); rng = np.random.default_rng(9401)
    # ---- the stage: painted floor, rock under it, the striped rim, the painted skin of the cone ----
    for x in range(-RA, RA + 1):
        for z in range(-RA, RA + 1):
            r = math.hypot(x, z)
            if r > RA:
                continue
            v.set(x, 0, z, floor(x, z, False))
            edge = r > RA - 1.5
            for y in range(-1, -SLAB - 1, -1):
                v.set(x, y, z, rim(x, z, y) if edge else ROCHA)
            if edge:
                for y in range(-SLAB - 1, -RIM - 1, -1):
                    v.set(x, y, z, rim(x, z, y))
            yb = cone_bottom(r)
            if yb < -SLAB - 1 and not (edge and yb >= -RIM):
                v.set(x, yb, z, cone_skin(x, z, -RIM - yb if yb < -RIM else 0))
                for y in range(yb + 1, min(-SLAB, yb + SHELL + 1)):
                    if v.get(x, y, z) is None:
                        v.set(x, y, z, ROCHA)
    # ---- the stem and its point under the cone ----
    tip = cone_bottom(0)
    L = 40 * K
    for y in range(tip + 1, tip - L, -1):
        rr = STEM_R if y > tip - 26 * K else STEM_R * max(0.0, (y - (tip - L)) / (14.0 * K))
        for x in range(-STEM_R, STEM_R + 1):
            for z in range(-STEM_R, STEM_R + 1):
                d = x * x + z * z
                if d <= rr * rr + 0.5:
                    inner = d < (rr - 2) ** 2
                    v.set(x, y, z, ROCHA if inner else stripes_h(y, 4 * K))
    # ---- the pillar in the middle: horizontal stripes, a ringed cap ----
    PR, PH = 6 * K, 46 * K
    for y in range(1, PH + 1):
        for x in range(-PR, PR + 1):
            for z in range(-PR, PR + 1):
                r = math.hypot(x, z)
                if r <= PR + 0.3:
                    if y == PH:
                        v.set(x, y, z, LINHA if abs(r - 3.5 * K) < 1.2 or r > PR - 1.2 else (AZUL if r > 3.5 * K else CREME))
                    elif r > PR - 2.5:
                        v.set(x, y, z, stripes_h(y, 6 * K))
                    else:
                        v.set(x, y, z, ROCHA)
    # ---- the gods' balcony: a curved band hanging above one side, a parapet outside and a low rail inside ----
    RB, WB, YB = RA + 4 * K, 9 * K, 34 * K
    for x in range(-RB - WB - 2, RB + WB + 3):
        for z in range(-RB - WB - 2, RB + WB + 3):
            r = math.hypot(x, z)
            if r < RB - WB / 2 or r > RB + WB / 2:
                continue
            ang = math.degrees(math.atan2(z, x)) % 360
            if not (200 <= ang <= 330):
                continue
            end = min(ang - 200, 330 - ang)
            for y in range(YB - 3 * K, YB):
                v.set(x, y, z, ROCHA_E if y < YB - 2 else LINHA)
            v.set(x, YB, z, BEGE if int(r / K) % 3 else CREME)
            if r > RB + WB / 2 - 2 or end < 0.4:
                for y in range(YB + 1, YB + 4 * K + 1):
                    v.set(x, y, z, (AZUL, BEGE)[int(ang * 2) % 2] if y < YB + 4 * K - 1 else LINHA)
            elif r < RB - WB / 2 + 1.5:
                for y in range(YB + 1, YB + 3):
                    v.set(x, y, z, LINHA)
    # ---- moons and a small top far away (all inside 350 blocks of the middle: the arena's chunks load together) ----
    MOONS = ((-220, 200, -200, 27), (240, 100, 180, 14))
    for mx, my, mz, mr in MOONS:
        moon(v, mx, my, mz, mr)
    TOP = (-267, -100, 140, 20)
    small_top(v, TOP[0], TOP[1], TOP[2], TOP[3])
    # ---- rocks drifting in the void, some of them broken pieces of a stage ----
    rocks = []
    tries = 0
    while len(rocks) < 40 and tries < 4000:
        tries += 1
        s = rng.uniform(3, 10) if rng.random() < 0.75 else rng.uniform(10, 18)
        ang = rng.uniform(0, 2 * math.pi)
        dist = rng.uniform(RA + 20, RA + 70 - s)
        cx, cz = int(math.cos(ang) * dist), int(math.sin(ang) * dist)
        cy = int(rng.uniform(-187, 147))
        ok = all((cx - a) ** 2 + (cy - b) ** 2 + (cz - c) ** 2 > (s + rs + 14) ** 2 for a, b, c, rs in rocks)
        for mx, my, mz, mr in MOONS + (TOP,):
            ok = ok and (cx - mx) ** 2 + (cy - my) ** 2 + (cz - mz) ** 2 > (s + mr + 16) ** 2
        bang = math.degrees(math.atan2(cz, cx)) % 360
        if 190 <= bang <= 340 and abs(math.hypot(cx, cz) - RB) < s + WB + 12 and abs(cy - YB) < s + 20:
            ok = False
        if not ok:
            continue
        rocks.append((cx, cy, cz, s))
        asteroid(v, n, cx, cy, cz, s, s * rng.uniform(0.55, 0.9), s * rng.uniform(0.7, 1.1), len(rocks) * 37,
                 painted=s > 6 and rng.random() < 0.45)
    flutua = [(-RB - WB, YB - 3 * K - 1, -RB - WB, RB + WB, YB + 4 * K + 1, RB + WB)]
    for mx, my, mz, mr in MOONS + (TOP,):
        R = mr + 4
        flutua.append((mx - R, my - R - 50, mz - R, mx + R, my + R, mz + R))
    for cx, cy, cz, s in rocks:
        R = int(s) + 3
        flutua.append((cx - R, cy - R, cz - R, cx + R, cy + R, cz + R))
    meta = dict(universo='dragonball', personagem='Torneio do Poder', lugar='Arena do Torneio do Poder',
                nome='Torneio do Poder',
                canon='O pião gigante no vácuo: o palco redondo de pedra pintada com o pilar no meio, o camarote dos deuses por cima, as luas e as rochas soltas no espaço.',
                R=RA, altura=60, parede=False, vazio=True, ceu=['#1a0b2e', '#40184f'], neblina=[400, 950], skyLight=15,
                jogador=[0, 1, 100], desafiante=[0, 1, -100], camLado=[470, 220, 510], alvoY=-40,
                flutua=flutua)
    return v, meta
