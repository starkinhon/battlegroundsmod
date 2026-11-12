// Tubarão de Água (Mar da Névoa). Médio, tier 8. Um tubarão feito de chakra e água do mar: dá pra ver através dele.
// Golpes: Salto (some no fundo, pula da água num arco e morde quem está na ponte) e
// Torpedo (gira em espiral rente à água e te atropela: empurra longe e encharca, lento 2 s).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  function painters(v) {
    const water = s => q => {
      // costas escuras, barriga clara, correntes de espuma correndo pelo corpo no sentido do nado
      const up = q.f === 'top' ? 1 : q.f === 'bottom' ? 0 : 1 - q.ny;
      let c = mix(v.belly, v.back, Math.pow(up, 0.8));
      c = cel(c, n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.12, s + v.seed), 0.86, 1.1);
      const flow = fr(q.az * 0.09 + 0.8 * n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.05, s + 4 + v.seed));
      if (flow < 0.07) c = mix(c, [236, 248, 255], 0.75);
      return c;
    };
    const body = water(1), fin = water(2);
    const head = q => {
      let c = body(q);
      if ((q.f === 'west' || q.f === 'east') && Math.abs(q.ny - 0.32) < 0.1 && Math.abs(q.nz - 0.22) < 0.07) c = [250, 252, 255];   // olho de luz
      if ((q.f === 'west' || q.f === 'east') && q.nz > 0.45 && q.nz < 0.75 && q.ny > 0.25 && q.ny < 0.55 && fr(q.nz * 14) < 0.25) c = mul(c, 0.8); // guelras
      return c;
    };
    const jaw = q => {
      let c = body(q);
      if (q.f === 'top' && (q.nx < 0.14 || q.nx > 0.86 || q.nz < 0.12) && fr((q.nx + q.nz) * 9) < 0.5) c = [244, 250, 255];        // dentes
      if (q.f === 'top' && q.nx > 0.14 && q.nx < 0.86 && q.nz > 0.12) c = mul(v.back, 0.45);                                       // boca
      return c;
    };
    const teeth = q => (fr((q.nx + q.nz) * 9) < 0.5 ? [244, 250, 255] : mul(v.back, 0.45));
    return { body, fin, head, jaw, teeth };
  }
  function defOf(v, low) {
    const P = painters(v);
    const fin = (name, pivot, rot, b) => ({ name, pivot, rot, boxes: [{ ...b, p: P.fin }] });
    const tail = {
      name: 'rabo', pivot: [0, 0, 9], boxes: [{ x: -1.8, y: -2, z: 0, w: 3.6, h: 4, d: 8, p: P.body }],
      kids: [
        fin('caudaC', [0, -0.5, 7.5], [-0.75, 0, 0], { x: -0.5, y: -9, z: -1, w: 1, h: 9, d: 3.4 }),
        fin('caudaB', [0, 0.5, 7.5], [0.65, 0, 0], { x: -0.5, y: 0, z: -1, w: 1, h: 6, d: 3 }),
      ],
    };
    const mid = {
      name: 'meio', pivot: [0, 0, 10], boxes: [{ x: -3.6, y: -3.8, z: 0, w: 7.2, h: 7.4, d: 9, p: P.body }],
      kids: [tail].concat(low ? [] : [fin('dorsal2', [0, -3.6, 6], [-0.5, 0, 0], { x: -0.4, y: -2.6, z: 0, w: 0.8, h: 2.6, d: 2 })]),
    };
    const torso = {
      name: 'corpo', pivot: [0, 18, -4], boxes: [{ x: -4.6, y: -4.6, z: 0, w: 9.2, h: 9.2, d: 10, p: P.body }],
      kids: [
        mid,
        fin('dorsal', [0, -4.4, 2], [-0.45, 0, 0], { x: -0.5, y: -7.5, z: 0, w: 1, h: 7.5, d: 4.6 }),
        fin('peitD', [-4.2, 3, 2.4], [0.35, 0, -0.55], { x: -8, y: -0.4, z: 0, w: 8, h: 0.8, d: 3.8 }),
        fin('peitE', [4.2, 3, 2.4], [0.35, 0, 0.55], { x: 0, y: -0.4, z: 0, w: 8, h: 0.8, d: 3.8 }),
        {
          name: 'cabeca', pivot: [0, -0.6, 0], boxes: [{ x: -4.2, y: -4, z: -8.6, w: 8.4, h: 6.2, d: 8.6, p: P.head }, { x: -3, y: -3.2, z: -11, w: 6, h: 4.4, d: 2.6, p: P.head }],
          kids: [{ name: 'mandibula', pivot: [0, 2.2, -1], boxes: [{ x: -3.8, y: 0, z: -8.2, w: 7.6, h: 2.2, d: 8.2, p: P.jaw }].concat(low ? [] : [{ x: -3.6, y: -1.1, z: -8, w: 7.2, h: 1.1, d: 0.8, p: P.teeth }]) }],
        },
      ],
    };
    return { name: 'raiz', pivot: [0, 0, 0], kids: [torso] };
  }
  // material de água: translúcido, brilho especular, a cor vem da pintura
  function waterify(ent, obj) {
    if (ent._water) return;
    let map = null; obj.traverse(o => { if (o.isMesh && !map) map = o.material.map; });
    const mat = new THREE.MeshPhongMaterial({ map, side: THREE.DoubleSide, transparent: true, opacity: 0.74, shininess: 110, specular: 0xffffff, emissive: 0x0c2c48, depthWrite: false });
    obj.traverse(o => { if (o.isMesh) o.material = mat; });
    ent._water = mat;
  }
  // rastro na superfície: anel de espuma que abre e some
  function wake() {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, uniforms: { uA: { value: 0 }, uSeed: { value: 0 } },
      vertexShader: 'varying vec2 vP; void main(){ vP=position.xy; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: `varying vec2 vP; uniform float uA, uSeed;
        float h(float x){ return fract(sin(x*71.3+uSeed)*43758.5); }
        float n(float x){ float i=floor(x), f=fract(x); f=f*f*(3.0-2.0*f); return mix(h(i),h(i+1.0),f); }
        void main(){ float r=length(vP), a=atan(vP.y,vP.x); float e=0.8+0.12*(n(a*5.0)-0.5);
          float k=smoothstep(0.1,0.0,abs(r-e))*(0.6+0.4*n(a*17.0)); if(k*uA<0.01) discard;
          gl_FragColor=vec4(vec3(0.92,0.97,1.0), k*uA); }`,
    });
    const m = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), mat);
    m.rotation.x = -Math.PI / 2;
    m.at = (p, s, a, seed) => { m.visible = a > 0.01; m.position.copy(p); m.scale.setScalar(s); mat.uniforms.uA.value = a; mat.uniforms.uSeed.value = seed; };
    return m;
  }
  const drops = seed => FX.debris(32, seed, [[150, 210, 250], [210, 238, 255], [110, 176, 226]]);
  // espiral de água em volta do torpedo: gotas em hélice, cada uma com raio e atraso próprios
  function helix(n, seed) {
    const R = FX.rnd(seed), g = new THREE.Group(), list = [];
    for (let i = 0; i < n; i++) {
      const s = 0.03 + R() * 0.05;
      const m = new THREE.Mesh(new THREE.BoxGeometry(s, s, s * 2.5), new THREE.MeshBasicMaterial({ color: new THREE.Color(0.75 + R() * 0.25, 0.9 + R() * 0.1, 1), transparent: true }));
      g.add(m); list.push({ m, a: R() * 6.28, r: 0.5 + R() * 0.4, back: R() * 2.2, sp: 6 + R() * 5 });
    }
    g.at = (o, t, k) => {
      g.visible = k > 0.01;
      for (const p of list) {
        const a = p.a + t * p.sp;
        p.m.position.set(o.x + Math.cos(a) * p.r, o.y + Math.sin(a) * p.r * 0.8, o.z + p.back);
        p.m.rotation.set(0, 0, a);
        p.m.material.opacity = k * (1 - p.back / 2.4);
      }
    };
    return g;
  }

  // nado: a onda passa da cabeça pro rabo, o rabo bate mais forte que o resto
  const SWIM = [[0, -1], [0.35, 0.6], [0.5, 1], [0.85, -0.6]];
  function swim(r, ph, amt) {
    r('cabeca', 0, 0.06 * loop(SWIM, ph) * amt, 0);
    r('meio', 0, -0.16 * loop(SWIM, ph - 0.15) * amt, 0);
    r('rabo', 0, -0.32 * loop(SWIM, ph - 0.3) * amt, 0);
    r('caudaC', 0, -0.25 * loop(SWIM, ph - 0.42) * amt, 0); r('caudaB', 0, -0.25 * loop(SWIM, ph - 0.42) * amt, 0);
    r('peitD', 0, 0, 0.1 * loop(SWIM, ph + 0.25) * amt); r('peitE', 0, 0, -0.1 * loop(SWIM, ph + 0.25) * amt);
  }

  function anim(P, t, mode, a, obj, ent, ctx) {
    waterify(ent, obj);
    const r = (k, x, y, z) => { if (!P[k]) return; const o = P[k].obj.rotation; o.x += x; o.y += y; o.z += z; };
    if (mode === 'parado') {
      // metade dentro da água: só as costas e a barbatana de fora, rondando devagar
      obj.position.y -= 0.42;
      swim(r, t * 0.45, 0.6);
      ctx.get('rastro', wake).at(new THREE.Vector3(0, 0.02, -0.4), 1.5, 0.35, 1);
      return;
    }
    if (mode === 'andar') {
      obj.position.y -= 0.42;
      swim(r, t * 1.3, 1.2);
      for (let i = 0; i < 3; i++) {
        const age = fr(t * 0.9 + i / 3);
        ctx.get('rastro' + i, wake).at(new THREE.Vector3(0, 0.02, -0.6 + age * 1.6), 0.9 + age * 1.4, (1 - age) * 0.8, i * 3.1);
      }
      return;
    }
    const R = ctx.ref;
    if (mode === 'salto') {
      const T = t % 3.8;
      R.place(0, -3.4, 0);
      // 1) afunda e some  2) a barbatana corre até você (aviso)  3) pula num arco de boca aberta  4) mergulha do outro lado
      const sink = seq([[0.1, 0], [0.5, 1, 'in']], T);
      const run = seq([[0.5, 0], [1.2, 1, 'io']], T);
      const jt = FX.clamp01((T - 1.25) / 0.9);
      const jumping = T > 1.25 && T < 2.15;
      if (!jumping) {
        obj.position.y -= 0.42 + 0.9 * sink * (T < 1.25 ? 1 : 1) - (T > 1.0 && T < 1.25 ? 0.6 * (T - 1.0) / 0.25 : 0);
        obj.position.z -= 1.4 * run + (T >= 2.15 ? 4.6 : 0);
        if (T > 0.5 && T < 1.25) ctx.get('rastro', wake).at(new THREE.Vector3(0, 0.02, -1.6 * run - 0.4), 0.7 + 0.4 * run, 0.8, 2);
        swim(r, T * 2.2, 1.3);
      } else {
        // arco: sai em -1.4, passa por cima do alvo e cai em -6
        const z = -1.4 - 4.6 * jt, y = -0.4 + 3.2 * Math.sin(Math.PI * jt);
        obj.position.z += z; obj.position.y += y;
        obj.rotation.x = 0.9 * Math.cos(Math.PI * jt);
        swim(r, T * 2.2, 0.5);
        r('mandibula', 0.75 * seq([[1.25, 0.2], [1.5, 1, 'out'], [1.62, 0, 'snap']], T), 0, 0);
        r('cabeca', -0.25 * seq([[1.25, 0], [1.5, 1], [1.62, 0, 'snap']], T), 0, 0);
      }
      if (T > 1.2) {
        ctx.get('saida', FX.volume).set(new THREE.Vector3(0, 0.3, -1.5), 1.0, (T - 1.2) / 1.0, { seed: 6.2, dens: 4, rise: 0.6, freq: 1.8, smoke: [0.82, 0.92, 1.0] });
        ctx.get('gotas', () => drops(51)).at(new THREE.Vector3(0, 0.2, -1.5), T - 1.2);
        ctx.get('anel1', wake).at(new THREE.Vector3(0, 0.02, -1.5), 0.6 + 1.8 * FX.clamp01((T - 1.2) / 1.2), 1 - FX.clamp01((T - 1.2) / 1.2), 5);
      }
      if (T > 2.12) {
        ctx.get('entrada', FX.volume).set(new THREE.Vector3(0, 0.3, -6), 1.0, (T - 2.12) / 1.0, { seed: 9.6, dens: 4, rise: 0.6, freq: 1.8, smoke: [0.82, 0.92, 1.0] });
        ctx.get('anel2', wake).at(new THREE.Vector3(0, 0.02, -6), 0.6 + 1.8 * FX.clamp01((T - 2.12) / 1.2), 1 - FX.clamp01((T - 2.12) / 1.2), 7);
      }
      if (T > 1.58) {
        // a mordida pega no alto: o jogador é puxado e cai
        const h = T - 1.58;
        if (h < 0.4) ctx.slash(new THREE.Vector3(0, 1.4, -3.3), h / 0.3);
        if (h < 0.2) ctx.shake(1 - h / 0.2);
        const k = seq([[1.58, 0], [2.1, 1, 'out']], T);
        R.place(0, -3.4 - 1.0 * k, 0.7 * Math.sin(Math.PI * k));
        R.lean(0.7 * Math.sin(Math.PI * FX.clamp01(h / 0.9)));
      }
      return;
    }
    if (mode === 'torpedo') {
      const T = t % 3.4;
      R.place(0, -4.4, 0);
      // recua girando (aviso: a espiral de água se forma), dispara rente à água e atropela
      const spin = seq([[0.1, 0], [0.8, 1, 'in'], [2.0, 1], [2.6, 0, 'out']], T);
      const dash = seq([[0.8, 0], [1.1, 1, 'snap'], [2.4, 1], [3.2, 0, 'io']], T);
      obj.position.y -= 0.15;
      obj.position.z += 0.6 * seq([[0.1, 0], [0.8, 1, 'out'], [0.85, 0, 'snap']], T) - 4.2 * dash;
      r('corpo', 0, 0, 9 * Math.pow(spin, 1.4) * T);   // gira no próprio eixo do corpo
      swim(r, T * 3, 0.4);
      ctx.get('espiral', () => helix(40, 61)).at(new THREE.Vector3(0, 0.25, obj.position.z - 0.2), T, spin);
      if (T > 0.8) for (let i = 0; i < 3; i++) {
        const age = FX.clamp01((T - 0.8 - i * 0.12) / 1.0);
        ctx.get('esteira' + i, wake).at(new THREE.Vector3(0, 0.02, -1.2 * i - 1.0), 0.6 + 1.6 * age, (1 - age) * 0.9, i * 2.3);
      }
      if (T > 1.05) {
        const h = T - 1.05;
        ctx.get('impacto', FX.volume).set(new THREE.Vector3(0, 1.0, -4.3), 1.2, h / 1.1, { seed: 3.7, dens: 4.5, rise: 0.4, freq: 1.8, smoke: [0.82, 0.92, 1.0] });
        ctx.get('gotas2', () => drops(52)).at(new THREE.Vector3(0, 1.0, -4.3), h);
        if (h < 0.25) ctx.shake(1 - h / 0.25);
        const k = seq([[1.05, 0], [1.7, 1, 'out']], T);
        R.place(0, -4.4 - 3.4 * k, 0.9 * Math.sin(Math.PI * k));
        R.lean(-1.1 * Math.sin(Math.PI * FX.clamp01(h / 1.1)));
        R.spin(seq([[1.05, 0], [1.7, 2.2, 'out']], T));
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const SK = [
    { nome: 'Mar', seed: 1, back: [40, 104, 176], belly: [160, 214, 244] },
    { nome: 'Abissal', seed: 2, back: [30, 52, 120], belly: [96, 160, 220] },
  ];
  window.MOBS.push({
    id: 'tubarao_agua', nome: 'Tubarão de Água', bioma: 'Mar da Névoa', tier: 'Médio · tier 8',
    variants: SK.map(v => ({ nome: v.nome, v, def: defOf(v, false), low: defOf(v, true) })),
    anim, modes: [['parado', 'Rondar'], ['andar', 'Nadar'], ['salto', 'Salto'], ['torpedo', 'Torpedo']],
    cam: 6, lookY: 0.6, lookZ: { salto: -3, torpedo: -2.4 }, camFar: { salto: 9, torpedo: 9 }, camYaw: { salto: -1.3, torpedo: -1.25 }, refX: 2.8,
    info: {
      'Tamanho': '2,4 blocos de comprimento',
      'Vida': '110 (tier 8)',
      'Jeito': 'Hostil, só na água. Ronda as pontes e quem cai no mar. Não sai da água: fique longe da borda e ele desiste.',
      'Salto': 'Afunda, a barbatana corre até você (0,7 s de aviso) e ele pula num arco de boca aberta por cima de quem está na ponte: mordida e te arrasta 1 bloco.',
      'Torpedo': 'Gira e recua enquanto a espiral de água se forma, depois dispara rente à água: te joga 3,5 blocos e te deixa lento 2 s (encharcado).',
      'Solta': 'Gota de chakra d\'água; raro: dente de tubarão.',
    },
  });
})();
