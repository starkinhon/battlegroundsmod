// Salamandra Venenosa (Vila da Chuva). Forte, rara, tier 6. Vive nos Canos Gigantes embaixo da Torre (estrutura).
// Golpes: Névoa Venenosa (enche o peito e solta gás numa área de 7 blocos: veneno forte enquanto você estiver dentro)
// e Rabada (gira o corpo e varre com o rabo: joga longe quem estiver atrás ou do lado).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  function painters(v) {
    // pele lisa e úmida: base escura, manchas de alerta sorteadas pelo ruído, brilho de umidade no topo
    const skin = s => q => {
      let c = cel(v.skin, n3(q.ax * 0.2, q.ay * 0.2, q.az * 0.2, s + v.seed), 0.84, 1.1);
      const spot = n3(q.ax * 0.22 + 7, q.ay * 0.22, q.az * 0.22, s + 3 + v.seed);
      if (spot > 0.68 && q.f !== 'bottom') c = v.spot;
      if (spot > 0.64 && spot <= 0.68 && q.f !== 'bottom') c = mix(c, v.spot, 0.5);
      if (q.f === 'top' && n3(q.ax * 0.9, 0, q.az * 0.9, s + 6) > 0.74) c = mix(c, [220, 230, 240], 0.3);
      if (q.f === 'bottom') c = v.belly;
      return c;
    };
    const body = skin(1), leg = skin(2), tail = skin(3);
    const head = q => {
      let c = skin(4)(q);
      if ((q.f === 'west' || q.f === 'east') && Math.abs(q.ny - 0.3) < 0.12 && q.nz < 0.3 && q.nz > 0.1) c = [230, 200, 60];   // olho amarelo
      if ((q.f === 'west' || q.f === 'east') && Math.abs(q.ny - 0.3) < 0.04 && q.nz < 0.24 && q.nz > 0.16) c = [16, 12, 14];
      return c;
    };
    const jaw = q => (q.f === 'top' ? [130, 60, 80] : skin(5)(q));
    // guelras de fora, franjadas, cor da toxina
    const gill = q => (q.ny > 0.6 + 0.4 * n3(Math.floor(q.px) * 1.9, 0, 0, 7) ? null : cel(v.gill, n3(q.ax, q.ay, q.az, 8), 0.82, 1.14));
    return { body, leg, tail, head, jaw, gill };
  }
  function defOf(v, low) {
    const P = painters(v);
    const leg = (s, front) => ({
      name: 'perna' + (front ? 'F' : 'T') + (s < 0 ? 'D' : 'E'), pivot: [s * 7, 2, front ? -8 : 9], rot: [0, s * (front ? 0.4 : -0.4), s * 0.6],
      boxes: [{ x: s < 0 ? -8 : 0, y: -2, z: -2, w: 8, h: 4, d: 4, p: P.leg }],
      kids: [{ name: 'pe' + (front ? 'F' : 'T') + (s < 0 ? 'D' : 'E'), pivot: [s * 7.6, 0, 0], rot: [0, 0, -s * 1.15], boxes: [{ x: s < 0 ? -2 : -1, y: -1.5, z: -2.2, w: 3, h: 9, d: 4.4, p: P.leg }].concat(low ? [] : [{ x: s < 0 ? -2.6 : -1.6, y: 7, z: -4, w: 4.2, h: 1.4, d: 6, p: P.leg }]) }],
    });
    let tailNode = null;
    const NTL = low ? 2 : 4;
    for (let i = NTL - 1; i >= 0; i--) {
      const w = 9 - i * 2, len = low ? 14 : 8;
      tailNode = { name: 'rabo' + i, pivot: [0, i ? 0 : 0.5, i ? len : 13], boxes: [{ x: -w / 2, y: -w * 0.35, z: 0, w, h: w * 0.75, d: len, p: P.tail }], kids: tailNode ? [tailNode] : [] };
    }
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'corpo', pivot: [0, 14, 0], boxes: [{ x: -8, y: -5, z: -12, w: 16, h: 9, d: 25, p: P.body }],
        kids: [leg(-1, true), leg(1, true), leg(-1, false), leg(1, false), tailNode, {
          name: 'cabeca', pivot: [0, -1, -12], boxes: [{ x: -7.5, y: -4.5, z: -11, w: 15, h: 5.5, d: 11, p: P.head }],
          kids: [{ name: 'mandibula', pivot: [0, 1, -0.5], boxes: [{ x: -7, y: 0, z: -10.4, w: 14, h: 3, d: 10.4, p: P.jaw }] }]
            .concat(low ? [] : [-1, 1].map(s => ({ name: 'guelra' + (s < 0 ? 'D' : 'E'), pivot: [s * 7.5, -1.5, -1.5], rot: [0, s * 0.5, s * -0.3], boxes: [{ x: s < 0 ? -5 : 0, y: -3, z: -0.4, w: 5, h: 6, d: 0.8, p: P.gill }] }))),
        }],
      }],
    };
  }

  // marcha: pernas cruzadas (frente direita com trás esquerda), o corpo serpenteia junto
  const STEP = [[0, -0.4], [0.5, 0.4]];
  function crawl(r, ph, amt) {
    for (const [k, o] of [['FD', 0], ['TE', 0], ['FE', 0.5], ['TD', 0.5]]) {
      r('perna' + k, 0, loop(STEP, ph + o) * amt * (k.endsWith('D') ? -1 : 1), -0.25 * Math.max(0, seq([[0, 0], [0.25, 1], [0.5, 0]], fr(ph + o))) * amt * (k.endsWith('D') ? -1 : 1));
    }
    r('corpo', 0, 0.1 * loop([[0, -1], [0.5, 1]], ph) * amt, 0);
    for (let i = 0; i < 4; i++) r('rabo' + i, 0, -0.18 * loop([[0, -1], [0.5, 1]], ph - 0.15 * (i + 1)) * amt, 0);
    r('cabeca', 0, -0.06 * loop([[0, -1], [0.5, 1]], ph) * amt, 0);
  }

  function anim(P, t, mode, a, obj, ent, ctx) {
    const r = (k, x, y, z) => { if (!P[k]) return; const o = P[k].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const gills = k => { r('guelraD', 0, -0.4 * k, 0); r('guelraE', 0, 0.4 * k, 0); };
    if (mode === 'parado') {
      // deitada, respirando pelas guelras (abre e fecha em dois tempos)
      gills(loop([[0, 0], [0.2, 1], [0.35, 1], [0.6, 0]], t * 0.35));
      for (let i = 0; i < 4; i++) r('rabo' + i, 0, 0.08 * (i + 1) * 0.5, 0);
      return;
    }
    if (mode === 'andar') {
      crawl(r, t * 0.7, 1);
      return;
    }
    const R = ctx.ref;
    if (mode === 'gas') {
      const T = t % 5.0;
      R.place(0, -6, 0);
      // levanta a frente, incha (as guelras abrem: aviso) e solta o gás numa onda baixa que se espalha
      const rear = seq([[0.1, 0], [0.8, 1, 'out'], [1.0, 1], [1.2, 0.3, 'snap'], [3.4, 0.3], [3.9, 0]], T);
      const puff = seq([[0.2, 0], [0.9, 1, 'in'], [1.05, 0, 'snap']], T);
      const mouth = seq([[0.95, 0], [1.05, 1, 'snap'], [3.0, 1], [3.4, 0]], T);
      r('corpo', -0.22 * rear, 0, 0);
      P.corpo.obj.position.y -= 1.5 * rear;
      r('cabeca', 0.25 * rear * (1 - mouth) - 0.1 * mouth, 0, 0); r('mandibula', 0.6 * mouth, 0, 0);
      r('pernaFD', 0, 0, -0.3 * rear); r('pernaFE', 0, 0, 0.3 * rear);
      gills(Math.max(puff, 0.4 * mouth));
      P.corpo.obj.scale.set(1 + 0.12 * puff, 1 + 0.12 * puff, 1);
      if (T > 1.0) {
        // três camadas de gás com sementes, tamanhos e atrasos diferentes, rente ao chão
        [[0, 0.5, 2.1, 0], [1.3, 0.45, 2.7, 0.25], [-1.1, 0.4, 2.5, 0.45]].forEach(([dx, y, rad, lag], i) => {
          const age = (T - 1.0 - lag) / 3.6;
          ctx.get('gas' + i, FX.volume).set(new THREE.Vector3(dx, y, -3.6 - 1.4 * FX.clamp01(age * 2)), rad, age, { seed: 4 + i * 1.7, dens: 3.4, rise: 0.2, squash: 0.5, freq: 1.3, smoke: v_gas(ent) });
        });
        const k = seq([[1.4, 0], [1.8, 1], [3.8, 1], [4.4, 0]], T);
        ctx.poison(k);
        R.lean(0.15 * k + 0.04 * Math.sin(T * 7) * k);
      }
      return;
    }
    if (mode === 'rabada') {
      const T = t % 3.4;
      R.place(2.6, 1.0, 0);
      // olha pra trás por cima do ombro (aviso), gira o corpo e varre com o rabo
      const look = seq([[0.1, 0], [0.5, 1, 'out'], [0.65, 1]], T);
      const sw = seq([[0.65, 0], [0.85, 1, 'snap'], [1.6, 1], [2.4, 0, 'io']], T);
      r('cabeca', 0, -0.5 * look * (1 - sw), 0);
      r('corpo', 0, -0.25 * look * (1 - sw) + 0.55 * sw, 0);
      ['rabo0', 'rabo1', 'rabo2', 'rabo3'].forEach((k, i) => r(k, 0, 0.25 * look * (1 - sw) + (0.35 + 0.12 * i) * sw, 0));
      crawl(r, T * 0.5, 0.25);
      if (T > 0.8) {
        const h = T - 0.8;
        if (h < 0.3) ctx.shake(1 - h / 0.3);
        if (h < 0.4) ctx.slash(new THREE.Vector3(2.4, 0.7, 1.0), h / 0.35);
        ctx.get('respingo', FX.volume).set(new THREE.Vector3(1.8, 0.25, 1.4), 1.0, h / 1.0, { seed: 5.9, dens: 3, rise: 0.4, squash: 0.5, freq: 1.8, smoke: [0.5, 0.52, 0.56] });
        const k = seq([[0.8, 0], [1.5, 1, 'out']], T);
        R.place(2.6 + 3.2 * k, 1.0 - 0.8 * k, 1.0 * Math.sin(Math.PI * k));
        R.spin(seq([[0.8, 0], [1.5, 2.4, 'out']], T));
        R.lean(-0.9 * Math.sin(Math.PI * FX.clamp01(h / 1.0)));
      }
      return;
    }
  }
  const v_gas = ent => ent.meta.v.gas;

  window.MOBS = window.MOBS || [];
  const SK = [
    { nome: 'Negra', seed: 1, skin: [44, 40, 50], spot: [224, 120, 40], belly: [110, 84, 70], gill: [200, 70, 120], gas: [0.52, 0.32, 0.6] },
    { nome: 'Pântano', seed: 2, skin: [56, 70, 52], spot: [210, 196, 70], belly: [130, 120, 86], gill: [160, 200, 70], gas: [0.45, 0.62, 0.28] },
  ];
  window.MOBS.push({
    id: 'salamandra_venenosa', nome: 'Salamandra Venenosa', bioma: 'Vila da Chuva', tier: 'Forte, rara · tier 6',
    variants: SK.map(v => ({ nome: v.nome, v, def: defOf(v, false), low: defOf(v, true) })),
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['gas', 'Névoa venenosa'], ['rabada', 'Rabada']],
    cam: 9, lookY: 0.9, lookZ: { gas: -3, rabada: 0.5 }, camFar: { gas: 12, rabada: 10 }, camYaw: { parado: -0.8, gas: -1.0, rabada: -0.4 }, refX: 3.6,
    info: {
      'Tamanho': '4,5 blocos de comprimento com o rabo, 1,3 de altura',
      'Vida': '420 (tier 6)',
      'Jeito': 'Uma por região, só nos Canos Gigantes embaixo da Torre. Dorme de dia; luta ou barulho perto acordam ela. Volta 2 dias depois de morrer.',
      'Névoa venenosa': 'Levanta a frente e incha, as guelras abrem (0,9 s de aviso), e solta uma onda de gás rente ao chão numa área de 7 blocos que fica 3 s: veneno forte enquanto você estiver dentro. Subir em algo alto escapa.',
      'Rabada': 'Olha pra trás por cima do ombro (0,5 s de aviso) e varre com o rabo: quem estiver atrás ou do lado voa 3 blocos.',
      'Solta': 'Glândula venenosa grande, pele de salamandra (armadura resistente a veneno); raro: pergaminho de invocação da salamandra.',
    },
  });
})();
