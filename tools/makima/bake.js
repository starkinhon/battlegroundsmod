// Makima's poses, from the 3D previews Stark approved on 2026-11-11 (forja/makima-*).
//   cd tools/makima && node bake.js
// The previews' poses are radians in their three.js rig (aRx/aRy/aRz arms, lLx/lRx legs, headX/Y/Z, torsoX/Y and hipY,
// the hips' height, 0.75 standing). Minecraft's model is the same figure turned half a turn about x (y down, forward
// is -z): x stays, y and z flip. The preview's torso lean becomes the rig's whole-body lean (tools/sukuna/rig.js), with
// the legs turned back by it so they keep their own angle, and the hips' drop its y.
const fs = require('fs');
const path = require('path');
const { channels } = require('../sukuna/rig');
const dir = path.join(__dirname, '../../src/main/resources/assets/battlegroundsmod/animations');
const D = 180 / Math.PI;
const ease = { out: u => 1 - Math.pow(1 - u, 3), snap: u => 1 - Math.pow(1 - u, 5), io: u => u < .5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2,
  step: u => Math.floor(u * 8) / 8 };
const cl = (x, a, b) => Math.max(a, Math.min(b, x));
const KEYS = ['aLx', 'aLy', 'aLz', 'aRx', 'aRy', 'aRz', 'lLx', 'lRx', 'lLz', 'lRz', 'headX', 'headY', 'headZ', 'torsoX', 'torsoY', 'hipY'];
function mix(a, b, u) {
  const o = {};
  for (const k of KEYS) {
    const d = k === 'hipY' ? 0.75 : 0;
    o[k] = (a[k] ?? d) + ((b[k] ?? d) - (a[k] ?? d)) * u;
  }
  return o;
}
function rig(p) {
  const lean = p.torsoX || 0;
  return {
    h: [p.headX * D, -(p.headY || 0) * D, -(p.headZ || 0) * D],
    ra: [p.aRx * D, -p.aRy * D, -p.aRz * D],
    la: [p.aLx * D, -p.aLy * D, -p.aLz * D],
    rl: [(p.lRx - lean) * D, 0, -(p.lRz || 0) * D],
    ll: [(p.lLx - lean) * D, 0, -(p.lLz || 0) * D],
    g: { lean, twist: -(p.torsoY || 0), y: (p.hipY ?? 0.75) - 0.75 },
  };
}
function poseAt(keys, t) {
  if (t <= keys[0].t) return mix(keys[0].p, keys[0].p, 0);
  for (let i = 1; i < keys.length; i++) {
    const k = keys[i];
    if (t <= k.t) {
      const a = keys[i - 1];
      return mix(a.p, k.p, ease[k.e || 'io'](cl((t - a.t) / Math.max(1e-6, k.t - a.t), 0, 1)));
    }
  }
  return mix(keys[keys.length - 1].p, keys[keys.length - 1].p, 0);
}
// keys: [{ t (ticks), p, e }]; f(t, pose) may add motion on top (a tremble, a step).
function bake(name, keys, f) {
  const last = keys[keys.length - 1].t;
  const poses = [];
  for (let k = 0; k <= last * 2; k++) {
    const t = k / 2;
    let p = poseAt(keys, t);
    if (f) p = f(t, p) || p;
    poses.push({ ts: +(t / 20).toFixed(4), p: rig(p) });
  }
  const out = { length: +(last / 20).toFixed(4), animations: channels(poses) };
  fs.writeFileSync(path.join(dir, name + '.json'), JSON.stringify(out));
  console.log(`${name}: ${out.length} s`);
}

