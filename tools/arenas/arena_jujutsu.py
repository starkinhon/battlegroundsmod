"""Jujutsu Kaisen arenas. The Shibuya ones are cut from the Shibuya generator of the Jujutsu biome thread
(shibuya/gen.js, copied from forja/jujutsu-shibuya, Stark 2026-11-12: one Shibuya for the whole mod), dumped by
shibuya/dump.js and turned into Minecraft blocks with MAP below."""
import json, math, os, subprocess
import numpy as np
from arenas_all import arena
from lib import Vox, Noise, Pick, terrain, tree, plants, big_tree

HERE = os.path.dirname(__file__)

MAP = {
    'STONE': 'stone', 'SOIL': 'dirt', 'ASPHALT': 'gray_concrete', 'LANE': 'white_concrete', 'YELLOW': 'yellow_concrete',
    'ZEBRA': 'white_concrete', 'SIDEWALK': 'polished_andesite', 'SIDEWALK2': 'andesite', 'TACTILE': 'yellow_terracotta',
    'CURB': 'smooth_stone', 'LOT': 'gray_concrete_powder', 'ALLEY': 'tuff', 'GRAVEL': 'gravel', 'PARK_LINE': 'white_concrete',
    'WALL0': 'polished_diorite', 'WALL1': 'light_gray_concrete', 'WALL2': 'white_terracotta', 'WALL3': 'packed_mud',
    'WALL4': 'quartz_bricks', 'WALL5': 'stone_bricks', 'WALL6': 'smooth_sandstone', 'WALL7': 'polished_andesite',
    'GLASS0': 'light_blue_stained_glass', 'GLASS1': 'cyan_stained_glass', 'GLASS2': 'light_gray_stained_glass',
    'GLASS3': 'gray_stained_glass', 'WIN': 'gray_stained_glass', 'MULLION': 'light_gray_concrete', 'SLAB': 'smooth_stone',
    'CORNER': 'gray_concrete', 'ROOF': 'andesite', 'PARAPET': 'stone_bricks', 'SHOP': 'light_blue_stained_glass',
    'DOOR': 'gray_stained_glass', 'AWNING0': 'red_wool', 'AWNING1': 'blue_wool', 'AWNING2': 'green_wool', 'AWNING3': 'orange_wool',
    'SIGN0': 'red_concrete', 'SIGN1': 'orange_concrete', 'SIGN2': 'yellow_concrete', 'SIGN3': 'lime_concrete',
    'SIGN4': 'cyan_concrete', 'SIGN5': 'blue_concrete', 'SIGN6': 'pink_concrete', 'SIGN7': 'white_concrete',
    'GLYPH': 'white_concrete', 'SIGN_FRAME': 'polished_blackstone', 'SCREEN': 'black_concrete', 'METAL': 'iron_block',
    'METAL_DARK': 'polished_deepslate', 'TANK': 'iron_block', 'AVIATION': 'red_concrete', 'POLE': 'andesite_wall',
    'CABLE': None, 'LAMP': 'sea_lantern', 'TRAFFIC_BOX': 'black_concrete', 'TRAFFIC_R': 'red_concrete', 'TRAFFIC_G': 'lime_concrete',
    'RAILING': 'iron_bars', 'PLANTER': 'dark_oak_planks', 'TRUNK': 'oak_log[axis=y]', 'LEAVES': 'oak_leaves[persistent=true]',
    'LEAVES2': 'dark_oak_leaves[persistent=true]', 'VENDING': 'white_concrete', 'VEND0': 'red_concrete', 'VEND1': 'blue_concrete',
    'VEND2': 'white_concrete', 'VEND3': 'black_concrete', 'TORII': 'red_concrete', 'TORII_TOP': 'polished_blackstone',
    'SHRINE_WOOD': 'spruce_planks', 'SHRINE_ROOF': 'deepslate_tiles', 'LANTERN': 'lantern[hanging=false]',
    'CURSE_STAIN': 'black_concrete', 'CURSE_VEIN': 'crying_obsidian', 'CURSE_GLOW': 'purple_concrete', 'CURSE_FLOOR': 'deepslate_tiles',
    'BOARD': 'spruce_planks', 'TAPE': 'yellow_concrete', 'TALISMAN': 'white_wool', 'DEAD_SIGN': 'black_concrete',
    'TILE_WALL': 'quartz_bricks', 'TILE_LINE': 'orange_concrete', 'TILE_FLOOR': 'smooth_stone', 'CEIL': 'light_gray_concrete',
    'CEIL_LIGHT': 'sea_lantern', 'PILLAR': 'smooth_quartz', 'GATE': 'iron_bars', 'EDGE': 'white_concrete', 'STAIR': 'stone_bricks',
    'BENCH': 'spruce_slab[type=bottom]', 'METRO_SIGN': 'sea_lantern', 'SIGN_DARK': 'blue_terracotta', 'BALLAST': 'gravel',
    'RAIL': 'rail[shape=east_west]', 'TUNNEL': 'andesite', 'TUNNEL_LIGHT': 'sea_lantern', 'TRAIN': 'light_gray_concrete',
    'TRAIN_STRIPE': 'orange_concrete', 'TRAIN_WIN': 'glass', 'TRAIN_DOOR': 'light_gray_concrete', 'TRAIN_ROOF': 'gray_concrete',
    'TRAIN_END': 'light_gray_concrete', 'TRAIN_UNDER': 'black_concrete', 'TRAIN_FRONT': 'black_stained_glass', 'HEADLIGHT': 'sea_lantern',
    'CROWN_LED': 'white_concrete', 'FIN': 'white_concrete', 'DECK_GLASS': 'light_blue_stained_glass', 'LOUVER': 'gray_concrete',
}
# at night what glows in the generator glows here too
NIGHT = {'SHOP': 'ochre_froglight[axis=y]', 'GLYPH': 'sea_lantern', 'TRAFFIC_R': 'redstone_lamp[lit=true]',
         'TRAFFIC_G': 'verdant_froglight[axis=y]', 'AVIATION': 'redstone_lamp[lit=true]', 'CURSE_GLOW': 'crying_obsidian',
         'DOOR': 'ochre_froglight[axis=y]', 'VENDING': 'sea_lantern', 'CROWN_LED': 'sea_lantern'}

