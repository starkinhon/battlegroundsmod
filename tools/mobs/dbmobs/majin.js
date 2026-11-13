// Spopovich e Yamu (Deserto do Diabo). A dupla com a marca Majin que o Babidi solta pelo deserto pra caçar energia
// pra reviver o Majin Boo. Andam juntos: o Spopovich segura, o Yamu suga com o aparelho.
// Spopovich: Abraço Esmagador (agarra, levanta e esmaga; as veias saltam) e Investida Majin (corre de ombro).
// Yamu: Sugar Energia (crava a agulha do aparelho e o frasco enche de luz) e Rajada Majin (bola de ki vermelha).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const V3 = (x, y, z) => new THREE.Vector3(x, y, z);

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.84, hi = 1.12) => cel(base, 0.62 - 0.38 * q.ny + 0.1 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0), lo, hi);
    const P = {};
    // pele com músculo marcado (sombra nas divisões) e veias quando "inchado" (v.veins controla no pintor da prévia)
    P.skin = q => { let c = shade(v.skin, q, 1); if (v.big && q.f === 'north' && Math.abs(q.px - q.b.w / 2) < 0.3) c = mul(c, 0.88); return c; };
    P.vein = q => { let c = shade(v.skin, q, 1); const k = Math.abs(n3(q.ax * 0.5, q.ay * 0.25, q.az * 0.5, 180 + S) - 0.5); if (k < 0.025) c = mix(c, [150, 70, 80], 0.3); return c; };
    // macacão de luta (calça larga) com faixa na cintura e o M grande no peito
    P.gi = q => {
      let c = shade(v.gi, q, 2);
      if (q.f !== 'top' && q.f !== 'bottom' && fr(q.ay * 0.3 + q.ax * 0.05) < 0.08) c = mul(c, 0.86);
      return c;
    };
    P.vest = q => {
      let c = shade(v.vest, q, 3);
      if (q.f === 'north') {
        const u = q.px / q.b.w, w = q.py / q.b.h;
        // "M" do Majin em traços: duas pernas e o V no meio
        const M = [[0.3, 0.7, 0.3, 0.3], [0.3, 0.3, 0.5, 0.55], [0.5, 0.55, 0.7, 0.3], [0.7, 0.3, 0.7, 0.7]];
        for (const [x0, y0, x1, y1] of M) { const dx = x1 - x0, dy = y1 - y0, l2 = dx * dx + dy * dy, k = Math.max(0, Math.min(1, ((u - x0) * dx + (w - y0) * dy) / l2)); if (Math.hypot(u - x0 - dx * k, w - y0 - dy * k) < 0.055) c = [190, 40, 50]; }
      }
      return c;
    };
    P.belt = q => shade(v.belt, q, 4);
    P.boot = q => shade([50, 44, 40], q, 5);
    P.head = q => {
      let c = shade(v.skin, q, 6, 0.88, 1.06);
      if (q.f !== 'north') { if (v.hair && (q.f === 'west' || q.f === 'east') && q.py > 2.2 && q.py < 4 && q.pz > 3) c = shade([40, 36, 34], q, 7); return c; }
      const W = q.b.w, x = Math.min(W - 1, Math.floor(q.px)), y = Math.min(q.b.h - 1, Math.floor(q.py)), m = Math.min(x, W - 1 - x);
      // marca Majin na testa (M preto pequeno), olho pequeno e mau, boca
      // M de 5x3 centrado na testa
      const s0 = (W - 5) / 2, mx = x - s0;
      const MM = ['X...X', 'XX.XX', 'X.X.X'];
      if (y <= 2 && mx >= 0 && mx < 5 && MM[y][mx] === 'X') c = [30, 26, 30];
      if (y === 4 && m === 1) c = v.big ? [30, 24, 24] : [240, 236, 230];
      if (y === 4 && m === 2) c = v.big ? mul(v.skin, 0.75) : [30, 24, 24];
      if (v.big && y === 5 && m === 2) c = mul(v.skin, 0.8);
      if (y === q.b.h - 1 && m >= 2) c = v.big ? [120, 60, 60] : mul(v.skin, 0.6);
      if (!v.big && y === q.b.h - 2 && m >= 2 && m <= 3) c = [50, 40, 36];   // bigodinho do Yamu
      return c;
    };
    P.ear = q => shade(mul(v.skin, 0.92), q, 8);
    P.device = q => { let c = shade([140, 146, 156], q, 9, 0.8, 1.3); if (fr(q.ay * 1.2) < 0.15) c = mul(c, 0.7); return c; };
    P.flask = q => mix([150, 200, 220], [255, 255, 255], q.f === 'north' ? 0.3 : 0.1);
    P.needle = q => [220, 224, 230];
    return P;
  }

  function rigDef(P, v, low) {
    const big = v.big, w = big ? 12 : 8, L = big ? 2.6 : 0, armW = big ? 4.4 : 3, hw = big ? 9 : 7, hh = big ? 7 : 7;
    const arm = s => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * (w / 2 + armW / 2), -9 - L, 0], boxes: [{ x: -armW / 2, y: -1, z: -armW / 2, w: armW, h: 5 + L * 0.6, d: armW, p: big ? P.vein : P.skin }],
      kids: [{
        name: s < 0 ? 'anteD' : 'anteE', pivot: [0, 4 + L * 0.6, 0], boxes: [{ x: -armW / 2 + 0.2, y: 0, z: -armW / 2 + 0.2, w: armW - 0.4, h: 4.4 + L * 0.3, d: armW - 0.4, p: big ? P.vein : P.skin }],
        kids: [{
          name: s < 0 ? 'maoD' : 'maoE', pivot: [0, 4.4 + L * 0.3, 0], boxes: [{ x: -armW / 2, y: -0.4, z: -armW / 2, w: armW, h: big ? 3.6 : 3, d: armW, p: P.skin }],
          // aparelho de sugar energia do Yamu: frasco, corpo de metal e agulha comprida
          kids: !big && s < 0 ? [{ name: 'aparelho', pivot: [0, 1.8, -0.2], boxes: [{ x: -1, y: -0.6, z: -1, w: 2, h: 3.4, d: 2, p: P.device }, ...(low ? [] : [{ x: -0.8, y: 2.8, z: -0.8, w: 1.6, h: 1.2, d: 1.6, p: P.device }]), { x: -0.25, y: 4, z: -0.25, w: 0.5, h: 3, d: 0.5, p: P.needle }, { x: -0.9, y: -0.4, z: -2.6, w: 1.8, h: 3, d: 1.6, p: P.flask }] }] : [],
        }],
      }],
    });
    const leg = s => ({
      name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * (big ? 3 : 2), 12 - L, 0], boxes: [{ x: big ? -2.8 : -2.1, y: 0, z: big ? -2.8 : -2.1, w: big ? 5.6 : 4.2, h: 6 + L / 2, d: big ? 5.6 : 4.2, p: P.gi }],
      kids: [{ name: s < 0 ? 'canelaD' : 'canelaE', pivot: [0, 6 + L / 2, 0], boxes: [{ x: big ? -2.6 : -2, y: 0, z: big ? -2.6 : -2, w: big ? 5.2 : 4, h: 4 + L / 2, d: big ? 5.2 : 4, p: P.gi }, { x: big ? -2.8 : -2.2, y: 4 + L / 2, z: big ? -3.4 : -2.8, w: big ? 5.6 : 4.4, h: 2, d: big ? 6.4 : 5, p: P.boot }] }],
    });
    const ears = v.ears ? [{ x: -hw / 2 - 1.2, y: -5, z: -0.5, w: 1.2, h: 2.4, d: 1.2, p: P.ear }, { x: hw / 2, y: -5, z: -0.5, w: 1.2, h: 2.4, d: 1.2, p: P.ear }] : [];
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'quadril', pivot: [0, 12 - L, 0], boxes: [{ x: -w / 2, y: -2.6, z: -2.4, w, h: 2.8, d: 4.8, p: P.belt }], kids: [{
          name: 'tronco', pivot: [0, -2.4, 0], boxes: [
            { x: -w / 2, y: -10 - L, z: big ? -3.2 : -2, w, h: 10 + L, d: big ? 6.4 : 4, p: big ? P.vein : P.skin },
            { x: -w / 2 - 0.3, y: -10.2 - L, z: big ? -3.5 : -2.3, w: w + 0.6, h: 7.4 + L, d: big ? 7 : 4.6, p: P.vest },
          ], kids: [
            { name: 'cabeca', pivot: [0, -10 - L, big ? -0.8 : 0], boxes: [{ x: -hw / 2, y: -hh, z: -hw / 2, w: hw, h: hh, d: hw, p: P.head }, ...ears] },
            arm(-1), arm(1),
          ],
        }],
      }, leg(-1), leg(1)],
    };
  }

  function stance(r, big) {
    r('pernaD', -0.1, 0.2, big ? 0.12 : 0.06); r('pernaE', 0.1, -0.2, big ? -0.12 : -0.06);
    r('bracoD', -0.1, 0, big ? 0.3 : 0.12); r('anteD', -0.4, 0, 0); r('bracoE', -0.1, 0, big ? -0.3 : -0.12); r('anteE', -0.4, 0, 0);
  }
  function kiBall(c1, c2) {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false,
      uniforms: { uA: { value: new THREE.Color(...c1) }, uB: { value: new THREE.Color(...c2) }, uK: { value: 1 }, uT: { value: 0 } },
      vertexShader: 'varying vec3 vN; varying vec3 vV; varying vec3 vP; void main(){ vN=normalize(normalMatrix*normal); vec4 mv=modelViewMatrix*vec4(position,1.0); vV=normalize(-mv.xyz); vP=position; gl_Position=projectionMatrix*mv; }',
      fragmentShader: `varying vec3 vN; varying vec3 vV; varying vec3 vP; uniform vec3 uA, uB; uniform float uK, uT;
        float h(vec3 p){ p=fract(p*0.3183+vec3(.71,.11,.42)); p*=17.0; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }
        float n(vec3 x){ vec3 i=floor(x), f=fract(x); f=f*f*(3.0-2.0*f);
          return mix(mix(mix(h(i),h(i+vec3(1,0,0)),f.x),mix(h(i+vec3(0,1,0)),h(i+vec3(1,1,0)),f.x),f.y),
                     mix(mix(h(i+vec3(0,0,1)),h(i+vec3(1,0,1)),f.x),mix(h(i+vec3(0,1,1)),h(i+vec3(1,1,1)),f.x),f.y),f.z); }
        void main(){ float face = abs(dot(vN, vV));
          float a1 = atan(vP.z, vP.x) + uT * 6.0 + vP.y * 2.5;
          float sw = n(vec3(cos(a1) * 2.0, vP.y * 3.0 - uT * 4.0, sin(a1) * 2.0) + uT);
          vec3 c = mix(uA * 0.75, uA, smoothstep(0.2, 0.6, face));
          c = mix(c, uB, smoothstep(0.5, 1.0, face + 0.25 * sw));
          gl_FragColor = vec4(c, smoothstep(0.0, 0.25, face) * uK); }`,
    });
    const m = new THREE.Mesh(new THREE.SphereGeometry(1, 24, 18), mat); m.frustumCulled = false;
    m.at = (p, r, k, t) => { m.visible = k > 0.01; m.position.copy(p); m.scale.setScalar(r); mat.uniforms.uK.value = k; mat.uniforms.uT.value = t; };
    return m;
  }
  // energia sugada: fio de luz ondulando do alvo até o frasco
  function drainStream() {
    const N = 60, pos = new Float32Array(N * 3), al = new Float32Array(N);
    const geo = new THREE.BufferGeometry(); geo.setAttribute('position', new THREE.BufferAttribute(pos, 3)); geo.setAttribute('al', new THREE.BufferAttribute(al, 1));
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
      vertexShader: 'attribute float al; varying float vA; void main(){ vA=al; vec4 mv=modelViewMatrix*vec4(position,1.0); gl_PointSize = 0.12*420.0/-mv.z*(0.6+al); gl_Position=projectionMatrix*mv; }',
      fragmentShader: 'varying float vA; void main(){ vec2 d=gl_PointCoord-0.5; float r=length(d); if(r>0.5) discard; gl_FragColor=vec4(vec3(1.0,0.95,0.6)*vA*(1.0-r*1.6), 1.0); }',
    });
    const pts = new THREE.Points(geo, mat); pts.frustumCulled = false;
    pts.at = (a, b, t, k) => {
      pts.visible = k > 0.01;
      for (let i = 0; i < N; i++) {
        const f = fr(i / N + t * 1.4), p = a.clone().lerp(b, f);
        p.y += 0.12 * Math.sin(f * 14 + t * 9 + i) * (1 - f); p.x += 0.1 * Math.cos(f * 11 + i) * (1 - f);
        pos.set([p.x, p.y, p.z], i * 3); al[i] = k * Math.sin(Math.PI * f);
      }
      geo.attributes.position.needsUpdate = true; geo.attributes.al.needsUpdate = true;
    };
    return pts;
  }

  function animSpo(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const at = (n, off) => { obj.updateMatrixWorld(true); return P[n].obj.localToWorld(off.clone()); };
    const breath = loop([[0, 0], [0.45, 1], [0.999, 0]], t / 2.6);
    if (mode === 'parado') {
      stance(r, true);
      // respira bufando, ombros subindo, olhar vidrado de quem está controlado
      r('tronco', -0.05 * breath, 0, 0); r('bracoD', 0, 0, 0.08 * breath); r('bracoE', 0, 0, -0.08 * breath);
      r('cabeca', 0.08, 0.15 * Math.sin(t * 0.6), 0.05 * Math.sin(t * 0.8));
      return;
    }
    if (mode === 'andar') {
      const ph = t * 0.85;
      for (const [s, off] of [['D', 0], ['E', 0.5]]) {
        r('perna' + s, 0.4 * Math.sin((ph + off) * Math.PI * 2), 0, 0); r('canela' + s, 0.35 * Math.max(0, Math.sin((ph + off) * Math.PI * 2 + 1.4)), 0, 0);
        r('braco' + s, -0.3 * Math.sin((ph + off) * Math.PI * 2), 0, 0.25 * (s === 'D' ? 1 : -1)); r('ante' + s, -0.4, 0, 0);
      }
      P.quadril.obj.position.y += 0.7 * Math.abs(Math.sin(ph * Math.PI * 2));
      r('tronco', 0, 0.12 * Math.sin(ph * Math.PI * 2), 0);
      return;
    }
    if (mode === 'abraco') {
      const T = t % 4.0;
      const R = ctx.ref; const RZ = -3.5;
      stance(r, true);
      // avança, abre os braços, abraça e levanta; aperta três vezes (veias pulsam, aura escura); joga no chão
      const go = seq([[0, 0], [0.6, 1, 'io']], T);
      obj.position.z += (RZ + 1.4) * go;
      const open = seq([[0.1, 0], [0.5, 1, 'out'], [0.75, 1], [0.85, 0]], T);
      const hug = seq([[0.75, 0], [0.9, 1, 'snap'], [2.9, 1], [3.1, 0]], T);
      const lift = seq([[0.9, 0], [1.3, 1, 'out'], [2.9, 1], [3.05, 0]], T);
      const sq = [1.4, 1.9, 2.4].reduce((k, st) => k + seq([[st, 0], [st + 0.08, 1, 'snap'], [st + 0.35, 0]], T), 0);
      r('bracoD', -0.4 * open - 1.3 * hug, -0.8 * open, 0.9 * open + 0.2 * hug); r('bracoE', -0.4 * open - 1.3 * hug, 0.8 * open, -0.9 * open - 0.2 * hug);
      r('anteD', -1.4 * hug - 0.3 * sq, 0.4 * hug, 0); r('anteE', -1.4 * hug - 0.3 * sq, -0.4 * hug, 0);
      r('tronco', -0.35 * lift + 0.08 * sq, 0, 0); r('cabeca', -0.2 * lift - 0.15 * sq, 0, 0);
      r('pernaD', 0.1 * lift, 0, 0.1 * lift); r('pernaE', 0.1 * lift, 0, -0.1 * lift);
      const drop = seq([[2.9, 0], [3.2, 1, 'in']], T);
      R.place(0, RZ + 0.75 * hug * (1 - drop) + 0.1, (0.9 * lift) * (1 - drop));
      if (T < 0.75) R.place(0, RZ, 0);
      R.lean(0.25 * hug * (1 - drop) - 1.4 * seq([[3.0, 0], [3.25, 1], [3.7, 1], [4.0, 0]], T));
      R.armsOut(-0.0); R.punch(0);
      if (T > 0.9 && T < 3.0) {
        // aura escura com faíscas vermelhas da marca Majin
        ctx.get('aura', FX.volume).set(at('tronco', V3(0, -8, 0)), 1.0 + 0.1 * sq, 0.35 + 0.1 * Math.sin(T * 3), { fire: 0.15, seed: 11.1, dens: 2.5, rise: 1.2, smoke: [0.16, 0.1, 0.16] });
        ctx.get('faiscasM', () => DBFX.grains({ n: 40, seed: 191, palette: [[255, 60, 60], [255, 140, 120], [180, 20, 30]], size: [0.015, 0.035], drag: 3, grav: -1, life: 0.4, spread: 2.0, add: true, cone: Rr => { const a = Rr() * 6.28; return [Math.cos(a) * 1.4, 1 + Rr() * 2, Math.sin(a) * 1.4]; } })).at(at('tronco', V3(0, -6, 0)), T - 0.9);
      }
      if (sq > 0.8) { ctx.shake(0.35); ctx.flash(at('tronco', V3(0, -8, -4)), 0.25); }
      if (T > 3.1 && T < 3.5) { ctx.shake(0.7); ctx.get('chao', () => FX.debris(20, 193, [[150, 110, 80], [120, 90, 64], [180, 140, 100]])).at(V3(0, 0.05, RZ - 0.5), T - 3.1); }
      return;
    }
    if (mode === 'investida') {
      const T = t % 3.2;
      const R = ctx.ref; const RZ = -8; R.place(0, RZ, 0);
      stance(r, true);
      // abaixa o ombro, pisa forte, corre que nem touro e trombou; poeira atrás
      const crouch = seq([[0, 0], [0.4, 1, 'out'], [0.6, 1]], T) * (1 - seq([[1.6, 0], [2.0, 1]], T));
      const run = seq([[0.55, 0], [1.25, 1, 'in']], T), back = seq([[2.2, 0], [2.9, 1, 'io']], T);
      obj.position.z += (RZ + 1.6) * run * (1 - back);
      r('tronco', 0.6 * crouch, -0.35 * crouch, 0); r('cabeca', -0.4 * crouch, 0.2 * crouch, 0);
      r('bracoD', 0.3 * crouch, 0, 0.4 * crouch); r('bracoE', -0.6 * crouch, 0, -0.2 * crouch);
      const ph = T * 6;
      if (T > 0.55 && T < 1.3) { r('pernaD', 0.8 * Math.sin(ph * Math.PI), 0, 0); r('pernaE', -0.8 * Math.sin(ph * Math.PI), 0, 0); r('canelaD', 0.6 * Math.max(0, Math.sin(ph * Math.PI + 1)), 0, 0); r('canelaE', 0.6 * Math.max(0, -Math.sin(ph * Math.PI + 1)), 0, 0); P.quadril.obj.position.y += 1.0 * Math.abs(Math.sin(ph * Math.PI)); }
      if (T > 0.55 && T < 1.8) ctx.get('rastro', () => DBFX.grains({ n: 70, seed: 197, palette: [[190, 150, 110], [150, 120, 90], [210, 180, 140]], size: [0.03, 0.07], drag: 2, grav: 2, life: 0.8, spread: 0.7, cone: Rr => [(Rr() - 0.5) * 1.5, 0.5 + Rr(), 1.5 + Rr() * 2] })).at(V3(0, 0.05, obj.position.z + 0.5), T - 0.55);
      if (T > 1.2) {
        const k = T - 1.25;
        const hp = V3(0, 1.3, RZ + 0.3);
        if (k > 0 && k < 0.08) { ctx.flash(hp, 1); ctx.get('impacto', () => DBFX.muzzle()).at(hp, ctx.camera, 1 - k / 0.08, 7, 1.4); }
        if (k > 0) ctx.shake(Math.max(0, 0.9 - k * 2));
        const fly = seq([[1.25, 0], [1.9, 1, 'out']], T);
        R.place(0, RZ - 3.5 * fly, 1.2 * Math.sin(Math.PI * fly)); R.lean(-1.4 * fly * (1 - seq([[2.6, 0], [3.2, 1]], T)));
      }
      return;
    }
  }

  function animYamu(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const at = (n, off) => { obj.updateMatrixWorld(true); return P[n].obj.localToWorld(off.clone()); };
    const breath = loop([[0, 0], [0.45, 1], [0.999, 0]], t / 2.6);
    if (mode === 'parado') {
      stance(r, false);
      // confere o frasco do aparelho, impaciente
      const look = seq([[0.5, 0], [0.6, 1, 'out'], [0.85, 1], [0.95, 0]], fr(t / 4));
      r('bracoD', -1.2 * look, 0.3 * look, 0); r('anteD', -0.9 * look, 0, 0); r('cabeca', 0.25 * look, 0.25 * look, 0);
      r('tronco', -0.03 * breath, 0, 0);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 1.1;
      for (const [s, off] of [['D', 0], ['E', 0.5]]) {
        r('perna' + s, 0.5 * Math.sin((ph + off) * Math.PI * 2), 0, 0); r('canela' + s, 0.4 * Math.max(0, Math.sin((ph + off) * Math.PI * 2 + 1.4)), 0, 0);
        r('braco' + s, -0.4 * Math.sin((ph + off) * Math.PI * 2), 0, 0); r('ante' + s, -0.3, 0, 0);
      }
      P.quadril.obj.position.y += 0.5 * Math.abs(Math.sin(ph * Math.PI * 2));
      return;
    }
    if (mode === 'sugar') {
      const T = t % 4.2;
      const R = ctx.ref; const RZ = -3.2; R.place(0, RZ, 0);
      stance(r, false);
      // avança rápido, crava a agulha no peito; o alvo dobra, a luz sobe pela agulha e enche o frasco; puxa e recua rindo
      const dash = seq([[0, 0], [0.45, 1, 'out']], T) * (1 - seq([[3.2, 0], [3.8, 1, 'io']], T));
      obj.position.z += (RZ + 1.35) * dash;
      const stab = seq([[0.35, 0], [0.5, 1, 'snap'], [2.9, 1], [3.1, 0]], T);
      r('bracoD', -1.5 * stab, -0.3 * stab, 0); r('anteD', 0.1 * stab, 0, 0); r('maoD', -0.2 * stab, 0, 0);
      r('bracoE', -0.6 * stab, 0, -0.5 * stab); r('anteE', -0.8 * stab, 0, 0);
      r('tronco', 0.15 * stab, 0, 0); r('pernaD', -0.4 * stab, 0, 0); r('canelaD', 0.4 * stab, 0, 0);
      const drain = seq([[0.6, 0], [2.8, 1, 'io']], T);
      const flask = at('aparelho', V3(0, 1.1, -1.8)), tip = at('aparelho', V3(0, 7, 0));
      if (T > 0.6 && T < 3.0) {
        ctx.get('fio', drainStream).at(V3(0, 1.2, RZ), tip, T, 1);
        ctx.get('frasco', () => kiBall([1.0, 0.85, 0.35], [1.0, 1.0, 0.85])).at(flask, 0.04 + 0.11 * drain, 1, T);
        ctx.get('luzF', () => DBFX.light(0xffe080)).at(flask, 1.5 * drain, 3);
      }
      if (T >= 3.0 && T < 4.2) ctx.get('frasco', () => kiBall([1.0, 0.85, 0.35], [1.0, 1.0, 0.85])).at(flask, 0.15, 1, T);
      // alvo: dobra pra frente, perde a cor (fica mais escuro) e cai de joelho
      const weak = seq([[0.5, 0], [0.7, 0.5, 'out'], [2.9, 1], [3.6, 1], [4.2, 0]], T);
      R.lean(0.5 * weak); R.place(0, RZ, -0.25 * Math.max(0, weak - 0.5) * 2);
      if (T > 0.5 && T < 0.6) { ctx.flash(tip, 0.6); ctx.shake(0.3); }
      return;
    }
    if (mode === 'rajada') {
      const T = t % 3.0;
      const R = ctx.ref; const RZ = -7; R.place(0, RZ, 0);
      stance(r, false);
      // empurra a mão esquerda aberta pra frente: bola vermelho-escura da marca Majin
      const aim = seq([[0, 0], [0.35, 1, 'out'], [1.6, 1], [2.0, 0, 'io']], T);
      r('bracoE', -1.55 * aim, 0.3 * aim, 0); r('maoE', -0.9 * aim, 0, 0); r('tronco', 0, -0.4 * aim, 0); r('quadril', 0, -0.2 * aim, 0);
      const palm = at('maoE', V3(0, 3, -1.2));
      const grow = seq([[0.3, 0], [0.85, 1, 'in']], T);
      const red = [[0.85, 0.1, 0.2], [1.0, 0.75, 0.75]];
      if (T > 0.3 && T < 0.9) ctx.get('bola', () => kiBall(...red)).at(palm, 0.05 + 0.2 * grow, 1, T);
      if (T >= 0.9 && T < 1.3) { const f = (T - 0.9) / 0.4; ctx.get('bola', () => kiBall(...red)).at(palm.clone().lerp(V3(0, 1.1, RZ + 0.2), f), 0.25, 1, T); ctx.get('rastro', () => DBFX.tracer([1, 0.2, 0.3])).at(palm, V3(0, 1.1, RZ + 0.2), f, 1.2); }
      if (T >= 1.3) {
        const k = (T - 1.3) / 1.2;
        ctx.get('boom', FX.volume).set(V3(0, 1.1, RZ), 0.9, Math.min(1, k), { fire: 0.7, seed: 9.9, dens: 10, rise: 0.5, smoke: [0.3, 0.18, 0.2] });
        if (k < 0.15) { ctx.flash(V3(0, 1.1, RZ), 1); ctx.shake(0.6); }
        const fly = seq([[1.3, 0], [2.0, 1, 'out']], T);
        R.place(0, RZ - 2 * fly, 0.6 * Math.sin(Math.PI * fly)); R.lean(-1.1 * fly * (1 - seq([[2.5, 0], [3.0, 1]], T)));
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const spo = [{ seed: 3, big: true, skin: [240, 206, 176], gi: [60, 44, 80], vest: [40, 34, 46], belt: [200, 170, 70], hair: false, ears: false },
               { seed: 9, big: true, skin: [222, 186, 156], gi: [44, 44, 70], vest: [34, 30, 40], belt: [170, 40, 40], hair: false, ears: false }];
  const yam = [{ seed: 5, big: false, skin: [236, 200, 170], gi: [60, 44, 80], vest: [40, 34, 46], belt: [200, 170, 70], hair: true, ears: true },
               { seed: 11, big: false, skin: [220, 180, 150], gi: [44, 44, 70], vest: [34, 30, 40], belt: [170, 40, 40], hair: true, ears: true }];
  const def = v => ({ def: rigDef(painters(v), v, false), low: rigDef(painters(v), v, true) });
  window.MOBS.push({
    id: 'spopovich', nome: 'Spopovich', bioma: 'Vilões do mundo', tier: 'Forte · tier 5',
    variants: spo.map((v, i) => ({ nome: ['Roupa roxa', 'Roupa azul'][i], v, ...def(v) })),
    randomVariant: () => { const v = { ...spo[Math.floor(Math.random() * 2)], seed: Math.floor(Math.random() * 1e5) }; return { nome: 'Sorteado', v, ...def(v) }; },
    anim: animSpo, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['abraco', 'Abraço Esmagador'], ['investida', 'Investida Majin']],
    cam: 7, lookY: 1.5, lookZ: { abraco: -1.8, investida: -3.6 }, camFar: { abraco: 7.5, investida: 11 }, camYaw: { abraco: -1.0, investida: -1.25 },
    info: {
      'Tamanho': '2,4 blocos, largão',
      'Vida': '180 (tier 5). Aguenta muito e não sente dor (stun pela metade).',
      'Jeito': 'Anda com o Yamu pelo Deserto do Diabo, de noite. Os dois caçam quem tem muita energia (Poder alto chama mais). Se o Yamu cai, ele fica furioso (mais rápido).',
      'Abraço Esmagador': 'Abre os braços (aviso), abraça, levanta e esmaga três vezes; depois joga no chão. Esmagar em pacotes (Escudo de tier). Bater nele 3 vezes rápido solta.',
      'Investida Majin': 'Abaixa o ombro e corre que nem touro até 8 blocos. Desviar de lado faz ele tropeçar e ficar atordoado.',
      'Solta': 'Pedaço da roupa com o M, Faixa Majin; raro: Alma do Spopovich.',
      'Variações': 'Cor da roupa e da faixa.',
    },
  });
  window.MOBS.push({
    id: 'yamu', nome: 'Yamu', bioma: 'Vilões do mundo', tier: 'Médio · tier 6',
    variants: yam.map((v, i) => ({ nome: ['Roupa roxa', 'Roupa azul'][i], v, ...def(v) })),
    randomVariant: () => { const v = { ...yam[Math.floor(Math.random() * 2)], seed: Math.floor(Math.random() * 1e5) }; return { nome: 'Sorteado', v, ...def(v) }; },
    anim: animYamu, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['sugar', 'Sugar Energia'], ['rajada', 'Rajada Majin']],
    cam: 5.5, lookY: 1.1, lookZ: { sugar: -1.6, rajada: -3.3 }, camFar: { sugar: 6.5, rajada: 9.5 }, camYaw: { sugar: -1.1, rajada: -1.25 },
    info: {
      'Tamanho': '1,8 bloco',
      'Vida': '90 (tier 6)',
      'Jeito': 'Sempre perto do Spopovich, atrás dele. Espera o Spopovich segurar e entra pra sugar.',
      'Sugar Energia': 'Avança e crava a agulha: o fio de luz sobe e enche o frasco. Rouba estamina e ki enquanto dura. Quebrar o frasco (acertar a mão dele) devolve tudo.',
      'Rajada Majin': 'Bola vermelho-escura da mão aberta, carrega meio segundo. Dano em pacotes no jogo.',
      'Solta': 'Aparelho de sugar energia (cheio: guarda ki pra usar depois), frasco vazio; raro: Alma do Yamu.',
      'Variações': 'Cor da roupa e da faixa.',
    },
  });
})();
