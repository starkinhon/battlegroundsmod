// Inseto Gigante (Monte Myoboku). Fraco, tier 10. Besouro do tamanho de um porco: os sapos caçam e comem.
// Golpes: Chifrada (raspa a pata, corre e levanta você no chifre) e Voo Zumbido (abre a carapaça, zumbe e mergulha em você).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  function painters(v) {
    // carapaça brilhante: cor muda com o ângulo da face (iridescente), sulcos longitudinais
    const shell = s => q => {
      const ang = q.f === 'top' ? 0 : q.f === 'north' || q.f === 'south' ? 0.5 : 1;
      let c = mix(v.a, v.b, FX.clamp01(ang * 0.6 + 0.5 * n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + v.seed)));
      c = cel(c, n3(q.ax * 0.6, q.ay * 0.6, q.az * 0.6, s + 3), 0.8, 1.2);
      if (q.f === 'top' && n3(q.ax * 0.5, 0, q.az * 0.5, s + 5) > 0.7) c = mix(c, [240, 250, 255], 0.35);  // reflexo
      if (q.f === 'bottom') c = [40, 32, 30];
      return c;
    };
    const elytra = q => { let c = shell(1)(q); if (q.f === 'top' && fr(q.nx * 4) < 0.08) c = mul(c, 0.7); if (q.f === 'top' && (q.nx < 0.05)) c = mul(c, 0.5); return c; };
    const pron = shell(2);
    const head = q => { let c = shell(3)(q); if ((q.f === 'west' || q.f === 'east') && Math.abs(q.ny - 0.45) < 0.2 && q.nz < 0.45 && q.nz > 0.15) c = [20, 16, 18]; return c; };
    const horn = q => { let c = shell(4)(q); if (q.ny < 0.2) c = mul(c, 1.2); return c; };
    const leg = q => { let c = cel([46, 36, 34], n3(q.ax, q.ay * 2, q.az, 6), 0.8, 1.2); if (fr(q.ay * 0.7) < 0.15) c = [80, 66, 58]; return c; };
    const belly = q => cel([60, 46, 40], n3(q.ax, q.ay, q.az * 2, 7), 0.8, 1.2);
    // asa de membrana: quase transparente, veias escuras
    const wing = q => { if (fr(q.nx * 5 + q.nz * 0.6) < 0.06 || fr(q.nz * 3) < 0.05) return [80, 70, 60]; return n3(q.ax, 0, q.az, 8) > 0.88 ? null : [200, 196, 176]; };
    return { elytra, pron, head, horn, leg, belly, wing };
  }
  function defOf(v, low) {
    const P = painters(v);
    const leg = (s, i) => {
      const z = [-3, 1, 5][i];
      return {
        name: 'perna' + i + (s < 0 ? 'D' : 'E'), pivot: [s * 4.5, 1, z], rot: [0, s * (i - 1) * 0.5, s * 0.7],
        boxes: [{ x: s < 0 ? -5 : 0, y: -0.6, z: -0.6, w: 5, h: 1.2, d: 1.2, p: P.leg }],
        kids: low ? [] : [{ name: 'canela' + i + (s < 0 ? 'D' : 'E'), pivot: [s * 5, 0, 0], rot: [0, 0, -s * 1.5], boxes: [{ x: -0.5, y: 0, z: -0.5, w: 1, h: 4.6, d: 1, p: P.leg }] }],
      };
    };
    const legs = [];
    for (let i = 0; i < 3; i++) legs.push(leg(-1, i), leg(1, i));
    const elytron = s => ({
      name: s < 0 ? 'elitroD' : 'elitroE', pivot: [s * 0.1, -4.5, -1.5],
      boxes: [{ x: s < 0 ? -5.4 : 0, y: -0.6, z: 0, w: 5.4, h: 4.6, d: 11.5, p: P.elytra }],
    });
    const memb = s => ({ name: s < 0 ? 'membD' : 'membE', pivot: [s * 1, -4.4, -0.5], boxes: [{ x: s < 0 ? -12 : 0, y: -0.1, z: 0, w: 12, h: 0.2, d: 5, p: P.wing }] });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'corpo', pivot: [0, 17, 0], boxes: [{ x: -5, y: -4, z: -1.5, w: 10, h: 5, d: 11, p: P.belly }],
        kids: legs.concat([elytron(-1), elytron(1)], low ? [] : [memb(-1), memb(1)], [{
          name: 'pronoto', pivot: [0, -1.5, -1.5], boxes: [{ x: -5, y: -3.5, z: -5, w: 10, h: 5, d: 5, p: P.pron }],
          kids: [{
            name: 'cabeca', pivot: [0, 0, -5], boxes: [{ x: -3, y: -2.5, z: -3.5, w: 6, h: 4, d: 3.5, p: P.head }],
            kids: [{ name: 'chifre', pivot: [0, -1.5, -3.2], rot: [0.5, 0, 0], boxes: [{ x: -1, y: -1, z: -5, w: 2, h: 2, d: 5, p: P.horn }],
              kids: low ? [] : [{ name: 'ponta', pivot: [0, 0, -5], rot: [-0.9, 0, 0], boxes: [{ x: -0.7, y: -0.7, z: -3, w: 1.4, h: 1.4, d: 3, p: P.horn }, { x: -2, y: -0.5, z: -3.2, w: 4, h: 1, d: 1, p: P.horn }] }] },
            ...[-1, 1].map(s => ({ name: s < 0 ? 'manD' : 'manE', pivot: [s * 2, 1, -3.4], rot: [0, s * -0.3, 0], boxes: [{ x: -0.5, y: -0.5, z: -2, w: 1, h: 1, d: 2, p: P.leg }] }))],
          }],
        }]),
      }],
    };
  }

  // tripé alternado: frente e trás de um lado com o meio do outro
  const STEP = [[0, -0.35], [0.5, 0.35]];
  function walk(r, ph, amt) {
    for (const [k, o] of [['0D', 0], ['2D', 0], ['1E', 0], ['0E', 0.5], ['2E', 0.5], ['1D', 0.5]]) {
      const s = k.endsWith('D') ? -1 : 1;
      r('perna' + k, 0, loop(STEP, ph + o) * amt * s, -0.3 * seq([[0, 0], [0.25, 1], [0.5, 0]], fr(ph + o)) * amt * s);
    }
  }
  // asas abertas: carapaça levanta pra frente/fora, membrana sai de baixo e bate rápido
  function wings(r, open, t, buzz) {
    r('elitroD', -0.5 * open, 0, 0.7 * open); r('elitroE', -0.5 * open, 0, -0.7 * open);
    const fl = buzz * Math.sin(t * 60);
    r('membD', 0, 0.9 * (1 - open), -0.2 * open + 0.6 * fl); r('membE', 0, -0.9 * (1 - open), 0.2 * open - 0.6 * fl);
  }

  function anim(P, t, mode, a, obj, ent, ctx) {
    const r = (k, x, y, z) => { if (!P[k]) return; const o = P[k].obj.rotation; o.x += x; o.y += y; o.z += z; };
    if (mode === 'parado') {
      wings(r, 0, t, 0);
      // antenas não tem: mexe as mandíbulas em estalos e às vezes levanta o chifre
      const m = seq([[0, 0], [0.1, 1], [0.2, 0], [0.3, 1], [0.4, 0]], fr(t * 0.6));
      r('manD', 0, 0.3 * m, 0); r('manE', 0, -0.3 * m, 0);
      r('cabeca', -0.2 * seq([[0, 0], [0.6, 0], [0.65, 1], [0.85, 1], [0.9, 0]], fr(t * 0.25)), 0, 0);
      return;
    }
    if (mode === 'andar') {
      wings(r, 0, t, 0);
      walk(r, t * 1.6, 1);
      obj.position.z -= fr(t * 0.25) * 2.5 - 1;
      return;
    }
    const R = ctx.ref;
    if (mode === 'chifrada') {
      const T = t % 3.4;
      wings(r, 0, t, 0);
      // abaixa o chifre e raspa a pata da frente duas vezes (aviso), corre e levanta você
      const lower = seq([[0.05, 0], [0.3, 1], [1.1, 1], [1.25, -0.6, 'snap'], [1.8, -0.6], [2.3, 0]], T);
      const scrape = T < 1.0 ? seq([[0, 0], [0.4, 1], [0.5, 0]], fr(T * 2.4)) : 0;
      const run = seq([[0.95, 0], [1.25, 1, 'in']], T);
      r('pronoto', 0.25 * Math.max(0, lower), 0, 0); r('cabeca', 0.2 * Math.max(0, lower) - 0.5 * Math.max(0, -lower), 0, 0);
      r('perna0D', 0, -0.6 * scrape, 0.3 * scrape);
      if (T > 0.95 && T < 1.3) walk(r, T * 9, 1.2);
      obj.position.z -= 2.6 * run;
      R.place(0, -3.3, 0);
      if (T > 1.2) {
        const k = seq([[1.2, 0], [1.45, 1, 'out'], [1.9, 0.6], [2.3, 0, 'in']], T);
        R.place(0, -3.3 - 0.8 * seq([[1.2, 0], [2.0, 1]], T), 1.5 * k);
        R.lean(-0.7 * k); R.spin(0.6 * seq([[1.2, 0], [2.3, 1]], T));
        if (T < 1.4) { ctx.shake(0.5); ctx.slash(new THREE.Vector3(0, 0.7, -3.2), (T - 1.2) / 0.2); }
      }
      return;
    }
    if (mode === 'voo') {
      const T = t % 3.8;
      R.place(0, -4, 0);
      // abre a carapaça (aviso com o zumbido), sobe em espiral curta, mergulha de chifre em você
      const open = seq([[0.05, 0], [0.3, 1, 'snap'], [2.6, 1], [2.9, 0]], T);
      wings(r, open, t, open);
      const up = seq([[0.35, 0], [1.1, 1, 'out'], [1.4, 1]], T);
      const dive = seq([[1.4, 0], [1.75, 1, 'in']], T);
      const back = seq([[2.2, 0], [2.9, 1, 'io']], T);
      const hover = new THREE.Vector3(0.6 * Math.sin(T * 3), 2.6 * up, -0.8 * up);
      const hit = new THREE.Vector3(0, 0.9, -3.7);
      const p = hover.clone().lerp(hit, dive);
      p.lerp(new THREE.Vector3(0, 0, -5.5), back);
      obj.position.add(p);
      obj.rotation.x = -0.6 * up * (1 - dive) + 0.55 * dive * (1 - back);
      for (let i = 0; i < 3; i++) { r('perna' + i + 'D', 0, 0, 0.5 * open); r('perna' + i + 'E', 0, 0, -0.5 * open); }
      if (T > 1.72 && T < 2.2) {
        const h = T - 1.72;
        ctx.slash(new THREE.Vector3(0, 1.1, -3.9), h / 0.3);
        if (h < 0.2) ctx.shake(0.6);
        R.lean(-0.6 * Math.sin(Math.PI * FX.clamp01(h / 0.45)));
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const SK = [
    { nome: 'Verde', seed: 1, a: [40, 120, 70], b: [40, 80, 130] },
    { nome: 'Bronze', seed: 2, a: [140, 92, 40], b: [110, 40, 60] },
    { nome: 'Preto-azul', seed: 3, a: [34, 40, 70], b: [80, 40, 110] },
  ];
  window.MOBS.push({
    id: 'inseto_gigante', nome: 'Inseto Gigante', bioma: 'Monte Myoboku', tier: 'Fraco · tier 10',
    variants: SK.map(v => ({ nome: v.nome, v, def: defOf(v, false), low: defOf(v, true) })),
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['chifrada', 'Chifrada'], ['voo', 'Voo Zumbido']],
    cam: 3.6, lookY: 0.5, lookYs: { voo: 1.3 }, lookZ: { chifrada: -1.8, voo: -2 }, camFar: { chifrada: 6, voo: 7 }, camYaw: { chifrada: -1.3, voo: -1.2 }, refX: 1.8,
    info: {
      'Tamanho': '1,1 bloco de comprimento com o chifre',
      'Vida': '30 (tier 10)',
      'Jeito': 'Hostil só se você chegar a 3 blocos. Grupos de 2 a 4 embaixo das flores gigantes. Os sapos ninja caçam eles: se um sapo estiver perto, o besouro foge.',
      'Chifrada': 'Abaixa o chifre e raspa a pata duas vezes (0,9 s de aviso), corre 3 blocos e te levanta 1,5 bloco.',
      'Voo Zumbido': 'Abre a carapaça com um zumbido (aviso), sobe 2,5 blocos e mergulha de chifre em você.',
      'Solta': 'Carapaça iridescente (tinta/armadura leve), chifre de besouro. Sapos ninja aceitam como presente.',
    },
  });
})();
