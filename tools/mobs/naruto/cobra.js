// Cobra Gigante (Floresta da Morte). Forte e rara, tier 6, uma por região. ~13 blocos de comprimento.
// Golpes: Bote (enrola o pescoço em S, avança 8 blocos de uma vez) e Engolir (prende o alvo dentro dela 4 s).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const N = 18, SEG = 12;

  const SKINS = [
    { nome: 'Violeta', back: [96, 82, 116], dark: [38, 30, 50], belly: [226, 214, 176], eye: [250, 206, 40] },
    { nome: 'Musgo', back: [88, 98, 62], dark: [34, 40, 26], belly: [222, 208, 160], eye: [240, 120, 30] },
  ];
  const width = i => { const f = i / (N - 1); return 3 + 14 * ENG.smooth(0, 0.42, f) - 2.6 * ENG.smooth(0.82, 1, f); };

  function painters(v) {
    const scales = (c, q) => {
      // escamas em losango pela posição: cada uma escurece na borda de baixo
      const u = q.ax * 0.5 + q.az * 0.35, w = q.az * 0.35 - q.ax * 0.5 + q.ay * 0.4;
      const e = Math.min(fr(u), fr(w));
      return e < 0.14 ? mul(c, 0.82) : c;
    };
    const body = q => {
      const belly = q.f === 'bottom' || ((q.f === 'west' || q.f === 'east') && q.ny > 0.72);
      if (belly) {
        let c = cel(v.belly, n3(q.ax, q.ay, q.az * 0.3, 2), 0.86, 1.06);
        if (fr(q.az * 0.25) < 0.16) c = mul(c, 0.78);                                               // placas da barriga
        return c;
      }
      let c = cel(v.back, n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.12, 3), 0.8, 1.12);
      // losangos escuros pelas costas, cada um deformado pelo ruído, com miolo um pouco mais claro
      const dz = fr(q.az / 26 + n3(q.az * 0.05, 0, 0, 4) * 0.25) - 0.5, dx = q.ax / 9;
      const dia = Math.abs(dz) * 2 + Math.abs(dx) * 1.2;
      if (q.f === 'top' || q.ny < 0.5) {
        if (dia < 0.62 && dia > 0.42) c = v.dark;
        else if (dia <= 0.42) c = mix(c, v.dark, 0.35);
      }
      if ((q.f === 'west' || q.f === 'east') && fr(q.az / 26 + 0.5) < 0.14 && q.ny > 0.35) c = mul(c, 0.7); // manchas dos lados
      return scales(c, q);
    };
    const head = q => {
      let c = body(q);
      if (q.f === 'west' || q.f === 'east') {
        // olho amarelo de pupila em fenda, com a crista escura em cima
        const ex = q.pz, ey = q.py;
        if (ex > 4 && ex < 8.5 && ey > 2.2 && ey < 5.6) {
          c = v.eye;
          if (Math.abs(ex - 6.2) < 0.6) c = [12, 10, 10];
          if (ex > 4 && ex < 5 && ey < 3.2) c = [255, 250, 220];
        }
        if (ey < 2.2 && ex > 3 && ex < 10) c = mul(v.dark, 1.1);
        if (ey > 8 && q.pz < 15) c = mul(v.belly, 0.9);                                          // lábio claro
      }
      if (q.f === 'north' && q.ny < 0.4 && (Math.abs(q.nx - 0.32) < 0.06 || Math.abs(q.nx - 0.68) < 0.06)) c = [20, 16, 18]; // narinas
      if (q.f === 'bottom') {
        // céu da boca, rosado escuro, com as presas
        c = q.nz < 0.85 ? [120, 40, 60] : mul(v.belly, 0.9);
        if (q.nz < 0.18 && (q.nx < 0.12 || q.nx > 0.88)) c = [246, 244, 236];
      }
      return c;
    };
    const jaw = q => {
      if (q.f === 'top') {
        let c = q.nz < 0.9 ? [150, 52, 72] : mul(v.belly, 0.9);
        if (Math.abs(q.nx - 0.5) < 0.16 && q.nz < 0.85) c = [96, 30, 46];                          // garganta
        if (q.nx < 0.1 || q.nx > 0.9) c = [236, 232, 220];
        return c;
      }
      return q.f === 'bottom' ? cel(v.belly, n3(q.ax, q.ay, q.az, 5), 0.86, 1.06) : body(q);
    };
    const fang = q => (q.ny > 0.85 ? [255, 255, 250] : [238, 234, 222]);
    const tongue = q => (q.nz < 0.3 && Math.abs(q.nx - 0.5) < 0.18 ? null : [180, 30, 50]);
    const tail = q => body(q);
    return { body, head, jaw, fang, tongue, tail };
  }

  function chainDef(v, n, low) {
    const P = painters(v);
    const ws = [];
    for (let i = 0; i < n; i++) ws.push(width(i * (N - 1) / (n - 1)));
    const len = SEG * N / n;
    const segs = [];
    for (let i = 0; i < n; i++) {
      const w = ws[i], h = w * 0.78, hp = i ? ws[i - 1] * 0.78 : h;
      segs.push({
        name: 's' + i, pivot: i ? [0, (hp - h) / 2, -len] : [0, 24 - h / 2, len * n / 2],
        boxes: [{ x: -w / 2, y: -h / 2, z: -len - 0.6, w, h, d: len + 1.2, p: i < 3 ? P.tail : P.body }],
        kids: [],
      });
    }
    for (let i = 0; i < n - 1; i++) segs[i].kids.push(segs[i + 1]);
    const hl = ws[n - 1] * 0.78;
    const head = {
      name: 'cabeca', pivot: [0, -1, -len], boxes: [
        { x: -10, y: hl / 2 - 11, z: -21, w: 20, h: 10, d: 21, p: P.head },
      ], kids: low ? [] : [
        { name: 'mandibula', pivot: [0, hl / 2 - 1, -2], boxes: [{ x: -9, y: 0, z: -18.5, w: 18, h: 3.5, d: 18.5, p: P.jaw }] },
        { name: 'presaD', pivot: [-7.6, hl / 2 - 1, -18.4], boxes: [{ x: -0.7, y: 0, z: -0.7, w: 1.4, h: 4, d: 1.4, p: P.fang }] },
        { name: 'presaE', pivot: [7.6, hl / 2 - 1, -18.4], boxes: [{ x: -0.7, y: 0, z: -0.7, w: 1.4, h: 4, d: 1.4, p: P.fang }] },
        { name: 'lingua', pivot: [0, hl / 2 - 0.5, -19], boxes: [{ x: -1.2, y: -0.3, z: -7, w: 2.4, h: 0.6, d: 7, p: P.tongue }] },
      ],
    };
    segs[n - 1].kids.push(head);
    return { name: 'raiz', pivot: [0, 0, 0], kids: [segs[0]] };
  }

  const WAVE = [[0, -1], [0.22, -0.4], [0.5, 1], [0.72, 0.4]];
  function anim(P, t, mode, a, obj, ent, ctx) {
    const n = P.s9 ? N : 8;
    const r = (k, x, y, z) => { if (!P[k]) return; const o = P[k].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = i => 's' + Math.round(i * (n - 1) / (N - 1));
    // repouso: corpo em S largo no chão, pescoço erguido, cabeça nivelada
    const rest = (lift = 1) => {
      for (let i = 0; i < N; i++) { const k = s(i); if (n < N && i % 2) continue; r(k, 0, 0.16 * Math.sin(i * 0.55 + 0.6) * (n < N ? 2 : 1), 0); }
      r(s(N - 6), -0.32 * lift, 0, 0); r(s(N - 5), -0.32 * lift, 0, 0); r(s(N - 4), -0.22 * lift, 0, 0);
      r(s(N - 2), 0.4 * lift, 0, 0); r('cabeca', 0.42 * lift, 0, 0);
    };
    const flick = T => { const f = seq([[0, 0], [0.08, 1, 'snap'], [0.22, 1], [0.3, 0]], T); if (P.lingua) P.lingua.obj.scale.z = Math.max(0.01, f); r('lingua', 0, 0, 0); };
    if (mode === 'parado') {
      rest();
      const br = loop([[0, 0], [0.45, 1], [0.999, 0]], t / 3.8);
      r(s(N - 6), -0.03 * br, 0, 0);
      flick((t % 2.6));
      return;
    }
    if (mode === 'andar') {
      const ph = t * 0.7;
      for (let i = 0; i < N; i++) { if (n < N && i % 2) continue; r(s(i), 0, 0.2 * loop(WAVE, ph - i * 0.075) * (0.4 + 0.6 * i / N) * (n < N ? 2 : 1), 0); }
      r(s(N - 4), -0.1, 0, 0); r('cabeca', 0.1, 0, 0);
      obj.position.z -= fr(t * 0.18) * 0;
      flick(t % 1.9);
      return;
    }
    const R = ctx.ref;
    if (mode === 'bote') {
      const T = t % 3.2;
      R.place(0, -11, 0);
      const coil = seq([[0.1, 0], [0.9, 1, 'out'], [0.98, 1]], T);
      const strike = seq([[0.98, 0], [1.06, 1, 'snap'], [1.5, 1], [2.3, 0, 'io']], T);
      const recoil = 1 - strike;
      rest(1 - 0.4 * strike);
      // pescoço em S apertado (recua a cabeça) e depois tudo esticado pra frente
      const S = [0, 0.5, -0.7, 0.75, -0.55];
      for (let j = 0; j < 5; j++) r(s(N - 2 - j * 1.4), -0.08 * coil * recoil, S[j] * coil * recoil, 0);
      for (let i = 0; i < N; i++) { if (n < N && i % 2) continue; r(s(i), 0, -0.16 * Math.sin(i * 0.55 + 0.6) * strike * (n < N ? 2 : 1), 0); }
      r(s(N - 6), 0.2 * strike, 0, 0); r(s(N - 5), 0.18 * strike, 0, 0);
      obj.position.z -= 2.4 * strike;
      r('mandibula', 0.15 * coil + 0.9 * seq([[0.9, 0], [1.0, 1], [1.08, 0.1, 'snap'], [1.5, 0.1], [1.7, 0]], T), 0, 0);
      r('cabeca', -0.3 * coil * recoil, 0, 0);
      flick(T < 0.9 ? (T % 0.45) : 1);
      if (T > 1.04 && T < 1.9) {
        const k = seq([[1.04, 0], [1.4, 1, 'out']], T);
        R.place(0, -11 - 3 * k, 0.8 * Math.sin(Math.PI * FX.clamp01((T - 1.04) / 0.5)));
        R.lean(-0.8 * Math.sin(Math.PI * FX.clamp01((T - 1.04) / 0.8)));
        if (T < 1.25) ctx.shake(1 - (T - 1.04) / 0.21);
      }
      return;
    }
    if (mode === 'engolir') {
      const T = t % 6.2;
      R.place(0, -9.6, 0);
      const strike = seq([[0.5, 0], [0.6, 1, 'snap'], [5.0, 1], [5.6, 0]], T);
      const open = seq([[0.2, 0], [0.5, 1.25, 'out'], [0.66, 1.25], [0.82, 0.05, 'snap'], [4.5, 0.05], [4.65, 1.3, 'snap'], [5.0, 0.2]], T);
      rest(1 - 0.5 * strike);
      for (let i = 0; i < N; i++) { if (n < N && i % 2) continue; r(s(i), 0, -0.12 * Math.sin(i * 0.55 + 0.6) * strike * (n < N ? 2 : 1), 0); }
      obj.position.z -= 1.4 * strike;
      r('mandibula', open, 0, 0);
      r('cabeca', -0.25 * open, 0, 0);
      // o alvo some na boca; o volume desce pelo corpo; depois é cuspido
      const inside = T > 0.74 && T < 4.6;
      if (inside) R.place(0, 99, 0);
      if (T > 0.6 && T < 0.74) R.place(0, -9.6 + 0.6, 0.4);
      const bulge = FX.clamp01((T - 0.9) / 3.4);
      for (let i = 0; i < n; i++) {
        const pos = (n - 1 - i) / (n - 1), d = (pos - bulge * 0.75) / 0.12;
        const k = inside ? Math.exp(-d * d) : 0;
        P['s' + i].obj.scale.set(1 + 0.55 * k, 1 + 0.45 * k, 1);
      }
      // a cobra se contorce enquanto engole
      const writhe = inside ? seq([[1.2, 0], [1.5, 1], [1.9, -0.6], [2.4, 0.8], [3.0, -0.4], [3.6, 0]], T) : 0;
      for (let i = 0; i < N; i += 3) r(s(i), 0, 0.12 * writhe * (i % 6 ? 1 : -1), 0);
      if (T > 4.6) {
        const k = seq([[4.6, 0], [5.2, 1, 'out']], T);
        R.place(0, -9.0 - 3.5 * k, 1.4 * Math.sin(Math.PI * FX.clamp01((T - 4.6) / 0.6)));
        R.lean(-1.1 * Math.sin(Math.PI * FX.clamp01((T - 4.6) / 1.1)));
        if (T < 4.8) ctx.shake(1 - (T - 4.6) / 0.2);
      }
      if (inside) ctx.dark(Math.min(1, (T - 0.74) / 0.2) * (1 - FX.clamp01((T - 4.4) / 0.2)));
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const vs = SKINS.map((v, i) => ({ nome: v.nome, v: Object.assign({ seed: i }, v), def: chainDef(v, N, false), low: chainDef(v, 8, true) }));
  window.MOBS.push({
    id: 'cobra_gigante', nome: 'Cobra Gigante', bioma: 'Floresta da Morte', tier: 'Forte, rara · tier 6',
    variants: vs, anim,
    modes: [['parado', 'Parado'], ['andar', 'Rastejar'], ['bote', 'Bote'], ['engolir', 'Engolir']],
    cam: 13, lookY: 1.2, lookZ: { parado: -4.5, andar: -4.5, bote: -8, engolir: -7.5 }, camFar: { bote: 15, engolir: 14 }, camYaw: { bote: -1.25, engolir: -1.1 }, refX: 4,
    info: {
      'Tamanho': '13 blocos de comprimento, 0,9 de grossura; a cabeça tem 1 bloco de largura',
      'Vida': '300 (tier 6)',
      'Jeito': 'Rara, uma por região. Dorme enrolada nas raízes e acorda com barulho ou luta perto.',
      'Bote': 'Enrola o pescoço em S por 0,8 s (aviso) e avança 8 blocos de uma vez. Quem sai da linha escapa.',
      'Engolir': 'Abre a boca, fecha em você e te prende lá dentro 4 s com a tela escura. Dar dano por dentro faz ela abrir antes e te cuspir.',
      'Solta': 'Escama de cobra e presa (raras); couro.',
    },
  });
})();
