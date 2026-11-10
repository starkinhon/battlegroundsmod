"""Dragon Ball buildings, second round: the six places Stark marked on the 2D map (2026-11-10 19:07), drawn after the
references in referencias/db-construcoes/1..6.

python3 tools/structures/dragonball2.py [preview_dir] [names...]
-> <name>.nbt in src/main/resources/data/battlegroundsmod/structure/dragonball and previews in preview_dir.

Every building's origin (0,0,0) is the center of its floor: the first air block above the ground (the Lookout's is the
middle of its tiled top). The curved shapes are drawn as implicit solids (numpy masks), so domes, eggs and bowls stay
round instead of stacked boxes."""
import math, os, sys, random
import numpy as np
sys.path.insert(0, os.path.dirname(__file__))
import vox
from vox import Vox
from dragonball import stairs, leaves, palm, OUT

PREV = sys.argv[1] if len(sys.argv) > 1 else os.path.join(OUT, '..', '..', '..', '..', '..', '..', 'build', 'previews')

vox.COLORS.update({
    'cut_red_sandstone': (186, 99, 29), 'chiseled_red_sandstone': (183, 96, 27), 'red_sandstone_stairs': (186, 99, 29), 'pearlescent_froglight': (245, 232, 228), 'azalea': (101, 124, 47), 'flowering_azalea': (150, 120, 160), 'gray_stained_glass': (76, 76, 76), 'light_gray_stained_glass': (153, 153, 153), 'white_stained_glass': (240, 240, 240), 'prismarine': (99, 156, 151), 'prismarine_bricks': (99, 171, 158), 'dark_prismarine': (51, 91, 75), 'stripped_dark_oak_log': (96, 76, 49), 'rooted_dirt': (144, 103, 76), 'tuff': (108, 109, 102),
    'cherry_planks': (226, 178, 172), 'stripped_spruce_wood': (115, 89, 52), 'bamboo_block': (127, 143, 55), 'smoker': (90, 86, 82), 'stripped_oak_wood': (177, 144, 86), 'purple_wall_banner': (122, 42, 173), 'quartz_block': (236, 230, 223),
    'oak_stairs': (162, 130, 78), 'oak_slab': (162, 130, 78), 'decorated_pot': (150, 85, 60), 'potted_dandelion': (230, 210, 40), 'potted_fern': (60, 130, 40), 'flower_pot': (120, 64, 46), 'cauldron': (60, 60, 60), 'water_cauldron': (60, 90, 200), 'lever': (100, 100, 100), 'birch_planks': (196, 179, 123),
    'calcite': (223, 224, 220), 'orange_concrete': (224, 97, 0), 'gold_block': (246, 208, 61), 'yellow_terracotta': (186, 133, 35),
    'light_blue_concrete': (36, 137, 199), 'cyan_concrete': (21, 119, 136), 'green_concrete': (73, 91, 36),
    'lime_concrete': (94, 168, 24), 'green_terracotta': (76, 83, 42), 'spruce_leaves': (40, 80, 40), 'spruce_log': (58, 37, 16),
    'quartz_pillar': (235, 229, 222), 'pink_concrete': (214, 101, 143), 'magenta_concrete': (169, 48, 159),
    'purple_concrete': (100, 31, 156), 'brown_terracotta': (77, 51, 35), 'terracotta': (152, 94, 67),
    'light_gray_terracotta': (135, 107, 98), 'brown_concrete': (96, 60, 32), 'stone_bricks': (122, 121, 122),
    'chiseled_stone_bricks': (119, 118, 119), 'polished_diorite': (192, 193, 194), 'diorite': (188, 188, 188),
    'ladder': (150, 115, 60), 'oak_fence': (162, 130, 78), 'spruce_trapdoor': (114, 84, 48), 'cake': (240, 220, 210),
    'water_cauldron': (60, 90, 200), 'cauldron': (70, 70, 70), 'pink_wool': (237, 141, 172), 'blue_wool': (53, 57, 157),
    'coarse_dirt': (119, 85, 59), 'dirt_path': (148, 122, 65), 'red_sand': (190, 102, 33), 'granite': (149, 103, 85),
    'bricks': (150, 97, 83), 'warped_door': (43, 120, 110), 'blue_terracotta': (74, 60, 91), 'oak_log': (109, 85, 50),
    'spruce_door': (100, 75, 45), 'shroomlight': (240, 146, 70), 'red_carpet': (160, 39, 34), 'packed_mud': (142, 106, 79),
    'magma_block': (140, 60, 20), 'end_rod': (240, 240, 230), 'chain': (60, 65, 80), 'glowstone': (250, 210, 120),
    'smooth_red_sandstone': (181, 98, 31), 'cut_sandstone': (217, 206, 159), 'chiseled_sandstone': (216, 203, 155), 'white_glazed_terracotta': (188, 212, 202),
    'tinted_glass': (44, 38, 46), 'dark_oak_planks': (66, 43, 20), 'stripped_spruce_log': (115, 89, 52),
    'spruce_stairs': (114, 84, 48), 'light_blue_terracotta': (113, 108, 137), 'quartz_bricks': (234, 229, 221), 'cocoa': (150, 90, 40), 'light_blue_wool': (58, 175, 217), 'chiseled_quartz_block': (231, 226, 218), 'yellow_carpet': (248, 197, 39), 'azalea_leaves': (90, 114, 44), 'flowering_azalea_leaves': (100, 112, 60), 'light_blue_carpet': (58, 175, 217), 'purple_bed': (121, 42, 172), 'dark_oak_fence': (66, 43, 20), 'dark_oak_pressure_plate': (66, 43, 20), 'dark_oak_stairs': (66, 43, 20), 'bookshelf': (117, 94, 59), 'purple_stained_glass': (127, 63, 178), 'smoker': (90, 90, 90), 'barrel': (140, 105, 60), 'crafting_table': (130, 100, 60), 'lime_concrete': (94, 168, 24), 'lime_stained_glass': (127, 204, 25), 'glass': (200, 230, 235), 'glass_pane': (200, 230, 235), 'yellow_concrete': (240, 175, 21), 'deepslate_tiles': (54, 54, 55), 'mud_bricks': (137, 103, 79),
    'oak_slab': (162, 130, 78), 'spruce_slab': (114, 84, 48), 'smooth_quartz_slab': (235, 229, 222), 'lantern': (200, 150, 60),
    'cyan_stained_glass_pane': (76, 127, 153), 'light_blue_stained_glass': (100, 160, 220), 'campfire': (230, 140, 40),
    'flower_pot': (120, 68, 53), 'potted_bamboo': (90, 140, 40), 'bamboo': (90, 140, 40), 'brewing_stand': (120, 100, 80),
    'orange_terracotta': (161, 83, 37), 'white_terracotta': (209, 178, 161), 'iron_bars': (150, 150, 150),
})


def solid(v, inside, bounds, block, fill=False, air_inside=False):
    """Voxelize an implicit shape. inside(X,Y,Z) -> bool array; bounds = (x0,x1,y0,y1,z0,z1) inclusive.
    block(x,y,z,surface) -> block name or None (skip). The shell (blocks touching the outside) is always drawn; with
    fill the inside is drawn too, with air_inside the inside is cleared to air (rooms)."""
    x0, x1, y0, y1, z0, z1 = bounds
    X, Y, Z = np.mgrid[x0 - 1:x1 + 2, y0 - 1:y1 + 2, z0 - 1:z1 + 2]
    m = inside(X.astype(float), Y.astype(float), Z.astype(float))
    m[0, :, :] = m[-1, :, :] = m[:, 0, :] = m[:, -1, :] = m[:, :, 0] = m[:, :, -1] = False
    inner = m.copy()
    for ax in range(3):
        for s in (1, -1):
            inner &= np.roll(m, s, axis=ax)
    shell = m & ~inner
    for i, j, k in zip(*np.nonzero(shell)):
        b = block(int(X[i, j, k]), int(Y[i, j, k]), int(Z[i, j, k]), True)
        if b:
            v.set(int(X[i, j, k]), int(Y[i, j, k]), int(Z[i, j, k]), b)
    if fill or air_inside:
        for i, j, k in zip(*np.nonzero(inner)):
            x, y, z = int(X[i, j, k]), int(Y[i, j, k]), int(Z[i, j, k])
            if air_inside:
                v.set(x, y, z, 'air')
            else:
                b = block(x, y, z, False)
                if b:
                    v.set(x, y, z, b)


def carve(v, inside, bounds, block=lambda x, y, z: 'air'):
    """Set every voxel inside the shape (rooms, doors, holes)."""
    x0, x1, y0, y1, z0, z1 = bounds
    X, Y, Z = np.mgrid[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1]
    m = inside(X.astype(float), Y.astype(float), Z.astype(float))
    for i, j, k in zip(*np.nonzero(m)):
        x, y, z = int(X[i, j, k]), int(Y[i, j, k]), int(Z[i, j, k])
        b = block(x, y, z)
        if b:
            v.set(x, y, z, b)


def ladder(v, x, z, y0, y1, facing='west', support='light_gray_concrete'):
    """A ladder column from y0 to y1 at (x, z) with its support block on the side it hangs from."""
    dx, dz = {'west': (1, 0), 'east': (-1, 0), 'north': (0, 1), 'south': (0, -1)}[facing]
    for y in range(min(y0, y1), max(y0, y1) + 1):
        if v.get(x + dx, y, z + dz) in (None, 'minecraft:air'):
            v.set(x + dx, y, z + dz, support)
        v.set(x, y, z, f'ladder[facing={facing},waterlogged=false]')


def door(v, x, y, z, facing, block='oak_door', hinge='left'):
    v.set(x, y, z, f'{block}[facing={facing},half=lower,hinge={hinge},open=false,powered=false]')
    v.set(x, y + 1, z, f'{block}[facing={facing},half=upper,hinge={hinge},open=false,powered=false]')


def trapdoor(v, x, y, z, facing='north', block='spruce_trapdoor'):
    v.set(x, y, z, f'{block}[facing={facing},half=top,open=false,powered=false,waterlogged=false]')


def cypress(v, x, y, z, h, rnd):
    """Kami's garden cypress: slim dark cone of leaves around a short trunk."""
    v.set(x, y, z, 'spruce_log[axis=y]')
    for j in range(1, h + 1):
        v.set(x, y + j, z, 'spruce_log[axis=y]' if j < h - 1 else leaves('spruce_leaves'))
        r = (1.45 + 0.08 * max(0, h - 7)) * math.sin(math.pi * min(1.0, (j + 0.5) / (h + 1.2))) ** 0.8
        for a in range(-2, 3):
            for b in range(-2, 3):
                if (a or b) and a * a + b * b <= r * r + 0.15:
                    v.set(x + a, y + j, z + b, leaves('spruce_leaves'))
    v.set(x, y + h + 1, z, leaves('spruce_leaves'))


