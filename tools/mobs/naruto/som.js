// Ninja do Som (Floresta da Morte). Médio, tier 8, em trio como na prova chunin.
// Golpes: Onda Sonora (o aparelho do braço solta anéis de som: mira treme e a vista oscila 2 s) e Rajada de Ar
// (tubos nas palmas: uma rajada que joga longe).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const N = window.NINJA;

  function variant(seed) {
    const v = N.variant(seed);
    const R = FX.rnd(seed + 77);
    v.bandaged = R() < 0.5;
    v.camo = [[96, 102, 78], [118, 106, 84], [82, 92, 96]][Math.floor(R() * 3)];
    v.scarf = null;
    return v;
  }
  function painters(v) {
    const P = N.painters(v);
    const S = v.seed;
    // camuflagem de manchas: cada mancha com forma do ruído, três tons
    const camo = q => {
      const a = n3(q.ax * 0.22, q.ay * 0.22, q.az * 0.22, 30 + S), b = n3(q.ax * 0.3 + 9, q.ay * 0.3, q.az * 0.3, 31 + S);
      let c = v.camo;
      if (a > 0.6) c = mul(v.camo, 0.66);
      else if (b > 0.64) c = mix(v.camo, [196, 186, 150], 0.45);
      const f = 0.62 - 0.4 * q.ny + (q.f === 'top' ? 0.2 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(c, f, 0.84, 1.08);
    };
    P.torso = q => {
      let c = camo(q);
      if (q.f === 'north' && q.ny > 0.12 && q.ny < 0.45 && q.nx > 0.3 && q.nx < 0.7) {
        // 死 bordado no peito, em traços (o kanji de "morte" que eles usam)
        const x = (q.nx - 0.3) / 0.4, y = (q.ny - 0.12) / 0.33;
        if (y < 0.14 || (Math.abs(x - 0.3) < 0.08 && y < 0.9) || (Math.abs(y - 0.5) < 0.08 && x < 0.5) || (Math.abs(x - 0.75) < 0.08 && y > 0.2) || (y > 0.86 && x > 0.6)) c = [24, 22, 28];
      }
      return c;
    };
    P.pouchV = q => camo(q);
    P.sleeve = q => camo(q);
    P.upperArm = q => camo(q);
    P.pants = q => cel([62, 58, 54], 0.62 - 0.4 * q.ny + 0.3 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, 32) - 0.5), 0.82, 1.12);
    P.plate = q => {
      const metal = mix([150, 156, 170], [214, 220, 230], 0.4 + 0.6 * n3(q.ax * 0.8, q.ay * 0.8, q.az * 0.8, 46));
      if (q.f !== 'north') return mul(metal, 0.82);
      if (q.nx < 0.07 || q.nx > 0.93 || q.ny < 0.1 || q.ny > 0.9) return mul(metal, 0.72);
      // nota musical gravada
      const x = q.nx, y = q.ny;
      if (Math.abs(x - 0.58) < 0.05 && y > 0.2 && y < 0.72) return mul(metal, 0.55);
      if (Math.hypot((x - 0.48) * 1.3, y - 0.72) < 0.11) return mul(metal, 0.55);
      if (y > 0.2 && y < 0.3 && x > 0.58 && x < 0.76) return mul(metal, 0.55);
      return metal;
    };
    const baseHead = P.head;
    P.head = q => {
      if (!v.bandaged) return baseHead(q);
      // rosto todo enfaixado, só o olho esquerdo dele de fora
      const w = fr(q.ay * 0.6 + q.ax * 0.12 + q.az * 0.12 + n3(q.ax * 0.5, q.ay * 0.3, q.az * 0.5, 33) * 0.6);
      let c = w < 0.2 ? [170, 166, 150] : w < 0.32 ? [206, 200, 184] : [232, 226, 208];
      if (q.f === 'north') {
        const x = Math.floor(q.px), y = Math.floor(q.py);
        if (y === 4 && (x === 4 || x === 5)) c = x === 4 ? [28, 24, 30] : [236, 234, 228];
        if (y === 3 && (x === 4 || x === 5)) c = [52, 46, 44];
      }
      return c;
    };
    const baseHair = P.hairTop;
    P.hairTop = q => (v.bandaged ? (q.ny > 0.6 ? P.head(q) : null) : baseHair(q));
    P.hairBack = q => (v.bandaged ? null : N.painters(v).hairBack(q));
    P.fur = q => {
      // gola de pelo: fios de comprimentos diferentes caindo da borda de baixo
      const k = Math.floor(q.px * 1.2) * 13 + Math.floor(q.pz * 1.2);
      if (q.ny > 0.55 + 0.45 * n3(k * 0.77, S, 0, 34)) return null;
      return cel([206, 198, 178], n3(q.ax * 1.1, q.ay * 0.4, q.az * 1.1, 35 + S), 0.76, 1.1);
    };
    P.device = q => {
      let c = mix([96, 100, 110], [176, 182, 194], n3(q.ax * 0.8, q.ay * 0.8, q.az * 0.8, 36));
      if (q.f === 'north' || q.f === 'west') {                                                   // furos do alto-falante
        const fx = fr(q.ny * 3.2 + 0.25), fz = fr((q.f === 'north' ? q.nx : q.nz) * 3.2 + 0.25);
        if (fx > 0.3 && fx < 0.7 && fz > 0.3 && fz < 0.7) c = [20, 20, 26];
      }
      if (q.ny < 0.12 || q.ny > 0.88) c = mul(c, 0.7);
      return c;
    };
    P.lowTorso = q => (q.ay > 11.2 ? P.pants(q) : q.ay > 10.3 ? P.belt(q) : P.torso({ ...q, ny: (q.ay - 2) / 8.3 }));
    P.lowArm = q => (q.ay < 10 ? camo(q) : P.glove({ ...q, ny: (q.ay - 10) / 2.2 }));
    P.lowLeg = q => (q.ay < 19 ? P.pants(q) : q.ay < 23.2 ? P.shinWrap(q) : P.sandal({ ...q, ny: 0.7 }));
    return P;
  }
  const defOf = v => { const P = painters(v); return N.rigDef(P, v, { noTails: false, torso: [{ x: -5, y: -9.6, z: -3, w: 10, h: 3.2, d: 6, p: P.fur }], armDevice: { x: -2, y: 0.4, z: -2, w: 4, h: 3, d: 4, p: P.device } }); };
  const lowOf = v => N.rigLow(painters(v), v);

  // ---------- anéis de som: borda ondulada pelo ruído, cada anel com sua semente ----------
  function ring(seed) {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, side: THREE.DoubleSide,
      uniforms: { uA: { value: 0 }, uSeed: { value: seed } },
      vertexShader: 'varying vec2 vP; void main(){ vP=position.xy; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: `varying vec2 vP; uniform float uA, uSeed;
        float h(float x){ return fract(sin(x*91.7+uSeed)*43758.5); }
        float n(float x){ float i=floor(x), f=fract(x); f=f*f*(3.0-2.0*f); return mix(h(i),h(i+1.0),f); }
        void main(){
          float r = length(vP), a = atan(vP.y, vP.x);
          float edge = 0.8 + 0.07*(n(a*3.0+uSeed)-0.5) + 0.04*(n(a*9.0-uSeed)-0.5);
          float d = abs(r-edge);
          float k = smoothstep(0.09, 0.0, d) * uA;
          float inner = smoothstep(0.75, 0.0, r) * 0.08 * uA;
          if(k+inner < 0.01) discard;
          vec3 c = mix(vec3(0.75,0.85,1.0), vec3(1.0), smoothstep(0.03,0.0,d));
          gl_FragColor = vec4(c, k*0.85 + inner);
        }`,
    });
    const m = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), mat);
    m.alpha = a => { mat.uniforms.uA.value = a; };
    return m;
  }
  // riscos de vento: cada risco com comprimento, distância do eixo e atraso próprios
  function streaks(n, seed) {
    const R = FX.rnd(seed), g = new THREE.Group(), list = [];
    for (let i = 0; i < n; i++) {
      const m = new THREE.Mesh(new THREE.BoxGeometry(0.025, 0.025, 1), new THREE.MeshBasicMaterial({ color: 0xf0f2f4, transparent: true, depthWrite: false }));
      g.add(m);
      const a = R() * 6.28;
      list.push({ m, a, r: 0.1 + R() * 0.75, len: 0.6 + R() * 1.6, lag: R() * 0.35, sp: 0.8 + R() * 0.5 });
    }
    g.at = (o, dist, age) => {
      g.visible = true;
      for (const s of list) {
        const f = (age - s.lag) * s.sp;
        const vis = f > 0 && f < 1;
        s.m.visible = vis;
        if (!vis) continue;
        const z = o.z - f * dist, spread = 1 + f * 1.4;
        s.m.position.set(o.x + Math.cos(s.a) * s.r * spread, o.y + Math.sin(s.a) * s.r * spread * 0.8, z);
        s.m.scale.set(1, 1, s.len * (1 - f * 0.5));
        s.m.material.opacity = 0.75 * Math.sin(Math.PI * f);
      }
    };
    return g;
  }

  function anim(P, t, mode, a, obj, ent, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const low = !P.tronco;
    if (mode === 'parado') {
      N.guard(r, P);
      r('bracoD', -0.2, 0, 0.1); r('anteD', -0.6, 0, 0);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 0.95;
      for (const [s, off] of [['D', 0], ['E', 0.5]]) {
        r('perna' + s, loop(N.WALK_T, ph + off), 0, 0);
        r('canela' + s, loop(N.WALK_K, ph + off), 0, 0);
      }
      P.quadril.obj.position.y += loop(N.WALK_B, ph);
      r('quadril', 0.12, 0, 0); r('tronco', 0.16, 0, 0); r('cabeca', -0.26, 0, 0);   // anda corcunda
      r('bracoD', loop(N.WALK_A, ph - 0.04) * 0.5, 0, 0.1); r('bracoE', loop(N.WALK_A, ph + 0.46), 0, -0.08);
      r('anteD', -0.5, 0, 0);
      return;
    }
    const R = ctx.ref;
    const dev = new THREE.Vector3(-0.3, 1.35, -0.7);
    if (mode === 'onda') {
      const T = t % 3.0;
      R.place(0, -4.6, 0);
      N.guard(r, P);
      const aim = seq([[0.15, 0], [0.55, 1, 'out'], [1.9, 1], [2.4, 0]], T);
      const kick = seq([[0.62, 0], [0.66, 1, 'snap'], [0.8, 0.3], [0.9, 1, 'snap'], [1.04, 0.3], [1.14, 1, 'snap'], [1.4, 0]], T);
      r('bracoD', -1.55 * aim + 0.12 * kick, 0.15 * aim, 0);
      r('anteD', 0.5 * aim, 0, 0);
      r('bracoE', 0.4 * aim, 0, 0.35 * aim);
      r('tronco', 0, -0.35 * aim, 0); r('cabeca', 0, 0.3 * aim, 0);
      for (let i = 0; i < 3; i++) {
        const m = ctx.get('anel' + i, () => ring(3 + i * 7));
        const t0 = 0.64 + i * 0.24, age = (T - t0) / 0.55;
        if (age > 0 && age < 1) {
          m.visible = true;
          m.position.set(dev.x, dev.y, dev.z - age * 4.0);
          m.scale.setScalar(0.25 + age * 1.3);
          m.alpha(1 - age);
        }
      }
      if (T > 0.9) {
        const k = seq([[0.9, 0], [1.1, 1], [2.6, 1], [2.9, 0]], T);
        R.lean(0.12 * Math.sin(T * 23) * k);
        R.spin(0.15 * Math.sin(T * 17) * k);
        ctx.dizzy(k);
      }
      return;
    }
    if (mode === 'rajada') {
      const T = t % 3.2;
      R.place(0, -3.8, 0);
      N.guard(r, P);
      const set = seq([[0.15, 0], [0.7, 1, 'out']], T);
      const blast = seq([[0.72, 0], [0.76, 1, 'snap'], [1.5, 1], [2.1, 0]], T);
      P.quadril.obj.position.y += 1.6 * set;
      r('pernaD', -0.6 * set, 0, 0); r('canelaD', 0.9 * set, 0, 0); r('pernaE', 0.3 * set, 0, 0); r('canelaE', 0.5 * set, 0, 0);
      r('bracoD', -1.45 * set - 0.15 * blast, 0.25 * set, 0); r('bracoE', -1.45 * set - 0.15 * blast, -0.25 * set, 0);
      r('anteD', 0.3 * set, 0, 0); r('anteE', 0.3 * set, 0, 0);
      r('maoD', -1.3 * set, 0, 0); r('maoE', -1.3 * set, 0, 0);                          // palmas viradas pra frente
      r('tronco', -0.1 * blast, 0, 0); r('cabeca', 0.1 * blast, 0, 0);
      obj.position.z += 0.35 * blast;                                                       // o coice da rajada
      if (T > 0.72 && T < 1.9) {
        ctx.get('vento-som', () => streaks(46, 4)).at(new THREE.Vector3(0, 1.2, -0.8), 6, (T - 0.72) / 0.9);
        const dust = ctx.get('rajada-poeira', FX.volume);
        dust.set(new THREE.Vector3(0, 0.2, -3.2), 1.6, (T - 0.74) / 1.2, { seed: 5.1, dens: 3.5, rise: 0.2, squash: 0.4, freq: 1.7, smoke: [0.5, 0.46, 0.38] });
        const k = seq([[0.78, 0], [1.4, 1, 'out']], T);
        R.place(0, -3.8 - 4.2 * k, 1.1 * Math.sin(Math.PI * FX.clamp01((T - 0.78) / 0.62)));
        R.lean(-1.0 * Math.sin(Math.PI * FX.clamp01((T - 0.78) / 1.0)));
        R.spin(seq([[0.78, 0], [1.4, 3.6, 'out']], T));
        if (T < 0.95) ctx.shake(1 - (T - 0.72) / 0.23);
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(5), variant(14), variant(30)];
  fixed[0].bandaged = true; fixed[1].bandaged = false;
  window.MOBS.push({
    id: 'ninja_som', nome: 'Ninja do Som', bioma: 'Floresta da Morte', tier: 'Médio · tier 8',
    variants: fixed.map((v, i) => ({ nome: ['Enfaixado', 'Camuflado', 'Sorteado'][i], v, def: defOf(v), low: lowOf(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, def: defOf(v), low: lowOf(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['onda', 'Onda sonora'], ['rajada', 'Rajada de ar']],
    cam: 6, lookY: 1.1, lookZ: { onda: -2.2, rajada: -3 }, camFar: { onda: 7, rajada: 9 }, camYaw: { onda: -1.2, rajada: -1.25 },
    info: {
      'Tamanho': '1,85 bloco, anda curvado',
      'Vida': '120 (tier 8)',
      'Jeito': 'Hostil, em trio. Um prende sua atenção de perto enquanto os outros atacam de longe.',
      'Onda sonora': 'Aponta o aparelho do braço (0,5 s de aviso) e solta três anéis de som: por 2 s sua mira treme e a vista oscila.',
      'Rajada de ar': 'Agacha com as palmas pra frente e solta uma rajada em linha que te joga 5 blocos pra trás, girando.',
      'Solta': 'Placa de braço metálica; raro: pergaminho do Céu ou da Terra.',
    },
  });
})();
