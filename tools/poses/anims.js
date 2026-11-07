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
};
