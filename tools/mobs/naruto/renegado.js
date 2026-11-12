// Ninja Renegado (Floresta da Folha). Fraco, tier 9. Bandana riscada, colete rasgado, máscara de pano.
// Golpes: Substituição (vira tronco quando apanha e aparece atrás) e Kunai com papel-bomba.
'use strict';
(() => {
  const { n3, fbm, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  // ---------- variantes: cada renegado é um ----------
  const HAIRS = [[30, 28, 34], [74, 46, 30], [120, 118, 124], [96, 28, 26], [44, 40, 58]];
  const SCARVES = [[128, 42, 34], [70, 72, 80], [40, 52, 86], [96, 84, 52], null];
  const VESTS = [[74, 82, 60], [64, 66, 72], [86, 74, 58], [58, 70, 74]];
  const SKINS = [[236, 196, 156], [214, 168, 126], [190, 140, 102], [244, 210, 176]];
  function variant(seed) {
    const R = FX.rnd(seed);
    const pick = a => a[Math.floor(R() * a.length)];
    return { seed, hair: pick(HAIRS), scarf: pick(SCARVES), vest: pick(VESTS), skin: pick(SKINS), mask: R() < 0.6, scar: R() < 0.5 };
  }

  function painters(v) {
    const S = v.seed;
    const ao = (c, q, k = 0.16) => mul(c, 1 - k * q.ny);
    const shade = (base, q, s, lo = 0.8, hi = 1.12) => {
      const f = 0.62 - 0.4 * q.ny + 0.35 * (n3(q.ax * 0.25, q.ay * 0.25, q.az * 0.25, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const UNDER = [26, 26, 32];
    const P = {};
    P.skin = q => shade(v.skin, q, 1, 0.86, 1.06);
    P.head = q => {
      let c = shade(v.skin, q, 1, 0.88, 1.05);
      const x = Math.floor(q.px), y = Math.floor(q.py);
      const cloth = () => { let k = cel([58, 60, 70], n3(q.ax * 0.6, q.ay * 0.9, q.az * 0.6, 40 + S), 0.8, 1.12); if (fr(q.ay * 0.9 + q.ax * 0.2) < 0.18) k = mul(k, 0.78); return k; };
      if (q.f === 'south') return q.py < 5.6 ? shade(v.hair, q, 2) : (v.mask ? cloth() : c);
      if (q.f === 'top') return shade(v.hair, q, 2);
      if (q.f === 'west' || q.f === 'east') {
        if (q.py < 3.2 || (q.pz > 4.6 && q.py < 5.6)) return shade(v.hair, q, 2);           // costeleta e nuca
        if (q.pz > 3.0 && q.pz < 4.4 && q.py > 3.4 && q.py < 5.0) return mul(c, 0.84);       // orelha
        if (v.mask && q.py >= 4.8) return cloth();
        return c;
      }
      if (q.f !== 'north') return c;
      if (y <= 1) return shade(v.hair, q, 2);                                                // franja
      if (y === 3 && (x === 1 || x === 2 || x === 4 || x === 5)) c = x === 2 || x === 4 ? [34, 22, 18] : mul(v.hair, 0.8); // sobrancelha baixando pro meio
      if (y === 4) {
        if (x === 1 || x === 5) c = [236, 234, 228];
        if (x === 2 || x === 4) c = [28, 24, 30];                                              // olhar fixo pra frente, sem brilho bobo
        if (x === 3) c = mul(c, 0.9);
      }
      if (v.mask && q.py >= 4.8) return cloth();
      if (!v.mask) {
        if (y === 5 && x === 3) c = mul(c, 0.86);
        if (y === 6 && x >= 2 && x <= 4) c = [118, 62, 52];
      }
      if (v.scar && ((x === 5 && (y === 5 || y === 2)) || (x === 4 && y === 6))) c = [176, 92, 84];
      return c;
    };
    P.hairTop = q => {
      const k = Math.floor(q.px * 0.9) * 7 + Math.floor(q.pz * 0.9);
      const hgt = 0.32 + 0.68 * n3(k * 1.31, S * 0.1, 0.5, 41) * (q.f === 'north' ? 0.7 : 1);
      if (1 - q.ny > hgt + 0.05 && q.f !== 'bottom') return null;
      return cel(v.hair, n3(q.ax * 0.9, q.ay * 0.5, q.az * 0.9, 42 + S), 0.74, 1.22);
    };
    P.hairBack = q => {
      if (q.ny > 0.5 + 0.5 * n3(Math.floor(q.px) * 1.7, S, 0, 43)) return null;
      return cel(v.hair, n3(q.ax * 0.9, q.ay * 0.4, q.az * 0.9, 44 + S), 0.76, 1.18);
    };
    P.band = q => {
      let c = cel([38, 40, 52], n3(q.ax * 0.5, q.ay * 0.8, q.az * 0.5, 45 + S), 0.78, 1.18);
      if (q.f === 'bottom') c = mul(c, 0.7);
      return c;
    };
    P.plate = q => {
      const metal = mix([150, 156, 170], [214, 220, 230], 0.4 + 0.6 * n3(q.ax * 0.8, q.ay * 0.8, q.az * 0.8, 46));
      if (q.f !== 'north') return mul(metal, 0.82);
      if (q.nx < 0.07 || q.nx > 0.93 || q.ny < 0.1 || q.ny > 0.9) return mul(metal, 0.72);    // rebite da borda
      const dx = (q.nx - 0.5) * 2, dy = (q.ny - 0.52) * 1.05;
      const ang = Math.atan2(dy, dx), rr = Math.hypot(dx, dy);
      let c = metal;
      if (Math.abs(rr - (0.18 + 0.08 * fr(ang / (Math.PI * 2) + 0.25))) < 0.07 && rr < 0.42) c = mul(metal, 0.62); // emblema gravado
      // o risco do desertor atravessa o emblema: fundo escuro com a borda raspada clara
      const sc = q.ny - (0.85 - q.nx * 0.7);
      if (Math.abs(sc) < 0.08) c = [62, 62, 72];
      else if (sc > 0.08 && sc < 0.14) c = [244, 246, 250];
      return c;
    };
    P.torso = q => {
      if ((q.f === 'west' || q.f === 'east') && q.ny < 0.38) return shade(UNDER, q, 3);       // cava do colete
      let c = shade(v.vest, q, 4, 0.78, 1.14);
      if (q.f === 'north' && Math.abs(q.nx - 0.5) < 0.05) c = mul(c, 0.7);                     // fecho
      if (q.f === 'north' && q.ny < 0.12) c = mul(c, 0.86);
      // barra rasgada: o colete foi cortado e mostra a camisa preta por baixo
      const rip = 0.84 + 0.16 * n3(q.ax * 0.9, 0, q.az * 0.9, 47 + S);
      if (q.ny > rip && q.f !== 'top') c = shade(UNDER, q, 3);
      if (q.f === 'south' && Math.abs(q.nx - 0.5) < 0.04 && q.ny < 0.8) c = mul(c, 0.8);       // costura das costas
      return ao(c, q, 0.1);
    };
    P.pouchV = q => {
      let c = shade(mul(v.vest, 0.92), q, 5);
      if (q.ny < 0.38 && q.f !== 'bottom') c = mul(shade(v.vest, q, 5), 1.08);
      if (q.f === 'north' && q.ny > 0.28 && q.ny < 0.46 && Math.abs(q.nx - 0.5) < 0.14) c = [196, 190, 170];
      return c;
    };
    P.pants = q => {
      let c = shade([36, 40, 60], q, 6, 0.8, 1.16);
      if (Math.sin(q.ay * 1.3 + n3(q.ax * 0.3, q.ay * 0.2, q.az * 0.3, 48) * 5) > 0.9) c = mul(c, 0.78);
      return ao(c, q, 0.12);
    };
    P.belt = q => {
      if (q.f === 'north' && Math.abs(q.nx - 0.5) < 0.08) return [176, 168, 150];
      return mul([70, 52, 36], 0.85 + 0.3 * n3(q.ax, q.ay, q.az, 49));
    };
    P.pouch = q => {
      let c = cel([128, 92, 58], n3(q.ax * 0.6, q.ay * 0.6, q.az * 0.6, 50), 0.8, 1.14);
      if (q.ny < 0.35 && q.f !== 'bottom') c = mul(c, 1.12);
      if (q.f === 'south' && q.ny > 0.3 && q.ny < 0.45 && Math.abs(q.nx - 0.5) < 0.12) c = [70, 50, 34];
      return c;
    };
    P.collar = q => shade(UNDER, q, 7);
    P.scarf = q => {
      if (!v.scarf) return shade(UNDER, q, 7);
      let c = cel(v.scarf, n3(q.ax * 0.5, q.ay * 0.5, q.az * 0.5, 51 + S), 0.78, 1.16);
      if (fr(q.ax * 0.35 + q.ay * 0.55 + q.az * 0.2) < 0.2) c = mul(c, 0.8);                 // voltas do pano
      return c;
    };
    P.scarfTail = q => {
      if (!v.scarf) return null;
      const k = Math.floor(q.px * 1.6);
      if (q.ny > 0.82 + 0.18 * n3(k * 2.1, S, 1, 52)) return null;                            // franja rasgada, cada fio de um tamanho
      let c = cel(v.scarf, n3(q.ax * 0.4, q.ay * 0.3, q.az * 0.4, 53 + S), 0.78, 1.14);
      return ao(c, q, 0.2);
    };
    P.sleeve = q => shade(UNDER, q, 8);
    P.upperArm = q => (q.ny < 0.42 ? shade(UNDER, q, 8) : shade(v.skin, q, 1, 0.86, 1.05));
    P.wrapArm = q => {
      const w = fr(q.ay * 0.7 + q.ax * 0.18 + n3(q.ax * 0.5, q.ay * 0.2, q.az * 0.5, 54) * 0.7);
      let c = w < 0.18 ? [150, 150, 158] : w < 0.3 ? [196, 196, 200] : [226, 224, 218];
      return ao(c, q, 0.2);
    };
    P.glove = q => {
      let c = shade([46, 48, 58], q, 9);
      if ((q.f === 'north' || q.f === 'bottom') && q.ny > 0.62) {
        c = shade(v.skin, q, 1, 0.86, 1.04);                                                     // dedos de fora
        if (q.f === 'north' && fr(q.px * 1.4) < 0.18) c = mul(c, 0.78);
      }
      return c;
    };
    P.holster = q => {
      let c = shade([30, 30, 36], q, 10);
      if (q.ny > 0.3 && q.ny < 0.42 || q.ny > 0.7 && q.ny < 0.82) c = [214, 212, 206];       // tiras
      if (q.f === 'west' && q.ny < 0.18 && Math.abs(q.nz - 0.5) < 0.2) c = [150, 154, 166];   // argolas das kunais
      return c;
    };
    P.shinWrap = q => {
      const w = fr(q.ay * 0.8 - q.ax * 0.15 + n3(q.ax * 0.5, q.ay * 0.2, q.az * 0.5, 55 + S) * 0.6);
      let c = w < 0.16 ? [168, 168, 176] : w < 0.3 ? [208, 208, 212] : [236, 234, 228];
      return ao(c, q, 0.18);
    };
    P.sandal = q => {
      if (q.f === 'bottom' || q.ny > 0.66) return [34, 36, 52];
      if (q.f === 'top' || q.f === 'north') {
        if (q.nz < 0.38) {                                                                       // dedos
          let c = shade(v.skin, q, 1, 0.86, 1.04);
          if (q.f === 'top' && fr(q.px * 1.3) < 0.2) c = mul(c, 0.8);
          return c;
        }
        if (q.nz > 0.44 && q.nz < 0.56) return [40, 44, 64];                                     // tira
        return shade(v.skin, q, 1, 0.86, 1.04);
      }
      return [44, 48, 70];
    };
    // low poly: uma caixa por membro, a pintura decide a peça pela altura absoluta
    P.lowTorso = q => q.ay > 11.2 ? P.pants(q) : q.ay > 10.3 ? P.belt(q) : q.ay < 2.4 ? P.collar(q) : P.torso({ ...q, ny: (q.ay - 2) / 8.3 });
    P.lowArm = q => q.ay < 4.2 ? (q.ay < 2.8 ? P.sleeve(q) : P.upperArm({ ...q, ny: 0.6 })) : q.ay < 10 ? P.wrapArm(q) : P.glove({ ...q, ny: (q.ay - 10) / 2.2 });
    P.lowLeg = q => q.ay < 19 ? P.pants(q) : q.ay < 23.2 ? P.shinWrap(q) : P.sandal({ ...q, ny: 0.7 });
    P.lowHair = q => P.hairTop({ ...q, ny: q.ny * 0.6 });
    return P;
  }

  function rigDef(P, v, extra = {}) {
    const arm = s => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * 5.5, -8, 0], boxes: [
        { x: -1.5, y: -1, z: -1.5, w: 3, h: 5, d: 3, p: P.upperArm },
        { x: -1.7, y: -1.25, z: -1.7, w: 3.4, h: 2.1, d: 3.4, p: P.sleeve },
      ], kids: [{
        name: s < 0 ? 'anteD' : 'anteE', pivot: [0, 4, 0], boxes: [{ x: -1.4, y: 0, z: -1.4, w: 2.8, h: 4, d: 2.8, p: P.wrapArm }].concat(s < 0 && extra.armDevice ? [extra.armDevice] : []),
        kids: [{ name: s < 0 ? 'maoD' : 'maoE', pivot: [0, 4, 0], boxes: [{ x: -1.3, y: 0, z: -1.3, w: 2.6, h: 2.2, d: 2.6, p: P.glove }] }],
      }],
    });
    const leg = s => ({
      name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * 2, 13, 0], boxes: [{ x: -2, y: 0, z: -2, w: 4, h: 6, d: 4, p: P.pants }]
        .concat(s < 0 ? [{ x: -2.7, y: 1.4, z: -1.1, w: 0.8, h: 3.2, d: 2.2, p: P.holster }] : []),
      kids: [{
        name: s < 0 ? 'canelaD' : 'canelaE', pivot: [0, 6, 0], boxes: [{ x: -1.8, y: 0, z: -1.8, w: 3.6, h: 4.2, d: 3.6, p: P.shinWrap }],
        kids: [{ name: s < 0 ? 'peD' : 'peE', pivot: [0, 4.2, 0], boxes: [{ x: -1.9, y: 0, z: -2.6, w: 3.8, h: 0.8, d: 4.4, p: P.sandal }] }],
      }],
    });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'quadril', pivot: [0, 13, 0], boxes: [
            { x: -4, y: -2, z: -2, w: 8, h: 2, d: 4, p: P.pants },
            { x: -4.3, y: -2.6, z: -2.3, w: 8.6, h: 1.1, d: 4.6, p: P.belt },
            { x: -1.5, y: -3, z: 2.1, w: 3, h: 2, d: 1.3, p: P.pouch },
          ], kids: [{
            name: 'tronco', pivot: [0, -2, 0], boxes: [
              { x: -4, y: -9, z: -2, w: 8, h: 9, d: 4, p: P.torso },
              { x: -3.6, y: -6.2, z: -2.7, w: 2.6, h: 2.2, d: 0.8, p: P.pouchV },
              { x: 1, y: -6.2, z: -2.7, w: 2.6, h: 2.2, d: 0.8, p: P.pouchV },
              ...(extra.torso || []),
              v.scarf ? { x: -3.2, y: -9.9, z: -2.5, w: 6.4, h: 1.9, d: 5, p: P.scarf } : { x: -2.8, y: -10, z: -1.9, w: 5.6, h: 1.3, d: 3.8, p: P.collar },
            ], kids: [
              {
                name: 'cabeca', pivot: [0, -9.3, 0], boxes: [
                  { x: -3.5, y: -7, z: -3.5, w: 7, h: 7, d: 7, p: P.head },
                  { x: -3.8, y: -8.6, z: -3.8, w: 7.6, h: 2.4, d: 7.6, p: P.hairTop },
                  { x: -3.7, y: -6.4, z: 2.6, w: 7.4, h: 4, d: 1.3, p: P.hairBack },
                  { x: -3.75, y: -5.6, z: -3.75, w: 7.5, h: 1.5, d: 7.5, p: P.band },
                  { x: -2, y: -5.8, z: -3.95, w: 4, h: 1.9, d: 0.4, p: P.plate },
                ], kids: [{
                  name: 'nos', pivot: [0.8, -4.9, 3.75], rot: [0.35, 0, 0.12], boxes: extra.noTails ? [] : [
                    { x: -1.2, y: 0, z: 0, w: 1.2, h: 4.4, d: 0.3, p: P.band },
                    { x: 0.2, y: 0, z: 0.05, w: 1, h: 3.4, d: 0.3, p: P.band },
                  ],
                }],
              },
              arm(-1), arm(1),
              { name: 'cauda', pivot: [-1.4, -8.4, 2.5], rot: [0.22, 0, 0.06], boxes: [{ x: -1, y: 0, z: 0, w: 2, h: 8, d: 0.5, p: P.scarfTail }] },
            ],
          }],
        },
        leg(-1), leg(1),
      ],
    };
  }
  // versão low poly: 8 caixas, mesmas cores (pra quando tiver muitos na tela ou estiverem longe)
  function rigLow(P, v) {
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'quadril', pivot: [0, 13, 0], boxes: [{ x: -4, y: -11.3, z: -2, w: 8, h: 11.3, d: 4, p: P.lowTorso }], kids: [
            {
              name: 'cabeca', pivot: [0, -11.3, 0], boxes: [
                { x: -3.5, y: -7, z: -3.5, w: 7, h: 7, d: 7, p: P.head },
                { x: -3.8, y: -8.4, z: -3.8, w: 7.6, h: 1.8, d: 7.6, p: P.lowHair },
                { x: -3.75, y: -5.6, z: -3.75, w: 7.5, h: 1.5, d: 7.5, p: q => (q.f === 'north' && Math.abs(q.nx - 0.5) < 0.27 ? P.plate({ ...q, nx: (q.nx - 0.23) / 0.54 }) : P.band(q)) },
              ],
            },
            { name: 'bracoD', pivot: [-5.5, -10, 0], boxes: [{ x: -1.5, y: -1, z: -1.5, w: 3, h: 11.2, d: 3, p: P.lowArm }] },
            { name: 'bracoE', pivot: [5.5, -10, 0], boxes: [{ x: -1.5, y: -1, z: -1.5, w: 3, h: 11.2, d: 3, p: P.lowArm }] },
          ],
        },
        { name: 'pernaD', pivot: [-2, 13, 0], boxes: [{ x: -2, y: 0, z: -2, w: 4, h: 11, d: 4, p: P.lowLeg }] },
        { name: 'pernaE', pivot: [2, 13, 0], boxes: [{ x: -2, y: 0, z: -2, w: 4, h: 11, d: 4, p: P.lowLeg }] },
      ],
    };
  }

  // ---------- kunai e tronco ----------
  function kunaiDef() {
    const steel = q => {
      const ridge = Math.abs(q.nx - 0.5) < 0.12;
      let c = mix([70, 74, 86], [150, 156, 170], n3(q.ax * 2, q.ay * 2, q.az * 2, 60));
      if (ridge) c = [196, 202, 214];
      if (q.nx < 0.12 || q.nx > 0.88) c = [226, 230, 238];
      return c;
    };
    return {
      name: 'kunai', pivot: [0, 0, 0], boxes: [
        { x: -0.35, y: -1.4, z: -0.35, w: 0.7, h: 2.8, d: 0.7, p: q => (fr(q.ay * 1.6 + q.ax) < 0.5 ? [36, 30, 30] : [70, 58, 50]) },
        { x: -0.9, y: -2.3, z: -0.2, w: 1.8, h: 0.4, d: 0.4, p: () => [96, 100, 112] },
        { x: -0.9, y: -3.1, z: -0.2, w: 1.8, h: 0.4, d: 0.4, p: () => [96, 100, 112] },
        { x: -0.9, y: -3.1, z: -0.2, w: 0.4, h: 1.2, d: 0.4, p: () => [96, 100, 112] },
        { x: 0.5, y: -3.1, z: -0.2, w: 0.4, h: 1.2, d: 0.4, p: () => [96, 100, 112] },
        {
          x: -1, y: 1.4, z: -0.22, w: 2, h: 4, d: 0.44, p: q => {
            // lâmina em folha: larga logo depois do cabo e afinando até a ponta
            const half = Math.abs(q.nx - 0.5) * 2;
            const wmax = q.ny < 0.18 ? 0.45 + q.ny * 3 : (1 - q.ny) / 0.82;
            if (half > wmax) return null;
            return steel(q);
          },
        },
      ],
    };
  }
  function logDef() {
    const bark = q => {
      if (q.f === 'top' || q.f === 'bottom') {                                                  // anéis do corte
        const r = Math.hypot(q.nx - 0.5, q.nz - 0.5) * 2;
        const ring = fr(r * 4 + n3(q.nx * 3, 0, q.nz * 3, 61) * 0.6);
        if (r > 0.86) return [86, 60, 40];
        return ring < 0.22 ? [176, 136, 88] : [212, 172, 118];
      }
      const g = n3(q.ax * 1.2, q.ay * 0.18, q.az * 1.2, 62);
      let c = cel([104, 76, 50], g, 0.7, 1.2);
      if (fr(q.ax * 0.7 + q.az * 0.7 + g * 1.5) < 0.16) c = [62, 44, 30];                     // sulcos da casca
      if (n3(q.ax * 0.5, q.ay * 0.5, q.az * 0.5, 63) > 0.8) c = mix(c, [92, 118, 60], 0.6);   // musgo
      return c;
    };
    return {
      name: 'tronco', pivot: [0, 0, 0], boxes: [
        { x: -2.6, y: 2, z: -2.6, w: 5.2, h: 22, d: 5.2, p: bark },
        { x: 2.4, y: 9, z: -0.8, w: 3, h: 1.6, d: 1.6, p: bark },
      ],
    };
  }
  const defOf = v => rigDef(painters(v), v);
  const lowOf = v => rigLow(painters(v), v);
  const kunaiModel = () => { const m = ENG.build(kunaiDef()); return ENG.toThree(m, ENG.paint(m, m.W, m.H)); };

  // ---------- poses ----------
  function guard(r, P) {
    r('quadril', 0.1, 0, 0); P.quadril.obj.position.y += 0.7;
    r('tronco', 0.08, 0, 0);
    r('cabeca', -0.18, 0, 0);                                                                    // cabeça firme olhando à frente
    r('pernaD', -0.38, 0.08, 0); r('canelaD', 0.55, 0, 0); r('peD', -0.17, 0, 0);
    r('pernaE', -0.08, -0.12, 0); r('canelaE', 0.4, 0, 0); r('peE', -0.32, 0, 0);
    r('bracoD', 0.08, 0, 0.18); r('anteD', -0.35, 0, 0);
    r('bracoE', -0.62, 0, -0.1); r('anteE', -0.95, 0, 0); r('maoE', 0, 0, 0);
  }
  const WALK_T = [[0, -0.42], [0.14, -0.28], [0.36, 0.1], [0.52, 0.42], [0.64, 0.22], [0.82, -0.3]];
  const WALK_K = [[0, 0.06], [0.14, 0.14], [0.4, 0.06], [0.56, 0.34], [0.7, 0.78], [0.86, 0.24]];
  const WALK_B = [[0, 0.1], [0.14, 0.42], [0.36, -0.12], [0.5, 0.1], [0.64, 0.42], [0.86, -0.12]];
  const WALK_A = [[0, 0.34], [0.2, 0.22], [0.5, -0.32], [0.7, -0.2]];

  function anim(P, t, mode, a, obj, v, ctx) {
    const has = n => !!P[n];
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const low = !has('tronco');
    const breath = loop([[0, 0], [0.42, 1], [1 - 0.001, 0]], t / 3.4);
    const sway = loop([[0, -0.04], [0.35, 0.05], [0.6, 0.02], [0.85, -0.05]], t / 4.1);
    const kunai = ctx.attach('kunai-mao', P.maoD || P.bracoD, () => {
      const k = kunaiModel().children[0];
      if (low) k.position.set(0, 10.5, 0);
      else k.position.set(0, 1.3, -0.2);
      return k;
    });
    kunai.visible = false;

    if (mode === 'parado') {
      guard(r, P);
      r('tronco', -0.02 * breath, 0, 0); r('cabeca', 0.02 * breath, 0, 0);
      r('cauda', sway, 0, sway * 0.5); r('nos', sway * 0.8, 0, 0);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 0.95;
      for (const [s, off] of [['D', 0], ['E', 0.5]]) {
        r('perna' + s, loop(WALK_T, ph + off), 0, 0);
        r(low ? '' : 'canela' + s, loop(WALK_K, ph + off), 0, 0);
        r('pe' + s, -0.25 * loop(WALK_K, ph + off - 0.06), 0, 0);
      }
      P.quadril.obj.position.y += loop(WALK_B, ph);
      r('quadril', 0.06, loop([[0, 0.05], [0.5, -0.05]], ph - 0.03), 0);
      r('tronco', 0.04, -loop([[0, 0.05], [0.5, -0.05]], ph - 0.03) * 0.6, 0);
      r('bracoD', loop(WALK_A, ph - 0.04), 0, 0.08); r('bracoE', loop(WALK_A, ph + 0.5 - 0.04), 0, -0.08);
      r('anteD', -0.3, 0, 0); r('anteE', -0.3, 0, 0);
      r('cabeca', -0.1, -loop([[0, 0.05], [0.5, -0.05]], ph - 0.03) * 0.4, 0);
      r('cauda', 0.35 + loop([[0, 0], [0.3, 0.1], [0.6, 0.03]], ph), 0, 0); r('nos', 0.25, 0, 0);
      obj.position.z -= fr(t * 0.95 / 2) * 0; // anda no lugar
      return;
    }

    if (mode === 'substituicao') {
      const T = t % 3.4;
      const R = ctx.ref; R.place(0, -2.4, 0);
      // o alvo prepara e soca
      R.punch(seq([[0.3, 0], [0.58, -0.45], [0.66, 1, 'snap'], [0.95, 1], [1.3, 0]], T));
      R.place(0, seq([[0.45, -2.4], [0.7, -1.75, 'out']], T), 0);
      // apanha pelas costas e é empurrado
      if (T > 1.5) {
        const hit = seq([[1.5, 0], [1.6, 1, 'snap'], [1.95, 1], [2.5, 0]], T);
        R.place(0, -1.75 + seq([[1.5, 0], [1.75, 0.8, 'out']], T), 0);
        R.lean(0.5 * hit);   // empurrado pra frente pelo corte nas costas
      }
      const log = ctx.get('tronco', () => { const m = ENG.build(logDef()); const o = ENG.toThree(m, ENG.paint(m, m.W, m.H)); const g = new THREE.Group(); g.add(o); return g; });
      if (T >= 0.7 && T < 3.2) {
        // o soco derruba o tronco pra trás, girando no pé; quica uma vez
        const fall = seq([[0.7, 0], [1.1, 1, 'in'], [1.22, 0.92, 'out'], [1.34, 1, 'in']], T);
        log.visible = true;
        log.position.set(0.05, 0, 0.1);
        log.rotation.set(fall * 1.45, 0.3, 0.12 * fall);
      }
      const poof = ctx.get('poof', FX.volume);
      if (T >= 0.7 && T < 1.4) poof.set(new THREE.Vector3(0, 1.0, 0), 0.75, (T - 0.7) / 0.7, { seed: 3.1, dens: 11, rise: 0.4, smoke: [0.93, 0.93, 0.95] });
      const poof2 = ctx.get('poof2', FX.volume);
      if (T >= 1.0 && T < 1.5) poof2.set(new THREE.Vector3(0, 0.9, -3.1), 0.5, (T - 1.0) / 0.5, { seed: 7.7, dens: 10, rise: 0.3, smoke: [0.9, 0.9, 0.93] });
      if (T >= 0.7 && T < 1.0) { obj.visible = false; return; }
      if (T >= 1.0 && T < 3.2) {
        obj.position.set(0, 1.5 + seq([[1.0, 0.55], [1.12, 0, 'in']], T), -3.1);
        obj.rotation.y = Math.PI;
        kunai.visible = T > 1.12;
        // aterrissa agachado, levanta armando, corta em 2 ticks, segura, volta à guarda
        const land = seq([[1.0, 0.4], [1.12, 1, 'in'], [1.3, 0.35, 'out'], [2.2, 0.35], [2.6, 0]], T);
        const wind = seq([[1.2, 0], [1.45, 1, 'out'], [1.52, 1]], T);
        const cut = seq([[1.5, 0], [1.57, 1, 'snap'], [1.9, 1], [2.6, 0, 'io']], T);
        guard(r, P);
        P.quadril.obj.position.y += 3.2 * land;
        r('pernaD', -0.9 * land, 0, 0); r('canelaD', 1.5 * land, 0, 0);
        r('pernaE', -0.4 * land, 0, 0); r('canelaE', 1.2 * land, 0, 0);
        r('quadril', 0.35 * land, 0, 0); r('cabeca', -0.35 * land, 0, 0);
        const w = wind * (1 - cut);
        r('tronco', -0.1 * w + 0.25 * cut, 0.7 * w - 0.55 * cut, 0);
        r('bracoD', -2.6 * w - 1.25 * cut, 0.2 * w, 0.5 * w - 0.9 * cut);
        r('anteD', -0.9 * w + 0.6 * cut, 0, 0);
        r('maoD', 0.4 * w - 0.5 * cut, 0, 0);
        r('bracoE', 0.4 * cut, 0, 0.5 * cut);
        r('cabeca', 0, -0.5 * w + 0.4 * cut, 0);
        r('cauda', 0.6 * cut + 0.3 * land, 0, 0.2 * cut);
        if (T > 1.55 && T < 1.9) ctx.slash(new THREE.Vector3(0.1, 1.15, -2.35), (T - 1.55) / 0.35);
        return;
      }
      guard(r, P);
      r('cauda', sway, 0, sway * 0.5);
      return;
    }

    if (mode === 'papelbomba') {
      const T = t % 3.9;
      const R = ctx.ref; R.place(0, -3.3, 0);
      const S = new THREE.Vector3(0.28, 0, -2.55);
      // pula pra trás depois do arremesso
      const hop = seq([[0.92, 0], [1.38, 1, 'out']], T), air = Math.sin(Math.PI * FX.clamp01((T - 0.98) / 0.42));
      obj.position.z += 1.9 * hop;
      obj.position.y += 0.9 * air * (T > 0.98 && T < 1.4 ? 1 : 0);
      guard(r, P);
      const wind = seq([[0.3, 0], [0.6, 1, 'out']], T);
      const thr = seq([[0.6, 0], [0.66, 1, 'snap'], [0.84, 1.15, 'out'], [1.0, 1]], T);
      const back = seq([[0.6, 0], [0.82, 1]], T) * (1 - seq([[1.7, 0], [2.1, 1]], T));
      const w = wind * (1 - Math.min(1, thr));
      r('tronco', -0.12 * w + 0.28 * thr * back, 0.6 * w - 0.4 * thr * back, 0);
      r('bracoD', -3.3 * w + (-1.25) * thr * back, 0, 0.25 * w);
      r('anteD', -1.3 * w, 0, 0);
      r('bracoE', -0.9 * w, 0, -0.3 * w);
      kunai.visible = T < 0.66;
      // agacha, salta, aterrissa
      const crouch = seq([[0.86, 0], [0.97, 1, 'out'], [1.0, 0.2, 'in'], [1.38, 0.15], [1.46, 1, 'out'], [1.8, 0]], T);
      P.quadril.obj.position.y += 2.6 * crouch;
      r('pernaD', -0.7 * crouch, 0, 0); r('canelaD', 1.3 * crouch, 0, 0);
      r('pernaE', -0.7 * crouch, 0, 0); r('canelaE', 1.3 * crouch, 0, 0);
      r('quadril', 0.3 * crouch, 0, 0); r('cabeca', -0.3 * crouch, 0, 0);
      if (T > 0.98 && T < 1.38) { r('pernaD', -0.9, 0, 0); r('canelaD', 1.6, 0, 0); r('pernaE', -0.5, 0, 0); r('canelaE', 1.1, 0, 0); r('bracoE', 0.5, 0, -0.6); }
      r('cauda', 0.3 + 0.5 * air, 0, 0); r('nos', 0.4 * air, 0, 0);

      // a kunai voa e finca
      const k = ctx.get('kunai-voando', () => {
        // origem do grupo = ponta da lâmina, pra cravar exatamente no ponto
        const o = new THREE.Group(); o.rotation.order = 'YXZ';
        const km = kunaiModel(); km.position.set(0, 0, 0); km.children[0].position.set(0, -5.4, 0); o.add(km);
        const tg = FX.tag(11); tg.position.set(0, 0.42, 0.035); o.add(tg); o.tg = tg; return o;
      });
      if (T >= 0.66 && T < 2.34) {
        k.visible = true;
        const H0 = new THREE.Vector3(-0.32, 1.55, -0.6);
        const f = FX.clamp01((T - 0.66) / 0.2);
        const p = H0.clone().lerp(S, f); p.y += Math.sin(Math.PI * f) * 0.3;
        k.position.copy(p);
        const dir = S.clone().sub(H0);
        // voa quase deitada, lâmina na frente; crava a 35 graus da vertical
        k.rotation.set(f < 1 ? 1.25 - 0.4 * f : 0.62, Math.atan2(-dir.x, -dir.z), 0);
        const flap = f >= 1 ? 0.12 * loop([[0, 0], [0.3, 1], [0.55, -0.4], [0.8, 0.6]], T * 3.1) : -0.5;
        k.tg.rotation.set(flap, 0, 0);
        k.tg.burn(FX.clamp01((T - 0.86) / 1.48));
      }
      if (T >= 0.86 && T < 2.34) ctx.ember(S.clone().add(new THREE.Vector3(0, 0.45 - 0.3 * FX.clamp01((T - 0.86) / 1.48), 0)), T);
      // explosão no estilo do Katon: miolo branco-amarelo, vermelho, fumaça escura
      const boom = ctx.get('boom', FX.volume);
      if (T >= 2.34) {
        const age = (T - 2.34) / 1.45;
        boom.set(S.clone().add(new THREE.Vector3(0, 0.7, 0)), 1.5, age, { fire: 1, seed: 4.2, dens: 13, rise: 0.55, smoke: [0.165, 0.133, 0.141] });
        ctx.flash(S.clone().add(new THREE.Vector3(0, 0.8, 0)), Math.max(0, 1 - (T - 2.34) / 0.35));
        const deb = ctx.get('detritos', () => FX.debris(30, 5, [[96, 70, 44], [74, 120, 50], [120, 96, 64], [60, 52, 46]]));
        deb.at(S.clone().add(new THREE.Vector3(0, 0.1, 0)), T - 2.34);
        ctx.shake(Math.max(0, 1 - (T - 2.34) / 0.4));
        // o alvo é jogado longe e cai
        const kb = seq([[2.34, 0], [2.9, 1, 'out']], T);
        const up = Math.sin(Math.PI * FX.clamp01((T - 2.34) / 0.56));
        R.place(0, -3.3 - 2.0 * kb, 1.1 * up);
        R.lean(-0.9 * Math.sin(Math.PI * FX.clamp01((T - 2.34) / 0.9)));
      }
      return;
    }
  }

  window.NINJA = { painters, variant, rigDef, rigLow, guard, kunaiModel, WALK_T, WALK_K, WALK_B, WALK_A };
  window.MOBS = window.MOBS || [];
  const fixed = [variant(3), variant(8), variant(21)];
  window.MOBS.push({
    id: 'ninja_renegado', nome: 'Ninja Renegado', bioma: 'Floresta da Folha', tier: 'Fraco · tier 9',
    variants: fixed.map((v, i) => ({ nome: ['Mascarado', 'Cicatriz', 'Cachecol'][i], v, def: defOf(v), low: lowOf(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, def: defOf(v), low: lowOf(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['substituicao', 'Substituição'], ['papelbomba', 'Papel-bomba']],
    cam: 6, lookY: 1.1, lookZ: { substituicao: -2.6, papelbomba: -1.4 }, camFar: { substituicao: 6.5, papelbomba: 7.5 }, camYaw: { substituicao: -2.05, papelbomba: -1.2 },
    info: {
      'Tamanho': '1,85 bloco, do tamanho do jogador',
      'Vida': '70 (tier 9)',
      'Jeito': 'Hostil, anda em dupla nas bordas da floresta. Espera nos galhos e pula em quem passa embaixo.',
      'Substituição': 'Quando leva um golpe forte (recarga 12 s), vira um tronco num estouro de fumaça e reaparece atrás de você com um corte de kunai. Aviso: o "puf" da fumaça antes do corte.',
      'Papel-bomba': 'Finca uma kunai com papel-bomba perto dos seus pés, pula pra trás e o papel queima por 1,5 s antes de explodir. Quem vê o papel queimando dá tempo de sair.',
      'Solta': 'Kunai, ramen; raro: bandana riscada.',
      'Variações': 'Cabelo, pele, cor do colete, cachecol, máscara e cicatriz sorteados: dois renegados nunca são iguais.',
    },
  });
})();
