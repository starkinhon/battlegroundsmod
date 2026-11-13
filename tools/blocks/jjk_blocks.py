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
    return with_alpha(c, a)


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
    return opaque(c)


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


STYLES = {"speckle": speckle, "bark": bark, "leaves": leaves, "grass_top": grass_top, "glow": glow, "eye": eye,
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
            elif kind == "cross":
                write(A + "models/block/" + name + ".json", {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout", "textures": {"cross": tx}})
                write(A + "models/item/" + name + ".json", {"parent": "minecraft:item/generated", "textures": {"layer0": tx}})
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
