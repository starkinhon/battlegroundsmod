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


// ===================== SUBALMAS DE DRAGON BALL (Goku, Vegeta, Freeza, Piccolo, Kuririn, Mestre Kame) =====================
// Held poses stay at their peak for the longest the skill can be held; the server sends the next pose (the push, the
// throw) when it goes off. Each one follows the character: Goku open and loose, Vegeta stiff and proud, Freeza small and
// elegant gestures, Piccolo serious and economic, Kuririn quick, Kame a crooked old man.
// hold(): from rest, a short wind-up, the peak in 1-2 ticks, held to `end`, back to rest in `back`.
function hold(peak, wind, end, back, pre) {
  const keys = [P(0, REST)];
  if (pre) keys.push(P(Math.max(1, wind - 2), pre, 'out'));
  keys.push(P(wind, peak, pre ? 'snap' : 'out'));
  keys.push(P(end, peak, 'inout'));
  keys.push(P(end + back, REST, 'inout'));
  return { keys };
}
// db.concha: W4 E70 back8. Cupped hands at the right hip ("Ka-me-ha-me..."), body turned, knees bent.
const dbCup = { twist: 34, lean: 4, ...wide, ra: { yaw: 30, pitch: -62 }, la: { yaw: -40, pitch: -60 }, head: { yaw: -20, pitch: 4 } };
S['db.concha'] = hold(dbCup, 5, 70, 8, { ...dbCup, twist: 18, ra: { yaw: 20, pitch: -40 }, la: { yaw: -20, pitch: -40 } });
// db.empurra: W2 E70 back10. Both hands thrust forward, palms out ("HA!"), feet set wide.
const dbPush = { twist: 0, lean: 10, ...wide, ra: { yaw: -6, pitch: 4 }, la: { yaw: -6, pitch: 4 }, head: { yaw: 0, pitch: 0 } };
S['db.empurra'] = hold(dbPush, 2, 70, 10, { ...dbPush, lean: 14, ra: { yaw: -10, pitch: -10 }, la: { yaw: -10, pitch: -10 } });
// db.testa: W5 E64 back8. Two fingers on the forehead, eyes closed, the other arm loose.
const dbBrow = { lean: 2, ...stance, ra: { yaw: -46, pitch: 64 }, la: { yaw: 8, pitch: -86 }, head: { yaw: 0, pitch: 8 } };
S['db.testa'] = hold(dbBrow, 5, 64, 8);
// db.grito: W3 E20 back8. Fists clenched down at the sides, chest out, head up: the Kaioken's shout.
const dbShout = { lean: -8, ...wide, ra: { yaw: 22, pitch: -70 }, la: { yaw: 22, pitch: -70 }, head: { yaw: 0, pitch: -14 } };
S['db.grito'] = hold(dbShout, 3, 20, 8, { ...dbShout, lean: 8, head: { yaw: 0, pitch: 10 } });
// db.ceu: W6 E120 back12. Both arms straight up to the sky (the Genki Dama), head back.
const dbSky = { lean: -6, ...stance, ra: { yaw: 14, pitch: 84 }, la: { yaw: 14, pitch: 84 }, head: { yaw: 0, pitch: -30 } };
S['db.ceu'] = hold(dbSky, 6, 120, 12);
// db.palma_dir: W3 E40 back8. The right arm straight out, palm open, the left hand holding the wrist.
const dbPalm = { twist: 24, lean: 4, ...stance, ra: { yaw: 4, pitch: 2 }, la: { yaw: -30, pitch: -4 }, head: { yaw: 0, pitch: 2 } };
S['db.palma_dir'] = hold(dbPalm, 3, 40, 8, { ...dbPalm, twist: 34, ra: { yaw: 30, pitch: -30 } });
// db.bracos_abertos: W6 E84 back10. Arms spread wide to the sides (the Final Flash gathering), chest out.
const dbOpen = { lean: -4, ...wide, ra: { yaw: 84, pitch: 6 }, la: { yaw: 84, pitch: 6 }, head: { yaw: 0, pitch: -4 } };
S['db.bracos_abertos'] = hold(dbOpen, 6, 84, 10);
// db.bracos_abertos_curto: W3 E14 back8. The same, quick: Piccolo closing the Hellzone.
S['db.bracos_abertos_curto'] = hold({ ...dbOpen, ra: { yaw: 70, pitch: 20 }, la: { yaw: 70, pitch: 20 } }, 3, 14, 8);
// db.apontar: W3 E40 back8. Freeza's finger pointed at the target, the left hand on the hip.
const dbPoint = { twist: 28, ...stance, ra: { yaw: 4, pitch: 4 }, la: { yaw: 30, pitch: -110 }, head: { yaw: 0, pitch: 2 } };
S['db.apontar'] = hold(dbPoint, 3, 40, 8);
// db.mao_alto: W4 E70 back8. One hand raised open in front, fingers spread (the telekinesis), head tilted.
const dbLift = { twist: 16, lean: -2, ...stance, ra: { yaw: 6, pitch: 30 }, la: { yaw: 30, pitch: -110 }, head: { yaw: 6, pitch: -4 } };
S['db.mao_alto'] = hold(dbLift, 4, 70, 8);
// db.gesto_baixo: W2 E12 back8. The raised hand flicks down: the one held is thrown at the ground.
S['db.gesto_baixo'] = { keys: [P(0, dbLift), P(2, { ...dbLift, ra: { yaw: 6, pitch: 50 } }, 'out'), P(4, { ...dbLift, lean: 6, ra: { yaw: 4, pitch: -40 } }, 'snap'), P(12, { ...dbLift, ra: { yaw: 4, pitch: -46 } }, 'inout'), P(20, REST, 'inout')] };
// db.dedo_ceu: W4 E26 back8. One finger up over the head (the Death Saucer grows on it).
const dbFinger = { lean: -2, ...stance, ra: { yaw: 8, pitch: 88 }, la: { yaw: 30, pitch: -110 }, head: { yaw: 0, pitch: -12 } };
S['db.dedo_ceu'] = hold(dbFinger, 4, 26, 8);
// db.dedo_ceu_longo: W6 E112 back10. The same, held for the whole Death Ball.
S['db.dedo_ceu_longo'] = hold(dbFinger, 6, 112, 10);
// db.arremesso: W2 E12 back8. The raised hand swings forward and down: something thrown.
const dbThrowUp = { twist: 20, lean: -4, ...stance, ra: { yaw: 10, pitch: 96 }, la: { yaw: 10, pitch: -86 }, head: { yaw: 0, pitch: -4 } };
const dbThrow = { twist: -14, lean: 12, ...stance, ra: { yaw: -6, pitch: -10 }, la: { yaw: 14, pitch: -80 }, head: { yaw: 0, pitch: 4 } };
S['db.arremesso'] = { keys: [P(0, dbThrowUp), P(2, dbThrow, 'snap'), P(4, { ...dbThrow, lean: 14, ra: { yaw: -10, pitch: -24 } }, 'out'), P(12, dbThrow, 'inout'), P(20, REST, 'inout')] };
// db.gesto: W2 E12 back8. A small flick of two fingers down: the Death Ball let go.
S['db.gesto'] = { keys: [P(0, dbFinger), P(2, { ...dbFinger, ra: { yaw: 6, pitch: 30 } }, 'snap'), P(12, { ...dbFinger, ra: { yaw: 6, pitch: 20 } }, 'inout'), P(20, REST, 'inout')] };
// db.joelhada: W2 E10 back8. Piccolo's knee up into the one pulled in, arms back.
const dbKnee = { lean: -6, rl: { pitch: 80, out: 2 }, ll: { pitch: -6, out: 3 }, ra: { yaw: 20, pitch: -110 }, la: { yaw: 20, pitch: -110 }, head: { yaw: 0, pitch: 10 } };
S['db.joelhada'] = hold(dbKnee, 2, 10, 8);
// db.disco_alto: W5 E32 back8. Kuririn's right arm straight up, palm flat to the sky: the Kienzan forms on it.
const dbDisc = { lean: -2, ...stance, ra: { yaw: 6, pitch: 86 }, la: { yaw: 10, pitch: -86 }, head: { yaw: 0, pitch: -8 } };
S['db.disco_alto'] = hold(dbDisc, 5, 32, 8);
// db.rosto: W4 E22 back8. Both hands open beside the face, fingers spread ("Taiyoken!").
const dbFace = { lean: -4, ...stance, ra: { yaw: -30, pitch: 70 }, la: { yaw: -30, pitch: 70 }, head: { yaw: 0, pitch: -6 } };
S['db.rosto'] = hold(dbFace, 4, 22, 8);
// db.lanca_alto: W3 E18 back8. A ki ball thrown straight up, the arm following through, head up.
const dbToss = { lean: -10, ...stance, ra: { yaw: 4, pitch: 70 }, la: { yaw: 10, pitch: -86 }, head: { yaw: 0, pitch: -26 } };
S['db.lanca_alto'] = hold(dbToss, 3, 18, 8, { ...dbToss, lean: 4, ra: { yaw: 10, pitch: -40 } });
// db.bebado: W3 E12 back4. The drunken sway: leaning to one side, arms loose and crooked (sent again every 12 ticks).
const dbDrunk = { twist: 14, lean: 8, rl: { pitch: -14, out: 6 }, ll: { pitch: 18, out: 2 }, ra: { yaw: 36, pitch: -30 }, la: { yaw: -10, pitch: -50 }, head: { yaw: 16, pitch: 12 } };
S['db.bebado'] = { keys: [P(0, REST), P(4, dbDrunk, 'inout'), P(10, { ...dbDrunk, twist: -12, ra: { yaw: 10, pitch: -60 }, la: { yaw: 30, pitch: -20 }, head: { yaw: -16, pitch: 14 } }, 'inout'), P(16, REST, 'inout')] };
// db.palma_torta: W2 E8 back6. A crooked palm from below, body leaning the wrong way.
const dbSlap = { twist: -20, lean: 16, ...stance, ra: { yaw: -10, pitch: 10 }, la: { yaw: 40, pitch: -40 }, head: { yaw: 12, pitch: 8 } };
S['db.palma_torta'] = hold(dbSlap, 2, 8, 6, { ...dbSlap, twist: 20, ra: { yaw: 30, pitch: -50 } });
// db.flex: W4 E40 back10. Max Power: arms up and bent, fists clenched, chest out, knees bent.
const dbFlex = { lean: -6, ...wide, ra: { yaw: 70, pitch: 50 }, la: { yaw: 70, pitch: 50 }, head: { yaw: 0, pitch: -10 } };
S['db.flex'] = hold(dbFlex, 4, 40, 10, { ...dbFlex, lean: 10, ra: { yaw: 30, pitch: -70 }, la: { yaw: 30, pitch: -70 } });
// db.mafuba: W6 E86 back10. Both open hands pushed forward and a bit apart, leaning in against the wind.
const dbSeal = { lean: 14, ...wide, ra: { yaw: 18, pitch: 0 }, la: { yaw: 18, pitch: 0 }, head: { yaw: 0, pitch: 0 } };
S['db.mafuba'] = hold(dbSeal, 6, 86, 10);
// db.ajoelhado: W8 E60 back12. Spent after the Mafuba: down on one knee, head hanging, a hand on the ground.
const dbKneel = { lean: 30, drop: 7, rl: { pitch: 80, out: 3 }, ll: { pitch: -40, out: 3 }, ra: { yaw: 6, pitch: -60 }, la: { yaw: 10, pitch: -84 }, head: { yaw: 0, pitch: 30 } };
S['db.ajoelhado'] = hold(dbKneel, 8, 60, 12);

