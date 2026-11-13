"""Turns a Vox (tools/structures/vox.py) into the mesh the web preview draws, using Minecraft's own block models and
textures (client jar 1.21.1), so the preview shows exactly the blocks that go into the game (Stark 2026-11-11:
JOGO = PRÉVIA).

Output per arena: <name>.bin, a little-endian buffer the preview page reads:
  u32 nOpaqueVerts, u32 nOpaqueIdx, u32 nTransVerts, u32 nTransIdx, then for each group:
  i16 pos[3]*n (1/16 block), u16 uv[2]*n (0..65535 of the atlas), u8 rgba[4]*n (tint*shade*AO, a=255 emissive / 0),
  u32 idx*m (opaque group first, then translucent).
The atlas is one PNG shared by every arena (atlas.png + atlas.json).

MC_ASSETS must point at an extracted client jar (assets/minecraft/...).
"""
import json, math, os, struct
from functools import lru_cache
from PIL import Image

MC = os.environ.get('MC_ASSETS', '.')
A = os.path.join(MC, 'assets', 'minecraft')
MOD = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets')

def _root(ns):
    return A if ns == 'minecraft' else os.path.join(MOD, ns)

def _ns(ref, default='minecraft'):
    return ref.split(':', 1) if ':' in ref else (default, ref)

def _load(path):
    with open(path) as f:
        return json.load(f)

def _id(name):
    return name.split(':', 1)[1] if ':' in name else name

def split_state(state):
    if '[' not in state:
        return _id(state), {}
    name, rest = state.split('[', 1)
    props = {}
    for p in rest.rstrip(']').split(','):
        if '=' in p:
            k, v = p.split('=', 1)
            props[k] = v
    return _id(name), props

# ---- models ---------------------------------------------------------------------------------------------------------
@lru_cache(None)
def model(ref):
    """Fully resolved model: (textures dict, elements list, ambientocclusion)."""
    ns, path = _ns(ref)
    p = os.path.join(_root(ns), 'models', path + '.json')
    if not os.path.exists(p):
        return {}, None, True
    m = _load(p)
    tex, els, ao = {}, None, m.get('ambientocclusion', True)
    if 'parent' in m and not m['parent'].startswith('builtin/'):
        ptex, pels, pao = model(m['parent'])
        tex.update(ptex); els = pels
        if 'ambientocclusion' not in m:
            ao = pao
    for k, t in m.get('textures', {}).items():
        tex[k] = t if t.startswith('#') or ':' in t else ns + ':' + t
    if 'elements' in m:
        els = m['elements']
    return tex, els, ao

def resolve_tex(tex, key, depth=0):
    v = tex.get(key.lstrip('#'))
    if v is None or depth > 8:
        return None
    if v.startswith('#'):
        return resolve_tex(tex, v, depth + 1)
    return v if ':' in v else 'minecraft:' + v

def _match(when, props):
    if 'OR' in when:
        return any(_match(w, props) for w in when['OR'])
    if 'AND' in when:
        return all(_match(w, props) for w in when['AND'])
    for k, v in when.items():
        have = props.get(k, 'false' if v in ('true', 'false') else 'none')
        if have not in str(v).split('|'):
            return False
    return True

@lru_cache(None)
def _blockstate(full):
    ns, name = _ns(full)
    p = os.path.join(_root(ns), 'blockstates', name + '.json')
    return _load(p) if os.path.exists(p) else None

def variants(state):
    """List of (model ref, x rot, y rot, uvlock) for a block state string."""
    name, props = split_state(state)
    ns = _ns(state.split('[')[0])[0] if ':' in state.split('[')[0] else 'minecraft'
    bs = _blockstate(ns + ':' + name)
    if bs is None:
        return []
    if name.endswith('_wall') and 'up' not in props:
        props = dict(props, up='true')     # the game's default wall state has its post up
    out = []
    def add(v):
        if isinstance(v, list):
            v = v[0]
        mref = v['model'] if ':' in v['model'] else ns + ':' + v['model']
        out.append((mref, v.get('x', 0), v.get('y', 0), v.get('uvlock', False)))
    if 'variants' in bs:
        best, score = None, -1
        for key, v in bs['variants'].items():
            conds = dict(c.split('=') for c in key.split(',') if '=' in c)
            if all(props.get(k, None) in (None, val) for k, val in conds.items()):
                s = sum(1 for k, val in conds.items() if props.get(k) == val)
                if s > score:
                    best, score = v, s
        if best is not None:
            add(best)
    else:
        for part in bs['multipart']:
            if 'when' not in part or _match(part['when'], props):
                add(part['apply'])
    return out

