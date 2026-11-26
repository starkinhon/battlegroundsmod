"""The Tournament of Power's sky (Stark 2026-11-26, his frames: purple, magenta and blue nebula with stars).

  python3 tools/sky/nebulosa.py [preview equirect png]

Writes the 6 faces of the sky cube the game draws (assets/battlegroundsmod/textures/environment/torneio_<face>.png, see
client/TorneioSky.java) and, when asked, the same sky as one equirectangular picture for the web preview: both come
from one function of the direction, so the preview and the game show the same sky (JOGO = PRÉVIA).
"""
import os, sys
import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'battlegroundsmod',
                   'textures', 'environment')
rng = np.random.default_rng(9501)
G = rng.random((64, 64, 64))

def vnoise(p):
    p = p % 64
    i = np.floor(p).astype(int)
    f = p - i
    f = f * f * (3 - 2 * f)
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (f[..., 0] if dx else 1 - f[..., 0]) * (f[..., 1] if dy else 1 - f[..., 1]) * (f[..., 2] if dz else 1 - f[..., 2])
                out = out + w * G[(i[..., 0] + dx) % 64, (i[..., 1] + dy) % 64, (i[..., 2] + dz) % 64]
    return out

def fbm(p, oct=6):
    s, a, n = 0, 1.0, 0
    for o in range(oct):
        s = s + a * vnoise(p * (2 ** o) + o * 7.31)
        n += a
        a *= 0.5
    return s / n

def sky(d):
    """Colour (0..1) for unit directions d (..., 3)."""
    p = d * 2.1 + 10
    warp = np.stack([fbm(p + 3.1, 4), fbm(p + 9.7, 4), fbm(p + 17.3, 4)], -1)
    q = p + (warp - 0.5) * 2.2
    a = fbm(q)                       # the big clouds
    b = fbm(q * 1.7 + 5)             # the second layer of colour
    c = fbm(q * 3.5 + 11, 4)         # wisps
    cloud = np.clip((a - 0.30) * 2.2, 0, 1) ** 1.2
    wisp = np.clip((c - 0.5) * 3, 0, 1)
    base = np.array([0.045, 0.02, 0.085])
    magenta = np.array([0.80, 0.20, 0.66])
    violet = np.array([0.42, 0.16, 0.78])
    blue = np.array([0.14, 0.22, 0.70])
    pink = np.array([1.0, 0.55, 0.85])
    k = np.clip((b - 0.35) * 2.5, 0, 1)[..., None]
    tone = magenta * k + np.where(b[..., None] < 0.45, blue, violet) * (1 - k)
    glow = np.clip((a - 0.15) * 1.4, 0, 1)[..., None] * 0.22
    col = base + tone * (cloud[..., None] * 0.95 + glow) + violet * (wisp[..., None] * cloud[..., None] * 0.25)
    col = col + pink * (np.clip(cloud - 0.7, 0, 1)[..., None] * 1.0)
    # dark dust lanes cutting the clouds
    dust = np.clip((fbm(q * 2.3 + 23, 4) - 0.55) * 4, 0, 1)
    col = col * (1 - 0.45 * dust[..., None])
    return np.clip(col, 0, 1)

def stars(d, size):
    """Small white stars and a few big ones, from a hash of the direction cell (so faces and the preview agree)."""
    out = np.zeros(d.shape[:-1])
    for scale, thr, gain in ((size * 1.6, 0.9993, 1.0), (size * 0.7, 0.9975, 1.4)):
        cell = np.floor(d * scale).astype(np.int64)
        h = (cell[..., 0] * 73856093 ^ cell[..., 1] * 19349663 ^ cell[..., 2] * 83492791) & 0xFFFFFF
        r = (h % 100000) / 100000.0
        out = np.maximum(out, np.where(r > thr, (r - thr) / (1 - thr) * gain, 0))
    return np.clip(out, 0, 1)

def render(d, size):
    c = sky(d)
    s = stars(d, size)[..., None]
    c = c + s * (1 - c) * 0.95
    return (np.clip(c, 0, 1) ** (1 / 1.05) * 255).astype(np.uint8)

FACES = {
    # face: (right, up, forward) in Minecraft axes (x east, y up, z south); the game draws them the same way
    'up':    ((1, 0, 0), (0, 0, -1), (0, 1, 0)),
    'down':  ((1, 0, 0), (0, 0, 1), (0, -1, 0)),
    'north': ((-1, 0, 0), (0, 1, 0), (0, 0, -1)),
    'south': ((1, 0, 0), (0, 1, 0), (0, 0, 1)),
    'east':  ((0, 0, -1), (0, 1, 0), (1, 0, 0)),
    'west':  ((0, 0, 1), (0, 1, 0), (-1, 0, 0)),
}

def main():
    N = 512
    os.makedirs(OUT, exist_ok=True)
    t = (np.arange(N) + 0.5) / N * 2 - 1
    u, v = np.meshgrid(t, -t)
    for name, (r, up, f) in FACES.items():
        d = u[..., None] * np.array(r) + v[..., None] * np.array(up) + np.array(f)
        d = d / np.linalg.norm(d, axis=-1, keepdims=True)
        Image.fromarray(render(d, N)).save(os.path.join(OUT, 'torneio_%s.png' % name))
    if len(sys.argv) > 1:
        W, H = 2048, 1024
        lon = (np.arange(W) + 0.5) / W * 2 * np.pi - np.pi
        lat = np.pi / 2 - (np.arange(H) + 0.5) / H * np.pi
        lo, la = np.meshgrid(lon, lat)
        # three.js equirect: x = sin(lon)cos(lat)... keep Minecraft axes: x east, y up, z south
        d = np.stack([np.cos(la) * np.sin(lo), np.sin(la), -np.cos(la) * np.cos(lo)], -1)
        Image.fromarray(render(d, N)).save(sys.argv[1])

if __name__ == '__main__':
    main()
