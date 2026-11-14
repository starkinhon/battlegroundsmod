import os
d=os.path.dirname(os.path.abspath(__file__))
rd=lambda f: open(os.path.join(d,f)).read()
page=rd('head.part')+rd('body.part')
eng='<script>\n'+rd('engine.js')+'\n</script>\n<script>\n'+rd('fx.js')+'\n</script>\n<script>\n'+rd('dbfx.js')+'\n</script>\n<script>\n'+rd('jjfx.js')+'\n</script>'
MOBS=['mosca.js','cabecao.js','transfigurado.js','metro.js','rastejante.js','verme.js','olhos.js','utero.js','boneco.js','kitsune.js','sino.js','espantalho.js','poco.js','kuchisake.js','peixe.js','caranguejo.js','embriao.js','afogado.js','broto.js','javali.js','guardiao.js','inseto.js','vulcaozinho.js','golem.js','lanterna.js','mechamaru.js','oni.js','larva.js','abate.js','solta.js','kogane.js']
mobs=''.join('<script>\n'+rd(f)+'\n</script>\n' for f in MOBS if os.path.exists(os.path.join(d,f)))
page=page.replace('<!--ENGINE-->',eng).replace('<!--MOBS-->',mobs)
open(os.path.join(d,'mobs-jujutsu.html'),'w').write(page)
print('ok', len(page))
