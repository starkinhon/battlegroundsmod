"""Furniture and finishing for the mod's buildings (Stark 2026-11-12: interiors looked bugged and ugly; this is the
shared kit so every room is built the same careful way: real sofas, tables, lamps, rugs ON the floor, ceilings under
grass floors, lit rooms so no mob spawns inside).

Directions: 'north' = -z, 'south' = +z, 'west' = -x, 'east' = +x. "facing" of a piece = the side it looks at (a sofa
facing south has its back on the north and you sit looking south)."""
import math

DV = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}
OPP = {'north': 'south', 'south': 'north', 'west': 'east', 'east': 'west'}
LEFT = {'north': 'west', 'west': 'south', 'south': 'east', 'east': 'north'}   # your left when you look that way
RIGHT = {v: k for k, v in LEFT.items()}


def stairs(block, facing, half='bottom', shape='straight'):
    return f'{block}[facing={facing},half={half},shape={shape},waterlogged=false]'


def slab(block, t='bottom'):
    return f'{block}[type={t},waterlogged=false]'


def trapdoor(block, facing, half='bottom', open_=False):
    return f'{block}[facing={facing},half={half},open={"true" if open_ else "false"},powered=false,waterlogged=false]'


def free(v, x, y, z):
    return v.get(x, y, z) in (None, 'minecraft:air')


def along(facing, n):
    """Cells to the right of the piece's own left->right axis (seen by someone sitting in it)."""
    r = DV[RIGHT[facing]]
    return [(r[0] * i, r[1] * i) for i in range(n)]


def sofa(v, x, y, z, facing, n=3, block='spruce_stairs', arm='spruce_trapdoor', cushion=None):
    """n seats in a row starting at (x, z) and going to the sitter's right. Stairs facing AWAY from the view (the back
    rest is the high part, which faces 'facing' reversed), trapdoor armrests at both ends."""
    back = OPP[facing]
    for dx, dz in along(facing, n):
        v.set(x + dx, y, z + dz, stairs(block, back))
    l = DV[LEFT[facing]]; r = DV[RIGHT[facing]]
    ex, ez = along(facing, n)[-1]
    if arm:
        v.set(x + l[0], y, z + l[1], trapdoor(arm, LEFT[facing], open_=True))
        v.set(x + ex + r[0], y, z + ez + r[1], trapdoor(arm, RIGHT[facing], open_=True))


def chair(v, x, y, z, facing, block='oak_stairs'):
    v.set(x, y, z, stairs(block, OPP[facing]))


def table(v, x0, z0, x1, z1, y, top='spruce_slab', leg='spruce_fence', legs=True):
    """Slab top at y on fence legs at the corners (or a pressure-plate-on-fence for 1x1)."""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            corner = x in (x0, x1) and z in (z0, z1)
            if legs and corner and (x0 != x1 or z0 != z1):
                v.set(x, y, z, leg)
            else:
                v.set(x, y, z, slab(top, 'top'))
    if x0 == x1 and z0 == z1:
        v.set(x0, y, z0, leg); v.set(x0, y + 1, z0, top.replace('_slab', '_pressure_plate') if 'slab' in top else 'oak_pressure_plate')


def rug(v, x0, z0, x1, z1, y, color, border=None):
    """Carpet lying ON the floor at height y (never replaces the floor)."""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if free(v, x, y, z):
                v.set(x, y, z, f'{border if (border and edge) else color}_carpet')


def lamp_floor(v, x, y, z, post='spruce_fence', light='lantern[hanging=false,waterlogged=false]'):
    v.set(x, y, z, post); v.set(x, y + 1, z, light)


def hang(v, x, y, z, chain=0, light='lantern[hanging=true,waterlogged=false]'):
    """A light hanging from the ceiling block at y+1+chain."""
    for k in range(chain):
        v.set(x, y + 1 + k, z, 'chain[axis=y,waterlogged=false]')
    v.set(x, y, z, light)


def ceiling_light(v, x, y, z, block='ochre_froglight[axis=y]'):
    """Flush light set into the ceiling (y = the ceiling block)."""
    v.set(x, y, z, block)


def bed(v, x, y, z, facing, color='red'):
    """Head at (x, z), foot one step toward 'facing' reversed: the head is against the wall it faces from."""
    d = DV[OPP[facing]]
    v.set(x, y, z, f'{color}_bed[facing={facing},occupied=false,part=head]')
    v.set(x + d[0], y, z + d[1], f'{color}_bed[facing={facing},occupied=false,part=foot]')


def door(v, x, y, z, facing, block='oak_door', hinge='left'):
    v.set(x, y, z, f'{block}[facing={facing},half=lower,hinge={hinge},open=false,powered=false]')
    v.set(x, y + 1, z, f'{block}[facing={facing},half=upper,hinge={hinge},open=false,powered=false]')


def pot(v, x, y, z, plant='fern'):
    v.set(x, y, z, f'potted_{plant}')


def counter(v, cells, y, body='stripped_spruce_wood[axis=y]', top='smooth_stone_slab', facing='south'):
    """Kitchen counter: cabinet blocks (barrels look like drawers) with a slab worktop above."""
    for i, (x, z) in enumerate(cells):
        v.set(x, y, z, f'barrel[facing={facing},open=false]' if i % 2 == 0 else body)


