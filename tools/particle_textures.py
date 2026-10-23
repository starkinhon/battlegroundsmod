"""Generates the soft smoke cloud and light streak particle textures (procedural, written from scratch).

Usage: python3 tools/particle_textures.py src/main/resources/assets/battlegroundsmod/textures/particle
"""
import sys
import numpy as np
from PIL import Image

OUT = sys.argv[1]
N = 64
SHAPES = 4
FRAMES = 6


def value_noise(rng, size, cells):
    grid = rng.random((cells + 1, cells + 1))
    grid[-1, :] = grid[0, :]
    grid[:, -1] = grid[:, 0]
    t = np.linspace(0, cells, size, endpoint=False)
    i = t.astype(int)
    f = t - i
    f = f * f * (3 - 2 * f)
    a = grid[np.ix_(i, i)]
    b = grid[np.ix_(i, i + 1)]
    c = grid[np.ix_(i + 1, i)]
    d = grid[np.ix_(i + 1, i + 1)]
    fy = f[:, None]
    fx = f[None, :]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fbm(rng, size, base=3, octaves=5):
    total = np.zeros((size, size))
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        total += value_noise(rng, size, base * 2 ** o) * amp
        norm += amp
        amp *= 0.5
    return total / norm


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


yy, xx = np.mgrid[0:N, 0:N]
cx = (xx + 0.5) / N * 2 - 1
cy = (yy + 0.5) / N * 2 - 1

index = 0
for s in range(SHAPES):
    rng = np.random.default_rng(1000 + s)
    # A few overlapping round lumps make the billowy outline, noise breaks it up; always fades out before the
    # edge of the square.
    lumps = np.zeros((N, N))
    for _ in range(6):
        ox, oy = rng.uniform(-0.28, 0.28, 2)
        r = rng.uniform(0.45, 0.62)
        d = np.sqrt((cx - ox) ** 2 + (cy - oy) ** 2) / r
        lumps = np.maximum(lumps, 1 - d * d)
    big = fbm(rng, N, 2, 5)
    fine = fbm(rng, N, 6, 3)
    radius = np.sqrt(cx ** 2 + cy ** 2)
    mask = smoothstep(1.0, 0.7, radius)
    density = (lumps * 0.9 + (big - 0.5) * 0.7 + (fine - 0.5) * 0.1) * mask
    # Light from above: the top of the cloud is brighter, the base darker, soft bumps from the big noise.
    gy, gx = np.gradient(big)
    shade = 0.8 + 0.2 * (1 - (cy + 1) / 2) + np.clip(-gy * 10, -0.1, 0.1)
    shade *= 0.96 + 0.08 * fine
    for f in range(FRAMES):
        k = f / (FRAMES - 1)
        # Later frames erode: the edge eats in and the thin parts tear into wisps.
        edge = -0.05 + k * 0.3
        alpha = smoothstep(edge, edge + 0.6, density) * (1 - 0.4 * k)
        lum = np.clip(shade * 235, 0, 255)
        img = np.zeros((N, N, 4), dtype=np.uint8)
        img[..., 0] = lum
        img[..., 1] = lum
        img[..., 2] = lum
        img[..., 3] = np.clip(alpha * 255, 0, 255)
        Image.fromarray(img, "RGBA").save(f"{OUT}/cloud_{index}.png")
        index += 1

# Light streak: across the width a bright core with a soft glow, along the length thin pointed ends.
W, H = 16, 128
u = (np.arange(W) + 0.5) / W * 2 - 1
v = (np.arange(H) + 0.5) / H
across = np.exp(-(u / 0.22) ** 2) * 0.75 + np.exp(-(u / 0.55) ** 2) * 0.25
along = np.sin(np.pi * v) ** 0.6 * (0.55 + 0.45 * (1 - v))
alpha = np.clip(along[:, None] * across[None, :], 0, 1)
core = np.exp(-(u / 0.3) ** 2)[None, :] * along[:, None]
lum = 215 + 40 * np.clip(core, 0, 1)
img = np.zeros((H, W, 4), dtype=np.uint8)
img[..., 0] = lum
img[..., 1] = lum
img[..., 2] = lum
img[..., 3] = np.clip(alpha * 255, 0, 255)
Image.fromarray(img, "RGBA").save(f"{OUT}/streak.png")
print("cloud sprites:", index)
