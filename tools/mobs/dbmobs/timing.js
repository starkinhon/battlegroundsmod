// Tabela de tempos de cada golpe tirada da prévia (quando o alvo reage, efeitos de tela, quanto o alvo anda).
// Uso: S=<pasta com three/package> node timing.js > timings.txt
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await b.newPage();
  await p.route('**/three.min.js', r => r.fulfill({ body: fs.readFileSync(process.env.S + '/three/package/build/three.min.js'), contentType: 'application/javascript' }));
  await p.route('**fonts.googleapis**', r => r.fulfill({ body: '', contentType: 'text/css' }));
  await p.goto('file://' + path.join(__dirname, 'mobs-db.html'));
  await p.waitForTimeout(1500);
  const txt = await p.evaluate(() => {
    const lines = [];
    const names = ['slash', 'shake', 'poison', 'dizzy', 'dark', 'drown', 'blur', 'flash', 'claw', 'roarRing', 'ember'];
    const log = {};
    for (const n of names) { const f = ctx[n]; ctx[n] = (...a) => { const v = typeof a[1] === 'number' ? a[1] : typeof a[0] === 'number' ? a[0] : 1; if (v > 0.01) (log[n] = log[n] || []).push(cf); return f(...a); }; }
    let cf = 0;
    built.forEach((bb, i) => {
      cur = i; curV = 0; lod = 'hi';
      const e = ent(); const src = bb.m.anim.toString();
      for (const [md] of bb.m.modes) {
        if (md === 'parado' || md === 'andar') continue;
        const k = src.indexOf(`mode === '${md}'`); const j = src.indexOf('mode ===', k + 10);
        const m = /t % ([\d.]+)/.exec(src.slice(k, j < 0 ? undefined : j)); const per = m ? +m[1] : 4;
        for (const n in log) delete log[n];
        let react = -1, p0 = null, pmax = 0, track = [];
        for (let f = 0; f < per * 20; f++) {
          cf = f; mode = md;
          for (const kk in cache) cache[kk].visible = false;
          ENG.reset(e.model); e.obj.visible = true; e.obj.position.set(0, 1.5, 0); e.obj.rotation.set(0, 0, 0); REF.reset(99);
          bb.m.anim(e.model.parts, f / 20 + 1e-4, md, 0, e.obj, e, ctx);
          const pos = ref.position.clone();
          if (!p0) p0 = pos.clone();
          const lean = Math.abs(refBody.rotation.x) + Math.abs(RP.armR.rotation.z) + Math.abs(RP.armR.rotation.x) + Math.abs(ref.rotation.y);
          if (react < 0 && lean > 0.02) react = f;
          pmax = Math.max(pmax, pos.distanceTo(p0));
          if (f % 5 === 0) track.push(`${f}:${pos.x.toFixed(1)},${pos.y.toFixed(1)},${pos.z.toFixed(1)}`);
        }
        const ev = Object.entries(log).map(([n, fs]) => `${n}[${fs[0]}-${fs[fs.length - 1]}]`).join(' ');
        lines.push(`${bb.m.id}.${md} per=${per}s (${per * 20}t) react=${react} refStart=${p0.z.toFixed(1)} move=${pmax.toFixed(1)} ${ev}\n   ${track.join(' ')}`);
      }
    });
    return lines.join('\n');
  });
  console.log(txt);
  await b.close();
})();
