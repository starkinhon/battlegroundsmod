"""Arenas of the bosses that came after the first 26 (Stark 2026-11-12: "esqueceu dos mobs novos"): Shigaraki (My Hero
Academia), Joy Boy (One Piece), Makima (Chainsaw Man) and Saitama (One Punch Man). Same rules as the others: the
canon place of the fight, everything procedural, nothing copied twice."""
import math
import numpy as np
from arenas_all import arena
from lib import Vox, Noise, Pick, terrain, seal_fluids

# ---- shared city pieces -----------------------------------------------------------------------------------------------
def tower(v, x0, z0, w, d, y0, h, rng, n, wall, trim, glass, roof, storey=4, lit=0.0, cut=None, backing='black_concrete'):
    """A city building: facade with a window grid (glass with a dark room behind, some rooms lit), corner trim, slab
    lines, flat roof with a parapet. cut(x, y, z) -> True removes a block (ruin, decay, impact holes)."""
    x1, z1 = x0 + w - 1, z0 + d - 1
    top = y0 + h
    lit_rooms = {}
    def keep(x, y, z):
        return cut is None or not cut(x, y, z)
    for y in range(y0, top + 1):
        fy = (y - y0) % storey
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                if not edge:
                    if y == top or (fy == 0 and y > y0):
                        if keep(x, y, z):
                            v.set(x, y, z, roof if y == top else wall)
                    continue
                if not keep(x, y, z):
                    continue
                corner = x in (x0, x1) and z in (z0, z1)
                along = z if x in (x0, x1) else x
                if corner:
                    v.set(x, y, z, trim)
                elif y == top or fy == 0 or y == y0:
                    v.set(x, y, z, trim if y == top else wall)
                elif along % 3 != 0 and fy in (1, 2) and y > y0 + 1:
                    v.set(x, y, z, glass)
                    # the room behind the window: dark, or lit (offices with the light on)
                    ix = x + (1 if x == x0 else -1 if x == x1 else 0)
                    iz = z + (1 if z == z0 else -1 if z == z1 else 0)
                    key = (along // 6, y // storey, x in (x0, x1))
                    if key not in lit_rooms:
                        lit_rooms[key] = rng.random() < lit
                    if (ix, iz) != (x, z) and keep(ix, y, iz) and v.get(ix, y, iz) is None:
                        v.set(ix, y, iz, 'ochre_froglight[axis=y]' if lit_rooms[key] else backing)
                else:
                    v.set(x, y, z, wall)
    # parapet
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x in (x0, x1) or z in (z0, z1)) and keep(x, top + 1, z):
                v.set(x, top + 1, z, trim)
    return top

