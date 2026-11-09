// Sukuna's chant and the World Cutting Slash (the 3D preview Stark approved on 2026-11-09, forja/sukuna-corte-mundo).
//   cd tools/sukuna && node bake_chant.js
// Writes animations/sukuna.canto (the three words, the right hand up in front of the face, two fingers raised, a little
// higher each word; the last pose then held while Z is held), sukuna.canto_gesto (both hands joined in front of the
// chest, head down: the vulnerable second before the World Cutting Slash), sukuna.corte_mundo (standing release: arm
// high, then brought down in one stroke; the cut leaves on tick RELEASE) and sukuna.waffle (crouched release: the open
// hand thrust forward; legs left to the crouch). Pose keys are the preview's SK (degrees in the preview's rig, body lean
// and twist in radians); in the files x and y are flipped into the mod's axes, as KeyframeAnimation reads them.
const fs = require('fs');
const path = require('path');
const dir = path.join(__dirname, '../../src/main/resources/assets/battlegroundsmod/animations');
const ease = { out: u => 1 - Math.pow(1 - u, 3), snap: u => 1 - Math.pow(1 - u, 5), io: u => u < .5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2 };
const cl = (x, a, b) => Math.max(a, Math.min(b, x));
const SK = {
  stand: { h: [0, 0, 0], ra: [0, 0, 4], la: [0, 0, -4], g: {} },
  word1: { h: [4, 0, 0], ra: [-92, -18, -22], la: [4, 0, -6], g: {} },
  word2: { h: [2, 0, 0], ra: [-104, -22, -26], la: [6, 0, -8], g: { lean: 0.02 } },
  word3: { h: [0, 0, 0], ra: [-116, -26, -30], la: [8, 0, -10], g: { lean: 0.03 } },
  gest: { h: [10, 0, 0], ra: [-78, -10, -30], la: [-78, 10, 30], g: {} },
  windPe: { h: [-6, 0, 0], ra: [-176, 0, 10], la: [-24, 0, -14], rl: [-8, 0, 0], ll: [10, 0, 0], g: { twist: -0.18, lean: -0.06 } },
  relPe: { h: [4, 0, 0], ra: [-30, 0, 8], la: [22, 0, -16], rl: [-20, 0, 0], ll: [24, 0, 0], g: { twist: 0.12, lean: 0.22 } },
  crouch: { h: [-14, 0, 0], ra: [-70, -10, -24], la: [-70, 10, 24], g: {} },
  windWa: { h: [-14, 10, 0], ra: [-50, 0, 72], la: [-60, 0, -6], g: { twist: -0.35 } },
  relWa: { h: [-12, -4, 0], ra: [-98, 0, -6], la: [36, 0, -26], g: { twist: 0.3 } },
};
const BONES = { h: 'Head', ra: 'Right Arm', la: 'Left Arm', rl: 'Right Leg', ll: 'Left Leg' };
const D = 180 / Math.PI;
function poseAt(keys, t) {
  if (t <= keys[0].t) return keys[0].p;
  for (let i = 1; i < keys.length; i++) {
    const k = keys[i];
    if (t <= k.t) {
      const a = keys[i - 1];
      const u = ease[k.e || 'io'](cl((t - a.t) / Math.max(1e-6, k.t - a.t), 0, 1));
      const o = { g: {} };
      for (const b of Object.keys(BONES)) {
        const x = a.p[b] || [0, 0, 0], y = k.p[b] || [0, 0, 0];
        o[b] = [0, 1, 2].map(j => x[j] + (y[j] - x[j]) * u);
      }
      for (const g of ['lean', 'twist']) o.g[g] = (a.p.g[g] || 0) + ((k.p.g[g] || 0) - (a.p.g[g] || 0)) * u;
      return o;
    }
  }
  return keys[keys.length - 1].p;
}
// legs: false leaves them to Minecraft (the crouch); hold: seconds the last pose stays (the key is still held).
function bake(name, keys, { legs = true, hold = 0 } = {}) {
  const last = keys[keys.length - 1].t;
  const chans = {};
  const bones = Object.keys(BONES).filter(b => legs || (b !== 'rl' && b !== 'll'));
  for (const b of bones) chans[b] = [];
  chans.body = [];
  for (let k = 0; k <= last * 2; k++) {
    const tt = k / 2;
    const p = poseAt(keys, tt);
    const ts = +(tt / 20).toFixed(4);
    for (const b of bones) {
      const r = p[b] || [0, 0, 0];
      chans[b].push({ timestamp: ts, target: [-r[0], -r[1], r[2]].map(x => +x.toFixed(3)), interpolation: 'linear' });
    }
    chans.body.push({ timestamp: ts, target: [-(p.g.lean || 0) * D, -(p.g.twist || 0) * D, 0].map(x => +x.toFixed(3)), interpolation: 'linear' });
  }
  const animations = bones.map(b => ({ bone: BONES[b], target: 'rotation', keyframes: chans[b] }));
  animations.push({ bone: 'Body', target: 'rotation', keyframes: chans.body });
  const out = { length: +((last / 20) + hold).toFixed(4), animations };
  fs.writeFileSync(path.join(dir, name + '.json'), JSON.stringify(out));
  console.log(`${name}: ${out.length} s`);
}
// The chant: a word every WORD ticks; each pose snaps in over 3-4 ticks.
const WORD = 10, RELEASE = 6;
bake('sukuna.canto', [{ t: 0, p: SK.stand }, { t: 4, p: SK.word1, e: 'snap' }, { t: WORD, p: SK.word1 }, { t: WORD + 3, p: SK.word2, e: 'snap' },
  { t: 2 * WORD, p: SK.word2 }, { t: 2 * WORD + 3, p: SK.word3, e: 'snap' }], { hold: 60 });
bake('sukuna.canto_gesto', [{ t: 0, p: SK.word3 }, { t: 5, p: SK.gest, e: 'out' }], { hold: 60 });
bake('sukuna.corte_mundo', [{ t: 0, p: SK.gest }, { t: 4, p: SK.windPe, e: 'out' }, { t: RELEASE, p: SK.windPe }, { t: RELEASE + 2, p: SK.relPe, e: 'snap' },
  { t: RELEASE + 50, p: SK.relPe }, { t: RELEASE + 80, p: SK.stand, e: 'io' }]);
bake('sukuna.waffle', [{ t: 0, p: SK.gest }, { t: 3, p: SK.crouch, e: 'out' }, { t: 5, p: SK.windWa, e: 'out' }, { t: RELEASE, p: SK.windWa },
  { t: RELEASE + 2, p: SK.relWa, e: 'snap' }, { t: RELEASE + 40, p: SK.relWa }, { t: RELEASE + 60, p: SK.crouch, e: 'io' }], { legs: false });
