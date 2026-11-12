// Exporta os mobs de Naruto da prévia aprovada pro mod (Stark 2026-11-12: "tá perfeito, só caprichar no spawn").
//   assets/battlegroundsmod/blocky/naruto/<id>_<v>.json e <id>_<v>_low.json: partes, pivôs, caixas com UV (normal e low poly)
//   textures/entity/naruto/<id>_<v>.png e <id>_<v>_low.png: a MESMA pintura da prévia
//   blocky/naruto/<id>.anim.json e <id>_low.anim.json: a animação da prévia ASSADA, quadro a quadro (20 por segundo),
//     cada canal que mexe (posição, rotação e escala de cada parte, e o corpo inteiro). O jogo toca isso em vez de
//     reescrever a animação à mão, então o jogo mexe igual à prévia.
// Uso: python3 build.py && S=<pasta com three/package> node export.js
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const DIR = __dirname, ROOT = path.join(DIR, '..', '..', '..', 'src/main/resources/assets/battlegroundsmod');
const OUT_M = path.join(ROOT, 'blocky/naruto'), OUT_T = path.join(ROOT, 'textures/entity/naruto');
// sementes extras sorteadas pros mobs que variam (cada um que nasce pega uma)
const EXTRA = { ninja_renegado: 6, ninja_chuva: 5, sapo_ninja: 7 };
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await b.newPage();
  p.on('pageerror', e => console.log('ERR', e.message));
  await p.route('**/three.min.js', r => r.fulfill({ body: fs.readFileSync(process.env.S + '/three/package/build/three.min.js'), contentType: 'application/javascript' }));
  await p.route('**fonts.googleapis**', r => r.fulfill({ body: '', contentType: 'text/css' }));
  await p.goto('file://' + path.join(DIR, 'mobs-naruto.html'));
  await p.waitForTimeout(1500);
  const out = await p.evaluate(({ EXTRA }) => {
    window.__T = 0;
    const r3 = x => Math.round(x * 1000) / 1000;
    function ser(d) {
      return { name: d.name, pivot: d.pivot, rot: d.rot || [0, 0, 0], boxes: (d.boxes || []).map(bx => [bx.x, bx.y, bx.z, bx.w, bx.h, bx.d, bx.u, bx.v]), kids: (d.kids || []).filter(Boolean).map(ser) };
    }
    function height(model) { let lo = 1e9; for (const bx of model.allBoxes) lo = Math.min(lo, bx.oy + bx.y); return (24 - lo) / 16; }
    function width(model) { let lo = 1e9, hi = -1e9; for (const bx of model.allBoxes) { lo = Math.min(lo, bx.ox + bx.x, bx.oz + bx.z); hi = Math.max(hi, bx.ox + bx.x + bx.w, bx.oz + bx.z + bx.d); } return (hi - lo) / 16; }
    const files = [];
    const model = (name, def) => {
      const m = ENG.build(def); const tex = ENG.paint(m, m.W, m.H);
      files.push({ kind: 'model', name, json: { w: m.W, h: m.H, height: r3(height(m)), width: r3(width(m)), root: ser(def) }, png: tex.toDataURL('image/png') });
    };
    // período de cada golpe: o "t % N" do bloco do modo
    function period(src, md) {
      const i = src.indexOf(`mode === '${md}'`); if (i < 0) return null;
      const j = src.indexOf('mode ===', i + 10);
      const m = /t % ([\d.]+)/.exec(src.slice(i, j < 0 ? undefined : j));
      return m ? +m[1] : null;
    }
    // assa um modo: roda a anim da prévia a 20 quadros por segundo e grava o que mudou em cada parte
    function bake(b, e, md, side) {
      const src = b.m.anim.toString();
      const per = period(src, md);
      const loop = per == null;
      const dur = per || 8;
      const n = Math.round(dur * 20);
      const ch = {}, put = (k, f, v) => { (ch[k] = ch[k] || new Array(n).fill(0))[f] = r3(v); };
      for (let f = 0; f < n; f++) {
        const t = f / 20 + 1e-4;
        for (const k in cache) cache[k].visible = false;
        ENG.reset(e.model);
        e.obj.visible = true; e.obj.position.set(0, 1.5, 0); e.obj.rotation.set(0, 0, 0);
        REF.reset(99);
        b.m.anim(e.model.parts, t, md, 0, e.obj, e, ctx);
        let parts = e.model.parts, obj = e.obj, base = [0, 1.5, 0];
        if (side) {
          const key = Object.keys(cache).find(k => /marionetista/.test(k) && cache[k].visible && cache[k].model);
          const pm = cache[key];
          parts = pm.model.parts; obj = pm; base = [0.9, 1.5, 3.4];
        }
        for (const name in parts) {
          const o = parts[name].obj, rest = parts[name].rest || null;
          const rp = rest ? rest.pos : [0, 0, 0], rr = rest ? rest.rot : [0, 0, 0];
          put(name + '.x', f, o.position.x - rp[0]); put(name + '.y', f, o.position.y - rp[1]); put(name + '.z', f, o.position.z - rp[2]);
          put(name + '.rx', f, o.rotation.x - rr[0]); put(name + '.ry', f, o.rotation.y - rr[1]); put(name + '.rz', f, o.rotation.z - rr[2]);
          put(name + '.sx', f, o.scale.x - 1); put(name + '.sy', f, o.scale.y - 1); put(name + '.sz', f, o.scale.z - 1);
        }
        put('@.x', f, obj.position.x - base[0]); put('@.y', f, obj.position.y - base[1]); put('@.z', f, obj.position.z - base[2]);
        put('@.rx', f, obj.rotation.x); put('@.ry', f, obj.rotation.y); put('@.rz', f, obj.rotation.z);
        put('@.hide', f, obj.visible ? 0 : 1);
      }
      for (const k in ch) if (ch[k].every(v => Math.abs(v) < 2e-3)) delete ch[k];
      return { frames: n, loop, ch };
    }
    const anims = [];
    built.forEach((b, i) => {
      const id = b.m.id;
      const pm = id === 'marionetista';
      const mainId = pm ? 'marionete' : id;
      // modelos e pinturas de cada variação
      const vs = b.m.variants.slice();
      if (b.m.randomVariant && EXTRA[id]) for (let k = 0; k < EXTRA[id]; k++) { const save = Math.random; let s = 9001 + k * 977 + i * 31; Math.random = () => ((s = (s * 16807) % 2147483647) / 2147483647); vs.push(b.m.randomVariant()); Math.random = save; }
      vs.forEach((v, k) => {
        model(`${mainId}_${k}`, v.def); model(`${mainId}_${k}_low`, v.low);
        if (pm) { model(`marionetista_${k}`, v.pmDef); model(`marionetista_${k}_low`, v.pmLow); }
      });
      // animações assadas, normal e low, da 1ª variação
      cur = i; curV = 0;
      for (const ld of ['hi', 'lo']) {
        lod = ld;
        const e = ent();
        const modes = {}, pms = {};
        for (const [md] of b.m.modes) { mode = md; modes[md] = bake(b, e, md, false); if (pm) pms[md] = bake(b, e, md, true); }
        anims.push({ name: mainId + (ld === 'lo' ? '_low' : ''), json: { fps: 20, variants: vs.length, modes } });
        if (pm) anims.push({ name: 'marionetista' + (ld === 'lo' ? '_low' : ''), json: { fps: 20, variants: vs.length, modes: pms } });
      }
    });
    return { files, anims };
  }, { EXTRA });
  fs.mkdirSync(OUT_M, { recursive: true }); fs.mkdirSync(OUT_T, { recursive: true });
  for (const f of out.files) {
    fs.writeFileSync(path.join(OUT_M, f.name + '.json'), JSON.stringify(f.json));
    fs.writeFileSync(path.join(OUT_T, f.name + '.png'), Buffer.from(f.png.split(',')[1], 'base64'));
  }
  for (const a of out.anims) fs.writeFileSync(path.join(OUT_M, a.name + '.anim.json'), JSON.stringify(a.json));
  console.log('modelos', out.files.length, 'animações', out.anims.length);
  await b.close();
})();
