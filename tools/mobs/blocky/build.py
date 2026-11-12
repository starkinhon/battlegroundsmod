import re,sys,os
d=os.path.dirname(os.path.abspath(__file__))
page=open(os.path.join(d,'page.html')).read()
eng='<script>\n'+open(os.path.join(d,'engine.js')).read()+'\n</script>'
mobs=''.join('<script>\n'+open(os.path.join(d,f)).read()+'\n</script>\n' for f in sys.argv[1:])
page=page.replace('<!--ENGINE-->',eng).replace('<!--MOBS-->',mobs)
open(os.path.join(d,'mobs-db.html'),'w').write(page)
