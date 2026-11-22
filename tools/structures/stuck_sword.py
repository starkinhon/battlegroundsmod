"""The Kubikiribocho stuck point down in the swordsman's grave (Stark 2026-11-22): the item's own 3D model, the Blender
mesh Stark made in #529 (models/mesh/kubikiribocho.bin, texture textures/models/kubikiribocho.png), turned half a turn
about z (blade down), scaled and set so the tip goes 3 px into the block under it. The old version read the cube model
item/kubikiribocho_3d.json, which #529 deleted with its texture: the sword in the grave went black and magenta.

The mesh is triangles, which a block's JSON model can't hold, so it goes out as an OBJ (NeoForge's obj loader):
  models/block/kubikiribocho_fincada.obj + .mtl  the whole sword, drawn by the lower half
  models/block/kubikiribocho_fincada.json        the loader, pointing at the texture copy in textures/block
  models/block/kubikiribocho_fincada_topo.json   the upper half: nothing drawn, it only catches the click on the hilt
  textures/block/kubikiribocho_fincada.png       copy of textures/models/kubikiribocho.png (a block model's texture
                                                 has to be in the block atlas; textures/models is not)
Every triangle goes out twice, front and back, because the item draws the mesh with no face culling and the blocks do
cull. Run again if Stark changes the sword's mesh or texture:

python3 tools/structures/stuck_sword.py
"""
import json, os, shutil, struct

ASSETS = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'battlegroundsmod')
MODELS = os.path.join(ASSETS, 'models')
SCALE = 1.2         # same size in the grave as the sword it replaces (34.9 px tall, the hilt ends at the top half's top)
TIP = -3.0 / 16     # the tip's height in the lower block (blocks); below 0 it is inside the ground

raw = open(os.path.join(MODELS, 'mesh', 'kubikiribocho.bin'), 'rb').read()
n = struct.unpack('>i', raw[:4])[0]
f = struct.unpack('>%df' % (n * 8), raw[4:4 + n * 32])
verts = [f[i * 8:i * 8 + 8] for i in range(n)]   # x y z u v nx ny nz, block units, +y the tip
assert n % 3 == 0

xs = [v[0] for v in verts]; zs = [v[2] for v in verts]
cx, cz, ytop = (min(xs) + max(xs)) / 2, (min(zs) + max(zs)) / 2, max(v[1] for v in verts)

def P(x, y, z):
    # half a turn about z (x -> -x, y -> -y, z stays: a turn, not a mirror), scaled around the block's middle
    return (0.5 - (x - cx) * SCALE, TIP + (ytop - y) * SCALE, 0.5 + (z - cz) * SCALE)

lines = ['# made by tools/structures/stuck_sword.py from models/mesh/kubikiribocho.bin', 'mtllib kubikiribocho_fincada.mtl',
         'o espada', 'usemtl espada']
for v in verts:
    x, y, z = P(v[0], v[1], v[2])
    lines.append('v %.5f %.5f %.5f' % (x, y, z))
for v in verts:
    lines.append('vt %.6f %.6f' % (v[3], v[4]))      # v top-down like the item renderer: flip_v false
for v in verts:
    lines.append('vn %.5f %.5f %.5f' % (-v[5], -v[6], v[7]))
for v in verts:
    lines.append('vn %.5f %.5f %.5f' % (v[5], v[6], -v[7]))   # the back faces
for t in range(0, n, 3):
    a, b, c = t + 1, t + 2, t + 3
    lines.append('f %d/%d/%d %d/%d/%d %d/%d/%d' % (a, a, a, b, b, b, c, c, c))
    a2, b2, c2 = a + n, b + n, c + n
    lines.append('f %d/%d/%d %d/%d/%d %d/%d/%d' % (a, a, a2, c, c, c2, b, b, b2))
open(os.path.join(MODELS, 'block', 'kubikiribocho_fincada.obj'), 'w', newline='\n').write('\n'.join(lines) + '\n')
open(os.path.join(MODELS, 'block', 'kubikiribocho_fincada.mtl'), 'w', newline='\n').write(
    'newmtl espada\nKa 0 0 0\nKd 1 1 1\nd 1\nmap_Kd #texture\n')

ys = [P(v[0], v[1], v[2])[1] for v in verts]
assert -0.25 <= min(ys) and max(ys) <= 2.0 + 1e-4, (min(ys), max(ys))

TEX = 'battlegroundsmod:block/kubikiribocho_fincada'
shutil.copyfile(os.path.join(ASSETS, 'textures', 'models', 'kubikiribocho.png'),
                os.path.join(ASSETS, 'textures', 'block', 'kubikiribocho_fincada.png'))
model = {
    'loader': 'neoforge:obj',
    'model': 'battlegroundsmod:models/block/kubikiribocho_fincada.obj',
    'flip_v': False,
    'automatic_culling': False,
    'emissive_ambient': False,
    'ambientocclusion': False,
    'render_type': 'minecraft:cutout',
    'textures': {'texture': TEX, 'particle': TEX},
}
json.dump(model, open(os.path.join(MODELS, 'block', 'kubikiribocho_fincada.json'), 'w', newline='\n'), indent=2)
json.dump({'textures': {'particle': TEX}}, open(os.path.join(MODELS, 'block', 'kubikiribocho_fincada_topo.json'), 'w', newline='\n'), indent=2)
print('%d triangles (x2), y %.3f..%.3f blocks' % (n // 3, min(ys), max(ys)))
