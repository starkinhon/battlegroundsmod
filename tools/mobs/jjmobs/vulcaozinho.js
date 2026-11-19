// Vulcãozinho (Terras do Jogo). Tier 7. Maldição nascida do medo de vulcão, ainda filhote: um cone de rocha em
// degraus com um olho só no meio, perninhas de pedra e bracinhos curtos. A cratera em cima borbulha magma de verdade
// e solta fumaça o tempo todo; a lava escorre pelos lados e esfria em listras escuras. Tem uma boca rachada embaixo.
// Golpes: Erupção (agacha, prende o ar e a cratera cospe bombas de lava em arco: onde caem fica magma) e
// Bafo de Cinza (abre a boca e solta uma nuvem quente de cinza que te cega e queima).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  // [nome, rocha, rocha clara, lava quente, lava fria]
  const KINDS = [
    ['Basalto', [62, 54, 52], [96, 86, 80], [255, 150, 40], [150, 40, 18]],
    ['Cinza Vulcânica', [112, 106, 100], [158, 150, 142], [255, 128, 36], [130, 46, 26]],
    ['Obsidiana', [30, 24, 36], [70, 56, 88], [255, 110, 60], [120, 26, 40]],
  ];
  function variant(seed, kind) {
    const R = FX.rnd(seed);
    const k = kind ?? Math.floor(R() * KINDS.length);
    const drips = []; for (let i = 0; i < 7; i++) drips.push({ a: R() * Math.PI * 2, len: 0.35 + R() * 0.6, w: 0.05 + R() * 0.05 });
    return { seed, kind: k, rock: KINDS[k][1], rock2: KINDS[k][2], hot: KINDS[k][3], cold: KINDS[k][4], drips, size: 0.94 + R() * 0.12, rocks: R() < 0.7, tilt: (R() - 0.5) * 0.12 };
  }

  const TOP = -17.5;   // topo da cratera (y do modelo relativo ao corpo)
  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.7, hi = 1.18) => {
      const f = 0.6 - 0.32 * q.ny + 0.3 * (n3(q.ax * 0.35, q.ay * 0.35, q.az * 0.35, s + S) - 0.5) + (q.f === 'top' ? 0.24 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const P = {};
    // rocha do cone: camadas (estratos) + manchas claras + escorridos de lava que descem da cratera e esfriam
    const rock = (q, s) => {
      let c = shade(v.rock, q, s);
      if (n3(q.ax * 0.4, q.ay * 1.2, q.az * 0.4, s + 1 + S) > 0.66) c = shade(v.rock2, q, s + 2);
      if (fr(q.ay * 0.42 + 0.3 * n3(q.ax * 0.2, 0, q.az * 0.2, s + 3 + S)) < 0.08) c = mul(c, 0.78);
      if (q.f !== 'top' && q.f !== 'bottom') {
        const ang = Math.atan2(q.az, q.ax);
        const depth = (q.ay - (TOP + 18)) / 18;       // 0 no topo do cone, 1 embaixo
        for (const d of v.drips) {
          let da = Math.abs(((ang - d.a + Math.PI * 3) % (Math.PI * 2)) - Math.PI);
          const wob = 0.03 * Math.sin(q.ay * 0.9 + d.a * 5);
          if (da + wob < d.w * (1.2 - depth * 0.6) && depth + 1 < d.len * 1.6 + 0.2 + 0.4) {
            const k = FX.clamp01((depth + 1) / (d.len * 1.6 + 0.6));
            return k < 0.45 ? mix(v.hot, [255, 230, 170], 0.25 * (1 - k * 2)) : mix(v.hot, v.cold, Math.min(1, (k - 0.45) * 2.2));
          }
        }
      }
      if (q.f === 'top') c = mix(c, [60, 56, 54], 0.25);     // cinza assentada nos degraus
      return c;
    };
    P.cone = q => rock(q, 1);
    P.rim = q => { let c = rock(q, 5); if (q.f === 'top') c = mix(c, v.cold, 0.3 + 0.4 * n3(q.ax, 0, q.az, 6 + S)); return c; };
    // magma da cratera: placas pretas boiando com fresta brilhando
    P.lava = q => {
      const a = n3(q.ax * 0.9, 0, q.az * 0.9, 7 + S), b = n3(q.ax * 2.2, 0, q.az * 2.2, 8 + S);
      if (Math.abs(a - 0.5) < 0.05) return [255, 240, 190];
      if (a > 0.6 && b > 0.45) return mul(v.cold, 0.5);
      return mix(v.hot, [255, 220, 120], b * 0.6);
    };
    // olho: esclera amarelada quente, íris laranja, pupila em fenda; pálpebra de pedra
    // olho em bitmap 10x6 (pixel a pixel lê melhor que elipse): amêndoa com íris de fogo e pupila em fenda
    const EYE = [
      '..KKKKKK..',
      '.KWOYYOWK.',
      'KWOYhPYOWK',
      'KWOYYPYOWK',
      '.KWOYYOWK.',
      '..KKKKKK..',
    ];
    P.eye = q => {
      if (q.f !== 'north') return shade(v.rock, q, 9);
      const col = Math.min(9, Math.floor(q.px * 10 / q.b.w)), row = Math.min(5, Math.floor(q.py * 6 / q.b.h));
      const ch = EYE[row][col];
      if (ch === 'K') return [34, 16, 12];
      if (ch === 'W') return [246, 226, 186];
      if (ch === 'O') return [226, 84, 24];
      if (ch === 'Y') return [255, 196, 56];
      if (ch === 'P') return [16, 6, 4];
      if (ch === 'h') return [255, 252, 230];
      return shade(v.rock, q, 9);
    };
    P.lid = q => { let c = shade(v.rock2, q, 10); if (q.ny > 0.8) c = [36, 26, 22]; return c; };
    P.mouth = q => {
      if (q.f !== 'north') return rock(q, 11);
      const x = q.px - q.b.w / 2, y = q.py;
      const edge = 0.6 + 0.4 * Math.sin(x * 2.2 + 1) + 0.3 * Math.sin(x * 5.1);
      if (y > edge && y < q.b.h - 0.3) return mix(v.hot, [255, 236, 160], 0.4 * (1 - Math.abs(x) / (q.b.w / 2)));
      return rock(q, 11);
    };
    P.jaw = q => { let c = rock(q, 12); if (q.f === 'north' && q.py < 0.5) c = mix(c, v.hot, 0.5); return c; };
    P.limb = q => { let c = shade(mul(v.rock, 0.92), q, 13); if (fr(q.ay * 0.55) < 0.1) c = mul(c, 0.75); if (n3(q.ax, q.ay, q.az, 14 + S) > 0.75) c = mix(c, v.cold, 0.5); return c; };
    P.pebble = q => shade(v.rock2, q, 15);
    return P;
  }

  function rig(P, v, lo) {
    const tier = (w, y0, h, p) => ({ x: -w / 2, y: y0 - h, z: -w / 2, w, h, d: w, p });
    const rimWalls = [
      { x: -4.2, y: TOP, z: -4.2, w: 8.4, h: 3, d: 1.6, p: P.rim }, { x: -4.2, y: TOP, z: 2.6, w: 8.4, h: 3, d: 1.6, p: P.rim },
      { x: -4.2, y: TOP, z: -2.6, w: 1.6, h: 3, d: 5.2, p: P.rim }, { x: 2.6, y: TOP + 0.6, z: -2.6, w: 1.6, h: 2.4, d: 5.2, p: P.rim },
    ];
    const pebbles = lo || !v.rocks ? [] : [[-7.4, -1.2, -5.8, 2], [6.8, -6.2, 4.4, 1.6], [-5.2, -11, 3.4, 1.4], [3.0, -10.8, -4.9, 1.3]].map(([x, y, z, s]) => ({ x: x - s / 2, y: y - s, z: z - s / 2, w: s, h: s, d: s, p: P.pebble }));
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'corpo', pivot: [0, 18, 0], rot: [0, 0, v.tilt], boxes: [
            tier(17, 0, 5.2, P.cone), tier(13.6, -5.2, 5.2, P.cone), tier(10.4, -10.4, 4.4, P.cone),
            ...rimWalls,
            { x: -2.6, y: TOP + 1.4, z: -2.6, w: 5.2, h: 1, d: 5.2, p: P.lava },
            ...pebbles,
          ],
          kids: [
            {
              name: 'olho', pivot: [0, -7.6, -6.8], boxes: [{ x: -5, y: -3.0, z: -0.9, w: 10, h: 6, d: 1.3, p: P.eye }],
              kids: [{ name: 'palpebra', pivot: [0, -3.2, -0.2], boxes: [{ x: -5.2, y: 0, z: -1.3, w: 10.4, h: 1.2, d: 1.5, p: P.lid }] }],
            },
            {
              name: 'boca', pivot: [0, -3.4, -8.5], boxes: [{ x: -4.2, y: 0, z: -0.3, w: 8.4, h: 2.4, d: 0.6, p: P.mouth }],
              kids: [{ name: 'queixo', pivot: [0, 2.4, 0.3], boxes: [{ x: -4.4, y: 0, z: -1.1, w: 8.8, h: 1.6, d: 1.4, p: P.jaw }] }],
            },
            ...[-1, 1].map(s => ({
              name: 'braco' + (s < 0 ? 'D' : 'E'), pivot: [s * 8.4, -3.6, 0], rot: [0, 0, -s * 0.35],
              boxes: [{ x: -1.6, y: -0.6, z: -1.6, w: 3.2, h: 4.6, d: 3.2, p: P.limb }],
              kids: [{ name: 'mao' + (s < 0 ? 'D' : 'E'), pivot: [0, 3.8, 0], boxes: [{ x: -1.9, y: 0, z: -1.9, w: 3.8, h: 2.6, d: 3.8, p: P.limb }] }],
            })),
          ],
        },
        ...[-1, 1].map(s => ({ name: 'perna' + (s < 0 ? 'D' : 'E'), pivot: [s * 4.2, 18, 0], boxes: [{ x: -2.6, y: 0, z: -2.6, w: 5.2, h: 6, d: 5.2, p: P.limb }, ...(lo ? [] : [{ x: -2.8, y: 4.6, z: -3.6, w: 5.6, h: 1.4, d: 6.2, p: P.limb }])] })),
      ],
    };
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const C = e.v.hot.map(c => c / 255);
    const W = (n, x, y, z) => { obj.updateMatrixWorld(true); return new THREE.Vector3(x, y, z).applyMatrix4(P[n].obj.matrixWorld); };
    const crater = () => W('corpo', 0, TOP + 1, 0);
    const craterGlow = (k = 1) => { const p = crater(); ctx.get('cratera', () => DBFX.glow(C)).at(p, 0.22, k, t, 0.5); ctx.get('luz', () => DBFX.light(new THREE.Color(...C))).at(p.clone().add(new THREE.Vector3(0, 0.4, 0)), 1.2 * k, 3.5); };
    const blink = ph => { const b = seq([[0, 0], [0.04, 1, 'snap'], [0.1, 0]], fr(ph)); P.palpebra.obj.scale.y = 1 + 2.6 * b; };
    // fumaça contínua: duas nuvens defasadas saindo da cratera e subindo
    const smoke = (k = 1, dk = 0.55) => {
      for (let i = 0; i < 3; i++) {
        const ag = fr(t / 2.7 + i / 3);
        const p = crater().add(new THREE.Vector3(0.08 * Math.sin(i * 3 + t * 0.4) + 0.25 * ag * ag, 0.02 + ag * 0.75, 0.05));
        ctx.get('fumo' + i, FX.volume).set(p, (0.12 + ag * 0.3) * k, Math.min(0.999, 0.05 + ag * 0.9), { seed: 3.1 + i * 1.7 + Math.floor(t / 2.7 + i / 3) * 0.37, dens: 5, rise: 0.5, squash: 1.3, smoke: [dk, dk * 0.96, dk * 0.94] });
      }
    };
    const lavaGrains = (key, at, age, n = 16) => ctx.get(key, () => DBFX.grains({ n, seed: key.length * 11 + 5, add: true, palette: [e.v.hot, mix(e.v.hot, [255, 250, 220], 0.5), e.v.cold], size: [0.025, 0.05], drag: 0.8, grav: 8, life: 0.9, spread: 0.8, cone: R2 => [(R2() - 0.5) * 1.2, 1.5 + R2() * 2, (R2() - 0.5) * 1.2] })).at(at, age, 0.02);

    if (mode === 'parado') {
      // fica quieto respirando: o cone sobe e desce (inspira, solta fumaça), o olho pisca e olha de lado,
      // de vez em quando arrota uma bolha de lava
      const br = loop([[0, 0], [0.45, 1, 'io'], [1, 0, 'io']], t / 3.2);
      P.corpo.obj.scale.set(1 + 0.02 * br, 1 + 0.035 * br, 1 + 0.02 * br);
      r('olho', 0, 0.25 * Math.sin(t * 0.6), 0); blink(t / 3.7);
      r('bracoD', 0.1 * Math.sin(t * 0.9), 0, 0.05 * br); r('bracoE', 0.1 * Math.sin(t * 0.9 + 1), 0, -0.05 * br);
      const burp = seq([[0, 0], [0.1, 1, 'out'], [0.3, 0]], fr(t / 4.1) * 1.4);
      r('queixo', 0.35 * burp, 0, 0);
      craterGlow(0.8 + 0.2 * br + 0.5 * burp); smoke(1);
      if (burp > 0.01) lavaGrains('arroto', crater(), fr(t / 4.1) * 4.1 * 0.34, 10);
      return;
    }
    if (mode === 'andar') {
      // gingado de pé curto: o cone inteiro tomba de um lado pro outro a cada passo
      const ph = t * 4;
      r('pernaD', 0.5 * Math.sin(ph), 0, 0); r('pernaE', -0.5 * Math.sin(ph), 0, 0);
      r('corpo', 0.05, 0, 0.1 * Math.sin(ph));
      obj.position.y += 0.05 * Math.abs(Math.sin(ph));
      r('bracoD', -0.4 * Math.sin(ph), 0, 0); r('bracoE', 0.4 * Math.sin(ph), 0, 0);
      blink(t / 3.1); craterGlow(1); smoke(0.9);
      if (Math.abs(Math.cos(ph)) > 0.98) ctx.shake(0.08);
      return;
    }
    if (mode === 'erupcao') {
      const T = t % 5.4;
      const R = ctx.ref; const RZ = -3.4; R.place(0.3, RZ, 0); R.spin(Math.PI);
      // agacha e incha prendendo a pressão (olho aperta, cratera fica branca), estoura pra cima: coluna de fogo e
      // cinco bombas de lava voando em arco; cada uma cai, espirra e deixa uma poça de magma esfriando
      const squat = seq([[0.2, 0], [1.1, 1, 'in'], [1.25, -0.4, 'snap'], [1.7, 0, 'io']], T);
      P.corpo.obj.scale.set(1 + 0.08 * Math.max(0, squat), 1 - 0.12 * squat, 1 + 0.08 * Math.max(0, squat));
      obj.position.y -= 0.08 * Math.max(0, squat);
      P.palpebra.obj.scale.y = 1 + 1.6 * Math.max(0, squat);
      r('bracoD', 0, 0, -0.6 * Math.max(0, squat)); r('bracoE', 0, 0, 0.6 * Math.max(0, squat));
      if (T > 0.2 && T < 1.2) obj.position.x += 0.015 * Math.sin(t * 60) * squat;
      const blast = T - 1.2;
      craterGlow(1 + 2 * Math.max(0, squat) + (blast > 0 && blast < 0.6 ? 2 * (1 - blast / 0.6) : 0));
      if (T < 1.2) ctx.get('branco', () => DBFX.glow([1, 0.95, 0.8])).at(crater(), 0.14, Math.max(0, squat), t, 0.5);
      if (blast > 0) {
        ctx.get('coluna', FX.volume).set(crater().add(new THREE.Vector3(0, 0.75, 0)), 0.7, Math.min(0.999, blast / 1.8), { fire: 1.5, seed: 6.6, dens: 8, rise: 1.6, squash: 1.9, smoke: [0.24, 0.22, 0.22] });
        if (blast < 0.3) { ctx.flash(crater().add(new THREE.Vector3(0, 0.6, 0)), 1.4 * (1 - blast / 0.3)); ctx.shake(0.7 * (1 - blast / 0.3)); }
        lavaGrains('jato', crater(), blast, 40);
      }
      // bombas: alvo em volta de você, uma em você
      const tg = [[0.3, RZ + 0.05], [-1.1, RZ + 0.6], [1.4, RZ - 0.5], [-0.4, RZ - 1.2], [1.0, RZ + 1.0]];
      tg.forEach(([x, z], i) => {
        const t0 = 1.2 + i * 0.07, dur = 0.85 + i * 0.07, k = (T - t0) / dur;
        const from = crater(), to = new THREE.Vector3(x, 0.12, z);
        const bomb = ctx.get('bomba' + i, () => {
          const g = new THREE.Group();
          const rk = new THREE.Mesh(new THREE.BoxGeometry(0.22, 0.2, 0.24), new THREE.MeshLambertMaterial({ color: new THREE.Color(...e.v.cold.map(c => c / 255 * 0.6)) }));
          const hot = new THREE.Mesh(new THREE.BoxGeometry(0.17, 0.24, 0.17), new THREE.MeshBasicMaterial({ color: new THREE.Color(...C) }));
          g.add(rk, hot); g.rk = rk; return g;
        });
        if (k > 0 && k < 1) {
          const p = from.clone().lerp(to, k); p.y += 3.0 * Math.sin(Math.PI * k) + (from.y - to.y) * 0;
          bomb.visible = true; bomb.position.copy(p); bomb.rotation.set(t * 7 + i, t * 5, 0);
          ctx.get('rastro' + i, () => DBFX.glow(C)).at(p, 0.11, 0.7, t);
          ctx.get('fumoB' + i, FX.volume).set(p.clone().add(new THREE.Vector3(0, 0.05, 0)), 0.14, 0.15 + 0.1 * k, { fire: 0.9, seed: 10 + i, dens: 8, rise: 0.4, smoke: [0.25, 0.23, 0.23] });
        }
        if (k >= 1) {
          const ag = (T - t0 - dur);
          ctx.get('poca' + i, JJFX.magma).at(new THREE.Vector3(x, 0.012 + i * 0.002, z), 0.5 + 0.08 * i, FX.EASE.out(FX.clamp01(ag * 5)), 1 - FX.clamp01((T - 5.0) / 0.35), t, FX.clamp01(1 - ag / 2.6) * 0.9 + 0.1, 11 + i);
          if (ag < 0.6) { ctx.get('splash' + i, () => DBFX.grains({ n: 22, seed: 40 + i, add: true, palette: [e.v.hot, mix(e.v.hot, [255, 250, 220], 0.5)], size: [0.02, 0.05], drag: 1, grav: 9, life: 0.6, cone: R2 => { const a2 = R2() * 6.28; return [Math.cos(a2) * (1 + R2() * 1.5), 1 + R2() * 2.2, Math.sin(a2) * (1 + R2() * 1.5)]; } })).at(to, ag, 0.02); }
          if (ag < 0.12) ctx.shake(0.3);
        }
      });
      const hit = seq([[2.05, 0], [2.12, 1, 'snap'], [3.2, 0.5], [3.8, 0]], T);
      R.lean(-0.3 * hit); R.armsOut(0.25 * hit);
      if (T > 2.1 && T < 4.6) { const at = new THREE.Vector3(0.3, 1.0, RZ + 0.18); ctx.get('fogoRef', FX.volume).set(at, 0.3, FX.clamp01(0.08 + 0.16 * fr(t * 2.2)), { fire: 1.3, seed: 12.1 + Math.floor(t * 2.2), dens: 9, rise: 0.9, squash: 1.4, smoke: [0.25, 0.22, 0.22] }); ctx.ember(at.clone().add(new THREE.Vector3(0, 0.05, 0.06)), t); }
      smoke(1.3, 0.35);
      return;
    }
    if (mode === 'cinza') {
      const T = t % 5.0;
      const R = ctx.ref; const RZ = -2.6; R.place(0, RZ, 0); R.spin(Math.PI);
      // inclina pra frente, enche (o cone estufa), escancara a boca e sopra: nuvem quente de cinza que rola
      // pelo chão até você, cobre tudo (tela fecha) e deixa cinza caindo
      const lean = seq([[0.2, 0], [0.9, -0.2, 'io'], [1.2, 0.3, 'snap'], [3.6, 0.3], [4.3, 0, 'io']], T);
      r('corpo', lean, 0, 0);
      const fill = seq([[0.2, 0], [1.1, 1, 'in'], [1.25, 0, 'snap']], T);
      P.corpo.obj.scale.set(1 + 0.06 * fill, 1 + 0.05 * fill, 1 + 0.06 * fill);
      const open = seq([[1.1, 0], [1.25, 1, 'snap'], [3.5, 1], [4.0, 0]], T);
      r('queixo', 0.9 * open, 0, 0); r('boca', 0.2 * open, 0, 0);
      P.palpebra.obj.scale.y = 1 + 1.2 * open;
      r('bracoD', -0.4 * open, 0, -0.3 * open); r('bracoE', -0.4 * open, 0, 0.3 * open);
      craterGlow(0.8 + 0.6 * open);
      const mouth = W('boca', 0, 2.5, -1.5);
      if (open > 0.05) ctx.get('bocaLuz', () => DBFX.glow(C)).at(mouth, 0.1, open, t, 0.5);
      // três nuvens que nascem na boca e rolam pra frente crescendo; a última chega em você
      for (let i = 0; i < 3; i++) {
        const t0 = 1.25 + i * 0.35, ag = (T - t0) / 2.6;
        if (ag <= 0 || ag >= 1) continue;
        const go = FX.EASE.out(FX.clamp01(ag * 1.6));
        const p = mouth.clone().lerp(new THREE.Vector3(0.1 * (i - 1), 0.75, RZ + 0.2 - i * 0.3), go);
        ctx.get('nuvem' + i, FX.volume).set(p, 0.45 + 0.5 * go, ag, { fire: i === 0 ? 0.5 : 0.2, seed: 21 + i * 2.3, dens: 7, rise: 0.25, squash: 0.85, smoke: [0.36, 0.33, 0.32] });
      }
      const cover = seq([[1.6, 0], [2.0, 1, 'in'], [3.6, 1], [4.4, 0]], T);
      ctx.blind(0.85 * cover);
      R.lean(0.2 * cover); R.armsOut(-0.3 * cover); R.punch(0.4 * cover);
      if (T > 1.6) ctx.get('cinzaCai', () => DBFX.grains({ n: 70, seed: 91, palette: [[90, 86, 84], [130, 124, 120], [60, 56, 54], e.v.hot], size: [0.015, 0.035], drag: 3, grav: 0.9, life: 2.6, spread: 1.4, cone: R2 => [(R2() - 0.5) * 2.6, 0.4 + R2() * 0.8, (R2() - 0.5) * 2.6] })).at(new THREE.Vector3(0, 1.4, RZ + 0.6), T - 1.6, 0.02);
      return;
    }
    if (mode === 'morte') {
      // a cratera apaga e desmorona pra dentro, o olho fecha, sai a última fumaça preta e ele vira cinza
      const { T, fall } = JJFX.exorcize(t, obj, e, ctx, { h: 1.5, refZ: -2.2, rotX: 0.2, back: 0.25, drop: -0.25, ashY: 0.4, ashZ: 0.4, n: 160 });
      const off = Math.max(0, 1 - T * 0.9);
      craterGlow(off);
      P.palpebra.obj.scale.y = 1 + 2.6 * fall;
      r('corpo', 0, 0, 0.25 * fall); r('bracoD', 0, 0, 0.6 * fall); r('bracoE', 0, 0, -0.6 * fall); r('queixo', 0.4 * fall, 0, 0);
      // nenhum membro escora o morto: o corpo encosta no chão (teste R5)
      // as pernas tocos cedem pros lados e o cone senta no chão (antes ficava em pé nelas)
      r('pernaD', 0, 0, 1.5 * fall); r('pernaE', 0, 0, -1.5 * fall);
      P.pernaD.obj.position.y -= 2.6 * fall; P.pernaE.obj.position.y -= 2.6 * fall;
      JJFX.floorLimbs(obj, P, ['corpo'], ['bracoD', 'bracoE'], fall);
      P.corpo.obj.scale.y = 1 - 0.18 * fall;
      ctx.get('ultimo', FX.volume).set(crater().add(new THREE.Vector3(0, 0.5, 0)), 0.5, FX.clamp01((T - 0.3) / 2.4), { seed: 31, dens: 6, rise: 1.0, squash: 1.4, smoke: [0.16, 0.15, 0.15] });
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(8, 0), variant(29, 1), variant(53, 2)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'vulcaozinho', nome: 'Vulcãozinho', bioma: 'Terras do Jogo', tier: 'Grau 2 · tier 7',
    variants: fixed.map(v => ({ nome: KINDS[v.kind][0], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado (' + KINDS[v.kind][0] + ')', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['erupcao', 'Erupção'], ['cinza', 'Bafo de Cinza'], ['morte', 'Exorcizado']],
    cam: 4.6, lookY: 0.9, lookZ: { erupcao: -1.6, cinza: -1.2, morte: -0.6 }, camFar: { erupcao: 8.4, cinza: 6.6, morte: 5.4 }, camYaw: { erupcao: -1.0, cinza: -1.15, morte: -1.0 },
    info: {
      'Tamanho': '1,5 bloco de altura, 1 bloco de largura',
      'Vida': '60 (rocha: armas de corte fazem metade; picareta faz dobrado)',
      'Jeito': 'Anda em grupinhos de 2 ou 3 perto de rios de lava. Lento e desconfiado: olha pros lados e arrota lava. Não entra na água (apaga).',
      'Erupção': 'Agacha e incha 1 s (a cratera fica branca: é o aviso), estoura pra cima e cinco bombas de lava caem em volta de você. Cada bomba deixa uma poça de magma que queima por uns segundos.',
      'Bafo de Cinza': 'Abre a boca rachada e sopra uma nuvem de cinza quente que rola pelo chão: dentro dela a tela fecha (cego 3 s) e queima devagar.',
      'Fraqueza': 'Água: um balde na cratera apaga (atordoado 5 s, toma dano dobrado). De costas não consegue mirar a Erupção.',
      'Solta': 'Pedra-pomes Amaldiçoada, Cinza Vulcânica; raro: Núcleo de Magma (combustível que nunca acaba).',
      'Variações': 'Basalto, cinza clara ou obsidiana; escorridos de lava em lugares diferentes; com ou sem pedras grudadas.',
    },
  });
})();
