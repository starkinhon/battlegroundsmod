import base64, json, sys
sk = {n: 'data:image/png;base64,' + base64.b64encode(open(n + '.png','rb').read()).decode() for n in ['goku','vegeta','piccolo','kuririn','freeza']}
h = open('page.html').read().replace('/*SKINS*/{}', json.dumps(sk))
open('db-voo.html','w').write(h)