// ===================== SUBALMAS DE NARUTO (Naruto, Sasuke, Kakashi, Gaara, Shikamaru, Ino) =====================
// Each one follows the character: Naruto loud and wide open, Sasuke cold and economic, Kakashi relaxed until he moves,
// Gaara still (only the sand moves), Shikamaru lazy and low, Ino light and precise.
// nr.molda: W4 E44 back8. Naruto's two hands cupped in front of the chest, the Rasengan turning between them.
const nrCup = { twist: 12, lean: 8, ...wide, ra: { yaw: -12, pitch: -22 }, la: { yaw: -26, pitch: -6 }, head: { yaw: 0, pitch: 12 } };
S['nr.molda'] = hold(nrCup, 4, 44, 8, { ...nrCup, lean: 2, ra: { yaw: -4, pitch: -50 }, la: { yaw: -10, pitch: -40 } });
// nr.investida: W2 E20 back8. Dashing in: the right arm driven forward, body low, the left arm thrown back.
const nrDash = { twist: 22, lean: 24, rl: { pitch: 34, out: 3 }, ll: { pitch: -34, out: 3 }, ra: { yaw: -4, pitch: 2 }, la: { yaw: 20, pitch: -124 }, head: { yaw: 0, pitch: -10 } };
S['nr.investida'] = hold(nrDash, 2, 20, 8);
// nr.selo_cruz: W3 E18 back8. The cross seal in front of the chest ("Kage Bunshin no Jutsu!").
const nrCross = { lean: 4, ...wide, ra: { yaw: -40, pitch: -28 }, la: { yaw: -40, pitch: -22 }, head: { yaw: 0, pitch: 6 } };
S['nr.selo_cruz'] = hold(nrCross, 3, 18, 8);
// nr.chute_alto: W2 E10 back8. A clone's kick straight up under the chin, arms flung back.
const nrUpKick = { lean: -18, rl: { pitch: 112, out: 2 }, ll: { pitch: -8, out: 3 }, ra: { yaw: 30, pitch: -120 }, la: { yaw: 30, pitch: -120 }, head: { yaw: 0, pitch: -16 } };
S['nr.chute_alto'] = hold(nrUpKick, 2, 10, 8, { ...nrUpKick, lean: 8, rl: { pitch: 30, out: 3 } });
// nr.salto: W3 E16 back6. In the air over the target: knees tucked, arms up for balance.
const nrJump = { lean: -6, rl: { pitch: 64, out: 4 }, ll: { pitch: 52, out: 4 }, ra: { yaw: 50, pitch: 30 }, la: { yaw: 50, pitch: 30 }, head: { yaw: 0, pitch: 20 } };
S['nr.salto'] = hold(nrJump, 3, 16, 6);
// nr.calcanhar: W2 E10 back10. The heel brought down from over the head ("Uzumaki Naruto Rendan!").
const nrHeel = { lean: 20, rl: { pitch: 18, out: 2 }, ll: { pitch: -10, out: 3 }, ra: { yaw: 40, pitch: -60 }, la: { yaw: 40, pitch: -60 }, head: { yaw: 0, pitch: 24 } };
S['nr.calcanhar'] = hold(nrHeel, 2, 10, 10, { ...nrHeel, lean: -10, rl: { pitch: 150, out: 2 }, ra: { yaw: 50, pitch: 20 }, la: { yaw: 50, pitch: 20 } });
// nr.meditar: W8 E88 back12. Sitting cross-legged gathering nature energy, hands together in the lap, head bowed.
const nrSit = { lean: 4, drop: 9, rl: { pitch: 84, out: 16 }, ll: { pitch: 84, out: 16 }, ra: { yaw: -30, pitch: -52 }, la: { yaw: -30, pitch: -52 }, head: { yaw: 0, pitch: 12 } };
S['nr.meditar'] = hold(nrSit, 8, 88, 12);
// nr.mao_alto: W4 E60 back8. One arm straight up holding the jutsu over the head (the Rasenshuriken, the Kirin's call).
const nrUp = { lean: -6, ...wide, ra: { yaw: 8, pitch: 88 }, la: { yaw: 24, pitch: -96 }, head: { yaw: 0, pitch: -14 } };
S['nr.mao_alto'] = hold(nrUp, 4, 60, 8);
// nr.chidori: W4 E62 back8. Sasuke low, the right hand down and back crackling, the left hand gripping the wrist.
const nrChidori = { twist: 32, lean: 16, ...wide, ra: { yaw: 18, pitch: -70 }, la: { yaw: -34, pitch: -42 }, head: { yaw: -10, pitch: 6 } };
S['nr.chidori'] = hold(nrChidori, 4, 62, 8);
// nr.leitura: W3 E22 back8. A cold guard: one hand loose in front, weight back, the Sharingan reading.
const nrRead = { twist: 14, lean: -2, ...stance, ra: { yaw: -16, pitch: -36 }, la: { yaw: -6, pitch: -64 }, head: { yaw: 0, pitch: 4 } };
S['nr.leitura'] = hold(nrRead, 3, 22, 8);
// nr.chute_giro: W2 E10 back8. Sasuke's spinning kick from behind, arms out.
const nrSpin = { twist: -60, lean: -12, rl: { pitch: 80, out: 34 }, ll: { pitch: -4, out: 3 }, ra: { yaw: 70, pitch: -20 }, la: { yaw: 70, pitch: -20 }, head: { yaw: 0, pitch: 4 } };
S['nr.chute_giro'] = hold(nrSpin, 2, 10, 8, { ...nrSpin, twist: 30, rl: { pitch: 20, out: 10 } });
// nr.senbon: W2 E14 back8. The needles flicked out from across the body in one tight motion.
const nrNeedle = { twist: -20, lean: 6, ...stance, ra: { yaw: 30, pitch: 2 }, la: { yaw: 10, pitch: -80 }, head: { yaw: 0, pitch: 2 } };
S['nr.senbon'] = hold(nrNeedle, 2, 14, 8, { ...nrNeedle, twist: 30, ra: { yaw: -50, pitch: -14 } });
// nr.selo: W3 E110 back8. The tiger seal in front of the chest, still: Sasuke calling the storm, Kakashi reading a move.
const nrTiger = { lean: 2, ...stance, ra: { yaw: -36, pitch: -34 }, la: { yaw: -36, pitch: -34 }, head: { yaw: 0, pitch: 6 } };
S['nr.selo'] = hold(nrTiger, 3, 110, 8);
// nr.mao_aberta: W3 E100 back8. Gaara's arm out, palm open toward the target; the rest of him doesn't move.
const nrOpen = { twist: 10, ...stance, ra: { yaw: 6, pitch: 4 }, la: { yaw: 6, pitch: -88 }, head: { yaw: 0, pitch: 0 } };
S['nr.mao_aberta'] = hold(nrOpen, 3, 100, 8);
// nr.punho: W1 E14 back8. The open hand closes into a fist ("Sabaku Soso").
const nrFist = { ...nrOpen, lean: 4, ra: { yaw: 4, pitch: 12 } };
S['nr.punho'] = { keys: [P(0, nrOpen), P(1, { ...nrOpen, ra: { yaw: 8, pitch: 2 } }, 'out'), P(2, nrFist, 'snap'), P(14, nrFist, 'inout'), P(22, REST, 'inout')] };
// nr.bracos_cruzados: W4 E40 back8. Arms crossed over the chest while the sand gathers under his feet.
const nrArms = { lean: -2, ...stance, ra: { yaw: -62, pitch: -24 }, la: { yaw: -62, pitch: -30 }, head: { yaw: 0, pitch: 2 } };
S['nr.bracos_cruzados'] = hold(nrArms, 4, 40, 8);
// nr.rato: W3 E130 back8. Shikamaru's rat seal, hands low in front, knees bent, head down.
const nrRat = { lean: 8, ...wide, ra: { yaw: -34, pitch: -52 }, la: { yaw: -34, pitch: -52 }, head: { yaw: 0, pitch: 10 } };
S['nr.rato'] = hold(nrRat, 3, 130, 8);
// nr.losango: W4 E40 back8. Ino's diamond of hands aimed straight at the target (Shintenshin).
const nrDiamond = { ...stance, ra: { yaw: -22, pitch: -2 }, la: { yaw: -22, pitch: -2 }, head: { yaw: 0, pitch: 0 } };
S['nr.losango'] = hold(nrDiamond, 4, 40, 8);
// nr.caida: W6 E90 back12. Her body left behind: slumped forward, arms hanging, head down.
const nrSlump = { lean: 28, drop: 3, rl: { pitch: 14, out: 4 }, ll: { pitch: -6, out: 4 }, ra: { yaw: 6, pitch: -82 }, la: { yaw: 6, pitch: -82 }, head: { yaw: 0, pitch: 40 } };
S['nr.caida'] = hold(nrSlump, 6, 90, 12);
// nr.testa_aponta: W3 E20 back8. Two fingers at the temple, then pointed out: the mind wave.
const nrTemple = { lean: 0, ...stance, ra: { yaw: -46, pitch: 64 }, la: { yaw: 8, pitch: -86 }, head: { yaw: 0, pitch: 4 } };
const nrPointOut = { twist: 14, lean: 6, ...stance, ra: { yaw: 2, pitch: 4 }, la: { yaw: 8, pitch: -86 }, head: { yaw: 0, pitch: 0 } };
S['nr.testa_aponta'] = hold(nrPointOut, 3, 20, 8, nrTemple);
// nr.fios: W3 E14 back8. Arms spread, fingers pulling the chakra threads between her enemies tight.
const nrThreads = { lean: -2, ...wide, ra: { yaw: 64, pitch: -8 }, la: { yaw: 64, pitch: -8 }, head: { yaw: 0, pitch: -4 } };
S['nr.fios'] = hold(nrThreads, 3, 14, 8);
// nr.raikiri: W4 E40 back6. Kakashi crouched, the right hand at chest height, the left holding the forearm.
const nrRaikiri = { twist: 20, lean: 14, ...wide, ra: { yaw: -4, pitch: -30 }, la: { yaw: -42, pitch: -40 }, head: { yaw: 0, pitch: 8 } };
S['nr.raikiri'] = hold(nrRaikiri, 4, 40, 6);
// nr.mao_chao: W2 E14 back8. The lightning hand driven into the ground, body bent over it.
const nrGround = { twist: 16, lean: 40, drop: 4, rl: { pitch: 50, out: 6 }, ll: { pitch: -20, out: 6 }, ra: { yaw: 0, pitch: -66 }, la: { yaw: 30, pitch: -110 }, head: { yaw: 0, pitch: 10 } };
S['nr.mao_chao'] = hold(nrGround, 2, 14, 8);
// nr.bandana: W4 E62 back8. The left hand lifts the headband off the Sharingan eye; the right hangs loose.
const nrBand = { lean: -2, ...stance, ra: { yaw: 10, pitch: -84 }, la: { yaw: -30, pitch: 76 }, head: { yaw: 0, pitch: -2 } };
S['nr.bandana'] = hold(nrBand, 4, 62, 8);

