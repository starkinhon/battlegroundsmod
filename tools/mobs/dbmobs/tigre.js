// Tigre-dente-de-sabre (florestas e montanhas Paozu). O tigrão do desenho que o Goku pequeno caçava: laranja forte,
// listras pretas grossas, barriga e focinho brancos, dois dentões pra fora. Grande (1,6 bloco de altura).
// Golpes: Bote (agacha, pula em você, derruba e morde sacudindo) e Patada Dupla (empina nas patas de trás,
// duas patadas com as garras e um rugido).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const V3 = (x, y, z) => new THREE.Vector3(x, y, z);

  const FURS = [[236, 138, 40], [226, 120, 34], [240, 156, 58], [214, 110, 40]];
  function variant(seed) {
    const R = FX.rnd(seed);
    return { seed, fur: FURS[Math.floor(R() * FURS.length)], scar: R() < 0.35, white: R() < 0.12, stripeW: 0.16 + R() * 0.08, fangs: 3 + Math.floor(R() * 2) };
  }

  function painters(v) {
    const S = v.seed;
    const base = v.white ? [236, 232, 222] : v.fur;
    const shade = (c, q, s, lo = 0.82, hi = 1.14) => cel(c, 0.62 - 0.38 * q.ny + 0.1 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0), lo, hi);
    // listras: faixas em volta do corpo que entortam com ruído e afinam chegando na barriga; cada uma com largura própria
    const stripe = (q, axis, scale) => {
      const s = axis / scale + 0.45 * (n3(q.ax * 0.12, q.ay * 0.12, q.az * 0.05, 40 + S) - 0.5) * 2;
      const id = Math.floor(s), w = v.stripeW * (0.7 + 0.6 * n3(id, 0, 0, 41 + S));
      const belly = q.f === 'bottom' ? 1 : Math.max(0, (q.ny - 0.55) / 0.45);
      return Math.abs(fr(s) - 0.5) < w * (1 - belly * 0.9);
    };
    const P = {};
    P.fur = q => {
      const under = q.f === 'bottom' || (q.f !== 'top' && q.ny > 0.78);
      let c = shade(under ? [246, 238, 216] : base, q, 1);
      if (!under && stripe(q, q.az, 3.4)) c = [34, 26, 22];
      if (v.scar && q.f === 'west' && Math.abs(q.az + 4 - q.ay * 0.6) < 0.5 && q.ny < 0.6) c = [170, 90, 80];
      return c;
    };
    P.leg = q => {
      let c = shade(q.ny > 0.75 || q.f === 'north' && q.ny > 0.4 ? mix(base, [246, 238, 216], 0.6) : base, q, 2);
      if (q.ny < 0.7 && q.f !== 'north' && stripe(q, q.ay, 2.6)) c = [34, 26, 22];
      return c;
    };
    P.paw = q => { let c = shade([246, 238, 216], q, 3); if (q.f === 'bottom') c = [70, 50, 50]; return c; };
    P.tail = q => { let c = shade(base, q, 4); if (q.pz > q.b.d - 2.2 || stripe(q, q.az, 2.4)) c = [34, 26, 22]; return c; };
    P.head = q => {
      let c = shade(base, q, 5, 0.86, 1.1);
      if (q.f === 'top' && stripe(q, q.ax, 1.8) && Math.abs(q.ax) > 0.8) c = [34, 26, 22];
      if (q.f === 'west' || q.f === 'east') { if (q.ny > 0.55) c = shade([246, 238, 216], q, 6); else if (stripe(q, q.ay + q.az * 0.4, 2.2)) c = [34, 26, 22]; }
      if (q.f !== 'north') return c;
      // cara 9x8: listras na testa, olhos amarelos com pupila, bochecha branca
      const x = Math.min(8, Math.floor(q.px)), y = Math.min(7, Math.floor(q.py)), m = Math.min(x, 8 - x);
      if (y <= 1 && (m === 3 || (y === 0 && m === 4))) c = [34, 26, 22];
      if (y === 1 && m === 1) c = [34, 26, 22];
      if (y === 3 && (m === 1 || m === 2)) c = m === 2 ? [250, 210, 60] : [255, 240, 140];
      if (y === 3 && m === 2 && x < 4) c = [250, 210, 60];
      if (y === 4 && m === 2) c = [20, 16, 14];
      if (y === 4 && m === 1) c = [250, 210, 60];
      if (y >= 5) c = m <= 1 ? shade(base, q, 7) : shade([246, 238, 216], q, 8);
      if (v.scar && x === 6 && y >= 1 && y <= 5) c = [150, 70, 64];
      return c;
    };
    P.muzzle = q => {
      let c = shade([248, 242, 226], q, 9, 0.88, 1.06);
      if (q.f === 'top' && q.pz < 1.6 && Math.abs(q.px - q.b.w / 2) < 1.2) c = [60, 34, 40];   // nariz
      if (q.f === 'north' && q.py < 1.4 && Math.abs(q.px - q.b.w / 2) < 1.0) c = [60, 34, 40];
      if (q.f === 'north' && q.py > 1.6 && q.py < 2.2 && Math.abs(q.px - q.b.w / 2) < 0.4) c = [60, 34, 40];
      return c;
    };
    P.jaw = q => q.f === 'top' ? (q.pz < 1 ? [250, 248, 240] : shade([170, 60, 70], q, 10)) : shade([246, 238, 216], q, 11);
    P.fang = q => cel([250, 246, 230], 0.5 + 0.5 * (1 - q.ny), 0.85, 1.08);
    P.ear = q => { let c = shade(base, q, 12); if (q.f === 'north') c = q.px > 0.5 && q.px < q.b.w - 0.5 && q.py > 0.6 ? [246, 238, 216] : [34, 26, 22]; return c; };
    return P;
  }

  function rigDef(P, v, low) {
    const leg = (s, front) => {
      const k = (front ? 'F' : 'T') + (s < 0 ? 'D' : 'E'), w = front ? 4.4 : 4.8;
      return {
        name: 'perna' + k, pivot: [s * 4.2, 1.5, front ? -8.5 : 8], boxes: [{ x: -w / 2, y: -2, z: -w / 2, w, h: 8, d: w, p: P.leg }],
        kids: [{
          name: 'canela' + k, pivot: [0, 6, 0], boxes: [{ x: -w / 2 + 0.3, y: 0, z: -w / 2 + 0.3, w: w - 0.6, h: 6.5, d: w - 0.6, p: P.leg }],
          kids: [{ name: 'pata' + k, pivot: [0, 6.5, 0], boxes: [{ x: -w / 2 - 0.1, y: 0, z: -w / 2 - 1.4, w: w + 0.2, h: 2, d: w + 1.6, p: P.paw }] }],
        }],
      };
    };
    const fangs = low ? [] : [-1, 1].map(s => ({ x: s * 1.7 - 0.5, y: 1.4, z: -11.0, w: 1, h: v.fangs, d: 1, p: P.fang }));
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'quadril', pivot: [0, 8, 0], boxes: [
          { x: -5.2, y: -4.8, z: -11, w: 10.4, h: 9, d: 21, p: P.fur },
          ...(low ? [] : [{ x: -5.8, y: -5.2, z: -11.8, w: 11.6, h: 8.6, d: 7, p: P.fur }]),   // ombro mais largo
        ], kids: [
          {
            name: 'cabeca', pivot: [0, -2.6, -11.4], boxes: [
              { x: -4.5, y: -5, z: -7.4, w: 9, h: 8, d: 8, p: P.head },
              { x: -2.8, y: -0.6, z: -10.6, w: 5.6, h: 3.2, d: 3.4, p: P.muzzle },
              { x: -5.6, y: -1.4, z: -6.4, w: 1.4, h: 4, d: 4, p: P.fur }, { x: 4.2, y: -1.4, z: -6.4, w: 1.4, h: 4, d: 4, p: P.fur },   // juba da bochecha
              { x: -4.4, y: -7, z: -3.6, w: 2.6, h: 2.2, d: 1.2, p: P.ear }, { x: 1.8, y: -7, z: -3.6, w: 2.6, h: 2.2, d: 1.2, p: P.ear },
              ...fangs,
            ],
            kids: [{ name: 'mandibula', pivot: [0, 2.6, -3.4], boxes: [{ x: -2.2, y: 0, z: -6.4, w: 4.4, h: 1.6, d: 6.0, p: P.jaw }] }],
          },
          leg(-1, true), leg(1, true), leg(-1, false), leg(1, false),
          {
            name: 'cauda1', pivot: [0, -3.4, 10], rot: [-0.5, 0, 0], boxes: [{ x: -1.2, y: -1.2, z: 0, w: 2.4, h: 2.4, d: 8, p: P.tail }],
            kids: [{ name: 'cauda2', pivot: [0, 0, 8], rot: [0.6, 0, 0], boxes: [{ x: -1.1, y: -1.1, z: 0, w: 2.2, h: 2.2, d: 8, p: P.tail }] }],
          },
        ],
      }],
    };
  }

  const K = 1.2, Y0 = 1.5 * K;
  function anim(P, t, mode, a, obj, e, ctx) {
    obj.scale.set(-K / 16, -K / 16, K / 16); obj.position.y += Y0 - 1.5;
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const at = (n, off) => { obj.updateMatrixWorld(true); return P[n].obj.localToWorld(off.clone()); };
    const breath = loop([[0, 0], [0.45, 1], [0.999, 0]], t / 2.8);
    const tailIdle = (k = 1) => { r('cauda1', 0.1 * Math.sin(t * 1.3) * k, 0.4 * Math.sin(t * 1.1) * k, 0); r('cauda2', 0.2 * Math.sin(t * 1.7 + 1) * k, 0.4 * Math.sin(t * 1.1 - 0.8) * k, 0); };
    // empinar: gira o corpo pra trás em volta do quadril de trás e mantém as patas de trás em pé
    const rear = k => {
      const ang = -0.85 * k;
      r('quadril', ang, 0, 0);
      P.quadril.obj.position.y += -8 * Math.sin(-ang) + 1.2 * k; P.quadril.obj.position.z += 8 * (1 - Math.cos(ang));
      r('pernaTD', -ang, 0, 0); r('pernaTE', -ang, 0, 0); r('cabeca', 0.6 * k, 0, 0); r('cauda1', 0.5 * k, 0, 0);
    };

    if (mode === 'parado') {
      // deitado de lado não: em pé, respirando, cauda balançando, lambe o beiço de vez em quando
      tailIdle();
      P.quadril.obj.position.y += 0.25 * breath;
      r('cabeca', 0.05 * breath, 0.3 * Math.sin(t * 0.5), 0);
      const lick = seq([[0.7, 0], [0.75, 1], [0.85, 1], [0.9, 0]], fr(t / 4));
      r('mandibula', 0.35 * lick, 0, 0);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 1.0 * Math.PI * 2;
      for (const [k, off] of [['FD', 0], ['TE', 0.1], ['FE', Math.PI], ['TD', Math.PI + 0.1]]) {
        r('perna' + k, 0.45 * Math.sin(ph + off), 0, 0); r('canela' + k, (k[0] === 'F' ? -0.3 : 0.4) * Math.max(0, Math.sin(ph + off + 1.3)), 0, 0);
        r('pata' + k, -0.3 * Math.max(0, -Math.sin(ph + off)), 0, 0);
      }
      P.quadril.obj.position.y += 0.4 * Math.abs(Math.sin(ph));
      r('quadril', 0, 0, 0.03 * Math.sin(ph)); r('cabeca', 0.05 * Math.sin(ph * 2), 0, 0);
      tailIdle(0.6);
      return;
    }

    if (mode === 'bote') {
      const T = t % 3.6;
      const R = ctx.ref; const RZ = -6; R.place(0, RZ, 0);
      // 0-0.9: abaixa o peito, traseiro pra cima mexendo, cauda chicoteando; 0.9-1.35: pulo; 1.35: derruba;
      // 1.4-2.3: morde sacudindo a cabeça; 2.6: solta e volta
      const crouch = seq([[0, 0], [0.5, 1, 'out'], [0.9, 1], [1.0, 0]], T);
      const wig = crouch * Math.sin(T * 26) * (T > 0.5 ? 1 : 0);
      P.quadril.obj.position.y += 2.5 * crouch; r('quadril', 0.12 * crouch, 0.04 * wig, 0);
      r('pernaFD', -0.5 * crouch, 0, 0); r('pernaFE', -0.5 * crouch, 0, 0); r('canelaFD', 1.0 * crouch, 0, 0); r('canelaFE', 1.0 * crouch, 0, 0);
      r('pernaTD', 0.4 * crouch, 0, 0); r('pernaTE', 0.4 * crouch, 0, 0); r('canelaTD', -0.6 * crouch, 0, 0); r('canelaTE', -0.6 * crouch, 0, 0);
      r('cauda1', 0, 0.8 * Math.sin(T * 9) * crouch, 0);
      const jump = seq([[0.95, 0], [1.35, 1, 'io']], T), back = seq([[2.6, 0], [3.2, 1, 'io']], T);
      const dz = (RZ + 0.4) * jump * (1 - back);
      obj.position.z += dz; obj.position.y += 1.3 * Math.sin(Math.PI * jump) * (jump < 1 ? 1 : 0) + 0.6 * Math.sin(Math.PI * back) * (back > 0 && back < 1 ? 1 : 0);
      const air = Math.sin(Math.PI * jump) * (jump < 1 ? 1 : 0);
      r('pernaFD', -1.3 * air, 0, 0); r('pernaFE', -1.3 * air, 0, 0); r('pernaTD', 1.0 * air, 0, 0); r('pernaTE', 1.0 * air, 0, 0);
      r('quadril', -0.25 * air, 0, 0);
      const pin = seq([[1.3, 0], [1.4, 1], [2.5, 1], [2.7, 0]], T);
      r('quadril', 0.2 * pin, 0, 0); r('pernaFD', -0.9 * pin, 0, 0); r('pernaFE', -0.9 * pin, 0, 0); r('canelaFD', 0.5 * pin, 0, 0); r('canelaFE', 0.5 * pin, 0, 0);
      const bite = seq([[1.4, 0], [1.5, 1], [2.3, 1], [2.45, 0]], T);
      r('mandibula', 0.6 * (1 - bite) * pin + 0.1, 0, 0); r('cabeca', 0.5 * bite, 0.35 * Math.sin(T * 22) * bite, 0.15 * Math.sin(T * 22) * bite);
      // alvo cai de costas e fica preso
      const fall = seq([[1.3, 0], [1.5, 1, 'out'], [2.6, 1], [3.2, 0, 'io']], T);
      R.lean(-1.4 * fall); R.place(0, RZ - 0.6 * fall, 0);
      R.armsOut(0.6 * fall);
      if (T > 0.95 && T < 1.6) ctx.get('poeira1', () => DBFX.grains({ n: 40, seed: 81, palette: [[170, 140, 100], [130, 104, 76], [200, 176, 140]], size: [0.02, 0.05], drag: 3, grav: 3, life: 0.6, spread: 0.1, cone: Rr => { const a = Rr() * 6.28; return [Math.cos(a) * 2.2, 0.6 + Rr() * 1.4, Math.sin(a) * 2.2 + 0.8]; } })).at(V3(0, 0.05, 0.3), T - 0.95);
      if (T > 1.3 && T < 2.2) ctx.get('poeira2', () => DBFX.grains({ n: 50, seed: 83, palette: [[170, 140, 100], [130, 104, 76], [200, 176, 140]], size: [0.02, 0.05], drag: 3, grav: 3, life: 0.6, spread: 0.1, cone: Rr => { const a = Rr() * 6.28; return [Math.cos(a) * 3, 0.5 + Rr() * 1.6, Math.sin(a) * 3]; } })).at(V3(0, 0.05, RZ + 0.2), T - 1.3);
      if (T > 1.3 && T < 1.7) ctx.shake(0.7 * (1 - (T - 1.3) / 0.4));
      const mouth = at('mandibula', V3(0, 1, -6));
      if (T > 1.5 && T < 2.5) ctx.get('sangue', () => DBFX.grains({ n: 30, seed: 87, palette: [[170, 20, 24], [120, 10, 16], [210, 40, 40]], size: [0.015, 0.035], drag: 2, grav: 8, life: 0.5, spread: 0.8, cone: Rr => [(Rr() - 0.5) * 2, 0.5 + Rr(), (Rr() - 0.5) * 2] })).at(mouth, T - 1.5);
      return;
    }

    if (mode === 'patada') {
      const T = t % 3.4;
      const R = ctx.ref; const RZ = -3.6; R.place(0, RZ, 0);
      // empina, patada direita (1.0), patada esquerda (1.35), desce rugindo (1.9-2.8)
      const up = seq([[0, 0], [0.5, 1, 'out'], [1.7, 1], [1.95, 0, 'in']], T);
      rear(up);
      const s1 = seq([[0.75, 0], [0.95, -1, 'out'], [1.08, 1, 'snap'], [1.35, 0.3]], T);
      const s2 = seq([[1.1, 0], [1.3, -1, 'out'], [1.42, 1, 'snap'], [1.7, 0.3]], T);
      r('pernaFD', -1.4 * up - 0.9 * s1, 0, 0.35 * up - 0.6 * Math.max(0, s1)); r('canelaFD', -0.4 * up, 0, 0);
      r('pernaFE', -1.4 * up - 0.9 * s2, 0, -0.35 * up + 0.6 * Math.max(0, s2)); r('canelaFE', -0.4 * up, 0, 0);
      r('pataFD', -0.6 * up, 0, 0); r('pataFE', -0.6 * up, 0, 0);
      r('quadril', 0, 0.15 * (s1 - s2), 0);
      const roar = seq([[1.95, 0], [2.1, 1, 'out'], [2.7, 1], [2.95, 0]], T);
      r('mandibula', 0.15 + 0.4 * up + 0.7 * roar, 0, 0); r('cabeca', -0.35 * roar, 0, 0);
      P.quadril.obj.position.y += -1.0 * roar; r('quadril', -0.08 * roar, 0, 0);
      for (const [k, st, dx] of [['g1', 1.02, -0.18], ['g2', 1.36, 0.18]]) {
        const age = (T - st) / 0.5;
        if (age > 0 && age < 1) ctx.get(k, clawMesh).at(V3(dx, 1.2, RZ + 0.3), age);
        if (age > 0 && age < 0.15) { ctx.shake(0.5); ctx.flash(V3(dx, 1.2, RZ + 0.3), 0.6); }
      }
      R.lean(-0.3 * seq([[1.02, 0], [1.1, 1], [1.3, 0.4], [1.45, 1.2], [2.2, 1], [2.8, 0]], T));
      R.spin(0.35 * seq([[1.02, 0], [1.1, 1], [1.36, 1], [1.45, -1], [2.4, -1], [2.9, 0]], T));
      if (T > 1.95 && T < 3.2) {
        const k = (T - 1.95) / 1.2;
        ctx.roarRing(at('mandibula', V3(0, 0, -5)), k);
        ctx.shake(0.45 * roar);
        ctx.get('folhas', () => DBFX.grains({ n: 36, seed: 91, palette: [[120, 160, 70], [90, 130, 50], [170, 150, 90]], size: [0.025, 0.05], drag: 2.5, grav: 1.2, life: 1.2, spread: 0.4, cone: Rr => { const a = -Math.PI / 2 + (Rr() - 0.5) * 1.6; return [Math.cos(a) * 3, 0.4 + Rr(), Math.sin(a) * 4]; } })).at(V3(0, 0.3, RZ + 0.5), T - 1.95);
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(3), variant(21), variant(57)];
  fixed[0].scar = false; fixed[0].white = false; fixed[1].scar = true; fixed[1].white = false; fixed[2].white = true;
  const def = v => ({ def: rigDef(painters(v), v, false), low: rigDef(painters(v), v, true) });
  window.MOBS.push({
    id: 'tigre', nome: 'Tigre-dente-de-sabre', bioma: 'Rios e florestas', tier: 'Médio · tier 7',
    variants: fixed.map((v, i) => ({ nome: ['Laranja', 'Cicatriz', 'Branco (raro)'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['bote', 'Bote'], ['patada', 'Patada Dupla']],
    cam: 7, lookY: 1.0, lookZ: { bote: -3, patada: -1.8 }, camFar: { bote: 9, patada: 8 }, camYaw: { bote: -1.25, patada: -1.0 }, refX: 3.2,
    info: {
      'Tamanho': '1,6 bloco de altura, 3 de comprimento',
      'Vida': '70 (tier 7)',
      'Jeito': 'Sozinho, nas florestas de Paozu e na beira dos rios. Fica de tocaia: você só vê a cauda mexendo no mato. Ataca quem chega perto de costas.',
      'Bote': 'Abaixa o peito e mexe o traseiro (aviso), pula até 6 blocos, te derruba e morde sacudindo. Preso, aperta pulo pra se soltar mais rápido. Dano da mordida em pacotes (Escudo de tier).',
      'Patada Dupla': 'Empina nas patas de trás e dá duas patadas com as garras (dá pra recuar no empinar), depois ruge e espanta tudo em volta.',
      'Solta': 'Carne de tigre (crua e assada), couro listrado, dente de sabre; raro: pele de tigre branco (tapete).',
      'Variações': 'Cor da pelagem, largura das listras, tamanho do dentão e cicatriz no olho sorteados. Tigre branco é raro.',
    },
  });
})();