// ---- her poses ----
const IDLE = { aLx: -0.45, aLz: -0.28, aRx: -0.45, aRz: 0.28, aLy: 0.2, aRy: -0.2, headX: 0.04 };   // hands joined in front
const STAND = {};
const AIM = { aRx: -Math.PI / 2 - 0.08, aRy: 0, aRz: 0.02, aLx: -0.3, aLz: -0.1, aLy: 0.25, torsoY: -0.18, headX: -0.03, headY: 0.12 };
const CHAIN = { aRx: -Math.PI / 2 - 0.06, aRz: 0.02, aLx: -0.35, aLz: -0.12, aLy: 0.3, headX: -0.02, torsoY: -0.12 };
const LIFT = { aRx: -1.75, aRz: 0.05, aRy: 0, aLx: -0.4, aLz: -0.15, aLy: 0.25, headX: -0.08 };
const PRESS = { aRx: -0.95, aRz: 0.08, aLx: -0.4, aLz: -0.15, aLy: 0.25, headX: 0.1 };
const FIST = { aRx: -1.35, aRz: 0.12, aRy: 0.25, aLx: -0.4, aLz: -0.15, aLy: 0.25, headX: 0.02, headZ: 0.12 };
const BECKON = { aRx: -1.25, aRy: 0.4, aRz: -0.1, aLx: -0.4, aLz: -0.15, aLy: 0.25, headX: -0.05, headZ: 0.1 };
const KNEEL_UP = { aRx: -1.7, aRz: 0.05, aLx: -0.4, aLz: -0.15, aLy: 0.25, headX: -0.08 };
const KNEEL_DOWN = { aRx: -0.75, aRz: 0.08, aLx: -0.4, aLz: -0.15, aLy: 0.25, headX: 0.15 };
const TILT = { aRx: -0.9, aRz: 0.15, aLx: -0.5, aLz: -0.25, aLy: 0.3, headX: 0.22, headZ: 0.18, torsoX: 0.08 };
const POINT = { aRx: -Math.PI / 2 - 0.05, aRz: 0.02, aLx: -0.35, aLz: -0.12, aLy: 0.25, headX: 0.02 };
const RELEASE = { aRx: -0.75, aRz: 0.35, aLx: -0.75, aLz: -0.35, headX: 0.15 };
const SEE = { aLx: -0.7, aLz: -0.38, aRx: -0.7, aRz: 0.38, aLy: 0.35, aRy: -0.35, headX: 0.32, torsoX: 0.05 };
const WRITE = { aLx: -0.95, aLz: -0.15, aLy: 0.3, aRx: -0.85, aRz: 0.3, aRy: -0.2, headX: 0.5, torsoX: 0.05 };
const PRAY = { aLx: -0.98, aLz: -0.6, aLy: 0.4, aRx: -0.98, aRz: 0.6, aRy: -0.4, headX: 0.42, torsoX: 0.06 };
const CUT = { aRx: -1.05, aRz: 0.12, aRy: -0.5, aLx: -0.75, aLz: -0.35, aLy: 0.6, headX: 0.3 };
const CALL = { aRx: -1.25, aRz: 0.15, aRy: 0.5, aLx: -0.45, aLz: -0.28, aLy: 0.2, headX: -0.05 };
const SPREAD = { aRx: -0.5, aLx: -0.5, aRz: 0.4, aLz: -0.4, headX: 0.12 };

// ---- the others' poses ----
const STUN = { hipY: 0.24, lLx: 1.35, lRx: 1.3, torsoX: 0.28, headX: 0.42, aLx: 0.25, aRx: 0.25, aLz: 0.1, aRz: -0.1 };
const BOUND = { hipY: 0.22, lLx: 1.4, lRx: 1.38, torsoX: 0.12, headX: -0.15, aLx: 0.05, aRx: 0.05, aLz: 0.05, aRz: -0.05 };
const KNEEL = { hipY: 0.24, lLx: 1.4, lRx: 1.36, torsoX: 0.18, headX: 0.55, aLx: 0.12, aRx: 0.12, aLz: 0.12, aRz: -0.12 };
const FOUR = { hipY: 0.3, lLx: 1.45, lRx: 1.42, torsoX: 1.02, headX: -0.95, aLx: -1.02, aRx: -1.02, aLz: 0.06, aRz: -0.06 };
const FLOOR = { hipY: 0.2, lLx: 1.5, lRx: 1.46, torsoX: 1.2, headX: -0.2, aLx: -1.4, aRx: -1.35, aLz: 0.25, aRz: -0.25 };
const LIMP = { hipY: 0.22, lLx: 1.4, lRx: 1.3, torsoX: 0.3, headX: 0.8, headZ: 0.3, aLx: 0.2, aRx: 0.15, aLz: 0.2, aRz: -0.15 };
const DEAD = { hipY: 0.16, lLx: 1.5, lRx: 1.45, torsoX: 1.45, headX: 0.3, headZ: 0.5, aLx: -0.4, aRx: -0.2, aLz: 0.6, aRz: -0.3 };
const WALK = { aLx: 0.05, aRx: 0.05, aLz: 0.05, aRz: -0.05, headX: 0.25, headZ: 0.25, torsoX: 0.06 };

