"""Naruto arenas."""
import math
import numpy as np
from arenas_all import arena
from lib import Vox, Noise, Pick, terrain, tree, humanoid, seal_fluids, Sculpt, sd_ellipsoid

def _rock_pick(n):
    return Pick(n, [('stone', 5), ('andesite', 4), ('tuff', 2), ('cobblestone', 1)], 6.0)

def boulder(v, c, r, n, pick, moss=0.5, rng=None):
    """A weathered boulder: squashed noisy ellipsoid, moss on top."""
    R = int(max(r) + 3)
    for x in range(-R, R + 1):
        for y in range(-R, R + 1):
            for z in range(-R, R + 1):
                p = (c[0] + x, c[1] + y, c[2] + z)
                d = sd_ellipsoid((x + .5, y + .5, z + .5), (0, 0, 0), r) + n.f3(*p, 4.0, 2) * 1.3
                if d <= 0:
                    top = sd_ellipsoid((x + .5, y + 1.5, z + .5), (0, 0, 0), r) + n.f3(p[0], p[1] + 1, p[2], 4.0, 2) * 1.3 > 0
                    b = pick.at(*p)
                    if top and n.f2(p[0] * 3, p[2] * 3, 6) > 0.15 - moss * 0.5:
                        b = 'moss_block'
                    v.set(*p, b)

