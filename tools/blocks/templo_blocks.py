# Textures of the Templos Antigos blocks (Jujutsu, Stark 2026-11-12, the approved preview's colors): the vermilion
# lacquered wood (#c9452a) with its grain and sheen, the torii's black lacquer (#1f1c1c), the courtyard's raked white
# gravel (#d9d6cc), the shoji (dark wood #5a3d27 grid over warm paper), the white plaster (#ece6d8), the stone lantern's
# side with the lit fire box (#ffbe5c), the shimenawa's braided straw (#d8c9a0), the shide's zigzag paper strips, the
# cedars' needles (#2f4a2c, light #3a5634) and the sakuras' blossom (#e89ab0, #f6cdd8, the almost white #fbeaf0). Each
# texture is drawn from its own seed, no copied pieces. Run from the repo root.
import math

import numpy as np
from PIL import Image

OUT = "src/main/resources/assets/battlegroundsmod/textures/"
N = 16
Y, X = np.mgrid[0:N, 0:N]


def noise(seed, scale):
    rng = np.random.default_rng(seed)
    g = rng.random((scale, scale))
    fx, fy = (X + 0.5) / N * scale, (Y + 0.5) / N * scale
    ix, iy = np.floor(fx).astype(int), np.floor(fy).astype(int)
    tx, ty = fx - ix, fy - iy
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a = g[iy % scale, ix % scale] + (g[iy % scale, (ix + 1) % scale] - g[iy % scale, ix % scale]) * tx
    b = g[(iy + 1) % scale, ix % scale] + (g[(iy + 1) % scale, (ix + 1) % scale] - g[(iy + 1) % scale, ix % scale]) * tx
    return a + (b - a) * ty


def shade(base, k):
    return np.clip(np.array(base, float)[None, None, :] * k[..., None], 0, 255)


def opaque(rgb):
    return np.concatenate([rgb, np.full((N, N, 1), 255.0)], 2)


def lacquer(seed, base, grain):
    # wood under lacquer: long vertical grain lines that wander a little, and a soft vertical sheen band
    rng = np.random.default_rng(seed)
    k = 0.92 + 0.08 * noise(seed, 3) + rng.uniform(-0.02, 0.02, (N, N))
    for _ in range(6):
        x = rng.uniform(0, N)
        wob = rng.uniform(0.5, 1.4)
        ph = rng.uniform(0, 6)
        line = np.abs(X - (x + wob * np.sin(Y / N * 2 * math.pi + ph))) < 0.6
        k[line] *= 1 - grain * rng.uniform(0.6, 1.0)
    sheen = rng.uniform(4, 11)
    k *= 1 + 0.1 * np.exp(-((X - sheen) ** 2) / 6)
    return opaque(shade(base, k))


def raked_gravel(seed, base):
    # small pebbles, and the rake's grooves running across every 4 pixels
    rng = np.random.default_rng(seed)
    k = 0.94 + 0.08 * noise(seed, 8) + rng.uniform(-0.05, 0.05, (N, N))
    groove = (Y + np.round(0.6 * np.sin(X / N * 2 * math.pi * 2 + rng.uniform(0, 6)))) % 4
    k[groove == 0] *= 0.86
    k[groove == 1] *= 1.04
    pebbles = rng.random((N, N)) < 0.12
    k[pebbles] *= rng.uniform(0.82, 1.08, pebbles.sum())
    return opaque(shade(base, k))


def shoji(seed, wood, paper):
    # paper panes with a faint fibre, behind a dark wood grid: frame on the edge, two bars each way
    rng = np.random.default_rng(seed)
    k = 0.95 + 0.06 * noise(seed, 6) + rng.uniform(-0.015, 0.015, (N, N))
    img = shade(paper, k)
    bars = (X == 0) | (X == 15) | (Y == 0) | (Y == 15) | (X == 5) | (X == 10) | (Y == 5) | (Y == 10)
    wk = 0.9 + 0.15 * noise(seed + 1, 4) + rng.uniform(-0.04, 0.04, (N, N))
    img[bars] = shade(wood, wk)[bars]
    # the light falls from above: each bar a bit darker on its lower edge
    under = np.roll(bars, 1, 0) & ~bars
    img[under] *= 0.9
    return opaque(img)


def plaster(seed, base):
    # smooth lime plaster with soft trowel patches and a hairline crack or two
    rng = np.random.default_rng(seed)
    k = 0.96 + 0.05 * noise(seed, 4) + 0.02 * noise(seed + 1, 8) + rng.uniform(-0.012, 0.012, (N, N))
    for _ in range(2):
        x, y = rng.uniform(0, N), rng.uniform(0, N)
        a = rng.uniform(0, math.pi)
        for s in np.linspace(0, rng.uniform(3, 6), 14):
            a += rng.uniform(-0.5, 0.5)
            x, y = x + math.cos(a) * 0.45, y + math.sin(a) * 0.45
            k[int(y) % N, int(x) % N] *= 0.88
    return opaque(shade(base, k))


