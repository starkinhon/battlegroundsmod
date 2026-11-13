"""Shared tools for the challenge arenas (Stark 2026-11-12: arenas must be the canon place of each fight).

Every arena is a Vox centred on (0, 0): y = 0 is the floor the fight happens on, the fight circle has radius R, and
everything past R is scenery (it stays outside the invisible wall). Shapes are made from noise and signed distance
fields so no two rocks, trees or buildings come out the same (regra: nunca preguiça em poder / nada repetido).
"""
import math, os, sys
import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'structures'))
from vox import Vox  # noqa: E402

# ---- noise ----------------------------------------------------------------------------------------------------------
def _hash(ix, iy, iz, seed):
    h = (ix * 374761393 + iy * 668265263 + iz * 2147483647 + seed * 144269504) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0

class Noise:
    """Smooth value noise with octaves, 2D and 3D, deterministic per seed."""
    def __init__(self, seed):
        self.seed = seed
        rng = np.random.default_rng(seed)
        self.t = rng.random(4096)
        self.p = rng.permutation(4096)

    def _v(self, ix, iy, iz):
        return self.t[self.p[(ix + self.p[(iy + self.p[iz & 4095]) & 4095]) & 4095]]

    def n3(self, x, y, z):
        ix, iy, iz = math.floor(x), math.floor(y), math.floor(z)
        fx, fy, fz = x - ix, y - iy, z - iz
        sx, sy, sz = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy), fz * fz * (3 - 2 * fz)
        def L(a, b, t):
            return a + (b - a) * t
        v = self._v
        return L(L(L(v(ix, iy, iz), v(ix + 1, iy, iz), sx), L(v(ix, iy + 1, iz), v(ix + 1, iy + 1, iz), sx), sy),
                 L(L(v(ix, iy, iz + 1), v(ix + 1, iy, iz + 1), sx), L(v(ix, iy + 1, iz + 1), v(ix + 1, iy + 1, iz + 1), sx), sy), sz)

    def f2(self, x, z, scale=16.0, oct=4, y=0.0):
        """Fractal noise in -1..1."""
        a, f, s, n = 1.0, 1.0 / scale, 0.0, 0.0
        for o in range(oct):
            s += a * (self.n3(x * f + o * 17.3, y * f + o * 5.1, z * f + o * 31.7) * 2 - 1)
            n += a; a *= 0.5; f *= 2
        return s / n

    def f3(self, x, y, z, scale=16.0, oct=3):
        return self.f2(x, z, scale, oct, y)

# ---- palette helpers ------------------------------------------------------------------------------------------------
class Pick:
    """Weighted block choice driven by noise, so patches are organic instead of salt-and-pepper."""
    def __init__(self, noise, items, scale=5.0):
        self.n = noise; self.scale = scale
        tot = sum(w for _, w in items)
        acc = 0; self.cut = []
        for b, w in items:
            acc += w / tot; self.cut.append((acc, b))
    def at(self, x, y, z, jitter=0.18):
        v = self.n.f3(x, y, z, self.scale, 2) * 0.5 + 0.5
        v += (_hash(x, y, z, self.n.seed) - 0.5) * jitter
        v = min(0.999, max(0.0, v))
        for c, b in self.cut:
            if v <= c:
                return b
        return self.cut[-1][1]

# ---- signed distance fields -------------------------------------------------------------------------------------------
def sd_sphere(p, c, r):
    return math.dist(p, c) - r

def sd_ellipsoid(p, c, r):
    k0 = math.sqrt(sum(((p[i] - c[i]) / r[i]) ** 2 for i in range(3)))
    k1 = math.sqrt(sum(((p[i] - c[i]) / (r[i] * r[i])) ** 2 for i in range(3)))
    return k0 * (k0 - 1.0) / k1 if k1 > 1e-9 else -min(r)

def sd_capsule(p, a, b, ra, rb=None):
    rb = ra if rb is None else rb
    pa = [p[i] - a[i] for i in range(3)]; ba = [b[i] - a[i] for i in range(3)]
    h = max(0.0, min(1.0, sum(pa[i] * ba[i] for i in range(3)) / max(1e-9, sum(x * x for x in ba))))
    d = math.sqrt(sum((pa[i] - ba[i] * h) ** 2 for i in range(3)))
    return d - (ra + (rb - ra) * h)

