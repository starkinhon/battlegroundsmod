#!/usr/bin/env python3
"""Verificador de z-fighting (faces sobrepostas piscando) dos modelos do mod.

Acha pares de faces de pecas diferentes que caem no MESMO plano, viradas pro MESMO lado e cobrindo a mesma area
(as que piscam no jogo), e com --corrigir afasta a menor um pouquinho ate nao sobrar nenhum par.

Formatos:
  blocky   assets/battlegroundsmod/blocky/**.json  caixas dos mobs (BlockyModel): [x,y,z,w,h,d,u,v,(g)]
           correcao: a caixa menor cresce G_BLOCKY px (indice 8 = CubeDeformation, o mesmo b.g das previas)
  json     assets/battlegroundsmod/models/{item,block}/**.json  elementos do Blockbench (from/to/rotation)
           correcao: o elemento menor cresce G_JSON px em cada lado
  mesh     assets/battlegroundsmod/models/mesh/*.bin  malhas de triangulos (MeshModel)
           correcao: o triangulo menor sai G_MESH pela propria normal

Uso:
  python3 tools/zfight/zfight.py              # so verifica; sai com erro se achar par
  python3 tools/zfight/zfight.py --corrigir   # corrige os arquivos e verifica de novo
  python3 tools/zfight/zfight.py --lista      # lista cada par achado

Pecas so de rest pose (sem animacao). Faces de lados opostos no mesmo plano nao contam: ficam lacradas entre duas
caixas e ninguem ve. Planos de espessura zero tambem nao: as duas faces tem os mesmos vertices e o teste de
profundidade sempre da o mesmo resultado (nao pisca).
"""
import glob
import json
import math
import os
import struct
import sys

import numpy as np

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources', 'assets',
                    'battlegroundsmod')

# Mobs sao vistos de longe: 0,03 px (0,002 bloco) ja e o que as previas usam e some do buffer de profundidade ate
# ~30 blocos. Itens/blocos sao vistos de perto: 0,001 (regra do Stark). Malha: 0,001 px pela normal.
G_BLOCKY = 0.015
G_JSON = 0.001
# Duas faces contam como mesmo plano abaixo destas distancias (menos que o passo de correcao de cada formato).
EPS_PLANE = {'blocky': 0.006, 'json': 0.0005}
MIN_AREA = 1e-4  # area em comum minima (px^2) pra contar


# ---------------------------------------------------------------- geometria
def rot_zyx(x, y, z):
    """Quaternionf.rotationZYX(z, y, x) do vanilla: v' = Rz * Ry * Rx * v."""
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def box_faces(lo, hi):
    """6 faces (4 cantos cada, em ordem) de uma caixa alinhada, com a normal de fora."""
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    return [
        (np.array([[x0, y0, z0], [x0, y1, z0], [x0, y1, z1], [x0, y0, z1]]), (-1, 0, 0)),
        (np.array([[x1, y0, z0], [x1, y0, z1], [x1, y1, z1], [x1, y1, z0]]), (1, 0, 0)),
        (np.array([[x0, y0, z0], [x0, y0, z1], [x1, y0, z1], [x1, y0, z0]]), (0, -1, 0)),
        (np.array([[x0, y1, z0], [x1, y1, z0], [x1, y1, z1], [x0, y1, z1]]), (0, 1, 0)),
        (np.array([[x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0]]), (0, 0, -1)),
        (np.array([[x0, y0, z1], [x0, y1, z1], [x1, y1, z1], [x1, y0, z1]]), (0, 0, 1)),
    ]


def clip(subject, clipper):
    """Sutherland-Hodgman: poligono convexo 2D cortado por outro convexo (anti-horario)."""
    out = subject
    n = len(clipper)
    for i in range(n):
        a, b = clipper[i], clipper[(i + 1) % n]
        inp, out = out, []
        if not inp:
            break
        ex, ey = b[0] - a[0], b[1] - a[1]

        def side(p):
            return ex * (p[1] - a[1]) - ey * (p[0] - a[0])

        for j in range(len(inp)):
            p, q = inp[j], inp[(j + 1) % len(inp)]
            sp, sq = side(p), side(q)
            if sp >= 0:
                out.append(p)
            if (sp >= 0) != (sq >= 0):
                t = sp / (sp - sq)
                out.append((p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1])))
    return out


