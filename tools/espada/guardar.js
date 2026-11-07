// Bakes Kokushibo's sheath moves. Putting his sword away (espada.guardar_kokushibo): he stands still holding it out
// while the sword, living flesh, leaves the hand by itself (client/outfit/FleshSheathe), then the arm drops.
// The quick draw (espada.sacar_kokushibo), the combo's first click while the sword rests in the sheath: the right hand
// on the grip at the hip pulls it forward along the sheath, then it comes out cutting across and up to the right (the
// cut lands on tick 3) and ends where the combo's third cut starts (SwordArts skips to it).
//   cd tools/espada && npm i three@0.160.0 && node guardar.js
const fs = require('fs');
const path = require('path');
// rig.js, with the held sword switched from Aizen's to Kokushibo's (KokushiboSwordRenderer.toSword, third person).
let src = fs.readFileSync(path.join(__dirname, 'rig.js'), 'utf8');
const from = src.indexOf('mul(new THREE.Matrix4().makeTranslation(3 / 16, 3 / 16, 0.5));');
const to = src.indexOf('return m;', from);
src = src.slice(0, from) + `mul(new THREE.Matrix4().makeTranslation(2.825 / 16, 3.084 / 16, 0.5));
    mul(new THREE.Matrix4().makeRotationZ(-0.2));
    mul(new THREE.Matrix4().makeRotationZ(-Math.PI / 4));
    mul(new THREE.Matrix4().makeRotationY(Math.PI));
    mul(new THREE.Matrix4().makeScale(1.15 / 16, 1.15 / 16, 1.15 / 16));
    mul(new THREE.Matrix4().makeTranslation(0, 3.6, 0));
    ` + src.slice(to);
// The wrist turns about the grip (Stark, 2026-11-07: about the item's pivot the grip slid out of the fist), as in
// KokushiboSwordRenderer.GRIP_PIVOT.
src = src.replace('return handFromArm().multiply(wristMatrix(w || [0, 0, 0])).multiply(swordFromHand());',
  `const g = new THREE.Vector3(0, -0.125, 0.0875);
    return handFromArm().multiply(new THREE.Matrix4().makeTranslation(g.x, g.y, g.z)).multiply(wristMatrix(w || [0, 0, 0]))
      .multiply(new THREE.Matrix4().makeTranslation(-g.x, -g.y, -g.z)).multiply(swordFromHand());`);
const mod = { exports: {} };
new Function('module', 'require', 'globalThis', src)(mod, require, globalThis);
const Rig = mod.exports;

// The right hand holding the sword IN the sheath, the guard out pixels from its mouth: fitted so that the sword in the
// hand lies along the sheath (KokushiboSheathLayer.GUARD_AT / TURN) as near as the arm reaches (4 to 8 px off); the
// left hand brings the sheath the rest of the way (KokushiboSheathLayer.SHEATH_OUT). The hand never lets go: the blade
// goes in and comes out because the hand moves it along the sheath.
const FIT = {
  0: [-51.9, -42.2, 151.8, -164.4, -21, 270.7],
  4: [-32.2, -27.2, 151.8, -163.2, -23.3, 281.2],
  8: [-19.8, -14.2, 151.8, -157.8, -26.8, 292.6],
};
const inSheath = (out) => {
  const f = FIT[out];
  return { twist: 0, lean: 0, drop: 1.8, arm: { yaw: f[0], pitch: f[1], roll: f[2] }, blade: { yaw: f[3], pitch: f[4], edge: f[5] } };
};
// Putting it away (Stark, 2026-11-07: the sword is living flesh and goes back by itself, client/outfit/FleshSheathe):
// the body stands still with the arm out holding it until the hand lets go (tick 40, FleshSheathe.LET_GO), then the
// arm drops. The sword itself is drawn by KokushiboSheathLayer.
const keys = [
  { t: 0, arm: { yaw: 0, pitch: -72 }, blade: { yaw: 0, pitch: 11 } },
  { t: 40, arm: { yaw: 0, pitch: -72 }, blade: { yaw: 0, pitch: 11 }, ease: 'linear' },
  { t: 46, arm: { yaw: 0, pitch: -90 }, blade: { yaw: 0, pitch: -60 }, ease: 'inout' },
];
const dir = path.join(__dirname, '../../src/main/resources/assets/battlegroundsmod/animations');
fs.writeFileSync(path.join(dir, 'espada.guardar_kokushibo.json'), JSON.stringify(Rig.bake(keys, 0.5)));
console.log('espada.guardar_kokushibo');

// The left hand holding the sheath for the quick draw.
const sheathHand = { yaw: -16, pitch: -78 };

// The combo's third cut starts here (anims.js C.c2end).
const C2END = { twist: 30, lean: 2, drop: 1.2, arm: { yaw: 55, pitch: 45 }, blade: { yaw: 80, pitch: 62 }, l: { yaw: 25, pitch: -50 },
  rl: { pitch: 22 }, ll: { pitch: -16 }, head: { yaw: -30 } };
const draw = [
  // the hand on the grip, the sword still in the sheath, crouched a little
  { t: 0, ...inSheath(0), larm: sheathHand, rl: { pitch: 18 }, ll: { pitch: -14 }, head: { yaw: 6, pitch: -4 } },
  // pulled forward along the sheath
  { t: 1, ...inSheath(4), larm: sheathHand, rl: { pitch: 20 }, ll: { pitch: -16 }, head: { yaw: 3 }, ease: 'in' },
  { t: 2, ...inSheath(8), larm: sheathHand, rl: { pitch: 24 }, ll: { pitch: -18 }, head: { yaw: 0 }, ease: 'linear' },
  // out and across the front, rising (the cut lands now: SwordArts.QUICK_DRAW_DELAY)
  { t: 3, twist: 10, lean: 12, drop: 1.8, arm: { yaw: 18, pitch: 8 }, blade: { yaw: 12, pitch: 18 }, larm: sheathHand,
    rl: { pitch: 26 }, ll: { pitch: -20 }, head: { yaw: -8 }, ease: 'linear' },
  { t: 4.2, ...C2END, lean: 6, drop: 1.6, larm: sheathHand, ease: 'out' },
  { t: 10, ...C2END, ease: 'inout' },
];
fs.writeFileSync(path.join(dir, 'espada.sacar_kokushibo.json'), JSON.stringify(Rig.bake(draw, 0.5)));
console.log('espada.sacar_kokushibo');