def shibuya(seed, cx=0, cy=0, cz=0, ext=104, night=True):
    """Shibuya around (cx, cy, cz), moved so that point is the arena's (0, 0, 0)."""
    path = os.path.join(HERE, 'shibuya', 's%d.json' % seed)
    if not os.path.exists(path):
        subprocess.run(['node', os.path.join(HERE, 'shibuya', 'dump.js'), str(seed), '120', path], check=True)
    d = json.load(open(path))
    v = Vox()
    for x, y, z, name, lit in d['vox']:
        x -= cx; y -= cy; z -= cz
        if x * x + z * z > ext * ext:
            continue
        b = MAP.get(name)
        if night and name in NIGHT:
            b = NIGHT[name]
        if night and lit:
            b = 'ochre_froglight[axis=y]' if lit == 1 else 'sea_lantern'
        if b:
            v.set(x, y, z, b)
    return v, d

@arena('itadori')
def cruzamento_de_shibuya():
    """Shibuya Scramble Crossing on Halloween night: the crossing, the screens and signs, the station entrances."""
    v, d = shibuya(1)
    meta = dict(universo='jujutsu', personagem='Itadori', lugar='Cruzamento de Shibuya', nome='Cruzamento de Shibuya',
                canon='O cruzamento de Shibuya na noite do Halloween: as faixas na diagonal, os telões, as placas acesas e as entradas do metrô.',
                R=36, altura=40, ceu=['#05060c', '#1d2235'], neblina=[60, 190], skyLight=4,
                jogador=[0, 1, 22], desafiante=[0, 1, -20], camLado=[6, 46, 44], alvoY=0)
    return v, meta

@arena('choso')
def plataforma_do_metro():
    """The Fukutoshin line platform under Shibuya: island platform, the stopped train, tunnels running off both ways."""
    v, d = shibuya(2, cy=-15)
    meta = dict(universo='jujutsu', personagem='Choso', lugar='Metrô de Shibuya', nome='Plataforma do metrô',
                canon='A plataforma do metrô embaixo de Shibuya: o trem parado, a plataforma no meio, os trilhos e os túneis seguindo pros dois lados.',
                R=26, altura=7, ceu=['#05060c', '#14161c'], neblina=[30, 110], skyLight=4,
                jogador=[-18, 1, 3], desafiante=[18, 1, 3], camLado=[-34, 4, 2], alvoY=2)
    return v, meta

