// Afogado (Mar de Dagon). Tier 6. Maldição do medo de se afogar, com a cara de quem o mar não devolveu: pescador
// alto e inchado, pele azul-esverdeada marmorizada, olhos de leite, boca que não fecha mais e não para de escorrer
// água, alga e cabelo grudados na cara, rede de pesca enrolada na cintura arrastando atrás e cracas no ombro.
// Golpes: Abraço do Fundo (te abraça e a água sobe do chão em volta de vocês dois até cobrir a cabeça) e
// Vômito do Mar (incha a barriga e vomita um jorro de água do mar com alga e peixe morto que te derruba).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  // [nome, camisa, calça, pele]
  const KINDS = [
    ['Pescador', [70, 88, 110], [150, 130, 96], [150, 172, 162]],
    ['Marinheiro', [210, 212, 214], [52, 60, 88], [160, 170, 176]],
    ['Banhista', [180, 70, 60], [60, 110, 140], [176, 168, 172]],
  ];
  function variant(seed, kind) {
    const R = FX.rnd(seed);
    const k = kind ?? Math.floor(R() * KINDS.length);
    return { seed, kind: k, shirt: KINDS[k][1], pants: KINDS[k][2], skin: KINDS[k][3], size: 0.96 + R() * 0.08, net: k !== 2 || R() < 0.5, eyeHid: R() < 0.5 ? -1 : 1 };
  }

  const FACE = [
    '........',
    '.bb..bb.',
    '.WW..WW.',
    '.Wg..gW.',
    '.pp..pp.',
    '...nn...',
    '..LLLL..',
    '........',
  ];
  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.74, hi = 1.16) => {
      const f = 0.62 - 0.3 * q.ny + 0.3 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.22 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const P = {};
    // pele de afogado: marmorizada (rede de veias verde-arroxeadas), manchas roxas onde o sangue parou
    const skin = (q, s) => {
      let c = shade(v.skin, q, s);
      const vein = Math.abs(n3(q.ax * 0.45, q.ay * 0.45, q.az * 0.45, 40 + S) - 0.5);
      if (vein < 0.03) c = mix(c, [70, 90, 100], 0.6); else if (vein < 0.05) c = mix(c, [100, 110, 120], 0.3);
      if (n3(q.ax * 0.25, q.ay * 0.25, q.az * 0.25, 41 + S) > 0.7) c = mix(c, [120, 100, 140], 0.3);
      return c;
    };
    P.skin = q => skin(q, 1);
    P.hand = q => { let c = mix(skin(q, 2), [210, 214, 206], 0.25); if (fr(q.ay * 1.2) < 0.15) c = mul(c, 0.86); return c; };    // mão enrugada de tanto tempo na água
    P.face = q => {
      let c = skin(q, 3);
      if (q.f !== 'north') return c;
      // rosto em bitmap 8x8 (olhos de leite com pálpebra roxa inchada, nariz, lábio roxo)
      const col = Math.min(7, Math.floor(q.px * 8 / q.b.w)), row = Math.min(7, Math.floor(q.py * 8 / q.b.h));
      const ch = FACE[row][col];
      if (ch === 'W') return [246, 250, 248];
      if (ch === 'g') return [150, 172, 196];
      if (ch === 'b') return [70, 50, 92];
      if (ch === 'p') return [104, 78, 128];
      if (ch === 'n') return mul(c, 0.82);
      if (ch === 'L') return [80, 60, 100];
      return c;
    };
    P.mouthIn = q => (q.py > 1.2 ? [24, 44, 50] : [40, 30, 50]);                                           // boca cheia de água escura
    P.lip = q => { let c = shade([84, 70, 104], q, 4); if (q.f === 'top') c = [50, 36, 66]; if (q.f === 'top' && q.nz < 0.4 && fr(q.px * 0.8) < 0.3) c = [190, 186, 160]; return c; };
    P.shirt = q => {
      let c = shade(v.shirt, q, 5);
      // desbotada pelo sal: manchas claras, listras de lodo escorrido, rasgos mostrando pele
      if (n3(q.ax * 0.4, q.ay * 0.4, q.az * 0.4, 42 + S) > 0.64) c = mix(c, [220, 220, 210], 0.25);
      if (n3(q.ax * 0.9, q.ay * 0.15, q.az * 0.9, 43 + S) > 0.7) c = mix(c, [70, 90, 60], 0.4);
      if (n3(q.ax * 0.6, q.ay * 0.6, q.az * 0.6, 44 + S) > 0.78) return skin(q, 6);
      if (v.kind === 1 && fr(q.ay * 0.45) < 0.4 && q.f !== 'top') c = mix(c, [40, 50, 90], 0.75);                                   // listras de marinheiro
      return c;
    };
    P.belly = q => { const x = q.px / q.b.w - 0.5; const torn = Math.abs(x) < 0.32 + 0.12 * (n3(q.ay * 0.9, 0, 0, 45 + S) - 0.5); return (q.f === 'north' || q.f === 'bottom') && torn ? mix(skin(q, 7), [190, 190, 200], 0.12) : P.shirt(q); };            // camisa estourada na barriga
    P.pants = q => { let c = shade(v.pants, q, 8); if (q.ny > 0.82 && q.b.h > 6) c = mul(c, 0.82); if (n3(q.ax * 0.8, q.ay * 0.8, q.az * 0.8, 46 + S) > 0.74) c = mix(c, [60, 70, 50], 0.4); return c; };
    P.foot = q => { let c = skin(q, 9); if (q.f === 'bottom') c = [70, 80, 80]; return c; };
    // alga e cabelo molhados: faixas verdes e pretas, grudados
    P.hair = q => { const k = n3(q.ax * 0.7 + q.az * 0.7, q.ay * 0.08, 0, 47 + S); let c = shade(k > 0.6 ? [54, 82, 42] : k < 0.3 ? [22, 24, 24] : [36, 40, 36], q, 10, 0.72, 1.2); if (n3(q.ax * 1.3, q.ay * 1.3, q.az * 1.3, 50 + S) > 0.82) c = mix(c, [140, 170, 170], 0.35); return c; };
    P.kelp = q => { let c = shade([66, 96, 44], q, 11, 0.72, 1.16); if (Math.abs(q.nx - 0.5) < 0.1) c = mul(c, 0.8); if (n3(q.ax, q.ay, q.az, 48 + S) > 0.72) c = mix(c, [130, 120, 50], 0.4); return c; };
    // rede: só as cordas aparecem (o resto é vazio), nós mais grossos
    P.net = q => {
      const u = q.f === 'top' || q.f === 'bottom' ? q.px : (q.f === 'north' || q.f === 'south' ? q.px : q.pz), w = q.f === 'top' || q.f === 'bottom' ? q.pz : q.py;
      const a = fr((u + w) * 0.42), b = fr((u - w) * 0.42);
      if (a > 0.22 && b > 0.22) return null;
      let c = shade([150, 136, 104], q, 12, 0.8, 1.1);
      if (a < 0.22 && b < 0.22) c = mul(c, 0.8);
      if (n3(q.ax * 0.5, q.ay * 0.5, q.az * 0.5, 49 + S) > 0.66) c = mix(c, [70, 100, 50], 0.5);
      return c;
    };
    P.float = q => { let c = shade([226, 110, 40], q, 13); if (fr(q.ay * 0.8 + q.ax * 0.8) < 0.3) c = [236, 226, 214]; return c; };
    P.barn = q => { let c = shade([214, 206, 190], q, 14, 0.8, 1.1); if (q.f === 'top' && Math.hypot(q.nx - 0.5, q.nz - 0.5) < 0.24) c = [40, 34, 30]; return c; };
    return P;
  }

  function rig(P, v, lo) {
    const arm = s => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * 5.9, -11.8, 0], rot: [0.1, 0, -s * 0.08], boxes: [
        { x: -1.7, y: -1, z: -1.7, w: 3.4, h: 8.4, d: 3.4, p: P.shirt },
        ...(s > 0 && !lo ? [{ x: -1.2, y: -1.8, z: -1.4, w: 2, h: 1, d: 2, p: P.barn }, { x: 0.4, y: -1.6, z: 0.2, w: 1.4, h: 0.8, d: 1.4, p: P.barn }] : []),
      ],
      kids: [{
        name: s < 0 ? 'anteD' : 'anteE', pivot: [0, 7.4, 0], boxes: [{ x: -1.5, y: 0, z: -1.5, w: 3, h: 8.2, d: 3, p: P.skin }],
        kids: [{ name: s < 0 ? 'maoD' : 'maoE', pivot: [0, 8.2, 0], boxes: [{ x: -1.5, y: 0, z: -1.7, w: 3, h: 2.6, d: 3.4, p: P.hand }, ...(lo ? [] : [-1.1, 0, 1.1].map(z => ({ x: -0.4, y: 2.6, z: z - 0.4, w: 0.8, h: 2.8 + (z === 0 ? 0.6 : 0), d: 0.8, p: P.hand })))] }],
      }],
    });
    const leg = s => ({
      name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * 2.3, 10.6, 0], boxes: [{ x: -2, y: 0, z: -2, w: 4, h: 9.4, d: 4, p: P.pants }],
      kids: [{ name: s < 0 ? 'canelaD' : 'canelaE', pivot: [0, 9.4, 0], boxes: [{ x: -1.7, y: 0, z: -1.7, w: 3.4, h: 3, d: 3.4, p: P.skin }, { x: -1.9, y: 3, z: -3.2, w: 3.8, h: 1, d: 5, p: P.foot }] }],
    });
    // fios de cabelo e alga caídos na frente da cara (um lado cobre um olho)
    const strands = lo ? [] : [-3.4, -2.2, 2.4, 3.4, v.eyeHid * 1.6].map((x, i) => ({ name: 'fio' + i, pivot: [x, -7.8, -4.6], rot: [0.06, 0, 0], boxes: [{ x: -0.5, y: 0, z: -0.3, w: 1, h: i === 4 ? 6.4 : 5 + (i % 2) * 2.2, d: 0.6, p: i % 2 ? P.kelp : P.hair }] }));
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'quadril', pivot: [0, 10.6, 0], boxes: [{ x: -4.6, y: -1.4, z: -2.6, w: 9.2, h: 3, d: 5.2, p: P.pants }],
          kids: [
            {
              name: 'tronco', pivot: [0, 0, 0], boxes: [
                { x: -4.8, y: -13, z: -2.8, w: 9.6, h: 12, d: 5.6, p: P.shirt },
                // barriga inchada saltando da camisa
                { x: -4, y: -7.4, z: -4.0, w: 8, h: 6.2, d: 1.4, p: P.belly },
                { x: -2.8, y: -6.4, z: -4.8, w: 5.6, h: 4.2, d: 1.0, p: P.belly },
                // alga pendurada no ombro
                ...(lo ? [] : [{ x: -5.4, y: -13.4, z: -1.6, w: 3, h: 0.8, d: 3.6, p: P.kelp }, { x: -5.2, y: -12.8, z: -2.2, w: 1.2, h: 7, d: 0.6, p: P.kelp }]),
              ],
              kids: [
                arm(-1), arm(1),
                {
                  name: 'cabeca', pivot: [0, -13, -0.4], boxes: [
                    { x: -4.2, y: -8.6, z: -4.2, w: 8.4, h: 8.6, d: 8.4, p: P.face },
                    { x: -2.4, y: -2.4, z: -4.25, w: 4.8, h: 2.2, d: 0.2, p: P.mouthIn },
                  ],
                  kids: [
                    { name: 'mandibula', pivot: [0, -1.9, -1.2], rot: [0.25, 0, 0], boxes: [{ x: -2.7, y: 0, z: -3.2, w: 5.4, h: 1.6, d: 5.2, p: P.lip }] },
                    {
                      name: 'cabelo', pivot: [0, -8.6, 0], boxes: [
                        { x: -4.5, y: -0.8, z: -4.5, w: 9, h: 1.6, d: 9, p: P.hair },
                        { x: -4.6, y: 0, z: -4.4, w: 0.8, h: 9, d: 8.8, p: P.hair }, { x: 3.8, y: 0, z: -4.4, w: 0.8, h: 9, d: 8.8, p: P.hair },
                        { x: -4.4, y: 0, z: 3.8, w: 8.8, h: 11, d: 0.8, p: P.hair },
                      ],
                    },
                    ...strands,
                  ],
                },
              ],
            },
            // rede: cai da cintura até o chão atrás e arrasta deitada na areia, com boias
            ...(v.net ? [{
              name: 'rede', pivot: [0, 0.4, 2.8], rot: [0.12, 0, 0], boxes: [{ x: -4.6, y: 0, z: 0, w: 9.2, h: 12.6, d: 0.4, p: P.net }, ...(lo ? [] : [{ x: -5, y: 3, z: -0.4, w: 1.4, h: 1.4, d: 1.2, p: P.float }])],
              kids: [{ name: 'redeChao', pivot: [0, 12.2, 0], rot: [1.42, 0, 0], boxes: [{ x: -5, y: -0.2, z: 0, w: 10, h: 10, d: 0.4, p: P.net }, ...(lo ? [] : [{ x: 3.6, y: 6, z: -0.6, w: 1.6, h: 1.6, d: 1.4, p: P.float }])] }],
            }] : []),
          ],
        },
        leg(-1), leg(1),
      ],
    };
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const W = (n, x, y, z) => { obj.updateMatrixWorld(true); return new THREE.Vector3(x, y, z).applyMatrix4(P[n].obj.matrixWorld); };
    // água sempre pingando: da boca, das pontas dos dedos e da barra da camisa (cada fio no seu ritmo)
    const drip = (k = 1) => {
      const spots = [['maoD', 0, 5.6, 0], ['maoE', 0, 5.6, 0], ['mandibula', 0, 1.6, -3]];
      spots.forEach(([n, x, y, z], i) => {
        if (!P[n]) return;
        const age = ((t + i * 0.37) % 0.9);
        ctx.get('pingo' + i, () => JJFX.drops(80 + i, 6, { size: [0.025, 0.04], drag: 0.5, grav: 9, life: 0.7, spread: 0.5, cone: R2 => [(R2() - 0.5) * 0.1, -R2() * 0.2, (R2() - 0.5) * 0.1] })).at(W(n, x, y, z), age * k, 0.02);
      });
    };
    const hairSway = (k = 1) => { for (let i = 0; i < 5; i++) r('fio' + i, 0.12 * Math.sin(t * 1.4 + i) * k, 0, 0.08 * Math.sin(t * 1.1 + i * 2) * k); };
    const jaw = k => r('mandibula', 0.5 * k, 0, 0);

    if (mode === 'parado') {
      // em pé torto, cabeça caída de lado, balança como se ainda tivesse onda; a boca abre e fecha sem som
      r('cabeca', 0.02, 0.1 * Math.sin(t * 0.3), 0.32 + 0.08 * Math.sin(t * 0.9));
      r('tronco', 0.08 + 0.04 * Math.sin(t * 0.9), 0, 0.05 * Math.sin(t * 0.6));
      r('bracoD', 0.08 * Math.sin(t * 0.9), 0, 0.05 + 0.04 * Math.sin(t * 0.8)); r('bracoE', 0.08 * Math.sin(t * 0.9 + 0.6), 0, -0.05);
      r('anteD', -0.15, 0, 0); r('anteE', -0.1, 0, 0);
      r('quadril', 0, 0, 0.04 * Math.sin(t * 0.9)); obj.position.x += 0.03 * Math.sin(t * 0.9);
      jaw(0.4 + 0.3 * Math.max(0, Math.sin(t * 0.8)));
      hairSway(1); drip(1);
      return;
    }
    if (mode === 'andar') {
      // arrasta: perna direita dura e arrastada, a esquerda puxa; tronco cai pra frente a cada passo; rede arrasta
      const ph = t * 2.6, st = Math.sin(ph);
      r('pernaE', -0.55 * Math.max(0, st) + 0.15, 0, 0); r('canelaE', 0.5 * Math.max(0, st), 0, 0);
      r('pernaD', 0.25 * Math.max(0, -st) - 0.05, 0, 0.06);
      r('tronco', 0.25 + 0.1 * Math.abs(st), 0.1 * st, 0); r('quadril', 0, 0, 0.06 * st);
      r('cabeca', 0.05, 0, 0.3 + 0.12 * st);
      r('bracoD', -0.35 - 0.1 * st, 0, 0.1); r('bracoE', -0.45 + 0.1 * st, 0, -0.1); r('anteD', -0.2, 0, 0); r('anteE', -0.3, 0, 0);
      obj.position.y += 0.05 * Math.abs(Math.cos(ph)); obj.position.x += 0.04 * st;
      r('rede', 0.05 * Math.sin(ph * 2), 0, 0.04 * st);
      jaw(0.6); hairSway(1.6); drip(1);
      // pegadas molhadas: cada passo deixa uma, que seca devagar
      for (let k = 0; k < 3; k++) {
        const age = (t + k * 0.8) % 2.4, side = k % 2 ? 1 : -1;
        ctx.get('pegada' + k, () => JJFX.wet()).at(new THREE.Vector3(side * 0.15, 0.012, -0.2 + age * 0.0), 0.2, 0.9, 0.7 * (1 - age / 2.4), t, k + 1);
      }
      return;
    }
    if (mode === 'abraco') {
      const T = t % 6.0;
      const R = ctx.ref; const RZ = -1.0; R.place(0, RZ, 0); R.spin(Math.PI);
      // tropeça pra frente e abraça; a água sobe do chão em volta dos dois até passar da cabeça; bolhas sobem;
      // a coluna desaba, ele solta e o alvo cai tossindo
      const lunge = seq([[0.1, 0], [0.7, 1, 'in'], [0.85, 1]], T), rel = seq([[4.3, 0], [4.9, 1, 'io']], T);
      const hug = seq([[0.5, 0], [0.95, 1, 'snap'], [4.3, 1], [4.7, 0]], T);
      obj.position.z -= (0.4) * lunge * (1 - rel);
      r('tronco', 0.35 * lunge * (1 - rel), 0, 0); r('cabeca', 0.2 + 0.3 * hug, 0, 0.2);
      // braços: abrem e fecham em volta das costas do alvo
      const op = seq([[0.2, 0], [0.6, 1, 'out'], [0.95, 0.15, 'snap'], [4.3, 0.15], [4.8, 0]], T);
      const YU = 0.75; const XU = 1.2; r('bracoD', -XU * Math.max(hug, op * 0.9), -0.2 + YU * hug, 0.9 * op); r('bracoE', -XU * Math.max(hug, op * 0.9), 0.2 - YU * hug, -0.9 * op);
      const FY = -1.6; r('anteD', -0.2 * hug, 0, FY * hug); r('anteE', -0.2 * hug, 0, -FY * hug);
      jaw(0.5 + 0.4 * hug); hairSway(1);
      const rise = seq([[1.0, 0], [2.6, 1, 'io'], [4.1, 1], [4.4, 0.0, 'in']], T);
      const col = ctx.get('coluna', () => JJFX.column());
      col.at(new THREE.Vector3(0, 0, RZ * 0.55), 0.95, 2.5 * rise + 0.05, FX.clamp01(rise * 3), T);
      ctx.get('poca', () => JJFX.wet()).at(new THREE.Vector3(0, 0.014, RZ * 0.55), 1.35, 0.5 + 0.5 * FX.clamp01((T - 0.9) / 0.4), FX.clamp01((T - 0.9) / 0.3) * (1 - FX.clamp01((T - 5.4) / 0.5)), t, 6);
      // bolhas do ar que sai do alvo
      if (T > 2.0 && T < 4.2) ctx.get('bolhas', () => JJFX.drops(90, 40, { palette: [[230, 248, 255], [200, 236, 255], [255, 255, 255]], size: [0.03, 0.08], drag: 0.6, grav: -1.4, life: 1.6, spread: 1.4, cone: R2 => [(R2() - 0.5) * 0.4, 0.3 + R2() * 0.4, (R2() - 0.5) * 0.4] })).at(new THREE.Vector3(0, 1.55, RZ), T - 2.0, 0);
      if (T > 4.1 && T < 4.9) ctx.get('desaba', () => JJFX.drops(91, 110, { life: 0.9, cone: R2 => { const a2 = R2() * 6.28, sp = 1 + R2() * 2.4; return [Math.cos(a2) * sp, 0.4 + R2() * 1.6, Math.sin(a2) * sp]; } })).at(new THREE.Vector3(0, 0.9, RZ * 0.55), T - 4.1, 0.02);
      const under = FX.clamp01((rise * 2.5 - 1.5));
      ctx.drown(under); if (under > 0.5) ctx.blur(0.25 * under);
      R.armsOut(0.2 * hug); if (T > 1.2 && T < 4.3) { R.lean(-0.08 * Math.sin(T * 6)); R.armsOut(0.5 + 0.3 * Math.sin(T * 5)); }
      if (T > 4.6) { const k = T - 4.6; R.place(0, RZ - 0.4 * FX.clamp01(k * 2), 0); R.lean(0.45 * Math.min(1, k * 3) * (0.8 + 0.2 * Math.sin(k * 14))); R.face(0.6); }
      return;
    }
    if (mode === 'vomito') {
      const T = t % 4.8;
      const R = ctx.ref; const RZ = -2.8; R.place(0, RZ, 0); R.spin(Math.PI);
      // joga a cabeça pra trás, a barriga incha (a camisa estica), dobra pra frente e o mar sai da boca
      const back = seq([[0.2, 0], [1.1, 1, 'io'], [1.25, -0.8, 'snap'], [3.0, -0.8], [3.6, 0, 'io']], T);
      r('tronco', -0.35 * back, 0, 0); r('cabeca', -0.4 * back, 0, 0);
      const bl = seq([[0.2, 0], [1.1, 1, 'io'], [1.3, 0.6], [3.0, 0.2], [3.4, 0]], T);
      if (P.tronco) P.tronco.obj.scale.set(1 + 0.08 * bl, 1, 1 + 0.18 * bl);
      r('bracoD', 0.3 * Math.max(0, back) - 0.4 * Math.max(0, -back), 0, 0.3 * Math.max(0, -back)); r('bracoE', 0.3 * Math.max(0, back) - 0.4 * Math.max(0, -back), 0, -0.3 * Math.max(0, -back));
      r('pernaD', -0.3 * Math.max(0, -back), 0, 0); r('pernaE', 0.15 * Math.max(0, -back), 0, 0);
      const open = seq([[1.0, 0], [1.25, 1, 'snap'], [3.0, 1], [3.5, 0.4]], T);
      jaw(0.4 + 1.6 * open); hairSway(1.5);
      // jorro: grosso, curva pra baixo, bate no chão e corre até o alvo
      const pour = seq([[1.25, 0], [1.45, 1, 'out'], [2.9, 1], [3.2, 0, 'in']], T);
      const m = W('mandibula', 0, 0.6, -3.4);
      const hitP = new THREE.Vector3(0.04 * Math.sin(T * 6), 0.95, RZ + 0.15);
      const A = ctx.get('jorroA', () => { const tb = JJFX.tube(0x2d6f86, 0.14, 0.34); tb.material.transparent = true; tb.material.opacity = 0.8; return tb; });
      const B = ctx.get('jorroB', () => { const tb = JJFX.tube(0x9fd4d8, 0.08, 0.2); tb.material.transparent = true; tb.material.opacity = 0.85; tb.material.emissive = new THREE.Color(0x12343c); return tb; });
      if (pour > 0) { A.at(m, hitP, pour, { arc: -0.15, wiggle: 0.07, t: T, pulse: fr(T * 1.8) }); B.at(m, hitP, Math.min(1, pour * 1.05), { arc: -0.15, wiggle: 0.09, t: T + 0.3, pulse: fr(T * 1.8 + 0.5) }); }
      if (T > 1.4 && T < 3.6) {
        // pedaços no jorro: alga, peixinho morto, lodo
        ctx.get('lixo', () => DBFX.grains({ n: 46, seed: 92, palette: [[66, 96, 44], [40, 60, 30], [200, 206, 210], [150, 160, 170], [110, 90, 60]], size: [0.04, 0.09], drag: 1.2, grav: 8, life: 0.9, spread: 1.4, cone: R2 => [(R2() - 0.5) * 1.8, R2() * 1.6, (R2() - 0.2) * 1.8] })).at(hitP, T - 1.4, 0.03);
        ctx.get('respingo', () => JJFX.drops(93, 90, { life: 0.8, spread: 1.4, cone: R2 => [(R2() - 0.5) * 3, R2() * 2.2, (R2() - 0.3) * -2.4] })).at(hitP, T - 1.4, 0.02);
      }
      ctx.get('poca', () => JJFX.wet([0.36, 0.6, 0.62], [0.12, 0.24, 0.24])).at(new THREE.Vector3(0, 0.014, RZ * 0.7), 1.5, FX.clamp01((T - 1.4) / 1.2), FX.clamp01((T - 1.4) / 0.3) * (1 - FX.clamp01((T - 4.2) / 0.6)), t, 9);
      // alvo: leva o jorro no peito, escorrega pra trás e cai sentado
      const push = seq([[1.45, 0], [2.8, 1, 'out']], T), fall = seq([[2.4, 0], [2.8, 1, 'in'], [4.2, 1], [4.7, 0]], T);
      if (T > 1.45) { R.place(0, RZ - 1.1 * push, -0.4 * fall); R.lean(-0.35 * pour - 0.4 * fall); R.face(0.7 * pour, true); ctx.drown(0.6 * pour); ctx.shake(0.3 * pour); }
      return;
    }
    if (mode === 'morte') {
      // cai de cara no chão, a água sai de dentro dele (boca e poça crescendo) e ele cai de cara na poça; depois cinza
      const { T, fall } = JJFX.exorcize(t, obj, e, ctx, { h: 2.4, refZ: -3.2, rotX: -1.5, back: -0.2, drop: -1.18, ashY: 0.25, ashZ: -1.0, n: 180 });
      r('pernaD', 0.15 * fall, 0, 0.05 * fall); r('pernaE', -0.1 * fall, 0, -0.08 * fall); r('canelaE', 0.3 * fall, 0, 0);
      r('bracoD', -3.05 * fall, 0, 0.3 * fall); r('bracoE', -2.95 * fall, 0, -0.35 * fall); r('redeChao', -1.42 * fall, 0, 0); r('rede', -0.42 * fall, 0, 0);
      jaw(1.2 * fall); hairSway(1 - fall);
      ctx.get('poca', () => JJFX.wet()).at(new THREE.Vector3(0, 0.014, -0.9), 1.4, FX.clamp01((T - 0.3) / 1.4), 1 - FX.clamp01((T - 3.0) / 0.4), t, 3);
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(14, 0), variant(27, 1), variant(53, 2)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'afogado', nome: 'Afogado', bioma: 'Mar de Dagon', tier: 'Grau 2 · tier 6',
    variants: fixed.map(v => ({ nome: KINDS[v.kind][0], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado (' + KINDS[v.kind][0] + ')', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Arrastar'], ['abraco', 'Abraço do Fundo'], ['vomito', 'Vômito do Mar'], ['morte', 'Exorcizado']],
    cam: 5.0, lookY: 1.4, lookZ: { abraco: -0.6, vomito: -1.6, morte: -0.6 }, camFar: { abraco: 5.4, vomito: 6.6 }, camYaw: { abraco: -1.3, vomito: -1.3 },
    info: {
      'Tamanho': '2,2 blocos (mais alto que o jogador), largo e inchado',
      'Vida': '70 (pesado: quase não toma empurrão)',
      'Jeito': 'Sai da água rasa de noite e arrasta a perna pela areia atrás de você, pingando e deixando pegada molhada. Lento, mas não para nunca.',
      'Abraço do Fundo': 'Te abraça e a água sobe do chão em volta: em 1,5 s cobre a sua cabeça (afogando: perde ar e vida rápido, tela azul). Bater nele 4 vezes ou usar técnica solta. Com Respiração na Água você só leva o aperto.',
      'Vômito do Mar': 'Joga a cabeça pra trás (a barriga incha), dobra e vomita um jorro de água com alga e peixe morto: empurra, derruba sentado e deixa uma poça escorregadia por 6 s.',
      'Fraqueza': 'Fogo seca ele (dano dobrado e fica 3 s sem usar água). Longe da praia anda ainda mais devagar.',
      'Solta': 'Rede Rasgada, Alga Amaldiçoada; raro: Âncora Afogada (arma pesada: golpe carregado puxa os inimigos em volta pra você).',
      'Variações': 'Pescador, marinheiro (camisa listrada) ou banhista; com ou sem rede; o cabelo cobre o olho esquerdo ou o direito.',
    },
  });
})();
