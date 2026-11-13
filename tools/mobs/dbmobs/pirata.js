// Pirata (Mar do Sul). Fraco, tier 9. Camisa listrada, lenço ou chapéu de três pontas, tapa-olho, argola de ouro,
// alguns com perna de pau. Sabre curvo na mão e bombas de pólvora na cintura.
// Golpes: Corte de Sabre (estocada e um corte largo) e Bomba de Pólvora (acende, joga alto, o pavio queima e explode).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  const STRIPES = [[[236, 232, 220], [176, 40, 40]], [[236, 232, 220], [40, 60, 120]], [[226, 218, 196], [40, 40, 44]], [[214, 206, 180], [150, 90, 40]]];
  const BANDANAS = [[180, 34, 34], [36, 40, 52], [40, 70, 140], [200, 140, 40], [30, 110, 90]];
  const PANTS = [[60, 52, 46], [80, 60, 40], [44, 50, 70], [110, 96, 70]];
  const SKINS = [[226, 176, 132], [198, 140, 98], [160, 108, 74], [236, 196, 160], [120, 80, 56]];
  const HAIRS = [[30, 26, 24], [110, 60, 30], [170, 90, 40], [70, 64, 60], [180, 170, 150]];
  function variant(seed) {
    const R = FX.rnd(seed);
    const pick = a => a[Math.floor(R() * a.length)];
    return {
      seed, stripe: pick(STRIPES), band: pick(BANDANAS), pants: pick(PANTS), skin: pick(SKINS), hair: pick(HAIRS),
      hat: R() < 0.35, patch: R() < 0.5, beard: R() < 0.6, peg: R() < 0.25, earring: R() < 0.6, vest: R() < 0.4,
    };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.82, hi = 1.12) => {
      const f = 0.62 - 0.4 * q.ny + 0.18 * (n3(q.ax * 0.2, q.ay * 0.2, q.az * 0.2, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const P = {};
    P.head = q => {
      let c = shade(v.skin, q, 1, 0.88, 1.05);
      const x = Math.floor(q.px), y = Math.floor(q.py);
      const hair = () => shade(v.hair, q, 2);
      const beard = () => cel(v.hair, n3(q.ax * 1.4, q.ay * 1.4, q.az, 3 + S), 0.8, 1.15);
      if (q.f === 'top') return hair();
      if (q.f === 'south') return y < 5 ? hair() : (v.beard ? beard() : c);
      if (q.f === 'west' || q.f === 'east') {
        if (q.pz > 4.6 && q.py < 5.4) return hair();
        if (q.pz > 2.8 && q.pz < 4.2 && q.py > 3 && q.py < 4.8) { c = mul(c, 0.84); if (v.earring && q.py > 4.3 && q.f === 'west') c = [236, 200, 70]; }
        if (v.patch && q.f === 'east' && Math.abs(q.py - (2.6 + (q.pz - 3.5) * 0.25)) < 0.4) c = [24, 22, 20];    // tira do tapa-olho
        if (v.beard && q.py >= 4.4) return beard();
        return c;
      }
      if (q.f !== 'north') return c;
      if (y === 2 && (x === 1 || x === 2 || x === 4 || x === 5)) c = mul(v.hair, 0.85);
      if (y === 3) { if (x === 1 || x === 5) c = [234, 230, 220]; if (x === 2 || x === 4) c = [30, 30, 40]; }
      if (v.patch && (x === 1 || x === 2) && (y === 2 || y === 3)) c = [22, 20, 18];
      if (v.patch && y === 1 && x >= 0 && x <= 3 && x === 3 - y + 1) c = [22, 20, 18];
      if (y === 4 && x === 3) c = mul(c, 0.84);
      if (v.beard && y >= 5) c = beard();
      if (y === 5 && x >= 2 && x <= 4) c = v.beard ? [70, 34, 30] : [126, 66, 56];
      if (v.beard && y === 4 && (x === 1 || x === 5)) c = beard();
      if (!v.beard && y === 6 && x === 4) c = [210, 200, 170];                                       // dente de ouro? um dente torto
      return c;
    };
    P.bandana = q => {
      let c = shade(v.band, q, 4, 0.78, 1.15);
      // bolinhas brancas do lenço, cada uma num lugar
      const cx = Math.floor(q.ax * 0.7 + 30), cy = Math.floor(q.ay * 0.7 + 30), cz = Math.floor(q.az * 0.7 + 30);
      if (n3(cx * 2.1, cy * 1.7, cz * 1.3, 5 + S) > 0.72) c = mix(c, [240, 236, 226], 0.85);
      return c;
    };
    P.knotTail = q => { if (q.ny > 0.82 + 0.18 * n3(Math.floor(q.px * 2), S, 6, 7)) return null; return P.bandana(q); };
    P.hat = q => {
      let c = shade([38, 32, 30], q, 8, 0.78, 1.3);
      if (q.f !== 'top' && q.ny > 0.78) c = [196, 160, 70];                                            // debrum dourado
      if (q.f === 'north' && Math.abs(q.nx - 0.5) < 0.16 && q.ny > 0.2 && q.ny < 0.68) {
        // caveira branca pequena
        const x = (q.nx - 0.5) / 0.16, y = (q.ny - 0.2) / 0.48;
        c = [236, 232, 222];
        if (y > 0.25 && y < 0.5 && Math.abs(Math.abs(x) - 0.45) < 0.3) c = [30, 26, 24];
        if (y > 0.75 && Math.abs(x) < 0.2) c = [30, 26, 24];
      }
      return c;
    };
    P.shirt = q => {
      const [a, b] = v.stripe;
      // listras horizontais que seguem a camisa amassada (ondulam um pouco)
      const k = fr(q.ay * 0.5 + 0.3 * n3(q.ax * 0.3, q.ay * 0.2, q.az * 0.3, 9 + S));
      let c = shade(k < 0.5 ? a : b, q, 10, 0.84, 1.08);
      if (q.f === 'north' && q.ny < 0.35 && Math.abs(q.nx - 0.5) < 0.12 - q.ny * 0.25) c = shade(v.skin, q, 1);   // gola em V
      if (q.f === 'north' && v.beard && q.ny < 0.12 && Math.abs(q.nx - 0.5) < 0.3) c = cel(v.hair, 0.5, 0.8, 1.1);
      return c;
    };
    P.vest = q => {
      let c = shade([60, 40, 30], q, 11, 0.78, 1.16);
      if (q.f === 'north' && Math.abs(Math.abs(q.nx - 0.5) - 0.24) < 0.03 && q.ny > 0.2 && q.ny < 0.8) c = [196, 160, 70];   // botões
      return c;
    };
    P.sash = q => {
      let c = shade(mul(v.band, 0.9), q, 12, 0.78, 1.12);
      if (fr(q.ay * 0.45 + q.ax * 0.2) < 0.2) c = mul(c, 0.82);
      if (q.f === 'north' && Math.abs(q.nx - 0.5) < 0.08) c = [80, 60, 40];                           // fivela de corda
      return c;
    };
    P.pants = q => {
      let c = shade(v.pants, q, 13, 0.82, 1.12);
      if (fr(q.ax * 0.5 + q.az * 0.5 + n3(q.ax * 0.3, q.ay * 0.2, q.az * 0.3, 14 + S) * 1.2) < 0.13) c = mul(c, 0.8);
      if (n3(q.ax * 0.5, q.ay * 0.5, q.az * 0.5, 15 + S) > 0.82) c = mix(c, [120, 100, 80], 0.35);      // remendo
      return c;
    };
    P.sleeve = q => P.shirt(q);
    P.arm = q => { let c = shade(v.skin, q, 1, 0.86, 1.05); if (q.ny < 0.3) c = P.shirt(q); return c; };
    P.hand = q => { let c = shade(v.skin, q, 1, 0.86, 1.04); if (q.f === 'north' && fr(q.px * 1.4) < 0.18) c = mul(c, 0.8); return c; };
    P.boot = q => {
      if (q.f === 'bottom') return [30, 24, 20];
      let c = shade([56, 40, 30], q, 16, 0.78, 1.3);
      if (q.ny < 0.25) c = mul(c, 1.25);                                                                // cano dobrado
      return c;
    };
    P.peg = q => { let c = cel([150, 110, 70], n3(q.ax, q.ay * 0.3, q.az, 17), 0.78, 1.12); if (fr(q.ay * 0.4) < 0.08) c = mul(c, 0.75); return c; };
    P.bomb = q => {
      let c = shade([30, 30, 34], q, 18, 0.7, 1.5);
      if (q.f === 'top' && q.nx > 0.3 && q.nx < 0.6 && q.nz > 0.2 && q.nz < 0.5) c = [90, 92, 100];     // brilho
      return c;
    };
    P.lowTorso = q => q.ay > 11.6 ? P.pants(q) : q.ay > 10 ? P.sash(q) : P.shirt({ ...q, ny: (q.ay - 2) / 8 });
    P.lowArm = q => q.ay < 3.4 ? P.shirt(q) : q.ay < 10 ? P.arm({ ...q, ny: 0.6 }) : P.hand(q);
    P.lowLeg = q => q.ay < 19 ? P.pants(q) : P.boot({ ...q, ny: (q.ay - 19) / 5 });
    P.lowPeg = q => q.ay < 19 ? P.pants(q) : P.peg(q);
    return P;
  }

  function sabreDef() {
    const blade = q => {
      const edge = q.nx < 0.2;
      let c = mix([150, 156, 168], [216, 222, 232], n3(q.ax * 1.2, q.ay * 1.2, q.az * 1.2, 40));
      if (edge) c = [240, 244, 250];
      if (q.nx > 0.85) c = mul(c, 0.8);
      return c;
    };
    // lâmina curva: três pedaços cada um um pouco mais inclinado
    return {
      name: 'sabre', pivot: [0, 0, 0], boxes: [
        { x: -0.5, y: -1.6, z: -0.5, w: 1, h: 2.6, d: 1, p: () => [70, 44, 30] },
        { x: -1.6, y: -2.1, z: -0.7, w: 3.2, h: 0.6, d: 1.4, p: () => [200, 164, 70] },
        { x: -1.6, y: -2.1, z: -0.7, w: 0.5, h: 2.6, d: 0.5, p: () => [200, 164, 70] },
      ], kids: [{
        name: 's1', pivot: [0, -2.1, 0], boxes: [{ x: -0.7, y: -4.2, z: -0.2, w: 1.5, h: 4.2, d: 0.4, p: blade }],
        kids: [{
          name: 's2', pivot: [0, -4.2, 0], rot: [0, 0, 0.18], boxes: [{ x: -0.7, y: -4, z: -0.2, w: 1.5, h: 4, d: 0.4, p: blade }],
          kids: [{ name: 's3', pivot: [0, -4, 0], rot: [0, 0, 0.3], boxes: [{ x: -0.6, y: -3, z: -0.2, w: 1.2, h: 3, d: 0.4, p: blade }] }],
        }],
      }],
    };
  }

  function rigDef(P, v) {
    const arm = s => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * 5.5, -8, 0], boxes: [{ x: -1.5, y: -1, z: -1.5, w: 3, h: 5, d: 3, p: P.arm }, { x: -1.7, y: -1.2, z: -1.7, w: 3.4, h: 2.2, d: 3.4, p: P.sleeve }],
      kids: [{
        name: s < 0 ? 'anteD' : 'anteE', pivot: [0, 4, 0], boxes: [{ x: -1.4, y: 0, z: -1.4, w: 2.8, h: 4, d: 2.8, p: P.arm }],
        kids: [{ name: s < 0 ? 'maoD' : 'maoE', pivot: [0, 4, 0], boxes: [{ x: -1.3, y: 0, z: -1.3, w: 2.6, h: 2.2, d: 2.6, p: P.hand }], kids: s < 0 ? [{ ...sabreDef(), pivot: [0, 1.4, -0.4], rot: [-1.45, 0, 0] }] : [] }],
      }],
    });
    const leg = s => {
      const peg = v.peg && s > 0;
      return {
        name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * 2, 13, 0], boxes: [{ x: -2.1, y: 0, z: -2.1, w: 4.2, h: 6.2, d: 4.2, p: P.pants }],
        kids: [{
          name: s < 0 ? 'canelaD' : 'canelaE', pivot: [0, 6, 0], boxes: peg ? [{ x: -0.8, y: 0, z: -0.8, w: 1.6, h: 5, d: 1.6, p: P.peg }, { x: -1.6, y: -0.4, z: -1.6, w: 3.2, h: 1.2, d: 3.2, p: P.peg }] : [{ x: -2, y: 0, z: -2, w: 4, h: 3, d: 4, p: P.boot }],
          kids: [{ name: s < 0 ? 'peD' : 'peE', pivot: [0, peg ? 5 : 3, 0], boxes: peg ? [] : [{ x: -2, y: 0, z: -2.6, w: 4, h: 2, d: 4.6, p: P.boot }] }],
        }],
      };
    };
    const head = v.hat ? [
      { x: -5.2, y: -8.4, z: -5.2, w: 10.4, h: 1, d: 10.4, p: P.hat },
      { x: -3.9, y: -10.6, z: -3.9, w: 7.8, h: 2.4, d: 7.8, p: P.hat },
      { x: -3.0, y: -11.4, z: -3.0, w: 6, h: 1, d: 6, p: P.hat },
    ] : [
      { x: -3.85, y: -7.4, z: -3.85, w: 7.7, h: 3.3, d: 7.7, p: P.bandana },
    ];
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'quadril', pivot: [0, 13, 0], boxes: [
            { x: -4.1, y: -2, z: -2.1, w: 8.2, h: 2.2, d: 4.2, p: P.pants },
            { x: -4.4, y: -3.4, z: -2.4, w: 8.8, h: 1.9, d: 4.8, p: P.sash },
            { x: -5.6, y: -2.2, z: -1.2, w: 2.2, h: 2.2, d: 2.2, p: P.bomb },
            { x: -5.3, y: -2.9, z: -0.4, w: 0.6, h: 0.8, d: 0.6, p: () => [150, 120, 80] },
          ], kids: [{
            name: 'tronco', pivot: [0, -2, 0], boxes: [
              { x: -4, y: -9, z: -2, w: 8, h: 9, d: 4, p: P.shirt },
              ...(v.vest ? [{ x: -4.3, y: -9.2, z: -2.25, w: 2.5, h: 8.4, d: 4.5, p: P.vest }, { x: 1.8, y: -9.2, z: -2.25, w: 2.5, h: 8.4, d: 4.5, p: P.vest }] : []),
            ], kids: [
              {
                name: 'cabeca', pivot: [0, -9.3, 0], boxes: [{ x: -3.5, y: -7, z: -3.5, w: 7, h: 7, d: 7, p: P.head }, ...head],
                kids: v.hat ? [] : [{ name: 'nos', pivot: [-1.6, -5.4, 3.9], rot: [0.35, 0, -0.15], boxes: [{ x: -1, y: 0, z: 0, w: 2, h: 4.4, d: 0.4, p: P.knotTail }, { x: 0.6, y: 0, z: 0.1, w: 1.3, h: 3.6, d: 0.4, p: P.knotTail }] }],
              },
              arm(-1), arm(1),
            ],
          }],
        },
        leg(-1), leg(1),
      ],
    };
  }
  function rigLow(P, v) {
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'quadril', pivot: [0, 13, 0], boxes: [{ x: -4.1, y: -11.3, z: -2.1, w: 8.2, h: 11.5, d: 4.2, p: P.lowTorso }], kids: [
            { name: 'cabeca', pivot: [0, -11.3, 0], boxes: [{ x: -3.5, y: -7, z: -3.5, w: 7, h: 7, d: 7, p: P.head }, v.hat ? { x: -4.4, y: -10.4, z: -4.4, w: 8.8, h: 3.4, d: 8.8, p: P.hat } : { x: -3.85, y: -7.4, z: -3.85, w: 7.7, h: 3.3, d: 7.7, p: P.bandana }] },
            { name: 'bracoD', pivot: [-5.5, -10, 0], boxes: [{ x: -1.5, y: -1, z: -1.5, w: 3, h: 11.2, d: 3, p: P.lowArm }], kids: [{ name: 'maoD', pivot: [0, 9.4, 0], boxes: [], kids: [{ ...sabreDef(), pivot: [0, 0, -0.4], rot: [-1.45, 0, 0] }] }] },
            { name: 'bracoE', pivot: [5.5, -10, 0], boxes: [{ x: -1.5, y: -1, z: -1.5, w: 3, h: 11.2, d: 3, p: P.lowArm }] },
          ],
        },
        { name: 'pernaD', pivot: [-2, 13, 0], boxes: [{ x: -2.1, y: 0, z: -2.1, w: 4.2, h: 11, d: 4.2, p: P.lowLeg }] },
        { name: 'pernaE', pivot: [2, 13, 0], boxes: [{ x: -2.1, y: 0, z: -2.1, w: 4.2, h: 11, d: 4.2, p: v.peg ? P.lowPeg : P.lowLeg }] },
      ],
    };
  }

  function stance(r, P) {
    r('quadril', 0.05, -0.3, 0); P.quadril.obj.position.y += 0.7;
    r('tronco', 0.06, 0.25, 0); r('cabeca', -0.1, 0.05, 0.06);
    r('pernaD', -0.35, 0.3, 0.1); r('canelaD', 0.45, 0, 0); r('peD', -0.1, -0.2, 0);
    r('pernaE', 0.15, -0.15, -0.1); r('canelaE', 0.3, 0, 0);
    r('bracoD', -0.5, 0, 0.25); r('anteD', -0.55, 0, 0);
    r('bracoE', 0.1, 0, -0.35); r('anteE', -0.6, 0, 0);
  }
  const WALK_T = [[0, -0.42], [0.14, -0.28], [0.36, 0.1], [0.52, 0.42], [0.64, 0.22], [0.82, -0.3]];
  const WALK_K = [[0, 0.06], [0.14, 0.14], [0.4, 0.06], [0.56, 0.34], [0.7, 0.78], [0.86, 0.24]];
  const WALK_B = [[0, 0.1], [0.14, 0.42], [0.36, -0.12], [0.5, 0.1], [0.64, 0.42], [0.86, -0.12]];

  function bombModel() {
    const m = ENG.build({
      name: 'bomba', pivot: [0, 0, 0], boxes: [
        { x: -2.2, y: -2.2, z: -2.2, w: 4.4, h: 4.4, d: 4.4, p: q => { let c = cel([34, 34, 38], n3(q.ax, q.ay, q.az, 60), 0.7, 1.5); if (q.f === 'top' && q.nx < 0.5 && q.nz < 0.5) c = [100, 104, 112]; return c; } },
        { x: -1.6, y: -2.8, z: -1.6, w: 3.2, h: 0.6, d: 3.2, p: () => [70, 70, 76] },
        { x: -0.3, y: -4.6, z: -0.3, w: 0.6, h: 1.8, d: 0.6, p: q => (fr(q.ay * 1.2) < 0.5 ? [170, 140, 90] : [130, 104, 70]) },
      ],
    });
    return ENG.toThree(m, ENG.paint(m, m.W, m.H));
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const breath = loop([[0, 0], [0.42, 1], [0.999, 0]], t / 3.4);
    const peg = e.v.peg;

    if (mode === 'parado') {
      stance(r, P);
      r('tronco', -0.02 * breath, 0, 0);
      // gira o sabre na mão, de vez em quando
      const twirl = seq([[0, 0], [0.25, 1, 'io']], fr(t / 4.4)) * (fr(t / 4.4) < 0.25 ? 1 : 0);
      r('maoD', 0, twirl * Math.PI * 2, 0);
      r('nos', loop([[0, -0.05], [0.5, 0.08]], t / 2.6), 0, 0);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 0.95;
      for (const [s, off] of [['D', 0], ['E', 0.5]]) {
        // perna de pau: passo duro, sem dobrar
        const pegLeg = peg && s === 'E';
        r('perna' + s, loop(WALK_T, ph + off) * (pegLeg ? 0.7 : 1), 0, 0);
        r('canela' + s, pegLeg ? 0 : loop(WALK_K, ph + off), 0, 0);
        r('pe' + s, -0.25 * loop(WALK_K, ph + off - 0.06), 0, 0);
      }
      P.quadril.obj.position.y += loop(WALK_B, ph) + (peg ? 0.5 * loop([[0, 0], [0.5, 1]], ph) : 0);
      const tw = loop([[0, 0.06], [0.5, -0.06]], ph - 0.03);
      r('quadril', 0.04, tw, peg ? 0.06 * loop([[0, 1], [0.5, -1]], ph) : 0); r('tronco', 0.02, -tw * 0.8, 0);
      r('bracoD', -0.4 + 0.25 * loop([[0, 1], [0.5, -1]], ph), 0, 0.2); r('anteD', -0.5, 0, 0);
      r('bracoE', -0.35 * loop([[0, 1], [0.5, -1]], ph), 0, -0.15); r('anteE', -0.35, 0, 0);
      r('nos', 0.35, 0, 0);
      return;
    }

    if (mode === 'sabre') {
      const T = t % 2.8;
      const R = ctx.ref; const RZ = -3.4; R.place(0, RZ, 0);
      stance(r, P);
      // arma de lado, avança num passo largo (estocada) e corta da direita pra esquerda bem aberto
      const wind = seq([[0, 0], [0.45, 1, 'out'], [0.55, 1]], T);
      const lunge = seq([[0.5, 0], [0.66, 1, 'out'], [1.6, 1], [2.2, 0, 'io']], T);
      const cut = seq([[0.6, 0], [0.7, 1, 'snap'], [1.1, 1], [1.7, 0]], T);
      obj.position.z -= 1.5 * lunge;
      const w = wind * (1 - cut);
      const fol = seq([[0.68, 0], [0.8, 1, 'out'], [1.1, 1], [1.7, 0, 'io']], T);
      r('tronco', 0.1 * w + 0.15 * cut, 0.9 * w - 0.8 * cut - 0.3 * fol, 0);
      r('quadril', 0, 0.3 * w - 0.25 * cut, 0);
      // braço aberto na altura do ombro, sabre alinhado: quem varre é o giro do tronco, depois o braço acompanha
      const up = Math.max(w, seq([[0.6, 0], [0.62, 1], [1.2, 1], [1.7, 0, 'io']], T));
      r('bracoD', -1.25 * fol, 0.25 * up + 0.25 * cut - 0.25 * fol, 1.5 * up - 1.0 * fol); r('anteD', 0, 0, 0);
      r('maoD', -1.5 * up, 0, 0);
      r('bracoE', -0.6 * w + 0.4 * cut, 0, -0.9 * cut);
      P.quadril.obj.position.y += 1.6 * lunge;
      r('pernaD', -0.9 * lunge, 0, 0); r('canelaD', 1.0 * lunge, 0, 0); r('peD', -0.1 * lunge, 0, 0);
      r('pernaE', 0.55 * lunge, 0, 0); r('canelaE', (peg ? 0 : 0.5) * lunge, 0, 0);
      r('cabeca', -0.1 * lunge, -0.4 * w + 0.3 * cut, 0);
      r('nos', 0.4 * lunge, 0, 0.3 * cut);
      // o corte: arco largo na horizontal, da direita pra esquerda dele
      const ca = (T - 0.66) / 0.42;
      if (ca >= 0 && ca < 1) {
        const arc = ctx.get('arcoSabre', () => {
          const SEG = 40, geo = new THREE.BufferGeometry(), pos = new Float32Array((SEG + 1) * 6), al = new Float32Array((SEG + 1) * 2), idx = [];
          for (let s = 0; s < SEG; s++) { const a2 = s * 2; idx.push(a2, a2 + 1, a2 + 2, a2 + 1, a2 + 3, a2 + 2); }
          geo.setIndex(idx); geo.setAttribute('position', new THREE.BufferAttribute(pos, 3)); geo.setAttribute('al', new THREE.BufferAttribute(al, 1)); const rr = new Float32Array((SEG + 1) * 2); for (let s = 0; s <= SEG; s++) { rr[s * 2] = 0; rr[s * 2 + 1] = 1; } geo.setAttribute('rr', new THREE.BufferAttribute(rr, 1));
          const m = new THREE.Mesh(geo, new THREE.ShaderMaterial({ transparent: true, depthWrite: false, side: THREE.DoubleSide, blending: THREE.NormalBlending,
            vertexShader: 'attribute float al; varying float vA; attribute float rr; varying float vR; void main(){ vA=al; vR=rr; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
            fragmentShader: 'varying float vA; varying float vR; void main(){ vec3 c=mix(vec3(0.22,0.42,0.95),vec3(1.0),smoothstep(0.45,0.95,vR)); gl_FragColor=vec4(c, min(1.0,vA*1.15)); }' }));
          m.frustumCulled = false;
          m.at = (c, age) => {
            m.visible = true;
            for (let s = 0; s <= SEG; s++) {
              const f = s / SEG, ang = -1.25 + f * 2.5;
              const R1 = 0.8, R2 = 1.8, y = 1.36 - f * 0.1;
              pos.set([c.x + Math.sin(ang) * R1, y, c.z - Math.cos(ang) * R1, c.x + Math.sin(ang) * R2, y + 0.06, c.z - Math.cos(ang) * R2], s * 6);
              const lead = Math.min(1, age * 2.2);
              const a2 = f < lead ? Math.max(0, 1 - (lead - f) * 2.2) * (1 - age) * Math.sin(Math.PI * Math.min(1, f * 1.1)) : 0;
              al[s * 2] = a2 * 0.15; al[s * 2 + 1] = a2;
            }
            geo.attributes.position.needsUpdate = true; geo.attributes.al.needsUpdate = true;
          };
          return m;
        });
        arc.at(new THREE.Vector3(0, 0, -1.5 * lunge), ca);
      }
      const hit = seq([[0.72, 0], [0.76, 1, 'snap'], [1.0, 1], [1.5, 0]], T);
      R.lean(-0.3 * hit); R.spin(-0.45 * hit); R.place(0.3 * hit, RZ - 0.7 * hit, 0);
      if (T > 0.72 && T < 0.9) ctx.shake(0.4);
      return;
    }

    if (mode === 'bomba') {
      const T = t % 4.0;
      const R = ctx.ref; const RZ = -5.5; R.place(0, RZ, 0);
      stance(r, P);
      // tira a bomba da cintura, acende o pavio no isqueiro de pederneira, gira o braço e joga alto
      const take = seq([[0, 0], [0.35, 1, 'out']], T);
      const light = seq([[0.35, 0], [0.5, 1, 'out'], [0.8, 1]], T);
      const wind = seq([[0.8, 0], [1.05, 1, 'out']], T);
      const thr = seq([[1.05, 0], [1.13, 1, 'snap'], [1.4, 1], [1.8, 0]], T);
      const lw = Math.max(take * (1 - wind), 0);
      r('bracoE', -1.0 * lw + 1.2 * wind * (1 - thr) - 2.6 * thr, 0, -0.3 * lw), r('anteE', -1.0 * lw - 0.6 * wind * (1 - thr), 0, 0);
      r('bracoD', -0.6 * light * (1 - wind), 0.4 * light * (1 - wind), 0); r('anteD', -0.8 * light * (1 - wind), 0, 0);
      r('tronco', -0.2 * wind * (1 - thr) + 0.25 * thr, 0.4 * wind * (1 - thr) - 0.3 * thr, 0);
      r('cabeca', 0.25 * light * (1 - wind) - 0.2 * wind, 0, 0);
      const bm = ctx.get('bomba', () => { const g = new THREE.Group(); const m = bombModel(); m.position.y = -1.5; g.add(m); return g; });
      const handE = new THREE.Vector3(0.45, 1.0 + 0.25 * lw, -0.45);
      const SPOT = new THREE.Vector3(0.2, 0.15, RZ + 0.9);
      const fuseTop = () => bm.position.clone().add(new THREE.Vector3(0, 0.3, 0));
      if (T < 1.13) { if (T > 0.2) { bm.visible = true; bm.position.copy(handE).add(new THREE.Vector3(0, 0.6 * wind, 0.5 * wind)); bm.rotation.set(0, 0, 0); } }
      else if (T < 1.85) {
        const f = (T - 1.13) / 0.72;
        bm.visible = true;
        bm.position.copy(handE.clone().lerp(SPOT, f)); bm.position.y += 2.6 * Math.sin(Math.PI * f);
        bm.rotation.set(f * 9, 0, f * 5);
      } else {
        // quica uma vez e para, o pavio queimando
        const k = T - 1.85;
        bm.visible = T < 3.0;
        bm.position.copy(SPOT); bm.position.y += 0.35 * Math.max(0, Math.sin(Math.PI * Math.min(1, k / 0.25))); bm.position.z += 0.25 * Math.min(1, k / 0.3);
        bm.rotation.set(9 + 0.6 * Math.min(1, k / 0.3), 0, 5);
      }
      if (T > 0.5 && T < 3.0) {
        ctx.ember(fuseTop(), T);
        ctx.get('fagulhas', () => DBFX.grains({
          n: 50, seed: 61, palette: [[255, 220, 120], [255, 160, 60], [255, 250, 200]], size: [0.015, 0.035], drag: 3, grav: 4, life: 0.35, spread: 2.6, add: true,
          cone: Rr => { const a = Rr() * Math.PI * 2, o = 0.4 + Rr() * 1.2; return [Math.cos(a) * o, 0.8 + Rr() * 1.5, Math.sin(a) * o]; },
        })).at(fuseTop(), T - 0.5);
      }
      // o alvo vê o pavio e tenta sair
      if (T > 2.0 && T < 3.0) R.place(0.9 * seq([[2.2, 0], [2.9, 1, 'out']], T), RZ, 0);
      if (T >= 3.0) {
        const age = (T - 3.0) / 1.0;
        const at = SPOT.clone().add(new THREE.Vector3(0, 0.6, 0.25));
        ctx.get('boom', FX.volume).set(at, 1.5, age, { fire: 1, seed: 7.4, dens: 13, rise: 0.6, smoke: [0.16, 0.15, 0.15] });
        ctx.flash(at, Math.max(0, 1 - (T - 3.0) / 0.3));
        ctx.shake(Math.max(0, 0.9 - (T - 3.0) / 0.4));
        ctx.roarRing(new THREE.Vector3(SPOT.x, 0.03, SPOT.z + 0.25), (T - 3.0) / 0.45);
        ctx.get('areia', () => FX.debris(26, 31, [[220, 200, 150], [196, 176, 130], [120, 110, 100], [60, 56, 54]])).at(SPOT.clone(), T - 3.0);
        const kb = seq([[3.0, 0], [3.45, 1, 'out']], T);
        R.place(0.9 + 1.6 * kb, RZ - 0.8 * kb, 0.8 * Math.sin(Math.PI * FX.clamp01((T - 3.0) / 0.45)));
        R.lean(-0.8 * seq([[3.0, 0], [3.3, 1], [3.8, 1], [4.0, 0]], T)); R.spin(0.6 * kb);
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(7), variant(19), variant(46)];
  const def = v => ({ def: rigDef(painters(v), v), low: rigLow(painters(v), v) });
  // garante que as três de exemplo mostrem chapéu, perna de pau e lenço
  fixed[0].hat = true; fixed[0].patch = true; fixed[1].peg = true; fixed[1].hat = false; fixed[2].hat = false; fixed[2].vest = true;
  window.MOBS.push({
    id: 'pirata', nome: 'Pirata', bioma: 'Mar do Sul e Norte', tier: 'Fraco · tier 9',
    variants: fixed.map((v, i) => ({ nome: ['Capitão', 'Perna de Pau', 'Lenço'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['sabre', 'Corte de Sabre'], ['bomba', 'Bomba de Pólvora']],
    cam: 6, lookY: 1.1, lookZ: { sabre: -1.8, bomba: -2.6 }, camFar: { sabre: 7, bomba: 9 }, camYaw: { sabre: -1.2, bomba: -1.25 },
    info: {
      'Tamanho': '1,85 bloco (o chapéu passa um pouco)',
      'Vida': '40 (tier 9)',
      'Jeito': 'Hostil, em grupos de 2 a 3 nas praias e no navio. Grita quando vê alguém e chama os outros.',
      'Corte de Sabre': 'Leva o sabre pro lado, avança um passo largo e corta bem aberto na horizontal: pega quem estiver na frente e dos lados.',
      'Bomba de Pólvora': 'Acende o pavio, joga a bomba alta e ela fica no chão soltando faísca por 1 s antes de explodir (abre um buraco pequeno). Dá pra ver e correr.',
      'Solta': 'Pólvora, peixe; raro: moedas de ouro (pepita de ouro) e o mapa do tesouro (mostra o navio pirata mais perto).',
      'Variações': 'Camisa listrada, lenço ou chapéu, tapa-olho, barba, argola, colete e perna de pau sorteados.',
    },
  });
})();