@arena('madara')
def vale_do_fim():
    """Vale do Fim: the river canyon carved by Hashirama and Madara, the waterfall between their two statues."""
    v = Vox(); n = Noise(1101); rng = np.random.default_rng(1101)
    R = 40
    rock = _rock_pick(n)
    LIP_Z = -54          # cliff edge of the waterfall
    TOP = 30             # plateau height
    def cxr(z):           # river centre line
        return 3.5 * math.sin(z / 23.0) + (z < -40) * 0
    def half(z):          # half width of the valley floor
        if z < -30:
            t = (-30 - z) / 24.0
            return 44 - 24 * min(1, t)
        if z > 46:
            t = (z - 46) / 30.0
            return 44 - 22 * min(1, t)
        return 44
    def hmap(x, z):
        d = math.hypot(x, z)
        if d > 112:
            return None
        e = abs(x - cxr(z)) - half(z) + n.f2(x, z, 18, 3) * 5
        if z < LIP_Z:
            # north plateau, the upper river comes through it to the lip
            e = max(e, 1 + (LIP_Z - z) * 3)
        if e < 0:
            h = 0 + (n.f2(x, z, 10, 2) > 0.45) * 1
            # the plunge pool and the river are cut later
            return h
        wall = TOP + n.f2(x, z, 30, 3) * 6 + n.f2(x, z, 7, 2) * 2
        h = min(wall, e * 3.2 + n.f2(x, z, 6, 2) * 3)
        # terraces: the canyon wall steps like layered rock
        h = math.floor(h / 4) * 4 + min(4, (h % 4) * 2.2)
        # fade the far edge down so the land does not end in a cut wall (hidden in fog anyway)
        if d > 100:
            h -= (d - 100) * 2.5
        return int(round(h))
    grass = 'grass_block[snowy=false]'
    def top(x, y, z, h):
        if h <= 1:
            v_ = n.f2(x * 2, z * 2, 9, 2)
            dr = abs(x - cxr(z))
            # the floor is worn rock near the river (the battle scoured it), grass only near the walls
            if dr < 22 + n.f2(x, z, 8) * 6:
                if v_ > 0.3:
                    return 'moss_block'
                return rock.at(x, y, z) if n.f2(x, z, 5, 2) > -0.25 else 'gravel'
            if v_ > 0.35:
                return 'moss_block'
            if v_ < -0.45:
                return 'coarse_dirt'
            return grass
        if h >= TOP - 6:
            return grass if n.f2(x, z, 5) > -0.55 else 'moss_block'
        return 'moss_block' if n.f2(x, z, 5) > 0.2 else rock.at(x, y, z)
    def under(x, y, z, h):
        if h <= 1:
            return 'dirt' if abs(x - cxr(z)) > 22 else rock.at(x, y, z)
        return 'dirt' if h >= TOP - 3 and y >= h - 1 else rock.at(x, y, z)
    def deep(x, y, z, h):
        # strata: bands of tuff / andesite / stone along y, bent by noise
        k = (y + n.f2(x, z, 20, 2) * 3) % 9
        if k < 1.2:
            return 'tuff'
        if k < 2.0:
            return 'andesite'
        return rock.at(x, y, z)
    H = terrain(v, hmap, -112, 112, -112, 112, top, under, deep, min_depth=3)

    # ---- river, pool and waterfall ----
    pool_c = (cxr(LIP_Z), LIP_Z + 9)
    for x in range(-60, 61):
        for z in range(-80, 113):
            h = H.get((x, z))
            if h is None:
                continue
            dx = x - cxr(z)
            wr = 8.5 + n.f2(x, z, 11, 2) * 2.2 + (z > 30) * (z - 30) * 0.04
            dp = math.hypot((x - pool_c[0]) / 1.25, z - pool_c[1])
            in_pool = dp < 13 + n.f2(x, z, 6) * 1.5 and z >= LIP_Z - 1
            in_river = abs(dx) < wr and z > LIP_Z
            in_upper = abs(x - cxr(z)) < 6.5 and z < LIP_Z and h >= TOP - 2
            if in_pool or in_river:
                depth = 3 if in_pool else (2 if abs(dx) < wr - 4 and n.f2(x, z, 7) > -0.1 else 1)
                for y in range(-depth, 2):
                    v.set(x, y, z, None)
                bed = 'gravel' if n.f2(x, z, 4) > -0.2 else 'clay'
                v.set(x, -depth - 1, z, bed)
                for y in range(-depth, 1):
                    v.set(x, y, z, 'water')
            elif in_upper:
                for y in range(TOP - 2, TOP + 6):
                    v.set(x, y, z, None)
                v.set(x, TOP - 3, z, 'gravel')
                v.set(x, TOP - 2, z, 'water'); v.set(x, TOP - 1, z, 'water')
            elif abs(dx) < wr + 2.2 and z > LIP_Z and h <= 1:
                # river bank: stones and gravel
                b = 'gravel' if n.f2(x * 2, z * 2, 5) > 0.1 else ('stone' if n.f2(x, z, 3) > 0 else 'cobblestone')
                v.set(x, h, z, b)
    # the curtain: the upper river pours over the lip into the pool (the steady state of Minecraft water)
    for x in range(-60, 61):
        if abs(x - cxr(LIP_Z)) < 6.5:
            z = LIP_Z
            for zz in (z, z + 1):
                pass
            v.set(x, TOP - 1, LIP_Z, 'water[level=1]')
            for y in range(1, TOP - 1):
                v.set(x, y, LIP_Z, 'water[level=8]')
            # the cliff face behind the fall stays rock
            for y in range(0, TOP - 1):
                if v.get(x, y, LIP_Z - 1) in (None, 'minecraft:air'):
                    v.set(x, y, LIP_Z - 1, rock.at(x, y, LIP_Z - 1))
            for y in range(1, TOP + 6):
                if v.get(x, y, LIP_Z + 1) not in (None,) and 'water' not in v.get(x, y, LIP_Z + 1):
                    v.set(x, y, LIP_Z + 1, None)

    # ---- stepping stones and boulders in the fight ----
    for i in range(9):
        z = rng.uniform(-30, 36); x = cxr(z) + rng.uniform(-3, 3)
        r = (rng.uniform(1.6, 2.6), rng.uniform(1.0, 1.6), rng.uniform(1.6, 2.6))
        boulder(v, (int(x), 0, int(z)), r, n, rock, 0.8)
    for i in range(14):
        a = rng.uniform(0, 2 * math.pi); d = rng.uniform(16, 36)
        x, z = int(math.cos(a) * d), int(math.sin(a) * d)
        if abs(x - cxr(z)) < 9 or abs(z) < 6 and abs(x) < 6:
            continue
        r = (rng.uniform(1.6, 3.6), rng.uniform(1.2, 2.6), rng.uniform(1.6, 3.6))
        boulder(v, (x, int(r[1] * 0.4), z), r, n, rock, 0.6)

    # ---- the statues: Hashirama (west) and Madara (east), facing each other across the falls ----
    def stone_mat(dark):
        def m(x, y, z, part, lp):
            M = 'battlegroundsmod:pedra_da_estatua'
            if part == 'eye':
                return M + '_sombra'
            if lp is not None and n.f3(x * 1.7, y, z * 1.7, 5, 2) > 0.5:
                return M + '_musgo'
            if part == 'hair':
                return M + '_escura' if dark else (M if n.f3(x, y, z, 3) > -0.2 else M + '_escura')
            if part in ('chest', 'pauldron', 'bracer', 'skirt', 'belt', 'shin', 'collar'):
                if dark:
                    return M + '_escura' if n.f3(x, y, z, 4, 2) > -0.45 else M + '_sombra'
                return M if n.f3(x, y, z, 4, 2) > -0.3 else M + '_escura'
            return M if not dark or part in ('head', 'neck', 'hand', 'finger', 'nose', 'brow') else M + '_escura'
        return m
    for side, (sx, face, hair, dark) in {'h': (-23, 0.0, 'long', False), 'm': (23, math.pi, 'spiky', True)}.items():
        sz = LIP_Z - 3
        base_y = TOP
        # rock shelf they stand on
        # a natural rock column rising out of the pool, wider at the foot, ledges where the strata stick out
        for y in range(-3, base_y + 1):
            t = (y + 3) / (base_y + 3)
            rx = 12.5 - 3 * t; rz = 11 - 2.5 * t
            for x in range(sx - 16, sx + 17):
                for z in range(sz - 16, sz + 14):
                    a = math.atan2(z - sz, x - sx)
                    q = (math.hypot((x - sx) / rx, (z - sz) / rz) + n.f3(x, y * 0.35, z, 9, 3) * 0.42
                         + n.f3(math.cos(a) * 8, y * 0.15, math.sin(a) * 8, 3, 2) * 0.25)
                    if q <= 1:
                        if y == base_y:
                            b = 'moss_block' if n.f2(x, z, 3) > -0.1 else 'stone'
                        elif q > 0.82 and n.f3(x, y, z, 4) > 0.45:
                            b = 'mossy_cobblestone'
                        else:
                            b = deep(x, y, z, base_y)
                        v.set(x, y, z, b)
        humanoid(v, (sx + 0.5, base_y + 1, sz + 0.5), face, 54, stone_mat(dark), hair=hair,
                 raise_arm='right' if side == 'h' else 'left')

    # ---- forest on the plateau ----
    placed = []
    for i in range(520):
        x = int(rng.uniform(-105, 105)); z = int(rng.uniform(-105, 105))
        h = H.get((x, z))
        if h is None or h < TOP - 8 or math.hypot(x, z) > 104:
            continue
        if v.get(x, h + 1, z) is not None or v.get(x, h, z) is None or 'grass' not in v.get(x, h, z) and 'moss' not in v.get(x, h, z):
            continue
        if any((x - a) ** 2 + (z - b) ** 2 < 42 for a, b in placed):
            continue
        if abs(x) < 36 and z < LIP_Z + 6 and z > LIP_Z - 16:
            continue  # keep the statues clear
        placed.append((x, z))
        kind = 'dark_oak' if n.f2(x, z, 30) > 0.1 else 'oak'
        tree(v, x, h + 1, z, rng, kind, height=int(rng.integers(8, 15)), crown=rng.uniform(3.2, 5.0),
             leaves_override='battlegroundsmod:folhas_da_folha' + ('_escura' if kind == 'dark_oak' else ''))
    # a few trees on the valley floor, away from the river
    for i in range(80):
        x = int(rng.uniform(-44, 44)); z = int(rng.uniform(-30, 60))
        h = H.get((x, z))
        if h is None or h > 2 or abs(x - cxr(z)) < 12 or math.hypot(x, z) < 26:
            continue
        if any((x - a) ** 2 + (z - b) ** 2 < 60 for a, b in placed):
            continue
        if v.get(x, h, z) is None or 'water' in v.get(x, h, z):
            continue
        placed.append((x, z))
        tree(v, x, h + 1, z, rng, 'oak', height=int(rng.integers(6, 10)), leaves_override='battlegroundsmod:folhas_da_folha_clara')
    # plants on the grass
    for (x, z), h in H.items():
        if h is None:
            continue
        b = v.get(x, h, z)
        if b and 'grass_block' in b and v.get(x, h + 1, z) is None:
            r = n.f2(x * 5, z * 5, 3)
            if r > 0.35:
                v.set(x, h + 1, z, 'short_grass')
            elif r < -0.6:
                v.set(x, h + 1, z, 'fern')
    seal_fluids(v)
    meta = dict(universo='naruto', personagem='Madara', lugar='Vale do Fim',
                nome='Vale do Fim',
                canon='O vale que Hashirama e Madara abriram na luta deles. A cachoeira cai entre as duas estátuas, que se encaram de lados opostos do rio.',
                R=R, altura=44, ceu=['#7b93ad', '#c9d2d6'], neblina=[70, 210],
                jogador=[-15, 1, 24], desafiante=[13, 1, -20],
                camLado=[58, 34, 70], alvoY=14,
                tints={'grass': (110, 168, 92), 'foliage': (72, 140, 52), 'water': (52, 110, 170)})
    return v, meta

