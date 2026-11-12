# Textures of the Vale do Fim statue stone (Stark 2026-11-12, the approved preview's colors): weathered carved stone in
# four tones, the light face (#a3a097), the darker block (#918e86), the mossy stone (#6c7a4b, moss creeping over grey) and
# the dark of the belt and face shadows (#55524c). Carved blocks with faint chisel courses, rain stains running down and
# pitting; each texture is drawn from its own seed, no copied pieces. Run from the repo root.
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


def carved(seed, base, stain=3, pit=0.06):
    rng = np.random.default_rng(seed)
    k = 0.88 + 0.16 * (noise(seed, 4) * 0.6 + noise(seed + 1, 8) * 0.4) + rng.uniform(-0.04, 0.04, (N, N))
    # chisel courses: faint diagonal strokes in short runs
    for _ in range(6):
        x, y = rng.integers(0, N), rng.integers(0, N)
        for i in range(rng.integers(2, 5)):
            k[(y + i) % N, (x + i) % N] *= 1.07
    # a worn joint between carved courses, dark with a lit edge under it
    j = rng.integers(5, 12)
    k[j, :] *= 0.84
    k[(j + 1) % N, :] *= 1.05
    # rain stains running down
    for _ in range(stain):
        x, y0 = rng.integers(0, N), rng.integers(0, N // 2)
        for y in range(y0, y0 + rng.integers(5, 12)):
            k[y % N, x] *= 0.9
    k[rng.random((N, N)) < pit] *= 0.75
    return shade(base, k)


def mossy(seed):
    img = carved(seed, (145, 142, 134), stain=2)
    rng = np.random.default_rng(seed + 9)
    m = noise(seed + 3, 5) * 0.65 + noise(seed + 4, 10) * 0.35
    k = 0.85 + 0.3 * noise(seed + 5, 8) + rng.uniform(-0.06, 0.06, (N, N))
    moss = shade((108, 122, 75), k)
    w = np.clip((m - 0.38) * 4.0, 0, 1)[..., None]
    return img * (1 - w) + moss * w


def save(name, img):
    a = np.full((N, N, 1), 255.0)
    Image.fromarray(np.clip(np.concatenate([img, a], 2), 0, 255).astype(np.uint8), "RGBA").save(OUT + name + ".png")


save("pedra_da_estatua", carved(1701, (163, 160, 151)))
save("pedra_da_estatua_escura", carved(1703, (145, 142, 134)))
save("pedra_da_estatua_musgo", mossy(1707))
save("pedra_da_estatua_sombra", carved(1709, (85, 82, 76), stain=1, pit=0.03))