def lots(rng, ext, spacing, street, skip):
    """City blocks between streets: yields (x0, z0, w, d) footprints, one or two buildings per block."""
    out = []
    g = int(ext // spacing) + 1
    for gx in range(-g, g):
        for gz in range(-g, g):
            bx0 = gx * spacing + street // 2 + 1
            bz0 = gz * spacing + street // 2 + 1
            inner = spacing - street - 2
            if rng.random() < 0.5:
                parts = [(bx0, bz0, inner, inner)]
            else:
                cut = int(rng.integers(inner // 3, inner * 2 // 3))
                if rng.random() < 0.5:
                    parts = [(bx0, bz0, cut - 1, inner), (bx0 + cut + 1, bz0, inner - cut - 1, inner)]
                else:
                    parts = [(bx0, bz0, inner, cut - 1), (bx0, bz0 + cut + 1, inner, inner - cut - 1)]
            for (x0, z0, w, d) in parts:
                # shrink a little so neighbours never share a wall
                sw, sd = int(rng.integers(0, 3)), int(rng.integers(0, 3))
                x0 += sw; z0 += sd; w -= sw + int(rng.integers(0, 2)); d -= sd + int(rng.integers(0, 2))
                if w < 5 or d < 5:
                    continue
                cx, cz = x0 + w / 2, z0 + d / 2
                if math.hypot(cx, cz) > ext or skip(x0, z0, w, d):
                    continue
                out.append((x0, z0, w, d))
    return out

def overlaps_disc(x0, z0, w, d, r):
    nx = min(max(0, x0), x0 + w - 1); nz = min(max(0, z0), z0 + d - 1)
    return nx * nx + nz * nz < r * r

WALLS = [('light_gray_concrete', 'gray_concrete'), ('white_terracotta', 'light_gray_terracotta'),
         ('smooth_sandstone', 'cut_sandstone'), ('polished_diorite', 'stone_bricks'), ('quartz_bricks', 'smooth_quartz'),
         ('packed_mud', 'mud_bricks'), ('polished_andesite', 'andesite'), ('bricks', 'stone_bricks'),
         ('light_blue_terracotta', 'light_gray_concrete'), ('terracotta', 'brown_terracotta')]
GLASS = ['light_blue_stained_glass', 'gray_stained_glass', 'light_gray_stained_glass', 'cyan_stained_glass', 'glass']

# ---- Shigaraki -------------------------------------------------------------------------------------------------------
@arena('shigaraki')
def cidade_jaku():
    """Jaku City after Shigaraki's Decay: from where his hand touched the ground everything turned to dust, the streets
    gone, cracks running out of the middle; the buildings get taller and more whole the farther they are, and the ones
    in between are melting into piles of dust."""
    v = Vox(); n = Noise(9101); rng = np.random.default_rng(9101)
    R, EXT = 36, 150
    dust = Pick(n, [('light_gray_concrete_powder', 4), ('gravel', 3), ('tuff', 2), ('gray_concrete_powder', 2),
                    ('andesite', 1), ('dead_brain_coral_block', 1)], 7)
    SP, ST = 34, 10
    def street(x, z):
        mx, mz = (x + ST // 2) % SP, (z + ST // 2) % SP
        return mx < ST or mz < ST
    def hmap(x, z):
        r = math.hypot(x, z)
        if r > EXT:
            return None
        bowl = 3.2 * math.exp(-(r / 22) ** 2)
        return int(round(n.f2(x, z, 38, 3) * 1.6 - bowl + 0.4 * n.f2(x, z, 6, 2)))
    def top(x, y, z, h):
        r = math.hypot(x, z)
        k = min(1.0, max(0.0, (r - 70) / 60))
        if street(x, z) and n.f2(x, z, 9, 3) + k * 0.9 > 0.35:
            mx = (x + ST // 2) % SP; mz = (z + ST // 2) % SP
            if (mx == ST // 2 or mz == ST // 2) and (x + z) % 5 < 3 and n.f2(x, z, 4) > -0.1:
                return 'white_concrete'
            return 'gray_concrete' if n.f2(x, z, 3) > -0.3 else 'cracked_stone_bricks'
        if not street(x, z) and k > 0.4 and n.f2(x, z, 5) > 0.1:
            return 'polished_andesite'
        return dust.at(x, y, z)
    H = terrain(v, hmap, -EXT, EXT, -EXT, EXT, top, lambda x, y, z, h: dust.at(x, y, z, 0.4), 'stone', min_depth=3)

    # the cracks the Decay left running out from the middle
    for i in range(11):
        a = i / 11 * 2 * math.pi + rng.uniform(-0.2, 0.2)
        x, z = rng.uniform(-3, 3), rng.uniform(-3, 3)
        L = rng.uniform(50, 120); w = 1.6
        for s in range(int(L)):
            a += rng.normal(0, 0.18)
            x += math.cos(a); z += math.sin(a)
            ww = w * (1 - s / L) + 0.4
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    if dx * dx + dz * dz <= ww * ww:
                        cx, cz = int(round(x)) + dx, int(round(z)) + dz
                        h = H.get((cx, cz))
                        if h is None:
                            continue
                        depth = 2 if ww > 1.0 else 1
                        for y in range(h - depth + 1, h + 1):
                            v.b.pop((cx, y, cz), None)
                        v.set(cx, h - depth, cz, 'blackstone' if (cx + cz) % 3 else 'black_concrete_powder')
            if rng.random() < 0.06:
                a += rng.choice([-0.9, 0.9])

    # buildings: dust in the middle, stumps melting into dust piles, whole towers far away
    def skip(x0, z0, w, d):
        return overlaps_disc(x0, z0, w, d, R + 14)
    for (x0, z0, w, d) in lots(rng, EXT - 6, SP, ST, skip):
        cx, cz = x0 + w / 2, z0 + d / 2
        r = math.hypot(cx, cz)
        k = min(1.0, max(0.0, (r - 50) / 85)) ** 1.3          # 0 = all dust, 1 = whole
        if k < 0.05:
            continue
        h = int(rng.integers(14, 52))
        y0 = max(H.get((int(cx), int(cz))) or 0, 0)
        wall, trim = WALLS[int(rng.integers(len(WALLS)))]
        glass = GLASS[int(rng.integers(len(GLASS)))]
        seed = int(rng.integers(1 << 20))
        def cut(x, y, z, k=k, h=h, y0=y0, seed=seed):
            # the side facing the centre goes first, the top crumbles in ragged steps
            facing = ((x * cx + z * cz) / max(1.0, r) - (r - w / 2)) / max(1.0, w)   # 0 near side .. 1 far side
            cap = y0 + h * min(1.0, k * 1.25 + facing * 0.35) + n.f2(x + seed, z, 5, 3) * 9 * (1 - k)
            if y > cap:
                return True
            hole = n.f3(x + seed, y, z, 6, 2)
            return hole > 0.2 + k * 0.6
        tower(v, x0, z0, w, d, y0, h, rng, n, wall, trim, glass, wall, cut=cut)
        # dust piled against the building, deeper on the side the wave came from
        if k < 0.92:
            pile = (1 - k) * 7
            for x in range(x0 - 6, x0 + w + 6):
                for z in range(z0 - 6, z0 + d + 6):
                    if x0 <= x < x0 + w and z0 <= z < z0 + d:
                        continue
                    dd = max(x0 - x, x - (x0 + w - 1), z0 - z, z - (z0 + d - 1))
                    towards = ((cx - x) * cx + (cz - z) * cz) / max(1.0, r * r) * 3
                    hh = int(pile * (1 - dd / 6.5) * (0.5 + max(0, towards)) + n.f2(x, z, 3) * 1.2)
                    g = H.get((x, z))
                    if g is None:
                        continue
                    for y in range(g + 1, g + hh + 1):
                        v.set(x, y, z, dust.at(x, y, z, 0.5))

    # inside the fight: what's left of a few walls and some mounds of dust to hide behind
    for i in range(7):
        a = rng.uniform(0, 2 * math.pi); r = rng.uniform(12, R - 6)
        x0, z0 = int(math.cos(a) * r), int(math.sin(a) * r)
        if math.hypot(x0, z0 - 22) < 8 or math.hypot(x0, z0 + 20) < 8:
            continue
        g = H.get((x0, z0), 0)
        if i % 2:
            L = int(rng.integers(5, 10)); ax = rng.random() < 0.5
            wall = WALLS[int(rng.integers(len(WALLS)))][0]
            for s in range(L):
                hh = int(1 + (1 - abs(s - L / 2) / (L / 2)) * rng.uniform(2, 5))
                for y in range(1, hh + 1):
                    x, z = (x0 + s, z0) if ax else (x0, z0 + s)
                    if n.f3(x, y, z, 3) < 0.35:
                        v.set(x, H.get((x, z), g) + y, z, wall)
                if rng.random() < 0.3:
                    x, z = (x0 + s, z0) if ax else (x0, z0 + s)
                    v.set(x, H.get((x, z), g) + hh + 1, z, 'iron_bars')
        else:
            rr = rng.uniform(2.5, 4.5)
            for dx in range(-5, 6):
                for dz in range(-5, 6):
                    d = math.hypot(dx, dz)
                    hh = int((rr - d) * 0.8 + n.f2(x0 + dx, z0 + dz, 2) * 0.8)
                    gg = H.get((x0 + dx, z0 + dz))
                    if gg is None:
                        continue
                    for y in range(1, hh + 1):
                        v.set(x0 + dx, gg + y, z0 + dz, dust.at(x0 + dx, y, z0 + dz, 0.4))
    # bent lamp posts and a couple of half-eaten cars
    for i in range(22):
        a = rng.uniform(0, 2 * math.pi); r = rng.uniform(R + 3, 110)
        x, z = int(math.cos(a) * r), int(math.sin(a) * r)
        g = H.get((x, z))
        if g is None or v.get(x, g + 1, z):
            continue
        if i % 3:
            hh = int(rng.integers(2, 7)); lean = rng.choice([(1, 0), (-1, 0), (0, 1), (0, -1)])
            for y in range(1, hh + 1):
                o = (y * y) // 12
                v.set(x + lean[0] * o, g + y, z + lean[1] * o, 'andesite_wall')
        else:
            col = ['red_concrete', 'white_concrete', 'blue_concrete', 'black_concrete'][i % 4]
            for dx in range(0, 4):
                for dz in range(0, 2):
                    if n.f3(x + dx, 0, z + dz, 2) < 0.3:
                        v.set(x + dx, g + 1, z + dz, col)
                    if 0 < dx < 3 and n.f3(x + dx, 1, z + dz, 2) < 0.1:
                        v.set(x + dx, g + 2, z + dz, 'gray_stained_glass' if dz == 0 else col)
    meta = dict(universo='mha', personagem='Shigaraki', lugar='Cidade Jaku', nome='Cidade Jaku em pó',
                canon='Cidade Jaku depois da Decadência: onde a mão do Shigaraki tocou o chão tudo virou pó, as rachaduras correm do meio pra fora e os prédios vão desmanchando até ficarem inteiros lá longe.',
                R=R, altura=40, ceu=['#3a3432', '#a39283'], neblina=[40, 170],
                jogador=[0, 1, 22], desafiante=[0, 1, -20], camLado=[40, 30, 58], alvoY=0)
    return v, meta

# ---- Joy Boy ----------------------------------------------------------------------------------------------------------
def _np_sculpt(v, box, sdf, block, shell=2.5):
    """Voxelize a numpy SDF (vectorised over the whole box) as a shell, for the very large shapes."""
    (x0, y0, z0), (x1, y1, z1) = box
    xs = np.arange(x0, x1 + 1) + 0.5; ys = np.arange(y0, y1 + 1) + 0.5; zs = np.arange(z0, z1 + 1) + 0.5
    X, Y, Z = np.meshgrid(xs, ys, zs, indexing='ij')
    d = sdf(X, Y, Z)
    m = (d <= 0) & (d > -shell)
    for ix, iy, iz in zip(*np.nonzero(m)):
        x, y, z = int(xs[ix] - 0.5), int(ys[iy] - 0.5), int(zs[iz] - 0.5)
        if v.get(x, y, z) is None:
            v.set(x, y, z, block(x, y, z) if callable(block) else block)

def _seg(X, Y, Z, a, b, ra, rb):
    pa = np.stack([X - a[0], Y - a[1], Z - a[2]]); ba = np.array(b, float) - np.array(a, float)
    h = np.clip((pa[0] * ba[0] + pa[1] * ba[1] + pa[2] * ba[2]) / (ba @ ba), 0, 1)
    d = np.sqrt((pa[0] - ba[0] * h) ** 2 + (pa[1] - ba[1] * h) ** 2 + (pa[2] - ba[2] * h) ** 2)
    return d - (ra + (rb - ra) * h)

@arena('joy_boy')
def telhado_de_onigashima():
    """The roof of the Skull Dome on Onigashima at dawn, where Luffy in Gear 5 beat Kaido: the wide tiled roof, eaves
    turned up at the corners, the castle burning in tiers below, the two great horns of the skull rising either side
    and the sea far down."""
    v = Vox(); n = Noise(9201); rng = np.random.default_rng(9201)
    R = 34
    AX, AZ = 46, 40                       # half size of the top roof
    tiles = Pick(n, [('deepslate_tiles', 6), ('polished_blackstone_bricks', 3), ('cracked_deepslate_tiles', 2),
                     ('deepslate_bricks', 1)], 6)
    def sq(x, z, ax, az):
        """Superellipse distance: < 1 inside."""
        return (abs(x) / ax) ** 4 + (abs(z) / az) ** 4
    # top roof: tile courses running along x, a little wavy from the fight
    for x in range(-AX - 1, AX + 2):
        for z in range(-AZ - 1, AZ + 2):
            q = sq(x, z, AX, AZ)
            if q > 1:
                continue
            b = 'polished_deepslate' if z % 5 == 0 else tiles.at(x, 0, z)
            if math.hypot(x, z) < 26 and n.f2(x, z, 7, 3) > 0.42:
                b = 'cracked_deepslate_tiles'
            v.set(x, 0, z, b)
            v.set(x, -1, z, 'dark_oak_planks')
    # broken spots: craters from Kaido's club and Luffy's punches
    for i in range(5):
        a = rng.uniform(0, 2 * math.pi); r = rng.uniform(8, R - 6)
        cx, cz = math.cos(a) * r, math.sin(a) * r; rr = rng.uniform(2.5, 4.5)
        if abs(cz - 22) < 6 and abs(cx) < 6:
            continue
        for x in range(int(cx - rr - 2), int(cx + rr + 3)):
            for z in range(int(cz - rr - 2), int(cz + rr + 3)):
                d = math.hypot(x - cx, z - cz)
                if d < rr * 0.6:
                    v.set(x, 0, z, 'dark_oak_slab[type=top]' if (x + z) % 2 else 'stripped_dark_oak_log[axis=x]')
                elif d < rr:
                    v.set(x, 0, z, 'cracked_deepslate_tiles' if (x * 7 + z) % 3 else 'deepslate_tile_slab[type=bottom]')
                elif d < rr + 1.5 and rng.random() < 0.3:
                    v.set(x, 1, z, 'deepslate_tile_slab[type=bottom]')
    # eaves sloping down and turning up at the corners
    def facing(x, z):
        if abs(x) / AX > abs(z) / AZ:
            return 'west' if x > 0 else 'east'
        return 'north' if z > 0 else 'south'
    def skirt(ax, az, y_top, out, wall_h, wall_y, curl):
        for x in range(-ax - out - 2, ax + out + 3):
            for z in range(-az - out - 2, az + out + 3):
                q = sq(x, z, ax, az)
                if q <= 1:
                    continue
                # distance outward from the roof edge, in blocks
                s = q ** 0.25
                d = (s - 1) * min(ax, az)
                if d > out:
                    continue
                corner = (abs(x) / ax) ** 2 * (abs(z) / az) ** 2      # 0 on the sides, ~1 at the corners
                y = y_top - int(d * 0.75) + int(curl * corner * (d / out) ** 2 * 2.2)
                v.set(x, y, z, 'deepslate_tile_stairs[facing=%s,half=bottom,shape=straight]' % facing(x, z))
                v.set(x, y - 1, z, 'dark_oak_planks')
                if d > out - 1.2:
                    v.set(x, y, z, 'gold_block' if corner > 0.8 else 'polished_blackstone')
                    if corner > 0.8:
                        v.set(x, y + 1, z, 'gold_block')
        # walls under the eaves: red lacquered posts, white plaster, dark beams
        for x in range(-ax, ax + 1):
            for z in range(-az, az + 1):
                q = sq(x, z, ax - 1.5, az - 1.5)
                if q <= 1 or sq(x, z, ax, az) > 1:
                    continue
                along = x if abs(z) / az > abs(x) / ax else z
                for y in range(wall_y - wall_h, wall_y + 1):
                    if along % 7 == 0:
                        b = 'red_terracotta'
                    elif y == wall_y or y == wall_y - wall_h or (y - wall_y) % 5 == 0:
                        b = 'dark_oak_planks'
                    elif along % 7 in (2, 3, 4) and (y - wall_y) % 5 in (-2, -3, 2, 3):
                        b = 'ochre_froglight[axis=y]' if n.f3(x, y, z, 4) > 0.15 else 'black_stained_glass'
                    else:
                        b = 'white_terracotta'
                    v.set(x, y, z, b)
    skirt(AX, AZ, -1, 7, 8, -2, 3)
    # lower tiers: wider, burning
    tiers = [(AX + 14, AZ + 12, -12, 8, 9, -13), (AX + 28, AZ + 24, -24, 9, 10, -25)]
    for (ax, az, yt, out, wh, wy) in tiers:
        skirt(ax, az, yt, out, wh, wy, 4)
        # the roof between this tier and the one above
        for x in range(-ax, ax + 1):
            for z in range(-az, az + 1):
                if sq(x, z, ax, az) <= 1 and v.get(x, yt, z) is None and sq(x, z, ax - 16, az - 14) > 1:
                    v.set(x, yt, z, tiles.at(x, yt, z))
    for i in range(40):
        a = rng.uniform(0, 2 * math.pi)
        for (ax, az, yt, *_r) in tiers:
            x = int(math.cos(a) * (ax - 6)); z = int(math.sin(a) * (az - 5))
            if v.get(x, yt, z) and v.get(x, yt + 1, z) is None and rng.random() < 0.5:
                v.set(x, yt, z, 'netherrack')
                v.set(x, yt + 1, z, 'fire[age=0,east=false,north=false,south=false,up=false,west=false]')
            if rng.random() < 0.3 and v.get(x, yt + 1, z) is None:
                v.set(x, yt + 1, z, 'lantern[hanging=false]')
    # the skull rock under the castle and the two horns
    bone = Pick(n, [('calcite', 5), ('bone_block[axis=y]', 3), ('diorite', 2), ('smooth_basalt', 1)], 9)
    def rock(X, Y, Z):
        brow = np.sqrt((X / 120.0) ** 2 + ((Y + 70) / 46.0) ** 2 + ((Z + 6) / 110.0) ** 2) - 1
        brow = brow * 40
        return np.maximum(brow, Y + 40)
    _np_sculpt(v, ((-130, -80, -122), (130, -39, 112)), rock, lambda x, y, z: bone.at(x, y, z), shell=3)
    def horns(X, Y, Z):
        d = None
        for sgn in (-1, 1):
            pts = [(sgn * 52, -50, -20), (sgn * 82, -12, -28), (sgn * 96, 22, -32), (sgn * 90, 52, -28),
                   (sgn * 74, 74, -20), (sgn * 56, 86, -12)]
            rs = [17, 13, 10, 7, 4, 1.0]
            for i in range(len(pts) - 1):
                s = _seg(X, Y, Z, pts[i], pts[i + 1], rs[i], rs[i + 1])
                d = s if d is None else np.minimum(d, s)
        return d
    hornp = Pick(n, [('bone_block[axis=y]', 4), ('calcite', 3), ('diorite', 1), ('polished_diorite', 1)], 7)
    def horn_block(x, y, z):
        # weathered grooves curling round the horn, not stripes
        g = (y * 0.5 + math.atan2(z + 28, abs(x) - 84) * 3 + n.f3(x, y, z, 9) * 4) % 7
        return 'smooth_basalt' if g < 0.6 and n.f3(x, y, z, 5) > 0 else hornp.at(x, y, z)
    for sgn in (-1, 1):
        lo, hi = (30, 116) if sgn > 0 else (-116, -30)
        _np_sculpt(v, ((lo, -70, -60), (hi, 92, 10)), horns, horn_block, shell=3)
    # sea all round, far below
    for x in range(-170, 171):
        for z in range(-170, 171):
            r = math.hypot(x, z)
            if r > 170 or v.get(x, -72, z):
                continue
            v.set(x, -72, z, 'water')
    seal_fluids(v, 'stone')
    meta = dict(universo='onepiece', personagem='Joy Boy', lugar='Onigashima', nome='Telhado do Domo da Caveira',
                canon='O telhado do Domo da Caveira em Onigashima, ao amanhecer, onde o Luffy de Gear 5 derrubou o Kaido: as telhas largas, os beirais virados pra cima, o castelo pegando fogo em andares lá embaixo, os chifres da caveira dos dois lados e o mar.',
                R=R, altura=60, ceu=['#272250', '#f2a066'], neblina=[90, 280],
                jogador=[0, 1, 22], desafiante=[0, 1, -20], camLado=[64, 44, 92], alvoY=0)
    return v, meta

# ---- Makima -----------------------------------------------------------------------------------------------------------
@arena('makima')
def telhado_da_seguranca_publica():
    """The roof of the Public Safety headquarters in Tokyo at sunset: helipad, water tank, the stair house, antennas,
    the city of low roofs below and Tokyo Tower on the horizon."""
    v = Vox(); n = Noise(9301); rng = np.random.default_rng(9301)
    R, HALF, BASE = 30, 38, -50
    floor = Pick(n, [('light_gray_concrete', 6), ('smooth_stone', 3), ('polished_andesite', 2), ('light_gray_concrete_powder', 1)], 5)
    # the HQ: facade with long bands of windows, setbacks
    tower(v, -HALF, -HALF, 2 * HALF + 1, 2 * HALF + 1, BASE, -BASE - 1, rng, n, 'smooth_quartz', 'light_gray_concrete',
          'gray_stained_glass', 'light_gray_concrete', storey=4, lit=0.35)
    for x in range(-HALF, HALF + 1):
        for z in range(-HALF, HALF + 1):
            v.set(x, 0, z, floor.at(x, 0, z))
            v.set(x, -1, z, 'light_gray_concrete')
    for x in range(-HALF, HALF + 1):
        for z in range(-HALF, HALF + 1):
            if x in (-HALF, HALF) or z in (-HALF, HALF):
                v.set(x, 1, z, 'light_gray_concrete')
                v.set(x, 2, z, 'iron_bars')
    # helipad
    for x in range(-12, 13):
        for z in range(-12, 13):
            d = math.hypot(x, z)
            if 10.2 <= d < 11.5:
                v.set(x, 0, z, 'yellow_concrete')
            elif d < 10.2:
                v.set(x, 0, z, 'gray_concrete')
    for z in range(-5, 6):
        v.set(-3, 0, z, 'white_concrete'); v.set(-4, 0, z, 'white_concrete')
        v.set(3, 0, z, 'white_concrete'); v.set(4, 0, z, 'white_concrete')
    for x in range(-2, 3):
        v.set(x, 0, 0, 'white_concrete'); v.set(x, 0, 1, 'white_concrete')
    # stair house with a door and a light
    v.box(-36, 1, -36, -28, 6, -30, 'light_gray_concrete')
    v.box(-35, 1, -35, -29, 5, -31, 'air')
    v.box(-31, 1, -30, -30, 3, -30, 'air')
    v.set(-31, 1, -30, 'iron_door[facing=south,half=lower,hinge=left,open=false,powered=false]')
    v.set(-31, 2, -30, 'iron_door[facing=south,half=upper,hinge=left,open=false,powered=false]')
    v.set(-30, 1, -30, 'iron_door[facing=south,half=lower,hinge=right,open=false,powered=false]')
    v.set(-30, 2, -30, 'iron_door[facing=south,half=upper,hinge=right,open=false,powered=false]')
    v.set(-32, 4, -29, 'sea_lantern')
    v.box(-36, 7, -36, -28, 7, -30, 'smooth_stone_slab[type=bottom]')
    # water tank on legs
    for (lx, lz) in ((28, -34), (34, -34), (28, -28), (34, -28)):
        for y in range(1, 6):
            v.set(lx, y, lz, 'iron_bars')
    v.cylinder(31, 6, -31, 5.2, 12, 'light_blue_terracotta', hollow=False)
    v.disc(31, 13, -31, 4.6, 'light_gray_concrete')
    for y in range(6, 13):
        v.set(31, y, -36, 'iron_bars' if y % 2 else 'chain[axis=y]')
    # AC units and ducts along the east edge
    for i, z in enumerate(range(-16, 30, 6)):
        x0 = 32 + int(rng.integers(0, 2))
        v.box(x0, 1, z, x0 + 3, 2, z + 3, 'white_concrete')
        v.set(x0 + 1, 3, z + 1, 'iron_trapdoor[facing=north,half=bottom,open=false]')
        v.set(x0 + 2, 3, z + 2, 'iron_trapdoor[facing=north,half=bottom,open=false]')
    for x in range(-24, 30):
        v.set(x, 1, 36, 'polished_andesite_slab[type=bottom]')
    # antenna mast, red light on top
    for y in range(1, 22):
        v.set(-32, y, 32, 'iron_bars' if y % 4 else 'iron_block')
    for d in (-1, 1):
        for y in (8, 14):
            v.set(-32 + d, y, 32, 'iron_bars'); v.set(-32, y, 32 + d, 'iron_bars')
    v.set(-32, 22, 32, 'redstone_lamp[lit=true]')
    # the city below: low roofs, streets, a few towers
    SP, ST = 26, 8
    for x in range(-180, 181):
        for z in range(-180, 181):
            if abs(x) <= HALF and abs(z) <= HALF or x * x + z * z > 180 * 180:
                continue
            mx, mz = (x + ST // 2) % SP, (z + ST // 2) % SP
            if mx < ST or mz < ST:
                b = 'gray_concrete' if (mx not in (0, ST - 1) and mz not in (0, ST - 1)) else 'polished_andesite'
                if (mx == ST // 2 and mz >= ST or mz == ST // 2 and mx >= ST) and (x + z) % 4 < 2:
                    b = 'white_concrete'
            else:
                b = 'polished_andesite'
            v.set(x, BASE, z, b)
    roofs = ['gray_concrete', 'light_gray_concrete', 'cyan_terracotta', 'brown_terracotta', 'green_terracotta',
             'blue_terracotta', 'polished_andesite', 'smooth_stone']
    def skip(x0, z0, w, d):
        return not (x0 > HALF + 2 or x0 + w < -HALF - 2 or z0 > HALF + 2 or z0 + d < -HALF - 2)
    for (x0, z0, w, d) in lots(rng, 172, SP, ST, skip):
        r = math.hypot(x0 + w / 2, z0 + d / 2)
        tall = rng.random() < (0.08 if r < 90 else 0.2)
        h = int(rng.integers(30, 70)) if tall else int(rng.integers(6, 26))
        wall, trim = WALLS[int(rng.integers(len(WALLS)))]
        tower(v, x0, z0, w, d, BASE + 1, h, rng, n, wall, trim, GLASS[int(rng.integers(len(GLASS)))],
              roofs[int(rng.integers(len(roofs)))], lit=0.25)
    # Tokyo Tower on the horizon: orange and white bands, lattice legs, two observation decks
    TX, TZ, TH = -96, -150, 132
    for y in range(0, TH):
        t = y / TH
        half = 15 * (1 - t) ** 1.6 + 1.2
        hi = int(round(half))
        band = 'orange_concrete' if (y // 11) % 2 == 0 else 'white_concrete'
        for x in range(-hi, hi + 1):
            for z in range(-hi, hi + 1):
                on_edge = abs(x) == hi or abs(z) == hi
                if not on_edge:
                    continue
                corner = abs(x) == hi and abs(z) == hi
                brace = (abs(x) + y) % 6 == 0 or (abs(z) + y) % 6 == 0 or (abs(x) - y) % 6 == 0 or (abs(z) - y) % 6 == 0
                if corner or (brace and y > 4) or (y < 40 and abs(abs(x) - hi) + abs(abs(z) - hi) < 3 and hi > 6):
                    v.set(TX + x, BASE + 1 + y, TZ + z, band)
        if y in (44, 45, 46, 47, 82, 83, 84):
            dh = int(half + (3 if y < 60 else 2))
            for x in range(-dh, dh + 1):
                for z in range(-dh, dh + 1):
                    if abs(x) == dh or abs(z) == dh:
                        v.set(TX + x, BASE + 1 + y, TZ + z, 'light_blue_stained_glass' if y not in (44, 82) else 'white_concrete')
                    elif y in (44, 82):
                        v.set(TX + x, BASE + 1 + y, TZ + z, 'white_concrete')
    for y in range(TH, TH + 16):
        v.set(TX, BASE + 1 + y, TZ, 'white_concrete' if y % 4 else 'orange_concrete')
    v.set(TX, BASE + 1 + TH + 16, TZ, 'redstone_lamp[lit=true]')
    meta = dict(universo='chainsawman', personagem='Makima', lugar='Tóquio', nome='Telhado da Segurança Pública',
                canon='O telhado do prédio da Segurança Pública em Tóquio, no fim da tarde: o heliponto, a caixa d\'água, a casinha da escada, as antenas, a cidade de telhados baixos lá embaixo e a Torre de Tóquio no horizonte.',
                R=R, altura=30, ceu=['#2c2148', '#f1874d'], neblina=[100, 320],
                jogador=[0, 1, 20], desafiante=[0, 1, -18], camLado=[60, 30, 78], alvoY=0)
    return v, meta

# ---- Saitama ----------------------------------------------------------------------------------------------------------
@arena('saitama')
def cidade_z():
    """City Z, the ghost town where Saitama lives, after the meteor: the crossing of two empty avenues with a crater
    where a piece of the meteor fell, smaller holes around, buildings punched open by the fragments, grass growing in
    the cracks, and his apartment block with the outside corridors still standing."""
    v = Vox(); n = Noise(9401); rng = np.random.default_rng(9401)
    R, EXT = 36, 150
    AV = 9                                   # avenue half width
    craters = [((4.0, -5.0), 12.0, 5.0)]
    for i in range(9):
        a = rng.uniform(0, 2 * math.pi); r = rng.uniform(R - 4, 120)
        craters.append(((math.cos(a) * r, math.sin(a) * r), rng.uniform(3, 7), rng.uniform(1.5, 3)))
    def crater_h(x, z):
        h = 0.0
        for (cx, cz), rr, dp in craters:
            d = math.hypot(x - cx, z - cz) / rr
            if d < 1:
                h = min(h, -dp * (1 - d * d))
            elif d < 1.35:
                h = max(h, (1.35 - d) / 0.35 * dp * 0.3) if h >= 0 else h
        return h
    def road(x, z):
        return abs(x) <= AV or abs(z) <= AV
    def side(x, z):
        return abs(x) <= AV + 4 or abs(z) <= AV + 4
    def hmap(x, z):
        if math.hypot(x, z) > EXT:
            return None
        return int(round(crater_h(x, z) + (n.f2(x, z, 3) * 0.6 if crater_h(x, z) != 0 else 0)))
    asphalt = Pick(n, [('gray_concrete', 7), ('gray_concrete_powder', 2), ('tuff', 1), ('andesite', 1)], 9)
    def top(x, y, z, h):
        c = crater_h(x, z)
        if c < -0.5:
            return 'blackstone' if c < -2.5 and n.f2(x, z, 2) > 0.2 else Pick.at(rubble, x, y, z)
        if c > 0.2:
            return rubble.at(x, y, z)
        if road(x, z):
            if (x == 0 or z == 0) and (x + z) % 6 < 3 and not (abs(x) <= AV and abs(z) <= AV):
                return 'yellow_concrete'
            if abs(x) <= AV and abs(z) <= AV and (abs(x) == AV - 1 or abs(z) == AV - 1) and (x + z) % 2 == 0:
                return 'white_concrete'
            if (AV + 1 < abs(z) < AV + 6 and abs(x) <= AV and x % 3 != 0) or (AV + 1 < abs(x) < AV + 6 and abs(z) <= AV and z % 3 != 0):
                return 'white_concrete'      # zebra crossings
            return asphalt.at(x, y, z)
        if side(x, z):
            return 'smooth_stone' if (x + z) % 4 else 'polished_andesite'
        return 'polished_andesite' if n.f2(x, z, 6) > -0.2 else 'gravel'
    rubble = Pick(n, [('gravel', 3), ('cobblestone', 2), ('andesite', 2), ('gray_concrete', 1), ('tuff', 1)], 3)
    H = terrain(v, hmap, -EXT, EXT, -EXT, EXT, top, 'stone', 'stone', min_depth=3)
    # the meteor fragment in the big crater
    (mx, mz), _, _ = craters[0]
    for dx in range(-4, 5):
        for dy in range(-2, 5):
            for dz in range(-4, 5):
                if math.sqrt(dx * dx + (dy * 1.2) ** 2 + dz * dz) < 3.2 + n.f3(dx, dy, dz, 2) * 1.2:
                    x, z = int(mx) + dx, int(mz) + dz
                    v.set(x, H.get((x, z), -4) + dy, z, 'magma_block' if n.f3(x, dy, z, 1.5) > 0.35 else 'blackstone' if dy % 2 else 'basalt[axis=y]')
    # grass and moss in the cracks: nobody has lived here for years
    for x in range(-EXT, EXT + 1):
        for z in range(-EXT, EXT + 1):
            h = H.get((x, z))
            if h is None or v.get(x, h + 1, z):
                continue
            s = n.f2(x * 3, z * 3, 5, 2)
            if s > 0.45 and not (abs(x) < AV and abs(z) < AV and abs(crater_h(x, z)) < 0.1 and s < 0.55):
                v.set(x, h + 1, z, 'moss_carpet' if s > 0.55 else 'short_grass')
                if v.get(x, h, z) in ('minecraft:gray_concrete',):
                    v.set(x, h, z, 'moss_block')
    # buildings, punched open by the fragments
    holes = []
    for i in range(26):
        a = rng.uniform(0, 2 * math.pi); r = rng.uniform(R + 8, 120)
        holes.append((math.cos(a) * r, rng.uniform(4, 30), math.sin(a) * r, rng.uniform(3, 7)))
    def skip(x0, z0, w, d):
        if x0 <= AV + 4 and x0 + w >= -AV - 4 or z0 <= AV + 4 and z0 + d >= -AV - 4:
            return True
        return -78 <= x0 <= -30 and -70 <= z0 <= -40          # the apartment block's lot
    def cut(x, y, z):
        for hx, hy, hz, hr in holes:
            if (x - hx) ** 2 + ((y - hy) * 1.2) ** 2 + (z - hz) ** 2 < hr * hr:
                return True
        return False
    SP = 40
    for (x0, z0, w, d) in lots(rng, EXT - 8, SP, 0, skip):
        h = int(rng.integers(10, 44))
        wall, trim = WALLS[int(rng.integers(len(WALLS)))]
        tower(v, x0, z0, w, d, 1, h, rng, n, wall, trim, GLASS[int(rng.integers(len(GLASS)))], 'gray_concrete', cut=cut, lit=0.0)
        # shop fronts with awnings on the ground floor facing the avenues
        aw = ['red_wool', 'blue_wool', 'green_wool', 'orange_wool', 'yellow_wool'][int(rng.integers(5))]
        for x in range(x0 + 1, x0 + w - 1):
            for zz, dz in ((z0, -1), (z0 + d - 1, 1)):
                if (x + zz) % 2 == 0 and not cut(x, 4, zz + dz):
                    v.set(x, 4, zz + dz, aw)
    # rubble in the streets from the holes
    for hx, hy, hz, hr in holes:
        for i in range(int(hr * 6)):
            x = int(hx + rng.normal(0, hr)); z = int(hz + rng.normal(0, hr))
            g = H.get((x, z))
            if g is not None and v.get(x, g + 1, z) is None:
                v.set(x, g + 1, z, rubble.at(x, 1, z))
                if rng.random() < 0.3:
                    v.set(x, g + 2, z, 'cobblestone_slab[type=bottom]')
    # Saitama's apartment block: three floors, outside corridors with railings, stairs at the end
    AX0, AZ0, AW, AD = -74, -62, 34, 12
    wall = 'smooth_sandstone'
    for f in range(4):
        y0 = 1 + f * 4
        for x in range(AX0, AX0 + AW):
            for z in range(AZ0, AZ0 + AD - 3):
                edge = x in (AX0, AX0 + AW - 1) or z in (AZ0, AZ0 + AD - 4)
                for y in range(y0, y0 + 4):
                    if f == 3 and y > y0:
                        continue
                    if y == y0:
                        v.set(x, y, z, 'cut_sandstone' if edge else 'smooth_stone')
                    elif edge:
                        door = z == AZ0 + AD - 4 and (x - AX0) % 6 == 3 and y < y0 + 3
                        win = z == AZ0 + AD - 4 and (x - AX0) % 6 == 1 and y == y0 + 2 or z == AZ0 and (x - AX0) % 6 in (2, 3) and y in (y0 + 1, y0 + 2)
                        if door:
                            v.set(x, y, z, 'dark_oak_planks' if y == y0 + 1 else 'spruce_planks')
                        elif win:
                            v.set(x, y, z, 'glass_pane')
                        else:
                            v.set(x, y, z, wall)
        if f < 3:
            # outside corridor and railing
            for x in range(AX0, AX0 + AW):
                for z in range(AZ0 + AD - 3, AZ0 + AD):
                    v.set(x, y0 + 4 if f < 3 else y0, z, 'smooth_stone_slab[type=top]')
                v.set(x, y0 + 5, AZ0 + AD - 1, 'iron_bars')
            for x in range(AX0, AX0 + AW):
                v.set(x, y0 + 4, AZ0 + AD - 4, 'cut_sandstone')
    for f in range(3):
        for s in range(4):
            x = AX0 + AW + 1 + (s if f % 2 == 0 else 3 - s)
            v.set(x, 2 + f * 4 + s, AZ0 + AD - 2, 'smooth_stone_stairs[facing=%s,half=bottom,shape=straight]' % ('east' if f % 2 == 0 else 'west'))
    for y in range(1, 15):
        for z in (AZ0 + AD - 3, AZ0 + AD - 1):
            v.set(AX0 + AW + 5, y, z, 'iron_bars')
    # his door: number plate and the little potted plant
    v.set(AX0 + 21, 10, AZ0 + AD - 3, 'flower_pot')
    # utility poles along the avenues
    for s in range(-130, 131, 18):
        for (x, z) in ((AV + 2, s), (-AV - 2, s + 9), (s, AV + 2), (s + 9, -AV - 2)):
            if abs(x) < AV + 4 and abs(z) < AV + 4 or math.hypot(x, z) < R + 2 or math.hypot(x, z) > EXT - 4:
                continue
            g = H.get((x, z), 0)
            lean = 1 if (s // 18) % 5 == 2 else 0
            for y in range(1, 11):
                v.set(x + (lean * y // 6), g + y, z, 'andesite_wall')
            v.set(x + lean, g + 10, z + 1, 'spruce_fence'); v.set(x + lean, g + 10, z - 1, 'spruce_fence')
    meta = dict(universo='opm', personagem='Saitama', lugar='Cidade Z', nome='Cidade Z depois do meteoro',
                canon='Cidade Z, a cidade fantasma onde o Saitama mora, depois do meteoro: o cruzamento de duas avenidas vazias com a cratera onde caiu um pedaço do meteoro, buracos menores, prédios furados pelos estilhaços, mato nas rachaduras e o prédio dele de corredor aberto ainda de pé.',
                R=R, altura=50, ceu=['#4b8edd', '#d4e5f3'], neblina=[80, 260],
                jogador=[0, 1, 24], desafiante=[0, 1, -22], camLado=[2, 30, 64], alvoY=0)
    return v, meta
