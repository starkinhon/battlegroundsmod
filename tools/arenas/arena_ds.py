"""Demon Slayer arenas."""
import math
import numpy as np
from arenas_all import arena
from lib import Vox, Noise

def tatami(v, x0, z0, x1, z1, y, rng):
    """A tatami floor: 2x4 mats laid in the traditional alternating pattern, dark borders between them."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            bx, bz = (x - x0), (z - z0)
            cell = ((bx // 4) + (bz // 4)) % 2
            if cell == 0:
                edge = bx % 4 == 0 or bz % 2 == 0 and bz % 4 == 0
                b = 'dark_oak_planks' if bx % 4 == 0 or bz % 4 == 0 else 'bamboo_mosaic'
            else:
                b = 'dark_oak_planks' if bz % 4 == 0 or bx % 4 == 0 else 'stripped_bamboo_block[axis=x]'
            v.set(x, y, z, b)

def shoji_wall(v, x0, z0, x1, z1, y0, y1, lit=True):
    """A straight shoji wall along x or z: dark wooden frame every 3 blocks, white paper glowing from behind."""
    horiz = z0 == z1
    n = (x1 - x0) if horiz else (z1 - z0)
    for i in range(n + 1):
        x = x0 + i if horiz else x0
        z = z0 if horiz else z0 + i
        for y in range(y0, y1 + 1):
            frame = i % 3 == 0 or y == y0 or y == y1 or (y - y0) % 4 == 0
            if frame:
                v.set(x, y, z, 'stripped_dark_oak_log[axis=y]' if i % 3 == 0 else 'dark_oak_planks')
            else:
                v.set(x, y, z, 'white_stained_glass' if not lit else 'white_stained_glass')
    if lit:
        # the paper glows: lamps one block behind the panels
        for i in range(1, n, 3):
            x = x0 + i if horiz else x0 + (1 if x0 > 0 else -1)
            z = z0 + (1 if z0 > 0 else -1) if horiz else z0 + i
            for y in range(y0 + 2, y1, 4):
                v.set(x, y, z, 'ochre_froglight[axis=y]')

def room(v, cx, cy, cz, w, d, h, rng, flip=False, side=None):
    """One of the castle's rooms floating in the void: tatami floor, posts, a few shoji walls, lanterns.
    flip: built upside down (its floor is a ceiling), side: rotated onto a wall."""
    tmp = Vox()
    tatami(tmp, -w, -d, w, d, 0, rng)
    for x in range(-w, w + 1):
        for z in range(-d, d + 1):
            tmp.set(x, -1, z, 'dark_oak_planks')
    for (px, pz) in ((-w, -d), (w, -d), (-w, d), (w, d)):
        for y in range(1, h + 1):
            tmp.set(px, y, pz, 'dark_oak_log[axis=y]')
    for x in range(-w, w + 1):
        for z in (-d, d):
            tmp.set(x, h + 1, z, 'dark_oak_log[axis=x]')
    for z in range(-d, d + 1):
        for x in (-w, w):
            tmp.set(x, h + 1, z, 'dark_oak_log[axis=z]')
    walls = rng.permutation(4)[:int(rng.integers(1, 3))]
    for k in walls:
        if k == 0:
            shoji_wall(tmp, -w + 1, -d, w - 1, -d, 1, h, lit=False)
        elif k == 1:
            shoji_wall(tmp, -w + 1, d, w - 1, d, 1, h, lit=False)
        elif k == 2:
            shoji_wall(tmp, -w, -d + 1, -w, d - 1, 1, h, lit=False)
        else:
            shoji_wall(tmp, w, -d + 1, w, d - 1, 1, h, lit=False)
    for x in range(-1, 2):
        for z in range(-1, 2):
            tmp.set(x, h + 1, z, 'dark_oak_planks')
    tmp.set(0, h + 1, 0, 'shroomlight')
    tmp.set(-w + 1, 1, -d + 1, 'lantern[hanging=false]'); tmp.set(w - 1, 1, d - 1, 'lantern[hanging=false]')
    for (x, y, z), b in tmp.b.items():
        if flip:
            y = h + 1 - y
            if 'lantern' in b:
                b = 'minecraft:lantern[hanging=false]'
        if side == 'x':
            x, y = y, x
            b = b.replace('axis=y', 'axis=X').replace('axis=x', 'axis=y').replace('axis=X', 'axis=x')
            if 'lantern' in b:
                b = 'minecraft:shroomlight'
        elif side == 'z':
            z, y = y, z
            b = b.replace('axis=y', 'axis=Z').replace('axis=z', 'axis=y').replace('axis=Z', 'axis=z')
            if 'lantern' in b:
                b = 'minecraft:shroomlight'
        v.set(cx + x, cy + y, cz + z, b)

@arena('kokushibo')
def castelo_infinito():
    """Muzan's Infinity Castle: a tatami hall hanging in an endless vertical maze of rooms, some on the walls and some
    upside down, wooden pillars running out of sight and stairs going everywhere. No sky: the paper glows."""
    v = Vox(); n = Noise(4401); rng = np.random.default_rng(4401)
    R = 40
    HALL = 44
    # main hall floor (square, the fight circle inside it), with a dark substructure
    tatami(v, -HALL, -HALL, HALL, HALL, 0, rng)
    for x in range(-HALL, HALL + 1):
        for z in range(-HALL, HALL + 1):
            v.set(x, -1, z, 'dark_oak_planks')
            if (x % 8 == 0) or (z % 8 == 0):
                v.set(x, -2, z, 'dark_oak_log[axis=%s]' % ('x' if z % 8 == 0 else 'z'))
    # railing round the hall edge: the drop into the castle below
    for i in range(-HALL, HALL + 1):
        for (x, z) in ((i, -HALL), (i, HALL), (-HALL, i), (HALL, i)):
            v.set(x, 1, z, 'dark_oak_fence')
            if i % 4 == 0:
                v.set(x, 1, z, 'dark_oak_log[axis=y]'); v.set(x, 2, z, 'lantern[hanging=false]')
    # gaps in the railing where stairs leave the hall
    stair_exits = [(-HALL, 0, 'west'), (HALL, 10, 'east'), (0, -HALL, 'north'), (14, HALL, 'south')]
    # pillars: a ring of giant columns inside the fight (cover) and a grid outside running up and down forever
    for (px, pz) in ((-22, -22), (22, -22), (-22, 22), (22, 22), (0, -30), (0, 30)):
        for y in range(1, 26):
            for dx in range(-1, 2):
                for dz in range(-1, 2):
                    v.set(px + dx, y, pz + dz, 'stripped_dark_oak_log[axis=y]' if abs(dx) + abs(dz) < 2 else 'dark_oak_log[axis=y]')
        v.set(px, 24, pz + 2, 'lantern[hanging=true]'); v.set(px, 24, pz - 2, 'lantern[hanging=true]')
    for gx in range(-96, 97, 24):
        for gz in range(-96, 97, 24):
            if max(abs(gx), abs(gz)) <= HALL:
                continue
            y0, y1 = int(rng.uniform(-70, -30)), int(rng.uniform(40, 86))
            for y in range(y0, y1):
                for dx in range(2):
                    for dz in range(2):
                        v.set(gx + dx, y, gz + dz, 'dark_oak_log[axis=y]')
    # rafters over the hall
    for y in (28,):
        for i in range(-HALL, HALL + 1):
            for k in range(-HALL, HALL + 1, 11):
                v.set(i, y, k, 'dark_oak_log[axis=x]'); v.set(k, y + 1, i, 'dark_oak_log[axis=z]')
        for x in range(-HALL + 5, HALL, 11):
            for z in range(-HALL + 5, HALL, 11):
                for j in range(1, 4):
                    v.set(x, y - j, z, 'chain[axis=y]')
                v.set(x, y - 4, z, 'lantern[hanging=true]')
    # the rooms of the maze, all around and above/below the hall
    rooms = []
    for i in range(70):
        cx, cz = rng.uniform(-100, 100, 2)
        if max(abs(cx), abs(cz)) < HALL + 12:
            continue
        cy = int(rng.uniform(-60, 80))
        w, d, h = int(rng.integers(4, 9)), int(rng.integers(4, 9)), int(rng.integers(4, 7))
        if any(abs(cx - a) < w + aw + 4 and abs(cz - c) < d + ad + 4 and abs(cy - b) < h + 8 for a, b, c, aw, ad in rooms):
            continue
        rooms.append((cx, cy, cz, w, d))
        kind = rng.random()
        room(v, int(cx), cy, int(cz), w, d, h, rng, flip=kind < 0.2, side=('x' if kind > 0.85 else 'z' if kind > 0.7 else None))
    # staircases: long straight flights hanging in the void, a few leaving from the hall
    for i in range(26):
        if i < len(stair_exits):
            x, z, facing = stair_exits[i]
            y = 0
        else:
            x, z = (int(a) for a in rng.uniform(-100, 100, 2))
            if max(abs(x), abs(z)) < HALL + 6:
                continue
            y = int(rng.uniform(-50, 60)); facing = ['north', 'south', 'east', 'west'][int(rng.integers(0, 4))]
        dx, dz = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}[facing]
        up = rng.random() < 0.5
        L = int(rng.integers(14, 34))
        for k in range(L):
            px, pz = x + dx * k, z + dz * k
            py = y + (k if up else -k)
            for s in (-1, 0, 1):
                sx, sz = px + dz * s, pz + dx * s
                if max(abs(sx), abs(sz)) <= HALL and py >= 0 and i < 4:
                    continue
                st = 'dark_oak_stairs[facing=%s,half=bottom,shape=straight]' % (facing if up else {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}[facing])
                v.set(sx, py, sz, st)
                v.set(sx, py - 1, sz, 'dark_oak_planks')
            for s in (-2, 2):
                sx, sz = px + dz * s, pz + dx * s
                v.set(sx, py + 1, sz, 'dark_oak_fence')
                if k % 6 == 0:
                    v.set(sx, py + 2, sz, 'lantern[hanging=false]')
    # a wooden sky over everything: no daylight gets in, the castle is lit only by its lamps
    E = 112
    for x in range(-E, E + 1):
        for z in range(-E, E + 1):
            v.set(x, 96, z, 'dark_oak_planks' if (x % 12) and (z % 12) else 'dark_oak_log[axis=x]')
    meta = dict(universo='demonslayer', personagem='Kokushibo', lugar='Castelo Infinito', nome='Castelo Infinito',
                canon='A fortaleza do Muzan: o salão de tatame onde a Lua Superior Um espera, pendurado num labirinto sem fim de salas, escadas e colunas, algumas de lado e outras de cabeça para baixo.',
                R=R, altura=26, ceu=['#0c0608', '#1e0f0c'], neblina=[40, 150],
                jogador=[0, 1, 26], desafiante=[0, 1, -24], camLado=[38, 22, 52], alvoY=6, skyLight=0)
    return v, meta
