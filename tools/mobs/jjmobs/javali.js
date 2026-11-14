// Javali de Raízes (Hanami). Tier 7. Maldição do medo da mata que avança: um javali feito de madeira viva e
// raízes trançadas, musgo e flores nas costas, presas de raiz torcida, olhos verdes brilhando dentro de dois nós
// do tronco. Onde ele pisa brota mato.
// Golpes: Investida (raspa o chão, abaixa a cabeça e dispara: as presas te jogam por cima dele) e Raízes (empina e
// pisa: uma fileira de raízes pontudas rompe o chão até você, uma depois da outra).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  // [nome, madeira, musgo, flor, olho]
  const KINDS = [
    ['Carvalho', [112, 82, 56], [86, 128, 52], [246, 246, 236], [120, 255, 120]],
    ['Cerejeira', [96, 62, 58], [96, 136, 64], [246, 176, 200], [255, 140, 200]],
    ['Bétula Morta', [190, 184, 170], [120, 120, 70], [230, 200, 90], [255, 200, 60]],
  ];
  function variant(seed, kind) {
    const R = FX.rnd(seed);
    const k = kind ?? Math.floor(R() * KINDS.length);
    const twigs = []; for (let i = 0; i < 7; i++) twigs.push({ z: -8 + i * 2.4 + (R() - 0.5), x: (R() - 0.5) * 3, h: 2.5 + R() * 3, a: (R() - 0.5) * 0.6 });
    const flowers = []; for (let i = 0; i < 6; i++) flowers.push({ x: (R() - 0.5) * 8, z: -7 + R() * 14 });
    return { seed, kind: k, wood: KINDS[k][1], moss: KINDS[k][2], flower: KINDS[k][3], eye: KINDS[k][4], twigs, flowers, size: 0.95 + R() * 0.12, brokenTusk: R() < 0.3 };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.74, hi = 1.18) => {
      const f = 0.62 - 0.3 * q.ny + 0.3 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.22 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const P = {};
    // madeira: fibras correndo no comprimento do corpo (z), raízes trançadas em faixas, nós escuros
    const wood = (q, s, along = 'z') => {
      let c = shade(v.wood, q, s);
      const a = along === 'z' ? q.az : q.ay, b = along === 'z' ? q.ay + q.ax : q.ax + q.az;
      if (fr(b * 0.55 + 0.4 * n3(a * 0.15, b * 0.2, 0, s + 3 + S)) < 0.18) c = mul(c, 0.78);
      if (fr((a + b) * 0.18) < 0.06) c = mul(c, 0.7);                                                         // raiz enrolada atravessando
      if (n3(q.ax * 0.7, q.ay * 0.7, q.az * 0.7, s + 4 + S) > 0.78) c = mix(c, [40, 28, 20], 0.6);              // nó
      return c;
    };
    P.body = q => {
      let c = wood(q, 1);
      if (q.f === 'top' || (q.ny < 0.25 && q.f !== 'bottom')) { const m = n3(q.ax * 0.4, 0, q.az * 0.4, 5 + S); if (m > 0.4) c = shade(v.moss, q, 6, 0.78, 1.16); if (m > 0.4 && n3(q.ax * 1.5, 0, q.az * 1.5, 7 + S) > 0.75) c = mix(c, [180, 200, 90], 0.4); }
      return c;
    };
    P.moss = q => { let c = shade(v.moss, q, 8, 0.76, 1.18); if (n3(q.ax * 1.2, q.ay * 1.2, q.az * 1.2, 9 + S) > 0.7) c = mix(c, [60, 90, 40], 0.6); return c; };
    P.leg = q => { let c = wood(q, 10, 'y'); if (q.ny > 0.85) c = [60, 44, 30]; return c; };
    // cabeça: olhos verdes dentro de nós de tronco (anel escuro de casca + brilho), focinho com narinas de buraco
    P.head = q => {
      let c = wood(q, 11);
      if (q.f === 'east' || q.f === 'west') {
        const d = Math.hypot(q.pz - 2.6, q.py - 3.0);
        if (d < 0.75) return [250, 255, 240];
        if (d < 1.3) return v.eye;
        if (d < 1.9) return [36, 24, 16];
        if (d < 2.4 && fr(Math.atan2(q.py - 3, q.pz - 2.6) * 2) < 0.5) return mul(c, 0.7);
      }
      return c;
    };
    P.snout = q => {
      let c = wood(q, 12);
      if (q.f === 'north') { for (const nx of [-1.1, 1.1]) if (Math.hypot(q.px - q.b.w / 2 - nx, q.py - 1.6) < 0.7) return [20, 12, 8]; if (fr(Math.hypot(q.px - q.b.w / 2, q.py - 2) * 1.3) < 0.2) c = mul(c, 0.82); }
      return c;
    };
    // presa: raiz clara torcida, ponta escura
    P.tusk = q => { let c = shade([226, 210, 176], q, 13, 0.8, 1.12); if (fr(q.ay * 0.9 + q.az * 0.9) < 0.25) c = mul(c, 0.86); return c; };
    P.tip = () => [70, 54, 40];
    P.ear = q => { let c = shade(mix(v.moss, [200, 210, 120], 0.2), q, 14); if (Math.abs(q.nx - 0.5) < 0.12) c = mul(c, 0.8); return c; };
    P.twig = q => { let c = shade(mul(v.wood, 0.85), q, 15); if (q.ny < 0.15) c = shade(v.moss, q, 16); return c; };
    P.flower = q => { let c = shade(v.flower, q, 17, 0.84, 1.1); if (q.f === 'top' && Math.hypot(q.nx - 0.5, q.nz - 0.5) < 0.22) c = [240, 200, 60]; return c; };
    return P;
  }

  function rig(P, v, lo) {
    const leg = (sx, sz) => {
      const n = (sx < 0 ? 'D' : 'E') + (sz < 0 ? 'F' : 'T');
      return {
        name: 'perna' + n, pivot: [sx * 3.4, 4, sz * 6.2], boxes: [{ x: -1.8, y: -1, z: -1.8, w: 3.6, h: 5.4, d: 3.6, p: P.leg }],
        kids: [{ name: 'canela' + n, pivot: [0, 4.4, 0], boxes: [{ x: -1.4, y: 0, z: -1.4, w: 2.8, h: 4.2, d: 2.8, p: P.leg }, { x: -1.7, y: 3.4, z: -1.9, w: 3.4, h: 1.2, d: 3.6, p: P.leg }, ...(lo ? [] : [{ x: -2.2, y: 4.0, z: -0.6, w: 4.4, h: 0.6, d: 1.2, p: P.leg }])] }],
      };
    };
    const tusk = s => ({
      name: s < 0 ? 'presaD' : 'presaE', pivot: [s * 2.4, 1.2, -9.6], rot: [0, s * 0.25, s * 0.15], boxes: [
        { x: -0.6, y: -2.4, z: -0.6, w: 1.2, h: 2.4, d: 1.2, p: P.tusk },
        { x: -0.5, y: -4.2, z: -0.1, w: 1, h: 2, d: 1, p: P.tusk },
        ...(v.brokenTusk && s > 0 ? [] : [{ x: -0.4, y: -5.4, z: 0.6, w: 0.8, h: 1.4, d: 0.8, p: P.tip }]),
      ],
    });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'corpo', pivot: [0, 11, 0], boxes: [
          { x: -5, y: -5, z: -8.5, w: 10, h: 9.6, d: 17, p: P.body },
          { x: -5.6, y: -7, z: -8.5, w: 11.2, h: 3.4, d: 8.5, p: P.moss },                                  // corcova de musgo no ombro
          { x: -4, y: 4.6, z: -6, w: 8, h: 1, d: 12, p: P.body },
          ...(lo ? [] : v.twigs.map(t => ({ x: t.x - 0.4, y: -7 - t.h + (t.z > 0 ? 2 : 0), z: t.z - 0.4, w: 0.8, h: t.h, d: 0.8, p: P.twig }))),
          ...(lo ? [] : v.flowers.map(f => ({ x: f.x - 0.8, y: f.z < 0 ? -7.8 : -5.8, z: f.z - 0.8, w: 1.6, h: 0.8, d: 1.6, p: P.flower }))),
        ],
        kids: [
          {
            name: 'cabeca', pivot: [0, -1.6, -8.4], rot: [0.25, 0, 0], boxes: [
              { x: -4, y: -4.4, z: -7.6, w: 8, h: 8, d: 7.6, p: P.head },
              { x: -2.8, y: -1.4, z: -10.6, w: 5.6, h: 4.6, d: 3.2, p: P.snout },
            ],
            kids: [
              tusk(-1), tusk(1),
              { name: 'orelhaD', pivot: [-3.4, -4.2, -2], rot: [0, 0, 0.6], boxes: [{ x: -0.4, y: -3.4, z: -1.2, w: 0.8, h: 3.4, d: 2.4, p: P.ear }] },
              { name: 'orelhaE', pivot: [3.4, -4.2, -2], rot: [0, 0, -0.6], boxes: [{ x: -0.4, y: -3.4, z: -1.2, w: 0.8, h: 3.4, d: 2.4, p: P.ear }] },
              { name: 'queixo', pivot: [0, 3.2, -3.6], boxes: [{ x: -2.6, y: 0, z: -6.4, w: 5.2, h: 1.4, d: 6.4, p: P.snout }] },
            ],
          },
          { name: 'rabo', pivot: [0, -3, 8.5], rot: [-0.6, 0, 0], boxes: [{ x: -0.5, y: -0.5, z: 0, w: 1, h: 1, d: 4, p: P.twig }, ...(lo ? [] : [{ x: -1, y: -1, z: 3.6, w: 2, h: 2, d: 1.6, p: P.moss }])] },
          leg(-1, -1), leg(1, -1), leg(-1, 1), leg(1, 1),
        ],
      }],
    };
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size * 1.1; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const gait = (ph, amp) => {
      const L = [['DF', 0], ['ET', 0.15], ['EF', Math.PI], ['DT', Math.PI + 0.15]];
      for (const [n, o] of L) { r('perna' + n, 0.55 * Math.sin(ph + o) * amp, 0, 0); r('canela' + n, -0.6 * Math.max(0, Math.cos(ph + o)) * amp, 0, 0); }
    };
    const ears = () => { r('orelhaD', 0, 0, 0.25 * loop([[0, 0], [0.1, 1], [0.2, 0], [1, 0]], t / 3)); r('orelhaE', 0, 0, -0.25 * loop([[0, 0], [0.5, 0], [0.6, 1], [0.7, 0], [1, 0]], t / 4)); };
    const W = (n, x, y, z) => { obj.updateMatrixWorld(true); return new THREE.Vector3(x, y, z).applyMatrix4(P[n].obj.matrixWorld); };
    const dirt = (key, p, age, n = 24) => ctx.get(key, () => DBFX.grains({ n, seed: key.length * 7 + 3, palette: [[90, 66, 44], [60, 44, 30], [80, 120, 50]], size: [0.03, 0.07], drag: 2, grav: 7, life: 0.6, cone: R2 => [(R2() - 0.5) * 1.6, 0.6 + R2() * 1.6, 0.4 + R2() * 1.8] })).at(p, age, 0);

    if (mode === 'parado') {
      // fuça o chão, bufa (sopro de esporos), cabeça pesada que sobe e desce, orelhas mexendo
      const sn = loop([[0, 0], [0.3, 0], [0.4, 1], [0.6, 1], [0.7, 0], [1, 0]], t / 4.5);
      r('cabeca', 0.35 * sn + 0.04 * Math.sin(t * 6) * sn, 0.1 * Math.sin(t * 0.5), 0);
      r('corpo', 0.02 * Math.sin(t * 1.4), 0, 0); if (P.corpo) { const b = 1 + 0.02 * Math.sin(t * 1.4); P.corpo.obj.scale.set(b, b, 1); }
      ears(); r('rabo', 0, 0.3 * Math.sin(t * 5), 0);
      const pf = (t % 4.5) / 4.5;
      if (pf > 0.72 && pf < 0.85) ctx.get('bufo', () => DBFX.grains({ n: 20, seed: 5, palette: [[220, 230, 160], [180, 200, 120], [240, 240, 210]], size: [0.02, 0.04], drag: 3, grav: -0.3, life: 0.8, cone: R2 => [(R2() - 0.5) * 0.6, R2() * 0.2, -0.6 - R2() * 0.6] })).at(W('cabeca', 0, 0, -10.8), (pf - 0.72) * 4.5, 0);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 7;
      gait(ph, 1); obj.position.y += 0.03 * Math.abs(Math.sin(ph));
      r('cabeca', 0.1 + 0.06 * Math.sin(ph * 2), 0, 0); r('corpo', 0.02 * Math.sin(ph * 2), 0, 0.03 * Math.sin(ph));
      ears(); r('rabo', 0, 0.4 * Math.sin(ph), 0);
      return;
    }
    if (mode === 'investida') {
      const T = t % 4.4;
      const R = ctx.ref; const RZ = -3.6; R.place(0, RZ, 0); R.spin(Math.PI);
      // raspa o casco 2 vezes (terra voa pra trás), abaixa a cabeça, dispara, presas pegam por baixo e jogam por cima
      const scr = T < 1.1 ? Math.max(0, Math.sin(T * 11)) : 0;
      r('pernaDF', -0.6 * scr, 0, 0); r('canelaDF', -0.4 * scr, 0, 0);
      if (T < 1.1 && scr > 0.6) dirt('raspa', W('canelaDF', 0, 4, 0), (T * 11 / (2 * Math.PI)) % 1);
      const low = seq([[0.6, 0], [1.1, 1, 'out'], [1.85, 1], [2.0, -0.6, 'snap'], [2.4, 0.3], [3.4, 0]], T);
      r('cabeca', 0.45 * low, 0, 0); r('corpo', 0.08 * Math.max(0, low), 0, 0);
      const run = seq([[1.1, 0], [1.9, 1, 'in'], [2.5, 1.35, 'out']], T), back = seq([[3.2, 0], [4.3, 1, 'io']], T);
      obj.position.z += (RZ + 1.0) * run * (1 - back);
      if (T > 1.1 && T < 2.5) { gait(T * 16, 1.2); dirt('poeira', new THREE.Vector3(0, 0.05, (RZ + 1.0) * run + 0.6), (T * 3) % 1, 30); }
      if (T > 3.2) gait(T * 9, 0.6);
      // alvo: presa pega na altura da cintura, ele gira por cima das costas e cai atrás
      const fl = FX.clamp01((T - 1.95) / 0.75);
      if (T > 1.95) {
        const zz = RZ + 1.0 * fl, yy = 1.3 * Math.sin(Math.PI * fl);
        if (fl < 1) { R.place(0.1, RZ + 2.2 * fl, yy); R.lean(-Math.PI * 1.2 * fl); R.armsOut(0.8); }
        else { R.place(0.1, RZ + 2.2, 0); R.lean(-0.9 * (1 - FX.clamp01((T - 2.7) / 1.2))); }
        if (T < 2.1) { ctx.shake(0.8); ctx.flash(new THREE.Vector3(0, 0.9, RZ + 0.2), 1 - (T - 1.95) / 0.15); }
      }
      if (T > 1.95 && T < 2.4) ctx.claw(new THREE.Vector3(0, 0.9, RZ + 0.3), (T - 1.95) / 0.45);
      ears();
      return;
    }
    if (mode === 'raizes') {
      const T = t % 4.6;
      const R = ctx.ref; const RZ = -4.0; R.place(0, RZ, 0); R.spin(Math.PI);
      // empina nas patas de trás e desce com tudo; da batida sai uma fileira de raízes pontudas rompendo o chão até o alvo
      const rear = seq([[0.2, 0], [0.9, 1, 'out'], [1.15, 1], [1.35, -0.15, 'in'], [1.6, 0]], T);
      r('corpo', -0.55 * rear, 0, 0); obj.position.y += 0.35 * Math.max(0, rear);
      r('pernaDF', -0.9 * Math.max(0, rear), 0, 0); r('pernaEF', -0.7 * Math.max(0, rear), 0, 0); r('canelaDF', -0.8 * Math.max(0, rear), 0, 0); r('canelaEF', -0.9 * Math.max(0, rear), 0, 0);
      r('pernaDT', 0.5 * Math.max(0, rear), 0, 0); r('pernaET', 0.5 * Math.max(0, rear), 0, 0);
      r('cabeca', -0.3 * Math.max(0, rear), 0, 0);
      const hit = T - 1.35;
      if (hit > 0 && hit < 0.4) { ctx.roarRing(new THREE.Vector3(0, 0.02, -0.7), hit / 0.4); ctx.shake(0.9 * (1 - hit / 0.4)); }
      const N = 7;
      for (let i = 0; i < N; i++) {
        const t0 = 1.4 + i * 0.12, g = seq([[t0, 0], [t0 + 0.14, 1, 'out'], [3.3 + i * 0.05, 1], [3.8 + i * 0.05, 0, 'in']], T);
        const zf = -0.9 + (RZ + 0.9) * (i + 1) / N, side = (i % 2 ? 1 : -1) * 0.12;
        const base = new THREE.Vector3(side, 0, zf), tip = new THREE.Vector3(side * 2.5, 0.55 + 0.12 * i, zf - 0.15);
        if (i === N - 1) tip.set(0, 1.3, RZ + 0.05);
        const sp = ctx.get('raiz' + i, () => { const tb = JJFX.tube(0x6e5034, 0.13 + 0.02 * (i % 3), 0.012); return tb; });
        sp.at(base, tip, g, { arc: 0.08, wiggle: 0.03, t: i });
        if (g > 0 && T < t0 + 0.4) dirt('rompe' + i, base.clone().add(new THREE.Vector3(0, 0.03, 0)), T - t0, 14);
      }
      const fl = FX.clamp01((T - 2.25) / 0.8);
      if (T > 2.25) { R.place(0, RZ - 0.8 * fl, 1.1 * Math.sin(Math.PI * Math.min(1, fl))); R.lean(-0.9 * Math.min(1, fl * 2) * (1 - FX.clamp01((T - 3.6) / 0.8))); R.armsOut(0.6 * (1 - fl)); }
      if (T > 2.2 && T < 2.4) ctx.flash(new THREE.Vector3(0, 1.2, RZ), 1 - (T - 2.2) / 0.2);
      ears();
      return;
    }
    if (mode === 'morte') {
      // tomba de lado, as pernas esticam e as flores das costas caem; vira cinza com o musgo por último
      const { T, fall } = JJFX.exorcize(t, obj, e, ctx, { h: 1.4, refZ: -2.6, rotX: 0.0, back: 0.2, ashY: 0.4, ashZ: 0, n: 170 });
      r('corpo', 0, 0, 1.57 * fall); obj.position.y -= 0.05 * fall; obj.position.x += 0.0;
      for (const n of ['DF', 'EF', 'DT', 'ET']) { r('perna' + n, 0.3 * Math.sin(T * 25) * Math.max(0, 1 - T) * fall, 0, 0); }
      r('cabeca', 0.2 * fall, 0, 0);
      // de lado o corpo encosta no chão: as pernas de baixo não escoram (antes sobrava uma folga, teste R5)
      JJFX.floorLimbs(obj, P, ['corpo'], ['pernaDF', 'pernaEF', 'pernaDT', 'pernaET', 'cabeca'], fall);
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(7, 0), variant(26, 1), variant(48, 2)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'javali', nome: 'Javali de Raízes', bioma: 'Hanami', tier: 'Grau 2 · tier 7',
    variants: fixed.map(v => ({ nome: KINDS[v.kind][0], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado (' + KINDS[v.kind][0] + ')', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Trotar'], ['investida', 'Investida'], ['raizes', 'Raízes'], ['morte', 'Exorcizado']],
    cam: 4.6, lookY: 0.8, lookZ: { investida: -1.8, raizes: -2.0, morte: 0 }, camFar: { investida: 7.0, raizes: 7.4 }, camYaw: { investida: -1.35, raizes: -1.3 },
    info: {
      'Tamanho': '1,3 bloco de altura, 1,6 de comprimento',
      'Vida': '65 (madeira: machado faz dano dobrado)',
      'Jeito': 'Anda sozinho fuçando a terra da mata; onde pisa nasce grama e flor. Se você chega perto, raspa o casco duas vezes antes de vir: é o aviso.',
      'Investida': 'Dispara em linha reta 6 blocos: quem pega é jogado por cima dele (dano alto + cai atrás). Ele não vira no meio: sair do lado desvia, e se bater em árvore fica tonto 2 s.',
      'Raízes': 'Empina e pisa: uma fileira de raízes pontudas rompe o chão até você, uma depois da outra (a última te joga pra cima). Correr de lado escapa da linha.',
      'Fraqueza': 'Fogo e machado. Tonto depois de bater em árvore leva dano dobrado nas costas.',
      'Solta': 'Madeira Amaldiçoada, Musgo; raro: Presa de Raiz (adaga: acerto crítico prende o alvo com raiz 1 s).',
      'Variações': 'Carvalho, cerejeira (flores rosa) ou bétula morta; galhos e flores das costas em lugares diferentes; presa quebrada às vezes.',
    },
  });
})();
