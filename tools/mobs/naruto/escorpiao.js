// Escorpião de Areia (Deserto do Vento). Fraco, tier 9. Fica enterrado com só o ferrão de fora.
// Golpes: Emboscada (sai da areia embaixo de você e prende com a pinça) e Ferroada (arma o rabo, crava: veneno 5 s,
// e se enterra de novo).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const NT = 5;

  function painters(v) {
    // carapaça: placas com borda clara, faixas escuras sorteadas pelo ruído, brilho de verniz no topo
    const shell = s => q => {
      let c = cel(v.shell, n3(q.ax * 0.35, q.ay * 0.35, q.az * 0.35, s + v.seed), 0.8, 1.14);
      if (n3(q.ax * 0.12 + 4, 0, q.az * 0.5, s + 3 + v.seed) > 0.62) c = mix(c, v.stripe, 0.7);
      if (q.f === 'top' && (q.nz < 0.1 || q.nz > 0.9)) c = mix(c, v.edge, 0.5);
      if (q.f === 'top' && Math.abs(q.nx - 0.3) < 0.07 && q.nz > 0.2 && q.nz < 0.8) c = mix(c, [255, 244, 220], 0.35);
      if (q.f === 'bottom') c = v.under;
      if (q.f !== 'top' && q.f !== 'bottom') c = mul(c, 0.82 + 0.18 * (1 - q.ny));
      return c;
    };
    const body = shell(10), tail = shell(20), claw = shell(30);
    const head = q => {
      let c = body(q);
      if (q.f === 'top') {
        // dois olhos no meio e três de cada lado na frente
        const x = q.nx, z = q.nz;
        if (Math.abs(z - 0.3) < 0.06 && Math.abs(Math.abs(x - 0.5) - 0.07) < 0.04) c = [12, 10, 12];
        for (const k of [0, 1, 2]) if (Math.abs(z - 0.06 - k * 0.035) < 0.025 && Math.abs(Math.abs(x - 0.5) - 0.38 + k * 0.03) < 0.03) c = [16, 14, 14];
      }
      return c;
    };
    const leg = q => (q.ny > 0.75 ? mul(v.leg, 0.62) : cel(v.leg, n3(q.ax * 0.8, q.ay * 0.8, q.az * 0.8, 40 + v.seed), 0.8, 1.12));
    const sting = q => (q.nz < 0.35 ? [36, 22, 18] : cel(v.bulb, n3(q.ax, q.ay, q.az, 50), 0.8, 1.16));
    return { body, tail, claw, head, leg, sting };
  }
  function defOf(v, low) {
    const P = painters(v);
    const legs = [];
    const LZ = [-4.2, -1.2, 2.2, 5.6];
    LZ.forEach((z, i) => {
      if (low && i % 2) return;
      for (const s of [-1, 1]) legs.push({
        name: 'p' + i + (s < 0 ? 'D' : 'E'), pivot: [s * 4.6, 0.6, z], rot: [0, s * (i - 1.5) * 0.35, s * 0.75],
        boxes: [{ x: s < 0 ? -5 : 0, y: -0.5, z: -0.5, w: 5, h: 1, d: 1, p: P.leg }],
        kids: low ? [] : [{ name: 'q' + i + (s < 0 ? 'D' : 'E'), pivot: [s * 4.9, 0, 0], rot: [0, 0, -s * 1.6], boxes: [{ x: s < 0 ? -5.4 : 0, y: -0.4, z: -0.4, w: 5.4, h: 0.8, d: 0.8, p: P.leg }] }],
      });
    });
    // rabo: 5 gomos que sobem e curvam pra frente por cima do corpo, ferrão na ponta
    let tailNode = { name: 'ferrao', pivot: [0, 0, 4.6], boxes: [{ x: -1.6, y: -1.6, z: 0, w: 3.2, h: 3.2, d: 3.6, p: P.sting }, { x: -0.5, y: -0.4, z: 3.4, w: 1, h: 1, d: 2.6, p: P.sting }], rot: [0.9, 0, 0] };
    for (let i = NT - 1; i >= 0; i--) {
      const w = 3.6 - i * 0.25;
      tailNode = { name: 't' + i, pivot: i ? [0, 0, 4.6] : [0, -0.4, 3.6], rot: [i ? 0.62 : 0.5, 0, 0], boxes: [{ x: -w / 2, y: -w / 2, z: 0, w, h: w, d: 5, p: P.tail }], kids: [tailNode] };
      if (low && i % 2 === 1) { /* low: rabo mantém os 5 gomos, só sem o espinho fino */ }
    }
    if (low) tailNode = strip(tailNode);
    const arm = s => ({
      name: 'braco' + (s < 0 ? 'D' : 'E'), pivot: [s * 3.6, 0.4, -5.4], rot: [0.1, -s * 0.55, 0],
      boxes: [{ x: -1.1, y: -1, z: -5, w: 2.2, h: 2, d: 5, p: P.claw }],
      kids: [{
        name: 'pinca' + (s < 0 ? 'D' : 'E'), pivot: [0, 0, -5], rot: [0, s * 1.0, 0],
        boxes: [{ x: -2, y: -1.6, z: -4.4, w: 4, h: 3, d: 4.6, p: P.claw }].concat(low ? [] : [{ x: s < 0 ? -2 : 0.6, y: -1.2, z: -8.4, w: 1.4, h: 2.2, d: 4.2, p: P.claw }]),
        kids: low ? [] : [{ name: 'dedo' + (s < 0 ? 'D' : 'E'), pivot: [s < 0 ? 1.2 : -1.2, 0, -4.2], rot: [0, s * 0.35, 0], boxes: [{ x: -0.6, y: -1, z: -4, w: 1.2, h: 2, d: 4, p: P.claw }] }],
      }],
    });
    const abd = [];
    for (let i = 0; i < (low ? 1 : 3); i++) abd.push(i);
    let abNode = { name: 'abd' + (abd.length - 1), pivot: [0, 0, low ? 4 : 3.4], boxes: [{ x: -4, y: -1.7, z: 0, w: 8, h: 3.2, d: low ? 9 : 3.6, p: P.body }], kids: [tailNode] };
    if (!low) for (let i = 1; i >= 0; i--) abNode = { name: 'abd' + i, pivot: [0, 0, i ? 3.4 : 4], boxes: [{ x: -4.6 + i * 0.3, y: -1.8, z: 0, w: 9.2 - i * 0.6, h: 3.4, d: 3.6, p: P.body }], kids: [abNode] };
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'corpo', pivot: [0, 20, -1], boxes: [{ x: -5, y: -1.8, z: -6, w: 10, h: 3.4, d: 10, p: P.head }],
        kids: legs.concat([arm(-1), arm(1), abNode]),
      }],
    };
  }
  function strip(n) { return { ...n, boxes: n.name === 'ferrao' ? [n.boxes[0]] : n.boxes, kids: (n.kids || []).map(strip) }; }

  // passada em quatro: pares alternados, passo curto, apoio longo
  const STEP = [[0, -0.32], [0.3, -0.05], [0.55, 0.3], [0.75, 0.05]];
  const LIFT = [[0, 0], [0.55, 0], [0.65, -0.4], [0.85, 0]];
  function legs(r, ph, amt) {
    for (let i = 0; i < 4; i++) for (const s of ['D', 'E']) {
      const o = ph + (i % 2 ? 0.5 : 0) + (s === 'E' ? 0.5 : 0);
      const sg = s === 'D' ? -1 : 1;
      r('p' + i + s, 0, loop(STEP, o) * amt * sg, loop(LIFT, o) * amt * sg);
    }
  }
  function sandBurst() {
    return FX.debris(34, 21, [[216, 188, 136], [198, 166, 112], [232, 210, 166], [180, 148, 100]]);
  }

  function anim(P, t, mode, a, obj, ent, ctx) {
    const r = (k, x, y, z) => { if (!P[k]) return; const o = P[k].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const claws = (o, d) => { r('pincaD', 0, -0.25 * o, 0); r('pincaE', 0, 0.25 * o, 0); r('dedoD', 0, -0.55 * d, 0); r('dedoE', 0, 0.55 * d, 0); };
    if (mode === 'parado') {
      // espera: o rabo armado sobe e desce em dois tempos, as pinças abrem e fecham sem pressa
      const tl = loop([[0, 0], [0.35, -0.08], [0.45, -0.08], [0.8, 0.04]], t * 0.3);
      for (let i = 0; i < NT; i++) r('t' + i, tl * (i < 2 ? 1 : 0.4), 0, 0);
      claws(0, loop([[0, 0], [0.1, 0.6], [0.2, 0], [0.6, 0], [0.66, 0.5], [0.74, 0]], t * 0.25));
      legs(r, t * 0.1, 0.15);
      return;
    }
    if (mode === 'andar') {
      legs(r, t * 1.8, 1);
      r('corpo', 0, 0.04 * loop([[0, -1], [0.5, 1]], t * 1.8), 0);
      for (let i = 0; i < NT; i++) r('t' + i, 0, 0.05 * loop([[0, 1], [0.5, -1]], t * 0.9 - i * 0.1), 0);
      r('bracoD', 0.05, 0.1, 0); r('bracoE', 0.05, -0.1, 0);
      return;
    }
    const R = ctx.ref;
    const sandAt = (key, p, size, age, seed) => ctx.get(key, FX.volume).set(p, size, age, { seed, dens: 4.5, rise: 0.3, squash: 0.6, freq: 1.8, smoke: [0.74, 0.62, 0.44] });
    if (mode === 'emboscada') {
      const T = t % 3.4;
      R.place(0, -1.45, 0);
      // 1) enterrado: só a ponta do ferrão; a areia treme embaixo do alvo  2) estoura pra cima  3) prende e aperta
      const out = seq([[0.6, 0], [0.75, 1, 'snap'], [2.8, 1], [3.2, 0, 'io']], T);
      const grab = seq([[0.72, 0], [0.86, 1, 'snap'], [2.4, 1], [2.7, 0]], T);
      obj.position.y -= 0.62 * (1 - out);
      obj.rotation.x = 0.35 * Math.sin(Math.PI * FX.clamp01((T - 0.6) / 0.4));
      if (T < 0.6) ctx.get('tremor', FX.volume).set(new THREE.Vector3(0, 0.05, -0.6), 0.6, T / 0.6 * 0.5, { seed: 3.3, dens: 2.0, rise: 0.1, squash: 0.35, freq: 2.2, smoke: [0.74, 0.62, 0.44] });
      if (T > 0.6) {
        sandAt('estouro', new THREE.Vector3(0, 0.35, -0.4), 1.15, (T - 0.6) / 1.3, 6.6);
        ctx.get('graos', sandBurst).at(new THREE.Vector3(0, 0.1, -0.5), T - 0.6);
        if (T < 0.85) ctx.shake(1 - (T - 0.6) / 0.25);
      }
      r('bracoD', -0.25 * out, 0.5 * grab, 0); r('bracoE', -0.25 * out, -0.5 * grab, 0);
      claws(1 - grab, grab > 0.9 ? 0.5 + 0.5 * Math.max(0, Math.sin(T * 9)) : 1 - grab);
      for (let i = 0; i < NT; i++) r('t' + i, -0.05 * out, 0, 0);
      legs(r, T * 0.5, 0.3);
      if (grab > 0.5) {
        // a perna fica presa: o jogador tropeça pra frente e se segura
        R.lean(0.22 * grab + 0.03 * Math.sin(T * 9));
        if (T > 1.0 && T < 2.4) ctx.slash(new THREE.Vector3(0, 0.45, -1.25), fr((T - 1.0) / 0.7) * 1.3);
      }
      return;
    }
    if (mode === 'ferroada') {
      const T = t % 3.6;
      if (!ent._tip && !ent._dry) {
        // ensaio: põe a pose do bote, mede onde a ponta do ferrão cai e coloca o jogador ali
        ent._dry = true; anim(P, 1.0, 'ferroada', a, obj, ent, { ...ctx, ref: { place() {}, lean() {} }, get: () => ({ set() {} }), poison() {}, shake() {} });
        obj.updateMatrixWorld(true);
        const p = new THREE.Vector3(0, 0, 0); P.ferrao.obj.localToWorld(p.set(0, 0, 0));
        const tipL = new THREE.Vector3(0, 0, 6.8); P.ferrao.obj.localToWorld(tipL);
        ent._tip = tipL.clone(); ent._dry = false;
        ENG.reset(ent.model); obj.position.set(0, 1.5, 0); obj.rotation.set(0, 0, 0);
      }
      const tip = ent._tip || new THREE.Vector3(0, 0.8, -1.1);
      R.place(0, Math.min(-0.9, tip.z - 0.12), 0);
      // arma o rabo pra trás (aviso), crava por cima do corpo, segura, e depois se enterra
      const cock = seq([[0.1, 0], [0.6, 1, 'out'], [0.72, 1]], T);
      const strike = seq([[0.72, 0], [0.8, 1, 'snap'], [1.2, 1], [1.6, 0, 'io']], T);
      const dig = seq([[2.3, 0], [3.1, 1, 'in'], [3.45, 1], [3.6, 0]], T);
      for (let i = 0; i < NT; i++) r('t' + i, (i ? 0.1 : -0.25) * cock * (1 - strike) + [1.0, -0.32, -0.3, -0.28, -0.2][i] * strike, 0, 0);   // base fica de pé e o resto abre: o ferrão passa por cima da cabeça
      r('ferrao', 0.3 * cock * (1 - strike) - 0.2 * strike, 0, 0);
      r('corpo', -0.08 * cock * (1 - strike) + 0.12 * strike, 0, 0);
      obj.position.z -= 0.3 * strike;
      r('bracoD', 0, -0.3 * cock, 0); r('bracoE', 0, 0.3 * cock, 0);
      claws(0.8 * cock, 0.8 * cock);
      legs(r, T * 0.6, 0.3 + 0.6 * dig);
      obj.position.y -= 0.7 * dig;
      if (T > 2.3) sandAt('cova', new THREE.Vector3(0, 0.2, 0.1), 1.0, (T - 2.3) / 1.3, 9.1);
      if (T > 0.8) {
        const pois = ctx.get('veneno-esc', FX.volume);
        pois.set(tip, 0.36, (T - 0.8) / 1.2, { seed: 4.4, dens: 6, rise: 0.5, freq: 2, smoke: [0.5, 0.7, 0.2] });
        R.lean(0.3 * Math.sin(Math.PI * FX.clamp01((T - 0.8) / 0.7)));
        ctx.poison(seq([[0.85, 0], [1.05, 1], [2.6, 1], [3.0, 0]], T));
        if (T < 0.95) ctx.shake(1 - (T - 0.8) / 0.15);
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const SK = [
    { nome: 'Areia', seed: 1, shell: [204, 164, 100], stripe: [132, 96, 56], edge: [236, 214, 170], under: [150, 120, 80], leg: [214, 180, 120], bulb: [176, 70, 44] },
    { nome: 'Negro', seed: 2, shell: [52, 46, 50], stripe: [24, 22, 26], edge: [120, 104, 90], under: [36, 30, 30], leg: [190, 132, 52], bulb: [212, 150, 40] },
  ];
  window.MOBS.push({
    id: 'escorpiao_areia', nome: 'Escorpião de Areia', bioma: 'Deserto do Vento', tier: 'Fraco · tier 9',
    variants: SK.map(v => ({ nome: v.nome, v, def: defOf(v, false), low: defOf(v, true) })),
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['emboscada', 'Emboscada'], ['ferroada', 'Ferroada']],
    cam: 4, lookY: 0.55, lookZ: { emboscada: -0.8, ferroada: -1 }, camFar: { emboscada: 4.5, ferroada: 4.5 }, camYaw: { emboscada: -1.1, ferroada: -1.3 }, refX: 2,
    info: {
      'Tamanho': '1,5 bloco com o rabo, 0,9 de altura com o rabo armado',
      'Vida': '60 (tier 9)',
      'Jeito': 'Hostil. Passa o dia enterrado com só a ponta do ferrão de fora (dá pra ver se olhar o chão); de noite anda na superfície.',
      'Emboscada': 'Quando você pisa perto, a areia treme 0,6 s e ele sai embaixo de você: a pinça prende sua perna por 1,5 s (lento) e aperta.',
      'Ferroada': 'Arma o rabo pra trás (0,6 s de aviso) e crava por cima do corpo: veneno por 5 s. Depois se enterra de novo e só sai na próxima emboscada.',
      'Solta': 'Ferrão (ingrediente de veneno), carapaça de areia.',
    },
  });
})();
