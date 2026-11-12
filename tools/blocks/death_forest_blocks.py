# Textures of the Floresta da Morte blocks (Stark 2026-11-11, the approved preview's colors): the dark leaves in three
# tones, the thick liana, the hanging vine, the giant mushroom's cap, the carnivorous flower (petal, spotted petal,
# mouth), the rotten leaves and the bare roots on the ground, the small glowing mushrooms. Each texture is drawn piece
# by piece from its own seed, no copied pieces. Run from the repo root.
import math
import numpy as np
from PIL import Image

OUT = "src/main/resources/assets/battlegroundsmod/textures/block/"
N = 16
Y, X = np.mgrid[0:N, 0:N] + 0.5


def leaf_mask(cx, cy, length, width, angle):
    out = np.zeros((N, N), bool)
    along = np.zeros((N, N))
    for ox in (-N, 0, N):  # wrap around so the texture tiles without seams
        for oy in (-N, 0, N):
            dx, dy = X - (cx + ox), Y - (cy + oy)
            u = dx * math.cos(angle) + dy * math.sin(angle)
            v = -dx * math.sin(angle) + dy * math.cos(angle)
            t = np.clip(u / length + 0.5, 0, 1)
            half = width * np.sin(np.pi * t) ** 0.8 * (1.1 - 0.2 * t)
            m = (np.abs(u) <= length / 2) & (np.abs(v) <= half)
            along = np.where(m, t, along)
            out |= m
    return out, along


def noise(seed, scale):
    # smooth tiling value noise
    rng = np.random.default_rng(seed)
    g = rng.random((scale, scale))
    fx, fy = X / N * scale, Y / N * scale
    ix, iy = np.floor(fx).astype(int), np.floor(fy).astype(int)
    tx, ty = fx - ix, fy - iy
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a = g[iy % scale, ix % scale] + (g[iy % scale, (ix + 1) % scale] - g[iy % scale, ix % scale]) * tx
    b = g[(iy + 1) % scale, ix % scale] + (g[(iy + 1) % scale, (ix + 1) % scale] - g[(iy + 1) % scale, ix % scale]) * tx
    return a + (b - a) * ty


def leaves(seed, base, holes):
    # long drooping leaves, mostly hanging down
    rng = np.random.default_rng(seed)
    img = np.zeros((N, N, 4))
    filled = np.zeros((N, N), bool)
    for i in range(30):
        m, t = leaf_mask(rng.uniform(0, N), rng.uniform(0, N), rng.uniform(5, 8.5), rng.uniform(1.2, 2.0),
                         math.pi / 2 + rng.uniform(-0.7, 0.7))
        shade = rng.uniform(0.78, 1.15) * (0.9 + 0.14 * t)
        col = np.array(base)[None, None, :] * shade[..., None]
        img[m, :3] = np.clip(col[m], 0, 255)
        img[m, 3] = 255
        filled |= m
    gap = ~filled
    img[gap, :3] = np.array(base) * 0.5
    img[gap, 3] = 255
    img[gap & (rng.random((N, N)) < holes), 3] = 0
    return img


def liana():
    # twisted fibers winding round the vine
    img = np.zeros((N, N, 4))
    base = np.array((77, 74, 43))
    twist = np.sin((X + Y * 0.6) / N * 2 * math.pi * 3) * 0.5 + 0.5
    fib = noise(61, 8)
    k = 0.65 + 0.35 * twist + 0.2 * (fib - 0.5)
    groove = (np.abs(((X + Y * 0.6) % (N / 3)) - N / 6) < 0.8)
    k = np.where(groove, k * 0.6, k)
    img[..., :3] = np.clip(base[None, None, :] * k[..., None], 0, 255)
    img[..., 3] = 255
    return img


def vine():
    # thin strands hanging down with small leaves on them (cutout)
    rng = np.random.default_rng(67)
    img = np.zeros((N, N, 4))
    for s in range(4):
        x0 = rng.uniform(1, N - 1)
        for yy in range(N):
            x = int(x0 + math.sin(yy / 3 + s) * 0.8) % N
            img[yy, x, :3] = (52, 70, 34)
            img[yy, x, 3] = 255
        for j in range(5):
            m, t = leaf_mask(x0 + rng.uniform(-1.5, 1.5), rng.uniform(0, N), rng.uniform(2.5, 3.8), rng.uniform(0.9, 1.3),
                             math.pi / 2 + rng.uniform(-0.9, 0.9))
            c = np.array((62, 91, 42)) * rng.uniform(0.8, 1.15)
            img[m, :3] = np.clip(c[None, :] * (0.9 + 0.15 * t[m])[:, None], 0, 255)
            img[m, 3] = 255
    return img


