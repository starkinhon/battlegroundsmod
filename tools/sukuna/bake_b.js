// Sukuna's B (the old Shift+X), as in the 3D preview Stark approved (forja/sukuna-desmantelar, "Shift+X").
//   cd tools/sukuna && node bake_b.js
// Ref: Stark's first video. He sprints, punches an uppercut on the run that throws the target up and forward, the punch
// carries him into a jump whose arc is solved to pass over the target's and catch it by the neck on the way down; he
// pulls it over his head and slams it into the ground, the throw kicks him up and gravity brings him down standing on
// it; the stomp spreads a web of cuts over the ground in three stages, then the whole web cuts the ground, which blows up.
// Everything is ballistic in blocks and ticks (gravity 0.08), the preview's own formulas (its SX block), so the server
// can replay it: this writes the poses (animations/sukuna.b_corre, sukuna.b_golpe, sukuna.b_alvo_*) and the flight
// (power/powers/sukuna/BTrack.java: where Sukuna's feet and the target's centre are every tick from the punch on, in
// his frame: x to his left, y up, z ahead). The one thing the preview measures on its rig, where the held body is when
// he lets go, is set here from the slam pose (arm hanging forward from a body leaning 47 degrees).
const fs = require('fs');
const path = require('path');
const { channels } = require('./rig');
const anims = path.join(__dirname, '../../src/main/resources/assets/battlegroundsmod/animations');
const javaOut = path.join(__dirname, '../../src/main/java/com/starkinhon/battlegroundsmod/power/powers/sukuna/BTrack.java');
class V {
  constructor(x = 0, y = 0, z = 0) { this.x = x; this.y = y; this.z = z; }
  clone() { return new V(this.x, this.y, this.z); }
  add(o) { this.x += o.x; this.y += o.y; this.z += o.z; return this; }
  sub(o) { this.x -= o.x; this.y -= o.y; this.z -= o.z; return this; }
  multiplyScalar(s) { this.x *= s; this.y *= s; this.z *= s; return this; }
  lerp(o, u) { this.x += (o.x - this.x) * u; this.y += (o.y - this.y) * u; this.z += (o.z - this.z) * u; return this; }
}
const ease = { out: u => 1 - Math.pow(1 - u, 3), snap: u => 1 - Math.pow(1 - u, 5), io: u => u < .5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2 };
const cl = (x, a, b) => Math.max(a, Math.min(b, x));
// ---- the preview's SX, line for line ----
const TZ = 8;
const S = { RUN0: 3, HIT: 10.5, G: 0.08 };
S.reach = TZ - 0.95;
S.JUMP = S.HIT + 0.2; S.LAUNCH = S.HIT + 0.8;
S.C0 = new V(0, 0.75, TZ); S.vT = new V(0, 1.6, 0.55);
S.tgtAt = t => { const s = t - S.LAUNCH; return S.C0.clone().add(S.vT.clone().multiplyScalar(s)).add(new V(0, -S.G * s * s / 2, 0)); };
const lead = 1.5; let T = 20; S.MEET = S.LAUNCH + S.vT.y / S.G + 2;
for (let k = 0; k < 12; k++) { S.Cm = S.tgtAt(S.MEET); S.Sm = S.Cm.clone().add(new V(0.55, 0.3, -0.8)); T = lead + Math.sqrt(lead * lead + 2 * S.Sm.y / S.G); S.MEET = S.JUMP + T; }
S.MEET = S.JUMP + T; S.Cm = S.tgtAt(S.MEET); S.Sm = S.Cm.clone().add(new V(0.55, 0.3, -0.8));
S.run = u => Math.pow(u, 1.25); S.v0 = S.reach * 1.25 / (S.JUMP - S.RUN0);
const tau = 1.5, E = 1 - Math.exp(-T / tau); S.A = (S.Sm.z - S.reach - S.v0 * tau * E) / (T - tau * E); S.tau = tau;
S.vS = new V(0, S.G * (T - lead), S.A);
const vTm = new V(S.vT.x, S.vT.y - S.G * (S.MEET - S.LAUNCH), S.vT.z), vSm = new V(0, S.vS.y - S.G * T, S.A + (S.v0 - S.A) * Math.exp(-T / tau));
S.vJ = vTm.add(vSm).multiplyScalar(0.5);
S.WIND = S.MEET + 3; S.REL = S.MEET + 4;
S.sukAt = t => {
  if (t < S.JUMP) return new V(0.55 * cl((t - S.RUN0) / (S.JUMP - S.RUN0), 0, 1), 0, S.reach * S.run(cl((t - S.RUN0) / (S.JUMP - S.RUN0), 0, 1)));
  if (t < S.MEET) { const s = t - S.JUMP; return new V(0.55, S.vS.y * s - S.G * s * s / 2, S.reach + S.A * s + (S.v0 - S.A) * S.tau * (1 - Math.exp(-s / S.tau))); }
  if (t < S.REL || !S.vS2) { const s = t - S.MEET; return S.Sm.clone().add(S.vJ.clone().multiplyScalar(s)).add(new V(0, -S.G * s * s / 2, 0)); }
  const s = t - S.REL; const p = S.P2.clone().add(S.vS2.clone().multiplyScalar(s)).add(new V(0, -S.G * s * s / 2, 0)); p.y = Math.max(S.landY || 0, p.y);
  if (s >= S.LAND - S.REL) { const k = ease.io(cl((t - S.LAND - 4) / 6, 0, 1)); p.x = S.LX; p.z = S.LZ; p.y = S.landY - 0.08 * k; }
  return p;
};
// the held body: from where it hangs at the catch, swung over his head at the wind-up, out in front at the release
const HOLD = [[0, new V(-0.55, -0.3, 0.8)], [2, new V(-0.55, -0.3, 0.8)], [3, new V(-0.55, 1.2, 0.2)], [4, new V(-0.55, 0.0, 1.6)]];
const heldOff = s => {
  for (let i = 1; i < HOLD.length; i++) {
    if (s <= HOLD[i][0]) { const u = ease.io(cl((s - HOLD[i - 1][0]) / (HOLD[i][0] - HOLD[i - 1][0]), 0, 1)); return HOLD[i - 1][1].clone().lerp(HOLD[i][1], u); }
  }
  return HOLD[HOLD.length - 1][1].clone();
};
// init(), with the release point from the slam pose
S.R0 = S.sukAt(S.REL - 1e-4).add(heldOff(4));
S.vR = new V(0, S.vJ.y - S.G * (S.REL - S.MEET) - 0.95, S.vJ.z * 0.4 + 0.05);
{ const a = S.G / 2, b = -S.vR.y, c = 0.16 - S.R0.y; const sI = (-b + Math.sqrt(b * b - 4 * a * c)) / (2 * a);
  S.IMPACT = S.REL + sI; S.IX = S.R0.x; S.IZ = S.R0.z + S.vR.z * sI; const vI = S.vR.y - S.G * sI; S.vB = -vI * 0.25; S.bT = 2 * S.vB / S.G; }