def area2(poly):
    s = 0.0
    for i in range(len(poly)):
        x0, y0 = poly[i]
        x1, y1 = poly[(i + 1) % len(poly)]
        s += x0 * y1 - x1 * y0
    return s / 2


def ccw(poly):
    return poly if area2(poly) >= 0 else poly[::-1]


class Face:
    __slots__ = ('pts', 'n', 'd', 'owner', 'lo', 'hi')

    def __init__(self, pts, n, owner):
        self.pts = pts
        self.n = n
        self.d = float(n @ pts[0])
        self.owner = owner
        self.lo = pts.min(axis=0)
        self.hi = pts.max(axis=0)


def make_face(pts, owner):
    """Face plana a partir dos cantos (pega a normal pelo produto vetorial); None se degenerada."""
    n = np.cross(pts[1] - pts[0], pts[2] - pts[0])
    if len(pts) == 4:
        n2 = np.cross(pts[2] - pts[0], pts[3] - pts[0])
        n = n + n2
    ln = np.linalg.norm(n)
    if ln < 1e-12:
        return None
    return Face(pts, n / ln, owner)


def overlap(a, b):
    """Area em comum de duas faces no mesmo plano."""
    n = a.n
    # base do plano
    t = np.array([1.0, 0, 0]) if abs(n[0]) < 0.9 else np.array([0, 1.0, 0])
    u = np.cross(n, t)
    u /= np.linalg.norm(u)
    v = np.cross(n, u)
    pa = ccw([(float(p @ u), float(p @ v)) for p in a.pts])
    pb = ccw([(float(p @ u), float(p @ v)) for p in b.pts])
    poly = clip(pa, pb)
    return abs(area2(poly)) if len(poly) >= 3 else 0.0


def find_pairs(faces, eps):
    """Pares de faces de donos diferentes, mesmo plano, mesmo lado, area em comum."""
    pairs = []
    # agrupa por normal arredondada pra nao comparar tudo com tudo
    buckets = {}
    for f in faces:
        key = tuple(np.round(f.n, 3))
        buckets.setdefault(key, []).append(f)
    for fl in buckets.values():
        fl.sort(key=lambda f: f.d)
        for i in range(len(fl)):
            a = fl[i]
            for j in range(i + 1, len(fl)):
                b = fl[j]
                if b.d - a.d > eps:
                    break
                if a.owner == b.owner:
                    continue
                if float(a.n @ b.n) < 0.99999:
                    continue
                if np.any(a.lo > b.hi + eps) or np.any(b.lo > a.hi + eps):
                    continue
                ar = overlap(a, b)
                if ar > MIN_AREA:
                    pairs.append((a, b, ar))
    return pairs


# ---------------------------------------------------------------- blocky
def blocky_faces(model):
    faces = []
    boxes = []

    def walk(part, M, T):
        px, py, pz = part['pivot']
        rx, ry, rz = part.get('rot', [0, 0, 0])
        T2 = T + M @ np.array([px, py, pz], dtype=float)
        M2 = M @ rot_zyx(rx, ry, rz) if (rx or ry or rz) else M
        for b in part['boxes']:
            g = b[8] if len(b) > 8 else 0.0
            g = g if isinstance(g, list) else [g, g, g]
            x, y, z, w, h, d = b[:6]
            lo = np.array([min(x, x + w) - g[0], min(y, y + h) - g[1], min(z, z + d) - g[2]])
            hi = np.array([max(x, x + w) + g[0], max(y, y + h) + g[1], max(z, z + d) + g[2]])
            idx = len(boxes)
            boxes.append((b, part['name'], abs(w * h * d), (w, h, d)))
            for pts, _n in box_faces(lo, hi):
                f = make_face((M2 @ pts.T).T + T2, idx)
                if f is not None:
                    faces.append(f)
        for k in part['kids']:
            walk(k, M2, T2)

    walk(model['root'], np.eye(3), np.zeros(3))
    return faces, boxes


