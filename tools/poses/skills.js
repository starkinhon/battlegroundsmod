// Skill poses (everything not made by Stark in his editor: his are madara.katon and gojo.dominio, never touched here).
// bills.hakai and bills.destruction_energy keep their Forja poses (Stark liked them as they were, 2026-11-07).
// Same rig and rules as anims.js (the pose guide from the emote study): wind up the other way, hold the peak a
// moment, strike in 2-3 ticks, overshoot, settle; the whole body moves and the head keeps on the target.
// Each entry: { keys, soltar?: ticks } baked to <skill>.json. The time of the skill's spec (souls_skills/<skill>.json,
// "tempo") is noted on each: W = atrasoTicks (the move goes off), E = W + duracaoTicks, back = how long the way back.
// soltar: a held skill waits at that tick until the key is let go (the server sends ModPayloads RELEASE), then plays
// the rest. Every pose here follows the aim with the head (segueMira).
//
// Arms are written world-relative here and turned into the rig's chest-relative terms by fix():
//   ra = right arm {yaw (+ = to his right), pitch (0 = straight ahead, 90 = up, -90 = hanging)}
//   la = left arm  {yaw (+ = out to his left), pitch (same)}
// Other fields as in anims.js: twist (+ = right shoulder back), lean (+ forward), drop / fwd (px), rl/ll legs
// {pitch + = forward, out}, head {yaw, pitch (+ = down)}.

const REST = { ra: { yaw: 0, pitch: -90 }, la: { yaw: 0, pitch: -90 } };

function fix(k) {
  const o = { ...k };
  const tw = k.twist || 0, ln = k.lean || 0;
  if (k.ra) { o.arm = { yaw: k.ra.yaw - tw, pitch: k.ra.pitch + ln }; delete o.ra; }
  if (k.la) { o.l = { yaw: k.la.yaw + tw, pitch: -(k.la.pitch + ln + 90) }; delete o.la; }
  if (!o.arm && !o.r) o.arm = { yaw: 0, pitch: -90 };
  return o;
}
const P = (t, pose, ease, extra) => ({ t, ...pose, ...(extra || {}), ...(ease ? { ease } : {}) });

// ---------- shared poses ----------
// Bills keeps the left hand behind his back for almost everything.
const BACK = { yaw: -12, pitch: -118 };
const stance = { rl: { pitch: -6, out: 3 }, ll: { pitch: 6, out: 3 } };
const wide = { rl: { pitch: -4, out: 9 }, ll: { pitch: 4, out: 9 }, drop: 1.6 };
const S = {};

// ===================== GOJO =====================
// gojo.azul: W6 E16 back8. Gojo doesn't wind up: the hand comes up to the chest, two fingers out, and flicks ahead.
// Feet stay put, the body barely turns; the left hand hangs loose.
const azulFlick = { twist: -6, lean: 3, ...stance, ra: { yaw: 4, pitch: 6 }, la: { yaw: 6, pitch: -96 }, head: { yaw: 0, pitch: 2 } };
S['gojo.azul'] = { keys: [
  P(0, REST),
  P(4, { twist: 8, ...stance, ra: { yaw: -24, pitch: -6 }, la: { yaw: 6, pitch: -94 }, head: { yaw: 0, pitch: 4 } }, 'out'),
  P(5, { twist: 10, ...stance, ra: { yaw: -28, pitch: -2 }, la: { yaw: 6, pitch: -94 }, head: { yaw: 0, pitch: 4 } }, 'inout'),
  P(6, azulFlick, 'snap'),
  P(8, { ...azulFlick, ra: { yaw: 6, pitch: 12 } }, 'out'),
  P(14, azulFlick, 'inout'),
  P(24, REST, 'inout'),
] };

// gojo.azul_maximo: held (up to 600). Both hands out, feet wide and low, leaning into it.
const azulMax = { twist: 4, lean: 12, ...wide, drop: 2.4, ra: { yaw: 12, pitch: 2 }, la: { yaw: 12, pitch: 2 }, head: { yaw: 0, pitch: 4 } };
S['gojo.azul_maximo'] = { keys: [
  P(0, REST),
  P(3, { lean: -4, drop: 1, ra: { yaw: 30, pitch: -30 }, la: { yaw: 30, pitch: -30 }, ...stance }, 'out'),
  P(8, azulMax, 'snap'),
  P(10, { ...azulMax, ra: { yaw: 14, pitch: 6 }, la: { yaw: 14, pitch: 6 } }, 'out'),
] };

// gojo.vermelho_segurando: held. Side-on, right hand up by the chest pointing ahead, left hand loose behind.
const vermHold = { twist: 46, lean: 4, drop: 1.4, ra: { yaw: 18, pitch: -6 }, la: { yaw: 30, pitch: -100 }, rl: { pitch: -16, out: 5 },
  ll: { pitch: 20, out: 5 }, head: { yaw: 0, pitch: 2 } };
