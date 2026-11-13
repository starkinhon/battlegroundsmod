"""Dragon Ball arenas."""
import math
import numpy as np
from arenas_all import arena
from lib import Vox, Noise, Pick, terrain, tree, seal_fluids, sd_ellipsoid

def float_island_under(v, H, n, depth=40, rock=None):
    """Hang a rocky underside below a floating landmass (its edge thins, its middle goes deep)."""
    for (x, z), h in H.items():
        if h is None:
            continue
        r = math.hypot(x, z)
    return

@arena('bills')
def planeta_do_bills():
    """Bills' planet: a small floating world, a lawn in front of his pyramid palace, odd rock towers with gardens on
    top, floating islets pouring water into space and a purple sky."""
    v = Vox(); n = Noise(2201); rng = np.random.default_rng(2201)
    R = 44
    ISL = 92
    rock = Pick(n, [('stone', 4), ('andesite', 3), ('calcite', 1), ('tuff', 1)], 7.0)
    def edge(x, z):
        return ISL + n.f2(x, z, 22, 3) * 12
    def hmap(x, z):
        r = math.hypot(x, z)
        e = edge(x, z)
        if r > e:
            return None
        k = max(0.0, min(1.0, (r - R + 2) / 14))
        h = k * ((r - R) * 0.1 + 2 + n.f2(x, z, 18, 3) * 4)
        return int(round(h))
    def top(x, y, z, h):
        k = n.f2(x * 2, z * 2, 7, 2)
        return 'moss_block' if k > 0.45 else 'grass_block[snowy=false]'
    H = terrain(v, hmap, -110, 110, -110, 110, top, 'dirt', lambda x, y, z, h: rock.at(x, y, z), min_depth=3)
    # underside: an upside-down mountain with strata, longer in the middle
    for (x, z), h in H.items():
        if h is None or abs(x) > 110 or abs(z) > 110:
            continue
        r = math.hypot(x, z); e = edge(x, z)
        d = (1 - r / e)
        bottom = -int(4 + d ** 0.7 * 70 + n.f2(x, z, 9, 3) * 8 * d)
        top_y = h - 4
        shell = r > e - 5
        for y in range(bottom, top_y + 1):
            if not shell and bottom + 4 < y < top_y - 1:
                continue      # hollow inside: nobody sees it, and the arena stays light to place
            if v.get(x, y, z) is None:
                k = (y + n.f2(x, z, 16) * 4) % 11
                b = 'terracotta' if k < 1.3 else ('calcite' if k < 2.2 else rock.at(x, y, z))
                v.set(x, y, z, b)
    # stone path from the lawn to the palace
    PZ = -70
    for z in range(PZ + 18, 30):
        w = 3 if z > -20 else 4
        for x in range(-w, w + 1):
            h = H.get((x, z)) or 0
            v.set(x, h, z, 'smooth_sandstone' if abs(x) < w else 'cut_sandstone')
    # ---- the palace: a stepped pyramid of pale stone with purple and gold, a temple room on top ----
    base = 23
    for lvl in range(0, 12):
        half = base - lvl * 2
        y0 = lvl * 3
        for y in range(y0, y0 + 3):
            for x in range(-half, half + 1):
                for z in range(PZ - half, PZ + half + 1):
                    edge_ = abs(x) == half or abs(z - PZ) == half
                    if lvl % 3 == 2 and y == y0 + 2 and edge_:
                        b = 'gold_block'
                    elif edge_ and y == y0:
                        b = 'purpur_block' if lvl % 2 == 0 else 'smooth_quartz'
                    else:
                        b = 'smooth_sandstone' if (x + z + y) % 7 else 'cut_sandstone'
                    v.set(x, y, z, b)
        if half < 5:
            break
    # front stairway cut into the pyramid
    for i in range(0, 34):
        z = PZ + base + 1 - int(i * 0.67)
        y = i
        for x in range(-3, 4):
            v.set(x, y, z, 'sandstone_stairs[facing=north,half=bottom,shape=straight]' if abs(x) < 3 else 'purpur_block')
            for yy in range(y + 1, y + 5):
                if abs(x) < 3 and v.get(x, yy, z) not in (None,):
                    v.set(x, yy, z, None)
    # temple on top: columns, roof, glowing core
    ty = 33
    for x in range(-6, 7):
        for z in range(PZ - 6, PZ + 7):
            v.set(x, ty, z, 'smooth_quartz')
    for cx in (-5, 5):
        for cz in (PZ - 5, PZ + 5):
            for y in range(ty + 1, ty + 8):
                v.set(cx, y, cz, 'quartz_pillar[axis=y]')
    for x in range(-7, 8):
        for z in range(PZ - 7, PZ + 8):
            v.set(x, ty + 8, z, 'purpur_block' if abs(x) == 7 or abs(z - PZ) == 7 else 'smooth_quartz')
    for x in range(-5, 6):
        for z in range(PZ - 5, PZ + 6):
            if abs(x) + abs(z - PZ) <= 6:
                v.set(x, ty + 9, z, 'purpur_slab[type=bottom]' if abs(x) + abs(z - PZ) > 4 else 'gold_block')
    v.set(0, ty + 10, PZ, 'end_rod[facing=up]')
    v.set(0, ty + 1, PZ, 'beacon'); v.set(0, ty, PZ, 'gold_block')
    # big sphinx-like cat heads either side of the stair foot (Bills is a cat god)
    for sx in (-9, 9):
        cz = PZ + base + 4
        for x in range(-3, 4):
            for y in range(0, 8):
                for z in range(-3, 4):
                    p = (x + .5, y + .5, z + .5)
                    d = sd_ellipsoid(p, (0, 3.2, 0), (2.8, 3.0, 2.6))
                    ear = min(sd_ellipsoid(p, (-1.7, 6.4, 0), (0.9, 1.6, 0.8)), sd_ellipsoid(p, (1.7, 6.4, 0), (0.9, 1.6, 0.8)))
                    if min(d, ear) <= 0:
                        v.set(sx + x, y + 1, cz + z, 'smooth_quartz' if y > 1 else 'purpur_block')
        v.set(sx - 1, 5, cz + 3, 'amethyst_block'); v.set(sx + 1, 5, cz + 3, 'amethyst_block')
        for x in range(-3, 4):
            for z in range(-3, 4):
                v.set(sx + x, 0, cz + z, 'purpur_block')
    # ---- rock towers with gardens on their caps (the odd 'mushroom' rocks of his planet) ----
    towers = []
    for i in range(12):
        a = rng.uniform(0, 2 * math.pi); d = rng.uniform(R + 10, ISL - 8)
        x, z = math.cos(a) * d, math.sin(a) * d
        if z < -40 and abs(x) < 34:
            continue
        if any(math.hypot(x - a2, z - b2) < 22 for a2, b2 in towers):
            continue
        towers.append((x, z))
        hgt = rng.uniform(28, 60); r0 = rng.uniform(3.5, 6.0); cap = rng.uniform(8, 13)
        bend = rng.normal(0, 0.05, 2)
        for y in range(0, int(hgt)):
            t = y / hgt
            cx = x + bend[0] * y * t * 3; cz = z + bend[1] * y * t * 3
            rr = r0 * (1.25 - 0.45 * t + 0.25 * math.sin(y * 0.35 + i)) + n.f2(cx, cz, 3) * 0.6
            R2 = int(rr + 2)
            for dx in range(-R2, R2 + 1):
                for dz in range(-R2, R2 + 1):
                    if dx * dx + dz * dz <= rr * rr:
                        k = (y + n.f2(dx, dz, 5) * 2) % 7
                        v.set(int(cx) + dx, y, int(cz) + dz, 'terracotta' if k < 1 else ('calcite' if k < 2 else rock.at(int(cx) + dx, y, int(cz) + dz)))
        # cap: flattened dome with lawn and round trees
        ty_ = int(hgt)
        for dx in range(-int(cap) - 2, int(cap) + 3):
            for dz in range(-int(cap) - 2, int(cap) + 3):
                rr = math.hypot(dx, dz) / (cap + n.f2(cx + dx, cz + dz, 5) * 1.5)
                if rr <= 1:
                    thick = int(2 + (1 - rr) * 5)
                    for y in range(ty_ - thick, ty_ + 1):
                        v.set(int(cx) + dx, y, int(cz) + dz, 'grass_block[snowy=false]' if y == ty_ else rock.at(dx, y, dz))
        for j in range(int(rng.integers(1, 4))):
            ox, oz = rng.uniform(-cap * 0.5, cap * 0.5, 2)
            ball_tree(v, int(cx + ox), ty_ + 1, int(cz + oz), rng)
    # round trees on the lawn edges
    for i in range(70):
        a = rng.uniform(0, 2 * math.pi); d = rng.uniform(R - 2, ISL - 6)
        x, z = int(math.cos(a) * d), int(math.sin(a) * d)
        h = H.get((x, z))
        if h is None or (z < -40 and abs(x) < 30) or (abs(x) < 6 and z > -50):
            continue
        if any(math.hypot(x - a2, z - b2) < 14 for a2, b2 in towers):
            continue
        ball_tree(v, x, h + 1, z, rng)
    # floating islets with little falls pouring off into space
    for i in range(7):
        a = rng.uniform(0, 2 * math.pi); d = rng.uniform(ISL + 10, ISL + 40)
        x, z = int(math.cos(a) * d), int(math.sin(a) * d); y0 = int(rng.uniform(15, 55))
        rr = rng.uniform(6, 11)
        for dx in range(-int(rr) - 1, int(rr) + 2):
            for dz in range(-int(rr) - 1, int(rr) + 2):
                q = math.hypot(dx, dz) / (rr + n.f2(x + dx, z + dz, 4) * 1.6)
                if q <= 1:
                    deep = int((1 - q) ** 0.6 * rr * 1.3)
                    for y in range(y0 - deep, y0 + 1):
                        v.set(x + dx, y, z + dz, 'grass_block[snowy=false]' if y == y0 else rock.at(x + dx, y, z + dz))
        if i % 2 == 0:
            ball_tree(v, x, y0 + 1, z, rng)
    # plants
    for (x, z), h in H.items():
        if h is None:
            continue
        b = v.get(x, h, z)
        if b and 'grass_block' in b and v.get(x, h + 1, z) is None:
            r = n.f2(x * 6, z * 6, 3)
            if r > 0.45:
                v.set(x, h + 1, z, 'short_grass')
            elif r < -0.62:
                v.set(x, h + 1, z, ['allium', 'pink_tulip', 'blue_orchid'][int(abs(x * 7 + z * 3)) % 3])
    meta = dict(universo='dragonball', personagem='Bills', lugar='Planeta do Bills', nome='Planeta do Bills',
                canon='O planeta onde o Deus da Destruição dorme: o gramado na frente do palácio em pirâmide, as torres de pedra com jardim no topo e as ilhas soltas no céu roxo.',
                R=R, altura=60, ceu=['#1d1640', '#a688d6'], neblina=[90, 260],
                jogador=[0, 1, 30], desafiante=[0, 1, -22], camLado=[70, 40, 80], alvoY=14,
                tints={'grass': (102, 196, 128), 'foliage': (160, 92, 200), 'water': (80, 140, 230)})
    return v, meta

