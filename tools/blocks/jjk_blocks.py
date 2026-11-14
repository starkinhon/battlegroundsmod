# The blocks of the new Jujutsu biomes (Stark 2026-11-13, "pode portar tudo"), from the approved previews' colors
# (forja/jujutsu-<bioma>/page.html). One list per biome; each block gets its texture (drawn from its own seed, nothing
# copied between blocks), its blockstate, block and item models, loot table, names (pt_br, en_us) and tags. Rerunning
# writes the same files. Run from the repo root.
import json
import math
import os

import numpy as np
from PIL import Image

A = "src/main/resources/assets/battlegroundsmod/"
D = "src/main/resources/data/"
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


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def shade(base, k):
    return np.clip(np.array(base, float)[None, None, :] * k[..., None], 0, 255)


def opaque(c):
    return np.concatenate([c, np.full((N, N, 1), 255.0)], 2)


def with_alpha(c, a):
    return np.concatenate([c, (a * 255.0)[..., None]], 2)


# ---- texture styles -------------------------------------------------------------------------------------------------

def speckle(seed, base, rough=0.12):
    # soil, ash, sand, mud: soft noise in two sizes and loose grains
    rng = np.random.default_rng(seed)
    k = 0.9 + rough * noise(seed, 4) + rough * 0.6 * noise(seed + 1, 8) + rng.uniform(-rough / 2, rough / 2, (N, N))
    grains = rng.random((N, N)) < 0.1
    k[grains] *= rng.uniform(0.75, 1.15, grains.sum())
    return opaque(shade(base, k))


def bark(seed, base, deep=0.3):
    # bark seen from any side (trunks bend every way): vertical ridges that wander and split, dark furrows between
    rng = np.random.default_rng(seed)
    k = 0.9 + 0.12 * noise(seed, 3) + rng.uniform(-0.04, 0.04, (N, N))
    for _ in range(5):
        x = rng.uniform(0, N)
        ph, wob = rng.uniform(0, 6), rng.uniform(0.4, 1.6)
        d = np.abs(X - (x + wob * np.sin(Y / N * 2 * math.pi * rng.uniform(0.6, 1.4) + ph)))
        k[d < 0.7] *= 1 - deep
        k[(d >= 0.7) & (d < 1.5)] *= 1 + deep * 0.25
    return opaque(shade(base, k))


def leaves(seed, base, holes=0.18, accent=None):
    # a heavy mass of leaves: clumps of light and shade, a few holes to the sky, now and then an accent leaf
    rng = np.random.default_rng(seed)
    k = 0.78 + 0.32 * noise(seed, 5) + rng.uniform(-0.1, 0.1, (N, N))
    c = shade(base, k)
    if accent is not None:
        m = rng.random((N, N)) < 0.06
        c[m] = np.array(accent, float) * rng.uniform(0.85, 1.1, (m.sum(), 1))
    a = np.ones((N, N))
    a[(rng.random((N, N)) < holes) & (noise(seed + 3, 6) < 0.55)] = 0
    return with_alpha(np.clip(c, 0, 255), a)


def grass_top(seed, base, tips):
    # a ground of dry tufts: blades in short strokes over a darker base
    rng = np.random.default_rng(seed)
    k = 0.82 + 0.15 * noise(seed, 4) + rng.uniform(-0.05, 0.05, (N, N))
    c = shade(base, k)
    for _ in range(40):
        x, y = rng.integers(0, N), rng.integers(0, N)
        l = rng.integers(1, 4)
        col = np.array(tips, float) * rng.uniform(0.8, 1.15)
        for j in range(l):
            c[(y - j) % N, (x + (j if rng.random() < 0.3 else 0)) % N] = col
    return opaque(np.clip(c, 0, 255))


def glow(seed, base, core):
    # something that gives light (veins, eyes): a dark base, the bright stuff in cracks and blobs, the core near white
    rng = np.random.default_rng(seed)
    n = noise(seed, 4) * 0.6 + noise(seed + 1, 8) * 0.4
    line = np.exp(-((n - 0.5) ** 2) / 0.004)
    k = 0.55 + 0.2 * noise(seed + 2, 6)
    c = shade(base, k)
    hot = np.clip(line + rng.uniform(0, 0.15, (N, N)), 0, 1)[..., None]
    c = c * (1 - hot) + np.array(core, float)[None, None, :] * hot
    return opaque(np.clip(c, 0, 255))


def eye(seed, white, iris):
    # an eye opened in the bark: the red white, the iris narrowed to a slit, a wet glint
    rng = np.random.default_rng(seed)
    d = np.hypot((X - 7.5) / 7.5, (Y - 7.5) / 4.6)
    c = shade(white, 0.85 + 0.2 * noise(seed, 4))
    veins = noise(seed + 1, 8) > 0.7
    c[veins] *= 0.75
    slit = (np.abs(X - 7.5 + rng.uniform(-0.6, 0.6)) < 1.2) & (np.abs(Y - 7.5) < 6)
    c[slit] = np.array(iris, float)
    c[d > 1] = np.array(white, float) * 0.35
    c[5:7, 9:11] = np.minimum(255, c[5:7, 9:11] + 90)
    return opaque(np.clip(c, 0, 255))


def flesh(seed, base):
    # living flesh: wet folds, darker veins, lighter ridges
    rng = np.random.default_rng(seed)
    n = noise(seed, 5)
    k = 0.75 + 0.45 * n + rng.uniform(-0.05, 0.05, (N, N))
    v = np.abs(noise(seed + 1, 6) - 0.5) < 0.04
    k[v] *= 0.55
    return opaque(shade(base, k))


def planks(seed, base, rot=0.0):
    # boards (rotten or not): rows of 4, a seam between, grain along, rot spots
    rng = np.random.default_rng(seed)
    k = 0.9 + 0.1 * noise(seed, 3) + rng.uniform(-0.04, 0.04, (N, N))
    k[Y % 4 == 3] *= 0.7
    for r in range(4):
        k[r * 4:r * 4 + 3] *= rng.uniform(0.9, 1.08)
    if rot:
        spots = noise(seed + 2, 5) < rot
        k[spots] *= 0.6
    return opaque(shade(base, k))


def cross_plant(seed, base, kind):
    # a plant drawn on a cross: 'vine' hanging strands, 'fungus' a cluster of small caps on stalks
    rng = np.random.default_rng(seed)
    a = np.zeros((N, N))
    c = np.zeros((N, N, 3))
    if kind == "vine":
        for _ in range(4):
            x = rng.uniform(2, 14)
            for y in range(N):
                xx = int(round(x + 1.2 * math.sin(y / 3 + seed)))
                if 0 <= xx < N and rng.random() < 0.92:
                    a[y, xx] = 1
                    c[y, xx] = np.array(base, float) * rng.uniform(0.7, 1.2)
                    if rng.random() < 0.25 and xx + 1 < N:
                        a[y, xx + 1] = 1
                        c[y, xx + 1] = np.array(base, float) * rng.uniform(0.9, 1.3)
    else:
        for _ in range(3):
            x, top = rng.integers(3, 13), rng.integers(4, 10)
            for y in range(top + 2, N):
                a[y, x] = 1
                c[y, x] = np.array(base, float) * 0.45
            w = rng.integers(2, 4)
            for dy in range(2):
                for dx in range(-w + dy, w - dy + 1):
                    if 0 <= x + dx < N:
                        a[top + dy, x + dx] = 1
                        c[top + dy, x + dx] = np.array(base, float) * (1.15 - 0.25 * dy) * rng.uniform(0.9, 1.1)
    return with_alpha(np.clip(c, 0, 255), a)


