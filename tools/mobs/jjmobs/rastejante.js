// Rastejante de Mil Mãos (Floresta Negra). Tier 4, grau especial: a maldição mais perigosa dos biomas de Jujutsu.
// Uma aranha enorme cujas 8 pernas são braços humanos compridos andando nas mãos, um bolo de carne pálida no meio
// com 5 máscaras de teatrinho (cada uma com uma cara) e dezenas de bracinhos pendurados por baixo.
// Golpes: Mãos do Chão (braços brotam da terra e te prendem) e Carrossel de Máscaras (as máscaras giram; a que
// para de frente pra você grita uma onda amaldiçoada).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const LEGZ = [-6, -2, 2.2, 6.2];

  const SKINS = [[206, 196, 186], [190, 194, 200], [214, 190, 180], [180, 186, 170]];
  function variant(seed, force) {
    const R = FX.rnd(seed);
    return Object.assign({ seed, skin: SKINS[Math.floor(R() * SKINS.length)], nails: R() < 0.5 ? [40, 30, 34] : [120, 30, 40], rot: R() * 6.28, size: 0.95 + R() * 0.1 }, force || {});
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.78, hi = 1.1) => {
      const f = 0.62 - 0.4 * q.ny + 0.35 * (n3(q.ax * 0.25, q.ay * 0.25, q.az * 0.25, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    // pele de cadáver: lisa, veias roxas, juntas avermelhadas
    const skin = (q, s = 1) => {
      let c = shade(v.skin, q, s);
      const w = n3(q.ax * 0.4, q.ay * 0.4, q.az * 0.4, 3 + S) * 3;
      if (Math.abs(fr(w) - 0.5) < 0.035) c = mix(c, [120, 90, 140], 0.55);
      return c;
    };
    const P = {};
    P.skin = q => skin(q);
    P.joint = q => mix(skin(q, 2), [190, 120, 120], 0.35);
    P.mass = q => {
      // bolo de carne: dobras, costelas aparecendo, uma mão inteira afundada na massa aqui e ali
      let c = skin(q, 4);
      const fold = fr(q.ax * 0.15 + q.az * 0.2 + n3(q.ax * 0.2, q.ay * 0.2, q.az * 0.2, 5 + S) * 1.8);
      if (fold < 0.08) c = mul(c, 0.7);
      if (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, 6 + S) > 0.72) c = mix(c, [150, 100, 110], 0.4);
      if (q.f === 'bottom') c = mix(c, [90, 50, 60], 0.4);
      return c;
    };
    P.nail = q => v.nails;
    P.hair = q => cel([22, 18, 24], n3(q.ax, q.ay * 0.4, q.az, 7 + S), 0.8, 1.3);
    // as 5 máscaras: laca branca, rachada, cada uma com uma expressão
    const MASK = [
      (x, y) => (y === 2 && (x === 1 || x === 4)) || (y === 4 && x >= 1 && x <= 4 && (y - 4 + Math.abs(x - 2.5)) < 2) ? 1 : (y === 5 && x >= 2 && x <= 3 ? 2 : 0), // sorriso
      (x, y) => (y === 2 && (x === 1 || x === 4)) ? 1 : ((y === 3 && (x === 1 || x === 4)) ? 3 : (y === 5 && x >= 2 && x <= 3 ? 1 : 0)),                       // choro (lágrima vermelha)
      (x, y) => (y === 1 && (x === 0 || x === 5)) || (y === 2 && (x === 1 || x === 4)) ? 1 : (y === 5 && x >= 1 && x <= 4 ? 2 : 0),                             // raiva
      (x, y) => (y === 2 && (x === 1 || x === 4)) ? 2 : 0,                                                                                                    // vazia (só dois buracos)
      (x, y) => (y === 2 && x >= 1 && x <= 4 && x !== 2 && x !== 3) ? 1 : ((y === 2 && (x === 2 || x === 3)) ? 0 : (y === 5 && x >= 1 && x <= 4 ? 1 : 0)), // olhos fechados
    ];
    P.mask = k => q => {
      let c = cel([234, 228, 214], 0.62 - 0.4 * q.ny + 0.3 * (n3(q.ax * 0.5, q.ay * 0.5, q.az * 0.5, 8 + k) - 0.5), 0.86, 1.06);
      if (q.f !== 'north') return mul(c, 0.86);
      const x = Math.floor(q.px), y = Math.floor(q.py);
      const m = MASK[k](x, y);
      if (m === 1) c = [24, 18, 22]; else if (m === 2) c = [70, 14, 22]; else if (m === 3) c = [170, 20, 30];
      if (k !== 3 && y === 3 && (x === 0 || x === 5)) c = [214, 120, 130];                                             // bochecha pintada
      if (n3(x * 1.3, y * 1.3, k, 9 + S) > 0.86) c = mul(c, 0.7);                                                      // rachadura
      return c;
    };
    P.maskMouth = q => (q.f === 'north' ? [20, 8, 12] : [180, 174, 160]);
    return P;
  }

  function arm(P, name, len, lo) {
    const fingers = lo ? [] : [-1, 0, 1].map(i => ({ x: -0.5 + i * 1.1, y: 1.6, z: -2.6, w: 0.9, h: 0.8, d: 2.4 + (i === 0 ? 0.6 : 0), p: P.skin }));
    return {
      name: 'mao' + name, pivot: [0, len, 0], boxes: [
        { x: -1.9, y: 0, z: -1.6, w: 3.8, h: 2.2, d: 3.2, p: P.skin }, ...fingers,
        ...(lo ? [] : [-1, 0, 1].map(i => ({ x: -0.45 + i * 1.1, y: 1.8, z: -3.1 - (i === 0 ? 0.6 : 0), w: 0.7, h: 0.5, d: 0.6, p: P.nail }))),
      ],
    };
  }
  function leg(P, s, i, lo) {
    const z = LEGZ[i], name = i + (s < 0 ? 'D' : 'E');
    return {
      // ombro no bolo; braço sobe e abre (cotovelo lá em cima), antebraço desce até a mão espalmada no chão
      name: 'braco' + name, pivot: [s * 8.6, -2, z * 1.15], rot: [0, (i - 1.5) * -0.32 * s, s * 0.85], boxes: [
        { x: -1.6, y: -18, z: -1.6, w: 3.2, h: 18, d: 3.2, p: P.skin },
        ...(lo ? [] : [{ x: -1.9, y: -19.6, z: -1.9, w: 3.8, h: 2.6, d: 3.8, p: P.joint }]),
      ],
      kids: [{
        name: 'ante' + name, pivot: [0, -18.2, 0], rot: [0, 0, s * (window.__AZ ?? -1.25)], boxes: [{ x: -1.35, y: 0, z: -1.35, w: 2.7, h: 34, d: 2.7, p: P.skin }],
        kids: [arm(P, name, 34, lo)],
      }],
    };
  }

  function rigDef(P, v, lo) {
    const legs = [];
    for (let i = 0; i < 4; i++) for (const s of [-1, 1]) legs.push(leg(P, s, i, lo));
    // as 5 máscaras: cada uma numa cabeça de carne de verdade, presa por um pescoço no miolo que sobe do bolo
    // (antes eram placas soltas num anel e afundavam nos cantos do corpo quando giravam)
    const masks = [];
    for (let k = 0; k < 5; k++) masks.push({
      name: 'mascara' + k, pivot: [0, 0, 0], rot: [0, k * Math.PI * 2 / 5, 0],
      boxes: [
        { x: -1.9, y: -4.2, z: -9.2, w: 3.8, h: 3.8, d: 6, p: P.mass },
        { x: -3.8, y: -6.8, z: -11.6, w: 7.6, h: 8, d: 4.2, p: P.skin },
        { x: -3.6, y: -6.4, z: -11.95, w: 7.2, h: 7.6, d: 0.4, p: P.mask(k) },
        ...(lo ? [] : [{ x: -4.1, y: -7.6, z: -11.9, w: 8.2, h: 1.6, d: 4.7, p: P.hair }, { x: -4.1, y: -6.2, z: -7.8, w: 8.2, h: 5, d: 0.6, p: P.hair }]),
      ],
      kids: k === 0 ? [{ name: 'grito', pivot: [0, -1.4, -12.0], boxes: [{ x: -1.4, y: 0, z: -0.2, w: 2.8, h: 1.6, d: 0.6, p: P.maskMouth }] }] : [],
    });
    const little = [];
    if (!lo) for (let i = 0; i < 9; i++) {
      const R = FX.rnd(i * 17 + v.seed), x = (R() - 0.5) * 9, z = (R() - 0.5) * 12, L = 4 + R() * 4;
      little.push({
        name: 'bracinho' + i, pivot: [x, 2, z], rot: [(R() - 0.5) * 0.6, R() * 6, (R() - 0.5) * 0.6],
        boxes: [{ x: -0.7, y: 0, z: -0.7, w: 1.4, h: L, d: 1.4, p: P.skin }, { x: -1, y: L, z: -0.9, w: 2, h: 1.6, d: 1.8, p: P.skin }],
      });
    }
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'corpo', pivot: [0, 6, 0], boxes: [
          { x: -9, y: -8, z: -10, w: 18, h: 10.6, d: 20, p: P.mass },
          { x: -7.4, y: -11.4, z: -7.4, w: 14.8, h: 3.6, d: 14.8, p: P.mass },
          ...(lo ? [] : [{ x: -5, y: -13.2, z: -4, w: 10, h: 2, d: 8, p: P.mass }, { x: -7.6, y: 2.4, z: -8, w: 15.2, h: 1.8, d: 16, p: P.mass }]),
        ],
        kids: [{ name: 'anel', pivot: [0, -13.2, 0], boxes: [{ x: -4.2, y: -6.4, z: -4.2, w: 8.4, h: 7.2, d: 8.4, p: P.mass }], kids: masks }, ...little, ...legs],
      }],
    };
  }

  // ---------- passo de aranha: 2 grupos alternados (D0 E1 D2 E3 / E0 D1 E2 D3) ----------
  function gait(r, ph, k = 1) {
    for (let i = 0; i < 4; i++) for (const sd of ['D', 'E']) {
      const s = sd === 'D' ? -1 : 1, grp = (i + (sd === 'D' ? 0 : 1)) % 2;
      const p = ph + grp * 0.5;
      const lift = Math.max(0, Math.sin(p * Math.PI * 2));
      r('braco' + i + sd, -0.3 * k * Math.cos(p * Math.PI * 2), 0, s * -0.25 * lift * k);
      r('ante' + i + sd, 0, 0, s * 0.3 * lift * k);
    }
  }

  // braços que brotam do chão (no jogo: entidade-efeito presa ao alvo). Cada um com comprimento, giro e atraso próprios.
  function groundArms(seed, skin) {
    const g = new THREE.Group(), R = FX.rnd(seed), list = [];
    const tex = (() => { const c = document.createElement('canvas'); c.width = 8; c.height = 32; const x = c.getContext('2d'); for (let y = 0; y < 32; y++) for (let i = 0; i < 8; i++) { const k = 0.78 + 0.25 * ENG.n3(i * 0.7, y * 0.3, seed, 1) + (y % 9 === 0 ? -0.15 : 0); x.fillStyle = `rgb(${skin[0] * k | 0},${skin[1] * k | 0},${skin[2] * k | 0})`; x.fillRect(i, y, 1, 1); } const t = new THREE.CanvasTexture(c); t.magFilter = t.minFilter = THREE.NearestFilter; return t; })();
    const mat = new THREE.MeshLambertMaterial({ map: tex });
    const nail = new THREE.MeshLambertMaterial({ color: 0x2a1e22 });
    for (let i = 0; i < 11; i++) {
      const a = (i / 11) * Math.PI * 2 + R() * 0.4, d = 0.9 + R() * 0.7, L = 0.9 + R() * 0.7, w = 0.06 + R() * 0.03;
      const arm = new THREE.Group();
      const up = new THREE.Mesh(new THREE.BoxGeometry(w * 2, L, w * 2), mat); up.position.y = L / 2; arm.add(up);
      const fore = new THREE.Group(); fore.position.y = L; arm.add(fore);
      const lo = new THREE.Mesh(new THREE.BoxGeometry(w * 1.7, L * 0.9, w * 1.7), mat); lo.position.y = L * 0.45; fore.add(lo);
      const hand = new THREE.Mesh(new THREE.BoxGeometry(w * 2.6, w * 2.2, w * 1.4), mat); hand.position.y = L * 0.9 + w; fore.add(hand);
      for (let f = 0; f < 3; f++) { const fi = new THREE.Mesh(new THREE.BoxGeometry(w * 0.5, w * 2, w * 0.5), nail); fi.position.set((f - 1) * w * 0.8, L * 0.9 + w * 3, 0); fore.add(fi); }
      g.add(arm);
      list.push({ arm, fore, a, d, L, delay: R() * 0.45, bend: 0.6 + R() * 0.6, twist: (R() - 0.5) * 0.8 });
    }
    g.at = (c, age, grip, sink) => {
      g.visible = age > 0;
      for (const it of list) {
        const k = FX.EASE.out(FX.clamp01((age - it.delay) / 0.45)) * (1 - sink);
        it.arm.position.set(c.x + Math.cos(it.a) * it.d, -it.L * 1.6 * (1 - k), c.z + Math.sin(it.a) * it.d);
        // nasce de pé, depois tomba pro centro e agarra
        it.arm.rotation.set(0, -it.a + Math.PI / 2 + it.twist, 0);
        it.arm.rotateX(0); it.arm.rotation.z = 0;
        const lean = 0.35 * k + 0.25 * grip;
        it.arm.rotation.order = 'YXZ';
        it.arm.rotation.x = 0; it.arm.rotation.z = 0;
        const q = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(Math.sin(it.a), 0, -Math.cos(it.a)), -lean);
        it.arm.quaternion.premultiply(q);
        it.fore.rotation.set(0, 0, 0); it.fore.rotateOnAxis(new THREE.Vector3(1, 0, 0), it.bend * grip);
      }
    };
    return g;
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size * 1.7; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const breath = loop([[0, 0], [0.45, 1], [0.999, 0]], t / 3.2);
    const little = (k = 1) => { for (let i = 0; i < 9; i++) r('bracinho' + i, 0.35 * k * Math.sin(t * (1.3 + i * 0.17) + i), 0, 0.3 * k * Math.sin(t * 1.1 + i * 2)); };
    // as máscaras não param: cada uma vira de leve pro lado que quer, e o anel inteiro gira devagar
    const maskLook = () => { for (let k = 0; k < 5; k++) r('mascara' + k, 0.08 * Math.sin(t * 0.7 + k), 0.12 * Math.sin(t * 0.5 + k * 1.7), 0.15 * Math.sin(t * 0.9 + k * 2.3)); };

    if (mode === 'parado') {
      P.corpo.obj.position.y += 0.8 * breath;
      r('anel', 0, 0.15 * t, 0); maskLook(); little();
      // dedos tamborilando no chão
      for (let i = 0; i < 4; i++) for (const sd of ['D', 'E']) r('mao' + i + sd, 0.12 * Math.max(0, Math.sin(t * 3 + i * 1.3 + (sd === 'D' ? 0 : 2))), 0, 0);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 0.9;
      gait(r, ph);
      P.corpo.obj.position.y += 0.6 * Math.abs(Math.sin(ph * Math.PI * 2));
      r('corpo', 0.04 * Math.sin(ph * Math.PI * 4), 0.05 * Math.sin(ph * Math.PI * 2), 0);
      r('anel', 0, 0.3 * t, 0); maskLook(); little(1.6);
      return;
    }

    if (mode === 'maos') {
      const T = t % 5.6;
      const R = ctx.ref; const RZ = -6.5; R.place(0, RZ, 0);
      // empina a frente, as máscaras viram todas pro alvo, bate as duas mãos da frente no chão
      const rear = seq([[0.1, 0], [0.8, 1, 'out'], [0.9, 1], [1.0, 0, 'in']], T);
      r('corpo', -0.35 * rear, 0, 0); P.corpo.obj.position.y -= 2.5 * rear;
      r('braco0D', 0, 0, 0.6 * rear); r('braco0E', 0, 0, -0.6 * rear); r('ante0D', 0, 0, -0.4 * rear); r('ante0E', 0, 0, 0.4 * rear);
      r('anel', 0, -0.4 * rear, 0); maskLook(); little(1 + rear);
      const age = T - 1.0;
      if (age > 0) {
        ctx.roarRing(new THREE.Vector3(0, 0.03, -3.0), age / 0.6);
        ctx.shake(Math.max(0, 0.9 - age * 2));
        // rachaduras correndo até o alvo, depois os braços brotam em volta dele
        const grip = seq([[0.6, 0], [1.0, 1, 'snap'], [3.6, 1], [3.9, 0]], age);
        const sink = seq([[3.7, 0], [4.4, 1, 'in']], age);
        ctx.get('bracosChao', () => groundArms(41, e.v.skin)).at(new THREE.Vector3(0, 0, RZ), age - 0.35, grip, sink);
        ctx.get('terra', () => FX.debris(30, 9, [[40, 34, 30], [60, 50, 40], [28, 24, 22], [80, 66, 50]])).at(new THREE.Vector3(0, 0.1, RZ), age - 0.35);
        // o alvo é puxado pra baixo: afunda até a cintura, se debate
        R.place(0, RZ, -0.55 * grip * (1 - sink)); R.armsOut(0.4 + 0.3 * Math.sin(T * 8) * grip); R.lean(0.15 * Math.sin(T * 6) * grip);
        if (grip > 0.5) ctx.cursed(0.6 * grip);
        // a máscara do sorriso aperta: estalos de dano
        if (age > 1.6 && age < 3.6 && fr(age * 1.6) < 0.08) ctx.shake(0.4);
      }
      return;
    }

    if (mode === 'carrossel') {
      const T = t % 5.0;
      const R = ctx.ref; const RZ = -6.0; R.place(0, RZ, 0);
      // as máscaras disparam a girar, o anel desacelera e para com a do sorriso de frente; ela abre a boca e grita
      const spin = seq([[0.3, 0], [1.9, 1, 'out']], T);
      const turns = Math.PI * 2 * 3;
      r('anel', 0, turns * FX.EASE.out(spin), 0);
      for (let k = 0; k < 5; k++) r('mascara' + k, 0.1 * Math.sin(T * 13 + k) * (1 - spin), 0, 0.2 * Math.sin(T * 11 + k * 2) * (1 - spin));
      little(1 + 2 * (1 - spin) * (T > 0.3 ? 1 : 0));
      const scream = seq([[1.95, 0], [2.1, 1, 'snap'], [3.1, 1], [3.5, 0]], T);
      r('grito', 0, 0, 0); if (P.grito) { P.grito.obj.scale.set(1 + 0.4 * scream, 1 + 3.2 * scream, 1); }
      r('corpo', 0.18 * scream, 0, 0); P.corpo.obj.position.y += 1.2 * scream;
      for (let i = 0; i < 4; i++) for (const sd of ['D', 'E']) r('mao' + i + sd, -0.25 * scream, 0, 0);
      if (T > 2.05) {
        const age = T - 2.05;
        // onda: um cone de energia amaldiçoada preta e roxa que anda pelo chão, levantando poeira e rachando
        const front = Math.min(1, age / 0.55);
        for (let i = 0; i < 3; i++) {
          const ag = (age - i * 0.12) / 1.0;
          if (ag > 0 && ag < 1) ctx.get('onda' + i, FX.volume).set(new THREE.Vector3(0, 1.6 - i * 0.3, -2.4 - front * (2.2 + i * 1.1)), 1.0 + i * 0.35 + front * 0.6, ag,
            { seed: 2 + i * 3.1, dens: 9, rise: 0.15, squash: 0.8, freq: 1.3, smoke: [0.16, 0.06, 0.26] });
        }
        ctx.roarRing(new THREE.Vector3(0, 0.03, -2.5 - front * 2), age / 0.7);
        ctx.get('luzGrito', () => DBFX.light(0x8a40ff)).at(new THREE.Vector3(0, 2.4, -3), 3 * (1 - FX.clamp01(age / 1.2)), 8);
        ctx.shake(Math.max(0, 1 - age / 0.9)); ctx.dizzy(Math.max(0, 1 - age / 1.6));
        const kb = seq([[0.35, 0], [0.9, 1, 'out']], age), up = Math.sin(Math.PI * FX.clamp01((age - 0.35) / 0.55));
        R.place(0, RZ - 3.4 * kb, 1.2 * up); R.lean(-1.4 * seq([[0.35, 0], [0.9, 1, 'in'], [2.3, 1], [2.7, 0]], age)); R.spin(0.6 * kb);
        if (age > 0.3 && age < 1.5) ctx.cursed(0.8 * (1 - (age - 0.3) / 1.2));
      }
      return;
    }

    if (mode === 'morte') {
      const { fall } = JJFX.exorcize(t, obj, e, ctx, { h: 3.4, refZ: -4.0, rotX: 0, back: 0, ashY: 0.8, ashZ: 0, n: 360, dur: 4.2 });
      // as pernas cedem, o bolo cai no chão, as máscaras se soltam
      P.corpo.obj.position.y += 7 * fall;
      for (let i = 0; i < 4; i++) for (const sd of ['D', 'E']) { const sg = sd === 'D' ? -1 : 1; r('braco' + i + sd, 0, 0, sg * 0.5 * fall); r('ante' + i + sd, 0, 0, -sg * 0.35 * fall); }
      for (let k = 0; k < 5; k++) { r('mascara' + k, 0.6 * fall * (k % 2 ? 1 : -1), 0, 0.4 * fall); }
      little(1 - fall);
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(4), variant(19, { skin: [190, 194, 200], nails: [120, 30, 40] })];
  const def = v => ({ def: rigDef(painters(v), v, false), low: rigDef(painters(v), v, true) });
  window.MOBS.push({
    id: 'rastejante', nome: 'Rastejante de Mil Mãos', bioma: 'Floresta Negra', tier: 'Grau especial · tier 4',
    variants: fixed.map((v, i) => ({ nome: ['Pálida', 'Cinzenta'][i], v, ...def(v) })),
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['maos', 'Mãos do Chão'], ['carrossel', 'Carrossel de Máscaras'], ['morte', 'Exorcizada']],
    cam: 13, lookY: 2.0, lookZ: { maos: -3.0, carrossel: -2.8, morte: -1 }, camFar: { maos: 15, carrossel: 15, morte: 13 }, camYaw: { maos: -1.1, carrossel: -1.15, morte: -0.8 },
    info: {
      'Tamanho': 'Uns 4 blocos de altura e 7 de pata a pata',
      'Vida': '900 (grau especial, tier 4: a mais perigosa dos biomas de Jujutsu)',
      'Jeito': 'Hostil, única: 1 por Floresta Negra, no coração da mata. Anda devagar nas mãos, tamborilando os dedos; as máscaras ficam girando e cada uma olha pra um lado. Ela sente você antes de você ver ela.',
      'Mãos do Chão': 'Empina, bate as mãos da frente no chão e manda rachaduras até você; 11 braços brotam da terra em volta, te agarram e puxam até a cintura (preso 2,5 s, dano a cada aperto). Pular na hora da rachadura escapa.',
      'Carrossel de Máscaras': 'As 5 máscaras disparam a girar e param com a do sorriso de frente pra você; ela abre a boca e solta um grito: uma onda de energia amaldiçoada preta e roxa que anda pelo chão, te joga 4 blocos e deixa a tela tonta.',
      'Solta': 'Muito resíduo; Máscara da Rastejante (uma das 5, sorteada: cada uma dá um efeito diferente vestida); dedo de unha preta (ingrediente raro).',
      'Variações': 'Tom da pele, cor das unhas; o resto é fixo (é uma só).',
    },
  });
})();
