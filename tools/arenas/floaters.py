"""Lists what still hangs in the air after build.py's passes (lib.settle removes loose bits and fills small gaps).

  python3 tools/arenas/floaters.py [arena ids...]

Stark 2026-11-13: fences, blocks and buildings floating over nothing. Every group printed here needs a fix in its
builder, or, when it is meant to float (clouds, the Infinity Castle), a box in the arena's meta['flutua'].
"""
import os, sys
from collections import Counter
sys.path.insert(0, os.path.dirname(__file__))
from arenas_all import ARENAS
import lib

def prepare(aid):
    v, m = ARENAS[aid]()
    import build
    build.grow(aid, v, m)
    lib.support_gravity(v); lib.ground_floaters(v); lib.underlay(v, m['R']); lib.dark_interiors(v, m['R'])
    removed, filled, rest = lib.settle(v, m.get('flutua') or ())
    return v, m, removed, filled, rest

def main():
    for aid in sys.argv[1:] or list(ARENAS):
        v, m, removed, filled, rest = prepare(aid)
        print('%s: %d removidos, %d preenchidos, %d grupos soltos (%d blocos)' % (
            aid, removed, filled, len(rest), sum(len(c) for c, _ in rest)), flush=True)
        for comp, box in sorted(rest, key=lambda g: -len(g[0]))[:25]:
            top = Counter(lib._name(v.b[c]) for c in comp).most_common(3)
            print('   %5d  %s  %s' % (len(comp), box, ', '.join('%s x%d' % t for t in top)), flush=True)

if __name__ == '__main__':
    main()