S.TZF = S.IZ + S.vR.z * 0.5 * (S.bT + 3); S.chest = new V(S.IX, 0.3, S.TZF + 0.35); S.landY = 0.3;
S.P2 = S.sukAt(S.REL - 1e-4); const vy2 = S.vJ.y - S.G * (S.REL - S.MEET) + 0.5;
const sL = (vy2 + Math.sqrt(vy2 * vy2 + 2 * S.G * (S.P2.y - S.landY))) / S.G;
S.vS2 = new V((S.chest.x - S.P2.x) / sL, vy2, (S.chest.z - 0.25 - S.P2.z) / sL); S.LAND = S.REL + sL; S.LX = S.chest.x; S.LZ = S.chest.z - 0.25;
const WEB = [0, 14, 28], BOOM = 42, CUT = 7;
S.tgtC = t => {
  if (t < S.LAUNCH) return new V(0, 0.75, TZ);
  if (t < S.MEET) return S.tgtAt(t);
  if (t < S.REL) { const s = t - S.MEET; const k = ease.out(cl(s / 2, 0, 1)); return S.tgtAt(t).lerp(S.sukAt(t).add(heldOff(s)), k); }
  if (t < S.IMPACT) { const s = t - S.REL; return S.R0.clone().add(S.vR.clone().multiplyScalar(s)).add(new V(0, -S.G * s * s / 2, 0)); }
  const s = t - S.IMPACT; let y = 0.16, z = S.IZ;
  if (s < S.bT) { y += S.vB * s - S.G * s * s / 2; z += S.vR.z * 0.5 * s; } else { const ss = s - S.bT; z += S.vR.z * 0.5 * S.bT + S.vR.z * 0.5 * 3 * (1 - Math.exp(-ss / 3)); }
  const st = t - S.LAND; if (st >= 0 && st < 6) y -= 0.07 * Math.sin(st / 6 * Math.PI);
  return new V(S.IX, y, z);
};
// ---- poses (the preview's SK, SXK, TK) ----
const SK = {
  stand: { h: [0, 0, 0], ra: [0, 0, 4], la: [0, 0, -4], g: {} },
  crouch: { h: [-12, 0, 0], ra: [40, 0, 14], la: [35, 0, -14], rl: [-38, 0, 0], ll: [28, 0, 0], g: { lean: 0.38, twist: 0.08 } },
};
const SXK = {
  runLoad: { h: [-10, 0, 0], ra: [48, 0, 18], la: [-46, 0, -14], rl: [-44, 0, 0], ll: [40, 0, 0], g: { lean: 0.5, twist: -0.35 } },
  rise: { h: [-30, 0, 0], ra: [-172, 0, -2], la: [24, 0, -30], rl: [-40, 0, 0], ll: [18, 0, 0], g: { lean: -0.05, twist: 0.25 } },
  upper: { h: [-28, 0, 0], ra: [-176, 0, -4], la: [34, 0, -26], rl: [12, 0, 0], ll: [-28, 0, 0], g: { lean: -0.1, twist: 0.38 } },
  jump: { h: [-32, 0, 0], ra: [-166, 0, 10], la: [-24, 0, -34], rl: [-52, 0, 0], ll: [22, 0, 0], g: { lean: -0.08, twist: 0.15 } },
  windD: { h: [-22, 0, 0], ra: [-170, 0, 0], la: [-64, 0, -40], rl: [-30, 0, 0], ll: [42, 0, 0], g: { lean: -0.5, twist: 0 } },
  slam: { h: [34, 0, 0], ra: [4, 0, 6], la: [24, 0, -34], rl: [-72, 0, 0], ll: [-18, 0, 0], g: { lean: 0.82, twist: 0 } },
  fall: { h: [30, 0, 0], ra: [-24, 0, 34], la: [-24, 0, -34], rl: [-34, 0, 0], ll: [12, 0, 0], g: { lean: 0.3, twist: 0 } },
  stompPrep: { h: [24, 0, 0], ra: [-40, 0, 50], la: [-40, 0, -50], rl: [-10, 0, 0], ll: [-60, 0, 0], g: { lean: 0.15, twist: 0 } },
  stomp: { h: [30, 0, 0], ra: [28, 0, 46], la: [28, 0, -46], rl: [-34, 0, 0], ll: [22, 0, 0], g: { lean: 0.3, twist: 0 } },
  footUp: { h: [34, 0, 0], ra: [0, 0, 6], la: [0, 0, -6], rl: [-42, 0, -6], ll: [4, 0, 0], g: { lean: 0.12, twist: 0 } },
};
const TK = {
  stand: { ra: [0, 0, 4], la: [0, 0, -4], g: {} },
  limp: { h: [38, 0, 0], ra: [8, 0, 9], la: [8, 0, -9], rl: [8, 0, 0], ll: [-4, 0, 0], g: {} },
  flail: { h: [18, 0, 0], ra: [-130, 0, 30], la: [-95, 0, -35], rl: [-30, 0, 0], ll: [25, 0, 0], g: {} },
  lie: { h: [0, 0, 0], ra: [-160, 0, 20], la: [-150, 0, -25], rl: [6, 0, 0], ll: [-10, 0, 0], g: {} },
  jolt: { h: [-30, 0, 0], ra: [-110, 0, 40], la: [-100, 0, -40], rl: [-30, 0, 0], ll: [20, 0, 0], g: {} },
};
const BONES = { h: 'Head', ra: 'Right Arm', la: 'Left Arm', rl: 'Right Leg', ll: 'Left Leg' };
const D = 180 / Math.PI;
function lerp(a, b, u) {
  const o = { g: {} };
  for (const k of Object.keys(BONES)) { const x = a[k] || [0, 0, 0], y = b[k] || [0, 0, 0]; o[k] = [0, 1, 2].map(i => x[i] + (y[i] - x[i]) * u); }
  for (const k of ['lean', 'twist', 'y']) o.g[k] = ((a.g || {})[k] || 0) + (((b.g || {})[k] || 0) - ((a.g || {})[k] || 0)) * u;
  return o;
}
function keys(list) {
  return t => {
    if (t <= list[0].t) return list[0].p;
    for (let i = 1; i < list.length; i++) { const k = list[i]; if (t <= k.t) { const a = list[i - 1]; return lerp(a.p, k.p, ease[k.e || 'io'](cl((t - a.t) / Math.max(1e-6, k.t - a.t), 0, 1))); } }
    return list[list.length - 1].p;
  };
}
function bake(name, fn, t0, t1, hold = 0) {
  const poses = [];
  for (let k = 0; k <= (t1 - t0) * 2; k++) poses.push({ ts: +((k / 2) / 20).toFixed(4), p: fn(t0 + k / 2) });
  const out = { length: +(((t1 - t0) / 20) + hold).toFixed(4), animations: channels(poses) };
  fs.writeFileSync(path.join(anims, name + '.json'), JSON.stringify(out));
  console.log(`${name}: ${out.length} s`);
}
// Anime sprint (Stark 2026-11-09: the old one was "uma bosta"): 7 ticks a stride pair, the whole body leaning in from the
// feet (rig.js), the knee driven high forward and the back leg kicking out less, arms pumping against the legs, shoulders
// counter-turning, the body rising in the flight phase, the head held up and square so he looks ahead.
const runPhase = ph => {
  const w = 2 * Math.PI * ph / 7, s = Math.sin(w), c = Math.cos(w);
  const leg = v => v > 0 ? -74 * v : -46 * v;
  const tw = -0.16 * s;
  return { h: [-24 - 3 * Math.abs(c), -tw * 180 / Math.PI * 0.85, 0], rl: [leg(s), 0, 2], ll: [leg(-s), 0, -2],
    ra: [52 * s - 6, 0, 10 + 4 * Math.abs(s)], la: [-52 * s - 6, 0, -10 - 4 * Math.abs(s)],
    g: { lean: 0.44 + 0.05 * Math.abs(c), twist: tw, y: 0.05 * Math.abs(s) - 0.05 } };
};
const runPose = t => runPhase(t - S.RUN0);
// the sprint runs until he reaches the target (the server decides): the cycle baked long, the punch is its own file
bake('sukuna.b_corre', t => t < S.RUN0 ? keys([{ t: 0, p: SK.stand }, { t: S.RUN0, p: SK.crouch, e: 'out' }])(t)
  : t < S.RUN0 + 1.5 ? lerp(SK.crouch, runPose(t), ease.out((t - S.RUN0) / 1.5)) : runPose(t), 0, 40);
