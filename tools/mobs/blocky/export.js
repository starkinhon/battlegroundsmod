// Exporta os modelos da prévia pro mod: assets/battlegroundsmod/blocky/<id>.json (partes, pivôs, caixas com UV) e
// textures/entity/<id>.png, com a MESMA pintura da prévia. Namekianos: N variantes por semente + índice.
// node tools/mobs/blocky/export.js   (precisa do playwright e do three só pra prévia; aqui não usa three)
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const DIR = __dirname, ROOT = path.join(DIR, '..', '..', '..', 'src/main/resources/assets/battlegroundsmod');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage();
  p.on('pageerror', e => console.log('ERR', e.message));
  let html = '<!doctype html><html><body>';
  for (const f of ['engine.js', 'namek.js', 'peixe.js', 'androide8.js', 'urso.js', 'bora.js']) html += '<script>' + fs.readFileSync(path.join(DIR, f), 'utf8') + '</script>';
  html += '</body></html>';
  await p.setContent(html);
  const out = await p.evaluate(() => {
    function ser(d) {
      return {
        name: d.name, pivot: d.pivot, rot: d.rot || [0, 0, 0],
        boxes: (d.boxes || []).map(b => [b.x, b.y, b.z, b.w, b.h, b.d, b.u, b.v]),
        kids: (d.kids || []).map(ser),
      };
    }
    function bounds(model) {
      let lo = 1e9, hi = -1e9;
      for (const bx of model.allBoxes) { lo = Math.min(lo, bx.oy + bx.y); hi = Math.max(hi, bx.oy + bx.y + bx.h); }
      return [lo, hi];
    }
    const res = [];
    const one = (id, def, extra) => {
      const model = ENG.build(def);
      const tex = ENG.paint(model, model.W, model.H);
      res.push({ id, json: { w: model.W, h: model.H, height: (24 - bounds(model)[0]) / 16, root: ser(def), ...extra }, png: tex.toDataURL('image/png') });
    };
    for (const m of window.MOBS) if (m.def) one(m.id, m.def, {});
    // namekianos: as 8 da prévia + sementes sorteadas, misturando tipo, altura e largura
    const list = window.NAMEK.MK.map(([t, s, f]) => window.NAMEK.variant(t, s, f));
    const types = ['guerreiro', 'aldeao', 'anciao', 'guerreiro', 'aldeao'];
    for (let s = 101; list.length < 40; s += 7) list.push(window.NAMEK.variant(types[s % 5], s));
    list.forEach((v, i) => one('namekiano_' + i, v.def, { type: v.m.type, build: v.m.build }));
    return res;
  });
  fs.mkdirSync(path.join(ROOT, 'blocky'), { recursive: true });
  const index = [];
  for (const r of out) {
    fs.writeFileSync(path.join(ROOT, 'blocky', r.id + '.json'), JSON.stringify(r.json));
    fs.writeFileSync(path.join(ROOT, 'textures/entity', r.id + '.png'), Buffer.from(r.png.split(',')[1], 'base64'));
    if (r.id.startsWith('namekiano_')) index.push({ type: r.json.type, height: +r.json.height.toFixed(3) });
  }
  fs.writeFileSync(path.join(ROOT, 'blocky', 'namekiano_index.json'), JSON.stringify(index));
  console.log('exported', out.length);
  await b.close();
})();
