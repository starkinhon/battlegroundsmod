// Exporta as maldições de Jujutsu da prévia aprovada pro mod (Stark 2026-11-14: "porta"). Mesmo formato dos mobs de
// Naruto e DB (tools/mobs/dbmobs/export.js); o id no jogo é "jj_" + o id da prévia.
//   assets/battlegroundsmod/blocky/jj/jj_<id>_<v>.json e _low.json: partes, pivôs, caixas com UV (normal e low poly)
//   textures/entity/jj/jj_<id>_<v>.png e _low.png: a MESMA pintura da prévia
//   blocky/jj/jj_<id>.anim.json e _low.anim.json: a animação da prévia ASSADA, quadro a quadro (20 por segundo),
//     cada canal que mexe (posição, rotação e escala de cada parte, e o corpo inteiro). O jogo toca isso em vez de
//     reescrever a animação à mão, então o jogo mexe igual à prévia.
// Uso: python3 build.py && S=<pasta com three/package> node export.js
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const DIR = __dirname, ROOT = path.join(DIR, '..', '..', '..', 'src/main/resources/assets/battlegroundsmod');
const OUT_M = path.join(ROOT, 'blocky/jj'), OUT_T = path.join(ROOT, 'textures/entity/jj');
// sementes extras sorteadas pros mobs que variam (cada um que nasce pega uma)
const EXTRA = { mosca: 3, cabecao: 3, transfigurado: 5, verme: 2, olhos: 2, kitsune: 2, espantalho: 2, poco: 1, kuchisake: 1,
  peixe: 6, caranguejo: 2, embriao: 1, afogado: 2, broto: 3, javali: 2, inseto: 3, vulcaozinho: 2, larva: 2, abate: 6, solta: 1 };
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await b.newPage();
  p.on('pageerror', e => console.log('ERR', e.message));
  await p.route('**/three.min.js', r => r.fulfill({ body: fs.readFileSync(process.env.S + '/three/package/build/three.min.js'), contentType: 'application/javascript' }));
  await p.route('**fonts.googleapis**', r => r.fulfill({ body: '', contentType: 'text/css' }));
  await p.goto('file://' + path.join(DIR, 'mobs-jujutsu.html'));
  await p.waitForTimeout(1500);
  const out = await p.evaluate(({ EXTRA }) => { try { return run(); } catch (err) { return { err: String(err.stack) }; } function run() {
    window.__T = 0;
    const r3 = x => Math.round(x * 1000) / 1000;
    // o low poly não tem todas as partes do normal: a anim mexe numa parte de mentira (Proxy) em vez de quebrar
    const fake = () => ({ obj: new THREE.Object3D(), rest: null });
    const FAKES = new WeakMap();
    const safe = parts => new Proxy(parts, { get: (o, k) => { if (k in o || typeof k !== 'string') return o[k]; let f = FAKES.get(o); if (!f) FAKES.set(o, f = {}); return f[k] = f[k] || fake(); } });
    function ser(d) {
      return { name: d.name, pivot: d.pivot, rot: d.rot || [0, 0, 0], boxes: (d.boxes || []).map(bx => [bx.x, bx.y, bx.z, bx.w, bx.h, bx.d, bx.u, bx.v]), kids: (d.kids || []).filter(Boolean).map(ser) };
    }
    function height(model) { let lo = 1e9; for (const bx of model.allBoxes) lo = Math.min(lo, bx.oy + bx.y); return (24 - lo) / 16; }
    function width(model) { let lo = 1e9, hi = -1e9; for (const bx of model.allBoxes) { lo = Math.min(lo, bx.ox + bx.x, bx.oz + bx.z); hi = Math.max(hi, bx.ox + bx.x + bx.w, bx.oz + bx.z + bx.d); } return (hi - lo) / 16; }
    const files = [];
    const model = (name, def, k) => {
      const m = ENG.build(def); const tex = ENG.paint(m, m.W, m.H);
      files.push({ kind: 'model', name, json: { w: m.W, h: m.H, scale: r3(k), height: r3(height(m) * k), width: r3(width(m) * k), root: ser(def) }, png: tex.toDataURL('image/png') });
    };
    // a escala de uma variação: a do corpo (obj, com o anim subindo o pivô 1,5*(K-1)) vezes a do modelo dentro dele
    function scaleOf(b, vi, ld) {
      cur = built.indexOf(b); curV = vi; lod = ld;
      const e = ent();
      for (const k in cache) cache[k].visible = false;
      ENG.reset(e.model); e.obj.position.set(0, 1.5, 0); e.obj.rotation.set(0, 0, 0); e.obj.scale.set(-1 / 16, -1 / 16, 1 / 16);
      if (e.obj.children[0]) e.obj.children[0].scale.set(1, 1, 1);
      b.m.anim(safe(e.model.parts), 1e-4, 'parado', 0, e.obj, e, ctx);
      const kObj = Math.abs(e.obj.scale.y) * 16, kIn = e.obj.children[0] ? Math.abs(e.obj.children[0].scale.y) : 1;
      return { kObj, k: kObj * kIn };
    }
    // período de cada golpe: o "t % N" do bloco do modo
    // a anim da Mulher do Poço é um invólucro (animIn): os períodos dela vão aqui
    const PERIOD = { poco: { pratos: 6.4, puxar: 5.6, morte: 3.4 } };
    function period(src, md, id) {
      if (PERIOD[id] && PERIOD[id][md]) return PERIOD[id][md];
      // cada bloco "mode === 'x'" (pode haver mais de um, o primeiro às vezes é só uma condição): o primeiro com "t % N";
      // a morte que usa JJFX.exorcize dura o "dur" dele (3,4 s se não disser)
      let i = -1;
      while ((i = src.indexOf(`mode === '${md}'`, i + 1)) >= 0) {
        const j = src.indexOf('mode ===', i + 10), blk = src.slice(i, j < 0 ? undefined : j);
        const m = /t % ([\d.]+)/.exec(blk);
        if (m) return +m[1];
        if (blk.includes('JJFX.exorcize')) { const d = /dur: ([\d.]+)/.exec(blk); return d ? +d[1] : 3.4; }
      }
      return null;
    }
    // assa um modo: roda a anim da prévia a 20 quadros por segundo e grava o que mudou em cada parte
    function bake(b, e, md, side, kObj) {
      const src = b.m.anim.toString();
      const per = period(src, md, b.m.id);
      const loop = per == null || md === 'parado' || md === 'andar';
      const dur = md === 'parado' || md === 'andar' ? Math.max(8, per || 8) : (per || 8);
      const n = Math.round(dur * 20);
      const ch = {}, put = (k, f, v) => { (ch[k] = ch[k] || new Array(n).fill(0))[f] = r3(v); };
      for (let f = 0; f < n; f++) {
        const t = f / 20 + 1e-4;
        for (const k in cache) cache[k].visible = false;
        ENG.reset(e.model);
        e.obj.visible = true; e.obj.position.set(0, 1.5, 0); e.obj.rotation.set(0, 0, 0);
        REF.reset(99);
        b.m.anim(safe(e.model.parts), t, md, 0, e.obj, e, ctx);
        let parts = e.model.parts, obj = e.obj, base = [0, 1.5 * kObj, 0];
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
          put(name + '.hide', f, o.visible ? 0 : 1); put(name + '.sx', f, o.scale.x - 1); put(name + '.sy', f, o.scale.y - 1); put(name + '.sz', f, o.scale.z - 1);
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
      const pm = false;
      const mainId = 'jj_' + id;
      // modelos e pinturas de cada variação
      const vs = b.m.variants.slice();
      if (b.m.randomVariant && EXTRA[id]) for (let k = 0; k < EXTRA[id]; k++) { const save = Math.random; let s = 9001 + k * 977 + i * 31; Math.random = () => ((s = (s * 16807) % 2147483647) / 2147483647); vs.push(b.m.randomVariant()); Math.random = save; }
      // as sorteadas entram na prévia como variações a mais, pra medir a escala delas
      const extra = vs.slice(b.vs.length);
      for (const v of extra) b.vs.push({ v, hi: make(v, false), lo: make(v, true) });
      let kObj0 = 1;
      vs.forEach((v, k) => {
        const sh = scaleOf(b, k, 'hi'), sl = scaleOf(b, k, 'lo');
        if (k === 0) kObj0 = sh.kObj;
        model(`${mainId}_${k}`, v.def, sh.k); model(`${mainId}_${k}_low`, v.low, sl.k);
        if (pm) { model(`marionetista_${k}`, v.pmDef); model(`marionetista_${k}_low`, v.pmLow); }
      });
      // animações assadas, normal e low, da 1ª variação
      cur = i; curV = 0;
      for (const ld of ['hi', 'lo']) {
        lod = ld;
        const e = ent();
        const modes = {}, pms = {};
        for (const [md] of b.m.modes) { mode = md; modes[md] = bake(b, e, md, false, kObj0); }
        anims.push({ name: mainId + (ld === 'lo' ? '_low' : ''), json: { fps: 20, variants: vs.length, scale: r3(kObj0), modes } });
        if (pm) anims.push({ name: 'marionetista' + (ld === 'lo' ? '_low' : ''), json: { fps: 20, variants: vs.length, modes: pms } });
      }
    });
    return { files, anims };
  } }, { EXTRA });
  if (out.err) { console.log(out.err); process.exit(1); }
  fs.mkdirSync(OUT_M, { recursive: true }); fs.mkdirSync(OUT_T, { recursive: true });
  for (const f of out.files) {
    fs.writeFileSync(path.join(OUT_M, f.name + '.json'), JSON.stringify(f.json));
    fs.writeFileSync(path.join(OUT_T, f.name + '.png'), Buffer.from(f.png.split(',')[1], 'base64'));
  }
  for (const a of out.anims) fs.writeFileSync(path.join(OUT_M, a.name + '.anim.json'), JSON.stringify(a.json));
  console.log('modelos', out.files.length, 'animações', out.anims.length);
  await b.close();
})();