def thatch(seed, base, rot=0.0):
    # thick straw thatch: long strands lying across, in loose rows that droop a little, dark gaps between bundles
    rng = np.random.default_rng(seed)
    k = 0.8 + 0.12 * noise(seed, 3)
    k = np.repeat(k[..., None], 1, 2)[..., 0]
    for _ in range(70):
        y, x0, l = rng.uniform(0, N), rng.integers(0, N), rng.integers(4, 11)
        sag, v = rng.uniform(-0.6, 0.6), rng.uniform(0.95, 1.3)
        for j in range(l):
            yy = int(y + sag * math.sin(j / l * math.pi)) % N
            k[yy, (x0 + j) % N] = v
    k[(Y % 5 == 4) & (noise(seed + 1, 8) < 0.7)] *= 0.62
    if rot:
        k[noise(seed + 2, 5) < rot] *= 0.66
    return opaque(shade(base, k + rng.uniform(-0.03, 0.03, (N, N))))


def paper(seed, base, frame, torn=0.0):
    # shoji: paper in a wooden lattice; the old ones torn open in places and stained
    rng = np.random.default_rng(seed)
    c = shade(base, 0.9 + 0.08 * noise(seed, 4) + rng.uniform(-0.03, 0.03, (N, N)))
    if torn:
        stain = noise(seed + 1, 5)
        c[stain < 0.35] *= 0.82
        holes = (noise(seed + 2, 6) < torn) & (X % 5 != 0) & (Y % 5 != 0)
        c[holes] = np.array(frame, float) * 0.45
    lat = (X % 5 == 0) | (Y % 5 == 0)
    c[lat] = np.array(frame, float) * rng.uniform(0.85, 1.1, (lat.sum(), 1))
    return opaque(np.clip(c, 0, 255))