def sd_box(p, c, half, rot_y=0.0):
    x, y, z = p[0] - c[0], p[1] - c[1], p[2] - c[2]
    if rot_y:
        cs, sn = math.cos(rot_y), math.sin(rot_y)
        x, z = x * cs - z * sn, x * sn + z * cs
    q = [abs(x) - half[0], abs(y) - half[1], abs(z) - half[2]]
    out = math.sqrt(sum(max(v, 0) ** 2 for v in q))
    return out + min(max(q[0], max(q[1], q[2])), 0)

def smin(a, b, k):
    h = max(k - abs(a - b), 0.0) / k
    return min(a, b) - h * h * k * 0.25

class Sculpt:
    """A list of (sdf function, block function) parts voxelized inside a box. Later parts win where they overlap
    (paint), smooth unions blend the shape. Used for statues, giant rocks, planets."""
    def __init__(self):
        self.parts = []
    def add(self, fn, block, smooth=0.0):
        self.parts.append((fn, block, smooth))
        return self
    def carve(self, fn):
        self.parts.append((fn, None, 0.0))
        return self
    def into(self, v, box, shell=None):
        (x0, y0, z0), (x1, y1, z1) = box
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                for y in range(y0, y1 + 1):
                    p = (x + 0.5, y + 0.5, z + 0.5)
                    d, blk = 1e9, None
                    for fn, b, k in self.parts:
                        dd = fn(p)
                        if b is None:
                            if -dd > d:
                                d = -dd
                            continue
                        if k > 0 and d < 1e8:
                            d2 = smin(d, dd, k)
                        else:
                            d2 = min(d, dd)
                        if dd <= 0.0 or (k > 0 and d2 <= 0 and d > 0):
                            blk = b
                        d = d2
                    if d <= 0 and blk is not None:
                        if shell is not None and d < -shell:
                            continue
                        v.set(x, y, z, blk(x, y, z) if callable(blk) else blk)

# ---- common pieces --------------------------------------------------------------------------------------------------
def column_fill(v, x, z, y0, y1, top, under, deep, under_depth=3):
    for y in range(y0, y1 + 1):
        if y == y1:
            b = top
        elif y >= y1 - under_depth:
            b = under
        else:
            b = deep
        v.set(x, y, z, b(x, y, z) if callable(b) else b)