from lib import plants, big_tree, konoha_house, sd_capsule, sd_box  # noqa: E402

def _forest_ground(v, n, R, ext, hfun=None, sky_open=True):
    def hmap(x, z):
        r = math.hypot(x, z)
        if r > ext:
            return None
        h = 0
        if r > R - 4:
            h = int((n.f2(x, z, 20, 3) * 0.5 + 0.5) * min(6, (r - R + 4) * 0.25))
        if hfun:
            h = hfun(x, z, h)
        return h
    def top(x, y, z, h):
        k = n.f2(x * 1.7, z * 1.7, 8, 2)
        return 'moss_block' if k > 0.4 else ('podzol[snowy=false]' if k < -0.45 else 'grass_block[snowy=false]')
    return terrain(v, hmap, -ext, ext, -ext, ext, top, 'dirt', 'stone', min_depth=3)

@arena('shikamaru')
def floresta_dos_nara():
    """The Nara clan forest (where Shikamaru caught Hidan): giant old trees, deer antler racks, shafts of light,
    the clearing where the trap closed."""
    v = Vox(); n = Noise(5101); rng = np.random.default_rng(5101)
    R = 36
    H = _forest_ground(v, n, R, 96)
    trees = []
    # a few giants inside the fight: the shadows need things to cast them
    for (x, z) in ((-14, 8), (17, -10), (6, 22), (-20, -18)):
        big_tree(v, x, (H.get((x, z)) or 0) + 1, z, rng, 'spruce_log', 'battlegroundsmod:folhas_de_pinheiro', height=int(rng.integers(26, 34)), trunk=1.6)
        trees.append((x, z))
    for i in range(700):
        x, z = (int(a) for a in rng.uniform(-92, 92, 2))
        r = math.hypot(x, z)
        if r < R - 2 or r > 92 or any((x - a) ** 2 + (z - b) ** 2 < 120 for a, b in trees):
            continue
        trees.append((x, z))
        big_tree(v, x, (H.get((x, z)) or 0) + 1, z, rng, rng.choice(['spruce_log', 'dark_oak_log', 'spruce_log']),
                 rng.choice(['battlegroundsmod:folhas_de_pinheiro', 'battlegroundsmod:folhas_de_pinheiro_escura', 'battlegroundsmod:folhas_da_folha_escura']),
                 trunk=rng.uniform(1.4, 2.8))
    # the Nara deer: antler racks hung on posts and the fence of the reserve
    for k in range(10):
        a = k / 10 * 2 * math.pi + 0.2; r = R + 3
        x, z = int(math.cos(a) * r), int(math.sin(a) * r)
        h = H.get((x, z)) or 0
        for y in range(1, 4):
            v.set(x, h + y, z, 'spruce_fence')
        v.set(x, h + 4, z, 'dead_bush'); v.set(x, h + 3, z + 1, 'spruce_fence')
    for i in range(400):
        a = rng.uniform(0, 2 * math.pi); r = R + 6 + rng.uniform(-1, 1)
        x, z = int(math.cos(a) * r), int(math.sin(a) * r)
        h = H.get((x, z))
        if h is not None and v.get(x, h + 1, z) is None:
            v.set(x, h + 1, z, 'spruce_fence')
    # mossy fallen logs and stones
    for i in range(14):
        x, z = (int(a) for a in rng.uniform(-R + 4, R - 4, 2))
        a = rng.uniform(0, math.pi); L = int(rng.uniform(5, 11))
        for j in range(L):
            px, pz = int(x + math.cos(a) * j), int(z + math.sin(a) * j)
            h = H.get((px, pz)) or 0
            v.set(px, h + 1, pz, 'spruce_log[axis=%s]' % ('x' if abs(math.cos(a)) > 0.7 else 'z'))
            if n.f2(px, pz, 2) > 0.2:
                v.set(px, h + 2, pz, 'moss_carpet')
    plants(v, H, n, (('short_grass', 0.25), ('fern', -0.4), ('large_fern[half=lower]', 0.6)), surface=('grass_block', 'moss', 'podzol'))
    meta = dict(universo='naruto', personagem='Shikamaru', lugar='Floresta dos Nara', nome='Floresta dos Nara',
                canon='A floresta do clã Nara, onde o Shikamaru prendeu o Hidan: árvores gigantes, os cervos do clã e a clareira onde a armadilha fecha.',
                R=R, altura=34, ceu=['#5d7f9e', '#b9c9b4'], neblina=[40, 130],
                jogador=[0, 1, 24], desafiante=[0, 1, -22], camLado=[4, 30, 34], alvoY=4,
                tints={'grass': (96, 150, 70), 'foliage': (60, 120, 45)})
    return v, meta

