// Androide 8 (Norte Gelado). Referências: referencias/mobs-db/androide-8/.
'use strict';
(() => {
  const { n3, mix, mul, cel } = ENG;
  const SUIT = [36, 62, 150], SKIN = [226, 162, 112], SWEATER = [76, 104, 214];
  const fr = x => x - Math.floor(x);
  const OUT = [14, 20, 52];

  function suitTone(q, k = 0) {
    const v = 0.62 - 0.38 * q.ny + 0.34 * (n3(q.ax * 0.1, q.ay * 0.1, q.az * 0.1, 51) - 0.5) + (q.f === 'top' ? 0.25 : 0) + k;
    let c = cel(SUIT, v, 0.72, 1.14);
    // vincos do pano grosso: poucas linhas longas
    const crease = Math.sin(q.ax * 0.35 + q.ay * 0.22 + n3(q.ax * 0.12, q.ay * 0.12, q.az * 0.12, 52) * 7);
    if (crease > 0.95) c = mul(SUIT, 0.6);
    return c;
  }
  function ribs(q) {
    // tricô canelado: listras verticais, cada coluna com seu tom
    const col = Math.floor((q.f === 'north' || q.f === 'south' ? q.ax : q.az) * 1.0);
    let c = col % 2 ? mul(SWEATER, 0.86) : SWEATER;
    return cel(c, 0.62 - 0.35 * q.ny, 0.84, 1.1);
  }
  function jacketUp(q) {
    let c = suitTone(q, 0.05);
    if (q.f !== 'north') return c;
    // abertura em V mostrando a blusa de gola alta, lapelas com contorno escuro
    const half = 0.34 * (1 - q.ny / 0.98);
    const d = Math.abs(q.nx - 0.5);
    if (d < half - 0.035) return ribs(q);
    if (d < half + 0.01) return OUT;
    if (d < half + 0.1 && q.ny < 0.55) c = mul(c, 1.12);                       // lapela, mais clara
    if (Math.abs(d - (half + 0.1)) < 0.02 && q.ny < 0.55) c = mul(SUIT, 0.65);
    // bolso do peito do lado esquerdo dele (direita da tela)
    if (q.nx > 0.7 && q.nx < 0.86 && Math.abs(q.ny - 0.55) < 0.045) c = mul(SUIT, 0.6);
    return c;
  }
  function jacketLow(q) {
    let c = suitTone(q);
    if (q.f === 'bottom') return mul(SUIT, 0.55);
    if (q.f !== 'north') return c;
    // fechamento do paletó um pouco fora do centro, botão, abas dos bolsos
    if (Math.abs(q.nx - 0.47) < 0.02) c = OUT;
    if (Math.abs(q.nx - 0.5) < 0.03 && q.ny < 0.09) c = [120, 150, 230];
    for (const cx of [0.2, 0.8]) {
      if (Math.abs(q.nx - cx) < 0.12 && Math.abs(q.ny - 0.4) < 0.06) c = mul(SUIT, 1.15);
      if (Math.abs(q.nx - cx) < 0.12 && Math.abs(q.ny - 0.47) < 0.02) c = OUT;
    }
    if (q.ny > 0.92) c = mul(SUIT, 0.62);
    return c;
  }
  function pants(q) {
    let c = suitTone(q, -0.05);
    if (q.f === 'north' && Math.abs(q.nx - 0.5) < 0.04) c = mul(SUIT, 0.7);       // vinco da calça
    return c;
  }
  const sock = q => cel([150, 130, 220], 0.6 - 0.4 * q.ny, 0.85, 1.05);
  function shoe(q) {
    if (q.f === 'bottom' || q.ny > 0.8) return [20, 18, 22];
    let c = [36, 34, 42];
    if (q.f === 'top' && n3(q.ax * 0.5, 0, q.az * 0.5, 53) > 0.66) c = [98, 100, 118];
    return c;
  }
  function skin(q, k = 0) {
    const v = 0.6 - 0.35 * q.ny + 0.3 * (n3(q.ax * 0.25, q.ay * 0.25, q.az * 0.25, 54) - 0.5) + (q.f === 'top' ? 0.2 : 0) + k;
    return cel(SKIN, v, 0.8, 1.1);
  }
  function fist(q) {
    let c = skin(q);
    // nós dos dedos e vãos entre os dedos, na frente e embaixo
    if (q.f === 'north' && q.ny > 0.25) {
      const g = fr(q.px / 1.75);
      if (g < 0.16 && q.ny > 0.45) c = mul(SKIN, 0.62);
      if (Math.abs(q.ny - 0.4) < 0.08) c = mul(SKIN, 1.08);
    }
    if (q.f === 'west' || q.f === 'east') if (q.ny > 0.35 && q.nz < 0.3) c = mul(c, 0.85);
    return c;
  }
  function face(q) {
    let c = skin(q, 0.08);
    if (q.f !== 'north') return c;
    const x = Math.floor(q.px), y = Math.floor(q.py);
    const stitch = [150, 92, 70];
    if (y === 4 && x >= 2 && x <= 6) c = stitch;                                       // costura na testa
    if (y === 3 && (x === 2 || x === 4 || x === 6)) c = stitch;
    if ((y === 5 && x >= 1 && x <= 3) || (y === 5 && x >= 6 && x <= 8)) c = [40, 26, 18]; // sobrancelhas pesadas
    if (y === 6 && (x === 2 || x === 3 || x === 6 || x === 7)) c = [250, 250, 246];   // olhos brancos grandes
    if (y === 7 && (x === 2 || x === 7)) c = [250, 250, 246];
    if (y === 7 && (x === 3 || x === 6)) c = [30, 24, 22];                             // pupila pequena, olhar pra baixo
    if (y === 8 && (x === 4 || x === 5)) c = mul(SKIN, 0.8);                           // nariz
    if ((x === 7 && y === 8) || (x === 8 && y === 9)) c = stitch;                      // costura da bochecha esquerda dele
    if (x === 8 && y === 8) c = mul(stitch, 1.2);
    if (y === 9 && (x === 4 || x === 5)) c = [150, 82, 70];                            // boquinha emburrada
    if (y === 10 && x >= 3 && x <= 6) c = mul(SKIN, 0.9);                              // queixo
    return c;
  }
  function hair(q) {
    if (q.f === 'bottom') return null;
    const col = Math.floor(q.f === 'north' || q.f === 'south' ? q.px : q.pz);
    // franja serrilhada: cada mecha desce até uma altura própria
    const reach = 0.42 + 0.5 * n3(col * 1.9, q.f.length, 0, 55) * (col % 2 ? 1 : 0.55);
    if ((q.f === 'north') && q.ny > reach) return null;
    if ((q.f === 'west' || q.f === 'east') && q.ny > (q.nz < 0.6 ? reach : 1)) return null;
    const v = n3(q.ax * 0.6, q.ay * 0.3, q.az * 0.6, 56);
    return v > 0.75 ? [62, 66, 90] : [16, 16, 22];
  }
  const ear = q => skin(q, -0.05);

  function arm(side) {
    const s = side, N = s < 0 ? 'D' : 'E';
    return {
      name: 'braco' + N, pivot: [s * 14.5, -21, 0], rot: [0, 0, -s * 0.12], boxes: [
        { x: -4.5, y: -2.5, z: -5, w: 9, h: 13, d: 10, p: q => suitTone(q, 0.05) },
      ], kids: [{
        name: 'ante' + N, pivot: [0, 10.5, 0], boxes: [
          { x: -4, y: 0, z: -4.5, w: 8, h: 9, d: 9, p: suitTone },
          { x: -3.25, y: 8.5, z: -3.5, w: 6.5, h: 1.5, d: 7, p: ribs },
          { x: -3.75, y: 10, z: -4, w: 7.5, h: 6.5, d: 8, p: fist },
        ],
      }],
    };
  }
  function leg(side) {
    const N = side < 0 ? 'D' : 'E';
    return {
      name: 'perna' + N, pivot: [side * 6, 12, 0], boxes: [
        { x: -5, y: 0, z: -5, w: 10, h: 9.5, d: 10, p: pants },
        { x: -3.5, y: 9.5, z: -3.5, w: 7, h: 1, d: 7, p: sock },
        { x: -4, y: 10, z: -6.5, w: 8, h: 2, d: 10.5, p: shoe },
      ],
    };
  }

  const def = {
    name: 'raiz', pivot: [0, 0, 0], kids: [
      {
        name: 'corpo', pivot: [0, 12, 0], boxes: [
          { x: -14, y: -12, z: -9, w: 28, h: 13, d: 17, p: jacketLow },
          { x: -12.5, y: -24, z: -8, w: 25, h: 12, d: 15, p: jacketUp },
          { x: -5, y: -27, z: -5, w: 10, h: 3, d: 9, p: ribs },
        ], kids: [
          {
            name: 'cabeca', pivot: [0, -26, -1], boxes: [
              { x: -5, y: -11, z: -5, w: 10, h: 11, d: 10, p: face },
              { x: -5.5, y: -12.5, z: -5.5, w: 11, h: 7, d: 11, p: hair },
              { x: -6, y: -6.5, z: -0.5, w: 1, h: 3, d: 2, p: ear },
              { x: 5, y: -6.5, z: -0.5, w: 1, h: 3, d: 2, p: ear },
            ],
          },
          arm(-1), arm(1),
        ],
      },
      leg(-1), leg(1),
    ],
  };

  function anim(P, t, mode, a) {
    const S = Math.sin, br = S(t * 1.1);
    const r = (n, x, y, z) => { const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    r('anteD', -0.25, 0, 0); r('anteE', -0.25, 0, 0);
    r('corpo', 0.05 + br * 0.025, 0, 0);
    P.corpo.obj.position.y += br * 0.35;
    r('bracoD', -br * 0.04, 0, br * 0.03); r('bracoE', -br * 0.04, 0, -br * 0.03);
    r('cabeca', -0.05, S(t * 0.35) * 0.12, S(t * 0.5) * 0.04);
    if (mode === 'andar') {
      const w = t * 3.2, sw = S(w);
      r('pernaD', sw * 0.45, 0, 0); r('pernaE', -sw * 0.45, 0, 0);
      r('bracoD', -sw * 0.35, 0, 0); r('bracoE', sw * 0.35, 0, 0);
      r('corpo', 0.04, sw * 0.07, -sw * 0.07);           // pesado: rola o corpo de um lado pro outro
      P.corpo.obj.position.y -= Math.abs(sw) * 1.1;
      r('cabeca', 0, -sw * 0.05, sw * 0.05);
    }
    if (mode === 'atacar') {
      // ergue os dois punhos juntos (0..0.45), martela o chão (0.45..0.53), fica curvado (0.53..0.75), levanta
      const sm = ENG.smooth;
      const up = a < 0.45 ? sm(0, 0.45, a) : a < 0.53 ? 1 - sm(0.45, 0.53, a) : 0;
      const hit = a < 0.45 ? 0 : a < 0.53 ? sm(0.45, 0.53, a) : a < 0.75 ? 1 : 1 - sm(0.75, 1, a);
      for (const [n, s] of [['bracoD', -1], ['bracoE', 1]]) {
        r(n, -2.9 * up - 1.25 * hit, 0, s * 0.45 * up + s * 0.32 * hit);
      }
      r('anteD', -0.4 * up + 0.2 * hit, 0, 0); r('anteE', -0.4 * up + 0.2 * hit, 0, 0);
      r('corpo', -0.22 * up + 0.55 * hit, 0, 0);
      r('cabeca', 0.2 * up - 0.35 * hit, 0, 0);
      P.corpo.obj.position.y += 2 * hit;
      r('pernaD', -0.25 * hit, 0, 0.12 * hit); r('pernaE', -0.25 * hit, 0, -0.12 * hit);
    }
  }

  window.MOBS = window.MOBS || [];
  window.MOBS.push({
    id: 'androide_8', nome: 'Androide 8', bioma: 'Norte Gelado', def, anim, attackLen: 2.0, cam: 8.5, lookY: 1.8, refX: 2.8,
    info: {
      'Tamanho': '3,1 blocos',
      'Vida': '300',
      'Jeito': 'Neutro e manso. Anda devagar e fica olhando o jogador. Se um yeti te atacar perto dele, ele vem te defender.',
      'Ataque': 'Só se você bater nele: ergue os dois punhos e martela o chão. Acerta 3 blocos em volta, racha o chão e deixa atordoado por 30 ticks.',
      'Solta': 'Nada se morrer. Se ficar seu amigo (te viu bater num yeti perto dele), deixa um presente de vez em quando.',
    },
  });
})();