def greedy(items, eps):
    """Coloca as pecas da maior pra menor; cada uma cresce o minimo ate nao ter face no mesmo plano de uma ja
    colocada. Pilha de N pecas iguais vira N niveis diferentes (o minimo possivel), nada cresce a toa.
    items: (volume, solve(hit) -> faces finais); hit(faces) diz se alguma bate numa face ja colocada."""
    import bisect
    placed = {}
    order = sorted(range(len(items)), key=lambda i: -items[i][0])
    for i in order:
        fs = items[i][1](lambda faces: any(face_hits(f, placed, eps) for f in faces))
        for f in fs:
            key = tuple(np.round(f.n, 3))
            lst = placed.setdefault(key, ([], []))
            j = bisect.bisect(lst[0], f.d)
            lst[0].insert(j, f.d)
            lst[1].insert(j, f)


def uniform_solve(faces_at, apply):
    def solve(hit):
        for k in range(400):
            fs = faces_at(k)
            if not hit(fs):
                break
        if k:
            apply(k)
        return fs
    return solve


def face_hits(f, placed, eps):
    import bisect
    lst = placed.get(tuple(np.round(f.n, 3)))
    if not lst:
        return False
    ds, fl = lst
    j0 = bisect.bisect_left(ds, f.d - eps)
    j1 = bisect.bisect_right(ds, f.d + eps)
    for j in range(j0, j1):
        b = fl[j]
        if float(f.n @ b.n) < 0.99999:
            continue
        if np.any(f.lo > b.hi + eps) or np.any(b.lo > f.hi + eps):
            continue
        if overlap(f, b) > MIN_AREA:
            return True
    return False


def blocky_items(model):
    """Uma entrada por caixa: cresce por eixo (gx, gy, gz = CubeDeformation), so no eixo das faces que batem."""
    items = []

    def walk(part, M, T):
        px, py, pz = part['pivot']
        rx, ry, rz = part.get('rot', [0, 0, 0])
        T2 = T + M @ np.array([px, py, pz], dtype=float)
        M2 = M @ rot_zyx(rx, ry, rz) if (rx or ry or rz) else M
        for b in part['boxes']:
            idx = len(items)
            g0 = b[8] if len(b) > 8 else 0.0
            g0 = list(g0) if isinstance(g0, list) else [g0, g0, g0]
            x, y, z, w, h, d = b[:6]

            def faces_at(k, g0=g0, M2=M2, T2=T2, idx=idx, x=x, y=y, z=z, w=w, h=h, d=d):
                g = [g0[a] + k[a] * G_BLOCKY for a in range(3)]
                lo = np.array([min(x, x + w) - g[0], min(y, y + h) - g[1], min(z, z + d) - g[2]])
                hi = np.array([max(x, x + w) + g[0], max(y, y + h) + g[1], max(z, z + d) + g[2]])
                out = []
                for j, (pts, _n) in enumerate(box_faces(lo, hi)):
                    f = make_face((M2 @ pts.T).T + T2, idx)
                    if f is not None:
                        out.append((j // 2, f))
                return out

            def solve(hit, faces_at=faces_at, b=b, g0=g0):
                k = [0, 0, 0]
                fs = faces_at(k)
                if not hit([f for _, f in fs]):
                    return [f for _, f in fs]
                for a in range(3):
                    while k[a] < 400 and hit([f for ax, f in faces_at(k) if ax == a]):
                        k[a] += 1
                while hit([f for _, f in faces_at(k)]):
                    k = [v + 1 for v in k]
                g = [round(g0[a] + k[a] * G_BLOCKY, 4) for a in range(3)]
                val = g[0] if g[0] == g[1] == g[2] else g
                if len(b) > 8:
                    b[8] = val
                else:
                    b.append(val)
                return [f for _, f in faces_at(k)]

            items.append((abs(w * h * d), solve))
        for kid in part['kids']:
            walk(kid, M2, T2)

    walk(model['root'], np.eye(3), np.zeros(3))
    return items


def blocky_files():
    return sorted(f for f in glob.glob(os.path.join(ROOT, 'blocky', '**', '*.json'), recursive=True)
                  if not f.endswith('.anim.json'))


# ---------------------------------------------------------------- json (Blockbench / vanilla)
AXES = {'x': 0, 'y': 1, 'z': 2}


def elem_matrix(rot):
    if not rot:
        return np.eye(3), np.zeros(3)
    ang = math.radians(rot.get('angle', 0))
    o = np.array(rot.get('origin', [8, 8, 8]), dtype=float)
    c, s = math.cos(ang), math.sin(ang)
    ax = rot.get('axis', 'y')
    if ax == 'x':
        R = np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    elif ax == 'y':
        R = np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    else:
        R = np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])
    if rot.get('rescale'):
        k = 1 / math.cos(ang) if abs(math.cos(ang)) > 1e-6 else 1
        S = np.eye(3) * k
        S[AXES[ax], AXES[ax]] = 1
        R = R @ S
    return R, o