@arena('kakashi')
def campo_de_treino_3():
    """Training Ground 3: the three wooden posts of the bell test, the memorial stone, the river and the forest edge
    with Konoha's roofs and the Hokage faces in the distance."""
    v = Vox(); n = Noise(5201); rng = np.random.default_rng(5201)
    R = 38
    def hf(x, z, h):
        if z < -60:
            return h + int(min(40, (-60 - z) * 1.6 + n.f2(x, z, 10) * 4))   # the Hokage mountain
        return h
    H = _forest_ground(v, n, R, 104, hf)
    # river along the east side of the field
    for z in range(-60, 104):
        cx = 30 + 4 * math.sin(z / 14)
        for x in range(int(cx) - 7, int(cx) + 8):
            if abs(x - cx) < 4.5 + n.f2(x, z, 6):
                for y in range(-1, 2):
                    v.set(x, y, z, None)
                v.set(x, -2, z, 'gravel'); v.set(x, -1, z, 'water'); v.set(x, 0, z, 'water')
            elif abs(x - cx) < 6.5:
                v.set(x, 0, z, 'gravel' if n.f2(x, z, 3) > 0 else 'cobblestone')
    # three posts
    for i, x in enumerate((-6, 0, 6)):
        for y in range(1, 5):
            v.set(x, y, -6, 'stripped_oak_log[axis=y]')
        v.set(x, 5, -6, 'oak_slab[type=bottom]')
        v.set(x, 2, -5, 'rope' if False else 'oak_button[face=wall,facing=south]')
    # memorial stone (cenotaph): dark polished slab, names carved
    for dx in range(-1, 2):
        for y in range(1, 4):
            v.set(dx - 14, y, -2, 'polished_blackstone' if y < 3 else 'polished_blackstone_slab[type=bottom]')
    v.set(-14, 1, -1, 'polished_blackstone_stairs[facing=north,half=bottom,shape=straight]')
    v.set(-15, 1, -1, 'poppy'); v.set(-13, 1, -1, 'oxeye_daisy')
    # forest around, thinner on the town side
    trees = []
    for i in range(500):
        x, z = (int(a) for a in rng.uniform(-100, 100, 2))
        r = math.hypot(x, z)
        if r < R + 2 or r > 100 or z < -56 or 22 < x < 40 and z > -60:
            continue
        if any((x - a) ** 2 + (z - b) ** 2 < 70 for a, b in trees):
            continue
        trees.append((x, z))
        tree(v, x, (H.get((x, z)) or 0) + 1, z, rng, rng.choice(['oak', 'oak', 'birch']), height=int(rng.integers(7, 13)),
             crown=rng.uniform(3, 4.6), leaves_override='battlegroundsmod:folhas_da_folha' + rng.choice(['', '_clara', '_escura']))
    # the village beyond the trees, the Hokage mountain behind it (the carved faces went out: Stark 2026-11-13)
    for i in range(18):
        x = int(rng.uniform(-80, 80)); z = int(rng.uniform(-56, -46))
        konoha_house(v, x, (H.get((x, z)) or 0) + 1, z, int(rng.integers(3, 6)), int(rng.integers(3, 5)), int(rng.integers(4, 9)), rng,
                     roof=rng.choice(['red_terracotta', 'orange_terracotta', 'red_terracotta', 'light_blue_terracotta']))
    plants(v, H, n, (('short_grass', 0.3), ('dandelion', 0.8), ('fern', -0.6)))
    meta = dict(universo='naruto', personagem='Kakashi', lugar='Campo de treino 3', nome='Campo de treino 3',
                canon='O campo do teste dos sinos: os três postes de madeira, a pedra memorial, o rio e a vila com o Monte Hokage atrás das árvores.',
                R=R, altura=34, ceu=['#5d9ae0', '#cfe3f1'], neblina=[70, 220],
                jogador=[0, 1, 24], desafiante=[0, 1, -18], camLado=[40, 26, 62], alvoY=10,
                tints={'grass': (110, 180, 80), 'foliage': (70, 150, 50)})
    return v, meta

def face(v, cx, cy, cz, s, n):
    """A Hokage face carved into the mountain: a forehead with the headband line, brow, eyes, nose, mouth."""
    for x in range(-s - 2, s + 3):
        for y in range(-s - 4, s + 4):
            for z in range(-4, 6):
                p = (x + .5, y + .5, z + .5)
                d = sd_ellipsoid(p, (0, 0, 0), (s, s * 1.15, s * 0.55))
                if d <= 0:
                    b = 'battlegroundsmod:pedra_do_pico' if z < 2 else 'battlegroundsmod:pedra_do_pico_clara'
                    if abs(y - s * 0.45) < 1 and z > 0:
                        b = 'battlegroundsmod:pedra_do_pico_escura'      # headband
                    if abs(y - s * 0.05) < 1 and 1 < abs(x) < s * 0.55 and z > 1:
                        b = 'battlegroundsmod:pedra_do_pico_escura'      # eyes
                    if abs(y + s * 0.55) < 1 and abs(x) < s * 0.35 and z > 1:
                        b = 'battlegroundsmod:pedra_do_pico_escura'      # mouth
                    v.set(cx + x, cy + y, cz + z, b)
        # nose
    for y in range(-3, 1):
        v.set(cx, cy + y, cz + int(s * 0.55) + 1, 'battlegroundsmod:pedra_do_pico_clara')

