// Cell Jr. (evento dos Jogos do Cell). Pequeno (1,3 bloco), rápido e debochado. Corpo azul com pintas escuras, rosto
// claro com as listras roxas do Cell, coroa de cristas e asinhas pretas nas costas.
// Golpes: Kamehameha Jr. (junta as mãos do lado, bola azul cresce e solta o raio) e Golpe Sumido (pisca, some,
// aparece atrás do alvo e chuta).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const V3 = (x, y, z) => new THREE.Vector3(x, y, z);

  const BODIES = [[110, 175, 230], [96, 160, 220], [126, 186, 236], [104, 170, 214]];
  const SPOTS = [[36, 64, 118], [44, 58, 104], [30, 70, 110]];
  function variant(seed) {
    const R = FX.rnd(seed);
    const pick = a => a[Math.floor(R() * a.length)];
    return { seed, body: pick(BODIES), spot: pick(SPOTS), spotK: 0.69 + R() * 0.05, grin: R() < 0.5 };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.84, hi = 1.12) => cel(base, 0.62 - 0.38 * q.ny + 0.1 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0), lo, hi);
    // pintas do Cell: manchas redondas de tamanhos diferentes, não um padrão repetido
    const spots = (q, c) => { const k = n3(q.ax * 0.55, q.ay * 0.55, q.az * 0.55, 70 + S); return k > v.spotK ? mix(c, v.spot, k > v.spotK + 0.05 ? 1 : 0.5) : c; };
    const P = {};
    P.body = q => spots(q, shade(v.body, q, 1));
    P.belly = q => { const c = shade(mix(v.body, [230, 236, 246], 0.55), q, 2); return q.f === 'north' && fr(q.py / 1.6) < 0.18 ? mul(c, 0.9) : c; };
    P.pale = q => shade([236, 232, 242], q, 3, 0.86, 1.08);
    P.dark = q => shade([34, 34, 44], q, 4, 0.8, 1.25);
    P.head = q => {
      let c = shade(v.body, q, 5, 0.86, 1.08);
      if (q.f !== 'north') return spots(q, c);
      // rosto em pixel de Minecraft (8x7): moldura azul, rosto claro, olho branco com íris rosa pro meio,
      // sobrancelha escura, listras roxas das bochechas e boca
      const x = Math.min(7, Math.floor(q.px)), y = Math.min(6, Math.floor(q.py)), m = Math.min(x, 7 - x);
      const PALE = shade([238, 234, 244], q, 6, 0.9, 1.05);
      if (y >= 1 && m >= 1 && !(y === 6 && m === 1)) c = PALE;
      if (y === 1 && (m === 1 || m === 2)) c = [50, 40, 64];
      if (y === 2 && m === 1) c = [250, 248, 252];
      if (y === 2 && m === 2) c = [205, 45, 100];
      if ((y === 3 || y === 4) && m === 1) c = [150, 70, 170];
      if (y === 5 && (m === 3 || (v.grin && m === 2))) c = v.grin ? (m === 3 ? [110, 30, 80] : [150, 60, 120]) : [150, 70, 140];
      return c;
    };
    P.crest = q => { let c = shade(mul(v.body, 0.85), q, 7); if (q.py < 0.6) c = mul(c, 1.15); return spots(q, c); };
    P.wing = q => {
      if (q.f === 'top' || q.f === 'bottom') return null;
      // élitro: oval cortado, brilho na borda
      const u = q.px / q.b.w, w = q.py / q.b.h;
      const e = ((u - 0.5) / 0.5) ** 2 + ((w - 0.45) / 0.55) ** 2;
      if (e > 1) return null;
      return e > 0.7 ? [70, 72, 92] : shade([28, 28, 38], q, 8, 0.8, 1.3);
    };
    return P;
  }

  function rigDef(P, v, low) {
    const arm = s => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * 4, -5.4, 0], boxes: [{ x: -1.2, y: -0.8, z: -1.2, w: 2.4, h: 3.6, d: 2.4, p: P.body }],
      kids: [{
        name: s < 0 ? 'anteD' : 'anteE', pivot: [0, 2.8, 0], boxes: [{ x: -1.1, y: 0, z: -1.1, w: 2.2, h: 3, d: 2.2, p: P.body }],
        kids: [{ name: s < 0 ? 'maoD' : 'maoE', pivot: [0, 3, 0], boxes: [{ x: -1.15, y: -0.4, z: -1.15, w: 2.3, h: 2.2, d: 2.3, p: P.dark }] }],
      }],
    });
    const leg = s => ({
      name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * 1.6, 13, 0], boxes: [{ x: -1.5, y: 0, z: -1.5, w: 3, h: 5.5, d: 3, p: P.body }],
      kids: [{ name: s < 0 ? 'canelaD' : 'canelaE', pivot: [0, 5.5, 0], boxes: [{ x: -1.55, y: 0, z: -1.55, w: 3.1, h: 3.5, d: 3.1, p: P.body }, { x: -1.7, y: 3.5, z: -2.2, w: 3.4, h: 2, d: 3.8, p: P.pale }] }],
    });
    const crests = low ? [{ x: -4.2, y: -9.4, z: -3, w: 8.4, h: 2.6, d: 6, p: P.crest }] : [
      { x: -4.4, y: -9.4, z: -3.2, w: 1.6, h: 3.4, d: 6.2, p: P.crest }, { x: 2.8, y: -9.4, z: -3.2, w: 1.6, h: 3.4, d: 6.2, p: P.crest },
      { x: -4.0, y: -10.8, z: -2.0, w: 1.3, h: 1.8, d: 4.0, p: P.crest }, { x: 2.7, y: -10.8, z: -2.0, w: 1.3, h: 1.8, d: 4.0, p: P.crest },
      { x: -0.7, y: -8.4, z: -4.2, w: 1.4, h: 1.6, d: 2.0, p: P.crest },
    ];
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'quadril', pivot: [0, 13, 0], boxes: [{ x: -3, y: -1.6, z: -1.8, w: 6, h: 2, d: 3.6, p: P.body }], kids: [{
          name: 'tronco', pivot: [0, -1.4, 0], boxes: [{ x: -3.2, y: -6, z: -1.9, w: 6.4, h: 6, d: 3.8, p: P.body }, { x: -2.2, y: -5.4, z: -2.1, w: 4.4, h: 5, d: 0.4, p: P.belly }], kids: [
            { name: 'cabeca', pivot: [0, -6, 0], boxes: [{ x: -4, y: -7, z: -3.5, w: 8, h: 7, d: 7, p: P.head }, ...crests] },
            arm(-1), arm(1),
            ...(low ? [] : [-1, 1].map(s => ({ name: s < 0 ? 'asaD' : 'asaE', pivot: [s * 0.6, -5.6, 1.9], rot: [0.12, -s * 0.35, -s * 0.15], boxes: [{ x: s < 0 ? -4 : 0, y: 0, z: 0, w: 4, h: 6.4, d: 0.4, p: P.wing }] }))),
          ],
        }],
      }, leg(-1), leg(1)],
    };
  }

  function stance(r) {
    r('pernaD', -0.2, 0.2, 0.08); r('canelaD', 0.3, 0, 0); r('pernaE', 0.15, -0.15, -0.08); r('canelaE', 0.15, 0, 0);
    r('bracoD', -0.1, 0, 0.2); r('anteD', -0.4, 0, 0); r('bracoE', -0.1, 0, -0.2); r('anteE', -0.4, 0, 0);
  }

  // raio do Kamehameha: tubo com miolo claro, borda azul e ondas correndo pra frente (normal blending, não estoura no céu)
  function beam() {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, side: THREE.DoubleSide,
      uniforms: { uK: { value: 1 }, uT: { value: 0 }, uL: { value: 1 } },
      vertexShader: 'varying vec3 vN; varying vec3 vV; varying vec2 vU; void main(){ vN=normalize(normalMatrix*normal); vec4 mv=modelViewMatrix*vec4(position,1.0); vV=normalize(-mv.xyz); vU=uv; gl_Position=projectionMatrix*mv; }',
      fragmentShader: `varying vec3 vN; varying vec3 vV; varying vec2 vU; uniform float uK, uT, uL;
        float h(vec2 p){ return fract(sin(dot(p, vec2(41.3, 289.1)))*43758.5); }
        float n(vec2 x){ vec2 i=floor(x), f=fract(x); f=f*f*(3.0-2.0*f); return mix(mix(h(i),h(i+vec2(1,0)),f.x),mix(h(i+vec2(0,1)),h(i+vec2(1,1)),f.x),f.y); }
        void main(){
          float face = abs(dot(vN, vV));
          float along = vU.y * uL * 3.0;
          float w = n(vec2(vU.x * 8.0, along - uT * 14.0)) * 0.6 + n(vec2(vU.x * 19.0, along * 2.3 - uT * 22.0)) * 0.4;
          float core = smoothstep(0.45, 0.95, face + 0.2 * (w - 0.5));
          vec3 c = mix(vec3(0.18, 0.45, 1.0), vec3(0.55, 0.85, 1.0), smoothstep(0.1, 0.5, face));
          c = mix(c, vec3(0.94, 0.98, 1.0), core);
          float a = smoothstep(0.0, 0.3, face) * (0.75 + 0.25 * w) * uK;
          gl_FragColor = vec4(c, a);
        }`,
    });
    const g = new THREE.CylinderGeometry(1, 1, 1, 28, 1, true); g.rotateX(Math.PI / 2);
    const m = new THREE.Mesh(g, mat);
    m.frustumCulled = false;
    m.at = (a, b, r, k, t) => {
      m.visible = k > 0.01;
      const d = b.clone().sub(a), L = d.length();
      m.position.copy(a).add(b).multiplyScalar(0.5); m.lookAt(b); m.scale.set(r, r, L);
      mat.uniforms.uK.value = k; mat.uniforms.uT.value = t; mat.uniforms.uL.value = L;
    };
    return m;
  }
  function ball(c1, c2) {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false,
      uniforms: { uA: { value: new THREE.Color(...c1) }, uB: { value: new THREE.Color(...c2) }, uK: { value: 1 }, uT: { value: 0 } },
      vertexShader: 'varying vec3 vN; varying vec3 vV; varying vec3 vP; void main(){ vN=normalize(normalMatrix*normal); vec4 mv=modelViewMatrix*vec4(position,1.0); vV=normalize(-mv.xyz); vP=position; gl_Position=projectionMatrix*mv; }',
      fragmentShader: `varying vec3 vN; varying vec3 vV; varying vec3 vP; uniform vec3 uA, uB; uniform float uK, uT;
        float h(vec3 p){ p=fract(p*0.3183+vec3(.71,.11,.42)); p*=17.0; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }
        float n(vec3 x){ vec3 i=floor(x), f=fract(x); f=f*f*(3.0-2.0*f);
          return mix(mix(mix(h(i),h(i+vec3(1,0,0)),f.x),mix(h(i+vec3(0,1,0)),h(i+vec3(1,1,0)),f.x),f.y),
                     mix(mix(h(i+vec3(0,0,1)),h(i+vec3(1,0,1)),f.x),mix(h(i+vec3(0,1,1)),h(i+vec3(1,1,1)),f.x),f.y),f.z); }
        void main(){
          float face = abs(dot(vN, vV));
          float a1 = atan(vP.z, vP.x) + uT * 6.0 + vP.y * 2.5;
          float sw = n(vec3(cos(a1) * 2.0, vP.y * 3.0 - uT * 4.0, sin(a1) * 2.0) + uT);
          vec3 c = mix(uA * 0.8, uA, smoothstep(0.2, 0.6, face));
          c = mix(c, uB, smoothstep(0.5, 1.0, face + 0.25 * sw));
          gl_FragColor = vec4(c, smoothstep(0.0, 0.25, face) * uK);
        }`,
    });
    const m = new THREE.Mesh(new THREE.SphereGeometry(1, 28, 20), mat);
    m.frustumCulled = false;
    m.at = (p, r, k, t) => { m.visible = k > 0.01; m.position.copy(p); m.scale.setScalar(r); mat.uniforms.uK.value = k; mat.uniforms.uT.value = t; };
    return m;
  }
  const BLUE = [[0.3, 0.6, 1.0], [0.92, 0.97, 1.0]];
  const K = 0.68, Y0 = 1.5 * K;

  // fantasma do corpo (imagem residual do sumiço): cópia translúcida que copia a pose do original a cada quadro
  function ghost(src) {
    const g = src.clone(true);
    g.traverse(o => { if (o.material) { o.material = o.material.clone(); o.material.transparent = true; o.material.depthWrite = false; o.material.alphaTest = 0.01; o.material.color = new THREE.Color(0.6, 0.85, 1.0); } });
    const A = [], B = [];
    src.traverse(o => A.push(o)); g.traverse(o => B.push(o));
    g.at = (p, ry, k) => {
      g.visible = k > 0.01;
      for (let i = 1; i < A.length; i++) { B[i].position.copy(A[i].position); B[i].rotation.copy(A[i].rotation); B[i].scale.copy(A[i].scale); }
      g.position.copy(p); g.rotation.set(0, ry, 0); g.scale.copy(src.scale);
      g.traverse(o => { if (o.material) o.material.opacity = k; });
    };
    return g;
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    // modelo em escala de criança: 0,68 do tamanho (1,3 bloco com as cristas)
    obj.scale.set(-K / 16, -K / 16, K / 16); obj.position.y += Y0 - 1.5;
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const at = (n, off) => { obj.updateMatrixWorld(true); return P[n].obj.localToWorld(off.clone()); };
    const breath = loop([[0, 0], [0.45, 1], [0.999, 0]], t / 2.2);
    const wings = k => { r('asaD', 0, -0.5 * k, -0.3 * k); r('asaE', 0, 0.5 * k, 0.3 * k); };

    if (mode === 'parado') {
      stance(r);
      // pulinho debochado no lugar, mão na cintura e cabeça inclinada rindo
      const hop = Math.abs(Math.sin(t * 2.4));
      obj.position.y += 0.05 * hop;
      r('bracoE', 0, 0, -0.6); r('anteE', -1.6, 0, 0);
      r('cabeca', -0.05 * breath, 0, 0.18 + 0.05 * Math.sin(t * 2.4));
      r('tronco', -0.04 * breath, 0, 0);
      wings(0.15 + 0.15 * Math.sin(t * 9));
      return;
    }
    if (mode === 'andar') {
      const ph = t * 1.6;
      for (const [s, off] of [['D', 0], ['E', 0.5]]) {
        r('perna' + s, 0.6 * Math.sin((ph + off) * Math.PI * 2), 0, 0); r('canela' + s, 0.5 * Math.max(0, Math.sin((ph + off) * Math.PI * 2 + 1.4)), 0, 0);
        r('braco' + s, -0.5 * Math.sin((ph + off) * Math.PI * 2), 0, 0); r('ante' + s, -0.4, 0, 0);
      }
      P.quadril.obj.position.y += 0.5 * Math.abs(Math.sin(ph * Math.PI * 2));
      r('tronco', 0.12, 0, 0);
      wings(0.2);
      return;
    }

    if (mode === 'kame') {
      const T = t % 3.4;
      const R = ctx.ref; const RZ = -7; R.place(0, RZ, 0);
      stance(r);
      // ka-me-ha-me: gira o tronco, mãos em concha do lado direito; HA: empurra pra frente com as pernas abertas
      const cup = seq([[0, 0], [0.35, 1, 'out'], [1.25, 1], [1.35, 0]], T);
      const push = seq([[1.25, 0], [1.38, 1, 'snap'], [2.3, 1], [2.8, 0, 'io']], T);
      const grow = seq([[0.3, 0], [1.25, 1, 'in']], T);
      r('tronco', 0.1 * cup, 0.7 * cup, 0); r('quadril', 0, 0.25 * cup, 0); r('cabeca', 0, -0.6 * cup, 0);
      r('bracoD', -0.5 * cup - 1.5 * push, 0.4 * cup, 0.2 * cup); r('anteD', -1.3 * cup + 0.9 * push * 0, 0, 0);
      r('bracoE', -0.9 * cup - 1.5 * push, 0.9 * cup, 0.5 * cup - 0.05 * push); r('anteE', -1.1 * cup, 0, 0);
      r('maoD', -0.6 * push, 0, 0); r('maoE', -0.6 * push, 0, 0);
      r('pernaD', -0.25 * push, 0, 0.25 * push); r('pernaE', 0.25 * push, 0, -0.25 * push); r('canelaD', 0.35 * push, 0, 0);
      r('tronco', 0.2 * push, 0, 0);
      obj.position.z += 0.25 * push * (1 - seq([[2.3, 0], [2.8, 1]], T));
      wings(0.4 * push);
      const hands = at('maoD', V3(0, 1.6, 0)).lerp(at('maoE', V3(0, 1.6, 0)), 0.5);
      if (T > 0.3 && T < 1.4) {
        ctx.get('bola', () => ball(...BLUE)).at(hands, 0.04 + 0.16 * grow, 1, T);
        ctx.get('halo', () => DBFX.glow([0.3, 0.6, 1.0])).at(hands, 0.1 + 0.35 * grow, 0.35, T);
        ctx.get('faisca', () => DBFX.grains({ n: 36, seed: 23, palette: [[150, 210, 255], [230, 245, 255], [90, 160, 255]], size: [0.012, 0.028], drag: 3, grav: 0, life: 0.3, spread: 1.0, add: true, cone: Rr => { const a = Rr() * 6.28, b = Rr() * 3.14; return [-Math.sin(b) * Math.cos(a) * 1.6, -Math.cos(b) * 1.6, -Math.sin(b) * Math.sin(a) * 1.6]; } })).at(hands, T - 0.3);
        ctx.get('luz', () => DBFX.light(0x60a0ff)).at(hands, 1.5 * grow, 4);
      }
      if (T >= 1.35 && T < 2.6) {
        const tgt = V3(0, 1.0, RZ + 0.3);
        const out = seq([[1.35, 0], [1.5, 1, 'out']], T), fade = seq([[2.2, 1], [2.6, 0]], T);
        const tip = hands.clone().lerp(tgt, out);
        const rr = 0.2 * fade + 0.03 * Math.sin(T * 50);
        ctx.get('raio', beam).at(hands, tip, rr, fade, T);
        ctx.get('ponta', () => ball(...BLUE)).at(tip, rr * 1.5, fade, T);
        ctx.get('boca', () => ball(...BLUE)).at(hands, rr * 1.4, fade, T + 3);
        ctx.get('luz', () => DBFX.light(0x60a0ff)).at(tip, 3 * fade, 6);
        if (out >= 1) {
          const k = (T - 1.5) / 1.1;
          ctx.get('estouro', FX.volume).set(tgt.clone().add(V3(0, 0.2, -0.3)), 0.9, Math.min(1, k), { fire: 0.5, seed: 3.3, dens: 10, rise: 0.6, smoke: [0.5, 0.62, 0.8] });
          ctx.flash(tgt, Math.max(0, 1 - k * 3)); ctx.shake(0.6 * fade);
          const fly = seq([[1.5, 0], [2.4, 1, 'out']], T);
          R.place(0, RZ - 2.2 * fly, 0.6 * Math.sin(Math.PI * fly)); R.lean(-1.0 * fly * (1 - seq([[2.9, 0], [3.4, 1]], T)));
        }
      }
      return;
    }

    if (mode === 'sumir') {
      const T = t % 3.2;
      const R = ctx.ref; const RZ = -4.5; R.place(0, RZ, 0);
      stance(r);
      // 0-0.55: ri e pisca (o corpo treme); 0.55: some num estalo, fica um vulto azul que se desfaz;
      // 0.75: aparece atrás do alvo já girando; 0.85-1.05: chute giratório nas costas; alvo voa pra frente
      const flick = T > 0.3 && T < 0.55 ? (Math.sin(T * 90) > 0.2 ? 1 : 0) : 0;
      if (T < 0.55) {
        r('cabeca', 0, 0, 0.2 * Math.sin(T * 30)); r('tronco', 0.15, 0, 0); r('pernaD', -0.4, 0, 0); r('canelaD', 0.6, 0, 0);
        obj.position.x += flick * 0.06;
        if (T > 0.45) ctx.get('vulto', () => ghost(obj)).at(V3(-0.15, Y0, 0.05), 0, 0.35);
      } else if (T < 0.78) {
        obj.visible = false;
        const k = (T - 0.55) / 0.23;
        ctx.get('vulto', () => ghost(obj)).at(V3(0, Y0, 0), 0, 0.55 * (1 - k));
        ctx.get('poeiraA', () => DBFX.grains({ n: 40, seed: 31, palette: [[170, 220, 255], [235, 245, 255], [130, 180, 240]], size: [0.012, 0.03], drag: 5, grav: -0.4, life: 0.35, spread: 0.5, add: true, cone: Rr => { const a = Rr() * 6.28; return [Math.cos(a) * 2, 1 + Rr(), Math.sin(a) * 2]; } })).at(V3(0, 0.8, 0), T - 0.55);
      } else {
        const k = T - 0.78;
        obj.position.set(0, Y0, RZ - 1.1); obj.rotation.y = Math.PI;
        const spin = seq([[0.78, 0], [0.98, 1, 'out']], T), back = seq([[1.6, 0], [2.0, 1, 'io']], T);
        obj.rotation.y += 2 * Math.PI * spin * (1 - back) - 0.0;
        r('pernaD', -1.6 * spin * (1 - back), 0, 0.3 * spin * (1 - back)); r('canelaD', -0.1 * spin, 0, 0);
        r('tronco', -0.35 * spin * (1 - back), 0, 0); r('bracoD', 0, 0, 0.9 * spin * (1 - back)); r('bracoE', 0, 0, -0.9 * spin * (1 - back));
        wings(0.5 * spin * (1 - back));
        ctx.get('poeiraB', () => DBFX.grains({ n: 40, seed: 37, palette: [[170, 220, 255], [235, 245, 255], [130, 180, 240]], size: [0.012, 0.03], drag: 5, grav: -0.4, life: 0.35, spread: 0.5, add: true, cone: Rr => { const a = Rr() * 6.28; return [Math.cos(a) * 2, 1 + Rr(), Math.sin(a) * 2]; } })).at(V3(0, 0.8, RZ - 1.1), k);
        if (k < 0.12) ctx.get('vultoB', () => ghost(obj)).at(V3(0, Y0, RZ - 1.45), Math.PI, 0.5 * (1 - k / 0.12));
        const hit = T - 0.95;
        if (hit > 0) {
          const hp = V3(0, 1.1, RZ - 0.3);
          if (hit < 0.08) { ctx.flash(hp, 1); ctx.get('impacto', () => DBFX.muzzle()).at(hp, ctx.camera, 1 - hit / 0.08, 5, 1.1); }
          ctx.shake(Math.max(0, 0.7 - hit * 2));
          if (hit < 0.6) ctx.get('anel', () => DBFX.glow([0.6, 0.85, 1.0])).at(hp, 0.15 + hit * 1.5, 0.5 * (1 - hit / 0.6), T);
          const fly = seq([[0.95, 0], [1.6, 1, 'out']], T);
          R.place(0, RZ + 3.0 * fly, 0.8 * Math.sin(Math.PI * fly)); R.lean(1.1 * fly * (1 - seq([[2.6, 0], [3.2, 1]], T)));
        }
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(4), variant(11), variant(29)];
  fixed[0].grin = true; fixed[1].body = BODIES[1]; fixed[2].grin = false;
  const def = v => ({ def: rigDef(painters(v), v, false), low: rigDef(painters(v), v, true) });
  window.MOBS.push({
    id: 'cell_jr', nome: 'Cell Jr.', bioma: 'Namek e estruturas', tier: 'Médio · tier 6',
    variants: fixed.map((v, i) => ({ nome: ['Risonho', 'Azul escuro', 'Sério'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['kame', 'Kamehameha Jr.'], ['sumir', 'Golpe Sumido']],
    cam: 4.5, lookY: 0.8, lookZ: { kame: -3.2, sumir: -2.4 }, camFar: { kame: 9, sumir: 7.5 }, camYaw: { kame: -1.25, sumir: -1.3 },
    info: {
      'Tamanho': '1,3 bloco',
      'Vida': '60 (tier 6). Pequeno mas forte: os 7 juntos são uma luta de verdade.',
      'Jeito': 'Só no evento dos Jogos do Cell: nascem 7 de uma vez em volta da arena, riem, pulam e atacam em bando. Fogem rindo se ficam sozinhos com pouca vida.',
      'Kamehameha Jr.': 'Junta as mãos do lado por 1 s (a bola azul avisa) e solta o raio reto. No jogo o dano do raio vem em pacotes (o Escudo de tier não come cada tick).',
      'Golpe Sumido': 'Pisca, some num estalo deixando um vulto, aparece atrás de você e dá um chute giratório que te joga pra frente. Olhar pra trás na hora certa bloqueia.',
      'Solta': 'Fragmento de Cell (célula), pedaço de asa; raro: Alma do Cell Jr. (lembrança do evento).',
      'Variações': 'Tom do azul, cor e tamanho das pintas, e cara (risonho ou sério) sorteados.',
    },
  });
})();