def ball_tree(v, x, y, z, rng):
    """The round lollipop trees of Bills' planet and Namek: thin trunk, one big ball of leaves."""
    h = int(rng.integers(6, 13)); r = rng.uniform(2.6, 4.6)
    leaf = ['cherry_leaves', 'azalea_leaves', 'flowering_azalea_leaves', 'mangrove_leaves'][int(rng.integers(0, 4))] + '[persistent=true]'
    for i in range(h):
        v.set(x, y + i, z, 'stripped_birch_log[axis=y]')
    cy = y + h + r * 0.4
    R = int(r + 1)
    for dx in range(-R, R + 1):
        for dy in range(-R, R + 1):
            for dz in range(-R, R + 1):
                if dx * dx + dy * dy + dz * dz <= r * r + rng.uniform(-0.8, 0.4):
                    p = (x + dx, int(cy + dy), z + dz)
                    if v.get(*p) is None:
                        v.set(*p, leaf)

@arena('freeza')
def namek_explodindo():
    """Namek in its last five minutes: the plain where Goku and Freeza fought, tall Namekian mesas, the sea boiling,
    lava breaking through the cracked ground, a Namekian house in ruins and the blue ball trees still standing."""
    v = Vox(); n = Noise(3301); rng = np.random.default_rng(3301)
    R = 44
    NR = 'battlegroundsmod:namek_rock'; NL = 'battlegroundsmod:namek_rock_layer'; NS = 'battlegroundsmod:namek_sand'
    LAND = 78
    def hmap(x, z):
        r = math.hypot(x, z)
        if r > 118:
            return None
        e = LAND + n.f2(x, z, 26, 3) * 16
        if r > e:
            return -3 - int(min(4, (r - e) * 0.3))          # sea floor
        h = 0
        if r > R - 2:
            h = int(((r - R + 2) / (e - R + 2)) * 5 + n.f2(x, z, 9, 2) * 2)
        return max(0, h)
    def top(x, y, z, h):
        if h < 0:
            return NS
        k = n.f2(x * 1.5, z * 1.5, 8, 3)
        r = math.hypot(x, z)
        if r < R + 6:
            # the scorched fight plain: bare rock and sand, burnt grass only in patches
            if k > 0.5:
                return 'grass_block[snowy=false]'
            return NS if k > -0.15 else NR
        return 'grass_block[snowy=false]' if k > -0.35 else NS
    def under(x, y, z, h):
        return NS if h < 0 else 'dirt'
    def deep(x, y, z, h):
        return NL if (y + int(n.f2(x, z, 12) * 3)) % 5 == 0 else NR
    H = terrain(v, hmap, -118, 118, -118, 118, top, under, deep, min_depth=3)
    # the sea, two blocks deep, all the way to the horizon of the arena
    for (x, z), h in H.items():
        if h is not None and h < 0 and abs(x) <= 118 and abs(z) <= 118:
            for y in range(h + 1, 0):
                v.set(x, y, z, 'water')
    # cracks across the plain: lava two blocks down, magma on the lips, they keep going under the sea as dark seams
    for c in range(5):
        a = rng.uniform(0, 2 * math.pi); x, z = rng.uniform(-30, 30, 2)
        L = int(rng.uniform(35, 90))
        for i in range(L):
            a += rng.normal(0, 0.18)
            x += math.cos(a); z += math.sin(a)
            w = 1 if i % 11 < 3 else 0
            for dx in range(-w, w + 1):
                for dz in range(-w, w + 1):
                    px, pz = int(round(x)) + dx, int(round(z)) + dz
                    h = H.get((px, pz))
                    if h is None or h < 0:
                        continue
                    for y in range(h - 2, h + 1):
                        v.set(px, y, pz, None)
                    v.set(px, h - 2, pz, 'lava'); v.set(px, h - 3, pz, 'magma_block')
                    for ex, ez in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        q = (px + ex, h, pz + ez)
                        b = v.get(*q)
                        if b and 'lava' not in b and rng.random() < 0.3:
                            v.set(*q, 'magma_block')
    # ---- the mesas: tall Namekian rock towers, layered, wider at the top like Namek's ----
    mesas = []
    for i in range(16):
        a = rng.uniform(0, 2 * math.pi); d = rng.uniform(R + 8, 112)
        x, z = math.cos(a) * d, math.sin(a) * d
        if any(math.hypot(x - a2, z - b2) < 18 for a2, b2 in mesas):
            continue
        mesas.append((x, z))
        hgt = rng.uniform(20, 58); r0 = rng.uniform(4, 9)
        base_h = H.get((int(x), int(z))) or 0
        for y in range(min(base_h, -3), int(hgt)):
            t = max(0, y) / hgt
            rr = r0 * (1.0 - 0.15 * t + 0.35 * t ** 6) + 0.6 * math.sin(y * 0.9 + i)
            R2 = int(rr + 3)
            for dx in range(-R2, R2 + 1):
                for dz in range(-R2, R2 + 1):
                    q = math.hypot(dx, dz) + n.f3(x + dx, y * 0.5, z + dz, 5, 2) * 1.6
                    if q <= rr:
                        b = NL if (y % 6 == 0 or (y % 6 == 1 and q > rr - 1)) else NR
                        if y == int(hgt) - 1:
                            b = 'grass_block[snowy=false]'
                        v.set(int(x) + dx, y, int(z) + dz, b)
        if hgt > 30 and rng.random() < 0.6:
            namek_tree(v, int(x), int(hgt), int(z), rng)
        if rng.random() < 0.35:
            # broken: the top half sheared off and lying on the ground
            pass
    # ball trees (Ajisa) on the green ground
    for i in range(60):
        a = rng.uniform(0, 2 * math.pi); d = rng.uniform(R - 4, LAND + 6)
        x, z = int(math.cos(a) * d), int(math.sin(a) * d)
        h = H.get((x, z))
        if h is None or h < 0 or v.get(x, h, z) is None or 'grass' not in v.get(x, h, z):
            continue
        if any(math.hypot(x - a2, z - b2) < 12 for a2, b2 in mesas):
            continue
        namek_tree(v, x, h + 1, z, rng)
    # a Namekian house in ruins near the edge of the fight
    ruined_dome(v, -30, 0, -34, 7, 8, n, rng)
    ruined_dome(v, -46, H.get((-46, -18)) or 2, -18, 4, 5, n, rng)
    # boulders torn out of the ground
    for i in range(10):
        a = rng.uniform(0, 2 * math.pi); d = rng.uniform(12, R - 4)
        x, z = int(math.cos(a) * d), int(math.sin(a) * d)
        r = rng.uniform(1.5, 3.0)
        for dx in range(-4, 5):
            for dy in range(-2, 5):
                for dz in range(-4, 5):
                    if (dx / r) ** 2 + (dy / (r * 0.8)) ** 2 + (dz / r) ** 2 <= 1 + n.f3(x + dx, dy, z + dz, 2) * 0.4:
                        v.set(x + dx, 1 + dy, z + dz, NR if dy < r * 0.5 else NL)
    # ---- the planet dying (Stark 2026-11-13: "laranja de fogo invadindo no fundo") ----
    # the sea boils over magma, and past the shallows it is already lava; a crust of blackstone keeps the two apart
    for (x, z), h in H.items():
        if h is None or h >= 0 or abs(x) > 118 or abs(z) > 118:
            continue
        r = math.hypot(x, z) + n.f2(x, z, 14, 2) * 6
        if r >= 96:
            for y in range(h + 1, 0):
                v.set(x, y, z, 'lava')
            v.set(x, h, z, 'magma_block')
        elif r >= 93:
            for y in range(h + 1, 0):
                v.set(x, y, z, 'blackstone' if y < -1 else 'basalt[axis=y]')
        elif n.f2(x * 2, z * 2, 5) > -0.2:
            v.set(x, h, z, 'magma_block')                     # bubble columns: the sea boiling
    # the ground beyond the fight burning: scorched rock, magma seams, grass gone to ash near the lava sea
    for (x, z), h in H.items():
        if h is None or h < 0:
            continue
        r = math.hypot(x, z)
        if r < R + 10:
            continue
        k = n.f2(x * 0.8 + 50, z * 0.8, 10, 3) + (r - 70) / 60
        b = v.get(x, h, z)
        if b is None or 'grass' not in b and 'namek_sand' not in b:
            continue
        if k > 0.55:
            v.set(x, h, z, 'magma_block')
        elif k > 0.3:
            v.set(x, h, z, 'blackstone')
        elif k > 0.1:
            v.set(x, h, z, 'coarse_dirt')
    # volcanoes torn open on the horizon, lava pouring down their flanks into the burning sea
    vents = []
    for i in range(40):
        if len(vents) >= 6:
            break
        a = rng.uniform(0, 2 * math.pi); d = rng.uniform(82, 112)
        x, z = math.cos(a) * d, math.sin(a) * d
        if any(math.hypot(x - a2, z - b2) < 26 for a2, b2 in mesas + vents):
            continue
        vents.append((x, z))
        hgt = rng.uniform(26, 46); rb = rng.uniform(13, 20); rc = rng.uniform(3, 5)
        flows = [rng.uniform(0, 2 * math.pi) for _ in range(int(rng.integers(2, 4)))]
        RB = int(rb + 4)
        for dx in range(-RB, RB + 1):
            for dz in range(-RB, RB + 1):
                q = math.hypot(dx, dz)
                ang = math.atan2(dz, dx)
                top = hgt * max(0.0, 1 - q / rb) ** 1.3 + n.f2(x + dx, z + dz, 4, 2) * 2.5
                if q < rc:
                    top = hgt - 3                                  # the crater
                if top < 1:
                    continue
                groove = any(abs(math.remainder(ang - f, 2 * math.pi)) * q < 1.3 for f in flows) and q > rc
                px, pz = int(x) + dx, int(z) + dz
                for y in range(-5, int(top) + 1):
                    t = y / hgt
                    b = 'magma_block' if n.f3(px, y * 0.7, pz, 3) > 0.45 - t * 0.3 else (
                        'basalt[axis=y]' if (y + dx) % 7 == 0 else 'blackstone')
                    v.set(px, y, pz, b)
                if groove or q < rc:
                    v.set(px, int(top), pz, 'lava')                # lava rivers and the crater's lake
                    v.set(px, int(top) - 1, pz, 'magma_block')
    seal_fluids(v, NR)
    meta = dict(universo='dragonball', personagem='Freeza', lugar='Namek explodindo', nome='Namek explodindo',
                canon='Os últimos cinco minutos de Namek: a planície da luta contra o Goku, chão rachado com lava, o mar fervendo e virando lava no horizonte, vulcões abertos, as torres de pedra e as árvores azuis, tudo sob um céu de fogo.',
                R=R, altura=60, ceu=['#2a0907', '#ff6420'], neblina=[75, 230],
                jogador=[0, 1, 26], desafiante=[0, 1, -22], camLado=[64, 38, 76], alvoY=10,
                tints={'grass': (79, 182, 214), 'foliage': (47, 134, 184), 'water': (63, 191, 122)})
    return v, meta

