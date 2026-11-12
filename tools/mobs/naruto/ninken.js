// Ninken (Floresta da Folha). Fraco, tier 10, matilha neutra de 3 a 4 cães ninja de raças diferentes.
// Golpes: Mordida em pinça (dois seguram os braços, um morde) e Gatsuga pequeno (gira como broca e atravessa).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  const BREEDS = [
    { raca: 'Pug', L: 9, W: 6.4, Hb: 5.4, leg: 4, hs: 6.4, snout: 1.2, ears: 'floppy', tail: 'curl', coat: [216, 180, 130], belly: [236, 212, 168], mask: [64, 50, 44], vest: [52, 82, 150], wrinkles: true },
    { raca: 'Shiba', L: 11, W: 5, Hb: 5, leg: 6, hs: 5.6, snout: 2.6, ears: 'up', tail: 'curl', coat: [208, 120, 58], belly: [246, 230, 200], mask: null, vest: [52, 82, 150] },
    { raca: 'Mastim', L: 15, W: 8, Hb: 7.4, leg: 8, hs: 7.4, snout: 2.8, ears: 'floppy', tail: 'straight', coat: [62, 48, 40], belly: [92, 72, 58], mask: [30, 24, 22], vest: [52, 70, 48], scar: true },
    { raca: 'Lobo', L: 14, W: 5.6, Hb: 6, leg: 8.6, hs: 6, snout: 3.6, ears: 'up', tail: 'bushy', coat: [126, 128, 138], belly: [222, 224, 228], mask: null, vest: [52, 82, 150], patch: true },
  ];
  function variant(i, seed) {
    const b = Object.assign({}, BREEDS[i % BREEDS.length]);
    const R = FX.rnd(seed + 1);
    const k = 0.9 + R() * 0.2;
    b.coat = mul(b.coat, k); b.belly = mul(b.belly, 0.95 + R() * 0.1);
    b.seed = seed; b.band = R() < 0.75;
    return b;
  }

  function painters(v) {
    const S = v.seed;
    const furOf = (base, q, s = 1) => {
      let c = cel(base, n3(q.ax * 0.35, q.ay * 0.3, q.az * 0.35, s + S) * 0.75 + n3(q.ax * 1.1, q.ay * 0.4, q.az * 1.1, s + 9) * 0.25, 0.82, 1.1);
      if (q.f === 'top') c = mul(c, 1.05);
      if (q.f === 'bottom') c = mul(c, 0.78);
      return c;
    };
    const P = {};
    P.body = q => {
      // barriga clara por baixo, subindo um pouco pelos lados com borda irregular
      const edge = 0.68 + 0.12 * n3(q.ax * 0.5, 0, q.az * 0.5, 2 + S);
      if (q.f === 'bottom' || ((q.f === 'west' || q.f === 'east') && q.ny > edge)) return furOf(v.belly, q, 3);
      return furOf(v.coat, q);
    };
    P.vest = q => {
      if (q.f === 'bottom') return null;
      // colete aberto embaixo: só cobre costas e lados até a metade
      if ((q.f === 'west' || q.f === 'east') && q.ny > 0.72) return null;
      let c = cel(v.vest, n3(q.ax * 0.4, q.ay * 0.4, q.az * 0.4, 4), 0.8, 1.14);
      if (q.nz < 0.08 || q.ny > 0.64) c = mul(c, 0.72);                                         // barra
      if (q.f === 'top') {                                                                      // marca de pata nas costas
        const dx = (q.nx - 0.5) * 2, dz = (q.nz - 0.55) * 2.4;
        if (dx * dx + dz * dz < 0.12) c = [236, 232, 220];
        for (const [ox, oz] of [[-0.42, -0.42], [-0.14, -0.6], [0.14, -0.6], [0.42, -0.42]]) {
          const ex = dx - ox, ez = dz - oz; if (ex * ex + ez * ez < 0.025) c = [236, 232, 220];
        }
      }
      return c;
    };
    P.head = q => {
      let c = furOf(v.coat, q, 5);
      if (q.f === 'top') return c;
      if (v.mask && q.f === 'north' && q.ny > 0.45) c = furOf(v.mask, q, 6);
      if (!v.mask && (q.f === 'north' || q.f === 'west' || q.f === 'east') && q.ny > 0.62) c = furOf(v.belly, q, 7); // bochechas claras
      if (q.f !== 'north') return c;
      const x = q.nx, y = q.ny;
      if (v.wrinkles && Math.abs(y - 0.36 - 0.06 * Math.cos(x * 6.28)) < 0.035) c = mul(c, 0.72);
      // olhos: escuros, com um pontinho de brilho, um pouco mais pra fora em cachorro de focinho curto
      for (const ex of [0.26, 0.74]) {
        const dx = (x - ex) * v.hs, dy = (y - 0.5) * v.hs;
        if (Math.abs(dx) < 0.75 && Math.abs(dy) < 0.6) c = [26, 20, 18];
        if (Math.abs(dx + 0.25) < 0.25 && Math.abs(dy + 0.2) < 0.2) c = [210, 210, 206];
      }
      if (v.patch && x > 0.58 && x < 0.92 && y > 0.36 && y < 0.64 && !(Math.abs((x - 0.74) * v.hs) < 0.75 && Math.abs((y - 0.5) * v.hs) < 0.6)) c = [72, 72, 80];
      if (v.scar && Math.abs((x - 0.2) - (y - 0.3) * 0.5) < 0.04 && y > 0.2 && y < 0.7) c = [160, 96, 88];
      return c;
    };
    P.snout = q => {
      let base = v.mask || mix(v.belly, v.coat, 0.2);
      let c = furOf(base, q, 8);
      if (q.f === 'bottom') c = mul(c, 0.7);
      if (q.f === 'north' && q.ny > 0.75 && Math.abs(q.nx - 0.5) < 0.06) c = mul(c, 0.5);
      return c;
    };
    P.jaw = q => {
      let c = furOf(mix(v.belly, v.coat, 0.3), q, 9);
      if (q.f === 'top') {
        const edge = q.nx < 0.18 || q.nx > 0.82 || q.nz < 0.2;
        c = edge ? [240, 236, 224] : [168, 60, 66];
      }
      return c;
    };
    P.nose = () => [30, 26, 28];
    P.ear = q => {
      let c = furOf(v.mask && v.ears === 'floppy' ? v.mask : v.coat, q, 10);
      if (v.ears === 'up' && q.f === 'north' && q.nx > 0.2 && q.nx < 0.8 && q.ny > 0.25) c = [196, 140, 130];
      if (v.ears === 'up' && Math.abs(q.nx - 0.5) * 2 > 1.15 - q.ny * 1.1 && q.ny < 0.6) return null; // ponta
      return c;
    };
    P.band = q => cel([44, 52, 90], n3(q.ax, q.ay, q.az, 11), 0.8, 1.15);
    P.plate = q => {
      const metal = mix([150, 156, 170], [214, 220, 230], 0.4 + 0.6 * n3(q.ax, q.ay, q.az, 12));
      if (q.f !== 'north') return mul(metal, 0.82);
      if (q.nx < 0.1 || q.nx > 0.9 || q.ny < 0.14 || q.ny > 0.86) return mul(metal, 0.72);
      const dx = (q.nx - 0.5) * 2, dy = (q.ny - 0.52) * 1.05, rr = Math.hypot(dx, dy);
      if (Math.abs(rr - 0.26) < 0.09) return mul(metal, 0.62);
      return metal;
    };
    P.leg = q => {
      let c = furOf(v.coat, q, 13);
      if (!v.mask && q.ny > 0.55) c = furOf(v.belly, q, 14);
      return c;
    };
    P.paw = q => {
      let c = furOf(!v.mask ? v.belly : mix(v.coat, v.belly, 0.35), q, 15);
      if (q.f === 'bottom') c = [58, 46, 44];
      if (q.f === 'north' && fr(q.px * 1.5) < 0.22 && q.ny > 0.4) c = mul(c, 0.7);
      return c;
    };
    P.tail = q => {
      let c = furOf(v.coat, q, 16);
      if (q.ny > 0.7 || (v.tail === 'bushy' && q.ny > 0.55)) c = furOf(v.belly, q, 17);
      return c;
    };
    return P;
  }

  // medidas que a animação precisa (em pixels do modelo)
  function geom(v) {
    const corpoY = 24 - v.leg - v.Hb / 2 + 0.5;
    return { corpoY, headY: corpoY - v.Hb / 2 + 0.5, mouthZ: -v.L / 2 - v.hs * 0.8 - v.snout * 0.7 };
  }
  function defOf(v, low) {
    const P = painters(v), g = geom(v);
    const { L, W, Hb, leg, hs, snout } = v;
    const lw = Math.max(1.6, W * 0.32);
    const legDef = (n, x, z) => low ? {
      name: 'perna' + n, pivot: [x, Hb / 2 - 0.5, z], boxes: [{ x: -lw / 2, y: 0, z: -lw / 2, w: lw, h: leg, d: lw, p: q => (q.ny > 0.86 ? P.paw(q) : P.leg(q)) }],
    } : {
      name: 'perna' + n, pivot: [x, Hb / 2 - 0.5, z], boxes: [{ x: -lw / 2, y: -0.5, z: -lw / 2 - 0.2, w: lw, h: leg * 0.55 + 0.5, d: lw + 0.4, p: P.leg }],
      kids: [{
        name: 'canela' + n, pivot: [0, leg * 0.55, 0], boxes: [{ x: -lw / 2 + 0.15, y: 0, z: -lw / 2 + 0.15, w: lw - 0.3, h: leg * 0.45 - 0.8, d: lw - 0.3, p: P.leg }],
        kids: [{ name: 'pata' + n, pivot: [0, leg * 0.45 - 0.8, 0], boxes: [{ x: -lw / 2 - 0.1, y: 0, z: -lw / 2 - 0.6, w: lw + 0.2, h: 0.8, d: lw + 0.7, p: P.paw }] }],
      }],
    };
    const lx = W / 2 - lw / 2 - 0.2, lzF = -L / 2 + lw / 2 + 0.6, lzB = L / 2 - lw / 2 - 0.6;
    const ears = v.ears === 'up'
      ? [-1, 1].map(s => ({ name: s < 0 ? 'orelhaD' : 'orelhaE', pivot: [s * hs * 0.3, -hs * 0.9, -hs * 0.35], rot: [0, 0, s * 0.18], boxes: [{ x: -hs * 0.16, y: -hs * 0.42, z: -0.4, w: hs * 0.32, h: hs * 0.42, d: 0.8, p: P.ear }] }))
      : [-1, 1].map(s => ({ name: s < 0 ? 'orelhaD' : 'orelhaE', pivot: [s * hs * 0.5, -hs * 0.82, -hs * 0.3], rot: [0, 0, s * 0.35], boxes: [{ x: s < 0 ? -0.8 : 0, y: 0, z: -hs * 0.18, w: 0.8, h: hs * 0.48, d: hs * 0.36, p: P.ear }] }));
    const tail = v.tail === 'curl'
      ? { name: 'rabo', pivot: [0, -Hb / 2 + 0.6, L / 2 - 0.4], rot: [-0.6, 0, 0], boxes: [{ x: -0.9, y: -3.2, z: -0.9, w: 1.8, h: 3.2, d: 1.8, p: P.tail }], kids: low ? [] : [{ name: 'rabo2', pivot: [0, -3.2, 0], rot: [-1.6, 0, 0], boxes: [{ x: -0.85, y: -2.6, z: -0.85, w: 1.7, h: 2.6, d: 1.7, p: P.tail }] }] }
      : { name: 'rabo', pivot: [0, -Hb / 2 + 1, L / 2 - 0.3], rot: [0.85, 0, 0], boxes: [{ x: -(v.tail === 'bushy' ? 1.3 : 0.8), y: 0, z: -(v.tail === 'bushy' ? 1.3 : 0.8), w: v.tail === 'bushy' ? 2.6 : 1.6, h: v.tail === 'bushy' ? 7 : 6, d: v.tail === 'bushy' ? 2.6 : 1.6, p: P.tail }] };
    const headKids = low ? [] : [
      { name: 'mandibula', pivot: [0, -hs * 0.12, -hs * 0.8 + 0.3], boxes: [{ x: -hs * 0.24, y: 0, z: -snout - 0.1, w: hs * 0.48, h: hs * 0.17, d: snout + 0.2, p: P.jaw }] },
      ...ears,
    ];
    const headBoxes = [
      { x: -hs / 2, y: -hs * 0.9, z: -hs * 0.8, w: hs, h: hs * 0.9, d: hs, p: P.head },
      { x: -hs * 0.28, y: -hs * 0.42, z: -hs * 0.8 - snout, w: hs * 0.56, h: hs * 0.32, d: snout + 0.2, p: P.snout },
      { x: -hs * 0.14, y: -hs * 0.46, z: -hs * 0.8 - snout - 0.3, w: hs * 0.28, h: hs * 0.2, d: 0.6, p: P.nose },
    ];
    if (v.band) headBoxes.push(
      { x: -hs / 2 - 0.2, y: -hs * 0.86, z: -hs * 0.8 - 0.2, w: hs + 0.4, h: hs * 0.2, d: hs + 0.4, p: P.band },
      { x: -hs * 0.24, y: -hs * 0.9, z: -hs * 0.8 - 0.42, w: hs * 0.48, h: hs * 0.28, d: 0.3, p: P.plate },
    );
    if (low) headBoxes.push(...ears.map(e => Object.assign({}, e.boxes[0], { x: e.boxes[0].x + e.pivot[0], y: e.boxes[0].y + e.pivot[1], z: e.boxes[0].z + e.pivot[2] })));
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'corpo', pivot: [0, g.corpoY, 0], boxes: [
          { x: -W / 2, y: -Hb / 2, z: -L / 2, w: W, h: Hb, d: L, p: P.body },
        ].concat(low ? [] : [{ x: -W / 2 - 0.3, y: -Hb / 2 - 0.3, z: -L / 2 - 0.2, w: W + 0.6, h: Hb * 0.8, d: L * 0.62, p: P.vest }]),
        kids: [
          { name: 'cabeca', pivot: [0, -Hb / 2 + 0.5, -L / 2 + 0.4], boxes: headBoxes, kids: headKids },
          legDef('FD', -lx, lzF), legDef('FE', lx, lzF), legDef('TD', -lx, lzB), legDef('TE', lx, lzB),
          tail,
        ],
      }],
    };
  }

  // ---------- faixas de vento do Gatsuga: cada uma com raio, passo, fase, largura e ruído próprios ----------
  function ribbons(n, seed) {
    const R = FX.rnd(seed), g = new THREE.Group(), list = [];
    const SEG = 42;
    for (let i = 0; i < n; i++) {
      const geo = new THREE.BufferGeometry();
      const pos = new Float32Array((SEG + 1) * 2 * 3), al = new Float32Array((SEG + 1) * 2);
      const idx = []; for (let s = 0; s < SEG; s++) { const a = s * 2; idx.push(a, a + 1, a + 2, a + 1, a + 3, a + 2); }
      geo.setIndex(idx);
      geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
      geo.setAttribute('al', new THREE.BufferAttribute(al, 1));
      const mat = new THREE.ShaderMaterial({
        transparent: true, depthWrite: false, side: THREE.DoubleSide, blending: THREE.NormalBlending,
        uniforms: { uCol: { value: new THREE.Color().setHSL(0.08, 0.15, 0.78 + R() * 0.18) } },
        vertexShader: 'attribute float al; varying float vA; void main(){ vA=al; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
        fragmentShader: 'uniform vec3 uCol; varying float vA; void main(){ if(vA<0.01) discard; gl_FragColor=vec4(uCol, vA); }',
      });
      const m = new THREE.Mesh(geo, mat); m.frustumCulled = false; g.add(m);
      list.push({ m, pos, al, geo, r: 0.28 + R() * 0.42, pitch: 0.9 + R() * 1.4, ph: R() * 6.28, w: 0.025 + R() * 0.05, len: 1.2 + R() * 1.8, a: 0.35 + R() * 0.45, s: R() * 100, lag: R() * 0.25 });
    }
    // head = posição da broca (z), dir = -1 indo pra frente; spin = ângulo de giro
    g.at = (cx, cy, head, spin, k) => {
      g.visible = k > 0.01;
      for (const it of list) {
        for (let s = 0; s <= SEG; s++) {
          const f = s / SEG, z = head + (it.lag + f * it.len);
          const ang = it.ph + spin * 0.8 - f * it.pitch * 6.28;
          const rr = it.r * (0.7 + 0.6 * f) * (0.85 + 0.3 * ENG.n3(f * 4, it.s, spin * 0.1, 7));
          const x = cx + Math.cos(ang) * rr, y = cy + Math.sin(ang) * rr;
          const ox = -Math.sin(ang) * it.w, oy = Math.cos(ang) * it.w;
          const b = s * 6;
          it.pos[b] = x - ox; it.pos[b + 1] = y - oy; it.pos[b + 2] = z;
          it.pos[b + 3] = x + ox; it.pos[b + 4] = y + oy; it.pos[b + 5] = z;
          const a = it.a * k * Math.sin(Math.PI * Math.min(1, f * 1.1)) * (1 - f * 0.6);
          it.al[s * 2] = a; it.al[s * 2 + 1] = a;
        }
        it.geo.attributes.position.needsUpdate = true; it.geo.attributes.al.needsUpdate = true;
      }
    };
    return g;
  }

  // ---------- poses ----------
  const TROT_U = [[0, -0.42], [0.18, -0.2], [0.46, 0.38], [0.6, 0.3], [0.8, -0.2]];
  const TROT_L = [[0, 0.05], [0.3, 0.1], [0.5, 0.2], [0.66, 0.9], [0.84, 0.35]];
  function stand(r, P, v) {
    r('cabeca', -0.05, 0, 0);
    r('rabo', 0, 0, 0);
  }
  function anim(P, t, mode, a, obj, ent, ctx) {
    const v = ent.v;
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    obj.rotation.order = 'YXZ';
    // abanar: batida rápida pra um lado, volta mais lenta (nada de seno)
    const wag = loop([[0, -0.5], [0.12, 0.55], [0.4, 0.3], [0.52, -0.55], [0.85, -0.35]], t * 1.6);
    const tailWag = k => { r('rabo', 0, wag * 0.6 * k, 0); };
    if (mode === 'parado') {
      stand(r, P, v);
      tailWag(1);
      const ear = seq([[0, 0], [1.6, 0], [1.66, -0.35, 'snap'], [1.9, 0]], t % 3.1);
      r('orelhaE', ear, 0, 0);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 1.5;
      for (const [n, off] of [['FD', 0], ['TE', 0.03], ['FE', 0.5], ['TD', 0.53]]) {
        r('perna' + n, loop(TROT_U, ph + off) * (n[0] === 'T' ? 0.85 : 1), 0, 0);
        r('canela' + n, (n[0] === 'T' ? -1 : 1) * loop(TROT_L, ph + off), 0, 0);
      }
      P.corpo.obj.position.y += loop([[0, 0.25], [0.2, -0.2], [0.5, 0.25], [0.7, -0.2]], ph);
      r('cabeca', 0.05, 0, 0);
      tailWag(0.5);
      return;
    }

    const R = ctx.ref;
    if (mode === 'pinca') {
      const T = t % 3.2;
      R.place(0, -2.6, 0);
      const hold = seq([[0.72, 0], [0.8, 1, 'snap'], [2.4, 1], [2.55, 0]], T);
      R.armsOut(hold);
      R.lean(0.12 * hold);
      // os dois de lado: correm, saltam e ficam pendurados nos braços, sacudindo
      const sides = [[1, ctx.extra(1)], [-1, ctx.extra(2)]];
      for (const [s, e] of sides) {
        if (!e) continue;
        const vv = e.ent.v, gg = geom(vv), o = e.obj; o.rotation.order = 'YXZ';
        const mouth = new THREE.Vector3(0, -gg.headY / 16 - vv.hs * 0.2 / 16, gg.mouthZ / 16);
        const arm = new THREE.Vector3(s * 0.44 + s * 0.08 * hold, 1.05 - 0.12 * hold, -2.6);
        const run = seq([[0.15, 0], [0.62, 1, 'lin']], T), jump = seq([[0.6, 0], [0.76, 1, 'out']], T);
        const drop = seq([[2.45, 0], [2.7, 1, 'in']], T);
        o.rotation.set(0, s * Math.PI / 2, 0);
        if (T < 0.6) {
          o.position.set(s * (2.6 - 1.4 * run), 1.5, -2.6 + 0.2);
          for (const [n, off] of [['FD', 0], ['TE', 0.05], ['FE', 0.5], ['TD', 0.55]]) e.r('perna' + n, loop(TROT_U, T * 3.4 + off) * 1.4, 0, 0);
          continue;
        }
        const pitch = 1.05 * jump * (1 - drop);
        const jerk = seq([[0.9, 0], [1.0, 1, 'snap'], [1.2, -0.6], [1.32, 0.8, 'snap'], [1.6, -0.3], [1.72, 0.7, 'snap'], [2.1, 0]], T + (s > 0 ? 0 : 0.17));
        o.rotation.set(pitch, s * Math.PI / 2 + 0.25 * jerk * (1 - drop), 0.12 * jerk);
        const target = arm.clone();
        const start = new THREE.Vector3(s * 1.2, 0, -2.4);
        const m = mouth.clone().applyEuler(o.rotation);
        const p = start.clone().lerp(target.clone().sub(m), jump);
        if (drop > 0) p.y = Math.max(1.5, p.y - drop * 1.2);
        o.position.set(p.x, p.y + 1.5 * (1 - jump) + (jump > 0 ? 0 : 0), p.z);
        o.position.y = jump < 1 ? 1.5 + (p.y - 1.5) * jump + Math.sin(Math.PI * jump) * 0.3 : p.y;
        e.r('mandibula', 0.7 * (1 - jump) + 0.12, 0, 0);
        // pernas soltas penduradas, as de trás chutando um pouco
        e.r('pernaFD', -0.5 * jump, 0, 0); e.r('pernaFE', -0.45 * jump, 0, 0);
        e.r('pernaTD', 0.7 * jump + 0.2 * jerk, 0, 0); e.r('pernaTE', 0.6 * jump - 0.2 * jerk, 0, 0);
        e.r('rabo', 0.4 * jump, 0, 0);
      }
      // o principal: agacha, salta e morde a perna, sacode a cabeça, solta e volta
      obj.rotation.set(0, 0, 0);
      const crouch = seq([[0.55, 0], [0.92, 1, 'out'], [0.98, 0, 'in']], T);
      const leap = seq([[0.95, 0], [1.12, 1, 'out'], [2.15, 1], [2.55, 0, 'io']], T);
      const shake = seq([[1.15, 0], [1.22, 1, 'snap'], [1.38, -1, 'snap'], [1.52, 0.8, 'snap'], [1.68, -0.7, 'snap'], [1.86, 0]], T);
      P.corpo.obj.position.y += 1.6 * crouch;
      r('pernaFD', -0.6 * crouch, 0, 0); r('pernaFE', -0.6 * crouch, 0, 0); r('canelaFD', 0.9 * crouch, 0, 0); r('canelaFE', 0.9 * crouch, 0, 0);
      r('pernaTD', 0.6 * crouch, 0, 0); r('pernaTE', 0.6 * crouch, 0, 0); r('canelaTD', -0.9 * crouch, 0, 0); r('canelaTE', -0.9 * crouch, 0, 0);
      r('cabeca', 0.25 * crouch, 0, 0);
      const air = Math.sin(Math.PI * FX.clamp01((T - 0.95) / 0.17));
      obj.position.z += (-2.47 - geom(v).mouthZ / 16) * leap;   // a boca chega na perna do alvo
      obj.position.y += 0.25 * air;
      obj.rotation.set(0.2 * leap, 0.35 * shake, 0);
      r('mandibula', 0.75 * seq([[0.9, 0], [1.05, 1], [1.13, 0.15, 'snap'], [2.15, 0.15], [2.25, 0.8]], T), 0, 0);
      r('cabeca', -0.25 * leap, 0, 0.3 * shake);
      tailWag(1 - leap);
      R.lean(0.1 * shake);
      return;
    }

    if (mode === 'gatsuga') {
      const T = t % 2.8;
      const z0 = 2.2, z1 = -5.2;
      R.place(0, -1.4, 0);
      const go = seq([[0.6, 0], [1.08, 1, 'lin']], T);
      const zc = z0 + (z1 - z0) * go;
      const g = geom(v);
      const cyLocal = -g.corpoY / 16;
      // agachar: escava o chão com as patas
      const crouch = seq([[0.05, 0], [0.5, 1, 'out'], [0.6, 1]], T);
      const spin = T > 0.6 && T < 1.08 ? (T - 0.6) * 62 : 0;
      const tuck = seq([[0.55, 0], [0.64, 1, 'snap'], [1.02, 1], [1.12, 0, 'out']], T);
      const skid = seq([[1.08, 0], [1.4, 1, 'out']], T);
      const turn = seq([[1.6, 0], [1.95, 1, 'io']], T);
      let C = new THREE.Vector3(0, 1.5 + cyLocal + (T > 0.6 && T < 1.1 ? 0.35 : 0) * tuck, zc - 0.6 * skid);
      obj.rotation.set(0, Math.PI * turn, spin);
      const rel = new THREE.Vector3(0, cyLocal, 0).applyEuler(obj.rotation);
      obj.position.set(C.x - rel.x, C.y - rel.y, C.z - rel.z);
      if (T < 0.6) obj.position.z = z0;
      P.corpo.obj.position.y += 1.4 * crouch * (1 - tuck);
      const dig = loop([[0, -0.6], [0.3, 0.5], [0.5, 0.2]], T * 5) * crouch * (1 - tuck);
      r('pernaFD', -0.5 * crouch + dig, 0, 0); r('pernaFE', -0.5 * crouch - dig, 0, 0);
      r('pernaTD', 0.5 * crouch, 0, 0); r('pernaTE', 0.5 * crouch, 0, 0);
      r('cabeca', 0.2 * crouch, 0, 0);
      // broca: encolhe tudo, cabeça baixa, pernas coladas
      r('pernaFD', -1.3 * tuck, 0, 0); r('pernaFE', -1.3 * tuck, 0, 0); r('pernaTD', 1.3 * tuck, 0, 0); r('pernaTE', 1.3 * tuck, 0, 0);
      r('cabeca', 0.35 * tuck, 0, 0); r('rabo', 0.9 * tuck, 0, 0);
      r('mandibula', 0.5 * seq([[1.5, 0], [1.6, 1], [2.3, 1], [2.45, 0]], T), 0, 0);
      tailWag(turn * (1 - tuck));
      const rib = ctx.get('vento', () => ribbons(16, 9));
      if (T > 0.58 && T < 1.25) rib.at(0, C.y, zc, spin, Math.min(1, (T - 0.58) / 0.05) * (1 - FX.clamp01((T - 1.05) / 0.2)));
      const dust = ctx.get('poeira', FX.volume), dust2 = ctx.get('poeira2', FX.volume);
      if (T > 0.4 && T < 1.3) dust.set(new THREE.Vector3(0, 0.25, z0 + 0.3), 0.7, (T - 0.4) / 0.9, { seed: 2.2, dens: 4.5, rise: 0.35, squash: 0.6, freq: 1.8, smoke: [0.46, 0.39, 0.31] });
      if (T > 1.08 && T < 2.1) dust2.set(new THREE.Vector3(0, 0.22, z1 - 0.4), 0.8, (T - 1.08) / 1.0, { seed: 6.4, dens: 4.5, rise: 0.35, squash: 0.55, freq: 1.8, smoke: [0.46, 0.39, 0.31] });
      // o alvo é atravessado: gira e voa pra trás
      if (T > 0.74) {
        const kb = seq([[0.74, 0], [1.3, 1, 'out']], T);
        R.place(0, -1.4 - 2.2 * kb, 0.9 * Math.sin(Math.PI * FX.clamp01((T - 0.74) / 0.6)));
        R.lean(-0.8 * Math.sin(Math.PI * FX.clamp01((T - 0.74) / 1.0)));
        R.spin(seq([[0.74, 0], [1.3, 6.28, 'out']], T));
        if (T < 0.95) ctx.shake(1 - (T - 0.74) / 0.21);
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const vs = [variant(0, 4), variant(1, 7), variant(2, 12), variant(3, 19)];
  window.MOBS.push({
    id: 'ninken', nome: 'Ninken', bioma: 'Floresta da Folha', tier: 'Fraco · tier 10',
    variants: vs.map(v => ({ nome: v.raca, v, def: defOf(v, false), low: defOf(v, true) })),
    randomVariant: () => { const i = Math.floor(Math.random() * 4), v = variant(i, Math.floor(Math.random() * 1e5)); return { nome: v.raca + ' sorteado', v, def: defOf(v, false), low: defOf(v, true) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['pinca', 'Mordida em pinça'], ['gatsuga', 'Gatsuga']],
    cam: 4.5, lookY: 0.6, lookZ: { pinca: -1.4, gatsuga: -2.0 }, camFar: { pinca: 5.5, gatsuga: 7.5 }, camYaw: { pinca: -1.0, gatsuga: -1.35 },
    info: {
      'Tamanho': 'Pug 0,7 bloco · Shiba 0,8 · Lobo 1,1 · Mastim 1,2',
      'Vida': '24 cada (tier 10)',
      'Jeito': 'Neutro, matilha de 3 a 4 de raças misturadas. Se você ataca um, todos rastreiam seu cheiro, cercam e atacam juntos.',
      'Mordida em pinça': 'Dois saltam e prendem seus braços (1 s sem atacar), o terceiro morde. Um golpe em qualquer um solta.',
      'Gatsuga': 'Agacha cavando o chão (0,5 s de aviso) e gira como broca em linha reta, atravessando quem estiver na frente.',
      'Solta': 'Raro: Pergaminho de Invocação Ninken (chama a matilha pra lutar com você).',
    },
  });
})();