@arena('gaara')
def deserto_de_suna():
    """The desert outside Sunagakure: wind-carved dunes, the rock wall round the village with its gate cut through,
    the round sand houses and the Kazekage tower beyond."""
    v = Vox(); n = Noise(5301); rng = np.random.default_rng(5301)
    R = 42
    SA = 'battlegroundsmod:deserto_areia'; RO = 'battlegroundsmod:deserto_rocha'; RC = 'battlegroundsmod:deserto_rocha_clara'
    def hmap(x, z):
        r = math.hypot(x, z)
        if r > 112:
            return None
        dune = (math.sin(x * 0.07 + n.f2(x, z, 30) * 2.5) * 0.5 + 0.5) * 6 + n.f2(x, z, 16, 3) * 3
        k = min(1, max(0, (r - R + 8) / 20))
        h = dune * (0.25 + 0.75 * k)
        # the cliff wall that rings the village, north side
        if z < -64:
            h = max(h, min(46, (-64 - z) * 4 + n.f2(x, z, 8, 2) * 6))
        return int(round(h))
    def top(x, y, z, h):
        return SA if h < 14 else (RC if n.f2(x, y, 6) > 0 else RO)
    def deep(x, y, z, h):
        return RO if (y + int(n.f2(x, z, 9) * 2)) % 6 else RC
    H = terrain(v, hmap, -112, 112, -112, 112, top, lambda x, y, z, h: SA if h < 14 else RO, deep, min_depth=3)
    # the gate: a narrow slot cut through the wall
    for z in range(-100, -60):
        for x in range(-4, 5):
            for y in range(1, 60):
                if v.get(x, y, z) is not None:
                    v.set(x, y, z, None)
            v.set(x, 0, z, SA)
    # wind-worn rock arches and pillars in the open desert
    for i in range(9):
        a = rng.uniform(0, 2 * math.pi); d = rng.uniform(R + 6, 100)
        x, z = int(math.cos(a) * d), int(math.sin(a) * d)
        if z < -60:
            continue
        hgt = rng.uniform(10, 26); r0 = rng.uniform(2.5, 5)
        for y in range(-2, int(hgt)):
            rr = r0 * (1 + 0.3 * math.sin(y * 0.5 + i)) * (0.75 if y > hgt * 0.6 else 1)
            for dx in range(-7, 8):
                for dz in range(-7, 8):
                    if dx * dx + dz * dz <= rr * rr + n.f3(x + dx, y, z + dz, 3) * 3:
                        v.set(x + dx, (H.get((x, z)) or 0) + y, z + dz, deep(x + dx, y, z + dz, 0))
    # Suna's round houses beyond the wall (seen through the gate and over it) and the Kazekage tower
    for i in range(40):
        x = int(rng.uniform(-90, 90)); z = int(rng.uniform(-108, -82))
        r = rng.uniform(3, 7)
        h0 = 47 if abs(x) > 8 else 0
        for dx in range(-8, 9):
            for dz in range(-8, 9):
                for dy in range(0, 12):
                    q = (dx / r) ** 2 + (dz / r) ** 2 + (dy / (r * 1.1)) ** 2
                    if dx * dx + dz * dz <= r * r and dy < r * 0.6 or q <= 1:
                        v.set(x + dx, h0 + dy, z + dz, 'smooth_sandstone' if dy % 5 else 'cut_sandstone')
    tx, tz = 0, -100
    for dy in range(0, 40):
        r = 9 if dy < 30 else 9 - (dy - 30) * 0.9
        for dx in range(-10, 11):
            for dz in range(-10, 11):
                if dx * dx + dz * dz <= r * r:
                    if dy < 30 and dx * dx + dz * dz < (r - 1) ** 2:
                        continue
                    v.set(tx + dx, dy, tz + dz, 'smooth_sandstone' if dy % 8 else 'orange_terracotta')
    v.set(tx, 20, tz + 9, 'orange_terracotta'); v.set(tx, 21, tz + 9, 'orange_terracotta')
    # dead bushes and the gourd-shaped sand piles
    plants(v, H, n, (('dead_bush', 0.62),), surface=(SA,))
    meta = dict(universo='naruto', personagem='Gaara', lugar='Deserto de Suna', nome='Deserto de Suna',
                canon='O deserto na frente de Sunagakure: dunas varridas pelo vento, a muralha de rocha com o portão cortado e a torre do Kazekage lá atrás.',
                R=R, altura=40, ceu=['#6aa4d8', '#f1d9a8'], neblina=[60, 220],
                jogador=[0, 1, 26], desafiante=[0, 1, -24], camLado=[50, 28, 70], alvoY=10)
    return v, meta