for (const k in S) S[k].keys = S[k].keys.map(fix);

// ===================== SUBALMAS DE JUJUTSU (Itadori, Choso, Nanami, Nobara, Todo, Inumaki, Miwa) =====================
// Each one follows the character: Itadori all body and energy, Choso calm and exact, Nanami formal (no wasted motion),
// Nobara cocky with the hammer, Todo huge and brash, Inumaki one hand always at the collar, Miwa the iaido stance.
// jj.div_punch: W5 E12 back8. Itadori's straight right, pulled back first, the whole body behind it.
const jjPunch = { twist: -26, lean: 14, ...wide, ra: { yaw: -4, pitch: 2 }, la: { yaw: 20, pitch: -70 }, head: { yaw: 0, pitch: 0 } };
S['jj.div_punch'] = hold(jjPunch, 5, 12, 8, { ...jjPunch, twist: 30, lean: 4, ra: { yaw: 30, pitch: -70 }, la: { yaw: -20, pitch: -30 } });
// jj.bf_ready: W3 E18 back6. The fist cocked at the hip, knees bent, eyes on the target: waiting for the ring to close.
const jjCock = { twist: 30, lean: 8, ...wide, ra: { yaw: 34, pitch: -74 }, la: { yaw: -26, pitch: -24 }, head: { yaw: -14, pitch: 4 } };
S['jj.bf_ready'] = hold(jjCock, 3, 18, 6);
// jj.bf_punch: W1 E18 back8. The Black Flash: the arm all the way out and held there through the lightning.
const jjBlack = { twist: -34, lean: 18, ...wide, drop: 2.2, ra: { yaw: -2, pitch: 6 }, la: { yaw: 30, pitch: -64 }, head: { yaw: 6, pitch: -2 } };
S['jj.bf_punch'] = hold(jjBlack, 1, 18, 8);
// jj.manji_guard: W2 E20 back6. Forearms crossed high in front of the face, weight on the back foot.
const jjGuard = { lean: -4, ...stance, ra: { yaw: -46, pitch: 24 }, la: { yaw: -46, pitch: 30 }, head: { yaw: 0, pitch: 8 } };
S['jj.manji_guard'] = hold(jjGuard, 2, 20, 6);
// jj.manji_kick: W2 E8 back8. The high kick out of the guard, the leg straight up, arms flung out for balance.
const jjKick = { twist: -20, lean: -20, rl: { pitch: 120, out: 4 }, ll: { pitch: -6, out: 3 }, ra: { yaw: 50, pitch: -40 }, la: { yaw: 50, pitch: -30 }, head: { yaw: 0, pitch: -6 } };
S['jj.manji_kick'] = hold(jjKick, 2, 8, 8, { ...jjKick, lean: 0, rl: { pitch: 40, out: 3 } });
// jj.dash_charge: W3 E14 back4. Down like a sprinter in the blocks, one hand nearly on the ground.
const jjSet = { twist: 10, lean: 40, drop: 6, rl: { pitch: -40, out: 4 }, ll: { pitch: 50, out: 4 }, ra: { yaw: 10, pitch: -110 }, la: { yaw: 6, pitch: -40 }, head: { yaw: 0, pitch: -30 } };
S['jj.dash_charge'] = hold(jjSet, 3, 14, 4);
// jj.dash_run: W1 E14 back4. Running flat out, leaning far forward, the arms pumping.
const jjRun = { lean: 34, rl: { pitch: 50, out: 3 }, ll: { pitch: -40, out: 3 }, ra: { yaw: 4, pitch: -130 }, la: { yaw: 4, pitch: -30 }, head: { yaw: 0, pitch: -24 } };
S['jj.dash_run'] = hold(jjRun, 1, 14, 4);
// jj.dash_knee: W1 E8 back10. The flying knee: the knee driven up into the target, both arms thrown back.
const jjKnee = { lean: 10, rl: { pitch: 104, out: 2 }, ll: { pitch: -20, out: 3 }, ra: { yaw: 20, pitch: -140 }, la: { yaw: 20, pitch: -140 }, head: { yaw: 0, pitch: 6 } };
S['jj.dash_knee'] = hold(jjKnee, 1, 8, 10);
// jj.blood_charge: W4 E62 back8. Choso's palms pressed together in front of the chest, still, the blood compressing.
const jjPalms = { lean: 2, ...stance, ra: { yaw: -40, pitch: -14 }, la: { yaw: -40, pitch: -14 }, head: { yaw: 0, pitch: 6 } };
S['jj.blood_charge'] = hold(jjPalms, 4, 62, 8);
// jj.blood_shoot: W1 E10 back8. The palms open forward, the line already gone.
const jjShoot = { lean: -4, ...stance, ra: { yaw: -16, pitch: 4 }, la: { yaw: -16, pitch: 2 }, head: { yaw: 0, pitch: 0 } };
S['jj.blood_shoot'] = hold(jjShoot, 1, 10, 8);
// jj.supernova: W3 E12 back6. The hands part slowly from the chest and the orbs leave them.
const jjOrbs = { lean: -2, ...stance, ra: { yaw: 40, pitch: -30 }, la: { yaw: 40, pitch: -30 }, head: { yaw: 0, pitch: 4 } };
S['jj.supernova'] = hold(jjOrbs, 3, 12, 6, jjPalms);
// jj.supernova_burst: W1 E8 back8. Both arms flung wide: the orbs burst.
const jjBurst = { lean: -6, ...wide, ra: { yaw: 80, pitch: -10 }, la: { yaw: 80, pitch: -10 }, head: { yaw: 0, pitch: -6 } };
S['jj.supernova_burst'] = hold(jjBurst, 1, 8, 8);
// jj.rope_throw: W2 E10 back8. The right arm whipped forward and across, letting the rope go.
const jjWhip = { twist: -24, lean: 8, ...stance, ra: { yaw: -30, pitch: -6 }, la: { yaw: 20, pitch: -80 }, head: { yaw: 0, pitch: 0 } };
S['jj.rope_throw'] = hold(jjWhip, 2, 10, 8, { ...jjWhip, twist: 20, ra: { yaw: 40, pitch: 30 } });
// jj.rope_pull: W1 E8 back8. The rope yanked back with both hands, leaning away from it.
const jjYank = { twist: 20, lean: -16, ...wide, ra: { yaw: 10, pitch: -60 }, la: { yaw: -10, pitch: -40 }, head: { yaw: 0, pitch: -4 } };
S['jj.rope_pull'] = hold(jjYank, 1, 8, 8);
// jj.scale_on: W3 E8 back6. Fists clenched low, chest out: the blood runs faster.
const jjScale = { lean: -6, ...wide, ra: { yaw: 24, pitch: -66 }, la: { yaw: 24, pitch: -66 }, head: { yaw: 0, pitch: -10 } };
S['jj.scale_on'] = hold(jjScale, 3, 8, 6);
// jj.scale_punch / jj.scale_punch_l: W1 E4 back3. Short, exact punches, right and left.
const jjJabR = { twist: -18, lean: 10, ...stance, ra: { yaw: -6, pitch: 0 }, la: { yaw: -30, pitch: -40 }, head: { yaw: 0, pitch: 0 } };
S['jj.scale_punch'] = hold(jjJabR, 1, 4, 3);
const jjJabL = { twist: 18, lean: 10, ...stance, ra: { yaw: -30, pitch: -40 }, la: { yaw: -6, pitch: 0 }, head: { yaw: 0, pitch: 0 } };
S['jj.scale_punch_l'] = hold(jjJabL, 1, 4, 3);
// jj.uppercut: W1 E6 back8. The right fist up past the head, rising onto the toes.
const jjUpper = { twist: -20, lean: -14, ...stance, ra: { yaw: -10, pitch: 80 }, la: { yaw: 10, pitch: -70 }, head: { yaw: 0, pitch: -20 } };
S['jj.uppercut'] = hold(jjUpper, 1, 6, 8, { ...jjUpper, lean: 12, drop: 3, ra: { yaw: 20, pitch: -80 } });
// jj.ratio_cut: W2 E6 back8. Nanami's blade swept across from right to left, nothing more than needed.
const jjCut = { twist: -30, lean: 8, ...stance, ra: { yaw: -40, pitch: -10 }, la: { yaw: 10, pitch: -80 }, head: { yaw: 0, pitch: 0 } };
S['jj.ratio_cut'] = hold(jjCut, 2, 6, 8, { ...jjCut, twist: 30, ra: { yaw: 70, pitch: 10 } });
// jj.ratio_crit: W3 E6 back10. The blade driven straight into the 7:3 point, the arm locked.
const jjThrust = { twist: -20, lean: 18, ...wide, ra: { yaw: -4, pitch: -2 }, la: { yaw: 16, pitch: -84 }, head: { yaw: 0, pitch: -2 } };
S['jj.ratio_crit'] = hold(jjThrust, 3, 6, 10);
// jj.slam_leap: W2 E8 back4. Up in the air, the right fist raised over the head, knees bent.
const jjLeap = { lean: -4, rl: { pitch: 50, out: 4 }, ll: { pitch: 40, out: 4 }, ra: { yaw: 10, pitch: 120 }, la: { yaw: 30, pitch: -40 }, head: { yaw: 0, pitch: 20 } };
S['jj.slam_leap'] = hold(jjLeap, 2, 8, 4);
// jj.slam_fist: W3 E10 back10. The fist driven into the ground, body bent over it, one knee down.
const jjSlam = { lean: 44, drop: 6, rl: { pitch: -30, out: 4 }, ll: { pitch: 70, out: 4 }, ra: { yaw: 0, pitch: -150 }, la: { yaw: 30, pitch: -60 }, head: { yaw: 0, pitch: -20 } };
S['jj.slam_fist'] = hold(jjSlam, 3, 10, 10, jjLeap);
// jj.overtime: W4 E14 back8. The left hand pulls the tie loose, the head tilts: the clock has stopped.
const jjTie = { lean: -2, ...stance, ra: { yaw: 10, pitch: -84 }, la: { yaw: -30, pitch: 50 }, head: { yaw: 10, pitch: 10 } };
S['jj.overtime'] = hold(jjTie, 4, 14, 8);
// jj.nail_throw: W2 E8 back8. Nobara tosses the nails with the left hand and hits them out of the air with the hammer.
const jjHit = { twist: -26, lean: 10, ...stance, ra: { yaw: -10, pitch: 4 }, la: { yaw: 10, pitch: 10 }, head: { yaw: 0, pitch: 0 } };
S['jj.nail_throw'] = hold(jjHit, 2, 8, 8, { ...jjHit, twist: 20, ra: { yaw: 20, pitch: 100 }, la: { yaw: -10, pitch: -20 } });
// jj.hairpin: W1 E8 back8. The hammer raised beside the head, a cocky snap: "Hairpin".
const jjPin = { lean: -6, ...stance, ra: { yaw: 30, pitch: 60 }, la: { yaw: 14, pitch: -80 }, head: { yaw: -16, pitch: -8 } };
S['jj.hairpin'] = hold(jjPin, 1, 8, 8);
// jj.resonance: W4 E20 back8. The doll held out in the left hand, the hammer brought down on the nail in it.
const jjDoll = { lean: 8, ...stance, ra: { yaw: -26, pitch: -16 }, la: { yaw: -14, pitch: -20 }, head: { yaw: 0, pitch: 16 } };
S['jj.resonance'] = hold(jjDoll, 4, 20, 8, { ...jjDoll, ra: { yaw: -10, pitch: 80 } });
// jj.point_blank: W2 E8 back8. The hammer swung straight into the chest of whoever is touching her.
const jjPoint = { twist: -24, lean: 16, ...wide, ra: { yaw: -10, pitch: -8 }, la: { yaw: -30, pitch: -10 }, head: { yaw: 0, pitch: 0 } };
S['jj.point_blank'] = hold(jjPoint, 2, 8, 8, { ...jjPoint, twist: 20, ra: { yaw: 20, pitch: 60 } });
// jj.clap: W2 E6 back6. Todo's clap: the hands come together hard in front of the chest.
const jjClap = { lean: 4, ...wide, ra: { yaw: -40, pitch: -6 }, la: { yaw: -40, pitch: -6 }, head: { yaw: 0, pitch: 0 } };
S['jj.clap'] = hold(jjClap, 2, 6, 6, { ...jjClap, ra: { yaw: 60, pitch: -6 }, la: { yaw: 60, pitch: -6 } });
// jj.block_rip: W3 E8 back2. Bent down, both hands under the block, tearing it out of the ground.
const jjRip = { lean: 50, drop: 6, ...wide, ra: { yaw: -10, pitch: -140 }, la: { yaw: -10, pitch: -140 }, head: { yaw: 0, pitch: -20 } };
S['jj.block_rip'] = hold(jjRip, 3, 8, 2);
// jj.block_throw: W2 E10 back8. The block heaved over the head and thrown forward high.
const jjHeave = { lean: -10, ...wide, ra: { yaw: -10, pitch: 120 }, la: { yaw: -10, pitch: 120 }, head: { yaw: 0, pitch: -24 } };
S['jj.block_throw'] = hold(jjHeave, 2, 10, 8, { ...jjHeave, lean: 20, ra: { yaw: -10, pitch: 30 }, la: { yaw: -10, pitch: 30 } });
// jj.hammer_punch: W2 E8 back8. Both fists brought down together from over the head.
const jjHammer = { lean: 30, ...wide, ra: { yaw: -24, pitch: -60 }, la: { yaw: -24, pitch: -60 }, head: { yaw: 0, pitch: -24 } };
S['jj.hammer_punch'] = hold(jjHammer, 2, 8, 8, { ...jjHammer, lean: -10, ra: { yaw: -20, pitch: 150 }, la: { yaw: -20, pitch: 150 } });
// jj.speak: W2 E10 back8. Inumaki pulls the collar down off his mouth with the left hand, the right hangs loose.
const jjCollar = { lean: 4, ...stance, ra: { yaw: 10, pitch: -84 }, la: { yaw: -44, pitch: 24 }, head: { yaw: 0, pitch: -2 } };
S['jj.speak'] = hold(jjCollar, 2, 10, 8);
// jj.speak_hard: W2 E14 back10. The collar down, leaning into the word, the right arm thrust down and back.
const jjShout = { lean: 18, ...wide, ra: { yaw: 30, pitch: -120 }, la: { yaw: -44, pitch: 30 }, head: { yaw: 0, pitch: -10 } };
S['jj.speak_hard'] = hold(jjShout, 2, 14, 10);
// jj.batto_stance: W3 E60 back6. Miwa's iaido stance: low, the left hand on the scabbard, the right on the grip across.
const jjBatto = { twist: 24, lean: 12, drop: 4, rl: { pitch: -24, out: 6 }, ll: { pitch: 30, out: 6 }, ra: { yaw: -50, pitch: -60 }, la: { yaw: -20, pitch: -70 }, head: { yaw: -20, pitch: 4 } };
S['jj.batto_stance'] = hold(jjBatto, 3, 60, 6);
// jj.batto_draw: W1 E6 back8. Drawn and swept in one tick: the sword arm out to the right, the body turned through.
const jjDraw = { twist: -40, lean: 10, drop: 3, rl: { pitch: -24, out: 6 }, ll: { pitch: 30, out: 6 }, ra: { yaw: 60, pitch: -6 }, la: { yaw: -10, pitch: -76 }, head: { yaw: 10, pitch: 2 } };
S['jj.batto_draw'] = hold(jjDraw, 1, 6, 8);
// jj.line_cut: W1 E10 back8. Past the target in a low lunge, the blade held out behind.
const jjLine = { twist: -30, lean: 26, drop: 5, rl: { pitch: 50, out: 4 }, ll: { pitch: -40, out: 4 }, ra: { yaw: 50, pitch: -30 }, la: { yaw: 20, pitch: -90 }, head: { yaw: 0, pitch: -6 } };
S['jj.line_cut'] = hold(jjLine, 1, 10, 8);

module.exports = S;