S['gojo.vermelho_segurando'] = { keys: [
  P(0, REST),
  P(2, { twist: 20, drop: 0.6, ra: { yaw: 40, pitch: -40 }, la: { yaw: 10, pitch: -90 }, ...stance }, 'out'),
  P(6, vermHold, 'inout'),
] };
// gojo.vermelho: W1 E11 back8. Fires from the held pose without stepping: the pointing hand kicks up with the recoil
// and the body rocks back on its heels, then settles.
const vermKick = { ...vermHold, twist: 40, lean: -8, fwd: -1, ra: { yaw: 16, pitch: 20 }, head: { yaw: 0, pitch: -2 } };
S['gojo.vermelho'] = { keys: [
  P(0, vermHold),
  P(1, vermKick, 'snap'),
  P(3, { ...vermKick, lean: -10, ra: { yaw: 16, pitch: 26 } }, 'out'),
  P(7, { ...vermHold, lean: 0, ra: { yaw: 18, pitch: 2 } }, 'inout'),
  P(11, vermHold, 'inout'),
  P(19, REST, 'inout'),
] };

// gojo.roxo_segurando: W40 then held. Arms open wide (Blue on the left, Red on the right), then slowly brought
// together in front until the hands meet.
const roxoOpen = { twist: 0, lean: -6, drop: 1.2, ...wide, ra: { yaw: 80, pitch: 6 }, la: { yaw: 80, pitch: 6 }, head: { yaw: 0, pitch: -8 } };
const roxoJoin = { twist: 16, lean: 10, ...wide, drop: 2.2, ra: { yaw: 4, pitch: 2 }, la: { yaw: -2, pitch: 2 }, head: { yaw: 0, pitch: 4 } };
S['gojo.roxo_segurando'] = { keys: [
  P(0, REST),
  P(4, { drop: 0.8, ra: { yaw: 20, pitch: -60 }, la: { yaw: 20, pitch: -60 }, ...stance }, 'out'),
  P(12, roxoOpen, 'inout'),
  P(20, { ...roxoOpen, ra: { yaw: 84, pitch: 10 }, la: { yaw: 84, pitch: 10 } }, 'inout'),
  P(36, { ...roxoJoin, ra: { yaw: 10, pitch: 4 }, la: { yaw: 6, pitch: 4 } }, 'inout'),
  P(40, roxoJoin, 'out'),
] };
// gojo.roxo: W1 E15 back12. Lets it go standing tall: the hands part, the right stays pointing ahead, the left drops
// away; the release pushes the chest back and the chin up, feet planted.
const roxoGo = { twist: 12, lean: -10, ...wide, drop: 1.2, ra: { yaw: 4, pitch: 6 }, la: { yaw: 40, pitch: -60 }, head: { yaw: 0, pitch: -6 } };
S['gojo.roxo'] = { keys: [
  P(0, roxoJoin),
  P(1, roxoGo, 'snap'),
  P(3, { ...roxoGo, lean: -14, ra: { yaw: 4, pitch: 12 }, la: { yaw: 50, pitch: -66 } }, 'out'),
  P(9, { ...roxoGo, lean: -6, ra: { yaw: 4, pitch: 4 }, la: { yaw: 30, pitch: -76 } }, 'inout'),
  P(15, { ...roxoGo, lean: -4, ra: { yaw: 4, pitch: 2 }, la: { yaw: 20, pitch: -84 } }, 'inout'),
  P(27, REST, 'inout'),
] };

// gojo.azul_black_flash_segurando: held. The left palm pulls the target in with the Blue; the right fist is cocked.
const bfHold = { twist: 40, lean: 8, drop: 1.8, ra: { yaw: 120, pitch: -40 }, la: { yaw: -4, pitch: 0 }, rl: { pitch: -20, out: 5 },
  ll: { pitch: 26, out: 5 }, head: { yaw: 0, pitch: 4 } };
S['gojo.azul_black_flash_segurando'] = { keys: [
  P(0, REST),
  P(2, { twist: 10, drop: 0.6, ra: { yaw: 30, pitch: -60 }, la: { yaw: 30, pitch: -30 }, ...stance }, 'out'),
  P(6, bfHold, 'inout'),
  P(9, { ...bfHold, twist: 44, ra: { yaw: 126, pitch: -36 } }, 'inout'),
] };
// gojo.azul_black_flash: W1 E37 back12 (Black Flash v2, PR #158). The punch lands at 1 and stays IN the target for the
// fit (encaixeTicks 34) while the black lightning goes off, the body leaning into it and grinding; at the end the
// last burst throws the target far: the fist drives through, then back.
const bfHit = { twist: -40, lean: 26, drop: 2.4, fwd: 3.5, ra: { yaw: 6, pitch: 2 }, la: { yaw: 10, pitch: -150 }, rl: { pitch: -36, out: 3 },
  ll: { pitch: 40, out: 3 }, head: { yaw: 0, pitch: 4 } };
