# Textures of the Vila da Chuva blocks (Stark 2026-11-11, the approved preview's colors): the towers' dark metal in
# panels with seams and rivets (#4a5056, darker #363b40), the reinforcing ring (#5f656b, a raised band with bolts), rust
# in two tones (#6e4630, #8a5a38: flaking, pitted), the windows (dark glass #1b2024 with a frame; lit amber #e0a85c,
# warm in the middle), the pipe (#666b70, ribbed) and the spikes (#3d4348, scratched). Each texture is drawn from its own
# seed, no copied pieces. Run from the repo root.
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


def panels(seed, base):
    rng = np.random.default_rng(seed)
    k = 0.92 + 0.12 * noise(seed, 4) + rng.uniform(-0.03, 0.03, (N, N))
    # one vertical and one horizontal seam, darker, with the light edge under it
    sx, sy = rng.integers(5, 11), rng.integers(4, 12)
    k[:, sx] *= 0.7
    k[:, (sx + 1) % N] *= 1.08
    k[sy, :] *= 0.72
    k[(sy + 1) % N, :] *= 1.07
    for (rx, ry) in [(sx - 2, sy - 2), (sx + 3, sy - 2), (sx - 2, sy + 3), (sx + 3, sy + 3)]:
        k[ry % N, rx % N] *= 1.25
        k[(ry + 1) % N, rx % N] *= 0.8
    # rain streaks: faint darker lines running down
    for _ in range(3):
        x = rng.integers(0, N)
        y0 = rng.integers(0, N)
        for y in range(y0, y0 + rng.integers(4, 10)):
            k[y % N, x] *= 0.9
    return shade(base, k)


def ring(seed, base):
    rng = np.random.default_rng(seed)
    k = 0.95 + 0.1 * noise(seed, 4) + rng.uniform(-0.03, 0.03, (N, N))
    k[2, :] *= 1.2
    k[3:13, :] *= 1.0
    k[13, :] *= 0.7
    k[14:, :] *= 0.82
    k[:2, :] *= 0.82
    for x in range(1, N, 5):
        k[7, x] *= 1.3
        k[8, x] *= 0.75
    return shade(base, k)


def rust(seed, base):
    rng = np.random.default_rng(seed)
    n = noise(seed, 5) * 0.6 + noise(seed + 1, 9) * 0.4
    k = 0.8 + 0.4 * n + rng.uniform(-0.06, 0.06, (N, N))
    pits = rng.random((N, N)) < 0.08
    k[pits] *= 0.65
    flakes = noise(seed + 2, 6) > 0.68
    k[flakes] *= 1.18
    return shade(base, k)


def window(seed, lit):
    rng = np.random.default_rng(seed)
    frame = (X < 2) | (X > 13) | (Y < 2) | (Y > 13)
    if lit:
        d = np.sqrt((X - 7.5) ** 2 + (Y - 8.5) ** 2)
        k = 1.15 - d * 0.035 + rng.uniform(-0.03, 0.03, (N, N))
        img = shade((224, 168, 92), k)
        img[(X == 7) | (X == 8)] *= 0.82  # the bar between the panes
    else:
        k = 0.9 + 0.2 * noise(seed, 3) + rng.uniform(-0.04, 0.04, (N, N))
        img = shade((27, 32, 36), k)
        # a cold reflection running across the glass
        refl = (X + Y > 9) & (X + Y < 12)
        img[refl] = np.clip(img[refl] * 1.9, 0, 255)
        img[(X == 7) | (X == 8)] *= 0.75
    img[frame] = shade((54, 59, 64), 0.95 + 0.1 * noise(seed + 5, 4))[frame]
    return img


def pipe(seed, base):
    rng = np.random.default_rng(seed)
    # a round pipe seen from the side: lit in the middle, dark at the edges; ribs every 8 pixels
    k = 0.75 + 0.35 * np.sin((X + 0.5) / N * np.pi) + rng.uniform(-0.03, 0.03, (N, N))
    k[(Y % 8) == 0] *= 1.2
    k[(Y % 8) == 1] *= 0.8
    rustspots = noise(seed + 3, 5) > 0.72
    img = shade(base, k)
    img[rustspots] = shade((110, 70, 48), k)[rustspots]
    return img


def spike(seed, base):
    rng = np.random.default_rng(seed)
    k = 0.9 + 0.15 * noise(seed, 4) + rng.uniform(-0.04, 0.04, (N, N))
    for _ in range(5):  # scratches
        x, y, dx = rng.integers(0, N), rng.integers(0, N), rng.choice([-1, 1])
        for i in range(rng.integers(3, 7)):
            k[(y + i) % N, (x + dx * i) % N] *= 1.25
    return shade(base, k)


def save(name, img):
    a = np.full((N, N, 1), 255.0)
    Image.fromarray(np.clip(np.concatenate([img, a], 2), 0, 255).astype(np.uint8), "RGBA").save(OUT + name + ".png")


save("metal_da_chuva", panels(411, (74, 80, 86)))
save("metal_da_chuva_escuro", panels(413, (54, 59, 64)))
save("anel_de_metal", ring(417, (95, 101, 107)))
save("ferrugem", rust(419, (110, 70, 48)))
save("ferrugem_clara", rust(421, (138, 90, 56)))
save("janela_escura", window(423, False))
save("janela_acesa", window(427, True))
save("cano_de_metal", pipe(431, (102, 107, 112)))
save("espeto_de_metal", spike(433, (61, 67, 72)))
