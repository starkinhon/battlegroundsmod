// Espadachim da Névoa (Mar da Névoa). Médio, tier 7. Ninja de Kirigakure com uma espada larga nas costas.
// Golpes: Corte Silencioso (levanta a névoa, some, a lâmina brilha 0,5 s atrás de você e corta) e
// Clones de Água (dois clones sobem de poças, correm na frente; o original corta enquanto você bate neles).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const N = window.NINJA, SU = window.SUNA;

  function variant(seed) {
    const v = N.variant(seed);
    const R = FX.rnd(seed + 501);
    const pick = a => a[Math.floor(R() * a.length)];
    v.vest = pick([[104, 116, 106], [92, 104, 112], [116, 118, 100]]);
    v.scarf = null;
    v.wrap = pick([[46, 50, 58], [70, 64, 60], [40, 56, 62]]);
    v.notch = 3 + Math.floor(R() * 4);
    return v;
  }
  function painters(v) {
    const P = N.painters(v);
    const S = v.seed;
    const shade = (base, q, s, lo = 0.8, hi = 1.12) => {
      const f = 0.62 - 0.4 * q.ny + 0.35 * (n3(q.ax * 0.25, q.ay * 0.25, q.az * 0.25, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    // colete de Kiri: placas verticais acolchoadas, alça diagonal da bainha cruzando o peito
    P.torso = q => {
      if ((q.f === 'west' || q.f === 'east') && q.ny < 0.36) return shade([30, 32, 38], q, 120);
      let c = shade(v.vest, q, 121, 0.8, 1.12);
      if (fr(q.ax * 0.45 + 0.5) < 0.12) c = mul(c, 0.82);
      const strap = (q.f === 'north' || q.f === 'south') && Math.abs((q.nx - 0.5) - (q.ny - 0.5) * (q.f === 'north' ? -0.9 : 0.9)) < 0.08;
      if (strap) c = shade([66, 48, 34], q, 122);
      return mul(c, 1 - 0.1 * q.ny);
    };
    // placa com as quatro ondas de Kiri
    P.plate = q => {
      const metal = mix([150, 156, 170], [214, 220, 230], 0.4 + 0.6 * n3(q.ax * 0.8, q.ay * 0.8, q.az * 0.8, 46));
      if (q.f !== 'north') return mul(metal, 0.82);
      if (q.nx < 0.07 || q.nx > 0.93 || q.ny < 0.1 || q.ny > 0.9) return mul(metal, 0.72);
      for (let i = 0; i < 4; i++) {
        const x0 = 0.26 + i * 0.16 + 0.05 * Math.sin(q.ny * 9 + i);
        if (Math.abs(q.nx - x0) < 0.035 && q.ny > 0.22 && q.ny < 0.8) return mul(metal, 0.56);
      }
      return metal;
    };
    P.blade = q => {
      // aço largo e gasto, com dentes de batalha na lombada e o fio claro
      let c = mix([110, 116, 128], [190, 196, 208], n3(q.ax * 0.6, q.ay * 0.6, q.az * 0.3, 123));
      if (q.ny > 0.86) c = [230, 236, 244];                                                        // fio
      if (q.ny < 0.1) {                                                                              // lombada com dentes
        const k = fr(q.az * v.notch / 22);
        if (k < 0.18) return null;
        c = mul(c, 0.7);
      }
      if (q.f === 'east' || q.f === 'west') c = mul(c, 0.92);
      if (n3(q.ax * 1.5, q.ay * 1.5, q.az * 0.4, 124 + S) > 0.78) c = mul(c, 0.82);                 // manchas de uso
      return c;
    };
    P.hilt = q => (fr(q.az * 0.9 + q.ay * 0.4) < 0.5 ? shade(v.wrap, q, 125) : mul(shade(v.wrap, q, 125), 0.7));
    P.guard = q => cel([60, 58, 62], n3(q.ax, q.ay, q.az, 126), 0.8, 1.2);
    return P;
  }
  function swordDef(P, low) {
    const boxes = [
      { x: -0.7, y: -0.7, z: -1.6, w: 1.4, h: 1.4, d: 6.4, p: P.hilt },
      { x: -1.1, y: -1.4, z: -2.6, w: 2.2, h: 5.4, d: 1, p: P.guard },
      { x: -0.4, y: -1, z: -25, w: 0.8, h: 6.6, d: 22.4, p: P.blade },
    ];
    return { name: 'espada', pivot: [0, 1.6, 0], boxes: low ? [boxes[0], boxes[2]] : boxes };
  }
  const defOf = v => { const P = painters(v); const d = N.rigDef(P, v, {}); SU.findNode(d, 'maoD').kids = [swordDef(P, false)]; return d; };
  const lowOf = v => {
    const P = painters(v); const d = N.rigLow(P, v);
    SU.findNode(d, 'bracoD').kids = [{ name: 'maoD', pivot: [0, 10.2, 0], kids: [swordDef(P, true)] }];
    return d;
  };

  // espada no ombro: braço dobrado na frente, a lâmina deitada por cima do ombro, ponta pra trás
  function shoulder(r, P) {
    r('bracoD', -0.5, 0.15, 0.25);
    if (P.anteD) r('anteD', -1.75, 0, 0); else r('bracoD', -1.4, 0, 0);
    r('espada', P.anteD ? -0.55 : -0.4, 0, 0);
  }

  // ---------- brilho da lâmina: estrela de quatro pontas ----------
  function glint() {
    const g = new THREE.Group();
    const mat = new THREE.MeshBasicMaterial({ color: 0xf2fbff, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false });
    for (const [w, h] of [[0.03, 0.5], [0.5, 0.03], [0.02, 0.26], [0.26, 0.02]]) {
      const m = new THREE.Mesh(new THREE.PlaneGeometry(w, h), mat); g.add(m);
    }
    g.children[2].rotation.z = g.children[3].rotation.z = Math.PI / 4;
    g.at = (p, k, cam) => { g.visible = k > 0.01; g.position.copy(p); if (cam) g.quaternion.copy(cam.quaternion); g.scale.setScalar(0.3 + 1.1 * k); mat.opacity = k; };
    return g;
  }
  // ---------- clone de água: o mesmo corpo, feito de água ----------
  function waterClone(v) {
    const d = defOf(v);
    const model = ENG.build(d), tex = ENG.paint(model, model.W, model.H), obj = ENG.toThree(model, tex);
    let map = null; obj.traverse(o => { if (o.isMesh && !map) map = o.material.map; });
    const mat = new THREE.MeshPhongMaterial({ map, alphaTest: 0.5, side: THREE.DoubleSide, color: 0x8fd0ff, emissive: 0x1a4a70, shininess: 90, specular: 0xffffff, transparent: true, opacity: 0.62 });
    obj.traverse(o => { if (o.isMesh) o.material = mat; });
    obj.model = model; obj.mat = mat;
    return obj;
  }
  function splash(seed) {
    return FX.debris(28, seed, [[150, 210, 250], [200, 236, 255], [110, 180, 230]]);
  }
  function puddle() {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, uniforms: { uA: { value: 0 }, uT: { value: 0 } },
      vertexShader: 'varying vec2 vP; void main(){ vP=position.xy; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: `varying vec2 vP; uniform float uA, uT;
        void main(){ float r=length(vP); float ring=smoothstep(0.06,0.0,abs(r-(0.35+0.5*fract(uT*1.7))))*(1.0-fract(uT*1.7));
          float k=smoothstep(0.95,0.7,r)*0.55+ring*0.6; if(k*uA<0.01) discard;
          gl_FragColor=vec4(mix(vec3(0.35,0.62,0.85),vec3(0.9,0.97,1.0),ring), k*uA); }`,
    });
    const m = new THREE.Mesh(new THREE.CircleGeometry(1, 32), mat);
    m.rotation.x = -Math.PI / 2;
    m.at = (p, s, a, t) => { m.visible = a > 0.01; m.position.copy(p); m.scale.setScalar(s); mat.uniforms.uA.value = a; mat.uniforms.uT.value = t; };
    return m;
  }

  function anim(P, t, mode, a, obj, ent, ctx) {
    const r = (k, x, y, z) => { if (!P[k]) return; const o = P[k].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const low = !P.tronco;
    if (mode === 'parado') {
      N.guard(r, P);
      r('bracoD', -0.08, 0, -0.18); if (P.anteD) r('anteD', 0.35, 0, 0);
      shoulder(r, P);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 0.85;
      for (const [s, off] of [['D', 0], ['E', 0.5]]) { r('perna' + s, loop(N.WALK_T, ph + off), 0, 0); r('canela' + s, loop(N.WALK_K, ph + off), 0, 0); }
      P.quadril.obj.position.y += loop(N.WALK_B, ph);
      shoulder(r, P);
      r('bracoE', loop(N.WALK_A, ph + 0.46), 0, -0.06);
      return;
    }
    const R = ctx.ref;
    // selo de mão: as duas mãos juntas na frente do peito
    const seal = k => {
      r('bracoE', -1.0 * k, -0.5 * k, 0); if (P.anteE) r('anteE', -1.0 * k, 0, 0);
      r('cabeca', 0.1 * k, 0, 0);
    };
    const swing = (wind, cut) => {
      // golpe largo da direita pra esquerda, a espada nas duas mãos
      P.quadril.obj.position.y += 1.2 * Math.max(wind, cut);
      r('quadril', 0, 0.5 * wind - 0.6 * cut, 0); r('tronco', 0.1 * cut, 0.6 * wind - 1.0 * cut, 0); r('cabeca', 0, -0.9 * wind + 1.2 * cut, 0);
      r('bracoD', -1.4 * Math.max(wind, cut), 0, -0.8 * wind + 0.4 * cut); if (P.anteD) r('anteD', -0.3 * wind, 0, 0);
      r('bracoE', -1.3 * Math.max(wind, cut), 0, 0.3 * wind); if (P.anteE) r('anteE', -0.5 * wind, 0, 0);
      r('espada', -1.45 * wind - 1.45 * cut, 0, 0);
      r('pernaD', 0.3 * wind - 0.5 * cut, 0, 0); r('canelaD', 0.4 * Math.max(wind, cut), 0, 0);
      r('pernaE', -0.4 * wind + 0.3 * cut, 0, 0); r('canelaE', 0.5 * Math.max(wind, cut), 0, 0);
    };
    if (mode === 'silencioso') {
      const T = t % 4.4;
      R.place(0, -3.0, 0);
      const fog = ctx.get('kirigakure', FX.volume);
      const ag = seq([[0.2, 0], [1.2, 0.42, 'out'], [3.2, 0.48], [4.3, 1, 'in']], T);
      fog.set(new THREE.Vector3(0, 1.0, -1.8), 4.2, ag, { seed: 8.2, dens: 2.2, rise: 0.15, squash: 0.55, freq: 0.9, smoke: [0.86, 0.9, 0.92] });
      const sealK = seq([[0.05, 0], [0.35, 1, 'out'], [0.9, 1], [1.1, 0]], T);
      N.guard(r, P);
      if (T < 1.1) { shoulder(r, P); seal(sealK); }
      // some na névoa e reaparece atrás do alvo
      const gone = T > 1.1 && T < 2.15;
      obj.visible = !gone;
      if (T >= 2.15) {
        obj.position.z = -4.3; obj.rotation.y = Math.PI;
        const cut = seq([[2.15, 0], [2.6, 0], [2.68, 1, 'snap'], [3.2, 1], [3.8, 0.4, 'io']], T);
        const wind = seq([[2.15, 1], [2.6, 1], [2.68, 0, 'snap']], T);
        swing(wind, cut);
        if (T > 2.66 && T < 3.1) ctx.slash(new THREE.Vector3(0, 1.25, -3.15), (T - 2.66) / 0.34);
        if (T > 2.66 && T < 2.85) ctx.shake(1 - (T - 2.66) / 0.19);
        if (T > 2.66) { R.lean(0.55 * Math.sin(Math.PI * FX.clamp01((T - 2.66) / 0.9))); R.place(0, -3.0 + 0.6 * seq([[2.66, 0], [3.1, 1, 'out']], T), 0); }
      }
      // o aviso: o brilho da lâmina atrás do alvo
      const gk = seq([[1.6, 0], [1.72, 1, 'snap'], [2.05, 0.6], [2.2, 0]], T);
      if (gk > 0) ctx.get('brilho', glint).at(new THREE.Vector3(0.45, 1.7, -4.1), gk, ctx.camera);
      return;
    }
    if (mode === 'clones') {
      const T = t % 4.6;
      R.place(0, -3.4, 0);
      const sealK = seq([[0.05, 0], [0.3, 1, 'out'], [0.8, 1], [1.0, 0]], T);
      N.guard(r, P);
      shoulder(r, P);
      seal(sealK);
      const spots = [[-1.3, -0.7], [1.3, -0.7]];
      spots.forEach(([x, z], i) => {
        const c = ctx.get('clone' + i + ent.meta.v.seed, () => waterClone(ent.meta.v));
        const born = seq([[0.35 + i * 0.12, 0], [0.95 + i * 0.12, 1, 'out']], T);
        const run = seq([[1.0, 0], [1.55, 1, 'in']], T);
        const popT = i === 0 ? 1.75 : 2.75;
        ctx.get('poca' + i, puddle).at(new THREE.Vector3(x, 0.01, z), 0.7, seq([[0.3, 0], [0.5, 1], [1.2, 1], [1.6, 0]], T), T);
        if (T > popT || born <= 0) {
          if (T > popT) {
            ctx.get('respingo' + i, () => splash(40 + i)).at(new THREE.Vector3(x * 0.35, 1.0, -2.75), T - popT);
            ctx.get('nevoa-agua' + i, FX.volume).set(new THREE.Vector3(x * 0.35, 1.0, -2.75), 0.8, (T - popT) / 0.9, { seed: 11 + i, dens: 3, rise: 0.3, freq: 2, smoke: [0.75, 0.88, 0.98] });
          }
          return;
        }
        c.visible = true;
        ENG.reset(c.model);
        const Q = c.model.parts, q = (k, ax, ay, az) => { if (!Q[k]) return; const o = Q[k].obj.rotation; o.x += ax; o.y += ay; o.z += az; };
        c.scale.set(-1 / 16, -born / 16, 1 / 16);
        c.position.set(x + (x * 0.35 - x) * run, 1.5 * born, z + (-2.75 - z) * run);
        c.rotation.set(0, 0, 0);
        c.mat.opacity = 0.62 * born;
        N.guard(q, Q);
        // correm curvados, espada pra trás
        const ph = T * 1.6 + i * 0.5;
        if (run > 0 && run < 1) for (const [s, off] of [['D', 0], ['E', 0.5]]) { q('perna' + s, 1.4 * loop(N.WALK_T, ph + off), 0, 0); q('canela' + s, 1.3 * loop(N.WALK_K, ph + off), 0, 0); }
        q('tronco', 0.35 * run, 0, 0); q('cabeca', -0.3 * run, 0, 0);
        q('bracoD', 0.6 * run, 0, 0.2); q('espada', -0.4, 0, 0);
      });
      // você bate no primeiro clone (estoura em água) enquanto o original vem por trás do segundo
      if (T > 1.55 && T < 2.0) R.punch(seq([[1.55, 0], [1.72, 1, 'snap'], [2.0, 0]], T));
      const dash = seq([[1.9, 0], [2.25, 1, 'snap'], [3.6, 1], [4.4, 0, 'io']], T);
      obj.position.z -= 2.05 * dash; obj.position.x += 0.9 * dash;
      if (T > 1.9) {
        const wind = seq([[1.9, 0], [2.25, 1, 'out'], [2.35, 0, 'snap']], T);
        const cut = seq([[2.27, 0], [2.37, 1, 'snap'], [3.4, 1], [3.9, 0, 'io']], T);
        swing(wind, cut);
        if (T > 2.35 && T < 2.75) ctx.slash(new THREE.Vector3(0.2, 1.2, -3.3), (T - 2.35) / 0.34);
        if (T > 2.35 && T < 2.55) ctx.shake(1 - (T - 2.35) / 0.2);
        if (T > 2.35) R.lean(-0.5 * Math.sin(Math.PI * FX.clamp01((T - 2.35) / 0.8)));
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(6), variant(19), variant(33)];
  fixed[0].mask = true; fixed[1].mask = false; fixed[1].scar = true;
  window.MOBS.push({
    id: 'espadachim_nevoa', nome: 'Espadachim da Névoa', bioma: 'Mar da Névoa', tier: 'Médio · tier 7',
    variants: fixed.map((v, i) => ({ nome: ['Mascarado', 'Cicatriz', 'Sorteado'][i], v, def: defOf(v), low: lowOf(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, def: defOf(v), low: lowOf(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['silencioso', 'Corte silencioso'], ['clones', 'Clones de água']],
    cam: 6, lookY: 1.2, lookZ: { silencioso: -2.4, clones: -1.8 }, camFar: { silencioso: 9, clones: 8.5 }, camYaw: { silencioso: -0.9, clones: -0.8 },
    info: {
      'Tamanho': '1,85 bloco; a espada tem 1,6 bloco',
      'Vida': '140 (tier 7)',
      'Jeito': 'Hostil, anda sobre a água e nas pontes de corda. Quando te vê, levanta a névoa (12 blocos) e você só enxerga a silhueta dele de perto.',
      'Corte silencioso': 'Faz o selo, some na névoa e aparece atrás de você. Sem som de passo: o único aviso é o brilho da lâmina 0,5 s antes do corte.',
      'Clones de água': 'Dois clones sobem de poças e correm na sua frente. Um golpe estoura cada um em água; enquanto você bate neles, o original vem pelo lado e corta.',
      'Solta': 'Bandana da Névoa, peixe; raro: espada partida.',
      'Variações': 'Máscara de pano ou cicatriz, cabelo, pele, cor do colete e os dentes da lombada da espada sorteados.',
    },
  });
})();
