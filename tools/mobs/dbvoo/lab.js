const { chromium } = require('playwright'); const path = require('path'); const dir = __dirname;
(async () => { const b = await chromium.launch({ args: ['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader'] }); const pg = await b.newPage({ viewport: { width: 640, height: 500 } });
  pg.on('pageerror', e => console.log('ERR', e.message));
  await pg.route('**/cdn.jsdelivr.net/npm/three@0.128.0/**', r => { const p = r.request().url().split('three@0.128.0/')[1]; r.fulfill({ path: path.join(dir, 'package', p), contentType: 'application/javascript' }); });
  await pg.route('**/fonts.googleapis.com/**', r => r.fulfill({ body: '', contentType: 'text/css' }));
  await pg.goto('file://' + path.join(dir, 'db-voo.html')); await pg.waitForTimeout(1000);
  await pg.evaluate(() => window.__set({ scene: 'luta' }));
  let last = ''; for (let i = 0; i < 160; i++) { const l = await pg.evaluate(() => { window.__step(15); return window.__lab(); }); const k = l.split(' | ')[0]; if (k !== last || (i>44 && i<64)) { console.log((i * 0.25).toFixed(2), l); last = k; } }
  await b.close(); })();
