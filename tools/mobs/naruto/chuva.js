// Ninja da Chuva (Vila da Chuva). Médio, tier 8. Capa de chuva, respirador, guarda-chuva de metal.
// Golpes: Guarda-chuva Senbon (joga o guarda-chuva pro alto, ele abre girando e chove agulha numa área de 5 blocos;
// a sombra no chão avisa) e Corrente (gancho na ponta de uma corrente: te puxa até ele).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const N = window.NINJA, SU = window.SUNA;
  const NP = 8;

  function variant(seed) {
    const v = N.variant(seed);
    const R = FX.rnd(seed + 909);
    const pick = a => a[Math.floor(R() * a.length)];
    v.scarf = null; v.mask = false;
    v.coat = pick([[58, 62, 74], [74, 70, 80], [52, 64, 70]]);
    v.canopy = pick([[160, 40, 44], [214, 206, 190], [60, 64, 76], [96, 120, 160]]);
    v.hood = R() < 0.7;
    return v;
  }
  function painters(v) {
    const P = N.painters(v);
    const S = v.seed;
    const shade = (base, q, s, lo = 0.8, hi = 1.12) => {
      const f = 0.62 - 0.4 * q.ny + 0.35 * (n3(q.ax * 0.25, q.ay * 0.25, q.az * 0.25, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    // capa encerada: escorrimento de água em listras verticais claras
    const coat = (q, s) => {
      let c = shade(v.coat, q, s);
      if (fr(q.ax * 0.6 + q.az * 0.6 + 0.4 * n3(0, q.ay * 0.1, 0, s)) < 0.06 && n3(q.ax, q.ay * 0.3, q.az, s + 1) > 0.45) c = mix(c, [190, 206, 220], 0.4);
      return c;
    };
    P.torso = q => coat(q, 140);
    P.pouchV = q => coat(q, 141);
    P.sleeve = q => coat(q, 142); P.upperArm = q => coat(q, 142); P.wrapArm = q => coat(q, 143);
    P.collar = q => coat(q, 144);
    P.cape = q => {
      if (q.ny > 0.86 + 0.14 * n3(Math.floor(q.px) * 1.7, S, 0, 145)) return null;                   // barra gasta
      return coat(q, 146);
    };
    // respirador: metade de baixo do rosto em metal, dois filtros redondos
    const base = P.head;
    P.head = q => {
      if (q.f !== 'north') return base(q);
      const x = q.px, y = q.py;
      if (y >= 4.2) {
        let c = mix([96, 100, 110], [150, 156, 166], n3(q.ax, q.ay, q.az, 147));
        if (Math.hypot(x - 1.6, y - 5.6) < 0.9 || Math.hypot(x - 5.4, y - 5.6) < 0.9) c = [40, 42, 48];
        if (Math.hypot(x - 1.6, y - 5.6) < 0.4 || Math.hypot(x - 5.4, y - 5.6) < 0.4) c = [120, 124, 134];
        return c;
      }
      return base(q);
    };
    const baseTop = P.hairTop, baseBack = P.hairBack;
    P.hairTop = q => (v.hood ? coat(q, 148) : baseTop(q));
    P.hairBack = q => (v.hood ? coat(q, 149) : baseBack(q));
    // placa com as quatro riscas da Chuva
    P.plate = q => {
      const metal = mix([150, 156, 170], [214, 220, 230], 0.4 + 0.6 * n3(q.ax * 0.8, q.ay * 0.8, q.az * 0.8, 46));
      if (q.f !== 'north') return mul(metal, 0.82);
      if (q.nx < 0.07 || q.nx > 0.93 || q.ny < 0.1 || q.ny > 0.9) return mul(metal, 0.72);
      for (let i = 0; i < 4; i++) if (Math.abs(q.nx - (0.26 + i * 0.16)) < 0.03 && q.ny > 0.22 && q.ny < 0.8) return mul(metal, 0.56);
      return metal;
    };
    P.lowArm = q => coat(q, 143);
    return P;
  }

  // ---------- guarda-chuva de metal: 8 gomos que abrem em volta da haste ----------
  function umbrellaDef(v, low) {
    const metal = q => cel([120, 124, 134], n3(q.ax, q.ay, q.az, 150), 0.8, 1.15);
    const cloth = i => q => {
      let c = cel(v.canopy, n3(q.ax * 0.4, q.ay * 0.4, q.az * 0.4, 151 + i), 0.86, 1.08);
      if (i % 2) c = mul(c, 0.9);
      if (Math.abs(q.nx - 0.5) < 0.05) c = [70, 72, 80];                                               // vareta
      if (q.ny > 0.9) c = mul(c, 0.7);
      return c;
    };
    if (low) return { name: 'guarda', pivot: [0, 1.6, 0], boxes: [{ x: -0.5, y: 0, z: -0.5, w: 1, h: 22, d: 1, p: metal }, { x: -2, y: 8, z: -2, w: 4, h: 13, d: 4, p: cloth(0) }] };
    const kids = [];
    for (let i = 0; i < NP; i++) kids.push({
      name: 'gR' + i, pivot: [0, 21, 0], rot: [0, i * Math.PI * 2 / NP, 0],
      kids: [{ name: 'g' + i, pivot: [0, 0, 0], rot: [0.1, 0, 0], boxes: [{ x: -1.6, y: -6, z: -0.15, w: 3.2, h: 6, d: 0.3, p: cloth(i) }, { x: -4.9, y: -13, z: -0.18, w: 9.8, h: 7, d: 0.36, p: cloth(i) }] }],
    });
    return {
      name: 'guarda', pivot: [0, 1.6, 0], boxes: [
        { x: -0.45, y: -2, z: -0.45, w: 0.9, h: 25, d: 0.9, p: metal },
        { x: -0.8, y: -2.5, z: -0.8, w: 1.6, h: 4, d: 1.6, p: q => cel([40, 36, 34], n3(q.ax, q.ay * 3, q.az, 152), 0.8, 1.2) },
      ], kids,
    };
  }
  const defOf = v => {
    const P = painters(v);
    const d = N.rigDef(P, v, { noTails: v.hood, torso: [{ x: -4.6, y: -9.4, z: 2.1, w: 9.2, h: 21, d: 0.8, p: P.cape }] });
    SU.findNode(d, 'maoD').kids = [umbrellaDef(v, false)];
    return d;
  };
  const lowOf = v => {
    const P = painters(v); const d = N.rigLow(P, v);
    SU.findNode(d, 'bracoD').kids = [{ name: 'maoD', pivot: [0, 10.2, 0], kids: [umbrellaDef(v, true)] }];
    return d;
  };
  function openUmb(P, r, k) { for (let i = 0; i < NP; i++) r('g' + i, 1.3 * k, 0, 0); }
  function sideModel(def) {
    const model = ENG.build(def), tex = ENG.paint(model, model.W, model.H), obj = ENG.toThree(model, tex);
    obj.model = model; return obj;
  }

  // agulhas caindo do guarda-chuva: cada uma sai de um ponto da borda e cai num ponto do chão
  function needleRain(n, seed) {
    const R = FX.rnd(seed), g = new THREE.Group(), list = [];
    const mat = new THREE.MeshBasicMaterial({ color: 0xdfe4ee });
    for (let i = 0; i < n; i++) {
      const m = new THREE.Mesh(new THREE.BoxGeometry(0.014, 0.014, 0.26), mat);
      g.add(m);
      const a = R() * 6.28, rr = Math.sqrt(R()) * 2.4;
      list.push({ m, a, rr, lag: R() * 0.9, sp: 0.22 + R() * 0.12 });
    }
    g.at = (o, spin, age) => {
      g.visible = true;
      for (const p of list) {
        const f = (age - p.lag) / p.sp;
        const stuck = f >= 1;
        p.m.visible = f > 0 && age < 2.4;
        if (!p.m.visible) continue;
        const a0 = p.a + spin, x0 = o.x + Math.cos(a0) * 0.7, z0 = o.z + Math.sin(a0) * 0.7;
        const x1 = o.x + Math.cos(a0 + 0.4) * p.rr, z1 = o.z + Math.sin(a0 + 0.4) * p.rr;
        const ff = Math.min(1, f);
        p.m.position.set(x0 + (x1 - x0) * ff, o.y + (0.06 - o.y) * ff + (stuck ? 0.08 : 0), z0 + (z1 - z0) * ff);
        p.m.lookAt(x1 + (x1 - x0) * 0.1, -1, z1 + (z1 - z0) * 0.1);
      }
    };
    return g;
  }
  function shadowDisc() {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, uniforms: { uA: { value: 0 } },
      vertexShader: 'varying vec2 vP; void main(){ vP=position.xy; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: 'varying vec2 vP; uniform float uA; void main(){ float r=length(vP); float k=smoothstep(1.0,0.75,r); float a=atan(vP.y,vP.x); k*=0.75+0.25*step(0.5,fract(a*8.0/6.2832)); gl_FragColor=vec4(0.02,0.03,0.05,k*uA*0.55); }',
    });
    const m = new THREE.Mesh(new THREE.CircleGeometry(1, 48), mat);
    m.rotation.x = -Math.PI / 2;
    m.at = (p, s, a, spin) => { m.visible = a > 0.01; m.position.copy(p); m.scale.setScalar(s); m.rotation.z = spin; mat.uniforms.uA.value = a; };
    return m;
  }
  // corrente: elos alternando o plano, com barriga, e o gancho na ponta
  function chain(n) {
    const g = new THREE.Group(), links = [];
    const mat = new THREE.MeshLambertMaterial({ color: 0x8a8e98 });
    for (let i = 0; i < n; i++) { const m = new THREE.Mesh(new THREE.BoxGeometry(0.035, 0.012, 0.09), mat); g.add(m); links.push(m); }
    const hook = new THREE.Group();
    const hm = new THREE.MeshLambertMaterial({ color: 0xb8bcc6 });
    const h1 = new THREE.Mesh(new THREE.BoxGeometry(0.03, 0.03, 0.22), hm); hook.add(h1);
    const h2 = new THREE.Mesh(new THREE.BoxGeometry(0.03, 0.14, 0.03), hm); h2.position.set(0, 0.06, 0.11); hook.add(h2);
    const h3 = new THREE.Mesh(new THREE.BoxGeometry(0.03, 0.03, 0.08), hm); h3.position.set(0, 0.12, 0.07); hook.add(h3);
    g.add(hook);
    g.at = (a, b, sag, k) => {
      g.visible = k > 0;
      const tip = new THREE.Vector3().lerpVectors(a, b, k);
      links.forEach((m, i) => {
        const f = (i + 0.5) / n;
        m.visible = f <= 1;
        m.position.set(a.x + (tip.x - a.x) * f, a.y + (tip.y - a.y) * f - Math.sin(Math.PI * f) * sag, a.z + (tip.z - a.z) * f);
        const f2 = Math.min(1, f + 0.02);
        m.lookAt(a.x + (tip.x - a.x) * f2, a.y + (tip.y - a.y) * f2 - Math.sin(Math.PI * f2) * sag, a.z + (tip.z - a.z) * f2);
        m.rotateZ(i % 2 ? Math.PI / 2 : 0);
      });
      hook.position.copy(tip); hook.lookAt(tip.x + (tip.x - a.x), tip.y, tip.z + (tip.z - a.z));
    };
    return g;
  }

  function anim(P, t, mode, a, obj, ent, ctx) {
    const r = (k, x, y, z) => { if (!P[k]) return; const o = P[k].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const low = !P.tronco;
    // guarda-chuva fechado apoiado no ombro, ponta pra trás
    const carry = () => { r('bracoD', -0.5, 0.15, 0.25); if (P.anteD) r('anteD', -1.75, 0, 0); else r('bracoD', -1.4, 0, 0); r('guarda', P.anteD ? -0.55 : -0.4, 0, 0); };
    if (mode === 'parado') {
      N.guard(r, P);
      r('bracoD', -0.08, 0, -0.18); if (P.anteD) r('anteD', 0.35, 0, 0);
      // na chuva, o guarda-chuva aberto por cima da cabeça
      r('bracoD', -0.35, 0, 0.1); if (P.anteD) r('anteD', -1.4, 0, 0); r('guarda', -1.25, 0, 0);
      openUmb(P, r, 1);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 0.95;
      for (const [s, off] of [['D', 0], ['E', 0.5]]) { r('perna' + s, loop(N.WALK_T, ph + off), 0, 0); r('canela' + s, loop(N.WALK_K, ph + off), 0, 0); }
      P.quadril.obj.position.y += loop(N.WALK_B, ph);
      carry();
      r('bracoE', loop(N.WALK_A, ph + 0.46), 0, -0.06);
      return;
    }
    const R = ctx.ref;
    if (mode === 'senbon') {
      const T = t % 4.2;
      R.place(0, -4.5, 0);
      // joga pra cima: o guarda-chuva sobe girando, abre, para no alto e chove agulha; depois desce na mão de volta
      const toss = seq([[0.1, 0], [0.45, 1, 'out'], [0.6, 0.2]], T);
      N.guard(r, P);
      r('bracoD', -2.6 * toss, 0, 0.2 * toss);
      r('cabeca', -0.35 * seq([[0.45, 0], [0.7, 1], [3.4, 1], [3.8, 0]], T), 0, 0);
      r('bracoE', -0.6 * seq([[0.5, 0], [0.8, 1], [3.3, 1], [3.6, 0]], T), 0, 0);    // mão esquerda guia o chakra
      const flying = T > 0.45 && T < 3.7;
      if (flying) {
        P.guarda.obj.scale.setScalar(0.001);
        const u = ctx.get('voador' + ent.meta.v.seed + (low ? 'b' : 'a'), () => sideModel(umbrellaDef(ent.meta.v, low)));
        u.visible = true; ENG.reset(u.model);
        const up = seq([[0.45, 0], [1.0, 1, 'out'], [3.1, 1], [3.7, 0, 'in']], T);
        const target = new THREE.Vector3(0, 3.3, -4.5);
        const hand = new THREE.Vector3(-0.35, 2.0, -0.2);
        const pos = hand.clone().lerp(target, up);
        u.position.copy(pos); u.position.y += 0.2;
        // de ponta-cabeça (haste pra baixo) enquanto gira lá em cima
        const spin = 8 * Math.max(0, T - 0.6) + 2 * T;
        u.rotation.set(Math.PI * up, spin, 0);
        u.scale.set(-1 / 16, -1 / 16, 1 / 16);
        const UQ = u.model.parts;
        const op = seq([[0.8, 0], [1.05, 1, 'snap'], [3.0, 1], [3.3, 0]], T);
        for (let i = 0; i < NP; i++) if (UQ['g' + i]) UQ['g' + i].obj.rotation.x += 1.3 * op;
        const shade = seq([[0.75, 0], [1.2, 1, 'out'], [3.0, 1], [3.3, 0]], T);
        ctx.get('sombra', shadowDisc).at(new THREE.Vector3(0, 0.015, -4.5), 2.5, shade, spin * 0.2);
        if (T > 1.5) ctx.get('agulhas', () => needleRain(90, 101)).at(new THREE.Vector3(0, 3.0, -4.5), spin, T - 1.5);
        if (T > 1.75 && T < 3.2) { R.lean(0.08 * Math.sin(T * 23)); R.armsOut(0.4 + 0.1 * Math.sin(T * 17)); }
      }
      return;
    }
    if (mode === 'corrente') {
      const T = t % 3.6;
      R.place(0, -5.2, 0);
      // gira a corrente do lado, lança o gancho, prende e puxa você até a frente dele
      const whirl = seq([[0.05, 0], [0.3, 1], [0.75, 1], [0.8, 0, 'snap']], T);
      const thr = seq([[0.75, 0], [0.82, 1, 'snap'], [1.0, 1]], T);
      const pull = seq([[1.15, 0], [1.6, 1, 'in'], [2.6, 1], [3.0, 0]], T);
      N.guard(r, P);
      carry();
      r('bracoE', -1.0 * whirl - 1.5 * thr * (1 - pull) + 0.6 * pull, 0, -0.6 * whirl);
      if (P.anteE) r('anteE', -0.4 * whirl, 0, 0);
      r('tronco', 0, -0.4 * thr + 0.5 * pull, 0); r('cabeca', 0, 0.4 * thr - 0.5 * pull, 0);
      r('pernaE', -0.3 * pull, 0, 0); r('canelaE', 0.4 * pull, 0, 0); r('pernaD', 0.3 * pull, 0, 0);
      obj.updateMatrixWorld(true);
      const hand = (P.maoE || P.bracoE).obj.localToWorld(new THREE.Vector3(0, P.maoE ? 2 : 10, 0));
      const refZ = -5.2 + 4.0 * pull;
      R.place(0, refZ, 0);
      if (T < 0.8) {
        // girando ao lado do corpo
        const a = T * 22, c = ctx.get('corrente', () => chain(26));
        const tip = hand.clone().add(new THREE.Vector3(0.1, Math.cos(a) * 0.55, Math.sin(a) * 0.55));
        c.at(hand, tip, 0, whirl);
      } else {
        const tgt = new THREE.Vector3(0, 1.05, refZ + 0.15);
        ctx.get('corrente', () => chain(26)).at(hand, tgt, 0.25 * (1 - pull) + 0.04, T < 2.7 ? Math.min(1, (T - 0.8) / 0.2) : 1 - (T - 2.7) / 0.4);
        if (T > 1.0 && T < 1.3) ctx.shake((1.3 - T) / 0.3 * 0.6);
        if (pull > 0) { R.lean(0.55 * pull * (1 - seq([[2.4, 0], [2.9, 1]], T))); }
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(7), variant(15), variant(40)];
  fixed[0].hood = true; fixed[1].hood = false;
  window.MOBS.push({
    id: 'ninja_chuva', nome: 'Ninja da Chuva', bioma: 'Vila da Chuva', tier: 'Médio · tier 8',
    variants: fixed.map((v, i) => ({ nome: ['Capuz', 'Bandana', 'Sorteado'][i], v, def: defOf(v), low: lowOf(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, def: defOf(v), low: lowOf(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['senbon', 'Guarda-chuva Senbon'], ['corrente', 'Corrente']],
    cam: 6, lookY: 1.3, lookZ: { senbon: -2.6, corrente: -2.6 }, lookYs: { senbon: 1.8 }, camFar: { senbon: 10, corrente: 9 }, camYaw: { senbon: -1.15, corrente: -1.3 },
    info: {
      'Tamanho': '1,85 bloco; o guarda-chuva aberto tem 1,6 bloco de diâmetro',
      'Vida': '120 (tier 8)',
      'Jeito': 'Hostil, patrulha os canos e as passarelas das torres. Na chuva do bioma fica mais rápido.',
      'Guarda-chuva Senbon': 'Joga o guarda-chuva pro alto: ele abre girando em cima de você e chove agulha numa área de 5 blocos por 1,5 s. A sombra girando no chão avisa 0,7 s antes.',
      'Corrente': 'Gira a corrente do lado (0,7 s de aviso) e lança o gancho a até 6 blocos: te puxa até a frente dele.',
      'Solta': 'Senbon, respirador; raro: guarda-chuva de metal (escudo que bloqueia projéteis).',
      'Variações': 'Capuz ou bandana, cor da capa e do pano do guarda-chuva sorteadas.',
    },
  });
})();