FACE_DIR = {'west': 0, 'east': 1, 'down': 2, 'up': 3, 'north': 4, 'south': 5}


def json_faces(model):
    faces, elems = [], []
    for i, e in enumerate(model.get('elements', [])):
        lo = np.minimum(e['from'], e['to']).astype(float)
        hi = np.maximum(e['from'], e['to']).astype(float)
        size = hi - lo
        R, o = elem_matrix(e.get('rotation'))
        elems.append((e, float(np.prod(size)), tuple(size)))
        bf = box_faces(lo, hi)
        for name, fdef in e.get('faces', {}).items():
            if name not in FACE_DIR:
                continue
            pts, _ = bf[FACE_DIR[name]]
            f = make_face((R @ (pts - o).T).T + o, i)
            if f is not None:
                faces.append(f)
    return faces, elems


def json_items(model):
    """Uma entrada por elemento: cresce G_JSON por lado (from/to). So contam as faces que o elemento tem."""
    items = []
    for i, e in enumerate(model.get('elements', [])):
        f0 = list(map(float, e['from']))
        t0 = list(map(float, e['to']))
        R, o = elem_matrix(e.get('rotation'))
        names = [n for n in e.get('faces', {}) if n in FACE_DIR]
        size = np.abs(np.array(t0) - np.array(f0))

        def corners(k, f0=f0, t0=t0):
            g = k * G_JSON
            lo = np.minimum(f0, t0) - g
            hi = np.maximum(f0, t0) + g
            return lo, hi

        def faces_at(k, corners=corners, R=R, o=o, names=names, i=i):
            lo, hi = corners(k)
            bf = box_faces(lo, hi)
            out = []
            for n in names:
                f = make_face((R @ (bf[FACE_DIR[n]][0] - o).T).T + o, i)
                if f is not None:
                    out.append(f)
            return out

        def apply(k, e=e, f0=f0, t0=t0):
            g = k * G_JSON
            nf, nt = [], []
            for a_, b_ in zip(f0, t0):
                if a_ <= b_:
                    nf.append(round(a_ - g, 4)); nt.append(round(b_ + g, 4))
                else:
                    nf.append(round(a_ + g, 4)); nt.append(round(b_ - g, 4))
            e['from'], e['to'] = nf, nt

        items.append((float(np.prod(size)), uniform_solve(faces_at, apply)))
    return items


def json_files():
    out = []
    for sub in ('item', 'block'):
        out += glob.glob(os.path.join(ROOT, 'models', sub, '**', '*.json'), recursive=True)
    return sorted(out)