def namek_tree(v, x, y, z, rng):
    """Ajisa: a long bare trunk with one round crown of blue leaves (foliage tint)."""
    h = int(rng.integers(7, 14)); r = rng.uniform(2.4, 3.6)
    for i in range(h):
        v.set(x, y + i, z, 'stripped_birch_log[axis=y]')
    cy = y + h + int(r * 0.5)
    R = int(r + 1)
    for dx in range(-R, R + 1):
        for dy in range(-R, R + 1):
            for dz in range(-R, R + 1):
                if dx * dx + (dy * 1.15) ** 2 + dz * dz <= r * r + rng.uniform(-0.7, 0.3):
                    p = (x + dx, cy + dy, z + dz)
                    if v.get(*p) is None:
                        v.set(*p, 'oak_leaves[persistent=true]')

def ruined_dome(v, cx, y0, cz, r, h, n, rng):
    """A Namekian dome house broken open by the fight."""
    for x in range(cx - r - 1, cx + r + 2):
        for z in range(cz - r - 1, cz + r + 2):
            for y in range(0, h + 1):
                q = ((x - cx) / r) ** 2 + ((z - cz) / r) ** 2 + (y / h) ** 2
                if 0.72 <= q <= 1:
                    # a big bite out of the side facing the fight and random holes
                    if (x - cx) * 0.7 + (z - cz) > r * 0.2 and y > 1 + n.f3(x, y, z, 3) * 3:
                        continue
                    if n.f3(x, y, z, 2.5) > 0.4:
                        continue
                    v.set(x, y0 + y, z, 'white_concrete' if y > 0 else 'smooth_quartz')
    # rubble
    for i in range(int(r * 4)):
        a = rng.uniform(0, 2 * math.pi); d = rng.uniform(r * 0.6, r * 2.2)
        x, z = int(cx + math.cos(a) * d), int(cz + math.sin(a) * d)
        v.set(x, y0 + 1, z, rng.choice(['white_concrete', 'smooth_quartz_slab[type=bottom]', 'white_concrete_powder']))