def tree(v, x, y, z, rng, kind='oak', height=None, crown=None, noise=None, leaves_override=None):
    """A grown tree: bent trunk, branches and an irregular crown. Every call differs (rng)."""
    h = height or int(rng.integers(7, 12))
    log = {'oak': 'oak_log', 'spruce': 'spruce_log', 'dark_oak': 'dark_oak_log', 'birch': 'birch_log',
           'cherry': 'cherry_log', 'jungle': 'jungle_log', 'acacia': 'acacia_log', 'mangrove': 'mangrove_log'}[kind]
    leaves = {'oak': 'oak_leaves', 'spruce': 'spruce_leaves', 'dark_oak': 'dark_oak_leaves', 'birch': 'birch_leaves',
              'cherry': 'cherry_leaves', 'jungle': 'jungle_leaves', 'acacia': 'acacia_leaves', 'mangrove': 'mangrove_leaves'}[kind]
    leaves = leaves_override or leaves + '[persistent=true]'
    lean = rng.normal(0, 0.18, 2)
    cx, cz = float(x), float(z)
    top = None
    for i in range(h):
        cx += lean[0] * (i / h); cz += lean[1] * (i / h)
        v.set(int(round(cx)), y + i, int(round(cz)), log + '[axis=y]')
        top = (int(round(cx)), y + i, int(round(cz)))
    if kind == 'spruce':
        r0 = crown or rng.uniform(3.0, 4.2)
        for j in range(2, h + 2):
            rr = r0 * (1 - (j - 2) / (h)) + 0.6
            if j % 2 == 0:
                rr *= 0.75
            for dx in range(-5, 6):
                for dz in range(-5, 6):
                    if dx * dx + dz * dz <= rr * rr + rng.uniform(-0.6, 0.6) and (dx or dz or j >= h):
                        p = (top[0] + dx, y + j, top[2] + dz)
                        if v.get(*p) is None:
                            v.set(*p, leaves)
        return
    # branches
    tips = [top]
    for _ in range(int(rng.integers(2, 5))):
        a = rng.uniform(0, 2 * math.pi); l = rng.uniform(2, 4.5); sy = int(rng.integers(h // 2, h))
        bx, bz = x + math.cos(a) * l, z + math.sin(a) * l
        for t in np.linspace(0, 1, 6):
            px = int(round(x + (bx - x) * t)); pz = int(round(z + (bz - z) * t)); py = int(round(y + sy + t * 2))
            axis = 'x' if abs(math.cos(a)) > abs(math.sin(a)) else 'z'
            v.set(px, py, pz, log + '[axis=%s]' % axis)
        tips.append((int(round(bx)), y + sy + 2, int(round(bz))))
    r = crown or rng.uniform(2.6, 3.8)
    for (tx, ty, tz) in tips:
        rr = r * rng.uniform(0.7, 1.0)
        R = int(rr + 1)
        for dx in range(-R, R + 1):
            for dy in range(-2, R):
                for dz in range(-R, R + 1):
                    d = math.sqrt(dx * dx + (dy * 1.4) ** 2 + dz * dz)
                    if d <= rr + rng.uniform(-0.7, 0.4):
                        p = (tx + dx, ty + dy, tz + dz)
                        if v.get(*p) is None:
                            v.set(*p, leaves)

def ring_barrier_ok(v, R):
    """Nothing important may sit exactly where the invisible wall goes; returns count of solid blocks on the ring."""
    n = 0
    for (x, y, z), b in v.b.items():
        if y > 0 and abs(math.hypot(x, z) - (R + 1)) < 0.5:
            n += 1
    return n

def seal_fluids(v, block='stone'):
    """Water/lava must never touch the void: a template is placed without updates, but the first block update next to
    an open edge would pour the river out of the arena. Close every open side (and below) with rock."""
    for (x, y, z), b in list(v.b.items()):
        if ('water' in b or 'lava' in b) and 'level=' not in b:
            for d in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0)):
                p = (x + d[0], y + d[1], z + d[2])
                if v.get(*p) is None:
                    # an empty cell inside a waterfall curtain is meant to be air only if water falls there
                    v.set(*p, block)

def terrain(v, hmap, x0, x1, z0, z1, top, under, deep, min_depth=4):
    """Fill columns from a height function; each column goes down far enough to close cliffs (no see-through gaps).
    hmap(x, z) -> int height or None (no ground). top/under/deep: block or fn(x, y, z, h)."""
    H = {}
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            H[(x, z)] = hmap(x, z)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            h = H[(x, z)]
            if h is None:
                continue
            nb = [H[(x + a, z + b)] for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))]
            low = min([n for n in nb if n is not None] + [h]) - 1
            bottom = min(h - min_depth, low)
            for y in range(bottom, h + 1):
                d = h - y
                b = top if d == 0 else under if d <= 2 else deep
                v.set(x, y, z, b(x, y, z, h) if callable(b) else b)
    return H