const bfGrind = { ...bfHit, twist: -46, lean: 30, drop: 2.8, fwd: 4 };
const bfThrow = { ...bfHit, twist: -58, lean: 34, drop: 2.6, fwd: 5, ra: { yaw: 2, pitch: 6 }, la: { yaw: 20, pitch: -160 } };
S['gojo.azul_black_flash'] = { keys: [
  P(0, bfHold),
  P(1, bfHit, 'snap'),
  P(3, { ...bfHit, twist: -50, lean: 30, fwd: 4 }, 'out'),
  P(12, bfGrind, 'inout'),
  P(24, { ...bfGrind, twist: -48, lean: 32, drop: 3 }, 'inout'),
  P(33, { ...bfGrind, twist: -44, lean: 28 }, 'inout'),
  P(35, bfThrow, 'snap'),
  P(38, { ...bfThrow, twist: -62, lean: 36 }, 'out'),
  P(49, REST, 'inout'),
] };

// ===================== BILLS =====================
// bills.hakai_bola: W96 E106 back3. A god doesn't throw: the ball forms over the raised hand, then the hand just
// tips down to point at the target and the ball goes; the left hand never leaves his back.
const bolaUp = { twist: 10, lean: -4, ra: { yaw: 10, pitch: 80 }, la: BACK, ...stance, head: { yaw: 0, pitch: -22 } };
const bolaSend = { twist: 8, lean: 2, ra: { yaw: 6, pitch: 10 }, la: BACK, ...stance, head: { yaw: 0, pitch: 4 } };
S['bills.hakai_bola'] = { keys: [
  P(0, REST),
  P(12, bolaUp, 'inout'),
  P(90, { ...bolaUp, head: { yaw: 0, pitch: -18 } }, 'inout'),
  P(95, { ...bolaUp, ra: { yaw: 10, pitch: 86 }, head: { yaw: 0, pitch: -6 } }, 'inout'),
  P(98, bolaSend, 'out'),
  P(100, { ...bolaSend, ra: { yaw: 6, pitch: 4 } }, 'out'),
  P(105, bolaSend, 'inout'),
  P(114, REST, 'inout'),
] };

// bills.hakaizinho: W8 E16 back2. The small one: the hand to the mouth, then a flick ahead.
const zinhoMouth = { twist: 14, ra: { yaw: -30, pitch: 34 }, la: BACK, ...stance, head: { yaw: 0, pitch: 4 } };
const zinhoFlick = { twist: -10, lean: 8, fwd: 1, ra: { yaw: 6, pitch: 6 }, la: BACK, ...stance, head: { yaw: 0, pitch: 2 } };
S['bills.hakaizinho'] = { keys: [
  P(0, REST),
  P(5, zinhoMouth, 'out'),
  P(7, { ...zinhoMouth, twist: 18 }, 'inout'),
  P(8, zinhoFlick, 'snap'),
  P(10, { ...zinhoFlick, ra: { yaw: 10, pitch: 14 } }, 'out'),
  P(15, zinhoFlick, 'inout'),
  P(22, REST, 'inout'),
] };

// bills.destruction_energy_chuva: flying, arms spread up while it rains down; let go when the last shot is out.
const chuva = { twist: 0, lean: -10, ra: { yaw: 70, pitch: 40 }, la: { yaw: 70, pitch: 40 }, rl: { pitch: -14, out: 4 },
  ll: { pitch: 8, out: 4 }, head: { yaw: 0, pitch: -16 } };
S['bills.destruction_energy_chuva'] = { soltar: 12, keys: [
  P(0, REST),
  P(3, { drop: 1.4, ra: { yaw: 20, pitch: -70 }, la: { yaw: 20, pitch: -70 }, rl: { pitch: 10, out: 2 }, ll: { pitch: 10, out: 2 } }, 'out'),
  P(8, { ...chuva, ra: { yaw: 74, pitch: 48 }, la: { yaw: 74, pitch: 48 } }, 'snap'),
  P(12, chuva, 'inout'),
  P(22, REST, 'inout'),
] };

