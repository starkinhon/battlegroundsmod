// Garça de Papel (Vila da Chuva). Fraca, tier 10. Origami de papel de chakra que voa em bando entre as torres.
// Golpes: Mergulho (dobra as asas, mergulha e explode como papel-bomba pequeno) e Corte de Papel (passa rasante: a borda da asa corta).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  function painters(v) {
    // papel: branco quente, vincos das dobras em linhas retas, fibra do papel no ruído, um selo de tinta sorteado
    const paper = s => q => {
      let c = cel(v.paper, n3(q.ax * 1.4, q.ay * 1.4, q.az * 1.4, s + v.seed), 0.94, 1.03);
      const crease = Math.min(Math.abs(q.nx - q.nz), Math.abs(q.nx + q.nz - 1));
      if (crease < 0.04) c = mul(c, 0.86);
      if (q.f === 'bottom') c = mul(c, 0.86);
      return c;
    };
    const body = paper(1), wing = paper(2);
    const seal = q => {
      let c = body(q);
      if (q.f === 'top' && Math.abs(q.nx - 0.5) < 0.18 && Math.abs(q.nz - 0.45) < 0.12) c = v.ink;
      return c;
    };
    const wingP = q => {
      let c = wing(q);
      if (q.f === 'top' && fr(q.ax * 0.5 + q.az * 0.25) < 0.05) c = mix(c, v.ink, 0.35);              // linhas de chakra escritas
      return c;
    };
    return { body, seal, wing: wingP };
  }
  function defOf(v, low) {
    const P = painters(v);
    const wing = s => ({
      name: s < 0 ? 'asaD' : 'asaE', pivot: [s * 1.6, -0.4, -0.6], rot: [0, 0, s * -0.15],
      boxes: [{ x: s < 0 ? -7 : 0, y: -0.15, z: -2.6, w: 7, h: 0.3, d: 5.2, p: P.wing }],
      kids: low ? [] : [{ name: s < 0 ? 'pontaD' : 'pontaE', pivot: [s * 7, 0, 0], rot: [0, s * 0.2, 0], boxes: [{ x: s < 0 ? -6 : 0, y: -0.12, z: -2, w: 6, h: 0.24, d: 3.6, p: P.wing }] }],
    });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'corpo', pivot: [0, 14, 0], boxes: [{ x: -1.6, y: -1.2, z: -3, w: 3.2, h: 2.6, d: 6, p: P.seal }, { x: -1.1, y: 1.4, z: -2.2, w: 2.2, h: 1.6, d: 4.4, p: P.body }],
        kids: [wing(-1), wing(1),
          { name: 'pescoco', pivot: [0, -0.6, -2.6], rot: [-0.75, 0, 0], boxes: [{ x: -0.4, y: -0.4, z: -6, w: 0.8, h: 0.8, d: 6, p: P.body }], kids: [{ name: 'bico', pivot: [0, 0, -6], rot: [1.0, 0, 0], boxes: [{ x: -0.35, y: -0.35, z: -2, w: 0.7, h: 0.7, d: 2, p: P.body }] }] },
          { name: 'cauda', pivot: [0, -0.6, 2.6], rot: [0.75, 0, 0], boxes: [{ x: -0.4, y: -0.4, z: 0, w: 0.8, h: 0.8, d: 6, p: P.body }] }],
      }],
    };
  }

  // batida de asa: desce rápido, sobe devagar planando
  const FLAP = [[0, -0.55], [0.28, 0.65], [0.36, 0.7], [0.9, -0.4]];
  function flap(r, ph, amt) {
    const k = loop(FLAP, ph) * amt;
    r('asaD', 0, 0, k); r('asaE', 0, 0, -k);
    r('pontaD', 0, 0, 0.6 * loop(FLAP, ph - 0.08) * amt); r('pontaE', 0, 0, -0.6 * loop(FLAP, ph - 0.08) * amt);
  }
  const confetti = seed => FX.debris(40, seed, [[240, 234, 220], [226, 218, 200], [250, 248, 240], [180, 60, 50]]);

  function anim(P, t, mode, a, obj, ent, ctx) {
    const r = (k, x, y, z) => { if (!P[k]) return; const o = P[k].obj.rotation; o.x += x; o.y += y; o.z += z; };
    if (mode === 'parado') {
      // pairando no lugar: sobe na batida, cai um pouco no planeio
      obj.position.y += 1.0 + 0.12 * loop([[0, -1], [0.3, 0.8], [0.9, -0.6]], t * 0.9);
      flap(r, t * 0.9, 1);
      r('corpo', -0.12, 0, 0);
      return;
    }
    if (mode === 'andar') {
      // voo em círculo largo, inclinando pra dentro da curva
      const a1 = t * 0.6;
      obj.position.set(Math.cos(a1) * 1.6, 1.5 + 1.2, Math.sin(a1) * 1.6 - 1);
      obj.rotation.set(0, -a1, 0);
      r('corpo', 0, 0, -0.35);
      flap(r, t * 1.3, 0.8);
      return;
    }
    const R = ctx.ref;
    if (mode === 'mergulho') {
      const T = t % 3.6;
      R.place(0, -3.4, 0);
      // no alto, para, dobra as asas (o selo acende) e cai em linha reta no alvo
      const rise = seq([[0, 0], [0.6, 1, 'out']], T);
      const fold = seq([[0.6, 0], [0.85, 1, 'snap']], T);
      const dive = seq([[0.9, 0], [1.3, 1, 'in']], T);
      const boom = T > 1.3;
      obj.visible = !boom;
      const start = new THREE.Vector3(0, 2.4 + 1.2 * rise, 0.5), end = new THREE.Vector3(0, 1.2, -3.2);
      const p = start.clone().lerp(end, dive);
      obj.position.set(p.x, p.y, p.z);
      const ang = Math.atan2(start.y - end.y, start.z - end.z);
      obj.rotation.set(-ang * dive - 0.3 * fold * (1 - dive), 0, 0);
      flap(r, T * 2.2, 1 - fold);
      r('asaD', 0, -0.9 * fold, 1.2 * fold); r('asaE', 0, 0.9 * fold, -1.2 * fold);
      ctx.ember(new THREE.Vector3(p.x, p.y + 0.1, p.z), T);
      if (boom) {
        const age = (T - 1.3) / 1.2;
        ctx.get('estouro', FX.volume).set(new THREE.Vector3(0, 1.3, -3.3), 0.9, age, { fire: 1, seed: 2.6, dens: 12, rise: 0.5, smoke: [0.2, 0.18, 0.2] });
        ctx.get('papel', () => confetti(121)).at(new THREE.Vector3(0, 1.3, -3.3), T - 1.3);
        ctx.flash(new THREE.Vector3(0, 1.4, -3.3), Math.max(0, 1 - (T - 1.3) / 0.3));
        if (T < 1.6) ctx.shake(1 - (T - 1.3) / 0.3);
        const k = seq([[1.3, 0], [1.8, 1, 'out']], T);
        R.place(0, -3.4 - 1.3 * k, 0.5 * Math.sin(Math.PI * k));
        R.lean(-0.7 * Math.sin(Math.PI * FX.clamp01((T - 1.3) / 0.8)));
      }
      return;
    }
    if (mode === 'corte') {
      const T = t % 3.0;
      R.place(0, -2.4, 0);
      // rasante: vem de trás, passa de lado rente ao ombro e a ponta da asa corta
      const pass = seq([[0.3, 0], [1.1, 1, 'lin']], T);
      const x = 0.55, z = 2.5 - 8 * pass, y = 2.5 - 1.1 * Math.sin(Math.PI * pass);
      obj.position.set(x, y, z);
      obj.rotation.set(0.15 - 0.5 * Math.cos(Math.PI * pass) * 0.5, 0, -0.9 * Math.sin(Math.PI * pass));
      flap(r, T * 1.8, 1 - Math.sin(Math.PI * pass) * 0.9);
      if (pass > 0.55 && pass < 0.75) ctx.slash(new THREE.Vector3(0.2, 1.4, -2.4), (pass - 0.55) / 0.2);
      if (pass > 0.6) R.lean(0.25 * Math.sin(Math.PI * FX.clamp01((pass - 0.6) / 0.4)));
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const SK = [
    { nome: 'Branca', seed: 1, paper: [240, 236, 226], ink: [176, 50, 46] },
    { nome: 'Azulada', seed: 2, paper: [214, 226, 236], ink: [50, 60, 120] },
  ];
  window.MOBS.push({
    id: 'garca_papel', nome: 'Garça de Papel', bioma: 'Vila da Chuva', tier: 'Fraco · tier 10',
    variants: SK.map(v => ({ nome: v.nome, v, def: defOf(v, false), low: defOf(v, true) })),
    anim, modes: [['parado', 'Pairar'], ['andar', 'Voar'], ['mergulho', 'Mergulho'], ['corte', 'Corte de papel']],
    cam: 5, lookY: 1.6, lookZ: { mergulho: -1.6, corte: -1, andar: -1 }, camFar: { mergulho: 8, corte: 7.5, andar: 6 }, camYaw: { mergulho: -1.25, corte: -0.6 }, refX: 2,
    info: {
      'Tamanho': '1,2 bloco de asa a asa',
      'Vida': '20 (tier 10): um golpe rasga',
      'Jeito': 'Rara, em bando de 3 a 5 entre as torres. Ignora você até levar dano ou você chegar perto do topo de uma torre.',
      'Mergulho': 'Sobe, dobra as asas (o selo de tinta acende) e cai reto em você: explode como papel-bomba pequeno.',
      'Corte de papel': 'Rasante de lado rente ao seu ombro: a ponta da asa corta.',
      'Solta': 'Papel de chakra (usado pra fazer papel-bomba).',
    },
  });
})();
