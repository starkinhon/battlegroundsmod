// Bora (Terra Sagrada de Karin). Referências: referencias/mobs-db/bora/.
'use strict';
(() => {
  const { n3, mix, mul, cel } = ENG;
  const SKIN = [134, 82, 46];
  const fr = x => x - Math.floor(x);

  function skin(q, k = 0) {
    // luz de cima e da frente, sombra embaixo; manchas de luz do músculo pelo ruído
    const v = 0.62 - 0.4 * q.ny + 0.32 * (n3(q.ax * 0.22, q.ay * 0.22, q.az * 0.22, 31) - 0.5) + (q.f === 'top' ? 0.25 : 0) + k;
    return cel(SKIN, v, 0.78, 1.16);
  }
  function chest(q) {
    let c = skin(q, 0.05);
    if (q.f !== 'north') return c;
    const x = q.px, y = q.py, W = q.b.w;
    // linha de baixo dos peitorais, curva, e o vão do meio
    const side = x < W / 2 ? x : W - x;
    const under = 5.2 - 0.18 * Math.pow(side - 3.5, 2) * 0.35;
    if (Math.abs(y - under) < 0.55 && side > 0.8) c = mul(SKIN, 0.62);
    if (Math.abs(x - W / 2) < 0.5 && y > 1.5) c = mul(SKIN, 0.7);
    if (y >= 3 && y < 4 && (Math.floor(x) === 3 || Math.floor(x) === W - 4)) c = mul(SKIN, 0.55);   // mamilos
    // cicatriz em X no peito direito dele (esquerda da tela)
    const sx = x - 3.6, sy = y - 2.4;
    if ((Math.abs(sx - sy) < 0.5 || Math.abs(sx + sy) < 0.5) && Math.abs(sx) < 1.9 && Math.abs(sy) < 1.9) c = [96, 48, 70];
    // colar de dentes: cordão em V e cada dente com seu tamanho
    const cx = Math.abs(x - W / 2), vy = 0.35 + cx * 0.28;
    if (Math.abs(y - vy) < 0.5 && cx < 4.5) c = [232, 222, 196];
    const tooth = Math.floor(x);
    const len = { 4: 1.4, 5: 1.9, 6: 2.6, 7: 3.0, 8: 2.4, 9: 1.8, 10: 1.3 }[tooth];
    if (len && y > vy && y < vy + len && Math.abs(fr(x) - 0.5) < 0.45 - (y - vy) / len * 0.3) c = [246, 240, 222];
    return c;
  }
  function abs_(q) {
    let c = skin(q);
    if (q.f !== 'north') return c;
    const x = q.px, y = q.py, W = q.b.w;
    if (Math.abs(x - W / 2) < 0.45) c = mul(SKIN, 0.68);
    for (const ly of [2.3, 4.6]) if (Math.abs(y - ly) < 0.4 && Math.abs(x - W / 2) < 3.2) c = mul(SKIN, 0.7);
    if ((Math.abs(x - 1.6) < 0.45 || Math.abs(x - (W - 1.6)) < 0.45) && y < 5.5) c = mul(SKIN, 0.75);   // oblíquos
    return c;
  }
  function face(q) {
    let c = skin(q, 0.1);
    if (q.f !== 'north') return c;
    const x = Math.floor(q.px), y = Math.floor(q.py);
    if (y === 3 && (x === 1 || x === 2 || x === 5 || x === 6)) c = [30, 22, 18];      // sobrancelhas grossas
    if (y === 4 && (x === 2 || x === 5)) c = [246, 246, 240];                        // olhos brancos de anime
    if (y === 4 && (x === 1 || x === 6)) c = mul(SKIN, 0.75);
    if (y === 5 && (x === 3 || x === 4)) c = mul(SKIN, 0.82);
    if (y === 4 && x === 1) c = [176, 46, 38];                                       // risco na bochecha direita dele
    if (x === 6 && (y === 5 || y === 6)) c = [205, 56, 44];                          // riscos na bochecha esquerda dele
    if (x === 7 && y === 5) c = [190, 48, 40];
    if (y === 6 && (x === 3 || x === 4)) c = [70, 34, 24];                           // boca firme
    return c;
  }
  function hair(q, cap) {
    // na frente da cabeça o cabelo só cobre a testa e as laterais (o rosto fica livre)
    if (cap && q.f === 'north' && q.ny > 0.36 && q.nx > 0.12 && q.nx < 0.88) return null;
    if (cap && q.f === 'bottom') return null;
    if (cap && (q.f === 'west' || q.f === 'east') && q.ny > 0.5 && q.nz < 0.35) return null;
    const v = n3(q.ax * 1.1, q.ay * 0.18, q.az * 1.1, 33);
    let c = v > 0.72 ? [52, 60, 88] : v < 0.3 ? [10, 10, 14] : [24, 24, 32];
    if (!cap && q.ny > 0.82 && n3(q.ax * 2, 0, q.az * 2, 34) > 0.55) return null;    // pontas desfiadas
    return c;
  }
  function pants(q) {
    const v = 0.6 - 0.35 * q.ny + 0.3 * (n3(q.ax * 0.15, q.ay * 0.15, q.az * 0.15, 35) - 0.5) + (q.f === 'top' ? 0.2 : 0);
    let c = cel([214, 180, 110], v, 0.8, 1.1);
    return c;
  }
  function fringe(q) {
    // franja de couro: cada fio com comprimento e tom próprios
    const k = Math.floor(q.ay * 1.0 + q.az * 0.0), strand = Math.floor(q.az * 2 + q.ay * 1.3);
    const len = 0.45 + 0.55 * n3(strand * 1.7, k * 0.3, q.ax, 37);
    if (q.nz > len) return null;
    return mix([150, 112, 60], [196, 158, 92], n3(strand * 2.1, q.ay * 0.2, 0, 38));
  }
  function moc(q) {
    if (q.f === 'bottom') return [96, 70, 40];
    let c = cel([206, 172, 108], 0.6 - 0.3 * q.ny + 0.3 * (n3(q.ax * 0.4, q.ay * 0.4, q.az * 0.4, 39) - 0.5), 0.82, 1.1);
    if (q.f === 'top' && Math.abs(q.nx - 0.5) < 0.1 && q.nz < 0.6) c = [130, 92, 50];  // costura
    return c;
  }
  function flap(q) {
    // tanga preta com borda vermelha e os desenhos da referência
    const x = Math.floor(q.px), y = Math.floor(q.py), W = q.b.w, H = q.b.h;
    if (q.f !== 'north' && q.f !== 'south') return [150, 30, 28];
    if (x === 0 || x === W - 1 || y === H - 1) return [196, 40, 34];
    const red = [204, 44, 36];
    if (y === 2 && (x === 1 || x === 3)) return red;
    if (y === 4 && (x === 1 || x === 3)) return [230, 70, 56];
    if ((y === 6 && x === 2) || (y === 7 && x >= 1 && x <= 3)) return red;
    return mul([32, 24, 26], 0.9 + 0.2 * n3(q.ax, q.ay, q.az, 40));
  }
  const cord = q => [210, 186, 132];
  function band(q) {
    const v = n3(q.ax * 0.6, q.ay * 0.6, q.az * 0.6, 41);
    return cel([238, 224, 184], v, 0.86, 1.04);
  }
  function tassel(q) {
    const s = Math.floor(q.pz * 2 + q.px * 2);
    if (q.ny > 0.4 + 0.6 * n3(s * 1.9, 0, q.ax, 42)) return null;
    return mix([214, 190, 140], [240, 226, 186], n3(s * 2.3, 1, 0, 43));
  }
  function wood(q) {
    const g = fr(q.az * 0.35 + n3(q.ax * 2, q.ay * 2, q.az * 0.1, 44) * 1.5);
    return g < 0.25 ? [96, 58, 30] : [138, 88, 46];
  }
  const rope = q => fr((q.az * 1.0 + q.ay * 0.7 + q.ax * 0.7) / 1.4) < 0.5 ? [124, 76, 120] : [88, 50, 92];
  function tip(q) {
    // ponta em folha: larga no meio, fina na base e no bico
    const half = Math.abs(q.nx - 0.5) * 2, z = 1 - q.nz;
    const prof = z < 0.45 ? 0.3 + 0.7 * z / 0.45 : 1 - (z - 0.45) / 0.55;
    if (half > prof + 0.04) return null;
    const lit = q.nx < 0.5 ? [232, 236, 242] : [168, 176, 190];
    return Math.abs(q.nx - 0.5) < 0.08 ? [120, 128, 142] : lit;
  }
  function feather(q) {
    if (q.ny < 0.22) return [214, 50, 40];
    const edge = Math.abs(q.nz - 0.5) * 2;
    if (edge > 1 - q.ny * 0.25) return null;
    return Math.abs(q.nz - 0.5) < 0.15 ? [200, 196, 190] : [248, 248, 244];
  }

  function arm(side) {
    const s = side, N = s < 0 ? 'D' : 'E';
    const outer = s < 0 ? -3.6 : 3.1;
    const kids = [];
    if (s < 0) kids.push({
      name: 'lanca', pivot: [0, 9, -0.5], boxes: [
        { x: -0.5, y: -0.5, z: -26, w: 1, h: 1, d: 40, p: wood },
        { x: -1, y: -1, z: -3, w: 2, h: 2, d: 5, p: rope },
        { x: -0.75, y: -0.75, z: -27, w: 1.5, h: 1.5, d: 2, p: rope },
      ], kids: [{ name: 'ponta', pivot: [0, 0, -27], boxes: [{ x: -2, y: -0.5, z: -8, w: 4, h: 1, d: 8, p: tip }] }],
    });
    return {
      name: 'braco' + N, pivot: [s * 9.5, -15.5, 0], rot: [0, 0, -s * 0.1], boxes: [
        { x: -3.5, y: -2, z: -3.5, w: 7, h: 8, d: 7, p: q => skin(q, 0.05) },
        { x: -3.75, y: 1.5, z: -3.75, w: 7.5, h: 1.5, d: 7.5, p: band },
        { x: outer, y: 3, z: -1.5, w: 0.5, h: 4, d: 3, p: tassel },
      ], kids: [{
        name: 'ante' + N, pivot: [0, 6, 0], boxes: [
          { x: -3, y: 0, z: -3, w: 6, h: 7, d: 6, p: skin },
          { x: -2.75, y: 7, z: -3.25, w: 5.5, h: 3, d: 6, p: q => skin(q, -0.05) },
        ], kids,
      }],
    };
  }
  function leg(side) {
    const N = side < 0 ? 'D' : 'E';
    return {
      name: 'perna' + N, pivot: [side * 3.4, 11, 0], boxes: [
        { x: -3.5, y: 0, z: -3.5, w: 7, h: 11, d: 7, p: pants },
        { x: side < 0 ? -4.5 : 3.5, y: 1, z: -2, w: 1, h: 10, d: 4, p: fringe },
        { x: -3.2, y: 11, z: -4.6, w: 6.4, h: 2, d: 8, p: moc },
      ],
    };
  }

  const def = {
    name: 'raiz', pivot: [0, 0, 0], kids: [
      {
        name: 'corpo', pivot: [0, 11, 0], boxes: [
          { x: -5.5, y: -3, z: -3.5, w: 11, h: 3, d: 7, p: pants },
          { x: -5.75, y: -3.2, z: -3.75, w: 11.5, h: 0.6, d: 7.5, p: cord },
          { x: -5.5, y: -10, z: -3.5, w: 11, h: 7, d: 7, p: abs_ },
          { x: -8, y: -17.5, z: -4.2, w: 16, h: 7.5, d: 8.4, p: chest },
          { x: -2.5, y: -19.5, z: -2, w: 5, h: 2, d: 4, p: skin },
        ], kids: [
          {
            name: 'cabeca', pivot: [0, -19, -0.6], boxes: [
              { x: -4, y: -8, z: -4, w: 8, h: 8, d: 8, p: face },
              { x: -4.5, y: -8.6, z: -4.5, w: 9, h: 4.5, d: 9, p: q => hair(q, true) },
              { x: -4.5, y: -5, z: 1.6, w: 9, h: 8, d: 3, p: q => hair(q, false) },
            ], kids: [
              { name: 'rabo', pivot: [0, -4, 4.6], rot: [0.25, 0, 0], boxes: [{ x: -1, y: 0, z: -1, w: 2, h: 9, d: 2, p: q => hair(q, false) }] },
              { name: 'pena', pivot: [3.6, -6.5, 3.2], rot: [-0.35, 0, 0.5], boxes: [{ x: -0.5, y: -6, z: -1, w: 1, h: 6, d: 2, p: feather }] },
            ],
          },
          arm(-1), arm(1),
          { name: 'tanga', pivot: [0, -2.6, -3.6], boxes: [{ x: -2.5, y: 0, z: -0.6, w: 5, h: 9, d: 1, p: flap }] },
        ],
      },
      leg(-1), leg(1),
    ],
  };

  function anim(P, t, mode, a) {
    const S = Math.sin, br = S(t * 1.4);
    const r = (n, x, y, z) => { const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    // guarda: lança na diagonal, punho esquerdo fechado
    r('bracoD', -0.35, 0.2, 0); r('anteD', -0.85, 0, 0); r('lanca', 0.25, 0, 0);
    r('bracoE', -0.1, 0, 0); r('anteE', -0.35, 0, 0);
    r('corpo', br * 0.02, 0, 0);
    P.corpo.obj.position.y += br * 0.2;
    r('cabeca', -br * 0.02, S(t * 0.5) * 0.2, 0);
    r('rabo', S(t * 1.1) * 0.08, 0, S(t * 0.8) * 0.06);
    r('tanga', S(t * 1.2) * 0.04, 0, 0);
    if (mode === 'andar') {
      const w = t * 5.5, sw = S(w);
      r('pernaD', sw * 0.6, 0, 0); r('pernaE', -sw * 0.6, 0, 0);
      r('bracoE', -sw * 0.5, 0, 0);
      r('corpo', 0.05, -sw * 0.08, 0);
      P.corpo.obj.position.y -= Math.abs(S(w)) * 0.6;
      r('tanga', -0.2 - Math.abs(sw) * 0.25, 0, 0);
      r('rabo', 0.3, 0, -sw * 0.15);
    }
    if (mode === 'atacar') {
      // puxa a lança pra trás (0..0.35), estoca de uma vez (0.35..0.43), segura (0.43..0.62), volta
      const sm = ENG.smooth;
      const pull = a < 0.35 ? sm(0, 0.35, a) : a < 0.43 ? 1 - sm(0.35, 0.43, a) : 0;
      const hit = a < 0.35 ? 0 : a < 0.43 ? sm(0.35, 0.43, a) : a < 0.62 ? 1 : 1 - sm(0.62, 1, a);
      r('bracoD', 0.5 * pull - 1.1 * hit, -0.3 * pull + 0.1 * hit, 0);
      r('anteD', 0.4 * pull + 0.85 * hit, 0, 0);
      r('lanca', -0.1 * pull + 1.2 * hit, 0, 0);
      r('corpo', 0.18 * hit - 0.05 * pull, 0.35 * pull - 0.3 * hit, 0);
      r('pernaD', -0.55 * hit, 0, 0); r('pernaE', 0.35 * hit, 0, 0);
      P.corpo.obj.position.z -= 2.5 * hit;
      P.corpo.obj.position.y += 1.2 * hit;
      r('bracoE', 0.4 * hit, 0, 0.2 * hit);
      r('cabeca', -0.15 * hit, 0.25 * hit, 0);
    }
  }

  window.MOBS = window.MOBS || [];
  window.MOBS.push({
    id: 'bora', nome: 'Bora', bioma: 'Terra Sagrada de Karin', def, anim, attackLen: 1.5, cam: 7, lookY: 1.5,
    info: {
      'Tamanho': '2,5 blocos',
      'Vida': '180',
      'Jeito': 'Neutro, guardião da Torre Karin. Quem chega perto com arma na mão ouve um aviso; se insistir, ele ataca.',
      'Ataque': 'Estocada de lança com 4 blocos de alcance e um passo à frente. Também arremessa a lança e puxa de volta pela corda.',
      'Solta': 'Pena do Bora (material) e, se ele te respeitar, abre caminho pra subir a torre.',
    },
  });
})();
