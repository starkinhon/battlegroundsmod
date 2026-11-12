import os
d=os.path.dirname(os.path.abspath(__file__))
page=open(os.path.join(d,'page.html')).read()
eng='<script>\n'+open(os.path.join(d,'engine.js')).read()+'\n</script>'
its='<script>\n'+open(os.path.join(d,'itens.js')).read()+'\n</script>'
page=page.replace('<!--ENGINE-->',eng).replace('<!--ITEMS-->',its)
open(os.path.join(d,'itens-db.html'),'w').write(page)
