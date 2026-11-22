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
            if z < -80 and H.get((x, z)) is not None:      # never past the edge of the sand
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
                jogador=[0, 1, 26], desafiante=[0, 1, -24], camLado=[50, 26, 66], alvoY=12, skyLight=15,
                flutua=[(44, 104, -62, 76, 136, -58)])          # the moon in the sky
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

@arena('aizen')
def karakura_falsa():
    """The fake Karakura Town (Stark 2026-11-22), where Aizen fought the Gotei 13: the copy of the town the Kidō Corps
    swapped in, a grid of low Japanese houses, flats and shops round an empty crossroads, half of it sliced clean through
    by blades that came from nowhere; the four pillars that hold the copy in place stand at its corners, and the Garganta
    he came through is still open in the blue sky."""
    from arena_novos import tower
    v = Vox(); n = Noise(7301); rng = np.random.default_rng(7301)
    R = 44
    CLEAR = int(R * 1.5) + 3          # build.py widens the fight circle to 1.5 R: nothing stands inside it
    EDGE = 116
    PILLARS = ((-72, -72), (72, -72), (-72, 72), (72, 72))     # on the crossroads near the corners
    SP, ROAD = 24, 8                     # a block every 24, roads 8 wide (6 of asphalt and a pavement each side)
    asphalt = Pick(n, [('gray_concrete', 9), ('cyan_terracotta', 1), ('light_gray_concrete', 1)], 9)
    walk = Pick(n, [('light_gray_concrete', 5), ('polished_andesite', 2), ('stone_bricks', 1)], 5)

    def cell(c):
        return (c + ROAD // 2) % SP
    # ---- the ground: roads, pavements, white dashes, the plots between them ----
    for x in range(-EDGE, EDGE + 1):
        for z in range(-EDGE, EDGE + 1):
            r = math.hypot(x, z)
            if r > EDGE:
                continue
            mx, mz = cell(x), cell(z)
            road_x, road_z = mx < ROAD, mz < ROAD
            if road_x or road_z:
                pave = (road_x and mx in (0, ROAD - 1)) and not road_z or (road_z and mz in (0, ROAD - 1)) and not road_x
                if pave:
                    b = walk.at(x, 0, z)
                else:
                    b = asphalt.at(x, 0, z)
                    # the dashed centre lines (not in the crossings)
                    if road_x and not road_z and mx == ROAD // 2 and z % 6 < 3:
                        b = 'white_concrete'
                    if road_z and not road_x and mz == ROAD // 2 and x % 6 < 3:
                        b = 'white_concrete'
                    # zebra crossings at the corners of the crossroads
                    if road_x and road_z and (mz in (1, ROAD - 2) or mx in (1, ROAD - 2)) and (x + z) % 2 == 0:
                        b = 'white_concrete'
            else:
                b = 'grass_block' if n.f2(x, z, 9) > 0.25 else 'coarse_dirt' if n.f2(x * 2, z * 2, 4) > 0.55 else 'smooth_stone'
            v.set(x, 0, z, b)
            for y in range(-4, 0):
                v.set(x, y, z, 'stone' if y < -1 else 'dirt')
    # the fight circle: the streets stay, the houses that stood on its plots are gone down to their floors (tiles, boards,
    # a garden here and there), and three blade marks are burnt straight across it
    floors = Pick(n, [('polished_andesite', 3), ('stone_bricks', 3), ('spruce_planks', 2), ('smooth_stone', 2), ('cracked_stone_bricks', 2)], 3)
    marks = [(math.cos(a), math.sin(a), rng.uniform(-30, 30)) for a in (rng.uniform(0, math.pi) for _ in range(3))]
    for x in range(-CLEAR, CLEAR + 1):
        for z in range(-CLEAR, CLEAR + 1):
            d = math.hypot(x, z)
            if d > CLEAR:
                continue
            mx, mz = cell(x), cell(z)
            if not (mx < ROAD or mz < ROAD):
                ox, oz = (x + ROAD // 2) % (SP // 2), (z + ROAD // 2) % (SP // 2)
                if ox == 0 or oz == 0:
                    b = 'stone_bricks'                     # where the walls stood
                elif n.f2(x, z, 6) > 0.35:
                    b = 'grass_block'
                else:
                    b = floors.at(x, 0, z)
                v.set(x, 0, z, b)
            for (cx, cz, off) in marks:
                s = -cz * x + cx * z - off
                if abs(s) < 1.0:
                    v.set(x, 0, z, 'black_concrete' if abs(s) < 0.5 else 'gray_concrete_powder')

    # ---- the blades: long straight cuts through whatever stood in their way (Aizen's, the captains') ----
    cuts = []
    for k in range(5):
        a = rng.uniform(0, math.pi)
        off = rng.uniform(CLEAR + 4, 100) * (1 if rng.random() < 0.5 else -1)
        tilt = rng.uniform(-0.35, 0.35)
        cuts.append((math.cos(a), math.sin(a), off, tilt, int(rng.integers(6, 14))))

    def sliced(x, y, z):
        for (cx, cz, off, tilt, base) in cuts:
            s = -cz * x + cx * z - off
            if abs(s) < 1.6 and y > base + tilt * (cx * x + cz * z) * 0.15:
                return True
        return False

    # ---- the houses: two floors, plastered walls, dark tiled roofs, a balcony; flats and shops on the big roads ----
    WALLS = ['white_terracotta', 'smooth_sandstone', 'white_concrete', 'light_gray_terracotta', 'calcite']
    ROOFS = [('deepslate_tiles', 'deepslate_tile_stairs'), ('blue_terracotta', 'dark_prismarine_stairs'),
             ('gray_terracotta', 'stone_brick_stairs'), ('brown_terracotta', 'spruce_stairs')]

    def house(x0, z0, w, d, floors):
        wall = WALLS[int(rng.integers(0, len(WALLS)))]
        roof, stair = ROOFS[int(rng.integers(0, len(ROOFS)))]
        h = 4 * floors
        x1, z1 = x0 + w - 1, z0 + d - 1
        for y in range(1, h + 1):
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    edge = x in (x0, x1) or z in (z0, z1)
                    if sliced(x, y, z):
                        continue
                    if not edge:
                        if y % 4 == 0:
                            v.set(x, y, z, 'spruce_planks')
                        elif v.get(x, y, z) is None:
                            v.set(x, y, z, 'black_concrete')      # the dark rooms behind the windows
                        continue
                    corner = x in (x0, x1) and z in (z0, z1)
                    along = z if x in (x0, x1) else x
                    if corner:
                        v.set(x, y, z, 'stripped_spruce_log[axis=y]')
                    elif y % 4 == 0:
                        v.set(x, y, z, 'spruce_planks')
                    elif y % 4 in (2, 3) and along % 3 != 0:
                        v.set(x, y, z, 'glass_pane' if y % 4 == 2 else 'white_stained_glass_pane')
                    else:
                        v.set(x, y, z, wall)
        # the hip roof: stairs stepping in on all four sides, tiles on top
        for k in range(0, (min(w, d) + 1) // 2 + 1):
            y = h + 1 + k
            xa, xb, za, zb = x0 - 1 + k, x1 + 1 - k, z0 - 1 + k, z1 + 1 - k
            if xa > xb or za > zb:
                break
            for x in range(xa, xb + 1):
                for z in range(za, zb + 1):
                    if sliced(x, y, z):
                        continue
                    if x in (xa, xb) or z in (za, zb):
                        f = 'south' if z == za else 'north' if z == zb else 'east' if x == xa else 'west'
                        v.set(x, y, z, f'{stair}[facing={f},half=bottom,shape=straight,waterlogged=false]')
                    else:
                        v.set(x, y, z, roof)
        # a door on the side facing the nearest road
        dx = x0 + w // 2
        v.set(dx, 1, z0, 'spruce_door[facing=north,half=lower,hinge=left,open=false,powered=false]')
        v.set(dx, 2, z0, 'spruce_door[facing=north,half=upper,hinge=left,open=false,powered=false]')

    for bx in range(-EDGE, EDGE + 1, SP):
        for bz in range(-EDGE, EDGE + 1, SP):
            # the plot between the roads: from road edge to road edge
            px0 = bx - (bx + ROAD // 2) % SP + ROAD
            pz0 = bz - (bz + ROAD // 2) % SP + ROAD
            size = SP - ROAD
            cx, cz = px0 + size / 2, pz0 + size / 2
            far = math.hypot(cx, cz)
            if far - size * 0.75 <= CLEAR or far + size * 0.75 > EDGE - 2:
                continue
            if any(math.hypot(cx - sx, cz - sz) < size * 0.75 + 10 for (sx, sz) in PILLARS):
                continue
            roll = rng.random()
            if roll < 0.2 and far > 70:
                # a block of flats
                tower(v, px0 + 1, pz0 + 1, size - 2, size - 2, 1, int(rng.integers(14, 26)), rng, n,
                      WALLS[int(rng.integers(0, len(WALLS)))], 'light_gray_concrete', 'gray_stained_glass', 'light_gray_concrete',
                      storey=4, lit=0.15, cut=sliced)
            elif roll < 0.3:
                # an empty lot: a small park, a bench and two trees
                for x in range(px0, px0 + size):
                    for z in range(pz0, pz0 + size):
                        v.set(x, 0, z, 'grass_block')
                for (tx, tz) in ((px0 + 3, pz0 + 3), (px0 + size - 4, pz0 + size - 5)):
                    for y in range(1, 6):
                        v.set(tx, y, tz, 'oak_log[axis=y]')
                    for ddx in range(-2, 3):
                        for ddz in range(-2, 3):
                            for y in (4, 5, 6):
                                if abs(ddx) + abs(ddz) <= (3 if y < 6 else 1) and (ddx, ddz) != (0, 0) or y == 6:
                                    v.set(tx + ddx, y, tz + ddz, 'oak_leaves[distance=1,persistent=true,waterlogged=false]')
                v.set(px0 + size // 2, 1, pz0 + size // 2, 'oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')
                v.set(px0 + size // 2 + 1, 1, pz0 + size // 2, 'oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')
            else:
                # two or four houses on the plot, a narrow gap between them
                half = size // 2
                for (qx, qz) in ((0, 0), (half, 0), (0, half), (half, half)):
                    if rng.random() < 0.15:
                        continue
                    house(px0 + qx + 1, pz0 + qz + 1, half - 2, half - 2, 1 + int(rng.random() < 0.6))

    # ---- utility poles along the pavements (no wires: nothing hangs in the air) ----
    for x in range(-EDGE, EDGE + 1, SP):
        for z in range(-EDGE + 6, EDGE, 24):
            px = x - (x + ROAD // 2) % SP
            if math.hypot(px, z) < CLEAR or any(math.hypot(px - sx, z - sz) < 11 for (sx, sz) in PILLARS) or math.hypot(px, z) > EDGE - 4:
                continue
            if v.get(px, 1, z) is None:
                for y in range(1, 10):
                    v.set(px, y, z, 'stripped_spruce_log[axis=y]')
                v.set(px, 9, z + 1, 'spruce_fence'); v.set(px, 9, z - 1, 'spruce_fence')

    # ---- craters in the streets outside the circle, where the fight already passed ----
    for i in range(7):
        a = rng.uniform(0, 2 * math.pi); d = rng.uniform(CLEAR + 6, 104)
        cx, cz = int(math.cos(a) * d), int(math.sin(a) * d)
        rr = rng.uniform(4, 8)
        for x in range(int(cx - rr - 2), int(cx + rr + 3)):
            for z in range(int(cz - rr - 2), int(cz + rr + 3)):
                q = math.hypot(x - cx, z - cz)
                if q <= rr:
                    depth = int(round((1 - (q / rr) ** 2) * rr * 0.45))
                    for y in range(-depth + 1, 12):
                        v.set(x, y, z, None)
                    v.set(x, -depth, z, 'gravel' if (x + z) % 3 else 'cobblestone')
                elif q <= rr + 1.5 and v.get(x, 0, z) is not None and v.get(x, 1, z) is None:
                    v.set(x, 1, z, 'cobblestone_slab[type=bottom,waterlogged=false]' if (x * z) % 2 else 'andesite')

    # ---- the four pillars at the corners: white columns with dark bands, a ring of seals at the foot ----
    for (sx, sz) in PILLARS:
        for y in range(1, 72):
            rr = 4.6 if y < 4 else 3.6 if y < 68 else 3.6 - (y - 68) * 0.8
            band = y % 12 in (0, 1)
            for ddx in range(-5, 6):
                for ddz in range(-5, 6):
                    q = ddx * ddx + ddz * ddz
                    if q <= rr * rr:
                        b = 'polished_blackstone' if band else ('quartz_pillar[axis=y]' if q < (rr - 1) ** 2 else 'smooth_quartz')
                        v.set(sx + ddx, y, sz + ddz, b)
        for k in range(16):
            a = k / 16 * 2 * math.pi
            v.set(sx + int(round(math.cos(a) * 7)), 1, sz + int(round(math.sin(a) * 7)), 'chiseled_quartz_block')
        v.disc(sx, 0, sz, 8, 'smooth_stone')

    # ---- the Garganta: a black tear in the sky, jagged white teeth along its edges, north over the town ----
    gx, gy, gz = 6, 92, -108
    for dy in range(-26, 27):
        t = 1 - (abs(dy) / 26) ** 1.6
        w = t * 7 + n.f2(dy * 3, 0, 4) * 1.5
        mid = int(round(math.sin(dy * 0.21) * 3))
        for dx in range(-9, 10):
            s = abs(dx - mid)
            if s <= w - 1:
                v.set(gx + dx, gy + dy, gz, 'black_concrete')
            elif s <= w and (dy + dx) % 3 == 0:
                v.set(gx + dx, gy + dy, gz, 'white_concrete')

    meta = dict(universo='bleach', personagem='Aizen', lugar='Karakura falsa', nome='Karakura Falsa',
                canon='A cópia de Karakura que a Divisão Kidō pôs no lugar da cidade de verdade, onde o Aizen lutou contra o Gotei 13: casas baixas, ruas cortadas em linha reta, os quatro pilares nos cantos e a Garganta aberta no céu.',
                R=R, altura=40, ceu=['#6ea6dc', '#d3e2ee'], neblina=[90, 280],
                jogador=[0, 1, 26], desafiante=[0, 1, -24], camLado=[52, 30, 66], alvoY=10,
                flutua=[(gx - 10, gy - 28, gz - 1, gx + 10, gy + 28, gz + 1)])        # the Garganta in the sky
    return v, meta