def blades(seed, base, head=None, droop=False):
    # reeds and rice: thin blades from the foot, leaning and bending; the reed's brown head, the rice's drooping grain
    rng = np.random.default_rng(seed)
    a = np.zeros((N, N))
    c = np.zeros((N, N, 3))
    for _ in range(7):
        x0, top = rng.uniform(2, 14), rng.integers(1, 7)
        lean = rng.uniform(-2.5, 2.5)
        for y in range(top, N):
            t = (N - 1 - y) / (N - 1 - top)
            xx = int(round(x0 + lean * t * t))
            if 0 <= xx < N:
                a[y, xx] = 1
                c[y, xx] = np.array(base, float) * (0.7 + 0.45 * t) * rng.uniform(0.9, 1.1)
        if head is not None and rng.random() < 0.6:
            xx = int(round(x0 + lean))
            for j in range(3 if not droop else 4):
                yy, xh = top + j, xx + (j // 2 if droop else 0)
                if 0 <= xh < N and 0 <= yy < N:
                    a[yy, xh] = 1
                    c[yy, xh] = np.array(head, float) * rng.uniform(0.85, 1.1)
    return with_alpha(np.clip(c, 0, 255), a)


def beach(seed, base, kind):
    # seen from above, lying on the sand: a fan shell with its ribs, or a five-armed starfish with its bumps
    rng = np.random.default_rng(seed)
    a = np.zeros((N, N))
    c = np.zeros((N, N, 3))
    cx, cy = rng.uniform(6.5, 8.5), rng.uniform(6.5, 8.5)
    ang = np.arctan2(Y + 0.5 - cy, X + 0.5 - cx)
    d = np.hypot(X + 0.5 - cx, Y + 0.5 - cy)
    if kind == "shell":
        rot = rng.uniform(0, 2 * math.pi)
        rel = (ang - rot + math.pi) % (2 * math.pi) - math.pi
        m = (np.abs(rel) < 1.25) & (d < 7.0) | (d < 1.8)
        a[m] = 1
        k = 0.92 + 0.12 * np.cos(rel * 9) - 0.18 * (d / 7.0) ** 3
        c[m] = (np.array(base, float)[None, :] * k[m][:, None])
    else:
        rot = rng.uniform(0, 2 * math.pi)
        arm = np.cos(5 * (ang - rot))
        rr = 2.0 + 4.6 * np.clip(arm, 0, 1) ** 1.2
        m = d < rr
        a[m] = 1
        k = 1.0 - 0.25 * (d / 6.0) + 0.12 * (rng.random((N, N)) < 0.18)
        c[m] = np.array(base, float)[None, :] * k[m][:, None]
    return with_alpha(np.clip(c, 0, 255), a)


def glowing(seed, base, glint):
    # living coral: lumpy, pitted, the pits shining with plankton light
    rng = np.random.default_rng(seed)
    n = noise(seed, 4) * 0.6 + noise(seed + 1, 8) * 0.4
    c = shade(base, 0.72 + 0.45 * n + rng.uniform(-0.05, 0.05, (N, N)))
    pits = (noise(seed + 2, 8) > 0.68) | (rng.random((N, N)) < 0.04)
    c[pits] = np.array(glint, float) * rng.uniform(0.85, 1.1, (pits.sum(), 1))
    return opaque(np.clip(c, 0, 255))


def tiles(seed, base, size=4, grout=None, cracks=0.0):
    # slabs and paving: a grid of squares with grout, each square its own shade; the old ones cracked
    rng = np.random.default_rng(seed)
    c = shade(base, 0.9 + 0.1 * noise(seed, 5) + rng.uniform(-0.03, 0.03, (N, N)))
    cell = (X // size) * 31 + (Y // size) * 17
    c *= (0.93 + 0.12 * ((cell * 2654435761 + seed) % 97) / 97.0)[..., None]
    g = (X % size == 0) | (Y % size == 0)
    c[g] = np.array(grout if grout is not None else np.array(base, float) * 0.7, float)
    if cracks:
        n = noise(seed + 3, 6)
        crack = np.exp(-((n - 0.5) ** 2) / 0.002) > 0.6
        crack &= noise(seed + 4, 3) < cracks
        c[crack] = c[crack] * 0.55
    return opaque(np.clip(c, 0, 255))


def manhole(seed, base, iron):
    # Shibuya's manhole (ambience port, Stark 2026-11-14): the asphalt round it, a cast-iron lid with a raised rim and
    # the city's pattern of rings and spokes in relief; the steam comes out of it (client/fx/ShibuyaAirFx)
    rng = np.random.default_rng(seed)
    c = shade(base, 0.9 + 0.12 * noise(seed, 4) + rng.uniform(-0.05, 0.05, (N, N)))
    dx, dy = X + 0.5 - N / 2, Y + 0.5 - N / 2
    r = np.sqrt(dx * dx + dy * dy)
    ang = np.arctan2(dy, dx)
    lid = r < 7.2
    k = 0.85 + 0.1 * noise(seed + 1, 8) + rng.uniform(-0.04, 0.04, (N, N))
    raised = (r > 6.0) | ((r > 3.2) & (r < 4.2)) | (r < 1.3) | ((r < 6.0) & (np.abs(((ang / (2 * math.pi) * 8) % 1) - 0.5) < 0.09))
    k = np.where(raised, k * 1.35, k * 0.8)
    ic = shade(iron, k)
    c[lid] = ic[lid]
    edge = (r >= 7.2) & (r < 7.9)
    c[edge] = c[edge] * 0.55
    return opaque(np.clip(c, 0, 255))


STYLES = {"manhole": manhole, "tiles": tiles, "speckle": speckle, "bark": bark, "leaves": leaves, "grass_top": grass_top, "glow": glow, "eye": eye,
          "flesh": flesh, "planks": planks, "cross_plant": cross_plant,
          "thatch": thatch, "paper": paper, "blades": blades, "beach": beach, "glowing": glowing}


# ---- the blocks -----------------------------------------------------------------------------------------------------
# (name, model kind, tool, (style, args...), pt name, en name)
# kind: cube | leaves | cross

BIOMES = {
    "floresta_negra": [
        ("madeira_negra", "cube", "axe", ("bark", 3101, rgb("#342a28"), 0.32), "Madeira Negra", "Black Wood"),
        ("madeira_negra_escura", "cube", "axe", ("bark", 3103, rgb("#241c1c"), 0.35), "Madeira Negra Escura", "Dark Black Wood"),
        ("raiz_negra", "cube", "axe", ("bark", 3105, rgb("#3e3129"), 0.22), "Raiz Negra", "Black Root"),
        ("folhas_negras", "leaves", "hoe", ("leaves", 3107, rgb("#18201b"), 0.12), "Folhas Negras", "Black Leaves"),
        ("folhas_negras_musgo", "leaves", "hoe", ("leaves", 3109, rgb("#232c24"), 0.14), "Folhas Negras de Musgo", "Mossy Black Leaves"),
        ("folhas_violeta", "leaves", "hoe", ("leaves", 3111, rgb("#36203f"), 0.14, rgb("#5a3070")), "Folhas Violeta", "Violet Leaves"),
        ("terra_negra", "cube", "shovel", ("speckle", 3113, rgb("#2a2422"), 0.14), "Terra Negra", "Black Soil"),
        ("capim_morto", "cube", "shovel", ("grass_top", 3115, rgb("#3a3529"), rgb("#5e5842")), "Capim Morto", "Dead Grass"),
        ("olho_amaldicoado", "cube", "axe", ("eye", 3117, rgb("#d01818"), rgb("#080204")), "Olho Amaldiçoado", "Cursed Eye"),
        ("veio_amaldicoado", "cube", "pickaxe", ("glow", 3119, rgb("#2c2830"), rgb("#b066ff")), "Veio Amaldiçoado", "Cursed Vein"),
        ("carne_amaldicoada", "cube", "hoe", ("flesh", 3121, rgb("#6e1c2c")), "Carne Amaldiçoada", "Cursed Flesh"),
        ("carne_escura", "cube", "hoe", ("flesh", 3123, rgb("#2a070f")), "Goela", "Maw"),
        ("madeira_vermelha_podre", "cube", "axe", ("planks", 3125, rgb("#5e2218"), 0.35), "Madeira Vermelha Podre", "Rotten Red Wood"),
        ("cipo_negro", "cross", None, ("cross_plant", 3127, rgb("#2a3424"), "vine"), "Cipó Negro", "Black Vine"),
        ("fungo_amaldicoado", "cross", None, ("cross_plant", 3129, rgb("#a45cf0"), "fungus"), "Fungo Amaldiçoado", "Cursed Fungus"),
    ],
    "aldeia": [
        ("capim_encharcado", "cube", "shovel", ("grass_top", 3201, rgb("#46553a"), rgb("#5e6e44")), "Capim Encharcado", "Soaked Grass"),
        ("tabua_do_brejo", "cube", "axe", ("planks", 3203, rgb("#6a5238"), 0.22), "Tábua do Brejo", "Swamp Boards"),
        ("madeira_de_fazenda", "cube", "axe", ("planks", 3205, rgb("#7a6040"), 0.08), "Madeira de Fazenda", "Farmhouse Wood"),
        ("madeira_de_fazenda_escura", "cube", "axe", ("planks", 3207, rgb("#2e241c"), 0.15), "Madeira de Fazenda Escura", "Dark Farmhouse Wood"),
        ("poste_do_brejo", "cube", "axe", ("bark", 3209, rgb("#3c2e22"), 0.25), "Poste do Brejo", "Swamp Post"),
        ("palha_de_telhado", "cube", "hoe", ("thatch", 3211, rgb("#8a7448")), "Palha de Telhado", "Roof Thatch"),
        ("palha_podre", "cube", "hoe", ("thatch", 3213, rgb("#6a5a3a"), 0.4), "Palha Podre", "Rotten Thatch"),
        ("cumeeira", "cube", "axe", ("planks", 3215, rgb("#2a2420"), 0.1), "Cumeeira", "Roof Ridge"),
        ("madeira_queimada", "cube", "axe", ("bark", 3217, rgb("#1c1a18"), 0.4), "Madeira Queimada", "Charred Wood"),
        ("shoji_rasgado", "cube", "axe", ("paper", 3219, rgb("#9a9280"), rgb("#3c2e22"), 0.4), "Shoji Rasgado", "Torn Shoji"),
        ("casca_de_cipreste", "cube", "axe", ("bark", 3221, rgb("#4a4038"), 0.28), "Casca de Cipreste", "Cypress Bark"),
        ("raiz_de_cipreste", "cube", "axe", ("bark", 3223, rgb("#4e4238"), 0.2), "Joelho de Cipreste", "Cypress Knee"),
        ("folhas_de_cipreste", "leaves", "hoe", ("leaves", 3225, rgb("#46523a"), 0.2), "Folhas de Cipreste", "Cypress Leaves"),
        ("folhas_de_cipreste_claras", "leaves", "hoe", ("leaves", 3227, rgb("#56603e"), 0.2), "Folhas Claras de Cipreste", "Light Cypress Leaves"),
        ("barba_de_velho", "cross", None, ("cross_plant", 3229, rgb("#6e7860"), "vine"), "Barba-de-Velho", "Old Man's Beard"),
        ("junco", "cross", None, ("blades", 3231, rgb("#5c6234"), rgb("#4a3020")), "Junco", "Reed"),
        ("arroz", "cross", None, ("blades", 3233, rgb("#5e7a34"), rgb("#b8a860"), True), "Arroz", "Rice"),
        ("madeira_de_torii_desbotada", "cube", "axe", ("planks", 3235, rgb("#9a4a36"), 0.25), "Madeira de Torii Desbotada", "Faded Torii Wood"),
        ("dique_de_barro", "cube", "shovel", ("speckle", 3237, rgb("#5a4a34"), 0.16), "Dique de Barro", "Clay Dike"),
    ],
    "dagon": [
        ("areia_de_dagon", "cube", "shovel", ("speckle", 3301, rgb("#ece2c6"), 0.08), "Areia de Dagon", "Dagon Sand"),
        ("areia_molhada", "cube", "shovel", ("speckle", 3303, rgb("#d2c4a0"), 0.1), "Areia Molhada", "Wet Sand"),
        ("tronco_de_coqueiro", "cube", "axe", ("bark", 3305, rgb("#8a6a48"), 0.18), "Tronco de Coqueiro", "Palm Trunk"),
        ("folha_de_coqueiro", "leaves", "hoe", ("leaves", 3307, rgb("#3e8a2e"), 0.22), "Folha de Coqueiro", "Palm Frond"),
        ("folha_de_coqueiro_clara", "leaves", "hoe", ("leaves", 3309, rgb("#56a03a"), 0.22), "Folha Clara de Coqueiro", "Light Palm Frond"),
        ("coco", "cube", "axe", ("speckle", 3311, rgb("#5a3a1e"), 0.2), "Coco", "Coconut"),
        ("coral_rosa", "cube", "pickaxe", ("glowing", 3313, rgb("#f06a8a"), rgb("#ffb0c8")), "Coral Rosa", "Pink Coral"),
        ("coral_laranja", "cube", "pickaxe", ("glowing", 3315, rgb("#f0904a"), rgb("#ffc890")), "Coral Laranja", "Orange Coral"),
        ("coral_roxo", "cube", "pickaxe", ("glowing", 3317, rgb("#9a5ad0"), rgb("#d0a8ff")), "Coral Roxo", "Purple Coral"),
        ("coral_amarelo", "cube", "pickaxe", ("glowing", 3319, rgb("#e8d04a"), rgb("#fff0a0")), "Coral Amarelo", "Yellow Coral"),
        ("concha", "flat", None, ("beach", 3321, rgb("#f2d8d0"), "shell"), "Concha", "Seashell"),
        ("estrela_do_mar", "flat", None, ("beach", 3323, rgb("#e8603a"), "star"), "Estrela-do-Mar", "Starfish"),
        ("tentaculo_de_dagon", "cube", "pickaxe", ("flesh", 3325, rgb("#b86a5a")), "Tentáculo de Dagon", "Dagon Tentacle"),
        ("tentaculo_de_dagon_escuro", "cube", "pickaxe", ("flesh", 3327, rgb("#6a2e34")), "Tentáculo Escuro de Dagon", "Dark Dagon Tentacle"),
        ("ventosa", "cube", "pickaxe", ("flesh", 3329, rgb("#f0c8b8")), "Ventosa", "Sucker"),
        ("abismo", "cube", "pickaxe", ("speckle", 3331, rgb("#0a0812"), 0.3), "Abismo", "Abyss"),
    ],
    "hanami": [
        ("casca_ancestral", "cube", "axe", ("bark", 3401, rgb("#6a4e36"), 0.28), "Casca Ancestral", "Ancient Bark"),
        ("casca_ancestral_musgo", "cube", "axe", ("bark", 3403, rgb("#4e6a32"), 0.24), "Casca Ancestral com Musgo", "Mossy Ancient Bark"),
        ("folhas_de_hanami", "leaves", "hoe", ("leaves", 3405, rgb("#2a6a28"), 0.14), "Folhas de Hanami", "Hanami Leaves"),
        ("folhas_de_hanami_vivas", "leaves", "hoe", ("leaves", 3407, rgb("#367f2e"), 0.14), "Folhas Vivas de Hanami", "Lush Hanami Leaves"),
        ("folhas_de_hanami_claras", "leaves", "hoe", ("leaves", 3409, rgb("#4f9636"), 0.16, rgb("#7ab84a")), "Folhas Claras de Hanami", "Light Hanami Leaves"),
        ("copa_florida", "leaves", "hoe", ("leaves", 3411, rgb("#f4b8d0"), 0.16, rgb("#fff0f6")), "Copa Florida", "Blooming Canopy"),
        ("petala_gigante_branca", "cube", "hoe", ("flesh", 3413, rgb("#f6f2ec")), "Pétala Gigante Branca", "White Giant Petal"),
        ("petala_gigante_rosa", "cube", "hoe", ("flesh", 3415, rgb("#f28ab4")), "Pétala Gigante Rosa", "Pink Giant Petal"),
        ("petala_gigante_amarela", "cube", "hoe", ("flesh", 3417, rgb("#f4cc40")), "Pétala Gigante Amarela", "Yellow Giant Petal"),
        ("petala_gigante_vermelha", "cube", "hoe", ("flesh", 3419, rgb("#e2483e")), "Pétala Gigante Vermelha", "Red Giant Petal"),
        ("caule_de_flor_gigante", "cube", "axe", ("bark", 3421, rgb("#4a9a38"), 0.12), "Caule de Flor Gigante", "Giant Flower Stem"),
        ("polen_dourado", "cube", "hoe", ("glowing", 3423, rgb("#ffd040"), rgb("#fff4b0")), "Pólen Dourado", "Golden Pollen"),
        ("estaca_de_madeira", "cube", "axe", ("bark", 3425, rgb("#8a6a44"), 0.2), "Estaca de Madeira", "Wooden Stake"),
        ("botao_amaldicoado", "cube", "hoe", ("flesh", 3427, rgb("#5aa040")), "Botão Amaldiçoado", "Cursed Bud"),
        ("ponta_do_botao", "cube", "hoe", ("glow", 3429, rgb("#c04a80"), rgb("#ff9ad0")), "Ponta do Botão", "Bud Tip"),
        ("oco_de_hanami", "cube", "axe", ("glowing", 3431, rgb("#0c0806"), rgb("#2a4a1a")), "Oco de Hanami", "Hanami's Hollow"),
        ("cipo_de_hanami", "cross", None, ("cross_plant", 3433, rgb("#3a8a2e"), "vine"), "Cipó de Hanami", "Hanami Vine"),
    ],
    "jogo": [
        ("basalto_do_jogo", "cube", "pickaxe", ("speckle", 3501, rgb("#3a3634"), 0.14), "Basalto do Jogo", "Jogo's Basalt"),
        ("basalto_escuro_do_jogo", "cube", "pickaxe", ("speckle", 3503, rgb("#2a2624"), 0.16), "Basalto Escuro do Jogo", "Jogo's Dark Basalt"),
        ("cinza_vulcanica", "cube", "shovel", ("speckle", 3505, rgb("#6e6662"), 0.1), "Cinza Vulcânica", "Volcanic Ash"),
        ("basalto_rachado_de_magma", "cube", "pickaxe", ("glow", 3507, rgb("#2a2624"), rgb("#ff7a2a")), "Basalto Rachado de Magma", "Magma-Cracked Basalt"),
        ("coluna_de_basalto", "cube", "pickaxe", ("bark", 3509, rgb("#4a4644"), 0.16), "Coluna de Basalto", "Basalt Column"),
        ("topo_de_coluna", "cube", "pickaxe", ("speckle", 3511, rgb("#5a5552"), 0.1), "Topo de Coluna", "Column Top"),
        ("enxofre", "cube", "pickaxe", ("speckle", 3513, rgb("#d8c040"), 0.2), "Enxofre", "Sulfur"),
        ("rocha_derretida", "cube", "pickaxe", ("glow", 3515, rgb("#3a1e14"), rgb("#ff5a14")), "Rocha Derretida", "Molten Rock"),
        ("escoria", "cube", "shovel", ("speckle", 3517, rgb("#5a3a30"), 0.18), "Escória", "Cinder"),
        ("faixa_de_ferro", "cube", "pickaxe", ("speckle", 3519, rgb("#6a4a3a"), 0.16), "Faixa de Ferro", "Iron Band"),
    ],
    "kyoto": [
        ("chao_de_folhas_vermelhas", "cube", "shovel", ("grass_top", 3601, rgb("#a8382a"), rgb("#d04a30")), "Chão de Folhas Vermelhas", "Red Leaf Litter"),
        ("chao_de_folhas_laranjas", "cube", "shovel", ("grass_top", 3603, rgb("#c06a2a"), rgb("#e88a3a")), "Chão de Folhas Laranjas", "Orange Leaf Litter"),
        ("chao_de_folhas_douradas", "cube", "shovel", ("grass_top", 3605, rgb("#d0a838"), rgb("#f0c850")), "Chão de Folhas Douradas", "Golden Leaf Litter"),
        ("tronco_de_bordo", "cube", "axe", ("bark", 3607, rgb("#4a3428"), 0.26), "Tronco de Bordo", "Maple Trunk"),
        ("casca_escura_de_kyoto", "cube", "axe", ("bark", 3609, rgb("#3a2c24"), 0.3), "Casca Escura de Kyoto", "Kyoto Dark Bark"),
        ("folhas_de_bordo_vermelhas", "leaves", "hoe", ("leaves", 3611, rgb("#c8301e"), 0.16, rgb("#e85a3a")), "Folhas de Bordo Vermelhas", "Red Maple Leaves"),
        ("folhas_de_bordo_carmim", "leaves", "hoe", ("leaves", 3613, rgb("#9a1e18"), 0.16, rgb("#c8301e")), "Folhas de Bordo Carmim", "Crimson Maple Leaves"),
        ("folhas_de_bordo_laranjas", "leaves", "hoe", ("leaves", 3615, rgb("#e0682a"), 0.16, rgb("#f0a040")), "Folhas de Bordo Laranjas", "Orange Maple Leaves"),
        ("folhas_de_bordo_douradas", "leaves", "hoe", ("leaves", 3617, rgb("#e8a830"), 0.16, rgb("#f8d060")), "Folhas de Bordo Douradas", "Golden Maple Leaves"),
        ("folhas_de_ginkgo", "leaves", "hoe", ("leaves", 3619, rgb("#f0c838"), 0.14, rgb("#fff080")), "Folhas de Ginkgo", "Ginkgo Leaves"),
        ("agulhas_de_pinheiro_japones", "leaves", "hoe", ("leaves", 3621, rgb("#2e4a2a"), 0.12), "Agulhas de Pinheiro Japonês", "Japanese Pine Needles"),
        ("folhas_de_bambu", "leaves", "hoe", ("leaves", 3623, rgb("#5a8a34"), 0.28), "Folhas de Bambu", "Bamboo Leaves"),
        ("madeira_clara_de_kyoto", "cube", "axe", ("planks", 3625, rgb("#a07a50"), 0.0), "Madeira Clara de Kyoto", "Kyoto Light Wood"),
        ("folha_de_ouro", "cube", "pickaxe", ("glowing", 3627, rgb("#e8b830"), rgb("#fff0a0")), "Folha de Ouro", "Gold Leaf"),
        ("ouro_escuro", "cube", "pickaxe", ("speckle", 3629, rgb("#a87818"), 0.12), "Ouro Escuro", "Dark Gold"),
        ("telhado_de_casca", "cube", "axe", ("planks", 3631, rgb("#3a3230"), 0.0), "Telhado de Casca", "Bark Roof"),
        ("beiral_de_casca", "cube", "axe", ("planks", 3633, rgb("#2a2422"), 0.0), "Beiral de Casca", "Bark Eave"),
        ("madeira_de_ponte", "cube", "axe", ("planks", 3635, rgb("#8a5a34"), 0.1), "Madeira de Ponte", "Bridge Wood"),
        ("tatame", "cube", "hoe", ("thatch", 3637, rgb("#c8b878")), "Tatame", "Tatami"),
    ],
    "colonia": [
        ("asfalto", "cube", "pickaxe", ("speckle", 3701, rgb("#3a3a40"), 0.12), "Asfalto", "Asphalt"),
        ("asfalto_rachado", "cube", "pickaxe", ("glow", 3703, rgb("#3e3e44"), rgb("#1e1e22")), "Asfalto Rachado", "Cracked Asphalt"),
        ("faixa_de_rua", "cube", "pickaxe", ("speckle", 3705, rgb("#d8d0b0"), 0.1), "Faixa de Rua", "Road Line"),
        ("calcada", "cube", "pickaxe", ("tiles", 3707, rgb("#7a787e"), 8, None, 0.3), "Calçada", "Sidewalk"),
        ("piso_de_lote", "cube", "pickaxe", ("tiles", 3709, rgb("#86827c"), 4), "Piso de Lote", "Lot Tiles"),
        ("concreto_da_colonia", "cube", "pickaxe", ("speckle", 3711, rgb("#8e8c90"), 0.1), "Concreto da Colônia", "Colony Concrete"),
        ("concreto_escuro_da_colonia", "cube", "pickaxe", ("speckle", 3713, rgb("#5e5c64"), 0.12), "Concreto Escuro da Colônia", "Colony Dark Concrete"),
        ("concreto_claro_da_colonia", "cube", "pickaxe", ("speckle", 3715, rgb("#b0aeb0"), 0.08), "Concreto Claro da Colônia", "Colony Light Concrete"),
        ("janela_da_colonia", "cube", "pickaxe", ("paper", 3717, rgb("#3a4a5a"), rgb("#5e5c64")), "Janela da Colônia", "Colony Window"),
        ("janela_acesa_da_colonia", "cube", "pickaxe", ("paper", 3719, rgb("#ffc878"), rgb("#5e5c64")), "Janela Acesa da Colônia", "Colony Lit Window"),
        ("laje", "cube", "pickaxe", ("tiles", 3721, rgb("#6e6a68"), 16, None, 0.5), "Laje", "Floor Slab"),
        ("vergalhao", "post:7,0,7,9,16,9", "pickaxe", ("speckle", 3723, rgb("#6a4a3a"), 0.22), "Vergalhão", "Rebar"),
        ("entulho", "cube", "shovel", ("speckle", 3725, rgb("#7a7674"), 0.26), "Entulho", "Rubble"),
        ("brasa", "cube", "pickaxe", ("glow", 3727, rgb("#2a2624"), rgb("#ff8a2a")), "Brasa", "Embers"),
        ("pneu", "cube", "pickaxe", ("speckle", 3729, rgb("#141414"), 0.2), "Pneu", "Tire"),
        ("cone_de_transito", "post:4,0,4,12,13,12", "pickaxe", ("speckle", 3731, rgb("#e8782a"), 0.1), "Cone de Trânsito", "Traffic Cone"),
        ("grade_de_obra", "post:5,0,5,11,16,11", "pickaxe", ("speckle", 3733, rgb("#a8a8a0"), 0.14), "Grade de Obra", "Work Fence"),
        ("placa_de_aviso", "post:2,3,2,14,14,14", "pickaxe", ("speckle", 3735, rgb("#e8c830"), 0.1), "Placa de Aviso", "Warning Sign"),
        ("mato_de_rua", "cross", None, ("blades", 3737, rgb("#52702e")), "Mato de Rua", "Street Weeds"),
    ],
    "shibuya": [
        ("asfalto_de_shibuya", "cube", "pickaxe", ("speckle", 3803, rgb("#3a3b3e"), 0.1), "Asfalto de Shibuya", "Shibuya Asphalt"),
        ("faixa_branca", "cube", "pickaxe", ("speckle", 3805, rgb("#e8e8e2"), 0.06), "Faixa Branca", "White Road Paint"),
        ("faixa_amarela", "cube", "pickaxe", ("speckle", 3807, rgb("#e0b23a"), 0.08), "Faixa Amarela", "Yellow Road Paint"),
        ("calcada_de_shibuya", "cube", "pickaxe", ("tiles", 3809, rgb("#9c9a94"), 4), "Calçada de Shibuya", "Shibuya Sidewalk"),
        ("calcada_escura_de_shibuya", "cube", "pickaxe", ("tiles", 3811, rgb("#8b8780"), 4), "Calçada Escura de Shibuya", "Shibuya Dark Sidewalk"),
        ("piso_tatil", "cube", "pickaxe", ("tiles", 3813, rgb("#e6c23c"), 2), "Piso Tátil", "Tactile Paving"),
        ("meio_fio", "cube", "pickaxe", ("speckle", 3815, rgb("#b9b7b0"), 0.08), "Meio-fio", "Curb"),
        ("chao_de_lote", "cube", "pickaxe", ("speckle", 3817, rgb("#58585a"), 0.14), "Chão de Lote", "Lot Ground"),
        ("chao_de_beco", "cube", "pickaxe", ("tiles", 3819, rgb("#4a4846"), 8, None, 0.4), "Chão de Beco", "Alley Ground"),
        ("fachada_creme", "cube", "pickaxe", ("tiles", 3821, rgb("#c9c4b8"), 8, None, 0.0), "Fachada Creme", "Cream Facade"),
        ("fachada_cinza", "cube", "pickaxe", ("tiles", 3823, rgb("#a8a49c"), 8, None, 0.0), "Fachada Cinza", "Gray Facade"),
        ("fachada_areia", "cube", "pickaxe", ("tiles", 3825, rgb("#d8cfc0"), 8, None, 0.0), "Fachada Areia", "Sand Facade"),
        ("fachada_marrom", "cube", "pickaxe", ("tiles", 3827, rgb("#8d7a68"), 8, None, 0.0), "Fachada Marrom", "Brown Facade"),
        ("fachada_branca", "cube", "pickaxe", ("tiles", 3829, rgb("#e2ded6"), 8, None, 0.0), "Fachada Branca", "White Facade"),
        ("fachada_grafite", "cube", "pickaxe", ("tiles", 3831, rgb("#6f7378"), 8, None, 0.0), "Fachada Grafite", "Graphite Facade"),
        ("fachada_caramelo", "cube", "pickaxe", ("tiles", 3833, rgb("#b59a7c"), 8, None, 0.0), "Fachada Caramelo", "Caramel Facade"),
        ("fachada_azulada", "cube", "pickaxe", ("tiles", 3835, rgb("#9aa3a8"), 8, None, 0.0), "Fachada Azulada", "Bluish Facade"),
        ("vidro_de_predio_azul", "cube", "pickaxe", ("paper", 3837, rgb("#5f7c8e"), rgb("#5f7c8e")), "Vidro de Prédio Azul", "Blue Building Glass"),
        ("vidro_de_predio_petroleo", "cube", "pickaxe", ("paper", 3839, rgb("#46606e"), rgb("#46606e")), "Vidro de Prédio Petroleo", "Petrol Building Glass"),
        ("vidro_de_predio_verde", "cube", "pickaxe", ("paper", 3841, rgb("#6d8a86"), rgb("#6d8a86")), "Vidro de Prédio Verde", "Green Building Glass"),
        ("vidro_de_predio_escuro", "cube", "pickaxe", ("paper", 3843, rgb("#3c4652"), rgb("#3c4652")), "Vidro de Prédio Escuro", "Dark Building Glass"),
        ("janela_de_predio", "cube", "pickaxe", ("paper", 3845, rgb("#39434c"), rgb("#4b5056")), "Janela de Prédio", "Building Window"),
        ("janela_acesa_quente", "cube", "pickaxe", ("paper", 3847, rgb("#ffd79a"), rgb("#7d8288")), "Janela Acesa Quente", "Warm Lit Window"),
        ("janela_acesa_fria", "cube", "pickaxe", ("paper", 3849, rgb("#dfeaff"), rgb("#7d8288")), "Janela Acesa Fria", "Cool Lit Window"),
        ("montante", "cube", "pickaxe", ("speckle", 3851, rgb("#7d8288"), 0.06), "Montante", "Mullion"),
        ("laje_de_predio", "cube", "pickaxe", ("speckle", 3853, rgb("#9a9ea2"), 0.06), "Laje de Prédio", "Building Slab"),
        ("quina_escura", "cube", "pickaxe", ("speckle", 3855, rgb("#4b5056"), 0.06), "Quina Escura", "Dark Corner"),
        ("telhado_de_predio", "cube", "pickaxe", ("tiles", 3857, rgb("#7c7a74"), 16, None, 0.3), "Telhado de Prédio", "Building Roof"),
        ("parapeito", "cube", "pickaxe", ("speckle", 3859, rgb("#8f8d86"), 0.08), "Parapeito", "Parapet"),
        ("vitrine_acesa", "cube", "pickaxe", ("paper", 3861, rgb("#ffe2b0"), rgb("#8fb0c0")), "Vitrine Acesa", "Lit Shop Window"),
        ("porta_de_vidro", "cube", "pickaxe", ("paper", 3863, rgb("#d9c6a0"), rgb("#3d4146")), "Porta de Vidro", "Glass Door"),
        ("toldo_vermelho", "cube", "axe", ("planks", 3865, rgb("#c23b3b"), 0.0), "Toldo Vermelho", "Red Awning"),
        ("toldo_azul", "cube", "axe", ("planks", 3867, rgb("#2f6db3"), 0.0), "Toldo Azul", "Blue Awning"),
        ("toldo_verde", "cube", "axe", ("planks", 3869, rgb("#2f8a55"), 0.0), "Toldo Verde", "Green Awning"),
        ("toldo_laranja", "cube", "axe", ("planks", 3871, rgb("#d9822b"), 0.0), "Toldo Laranja", "Orange Awning"),
        ("bueiro", "cube", "pickaxe", ("manhole", 3895, rgb("#3a3b3e"), rgb("#4a4a50")), "Bueiro", "Manhole"),
        ("letreiro_vermelho", "neon", "pickaxe", ("speckle", 3873, rgb("#e8423c"), 0.05), "Letreiro Vermelho", "Red Sign"),
        ("letreiro_laranja", "neon", "pickaxe", ("speckle", 3875, rgb("#ff8a1e"), 0.05), "Letreiro Laranja", "Orange Sign"),
        ("letreiro_amarelo", "neon", "pickaxe", ("speckle", 3877, rgb("#ffd23a"), 0.05), "Letreiro Amarelo", "Yellow Sign"),
        ("letreiro_verde", "neon", "pickaxe", ("speckle", 3879, rgb("#38d16a"), 0.05), "Letreiro Verde", "Green Sign"),
        ("letreiro_ciano", "neon", "pickaxe", ("speckle", 3881, rgb("#2fd6e6"), 0.05), "Letreiro Ciano", "Cyan Sign"),
        ("letreiro_azul", "neon", "pickaxe", ("speckle", 3883, rgb("#3a6cff"), 0.05), "Letreiro Azul", "Blue Sign"),
        ("letreiro_rosa", "neon", "pickaxe", ("speckle", 3885, rgb("#ff4fb0"), 0.05), "Letreiro Rosa", "Pink Sign"),
        ("letreiro_branco", "neon", "pickaxe", ("speckle", 3887, rgb("#f4f1ea"), 0.05), "Letreiro Branco", "White Sign"),
        ("letra_de_letreiro", "neon", "pickaxe", ("speckle", 3889, rgb("#ffffff"), 0.02), "Letra de Letreiro", "Sign Letter"),
        ("moldura_de_letreiro", "cube", "pickaxe", ("speckle", 3891, rgb("#2a2c30"), 0.08), "Moldura de Letreiro", "Sign Frame"),
        ("telao", "cube", "pickaxe", ("glowing", 3893, rgb("#202326"), rgb("#7fd0ff")), "Telão", "Big Screen"),
        ("metal_claro", "cube", "pickaxe", ("speckle", 3895, rgb("#9aa0a5"), 0.08), "Metal Claro", "Light Metal"),
        ("metal_escuro", "cube", "pickaxe", ("speckle", 3897, rgb("#4d5257"), 0.08), "Metal Escuro", "Dark Metal"),
        ("caixa_dagua", "cube", "pickaxe", ("planks", 3899, rgb("#b8bcc0"), 0.0), "Caixa d'Água", "Water Tank"),
        ("luz_de_aviao", "post:4,0,4,12,8,12", "pickaxe", ("speckle", 3901, rgb("#ff2a2a"), 0.05), "Luz de Sinalização Aérea", "Aviation Light"),
        ("poste_de_rua", "post:6,0,6,10,16,10", "pickaxe", ("speckle", 3903, rgb("#6d7176"), 0.08), "Poste de Rua", "Street Pole"),
        ("fio_de_poste", "post:7,7,7,9,9,9", "pickaxe", ("speckle", 3905, rgb("#1a1a1c"), 0.05), "Fio de Poste", "Power Line"),
        ("luminaria_de_rua", "cube", "pickaxe", ("glowing", 3907, rgb("#e8e4d8"), rgb("#fff1c8")), "Luminária de Rua", "Street Lamp"),
        ("caixa_de_semaforo", "cube", "pickaxe", ("speckle", 3909, rgb("#2c2e31"), 0.06), "Caixa de Semáforo", "Traffic Light Box"),
        ("luz_vermelha_de_semaforo", "post:2,2,2,14,14,14", "pickaxe", ("glowing", 3911, rgb("#b02020"), rgb("#ff3a2a")), "Luz Vermelha de Semáforo", "Red Traffic Light"),
        ("luz_verde_de_semaforo", "post:2,2,2,14,14,14", "pickaxe", ("glowing", 3913, rgb("#1f9a6a"), rgb("#3affb4")), "Luz Verde de Semáforo", "Green Traffic Light"),
        ("grade_de_protecao", "post:1,0,1,15,10,15", "pickaxe", ("speckle", 3915, rgb("#c8ccd0"), 0.06), "Grade de Proteção", "Guard Rail"),
        ("canteiro", "cube", "shovel", ("speckle", 3917, rgb("#4a3a2c"), 0.2), "Canteiro", "Planter"),
        ("tronco_de_rua", "cube", "axe", ("bark", 3919, rgb("#5a4330"), 0.24), "Tronco de Árvore de Rua", "Street Tree Trunk"),
        ("folhas_de_rua", "leaves", "hoe", ("leaves", 3921, rgb("#4f7a3a"), 0.16), "Folhas de Árvore de Rua", "Street Tree Leaves"),
        ("folhas_de_rua_escuras", "leaves", "hoe", ("leaves", 3923, rgb("#3f6a30"), 0.16), "Folhas Escuras de Árvore de Rua", "Dark Street Tree Leaves"),
        ("maquina_de_bebida_painel", "cube", "pickaxe", ("glowing", 3925, rgb("#f2f2f0"), rgb("#ffffff")), "Painel de Máquina de Bebida", "Vending Machine Panel"),
        ("maquina_de_bebida_vermelha", "cube", "pickaxe", ("paper", 3927, rgb("#c8292c"), rgb("#9aa0a5")), "Máquina de Bebida Vermelha", "Red Vending Machine"),
        ("maquina_de_bebida_azul", "cube", "pickaxe", ("paper", 3929, rgb("#2a62c0"), rgb("#9aa0a5")), "Máquina de Bebida Azul", "Blue Vending Machine"),
        ("maquina_de_bebida_branca", "cube", "pickaxe", ("paper", 3931, rgb("#e8e8e8"), rgb("#9aa0a5")), "Máquina de Bebida Branca", "White Vending Machine"),
        ("maquina_de_bebida_preta", "cube", "pickaxe", ("paper", 3933, rgb("#2a2a2a"), rgb("#9aa0a5")), "Máquina de Bebida Preta", "Black Vending Machine"),
        ("mancha_amaldicoada", "cube", "pickaxe", ("glow", 3935, rgb("#15111a"), rgb("#2a1a34")), "Mancha Amaldiçoada", "Cursed Stain"),
        ("janela_amaldicoada", "cube", "pickaxe", ("glow", 3937, rgb("#3a2950"), rgb("#7a3cc8")), "Janela Amaldiçoada", "Cursed Window"),
        ("piso_abandonado", "cube", "pickaxe", ("tiles", 3939, rgb("#3a3a3c"), 8, None, 0.5), "Piso Abandonado", "Abandoned Floor"),
        ("tabuas_pregadas", "cube", "axe", ("planks", 3941, rgb("#8a6a46"), 0.0), "Tábuas Pregadas", "Nailed Boards"),
        ("fita_de_isolamento", "cube", "pickaxe", ("paper", 3943, rgb("#e2c020"), rgb("#1e1e1e")), "Fita de Isolamento", "Caution Tape"),
        ("talisma_colado", "cube", "axe", ("paper", 3945, rgb("#e9dcc0"), rgb("#b02a20"), 0.1), "Talismã Colado", "Stuck Talisman"),
        ("letreiro_apagado", "cube", "pickaxe", ("speckle", 3947, rgb("#2c2c30"), 0.1), "Letreiro Apagado", "Dead Sign"),
        ("azulejo_do_metro", "cube", "pickaxe", ("tiles", 3949, rgb("#e4e2da"), 4), "Azulejo do Metrô", "Metro Tile"),
        ("faixa_laranja", "cube", "pickaxe", ("tiles", 3951, rgb("#f39a1e"), 4), "Faixa Laranja", "Orange Stripe"),
        ("piso_do_metro", "cube", "pickaxe", ("tiles", 3953, rgb("#b8b2a6"), 8), "Piso do Metrô", "Metro Floor"),
        ("teto_do_metro", "cube", "pickaxe", ("tiles", 3955, rgb("#c8c8c4"), 16), "Teto do Metrô", "Metro Ceiling"),
        ("luz_do_metro", "cube", "pickaxe", ("glowing", 3957, rgb("#f8f8f4"), rgb("#ffffff")), "Luz do Metrô", "Metro Light"),
        ("pilar_do_metro", "cube", "pickaxe", ("speckle", 3959, rgb("#d6d2c8"), 0.05), "Pilar do Metrô", "Metro Pillar"),
        ("catraca", "post:2,0,1,14,14,15", "pickaxe", ("speckle", 3961, rgb("#9aa0a8"), 0.06), "Catraca", "Turnstile"),
        ("borda_da_plataforma", "cube", "pickaxe", ("speckle", 3963, rgb("#dedad0"), 0.05), "Borda da Plataforma", "Platform Edge"),
        ("degrau_do_metro", "cube", "pickaxe", ("tiles", 3965, rgb("#a8a49a"), 16, rgb("#e6c23c")), "Degrau do Metrô", "Metro Step"),
        ("banco_do_metro", "post:1,0,2,15,7,14", "axe", ("planks", 3967, rgb("#5a6a7a"), 0.0), "Banco do Metrô", "Metro Bench"),
        ("placa_do_metro", "cube", "pickaxe", ("glowing", 3969, rgb("#f4f4f0"), rgb("#ffffff")), "Placa do Metrô", "Metro Sign"),
        ("letra_da_placa_do_metro", "cube", "pickaxe", ("speckle", 3971, rgb("#1e2a40"), 0.05), "Letra da Placa do Metrô", "Metro Sign Letter"),
        ("brita", "cube", "shovel", ("speckle", 3973, rgb("#6a645c"), 0.3), "Brita", "Ballast"),
        ("trilho", "post:1,0,1,15,2,15", "pickaxe", ("speckle", 3975, rgb("#8a8e92"), 0.06), "Trilho do Metrô", "Metro Rail"),
        ("concreto_de_tunel", "cube", "pickaxe", ("speckle", 3977, rgb("#7c7a74"), 0.14), "Concreto de Túnel", "Tunnel Concrete"),
        ("luz_de_tunel", "cube", "pickaxe", ("glowing", 3979, rgb("#ffeec8"), rgb("#ffffff")), "Luz de Túnel", "Tunnel Light"),
        ("coroa_de_led", "cube", "pickaxe", ("glowing", 3981, rgb("#d8f4ff"), rgb("#7fe6ff")), "Coroa de LED", "LED Crown"),
        ("aleta_branca", "cube", "pickaxe", ("speckle", 3983, rgb("#e6e8ea"), 0.04), "Aleta Branca", "White Fin"),
        ("veneziana", "cube", "pickaxe", ("planks", 3985, rgb("#5a6066"), 0.0), "Veneziana", "Louver"),
        ("lataria_do_trem", "cube", "pickaxe", ("speckle", 3987, rgb("#c4c8cc"), 0.04), "Lataria do Trem", "Train Body"),
        ("janela_do_trem", "cube", "pickaxe", ("paper", 3989, rgb("#dfe8ec"), rgb("#c4c8cc")), "Janela do Trem", "Train Window"),
        ("porta_do_trem", "cube", "pickaxe", ("speckle", 3991, rgb("#b4b8bc"), 0.04), "Porta do Trem", "Train Door"),
        ("teto_do_trem", "cube", "pickaxe", ("speckle", 3993, rgb("#8c9094"), 0.06), "Teto do Trem", "Train Roof"),
        ("ponta_do_trem", "cube", "pickaxe", ("speckle", 3995, rgb("#a8acb0"), 0.05), "Ponta do Trem", "Train End"),
        ("chassi_do_trem", "cube", "pickaxe", ("speckle", 3997, rgb("#2a2c2e"), 0.1), "Chassi do Trem", "Train Undercarriage"),
        ("para_brisa_do_trem", "cube", "pickaxe", ("paper", 3999, rgb("#1a2228"), rgb("#a8acb0")), "Para-brisa do Trem", "Train Windshield"),
        ("farol_do_trem", "cube", "pickaxe", ("glowing", 4001, rgb("#fff6d0"), rgb("#ffffff")), "Farol do Trem", "Train Headlight"),
    ],
}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(obj if isinstance(obj, str) else json.dumps(obj))


def merge_lang(path, entries):
    with open(path) as f:
        d = json.load(f)
    d.update(entries)
    with open(path, "w") as f:
        json.dump(d, f, ensure_ascii=False, indent=2)
        f.write("\n")


def merge_tag(path, names):
    if os.path.exists(path):
        with open(path) as f:
            d = json.load(f)
    else:
        d = {"replace": False, "values": []}
    for n in names:
        if n not in d["values"]:
            d["values"].append(n)
    write(path, json.dumps(d, indent=2) + "\n")


def loot(name):
    return {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "battlegroundsmod:" + name}],
             "conditions": [{"condition": "minecraft:survives_explosion"}]}], "random_sequence": "battlegroundsmod:blocks/" + name}


