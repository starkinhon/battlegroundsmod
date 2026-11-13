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


STYLES = {"speckle": speckle, "bark": bark, "leaves": leaves, "grass_top": grass_top, "glow": glow, "eye": eye,
          "flesh": flesh, "planks": planks, "cross_plant": cross_plant}


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
            if kind == "cross":
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
