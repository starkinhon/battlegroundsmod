const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const dir = __dirname;
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const pg = await b.newPage({ viewport: { width: 1280, height: 900 } });
  const errs = []; pg.on('console', m => { if (m.type() === 'error' || m.type()==='warning') errs.push(m.text()); }); pg.on('pageerror', e => errs.push('PAGEERR ' + e.message));
  await pg.route('**/cdn.jsdelivr.net/npm/three@0.128.0/**', r => { const p = r.request().url().split('three@0.128.0/')[1]; r.fulfill({ path: path.join(dir, 'package', p), contentType: 'application/javascript' }); });
  await pg.route('**/fonts.googleapis.com/**', r => r.fulfill({ body: '', contentType: 'text/css' }));
  await pg.goto('file://' + path.join(dir, 'db-voo.html'));
  await pg.waitForTimeout(1500);
  const plan = JSON.parse(process.argv[2] || '[]');
  for (const s of plan) {
    await pg.evaluate(s => { if (s.set) window.__set(s.set); if (s.step) window.__step(s.step); }, s);
    if (s.ray) console.log(JSON.stringify(await pg.evaluate(() => window.__ray())));
    if (s.dbg) console.log(JSON.stringify(await pg.evaluate(() => window.__dbg())));
    if (s.shot) { const el = await pg.$('#stage'); const bb = await el.boundingBox(); await pg.screenshot({ path: path.join(dir, s.shot), clip: bb }); }
  }
  console.log(errs.slice(0, 15).join('\n'));
  await b.close();
})();
