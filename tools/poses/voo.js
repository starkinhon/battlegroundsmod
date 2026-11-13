// The combat flight's own moves (combat/AirFight, boss/AirCombat; Stark 2026-11-13: "no ar, cada coisinha tem uma
// animação"). Same rig and rules as anims.js (pose to pose: tick 0 already loaded, the strike in one tick with a
// smear, one tick past, the impact held, a quick way back), but nobody stands on anything: the legs hang, tuck and
// kick instead of stepping, and every move ends in the air guard (client/FlightPose takes over from there with the
// stance for each way it moves: ahead, back, to each side, up and down).
// Contact ticks match the server: punches 1, 2, 2, 2 (padrao.combo.json), the dash hit on arrival, the kick from
// behind at 2 (AirFight.strong), the Martelo at 5, a ki ball on tick 1 of each hand, the beam charge 21 ticks.
const L = 'linear';
const SMEAR_R = { rightArm: [1.18, 1.5, 1.18] };
const SMEAR_L = { leftArm: [1.18, 1.5, 1.18] };
const BIG_ARMS = { rightArm: [1.2, 1.6, 1.2], leftArm: [1.2, 1.6, 1.2] };
const KICK_R = { rightLeg: [1.18, 1.45, 1.18] };

// The air guard: fists up by the chin, chest turned a bit, the right knee drawn up, the left leg hanging.
const ag = { twist: 16, lean: 4, drop: 0, arm: { yaw: -22, pitch: -26 }, l: { yaw: 6, pitch: -66 },
  rl: { pitch: 42, out: 3 }, ll: { pitch: -6, out: 3 }, head: { yaw: 0, pitch: 5 } };

