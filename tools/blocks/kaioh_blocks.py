# Textures of the King Kai's planet blocks (Stark 2026-11-13, after his anime frames: "cria um bloco novo pra essa arena"):
# the lawn of the little planet is one flat cartoon green on every face (the sphere reads as a smooth ball, not as grass
# terraces with dirt sides), the road round the planet is big pale lavender-white flagstones with blue-grey joints, and the
# houses are soft orange plaster with a light yellow cap. Each texture is its own noise, no copied pieces. Run from the repo
# root.
import numpy as np
from PIL import Image

OUT = "src/main/resources/assets/battlegroundsmod/textures/block/"

def noise(seed, scale):
    rng = np.random.default_rng(seed)
    n = 16 // scale
    g = rng.random((n + 1, n + 1))
    g[-1, :] = g[0, :]
    g[:, -1] = g[:, 0]
    y, x = np.mgrid[0:16, 0:16] / scale
    x0, y0 = x.astype(int), y.astype(int)
    fx, fy = x - x0, y - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = g[y0, x0] * (1 - fx) + g[y0, x0 + 1] * fx
    b = g[y0 + 1, x0] * (1 - fx) + g[y0 + 1, x0 + 1] * fx
    return a * (1 - fy) + b * fy

def save(name, img):
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB").save(OUT + name + ".png")

def lawn(seed):
    rng = np.random.default_rng(seed)
    base = np.array([138, 206, 72], float)
    soft = noise(seed + 1, 8) * 0.6 + noise(seed + 2, 4) * 0.4
    img = base[None, None, :] * (0.95 + 0.07 * (soft - 0.5))[:, :, None]
    # a few short blade strokes, lighter on top and darker under, like the anime's painted lawn
    for _ in range(7):
        x, y = rng.integers(0, 16, 2)
        img[y, x] = base * 1.12
        img[(y + 1) % 16, x] = base * 0.88
    return img

def flagstones(seed):
    rng = np.random.default_rng(seed)
    base = np.array([232, 228, 242], float)
    joint = np.array([150, 152, 190], float)
    soft = noise(seed + 1, 8)
    img = base[None, None, :] * (0.97 + 0.05 * soft)[:, :, None]
    # two rows of big stones, the lower row offset by half a stone; each stone a hair different in tone
    for row, off in ((0, 0), (1, 8)):
        for k in range(2):
            x0 = (off + k * 8) % 16
            tone = 1 + rng.uniform(-0.03, 0.03)
            for yy in range(row * 8, row * 8 + 8):
                for xx in range(x0, x0 + 8):
                    img[yy, xx % 16] *= tone
    img[0, :] = joint
    img[8, :] = joint
    for y in range(1, 8):
        img[y, 0] = joint
        img[y, 8] = joint
    for y in range(9, 16):
        img[y, 4] = joint
        img[y, 12] = joint
    return img

def plaster(seed, base):
    rng = np.random.default_rng(seed)
    soft = noise(seed + 1, 8) * 0.7 + noise(seed + 2, 4) * 0.3
    grain = rng.random((16, 16))
    shade = 0.96 + 0.06 * (soft - 0.5) + 0.03 * (grain - 0.5)
    return np.array(base, float)[None, None, :] * shade[:, :, None]

save("kaioh_grama", lawn(8101))
save("kaioh_lajota", flagstones(8102))
save("kaioh_casa", plaster(8103, (240, 160, 64)))
save("kaioh_casa_clara", plaster(8104, (250, 220, 130)))
