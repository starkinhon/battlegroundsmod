"""Builds the challenge arenas: one structure file per arena for the mod and one mesh per arena for the web preview.

  MC_ASSETS=<extracted client jar> python3 tools/arenas/build.py <out dir> [arena ids...] [--mod src/main/resources]

Writes <out>/arenas/<id>.wasm (preview mesh), <out>/atlas.png/json, <out>/arenas.json (names, sky, fight radius,
spawn points) and, with --mod <resources dir>, what the mod loads (modexport.py): the blocks of each arena
(data/battlegroundsmod/souls_arenas/<id>.bin), their meta (souls_arenas/arenas.json) and one biome per arena for its
sky, fog and grass colours (worldgen/biome/arena_<id>.json). The game places exactly these blocks: JOGO = PRÉVIA.
"""
import json, os, sys, time
sys.path.insert(0, os.path.dirname(__file__))
import mesher
from arenas_all import ARENAS

# Stark 2026-11-13: the fight circles were a cage with a lion. The heavy hitters (Saitama's punches, Shigaraki's decay,
# Madara's meteors, Gojo's Purple, Sukuna's arrows...) wreck a 40 block circle in seconds and leave nowhere to run, so
# their circle is twice as wide and everyone else's half again, as far as the scenery around still has ground.
BIG = ('shigaraki', 'bills', 'saitama', 'madara', 'joy_boy', 'kokushibo', 'gojo', 'sukuna')

def grow(aid, v, m):
    import math
    want = m['R'] * (2.0 if aid in BIG else 1.5)
    ground = {}
    for (x, y, z), b in v.b.items():
        if b and not b.endswith(':air') and not b.endswith(':water') and not b.endswith(':lava'):
            ground[(x, z)] = True
    # the ground reaches out to `ext` in every direction (the closest edge of the land around the circle)
    ext = None
    for k in range(72):
        a = k / 72 * 2 * math.pi
        r = m['R']
        while ground.get((int(round(math.cos(a) * (r + 1))), int(round(math.sin(a) * (r + 1))))) and r < 400:
            r += 1
        ext = r if ext is None else min(ext, r)
    m['R'] = int(max(m['R'], min(want, ext - 10)))

def main():
    args = sys.argv[1:]
    mod_dir = None
    if '--mod' in args:
        i = args.index('--mod'); mod_dir = args[i + 1]; del args[i:i + 2]
    out = args[0]
    want = args[1:] or list(ARENAS)
    os.makedirs(os.path.join(out, 'arenas'), exist_ok=True)
    atlas = mesher.Atlas(os.path.join(out, 'atlas.json'))
    meshes = {}
    meta_path = os.path.join(out, 'arenas.json')
    meta = json.load(open(meta_path)) if os.path.exists(meta_path) else {}
    for aid in want:
        t = time.time()
        v, m = ARENAS[aid]()
        grow(aid, v, m)
        from lib import support_gravity, safe_spawn, underlay, illuminate, dark_interiors
        fixed = support_gravity(v)
        from lib import ground_floaters
        ground_floaters(v)
        underlay(v, m['R'])
        dark = dark_interiors(v, m['R'])
        from lib import settle
        loose_rm, loose_fill, loose = settle(v, m.get('flutua') or ())    # nothing hangs in the air
        if loose:
            print('  ATENÇÃO %s: %d grupos ainda soltos (python3 tools/arenas/floaters.py %s)' % (aid, len(loose), aid))
        lights = illuminate(v, m['R'], m['altura'], open_sky=m.get('skyLight', 15) == 0)
        for k in ('jogador', 'desafiante'):
            m[k] = safe_spawn(v, m[k])
        from furni import finish
        finish(v)                         # panes/fences connect the way the game will show them
        o, tr = mesher.build(v, atlas, m.get('tints'), m.get('skyLight', 15))
        meshes[aid] = (o, tr)
        m['blocks'] = sum(1 for b in v.b.values() if b != 'minecraft:air')
        m['quads'] = (len(o.idx) + len(tr.idx)) // 6
        meta[aid] = m
        if mod_dir:
            import modexport
            modexport.export(mod_dir, aid, v, m)
        print(aid, lights, 'luzes', dark, 'pretos', loose_rm, 'soltos removidos', loose_fill, 'apoios', m['blocks'], 'blocks', m['quads'], 'quads', '%.1fs' % (time.time() - t), flush=True)
    # one atlas for everything built in this run (re-run all arenas together for the final page)
    atlas.save(os.path.join(out, 'atlas.png'), os.path.join(out, 'atlas.json'))
    aj = json.load(open(os.path.join(out, 'atlas.json')))
    for aid, (o, tr) in meshes.items():
        mesher.write(os.path.join(out, 'arenas', aid + '.wasm'), o, tr, aj['w'], aj['h'])
    json.dump(meta, open(meta_path, 'w'), ensure_ascii=False, indent=1)

if __name__ == '__main__':
    main()