from lib import plants, sd_capsule  # noqa: E402

def _rock(n):
    return Pick(n, [('stone', 4), ('andesite', 3), ('granite', 1)], 6.0)

@arena('kuririn')
def torneio_de_artes_marciais():
    """The World Martial Arts Tournament: the square stone ring of tiles, the crowd walls, the temple-roofed hall
    the fighters walk out of, palm trees and the island town around."""
    v = Vox(); n = Noise(6101); rng = np.random.default_rng(6101)
    R = 36
    def hmap(x, z):
        return 0 if math.hypot(x, z) < 104 else None
    def top(x, y, z, h):
        return 'grass_block[snowy=false]' if n.f2(x, z, 9) > -0.4 else 'sand'
    H = terrain(v, hmap, -104, 104, -104, 104, top, 'dirt', 'stone', min_depth=2)
    # the ring: raised 2 blocks, big square tiles with dark joints, steps on the front
    RW = 24
    for x in range(-RW, RW + 1):
        for z in range(-RW, RW + 1):
            for y in (0, 1):
                v.set(x, y, z, 'smooth_stone' if y else 'stone_bricks')
            joint = x % 6 == 0 or z % 6 == 0
            b = 'polished_diorite' if not joint else 'smooth_stone'
            if n.f2(x, z, 3) > 0.55 and not joint:
                b = 'calcite'
            v.set(x, 2, z, b)
            if abs(x) == RW or abs(z) == RW:
                v.set(x, 2, z, 'smooth_stone_slab[type=top]' if (x + z) % 2 else 'polished_andesite')
    for x in range(-4, 5):
        v.set(x, 1, RW + 1, 'stone_brick_stairs[facing=north,half=bottom,shape=straight]')
        v.set(x, 0, RW + 2, 'stone_brick_stairs[facing=north,half=bottom,shape=straight]')
    # the low wall round the grounds (the crowd stands behind it) and the stands
    for x in range(-64, 65):
        for z in range(-64, 65):
            m = max(abs(x), abs(z))
            if m == 46:
                for y in range(1, 4):
                    v.set(x, y, z, 'white_terracotta' if y < 3 else 'red_terracotta')
            elif 47 <= m <= 60:
                t = (m - 47) // 2
                for y in range(1, 4 + t * 2):
                    if y > 1 + t * 2:
                        v.set(x, y, z, 'smooth_sandstone' if (m - 47) % 2 == 0 else 'cut_sandstone')
            elif m == 61:
                for y in range(1, 18):
                    v.set(x, y, z, 'white_terracotta' if y % 6 else 'red_terracotta')
    # the fighters' hall: north side, Chinese-temple roof with upturned corners
    hz = -40
    for x in range(-12, 13):
        for z in range(hz - 8, hz + 5):
            for y in range(1, 9):
                if abs(x) == 12 or z in (hz - 8, hz + 4):
                    b = 'red_concrete' if (abs(x) == 12 and abs(z - hz) % 4 == 0) or (z == hz + 4 and x % 4 == 0) else 'white_terracotta'
                    if z == hz + 4 and abs(x) < 3 and y < 6:
                        b = None
                    v.set(x, y, z, b)
    for k, y in enumerate(range(9, 16)):
        for x in range(-15 + k, 16 - k):
            for z in range(hz - 11 + k, hz + 8 - k):
                edge = abs(x) == 15 - k or z in (hz - 11 + k, hz + 7 - k)
                if edge or k == 6:
                    v.set(x, y, z, 'orange_terracotta' if k % 2 == 0 else 'red_terracotta')
        for (cx, cz) in ((-15 + k, hz - 11 + k), (15 - k, hz - 11 + k), (-15 + k, hz + 7 - k), (15 - k, hz + 7 - k)):
            if k == 0:
                v.set(cx, y + 1, cz, 'orange_terracotta')
    v.set(0, 16, hz - 2, 'gold_block')
    # palm trees and the town of Papaya island
    for i in range(40):
        a = rng.uniform(0, 2 * math.pi); d = rng.uniform(64, 98)
        x, z = int(math.cos(a) * d), int(math.sin(a) * d)
        if i % 2:
            palm(v, x, 1, z, rng)
        else:
            w, dd, hh = int(rng.integers(3, 6)), int(rng.integers(3, 6)), int(rng.integers(5, 14))
            for dx in range(-w, w + 1):
                for dz in range(-dd, dd + 1):
                    for y in range(1, hh):
                        if abs(dx) == w or abs(dz) == dd:
                            v.set(x + dx, y, z + dz, 'white_terracotta' if y % 4 else 'light_blue_terracotta')
                    v.set(x + dx, hh, z + dz, rng.choice(['red_terracotta', 'orange_terracotta', 'blue_terracotta']))
    for (x, z) in ((-40, 30), (40, 30), (-40, -10), (40, -10)):
        palm(v, x, 1, z, rng)
    plants(v, H, n, (('short_grass', 0.4),))
    meta = dict(universo='dragonball', personagem='Kuririn', lugar='Torneio de Artes Marciais', nome='Torneio de Artes Marciais',
                canon='O Torneio de Artes Marciais na Ilha Papaya: o ringue de pedra quadrado, o muro da torcida, o salão de onde os lutadores saem e as palmeiras.',
                R=R, altura=40, ceu=['#3f8fe6', '#cde6f7'], neblina=[90, 240],
                jogador=[0, 3, 16], desafiante=[0, 3, -16], camLado=[42, 30, 54], alvoY=4)
    return v, meta

