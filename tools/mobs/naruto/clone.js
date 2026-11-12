// Clone de Madeira (Vale do Fim). Médio-forte, tier 7. Clone de Mokuton esquecido desde a guerra, com broto na cabeça.
// Golpes: Estacas (mãos no chão: uma fila de estacas de madeira rasga o chão até você e a última te joga pra cima)
// e Prisão de Madeira (bate as mãos no selo: vigas brotam em volta de você e fecham uma gaiola em cúpula).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const N = window.NINJA;

  function painters(v) {
    const S = v.seed;
    // madeira: veio vertical ondulado, nós escuros, musgo nas faces de cima
    const wood = (q, s, k = 1) => {
      const g = n3(q.ax * 0.9, q.ay * 0.12, q.az * 0.9, s + S);
      let c = cel(mul(v.wood, k), g, 0.82, 1.1);
      if (fr(q.ax * 0.8 + q.az * 0.8 + g * 1.8) < 0.14) c = mul(c, 0.8);                           // veio
      const knot = n3(q.ax * 0.35, q.ay * 0.35, q.az * 0.35, s + 90);
      if (knot > 0.82) c = mul(v.wood, 0.55); else if (knot > 0.78) c = mul(c, 0.82);
      if ((q.f === 'top' || q.ny < 0.12) && n3(q.ax * 0.6, q.ay * 0.6, q.az * 0.6, s + 91) > 1 - v.moss) c = mix(c, [86, 120, 58], 0.7);
      if (q.f === 'bottom') c = mul(c, 0.78);
      return c;
    };
    const P = {};
    P.skin = q => wood(q, 1);
    P.head = q => {
      let c = wood(q, 2);
      if (q.f !== 'north') return c;
      const x = q.px, y = q.py;
      if (y > 2.9 && y < 3.4 && (Math.abs(x - 2) < 1 || Math.abs(x - 5) < 1)) c = mul(v.wood, 0.6);  // sobrancelha entalhada
      if (y >= 3.6 && y < 4.5 && (Math.abs(x - 2) < 0.7 || Math.abs(x - 5) < 0.7)) c = [26, 18, 12];  // olhos fundos
      if (y >= 5.4 && y < 5.75 && Math.abs(x - 3.5) < 1.2) c = mul(v.wood, 0.58);                     // boca: um risco
      if (x > 3.2 && x < 3.8 && y > 4.4 && y < 5.2) c = mul(c, 0.86);                                  // nariz
      return c;
    };
    P.hairTop = q => wood(q, 3, 0.8);
    P.hairBack = q => wood(q, 3, 0.8);
    // roupa fundida na madeira: armadura de placas da era dos clãs, mais escura
    P.torso = q => { let c = wood(q, 4, 0.82); if (fr(q.ay * 0.5) < 0.12) c = mul(c, 0.74); return c; };
    P.collar = q => wood(q, 5, 0.75);
    P.pants = q => wood(q, 6, 0.86); P.belt = q => wood(q, 7, 0.7); P.pouch = q => wood(q, 7, 0.7); P.pouchV = q => wood(q, 4, 0.74);
    P.sleeve = q => wood(q, 8, 0.82); P.upperArm = q => wood(q, 8); P.wrapArm = q => wood(q, 9); P.glove = q => wood(q, 10, 0.94);
    P.shinWrap = q => wood(q, 11, 0.86); P.sandal = q => wood(q, 12, 0.7); P.holster = () => null;
    P.band = () => null; P.plate = () => null; P.scarf = q => wood(q, 5); P.scarfTail = () => null;
    P.lowTorso = q => wood(q, 4, 0.84); P.lowArm = q => wood(q, 8); P.lowLeg = q => wood(q, 6, 0.86); P.lowHair = q => wood(q, 3, 0.8);
    P.leaf = q => { let c = cel(v.leaf, n3(q.ax * 2, q.ay * 2, q.az * 2, 13), 0.82, 1.15); if (Math.abs(q.nx - 0.5) < 0.08) c = mul(c, 0.8); return c; };
    P.twig = q => wood(q, 14, 0.7);
    return P;
  }
  // broto na cabeça (e um galho saindo do ombro nas variações velhas)
  const sprout = P => ({ name: 'broto', pivot: [0.8, -8.6, -0.5], rot: [-0.2, 0, 0.25], boxes: [{ x: -0.3, y: -3, z: -0.3, w: 0.6, h: 3, d: 0.6, p: P.twig }], kids: [
    { name: 'folha1', pivot: [0, -3, 0], rot: [0, 0, 0.7], boxes: [{ x: 0, y: -0.15, z: -0.9, w: 2.4, h: 0.3, d: 1.8, p: P.leaf }] },
    { name: 'folha2', pivot: [0, -2, 0], rot: [0, 0.4, -0.6], boxes: [{ x: -2, y: -0.15, z: -0.7, w: 2, h: 0.3, d: 1.4, p: P.leaf }] },
  ] });
  const defOf = v => {
    const P = painters(v);
    const d = N.rigDef(P, v, { noTails: true });
    const head = window.SUNA.findNode(d, 'cabeca'); head.kids = (head.kids || []).concat([sprout(P)]);
    if (v.old) { const t = window.SUNA.findNode(d, 'tronco'); t.kids.push({ name: 'galho', pivot: [3.6, -8.6, 1], rot: [-0.5, 0, -0.7], boxes: [{ x: -0.5, y: -5, z: -0.5, w: 1, h: 5, d: 1, p: P.twig }], kids: [{ name: 'folha3', pivot: [0, -5, 0], rot: [0.3, 0, 0.4], boxes: [{ x: -1, y: -0.15, z: -1, w: 2.2, h: 0.3, d: 2, p: P.leaf }] }] }); }
    return d;
  };
  const lowOf = v => { const P = painters(v); const d = N.rigLow(P, v); const head = window.SUNA.findNode(d, 'cabeca'); head.kids = [{ name: 'broto', pivot: [0.8, -8.4, -0.5], boxes: [{ x: -0.3, y: -2.6, z: -0.3, w: 0.6, h: 2.6, d: 0.6, p: P.twig }, { x: -0.3, y: -3, z: -1, w: 2.4, h: 0.4, d: 1.8, p: P.leaf }] }]; return d; };

  // madeira de geometria (estacas e vigas): textura de veio desenhada num canvas pequeno
  let woodTex = null;
  function woodMat() {
    if (!woodTex) {
      const c = document.createElement('canvas'); c.width = 16; c.height = 64;
      const g = c.getContext('2d');
      for (let y = 0; y < 64; y++) for (let x = 0; x < 16; x++) {
        const k = n3(x * 0.9, y * 0.12, 0, 160);
        let col = cel([124, 92, 60], k, 0.8, 1.12);
        if (fr(x * 0.3 + k * 1.6) < 0.18) col = mul(col, 0.78);
        if (n3(x * 0.4, y * 0.4, 1, 161) > 0.8) col = mul(col, 0.6);
        g.fillStyle = `rgb(${col.map(Math.round).join(',')})`; g.fillRect(x, y, 1, 1);
      }
      woodTex = new THREE.CanvasTexture(c); woodTex.magFilter = THREE.NearestFilter; woodTex.minFilter = THREE.NearestFilter;
      woodTex.wrapS = woodTex.wrapT = THREE.RepeatWrapping;
    }
    return new THREE.MeshLambertMaterial({ map: woodTex });
  }
  // estaca: 3 troncos empilhados afinando, levemente torta, ponta em lasca
  function stake(R, mat, h) {
    const g = new THREE.Group();
    const w = 0.22 + R() * 0.1;
    let y = 0, lean = (R() - 0.5) * 0.25;
    for (let i = 0; i < 3; i++) {
      const sh = h / 3, s = w * (1 - i * 0.28);
      const m = new THREE.Mesh(new THREE.BoxGeometry(s, sh, s), mat);
      m.position.set(lean * y, y + sh / 2, 0); m.rotation.y = R() * 0.6; m.rotation.z = -lean;
      g.add(m); y += sh * 0.92;
    }
    const tip = new THREE.Mesh(new THREE.BoxGeometry(w * 0.25, h * 0.16, w * 0.25), mat); tip.position.set(lean * y, y + h * 0.06, 0); g.add(tip);
    return g;
  }
  // fila de estacas: cada uma brota na sua vez, do clone até o alvo
  function stakeLine(n, seed, len) {
    const R = FX.rnd(seed), g = new THREE.Group(), list = [], mat = woodMat();
    for (let i = 0; i < n; i++) {
      const f = i / (n - 1);
      const last = i === n - 1;
      const h = last ? 2.2 : 0.7 + f * 0.9 + R() * 0.3;
      const s = stake(R, mat, h);
      s.position.set((R() - 0.5) * 0.5 * (last ? 0 : 1), 0, -0.8 - f * len);
      s.rotation.set(-0.25 - R() * 0.2, (R() - 0.5) * 0.5, (R() - 0.5) * 0.3);
      g.add(s); list.push({ s, f, last });
    }
    g.at = (age, back) => {
      g.visible = true;
      for (const p of list) {
        const k = FX.clamp01((age - p.f * 0.45) / (p.last ? 0.12 : 0.1));
        const e = 1 - Math.pow(1 - k, 3);
        p.s.visible = k > 0;
        p.s.scale.set(1, Math.max(0.001, e * (1 - back)), 1);
      }
    };
    return g;
  }
  // gaiola: 8 vigas curvas, cada uma em 5 gomos que vão dobrando pra dentro até fechar em cúpula
  function cage(seed) {
    const R = FX.rnd(seed), g = new THREE.Group(), beams = [], mat = woodMat();
    for (let i = 0; i < 8; i++) {
      const root = new THREE.Group(); root.rotation.y = i * Math.PI / 4 + (R() - 0.5) * 0.1; g.add(root);
      const segs = []; let parent = root;
      for (let j = 0; j < 5; j++) {
        const s = new THREE.Group(); s.position.set(0, j === 0 ? 0 : 0.5, 0); if (j === 0) s.position.z = 1.15;
        const m = new THREE.Mesh(new THREE.BoxGeometry(0.26 - j * 0.03, 0.54, 0.26 - j * 0.03), mat); m.position.y = 0.25; s.add(m);
        parent.add(s); parent = s; segs.push(s);
      }
      beams.push({ segs, lag: R() * 0.12, curl: 0.36 + R() * 0.04 });
    }
    g.at = (c, grow, close, back) => {
      g.visible = grow > 0;
      g.position.copy(c);
      for (const b of beams) {
        const k = FX.clamp01((grow - b.lag) / (1 - b.lag));
        b.segs.forEach((s, j) => {
          s.scale.setScalar(1);
          s.visible = k * 5 > j;
          s.rotation.x = j === 0 ? 0.1 : -b.curl * close;
          if (j === 0) s.scale.y = Math.max(0.001, Math.min(1, k * 5)) * (1 - back);
        });
      }
    };
    return g;
  }

  function anim(P, t, mode, a, obj, ent, ctx) {
    const r = (k, x, y, z) => { if (!P[k]) return; const o = P[k].obj.rotation; o.x += x; o.y += y; o.z += z; };
    // parado: postura reta e pesada, como quem ficou de pé décadas no mesmo lugar
    const stand = () => { r('cabeca', -0.05, 0, 0); r('bracoD', 0.05, 0, 0.06); r('bracoE', 0.05, 0, -0.06); r('anteD', -0.15, 0, 0); r('anteE', -0.15, 0, 0); };
    const leaves = k => { r('folha1', 0, 0, 0.15 * k); r('folha2', 0, 0, -0.12 * k); };
    if (mode === 'parado') {
      stand();
      leaves(loop([[0, 0], [0.5, 1], [1, 0]], t * 0.3));
      return;
    }
    if (mode === 'andar') {
      const ph = t * 0.75;
      stand();
      for (const [s, off] of [['D', 0], ['E', 0.5]]) { r('perna' + s, 0.85 * loop(N.WALK_T, ph + off), 0, 0); r('canela' + s, loop(N.WALK_K, ph + off), 0, 0); }
      P.quadril.obj.position.y += loop(N.WALK_B, ph);
      r('bracoD', 0.6 * loop(N.WALK_A, ph), 0, 0); r('bracoE', 0.6 * loop(N.WALK_A, ph + 0.5), 0, 0);
      leaves(loop([[0, -1], [0.5, 1], [1, -1]], ph));
      return;
    }
    const R = ctx.ref;
    if (mode === 'estacas') {
      const T = t % 3.8;
      // agacha e bate as duas mãos no chão; as estacas correm do clone até você; a última te joga pra cima
      const down = seq([[0.1, 0], [0.45, 1, 'out'], [2.6, 1], [3.1, 0, 'io']], T);
      const slam = seq([[0.4, 0], [0.48, 1, 'snap']], T);
      r('quadril', 0.5 * down, 0, 0); P.quadril.obj.position.y += 4 * down;
      r('tronco', 0.55 * down, 0, 0); r('cabeca', -0.9 * down, 0, 0);
      r('pernaD', -1.3 * down, 0, 0); r('canelaD', 2.0 * down, 0, 0); r('peD', -0.7 * down, 0, 0);
      r('pernaE', -0.4 * down, 0, 0); r('canelaE', 1.6 * down, 0, 0); r('peE', -0.6 * down, 0, 0);
      r('bracoD', -1.0 * down - 0.3 * slam, 0, 0.15); r('bracoE', -1.0 * down - 0.3 * slam, 0, -0.15);
      const age = T - 0.48;
      const back = seq([[2.8, 0], [3.3, 1, 'in']], T);
      const len = 4.4;
      if (age > 0) {
        ctx.get('estacas', () => stakeLine(9, 171, len)).at(age, back);
        ctx.get('lascas', () => FX.debris(30, 172, [[110, 86, 60], [80, 64, 46], [140, 112, 78]])).at(new THREE.Vector3(0, 0.1, -0.8 - len), age - 0.45);
        if (age < 0.6) ctx.shake(0.4);
      }
      // jogado pra cima pela última estaca
      const lift = seq([[0.93, 0], [1.08, 1, 'out'], [1.5, 0.75], [1.9, 0, 'in']], T);
      R.place(0, -0.8 - len - 0.1, 1.5 * lift);
      R.lean(-0.5 * lift); R.armsOut(0.6 * lift);
      if (T > 0.93 && T < 1.15) ctx.slash(new THREE.Vector3(0, 0.9, -0.8 - len + 0.1), (T - 0.93) / 0.22);
      return;
    }
    if (mode === 'prisao') {
      const T = t % 4.6;
      const zc = -4.2;
      R.place(0, zc, 0);
      // selo do tigre na frente do peito (0,5 s de aviso), as vigas brotam e se fecham em cima de você
      const seal = seq([[0.1, 0], [0.4, 1, 'snap'], [3.6, 1], [3.9, 0]], T);
      r('bracoD', -1.15 * seal, 0.5 * seal, 0); r('bracoE', -1.15 * seal, -0.5 * seal, 0);
      r('anteD', -0.9 * seal, 0, 0); r('anteE', -0.9 * seal, 0, 0);
      r('pernaD', -0.2 * seal, 0, 0); r('canelaD', 0.3 * seal, 0, 0); r('pernaE', 0.15 * seal, 0, 0);
      r('cabeca', 0.08 * seal, 0, 0);
      const grow = seq([[0.6, 0], [1.1, 1, 'out']], T);
      const close = seq([[1.0, 0], [1.6, 1, 'io']], T);
      const back = seq([[3.8, 0], [4.3, 1, 'in']], T);
      ctx.get('gaiola', () => cage(181)).at(new THREE.Vector3(0, 0, zc), grow * (1 - back), close * (1 - back), 0);
      if (T > 0.6 && T < 1.2) {
        ctx.shake(0.5 * (1 - (T - 0.6) / 0.6));
        ctx.get('lascas', () => FX.debris(36, 182, [[110, 86, 60], [80, 64, 46], [86, 74, 58]])).at(new THREE.Vector3(0, 0.05, zc), T - 0.6);
      }
      // preso: bate nas vigas
      const trap = seq([[1.6, 0], [1.8, 1], [3.6, 1], [3.9, 0]], T);
      if (trap > 0) { R.armsOut(0.5 * trap + 0.2 * Math.max(0, Math.sin(T * 7))); R.lean(0.08 * Math.sin(T * 7) * trap); }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const mk = (seed, wood, moss, leaf, old) => { const v = N.variant(seed); Object.assign(v, { wood, moss, leaf, old, scarf: null, mask: false }); return v; };
  const fixed = [mk(51, [150, 112, 72], 0.12, [96, 150, 64], false), mk(52, [104, 76, 52], 0.25, [80, 130, 56], false), mk(53, [128, 104, 76], 0.5, [120, 150, 70], true)];
  window.MOBS.push({
    id: 'clone_madeira', nome: 'Clone de Madeira', bioma: 'Vale do Fim', tier: 'Médio · tier 7',
    variants: fixed.map((v, i) => ({ nome: ['Madeira clara', 'Madeira escura', 'Velho com musgo'][i], v, def: defOf(v), low: lowOf(v) })),
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['estacas', 'Estacas'], ['prisao', 'Prisão de Madeira']],
    cam: 6, lookY: 1.2, lookZ: { estacas: -2.6, prisao: -2.4 }, lookYs: { estacas: 1.4 }, camFar: { estacas: 9, prisao: 9.5 }, camYaw: { estacas: -1.3, prisao: -1.0 },
    info: {
      'Tamanho': '1,85 bloco',
      'Vida': '180 (tier 7); fogo dá dano dobrado',
      'Jeito': 'Neutro até você chegar perto das estátuas: aí vira guarda. Fica parado décadas no mesmo lugar (tem musgo e broto).',
      'Estacas': 'Agacha e bate as mãos no chão (0,4 s de aviso): uma fila de estacas corre 5 blocos até você e a última te joga 1,5 bloco pra cima.',
      'Prisão de Madeira': 'Faz o selo do tigre (0,5 s de aviso): 8 vigas brotam num círculo de 2 blocos em volta de você e fecham em cúpula por 2 s. Fogo ou golpe forte quebra antes.',
      'Solta': 'Madeira de Mokuton (bloco de construção que cresce), muda de árvore.',
    },
  });
})();