// bills.destruction_sphere: held (up to 200), let go = the arm sweeps up and the ball flies up.
const sphHold = { twist: 16, lean: 4, ra: { yaw: 8, pitch: 6 }, la: BACK, ...stance, head: { yaw: 0, pitch: 4 } };
const sphUp = { twist: 4, lean: -10, ra: { yaw: 6, pitch: 80 }, la: BACK, ...stance, head: { yaw: 0, pitch: -26 } };
S['bills.destruction_sphere'] = { soltar: 8, keys: [
  P(0, REST),
  P(3, { drop: 0.8, ra: { yaw: 16, pitch: -40 }, la: BACK, ...stance }, 'out'),
  P(8, sphHold, 'inout'),
  P(9, { ...sphHold, ra: { yaw: 8, pitch: -4 }, lean: 6 }, 'out'),
  P(11, sphUp, 'snap'),
  P(13, { ...sphUp, ra: { yaw: 6, pitch: 88 }, lean: -12 }, 'out'),
  P(20, sphUp, 'inout'),
  P(30, REST, 'inout'),
] };

// bills.destruction_touch: W30 E44 back5. Looks down at it, the finger pulls back a little, then pokes it.
const touchAim = { twist: 12, lean: 8, ra: { yaw: 6, pitch: -38 }, la: BACK, ...stance, head: { yaw: 0, pitch: 22 } };
const touchHit = { twist: -6, lean: 20, fwd: 1.5, drop: 1, ra: { yaw: 4, pitch: -54 }, la: BACK, ...stance, head: { yaw: 0, pitch: 34 } };
S['bills.destruction_touch'] = { keys: [
  P(0, REST),
  P(12, touchAim, 'inout'),
  P(27, { ...touchAim, twist: 18, lean: 4, ra: { yaw: 10, pitch: -22 } }, 'inout'),
  P(30, touchHit, 'snap'),
  P(32, { ...touchHit, lean: 24, ra: { yaw: 4, pitch: -60 } }, 'out'),
  P(43, touchHit, 'inout'),
  P(50, REST, 'inout'),
] };

// ===================== AIZEN =====================
// aizen.black_box: the hand rises at the target, waits while the box builds (soltar), and closes when it shuts.
const bbAim = { twist: 24, lean: 2, ra: { yaw: 8, pitch: 2 }, la: { yaw: 6, pitch: -90 }, ...stance, head: { yaw: 0, pitch: 6 } };
const bbShut = { twist: 14, lean: 6, ra: { yaw: -6, pitch: -8 }, la: { yaw: 6, pitch: -90 }, ...stance, head: { yaw: 0, pitch: 8 } };
S['aizen.black_box'] = { soltar: 10, keys: [
  P(0, REST),
  P(10, bbAim, 'inout'),
  P(11, { ...bbAim, ra: { yaw: 10, pitch: 6 } }, 'out'),
  P(13, bbShut, 'snap'),
  P(18, bbShut, 'inout'),
  P(28, REST, 'inout'),
] };

// aizen.dash: W2 E14 back8. Low and forward, the blade trailing out to the right.
const dashP = { twist: 24, lean: 28, drop: 2.2, ra: { yaw: 120, pitch: -30 }, la: { yaw: 30, pitch: -110 }, rl: { pitch: -34, out: 2 },
  ll: { pitch: 36, out: 2 }, head: { yaw: 0, pitch: -12 } };
S['aizen.dash'] = { keys: [
  P(0, REST),
  P(2, dashP, 'snap'),
  P(8, { ...dashP, drop: 2.8, lean: 30 }, 'inout'),
  P(14, dashP, 'inout'),
  P(22, REST, 'inout'),
] };

// aizen.hogyoku: W20 E140 back20. The evolution: chest out, chin up, arms opening down at his sides.
const hogy = { twist: 0, lean: -8, drop: -0.5, ra: { yaw: 90, pitch: -48 }, la: { yaw: 90, pitch: -48 }, rl: { pitch: 0, out: 5 },
  ll: { pitch: 0, out: 5 }, head: { yaw: 0, pitch: -20 } };
S['aizen.hogyoku'] = { keys: [
  P(0, REST),
  P(8, { drop: 1.2, lean: 6, ra: { yaw: 10, pitch: -84 }, la: { yaw: 10, pitch: -84 }, head: { yaw: 0, pitch: 12 } }, 'out'),
  P(20, hogy, 'inout'),
  P(80, { ...hogy, ra: { yaw: 90, pitch: -38 }, la: { yaw: 90, pitch: -38 }, head: { yaw: 0, pitch: -26 } }, 'inout'),
  P(138, { ...hogy, ra: { yaw: 90, pitch: -42 }, la: { yaw: 90, pitch: -42 } }, 'inout'),
  P(160, REST, 'inout'),
] };

