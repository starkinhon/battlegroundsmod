# Namek (Stark, 2026-11-10): its own small planet, 400x400, a sub-dimension of the Dragon Ball universe.
# Green sea with many islands; the land in low terraces with short cliffs into the water; flat-topped mesas with sheer
# pink-beige walls ("picos bem quadrados"), some tall square pillars; tan bare patches on the blue grass.
# Output: data/battlegroundsmod/worldmaps/namek.bin (same format as build_worldmap.py) + a relief preview.
import numpy as np, gzip, struct, sys
from scipy import ndimage as nd
from PIL import Image
W = H = 400; O = -200; SEA = 63
rng = np.random.default_rng(1977)
N = {'deep': 0, 'shallow': 1, 'ngrass': 13, 'nmesa': 14, 'nsand': 15, 'nsea': 16}
yy, xx = np.mgrid[0:H, 0:W].astype(float)

def fbm(scale, octaves=4, seed=0):
    r = np.random.default_rng(seed); out = np.zeros((H, W)); amp = 1; tot = 0
    for o in range(octaves):
        s = max(2, int(W / scale * 2 ** o))
        g = r.random((s + 3, s + 3))
        out += amp * nd.zoom(g, (H + 3 * W / s) / (s + 3), order=3)[:H, :W]; tot += amp; amp *= 0.5
    out /= tot
    return (out - out.min()) / (out.max() - out.min())

# land: noise islands, fading to sea at the edge of the planet
edge = np.clip(np.minimum.reduce([xx, yy, W - 1 - xx, H - 1 - yy]) / 50.0, 0, 1)
land_n = fbm(140, 5, 1) * (0.35 + 0.65 * edge)
land = land_n > 0.47
lakes = fbm(70, 3, 9)
land &= lakes > 0.4                                # lakes and channels inside the islands (refs: water everywhere)
land = nd.binary_opening(land, iterations=2)
# terraces: 1-4 steps of 3-5 blocks over the sea, sheer edges
ter = fbm(90, 4, 2)
steps = np.floor(np.clip((land_n - 0.47) / 0.25, 0, 0.999) * 3 + ter * 1.6).astype(int)
h = np.where(land, SEA + 1 + steps * 4 + np.round(fbm(30, 2, 3) * 1.4), 0)
sea_depth = np.clip(nd.distance_transform_edt(~land) / 5.0, 1, 9)
h = np.where(land, h, SEA - 1 - sea_depth).astype(int)
kind = np.where(land, N['ngrass'], N['nsea'])
sandy = (fbm(25, 3, 4) > 0.72) & land
kind[sandy] = N['nsand']

# mesas: squarish super-ellipses with noisy walls, flat tops (some on the sea, rising out of the water)
WOB = [fbm(45, 3, 100 + i) for i in range(6)]
def blob_d(cx, cz, rx, rz, p, rough):
    d = (np.abs(xx - cx) / rx) ** p + (np.abs(yy - cz) / rz) ** p
    return d + (WOB[int(cx + cz) % 6] - 0.5) * rough
def blob(cx, cz, rx, rz, p, rough):
    return blob_d(cx, cz, rx, rz, p, rough) <= 1

sites = {'arrival': (200, 230), 'freeza': (120, 140), 'capsula': (265, 285), 'vila': (285, 150), 'guru': (95, 290)}
def near_site(cx, cz, r):
    return any((cx - x) ** 2 + (cz - z) ** 2 < (r + 40) ** 2 for x, z in sites.values())