module.exports = {
  // 1 Jab (contact 1): the left fist out in one tick, the right knee kicks up with it, held, back.
  soco1: [
    { t: 0, ...ag, twist: 6, l: { yaw: 0, pitch: -50 } },
    { t: 1, ...ag, twist: 42, lean: 10, fwd: 1.5, l: { yaw: 38, pitch: -96 }, arm: { yaw: -30, pitch: -40 },
      rl: { pitch: 30, out: 2 }, ll: { pitch: -20, out: 2 }, sc: SMEAR_L, ease: L },
    { t: 2, ...ag, twist: 48, lean: 12, fwd: 2, l: { yaw: 42, pitch: -98 }, arm: { yaw: -30, pitch: -40 },
      rl: { pitch: 28, out: 2 }, ll: { pitch: -24, out: 2 }, ease: L },
    { t: 4, ...ag, twist: 46, lean: 11, fwd: 2, l: { yaw: 40, pitch: -97 }, arm: { yaw: -30, pitch: -40 },
      rl: { pitch: 28, out: 2 }, ll: { pitch: -24, out: 2 }, ease: L },
    { t: 7, ...ag, ease: L },
  ],
  // 2 Cross (contact 2): the right shoulder wound back, held a tick, the fist straight out with the hips round, the
  // right leg swinging back for the weight (no floor to push from).
  soco2: [
    { t: 0, ...ag, twist: 46, arm: { yaw: 50, pitch: -50 }, rl: { pitch: 48, out: 3 } },
    { t: 1, ...ag, twist: 52, arm: { yaw: 54, pitch: -48 }, rl: { pitch: 52, out: 3 }, ease: L },
    { t: 2, ...ag, twist: -42, lean: 18, fwd: 2.5, arm: { yaw: 36, pitch: 4 }, l: { yaw: -10, pitch: -30 },
      ll: { pitch: 34, out: 3 }, rl: { pitch: -40, out: 3 }, sc: SMEAR_R, ease: L },
    { t: 3, ...ag, twist: -50, lean: 21, fwd: 3, arm: { yaw: 42, pitch: 3 }, l: { yaw: -10, pitch: -28 },
      ll: { pitch: 36, out: 3 }, rl: { pitch: -46, out: 3 }, ease: L },
    { t: 6, ...ag, twist: -47, lean: 20, fwd: 3, arm: { yaw: 40, pitch: 3 }, l: { yaw: -10, pitch: -28 },
      ll: { pitch: 36, out: 3 }, rl: { pitch: -44, out: 3 }, ease: L },
    { t: 9, ...ag, ease: L },
  ],
  // 3 Knee (contact 2): both hands reach out and grab, the right leg loads back, then the knee drives up into the
  // target while the hands pull it down onto it.
  soco3: [
    { t: 0, ...ag, twist: 4, lean: 12, arm: { yaw: -8, pitch: -8 }, l: { yaw: 10, pitch: -96 }, rl: { pitch: -30, out: 2 }, ll: { pitch: 8, out: 2 } },
    { t: 1, ...ag, twist: 2, lean: 14, arm: { yaw: -8, pitch: -6 }, l: { yaw: 10, pitch: -98 }, rl: { pitch: -42, out: 2 }, ll: { pitch: 10, out: 2 }, ease: L },
    { t: 2, ...ag, twist: -6, lean: 22, drop: -1.5, fwd: 2, arm: { yaw: -6, pitch: -42 }, l: { yaw: 8, pitch: -58 },
      rl: { pitch: 112, out: 1 }, ll: { pitch: -16, out: 2 }, head: { yaw: 0, pitch: 12 }, sc: KICK_R, ease: L },
    { t: 3, ...ag, twist: -8, lean: 25, drop: -1.8, fwd: 2, arm: { yaw: -6, pitch: -48 }, l: { yaw: 8, pitch: -52 },
      rl: { pitch: 118, out: 1 }, ll: { pitch: -18, out: 2 }, head: { yaw: 0, pitch: 14 }, ease: L },
    { t: 6, ...ag, twist: -7, lean: 24, drop: -1.7, fwd: 2, arm: { yaw: -6, pitch: -46 }, l: { yaw: 8, pitch: -54 },
      rl: { pitch: 115, out: 1 }, ll: { pitch: -18, out: 2 }, head: { yaw: 0, pitch: 13 }, ease: L },
    { t: 10, ...ag, ease: L },
  ],
  // 4 The kick that throws (contact 2): wound round, the whole body spins into a roundhouse, the leg stretched on the
  // strike tick, held while the target flies off.
  soco4: [
    { t: 0, ...ag, twist: 70, lean: -8, arm: { yaw: 60, pitch: -60 }, l: { yaw: -30, pitch: -50 }, rl: { pitch: -20, out: 2 }, ll: { pitch: 10, out: 3 } },
    { t: 1, ...ag, twist: 82, lean: -10, arm: { yaw: 64, pitch: -58 }, l: { yaw: -34, pitch: -48 }, rl: { pitch: -24, out: 2 }, ll: { pitch: 12, out: 3 }, ease: L },
    { t: 2, ...ag, twist: -62, lean: -18, arm: { yaw: 70, pitch: -20 }, l: { yaw: 40, pitch: -40 },
      rl: { pitch: 92, out: 30 }, ll: { pitch: -20, out: 4 }, head: { yaw: 0, pitch: 6 }, sc: KICK_R, ease: L },
    { t: 3, ...ag, twist: -74, lean: -20, arm: { yaw: 72, pitch: -18 }, l: { yaw: 42, pitch: -38 },
      rl: { pitch: 96, out: 32 }, ll: { pitch: -22, out: 4 }, head: { yaw: 0, pitch: 6 }, ease: L },
    { t: 8, ...ag, twist: -70, lean: -19, arm: { yaw: 70, pitch: -20 }, l: { yaw: 40, pitch: -40 },
      rl: { pitch: 94, out: 30 }, ll: { pitch: -22, out: 4 }, head: { yaw: 0, pitch: 6 }, ease: L },
    { t: 10, ...ag, twist: -30, rl: { pitch: 50, out: 10 }, ease: 'out' },
    { t: 13, ...ag, ease: L },
  ],
  // The ki dash: into it in one tick, the right fist straight at the target, the left arm and both legs streaming
  // back, held for the whole run (36 ticks at most) with two small settles.
  arrancada: [
    { t: 0, ...ag },
    { t: 1, ...ag, twist: -20, lean: 14, fwd: 1, arm: { yaw: -4, pitch: 4 }, l: { yaw: 14, pitch: 34 },
      rl: { pitch: -18, out: 1.5 }, ll: { pitch: -34, out: 1.5 }, head: { yaw: 0, pitch: -8 }, sc: SMEAR_R, ease: L },
    { t: 2, ...ag, twist: -16, lean: 12, fwd: 1, arm: { yaw: -4, pitch: 2 }, l: { yaw: 12, pitch: 30 },
      rl: { pitch: -16, out: 1.5 }, ll: { pitch: -30, out: 1.5 }, head: { yaw: 0, pitch: -8 }, ease: L },
    { t: 14, ...ag, twist: -14, lean: 11, fwd: 1, arm: { yaw: -3, pitch: 3 }, l: { yaw: 10, pitch: 28 },
      rl: { pitch: -12, out: 1.8 }, ll: { pitch: -32, out: 1.2 }, head: { yaw: 0, pitch: -7 }, ease: 'inout' },
    { t: 36, ...ag, twist: -16, lean: 12, fwd: 1, arm: { yaw: -4, pitch: 2 }, l: { yaw: 12, pitch: 30 },
      rl: { pitch: -16, out: 1.5 }, ll: { pitch: -30, out: 1.5 }, head: { yaw: 0, pitch: -8 }, ease: 'inout' },
  ],
  // A ki ball from the right hand (the Barragem in the air): the palm pulled to the hip, shot out in one tick.
  ki_d: [
    { t: 0, ...ag, twist: 32, arm: { yaw: 42, pitch: -62 } },
    { t: 1, ...ag, twist: -28, lean: 8, arm: { yaw: -6, pitch: 4 }, l: { yaw: -8, pitch: -40 }, sc: SMEAR_R, ease: L },
    { t: 2, ...ag, twist: -32, lean: 9, arm: { yaw: -6, pitch: 5 }, l: { yaw: -8, pitch: -40 }, ease: L },
    { t: 4, ...ag, twist: -30, lean: 9, arm: { yaw: -6, pitch: 4 }, l: { yaw: -8, pitch: -40 }, ease: L },
    { t: 8, ...ag, ease: L },
  ],
  // The same from the left hand.
  ki_e: [
    { t: 0, ...ag, twist: -30, l: { yaw: -22, pitch: -40 } },
    { t: 1, ...ag, twist: 28, lean: 8, l: { yaw: 22, pitch: -94 }, arm: { yaw: -30, pitch: -50 }, sc: SMEAR_L, ease: L },
    { t: 2, ...ag, twist: 32, lean: 9, l: { yaw: 24, pitch: -95 }, arm: { yaw: -30, pitch: -50 }, ease: L },
    { t: 4, ...ag, twist: 30, lean: 9, l: { yaw: 23, pitch: -94 }, arm: { yaw: -30, pitch: -50 }, ease: L },
    { t: 8, ...ag, ease: L },
  ],
  // Back behind the target (the Forte): the right leg cocked, then a straight thrust kick into its back.
  chute_atras: [
    { t: 0, ...ag, twist: 22, lean: -4, rl: { pitch: 60, out: 2 }, arm: { yaw: 20, pitch: -60 } },
    { t: 1, ...ag, twist: 26, lean: -6, rl: { pitch: 70, out: 2 }, arm: { yaw: 24, pitch: -62 }, ease: L },
    { t: 2, ...ag, twist: 10, lean: -26, fwd: 1, arm: { yaw: 40, pitch: -70 }, l: { yaw: -20, pitch: -40 },
      rl: { pitch: 96, out: 3 }, ll: { pitch: -18, out: 2 }, sc: KICK_R, ease: L },
    { t: 3, ...ag, twist: 8, lean: -30, fwd: 1.2, arm: { yaw: 42, pitch: -72 }, l: { yaw: -22, pitch: -38 },
      rl: { pitch: 100, out: 3 }, ll: { pitch: -20, out: 2 }, ease: L },
    { t: 8, ...ag, twist: 9, lean: -28, fwd: 1.2, arm: { yaw: 41, pitch: -71 }, l: { yaw: -21, pitch: -39 },
      rl: { pitch: 98, out: 3 }, ll: { pitch: -20, out: 2 }, ease: L },
    { t: 12, ...ag, ease: L },
  ],
  // The Martelo: over the target with both fists joined high over the head, arched back (held: the tension), then
  // the double axe handle straight down in one tick, the body folding over it, held while the target falls.
  martelo: [
    { t: 0, ...ag, twist: 0, lean: -14, arm: { yaw: -10, pitch: 78 }, l: { yaw: 10, pitch: -168 },
      rl: { pitch: 30, out: 2 }, ll: { pitch: 12, out: 2 }, head: { yaw: 0, pitch: 18 } },
    { t: 4, ...ag, twist: 0, lean: -20, arm: { yaw: -10, pitch: 86 }, l: { yaw: 10, pitch: -175 },
      rl: { pitch: 26, out: 2 }, ll: { pitch: 10, out: 2 }, head: { yaw: 0, pitch: 20 }, ease: L },
    { t: 5, ...ag, twist: 0, lean: 40, drop: 1, arm: { yaw: -8, pitch: -58 }, l: { yaw: 8, pitch: -32 },
      rl: { pitch: -20, out: 2 }, ll: { pitch: -30, out: 2 }, head: { yaw: 0, pitch: 24 }, sc: BIG_ARMS, ease: L },
    { t: 6, ...ag, twist: 0, lean: 46, drop: 1.2, arm: { yaw: -8, pitch: -66 }, l: { yaw: 8, pitch: -26 },
      rl: { pitch: -24, out: 2 }, ll: { pitch: -34, out: 2 }, head: { yaw: 0, pitch: 26 }, ease: L },
    { t: 11, ...ag, twist: 0, lean: 44, drop: 1.1, arm: { yaw: -8, pitch: -64 }, l: { yaw: 8, pitch: -28 },
      rl: { pitch: -22, out: 2 }, ll: { pitch: -32, out: 2 }, head: { yaw: 0, pitch: 25 }, ease: L },
    { t: 16, ...ag, ease: L },
  ],
  // The beam's charge (a soul backing off, boss/AirCombat RECUA): arms flung wide, legs braced apart, trembling,
  // then both hands brought together in front (the ball between them) for the last ticks.
  feixe: [
    { t: 0, ...ag },
    { t: 2, ...ag, twist: 0, lean: -6, arm: { yaw: 82, pitch: 8 }, l: { yaw: -82, pitch: -92 },
      rl: { pitch: 18, out: 9 }, ll: { pitch: -8, out: 9 }, head: { yaw: 0, pitch: 0 }, ease: L },
    { t: 3, ...ag, twist: 0, lean: -8, arm: { yaw: 86, pitch: 10 }, l: { yaw: -86, pitch: -94 },
      rl: { pitch: 18, out: 10 }, ll: { pitch: -8, out: 10 }, head: { yaw: 0, pitch: -2 }, ease: L },
    { t: 12, ...ag, twist: 2, lean: -9, arm: { yaw: 84, pitch: 12 }, l: { yaw: -84, pitch: -95 },
      rl: { pitch: 18, out: 10 }, ll: { pitch: -8, out: 10 }, head: { yaw: 0, pitch: -3 }, ease: 'inout' },
    { t: 15, ...ag, twist: 0, lean: 6, arm: { yaw: -6, pitch: 0 }, l: { yaw: 6, pitch: -90 },
      rl: { pitch: 16, out: 8 }, ll: { pitch: -10, out: 8 }, head: { yaw: 0, pitch: 4 }, sc: BIG_ARMS, ease: L },
    { t: 16, ...ag, twist: 0, lean: 8, arm: { yaw: -6, pitch: 0 }, l: { yaw: 6, pitch: -90 },
      rl: { pitch: 16, out: 8 }, ll: { pitch: -10, out: 8 }, head: { yaw: 0, pitch: 4 }, ease: L },
    { t: 21, ...ag, twist: 0, lean: 7, arm: { yaw: -5, pitch: 1 }, l: { yaw: 5, pitch: -91 },
      rl: { pitch: 16, out: 8 }, ll: { pitch: -10, out: 8 }, head: { yaw: 0, pitch: 4 }, ease: L },
  ],
  // The beam going out: hands pushed forward, the body leaning into the recoil, held while it fires (21 ticks).
  feixe_solta: [
    { t: 0, ...ag, twist: 0, lean: 12, fwd: 1.5, arm: { yaw: -5, pitch: 2 }, l: { yaw: 5, pitch: -92 },
      rl: { pitch: 10, out: 8 }, ll: { pitch: -14, out: 8 }, head: { yaw: 0, pitch: 4 }, sc: BIG_ARMS },
    { t: 1, ...ag, twist: 0, lean: 4, fwd: 0.5, arm: { yaw: -5, pitch: 4 }, l: { yaw: 5, pitch: -94 },
      rl: { pitch: 14, out: 8 }, ll: { pitch: -10, out: 8 }, head: { yaw: 0, pitch: 3 }, ease: L },
    { t: 21, ...ag, twist: 0, lean: 6, fwd: 0.8, arm: { yaw: -5, pitch: 3 }, l: { yaw: 5, pitch: -93 },
      rl: { pitch: 14, out: 8 }, ll: { pitch: -10, out: 8 }, head: { yaw: 0, pitch: 3 }, ease: 'inout' },
    { t: 27, ...ag, ease: L },
  ],
};
