import os
d=os.path.dirname(os.path.abspath(__file__))
rd=lambda f: open(os.path.join(d,f)).read()
page=rd('head.part')+rd('body.part')
eng='<script>\n'+rd('../naruto/engine.js')+'\n</script>\n<script>\n'+rd('../naruto/fx.js')+'\n</script>\n<script>\n'+rd('dbfx.js')+'\n</script>'
MOBS=['saibaman.js','bandido.js','redribbon.js','pirata.js','yeti.js','raptor.js','chifrudo.js','trex.js','ptero.js','dragao.js','soldado.js','celljr.js','yakon.js','tigre.js','peixe.js','androide.js','taopaipai.js','majin.js','babidi.js']
mobs=''.join('<script>\n'+rd(f)+'\n</script>\n' for f in MOBS if os.path.exists(os.path.join(d,f)))
page=page.replace('<!--ENGINE-->',eng).replace('<!--MOBS-->',mobs)
open(os.path.join(d,'mobs-db.html'),'w').write(page)
print('ok', len(page))