// aizen.kyoka: W12 E18 back6. The sword up in front of the face, then let down: the illusion is cast.
const kyUp = { twist: 18, lean: -2, ra: { yaw: -16, pitch: 26 }, la: { yaw: 6, pitch: -90 }, ...stance, head: { yaw: 0, pitch: -2 } };
const kyDown = { twist: 6, lean: 8, ra: { yaw: 0, pitch: -40 }, la: { yaw: 6, pitch: -90 }, ...stance, head: { yaw: 0, pitch: 6 } };
S['aizen.kyoka'] = { keys: [
  P(0, REST),
  P(8, kyUp, 'inout'),
  P(11, { ...kyUp, ra: { yaw: -16, pitch: 32 } }, 'inout'),
  P(12, kyDown, 'snap'),
  P(14, { ...kyDown, ra: { yaw: 0, pitch: -48 } }, 'out'),
  P(18, kyDown, 'inout'),
  P(26, REST, 'inout'),
] };

// aizen.reiatsu: W4 E104 back8. The pressure: shoulders drop, then chest out, chin up, hands open at his sides.
const reia = { lean: -6, ...wide, drop: 1, ra: { yaw: 90, pitch: -66 }, la: { yaw: 90, pitch: -66 }, head: { yaw: 0, pitch: -10 } };
S['aizen.reiatsu'] = { keys: [
  P(0, REST),
  P(3, { drop: 0.8, lean: 6, ...stance, ra: { yaw: 6, pitch: -88 }, la: { yaw: 6, pitch: -88 }, head: { yaw: 0, pitch: 10 } }, 'out'),
  P(6, reia, 'snap'),
  P(60, { ...reia, ra: { yaw: 90, pitch: -60 }, la: { yaw: 90, pitch: -60 } }, 'inout'),
  P(102, reia, 'inout'),
  P(112, REST, 'inout'),
] };

// aizen.um_dedo: W3 E13 back6. One finger up, quick, the rest of him barely moves.
const dedo = { twist: 12, lean: 0, ra: { yaw: -6, pitch: -4 }, la: { yaw: 6, pitch: -90 }, ...stance, head: { yaw: 0, pitch: 4 } };
S['aizen.um_dedo'] = { keys: [
  P(0, REST),
  P(2, { ...dedo, ra: { yaw: -4, pitch: -16 } }, 'out'),
  P(3, dedo, 'snap'),
  P(12, dedo, 'inout'),
  P(19, REST, 'inout'),
] };

// aizen.um_dedo_corte: W12 E32 back14. One clean cut, no effort: the blade comes up by the right shoulder, then
// down across in one line with half a step, and stays there a moment before he lowers it.
const udUp = { twist: 20, lean: -2, ra: { yaw: 34, pitch: 52 }, la: { yaw: 6, pitch: -92 }, ...stance, head: { yaw: 0, pitch: 2 } };
const udDown = { twist: -18, lean: 8, drop: 1, fwd: 1, ra: { yaw: -34, pitch: -38 }, la: { yaw: 6, pitch: -92 }, rl: { pitch: 12, out: 3 },
  ll: { pitch: -8, out: 3 }, head: { yaw: 0, pitch: 6 } };
S['aizen.um_dedo_corte'] = { keys: [
  P(0, REST),
  P(9, udUp, 'inout'),
  P(11, { ...udUp, ra: { yaw: 36, pitch: 56 } }, 'inout'),
  P(12, udDown, 'snap'),
  P(14, { ...udDown, ra: { yaw: -40, pitch: -44 } }, 'out'),
  P(30, udDown, 'inout'),
  P(46, REST, 'inout'),
] };

// ===================== MADARA =====================
// madara.katon_gigante: W8 E88 back6. Hands together at the chest (the seal), then leaning back and blowing up and
// ahead, the arms up and out.
const kgSeal = { twist: 0, lean: 8, drop: 2, ...stance, ra: { yaw: -26, pitch: -10 }, la: { yaw: -26, pitch: -10 }, head: { yaw: 0, pitch: 10 } };
const kgBlow = { lean: -16, ...wide, drop: 1.8, ra: { yaw: 36, pitch: 50 }, la: { yaw: 36, pitch: 50 }, head: { yaw: 0, pitch: -30 } };
S['madara.katon_gigante'] = { keys: [
  P(0, REST),
  P(5, kgSeal, 'out'),
  P(7, { ...kgSeal, lean: 12 }, 'inout'),
  P(8, kgBlow, 'snap'),
  P(10, { ...kgBlow, lean: -20 }, 'out'),
  P(50, { ...kgBlow, lean: -14 }, 'inout'),
  P(86, kgBlow, 'inout'),
  P(96, REST, 'inout'),
] };

