// Caranguejo de Cracas (Mar de Dagon). Tier 8. Maldição do medo de pisar em coisa viva na areia: caranguejo do
// tamanho de um porco, o casco coberto de cracas (as cascas brancas que grudam em pedra e barco), uma pinça enorme
// e outra pequena, olhos em antena e, na frente do casco, uma boca de gente com dentes de gente, sempre sorrindo.
// Golpes: Pinça Esmagadora (agarra você pela cintura, aperta até estalar e joga de lado) e Cuspe de Cracas (empina,
// as cracas abrem as penas e cospem cascas que grudam em você e deixam lento).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  // [nome, casco, barriga, cracas, olho]
  const KINDS = [
    ['Casco de Lama', [120, 84, 60], [214, 190, 160], [214, 206, 190], [240, 200, 60]],
    ['Casco Vermelho', [168, 58, 40], [232, 196, 168], [226, 222, 214], [30, 30, 30]],
    ['Lodo Verde', [64, 82, 58], [170, 176, 140], [196, 200, 180], [230, 70, 50]],
  ];
  function variant(seed, kind) {
    const R = FX.rnd(seed);
    const k = kind ?? Math.floor(R() * KINDS.length);
    // cracas: cada uma num lugar do casco, com tamanho próprio
    const bar = [];
    for (let i = 0; i < 13; i++) bar.push({ x: (R() - 0.5) * 11, z: (R() - 0.5) * 8, s: 0.7 + R() * 0.6, top: R() < 0.7 });
    return { seed, kind: k, shell: KINDS[k][1], belly: KINDS[k][2], barn: KINDS[k][3], iris: KINDS[k][4], bar, size: 0.95 + R() * 0.12, missing: Math.floor(R() * 6) };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.74, hi = 1.16) => {
      const f = 0.62 - 0.3 * q.ny + 0.3 * (n3(q.ax * 0.35, q.ay * 0.35, q.az * 0.35, s + S) - 0.5) + (q.f === 'top' ? 0.22 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const P = {};
    // casco: pintinhas, borda escura, alga verde em cima, marcas redondas onde cracas caíram
    const shell = (q, s) => {
      let c = shade(v.shell, q, s);
      if (n3(q.ax * 0.9, q.ay * 0.9, q.az * 0.9, 20 + S) > 0.7) c = mul(c, 0.8);
      if (n3(q.ax * 2.2, q.ay * 2.2, q.az * 2.2, 21 + S) > 0.8) c = mix(c, v.belly, 0.4);
      if (q.f === 'top' && n3(q.ax * 0.25, 0, q.az * 0.25, 22 + S) > 0.64) c = mix(c, [70, 110, 60], 0.55);
      if (q.f === 'top') { const cx = fr(q.ax * 0.23 + 0.3), cz = fr(q.az * 0.27 + 0.6); if (Math.hypot(cx - 0.5, cz - 0.5) < 0.12 && n3(q.ax * 0.2, 1, q.az * 0.2, 23 + S) > 0.55) c = mix(c, [230, 226, 214], 0.5); }
      return c;
    };
    P.shell = q => shell(q, 1);
    P.dome = q => shell(q, 2);
    // frente do casco: boca de gente com dentes de gente (gengiva, dentes chatos, um faltando)
    P.front = q => {
      let c = shell(q, 3);
      if (q.f === 'bottom') return shade(v.belly, q, 4);
      if (q.f !== 'north') return c;
      const x = q.px, y = q.py, W = q.b.w, H = q.b.h;
      const mx = Math.abs(x - W / 2) / (W * 0.36);
      const top = H * 0.38 + 0.6 * mx * mx;                                                     // linha do sorriso curvada
      if (mx < 1 && y > top && y < H - 0.15) {
        const ty = y - top;
        if (ty < 0.5) return [150, 50, 64];                                                        // gengiva
        const col = Math.floor((x - W / 2) / 1.05 + 20);
        if (col === 20 + v.missing - 3) return [30, 8, 14];                                         // dente faltando
        if (fr((x - W / 2) / 1.05) < 0.12) return [70, 50, 40];                                      // vão entre dentes
        return ty > 1.6 ? [30, 8, 14] : mix([236, 226, 196], [196, 180, 140], ty / 1.6);
      }
      if (mx < 1.05 && Math.abs(y - top) < 0.5) return [110, 40, 50];                              // lábio
      return c;
    };
    P.lip = q => {
      let c = mix(shell(q, 5), [150, 60, 70], 0.4);
      if (q.f === 'north' && q.ny < 0.55) { const x = q.px - q.b.w / 2; c = fr(x / 1.05) < 0.12 ? [70, 50, 40] : [224, 214, 184]; }
      return c;
    };
    P.mouthIn = () => [26, 6, 12];
    P.belly = q => { let c = shade(v.belly, q, 6); if (fr(q.az * 0.4) < 0.1) c = mul(c, 0.85); return c; };
    // cracas: cone branco com o furo escuro em cima e as plaquinhas fechando o furo
    P.barn = q => {
      let c = shade(v.barn, q, 7, 0.78, 1.1);
      if (q.f !== 'top' && fr(q.py * 1.3 + q.px * 0.4) < 0.25) c = mul(c, 0.84);
      if (q.f === 'top') { const d = Math.hypot(q.nx - 0.5, q.nz - 0.5); if (d < 0.22) c = [40, 34, 30]; else if (d < 0.3) c = [150, 140, 120]; }
      if (n3(q.ax * 1.5, q.ay * 1.5, q.az * 1.5, 24 + S) > 0.8) c = mix(c, [80, 120, 70], 0.5);
      return c;
    };
    P.plume = q => { let c = shade([214, 160, 120], q, 8, 0.8, 1.12); if (fr(q.ay * 1.5) < 0.3) c = mul(c, 0.8); return c; };
    P.stalk = q => shade(mul(v.shell, 1.1), q, 9);
    P.eye = q => {
      const d = Math.hypot(q.nx - 0.5, q.ny - 0.5);
      if (q.f === 'north' || q.f === 'east' || q.f === 'west') { if (d < 0.18) return [10, 8, 10]; if (d < 0.36) return v.iris; }
      return [24, 20, 26];
    };
    P.claw = q => {
      let c = shade(mix(v.shell, [230, 200, 170], 0.15), q, 10);
      if (n3(q.ax * 1.1, q.ay * 1.1, q.az * 1.1, 25 + S) > 0.74) c = mul(c, 0.8);
      return c;
    };
    // dedos da pinça: ponta escura e serrilha clara no lado de dentro
    P.finger = q => {
      let c = P.claw(q);
      if (q.nz < 0.22) c = [40, 28, 26];
      if (((q.f === 'bottom' && q.b.y < 0) || (q.f === 'top' && q.b.y >= 0)) && fr(q.pz * 0.9) < 0.45) c = [236, 220, 190];
      return c;
    };
    P.leg = q => { let c = shade(v.shell, q, 11); if (q.ny > 0.85) c = [40, 28, 26]; if (fr(q.ay * 0.5) < 0.1) c = mul(c, 0.8); return c; };
    return P;
  }

  function rig(P, v, lo) {
    // pinça: braço pra frente, antebraço vira pra dentro, palma e dois dedos (o de cima mexe)
    const claw = (s, k) => {
      const n = s < 0 ? 'D' : 'E';
      return {
        name: 'braco' + n, pivot: [s * 5.6, 0.6, -4.4], rot: [0.15, -s * 0.55, 0], boxes: [{ x: -1.2 * k, y: -1.2 * k, z: -5 * k, w: 2.4 * k, h: 2.4 * k, d: 5 * k, p: P.claw }],
        kids: [{
          name: 'ante' + n, pivot: [0, 0, -5 * k], rot: [0, s * 1.0, 0], boxes: [{ x: -1.4 * k, y: -1.4 * k, z: -4 * k, w: 2.8 * k, h: 2.8 * k, d: 4 * k, p: P.claw }],
          kids: [{
            name: 'pinca' + n, pivot: [0, 0, -4 * k], rot: [-0.15, 0, 0], boxes: [
              { x: -2.4 * k, y: -2.6 * k, z: -6 * k, w: 4.8 * k, h: 4.6 * k, d: 6 * k, p: P.claw },
              { x: -1.6 * k, y: 0.2 * k, z: -10.4 * k, w: 3.2 * k, h: 1.8 * k, d: 4.6 * k, p: P.finger },                 // dedo fixo (de baixo)
              ...(lo ? [] : [{ x: -1.2 * k, y: 0.6 * k, z: -11.2 * k, w: 2.4 * k, h: 1.2 * k, d: 1 * k, p: P.finger }]),
            ],
            kids: [{ name: 'dedo' + n, pivot: [0, -1.4 * k, -6 * k], boxes: [{ x: -1.5 * k, y: -0.4 * k, z: -4.8 * k, w: 3 * k, h: 1.8 * k, d: 4.8 * k, p: P.finger }, ...(lo ? [] : [{ x: -1.1 * k, y: 0.6 * k, z: -5.6 * k, w: 2.2 * k, h: 1.2 * k, d: 1 * k, p: P.finger }])] }],
          }],
        }],
      };
    };
    const leg = (s, i) => {
      const n = (s < 0 ? 'D' : 'E') + i, z = [-2.6, 0.6, 3.6][i], fan = s * [0.5, 0, -0.5][i];
      return {
        name: 'perna' + n, pivot: [s * 6.2, 17.6, z], rot: [0, fan, 0], kids: [{
          name: 'coxa' + n, pivot: [0, 0, 0], rot: [0, 0, -s * 0.32], boxes: [{ x: s > 0 ? 0 : -7.2, y: -1, z: -1, w: 7.2, h: 2, d: 2, p: P.leg }],
          kids: [{
            name: 'canela' + n, pivot: [s * 7, 0, 0], rot: [0, 0, -s * 0.24], boxes: [
              { x: -0.8, y: 0, z: -0.8, w: 1.6, h: 8.6, d: 1.6, p: P.leg },
              ...(lo ? [] : [{ x: -0.5, y: 8.6, z: -0.5, w: 1, h: 1.4, d: 1, p: P.leg }]),
            ],
          }],
        }],
      };
    };
    const bar = (lo ? v.bar.slice(0, 4) : v.bar).flatMap((b, i) => {
      const y = b.top ? -5 : -3;
      if (!b.top && Math.abs(b.x) < 5.4 && Math.abs(b.z) < 4.4) return [];
      const k = b.s;
      return [
        { x: b.x - 1.1 * k, y: y - 1.4 * k, z: b.z - 1.1 * k, w: 2.2 * k, h: 1.4 * k, d: 2.2 * k, p: P.barn },
        ...(lo ? [] : [{ x: b.x - 0.7 * k, y: y - 2.2 * k, z: b.z - 0.7 * k, w: 1.4 * k, h: 0.8 * k, d: 1.4 * k, p: P.barn }]),
      ];
    });
    // penas das cracas (só aparecem no cuspe): saem do furo das 4 maiores
    const big = v.bar.filter(b => b.top).slice(0, 4);
    const plumes = lo ? [] : big.map((b, i) => ({ name: 'pluma' + i, pivot: [b.x, -5 - 2.2 * b.s, b.z], boxes: [{ x: -0.3, y: -3.2, z: -0.3, w: 0.6, h: 3.2, d: 0.6, p: P.plume }, { x: -1.2, y: -3.6, z: -0.2, w: 2.4, h: 0.6, d: 0.4, p: P.plume }, { x: -0.2, y: -3.0, z: -1.0, w: 0.4, h: 0.6, d: 2.0, p: P.plume }] }));
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'casco', pivot: [0, 17, 0], boxes: [
            { x: -7, y: -3, z: -5.5, w: 14, h: 5, d: 11, p: P.shell },
            { x: -5.6, y: -5, z: -4.6, w: 11.2, h: 2, d: 9.2, p: P.dome },
            { x: -6.4, y: 2, z: -4.8, w: 12.8, h: 1.2, d: 9.6, p: P.belly },
            // frente com a boca (caixa fina na frente do casco)
            { x: -6, y: -2.6, z: -6, w: 12, h: 4.4, d: 0.6, p: P.front },
            { x: -4.2, y: 0.3, z: -5.9, w: 8.4, h: 1.4, d: 0.2, p: P.mouthIn },
            ...bar,
          ],
          kids: [
            { name: 'labio', pivot: [0, 1.6, -6.1], boxes: [{ x: -4.2, y: -0.2, z: -0.4, w: 8.4, h: 1.4, d: 0.6, p: P.lip }] },
            ...['D', 'E'].map((n, j) => ({
              name: 'olho' + n, pivot: [(j ? 1 : -1) * 2.6, -4.6, -4.2], rot: [-0.25, 0, (j ? 1 : -1) * 0.15], boxes: [{ x: -0.5, y: -4, z: -0.5, w: 1, h: 4, d: 1, p: P.stalk }, { x: -1, y: -6, z: -1, w: 2, h: 2, d: 2, p: P.eye }],
            })),
            claw(-1, 1), claw(1, 0.58),
            ...plumes,
          ],
        },
        ...[0, 1, 2].flatMap(i => [leg(-1, i), leg(1, i)]),
      ],
    };
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size * 1.1; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const pinch = (n, k) => r('dedo' + n, -0.7 * k, 0, 0);                                  // 0 fechada, 1 aberta
    const grin = k => { if (P.labio) P.labio.obj.position.y += 1.3 * k; r('labio', 0.3 * k, 0, 0); };
    const plumes = k => { for (let i = 0; i < 4; i++) if (P['pluma' + i]) { const o = P['pluma' + i].obj; const kk = Math.max(0.01, 1.35 * FX.clamp01(k * 1.4 - i * 0.12)); o.scale.set(kk, kk, kk); r('pluma' + i, 0.4 * Math.sin(t * 9 + i), 0, 0.4 * Math.sin(t * 7 + i * 2)); } };
    plumes(0);
    // pernas: cada uma levanta na sua vez (tripé alternado), joelho sobe e o pé desce de novo
    const legs = (ph, amp, side = 0) => {
      for (const n of ['D', 'E']) for (let i = 0; i < 3; i++) {
        const sg = n === 'D' ? -1 : 1, off = (i % 2 ? Math.PI : 0) + (n === 'D' ? Math.PI : 0);
        const lift = Math.max(0, Math.sin(ph + off));
        r('coxa' + n + i, 0, 0, -sg * 0.35 * lift * amp);
        r('canela' + n + i, 0, 0, sg * 0.2 * lift * amp + sg * 0.12 * side * Math.sin(ph + off));
        r('perna' + n + i, 0, 0.12 * amp * Math.cos(ph + off), 0);
      }
    };
    const eyes = () => {
      r('olhoD', 0.2 * Math.sin(t * 0.9), 0.5 * loop([[0, 0], [0.2, 0.8], [0.5, 0.8], [0.6, -0.6], [1, 0]], t / 5), 0);
      r('olhoE', 0.2 * Math.sin(t * 1.1 + 2), 0.5 * loop([[0, 0.3], [0.35, -0.7], [0.7, 0.5], [1, 0.3]], t / 6.5), 0);
    };

    if (mode === 'parado') {
      // parado de lado na areia: olhos cada um pra um canto, a pinça grande abrindo e fechando (tec... tec), dentes batendo
      eyes();
      pinch('D', 0.5 * Math.max(0, Math.sin(t * 1.6)) ** 2); pinch('E', 0.4 * Math.max(0, Math.sin(t * 2.3 + 1)) ** 2);
      r('bracoD', 0.06 * Math.sin(t * 0.8), 0, 0); r('bracoE', 0.08 * Math.sin(t * 1.2 + 1), 0.1 * Math.sin(t * 0.7), 0);
      grin(0.25 * Math.max(0, Math.sin(t * 5)) * (Math.sin(t * 0.7) > 0.4 ? 1 : 0));
      obj.position.y += 0.015 * Math.sin(t * 2);
      legs(t * 0.6, 0.15);
      return;
    }
    if (mode === 'andar') {
      // de lado, rápido: vai e volta, o casco balança pro lado que anda
      const X = 1.3 * Math.sin(t * 0.9), vx = Math.cos(t * 0.9);
      obj.position.x += X;
      legs(t * 12, 1, vx);
      r('casco', 0, 0, 0.06 * vx * Math.abs(Math.sin(t * 12)));
      obj.position.y += 0.02 * Math.abs(Math.sin(t * 12));
      eyes(); r('bracoD', -0.15, 0, 0); pinch('D', 0.3); pinch('E', 0.3 + 0.3 * Math.sin(t * 6));
      return;
    }
    if (mode === 'pincar') {
      const T = t % 4.6;
      const R = ctx.ref; const RZ = -1.75; R.place(0.38, RZ, 0); R.spin(Math.PI);
      // corre de lado até o alvo, levanta a pinça aberta, fecha na cintura, levanta, aperta (estala), joga pro lado
      const step = seq([[0, 0], [0.5, 1, 'out']], T);
      obj.position.z -= 0.35 * step; legs(T < 0.5 ? T * 14 : 0, T < 0.5 ? 1 : 0.2);
      const raise = seq([[0.4, 0], [0.85, 1, 'out'], [1.05, 1], [1.2, 0.35, 'snap'], [1.6, 0.6, 'out'], [2.9, 0.6], [3.25, -0.2, 'in'], [3.9, 0, 'io']], T);
      const open = seq([[0.4, 0], [0.8, 1, 'out'], [1.08, 1], [1.16, 0, 'snap'], [3.25, 0], [3.4, 1, 'out'], [4.2, 0.2]], T);
      r('bracoD', -0.9 * raise, 0.25 * raise, 0); r('anteD', 0.3 * raise, 0, 0); r('pincaD', 0.2 * raise, 0, 0); pinch('D', 1.2 * open);
      const swing = seq([[2.8, 0], [3.25, 1, 'in'], [3.6, 1], [4.2, 0]], T);
      r('bracoD', 0, -0.9 * swing, 0);
      r('casco', -0.12 * raise, 0, 0);
      const held = T > 1.16 && T < 3.3;
      const lift = seq([[1.2, 0], [1.6, 1, 'out'], [2.8, 1], [3.1, 0.6]], T);
      r('bracoD', -0.55 * lift, 0, 0); r('casco', -0.1 * lift, 0, 0);
      if (held) {
        // o alvo fica preso na pinça: a cintura dele segue o ponto entre os dedos
        obj.updateMatrixWorld(true);
        const g = new THREE.Vector3(0, 0, -7).applyMatrix4(P.pincaD.obj.matrixWorld);
        R.place(g.x, g.z - 0.05, Math.max(0, g.y - 0.8)); R.armsOut(0.15); R.lean(-0.1 * lift);
        // aperto: três estalos com tremida, o alvo se contorce
        for (const t0 of [1.8, 2.25, 2.7]) { const k = T - t0; if (k > 0 && k < 0.3) { pinch('D', -0.15 * (1 - k / 0.3)); ctx.shake(0.6 * (1 - k / 0.3)); R.lean(-0.2 * (1 - k / 0.3)); R.armsOut(0.6 * (1 - k / 0.3)); if (k < 0.1) ctx.flash(new THREE.Vector3(0, 1.1, RZ), 1 - k / 0.1); } }
      }
      grin(seq([[1.1, 0], [1.3, 1], [2.9, 1], [3.3, 0.3]], T) * (0.7 + 0.3 * Math.sin(T * 14)));
      // jogado de lado
      const fly = FX.clamp01((T - 3.3) / 0.7);
      if (T > 3.3) { R.place(0.4 - 2.4 * FX.EASE.out(fly), RZ - 0.4 * fly, 0.6 * Math.sin(Math.PI * fly) + 0.3 * (1 - fly)); R.lean(-0.6 * Math.min(1, fly * 2)); R.spin(Math.PI - 0.8 * fly); }
      if (T > 3.3 && T < 3.7) ctx.claw(new THREE.Vector3(-0.3, 1.1, RZ + 0.2), (T - 3.3) / 0.4);
      eyes();
      return;
    }
    if (mode === 'cuspe') {
      const T = t % 4.8;
      const R = ctx.ref; const RZ = -3.0; R.place(0, RZ, 0); R.spin(Math.PI);
      // empina a frente, as cracas abrem as penas (cheirando o ar), puxa e cospe as cascas em leque; elas grudam
      const rear = seq([[0.2, 0], [0.8, 1, 'out'], [2.6, 1], [3.0, 0, 'io']], T);
      r('casco', -0.35 * rear, 0, 0); obj.position.y += 0.08 * rear;
      r('bracoD', -0.5 * rear, -0.3 * rear, 0); r('bracoE', -0.6 * rear, 0.3 * rear, 0); pinch('D', 0.8 * rear); pinch('E', 0.8 * rear);
      plumes(seq([[0.5, 0], [1.1, 1, 'out'], [2.3, 1], [2.6, 0, 'in']], T));
      const suck = seq([[1.2, 0], [1.6, 1, 'io'], [1.7, -0.4, 'snap'], [2.2, 0]], T);
      r('casco', 0.12 * suck, 0, 0); grin(Math.max(0, -suck) * 2 + 0.3 * rear);
      eyes();
      // cascas: cada uma sai de uma craca diferente, arco próprio, e gruda num ponto do alvo
      obj.updateMatrixWorld(true);
      const N = 7, aims = [];
      for (let i = 0; i < N; i++) aims.push(new THREE.Vector3((i - 3) * 0.08 + 0.05 * Math.sin(i * 3), 0.4 + 0.15 * i + 0.05 * (i % 2), RZ + 0.14));
      for (let i = 0; i < N; i++) {
        const t0 = 1.7 + i * 0.05, f = FX.clamp01((T - t0) / 0.45);
        const m = ctx.get('casca' + i, barnacleMesh);
        if (T < t0) { m.visible = false; continue; }
        const b = e.v.bar[i % e.v.bar.length];
        const from = new THREE.Vector3(b.x, -6.5, b.z).applyMatrix4(P.casco.obj.matrixWorld);
        const to = aims[i];
        if (f < 1) { m.position.copy(from).lerp(to, f); m.position.y += 0.6 * Math.sin(Math.PI * f) * (1 + 0.3 * (i % 3)); m.rotation.set(T * 9 + i, T * 7, 0); }
        else { m.position.copy(to); m.rotation.set(-Math.PI / 2, 0, i); }
        m.visible = T < 4.6; m.scale.setScalar(1);
        if (f >= 1 && T - t0 - 0.45 < 0.12) ctx.get('pof' + i, () => DBFX.grains({ n: 8, seed: 40 + i, palette: [[220, 214, 200], [160, 150, 130]], size: [0.02, 0.04], drag: 3, grav: 4, life: 0.4, cone: R2 => [(R2() - 0.5) * 1.4, R2() * 0.8, (R2() - 0.2) * 1.4] })).at(to, T - t0 - 0.45, 0);
      }
      const stuck = FX.clamp01((T - 2.2) / 0.3);
      if (T > 2.15) { R.lean(-0.12 * Math.max(0, 1 - (T - 2.15) * 3)); R.armsOut(0.25 * stuck * (0.6 + 0.4 * Math.sin(T * 3))); ctx.dark(0.18 * stuck); }
      if (T > 1.7 && T < 2.0) ctx.shake(0.3);
      return;
    }
    if (mode === 'morte') {
      // vira de barriga pra cima, as pernas fecham pra dentro, a boca fica aberta; depois cinza (as cracas por último)
      const { T, fall } = JJFX.exorcize(t, obj, e, ctx, { h: 1.1, refZ: -2.2, rotX: 0.25, back: 0.3, ashY: 0.3, ashZ: 0, n: 150 });
      r('casco', 0, 0, 0.6 * fall);
      for (const n of ['D', 'E']) for (let i = 0; i < 3; i++) { const sg = n === 'D' ? -1 : 1; r('coxa' + n + i, 0, 0, -sg * 0.6 * fall); r('canela' + n + i, 0, 0, -sg * 0.9 * fall + 0.15 * Math.sin(T * 20 + i) * Math.max(0, 1 - T)); }
      obj.position.y -= 0.18 * fall;
      grin(fall); pinch('D', 0.6 * fall);
      r('olhoD', 0.9 * fall, 0, -0.4 * fall); r('olhoE', 0.9 * fall, 0, 0.4 * fall);
    }
  }

  // casca de craca voando: cone branco de dois andares com o furo escuro
  function barnacleMesh() {
    const g = new THREE.Group();
    const m1 = new THREE.MeshLambertMaterial({ color: 0xd8d2c2 }), m2 = new THREE.MeshLambertMaterial({ color: 0x2a221e }), m3 = new THREE.MeshLambertMaterial({ color: 0xa8a090 });
    const a = new THREE.Mesh(new THREE.BoxGeometry(0.17, 0.1, 0.17), m1); g.add(a);
    const b = new THREE.Mesh(new THREE.BoxGeometry(0.11, 0.06, 0.11), m3); b.position.y = 0.075; g.add(b);
    const c = new THREE.Mesh(new THREE.BoxGeometry(0.05, 0.014, 0.05), m2); c.position.y = 0.106; g.add(c);
    g.frustumCulled = false;
    return g;
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(6, 0), variant(23, 1), variant(41, 2)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'caranguejo', nome: 'Caranguejo de Cracas', bioma: 'Mar de Dagon', tier: 'Grau 3 · tier 8',
    variants: fixed.map(v => ({ nome: KINDS[v.kind][0], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado (' + KINDS[v.kind][0] + ')', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar de lado'], ['pincar', 'Pinça Esmagadora'], ['cuspe', 'Cuspe de Cracas'], ['morte', 'Exorcizado']],
    cam: 4.2, lookY: 0.7, lookZ: { pincar: -1.0, cuspe: -1.6, morte: 0 }, camFar: { andar: 5.2, pincar: 5.2, cuspe: 6.0 }, camYaw: { pincar: -1.1, cuspe: -1.3 },
    info: {
      'Tamanho': '0,9 bloco de altura, 1,5 de largura (2 com as pernas abertas)',
      'Vida': '40 (casco: flecha e espada de frente fazem metade)',
      'Jeito': 'Fica meio enterrado na areia com só os olhos pra fora. Anda de lado, rápido, e sempre de frente pra você, sorrindo.',
      'Pinça Esmagadora': 'Agarra pela cintura e levanta: 3 apertos (dano + quebra a armadura mais rápido) e te joga 2 blocos de lado. Dá pra soltar batendo na pinça grande.',
      'Cuspe de Cracas': 'Empina, as cracas abrem as penas e cospem 7 cascas em leque. Cada uma que pega gruda em você: -5% de velocidade por casca por 8 s (até 35%). Água tira todas.',
      'Fraqueza': 'Barriga: virado de costas (golpe pesado de cima) fica 2 s se debatendo e leva dano dobrado. A pinça pequena não defende.',
      'Solta': 'Craca, Carne de Caranguejo Amaldiçoada; raro: Pinça Gigante (arma pesada: o golpe carregado prende o alvo 1 s).',
      'Variações': 'Casco de lama, vermelho ou lodo verde; cracas em lugares diferentes; um dente faltando em lugar diferente.',
    },
  });
})();
