// Sukuna's X, Clivar (捌), as in the 3D preview Stark approved (forja/sukuna-desmantelar, "X: Clivar").
//   cd tools/sukuna && node bake_clivar.js
// The preview's hand-keyed poses (SK for Sukuna, TK for the target; degrees in the preview's rig, body lean and twist in
// radians) baked tick by tick (every half tick) into KeyframeAnimation files; x and y are flipped into the mod's axes.
// Sukuna: sukuna.clivar_corre (the crouch, the sprint with legs and the free arm pumping, the gripping arm shooting out,
// then holding up by the neck while the lines measure: the head turns a little), sukuna.clivar_prensa (against a wall:
// the arm pushes the target into it), sukuna.clivar_joga(_parede) (keeps holding, winds the arm back, throws, follows
// through and stands), sukuna.clivar_solta(_parede) (lethal: holds, then lowers the empty hand). Target (players):
// sukuna.clivar_preso (hands at the neck, held), sukuna.clivar_mole (limp after the snap), sukuna.clivar_voa (flailing
// in the air). Timings match power/powers/sukuna/Clivar (RUN0, GRAB, MEASURE, HOLD, WIND).
const fs = require('fs');
const path = require('path');
const dir = path.join(__dirname, '../../src/main/resources/assets/battlegroundsmod/animations');
const ease = { out: u => 1 - Math.pow(1 - u, 3), snap: u => 1 - Math.pow(1 - u, 5), io: u => u < .5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2 };
const cl = (x, a, b) => Math.max(a, Math.min(b, x));
const SK = {
  stand: { h: [0, 0, 0], ra: [0, 0, 4], la: [0, 0, -4], g: {} },
  crouch: { h: [-12, 0, 0], ra: [40, 0, 14], la: [35, 0, -14], rl: [-38, 0, 0], ll: [28, 0, 0], g: { lean: 0.38, twist: 0.08 } },
  grabF: { h: [-12, 6, 0], ra: [-112, 0, -4], la: [14, 0, -12], rl: [-14, 0, 0], ll: [22, 0, 0], g: { lean: -0.16, twist: 0.12 } },
  grabW: { h: [-12, 0, 0], ra: [-100, 0, -6], la: [18, 0, -14], rl: [-26, 0, 0], ll: [32, 0, 0], g: { lean: 0.12, twist: 0.25 } },
  wind: { h: [-14, 34, 0], ra: [24, 0, 96], la: [-62, 0, -26], rl: [34, 0, 0], ll: [-38, 0, 0], g: { lean: -0.16, twist: -0.85 } },
  throw: { h: [2, -12, 0], ra: [-140, 0, 20], la: [42, 0, -22], rl: [42, 0, 0], ll: [-36, 0, 0], g: { lean: 0.36, twist: 0.75 } },
  follow: { h: [12, -8, 0], ra: [-48, 0, 26], la: [46, 0, -16], rl: [30, 0, 0], ll: [-30, 0, 0], g: { lean: 0.46, twist: 0.95 } },
  lower: { h: [6, 0, 0], ra: [-22, 0, 12], la: [0, 0, -6], rl: [-10, 0, 0], ll: [12, 0, 0], g: {} },
};
const TK = {
  stand: { ra: [0, 0, 4], la: [0, 0, -4], g: {} },
  held: { h: [-26, 0, 0], ra: [-150, 0, -16], la: [-150, 0, 16], rl: [12, 0, 0], ll: [-8, 0, 0], g: {} },
  limp: { h: [38, 0, 0], ra: [8, 0, 9], la: [8, 0, -9], rl: [8, 0, 0], ll: [-4, 0, 0], g: {} },
  flail: { h: [18, 0, 0], ra: [-130, 0, 30], la: [-95, 0, -35], rl: [-30, 0, 0], ll: [25, 0, 0], g: {} },
};
const BONES = { h: 'Head', ra: 'Right Arm', la: 'Left Arm', rl: 'Right Leg', ll: 'Left Leg' };
const D = 180 / Math.PI;
function lerp(a, b, u) {
  const o = { g: {} };
  for (const k of Object.keys(BONES)) {
    const x = a[k] || [0, 0, 0], y = b[k] || [0, 0, 0];
    o[k] = [0, 1, 2].map(i => x[i] + (y[i] - x[i]) * u);
  }
  for (const k of ['lean', 'twist']) o.g[k] = ((a.g || {})[k] || 0) + (((b.g || {})[k] || 0) - ((a.g || {})[k] || 0)) * u;
  return o;
}
function keys(list) {
  return t => {
    if (t <= list[0].t) return list[0].p;
    for (let i = 1; i < list.length; i++) {
      const k = list[i];
      if (t <= k.t) {
        const a = list[i - 1];
        return lerp(a.p, k.p, ease[k.e || 'io'](cl((t - a.t) / Math.max(1e-6, k.t - a.t), 0, 1)));
      }
    }
    return list[list.length - 1].p;
  };
}
function headTurn(p, deg) {
  const o = lerp(p, p, 0);
  o.h[1] += deg;
  return o;
}
// fn: tick -> pose; last: ticks baked; hold: seconds the last pose stays.
function bake(name, fn, last, hold = 0) {
  const chans = {};
  for (const b of Object.keys(BONES)) chans[b] = [];
  chans.body = [];
  for (let k = 0; k <= last * 2; k++) {
    const tt = k / 2;
    const p = fn(tt);
    const ts = +(tt / 20).toFixed(4);
    for (const b of Object.keys(BONES)) {
      const r = p[b] || [0, 0, 0];
      chans[b].push({ timestamp: ts, target: [-r[0], -r[1], r[2]].map(x => +x.toFixed(3)), interpolation: 'linear' });
    }
    chans.body.push({ timestamp: ts, target: [-((p.g || {}).lean || 0) * D, -((p.g || {}).twist || 0) * D, 0].map(x => +x.toFixed(3)), interpolation: 'linear' });
  }
  const animations = Object.keys(BONES).map(b => ({ bone: BONES[b], target: 'rotation', keyframes: chans[b] }));
  animations.push({ bone: 'Body', target: 'rotation', keyframes: chans.body });
  const out = { length: +((last / 20) + hold).toFixed(4), animations };
  fs.writeFileSync(path.join(dir, name + '.json'), JSON.stringify(out));
  console.log(`${name}: ${out.length} s`);
}
const RUN0 = 3, GRAB = 9, PIN = 3, MEASURE = 48, HOLD = 12, WIND = 9, SIL = 5;
// the measuring: the head turns 10 degrees as the lines run over the body
const measured = (G, t0) => t => headTurn(G, 10 * ease.io(cl((t - t0) / MEASURE, 0, 1)));
bake('sukuna.clivar_corre', t => {
  if (t < RUN0) return keys([{ t: 0, p: SK.stand }, { t: RUN0, p: SK.crouch, e: 'out' }])(t);
  if (t < GRAB) {
    const ph = t - RUN0, s = Math.sin(ph), reach = ease.io(cl((t - (GRAB - 2.2)) / 2.2, 0, 1));
    const run = { h: [-8, 0, 0], rl: [-58 * s, 0, 0], ll: [58 * s, 0, 0], la: [48 * s, 0, -10], ra: [-48 * s, 0, 10], g: { lean: 0.5, twist: 0.12 * s } };
    let p = lerp(run, SK.grabF, reach);
    if (t < RUN0 + 1.5) p = lerp(SK.crouch, p, ease.out((t - RUN0) / 1.5));
    return p;
  }
  return measured(SK.grabF, GRAB + 2)(t);
}, GRAB + 2 + MEASURE, 3);
bake('sukuna.clivar_prensa', t => t < PIN ? lerp(SK.grabF, SK.grabW, ease.out(t / PIN)) : measured(SK.grabW, PIN + 2)(t), PIN + 2 + MEASURE, 3);
for (const [suffix, G] of [['', SK.grabF], ['_parede', SK.grabW]]) {
  const g = headTurn(G, 10);
  bake('sukuna.clivar_joga' + suffix, keys([{ t: 0, p: g }, { t: HOLD, p: g }, { t: HOLD + 6, p: SK.wind, e: 'io' }, { t: HOLD + WIND, p: SK.wind },
    { t: HOLD + WIND + 2, p: SK.throw, e: 'snap' }, { t: HOLD + WIND + 6, p: SK.follow, e: 'out' }, { t: HOLD + WIND + 24, p: SK.stand, e: 'io' }]),
    HOLD + WIND + 24);
  bake('sukuna.clivar_solta' + suffix, keys([{ t: 0, p: g }, { t: SIL + 18, p: g }, { t: SIL + 44, p: SK.lower }, { t: SIL + 60, p: SK.stand, e: 'io' }]),
    SIL + 60);
}
bake('sukuna.clivar_preso', keys([{ t: 0, p: TK.stand }, { t: 3, p: TK.held, e: 'out' }]), 3, 6);
bake('sukuna.clivar_mole', keys([{ t: 0, p: TK.held }, { t: 4, p: TK.limp, e: 'out' }]), 4, 4);
bake('sukuna.clivar_voa', t => {
  const p = lerp(TK.limp, TK.flail, ease.out(cl(t / 3, 0, 1)));
  const k = Math.sin(t * 0.7), fade = 1 - cl((t - 30) / 10, 0, 1);
  p.ra[0] += 25 * k * fade;
  p.la[0] -= 25 * k * fade;
  p.rl[0] += 20 * k * fade;
  return t > 30 ? lerp(p, TK.stand, ease.io(cl((t - 30) / 10, 0, 1))) : p;
}, 40);