# ---- block classes --------------------------------------------------------------------------------------------------
TRANSLUCENT_HINT = ('glass', 'ice', 'slime_block', 'honey_block', 'water')
EMISSIVE = ('glowstone', 'sea_lantern', 'shroomlight', 'froglight', 'lantern', 'torch', 'lava', 'magma_block', 'fire',
            'jack_o_lantern', 'redstone_lamp[lit=true', 'end_rod', 'beacon', 'crying_obsidian', 'campfire',
            'amethyst_cluster', 'glow_lichen', 'light_block', 'respawn_anchor', 'ochre_froglight')

def leafy(name):
    return 'leaves' in name or name.startswith('folhas') or name.startswith('folha_')

@lru_cache(None)
def is_full(state):
    """Opaque full cube that hides its neighbours' faces."""
    name, _ = split_state(state)
    if name in ('air', 'barrier', 'water', 'lava') or any(t in name for t in TRANSLUCENT_HINT) or leafy(name):
        return False
    vs = variants(state)
    if len(vs) != 1:
        return False
    tex, els, _ = model(vs[0][0])
    if not els or len(els) != 1:
        return False
    e = els[0]
    return e['from'] == [0, 0, 0] and e['to'] == [16, 16, 16]

@lru_cache(None)
def is_translucent(state):
    name, _ = split_state(state)
    return ('glass' in name and 'tinted' not in name) or name in ('ice', 'water', 'slime_block', 'honey_block')

@lru_cache(None)
def is_emissive(state):
    return any(t in state for t in EMISSIVE)

# biome tints (per arena: palette may override)
TINT = {'grass': (124, 189, 107), 'foliage': (89, 174, 48), 'water': (63, 118, 228), 'spruce': (97, 153, 97),
        'birch': (128, 167, 85), 'redstone': (220, 30, 30), 'stem': (120, 180, 60)}

def tint_for(name, tints):
    if 'water' in name:
        return tints['water']
    if 'spruce_leaves' in name:
        return tints['spruce']
    if 'birch_leaves' in name:
        return tints['birch']
    if 'leaves' in name or name == 'vine':
        return tints['foliage']
    if 'redstone_wire' in name:
        return tints['redstone']
    if 'stem' in name:
        return tints['stem']
    return tints['grass']

