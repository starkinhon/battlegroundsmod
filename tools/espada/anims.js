// Sword moves, in high level terms (rig.js): arm {yaw, pitch} = where the right arm points (yaw + right, pitch + up,
// -90 = hanging), blade {yaw, pitch} = where the blade points; twist (+ = chest to the right), lean (+ forward),
// drop (px down), grip (left hand on the grip), l = left arm (pitch -90 = forward), rl/ll = legs (pitch + = forward).
const REST = { arm: { yaw: 0, pitch: -90 }, blade: { yaw: 0, pitch: 25 } };
const stance = { rl: { pitch: 22 }, ll: { pitch: -16 }, drop: 1.2 };

// Waki-gamae run: low, leaning in, sword trailing behind on the right, tip back and low.
const run = { lean: 28, drop: 2, twist: 12, arm: { yaw: 150, pitch: -55 }, blade: { yaw: 172, pitch: -12 },
  l: { yaw: 15, pitch: -45 }, rl: { pitch: 38 }, ll: { pitch: -32 }, head: { yaw: -12, pitch: 6 } };

// Hasso: hands by the right shoulder, blade straight up and a bit back, chest turned away, weight back.
const hasso = { twist: 38, lean: -5, drop: 1.4, grip: 1, arm: { yaw: 22, pitch: 52 }, blade: { yaw: 172, pitch: 64 },
  ll: { pitch: 24 }, rl: { pitch: -16 }, head: { yaw: -38, pitch: 0 } };

// Barrage keys (one hand, fast): right high wound up, left low, right mid, left mid.
const B = {
  RH: { twist: 26, lean: 6, arm: { yaw: 48, pitch: 48 }, blade: { yaw: 125, pitch: 42 }, l: { yaw: 25, pitch: -45 }, ...stance, head: { yaw: -26 } },
  LL: { twist: -26, lean: 14, arm: { yaw: -48, pitch: -38 }, blade: { yaw: -85, pitch: -28 }, l: { yaw: 40, pitch: -20 }, ...stance, head: { yaw: 26 } },
  RU: { twist: 22, lean: 4, arm: { yaw: 52, pitch: 38 }, blade: { yaw: 70, pitch: 62 }, l: { yaw: 20, pitch: -50 }, ...stance, head: { yaw: -22 } },
  RM: { twist: 34, lean: 8, arm: { yaw: 75, pitch: 4 }, blade: { yaw: 128, pitch: 2 }, l: { yaw: 20, pitch: -55 }, ...stance, head: { yaw: -34 } },
  LM: { twist: -38, lean: 10, arm: { yaw: -62, pitch: -2 }, blade: { yaw: -105, pitch: -6 }, l: { yaw: 45, pitch: -25 }, ...stance, head: { yaw: 38 } },
};