mesas = []
tries = 0
while len(mesas) < 16 and tries < 500:
    tries += 1
    cx, cz = rng.uniform(40, 360, 2); rx, rz = rng.uniform(9, 26, 2)
    if near_site(cx, cz, max(rx, rz)) or any((cx - a) ** 2 + (cz - b) ** 2 < (max(rx, rz) + r + 10) ** 2 for a, b, r in mesas):
        continue
    mesas.append((cx, cz, max(rx, rz)))
    p = rng.uniform(2.2, 5)
    dd = blob_d(cx, cz, rx, rz, p, 0.9)
    m = dd <= 1
    top = int(rng.uniform(84, 122))
    hill = rng.random() < 0.45                    # the rounded beige hills of Stark's Minecraft refs
    if hill:
        hh = (top - (np.clip(dd, 0, 1) ** 1.6) * (top - SEA - 8)).astype(int)
        h[m] = np.maximum(h[m], hh[m]); kind[m] = N['nmesa']
        continue
    # a lower shelf around some of them (the refs' stepped cliffs)
    if rng.random() < 0.6:
        shelf = blob(cx, cz, rx + 5, rz + 5, 3, 0.4) & ~m
        h[shelf] = np.maximum(h[shelf], top - int(rng.uniform(12, 22))); kind[shelf] = N['nmesa']
    h[m] = top; kind[m] = N['nmesa']
pillars = 0; tries = 0
while pillars < 10 and tries < 400:
    tries += 1
    cx, cz = rng.uniform(30, 370, 2); r = rng.uniform(3.5, 7)
    if near_site(cx, cz, r) or any((cx - a) ** 2 + (cz - b) ** 2 < (rr + r + 6) ** 2 for a, b, rr in mesas):
        continue
    mesas.append((cx, cz, r)); pillars += 1
    m = blob(cx, cz, r, r * rng.uniform(0.8, 1.2), 4, 0.25)
    h[m] = int(rng.uniform(105, 160)); kind[m] = N['nmesa']

# building sites: flat grass discs
for name, (sx, sz) in sites.items():
    r = {'freeza': 34, 'capsula': 16, 'vila': 30, 'guru': 18, 'arrival': 16}[name]
    d = np.hypot(xx - sx, yy - sz)
    m = d <= r
    lvl = SEA + 3
    if name == 'guru':
        lvl = 112                                  # Guru's house stands on top of a round mesa
        h[d <= r] = lvl; kind[d <= r] = N['nmesa']
        continue
    h[m] = lvl; kind[m] = N['ngrass']
    ring = (d > r) & (d <= r + 8) & (h < lvl)
    h[ring] = np.maximum(h[ring], lvl - ((d[ring] - r) // 2.5).astype(int)); kind[ring & (h >= SEA)] = N['ngrass']
    # higher ground around is cut down in a gentle slope, so a site is a clearing, never the bottom of a well
    # (Stark's test 2026-11-10: the arrival pad sat in a deep round pit)
    up = (d > r) & (d <= r + 40) & (h > lvl)
    h[up] = np.minimum(h[up], lvl + ((d[up] - r) // 3).astype(int))
h = np.clip(h, 30, 250).astype(int)
kind[(h < SEA) & (kind != N['nsea'])] = N['nsea']
ax, az = sites['arrival']
out = sys.argv[1] if len(sys.argv) > 1 else 'src/main/resources/data/battlegroundsmod/worldmaps/namek.bin'
with gzip.open(out, 'wb') as f:
    f.write(b'SMAP'); f.write(struct.pack('>iiiii', 1, W, H, O, O)); f.write(struct.pack('>ii', ax + O, az + O))
    f.write(h.astype('>i2').tobytes()); f.write(kind.astype(np.uint8).tobytes())
pal = {0: (40, 150, 100), 1: (60, 180, 120), 13: (80, 182, 214), 14: (226, 196, 180), 15: (212, 186, 140), 16: (63, 191, 122)}
img = np.zeros((H, W, 3))
for k, c in pal.items():
    img[kind == k] = c
gy, gx = np.gradient(h.astype(float)); shade = np.clip(1 + (-gx - gy) * 0.05, 0.5, 1.4)[..., None]
Image.fromarray(np.clip(img * shade, 0, 255).astype(np.uint8)).resize((800, 800), Image.NEAREST).save(out.replace('.bin', '_relief.png'))
for n, (x, z) in sites.items():
    print(n, x + O, int(h[z, x]), z + O)
print('hmax', h.max(), 'land', (h >= SEA).mean())
