# Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
# Converte uma animacao exportada do Blockbench (Animacao > Exportar animacoes, .animation.json) para o formato do mod
# (assets/battlegroundsmod/animations/<skill>.json, lido por KeyframeAnimation).
#
#   python3 tools/poses/blockbench/converter.py <arquivo.animation.json> <skill> [mapa de tempo] [rest] [segura_ate] [length]
# Soco Serio do Saitama (2026-11-27, pose do Stark): golpe dele em 1.4167 cai no soco do jogo (OpmShape.T_P = 2.1 s):
#   mapa 0=1.0,1.25=1.933,1.4167=2.1,1.9167=2.6, rest 0.6,5.95, segura ate 4.6, length 5.95, depois slerp_back(4.6, 5.95, 27).
#
# Sinais: o Blockbench mostra o osso girando com x e y ao contrario do que o KeyframeAnimation faz com o mesmo numero
# (braco pra frente e X negativo no Blockbench e X positivo no arquivo do mod). Para o jogo ficar igual ao que se ve no
# Blockbench: rotacao (x, y, z) -> (-x, -y, z), posicao (x, y, z) -> (-x, y, z). A ordem dos eixos (ZYX) e a mesma nos
# dois, entao poses com angulos grandes (ex.: perna em 168/-57/-174) continuam exatas.
#
# Mapa de tempo: pares "tempo_do_blockbench=tempo_no_jogo" separados por virgula; entre os pares o tempo e esticado
# em linha reta. Chaves extras de repouso (osso em 0) entram com "rest=t1,t2,..." (ex.: comeca parado e volta no fim).
import json
import sys


def time_map(pairs):
    pts = sorted(pairs)

    def f(t):
        if t <= pts[0][0]:
            return pts[0][1] + (t - pts[0][0])
        for (a, a2), (b, b2) in zip(pts, pts[1:]):
            if t <= b:
                return a2 + (t - a) / (b - a) * (b2 - a2)
        return pts[-1][1] + (t - pts[-1][0])
    return f


def convert(src, name, pairs, rest=(), hold_until=None, length=None):
    anim = json.load(open(src))['animations'][name]
    f = time_map(pairs)
    out = []
    end = 0.0
    for bone, chans in anim['bones'].items():
        for target in ('rotation', 'position', 'scale'):
            if target not in chans:
                continue
            raw = chans[target]
            if isinstance(raw, list):
                raw = {'0.0': raw}
            keys = []
            for t, v in sorted(raw.items(), key=lambda kv: float(kv[0])):
                interp = 'linear'
                if isinstance(v, dict):
                    interp = 'catmullrom' if v.get('lerp_mode') == 'catmullrom' else 'linear'
                    v = v.get('post', v.get('pre'))
                x, y, z = (float(c) for c in v)
                if target == 'rotation':
                    x, y = -x, -y
                elif target == 'position':
                    x = -x
                keys.append({'timestamp': round(f(float(t)), 4), 'target': [round(x, 4), round(y, 4), round(z, 4)],
                             'interpolation': interp})
            zero = [1, 1, 1] if target == 'scale' else [0, 0, 0]
            last = keys[-1]
            if hold_until is not None and hold_until > last['timestamp']:
                keys.append({**last, 'timestamp': hold_until})
            for r in rest:
                keys.append({'timestamp': r, 'target': zero, 'interpolation': 'linear'})
            keys.sort(key=lambda k: k['timestamp'])
            end = max(end, keys[-1]['timestamp'])
            out.append({'bone': bone, 'target': target, 'keyframes': keys})
    return {'length': length or end, 'animations': out}