module.exports = {
  // The slide (up to 14 ticks): into the run in 3 ticks, then held.
  dash: [
    { t: 0, ...REST },
    { t: 3, ...run, ease: 'out' },
    { t: 16, ...run, lean: 30, ease: 'linear' },
  ],
  // The cut through: from the run, a flat cut right to left as he goes past (hit at tick 2), then the end pose held
  // with the blade out to the left, his back to them, until the cut shows on them.
  dash_corte: [
    { t: 0, ...run },
    { t: 1, ...run, twist: 40, lean: 18, arm: { yaw: 95, pitch: -8 }, blade: { yaw: 130, pitch: -4 }, ease: 'out' },
    { t: 3, twist: -10, lean: 16, drop: 2.2, arm: { yaw: 5, pitch: 0 }, blade: { yaw: -40, pitch: -2 }, l: { yaw: 20, pitch: -30 },
      rl: { pitch: 34 }, ll: { pitch: -26 }, head: { yaw: 10, pitch: 3 }, ease: 'in' },
    { t: 5, twist: -48, lean: 22, drop: 3, arm: { yaw: -95, pitch: -6 }, blade: { yaw: -112, pitch: -14 }, l: { yaw: 30, pitch: -10 },
      rl: { pitch: 40 }, ll: { pitch: -34 }, head: { yaw: 40, pitch: 4 }, ease: 'out' },
    { t: 14, twist: -50, lean: 22, drop: 3, arm: { yaw: -97, pitch: -8 }, blade: { yaw: -114, pitch: -16 }, l: { yaw: 30, pitch: -10 },
      rl: { pitch: 40 }, ll: { pitch: -34 }, head: { yaw: 40, pitch: 4 }, ease: 'linear' },
  ],
  // Charging the Forte: into hasso in 6 ticks, then held as long as the key is (the server cuts it off).
  forte_carga: [
    { t: 0, ...REST },
    { t: 6, ...hasso, ease: 'out' },
    { t: 44, ...hasso, drop: 1.8, lean: -6, ease: 'inout' },
  ],
  // Kesagiri: from hasso down across, right shoulder to left hip, stepping in; the end pose held a moment.
  forte_corte: [
    { t: 0, ...hasso },
    { t: 1, ...hasso, twist: 44, arm: { yaw: 26, pitch: 60 }, blade: { yaw: 175, pitch: 50 }, ease: 'out' },
    { t: 3, twist: 10, lean: 8, drop: 1.6, grip: 1, arm: { yaw: 5, pitch: 10 }, blade: { yaw: 30, pitch: 45 }, ll: { pitch: 5 }, rl: { pitch: 10 },
      head: { yaw: -10 }, ease: 'in' },
    { t: 5, twist: -30, lean: 22, drop: 2.6, fwd: 2, grip: 0.6, arm: { yaw: -50, pitch: -45 }, blade: { yaw: -58, pitch: -26 }, ll: { pitch: -20 },
      rl: { pitch: 32 }, head: { yaw: 28 }, ease: 'out' },
    { t: 13, twist: -32, lean: 20, drop: 2.6, fwd: 2, grip: 0.5, arm: { yaw: -52, pitch: -48 }, blade: { yaw: -60, pitch: -28 }, ll: { pitch: -20 },
      rl: { pitch: 32 }, head: { yaw: 28 }, ease: 'linear' },
  ],
  // One round of the barrage (12 ticks, a cut every 3): kesa down, rising back up, flat right to left, rising across.
  // The server starts it again while the key is held.
  barragem: [
    { t: 0, ...B.RH },
    { t: 3, ...B.LL, ease: 'snap' },
    { t: 6, ...B.RU, ease: 'snap' },
    { t: 6.5, ...B.RM, ease: 'out' },
    { t: 9, ...B.LM, ease: 'snap' },
    { t: 12, ...B.RH, ease: 'snap' },
  ],
  // The barrage's last cut: both hands, low behind on the right, then a big rising cut over the head that throws them up.
  barragem_final: [
    { t: 0, ...B.RH },
    { t: 3, twist: 40, lean: 18, drop: 3, grip: 1, arm: { yaw: 40, pitch: -60 }, blade: { yaw: 160, pitch: -35 }, rl: { pitch: 30 }, ll: { pitch: -24 },
      head: { yaw: -40, pitch: 4 }, ease: 'out' },
    { t: 5, twist: 5, lean: 2, drop: 1.5, grip: 1, arm: { yaw: 5, pitch: 15 }, blade: { yaw: 0, pitch: 70 }, rl: { pitch: 20 }, ll: { pitch: -10 },
      head: { yaw: -5, pitch: 3 }, ease: 'in' },
    { t: 7, twist: -18, lean: -12, drop: 0, grip: 0.8, arm: { yaw: -15, pitch: 78 }, blade: { yaw: -160, pitch: 55 }, rl: { pitch: 8 }, ll: { pitch: -6 },
      head: { yaw: 18, pitch: 6 }, ease: 'out' },
    { t: 16, twist: -18, lean: -10, drop: 0, grip: 0.8, arm: { yaw: -15, pitch: 76 }, blade: { yaw: -160, pitch: 52 }, rl: { pitch: 8 }, ll: { pitch: -6 },
      head: { yaw: 18, pitch: 6 }, ease: 'linear' },
  ],
};