# ---------------------------------------------------------------- mesh (.bin)
# O Santuario (malevolent_shrine_*) e uma malha por material desenhadas juntas, em blocos e visto de longe: entra
# como um modelo so e afasta 0,003 bloco. As espadas/martelo sao em px do jogador e vistas de perto: 0,015 px.
class Mesh:
    def __init__(self, paths):
        self.paths = paths
        self.parts = [mesh_read(p) for p in paths]
        self.shrine = 'malevolent_shrine_' in paths[0]
        self.step = 0.003 if self.shrine else 0.015
        self.eps = 0.001 if self.shrine else 0.006


def mesh_read(path):
    raw = open(path, 'rb').read()
    count = struct.unpack('>i', raw[:4])[0]
    return np.frombuffer(raw[4:4 + count * 32], dtype='>f4').astype(np.float64).reshape(count, 8)


def mesh_write(path, data):
    with open(path, 'wb') as fh:
        fh.write(struct.pack('>i', len(data)))
        fh.write(data.astype('>f4').tobytes())


def mesh_tris(mesh):
    """(parte, indice, triangulo) de todas as partes; o dono de cada face = um triangulo."""
    for pi, d in enumerate(mesh.parts):
        tris = d[:, :3].reshape(-1, 3, 3)
        for i, t in enumerate(tris):
            yield pi, i, t


def mesh_faces(mesh):
    faces = []
    for pi, i, t in mesh_tris(mesh):
        f = make_face(t.copy(), (pi, i))
        if f is not None:
            faces.append(f)
    return faces


def mesh_patches(mesh):
    """Triangulos vizinhos (dividem uma aresta) no mesmo plano da mesma parte andam juntos: assim um quadrado nunca
    racha no meio quando um dos seus dois triangulos sai do plano."""
    out = []
    for pi, d in enumerate(mesh.parts):
        tris = d[:, :3].reshape(-1, 3, 3)
        n = len(tris)
        parent = list(range(n))

        def find(x):
            while parent[x] != x:
                parent[x] = parent[parent[x]]
                x = parent[x]
            return x

        normals, planes = [None] * n, [None] * n
        edges = {}
        for i, t in enumerate(tris):
            f = make_face(t.copy(), i)
            if f is None:
                continue
            normals[i], planes[i] = f.n, f.d
            keys = [tuple(np.round(v, 4)) for v in t]
            for e in ((keys[0], keys[1]), (keys[1], keys[2]), (keys[2], keys[0])):
                edges.setdefault(tuple(sorted(e)), []).append(i)
        for lst in edges.values():
            for j in lst[1:]:
                i = lst[0]
                if float(normals[i] @ normals[j]) > 0.99999 and abs(planes[i] - planes[j]) < 1e-4:
                    parent[find(j)] = find(i)
        groups = {}
        for i in range(n):
            if normals[i] is not None:
                groups.setdefault(find(i), []).append(i)
        for g in groups.values():
            out.append((pi, g, normals[g[0]]))
    return out


def mesh_items(mesh):
    """Uma entrada por pedaco plano: sai k*step pela propria normal."""
    items = []
    for pi, idx, n in mesh_patches(mesh):
        d = mesh.parts[pi]
        tris = [d[i * 3:i * 3 + 3, :3].copy() for i in idx]
        ar = sum(float(np.linalg.norm(np.cross(t[1] - t[0], t[2] - t[0]))) for t in tris)

        def faces_at(k, tris=tris, n=n, pi=pi, idx=idx):
            off = n * (k * mesh.step)
            return [Face(t + off, n, (pi, i)) for t, i in zip(tris, idx)]

        def apply(k, n=n, pi=pi, idx=idx):
            for i in idx:
                mesh.parts[pi][i * 3:i * 3 + 3, :3] += n * (k * mesh.step)

        items.append((ar, uniform_solve(faces_at, apply)))
    return items


def mesh_files():
    """Grupos de arquivos no formato do MeshModel (int + 8 floats por vertice); kakashi_susanoo e *_meta tem formato
    proprio e ficam de fora. O Santuario inteiro e um grupo."""
    singles, shrine = [], []
    for f in sorted(glob.glob(os.path.join(ROOT, 'models', 'mesh', '*.bin'))):
        raw = open(f, 'rb').read(4)
        if os.path.getsize(f) != 4 + struct.unpack('>i', raw)[0] * 32:
            continue
        (shrine if 'malevolent_shrine_' in f else singles).append(f)
    return ([shrine] if shrine else []) + [[f] for f in singles]


