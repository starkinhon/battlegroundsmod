# Texture of the Deserto do Vento's dry grass (Stark 2026-11-11): thin curved blades in dry straw tones, some bent by
# the wind toward the same side, drawn blade by blade from its own seed (cutout, drawn on the cross). Run from the
# repo root.
import math
import numpy as np
from PIL import Image

OUT = "src/main/resources/assets/battlegroundsmod/textures/block/"
N = 16


def dry_grass():
    rng = np.random.default_rng(171)
    img = np.zeros((N, N, 4))
    tones = [(196, 170, 108), (178, 150, 92), (214, 192, 132), (160, 132, 80)]
    for b in range(11):
        x = rng.uniform(1.5, N - 1.5)
        h = rng.uniform(6, 13)
        lean = rng.uniform(0.15, 0.6)  # bent by the wind, all the same way
        c = np.array(tones[rng.integers(len(tones))]) * rng.uniform(0.9, 1.08)
        for s in range(int(h * 3)):
            t = s / (h * 3)
            px = x + lean * (t * h) ** 1.4 / 3
            py = N - 1 - t * h
            xi, yi = int(px), int(py)
            if 0 <= xi < N and 0 <= yi < N:
                k = 0.82 + 0.3 * t
                img[yi, xi, :3] = np.clip(c * k, 0, 255)
                img[yi, xi, 3] = 255
    return img


Image.fromarray(np.clip(dry_grass(), 0, 255).astype(np.uint8), "RGBA").save(OUT + "capim_seco.png")
print("ok")