# ---- atlas ----------------------------------------------------------------------------------------------------------
class Atlas:
    """Fixed 1024x1024 grid of 16px slots, kept across runs (atlas.json), so arenas built separately share it."""
    def __init__(self, path=None):
        self.slots = {}
        if path and os.path.exists(path):
            self.slots = json.load(open(path))['slots']
    def slot(self, tex):
        if tex not in self.slots:
            self.slots[tex] = len(self.slots)
        return self.slots[tex]
    def save(self, png, js):
        cols = 64
        H = 1024
        assert len(self.slots) <= 4096
        img = Image.new('RGBA', (cols * 16, H), (0, 0, 0, 0))
        for tex, i in self.slots.items():
            tns, tpath = _ns(tex)
            p = os.path.join(_root(tns), 'textures', tpath + '.png')
            try:
                t = Image.open(p).convert('RGBA')
                t = t.crop((0, 0, 16, 16)) if t.size[0] == 16 else t.crop((0, 0, t.size[0], t.size[0])).resize((16, 16), Image.NEAREST)
            except Exception:
                t = Image.new('RGBA', (16, 16), (255, 0, 255, 255))
            img.paste(t, ((i % cols) * 16, (i // cols) * 16))
        img.save(png)
        with open(js, 'w') as f:
            json.dump({'cols': cols, 'w': cols * 16, 'h': H, 'slots': self.slots}, f)
    def uv(self, tex, u, v):
        """u, v in 0..16 inside the texture -> atlas 0..1 (v from top)."""
        i = self.slots[tex]
        cols = 64
        return ((i % cols) * 16 + u, (i // cols) * 16 + v)

# ---- geometry -------------------------------------------------------------------------------------------------------
DIRS = {'down': (0, -1, 0), 'up': (0, 1, 0), 'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0)}
SHADE = {'up': 1.0, 'down': 0.5, 'north': 0.8, 'south': 0.8, 'west': 0.6, 'east': 0.6}

def face_corners(f, t, face):
    x0, y0, z0 = f; x1, y1, z1 = t
    # corners in the order matching uv (u0,v0) (u1,v0) (u1,v1) (u0,v1), as Minecraft does
    return {
        'up':    [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        'down':  [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
        'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        'west':  [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        'east':  [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
    }[face]

def default_uv(f, t, face):
    x0, y0, z0 = f; x1, y1, z1 = t
    return {
        'up': [x0, z0, x1, z1], 'down': [x0, 16 - z1, x1, 16 - z0],
        'north': [16 - x1, 16 - y1, 16 - x0, 16 - y0], 'south': [x0, 16 - y1, x1, 16 - y0],
        'west': [z0, 16 - y1, z1, 16 - y0], 'east': [16 - z1, 16 - y1, 16 - z0, 16 - y0],
    }[face]

def rot_el(p, rot):
    if not rot:
        return p
    o = rot['origin']; a = math.radians(rot['angle']); ax = rot['axis']
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    c, s = math.cos(a), math.sin(a)
    if ax == 'x':
        y, z = y * c - z * s, y * s + z * c
    elif ax == 'y':
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])

def rot_block(p, xr, yr):
    x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
    for _ in range((xr // 90) % 4):          # rotate around X: y,z
        y, z = z, -y
    for _ in range((yr // 90) % 4):          # rotate around Y (clockwise from above): x,z
        x, z = -z, x
    return (x + 8, y + 8, z + 8)

def rot_dir(d, xr, yr):
    x, y, z = DIRS[d]
    for _ in range((xr // 90) % 4):
        y, z = z, -y
    for _ in range((yr // 90) % 4):
        x, z = -z, x
    for k, v in DIRS.items():
        if v == (x, y, z):
            return k
    return d

class Mesh:
    def __init__(self):
        self.pos = []; self.uv = []; self.col = []; self.idx = []
    def quad(self, ps, uvs, col):
        b = len(self.pos)
        self.pos += ps; self.uv += uvs; self.col += col
        self.idx += [b, b + 1, b + 2, b, b + 2, b + 3]

LIGHT_LEVEL = {'glowstone': 15, 'sea_lantern': 15, 'shroomlight': 15, 'froglight': 15, 'lantern': 15, 'torch': 14,
               'lava': 15, 'magma_block': 3, 'fire': 15, 'jack_o_lantern': 15, 'end_rod': 14, 'beacon': 15,
               'crying_obsidian': 10, 'campfire': 15, 'amethyst_cluster': 5, 'glow_lichen': 7, 'respawn_anchor': 15,
               'soul_lantern': 10, 'soul_torch': 10, 'soul_fire': 10, 'soul_campfire': 10, 'redstone_lamp[lit=true': 15}

def light_of(state):
    if state.split(':')[-1].startswith('light[') or state.split(':')[-1] == 'light':
        import re
        m = re.search(r'level=(\d+)', state)
        return int(m.group(1)) if m else 15     # the invisible light block (lib.illuminate)
    best = 0
    for k, l in LIGHT_LEVEL.items():
        if k in state:
            best = max(best, l)
    return best

class Light:
    """Minecraft-like light: sky light from straight above (15, minus 1 per step sideways) and block light from the
    blocks that glow, both stopped by opaque blocks. Gives the preview the same dark caves/halls the game has."""
    def __init__(self, blocks, full, sky=15):
        import numpy as np
        xs = [k[0] for k in blocks]; ys = [k[1] for k in blocks]; zs = [k[2] for k in blocks]
        self.o = (min(xs) - 2, min(ys) - 2, min(zs) - 2)
        size = (max(xs) - self.o[0] + 3, max(ys) - self.o[1] + 3, max(zs) - self.o[2] + 3)
        op = np.zeros(size, dtype=bool)
        att = np.zeros(size, dtype=np.int8)
        src = np.zeros(size, dtype=np.int8)
        for (x, y, z), st in blocks.items():
            i = (x - self.o[0], y - self.o[1], z - self.o[2])
            if (x, y, z) in full:
                op[i] = True
            elif leafy(split_state(st)[0]) or 'water' in st:
                att[i] = 1
            l = light_of(st)
            if l:
                src[i] = l
        # sky: 15 down every column until the first opaque block (leaves/water take one off each)
        skyl = np.zeros(size, dtype=np.int8)
        cur = np.full((size[0], size[2]), sky, dtype=np.int8)
        for y in range(size[1] - 1, -1, -1):
            cur = np.where(op[:, y, :], 0, np.maximum(cur - att[:, y, :], 0)).astype(np.int8)
            skyl[:, y, :] = cur
        self.sky = self._spread(skyl, op)
        self.blk = self._spread(src.copy(), op, keep_src=True)
    @staticmethod
    def _spread(L, op, keep_src=False):
        import numpy as np
        src = L.astype(np.int16)
        L = src.copy()
        for _ in range(15):
            n = L.copy()
            for ax in range(3):
                for sh in (1, -1):
                    r = np.roll(L, sh, axis=ax) - 1
                    idx = [slice(None)] * 3; idx[ax] = 0 if sh == 1 else -1; r[tuple(idx)] = 0
                    n = np.maximum(n, r)
            n[op] = 0
            if keep_src:
                n = np.maximum(n, src)
            if (n == L).all():
                break
            L = n
        return L.clip(0, 15)
    def at(self, x, y, z):
        i = (x - self.o[0], y - self.o[1], z - self.o[2])
        try:
            if min(i) < 0:
                return 15, 0
            return int(self.sky[i]), int(self.blk[i])
        except IndexError:
            return 15, 0

def brightness(sky, blk):
    l = max(sky, blk) / 15.0
    return 0.06 + 0.94 * (l / (4 - 3 * l)) ** 0.55

def build(vox, atlas, tints=None, sky=15):
    tints = dict(TINT, **(tints or {}))
    lit = {k: v for k, v in vox.b.items() if v and _id(v.split('[')[0]) not in ('air', 'barrier', 'structure_void')}
    blocks = {k: v for k, v in lit.items() if _id(v.split('[')[0]) != 'light'}     # light blocks glow, unseen
    full = {k for k, v in blocks.items() if is_full(v)}
    light = Light(lit, full, sky)
    opaque, trans = Mesh(), Mesh()
    def solid(x, y, z):
        return (x, y, z) in full
    for (bx, by, bz), state in blocks.items():
        name, props = split_state(state)
        emis = 255 if is_emissive(state) else 0
        if name in ('water', 'lava'):
            _fluid(blocks, bx, by, bz, name, atlas, tints, trans if name == 'water' else opaque, emis,
                   1.0 if emis else brightness(*light.at(bx, by + 1, bz)))
            continue
        target = trans if is_translucent(state) else opaque
        vs = variants(state)
        for ref, xr, yr, uvlock in vs:
            tex, els, ao = model(ref)
            if els is None:
                p = resolve_tex(tex, 'particle')
                if not p:
                    continue
                els = [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {d: {'texture': '#particle', 'cullface': d} for d in DIRS}}]
            for e in els:
                f, t = e['from'], e['to']
                for face, fd in e.get('faces', {}).items():
                    world_face = rot_dir(face, xr, yr)
                    cull = fd.get('cullface')
                    if cull:
                        cd = DIRS[rot_dir(cull, xr, yr)]
                        n = (bx + cd[0], by + cd[1], bz + cd[2])
                        if n in full:
                            continue
                        ns = blocks.get(n)
                        if ns and ((ns == state and is_translucent(state)) or (leafy(name) and leafy(split_state(ns)[0]))):
                            continue
                    texn = resolve_tex(tex, fd.get('texture', '#missing'))
                    if not texn:
                        continue
                    atlas.slot(texn)
                    uv = fd.get('uv') or default_uv(f, t, face)
                    u0, v0, u1, v1 = uv
                    cuv = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
                    r = (fd.get('rotation', 0) // 90) % 4
                    cuv = cuv[r:] + cuv[:r] if r else cuv
                    corners = [rot_el(p, e.get('rotation')) for p in face_corners(f, t, face)]
                    corners = [rot_block(p, xr, yr) for p in corners]
                    shade = SHADE[world_face] if e.get('shade', True) else 1.0
                    tint = tint_for(name, tints) if 'tintindex' in fd else (255, 255, 255)
                    # ambient occlusion only on faces lying on the block boundary
                    aos = [1.0] * 4
                    if ao and not emis:
                        dn = DIRS[world_face]
                        on_edge = all(abs(c[i] - (16 if dn[i] > 0 else 0)) < 0.01 for c in corners for i in range(3) if dn[i] != 0)
                        if on_edge:
                            aos = [_ao(solid, bx, by, bz, dn, c) for c in corners]
                    ps = [(int(round(bx * 16 + c[0])), int(round(by * 16 + c[1])), int(round(bz * 16 + c[2]))) for c in corners]
                    uvs = [atlas.uv(texn, a, b) for a, b in cuv]
                    if emis:
                        lb = 1.0
                    else:
                        dn = DIRS[world_face]
                        lp = (bx + dn[0], by + dn[1], bz + dn[2]) if (bx + dn[0], by + dn[1], bz + dn[2]) not in full else (bx, by, bz)
                        lb = brightness(*light.at(*lp))
                    k = shade * lb
                    cols = [(int(tint[0] * k * a), int(tint[1] * k * a), int(tint[2] * k * a), emis) for a in aos]
                    target.quad(ps, uvs, cols)
    return opaque, trans

def _ao(solid, bx, by, bz, n, c):
    # corner in block-space (0 or 16 per axis); look at the 3 blocks touching that corner in the layer the face looks at
    axes = [i for i in range(3) if n[i] == 0]
    base = [bx + n[0], by + n[1], bz + n[2]]
    off = []
    for i in axes:
        off.append(1 if c[i] > 8 else -1)
    s1 = list(base); s1[axes[0]] += off[0]
    s2 = list(base); s2[axes[1]] += off[1]
    cc = list(base); cc[axes[0]] += off[0]; cc[axes[1]] += off[1]
    a, b, d = solid(*s1), solid(*s2), solid(*cc)
    lvl = 0 if (a and b) else 3 - (a + b + d)
    return (0.45, 0.62, 0.8, 1.0)[lvl]

def _fluid(blocks, bx, by, bz, name, atlas, tints, mesh, emis, lb=1.0):
    tex = 'minecraft:block/water_still' if name == 'water' else 'minecraft:block/lava_still'
    atlas.slot(tex)
    above = blocks.get((bx, by + 1, bz), '')
    same_above = name in above
    top = 16 if same_above else 14
    tint = tints['water'] if name == 'water' else (255, 255, 255)
    for face, d in DIRS.items():
        nb = blocks.get((bx + d[0], by + d[1], bz + d[2]), '')
        if name in nb or (nb and is_full(nb)):
            continue
        if face == 'up' and same_above:
            continue
        corners = face_corners((0, 0, 0), (16, top, 16), face)
        uv = [(0, 0), (16, 0), (16, 16), (0, 16)]
        ps = [(bx * 16 + c[0], by * 16 + c[1], bz * 16 + c[2]) for c in corners]
        sh = SHADE[face] * lb
        mesh.quad(ps, [atlas.uv(tex, a, b) for a, b in uv], [(int(tint[0] * sh), int(tint[1] * sh), int(tint[2] * sh), emis)] * 4)

def write(path, opaque, trans, atlas_w, atlas_h):
    """Quads only (4 vertices each, the page builds the index). Gzipped; the page inflates it with DecompressionStream."""
    import gzip, numpy as np
    parts = [struct.pack('<4I', len(opaque.pos), 0, len(trans.pos), 0)]
    for m in (opaque, trans):
        n = len(m.pos)
        pos = np.array(m.pos, dtype=np.int16).reshape(-1, 3) if n else np.zeros((0, 3), np.int16)
        if n % 2:
            pos = np.vstack([pos, np.zeros((1, 3), np.int16)])
        uv = np.array(m.uv, dtype=np.float64).reshape(-1, 2) if n else np.zeros((0, 2))
        uv = np.stack([np.clip(uv[:, 0] / atlas_w * 65535, 0, 65535), np.clip(uv[:, 1] / atlas_h * 65535, 0, 65535)], 1).astype(np.uint16)
        col = np.clip(np.array(m.col, dtype=np.int32).reshape(-1, 4) if n else np.zeros((0, 4)), 0, 255).astype(np.uint8)
        parts += [pos.tobytes(), uv.tobytes(), col.tobytes()]
    with gzip.open(path, 'wb', compresslevel=9) as f:
        f.write(b''.join(parts))