def cap():
    # rust-red skin, darker blotches, a fine sheen
    n = noise(71, 4) * 0.6 + noise(72, 8) * 0.4
    base = np.array((140, 48, 30))
    k = 0.78 + 0.35 * n
    img = np.zeros((N, N, 4))
    img[..., :3] = np.clip(base[None, None, :] * k[..., None], 0, 255)
    img[..., 3] = 255
    return img


def petal(spotted):
    # thick dark red flesh with veins along it; the spotted one with a big pale blotch
    n = noise(81, 4) * 0.5 + noise(82, 8) * 0.5
    base = np.array((124, 31, 43))
    vein = np.abs(np.sin((X + n * 3) / N * 2 * math.pi * 2)) < 0.12
    k = 0.82 + 0.3 * n
    k = np.where(vein, k * 0.7, k)
    img = np.zeros((N, N, 4))
    img[..., :3] = np.clip(base[None, None, :] * k[..., None], 0, 255)
    if spotted:
        blob = noise(83, 3) > 0.55
        pale = np.array((216, 195, 154)) * (0.9 + 0.15 * noise(84, 8))[..., None]
        img[..., :3] = np.where(blob[..., None], pale, img[..., :3])
    img[..., 3] = 255
    return img


def maw():
    # the inside of the mouth: almost black, wet dark red folds
    n = noise(91, 4) * 0.6 + noise(92, 8) * 0.4
    fold = np.clip((n - 0.5) * 3, 0, 1)
    col = np.array((23, 10, 13))[None, None, :] * (1 - fold[..., None]) + np.array((74, 16, 24))[None, None, :] * fold[..., None]
    img = np.zeros((N, N, 4))
    img[..., :3] = col
    img[..., 3] = 255
    return img


def rotten():
    rng = np.random.default_rng(101)
    img = np.zeros((N, N, 4))
    colors = [(93, 70, 40), (74, 56, 34), (101, 84, 46), (62, 52, 33), (82, 74, 40)]
    for i in range(16):
        m, t = leaf_mask(rng.uniform(0, N), rng.uniform(0, N), rng.uniform(3.5, 5.5), rng.uniform(1.1, 1.8), rng.uniform(0, math.pi))
        c = np.array(colors[rng.integers(len(colors))]) * rng.uniform(0.85, 1.1)
        img[m, :3] = np.clip(c[None, :] * (0.9 + 0.15 * t[m])[:, None], 0, 255)
        img[m, 3] = 255
    return img


def roots():
    # thin roots crossing the ground, branching (cutout)
    rng = np.random.default_rng(111)
    img = np.zeros((N, N, 4))
    for r in range(5):
        x, y, a = rng.uniform(0, N), rng.uniform(0, N), rng.uniform(0, 2 * math.pi)
        w = rng.uniform(0.8, 1.3)
        for s in range(26):
            for ox in (0, 1):
                xi, yi = int(x + ox * math.sin(a)) % N, int(y + ox * math.cos(a)) % N
                if ox == 0 or w > 1:
                    c = np.array((76, 59, 42)) * rng.uniform(0.8, 1.15)
                    img[yi, xi, :3] = c
                    img[yi, xi, 3] = 255
            a += rng.uniform(-0.35, 0.35)
            x += math.cos(a) * 0.7
            y += math.sin(a) * 0.7
    return img


def glowshroom():
    # three small mushrooms: pale stems, glowing pale green caps (cutout, drawn on the cross)
    img = np.zeros((N, N, 4))
    for cx, h, r in ((4.5, 7, 2.2), (9.5, 10, 2.8), (13, 5, 1.6)):
        for yy in range(N - h, N):
            img[yy, int(cx), :3] = (214, 210, 186)
            img[yy, int(cx), 3] = 255
        top = N - h
        for yy in range(top - 2, top + 1):
            for xx in range(N):
                d = abs(xx + 0.5 - cx) / r + max(0, top - yy) / 2.2
                if d <= 1:
                    k = 1.1 - 0.25 * d
                    img[yy, xx, :3] = np.clip(np.array((180, 245, 154)) * k, 0, 255)
                    img[yy, xx, 3] = 255
    return img


def save(name, img):
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA").save(OUT + name + ".png")


save("folhas_da_morte", leaves(141, (47, 68, 35), 0.4))
save("folhas_da_morte_clara", leaves(143, (75, 94, 45), 0.4))
save("folhas_da_morte_escura", leaves(147, (30, 46, 24), 0.3))
save("cipo", liana())
save("trepadeira", vine())
save("chapeu_cogumelo", cap())
save("petala_carnivora", petal(False))
save("petala_carnivora_pintada", petal(True))
save("boca_carnivora", maw())
save("folhas_podres", rotten())
save("raizes_expostas", roots())
save("cogumelo_brilhante", glowshroom())
print("ok")
