// Androide do Dr. Gero (laboratório escondido e Terras do Dragão). Os modelos de série que o Gero ia montando:
// pele branca de boneco, roupa da Red Ribbon, emendas e painel no corpo, olho de LED. Cada um com número e
// peças diferentes (chapéu, cabelo, viseira, braço de canhão).
// Golpes: Laser dos Olhos (dois raios finos que riscam o chão até o alvo) e Canhão do Braço (a mão desencaixa,
// o canhão aparece e solta um raio grosso).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const V3 = (x, y, z) => new THREE.Vector3(x, y, z);

  const SUITS = [[44, 60, 96], [70, 70, 74], [96, 40, 44], [40, 76, 60]];
  const HAIRS = [[30, 30, 34], [190, 180, 160], [150, 40, 40], [230, 230, 236]];
  function variant(seed) {
    const R = FX.rnd(seed);
    const pick = a => a[Math.floor(R() * a.length)];
    return { seed, num: 11 + Math.floor(R() * 12), suit: pick(SUITS), hair: pick(HAIRS), top: pick(['careca', 'cabelo', 'chapeu', 'viseira']), eye: R() < 0.5 ? [255, 60, 50] : [80, 220, 255], big: R() < 0.3 };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.84, hi = 1.12) => cel(base, 0.62 - 0.38 * q.ny + 0.1 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0), lo, hi);
    const SK = [236, 232, 230];
    const P = {};
    // pele de boneco com emendas finas nas juntas (linha cinza onde as peças se encaixam)
    P.skin = q => { let c = shade(SK, q, 1, 0.86, 1.06); if (q.f !== 'top' && q.f !== 'bottom' && fr(q.py / q.b.h * 2 + 0.02) < 0.05) c = mul(c, 0.8); return c; };
    P.suit = q => { let c = shade(v.suit, q, 2); if (q.f !== 'top' && q.f !== 'bottom' && Math.abs(q.px - q.b.w / 2) < 0.3 && q.f === 'north') c = mul(c, 0.8); return c; };
    // peito: colete com a marca da Red Ribbon (fita vermelha) e o número do modelo
    P.vest = q => {
      let c = shade(mix(v.suit, [240, 230, 210], 0.65), q, 3);
      if (q.f === 'north') {
        const u = q.px / q.b.w, w = q.py / q.b.h;
        if (Math.hypot(u - 0.3, w - 0.3) < 0.12) c = [200, 30, 40];
        if (Math.hypot(u - 0.3, w - 0.3) < 0.06) c = [240, 230, 210];
        if (u > 0.55 && u < 0.85 && w > 0.2 && w < 0.42) c = (fr(u * 12) < 0.45 ? [40, 40, 44] : c);
      }
      if (q.f === 'south') { const u = q.px / q.b.w, w = q.py / q.b.h; if (u > 0.3 && u < 0.7 && w > 0.2 && w < 0.6) c = fr(w * 8) < 0.2 ? [90, 96, 100] : [150, 156, 160]; }   // painel de manutenção
      return c;
    };
    P.boot = q => shade([60, 50, 44], q, 4);
    P.head = q => {
      let c = shade(SK, q, 5, 0.88, 1.06);
      if (q.f === 'south' && q.py > 2 && q.py < 5 && Math.abs(q.px - 3.5) < 1.8) c = fr(q.py) < 0.3 ? [100, 104, 110] : [170, 174, 180];   // tampa na nuca
      if (q.f === 'west' || q.f === 'east') { if (Math.abs(q.pz - 3.5) < 0.6 && q.py > 2.5 && q.py < 4.5) c = [140, 144, 150]; }               // orelha de metal
      if (q.f !== 'north') return c;
      const x = Math.floor(q.px), y = Math.floor(q.py), m = Math.min(x, 6 - x);
      if (y === 3 && (m === 1 || m === 2)) c = m === 2 ? v.eye : [250, 250, 250];
      if (y === 2 && (m === 1 || m === 2)) c = mul(SK, 0.75);
      if (y === 5 && m >= 2) c = [150, 120, 120];
      if (v.top === 'viseira' && y === 3) c = mix(v.eye, [40, 30, 30], 0.35);
      return c;
    };
    P.hair = q => shade(v.hair, q, 6, 0.8, 1.2);
    P.hat = q => { let c = shade([60, 60, 66], q, 7); if (q.f !== 'top' && q.py > q.b.h - 0.8) c = [200, 30, 40]; return c; };
    P.metal = q => { let c = shade([150, 156, 164], q, 8, 0.8, 1.25); if (fr(q.ay * 0.8) < 0.15) c = mul(c, 0.75); return c; };
    P.cannon = q => q.f === 'north' || q.f === 'bottom' ? (Math.hypot(q.px - q.b.w / 2, q.pz - q.b.d / 2) < 0.9 || Math.hypot(q.px - q.b.w / 2, q.py - q.b.h / 2) < 0.9 ? [30, 30, 34] : shade([130, 134, 140], q, 9)) : shade([110, 114, 120], q, 9);
    return P;
  }

  function rigDef(P, v, low) {
    const w = v.big ? 9.6 : 8;
    const arm = s => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * (w / 2 + 1.5), -9, 0], boxes: [{ x: -1.5, y: -1, z: -1.5, w: 3, h: 5, d: 3, p: P.suit }],
      kids: [{
        name: s < 0 ? 'anteD' : 'anteE', pivot: [0, 4, 0], boxes: [{ x: -1.4, y: 0, z: -1.4, w: 2.8, h: 4, d: 2.8, p: P.skin }],
        kids: [
          { name: s < 0 ? 'maoD' : 'maoE', pivot: [0, 4, 0], boxes: [{ x: -1.5, y: -0.6, z: -1.5, w: 3, h: 3, d: 3, p: P.skin }] },
          ...(s < 0 && !low ? [{ name: 'canhao', pivot: [0, 3.6, 0], boxes: [{ x: -1.1, y: -0.4, z: -1.1, w: 2.2, h: 2.4, d: 2.2, p: P.cannon }] }] : []),
        ],
      }],
    });
    const leg = s => ({
      name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * 2, 12, 0], boxes: [{ x: -2, y: 0, z: -2, w: 4, h: 6, d: 4, p: P.suit }],
      kids: [{ name: s < 0 ? 'canelaD' : 'canelaE', pivot: [0, 6, 0], boxes: [{ x: -2.1, y: 0, z: -2.1, w: 4.2, h: 6, d: 4.2, p: P.boot }] }],
    });
    const top = [];
    if (v.top === 'cabelo') top.push({ x: -3.8, y: -7.6, z: -3.8, w: 7.6, h: 2.4, d: 7.6, p: P.hair }, { x: -3.8, y: -5.4, z: 1.5, w: 7.6, h: 4.2, d: 2.3, p: P.hair });
    if (v.top === 'chapeu') top.push({ x: -4.4, y: -7.4, z: -4.4, w: 8.8, h: 2.4, d: 8.8, p: P.hat }, ...(low ? [] : [{ x: -3.4, y: -8.6, z: -3.4, w: 6.8, h: 1.4, d: 6.8, p: P.hat }]));
    if (v.top === 'viseira' && !low) top.push({ x: -3.8, y: -4.6, z: -3.9, w: 7.6, h: 1.4, d: 0.4, p: P.metal });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'quadril', pivot: [0, 12, 0], boxes: [{ x: -w / 2, y: -2.4, z: -2.1, w, h: 2.6, d: 4.2, p: P.suit }], kids: [{
          name: 'tronco', pivot: [0, -2.2, 0], boxes: [{ x: -w / 2, y: -10, z: -2, w, h: 10, d: 4, p: P.suit }, { x: -w / 2 - 0.3, y: -10.1, z: -2.3, w: w + 0.6, h: 7, d: 4.6, p: P.vest }], kids: [
            { name: 'cabeca', pivot: [0, -10, 0], boxes: [{ x: -3.5, y: -7, z: -3.5, w: 7, h: 7, d: 7, p: P.head }, ...top] },
            arm(-1), arm(1),
          ],
        }],
      }, leg(-1), leg(1)],
    };
  }

  function stance(r) {
    r('pernaD', -0.05, 0.1, 0.03); r('pernaE', 0.05, -0.1, -0.03);
    r('bracoD', 0, 0, 0.06); r('bracoE', 0, 0, -0.06);
  }

  // raio: tubo com miolo claro e borda colorida que corre (normal blending pra não estourar em branco)
  function beam(c1) {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, side: THREE.DoubleSide,
      uniforms: { uK: { value: 1 }, uT: { value: 0 }, uL: { value: 1 }, uC: { value: new THREE.Color(...c1) } },
      vertexShader: 'varying vec3 vN; varying vec3 vV; varying vec2 vU; void main(){ vN=normalize(normalMatrix*normal); vec4 mv=modelViewMatrix*vec4(position,1.0); vV=normalize(-mv.xyz); vU=uv; gl_Position=projectionMatrix*mv; }',
      fragmentShader: `varying vec3 vN; varying vec3 vV; varying vec2 vU; uniform float uK, uT, uL; uniform vec3 uC;
        float h(vec2 p){ return fract(sin(dot(p, vec2(41.3, 289.1)))*43758.5); }
        float n(vec2 x){ vec2 i=floor(x), f=fract(x); f=f*f*(3.0-2.0*f); return mix(mix(h(i),h(i+vec2(1,0)),f.x),mix(h(i+vec2(0,1)),h(i+vec2(1,1)),f.x),f.y); }
        void main(){ float face = abs(dot(vN, vV));
          float w = n(vec2(vU.x * 8.0, vU.y * uL * 3.0 - uT * 16.0));
          vec3 c = mix(uC * 0.8, uC, smoothstep(0.1, 0.5, face));
          c = mix(c, vec3(1.0, 0.98, 0.94), smoothstep(0.5, 0.95, face + 0.2 * (w - 0.5)));
          gl_FragColor = vec4(c, smoothstep(0.0, 0.3, face) * (0.75 + 0.25 * w) * uK); }`,
    });
    const g = new THREE.CylinderGeometry(1, 1, 1, 20, 1, true); g.rotateX(Math.PI / 2);
    const m = new THREE.Mesh(g, mat); m.frustumCulled = false;
    m.at = (a, b, r, k, t) => { m.visible = k > 0.01; const L = b.distanceTo(a); m.position.copy(a).add(b).multiplyScalar(0.5); m.lookAt(b); m.scale.set(r, r, L); mat.uniforms.uK.value = k; mat.uniforms.uT.value = t; mat.uniforms.uL.value = L; };
    return m;
  }
  // risco no chão: faixa queimada que vai ficando pra trás do ponto do laser
  function scorch() {
    const N = 40, pos = new Float32Array(N * 2 * 3), al = new Float32Array(N * 2), idx = [];
    for (let i = 0; i < N - 1; i++) { const a = i * 2; idx.push(a, a + 1, a + 2, a + 1, a + 3, a + 2); }
    const geo = new THREE.BufferGeometry(); geo.setIndex(idx);
    geo.setAttribute('position', new THREE.BufferAttribute(pos, 3)); geo.setAttribute('al', new THREE.BufferAttribute(al, 1));
    const m = new THREE.Mesh(geo, new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, side: THREE.DoubleSide,
      vertexShader: 'attribute float al; varying float vA; void main(){ vA=al; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: 'varying float vA; void main(){ vec3 c = mix(vec3(0.12,0.08,0.06), vec3(1.0,0.55,0.2), smoothstep(0.75,1.0,vA)); gl_FragColor = vec4(c, min(1.0, vA*1.3)*0.85); }',
    }));
    m.frustumCulled = false;
    m.at = (path, k) => {
      m.visible = true;
      for (let i = 0; i < N; i++) {
        const f = i / (N - 1), p = path(f * k), q = path(Math.min(1, f * k + 0.01));
        const dx = q.z - p.z, dz = -(q.x - p.x), l = Math.hypot(dx, dz) || 1, wdt = 0.07;
        pos.set([p.x + dx / l * wdt, 0.02, p.z + dz / l * wdt, p.x - dx / l * wdt, 0.02, p.z - dz / l * wdt], i * 6);
        al[i * 2] = al[i * 2 + 1] = 0.35 + 0.65 * f;
      }
      geo.attributes.position.needsUpdate = true; geo.attributes.al.needsUpdate = true;
    };
    return m;
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const at = (n, off) => { obj.updateMatrixWorld(true); return P[n].obj.localToWorld(off.clone()); };
    const eyeCol = e.v.eye.map(c => c / 255);
    // canhão só aparece quando a mão desencaixa
    if (P.canhao) P.canhao.obj.scale.set(0.001, 0.001, 0.001);

    if (mode === 'parado') {
      stance(r);
      // parado demais, como máquina: a cabeça gira em passos secos escaneando, o olho pisca o LED
      const st = Math.floor(t / 1.2) % 4, ang = [0, 0.5, 0, -0.5][st];
      const k = Math.min(1, fr(t / 1.2) * 8);
      const prev = [0, 0.5, 0, -0.5][(st + 3) % 4];
      r('cabeca', 0, prev + (ang - prev) * k, 0);
      if (fr(t / 1.2) < 0.08) ctx.get('led', () => DBFX.glow(eyeCol)).at(at('cabeca', V3(1.5, -3.5, -3.7)), 0.07, 1, t);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 1.0;
      for (const [s, off] of [['D', 0], ['E', 0.5]]) {
        r('perna' + s, 0.45 * Math.sin((ph + off) * Math.PI * 2), 0, 0); r('canela' + s, 0.3 * Math.max(0, Math.sin((ph + off) * Math.PI * 2 + 1.4)), 0, 0);
        r('braco' + s, -0.12 * Math.sin((ph + off) * Math.PI * 2), 0, 0);     // braço quase parado: andar de máquina
      }
      P.quadril.obj.position.y += 0.3 * Math.abs(Math.sin(ph * Math.PI * 2));
      return;
    }

    if (mode === 'laser') {
      const T = t % 3.2;
      const R = ctx.ref; const RZ = -6.5; R.place(0, RZ, 0);
      stance(r);
      // trava, inclina a cabeça pra baixo, os olhos acendem; dois raios saem e riscam o chão de perto até o alvo
      const lock = seq([[0, 0], [0.3, 1, 'snap'], [2.2, 1], [2.5, 0]], T);
      const charge = seq([[0.3, 0], [0.8, 1, 'in']], T);
      const sweep = seq([[0.85, 0], [1.6, 1, 'io']], T);
      r('cabeca', 0.25 * lock - 0.25 * sweep, 0, 0);
      const eyes = [V3(-1.5, -3.5, -3.7), V3(1.5, -3.5, -3.7)].map(o => at('cabeca', o));
      eyes.forEach((p, i) => ctx.get('olho' + i, () => DBFX.glow(eyeCol)).at(p, 0.05 + 0.08 * charge, charge * lock, T + i));
      const path = f => V3(0.15 * Math.sin(f * 9), 0, -1.2 + (RZ + 0.1 + 1.2) * f);
      if (T > 0.85 && T < 2.2) {
        const k = Math.min(1, sweep), fade = seq([[1.9, 1], [2.2, 0]], T);
        const hit = path(k); hit.y = k >= 1 ? 1.2 : 0.03;
        eyes.forEach((p, i) => { const h2 = hit.clone(); h2.x += (i - 0.5) * 0.12; ctx.get('raio' + i, () => beam(eyeCol)).at(p, h2, 0.025, fade, T); });
        ctx.get('risco', scorch).at(path, k);
        ctx.get('faiscas', () => DBFX.grains({ n: 40, seed: 121, palette: [[255, 220, 120], [255, 160, 60], [255, 255, 220]], size: [0.012, 0.03], drag: 2, grav: 6, life: 0.4, spread: 1.2, add: true, cone: Rr => { const a = Rr() * 6.28; return [Math.cos(a) * 1.5, 1 + Rr() * 1.5, Math.sin(a) * 1.5]; } })).at(hit, T - 0.85);
        ctx.get('luzLaser', () => DBFX.light(0xff5040)).at(hit, 2 * fade, 3);
        if (k >= 1) { ctx.shake(0.25 * fade); R.lean(-0.4 * fade); }
      }
      if (T > 1.6 && T < 2.6) ctx.get('fumaca', FX.volume).set(V3(0, 1.0, RZ), 0.5, (T - 1.6) / 1.0, { fire: 0.3, seed: 2.2, dens: 7, rise: 0.8, smoke: [0.3, 0.28, 0.27] });
      return;
    }

    if (mode === 'canhao') {
      const T = t % 3.8;
      const R = ctx.ref; const RZ = -7; R.place(0, RZ, 0);
      stance(r);
      // ergue o braço direito, a mão abre pra trás (dobradiça) e o canhão sai; carrega e solta o raio grosso
      const aim = seq([[0, 0], [0.4, 1, 'out'], [2.6, 1], [3.0, 0, 'io']], T);
      const open = seq([[0.45, 0], [0.75, 1, 'out'], [2.5, 1], [2.8, 0]], T);
      r('bracoD', -1.55 * aim, 0, 0); r('bracoE', -0.6 * aim, 0, -0.3 * aim); r('anteE', -1.4 * aim, 0, 0);   // a outra mão segura o braço
      r('tronco', 0, 0.12 * aim, 0);
      P.maoD.obj.rotation.x += 2.2 * open; P.maoD.obj.position.z += 1.6 * open;
      if (P.canhao) { const s = Math.max(0.001, open); P.canhao.obj.scale.set(s, s, s); }
      const muzzle = at('anteD', V3(0, 6.2, 0));
      const charge = seq([[0.8, 0], [1.4, 1, 'in']], T);
      if (T > 0.8 && T < 1.45) {
        ctx.get('carga', () => DBFX.glow([1.0, 0.85, 0.3])).at(muzzle, 0.05 + 0.18 * charge, 0.5, T);
        ctx.get('faisca', () => DBFX.grains({ n: 30, seed: 127, palette: [[255, 230, 140], [255, 255, 220]], size: [0.012, 0.025], drag: 4, grav: 0, life: 0.25, spread: 0.6, add: true, cone: Rr => { const a = Rr() * 6.28, b = Rr() * 3.14; return [-Math.sin(b) * Math.cos(a) * 1.4, -Math.cos(b) * 1.4, -Math.sin(b) * Math.sin(a) * 1.4]; } })).at(muzzle, T - 0.8);
      }
      if (T >= 1.45 && T < 2.5) {
        const tgt = V3(0, 1.1, RZ + 0.2);
        const out = seq([[1.45, 0], [1.6, 1, 'out']], T), fade = seq([[2.1, 1], [2.5, 0]], T);
        const tip = muzzle.clone().lerp(tgt, out);
        const rr = 0.22 * fade + 0.03 * Math.sin(T * 60);
        ctx.get('raioC', () => beam([1.0, 0.75, 0.2])).at(muzzle, tip, rr, fade, T);
        ctx.get('pontaC', () => DBFX.glow([1.0, 0.8, 0.3])).at(tip, rr * 2, 0.5 * fade, T);
        ctx.get('luzC', () => DBFX.light(0xffc050)).at(tip, 3 * fade, 6);
        r('bracoD', 0.15 * fade * Math.sin(T * 40), 0, 0); r('tronco', 0.05 * fade, 0, 0);
        if (out >= 1) {
          const k = (T - 1.6) / 1.1;
          ctx.get('boom', FX.volume).set(tgt, 1.1, Math.min(1, k), { fire: 1, seed: 6.6, dens: 11, rise: 0.6, smoke: [0.35, 0.3, 0.28] });
          ctx.flash(tgt, Math.max(0, 1 - k * 3)); ctx.shake(0.7 * fade);
          ctx.get('terra', () => FX.debris(20, 131, [[120, 100, 80], [90, 74, 60], [150, 130, 100]])).at(tgt.clone(), T - 1.6);
          const fly = seq([[1.6, 0], [2.4, 1, 'out']], T);
          R.place(0, RZ - 2.6 * fly, 0.8 * Math.sin(Math.PI * fly)); R.lean(-1.2 * fly * (1 - seq([[3.2, 0], [3.8, 1]], T)));
        }
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(6), variant(19), variant(33)];
  fixed[0].top = 'chapeu'; fixed[0].eye = [255, 60, 50]; fixed[0].num = 13;
  fixed[1].top = 'cabelo'; fixed[1].hair = HAIRS[1]; fixed[1].eye = [80, 220, 255]; fixed[1].num = 14; fixed[1].big = true;
  fixed[2].top = 'viseira'; fixed[2].eye = [255, 60, 50]; fixed[2].num = 15; fixed[2].suit = SUITS[2];
  const def = v => ({ def: rigDef(painters(v), v, false), low: rigDef(painters(v), v, true) });
  window.MOBS.push({
    id: 'androide_gero', nome: 'Androide do Gero', bioma: 'Vilões do mundo', tier: 'Médio · tier 6',
    variants: fixed.map((v, i) => ({ nome: 'Modelo ' + v.num + ' (' + ['chapéu', 'cabelo, grandão', 'viseira'][i] + ')', v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Modelo ' + v.num, v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['laser', 'Laser dos Olhos'], ['canhao', 'Canhão do Braço']],
    cam: 6, lookY: 1.1, lookZ: { laser: -3.2, canhao: -3.4 }, camFar: { laser: 9, canhao: 10 }, camYaw: { laser: -1.2, canhao: -1.25 },
    info: {
      'Tamanho': '1,9 bloco',
      'Vida': '80 (tier 6). Não sente dor: não toma stun leve, só stun forte.',
      'Jeito': 'Duplas e trios perto do laboratório escondido do Gero (estrutura nova nas Terras do Dragão) e às vezes sozinho vagando. Não cansa: estamina infinita, mas não regenera vida.',
      'Laser dos Olhos': 'Trava e os olhos acendem (aviso de meio segundo), dois raios riscam o chão até você: pular de lado escapa. Deixa o chão queimado.',
      'Canhão do Braço': 'A mão abre pra trás e sai o canhão; carrega 0,6 s e solta um raio grosso que explode. No jogo o dano do raio vem em pacotes (Escudo de tier).',
      'Pouca vida': 'Pisca vermelho e se autodestrói em 3 s se você ficar perto (explosão grande). Dá pra correr ou nocautear antes.',
      'Solta': 'Peças de androide, chip do Gero; raro: Núcleo de energia infinita (bateria pra itens).',
      'Variações': 'Número do modelo, chapéu, cabelo ou viseira, cor da roupa, cor do LED do olho e corpo grandão sorteados.',
    },
  });
})();
