# Textures of the Monte Myoboku blocks (Stark 2026-11-12, the approved preview's colors): the spires' warm stone in three
# layers (#8a8072, darker #6e655a, light #a39885), with thin horizontal strata and pits; the giant plants' tissue: the
# stalk (#5d7f2a) with fibres running up it, the leaves (#8cb83a, yellow #d4c13a, dark edge #4f7a26) with fine veins, the
# petals (orange #e2702c, the yellow tip #f2c23d) with streaks running out from the core, and the core (#f6dc6a) dotted
# with pollen. Each texture is drawn from its own seed, no copied pieces. Run from the repo root.
import numpy as np
from PIL import Image

OUT = "src/main/resources/assets/battlegroundsmod/textures/block/"
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


def strata(seed, base):
    rng = np.random.default_rng(seed)
    k = 0.88 + 0.16 * (noise(seed, 4) * 0.6 + noise(seed + 1, 8) * 0.4) + rng.uniform(-0.04, 0.04, (N, N))
    # thin layers running across, wavy
    wave = (Y + 1.5 * np.sin(X / 16 * 2 * np.pi + rng.uniform(0, 6))) % 5
    k[wave < 0.9] *= 0.86
    k[(wave >= 0.9) & (wave < 1.6)] *= 1.06
    k[rng.random((N, N)) < 0.05] *= 0.72
    return shade(base, k)


def stalk(seed, base):
    rng = np.random.default_rng(seed)
    k = 0.9 + 0.1 * noise(seed, 3) + rng.uniform(-0.03, 0.03, (N, N))
    cols = rng.choice(N, 5, replace=False)
    for c in cols:
        k[:, c] *= 1.12
        k[:, (c + 1) % N] *= 0.9
    k *= 0.95 + 0.08 * np.sin(Y / 16 * 2 * np.pi * 2 + rng.uniform(0, 6))[:, :] ** 2
    return shade(base, k)


def leaf(seed, base, vein):
    rng = np.random.default_rng(seed)
    k = 0.9 + 0.14 * noise(seed, 4) + rng.uniform(-0.035, 0.035, (N, N))
    img = shade(base, k)
    # fine veins branching across the tissue
    v = (np.abs(((X + Y * 0.6 + rng.uniform(0, 8)) % 7) - 3.5) < 0.45) | (np.abs(((X - Y * 0.6 + rng.uniform(0, 8)) % 9) - 4.5) < 0.4)
    img[v] = shade(vein, k)[v]
    return img


def petal(seed, base):
    rng = np.random.default_rng(seed)
    k = 0.85 + 0.2 * (1 - Y / N) + rng.uniform(-0.03, 0.03, (N, N))
    for _ in range(5):
        x = rng.integers(0, N)
        k[:, x] *= 1.1
        k[:, (x + 1) % N] *= 0.93
    k *= 0.96 + 0.08 * noise(seed + 2, 4)
    return shade(base, k)


def core(seed, base):
    rng = np.random.default_rng(seed)
    k = 0.9 + 0.15 * noise(seed, 5) + rng.uniform(-0.04, 0.04, (N, N))
    img = shade(base, k)
    dots = rng.random((N, N)) < 0.14
    img[dots] = shade((200, 130, 40), k)[dots]
    return img


def save(name, img):
    a = np.full((N, N, 1), 255.0)
    Image.fromarray(np.clip(np.concatenate([img, a], 2), 0, 255).astype(np.uint8), "RGBA").save(OUT + name + ".png")


save("pedra_do_pico", strata(1801, (138, 128, 114)))
save("pedra_do_pico_escura", strata(1803, (110, 101, 90)))
save("pedra_do_pico_clara", strata(1807, (163, 152, 133)))
save("caule_gigante", stalk(1809, (93, 127, 42)))
save("folha_gigante", leaf(1811, (140, 184, 58), (112, 150, 44)))
save("folha_gigante_amarela", leaf(1813, (212, 193, 58), (180, 160, 44)))
save("folha_gigante_escura", leaf(1817, (79, 122, 38), (64, 100, 30)))
save("petala_gigante", petal(1819, (226, 112, 44)))
save("petala_gigante_ponta", petal(1821, (242, 194, 61)))
save("miolo_de_flor", core(1823, (246, 220, 106)))
