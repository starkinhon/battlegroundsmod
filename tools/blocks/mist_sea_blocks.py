# Textures of the Mar da Névoa blocks (Stark 2026-11-11, the approved preview's colors): the coastal pines' needles in
# two tones (the pads' tops #2e4a2d and their dark undersides #203521) and the bridges' rope (#b39a6c), for the block (a
# twisted cord in the chain model's two 3-pixel strips) and the item (a coil). Each texture is drawn from its own seed,
# needle by needle, no copied pieces. Run from the repo root.
import math
import numpy as np
from PIL import Image

OUT = "src/main/resources/assets/battlegroundsmod/textures/"
N = 16


def needles(seed, base, holes):
    # tufts of short straight needles fanning out from a twig, on a dark ground with a few holes
    rng = np.random.default_rng(seed)
    img = np.zeros((N, N, 4))
    img[..., :3] = np.array(base) * 0.45
    img[..., 3] = 255
    filled = np.zeros((N, N), bool)
    for _ in range(9):
        cx, cy = rng.uniform(0, N), rng.uniform(0, N)
        twig = rng.uniform(0, 2 * math.pi)
        for k in range(rng.integers(7, 12)):
            a = twig + rng.uniform(-1.2, 1.2)
            length = rng.uniform(2.5, 4.5)
            sx, sy = cx + math.cos(twig) * rng.uniform(-1.5, 1.5), cy + math.sin(twig) * rng.uniform(-1.5, 1.5)
            shade = rng.uniform(0.8, 1.25)
            for s in np.linspace(0, length, 8):
                x, y = int(sx + math.cos(a) * s) % N, int(sy + math.sin(a) * s) % N
                tip = s / length
                img[y, x, :3] = np.clip(np.array(base) * shade * (0.85 + 0.35 * tip), 0, 255)
                filled[y, x] = True
    gap = ~filled
    img[gap & (rng.random((N, N)) < holes), 3] = 0
    return img


def rope_block(seed):
    # two strips, 3 pixels wide (x 0-2 and 3-5), each a cord of three strands twisting down
    rng = np.random.default_rng(seed)
    img = np.zeros((N, N, 4))
    base = np.array((179, 154, 108))
    for x0 in (0, 3):
        for y in range(N):
            for i in range(3):
                strand = (y + i + x0) % 3
                shade = (0.78, 1.0, 1.12)[strand] * rng.uniform(0.93, 1.05)
                if i == 2 and strand == 0:
                    shade *= 0.85
                img[y, x0 + i, :3] = np.clip(base * shade, 0, 255)
                img[y, x0 + i, 3] = 255
    return img


def rope_item(seed):
    # a coil seen from above: a spiral of cord, lit from the top left
    rng = np.random.default_rng(seed)
    img = np.zeros((N, N, 4))
    base = np.array((179, 154, 108))
    for t in np.linspace(0, 3.2 * 2 * math.pi, 900):
        r = 1.5 + t / (2 * math.pi) * 1.9
        x, y = 7.5 + math.cos(t) * r, 7.5 + math.sin(t) * r
        ix, iy = int(round(x)), int(round(y))
        if 0 <= ix < N and 0 <= iy < N:
            light = 0.85 + 0.25 * (-(math.cos(t) + math.sin(t)) / 1.414) + 0.12 * math.sin(t * 9)
            img[iy, ix, :3] = np.clip(base * light * rng.uniform(0.95, 1.03), 0, 255)
            img[iy, ix, 3] = 255
    # the outline a little darker so the coil reads on any background
    a = img[..., 3] > 0
    edge = a & ~(np.roll(a, 1, 0) & np.roll(a, -1, 0) & np.roll(a, 1, 1) & np.roll(a, -1, 1))
    img[edge, :3] *= 0.75
    return img


def save(path, img):
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA").save(OUT + path + ".png")


save("block/folhas_de_pinheiro", needles(311, (46, 74, 45), 0.18))
save("block/folhas_de_pinheiro_escura", needles(313, (32, 53, 33), 0.1))
save("block/corda", rope_block(317))
save("item/corda", rope_item(319))