def big_palm(v, x, z, y, h, lean, rnd):
    """A tall palm (refs: the big trees of the Lookout): curved trunk, thick at the base, long drooping fronds."""
    log = 'jungle_log[axis=y]'
    v.set(x, y, z, 'jungle_wood[axis=y]')
    bx = 0.0
    px = x
    for j in range(h):
        bx = lean * 2.2 * (j / h) ** 2
        cx = x + round(bx)
        if cx != px:
            v.set(cx, y + j - 1, z, log)
        v.set(cx, y + j, z, log); px = cx
    tx, ty = px, y + h
    leaf = leaves()
    v.set(tx, ty, tz := z, leaf)
    for k in range(10):
        a = k * math.pi / 5 + rnd.random() * 0.3
        L = 6 + rnd.randint(0, 1)
        for s_ in range(1, L + 1):
            droop = -round(0.05 * s_ * s_ * 1.4) + (1 if s_ < 2 else 0)
            v.set(tx + round(math.cos(a) * s_), ty + droop, tz + round(math.sin(a) * s_), leaf)
    for c in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        v.set(tx + c[0], ty - 1, tz + c[1], 'cocoa[age=2,facing=%s]' % {(1, 0): 'west', (-1, 0): 'east', (0, 1): 'north', (0, -1): 'south'}[c]) if rnd.random() < 0.5 else None


