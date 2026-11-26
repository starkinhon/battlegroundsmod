# Textures of the Tournament of Power arena blocks (Stark 2026-11-26, after his anime frames: "um bloco novo pra fazer o
# chão, que parece esse chão desenhado mas também é esse chão de pedra"). The floor is painted stone: flat cream, peach and
# teal tiles (the drawing on the arena comes from how the blocks are laid, see tools/arenas/arena_torneio.py), each with
# the grain and hairline cracks of stone, and the brown line that outlines the drawing. Under the paint the arena is
# brown rock (what shows when a power digs in). The glow block is the light of the moons floating round the arena.
# Each texture is its own noise, no copied pieces. Run from the repo root.
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

def crack(img, rng, k, steps):
    # a hairline crack wandering across the tile (wraps, so neighbours join without a seam)
    x, y = rng.integers(0, 16, 2)
    dx, dy = rng.choice([-1, 1]), rng.choice([-1, 1])
    for _ in range(steps):
        img[y % 16, x % 16] *= k
        if rng.random() < 0.6:
            x += dx
        else:
            y += dy
        if rng.random() < 0.15:
            dx = -dx if rng.random() < 0.5 else dx

def painted(seed, base, cracks=1):
    rng = np.random.default_rng(seed)
    soft = noise(seed + 1, 8) * 0.6 + noise(seed + 2, 4) * 0.4
    grain = rng.random((16, 16))
    shade = 0.975 + 0.05 * (soft - 0.5) + 0.035 * (grain - 0.5)
    img = np.array(base, float)[None, None, :] * shade[:, :, None]
    # a few pits of the stone under the paint
    for _ in range(4):
        x, y = rng.integers(0, 16, 2)
        img[y, x] *= 0.9
    for _ in range(cracks):
        crack(img, rng, 0.93, int(rng.integers(5, 10)))
    return img

def rock(seed, base, dark):
    rng = np.random.default_rng(seed)
    soft = noise(seed + 1, 8) * 0.5 + noise(seed + 2, 4) * 0.35 + noise(seed + 3, 2) * 0.15
    img = np.array(base, float)[None, None, :] * (0.88 + 0.22 * soft)[:, :, None]
    # strata: two soft darker bands, as in the broken arena's cliffs
    for yy in range(16):
        k = (yy + int(rng.integers(0, 3))) % 7
        if k == 0:
            img[yy, :] = img[yy, :] * 0.8 + np.array(dark, float) * 0.2
    for _ in range(3):
        crack(img, rng, 0.8, int(rng.integers(6, 12)))
    return img

def glow(seed):
    soft = noise(seed + 1, 8) * 0.6 + noise(seed + 2, 4) * 0.4
    base = np.array([255, 250, 236], float)
    return base[None, None, :] * (0.94 + 0.06 * soft)[:, :, None]

save("torneio_piso_creme", painted(9301, (232, 220, 196)))
save("torneio_piso_bege", painted(9302, (214, 160, 124)))
save("torneio_piso_azul", painted(9303, (92, 142, 140)))
save("torneio_piso_azul_escuro", painted(9304, (62, 104, 108)))
save("torneio_linha", painted(9305, (92, 66, 50), cracks=1))
save("torneio_rocha", rock(9306, (128, 106, 84), (70, 56, 44)))
save("torneio_rocha_escura", rock(9307, (88, 74, 60), (50, 40, 32)))
save("torneio_luz", glow(9308))
