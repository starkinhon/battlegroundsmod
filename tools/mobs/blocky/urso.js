// Ladrão Urso (Montanhas Paozu). Referências: referencias/mobs-db/ladrao-urso/.
'use strict';
(() => {
  const { n3, fbm, mix, mul, smooth } = ENG;
  const FUR = [224, 106, 44], FUR_D = [150, 58, 26], FUR_L = [246, 152, 84];
  const fr = x => x - Math.floor(x);

  function fur(q) {
    // pelo em faixas chapadas de anime + alguns fios mais escuros/claros soltos
    const v = n3(q.ax * 0.18, q.ay * 0.14, q.az * 0.18, 2) * 0.7 + n3(q.ax * 0.5, q.ay * 0.2, q.az * 0.5, 1) * 0.3;
    let c = ENG.cel(FUR, v, 0.8, 1.1);
    const st = n3(q.ax * 1.4, q.ay * 0.25, q.az * 1.4, 3);
    if (st > 0.84) c = mul(c, 0.82); else if (st < 0.1) c = mix(c, FUR_L, 0.5);
    if (q.f === 'top') c = mul(c, 1.06);
    if (q.f === 'bottom') c = mul(c, 0.76);
    return c;
  }
  function ao(c, q, k = 0.18) { return mul(c, 1 - k * q.ny); }

  function rope(q) {
    // corda torcida: espiras diagonais com variação
    const t = fr((q.ax * 0.5 + q.ay * 1.2 + q.az * 0.5) / 2.4);
    return t < 0.12 ? [110, 14, 18] : t < 0.3 ? [250, 96, 78] : [214, 44, 38];
  }
  function armor(q) {
    const n = n3(q.ax * 0.22, q.ay * 0.22, q.az * 0.22, 3);
    let c = ENG.cel([34, 32, 42], n, 0.75, 1.35);
    // um reflexo de verniz por placa, alongado
    if (q.f !== 'bottom' && n3(q.ax * 0.15, q.ay * 0.5, q.az * 0.15, 23) > 0.74) c = [168, 174, 192];
    return c;
  }
  const GOLD = q => mix([170, 110, 20], [250, 205, 70], 0.45 + 0.55 * n3(q.ax * 0.8, q.ay * 0.8, q.az * 0.8, 9));

  function pants(q) {
    // luz de cima, sombra embaixo, bordas das dobras pelo ruído
    const f = 0.62 - 0.45 * q.ny + 0.35 * (n3(q.ax * 0.12, q.ay * 0.12, q.az * 0.12, 4) - 0.5) + (q.f === 'top' ? 0.3 : 0);
    let c = ENG.cel([124, 64, 176], f, 0.74, 1.14);
    // dobras grandes do tecido bufante, poucas e largas
    const fold = Math.sin(q.ax * 0.55 + q.az * 0.55 + n3(q.ax * 0.2, q.ay * 0.2, q.az * 0.2, 21) * 6);
    if (fold > 0.9) c = mul(c, 0.7);
    if (q.f === 'top') c = mix(c, [196, 158, 236], 0.25);
    return ao(c, q, 0.2);
  }
  function tunic(q) {
    const f = 0.6 - 0.4 * q.ny + 0.3 * (n3(q.ax * 0.12, q.ay * 0.12, q.az * 0.12, 22) - 0.5);
    return ENG.cel([112, 58, 164], f, 0.76, 1.12);
  }
  function wraps(q) {
    const w = fr(q.ay * 0.62 + n3(q.ax * 0.5, q.ay * 0.2, q.az * 0.5, 6) * 0.9);
    let c = [240, 238, 230];
    if (w < 0.2) c = [176, 172, 184];
    else if (w < 0.32) c = [212, 210, 214];
    return ao(c, q, 0.22);
  }
  function boot(q) {
    if (q.f === 'bottom' || q.ny > 0.86) return [58, 38, 30];
    if (q.ny < 0.2 && q.f !== 'top') return GOLD(q);
    if (q.f === 'north' && Math.abs(q.nx - 0.5) < 0.07) return GOLD(q);
    const n = fbm(q.ax * 0.4, q.ay * 0.4, q.az * 0.4, 8);
    return mul([92, 50, 136], 0.75 + 0.4 * n);
  }
  function gauntlet(q, outerFace) {
    if (q.ny < 0.14 || (q.ny > 0.5 && q.ny < 0.6)) return GOLD(q);
    if (q.ny > 0.66) return rope(q);
    // medalhão dourado redondo na face de fora
    if (q.f === outerFace) {
      const dx = q.nz - 0.5, dy = (q.ny - 0.33) * 1.6;
      if (dx * dx + dy * dy < 0.07) return dx * dx + dy * dy < 0.025 ? [200, 140, 30] : GOLD(q);
    }
    return mul([112, 56, 162], 0.78 + 0.35 * fbm(q.ax * 0.4, q.ay * 0.4, q.az * 0.4, 10));
  }
  function fist(q) {
    let c = ao(fur(q), q, 0.1);
    // garras escuras saindo dos nós dos dedos, cada uma com comprimento próprio
    if ((q.f === 'north' || q.f === 'bottom') && q.ny > 0.7) {
      const k = Math.floor(q.px * 1.0);
      const len = 0.7 + 0.25 * n3(k * 3.1, 0, q.ax, 11);
      if (k % 2 === 0 && q.ny > len) c = [46, 34, 34];
    }
    return c;
  }
  const STRAP = q => mix([200, 150, 30], [250, 210, 60], 0.5 + 0.5 * n3(q.ax * 0.3, q.ay * 0.3, 0, 12));
  function chest(q) {
    if (q.ny < 0.3 && q.f !== 'bottom') return rope(q);
    let c = armor(q);
    if (q.f === 'north') for (const cx of [0.28, 0.72]) if (Math.abs(q.nx - cx) < 0.05) c = STRAP(q);
    return c;
  }
  function tunicFront(q) {
    let c = tunic(q);
    if (q.f !== 'north') return c;
    for (const cx of [0.28, 0.72]) {
      if (Math.abs(q.nx - cx) < 0.05 && q.ny < 0.45) c = STRAP(q);
      if (Math.abs(q.nx - cx) < 0.08 && q.ny >= 0.3 && q.ny < 0.5) c = [218, 170, 44];
    }
    // placa dourada embaixo, no meio
    if (Math.abs(q.nx - 0.5) < 0.17 && q.ny > 0.7) c = Math.abs(q.nx - 0.5) < 0.12 && q.ny > 0.78 && q.ny < 0.92 ? [206, 156, 36] : [250, 215, 80];
    return c;
  }
  function pad(q) {
    if (q.ny > 0.62 && q.f !== 'top') return rope(q);
    return armor(q);
  }
  function head(q) {
    let c = fur(q);
    if (q.f !== 'north') return c;
    const x = Math.floor(q.px), y = Math.floor(q.py);
    if (y >= 5 && x >= 3 && x <= 7) c = mix(c, FUR_L, 0.4);              // focinho mais claro
    // olho aberto (direito dele = esquerda da tela): sobrancelha brava descendo pro meio, olho amarelo
    if ((y === 2 && x >= 1 && x <= 2) || (y === 3 && x >= 3 && x <= 4)) c = [64, 22, 10];
    if (y === 3 && x >= 1 && x <= 2) c = [252, 214, 40];
    if (y === 3 && x === 2) c = [252, 214, 40];
    if (y === 4 && x >= 1 && x <= 3) c = mul(c, 0.72);
    if (y === 3 && x === 2) c = [24, 14, 8];
    // olho fechado (esquerdo dele) e a cicatriz atravessando
    if (y === 4 && x >= 6 && x <= 9) c = [64, 22, 10];
    if (y === 3 && (x === 6 || x === 9)) c = mul(c, 0.8);
    if (x === 8 && y >= 1 && y <= 7) c = y % 2 ? [150, 38, 28] : [196, 72, 50];
    // sorriso cruel no canto da boca
    if (y === 8 && (x === 2 || x === 8)) c = [70, 22, 12];
    return c;
  }
  function snout(q) {
    let c = mix(fur(q), FUR_L, 0.35);
    if (q.f === 'bottom') {
      // céu da boca + dentes de cima (presas maiores nos cantos)
      const edge = q.nx < 0.17 || q.nx > 0.83 || q.nz < 0.22;
      c = edge ? [244, 240, 226] : [150, 36, 44];
      if (q.nz < 0.22 && (q.nx < 0.2 || q.nx > 0.8)) c = [255, 252, 240];
    }
    if (q.f === 'north' && q.ny > 0.8) c = [90, 30, 22];
    return c;
  }
  function jaw(q) {
    let c = mul(mix(fur(q), FUR_L, 0.3), 0.92);
    if (q.f === 'top') {
      const edge = q.nx < 0.2 || q.nx > 0.8 || q.nz < 0.2;
      c = edge ? [242, 238, 222] : (Math.abs(q.nx - 0.5) < 0.22 && q.nz > 0.3 ? [214, 90, 104] : [140, 30, 40]);
      if (edge && n3(q.ax * 2.3, 0, q.az * 2.3, 14) < 0.3) c = [150, 34, 42];
    }
    return c;
  }
  const nose = q => q.f === 'top' || (q.f === 'north' && q.ny < 0.3) ? [120, 118, 128] : [48, 44, 54];
  function ear(q, torn) {
    if (torn && q.nx > 0.55 && q.ny < 0.4) return null;
    let c = fur(q);
    if (q.f === 'north' && q.nx > 0.2 && q.nx < 0.8 && q.ny > 0.25) c = [110, 44, 26];
    return c;
  }
  function mohawk(q) {
    // crista: cada fio com altura própria
    const k = Math.floor(q.pz * 1.0);
    const hgt = 0.25 + 0.75 * Math.pow(n3(k * 1.37, 0.5, 0, 15), 0.8) * (0.55 + 0.45 * Math.sin(Math.PI * (q.nz * 0.8 + 0.15)));
    if (1 - q.ny > hgt + 0.02) return null;
    const s = n3(q.ax * 1.2, q.ay * 0.4, q.az * 1.2, 16);
    return mix([140, 20, 20], [235, 52, 42], 0.35 + 0.65 * s);
  }
  function blade(q, tip) {
    if (tip) {
      // ponta: afina até o bico, com o fio de cima reto e o de baixo curvo
      const half = Math.abs(q.ny - 0.5) * 2;
      if (half > Math.pow(q.nz, 0.7) * 1.05 + 0.05) return null;
      if (q.nz < 0.1 && q.ny < 0.55) return null;
    }
    const n = n3(q.ax * 0.4, q.ay * 0.4, q.az * 0.4, 17);
    let c = mix([196, 204, 214], [248, 250, 252], q.ny * 0.6 + n * 0.4);
    if (q.ny > 0.82) c = [255, 255, 255];
    if (q.ny < 0.16) c = [150, 156, 168];
    return c;
  }
  const grip = q => fr(q.az * 0.55 + q.ay * 0.3) < 0.5 ? [78, 48, 30] : [120, 78, 44];
  function sash(q) {
    const f = fbm(q.ax * 0.3, q.ay * 0.6, q.az * 0.3, 18);
    let c = mul([72, 176, 62], 0.72 + 0.45 * f);
    if (Math.sin(q.ax * 1.1 + f * 5) > 0.85) c = mul(c, 0.7);
    return c;
  }
  function belt(q) {
    if (q.f === 'north' && (Math.abs(q.nx - 0.3) < 0.04 || Math.abs(q.nx - 0.7) < 0.04)) return GOLD(q);
    return mul([96, 60, 36], 0.8 + 0.3 * n3(q.ax, q.ay, q.az, 19));
  }
  function blueSash(q) {
    const f = fbm(q.ax * 0.5, q.ay * 0.3, q.az * 0.5, 20);
    let c = mul([124, 194, 238], 0.75 + 0.35 * f);
    if (q.f === 'west' || q.f === 'east') c = mul(c, 0.8);
    return c;
  }

  function arm(side) {
    const s = side; // -1 direito dele, +1 esquerdo
    const outer = s < 0 ? 'west' : 'east';
    const fistKids = [];
    if (s < 0) {
      fistKids.push({
        name: 'espada', pivot: [0, 3, -1.5], boxes: [
          { x: -1, y: -1, z: -3, w: 2, h: 2, d: 7, p: grip },
          { x: -2.5, y: -2.5, z: -4, w: 5, h: 5, d: 1, p: GOLD },
        ], kids: [{
          name: 'lamina1', pivot: [0, 0, -4], boxes: [{ x: -0.5, y: -2.5, z: -12, w: 1, h: 5, d: 12, p: q => blade(q, false) }],
          kids: [{
            name: 'lamina2', pivot: [0, 0, -12], rot: [-0.24, 0, 0], boxes: [{ x: -0.5, y: -3, z: -10, w: 1, h: 6, d: 10, p: q => blade(q, false) }],
            kids: [{ name: 'lamina3', pivot: [0, 0, -10], rot: [-0.36, 0, 0], boxes: [{ x: -0.5, y: -3, z: -8, w: 1, h: 6, d: 8, p: q => blade(q, true) }] }],
          }],
        }],
      });
    }
    return {
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * 13.5, -21, 0], rot: [0, 0, -s * 0.16], boxes: [
        { x: -6, y: -3.5, z: -7, w: 12, h: 6, d: 14, p: pad },
        { x: -5.25, y: 2.5, z: -5.25, w: 10.5, h: 2, d: 10.5, p: rope },
        { x: -5, y: -1, z: -5, w: 10, h: 11, d: 10, p: q => ao(fur(q), q, 0.12) },
      ], kids: [{
        name: s < 0 ? 'anteD' : 'anteE', pivot: [0, 10, 0], boxes: [{ x: -4.5, y: 0, z: -4.5, w: 9, h: 8, d: 9, p: q => gauntlet(q, outer) }],
        kids: [{ name: s < 0 ? 'punhoD' : 'punhoE', pivot: [0, 8, 0], boxes: [{ x: -4, y: 0, z: -4, w: 8, h: 6, d: 8, p: fist }], kids: fistKids }],
      }],
    };
  }
  function leg(side) {
    const n = side < 0 ? 'D' : 'E';
    return {
      name: 'coxa' + n, pivot: [side * 8, 5.4, 0], rot: [0, 0, -side * 0.24], boxes: [{ x: -6, y: 0, z: -6, w: 12, h: 9, d: 12, p: pants }],
      kids: [{
        name: 'canela' + n, pivot: [0, 9, 0], rot: [0, 0, side * 0.24], boxes: [{ x: -5, y: 0, z: -5, w: 10, h: 5, d: 10, p: wraps }],
        kids: [{ name: 'bota' + n, pivot: [0, 5, 0], boxes: [{ x: -4.5, y: 0, z: -6, w: 9, h: 5, d: 11, p: boot }] }],
      }],
    };
  }

  const def = {
    name: 'raiz', pivot: [0, 0, 0], kids: [
      {
        name: 'corpo', pivot: [0, 5, 0], boxes: [
          { x: -13, y: -3, z: -7, w: 26, h: 8, d: 14, p: pants },
          { x: -11.5, y: -4, z: -7.5, w: 23, h: 1, d: 15, p: belt },
          { x: -11, y: -8, z: -7, w: 22, h: 4, d: 14, p: sash },
          { x: -11, y: -18, z: -6.5, w: 22, h: 10, d: 13, p: tunicFront },
          { x: -12, y: -25, z: -7, w: 24, h: 7, d: 14, p: chest },
          { x: -6.5, y: -26, z: -5.5, w: 13, h: 2, d: 10, p: rope },
        ], kids: [
          {
            name: 'cabeca', pivot: [0, -24, -3], boxes: [
              { x: -5.5, y: -8, z: -5, w: 11, h: 9, d: 10, p: head },
              { x: -3, y: -3.5, z: -8.5, w: 6, h: 3, d: 3.5, p: snout },
              { x: -1.5, y: -4.1, z: -9.1, w: 3, h: 1.5, d: 1.5, p: nose },
            ], kids: [
              { name: 'mandibula', pivot: [0, -0.5, -4.5], boxes: [{ x: -2.5, y: 0, z: -4, w: 5, h: 2, d: 4.5, p: jaw }] },
              { name: 'orelhaD', pivot: [-4.5, -7.5, -0.5], rot: [0, 0, -0.3], boxes: [{ x: -1.5, y: -2.5, z: -1, w: 3, h: 3, d: 2, p: q => ear(q, false) }] },
              { name: 'orelhaE', pivot: [4.5, -7.5, -0.5], rot: [0, 0, 0.3], boxes: [{ x: -1.5, y: -2.5, z: -1, w: 3, h: 3, d: 2, p: q => ear(q, true) }] },
              { name: 'moicano', pivot: [0, -8, 0], boxes: [{ x: -1, y: -5, z: -5, w: 2, h: 5, d: 10, p: mohawk }] },
            ],
          },
          arm(-1), arm(1),
          {
            name: 'fitas', pivot: [-1, -3, -7.8], boxes: [
              { x: -2.5, y: 0, z: -0.2, w: 2, h: 12, d: 1, p: blueSash },
              { x: 0.4, y: 0.5, z: -0.6, w: 2, h: 9, d: 1, p: blueSash },
            ],
          },
        ],
      },
      leg(-1), leg(1),
    ],
  };

  // animação em segundos. a = fase do ataque 0..1
  function anim(P, t, mode, a) {
    const S = Math.sin, br = S(t * 1.6);
    const r = (n, x, y, z) => { const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    // pose base: espada erguida, punho esquerdo fechado
    r('bracoD', -0.45, 0.15, 0); r('anteD', -1.05, 0, 0);
    r('bracoE', -0.15, 0, 0); r('anteE', -0.5, 0, 0);
    r('corpo', 0.08 + br * 0.015, 0, 0);
    r('cabeca', -0.1 - br * 0.02, S(t * 0.4) * 0.15, 0);
    r('fitas', S(t * 1.3) * 0.06, 0, S(t * 0.9) * 0.05);
    P.corpo.obj.position.y += br * 0.25;
    r('punhoD', 0.2, 0, 0);
    if (mode === 'andar') {
      const w = t * 4.2, sw = S(w);
      r('coxaD', sw * 0.45, 0, 0); r('coxaE', -sw * 0.45, 0, 0);
      r('canelaD', Math.max(0, -sw) * 0.4, 0, 0); r('canelaE', Math.max(0, sw) * 0.4, 0, 0);
      r('corpo', 0, sw * 0.08, sw * 0.09);   // gingado pesado
      P.corpo.obj.position.y -= Math.abs(S(w)) * 0.9;
      r('bracoE', -sw * 0.35, 0, 0);
      r('fitas', -0.15 - Math.abs(sw) * 0.2, 0, sw * 0.12);
      r('cabeca', 0, -sw * 0.06, -sw * 0.05);
    }
    if (mode === 'atacar') {
      // levanta e ruge (0..0.42), desce num corte (0.42..0.52), segura o impacto (0.52..0.72), volta
      let up, down;
      if (a < 0.42) { up = ENG.smooth(0, 0.42, a); down = 0; }
      else if (a < 0.52) { up = 1; down = ENG.smooth(0.42, 0.52, a); }
      else if (a < 0.72) { up = 1; down = 1; }
      else { const k = ENG.smooth(0.72, 1, a); up = 1 - k; down = 1 - k; }
      const lift = up * (1 - down);
      r('bracoD', -2.3 * lift + 1.1 * down * (up), 0, -0.25 * lift);
      r('anteD', 0.6 * lift + 0.75 * down * up, 0, 0);
      r('corpo', -0.22 * lift + 0.32 * down * up, 0.25 * lift - 0.2 * down * up, 0);
      r('cabeca', -0.25 * lift + 0.15 * down * up, 0, 0);
      r('mandibula', 0.75 * lift + 0.3 * down * up, 0, 0);
      r('bracoE', -0.6 * lift, 0, 0.3 * lift);
      P.corpo.obj.position.y += 1.6 * down * up;
      r('coxaD', -0.25 * down * up, 0, 0.1 * down * up); r('coxaE', 0.2 * down * up, 0, -0.1 * down * up);
    }
  }

  window.MOBS = window.MOBS || [];
  window.MOBS.push({
    id: 'ladrao_urso', nome: 'Ladrão Urso', bioma: 'Montanhas Paozu', def, anim, attackLen: 1.9,
    cam: 8, lookY: 1.7,
    info: {
      'Tamanho': '3,1 blocos (3,4 com o moicano), 2 de largura',
      'Vida': '140',
      'Jeito': 'Hostil. Anda gingando, devagar, e ruge antes de atacar.',
      'Ataque': 'Ergue a cimitarra e corta de cima pra baixo: aviso de 0,8 s, dano alto, quebra a guarda.',
      'Rugido': 'Quando você foge, ruge e te deixa lento por 2 s.',
      'Solta': 'Pedaço da cimitarra (material) e às vezes uma senzu.',
    },
  });
})();