def ruin(v, n, rng, R0, fire=True, cuts=0, trench=None):
    """Wreck a piece of city: buildings sheared lower toward the middle, scorched tops, rubble, fires, straight slash
    cuts (Sukuna) or a long smooth trench (Gojo's Purple)."""
    cols = {}
    for (x, y, z), b in v.b.items():
        if y > 0:
            cols.setdefault((x, z), []).append(y)
    for (x, z), ys in cols.items():
        r = math.hypot(x, z)
        k = min(1.0, max(0.0, (r - R0 * 0.35) / R0)) ** 1.4
        cap = int(1 + k * 60 + n.f2(x, z, 6, 2) * 6 * k)
        if trench:
            (ax, az), w = trench
            dd = abs(x * az - z * ax)          # distance to the line through the centre
            if dd < w:
                cap = min(cap, -int((1 - (dd / w) ** 2) * 6))
        for y in ys:
            if y > cap:
                v.b.pop((x, y, z), None)
        if cap <= 0:
            for y in range(cap + 1, 1):
                v.b.pop((x, y, z), None)
            v.set(x, cap, z, 'smooth_basalt' if trench else 'blackstone')
        else:
            top = max([y for y in ys if y <= cap], default=None)
            if top and top > 1 and n.f2(x, z, 3) > -0.2:
                v.set(x, top, z, rng.choice(['blackstone', 'basalt[axis=y]', 'cobblestone', 'gravel']))
    # the city's ground is thin: give every column solid rock down to y = -9 so the trench and the crater walls never
    # show the void through them (Stark 2026-11-13: "parte vazando em Shinjuku")
    low = {}
    for (x, y, z) in v.b:
        if (x, z) not in low or y < low[(x, z)]:
            low[(x, z)] = y
    for (x, z), y0 in low.items():
        for y in range(-9, min(y0, 1)):
            if v.b.get((x, y, z)) is None:
                v.set(x, y, z, 'stone')
    # slash cuts: thin vertical planes through everything, straight
    for c in range(cuts):
        a = rng.uniform(0, math.pi); off = rng.uniform(-60, 60); tilt = rng.uniform(-0.6, 0.6)
        ca, sa = math.cos(a), math.sin(a)
        for (x, y, z) in list(v.b.keys()):
            if y < 2:
                continue
            dist = x * sa - z * ca - off + y * tilt
            if abs(dist) < 0.8:
                v.b.pop((x, y, z), None)
    # rubble and fire on the ground
    for i in range(int(R0 * 9)):
        a = rng.uniform(0, 2 * math.pi); r = rng.uniform(4, R0 * 1.3)
        x, z = int(math.cos(a) * r), int(math.sin(a) * r)
        if v.get(x, 1, z) is None and v.get(x, 0, z):
            b = rng.choice(['cobblestone', 'gravel', 'andesite', 'light_gray_concrete', 'cobblestone_slab[type=bottom]', 'iron_bars'])
            v.set(x, 1, z, b)
            if rng.random() < 0.3 and v.get(x, 2, z) is None:
                v.set(x, 2, z, 'cobblestone_slab[type=bottom]')
        elif fire and rng.random() < 0.08 and v.get(x, 1, z) is None and v.get(x, 0, z):
            v.set(x, 0, z, 'netherrack'); v.set(x, 1, z, 'fire[age=0,east=false,north=false,south=false,up=false,west=false]')

@arena('sukuna')
def shibuya_destruida():
    """Shibuya after Sukuna: the blocks round the crossing cut down to stumps, straight slashes through what still
    stands, fires everywhere, the sky black."""
    v, d = shibuya(3)
    n = Noise(8301); rng = np.random.default_rng(8301)
    ruin(v, n, rng, 70, fire=True, cuts=7)
    meta = dict(universo='jujutsu', personagem='Sukuna', lugar='Shibuya destruída', nome='Shibuya destruída',
                canon='Shibuya depois do Sukuna: os quarteirões em volta do cruzamento cortados até o toco, cortes retos atravessando o que sobrou e fogo pra todo lado.',
                R=42, altura=50, ceu=['#090303', '#3a1410'], neblina=[50, 180], skyLight=4,
                jogador=[0, 1, 26], desafiante=[0, 1, -24], camLado=[48, 34, 64], alvoY=8)
    return v, meta

