// Bakes Kokushibo's sheath moves. Putting his sword away (espada.guardar_kokushibo): a flick of the blade (chiburi), the hand brings it
// across to the sheath on the left hip with the blade pointing back along it, the left hand at the mouth, then the
// blade slides in and the arms drop. The sword itself is drawn by client/outfit/KokushiboSheathLayer (in the hand at
// in the hand all the way, the hand bringing it along the sheath); this only moves the body and turns the wrist.
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
src = src.slice(0, from) + `mul(new THREE.Matrix4().makeTranslation(1.5 / 16, 3 / 16, 0.5 + 1.5 / 16));
    mul(new THREE.Matrix4().makeRotationZ(-0.2));
    mul(new THREE.Matrix4().makeRotationZ(-Math.PI / 4));
    mul(new THREE.Matrix4().makeRotationY(Math.PI));
    mul(new THREE.Matrix4().makeScale(1.15 / 16, 1.15 / 16, 1.15 / 16));
    mul(new THREE.Matrix4().makeTranslation(0, 3.6, 0));
    ` + src.slice(to);
const mod = { exports: {} };
new Function('module', 'require', 'globalThis', src)(mod, require, globalThis);
const Rig = mod.exports;

// The right hand holding the sword IN the sheath, the guard out pixels from its mouth: fitted so that the sword in the
// hand lies along the sheath (KokushiboSheathLayer.GUARD_AT / TURN) within about 2 px at out 0. The hand never lets
// go: the blade goes in and comes out because the hand moves it along the sheath.
const FIT = {
  0: [-63.8, -29.4, 108.4, -168.9, -18.2, 254.7],
  4: [-47.6, -17.8, 108.4, -168.2, -19.1, 248.8],
  8: [-33.1, -4.1, 108.4, -164.5, -21.4, 248.8],
};
const inSheath = (out) => {
  const f = FIT[out];
  return { twist: 0, lean: 0, drop: 1, arm: { yaw: f[0], pitch: f[1], roll: f[2] }, blade: { yaw: f[3], pitch: f[4], edge: f[5] } };
};
const sheathHand = { yaw: -16, pitch: -78 };
const keys = [
  { t: 0, arm: { yaw: 0, pitch: -72 }, blade: { yaw: 0, pitch: 11 } },
  // chiburi: the blade flicked out and down to the right
  { t: 3, twist: 14, lean: 4, arm: { yaw: 50, pitch: -52 }, blade: { yaw: 75, pitch: -42 }, head: { yaw: -8 }, ease: 'snap' },
  { t: 5, twist: 16, lean: 4, arm: { yaw: 52, pitch: -55 }, blade: { yaw: 78, pitch: -45 }, head: { yaw: -8 }, ease: 'linear' },
  // across to the left hip: the tip into the sheath's mouth, the left hand holding it
  { t: 9, ...inSheath(8), larm: sheathHand, head: { yaw: 14, pitch: -12 }, ease: 'inout' },
  // the slide: the hand pushes the blade home along the sheath
  { t: 12.5, ...inSheath(4), larm: sheathHand, head: { yaw: 12, pitch: -10 }, ease: 'linear' },
  { t: 16, ...inSheath(0), larm: sheathHand, head: { yaw: 10, pitch: -10 }, ease: 'linear' },
  { t: 18, ...inSheath(0), larm: sheathHand, head: { yaw: 8, pitch: -6 }, ease: 'linear' },
  // hands down
  { t: 24, arm: { yaw: 0, pitch: -90 }, blade: { yaw: 0, pitch: -60 }, ease: 'inout' },
];
const dir = path.join(__dirname, '../../src/main/resources/assets/battlegroundsmod/animations');
fs.writeFileSync(path.join(dir, 'espada.guardar_kokushibo.json'), JSON.stringify(Rig.bake(keys, 0.5)));
console.log('espada.guardar_kokushibo');

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
