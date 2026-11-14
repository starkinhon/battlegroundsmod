// Boneco do Yaga (Templos e Escola). Tier 9. Os cadáveres amaldiçoados que o diretor Yaga costura pra treinar os
// alunos: bicho de pelúcia de feltro, cabeção, olhos de botão, boca costurada e luva de boxe. Alguns fugiram da
// oficina e andam soltos pelos templos. Golpes: Combo de Boxe (jab, jab, direto e esquiva) e Despertador (igual ao
// boneco do treino do Itadori: se você fica parado e "apaga" a energia, ele chega de mansinho e manda um uppercut).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  // [nome, feltro, focinho/barriga, orelha]
  const KINDS = [
    ['Urso', [150, 104, 70], [222, 196, 160], 'redonda'],
    ['Gato', [128, 130, 140], [236, 232, 226], 'pontuda'],
    ['Coelho', [226, 200, 206], [250, 244, 240], 'comprida'],
  ];
  const GLOVES = [[196, 40, 40], [40, 80, 170], [230, 190, 40]];
  function variant(seed, kind) {
    const R = FX.rnd(seed);
    const k = kind ?? Math.floor(R() * KINDS.length);
    return { seed, kind: k, fur: KINDS[k][1], belly: KINDS[k][2], ear: KINDS[k][3], glove: GLOVES[Math.floor(R() * GLOVES.length)], patch: R() < 0.6, oneEye: R() < 0.3, size: 0.92 + R() * 0.16 };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.8, hi = 1.1) => {
      const f = 0.62 - 0.4 * q.ny + 0.3 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.22 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    // feltro: fibra miudinha + costura de ponto corrido onde as peças se juntam (meio do corpo e laterais)
    const felt = (base, q, s) => {
      let c = shade(base, q, s);
      const fib = n3(q.ax * 2.2, q.ay * 2.2, q.az * 2.2, 20 + s + S);
      if (fib > 0.72) c = mul(c, 1.07); else if (fib < 0.25) c = mul(c, 0.93);
      const seamX = Math.abs(q.ax) < 0.35 && (q.f === 'north' || q.f === 'south' || q.f === 'top');
      const seamS = (q.f === 'east' || q.f === 'west') && Math.abs(q.nz - 0.5) < 0.05;
      if ((seamX || seamS) && fr(q.ay * 0.9 + q.az * 0.9) < 0.5) c = mul(c, 0.62);
      return c;
    };
    const P = {};
    P.body = q => {
      let c = felt(v.fur, q, 1);
      // barriga de outro feltro, oval, com remendo quadrado costurado em X
      if (q.f === 'north') {
        const x = q.nx - 0.5, y = q.ny - 0.58;
        if (x * x / 0.13 + y * y / 0.16 < 1) c = felt(v.belly, q, 2);
        if (v.patch && q.nx > 0.58 && q.nx < 0.84 && q.ny > 0.2 && q.ny < 0.42) {
          c = felt([90, 110, 150], q, 3);
          const px = (q.nx - 0.58) / 0.26, py = (q.ny - 0.2) / 0.22;
          if (Math.abs(px - py) < 0.12 || Math.abs(px + py - 1) < 0.12) c = [234, 226, 200];
          if (px < 0.08 || px > 0.92 || py < 0.08 || py > 0.92) c = mul(c, 0.7);
        }
      }
      return c;
    };
    P.head = q => {
      let c = felt(v.fur, q, 4);
      if (q.f !== 'north') return c;
      const W = q.b.w, H = q.b.h, x = q.px / W, y = q.py / H;
      // focinho claro embaixo
      const mx = x - 0.5, my = y - 0.74;
      if (mx * mx / 0.07 + my * my / 0.035 < 1) c = felt(v.belly, q, 5);
      // olhos de botão: disco preto, aro, 4 furinhos e brilho; um deles pode ser um X costurado
      const eye = (cx, cy, cross) => {
        const dx = (x - cx) * W, dy = (y - cy) * H, d = Math.hypot(dx, dy);
        if (cross) { if (d < 1.4 && (Math.abs(dx - dy) < 0.45 || Math.abs(dx + dy) < 0.45)) return [30, 20, 20]; return null; }
        if (d < 1.3) {
          if (dx < -0.2 && dx > -0.9 && dy < -0.2 && dy > -0.9) return [236, 236, 240];                       // brilho
          if ((Math.abs(dx - 0.35) < 0.25 || Math.abs(dx + 0.35) < 0.25) && Math.abs(dy - 0.35) < 0.25) return [96, 70, 60]; // furinhos
          return d > 0.95 ? [74, 52, 44] : [34, 22, 20];
        }
        if (d < 1.6 && fr(Math.atan2(dy, dx) * 1.3) < 0.5) return [234, 226, 200];                                     // linha que prende o botão
        return null;
      };
      const e1 = eye(0.3, 0.46, false), e2 = eye(0.7, 0.46, v.oneEye);
      if (e1) return e1; if (e2) return e2;
      // nariz triangular e boca costurada em zigue-zague
      if (Math.abs(x - 0.5) < 0.07 - (y - 0.62) * 0.4 && y > 0.6 && y < 0.68) return [40, 26, 30];
      if (y > 0.76 && y < 0.84 && x > 0.33 && x < 0.67) {
        const z = Math.abs(fr((x - 0.33) * W * 0.5) - 0.5) * 2;
        if (Math.abs((y - 0.76) / 0.08 - z) < 0.3) return [40, 26, 30];
      }
      return c;
    };
    P.ear = q => {
      let c = felt(v.fur, q, 6);
      if (q.f === 'north' && q.nx > 0.25 && q.nx < 0.75 && q.ny > 0.25) c = felt(mix(v.belly, [230, 150, 160], 0.4), q, 7);
      return c;
    };
    P.arm = q => felt(v.fur, q, 8);
    P.glove = q => {
      let c = shade(v.glove, q, 9, 0.7, 1.18);
      // couro: brilho no topo da luva, punho branco com cadarço
      if (q.ny < 0.24) { c = shade([236, 232, 222], q, 10); if (q.f === 'north' && Math.abs(q.nx - 0.5) < 0.08) c = [120, 110, 100]; }
      if (q.f === 'top' && q.nx > 0.3 && q.nx < 0.55 && q.nz > 0.3 && q.nz < 0.6) c = mix(c, [255, 255, 255], 0.35);
      if (q.f === 'north' && q.ny > 0.35 && Math.abs(q.nx - 0.3) < 0.04) c = mul(c, 0.75);                                 // dedão
      return c;
    };
    P.foot = q => {
      let c = felt(v.fur, q, 11);
      if (q.f === 'bottom') { c = felt(v.belly, q, 12); const dx = q.nx - 0.5, dz = q.nz - 0.55; if (dx * dx + dz * dz < 0.06) c = felt(mix(v.belly, [180, 110, 110], 0.4), q, 13); }
      return c;
    };
    P.thread = q => [234, 226, 200];
    return P;
  }

  function rig(P, v, lo) {
    const earBox = v.ear === 'comprida' ? { x: -1.2, y: -7, z: -0.8, w: 2.4, h: 7, d: 1.6, p: P.ear }
      : v.ear === 'pontuda' ? { x: -1.4, y: -3.2, z: -0.8, w: 2.8, h: 3.2, d: 1.6, p: P.ear }
        : { x: -1.6, y: -2.6, z: -0.8, w: 3.2, h: 2.8, d: 1.6, p: P.ear };
    const ear = s => ({ name: s < 0 ? 'orelhaD' : 'orelhaE', pivot: [s * 3.4, -9.2, 0], rot: [0, 0, s * (v.ear === 'pontuda' ? 0.2 : 0.1)], boxes: [earBox, ...(v.ear === 'pontuda' && !lo ? [{ x: -0.8, y: -4.4, z: -0.6, w: 1.6, h: 1.4, d: 1.2, p: P.ear }] : [])] });
    const arm = s => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * 5.4, -6.2, -0.4], rot: [-0.9, 0, s * -0.2], boxes: [{ x: -1.5, y: -1, z: -1.5, w: 3, h: 4.4, d: 3, p: P.arm }],
      kids: [{ name: s < 0 ? 'luvaD' : 'luvaE', pivot: [0, 3.6, 0], boxes: [{ x: -2.3, y: 0, z: -2.5, w: 4.6, h: 4.4, d: 4.8, p: P.glove }] }],
    });
    const leg = s => ({
      name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * 2.4, 20.2, 0], boxes: [{ x: -1.9, y: 0, z: -1.9, w: 3.8, h: 2.8, d: 3.8, p: P.arm }],
      kids: [{ name: s < 0 ? 'peD' : 'peE', pivot: [0, 2.6, 0], boxes: [{ x: -2.1, y: 0, z: -3, w: 4.2, h: 1.2, d: 4.8, p: P.foot }] }],
    });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'corpo', pivot: [0, 20.8, 0], boxes: [
          { x: -4.6, y: -9, z: -3.4, w: 9.2, h: 9.4, d: 6.8, p: P.body },
          ...(lo ? [] : [{ x: -0.3, y: -9.4, z: -3.55, w: 0.6, h: 9, d: 0.2, p: P.thread }]),                          // costura da frente
        ],
        kids: [
          {
            name: 'cabeca', pivot: [0, -8.6, 0], boxes: [{ x: -5.8, y: -10, z: -5, w: 11.6, h: 10.2, d: 10, p: P.head }],
            kids: [ear(-1), ear(1)],
          },
          arm(-1), arm(1),
        ],
      }, leg(-1), leg(1)],
    };
  }

  // guarda de boxeador: luvas na frente do rosto, quicando
  function guard(r, k = 1) {
    r('bracoD', -0.35 * k, 0.35 * k, 0); r('bracoE', -0.35 * k, -0.35 * k, 0);
    r('luvaD', -0.9 * k, 0, 0); r('luvaE', -0.9 * k, 0, 0);
  }
  // braço que soca reto: estica o ombro pra frente e a luva alinha
  function jab(r, side, k) {
    const n = side < 0 ? 'D' : 'E';
    r('braco' + n, -0.75 * k, side * -0.35 * k, 0); r('luva' + n, 0.9 * k, 0, 0);
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const earWob = (k) => { r('orelhaD', 0.15 * k, 0, 0); r('orelhaE', 0.15 * k, 0, 0); };

    if (mode === 'parado') {
      // quica na ponta dos pés trocando o peso, cabeça balança atrasada (pelúcia é mole)
      const b = Math.abs(Math.sin(t * 4.2));
      obj.position.y += 0.05 * b;
      guard(r);
      r('corpo', 0.08, 0.15 * Math.sin(t * 2.1), 0.05 * Math.sin(t * 2.1));
      r('cabeca', 0.05 * Math.sin(t * 4.2 - 0.8), 0, 0.12 * Math.sin(t * 1.3));
      r('pernaD', -0.2 * Math.sin(t * 2.1), 0, 0); r('pernaE', 0.2 * Math.sin(t * 2.1), 0, 0);
      earWob(Math.sin(t * 4.2 - 1.4));
      // de vez em quando dá dois socos no ar (treino de sombra)
      const sh = t % 5;
      if (sh > 3.6 && sh < 4.4) { const k = seq([[3.6, 0], [3.7, 1, 'out'], [3.85, 0], [4.0, 0], [4.1, 1, 'out'], [4.3, 0]], sh); jab(r, sh < 3.95 ? 1 : -1, k); }
      return;
    }
    if (mode === 'andar') {
      // gingado de pelúcia: pernas curtas, balança o corpo todo de lado a lado
      const w = Math.sin(t * 6);
      r('pernaD', 0.7 * w, 0, 0); r('pernaE', -0.7 * w, 0, 0);
      r('corpo', 0.05, 0, 0.16 * w); r('cabeca', 0, 0, -0.1 * w);
      r('bracoD', -0.7 * w, 0, 0.3); r('bracoE', 0.7 * w, 0, -0.3);
      obj.position.y += 0.06 * Math.abs(Math.cos(t * 6));
      earWob(Math.sin(t * 12));
      return;
    }
    if (mode === 'combo') {
      const T = t % 3.0;
      const R = ctx.ref; const RZ = -1.7; R.place(0, RZ, 0);
      R.spin(Math.PI);
      // passo pra frente, jab esquerdo, jab esquerdo, direto de direita girando o quadril; o alvo devolve e ele esquiva
      const step = seq([[0, 0], [0.25, 1, 'out'], [2.5, 1], [2.9, 0, 'io']], T);
      obj.position.z -= 0.25 * step;
      guard(r);
      const j1 = seq([[0.3, 0], [0.38, 1, 'out'], [0.5, 0, 'io']], T);
      const j2 = seq([[0.6, 0], [0.68, 1, 'out'], [0.8, 0, 'io']], T);
      const cr = seq([[0.95, 0], [1.08, 1, 'out'], [1.35, 1], [1.6, 0, 'io']], T);
      jab(r, 1, j1 + j2); jab(r, -1, cr * 1.1);
      r('corpo', 0.15 * cr, 0.45 * cr - 0.15 * (j1 + j2), 0);
      r('pernaD', -0.4 * cr, 0, 0); r('pernaE', 0.3 * cr, 0, 0);
      // alvo leva: cabeça vai pra trás nos jabs, recua no direto
      const hit = Math.max(j1, j2) * 0.5 + cr;
      R.lean(-0.25 * hit);
      R.place(0, RZ - 0.35 * seq([[1.05, 0], [1.2, 1, 'out'], [2.6, 1], [2.9, 0]], T), 0);
      for (const [t0, x] of [[0.38, 0.1], [0.68, 0.1], [1.08, -0.1]]) if (T > t0 && T < t0 + 0.12) ctx.flash(new THREE.Vector3(x, 1.4, RZ + 0.2), 1 - (T - t0) / 0.12);
      if (T > 1.08 && T < 1.25) ctx.shake(0.5);
      // devolve o soco (alvo) e o boneco abaixa e gira (esquiva em U)
      const counter = seq([[1.8, 0], [1.95, 1, 'out'], [2.2, 0]], T);
      R.punch(counter);
      const duck = seq([[1.75, 0], [1.92, 1, 'out'], [2.15, 1], [2.4, 0, 'io']], T);
      obj.position.y -= 0.22 * duck; obj.position.x += 0.18 * Math.sin(duck * Math.PI);
      r('corpo', 0.35 * duck, 0, -0.2 * duck);
      r('cabeca', 0, 0, 0.15 * duck);
      earWob(Math.sin(T * 14) * Math.max(j1, j2, cr));
      return;
    }
    if (mode === 'despertador') {
      const T = t % 5.0;
      const R = ctx.ref; const RZ = -1.5;
      R.spin(Math.PI);
      // o alvo para e cochila (cabeceia), o boneco chega na ponta dos pés por trás da guarda, pausa... e o uppercut lança
      const nod = seq([[0.2, 0], [0.8, 1, 'io'], [1.0, 0.7], [1.6, 1, 'io'], [2.6, 1]], T);
      R.lean(0.22 * nod);
      R.armsOut(-0.05 * nod);
      const sneak = seq([[0.6, 0], [2.2, 1, 'io']], T);
      obj.position.z += 1.4 * (1 - sneak) - 0.05;
      const tip = Math.sin(T * 9) * (T > 0.6 && T < 2.2 ? 1 : 0);
      r('pernaD', 0.35 * tip, 0, 0); r('pernaE', -0.35 * tip, 0, 0);
      obj.position.y += 0.04 * Math.abs(tip);
      r('corpo', 0.2 * sneak, 0, 0);
      // pausa olhando pra cara do alvo (cabeça inclina, curiosa)
      const look = seq([[2.2, 0], [2.5, 1, 'out'], [2.9, 1], [3.0, 0]], T);
      r('cabeca', -0.25 * look, 0, 0.3 * look);
      // carrega: agacha, braço direito lá embaixo
      const load = seq([[2.9, 0], [3.15, 1, 'out'], [3.3, 1]], T);
      const up = seq([[3.3, 0], [3.42, 1, 'out'], [3.9, 1], [4.4, 0, 'io']], T);
      obj.position.y -= 0.25 * load * (1 - up) - 0.15 * up;
      r('corpo', 0.3 * load * (1 - up) - 0.25 * up, 0.3 * load * (1 - up), 0);
      r('bracoD', 0.6 * load * (1 - up) - 2.4 * up, 0, 0.2 * up); r('luvaD', -0.9 * load * (1 - up) - 0.3 * up, 0, 0);
      r('bracoE', -0.35, 0.35, 0); r('luvaE', -0.9, 0, 0);
      r('pernaD', 0.3 * load * (1 - up) - 0.3 * up, 0, 0);
      // alvo voa pra cima e cai de costas
      const fly = FX.clamp01((T - 3.42) / 1.1);
      const ry = fly > 0 && fly < 1 ? 2.6 * Math.sin(Math.PI * fly) : 0;
      R.place(0, RZ - 0.6 * fly, ry);
      if (fly > 0) { R.lean(-0.22 * nod - 0.6 * Math.min(1, fly * 2)); R.armsOut(0.8 * Math.sin(Math.PI * fly)); }
      if (T > 3.42 && T < 3.6) { ctx.flash(new THREE.Vector3(0, 1.3, RZ + 0.2), 1 - (T - 3.42) / 0.18); ctx.shake(0.9); }
      if (T > 4.5 && T < 4.8) ctx.roarRing(new THREE.Vector3(0, 0.02, RZ - 0.6), (T - 4.5) / 0.3);
      // "Zzz" do alvo vira estrela depois: se encostar no chão, borrão
      if (T > 3.42 && T < 4.2) ctx.dizzy(1 - (T - 3.42) / 0.8);
      earWob(Math.sin(T * 16) * up);
      return;
    }
    if (mode === 'morte') {
      // cadáver amaldiçoado não vira cinza: o núcleo apaga, o enchimento estoura pela costura e ele desaba mole
      const T = t % 3.6;
      const R = ctx.ref; R.place(0, -1.7, 0); R.spin(Math.PI);
      R.punch(seq([[0.15, 0], [0.25, 1, 'snap'], [0.6, 1], [0.9, 0]], T));
      const fall = seq([[0.25, 0], [0.75, 1, 'in'], [0.85, 0.92, 'out'], [0.95, 1]], T);
      obj.rotation.x = -1.42 * fall;
      obj.position.z += 0.5 * FX.EASE.out(FX.clamp01((T - 0.25) / 0.5));
      obj.position.y -= 0.25 * fall;
      const limp = FX.clamp01((T - 0.8) / 0.5);
      r('cabeca', 0.25 * limp, 0, 0.5 * limp);
      r('bracoD', 0.9 * limp, 0, 0.5 * limp); r('bracoE', 0.9 * limp, 0, -0.6 * limp);
      r('pernaD', -0.3 * limp, 0, 0.2 * limp); r('pernaE', -0.2 * limp, 0, -0.3 * limp);
      // murcha um pouco (perdeu enchimento)
      const sag = 1 - 0.18 * FX.clamp01((T - 0.9) / 1.2);
      P.corpo.obj.scale.set(1, sag, 1.0 + (1 - sag) * 0.5);
      if (T > 0.25 && T < 0.4) ctx.flash(new THREE.Vector3(0, 1, -0.6), 1 - (T - 0.25) / 0.15);
      if (T > 0.9) {
        ctx.get('enchimento', () => DBFX.grains({
          n: 90, seed: 4, palette: [[244, 240, 232], [230, 226, 216], [250, 250, 246], [214, 208, 196]], size: [0.04, 0.11], drag: 3.2, grav: 0.5, life: 2.2, spread: 0.5,
          cone: R2 => { const a = R2() * Math.PI * 2, o = 0.3 + R2() * 1.2; return [Math.cos(a) * o, 1 + R2() * 2.2, Math.sin(a) * o * 0.6]; },
        })).at(new THREE.Vector3(0, 0.25, 0.3), T - 0.9, 0.02);
      }
      // o núcleo (pedrinha de energia do Yaga) rola pra fora apagando
      const core = ctx.get('nucleo', () => DBFX.glow([0.6, 0.45, 1.0]));
      const cf = FX.clamp01((T - 0.95) / 0.8);
      core.at(new THREE.Vector3(0.5 * cf, 0.08, 0.2 + 0.4 * cf), 0.09, T > 0.95 ? 1 - FX.clamp01((T - 1.8) / 1.2) : 0, t);
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(3, 0), variant(17, 1), variant(41, 2)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'boneco', nome: 'Boneco do Yaga', bioma: 'Templos e Escola', tier: 'Cadáver amaldiçoado · tier 9',
    variants: fixed.map((v, i) => ({ nome: KINDS[v.kind][0], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado (' + KINDS[v.kind][0] + ')', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['combo', 'Combo de Boxe'], ['despertador', 'Despertador'], ['morte', 'Desmontado']],
    cam: 4.2, lookY: 0.75, lookZ: { combo: -0.8, despertador: -0.8, morte: 0 }, camFar: { combo: 4.8, despertador: 6.2, morte: 4.6 }, camYaw: { combo: -1.3, despertador: -1.35 },
    info: {
      'Tamanho': 'Pouco mais de 1 bloco (cabeção de pelúcia)',
      'Vida': '16',
      'Jeito': 'Não é maldição: é cadáver amaldiçoado que fugiu da oficina do Yaga. Fica treinando soco no ar perto das escolas e templos. Neutro: só briga se você bater ou se ficar parado perto dele.',
      'Combo de Boxe': 'Jab, jab e direto (o direto empurra). Se você revidar logo depois, ele abaixa e esquiva.',
      'Despertador': 'Se você ficar parado 3 s perto dele (ou dormindo/AFK), ele chega de mansinho e dá um uppercut que te joga 2 blocos pra cima. O mesmo treino que o Yaga fez com o Itadori.',
      'Morte': 'Não vira cinza: o enchimento estoura pela costura e o núcleo de energia rola pra fora apagando.',
      'Solta': 'Enchimento (algodão), Agulha e Linha; raro: Núcleo de Cadáver (dá pra costurar o seu próprio boneco ajudante).',
      'Variações': 'Urso, Gato ou Coelho; cor da luva; remendo na barriga; às vezes um olho é um X costurado.',
    },
  });
})();
