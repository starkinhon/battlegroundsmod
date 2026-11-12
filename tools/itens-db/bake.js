// Gera os arquivos do jogo a partir da própria prévia (mesmo motor, mesma pintura): modelo 3D Alta, textura do modelo,
// ícone 32x32 e o JSON separate_transforms (inventário = ícone, resto = 3D).
// uso: node bake.js <itens-db.html> <assets/battlegroundsmod> <three.min.js>
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const [html, assets, three] = process.argv.slice(2);
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await b.newPage();
  p.on('pageerror', e => console.log('ERR', e.message));
  await p.route('**/three.min.js', r => r.fulfill({ body: fs.readFileSync(three), contentType: 'application/javascript' }));
  await p.route('https://fonts.googleapis.com/**', r => r.fulfill({ body: '', contentType: 'text/css' }));
  await p.goto('file://' + path.resolve(html)); await p.waitForTimeout(1500);
  const out = await p.evaluate(() => {
    const D = ITEMS_DB, res = [];
    const png = (px, w, h) => D.toCanvas(px, w, h).toDataURL('image/png').split(',')[1];
    for (const it of D.ITEMS) {
      const m = D.build(it, 'hi');
      const tex = png(D.stackFrames(m.atlas, m.size, m.size), m.size, m.size * m.frames);
      const ic = D.iconRayFrames(it, 'hi', 32);
      res.push({ id: it.id, frames: m.frames, model: D.modelJson(m, 'battlegroundsmod:item/' + it.id + '_modelo'), tex, icon: png(D.stackFrames(ic, 32, 32), 32, 32 * ic.length), translucent: !!it.translucent });
    }
    return res;
  });
  const w = (f, s) => { fs.mkdirSync(path.dirname(f), { recursive: true }); fs.writeFileSync(f, s); console.log('wrote', f); };
  const anim = JSON.stringify({ animation: { frametime: 5 } }, null, 2) + '\n';
  for (const r of out) {
    w(`${assets}/models/item/${r.id}_3d.json`, JSON.stringify(r.model) + '\n');
    w(`${assets}/models/item/${r.id}_icone.json`, JSON.stringify({ parent: 'minecraft:item/generated', textures: { layer0: 'battlegroundsmod:item/' + r.id } }, null, 2) + '\n');
    const base = { parent: 'battlegroundsmod:item/' + r.id + '_3d' };
    if (r.translucent) base.render_type = 'minecraft:translucent';
    w(`${assets}/models/item/${r.id}.json`, JSON.stringify({
      loader: 'neoforge:separate_transforms', base,
      perspectives: { gui: { parent: 'battlegroundsmod:item/' + r.id + '_icone' } },
    }, null, 2) + '\n');
    w(`${assets}/textures/item/${r.id}_modelo.png`, Buffer.from(r.tex, 'base64'));
    w(`${assets}/textures/item/${r.id}.png`, Buffer.from(r.icon, 'base64'));
    if (r.frames > 1) { w(`${assets}/textures/item/${r.id}_modelo.png.mcmeta`, anim); w(`${assets}/textures/item/${r.id}.png.mcmeta`, anim); }
  }
  await b.close();
})();
