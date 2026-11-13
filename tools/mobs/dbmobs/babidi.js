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
    const SK = v.skin;
    const P = {};
    P.skin = q => { let c = shade(SK, q, 1); if (fr(q.ay * 0.9 + 0.3 * Math.sin(q.ax)) < 0.12) c = mul(c, 0.86); return c; };
    // cabeção: domo amarelo com dobras (rugas em arco que seguem a curva da cabeça) e uns pelinhos escuros espetados
    const wrinkles = (q, c) => {
      const arc = q.ay * 0.62 + 0.18 * Math.sin(q.ax * 0.45) + 0.12 * Math.sin(q.az * 0.5 + 1);
      if (fr(arc) < 0.13) c = mul(c, 0.8);
      if (n3(q.ax * 1.7, q.ay * 1.7, q.az * 1.7, 230 + S) > 0.8 && q.ay < -3) c = [90, 70, 40];   // pelinhos
      return c;
    };
    P.head = q => {
      let c = wrinkles(q, shade(SK, q, 2, 0.84, 1.1));
      if (q.f !== 'north') return c;
      const x = Math.min(9, Math.floor(q.px)), y = Math.min(8, Math.floor(q.py)), m = Math.min(x, 9 - x);
      // rosto 10x9 (só a metade de baixo; em cima é testa enrugada): pálpebra pesada caída, olho branco estreito
      // com a íris verde virada pro lado, bochecha funda
      if (y <= 2) return c;
      c = shade(SK, q, 3, 0.86, 1.08);
      if (y === 3 && m >= 1 && m <= 3) c = mul(SK, 0.5);                  // pálpebra pesada caída
      if (y === 4 && m >= 1 && m <= 3) c = m === 2 ? [60, 170, 60] : [248, 246, 238];
      if (y === 4 && m === 2 && x > 4) c = [248, 246, 238];
      if (y === 4 && m === 3 && x > 4) c = [60, 170, 60];                  // os dois olham pro mesmo lado
      if (y === 5 && m >= 1 && m <= 3) c = mul(SK, 0.78);                  // olheira
      if (y >= 6 && m <= 1) c = mul(SK, 0.82);
      return c;
    };
    // focinho: o nariz de camelo com as duas narinas e a boca fechada pra baixo
    P.snout = q => {
      let c = shade(SK, q, 4, 0.86, 1.1);
      if (q.f === 'north') {
        const u = q.px / q.b.w, w = q.py / q.b.h;
        if (w < 0.45 && Math.abs(Math.abs(u - 0.5) - 0.17) < 0.09 && w > 0.15) c = [70, 50, 30];
        if (Math.abs(w - 0.62) < 0.07 && Math.abs(u - 0.5) < 0.3) c = mul(SK, 0.55);
        if (w > 0.66 && w < 0.85 && Math.abs(Math.abs(u - 0.5) - 0.3) < 0.07) c = mul(SK, 0.6);   // canto da boca caindo
      }
      return c;
    };
    P.whisker = q => shade(mix(SK, [255, 240, 160], 0.2), q, 5, 0.8, 1.15);
    P.hair = q => shade([70, 54, 30], q, 6, 0.8, 1.2);
    P.top = q => { let c = shade([34, 34, 40], q, 7, 0.8, 1.3); if (q.f === 'north' && q.py > q.b.h - 0.6) c = mul(c, 0.85); return c; };
    // saia azul-clara comprida em sino, com pregas verticais, e o pano branco com o M na frente
    P.skirt = q => {
      let c = shade(v.robe, q, 8, 0.82, 1.12);
      const pl = fr((q.f === 'north' || q.f === 'south' ? q.ax : q.az) * 0.45 + 0.15 * Math.sin(q.ay * 0.3));
      if (q.f !== 'top' && q.f !== 'bottom' && pl < 0.15) c = mul(c, 0.84);
      if (q.f !== 'top' && q.f !== 'bottom' && pl > 0.85) c = mul(c, 1.07);
      if (q.f === 'top') c = mul(c, 0.82);
      return c;
    };
    P.patch = q => {
      if (q.f !== 'north') return shade([240, 240, 236], q, 9);
      let c = shade([244, 244, 240], q, 9, 0.92, 1.04);
      // M do Majin em pixel (5x4), com as pontas de baixo viradas pra fora
      const x = Math.min(4, Math.floor(q.px)), y = Math.min(3, Math.floor(q.py));
      const MM = ['X...X', 'XX.XX', 'X.X.X', 'X...X'];
      if (MM[y][x] === 'X') c = [30, 30, 36];
      return c;
    };
    // capa laranja: dobras largas que descem, borda de dentro mais escura
    P.cape = q => {
      let c = shade(v.cape, q, 10, 0.78, 1.15);
      const fold = Math.sin((q.f === 'north' || q.f === 'south' ? q.ax : q.az) * 0.7 + q.ay * 0.08);
      c = mul(c, 0.92 + 0.1 * fold);
      if (q.f === 'north') c = mul(c, 0.8);
      return c;
    };
    P.shoe = q => { let c = shade([110, 80, 60], q, 11); if (q.f === 'top' && q.pz < 1.2) c = mul(c, 0.85); return c; };
    return P;
  }

  function rigDef(P, v, low) {
    const arm = s => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * 3.2, -4.4, -0.4], boxes: [{ x: -0.9, y: -0.6, z: -0.9, w: 1.8, h: 3.4, d: 1.8, p: P.skin }],
      kids: [{ name: s < 0 ? 'anteD' : 'anteE', pivot: [0, 2.8, 0], boxes: [{ x: -0.8, y: 0, z: -0.8, w: 1.6, h: 2.8, d: 1.6, p: P.skin }], kids: [{ name: s < 0 ? 'maoD' : 'maoE', pivot: [0, 2.8, 0], boxes: [{ x: -0.9, y: -0.2, z: -0.9, w: 1.8, h: 1.6, d: 1.8, p: P.skin }] }] }],
    });
    // pé pontudo marrom saindo debaixo da saia
    const leg = s => ({ name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * 1.6, 21, 0], boxes: [{ x: -1.1, y: 0, z: -1.1, w: 2.2, h: 1.6, d: 2.2, p: P.skin }, { x: -1.2, y: 1.4, z: -2.6, w: 2.4, h: 1.6, d: 3.8, p: P.shoe }, ...(low ? [] : [{ x: -0.6, y: 1.8, z: -3.8, w: 1.2, h: 1.0, d: 1.4, p: P.shoe }])] });
    const hairs = low ? [] : [[-2.4, -1.5], [1.2, -2.6], [3.0, 0.8], [-3.2, 1.6], [0.2, 2.8], [-1, 0.2], [2.2, -0.6]].map(([x, z], i) => ({ x, y: -11.6 - (i % 3) * 0.3, z, w: 0.3, h: 1 + (i % 2) * 0.6, d: 0.3, p: P.hair }));
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'quadril', pivot: [0, 13, 0], boxes: [
          ...(low ? [[7.2, 6, -0.6, 3.6], [9.2, 7.6, 3, 3.8], [11, 9, 6.8, 3.8]] : Array.from({ length: 10 }, (_, i) => [7.2 + i * 0.42, 6 + i * 0.32, -0.6 + i * 1.05, 1.1 + (i === 9 ? 0.5 : 0)])).map(([w, d, y, h]) => ({ x: -w / 2, y, z: -d / 2 - 0.2, w, h, d, p: P.skirt })),
          { x: -2.5, y: -0.4, z: -3.5, w: 5, h: 4, d: 0.5, p: P.patch },
        ], kids: [{
          name: 'tronco', pivot: [0, -0.4, 0], rot: [0.3, 0, 0], boxes: [{ x: -3.2, y: -5.2, z: -2.6, w: 6.4, h: 5.4, d: 5.2, p: P.top }], kids: [
            {
              name: 'cabeca', pivot: [0, -5.2, -1.2], rot: [-0.25, 0, 0], boxes: [
                { x: -5, y: -8, z: -5, w: 10, h: 8, d: 9.4, p: P.head },
                { x: -5.6, y: -7.4, z: -4.2, w: 11.2, h: 4.4, d: 7.8, p: P.head },
                { x: -4.4, y: -9.6, z: -4.4, w: 8.8, h: 1.8, d: 8.8, p: P.head },
                ...(low ? [] : [{ x: -3.4, y: -10.8, z: -3.4, w: 6.8, h: 1.4, d: 6.8, p: P.head }, { x: -4.4, y: -8.8, z: 4.2, w: 8.8, h: 6, d: 1.0, p: P.head }]),
                { x: -1.6, y: -3.0, z: -6.4, w: 3.2, h: 3, d: 1.6, p: P.snout },
                ...hairs,
              ],
              kids: low ? [] : [-1, 1].map(s => ({
                name: s < 0 ? 'bigodeD' : 'bigodeE', pivot: [s * 1.5, -1.4, -6.2], rot: [0, 0, s * 0.5], boxes: [{ x: -0.25, y: 0, z: -0.25, w: 0.5, h: 2.2, d: 0.5, p: P.whisker }],
                kids: [{ name: s < 0 ? 'bigodeD2' : 'bigodeE2', pivot: [0, 2.1, 0], rot: [0.3, 0, -s * 0.9], boxes: [{ x: -0.22, y: 0, z: -0.22, w: 0.44, h: 2, d: 0.44, p: P.whisker }] }],
              })),
            },
            // capa: volume nos ombros (gola erguida em volta da cabeça) e o pano que cai atrás até o chão
            { name: 'ombD', pivot: [-3.4, -5, 0], boxes: [{ x: -2.6, y: -1.6, z: -3, w: 3.6, h: 3.6, d: 6.4, p: P.cape }] },
            { name: 'ombE', pivot: [3.4, -5, 0], boxes: [{ x: -1, y: -1.6, z: -3, w: 3.6, h: 3.6, d: 6.4, p: P.cape }] },
            ...(low ? [] : [{ name: 'gola', pivot: [0, -5.6, 2.6], boxes: [{ x: -5, y: -3.6, z: -0.4, w: 10, h: 4, d: 1.2, p: P.cape }] }]),
            {
              name: 'capa', pivot: [0, -4.6, 3.4], rot: [-0.1, 0, 0], boxes: [{ x: -6.6, y: 0, z: 0, w: 13.2, h: 8, d: 0.9, p: P.cape }],
              kids: [{ name: 'capa2', pivot: [0, 8, 0], rot: [-0.1, 0, 0], boxes: [{ x: -7.2, y: 0, z: -0.2, w: 14.4, h: 9.4, d: 0.9, p: P.cape }] }],
            },
            ...[-1, 1].map(s => ({ name: s < 0 ? 'abaD' : 'abaE', pivot: [s * 5.6, -4, -0.6], rot: [-0.15, 0, -s * 0.14], boxes: [{ x: -0.45, y: 0, z: -2.0, w: 0.9, h: 15.5, d: 5.6, p: P.cape }] })),
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
    const cape = (k = 1) => { r('capa', 0.04 * Math.sin(t * 2.1) * k, 0, 0.02 * Math.sin(t * 1.7) * k); r('capa2', 0.05 + 0.05 * Math.sin(t * 2.1 - 0.8) * k, 0, 0); r('bigodeD', 0, 0, 0.1 * Math.sin(t * 1.3)); r('bigodeE', 0, 0, -0.1 * Math.sin(t * 1.3 + 0.5)); };

    if (mode === 'parado') {
      // corcunda, mãos juntas na frente esfregando por baixo da capa, olho de lado, risadinha
      r('bracoD', -0.5, 0.4, 0); r('anteD', -1.0, 0, 0); r('bracoE', -0.5, -0.4, 0); r('anteE', -1.0, 0, 0);
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
  const vs = [{ seed: 2, skin: [214, 184, 92], cape: [232, 104, 52], robe: [100, 186, 222] }, { seed: 7, skin: [206, 176, 88], cape: [210, 84, 44], robe: [86, 170, 210] }];
  const def = v => ({ def: rigDef(painters(v), v, false), low: rigDef(painters(v), v, true) });
  window.MOBS.push({
    id: 'babidi', nome: 'Babidi', bioma: 'Vilões do mundo', tier: 'Mago · chefe da nave',
    variants: vs.map((v, i) => ({ nome: ['Babidi', 'Babidi (capa gasta)'][i], v, ...def(v) })),
    randomVariant: () => { const v = { ...vs[Math.floor(Math.random() * 2)], seed: Math.floor(Math.random() * 1e5) }; return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['paparapa', 'Paparapa'], ['controle', 'Controle Majin']],
    cam: 4.5, lookY: 0.9, lookZ: { paparapa: -3, controle: -2.5 }, camFar: { paparapa: 9, controle: 8 }, camYaw: { paparapa: -1.15, controle: -1.0 }, refX: 2.2,
    info: {
      'Tamanho': '1,5 bloco, corcunda (o cabeção é quase metade)',
      'Vida': '60 (fraco no corpo). Chefe do último andar da nave: luta escondido atrás dos que ele controla e se teleporta quando você chega perto.',
      'Jeito': 'Só na nave dele. Antes da luta ele já marcou 2 ou 3 mobs do andar com o M (eles lutam do lado dele). Vencer o Yakon e a dupla Spopovich/Yamu abre a porta dele.',
      'Paparapa': 'Braços pra cima e o círculo de magia acende em volta de você: 1,6 s pra sair. Se ficar, incha e explode por dentro (dano alto, em pacotes no jogo).',
      'Controle Majin': 'O fio escuro procura a sua cabeça (dá pra quebrar a linha de visão). Em mob: vira aliado dele com o M na testa. Em jogador: 4 s com o M aceso, controles trocados e o golpe acerta os seus amigos.',
      'Solta': 'Livro de magia do Babidi (ensina Paparapa), capa; raro: Alma do Babidi. Vencer ele no fim da nave libera o caminho do Majin Boo.',
      'Visual': 'Do desenho: cabeção amarelo enrugado com pelinhos, olho verde de lado, nariz de camelo, bigodinho caindo, regata preta, saia azul-clara com o M e capa laranja.',
    },
  });
})();
