// Tao Pai Pai (vagando pelas Terras do Dragão). O assassino contratado que todo mundo reconhece e que a gente derruba
// rápido: alto e magro, roupa chinesa rosa até o joelho, trança comprida, bigodinho fino e cara de deboche.
// Golpes: Dodonpa (aponta o dedo, junta uma faísca amarela e solta um raio fino que fura) e Pilar Voador (arranca
// um pilar de pedra do chão, arremessa, pula em cima, voa nele e salta numa voadora).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const V3 = (x, y, z) => new THREE.Vector3(x, y, z);

  function variant(seed) {
    const R = FX.rnd(seed);
    return { seed, robe: [[236, 150, 160], [226, 136, 150], [240, 170, 176]][Math.floor(R() * 3)], cyborg: R() < 0.3 };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.84, hi = 1.12) => cel(base, 0.62 - 0.38 * q.ny + 0.1 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0), lo, hi);
    const SK = [240, 214, 190];
    const P = {};
    P.skin = q => shade(SK, q, 1);
    // roupa: rosa com gola alta, botões de nó na frente e o sinal de "matar" nas costas (traços pretos, desenhados por posição)
    P.robe = q => {
      let c = shade(v.robe, q, 2);
      if (q.f === 'north' && Math.abs(q.px - q.b.w * 0.62) < 0.35 && fr(q.py / 2.2) < 0.35) c = [60, 40, 44];
      if (q.f === 'north' && Math.abs(q.px - (q.b.w * 0.62 - q.py * 0.0)) < 0.18) c = mul(c, 0.85);
      if (q.f === 'south') {
        const u = q.px / q.b.w, w = q.py / q.b.h;
        const strokes = [[0.3, 0.18, 0.7, 0.18], [0.5, 0.1, 0.5, 0.7], [0.25, 0.42, 0.75, 0.42], [0.3, 0.7, 0.5, 0.45], [0.7, 0.7, 0.52, 0.5]];
        for (const [x0, y0, x1, y1] of strokes) {
          const dx = x1 - x0, dy = y1 - y0, l2 = dx * dx + dy * dy, k = Math.max(0, Math.min(1, ((u - x0) * dx + (w - y0) * dy) / l2));
          if (Math.hypot(u - x0 - dx * k, w - y0 - dy * k) < 0.05) c = [30, 20, 24];
        }
      }
      return c;
    };
    P.skirt = q => { if (q.f === 'top' || q.f === 'bottom') return null; let c = shade(v.robe, q, 3); if (q.py > q.b.h - 0.6) c = mul(c, 0.8); return c; };
    P.pants = q => shade([240, 236, 230], q, 4);
    P.shoe = q => shade([34, 30, 32], q, 5);
    P.hair = q => { let c = shade([30, 28, 32], q, 6, 0.8, 1.3); if (fr(q.ay * 0.9 + q.ax * 0.3) < 0.2) c = mul(c, 0.7); return c; };
    P.metal = q => { let c = shade([170, 176, 184], q, 7, 0.8, 1.3); if (fr(q.ay * 0.7) < 0.15) c = mul(c, 0.7); return c; };
    P.head = q => {
      let c = shade(SK, q, 8, 0.88, 1.06);
      if (q.f === 'top' || (q.f !== 'north' && q.py < 2.2) || (q.f === 'south' && q.py < 5)) c = shade([30, 28, 32], q, 9, 0.8, 1.3);
      if (v.cyborg && q.f === 'west' && q.py > 1.5 && q.py < 5) c = shade([170, 176, 184], q, 10, 0.8, 1.3);
      if (q.f !== 'north') return c;
      // rosto 7x7: franja curta, sobrancelha fina subindo, olho estreito, bigodinho fino caindo dos lados
      const x = Math.min(6, Math.floor(q.px)), y = Math.min(6, Math.floor(q.py)), m = Math.min(x, 6 - x);
      if (y === 0) c = [30, 28, 32];
      if (y === 2 && m === 1) c = [40, 30, 30];
      if (y === 3 && (m === 1 || m === 2)) c = m === 2 ? [30, 24, 24] : [236, 232, 226];
      if (y === 5 && m >= 1 && m <= 2) c = [40, 30, 30];
      if (y === 6 && m === 1) c = [40, 30, 30];
      if (y === 5 && m === 3) c = mul(SK, 0.75);
      if (v.cyborg && x === 0 && y >= 2 && y <= 4) c = [200, 40, 40];
      return c;
    };
    return P;
  }

  function rigDef(P, v, low) {
    const w = 7.2, L = 1.6;
    const arm = s => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * (w / 2 + 1.4), -9 - L, 0], boxes: [{ x: -1.4, y: -1, z: -1.4, w: 2.8, h: 5.6, d: 2.8, p: P.robe }],
      kids: [{
        name: s < 0 ? 'anteD' : 'anteE', pivot: [0, 4.6, 0], boxes: [{ x: -1.5, y: 0, z: -1.5, w: 3, h: 3.6, d: 3, p: P.robe }, { x: -1.1, y: 3.4, z: -1.1, w: 2.2, h: 1.2, d: 2.2, p: v.cyborg && s > 0 ? P.metal : P.skin }],
        kids: [{
          name: s < 0 ? 'maoD' : 'maoE', pivot: [0, 4.6, 0], boxes: [{ x: -1.2, y: -0.2, z: -1.2, w: 2.4, h: 2.2, d: 2.4, p: v.cyborg && s > 0 ? P.metal : P.skin }],
          kids: s < 0 ? [{ name: 'dedo', pivot: [0, 1.9, -0.6], boxes: [{ x: -0.35, y: 0, z: -0.35, w: 0.7, h: 2, d: 0.7, p: P.skin }] }] : [],
        }],
      }],
    });
    const leg = s => ({
      name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * 1.8, 12 - L, 0], boxes: [{ x: -1.7, y: 0, z: -1.7, w: 3.4, h: 6.8, d: 3.4, p: P.pants }],
      kids: [{ name: s < 0 ? 'canelaD' : 'canelaE', pivot: [0, 6.8, 0], boxes: [{ x: -1.6, y: 0, z: -1.6, w: 3.2, h: 5.4, d: 3.2, p: P.pants }, { x: -1.8, y: 5.4, z: -2.6, w: 3.6, h: 1.4, d: 4.4, p: P.shoe }] }],
    });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'quadril', pivot: [0, 12 - L, 0], boxes: [{ x: -w / 2, y: -2.4, z: -2, w, h: 2.6, d: 4, p: P.robe }],
        kids: [
          // aba da roupa na frente e atrás (balança com as pernas)
          { name: 'abaF', pivot: [0, 0, -2.1], boxes: [{ x: -w / 2 + 0.4, y: 0, z: -0.3, w: w - 0.8, h: 6.6, d: 0.6, p: P.skirt }] },
          { name: 'abaT', pivot: [0, 0, 2.1], boxes: [{ x: -w / 2 + 0.4, y: 0, z: -0.3, w: w - 0.8, h: 6.6, d: 0.6, p: P.skirt }] },
          {
            name: 'tronco', pivot: [0, -2.2, 0], boxes: [{ x: -w / 2, y: -10 - L, z: -2, w, h: 10 + L, d: 4, p: P.robe }, { x: -2.2, y: -11.8 - L, z: -2.2, w: 4.4, h: 1.6, d: 4.4, p: P.robe }], kids: [
              {
                name: 'cabeca', pivot: [0, -11.4 - L, 0], boxes: [{ x: -3.5, y: -7, z: -3.5, w: 7, h: 7, d: 7, p: P.head }, { x: -3.7, y: -7.4, z: -3.7, w: 7.4, h: 1.6, d: 7.4, p: P.hair }],
                kids: low ? [] : [{
                  name: 'tranca1', pivot: [0, -3.6, 3.4], rot: [0.25, 0, 0], boxes: [{ x: -0.9, y: 0, z: -0.9, w: 1.8, h: 6, d: 1.8, p: P.hair }],
                  kids: [{ name: 'tranca2', pivot: [0, 6, 0], boxes: [{ x: -0.75, y: 0, z: -0.75, w: 1.5, h: 6, d: 1.5, p: P.hair }, { x: -0.5, y: 6, z: -0.5, w: 1, h: 1.6, d: 1, p: P.hair }] }],
                }],
              },
              arm(-1), arm(1),
            ],
          },
        ],
      }, leg(-1), leg(1)],
    };
  }

  function stance(r) {
    r('pernaD', -0.1, 0.25, 0.08); r('pernaE', 0.1, -0.15, -0.08);
    // mãos pra trás, de nariz empinado
    r('bracoD', 0.35, 0, 0.1); r('anteD', -0.6, 0, 0); r('bracoE', 0.35, 0, -0.1); r('anteE', -0.6, 0, 0);
    r('cabeca', -0.12, 0, 0);
  }
  function beam(c1) {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, side: THREE.DoubleSide,
      uniforms: { uK: { value: 1 }, uT: { value: 0 }, uL: { value: 1 }, uC: { value: new THREE.Color(...c1) } },
      vertexShader: 'varying vec3 vN; varying vec3 vV; varying vec2 vU; void main(){ vN=normalize(normalMatrix*normal); vec4 mv=modelViewMatrix*vec4(position,1.0); vV=normalize(-mv.xyz); vU=uv; gl_Position=projectionMatrix*mv; }',
      fragmentShader: `varying vec3 vN; varying vec3 vV; varying vec2 vU; uniform float uK, uT, uL; uniform vec3 uC;
        float h(vec2 p){ return fract(sin(dot(p, vec2(41.3, 289.1)))*43758.5); }
        float n(vec2 x){ vec2 i=floor(x), f=fract(x); f=f*f*(3.0-2.0*f); return mix(mix(h(i),h(i+vec2(1,0)),f.x),mix(h(i+vec2(0,1)),h(i+vec2(1,1)),f.x),f.y); }
        void main(){ float face = abs(dot(vN, vV));
          float w = n(vec2(vU.x * 8.0, vU.y * uL * 4.0 - uT * 30.0));
          vec3 c = mix(uC * 0.8, uC, smoothstep(0.1, 0.5, face));
          c = mix(c, vec3(1.0, 1.0, 0.92), smoothstep(0.45, 0.9, face + 0.2 * (w - 0.5)));
          gl_FragColor = vec4(c, smoothstep(0.0, 0.3, face) * uK); }`,
    });
    const g = new THREE.CylinderGeometry(1, 1, 1, 16, 1, true); g.rotateX(Math.PI / 2);
    const m = new THREE.Mesh(g, mat); m.frustumCulled = false;
    m.at = (a, b, r, k, t) => { m.visible = k > 0.01; const L = b.distanceTo(a); m.position.copy(a).add(b).multiplyScalar(0.5); m.lookAt(b); m.scale.set(r, r, L); mat.uniforms.uK.value = k; mat.uniforms.uT.value = t; mat.uniforms.uL.value = L; };
    return m;
  }
  // pilar de pedra: textura pintada por posição (veios, lascas), base com terra grudada
  function pillar() {
    const cv = document.createElement('canvas'); cv.width = 32; cv.height = 128;
    const g = cv.getContext('2d'); const img = g.createImageData(32, 128);
    for (let y = 0; y < 128; y++) for (let x = 0; x < 32; x++) {
      const k = ENG.n3(x * 0.2, y * 0.08, 0, 151), vein = Math.abs(ENG.n3(x * 0.1, y * 0.03, 3, 152) - 0.5) < 0.03;
      let c = [150 + 40 * k, 140 + 36 * k, 124 + 30 * k];
      if (vein) c = [110, 100, 90];
      if (y > 112) c = mix(c, [110, 86, 60], (y - 112) / 16);
      const i = (y * 32 + x) * 4; img.data[i] = c[0]; img.data[i + 1] = c[1]; img.data[i + 2] = c[2]; img.data[i + 3] = 255;
    }
    g.putImageData(img, 0, 0);
    const tex = new THREE.CanvasTexture(cv); tex.magFilter = THREE.NearestFilter;
    const geo = new THREE.CylinderGeometry(0.32, 0.38, 3.2, 9, 4);
    const pos = geo.attributes.position;
    for (let i = 0; i < pos.count; i++) { const x = pos.getX(i), y = pos.getY(i), z = pos.getZ(i); const k = 1 + 0.12 * (ENG.n3(x * 3, y * 2, z * 3, 153) - 0.5); pos.setX(i, x * k); pos.setZ(i, z * k); }
    geo.computeVertexNormals();
    const m = new THREE.Mesh(geo, new THREE.MeshLambertMaterial({ map: tex, flatShading: true }));
    m.frustumCulled = false;
    return m;
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const at = (n, off) => { obj.updateMatrixWorld(true); return P[n].obj.localToWorld(off.clone()); };
    const braid = (sw, k = 1) => { r('tranca1', 0.1 * Math.sin(t * 1.4) * k, 0, 0.15 * Math.sin(t * 1.1) * k + sw); r('tranca2', 0.15 * Math.sin(t * 1.4 - 0.7) * k, 0, 0.2 * Math.sin(t * 1.1 - 0.7) * k); };

    if (mode === 'parado') {
      stance(r); braid(0);
      // alisa o bigode de vez em quando, com desdém
      const st = seq([[0.6, 0], [0.68, 1, 'out'], [0.88, 1], [0.95, 0]], fr(t / 5));
      r('bracoD', -1.2 * st - 0.35 * st, 0, 0.2 * st); r('anteD', -1.7 * st + 0.6 * st, 0, 0); r('cabeca', 0, 0.15 * st, 0);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 1.0;
      for (const [s, off] of [['D', 0], ['E', 0.5]]) {
        r('perna' + s, 0.5 * Math.sin((ph + off) * Math.PI * 2), 0, 0); r('canela' + s, 0.4 * Math.max(0, Math.sin((ph + off) * Math.PI * 2 + 1.4)), 0, 0);
        r('braco' + s, 0.35, 0, 0); r('ante' + s, -0.6, 0, 0);   // anda de mão pra trás
      }
      r('abaF', -0.3 * Math.abs(Math.sin(ph * Math.PI * 2)), 0, 0); r('abaT', 0.2 * Math.abs(Math.sin(ph * Math.PI * 2)), 0, 0);
      P.quadril.obj.position.y += 0.4 * Math.abs(Math.sin(ph * Math.PI * 2));
      r('cabeca', -0.12, 0, 0); braid(0, 1.5);
      return;
    }

    if (mode === 'dodonpa') {
      const T = t % 3.0;
      const R = ctx.ref; const RZ = -7.5; R.place(0, RZ, 0);
      // vira de lado, estica o braço e aponta só o indicador; faísca cresce no dedo; o raio fino sai e fura
      const aim = seq([[0, 0], [0.35, 1, 'out'], [1.9, 1], [2.3, 0, 'io']], T);
      const charge = seq([[0.4, 0], [1.05, 1, 'in']], T);
      r('quadril', 0, 0.6 * aim, 0); r('tronco', 0, 0.3 * aim, 0); r('cabeca', 0, -0.85 * aim, 0);
      r('bracoD', -1.55 * aim, -0.55 * aim, 0); r('anteD', 0.05 * aim, 0, 0); r('maoD', -0.1 * aim, 0, 0);
      r('bracoE', 0.35, 0, -0.1); r('anteE', -0.6, 0, 0);
      r('pernaD', -0.2 * aim, 0, 0.15 * aim); r('pernaE', 0.2 * aim, 0, -0.1 * aim);
      braid(0.3 * aim);
      const tip = at('dedo', V3(0, 2.2, 0));
      if (T > 0.4 && T < 1.15) {
        ctx.get('faisca', () => DBFX.glow([1.0, 0.9, 0.3])).at(tip, 0.04 + 0.1 * charge + 0.02 * Math.sin(T * 60), 0.9, T);
        ctx.get('luzD', () => DBFX.light(0xffe060)).at(tip, 1.5 * charge, 3);
      }
      if (T >= 1.1 && T < 1.8) {
        const tgt = V3(0, 1.25, RZ + 0.2);
        const out = seq([[1.1, 0], [1.16, 1]], T), fade = seq([[1.4, 1], [1.8, 0]], T);
        ctx.get('raio', () => beam([1.0, 0.85, 0.25])).at(tip, tip.clone().lerp(tgt, out), 0.035 * fade + 0.01, fade, T);
        ctx.get('luzD', () => DBFX.light(0xffe060)).at(tgt, 2.5 * fade, 4);
        if (out >= 1) {
          const k = (T - 1.16) / 0.9;
          ctx.get('furo', FX.volume).set(tgt.clone().add(V3(0, 0, -0.35)), 0.45, Math.min(1, k), { fire: 0.8, seed: 8.8, dens: 9, rise: 0.4, smoke: [0.35, 0.32, 0.3] });
          if (k < 0.15) { ctx.flash(tgt, 1); ctx.shake(0.4); }
          ctx.get('fagulha', () => DBFX.grains({ n: 30, seed: 161, palette: [[255, 230, 120], [255, 255, 220], [255, 180, 60]], size: [0.012, 0.03], drag: 2, grav: 4, life: 0.5, spread: 0.05, add: true, cone: Rr => [(Rr() - 0.5) * 2, Rr() * 2, -2 - Rr() * 2] })).at(tgt, T - 1.16);
        }
      }
      R.lean(-0.5 * seq([[1.16, 0], [1.3, 1, 'out'], [2.2, 1], [2.8, 0]], T));
      return;
    }

    if (mode === 'pilar') {
      const T = t % 5.2;
      const R = ctx.ref; const RZ = -9; R.place(0, RZ, 0);
      // 0-0.9 enfia a mão no chão e arranca o pilar (sobe com terra); 0.9-1.3 ergue e arremessa;
      // 1.35 pula atrás e cai em pé em cima; 1.5-2.6 voa em pé no pilar, braços cruzados;
      // 2.6 salta numa voadora no alvo; o pilar passa e se espatifa; 3.6 volta
      const pz0 = -1.0;
      const pullUp = seq([[0.2, 0], [0.9, 1, 'out']], T);
      const lift = seq([[0.9, 0], [1.2, 1, 'out']], T);
      const flight = seq([[1.25, 0], [2.9, 1, 'in']], T);
      const pil = ctx.get('pilar', pillar); pil.visible = T < 3.3;
      let pp = V3(0, -1.6 + 1.8 * pullUp, pz0), prot = 0;
      if (T >= 0.9) { pp = V3(0, 0.2 + 1.8 + 0.6 * lift, pz0); prot = -Math.PI / 2 * lift; }
      if (T >= 1.25) { const zz = pz0 + (RZ - 1.5 - pz0) * flight; pp = V3(0, 2.6 + 0.3 * Math.sin(Math.PI * flight), zz); prot = -Math.PI / 2; }
      if (T >= 2.9) { const k = (T - 2.9) / 0.4; pp = V3(0.3 * k, 2.6 - 2.4 * k * k, RZ - 1.5 - 1.5 * k); prot = -Math.PI / 2 + 0.6 * k; }
      pil.position.copy(pp); pil.rotation.set(prot, 0, 0);
      // corpo
      const crouch = seq([[0, 0], [0.2, 1, 'out'], [0.9, 1], [1.0, 0]], T);
      r('tronco', 0.9 * crouch, 0, 0); r('pernaD', -0.8 * crouch, 0, 0); r('pernaE', -0.8 * crouch, 0, 0); r('canelaD', 1.3 * crouch, 0, 0); r('canelaE', 1.3 * crouch, 0, 0);
      P.quadril.obj.position.y += 4 * crouch;
      r('bracoD', -1.2 * crouch - 2.6 * lift * (1 - seq([[1.2, 0], [1.3, 1]], T)), 0, 0); r('bracoE', -1.2 * crouch - 2.6 * lift * (1 - seq([[1.2, 0], [1.3, 1]], T)), 0, 0);
      if (T >= 1.3 && T < 2.6) {
        // em pé no pilar, braços cruzados, roupa e trança batendo no vento
        obj.position.set(0, pp.y + 0.35 + 1.5, pp.z + 0.6);
        r('bracoD', -0.8, 0.6, 0); r('anteD', -1.8, 0, 0); r('bracoE', -0.8, -0.6, 0); r('anteE', -1.8, 0, 0);
        r('abaF', 0.6 + 0.15 * Math.sin(T * 30), 0, 0); r('abaT', 0.9 + 0.2 * Math.sin(T * 26), 0, 0);
        braid(0, 0); r('tranca1', 1.2 + 0.1 * Math.sin(T * 20), 0, 0); r('tranca2', 0.3 + 0.2 * Math.sin(T * 20 - 1), 0, 0);
        r('cabeca', -0.2, 0, 0);
      } else if (T >= 1.25 && T < 1.3) {
        obj.position.set(0, 1.5 + 1.2, -0.3);
      } else if (T >= 2.6 && T < 3.3) {
        // voadora: perna direita esticada, a outra dobrada
        const k = seq([[2.6, 0], [2.85, 1, 'out']], T);
        const zz = (pz0 + (RZ - 1.5 - pz0) * 0.83) + 0.6;
        obj.position.set(0, 4.45 + (1.9 - 4.45) * k + 0.4 * Math.sin(Math.PI * k), zz + (RZ + 1.0 - zz) * k);
        obj.rotation.y = 0;
        r('pernaD', -1.5 * k, 0, 0); r('pernaE', 0.2 * k, 0, 0); r('canelaE', 1.6 * k, 0, 0);
        r('tronco', 0.4 * k, 0, 0); r('bracoD', -0.4, 0, 1.2 * k); r('bracoE', -0.4, 0, -1.2 * k);
        r('abaF', 0.5, 0, 0); r('abaT', 0.7, 0, 0);
      } else if (T >= 3.3) {
        const k = seq([[3.3, 0], [4.2, 1, 'io']], T);
        obj.position.set(0, 1.5, (RZ + 1.0) * (1 - k));
        stance(r);
      } else { braid(0); }
      if (T > 0.2 && T < 1.4) ctx.get('terra', () => FX.debris(26, 167, [[120, 96, 70], [90, 74, 60], [150, 130, 100], [80, 110, 60]])).at(V3(0, 0.05, pz0), T - 0.2);
      if (T > 0.2 && T < 1.0) ctx.shake(0.3);
      const kick = T - 2.85;
      if (kick > 0) {
        const hp = V3(0, 1.3, RZ + 0.3);
        if (kick < 0.08) { ctx.flash(hp, 1); ctx.get('impacto', () => DBFX.muzzle()).at(hp, ctx.camera, 1 - kick / 0.08, 3, 1.3); }
        ctx.shake(Math.max(0, 0.8 - kick * 2));
        const fly = seq([[2.85, 0], [3.5, 1, 'out']], T);
        R.place(0, RZ - 3 * fly, 0.9 * Math.sin(Math.PI * fly)); R.lean(-1.3 * fly * (1 - seq([[4.5, 0], [5.2, 1]], T)));
      }
      if (T > 3.2 && T < 4.6) {
        const k = (T - 3.2) / 1.4;
        ctx.get('pilarQuebra', () => FX.debris(30, 171, [[170, 160, 140], [140, 130, 116], [190, 180, 160]])).at(V3(0.3, 0.3, RZ - 3), T - 3.2);
        ctx.get('poeira', FX.volume).set(V3(0.3, 0.4, RZ - 3), 1.0, k, { fire: 0, seed: 4.1, dens: 7, rise: 0.4, smoke: [0.62, 0.56, 0.48] });
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(4), variant(12)];
  fixed[0].cyborg = false; fixed[1].cyborg = true;
  const def = v => ({ def: rigDef(painters(v), v, false), low: rigDef(painters(v), v, true) });
  window.MOBS.push({
    id: 'tao_pai_pai', nome: 'Tao Pai Pai', bioma: 'Vilões do mundo', tier: 'Médio · tier 6 (raro)',
    variants: fixed.map((v, i) => ({ nome: ['Assassino', 'Ciborgue (voltou)'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['dodonpa', 'Dodonpa'], ['pilar', 'Pilar Voador']],
    cam: 6.5, lookY: 1.3, lookZ: { dodonpa: -3.5, pilar: -4.4 }, camFar: { dodonpa: 9.5, pilar: 13 }, camYaw: { dodonpa: -1.25, pilar: -1.3 },
    info: {
      'Tamanho': '2,1 blocos (alto e magro)',
      'Vida': '90 (tier 6). Raro: só 1 por mundo de cada vez. Quando cai, dá pra ver ele de novo semanas depois na versão ciborgue.',
      'Jeito': 'Vaga sozinho pelas Terras do Dragão e perto da torre do Karin. Quem passa perto vira "alvo contratado": ele avisa com uma fala e persegue.',
      'Dodonpa': 'Vira de lado e aponta o dedo: a faísca amarela no dedo é o aviso. Raio finíssimo e muito rápido que fura (ignora parte da armadura). Dano em pacotes no jogo (Escudo de tier).',
      'Pilar Voador': 'Arranca um pilar do chão (treme e espirra terra), arremessa, voa em cima dele de braços cruzados e salta numa voadora. Quebrar o pilar no ar (bater nele) derruba ele.',
      'Solta': 'Trança do Tao Pai Pai (troféu), roupa rosa; raro: Alma do Tao Pai Pai.',
      'Variações': 'Tom da roupa; a versão ciborgue tem braço e lado da cabeça de metal.',
    },
  });
})();
