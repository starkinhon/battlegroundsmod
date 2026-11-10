"""Tiny voxel toolkit for the mod's buildings (Stark 2026-11-10: buildings must look like the anime, preview first).

A Vox holds blocks by (x, y, z) -> "minecraft:block[prop=value,...]". save_nbt() writes a vanilla structure file
(the same format as a structure block, so the buildings can also be opened and edited in game); render() draws an
isometric preview PNG to check the shape before it goes in the game.
"""
import gzip, io, math, struct
from PIL import Image, ImageDraw

class Vox:
    def __init__(self):
        self.b = {}

    def set(self, x, y, z, block):
        if block is None or block == 'air':
            self.b[(x, y, z)] = 'minecraft:air'
        else:
            self.b[(x, y, z)] = block if ':' in block else 'minecraft:' + block

    def get(self, x, y, z):
        return self.b.get((x, y, z))

    def box(self, x0, y0, z0, x1, y1, z1, block):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, block)

    def walls(self, x0, y0, z0, x1, y1, z1, block):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, z0, block); self.set(x, y, z1, block)
            for z in range(z0 + 1, z1):
                self.set(x0, y, z, block); self.set(x1, y, z, block)

    def disc(self, cx, y, cz, r, block):
        R = int(math.ceil(r))
        for x in range(-R, R + 1):
            for z in range(-R, R + 1):
                if x * x + z * z <= r * r:
                    self.set(cx + x, y, cz + z, block)

    def cylinder(self, cx, y0, cz, r, y1, block, hollow=True):
        R = int(math.ceil(r))
        for x in range(-R, R + 1):
            for z in range(-R, R + 1):
                d = math.hypot(x, z)
                if d <= r and (not hollow or d > r - 1.2):
                    for y in range(y0, y1 + 1):
                        self.set(cx + x, y, cz + z, block)

    def remove_air(self):
        self.b = {k: v for k, v in self.b.items() if v != 'minecraft:air'}

    # ---- vanilla structure .nbt -------------------------------------------------------------------------------
    def save_nbt(self, path, data_version=3955):
        blocks = {k: v for k, v in self.b.items()}
        xs = [k[0] for k in blocks]; ys = [k[1] for k in blocks]; zs = [k[2] for k in blocks]
        ox, oy, oz = min(xs), min(ys), min(zs)
        size = (max(xs) - ox + 1, max(ys) - oy + 1, max(zs) - oz + 1)
        palette = []; index = {}
        for v in blocks.values():
            if v not in index:
                index[v] = len(palette); palette.append(v)
        out = io.BytesIO()
        w = _NbtWriter(out)
        w.begin_compound('')
        w.int_list('size', size)
        w.begin_list('palette', 10, len(palette))
        for state in palette:
            name, props = _split(state)
            w.string('Name', name)
            if props:
                w.begin_compound('Properties')
                for k, v in props:
                    w.string(k, v)
                w.end()
            w.end()
        w.begin_list('blocks', 10, len(blocks))
        for (x, y, z), v in sorted(blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            w.int_list('pos', (x - ox, y - oy, z - oz))
            w.int('state', index[v])
            w.end()
        w.begin_list('entities', 10, 0)
        w.int('DataVersion', data_version)
        # where the building's origin (0,0,0 = center of its floor) is inside the file, so the mod can place it
        w.begin_compound('battlegroundsmod')
        w.int_list('origin', (-ox, -oy, -oz))
        w.end()
        w.end()
        with gzip.open(path, 'wb') as f:
            f.write(out.getvalue())
        return size, (-ox, -oy, -oz)

    # ---- isometric preview ------------------------------------------------------------------------------------
    def render(self, path, scale=6, angle=0, title=None):
        cols = {}
        pts = []
        ca, sa = [(1, 0), (0, 1), (-1, 0), (0, -1)][angle]
        for (x, y, z), v in self.b.items():
            if v == 'minecraft:air':
                continue
            rx, rz = x * ca - z * sa, x * sa + z * ca
            pts.append((rx, y, rz, v))
        occ = {(p[0], p[1], p[2]) for p in pts}
        s = scale
        def proj(x, y, z):
            return ((x - z) * s * 0.866, (x + z) * s * 0.5 - y * s)
        allp = [proj(x, y, z) for x, y, z, _ in pts]
        minx = min(p[0] for p in allp) - 3 * s; maxx = max(p[0] for p in allp) + 3 * s
        miny = min(p[1] for p in allp) - 3 * s; maxy = max(p[1] for p in allp) + 3 * s
        W, H = int(maxx - minx), int(maxy - miny) + (24 if title else 0)
        img = Image.new('RGB', (W, H), (170, 205, 235))
        d = ImageDraw.Draw(img)
        oy = 24 if title else 0
        pts.sort(key=lambda p: (p[0] + p[2], p[1]))
        for x, y, z, v in pts:
            c = color(v)
            def P(a, b, cc):
                px, py = proj(a, b, cc)
                return (px - minx, py - miny + oy)
            # top
            if (x, y + 1, z) not in occ:
                d.polygon([P(x, y + 1, z), P(x + 1, y + 1, z), P(x + 1, y + 1, z + 1), P(x, y + 1, z + 1)], fill=_shade(c, 1.0))
            if (x, y, z + 1) not in occ:
                d.polygon([P(x, y, z + 1), P(x + 1, y, z + 1), P(x + 1, y + 1, z + 1), P(x, y + 1, z + 1)], fill=_shade(c, 0.78))
            if (x + 1, y, z) not in occ:
                d.polygon([P(x + 1, y, z), P(x + 1, y, z + 1), P(x + 1, y + 1, z + 1), P(x + 1, y + 1, z)], fill=_shade(c, 0.62))
        if title:
            d.text((6, 4), title, fill=(0, 0, 0))
        img.save(path)
        return img

def _shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)

