# Textures of the Floresta da Folha blocks (Stark 2026-11-11, the approved preview's colors): the leaves in three tones
# (light top of a cluster, middle, dark underside) and the fallen leaves lying under the crowns. Each texture is drawn
# leaf by leaf from its own seed (shape, size, angle, shade), no copied pieces. Cutout: pixels are fully on or off.
# Run from the repo root.
import math
import numpy as np
from PIL import Image

OUT = "src/main/resources/assets/battlegroundsmod/textures/block/"
N = 16

def leaf_mask(cx, cy, length, width, angle):
    y, x = np.mgrid[0:N, 0:N] + 0.5
    out = np.zeros((N, N), bool)
    along = np.zeros((N, N))
    for ox in (-N, 0, N):  # wrap around so the texture tiles without seams
        for oy in (-N, 0, N):
            dx, dy = x - (cx + ox), y - (cy + oy)
            u = dx * math.cos(angle) + dy * math.sin(angle)
            v = -dx * math.sin(angle) + dy * math.cos(angle)
            # a leaf: pointed at both ends, widest a little before the middle
            t = np.clip(u / length + 0.5, 0, 1)
            half = width * np.sin(np.pi * t) ** 0.8 * (1.1 - 0.2 * t)
            m = (np.abs(u) <= length / 2) & (np.abs(v) <= half)
            along = np.where(m, t, along)
            out |= m
    return out, along

def leaves(seed, base, holes):
    rng = np.random.default_rng(seed)
    img = np.zeros((N, N, 4))
    filled = np.zeros((N, N), bool)
    for i in range(34):
        m, t = leaf_mask(rng.uniform(0, N), rng.uniform(0, N), rng.uniform(4.5, 7.5), rng.uniform(1.4, 2.3), rng.uniform(0, math.pi))
        shade = rng.uniform(0.82, 1.14) * (0.92 + 0.12 * t)  # lighter toward the tip
        col = np.array(base) * shade[..., None] if np.ndim(shade) else np.array(base) * shade
        img[m, :3] = np.clip(col[m] if np.ndim(col) == 3 else col, 0, 255)
        img[m, 3] = 255
        filled |= m
    # the dark gaps between the leaves are the inside of the cluster
    gap = ~filled
    img[gap, :3] = np.array(base) * 0.55
    img[gap, 3] = 255
    # and some gaps go right through (light passes)
    through = gap & (rng.random((N, N)) < holes)
    img[through, 3] = 0
    return img

def litter(seed):
    rng = np.random.default_rng(seed)
    img = np.zeros((N, N, 4))
    colors = [(165, 138, 62), (184, 116, 46), (143, 154, 58), (111, 79, 42), (196, 150, 70)]
    for i in range(16):
        m, t = leaf_mask(rng.uniform(0, N), rng.uniform(0, N), rng.uniform(3.5, 5.5), rng.uniform(1.1, 1.8), rng.uniform(0, math.pi))
        c = np.array(colors[rng.integers(len(colors))]) * rng.uniform(0.85, 1.1)
        img[m, :3] = np.clip(c[None, :] * (0.9 + 0.15 * t[m])[:, None], 0, 255)
        img[m, 3] = 255
    return img

def save(name, img):
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA").save(OUT + name + ".png")

save("folhas_da_folha", leaves(41, (63, 122, 36), 0.55))
save("folhas_da_folha_clara", leaves(43, (106, 159, 51), 0.55))
save("folhas_da_folha_escura", leaves(47, (42, 90, 28), 0.45))
save("folhas_caidas", litter(53))
print("ok")
