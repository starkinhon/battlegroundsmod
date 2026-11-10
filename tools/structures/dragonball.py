"""Dragon Ball buildings, drawn after the anime references Stark sent (2026-11-10).
python3 tools/structures/dragonball.py [out_dir] -> <name>.nbt in src/main/resources/data/battlegroundsmod/structure/dragonball
and a preview <name>.png beside the script output (out_dir/previews)."""
import math, os, sys, random
sys.path.insert(0, os.path.dirname(__file__))
from vox import Vox

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
                if abs(x) <= X and abs(z) <= Z:
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
    """Inside Kame House (Stark's cutaway ref, 2026-11-10). Ground floor: living room with the sofa, the TV, the VCR and
    the old man's tapes; kitchen with counter, stove, sink and fridge on the back wall; bathroom with the tub and toilet
    behind its own door. Upper floor: Lunch's room (pink bed, the wardrobe where she hides her guns) and Kame's room (bed,
    magazines, TV). Outside: the blue and white beach umbrella with the little table and the deck chair, the mailbox.
    Under the house, through a trapdoor in the hall: the secret basement."""
    lam = 'lantern[hanging=true,waterlogged=false]'
    # --- ladder between the floors (through the stair hole on the left)
    for y in range(1, 5):
        v.set(-5, y, -2, 'ladder[facing=east,waterlogged=false]')
    v.set(-5, 4, -1, 'air')
    # --- bathroom (back right): partition, door, sunken tub, toilet, sink
    v.box(1, 1, -6, 1, 3, -2, 'cherry_planks'); v.box(1, 1, -2, 5, 3, -2, 'cherry_planks')
    v.set(3, 1, -2, 'birch_door[facing=north,half=lower,hinge=left,open=false,powered=false]')
    v.set(3, 2, -2, 'birch_door[facing=north,half=upper,hinge=left,open=false,powered=false]')
    v.box(2, 0, -6, 5, 0, -3, 'smooth_quartz')
    v.box(3, 0, -6, 5, 0, -5, 'water')
    v.box(3, 1, -4, 5, 1, -4, 'smooth_quartz_slab[type=bottom,waterlogged=false]')
    v.set(2, 1, -3, 'smooth_quartz_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]')   # toilet
    v.set(2, 1, -6, 'cauldron')                                                                         # sink
    v.set(3, 3, -4, lam)
    # --- kitchen (back left): counter, stove, sink, the fridge
    for x in range(-4, 0):
        v.set(x, 1, -6, 'stripped_birch_wood[axis=y]')
    v.set(-3, 1, -6, 'smoker[facing=south,lit=false]')
    v.set(-1, 1, -6, 'water_cauldron[level=3]')
    v.box(0, 1, -6, 0, 3, -6, 'quartz_block'); v.set(0, 2, -5, 'lever[face=wall,facing=south,powered=false]')
    v.box(-4, 3, -6, -2, 3, -6, 'smooth_quartz_slab[type=top,waterlogged=false]')
    v.set(-2, 1, -3, 'oak_slab[type=top,waterlogged=false]'); v.set(-2, 1, -4, 'oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
    v.set(-3, 3, -3, lam)
    # --- living room (front left): TV + VCR + tapes on the left wall, sofa facing it, coffee table, rug
    v.box(-5, 1, 4, -5, 1, 6, 'dark_oak_slab[type=top,waterlogged=false]')
    v.set(-5, 1, 5, 'dropper[facing=east,triggered=false]')                                             # the VCR
    v.set(-5, 1, 6, 'chiseled_bookshelf[facing=east,slot_0_occupied=true,slot_1_occupied=true,slot_2_occupied=true,slot_3_occupied=true,slot_4_occupied=false,slot_5_occupied=true]')
    v.box(-5, 2, 4, -5, 2, 6, 'black_concrete'); v.set(-5, 2, 5, 'tinted_glass')
    for z in (4, 5, 6):
        v.set(-1, 1, z, 'red_wool')
        v.set(0, 1, z, 'red_wool')
    v.set(-1, 1, 3, 'red_wool'); v.set(-1, 1, 6, 'red_wool')
    v.set(-1, 1, 4, 'red_carpet'); v.set(-1, 1, 5, 'red_carpet')
    v.set(-3, 1, 5, 'oak_slab[type=bottom,waterlogged=false]')
    v.set(-4, 0, 4, 'yellow_wool'); v.set(-3, 0, 4, 'yellow_wool'); v.set(-4, 0, 5, 'yellow_wool'); v.set(-2, 0, 6, 'yellow_wool')
    v.set(-3, 3, 5, lam)
    # --- entrance hall (front right): plant, coat stand, the trapdoor to the secret basement
    v.set(5, 1, 6, 'potted_cactus'); v.set(1, 1, 6, 'potted_bamboo')
    v.set(5, 0, 4, 'spruce_trapdoor[facing=west,half=top,open=false,powered=false,waterlogged=false]')
    v.set(3, 3, 3, lam)
    # --- upper floor: partition between Lunch's room (left) and Kame's room (right)
    v.box(0, 5, -6, 0, 7, 6, 'cherry_planks')
    v.set(0, 5, 2, 'birch_door[facing=east,half=lower,hinge=left,open=false,powered=false]')
    v.set(0, 6, 2, 'birch_door[facing=east,half=upper,hinge=left,open=false,powered=false]')
    # Lunch: pink bed, wardrobe with the hidden guns, a mirror, rug
    v.set(-3, 5, -6, 'pink_bed[facing=north,occupied=false,part=head]'); v.set(-3, 5, -5, 'pink_bed[facing=north,occupied=false,part=foot]')
    v.box(-5, 5, 4, -5, 6, 6, 'spruce_planks'); v.set(-4, 5, 5, 'barrel[facing=west,open=false]')
    v.set(-4, 6, 4, 'dispenser[facing=east,triggered=false]')
    v.box(-4, 4, 0, -2, 4, 2, 'pink_wool')
    v.set(-1, 5, -6, 'spruce_slab[type=top,waterlogged=false]'); v.set(-1, 6, -6, 'potted_pink_tulip')
    v.set(-3, 7, 0, lam)
    # Kame: red bed, magazines, TV, rug
    v.set(3, 5, -6, 'red_bed[facing=north,occupied=false,part=head]'); v.set(3, 5, -5, 'red_bed[facing=north,occupied=false,part=foot]')
    v.box(5, 5, -1, 5, 6, 1, 'bookshelf')
    v.set(1, 5, 6, 'dark_oak_slab[type=top,waterlogged=false]'); v.set(1, 6, 6, 'black_concrete')
    v.box(2, 4, 1, 4, 4, 3, 'orange_wool')
    v.set(5, 5, 6, 'chest[facing=north,type=single,waterlogged=false]')
    v.set(3, 7, 0, lam)
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
    return v

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
    v.set(0, 1, 0, 'polished_andesite'); v.set(0, 2, 0, 'sea_lantern')
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