COLORS = {
    'pink_concrete': (214, 101, 143), 'pink_terracotta': (162, 78, 79), 'white_concrete': (207, 213, 214),
    'red_concrete': (142, 33, 33), 'red_nether_bricks': (69, 7, 9), 'red_nether_brick_stairs': (69, 7, 9),
    'red_nether_brick_slab': (69, 7, 9), 'glass_pane': (190, 225, 235), 'glass': (190, 225, 235),
    'light_blue_stained_glass': (100, 160, 220), 'light_blue_stained_glass_pane': (100, 160, 220),
    'blue_stained_glass': (50, 70, 170), 'blue_stained_glass_pane': (50, 70, 170), 'blue_concrete': (45, 47, 143),
    'smooth_sandstone': (223, 214, 170), 'sandstone': (216, 203, 155), 'end_stone_bricks': (218, 224, 162),
    'oak_planks': (162, 130, 78), 'spruce_planks': (114, 84, 48), 'birch_planks': (192, 175, 121),
    'grass_block': (95, 159, 53), 'sand': (219, 207, 163), 'jungle_log': (85, 67, 25), 'jungle_leaves': (48, 120, 30),
    'stone': (125, 125, 125), 'cobblestone': (110, 110, 110), 'iron_bars': (150, 150, 150), 'lightning_rod': (200, 120, 90),
    'black_concrete': (8, 10, 15), 'gray_concrete': (54, 57, 61), 'light_gray_concrete': (125, 125, 115),
    'iron_block': (220, 220, 220), 'quartz_block': (235, 229, 222), 'smooth_quartz': (235, 229, 222),
    'red_terracotta': (143, 61, 46), 'yellow_concrete': (240, 175, 21), 'white_terracotta': (209, 178, 161),
    'birch_fence': (192, 175, 121), 'oak_door': (140, 110, 60), 'birch_door': (220, 210, 170), 'white_wool': (233, 236, 236),
    'smooth_stone': (158, 158, 158), 'smooth_stone_slab': (158, 158, 158), 'quartz_stairs': (235, 229, 222),
    'quartz_slab': (235, 229, 222), 'smooth_quartz_stairs': (235, 229, 222), 'smooth_quartz_slab': (235, 229, 222),
    'cyan_terracotta': (86, 91, 91), 'red_wool': (160, 39, 34), 'polished_andesite': (132, 134, 133),
    'light_gray_concrete_powder': (154, 154, 148), 'sea_lantern': (172, 199, 190), 'flower_pot': (120, 68, 53),
    'white_concrete_powder': (225, 227, 227), 'gray_stained_glass_pane': (75, 75, 75), 'tinted_glass': (44, 38, 46),
    'chain': (60, 65, 80), 'anvil': (70, 70, 70), 'campfire': (230, 140, 40), 'oak_trapdoor': (140, 110, 60),
    'birch_trapdoor': (220, 210, 170), 'white_stained_glass_pane': (240, 240, 240), 'moss_block': (89, 109, 45),
    'oak_leaves': (60, 130, 40), 'cherry_planks': (226, 178, 172), 'blue_terracotta': (74, 60, 91),
    'birch_log': (216, 215, 210), 'jungle_wood': (85, 67, 25), 'red_stained_glass': (170, 50, 50), 'yellow_terracotta': (186, 133, 35),
}

def color(state):
    name = state.split('[')[0].split(':')[1]
    if name in COLORS:
        return COLORS[name]
    for k, v in COLORS.items():
        if name.startswith(k.split('_stairs')[0]):
            return v
    return (255, 0, 255)

def _split(state):
    if '[' not in state:
        return state, []
    name, rest = state.split('[', 1)
    props = [p.split('=') for p in rest.rstrip(']').split(',') if p]
    return name, [(a, b) for a, b in props]

class _NbtWriter:
    def __init__(self, out):
        self.o = out
    def _name(self, tag, name):
        self.o.write(struct.pack('>b', tag)); b = name.encode(); self.o.write(struct.pack('>H', len(b))); self.o.write(b)
    def begin_compound(self, name):
        self._name(10, name)
    def end(self):
        self.o.write(b'\x00')
    def string(self, name, v):
        self._name(8, name); b = v.encode(); self.o.write(struct.pack('>H', len(b))); self.o.write(b)
    def int(self, name, v):
        self._name(3, name); self.o.write(struct.pack('>i', v))
    def int_list(self, name, vals):
        self._name(9, name); self.o.write(struct.pack('>bi', 3, len(vals)))
        for v in vals:
            self.o.write(struct.pack('>i', v))
    def begin_list(self, name, tag, n):
        self._name(9, name); self.o.write(struct.pack('>bi', tag, n))