def humanoid(v, at, face, H, mat, hair='long', raise_arm='right', detail=None):
    """A giant stone figure (Vale do Fim statues). at = feet centre (x, y, z), face = angle the statue looks along
    (radians, 0 = +x), H = total height. mat(x, y, z, part) -> block. hair: 'long' (Hashirama) or 'spiky' (Madara).
    One hand is raised in front of the chest with two fingers up (the seal of confrontation)."""
    cx, cy, cz = at
    cs, sn = math.cos(face), math.sin(face)
    def W(u, y, w):          # local (forward u, up y, side w) -> world
        return (cx + u * cs - w * sn, cy + y, cz + u * sn + w * cs)
    def L(p):                # world -> local
        dx, dz = p[0] - cx, p[2] - cz
        return (dx * cs + dz * sn, p[1] - cy, -dx * sn + dz * cs)
    s = H / 46.0
    parts = []
    def P(name, fn):
        parts.append((name, fn))
    S = lambda *a: tuple(x * s for x in a)
    # legs: shin guards below the armour skirt, feet planted wide
    for sd in (-1, 1):
        P('leg', lambda p, sd=sd: sd_capsule(L(p), S(0.3, 1.5, sd * 3.4), S(0, 16, sd * 2.8), 2.2 * s, 2.6 * s))
        P('shin', lambda p, sd=sd: sd_capsule(L(p), S(0.8, 3.5, sd * 3.4), S(0.6, 11.5, sd * 3.1), 2.5 * s, 2.8 * s))
        P('foot', lambda p, sd=sd: sd_ellipsoid(L(p), S(1.8, 0.9, sd * 3.5), S(3.6, 1.2, 2.0)))
    # armour skirt (tassets): flared plates from the waist down to the knee, open at the front
    def skirt(p):
        u, y, w = L(p)
        t = (24 * s - y) / (12 * s)
        if t < 0 or t > 1:
            return 1e9
        rw = (5.6 + 2.8 * t) * s; ru = (3.9 + 1.6 * t) * s
        e = math.sqrt((u / ru) ** 2 + (w / rw) ** 2)
        d = (e - 1) * min(ru, rw)
        if u > 0 and abs(w) < 1.2 * s and t > 0.25:
            return 1e9                     # the front opening between the plates
        return d
    P('skirt', skirt)
    P('belt', lambda p: sd_ellipsoid(L(p), S(0, 24.3, 0), S(3.5, 1.3, 5.0)))
    # torso: narrow waist, wide chest
    P('torso', lambda p: sd_capsule(L(p), S(0, 24.5, 0), S(0, 32.5, 0), 4.2 * s, 5.6 * s) if abs(L(p)[0]) < 3.3 * s else
      max(sd_capsule(L(p), S(0, 24.5, 0), S(0, 32.5, 0), 4.2 * s, 5.6 * s), abs(L(p)[0]) - 3.3 * s))
    P('chest', lambda p: max(sd_ellipsoid(L(p), S(1.0, 30.0, 0), S(3.0, 4.4, 5.2)), -L(p)[0] + 1.2 * s))
    P('collar', lambda p: sd_ellipsoid(L(p), S(-0.2, 34.0, 0), S(3.0, 1.4, 4.0)))
    # shoulder guards: big flared plates that make the silhouette
    for sd in (-1, 1):
        P('pauldron', lambda p, sd=sd: max(sd_ellipsoid(L(p), S(0, 33.2, sd * 7.8), S(3.6, 2.6, 3.6)), -(L(p)[1] - 30.4 * s) - abs(L(p)[2] - sd * 7.8 * s) * 0.0))
        P('pauldron', lambda p, sd=sd: max(sd_box(L(p), S(0, 31.0, sd * 9.4), S(3.2, 2.4, 0.9)), 0))
    # neck + head
    P('neck', lambda p: sd_capsule(L(p), S(0, 34.0, 0), S(0.3, 36.6, 0), 1.8 * s))
    P('head', lambda p: sd_ellipsoid(L(p), S(0.5, 39.4, 0), S(3.2, 3.9, 2.9)))
    P('nose', lambda p: sd_capsule(L(p), S(3.4, 39.9, 0), S(3.8, 38.7, 0), 0.5 * s))
    P('brow', lambda p: sd_capsule(L(p), S(3.1, 40.6, -1.8), S(3.1, 40.6, 1.8), 0.55 * s))
    # arms, held away from the body so the shape reads from far
    for sd in (-1, 1):
        sh = S(0, 32.6, sd * 7.6)
        if (sd == 1) == (raise_arm == 'right'):
            el = S(0.6, 25.8, sd * 9.4)
            hand = S(6.2, 31.8, sd * 1.6)
            P('arm', lambda p, a=sh, b=el: sd_capsule(L(p), a, b, 2.0 * s, 1.9 * s))
            P('arm', lambda p, a=el, b=hand: sd_capsule(L(p), a, b, 1.9 * s, 1.5 * s))
            P('bracer', lambda p, a=el, b=hand: sd_capsule(L(p), a, tuple(a[i] * .45 + b[i] * .55 for i in range(3)), 2.2 * s, 2.0 * s))
            P('hand', lambda p, c=hand: sd_ellipsoid(L(p), c, S(1.5, 1.7, 1.1)))
            P('finger', lambda p, c=hand: sd_capsule(L(p), (c[0] + 0.3 * s, c[1] + 1.0 * s, c[2]), (c[0] + 0.5 * s, c[1] + 4.6 * s, c[2]), 0.7 * s))
        else:
            el = S(0.2, 25.4, sd * 9.8)
            hand = S(1.2, 18.4, sd * 9.6)
            P('arm', lambda p, a=sh, b=el: sd_capsule(L(p), a, b, 2.0 * s, 1.9 * s))
            P('arm', lambda p, a=el, b=hand: sd_capsule(L(p), a, b, 1.9 * s, 1.6 * s))
            P('bracer', lambda p, a=el, b=hand: sd_capsule(L(p), a, tuple(a[i] * .4 + b[i] * .6 for i in range(3)), 2.2 * s, 2.0 * s))
            P('hand', lambda p, c=hand: sd_ellipsoid(L(p), c, S(1.4, 1.9, 1.2)))
    # hair
    if hair == 'long':
        P('hair', lambda p: sd_ellipsoid(L(p), S(-0.3, 40.2, 0), S(3.4, 4.1, 3.2)) if L(p)[0] < 1.9 * s or L(p)[1] > 42.2 * s else 1e9)
        # straight curtain down the back to the shoulder blades, slightly wider at the bottom
        P('hair', lambda p: sd_capsule(L(p), S(-2.4, 40, 0), S(-3.6, 27.5, 0), 3.4 * s, 4.4 * s) if L(p)[0] < -1.0 * s else 1e9)
        for sd in (-1, 1):
            P('hair', lambda p, sd=sd: sd_capsule(L(p), S(1.2, 41.0, sd * 3.0), S(1.6, 33.5, sd * 3.6), 1.0 * s, 0.8 * s))
    else:
        P('hair', lambda p: sd_ellipsoid(L(p), S(-0.8, 40.4, 0), S(3.6, 4.2, 3.5)) if L(p)[0] < 2.0 * s or L(p)[1] > 41.6 * s else 1e9)
        rng = np.random.default_rng(7)
        for i in range(34):
            a = rng.uniform(-1.35, 1.35); h0 = rng.uniform(31, 43)
            ln = rng.uniform(8, 15) * (1.3 if h0 < 37 else 1.0)
            base = S(-1.6, h0, math.sin(a) * 2.6)
            tip = (base[0] - math.cos(a) * ln * 0.75 * s, base[1] - ln * 0.6 * s, base[2] + math.sin(a) * ln * 0.8 * s)
            P('hair', lambda p, b=base, t=tip: sd_capsule(L(p), b, t, 1.9 * s, 0.35 * s))
        # the long fringe over one eye
        P('hair', lambda p: sd_capsule(L(p), S(2.4, 43.2, -0.9), S(3.4, 37.4, -2.0), 1.1 * s, 0.45 * s))
    # pedestal-like rock under the feet is the cliff itself
    xs, zs = [], []
    R = 14 * s + 3
    box = ((int(cx - R - 6), int(cy - 1), int(cz - R - 6)), (int(cx + R + 6), int(cy + H + 2), int(cz + R + 6)))
    for x in range(box[0][0], box[1][0] + 1):
        for z in range(box[0][2], box[1][2] + 1):
            for y in range(box[0][1], box[1][1] + 1):
                p = (x + 0.5, y + 0.5, z + 0.5)
                lp = L(p)
                if abs(lp[0]) > 14 * s or abs(lp[2]) > 13 * s:
                    continue
                best, part = 1e9, None
                for name, fn in parts:
                    d = fn(p)
                    if d < best:
                        best, part = d, name
                if best <= 0:
                    v.set(x, y, z, mat(x, y, z, part, lp))
    # eyes: two dark slits carved into the face
    for side in (-1, 1):
        for k in range(2):
            p = W(3.3 * s, 39.9 * s, side * (1.0 + 0.6 * k) * s)
            v.set(int(math.floor(p[0])), int(math.floor(p[1])), int(math.floor(p[2])), mat(0, 0, 0, 'eye', None))