// madara.meteoro: preparo 20, queda 70, back6. Calls it: the arm straight up, looking up; then points where it falls.
function meteoro(fall, both) {
  const call = { lean: -6, ...stance, ra: { yaw: 10, pitch: 86 }, la: both ? { yaw: 10, pitch: 86 } : { yaw: 20, pitch: -80 }, head: { yaw: 0, pitch: -26 } };
  const point = { twist: 16, lean: 4, ...stance, ra: { yaw: 8, pitch: 34 }, la: both ? { yaw: 8, pitch: 34 } : { yaw: 20, pitch: -80 }, head: { yaw: 0, pitch: -10 } };
  return [
    P(0, REST),
    P(6, { drop: 1.2, lean: 6, ...stance, ra: { yaw: 10, pitch: -70 }, la: { yaw: 10, pitch: -80 } }, 'out'),
    P(18, call, 'inout'),
    P(20, { ...call, ra: { yaw: 10, pitch: 90 } }, 'inout'),
    P(23, point, 'snap'),
    P(26, { ...point, ra: { yaw: 8, pitch: 28 } }, 'out'),
    P(20 + fall - 2, point, 'inout'),
    P(20 + fall + 8, REST, 'inout'),
  ];
}
S['madara.meteoro'] = { keys: meteoro(70, false) };
S['madara.meteoro_duplo'] = { keys: meteoro(120, true) };

// madara.susanoo_corte: W9 E19 back5. He commands the Susanoo's blade with one wide sweep of the arm, out to the
// right and across, standing his ground (the Susanoo does the swinging).
const scOut = { twist: 14, ra: { yaw: 74, pitch: 8 }, la: { yaw: 10, pitch: -90 }, ...stance, head: { yaw: 0, pitch: 2 } };
const scAcross = { twist: -16, lean: 4, drop: 0.8, ra: { yaw: -46, pitch: -6 }, la: { yaw: 10, pitch: -90 }, ...stance, head: { yaw: 0, pitch: 4 } };
S['madara.susanoo_corte'] = { keys: [
  P(0, REST),
  P(7, scOut, 'inout'),
  P(9, scAcross, 'snap'),
  P(11, { ...scAcross, ra: { yaw: -52, pitch: -8 } }, 'out'),
  P(17, scAcross, 'inout'),
  P(24, REST, 'inout'),
] };

// ===================== SUBALMAS (Rukia, Byakuya) =====================
// sub.apontar: W4 E34 back8. Points the sword (or finger) at the target.
const apont = { twist: 30, lean: 4, ...stance, ra: { yaw: 6, pitch: 2 }, la: { yaw: 10, pitch: -88 }, head: { yaw: 0, pitch: 4 } };
S['sub.apontar'] = { keys: [
  P(0, REST),
  P(3, { ...apont, twist: 36, ra: { yaw: 24, pitch: -20 } }, 'out'),
  P(4, apont, 'snap'),
  P(6, { ...apont, ra: { yaw: 6, pitch: 4 } }, 'out'),
  P(32, apont, 'inout'),
  P(42, REST, 'inout'),
] };

// sub.apontar_baixo: W4 E20 back8. Raises the blade, then points it down at the ground in front.
const apBaixo = { twist: 10, lean: 14, drop: 1.6, ...stance, ra: { yaw: 4, pitch: -48 }, la: { yaw: 12, pitch: -84 }, head: { yaw: 0, pitch: 18 } };
S['sub.apontar_baixo'] = { keys: [
  P(0, REST),
  P(3, { twist: 16, lean: -4, ...stance, ra: { yaw: 6, pitch: 46 }, la: { yaw: 10, pitch: -86 }, head: { yaw: 0, pitch: -6 } }, 'out'),
  P(5, apBaixo, 'snap'),
  P(7, { ...apBaixo, lean: 16, ra: { yaw: 4, pitch: -54 } }, 'out'),
  P(19, apBaixo, 'inout'),
  P(28, REST, 'inout'),
] };

// sub.arco: W6 E22 back8. Side-on, left arm out holding the bow, the right hand draws back to the cheek.
const arcoAim = { twist: 70, lean: 0, rl: { pitch: -10, out: 6 }, ll: { pitch: 12, out: 6 }, ra: { yaw: -10, pitch: 8 }, la: { yaw: 4, pitch: 8 },
  head: { yaw: 0, pitch: 2 } };
const arcoDraw = { ...arcoAim, ra: { yaw: 70, pitch: 14 } };
S['sub.arco'] = { keys: [
  P(0, REST),
  P(5, arcoAim, 'out'),
  P(9, arcoDraw, 'inout'),
  P(20, { ...arcoDraw, ra: { yaw: 76, pitch: 14 } }, 'inout'),
  P(22, { ...arcoAim, ra: { yaw: 90, pitch: 4 } }, 'snap'),
  P(30, REST, 'inout'),
] };

