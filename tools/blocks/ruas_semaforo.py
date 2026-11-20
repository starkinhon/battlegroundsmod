#!/usr/bin/env python3
"""Modelos do semaforo japones, da cabeca da luminaria de rua (Shibuya) e da amarra da corda da ponte (Mar da Nevoa).

Stark 2026-11-20: os semaforos eram cubos soltos (caixa preta + cubo vermelho) e a luminaria um cubo de arenito na ponta
do braco. Aqui: semaforo horizontal de Toquio (3 lentes com viseira, vermelho a direita de quem olha), pendurado no braco
por uma haste; a lente acesa e desenhada pelo cliente (client/TrafficLightRenderer). Luminaria: cabeca de metal com lente
quente embaixo, presa no braco/poste por uma luva. Amarra: o topo do poste da ponte com a corda enrolada.

Modelos virados pro norte; o blockstate gira. Rodar da raiz do repo: python3 tools/blocks/ruas_semaforo.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources', 'assets',
                    'battlegroundsmod')
M = 'battlegroundsmod:block/'
FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def el(a, b, tex, faces=FACES, over=None, extra=None, uv=(0, 0, 16, 16)):
    # the uv is always given: from the coordinates (the game's default) it would cut the round lens and go off the
    # texture where a piece passes the block's edge
    f = {d: {'texture': over.get(d, tex) if over else tex, 'uv': list(uv)} for d in faces}
    e = {'from': list(a), 'to': list(b), 'faces': f}
    if extra:
        e.update(extra)
    return e


def write(name, textures, elements, parent='minecraft:block/block'):
    m = {'parent': parent, 'render_type': 'minecraft:cutout', 'textures': textures, 'elements': elements}
    with open(os.path.join(ROOT, 'models', 'block', name + '.json'), 'w') as fp:
        json.dump(m, fp, indent=1)


def facing_states(name, model):
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    v = {f'facing={f}': {'model': M + model, 'y': r} if r else {'model': M + model} for f, r in rot.items()}
    with open(os.path.join(ROOT, 'blockstates', name + '.json'), 'w') as fp:
        json.dump({'variants': v}, fp, indent=1)


# ---- the traffic light: lamps face north (-z), the long side along x
LENS = [(1.3, 'vermelha'), (8.0, 'amarela'), (14.7, 'verde')]
els = [
    el((7.5, 12, 8), (8.5, 28, 9), '#corpo'),  # the rod up to the arm above (its bar is at y 27..29)
    el((6.5, 26.5, 6.5), (9.5, 29.5, 10.5), '#corpo'),  # the clamp round the arm
    el((6, 11, 7), (10, 12, 10), '#corpo'),  # the mount on the housing
    el((-2, 3, 6), (18, 11, 11), '#corpo'),  # the housing
]
for cx, cor in LENS:
    els.append(el((cx - 2.5, 4.5, 5.6), (cx + 2.5, 9.5, 6), '#corpo', over={'north': '#' + cor}))
    els.append(el((cx - 2.9, 9.5, 2.6), (cx + 2.9, 10.1, 6), '#corpo'))  # the visor
    els.append(el((cx - 2.9, 6.6, 3.6), (cx - 2.5, 9.5, 6), '#corpo'))
    els.append(el((cx + 2.5, 6.6, 3.6), (cx + 2.9, 9.5, 6), '#corpo'))
write('caixa_de_semaforo', {'corpo': M + 'semaforo_corpo', 'particle': M + 'semaforo_corpo',
                            'vermelha': M + 'lente_vermelha_de_semaforo', 'amarela': M + 'lente_amarela_de_semaforo',
                            'verde': M + 'lente_verde_de_semaforo'}, els)
facing_states('caixa_de_semaforo', 'caixa_de_semaforo')

# ---- the street lamp's head: the pole/arm at north (-z), the head reaching south
lit = {'neoforge_data': {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}, 'shade': False}
write('luminaria_de_rua', {'corpo': M + 'luminaria_corpo', 'lente': M + 'luminaria_de_rua', 'particle': M + 'luminaria_corpo'}, [
    el((6.5, 10.5, -6.5), (9.5, 13.5, 3), '#corpo'),  # the sleeve over the arm (or into the pole)
    el((4, 10, 2), (12, 13, 14), '#corpo'),  # the head
    el((5, 13, 3), (11, 14, 13), '#corpo'),  # its rounded back
    el((4.6, 9.6, 3), (11.4, 10, 13.4), '#corpo', over={'down': '#lente'}, extra=lit),  # the warm lens under it
])
facing_states('luminaria_de_rua', 'luminaria_de_rua')

# ---- the bridge post's top with the rope wound round it
write('amarra_de_corda', {'lado': 'minecraft:block/stripped_dark_oak_log', 'topo': 'minecraft:block/stripped_dark_oak_log_top',
                          'corda': M + 'corda_enrolada', 'particle': 'minecraft:block/stripped_dark_oak_log'}, [
    {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {
        d: {'texture': '#topo' if d in ('up', 'down') else '#lado', 'cullface': d} for d in FACES}},
    el((-0.6, 9, -0.6), (16.6, 13, 16.6), '#corda', uv=(0, 0, 16, 4)),
    el((-0.4, 5.5, -0.4), (16.4, 7, 16.4), '#corda', uv=(0, 6, 16, 7.5)),
])
with open(os.path.join(ROOT, 'blockstates', 'amarra_de_corda.json'), 'w') as fp:
    json.dump({'variants': {'': {'model': M + 'amarra_de_corda'}}}, fp, indent=1)
for n in ('amarra_de_corda',):
    with open(os.path.join(ROOT, 'models', 'item', n + '.json'), 'w') as fp:
        json.dump({'parent': M + n}, fp)