@arena('ino')
def salao_do_chunin():
    """The hall of the Chunin preliminaries (Ino vs Sakura): a tall stone hall, balconies on both long sides with
    railings, the giant statue of two hands making the Ram seal on the back wall, high windows, the board."""
    v = Vox(); n = Noise(5401); rng = np.random.default_rng(5401)
    R = 30
    W, D, HH = 56, 64, 30          # Stark 2026-11-13: room to run from the big techniques
    floor = Pick(n, [('polished_andesite', 4), ('smooth_stone', 3), ('andesite', 1)], 4.0)
    for x in range(-W, W + 1):
        for z in range(-D, D + 1):
            v.set(x, -1, z, 'stone')
            b = floor.at(x, 0, z)
            if x % 8 == 0 or z % 8 == 0:
                b = 'stone_bricks'
            v.set(x, 0, z, b)
            v.set(x, HH + 1, z, 'spruce_planks' if (x % 6) and (z % 6) else 'stripped_spruce_log[axis=x]')
    for y in range(0, HH + 2):
        for x in range(-W - 1, W + 2):
            for z in (-D - 1, D + 1):
                win = 18 <= y <= 25 and x % 7 in (2, 3, 4)
                v.set(x, y, z, 'glass' if win else ('stone_bricks' if y % 6 else 'chiseled_stone_bricks'))
        for z in range(-D - 1, D + 2):
            for x in (-W - 1, W + 1):
                win = 18 <= y <= 25 and z % 7 in (2, 3, 4)
                v.set(x, y, z, 'glass' if win else ('stone_bricks' if y % 6 else 'chiseled_stone_bricks'))
    # balconies (the waiting gallery) along both long walls, outside the fight circle
    for sx in (-1, 1):
        for z in range(-D, D + 1):
            for k in range(0, 6):
                x = sx * (W - k)
                v.set(x, 9, z, 'spruce_planks')
                v.set(x, 8, z, 'spruce_slab[type=top]')
            x = sx * (W - 6)
            v.set(x, 10, z, 'spruce_fence')
            if z % 6 == 0:
                for y in range(1, 9):
                    v.set(x, y, z, 'stripped_spruce_log[axis=y]')
                v.set(x, 11, z, 'lantern[hanging=false]')
        # stairs up to the gallery
        for k in range(9):
            for w in range(2):
                v.set(sx * (W - 1 - w), 1 + k, D - 10 + k, 'stone_brick_stairs[facing=south,half=bottom,shape=straight]')
    # the statue: two huge forearms rising from the back wall, hands pressed together in the Ram seal
    ST = 'battlegroundsmod:pedra_da_estatua'
    zc = -D + 4
    parts = []
    for sx in (-1, 1):
        parts.append(lambda p, sx=sx: sd_capsule(p, (sx * 9, 4, zc - 1), (sx * 3.2, 17, zc + 1), 3.4, 2.8))       # forearm
        parts.append(lambda p, sx=sx: sd_ellipsoid(p, (sx * 1.7, 20.5, zc + 1.5), (2.0, 4.0, 2.8)))                # palm
        for f in range(4):
            parts.append(lambda p, sx=sx, f=f: sd_capsule(p, (sx * (0.6 + f * 0.0), 23, zc + 0.2 + f * 1.15), (sx * 0.6, 28 - abs(f - 1.5) * 0.9, zc + 0.2 + f * 1.15), 0.6))
        parts.append(lambda p, sx=sx: sd_capsule(p, (sx * 2.4, 21, zc + 3.4), (sx * 0.9, 24.5, zc + 4.2), 0.7))     # thumb
    for x in range(-14, 15):
        for y in range(0, 32):
            for z in range(zc - 6, zc + 8):
                p = (x + .5, y + .5, z + .5)
                if min(f(p) for f in parts) <= 0:
                    v.set(x, y, z, ST if n.f3(x, y, z, 4) > -0.35 else ST + '_sombra')
    # pedestal block under the hands
    for x in range(-12, 13):
        for z in range(-D, zc + 5):
            for y in range(0, 4):
                v.set(x, y, z, 'polished_andesite' if y < 3 else 'stone_brick_slab[type=bottom]')
    # the match board on the wall and lamps
    for x in range(-6, 7):
        for y in range(12, 17):
            v.set(x, y, D, 'black_concrete' if 0 < y - 12 < 4 and abs(x) < 6 else 'spruce_planks')
    for x in range(-W + 8, W - 7, 8):
        for z in range(-D + 6, D - 5, 10):
            for j in range(1, 4):
                v.set(x, HH + 1 - j, z, 'chain[axis=y]')
            v.set(x, HH - 3, z, 'lantern[hanging=true]')
    meta = dict(universo='naruto', personagem='Ino', lugar='Salão do Chunin', nome='Salão das preliminares',
                canon='O salão das preliminares do Exame Chunin, onde Ino e Sakura empataram: galerias dos dois lados e a estátua das mãos fazendo o selo no fundo.',
                R=R, altura=28, ceu=['#5d9ae0', '#cfe3f1'], neblina=[140, 280],
                jogador=[0, 1, 22], desafiante=[0, 1, -20], camLado=[26, 20, 40], alvoY=8)
    return v, meta