def palm(v, x, y, z, rng):
    h = int(rng.integers(8, 14)); a = rng.uniform(0, 2 * math.pi); bend = rng.uniform(0.05, 0.18)
    px, pz = float(x), float(z)
    for i in range(h):
        px += math.cos(a) * bend * i / h * 2; pz += math.sin(a) * bend * i / h * 2
        v.set(int(round(px)), y + i, int(round(pz)), 'jungle_log[axis=y]')
    tx, ty, tz = int(round(px)), y + h, int(round(pz))
    v.set(tx, ty, tz, 'jungle_leaves[persistent=true]')
    for k in range(6):
        b = k / 6 * 2 * math.pi + rng.uniform(-0.2, 0.2)
        for j in range(1, 6):
            dy = 0 if j < 3 else -(j - 2)
            v.set(tx + int(round(math.cos(b) * j)), ty + dy, tz + int(round(math.sin(b) * j)), 'jungle_leaves[persistent=true]')

@arena('piccolo')
def planalto_rochoso():
    """The rocky wasteland where Piccolo trained Gohan: red-brown mesas and needle rocks, a dry river bed, the cliff
    Gohan was left on, sparse dry grass."""
    v = Vox(); n = Noise(6201); rng = np.random.default_rng(6201)
    R = 44
    rock = Pick(n, [('terracotta', 3), ('brown_terracotta', 2), ('red_terracotta', 1), ('granite', 1)], 7.0)
    def hmap(x, z):
        r = math.hypot(x, z)
        if r > 112:
            return None
        h = n.f2(x, z, 24, 3) * 2
        k = min(1, max(0, (r - R + 4) / 14))
        h += k * (max(0, n.f2(x, z, 40, 3)) * 18)
        return int(round(h))
    def top(x, y, z, h):
        k = n.f2(x * 2, z * 2, 7, 2)
        return 'coarse_dirt' if k > 0.2 else ('red_sand' if k > -0.3 else 'packed_mud')
    H = terrain(v, hmap, -112, 112, -112, 112, top, lambda x, y, z, h: 'terracotta', lambda x, y, z, h: rock.at(x, y, z), min_depth=3)
    # mesas and needles
    spots = []
    for i in range(30):
        a = rng.uniform(0, 2 * math.pi); d = rng.uniform(R - 2, 108)
        x, z = math.cos(a) * d, math.sin(a) * d
        if any(math.hypot(x - p, z - q) < 16 for p, q in spots):
            continue
        spots.append((x, z))
        needle = rng.random() < 0.45
        hgt = rng.uniform(25, 60) if needle else rng.uniform(16, 34)
        r0 = rng.uniform(2.5, 4.5) if needle else rng.uniform(7, 14)
        base_h = H.get((int(x), int(z))) or 0
        for y in range(base_h - 2, base_h + int(hgt)):
            t = (y - base_h) / hgt
            rr = r0 * ((1 - 0.6 * t) if needle else (1 - 0.12 * t)) + 0.5 * math.sin(y * 0.7 + i)
            R2 = int(rr + 3)
            for dx in range(-R2, R2 + 1):
                for dz in range(-R2, R2 + 1):
                    if math.hypot(dx, dz) + n.f3(x + dx, y * 0.5, z + dz, 4, 2) * 1.8 <= rr:
                        k = (y + int(n.f2(x + dx, z + dz, 20) * 3)) % 7
                        b = ('orange_terracotta' if k == 0 else 'white_terracotta' if k == 3 else rock.at(int(x) + dx, y, int(z) + dz))
                        v.set(int(x) + dx, y, int(z) + dz, b)
        if not needle:
            for dx in range(-int(r0), int(r0) + 1):
                for dz in range(-int(r0), int(r0) + 1):
                    p = (int(x) + dx, base_h + int(hgt) - 1, int(z) + dz)
                    if v.get(*p) and v.get(p[0], p[1] + 1, p[2]) is None and n.f2(p[0], p[2], 4) > -0.2:
                        v.set(*p, 'coarse_dirt')
    # boulders inside the fight
    for i in range(12):
        a = rng.uniform(0, 2 * math.pi); d = rng.uniform(10, R - 6)
        x, z = int(math.cos(a) * d), int(math.sin(a) * d)
        r = rng.uniform(1.4, 3.2)
        for dx in range(-4, 5):
            for dy in range(-1, 5):
                for dz in range(-4, 5):
                    if (dx / r) ** 2 + (dy / (r * 0.8)) ** 2 + (dz / r) ** 2 <= 1 + n.f3(x + dx, dy, z + dz, 2) * 0.4:
                        v.set(x + dx, (H.get((x, z)) or 0) + dy, z + dz, rock.at(x + dx, dy, z + dz))
    plants(v, H, n, (('dead_bush', 0.55), ('short_grass', -0.7)), surface=('coarse_dirt', 'red_sand'))
    meta = dict(universo='dragonball', personagem='Piccolo', lugar='Planalto rochoso', nome='Planalto rochoso',
                canon='O descampado de pedra onde o Piccolo treinou o Gohan: mesas e agulhas de rocha avermelhada, chão seco e capim morto.',
                R=R, altura=60, ceu=['#5b97d8', '#e8d6b4'], neblina=[80, 230],
                jogador=[0, 1, 28], desafiante=[0, 1, -24], camLado=[20, 42, 62], alvoY=4)
    return v, meta