@arena('gojo')
def shinjuku():
    """Shinjuku, the duel of the strongest: a city torn open in daylight, the long smooth trench the Hollow Purple
    dug straight through the blocks, the towers still standing round the edge."""
    v, d = shibuya(4, night=False)
    n = Noise(8401); rng = np.random.default_rng(8401)
    ruin(v, n, rng, 58, fire=False, cuts=0, trench=((0.6, 0.8), 11))
    meta = dict(universo='jujutsu', personagem='Gojo', lugar='Shinjuku', nome='Shinjuku',
                canon='Shinjuku, a luta dos mais fortes: a cidade aberta em plena luz do dia, a vala lisa que o Roxo cavou atravessando os quarteirões e os prédios ainda de pé na beirada.',
                R=44, altura=60, ceu=['#6c9fd6', '#d8e2ea'], neblina=[70, 220],
                jogador=[0, 1, 28], desafiante=[0, 1, -24], camLado=[50, 40, 70], alvoY=6)
    return v, meta


@arena('nobara')
def ponte_yasohachi():
    """Under the Yasohachi bridge (Itadori and Nobara against Eso and Kechizu): a wide shallow river with gravel
    bars, the concrete road bridge on its piers overhead, the embankments and houses of the town on both sides."""
    v = Vox(); n = Noise(8501); rng = np.random.default_rng(8501)
    R = 38
    def hmap(x, z):
        r = math.hypot(x, z)
        if r > 108:
            return None
        bank = abs(x) - 30 - n.f2(x, z, 14) * 4
        if bank < 0:
            return -1 if abs(x) > 12 + n.f2(x, z, 8) * 6 else 0
        return int(min(9, bank * 0.9))
    def top(x, y, z, h):
        if h <= 0:
            return 'gravel' if n.f2(x * 2, z * 2, 5) > -0.3 else 'stone'
        if h >= 8:
            return 'grass_block[snowy=false]'
        return 'stone_bricks' if n.f2(x, z, 4) > -0.2 else 'mossy_stone_bricks'
    H = terrain(v, hmap, -108, 108, -108, 108, top, 'stone', 'stone', min_depth=3)
    for (x, z), h in H.items():
        if h == -1 and abs(x) <= 108 and abs(z) <= 108:
            v.set(x, 0, z, 'water')
    # the bridge: deck across the river at y 14, square piers, railings, street lamps
    for z in range(-7, 8):
        for x in range(-108, 109):
            if abs(z) < 6:
                v.set(x, 14, z, 'gray_concrete' if abs(z) > 1 else 'white_concrete' if x % 6 < 3 else 'gray_concrete')
            else:
                v.set(x, 14, z, 'smooth_stone')
                v.set(x, 15, z, 'iron_bars')
            v.set(x, 13, z, 'light_gray_concrete')
    for px in (-36, -12, 12, 36):
        for y in range(-1, 13):
            for dx in range(-2, 3):
                for dz in range(-5, 6):
                    v.set(px + dx, y, dz, 'light_gray_concrete' if y > 0 else 'stone')
    for x in range(-100, 101, 14):
        for y in range(15, 19):
            v.set(x, y, -7, 'andesite_wall')
        v.set(x, 19, -7, 'sea_lantern')
    # houses of the town up on the banks
    for i in range(36):
        sx = rng.choice([-1, 1]); x = sx * int(rng.uniform(44, 96)); z = int(rng.uniform(-100, 100))
        w, dd, hh = int(rng.integers(3, 6)), int(rng.integers(3, 6)), int(rng.integers(5, 9))
        foot = [H.get((x + dx, z + dz)) for dx in range(-w, w + 1) for dz in range(-dd, dd + 1)]
        if any(f is None for f in foot):
            continue                          # off the edge of the land: no house hanging over nothing
        h = max(foot)
        for dx in range(-w, w + 1):
            for dz in range(-dd, dd + 1):
                for y in range(H[(x + dx, z + dz)] + 1, h + 1):    # stone foundation down to the slope
                    v.set(x + dx, y, z + dz, 'stone_bricks')
                for y in range(1, hh):
                    if abs(dx) == w or abs(dz) == dd:
                        v.set(x + dx, h + y, z + dz, 'white_terracotta' if (y % 3 or abs(dx) == w and abs(dz) == dd) else 'glass_pane')
                k = min(w - abs(dx), dd - abs(dz))
                v.set(x + dx, h + hh + min(2, k), z + dz, rng.choice(['deepslate_tiles', 'blue_terracotta']) if k < 3 else 'deepslate_tiles')
    for i in range(30):
        x = rng.choice([-1, 1]) * int(rng.uniform(40, 100)); z = int(rng.uniform(-100, 100))
        h = H.get((x, z))
        if h and h >= 8 and v.get(x, h + 1, z) is None:
            tree(v, x, h + 1, z, rng, 'oak', height=int(rng.integers(5, 8)))
    plants(v, H, n, (('short_grass', 0.2), ('tall_grass[half=lower]', 0.7)))
    meta = dict(universo='jujutsu', personagem='Nobara', lugar='Ponte Yasohachi', nome='Ponte Yasohachi',
                canon='Embaixo da ponte Yasohachi, onde Itadori e Nobara enfrentaram o Eso e o Kechizu: o rio raso com bancos de cascalho, a ponte de concreto em cima e a cidade nas margens.',
                R=R, altura=13, ceu=['#6a95c4', '#d6dde2'], neblina=[60, 200],
                jogador=[-20, 1, 18], desafiante=[20, 1, -18], camLado=[30, 30, 60], alvoY=4)
    return v, meta