// X Corrente: aims, the chain flies (tick 15 it reaches), a short pull back, held on them until it lets go.
bake('makima.corrente', [{ t: 0, p: IDLE }, { t: 5, p: CHAIN, e: 'out' }, { t: 14, p: CHAIN }, { t: 17, p: mix(CHAIN, { ...CHAIN, aRx: CHAIN.aRx + 0.3, torsoY: -0.3 }, 1), e: 'snap' },
  { t: 26, p: CHAIN }, { t: 108, p: CHAIN }, { t: 118, p: IDLE }, { t: 124, p: STAND }]);
bake('makima.preso', [{ t: 0, p: STAND }, { t: 5, p: STUN, e: 'snap' }, { t: 84, p: STUN }, { t: 92, p: STAND }],
  (t, p) => { p.torsoY = Math.sin(t * 0.9) * 0.08 * (t > 5 && t < 84 ? 1 : 0); p.aLz += p.torsoY; });
// V without souls: the hand up, then pressed down; "Morram": a fist.
bake('makima.v_ergue', [{ t: 0, p: IDLE }, { t: 6, p: LIFT, e: 'out' }, { t: 14, p: LIFT }, { t: 18, p: PRESS, e: 'snap' }, { t: 140, p: PRESS }, { t: 150, p: IDLE }, { t: 156, p: STAND }]);
bake('makima.amarrado', [{ t: 0, p: STAND }, { t: 4, p: BOUND, e: 'snap' }, { t: 112, p: BOUND }, { t: 120, p: STAND }],
  (t, p) => { const s = Math.sin(t * 1.7) * 0.03 * (t > 4 && t < 112 ? 1 : 0); p.torsoY = s; p.headY = s * 2; });
bake('makima.morram', [{ t: 0, p: IDLE }, { t: 3, p: FIST, e: 'snap' }, { t: 40, p: FIST }, { t: 50, p: IDLE }, { t: 56, p: STAND }]);
// Z Bang: the finger gun up and held (as long as Z is held); every shot a short kick.
bake('makima.bang', [{ t: 0, p: IDLE }, { t: 5, p: AIM, e: 'out' }, { t: 128, p: AIM }, { t: 136, p: IDLE }, { t: 142, p: STAND }]);
bake('makima.bang_tiro', [{ t: 0, p: AIM }, { t: 1, p: { ...AIM, aRx: AIM.aRx - 0.35, headZ: 0.05 }, e: 'snap' }, { t: 5, p: AIM, e: 'out' }, { t: 22, p: AIM },
  { t: 30, p: IDLE }, { t: 36, p: STAND }]);
// Shift+X Ordem: her gesture per word, the word leaving at 0.55 s.
bake('makima.ordem_venha', [{ t: 0, p: IDLE }, { t: 7, p: BECKON, e: 'out' }, { t: 32, p: BECKON }, { t: 90, p: BECKON }, { t: 100, p: IDLE }, { t: 106, p: STAND }],
  (t, p) => { const b = t > 8 && t < 32 ? Math.max(0, Math.sin((t - 8) * 0.45)) : 0; p.aRx -= b * 0.35; });
bake('makima.ordem_ajoelhe', [{ t: 0, p: IDLE }, { t: 6, p: KNEEL_UP, e: 'out' }, { t: 10, p: KNEEL_UP }, { t: 13, p: KNEEL_DOWN, e: 'snap' }, { t: 72, p: KNEEL_DOWN },
  { t: 82, p: IDLE }, { t: 88, p: STAND }]);
bake('makima.ordem_woof', [{ t: 0, p: IDLE }, { t: 8, p: TILT, e: 'out' }, { t: 88, p: TILT }, { t: 98, p: IDLE }, { t: 104, p: STAND }]);
bake('makima.ordem_morra', [{ t: 0, p: IDLE }, { t: 8, p: POINT, e: 'out' }, { t: 90, p: POINT }, { t: 100, p: IDLE }, { t: 106, p: STAND }]);
// Who obeys: stiff steps (8 poses a second) then on the knees; kneeling; on all fours, panting; limp, then fallen.
bake('makima.venha', [{ t: 0, p: STAND }, { t: 3, p: WALK, e: 'snap' }, { t: 64, p: WALK }, { t: 69, p: KNEEL, e: 'snap' }, { t: 84, p: KNEEL }, { t: 90, p: STAND }],
  (t, p) => { if (t > 3 && t < 64) { const q = Math.floor(t / 2.5) * 2.5; const s = Math.sin(q * 0.47); p.lLx = s * 0.55; p.lRx = -s * 0.55; p.headX += (Math.floor(t / 2.5) % 2) * 0.04; } });
