import os
d=os.path.dirname(os.path.abspath(__file__))
rd=lambda f: open(os.path.join(d,f)).read()
page=rd('head.part')+rd('body.part')
eng='<script>\n'+rd('engine.js')+'\n</script>\n<script>\n'+rd('fx.js')+'\n</script>'
mobs=''.join('<script>\n'+rd(f)+'\n</script>\n' for f in ['renegado.js','ninken.js','cobra.js','tigre.js','som.js','centopeia.js','leque.js','escorpiao.js','marionete.js','espadachim.js','tubarao.js','caranguejo.js','cacador.js','chuva.js','garca.js','salamandra.js','zetsu.js','corvo.js','clone.js','sapo.js','inseto.js'])
page=page.replace('<!--ENGINE-->',eng).replace('<!--MOBS-->',mobs)
open(os.path.join(d,'mobs-naruto.html'),'w').write(page)
print('ok', len(page))
