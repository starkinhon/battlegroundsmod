// Sukuna's base moves with the Dismantle (the 3D preview Stark approved on 2026-11-09, forja/sukuna-combo, his version):
// before each punch an open hand slashes the air and the cut leaves it halfway through the slash.
//   cd tools/sukuna && npm i three@0.160.0 && node bake_combo.js
// Reads the bare-fist animations (animations/soco.*.json) and writes animations/sukuna.combo1..5, sukuna.forte_golpe,
// sukuna.barragem_final and sukuna.dash: the same poses with the swing laid over the arm (and, in the combo, 3 ticks
// of the arm rising before each punch). Prints where the hand is when the cut leaves it and which way it slashes, in
// Sukuna's own frame (feet at 0, facing +z, +x his left), for power/powers/sukuna/SukunaMelee.java.
const fs = require('fs');
const path = require('path');
const THREE = require('three');
global.THREE = THREE;
const Rig = require('../espada/rig.js');
const dir = path.join(__dirname, '../../src/main/resources/assets/battlegroundsmod/animations');
const D = Math.PI / 180;
const load = n => JSON.parse(fs.readFileSync(path.join(dir, n + '.json'), 'utf8'));
const ease = { out: u => 1 - Math.pow(1 - u, 3), snap: u => 1 - Math.pow(1 - u, 5), io: u => u < .5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2 };
const cl = (x, a, b) => Math.max(a, Math.min(b, x));

// The swings, as in the preview: rotations of the arm in the model [x, y, z] (x -1.57 = arm ahead, -3 = overhead;
// y + turns the right arm outwards and the left arm across the body). cock: where the hand starts, slash: where it ends.
const SW = {
  downL: { cock: [-2.6, -0.9, 0], slash: [-1.0, 0.9, 0] },
  flatR: { cock: [-1.57, 1.2, 0], slash: [-1.57, -1.0, 0] },
  upL: { cock: [-0.7, -0.9, 0], slash: [-2.4, 0.9, 0] },
  backR: { cock: [-1.5, -1.0, 0], slash: [-1.5, 1.2, 0] },
  overR: { cock: [-3.0, 0.2, 0], slash: [-0.9, -0.3, 0] },
  bigL: { cock: [-3.0, -0.6, 0], slash: [-0.8, 0.8, 0] },
  backL: { cock: [-1.5, 1.0, 0], slash: [-1.5, -1.2, 0] },
  flatL: { cock: [-1.57, -1.2, 0], slash: [-1.57, 1.0, 0] },
};
const SWING = 3, RELEASE = 1.4, RAISE = 3;
function swingRot(sw, lt, base) {
  const a = 1, b = 1.8, L = (x, y, u) => x.map((v, j) => v + (y[j] - v) * u);
  if (lt < a) return L(base, sw.cock, ease.out(lt / a));
  if (lt < b) return L(sw.cock, sw.slash, ease.snap((lt - a) / (b - a)));
  return L(sw.slash, base, ease.io(cl((lt - b) / (SWING - b), 0, 1)));
}
function sample(ch, s) {
  const ks = ch.keyframes;
  if (s <= ks[0].timestamp) return ks[0].target.slice();
  if (s >= ks[ks.length - 1].timestamp) return ks[ks.length - 1].target.slice();
  let i = 0; while (i < ks.length - 2 && ks[i + 1].timestamp <= s) i++;
  const u = (s - ks[i].timestamp) / (ks[i + 1].timestamp - ks[i].timestamp);
  return ks[i].target.map((x, j) => x + (ks[i + 1].target[j] - x) * u);
}
const ARM = { L: 'Left Arm', R: 'Right Arm' };
function bake(srcName, opt) {
  const src = load(srcName);
  const pre = opt.raise ? RAISE : 0;
  const lenTicks = pre + Math.round(src.length * 20);
  const sw = Object.assign({ t: opt.swingAt }, SW[opt.swing]);
  const out = { length: +(lenTicks / 20).toFixed(4), animations: [] };
  for (const ch of src.animations) {
    const neutral = ch.target === 'scale' ? [1, 1, 1] : [0, 0, 0];
    const keyframes = [];
    for (let k = 0; k <= lenTicks * 2; k++) {
      const tt = k / 2;
      let v;
      if (tt < pre) v = neutral.map((n, j) => n + (sample(ch, 0)[j] - n) * ease.out(cl(tt / (pre - 0.5), 0, 1)));
      else v = sample(ch, (tt - pre) / 20);
      if (ch.bone === ARM[opt.hand] && ch.target === 'rotation') {
        const lt = tt - sw.t;
        if (lt >= 0 && lt <= SWING) {
          const base = [-v[0] * D, -v[1] * D, v[2] * D];
          const r = swingRot(sw, lt, base);
          v = [-r[0] / D, -r[1] / D, r[2] / D];
        }
      }
      keyframes.push({ timestamp: +(tt / 20).toFixed(4), target: v.map(x => +x.toFixed(3)), interpolation: 'linear' });
    }
    out.animations.push({ bone: ch.bone, target: ch.target, keyframes });
  }
  return { out, sw };
}
// Where a hand is at tick tt of a baked animation, in Sukuna's frame (the preview's handLocal).
const HAND = { R: new THREE.Vector3(-1 / 16, 10.5 / 16, 0), L: new THREE.Vector3(1 / 16, 10.5 / 16, 0) };
const ROOT = Rig.rootMatrix();
function hand(json, hand, tt) {
  const p = Rig.sampleJson(json, tt / 20);
  return HAND[hand].clone().applyMatrix4(ROOT.clone().multiply(Rig.partMatrix(p[hand === 'R' ? 'rightArm' : 'leftArm'])));
}
const JOBS = [
  ['soco.combo1', 'sukuna.combo1', { raise: true, hand: 'L', swing: 'downL', swingAt: 0 }],
  ['soco.combo2', 'sukuna.combo2', { raise: true, hand: 'R', swing: 'flatR', swingAt: 0 }],
  ['soco.combo3', 'sukuna.combo3', { raise: true, hand: 'L', swing: 'upL', swingAt: 0 }],
  ['soco.combo4', 'sukuna.combo4', { raise: true, hand: 'R', swing: 'backR', swingAt: 0 }],
  ['soco.combo5', 'sukuna.combo5', { raise: true, hand: 'R', swing: 'overR', swingAt: 0 }],
  ['soco.forte_golpe', 'sukuna.forte_golpe', { hand: 'L', swing: 'bigL', swingAt: 0 }],
  ['soco.barragem_final', 'sukuna.barragem_final', { hand: 'L', swing: 'backL', swingAt: 2 }],
  ['soco.dash', 'sukuna.dash', { hand: 'L', swing: 'flatL', swingAt: 0 }],
];
const f = v => v.toArray().map(x => x.toFixed(3)).join(', ');
for (const [src, name, opt] of JOBS) {
  const { out, sw } = bake(src, opt);
  fs.writeFileSync(path.join(dir, name + '.json'), JSON.stringify(out));
  const at = hand(out, opt.hand, sw.t + RELEASE);
  const slash = hand(out, opt.hand, sw.t + 1.8).sub(hand(out, opt.hand, sw.t + 1)).normalize();
  console.log(`${name}: length ${out.length}, cut leaves at tick ${sw.t + RELEASE}, hand ${f(at)}, slash ${f(slash)}`);
}