@arena('naruto')
def estadio_do_chunin():
    """The arena of the Chunin finals (Naruto vs Neji): a round stadium with tiered stands, the Hokage's box with its
    two roofs, the grass and dirt field with trees along the wall, Konoha behind."""
    v = Vox(); n = Noise(5501); rng = np.random.default_rng(5501)
    R = 40
    FW = 50          # field radius (wall)
    def hmap(x, z):
        r = math.hypot(x, z)
        if r > 104:
            return None
        return 0
    def top(x, y, z, h):
        r = math.hypot(x, z)
        k = n.f2(x * 2, z * 2, 8, 2)
        if r > FW + 1:
            return 'grass_block[snowy=false]'
        return 'grass_block[snowy=false]' if k > -0.2 else ('coarse_dirt' if k > -0.5 else 'dirt_path')
    H = terrain(v, hmap, -104, 104, -104, 104, top, 'dirt', 'stone', min_depth=2)
    # the wall and the stands: 12 tiers rising outwards
    for x in range(-80, 81):
        for z in range(-80, 81):
            r = math.hypot(x, z)
            if FW <= r <= FW + 1.2:
                for y in range(1, 9):
                    v.set(x, y, z, 'stone_bricks' if y < 8 else 'stone_brick_slab[type=bottom]')
            elif FW + 1.2 < r <= FW + 26:
                tier = int((r - FW - 1.2) / 2)
                top_y = 8 + tier * 2
                for y in range(1, top_y + 1):
                    if y == top_y:
                        b = 'smooth_stone_slab[type=top]' if (r - FW) % 2 < 1 else 'stone_bricks'
                    else:
                        b = 'stone_bricks' if y > top_y - 2 else ('stone' if y > 2 else 'stone_bricks')
                    if y < top_y - 2 and r < FW + 24:
                        continue          # hollow under the stands
                    v.set(x, y, z, b)
            elif FW + 26 < r <= FW + 27.5:
                for y in range(1, 34):
                    v.set(x, y, z, 'white_terracotta' if y % 7 else 'stripped_spruce_log[axis=y]')
    # trees along the inner wall (the field had them)
    for i in range(22):
        a = i / 22 * 2 * math.pi + rng.uniform(-0.05, 0.05)
        x, z = int(math.cos(a) * (FW - 4)), int(math.sin(a) * (FW - 4))
        if -0.6 < a - math.pi * 1.5 < 0.6:
            continue
        tree(v, x, 1, z, rng, 'oak', height=int(rng.integers(8, 12)), crown=rng.uniform(3.2, 4.4),
             leaves_override='battlegroundsmod:folhas_da_folha' + rng.choice(['', '_clara']))
    # the Hokage's box, north side: a stone tower rising out of the stands, the open platform on top of it with a
    # railing and posts, a wide red hipped roof, a small white cabin over it and a second, smaller roof with its finial.
    # Everything rests on what is under it (Stark 2026-11-13: the old roofs were floating).
    bx, bz = 0, -(FW + 14)
    PY = 30
    for dx in range(-9, 10):
        for dz in range(-7, 8):
            edge = abs(dx) == 9 or abs(dz) == 7
            corner = abs(dx) == 9 and abs(dz) == 7
            for y in range(1, PY):
                if corner:
                    b_ = 'stripped_spruce_log[axis=y]'
                elif edge:
                    b_ = 'white_terracotta' if y >= PY - 4 else 'stone_bricks'
                else:
                    b_ = 'stone'
                v.set(bx + dx, y, bz + dz, b_)
            v.set(bx + dx, PY, bz + dz, 'spruce_planks')
            if edge and dz != -7:
                v.set(bx + dx, PY + 1, bz + dz, 'spruce_fence')
    for (px, pz) in ((-9, -7), (9, -7), (-9, 7), (9, 7), (-3, 7), (3, 7), (-9, 0), (9, 0)):
        for y in range(PY + 1, PY + 8):
            v.set(bx + px, y, bz + pz, 'stripped_spruce_log[axis=y]')
    for dx in range(-9, 10):                                   # the back wall, so the box is a room open to the field
        for y in range(PY + 1, PY + 8):
            v.set(bx + dx, y, bz - 7, 'white_terracotta' if dx % 6 else 'stripped_spruce_log[axis=y]')
    def hip_roof(y0, wx, wz, layers, under=None):
        for k in range(layers):
            hx, hz = wx - k, wz - k
            for dx in range(-hx, hx + 1):
                for dz in range(-hz, hz + 1):
                    rim = abs(dx) >= hx - 1 or abs(dz) >= hz - 1 or k == layers - 1
                    if rim:
                        v.set(bx + dx, y0 + k, bz + dz, 'red_terracotta' if k < layers - 1 else 'red_nether_bricks')
        if under:
            for dx in range(-wx, wx + 1):
                for dz in range(-wz, wz + 1):
                    if abs(dx) == wx or abs(dz) == wz:
                        v.set(bx + dx, y0 - 1, bz + dz, under)
    hip_roof(PY + 8, 12, 10, 5, under='orange_terracotta')    # eaves overhanging the posts
    for dx in range(-4, 5):                                    # the cabin on the roof
        for dz in range(-3, 4):
            for y in range(PY + 13, PY + 16):
                if abs(dx) == 4 or abs(dz) == 3:
                    v.set(bx + dx, y, bz + dz, 'stripped_spruce_log[axis=y]' if abs(dx) == 4 and abs(dz) == 3 else 'white_terracotta')
                else:
                    v.set(bx + dx, y, bz + dz, 'spruce_planks')
    hip_roof(PY + 16, 7, 6, 4, under='orange_terracotta')
    v.set(bx, PY + 20, bz, 'orange_terracotta'); v.set(bx, PY + 21, bz, 'lightning_rod')
    # banners with the leaf crest between the tiers
    for i in range(16):
        a = i / 16 * 2 * math.pi
        x, z = int(math.cos(a) * (FW + 26)), int(math.sin(a) * (FW + 26))
        for y in range(20, 32):
            v.set(x, y, z, 'orange_wool' if y % 4 else 'green_wool')
    # Konoha around
    for i in range(30):
        a = rng.uniform(0, 2 * math.pi); d = rng.uniform(84, 100)
        x, z = int(math.cos(a) * d), int(math.sin(a) * d)
        konoha_house(v, x, 1, z, int(rng.integers(3, 6)), int(rng.integers(3, 5)), int(rng.integers(5, 12)), rng,
                     roof=rng.choice(['red_terracotta', 'orange_terracotta', 'light_blue_terracotta']))
    meta = dict(universo='naruto', personagem='Naruto', lugar='Estádio do Chunin', nome='Estádio das finais',
                canon='A arena das finais do Exame Chunin, onde Naruto venceu o Neji: arquibancada redonda, o camarote do Hokage com os dois telhados e as árvores na beira do campo.',
                R=R, altura=40, ceu=['#5d9ae0', '#cfe3f1'], neblina=[80, 220],
                jogador=[0, 1, 26], desafiante=[0, 1, -24], camLado=[46, 40, 66], alvoY=6,
                tints={'grass': (110, 180, 80), 'foliage': (70, 150, 50)})
    return v, meta