bake('makima.ajoelhado', [{ t: 0, p: STAND }, { t: 2, p: KNEEL, e: 'snap' }, { t: 72, p: KNEEL }, { t: 80, p: STAND }],
  (t, p) => { if (t > 2 && t < 10) { const tr = Math.sin(t * 4.5) * 0.03 * (1 - (t - 2) / 8); p.torsoY = tr; p.headY = -tr; } });
bake('makima.woof', [{ t: 0, p: STAND }, { t: 4, p: FOUR, e: 'out' }, { t: 84, p: FOUR }, { t: 92, p: STAND }],
  (t, p) => { if (t > 8 && t < 84) { p.headX += Math.sin(t * 0.7) * 0.04; p.headZ = 0.15 * Math.sin(t * 0.11); } });
bake('makima.morra', [{ t: 0, p: STAND }, { t: 9, p: STAND }, { t: 16, p: LIMP, e: 'io' }, { t: 22, p: LIMP }, { t: 28, p: DEAD, e: 'io' }, { t: 84, p: DEAD }],
  (t, p) => { if (t < 9) { const tr = Math.sin(t * 4) * 0.02; p.torsoY = tr; p.headY = tr * 2; } });
bake('makima.quatro', [{ t: 0, p: KNEEL }, { t: 3, p: FLOOR, e: 'snap' }, { t: 70, p: FLOOR }, { t: 80, p: STAND }],
  (t, p) => { if (t > 3 && t < 11) { const tr = Math.sin(t * 4.5) * 0.03 * (1 - (t - 3) / 8); p.torsoY = tr; p.headY = -tr; } });
// C, Shift+C, B, V.
bake('makima.fantoche', [{ t: 0, p: IDLE }, { t: 5, p: RELEASE, e: 'out' }, { t: 48, p: RELEASE }, { t: 58, p: IDLE }, { t: 64, p: STAND }]);
bake('makima.ratos', [{ t: 0, p: IDLE }, { t: 6, p: SEE, e: 'out' }, { t: 192, p: SEE }, { t: 200, p: IDLE }, { t: 206, p: STAND }]);
bake('makima.afunda', [{ t: 0, p: SEE }, { t: 4, p: { ...SEE, headX: 0.5 }, e: 'out' }, { t: 8, p: { ...SEE, headX: 0.5 } }]);
bake('makima.sobe', [{ t: 0, p: { ...IDLE, headX: 0.6 } }, { t: 9, p: IDLE, e: 'step' }, { t: 26, p: IDLE }, { t: 32, p: STAND }]);
bake('makima.escreve', [{ t: 0, p: IDLE }, { t: 6, p: WRITE, e: 'out' }, { t: 600, p: WRITE }, { t: 606, p: STAND }],
  (t, p) => { if (t > 6) { p.aRy += Math.sin(t * 1.3) * 0.08; p.aRx += Math.sin(t * 2.1) * 0.04; } });
bake('makima.ritual', [{ t: 0, p: IDLE }, { t: 7, p: PRAY, e: 'out' }, { t: 120, p: PRAY }, { t: 130, p: IDLE }, { t: 136, p: STAND }]);
bake('makima.ritual_sangue', [{ t: 0, p: IDLE }, { t: 7, p: PRAY, e: 'out' }, { t: 19, p: PRAY }, { t: 23, p: CUT, e: 'snap' }, { t: 52, p: CUT }, { t: 60, p: PRAY },
  { t: 80, p: PRAY }, { t: 90, p: IDLE }, { t: 96, p: STAND }]);
bake('makima.levantem', [{ t: 0, p: IDLE }, { t: 7, p: CALL, e: 'out' }, { t: 72, p: CALL }, { t: 82, p: IDLE }, { t: 88, p: STAND }]);
bake('makima.morram_almas', [{ t: 0, p: IDLE }, { t: 6, p: SPREAD, e: 'out' }, { t: 84, p: SPREAD }, { t: 94, p: IDLE }, { t: 100, p: STAND }]);
bake('makima.parado', [{ t: 0, p: WRITE }, { t: 6, p: IDLE, e: 'out' }, { t: 12, p: STAND }]);
