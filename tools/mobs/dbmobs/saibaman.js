// Saibaman (Terras do Dragão e Deserto do Diabo). Fraco, tier 9. Brota do chão, anda curvado, ri com a boca toda.
// Golpes: Agarrão Explosivo (pula nas costas, brilha cada vez mais rápido e explode junto) e Ácido (cuspe que
// vira poça borbulhando).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  // ---------- variantes: o tom de verde, o desenho das dobras da cabeça e o tamanho mudam ----------
  const SKINS = [[92, 164, 66], [72, 140, 58], [110, 168, 72], [76, 150, 92], [118, 158, 56], [64, 128, 72]];
  function variant(seed) {
    const R = FX.rnd(seed);
    const skin = SKINS[Math.floor(R() * SKINS.length)].map(c => c * (0.92 + R() * 0.14));
    return {
      seed, skin, belly: mix(skin, [218, 230, 158], 0.62), pore: mix([110, 64, 128], skin, 0.25 + R() * 0.2),
      size: 0.92 + R() * 0.16, fold: 0.5 + R() * 0.5, scar: R() < 0.3,
    };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.8, hi = 1.12) => {
      const f = 0.62 - 0.4 * q.ny + 0.35 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    // pele: verde com rugas finas que correm na direção do membro
    const skin = (q, s = 1) => {
      let c = shade(v.skin, q, s, 0.78, 1.12);
      if (fr(q.ay * 0.55 + n3(q.ax * 0.7, q.ay * 0.2, q.az * 0.7, 3 + S) * 1.6) < 0.1) c = mul(c, 0.84);
      return c;
    };
    // dobras do crânio: sulcos fundos como de cérebro, cada Saibaman com um desenho
    const folds = q => {
      const w = n3(q.ax * 0.42 * v.fold + 2, q.ay * 0.42, q.az * 0.42 * v.fold, 10 + S) * 3.2 + n3(q.ax * 0.9, q.ay * 0.9, q.az * 0.9, 11 + S) * 0.8;
      return fr(w) < 0.14;
    };
    const pore = q => {
      const cx = Math.floor(q.ax * 1.1 + 40), cz = Math.floor(q.az * 1.1 + 40), cy = Math.floor(q.ay * 1.1 + 40);
      return n3(cx * 1.7, cy * 1.3, cz * 1.9, 12 + S) > 0.8;
    };
    const P = {};
    P.skin = q => skin(q);
    P.cranium = q => {
      let c = shade(v.skin, q, 2, 0.8, 1.14);
      const upper = q.f === 'top' || q.ny < 0.55 || q.f === 'south';
      if (upper && folds(q)) c = mul(v.skin, 0.68);
      if (upper && pore(q) && !folds(q)) c = v.pore;
      if (q.f !== 'north') return c;
      // rosto: 7 x 6 texels. Olhos vermelhos sem pupila, puxados pra baixo no meio (cara de raiva)
      const x = Math.floor(q.px), y = Math.floor(q.py);
      c = shade(v.skin, q, 2, 0.84, 1.08);
      if (y === 0 && folds(q)) c = mul(v.skin, 0.55);
      const RED = [222, 34, 30], DARKRED = [150, 16, 20], BROW = mul(v.skin, 0.5);
      if (y === 2) { if (x === 1 || x === 5) c = RED; if (x === 2 || x === 4) c = BROW; }
      if (y === 3) { if (x === 1 || x === 2 || x === 4 || x === 5) c = x === 2 || x === 4 ? DARKRED : RED; if (x === 3) c = mul(v.skin, 0.78); }
      if (y === 4 && (x === 1 || x === 5)) c = mul(v.skin, 0.8);                                   // maçã do rosto
      if (y === 5) c = x >= 1 && x <= 5 ? (x % 2 === 0 ? [236, 232, 210] : [40, 22, 18]) : c;    // boca de orelha a orelha
      if (v.scar && x === 5 && y <= 4 && y >= 1) c = [176, 120, 96];
      return c;
    };
    P.brow = q => { let c = shade(mul(v.skin, 0.86), q, 4, 0.7, 1.1); if (q.f === 'bottom') c = mul(v.skin, 0.45); return c; };
    P.mouthIn = q => (q.f === 'bottom' && q.nz < 0.45 ? [96, 20, 28] : shade(v.skin, q, 2));
    P.jaw = q => {
      if (q.f === 'top') return q.nz < 0.3 ? [120, 24, 34] : [96, 20, 28];
      if (q.f === 'north' && q.ny < 0.42) return Math.floor(q.px) % 2 ? [232, 228, 206] : [60, 28, 24];  // dentes de baixo
      return shade(mul(v.skin, 0.94), q, 5);
    };
    P.belly = q => {
      let c = shade(v.belly, q, 6, 0.9, 1.1);
      if (fr(q.ay * 0.62 + 0.3) < 0.13) c = mul(v.belly, 0.78);                                   // placas da barriga
      if (Math.abs(q.nx - 0.5) < 0.05) c = mul(c, 0.86);
      return c;
    };
    P.torso = q => {
      let c = skin(q, 7);
      if (q.f === 'south' && Math.abs(q.nx - 0.5) < 0.12 && fr(q.ay * 0.7) < 0.55) c = mul(v.skin, 0.62);   // espinha
      if ((q.f === 'west' || q.f === 'east') && fr(q.ay * 0.6 + q.az * 0.3) < 0.14) c = mul(v.skin, 0.7); // costelas
      return c;
    };
    P.claw = q => (q.ny > 0.55 ? [44, 40, 30] : mix(mul(v.skin, 0.6), [60, 52, 36], 0.5));
    P.foot = q => (q.f === 'bottom' ? mul(v.skin, 0.5) : skin(q, 8));
    // low poly: uma caixa por peça, a pintura decide pela altura
    P.lowBody = q => (q.f === 'north' && q.ay > 7.4 && q.ay < 13 && Math.abs(q.ax) < 2.3 ? P.belly({ ...q, ny: (q.ay - 7.4) / 5.6 }) : P.torso(q));
    P.lowHead = q => P.cranium(q);
    return P;
  }

  function rigDef(P) {
    const claws = (y, z) => [-1.1, -0.3, 0.5].map(x => ({ x, y, z, w: 0.6, h: 1.5, d: 0.6, p: P.claw }));
    const arm = s => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * 3.7, -5.8, 0], rot: [-0.12, 0, s * -0.12], boxes: [{ x: -1.1, y: -0.6, z: -1.1, w: 2.2, h: 4.6, d: 2.2, p: P.skin }],
      kids: [{
        name: s < 0 ? 'anteD' : 'anteE', pivot: [0, 4.0, 0], rot: [-0.25, 0, 0], boxes: [{ x: -1, y: 0, z: -1, w: 2, h: 4.2, d: 2, p: P.skin }],
        kids: [{ name: s < 0 ? 'maoD' : 'maoE', pivot: [0, 4.0, 0], boxes: [{ x: -1.2, y: 0, z: -1.2, w: 2.4, h: 1.4, d: 2.4, p: P.skin }, ...claws(1.2, -1.1)] }],
      }],
    });
    const leg = s => ({
      name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * 1.7, 15, 0], boxes: [{ x: -1.3, y: 0, z: -1.4, w: 2.6, h: 4.8, d: 2.8, p: P.skin }],
      kids: [{
        name: s < 0 ? 'canelaD' : 'canelaE', pivot: [0, 4.6, 0], boxes: [{ x: -1.1, y: 0, z: -1.1, w: 2.2, h: 3.8, d: 2.2, p: P.skin }],
        kids: [{
          name: s < 0 ? 'peD' : 'peE', pivot: [0, 3.6, 0], boxes: [
            { x: -1.4, y: 0, z: -2.9, w: 2.8, h: 0.8, d: 3.8, p: P.foot },
            ...[-1.3, -0.35, 0.6].map(x => ({ x, y: 0.1, z: -3.6, w: 0.7, h: 0.6, d: 0.8, p: P.claw })),
          ],
        }],
      }],
    });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'quadril', pivot: [0, 15, 0], boxes: [{ x: -2.8, y: -1.8, z: -1.7, w: 5.6, h: 2.6, d: 3.4, p: P.torso }],
          kids: [{
            name: 'tronco', pivot: [0, -1.6, 0], boxes: [
              { x: -3, y: -6.4, z: -1.9, w: 6, h: 6.6, d: 3.8, p: P.torso },
              { x: -2.2, y: -5.6, z: -2.3, w: 4.4, h: 5.2, d: 0.6, p: P.belly },
              { x: -3.7, y: -6.6, z: -1.6, w: 1.5, h: 1.7, d: 3.2, p: P.skin },
              { x: 2.2, y: -6.6, z: -1.6, w: 1.5, h: 1.7, d: 3.2, p: P.skin },
              { x: -1.3, y: -7.5, z: -1.2, w: 2.6, h: 1.5, d: 2.4, p: P.skin },
            ], kids: [
              {
                name: 'cabeca', pivot: [0, -7.1, -0.2], boxes: [
                  { x: -3.6, y: -5.8, z: -3.4, w: 7.2, h: 5.8, d: 6.8, p: P.cranium },
                  { x: -3.0, y: -6.6, z: -2.9, w: 6, h: 0.9, d: 6.2, p: P.cranium },
                  { x: -2.2, y: -7.2, z: -2.1, w: 4.4, h: 0.7, d: 4.6, p: P.cranium },
                  { x: -3.2, y: -5.3, z: 3.3, w: 6.4, h: 4, d: 1.2, p: P.cranium },
                  { x: -3.5, y: -4.0, z: -3.85, w: 7, h: 0.8, d: 0.6, p: P.brow },
                  { x: -2.4, y: -0.2, z: -3.2, w: 4.8, h: 0.3, d: 3, p: P.mouthIn },
                ],
                kids: [{ name: 'mandibula', pivot: [0, 0, -0.4], boxes: [{ x: -2.6, y: 0, z: -2.9, w: 5.2, h: 1.3, d: 3.2, p: P.jaw }] }],
              },
              arm(-1), arm(1),
            ],
          }],
        },
        leg(-1), leg(1),
      ],
    };
  }
  function rigLow(P) {
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'quadril', pivot: [0, 15, 0], boxes: [{ x: -3, y: -8.2, z: -1.9, w: 6, h: 9, d: 3.8, p: P.lowBody }], kids: [
            {
              name: 'cabeca', pivot: [0, -8.7, -0.2], boxes: [
                { x: -3.6, y: -5.8, z: -3.4, w: 7.2, h: 5.8, d: 6.8, p: P.lowHead },
                { x: -3.0, y: -7.0, z: -2.8, w: 6, h: 1.3, d: 6, p: P.lowHead },
              ],
              kids: [{ name: 'mandibula', pivot: [0, 0, -0.4], boxes: [{ x: -2.6, y: 0, z: -2.9, w: 5.2, h: 1.3, d: 3.2, p: P.jaw }] }],
            },
            { name: 'bracoD', pivot: [-3.9, -7.4, 0], rot: [-0.3, 0, 0.12], boxes: [{ x: -1.1, y: -0.6, z: -1.1, w: 2.2, h: 9.8, d: 2.2, p: P.skin }] },
            { name: 'bracoE', pivot: [3.9, -7.4, 0], rot: [-0.3, 0, -0.12], boxes: [{ x: -1.1, y: -0.6, z: -1.1, w: 2.2, h: 9.8, d: 2.2, p: P.skin }] },
          ],
        },
        { name: 'pernaD', pivot: [-1.7, 15, 0], boxes: [{ x: -1.3, y: 0, z: -1.4, w: 2.6, h: 9, d: 2.8, p: P.foot }] },
        { name: 'pernaE', pivot: [1.7, 15, 0], boxes: [{ x: -1.3, y: 0, z: -1.4, w: 2.6, h: 9, d: 2.8, p: P.foot }] },
      ],
    };
  }

  // ---------- poses ----------
  function hunch(r, P, k = 1) {
    r('quadril', 0.22 * k, 0, 0); P.quadril.obj.position.y += 1.3 * k;
    r('tronco', 0.32 * k, 0, 0);
    r('cabeca', -0.5 * k, 0, 0);
    r('pernaD', -0.42 * k, 0.1, 0.06); r('canelaD', 0.7 * k, 0, 0); r('peD', -0.28 * k, 0, 0);
    r('pernaE', -0.3 * k, -0.12, -0.06); r('canelaE', 0.6 * k, 0, 0); r('peE', -0.3 * k, 0, 0);
    r('bracoD', -0.15 * k, 0, 0.18 * k); r('anteD', -0.35 * k, 0, 0); r('maoD', -0.3 * k, 0, 0);
    r('bracoE', -0.25 * k, 0, -0.16 * k); r('anteE', -0.45 * k, 0, 0); r('maoE', -0.3 * k, 0, 0);
  }
  const WALK_T = [[0, -0.55], [0.18, -0.3], [0.4, 0.2], [0.55, 0.45], [0.7, 0.15], [0.85, -0.4]];
  const WALK_K = [[0, 0.1], [0.2, 0.15], [0.45, 0.2], [0.6, 0.6], [0.75, 1.05], [0.9, 0.35]];

  function glowModel(e, k, col = [0.35, 1.0, 0.25]) {
    // a pele inteira acende (no jogo: o tom por cima do modelo, como o creeper)
    e.obj.traverse(o => { if (o.material && o.material.emissive) o.material.emissive.setRGB(col[0] * k, col[1] * k, col[2] * k); });
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size; e.obj.children[0].scale.set(s, s, s); obj.position.y -= 1.5 * (1 - s) * 0;
    glowModel(e, 0);
    const breath = loop([[0, 0], [0.4, 1], [0.999, 0]], t / 2.1);
    // tique: a cabeça vira de repente, segura e volta (um animal, não um soldado)
    const tic = seq([[0, 0], [0.04, 1, 'snap'], [0.32, 1], [0.4, 0]], fr(t / 2.7));

    if (mode === 'parado') {
      hunch(r, P);
      r('tronco', -0.04 * breath, 0, 0); r('cabeca', 0.05 * breath, 0.45 * tic, 0.18 * tic);
      r('mandibula', 0.12 + 0.1 * loop([[0, 0], [0.1, 1], [0.2, 0], [0.3, 0.8], [0.4, 0]], t * 0.9) * (fr(t / 3.1) < 0.35 ? 1 : 0), 0, 0);
      r('bracoD', 0.06 * breath, 0, 0); r('bracoE', 0.06 * breath, 0, 0);
      r('maoD', 0.2 * loop([[0, 0], [0.5, 1]], t * 1.3), 0, 0);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 1.35;
      hunch(r, P, 1.15);
      for (const [sd, off] of [['D', 0], ['E', 0.5]]) {
        r('perna' + sd, loop(WALK_T, ph + off), 0, 0);
        r('canela' + sd, loop(WALK_K, ph + off), 0, 0);
        r('pe' + sd, -0.3 * loop(WALK_K, ph + off - 0.08), 0, 0);
      }
      P.quadril.obj.position.y += loop([[0, 0.1], [0.15, 0.6], [0.35, -0.2], [0.5, 0.1], [0.65, 0.6], [0.85, -0.2]], ph);
      const tw = loop([[0, 0.08], [0.5, -0.08]], ph - 0.05);
      r('quadril', 0, tw, 0); r('tronco', 0, -tw * 0.8, tw * 0.4);
      r('bracoD', -0.15 + 0.35 * loop([[0, 1], [0.5, -1]], ph), 0, 0); r('bracoE', -0.15 - 0.35 * loop([[0, 1], [0.5, -1]], ph), 0, 0);
      r('cabeca', 0.1, -tw * 0.6 + 0.3 * tic, 0);
      return;
    }

    if (mode === 'agarrao') {
      const T = t % 4.2;
      const R = ctx.ref; const RZ = -2.8; R.place(0, RZ, 0);
      const BACKZ = RZ - 0.32;
      const explode = 2.75;
      // ----- agacha, pula por cima, gira no ar e gruda nas costas -----
      const crouch = seq([[0, 0], [0.42, 1, 'out'], [0.5, 1], [0.56, 0, 'in']], T);
      const fly = FX.clamp01((T - 0.52) / 0.4);
      if (T < explode) {
        hunch(r, P);
        if (T < 0.56) {
          P.quadril.obj.position.y += 2.6 * crouch;
          r('pernaD', -0.7 * crouch, 0, 0); r('canelaD', 1.3 * crouch, 0, 0); r('peD', -0.5 * crouch, 0, 0);
          r('pernaE', -0.7 * crouch, 0, 0); r('canelaE', 1.3 * crouch, 0, 0); r('peE', -0.5 * crouch, 0, 0);
          r('tronco', 0.35 * crouch, 0, 0); r('cabeca', -0.35 * crouch, 0, 0);
          r('bracoD', 0.9 * crouch, 0, 0.3 * crouch); r('bracoE', 0.9 * crouch, 0, -0.3 * crouch);
          r('mandibula', 0.5 * crouch, 0, 0);
        } else if (T < 0.92) {
          // no ar: encolhido, braços pra frente, girando de frente pro alvo pras costas dele
          const f = FX.EASE.io(fly);
          obj.position.z += BACKZ * f;
          obj.position.y += 1.5 * Math.sin(Math.PI * fly) + 0.42 * f;
          obj.rotation.y = Math.PI * FX.EASE.io(FX.clamp01((fly - 0.25) / 0.6));
          obj.rotation.x = -0.6 * Math.sin(Math.PI * fly);
          r('pernaD', -1.3, 0, 0.2); r('canelaD', 1.8, 0, 0); r('pernaE', -1.2, 0, -0.2); r('canelaE', 1.8, 0, 0);
          r('bracoD', -1.9 * f, 0, 0.5); r('bracoE', -1.9 * f, 0, -0.5); r('anteD', -0.5, 0, 0); r('anteE', -0.5, 0, 0);
          r('mandibula', 0.6, 0, 0);
        } else {
          // grudado: braços por cima dos ombros, pernas na cintura, treme e acende cada vez mais rápido
          const c = T - 0.92, dur = explode - 0.92;
          const shk = 0.02 + 0.06 * (c / dur);
          obj.position.z += BACKZ + (n3(T * 30, 0, 0, 3) - 0.5) * shk;
          obj.position.y += 0.42 + (n3(0, T * 30, 0, 4) - 0.5) * shk;
          obj.position.x += (n3(0, 0, T * 30, 5) - 0.5) * shk;
          obj.rotation.y = Math.PI;
          const land = seq([[0, 1], [0.12, 0, 'out']], c);
          r('quadril', -0.3, 0, 0); r('tronco', -0.25 + 0.25 * land, 0, 0); r('cabeca', 0.1, 0.35, 0.25);
          r('bracoD', -2.5, 0.2, 0.35); r('anteD', -1.25, 0, 0); r('maoD', -0.6, 0, 0);
          r('bracoE', -2.5, -0.2, -0.35); r('anteE', -1.25, 0, 0); r('maoE', -0.6, 0, 0);
          r('pernaD', -1.1, 0, 0.55); r('canelaD', 1.5, 0, 0); r('pernaE', -1.1, 0, -0.55); r('canelaE', 1.5, 0, 0);
          // risada: a boca abre e fecha cada vez mais
          r('mandibula', 0.3 + 0.35 * Math.abs(Math.sin(c * 11)), 0, 0);
          const ph = 2 * Math.PI * (1.6 * c + 1.9 * c * c);
          const pulse = Math.pow(0.5 + 0.5 * Math.sin(ph), 2) * (0.35 + 0.65 * c / dur);
          glowModel(e, 0.85 * pulse);
          const cen = new THREE.Vector3(0, 1.1, BACKZ - 0.05);
          // vapor verde saindo da pele, cada vez mais
          ctx.get('vapor', () => DBFX.grains({
            n: 90, seed: 52, palette: [[140, 255, 110], [90, 220, 70], [200, 255, 170]], size: [0.025, 0.06], drag: 1.2, grav: -1.5, life: 0.7, spread: 1.8, add: true,
            cone: Rr => { const a = Rr() * Math.PI * 2, o = 0.15 + Rr() * 0.3; return [Math.cos(a) * o, 0.4 + Rr() * 0.8, Math.sin(a) * o]; },
          })).at(cen.clone().add(new THREE.Vector3(0, -0.2, 0)), c, -5);
          ctx.get('luzVerde', () => DBFX.light(0x6cff4a)).at(cen, 2.5 * pulse + 0.3, 5);
          // o alvo se debate
          R.spin(0.25 * Math.sin(T * 9) * (1 - 0.4 * c / dur));
          R.armsOut(0.55 + 0.35 * Math.sin(T * 7));
          R.lean(0.15 * Math.sin(T * 5));
        }
      }
      if (T >= explode) {
        const age = (T - explode) / 1.3;
        const at = new THREE.Vector3(0, 1.15, BACKZ);
        ctx.get('boom', FX.volume).set(at, 1.8, age, { fire: 1, seed: 6.1, dens: 14, rise: 0.5, smoke: [0.2, 0.23, 0.16] });
        ctx.get('boomVerde', FX.volume).set(at.clone().add(new THREE.Vector3(0.3, 0.2, -0.3)), 1.2, Math.max(0.001, age - 0.12), { seed: 2.4, dens: 9, rise: 0.8, smoke: [0.36, 0.6, 0.24] });
        ctx.flash(at, Math.max(0, 1 - (T - explode) / 0.3));
        ctx.shake(Math.max(0, 1 - (T - explode) / 0.45));
        ctx.roarRing(new THREE.Vector3(0, 0.03, BACKZ), (T - explode) / 0.5);
        ctx.get('pedacos', () => FX.debris(26, 17, [[86, 160, 60], [62, 120, 44], [150, 110, 80], [110, 82, 58]])).at(at.clone().add(new THREE.Vector3(0, -0.4, 0)), T - explode);
        // o alvo é jogado pra frente e cai de cara
        const kb = seq([[explode, 0], [explode + 0.45, 1, 'out']], T);
        const up = Math.sin(Math.PI * FX.clamp01((T - explode) / 0.45));
        R.place(0, RZ + 2.2 * kb, 0.7 * up);
        R.lean(1.45 * seq([[explode, 0], [explode + 0.5, 1, 'in'], [4.0, 1], [4.2, 0]], T));
        obj.visible = false;
      }
      return;
    }

    if (mode === 'acido') {
      const T = t % 3.4;
      const R = ctx.ref; const RZ = -4.6; R.place(0, RZ, 0);
      hunch(r, P);
      // enche o peito, joga a cabeça pra trás, e cospe num estalo
      const inhale = seq([[0, 0], [0.62, 1, 'out'], [0.7, 1]], T) * (1 - seq([[0.7, 0], [0.76, 1, 'snap']], T));
      const spit = seq([[0.7, 0], [0.76, 1, 'snap'], [1.1, 1], [1.5, 0]], T);
      r('tronco', -0.45 * inhale + 0.4 * spit, 0, 0);
      r('cabeca', -0.55 * inhale + 0.55 * spit, 0, 0);
      r('quadril', -0.1 * inhale + 0.1 * spit, 0, 0);
      if (P.tronco) P.tronco.obj.scale.set(1 + 0.1 * inhale, 1, 1 + 0.12 * inhale);
      r('mandibula', 0.15 * inhale + 0.85 * spit, 0, 0);
      r('bracoD', 0.4 * inhale - 0.3 * spit, 0, 0.4 * inhale); r('bracoE', 0.4 * inhale - 0.3 * spit, 0, -0.4 * inhale);
      r('pernaD', -0.25 * inhale, 0, 0); r('canelaD', 0.3 * inhale, 0, 0);
      // depois: limpa a boca com as costas da mão e ri
      const wipe = seq([[1.5, 0], [1.75, 1, 'out'], [2.05, 1], [2.3, 0]], T);
      r('bracoD', -1.6 * wipe, -0.6 * wipe, -0.3 * wipe); r('anteD', -1.4 * wipe, 0, 0);
      r('cabeca', 0.15 * wipe, 0.3 * wipe, 0);
      const cackle = T > 2.3 && T < 3.0 ? Math.abs(Math.sin((T - 2.3) * 14)) : 0;
      r('mandibula', 0.4 * cackle, 0, 0); r('tronco', 0.06 * cackle, 0, 0);
      // gosma no fundo da boca antes do cuspe
      const mouth = new THREE.Vector3(0, 1.22, -0.48);
      if (T > 0.45 && T < 0.76) ctx.get('boca', () => DBFX.glow([0.4, 1.0, 0.2])).at(mouth.clone().add(new THREE.Vector3(0, 0.15, 0.2)), 0.16, FX.clamp01((T - 0.45) / 0.2), T);
      // a bola de ácido voa em arco até os pés do alvo
      const SPOT = new THREE.Vector3(0.1, 0.02, RZ + 0.15);
      const path = f => new THREE.Vector3(mouth.x + (SPOT.x - mouth.x) * f, mouth.y + (SPOT.y - mouth.y) * f + 1.0 * Math.sin(Math.PI * f), mouth.z + (SPOT.z - mouth.z) * f);
      const fl = (T - 0.76) / 0.42;
      if (fl >= 0 && fl <= 1) ctx.get('gosma', () => DBFX.blob(0x86e83c)).at(path, fl, 0.15, T);
      const hit = 0.76 + 0.42;
      if (T >= hit) {
        const age = T - hit;
        const grow = seq([[0, 0.15], [0.22, 1, 'out']], age);
        const fade = 1 - seq([[1.6, 0], [2.15, 1]], age);
        ctx.get('poca', () => DBFX.puddle()).at(SPOT, 1.35, grow, fade, T, 3.7);
        ctx.get('respingo', () => DBFX.grains({
          n: 60, seed: 41, palette: [[150, 240, 80], [100, 200, 50], [200, 255, 140]], size: [0.03, 0.07], drag: 1.5, grav: 9.8, life: 0.8,
          cone: R2 => { const a = R2() * Math.PI * 2, o = 0.6 + R2() * 2.2; return [Math.cos(a) * o, 1.5 + R2() * 3, Math.sin(a) * o]; },
        })).at(SPOT.clone().add(new THREE.Vector3(0, 0.05, 0)), age, 0.02);
        // chiado: baforadas verdes subindo de pontos diferentes da poça, cada uma no seu tempo
        for (let i = 0; i < 4; i++) {
          const st = 0.15 + i * 0.37, ag = (age - st) / 0.9;
          if (ag > 0 && ag < 1 && age < 2.2) {
            const ang = i * 2.1 + 0.6, rr = 0.25 + 0.5 * fr(i * 0.37 + 0.2);
            ctx.get('chiado' + i, FX.volume).set(SPOT.clone().add(new THREE.Vector3(Math.cos(ang) * rr, 0.25, Math.sin(ang) * rr)), 0.35, ag,
              { seed: 9 + i * 3.3, dens: 6, rise: 1.2, squash: 1.6, smoke: [0.55, 0.85, 0.4] });
          }
        }
        ctx.get('luzPoca', () => DBFX.light(0x7cff40)).at(SPOT.clone().add(new THREE.Vector3(0, 0.4, 0)), 1.6 * fade, 3);
        // o alvo pisa na poça: veneno na tela, recua pulando
        if (age < 1.8) ctx.poison(Math.min(1, age * 3) * (1 - FX.clamp01((age - 1.2) / 0.6)));
        const back = seq([[0.35, 0], [0.75, 1, 'out']], age);
        R.place(0, RZ - 1.2 * back, 0.35 * Math.sin(Math.PI * FX.clamp01((age - 0.35) / 0.4)));
        R.lean(-0.3 * Math.sin(Math.PI * FX.clamp01(age / 0.8)));
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(4), variant(17), variant(29)];
  const def = v => ({ def: rigDef(painters(v)), low: rigLow(painters(v)) });
  window.MOBS.push({
    id: 'saibaman', nome: 'Saibaman', bioma: 'Terras e Deserto', tier: 'Fraco · tier 9',
    variants: fixed.map((v, i) => ({ nome: ['Verde', 'Escuro', 'Musgo'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['agarrao', 'Agarrão Explosivo'], ['acido', 'Ácido']],
    cam: 5, lookY: 0.9, lookZ: { agarrao: -1.6, acido: -2.4 }, camFar: { agarrao: 6.5, acido: 7.5 }, camYaw: { agarrao: -1.15, acido: -1.25 },
    info: {
      'Tamanho': '1,5 bloco, um pouco menor que o jogador (cada um nasce de um tamanho)',
      'Vida': '28 (tier 9)',
      'Jeito': 'Hostil, em grupos de 2 a 4. Brota do chão perto de quem passa, anda curvado e ri o tempo todo.',
      'Agarrão Explosivo': 'Agacha e pula por cima de você, gira no ar e gruda nas suas costas. Pisca verde cada vez mais rápido por quase 2 s e explode junto (ele morre). Bater forte nele enquanto está grudado faz explodir antes, com menos dano.',
      'Ácido': 'Enche o peito e cospe uma bola de ácido nos seus pés. Vira uma poça borbulhando por 2 s que envenena e arde a cada meio segundo quem fica em cima.',
      'Solta': 'Semente de Saibaman (planta e brota um Saibaman aliado por 60 s); raro: frasco de ácido.',
      'Variações': 'Tom de verde, desenho das dobras da cabeça, poros, cicatriz e tamanho sorteados.',
    },
  });
})();
