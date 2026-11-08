// Fist moves (base combat, bare hands), anime style, from the study of 782 fighting animations of anime mods
// (/mnt/project-files/estudo-animacao.md, Stark 2026-11-08). The earlier, smoother version is antigas/anims_suave.js.
// Rules used here, pose to pose instead of smooth motion:
//   - tick 0 is already the wind-up (the move cuts into it, no easing out of the guard);
//   - the wind-up is held a moment on the heavy hits (tension);
//   - the strike takes 1 tick, linear, with the hips and the limb together (120-180 degrees summed);
//   - smear: on the strike tick the striking limb stretches along its length (sc, y = along the arm/leg) and is back to
//     normal on the next tick;
//   - one tick past the target, then the impact pose is HELD 3-6 ticks (hitstop, VFX), and a quick linear way back.
// Same rig and field meaning as before (../espada/rig.js): twist (+ = right shoulder back), lean (+ forward), drop (px
// down), fwd (px forward), arm {yaw, pitch} = right arm (yaw + right, pitch + up, -90 = hanging), l = left arm (pitch
// -90 = forward, + = back), rl/ll = legs (pitch + = forward, out = to the side), head {yaw, pitch}, sc = part scale.
// The contact ticks match the server (padrao.combo.json atrasoTicks 1, 2, 2, 2, 3; Forte at 3 after the release;
// Barragem a punch every 2 ticks; finisher at 1).
const REST = { arm: { yaw: 0, pitch: -90 } };
const L = 'linear';

// Guard: fists up, chest turned a bit, knees soft, lower than before. Every combo hit starts and ends here.
const guard = { twist: 20, lean: 8, drop: 1.2, arm: { yaw: -22, pitch: -30 }, l: { yaw: 6, pitch: -64 },
  ll: { pitch: 18, out: 2.5 }, rl: { pitch: -14, out: 2.5 }, head: { yaw: 0, pitch: 3 } };

// Coiled for the heavy right: hips wound far back, low and wide, right fist behind him, left hand out at the target.
const coil = { twist: 60, lean: 8, drop: 2.2, arm: { yaw: 112, pitch: -32 }, l: { yaw: 50, pitch: -80 },
  ll: { pitch: 34, out: 4.5 }, rl: { pitch: -30, out: 4.5 }, head: { yaw: 0, pitch: 6 } };
const coilFull = { ...coil, twist: 68, lean: 12, drop: 2.8, arm: { yaw: 118, pitch: -26 }, ll: { pitch: 38, out: 5 }, rl: { pitch: -34, out: 5 } };
// The heavy right landing: hips swung all the way round, stepping in, the back leg pushing off.
const hit = { twist: -56, lean: 30, drop: 2.6, fwd: 4, arm: { yaw: 50, pitch: 2 }, l: { yaw: -12, pitch: 40 },
  ll: { pitch: 44, out: 3 }, rl: { pitch: -42, out: 3 }, head: { yaw: 0, pitch: 4 } };
// Smear of a straight punch: the arm stretched and a bit thicker for one tick.
const SMEAR_R = { rightArm: [1.18, 1.5, 1.18] };
const SMEAR_L = { leftArm: [1.18, 1.5, 1.18] };
const BIG_R = { rightArm: [1.25, 1.75, 1.25] };

// Barrage: four punches that repeat (right high, left middle, right low, left high), the hips swinging with each and
// the head shaking, like the anime rushes (each punch stretched on its tick).
const rushR = (pitch, tw) => ({ twist: -tw, lean: 16, drop: 1.8, fwd: 1.5, arm: { yaw: tw - 2, pitch }, l: { yaw: -14, pitch: -36 },
  ll: { pitch: 28, out: 3.5 }, rl: { pitch: -24, out: 3.5 } });
const rushL = (pitch, tw) => ({ twist: tw, lean: 16, drop: 1.8, fwd: 1.5, arm: { yaw: -40, pitch: -40 }, l: { yaw: tw - 2, pitch: -90 - pitch },
  ll: { pitch: 28, out: 3.5 }, rl: { pitch: -24, out: 3.5 } });
const RUSH = [
  { ...rushR(8, 34), head: { yaw: 4, pitch: 0 }, sc: SMEAR_R },
  { ...rushL(0, 30), head: { yaw: -5, pitch: 4 }, sc: SMEAR_L },
  { ...rushR(-14, 38), head: { yaw: 3, pitch: 7 }, sc: SMEAR_R },
  { ...rushL(10, 32), head: { yaw: -4, pitch: 1 }, sc: SMEAR_L },
];
function barrage(ticks) {
  const keys = [{ t: 0, ...guard, twist: 34 }];
  for (let t = 1, i = 0; t <= ticks; t += 2, i++) {
    const p = RUSH[i % 4];
    keys.push({ t, ...p, ease: L });
    // the next tick: arm back at normal size, a hair past (the recoil), ready for the other hand
    keys.push({ t: t + 1, ...p, twist: p.twist * 0.8, sc: undefined, ease: L });
  }
  return keys;
}

