// Oni do Torii (Kyoto). Tier 6. Maldição do medo de passar debaixo dos portais à noite: um ogro de quase três
// blocos, pele vermelha rachada, dois chifres, presas pra fora e cabelo de crina. Arrancou um pilar de torii e usa
// de clava (ainda com o pedaço da trave preta na ponta). Tem uma shimenawa (corda sagrada) amarrada na cintura com
// as tiras de papel, que ele não consegue tirar.
// Golpes: Porrada do Portal (levanta a clava com as duas mãos e desce no chão: onda de choque e tábuas voando) e
// Agarrão (pega você pela cintura, levanta na altura da cara, ruge e te arremessa longe).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  // [nome, pele, cabelo, chifre, olho]
  const KINDS = [
    ['Aka-oni', [190, 58, 44], [40, 30, 30], [230, 214, 170], [255, 214, 60]],
    ['Ao-oni', [66, 98, 160], [26, 26, 34], [220, 210, 180], [255, 240, 120]],
    ['Kuro-oni', [64, 56, 60], [200, 196, 190], [170, 40, 40], [255, 90, 60]],
  ];
  function variant(seed, kind) {
    const R = FX.rnd(seed);
    const k = kind ?? Math.floor(R() * KINDS.length);
    const scars = []; for (let i = 0; i < 3; i++) scars.push({ x: (R() - 0.5) * 10, y: -14 + R() * 12, a: (R() - 0.5) * 1.6, l: 2 + R() * 3 });
    return { seed, kind: k, skin: KINDS[k][1], hair: KINDS[k][2], horn: KINDS[k][3], eye: KINDS[k][4], scars, oneHorn: R() < 0.25, size: 0.95 + R() * 0.1 };
  }

  const FACE = [
    'BB....BB',
    '.BB..BB.',
    '.YYK.YY.',
    '.KKK.KK.',
    '...NN...',
    '.MMMMMM.',
    'T.MMMM.T',
    'T......T',
  ];
  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.7, hi = 1.18) => {
      const f = 0.6 - 0.3 * q.ny + 0.26 * (n3(q.ax * 0.35, q.ay * 0.35, q.az * 0.35, s + S) - 0.5) + (q.f === 'top' ? 0.2 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const P = {};
    // pele: rachaduras escuras, veias, cicatrizes e pelos
    const skin = (q, s) => {
      let c = shade(v.skin, q, s);
      if (Math.abs(n3(q.ax * 0.5, q.ay * 0.5, q.az * 0.5, s + 1 + S) - 0.5) < 0.025) c = mul(c, 0.6);
      if (n3(q.ax * 1.5, q.ay * 1.5, q.az * 1.5, s + 2 + S) > 0.78) c = mul(c, 0.86);
      return c;
    };
    P.skin = q => skin(q, 1);
    // peito: músculo marcado (sombras do peitoral e da barriga) e cicatrizes
    P.chest = q => {
      let c = skin(q, 2);
      if (q.f !== 'north') return c;
      const x = q.px - q.b.w / 2, y = q.py;
      if (Math.abs(x) < 0.3 && y > 2) c = mul(c, 0.78);
      if (Math.abs(y - 5.5 + 0.06 * x * x) < 0.35) c = mul(c, 0.78);
      if (y > 8 && fr(y / 2.4) < 0.15 && Math.abs(x) < 3.2) c = mul(c, 0.82);
      for (const sc of v.scars) { const dx = x - sc.x * 0.5, dy = y - (sc.y + 16) * 0.8; const u = dx * Math.cos(sc.a) + dy * Math.sin(sc.a), w = -dx * Math.sin(sc.a) + dy * Math.cos(sc.a); if (Math.abs(w) < 0.3 && Math.abs(u) < sc.l) return mix(c, [240, 200, 180], 0.4); }
      return c;
    };
    P.head = q => {
      let c = skin(q, 3);
      if (q.f !== 'north') return c;
      const col = 7 - Math.min(7, Math.floor(q.px * 8 / q.b.w)), row = Math.min(7, Math.floor(q.py * 8 / q.b.h));
      const ch = FACE[row][col];
      if (ch === 'B') return mul(v.hair, 1.1);
      if (ch === 'Y') return v.eye;
      if (ch === 'K') return [20, 10, 8];
      if (ch === 'N') return mul(v.skin, 0.7);
      if (ch === 'M') return [40, 10, 10];
      if (ch === 'T') return [240, 232, 210];
      return c;
    };
    P.hair = q => { let c = shade(v.hair, q, 4); if (fr(q.ax * 0.9 + q.az * 0.3) < 0.3) c = mul(c, 0.75); return c; };
    P.horn = q => { let c = shade(v.horn, q, 5, 0.8, 1.15); if (fr(q.ay * 0.7) < 0.2) c = mul(c, 0.85); if (q.ny < 0.3) c = mix(c, [255, 250, 240], 0.3); return c; };
    P.tusk = q => shade([240, 232, 210], q, 6, 0.85, 1.12);
    P.nail = q => shade([60, 44, 40], q, 7);
    // tanga de tigre (listrada) e a shimenawa: corda trançada em diagonal
    P.loin = q => { let c = shade([214, 160, 50], q, 8); if (fr(q.ax * 0.3 + q.ay * 0.15 + 0.3 * n3(q.ax * 0.2, q.ay * 0.3, 0, 9 + S)) < 0.32) c = [36, 28, 22]; return c; };
    P.rope = q => { let c = shade([214, 196, 140], q, 10, 0.8, 1.12); const k = fr((q.ax + q.az) * 0.45 + q.ay * 0.5); if (k < 0.2) c = mul(c, 0.7); return c; };
    P.shide = q => { let c = shade([248, 246, 238], q, 11, 0.86, 1.08); return c; };
    // pilar do torii: laca vermelha descascando, madeira por baixo, a trave preta na ponta
    P.pillar = q => {
      let c = shade([200, 46, 32], q, 12);
      if (n3(q.ax * 0.6, q.ay * 0.6, q.az * 0.6, 13 + S) > 0.66) c = shade([120, 84, 56], q, 14);
      if (fr(q.ay * 0.25) < 0.04) c = mul(c, 0.8);
      return c;
    };
    P.beam = q => { let c = shade([30, 26, 26], q, 15); if (q.f === 'top') c = mix(c, [70, 66, 60], 0.3); return c; };
    P.wood = q => { let c = shade([150, 110, 70], q, 16); if (fr(q.ay * 0.8 + n3(q.ax, 0, q.az, 17 + S)) < 0.2) c = mul(c, 0.8); return c; };
    return P;
  }

  function rig(P, v, lo) {
    const arm = s => {
      const n = s < 0 ? 'D' : 'E';
      return {
        name: 'braco' + n, pivot: [s * 10.4, -14.4, 0], rot: [0, 0, -s * 0.18], boxes: [{ x: -2.8, y: -1.6, z: -2.8, w: 5.6, h: 10.6, d: 5.6, p: P.skin }],
        kids: [{
          name: 'ante' + n, pivot: [0, 8.8, 0], rot: [-0.25, 0, 0], boxes: [{ x: -2.6, y: 0, z: -2.6, w: 5.2, h: 9, d: 5.2, p: P.skin }, ...(lo ? [] : [{ x: -2.8, y: 1, z: -2.8, w: 5.6, h: 1.4, d: 5.6, p: P.rope }])],
          kids: [{
            name: 'mao' + n, pivot: [0, 9, 0], boxes: [{ x: -2.7, y: 0, z: -2.7, w: 5.4, h: 4.4, d: 5.4, p: P.skin }, ...(lo ? [] : [-1.8, 0, 1.8].map(x => ({ x: x - 0.5, y: 4.2, z: -2.9, w: 1, h: 1.2, d: 1, p: P.nail })))],
            kids: s < 0 ? [{
              // clava: o pilar do torii, segurado pelo meio de baixo; a ponta pesada tem o pedaço da trave
              name: 'clava', pivot: [0, 2.6, 0], rot: [1.45, 0, 0], boxes: [
                { x: -2.6, y: -4, z: -2.6, w: 5.2, h: 24, d: 5.2, p: P.pillar },
                { x: -3.0, y: 16, z: -3.0, w: 6.0, h: 1.8, d: 6.0, p: P.beam },
                { x: -7, y: 18, z: -2.4, w: 14, h: 3.2, d: 4.8, p: P.beam },
                ...(lo ? [] : [{ x: -6.2, y: 21.0, z: -1.9, w: 2.4, h: 1.4, d: 3.6, p: P.wood }, { x: 3.4, y: 20.9, z: -1.8, w: 2.8, h: 1.6, d: 3.4, p: P.wood }]),
              ],
            }] : [],
          }],
        }],
      };
    };
    const leg = s => {
      const n = s < 0 ? 'D' : 'E';
      return {
        name: 'perna' + n, pivot: [s * 4.6, 9, 0], boxes: [{ x: -3.2, y: 0, z: -3.2, w: 6.4, h: 8.6, d: 6.4, p: P.skin }],
        kids: [{ name: 'canela' + n, pivot: [0, 8.2, 0], boxes: [{ x: -2.9, y: 0, z: -2.9, w: 5.8, h: 6.8, d: 5.8, p: P.skin }, { x: -3.1, y: 5.6, z: -4.6, w: 6.2, h: 1.2, d: 7.4, p: P.skin }, ...(lo ? [] : [-2, 0, 2].map(x => ({ x: x - 0.6, y: 5.8, z: -5.2, w: 1.2, h: 1, d: 0.8, p: P.nail })))] }],
      };
    };
    const shide = lo ? [] : [-5.4, -1.8, 1.8, 5.4].map((x, i) => ({ x: x - 0.8, y: 2.6, z: -5.6, w: 1.6, h: 3.4 + (i % 2) * 0.6, d: 0.3, p: P.shide }));
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'tronco', pivot: [0, 9, 0], boxes: [
            { x: -6.4, y: -3, z: -4.4, w: 12.8, h: 6, d: 8.8, p: P.loin },
            { x: -6.8, y: -2.2, z: -4.9, w: 13.6, h: 2.0, d: 9.8, p: P.rope },
            ...shide,
            { x: -7.2, y: -17, z: -4.6, w: 14.4, h: 14.4, d: 9.2, p: P.chest },
            { x: -8.8, y: -18.2, z: -4.2, w: 17.6, h: 4.6, d: 8.4, p: P.skin },
          ],
          kids: [
            {
              name: 'cabeca', pivot: [0, -17.6, -1.6], rot: [0.08, 0, 0], boxes: [
                { x: -4.2, y: -8.2, z: -4.4, w: 8.4, h: 8.4, d: 8.2, p: P.head },
                { x: -4.6, y: -9.2, z: -3.4, w: 9.2, h: 3.0, d: 8.6, p: P.hair },
                { x: -4.8, y: -7.2, z: 1.6, w: 9.6, h: 7, d: 3.6, p: P.hair },
                ...(lo ? [] : [{ x: -1.2, y: -1.6, z: -5.0, w: 0.8, h: 2.0, d: 0.8, p: P.tusk }, { x: 0.4, y: -1.6, z: -5.0, w: 0.8, h: 2.0, d: 0.8, p: P.tusk }]),
              ],
              kids: [
                ...(v.oneHorn ? [] : [{ name: 'chifreD', pivot: [-3, -9, -1], rot: [-0.2, 0, 0.35], boxes: [{ x: -1.1, y: -3.6, z: -1.1, w: 2.2, h: 3.8, d: 2.2, p: P.horn }, { x: -0.7, y: -6.2, z: -0.7, w: 1.4, h: 2.8, d: 1.4, p: P.horn }] }]),
                { name: 'chifreE', pivot: [v.oneHorn ? 0 : 3, -9, -1], rot: [-0.2, 0, v.oneHorn ? 0 : -0.35], boxes: [{ x: -1.1, y: -3.6, z: -1.1, w: 2.2, h: 3.8, d: 2.2, p: P.horn }, { x: -0.7, y: -6.2, z: -0.7, w: 1.4, h: 2.8, d: 1.4, p: P.horn }] },
              ],
            },
            arm(-1), arm(1),
          ],
        },
        leg(-1), leg(1),
      ],
    };
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    if (P.clava) P.clava.obj.visible = true;
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const W = (n, x, y, z) => { obj.updateMatrixWorld(true); return new THREE.Vector3(x, y, z).applyMatrix4(P[n].obj.matrixWorld); };
    const eyes = (k = 1) => { for (const [i, ex] of [[0, -2.1], [1, 2.1]]) ctx.get('olho' + i, () => DBFX.glow(e.v.eye.map(c => c / 255))).at(W('cabeca', ex, -5.4, -4.5), 0.05, 0.7 * k, t, 0.6); };
    const breathe = loop([[0, 0], [0.5, 1, 'io'], [1, 0, 'io']], t / 3);
    const debris = (key, at, age, n = 30) => ctx.get(key, () => DBFX.grains({ n, seed: key.length * 7 + 9, palette: [[200, 46, 32], [150, 110, 70], [90, 70, 50], [60, 56, 50], [110, 96, 70]], size: [0.03, 0.08], drag: 1.2, grav: 9, life: 1.0, cone: R2 => { const a2 = R2() * 6.28; return [Math.cos(a2) * (1 + R2() * 2.6), 2 + R2() * 3, Math.sin(a2) * (1 + R2() * 2.6)]; } })).at(at, age, 0.02);

    if (mode === 'parado') {
      // apoiado na clava fincada no chão, ombros subindo com a respiração pesada; coça a cabeça com a outra mão
      r('bracoD', -2.4, 0.3, 0.3); r('anteD', -1.6, 0, 0); r('clava', -1.0, 0.8, 0);   // clava descansando no ombro
      r('tronco', 0.1 + 0.03 * breathe, 0, 0); r('cabeca', -0.05 - 0.03 * breathe, 0.25 * Math.sin(t * 0.4), 0);
      const scratch = seq([[0, 0], [0.15, 1, 'io'], [0.6, 1], [0.75, 0, 'io']], fr(t / 6) * 1.5);
      r('bracoE', -2.6 * scratch, 0, -0.5 * scratch); r('anteE', -1.3 * scratch + 0.25 * Math.sin(t * 14) * scratch, 0, 0);
      eyes(0.8 + 0.2 * breathe);
      return;
    }
    if (mode === 'andar') {
      // passo largo e pesado, clava no ombro
      const ph = t * 2.3;
      r('pernaD', 0.45 * Math.sin(ph), 0, 0); r('pernaE', -0.45 * Math.sin(ph), 0, 0);
      r('canelaD', 0.4 * Math.max(0, -Math.sin(ph)), 0, 0); r('canelaE', 0.4 * Math.max(0, Math.sin(ph)), 0, 0);
      r('tronco', 0.1, 0.12 * Math.sin(ph), 0.04 * Math.sin(ph));
      r('bracoD', -2.4, 0.3, 0.3); r('anteD', -1.6, 0, 0); r('clava', -1.0, 0.8, 0);
      r('bracoE', 0.4 * Math.sin(ph), 0, 0);
      obj.position.y += 0.05 * Math.abs(Math.cos(ph));
      if (Math.abs(Math.cos(ph)) > 0.985) ctx.shake(0.18);
      eyes(1);
      return;
    }
    if (mode === 'porrada') {
      const T = t % 4.6;
      const R = ctx.ref; const RZ = -3.8; R.place(0, RZ, 0); R.spin(Math.PI);
      // pega a clava com as duas mãos, levanta bem acima da cabeça (inclina pra trás), ruge e desce com tudo;
      // o chão afunda, a onda de choque derruba e pedaços de madeira e pedra voam
      const up = seq([[0.2, 0], [1.2, 1, 'io'], [1.45, 1.05], [1.62, -0.2, 'in'], [3.2, -0.2], [4.2, 0, 'io']], T);
      const upP = Math.max(0, up), down = seq([[1.45, 0], [1.62, 1, 'in'], [3.2, 1], [4.2, 0, 'io']], T);
      r('bracoD', -2.9 * upP - 1.2 * down, 0, 0.25 * upP + 0.1 * down); r('bracoE', -2.9 * upP - 1.2 * down, 0, -0.45 * upP - 0.35 * down);
      r('anteD', -0.6 * upP - 0.2 * down, 0, 0); r('anteE', -0.6 * upP - 0.2 * down, 0, 0.3);
      r('clava', -0.5 * upP + 0.2 * down, 0, 0);
      // pose do impacto achada por busca em grade: ponta da trave encosta no chão em (0,1; -2,6)
      r('bracoD', -0.3 * down, 0, 1.0 * down); r('clava', -1.4 * down, 0.8 * down, 0);
      r('tronco', -0.3 * upP + 0.5 * down, 0, 0); r('cabeca', 0.2 * upP - 0.3 * down, 0, 0);
      r('pernaD', -0.4 * down, 0, 0); r('canelaD', 0.6 * down, 0, 0); r('pernaE', 0.3 * down, 0, 0);
      obj.position.y -= 0.2 * down;
      const imp = new THREE.Vector3(0.14, 0.02, -2.65);
      const ag = T - 1.62;
      if (ag > 0) {
        if (ag < 0.25) { ctx.flash(imp.clone().setY(0.5), 1.4 * (1 - ag / 0.25)); ctx.shake(1.2 * (1 - ag / 0.25)); }
        ctx.roarRing(imp, Math.min(0.999, ag * 1.2));
        if (ag < 0.5) ctx.get('poeira', FX.volume).set(imp.clone().setY(0.5), 1.2, Math.min(0.999, ag / 1.4 + 0.05), { seed: 7.7, dens: 6, rise: 0.4, squash: 0.6, smoke: [0.6, 0.56, 0.5] });
        else if (ag < 1.4) ctx.get('poeira', FX.volume).set(imp.clone().setY(0.5), 1.2, Math.min(0.999, ag / 1.4 + 0.05), { seed: 7.7, dens: 6, rise: 0.4, squash: 0.6, smoke: [0.6, 0.56, 0.5] });
        debris('lascas', imp, ag, 40);
        ctx.get('cratera', JJFX.crack).at(imp, imp.clone().add(new THREE.Vector3(0.4, 0, -1.6)), 0.6, FX.EASE.out(FX.clamp01(ag * 4)), 1 - FX.clamp01((T - 4.0) / 0.5), t, 0, 4);
      }
      const hit = seq([[1.66, 0], [1.74, 1, 'snap'], [3.0, 0.7], [3.8, 0]], T);
      R.place(0, RZ - 0.7 * FX.EASE.out(FX.clamp01((T - 1.66) / 0.5)), 0.5 * seq([[1.66, 0], [1.9, 1, 'out'], [2.2, 0, 'in']], T)); R.lean(-0.5 * hit); R.armsOut(0.4 * hit);
      eyes(1 + upP);
      return;
    }
    if (mode === 'agarrao') {
      const T = t % 5.0;
      const R = ctx.ref; const RZ = -1.7;
      // finca a clava, avança um passo e pega você pela cintura com a mão esquerda; levanta até a cara,
      // ruge (onda + tela tremendo) e arremessa por cima do ombro
      r('bracoD', -0.35, 0.2, 0.15); r('anteD', -0.6, 0, 0); r('clava', -1.05, 0, 0);
      const reach = seq([[0.3, 0], [0.9, 1, 'io']], T);
      const lift = seq([[1.0, 0], [1.6, 1, 'io'], [2.9, 1], [3.15, 1.4, 'in']], T);
      const thr = T > 3.15;
      r('bracoE', -1.2 * reach - 1.0 * Math.min(1, lift) - 1.4 * Math.max(0, lift - 1) / 0.4, 0, -0.1);
      r('anteE', -0.3 * reach + 0.2 * lift, 0, 0);
      r('tronco', 0.25 * reach - 0.25 * Math.min(1, lift) + (thr ? 0 : 0), 0, 0);
      const roar = seq([[1.8, 0], [2.0, 1, 'snap'], [2.8, 1], [3.0, 0]], T);
      r('cabeca', -0.35 * roar, 0, 0);
      if (!thr) {
        const hand = W('maoE', 0, 3.2, -1.6);
        if (T < 1.0) { R.place(0, RZ, 0); R.spin(Math.PI); }
        else { R.place(hand.x, hand.z, hand.y - 0.75); R.spin(Math.PI); R.armsOut(0.5); R.lean(0.15 * Math.sin(T * 9)); }
      } else {
        const k = FX.clamp01((T - 3.15) / 0.8);
        R.place(0.2, -1.2 + 5.0 * k, 2.6 * Math.sin(Math.PI * k) + (1 - k) * 2.2); R.lean(-Math.PI * 1.4 * k); R.armsOut(0.8); R.spin(Math.PI);
        if (k >= 1) { R.place(0.2, 3.8, 0); R.lean(-1.3); }
      }
      if (roar > 0) { ctx.shake(0.5 * roar); ctx.roarRing(W('cabeca', 0, -2, -5).setY(1.0), fr(T * 2.2)); }
      if (T > 0.95 && T < 1.1) ctx.flash(new THREE.Vector3(0, 1.0, RZ), 0.5);
      eyes(1 + roar);
      return;
    }
    if (mode === 'morte') {
      // cambaleia, solta a clava (que cai e quica), cai sentado e tomba de costas; vira cinza
      const { T, fall } = JJFX.exorcize(t, obj, e, ctx, { h: 2.8, refZ: -2.6, rotX: 1.35, back: 0.7, drop: -0.95, ashY: 0.3, ashZ: 1.8, n: 240 });
      r('bracoD', 0.5 * fall, 0, 0.9 * fall); r('bracoE', 0.5 * fall, 0, -0.9 * fall);
      // a clava escapa da mão no tombo (Stark 2026-11-14: escorada nela, o corpo ficava deitado no ar)
      r('clava', -1.0 * fall, 0, 0);
      if (fall > 0.35 && P.clava) P.clava.obj.visible = false;
      eyes(Math.max(0, 1 - T));
      // nenhum membro escora o morto: o corpo encosta no chão (teste R5)
      JJFX.floorLimbs(obj, P, ['tronco', 'cabeca'], ['bracoD', 'bracoE', 'pernaD', 'pernaE'], fall);
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(14, 0), variant(36, 1), variant(70, 2)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'oni', nome: 'Oni do Torii', bioma: 'Kyoto', tier: 'Grau 2 · tier 6',
    variants: fixed.map(v => ({ nome: KINDS[v.kind][0], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado (' + KINDS[v.kind][0] + ')', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['porrada', 'Porrada do Portal'], ['agarrao', 'Agarrão'], ['morte', 'Exorcizado']],
    cam: 6.6, lookY: 1.5, lookZ: { porrada: -1.6, agarrao: -0.4, morte: 0.8 }, camFar: { porrada: 8.6, agarrao: 9.0, morte: 7.6 }, camYaw: { porrada: -1.1, agarrao: -1.3, morte: -1.0 },
    info: {
      'Tamanho': '2,8 blocos com os chifres',
      'Vida': '110 (couro grosso: flecha faz metade)',
      'Jeito': 'Um por portal de torii abandonado em Kyoto, sentado debaixo dele de noite. Quem passa pelo portal acorda ele. De dia dorme encostado no pilar que sobrou.',
      'Porrada do Portal': 'Levanta a clava com as duas mãos (1,2 s de aviso: ele inclina pra trás) e desce: dano alto na frente, onda de choque de 3 blocos que derruba e lascas voando.',
      'Agarrão': 'Avança um passo e pega você pela cintura, levanta até a cara e ruge (atordoa), depois te arremessa 5 blocos por cima do ombro. Golpe na mão solta.',
      'Fraqueza': 'A shimenawa na cintura: cortar a corda deixa ele fraco (sem armadura) por 10 s. Feijão jogado (item Feijão Torrado) espanta.',
      'Solta': 'Chifre de Oni, Lasca de Torii; raro: Clava do Portal (arma pesada, lenta, derruba).',
      'Variações': 'Aka-oni (vermelho), Ao-oni (azul) ou Kuro-oni (preto de cabelo branco); 1 em 4 tem um chifre só; cicatrizes diferentes.',
    },
  });
})();
