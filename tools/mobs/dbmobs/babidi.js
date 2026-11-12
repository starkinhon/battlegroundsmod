// Babidi (dentro da nave dele, no último andar). Bruxo baixinho, cabeçudo e enrugado, orelhas pontudas e capa de gola
// alta. Fraco no braço, perigoso na magia. Foge se você chega perto.
// Golpes: Paparapa (círculo de magia embaixo do alvo: incha, brilha e explode por dentro; dá pra sair do círculo)
// e Controle Majin (o fio de magia escura acha a sua cabeça e acende o M na testa: você vira contra os seus).
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
    // pele enrugada: dobras curvas na testa e na cabeça (linhas escuras finas que seguem a curva)
    P.skin = q => shade(v.skin, q, 1);
    P.head = q => {
      let c = shade(v.skin, q, 2, 0.86, 1.08);
      const wr = fr(q.ay * 0.55 + 0.25 * Math.sin(q.ax * 0.5 + q.az * 0.4));
      if (q.f !== 'bottom' && wr < 0.1 && q.py < q.b.h * 0.55) c = mul(c, 0.82);
      if (q.f !== 'north') return c;
      const W = q.b.w, x = Math.min(W - 1, Math.floor(q.px)), y = Math.min(q.b.h - 1, Math.floor(q.py)), m = Math.min(x, W - 1 - x);
      // olhão redondo: branco com pupila grande, sobrancelha nenhuma; nariz pequeno; boca larga de sorriso maldoso
      if ((y === 5 || y === 6) && (m === 1 || m === 2 || m === 3)) c = (m === 3 || (y === 5 && m === 1)) ? [250, 248, 240] : [24, 20, 30];
      if (y === 6 && m === 1) c = [250, 248, 240];
      if (y === 7 && m === 4) c = mul(v.skin, 0.75);
      if (y === 8 && m >= 2 && m <= 4) c = [90, 40, 50];
      if (y === 8 && m === 1) c = mul(v.skin, 0.75);
      return c;
    };
    P.ear = q => { if (q.f !== 'north' && q.f !== 'south') return null; const d = q.ax < 0 ? q.px / q.b.w : 1 - q.px / q.b.w, w = q.py / q.b.h; if (Math.abs(w - 0.55) > 0.5 * d + 0.03) return null; let c = shade(mul(v.skin, 0.95), q, 3); if (q.f === 'north' && Math.abs(w - 0.55) < 0.25 * d && d > 0.4) c = mul(c, 0.82); return c; };
    P.cape = q => { let c = shade(v.cape, q, 4); if (q.f !== 'top' && fr(q.px * 0.5 + q.pz * 0.5) < 0.1) c = mul(c, 0.86); return c; };
    P.collar = q => { if (q.f === 'top' || q.f === 'bottom') return null; let c = shade(mul(v.cape, 0.85), q, 5); if (q.py < 0.6) c = shade([220, 190, 90], q, 6); return c; };
    P.robe = q => shade(v.robe, q, 7);
    P.belt = q => { let c = shade([200, 170, 70], q, 8); if (q.f === 'north' && Math.abs(q.px - q.b.w / 2) < 0.8) c = [170, 40, 70]; return c; };
    P.boot = q => shade([90, 60, 80], q, 9);
    return P;
  }

  function rigDef(P, v, low) {
    const arm = s => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * 3.4, -6.4, 0], boxes: [{ x: -1.1, y: -0.6, z: -1.1, w: 2.2, h: 3.6, d: 2.2, p: P.robe }],
      kids: [{ name: s < 0 ? 'anteD' : 'anteE', pivot: [0, 3, 0], boxes: [{ x: -1, y: 0, z: -1, w: 2, h: 2.6, d: 2, p: P.robe }], kids: [{ name: s < 0 ? 'maoD' : 'maoE', pivot: [0, 2.6, 0], boxes: [{ x: -1, y: -0.2, z: -1, w: 2, h: 1.8, d: 2, p: P.skin }] }] }],
    });
    const leg = s => ({ name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * 1.4, 18, 0], boxes: [{ x: -1.3, y: 0, z: -1.3, w: 2.6, h: 4, d: 2.6, p: P.robe }, { x: -1.4, y: 4, z: -2.2, w: 2.8, h: 2, d: 3.6, p: P.boot }] });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'quadril', pivot: [0, 18, 0], boxes: [{ x: -2.8, y: -1.6, z: -1.8, w: 5.6, h: 1.8, d: 3.6, p: P.belt }], kids: [{
          name: 'tronco', pivot: [0, -1.4, 0], boxes: [{ x: -2.8, y: -6.6, z: -1.8, w: 5.6, h: 6.6, d: 3.6, p: P.robe }], kids: [
            {
              name: 'cabeca', pivot: [0, -6.6, -0.2], boxes: [{ x: -5, y: -10, z: -4.5, w: 10, h: 10, d: 9, p: P.head }, ...(low ? [] : [{ x: -4.2, y: -11, z: -3.6, w: 8.4, h: 1.2, d: 7.2, p: P.head }])],
              kids: [
                { name: 'orelhaD', pivot: [-4.8, -5.6, 0.6], rot: [0, 0.2, 0.3], boxes: [{ x: -6, y: -2.4, z: -0.3, w: 6, h: 4.4, d: 0.6, p: P.ear }] },
                { name: 'orelhaE', pivot: [4.8, -5.6, 0.6], rot: [0, -0.2, -0.3], boxes: [{ x: 0, y: -2.4, z: -0.3, w: 6, h: 4.4, d: 0.6, p: P.ear }] },
              ],
            },
            // capa: gola alta em volta da cabeça e o pano caindo atrás
            ...(low ? [] : [{ name: 'gola', pivot: [0, -6.6, 0], boxes: [{ x: -4.6, y: -3, z: 1.4, w: 9.2, h: 3.6, d: 0.6, p: P.collar }, { x: -4.6, y: -3, z: -2.6, w: 0.6, h: 3.6, d: 4.4, p: P.collar }, { x: 4, y: -3, z: -2.6, w: 0.6, h: 3.6, d: 4.4, p: P.collar }] }]),
            { name: 'capa', pivot: [0, -6, 2], boxes: [{ x: -4.2, y: 0, z: 0, w: 8.4, h: 11.6, d: 0.6, p: P.cape }] },
            arm(-1), arm(1),
          ],
        }],
      }, leg(-1), leg(1)],
    };
  }

  // círculo de magia: anéis, runas angulares sorteadas por setor (nenhuma igual à outra) e um pentagrama girando
  function circle(col) {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, side: THREE.DoubleSide, blending: THREE.NormalBlending,
      uniforms: { uT: { value: 0 }, uK: { value: 0 }, uC: { value: new THREE.Color(...col) } },
      vertexShader: 'varying vec2 vP; void main(){ vP=position.xy; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: `varying vec2 vP; uniform float uT, uK; uniform vec3 uC;
        float h(float x){ return fract(sin(x*61.3)*43758.5); }
        float seg(vec2 p, vec2 a, vec2 b){ vec2 pa=p-a, ba=b-a; float k=clamp(dot(pa,ba)/dot(ba,ba),0.0,1.0); return length(pa-ba*k); }
        void main(){
          float r = length(vP), a = atan(vP.y, vP.x);
          float line = 0.0;
          line += smoothstep(0.04, 0.0, abs(r - 0.95)) + smoothstep(0.03, 0.0, abs(r - 0.86)) + smoothstep(0.025, 0.0, abs(r - 0.52));
          // runas entre os dois anéis de fora: cada setor tem 3 traços com ângulo sorteado
          float N = 18.0, sid = floor((a + uT * 0.4) / 6.2832 * N);
          float sa = (sid + 0.5) / N * 6.2832 - uT * 0.4;
          vec2 c = vec2(cos(sa), sin(sa)) * 0.905;
          vec2 tg = vec2(-sin(sa), cos(sa)), nr = vec2(cos(sa), sin(sa));
          for (int i = 0; i < 3; i++) {
            float fi = float(i);
            vec2 p0 = c + tg * (h(sid * 7.0 + fi) - 0.5) * 0.12 + nr * (h(sid * 3.0 + fi + 9.0) - 0.5) * 0.06;
            vec2 p1 = c + tg * (h(sid * 5.0 + fi + 2.0) - 0.5) * 0.12 + nr * (h(sid * 11.0 + fi + 4.0) - 0.5) * 0.06;
            line += smoothstep(0.02, 0.0, seg(vP, p0, p1));
          }
          // estrela de cinco pontas girando ao contrário
          for (int i = 0; i < 5; i++) {
            float a0 = float(i) * 1.2566 - uT * 0.7, a1 = a0 + 2.5133;
            line += smoothstep(0.025, 0.0, seg(vP, vec2(cos(a0), sin(a0)) * 0.84, vec2(cos(a1), sin(a1)) * 0.84));
          }
          float glow = smoothstep(1.0, 0.0, r) * 0.35;
          float k = clamp(line, 0.0, 1.0);
          // revela girando: o desenho aparece por ângulo conforme uK
          float rev = step(fract((a + 3.1416) / 6.2832), uK);
          float al = (k + glow * (0.5 + 0.5 * sin(uT * 6.0))) * rev * min(1.0, uK * 2.0);
          if (al < 0.01 || r > 1.0) discard;
          gl_FragColor = vec4(mix(uC, vec3(1.0), k * 0.4), al);
        }`,
    });
    const m = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), mat); m.rotation.x = -Math.PI / 2; m.frustumCulled = false;
    m.at = (p, r, k, t) => { m.visible = k > 0.01; m.position.copy(p); m.scale.setScalar(r); mat.uniforms.uK.value = k; mat.uniforms.uT.value = t; };
    return m;
  }
  // M que acende na testa do alvo: plaquinha desenhada em pixel, sempre virada pra câmera
  function mSign() {
    const cv = document.createElement('canvas'); cv.width = 5; cv.height = 3;
    const g = cv.getContext('2d'); g.fillStyle = '#ff2040';
    ['X...X', 'XX.XX', 'X.X.X'].forEach((row, y) => [...row].forEach((ch, x) => { if (ch === 'X') g.fillRect(x, y, 1, 1); }));
    const tex = new THREE.CanvasTexture(cv); tex.magFilter = THREE.NearestFilter;
    const s = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, transparent: true, depthTest: false }));
    s.at = (p, k) => { s.visible = k > 0.01; s.position.copy(p); s.scale.set(0.3 * k, 0.18 * k, 1); s.material.opacity = k; };
    return s;
  }
  // fio de magia escura: fita torcida do dedo até a cabeça do alvo
  function tendril() {
    const N = 40, pos = new Float32Array(N * 2 * 3), al = new Float32Array(N * 2), idx = [];
    for (let i = 0; i < N - 1; i++) { const a = i * 2; idx.push(a, a + 1, a + 2, a + 1, a + 3, a + 2); }
    const geo = new THREE.BufferGeometry(); geo.setIndex(idx);
    geo.setAttribute('position', new THREE.BufferAttribute(pos, 3)); geo.setAttribute('al', new THREE.BufferAttribute(al, 1));
    const m = new THREE.Mesh(geo, new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, side: THREE.DoubleSide,
      vertexShader: 'attribute float al; varying float vA; void main(){ vA=al; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: 'varying float vA; void main(){ gl_FragColor = vec4(mix(vec3(0.25,0.05,0.3), vec3(0.9,0.3,0.6), vA*0.6), vA); }',
    }));
    m.frustumCulled = false;
    m.at = (a, b, k, t) => {
      m.visible = k > 0.01;
      for (let i = 0; i < N; i++) {
        const f = i / (N - 1) * k, p = a.clone().lerp(b, f);
        const tw = Math.sin(f * 12 - t * 8) * 0.18 * Math.sin(Math.PI * f / Math.max(k, 0.01));
        p.y += tw + 0.4 * Math.sin(Math.PI * f / Math.max(k, 0.01)); p.x += Math.cos(f * 9 - t * 6) * 0.12;
        const wdt = 0.05 * (1 - f * 0.5);
        pos.set([p.x, p.y + wdt, p.z, p.x, p.y - wdt, p.z], i * 6);
        al[i * 2] = al[i * 2 + 1] = 0.85 * (0.4 + 0.6 * Math.abs(Math.sin(f * 20 - t * 10)));
      }
      geo.attributes.position.needsUpdate = true; geo.attributes.al.needsUpdate = true;
    };
    return m;
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const at = (n, off) => { obj.updateMatrixWorld(true); return P[n].obj.localToWorld(off.clone()); };
    const breath = loop([[0, 0], [0.45, 1], [0.999, 0]], t / 2.2);
    const cape = (k = 1) => r('capa', 0.08 + 0.05 * Math.sin(t * 2.1) * k, 0, 0.03 * Math.sin(t * 1.7) * k);

    if (mode === 'parado') {
      // mãos juntas na frente esfregando, risadinha (ombros sacudindo), cabeça balançando
      r('bracoD', -0.7, 0.4, 0); r('anteD', -0.9, 0, 0); r('bracoE', -0.7, -0.4, 0); r('anteE', -0.9, 0, 0);
      const giggle = seq([[0.5, 0], [0.55, 1], [0.85, 1], [0.9, 0]], fr(t / 3.5));
      r('tronco', -0.05 * breath + 0.05 * giggle * Math.sin(t * 40), 0, 0); r('cabeca', -0.1 * giggle, 0.2 * Math.sin(t * 0.7), 0.05 * Math.sin(t * 1.3));
      r('maoD', 0.2 * Math.sin(t * 8), 0, 0); r('maoE', -0.2 * Math.sin(t * 8), 0, 0);
      cape();
      return;
    }
    if (mode === 'andar') {
      const ph = t * 1.5;
      for (const [s, off] of [['D', 0], ['E', 0.5]]) { r('perna' + s, 0.5 * Math.sin((ph + off) * Math.PI * 2), 0, 0); r('braco' + s, -0.3 * Math.sin((ph + off) * Math.PI * 2), 0, 0); r('ante' + s, -0.4, 0, 0); }
      P.quadril.obj.position.y += 0.4 * Math.abs(Math.sin(ph * Math.PI * 2));
      r('cabeca', 0, 0, 0.08 * Math.sin(ph * Math.PI * 2)); cape(2); r('capa', 0.15, 0, 0);
      return;
    }

    if (mode === 'paparapa') {
      const T = t % 4.4;
      const R = ctx.ref; const RZ = -6; R.place(0, RZ, 0);
      // "Paparapa!": braços pra cima balançando, o círculo acende em volta do alvo (1,6 s pra sair dele),
      // aponta as duas mãos e fecha: o alvo incha, brilha por dentro e explode
      const up = seq([[0, 0], [0.3, 1, 'out'], [1.7, 1], [1.85, 0]], T);
      const wave = Math.sin(T * 12) * up;
      r('bracoD', -2.6 * up, 0, 0.4 * up + 0.2 * wave); r('bracoE', -2.6 * up, 0, -0.4 * up - 0.2 * wave); r('maoD', 0.4 * wave, 0, 0); r('maoE', -0.4 * wave, 0, 0);
      r('cabeca', -0.25 * up, 0, 0.1 * wave);
      const point = seq([[1.75, 0], [1.9, 1, 'snap'], [2.6, 1], [3.0, 0]], T);
      r('bracoD', -1.5 * point, 0.2 * point, 0); r('bracoE', -1.5 * point, -0.2 * point, 0); r('tronco', 0.15 * point, 0, 0);
      cape(1 + up);
      const rev = seq([[0.2, 0], [1.2, 1, 'out']], T), pulse = 1 + 0.06 * Math.sin(T * 20) * up;
      if (T < 2.3) ctx.get('circulo', () => circle([0.85, 0.1, 0.6])).at(V3(0, 0.03, RZ), 1.3 * pulse, rev, T);
      ctx.get('maos', () => DBFX.glow([0.95, 0.3, 0.8])).at(at('maoD', V3(0, 2, 0)).lerp(at('maoE', V3(0, 2, 0)), 0.5), 0.1 + 0.05 * Math.sin(T * 30), 0.5 * (up + point), T);
      // alvo: sobe um pouco, treme, incha (escala) e explode
      const swell = seq([[1.9, 0], [2.5, 1, 'in']], T);
      if (T > 1.9 && T < 2.55) { R.place(0, RZ, 0.3 * swell); R.lean(0.15 * Math.sin(T * 50) * swell); ctx.get('dentro', () => DBFX.glow([1, 0.4, 0.8])).at(V3(0, 1.0 + 0.3 * swell, RZ), 0.3 + 0.6 * swell, 0.6 * swell, T); }
      if (T >= 2.5) {
        const k = (T - 2.5) / 1.4;
        ctx.get('boom', FX.volume).set(V3(0, 1.0, RZ), 1.4, Math.min(1, k), { fire: 0.9, seed: 12.2, dens: 11, rise: 0.6, smoke: [0.4, 0.2, 0.36] });
        if (k < 0.2) { ctx.flash(V3(0, 1, RZ), 1 - k * 5); ctx.shake(1 - k * 4); }
        ctx.roarRing(V3(0, 0.03, RZ), Math.min(1, k * 1.3));
        const fly = seq([[2.5, 0], [3.1, 1, 'out']], T);
        R.place(0, RZ - 2.5 * fly, 1.4 * Math.sin(Math.PI * fly)); R.lean(-1.3 * fly * (1 - seq([[3.8, 0], [4.4, 1]], T))); R.spin(2 * fly);
      }
      return;
    }

    if (mode === 'controle') {
      const T = t % 5.0;
      const R = ctx.ref; const RZ = -5; R.place(0, RZ, 0);
      // estica um dedo, fio escuro serpenteia até a cabeça do alvo; o alvo segura a cabeça, o M acende na testa,
      // ele se endireita de olhar vazio e vira de costas pro Babidi (agora protege ele). Babidi gargalha.
      const aim = seq([[0, 0], [0.35, 1, 'out'], [2.4, 1], [2.7, 0]], T);
      r('bracoD', -1.6 * aim, 0, 0); r('bracoE', -0.6 * aim, 0, -0.6 * aim); r('anteE', -1.2 * aim, 0, 0); r('cabeca', 0.1 * aim, 0, 0);
      cape(1);
      const reach = seq([[0.4, 0], [1.1, 1, 'out']], T);
      const finger = at('maoD', V3(0, 2, 0)), head = V3(0, 1.75, RZ);
      if (T > 0.4 && T < 2.6) ctx.get('fio', tendril).at(finger, head, reach * (1 - seq([[2.3, 0], [2.6, 1]], T)), T);
      const pain = seq([[1.1, 0], [1.25, 1], [2.2, 1], [2.4, 0]], T);
      R.armsOut(-0.6 * pain); R.lean(0.35 * pain + 0.1 * Math.sin(T * 30) * pain);
      const mk = seq([[1.6, 0], [1.9, 1, 'out']], T);
      ctx.get('M', mSign).at(V3(0, 1.82, RZ + 0.27), mk);
      if (T > 1.6 && T < 2.2) ctx.get('estalo', () => DBFX.grains({ n: 30, seed: 211, palette: [[255, 60, 90], [255, 160, 180], [120, 10, 40]], size: [0.012, 0.03], drag: 3, grav: 0, life: 0.4, spread: 0.3, add: true, cone: Rr => { const a = Rr() * 6.28; return [Math.cos(a) * 1.5, Rr(), Math.sin(a) * 1.5]; } })).at(head, T - 1.6);
      const turn = seq([[2.5, 0], [3.0, 1, 'io']], T);
      R.spin(Math.PI * turn); if (T > 2.5) R.armsOut(0.25 * turn);
      // gargalhada: tronco pra trás sacudindo
      const laugh = seq([[2.6, 0], [2.8, 1], [4.4, 1], [4.8, 0]], T);
      r('tronco', -0.3 * laugh + 0.06 * Math.sin(T * 35) * laugh, 0, 0); r('cabeca', -0.3 * laugh, 0, 0); r('bracoD', -0.4 * laugh, 0, 0.5 * laugh); r('bracoE', -0.4 * laugh, 0, -0.5 * laugh);
      if (T > 4.6) ctx.get('M', mSign).at(V3(0, 1.82, RZ - 0.27), 1 - (T - 4.6) / 0.4);
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const vs = [{ seed: 2, skin: [222, 214, 150], cape: [70, 40, 90], robe: [120, 70, 120] }, { seed: 7, skin: [210, 200, 160], cape: [40, 40, 80], robe: [90, 60, 110] }];
  const def = v => ({ def: rigDef(painters(v), v, false), low: rigDef(painters(v), v, true) });
  window.MOBS.push({
    id: 'babidi', nome: 'Babidi', bioma: 'Vilões do mundo', tier: 'Mago · chefe da nave',
    variants: vs.map((v, i) => ({ nome: ['Capa roxa', 'Capa azul'][i], v, ...def(v) })),
    randomVariant: () => { const v = { ...vs[Math.floor(Math.random() * 2)], seed: Math.floor(Math.random() * 1e5) }; return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['paparapa', 'Paparapa'], ['controle', 'Controle Majin']],
    cam: 4.5, lookY: 0.9, lookZ: { paparapa: -3, controle: -2.5 }, camFar: { paparapa: 9, controle: 8 }, camYaw: { paparapa: -1.15, controle: -1.0 }, refX: 2.2,
    info: {
      'Tamanho': '1,5 bloco (metade é cabeça)',
      'Vida': '60 (fraco no corpo). Chefe do último andar da nave: luta escondido atrás dos que ele controla e se teleporta quando você chega perto.',
      'Jeito': 'Só na nave dele. Antes da luta ele já marcou 2 ou 3 mobs do andar com o M (eles lutam do lado dele). Vencer o Yakon e a dupla Spopovich/Yamu abre a porta dele.',
      'Paparapa': 'Braços pra cima e o círculo de magia acende em volta de você: 1,6 s pra sair. Se ficar, incha e explode por dentro (dano alto, em pacotes no jogo).',
      'Controle Majin': 'O fio escuro procura a sua cabeça (dá pra quebrar a linha de visão). Em mob: vira aliado dele com o M na testa. Em jogador: 4 s com o M aceso, controles trocados e o golpe acerta os seus amigos.',
      'Solta': 'Livro de magia do Babidi (ensina Paparapa), capa; raro: Alma do Babidi. Vencer ele no fim da nave libera o caminho do Majin Boo.',
      'Variações': 'Cor da capa e da roupa.',
    },
  });
})();