def emit():
    pt, en, tags = {}, {}, {}
    for blocks in BIOMES.values():
        for name, kind, tool, tex, ptn, enn in blocks:
            img = STYLES[tex[0]](*tex[1:])
            os.makedirs(A + "textures/block", exist_ok=True)
            Image.fromarray(img.astype(np.uint8), "RGBA").save(A + "textures/block/" + name + ".png")
            write(A + "blockstates/" + name + ".json", {"variants": {"": {"model": "battlegroundsmod:block/" + name}}})
            tx = "battlegroundsmod:block/" + name
            if kind == "flat":
                write(A + "models/block/" + name + ".json", {"parent": "minecraft:block/carpet", "render_type": "minecraft:cutout", "textures": {"wool": tx, "particle": tx}})
                write(A + "models/item/" + name + ".json", {"parent": "minecraft:item/generated", "textures": {"layer0": tx}})
            elif kind.startswith("post:"):
                # a thin box (rebar, a post, a cone, a sign) with the texture on every face
                b = [float(v) for v in kind[5:].split(",")]
                el = {"from": b[:3], "to": b[3:], "faces": {f: {"texture": "#all", **({"cullface": f} if (f == "down" and b[1] == 0) or (f == "up" and b[4] == 16) else {})}
                      for f in ("north", "south", "east", "west", "up", "down")}}
                write(A + "models/block/" + name + ".json", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                      "textures": {"all": tx, "particle": tx}, "elements": [el]})
                write(A + "models/item/" + name + ".json", {"parent": "battlegroundsmod:block/" + name})
            elif kind == "cross":
                write(A + "models/block/" + name + ".json", {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout", "textures": {"cross": tx}})
                write(A + "models/item/" + name + ".json", {"parent": "minecraft:item/generated", "textures": {"layer0": tx}})
            elif kind == "neon":
                # a lit sign: the faces drawn at full light and without the side shading (NeoForge's element data), so
                # the neon reads as lit at night instead of a colored block lit by its own light 9
                el = {"from": [0, 0, 0], "to": [16, 16, 16], "shade": False, "neoforge_data": {"block_light": 15, "sky_light": 15, "ambient_occlusion": False},
                      "faces": {f: {"texture": "#all", "cullface": f} for f in ("north", "south", "east", "west", "up", "down")}}
                write(A + "models/block/" + name + ".json", {"parent": "minecraft:block/block", "textures": {"all": tx, "particle": tx}, "elements": [el]})
                write(A + "models/item/" + name + ".json", {"parent": "battlegroundsmod:block/" + name})
            else:
                m = {"parent": "minecraft:block/cube_all", "textures": {"all": tx}}
                if kind == "leaves":
                    m["render_type"] = "minecraft:cutout_mipped"
                write(A + "models/block/" + name + ".json", m)
                write(A + "models/item/" + name + ".json", {"parent": "battlegroundsmod:block/" + name})
            if kind != "leaves":
                write(D + "battlegroundsmod/loot_table/blocks/" + name + ".json", json.dumps(loot(name), indent=2) + "\n")
            pt["block.battlegroundsmod." + name] = ptn
            en["block.battlegroundsmod." + name] = enn
            if kind == "leaves":
                tags.setdefault("leaves", []).append("battlegroundsmod:" + name)
            if tool:
                tags.setdefault("mineable/" + tool, []).append("battlegroundsmod:" + name)
    merge_lang(A + "lang/pt_br.json", pt)
    merge_lang(A + "lang/en_us.json", en)
    for tag, names in tags.items():
        merge_tag(D + "minecraft/tags/block/" + tag + ".json", names)


if __name__ == "__main__":
    emit()