@arena('kame')
def ilha_kame_house():
    """Kame House's little island: the pink house with its red roof and 'KAME HOUSE' sign, one palm tree, sand all
    round and the open sea. The fight is on the island and the shallows around it."""
    v = Vox(); n = Noise(6301); rng = np.random.default_rng(6301)
    R = 36
    def hmap(x, z):
        r = math.hypot(x, z)
        if r > 116:
            return None
        e = 30 + n.f2(x, z, 12, 2) * 4
        if r < e:
            return int(round(max(0, (1 - r / e) * 2.5)))
        return -1 - int(min(5, (r - e) * 0.25))
    def top(x, y, z, h):
        return 'sand' if h >= -1 else ('sand' if n.f2(x, z, 5) > -0.2 else 'gravel')
    H = terrain(v, hmap, -116, 116, -116, 116, top, 'sand', 'sandstone', min_depth=2)
    for (x, z), h in H.items():
        if h is not None and h < 0 and abs(x) <= 116 and abs(z) <= 116:
            for y in range(h + 1, 1):
                v.set(x, y, z, 'water')
    # Kame House (pink walls, red roof, porch, round windows)
    hx, hz = -6, -12
    for dx in range(-6, 7):
        for dz in range(-5, 6):
            for y in range(2, 9):
                if abs(dx) == 6 or abs(dz) == 5:
                    b = 'pink_terracotta' if y > 2 else 'white_terracotta'
                    if y in (4, 5) and (dx in (-3, 3) and abs(dz) == 5 or dz in (-2, 2) and abs(dx) == 6):
                        b = 'glass_pane'
                    if dz == 5 and dx in (0, 1) and y < 6:
                        b = None
                    v.set(hx + dx, y, hz + dz, b)
            v.set(hx + dx, 2, hz + dz, 'white_terracotta')
    for k in range(0, 5):
        for dx in range(-8 + k, 9 - k):
            for dz in range(-7 + k, 8 - k):
                if abs(dx) == 8 - k or abs(dz) == 7 - k or k == 4:
                    v.set(hx + dx, 9 + k, hz + dz, 'red_terracotta')
    for dx in range(-3, 4):
        v.set(hx + dx, 7, hz + 6, 'white_concrete')
    for dx in (-2, -1, 0, 1, 2):
        v.set(hx + dx, 7, hz + 6, 'red_concrete' if dx % 2 == 0 else 'white_concrete')
    for dx in range(-7, 8):
        for dz in range(6, 9):
            v.set(hx + dx, 2, hz + dz, 'smooth_quartz_slab[type=top]' if dz < 8 else 'birch_planks')
    for dx in (-7, 7):
        for y in range(3, 6):
            v.set(hx + dx, y, hz + 8, 'birch_fence')
    palm(v, 12, 2, -8, rng)
    palm(v, 15, 1, 6, rng)
    # the beach chair and umbrella
    v.set(4, 2, 4, 'red_carpet'); v.set(4, 2, 5, 'red_carpet')
    for y in range(2, 6):
        v.set(6, y, 4, 'birch_fence')
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if abs(dx) + abs(dz) < 4:
                v.set(6 + dx, 6, 4 + dz, 'red_wool' if (dx + dz) % 2 else 'white_wool')
    # a few rocks in the shallows to stand on
    for i in range(9):
        a = rng.uniform(0, 2 * math.pi); d = rng.uniform(30, 44)
        x, z = int(math.cos(a) * d), int(math.sin(a) * d)
        r = rng.uniform(1.5, 3)
        for dx in range(-4, 5):
            for dy in range(-4, 3):
                for dz in range(-4, 5):
                    if (dx / r) ** 2 + (dy / r) ** 2 + (dz / r) ** 2 <= 1 + n.f3(x + dx, dy, z + dz, 2) * 0.4:
                        v.set(x + dx, dy, z + dz, 'stone' if dy < 1 else 'mossy_cobblestone')
    seal_fluids(v, 'sand')
    meta = dict(universo='dragonball', personagem='Mestre Kame', lugar='Ilha da Kame House', nome='Ilha da Kame House',
                canon='A ilhota da Kame House: a casa rosa de telhado vermelho, a palmeira, a areia e o mar raso em volta.',
                R=R, altura=40, ceu=['#3f9ae8', '#d8eefa'], neblina=[90, 260],
                jogador=[0, 3, 18], desafiante=[0, 3, -2], camLado=[38, 24, 48], alvoY=4,
                tints={'water': (40, 160, 220)})
    return v, meta

