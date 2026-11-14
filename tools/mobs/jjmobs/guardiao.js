// Guardião do Bosque (Hanami). Tier 5. Maldição do medo que as pessoas têm da mata fechada: árvore em pé de três
// blocos com corpo de tronco oco (um coração-semente brilhando lá dentro), braços de galho com dedos de graveto,
// pernas de feixe de raízes, uma máscara de casca com três olhos-nó e uma copa florida em cima da cabeça.
// Golpes: Campo de Flores (ajoelha e a terra floresce em volta; o pólen tira a vontade de lutar: tudo fica lento e
// embaçado) e Estacas (levanta a mão e um feixe de raízes pontudas fecha em você de baixo pra cima).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  // [nome, casca, flor, folha, olho/coração]
  const KINDS = [
    ['Cerejeira', [92, 70, 62], [248, 186, 206], [110, 150, 80], [255, 120, 170]],
    ['Glicínia', [96, 86, 80], [176, 140, 230], [100, 140, 80], [190, 140, 255]],
    ['Bordo de Outono', [84, 62, 46], [226, 96, 44], [196, 70, 36], [255, 170, 60]],
  ];
  function variant(seed, kind) {
    const R = FX.rnd(seed);
    const k = kind ?? Math.floor(R() * KINDS.length);
    const crown = [];
    // copa larga como de árvore de verdade: um anel de cachos grandes nas pontas dos galhos + miolo mais alto
    for (let i = 0; i < 11; i++) { const a = i / 11 * Math.PI * 2 + R() * 0.35; crown.push({ x: Math.cos(a) * (9.5 + R() * 3), z: Math.sin(a) * (4.6 + R() * 2) + 1.4, y: -8.5 - R() * 3.5 + 1.5 * Math.abs(Math.cos(a)), s: 5.4 + R() * 2.6 }); }
    for (let i = 0; i < 6; i++) { const a = i / 6 * Math.PI * 2 + R(); crown.push({ x: Math.cos(a) * (3 + R() * 3.5), z: Math.sin(a) * 2.6 + 1.4, y: -13 - R() * 3.5, s: 6 + R() * 2.2 }); }
    return { seed, kind: k, bark: KINDS[k][1], bloom: KINDS[k][2], leaf: KINDS[k][3], glow: KINDS[k][4], crown, size: 0.96 + R() * 0.08, vine: R() < 0.6 };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.72, hi = 1.18) => {
      const f = 0.62 - 0.3 * q.ny + 0.3 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.22 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const P = {};
    // casca: sulcos verticais fundos e irregulares, placas, musgo do lado da sombra, líquen claro
    const bark = (q, s) => {
      let c = shade(v.bark, q, s);
      const g = fr(q.ax * 0.45 + q.az * 0.45 + 0.6 * n3(q.ax * 0.1, q.ay * 0.12, q.az * 0.1, s + 1 + S));
      if (g < 0.16) c = mul(c, 0.62); else if (g < 0.26) c = mul(c, 0.84);
      if (fr(q.ay * 0.35 + n3(q.ax * 0.5, 0, q.az * 0.5, s + 2 + S)) < 0.06) c = mul(c, 0.8);
      if (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + 3 + S) > 0.68 && q.f !== 'top') c = mix(c, [90, 120, 60], 0.55);
      if (n3(q.ax * 1.2, q.ay * 1.2, q.az * 1.2, s + 4 + S) > 0.82) c = mix(c, [200, 206, 170], 0.5);
      return c;
    };
    P.bark = q => bark(q, 1);
    // tronco do peito: oco na frente (rachadura comprida, escura por dentro) mostrando o coração
    P.chest = q => {
      let c = bark(q, 2);
      if (q.f !== 'north') return c;
      const x = (q.px - q.b.w / 2), y = q.py;
      const crack = Math.abs(x + 0.8 * Math.sin(y * 0.35)) < 1.6 - 0.9 * Math.abs(y - 9) / 9;
      if (crack && y > 2 && y < 16) return Math.abs(x + 0.8 * Math.sin(y * 0.35)) > 1.1 - 0.9 * Math.abs(y - 9) / 9 ? [30, 18, 14] : [12, 6, 6];
      return c;
    };
    P.core = q => { const d = Math.hypot(q.nx - 0.5, q.ny - 0.5); let c = mix([255, 250, 230], v.glow, Math.min(1, d * 2.2)); if (fr((q.nx + q.ny) * 3) < 0.15) c = mul(c, 0.8); return c; };
    // máscara: três olhos-nó (dois e um na testa), sem boca: só um sulco fundo
    P.mask = q => {
      let c = mix(bark(q, 5), [200, 180, 150], 0.35);
      if (q.f !== 'north') return c;
      const x = q.px - q.b.w / 2, y = q.py;
      for (const [ex, ey, rr] of [[-2.1, 3.6, 1.15], [2.1, 3.6, 1.15], [0, 1.3, 0.85]]) {
        const d = Math.hypot(x - ex, (y - ey) * 1.15);
        if (d < rr * 0.45) return [255, 252, 236];
        if (d < rr * 0.8) return v.glow;
        if (d < rr) return [26, 16, 12];
        if (d < rr + 0.45 && fr(Math.atan2(y - ey, x - ex) * 1.6) < 0.5) return mul(c, 0.7);
      }
      if (Math.abs(x) < 2.2 && Math.abs(y - 5.9) < 0.3) return [36, 22, 16];
      if (Math.abs(x) < 0.25 && y > 4.6 && y < 5.8) return mul(c, 0.7);
      return c;
    };
    P.branch = q => { let c = bark(q, 6); if (q.ny < 0.08 && q.f !== 'bottom') c = mix(c, [90, 120, 60], 0.4); return c; };
    P.root = q => { let c = bark(q, 7); if (q.ny > 0.86) c = mix(c, [70, 52, 36], 0.6); return c; };
    // cacho de flores: cada flor de 5 pétalas aparece em células com centro amarelo, entre folhas
    P.bloom = q => {
      const u = q.f === 'top' || q.f === 'bottom' ? [q.ax, q.az] : (q.f === 'north' || q.f === 'south' ? [q.ax, q.ay] : [q.az, q.ay]);
      const cx = Math.floor(u[0] * 0.8), cy = Math.floor(u[1] * 0.8), fx = fr(u[0] * 0.8) - 0.5, fy = fr(u[1] * 0.8) - 0.5;
      const R = FX.rnd(cx * 31 + cy * 17 + S);
      const on = R() < 0.62, d = Math.hypot(fx, fy);
      let c;
      if (on && d < 0.42) c = d < 0.12 ? [250, 214, 80] : shade(v.bloom, q, 8, 0.86, 1.1);
      else c = shade(v.leaf, q, 9, 0.7, 1.12);
      if (q.f === 'bottom') c = mul(c, 0.8);
      return c;
    };
    P.vine = q => { let c = shade([80, 120, 56], q, 10); if (fr(q.ay * 0.6) < 0.25) c = shade([120, 160, 80], q, 11); return c; };
    P.finger = q => { let c = bark(q, 12); if (q.ny > 0.8) c = [50, 34, 26]; return c; };
    return P;
  }

  function rig(P, v, lo) {
    const arm = s => {
      const n = s < 0 ? 'D' : 'E';
      return {
        name: 'braco' + n, pivot: [s * 9.4, -18, 0], rot: [0, 0, -s * 0.12], boxes: [{ x: -2.3, y: -1.6, z: -2.3, w: 4.6, h: 13, d: 4.6, p: P.branch }, ...(lo ? [] : [{ x: -2.6, y: -2.2, z: -2.6, w: 5.2, h: 3, d: 5.2, p: P.bark }]), ...(lo ? [] : [{ x: s * 1.2 - 0.5, y: 3, z: -0.5, w: 1, h: 1, d: 3.2, p: P.branch }])],
        kids: [{
          name: 'ante' + n, pivot: [0, 11.4, 0], boxes: [{ x: -1.8, y: 0, z: -1.8, w: 3.6, h: 11, d: 3.6, p: P.branch }],
          kids: [{
            name: 'mao' + n, pivot: [0, 11, 0], boxes: [{ x: -2.1, y: 0, z: -2.1, w: 4.2, h: 2.6, d: 4.2, p: P.branch }],
            kids: [-1.1, 0, 1.1, 2].map((z, i) => ({
              name: 'dedo' + n + i, pivot: [i === 3 ? s * 1.2 : 0, 2.2, i === 3 ? -1 : z], rot: [i === 3 ? -0.4 : 0.1 * (i - 1), 0, i === 3 ? -s * 0.5 : 0],
              boxes: [{ x: -0.55, y: 0, z: -0.55, w: 1.1, h: i === 3 ? 4 : 6.4 - Math.abs(i - 1) * 0.8, d: 1.1, p: P.finger }],
            })),
          }],
        }],
      };
    };
    const leg = s => {
      const n = s < 0 ? 'D' : 'E';
      return {
        name: 'perna' + n, pivot: [s * 3.2, 6, 0], boxes: [{ x: -2.3, y: 0, z: -2.3, w: 4.6, h: 10, d: 4.6, p: P.root }],
        kids: [{
          name: 'canela' + n, pivot: [0, 9.6, 0], boxes: [
            { x: -2, y: 0, z: -2, w: 4, h: 8.4, d: 4, p: P.root },
            // pé: raízes abrindo no chão
            { x: -3.2, y: 7.4, z: -4.4, w: 6.4, h: 1, d: 6.8, p: P.root },
            ...(lo ? [] : [{ x: -0.6, y: 7.6, z: -6.4, w: 1.2, h: 0.8, d: 2.4, p: P.root }, { x: s * 3.6 - 0.6, y: 7.6, z: -1, w: 1.2, h: 0.8, d: 2.2, p: P.root }]),
          ],
        }],
      };
    };
    const crown = (lo ? v.crown.slice(0, 4) : v.crown).map(c => ({ x: c.x - c.s / 2, y: c.y - c.s * 0.8, z: c.z - c.s / 2, w: c.s, h: c.s * 0.8, d: c.s, p: P.bloom }));
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'quadril', pivot: [0, 6, 0], boxes: [{ x: -5.4, y: -2, z: -3.8, w: 10.8, h: 4, d: 7.6, p: P.bark }],
          kids: [{
            name: 'tronco', pivot: [0, -1.6, 0], boxes: [
              { x: -7, y: -16.4, z: -4.8, w: 14, h: 16.4, d: 9.6, p: P.chest },
              { x: -9.6, y: -20.4, z: -5.4, w: 19.2, h: 5, d: 10.8, p: P.bark },
              ...(v.vine && !lo ? [{ x: -6.2, y: -19, z: -4.7, w: 1.2, h: 14, d: 1.2, p: P.vine }, { x: 4, y: -19.4, z: 3.8, w: 1.2, h: 10, d: 1.2, p: P.vine }] : []),
            ],
            kids: [
              { name: 'coracao', pivot: [0, -8, -2.2], boxes: [{ x: -1.4, y: -1.6, z: -1.4, w: 2.8, h: 3.2, d: 2.8, p: P.core }] },
              arm(-1), arm(1),
              {
                name: 'cabeca', pivot: [0, -19.4, -1.6], boxes: [
                  { x: -4.2, y: -8.4, z: -4, w: 8.4, h: 8.4, d: 8, p: P.bark },
                  { x: -4.6, y: -7.8, z: -4.8, w: 9.2, h: 7.4, d: 0.9, p: P.mask },
                ],
              },
              {
                // copa sai das costas/ombros por galhos grossos e abre por cima da cabeça
                name: 'copa', pivot: [0, -20, 2.4], boxes: [...crown],
                kids: [[-1, 0.8, 6.4], [1, -0.8, 6.4], [-0.4, 0.2, 3], [0.5, -0.25, 3]].map(([s2, rz, sx], i) => ({
                  name: 'galho' + i, pivot: [s2 * sx, 0.4, 0], rot: [0.12 * (i > 1 ? -1 : 1), 0, rz],
                  boxes: [{ x: -1.3, y: -11, z: -1.3, w: 2.6, h: 11.4, d: 2.6, p: P.branch }, ...(lo || i > 1 ? [] : [{ x: -s2 * 3.6 - 0.6, y: -8, z: -0.6, w: 3.6, h: 1.2, d: 1.2, p: P.branch }])],
                })),
              },
            ],
          }],
        },
        leg(-1), leg(1),
      ],
    };
  }

  // flor que nasce do chão: caule + 5 pétalas de caixa + miolo (cada uma com cor e tamanho próprios)
  function flowerField(v, n = 70) {
    const g = new THREE.Group(); g.items = [];
    const R = FX.rnd(v.seed + 5);
    const stemM = new THREE.MeshLambertMaterial({ color: 0x4e8a3a }), core = new THREE.MeshLambertMaterial({ color: 0xf2d050 });
    for (let i = 0; i < n; i++) {
      const f = new THREE.Group();
      const col = new THREE.Color(...v.bloom.map(c => c / 255)).offsetHSL((R() - 0.5) * 0.06, 0, (R() - 0.5) * 0.15);
      const pm = new THREE.MeshLambertMaterial({ color: col });
      const h = 0.08 + R() * 0.14;
      const st = new THREE.Mesh(new THREE.BoxGeometry(0.02, h, 0.02), stemM); st.position.y = h / 2; f.add(st);
      for (let k = 0; k < 5; k++) { const p = new THREE.Mesh(new THREE.BoxGeometry(0.045, 0.012, 0.03), pm); const a = k / 5 * Math.PI * 2; p.position.set(Math.cos(a) * 0.035, h, Math.sin(a) * 0.035); p.rotation.y = -a; f.add(p); }
      const c = new THREE.Mesh(new THREE.BoxGeometry(0.03, 0.02, 0.03), core); c.position.y = h + 0.008; f.add(c);
      const a = R() * Math.PI * 2, rr = Math.sqrt(R()) * 3.2;
      f.position.set(Math.cos(a) * rr, 0, Math.sin(a) * rr); f.rotation.y = R() * 6;
      g.add(f); g.items.push({ f, r: rr, d: R() * 0.25, s: 0.8 + R() * 0.7 });
    }
    g.frustumCulled = false;
    g.at = (c, grow, t) => {
      g.visible = grow > 0; g.position.copy(c);
      for (const it of g.items) { const k = FX.clamp01((grow * 3.6 - it.r - it.d) * 1.8); it.f.scale.setScalar(Math.max(0.001, FX.EASE.out(k) * it.s)); it.f.rotation.z = 0.08 * Math.sin(t * 2 + it.r * 3); }
    };
    return g;
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const W = (n, x, y, z) => { obj.updateMatrixWorld(true); return new THREE.Vector3(x, y, z).applyMatrix4(P[n].obj.matrixWorld); };
    const glow = C => DBFX.glow(C.map(c => c / 255));
    const heart = (k = 1) => { const p = W('coracao', 0, 0, -1.6); const b = 0.75 + 0.25 * Math.max(0, Math.sin(t * 2.4)) ** 4; ctx.get('cor', () => glow(e.v.glow)).at(p, 0.16, k * b, t); };
    const eyes = (k = 1) => { for (const [i, ex, ey] of [[0, -2.1, -4.2], [1, 2.1, -4.2], [2, 0, -6.5]]) ctx.get('olho' + i, () => glow(e.v.glow)).at(W('cabeca', ex, ey, -4.9), i === 2 ? 0.06 : 0.075, 0.8 * k, t); };
    const fingers = k => { for (const n of ['D', 'E']) for (let i = 0; i < 3; i++) r('dedo' + n + i, 0.5 * k, 0, 0); };
    const petals = (key, at, age, n = 30, sp = 1) => ctx.get(key, () => DBFX.grains({ n, seed: key.length * 13 + 1, palette: [e.v.bloom, mix(e.v.bloom, [255, 255, 255], 0.35), mul(e.v.bloom, 0.85)], size: [0.03, 0.055], drag: 2.2, grav: 0.7, life: 2.2, spread: 2.0, cone: R2 => [(R2() - 0.5) * 1.4 * sp, R2() * 0.6, (R2() - 0.5) * 1.4 * sp] })).at(at, age, 0.02);

    if (mode === 'parado') {
      // parado igual árvore: só a copa balança no vento, os dedos mexem devagar, coração pulsa; pétalas caindo
      r('copa', 0.05 * Math.sin(t * 0.9), 0, 0.06 * Math.sin(t * 0.7));
      r('cabeca', 0, 0.15 * Math.sin(t * 0.3), 0.03 * Math.sin(t * 0.5));
      r('bracoD', 0.04 * Math.sin(t * 0.8), 0, 0.03); r('bracoE', 0.04 * Math.sin(t * 0.8 + 1), 0, -0.03);
      fingers(0.2 + 0.15 * Math.sin(t * 1.3));
      r('tronco', 0.02 * Math.sin(t * 0.6), 0, 0);
      heart(1); eyes(0.8 + 0.2 * Math.sin(t * 1.7));
      petals('cai', W('copa', 0, -11, 1), (t % 3) , 18, 1);
      return;
    }
    if (mode === 'andar') {
      // passos lentos e pesados, cada um afunda as raízes no chão; o corpo inteiro balança
      const ph = t * 2.2;
      r('pernaD', 0.4 * Math.sin(ph), 0, 0); r('pernaE', -0.4 * Math.sin(ph), 0, 0);
      r('canelaD', 0.35 * Math.max(0, -Math.sin(ph)), 0, 0); r('canelaE', 0.35 * Math.max(0, Math.sin(ph)), 0, 0);
      r('tronco', 0.06, 0.08 * Math.sin(ph), 0.04 * Math.sin(ph)); r('quadril', 0, -0.06 * Math.sin(ph), 0);
      r('bracoD', -0.3 * Math.sin(ph), 0, 0.05); r('bracoE', 0.3 * Math.sin(ph), 0, -0.05);
      r('copa', 0.08 * Math.sin(ph * 2), 0, 0.06 * Math.sin(ph));
      obj.position.y += 0.03 * Math.abs(Math.cos(ph));
      const land = Math.abs(Math.cos(ph)); if (land > 0.97) ctx.shake(0.15);
      heart(1); eyes(1); fingers(0.3);
      return;
    }
    if (mode === 'flores') {
      const T = t % 6.4;
      const R = ctx.ref; const RZ = -2.6; R.place(0, RZ, 0); R.spin(Math.PI);
      // ajoelha e enfia as mãos na terra; o chão floresce em círculo; pólen sobe; o alvo amolece e baixa os braços
      const kneel = seq([[0.2, 0], [1.0, 1, 'io'], [5.0, 1], [5.8, 0, 'io']], T);
      r('pernaD', -1.2 * kneel, 0, 0); r('canelaD', 1.9 * kneel, 0, 0); r('pernaE', -0.2 * kneel, 0, 0); r('canelaE', 1.2 * kneel, 0, 0);
      obj.position.y -= 0.55 * kneel;
      r('tronco', 0.55 * kneel, 0, 0); r('cabeca', -0.35 * kneel, 0, 0);
      r('bracoD', -0.9 * kneel, 0, 0.15 * kneel); r('bracoE', -0.9 * kneel, 0, -0.15 * kneel); r('anteD', -0.3 * kneel, 0, 0); r('anteE', -0.3 * kneel, 0, 0);
      fingers(-0.4 * kneel);
      const grow = seq([[1.0, 0], [3.2, 1, 'out'], [5.4, 1], [6.3, 0, 'in']], T);
      ctx.get('campo', () => flowerField(e.v, 90)).at(new THREE.Vector3(0, 0, -1.2), grow, t);
      if (T > 1.4 && T < 6.0) ctx.get('polen', () => DBFX.grains({ n: 70, seed: 202, palette: [[250, 230, 120], [255, 245, 190], e.v.bloom], size: [0.015, 0.03], drag: 0.8, grav: -0.25, life: 3.4, spread: 3.0, cone: R2 => { const a2 = R2() * 6.28, rr = R2() * 2.6; return [Math.cos(a2) * rr * 0.4, 0.15 + R2() * 0.35, Math.sin(a2) * rr * 0.4]; } })).at(new THREE.Vector3(0, 0.15, -1.4), T - 1.4, 0);
      petals('petalas', W('copa', 0, -11, 1), T, 40, 2);
      const calm = seq([[2.0, 0], [3.4, 1, 'io'], [5.4, 1], [6.2, 0]], T);
      ctx.blur(0.45 * calm); ctx.dizzy(0.5 * calm);
      R.lean(0.15 * calm); R.armsOut(-0.15 * calm); R.spin(Math.PI + 0.2 * calm * Math.sin(T * 0.9));
      heart(1 + 0.6 * calm); eyes(1 + 0.5 * calm);
      return;
    }
    if (mode === 'estacas') {
      const T = t % 4.8;
      const R = ctx.ref; const RZ = -3.0; R.place(0, RZ, 0); R.spin(Math.PI);
      // aponta a mão aberta pro alvo, levanta devagar (o chão embaixo dele racha) e fecha: seis estacas de raiz
      // fecham nele de baixo pra cima, cada uma de um lado
      const raise = seq([[0.2, 0], [0.8, 1, 'io'], [1.4, 1.05], [1.55, 1.4, 'snap'], [3.6, 1.4], [4.3, 0, 'io']], T);
      r('bracoD', -1.3 * Math.min(1, raise) - 0.6 * Math.max(0, raise - 1), -0.2, 0.1); r('anteD', -0.2 * raise, 0, 0);
      fingers(seq([[0.2, 0], [0.8, -0.6], [1.45, -0.6], [1.55, 1.2, 'snap'], [3.6, 1.2], [4.3, 0]], T));
      r('tronco', -0.1 * Math.min(1, raise), 0.15 * Math.min(1, raise), 0); r('cabeca', -0.1, 0, 0);
      if (T > 0.6 && T < 1.55) ctx.get('racha', () => DBFX.grains({ n: 26, seed: 211, palette: [[90, 66, 44], [60, 44, 30]], size: [0.02, 0.05], drag: 3, grav: 6, life: 0.4, cone: R2 => [(R2() - 0.5) * 1.2, R2() * 0.9, (R2() - 0.5) * 1.2] })).at(new THREE.Vector3(0, 0.03, RZ), (T * 2.5) % 1, 0);
      const N = 6;
      for (let i = 0; i < N; i++) {
        const t0 = 1.55 + i * 0.04, g = seq([[t0, 0], [t0 + 0.16, 1, 'out'], [3.6, 1], [4.0, 0, 'in']], T);
        const ang = i / N * Math.PI * 2 + 0.3;
        const base = new THREE.Vector3(Math.cos(ang) * 0.75, 0, RZ + Math.sin(ang) * 0.75);
        const tip = new THREE.Vector3(Math.cos(ang) * 0.12, 1.0 + 0.12 * (i % 3), RZ + Math.sin(ang) * 0.12);
        ctx.get('estaca' + i, () => JJFX.tube(0x6a4a34, 0.12, 0.01)).at(base, tip, g, { arc: 0.15, wiggle: 0.03, t: i });
        if (g > 0 && T < t0 + 0.35) ctx.get('terra' + i, () => DBFX.grains({ n: 12, seed: 220 + i, palette: [[90, 66, 44], [60, 44, 30], [70, 110, 50]], size: [0.03, 0.06], drag: 2, grav: 7, life: 0.5, cone: R2 => [(R2() - 0.5) * 1.4, 0.8 + R2() * 1.2, (R2() - 0.5) * 1.4] })).at(base, T - t0, 0);
      }
      if (T > 1.55 && T < 1.75) { ctx.flash(new THREE.Vector3(0, 1.0, RZ), 1 - (T - 1.55) / 0.2); ctx.shake(0.8); }
      const held = T > 1.6 && T < 3.7;
      if (held) { R.place(0, RZ, 0.25 * Math.min(1, (T - 1.6) * 5)); R.armsOut(0.15); R.lean(-0.1 + 0.05 * Math.sin(T * 9)); }
      if (T > 3.7) { const k = FX.clamp01((T - 3.7) / 0.5); R.place(0, RZ, 0.25 * (1 - k)); R.lean(0.35 * k); }
      heart(1 + 0.8 * Math.max(0, raise - 1)); eyes(1 + Math.max(0, raise - 1));
      return;
    }
    if (mode === 'morte') {
      // o coração apaga, os olhos fecham, a copa solta todas as flores de uma vez e ele cai de costas como árvore cortada
      const { T, fall } = JJFX.exorcize(t, obj, e, ctx, { h: 3.4, refZ: -2.6, rotX: 1.35, back: 0.5, drop: -0.9, ashY: 0.3, ashZ: 1.8, n: 220 });
      const off = Math.max(0, 1 - T * 1.2);
      heart(off); eyes(off);
      if (T > 0.3 && T < 3.0) petals('solta', W('copa', 0, -11, 1), T - 0.3, 80, 2.4);
      r('bracoD', -0.4 * fall, 0, 0.5 * fall); r('bracoE', -0.4 * fall, 0, -0.5 * fall); fingers(0.8 * fall);
      if (T > 0.8 && T < 1.2) ctx.shake(1 - (T - 0.8) / 0.4);
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(10, 0), variant(34, 1), variant(57, 2)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'guardiao', nome: 'Guardião do Bosque', bioma: 'Hanami', tier: 'Grau 1 · tier 5',
    variants: fixed.map(v => ({ nome: KINDS[v.kind][0], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado (' + KINDS[v.kind][0] + ')', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['flores', 'Campo de Flores'], ['estacas', 'Estacas'], ['morte', 'Exorcizado']],
    cam: 7.4, lookY: 1.8, lookZ: { flores: -1.4, estacas: -1.5, morte: 1.0 }, camFar: { flores: 8.4, estacas: 8.0, morte: 8.4 }, camYaw: { flores: -0.9, estacas: -1.2 },
    info: {
      'Tamanho': '3,4 blocos de altura com a copa',
      'Vida': '140 (madeira: machado e fogo fazem dano dobrado)',
      'Jeito': 'Fica parado no meio da mata, igual árvore florida (só a copa balançando). Anda devagar e pesado, mas onde ele passa as árvores em volta se mexem junto.',
      'Campo de Flores': 'Ajoelha e o chão floresce num círculo de 7 blocos. Dentro das flores: lento, golpes mais fracos (-30%) e tela embaçada, igual sono. Queimar as flores (fogo) desfaz o campo.',
      'Estacas': 'Aponta a mão e fecha: seis estacas de raiz saem do chão em volta de você e fecham em você (dano alto + preso 2 s). O chão racha embaixo de você 1 s antes: é o aviso.',
      'Fraqueza': 'O coração-semente no peito: golpe no oco do tronco faz dano triplo. Fogo.',
      'Solta': 'Coração-Semente (planta uma árvore guardiã que ataca maldições), Madeira Amaldiçoada, Flores; raro: Máscara de Casca (capacete: vê maldições escondidas).',
      'Variações': 'Cerejeira, glicínia (roxa) ou bordo de outono; flores da copa em lugares diferentes; com ou sem cipó.',
    },
  });
})();
