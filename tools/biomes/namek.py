# Namek redone with generated terrain (Stark 2026-11-11: the Dragon Ball's "gran finale"; "pilares mais suaves, com mais
# variações de tamanho e de grossura"; "neblina leve verde, o céu de Namek"). Its own dimension (battlegroundsmod:namek)
# with vanilla's overworld terrain shape (seas, islands, hills) under one biome: blue-green grass, pale and pink
# Namekian rock, green seas over Namekian sand, the rock towers (world/NamekSpireFeature) and the Namekian trees
# (world/NamekTreeFeature); the green haze and the three suns are client/fx/DragonWind's. The noise settings start from
# vanilla 1.21.1's overworld (tools/biomes/overworld_noise_1.21.1.json). Run from the repo root.
import json, os

R = "src/main/resources/data/battlegroundsmod"
ROCK = "battlegroundsmod:namek_rock"
LAYER = "battlegroundsmod:namek_rock_layer"
SAND = "battlegroundsmod:namek_sand"

def block(name):
    return {"type": "minecraft:block", "result_state": {"Name": name}}

def cond(c, then):
    return {"type": "minecraft:condition", "if_true": c, "then_run": then}

def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}

def noise(lo, hi=1.7976931348623157e308):
    return {"type": "minecraft:noise_threshold", "noise": "minecraft:surface", "min_threshold": lo, "max_threshold": hi}

ON_FLOOR = {"type": "minecraft:stone_depth", "offset": 0, "add_surface_depth": False, "secondary_depth_range": 0, "surface_type": "floor"}
UNDER_FLOOR = {"type": "minecraft:stone_depth", "offset": 0, "add_surface_depth": True, "secondary_depth_range": 0, "surface_type": "floor"}
DEEP_FLOOR = {"type": "minecraft:stone_depth", "offset": 0, "add_surface_depth": True, "secondary_depth_range": 6, "surface_type": "floor"}
DRY = {"type": "minecraft:water", "offset": -1, "surface_depth_multiplier": 0, "add_stone_depth": False}
STEEP = {"type": "minecraft:steep"}
NOT = lambda c: {"type": "minecraft:not", "invert": c}

surface = seq(
    cond({"type": "minecraft:vertical_gradient", "random_name": "minecraft:bedrock_floor",
          "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 5}}, block("minecraft:bedrock")),
    cond({"type": "minecraft:above_preliminary_surface"}, seq(
        # the top: grass where it's dry and not a cliff, pink rock on the cliffs, Namekian sand on the beaches and
        # under the sea, a few bare patches of sand inland
        cond(ON_FLOOR, seq(
            cond(DRY, seq(
                cond(STEEP, block(LAYER)),
                cond({"type": "minecraft:y_above", "anchor": {"absolute": 62}, "surface_depth_multiplier": 0, "add_stone_depth": False},
                     seq(cond(noise(0.52), block(SAND)), block("minecraft:grass_block"))),
                block(SAND))),
            block(SAND))),
        cond(UNDER_FLOOR, seq(
            cond(DRY, seq(cond(STEEP, block(ROCK)),
                          cond({"type": "minecraft:y_above", "anchor": {"absolute": 62}, "surface_depth_multiplier": 0, "add_stone_depth": False},
                               block("minecraft:dirt")),
                          block(SAND))),
            block(SAND))),
        cond(DEEP_FLOOR, block(LAYER)))))

ow = json.load(open("tools/biomes/overworld_noise_1.21.1.json"))
ow["default_block"] = {"Name": ROCK}
ow["ore_veins_enabled"] = False
ow["surface_rule"] = surface
os.makedirs(f"{R}/worldgen/noise_settings", exist_ok=True)
with open(f"{R}/worldgen/noise_settings/namek.json", "w") as f:
    json.dump(ow, f, indent=2)
    f.write("\n")

with open(f"{R}/dimension/namek.json", "w") as f:
    json.dump({"type": "battlegroundsmod:namek", "generator": {
        "type": "minecraft:noise", "settings": "battlegroundsmod:namek",
        "biome_source": {"type": "minecraft:fixed", "biome": "battlegroundsmod:namek"}}}, f, indent=2)
    f.write("\n")

# The biome: Namek's green sky going yellow-green at the horizon, green water, blue-green grass and leaves.
biome = {"has_precipitation": False, "temperature": 0.9, "downfall": 0.0,
         "effects": {"sky_color": 0x62C23C, "fog_color": 0xCDE9A0, "water_color": 0x3FBF7A, "water_fog_color": 0x2A8C5A,
                     "grass_color": 0x4FB6D6, "foliage_color": 0x2F86B8,
                     "mood_sound": {"block_search_extent": 8, "offset": 2.0, "sound": "minecraft:ambient.cave", "tick_delay": 6000}},
         "spawners": {"ambient": [], "axolotls": [], "creature": [], "misc": [], "monster": [],
                      "underground_water_creature": [], "water_ambient": [], "water_creature": []},
         "spawn_costs": {},
         "carvers": {"air": ["minecraft:cave", "minecraft:canyon"]},
         "features": [[], [], [], [], ["battlegroundsmod:namek_spire"], [], [], [], [], 
                      ["battlegroundsmod:namek_tree", "battlegroundsmod:namek_grass"], []]}
with open(f"{R}/worldgen/biome/namek.json", "w") as f:
    json.dump(biome, f, indent=2)
    f.write("\n")

def write(path, data):
    with open(f"{R}/worldgen/{path}.json", "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")

for name in ("namek_spire", "namek_tree"):
    write(f"configured_feature/{name}", {"type": f"battlegroundsmod:{name}", "config": {}})
# Towers everywhere, on land and out of the sea: one or two a chunk, now and then a cluster.
write("placed_feature/namek_spire", {"feature": "battlegroundsmod:namek_spire", "placement": [
    {"type": "minecraft:count", "count": {"type": "minecraft:weighted_list", "distribution": [
        {"weight": 4, "data": 0}, {"weight": 4, "data": 1}, {"weight": 1, "data": 2}, {"weight": 1, "data": 3}]}},
    {"type": "minecraft:in_square"},
    {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"},
    {"type": "minecraft:biome"}]})
write("placed_feature/namek_tree", {"feature": "battlegroundsmod:namek_tree", "placement": [
    {"type": "minecraft:count", "count": {"type": "minecraft:weighted_list", "distribution": [
        {"weight": 3, "data": 0}, {"weight": 4, "data": 1}, {"weight": 2, "data": 2}, {"weight": 1, "data": 4}]}},
    {"type": "minecraft:in_square"},
    {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
    {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
    {"type": "minecraft:biome"}]})
# Tufts of the blue grass.
write("configured_feature/namek_grass", {"type": "minecraft:random_patch", "config": {
    "tries": 32, "xz_spread": 7, "y_spread": 3, "feature": {"feature": {"type": "minecraft:simple_block", "config": {
        "to_place": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:short_grass"}}}},
        "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"}}]}}})
write("placed_feature/namek_grass", {"feature": "battlegroundsmod:namek_grass", "placement": [
    {"type": "minecraft:count", "count": 3},
    {"type": "minecraft:in_square"},
    {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
    {"type": "minecraft:biome"}]})
print("ok")