@arena('goku')
def planeta_do_kaioh():
    """King Kai's tiny planet at the end of Snake Way: a small round world with heavy gravity, green lawn, the domed
    house, his round car, one tree, the clouds of Other World below and the yellow sky."""
    v = Vox(); n = Noise(6401); rng = np.random.default_rng(6401)
    R = 36
    PR = 58                                   # planet radius: the lawn curves away round the fight
    cy = -PR
    for x in range(-PR - 2, PR + 3):
        for z in range(-PR - 2, PR + 3):
            for y in range(-2 * PR - 2, 2):
                d = math.sqrt(x * x + (y - cy) ** 2 + z * z)
                if d <= PR:
                    if y < cy + PR - 6 and d < PR - 3:
                        continue        # hollow
                    v.set(x, y, z, 'grass_block[snowy=false]' if d > PR - 1 else ('dirt' if d > PR - 3 else 'stone'))
    def surf(x, z):
        q = PR * PR - x * x - z * z
        return int(math.floor(cy + math.sqrt(q))) if q > 0 else None
    # the house: white dome with a red band, door and round windows
    hx, hz = 0, -22
    sy = surf(hx, hz)
    for dx in range(-8, 9):
        for dz in range(-8, 9):
            for dy in range(0, 10):
                q = (dx / 7.5) ** 2 + (dz / 7.5) ** 2 + (dy / 9.0) ** 2
                if 0.78 <= q <= 1.0:
                    b = 'white_concrete' if dy != 3 else 'red_concrete'
                    if dy in (2, 3, 4) and abs(dx) < 2 and dz > 5:
                        b = None
                    v.set(hx + dx, sy + 1 + dy, hz + dz, b)
    for dy in range(9, 13):
        v.set(hx, sy + dy, hz, 'white_concrete' if dy < 12 else 'red_concrete')
    # the car (round red body) and the tree
    cx_, cz_ = 18, -10
    s2 = surf(cx_, cz_)
    for dx in range(-3, 4):
        for dz in range(-2, 3):
            for dy in range(0, 3):
                if (dx / 3.2) ** 2 + (dz / 2.2) ** 2 + (dy / 2.6) ** 2 <= 1:
                    v.set(cx_ + dx, s2 + 2 + dy, cz_ + dz, 'red_concrete' if dy < 2 else 'light_blue_stained_glass')
    for (dx, dz) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        v.set(cx_ + dx, s2 + 1, cz_ + dz, 'black_concrete')
    tree(v, -18, surf(-18, -6) + 1, -6, rng, 'oak', height=9, crown=4)
    # the road that goes all the way round the little planet (a great circle, past the house and the car), and Snake
    # Way leaving it tangent to the lawn, yellow, out into the sky
    a = math.radians(30); nx, nz = math.cos(a), math.sin(a)
    for (x, y, z), b in list(v.b.items()):
        if b and 'grass_block' in b and abs(x * nx + z * nz) <= 1.6:
            v.set(x, y, z, 'dirt_path')
    tx, tz = -nz, nx
    y0 = surf(int(tx * 30), int(tz * 30))
    for i in range(0, 90):
        d = 30 + i * 0.5
        y = int(round(y0 - i * 0.15))
        for k in (-1.0, -0.5, 0.0, 0.5, 1.0):
            x, z = int(round(tx * d + nx * k)), int(round(tz * d + nz * k))
            s0 = surf(x, z)
            if s0 is not None and s0 >= y:
                continue
            v.set(x, y, z, 'yellow_terracotta' if abs(k) < 0.6 else 'orange_terracotta')
    # the yellow clouds of Other World far below
    for i in range(160):
        x, z = (int(a) for a in rng.uniform(-120, 120, 2))
        y = int(rng.uniform(-90, -60))
        r = rng.uniform(4, 10)
        for dx in range(-int(r), int(r) + 1):
            for dz in range(-int(r), int(r) + 1):
                if dx * dx + dz * dz <= r * r * (1 + n.f2(x + dx, z + dz, 3) * 0.3):
                    v.set(x + dx, y, z + dz, 'yellow_wool' if rng.random() < 0.7 else 'white_wool')
    meta = dict(universo='dragonball', personagem='Goku', lugar='Planeta do Senhor Kaioh', nome='Planeta do Senhor Kaioh',
                canon='O planetinha do Senhor Kaioh no fim do Caminho da Serpente, onde o Goku treinou: gramado curvo com a estradinha que dá a volta no planeta inteiro, a casa redonda, o carro, a árvore, o céu rosado e as nuvens amarelas do Outro Mundo lá embaixo.',
                R=R, altura=60, ceu=['#e48fb6', '#fbd3e2'], neblina=[90, 240],
                jogador=[0, 1, 20], desafiante=[0, 1, -8], camLado=[50, 26, 62], alvoY=-2,
                tints={'grass': (110, 200, 90), 'foliage': (80, 170, 60)})
    return v, meta