// ---------- the click combo (padrao.combo.json: 5 hits landing at ticks 1, 2, 2, 2, 3) ----------
// Each one starts where the one before ends, so the chain flows; the blade crosses in front on the hit tick.
const C = {
  // flat cut right to left
  c1wind: { twist: 34, lean: 6, arm: { yaw: 80, pitch: 2 }, blade: { yaw: 130, pitch: 4 }, l: { yaw: 20, pitch: -50 }, ...stance, head: { yaw: -34 } },
  c1mid: { twist: 0, lean: 10, arm: { yaw: 5, pitch: 0 }, blade: { yaw: -15, pitch: 0 }, l: { yaw: 30, pitch: -35 }, ...stance, head: { yaw: 0 } },
  c1end: { twist: -36, lean: 10, arm: { yaw: -70, pitch: -6 }, blade: { yaw: -110, pitch: -8 }, l: { yaw: 45, pitch: -25 }, ...stance, head: { yaw: 36 } },
  // rising back across, left low to right high
  c2mid: { twist: 0, lean: 6, arm: { yaw: 5, pitch: 10 }, blade: { yaw: 25, pitch: 40 }, l: { yaw: 30, pitch: -40 }, ...stance, head: { yaw: 0 } },
  c2end: { twist: 30, lean: 2, arm: { yaw: 55, pitch: 45 }, blade: { yaw: 80, pitch: 62 }, l: { yaw: 25, pitch: -50 }, ...stance, head: { yaw: -30 } },
  // kesagiri, right high down to left low
  c3mid: { twist: 0, lean: 12, arm: { yaw: 0, pitch: 12 }, blade: { yaw: 25, pitch: 42 }, l: { yaw: 30, pitch: -40 }, ...stance, head: { yaw: 0 } },
  c3end: { twist: -28, lean: 16, arm: { yaw: -48, pitch: -40 }, blade: { yaw: -70, pitch: -28 }, l: { yaw: 40, pitch: -20 }, ...stance, head: { yaw: 28 } },
  // thrust: pulled back by the hip, then straight out with a lunge
  c4wind: { twist: 30, lean: 0, drop: 1.6, arm: { yaw: 40, pitch: -45 }, blade: { yaw: 0, pitch: 2 }, l: { yaw: 10, pitch: -60 }, rl: { pitch: 10 }, ll: { pitch: -10 }, head: { yaw: -30 } },
  c4end: { twist: -12, lean: 18, drop: 2.6, fwd: 2.5, arm: { yaw: 0, pitch: 0 }, blade: { yaw: -2, pitch: -2 }, l: { yaw: -30, pitch: 10 }, rl: { pitch: 40 }, ll: { pitch: -32 }, head: { yaw: 12 } },
  // both hands up over the head (jodan), then straight down the middle
  c5wind: { twist: 0, lean: -8, drop: 0.5, grip: 1, arm: { yaw: 8, pitch: 80 }, blade: { yaw: 180, pitch: 40 }, rl: { pitch: 10 }, ll: { pitch: -8 }, head: { yaw: 0 } },
  c5mid: { twist: 0, lean: 10, drop: 1.5, grip: 1, arm: { yaw: 4, pitch: 25 }, blade: { yaw: 0, pitch: 55 }, rl: { pitch: 24 }, ll: { pitch: -18 }, head: { yaw: 0 } },
  c5end: { twist: 0, lean: 24, drop: 3, fwd: 2, grip: 1, arm: { yaw: 3, pitch: -35 }, blade: { yaw: 0, pitch: -30 }, rl: { pitch: 36 }, ll: { pitch: -28 }, head: { yaw: 0 } },
};
const hold = (p, d) => ({ ...p, lean: (p.lean || 0) + d });
Object.assign(module.exports, {
  combo1: [
    { t: 0, ...REST },
    { t: 0.5, ...C.c1wind, ease: 'out' },
    { t: 1, ...C.c1mid, ease: 'in' },
    { t: 2.2, ...C.c1end, ease: 'out' },
    { t: 7, ...hold(C.c1end, -2), ease: 'inout' },
  ],
  combo2: [
    { t: 0, ...C.c1end },
    { t: 0.8, ...C.c3end, twist: -30, arm: { yaw: -55, pitch: -45 }, blade: { yaw: -80, pitch: -40 }, ease: 'out' },
    { t: 2, ...C.c2mid, ease: 'in' },
    { t: 3.2, ...C.c2end, ease: 'out' },
    { t: 8, ...hold(C.c2end, -2), ease: 'inout' },
  ],
  combo3: [
    { t: 0, ...C.c2end },
    { t: 0.9, ...C.c2end, twist: 36, arm: { yaw: 45, pitch: 55 }, blade: { yaw: 130, pitch: 45 }, ease: 'out' },
    { t: 2, ...C.c3mid, ease: 'in' },
    { t: 3.2, ...C.c3end, ease: 'out' },
    { t: 8, ...hold(C.c3end, -2), ease: 'inout' },
  ],
  combo4: [
    { t: 0, ...C.c3end },
    { t: 1.2, ...C.c4wind, ease: 'out' },
    { t: 2, ...C.c4end, ease: 'snap' },
    { t: 8, ...hold(C.c4end, -2), ease: 'inout' },
  ],
  combo5: [
    { t: 0, ...C.c4end },
    { t: 2, ...C.c5wind, ease: 'out' },
    { t: 3, ...C.c5mid, ease: 'in' },
    { t: 4.2, ...C.c5end, ease: 'out' },
    { t: 14, ...hold(C.c5end, -2), ease: 'inout' },
  ],
});
