// Raptor, o dino pequeno (Paozu, Terras do Dragão e Deserto). Estilo do desenho: cabeção, olho redondo, couro liso
// de duas cores com a barriga em gomos. Fraco, tier 8. Caçador de bando: corpo na horizontal, cauda de
// contrapeso, pernas de ave com a garra-foice levantada no dedo de dentro.
// Golpes: Bote (agacha, rebola, salta em cima do alvo, prende no chão e morde três vezes) e Garras (pula e chuta
// com as duas foices, deixando um X de cortes que sangra).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const V3 = (x, y, z) => new THREE.Vector3(x, y, z);

  const SKINS = [[96, 150, 70], [176, 112, 64], [128, 104, 156], [190, 150, 70], [90, 130, 140], [150, 90, 70]];
  const CRESTS = [[200, 60, 40], [230, 150, 40], [60, 110, 190], [40, 40, 44], [220, 210, 190]];
  function variant(seed) {
    const R = FX.rnd(seed);
    const pick = a => a[Math.floor(R() * a.length)];
    const skin = pick(SKINS).map(c => c * (0.92 + R() * 0.12));
    return {
      seed, skin, belly: mix(skin, [244, 226, 176], 0.72), stripe: mul(skin, 0.55 + R() * 0.12), crest: pick(CRESTS),
      stripes: 0.8 + R() * 0.6, quills: R() < 0.55, scar: R() < 0.3, eye: R() < 0.5 ? [236, 200, 60] : [230, 120, 40],
    };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.8, hi = 1.14) => {
      const f = 0.62 - 0.38 * q.ny + 0.14 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    // couro: barriga clara, faixas escuras que atravessam as costas e a cauda, escamas pequenas
    // couro liso como no desenho: duas cores (costas e barriga creme com gomos), sombra em faixas, quase sem ruído
    const hide = (q, s = 1) => {
      const under = q.f === 'bottom' || (q.f !== 'top' && q.ny > 0.6);
      let c = shade(under ? v.belly : v.skin, q, s, 0.84, 1.1);
      if (under && fr(q.az * 0.5) < 0.16) c = mul(c, 0.82);                                        // gomos da barriga
      if (!under && q.f !== 'bottom') {
        const band = fr(q.az * 0.09 * v.stripes + 0.3);
        if (band < 0.12 && q.f === 'top') c = shade(v.stripe, q, s + 1, 0.84, 1.1);
      }
      return c;
    };
    const limb = (q, s = 6) => { let c = shade(v.skin, q, s, 0.84, 1.1); if (q.ny > 0.75 && q.f !== 'top') c = mix(c, v.belly, 0.35); return c; };
    const P = { hide, limb };
    P.head = q => {
      let c = hide(q, 2);
      const x = q.pz, y = q.py;
      if (q.f === 'west' || q.f === 'east') {
        // olho redondo de desenho: branco, pupila preta olhando pra frente, sobrancelha brava por cima
        const ex = 2.6, ey = 1.5, d = Math.hypot((x - ex) / 1.15, (y - ey) / 1.0);
        if (d < 1) { c = [246, 244, 232]; if (Math.hypot(x - (ex - 0.45), y - ey) < 0.55) c = [18, 16, 14]; }
        if (y < ey - 0.75 && y > ey - 1.4 && Math.abs(x - ex) < 1.5 && (y - (ey - 1.1)) < (ex - x) * 0.25 + 0.3) c = mul(v.skin, 0.55);
        if (v.scar && q.f === 'west' && Math.abs(y - (2.9 - (x - 4.5) * 0.6)) < 0.3 && x > 4 && x < 6.5) c = mul(v.skin, 0.62);
      }
      return c;
    };
    P.snout = q => {
      let c = hide(q, 3);
      if ((q.f === 'west' || q.f === 'east') && q.py > 2.0) { const k = fr(q.pz / 1.4); if (k < 0.5 && (q.py - 2.0) < 0.9 * (1 - Math.abs(k - 0.25) * 4)) c = [244, 240, 226]; }   // dentes de cima, poucos e grandes
      if (q.f === 'north' && q.py < 0.8 && (Math.floor(q.px) === 0 || Math.floor(q.px) === 2)) c = [30, 24, 20];                       // narinas
      return c;
    };
    P.jaw = q => {
      let c = hide(q, 4);
      if ((q.f === 'west' || q.f === 'east') && q.py < 0.6 && q.pz < 7) { const k = fr(q.pz / 1.6 + 0.5); if (k < 0.45 && q.py < 0.6 * (1 - Math.abs(k - 0.22) * 4)) c = [244, 240, 226]; }
      if (q.f === 'top') c = [150, 60, 70];
      return c;
    };
    P.mouth = () => [120, 40, 50];
    P.claw = q => cel([40, 34, 30], 0.4 + 0.6 * (1 - q.ny), 0.8, 1.6);
    P.quill = q => { const k = Math.floor(q.az); const cut = 3 * (0.4 + 0.6 * n3(k * 1.3, 0, 0, 40 + S)); if (q.f === 'top' || q.f === 'bottom') return null; if ((3 - q.py) > cut) return null; return shade(v.crest, q, 5); };
    return P;
  }

  function rigDef(P, v) {
    const leg = s => {
      const D = s < 0;
      return {
        name: D ? 'pernaD' : 'pernaE', pivot: [s * 2.8, 8.6, 0.6], rot: [-0.55, 0, 0], boxes: [{ x: -1.9, y: -1.5, z: -2.4, w: 3.8, h: 8, d: 4.8, p: P.limb }],
        kids: [{
          name: D ? 'canelaD' : 'canelaE', pivot: [0, 6, 0], rot: [1.1, 0, 0], boxes: [{ x: -1.25, y: 0, z: -1.3, w: 2.5, h: 6.2, d: 2.6, p: P.limb }],
          kids: [{
            name: D ? 'metaD' : 'metaE', pivot: [0, 6, 0], rot: [-0.75, 0, 0], boxes: [{ x: -0.95, y: 0, z: -1, w: 1.9, h: 4.6, d: 2, p: P.limb }],
            kids: [{
              name: D ? 'peD' : 'peE', pivot: [0, 4.5, 0], rot: [0.2, 0, 0], boxes: [
                { x: -1.5, y: -0.4, z: -3.2, w: 3, h: 1.4, d: 4, p: P.limb },
                { x: -1.4, y: 0.1, z: -4.2, w: 0.9, h: 0.9, d: 1.2, p: P.claw },
                { x: 0.4, y: 0.1, z: -4.3, w: 0.9, h: 0.9, d: 1.3, p: P.claw },
              ],
              kids: [{ name: D ? 'foiceD' : 'foiceE', pivot: [-s * 1.1, 0.2, -1.4], rot: [-1.0, 0, 0], boxes: [{ x: -0.4, y: -2.4, z: -0.45, w: 0.8, h: 2.6, d: 0.9, p: P.claw }, { x: -0.3, y: -2.9, z: -1.1, w: 0.6, h: 0.8, d: 0.9, p: P.claw }] }],
            }],
          }],
        }],
      };
    };
    const arm = s => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * 2.5, 2.4, -6.3], rot: [0.7, 0, s * -0.2], boxes: [{ x: -0.7, y: 0, z: -0.8, w: 1.4, h: 3.6, d: 1.6, p: P.limb }],
      kids: [{
        name: s < 0 ? 'anteD' : 'anteE', pivot: [0, 3.4, 0], rot: [-1.3, 0, 0], boxes: [{ x: -0.6, y: 0, z: -0.6, w: 1.2, h: 3, d: 1.2, p: P.limb }],
        kids: [{ name: s < 0 ? 'maoD' : 'maoE', pivot: [0, 2.8, 0], rot: [0.5, 0, 0], boxes: [{ x: -0.7, y: 0, z: -0.4, w: 0.5, h: 1.6, d: 0.6, p: P.claw }, { x: 0.2, y: 0, z: -0.4, w: 0.5, h: 1.8, d: 0.6, p: P.claw }] }],
      }],
    });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'quadril', pivot: [0, 8.6, 1], boxes: [
            { x: -3.4, y: -3.2, z: -8, w: 6.8, h: 6.8, d: 11, p: P.hide },
            { x: -3.1, y: -2.8, z: -8.8, w: 6.2, h: 7.4, d: 5.2, p: P.hide },                         // peito
          ], kids: [
            {
              name: 'pescoco', pivot: [0, -1, -7.6], rot: [0.62, 0, 0], boxes: [
                { x: -1.7, y: -6.6, z: -1.9, w: 3.4, h: 7.2, d: 3.8, p: P.hide },
                ...(v.quills ? [{ x: -0.15, y: -9.2, z: 1.2, w: 0.3, h: 3, d: 3.2, p: P.quill }] : []),
              ],
              kids: [{
                name: 'cabeca', pivot: [0, -6.2, 0], rot: [-0.62, 0, 0], boxes: [
                  { x: -2.9, y: -3.6, z: -5.2, w: 5.8, h: 4.0, d: 7.2, p: P.head },
                  { x: -2.3, y: -2.6, z: -9.0, w: 4.6, h: 2.9, d: 4.2, p: P.snout },
                  ...(v.quills ? [{ x: -0.15, y: -5.6, z: -1.5, w: 0.3, h: 3, d: 3.4, p: P.quill }] : []),
                ],
                kids: [{ name: 'mandibula', pivot: [0, 0.3, 0.8], boxes: [{ x: -2.4, y: 0, z: -9.2, w: 4.8, h: 1.6, d: 9.6, p: P.jaw }, { x: -1.9, y: -0.25, z: -8.6, w: 3.8, h: 0.3, d: 8, p: P.mouth }] }],
              }],
            },
            arm(-1), arm(1),
            {
              name: 'cauda1', pivot: [0, -1, 3], rot: [-0.12, 0, 0], boxes: [{ x: -2.4, y: -2, z: 0, w: 4.8, h: 4, d: 7, p: P.hide }],
              kids: [{
                name: 'cauda2', pivot: [0, 0, 6.8], rot: [0.05, 0, 0], boxes: [{ x: -1.6, y: -1.4, z: 0, w: 3.2, h: 2.8, d: 7.2, p: P.hide }],
                kids: [{ name: 'cauda3', pivot: [0, 0, 7], rot: [0.05, 0, 0], boxes: [{ x: -0.9, y: -0.8, z: 0, w: 1.8, h: 1.6, d: 7, p: P.hide }] }],
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
      name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * 2.8, 8.6, 0.6], rot: [-0.55, 0, 0], boxes: [{ x: -1.8, y: -1.5, z: -2.3, w: 3.6, h: 8, d: 4.6, p: P.limb }],
      kids: [{
        name: s < 0 ? 'canelaD' : 'canelaE', pivot: [0, 6, 0], rot: [1.1, 0, 0], boxes: [{ x: -1.2, y: 0, z: -1.2, w: 2.4, h: 6.2, d: 2.4, p: P.limb }],
        kids: [{ name: s < 0 ? 'metaD' : 'metaE', pivot: [0, 6, 0], rot: [-0.55, 0, 0], boxes: [{ x: -1, y: 0, z: -1, w: 2, h: 4.8, d: 2, p: P.limb }, { x: -1.5, y: 4.2, z: -3.2, w: 3, h: 1.4, d: 4, p: P.limb }] }],
      }],
    });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'quadril', pivot: [0, 8.6, 1], boxes: [{ x: -3, y: -3, z: -8.6, w: 6, h: 6.6, d: 11.6, p: P.hide }], kids: [
            {
              name: 'pescoco', pivot: [0, -1, -7.6], rot: [0.62, 0, 0], boxes: [{ x: -1.7, y: -6.6, z: -1.9, w: 3.4, h: 7.2, d: 3.8, p: P.hide }],
              kids: [{ name: 'cabeca', pivot: [0, -6.2, 0], rot: [-0.62, 0, 0], boxes: [{ x: -2.3, y: -2.8, z: -5, w: 4.6, h: 3.1, d: 6.6, p: P.head }, { x: -1.7, y: -2.1, z: -8.6, w: 3.4, h: 2.4, d: 3.8, p: P.snout }], kids: [{ name: 'mandibula', pivot: [0, 0.3, 0.8], boxes: [{ x: -1.9, y: 0, z: -9.0, w: 3.8, h: 1.3, d: 9, p: P.jaw }] }] }],
            },
            { name: 'cauda1', pivot: [0, -1, 3], rot: [-0.12, 0, 0], boxes: [{ x: -2.2, y: -2, z: 0, w: 4.4, h: 3.8, d: 9, p: P.hide }], kids: [{ name: 'cauda2', pivot: [0, 0, 8.8], rot: [0.1, 0, 0], boxes: [{ x: -1.2, y: -1.1, z: 0, w: 2.4, h: 2.2, d: 11, p: P.hide }] }] },
          ],
        },
        leg(-1), leg(1),
      ],
    };
  }

  // Marcas de garra: 2 cortes que se cruzam em X, cada corte com 3 riscos (a foice no meio, mais grossa e comprida,
  // os dois dedos do lado mais finos e curtos). Brilham quando passam e ficam vermelhos no alvo até sumir.
  function clawX() {
    const SEG = 22, LINES = 6;
    const N = LINES * (SEG + 1) * 2;
    const pos = new Float32Array(N * 3), al = new Float32Array(N), hot = new Float32Array(N), idx = [];
    for (let l = 0; l < LINES; l++) for (let s = 0; s < SEG; s++) { const a = (l * (SEG + 1) + s) * 2; idx.push(a, a + 1, a + 2, a + 1, a + 3, a + 2); }
    const geo = new THREE.BufferGeometry();
    geo.setIndex(idx);
    geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    geo.setAttribute('al', new THREE.BufferAttribute(al, 1));
    geo.setAttribute('hot', new THREE.BufferAttribute(hot, 1));
    const m = new THREE.Mesh(geo, new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, side: THREE.DoubleSide,
      vertexShader: 'attribute float al; attribute float hot; varying float vA; varying float vH; void main(){ vA=al; vH=hot; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: 'varying float vA; varying float vH; void main(){ vec3 c = mix(vec3(0.5,0.03,0.05), vec3(1.0,0.96,0.92), vH); gl_FragColor = vec4(c, vA); }',
    }));
    m.frustumCulled = false;
    // [início, fim] no plano do alvo (x lateral, y altura), largura e deslocamento de cada risco
    const CUTS = [{ a: [-0.42, 1.55], b: [0.38, 0.75], bend: 0.12 }, { a: [0.44, 1.5], b: [-0.36, 0.7], bend: -0.1 }];
    const LN = [{ off: -0.085, len: 0.82, w: 0.018 }, { off: 0, len: 1, w: 0.036 }, { off: 0.09, len: 0.78, w: 0.016 }];
    m.at = (c, ages) => {
      m.visible = true;
      let k = 0;
      CUTS.forEach((cut, ci) => {
        const age = ages[ci];
        const dx = cut.b[0] - cut.a[0], dy = cut.b[1] - cut.a[1], L = Math.hypot(dx, dy);
        const nx = -dy / L, ny = dx / L;
        LN.forEach(ln => {
          for (let s = 0; s <= SEG; s++) {
            const f = s / SEG;
            const ff = (1 - ln.len) * 0.5 + f * ln.len;
            const bow = Math.sin(Math.PI * ff) * cut.bend;
            const x = c.x + cut.a[0] + dx * ff + nx * (ln.off + bow), y = c.y + cut.a[1] + dy * ff + ny * (ln.off + bow);
            const w = ln.w * Math.sin(Math.PI * Math.min(1, Math.max(0, f))) ** 0.5;
            const lead = Math.min(1, Math.max(0, age) * 5);
            let a = 0, h = 0;
            if (age > 0 && f <= lead) {
              const fresh = Math.max(0, 1 - (lead - f) * 3 - Math.max(0, age - 0.2) * 3);
              h = fresh;
              a = Math.max(fresh, 0.85 * (1 - Math.max(0, (age - 0.3) / 0.7)));
            }
            const i = (k * (SEG + 1) + s) * 2;
            pos.set([x - nx * w, y - ny * w, c.z, x + nx * w, y + ny * w, c.z], i * 3);
            al[i] = al[i + 1] = Math.max(0, a); hot[i] = hot[i + 1] = h;
          }
          k++;
        });
      });
      geo.attributes.position.needsUpdate = true; geo.attributes.al.needsUpdate = true; geo.attributes.hot.needsUpdate = true;
    };
    return m;
  }
  const blood = (seed, n, up) => () => DBFX.grains({
    n, seed, palette: [[150, 10, 16], [120, 6, 12], [180, 24, 24], [90, 4, 8]], size: [0.02, 0.05], drag: 2.2, grav: 9, life: 0.7, spread: up ? 0.08 : 1.6,
    cone: Rr => up ? [(Rr() - 0.5) * 2.4, 1 + Rr() * 2.4, (Rr() - 0.5) * 2.4] : [(Rr() - 0.5) * 0.4, -0.2 - Rr() * 0.5, 0.3 + Rr() * 0.4],
  });

  function stance(r, P) {
    r('pernaD', 0, 0, 0.05); r('pernaE', 0, 0, -0.05);
  }
  const WALK_T = [[0, -0.5], [0.2, -0.25], [0.45, 0.35], [0.55, 0.42], [0.7, 0.1], [0.88, -0.45]];
  const WALK_K = [[0, 0], [0.45, 0], [0.6, 0.5], [0.75, 0.9], [0.9, 0.3]];

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const breath = loop([[0, 0], [0.45, 1], [0.999, 0]], t / 1.6);
    const tailSway = (k, ph) => { r('cauda1', 0, 0.1 * k * Math.sin(ph), 0); r('cauda2', 0, 0.16 * k * Math.sin(ph - 0.7), 0); r('cauda3', 0, 0.22 * k * Math.sin(ph - 1.4), 0); };

    if (mode === 'parado') {
      stance(r, P);
      r('quadril', -0.02 * breath, 0, 0);
      // cabeça de ave: vira em trancos, para, cheira
      const look = [[0, 0], [0.12, 0], [0.16, 0.5], [0.4, 0.5], [0.44, -0.4], [0.7, -0.4], [0.74, 0]];
      const ph = fr(t / 4.2);
      let yaw = 0; for (let i = 0; i < look.length - 1; i++) if (ph >= look[i][0] && ph < look[i + 1][0]) { const k = (ph - look[i][0]) / (look[i + 1][0] - look[i][0]); yaw = look[i][1] + (look[i + 1][1] - look[i][1]) * (k > 0.5 || look[i][1] === look[i + 1][1] ? 1 : k * 2) * (look[i][1] === look[i + 1][1] ? 0 : 1); if (look[i][1] === look[i + 1][1]) yaw = look[i][1]; }
      r('pescoco', 0.04 * breath, yaw * 0.5, 0); r('cabeca', 0, yaw * 0.5, 0);
      const sniff = seq([[0.8, 0], [0.84, 1], [0.88, 0], [0.92, 1], [0.96, 0]], ph);
      r('cabeca', -0.1 * sniff, 0, 0);
      r('mandibula', 0.05 * breath, 0, 0);
      tailSway(0.6, t * 1.7);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 1.5;
      for (const [s, off] of [['D', 0], ['E', 0.5]]) {
        r('perna' + s, loop(WALK_T, ph + off), 0, 0);
        r('canela' + s, 0.4 * loop(WALK_K, ph + off), 0, 0);
        r('meta' + s, -0.5 * loop(WALK_K, ph + off), 0, 0);
        r('pe' + s, 0.6 * loop(WALK_K, ph + off - 0.05), 0, 0);
      }
      P.quadril.obj.position.y += 0.6 * Math.abs(Math.sin(ph * Math.PI * 2));
      r('quadril', 0.03, 0.05 * Math.sin(ph * Math.PI * 2), 0);
      r('pescoco', -0.05 * Math.sin(ph * Math.PI * 4), -0.05 * Math.sin(ph * Math.PI * 2), 0);
      tailSway(0.8, ph * Math.PI * 2 + 1);
      return;
    }

    if (mode === 'bote') {
      const T = t % 4.0;
      const R = ctx.ref; const RZ = -5.0; R.place(0, RZ, 0);
      stance(r, P);
      // agacha e rebola, salta, prende o alvo no chão e morde três vezes; depois pula pra trás
      const crouch = seq([[0, 0], [0.45, 1, 'out'], [0.85, 1], [0.95, 0]], T);
      const wig = T > 0.4 && T < 0.85 ? Math.sin(T * 26) : 0;
      const jump = seq([[0.88, 0], [1.25, 1, 'io']], T);
      const pin = seq([[1.2, 0], [1.3, 1, 'out'], [2.9, 1], [3.05, 0]], T);
      const back = seq([[2.95, 0], [3.4, 1, 'io']], T);
      const air = Math.sin(Math.PI * jump) * (T < 1.25 ? 1 : 0), air2 = Math.sin(Math.PI * back) * (T < 3.4 ? 1 : 0);
      P.quadril.obj.position.y += 3.2 * crouch;
      r('quadril', 0.1 * crouch + 0.3 * pin - 0.25 * air, 0.08 * wig, 0);
      r('pescoco', -0.5 * crouch + 0.3 * air, 0, 0); r('cabeca', 0.45 * crouch - 0.2 * air, 0, 0);
      r('cauda1', -0.3 * crouch + 0.2 * air, 0.25 * wig, 0); r('cauda2', -0.1 * crouch, 0.3 * wig, 0);
      for (const s of ['D', 'E']) {
        r('perna' + s, -0.5 * crouch + 0.8 * air - 0.9 * pin, 0, 0); r('canela' + s, 0.8 * crouch - 0.5 * air + 0.5 * pin, 0, 0);
        r('meta' + s, -0.6 * crouch + 0.2 * pin, 0, 0); r('foice' + s, -0.5 * (air + pin), 0, 0);
        r('braco' + s, -0.8 * (air + pin), 0, 0);
      }
      obj.position.z += (RZ + 1.3) * jump - (RZ + 1.3 - 0.0) * back * 0.75;
      obj.position.y += 1.3 * air + 0.38 * pin + 0.6 * air2;
      // o alvo cai de costas e fica preso
      const fall = seq([[1.22, 0], [1.42, 1, 'out'], [3.2, 1], [3.8, 0, 'io']], T);
      R.lean(-1.4 * fall); R.place(0, RZ + 0.3 * fall, 0); R.armsOut(0.5 * fall);
      if (T > 1.22 && T < 1.4) ctx.shake(0.5);
      // três mordidas, cada uma com seu ritmo
      const BITES = [1.55, 1.98, 2.5];
      BITES.forEach((bt, i) => {
        const op = seq([[bt - 0.22, 0], [bt - 0.06, 1, 'out'], [bt, 0, 'snap']], T);
        const dip = seq([[bt - 0.1, 0], [bt, 1, 'snap'], [bt + 0.18, 0]], T);
        r('mandibula', 0.75 * op, 0, 0);
        r('pescoco', 0.55 * dip, (i - 1) * 0.15 * dip, 0); r('cabeca', 0.25 * dip, 0, (i - 1) * 0.12 * dip);
        if (T > bt && T < bt + 0.7) ctx.get('sangue' + i, blood(70 + i * 13, 24 + i * 6, true)).at(V3((i - 1) * 0.12, 0.42, RZ - 0.15), T - bt, 0.02);
        if (T > bt && T < bt + 0.12) { ctx.shake(0.35); ctx.dark(0.18); }
      });
      return;
    }

    if (mode === 'garras') {
      const T = t % 3.2;
      const R = ctx.ref; const RZ = -2.9; R.place(0, RZ, 0);
      stance(r, P);
      // ergue o corpo, pula e chuta com a foice direita e depois a esquerda: os cortes cruzam em X
      const rear = seq([[0, 0], [0.4, 1, 'out'], [1.3, 1], [1.7, 0, 'io']], T);
      const hop = seq([[0.35, 0], [0.55, 1, 'out'], [1.15, 1], [1.35, 0, 'in']], T);
      const k1 = seq([[0.48, 0], [0.6, 1, 'snap'], [0.75, 0.3]], T) * (T < 0.95 ? 1 : 0);
      const k2 = seq([[0.8, 0], [0.92, 1, 'snap'], [1.1, 0.3], [1.4, 0]], T);
      r('quadril', -0.55 * rear, 0.25 * k1 - 0.25 * k2, 0);
      r('pescoco', -0.35 * rear, 0, 0); r('cabeca', 0.5 * rear, 0, 0);
      r('mandibula', 0.4 * rear, 0, 0);
      r('cauda1', 0.35 * rear, 0, 0); r('cauda2', 0.2 * rear, 0, 0);
      r('pernaD', -1.5 * k1 - 0.3 * rear, 0, 0.2 * k1); r('canelaD', -0.6 * k1, 0, 0); r('foiceD', -0.9 * k1, 0, 0);
      r('pernaE', -1.5 * k2 - 0.3 * rear, 0, -0.2 * k2); r('canelaE', -0.6 * k2, 0, 0); r('foiceE', -0.9 * k2, 0, 0);
      r('bracoD', -0.8 * rear, 0, 0.4 * rear); r('bracoE', -0.8 * rear, 0, -0.4 * rear);
      obj.position.y += 0.55 * hop;
      obj.position.z -= 0.9 * hop;
      const ages = [(T - 0.52) / 1.7, (T - 0.84) / 1.7];
      if (T > 0.52 && T < 2.6) ctx.get('xis', clawX).at(V3(0, 0, RZ + 0.32), ages);
      [0.56, 0.88].forEach((kt, i) => { if (T > kt && T < kt + 0.1) ctx.shake(0.4); });
      const flinch = seq([[0.58, 0], [0.66, 1, 'out'], [1.0, 0.5], [1.5, 0]], T) + seq([[0.9, 0], [0.98, 1, 'out'], [1.4, 0]], T);
      R.lean(-0.2 * flinch); R.spin(0.25 * (seq([[0.58, 0], [0.66, 1]], T) - 2 * seq([[0.9, 0], [0.98, 1]], T)));
      // sangra: gotas caem do peito por um tempo
      if (T > 1.0) ctx.get('sangra', blood(99, 40, false)).at(V3(0, 1.1, RZ + 0.25), T - 1.0, 0.02);
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(5), variant(27), variant(63)];
  fixed[0].skin = SKINS[0]; fixed[0].quills = true; fixed[0].crest = CRESTS[0];
  fixed[1].skin = SKINS[1]; fixed[1].quills = false; fixed[1].scar = true;
  fixed[2].skin = SKINS[2]; fixed[2].quills = true; fixed[2].crest = CRESTS[1];
  for (const v of fixed) { v.belly = mix(v.skin, [244, 226, 176], 0.72); v.stripe = mul(v.skin, 0.6); }
  const def = v => ({ def: rigDef(painters(v), v), low: rigLow(painters(v), v) });
  window.MOBS.push({
    id: 'raptor', nome: 'Raptor', bioma: 'Dinossauros', tier: 'Pequeno · fraco · tier 8', refX: 2.4,
    variants: fixed.map((v, i) => ({ nome: ['Verde', 'Laranja', 'Roxo'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['bote', 'Bote'], ['garras', 'Garras']],
    cam: 5, lookY: 0.8, lookZ: { bote: -2.6, garras: -1.6 }, camFar: { bote: 8, garras: 6.5 }, camYaw: { bote: -1.3, garras: -0.9 },
    info: {
      'Tamanho': '1,3 bloco de altura, 2,4 de comprimento com a cauda',
      'Vida': '34 (tier 8)',
      'Jeito': 'Hostil, em bando de 2 a 3. Um vê e grita, os outros dão a volta pra cercar. Foge quando sobra só um e está fraco.',
      'Bote': 'Agacha e rebola, salta até 10 blocos e cai em cima: você fica preso no chão (não anda nem usa skill) enquanto ele morde três vezes. Bater nele enquanto morde faz ele soltar.',
      'Garras': 'Ergue o corpo, pula e chuta com as duas foices do pé. Os cortes cruzam em X e você sangra por 4 s.',
      'Solta': 'Carne de dinossauro (crua e assada), couro de raptor; raro: garra-foice (adaga leve).',
      'Variações': 'Cor do couro, desenho das faixas, crista de espinhos colorida, cor do olho e cicatriz sorteados.',
    },
  });
})();