@arena('vegeta')
def deserto_dos_saiyajins():
    """The wasteland where Vegeta and Nappa landed and fought Goku: flat dry ground broken by craters, the two
    round Saiyan pods sunk in their impact craters, rock spires and the ruins of the city they blew up."""
    v = Vox(); n = Noise(6501); rng = np.random.default_rng(6501)
    R = 44
    rock = _rock(n)
    craters = [(-14, -20, 9), (20, 8, 7), (-26, 18, 6), (8, -36, 12)]
    def hmap(x, z):
        r = math.hypot(x, z)
        if r > 112:
            return None
        h = n.f2(x, z, 30, 3) * 2
        k = min(1, max(0, (r - R + 4) / 18))
        h += k * max(0, n.f2(x, z, 26, 3) + 0.2) * 22
        for (cx, cz, cr) in craters:
            d = math.hypot(x - cx, z - cz)
            if d < cr * 1.5:
                q = d / cr
                h += (-(1 - q * q) * cr * 0.5) if q < 1 else (1.5 - q) * 3
        return int(round(h))
    def top(x, y, z, h):
        k = n.f2(x * 2, z * 2, 6, 2)
        return 'coarse_dirt' if k > 0 else ('gravel' if k > -0.4 else 'stone')
    H = terrain(v, hmap, -112, 112, -112, 112, top, 'coarse_dirt', lambda x, y, z, h: rock.at(x, y, z), min_depth=3)
    # the pods in the two craters
    for (cx, cz, cr) in craters[:2]:
        b = (H.get((cx, cz)) or 0) + 1
        for dx in range(-3, 4):
            for dy in range(-1, 6):
                for dz in range(-3, 4):
                    if dx * dx + (dy - 2) ** 2 + dz * dz <= 8.5:
                        v.set(cx + dx, b + dy, cz + dz, 'white_concrete' if not (dy == 3 and dz == 3) else 'red_stained_glass')
        v.set(cx, b + 3, cz + 3, 'red_stained_glass'); v.set(cx + 1, b + 3, cz + 3, 'red_stained_glass')
    # rock spires
    for i in range(22):
        a = rng.uniform(0, 2 * math.pi); d = rng.uniform(R, 108)
        x, z = int(math.cos(a) * d), int(math.sin(a) * d)
        hgt = rng.uniform(14, 44); r0 = rng.uniform(2.5, 6)
        base_h = H.get((x, z)) or 0
        for y in range(base_h - 2, base_h + int(hgt)):
            t = (y - base_h) / hgt
            rr = r0 * (1 - 0.55 * t)
            for dx in range(-8, 9):
                for dz in range(-8, 9):
                    if math.hypot(dx, dz) + n.f3(x + dx, y * 0.5, z + dz, 4) * 1.5 <= rr:
                        v.set(x + dx, y, z + dz, rock.at(x + dx, y, z + dz))
    # ruined city blocks far away (East City)
    for i in range(16):
        a = rng.uniform(-0.6, 0.6) - math.pi / 2; d = rng.uniform(80, 106)
        x, z = int(math.cos(a) * d), int(math.sin(a) * d)
        w = int(rng.integers(3, 7)); hh = int(rng.integers(6, 26))
        base_h = H.get((x, z)) or 0
        for dx in range(-w, w + 1):
            for dz in range(-w, w + 1):
                top_y = int(hh * (0.4 + 0.6 * (n.f2(x + dx, z + dz, 3) * 0.5 + 0.5)))
                for y in range(1, top_y):
                    if abs(dx) == w or abs(dz) == w:
                        v.set(x + dx, base_h + y, z + dz, 'light_gray_concrete' if (y % 3) else 'gray_stained_glass')
    plants(v, H, n, (('dead_bush', 0.6),), surface=('coarse_dirt',))
    meta = dict(universo='dragonball', personagem='Vegeta', lugar='Deserto dos Saiyajins', nome='Deserto dos Saiyajins',
                canon='O descampado onde o Vegeta e o Nappa pousaram e lutaram com o Goku: as naves redondas nas crateras, agulhas de pedra e a cidade destruída no horizonte.',
                R=R, altura=60, ceu=['#5d93cf', '#d9d0bd'], neblina=[80, 230],
                jogador=[0, 1, 28], desafiante=[0, 1, -24], camLado=[56, 30, 70], alvoY=6)
    return v, meta