def check_mesh(paths, fix, verbose):
    mesh = Mesh(paths)
    name = rel(paths[0]) if len(paths) == 1 else 'models/mesh/malevolent_shrine_*'
    pairs = find_pairs(mesh_faces(mesh), mesh.eps)
    if verbose:
        for a, b, ar in pairs:
            print('   %s: triangulo %s x %s area %.5f' % (name, a.owner, b.owner, ar))
    if not pairs or not fix:
        return len(pairs), len(pairs)
    for _ in range(5):
        greedy(mesh_items(mesh), mesh.eps)
        for p, d in zip(paths, mesh.parts):
            mesh_write(p, d)
        mesh = Mesh(paths)
        left = len(find_pairs(mesh_faces(mesh), mesh.eps))
        if not left:
            break
    return len(pairs), left


# ---------------------------------------------------------------- main
def rel(p):
    return os.path.relpath(p, ROOT)


def run(path, kind, load, save, faces_of, items_of, fix, verbose):
    model = load(path)
    if model is None:
        return 0, 0
    eps = EPS_PLANE[kind]
    pairs = find_pairs(faces_of(model), eps)
    if verbose:
        for a, b, ar in pairs:
            print('   %s: peca %d x peca %d area %.4f' % (rel(path), a.owner, b.owner, ar))
    if not pairs or not fix:
        return len(pairs), len(pairs)
    for _ in range(5):
        greedy(items_of(model), eps)
        save(path, model)
        model = load(path)
        left = len(find_pairs(faces_of(model), eps))
        if not left:
            break
    return len(pairs), left


def load_blocky(path):
    m = json.load(open(path, encoding='utf-8'))
    return m if 'root' in m else None


def save_blocky(path, m):
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(m, fh, separators=(',', ':'), ensure_ascii=False)


def load_json(path):
    text = open(path, encoding='utf-8').read()
    m = json.loads(text)
    if not m.get('elements'):
        return None
    m['__pretty'] = '\n' in text.strip()
    return m


def save_json(path, m):
    pretty = m.pop('__pretty', False)
    with open(path, 'w', encoding='utf-8') as fh:
        if pretty:
            json.dump(m, fh, indent=2, ensure_ascii=False)
            fh.write('\n')
        else:
            json.dump(m, fh, separators=(',', ':'), ensure_ascii=False)


def check_blocky(path, fix, verbose):
    return run(path, 'blocky', load_blocky, save_blocky, lambda m: blocky_faces(m)[0], blocky_items, fix, verbose)


def check_json(path, fix, verbose):
    return run(path, 'json', load_json, save_json, lambda m: json_faces(m)[0], json_items, fix, verbose)


def main():
    fix = '--corrigir' in sys.argv
    verbose = '--lista' in sys.argv
    only = [a for a in sys.argv[1:] if not a.startswith('--')]
    kinds = [('blocky', blocky_files, check_blocky), ('json', json_files, check_json), ('mesh', mesh_files, check_mesh)]
    total_before = total_after = 0
    for kind, files, check in kinds:
        if only and kind not in only:
            continue
        nfiles = bad = before = after = 0
        for path in files():
            nfiles += 1
            b, a = check(path, fix, verbose)
            before += b
            after += a
            if b:
                bad += 1
        total_before += before
        total_after += after
        if fix:
            print('%-7s %4d modelos, %4d com par, %5d pares -> %d depois da correcao' % (kind, nfiles, bad, before, after))
        else:
            print('%-7s %4d modelos, %4d com par, %5d pares' % (kind, nfiles, bad, before))
    left = total_after if fix else total_before
    print('TOTAL: %d pares' % left)
    sys.exit(1 if left else 0)


if __name__ == '__main__':
    main()
