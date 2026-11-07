// Fist moves, in the same high level terms as the sword moves (../espada/rig.js, see ../espada/anims.js for the
// meaning of each field). Made following the pose guide from the emote study (Stark, 2026-11-07): wind up the other
// way first, hold the peak a moment, strike in 2-3 ticks, overshoot, settle; the whole body moves (legs, hips,
// chest, then the arm last) and the head keeps on the target.
// The arms are turned with the chest: arm and l yaw are counted from where the chest faces.
// Fields: twist (+ = right shoulder back), lean (+ forward), drop (px down), fwd (px forward), arm {yaw, pitch}
// = where the right arm points (yaw + right, pitch + up, -90 = hanging), l = left arm (pitch -90 = forward,
// + = back), rl/ll = legs (pitch + = forward, out = to the side), head {yaw, pitch}.
const REST = { arm: { yaw: 0, pitch: -90 } };

// Coiled: low and wide, right shoulder far back, right fist pulled behind him, left hand out towards the target.
const coil = { twist: 52, lean: 10, drop: 1.6, arm: { yaw: 108, pitch: -34 }, l: { yaw: 46, pitch: -78 },
  ll: { pitch: 30, out: 4 }, rl: { pitch: -26, out: 4 }, head: { yaw: 0, pitch: 6 } };
// Charged all the way: coiled tighter and lower.
const coilFull = { ...coil, twist: 60, lean: 12, drop: 2, arm: { yaw: 112, pitch: -28 }, ll: { pitch: 34, out: 5 }, rl: { pitch: -30, out: 5 } };
// The hit: chest swung round to the left, right arm straight out at chest height, left fist back at the hip,
// stepping in on the left leg, the right leg pushing off behind.
const hit = { twist: -40, lean: 24, drop: 2.2, fwd: 3, arm: { yaw: 38, pitch: 4 }, l: { yaw: -10, pitch: 35 },
  ll: { pitch: 38, out: 3 }, rl: { pitch: -34, out: 3 }, head: { yaw: 0, pitch: 4 } };

// Guard: fists up in front (no elbows in Minecraft, so the arms point forward and down), chest a bit turned, knees
// soft. Every combo hit starts and ends here, so hits chain without the body snapping back to standing in between.
const guard = { twist: 18, lean: 6, drop: 0.8, arm: { yaw: -22, pitch: -32 }, l: { yaw: 6, pitch: -62 },
  ll: { pitch: 16, out: 2 }, rl: { pitch: -12, out: 2 }, head: { yaw: 0, pitch: 3 } };
// Barrage: right and left straight punches, the chest swinging with each.
const bR = { twist: -26, lean: 14, drop: 1.4, fwd: 1, arm: { yaw: 22, pitch: 2 }, l: { yaw: -16, pitch: -40 },
  ll: { pitch: 26, out: 3 }, rl: { pitch: -22, out: 3 }, head: { yaw: 0, pitch: 3 } };
const bL = { twist: 26, lean: 14, drop: 1.4, fwd: 1, arm: { yaw: -40, pitch: -38 }, l: { yaw: 24, pitch: -94 },
  ll: { pitch: 26, out: 3 }, rl: { pitch: -22, out: 3 }, head: { yaw: 0, pitch: 3 } };
// Dash: low lunge, chest forward, right fist cocked back, left hand reaching for the target, long stride.
const run = { lean: 32, drop: 2.2, twist: 22, arm: { yaw: 150, pitch: -48 }, l: { yaw: 18, pitch: -72 },
  ll: { pitch: 38, out: 2 }, rl: { pitch: -36, out: 2 }, head: { yaw: 0, pitch: -14 } };

function barrage(ticks) {
  const keys = [{ t: 0, ...guard }, { t: 1, ...bR, ease: 'snap' }];
  for (let t = 3, i = 1; t <= ticks; t += 2, i++) keys.push({ t, ...(i % 2 ? bL : bR), ease: 'snap' });
  return keys;
}