@arena('sasuke')
def esconderijo_uchiha():
    """The Uchiha hideout where Sasuke fought Itachi: a great hall with the fan crest, its roof blown off by Kirin,
    broken walls and pillars, the storm still overhead."""
    v = Vox(); n = Noise(5601); rng = np.random.default_rng(5601)
    R = 38
    W = 44
    rock = Pick(n, [('stone', 4), ('andesite', 3), ('cobblestone', 2), ('tuff', 1)], 5.0)
    # mountain plateau around, rocky
    def hmap(x, z):
        r = math.hypot(x, z)
        if r > 110:
            return None
        if abs(x) <= W + 2 and abs(z) <= W + 2:
            return 0
        return int(max(0, n.f2(x, z, 18, 3) * 8 + (r - 50) * 0.18))
    def top(x, y, z, h):
        return rock.at(x, y, z) if n.f2(x, z, 6) > -0.2 else 'gravel'
    H = terrain(v, hmap, -110, 110, -110, 110, top, 'stone', 'stone', min_depth=3)
    for x in range(-W, W + 1):
        for z in range(-W, W + 1):
            b = 'polished_blackstone_bricks' if (x + z) % 2 else 'polished_blackstone'
            if n.f2(x, z, 5) > 0.35:
                b = 'cracked_polished_blackstone_bricks'
            if n.f2(x * 2, z * 2, 4) > 0.55:
                b = 'gravel'
            v.set(x, 0, z, b)
    # the walls: tall, broken along the top, holes blown through
    for i in range(-W, W + 1):
        for (x, z) in ((i, -W), (i, W), (-W, i), (W, i)):
            top_y = int(18 + n.f2(x, z, 7, 2) * 9)
            for y in range(1, top_y):
                if n.f3(x, y, z, 5, 2) > 0.38:
                    continue
                b = 'deepslate_bricks' if y % 5 else 'polished_deepslate'
                if n.f3(x, y, z, 3) > 0.2:
                    b = 'cracked_deepslate_bricks'
                v.set(x, y, z, b)
    # the Uchiha fan on the north wall: red top, white handle
    for dx in range(-7, 8):
        for dy in range(0, 17):
            p = (dx + .5, dy + .5)
            if (p[0] ** 2 + (p[1] - 10) ** 2) <= 36:
                v.set(dx, 6 + dy, -W + 1, 'red_concrete' if p[1] > 9 else 'white_concrete')
            elif abs(p[0]) < 1.2 and dy < 5:
                v.set(dx, 6 + dy, -W + 1, 'white_concrete')
    # pillars, several snapped, chunks on the floor
    for (px, pz) in ((-24, -24), (0, -28), (24, -24), (-28, 0), (28, 0), (-24, 24), (24, 24), (0, 30)):
        hgt = int(rng.uniform(5, 22))
        for y in range(1, hgt):
            for dx in range(-1, 2):
                for dz in range(-1, 2):
                    v.set(px + dx, y, pz + dz, 'polished_deepslate' if abs(dx) + abs(dz) == 2 else 'deepslate_bricks')
        if hgt < 14:
            a = rng.uniform(0, 2 * math.pi)
            for j in range(3, 10):
                x, z = int(px + math.cos(a) * j), int(pz + math.sin(a) * j)
                v.set(x, 1, z, 'polished_deepslate'); v.set(x, 2, z, 'deepslate_bricks')
    # the throne dais at the north end, where Itachi sat
    for dx in range(-5, 6):
        for dz in range(-W + 2, -W + 9):
            for y in range(1, 3):
                v.set(dx, y, dz, 'polished_blackstone_bricks')
    for dx in range(-1, 2):
        v.set(dx, 3, -W + 4, 'polished_blackstone_stairs[facing=south,half=bottom,shape=straight]')
    v.set(-2, 3, -W + 4, 'polished_blackstone_wall'); v.set(2, 3, -W + 4, 'polished_blackstone_wall')
    for y in range(3, 6):
        for dx in range(-2, 3):
            v.set(dx, y, -W + 3, 'polished_blackstone')
    # scorch marks and lightning-split rock, fires burning in the rubble
    for i in range(20):
        x, z = (int(a) for a in rng.uniform(-W + 3, W - 3, 2))
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if dx * dx + dz * dz <= 4 and rng.random() < 0.7:
                    v.set(x + dx, 0, z + dz, 'blackstone' if rng.random() < 0.5 else 'basalt[axis=y]')
    for i in range(8):
        x, z = (int(a) for a in rng.uniform(-W + 3, W - 3, 2))
        if math.hypot(x, z) < 12:
            continue
        v.set(x, 0, z, 'netherrack'); v.set(x, 1, z, 'fire[age=0,east=false,north=false,south=false,up=false,west=false]')
    meta = dict(universo='naruto', personagem='Sasuke', lugar='Esconderijo Uchiha', nome='Esconderijo Uchiha',
                canon='O esconderijo dos Uchiha onde Sasuke lutou com o Itachi: o salão com o leque do clã, o teto arrancado pelo Kirin, colunas partidas e a tempestade em cima.',
                R=R, altura=40, ceu=['#20232c', '#5a5f6b'], neblina=[40, 160],
                jogador=[0, 1, 26], desafiante=[0, 1, -18], camLado=[50, 34, 64], alvoY=8)
    return v, meta
