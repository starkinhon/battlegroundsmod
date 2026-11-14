// Golem de Basalto (Terras do Jogo). Tier 5. Maldição do medo de ser soterrado pelo vulcão: um gigante de três
// blocos feito de colunas de basalto (prismas que o magma esfria e racha) grudadas por veias de magma que brilham
// nas juntas. Braços de gorila até o chão, punhos que são blocos de rocha, cabeça afundada nos ombros com dois
// olhos em fresta, e um feixe de colunas saindo das costas como espinhos.
// Golpes: Punho de Magma (levanta os dois punhos, eles ficam brancos de quente, e esmaga o chão: rachaduras de
// magma correm até você e estouram) e Colunas de Basalto (pisa e uma fileira de colunas sextavadas brota do chão
// até debaixo de você, uma atrás da outra, e te joga pra cima).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  // [nome, rocha, rocha clara, magma, magma escuro]
  const KINDS = [
    ['Basalto', [58, 56, 60], [92, 88, 92], [255, 140, 36], [170, 40, 14]],
    ['Ardósia', [70, 76, 80], [110, 118, 122], [255, 214, 90], [200, 110, 20]],
    ['Obsidiana', [28, 22, 34], [66, 52, 80], [255, 70, 60], [130, 16, 36]],
  ];
  function variant(seed, kind) {
    const R = FX.rnd(seed);
    const k = kind ?? Math.floor(R() * KINDS.length);
    const cols = []; for (let i = 0; i < 5; i++) cols.push({ top: R() * 4.5, d: 10 + R() * 2.4, z: (R() - 0.5) * 1.2 });
    const spikes = []; for (let i = 0; i < 6; i++) spikes.push({ x: (i - 2.5) * 3.1 + (R() - 0.5), h: 6 + R() * 8, z: 2 + R() * 3, lean: (i - 2.5) * 0.07 + (R() - 0.5) * 0.1 });
    return { seed, kind: k, rock: KINDS[k][1], rock2: KINDS[k][2], hot: KINDS[k][3], dark: KINDS[k][4], cols, spikes, size: 0.96 + R() * 0.08 };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.7, hi = 1.18) => {
      const f = 0.6 - 0.3 * q.ny + 0.28 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.24 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const P = {};
    // coluna de basalto: faixas horizontais de resfriamento, aresta clara no meio da face (o prisma), topo com
    // desenho de hexágono, e magma nas juntas (bordas da caixa) que brilha mais embaixo
    const basalt = (q, s, seam = 0.09) => {
      let c = shade(v.rock, q, s);
      if (n3(q.ax * 0.5, q.ay * 1.6, q.az * 0.5, s + 1 + S) > 0.64) c = shade(v.rock2, q, s + 2);
      if (q.f === 'top' || q.f === 'bottom') {
        const x = q.nx - 0.5, z = q.nz - 0.5;
        const hex = Math.max(Math.abs(x) * 0.866 + Math.abs(z) * 0.5, Math.abs(z));
        if (Math.abs(hex - 0.42) < 0.05) return mul(c, 0.6);
        if (hex > 0.47) return mix(v.dark, v.hot, 0.5 * n3(q.ax, 0, q.az, s + 3 + S));
        return c;
      }
      const u = q.f === 'north' || q.f === 'south' ? q.nx : q.nz;
      if (u < seam || u > 1 - seam) { const k = n3(q.ax * 0.45, q.ay * 0.45, q.az * 0.45, s + 4 + S); return k > 0.56 ? mix(v.dark, v.hot, Math.min(1, (k - 0.56) * 4)) : mul(v.rock, 0.45); }
      if (Math.abs(u - 0.5) < 0.04) c = mul(c, 1.18);
      if (fr(q.ay * 0.3 + 0.4 * n3(q.ax * 0.2, 0, q.az * 0.2, s + 5 + S)) < 0.07) c = n3(q.ax, q.ay, q.az, s + 6 + S) > 0.6 ? v.dark : mul(c, 0.62);
      return c;
    };
    P.col = q => basalt(q, 1);
    P.limb = q => basalt(q, 2, 0.07);
    P.spike = q => { let c = basalt(q, 3, 0.06); if (q.f !== 'top' && q.ny < 0.12) c = mix(c, [60, 58, 60], 0.4); return c; };
    // punho: bloco de rocha com os nós dos dedos marcados em magma
    P.fist = q => {
      let c = basalt(q, 4, 0.05);
      if (q.f === 'north' || q.f === 'bottom') { const k = fr(q.nx * 4); if (k < 0.1) return mix(v.dark, v.hot, 0.8); }
      return c;
    };
    // pelve/barriga: placa com uma fenda grande de magma
    P.core = q => {
      let c = basalt(q, 5, 0.04);
      if (q.f === 'north') { const x = q.nx - 0.5, y = q.ny - 0.5; if (Math.abs(x + 0.06 * Math.sin(y * 14)) < 0.06 * (1 - Math.abs(y) * 1.4)) return mix(v.hot, [255, 250, 220], 0.35); }
      return c;
    };
    // rosto: bitmap 7x7, testa pesada, dois olhos em fresta e uma boca de fenda
    const FACE = [
      'bbbbbbb',
      'BBBBBBB',
      '.EE.EE.',
      '.......',
      '.......',
      '.MMMMM.',
      '.......',
    ];
    P.head = q => {
      if (q.f !== 'north') return basalt(q, 6, 0.05);
      const col = Math.min(6, Math.floor(q.px * 7 / q.b.w)), row = Math.min(6, Math.floor(q.py * 7 / q.b.h));
      const ch = FACE[row][col];
      if (ch === 'b') return shade(v.rock2, q, 7);
      if (ch === 'B') return mul(v.rock, 0.42);
      if (ch === 'E') return mix(v.hot, [255, 252, 230], 0.55);
      if (ch === 'M') return mix(v.dark, v.hot, 0.7);
      return basalt(q, 6, 0.0);
    };
    return P;
  }

  function rig(P, v, lo) {
    const chest = v.cols.map((c, i) => ({ x: -10 + i * 4, y: -15 + c.top, z: -c.d / 2 + c.z, w: 4, h: 16 - c.top, d: c.d, p: P.col }));
    const pauldron = s => (lo ? [[0, 6, 0]] : [[-1.8, 6.5, -1.6], [1.6, 8.6, -0.6], [0, 5.4, 2.2], [-1.4, 4.2, 2.6]]).map(([dx, h, dz]) => ({ x: s * 1 + dx - 1.8, y: -3 - h, z: dz - 1.8, w: 3.6, h: h + 2, d: 3.6, p: P.col }));
    const arm = s => {
      const n = s < 0 ? 'D' : 'E';
      return {
        name: 'braco' + n, pivot: [s * 14.4, -12, 0], rot: [0, 0, -s * 0.2], boxes: [{ x: -3.2, y: -2, z: -3.2, w: 6.4, h: 13, d: 6.4, p: P.limb }, ...pauldron(s)],
        kids: [{
          name: 'ante' + n, pivot: [0, 11, 0], rot: [-0.15, 0, 0], boxes: [{ x: -3.4, y: 0, z: -3.4, w: 6.8, h: 10, d: 6.8, p: P.limb }, ...(lo ? [] : [{ x: s < 0 ? -4.2 : 1.6, y: 2, z: -2, w: 2.6, h: 6, d: 4, p: P.col }])],
          kids: [{ name: 'punho' + n, pivot: [0, 9.6, 0], boxes: [{ x: -4.6, y: 0, z: -4.6, w: 9.2, h: 7.4, d: 9.2, p: P.fist }] }],
        }],
      };
    };
    const leg = s => {
      const n = s < 0 ? 'D' : 'E';
      return {
        name: 'perna' + n, pivot: [s * 5.2, 8, 0], boxes: [{ x: -3.6, y: -1, z: -3.6, w: 7.2, h: 9.6, d: 7.2, p: P.limb }],
        kids: [{ name: 'canela' + n, pivot: [0, 8.2, 0], boxes: [{ x: -3.9, y: 0, z: -4.4, w: 7.8, h: 7.8, d: 8.2, p: P.limb }, ...(lo ? [] : [{ x: -4.2, y: 5.6, z: -5.6, w: 8.4, h: 2.2, d: 3, p: P.col }])] }],
      };
    };
    const spikes = (lo ? v.spikes.filter((_, i) => i % 2 === 0) : v.spikes).map((sp, i) => ({
      name: 'espinho' + i, pivot: [sp.x, -12, sp.z], rot: [0.32, 0, sp.lean],
      boxes: [{ x: -1.5, y: -sp.h, z: -1.5, w: 3, h: sp.h + 2, d: 3, p: P.spike }],
    }));
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'tronco', pivot: [0, 6, 0], rot: [0.14, 0, 0], boxes: [
            ...chest,
            { x: -7.4, y: 0, z: -5, w: 14.8, h: 4, d: 10, p: P.core },
            { x: -12.6, y: -15.6, z: -5.6, w: 25.2, h: 4.6, d: 11.2, p: P.col },
          ],
          kids: [
            { name: 'cabeca', pivot: [0, -14.6, -3.4], rot: [-0.14, 0, 0], boxes: [{ x: -3.6, y: -6.6, z: -4.2, w: 7.2, h: 7.2, d: 7.4, p: P.head }] },
            arm(-1), arm(1), ...spikes,
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
    const C = e.v.hot.map(c => c / 255);
    const W = (n, x, y, z) => { obj.updateMatrixWorld(true); return new THREE.Vector3(x, y, z).applyMatrix4(P[n].obj.matrixWorld); };
    const eyes = (k = 1) => { for (const [i, ex] of [[0, -1.6], [1, 1.6]]) ctx.get('olho' + i, () => DBFX.glow(C)).at(W('cabeca', ex, -4.2, -4.4), 0.06, 0.9 * k, t, 0.5); };
    const core = (k = 1) => ctx.get('nucleo', () => DBFX.glow(C)).at(W('tronco', 0, 2, -5.2), 0.14, 0.8 * k, t, 1.4);
    const embers = (key, at, age, n = 14, sp = 0.5) => ctx.get(key, () => DBFX.grains({ n, seed: key.length * 9 + 1, add: true, palette: [e.v.hot, mix(e.v.hot, [255, 250, 220], 0.5), e.v.dark], size: [0.02, 0.04], drag: 1.4, grav: -0.5, life: 1.3, spread: 1.2, cone: R2 => [(R2() - 0.5) * sp, 0.2 + R2() * 0.5, (R2() - 0.5) * sp] })).at(at, age, -9);
    const dust = (key, at, age, n = 30) => ctx.get(key, () => DBFX.grains({ n, seed: key.length * 5 + 2, palette: [[70, 64, 62], [96, 90, 86], [50, 46, 46], e.v.dark], size: [0.03, 0.07], drag: 2, grav: 8, life: 0.7, cone: R2 => { const a2 = R2() * 6.28; return [Math.cos(a2) * (1 + R2() * 2), 1 + R2() * 2.5, Math.sin(a2) * (1 + R2() * 2)]; } })).at(at, age, 0.02);

    if (mode === 'parado') {
      // parado como montanha: respira devagar e pesado (o magma das juntas acende junto), os dedos-rocha
      // apoiados no chão, a cabeça vira devagar; fagulhas sobem das costas
      const br = loop([[0, 0], [0.5, 1, 'io'], [1, 0, 'io']], t / 4);
      r('tronco', 0.04 + 0.03 * br, 0, 0); r('cabeca', -0.04 - 0.03 * br, 0.3 * Math.sin(t * 0.35), 0);
      r('bracoD', -0.03 * br, 0, 0.02 * br); r('bracoE', -0.03 * br, 0, -0.02 * br);
      obj.position.y -= 0.02 * br;
      eyes(0.8 + 0.2 * br); core(0.7 + 0.5 * br);
      embers('costas', W('tronco', 0, -18, 4), fr(t / 1.6) * 1.6, 16, 1.2);
      return;
    }
    if (mode === 'andar') {
      // passo de gigante: cada pisada racha o chão e treme; os braços balançam pesados
      const ph = t * 1.8;
      r('pernaD', 0.35 * Math.sin(ph), 0, 0); r('pernaE', -0.35 * Math.sin(ph), 0, 0);
      r('canelaD', 0.3 * Math.max(0, -Math.sin(ph)), 0, 0); r('canelaE', 0.3 * Math.max(0, Math.sin(ph)), 0, 0);
      r('tronco', 0.1, 0.1 * Math.sin(ph), 0.05 * Math.sin(ph));
      r('bracoD', -0.35 * Math.sin(ph), 0, 0); r('bracoE', 0.35 * Math.sin(ph), 0, 0);
      obj.position.y += 0.05 * Math.abs(Math.cos(ph));
      const land = fr(ph / Math.PI + 0.5);
      if (land < 0.06) ctx.shake(0.35 * (1 - land / 0.06));
      eyes(1); core(1);
      return;
    }
    if (mode === 'punho') {
      const T = t % 5.6;
      const R = ctx.ref; const RZ = -3.6; R.place(0.2, RZ, 0); R.spin(Math.PI);
      // junta os punhos lá em cima (ficam brancos de quente), desce com tudo na frente do corpo; o chão afunda,
      // cinco rachaduras de magma correm em leque e a do meio passa debaixo de você e estoura
      const up = seq([[0.2, 0], [1.3, 1, 'io'], [1.55, 1.05], [1.72, -0.15, 'in'], [3.6, -0.15], [4.6, 0, 'io']], T);
      const upP = Math.max(0, up);
      r('bracoD', -2.9 * upP + 0.4 * Math.max(0, -up) * 0, 0, 0.35 * upP); r('bracoE', -2.9 * upP, 0, -0.35 * upP);
      r('anteD', -0.5 * upP, 0, 0); r('anteE', -0.5 * upP, 0, 0);
      const slam = seq([[1.55, 0], [1.72, 1, 'in'], [3.6, 1], [4.6, 0, 'io']], T);
      r('bracoD', -1.15 * slam, 0, 0.32 * slam); r('bracoE', -1.15 * slam, 0, -0.32 * slam);
      r('anteD', -0.35 * slam, 0, -0.25 * slam); r('anteE', -0.35 * slam, 0, 0.25 * slam);
      r('tronco', -0.18 * upP + 0.55 * slam, 0, 0); r('cabeca', 0.1 * upP - 0.3 * slam, 0, 0);
      r('pernaD', -0.3 * slam, 0, 0); r('pernaE', -0.3 * slam, 0, 0); r('canelaD', 0.5 * slam, 0, 0); r('canelaE', 0.5 * slam, 0, 0);
      obj.position.y -= 0.32 * slam;
      const heat = FX.clamp01((T - 0.6) / 0.9) * (T < 3.0 ? 1 : 1 - FX.clamp01((T - 3.0) / 1.2));
      for (const n of ['D', 'E']) ctx.get('quente' + n, () => DBFX.glow(C)).at(W('punho' + n, 0, 3.7, 0), 0.42, 1.4 * heat, t, 0.9);
      if (heat > 0.5 && T < 1.7) ctx.get('branco', () => DBFX.glow([1, 0.96, 0.85])).at(W('punhoD', 0, 3.7, 0).lerp(W('punhoE', 0, 3.7, 0), 0.5), 0.35, (heat - 0.5) * 2 * 0.8, t, 0.9);
      const imp = new THREE.Vector3(0, 0.02, -1.55);
      const ag = T - 1.72;
      if (ag > 0) {
        if (ag < 0.25) { ctx.flash(imp.clone().setY(0.6), 1.6 * (1 - ag / 0.25)); ctx.shake(1.2 * (1 - ag / 0.25)); }
        ctx.roarRing(imp, Math.min(0.999, ag * 1.3));
        dust('poeira', imp, ag, 50);
        ctx.get('poca', JJFX.magma).at(imp.clone().setY(0.014), 0.95, FX.EASE.out(FX.clamp01(ag * 4)), 1 - FX.clamp01((T - 5.0) / 0.5), t, FX.clamp01(1 - ag / 3.4) * 0.85 + 0.15, 5);
        const fan = [[0.2, RZ - 0.4], [-1.7, RZ + 0.2], [1.9, RZ + 0.3], [-2.6, -1.6], [2.7, -1.2]];
        fan.forEach(([x, z], i) => {
          const end = new THREE.Vector3(x, 0, z);
          const len = FX.EASE.out(FX.clamp01((ag - i * 0.04) / 0.45));
          ctx.get('racha' + i, JJFX.crack).at(imp, end, i === 0 ? 1.1 : 0.85, len, 1 - FX.clamp01((T - 5.0) / 0.5), t, FX.clamp01(1 - ag / 3.2) * 0.85 + 0.15, 3 + i * 7);
          // estouro na ponta da rachadura quando ela chega
          const ea = ag - 0.45 - i * 0.04;
          if (ea > 0 && ea < 1.4) ctx.get('estouro' + i, FX.volume).set(end.clone().setY(0.5), i === 0 ? 0.8 : 0.5, Math.min(0.999, ea / 1.4), { fire: 1.5, seed: 40 + i * 3.1, dens: 9, rise: 1.0, squash: 1.5, smoke: [0.22, 0.2, 0.2] });
          if (ea > 0 && ea < 0.8) embers('fag' + i, end, ea, 16, 1.6);
        });
      }
      const pop = seq([[2.15, 0], [2.4, 1, 'out'], [2.75, 0.2, 'in'], [3.0, 0]], T);
      const hit = seq([[2.15, 0], [2.25, 1, 'snap'], [3.4, 0.6], [4.2, 0]], T);
      R.place(0.2, RZ + 0.4 * FX.EASE.out(FX.clamp01((T - 2.15) / 0.5)), 1.4 * pop); R.lean(-0.4 * hit); R.armsOut(0.4 * hit);
      eyes(1 + heat); core(1 + heat);
      return;
    }
    if (mode === 'colunas') {
      const T = t % 5.6;
      const R = ctx.ref; const RZ = -4.4; R.place(0, RZ, 0); R.spin(Math.PI);
      // levanta o pé direito e pisa; a cada passo da onda nasce uma coluna sextavada mais alta que a anterior,
      // em fila até você; a última nasce debaixo de você e te arremessa
      const lift = seq([[0.2, 0], [0.9, 1, 'io'], [1.15, 1], [1.3, 0, 'in']], T);
      r('pernaD', -1.0 * lift, 0, 0); r('canelaD', 1.0 * lift, 0, 0); r('tronco', -0.08 * lift, 0, 0.08 * lift);
      r('bracoD', 0.3 * lift, 0, 0.3 * lift); r('bracoE', -0.2 * lift, 0, -0.4 * lift);
      // depois aponta pra frente com o punho e "puxa" as colunas
      const point = seq([[1.3, 0], [1.6, 1, 'out'], [3.8, 1], [4.5, 0, 'io']], T);
      r('bracoE', -1.3 * point, 0.2 * point, 0); r('tronco', 0.12 * point, -0.15 * point, 0);
      const stomp = T - 1.3;
      if (stomp > 0 && stomp < 0.25) { ctx.shake(1 - stomp / 0.25); }
      if (stomp > 0) { dust('pisada', new THREE.Vector3(-0.33, 0.02, -0.1), stomp, 30); ctx.roarRing(new THREE.Vector3(-0.33, 0.03, -0.1), Math.min(0.999, stomp * 1.6)); }
      const N = 6;
      for (let i = 0; i < N; i++) {
        const f = (i + 1) / N;
        const p = new THREE.Vector3(0.18 * Math.sin(i * 2.1), 0, -1.3 + (RZ + 1.3) * f);
        const t0 = 1.4 + i * 0.14;
        const h = (0.6 + 1.4 * f) * seq([[t0, 0], [t0 + 0.12, 1.08, 'out'], [t0 + 0.22, 1], [4.4, 1], [5.2, 0, 'in']], T);
        const heat = FX.clamp01(1 - (T - t0) / 2.4);
        ctx.get('coluna' + i, () => JJFX.basalt(i + 1)).at(p, 0.32 + 0.06 * f, h, heat * 0.9, i * 0.7);
        // vizinhas menores (uma coluna só ficava "copiada": cada ponto da fila tem um feixe próprio)
        for (let j = 0; j < 2; j++) {
          const a2 = i * 2.3 + j * 2.9, off = new THREE.Vector3(Math.cos(a2) * 0.42, 0, Math.sin(a2) * 0.3);
          const hj = h * (0.55 + 0.25 * j);
          ctx.get('viz' + i + '_' + j, () => JJFX.basalt(20 + i * 2 + j)).at(p.clone().add(off), 0.18 + 0.04 * j, hj, heat * 0.9, a2);
        }
        if (T > t0 && T < t0 + 0.6) dust('terra' + i, p, T - t0, 18);
        if (T > t0 && T < t0 + 0.15) ctx.shake(0.4);
      }
      // a última: debaixo de você
      const tl = 1.4 + (N - 1) * 0.14;
      const launch = seq([[tl, 0], [tl + 0.12, 1, 'out'], [3.6, 1], [4.4, 0, 'in']], T);
      R.place(0, RZ, 2.0 * launch); R.lean(-0.3 * launch); R.armsOut(0.5 * launch);
      if (T > tl && T < tl + 0.25) ctx.flash(new THREE.Vector3(0, 0.8, RZ), 1 - (T - tl) / 0.25);
      ctx.get('pocaC', JJFX.magma).at(new THREE.Vector3(0, 0.013, RZ), 0.9, FX.EASE.out(FX.clamp01((T - tl) * 4)), 1 - FX.clamp01((T - 5.0) / 0.5), t, FX.clamp01(1 - (T - tl) / 3), 9);
      eyes(1 + 0.6 * point); core(1 + 0.6 * point);
      return;
    }
    if (mode === 'morte') {
      // o magma das juntas apaga, ele cai de joelhos (o chão treme) e tomba pra frente; vira cinza preta
      const T = t % 4.4;
      const R = ctx.ref; R.place(0, -3.2, 0); R.spin(Math.PI);
      R.punch(seq([[0.15, 0], [0.25, 1, 'snap'], [0.6, 1], [0.9, 0]], T));
      const knee = seq([[0.3, 0], [0.9, 1, 'in']], T), fall = seq([[1.1, 0], [1.7, 1, 'in']], T);
      r('pernaD', -1.3 * knee, 0, 0); r('pernaE', -1.3 * knee, 0, 0); r('canelaD', 1.6 * knee, 0, 0); r('canelaE', 1.6 * knee, 0, 0);
      obj.position.y -= 0.62 * knee;
      P.tronco.obj.rotation.x += 1.25 * fall + 0.15 * knee;
      obj.position.y -= 0.35 * fall; obj.position.z -= 0.6 * fall;
      r('bracoD', -1.4 * fall, 0, 0.2); r('bracoE', -1.4 * fall, 0, -0.2); r('cabeca', 0.4 * fall, 0, 0);
      if (T > 0.88 && T < 1.05) ctx.shake(0.6);
      if (T > 1.68 && T < 1.95) { ctx.shake(1.2); }
      if (T > 1.7) dust('queda', new THREE.Vector3(0, 0.02, -1.4), T - 1.7, 50);
      const off = Math.max(0, 1 - T * 0.7);
      eyes(off); core(off);
      if (T > 0.3 && T < 2.6) embers('apaga', W('tronco', 0, -8, 0), T - 0.3, 30, 2);
      if (T > 0.22 && T < 0.4) ctx.flash(new THREE.Vector3(0, 1.5, -1.2), 1 - (T - 0.22) / 0.18);
      const dis = FX.clamp01((T - 2.0) / 1.8);
      JJFX.dissolve(e, dis, 0, 2.4);
      if (T > 2.0) ctx.get('cinza', () => JJFX.ash(17, 220)).at(new THREE.Vector3(0, 0.4, -1.4), T - 2.0, 0.02);
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(6, 0), variant(27, 1), variant(48, 2)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'golem', nome: 'Golem de Basalto', bioma: 'Terras do Jogo', tier: 'Grau 1 · tier 5',
    variants: fixed.map(v => ({ nome: KINDS[v.kind][0], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado (' + KINDS[v.kind][0] + ')', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['punho', 'Punho de Magma'], ['colunas', 'Colunas de Basalto'], ['morte', 'Exorcizado']],
    cam: 7.6, lookY: 1.6, lookZ: { punho: -1.6, colunas: -2.0, morte: -0.8 }, camFar: { punho: 9.6, colunas: 10.4, morte: 8.4 }, camYaw: { punho: -1.0, colunas: -1.15, morte: -1.1 },
    info: {
      'Tamanho': '3 blocos de altura, quase 2 de largura',
      'Vida': '180 (rocha: espada faz metade, picareta e explosão fazem dobrado)',
      'Jeito': 'Fica parado no meio do campo de lava e parece uma formação de pedra até você chegar perto (as juntas acendem). Lento, mas cada pisada racha o chão e treme a tela.',
      'Punho de Magma': 'Junta os dois punhos lá em cima (ficam brancos de quente: 1,5 s de aviso) e esmaga o chão: cinco rachaduras de magma correm em leque e estouram na ponta. Pular na hora certa escapa da rachadura.',
      'Colunas de Basalto': 'Pisa e aponta: uma fila de colunas sextavadas brota do chão até você, cada uma mais alta; a última nasce debaixo de você e te joga 2 blocos pra cima. As colunas ficam uns segundos e viram parede (dá pra usar de cobertura).',
      'Fraqueza': 'As juntas de magma: golpe no núcleo da barriga faz dano dobrado. Água na junta trinca a rocha (mais dano por 5 s).',
      'Solta': 'Coluna de Basalto (bloco decorativo que brilha), Magma Amaldiçoado; raro: Coração de Basalto (armadura: resistência a fogo e empurrão).',
      'Variações': 'Basalto (magma laranja), Ardósia (magma amarelo) ou Obsidiana (magma vermelho); colunas do peito e espinhos das costas diferentes em cada um.',
    },
  });
})();
