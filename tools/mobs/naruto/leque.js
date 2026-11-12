// Ninja do Leque (Deserto do Vento). Médio, tier 8. Ninja de Suna com um leque de guerra do tamanho dele.
// Golpes: Kamaitachi (abre o leque e varre: lâminas de vento que cortam e jogam longe) e
// Leque Fechado (pancada de baixo pra cima com o leque fechado: te joga pro alto).
// Também define window.SUNA (roupa de Suna), usada pelo Marionetista.
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const N = window.NINJA;

  // ---------- roupa de Suna ----------
  function variant(seed) {
    const v = N.variant(seed);
    const R = FX.rnd(seed + 313);
    const pick = a => a[Math.floor(R() * a.length)];
    v.vest = pick([[186, 160, 114], [172, 148, 106], [198, 174, 128]]);
    v.cloth = pick([[112, 54, 42], [88, 62, 50], [66, 60, 72], [120, 92, 60]]);
    v.hood = R() < 0.6;
    v.hoodCol = pick([[216, 198, 162], [192, 162, 122], [152, 98, 72], [226, 214, 190]]);
    v.skin = pick([[214, 168, 126], [190, 140, 102], [168, 120, 86], [236, 196, 156]]);
    v.scarf = null;
    return v;
  }
  function painters(v) {
    const P = N.painters(v);
    const S = v.seed;
    const shade = (base, q, s, lo = 0.8, hi = 1.12) => {
      const f = 0.62 - 0.4 * q.ny + 0.35 * (n3(q.ax * 0.25, q.ay * 0.25, q.az * 0.25, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const cloth = (q, s = 60) => {
      let c = shade(v.cloth, q, s);
      if (fr(q.ay * 0.55 + n3(q.ax * 0.3, q.ay * 0.2, q.az * 0.3, s + 1 + S) * 0.5) < 0.12) c = mul(c, 0.84);  // dobras do pano
      return c;
    };
    // colete de Suna: acolchoado em faixas horizontais, alça larga no ombro
    P.torso = q => {
      if ((q.f === 'west' || q.f === 'east') && q.ny < 0.3) return cloth(q);
      let c = shade(v.vest, q, 61, 0.8, 1.12);
      const band = fr(q.ay * 0.42 + 0.3);
      if (band < 0.14) c = mul(c, 0.82);
      if (q.f === 'north' && Math.abs(q.nx - 0.5) < 0.04) c = mul(c, 0.72);
      if ((q.f === 'north' || q.f === 'south') && q.ny < 0.22 && (q.nx < 0.3 || q.nx > 0.7)) c = mix(c, [236, 222, 190], 0.35); // alças
      if (q.ny > 0.9) c = mul(c, 0.8);
      return mul(c, 1 - 0.1 * q.ny);
    };
    P.pouchV = q => shade(mul(v.vest, 0.86), q, 62);
    P.collar = q => cloth(q, 63);
    P.sleeve = q => cloth(q, 64);
    P.upperArm = q => cloth(q, 64);
    P.wrapArm = q => {
      let c = cloth(q, 65);
      if (fr(q.ay * 0.9 + q.az * 0.2) < 0.22) c = mix(c, [220, 206, 176], 0.5);                     // faixa no antebraço
      return c;
    };
    P.pants = q => cloth(q, 66);
    P.holster = q => shade([74, 60, 50], q, 67);
    P.shinWrap = q => {
      let c = shade([226, 214, 188], q, 68, 0.84, 1.06);
      if (fr(q.ay * 1.1 + q.ax * 0.15 + q.az * 0.15) < 0.2) c = mul(c, 0.84);
      return c;
    };
    // capuz: pano inteiro cobrindo a cabeça (no deserto ninguém deixa o cabelo ao sol)
    const baseTop = P.hairTop, baseBack = P.hairBack, baseBand = P.band;
    const hood = (q, s) => {
      let c = shade(v.hoodCol, q, s, 0.8, 1.1);
      const fold = fr(q.ax * 0.18 + q.az * 0.18 + q.ay * 0.45 + 0.6 * n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + 2 + S));
      if (fold < 0.16) c = mul(c, 0.82);
      return c;
    };
    P.hairTop = q => (v.hood ? hood(q, 70) : baseTop(q));
    P.hairBack = q => (v.hood ? (q.ny > 0.9 + 0.1 * n3(Math.floor(q.px) * 1.3, S, 0, 71) ? null : hood(q, 72)) : baseBack(q));
    P.band = q => (v.hood && q.f !== 'north' ? hood(q, 73) : baseBand(q));
    // placa com a ampulheta de Suna gravada
    P.plate = q => {
      const metal = mix([150, 156, 170], [214, 220, 230], 0.4 + 0.6 * n3(q.ax * 0.8, q.ay * 0.8, q.az * 0.8, 46));
      if (q.f !== 'north') return mul(metal, 0.82);
      if (q.nx < 0.07 || q.nx > 0.93 || q.ny < 0.1 || q.ny > 0.9) return mul(metal, 0.72);
      const dx = Math.abs(q.nx - 0.5), dy = q.ny - 0.52;
      if (Math.abs(dy) < 0.3 && dx < Math.abs(dy) * 0.9 + 0.03 && dx > Math.abs(dy) * 0.9 - 0.1) return mul(metal, 0.58);
      if (Math.abs(Math.abs(dy) - 0.3) < 0.05 && dx < 0.3) return mul(metal, 0.58);
      return metal;
    };
    P.lowTorso = q => (q.ay > 11.2 ? P.pants(q) : q.ay > 10.3 ? P.belt(q) : q.ay < 2.4 ? P.collar(q) : P.torso({ ...q, ny: (q.ay - 2) / 8.3 }));
    P.lowArm = q => (q.ay < 10 ? P.wrapArm(q) : P.glove({ ...q, ny: (q.ay - 10) / 2.2 }));
    P.lowLeg = q => (q.ay < 19 ? P.pants(q) : q.ay < 23.2 ? P.shinWrap(q) : P.sandal({ ...q, ny: 0.7 }));
    P.lowHair = q => (v.hood ? hood(q, 74) : baseTop({ ...q, ny: q.ny * 0.6 }));
    return P;
  }
  function findNode(d, name) {
    if (d.name === name) return d;
    for (const k of d.kids || []) { const f = findNode(k, name); if (f) return f; }
    return null;
  }
  window.SUNA = { variant, painters, findNode };

  // ---------- o leque: 9 varetas que abrem em leque ----------
  const NV = 9, SPREAD = 0.3;
  function fanPainters(v) {
    const paper = mix([238, 228, 204], v.hoodCol, 0.2);
    const edge = [[150, 40, 36], [44, 60, 104], [56, 56, 62]][v.seed % 3];
    const panel = i => q => {
      let c = cel(paper, n3(q.ax * 0.4, q.ay * 0.2, q.az * 0.4, 80 + i), 0.86, 1.06);
      if (i % 2) c = mul(c, 0.93);                                                                       // as dobras alternam luz e sombra
      if (Math.abs(q.nx - 0.5) < 0.07) c = [92, 64, 42];                                                // vareta
      if (q.ny > 0.86) c = edge;                                                                        // barra pintada na ponta
      if (q.ny > 0.8 && q.ny <= 0.86) c = mul(edge, 0.7);
      if (q.f === 'east' || q.f === 'west' || q.f === 'bottom') c = mul(c, 0.8);
      return c;
    };
    const wood = q => cel([84, 58, 38], n3(q.ax * 0.5, q.ay * 1.5, q.az * 0.5, 81), 0.8, 1.14);
    return { panel, wood };
  }
  function fanDef(v, low) {
    const F = fanPainters(v);
    if (low) return { name: 'leque', pivot: [0, 1.6, 0], boxes: [{ x: -2.4, y: 0, z: -0.6, w: 4.8, h: 21, d: 1.2, p: F.panel(0) }] };
    const kids = [];
    for (let i = 0; i < NV; i++) kids.push({
      name: 'v' + i, pivot: [0, 0, (i - 4) * 0.05], boxes: [
        { x: -0.6, y: 0, z: -0.2, w: 1.2, h: 9, d: 0.4, p: F.wood },
        { x: -2.9, y: 9, z: -0.25, w: 5.8, h: 12, d: 0.5, p: F.panel(i) },
      ],
    });
    return { name: 'leque', pivot: [0, 1.6, 0], boxes: [{ x: -0.9, y: -1.4, z: -0.9, w: 1.8, h: 2.2, d: 1.8, p: F.wood }], kids };
  }
  const defOf = v => {
    const d = N.rigDef(painters(v), v, { noTails: v.hood });
    findNode(d, 'maoD').kids = [fanDef(v, false)];
    return d;
  };
  const lowOf = v => {
    const d = N.rigLow(painters(v), v);
    const b = findNode(d, 'bracoD');
    b.kids = [{ name: 'maoD', pivot: [0, 10.2, 0], kids: [fanDef(v, true)] }];
    return d;
  };
  // abre (0..1): as varetas giram no próprio eixo e o papel estica
  function fan(P, r, open, aimUp) {
    // segurando pelo cabo: o leque segue o braço; aimUp gira o plano dele pra ficar de pé
    r('leque', 0, aimUp, 0);
    for (let i = 0; i < NV; i++) {
      if (!P['v' + i]) continue;
      r('v' + i, 0, 0, (i - 4) * SPREAD * open);
      P['v' + i].obj.scale.x = 0.42 + 0.58 * open;
    }
  }

  // ---------- lâminas de vento: meia-lua fina, cada uma com borda, largura e atraso próprios ----------
  function blade(seed) {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, side: THREE.DoubleSide, blending: THREE.AdditiveBlending,
      uniforms: { uA: { value: 0 }, uSeed: { value: seed }, uT: { value: 0 } },
      vertexShader: 'varying vec2 vP; void main(){ vP=position.xy; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: `varying vec2 vP; uniform float uA, uSeed, uT;
        float h(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))+uSeed)*43758.5); }
        float n(vec2 p){ vec2 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
          return mix(mix(h(i),h(i+vec2(1,0)),f.x), mix(h(i+vec2(0,1)),h(i+vec2(1,1)),f.x), f.y); }
        void main(){
          float r = length(vP), a = atan(vP.y, vP.x);
          // arco de ~140 graus, mais grosso no meio, afinando nas pontas
          float span = smoothstep(1.25, 0.2, abs(a));
          float rr = 0.8 + 0.05*(n(vec2(a*4.0, uSeed))-0.5);
          float w = 0.035 + 0.075*span;
          float d = abs(r - rr);
          float k = smoothstep(w, 0.0, d) * span;
          // fios de vento correndo pelo arco
          k *= 0.55 + 0.45*n(vec2(a*14.0 - uT*30.0, r*30.0));
          if(k*uA < 0.01) discard;
          vec3 c = mix(vec3(0.85,0.8,0.68), vec3(1.0,0.99,0.95), smoothstep(w*0.6, 0.0, d));
          gl_FragColor = vec4(c*k*uA*1.7, min(1.0, k*uA*1.4));
        }`,
    });
    const m = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), mat);
    m.frustumCulled = false;
    m.set = (a, t) => { mat.uniforms.uA.value = a; mat.uniforms.uT.value = t; };
    return m;
  }
  function blades(n, seed) {
    const R = FX.rnd(seed), g = new THREE.Group(), list = [];
    for (let i = 0; i < n; i++) {
      const m = blade(i * 3.7 + seed);
      g.add(m);
      list.push({ m, y: 0.5 + R() * 1.4, x: (R() - 0.5) * 1.4, lag: i * 0.07 + R() * 0.05, s: 0.7 + R() * 0.9, tilt: (R() - 0.5) * 0.9, sp: 0.85 + R() * 0.4 });
    }
    g.at = (o, dist, age) => {
      g.visible = true;
      for (const b of list) {
        const f = (age - b.lag) * b.sp;
        b.m.visible = f > 0 && f < 1;
        if (!b.m.visible) continue;
        // deitada de lado (o arco abre pra frente) e crescendo enquanto voa
        b.m.position.set(o.x + b.x * (0.4 + f), o.y + b.y - 1.0, o.z - f * dist);
        // plano de pé ao longo do voo, cada uma girada no próprio eixo: o arco abre pra frente
        b.m.rotation.set(0, 0, 0); b.m.rotateZ(b.tilt * 2.6); b.m.rotateY(Math.PI / 2);
        b.m.scale.setScalar(b.s * (0.6 + f * 1.1));
        b.m.set(Math.sin(Math.PI * Math.min(1, f * 1.3)) * (1 - f * 0.4), age);
      }
    };
    return g;
  }
  // areia levantada: grãos com tamanho, cor e caminho próprios, levados pelo vento
  function sand(n, seed) {
    const R = FX.rnd(seed), g = new THREE.Group(), list = [];
    const cols = [[214, 186, 134], [196, 164, 112], [228, 206, 160], [176, 146, 100]];
    for (let i = 0; i < n; i++) {
      const c = cols[Math.floor(R() * cols.length)], s = 0.02 + R() * 0.04;
      const m = new THREE.Mesh(new THREE.BoxGeometry(s, s, s), new THREE.MeshBasicMaterial({ color: new THREE.Color(c[0] / 255, c[1] / 255, c[2] / 255), transparent: true }));
      g.add(m);
      list.push({ m, x: (R() - 0.5) * 2.4, y: R() * 1.6, lag: R() * 0.4, sp: 0.6 + R() * 0.7, wob: R() * 6.28 });
    }
    g.at = (o, dist, age) => {
      g.visible = true;
      for (const p of list) {
        const f = (age - p.lag) * p.sp;
        p.m.visible = f > 0 && f < 1;
        if (!p.m.visible) continue;
        p.m.position.set(o.x + p.x * (0.5 + f) + 0.15 * Math.sin(p.wob + f * 9), o.y + p.y * f + 0.05, o.z - f * dist);
        p.m.material.opacity = Math.sin(Math.PI * f);
      }
    };
    return g;
  }

  function anim(P, t, mode, a, obj, ent, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const low = !P.tronco;
    // parado: o leque fechado apoiado no ombro, em pé atrás do braço
    const rest = () => { r('bracoD', -0.15, 0, 0.2); r('leque', -0.35, 0, 0.4); fan(P, r, 0, Math.PI / 2); };   // fechado, ponta no chão, como um cajado
    if (mode === 'parado') {
      N.guard(r, P);
      r('bracoD', -0.08, 0, -0.18); if (P.anteD) r('anteD', 0.35, 0, 0);    // desfaz a guarda do braço direito
      rest();
      return;
    }
    if (mode === 'andar') {
      const ph = t * 0.95;
      for (const [s, off] of [['D', 0], ['E', 0.5]]) {
        r('perna' + s, loop(N.WALK_T, ph + off), 0, 0);
        r('canela' + s, loop(N.WALK_K, ph + off), 0, 0);
      }
      P.quadril.obj.position.y += loop(N.WALK_B, ph);
      rest();
      r('bracoE', loop(N.WALK_A, ph + 0.46), 0, -0.06);
      return;
    }
    const R = ctx.ref;
    if (mode === 'kamaitachi') {
      const T = t % 3.6;
      R.place(0, -4.2, 0);
      N.guard(r, P);
      // 1) gira o leque pra frente e abre num estalo; 2) recua torcendo o tronco; 3) varre da direita pra esquerda
      const lift = seq([[0.1, 0], [0.45, 1, 'out']], T);
      const open = seq([[0.42, 0], [0.52, 1, 'snap']], T);
      const wind = seq([[0.5, 0], [0.95, 1, 'out'], [1.05, 1]], T);
      const sweep = seq([[1.05, 0], [1.17, 1, 'snap'], [1.6, 1], [2.4, 0, 'io']], T);
      const twist = 0.95 * wind - 1.9 * sweep;
      P.quadril.obj.position.y += 1.2 * Math.max(wind, sweep);
      r('pernaD', -0.5 * sweep + 0.3 * wind, 0, 0); r('canelaD', 0.5 * Math.max(wind, sweep), 0, 0);
      r('pernaE', 0.25 * wind - 0.2 * sweep, 0, 0); r('canelaE', 0.4 * Math.max(wind, sweep), 0, 0);
      r('quadril', 0, twist * 0.35, 0);
      r('tronco', 0, twist * 0.65, 0); r('cabeca', 0, -twist * 0.85, 0);   // a cabeça fica firme no alvo
      r('bracoD', -1.5 * lift, 0, 0.0); if (P.anteD) r('anteD', -0.15 * lift, 0, 0);
      r('bracoD', 0, 0, -0.9 * wind + 0.6 * sweep);
      r('bracoE', 0.3 * sweep, 0, 0.5 * sweep);
      r('leque', 0, 0, 0); fan(P, r, open * (1 - seq([[2.0, 0], [2.6, 1, 'io']], T)), Math.PI / 2);
      if (T > 1.1 && T < 2.4) {
        const age = (T - 1.1) / 1.0;
        ctx.get('kamaitachi', () => blades(9, 11)).at(new THREE.Vector3(0, 1.0, -1.0), 6.5, age);
        ctx.get('areia-vento', () => sand(70, 12)).at(new THREE.Vector3(0, 0, -1.2), 7, age);
        const dust = ctx.get('poeira-leque', FX.volume);
        dust.set(new THREE.Vector3(0, 0.25, -2.6 - 1.2 * FX.clamp01(age)), 1.3, (T - 1.12) / 1.25, { seed: 7.7, dens: 3.2, rise: 0.25, squash: 0.45, freq: 1.6, smoke: [0.62, 0.52, 0.38] });
        if (T < 1.3) ctx.shake(1 - (T - 1.1) / 0.2);
      }
      if (T > 1.3) {
        // as lâminas pegam: cortes no corpo e o vento leva longe, girando
        const hit = T - 1.3;
        if (hit < 0.5) { ctx.slash(new THREE.Vector3(0.1, 1.2, -4.0), hit / 0.32); ctx.slash(new THREE.Vector3(-0.15, 0.8, -4.05), (hit - 0.12) / 0.32); }
        const k = seq([[1.32, 0], [2.0, 1, 'out']], T);
        R.place(0.6 * k, -4.2 - 5.0 * k, 0.8 * Math.sin(Math.PI * FX.clamp01((T - 1.32) / 0.7)));
        R.lean(-0.9 * Math.sin(Math.PI * FX.clamp01((T - 1.32) / 1.1)));
        R.spin(seq([[1.32, 0], [2.0, -2.6, 'out']], T));
      }
      return;
    }
    if (mode === 'fechado') {
      const T = t % 3.0;
      R.place(0, -2.0, 0);
      N.guard(r, P);
      // abaixa o leque fechado pra trás, rente ao chão, e sobe num arco curto até o queixo do alvo
      const load = seq([[0.1, 0], [0.55, 1, 'out'], [0.7, 1]], T);
      const up = seq([[0.7, 0], [0.8, 1, 'snap'], [1.2, 1], [1.9, 0, 'io']], T);
      P.quadril.obj.position.y += 2.2 * load * (1 - up) + 0.6 * up;
      r('quadril', 0.25 * load * (1 - up) - 0.1 * up, 0.4 * load * (1 - up), 0);
      r('tronco', 0.35 * load * (1 - up) - 0.3 * up, 0.3 * load * (1 - up) - 0.3 * up, 0);
      r('cabeca', -0.5 * load * (1 - up) + 0.25 * up, -0.5 * load * (1 - up) + 0.3 * up, 0);
      r('pernaD', -0.9 * load * (1 - up) - 0.2 * up, 0, 0); r('canelaD', 1.3 * load * (1 - up) + 0.1 * up, 0, 0);
      r('pernaE', 0.5 * load * (1 - up) + 0.3 * up, 0, 0); r('canelaE', 0.9 * load * (1 - up), 0, 0);
      r('bracoD', 0.9 * load * (1 - up) - 2.7 * up, 0, 0.15); if (P.anteD) r('anteD', -0.3 * load * (1 - up) - 0.1 * up, 0, 0);
      r('bracoE', -0.6 * up, 0, -0.2 * up);
      fan(P, r, 0, Math.PI / 2);
      if (T > 0.8) {
        const hit = T - 0.8;
        if (hit < 0.4) ctx.slash(new THREE.Vector3(0, 1.4, -1.9), hit / 0.3);
        if (hit < 0.3) ctx.shake(1 - hit / 0.3);
        const ring = seq([[0.8, 0], [1.6, 1, 'out']], T);
        if (ring < 1) ctx.roarRing(new THREE.Vector3(0, 0.03, -2.0), ring);
        // sobe girando de costas e cai 2 blocos atrás
        const k = FX.clamp01(hit / 1.2);
        R.place(0, -2.0 - 2.0 * k, 3.2 * Math.sin(Math.PI * k));
        R.lean(-2.4 * seq([[0.8, 0], [2.0, 1, 'out']], T));
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(4), variant(17), variant(29)];
  fixed[0].hood = true; fixed[1].hood = false;
  window.MOBS.push({
    id: 'ninja_leque', nome: 'Ninja do Leque', bioma: 'Deserto do Vento', tier: 'Médio · tier 8',
    variants: fixed.map((v, i) => ({ nome: ['Capuz', 'Bandana', 'Sorteado'][i], v, def: defOf(v), low: lowOf(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, def: defOf(v), low: lowOf(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['kamaitachi', 'Kamaitachi'], ['fechado', 'Leque fechado']],
    cam: 6, lookY: 1.2, lookZ: { kamaitachi: -3.4, fechado: -1.6 }, camFar: { kamaitachi: 10, fechado: 7 }, camYaw: { kamaitachi: -1.25, fechado: -1.3 },
    info: {
      'Tamanho': '1,85 bloco; o leque fechado tem 1,4 bloco',
      'Vida': '120 (tier 8)',
      'Jeito': 'Hostil, sozinho ou em dupla. Fica longe e usa o vento; quando você chega perto, troca pra pancada.',
      'Kamaitachi': 'Abre o leque num estalo e recua o tronco (0,6 s de aviso), varre e solta lâminas de vento em leque por 7 blocos: corta e te joga 5 blocos pra trás. Na tempestade de areia o alcance dobra.',
      'Leque fechado': 'Abaixa o leque rente ao chão e sobe num arco curto: te joga 3 blocos pro alto.',
      'Solta': 'Vareta de leque, tecido de Suna; raro: leque de guerra (arma).',
      'Variações': 'Capuz ou bandana, cor da roupa, pele e a cor da barra do leque sorteadas.',
    },
  });
})();
