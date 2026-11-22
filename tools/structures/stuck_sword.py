"""The Kubikiribocho stuck point down in the swordsman's grave (Stark 2026-11-22): a block model made from the item's own
3D model (item/kubikiribocho_3d.json), turned half a turn about z (blade down), scaled and set so the tip goes 3 px into
the block under it. Writes models/block/kubikiribocho_fincada.json (the lower half draws the whole sword) and
kubikiribocho_fincada_topo.json (the upper half, nothing drawn: it only catches the click on the hilt).

python3 tools/structures/stuck_sword.py
"""
import json, os

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'battlegroundsmod', 'models')
SCALE = 1.2
TIP = -3.0          # the tip's height in the lower block (px); below 0 it is inside the ground

src = json.load(open(os.path.join(ROOT, 'item', 'kubikiribocho_3d.json')))
els = src['elements']
xs = [c[0] for e in els for c in (e['from'], e['to'])]
ys = [c[1] for e in els for c in (e['from'], e['to'])]
zs = [c[2] for e in els for c in (e['from'], e['to'])]
cx, cz, ytop = (min(xs) + max(xs)) / 2, (min(zs) + max(zs)) / 2, max(ys)

def P(x, y, z):
    # half a turn about z: x -> -x, y -> -y (z stays), then scaled around the block's middle
    return (8 - (x - cx) * SCALE, TIP + (ytop - y) * SCALE, 8 + (z - cz) * SCALE)

SWAP = {'east': 'west', 'west': 'east', 'up': 'down', 'down': 'up', 'north': 'north', 'south': 'south'}
out = []
for e in els:
    a, b = P(*e['from']), P(*e['to'])
    fr = [round(min(a[i], b[i]), 4) for i in range(3)]
    to = [round(max(a[i], b[i]), 4) for i in range(3)]
    faces = {}
    for name, f in e['faces'].items():
        g = {k: v for k, v in f.items() if k != 'cullface'}
        # the half turn shows every face upside down
        g['rotation'] = (f.get('rotation', 0) + 180) % 360
        faces[SWAP[name]] = g
    out.append({'from': fr, 'to': to, 'faces': faces})
lo = min(c[1] for e in out for c in (e['from'], e['to'])); hi = max(c[1] for e in out for c in (e['from'], e['to']))
assert lo >= -16 and hi <= 32, (lo, hi)
model = {'textures': src['textures'], 'elements': out}
json.dump(model, open(os.path.join(ROOT, 'block', 'kubikiribocho_fincada.json'), 'w'), separators=(',', ':'))
json.dump({'textures': {'particle': src['textures']['particle']}},
          open(os.path.join(ROOT, 'block', 'kubikiribocho_fincada_topo.json'), 'w'), indent=2)
print('elements', len(out), 'y', round(lo, 2), round(hi, 2),
      'x', round(min(c[0] for e in out for c in (e['from'], e['to'])), 2), round(max(c[0] for e in out for c in (e['from'], e['to'])), 2))
