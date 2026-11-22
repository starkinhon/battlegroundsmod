"""Every model's textures and parents, every blockstate's models and every OBJ model's file must exist in the mod's
assets. A model pointing at a deleted file draws black and magenta in the game (2026-11-22: the Kubikiribocho in the
Névoa grave still pointed at the old texture that #529 removed). Runs in CI; exits 1 on any missing file.

python3 tools/assets/refs.py
"""
import glob, json, os, re, sys

A = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets')
OURS = set(os.listdir(A))

def split(ref):
    return ref.split(':', 1) if ':' in ref else ('minecraft', ref)

bad = []
for f in glob.glob(os.path.join(A, '*', 'models', '**', '*.json'), recursive=True):
    try:
        m = json.load(open(f, encoding='utf-8'))
    except Exception as e:
        bad.append((f, 'json', str(e)))
        continue
    for k, v in (m.get('textures') or {}).items():
        if isinstance(v, str) and not v.startswith('#'):
            ns, p = split(v)
            if ns in OURS and not os.path.exists(os.path.join(A, ns, 'textures', p + '.png')):
                bad.append((f, 'texture ' + k, v))
    par = m.get('parent')
    if isinstance(par, str) and not par.startswith('builtin/'):
        ns, p = split(par)
        if ns in OURS and not os.path.exists(os.path.join(A, ns, 'models', p + '.json')):
            bad.append((f, 'parent', par))
    if m.get('loader') in ('neoforge:obj', 'forge:obj'):
        ns, p = split(m.get('model', ''))
        if not os.path.exists(os.path.join(A, ns, p)):
            bad.append((f, 'obj', m.get('model')))
for f in glob.glob(os.path.join(A, '*', 'blockstates', '*.json')):
    for ref in re.findall(r'"model"\s*:\s*"([^"]+)"', open(f, encoding='utf-8').read()):
        ns, p = split(ref)
        if ns in OURS and not os.path.exists(os.path.join(A, ns, 'models', p + '.json')):
            bad.append((f, 'model', ref))

for b in bad:
    print('FALTANDO: %s  (%s -> %s)' % (os.path.relpath(b[0], A), b[1], b[2]))
print('referencias de assets: %d faltando' % len(bad))
sys.exit(1 if bad else 0)
