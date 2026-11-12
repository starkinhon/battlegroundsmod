// Centopeia Gigante (Floresta da Morte). Fraca, tier 9. Sai das raízes ocas das árvores gigantes.
// Golpes: Picada (ergue a frente e crava as presas: veneno 4 s) e Enrolar (se enrola na sua perna: lento 2 s).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const N = 14, SEG = 5;

  function painters(v) {
    const shell = q => {
      // casca brilhante: placa escura com borda clara e um reflexo comprido de verniz
      let c = cel(v.shell, n3(q.ax * 0.4, q.ay * 0.4, q.az * 0.4, 2 + v.seed), 0.78, 1.16);
      if (q.nz < 0.14) c = mul(c, 0.55);
      if (q.f === 'top' && Math.abs(q.nx - 0.35) < 0.08 && q.nz > 0.25) c = mix(c, [255, 240, 230], 0.55);
      if (q.f === 'bottom') c = v.under;
      return c;
    };
    const leg = q => (q.ny > 0.7 ? mul(v.leg, 0.6) : cel(v.leg, n3(q.ax, q.ay, q.az, 3), 0.82, 1.1));
    const head = q => {
      let c = shell(q);
      if (q.f === 'north') {
        c = mul(v.shell, 0.8);
        for (const ex of [0.22, 0.78]) if (Math.abs(q.nx - ex) < 0.12 && Math.abs(q.ny - 0.35) < 0.14) c = [20, 16, 16];
      }
      return c;
    };
    const fang = q => (q.ny > 0.6 ? [30, 20, 18] : [190, 120, 40]);
    const ant = () => mul(v.leg, 0.8);
    return { shell, leg, head, fang, ant };
  }
  function defOf(v, n, low) {
    const P = painters(v), len = SEG * N / n;
    const segs = [];
    for (let i = 0; i < n; i++) {
      const f = i / (n - 1), w = 4.2 + 2 * Math.sin(Math.PI * Math.min(1, f * 1.2)), h = 2.6;
      const kids = [];
      if (!low || i % 2 === 0) for (const s of [-1, 1]) kids.push({
        name: 'p' + i + (s < 0 ? 'D' : 'E'), pivot: [s * w / 2, 0.4, -len / 2], rot: [0, 0, s * 0.9],
        boxes: [{ x: s < 0 ? -4.2 : 0, y: -0.35, z: -0.35, w: 4.2, h: 0.7, d: 0.7, p: P.leg }],
        kids: low ? [] : [{ name: 'q' + i + (s < 0 ? 'D' : 'E'), pivot: [s * 4.1, 0, 0], rot: [0, 0, -s * 1.5], boxes: [{ x: s < 0 ? -3.4 : 0, y: -0.3, z: -0.3, w: 3.4, h: 0.6, d: 0.6, p: P.leg }] }],
      });
      segs.push({ name: 's' + i, pivot: i ? [0, 0, -len] : [0, 24 - 2.8, len * n / 2], boxes: [{ x: -w / 2, y: -h / 2, z: -len - 0.3, w, h, d: len + 0.6, p: P.shell }], kids });
    }
    for (let i = 0; i < n - 1; i++) segs[i].kids.push(segs[i + 1]);
    segs[n - 1].kids.push({
      name: 'cabeca', pivot: [0, 0, -len], boxes: [{ x: -3, y: -1.6, z: -3.6, w: 6, h: 3, d: 3.8, p: P.head }],
      kids: [
        { name: 'presaD', pivot: [-1.8, 1, -3.4], rot: [0, 0.4, 0], boxes: [{ x: -0.5, y: -0.5, z: -2.6, w: 1, h: 1, d: 2.6, p: P.fang }] },
        { name: 'presaE', pivot: [1.8, 1, -3.4], rot: [0, -0.4, 0], boxes: [{ x: -0.5, y: -0.5, z: -2.6, w: 1, h: 1, d: 2.6, p: P.fang }] },
      ].concat(low ? [] : [
        { name: 'anteD', pivot: [-1.6, -1.4, -3.2], rot: [-0.5, 0.5, 0], boxes: [{ x: -0.25, y: -0.25, z: -7, w: 0.5, h: 0.5, d: 7, p: P.ant }] },
        { name: 'anteE', pivot: [1.6, -1.4, -3.2], rot: [-0.5, -0.5, 0], boxes: [{ x: -0.25, y: -0.25, z: -7, w: 0.5, h: 0.5, d: 7, p: P.ant }] },
      ]),
    });
    // o rabo: dois ganchos
    segs[0].kids.push({ name: 'rabo', pivot: [0, 0, 0.3], boxes: [{ x: -1.8, y: -0.4, z: 0, w: 0.8, h: 0.8, d: 3.4, p: P.fang }, { x: 1, y: -0.4, z: 0, w: 0.8, h: 0.8, d: 3.4, p: P.fang }] });
    return { name: 'raiz', pivot: [0, 0, 0], kids: [segs[0]] };
  }

  // ondas de patas: cada par um pouco depois do anterior (metacronal), passo curto pra frente e longo pra trás
  const STEP = [[0, -0.5], [0.25, -0.2], [0.62, 0.45], [0.8, 0.2]];
  const LIFT = [[0, 0], [0.62, 0], [0.72, -0.5], [0.9, -0.2]];
  function legs(P, r, ph, amt) {
    for (let i = 0; i < N; i++) for (const s of ['D', 'E']) {
      const o = ph - i * 0.09 + (s === 'E' ? 0.5 : 0);
      r('p' + i + s, 0, loop(STEP, o) * amt * (s === 'D' ? -1 : 1), loop(LIFT, o) * amt * (s === 'D' ? -1 : 1));
    }
  }
  function anim(P, t, mode, a, obj, ent, ctx) {
    const r = (k, x, y, z) => { if (!P[k]) return; const o = P[k].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const n = P.s10 ? N : 7;
    const s = i => 's' + Math.round(i * (n - 1) / (N - 1));
    const ants = T => { r('anteD', loop([[0, 0], [0.3, 0.25], [0.5, -0.1], [0.75, 0.15]], T), 0, 0); r('anteE', loop([[0, 0.1], [0.25, -0.2], [0.6, 0.2]], T + 0.3), 0, 0); };
    if (mode === 'parado') {
      for (let i = 0; i < N; i += 2) r(s(i), 0, 0.1 * Math.sin(i * 0.7), 0);
      legs(P, r, t * 0.15, 0.3);
      ants(t * 0.6);
      return;
    }
    if (mode === 'andar') {
      legs(P, r, t * 2.2, 1);
      for (let i = 0; i < N; i++) { if (n < N && i % 2) continue; r(s(i), 0, 0.09 * loop([[0, -1], [0.5, 1]], t * 0.6 - i * 0.06) * (n < N ? 2 : 1), 0); }
      ants(t * 1.4);
      return;
    }
    const R = ctx.ref;
    if (mode === 'picada') {
      const T = t % 2.6;
      R.place(0, -4.4, 0);
      const rear = seq([[0.1, 0], [0.7, 1, 'out'], [0.78, 1]], T);
      const bite = seq([[0.78, 0], [0.84, 1, 'snap'], [1.3, 1], [1.8, 0]], T);
      legs(P, r, T * 1.2, 0.5);
      // ergue a frente como uma naja, as patas da frente abertas no ar
      for (const [k, x] of [[N - 6, -0.3], [N - 5, -0.35], [N - 4, -0.3], [N - 3, 0.2], [N - 2, 0.35]]) r(s(k), x * rear * (1 - bite) + [0.1, 0.12, 0.12, 0.1, 0.05][N - 2 - k] * 0, 0, 0);
      obj.position.z -= 1.5 * bite;
      for (const [k, x] of [[N - 6, 0.08], [N - 5, 0.1]]) r(s(k), x * bite, 0, 0);
      r('presaD', 0, 0.5 * rear * (1 - bite) - 0.5 * bite, 0); r('presaE', 0, -0.5 * rear * (1 - bite) + 0.5 * bite, 0);
      ants(T * 3);
      if (T > 0.84) {
        const pois = ctx.get('veneno', FX.volume);
        pois.set(new THREE.Vector3(0, 0.4, -4.25), 0.42, (T - 0.84) / 1.2, { seed: 9.3, dens: 6, rise: 0.5, freq: 2, smoke: [0.42, 0.72, 0.22] });
        R.lean(0.25 * Math.sin(Math.PI * FX.clamp01((T - 0.84) / 0.8)));
        ctx.poison(seq([[0.9, 0], [1.1, 1], [2.3, 1], [2.6, 0]], T));
      }
      return;
    }
    if (mode === 'enrolar') {
      const T = t % 3.6;
      // a frente (8 pares de patas) dá uma volta e pouco em espiral subindo pela canela
      const len = SEG * N / n, nf = Math.round(8 * n / N), a1 = 0.8 * N / n, D = 2.4;
      if (!ent._ring) {
        // centro da volta calculado na pose final (mundo: x do modelo invertido, 1/16)
        let x = 0, z = SEG * N / 2 - (n - nf) * len, ang = 0, cx = 0, cz = 0, c = 0;
        for (let k = n - nf; k <= n; k++) { cx += x; cz += z; c++; if (k < n) ang += a1; x += len * Math.sin(ang); z -= len * Math.cos(ang); }
        ent._ring = [-cx / c / 16, cz / c / 16 - D];
      }
      R.place(ent._ring[0] + 0.125, ent._ring[1]);   // perna direita do jogador no meio da volta
      const go = seq([[0.0, 0], [0.7, 1, 'out']], T);
      const wrap = seq([[0.6, 0], [1.25, 1, 'out'], [2.8, 1], [3.3, 0, 'io']], T);
      legs(P, r, T * 2.5, 1 - wrap * 0.8);
      obj.position.z -= D * go;
      for (let k = n - nf; k < n; k++) r('s' + k, -0.06 * wrap, a1 * wrap, 0);
      r('cabeca', -0.2 * wrap, 0.3 * wrap, 0);
      // aperta: as presas mordiscam e a canela treme
      const bite = wrap > 0.9 ? Math.max(0, Math.sin(T * 13)) : 0;
      r('presaD', 0, 0.45 * bite, 0); r('presaE', 0, -0.45 * bite, 0);
      if (wrap > 0.5) R.lean(0.08 * wrap + 0.02 * Math.sin(T * 30) * wrap);
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const SK = [
    { nome: 'Vermelha', seed: 1, shell: [150, 34, 30], under: [60, 30, 26], leg: [232, 176, 54] },
    { nome: 'Azul-negra', seed: 2, shell: [36, 44, 72], under: [24, 24, 30], leg: [226, 120, 40] },
  ];
  window.MOBS.push({
    id: 'centopeia', nome: 'Centopeia Gigante', bioma: 'Floresta da Morte', tier: 'Fraco · tier 9',
    variants: SK.map(v => ({ nome: v.nome, v, def: defOf(v, N, false), low: defOf(v, 7, true) })),
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['picada', 'Picada'], ['enrolar', 'Enrolar']],
    cam: 5, lookY: 0.5, lookZ: { picada: -2.6, enrolar: -2.6 }, camFar: { picada: 7, enrolar: 5 }, camYaw: { picada: -1.25, enrolar: -0.8 }, refX: 2.4,
    info: {
      'Tamanho': '4,5 blocos de comprimento, 0,4 de altura',
      'Vida': '70 (tier 9)',
      'Jeito': 'Hostil. Sai das raízes ocas das árvores gigantes quando você passa perto; várias patas, se mexe rápido.',
      'Picada': 'Ergue a frente do corpo (0,6 s de aviso) e crava as presas: veneno por 4 s.',
      'Enrolar': 'Corre até sua perna e se enrola nela: você fica lento por 2 s ou até acertar ela.',
      'Solta': 'Carapaça (material de armadura leve) e glândula de veneno.',
    },
  });
})();
