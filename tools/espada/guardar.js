// Bakes Kokushibo putting his sword away (espada.guardar_kokushibo): a flick of the blade (chiburi), the hand brings it
// across to the sheath on the left hip with the blade pointing back along it, the left hand at the mouth, then the
// blade slides in and the arms drop. The sword itself is drawn by client/outfit/KokushiboSheathLayer (in the hand at
// first, then sliding along the sheath); this only moves the body and turns the wrist.
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

// Blade pointing back along the sheath (KokushiboSheathLayer: tip toward (0.12, 0.3, 0.95) in model space).
const BACK = { yaw: -173, pitch: -17 };
const keys = [
  { t: 0, arm: { yaw: 0, pitch: -72 }, blade: { yaw: 0, pitch: 11 } },
  // chiburi: the blade flicked out and down to the right
  { t: 3, twist: 14, lean: 4, arm: { yaw: 50, pitch: -52 }, blade: { yaw: 75, pitch: -42 }, head: { yaw: -8 }, ease: 'snap' },
  { t: 5, twist: 16, lean: 4, arm: { yaw: 52, pitch: -55 }, blade: { yaw: 78, pitch: -45 }, head: { yaw: -8 }, ease: 'linear' },
  // across to the left hip, blade turned back along the sheath, left hand on its mouth
  { t: 9, twist: -24, lean: 8, drop: 0.6, arm: { yaw: -38, pitch: -48 }, blade: BACK, larm: { yaw: -12, pitch: -72 },
    head: { yaw: 14, pitch: -12 }, ease: 'inout' },
  // the slide: the hand follows the grip to the mouth
  { t: 16, twist: -16, lean: 6, drop: 0.6, arm: { yaw: -22, pitch: -74 }, blade: BACK, larm: { yaw: -14, pitch: -76 },
    head: { yaw: 10, pitch: -10 }, ease: 'in' },
  { t: 18, twist: -16, lean: 5, drop: 0.5, arm: { yaw: -22, pitch: -75 }, blade: BACK, larm: { yaw: -14, pitch: -77 },
    head: { yaw: 8, pitch: -6 }, ease: 'linear' },
  // hands down
  { t: 24, arm: { yaw: 0, pitch: -90 }, blade: { yaw: 0, pitch: -60 }, ease: 'inout' },
];
const out = path.join(__dirname, '../../src/main/resources/assets/battlegroundsmod/animations/espada.guardar_kokushibo.json');
fs.writeFileSync(out, JSON.stringify(Rig.bake(keys, 0.5)));
console.log('espada.guardar_kokushibo');
