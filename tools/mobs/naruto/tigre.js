// Tigre da Floresta (Floresta da Morte). Médio, tier 8. Solitário: rodeia antes de atacar, foge com pouca vida.
// Golpes: Salto com patada (abre a guarda) e Rugido (assusta mobs fracos e faz você recuar).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const L = 22, W = 9, HB = 8.5, LEG = 9, HS = 8.5;

  function painters(v) {
    const S = v.seed;
    // listras: cada uma com largura, inclinação e bifurcação próprias, vindas do ruído (nada repetido)
    const stripe = q => {
      const warp = n3(q.ax * 0.12, q.ay * 0.12, q.az * 0.12, 3 + S) * 2.2;
      const k = fr(q.az * 0.105 + warp + Math.abs(q.ax) * 0.02);
      const wd = 0.1 + 0.14 * n3(q.az * 0.2, q.ay * 0.1, 0, 4 + S);
      return k < wd && n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, 5 + S) > 0.25;
    };
    const coat = (q, belly) => {
      const lowSide = (q.f === 'west' || q.f === 'east') && q.ny > 0.68 + 0.1 * n3(q.az * 0.4, 0, 0, 6);
      if (belly && (q.f === 'bottom' || lowSide)) return cel(v.belly, n3(q.ax, q.ay, q.az, 7), 0.86, 1.05);
      let c = cel(v.fur, n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, 8 + S), 0.84, 1.1);
      if (q.f === 'top') c = mul(c, 1.04);
      if (stripe(q)) c = v.stripe;
      return c;
    };
    const P = {};
    P.body = q => coat(q, true);
    P.leg = q => {
      if (q.ny > 0.5 && (q.f === 'north' || q.f === 'south' || q.f === 'bottom')) return cel(v.belly, n3(q.ax, q.ay, q.az, 9), 0.86, 1.05);
      return coat(q, false);
    };
    P.paw = q => (q.f === 'bottom' ? [58, 44, 40] : cel(v.belly, n3(q.ax, q.ay, q.az, 10), 0.86, 1.05));
    P.head = q => {
      if (q.f === 'bottom') return cel(v.belly, 0.5, 0.86, 1.05);
      let c = coat(q, false);
      if (q.f !== 'north') { if ((q.f === 'west' || q.f === 'east') && q.ny > 0.55) c = cel(v.belly, n3(q.ax, q.ay, q.az, 11), 0.86, 1.05); return c; }
      const x = q.nx, y = q.ny;
      // máscara branca em volta dos olhos e bochechas, olhos âmbar, linhas pretas da testa
      if (y > 0.3 && (x < 0.4 || x > 0.6)) c = mix(c, v.belly, 0.25);
      for (const ex of [0.27, 0.73]) {
        const dx = (x - ex) * HS, dy = (y - 0.48) * HS;
        if (Math.abs(dx) < 0.9 && Math.abs(dy) < 0.55) c = [236, 170, 40];
        if (Math.abs(dx) < 0.3 && Math.abs(dy) < 0.55) c = [18, 14, 10];
        if (Math.abs(dx) < 1.1 && dy < -0.55 && dy > -1.1) c = v.belly;                         // sobrancelha branca
      }
      if (y < 0.3 && Math.abs(x - 0.5) < 0.3 && fr(x * 6 + n3(x * 3, y * 3, 0, 12) * 0.5) < 0.22) c = v.stripe;
      return c;
    };
    P.muzzle = q => {
      let c = cel(v.belly, n3(q.ax, q.ay, q.az, 13), 0.88, 1.04);
      if (q.f === 'north' && q.ny < 0.42 && Math.abs(q.nx - 0.5) < 0.22) c = [196, 110, 110];   // nariz
      if (q.f === 'north' && q.ny > 0.48 && q.ny < 0.6) c = [60, 40, 36];
      if (q.f === 'bottom') c = [170, 60, 70];
      return c;
    };
    P.jaw = q => (q.f === 'top' ? (q.nx < 0.15 || q.nx > 0.85 ? [244, 240, 228] : [160, 54, 66]) : cel(v.belly, 0.5, 0.86, 1.05));
    P.ear = q => (q.f === 'north' && q.ny > 0.3 && q.nx > 0.2 && q.nx < 0.8 ? v.belly : (q.f === 'south' && q.ny < 0.6 ? [240, 236, 226] : v.stripe));
    P.tail = q => {
      let c = coat(q, false);
      if (q.ay > 0 && fr(q.az * 0.25 + q.ay * 0.1) < 0.3) c = v.stripe;                          // aneis do rabo
      return c;
    };
    P.tailTip = () => v.stripe;
    return P;
  }

  function defOf(v, low) {
    const P = painters(v);
    const cy = 24 - LEG - HB / 2 + 0.5, lw = 3.4;
    const leg = (n, x, z, back) => low ? {
      name: 'perna' + n, pivot: [x, HB / 2 - 1, z], boxes: [{ x: -lw / 2, y: 0, z: -lw / 2, w: lw, h: LEG + 0.5, d: lw, p: q => (q.ny > 0.9 ? P.paw(q) : P.leg(q)) }],
    } : {
      name: 'perna' + n, pivot: [x, HB / 2 - 1, z], boxes: [{ x: -lw / 2 - 0.3, y: -1, z: -lw / 2 - 0.4, w: lw + 0.6, h: LEG * 0.55 + 1, d: lw + 0.8, p: P.leg }],
      kids: [{
        name: 'canela' + n, pivot: [0, LEG * 0.55, back ? 0.4 : 0], boxes: [{ x: -lw / 2, y: 0, z: -lw / 2, w: lw, h: LEG * 0.45 - 0.5, d: lw, p: P.leg }],
        kids: [{ name: 'pata' + n, pivot: [0, LEG * 0.45 - 0.5, 0], boxes: [{ x: -lw / 2 - 0.4, y: 0, z: -lw / 2 - 1, w: lw + 0.8, h: 1, d: lw + 1.4, p: P.paw }] }],
      }],
    };
    const lx = W / 2 - lw / 2;
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'corpo', pivot: [0, cy, 0], boxes: [
          { x: -W / 2, y: -HB / 2, z: -L / 2, w: W, h: HB, d: L * 0.55, p: P.body },
          { x: -W / 2 + 0.4, y: -HB / 2 + 0.4, z: -L / 2 + L * 0.55 - 0.5, w: W - 0.8, h: HB - 0.6, d: L * 0.45 + 0.5, p: P.body },
        ],
        kids: [
          {
            name: 'cabeca', pivot: [0, -HB / 2 + 1.2, -L / 2], boxes: [
              { x: -HS / 2, y: -HS * 0.6, z: -HS * 0.85, w: HS, h: HS * 0.85, d: HS * 0.95, p: P.head },
              { x: -HS * 0.28, y: -HS * 0.18, z: -HS * 0.85 - 2.4, w: HS * 0.56, h: HS * 0.3, d: 2.6, p: P.muzzle },
            ].concat(low ? [] : [
              { x: -HS / 2 - 0.8, y: -HS * 0.1, z: -HS * 0.7, w: 1.2, h: HS * 0.38, d: HS * 0.45, p: P.head },          // suíças
              { x: HS / 2 - 0.4, y: -HS * 0.1, z: -HS * 0.7, w: 1.2, h: HS * 0.38, d: HS * 0.45, p: P.head },
            ]),
            kids: (low ? [] : [{ name: 'mandibula', pivot: [0, HS * 0.12, -HS * 0.8], boxes: [{ x: -HS * 0.25, y: 0, z: -2.3, w: HS * 0.5, h: 1.6, d: 2.5, p: P.jaw }] }]).concat([
              { name: 'orelhaD', pivot: [-HS * 0.32, -HS * 0.6, -HS * 0.4], boxes: [{ x: -1.2, y: -2, z: -0.5, w: 2.4, h: 2, d: 1, p: P.ear }] },
              { name: 'orelhaE', pivot: [HS * 0.32, -HS * 0.6, -HS * 0.4], boxes: [{ x: -1.2, y: -2, z: -0.5, w: 2.4, h: 2, d: 1, p: P.ear }] },
            ]),
          },
          leg('FD', -lx, -L / 2 + 3, false), leg('FE', lx, -L / 2 + 3, false), leg('TD', -lx, L / 2 - 3, true), leg('TE', lx, L / 2 - 3, true),
          {
            name: 'rabo', pivot: [0, -HB / 2 + 1, L / 2 - 0.5], rot: [0.95, 0, 0], boxes: [{ x: -1, y: 0, z: -1, w: 2, h: 8, d: 2, p: P.tail }],
            kids: low ? [] : [{ name: 'rabo2', pivot: [0, 8, 0], rot: [-0.6, 0, 0], boxes: [{ x: -0.9, y: 0, z: -0.9, w: 1.8, h: 7, d: 1.8, p: P.tail }, { x: -1, y: 5, z: -1, w: 2, h: 2.4, d: 2, p: P.tailTip }] }],
          },
        ],
      }],
    };
  }

  const STALK_U = [[0, -0.36], [0.2, -0.16], [0.5, 0.32], [0.62, 0.26], [0.82, -0.14]];
  const STALK_L = [[0, 0.04], [0.3, 0.1], [0.52, 0.22], [0.68, 0.85], [0.86, 0.3]];
  function anim(P, t, mode, a, obj, ent, ctx) {
    const r = (k, x, y, z) => { if (!P[k]) return; const o = P[k].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const tailSway = loop([[0, -0.25], [0.3, 0.2], [0.55, 0.3], [0.8, -0.15]], t / 3.3);
    if (mode === 'parado') {
      r('rabo', 0, tailSway, 0); r('rabo2', 0, tailSway * 0.8, 0);
      const br = loop([[0, 0], [0.4, 1], [0.999, 0]], t / 3.6);
      P.corpo.obj.position.y += 0.15 * br;
      r('orelhaD', seq([[0, 0], [2.1, 0], [2.16, -0.4, 'snap'], [2.4, 0]], t % 4.4), 0, 0);
      return;
    }
    if (mode === 'andar') {
      // passo de caça: corpo baixo, ombros que sobem e descem, cabeça firme
      const ph = t * 0.85;
      P.corpo.obj.position.y += 1.2;
      for (const [n, off] of [['FD', 0], ['TE', 0.06], ['FE', 0.5], ['TD', 0.56]]) {
        r('perna' + n, loop(STALK_U, ph + off), 0, 0);
        r('canela' + n, (n[0] === 'T' ? -1 : 1) * loop(STALK_L, ph + off), 0, 0);
      }
      r('corpo', 0, 0, loop([[0, 0.03], [0.5, -0.03]], ph));
      r('cabeca', 0.1, 0, -loop([[0, 0.03], [0.5, -0.03]], ph));
      r('rabo', 0.2, tailSway * 0.5, 0);
      return;
    }
    const R = ctx.ref;
    if (mode === 'salto') {
      const T = t % 3.0;
      R.place(0, -5.4, 0);
      const crouch = seq([[0.1, 0], [0.75, 1, 'out'], [0.82, 1], [0.9, 0, 'in']], T);
      const wig = seq([[0.45, 0], [0.55, 0.15], [0.62, -0.15], [0.7, 0.12], [0.78, 0]], T);  // rebola antes de pular
      const leap = seq([[0.86, 0], [1.2, 1, 'out']], T), air = Math.sin(Math.PI * FX.clamp01((T - 0.86) / 0.34));
      const swipe = seq([[1.12, 0], [1.18, 1, 'snap'], [1.5, 1], [1.9, 0]], T);
      const back = seq([[2.0, 0], [2.6, 1, 'io']], T);
      obj.position.z -= (3.9 * leap) * (1 - back);
      obj.position.y += 1.1 * air;
      P.corpo.obj.position.y += 2.8 * crouch;
      r('corpo', -0.08 * crouch - 0.3 * air + 0.15 * swipe * (1 - back), 0, wig);
      r('pernaFD', -0.7 * crouch - 1.3 * air - 1.6 * swipe * (1 - back), 0, 0.3 * swipe); r('canelaFD', 1.0 * crouch + 0.4 * swipe, 0, 0);
      r('pernaFE', -0.7 * crouch - 1.3 * air, 0, 0); r('canelaFE', 1.0 * crouch, 0, 0);
      r('pernaTD', 0.8 * crouch + 0.9 * air, 0, 0); r('canelaTD', -1.2 * crouch, 0, 0);
      r('pernaTE', 0.8 * crouch + 0.9 * air, 0, 0); r('canelaTE', -1.2 * crouch, 0, 0);
      r('cabeca', 0.25 * crouch + 0.2 * air, 0, 0);
      r('mandibula', 0.6 * (air + swipe) * (1 - back), 0, 0);
      r('rabo', -0.3 * crouch + 0.4 * air, 0, 0);
      if (T > 1.15) {
        const k = seq([[1.15, 0], [1.5, 1, 'out']], T);
        R.place(0, -5.4 - 1.8 * k, 0);
        R.lean(-1.25 * seq([[1.15, 0], [1.45, 1, 'out'], [2.4, 1], [2.9, 0]], T));
        if (T < 1.35) ctx.shake(1 - (T - 1.15) / 0.2);
        if (T < 1.5) ctx.claw(new THREE.Vector3(0.1, 1.1, -5.0), (T - 1.15) / 0.35);
      }
      return;
    }
    if (mode === 'rugido') {
      const T = t % 3.2;
      R.place(0, -4.5, 0);
      const draw = seq([[0.2, 0], [0.7, 1, 'out']], T);
      const roar = seq([[0.7, 0], [0.78, 1, 'snap'], [1.9, 1], [2.4, 0]], T);
      P.corpo.obj.position.y += 1.2 * draw * (1 - roar) - 0.6 * roar;
      r('corpo', 0.12 * draw * (1 - roar) - 0.18 * roar, 0, 0);
      r('cabeca', 0.3 * draw * (1 - roar) - 0.45 * roar, 0, 0);
      r('mandibula', 1.05 * roar, 0, 0);
      r('orelhaD', 0.6 * roar, 0, -0.3 * roar); r('orelhaE', 0.6 * roar, 0, 0.3 * roar);
      r('pernaFD', -0.25 * roar, 0, 0); r('pernaFE', -0.25 * roar, 0, 0);
      r('rabo', 0.5 * roar, 0, 0);
      if (T > 0.75 && T < 2.0) {
        ctx.roarRing(new THREE.Vector3(0, 0.06, -0.8), (T - 0.75) / 1.1);
        const dust = ctx.get('rugido-poeira', FX.volume);
        dust.set(new THREE.Vector3(0, 0.15, -1.5), 1.6, (T - 0.75) / 1.25, { seed: 8.8, dens: 3.2, rise: 0.15, squash: 0.35, freq: 1.6, smoke: [0.44, 0.4, 0.32] });
        R.lean(-0.3 * Math.sin(Math.PI * FX.clamp01((T - 0.8) / 1.0)));
        R.place(0, -4.5 - 0.8 * seq([[0.8, 0], [1.3, 1, 'out']], T), 0);
        ctx.shake(0.35 * (1 - FX.clamp01((T - 0.75) / 1.0)));
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const SK = [
    { nome: 'Laranja', seed: 1, fur: [214, 120, 40], stripe: [30, 22, 20], belly: [244, 236, 220] },
    { nome: 'Escuro', seed: 2, fur: [176, 84, 34], stripe: [24, 16, 14], belly: [232, 220, 200] },
  ];
  window.MOBS.push({
    id: 'tigre', nome: 'Tigre da Floresta', bioma: 'Floresta da Morte', tier: 'Médio · tier 8',
    variants: SK.map(v => ({ nome: v.nome, v, def: defOf(v, false), low: defOf(v, true) })),
    anim, modes: [['parado', 'Parado'], ['andar', 'Rondar'], ['salto', 'Salto com patada'], ['rugido', 'Rugido']],
    cam: 6, lookY: 0.9, lookZ: { salto: -2.6, rugido: -2.2 }, camFar: { salto: 8, rugido: 7 }, camYaw: { salto: -1.3, rugido: -0.9 }, refX: 2.8,
    info: {
      'Tamanho': '1,6 bloco de altura, 2,7 de comprimento (sem o rabo)',
      'Vida': '110 (tier 8)',
      'Jeito': 'Hostil e solitário. Rodeia você a uns 8 blocos antes de atacar; com pouca vida, foge.',
      'Salto com patada': 'Agacha e rebola (0,7 s de aviso), salta 4 blocos e dá uma patada que abre a guarda.',
      'Rugido': 'Ruge forte: ninken e mobs fracos perto fogem, e você é empurrado um pouco pra trás.',
      'Solta': 'Couro listrado e carne.',
    },
  });
})();
