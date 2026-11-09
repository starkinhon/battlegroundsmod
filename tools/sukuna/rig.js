// The previews' rig, turned into what the mod's KeyframeAnimation can play.
// In the 3D previews (forja/sukuna-*) the body's lean and twist turn the WHOLE character about its feet (the group,
// Euler(lean, twist, 0, 'YXZ')), and the limbs, head and chest move with it. Minecraft's model has no hierarchy: head,
// chest, arms and legs are separate boxes, each turning about its own pivot. The old bakes only turned the chest, so a
// lean of 0.5 to 0.8 rad (the sprint, the slam) tilted the chest alone about the neck while the arms, head and legs
// stayed upright and came apart from it (Stark 2026-11-09: "a animação tá toda cagada").
// Here every part is carried by the lean and twist: its pivot is moved round the feet and its own rotation is put
// under the body's, then written back as Minecraft's Z-Y-X angles plus a position channel.
//   const { channels } = require('./rig');  animations = channels(poses)  (poses: [{ ts, p }])
// Pose p: h, ra, la, rl, ll = [x, y, z] degrees (as before), g: { lean, twist (radians), y (blocks, the drop) }.

const DEG = Math.PI / 180;
// Minecraft's pivots in model pixels (y down, forward is -z), as SkillAnimations.standStill leaves them.
const PIVOT = {
  h: [0, 0, 0], body: [0, 0, 0], ra: [-5, 2, 0], la: [5, 2, 0], rl: [-1.9, 12, 0], ll: [1.9, 12, 0],
};
const FEET = [0, 24, 0];
const NAMES = { h: 'Head', body: 'Body', ra: 'Right Arm', la: 'Left Arm', rl: 'Right Leg', ll: 'Left Leg' };

function mul(a, b) {
  const o = [[0, 0, 0], [0, 0, 0], [0, 0, 0]];
  for (let i = 0; i < 3; i++) for (let j = 0; j < 3; j++) o[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
  return o;
}
function rx(a) { const c = Math.cos(a), s = Math.sin(a); return [[1, 0, 0], [0, c, -s], [0, s, c]]; }
function ry(a) { const c = Math.cos(a), s = Math.sin(a); return [[c, 0, s], [0, 1, 0], [-s, 0, c]]; }
function rz(a) { const c = Math.cos(a), s = Math.sin(a); return [[c, -s, 0], [s, c, 0], [0, 0, 1]]; }
function apply(m, v) { return [0, 1, 2].map(i => m[i][0] * v[0] + m[i][1] * v[1] + m[i][2] * v[2]); }
// ModelPart: rotationZYX(z, y, x) = Rz * Ry * Rx.
function part(x, y, z) { return mul(rz(z), mul(ry(y), rx(x))); }
function euler(m) {
  const y = Math.asin(Math.max(-1, Math.min(1, -m[2][0])));
  if (Math.abs(m[2][0]) < 0.99999) return [Math.atan2(m[2][1], m[2][2]), y, Math.atan2(m[1][0], m[0][0])];
  // straight up or down: put it all in x
  return [Math.atan2(-m[1][2], m[1][1]), y, 0];
}
// The same rotation as close as possible to the last one (no 360 spins between two samples).
function near(e, last) {
  if (!last) return e;
  const alt = [e[0] + Math.PI, Math.PI - e[1], e[2] + Math.PI];
  const fix = v => v.map((a, i) => a + 2 * Math.PI * Math.round((last[i] - a) / (2 * Math.PI)));
  const a = fix(e), b = fix(alt);
  const d = v => v.reduce((s, x, i) => s + Math.abs(x - last[i]), 0);
  return d(a) <= d(b) ? a : b;
}

// poses: [{ ts (seconds), p }] -> the "animations" list of a KeyframeAnimation file.
function channels(poses, { legs = true } = {}) {
  const bones = ['h', 'body', 'ra', 'la'].concat(legs ? ['rl', 'll'] : []);
  const rot = {}, pos = {}, last = {};
  for (const b of bones) { rot[b] = []; pos[b] = []; }
  for (const { ts, p } of poses) {
    const g = p.g || {};
    // the previews' group: Euler(lean, twist, 0, 'YXZ') = Ry(twist) * Rx(lean), about the feet
    const G = mul(ry(g.twist || 0), rx(g.lean || 0));
    const drop = -(g.y || 0) * 16;
    for (const b of bones) {
      const r = b === 'body' ? [0, 0, 0] : p[b] || [0, 0, 0];
      const m = mul(G, part(r[0] * DEG, r[1] * DEG, r[2] * DEG));
      const e = near(euler(m), last[b]);
      last[b] = e;
      const pv = PIVOT[b];
      const rel = [pv[0] - FEET[0], pv[1] - FEET[1], pv[2] - FEET[2]];
      const at = apply(G, rel);
      const off = [at[0] + FEET[0] - pv[0], at[1] + FEET[1] - pv[1] + drop, at[2] + FEET[2] - pv[2]];
      // x and y flip into the file's axes (KeyframeAnimation flips them back), for turning and moving
      rot[b].push({ timestamp: ts, target: [-e[0] / DEG, -e[1] / DEG, e[2] / DEG].map(x => +x.toFixed(3)), interpolation: 'linear' });
      pos[b].push({ timestamp: ts, target: [-off[0], -off[1], off[2]].map(x => +x.toFixed(3)), interpolation: 'linear' });
    }
  }
  const out = [];
  for (const b of bones) {
    out.push({ bone: NAMES[b], target: 'rotation', keyframes: rot[b] });
    out.push({ bone: NAMES[b], target: 'position', keyframes: pos[b] });
  }
  return out;
}

module.exports = { channels };