const H0 = S.HIT - 1, END = S.LAND + BOOM + CUT + 30;
bake('sukuna.b_golpe', keys([{ t: H0, p: SXK.runLoad }, { t: S.HIT + 0.4, p: SXK.upper, e: 'snap' }, { t: S.HIT + 5, p: SXK.rise, e: 'out' }, { t: S.MEET - 2, p: SXK.jump, e: 'io' },
  { t: S.MEET, p: SXK.jump }, { t: S.WIND, p: SXK.windD, e: 'io' }, { t: S.REL - 0.3, p: SXK.slam, e: 'snap' }, { t: S.REL + 5, p: SXK.fall, e: 'out' }, { t: S.LAND - 5, p: SXK.fall },
  { t: S.LAND - 1, p: SXK.stompPrep, e: 'io' }, { t: S.LAND, p: SXK.stomp, e: 'snap' }, { t: S.LAND + 4, p: SXK.stomp }, { t: S.LAND + 10, p: SXK.footUp, e: 'io' },
  { t: S.LAND + BOOM + CUT + 10, p: SXK.footUp }, { t: S.LAND + BOOM + CUT + 24, p: SK.stand, e: 'io' }]), H0, END);
// the target: thrown up flailing, limp in his hand and on the way down, then on its back, jolted by the stomp
bake('sukuna.b_alvo', t => {
  if (t < S.HIT) return TK.stand;
  if (t < S.MEET) return lerp(TK.stand, TK.flail, ease.out(cl((t - S.HIT) / 3, 0, 1)));
  if (t < S.IMPACT) return TK.limp;
  let p = lerp(TK.limp, TK.lie, ease.out(cl((t - S.IMPACT) / 4, 0, 1)));
  const st = t - S.LAND;
  if (st >= 0 && st < 8) p = lerp(p, TK.jolt, Math.sin(st / 8 * Math.PI));
  return p;
}, H0, S.LAND + BOOM + CUT + 10);
// ---- the flight, every tick from the punch's first tick ----
const N = Math.ceil(END - H0);
const f = v => (+v.toFixed(3)).toString();
const rows = (fn) => { const a = []; for (let k = 0; k <= N; k++) { const p = fn(H0 + k); a.push(`{${f(p.x)}, ${f(p.y)}, ${f(p.z)}}`); } return a; };
const suk = rows(S.sukAt), tgt = rows(S.tgtC);
const tick = t => Math.round(t - H0);
const java = `package com.starkinhon.battlegroundsmod.power.powers.sukuna;

// Generated by tools/sukuna/bake_b.js from the B's 3D preview (forja/sukuna-desmantelar, "Shift+X"): do not edit by hand.
// Tick k counts from the punch's first tick (the sprint reaching the target). Positions in blocks, in Sukuna's frame at
// that moment: x to his left, y up, z ahead, from his feet when he set off (the target stood at z = ${TZ}).
public final class BTrack {
    public static final int LENGTH = ${N};
    // The uppercut lands; the target leaves the ground; he catches it; he pulls it over his head; he lets go; it hits the
    // ground; he lands on it (the stomp: the first web); the second and third webs; the whole web cuts the ground; the ground
    // blows up.
    public static final int HIT = ${tick(S.HIT)}, LAUNCH = ${tick(S.LAUNCH)}, CATCH = ${tick(S.MEET)}, WIND = ${tick(S.WIND)}, RELEASE = ${tick(S.REL)}, IMPACT = ${tick(S.IMPACT)},
            LAND = ${tick(S.LAND)}, WEB2 = ${tick(S.LAND + WEB[1])}, WEB3 = ${tick(S.LAND + WEB[2])}, BOOM = ${tick(S.LAND + BOOM)}, BURST = ${tick(S.LAND + BOOM + CUT)};
    // Where the target stood at the start, and where it comes to rest on its back (the web's centre).
    public static final double TARGET_Z = ${TZ}, CHEST_X = ${f(S.chest.x)}, CHEST_Z = ${f(S.chest.z)};
    // Sukuna's feet.
    public static final double[][] SUKUNA = {
            ${suk.join(',\n            ')}};
    // The target's centre (0.75 over its feet standing; 0.16 lying on its back).
    public static final double[][] TARGET = {
            ${tgt.join(',\n            ')}};

    private BTrack() {
    }
}
`;
fs.writeFileSync(javaOut, java);
console.log('track', N, 'ticks; HIT', tick(S.HIT), 'CATCH', tick(S.MEET), 'REL', tick(S.REL), 'IMPACT', tick(S.IMPACT), 'LAND', tick(S.LAND), 'BURST', tick(S.LAND + BOOM + CUT));
console.log('apex suk', Math.max(...suk.map(r => +r.split(',')[1])), 'chest', S.chest);