// sub.corte: W5 E13 back8. A rising cut: the blade low on the left, then up across to high on the right.
const ctLow = { twist: -24, lean: 10, drop: 1.6, ra: { yaw: -40, pitch: -58 }, la: { yaw: 20, pitch: -80 }, rl: { pitch: -10, out: 4 },
  ll: { pitch: 14, out: 4 }, head: { yaw: 0, pitch: 6 } };
const ctHigh = { twist: 26, lean: -4, drop: 0.6, ra: { yaw: 52, pitch: 44 }, la: { yaw: 30, pitch: -70 }, rl: { pitch: 8, out: 4 },
  ll: { pitch: -6, out: 4 }, head: { yaw: 0, pitch: -2 } };
S['sub.corte'] = { keys: [
  P(0, REST),
  P(4, ctLow, 'out'),
  P(5, ctHigh, 'snap'),
  P(7, { ...ctHigh, twist: 30, ra: { yaw: 58, pitch: 50 } }, 'out'),
  P(12, ctHigh, 'inout'),
  P(21, REST, 'inout'),
] };

// sub.cravar: W6 E16 back8. Both hands up over the head on the hilt, then drives it down into the ground.
const cravUp = { lean: -10, drop: -0.5, ...stance, ra: { yaw: -6, pitch: 84 }, la: { yaw: -6, pitch: 84 }, head: { yaw: 0, pitch: -14 } };
const cravDown = { lean: 30, drop: 3.4, fwd: 1, rl: { pitch: -18, out: 4 }, ll: { pitch: 30, out: 4 }, ra: { yaw: -8, pitch: -64 },
  la: { yaw: -8, pitch: -64 }, head: { yaw: 0, pitch: 22 } };
S['sub.cravar'] = { keys: [
  P(0, REST),
  P(5, cravUp, 'out'),
  P(6, cravDown, 'snap'),
  P(8, { ...cravDown, drop: 3.8, lean: 34 }, 'out'),
  P(15, cravDown, 'inout'),
  P(24, REST, 'inout'),
] };

// sub.defender: W3 E27 back8. The blade held across in front, both hands, feet set.
const defP = { twist: 10, lean: 6, ...wide, ra: { yaw: -26, pitch: 24 }, la: { yaw: -30, pitch: 22 }, head: { yaw: 0, pitch: 6 } };
S['sub.defender'] = { keys: [
  P(0, REST),
  P(3, defP, 'snap'),
  P(5, { ...defP, drop: 2 }, 'out'),
  P(26, defP, 'inout'),
  P(35, REST, 'inout'),
] };

// sub.erguer: W6 E26 back8. Lifts the blade straight up over the head, chest out.
const ergP = { lean: -8, ...stance, ra: { yaw: 4, pitch: 88 }, la: { yaw: 16, pitch: -84 }, head: { yaw: 0, pitch: -16 } };
S['sub.erguer'] = { keys: [
  P(0, REST),
  P(4, { lean: 4, drop: 1, ...stance, ra: { yaw: 6, pitch: 20 }, la: { yaw: 14, pitch: -86 } }, 'out'),
  P(6, ergP, 'snap'),
  P(8, { ...ergP, lean: -10 }, 'out'),
  P(25, ergP, 'inout'),
  P(34, REST, 'inout'),
] };

// sub.estocada: W4 E14 back8. A fencer's thrust: side-on, right foot leading, the left hand up behind for balance.
const fenGuard = { twist: -50, lean: 2, drop: 1.6, ra: { yaw: 14, pitch: -20 }, la: { yaw: 70, pitch: 10 }, rl: { pitch: 14, out: 3 },
  ll: { pitch: -16, out: 3 }, head: { yaw: 0, pitch: 4 } };
const fenLunge = { twist: -64, lean: 10, drop: 3, fwd: 3, ra: { yaw: 2, pitch: 0 }, la: { yaw: 80, pitch: 30 }, rl: { pitch: 40, out: 2 },
  ll: { pitch: -36, out: 2 }, head: { yaw: 0, pitch: 4 } };
S['sub.estocada'] = { keys: [
  P(0, REST),
  P(3, fenGuard, 'out'),
  P(5, fenLunge, 'snap'),
  P(7, { ...fenLunge, fwd: 3.6 }, 'out'),
  P(13, fenLunge, 'inout'),
  P(22, REST, 'inout'),
] };

// sub.gesto: W2 E8 back8. A flick of the hand out to the side.
S['sub.gesto'] = { keys: [
  P(0, REST),
  P(2, { twist: -10, ...stance, ra: { yaw: -30, pitch: 0 }, la: { yaw: 8, pitch: -88 }, head: { yaw: 0, pitch: 4 } }, 'out'),
  P(4, { twist: 12, ...stance, ra: { yaw: 40, pitch: 4 }, la: { yaw: 8, pitch: -88 }, head: { yaw: 0, pitch: 4 } }, 'snap'),
  P(8, { twist: 10, ...stance, ra: { yaw: 36, pitch: 0 }, la: { yaw: 8, pitch: -88 } }, 'inout'),
  P(16, REST, 'inout'),
] };