def lantern_side(seed, stone, fire):
    # carved stone around a window of warm light, with a thin stone cross over the window
    rng = np.random.default_rng(seed)
    k = 0.9 + 0.14 * noise(seed, 5) + rng.uniform(-0.05, 0.05, (N, N))
    img = shade(stone, k)
    win = (X >= 4) & (X <= 11) & (Y >= 4) & (Y <= 12)
    glow = 1.0 - 0.25 * (np.abs(X - 7.5) / 4 + np.abs(Y - 8) / 5)
    img[win] = shade(fire, glow * (0.96 + 0.06 * noise(seed + 1, 4)))[win]
    cross = win & ((X == 7) | (X == 8) | (Y == 8))
    img[cross] = shade(stone, k * 0.75)[cross]
    rim = ((X == 3) | (X == 12)) & (Y >= 3) & (Y <= 13) | ((Y == 3) | (Y == 13)) & (X >= 3) & (X <= 12)
    img[rim] *= 0.8
    return opaque(img)


def straw_braid(seed, base):
    # thick straw rope: strands crossing at a slant, each strand with fine straw lines along it
    rng = np.random.default_rng(seed)
    band = ((X + Y) // 4) % 2
    k = np.where(band == 0, 1.05, 0.88) + rng.uniform(-0.04, 0.04, (N, N))
    k *= 1 - 0.12 * (((X - Y) % 4) == 0)
    k *= 0.95 + 0.08 * noise(seed, 4)
    return opaque(shade(base, k))


def shide(seed, paper):
    # two strips of folded paper hanging in a zigzag, on a clear ground
    rng = np.random.default_rng(seed)
    img = np.zeros((N, N, 4))
    for x0 in (3, 9):
        for y in range(1, N):
            seg = (y - 1) // 4
            off = 0 if seg % 2 == 0 else 2
            for dx in range(3):
                x = x0 + off + dx
                light = 1.0 if dx < 2 else 0.86
                light *= 0.97 if seg % 2 else 1.0
                img[y, x, :3] = np.clip(np.array(paper) * light * rng.uniform(0.97, 1.02), 0, 255)
                img[y, x, 3] = 255
    # the cord they hang from
    img[0, 2:14, :3] = (200, 184, 140)
    img[0, 2:14, 3] = 255
    return img


def needles(seed, base, holes):
    # tufts of short straight needles fanning out from a twig, on a dark ground with a few holes
    rng = np.random.default_rng(seed)
    img = np.zeros((N, N, 4))
    img[..., :3] = np.array(base) * 0.5
    img[..., 3] = 255
    filled = np.zeros((N, N), bool)
    for _ in range(10):
        cx, cy = rng.uniform(0, N), rng.uniform(0, N)
        twig = rng.uniform(0, 2 * math.pi)
        for _ in range(rng.integers(7, 12)):
            a = twig + rng.uniform(-1.1, 1.1)
            length = rng.uniform(2.0, 4.0)
            sx, sy = cx + math.cos(twig) * rng.uniform(-1.5, 1.5), cy + math.sin(twig) * rng.uniform(-1.5, 1.5)
            sh = rng.uniform(0.82, 1.22)
            for s in np.linspace(0, length, 8):
                x, y = int(sx + math.cos(a) * s) % N, int(sy + math.sin(a) * s) % N
                tip = s / length
                img[y, x, :3] = np.clip(np.array(base) * sh * (0.85 + 0.3 * tip), 0, 255)
                filled[y, x] = True
    img[~filled & (rng.random((N, N)) < holes), 3] = 0
    return img


def blossom(seed, base, holes):
    # clusters of five-petal flowers with a darker center, on a ground of petals with gaps
    rng = np.random.default_rng(seed)
    img = np.zeros((N, N, 4))
    k = 0.86 + 0.1 * noise(seed, 4)
    img[..., :3] = shade(base, k)
    img[..., 3] = 255
    covered = np.zeros((N, N), bool)
    for _ in range(9):
        cx, cy = rng.integers(0, N), rng.integers(0, N)
        light = rng.uniform(0.98, 1.12)
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            x, y = (cx + dx) % N, (cy + dy) % N
            img[y, x, :3] = np.clip(np.array(base) * light * (1.06 if (dx or dy) else 1.0), 0, 255)
            covered[y, x] = True
        img[cy, cx, :3] = np.clip(np.array(base) * np.array((0.86, 0.7, 0.75)), 0, 255)
    img[~covered & (rng.random((N, N)) < holes), 3] = 0
    return img


def save(path, img):
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA").save(OUT + path + ".png")


save("block/madeira_vermelha", lacquer(2101, (201, 69, 42), 0.12))
save("block/laca_preta", lacquer(2103, (40, 36, 36), 0.25))
save("block/cascalho_branco", raked_gravel(2107, (217, 214, 204)))
save("block/shoji", shoji(2109, (90, 61, 39), (236, 226, 200)))
save("block/reboco", plaster(2111, (236, 230, 216)))
save("block/lanterna_de_pedra_acesa", lantern_side(2113, (154, 150, 138), (255, 190, 92)))
save("block/shimenawa", straw_braid(2117, (216, 201, 160)))
save("block/shide", shide(2119, (244, 242, 234)))
save("block/folhas_de_cedro", needles(2121, (47, 74, 44), 0.14))
save("block/folhas_de_cedro_clara", needles(2123, (58, 86, 52), 0.18))
save("block/folhas_de_sakura_escura", blossom(2127, (232, 154, 176), 0.2))
save("block/folhas_de_sakura_clara", blossom(2129, (246, 205, 216), 0.2))
save("block/folhas_de_sakura_branca", blossom(2131, (251, 234, 240), 0.22))
