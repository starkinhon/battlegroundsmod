#!/usr/bin/env python3
# Generates the structures' loot tables (data/battlegroundsmod/loot_table/estruturas/*.json), Stark 2026-11-12:
# "loot em todas as estruturas, baseado nas estruturas", mod items plus useful Minecraft things (diamond, iron, ender
# pearl, since no normal mobs spawn in the Souls world; gunpowder in the deserts). Run: python3 tools/loot/gen_loot.py
# Entry: (item, weight, min, max). "M:" = battlegroundsmod item, otherwise minecraft.
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'data', 'battlegroundsmod',
                   'loot_table', 'estruturas')


def item(name):
    return 'battlegroundsmod:' + name[2:] if name.startswith('M:') else 'minecraft:' + name


def entry(name, weight=1, lo=1, hi=1):
    e = {'type': 'minecraft:item', 'name': item(name)}
    if weight != 1:
        e['weight'] = weight
    if (lo, hi) != (1, 1):
        e['functions'] = [{'function': 'minecraft:set_count',
                           'count': lo if lo == hi else {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]
    return e


def pool(rolls, entries, chance=None):
    p = {'rolls': rolls if isinstance(rolls, int) else {'type': 'minecraft:uniform', 'min': rolls[0], 'max': rolls[1]},
         'entries': [entry(*e) if isinstance(e, tuple) else entry(e) for e in entries]}
    if chance is not None:
        p['conditions'] = [{'condition': 'minecraft:random_chance', 'chance': chance}]
    return p


def thing(name, chance, lo=1, hi=1):
    # one item with its own chance
    return pool(1, [(name, 1, lo, hi)], chance)


def table(*pools):
    return {'type': 'minecraft:chest', 'pools': list(pools)}


# what every bigger chest can also have (the vanilla useful things)
def useful(rolls=(2, 4), desert=False):
    e = [('iron_ingot', 10, 2, 6), ('gold_ingot', 5, 1, 4), ('ender_pearl', 6, 1, 3), ('diamond', 2, 1, 2),
         ('coal', 6, 3, 8), ('redstone', 4, 3, 8), ('lapis_lazuli', 3, 2, 6), ('emerald', 2, 1, 3),
         ('experience_bottle', 3, 2, 5), ('bread', 5, 2, 5), ('cooked_beef', 4, 2, 4), ('arrow', 3, 4, 12),
         ('obsidian', 1, 1, 3)]
    if desert:
        e.append(('gunpowder', 9, 2, 7))
    return pool(rolls, e)


T = {}

# ---------------------------------------------------------------- Dragon Ball
# Capsule Corp: Bulma's lab.
T['capsula'] = table(thing('M:radar_dragao', 0.15), thing('M:scouter', 0.15), thing('M:senzu', 0.35, 1, 2),
                     thing('M:cristal_multiversal', 0.15),
                     pool((3, 5), [('redstone', 8, 4, 12), ('copper_ingot', 8, 4, 10), ('iron_ingot', 8, 3, 8),
                                   ('gold_ingot', 4, 2, 5), ('quartz', 5, 4, 10), ('diamond', 3, 1, 3),
                                   ('ender_pearl', 4, 1, 3), ('glowstone_dust', 3, 3, 8), ('comparator', 1),
                                   ('repeater', 2, 1, 3)]))
# Goku's house (grandpa Gohan's): the four-star ball, food from the mountains.
T['goku'] = table(thing('M:esfera_dragao_4', 0.25), thing('M:senzu', 0.2),
                  pool((3, 5), [('cooked_beef', 6, 3, 6), ('cooked_porkchop', 5, 3, 6), ('bread', 6, 3, 6),
                                ('apple', 5, 2, 5), ('baked_potato', 4, 3, 6), ('iron_ingot', 5, 2, 5),
                                ('leather', 3, 2, 5), ('arrow', 3, 6, 16), ('ender_pearl', 3, 1, 2),
                                ('diamond', 1)]))
# Korin's tower top: the Senzu, and the climb paid back.
T['karin'] = table(thing('M:senzu', 1.0, 1, 3), thing('golden_apple', 0.35),
                   pool((2, 4), [('gold_ingot', 5, 2, 6), ('diamond', 3, 1, 2), ('ender_pearl', 5, 2, 4),
                                 ('experience_bottle', 4, 3, 6), ('honey_bottle', 3, 1, 3), ('emerald', 3, 2, 4)]))
# Cell's ring: a prize for the fighters.
T['cell'] = table(thing('M:senzu', 0.5, 1, 2), thing('M:cristal_multiversal', 0.1),
                  pool((3, 5), [('diamond', 4, 1, 3), ('gold_ingot', 5, 2, 6), ('iron_ingot', 6, 3, 8),
                                ('experience_bottle', 5, 3, 8), ('ender_pearl', 5, 1, 3), ('golden_apple', 1),
                                ('gunpowder', 6, 2, 6)]))
# The crashed Capsule Corp ship in the desert: scrap, wiring, gunpowder.
T['nave'] = table(thing('M:scouter', 0.15), thing('M:radar_dragao', 0.08), thing('M:senzu', 0.3),
                  pool((3, 5), [('iron_ingot', 9, 3, 8), ('copper_ingot', 7, 4, 10), ('redstone', 7, 4, 10),
                                ('gunpowder', 9, 3, 8), ('gold_ingot', 3, 1, 4), ('ender_pearl', 4, 1, 3),
                                ('diamond', 2, 1, 2), ('chain', 3, 2, 6)]))
# Babidi's ship: dark magic.
T['babidi'] = table(thing('M:senzu', 0.35, 1, 2), thing('M:cristal_multiversal', 0.2),
                    pool((3, 5), [('ender_pearl', 8, 2, 5), ('obsidian', 4, 2, 5), ('amethyst_shard', 5, 3, 8),
                                  ('gold_ingot', 5, 2, 6), ('diamond', 3, 1, 3), ('experience_bottle', 5, 3, 7),
                                  ('gunpowder', 6, 2, 6), ('crying_obsidian', 2, 1, 3)]))
# Buu's house: sweets (and something good, he likes you).
T['buu'] = table(thing('golden_apple', 0.3), thing('M:senzu', 0.25),
                 pool((3, 6), [('cake', 3), ('cookie', 8, 4, 12), ('pumpkin_pie', 6, 2, 5), ('sugar', 5, 4, 10),
                               ('honey_bottle', 4, 1, 3), ('sweet_berries', 4, 4, 10), ('gold_ingot', 3, 2, 5),
                               ('ender_pearl', 3, 1, 2), ('diamond', 1)]))
# Kame House: the sea and the old master's things.
T['kame'] = table(thing('M:senzu', 0.3), thing('turtle_scute', 0.4, 1, 3), thing('heart_of_the_sea', 0.05),
                  pool((3, 5), [('cooked_cod', 6, 3, 6), ('cooked_salmon', 6, 3, 6), ('prismarine_shard', 4, 3, 8),
                                ('nautilus_shell', 2), ('iron_ingot', 4, 2, 5), ('ender_pearl', 3, 1, 2),
                                ('diamond', 1), ('gold_ingot', 3, 1, 3)]))
# Kami's lookout: the gods' palace.
T['lookout'] = table(thing('M:senzu', 0.6, 1, 3), thing('golden_apple', 0.5, 1, 2), thing('enchanted_golden_apple', 0.03),
                     pool((3, 5), [('diamond', 4, 2, 4), ('gold_ingot', 5, 3, 8), ('ender_pearl', 6, 2, 5),
                                   ('experience_bottle', 6, 4, 10), ('emerald', 3, 2, 5)]))
# The Room of Spirit and Time: supplies for a year inside.
T['sala_tempo'] = table(thing('M:senzu', 0.5, 1, 2),
                        pool((4, 6), [('bread', 8, 4, 10), ('cooked_beef', 7, 4, 8), ('golden_carrot', 4, 3, 8),
                                      ('experience_bottle', 5, 4, 10), ('iron_ingot', 3, 2, 6), ('diamond', 1)]))
# Namek: Freeza's ship (the soldiers' scouters), the crashed capsule, the village, Guru's house.
T['nave_freeza'] = table(thing('M:scouter', 0.4), thing('M:senzu', 0.2),
                         pool((3, 5), [('iron_ingot', 8, 3, 8), ('diamond', 3, 1, 3), ('gold_ingot', 4, 2, 5),
                                       ('ender_pearl', 6, 2, 4), ('redstone', 5, 4, 10), ('experience_bottle', 3, 2, 6)]))
T['nave_capsula'] = table(thing('M:radar_dragao', 0.1), thing('M:senzu', 0.35, 1, 2), useful())
T['vila'] = table(thing('M:senzu', 0.15),
                  pool((3, 5), [('glow_berries', 6, 3, 8), ('melon_slice', 6, 4, 10), ('emerald', 5, 2, 5),
                                ('lapis_lazuli', 4, 3, 8), ('ender_pearl', 3, 1, 2), ('bread', 4, 2, 5),
                                ('diamond', 1)]))
T['guru'] = table(thing('golden_apple', 0.35), thing('M:cristal_multiversal', 0.15),
                  pool((3, 5), [('diamond', 4, 1, 3), ('lapis_lazuli', 5, 4, 10), ('experience_bottle', 6, 4, 8),
                                ('emerald', 4, 2, 5), ('ender_pearl', 4, 1, 3), ('gold_ingot', 4, 2, 5)]))

# ---------------------------------------------------------------- Naruto
# Training Ground 3: the stumps (the lunch Kakashi left).
T['folha'] = table(thing('M:ramen', 0.7, 1, 2), thing('M:pilula_soldado', 0.4, 1, 2), thing('M:bandana_ninja', 0.12),
                   pool((2, 4), [('iron_ingot', 6, 2, 5), ('arrow', 5, 6, 16), ('bread', 5, 2, 5),
                                 ('ender_pearl', 4, 1, 2), ('emerald', 2, 1, 3), ('diamond', 1)]))
# The Chunin exam tower (its sealed chests also get the pills, kunai and headband of the exam).
T['morte'] = table(pool((2, 4), [('diamond', 3, 1, 2), ('gold_ingot', 5, 2, 5), ('ender_pearl', 6, 1, 3),
                                 ('iron_ingot', 6, 2, 6), ('experience_bottle', 4, 2, 5)]))
# The Snake's Den: what the giant snake swallowed.
T['covil'] = table(thing('M:celula_hashirama', 0.08),
                   pool((3, 5), [('bone', 8, 3, 8), ('ender_pearl', 6, 1, 3), ('gold_ingot', 5, 2, 5),
                                 ('diamond', 3, 1, 2), ('iron_ingot', 5, 2, 6), ('spider_eye', 4, 1, 3),
                                 ('emerald', 3, 1, 3)]))
# The puppeteer's workshop: gunpowder, string, wood and metal.
T['deserto'] = table(pool((3, 5), [('gunpowder', 10, 3, 9), ('string', 7, 3, 8), ('iron_ingot', 6, 2, 6),
                                   ('redstone', 5, 3, 8), ('gold_ingot', 3, 1, 4), ('ender_pearl', 4, 1, 3),
                                   ('diamond', 2, 1, 2), ('tnt', 1)]))
# The unfinished bridge: the builders' supplies (the barrels).
T['nevoa'] = table(thing('M:pilula_soldado', 0.3),
                   pool((3, 5), [('iron_ingot', 8, 3, 8), ('cooked_cod', 6, 3, 6), ('cooked_salmon', 5, 2, 5),
                                 ('chain', 4, 2, 6), ('gunpowder', 4, 2, 5), ('ender_pearl', 4, 1, 3),
                                 ('diamond', 1), ('prismarine_shard', 3, 2, 6)]))
# The Angel's tower in the Rain village: paper bombs and metal.
T['chuva'] = table(thing('M:pilula_soldado', 0.3, 1, 2),
                   pool((3, 5), [('paper', 7, 4, 12), ('gunpowder', 8, 3, 8), ('iron_ingot', 7, 3, 8),
                                 ('copper_ingot', 5, 3, 8), ('redstone', 4, 3, 8), ('ender_pearl', 5, 1, 3),
                                 ('diamond', 2, 1, 2)]))
# The Sages' spring at Myoboku: the toads' pantry.
T['myoboku'] = table(thing('M:ramen', 0.4), thing('golden_apple', 0.15),
                     pool((3, 5), [('slime_ball', 6, 2, 6), ('lily_pad', 4, 2, 5), ('ochre_froglight', 3, 1, 3),
                                   ('emerald', 5, 2, 5), ('gold_ingot', 4, 2, 4), ('ender_pearl', 4, 1, 3),
                                   ('diamond', 1)]))
# The toad's peak: the prize of the climb.
T['pico'] = table(thing('golden_apple', 0.4), thing('M:pilula_soldado', 0.4, 1, 2),
                  pool((3, 5), [('diamond', 5, 1, 3), ('ender_pearl', 6, 2, 4), ('experience_bottle', 5, 3, 8),
                                ('emerald', 4, 2, 5), ('gold_ingot', 4, 2, 5)]))

# ---------------------------------------------------------------- the others in a structure
# A second chest in the same building: a little of everything.
T['comum'] = table(useful((2, 4)))
T['comum_deserto'] = table(useful((2, 4), desert=True))
# Barrels: supplies, a few things each (an empty one looked broken in the PC test of 2026-11-12).
T['suprimentos'] = table(pool((1, 3), [('bread', 6, 1, 4), ('apple', 5, 1, 4), ('carrot', 4, 2, 5), ('potato', 4, 2, 5),
                                       ('coal', 5, 2, 6), ('torch', 4, 3, 8), ('arrow', 3, 3, 10), ('string', 2, 1, 4),
                                       ('wheat', 3, 2, 6), ('ender_pearl', 1), ('iron_nugget', 3, 3, 9)]))
T['suprimentos_deserto'] = table(pool((1, 3), [('bread', 5, 1, 4), ('gunpowder', 6, 1, 4), ('coal', 5, 2, 6),
                                               ('torch', 4, 3, 8), ('string', 3, 1, 4), ('iron_nugget', 4, 3, 9),
                                               ('ender_pearl', 1)]))

# Every structure's own chest has at least one item of its universe (PC test 2026-11-12: some opened with only
# Minecraft things). One draw from these, on top of the rarer chances above.
THEME = {
    'capsula': [('M:senzu', 6), ('M:scouter', 2), ('M:radar_dragao', 2), ('M:cristal_multiversal', 2)],
    'goku': [('M:senzu', 6), ('M:esfera_dragao_4', 1)],
    'cell': [('M:senzu', 6), ('M:cristal_multiversal', 1)],
    'nave': [('M:senzu', 5), ('M:scouter', 3), ('M:radar_dragao', 1)],
    'babidi': [('M:senzu', 5), ('M:cristal_multiversal', 2)],
    'buu': [('M:senzu', 1)],
    'kame': [('M:senzu', 1)],
    'lookout': [('M:senzu', 1)],
    'sala_tempo': [('M:senzu', 1)],
    'nave_freeza': [('M:scouter', 3), ('M:senzu', 3)],
    'nave_capsula': [('M:senzu', 5), ('M:radar_dragao', 1)],
    'vila': [('M:senzu', 4), ('M:esfera_dragao_1', 1)],
    'guru': [('M:senzu', 4), ('M:cristal_multiversal', 2)],
    'folha': [('M:ramen', 5), ('M:pilula_soldado', 4), ('M:bandana_ninja', 1)],
    'morte': [('M:pilula_soldado', 5), ('M:ramen', 3), ('M:kunai_hiraishin', 1)],
    'covil': [('M:pilula_soldado', 4), ('M:bolsa_veneno', 3), ('M:celula_hashirama', 1)],
    'deserto': [('M:bolsa_veneno', 4), ('M:pilula_soldado', 4), ('M:bandana_ninja', 1)],
    'nevoa': [('M:pilula_soldado', 4), ('M:ramen', 4), ('M:bandana_ninja', 1)],
    'chuva': [('M:pilula_soldado', 4), ('M:ramen', 3), ('M:bandana_ninja', 1)],
    'myoboku': [('M:ramen', 5), ('M:pilula_soldado', 3)],
    'pico': [('M:pilula_soldado', 4), ('M:ramen', 3), ('M:kunai_hiraishin', 1)],
}
for name, items in THEME.items():
    T[name]['pools'].insert(0, pool(1, items))

os.makedirs(OUT, exist_ok=True)
for name, t in T.items():
    with open(os.path.join(OUT, name + '.json'), 'w') as f:
        json.dump(t, f, indent=2)
        f.write('\n')
print(len(T), 'tables in', os.path.normpath(OUT))