def temple(v, x, y, z, w, d, h, roof='deepslate_tiles', wall='white_terracotta', wood='dark_oak'):
    """A Japanese temple hall: raised wooden floor, posts, white walls, a deep two-slope roof with curved eaves."""
    for dx in range(-w, w + 1):
        for dz in range(-d, d + 1):
            v.set(x + dx, y, z + dz, wood + '_planks')
            if abs(dx) == w or abs(dz) == d:
                for yy in range(y + 1, y + h):
                    post = (abs(dx) == w and dz % 3 == 0) or (abs(dz) == d and dx % 3 == 0)
                    v.set(x + dx, yy, z + dz, (wood + '_log[axis=y]') if post else (wall if yy < y + h - 1 else wood + '_planks'))
    for k in range(0, d + 4):
        for dx in range(-w - 3, w + 4):
            for s in (-1, 1):
                zz = z + s * (d + 3 - k)
                yy = y + h + k // 2 + (1 if k == 0 else 0)
                # two blocks thick so the steps never leave a see-through seam, and solid down to the walls over the
                # hall (Stark 2026-11-13: the school roofs looked like loose floating strips)
                low = y + h - 1 if abs(zz - z) <= d and abs(dx) <= w else yy - 1
                for y2 in range(low, yy + 1):
                    v.set(x + dx, y2, zz, roof)
        if d + 3 - k <= 0:
            break
    for dx in range(-w - 3, w + 4):
        v.set(x + dx, y + h + (d + 3) // 2 + 1, z, 'polished_blackstone')

def torii(v, x, y, z, h=8, w=5, axis='x'):
    for s in (-w, w):
        for yy in range(y, y + h):
            v.set(x + (s if axis == 'x' else 0), yy, z + (s if axis == 'z' else 0), 'red_concrete')
    for k in range(-w - 2, w + 3):
        px, pz = (x + k, z) if axis == 'x' else (x, z + k)
        v.set(px, y + h - 2, pz, 'red_concrete')
        v.set(px, y + h, pz, 'polished_blackstone')
        if abs(k) <= w + 1:
            v.set(px, y + h - 1, pz, 'red_concrete')

def _school(seed, nome, personagem, lugar, canon, kyoto):
    v = Vox(); n = Noise(seed); rng = np.random.default_rng(seed)
    R = 36
    def hmap(x, z):
        r = math.hypot(x, z)
        if r > 108:
            return None
        if r < R + 6:
            return 0
        return int(min(30, (r - R - 6) * 0.35 + max(0, n.f2(x, z, 24, 3)) * 16))
    def top(x, y, z, h):
        if h == 0:
            return 'gravel' if n.f2(x * 2, z * 2, 6) > -0.4 else 'coarse_dirt'
        return 'grass_block[snowy=false]' if n.f2(x, z, 6) > -0.4 else 'moss_block'
    H = terrain(v, hmap, -108, 108, -108, 108, top, 'dirt', 'stone', min_depth=3)
    # stone path from the gate to the main hall
    for z in range(-40, 44):
        for x in range(-2, 3):
            v.set(x, 0, z, 'stone_bricks' if (z % 2 == 0) == (x % 2 == 0) else 'polished_andesite')
    temple(v, 0, 1, -50, 14, 7, 7)
    for i, (x, z) in enumerate(((-34, -20), (34, -20), (-34, 22), (34, 22))):
        temple(v, x, (H.get((x, z)) or 0) + 1, z, 7, 5, 6)
    torii(v, 0, 1, 38)
    for z in range(-30, 34, 9):
        for s in (-6, 6):
            for y in range(1, 3):
                v.set(s, y, z, 'stone_brick_wall')
            v.set(s, 3, z, 'lantern[hanging=false]')
    if kyoto:
        # the five-storey pagoda of Kyoto's school
        px, pz = -48, -46
        for lvl in range(5):
            w = 7 - lvl
            y0 = 1 + lvl * 7
            for dx in range(-w + 2, w - 1):
                for dz in range(-w + 2, w - 1):
                    for y in range(y0, y0 + 5):
                        if abs(dx) == w - 2 or abs(dz) == w - 2:
                            v.set(px + dx, y, pz + dz, 'red_concrete' if (dx + dz) % 3 else 'white_terracotta')
            for dx in range(-w - 1, w + 2):
                for dz in range(-w - 1, w + 2):
                    v.set(px + dx, y0 + 5, pz + dz, 'deepslate_tiles')
                    if abs(dx) == w + 1 or abs(dz) == w + 1:
                        v.set(px + dx, y0 + 4, pz + dz, 'deepslate_tile_slab[type=top]')
        for y in range(35, 46):                       # the spire, standing on the top roof
            v.set(px, y, pz, 'gold_block' if y % 3 else 'polished_blackstone')
        # rows of torii up the hill (Kyoto)
        for k in range(8):
            torii(v, 40, (H.get((40, 40 + k * 5)) or 0) + 1, 40 + k * 5, 6, 3, 'x')
    trees = []
    for i in range(420):
        x, z = (int(a) for a in rng.uniform(-104, 104, 2))
        r = math.hypot(x, z)
        h = H.get((x, z))
        if h is None or r < R + 10 or any((x - a) ** 2 + (z - b) ** 2 < 60 for a, b in trees):
            continue
        if abs(x) < 24 and -62 < z < -38:
            continue
        trees.append((x, z))
        if kyoto and rng.random() < 0.55:
            tree(v, x, h + 1, z, rng, 'oak', height=int(rng.integers(6, 10)), crown=rng.uniform(3, 4.2), leaves_override='acacia_leaves[persistent=true]')
        else:
            tree(v, x, h + 1, z, rng, 'spruce' if rng.random() < 0.6 else 'oak', height=int(rng.integers(9, 16)))
    for (x, z) in ((-22, 14), (22, -12), (-18, -24)):
        tree(v, x, 1, z, rng, 'cherry', height=7, crown=3.6, leaves_override='cherry_leaves[persistent=true]')
    plants(v, H, n, (('short_grass', 0.3), ('fern', -0.55)))
    meta = dict(universo='jujutsu', personagem=personagem, lugar=lugar, nome=nome, canon=canon,
                R=R, altura=40, ceu=['#5d93d4', '#dde6ea'], neblina=[70, 210],
                jogador=[0, 1, 26], desafiante=[0, 1, -24], camLado=[40, 30, 62], alvoY=6,
                tints={'foliage': (214, 98, 40) if kyoto else (70, 140, 50), 'grass': (120, 170, 80)})
    return v, meta

@arena('inumaki')
def escola_de_toquio():
    return _school(8601, 'Escola Jujutsu de Tóquio', 'Inumaki', 'Escola de Tóquio',
                   'A Escola Técnica Jujutsu de Tóquio escondida no morro: o pátio de cascalho, o caminho de pedra até o salão principal, os templos e o torii.', False)

@arena('miwa')
def escola_de_kyoto():
    return _school(8701, 'Escola Jujutsu de Kyoto', 'Miwa', 'Escola de Kyoto',
                   'A Escola Jujutsu de Kyoto: o pagode de cinco andares, os bordos vermelhos do outono, os templos e a fila de torii subindo o morro.', True)

@arena('todo')
def floresta_do_intercambio():
    """The forest of the Goodwill Event, where Todo and Itadori fought and then teamed up against Hanami: a tall
    cedar wood on a slope, a stream with stones, a small shrine, mist between the trunks."""
    v = Vox(); n = Noise(8801); rng = np.random.default_rng(8801)
    R = 36
    def hmap(x, z):
        r = math.hypot(x, z)
        if r > 100:
            return None
        h = n.f2(x, z, 26, 3) * 3 + (max(0, r - R + 6)) * 0.2
        return int(round(h))
    def top(x, y, z, h):
        k = n.f2(x * 2, z * 2, 7, 2)
        return 'podzol[snowy=false]' if k > 0.3 else ('moss_block' if k < -0.35 else 'grass_block[snowy=false]')
    H = terrain(v, hmap, -100, 100, -100, 100, top, 'dirt', 'stone', min_depth=3)
    for z in range(-100, 101):
        cx = -10 + 9 * math.sin(z / 19)
        for x in range(int(cx) - 4, int(cx) + 5):
            h = H.get((x, z))
            if h is None:
                continue
            if abs(x - cx) < 2.4:
                v.set(x, h, z, 'water'); v.set(x, h - 1, z, 'gravel')
                for y in range(h + 1, h + 4):
                    if v.get(x, y, z) and 'water' not in v.get(x, y, z):
                        v.set(x, y, z, None)
            elif n.f2(x, z, 2) > 0.3:
                v.set(x, h + 1, z, 'mossy_cobblestone')
    trees = []
    for (x, z) in ((12, 6), (-24, 14), (20, -16), (-4, -22), (26, 20)):
        big_tree(v, x, (H.get((x, z)) or 0) + 1, z, rng, 'spruce_log', 'spruce_leaves[persistent=true]', height=int(rng.integers(26, 34)), trunk=1.4)
        trees.append((x, z))
    for i in range(600):
        x, z = (int(a) for a in rng.uniform(-96, 96, 2))
        r = math.hypot(x, z)
        h = H.get((x, z))
        if h is None or r < R - 4 or any((x - a) ** 2 + (z - b) ** 2 < 90 for a, b in trees):
            continue
        trees.append((x, z))
        big_tree(v, x, h + 1, z, rng, 'spruce_log', rng.choice(['spruce_leaves[persistent=true]', 'battlegroundsmod:folhas_de_pinheiro_escura']), trunk=rng.uniform(1.2, 2.2))
    # small shrine with its torii
    sx, sz = 18, -30
    sh = H.get((sx, sz)) or 0
    temple(v, sx, sh + 1, sz, 3, 2, 4, roof='spruce_planks', wall='white_wool', wood='spruce')
    torii(v, sx, sh + 1, sz + 9, 6, 3)
    plants(v, H, n, (('fern', 0.2), ('short_grass', -0.4), ('large_fern[half=lower]', 0.7)), surface=('grass_block', 'podzol', 'moss'))
    meta = dict(universo='jujutsu', personagem='Todo', lugar='Floresta do intercâmbio', nome='Floresta do intercâmbio',
                canon='A floresta do Evento de Intercâmbio, onde Todo e Itadori lutaram e depois enfrentaram o Hanami juntos: cedros altos, o riacho, o santuário e a névoa entre os troncos.',
                R=R, altura=34, ceu=['#6f8ea8', '#c8d3cc'], neblina=[30, 120],
                jogador=[0, 1, 24], desafiante=[0, 1, -22], camLado=[6, 30, 36], alvoY=4,
                tints={'grass': (100, 150, 75), 'foliage': (60, 110, 50)})
    return v, meta

@arena('nanami')
def esgoto():
    """The sewers under the city where Nanami fought Mahito: wide brick tunnels crossing at a big round chamber,
    the dirty water channels, walkways, pipes and grates letting a little light down."""
    v = Vox(); n = Noise(8901); rng = np.random.default_rng(8901)
    R = 30
    brick = Pick(n, [('bricks', 3), ('stone_bricks', 3), ('mossy_stone_bricks', 2), ('cracked_stone_bricks', 1)], 4.0)
    CH = 52          # chamber radius (Stark 2026-11-13: room to run from the big techniques)
    def inside(x, y, z):
        r = math.hypot(x, z)
        if r <= CH and -2 <= y <= 14 - (r / CH) ** 2 * 4:
            return True
        for ax in (0, 1, 2):
            a = [(1, 0), (0, 1), (0.707, 0.707)][ax]
            d = abs(x * a[1] - z * a[0])
            along = abs(x * a[0] + z * a[1])
            if along <= 100 and d <= 6 and -2 <= y <= 8 - (d / 6) ** 2 * 3:
                return True
        return False
    for x in range(-100, 101):
        for z in range(-100, 101):
            col = [y for y in range(-3, 16) if inside(x, y, z)]
            if not col:
                continue
            lo, hi = min(col), max(col)
            v.set(x, lo - 1, z, brick.at(x, lo - 1, z))
            v.set(x, hi + 1, z, brick.at(x, hi + 1, z))
            v.set(x, hi + 2, z, 'stone')
            for y in range(lo, hi + 1):
                for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if not inside(x + dx, y, z + dz) and v.get(x + dx, y, z + dz) is None:
                        v.set(x + dx, y, z + dz, brick.at(x + dx, y, z + dz))
            # floor: walkway, channel of water in the middle of the tunnels and a ring channel in the chamber
            r = math.hypot(x, z)
            chan = (CH - 5 < r < CH - 2) or (r > CH and min(abs(x), abs(z), abs(x - z) * 0.707) < 1.6)
            if chan:
                v.set(x, lo - 1, z, 'mud'); v.set(x, lo, z, 'water')
                v.set(x, lo + 1, z, None)
                for y in range(lo + 1, lo + 2):
                    pass
            else:
                v.set(x, lo, z, 'polished_andesite' if (x + z) % 5 else 'stone_bricks')
    # lift the floor so y = 0 is the walkway
    nv = Vox()
    for (x, y, z), b in v.b.items():
        nv.set(x, y + 2, z, b)
    v = nv
    # lamps on the walls, pipes, grates of light from the street
    for a in range(0, 360, 20):
        x, z = int(math.cos(math.radians(a)) * (CH - 0.5)), int(math.sin(math.radians(a)) * (CH - 0.5))
        v.set(x, 6, z, 'lantern[hanging=false]')
    for k in range(-96, 97, 12):
        for (x, z) in ((k, 6), (k, -6), (6, k), (-6, k)):
            if math.hypot(x, z) > CH + 2:
                v.set(x, 5, z, 'lantern[hanging=false]')
    for k in range(-96, 97):
        for (x, z) in ((k, 5), (5, k)):
            if math.hypot(x, z) > CH + 2:
                v.set(x, 7, z, 'copper_block' if k % 9 else 'cut_copper')
    for (x, z) in ((0, 0), (40, 0), (-40, 0), (0, 40), (0, -40)):
        top = 17 if (x, z) == (0, 0) else 11
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                for y in range(top - 1, top + 2):
                    v.set(x + dx, y, z + dz, None)
                v.set(x + dx, top + 2, z + dz, 'iron_bars')
    meta = dict(universo='jujutsu', personagem='Nanami', lugar='Esgoto', nome='Esgoto',
                canon='O esgoto embaixo da cidade onde o Nanami lutou com o Mahito: túneis de tijolo cruzando numa câmara redonda, canais de água suja, passarelas, canos e grades deixando passar um pouco de luz.',
                R=R, altura=12, ceu=['#0a0b0d', '#1a1c1f'], neblina=[25, 100],
                jogador=[0, 1, 16], desafiante=[0, 1, -16], camLado=[12, 9, 18], alvoY=1)
    return v, meta
