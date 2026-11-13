// Soldado da Red Ribbon (Terras do Dragão, Picos, torre do Karin). Médio, tier 8. Farda verde-oliva com bolsos,
// capacete ou boné com o laço vermelho do exército, coturno, fuzil na mão e bazuca nas costas.
// Golpes: Metralhadora (rajada de 14 tiros) e Bazuca (ajoelha, mira com o ponto vermelho e dispara um foguete).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  const UNIFORMS = [[96, 104, 62], [84, 94, 58], [108, 110, 70], [76, 86, 64]];
  const SKINS = [[236, 196, 156], [214, 168, 126], [184, 132, 96], [244, 210, 176], [150, 102, 70]];
  const HAIRS = [[30, 26, 24], [92, 60, 34], [150, 120, 70], [60, 44, 34]];
  function variant(seed) {
    const R = FX.rnd(seed);
    const pick = a => a[Math.floor(R() * a.length)];
    return {
      seed, uni: pick(UNIFORMS), skin: pick(SKINS), hair: pick(HAIRS),
      helmet: R() < 0.5, shades: R() < 0.35, mustache: R() < 0.35, rank: Math.floor(R() * 3), scar: R() < 0.25,
    };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.8, hi = 1.12) => {
      const f = 0.62 - 0.4 * q.ny + 0.35 * (n3(q.ax * 0.25, q.ay * 0.25, q.az * 0.25, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const RED = [206, 30, 36], DRED = [140, 14, 22];
    // o laço da Red Ribbon: dois laços e as pontas caindo, pintado em coordenadas da face (u, v de 0 a 1)
    const ribbon = (u, w) => {
      const x = (u - 0.5) * 2, y = (w - 0.45) * 2;
      const loopL = Math.hypot((x + 0.42) / 0.42, y / 0.34) < 1, loopR = Math.hypot((x - 0.42) / 0.42, y / 0.34) < 1;
      const knot = Math.abs(x) < 0.14 && Math.abs(y) < 0.2;
      const tails = y > 0.1 && y < 0.95 && (Math.abs(x + 0.25 + (y - 0.1) * 0.35) < 0.12 || Math.abs(x - 0.25 - (y - 0.1) * 0.35) < 0.12);
      if (knot) return DRED;
      if (loopL || loopR || tails) return RED;
      return null;
    };
    // o laço do exército em pixel, simétrico (lê igual do lado espelhado), centrado na face
    const BOW4 = ['.#...#.', '###.###', '.##o##.', '..#.#..'], BOW3 = ['##.##', '##o##', '.#.#.'];
    const bow = (q, top, G) => {
      const gx = Math.floor(q.ax + G[0].length / 2), y = Math.floor(q.py) - top;
      if (y < 0 || y >= G.length || gx < 0 || gx >= G[0].length) return null;
      const ch = G[y][gx];
      return ch === '#' ? RED : ch === 'o' ? DRED : null;
    };
    const cloth = (q, s, k = 1) => {
      let c = shade(mul(v.uni, k), q, s, 0.78, 1.12);
      if (n3(q.ax * 0.8, q.ay * 0.8, q.az * 0.8, s + 30 + S) > 0.78) c = mul(c, 0.9);           // manchas de campo
      return c;
    };
    const P = {};
    P.head = q => {
      let c = shade(v.skin, q, 1, 0.88, 1.05);
      const x = Math.floor(q.px), y = Math.floor(q.py);
      if (q.f === 'top') return shade(v.hair, q, 2);
      if (q.f === 'south') return y < 5 ? shade(v.hair, q, 2) : c;
      if (q.f === 'west' || q.f === 'east') {
        if (q.py < 3 || (q.pz > 5 && q.py < 5.4)) return shade(v.hair, q, 2);
        if (q.pz > 3 && q.pz < 4.4 && q.py > 3.2 && q.py < 4.8) return mul(c, 0.84);
        return c;
      }
      if (q.f !== 'north') return c;
      if (y <= 1) return shade(v.hair, q, 2);
      if (y === 2 && (x === 1 || x === 2 || x === 4 || x === 5)) c = mul(v.hair, 0.85);
      if (y === 3) {
        if (v.shades) { if (x >= 1 && x <= 5 && x !== 3) c = [20, 22, 28]; if (x === 3) c = [44, 46, 52]; if (x === 1 || x === 4) c = [70, 90, 110]; }
        else { if (x === 1 || x === 5) c = [236, 234, 228]; if (x === 2 || x === 4) c = [34, 34, 50]; }
      }
      if (y === 4 && x === 3) c = mul(c, 0.86);
      if (v.mustache && y === 5 && x >= 1 && x <= 5) c = mul(v.hair, 0.9);
      if (y === 6 && x >= 2 && x <= 4) c = [130, 72, 60];
      if (v.scar && x === 2 && y >= 4 && y <= 6) c = [170, 100, 86];
      return c;
    };
    P.helmet = q => {
      let c = shade(mul(v.uni, 0.92), q, 3, 0.74, 1.18);
      if (q.f === 'top') c = mul(c, 1.05);
      if (q.f === 'north') { const g = bow(q, 1, BOW4); if (g) c = g; }
      if (q.ny > 0.82 && q.f !== 'top') c = mul(c, 0.8);                                          // aba
      return c;
    };
    P.cap = q => {
      let c = shade(mul(v.uni, 0.95), q, 3, 0.78, 1.14);
      if (q.f === 'north') { const g = bow(q, 0, BOW3); if (g) c = g; }
      return c;
    };
    P.visor = q => (q.f === 'bottom' ? [40, 44, 30] : shade(mul(v.uni, 0.6), q, 4));
    P.jacket = q => {
      let c = cloth(q, 5);
      if (q.f === 'north') {
        if (Math.abs(q.nx - 0.5) < 0.04) c = mul(c, 0.72);                                         // zíper
        // bolsos do peito com lapela
        for (const cx of [0.27, 0.73]) {
          if (Math.abs(q.nx - cx) < 0.17 && q.ny > 0.18 && q.ny < 0.46) { c = cloth(q, 6, 0.93); if (q.ny < 0.25) c = mul(v.uni, 0.78); if (Math.abs(q.nx - cx) < 0.03 && q.ny < 0.28) c = [200, 190, 150]; }
        }
        // o laço no peito esquerdo dele
        const rb = ribbon((q.nx - 0.58) / 0.3, (q.ny - 0.04) / 0.16);
        if (rb && q.nx > 0.58 && q.nx < 0.88 && q.ny < 0.2) c = rb;
      }
      if (q.ny < 0.1 && q.f !== 'bottom') c = mul(c, 0.85);                                          // gola
      return c;
    };
    P.belt = q => {
      if (q.f === 'north' && Math.abs(q.nx - 0.5) < 0.09) return [196, 186, 120];
      return shade([58, 52, 40], q, 7);
    };
    P.pouch = q => { let c = cloth(q, 8, 0.82); if (q.ny < 0.32 && q.f !== 'bottom') c = mul(c, 1.1); if (q.f === 'north' && q.ny > 0.25 && q.ny < 0.38 && Math.abs(q.nx - 0.5) < 0.15) c = [40, 36, 30]; return c; };
    P.pants = q => {
      let c = cloth(q, 9, 0.94);
      if ((q.f === 'west' || q.f === 'east') && q.ny > 0.35 && q.ny < 0.7 && Math.abs(q.nz - 0.5) < 0.3) { c = cloth(q, 10, 0.88); if (q.ny < 0.42) c = mul(v.uni, 0.75); }   // bolso da perna
      return c;
    };
    P.sleeve = q => {
      let c = cloth(q, 11);
      if (q.f === 'west' || q.f === 'east') {
        // divisas da patente
        for (let i = 0; i < v.rank; i++) if (Math.abs(q.ny - (0.32 + i * 0.12)) < 0.04 && Math.abs(q.nz - 0.5) < 0.35 - Math.abs(q.ny - 0.4) * 0.3) c = [216, 190, 90];
        if (v.rank === 0 && q.ny > 0.18 && q.ny < 0.4) { const rb = ribbon(q.nz, (q.ny - 0.18) / 0.22); if (rb) c = rb; }
      }
      return c;
    };
    P.glove = q => shade([44, 40, 34], q, 12, 0.8, 1.2);
    P.boot = q => {
      if (q.f === 'bottom') return [24, 22, 20];
      let c = shade([40, 36, 32], q, 13, 0.8, 1.3);
      if (q.ny < 0.2) c = cloth(q, 14, 0.85);
      if (q.f === 'north' && q.ny > 0.25 && q.ny < 0.7 && fr(q.ay * 1.6) < 0.25 && Math.abs(q.nx - 0.5) < 0.25) c = [120, 110, 90];   // cadarço
      return c;
    };
    P.lowTorso = q => q.ay > 11.4 ? P.pants(q) : q.ay > 10.2 ? P.belt(q) : P.jacket({ ...q, ny: (q.ay - 2) / 8.2 });
    P.lowArm = q => q.ay > 10 ? P.glove(q) : P.sleeve({ ...q, ny: (q.ay - 1) / 9 });
    P.lowLeg = q => q.ay < 19.6 ? P.pants({ ...q, ny: (q.ay - 13) / 11 }) : P.boot({ ...q, ny: (q.ay - 19.6) / 4.4 });
    return P;
  }

  // ---------- armas ----------
  function rifleDef() {
    const metal = (q, s) => { let c = mix([40, 42, 46], [80, 84, 90], n3(q.ax * 1.5, q.ay * 1.5, q.az * 1.5, s)); if (q.f === 'top') c = mul(c, 1.25); return c; };
    const wood = q => { let c = cel([118, 74, 40], n3(q.ax * 0.4, q.ay * 2, q.az * 0.4, 70), 0.8, 1.12); if (fr(q.az * 0.5 + q.ay * 0.2) < 0.15) c = mul(c, 0.82); return c; };
    return {
      name: 'arma', pivot: [0, 0, 0], boxes: [
        { x: -0.7, y: -0.9, z: -4, w: 1.4, h: 1.8, d: 7, p: q => metal(q, 71) },                    // corpo
        { x: -0.45, y: -0.6, z: -9.5, w: 0.9, h: 0.9, d: 5.6, p: q => metal(q, 72) },              // cano
        { x: -0.75, y: -1.0, z: -7.2, w: 1.5, h: 1.6, d: 3.2, p: wood },                            // guarda-mão
        { x: -0.5, y: 0.8, z: -2.6, w: 1, h: 3.2, d: 1.4, p: q => metal(q, 73) },                  // carregador
        { x: -0.45, y: 0.8, z: 1.2, w: 0.9, h: 2, d: 1.1, p: wood },                                 // punho
        { x: -0.6, y: -0.6, z: 3, w: 1.2, h: 2.2, d: 4, p: wood },                                   // coronha
        { x: -0.25, y: -1.6, z: -8.8, w: 0.5, h: 0.8, d: 0.5, p: q => metal(q, 74) },              // massa de mira
      ],
    };
  }
  function bazookaDef() {
    const tube = q => {
      let c = cel([92, 100, 64], n3(q.ax * 0.4, q.ay * 0.4, q.az * 0.4, 80), 0.78, 1.14);
      if (fr(q.az * 0.18) < 0.06) c = mul(c, 0.7);                                                   // anéis
      if (q.f === 'north' || q.f === 'south') c = mul(c, 0.7);
      return c;
    };
    return {
      name: 'bazuca', pivot: [0, 0, 0], boxes: [
        { x: -1.4, y: -1.4, z: -9, w: 2.8, h: 2.8, d: 17, p: tube },
        { x: -1.8, y: -1.8, z: -10, w: 3.6, h: 3.6, d: 1.6, p: q => mul(tube(q), 0.8) },          // boca
        { x: -1.7, y: -1.7, z: 7, w: 3.4, h: 3.4, d: 1.4, p: q => mul(tube(q), 0.8) },             // traseira
        { x: -0.5, y: 1.3, z: -2, w: 1, h: 2.4, d: 1.2, p: () => [52, 48, 40] },                    // gatilho
        { x: 1.3, y: -2.4, z: -3, w: 1.2, h: 1.6, d: 2.4, p: () => [60, 62, 50] },                  // mira
      ],
    };
  }

  function rigDef(P, v, lowArms) {
    const arm = s => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * 5.5, -8, 0], boxes: [{ x: -1.5, y: -1, z: -1.5, w: 3, h: 5.2, d: 3, p: P.sleeve }],
      kids: [{
        name: s < 0 ? 'anteD' : 'anteE', pivot: [0, 4.2, 0], boxes: [{ x: -1.45, y: 0, z: -1.45, w: 2.9, h: 4, d: 2.9, p: P.sleeve }],
        kids: [{ name: s < 0 ? 'maoD' : 'maoE', pivot: [0, 4, 0], boxes: [{ x: -1.3, y: 0, z: -1.3, w: 2.6, h: 2.3, d: 2.6, p: P.glove }] }],
      }],
    });
    const leg = s => ({
      name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * 2, 13, 0], boxes: [
        { x: -2, y: 0, z: -2, w: 4, h: 6.2, d: 4, p: P.pants },
        { x: s * 2.05 - (s < 0 ? 0.6 : 0), y: 2.2, z: -1.1, w: 0.6, h: 2.6, d: 2.2, p: P.pouch },
      ],
      kids: [{
        name: s < 0 ? 'canelaD' : 'canelaE', pivot: [0, 6, 0], boxes: [{ x: -1.95, y: 0, z: -1.95, w: 3.9, h: 2.6, d: 3.9, p: P.pants }],
        kids: [{ name: s < 0 ? 'peD' : 'peE', pivot: [0, 2.6, 0], boxes: [{ x: -2.1, y: -1.8, z: -2.2, w: 4.2, h: 4.2, d: 4.4, p: P.boot }, { x: -2, y: 1.4, z: -3.2, w: 4, h: 1, d: 1.2, p: P.boot }] }],
      }],
    });
    const head = v.helmet ? [
      { x: -4.1, y: -9.0, z: -4.1, w: 8.2, h: 4.4, d: 8.2, p: P.helmet },
      { x: -3.4, y: -9.8, z: -3.4, w: 6.8, h: 1, d: 6.8, p: P.helmet },
    ] : [
      { x: -3.8, y: -8.4, z: -3.8, w: 7.6, h: 2.8, d: 7.6, p: P.cap },
      { x: -3.4, y: -6.0, z: -6.2, w: 6.8, h: 0.6, d: 2.6, p: P.visor },
    ];
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'quadril', pivot: [0, 13, 0], boxes: [
            { x: -4, y: -2, z: -2, w: 8, h: 2, d: 4, p: P.pants },
            { x: -4.3, y: -2.8, z: -2.3, w: 8.6, h: 1.2, d: 4.6, p: P.belt },
            { x: -4.0, y: -3.2, z: -2.9, w: 2.2, h: 2, d: 0.9, p: P.pouch },
            { x: 1.8, y: -3.2, z: -2.9, w: 2.2, h: 2, d: 0.9, p: P.pouch },
            { x: -1.4, y: -3.2, z: 2.1, w: 2.8, h: 2.2, d: 1.2, p: P.pouch },
          ], kids: [{
            name: 'tronco', pivot: [0, -2, 0], boxes: [
              { x: -4, y: -9, z: -2, w: 8, h: 9, d: 4, p: P.jacket },
            ], kids: [
              { name: 'cabeca', pivot: [0, -9.2, 0], boxes: [{ x: -3.5, y: -7, z: -3.5, w: 7, h: 7, d: 7, p: P.head }, ...head] },
              arm(-1), arm(1),
              { name: 'arma', pivot: [-1.6, -4.2, -4.4], boxes: [], kids: [rifleDef()] },
              { name: 'costas', pivot: [0.5, -5, 3.6], rot: [0, 0, 0.3], boxes: [], kids: [{ ...bazookaDef(), rot: [Math.PI / 2, 0, 0] }] },
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
          name: 'quadril', pivot: [0, 13, 0], boxes: [{ x: -4, y: -11, z: -2, w: 8, h: 11, d: 4, p: P.lowTorso }], kids: [
            {
              name: 'cabeca', pivot: [0, -11.2, 0], boxes: [
                { x: -3.5, y: -7, z: -3.5, w: 7, h: 7, d: 7, p: P.head },
                v.helmet ? { x: -4.1, y: -9.0, z: -4.1, w: 8.2, h: 4.4, d: 8.2, p: P.helmet } : { x: -3.8, y: -8.4, z: -3.8, w: 7.6, h: 2.8, d: 7.6, p: P.cap },
              ],
            },
            { name: 'bracoD', pivot: [-5.5, -10, 0], boxes: [{ x: -1.5, y: -1, z: -1.5, w: 3, h: 11.5, d: 3, p: P.lowArm }] },
            { name: 'bracoE', pivot: [5.5, -10, 0], boxes: [{ x: -1.5, y: -1, z: -1.5, w: 3, h: 11.5, d: 3, p: P.lowArm }] },
            { name: 'arma', pivot: [-1.6, -6.2, -4.4], boxes: [], kids: [{ name: 'armaLow', pivot: [0, 0, 0], boxes: [{ x: -0.7, y: -0.9, z: -9, w: 1.4, h: 2, d: 14, p: () => [50, 50, 54] }] }] },
            { name: 'costas', pivot: [0.5, -7, 3.6], rot: [0, 0, 0.3], boxes: [], kids: [{ name: 'bazucaLow', pivot: [0, 0, 0], rot: [Math.PI / 2, 0, 0], boxes: [{ x: -1.4, y: -1.4, z: -9, w: 2.8, h: 2.8, d: 17, p: () => [86, 94, 60] }] }] },
          ],
        },
        { name: 'pernaD', pivot: [-2, 13, 0], boxes: [{ x: -2, y: 0, z: -2, w: 4, h: 11, d: 4, p: P.lowLeg }] },
        { name: 'pernaE', pivot: [2, 13, 0], boxes: [{ x: -2, y: 0, z: -2, w: 4, h: 11, d: 4, p: P.lowLeg }] },
      ],
    };
  }

  // ---------- poses ----------
  // segurando o fuzil na diagonal, cano pra baixo e à frente (pronto, sem mirar)
  function ready(r, P) {
    r('quadril', 0.04, 0, 0); P.quadril.obj.position.y += 0.3;
    r('pernaD', -0.12, 0.06, 0); r('canelaD', 0.2, 0, 0); r('pernaE', 0.06, -0.06, 0); r('canelaE', 0.12, 0, 0);
    r('bracoD', -0.35, -0.1, 0.15); r('anteD', -0.75, 0, 0);
    r('bracoE', -0.75, 0.6, -0.05); r('anteE', -0.45, 0, 0);
    r('arma', 0.35, 0.5, 0.2); P.arma.obj.position.y -= 1.2;
  }
  // fuzil no ombro, olho na mira
  function aim(r, P, k) {
    r('bracoD', -0.95 * k, -0.35 * k, 0.25 * k); r('anteD', -0.75 * k, 0, 0);
    r('bracoE', -0.75 * k, 0.15 * k, -0.15 * k); r('anteE', -0.45 * k, 0, 0);
    r('arma', -0.35 * k, -0.5 * k, -0.2 * k); P.arma.obj.position.y -= 3.2 * k; P.arma.obj.position.x += 0.4 * k;
    r('cabeca', 0.08 * k, -0.12 * k, 0.12 * k);
    r('tronco', -0.04 * k, 0.12 * k, 0); r('quadril', 0, -0.12 * k, 0);
    r('pernaD', -0.2 * k, 0, 0); r('canelaD', 0.25 * k, 0, 0); r('pernaE', 0.2 * k, 0, 0);
  }
  const WALK_T = [[0, -0.4], [0.14, -0.26], [0.36, 0.1], [0.52, 0.4], [0.64, 0.2], [0.82, -0.28]];
  const WALK_K = [[0, 0.06], [0.14, 0.14], [0.4, 0.06], [0.56, 0.34], [0.7, 0.74], [0.86, 0.24]];
  const WALK_B = [[0, 0.1], [0.14, 0.4], [0.36, -0.12], [0.5, 0.1], [0.64, 0.4], [0.86, -0.12]];
  // onde cada tiro da rajada cai em volta do alvo (uns acertam, outros levantam terra), sorteado uma vez
  const SHOTS = (() => { const R = FX.rnd(91); return Array.from({ length: 14 }, (_, i) => ({ dx: (R() - 0.5) * 1.1, dy: 0.5 + R() * 1.2, hit: R() < 0.55 && i > 1, gz: (R() - 0.5) * 1.2 })); })();

  function rocketModel() {
    const m = ENG.build({
      name: 'foguete', pivot: [0, 0, 0], boxes: [
        { x: -1, y: -1, z: -3, w: 2, h: 2, d: 6, p: q => cel([80, 88, 60], n3(q.ax, q.ay, q.az, 90), 0.8, 1.15) },
        { x: -0.8, y: -0.8, z: -4.6, w: 1.6, h: 1.6, d: 1.6, p: () => [180, 40, 34] },
        { x: -1.8, y: -0.2, z: 2, w: 3.6, h: 0.4, d: 1.4, p: () => [60, 64, 46] },
        { x: -0.2, y: -1.8, z: 2, w: 0.4, h: 3.6, d: 1.4, p: () => [60, 64, 46] },
      ],
    });
    return ENG.toThree(m, ENG.paint(m, m.W, m.H));
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const breath = loop([[0, 0], [0.42, 1], [0.999, 0]], t / 3.8);

    if (mode === 'parado') {
      ready(r, P);
      r('tronco', -0.02 * breath, 0, 0);
      // olha em volta como quem vigia: esquerda, segura, direita, segura
      const look = loop([[0, 0], [0.12, 0.5], [0.4, 0.5], [0.52, -0.45], [0.8, -0.45], [0.92, 0]], t / 6);
      r('cabeca', 0, look, 0);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 0.95;
      ready(r, P);
      for (const [s, off] of [['D', 0], ['E', 0.5]]) {
        r('perna' + s, loop(WALK_T, ph + off), 0, 0);
        r('canela' + s, loop(WALK_K, ph + off), 0, 0);
        r('pe' + s, -0.25 * loop(WALK_K, ph + off - 0.06), 0, 0);
      }
      P.quadril.obj.position.y += loop(WALK_B, ph);
      const tw = loop([[0, 0.05], [0.5, -0.05]], ph - 0.03);
      r('quadril', 0.04, tw, 0); r('tronco', 0.02, -tw * 0.7, 0);
      r('arma', 0.04 * loop([[0, 1], [0.5, -1]], ph * 2), 0, 0);
      return;
    }

    if (mode === 'metralhadora') {
      const T = t % 3.4;
      const R = ctx.ref; const RZ = -6.5; R.place(0, RZ, 0);
      ready(r, P);
      const up = seq([[0, 0], [0.38, 1, 'out'], [2.0, 1], [2.45, 0]], T);
      aim(r, P, up);
      const muzzle = new THREE.Vector3(-0.2, 1.42, -1.25);
      let shotK = 0;
      SHOTS.forEach((sh, i) => {
        const st = 0.45 + i * 0.1, age = T - st;
        if (age < 0) return;
        if (age < 0.06) shotK = 1 - age / 0.06;
        const end = sh.hit ? new THREE.Vector3(sh.dx * 0.4, 0.9 + sh.dy * 0.4, RZ + 0.1) : new THREE.Vector3(sh.dx * 1.6, 0.03, RZ + 0.6 + sh.gz);
        if (age < 0.1) ctx.get('tiro' + (i % 4), () => DBFX.tracer([1.0, 0.82, 0.4])).at(muzzle, end, age / 0.07, 1.4);
        if (age < 0.05) ctx.get('boca' + (i % 2), DBFX.muzzle).at(muzzle.clone().add(new THREE.Vector3(0, 0, -0.08)), ctx.camera, 1 - age / 0.05, i * 3.7, 0.55);
        // terra levantando onde erra, faísca onde acerta
        if (!sh.hit && age > 0.06 && age < 0.7) ctx.get('terra' + (i % 5), () => DBFX.grains({
          n: 18, seed: 100 + i, palette: [[150, 92, 64], [120, 74, 50], [176, 120, 84]], size: [0.025, 0.06], drag: 2, grav: 9.8, life: 0.6,
          cone: Rr => { const a = Rr() * Math.PI * 2, o = Rr() * 0.8; return [Math.cos(a) * o, 1.5 + Rr() * 2.2, Math.sin(a) * o]; },
        })).at(end, age - 0.06, 0.02);
        if (sh.hit && age > 0.06 && age < 0.3) ctx.get('faisca' + (i % 3), () => DBFX.grains({
          n: 10, seed: 200 + i, palette: [[255, 220, 140], [255, 180, 80]], size: [0.02, 0.035], drag: 4, grav: 6, life: 0.25, add: true,
          cone: Rr => [(Rr() - 0.5) * 3, Rr() * 2, 1 + Rr() * 2],
        })).at(end, age - 0.06, 0);
        // cápsula voando pro lado direito dele
        if (age > 0 && age < 0.9) {
          const cs = ctx.get('capsula' + (i % 6), () => { const m = new THREE.Mesh(new THREE.BoxGeometry(0.03, 0.03, 0.07), new THREE.MeshLambertMaterial({ color: 0xd8a640, emissive: 0x2a1800 })); return m; });
          cs.visible = true;
          const k = Math.min(age, 0.5), side = 1.6 + 0.4 * Math.sin(i * 1.7);
          cs.position.set(muzzle.x - 0.2 - side * k, Math.max(0.02, 1.32 + 2.2 * k - 9.8 * k * k / 2 * 1.4), muzzle.z + 0.9 + 0.3 * Math.sin(i * 2.9) * k);
          cs.rotation.set(age * 20, age * 13, i);
        }
        if (sh.hit && age < 0.12) { R.lean(-0.12 * (1 - age / 0.12)); R.spin(0.1 * Math.sign(sh.dx) * (1 - age / 0.12)); }
      });
      // coice de cada tiro
      r('arma', -0.12 * shotK, 0, 0); P.arma.obj.position.z += 0.8 * shotK; r('tronco', -0.04 * shotK, 0, 0); r('bracoD', 0.08 * shotK, 0, 0);
      if (shotK > 0) ctx.flash(muzzle.clone().add(new THREE.Vector3(0, 0, -0.6)), 0.12 * shotK);
      ctx.shake(T > 0.45 && T < 1.85 ? 0.12 : 0);
      // fumacinha do cano no fim
      if (T > 1.8 && T < 3.2) ctx.get('fumacaCano', FX.volume).set(muzzle.clone().add(new THREE.Vector3(0, 0.15, 0)), 0.22, (T - 1.8) / 1.4, { seed: 5.5, dens: 4, rise: 1.6, squash: 2, smoke: [0.7, 0.7, 0.72] });
      // o alvo se encolhe na rajada
      const flinch = seq([[0.6, 0], [0.8, 1], [1.9, 1], [2.3, 0]], T);
      R.armsOut(-0.2 * flinch); R.lean(-0.15 * flinch);
      R.place(0, RZ - 0.6 * seq([[0.6, 0], [1.9, 1]], T), 0);
      return;
    }

    if (mode === 'bazuca') {
      const T = t % 4.4;
      const R = ctx.ref; const RZ = -8; R.place(0, RZ, 0);
      ready(r, P);
      // pega a bazuca das costas e leva ao ombro; o fuzil vai pra mão esquerda, baixo
      const grab = seq([[0, 0], [0.5, 1, 'io']], T) * (1 - seq([[2.6, 0], [3.2, 1, 'io']], T));
      const kneel = seq([[0.45, 0], [0.7, 1, 'out'], [2.4, 1], [2.8, 0]], T);
      r('bracoD', -0.6 * grab, 0.2 * grab, -0.1 * grab); r('anteD', 0.3 * grab, 0, 0);
      r('arma', 0.6 * grab, 0, 0); P.arma.obj.position.y += 2.5 * grab; P.arma.obj.position.x += 5 * grab;
      const B = P.costas.obj, br = P.costas.rest;
      // da diagonal nas costas pro ombro direito, apontando pra frente
      B.position.set(br.pos[0] + (-3.6 - br.pos[0]) * grab, br.pos[1] + (-10.4 - br.pos[1]) * grab, br.pos[2] + (0.5 - br.pos[2]) * grab);
      B.rotation.set(-Math.PI / 2 * grab, 0, br.rot[2] * (1 - grab));
      // ajoelha: joelho esquerdo no chão
      P.quadril.obj.position.y += 5.2 * kneel;
      r('pernaE', 1.0 * kneel, 0, 0); r('canelaE', 0.55 * kneel, 0, 0); r('peE', -1.1 * kneel, 0, 0);
      r('pernaD', -1.45 * kneel, 0, 0); r('canelaD', 1.45 * kneel, 0, 0);
      r('bracoD', -0.9 * kneel, -0.2, 0.4 * kneel); r('anteD', -1.2 * kneel, 0, 0);
      r('bracoE', -0.5 * kneel, 0, -0.1); r('cabeca', 0.1 * kneel, -0.1 * kneel, 0.15 * kneel);
      // ponto vermelho da mira no peito do alvo (aviso: dá pra sair da linha)
      const dot = new THREE.Vector3(0.05, 1.2, RZ + 0.14);
      if (T > 0.75 && T < 1.32) {
        const blink = 0.6 + 0.4 * Math.sin(T * 40);
        ctx.get('mira', () => DBFX.glow([1.0, 0.1, 0.08])).at(dot, 0.05, blink * 2, T);
        ctx.get('feixe', () => DBFX.tracer([1.0, 0.15, 0.1])).at(new THREE.Vector3(-0.2, 1.05, -1.0), dot, 1, 99);
      }
      // disparo: fogo atrás, foguete voando com rastro, explosão no alvo
      const fire = 1.32, hit = fire + 0.4;
      const mouth = new THREE.Vector3(-0.22, 1.05, -1.25);
      const kick = seq([[fire, 0], [fire + 0.04, 1, 'snap'], [fire + 0.4, 0]], T);
      r('tronco', -0.15 * kick, 0, 0); B.position.z += 1.5 * kick;
      if (T >= fire && T < fire + 0.9) {
        ctx.get('tras', FX.volume).set(new THREE.Vector3(-0.25, 1.1, 1.3), 0.7, (T - fire) / 0.9, { fire: 0.7, seed: 8.8, dens: 9, rise: 0.4, smoke: [0.55, 0.55, 0.55] });
        ctx.get('bocaBazuca', DBFX.muzzle).at(mouth, ctx.camera, Math.max(0, 1 - (T - fire) / 0.08), 5, 1.0);
      }
      const rk = ctx.get('foguete', () => { const g = new THREE.Group(); const m = rocketModel(); g.add(m); m.position.y = -1.5; return g; });
      const path = f => mouth.clone().lerp(dot, f);
      if (T >= fire && T < hit) {
        const f = (T - fire) / (hit - fire);
        rk.visible = true; rk.position.copy(path(f)); rk.rotation.set(0, 0, T * 30);
        ctx.get('chamaFoguete', () => DBFX.glow([1.0, 0.6, 0.2])).at(path(f).add(new THREE.Vector3(0, 0, 0.3)), 0.12, 1.2, T, 1);
      }
      // rastro de fumaça: baforadas soltas no caminho, cada uma nasce quando o foguete passa e se desfaz no seu tempo
      const NT = 14;
      for (let i = 0; i < NT; i++) {
        const f0 = (i + 0.5) / NT, born = fire + f0 * (hit - fire), life = 1.3 + 0.5 * fr(i * 0.618), ag = (T - born) / life;
        // cada baforada com tamanho, desvio e semente próprios; juntas formam um rastro só que engrossa e se desfaz
        const off = new THREE.Vector3((n3(i, 0, 0, 31) - 0.5) * 0.12, 0.08 * ag + (n3(0, i, 0, 32) - 0.5) * 0.1, 0);
        if (ag > 0 && ag < 1) ctx.get('rastro' + i, FX.volume).set(path(f0).add(off), 0.2 + 0.12 * f0 + 0.12 * fr(i * 0.37), ag, { seed: 30 + i * 2.7, dens: 3.5, rise: 0.25, squash: 0.8, smoke: [0.72, 0.71, 0.7] });
      }
      if (T >= hit) {
        const age = (T - hit) / 1.5;
        const at = new THREE.Vector3(0, 1.0, RZ);
        ctx.get('boom', FX.volume).set(at, 1.9, age, { fire: 1, seed: 12.3, dens: 14, rise: 0.55, smoke: [0.17, 0.15, 0.14] });
        ctx.flash(at, Math.max(0, 1 - (T - hit) / 0.3));
        ctx.shake(Math.max(0, 1 - (T - hit) / 0.5));
        ctx.roarRing(new THREE.Vector3(0, 0.03, RZ), (T - hit) / 0.55);
        ctx.get('detritos', () => FX.debris(30, 23, [[150, 92, 64], [120, 74, 50], [96, 60, 40], [60, 52, 46]])).at(new THREE.Vector3(0, 0.1, RZ), T - hit);
        const kb = seq([[hit, 0], [hit + 0.55, 1, 'out']], T), upk = Math.sin(Math.PI * FX.clamp01((T - hit) / 0.55));
        R.place(0, RZ - 2.8 * kb, 1.3 * upk); R.lean(-1.4 * seq([[hit, 0], [hit + 0.5, 1], [4.1, 1], [4.4, 0]], T));
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(5), variant(12), variant(40)];
  const def = v => ({ def: rigDef(painters(v), v), low: rigLow(painters(v), v) });
  window.MOBS.push({
    id: 'soldado_red_ribbon', nome: 'Soldado da Red Ribbon', bioma: 'Terras e Deserto', tier: 'Médio · tier 8',
    variants: fixed.map((v, i) => ({ nome: ['Capacete', 'Boné', 'Óculos'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['metralhadora', 'Metralhadora'], ['bazuca', 'Bazuca']],
    cam: 6, lookY: 1.1, lookZ: { metralhadora: -2.8, bazuca: -3.6 }, camFar: { metralhadora: 9, bazuca: 11 }, camYaw: { metralhadora: -1.25, bazuca: -1.3 },
    info: {
      'Tamanho': '1,85 bloco, do tamanho do jogador',
      'Vida': '60 (tier 8)',
      'Jeito': 'Hostil, em patrulhas de 2 a 3. Mantém 6 a 9 blocos de distância e se protege atrás dos colegas.',
      'Metralhadora': 'Leva o fuzil ao ombro e solta 14 tiros, 2 ticks entre eles. Cada bala para no primeiro bloco ou corpo. O dano dos tiros soma em blocos de 4 (o Escudo de tier não engole tiro por tiro).',
      'Bazuca': 'Tira a bazuca das costas, ajoelha e mira: o ponto vermelho aparece no seu peito por meio segundo (dá pra sair da linha). O foguete voa reto e explode no primeiro bloco ou corpo, abre um buraco e arremessa.',
      'Solta': 'Munição (pólvora), ração do exército (comida); raro: Radar do Dragão quebrado (conserta na Capsule Corp).',
      'Variações': 'Capacete ou boné, farda, pele, óculos escuros, bigode, cicatriz e patente sorteados.',
    },
  });
})();