def plants(v, H, n, kinds=(('short_grass', 0.35), ('fern', -0.62)), surface=('grass_block',)):
    """Grass and flowers on the open ground (noise driven, never a pattern)."""
    for (x, z), h in H.items():
        if h is None:
            continue
        b = v.get(x, h, z)
        if b and any(s in b for s in surface) and v.get(x, h + 1, z) is None:
            r = n.f2(x * 5, z * 5, 3)
            for k, thr in kinds:
                if (thr >= 0 and r > thr) or (thr < 0 and r < thr):
                    v.set(x, h + 1, z, k)
                    break

def big_tree(v, x, y, z, rng, log='spruce_log', leaves='spruce_leaves[persistent=true]', height=None, trunk=None):
    """A huge forest tree: thick round trunk with flaring roots, branches high up, wide layered crown."""
    h = height or int(rng.integers(22, 34)); r0 = trunk or rng.uniform(1.6, 2.6)
    lean = rng.normal(0, 0.06, 2)
    for i in range(-2, h):
        t = max(0, i) / h
        rr = r0 * (1.6 - 0.6 * min(1, t * 4)) * (1 - 0.35 * t)
        cx, cz = x + lean[0] * i, z + lean[1] * i
        R = int(rr + 2)
        for dx in range(-R, R + 1):
            for dz in range(-R, R + 1):
                if dx * dx + dz * dz <= rr * rr:
                    v.set(int(round(cx + dx)), y + i, int(round(cz + dz)), log + '[axis=y]')
    # roots
    for k in range(int(rng.integers(3, 6))):
        a = rng.uniform(0, 2 * math.pi)
        for j in range(int(r0 + 1), int(r0 + 5)):
            px, pz = int(round(x + math.cos(a) * j)), int(round(z + math.sin(a) * j))
            v.set(px, y - 1 + (j < r0 + 3), pz, log + '[axis=%s]' % ('x' if abs(math.cos(a)) > 0.7 else 'z'))
    top = (int(x + lean[0] * h), y + h, int(z + lean[1] * h))
    crowns = [top]
    for k in range(int(rng.integers(3, 6))):
        a = rng.uniform(0, 2 * math.pi); sy = int(h * rng.uniform(0.55, 0.9)); L = rng.uniform(4, 8)
        for j in range(int(L)):
            px, pz = int(round(x + math.cos(a) * j)), int(round(z + math.sin(a) * j))
            v.set(px, y + sy + j // 3, pz, log + '[axis=%s]' % ('x' if abs(math.cos(a)) > 0.7 else 'z'))
        crowns.append((int(round(x + math.cos(a) * L)), y + sy + int(L) // 3 + 1, int(round(z + math.sin(a) * L))))
    for (cx, cy, cz) in crowns:
        rr = rng.uniform(4, 6.5)
        R = int(rr + 1)
        for dx in range(-R, R + 1):
            for dy in range(-3, 4):
                for dz in range(-R, R + 1):
                    if dx * dx + (dy * 2.0) ** 2 + dz * dz <= rr * rr + rng.uniform(-2, 1):
                        p = (cx + dx, cy + dy, cz + dz)
                        if v.get(*p) is None:
                            v.set(*p, leaves)

def konoha_house(v, x, y, z, w, d, h, rng, facing=0, roof='red_terracotta'):
    """A Konoha style house: cream plaster walls, wooden frame, round-ish tiled roof in red/orange, water tank on top."""
    wall = rng.choice(['white_terracotta', 'smooth_sandstone', 'light_gray_concrete', 'white_concrete'])
    for dx in range(-w, w + 1):
        for dz in range(-d, d + 1):
            for dy in range(0, h):
                edge = abs(dx) == w or abs(dz) == d
                if not edge:
                    continue
                if (abs(dx) == w and abs(dz) == d):
                    b = 'stripped_spruce_log[axis=y]'
                elif dy == 0:
                    b = 'spruce_planks'
                elif dy % 3 == 2 and ((dx + dz) % 3 == 0):
                    b = 'glass_pane'
                else:
                    b = wall
                v.set(x + dx, y + dy, z + dz, b)
            v.set(x + dx, y - 1, z + dz, 'spruce_planks')
    # roof: a shallow vault along the longer side
    long_x = w >= d
    for dx in range(-w - 1, w + 2):
        for dz in range(-d - 1, d + 2):
            k = abs(dz) if long_x else abs(dx)
            span = d + 1 if long_x else w + 1
            ry = h + int(round((1 - (k / span) ** 2) * 2.4))
            v.set(x + dx, y + ry, z + dz, roof)
            if abs(dx) == w + 1 or abs(dz) == d + 1:
                v.set(x + dx, y + h - 1, z + dz, roof)
    if rng.random() < 0.5:
        tx, tz = x + int(rng.integers(-w + 1, w)), z + int(rng.integers(-d + 1, d))
        for dy in range(0, 3):
            v.set(tx, y + h + 3 + dy, tz, 'spruce_fence' if dy == 0 else 'cauldron' if dy == 1 else 'spruce_slab[type=bottom]')

# ---- what the game needs that the eye does not --------------------------------------------------------------------
GRAVITY = {'sand': 'sandstone', 'red_sand': 'red_sandstone', 'gravel': 'andesite', 'suspicious_sand': 'sandstone',
           'suspicious_gravel': 'andesite'}
for _c in ('white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan', 'purple',
           'blue', 'brown', 'green', 'red', 'black'):
    GRAVITY[_c + '_concrete_powder'] = _c + '_concrete'
PASSABLE = ('short_grass', 'tall_grass', 'fern', 'large_fern', 'dead_bush', 'moss_carpet', 'snow', 'dandelion', 'poppy',
            'cornflower', 'azure_bluet', 'oxeye_daisy', 'allium', 'lily_of_the_valley', 'pink_petals', 'sweet_berry_bush',
            'rail', 'redstone_wire', 'torch', 'fire')

def _name(b):
    return b.split(':', 1)[-1].split('[')[0]

def support_gravity(v):
    """Sand, gravel and concrete powder fall when anything touches them. Placed without updates they hang, but the
    first block update under an overhang (or the sea's own fluid ticks) drops whole columns into the void: Stark's PC
    test 2026-11-13, the Kame island and Las Noches stalled the server 24 s and 54 s. Any such block without a solid
    block under it becomes its stable twin (sandstone, andesite, concrete), so the arena never falls apart."""
    n = 0
    for (x, y, z), b in list(v.b.items()):
        name = _name(b)
        if name not in GRAVITY:
            continue
        below = v.b.get((x, y - 1, z))
        bn = _name(below) if below else None
        if bn is None or bn == 'air' or 'water' in bn or 'lava' in bn or bn in PASSABLE or 'leaves' in bn:
            v.b[(x, y, z)] = 'minecraft:' + GRAVITY[name]
            n += 1
    return n

def _free(v, x, y, z):
    b = v.b.get((x, y, z))
    return b is None or _name(b) in ('air',) + PASSABLE

def _ground(v, x, y, z):
    b = v.b.get((x, y, z))
    if b is None:
        return False
    nm = _name(b)
    return nm not in ('air', 'water', 'lava') + PASSABLE and 'leaves' not in nm and 'water' not in b

def safe_spawn(v, p, reach=10):
    """The nearest place to p where someone can stand: ground under the feet, two free blocks (no water) above it.
    Stark's PC test 2026-11-13: players spawned inside the Todo forest floor and in the river under the Yasohachi
    bridge."""
    x0, y0, z0 = p
    best = None
    for r in range(0, reach + 1):
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if max(abs(dx), abs(dz)) != r:
                    continue
                for dy in sorted(range(-4, 25), key=abs):
                    x, y, z = x0 + dx, y0 + dy, z0 + dz
                    if _free(v, x, y, z) and _free(v, x, y + 1, z) and _ground(v, x, y - 1, z):
                        cost = dx * dx + dz * dz + dy * dy * 0.5
                        if best is None or cost < best[0]:
                            best = (cost, [x, y, z])
                        break
        if best is not None and best[0] <= (r + 1) ** 2:
            return best[1]
    return best[1] if best else list(p)
