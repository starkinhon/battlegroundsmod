// Standing, walking and running poses of each character (Stark, 2026-11-07: "cada personagem vai ter uma pose padrão").
// Same rig as the skill poses (../espada/rig.js). Each character has three loops:
//   parado (idle, timed in ticks), andar (walk) and correr (run). The walk and run loops are one full stride (both
//   feet) and, in the game, follow the distance walked (limbSwing) the way vanilla's walk does, so the feet keep up
//   with the ground; len is how many ticks one stride takes at the normal walk / sprint speed.
// flutua: px the whole body floats over the ground (Bills).
// Fields: arm / larm = where the right / left arm points, in chest terms {yaw (+ = out to that arm's side, 180 =
// straight back), pitch (0 = ahead, -90 = hanging), roll}; lean (+ forward), twist (+ = right shoulder back), drop
// (px down, negative floats up), rl/ll legs {pitch + = forward, out}, head {yaw, pitch (+ = looks up)}; the head is
// counted from level ground, not from the chest, so a leaning body still looks ahead.
// Gojo is left as vanilla (Stark: "do jeito que tá").

(function () {
const P = (t, pose, ease) => ({ t, ...pose, ...(ease ? { ease } : {}) });
const mix = (a, b) => {
  const o = JSON.parse(JSON.stringify(a));
  for (const k in b) o[k] = b[k] && typeof b[k] === 'object' ? { ...(o[k] || {}), ...b[k] } : b[k];
  return o;
};
// The other foot forward: legs and arms change sides, the twist and the head turn the other way.
function mirror(p) {
  const o = JSON.parse(JSON.stringify(p));
  [o.rl, o.ll] = [p.ll, p.rl];
  o.arm = p.larm; o.larm = p.arm;
  if (p.twist) o.twist = -p.twist;
  if (p.side) o.side = -p.side;
  if (p.head) o.head = { ...p.head, yaw: -(p.head.yaw || 0) };
  return o;
}
// One stride from the pose with the right foot forward (contact) and the pose passing under the body (pass).
// keepArms: the arms don't swap sides (crossed, behind the back, hand on the sword...).
function stride(len, contact, pass, keepArms) {
  const m = (p) => { const o = mirror(p); if (keepArms) { o.arm = p.arm; o.larm = p.larm; } return o; };
  return [P(0, contact), P(len / 4, pass, 'inout'), P(len / 2, m(contact), 'inout'), P(len * 3 / 4, m(pass), 'inout'), P(len, contact, 'inout')];
}
// A breathing loop: from a to b and back.
const breathe = (len, a, b) => [P(0, a), P(len / 2, b, 'inout'), P(len, a, 'inout')];

const L = {};

// ===================== BILLS =====================
// God of Destruction: never walks. Hands clasped behind his back, floating a little over the ground, chin up, bored.
// Walking and running are gliding: the body leans into it and the legs trail behind, together, like a cape.
// The hands meet behind the lower back (no elbows in Minecraft: straight arms angled back and in).
const billsHands = { arm: { yaw: -142, pitch: -59, roll: -30 }, larm: { yaw: -137, pitch: -61, roll: -30 } };
const billsIdle = { ...billsHands, lean: -3, drop: -3, rl: { pitch: 7, out: -1 }, ll: { pitch: -3, out: 1 }, head: { yaw: 0, pitch: 6 } };
L.bills = {
  nome: 'Bills', flutua: 3,
  parado: { len: 60, keys: breathe(60, billsIdle,
    mix(billsIdle, { drop: -3.8, lean: -2, rl: { pitch: 9, out: -1 }, ll: { pitch: -1, out: 1 }, head: { yaw: 0, pitch: 7 } })) },
  // Gliding at walk speed: a slight lean, legs drifting back, a slow sway of the feet.
  andar: { len: 24, keys: breathe(24,
    mix(billsIdle, { lean: 7, drop: -3, rl: { pitch: -10, out: -1 }, ll: { pitch: -16, out: 1 }, head: { yaw: 0, pitch: 5 } }),
    mix(billsIdle, { lean: 8, drop: -3.5, rl: { pitch: -14, out: -1 }, ll: { pitch: -12, out: 1 }, head: { yaw: 0, pitch: 5 } })) },
  // Fast glide: lower to the ground, leaning hard, legs straight back like he is being pulled.
  correr: { len: 16, keys: breathe(16,
    mix(billsHands, { lean: 17, drop: -2.2, rl: { pitch: -26, out: -1 }, ll: { pitch: -32, out: 1 }, head: { yaw: 0, pitch: 3 } }),
    mix(billsHands, { lean: 18, drop: -2.6, rl: { pitch: -31, out: -1 }, ll: { pitch: -28, out: 1 }, head: { yaw: 0, pitch: 3 } })) },
};

// ===================== MADARA =====================
// Always arms crossed, standing tall with the weight on one leg, chin up, looking down on everyone.
// Walks slow and heavy with the arms still crossed; runs like a ninja (arms straight back, chest forward).
const crossed = { arm: { yaw: -70, pitch: -24, roll: 20 }, larm: { yaw: -77, pitch: -33, roll: 20 } };
const madaraIdle = { ...crossed, twist: 7, lean: -1, rl: { pitch: -2, out: 5 }, ll: { pitch: 4, out: 3 }, head: { yaw: -4, pitch: 5 } };
L.madara = {
  nome: 'Madara',
  parado: { len: 70, keys: breathe(70, madaraIdle, mix(madaraIdle, { lean: -2, drop: -0.25, head: { yaw: -4, pitch: 6 } })) },
  // A slow, wide stride (long legs), the chest swinging a little against the steps, arms crossed the whole time.
  andar: { len: 14, keys: stride(14,
    mix(crossed, { twist: 5, lean: 1, drop: 0.5, rl: { pitch: 30, out: 2 }, ll: { pitch: -26, out: 2 }, head: { yaw: -3, pitch: 4 } }),
    mix(crossed, { twist: 0, lean: 1, drop: -0.3, rl: { pitch: -2, out: 2 }, ll: { pitch: 6, out: 2 }, head: { yaw: 0, pitch: 4 } }), true) },
  // Ninja run: chest far forward, arms straight back and a little open, long fast strides, head up on the target.
  correr: { len: 8, keys: stride(8,
    { lean: 32, drop: 1.2, twist: 4, arm: { yaw: 168, pitch: -14, roll: 0 }, larm: { yaw: 172, pitch: -18, roll: 0 },
      rl: { pitch: 58, out: 1 }, ll: { pitch: -48, out: 1 }, head: { yaw: 0, pitch: 4 } },
    { lean: 30, drop: -0.6, twist: 0, arm: { yaw: 170, pitch: -16, roll: 0 }, larm: { yaw: 170, pitch: -16, roll: 0 },
      rl: { pitch: 6, out: 1 }, ll: { pitch: 10, out: 1 }, head: { yaw: 0, pitch: 4 } }) },
};

// ===================== AIZEN =====================
// Calm, elegant, always in control (references: Aizen standing still with the left hand resting on Kyōka Suigetsu at
// his hip, the thumb on the guard; he never hurries). Straight back, weight on the right leg, chin level, the right
// arm loose. Walk: short arm swing, unhurried. Run: smooth and low, the left hand keeping the sword against the hip.
const sheath = { yaw: -22, pitch: -70, roll: -30 };
const aizenIdle = { arm: { yaw: 6, pitch: -86, roll: 0 }, larm: sheath, twist: -5, rl: { pitch: 1, out: 2 }, ll: { pitch: 7, out: 4 },
  head: { yaw: 3, pitch: 0 } };
L.aizen = {
  nome: 'Aizen',
  parado: { len: 80, keys: breathe(80, aizenIdle, mix(aizenIdle, { drop: -0.2, arm: { yaw: 6, pitch: -84, roll: 0 }, head: { yaw: 5, pitch: 1 } })) },
  andar: { len: 13, keys: stride(13,
    { arm: { yaw: 6, pitch: -102, roll: 0 }, larm: sheath, twist: 3, lean: 1, drop: 0.3, rl: { pitch: 24, out: 1 }, ll: { pitch: -22, out: 1 }, head: { yaw: 0, pitch: 0 } },
    { arm: { yaw: 6, pitch: -90, roll: 0 }, larm: sheath, twist: 0, lean: 1, drop: -0.2, rl: { pitch: -2, out: 1 }, ll: { pitch: 4, out: 1 }, head: { yaw: 0, pitch: 0 } }, true)
    // only the right arm swings: forward when the left foot is forward
    .map((k, i) => (i === 2 ? { ...k, arm: { yaw: 6, pitch: -74, roll: 0 } } : k)) },
  correr: { len: 9, keys: stride(9,
    { arm: { yaw: 10, pitch: -128, roll: 0 }, larm: { yaw: -18, pitch: -98, roll: -30 }, twist: 8, lean: 16, drop: 1, rl: { pitch: 44, out: 1 }, ll: { pitch: -40, out: 1 }, head: { yaw: 0, pitch: 1 } },
    { arm: { yaw: 8, pitch: -90, roll: 0 }, larm: { yaw: -18, pitch: -98, roll: -30 }, twist: 0, lean: 15, drop: -0.4, rl: { pitch: 4, out: 1 }, ll: { pitch: 8, out: 1 }, head: { yaw: 0, pitch: 1 } }, true)
    .map((k, i) => (i === 2 ? { ...k, arm: { yaw: 6, pitch: -48, roll: 0 } } : k)) },
};

// ===================== GOJO (vanilla, for comparison only; not baked) =====================
const vanilla = (len, amp, arms) => stride(len,
  { arm: { yaw: 0, pitch: -90 - arms, roll: 0 }, larm: { yaw: 0, pitch: -90 + arms, roll: 0 }, rl: { pitch: amp, out: 0 }, ll: { pitch: -amp, out: 0 } },
  { arm: { yaw: 0, pitch: -90, roll: 0 }, larm: { yaw: 0, pitch: -90, roll: 0 }, rl: { pitch: 0, out: 0 }, ll: { pitch: 0, out: 0 } });
const rest = { arm: { yaw: 0, pitch: -90, roll: 0 }, larm: { yaw: 0, pitch: -90, roll: 0 } };
L.gojo = {
  nome: 'Gojo', vanilla: true,
  parado: { len: 60, keys: breathe(60, rest, rest) },
  andar: { len: 11, keys: vanilla(11, 40, 34) },
  correr: { len: 8.4, keys: vanilla(8.4, 52, 46) },
};

if (typeof module !== 'undefined') module.exports = L; else (typeof window !== 'undefined' ? window : globalThis).LOCOMOCAO = L;
})();