// Dash: a low lunge, chest far forward, right fist cocked back, left hand reaching for the target, long stride.
const run = { lean: 36, drop: 2.6, twist: 24, arm: { yaw: 150, pitch: -46 }, l: { yaw: 20, pitch: -74 },
  ll: { pitch: 44, out: 2 }, rl: { pitch: -42, out: 2 }, head: { yaw: 0, pitch: -16 } };

// Forte charge tremor: alternating ticks a little each way, growing the longer it is held.
function tremor(from, to, base, amp0, amp1) {
  const keys = [];
  for (let t = from, i = 0; t <= to; t++, i++) {
    const a = amp0 + (amp1 - amp0) * ((t - from) / Math.max(1, to - from));
    const s = i % 2 ? 1 : -1;
    keys.push({ t, ...base, twist: base.twist + s * a, drop: base.drop + (i % 2) * a * 0.15, arm: { ...base.arm, yaw: base.arm.yaw - s * a * 0.6 }, ease: L });
  }
  return keys;
}

module.exports = {
  // Forte charge: cuts into the coil in 2 ticks, settles, and from tick 8 (the server's wind-up) it trembles harder
  // and harder while the key is held (the server cuts it off, 38 ticks at most).
  forte_carga: [
    { t: 0, ...REST },
    { t: 2, ...coil, twist: 64, ease: L },
    { t: 3, ...coil, ease: L },
    { t: 8, ...coil, twist: 62, drop: 2.4, ease: 'inout' },
    ...tremor(9, 40, coilFull, 0.8, 2.6),
  ],
  // Forte release: holds coiled 2 ticks (tension), strikes on tick 3 in one tick with a long smear, one tick past,
  // holds the impact 6 ticks, settles and goes back.
  forte_golpe: [
    { t: 0, ...coilFull },
    { t: 2, ...coilFull, twist: 72, arm: { yaw: 122, pitch: -24 }, ease: L },
    { t: 3, ...hit, sc: BIG_R, ease: L },
    { t: 4, ...hit, twist: -64, lean: 34, fwd: 5, arm: { yaw: 58, pitch: 4 }, ease: L },
    { t: 10, ...hit, twist: -62, lean: 33, fwd: 5, arm: { yaw: 56, pitch: 3 }, ease: L },
    { t: 12, ...hit, ease: 'out' },
    { t: 18, ...REST, ease: L },
  ],
  // 1 Jab (contact at 1): already loaded at tick 0, the left hand shoots out in one tick, past, held 2 ticks, back.
  combo1: [
    { t: 0, ...guard, twist: 8, drop: 1.6, l: { yaw: 0, pitch: -50 } },
    { t: 1, ...guard, twist: 40, lean: 12, fwd: 1.5, l: { yaw: 38, pitch: -94 }, arm: { yaw: -30, pitch: -40 }, sc: SMEAR_L, ease: L },
    { t: 2, ...guard, twist: 46, lean: 13, fwd: 2, l: { yaw: 42, pitch: -96 }, arm: { yaw: -30, pitch: -40 }, ease: L },
    { t: 4, ...guard, twist: 44, lean: 12, fwd: 2, l: { yaw: 40, pitch: -95 }, arm: { yaw: -30, pitch: -40 }, ease: L },
    { t: 7, ...guard, ease: L },
  ],
  // 2 Direto (contact at 2): right shoulder wound back at tick 0, held 1 tick, the right fist straight out in one tick
  // (hips 90 degrees round), past, held, back.
  combo2: [
    { t: 0, ...guard, twist: 46, drop: 1.8, arm: { yaw: 50, pitch: -50 } },
    { t: 1, ...guard, twist: 50, drop: 2, arm: { yaw: 54, pitch: -48 }, ease: L },
    { t: 2, ...guard, twist: -40, lean: 18, drop: 1.8, fwd: 2.5, arm: { yaw: 38, pitch: 3 }, l: { yaw: -10, pitch: -26 },
      ll: { pitch: 30, out: 3 }, rl: { pitch: -28, out: 3 }, sc: SMEAR_R, ease: L },
    { t: 3, ...guard, twist: -48, lean: 21, drop: 1.8, fwd: 3, arm: { yaw: 44, pitch: 2 }, l: { yaw: -10, pitch: -26 },
      ll: { pitch: 30, out: 3 }, rl: { pitch: -28, out: 3 }, ease: L },
    { t: 6, ...guard, twist: -45, lean: 20, drop: 1.8, fwd: 3, arm: { yaw: 42, pitch: 2 }, l: { yaw: -10, pitch: -26 },
      ll: { pitch: 30, out: 3 }, rl: { pitch: -28, out: 3 }, ease: L },
    { t: 9, ...guard, ease: L },
  ],
  // 3 Gancho (left uppercut, contact at 2): dropped low with the left fist down at tick 0, deeper on tick 1, then up on
  // the toes with the uppercut in one tick, head following it up, held, back.
  combo3: [
    { t: 0, ...guard, twist: 4, lean: 20, drop: 3.2, l: { yaw: 10, pitch: -14 } },
    { t: 1, ...guard, twist: 0, lean: 22, drop: 3.8, l: { yaw: 8, pitch: -8 }, ease: L },
    { t: 2, ...guard, twist: 40, lean: -6, drop: -0.8, l: { yaw: 26, pitch: -158 }, arm: { yaw: -20, pitch: -50 },
      ll: { pitch: 12, out: 2 }, rl: { pitch: -18, out: 2 }, head: { yaw: 0, pitch: -14 }, sc: SMEAR_L, ease: L },
    { t: 3, ...guard, twist: 46, lean: -10, drop: -1.2, l: { yaw: 28, pitch: -166 }, arm: { yaw: -20, pitch: -50 },
      ll: { pitch: 10, out: 2 }, rl: { pitch: -16, out: 2 }, head: { yaw: 0, pitch: -18 }, ease: L },
    { t: 6, ...guard, twist: 44, lean: -9, drop: -1, l: { yaw: 27, pitch: -162 }, arm: { yaw: -20, pitch: -50 },
      ll: { pitch: 10, out: 2 }, rl: { pitch: -16, out: 2 }, head: { yaw: 0, pitch: -16 }, ease: L },
    { t: 10, ...guard, ease: L },
  ],
  // 4 Joelhada (contact at 2): both hands already up grabbing at tick 0, leaning back with the right leg back, then
  // pulls down while the knee drives up in one tick (leg stretched for that tick), held, back.
  combo4: [
    { t: 0, ...guard, twist: 0, lean: -10, drop: 0.6, arm: { yaw: 6, pitch: 16 }, l: { yaw: -6, pitch: -106 }, rl: { pitch: -28, out: 1 } },
    { t: 1, ...guard, twist: 0, lean: -12, drop: 0.6, arm: { yaw: 6, pitch: 18 }, l: { yaw: -6, pitch: -108 }, rl: { pitch: -32, out: 1 }, ease: L },
    { t: 2, ...guard, twist: -8, lean: 20, drop: -0.6, fwd: 1.5, arm: { yaw: 8, pitch: -30 }, l: { yaw: -8, pitch: -60 },
      rl: { pitch: 100, out: 2 }, ll: { pitch: -8, out: 1 }, head: { yaw: 0, pitch: 12 }, sc: { rightLeg: [1.15, 1.35, 1.15] }, ease: L },
    { t: 3, ...guard, twist: -10, lean: 23, drop: -0.6, fwd: 1.5, arm: { yaw: 8, pitch: -36 }, l: { yaw: -8, pitch: -54 },
      rl: { pitch: 108, out: 2 }, ll: { pitch: -8, out: 1 }, head: { yaw: 0, pitch: 14 }, ease: L },
    { t: 6, ...guard, twist: -9, lean: 22, drop: -0.6, fwd: 1.5, arm: { yaw: 8, pitch: -34 }, l: { yaw: -8, pitch: -56 },
      rl: { pitch: 104, out: 2 }, ll: { pitch: -8, out: 1 }, head: { yaw: 0, pitch: 13 }, ease: L },
    { t: 10, ...guard, ease: L },
  ],
  // 5 Soco final (contact at 3): cuts into the coil, holds it 2 ticks winding tighter, the heavy right in one tick with
  // a long smear, past, held 5 ticks, settles, back to the guard.
  combo5: [
    { t: 0, ...coil },
    { t: 2, ...coil, twist: 66, drop: 2.6, ease: L },
    { t: 3, ...hit, sc: BIG_R, ease: L },
    { t: 4, ...hit, twist: -64, lean: 34, fwd: 4.5, arm: { yaw: 58, pitch: 4 }, ease: L },
    { t: 9, ...hit, twist: -61, lean: 32, fwd: 4.5, arm: { yaw: 55, pitch: 3 }, ease: L },
    { t: 11, ...hit, ease: 'out' },
    { t: 17, ...guard, ease: L },
  ],
  // Barragem: a punch every 2 ticks for the longest hold (64 ticks); the server switches to the finisher at the end.
  barragem: barrage(64),
  // Finisher (contact at 1): coiled at tick 0, the big right in one tick with a long smear, past, held 7, back.
  barragem_final: [
    { t: 0, ...coilFull },
    { t: 1, ...hit, sc: BIG_R, ease: L },
    { t: 2, ...hit, twist: -64, lean: 34, fwd: 5, arm: { yaw: 58, pitch: 4 }, ease: L },
    { t: 9, ...hit, twist: -61, lean: 32, fwd: 5, arm: { yaw: 55, pitch: 3 }, ease: L },
    { t: 11, ...hit, ease: 'out' },
    { t: 18, ...REST, ease: L },
  ],
  // Dash Attack slide: into the lunge in one tick, one tick past it (leaning further), then held with a small bob for
  // as long as it slides (14 ticks at most).
  dash: [
    { t: 0, ...REST },
    { t: 1, ...run, lean: 42, drop: 3, ease: L },
    { t: 2, ...run, ease: L },
    { t: 8, ...run, drop: 3, lean: 38, ease: 'inout' },
    { t: 14, ...run, ease: 'inout' },
  ],
};
