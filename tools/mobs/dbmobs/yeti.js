// Yeti (Montanhas do Norte). Médio, tier 7. Bicho grande e curvado, pelo branco desgrenhado, cara, mãos e pés
// azul-acinzentados. Sozinho; anda devagar e solta vapor pela boca.
// Golpes: Rugido Congelante (enche o peito e ruge um cone de gelo que congela o alvo dentro de um bloco de cristal)
// e Pisão Avalanche (pula com os dois punhos pra cima e soca o chão: um anel de espinhos de gelo brota em volta).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const V3 = (x, y, z) => new THREE.Vector3(x, y, z);

  // ---------- variantes ----------
  const FURS = [[250, 252, 255], [246, 240, 226], [228, 236, 246], [255, 252, 244], [238, 244, 250]];
  const SKINS = [[124, 146, 172], [136, 150, 176], [110, 128, 158], [128, 136, 154]];
  function variant(seed) {
    const R = FX.rnd(seed);
    const pick = a => a[Math.floor(R() * a.length)];
    return {
      seed, fur: pick(FURS), skin: pick(SKINS).map(c => c * (0.94 + R() * 0.1)),
      old: R() < 0.25, icy: R() < 0.35, scar: R() < 0.3, brokenTusk: R() < 0.25, size: 0.95 + R() * 0.1,
    };
  }

  function painters(v) {
    const S = v.seed;
    const furBase = v.old ? mix(v.fur, [176, 178, 180], 0.35) : v.fur;
    const shadow = [150, 170, 204];
    // pelo: sombra puxa pro azul (frio), mechas descem na direção do membro
    const fur = (q, s = 1) => {
      const f = 0.66 - 0.36 * q.ny + 0.14 * (n3(q.ax * 0.22, q.ay * 0.22, q.az * 0.22, s + S) - 0.5) + (q.f === 'top' ? 0.22 : 0) - (q.f === 'bottom' ? 0.35 : 0);
      let c = f > 0.6 ? mix(furBase, [255, 255, 255], Math.min(1, (f - 0.6) * 1.2)) : mix(shadow, furBase, Math.min(1, Math.max(0, 0.45 + f * 0.95)));
      // mechas: riscos finos que descem, cada coluna com seu comprimento
      const col = Math.floor((q.f === 'north' || q.f === 'south' ? q.ax : q.az) * 1.0);
      const st = n3(col * 0.71, Math.floor(q.ay * 0.35) * 0.9, 0, 7 + S);
      if (st < 0.22) c = mix(c, shadow, 0.22);
      else if (st > 0.82) c = mix(c, [255, 255, 255], 0.45);
      if (v.icy && q.f === 'top' && n3(q.ax * 0.6, 0, q.az * 0.6, 21 + S) > 0.6) c = [196, 232, 252];
      return c;
    };
    const skin = (q, s = 2) => {
      const f = 0.6 - 0.4 * q.ny + 0.14 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.2 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      let c = cel(v.skin, f, 0.78, 1.14);
      if (fr(q.ay * 0.5 + n3(q.ax * 0.6, q.ay * 0.2, q.az * 0.6, 3 + S) * 1.4) < 0.08) c = mul(c, 0.86);     // rugas finas
      return c;
    };
    // franja: tira fina de pelo que pende; a borda de baixo é rasgada, cada tufo de um comprimento
    const fringe = (len, axis) => q => {
      const k = Math.floor(axis === 'x' ? q.ax : q.az);
      const cut = len * (0.35 + 0.65 * n3(k * 0.83, q.ay * 0.0, 0, 31 + S)) + (n3(k * 2.7, 5, 0, 32 + S) > 0.6 ? len * 0.25 : 0);
      if (q.py > cut) return null;
      if (q.f === 'top' || q.f === 'bottom') return null;
      const c = fur(q, 4);
      // pontas claras (pegam luz), raiz mais escura: cada fileira se descola da de baixo
      return q.py > cut - 1.2 ? mix(c, [255, 255, 255], 0.35) : (q.py < 1.4 ? mix(c, shadow, 0.3) : c);
    };
    // camadas de pelo (telhas): fileiras de franja sobrepostas na frente e atrás do peito
    const shingles = (x, w, z, y0, rows, step, len) => {
      const out = [];
      for (let i = 0; i < rows; i++) out.push({ x, y: y0 + i * step, z: z - Math.sign(z) * 0.0 + Math.sign(z) * 0.05 * i, w, h: len, d: 0.3, p: fringe(len, 'x') });
      return out;
    };
    const under = q => mix(fur(q, 13), shadow, 0.15);
    const P = { fur, skin, fringe, shingles, under };
    P.head = q => {
      // a cara (pele) fica só no meio da face da frente, emoldurada de pelo
      if (q.f !== 'north') return fur(q, 5);
      const x = q.px, y = q.py;
      const inFace = Math.abs(x - 4.5) < 3.3 - Math.max(0, 2.4 - y) * 0.6 && y > 1.6;
      if (!inFace) return fur(q, 5);
      let c = skin(q, 6);
      const ix = Math.floor(x), iy = Math.floor(y);
      if (iy === 3 && (ix === 2 || ix === 6)) c = [214, 236, 248];                                   // olho claro, gelado
      if (iy === 3 && (ix === 3 || ix === 5)) c = [20, 24, 36];
      if (iy === 2 && ix >= 1 && ix <= 7) c = mul(v.skin, 0.62);                                    // sobrancelha funda
      if (iy === 5 && (ix === 3 || ix === 5)) c = mul(v.skin, 0.55);                                // narinas
      if (v.scar && ix === 6 && iy >= 2 && iy <= 5) c = mix(c, [190, 120, 130], 0.6);
      return c;
    };
    P.brow = q => (q.f === 'bottom' ? mul(fur(q, 8), 0.7) : fur(q, 8));
    P.jaw = q => {
      if (q.f !== 'north') return q.f === 'top' ? [120, 40, 52] : skin(q, 9);
      const ix = Math.floor(q.px), iy = Math.floor(q.py);
      let c = skin(q, 9);
      if (iy === 0) c = [70, 20, 32];
      // presas de baixo, uma às vezes quebrada
      if ((ix === 1 || ix === 5) && iy <= 1) c = (v.brokenTusk && ix === 5 && iy === 0) ? [70, 20, 32] : [236, 232, 214];
      return c;
    };
    P.mouth = () => [70, 18, 30];
    P.hand = q => skin(q, 10);
    P.claw = q => cel([70, 64, 66], 0.4 + 0.5 * (1 - q.ny), 0.8, 1.4);
    P.foot = q => skin(q, 11);
    P.lowBody = q => fur(q, 12);
    return P;
  }

  function rigDef(P, v) {
    const s16 = 1;
    const arm = s => {
      const D = s < 0;
      return {
        name: D ? 'bracoD' : 'bracoE', pivot: [s * 9.2, -12.5, 0], rot: [-0.25, 0, s * 0.12],
        boxes: [
          { x: -2.7, y: -2, z: -2.7, w: 5.4, h: 11, d: 5.4, p: P.fur },
          { x: -3.3, y: -2.6, z: -3.1, w: 6.6, h: 4.2, d: 6.2, p: P.fur },                         // ombro peludo
          { x: -3.4, y: 1.4, z: -3.2, w: 6.8, h: 4.4, d: 0.3, p: P.fringe(4.4, 'x') },
          { x: -3.4, y: 1.4, z: 2.9, w: 6.8, h: 4.4, d: 0.3, p: P.fringe(4.4, 'x') },
          { x: s * 3.1 - 0.15, y: 1.4, z: -3.2, w: 0.3, h: 4.4, d: 6.4, p: P.fringe(4.4, 'z') },
          { x: -3.4, y: 6.4, z: -2.95, w: 6.8, h: 4.2, d: 0.3, p: P.fringe(4.2, 'x') },
          { x: s * 2.85 - 0.15, y: 6.4, z: -2.9, w: 0.3, h: 4.2, d: 5.8, p: P.fringe(4.2, 'z') },
        ],
        kids: [{
          name: D ? 'anteD' : 'anteE', pivot: [0, 9, 0], rot: [-0.2, 0, 0], boxes: [
            { x: -3, y: 0, z: -3, w: 6, h: 9, d: 6, p: P.fur },
            { x: s * 3.05 - 0.15, y: 3, z: -3, w: 0.3, h: 6.5, d: 6, p: P.fringe(6.5, 'z') },        // franja de fora do antebraço
            { x: -3, y: 3, z: 3.05, w: 6, h: 6.5, d: 0.3, p: P.fringe(6.5, 'x') },
          ],
          kids: [{
            name: D ? 'maoD' : 'maoE', pivot: [0, 9, 0], boxes: [
              { x: -3.1, y: -0.4, z: -3.3, w: 6.2, h: 4.4, d: 6.6, p: P.hand },
              { x: -2.6, y: 3.6, z: -3.9, w: 1.2, h: 1.2, d: 1.4, p: P.claw },
              { x: -0.6, y: 3.6, z: -4.1, w: 1.2, h: 1.2, d: 1.6, p: P.claw },
              { x: 1.4, y: 3.6, z: -3.9, w: 1.2, h: 1.2, d: 1.4, p: P.claw },
            ],
          }],
        }],
      };
    };
    const leg = s => {
      const D = s < 0;
      return {
        name: D ? 'pernaD' : 'pernaE', pivot: [s * 4.5, 12, 0], boxes: [{ x: -3.3, y: -1, z: -3.3, w: 6.6, h: 7.2, d: 6.6, p: P.fur }],
        kids: [{
          name: D ? 'canelaD' : 'canelaE', pivot: [0, 6, 0], boxes: [
            { x: -2.8, y: 0, z: -2.8, w: 5.6, h: 4, d: 5.6, p: P.fur },
            { x: -2.8, y: 1.2, z: -2.95, w: 5.6, h: 3.6, d: 0.3, p: P.fringe(3.6, 'x') },
          ],
          kids: [{
            name: D ? 'peD' : 'peE', pivot: [0, 4, 0], boxes: [
              { x: -3, y: 0, z: -6, w: 6, h: 2, d: 9, p: P.foot },
              { x: -2.6, y: 0.9, z: -6.9, w: 1.2, h: 1.1, d: 1.2, p: P.claw },
              { x: -0.6, y: 0.9, z: -7.0, w: 1.2, h: 1.1, d: 1.3, p: P.claw },
              { x: 1.4, y: 0.9, z: -6.9, w: 1.2, h: 1.1, d: 1.2, p: P.claw },
            ],
          }],
        }],
      };
    };
    const ice = v.icy ? [
      { x: -7, y: -15.4, z: -2, w: 1.2, h: 1.6, d: 1.2, p: () => [190, 230, 250] },
      { x: 4.5, y: -15.8, z: 0, w: 1.4, h: 2, d: 1.4, p: () => [176, 222, 248] },
    ] : [];
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'quadril', pivot: [0, 12, 0], boxes: [{ x: -7, y: -4, z: -4.6, w: 14, h: 5, d: 9.2, p: P.fur }], kids: [{
            name: 'tronco', pivot: [0, -4, 0], rot: [0.26, 0, 0], boxes: [
              { x: -8.5, y: -15, z: -5.5, w: 17, h: 8.5, d: 11, p: P.under },                              // peito largo
              { x: -7, y: -6.8, z: -5.2, w: 14, h: 7, d: 10, p: P.under },                                  // barriga mais estreita
              { x: -6.5, y: -17.4, z: -1.5, w: 13, h: 6, d: 7.5, p: P.fur },                          // corcunda
              { x: -7, y: -0.6, z: -5.55, w: 14, h: 5, d: 0.3, p: P.fringe(5, 'x') },                      // saia de pelo
              ...P.shingles(-8.4, 16.8, -5.9, -13.5, 2, 4.4, 5.2),
              ...P.shingles(-6.9, 13.8, -5.6, -4.8, 1, 4.4, 4.6),
              ...P.shingles(-8.4, 16.8, 5.6, -16.5, 2, 4.2, 5.4),
              ...P.shingles(-6.9, 13.8, 5.1, -7.4, 2, 4.2, 5.0),
              { x: -7, y: -0.6, z: 4.85, w: 14, h: 5, d: 0.3, p: P.fringe(5, 'x') },
              { x: -7.35, y: -0.6, z: -5.2, w: 0.3, h: 5, d: 10, p: P.fringe(5, 'z') },
              { x: -8.85, y: -7.4, z: -5.5, w: 0.3, h: 5, d: 11, p: P.fringe(5, 'z') },
              { x: 7.05, y: -0.6, z: -5.2, w: 0.3, h: 5, d: 10, p: P.fringe(5, 'z') },
              { x: 8.55, y: -7.4, z: -5.5, w: 0.3, h: 5, d: 11, p: P.fringe(5, 'z') },
              ...ice,
            ], kids: [
              {
                name: 'cabeca', pivot: [0, -14, -3.6], rot: [-0.3, 0, 0], boxes: [
                  { x: -4.5, y: -8, z: -5.5, w: 9, h: 8, d: 8.5, p: P.head },
                  { x: -4.6, y: -6.4, z: -6.4, w: 9.2, h: 1.4, d: 1.2, p: P.brow },
                  { x: -5, y: -9, z: -4, w: 10, h: 2.2, d: 7.5, p: P.fur },                            // topete
                  { x: -5, y: -8.6, z: -5.85, w: 10, h: 3.2, d: 0.3, p: P.fringe(3.2, 'x') },             // franja na testa
                  { x: -5.15, y: -8.2, z: -5.5, w: 0.3, h: 6.5, d: 8, p: P.fringe(6.5, 'z') },
                  { x: 4.85, y: -8.2, z: -5.5, w: 0.3, h: 6.5, d: 8, p: P.fringe(6.5, 'z') },
                ],
                kids: [{ name: 'mandibula', pivot: [0, -1.6, -2.4], boxes: [{ x: -3.3, y: -0.4, z: -3.6, w: 6.6, h: 2.8, d: 4.4, p: P.jaw }, { x: -2.6, y: -0.8, z: -3.2, w: 5.2, h: 0.6, d: 3.4, p: P.mouth }] }],
              },
              arm(-1), arm(1),
            ],
          }],
        },
        leg(-1), leg(1),
      ],
    };
  }
  function rigLow(P, v) {
    const arm = s => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * 9.2, -12.5, 0], rot: [-0.25, 0, s * 0.12],
      boxes: [{ x: -2.9, y: -2, z: -2.9, w: 5.8, h: 19.5, d: 5.8, p: P.fur }],
      kids: [{ name: s < 0 ? 'maoD' : 'maoE', pivot: [0, 17.5, -0.4], boxes: [{ x: -3.1, y: 0, z: -3.3, w: 6.2, h: 4, d: 6.6, p: P.hand }] }],
    });
    const leg = s => ({
      name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * 4.5, 12, 0], boxes: [{ x: -3.2, y: -1, z: -3.2, w: 6.4, h: 11, d: 6.4, p: P.fur }, { x: -3, y: 10, z: -6, w: 6, h: 2, d: 9, p: P.foot }],
    });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'quadril', pivot: [0, 12, 0], boxes: [{ x: -7, y: -4, z: -4.6, w: 14, h: 5, d: 9.2, p: P.lowBody }], kids: [{
            name: 'tronco', pivot: [0, -4, 0], rot: [0.32, 0, 0], boxes: [{ x: -8.5, y: -17, z: -5.5, w: 17, h: 18.5, d: 11, p: P.lowBody }], kids: [
              { name: 'cabeca', pivot: [0, -14, -3.6], rot: [-0.3, 0, 0], boxes: [{ x: -4.5, y: -8.6, z: -5.5, w: 9, h: 8.6, d: 8.5, p: P.head }], kids: [{ name: 'mandibula', pivot: [0, -1.6, -2.4], boxes: [{ x: -3.3, y: -0.4, z: -3.6, w: 6.6, h: 2.8, d: 4.4, p: P.jaw }] }] },
              arm(-1), arm(1),
            ],
          }],
        },
        leg(-1), leg(1),
      ],
    };
  }

  // ---------- gelo procedural: cada cristal nasce com o próprio número de faces, raio, dobra e ponta ----------
  // Usado no anel de espinhos (Pisão) e na prisão de gelo (Rugido). Nenhum cristal é cópia de outro.
  function crystals(spec) {
    const R = FX.rnd(spec.seed);
    const pos = [], nor = [], del = [], hgt = [], base = [];
    const push = (a, b, c, d, h, bc) => {
      const e1 = [b[0] - a[0], b[1] - a[1], b[2] - a[2]], e2 = [c[0] - a[0], c[1] - a[1], c[2] - a[2]];
      let n = [e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]];
      const L = Math.hypot(...n) || 1; n = n.map(x => x / L);
      for (const p of [a, b, c]) { pos.push(...p); nor.push(...n); del.push(d); hgt.push(h); base.push(...bc); }
    };
    for (let i = 0; i < spec.n; i++) {
      const u = (i + R() * 0.8) / spec.n;
      const ang = u * Math.PI * 2 + (R() - 0.5) * 0.3;
      const rk = spec.ring ? Math.pow(R(), 0.8) : R();
      const r = spec.rMin + rk * (spec.rMax - spec.rMin);
      const far = (r - spec.rMin) / Math.max(0.01, spec.rMax - spec.rMin);
      const bx = Math.sin(ang) * r, bz = -Math.cos(ang) * r;
      const out = [Math.sin(ang), 0, -Math.cos(ang)];
      const h = (spec.hMin + (spec.hMax - spec.hMin) * (spec.ring ? (1 - far * 0.6) : 1) * (0.55 + R() * 0.45));
      const br = spec.bMin + (spec.bMax - spec.bMin) * (spec.ring ? (1 - far * 0.5) : 1) * (0.6 + R() * 0.4);
      const k = 4 + Math.floor(R() * 3);
      const tilt = spec.tilt * (0.5 + R() * 0.8);
      const tw = R() * Math.PI;
      const kink = 0.3 + R() * 0.3;
      const d = spec.delay * (spec.ring ? far : R());
      const ring = (y, rad, off) => {
        const pts = [];
        for (let j = 0; j < k; j++) {
          const a = tw + j / k * Math.PI * 2, rr = rad * (0.75 + R() * 0.5);
          pts.push([bx + out[0] * off + Math.cos(a) * rr, y, bz + out[2] * off + Math.sin(a) * rr]);
        }
        return pts;
      };
      const r0 = ring(-0.25, br, 0), r1 = ring(h * kink, br * (0.7 + R() * 0.25), tilt * h * kink);
      const tip = [bx + out[0] * tilt * h + (R() - 0.5) * 0.08, h, bz + out[2] * tilt * h + (R() - 0.5) * 0.08];
      const bc = [bx, 0, bz];
      for (let j = 0; j < k; j++) {
        const j2 = (j + 1) % k;
        push(r0[j], r1[j], r0[j2], d, h, bc); push(r0[j2], r1[j], r1[j2], d, h, bc);
        push(r1[j], tip, r1[j2], d, h, bc);
      }
    }
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    geo.setAttribute('normal', new THREE.Float32BufferAttribute(nor, 3));
    geo.setAttribute('aDelay', new THREE.Float32BufferAttribute(del, 1));
    geo.setAttribute('aH', new THREE.Float32BufferAttribute(hgt, 1));
    geo.setAttribute('aBase', new THREE.Float32BufferAttribute(base, 3));
    const mat = new THREE.ShaderMaterial({
      transparent: spec.glass, depthWrite: !spec.glass, side: THREE.DoubleSide,
      uniforms: { uAge: { value: 0 }, uSink: { value: 9 }, uLevel: { value: 99 }, uCrack: { value: 0 }, uGlass: { value: spec.glass ? 1 : 0 } },
      vertexShader: `
        attribute float aDelay; attribute float aH; attribute vec3 aBase;
        uniform float uAge, uSink;
        varying vec3 vN; varying vec3 vW; varying float vY;
        void main(){
          float g = smoothstep(aDelay, aDelay + 0.12, uAge);
          float s = smoothstep(uSink + aDelay * 0.5, uSink + aDelay * 0.5 + 0.5, uAge);
          vec3 p = position;
          p.y -= (1.0 - g) * (aH + 0.3) + s * (aH + 0.3);
          vY = clamp(position.y / max(aH, 0.01), 0.0, 1.0);
          vec4 w = modelMatrix * vec4(p, 1.0);
          vW = w.xyz; vN = normalize(mat3(modelMatrix) * normal);
          gl_Position = projectionMatrix * viewMatrix * w;
        }`,
      fragmentShader: `
        uniform float uLevel, uCrack, uGlass;
        varying vec3 vN; varying vec3 vW; varying float vY;
        float h(vec3 p){ p = fract(p*0.3183099+vec3(.71,.113,.419)); p*=17.0; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }
        float n3(vec3 x){ vec3 i=floor(x), f=fract(x); f=f*f*(3.0-2.0*f);
          return mix(mix(mix(h(i),h(i+vec3(1,0,0)),f.x),mix(h(i+vec3(0,1,0)),h(i+vec3(1,1,0)),f.x),f.y),
                     mix(mix(h(i+vec3(0,0,1)),h(i+vec3(1,0,1)),f.x),mix(h(i+vec3(0,1,1)),h(i+vec3(1,1,1)),f.x),f.y),f.z); }
        void main(){
          // a prisão cresce do pé pra cima com a borda de cima irregular
          float jag = (n3(vec3(vW.xz * 7.0, 1.3)) - 0.5) * 0.18;
          if (vW.y > uLevel + jag) discard;
          vec3 n = normalize(vN); vec3 V = normalize(cameraPosition - vW);
          if (dot(n, V) < 0.0) n = -n;
          float lam = clamp(dot(n, normalize(vec3(0.4, 0.9, 0.3))), 0.0, 1.0);
          float fres = pow(1.0 - clamp(dot(n, V), 0.0, 1.0), 2.0);
          vec3 deep = vec3(0.18, 0.40, 0.72), light = vec3(0.78, 0.93, 1.0);
          vec3 c = mix(deep, light, 0.25 + 0.6 * vY) * (0.55 + 0.55 * lam);
          // nuvem branca presa dentro do gelo
          float cl = n3(vW * 4.3) * 0.6 + n3(vW * 9.1) * 0.4;
          c = mix(c, vec3(0.92, 0.97, 1.0), smoothstep(0.55, 0.85, cl) * 0.5);
          c += fres * vec3(0.55, 0.8, 1.0) * 0.8;
          // rachaduras: aparecem antes de quebrar
          float cr = abs(n3(vW * 5.0 + 3.1) - 0.5);
          float crack = (1.0 - smoothstep(0.0, 0.02 + 0.03 * uCrack, cr)) * uCrack;
          c = mix(c, vec3(1.0), crack);
          float a = uGlass > 0.5 ? clamp(0.42 + fres * 0.45 + crack * 0.5 + smoothstep(0.6, 0.9, cl) * 0.2, 0.0, 0.95) : 1.0;
          gl_FragColor = vec4(c, a);
        }`,
    });
    const m = new THREE.Mesh(geo, mat);
    m.frustumCulled = false;
    m.at = (c, age, o = {}) => {
      m.visible = age > 0; m.position.copy(c);
      const u = mat.uniforms; u.uAge.value = age; u.uSink.value = o.sink ?? 9; u.uLevel.value = o.level ?? 99; u.uCrack.value = o.crack || 0;
    };
    return m;
  }

  // Geada no chão: cristais finos que se espalham no cone do rugido (Voronoi + penas de gelo).
  function frost() {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false,
      uniforms: { uO: { value: V3(0, 0, 0) }, uDir: { value: 0 }, uReach: { value: 0 }, uFade: { value: 1 } },
      vertexShader: 'varying vec3 vW; void main(){ vec4 w = modelMatrix*vec4(position,1.0); vW = w.xyz; gl_Position = projectionMatrix*viewMatrix*w; }',
      fragmentShader: `
        uniform vec3 uO; uniform float uDir, uReach, uFade; varying vec3 vW;
        vec2 h2(vec2 p){ p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3))); return fract(sin(p) * 43758.5453); }
        float n2(vec2 p){ vec2 i = floor(p), f = fract(p); f = f*f*(3.0-2.0*f);
          return mix(mix(h2(i).x, h2(i+vec2(1,0)).x, f.x), mix(h2(i+vec2(0,1)).x, h2(i+vec2(1,1)).x, f.x), f.y); }
        void main(){
          vec2 d = vW.xz - uO.xz;
          float dist = length(d);
          float ang = atan(d.x, -d.y) - uDir;
          float wob = (n2(vec2(dist * 2.0, 3.0)) - 0.5) * 0.25;
          float cone = smoothstep(0.62 + wob, 0.42 + wob, abs(ang)) * smoothstep(0.1, 0.5, dist);
          float edge = uReach + (n2(vec2(ang * 9.0, 1.7)) - 0.5) * 0.8;
          float m = cone * smoothstep(edge, edge - 0.6, dist);
          if (m < 0.01) discard;
          // células de gelo: bordas finas e brilhantes, miolo fosco
          vec2 p = vW.xz * 3.2; vec2 ip = floor(p); float f1 = 9.0, f2 = 9.0;
          for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
            vec2 g = vec2(float(i), float(j)); vec2 o = h2(ip + g);
            float e = length(g + o - fract(p));
            if (e < f1) { f2 = f1; f1 = e; } else if (e < f2) f2 = e;
          }
          float line = 1.0 - smoothstep(0.0, 0.07, f2 - f1);
          float feather = smoothstep(0.55, 0.8, n2(vW.xz * 11.0 + n2(vW.xz * 3.0) * 4.0));
          vec3 c = mix(vec3(0.72, 0.86, 0.97), vec3(1.0), line * 0.8 + feather * 0.3);
          float a = m * (0.5 + 0.4 * line + 0.15 * feather) * uFade;
          gl_FragColor = vec4(c, a);
        }`,
    });
    const g = new THREE.PlaneGeometry(16, 16); g.rotateX(-Math.PI / 2);
    const m = new THREE.Mesh(g, mat);
    m.frustumCulled = false;
    m.at = (o, dir, reach, fade) => { m.visible = true; m.position.set(o.x, 0.025, o.z); mat.uniforms.uO.value.copy(o); mat.uniforms.uDir.value = dir; mat.uniforms.uReach.value = reach; mat.uniforms.uFade.value = fade; };
    return m;
  }

  function stance(r, P) {
    r('quadril', 0, 0, 0);
    r('pernaD', -0.1, 0.12, 0.08); r('canelaD', 0.15, 0, 0);
    r('pernaE', 0.05, -0.12, -0.08); r('canelaE', 0.12, 0, 0);
  }
  const WALK_T = [[0, -0.36], [0.16, -0.2], [0.4, 0.12], [0.55, 0.36], [0.68, 0.18], [0.85, -0.26]];
  const WALK_K = [[0, 0.05], [0.16, 0.1], [0.42, 0.05], [0.58, 0.3], [0.72, 0.7], [0.88, 0.2]];
  const WALK_B = [[0, 0.2], [0.16, 0.7], [0.4, -0.1], [0.5, 0.2], [0.66, 0.7], [0.9, -0.1]];

  function mouthOf(obj, P) {
    obj.updateMatrixWorld(true);
    return P.mandibula.obj.localToWorld(V3(0, 0.6, -4.2));
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const breath = loop([[0, 0], [0.45, 1], [0.999, 0]], t / 3.0);

    if (mode === 'parado') {
      stance(r, P);
      r('tronco', -0.05 * breath, 0, 0);
      r('bracoD', 0.04 * breath, 0, -0.04 * breath); r('bracoE', 0.04 * breath, 0, 0.04 * breath);
      r('cabeca', -0.04 * breath + 0.08 * loop([[0, 0], [0.3, 1], [0.6, 0]], t / 6.2), 0.3 * loop([[0, 0], [0.2, 0], [0.35, 1], [0.6, 1], [0.75, -0.6], [0.9, 0]], t / 7.0), 0);
      r('mandibula', 0.12 * seq([[0.5, 0], [0.62, 1], [0.8, 0]], fr(t / 3.0)), 0, 0);
      // vapor na boca a cada respiração
      const k = fr(t / 3.0);
      if (k > 0.55) {
        const m = mouthOf(obj, P);
        ctx.get('vapor', FX.volume).set(m.add(V3(0, 0.05, -0.15 - (k - 0.55) * 0.5)), 0.2, (k - 0.55) / 0.45, { seed: Math.floor(t / 3.0) * 3.7, dens: 4, rise: 0.4, smoke: [0.92, 0.95, 1.0] });
      }
      return;
    }
    if (mode === 'andar') {
      const ph = t * 0.7;
      for (const [s, off] of [['D', 0], ['E', 0.5]]) {
        r('perna' + s, loop(WALK_T, ph + off), 0, 0);
        r('canela' + s, loop(WALK_K, ph + off), 0, 0);
        r('pe' + s, -0.25 * loop(WALK_K, ph + off - 0.06), 0, 0);
        r('braco' + s, -0.35 * loop(WALK_T, ph + off + 0.5), 0, (s === 'D' ? -1 : 1) * 0.06);
        r('ante' + s, -0.15 - 0.2 * Math.max(0, loop(WALK_T, ph + off + 0.4)), 0, 0);
      }
      P.quadril.obj.position.y += loop(WALK_B, ph);
      r('tronco', 0.05, 0.12 * loop([[0, 1], [0.5, -1]], ph), 0.05 * loop([[0, 1], [0.5, -1]], ph));
      r('cabeca', -0.05, -0.08 * loop([[0, 1], [0.5, -1]], ph), 0);
      return;
    }

    if (mode === 'rugido') {
      const T = t % 3.9;
      const R = ctx.ref; const RZ = -5.2; R.place(0, RZ, 0);
      stance(r, P);
      // enche o peito (sobe e abre os braços), depois joga o corpo pra frente e ruge
      const inh = seq([[0, 0], [0.7, 1, 'out'], [0.8, 1]], T);
      const roar = seq([[0.75, 0], [0.88, 1, 'snap'], [2.0, 1], [2.6, 0, 'io']], T);
      const pre = inh * (1 - roar);
      r('tronco', -0.42 * pre + 0.22 * roar, 0, 0);
      r('cabeca', -0.35 * pre + 0.25 * roar, 0, 0);
      r('mandibula', 0.2 * pre + 0.75 * roar, 0, 0);
      r('bracoD', 0.25 * pre + 0.55 * roar, 0, -0.65 * pre - 0.3 * roar); r('anteD', -0.4 * pre - 0.5 * roar, 0, 0);
      r('bracoE', 0.25 * pre + 0.55 * roar, 0, 0.65 * pre + 0.3 * roar); r('anteE', -0.4 * pre - 0.5 * roar, 0, 0);
      r('pernaD', -0.25 * roar, 0, 0); r('canelaD', 0.35 * roar, 0, 0); r('pernaE', 0.2 * roar, 0, 0);
      P.quadril.obj.position.y += 1.2 * roar - 0.6 * pre;
      // tremor do rugido no corpo todo
      if (roar > 0.5) r('cabeca', 0, 0.04 * Math.sin(T * 70), 0.03 * Math.sin(T * 53));

      const mouth = mouthOf(obj, P);
      const tgt = V3(0, 1.1, RZ);
      const dir = tgt.clone().sub(mouth).normalize();
      // vapor juntando na boca enquanto puxa o ar
      if (T > 0.15 && T < 0.85) ctx.get('puxa', FX.volume).set(mouth.clone().add(dir.clone().multiplyScalar(0.25)), 0.22 + 0.1 * inh, (T - 0.15) / 0.75, { seed: 4.2, dens: 5, rise: -0.1, smoke: [0.85, 0.93, 1.0] });
      // o sopro: nuvens frias de tamanhos diferentes que correm até o alvo e se abrem
      if (T > 0.82 && T < 2.9) {
        const SP = [[0.0, 0.4, 1.4], [0.07, 0.55, 2.2], [0.15, 0.7, 2.9], [0.24, 0.9, 3.7], [0.34, 1.05, 4.4], [0.46, 1.2, 5.0], [0.6, 1.0, 5.4], [0.78, 0.8, 5.2]];
        SP.forEach(([dl, rad, reach], i) => {
          const age = (T - 0.82 - dl) / 1.35;
          if (age <= 0 || age >= 1) return;
          const travel = Math.min(1, age * 2.4);
          const c = mouth.clone().add(dir.clone().multiplyScalar(0.3 + reach * travel));
          c.y = Math.max(c.y, rad * 0.55);
          ctx.get('sopro' + i, FX.volume).set(c, rad * (0.45 + 0.55 * travel), age, { seed: 11.3 + i * 5.17, dens: 7 - i * 0.4, rise: 0.12, squash: 0.85, smoke: [0.7 + i * 0.025, 0.84 + i * 0.015, 1.0] });
        });
        // cristais de gelo soltos no vento
        const basis = { f: dir, r: V3(-dir.z, 0, dir.x).normalize(), u: V3(0, 1, 0) };
        ctx.get('cristais', () => DBFX.grains({
          n: 170, seed: 77, palette: [[120, 190, 245], [90, 160, 230], [170, 220, 250], [60, 130, 210]], size: [0.02, 0.055], drag: 1.4, grav: 0.5, life: 0.95, spread: 1.0,
          cone: Rr => [(Rr() - 0.5) * 3.2, (Rr() - 0.4) * 1.8, 6 + Rr() * 4],
        })).at(mouth, T - 0.82, 0, basis);
        ctx.get('luzFria', () => DBFX.light(0x9fdcff)).at(mouth.clone().add(dir.clone().multiplyScalar(0.6)), 2.2 * roar, 5);
        ctx.shake(0.45 * roar);
      }
      // geada no chão, em cone, que derrete devagar no fim
      if (T > 0.85) {
        const g0 = V3(mouth.x, 0, mouth.z);
        ctx.get('geada', frost).at(g0, Math.atan2(dir.x, -dir.z), 6.2 * seq([[0.85, 0], [1.7, 1, 'out']], T), 1 - seq([[3.2, 0], [3.9, 1]], T));
      }
      // o alvo: é empurrado, congela dentro de uma prisão de cristal, racha e quebra
      const hit = seq([[1.0, 0], [1.15, 1, 'out']], T);
      const frozen = T > 1.15 && T < 2.95;
      if (!frozen) R.lean(-0.25 * hit * (T < 1.15 ? 1 : 0));
      if (frozen) { R.lean(-0.25); R.armsOut(0.45); R.punch(-0.3); }
      if (T > 1.1) {
        const lvl = 2.3 * seq([[1.1, 0], [1.7, 1, 'out']], T);
        const crack = seq([[2.35, 0], [2.92, 1]], T);
        if (T < 2.95) {
          const pr = ctx.get('prisao', () => crystals({ seed: 909, n: 11, rMin: 0.12, rMax: 0.5, hMin: 1.6, hMax: 2.35, bMin: 0.28, bMax: 0.46, tilt: 0.12, delay: 0, glass: true }));
          pr.at(V3(0, 0, RZ), 1, { level: lvl, crack });
          ctx.blur(0.12 * Math.min(1, lvl / 2));
          if (T > 2.4) R.place(0.03 * Math.sin(T * 90) * crack, RZ, 0);
        }
        if (T >= 2.95) {
          const k = T - 2.95;
          ctx.get('estilhaco', () => DBFX.grains({
            n: 90, seed: 33, palette: [[230, 246, 255], [180, 220, 250], [120, 180, 230], [255, 255, 255]], size: [0.03, 0.09], drag: 0.8, grav: 9, life: 0.9, spread: 0.05,
            cone: Rr => { const a = Rr() * Math.PI * 2, o = 1.5 + Rr() * 3; return [Math.cos(a) * o, 1 + Rr() * 3.5, Math.sin(a) * o]; },
          })).at(V3(0, 0.3, RZ), k, 0);
          ctx.get('poQuebra', FX.volume).set(V3(0, 1.0, RZ), 0.9, k / 0.9, { seed: 2.9, dens: 5, rise: 0.3, smoke: [0.86, 0.93, 1.0] });
          R.lean(-0.25 * (1 - seq([[2.95, 0], [3.5, 1]], T)));
          ctx.shake(Math.max(0, 0.5 - k));
        }
      }
      return;
    }

    if (mode === 'pisao') {
      const T = t % 3.8;
      const R = ctx.ref; const RZ = -4.6; R.place(0, RZ, 0);
      stance(r, P);
      // agacha com os dois braços pra cima, pula pra frente e soca o chão com os punhos juntos
      const crouch = seq([[0, 0], [0.5, 1, 'out'], [0.6, 1]], T);
      const jump = seq([[0.55, 0], [1.05, 1, 'io']], T);
      const slam = seq([[0.98, 0], [1.08, 1, 'snap'], [2.3, 1], [3.1, 0, 'io']], T);
      const down = seq([[0.98, 0], [1.08, 1, 'snap']], T);
      const up = Math.max(crouch, jump) * (1 - down);
      r('bracoD', -2.75 * up - 1.25 * slam, 0, -0.25 * up + 0.35 * slam); r('anteD', -0.25 * up - 0.2 * slam, 0, 0);
      r('bracoE', -2.75 * up - 1.25 * slam, 0, 0.25 * up - 0.35 * slam); r('anteE', -0.25 * up - 0.2 * slam, 0, 0);
      r('maoD', 0, 0, 0.3 * slam); r('maoE', 0, 0, -0.3 * slam);
      const air = jump * (1 - jump) * 4;
      r('tronco', -0.32 * up + 0.62 * slam, 0, 0);
      r('cabeca', 0.2 * up - 0.45 * slam, 0, 0);
      r('mandibula', 0.5 * slam * (1 - seq([[1.6, 0], [2.3, 1]], T)), 0, 0);
      const cr = crouch * (1 - jump) + slam * 0.8;
      r('pernaD', -0.75 * cr - 0.5 * air, 0, 0); r('canelaD', 1.2 * cr + 0.6 * air, 0, 0); r('peD', -0.4 * cr, 0, 0);
      r('pernaE', -0.75 * cr - 0.5 * air, 0, 0); r('canelaE', 1.2 * cr + 0.6 * air, 0, 0); r('peE', -0.4 * cr, 0, 0);
      P.quadril.obj.position.y += 4.0 * cr;
      obj.position.z -= 2.2 * jump;
      obj.position.y += 1.5 * Math.sin(Math.PI * jump) * (T < 1.06 ? 1 : 0);
      const C = V3(0, 0, -2.2 - 1.15);
      if (T > 1.06) {
        const k = T - 1.06;
        ctx.get('espinhos', () => crystals({ seed: 515, n: 38, ring: true, rMin: 0.55, rMax: 3.1, hMin: 0.45, hMax: 1.55, bMin: 0.1, bMax: 0.3, tilt: 0.28, delay: 0.38, glass: false })).at(C, k, { sink: 1.5 });
        ctx.roarRing(V3(C.x, 0.03, C.z), k / 0.5);
        ctx.shake(Math.max(0, 1.0 - k * 1.6));
        // a neve do chão sobe em nuvens, cada uma do seu tamanho
        [[0, 0, 0.0, 1.2], [-1.4, 0.5, 0.06, 0.85], [1.3, -0.6, 0.1, 0.9], [0.3, 1.6, 0.16, 0.75], [-0.4, -1.5, 0.22, 0.7]].forEach(([dx, dz, dl, rad], i) => {
          const age = (k - dl) / 1.6; if (age <= 0 || age >= 1) return;
          ctx.get('neve' + i, FX.volume).set(V3(C.x + dx, rad * 0.6 + age * 0.3, C.z + dz), rad, age, { seed: 40.1 + i * 7.3, dens: 7, rise: 0.25, smoke: [0.8, 0.86, 0.95] });
        });
        ctx.get('blocos', () => FX.debris(28, 52, [[240, 246, 252], [214, 228, 240], [180, 206, 230], [130, 160, 190]])).at(C.clone(), k);
        // o alvo sobe no espinho e cai deitado
        const lift = seq([[1.12, 0], [1.3, 1, 'out'], [1.55, 1], [1.95, 0, 'in']], T);
        const lay = seq([[1.2, 0], [1.6, 1], [2.9, 1], [3.5, 0, 'io']], T);
        R.place(0.25 * lay, RZ - 0.6 * lay, 1.1 * lift);
        R.lean(-1.2 * lay); R.spin(0.5 * lay);
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(12), variant(58), variant(91)];
  fixed[0].old = false; fixed[0].icy = false; fixed[0].fur = FURS[0];
  fixed[1].old = true; fixed[1].scar = true; fixed[1].brokenTusk = true;
  fixed[2].icy = true; fixed[2].old = false; fixed[2].fur = FURS[2];
  const def = v => ({ def: rigDef(painters(v), v), low: rigLow(painters(v), v) });
  window.MOBS.push({
    id: 'yeti', nome: 'Yeti', bioma: 'Mar do Sul e Norte', tier: 'Médio · tier 7', refX: 3.2,
    variants: fixed.map((v, i) => ({ nome: ['Branco', 'Velho', 'Gelado'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['rugido', 'Rugido Congelante'], ['pisao', 'Pisão Avalanche']],
    cam: 8, lookY: 1.4, lookZ: { rugido: -2.6, pisao: -2.6 }, camFar: { rugido: 10, pisao: 10.5 }, camYaw: { rugido: -1.3, pisao: -1.05 },
    info: {
      'Tamanho': '2,45 blocos, curvado (em pé passaria de 3)',
      'Vida': '90 (tier 7)',
      'Jeito': 'Territorial e sozinho nas Montanhas do Norte. Só ataca quem chega perto da toca; se for ferido, persegue longe.',
      'Rugido Congelante': 'Enche o peito e ruge um cone de gelo. Quem fica no cone congela dentro de um cristal por 1,5 s (não anda nem usa skill) e leva dano quando o gelo quebra. A geada no chão deixa lento por 3 s.',
      'Pisão Avalanche': 'Agacha com os punhos pra cima, pula até você e soca o chão: um anel de espinhos de gelo brota do centro pra fora e joga pra cima quem estiver em cima.',
      'Solta': 'Pelo de yeti (casaco que tira o lento do frio), gelo compactado; raro: presa de yeti.',
      'Variações': 'Tom do pelo, idade (pelo acinzentado), cicatriz, presa quebrada, gelo preso no pelo e tamanho sorteados.',
    },
  });
})();