# ---------------------------------------------------------------------------------------------------------------
def kami_lookout():
    """Kami's Lookout (ref 1): a floating disc ~110 across. White tiled top; rim with the red/orange zigzag band, a cream
    band with long black oval windows and a red line, then the rounded bowl underneath going cream -> light blue -> blue,
    with a tip at the bottom. The palace at the back (white, a big yellow dome with a lantern and two smaller yellow
    domes), pink-roofed colonnades on both sides, saucer lamp posts, cypress planters along the edge, two palm planters
    along the central path and the ladder down the rim at the front."""
    v = Vox(); rnd = random.Random(31)
    R = 55
    # top floor: tiles 7x7 with calcite joints, a smooth white edge
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            d = math.hypot(x, z)
            if d <= R + 0.3:
                edge = d > R - 1.5
                joint = (x % 7 == 0 or z % 7 == 0)
                tile_rnd = random.Random(x // 7 * 1000 + z // 7).random()                 # each 7x7 tile its own shade
                body = 'white_concrete' if tile_rnd < 0.55 else 'smooth_quartz' if tile_rnd < 0.85 else 'quartz_bricks'
                v.set(x, -1, z, 'smooth_quartz' if edge else 'calcite' if joint else body)

    # rim and bowl as one solid of revolution: radius by height
    def prof(y):
        if y >= -15:
            return R + 0.3 - max(0, -y - 12) * 0.25
        t = (y + 15) / 24.0                     # 0 at -15, -1 at -39
        return (R - 1) * math.sqrt(max(0.0, 1 - t * t)) ** 0.9

    def inside(X, Y, Z):
        r = np.hypot(X, Z)
        pr = np.vectorize(prof)(Y)
        return (r <= pr) & (Y <= -2) & (Y >= -39)

    def skin(x, y, z, surf):
        a = math.atan2(z, x); s = (a % (2 * math.pi)) * R                     # arc length along the rim
        if -6 <= y <= -2:                                                    # zigzag band
            tri = abs((s / 6.0) % 2 - 1)                                      # 0..1 triangle wave, period 12
            lvl = (y + 6) / 4.0                                               # 0 bottom .. 1 top
            if y == -2 or y == -6:
                return 'red_concrete'
            return 'orange_concrete' if lvl > tri else 'red_concrete'
        if y == -7:
            return 'white_concrete'
        if -12 <= y <= -8:                                                   # cream band with oval windows
            if y in (-10, -9) or (y in (-11, -8) and (s % 13) > 1.5 and (s % 13) < 7.5):
                if (s % 13) < 8.5:
                    return 'black_concrete'
            return 'smooth_sandstone'
        if y == -13:
            return 'red_concrete'
        if -15 <= y <= -14:
            return 'gray_concrete' if (s % 6) < 1 and y == -14 else 'smooth_sandstone'
        depth = (-y - 15) / 24.0
        n = rnd.random() * 0.12
        if depth + n < 0.25:
            return 'white_concrete'
        if depth + n < 0.5:
            return 'light_blue_concrete' if depth + n > 0.4 else 'white_terracotta' if False else 'smooth_sandstone'
        if depth + n < 0.72:
            return 'light_blue_concrete'
        return 'blue_concrete'
    solid(v, inside, (-R - 1, R + 1, -40, -2, -R - 1, R + 1), skin)
    # a dark band on the bowl and the tip
    for y in range(-28, -26):
        r = prof(y)
        for k in range(int(2 * math.pi * r)):
            a = k / r
            v.set(round(r * math.cos(a) - 0.3 * math.cos(a)), y, round(r * math.sin(a) - 0.3 * math.sin(a)), 'cyan_concrete')
    for y in range(-46, -38):
        rr = 3.2 if y > -42 else 2.2 if y > -44 else 1.2
        v.disc(0, y, 0, rr, 'blue_concrete' if y > -44 else 'light_blue_concrete')
    v.set(0, -47, 0, 'blue_concrete')

    # the palace at the back (-z), its door facing +z
    pz = -32
    v.box(-11, 0, pz - 7, 11, 9, pz + 7, 'quartz_block')
    v.box(-10, 1, pz - 6, 10, 8, pz + 6, 'air')
    v.box(-12, 10, pz - 8, 12, 10, pz + 8, 'smooth_quartz')                       # cornice
    v.box(-11, 0, pz - 7, 11, 0, pz + 7, 'smooth_quartz')
    for x in range(-11, 12, 4):                                                   # pilasters
        v.box(x, 1, pz + 7, x, 9, pz + 7, 'quartz_pillar[axis=y]')
    # tall arched doorway with gold trim
    for y in range(1, 8):
        for x in range(-3, 4):
            w = 3 if y < 6 else 2 if y == 6 else 1
            if abs(x) <= w:
                v.set(x, y, pz + 7, 'air' if abs(x) < w or y == 7 else 'gold_block')
            if abs(x) == w + 1 and y < 7:
                v.set(x, y, pz + 7, 'gold_block')
    v.box(-4, 8, pz + 7, 4, 8, pz + 7, 'gold_block')
    for x in (-8, 8):                                                              # side windows
        v.box(x - 1, 3, pz + 7, x + 1, 6, pz + 7, 'light_blue_stained_glass')
    # big yellow dome on a windowed drum, lantern on top
    v.cylinder(0, 11, pz, 8, 14, 'quartz_block')
    for k in range(16):
        a = k * math.pi / 8
        v.box(round(8 * math.cos(a)), 12, pz + round(8 * math.sin(a)), round(8 * math.cos(a)), 13, pz + round(8 * math.sin(a)),
              'light_blue_stained_glass' if k % 2 else 'quartz_block')

    def dome(cx, cy, cz, r, hgt, ribs=16):
        def ins(X, Y, Z):
            return ((X - cx) ** 2 + (Z - cz) ** 2) / r ** 2 + ((Y - cy) / hgt) ** 2 <= 1.0
        def col(x, y, z, s):
            a = math.atan2(z - cz, x - cx)
            rib = (a * ribs / (2 * math.pi)) % 1 < 0.22
            return 'gold_block' if rib or y == cy else 'yellow_concrete'
        solid(v, lambda X, Y, Z: ins(X, Y, Z) & (Y >= cy), (cx - r - 1, cx + r + 1, cy, cy + hgt + 1, cz - r - 1, cz + r + 1), col)
    dome(0, 15, pz, 9, 7)
    # on top a white rotunda ringed with columns (refs: the tall layered silhouette), then the gold cap and spike
    v.disc(0, 21, pz, 5.6, 'smooth_quartz')
    v.cylinder(0, 22, pz, 3.6, 25, 'quartz_block', hollow=True)
    for k in range(12):
        a = k * math.pi / 6
        x, z = round(3.6 * math.cos(a)), pz + round(3.6 * math.sin(a))
        if k % 2:
            v.box(x, 23, z, x, 24, z, 'light_blue_stained_glass')
        x, z = round(5.0 * math.cos(a)), pz + round(5.0 * math.sin(a))
        v.box(x, 22, z, x, 25, z, 'quartz_pillar[axis=y]')
    v.disc(0, 26, pz, 5.6, 'smooth_quartz'); v.disc(0, 26, pz, 2.5, 'glowstone')
    dome(0, 27, pz, 4, 3, ribs=8)
    v.set(0, 31, pz, 'gold_block'); v.set(0, 32, pz, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
    # porch at the door: a gold barrel-vault canopy on white columns (refs)
    for z in range(pz + 8, pz + 14):
        for x in range(-4, 5):
            h = 6 + round(1.6 * math.sqrt(max(0, 1 - (x / 4.5) ** 2)))
            v.set(x, h, z, 'yellow_concrete' if z % 2 else 'gold_block')
        for x in (-4, 4):
            if z in (pz + 9, pz + 13):
                v.box(x, 1, z, x, 5, z, 'quartz_pillar[axis=y]')
    v.box(-4, 6, pz + 13, 4, 6, pz + 13, 'smooth_quartz')
    # two side pavilions with smaller domes
    for sx in (-17, 17):
        v.cylinder(sx, 0, pz + 2, 5, 8, 'quartz_block', hollow=True)
        v.disc(sx, 0, pz + 2, 4.5, 'smooth_quartz')
        for k in range(8):
            a = k * math.pi / 4 + 0.2
            v.box(sx + round(5 * math.cos(a)), 3, pz + 2 + round(5 * math.sin(a)), sx + round(5 * math.cos(a)), 6,
                  pz + 2 + round(5 * math.sin(a)), 'light_blue_stained_glass')
        v.box(sx, 1, pz + 7, sx, 2, pz + 7, 'air')
        v.disc(sx, 9, pz + 2, 5.5, 'smooth_quartz')
        dome(sx, 10, pz + 2, 5, 5, ribs=10)
        v.cylinder(sx, 9, pz + 2, 5.6, 9, 'gold_block', hollow=True)                   # gold band under the dome
        v.cylinder(sx, 15, pz + 2, 1.6, 16, 'quartz_block', hollow=False)            # little white lantern on top
        v.set(sx, 16, pz + 2, 'glowstone')
        v.disc(sx, 17, pz + 2, 2.2, 'smooth_quartz'); v.set(sx, 18, pz + 2, 'gold_block')
        v.set(sx, 19, pz + 2, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
    # ---- inside the palace (Stark 2026-11-10, interiors one at a time: the way to the Room of Spirit and Time) ----
    v.box(-10, 0, pz - 6, 10, 0, pz + 6, 'smooth_quartz')
    for x in range(-10, 11):                                                      # gold inlay border and squares
        for z in range(pz - 6, pz + 7):
            if abs(x) == 9 or z in (pz - 5, pz + 5):
                v.set(x, 0, z, 'gold_block' if (x + z) % 2 == 0 else 'smooth_quartz')
    for z in range(pz - 4, pz + 7):                                               # red carpet from the door to the back
        for x in (-1, 0, 1):
            v.set(x, 1, z, 'red_carpet')
        v.set(-2, 1, z, 'yellow_carpet'); v.set(2, 1, z, 'yellow_carpet')
    for sx in (-6, 6):                                                            # two rows of columns
        for z in (pz - 3, pz + 1, pz + 5):
            v.box(sx, 1, z, sx, 8, z, 'quartz_pillar[axis=y]')
            v.set(sx, 1, z, 'chiseled_quartz_block'); v.set(sx, 8, z, 'chiseled_quartz_block')
    carve(v, lambda X, Y, Z: (X ** 2 + (Z - pz) ** 2 <= 6.5 ** 2) & (Y >= 9) & (Y <= 10), (-7, 7, 9, 10, pz - 7, pz + 7))
    for y in range(12, 23):                                                       # chandelier hanging from the dome
        v.set(0, y, pz, 'chain[axis=y]')
    for k in range(8):
        a = k * math.pi / 4
        v.set(round(2 * math.cos(a)), 11, pz + round(2 * math.sin(a)), 'lantern[hanging=true,waterlogged=false]')
        v.set(round(2 * math.cos(a)), 12, pz + round(2 * math.sin(a)), 'chain[axis=y]')
    v.disc(0, 12, pz, 2.2, 'gold_block'); v.set(0, 11, pz, 'glowstone')
    for x in (-9, 9):                                                             # big plants in gold pots
        for z in (pz - 4, pz + 4):
            v.set(x, 1, z, 'gold_block'); v.set(x, 2, z, leaves('azalea_leaves'))
            v.set(x, 3, z, leaves('flowering_azalea_leaves'))
    for x in (-8, 8):
        v.set(x, 6, pz - 6, 'lantern[hanging=false,waterlogged=false]')
    # the back wall: through a corridor, the plain wooden door of the Room of Spirit and Time (ref)
    v.box(-2, 1, pz - 7, 2, 5, pz - 7, 'air')
    for x in (-3, 3):
        v.box(x, 1, pz - 7, x, 6, pz - 7, 'gold_block')
    v.box(-3, 6, pz - 7, 3, 6, pz - 7, 'gold_block')
    cz0, cz1 = pz - 18, pz - 8                                                    # the corridor behind the palace
    v.box(-4, 0, cz0 - 1, 4, 7, cz1, 'quartz_block')
    v.box(-3, 1, cz0, 3, 6, cz1, 'air')
    v.box(-3, 0, cz0, 3, 0, cz1, 'smooth_quartz')
    for z in range(cz0, cz1 + 1):
        v.set(0, 1, z, 'blue_carpet')
        if (z - cz0) % 4 == 1:
            for x in (-3, 3):
                v.set(x, 4, z, 'lantern[hanging=false,waterlogged=false]') if False else None
            v.set(0, 6, z, 'lantern[hanging=true,waterlogged=false]')
    v.box(-4, 7, cz0 - 1, 4, 7, cz1, 'smooth_quartz')
    for z in range(cz0 + 1, cz1, 3):
        v.set(-4, 3, z, 'light_blue_stained_glass'); v.set(4, 3, z, 'light_blue_stained_glass')
        v.set(-4, 4, z, 'light_blue_stained_glass'); v.set(4, 4, z, 'light_blue_stained_glass')
    # the door at the end: wooden, in a white frame with a gold arch, the round light over it
    v.box(-2, 1, cz0 - 1, 2, 5, cz0 - 1, 'smooth_quartz')
    door(v, 0, 1, cz0 - 1, 'south', block='spruce_door', hinge='left')
    v.set(-1, 1, cz0 - 1, 'quartz_pillar[axis=y]'); v.set(1, 1, cz0 - 1, 'quartz_pillar[axis=y]')
    v.box(-1, 3, cz0 - 1, 1, 3, cz0 - 1, 'gold_block'); v.set(0, 4, cz0 - 1, 'gold_block')
    v.set(0, 5, cz0 - 1, 'sea_lantern')
    # the pad in front of the door (TimeChamber takes whoever stands on it for two seconds)
    v.set(0, 0, cz0 + 1, 'sea_lantern')
    for x in (-1, 1):
        v.set(x, 0, cz0 + 1, 'light_blue_concrete'); v.set(x, 0, cz0 + 2, 'light_blue_concrete')
    v.set(0, 0, cz0 + 2, 'light_blue_concrete')
    v.set(0, 1, cz0 + 1, 'air'); v.set(0, 1, cz0 + 2, 'blue_carpet')
    # pink-roofed colonnades from the pavilions outwards and toward the front
    def colonnade(x0, x1, z):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            if x % 3 == 0:
                v.box(x, 0, z, x, 4, z, 'quartz_pillar[axis=y]')
            v.set(x, 5, z - 1, 'pink_concrete'); v.set(x, 5, z, 'pink_concrete'); v.set(x, 5, z + 1, 'pink_concrete')
            v.set(x, 6, z, 'pink_terracotta')
            v.set(x, 0, z, 'smooth_quartz') if x % 3 else None
    colonnade(-34, -23, pz + 3); colonnade(23, 34, pz + 3)
    colonnade(-21, -12, pz + 9); colonnade(12, 21, pz + 9)
    # the central path: palm planters on both sides
    for sx in (-6, 6):
        v.box(sx - 2, 0, -18, sx + 2, 0, 22, 'green_concrete')
        v.box(sx - 1, 0, -17, sx + 1, 0, 21, 'grass_block')
        for i, z in enumerate(range(-14, 22, 6)):
            big_palm(v, sx, z, 1, 11 + (i % 3) * 2, 1 if sx > 0 else -1, rnd)
    # cypress planters along the edge (front and sides) and beside the palace
    groups = []
    for a0 in (-2.75, -2.25, -1.75, -1.3, -0.4, 0.05, 0.5, 0.95, 1.4, 1.85, 2.35, 2.8):
        groups.append(a0)
    for a0 in groups:
        cx, cz = 45 * math.cos(a0), 45 * math.sin(a0)
        tx, tz = -math.sin(a0), math.cos(a0)                                      # along the edge
        if cz < -25 and abs(cx) < 38:
            continue                                                              # the palace is there
        for t in range(-6, 7):
            for w in (-1, 0, 1):
                x, z = round(cx + tx * t + math.cos(a0) * w), round(cz + tz * t + math.sin(a0) * w)
                v.set(x, 0, z, 'green_concrete' if abs(t) == 6 or w != 0 else 'grass_block')
        for t in range(-4, 5, 2):
            x, z = round(cx + tx * t), round(cz + tz * t)
            cypress(v, x, 1, z, rnd.randint(9, 12), rnd)
    for sx in (-26, 26):                                                          # beside the palace front
        v.box(sx - 4, 0, pz + 12, sx + 4, 0, pz + 14, 'green_concrete')
        v.box(sx - 3, 0, pz + 13, sx + 3, 0, pz + 13, 'grass_block')
        for x in range(sx - 3, sx + 4, 2):
            cypress(v, x, 1, pz + 13, rnd.randint(8, 10), rnd)
    # saucer lamp posts
    for lx, lz, h in ((-20, -44, 22), (20, -44, 22), (-30, -36, 18), (30, -36, 18), (-40, -14, 16), (40, -14, 16),
                      (-15, 10, 14), (15, 10, 14)):
        v.box(lx, 0, lz, lx, h, lz, 'quartz_pillar[axis=y]')
        v.disc(lx, h + 1, lz, 1.5, 'white_concrete')
        v.disc(lx, h + 2, lz, 3.2, 'white_concrete')
        v.disc(lx, h + 2, lz, 1.2, 'sea_lantern')
        for k in range(12):
            a = k * math.pi / 6
            v.set(lx + round(2.6 * math.cos(a)), h + 3, lz + round(2.6 * math.sin(a)), 'light_blue_stained_glass')
        v.disc(lx, h + 4, lz, 2.4, 'white_concrete'); v.set(lx, h + 5, lz, 'white_concrete')
        if h >= 18:                                                                # the tall ones: a second saucer below
            m = h - 6
            v.disc(lx, m, lz, 1.4, 'white_concrete'); v.disc(lx, m + 1, lz, 2.8, 'white_concrete')
            v.disc(lx, m + 1, lz, 1.0, 'sea_lantern'); v.disc(lx, m + 2, lz, 2.0, 'white_concrete')
    # a low white border all round the edge (refs)
    for k in range(int(2 * math.pi * (R - 0.6))):
        a = k / (R - 0.6)
        v.set(round((R - 0.6) * math.cos(a)), 0, round((R - 0.6) * math.sin(a)), 'smooth_quartz_slab[type=bottom,waterlogged=false]')
    # the ladder down the rim at the front (Goku came up from Korin's tower)
    for y in range(-15, 0):
        v.set(0, y, R + 1, 'ladder[facing=south,waterlogged=false]')
        v.set(0, y, R, 'smooth_quartz' if v.get(0, y, R) in (None, 'minecraft:air') else v.get(0, y, R))
    # the arrival pad from Korin's tower, near the front edge
    v.disc(0, -1, 40, 2.6, 'smooth_quartz'); v.disc(0, -1, 40, 1.6, 'light_blue_concrete'); v.set(0, -1, 40, 'sea_lantern')
    for c in ((2, 42), (-2, 42), (2, 38), (-2, 38)):
        v.set(c[0], 0, c[1], 'end_rod[facing=up]')
    return v


# ---------------------------------------------------------------------------------------------------------------
def buu_house():
    """Majin Buu's house (ref 2): a white segmented worm lying on the ground with a round bulb at the front, and a long
    capsule 'head' lying on top of it, its open round mouth facing front. Portholes along the sides, little feet in
    front, a round door on the bulb. Inside: a table with cakes (Buu loves sweets) and a ladder up into the capsule."""
    v = Vox(); rnd = random.Random(5)
    white = lambda x, y, z, s: 'calcite' if rnd.random() < 0.12 else 'white_concrete'
    # body: bulb + 4 shrinking segments trailing to +x
    parts = [(1, 5.8, 7.2), (9, 5.2, 5.4), (14, 4.5, 4.7), (18.4, 3.6, 3.8), (21.8, 2.7, 2.8)]
    def body(X, Y, Z):
        m = np.zeros(X.shape, bool)
        for cx, ry, rr in parts:
            m |= ((X - cx) / (rr * 0.95)) ** 2 + ((Y + 0.5) / ry) ** 2 + (Z / rr) ** 2 <= 1
        return m & (Y >= 0)
    def seg_col(x, y, z, srf):
        ds = sorted(math.sqrt(((x - cx) / (rr * 0.95)) ** 2 + ((y + 0.5) / ry) ** 2 + (z / rr) ** 2) for cx, ry, rr in parts)
        return 'light_gray_concrete' if ds[1] - ds[0] < 0.1 else white(x, y, z, srf)   # the creases between segments
    solid(v, body, (-9, 26, 0, 8, -9, 9), seg_col, air_inside=True)
    # capsule head: long rounded cylinder on top, x -4..17, center y 11
    def cap(X, Y, Z):
        cx = np.clip(X, 2.0, 14.0)
        r = 5.0 + 0.5 * np.clip((X - 7) / 8, -1, 1)
        return (X - cx) ** 2 / 1.0 + (Y - 12.0) ** 2 + Z ** 2 <= r ** 2
    solid(v, cap, (-5, 21, 5, 19, -7, 7), white, air_inside=True)
    # neck joining the head to the body (a short thick column inside)
    v.cylinder(5, 5, 0, 2.2, 8, 'white_concrete', hollow=True)
    carve(v, lambda X, Y, Z: ((X - 5) ** 2 + Z ** 2 <= 1.6 ** 2), (3, 7, 4, 9, -2, 2))
    # open mouth of the capsule facing -x: dark ring and air
    for y in range(7, 17):
        for z in range(-5, 6):
            d = math.hypot(y - 12.0, z)
            if d <= 3.2:
                v.set(-3, y, z, 'air'); v.set(-2, y, z, 'air')
            elif d <= 4.2 and v.get(-3, y, z):
                v.set(-3, y, z, 'light_gray_concrete')
    # round door on the bulb (front, -x side)
    for y in range(0, 6):
        for z in range(-3, 4):
            d = math.hypot((y - 2.2) * 0.9, z)
            if d <= 2.0:
                for x in (-6, -5):
                    if v.get(x, y, z):
                        v.set(x, y, z, 'air')
            elif d <= 2.9:
                for x in range(-7, -3):
                    if v.get(x, y, z) and v.get(x - 1, y, z) is None:
                        v.set(x, y, z, 'light_gray_concrete'); break
    # portholes: on the capsule sides and on each segment
    def porthole(x, y, side):
        for z in range(side * 8, 0, -side):
            if v.get(x, y, z) not in (None, 'minecraft:air'):
                v.set(x, y, z, 'blue_stained_glass'); return
    for x in (2, 5, 8, 11, 14):
        porthole(x, 12, 1); porthole(x, 12, -1)
    for x in (9, 14, 18):
        porthole(x, 2, 1); porthole(x, 2, -1)
    porthole(1, 3, 1); porthole(1, 3, -1)
    # little feet at the front
    for sz in (-5, 5):
        solid(v, lambda X, Y, Z, sz=sz: ((X + 6) / 3.5) ** 2 + (Y / 1.2) ** 2 + ((Z - sz) / 2.0) ** 2 <= 1,
              (-10, -2, 0, 1, sz - 3, sz + 3), white, fill=True)
    # inside: floor, cake table, couch, light, ladder to the capsule
    carve(v, lambda X, Y, Z: (((X - 1) / 6.2) ** 2 + (Z / 6.2) ** 2 <= 1) & (Y == 0) | ((((X - 8.5) / 4.2) ** 2 + (Z / 4.2) ** 2 <= 1) & (Y == 0)),
          (-6, 13, 0, 0, -7, 7), lambda x, y, z: 'pink_wool' if (x + z) % 2 else 'white_wool')
    v.set(0, 1, 0, 'dark_oak_fence'); v.set(0, 2, 0, 'pink_carpet') if False else None
    v.set(0, 2, 0, 'cake[bites=0]'); v.set(0, 1, 2, 'pink_wool'); v.set(0, 1, -2, 'pink_wool')
    v.set(-1, 1, 3, 'pink_wool'); v.set(1, 1, 3, 'pink_wool')
    v.set(1, 5, 0, 'lantern[hanging=true,waterlogged=false]')
    ladder(v, 5, 0, 1, 9, facing='west', support='white_concrete')
    v.set(6, 1, 0, 'white_concrete')
    # capsule floor (Buu's room up there): bed of blue wool, window seat
    for x in range(-1, 17):
        for z in range(-2, 3):
            if v.get(x, 8, z) in (None, 'minecraft:air') and not (x == 5 and z == 0):
                v.set(x, 8, z, 'blue_wool' if x < 11 else 'pink_wool')
    v.set(9, 9, 1, 'cake[bites=0]')
    v.set(11, 15, 0, 'lantern[hanging=true,waterlogged=false]')
    return v


# ---------------------------------------------------------------------------------------------------------------
def cell_arena():
    """The Cell Games ring (ref 3): a square tiled platform 50 across, raised on a wider plinth (stepped edge), and the
    four very tall white needles on round bulb pedestals at the corners. A worn dirt border all around."""
    v = Vox(); rnd = random.Random(3)
    H = 25
    for x in range(-H - 6, H + 7):
        for z in range(-H - 6, H + 7):
            m = max(abs(x), abs(z))
            if m <= H + 2:
                v.set(x, -1, z, 'smooth_stone')
                v.set(x, 0, z, 'polished_diorite' if m > H else 'smooth_stone')
            elif rnd.random() < 0.75 - (m - H - 3) * 0.12:
                v.set(x, -1, z, rnd.choice(['coarse_dirt', 'coarse_dirt', 'dirt_path', 'packed_mud']))
    for x in range(-H, H + 1):
        for z in range(-H, H + 1):
            m = max(abs(x), abs(z))
            joint = (x + H) % 5 == 0 or (z + H) % 5 == 0
            v.set(x, 1, z, 'smooth_quartz' if m == H else 'polished_diorite' if joint else 'white_concrete' if (x // 5 + z // 5) % 2 else 'calcite')
    # corner needles
    C = H + 3
    for sx in (-1, 1):
        for sz in (-1, 1):
            cx, cz = sx * C, sz * C
            prof = [(0, 3.4), (1, 3.0), (2, 2.4), (3, 2.0), (4, 2.6), (5, 2.9), (6, 2.6), (7, 2.0), (8, 1.6), (9, 2.2)]
            for y, r in prof:
                v.disc(cx, y, cz, r, 'smooth_quartz' if y in (0, 4, 5, 9) else 'white_concrete')
            def needle(X, Y, Z, cx=cx, cz=cz):
                t = (Y - 10) / 34.0
                r = 2.3 * np.clip(1 - t, 0, 1) ** 0.85 * (1 + 0.15 * np.clip(1 - abs(t - 0.15) * 4, 0, 1))
                return ((X - cx) ** 2 + (Z - cz) ** 2 <= r * r + 0.2) & (Y >= 10) & (Y <= 44)
            solid(v, needle, (cx - 3, cx + 3, 10, 44, cz - 3, cz + 3),
                  lambda x, y, z, s: 'light_gray_concrete' if y > 41 else 'white_concrete', fill=True)
            v.set(cx, 45, cz, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
    return v


# ---------------------------------------------------------------------------------------------------------------
KORIN_TOP = 150      # pillar height (blocks above the ground) where the tower's ball starts


def korin_tower():
    """Korin's tower (ref 4): a thin carved pillar from the ground up into the clouds, a ladder all the way, and at the
    top the round house: lower bowl with oval windows and a gold ring, an open balcony with a railing, the brown dome with
    the zigzag band and dots, and the gold finial. Inside: Korin's room with the Ultra Holy Water and the senzu pots."""
    v = Vox(); rnd = random.Random(44)
    T = KORIN_TOP
    # base: carved flared pedestal
    for y, r in ((-2, 5.2), (-1, 5.0), (0, 4.6), (1, 4.0), (2, 3.6), (3, 3.2), (4, 3.6), (5, 3.0), (6, 2.7)):
        v.disc(0, y, 0, r, 'chiseled_sandstone' if y in (1, 4) else 'smooth_sandstone' if y < 0 else 'cut_sandstone')
    # the pillar: radius 2, a carved ring every 9 blocks, darker joints
    for y in range(7, T + 1):
        k = y % 9
        r = 2.8 if k == 0 else 2.45 if k in (1, 8) else 2.2
        blk = 'chiseled_sandstone' if k == 0 else 'smooth_sandstone' if k in (1, 8) else 'cut_sandstone' if (y // 9) % 2 else 'sandstone'
        v.disc(0, y, 0, r, blk)
    # neck under the bowl (hollow, the ladder goes up through it)
    for y in range(T - 3, T + 2):
        v.cylinder(0, y, 0, 4.6 if y > T - 3 else 4.0, y, 'smooth_sandstone', hollow=True)
    b0 = T                                                       # bottom of the bowl
    FL = b0 + 12                                                 # balcony / hall floor
    LF = b0 + 3                                                  # floor of the senzu room inside the bowl
    def bowl_r(Y):
        return 5 + 13 * np.sqrt(np.clip(1 - ((FL - Y) / 12.0) ** 2, 0, 1))
    # lower bowl (refs: white with long black oval windows, an orange stripe, gold/tan underside running into the pillar)
    def bowl(X, Y, Z):
        return (np.hypot(X, Z) <= bowl_r(Y)) & (Y <= FL) & (Y >= b0)
    def bowl_col(x, y, z, s):
        if not s:
            return 'smooth_sandstone'                                # inside: the senzu room's pale stone walls
        a = math.atan2(z, x)
        if y <= b0 + 1:
            return 'chiseled_sandstone'
        if y == FL - 1:
            return 'light_gray_concrete'
        if y == FL - 7:
            return 'orange_terracotta'
        if y == FL - 8:
            return 'gold_block'
        if y > FL - 7:
            return 'white_concrete'
        return 'yellow_terracotta' if ((a % (2 * math.pi)) * 12) % 5 < 1 else 'smooth_sandstone'
    solid(v, bowl, (-18, 18, b0, FL, -18, 18), bowl_col, fill=True)
    carve(v, lambda X, Y, Z: (np.hypot(X, Z) <= bowl_r(Y) - 2) & (Y > LF) & (Y < FL), (-17, 17, LF + 1, FL - 1, -17, 17))
    v.disc(0, LF, 0, float(bowl_r(LF)) - 1.6, 'tuff')                 # gravel-grey floor of the senzu room
    # four long oval windows through the wall (glass is dark from outside, the sky shows through from inside)
    for y in range(FL - 6, FL - 1):
        R = float(bowl_r(y)); half = {FL - 6: 0.22, FL - 5: 0.3, FL - 4: 0.32, FL - 3: 0.3, FL - 2: 0.2}[y]
        for x in range(-19, 20):
            for z in range(-19, 20):
                rr = math.hypot(x, z)
                if not (R - 2.6 < rr <= R + 0.6):
                    continue
                a = math.atan2(z, x)
                for k in range(4):
                    c = math.pi / 4 + k * math.pi / 2
                    d = abs((a - c + math.pi) % (2 * math.pi) - math.pi)
                    if d < half:
                        v.set(x, y, z, 'gray_stained_glass')
                    elif d < half + 0.07 and rr > R - 1:
                        v.set(x, y, z, 'black_concrete')
    carve(v, lambda X, Y, Z: (np.hypot(X, Z) <= 4.4) & (Y <= b0 + 1), (-5, 5, b0 - 1, b0 + 1, -5, 5))
    # --- the senzu room: a small terracotta temple in the middle and four senzu planters that glow
    for y, r, blk in ((LF + 1, 2, 'cut_red_sandstone'), (LF + 2, 2, 'smooth_red_sandstone'), (LF + 3, 1, 'cut_red_sandstone'),
                      (LF + 4, 1, 'smooth_red_sandstone')):
        v.box(-r, y, -r, r, y, r, blk)
    v.set(0, LF + 5, 0, 'chiseled_red_sandstone')
    for i in range(-2, 3):
        for (x, z, f) in ((i, -3, 'south'), (i, 3, 'north'), (-3, i, 'east'), (3, i, 'west')):
            if x == 3 and abs(z) <= 1:
                continue                                              # the pillar ladder comes up here
            v.set(x, LF + 1, z, f'red_sandstone_stairs[facing={f},half=bottom,shape=straight,waterlogged=false]')
    for cx, cz in ((-6, -6), (6, -6), (-6, 6), (6, 6)):
        v.box(cx - 2, LF + 1, cz - 2, cx + 2, LF + 1, cz + 2, 'smooth_red_sandstone')
        v.walls(cx - 2, LF + 2, cz - 2, cx + 2, LF + 2, cz + 2, 'cut_red_sandstone')
        for ex, ez in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
            v.set(cx + ex, LF + 2, cz + ez, 'shroomlight')
        v.box(cx - 1, LF + 2, cz - 1, cx + 1, LF + 2, cz + 1, 'rooted_dirt')
        for ex in (-1, 0, 1):
            for ez in (-1, 0, 1):
                v.set(cx + ex, LF + 3, cz + ez, 'flowering_azalea' if (ex + ez) % 2 == 0 else 'azalea')
    for k in range(6):
        a = k * math.pi / 3 + 0.5
        v.set(round(9.5 * math.cos(a)), FL - 1, round(9.5 * math.sin(a)), 'lantern[hanging=true,waterlogged=false]')
    # door on the west side of the bowl, opening onto the landing of the outside stair
    v.box(-17, LF + 1, -1, -11, LF + 3, 1, 'air'); v.set(-17, LF + 4, 0, 'air'); v.set(-16, LF + 4, 0, 'air')
    v.box(-21, LF, -2, -14, LF, 2, 'smooth_quartz')
    # --- the open hall: dark wood floor, a pale stone ring, the balcony railing with glowing posts
    v.disc(0, FL, 0, 18.3, 'smooth_quartz')
    v.disc(0, FL, 0, 13.4, 'stripped_dark_oak_log[axis=y]')
    v.disc(0, FL, 0, 12.4, 'dark_oak_planks')
    seen = set()
    for k in range(int(2 * math.pi * 17.4) * 2):
        a = k / (17.4 * 2)
        x, z = round(17.4 * math.cos(a)), round(17.4 * math.sin(a))
        if (x, z) in seen or (x <= -15 and -3 <= z <= 11):
            continue                                                  # gap where the stair leaves the balcony
        seen.add((x, z))
        v.set(x, FL + 1, z, 'birch_fence[east=false,north=false,south=false,west=false,waterlogged=false]')
        if len(seen) % 4 == 0:
            v.set(x, FL + 2, z, 'end_rod[facing=up]')
    # twelve dark-green carved columns holding the roof
    for k in range(12):
        a = k * math.pi / 6 + math.pi / 12
        x, z = round(15.6 * math.cos(a)), round(15.6 * math.sin(a))
        for y in range(FL + 1, FL + 7):
            v.set(x, y, z, 'dark_prismarine' if y in (FL + 1, FL + 3, FL + 6) else 'prismarine_bricks')
        ix, iz = round(14.6 * math.cos(a)), round(14.6 * math.sin(a))
        v.set(ix, FL + 6, iz, 'lantern[hanging=true,waterlogged=false]')
    # --- the roof: a tall round dome with the relief rings of the refs and a wide eave past the balcony
    DB = FL + 7
    H = 13
    def dome_r(Y):
        return 20 * np.sqrt(np.clip(1 - ((Y - DB) / float(H)) ** 2, 0, 1))
    def dome(X, Y, Z):
        return (np.hypot(X, Z) <= dome_r(Y)) & (Y >= DB) & (Y <= DB + H)
    def dome_col(x, y, z, s):
        h = y - DB; a = math.atan2(z, x) % (2 * math.pi); R = max(float(dome_r(y)), 1.0); arc = a * R
        if h == 0:                                                    # flat ceiling ring over the columns, light band
            rr = math.hypot(x, z)
            return 'pearlescent_froglight[axis=y]' if 16.4 < rr < 17.6 else 'dark_oak_planks' if rr < 19 else 'light_gray_concrete'
        if not s:                                                     # inside: dark wood in segments with ribs
            return 'stripped_dark_oak_log[axis=y]' if (a * 12 / (2 * math.pi)) % 1 < 0.1 else 'dark_oak_planks'
        if h == 0:
            return 'light_gray_concrete'
        if 1 <= h <= 3:                                               # big gold triangles over the columns
            d = abs((arc / 3.5) % 2 - 1)
            return 'gold_block' if d < 1 - (h - 0.5) / 3.2 else 'white_concrete'
        if h in (4, 9):
            return 'gray_concrete'                                    # the two dark grooves
        if h == 6:
            return 'gold_block' if arc % 5 < 1.3 else 'white_concrete'      # the round gems
        if h == 7:
            return 'light_gray_concrete' if arc % 4 < 1.2 else 'white_concrete'   # small inverted triangles
        if h == 11:
            return 'gold_block' if arc % 3 < 1 else 'white_concrete'        # the ring of little circles
        return 'white_concrete'
    solid(v, dome, (-21, 21, DB, DB + H, -21, 21), dome_col, fill=True)
    carve(v, lambda X, Y, Z: (np.hypot(X, Z) <= 20 * np.sqrt(np.clip(1 - ((Y - DB) / (H - 2.0)) ** 2, 0, 1)) - 2) & (Y >= DB)
                          & ((Y > DB) | (np.hypot(X, Z) <= 12)),
          (-20, 20, DB, DB + H, -20, 20))
    # stepped spire
    for y, r, blk in ((DB + H, 3.4, 'smooth_quartz'), (DB + H + 1, 2.6, 'gold_block'), (DB + H + 2, 1.9, 'gold_block'),
                      (DB + H + 3, 1.5, 'smooth_quartz'), (DB + H + 4, 1.0, 'gold_block'), (DB + H + 5, 0.4, 'gold_block'),
                      (DB + H + 6, 0.4, 'gold_block')):
        v.disc(0, y, 0, r, blk)
    v.set(0, DB + H + 7, 0, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
    # the cloudy glass ball hanging under the dome
    top_in = DB + H - 3
    for y in range(DB + 5, DB + 11):
        for x in range(-4, 5):
            for z in range(-4, 5):
                d = math.sqrt(x * x + (y - DB - 8) ** 2 + z * z)
                if 1.6 < d <= 2.7:
                    v.set(x, y, z, 'light_gray_stained_glass' if (x * 7 + y * 3 + z * 5) % 4 == 0 else 'white_stained_glass')
    v.set(0, DB + 8, 0, 'sea_lantern')
    for y in range(DB + 11, top_in + 1):
        v.set(0, y, 0, 'chain[axis=y]')
    # --- the Ultra Divine Water: green carved pedestal, two big blue hands holding the gold-and-teal jar
    v.disc(0, FL + 1, 0, 4.2, 'dark_prismarine')
    v.disc(0, FL + 2, 0, 3.2, 'prismarine_bricks')
    for y in range(FL + 3, FL + 7):
        v.disc(0, y, 0, 1.5, 'prismarine_bricks')
        for cx, cz in ((-2, 0), (2, 0), (0, -2), (0, 2)):
            v.set(cx, y, cz, 'prismarine')
    v.disc(0, FL + 7, 0, 2.3, 'dark_prismarine')
    for sx in (-1, 1):                                                # the hands: palms on the jar's sides, fingers over
        v.box(2 * sx, FL + 8, -1, 2 * sx, FL + 10, 1, 'light_blue_concrete')
        v.set(3 * sx, FL + 8, 0, 'light_blue_concrete')
        v.set(2 * sx, FL + 11, 0, 'light_blue_concrete')
        v.set(2 * sx, FL + 9, 0, 'blue_concrete')                     # the dark nail/cuff line of the ref
    v.disc(0, FL + 8, 0, 1.5, 'cyan_terracotta')
    for y in (FL + 9, FL + 10):
        v.disc(0, y, 0, 1.5, 'yellow_concrete')
        v.set(0, y, -1, 'light_blue_concrete'); v.set(0, y, 1, 'light_blue_concrete')
    v.disc(0, FL + 11, 0, 1.2, 'cyan_terracotta')
    v.set(0, FL + 12, 0, 'cyan_concrete')
    # floor lights round the hall
    for k in range(6):
        a = k * math.pi / 3 + 0.5
        v.set(round(8 * math.cos(a)), FL, round(8 * math.sin(a)), 'sea_lantern')
    # the pad to Kami's Lookout, on the balcony (DragonBallPlaces links it)
    v.disc(14, FL, 0, 1.6, 'light_blue_concrete'); v.set(14, FL, 0, 'sea_lantern')
    for c in ((15, 1), (15, -1), (13, 1), (13, -1)):
        v.set(c[0], FL + 1, c[1], 'end_rod[facing=up]')
    # outside stair: from the balcony's west edge down along the bowl to the senzu-room door
    v.box(-20, FL, 8, -17, FL, 11, 'smooth_quartz')
    for i in range(8):
        z, y = 7 - i, FL - 1 - i
        v.box(-20, y, z, -18, y, z, 'quartz_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
        v.box(-20, y - 1, z, -18, y - 1, z, 'smooth_quartz_slab[type=top,waterlogged=false]')
        v.set(-21, y + 1, z, 'birch_fence[east=false,north=false,south=false,west=false,waterlogged=false]')
    for z in range(-2, 3):
        v.set(-22, LF + 1, z, 'birch_fence[east=false,north=false,south=false,west=false,waterlogged=false]')
    v.set(-22, LF + 2, -2, 'lantern[hanging=false,waterlogged=false]'); v.set(-22, LF + 2, 2, 'lantern[hanging=false,waterlogged=false]')
    # ladders: up the pillar into the senzu room, then from the senzu room up through a trapdoor into the hall
    ladder(v, 3, 0, 0, LF, facing='east', support='cut_sandstone')
    v.set(3, LF, 0, 'air')
    ladder(v, 3, 0, 0, LF, facing='east', support='cut_sandstone')
    ladder(v, 0, 9, LF + 1, FL - 1, facing='north', support='smooth_sandstone')
    trapdoor(v, 0, FL, 9, facing='south')
    return v


# ---------------------------------------------------------------------------------------------------------------
def babidi_ship():
    """Babidi's spaceship (ref 5). Above ground only the small white egg 'head': red crest along the top, the M on the
    front, the oval door, portholes and two side lobes, in a ring of rubble. Underground the big ship of the manga's
    cutaway: the entrance hall, Stages 1-4 stacked (domed ceilings, each floor its color), the Command Center (wide
    dome, blue floor, the gold ring), and at the bottom Buu's shell. Side towers, legs, ladders between the floors."""
    v = Vox(); rnd = random.Random(55)
    # ---- underground hull (built first, the rooms are carved after) ----
    hull = lambda x, y, z, s: ('light_gray_concrete' if y % 7 == 0 else 'white_concrete') if s else 'smooth_stone'
    def core(X, Y, Z):
        r = np.hypot(X, Z)
        top = np.where(Y > -10, 12 * np.sqrt(np.clip(1 - ((Y + 10) / 10.0) ** 2, 0, 1)), 12.5)
        return (r <= top) & (Y >= -46) & (Y <= -1)
    solid(v, core, (-14, 14, -47, 0, -14, 14), hull, fill=True)
    for sz in (-1, 1):                                                   # side towers
        def tower(X, Y, Z, sz=sz):
            top = np.where(Y > -18, 5 * np.sqrt(np.clip(1 - ((Y + 18) / 6.0) ** 2, 0, 1)), 5)
            return (np.hypot(X, Z - sz * 15) <= top) & (Y >= -44) & (Y <= -12)
        solid(v, tower, (-6, 6, -45, -11, sz * 15 - 6, sz * 15 + 6), hull, fill=True)
    def saucer(X, Y, Z):
        return (X / 22.0) ** 2 + (Z / 22.0) ** 2 + ((Y + 50) / 8.0) ** 2 <= 1
    solid(v, saucer, (-23, 23, -59, -41, -23, 23), hull, fill=True)
    solid(v, lambda X, Y, Z: X ** 2 + Z ** 2 + (Y + 61) ** 2 <= 8.5 ** 2, (-9, 9, -70, -52, -9, 9), hull, fill=True)
    for lx, lz in ((1, 1), (-1, 1), (1, -1), (-1, -1)):                 # legs, slanting out
        for i in range(16):
            x, y, z = round(lx * (14 + i * 0.6)), -50 - i, round(lz * (14 + i * 0.6))
            v.box(x - 1, y, z - 1, x + 1, y, z + 1, 'light_gray_concrete')
    # ---- rooms ----
    def room(cy, r, h, floor, wall='white_concrete', ceil='smooth_sandstone', windows='blue_concrete'):
        """Round room: floor at cy-1, air cy..cy+h, domed ceiling; window panels around the wall."""
        def ins(X, Y, Z):
            d = np.hypot(X, Z)
            rr = np.where(Y < cy + h - 3, r, r * np.sqrt(np.clip(1 - ((Y - (cy + h - 3)) / 3.5) ** 2, 0, 1)))
            return (d <= rr) & (Y >= cy) & (Y <= cy + h)
        carve(v, ins, (-r - 1, r + 1, cy, cy + h, -r - 1, r + 1))
        for x in range(-r - 1, r + 2):
            for z in range(-r - 1, r + 2):
                d = math.hypot(x, z)
                if d <= r + 0.5:
                    v.set(x, cy - 1, z, floor(x, z, d))
                # wall ring + ceiling skin
                for y in range(cy, cy + h + 2):
                    if v.get(x, y, z) not in (None, 'minecraft:air') and r - 0.5 < d <= r + 1.5 and y < cy + h - 2:
                        a = math.atan2(z, x)
                        win = (y in (cy + 1, cy + 2, cy + 3)) and ((a * 8 / math.pi) % 2 < 1.3)
                        v.set(x, y, z, windows if win else wall)
        for x in range(-r - 1, r + 2):
            for z in range(-r - 1, r + 2):
                for y in range(cy + h - 3, cy + h + 3):
                    if v.get(x, y, z) not in (None, 'minecraft:air') and v.get(x, y - 1, z) == 'minecraft:air':
                        v.set(x, y, z, ceil)
        v.set(0, cy + h, 0, 'sea_lantern')
        for k in range(6):
            a = k * math.pi / 3
            v.set(round((r - 2) * math.cos(a)), cy + h - 3, round((r - 2) * math.sin(a)), 'sea_lantern')
    plain = lambda c: (lambda x, z, d: c)
    ringed = lambda c, c2: (lambda x, z, d: c2 if abs(d - 5) < 0.6 else c)
    room(-8, 8, 6, plain('light_blue_concrete'))                                         # entrance hall / flight deck
    stages = [(-16, 'orange_terracotta', 'yellow_terracotta'), (-24, 'magenta_concrete', 'purple_concrete'),
              (-32, 'orange_concrete', 'yellow_concrete'), (-40, 'lime_concrete', 'green_concrete')]
    for cy, c1, c2 in stages:                                                            # Stages 1-4
        room(cy, 9, 6, ringed(c1, c2))
    def cc_floor(x, z, d):
        if d <= 2.2:
            return 'blue_concrete'
        if d <= 4.2:
            return 'gold_block' if d > 3.2 else 'yellow_concrete'
        a = math.atan2(z, x)
        return 'light_blue_concrete' if (a * 12 / math.pi) % 2 < 0.25 else 'blue_concrete'
    room(-53, 17, 8, cc_floor, ceil='brown_terracotta', windows='black_concrete')       # Command Center
    room(-66, 6, 6, plain('packed_mud'), ceil='light_gray_concrete')                    # Buu's shell room
    solid(v, lambda X, Y, Z: X ** 2 + (Y + 63) ** 2 + Z ** 2 <= 3.3 ** 2, (-4, 4, -67, -59, -4, 4),
          lambda x, y, z, s: 'pink_concrete' if y in (-63,) else 'white_concrete', fill=True)
    # two white capsule pods with the M at the sides of the Command Center (ref)
    for sz in (-1, 1):
        solid(v, lambda X, Y, Z, sz=sz: ((X) / 2.6) ** 2 + ((Y + 50) / 4.0) ** 2 + ((Z - sz * 13) / 2.6) ** 2 <= 1,
              (-3, 3, -53, -46, sz * 13 - 3, sz * 13 + 3), lambda x, y, z, s: 'white_concrete', fill=True)
    # side tower quarters (warriors' quarters / waiting room)
    for sz in (-1, 1):
        for cy in (-40, -30, -22):
            carve(v, lambda X, Y, Z, sz=sz, cy=cy: (np.hypot(X, Z - sz * 15) <= 3.6) & (Y >= cy) & (Y <= cy + 4),
                  (-5, 5, cy, cy + 4, sz * 15 - 5, sz * 15 + 5))
            v.disc(0, cy - 1, sz * 15, 3.6, 'blue_concrete'); v.set(0, cy + 4, sz * 15, 'sea_lantern')
            carve(v, lambda X, Y, Z, sz=sz, cy=cy: (X == 0) & (Y >= cy) & (Y <= cy + 1) & (np.abs(Z - sz * 11) <= 1.5),
                  (0, 0, cy, cy + 1, sz * 11 - 2, sz * 11 + 2))
    # ---- the head above ground: white egg with side lobes ----
    def egg(X, Y, Z):
        k = 1 - 0.28 * np.clip(Y / 11.0, 0, 1)
        return ((X / (6.2 * k)) ** 2 + ((Y + 1) / 11.5) ** 2 + (Z / (6.8 * k)) ** 2 <= 1) & (Y >= -1)
    def lobes(X, Y, Z):
        m = np.zeros(X.shape, bool)
        for sz in (-1, 1):
            m |= ((X - 2) / 4.2) ** 2 + ((Y + 1) / 6.5) ** 2 + ((Z - sz * 6.2) / 3.6) ** 2 <= 1
        return m & (Y >= -1)
    def head_col(x, y, z, s):
        if z == 0 and y >= 3 and x > -3:
            return 'red_concrete'                                     # the crest stripe along the top
        return 'white_concrete'
    solid(v, lambda X, Y, Z: egg(X, Y, Z) | lobes(X, Y, Z), (-8, 8, -1, 12, -11, 11), head_col, air_inside=True)
    # oval door (front = -x)
    for y in range(0, 8):
        for z in range(-3, 4):
            e = ((y - 3.2) / 3.6) ** 2 + (z / 2.4) ** 2
            if e <= 1:
                for x in range(-8, -1):
                    if v.get(x, y, z) and v.get(x, y, z) != 'minecraft:air':
                        v.set(x, y, z, 'air'); v.set(x + 1, y, z, 'air') if v.get(x + 1, y, z) and x + 1 < -3 else None
                        break
            elif e <= 1.55:
                for x in range(-8, -1):
                    if v.get(x, y, z) and v.get(x, y, z) != 'minecraft:air':
                        v.set(x, y, z, 'light_gray_concrete'); break
    # the M (Babidi's mark) above the door, red, on the front face
    M = ['#...#', '##.##', '#.#.#', '#...#']
    for r, line in enumerate(M):
        for c, ch in enumerate(line):
            if ch == '#':
                y, z = 11 - r, c - 2
                for x in range(-8, 2):
                    if v.get(x, y, z) not in (None, 'minecraft:air'):
                        v.set(x, y, z, 'red_concrete'); break
    # portholes on the lobes and the head sides
    for (x, y, sz) in ((0, 2, 1), (3, 2, 1), (0, 2, -1), (3, 2, -1), (-1, 7, 1), (-1, 7, -1), (2, 8, 1), (2, 8, -1)):
        for z in range(sz * 11, 0, -sz):
            if v.get(x, y, z) not in (None, 'minecraft:air'):
                v.set(x, y, z, 'blue_stained_glass'); break
    # floor inside the head with the hatch down
    v.disc(0, -1, 0, 5, 'light_gray_concrete')
    v.set(4, 6, 0, 'sea_lantern')
    # ---- ladders between the floors ----
    ladder(v, 3, 0, -8, -1, facing='west')                 # head -> entrance hall
    ladder(v, 6, 0, -16, -9, facing='west')                # hall -> stage 1
    for top in (-16, -24, -32):                            # stage -> stage
        ladder(v, 7, 0, top - 8, top - 1, facing='west')
    ladder(v, 7, 0, -53, -41, facing='west')               # stage 4 -> command center
    ladder(v, 4, 0, -66, -54, facing='west')               # command center -> Buu's shell
    for x, y in ((3, -1), (6, -9), (7, -17), (7, -25), (7, -33), (7, -41), (4, -54)):
        if v.get(x, y, 0) and 'ladder' not in v.get(x, y, 0):
            v.set(x, y, 0, f'ladder[facing=west,waterlogged=false]')
    # ---- rubble ring around the head ----
    for k in range(260):
        a = rnd.random() * 2 * math.pi; d = 9 + rnd.random() * 9
        x, z = round(d * math.cos(a)), round(d * math.sin(a))
        if v.get(x, 0, z):
            continue
        blk = rnd.choice(['coarse_dirt', 'smooth_red_sandstone', 'terracotta', 'granite', 'packed_mud', 'red_terracotta'])
        v.set(x, -1, z, blk)
        if rnd.random() < 0.5 and 10 < d < 16:
            v.set(x, 0, z, rnd.choice(['granite', 'terracotta', 'coarse_dirt', 'packed_mud']))
            if rnd.random() < 0.25:
                v.set(x, 1, z, 'granite')
    return v


# ---------------------------------------------------------------------------------------------------------------
def goku_house():
    """Goku's house (ref 6): the white capsule dome with its blue window band and the arched wooden door with the orange
    sign, the little round chimney tower with the gold cap, and the wooden annex on the side. Behind it Grandpa Gohan's
    small house: brick base, white walls, teal double door, the dark-blue curved roof; the four-star ball inside."""
    v = Vox(); rnd = random.Random(66)
    # dome house: half ellipsoid 17 x 14, 7 high, door facing +z
    def dome(X, Y, Z):
        return (X / 10.5) ** 2 + (Z / 9.0) ** 2 + (Y / 8.2) ** 2 <= 1
    def dcol(x, y, z, s):
        a = math.atan2(z, x)
        if y == 3 and z > 5 and 3 <= abs(x) <= 7:
            return 'light_blue_stained_glass'                         # long front windows
        if y == 4 and abs(z) <= 1 and abs(x) > 7:
            return 'light_blue_stained_glass'
        return 'white_concrete' if y > 0 else 'white_terracotta'
    solid(v, lambda X, Y, Z: dome(X, Y, Z) & (Y >= 0), (-12, 12, 0, 9, -10, 10), dcol, air_inside=True)
    v.box(-10, -1, -8, 10, -1, 8, 'birch_planks')                    # light plank floor (interior refs)
    # arched door frame + door + orange diamond sign
    for y in range(0, 5):
        for x in range(-2, 3):
            for z in range(5, 11):
                if v.get(x, y, z) and v.get(x, y, z) != 'minecraft:air' and v.get(x, y, z + 1) is None:
                    if abs(x) <= 1 and y <= 3 - (1 if abs(x) == 1 and y == 3 else 0):
                        v.set(x, y, z, 'air')
                    else:
                        v.set(x, y, z, 'light_gray_concrete')
    door(v, 0, 0, 8, 'south', block='spruce_door')
    for (x, y) in ((3, 2), (2, 3), (3, 3), (4, 3), (3, 4)):
        for z in range(11, 0, -1):
            if v.get(x, y, z) not in (None, 'minecraft:air'):
                v.set(x, y, z, 'orange_concrete' if (x, y) != (3, 3) else 'red_concrete'); break
    # chimney tower with the gold cap
    tx, tz = -4, 5
    for y in range(0, 12):
        v.cylinder(tx, y, tz, 1.8, y, 'white_concrete', hollow=False)
    v.set(tx, 9, tz + 2, 'light_blue_stained_glass')
    for y, r in ((12, 2.0), (13, 1.7), (14, 1.0)):
        v.disc(tx, y, tz, r, 'gold_block')
    v.set(tx, 15, tz, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
    # wooden annex on the -x side
    v.box(-18, -1, -5, -10, -1, 5, 'oak_planks')
    v.walls(-18, 0, -5, -10, 5, 5, 'spruce_planks')
    v.box(-17, 0, -4, -11, 5, 4, 'air')
    for x in (-18, -10):
        v.box(x, 0, -5, x, 5, -5, 'stripped_spruce_log[axis=y]'); v.box(x, 0, 5, x, 5, 5, 'stripped_spruce_log[axis=y]')
    v.box(-19, 6, -6, -10, 6, 6, 'smooth_quartz_slab[type=bottom,waterlogged=false]')
    v.box(-18, 6, -5, -11, 6, 5, 'white_concrete')
    door(v, -16, 0, 5, 'south', block='spruce_door')
    v.box(-14, 2, 5, -12, 3, 5, 'blue_stained_glass')
    v.box(-18, 2, -2, -18, 3, 1, 'blue_stained_glass')
    v.box(-11, 0, -2, -9, 2, 2, 'air')                                                   # opening into the dome
    v.set(-14, 0, -3, 'red_wool'); v.set(-14, 0, -2, 'red_wool'); v.set(-16, 0, -3, 'blue_wool'); v.set(-16, 0, -2, 'blue_wool')
    goku_interior(v, dome)
    # Grandpa Gohan's house, behind on the right
    gx, gz = 18, -15
    v.box(gx - 4, -1, gz - 4, gx + 4, -1, gz + 4, 'smooth_stone')
    v.walls(gx - 3, 0, gz - 3, gx + 3, 1, gz + 3, 'bricks')
    v.walls(gx - 3, 2, gz - 3, gx + 3, 4, gz + 3, 'white_concrete')
    v.box(gx - 2, 0, gz - 2, gx + 2, 4, gz + 2, 'air')
    door(v, gx, 0, gz + 3, 'south', block='warped_door', hinge='left'); door(v, gx + 1, 0, gz + 3, 'south', block='warped_door', hinge='right')
    v.set(gx - 2, 2, gz + 3, 'cyan_stained_glass_pane'); v.set(gx + 2, 3, gz + 3, 'red_concrete')
    # curved pagoda roof: concave slopes, corners turned up
    for x in range(-6, 7):
        for z in range(-6, 7):
            m = max(abs(x), abs(z)); c = abs(abs(x) - abs(z)) <= 0 and m >= 5
            h = 5 + (6 - m) ** 2.2 * 0.24
            if m == 6 and not c:
                h = 5
            if c:
                h = 6 + (m - 5)
            v.set(gx + x, round(h), gz + z, 'blue_terracotta')
            if m <= 3:
                v.box(gx + x, 5, gz + z, gx + x, round(h), gz + z, 'blue_terracotta')
            elif round(h) > 5 and m < 6:
                v.set(gx + x, round(h) - 1, gz + z, 'blue_terracotta')
    for y in range(round(5 + 6 ** 2.2 * 0.24), round(5 + 6 ** 2.2 * 0.24) + 4):
        v.set(gx, y, gz, 'blue_concrete')
    v.set(gx, round(5 + 6 ** 2.2 * 0.24) + 4, gz, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
    # the four-star ball on a little red cushion
    v.set(gx, 0, gz - 1, 'dark_oak_planks'); v.set(gx, 1, gz - 1, 'red_carpet')
    v.set(gx, 2, gz - 1, 'shroomlight')
    # yard: path, tree, stones
    for z in range(9, 18):
        for x in (-1, 0, 1):
            v.set(x, -1, z, 'dirt_path')
    for i in range(14):
        v.set(8 + i // 2, -1, -6 - i // 2, 'dirt_path'); v.set(9 + i // 2, -1, -6 - i // 2, 'dirt_path')
    tx, tz = 13, -4
    for y in range(0, 7):
        v.set(tx, y, tz, 'oak_log[axis=y]')
    for x in range(-4, 5):
        for y in range(5, 11):
            for z in range(-4, 5):
                if (x * x + z * z) / 16.0 + ((y - 8) / 3.0) ** 2 <= 1 + rnd.random() * 0.15:
                    v.set(tx + x, y, tz + z, leaves('oak_leaves'))
    return v


def goku_interior(v, dome):
    """Inside the dome (Stark's refs, 2026-11-10): cream curved walls over a dark baseboard, the rosy ceiling with two round
    lamps, light plank floor, the round table with four chairs, the kitchen on the back wall (counter, stove, sink, hood,
    pots on a shelf, white cabinet) and the big white fridge, the bamboo pole floor to ceiling, the dark cabinet with the
    yellow and green vases, and the side window with purple curtains."""
    WALL = ('white_concrete', 'white_terracotta')
    lining = []
    for x in range(-11, 12):
        for y in range(0, 9):
            for z in range(-10, 11):
                if v.get(x, y, z) not in (None, 'minecraft:air') or not dome(x, y, z):
                    continue
                if any(v.get(x + dx, y + dy, z + dz) in ['minecraft:' + w for w in WALL]
                       for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, 0, 1), (0, 0, -1))):
                    lining.append((x, y, z))
    for x, y, z in lining:
        if x < -8 and abs(z) <= 2 and y <= 2:
            continue                                                  # the opening into the wooden annex
        if z > 5 and abs(x) <= 1 and y <= 3:
            continue                                                  # the front door
        v.set(x, y, z, 'stripped_spruce_wood[axis=y]' if y == 0 else 'smooth_sandstone' if y <= 4 else 'cherry_planks')
    v.box(-1, 0, 5, 1, 2, 7, 'air'); v.box(-9, 0, -2, -8, 2, 2, 'air')
    for x, y, z in lining:                                            # keep the windows clear from inside
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if v.get(x + dx, y, z + dz) == 'minecraft:light_blue_stained_glass':
                v.set(x, y, z, 'air')
    # two round ceiling lamps
    for lx in (-3, 3):
        top = max(y for y in range(0, 10) if v.get(lx, y, 0) == 'minecraft:cherry_planks' or y == 0)
        v.set(lx, top, 0, 'pearlescent_froglight[axis=y]'); v.set(lx, top, -1, 'pearlescent_froglight[axis=y]')
    # round table (2x2) and four slatted chairs
    v.box(0, 0, 1, 1, 0, 2, 'oak_slab[type=top,waterlogged=false]')
    chairs = ((0, 0, 'north'), (1, 3, 'south'), (-1, 2, 'west'), (2, 1, 'east'))
    for x, z, f in chairs:
        v.set(x, 0, z, f'oak_stairs[facing={f},half=bottom,shape=straight,waterlogged=false]')
    # kitchen on the back wall
    kz = -6
    for x in range(-5, 3):
        v.set(x, 0, kz, 'stripped_oak_wood[axis=y]')
    v.set(-3, 0, kz, 'smoker[facing=south,lit=false]')                     # stove + oven
    v.set(0, 0, kz, 'water_cauldron[level=3]')                             # sink
    v.set(1, 1, kz, 'decorated_pot[cracked=false,facing=south,waterlogged=false]')
    v.box(-4, 3, kz, -2, 3, kz, 'smooth_quartz_slab[type=top,waterlogged=false]')    # hood over the stove
    v.box(-1, 2, kz - 1, 1, 2, kz - 1, 'spruce_planks')                    # pot shelf
    v.set(-1, 3, kz - 1, 'cauldron'); v.set(1, 3, kz - 1, 'flower_pot')
    v.box(0, 3, kz, 2, 3, kz, 'quartz_block')                              # white wall cabinet
    # big white fridge
    v.box(4, 0, kz, 5, 2, kz, 'quartz_block')
    v.set(4, 1, kz + 1, 'lever[face=wall,facing=south,powered=false]')
    # the bamboo pole
    for y in range(0, 9):
        if v.get(-6, y, -4) in (None, 'minecraft:air'):
            v.set(-6, y, -4, 'bamboo_block[axis=y]')
        else:
            break
    # dark cabinet with two vases on the right wall
    v.box(7, 0, -3, 7, 0, -2, 'dark_oak_planks')
    v.set(7, 1, -3, 'potted_dandelion'); v.set(7, 1, -2, 'potted_fern')
    # purple curtains each side of the right window
    for z in (-2, 2):
        for x in range(10, 0, -1):
            if v.get(x, 4, z) in (None, 'minecraft:air') and v.get(x + 1, 4, z) not in (None, 'minecraft:air'):
                v.set(x, 4, z, 'purple_wall_banner[facing=west]'); break


# ---------------------------------------------------------------------------------------------------------------
def time_chamber():
    """The Room of Spirit and Time (Stark's refs, 2026-11-10): in its own all-white dimension, the T-shaped platform of
    light-blue tiles with three rounded steps at the front; the round house in the middle (white walls with columns and
    glass, the gold dome with its spike and the clock over the door; bed, table and chairs inside), a white wing with a
    rounded pink roof and purple windows on each side, the two giant hourglasses of green sand with gold caps and bases at
    the ends, and the gold lamp posts with their pale globes. Origin = first air above the white floor."""
    v = Vox(); rnd = random.Random(365)
    tile = lambda x, z: 'light_blue_wool' if x % 4 == 0 or z % 4 == 0 else 'light_blue_concrete'
    def rrect(x, z, x0, x1, z0, z1, r):
        cx = min(max(x, x0 + r), x1 - r); cz = min(max(z, z0 + r), z1 - r)
        return (x - cx) ** 2 + (z - cz) ** 2 <= r * r + 0.3
    # the back bar (wings + hourglasses) and the front stem with its steps
    for x in range(-36, 37):
        for z in range(-14, 26):
            bar = rrect(x, z, -36, 36, -14, 3, 5)
            top = rrect(x, z, -16, 16, -14, 12, 6) or bar
            s1 = rrect(x, z, -18, 18, -14, 16, 7)
            s2 = rrect(x, z, -20, 20, -14, 20, 8)
            h = 2 if top else 1 if s1 else 0 if s2 else None
            if h is None:
                continue
            for y in range(0, h + 1):
                v.set(x, y, z, tile(x, z) if y == h else 'light_blue_concrete')
    # the round house: drum of columns and glass, white wall at the back, gold dome, spike, the clock over the door
    hz = -5; R = 7
    v.disc(0, 2, hz, R + 0.5, 'smooth_quartz')
    for k in range(int(2 * math.pi * R)):
        a = k / R; x, z = round(R * math.cos(a)), hz + round(R * math.sin(a))
        back = z < hz - 2
        for y in range(3, 9):
            v.set(x, y, z, 'white_concrete' if back or y in (3, 8) else 'glass_pane[east=true,north=true,south=true,west=true,waterlogged=false]')
    for k in range(10):                                                            # columns all round the front
        a = math.pi * (0.05 + 0.9 * k / 9)
        x, z = round(R * math.cos(a)), hz + round(R * math.sin(a))
        v.box(x, 3, z, x, 8, z, 'quartz_pillar[axis=y]')
    v.box(-1, 3, hz + R, 1, 6, hz + R, 'air')                                       # the door opening
    v.box(-1, 3, hz + R - 1, 1, 6, hz + R - 1, 'air')
    v.disc(0, 9, hz, R + 1.2, 'smooth_quartz')                                      # cornice
    def dome(X, Y, Z):
        return (X ** 2 + (Z - hz) ** 2) / (R + 0.6) ** 2 + ((Y - 9) / 6.5) ** 2 <= 1
    solid(v, lambda X, Y, Z: dome(X, Y, Z) & (Y >= 10), (-9, 9, 10, 17, hz - 9, hz + 9),
          lambda x, y, z, s: 'gold_block' if (math.atan2(z - hz, x) * 12 / math.pi) % 2 < 0.35 else 'yellow_concrete')
    v.disc(0, 16, hz, 1.5, 'gold_block'); v.set(0, 17, hz, 'gold_block'); v.set(0, 18, hz, 'gold_block')
    v.set(0, 19, hz, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
    # clock: a white gable over the door with a big round face, black rim and hands (at a quarter past twelve)
    for x in range(-4, 5):
        for y in range(9, 18):
            if (x / 4.3) ** 2 + ((y - 13) / 4.3) ** 2 <= 1:
                for z in (hz + R, hz + R + 1):
                    v.set(x, y, z, 'white_concrete')
    for x in range(-4, 5):
        for y in range(9, 18):
            d = math.hypot(x, y - 13)
            if d <= 3.4:
                v.set(x, y, hz + R + 2, 'black_concrete' if d > 2.6 else 'smooth_quartz')
    for y in (13, 14, 15):
        v.set(0, y, hz + R + 2, 'black_concrete')                                  # minute hand up
    v.set(1, 13, hz + R + 2, 'black_concrete')                                     # hour hand right
    v.set(0, 9, hz + R + 2, 'gold_block')
    # inside: purple bed, round table and two chairs, rug, lamp
    v.disc(0, 3, hz, 3.0, 'light_blue_carpet')
    v.set(-4, 3, hz - 3, 'purple_bed[facing=north,part=head,occupied=false]'); v.set(-4, 3, hz - 2, 'purple_bed[facing=north,part=foot,occupied=false]')
    v.set(-3, 3, hz - 3, 'purple_bed[facing=north,part=head,occupied=false]'); v.set(-3, 3, hz - 2, 'purple_bed[facing=north,part=foot,occupied=false]')
    v.set(3, 3, hz - 1, 'dark_oak_fence'); v.set(3, 4, hz - 1, 'dark_oak_pressure_plate[powered=false]')
    v.set(4, 3, hz - 1, stairs('dark_oak_stairs', 'east')); v.set(2, 3, hz - 1, stairs('dark_oak_stairs', 'west'))
    v.set(0, 8, hz, 'lantern[hanging=true,waterlogged=false]')
    v.set(5, 3, hz - 4, 'bookshelf'); v.set(5, 4, hz - 4, 'bookshelf')
    # the pad home (TimeChamber links it): by the back wall
    v.set(0, 2, hz - 4, 'sea_lantern')
    for c in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        v.set(c[0], 2, hz - 4 + c[1], 'light_blue_concrete')
    # the wings: white rooms with a rounded pink roof (vault along x), purple oval windows, open at the outer end
    for sx in (-1, 1):
        x0, x1 = sorted((sx * 8, sx * 22))
        z0, z1 = -11, 0
        v.box(x0, 3, z0, x1, 7, z1, 'white_concrete')
        v.box(x0 + 1, 3, z0 + 1, x1 - 1, 7, z1 - 1, 'air')
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                t = (z - (z0 + z1) / 2) / ((z1 - z0) / 2 + 0.5)
                h = 8 + round(3.2 * math.sqrt(max(0, 1 - t * t)))
                v.set(x, h, z, 'pink_concrete')
                for y in range(8, h):
                    v.set(x, y, z, 'white_concrete' if x in (x0, x1) else 'air')
        for x in range(x0 + 3, x1 - 2):                                         # long purple window on the front
            v.set(x, 5, z1, 'purple_stained_glass')
            if x0 + 4 <= x <= x1 - 4:
                v.set(x, 6, z1, 'purple_stained_glass')
        ox = x1 if sx > 0 else x0                                                 # arched doorway at the outer end
        v.box(ox, 3, -6, ox, 5, -4, 'air'); v.set(ox, 6, -5, 'air')
        for z, y in ((-7, 3), (-7, 4), (-7, 5), (-6, 6), (-5, 7), (-4, 6), (-3, 3), (-3, 4), (-3, 5)):
            v.set(ox, y, z, 'smooth_quartz')                                      # white trim round the arch
        for z in range(z0 - 1, z1 + 2):                                           # the pink roof curls over the end
            t = (z - (z0 + z1) / 2) / ((z1 - z0) / 2 + 1.5)
            h = 8 + round(3.2 * math.sqrt(max(0, 1 - t * t)))
            v.set(ox + sx, h, z, 'pink_concrete')
            v.set(ox, h, z, 'pink_concrete')
        for z in (z0 - 1, z1 + 1):
            for x in range(x0, x1 + 1):
                v.set(x, 8, z, 'pink_concrete')                                   # eaves along both sides
        v.box(x0 + 1, 2, z0 + 1, x1 - 1, 2, z1 - 1, 'smooth_quartz')
        v.set(sx * 9, 3, hz, 'air')
        v.box(sx * 8, 3, -6, sx * 8, 5, -4, 'air')                               # opens into the house drum side
    # left wing: kitchen and food; right wing: bath
    for x, b in ((-12, 'smoker[facing=south,lit=false]'), (-13, 'barrel[facing=up,open=false]'), (-14, 'barrel[facing=up,open=false]'),
                 (-15, 'crafting_table'), (-16, 'cauldron'), (-17, 'barrel[facing=up,open=false]')):
        v.set(x, 3, -10, b)
    v.set(-14, 4, -10, 'cake[bites=0]')
    for x in range(13, 19):
        v.set(x, 3, -10, 'water_cauldron[level=3]' if x in (14, 17) else 'smooth_quartz')
    v.set(16, 7, -5, 'lantern[hanging=true,waterlogged=false]'); v.set(-16, 7, -5, 'lantern[hanging=true,waterlogged=false]')
    # giant hourglasses at the ends: gold base, glass bulbs, green sand, gold cap with a spike
    for sx in (-1, 1):
        cx, cz = sx * 29, -6
        v.disc(cx, 3, cz, 4.4, 'gold_block'); v.disc(cx, 4, cz, 3.8, 'gold_block')
        def bulbs(X, Y, Z, cx=cx, cz=cz):
            r = np.where(Y < 12, 3.6 * np.sqrt(np.clip(1 - ((Y - 8.5) / 4.2) ** 2, 0, 1)),
                         3.6 * np.sqrt(np.clip(1 - ((Y - 15.5) / 4.2) ** 2, 0, 1)))
            r = np.maximum(r, 0.9)
            return (np.hypot(X - cx, Z - cz) <= r) & (Y >= 5) & (Y <= 19)
        solid(v, bulbs, (cx - 5, cx + 5, 5, 19, cz - 5, cz + 5), lambda x, y, z, s: 'glass', air_inside=True)
        for y in range(5, 20):                                                   # the green sand: most of it fallen
            r = (3.6 * math.sqrt(max(0, 1 - ((y - 8.5) / 4.2) ** 2))) if y < 12 else 3.6 * math.sqrt(max(0, 1 - ((y - 15.5) / 4.2) ** 2))
            fill = y <= 8 or (12 <= y <= 13)
            if fill:
                v.disc(cx, y, cz, max(0.6, r - 1.1), 'lime_concrete' if y > 6 else 'green_concrete')
        for y in range(9, 12):
            v.set(cx, y, cz, 'lime_stained_glass')                               # the thin stream of sand
        v.disc(cx, 20, cz, 4.4, 'gold_block')                                       # rim, then a round gold dome
        solid(v, lambda X, Y, Z, cx=cx, cz=cz: ((X - cx) ** 2 + (Z - cz) ** 2) / 3.8 ** 2 + ((Y - 21) / 3.2) ** 2 <= 1,
              (cx - 5, cx + 5, 21, 25, cz - 5, cz + 5), lambda x, y, z, s: 'gold_block', fill=True)
        v.set(cx, 25, cz, 'gold_block')
        v.set(cx, 26, cz, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
        # lamp post beyond the hourglass
        lx = sx * 34
        v.box(lx, 3, -6, lx, 9, -6, 'gold_block')
        v.set(lx, 10, -6, 'sea_lantern')
        for c in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            v.set(lx + c[0], 10, -6 + c[1], 'light_blue_stained_glass')
        v.set(lx, 11, -6, 'gold_block')
    return v

BUILDINGS = {'kami_lookout': kami_lookout, 'buu_house': buu_house, 'cell_arena': cell_arena,
             'korin_tower': korin_tower, 'babidi_ship': babidi_ship, 'goku_house': goku_house,
             'time_chamber': time_chamber}

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True); os.makedirs(PREV, exist_ok=True)
    only = sys.argv[2:] or list(BUILDINGS)
    for name in only:
        v = BUILDINGS[name]()
        size, origin = v.save_nbt(os.path.join(OUT, name + '.nbt'))
        sc = 3 if name in ('kami_lookout', 'korin_tower', 'babidi_ship') else 6 if name == 'cell_arena' else 10
        for a in (0, 2):
            v.render(os.path.join(PREV, f'{name}_{a}.png'), scale=sc, angle=a, title=f'{name} {size}')
        if name in ('babidi_ship', 'korin_tower', 'buu_house', 'kami_lookout'):
            cut = Vox(); cut.b = {k: b for k, b in v.b.items() if k[2] <= 0}
            cut.render(os.path.join(PREV, f'{name}_corte.png'), scale=sc, angle=0, title=f'{name} corte')
        print(name, 'size', size, 'origin', origin, 'blocks', len(v.b))
