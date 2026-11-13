// Tiranossauro, o dino grandão (Paozu, Terras do Dragão e Deserto). Como o do desenho: cabeçorra quadrada, olho
// pequeno e bravo, dentes grandes e poucos, calombos em fileira nas costas, barriga creme em gomos, bracinhos.
// Golpes: Investida (ruge, abaixa a cabeça e atropela tudo em linha reta) e Pisão Sísmico (levanta a pata e pisa:
// o chão racha e uma onda de terra corre pra fora jogando quem estiver em cima).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const V3 = (x, y, z) => new THREE.Vector3(x, y, z);

  const SKINS = [[150, 104, 66], [100, 130, 70], [160, 80, 60], [110, 100, 130], [140, 130, 80]];
  function variant(seed) {
    const R = FX.rnd(seed);
    const pick = a => a[Math.floor(R() * a.length)];
    const skin = pick(SKINS).map(c => c * (0.92 + R() * 0.12));
    return { seed, skin, belly: mix(skin, [244, 226, 176], 0.72), spots: R() < 0.6, bumps: 5 + Math.floor(R() * 3), scar: R() < 0.4, chipped: R() < 0.35 };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.84, hi = 1.1) => {
      const f = 0.62 - 0.38 * q.ny + 0.12 * (n3(q.ax * 0.15, q.ay * 0.15, q.az * 0.15, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const hide = (q, s = 1) => {
      const under = q.f === 'bottom' || (q.f !== 'top' && q.ny > 0.64);
      let c = shade(under ? v.belly : v.skin, q, s);
      if (under && fr(q.az * 0.26) < 0.12) c = mul(c, 0.84);
      if (!under && v.spots && n3(q.ax * 0.11, q.ay * 0.11, q.az * 0.11, 70 + S) > 0.68) c = mul(c, 0.8);
      return c;
    };
    const limb = (q, s = 6) => { let c = shade(v.skin, q, s); if (q.ny > 0.8 && q.f !== 'top') c = mix(c, v.belly, 0.25); if (q.f === 'north' && fr(q.ay * 0.3) < 0.08) c = mul(c, 0.88); return c; };
    const P = { hide, limb };
    P.head = q => {
      let c = hide(q, 2);
      if (q.f === 'west' || q.f === 'east') {
        const x = q.pz, y = q.py, ex = 8.2, ey = 3.0;
        // olho pequeno, bravo, embaixo de uma sobrancelha grossa
        if (Math.hypot((x - ex) / 1.2, y - ey) < 1) { c = [246, 238, 200]; if (Math.hypot(x - (ex - 0.4), y - ey) < 0.6) c = [18, 14, 12]; }
        if (y > ey - 2.1 && y < ey - 0.9 && Math.abs(x - ex) < 2.2 && (y - (ey - 2.1)) < 1.2 - (ex - x) * 0.25) c = mul(v.skin, 0.55);
        if (v.scar && q.f === 'east' && Math.abs(y - (1.5 + (x - 12) * 0.5)) < 0.4 && x > 11 && x < 16) c = mul(v.skin, 0.6);
        // dentes de cima: poucos, grandes, triangulares
        if (y > 7.2) { const k = fr((x - 2) / 2.6); if (x > 2 && x < 21 && k < 0.6 && (y - 7.2) < 1.8 * (1 - Math.abs(k - 0.3) / 0.3) && !(v.chipped && x > 9 && x < 11.6)) c = [246, 240, 222]; }
      }
      if (q.f === 'north' && q.py < 2 && (Math.floor(q.px) === 4 || Math.floor(q.px) === 9)) c = [30, 22, 18];        // narinas
      return c;
    };
    P.jaw = q => {
      let c = hide(q, 3);
      if (q.f === 'top') return [150, 64, 70];
      if ((q.f === 'west' || q.f === 'east') && q.py < 1.6) { const k = fr((q.pz - 1) / 2.4 + 0.4); if (q.pz > 1 && q.pz < 17 && k < 0.55 && q.py < 1.5 * (1 - Math.abs(k - 0.27) / 0.27)) c = [246, 240, 222]; }
      return c;
    };
    P.mouth = () => [120, 36, 46];
    P.claw = q => cel([60, 50, 44], 0.45 + 0.5 * (1 - q.ny), 0.8, 1.4);
    P.bump = q => shade(mul(v.skin, 0.82), q, 9);
    return P;
  }

  function rigDef(P, v) {
    const leg = s => {
      const D = s < 0;
      return {
        name: D ? 'pernaD' : 'pernaE', pivot: [s * 7.5, -9, 3], rot: [-0.3, 0, s * 0.04], boxes: [{ x: -4.6, y: -4, z: -5.6, w: 9.2, h: 17, d: 11.2, p: P.limb }],
        kids: [{
          name: D ? 'canelaD' : 'canelaE', pivot: [0, 12.5, 0], rot: [0.62, 0, 0], boxes: [{ x: -3.2, y: 0, z: -3.4, w: 6.4, h: 12, d: 6.8, p: P.limb }],
          kids: [{
            name: D ? 'metaD' : 'metaE', pivot: [0, 11.4, 0], rot: [-0.55, 0, 0], boxes: [{ x: -2.6, y: 0, z: -2.6, w: 5.2, h: 8, d: 5.2, p: P.limb }],
            kids: [{
              name: D ? 'peD' : 'peE', pivot: [0, 7.6, 0], rot: [0.23, 0, 0], boxes: [
                { x: -4, y: -0.5, z: -9, w: 8, h: 3.2, d: 12, p: P.limb },
                { x: -4.2, y: 0.8, z: -11, w: 2, h: 1.8, d: 2.2, p: P.claw }, { x: -1, y: 0.8, z: -11.4, w: 2, h: 1.8, d: 2.6, p: P.claw }, { x: 2.2, y: 0.8, z: -11, w: 2, h: 1.8, d: 2.2, p: P.claw },
              ],
            }],
          }],
        }],
      };
    };
    const arm = s => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * 7.6, 2.5, -11], rot: [0.5, 0, s * -0.15], boxes: [{ x: -1.4, y: 0, z: -1.5, w: 2.8, h: 6.4, d: 3, p: P.limb }],
      kids: [{ name: s < 0 ? 'anteD' : 'anteE', pivot: [0, 6, 0], rot: [-1.2, 0, 0], boxes: [{ x: -1.1, y: 0, z: -1.2, w: 2.2, h: 4.4, d: 2.4, p: P.limb }, { x: -1.1, y: 4.2, z: -1.2, w: 0.9, h: 1.8, d: 0.9, p: P.claw }, { x: 0.3, y: 4.2, z: -1.2, w: 0.9, h: 2, d: 0.9, p: P.claw }] }],
    });
    // calombos: fileira nas costas e na cauda, cada um de um tamanho
    const R = FX.rnd(v.seed + 3);
    const bumps = []; for (let i = 0; i < v.bumps; i++) { const h = 1.6 + R() * 1.8, z = -11 + i * (22 / v.bumps); bumps.push({ x: -1.2, y: -9 - h, z, w: 2.4, h: h + 0.2, d: 3 + R(), p: P.bump }); }
    const tb = (n, len) => { const out = []; for (let i = 0; i < n; i++) { const h = 1 + R() * 1.2; out.push({ x: -0.9, y: -len - h, z: 2 + i * 5, w: 1.8, h: h + 0.2, d: 2.4, p: P.bump }); } return out; };
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'quadril', pivot: [0, -9, 0], rot: [-0.22, 0, 0], boxes: [
            { x: -8, y: -9, z: -14, w: 16, h: 18, d: 26, p: P.hide },
            { x: -7, y: -7.6, z: -16, w: 14, h: 15, d: 3, p: P.hide },
            ...bumps,
          ], kids: [
            {
              name: 'pescoco', pivot: [0, -3, -14], rot: [0.55, 0, 0], boxes: [{ x: -5.6, y: -11, z: -6.4, w: 11.2, h: 14, d: 11.6, p: P.hide }],
              kids: [{
                name: 'cabeca', pivot: [0, -9.5, -1], rot: [-0.35, 0, 0], boxes: [
                  { x: -7, y: -9.4, z: -18, w: 14, h: 9.8, d: 22.4, p: P.head },
                  { x: -7.4, y: -10.6, z: -11, w: 3.4, h: 2.4, d: 5, p: P.bump }, { x: 4, y: -10.6, z: -11, w: 3.4, h: 2.4, d: 5, p: P.bump },     // sobrancelhas de calombo
                  { x: -1.6, y: -11.4, z: -4, w: 3.2, h: 2.4, d: 6, p: P.bump },
                ],
                kids: [{ name: 'mandibula', pivot: [0, 0.4, 2.6], boxes: [{ x: -6.6, y: 0, z: -19.6, w: 13.2, h: 5.4, d: 21, p: P.jaw }, { x: -5.6, y: -0.3, z: -18.6, w: 11.2, h: 0.4, d: 19, p: P.mouth }] }],
              }],
            },
            arm(-1), arm(1),
            {
              name: 'cauda1', pivot: [0, -3, 11.5], rot: [-0.05, 0, 0], boxes: [{ x: -6, y: -6, z: 0, w: 12, h: 12, d: 14, p: P.hide }, ...tb(2, 6)],
              kids: [{
                name: 'cauda2', pivot: [0, 0, 13.5], rot: [0.05, 0, 0], boxes: [{ x: -4.2, y: -4.2, z: 0, w: 8.4, h: 8.4, d: 14, p: P.hide }, ...tb(2, 4.2)],
                kids: [{
                  name: 'cauda3', pivot: [0, 0, 13.5], rot: [0.06, 0, 0], boxes: [{ x: -2.7, y: -2.7, z: 0, w: 5.4, h: 5.4, d: 14, p: P.hide }],
                  kids: [{ name: 'cauda4', pivot: [0, 0, 13.5], rot: [0.06, 0, 0], boxes: [{ x: -1.5, y: -1.5, z: 0, w: 3, h: 3, d: 10, p: P.hide }] }],
                }],
              }],
            },
          ],
        },
        leg(-1), leg(1),
      ],
    };
  }
  function rigLow(P, v) {
    const leg = s => ({
      name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * 7.5, -9, 3], rot: [-0.3, 0, 0], boxes: [{ x: -4.6, y: -4, z: -5.6, w: 9.2, h: 17, d: 11.2, p: P.limb }],
      kids: [{ name: s < 0 ? 'canelaD' : 'canelaE', pivot: [0, 12.5, 0], rot: [0.3, 0, 0], boxes: [{ x: -3, y: 0, z: -3, w: 6, h: 20, d: 6, p: P.limb }, { x: -4, y: 17.5, z: -9, w: 8, h: 3.2, d: 12, p: P.limb }] }],
    });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'quadril', pivot: [0, -9, 0], rot: [-0.22, 0, 0], boxes: [{ x: -8, y: -9, z: -16, w: 16, h: 18, d: 28, p: P.hide }], kids: [
            { name: 'pescoco', pivot: [0, -3, -14], rot: [0.55, 0, 0], boxes: [{ x: -5.6, y: -11, z: -6.4, w: 11.2, h: 14, d: 11.6, p: P.hide }], kids: [{ name: 'cabeca', pivot: [0, -9.5, -1], rot: [-0.35, 0, 0], boxes: [{ x: -7, y: -9.4, z: -18, w: 14, h: 9.8, d: 22.4, p: P.head }], kids: [{ name: 'mandibula', pivot: [0, 0.4, 2.6], boxes: [{ x: -6.6, y: 0, z: -19.6, w: 13.2, h: 5.4, d: 21, p: P.jaw }] }] }] },
            { name: 'cauda1', pivot: [0, -3, 11.5], boxes: [{ x: -5.4, y: -5.4, z: 0, w: 10.8, h: 10.8, d: 22, p: P.hide }], kids: [{ name: 'cauda2', pivot: [0, 0, 22], rot: [0.1, 0, 0], boxes: [{ x: -2.6, y: -2.6, z: 0, w: 5.2, h: 5.2, d: 24, p: P.hide }] }] },
          ],
        },
        leg(-1), leg(1),
      ],
    };
  }

  // Rachaduras no chão: riscos que saem do pé em raios tortos e se ligam em células perto do centro.
  function cracks() {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false,
      uniforms: { uR: { value: 0 }, uFade: { value: 1 }, uSeed: { value: 0 } },
      vertexShader: 'varying vec2 vP; void main(){ vP = position.xz; gl_Position = projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: `
        uniform float uR, uFade, uSeed; varying vec2 vP;
        vec2 h2(vec2 p){ p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3))); return fract(sin(p) * 43758.5453); }
        float n1(float x){ float i = floor(x), f = fract(x); f = f*f*(3.0-2.0*f); return mix(fract(sin(i*91.7+uSeed)*4375.5), fract(sin((i+1.0)*91.7+uSeed)*4375.5), f); }
        void main(){
          float r = length(vP), a = atan(vP.y, vP.x);
          if (r > uR) discard;
          // raios: cada um com a sua torção
          float k = a / 6.2831 * 13.0 + (n1(r * 1.3) - 0.5) * 0.9 + (n1(r * 4.1 + 7.0) - 0.5) * 0.25;
          float ray = abs(fract(k) - 0.5);
          float width = 0.035 * (1.0 - r / max(uR, 0.01)) + 0.008;
          float line = 1.0 - smoothstep(width, width + 0.02, ray * (1.0 + r * 0.4));
          line *= step(0.3, n1(floor(k) * 3.7 + 1.0) + (1.0 - r / 6.0) * 0.6);
          // células perto do centro
          vec2 p = vP * 2.2; vec2 ip = floor(p); float f1 = 9.0, f2 = 9.0;
          for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) { vec2 g = vec2(float(i), float(j)); vec2 o = h2(ip + g + uSeed); float e = length(g + o - fract(p)); if (e < f1) { f2 = f1; f1 = e; } else if (e < f2) f2 = e; }
          float cell = (1.0 - smoothstep(0.0, 0.06, f2 - f1)) * (1.0 - smoothstep(0.6, 1.6, r));
          float m = max(line, cell);
          if (m < 0.02) discard;
          float tip = smoothstep(uR, uR - 0.4, r);
          gl_FragColor = vec4(vec3(0.16, 0.1, 0.06), m * tip * uFade * 0.92);
        }`,
    });
    const g = new THREE.PlaneGeometry(16, 16); g.rotateX(-Math.PI / 2);
    const m = new THREE.Mesh(g, mat); m.frustumCulled = false;
    m.at = (c, R, fade, seed) => { m.visible = true; m.position.set(c.x, 0.03, c.z); mat.uniforms.uR.value = R; mat.uniforms.uFade.value = fade; mat.uniforms.uSeed.value = seed; };
    return m;
  }
  // Onda de terra: o chão sobe numa crista que corre pra fora; a crista é quebrada em blocos irregulares.
  function quake() {
    const g = new THREE.PlaneGeometry(18, 18, 160, 160); g.rotateX(-Math.PI / 2);
    const mat = new THREE.ShaderMaterial({
      transparent: true, side: THREE.DoubleSide,
      uniforms: { uR: { value: 0 }, uAmp: { value: 1 }, uC: { value: new THREE.Color(0xb48c5a) } },
      vertexShader: `
        uniform float uR, uAmp; varying float vH; varying vec2 vP;
        float h(vec2 p){ return fract(sin(dot(floor(p), vec2(12.9898, 78.233))) * 43758.5453); }
        void main(){
          vec3 p = position; float r = length(p.xz);
          float wave = exp(-pow((r - uR) / 0.55, 2.0)) * (r < uR + 0.6 ? 1.0 : 0.0);
          float blocky = 0.55 + 0.9 * h(p.xz * 2.5);
          p.y = wave * uAmp * blocky * smoothstep(0.4, 1.2, r);
          vH = p.y; vP = p.xz;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(p, 1.0);
        }`,
      fragmentShader: `
        uniform vec3 uC; varying float vH; varying vec2 vP;
        float h(vec2 p){ return fract(sin(dot(floor(p), vec2(12.9898, 78.233))) * 43758.5453); }
        void main(){
          if (vH < 0.03) discard;
          vec3 c = uC * (0.7 + 0.45 * h(vP * 2.5)) * (0.75 + 0.4 * clamp(vH, 0.0, 1.0));
          c = mix(c, vec3(0.38, 0.26, 0.16), smoothstep(0.03, 0.1, vH) * (1.0 - smoothstep(0.1, 0.3, vH)) * 0.5);
          gl_FragColor = vec4(c, 1.0);
        }`,
    });
    const m = new THREE.Mesh(g, mat); m.frustumCulled = false;
    m.at = (c, R, amp) => { m.visible = amp > 0.01; m.position.set(c.x, 0, c.z); mat.uniforms.uR.value = R; mat.uniforms.uAmp.value = amp; };
    return m;
  }

  const WALK_T = [[0, -0.42], [0.2, -0.2], [0.45, 0.3], [0.55, 0.36], [0.7, 0.1], [0.88, -0.38]];
  const WALK_K = [[0, 0], [0.45, 0], [0.6, 0.5], [0.75, 0.8], [0.9, 0.25]];
  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const breath = loop([[0, 0], [0.45, 1], [0.999, 0]], t / 3.2);
    const tail = (k, ph) => { r('cauda1', 0, 0.06 * k * Math.sin(ph), 0); r('cauda2', 0, 0.1 * k * Math.sin(ph - 0.7), 0); r('cauda3', 0, 0.14 * k * Math.sin(ph - 1.4), 0); r('cauda4', 0, 0.18 * k * Math.sin(ph - 2.1), 0); };
    const walk = (ph, amp) => {
      for (const [s, off] of [['D', 0], ['E', 0.5]]) {
        r('perna' + s, loop(WALK_T, ph + off) * amp, 0, 0);
        r('canela' + s, 0.4 * loop(WALK_K, ph + off) * amp, 0, 0);
        r('meta' + s, -0.4 * loop(WALK_K, ph + off) * amp, 0, 0);
        r('pe' + s, 0.45 * loop(WALK_K, ph + off - 0.05) * amp, 0, 0);
      }
      P.quadril.obj.position.y += 1.2 * amp * Math.abs(Math.sin(ph * Math.PI * 2));
      r('quadril', 0, 0.04 * amp * Math.sin(ph * Math.PI * 2), 0.03 * amp * Math.sin(ph * Math.PI * 2));
    };

    if (mode === 'parado') {
      r('quadril', -0.02 * breath, 0, 0);
      r('pescoco', 0.03 * breath, 0.15 * Math.sin(t * 0.5), 0); r('cabeca', 0, 0.1 * Math.sin(t * 0.5 - 0.5), 0);
      r('mandibula', 0.06 * breath + 0.25 * seq([[0.7, 0], [0.75, 1], [0.9, 1], [0.98, 0]], fr(t / 5.5)), 0, 0);
      r('anteD', 0.15 * Math.sin(t * 2.1), 0, 0); r('anteE', 0.15 * Math.sin(t * 2.1 + 1), 0, 0);
      tail(0.7, t * 0.9);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 0.55;
      walk(ph, 1);
      r('pescoco', -0.04 * Math.sin(ph * Math.PI * 4), -0.04 * Math.sin(ph * Math.PI * 2), 0);
      tail(1, ph * Math.PI * 2 + 1);
      if (fr(ph * 2) < 0.06) ctx.shake(0.12);
      return;
    }

    if (mode === 'investida') {
      const T = t % 4.2;
      const R = ctx.ref; const RZ = -10; R.place(0, RZ, 0);
      // ruge, abaixa a cabeça e corre: passa por cima do que tiver no caminho
      const roar = seq([[0, 0], [0.2, 1, 'out'], [0.75, 1], [0.9, 0]], T);
      const low = seq([[0.8, 0], [1.0, 1, 'out'], [2.0, 1], [2.5, 0, 'io']], T);
      const run = seq([[0.95, 0], [1.95, 1, 'in']], T);
      const stop = seq([[1.95, 0], [2.4, 1, 'out']], T);
      r('pescoco', -0.35 * roar + 0.3 * low, 0.1 * Math.sin(T * 30) * roar, 0); r('cabeca', -0.25 * roar + 0.25 * low, 0, 0);
      r('mandibula', 0.65 * roar + 0.2 * low, 0, 0);
      r('quadril', 0.12 * low, 0, 0);
      if (run > 0 && run < 1) walk(T * 1.6, 1.5);
      obj.position.z += (RZ + 3.5) * run + (-1.2) * stop;
      if (roar > 0.5) ctx.shake(0.3);
      if (T > 0.95 && T < 2.4) {
        ctx.shake(0.5);
        for (let i = 0; i < 6; i++) {
          const st = 1.0 + i * 0.18, age = (T - st) / 1.3;
          if (age <= 0 || age >= 1) continue;
          const z = (RZ + 3.5) * seq([[0.95, 0], [1.95, 1, 'in']], st) + 1.4;
          ctx.get('poeira' + i, FX.volume).set(V3(i % 2 ? 0.9 : -0.9, 0.5, z), 0.75 + 0.12 * (i % 3), age, { seed: 5.3 + i * 2.71, dens: 6, rise: 0.4, smoke: [0.8, 0.66, 0.48] });
        }
      }
      if (T > 1.72) {
        // o alvo é atropelado e voa longe
        const fly = seq([[1.72, 0], [2.6, 1, 'out']], T);
        R.place(-0.6 * fly, RZ - 5.5 * fly, 2.6 * Math.sin(Math.PI * Math.min(1, fly * 0.85)));
        R.spin(-3 * fly); R.lean(-1.3 * Math.min(1, fly * 2) * (1 - seq([[3.3, 0], [4.0, 1]], T)));
        if (T < 1.9) ctx.shake(1.0);
        ctx.get('pedras', () => FX.debris(22, 13, [[176, 140, 100], [140, 108, 74], [214, 184, 140], [96, 80, 66]])).at(V3(0, 0.3, RZ + 0.6), T - 1.72);
      }
      return;
    }

    if (mode === 'pisao') {
      const T = t % 4.0;
      const R = ctx.ref; const RZ = -8; R.place(0.8, RZ, 0);
      // levanta a pata alta, segura, pisa com o corpo todo
      const lift = seq([[0, 0], [0.7, 1, 'out'], [0.95, 1], [1.05, 0, 'in']], T);
      const lean = seq([[0.2, 0], [0.8, 1], [1.05, 0]], T);
      const settle = seq([[1.05, 0], [1.2, 1], [1.8, 0, 'io']], T);
      r('pernaD', -1.25 * lift, 0, 0.1 * lift); r('canelaD', 1.1 * lift, 0, 0); r('peD', -0.3 * lift, 0, 0);
      r('quadril', -0.18 * lean + 0.1 * settle, 0, 0.1 * lean); P.quadril.obj.position.y -= 2 * lean - 2.5 * settle;
      r('pescoco', -0.3 * lean + 0.25 * settle, 0, 0); r('mandibula', 0.5 * lean + 0.4 * settle, 0, 0);
      r('pernaE', 0.1 * lean, 0, -0.06 * lean); r('canelaE', 0.2 * settle, 0, 0);
      const C = V3(-0.48, 0, -0.45);
      if (T > 1.05) {
        const k = T - 1.05;
        ctx.shake(Math.max(0, 1.1 - k * 0.9));
        ctx.get('rachas', cracks).at(C, Math.min(6.5, 0.6 + k * 12), 1 - seq([[3.0, 0], [4.0, 1]], T), 3.7);
        const R2 = 0.6 + k * 5.2;
        ctx.get('onda', quake).at(C, R2, 0.7 * Math.max(0, 1 - k / 1.7));
        ctx.get('poPe', FX.volume).set(V3(C.x, 0.4, C.z), 1.0, k / 1.4, { seed: 8.2, dens: 6, rise: 0.4, smoke: [0.82, 0.68, 0.5] });
        ctx.get('lascas', () => FX.debris(30, 19, [[180, 144, 100], [150, 116, 80], [210, 180, 136], [110, 90, 70]])).at(V3(C.x, 0.1, C.z), k);
        // a onda chega no alvo e ele salta
        const d = Math.hypot(0.8 - C.x, RZ - C.z), hitT = (d - 0.6) / 5.2;
        const up = seq([[hitT, 0], [hitT + 0.25, 1, 'out'], [hitT + 0.55, 0, 'in']], k);
        R.place(0.8, RZ - 0.4 * seq([[hitT, 0], [hitT + 0.55, 1]], k), 1.3 * up);
        R.lean(-0.7 * seq([[hitT, 0], [hitT + 0.4, 1], [hitT + 1.6, 1], [hitT + 2.2, 0]], k)); R.armsOut(0.5 * up);
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(4), variant(22), variant(57)];
  fixed[0].skin = SKINS[0]; fixed[1].skin = SKINS[1]; fixed[1].scar = true; fixed[2].skin = SKINS[2]; fixed[2].chipped = true;
  for (const v of fixed) v.belly = mix(v.skin, [244, 226, 176], 0.72);
  const def = v => ({ def: rigDef(painters(v), v), low: rigLow(painters(v), v) });
  window.MOBS.push({
    id: 'tiranossauro', nome: 'Tiranossauro', bioma: 'Dinossauros', tier: 'Grandão · forte · tier 5', refX: 5,
    variants: fixed.map((v, i) => ({ nome: ['Marrom', 'Verde', 'Vermelho'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['investida', 'Investida'], ['pisao', 'Pisão Sísmico']],
    cam: 13, lookY: 2.0, lookZ: { investida: -5, pisao: -4 }, camFar: { investida: 19, pisao: 16 }, camYaw: { investida: -1.35, pisao: -0.9 },
    info: {
      'Tamanho': '3,6 blocos de altura, 7 de comprimento',
      'Vida': '220 (tier 5)',
      'Jeito': 'Territorial e sozinho. Ruge quando você entra no espaço dele; se continuar, ataca. Come os outros dinos se estiver com fome.',
      'Investida': 'Ruge (aviso), abaixa a cabeça e corre em linha reta derrubando árvores e blocos fracos. Quem é atropelado voa longe. Ele demora pra frear: dá pra contra-atacar por trás.',
      'Pisão Sísmico': 'Levanta a pata alta e pisa: o chão racha e uma onda de terra corre pra fora até 6 blocos. Pular na hora certa escapa.',
      'Solta': 'Carne grande de dinossauro (enche muito a fome), couro grosso; raro: dente de tiranossauro.',
      'Variações': 'Cor do couro, manchas, quantos calombos nas costas, cicatriz e dente quebrado sorteados.',
    },
  });
})();