def bookshelf_wall(v, x0, z0, x1, z1, y0, y1, chiseled=True, facing='south'):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            for y in range(y0, y1 + 1):
                if chiseled and (x + y + z) % 3 == 0:
                    s = ','.join(f'slot_{i}_occupied={"true" if (x * 7 + y * 3 + z + i) % 3 else "false"}' for i in range(6))
                    v.set(x, y, z, f'chiseled_bookshelf[facing={facing},{s}]')
                else:
                    v.set(x, y, z, 'bookshelf')


def tv(v, x, y, z, facing, w=2):
    """Flat screen: black concrete with a dark glass face, on a low cabinet."""
    for dx, dz in along(facing, w):
        v.set(x + dx, y, z + dz, trapdoor('dark_oak_trapdoor', facing, half='top'))
        v.set(x + dx, y + 1, z + dz, 'black_concrete')


def wardrobe(v, x, y, z, facing, w=2, wood='spruce'):
    for dx, dz in along(facing, w):
        v.set(x + dx, y, z + dz, f'{wood}_planks'); v.set(x + dx, y + 1, z + dz, f'{wood}_planks')
        f = DV[facing]
        v.set(x + dx + f[0], y, z + dz + f[1], trapdoor(f'{wood}_trapdoor', facing, open_=True))
        v.set(x + dx + f[0], y + 1, z + dz + f[1], trapdoor(f'{wood}_trapdoor', facing, open_=True))


def window_box(v, x, y, z, facing, flower='poppy'):
    """Flower box under an outside window."""
    v.set(x, y, z, trapdoor('spruce_trapdoor', facing, half='top'))


# ---- final pass -------------------------------------------------------------------------------------------------
PANE_LIKE = ('_pane', 'iron_bars')


def _name(s):
    return s.split(':')[-1].split('[')[0] if s else None


def _props(s):
    if not s or '[' not in s:
        return {}
    return dict(p.split('=') for p in s.split('[', 1)[1].rstrip(']').split(',') if p)


def _with(s, **kw):
    n = s.split('[')[0]; p = _props(s); p.update({k: str(v).lower() for k, v in kw.items()})
    return n + ('[' + ','.join(f'{k}={v}' for k, v in p.items()) + ']' if p else '')


def solidish(s):
    if not s or s == 'minecraft:air':
        return False
    n = _name(s)
    if n == 'lantern' or n == 'soul_lantern':
        return False
    if any(k in n for k in ('slab', 'stairs', 'fence', '_wall', 'pane', 'bars', 'door', 'carpet', 'button', 'plate',
                             'chain', 'torch', 'rod', 'sign', 'banner', 'potted', 'flower_pot', 'bed', 'ladder', 'rail',
                             'candle', 'scaffolding', 'cake', 'cauldron', 'anvil', 'lever', 'hook', 'head', 'skull',
                             'campfire', 'sapling', 'short_grass', 'tall_grass', 'fern', 'leaves', 'azalea', 'bamboo',
                             'vine', 'cocoa', 'lectern', 'brewing', 'stonecutter', 'grindstone', 'bell', 'chest',
                             'water', 'lava', 'poppy', 'dandelion', 'tulip', 'orchid', 'allium', 'daisy', 'cornflower',
                             'lily', 'rose', 'sugar_cane', 'pickle', 'cluster', 'bud', 'dripleaf', 'cobweb', 'end_rod', 'decorated_pot', 'trapdoor', 'shulker', 'snow', 'petals',
                             'roots', 'fungus', 'coral', 'sea_pickle', 'item_frame')):
        return False
    return True


def finish(v):
    """Make block states agree with their neighbours, because the mod places structures without neighbour updates:
    glass panes, iron bars and fences connect to what is really next to them; lanterns that would hang in the air get a
    chain up to the ceiling or stand on what is below; doors keep both halves."""
    b = v.b
    for (x, y, z), s in list(b.items()):
        n = _name(s)
        if n is None:
            continue
        if n.endswith('_pane') or n == 'iron_bars' or n.endswith('_fence'):
            kw = {}
            for d, (dx, dz) in DV.items():
                o = b.get((x + dx, y, z + dz)); on = _name(o) or ''
                if n.endswith('_fence'):
                    ok = solidish(o) or on.endswith('_fence') or on.endswith('_fence_gate')
                else:
                    ok = solidish(o) or on.endswith('_pane') or on == 'iron_bars' or on.endswith('_wall')
                kw[d] = ok
            b[(x, y, z)] = _with(s, **kw)
        elif n in ('lantern', 'soul_lantern'):
            hanging = _props(s).get('hanging') == 'true'
            up = b.get((x, y + 1, z)); dn = b.get((x, y - 1, z))
            if hanging and not (solidish(up) or _name(up) in ('chain',) or (_name(up) or '').endswith(('_fence', '_slab'))):
                # chain up to the first block above (max 6), else stand on the floor
                k = 1
                while k <= 6 and (b.get((x, y + k, z)) in (None, 'minecraft:air')):
                    k += 1
                if k <= 6 and solidish(b.get((x, y + k, z))):
                    for j in range(1, k):
                        b[(x, y + j, z)] = 'minecraft:chain[axis=y,waterlogged=false]'
                elif solidish(dn) or (_name(dn) or '').endswith(('_fence', '_slab', '_wall')):
                    b[(x, y, z)] = _with(s, hanging=False)
            elif not hanging and not (solidish(dn) or (_name(dn) or '').endswith(('_fence', '_slab', '_wall', '_stairs'))):
                if solidish(up):
                    b[(x, y, z)] = _with(s, hanging=True)
                else:
                    b[(x, y, z)] = 'minecraft:air'
    return v