// sub.maos: W6 E30 back8. Hands brought together in front of the chest, head bowed a little.
const maosP = { lean: 6, ...stance, drop: 0.8, ra: { yaw: -24, pitch: -6 }, la: { yaw: -24, pitch: -6 }, head: { yaw: 0, pitch: 10 } };
S['sub.maos'] = { keys: [
  P(0, REST),
  P(4, { ...maosP, ra: { yaw: 10, pitch: -20 }, la: { yaw: 10, pitch: -20 } }, 'out'),
  P(6, maosP, 'snap'),
  P(28, { ...maosP, head: { yaw: 0, pitch: 6 } }, 'inout'),
  P(38, REST, 'inout'),
] };

// sub.mascara: W4 E18 back8. The hand comes up over the face, the head bows into it, then lifts.
const masc = { twist: 10, lean: 6, ...stance, ra: { yaw: -40, pitch: 50 }, la: { yaw: 10, pitch: -86 }, head: { yaw: 0, pitch: 12 } };
S['sub.mascara'] = { keys: [
  P(0, REST),
  P(4, masc, 'out'),
  P(8, { ...masc, lean: 2, head: { yaw: 0, pitch: -4 } }, 'inout'),
  P(17, { ...masc, lean: 2, head: { yaw: 0, pitch: -6 } }, 'inout'),
  P(26, REST, 'inout'),
] };

// sub.palma: W3 E27 back8. Both palms out at the target.
const palmP = { lean: 10, ...wide, drop: 1.8, ra: { yaw: 14, pitch: 4 }, la: { yaw: 14, pitch: 4 }, head: { yaw: 0, pitch: 4 } };
S['sub.palma'] = { keys: [
  P(0, REST),
  P(2, { lean: -4, drop: 1, ...stance, ra: { yaw: 20, pitch: -30 }, la: { yaw: 20, pitch: -30 } }, 'out'),
  P(3, palmP, 'snap'),
  P(5, { ...palmP, lean: 14 }, 'out'),
  P(26, palmP, 'inout'),
  P(35, REST, 'inout'),
] };

// sub.sinal: W6 E36 back8. A hand sign held up in front of the chest.
const sinalP = { lean: 4, ...stance, ra: { yaw: -22, pitch: 10 }, la: { yaw: -22, pitch: 10 }, head: { yaw: 0, pitch: 6 } };
S['sub.sinal'] = { keys: [
  P(0, REST),
  P(5, { ...sinalP, ra: { yaw: -10, pitch: -10 }, la: { yaw: -10, pitch: -10 } }, 'out'),
  P(6, sinalP, 'snap'),
  P(34, { ...sinalP, head: { yaw: 0, pitch: 2 } }, 'inout'),
  P(44, REST, 'inout'),
] };

// sub.soltar: W8 E32 back8. The blade held out pointing down, then let go of (it scatters into petals).
const soltHold = { twist: 16, lean: 2, ...stance, ra: { yaw: 6, pitch: -12 }, la: { yaw: 10, pitch: -88 }, head: { yaw: 0, pitch: 8 } };
const soltGo = { twist: 12, lean: 4, ...stance, ra: { yaw: 8, pitch: -24 }, la: { yaw: 10, pitch: -88 }, head: { yaw: 0, pitch: 16 } };
S['sub.soltar'] = { keys: [
  P(0, REST),
  P(6, soltHold, 'inout'),
  P(8, soltGo, 'snap'),
  P(30, soltGo, 'inout'),
  P(40, REST, 'inout'),
] };

// sub.varrer: W3 E13 back8. The open hand sweeps across the air from right to left.
const varA = { twist: 26, ...stance, ra: { yaw: 70, pitch: 2 }, la: { yaw: 10, pitch: -86 }, head: { yaw: 0, pitch: 4 } };
const varB = { twist: -26, lean: 8, ...stance, ra: { yaw: -54, pitch: 4 }, la: { yaw: 10, pitch: -86 }, head: { yaw: 0, pitch: 4 } };
S['sub.varrer'] = { keys: [
  P(0, REST),
  P(2, varA, 'out'),
  P(4, varB, 'snap'),
  P(6, { ...varB, twist: -32, ra: { yaw: -62, pitch: 4 } }, 'out'),
  P(12, varB, 'inout'),
  P(21, REST, 'inout'),
] };

for (const k in S) S[k].keys = S[k].keys.map(fix);
module.exports = S;
