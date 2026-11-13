"""Dragon Ball buildings, drawn after the anime references Stark sent (2026-11-10).
python3 tools/structures/dragonball.py [out_dir] -> <name>.nbt in src/main/resources/data/battlegroundsmod/structure/dragonball
and a preview <name>.png beside the script output (out_dir/previews)."""
import math, os, sys, random
sys.path.insert(0, os.path.dirname(__file__))
from vox import Vox
from furni import sofa, chair, table, rug, lamp_floor, hang, bed, door, tv, wardrobe, bookshelf_wall, slab, finish

def trapdoor_open(wood, facing, top=False):
    return f'{wood}_trapdoor[facing={facing},half={"top" if top else "bottom"},open=false,powered=false,waterlogged=false]'

ROOT = os.path.join(os.path.dirname(__file__), '..', '..')
OUT = os.path.join(ROOT, 'src/main/resources/data/battlegroundsmod/structure/dragonball')
PREV = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'build', 'previews')

def stairs(block, facing, half='bottom', shape='straight'):
    return f'{block}[facing={facing},half={half},shape={shape},waterlogged=false]'

def leaves(block='jungle_leaves'):
    return f'{block}[distance=1,persistent=true,waterlogged=false]'

# 3x5 letters, rows top to bottom
FONT = {
    'C': ['###', '#..', '#..', '#..', '###'], 'A': ['.#.', '#.#', '###', '#.#', '#.#'], 'P': ['##.', '#.#', '##.', '#..', '#..'],
    'S': ['###', '#..', '###', '..#', '###'], 'U': ['#.#', '#.#', '#.#', '#.#', '###'], 'L': ['#..', '#..', '#..', '#..', '###'],
    'E': ['###', '#..', '##.', '#..', '###'], 'O': ['###', '#.#', '#.#', '#.#', '###'], 'R': ['##.', '#.#', '##.', '#.#', '#.#'],
    'K': ['#.#', '#.#', '##.', '#.#', '#.#'], 'M': ['#.#', '###', '###', '#.#', '#.#'], 'H': ['#.#', '#.#', '###', '#.#', '#.#'],
    ' ': ['...'] * 5,
}

