// Yakon (nave do Babidi). Grandão (2,6 blocos), corcunda, pele preta-azulada com listras douradas em zigue-zague,
// cabeção com dobras no crânio, marca Majin na testa, olhos amarelos e uma bocarra cheia de dente.
// Golpes: Escuridão (apaga a luz em volta; no escuro só as listras e os olhos dele brilham; pula e rasga)
// e Devorar Luz (abre a boca e suga a luz do lugar e a energia do alvo: incha, brilha e se cura).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const V3 = (x, y, z) => new THREE.Vector3(x, y, z);
  const GOLD = [236, 192, 58];

  function variant(seed) {
    const R = FX.rnd(seed);
    return { seed, skin: [[30, 32, 48], [36, 30, 50], [26, 36, 46]][Math.floor(R() * 3)], zig: 5.0 + R() * 1.5, off: R() * 4, tooth: R() < 0.5 ? 'serra' : 'presas' };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.8, hi = 1.3) => cel(base, 0.62 - 0.38 * q.ny + 0.12 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0), lo, hi);
    // listras douradas: faixas horizontais em zigue-zague, cada uma com período e fase próprios (nada repetido)
    const zig = q => {
      const band = Math.floor((q.ay + v.off) / 5);
      const per = v.zig + 0.6 * (n3(band, 0, 0, 90 + S) - 0.5), amp = 1.8 + 0.8 * n3(band, 1, 0, 91 + S);
      const tri = Math.abs(fr((q.ax + q.az * 0.7) / per + band * 0.37) - 0.5) * 2;
      const y = fr((q.ay + v.off) / 5) * 5;
      return Math.abs(y - (1.2 + amp * tri)) < 0.6;
    };
    const P = {};
    P.skin = q => zig(q) && q.f !== 'bottom' ? GOLD : shade(v.skin, q, 1);
    P.limb = q => zig(q) && q.f !== 'top' && q.f !== 'bottom' ? GOLD : shade(v.skin, q, 2);
    P.belt = q => { let c = shade([82, 58, 44], q, 3, 0.85, 1.15); if (q.f === 'north' && Math.abs(q.px - q.b.w / 2) < 1 && q.py < 1.4) c = [200, 170, 90]; return c; };
    P.cloth = q => { if (q.f === 'top' || q.f === 'bottom') return null; const w = q.px / q.b.w; if (q.py > q.b.h - 1.2 * Math.abs(Math.sin(w * 9.4 + S))) return null; return shade([70, 40, 54], q, 4, 0.85, 1.15); };
    P.claw = q => cel([226, 214, 186], 0.4 + 0.6 * (1 - q.ny), 0.8, 1.1);
    P.ridge = q => zig(q) && q.f === 'top' ? GOLD : shade(mix(v.skin, [70, 70, 96], 0.25), q, 5);
    P.head = q => {
      let c = shade(v.skin, q, 6);
      if (q.f !== 'north') return zig(q) && q.f !== 'top' ? GOLD : c;
      // rosto 9x6 em pixel de Minecraft: marca Majin na testa, sobrancelha pesada, olho amarelo com fenda,
      // e a fileira de dentes de cima
      const x = Math.min(8, Math.floor(q.px)), y = Math.min(5, Math.floor(q.py)), m = Math.min(x, 8 - x);
      const M = [[2, 6], [2, 3, 5, 6], [2, 4, 6]];
      if (y <= 2 && M[y].includes(x)) c = [170, 30, 40];
      if (y === 3 && m >= 1 && m <= 2) c = mul(v.skin, 0.5);
      if (y === 4 && (m === 1 || m === 2)) c = m === 2 ? [255, 236, 90] : [240, 190, 50];
      if (y === 5) c = v.tooth === 'serra' ? (x % 2 ? [244, 240, 226] : [210, 200, 180]) : (m === 1 || m === 3 ? [248, 244, 230] : [70, 20, 26]);
      return c;
    };
    P.jaw = q => {
      if (q.f === 'top') { const fz = q.pz; return fz < 1.2 ? (Math.floor(q.px) % 2 ? [244, 240, 226] : [200, 190, 170]) : shade([120, 30, 40], q, 7, 0.8, 1.1); }
      if (q.f === 'north' && q.py < 1) return Math.floor(q.px) % 2 ? [244, 240, 226] : [70, 20, 26];
      return zig(q) ? GOLD : shade(v.skin, q, 8);
    };
    P.ear = q => shade(mix(v.skin, [60, 50, 80], 0.3), q, 9);
    return P;
  }

  function rigDef(P, v, low) {
    const claws = low ? [] : [-1, 0, 1].map(i => ({ x: -0.5 + i * 1.2, y: 3.2, z: -1.4, w: 0.8, h: 1.8, d: 0.8, p: P.claw }));
    const arm = s => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * 7.2, -9.5, -0.5], boxes: [{ x: -2.1, y: -1.5, z: -2.1, w: 4.2, h: 8, d: 4.2, p: P.limb }],
      kids: [{
        name: s < 0 ? 'anteD' : 'anteE', pivot: [0, 6.4, 0], boxes: [{ x: -1.9, y: 0, z: -1.9, w: 3.8, h: 7, d: 3.8, p: P.limb }],
        kids: [{ name: s < 0 ? 'maoD' : 'maoE', pivot: [0, 7, 0], boxes: [{ x: -2.1, y: -0.5, z: -2.1, w: 4.2, h: 3.8, d: 4.2, p: P.skin }, ...claws] }],
      }],
    });
    const leg = s => ({
      name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * 3, 12, 0], boxes: [{ x: -2.4, y: 0, z: -2.4, w: 4.8, h: 6, d: 4.8, p: P.limb }],
      kids: [{ name: s < 0 ? 'canelaD' : 'canelaE', pivot: [0, 6, 0], boxes: [{ x: -2.2, y: 0, z: -2.2, w: 4.4, h: 5, d: 4.4, p: P.limb }, { x: -2.5, y: 5, z: -3.6, w: 5, h: 1, d: 6, p: P.skin }] }],
    });
    const ridges = low ? [{ x: -3.8, y: -9.2, z: -3.6, w: 7.6, h: 1.4, d: 7, p: P.ridge }] : [-3.4, -0.7, 2.0].map((x, i) => ({ x, y: -9.4 + (i === 1 ? -0.5 : 0), z: -3.8, w: 1.4, h: 2, d: 7.6, p: P.ridge }));
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'quadril', pivot: [0, 12, 0], boxes: [{ x: -5, y: -3, z: -2.8, w: 10, h: 3.4, d: 5.6, p: P.belt }, ...(low ? [] : [{ x: -3, y: 0.4, z: -3, w: 6, h: 4.4, d: 0.4, p: P.cloth }])], kids: [{
          name: 'tronco', pivot: [0, -2.6, 0], rot: [0.28, 0, 0], boxes: [{ x: -6.2, y: -10.5, z: -3.2, w: 12.4, h: 10.5, d: 6.4, p: P.skin }, ...(low ? [] : [{ x: -5, y: -9.5, z: 2.6, w: 10, h: 5, d: 1.8, p: P.skin }])], kids: [
            {
              name: 'cabeca', pivot: [0, -10.2, -1.6], rot: [-0.2, 0, 0], boxes: [{ x: -4.5, y: -8, z: -4, w: 9, h: 6, d: 8, p: P.head }, ...ridges, { x: -6, y: -6.4, z: -0.6, w: 1.6, h: 2.6, d: 1.4, p: P.ear }, { x: 4.4, y: -6.4, z: -0.6, w: 1.6, h: 2.6, d: 1.4, p: P.ear }],
              kids: [{ name: 'mandibula', pivot: [0, -2, 2.6], boxes: [{ x: -4.2, y: 0, z: -7.2, w: 8.4, h: 2.8, d: 7.2, p: P.jaw }] }],
            },
            arm(-1), arm(1),
          ],
        }],
      }, leg(-1), leg(1)],
    };
  }

  function stance(r) {
    r('pernaD', -0.2, 0.2, 0.1); r('canelaD', 0.35, 0, 0); r('pernaE', 0.15, -0.2, -0.1); r('canelaE', 0.3, 0, 0);
    r('bracoD', -0.15, 0, 0.18); r('anteD', -0.35, 0, 0); r('bracoE', -0.1, 0, -0.18); r('anteE', -0.35, 0, 0);
  }

  // Cópia do corpo que desenha só o dourado (listras e olhos) brilhando por cima; copia a pose do original a cada quadro.
  function glowSkin(src) {
    const g = src.clone(true);
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide,
      uniforms: { map: { value: null }, uK: { value: 0 }, uT: { value: 0 } },
      vertexShader: 'varying vec2 vU; varying vec3 vW; void main(){ vU=uv; vW=(modelMatrix*vec4(position,1.0)).xyz; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: `uniform sampler2D map; uniform float uK, uT; varying vec2 vU; varying vec3 vW;
        void main(){ vec4 c = texture2D(map, vU);
          float gold = step(0.7, c.r) * step(0.5, c.g) * step(c.b, 0.4);
          if (gold < 0.5 || c.a < 0.5) discard;
          float pulse = 0.75 + 0.25 * sin(uT * 7.0 - vW.y * 5.0);
          gl_FragColor = vec4(vec3(1.0, 0.82, 0.3) * uK * pulse, 1.0); }`,
    });
    let first = true;
    g.traverse(o => { if (o.material) { if (first) { mat.uniforms.map.value = o.material.map; first = false; } o.material = mat; o.renderOrder = 11; } });
    const A = [], B = [];
    src.traverse(o => A.push(o)); g.traverse(o => B.push(o));
    g.at = (k, t) => {
      g.visible = k > 0.01;
      for (let i = 0; i < A.length; i++) { B[i].position.copy(A[i].position); B[i].rotation.copy(A[i].rotation); B[i].scale.copy(A[i].scale); }
      mat.uniforms.uK.value = k; mat.uniforms.uT.value = t;
    };
    return g;
  }
  // Escuro em volta da câmera: uma esfera preta com fumaça mexendo, desenhada por cima da cena (as listras vêm depois)
  function darkness(camera) {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, depthTest: false, side: THREE.BackSide,
      uniforms: { uK: { value: 0 }, uT: { value: 0 } },
      vertexShader: 'varying vec3 vP; void main(){ vP=position; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: `varying vec3 vP; uniform float uK, uT;
        float h(vec3 p){ p=fract(p*0.3183+vec3(.71,.11,.42)); p*=17.0; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }
        float n(vec3 x){ vec3 i=floor(x), f=fract(x); f=f*f*(3.0-2.0*f);
          return mix(mix(mix(h(i),h(i+vec3(1,0,0)),f.x),mix(h(i+vec3(0,1,0)),h(i+vec3(1,1,0)),f.x),f.y),
                     mix(mix(h(i+vec3(0,0,1)),h(i+vec3(1,0,1)),f.x),mix(h(i+vec3(0,1,1)),h(i+vec3(1,1,1)),f.x),f.y),f.z); }
        void main(){ vec3 d = normalize(vP);
          float s = n(d * 3.0 + vec3(0.0, uT * 0.4, uT * 0.2)) * 0.6 + n(d * 7.0 - vec3(uT * 0.5)) * 0.4;
          vec3 c = mix(vec3(0.015, 0.012, 0.03), vec3(0.08, 0.06, 0.12), s);
          gl_FragColor = vec4(c, clamp(uK * (0.86 + 0.14 * s), 0.0, 0.97)); }`,
    });
    const m = new THREE.Mesh(new THREE.SphereGeometry(1.5, 24, 16), mat);
    m.frustumCulled = false; m.renderOrder = 10;
    m.at = (k, t) => { m.visible = k > 0.01; m.position.copy(camera.position); mat.uniforms.uK.value = k; mat.uniforms.uT.value = t; };
    return m;
  }
  // Luz sugada: cada grão nasce num ponto diferente em volta (e do alvo) e entra na boca em espiral
  function suck(n, seed) {
    const R = FX.rnd(seed);
    const pos = new Float32Array(n * 3), col = new Float32Array(n * 3), it = [];
    for (let i = 0; i < n; i++) {
      const fromRef = R() < 0.35;
      const a = R() * 6.28, b = 0.3 + R() * 2.2, rr = 2.2 + R() * 2.6;
      it.push({ fromRef, a, b, rr, ph: R(), sp: 0.7 + R() * 0.8, sw: (R() - 0.5) * 3, off: [R() - 0.5, R() * 1.6, R() - 0.5] });
      const c = R() < 0.3 ? [1, 0.97, 0.85] : [1, 0.78 + R() * 0.15, 0.3];
      col.set(c, i * 3);
    }
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(pos, 3)); geo.setAttribute('color', new THREE.BufferAttribute(col, 3));
    const al = new Float32Array(n); geo.setAttribute('al', new THREE.BufferAttribute(al, 1));
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, vertexColors: true, blending: THREE.AdditiveBlending,
      vertexShader: 'attribute float al; varying vec3 vC; varying float vA; void main(){ vC=color; vA=al; vec4 mv=modelViewMatrix*vec4(position,1.0); gl_PointSize = 0.075*420.0/-mv.z*(0.5+al); gl_Position=projectionMatrix*mv; }',
      fragmentShader: 'varying vec3 vC; varying float vA; void main(){ vec2 d=gl_PointCoord-0.5; if(max(abs(d.x),abs(d.y))>0.5) discard; gl_FragColor=vec4(vC*vA, vA); }',
    });
    const pts = new THREE.Points(geo, mat); pts.frustumCulled = false; pts.renderOrder = 12;
    pts.at = (mouth, ref, t, k) => {
      pts.visible = k > 0.01;
      for (let i = 0; i < n; i++) {
        const p = it[i], f = fr(t * p.sp * 0.7 + p.ph), e = f * f;
        let sx, sy, sz;
        if (p.fromRef) { sx = ref.x + p.off[0]; sy = ref.y + p.off[1]; sz = ref.z + p.off[2]; }
        else { sx = mouth.x + Math.cos(p.a) * p.rr; sy = p.b; sz = mouth.z + Math.sin(p.a) * p.rr; }
        // espiral que aperta chegando na boca
        const sp = p.sw * (1 - e) * Math.sin(e * 6.0);
        const x = sx + (mouth.x - sx) * e + sp * 0.3, y = sy + (mouth.y - sy) * e + Math.sin(e * 9 + i) * 0.12 * (1 - e), z = sz + (mouth.z - sz) * e - sp * 0.3;
        pos.set([x, y, z], i * 3);
        al[i] = k * Math.min(1, f * 5) * (1 - Math.max(0, (f - 0.92) / 0.08));
      }
      geo.attributes.position.needsUpdate = true; geo.attributes.al.needsUpdate = true;
    };
    return pts;
  }

  const K = 1.3, Y0 = 1.5 * K;
  function anim(P, t, mode, a, obj, e, ctx) {
    obj.scale.set(-K / 16, -K / 16, K / 16); obj.position.y += Y0 - 1.5 + 0.09;
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const at = (n, off) => { obj.updateMatrixWorld(true); return P[n].obj.localToWorld(off.clone()); };
    const breath = loop([[0, 0], [0.45, 1], [0.999, 0]], t / 3.4);

    if (mode === 'parado') {
      stance(r);
      // corcunda respirando pesado, boca entreaberta mastigando, cabeça farejando de um lado pro outro
      r('tronco', 0.06 * breath, 0, 0); r('cabeca', -0.05 * breath, 0.35 * Math.sin(t * 0.7), 0.06 * Math.sin(t * 0.9));
      r('mandibula', 0.12 + 0.1 * Math.max(0, Math.sin(t * 5.5)), 0, 0);
      r('bracoD', 0.08 * breath, 0, 0); r('bracoE', 0.08 * breath, 0, 0);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 0.8;
      for (const [s, off] of [['D', 0], ['E', 0.5]]) {
        r('perna' + s, 0.45 * Math.sin((ph + off) * Math.PI * 2), 0, 0); r('canela' + s, 0.45 * Math.max(0, Math.sin((ph + off) * Math.PI * 2 + 1.4)), 0, 0);
        r('braco' + s, -0.35 * Math.sin((ph + off) * Math.PI * 2), 0, 0); r('ante' + s, -0.3, 0, 0);
      }
      P.quadril.obj.position.y += 0.8 * Math.abs(Math.sin(ph * Math.PI * 2));
      r('tronco', 0, 0.12 * Math.sin(ph * Math.PI * 2), 0); r('mandibula', 0.15, 0, 0);
      return;
    }

    if (mode === 'escuro') {
      const T = t % 4.4;
      const R = ctx.ref; const RZ = -5.5; R.place(0, RZ, 0);
      stance(r);
      // 0-1.0: abre os braços e ruge, a luz morre em volta (só as listras e os olhos dele ficam acesos)
      // 1.5: some no escuro e pula; 1.9 e 2.15: dois rasgos com as garras; 2.8: volta pro lugar; 3.6: a luz volta
      const dk = seq([[0.2, 0], [1.0, 1, 'in'], [3.5, 1], [4.1, 0]], T);
      const roar = seq([[0, 0], [0.4, 1, 'out'], [1.2, 1], [1.45, 0]], T);
      r('bracoD', -0.4 * roar, 0, 1.0 * roar); r('bracoE', -0.4 * roar, 0, -1.0 * roar); r('anteD', -0.6 * roar, 0, 0); r('anteE', -0.6 * roar, 0, 0);
      r('tronco', -0.35 * roar, 0, 0); r('cabeca', -0.4 * roar, 0, 0); r('mandibula', 0.9 * roar, 0, 0);
      const jump = seq([[1.45, 0], [1.85, 1, 'io']], T), home = seq([[2.6, 0], [3.0, 1, 'io']], T);
      const zz = (RZ + 1.6) * jump * (1 - home);
      obj.position.z += zz; obj.position.y += 1.4 * Math.sin(Math.PI * jump) * (jump < 1 ? 1 : 0) + 1.2 * Math.sin(Math.PI * home) * (home > 0 ? 1 : 0);
      const s1 = seq([[1.8, 0], [1.95, 1, 'snap'], [2.1, 1]], T), s2 = seq([[2.05, 0], [2.2, 1, 'snap'], [2.4, 1]], T);
      const up = seq([[1.6, 0], [1.8, 1, 'out']], T) * (1 - home);
      r('bracoD', -2.6 * up + 2.0 * s1, 0, 0.4 * up); r('bracoE', -2.6 * up + 2.0 * s2, 0, -0.4 * up);
      r('tronco', 0.4 * (s1 + s2) * 0.5 * (1 - home), 0, 0.15 * (s1 - s2));
      r('pernaD', -0.5 * jump * (1 - home), 0, 0); r('pernaE', 0.3 * jump * (1 - home), 0, 0);
      ctx.get('escuro', () => darkness(ctx.camera)).at(dk * 0.93, T);
      ctx.get('brilho', () => glowSkin(obj)).at(dk * 1.2, T);
      const eyeL = at('cabeca', V3(-2.5, -3.5, -4.2)), eyeR = at('cabeca', V3(2.5, -3.5, -4.2));
      const eyeGlow = () => { const g = DBFX.glow([1.0, 0.8, 0.25]); g.renderOrder = 12; return g; };
      ctx.get('olhoD', eyeGlow).at(eyeL, 0.18, dk, T);
      ctx.get('olhoE', eyeGlow).at(eyeR, 0.18, dk, T + 1);
      for (const [k, st] of [['c1', 1.9], ['c2', 2.15]]) {
        const age = (T - st) / 0.5;
        if (age > 0 && age < 1) { ctx.get(k, () => { const s = clawMesh(); s.traverse(o => { o.renderOrder = 12; }); return s; }).at(V3(k === 'c1' ? -0.15 : 0.15, 1.1, RZ + 0.35), age); }
      }
      const hit = Math.max(seq([[1.95, 0], [2.0, 1], [2.3, 0]], T), seq([[2.2, 0], [2.25, 1], [2.55, 0]], T));
      ctx.shake(0.5 * hit);
      R.lean(-0.5 * seq([[1.95, 0], [2.1, 1], [3.2, 1], [3.8, 0]], T)); R.spin(0.3 * seq([[2.2, 0], [2.35, 1], [3.4, 1], [3.9, 0]], T));
      return;
    }

    if (mode === 'devorar') {
      const T = t % 4.2;
      const R = ctx.ref; const RZ = -5; R.place(0, RZ, 0);
      stance(r);
      // abre a bocarra pra cima e suga: a luz de todo lado e a energia do alvo entram em espiral; ele incha e
      // as listras acendem; 2.5: fecha a boca num gole, arrota um anel dourado e se cura
      const open = seq([[0, 0], [0.4, 1, 'out'], [2.4, 1], [2.55, 0, 'snap']], T);
      const eat = seq([[0.4, 0], [2.4, 1, 'in'], [3.2, 1], [4.0, 0, 'io']], T);
      const gulp = seq([[2.45, 0], [2.6, 1, 'snap'], [2.9, 0]], T);
      r('tronco', -0.3 * open + 0.25 * gulp, 0, 0); r('cabeca', -0.55 * open + 0.4 * gulp, 0, 0); r('mandibula', 1.15 * open, 0, 0);
      r('bracoD', -0.3 * open, 0, 0.7 * open); r('bracoE', -0.3 * open, 0, -0.7 * open); r('anteD', -1.0 * open, 0, 0); r('anteE', -1.0 * open, 0, 0);
      const sw = 1 + 0.16 * eat + 0.05 * gulp;
      P.tronco.obj.scale.set(sw, 1 + 0.06 * eat, sw); P.cabeca.obj.scale.set(1 / sw * (1 + 0.08 * eat), 1, 1 / sw * (1 + 0.08 * eat));
      const mouth = at('cabeca', V3(0, -2, -5));
      const refC = V3(0, 1.1, RZ);
      // ele come a luz do lugar: escurece em volta enquanto suga
      ctx.get('escuro', () => darkness(ctx.camera)).at(0.6 * open * eat + 0.2 * open, T);
      ctx.get('luz', () => suck(260, 61)).at(mouth, refC, T, open);
      ctx.get('brilho', () => glowSkin(obj)).at(0.9 * eat + 0.6 * gulp, T);
      ctx.get('boca', () => DBFX.glow([1.0, 0.85, 0.4])).at(mouth, 0.2 + 0.25 * open * eat, 0.6 * open, T);
      ctx.get('luzBoca', () => DBFX.light(0xffc860)).at(mouth, 2.5 * open, 5);
      R.lean(0.35 * open * (1 - seq([[2.6, 0], [3.4, 1]], T)));
      if (T > 2.5) {
        const k = (T - 2.5) / 1.2;
        ctx.roarRing(V3(0, 0.03, 0), Math.min(1, k * 1.2));
        ctx.get('cura', () => DBFX.grains({ n: 46, seed: 73, palette: [[140, 255, 150], [220, 255, 200], [90, 230, 120], [255, 230, 120]], size: [0.02, 0.045], drag: 1.2, grav: -1.4, life: 1.1, spread: 0.6, add: true, cone: Rr => { const a = Rr() * 6.28, rr = 0.6 + Rr() * 0.6; return [Math.cos(a) * rr, 0.6 + Rr(), Math.sin(a) * rr]; } })).at(V3(0, 0.3, 0), T - 2.5);
        if (k < 0.15) ctx.flash(mouth, 1 - k / 0.15);
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(5), variant(13)];
  fixed[0].tooth = 'serra'; fixed[1].tooth = 'presas'; fixed[1].skin = [36, 30, 50];
  const def = v => ({ def: rigDef(painters(v), v, false), low: rigDef(painters(v), v, true) });
  window.MOBS.push({
    id: 'yakon', nome: 'Yakon', bioma: 'Namek e estruturas', tier: 'Forte · tier 6 (guarda da nave)',
    variants: fixed.map((v, i) => ({ nome: ['Dente de serra', 'Presas'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['escuro', 'Escuridão'], ['devorar', 'Devorar Luz']],
    cam: 8, lookY: 1.5, lookZ: { escuro: -2.6, devorar: -2.4 }, camFar: { escuro: 10, devorar: 10 }, camYaw: { escuro: -1.2, devorar: -1.15 },
    info: {
      'Tamanho': '2,6 blocos, corcunda',
      'Vida': '220 (guarda de andar, tier 6). Mini-chefe da nave do Babidi.',
      'Jeito': 'Fica parado no andar dele farejando. Quando você entra, apaga a luz e caça no escuro. Tocha e ki claro atraem ele.',
      'Escuridão': 'Apaga a luz em volta (cegueira leve). No escuro só as listras douradas e os olhos dele aparecem: dá pra ler o pulo. Pula e dá dois rasgos.',
      'Devorar Luz': 'Abre a boca e suga a luz do lugar e a sua energia (estamina e ki). Incha, brilha e se cura. Jogar ki forte DEMAIS enquanto ele suga faz ele estourar (como no anime com o Goku SSJ).',
      'Solta': 'Dente do Yakon, couro listrado; raro: Alma do Yakon.',
      'Variações': 'Tom da pele, desenho das listras e dentes (serra ou presas) sorteados.',
    },
  });
})();