if __name__ == '__main__':
    src, skill = sys.argv[1], sys.argv[2]
    name = next(iter(json.load(open(src))['animations']))
    pairs = [tuple(float(x) for x in p.split('=')) for p in sys.argv[3].split(',')]
    rest = [float(x) for x in sys.argv[4].split(',')] if len(sys.argv) > 4 and sys.argv[4] else []
    hold = float(sys.argv[5]) if len(sys.argv) > 5 else None
    length = float(sys.argv[6]) if len(sys.argv) > 6 else None
    data = convert(src, name, pairs, rest, hold, length)
    path = 'src/main/resources/assets/battlegroundsmod/animations/' + skill + '.json'
    json.dump(data, open(path, 'w'), separators=(',', ':'))
    print(path, data['length'], sum(len(c['keyframes']) for c in data['animations']))


# ---- volta suave ao repouso (giro pelo caminho mais curto) ----
# Entre duas chaves de rotacao, os angulos sao interpolados um a um; com angulos grandes (perna em 168/-57/-174) a volta
# pro 0 da um giro enorme. slerp_back troca esse trecho por chaves intermediarias no caminho mais curto (quaternion),
# cada uma no ramo de angulos mais perto da anterior.
import math


def _q_from_zyx(x, y, z):
    # R = Rz(z) * Ry(y) * Rx(x), em graus (mesma ordem do ModelPart: rotationZYX)
    x, y, z = (math.radians(a) / 2 for a in (x, y, z))
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    return (cz * cy * cx + sz * sy * sx, cz * cy * sx - sz * sy * cx, cz * sy * cx + sz * cy * sx, sz * cy * cx - cz * sy * sx)


def _zyx_from_q(q):
    w, x, y, z = q
    sy = max(-1.0, min(1.0, 2 * (w * y - z * x)))
    ax = math.degrees(math.atan2(2 * (w * x + y * z), 1 - 2 * (x * x + y * y)))
    ay = math.degrees(math.asin(sy))
    az = math.degrees(math.atan2(2 * (w * z + x * y), 1 - 2 * (y * y + z * z)))
    return ax, ay, az


def _near(e, ref):
    a = (e[0] + 180, 180 - e[1], e[2] + 180)
    best = None
    for c in (e, a):
        c = tuple(v + 360 * round((r - v) / 360) for v, r in zip(c, ref))
        d = sum((v - r) ** 2 for v, r in zip(c, ref))
        if best is None or d < best[0]:
            best = (d, c)
    return best[1]


def _slerp(a, b, u):
    dot = sum(p * q for p, q in zip(a, b))
    if dot < 0:
        b, dot = tuple(-q for q in b), -dot
    if dot > 0.9995:
        r = tuple(p + (q - p) * u for p, q in zip(a, b))
    else:
        th = math.acos(dot)
        s1, s2 = math.sin((1 - u) * th) / math.sin(th), math.sin(u * th) / math.sin(th)
        r = tuple(s1 * p + s2 * q for p, q in zip(a, b))
    n = math.sqrt(sum(v * v for v in r))
    return tuple(v / n for v in r)


def slerp_back(data, t0, t1, steps=8):
    for ch in data['animations']:
        if ch['target'] != 'rotation':
            continue
        ks = ch['keyframes']
        i = next((i for i, k in enumerate(ks) if abs(k['timestamp'] - t0) < 1e-6), None)
        if i is None or i + 1 >= len(ks) or abs(ks[i + 1]['timestamp'] - t1) > 1e-6:
            continue
        a, b = ks[i]['target'], ks[i + 1]['target']
        if max(abs(v) for v in a) < 90:
            continue
        qa, qb = _q_from_zyx(*a), _q_from_zyx(*b)
        prev = tuple(a)
        mids = []
        for s in range(1, steps + 1):
            u = s / steps
            e = _near(_zyx_from_q(_slerp(qa, qb, u)), prev)
            prev = e
            mids.append({'timestamp': round(t0 + (t1 - t0) * u, 4), 'target': [round(v, 4) for v in e], 'interpolation': 'linear'})
        ch['keyframes'] = ks[:i + 1] + mids + ks[i + 2:]
