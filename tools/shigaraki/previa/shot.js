// Prints of the approved preview (tentaculos-de-maos.html) at the moments the game prints are compared with.
//   cd tools/shigaraki/previa && npm i --no-save playwright-core && node shot.js [out dir]
// Uses the installed Edge (real GPU: software WebGL draws the ground black). Writes <skill>_<t>.png (the canvas only,
// 1600x900) and meta.json (the camera and Shigaraki's place and facing at each print, in the preview's frame).
const { chromium } = require('playwright-core');
const fs = require('fs');
const path = require('path');
const out = process.argv[2] || path.join(__dirname, 'shots');
fs.mkdirSync(out, { recursive: true });
// [name, cast, the cast's clock start, times (s), camera ('perto' = the one the skill sets itself)]
const ONLY = process.env.ONLY;
const S0 = [
  ['z_acerta', "__dbg.castZ('acerta')", '__dbg.Z.t0', [0.3, 0.6, 1.2, 2.0], 'perto'],
  ['z_erra', "__dbg.castZ('erra')", '__dbg.Z.t0', [0.6, 1.0, 1.6], 'perto'],
  ['x_onda', '__dbg.castX()', '__dbg.XS.t0', [0.45, 0.8, 1.3], 'perto'],
  ['c_maos', '__dbg.castC()', '__dbg.CS.t0', [0.5, 1.5, 3.0, 4.5], 'perto'],
  ['b_absorver', '__dbg.castB()', '__dbg.BS.t0', [0.3, 1.0, 1.8], 'perto'],
  ['shz_regen', '__dbg.castRG()', '__dbg.RG.t0', [0.8, 2.0, 3.5], 'perto'],
  ['v_tentaculos', '__dbg.cast()', '__dbg.simT', [1.0, 3.0, 6.0], 'livre'],
  ['shv_fim', '__dbg.castFM()', '__dbg.FM.t0', [1.0, 2.0, 3.5, 5.5], 'livre'],
];
const S = ONLY ? S0.filter((x) => ONLY.split(',').includes(x[0])) : S0;
(async () => {
  const b = await chromium.launch({ channel: process.env.CH === 'none' ? undefined : (process.env.CH || 'msedge'), args: ['--use-angle=d3d11', '--ignore-gpu-blocklist', '--enable-gpu'] });
  const meta = {};
  for (const [name, cast, t0e, times, camk] of S) {
    const p = await b.newPage({ viewport: { width: 1600, height: 900 } });
    await p.route('**/three.min.js', (r) => r.fulfill({ path: path.join(__dirname, 'three.min.js'), contentType: 'application/javascript' }));
    p.on('pageerror', (e) => console.log('ERR', name, e.message));
    await p.goto('file:///' + path.join(__dirname, 'tentaculos-de-maos.html').replace(/\\/g, '/'));
    await p.waitForFunction(() => window.__dbg && __dbg.simT > 0.3, null, { timeout: 120000 });
    // a clean world, Shigaraki at the origin, then the skill
    await p.evaluate(() => { [...document.querySelectorAll('button')].find((x) => x.textContent === 'Recomeçar terreno').click(); });
    await p.waitForTimeout(400);
    await p.evaluate(cast);
    if (camk === 'livre') await p.evaluate(() => document.querySelectorAll('#cams button')[0].click());
    const t0 = await p.evaluate(t0e);
    meta[name] = [];
    for (const t of times) {
      await p.waitForFunction(`__dbg.simT - ${t0} >= ${t}`, null, { timeout: 600000, polling: 10 });
      await p.evaluate(() => document.querySelector('#pause').click());
      const info = await p.evaluate(`({t: __dbg.simT - ${t0}, cam: {yaw: __dbg.cam.yaw, pitch: __dbg.cam.pitch, dist: __dbg.cam.dist, tgt: __dbg.cam.tgt.toArray(), pos: __dbg.camera.position.toArray()}, shig: __dbg.shigPos.toArray()})`);
      await p.locator('canvas').first().screenshot({ path: path.join(out, `${name}_${t.toFixed(1)}.png`), timeout: 120000 });
      meta[name].push(info);
      console.log(name, JSON.stringify(info));
      await p.evaluate(() => document.querySelector('#pause').click());
    }
    await p.close();
  }
  fs.writeFileSync(path.join(out, 'meta.json'), JSON.stringify(meta, null, 1));
  await b.close();
})();
