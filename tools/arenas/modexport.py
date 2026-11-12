"""What the mod loads for each arena (challenge/ArenaTemplate.java reads it back).

<res>/data/battlegroundsmod/souls_arenas/<id>.bin, gzip, big endian (java.io.DataInputStream):
    int   magic 0x41524E31 ('ARN1')
    int   palette size, then each block state as writeUTF ("minecraft:stone_stairs[facing=north,...]")
    int   column count, then per column (closest to the centre first, so the fight floor comes in first):
          short x, short z, short runs, then per run: short y0, short length, short palette index
    Positions are relative to the arena centre, y = 0 is the fight floor. Air is not stored.
<res>/data/battlegroundsmod/souls_arenas/arenas.json  per arena: R, altura, jogador, desafiante, neblina, noite, minY, maxY
<res>/data/battlegroundsmod/worldgen/biome/arena_<id>.json  sky = ceu[0], fog = ceu[1], grass/foliage/water = the preview's tints
"""
import gzip, io, json, math, os, struct

import mesher

MAGIC = 0x41524E31

def _utf(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b

def _rgb(h):
    h = h.lstrip('#')
    return int(h, 16)

def _int(c):
    return (c[0] << 16) | (c[1] << 8) | c[2]

def export(res, aid, v, m):
    base = os.path.join(res, 'data', 'battlegroundsmod', 'souls_arenas')
    os.makedirs(base, exist_ok=True)
    blocks = {k: s for k, s in v.b.items() if s and s != 'minecraft:air'}
    pal, pidx = [], {}
    cols = {}
    for (x, y, z), s in blocks.items():
        if s not in pidx:
            pidx[s] = len(pal); pal.append(s)
        cols.setdefault((x, z), []).append((y, pidx[s]))
    out = io.BytesIO()
    out.write(struct.pack('>i', MAGIC))
    out.write(struct.pack('>i', len(pal)))
    for s in pal:
        out.write(_utf(s))
    order = sorted(cols, key=lambda c: (c[0] * c[0] + c[1] * c[1], c))
    out.write(struct.pack('>i', len(order)))
    for (x, z) in order:
        ys = sorted(cols[(x, z)])
        runs = []
        for y, p in ys:
            if runs and runs[-1][0] + runs[-1][1] == y and runs[-1][2] == p:
                runs[-1][1] += 1
            else:
                runs.append([y, 1, p])
        out.write(struct.pack('>hhh', x, z, len(runs)))
        for y0, n, p in runs:
            out.write(struct.pack('>hhh', y0, n, p))
    with gzip.open(os.path.join(base, aid + '.bin'), 'wb', 9) as f:
        f.write(out.getvalue())
    ys = [k[1] for k in blocks]
    meta_path = os.path.join(base, 'arenas.json')
    meta = json.load(open(meta_path)) if os.path.exists(meta_path) else {}
    meta[aid] = dict(R=m['R'], altura=m['altura'], jogador=m['jogador'], desafiante=m['desafiante'],
                     neblina=m['neblina'], noite=m.get('skyLight', 15) <= 4, minY=min(ys), maxY=max(ys),
                     nome=m['nome'])
    json.dump(dict(sorted(meta.items())), open(meta_path, 'w'), ensure_ascii=False, indent=1)
    # the arena's own biome: sky, fog and tints exactly as the preview draws them
    tints = dict(mesher.TINT, **(m.get('tints') or {}))
    biome = {
        'has_precipitation': False, 'temperature': 0.8, 'downfall': 0.4,
        'effects': {
            'sky_color': _rgb(m['ceu'][0]), 'fog_color': _rgb(m['ceu'][1]),
            'water_color': _int(tints['water']), 'water_fog_color': _int([c // 3 for c in tints['water']]),
            'grass_color': _int(tints['grass']), 'foliage_color': _int(tints['foliage']),
            'mood_sound': {'block_search_extent': 8, 'offset': 2.0, 'sound': 'minecraft:ambient.cave', 'tick_delay': 6000},
        },
        'spawners': {k: [] for k in ('ambient', 'axolotls', 'creature', 'misc', 'monster', 'underground_water_creature',
                                     'water_ambient', 'water_creature')},
        'spawn_costs': {}, 'carvers': {}, 'features': [],
    }
    bdir = os.path.join(res, 'data', 'battlegroundsmod', 'worldgen', 'biome')
    os.makedirs(bdir, exist_ok=True)
    json.dump(biome, open(os.path.join(bdir, 'arena_' + aid + '.json'), 'w'), indent=2)