def text_pixels(text):
    """(column, row) of each lit pixel, columns centered on 0, row 0 = top."""
    width = len(text) * 4 - 1
    px = []
    for i, ch in enumerate(text):
        for r, line in enumerate(FONT[ch]):
            for c, on in enumerate(line):
                if on == '#':
                    px.append((i * 4 + c - width // 2, r))
    return px

def palm(v, x, z, y=0, height=None, lean=(1, 0), rnd=random):
    log = 'jungle_log[axis=y]'
    h = height or rnd.randint(7, 10)
    bx = bz = 0
    for j in range(h):
        if j in (h // 3, (2 * h) // 3):
            # the bend: the log under the new column too, so the trunk stays joined (not loose steps)
            v.set(x + bx + lean[0], y + j - 1, z + bz + lean[1], log)
            bx += lean[0]; bz += lean[1]
        v.set(x + bx, y + j, z + bz, log)
    tx, ty, tz = x + bx, y + h, z + bz
    leaf = leaves()
    v.set(tx, ty, tz, leaf); v.set(tx, ty - 1, tz, log)
    for dx, dz in [(1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)]:
        n = 4 if dx == 0 or dz == 0 else 3
        for s in range(1, n + 1):
            droop = 0 if s < n - 1 else -1 if s == n - 1 else -2
            v.set(tx + dx * s, ty + droop, tz + dz * s, leaf)

# ---------------------------------------------------------------------------------------------------------------
def kame_house():
    """Pink wooden house, steep red gable roof (ridge along z), dormer on the left slope, porch with its own little red
    roof at the front right, white trims, shuttered windows, antenna; three palms."""
    v = Vox(); rnd = random.Random(7)
    wall = 'cherry_planks'; trim = 'white_concrete'; roof = 'red_nether_brick'
    X, Z = 6, 7               # walls at x=+-6, z=+-7
    TOP = 7                   # walls y=1..7
    # clear a bit of air inside and above
    v.box(-X, 1, -Z, X, 15, Z, 'air')
    v.box(-X, 0, -Z, X, 0, Z, 'birch_planks')
    v.walls(-X, 0, -Z, X, 0, Z, trim)                                   # white skirting
    v.walls(-X, 1, -Z, X, TOP, Z, wall)
    for cx in (-X, X):
        for cz in (-Z, Z):
            v.box(cx, 1, cz, cx, TOP, cz, trim)                         # white corner boards
    v.box(-X + 1, 4, -Z + 1, X - 1, 4, Z - 1, 'birch_planks')           # upper floor
    v.box(-X + 1, 1, -Z + 1, X - 1, 3, Z - 1, 'air'); v.box(-X + 1, 5, -Z + 1, X - 1, TOP, Z - 1, 'air')
    v.box(-X + 1, 4, -2, -X + 2, 4, -1, 'air')                          # stair hole
    for j in range(1, 4):
        v.set(-X + 1, j, -1 + j - 3 if j < 3 else -1, 'air')
    # gable ends filled up under the roof
    for z in (-Z, Z):
        for x in range(-X, X + 1):
            for y in range(TOP + 1, 14 - abs(x)):
                v.set(x, y, z, wall)
    # roof: stairs 1:1, eaves overhang one block on every side, solid under-layer
    for z in range(-Z - 1, Z + 2):
        for x in range(-X - 1, X + 2):
            y = TOP + (X + 1 - abs(x))
            if x == 0:
                v.set(x, y, z, roof + 's')
                v.set(x, y + 1, z, roof + '_slab[type=bottom,waterlogged=false]')
            else:
                v.set(x, y, z, stairs(roof + '_stairs', 'east' if x < 0 else 'west'))
                if abs(x) < X and abs(z) <= Z:          # not on top of the side walls (seen from inside)
                    v.set(x, y - 1, z, roof + 's')
    # dormer on the left slope
    for z in range(-2, 3):
        for y in range(10, 13):
            v.set(-4, y, z, wall if abs(z) == 2 else trim if y in (10, 12) or abs(z) == 1 else 'glass_pane[east=false,north=true,south=true,west=false,waterlogged=false]')
        for x in range(-3, 0):
            for y in range(10, 13):
                if abs(z) < 2:
                    v.set(x, y, z, 'air')
            v.set(x, 10, z, wall) if abs(z) == 2 else None
            v.set(x, 11, z, wall) if abs(z) == 2 else None
            v.set(x, 12, z, wall) if abs(z) == 2 else None
    for x in range(-5, 0):
        v.set(x, 12, -3, stairs(roof + '_stairs', 'south')); v.set(x, 12, 3, stairs(roof + '_stairs', 'north'))
        v.set(x, 13, -2, stairs(roof + '_stairs', 'south')); v.set(x, 13, 2, stairs(roof + '_stairs', 'north'))
        v.set(x, 14, -1, stairs(roof + '_stairs', 'south')); v.set(x, 14, 1, stairs(roof + '_stairs', 'north'))
        v.set(x, 14, 0, roof + 's'); v.set(x, 15, 0, roof + '_slab[type=bottom,waterlogged=false]')
    for y, w in ((13, 1), (14, 0)):
        for z in range(-w, w + 1):
            v.set(-4, y, z, wall)
    # windows: white frame, glass, shutters (white trapdoors) on the sides
    def window(face, a, y0, w=1, h=2):
        # face: ('x', +-X) or ('z', +-Z); a = center along the wall
        axis, c = face
        for d in range(-w - 1, w + 2):
            for y in range(y0 - 1, y0 + h + 1):
                pos = (c, y, a + d) if axis == 'x' else (a + d, y, c)
                edge = abs(d) == w + 1 or y in (y0 - 1, y0 + h)
                pane = 'glass_pane[east=%s,north=%s,south=%s,west=%s,waterlogged=false]' % (
                    ('false', 'true', 'true', 'false') if axis == 'x' else ('true', 'false', 'false', 'true'))
                v.set(*pos, trim if edge else pane)
        out = 1 if c > 0 else -1
        for d in (-w - 2, w + 2):
            for y in range(y0, y0 + h):
                if axis == 'x':
                    v.set(c + out, y, a + d, 'birch_trapdoor[facing=%s,half=bottom,open=true,powered=false,waterlogged=false]' % ('east' if out > 0 else 'west'))
                else:
                    v.set(a + d, y, c + out, 'birch_trapdoor[facing=%s,half=bottom,open=true,powered=false,waterlogged=false]' % ('south' if out > 0 else 'north'))
    window(('z', Z), -3, 2, w=1)                 # big front window
    window(('z', Z), 0, 9, w=0, h=2)             # little gable window
    window(('z', -Z), 0, 2, w=1)
    window(('z', -Z), 0, 9, w=0, h=2)
    for z in (-4, 3):
        window(('x', X), z, 2, w=0); window(('x', -X), z, 2, w=0)
        window(('x', X), z, 5, w=0); window(('x', -X), z, 5, w=0)
    # door + porch at the front right
    v.set(3, 1, Z, 'birch_door[facing=south,half=lower,hinge=left,open=false,powered=false]')
    v.set(3, 2, Z, 'birch_door[facing=south,half=upper,hinge=left,open=false,powered=false]')
    v.set(3, 3, Z, trim); v.set(2, 1, Z, trim); v.set(2, 2, Z, trim); v.set(4, 1, Z, trim); v.set(4, 2, Z, trim)
    v.box(0, 0, Z + 1, X, 0, Z + 3, 'smooth_quartz')
    v.set(3, 0, Z + 4, stairs('smooth_quartz_stairs', 'north')); v.set(3, -1, Z + 4, 'smooth_quartz')
    for px in (0, X):
        v.box(px, 1, Z + 3, px, 3, Z + 3, 'birch_fence[east=false,north=false,south=false,west=false,waterlogged=false]')
    for x in range(-1, X + 2):
        v.set(x, 4, Z + 1, roof + 's')
        v.set(x, 4, Z + 2, stairs(roof + '_stairs', 'north'))
        v.set(x, 3, Z + 3, stairs(roof + '_stairs', 'north'))
        v.set(x, 4, Z + 3, 'air')
    for x in range(1, X):
        v.set(x, 1, Z + 3, 'air')
    # antenna on the ridge
    v.box(0, 16, -3, 0, 18, -3, 'iron_bars[east=false,north=false,south=false,west=false,waterlogged=false]')
    v.set(0, 19, -3, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
    v.set(-1, 17, -3, 'iron_bars[east=true,north=false,south=false,west=true,waterlogged=false]'); v.set(1, 17, -3, 'iron_bars[east=true,north=false,south=false,west=true,waterlogged=false]')
    # palms and rocks around
    palm(v, 10, 4, height=9, lean=(1, 0), rnd=rnd)
    palm(v, -9, 9, height=8, lean=(-1, 1), rnd=rnd)
    palm(v, 8, -9, height=10, lean=(0, -1), rnd=rnd)
    v.set(-2, 10, 0, 'lantern[hanging=false,waterlogged=false]')
    kame_interior(v, X, Z)
    return v


def kame_interior(v, X, Z):
    """Inside Kame House (Stark's cutaway ref, 2026-11-10; redone 2026-11-12 because it looked bugged: rugs were wool
    blocks set INTO the floor, so they showed on the ceiling below; the upstairs was a bare red brick attic).
    Ground floor: living room at the front window (sofa facing the TV, the old man's tapes, rug, coffee table, lamp),
    kitchen along the back (counter, stove, sink, fridge, wall cabinets, dining table with chairs), a little aquarium,
    the bathroom with its own door, the entrance with a bench and the trapdoor to the secret basement, and a real
    staircase up. Upstairs, under a wooden ceiling that follows the roof: the landing with a reading corner, Lunch's room
    (pink bed, the wardrobe where she hides her guns, vanity) and Kame's room (bed, TV, armchair, magazine shelves)."""
    lam = 'lantern[hanging=true,waterlogged=false]'
    wood = 'spruce'
    # ---------------- floors and ceilings ----------------
    for x in range(-X + 1, X):
        for z in range(-Z + 1, Z):
            v.set(x, 0, z, 'oak_planks')                                       # warm wooden floor downstairs
            v.set(x, 4, z, 'birch_planks')                                     # upstairs floor = ceiling below
    for x in range(-5, 1):                                                     # kitchen tiles
        for z in range(-6, -2):
            v.set(x, 0, z, 'calcite' if (x + z) % 2 else 'polished_andesite')
    # wooden ceiling upstairs following the roof (in place of the roof's red brick under layer, only inside the walls;
    # the dormer stays open)
    def ceil(x):
        return 7 + (X + 1 - abs(x)) - 1
    for z in range(-Z + 1, Z):
        for x in range(-X + 1, X):
            if -3 <= x <= -1 and abs(z) <= 1:
                continue
            v.set(x, ceil(x), z, 'dark_oak_planks')
    # ---------------- staircase on the left wall (replaces the ladder) ----------------
    v.box(-5, 4, -2, -4, 4, -1, 'birch_planks')                                # the old stair hole closed
    for x in (-5, -4):
        v.set(x, 1, 1, stairs(wood + '_stairs', 'north'))
        v.set(x, 1, 0, wood + '_planks'); v.set(x, 2, 0, stairs(wood + '_stairs', 'north'))
        v.set(x, 1, -1, wood + '_planks'); v.set(x, 2, -1, wood + '_planks'); v.set(x, 3, -1, stairs(wood + '_stairs', 'north'))
        for z in (1, 0, -1):
            v.set(x, 4, z, 'air')                                              # the opening above the steps
    v.box(-3, 1, 0, -3, 2, -1, wood + '_planks')                               # side wall of the staircase
    v.set(-3, 3, -1, wood + '_planks')
    # ---------------- kitchen (back left) ----------------
    v.set(-5, 1, -6, 'quartz_block'); v.set(-5, 2, -6, 'quartz_block')        # fridge
    v.set(-5, 2, -5, 'lever[face=wall,facing=south,powered=false]')
    v.set(-5, 3, -6, trapdoor_open(wood, 'south', top=True))
    v.set(-4, 1, -6, 'barrel[facing=up,open=false]')
    v.set(-3, 1, -6, 'smoker[facing=south,lit=false]')
    v.set(-2, 1, -6, 'barrel[facing=up,open=false]')
    v.set(-1, 1, -6, 'water_cauldron[level=3]')                                # sink under the window
    v.set(0, 1, -6, 'barrel[facing=up,open=false]')
    for x in (-4, -3):
        v.set(x, 3, -6, f'{wood}_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]')
    v.set(-4, 2, -6, 'flower_pot'); v.set(0, 2, -6, 'potted_red_tulip')
    v.set(-3, 2, -6, 'air')
    # dining table with four chairs
    v.set(-2, 1, -4, slab('spruce_slab', 'top')); v.set(-1, 1, -4, slab('spruce_slab', 'top'))
    chair(v, -2, 1, -5, 'south', 'birch_stairs'); chair(v, -1, 1, -5, 'south', 'birch_stairs')
    chair(v, -2, 1, -3, 'north', 'birch_stairs'); chair(v, -1, 1, -3, 'north', 'birch_stairs')
    v.set(-2, 3, -4, lam)
    # ---------------- living room (front left, at the big window) ----------------
    tv(v, -5, 1, 4, 'east', w=2)                                               # TV on the left wall (z 4..5)
    v.set(-5, 1, 3, 'chiseled_bookshelf[facing=east,slot_0_occupied=true,slot_1_occupied=true,slot_2_occupied=false,slot_3_occupied=true,slot_4_occupied=true,slot_5_occupied=true]')
    v.set(-5, 2, 3, 'chiseled_bookshelf[facing=east,slot_0_occupied=true,slot_1_occupied=false,slot_2_occupied=true,slot_3_occupied=true,slot_4_occupied=true,slot_5_occupied=false]')   # the tapes
    sofa(v, -1, 1, 5, 'west', n=3, block='crimson_stairs', arm='crimson_trapdoor')   # z 5..3, facing the TV
    rug(v, -4, 3, -2, 6, 1, 'yellow', border='orange')
    v.set(-3, 1, 4, f'{wood}_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]')  # coffee table
    v.set(-5, 1, 6, 'spruce_fence'); v.set(-5, 2, 6, 'lantern[hanging=false,waterlogged=false]')
    v.set(-3, 3, 4, lam)
    # ---------------- aquarium against the bathroom wall ----------------
    v.box(4, 1, -1, 5, 2, 0, 'glass')
    v.set(5, 1, -1, 'sea_pickle[pickles=3,waterlogged=true]')
    # ---------------- bathroom (back right) ----------------
    v.box(2, 1, -6, 5, 3, -3, 'air')
    v.box(1, 1, -6, 1, 3, -2, 'cherry_planks'); v.box(1, 1, -2, 5, 3, -2, 'cherry_planks')
    v.set(3, 1, -2, 'birch_door[facing=north,half=lower,hinge=left,open=false,powered=false]')
    v.set(3, 2, -2, 'birch_door[facing=north,half=upper,hinge=left,open=false,powered=false]')
    v.box(2, 0, -6, 5, 0, -3, 'smooth_quartz')
    v.box(3, 0, -6, 5, 0, -5, 'water')                                         # sunken tub
    v.box(3, 1, -4, 5, 1, -4, 'smooth_quartz_slab[type=bottom,waterlogged=false]')
    v.set(5, 1, -3, 'cauldron'); v.set(5, 2, -3, 'smooth_quartz_slab[type=bottom,waterlogged=false]')   # toilet
    v.set(2, 1, -6, 'water_cauldron[level=3]'); v.set(2, 2, -6, 'quartz_slab[type=top,waterlogged=false]')  # sink
    v.set(2, 1, -3, 'light_blue_carpet')
    v.set(4, 3, -4, lam)
    # ---------------- entrance (front right) ----------------
    v.set(3, 1, 6, 'red_carpet'); v.set(3, 1, 5, 'red_carpet')
    chair(v, 5, 1, 1, 'west', 'spruce_stairs'); chair(v, 5, 1, 2, 'west', 'spruce_stairs')   # bench
    v.set(5, 0, 4, 'spruce_trapdoor[facing=west,half=top,open=false,powered=false,waterlogged=false]')
    v.set(1, 1, 6, 'potted_bamboo'); v.set(5, 1, 6, 'potted_cactus')
    v.set(1, 1, 2, 'spruce_fence'); v.set(1, 2, 2, 'oak_pressure_plate')            # little side table
    v.set(3, 3, 3, lam)
    # ---------------- upstairs ----------------
    # partitions: Kame's room behind x = 0, Lunch's room behind z = 2 (they rise to the sloped ceiling)
    for z in range(-Z + 1, Z):
        for y in range(5, ceil(0)):
            v.set(0, y, z, 'cherry_planks')
    for x in range(-X + 1, 0):
        for y in range(5, ceil(x)):
            v.set(x, y, 2, 'cherry_planks')
    door(v, 0, 5, -4, 'east', 'birch_door')
    door(v, -2, 5, 2, 'south', 'birch_door')
    for z in (-1, 0, 1):
        v.set(-3, 5, z, 'spruce_fence')                                        # railing around the stairs
    # landing / reading corner (back left)
    chair(v, -4, 5, -5, 'east', 'crimson_stairs')
    v.set(-5, 5, -6, 'bookshelf'); v.set(-5, 6, -6, 'bookshelf'); v.set(-4, 5, -6, 'bookshelf')
    v.set(-5, 5, -4, 'potted_azalea_bush')
    rug(v, -4, -5, -2, -3, 5, 'light_gray')
    v.set(-3, 7, -4, lam)
    # Lunch's room (front left)
    bed(v, -5, 5, 5, 'west', 'pink')
    v.set(-5, 5, 6, 'barrel[facing=up,open=false]'); v.set(-5, 6, 6, 'potted_pink_tulip')
    wardrobe(v, -2, 5, 6, 'north', w=2, wood='cherry')                       # where she hides her guns
    rug(v, -4, 3, -3, 4, 5, 'pink')
    v.set(-1, 5, 3, 'cherry_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')   # vanity stool
    v.set(-3, 7, 4, lam)
    # Kame's room (right)
    bed(v, 5, 5, -5, 'east', 'red')
    v.set(5, 5, -6, 'barrel[facing=up,open=false]'); v.set(5, 6, -6, 'lantern[hanging=false,waterlogged=false]')
    v.set(5, 5, -3, 'chest[facing=west,type=single,waterlogged=false]')
    bookshelf_wall(v, 5, -1, 5, 1, 5, 6, facing='west')                          # the magazines
    tv(v, 3, 5, 6, 'north', w=2)
    sofa(v, 3, 5, 3, 'south', n=2, block='spruce_stairs', arm='spruce_trapdoor')   # x 3..2, facing the TV
    rug(v, 1, -2, 4, 2, 5, 'orange', border='red')
    v.set(1, 5, -6, 'potted_dead_bush')
    v.set(3, 7, -1, lam)
    # --- the secret basement under the house: Kame's hidden collection
    v.box(-6, -7, -7, 6, -1, 7, 'stone_bricks')
    v.box(-5, -6, -6, 5, -2, 6, 'air')
    v.box(-5, -7, -6, 5, -7, 6, 'polished_andesite')
    for y in range(-6, 0):
        v.set(5, y, 4, 'ladder[facing=west,waterlogged=false]')
    v.box(-5, -6, -6, -5, -3, 6, 'bookshelf')                                  # the magazine collection
    v.box(-4, -6, -6, 4, -5, -6, 'chiseled_bookshelf[facing=south,slot_0_occupied=true,slot_1_occupied=true,slot_2_occupied=false,slot_3_occupied=true,slot_4_occupied=true,slot_5_occupied=true]')
    for x in (-2, 0, 2):
        v.set(x, -6, 5, 'chest[facing=north,type=single,waterlogged=false]')
    v.set(3, -6, -2, 'anvil[facing=north]'); v.set(3, -6, -3, 'anvil[facing=north]')        # training weights
    v.box(0, -6, -1, 1, -6, 0, 'green_terracotta'); v.set(0, -5, -1, 'green_carpet')       # spare turtle shell
    v.set(-3, -6, 1, 'red_wool'); v.set(-3, -6, 2, 'red_wool')
    for x, z in ((-2, -3), (2, 2), (-2, 3)):
        v.set(x, -2, z, lam)
    # --- outside: beach umbrella, table, deck chair, mailbox
    ux, uz = -11, -2
    v.box(ux, 0, uz, ux, 2, uz, 'birch_fence[east=false,north=false,south=false,west=false,waterlogged=false]')
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if dx * dx + dz * dz <= 5:
                a = math.atan2(dz, dx)
                v.set(ux + dx, 3, uz + dz, 'blue_wool' if int((a + math.pi) / (math.pi / 4)) % 2 else 'white_wool')
    v.set(ux, 4, uz, 'white_wool')
    v.set(ux + 1, 0, uz - 1, 'birch_fence[east=false,north=false,south=false,west=false,waterlogged=false]')
    v.set(ux + 1, 1, uz - 1, 'white_carpet')
    v.set(ux - 1, 0, uz + 2, 'yellow_bed[facing=south,occupied=false,part=head]'); v.set(ux - 1, 0, uz + 1, 'yellow_bed[facing=south,occupied=false,part=foot]')
    v.set(1, 0, Z + 6, 'birch_fence[east=false,north=false,south=false,west=false,waterlogged=false]')
    v.set(1, 1, Z + 6, 'red_terracotta')

# ---------------------------------------------------------------------------------------------------------------
def capsule_corp():
    """The big cream dome, flattened, with long blue window bands following the curve, white cap, two round towers with
    white domes, CAPSULE in blue letters on the front, the round entrance with a red door, palms; round courtyard."""
    v = Vox(); rnd = random.Random(3)
    RX, RY = 24.0, 18.0
    cream = 'smooth_sandstone'; cap = 'white_concrete'; glass = 'blue_stained_glass'; line = 'blue_terracotta'
    R = int(RX) + 1
    v.disc(0, -1, 0, 34, 'smooth_stone'); v.disc(0, -1, 0, 27, 'white_concrete')
    letters = {(c, 12 - r) for (c, r) in text_pixels('CAPSULE')}     # rows y=8..12, above the entrance
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            for y in range(0, int(RY) + 1):
                d = math.sqrt((x / RX) ** 2 + (z / RX) ** 2 + (y / RY) ** 2)
                if d > 1.0:
                    continue
                shell = d > 1.0 - 1.6 / RX * (1 + (y / RY) * 0.6)
                if not shell:
                    v.set(x, y, z, 'air'); continue
                block = cream
                if y >= RY * 0.86:
                    block = cap
                ang = math.atan2(z, x)
                seg = (ang / (2 * math.pi) * 18) % 1.0
                if y in (3, 4, 14, 15) and seg < 0.78:
                    block = glass
                elif y in (2, 13) and seg < 0.82:
                    block = line
                if z > RX * 0.4 and abs(x) < 16 and 6 <= y <= 13:
                    block = cream                          # plain wall behind the letters
                v.set(x, y, z, block)
    # CAPSULE on the outermost block of each column of the front, so the letters stay whole on the curve
    for (c, y) in letters:
        for z in range(R, 0, -1):
            if v.get(c, y, z) not in (None, 'minecraft:air'):
                v.set(c, y, z, 'blue_concrete'); break
    v.box(-R, 0, -R, R, 0, R, 'air'); v.disc(0, 0, 0, RX - 1, 'smooth_stone')
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            if (x / RX) ** 2 + (z / RX) ** 2 <= 1 and (x / RX) ** 2 + (z / RX) ** 2 > (1 - 1.6 / RX) ** 2:
                v.set(x, 0, z, cream)
    # two towers on the upper left and right, a little behind the front
    for tx, tz in ((-11, -3), (11, -3)):
        top = int(RY) + 2
        v.cylinder(tx, 1, tz, 5, top, cream)
        for y in range(1, top + 1):
            for dx in range(-5, 6):
                for dz in range(-5, 6):
                    dd = math.hypot(dx, dz)
                    if dd <= 3.8:
                        v.set(tx + dx, y, tz + dz, 'air')
                    elif dd <= 5 and y in (top - 5, top - 4, top - 1) and (math.atan2(dz, dx) * 8 / math.pi) % 1 < 0.7:
                        v.set(tx + dx, y, tz + dz, glass if y != top - 1 else line)
        v.disc(tx, top + 1, tz, 5.4, line)
        for dx in range(-6, 7):                                   # round white dome on top
            for dz in range(-6, 7):
                for dy in range(0, 6):
                    if math.sqrt(dx * dx + dz * dz + (dy * 1.1) ** 2) <= 5.6:
                        v.set(tx + dx, top + 2 + dy, tz + dz, cap)
    # entrance: little dome in front with a round red door
    ez = int(RX) - 1
    for x in range(-6, 7):
        for z in range(-6, 7):
            for y in range(0, 7):
                d = math.sqrt(x * x + z * z + (y * 1.15) ** 2)
                if d <= 6 and z >= -1:
                    v.set(x, y, ez + z, cream if d > 4.8 else 'air')
    for x in range(-3, 4):
        for y in range(0, 6):
            d = math.hypot(x, y - 2.5)
            if d <= 3.2:
                v.set(x, y, ez + 6, 'red_concrete' if d > 1.8 else 'gray_concrete')
    v.box(-1, 1, ez - 3, 1, 3, ez + 6, 'air')                   # a proper 3 wide hall through the entrance
    for a in range(0, 360, 30):                                   # lights in the floor of the big hall
        r = 15
        v.set(int(round(math.cos(math.radians(a)) * r)), 0, int(round(math.sin(math.radians(a)) * r)), 'sea_lantern')
    v.set(0, 0, 0, 'sea_lantern')
    capsule_interior(v, RX, RY, ez, rnd)
    # path, gate and palms
    for z in range(ez + 7, 34):
        for x in range(-2, 3):
            v.set(x, -1, z, 'smooth_quartz')
    for a in range(0, 360, 30):
        r = 30 + rnd.random() * 2
        x, z = int(round(math.cos(math.radians(a)) * r)), int(round(math.sin(math.radians(a)) * r))
        if abs(x) < 6 and z > 0:
            continue
        palm(v, x, z, height=rnd.randint(7, 10), lean=rnd.choice([(1, 0), (-1, 0), (0, 1), (0, -1)]), rnd=rnd)
    # lights where the rooms stayed dark (2026-11-12 check: block light 0 = monsters could spawn there at night)
    for x, z in ((1, -21), (3, -20), (-3, -20)):
        v.set(x, 0, z, 'sea_lantern')
    for x in (-3, 3):
        v.set(x, 2, 25, 'lantern[hanging=true,waterlogged=false]')
    return v

def capsule_interior(v, RX, RY, ez, rnd):
    """Inside Capsule Corp (Stark's refs, 2026-11-10: the manga cutaway, Bulma's lab, the lab with the time machine, the
    purple hall). Redone 2026-11-12 (it looked bugged and ugly: floors of flat saturated concrete, the garden's dirt
    showing as the ceiling, the pond leaking down, half a bed, furniture made of loose wool blocks).
    Ground floor (ceiling of white panels with lights): the hall at the front (lilac stone floor with a rug, reception
    desk, sofas, plants), and two labs behind it through the orange doors: Bulma's lab (Goku's round ship on its legs
    with a scaffold, the Capsule logo on the floor, workbenches, computers, tool walls) and the general lab (Trunks' time
    machine, server racks, computer desks, shelves). Second floor: the indoor garden (grass, palms, a pond that holds its
    water) and Bulma's room. Third floor: the family lounge under the dome. Ladders in the two towers link the floors."""
    F2, F3 = 8, 14
    CEIL = F2 - 1
    rnd = random.Random(31)          # own random: the palms outside keep their places
    def inner_r(y):
        return RX * math.sqrt(max(0.0, 1 - (y / RY) ** 2)) - 1.8
    def area(y, r=None):
        rr = inner_r(y) if r is None else r
        R = int(rr) + 1
        for x in range(-R, R + 1):
            for z in range(-R, R + 1):
                if x * x + z * z <= rr * rr:
                    yield x, z
    towers = ((-11, -3), (11, -3))
    def in_tower(x, z, pad=0.0):
        return any(math.hypot(x - tx, z - tz) <= 5 + pad for tx, tz in towers)
    def empty(x, y, z):
        return v.get(x, y, z) in (None, 'minecraft:air')
    # ---------------- ground floor: floors ----------------
    for x, z in area(1):
        if in_tower(x, z):
            continue
        if z > 8:        # hall: lilac and white stone, like the purple hall of the refs but soft
            c = 'purpur_block' if (x % 6 == 0 or z % 6 == 0) else 'polished_diorite' if (x // 3 + z // 3) % 2 else 'calcite'
        elif x < 0:      # Bulma's lab: clean white tiles with grey joints
            c = 'light_gray_concrete' if (x % 4 == 0 or z % 4 == 0) else 'smooth_quartz'
        else:            # general lab: workshop floor
            c = 'polished_andesite' if (x % 4 == 0 or z % 4 == 0) else 'smooth_stone'
        v.set(x, 0, z, c)
    # ceiling of white panels with flush lights (the garden's grass sits on top of it, so no dirt shows below)
    for x, z in area(CEIL):
        if in_tower(x, z):
            continue
        v.set(x, CEIL, z, 'ochre_froglight[axis=y]' if (x % 5 == 0 and z % 5 == 0) else 'white_concrete')
    # partitions: hall | labs (z = 8), Bulma's lab | general lab (x = 0), white with a blue stripe
    for x, z in area(1):
        if in_tower(x, z):
            continue
        for y in range(1, CEIL):
            stripe = 'light_blue_concrete' if y == 4 else 'white_concrete'
            if z == 8 and abs(x) > 1:
                v.set(x, y, z, stripe)
            elif z < 8 and x in (-1, 0):
                v.set(x, y, z, stripe if x == -1 else 'white_concrete')
    v.box(-1, 1, 8, 1, 3, 8, 'white_concrete'); v.box(-1, 4, 8, 1, 4, 8, 'light_blue_concrete')
    for dx in (-9, 8):                                                    # the two orange arched doors
        v.box(dx - 1, 1, 8, dx + 2, 5, 8, 'orange_terracotta')
        v.set(dx - 1, 5, 8, 'white_concrete'); v.set(dx + 2, 5, 8, 'white_concrete')
        v.set(dx, 1, 8, 'acacia_door[facing=south,half=lower,hinge=right,open=false,powered=false]')
        v.set(dx, 2, 8, 'acacia_door[facing=south,half=upper,hinge=right,open=false,powered=false]')
        v.set(dx + 1, 1, 8, 'acacia_door[facing=south,half=lower,hinge=left,open=false,powered=false]')
        v.set(dx + 1, 2, 8, 'acacia_door[facing=south,half=upper,hinge=left,open=false,powered=false]')
        v.set(dx, 3, 8, 'orange_terracotta'); v.set(dx + 1, 3, 8, 'orange_terracotta')
    # ---------------- the hall ----------------
    rug(v, -4, 12, 4, 18, 1, 'purple', border='magenta')
    # reception desk facing the entrance
    for x in range(-3, 4):
        v.set(x, 1, 10, 'quartz_block' if abs(x) == 3 else 'white_concrete')
        v.set(x, 2, 10, slab('smooth_quartz_slab', 'bottom'))
    v.set(-1, 2, 10, 'black_stained_glass_pane'); v.set(1, 2, 10, 'black_stained_glass_pane')
    chair(v, 0, 1, 9, 'south', 'quartz_stairs')
    # sofas around low tables on both sides, plants, lamps
    for sx in (-1, 1):
        cx = sx * 13
        sofa(v, cx - 1, 1, 16, 'north', n=3, block='warped_stairs', arm='warped_trapdoor')
        v.set(cx, 1, 13, f'birch_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]')
        v.set(cx - 1, 1, 13, f'birch_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]')
        v.set(cx, 2, 13, 'potted_white_tulip')
        lamp_floor(v, cx + 3 * sx, 1, 16, post='birch_fence')
    for x, z in ((-6, 11), (6, 11), (-17, 10), (17, 10), (-5, 19), (5, 19)):
        if empty(x, 1, z):
            v.set(x, 1, z, 'potted_fern' if (x + z) % 2 else 'potted_bamboo')
    # ---------------- Bulma's lab (left) ----------------
    lx, lz = -13, -15                                                    # the Capsule logo on the floor
    for x in range(-3, 4):
        for z in range(-3, 4):
            d = math.hypot(x, z)
            if d <= 3.2:
                c = 'white_concrete'
                if 1.2 < d <= 2.2 and not (x > 0 and abs(z) <= 1):
                    c = 'blue_concrete'
                v.set(lx + x, 0, lz + z, c)
    sx, sz, sy = -7, -11, 4                                              # Goku's round capsule ship on its legs
    for x in range(-3, 4):
        for y in range(-3, 4):
            for z in range(-3, 4):
                d = math.sqrt(x * x + y * y + z * z)
                if d <= 2.8:
                    a = math.atan2(z, x)
                    c = 'white_concrete'
                    if y == 0:
                        c = 'light_blue_stained_glass' if (a * 4 / math.pi) % 2 < 0.6 and d > 2.2 else 'black_concrete'
                    elif y == -1 or y >= 2:
                        c = 'black_concrete'
                    v.set(sx + x, sy + y, sz + z, c)
    for ex, ez2 in ((2, 2), (-2, 2), (2, -2), (-2, -2)):
        v.set(sx + ex, 1, sz + ez2, 'iron_block'); v.set(sx + ex, 2, sz + ez2, 'light_gray_concrete')
    v.set(sx, 1, sz + 3, slab('smooth_quartz_slab')); v.set(sx, 1, sz + 4, slab('smooth_quartz_slab'))
    for y in range(1, 6):
        v.set(sx + 4, y, sz, 'scaffolding[bottom=false,distance=0,waterlogged=false]')
    # workbenches with tools along the partition, computers with screens
    for z in range(-17, -3, 1):
        if z % 5 == 0:
            continue
        v.set(-2, 1, z, 'iron_block' if z % 3 == 0 else 'polished_andesite')
        v.set(-2, 2, z, 'black_stained_glass_pane' if z % 2 else ('smithing_table' if z % 3 else 'crafting_table'))
    for z in (-15, -10, -5):
        chair(v, -3, 1, z, 'east', 'quartz_stairs')
    v.set(-2, 3, -12, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
    v.box(-20, 1, -9, -18, 1, -9, slab('smooth_stone_slab', 'top'))
    v.set(-19, 2, -9, 'black_stained_glass_pane'); chair(v, -19, 1, -8, 'north', 'quartz_stairs')
    bookshelf_wall(v, -17, -17, -15, -17, 1, 3, facing='south')
    v.set(-20, 1, 2, 'cauldron'); v.set(-19, 1, 3, 'brewing_stand[has_bottle_0=true,has_bottle_1=false,has_bottle_2=true]')
    v.set(-18, 1, 3, 'cartography_table'); v.set(-17, 1, 3, 'fletching_table')
    rug(v, -15, -4, -11, -2, 1, 'light_blue')
    # ---------------- general lab (right): Trunks' time machine ----------------
    tx, tz = 8, -14
    v.disc(tx, 1, tz, 1.3, 'light_blue_concrete')
    for y in (2, 3, 4):
        v.disc(tx, y, tz, 2.0, 'yellow_concrete')
        v.set(tx - 2, y, tz, 'black_concrete'); v.set(tx + 2, y, tz, 'black_concrete')
    v.set(tx, 3, tz + 2, 'black_concrete'); v.set(tx, 4, tz + 2, 'black_concrete')         # the "1" on the front
    v.disc(tx, 5, tz, 2.6, 'gold_block')
    v.disc(tx, 6, tz, 2.0, 'glass'); v.disc(tx, 6, tz, 1.0, 'air'); v.set(tx, 6, tz, 'red_wool')
    for ex, ez2 in ((3, 0), (-3, 0), (0, 3), (0, -3)):                                  # the four thruster pods
        v.set(tx + ex, 1, tz + ez2, 'light_blue_concrete')
        v.set(tx + ex, 2, tz + ez2, 'yellow_concrete'); v.set(tx + ex, 3, tz + ez2, 'yellow_concrete')
        v.set(tx + ex, 4, tz + ez2, 'gold_block')
    for y in range(1, 6):
        v.set(tx - 4, y, tz + 2, 'scaffolding[bottom=false,distance=0,waterlogged=false]')
    # server racks (blinking lights), computer desks, shelves, tool wall
    for z in (-11, -9, -7, -5):
        for y in (1, 2, 3):
            v.set(16, y, z, 'redstone_lamp[lit=true]' if (y + z) % 3 == 0 else 'polished_blackstone' if y != 2 else 'observer[facing=west,powered=false]')
    for z in range(-17, -3):
        if z % 5 == 0:
            continue
        v.set(1, 1, z, 'polished_andesite' if z % 2 else 'barrel[facing=east,open=false]')
        v.set(1, 2, z, 'black_stained_glass_pane' if z % 3 == 0 else 'air')
    for z in (-16, -11, -6):
        chair(v, 2, 1, z, 'west', 'quartz_stairs')
    v.box(15, 1, 5, 17, 1, 5, slab('smooth_stone_slab', 'top')); v.set(16, 2, 5, 'black_stained_glass_pane')
    chair(v, 16, 1, 4, 'south', 'quartz_stairs')
    v.set(14, 1, -16, 'decorated_pot[cracked=false,facing=north,waterlogged=false]')
    bookshelf_wall(v, 4, -19, 6, -19, 1, 2, facing='south')
    v.set(5, 1, 3, 'anvil[facing=north]'); v.set(6, 1, 3, 'grindstone[face=floor,facing=north]'); v.set(7, 1, 3, 'stonecutter[facing=north]')
    # ---------------- second floor: the garden and Bulma's room ----------------
    for x, z in area(F2):
        if in_tower(x, z):
            continue
        if empty(x, F2, z):
            v.set(x, F2, z, 'grass_block[snowy=false]' if math.hypot(x, z) < 13 else 'smooth_quartz')
            if (x % 6 == 0 and z % 6 == 0) and math.hypot(x, z) >= 13:
                v.set(x, F2, z, 'sea_lantern')
    for x, z in area(F2 + 1, 3):                                            # pond: grass and stone all around, ceiling below
        v.set(x + 4, F2, z + 3, 'water')
        v.set(x + 4, CEIL, z + 3, 'white_concrete')
    for x, z in area(F2, 4.2):
        if v.get(x + 4, F2, z + 3) != 'minecraft:water':
            v.set(x + 4, F2, z + 3, 'mossy_cobblestone' if (x + z) % 3 else 'stone')
    v.set(4, F2 + 1, 3, 'lily_pad')
    for (px, pz, h) in ((-6, -5, 4), (9, -6, 4), (-3, 7, 3)):
        palm(v, px, pz, y=F2 + 1, height=h, lean=(1, 0), rnd=rnd)
    for k in range(60):
        x, z = rnd.randint(-12, 12), rnd.randint(-12, 12)
        if math.hypot(x, z) < 12 and empty(x, F2 + 1, z) and v.get(x, F2, z) == 'minecraft:grass_block[snowy=false]':
            v.set(x, F2 + 1, z, rnd.choice(['poppy', 'dandelion', 'azalea', 'short_grass', 'short_grass', 'flowering_azalea', 'blue_orchid', 'oxeye_daisy']))
    for (bx, bz, f) in ((-9, 0, 'east'), (0, -10, 'south')):              # garden benches
        sofa(v, bx, F2 + 1, bz, f, n=2, block='birch_stairs', arm=None)
    for a in range(0, 360, 45):
        x, z = int(round(13.5 * math.cos(math.radians(a)))), int(round(13.5 * math.sin(math.radians(a))))
        if not in_tower(x, z) and empty(x, F2 + 1, z) and not (x < -4 and z > 6):
            lamp_floor(v, x, F2 + 1, z, post='birch_fence')
    # Bulma's room (front left)
    for x, z in area(F2 + 1):
        if x < -4 and z > 6:
            for y in range(F2 + 1, F3):
                if x == -5 or z == 7:
                    v.set(x, y, z, 'pink_concrete' if y != F2 + 3 else 'white_concrete')
            v.set(x, F2, z, 'birch_planks')
    door(v, -5, F2 + 1, 12, 'east', 'birch_door')
    bed(v, -12, F2 + 1, 9, 'north', 'pink')
    v.set(-11, F2 + 1, 8, 'barrel[facing=up,open=false]'); v.set(-11, F2 + 2, 8, 'lantern[hanging=false,waterlogged=false]')
    rug(v, -13, 11, -9, 13, F2 + 1, 'pink', border='white')
    v.box(-15, F2 + 1, 14, -13, F2 + 1, 14, 'white_concrete'); v.set(-14, F2 + 2, 14, 'black_stained_glass_pane')
    chair(v, -14, F2 + 1, 13, 'south', 'quartz_stairs')
    wardrobe(v, -9, F2 + 1, 15, 'north', w=2, wood='birch')
    v.set(-7, F2 + 1, 8, 'potted_pink_tulip')
    v.set(-10, F2 + 4, 11, 'lantern[hanging=true,waterlogged=false]')
    # ---------------- third floor: the lounge under the dome ----------------
    for x, z in area(F3):
        v.set(x, F3, z, 'polished_diorite' if (x // 2 + z // 2) % 2 else 'smooth_quartz')
    for x, z in ((0, 0), (7, 0), (-7, 0), (0, 7), (0, -7)):
        v.set(x, F3, z, 'sea_lantern')
    rug(v, -4, -3, 2, 3, F3 + 1, 'light_blue', border='blue')
    sofa(v, -4, F3 + 1, 2, 'east', n=5, block='red_nether_brick_stairs', arm=None)       # z 2..-2 facing the table
    sofa(v, -2, F3 + 1, -3, 'south', n=3, block='red_nether_brick_stairs', arm=None)
    v.set(-1, F3 + 1, 0, f'dark_oak_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]')
    v.set(-1, F3 + 1, -1, f'dark_oak_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]')
    tv(v, 3, F3 + 1, 1, 'west', w=3)
    v.set(4, F3 + 1, -8, 'brewing_stand[has_bottle_0=true,has_bottle_1=true,has_bottle_2=false]')
    for z in (-7, -8, -9):                                                   # little bar
        v.set(6, F3 + 1, z, 'quartz_block'); v.set(6, F3 + 2, z, slab('dark_oak_slab'))
    chair(v, 5, F3 + 1, -8, 'east', 'dark_oak_stairs')
    for x, z in ((-8, 6), (8, 6), (-9, -5)):
        v.set(x, F3 + 1, z, 'potted_azalea_bush')
    # ---------------- the towers: openings on every floor and a ladder up ----------------
    for tx, tz in towers:
        sx2 = 1 if tx < 0 else -1
        lxx = tx - 3 * sx2                                           # ladder on the outer side of the tower
        for y in (F2, F3):
            v.disc(tx, y, tz, 3.8, 'smooth_stone')
            v.set(lxx, y, tz, 'air')
        v.disc(tx, 0, tz, 3.8, 'smooth_stone')
        for y in range(1, int(RY) + 1):
            v.set(lxx, y, tz, f'ladder[facing={"east" if sx2 > 0 else "west"},waterlogged=false]')
            if empty(lxx - sx2, y, tz):
                v.set(lxx - sx2, y, tz, 'smooth_sandstone')
        for base in (1, F2 + 1, F3 + 1):
            for d in (4, 5):
                for dz in (-1, 0, 1):
                    for y in range(base, base + 3):
                        v.set(tx + sx2 * d, y, tz + dz, 'air')
            v.set(tx, base + 3, tz, 'lantern[hanging=true,waterlogged=false]')
        v.set(tx, 1, tz, 'chest[facing=north,type=single,waterlogged=false]')

# ---------------------------------------------------------------------------------------------------------------
def capsule_ship_wreck():
    """Capsule Corp's round ship (white, wide black belt with round portholes, landing legs, ramp) after a crash:
    sunk in the sand, leaning, one leg broken, a torn hole in the top. Floor of the sand = y 0."""
    v = Vox(); rnd = random.Random(11)
    R = 9.0; cy = 5
    for x in range(-10, 11):
        for y in range(-6, 16):
            for z in range(-10, 11):
                # lean: tilt toward +x by shifting by height
                sx = x - (y - cy) * 0.18
                d = math.sqrt(sx * sx + (y - cy) ** 2 + z * z)
                if d > R:
                    continue
                if d < R - 1.4:
                    v.set(x, y, z, 'air'); continue
                yy = y - cy
                block = 'white_concrete'
                if -3 <= yy <= 1:
                    block = 'black_concrete'
                    ang = math.atan2(z, sx)
                    seg = (ang / (2 * math.pi) * 8) % 1.0
                    if -2 <= yy <= 0 and 0.35 < seg < 0.65:
                        block = 'light_blue_stained_glass' if yy == -1 and 0.42 < seg < 0.58 else 'light_gray_concrete'
                elif yy in (2, -4):
                    block = 'light_gray_concrete'
                v.set(x, y, z, block)
    # floor inside
    v.box(-6, 0, -6, 6, 0, 6, 'air')
    for x in range(-7, 8):
        for z in range(-7, 8):
            if x * x + z * z < 46:
                v.set(x, 0, z, 'smooth_stone')
    # inside Goku's ship (refs): the gravity machine in the middle (white column, the dial panel, the glass ring that
    # glows), the round floor with a blue ring, two bunks, lockers, a console with screens; crash debris and sparks
    for x in range(-7, 8):
        for z in range(-7, 8):
            d = math.hypot(x, z)
            if d * d < 46:
                v.set(x, 0, z, 'blue_concrete' if 3.6 < d < 4.6 else 'light_gray_concrete' if d > 2 else 'smooth_stone')
    for y in range(1, 11):
        if v.get(0, y, 0) not in (None, 'minecraft:air'):
            break
        v.disc(0, y, 0, 1.2, 'light_blue_stained_glass' if y == 4 else 'white_concrete')
        if y == 4:
            v.set(0, y, 0, 'sea_lantern')
    # the gravity machine's panel: dial, screen and buttons on the front of the column
    v.set(0, 2, 2, 'red_concrete'); v.set(0, 3, 2, 'polished_andesite')
    v.set(0, 2, 3, 'stone_button[face=wall,facing=south,powered=false]'); v.set(-1, 3, 2, 'black_stained_glass_pane')
    # bunks along the wall (whole beds), lockers, a console with screens, the emergency lights
    bed(v, -5, 1, 2, 'north', 'white'); bed(v, 5, 1, -2, 'south', 'white')
    v.set(-6, 1, 1, 'iron_block'); v.set(-6, 2, 1, 'redstone_lamp[lit=true]')
    v.set(6, 1, -1, 'iron_block'); v.set(6, 2, -1, 'redstone_lamp[lit=true]')
    for z in (-4, -3):
        v.set(-5, 1, z, 'iron_block'); v.set(-5, 2, z, 'iron_block')
        v.set(-4, 1, z, 'iron_trapdoor[facing=east,half=bottom,open=true,powered=false,waterlogged=false]')
        v.set(-4, 2, z, 'iron_trapdoor[facing=east,half=bottom,open=true,powered=false,waterlogged=false]')
    v.box(4, 1, 3, 5, 1, 3, 'polished_andesite'); v.set(4, 2, 3, 'black_stained_glass_pane'); v.set(5, 2, 3, 'lime_stained_glass_pane')
    chair(v, 4, 1, 2, 'south', 'quartz_stairs')
    # the crash: things thrown about, cobwebs, broken glass, a cable on the floor, a sparking box
    v.set(3, 1, 5, 'gray_concrete'); v.set(2, 1, 5, 'cobweb'); v.set(-3, 1, -5, 'gray_concrete'); v.set(-2, 1, 5, 'light_gray_concrete')
    v.set(-3, 2, -5, 'cobweb'); v.set(1, 1, -5, 'chain[axis=z,waterlogged=false]'); v.set(1, 1, -4, 'chain[axis=z,waterlogged=false]')
    v.set(2, 1, -4, 'iron_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]')
    v.set(-2, 1, 4, 'campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
    v.set(3, 1, -1, 'end_rod[facing=up]'); v.set(-3, 1, 1, 'end_rod[facing=up]')
    # under the floor: filled (a dark hollow there let mobs spawn inside the hull)
    for x in range(-9, 10):
        for z in range(-9, 10):
            for y in range(-6, 0):
                if v.get(x, y, z) == 'minecraft:air':
                    v.set(x, y, z, 'stone')
    # door with ramp on -z side
    for y in range(1, 5):
        for x in range(-1, 2):
            for z in range(-10, -6):
                if v.get(x, y, z) is not None:
                    v.set(x, y, z, 'air')
    for i, z in enumerate(range(-11, -8)):
        for x in range(-1, 2):
            v.set(x, 0 + (1 if i == 2 else 0), z, 'smooth_stone_slab[type=bottom,waterlogged=false]')
    # torn hole in the top, scorched edges
    hx, hy, hz = 4, cy + 7, 3
    for (x, y, z), b in list(v.b.items()):
        e = math.sqrt((x - hx) ** 2 + (y - hy) ** 2 + (z - hz) ** 2)
        if b != 'minecraft:air' and e < 3.2 + rnd.random():
            v.set(x, y, z, 'air')
        elif b != 'minecraft:air' and e < 4.6 and rnd.random() < 0.5:
            v.set(x, y, z, 'gray_concrete' if e < 4.0 else 'light_gray_concrete')
    # legs: three standing (sunk), one broken lying on the sand
    for lx, lz in ((7, 7), (-7, 7), (7, -7)):
        v.box(lx, 0, lz, lx, 2, lz, 'light_gray_concrete')
        v.set(lx, 0, lz, 'iron_block')
    for i in range(4):
        v.set(-9 - i, 0, -9, 'light_gray_concrete' if i < 3 else 'iron_block')
    return v

BUILDINGS = {'kame_house': kame_house, 'capsule_corp': capsule_corp, 'capsule_ship_wreck': capsule_ship_wreck}

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True); os.makedirs(PREV, exist_ok=True)
    only = sys.argv[2:] or list(BUILDINGS)
    for name in only:
        v = BUILDINGS[name]()
        size, origin = v.save_nbt(os.path.join(OUT, name + '.nbt'))
        for a in (0, 2):
            v.render(os.path.join(PREV, f'{name}_{a}.png'), scale=10 if name == 'kame_house' else 6, angle=a, title=f'{name} {size}')
        print(name, 'size', size, 'origin', origin, 'blocks', len(v.b))
