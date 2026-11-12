"""Bleach arenas."""
import math
import numpy as np
from arenas_all import arena
from lib import Vox, Noise, Pick, terrain, plants

@arena('rukia')
def las_noches():
    """Hueco Mundo outside Las Noches: endless white sand under a black sky and a crescent moon, quartz trees, and
    the enormous white dome of Aizen's palace with its towers on the horizon."""
    v = Vox(); n = Noise(7101); rng = np.random.default_rng(7101)
    R = 42
    def hmap(x, z):
        r = math.hypot(x, z)
        if r > 116:
            return None
        k = min(1, max(0, (r - R + 10) / 22))
        return int(round((0.3 + 0.7 * k) * ((math.sin(x * 0.05 + n.f2(x, z, 34) * 3) * 0.5 + 0.5) * 7 + n.f2(x, z, 14, 3) * 3)))
    def top(x, y, z, h):
        return 'white_concrete_powder' if n.f2(x * 2, z * 2, 6) > -0.35 else 'smooth_quartz'
    H = terrain(v, hmap, -116, 116, -116, 116, top, 'white_concrete_powder', 'calcite', min_depth=2)
    # quartz trees: bare white branching trunks (the forest of Menos is below, these are the dead trees above)
    def qtree(x, y, z):
        h = int(rng.integers(6, 14))
        pts = [(x, y, z)]
        for i in range(h):
            v.set(x, y + i, z, 'quartz_pillar[axis=y]')
        for k in range(int(rng.integers(2, 5))):
            a = rng.uniform(0, 2 * math.pi); sy = int(rng.integers(h // 2, h)); L = int(rng.integers(3, 7))
            for j in range(1, L):
                v.set(x + int(round(math.cos(a) * j)), y + sy + j // 2, z + int(round(math.sin(a) * j)), 'quartz_block')
    for i in range(90):
        a = rng.uniform(0, 2 * math.pi); d = rng.uniform(R - 6, 108)
        x, z = int(math.cos(a) * d), int(math.sin(a) * d)
        if z < -70 and abs(x) < 70:
            continue
        qtree(x, (H.get((x, z)) or 0) + 1, z)
    for (x, z) in ((-18, 10), (16, -8), (4, 24)):
        qtree(x, (H.get((x, z)) or 0) + 1, z)
    # Las Noches: a vast white dome behind a wall, with towers, north
    cz = -150
    for x in range(-110, 111):
        for z in range(-230, -78):
            for y in range(0, 70):
                q = (x / 100) ** 2 + ((z - cz) / 70) ** 2 + (y / 64) ** 2
                if 0.93 <= q <= 1.0:
                    v.set(x, y, z, 'smooth_quartz' if (y % 9) else 'quartz_bricks')
    for x in range(-110, 111):
        for y in range(0, 22):
            z = int(cz + 72 * math.sqrt(max(0, 1 - (x / 104) ** 2))) + 2
            if z < -80:
                v.set(x, y, z, 'quartz_bricks' if y % 6 else 'smooth_quartz')
    for (tx, th) in ((-40, 82), (0, 104), (40, 82), (-70, 60), (70, 60)):
        tz = cz + 20
        for y in range(0, th):
            r = 3.5 if y < th - 10 else 3.5 - (y - th + 10) * 0.33
            for dx in range(-4, 5):
                for dz in range(-4, 5):
                    if dx * dx + dz * dz <= r * r:
                        v.set(tx + dx, y, tz + dz, 'quartz_pillar[axis=y]' if (dx * dx + dz * dz) < (r - 1) ** 2 else 'smooth_quartz')
    # the crescent moon hanging in the black sky
    mx, my, mz = 60, 120, -60
    for dx in range(-14, 15):
        for dy in range(-14, 15):
            a = dx * dx + dy * dy
            b = (dx - 6) ** 2 + (dy + 3) ** 2
            if a <= 196 and b > 150:
                v.set(mx + dx, my + dy, mz, 'sea_lantern')
    meta = dict(universo='bleach', personagem='Rukia', lugar='Las Noches', nome='Las Noches',
                canon='Hueco Mundo na frente de Las Noches: areia branca sem fim, céu preto com a lua em forquilha, árvores de quartzo e a cúpula do palácio do Aizen.',
                R=R, altura=44, ceu=['#000000', '#1a1c24'], neblina=[70, 260],
                jogador=[0, 1, 26], desafiante=[0, 1, -24], camLado=[50, 26, 66], alvoY=12, skyLight=15)
    return v, meta

@arena('byakuya')
def colina_sokyoku():
    """Sokyoku Hill above the Seireitei, where Ichigo fought Byakuya: a bare rock summit, the giant execution frame
    of the Sokyoku with its spear, cliffs falling to the white walls and tiled roofs of the Soul Society."""
    v = Vox(); n = Noise(7201); rng = np.random.default_rng(7201)
    R = 40
    rock = Pick(n, [('stone', 4), ('andesite', 3), ('tuff', 1), ('cobblestone', 1)], 6.0)
    TOP = 0
    def hmap(x, z):
        r = math.hypot(x, z)
        if r > 116:
            return None
        e = 52 + n.f2(x, z, 16, 3) * 8
        if r < e:
            return int(round(n.f2(x, z, 12, 2) * 1.5))
        return int(-min(70, (r - e) * 5)) if r < e + 16 else -70
    def top(x, y, z, h):
        if h <= -60:
            return 'grass_block[snowy=false]'
        return rock.at(x, y, z) if n.f2(x * 2, z * 2, 5) > -0.35 else 'gravel'
    H = terrain(v, hmap, -116, 116, -116, 116, top, 'stone', lambda x, y, z, h: rock.at(x, y, z), min_depth=3)
    # the Sokyoku: two great wooden towers joined by beams, the halberd hanging between them
    sz = -28
    for sx in (-12, 12):
        for y in range(1, 46):
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    if abs(dx) == 2 or abs(dz) == 2:
                        v.set(sx + dx, y, sz + dz, 'dark_oak_log[axis=y]' if (abs(dx) == 2 and abs(dz) == 2) else ('stripped_dark_oak_log[axis=y]' if y % 6 else 'dark_oak_planks'))
    for y in (20, 34, 45):
        for x in range(-14, 15):
            for dz in (-1, 0, 1):
                v.set(x, y, sz + dz, 'dark_oak_log[axis=x]')
    # the halberd (Sokyoku itself): long shaft and blade, pointing down at the platform
    for y in range(22, 44):
        v.set(0, y, sz, 'gold_block' if y > 38 else 'iron_block')
    for y in range(16, 23):
        w = (22 - y) // 2
        for dx in range(-w, w + 1):
            v.set(dx, y, sz, 'iron_block')
    # the execution platform
    for x in range(-6, 7):
        for z in range(sz - 6, sz + 7):
            v.set(x, 0, z, 'smooth_stone')
            v.set(x, 1, z, 'smooth_stone_slab[type=bottom]' if abs(x) == 6 or abs(z - sz) == 6 else None)
    # the Seireitei far below: white walls in a grid, dark blue tiled roofs
    for i in range(140):
        a = rng.uniform(0, 2 * math.pi); d = rng.uniform(72, 114)
        x, z = int(math.cos(a) * d), int(math.sin(a) * d)
        w = int(rng.integers(3, 8)); dd = int(rng.integers(3, 8)); hh = int(rng.integers(4, 10))
        base = -69
        for dx in range(-w, w + 1):
            for dz in range(-dd, dd + 1):
                for y in range(base, base + hh):
                    if abs(dx) == w or abs(dz) == dd:
                        v.set(x + dx, y, z + dz, 'white_concrete' if y > base else 'stone_bricks')
                k = min(w - abs(dx), dd - abs(dz))
                v.set(x + dx, base + hh + min(2, k), z + dz, 'blue_terracotta' if k < 2 else 'gray_terracotta')
    # long white walls between the districts
    for k in range(8):
        a = k / 8 * 2 * math.pi
        for j in range(70, 116):
            x, z = int(math.cos(a) * j), int(math.sin(a) * j)
            for y in range(-69, -63):
                v.set(x, y, z, 'white_concrete')
            v.set(x, -63, z, 'blue_terracotta')
    meta = dict(universo='bleach', personagem='Byakuya', lugar='Colina Sokyoku', nome='Colina Sokyoku',
                canon='O topo da Colina Sokyoku, onde o Ichigo lutou com o Byakuya: rocha nua, a armação gigante da execução com a alabarda e a Seireitei lá embaixo.',
                R=R, altura=46, ceu=['#4f86c6', '#d9e4ea'], neblina=[80, 230],
                jogador=[0, 1, 26], desafiante=[0, 1, -18], camLado=[46, 30, 62], alvoY=14)
    return v, meta
