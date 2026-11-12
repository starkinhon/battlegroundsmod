# Textures of the desert blocks (Stark 2026-11-11, the Deserto do Diabo after his reference: warm orange-yellow sand and
# sandstone rock). Each texture is its own noise (seeded per block), no copied pieces. Run from the repo root.
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

def sand(seed, base):
    rng = np.random.default_rng(seed)
    soft = noise(seed + 1, 8) * 0.6 + noise(seed + 2, 4) * 0.4
    grain = rng.random((16, 16))
    shade = 0.9 + 0.12 * soft + 0.1 * (grain - 0.5)
    # a few darker and lighter grains
    shade[grain > 0.94] += 0.08
    shade[grain < 0.05] -= 0.1
    return np.array(base)[None, None, :] * shade[:, :, None]

def rock(seed, base, light):
    rng = np.random.default_rng(seed)
    y = np.mgrid[0:16, 0:16][0]
    wobble = noise(seed + 3, 8) * 3
    # soft horizontal strata, wavy, light and dark
    strata = np.sin((y + wobble) * 2 * np.pi / 5.33) * 0.5 + 0.5
    soft = noise(seed + 4, 4)
    grain = rng.random((16, 16))
    t = np.clip(0.55 * strata + 0.3 * soft + 0.15 * grain, 0, 1)
    img = np.array(base)[None, None, :] * (1 - t[:, :, None]) + np.array(light)[None, None, :] * t[:, :, None]
    # thin cracks along some strata edges
    crack = (np.abs(np.sin((y + wobble) * 2 * np.pi / 5.33)) < 0.12) & (noise(seed + 5, 4) > 0.55)
    img[crack] *= 0.82
    return img

save("deserto_areia", sand(11, (226, 172, 98)))
save("deserto_rocha", rock(23, (196, 122, 70), (226, 158, 96)))
save("deserto_rocha_clara", rock(37, (222, 170, 104), (240, 204, 140)))
print("ok")
