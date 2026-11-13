// Chifrudo, o dino médio (Paozu, Terras do Dragão e Deserto). Quatro patas, cabeçorra com gola e três chifres,
// bico, e a cauda termina numa clava cheia de pontas. Estilo do desenho: couro liso de duas cores, olho redondo bravo.
// Golpes: Chifrada (bate a pata no chão, abaixa a cabeça, corre e arremessa o alvo por cima) e Clava (gira o corpo
// e varre com a cauda).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const V3 = (x, y, z) => new THREE.Vector3(x, y, z);

  const SKINS = [[186, 120, 70], [80, 140, 140], [140, 100, 160], [130, 140, 70], [170, 80, 64], [96, 120, 170]];
  const FRILLS = [[230, 190, 70], [220, 90, 60], [70, 170, 150], [240, 220, 190], [200, 120, 200]];
  function variant(seed) {
    const R = FX.rnd(seed);
    const pick = a => a[Math.floor(R() * a.length)];
    const skin = pick(SKINS).map(c => c * (0.92 + R() * 0.12));
    return { seed, skin, belly: mix(skin, [244, 226, 176], 0.7), frill: pick(FRILLS), rings: 2 + Math.floor(R() * 2), broken: R() < 0.3, plates: R() < 0.6, scar: R() < 0.3 };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.84, hi = 1.1) => {
      const f = 0.62 - 0.38 * q.ny + 0.12 * (n3(q.ax * 0.25, q.ay * 0.25, q.az * 0.25, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const hide = (q, s = 1) => {
      const under = q.f === 'bottom' || (q.f !== 'top' && q.ny > 0.66);
      let c = shade(under ? v.belly : v.skin, q, s);
      if (under && fr(q.az * 0.42) < 0.14) c = mul(c, 0.84);
      // manchas grandes nas costas, poucas, como pintadas à mão
      if (!under && n3(q.ax * 0.16, q.ay * 0.16, q.az * 0.16, 50 + S) > 0.66) c = mul(c, 0.82);
      return c;
    };
    const limb = (q, s = 6) => { let c = shade(v.skin, q, s); if (q.ny > 0.8 && q.f !== 'top') c = mix(c, v.belly, 0.3); return c; };
    const bone = q => cel([236, 226, 196], 0.35 + 0.65 * (1 - q.ny) + (q.f === 'top' ? 0.2 : 0), 0.8, 1.1);
    const P = { hide, limb, bone };
    P.head = q => {
      let c = hide(q, 2);
      if (q.f === 'west' || q.f === 'east') {
        const x = q.pz, y = q.py, ex = 3.4, ey = 2.3;
        if (Math.hypot((x - ex) / 1.2, y - ey) < 1) { c = [246, 244, 232]; if (Math.hypot(x - (ex - 0.5), y - ey - 0.1) < 0.55) c = [18, 16, 14]; }
        if (y < ey - 0.8 && y > ey - 1.5 && Math.abs(x - ex) < 1.6) c = mul(v.skin, 0.55);
        if (v.scar && q.f === 'east' && Math.abs(y - (4.6 - (x - 5) * 0.5)) < 0.3 && x > 4.5 && x < 8) c = mul(v.skin, 0.6);
      }
      return c;
    };
    // gola: borda de cima em ondas, anéis coloridos e pontinhas de osso na beirada
    P.frill = q => {
      // escudo: elipse que nasce atrás da cabeça e abre pra cima, beirada em ondas
      const cx = (q.px - 7.5) / 7.5, cy = (8.2 - q.py) / 8.2;
      const ang = Math.atan2(cx, cy);
      const r = Math.hypot(cx, cy) * 7.0;
      const edge = 6.4 + 0.55 * Math.cos(ang * 9);
      if (r > edge || q.f === 'top') return null;
      let c = shade(v.skin, q, 8);
      if (q.f === 'north' || q.f === 'south') {
        const ring = fr(r / 7.0 * v.rings);
        if (ring > 0.6 && ring < 0.82) c = shade(v.frill, q, 9);
        if (r > edge - 0.9) c = bone(q);
        if (q.f === 'north') c = mul(c, 0.92);
      }
      return c;
    };
    P.beak = q => cel([150, 132, 112], 0.45 + 0.5 * (1 - q.ny) + (q.f === 'top' ? 0.2 : 0), 0.8, 1.3);
    P.jaw = q => (q.f === 'top' ? [140, 70, 76] : hide(q, 4));
    P.nail = q => cel([70, 60, 56], 0.5 + 0.4 * (1 - q.ny), 0.8, 1.3);
    P.club = q => { let c = shade(mul(v.skin, 0.85), q, 10); if (n3(q.ax * 0.5, q.ay * 0.5, q.az * 0.5, 60 + S) > 0.62) c = mul(c, 0.8); return c; };
    P.plate = q => shade(mix(v.skin, v.frill, 0.4), q, 11);
    return P;
  }

  function rigDef(P, v) {
    const leg = (side, front) => {
      const s = side, k = (front ? 'F' : 'T') + (s < 0 ? 'D' : 'E');
      const w = front ? 4.4 : 5.4;
      return {
        name: 'perna' + k, pivot: [s * 4.6, front ? 2.4 : 1.8, front ? -6 : 6], boxes: [{ x: -w / 2, y: -1.5, z: -w / 2, w, h: 8, d: w, p: P.limb }],
        kids: [{
          name: 'canela' + k, pivot: [0, 6.5, 0], boxes: [{ x: -w / 2 + 0.3, y: 0, z: -w / 2 + 0.3, w: w - 0.6, h: 6, d: w - 0.6, p: P.limb }],
          kids: [{
            name: 'pe' + k, pivot: [0, 6, 0], boxes: [
              { x: -w / 2 - 0.2, y: 0, z: -w / 2 - 0.6, w: w + 0.4, h: 2, d: w + 0.6, p: P.limb },
              { x: -w / 2, y: 0.7, z: -w / 2 - 1.2, w: 1.2, h: 1.3, d: 0.8, p: P.nail },
              { x: -0.6, y: 0.7, z: -w / 2 - 1.3, w: 1.2, h: 1.3, d: 0.9, p: P.nail },
              { x: w / 2 - 1.2, y: 0.7, z: -w / 2 - 1.2, w: 1.2, h: 1.3, d: 0.8, p: P.nail },
            ],
          }],
        }],
      };
    };
    const plates = v.plates ? [[-6, 1.6], [-1.5, 2.2], [3, 1.8], [7, 1.4]].map(([z, h]) => ({ x: -0.6, y: -5 - h, z: z - 1, w: 1.2, h, d: 2.2, p: P.plate })) : [];
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'quadril', pivot: [0, 7.1, 0], boxes: [
            { x: -6, y: -5, z: -9, w: 12, h: 9.5, d: 18, p: P.hide },
            { x: -5.4, y: -4.2, z: -10.2, w: 10.8, h: 8, d: 2, p: P.hide },
            ...plates,
          ], kids: [
            {
              name: 'cabeca', pivot: [0, -3.2, -9.6], rot: [0.05, 0, 0], boxes: [
                { x: -4.2, y: -4.2, z: -9, w: 8.4, h: 6.6, d: 9.4, p: P.head },
                { x: -2.5, y: -2.6, z: -12.2, w: 5, h: 4.4, d: 3.4, p: P.beak },
              ],
              kids: [
                { name: 'gola', pivot: [0, -3.0, -0.6], rot: [-0.22, 0, 0], boxes: [{ x: -7.5, y: -11.5, z: -0.6, w: 15, h: 12.5, d: 1.2, p: P.frill }] },
                { name: 'chifreD', pivot: [-2.4, -4, -5.5], rot: [0.75, 0.15, 0], boxes: [{ x: -0.7, y: -5.6, z: -0.7, w: 1.4, h: 5.8, d: 1.4, p: P.bone }, { x: -0.4, y: -7.4, z: -0.4, w: 0.8, h: 2, d: 0.8, p: P.bone }] },
                { name: 'chifreE', pivot: [2.4, -4, -5.5], rot: [0.75, -0.15, 0], boxes: [{ x: -0.7, y: -5.6, z: -0.7, w: 1.4, h: 5.8, d: 1.4, p: P.bone }, ...(v.broken ? [] : [{ x: -0.4, y: -7.4, z: -0.4, w: 0.8, h: 2, d: 0.8, p: P.bone }])] },
                { name: 'chifreN', pivot: [0, -2.6, -11], rot: [0.35, 0, 0], boxes: [{ x: -0.6, y: -2.6, z: -0.6, w: 1.2, h: 2.8, d: 1.2, p: P.bone }] },
                { name: 'mandibula', pivot: [0, 2.4, -2], boxes: [{ x: -3.4, y: 0, z: -9, w: 6.8, h: 2.2, d: 9, p: P.jaw }, { x: -2, y: -0.5, z: -10.4, w: 4, h: 2.4, d: 1.8, p: P.beak }] },
              ],
            },
            leg(-1, true), leg(1, true), leg(-1, false), leg(1, false),
            {
              name: 'cauda1', pivot: [0, -1, 8.6], rot: [0.08, 0, 0], boxes: [{ x: -3.2, y: -3, z: 0, w: 6.4, h: 5.6, d: 7.4, p: P.hide }],
              kids: [{
                name: 'cauda2', pivot: [0, 0, 7], boxes: [{ x: -2, y: -1.8, z: 0, w: 4, h: 3.6, d: 7.4, p: P.hide }],
                kids: [{
                  name: 'clava', pivot: [0, 0, 7.2], boxes: [
                    { x: -3.6, y: -2.4, z: 0, w: 7.2, h: 4.6, d: 5, p: P.club },
                    { x: -4.6, y: -1, z: 1.4, w: 1.2, h: 1.6, d: 1.6, p: P.bone }, { x: 3.4, y: -1, z: 1.8, w: 1.2, h: 1.6, d: 1.6, p: P.bone },
                    { x: -0.7, y: -3.4, z: 2.2, w: 1.4, h: 1.2, d: 1.5, p: P.bone },
                  ],
                }],
              }],
            },
          ],
        },
      ],
    };
  }
  function rigLow(P, v) {
    const leg = (s, front) => {
      const k = (front ? 'F' : 'T') + (s < 0 ? 'D' : 'E'), w = front ? 4.4 : 5.4;
      return { name: 'perna' + k, pivot: [s * 4.6, front ? 2.4 : 1.8, front ? -6 : 6], boxes: [{ x: -w / 2, y: -1.5, z: -w / 2, w, h: 15.5, d: w, p: P.limb }] };
    };
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'quadril', pivot: [0, 7.1, 0], boxes: [{ x: -6, y: -5, z: -10, w: 12, h: 9.5, d: 19, p: P.hide }], kids: [
          { name: 'cabeca', pivot: [0, -3.2, -9.6], rot: [0.05, 0, 0], boxes: [{ x: -4.2, y: -4.2, z: -12, w: 8.4, h: 8.6, d: 12.2, p: P.head }], kids: [{ name: 'gola', pivot: [0, -3.0, -0.6], rot: [-0.22, 0, 0], boxes: [{ x: -7.5, y: -11.5, z: -0.6, w: 15, h: 12.5, d: 1.2, p: P.frill }] }, { name: 'chifreD', pivot: [-2.4, -4, -5.5], rot: [0.75, 0.15, 0], boxes: [{ x: -0.7, y: -7, z: -0.7, w: 1.4, h: 7, d: 1.4, p: P.bone }] }, { name: 'chifreE', pivot: [2.4, -4, -5.5], rot: [0.75, -0.15, 0], boxes: [{ x: -0.7, y: -7, z: -0.7, w: 1.4, h: 7, d: 1.4, p: P.bone }] }] },
          leg(-1, true), leg(1, true), leg(-1, false), leg(1, false),
          { name: 'cauda1', pivot: [0, -1, 8.6], boxes: [{ x: -2.8, y: -2.6, z: 0, w: 5.6, h: 4.8, d: 14, p: P.hide }], kids: [{ name: 'clava', pivot: [0, 0, 14], boxes: [{ x: -3.6, y: -2.4, z: 0, w: 7.2, h: 4.6, d: 5, p: P.club }] }] },
        ],
      }],
    };
  }

  // rastro da clava: faixa larga de poeira que acompanha o arco da cauda
  function sweep() {
    const SEG = 30, pos = new Float32Array((SEG + 1) * 6), al = new Float32Array((SEG + 1) * 2), rr = new Float32Array((SEG + 1) * 2), idx = [];
    for (let s = 0; s < SEG; s++) { const a = s * 2; idx.push(a, a + 1, a + 2, a + 1, a + 3, a + 2); }
    for (let s = 0; s <= SEG; s++) { rr[s * 2] = 0; rr[s * 2 + 1] = 1; }
    const geo = new THREE.BufferGeometry(); geo.setIndex(idx);
    geo.setAttribute('position', new THREE.BufferAttribute(pos, 3)); geo.setAttribute('al', new THREE.BufferAttribute(al, 1)); geo.setAttribute('rr', new THREE.BufferAttribute(rr, 1));
    const m = new THREE.Mesh(geo, new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, side: THREE.DoubleSide,
      vertexShader: 'attribute float al; attribute float rr; varying float vA; varying float vR; void main(){ vA=al; vR=rr; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: 'varying float vA; varying float vR; void main(){ vec3 c = mix(vec3(0.62,0.5,0.36), vec3(0.96,0.9,0.78), smoothstep(0.3,1.0,vR)); gl_FragColor = vec4(c, vA * (0.25 + 0.75 * vR)); }',
    }));
    m.frustumCulled = false;
    m.at = (c, a0, a1, R1, R2, y, age) => {
      m.visible = true;
      const lead = Math.min(1, age * 3.2);
      for (let s = 0; s <= SEG; s++) {
        const f = s / SEG, ang = a0 + (a1 - a0) * f * lead;
        pos.set([c.x + Math.sin(ang) * R1, y, c.z + Math.cos(ang) * R1, c.x + Math.sin(ang) * R2, y + 0.1, c.z + Math.cos(ang) * R2], s * 6);
        const a = Math.max(0, 1 - (1 - f) * 1.6) * (1 - age) * Math.sin(Math.PI * Math.min(1, f * 1.05));
        al[s * 2] = al[s * 2 + 1] = a;
      }
      geo.attributes.position.needsUpdate = true; geo.attributes.al.needsUpdate = true;
    };
    return m;
  }

  const TROT = [[0, -0.4], [0.5, 0.4]];
  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const breath = loop([[0, 0], [0.45, 1], [0.999, 0]], t / 2.6);
    const legs = (ph, amp) => {
      for (const [k, off] of [['FD', 0], ['TE', 0.02], ['FE', 0.5], ['TD', 0.52]]) {
        const sw = loop(TROT, ph + off) * amp, lift = Math.max(0, Math.sin((ph + off) * Math.PI * 2));
        r('perna' + k, sw, 0, 0); r('canela' + k, 0.5 * lift * amp, 0, 0); r('pe' + k, -0.3 * lift * amp, 0, 0);
      }
    };

    if (mode === 'parado') {
      r('quadril', -0.02 * breath, 0, 0);
      // de vez em quando abaixa a cabeça e fuça o chão
      const graze = seq([[0.5, 0], [0.6, 1, 'io'], [0.85, 1], [0.95, 0, 'io']], fr(t / 6));
      r('cabeca', 0.5 * graze + 0.04 * breath, 0.1 * Math.sin(t * 0.7) * (1 - graze), 0);
      r('mandibula', 0.15 * graze * Math.max(0, Math.sin(t * 9)), 0, 0);
      r('cauda1', 0, 0.12 * Math.sin(t * 1.1), 0); r('cauda2', 0, 0.16 * Math.sin(t * 1.1 - 0.8), 0);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 0.9;
      legs(ph, 1);
      P.quadril.obj.position.y += 0.4 * Math.abs(Math.sin(ph * Math.PI * 2));
      r('quadril', 0, 0, 0.03 * Math.sin(ph * Math.PI * 2));
      r('cabeca', 0.04 * Math.sin(ph * Math.PI * 4), 0.05 * Math.sin(ph * Math.PI * 2), 0);
      r('cauda1', 0, 0.12 * Math.sin(ph * Math.PI * 2 + 1), 0); r('cauda2', 0, 0.16 * Math.sin(ph * Math.PI * 2), 0);
      return;
    }

    if (mode === 'chifrada') {
      const T = t % 3.8;
      const R = ctx.ref; const RZ = -6.4; R.place(0, RZ, 0);
      // bate a pata duas vezes, abaixa a cabeça, corre e joga o alvo pra cima com os chifres
      const paw1 = seq([[0.1, 0], [0.25, 1, 'out'], [0.38, 0, 'in']], T), paw2 = seq([[0.48, 0], [0.62, 1, 'out'], [0.75, 0, 'in']], T);
      const low = seq([[0.75, 0], [1.0, 1, 'out'], [1.62, 1], [1.7, 0]], T);
      const run = seq([[1.0, 0], [1.62, 1, 'in']], T);
      const toss = seq([[1.6, 0], [1.75, 1, 'snap'], [2.2, 1], [2.8, 0, 'io']], T);
      r('pernaFD', -0.7 * (paw1 + paw2), 0, 0); r('canelaFD', 0.8 * (paw1 + paw2), 0, 0);
      r('cabeca', 0.45 * low - 0.75 * toss, 0, 0.1 * Math.sin(T * 9) * (paw1 + paw2));
      r('mandibula', 0.35 * toss, 0, 0);
      if (run > 0 && run < 1) legs(T * 2.6, 1.6);
      r('quadril', 0.08 * low - 0.15 * toss, 0, 0);
      obj.position.z += (RZ + 2.0) * run;
      if (T > 1.0 && T < 1.75) obj.position.y += 0.12 * Math.abs(Math.sin(T * 16));
      // poeira da pata e do galope
      [0.25, 0.62].forEach((pt, i) => { if (T > pt && T < pt + 1.0) ctx.get('pata' + i, FX.volume).set(V3(-0.3, 0.2, -0.6), 0.32 + i * 0.06, (T - pt) / 1.0, { seed: 3.1 + i * 4.4, dens: 6, rise: 0.4, smoke: [0.8, 0.68, 0.5] }); });
      if (T > 1.05 && T < 2.6) {
        [0, 1, 2, 3].forEach(i => {
          const st = 1.05 + i * 0.14, age = (T - st) / 1.0;
          if (age <= 0 || age >= 1) return;
          const z = (RZ + 2.0) * seq([[1.0, 0], [1.62, 1, 'in']], st) + 0.8;
          ctx.get('galope' + i, FX.volume).set(V3((i % 2 ? 0.4 : -0.4), 0.3, z), 0.45 + i * 0.05, age, { seed: 9.7 + i * 3.3, dens: 6, rise: 0.35, smoke: [0.82, 0.7, 0.52] });
        });
      }
      // o alvo voa por cima e cai atrás
      if (T > 1.62) {
        const fly = seq([[1.62, 0], [2.35, 1, 'out']], T), down = seq([[2.35, 0], [2.6, 1, 'in']], T);
        R.place(0.3 * fly, RZ + 1.2 - 2.6 * fly, 2.2 * Math.sin(Math.PI * Math.min(1, fly * 0.75)) * (1 - down));
        R.spin(-2.6 * fly); R.lean(-1.3 * Math.min(1, fly * 1.5));
        if (T < 1.8) ctx.shake(0.6);
        if (T > 2.55 && T < 3.4) { ctx.get('queda', FX.volume).set(V3(0.3, 0.25, RZ - 1.4), 0.6, (T - 2.55) / 0.85, { seed: 21.3, dens: 6, rise: 0.3, smoke: [0.82, 0.7, 0.52] }); }
      }
      return;
    }

    if (mode === 'clava') {
      const T = t % 3.2;
      const R = ctx.ref; R.place(-2.6, 2.6, 0);
      // gira o corpo de lado, arma a cauda e varre: a clava passa num arco baixo e arremessa pro lado
      const turn = seq([[0, 0], [0.5, 1, 'io'], [1.6, 1], [2.3, 0, 'io']], T);
      const cock = seq([[0.4, 0], [0.75, 1, 'out'], [0.8, 1]], T);
      const swing = seq([[0.8, 0], [0.98, 1, 'snap'], [1.5, 1], [2.3, 0, 'io']], T);
      const cw = cock * (1 - swing);
      r('quadril', 0, -0.5 * turn, 0);
      legs(T * 0.8, 0.3 * turn * (1 - swing));
      r('cauda1', -0.1 * cw, 0.5 * cw - 0.6 * swing, 0); r('cauda2', 0, 0.5 * cw - 0.7 * swing, 0); r('clava', 0, 0.4 * cw - 0.5 * swing, 0);
      r('cabeca', 0, 0.4 * turn, 0);
      if (T > 0.8 && T < 1.7) ctx.get('rastro', sweep).at(V3(0, 0, 0.4), 0.6, -1.5, 1.4, 2.5, 0.55, (T - 0.8) / 0.9);
      const hit = seq([[0.92, 0], [1.05, 1, 'out']], T);
      if (T > 0.9 && T < 1.1) ctx.shake(0.7);
      const fly = seq([[0.95, 0], [1.6, 1, 'out']], T);
      R.place(-2.6 - 2.8 * fly, 2.6 - 1.2 * fly, 1.3 * Math.sin(Math.PI * fly));
      R.spin(1.6 * fly); R.lean(-1.0 * Math.min(1, fly * 1.6) * (1 - seq([[2.4, 0], [3.1, 1]], T)));
      if (T > 0.95 && T < 1.9) ctx.get('fagulha', () => FX.debris(14, 81, [[176, 140, 100], [140, 110, 80], [210, 180, 140]])).at(V3(-2.4, 0.6, 2.4), T - 0.95);
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(8), variant(31), variant(77)];
  fixed[0].skin = SKINS[0]; fixed[0].frill = FRILLS[0]; fixed[1].skin = SKINS[1]; fixed[1].frill = FRILLS[1]; fixed[1].broken = true; fixed[2].skin = SKINS[2]; fixed[2].frill = FRILLS[2];
  for (const v of fixed) v.belly = mix(v.skin, [244, 226, 176], 0.7);
  const def = v => ({ def: rigDef(painters(v), v), low: rigLow(painters(v), v) });
  window.MOBS.push({
    id: 'dino_chifrudo', nome: 'Chifrudo', bioma: 'Dinossauros', tier: 'Médio · tier 7', refX: 3.2,
    variants: fixed.map((v, i) => ({ nome: ['Laranja', 'Verde-água', 'Roxo'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['chifrada', 'Chifrada'], ['clava', 'Clava']],
    cam: 7, lookY: 1.0, lookZ: { chifrada: -3.2, clava: 0.6 }, camFar: { chifrada: 11, clava: 8.5 }, camYaw: { chifrada: -1.3, clava: -0.4 },
    info: {
      'Tamanho': '1,7 bloco de altura com a gola, 3,4 de comprimento',
      'Vida': '80 (tier 7)',
      'Jeito': 'Neutro, em casal ou trio pastando. Se apanhar, o bando todo fica bravo e ataca.',
      'Chifrada': 'Bate a pata no chão duas vezes (aviso), abaixa a cabeça e corre em linha reta. Quem estiver na frente voa por cima dele e cai atrás. Desviar pro lado escapa.',
      'Clava': 'Quando você fica do lado ou atrás, ele vira o corpo e varre com a clava da cauda: arremessa pro lado e quebra blocos fracos no caminho.',
      'Solta': 'Carne de dinossauro, couro grosso; raro: chifre (vira lança curta) e pedaço da clava.',
      'Variações': 'Cor do couro e da gola, número de anéis da gola, placas nas costas, chifre quebrado e cicatriz sorteados.',
    },
  });
})();
