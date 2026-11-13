// Peixe gigante (rios e lagos de Paozu). O peixão do desenho que o Goku pequeno pescava com o rabo e carregava
// nas costas: corpo redondo, beiçola, olhão do lado, nadadeiras grandes. Uns 5 blocos de comprimento.
// Golpes: Salto Esmagador (afunda, explode pra fora d'água num arco e cai em cima de você) e Abocanhada
// (abre o bocão, puxa a água num redemoinho, morde e cospe você longe).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const V3 = (x, y, z) => new THREE.Vector3(x, y, z);

  const KINDS = {
    cinza: { back: [96, 124, 150], belly: [236, 236, 228], fin: [80, 104, 132], lip: [206, 150, 140] },
    carpa: { back: [214, 96, 60], belly: [248, 226, 196], fin: [230, 140, 90], lip: [240, 170, 150] },
    bagre: { back: [120, 128, 70], belly: [226, 222, 176], fin: [100, 104, 60], lip: [190, 170, 120] },
  };
  function variant(seed) {
    const R = FX.rnd(seed);
    const kinds = Object.keys(KINDS);
    return { seed, kind: kinds[Math.floor(R() * kinds.length)], spots: R() < 0.5, hook: R() < 0.25 };
  }

  function painters(v) {
    const S = v.seed, C = KINDS[v.kind];
    const shade = (c, q, s, lo = 0.84, hi = 1.12) => cel(c, 0.62 - 0.38 * q.ny + 0.1 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0), lo, hi);
    // escamas: meias-luas em fileiras desencontradas, a borda escura de cada uma no lado de trás
    const scale = (q, c) => {
      if (q.f === 'north' || q.f === 'south') return c;
      const u = q.f === 'top' || q.f === 'bottom' ? q.ax : q.ay;
      const row = Math.floor(u / 2.2), zz = q.az / 2.6 + (row % 2) * 0.5;
      const d = fr(zz);
      return d > 0.8 ? mul(c, 0.86) : c;
    };
    const P = {};
    P.body = q => {
      const belly = q.f === 'bottom' ? 1 : q.f === 'top' ? 0 : Math.max(0, Math.min(1, (q.ny - 0.5) / 0.25));
      let c = shade(mix(C.back, C.belly, belly), q, 1);
      if (v.spots && belly < 0.3 && n3(q.ax * 0.5, q.ay * 0.5, q.az * 0.5, 60 + S) > 0.7) c = mul(C.back, 0.6);
      // linha lateral
      if ((q.f === 'west' || q.f === 'east') && Math.abs(q.ny - 0.45 - 0.05 * Math.sin(q.az * 0.3)) < 0.03) c = mul(c, 0.8);
      return scale(q, c);
    };
    P.head = q => {
      const belly = q.f === 'bottom' ? 1 : q.f === 'top' ? 0 : Math.max(0, Math.min(1, (q.ny - 0.55) / 0.25));
      let c = shade(mix(C.back, C.belly, belly), q, 2);
      if (q.f === 'west' || q.f === 'east') {
        // olhão de desenho: branco grande, pupila preta com brilho, e a guelra atrás
        const ex = q.pz - q.b.d * 0.42, ey = q.py - q.b.h * 0.34;
        const d = Math.hypot(ex, ey);
        if (d < 2.4) c = [250, 250, 246];
        if (Math.hypot(ex + 0.4, ey + 0.2) < 1.3) c = [16, 16, 22];
        if (Math.hypot(ex + 0.9, ey + 0.8) < 0.45) c = [255, 255, 255];
        if (Math.abs(q.pz - q.b.d + 1.2) < 0.4 && q.ny > 0.2 && q.ny < 0.85) c = mul(c, 0.7);
      }
      return c;
    };
    P.lip = q => shade(C.lip, q, 3, 0.86, 1.1);
    P.mouth = q => q.f === 'top' || q.f === 'bottom' ? shade([150, 50, 64], q, 4) : shade(C.lip, q, 5);
    // nadadeira: membrana com raios, recortada (o pintor devolve nada fora do desenho)
    const finShape = (q, shape) => {
      if (q.f === 'top' || q.f === 'bottom' || q.f === 'north' || q.f === 'south') return null;
      const u = q.pz / q.b.d, w = q.py / q.b.h;
      if (!shape(u, w)) return null;
      let c = shade(C.fin, q, 6, 0.8, 1.2);
      if (fr(u * 7 + w * 0.6) < 0.18) c = mul(c, 0.78);
      return c;
    };
    P.tailFin = q => finShape(q, (u, w) => { const fork = 0.45 * (1 - Math.abs(w - 0.5) * 2); return u > fork * 0.9 && u < 1 - 0.25 * Math.abs(w - 0.5) || u < 0.35 && Math.abs(w - 0.5) < 0.18; });
    P.dorsal = q => finShape(q, (u, w) => w > 1 - (1 - u) * 1.05 + 0.1 * Math.sin(u * 18));
    P.pect = q => { if (q.f === 'west' || q.f === 'east' || q.f === 'north' || q.f === 'south') return null; const u = q.pz / q.b.d, w = q.px / q.b.w; if (w > 1 - u * 0.9) return null; let c = shade(C.fin, q, 7, 0.8, 1.2); if (fr(w * 6) < 0.2) c = mul(c, 0.8); return c; };
    P.whisker = q => shade(mul(C.lip, 0.8), q, 8);
    P.hook = q => [190, 190, 200];
    return P;
  }

  function rigDef(P, v, low) {
    const whiskers = v.kind === 'bagre' && !low ? [-1, 1].map(s => ({ x: s * 6.2 - 0.4, y: 1, z: -14, w: 0.8, h: 0.8, d: 9, p: P.whisker })) : [];
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'corpo', pivot: [0, 0, 0], boxes: [
          { x: -7, y: -8, z: -12, w: 14, h: 16, d: 22, p: P.body },
          ...(low ? [] : [{ x: -6.2, y: -9, z: -9, w: 12.4, h: 1.2, d: 16, p: P.body }, { x: -6.2, y: 7.8, z: -9, w: 12.4, h: 1.2, d: 16, p: P.body }]),
        ], kids: [
          {
            name: 'cabeca', pivot: [0, 0, -12], boxes: [{ x: -6.5, y: -7, z: -10, w: 13, h: 13.5, d: 10, p: P.head }, ...whiskers],
            kids: [
              { name: 'labioS', pivot: [0, 1.5, -10], boxes: [{ x: -5, y: -2.2, z: -2.4, w: 10, h: 3, d: 3, p: P.lip }] },
              { name: 'labioI', pivot: [0, 3.2, -6], boxes: [{ x: -4.6, y: 0, z: -6.6, w: 9.2, h: 3.2, d: 6.8, p: P.mouth }, { x: -5, y: -0.4, z: -7.2, w: 10, h: 2.4, d: 2.2, p: P.lip }] },
              ...(v.hook && !low ? [{ name: 'anzol', pivot: [-4.6, 2.5, -11], boxes: [{ x: 0, y: 0, z: 0, w: 0.5, h: 2.4, d: 0.5, p: P.hook }, { x: 0, y: 2, z: -1, w: 0.5, h: 0.5, d: 1.4, p: P.hook }] }] : []),
            ],
          },
          { name: 'dorsal', pivot: [0, -8, -6], boxes: [{ x: -0.3, y: -9, z: 0, w: 0.6, h: 9, d: 14, p: P.dorsal }] },
          { name: 'peitD', pivot: [-7, 3, -9], rot: [0, 0, 0.5], boxes: [{ x: -7, y: 0, z: 0, w: 7, h: 0.6, d: 7, p: P.pect }] },
          { name: 'peitE', pivot: [7, 3, -9], rot: [0, 0, -0.5], boxes: [{ x: 0, y: 0, z: 0, w: 7, h: 0.6, d: 7, p: P.pect, mirror: true }] },
          {
            name: 'cauda1', pivot: [0, 0, 10], boxes: [{ x: -5, y: -6, z: 0, w: 10, h: 12, d: 9, p: P.body }],
            kids: [{
              name: 'cauda2', pivot: [0, 0, 9], boxes: [{ x: -3, y: -4, z: 0, w: 6, h: 8, d: 7, p: P.body }],
              kids: [{ name: 'rabo', pivot: [0, 0, 6], boxes: [{ x: -0.3, y: -13, z: 0, w: 0.6, h: 26, d: 12, p: P.tailFin }] }],
            }],
          },
        ],
      }],
    };
  }

  // superfície do lago: ondulação, reflexo do céu, e espuma em volta do peixe e nos estouros
  function water() {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false,
      uniforms: { uT: { value: 0 }, uF: { value: new THREE.Vector3() }, uS: { value: new THREE.Vector4(0, 0, 0, 0) } },
      vertexShader: 'varying vec3 vW; void main(){ vec4 w=modelMatrix*vec4(position,1.0); vW=w.xyz; gl_Position=projectionMatrix*viewMatrix*w; }',
      fragmentShader: `varying vec3 vW; uniform float uT; uniform vec3 uF; uniform vec4 uS;
        float h(vec2 p){ return fract(sin(dot(p, vec2(41.3, 289.1)))*43758.5); }
        float n(vec2 x){ vec2 i=floor(x), f=fract(x); f=f*f*(3.0-2.0*f); return mix(mix(h(i),h(i+vec2(1,0)),f.x),mix(h(i+vec2(0,1)),h(i+vec2(1,1)),f.x),f.y); }
        void main(){
          vec2 p = vW.xz;
          float r0 = length(p) / 9.0; if (r0 > 1.0) discard;
          float w = n(p * 1.6 + vec2(uT * 0.5, uT * 0.3)) * 0.6 + n(p * 4.0 - vec2(uT * 0.8, -uT * 0.4)) * 0.4;
          vec3 c = mix(vec3(0.10, 0.34, 0.46), vec3(0.32, 0.62, 0.74), w);
          c += vec3(0.5) * smoothstep(0.72, 0.8, w) * 0.5;
          // espuma: anel em volta do peixe
          float df = length(p - uF.xz);
          float foam = smoothstep(0.35, 0.0, abs(df - uF.y - 0.2 * sin(uT * 3.0)) - 0.15 * n(p * 6.0 + uT)) * 0.8;
          // estouro: anéis que abrem a partir do ponto de queda
          float ds = length(p - uS.xy);
          foam += uS.w * smoothstep(0.3, 0.0, abs(ds - uS.z) - 0.2 * n(p * 5.0)) ;
          foam += uS.w * smoothstep(0.4, 0.0, abs(ds - uS.z * 0.55) - 0.2 * n(p * 7.0 + 3.0)) * 0.6;
          c = mix(c, vec3(0.94, 0.97, 1.0), clamp(foam, 0.0, 1.0));
          float edge = smoothstep(1.0, 0.92, r0);
          gl_FragColor = vec4(c, 0.86 * edge);
        }`,
    });
    const m = new THREE.Mesh(new THREE.PlaneGeometry(18, 18), mat); m.rotation.x = -Math.PI / 2; m.frustumCulled = false;
    m.at = (y, t, fish, foamR, splash) => { m.visible = true; m.position.set(0, y, -2); mat.uniforms.uT.value = t; mat.uniforms.uF.value.set(fish.x, foamR, fish.z); mat.uniforms.uS.value.copy(splash); };
    return m;
  }
  const WY = 0.75;
  const splashSpec = (seed, n, up) => ({ n, seed, palette: [[230, 244, 255], [180, 220, 240], [255, 255, 255], [140, 200, 230]], size: [0.03, 0.08], drag: 1.2, grav: 9, life: 1.2, spread: 0.15, cone: Rr => { const a = Rr() * 6.28, rr = Rr() * 2.5; return [Math.cos(a) * rr, up * (0.6 + Rr() * 0.7), Math.sin(a) * rr]; } });

  const K = 1.2;
  function anim(P, t, mode, a, obj, e, ctx) {
    obj.scale.set(-K / 16, -K / 16, K / 16);
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const at = (n, off) => { obj.updateMatrixWorld(true); return P[n].obj.localToWorld(off.clone()); };
    const swim = (sp, amp) => {
      const s = t * sp;
      r('cabeca', 0, 0.06 * amp * Math.sin(s), 0); r('cauda1', 0, -0.25 * amp * Math.sin(s - 0.8), 0); r('cauda2', 0, -0.35 * amp * Math.sin(s - 1.6), 0); r('rabo', 0, -0.45 * amp * Math.sin(s - 2.4), 0);
      r('peitD', 0, 0.3 * Math.sin(s * 1.3), 0); r('peitE', 0, -0.3 * Math.sin(s * 1.3), 0);
      r('labioI', 0.08 + 0.08 * Math.sin(s * 0.7), 0, 0);
    };
    const splash = new THREE.Vector4(0, 0, 0, 0);
    let fish = V3(0, 0, 0), foamR = 2.2;
    obj.position.set(0, WY - 0.05, 0);

    if (mode === 'parado' || mode === 'andar') {
      const sp = mode === 'andar' ? 9 : 4, amp = mode === 'andar' ? 1 : 0.6;
      swim(sp, amp);
      obj.position.y += 0.08 * Math.sin(t * 1.4); obj.rotation.z = 0.04 * Math.sin(t * 1.1);
      fish = obj.position.clone();
      ctx.get('agua', water).at(WY, t, fish, foamR, splash);
      return;
    }

    if (mode === 'salto') {
      const T = t % 4.2;
      const R = ctx.ref; const RZ = -6; R.place(0, RZ, 0);
      ctx.get('beira', () => { const g = new THREE.Mesh(new THREE.CylinderGeometry(0.9, 1.1, 0.5, 20), new THREE.MeshLambertMaterial({ color: 0x8a7a64 })); return g; }).position.set(0, WY - 0.2, RZ);
      ctx.get('beira', () => 0).visible = true;
      R.place(0, RZ, WY + 0.05);
      // 0-0.7 afunda (só a nadadeira de cima corta a água); 0.7-1.7 sai num arco; 1.7 cai no alvo; 2.6 volta pra água
      const dive = seq([[0, 0], [0.6, 1, 'in'], [0.7, 1]], T);
      const fly = seq([[0.7, 0], [1.7, 1, 'io']], T), back = seq([[2.4, 0], [3.4, 1, 'io']], T);
      const z0 = 3, z1 = RZ + 0.5;
      let y = WY - 0.05 - 1.2 * dive, z = z0 * Math.min(1, T / 0.7);
      let pitch = 0;
      if (T >= 0.7) {
        z = z0 + (z1 - z0) * fly; y = WY - 1.25 + 1.25 * fly + 4.2 * Math.sin(Math.PI * fly) * (fly < 1 ? 1 : 0);
        pitch = 1.1 * Math.cos(Math.PI * fly) * (fly < 1 ? 1 : 0);
      }
      if (T >= 1.7) { z = z1 + (0 - z1) * back; y = WY - 0.05 + 0.3 * (1 - Math.min(1, (T - 1.7) / 0.3)) + 1.2 * Math.sin(Math.PI * back) * (back > 0 && back < 1 ? 1 : 0); pitch = -0.25 * (1 - Math.min(1, (T - 1.7) / 0.4)); }
      obj.position.set(0, y, z); obj.rotation.x = pitch;
      swim(T < 0.7 ? 14 : 7, 1);
      r('labioI', 0.5 * seq([[1.0, 0], [1.4, 1], [1.7, 0]], T), 0, 0);
      fish = obj.position.clone();
      // saída
      if (T > 0.7 && T < 2.0) ctx.get('saida', () => DBFX.grains(splashSpec(101, 90, 6))).at(V3(0, WY, z0 + 0.4), T - 0.7);
      // queda
      if (T > 1.7) {
        const k = (T - 1.7) / 1.4;
        splash.set(0, z1 + 2, k * 6, Math.max(0, 1 - k));
        ctx.get('queda', () => DBFX.grains(splashSpec(103, 140, 8))).at(V3(0, WY, z1), T - 1.7);
        ctx.get('coluna', FX.volume).set(V3(0, WY + 0.8, z1), 1.6, Math.min(1, k * 1.2), { fire: 0, seed: 9.1, dens: 8, rise: 0.6, smoke: [0.86, 0.93, 1.0] });
        if (k < 0.25) ctx.shake(1 - k * 4);
        const hit = seq([[1.7, 0], [1.85, 1, 'out'], [3.2, 1], [3.8, 0]], T);
        R.place(0, RZ - 1.5 * hit, WY + 0.05 + 0.5 * Math.sin(Math.PI * Math.min(1, (T - 1.7) / 0.5))); R.lean(-1.3 * hit);
      }
      foamR = T < 0.7 ? 1.4 : 2.2;
      ctx.get('agua', water).at(WY, T, fish, foamR, splash);
      return;
    }

    if (mode === 'abocanhar') {
      const T = t % 3.8;
      const R = ctx.ref; const RZ = -5.4;
      ctx.get('beira', () => { const g = new THREE.Mesh(new THREE.CylinderGeometry(0.9, 1.1, 0.5, 20), new THREE.MeshLambertMaterial({ color: 0x8a7a64 })); return g; }).position.set(0, WY - 0.2, RZ);
      ctx.get('beira', () => 0).visible = true;
      // ergue a cabeça pra fora, abre o bocão e puxa: a água vira redemoinho e o alvo escorrega pra boca;
      // 1.6 fecha (mordida); 2.2 cospe o alvo longe com um jato
      const rise = seq([[0, 0], [0.4, 1, 'out'], [2.6, 1], [3.2, 0, 'io']], T);
      const open = seq([[0.3, 0], [0.6, 1, 'out'], [1.55, 1], [1.62, 0, 'snap'], [2.1, 0], [2.2, 0.8, 'snap'], [2.6, 0.2]], T);
      obj.position.y += 0.7 * rise; obj.rotation.x = 0.45 * rise; obj.position.z += -1.8 * rise;
      swim(6, 0.6);
      r('labioI', 1.0 * open, 0, 0); r('labioS', -0.4 * open, 0, 0);
      const mouth = at('cabeca', V3(0, 1.5, -12));
      const pull = seq([[0.6, 0], [1.55, 1, 'in']], T), spit = seq([[2.2, 0], [2.9, 1, 'out']], T);
      const held = T >= 1.55 && T < 2.2;
      const p0 = V3(0, WY + 0.05, RZ);
      let rp = p0.clone().lerp(V3(mouth.x, WY + 0.05, mouth.z - 0.6), pull);
      if (held) rp = V3(mouth.x, WY + 0.1, mouth.z - 0.8);
      if (T >= 2.2) rp = V3(0, WY + 0.1 + 2.4 * Math.sin(Math.PI * spit), (mouth.z - 0.8) + (RZ - 2 - mouth.z) * spit);
      R.place(rp.x, rp.z, rp.y); R.lean(-0.6 * pull * (T < 2.2 ? 1 : 0) - 1.2 * spit * (1 - seq([[3.2, 0], [3.8, 1]], T)));
      R.armsOut(0.7 * pull);
      fish = obj.position.clone();
      // redemoinho: água girando entrando na boca
      if (T > 0.5 && T < 1.65) {
        ctx.get('redemoinho', () => {
          const n = 160, R2 = FX.rnd(107), pos = new Float32Array(n * 3), col = new Float32Array(n * 3), it = [];
          for (let i = 0; i < n; i++) { it.push({ a: R2() * 6.28, rr: 1 + R2() * 3, ph: R2(), sp: 0.8 + R2() * 0.6 }); const c = R2() < 0.4 ? [1, 1, 1] : [0.7, 0.88, 0.98]; col.set(c, i * 3); }
          const geo = new THREE.BufferGeometry(); geo.setAttribute('position', new THREE.BufferAttribute(pos, 3)); geo.setAttribute('color', new THREE.BufferAttribute(col, 3));
          const pts = new THREE.Points(geo, new THREE.PointsMaterial({ size: 0.17, vertexColors: true, transparent: true, opacity: 0.9, depthWrite: false }));
          pts.frustumCulled = false;
          pts.at = (c, tt) => { pts.visible = true; for (let i = 0; i < n; i++) { const p = it[i], f = fr(tt * p.sp + p.ph), rr = p.rr * (1 - f), ang = p.a + f * 7; pos.set([c.x + Math.cos(ang) * rr, WY + 0.05 + f * f * (c.y - WY), c.z + Math.sin(ang) * rr * 0.8 - (1 - f) * 0.5], i * 3); } geo.attributes.position.needsUpdate = true; };
          return pts;
        }).at(mouth, T);
        splash.set(mouth.x, mouth.z - 1, 1.2 + 0.3 * Math.sin(T * 9), 0.6);
      }
      if (T > 1.55 && T < 1.75) { ctx.shake(0.6); ctx.flash(mouth, 0.5); }
      if (T > 2.2 && T < 3.2) ctx.get('jato', () => DBFX.grains({ n: 90, seed: 109, palette: [[230, 244, 255], [180, 220, 240], [255, 255, 255]], size: [0.03, 0.07], drag: 1.5, grav: 6, life: 0.9, spread: 0.2, cone: Rr => [(Rr() - 0.5) * 1.2, 2 + Rr() * 2, -5 - Rr() * 3] })).at(mouth, T - 2.2);
      ctx.get('agua', water).at(WY, T, fish, 2.4, splash);
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(1), variant(8), variant(14)];
  fixed[0].kind = 'cinza'; fixed[0].hook = true; fixed[1].kind = 'carpa'; fixed[1].spots = true; fixed[2].kind = 'bagre'; fixed[2].spots = false;
  const def = v => ({ def: rigDef(painters(v), v, false), low: rigDef(painters(v), v, true) });
  window.MOBS.push({
    id: 'peixe_gigante', nome: 'Peixe gigante', bioma: 'Rios e florestas', tier: 'Médio · tier 7',
    variants: fixed.map((v, i) => ({ nome: ['Cinzão (com anzol)', 'Carpa vermelha', 'Bagre bigodudo'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Nadar'], ['salto', 'Salto Esmagador'], ['abocanhar', 'Abocanhada']],
    cam: 10, lookY: 1.2, lookZ: { salto: -2, abocanhar: -2.5 }, camFar: { salto: 13, abocanhar: 11 }, camYaw: { salto: -1.2, abocanhar: -1.05 }, refX: 4.5,
    info: {
      'Tamanho': '5 blocos de comprimento, 1,6 de altura',
      'Vida': '90 (tier 7). Neutro: só ataca quem entra na água perto dele ou bate nele.',
      'Jeito': 'Nada em rios e lagos de Paozu e no Mar do Sul. Dá pra ver a nadadeira cortando a água. Pescar com isca de carne puxa ele pra fora (a luta começa na beira).',
      'Salto Esmagador': 'Afunda (some a nadadeira: é o aviso), explode pra fora num arco de até 9 blocos e cai em cima de quem está na beira. Onda de água empurra.',
      'Abocanhada': 'Abre o bocão e puxa a água: quem está perto escorrega pra boca. Mordida e depois cospe longe. Correr pra trás na hora do redemoinho escapa.',
      'Solta': 'Filé de peixe gigante (assado alimenta MUITO, dá pra carregar nas costas como o Goku), escama grande; raro: anzol antigo.',
      'Variações': 'Cinzão, carpa vermelha ou bagre bigodudo; pintas e anzol preso na boca sorteados.',
    },
  });
})();