module.exports = {
  // The Forte's charge: a quick dip the other way (anticipation), into the coil in 8 ticks (the server's wind-up),
  // then coiling tighter for as long as the key is held (the server cuts it off, 38 ticks at most).
  forte_carga: [
    { t: 0, ...REST },
    { t: 2, twist: -8, lean: -4, drop: 0.5, arm: { yaw: -10, pitch: -80 }, l: { pitch: -10 }, head: { pitch: -2 }, ease: 'out' },
    { t: 8, ...coil, ease: 'out' },
    { t: 40, ...coilFull, ease: 'inout' },
  ],
  // The release: hangs one tick at the peak, then fires in 2 ticks (the hit lands at tick 3), goes a bit past,
  // settles, holds the pose a moment and goes back to standing.
  forte_golpe: [
    { t: 0, ...coilFull },
    { t: 1, ...coilFull, twist: 64, arm: { yaw: 116, pitch: -26 }, ease: 'out' },
    { t: 3, ...hit, ease: 'snap' },
    { t: 5, ...hit, twist: -48, lean: 28, fwd: 4, arm: { yaw: 46, pitch: 2 }, ease: 'out' },
    { t: 9, ...hit, ease: 'inout' },
    { t: 13, ...hit, twist: -36, lean: 20, ease: 'linear' },
    { t: 22, ...REST, ease: 'inout' },
  ],
  // Combo (padrao.combo.json): the hit lands at the step's atrasoTicks (1, 2, 2, 2, 3), from the guard and back to it.
  // 1 Jab: the left hand snaps out (contact at 1), a bit past, back.
  combo1: [
    { t: 0, ...guard },
    { t: 1, ...guard, twist: 34, lean: 9, fwd: 1, l: { yaw: 32, pitch: -94 }, arm: { yaw: -30, pitch: -40 }, ease: 'snap' },
    { t: 3, ...guard, twist: 38, lean: 10, fwd: 1.5, l: { yaw: 36, pitch: -96 }, arm: { yaw: -30, pitch: -40 }, ease: 'out' },
    { t: 7, ...guard, ease: 'inout' },
  ],
  // 2 Direto: right shoulder back first, then the right fist straight out, the chest swinging round (contact at 2).
  combo2: [
    { t: 0, ...guard },
    { t: 1, ...guard, twist: 34, drop: 1.4, arm: { yaw: 40, pitch: -52 }, ease: 'out' },
    { t: 2, ...guard, twist: -30, lean: 14, drop: 1.4, fwd: 2, arm: { yaw: 30, pitch: 3 }, l: { yaw: -10, pitch: -30 },
      ll: { pitch: 26, out: 3 }, rl: { pitch: -22, out: 3 }, ease: 'snap' },
    { t: 4, ...guard, twist: -36, lean: 16, drop: 1.4, fwd: 2.5, arm: { yaw: 36, pitch: 2 }, l: { yaw: -10, pitch: -30 },
      ll: { pitch: 26, out: 3 }, rl: { pitch: -22, out: 3 }, ease: 'out' },
    { t: 9, ...guard, ease: 'inout' },
  ],
  // 3 Gancho: dips down and loads the left hand low, then rises with the uppercut (contact at 2), head up after it.
  combo3: [
    { t: 0, ...guard },
    { t: 1, ...guard, twist: 6, lean: 16, drop: 2.6, l: { yaw: 10, pitch: -18 }, ease: 'out' },
    { t: 2, ...guard, twist: 34, lean: -4, drop: 0, l: { yaw: 24, pitch: -146 }, arm: { yaw: -20, pitch: -50 },
      ll: { pitch: 14, out: 2 }, rl: { pitch: -16, out: 2 }, head: { yaw: 0, pitch: -12 }, ease: 'snap' },
    { t: 4, ...guard, twist: 38, lean: -8, drop: -0.5, l: { yaw: 26, pitch: -154 }, arm: { yaw: -20, pitch: -50 },
      ll: { pitch: 12, out: 2 }, rl: { pitch: -14, out: 2 }, head: { yaw: 0, pitch: -16 }, ease: 'out' },
    { t: 10, ...guard, ease: 'inout' },
  ],
  // 4 Joelhada: grabs high with both hands and pulls down while the right knee drives up (contact at 2).
  combo4: [
    { t: 0, ...guard },
    { t: 1, ...guard, twist: 0, lean: -6, drop: 0.4, arm: { yaw: 6, pitch: 12 }, l: { yaw: -6, pitch: -102 }, rl: { pitch: -18, out: 1 }, ease: 'out' },
    { t: 2, ...guard, twist: -6, lean: 14, drop: -0.5, fwd: 1, arm: { yaw: 8, pitch: -26 }, l: { yaw: -8, pitch: -64 },
      rl: { pitch: 92, out: 2 }, ll: { pitch: -6, out: 1 }, head: { yaw: 0, pitch: 10 }, ease: 'snap' },
    { t: 4, ...guard, twist: -8, lean: 16, drop: -0.5, fwd: 1, arm: { yaw: 8, pitch: -32 }, l: { yaw: -8, pitch: -58 },
      rl: { pitch: 98, out: 2 }, ll: { pitch: -6, out: 1 }, head: { yaw: 0, pitch: 12 }, ease: 'out' },
    { t: 10, ...guard, ease: 'inout' },
  ],
  // 5 Chute final: winds the other way, then a roundhouse with the right leg, leaning back, arms flung out for
  // balance (contact at 3), holds the end and comes back.
  combo5: [
    { t: 0, ...guard },
    { t: 2, ...guard, twist: -28, lean: 8, drop: 1.6, arm: { yaw: -40, pitch: -50 }, l: { yaw: 30, pitch: -40 }, rl: { pitch: -24, out: 4 }, ease: 'out' },
    { t: 3, twist: 44, lean: -20, drop: 0.4, arm: { yaw: 70, pitch: -10 }, l: { yaw: 40, pitch: -70 },
      rl: { pitch: 84, out: 26 }, ll: { pitch: -8, out: 2 }, head: { yaw: 0, pitch: -4 }, ease: 'snap' },
    { t: 5, twist: 56, lean: -24, drop: 0.4, arm: { yaw: 80, pitch: -6 }, l: { yaw: 44, pitch: -74 },
      rl: { pitch: 92, out: 30 }, ll: { pitch: -8, out: 2 }, head: { yaw: 0, pitch: -4 }, ease: 'out' },
    { t: 9, twist: 50, lean: -20, drop: 0.4, arm: { yaw: 76, pitch: -8 }, l: { yaw: 40, pitch: -70 },
      rl: { pitch: 86, out: 28 }, ll: { pitch: -8, out: 2 }, head: { yaw: 0, pitch: -4 }, ease: 'linear' },
    { t: 17, ...guard, ease: 'inout' },
  ],
  // Barragem: a punch every 2 ticks, right and left, for the longest hold (60 ticks); the server switches to the
  // finisher when it ends.
  barragem: barrage(64),
  // The finisher: both shoulders back, then a big right straight that steps in (contact at 1), held, back to standing.
  barragem_final: [
    { t: 0, ...bL },
    { t: 1, ...hit, twist: -46, ease: 'snap' },
    { t: 3, ...hit, twist: -52, lean: 28, fwd: 4, arm: { yaw: 52, pitch: 2 }, ease: 'out' },
    { t: 10, ...hit, twist: -40, lean: 22, ease: 'linear' },
    { t: 18, ...REST, ease: 'inout' },
  ],
  // Dash Attack slide: into the lunge in 2 ticks, then a slight bob for as long as it slides (14 ticks at most).
  dash: [
    { t: 0, ...REST },
    { t: 2, ...run, ease: 'out' },
    { t: 8, ...run, drop: 2.8, lean: 34, ease: 'inout' },
    { t: 14, ...run, ease: 'inout' },
  ],
};
