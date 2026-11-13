// Bandido do Deserto (Deserto do Diabo). Médio, tier 8. Turbante com a ponta caindo nas costas, colete de couro
// aberto, faixa larga na cintura, calça bufante. Luta no estilo do lobo do deserto (o Yamcha das antigas).
// Golpes: Punho do Lobo (avança e bate 6 vezes, de um lado e do outro, e o último arremessa) e Areia nos Olhos.
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  const WRAPS = [[214, 198, 160], [168, 72, 50], [52, 62, 110], [46, 42, 44], [120, 116, 72], [196, 150, 84]];
  const VESTS = [[96, 62, 38], [70, 46, 30], [120, 84, 50], [58, 52, 46]];
  const SASHES = [[184, 50, 40], [210, 120, 40], [64, 110, 120], [140, 40, 90], [70, 120, 60]];
  const PANTS = [[220, 206, 172], [196, 176, 140], [120, 100, 132], [168, 150, 120]];
  const SKINS = [[198, 142, 100], [176, 120, 82], [150, 98, 66], [214, 162, 120]];
  const HAIRS = [[28, 24, 22], [52, 36, 26], [80, 70, 64]];
  function variant(seed) {
    const R = FX.rnd(seed);
    const pick = a => a[Math.floor(R() * a.length)];
    const wrap = pick(WRAPS);
    return {
      seed, wrap, vest: pick(VESTS), sash: pick(SASHES.filter(c => c !== wrap)), pants: pick(PANTS), skin: pick(SKINS), hair: pick(HAIRS),
      mask: R() < 0.55, beard: R() < 0.5, scar: R() < 0.45, earring: R() < 0.4, shirt: R() < 0.35,
    };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.8, hi = 1.12) => {
      const f = 0.62 - 0.4 * q.ny + 0.18 * (n3(q.ax * 0.2, q.ay * 0.2, q.az * 0.2, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    // pano enrolado: faixas inclinadas, cada volta de uma largura (vem do ruído)
    const wrapCloth = (q, base, s, k = 0.85) => {
      let c = shade(base, q, s, 0.78, 1.14);
      const w = fr(q.ay * 0.3 + q.ax * 0.12 - q.az * 0.06 + n3(q.ax * 0.2, q.ay * 0.15, q.az * 0.2, s + 20 + S) * 0.3);
      if (w < 0.2) c = mul(c, k);
      else if (w < 0.3) c = mul(c, 1.07);
      return c;
    };
    const P = {};
    P.skin = q => shade(v.skin, q, 1, 0.86, 1.06);
    P.head = q => {
      let c = shade(v.skin, q, 1, 0.88, 1.05);
      const x = Math.floor(q.px), y = Math.floor(q.py);
      const cloth = () => wrapCloth(q, mul(v.wrap, 0.92), 3, 0.8);
      if (q.f === 'top') return shade(v.hair, q, 2);
      if (q.f === 'south') return y < 4 ? shade(v.hair, q, 2) : (v.mask ? cloth() : c);
      if (q.f === 'west' || q.f === 'east') {
        if (q.pz > 4.8 && q.py < 5) return shade(v.hair, q, 2);
        if (q.pz > 2.6 && q.pz < 4.2 && q.py > 2.8 && q.py < 4.6) { c = mul(c, 0.84); if (v.earring && q.py > 4.2 && q.f === 'east') c = [230, 196, 70]; }
        if (v.mask && q.py >= 4) return cloth();
        if (v.beard && q.py >= 4.6) return cel(v.hair, n3(q.ax, q.ay, q.az, 5), 0.8, 1.1);
        return c;
      }
      if (q.f !== 'north') return c;
      if (y === 2 && (x === 1 || x === 2 || x === 4 || x === 5)) c = mul(v.hair, 0.9);         // sobrancelha grossa
      if (y === 2 && (x === 2 || x === 4)) c = [24, 18, 16];
      if (y === 3) {
        if (x === 1 || x === 5) c = [232, 226, 214];
        if (x === 2 || x === 4) c = [36, 26, 22];                                              // olhos apertados de sol
        if (x === 3) c = mul(c, 0.9);
      }
      if (v.mask && y >= 4) return cloth();
      if (y === 4 && x === 3) c = mul(c, 0.84);
      if (v.beard && y >= 5) c = cel(v.hair, n3(q.ax * 2, q.ay * 2, q.az, 5 + S), 0.8, 1.15);
      if (y === 5 && x >= 2 && x <= 4) c = v.beard ? [60, 30, 26] : [120, 64, 52];
      if (!v.beard && y === 6 && (x === 1 || x === 5 || x === 3)) c = mul(c, 0.86);          // barba por fazer
      if (v.scar && ((x === 1 && y >= 2 && y <= 4) || (x === 2 && y === 5))) c = [156, 84, 72];
      return c;
    };
    P.turban = q => {
      let c = wrapCloth(q, v.wrap, 4);
      if (q.f === 'bottom') c = mul(v.wrap, 0.6);
      return c;
    };
    P.knot = q => wrapCloth(q, mul(v.wrap, 0.95), 6, 0.78);
    P.tail = q => {
      const k = Math.floor(q.px * 1.4);
      if (q.ny > 0.8 + 0.2 * n3(k * 2.3, S, 2, 7)) return null;                                 // ponta desfiada
      return wrapCloth(q, mul(v.wrap, 0.94), 7, 0.82);
    };
    P.chest = q => {
      // peito à mostra no meio do colete aberto (ou a camisa clara, em alguns)
      const open = q.f === 'north' && Math.abs(q.nx - 0.5) < 0.2 + 0.06 * (1 - q.ny);
      if (open) {
        if (v.shirt) return wrapCloth(q, [214, 204, 178], 8, 0.88);
        let c = shade(v.skin, q, 1, 0.86, 1.06);
        if (q.ny > 0.35 && Math.abs(q.nx - 0.5) < 0.03) c = mul(c, 0.86);                     // linha do peito
        if (v.scar && Math.abs((q.ny - 0.3) - (q.nx - 0.4) * 1.4) < 0.05 && q.ny < 0.7) c = [168, 92, 80];
        return c;
      }
      let c = shade(v.vest, q, 9, 0.76, 1.16);
      if (q.f === 'north' && Math.abs(Math.abs(q.nx - 0.5) - (0.22 + 0.06 * (1 - q.ny))) < 0.04) c = mul(v.vest, 1.3);   // borda costurada
      if (n3(q.ax * 0.9, q.ay * 0.9, q.az * 0.9, 10 + S) > 0.78) c = mul(c, 0.82);            // couro gasto
      if (q.f === 'south' && q.ny > 0.2 && q.ny < 0.7 && Math.abs(q.nx - 0.5) < 0.25 && fr(q.ax * 0.5 + q.ay * 0.5) < 0.15) c = mul(c, 1.2);
      return c;
    };
    P.sash = q => {
      let c = wrapCloth(q, v.sash, 11, 0.78);
      if (q.f === 'north' && q.nx > 0.62 && q.nx < 0.8) c = mul(v.sash, 0.8);                    // nó
      return c;
    };
    P.sashEnd = q => { if (q.ny > 0.86 + 0.14 * n3(Math.floor(q.px * 2), S, 4, 12)) return null; return wrapCloth(q, v.sash, 12, 0.8); };
    P.pants = q => {
      let c = shade(v.pants, q, 13, 0.86, 1.1);
      // dobras da calça bufante: verticais, tortas, cada uma num lugar
      const fold = fr(q.ax * 0.45 + q.az * 0.45 + n3(q.ax * 0.3, q.ay * 0.2, q.az * 0.3, 14 + S) * 1.6);
      if (fold < 0.14) c = mul(c, 0.82);
            return c;
    };
    P.upperArm = q => {
      let c = shade(v.skin, q, 1, 0.86, 1.05);
      if (q.ny < 0.3) c = shade(v.vest, q, 9, 0.76, 1.14);                                       // cava do colete
      return c;
    };
    P.wrapArm = q => wrapCloth(q, mix(v.wrap, [200, 190, 170], 0.4), 16, 0.8);
    P.fist = q => {
      let c = shade(v.skin, q, 1, 0.86, 1.04);
      if (q.f === 'north' && fr(q.px * 1.4) < 0.18) c = mul(c, 0.8);
      if (q.ny < 0.35) c = P.wrapArm(q);
      return c;
    };
    P.shin = q => wrapCloth(q, mul(v.pants, 0.86), 17, 0.8);
    P.boot = q => {
      if (q.f === 'bottom') return [40, 30, 22];
      let c = shade([104, 70, 44], q, 18, 0.78, 1.14);
      if (q.ny < 0.25) c = mul(c, 1.15);
      if (q.f === 'north' && q.nz < 0.25 && q.ny > 0.2) c = mul(c, 0.86);
      return c;
    };
    P.dagger = q => (q.ny < 0.35 ? [60, 40, 26] : q.ny < 0.42 ? [190, 160, 70] : shade([140, 96, 56], q, 19));
    P.lowTorso = q => q.ay > 11.6 ? P.pants(q) : q.ay > 9.6 ? P.sash(q) : P.chest({ ...q, ny: (q.ay - 2) / 7.6 });
    P.lowArm = q => q.ay < 6 ? P.upperArm({ ...q, ny: (q.ay - 1) / 5 }) : q.ay < 10 ? P.wrapArm(q) : P.fist({ ...q, ny: 0.6 });
    P.lowLeg = q => q.ay < 19 ? P.pants(q) : q.ay < 23 ? P.shin(q) : P.boot({ ...q, ny: 0.7 });
    return P;
  }

  function rigDef(P, v) {
    const arm = s => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * 5.5, -8, 0], boxes: [{ x: -1.5, y: -1, z: -1.5, w: 3, h: 5, d: 3, p: P.upperArm }],
      kids: [{
        name: s < 0 ? 'anteD' : 'anteE', pivot: [0, 4, 0], boxes: [{ x: -1.5, y: 0, z: -1.5, w: 3, h: 4, d: 3, p: P.wrapArm }],
        kids: [{ name: s < 0 ? 'maoD' : 'maoE', pivot: [0, 4, 0], boxes: [{ x: -1.3, y: 0, z: -1.3, w: 2.6, h: 2.3, d: 2.6, p: P.fist }] }],
      }],
    });
    const leg = s => ({
      name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * 2, 13, 0], boxes: [{ x: -2.4, y: 0, z: -2.4, w: 4.8, h: 6.4, d: 4.8, p: P.pants }],
      kids: [{
        name: s < 0 ? 'canelaD' : 'canelaE', pivot: [0, 6, 0], boxes: [{ x: -1.9, y: 0, z: -1.9, w: 3.8, h: 4.2, d: 3.8, p: P.shin }],
        kids: [{
          name: s < 0 ? 'peD' : 'peE', pivot: [0, 4.0, 0], boxes: [
            { x: -2, y: -1.4, z: -2.2, w: 4, h: 2.4, d: 4.2, p: P.boot },
            { x: -1.9, y: 0.1, z: -3.4, w: 3.8, h: 0.9, d: 1.4, p: P.boot },
          ],
        }],
      }],
    });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'quadril', pivot: [0, 13, 0], boxes: [
            { x: -4.2, y: -2, z: -2.2, w: 8.4, h: 2.4, d: 4.4, p: P.pants },
            { x: -4.4, y: -3.6, z: -2.4, w: 8.8, h: 2.2, d: 4.8, p: P.sash },
            { x: 3.6, y: -2.6, z: -1.2, w: 1, h: 4.2, d: 1.4, p: P.dagger },
          ], kids: [
            { name: 'faixa', pivot: [1.8, -2, -2.5], rot: [-0.1, 0, -0.1], boxes: [{ x: -0.8, y: 0, z: -0.3, w: 1.8, h: 5, d: 0.4, p: P.sashEnd }] },
            {
              name: 'tronco', pivot: [0, -2, 0], boxes: [
                { x: -4, y: -9, z: -2, w: 8, h: 9, d: 4, p: P.chest },
                { x: -4.3, y: -9.2, z: -2.25, w: 2.6, h: 9.4, d: 4.5, p: P.chest },
                { x: 1.7, y: -9.2, z: -2.25, w: 2.6, h: 9.4, d: 4.5, p: P.chest },
              ], kids: [
                {
                  name: 'cabeca', pivot: [0, -9.3, 0], boxes: [
                    { x: -3.5, y: -7, z: -3.5, w: 7, h: 7, d: 7, p: P.head },
                    { x: -3.95, y: -8.6, z: -3.95, w: 7.9, h: 3.8, d: 7.9, p: P.turban },
                    { x: -3.2, y: -9.6, z: -3.0, w: 6.4, h: 1.2, d: 6.4, p: P.turban },
                    { x: 1.6, y: -9.2, z: -4.3, w: 2.2, h: 2.4, d: 1.2, p: P.knot },
                  ],
                  kids: [{ name: 'nos', pivot: [0.6, -5.6, 3.9], rot: [0.3, 0, 0.1], boxes: [{ x: -1.4, y: 0, z: 0, w: 2.6, h: 7.2, d: 0.4, p: P.tail }] }],
                },
                arm(-1), arm(1),
              ],
            },
          ],
        },
        leg(-1), leg(1),
      ],
    };
  }
  function rigLow(P) {
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'quadril', pivot: [0, 13, 0], boxes: [{ x: -4.2, y: -11.3, z: -2.2, w: 8.4, h: 11.7, d: 4.4, p: P.lowTorso }], kids: [
            {
              name: 'cabeca', pivot: [0, -11.3, 0], boxes: [
                { x: -3.5, y: -7, z: -3.5, w: 7, h: 7, d: 7, p: P.head },
                { x: -3.95, y: -8.6, z: -3.95, w: 7.9, h: 3.8, d: 7.9, p: P.turban },
              ],
            },
            { name: 'bracoD', pivot: [-5.6, -10, 0], boxes: [{ x: -1.5, y: -1, z: -1.5, w: 3, h: 11.3, d: 3, p: P.lowArm }] },
            { name: 'bracoE', pivot: [5.6, -10, 0], boxes: [{ x: -1.5, y: -1, z: -1.5, w: 3, h: 11.3, d: 3, p: P.lowArm }] },
          ],
        },
        { name: 'pernaD', pivot: [-2, 13, 0], boxes: [{ x: -2.4, y: 0, z: -2.4, w: 4.8, h: 11, d: 4.8, p: P.lowLeg }] },
        { name: 'pernaE', pivot: [2, 13, 0], boxes: [{ x: -2.4, y: 0, z: -2.4, w: 4.8, h: 11, d: 4.8, p: P.lowLeg }] },
      ],
    };
  }

  // ---------- poses ----------
  // base: largo e solto, peso atrás, um braço à frente (bandido que já brigou muito)
  function stance(r, P) {
    r('quadril', 0.08, -0.25, 0); P.quadril.obj.position.y += 0.6;
    r('tronco', 0.06, 0.2, 0);
    r('cabeca', -0.1, 0.05, 0);
    r('pernaD', -0.3, 0.25, 0.08); r('canelaD', 0.42, 0, 0); r('peD', -0.12, -0.2, 0);
    r('pernaE', 0.15, -0.1, -0.1); r('canelaE', 0.3, 0, 0); r('peE', -0.35, 0.2, 0);
    r('bracoD', -0.25, 0, 0.3); r('anteD', -0.6, 0, 0);
    r('bracoE', 0.15, 0, -0.25); r('anteE', -0.4, 0, 0);
  }
  // posição do lobo: bem baixo, mãos em garra, uma na frente e outra atrás
  function wolf(r, P, k) {
    P.quadril.obj.position.y += 2.4 * k;
    r('quadril', 0.25 * k, -0.35 * k, 0); r('tronco', 0.35 * k, 0.3 * k, 0); r('cabeca', -0.55 * k, 0.1 * k, 0);
    r('pernaD', -0.9 * k, 0.3 * k, 0.15 * k); r('canelaD', 1.3 * k, 0, 0); r('peD', -0.35 * k, 0, 0);
    r('pernaE', 0.35 * k, -0.2 * k, -0.1 * k); r('canelaE', 1.0 * k, 0, 0); r('peE', -0.9 * k, 0, 0);
    r('bracoD', -1.35 * k, 0.2 * k, 0.2 * k); r('anteD', -0.25 * k, 0, 0); r('maoD', -0.8 * k, 0, 0);
    r('bracoE', 0.6 * k, 0, -0.5 * k); r('anteE', -0.9 * k, 0, 0); r('maoE', -0.8 * k, 0, 0);
  }
  const WALK_T = [[0, -0.42], [0.14, -0.28], [0.36, 0.1], [0.52, 0.42], [0.64, 0.22], [0.82, -0.3]];
  const WALK_K = [[0, 0.06], [0.14, 0.14], [0.4, 0.06], [0.56, 0.34], [0.7, 0.78], [0.86, 0.24]];
  const WALK_B = [[0, 0.1], [0.14, 0.42], [0.36, -0.12], [0.5, 0.1], [0.64, 0.42], [0.86, -0.12]];

  // golpe: [início, lado (1 = direita), alto?]
  const BLOWS = [[0.66, 1, 0], [0.9, -1, 0], [1.12, 1, 1], [1.33, -1, 0], [1.53, 1, 0], [1.72, -1, 1]];

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const breath = loop([[0, 0], [0.42, 1], [0.999, 0]], t / 3.6);
    const sway = loop([[0, -0.05], [0.35, 0.06], [0.6, 0.02], [0.85, -0.06]], t / 3.3);

    if (mode === 'parado') {
      stance(r, P);
      r('tronco', -0.02 * breath, 0, 0);
      // de vez em quando estala o pescoço
      const crack = seq([[0, 0], [0.06, 1, 'out'], [0.14, 1], [0.2, -1, 'io'], [0.28, -1], [0.36, 0]], fr(t / 5.2));
      r('cabeca', 0, 0, 0.25 * crack);
      r('nos', sway, 0, sway * 0.6); r('faixa', sway * 0.5, 0, 0);
      r('maoD', 0.15 * breath, 0, 0);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 0.95;
      for (const [s, off] of [['D', 0], ['E', 0.5]]) {
        r('perna' + s, loop(WALK_T, ph + off), 0, 0);
        r('canela' + s, loop(WALK_K, ph + off), 0, 0);
        r('pe' + s, -0.25 * loop(WALK_K, ph + off - 0.06), 0, 0);
      }
      P.quadril.obj.position.y += loop(WALK_B, ph);
      const tw = loop([[0, 0.07], [0.5, -0.07]], ph - 0.03);
      r('quadril', 0.04, tw, 0); r('tronco', 0.02, -tw * 0.9, 0);
      // gingado de malandro: ombros soltos, braços largos
      r('bracoD', 0.4 * loop([[0, 1], [0.5, -1]], ph), 0, 0.2); r('bracoE', -0.4 * loop([[0, 1], [0.5, -1]], ph), 0, -0.2);
      r('anteD', -0.35, 0, 0); r('anteE', -0.35, 0, 0);
      r('cabeca', -0.05, -tw * 0.5, 0);
      r('nos', 0.4 + 0.12 * loop([[0, 0], [0.5, 1]], ph * 2), 0, 0); r('faixa', 0.3, 0, 0);
      return;
    }

    if (mode === 'lobo') {
      const T = t % 3.6;
      const R = ctx.ref; const RZ = -3.6; R.place(0, RZ, 0);
      const PZ = RZ + 0.75;                       // onde ele bate: colado no alvo
      const crouch = seq([[0, 0], [0.32, 1, 'out'], [0.44, 1]], T);
      const dash = seq([[0.44, 0], [0.62, 1, 'out']], T);
      const back = seq([[2.6, 0], [3.1, 1, 'io']], T);
      // corpo: avança, faz os golpes trocando de lado, e no fim volta pulando
      obj.position.z += PZ * dash * (1 - back);
      if (T < 0.62) { stance(r, P); wolf(r, P, crouch); r('cabeca', -0.25 * seq([[0.2, 0], [0.3, 1], [0.42, 0]], T), 0, 0); }
      if (T >= 0.44 && T < 0.66) {
        // rastro do avanço: riscos de vento ao longo do caminho, cada um numa altura
        for (let i = 0; i < 5; i++) {
          const y = 0.5 + i * 0.3 + 0.08 * Math.sin(i * 2.3), xo = (i - 2) * 0.18;
          ctx.get('vento' + i, () => DBFX.tracer([0.95, 0.9, 0.8])).at(new THREE.Vector3(xo, y, 0.6), new THREE.Vector3(xo, y, PZ + 0.3), FX.clamp01((T - 0.44 - i * 0.012) / 0.2), 1.6);
        }
      }
      if (T >= 0.62 && T < 2.6) {
        stance(r, P);
        wolf(r, P, 0.55);
        let side = 0, hitK = 0, cur = -1;
        BLOWS.forEach(([st, sd, hi], i) => { if (T >= st) { cur = i; side = sd; } });
        if (cur >= 0) {
          const [st, sd, hi] = BLOWS[cur];
          const sw = seq([[st, 0], [st + 0.05, 1, 'snap'], [st + 0.15, 1], [st + 0.2, 0.3]], T);
          hitK = seq([[st + 0.04, 0], [st + 0.06, 1, 'snap'], [st + 0.18, 0]], T);
          // troca de lado num pulo curto e bate com a mão em garra, de cima pra baixo
          const sx = seq([[st - 0.06, -sd * 0.55], [st, sd * 0.55, 'out']], T);
          obj.position.x += sx;
          obj.rotation.y = -0.45 * sd;
          const arm = sd > 0 ? 'D' : 'E', other = sd > 0 ? 'E' : 'D';
          r('braco' + arm, (hi ? -2.5 : -1.7) * sw, 0, (sd > 0 ? 1 : -1) * 0.2 * sw);
          r('ante' + arm, 0.4 * sw, 0, 0); r('mao' + arm, -0.9, 0, 0);
          r('braco' + other, 0.7 * sw, 0, 0); r('tronco', 0.15 * sw, -0.45 * sd * sw, 0);
          const c = new THREE.Vector3(0.25 * sd, hi ? 1.45 : 1.15, RZ + 0.25);
          ctx.claw(c, (T - st - 0.04) / 0.32);
          ctx.shake(0.35 * hitK);
          R.lean(-0.18 * hitK); R.spin(0.22 * sd * hitK);
          R.place(0, RZ - 0.05 * cur, 0);
        }
      }
      if (T >= 2.1 && T < 2.6) {
        // o último: as duas palmas juntas no peito, empurrando
        const k = seq([[2.1, 0], [2.24, -0.4, 'out'], [2.3, 1, 'snap'], [2.45, 1], [2.6, 0.4]], T);
        r('bracoD', -1.6 * Math.max(0, k) + 0.6 * Math.min(0, k), 0, -0.25 * Math.max(0, k)); r('anteD', -0.2, 0, 0);
        r('bracoE', -1.6 * Math.max(0, k) + 0.6 * Math.min(0, k), 0, 0.25 * Math.max(0, k)); r('anteE', -0.2, 0, 0);
        r('tronco', 0.3 * Math.max(0, k), 0, 0);
        r('maoD', 0.9 * Math.max(0, k), 0, 0); r('maoE', 0.9 * Math.max(0, k), 0, 0);
        obj.position.x *= 1 - seq([[2.1, 0], [2.2, 1]], T);
        obj.rotation.y *= 1 - seq([[2.1, 0], [2.2, 1]], T);
      }
      if (T >= 2.3) {
        const kb = seq([[2.3, 0], [2.8, 1, 'out']], T), up = Math.sin(Math.PI * FX.clamp01((T - 2.3) / 0.5));
        R.place(0, RZ - 3.2 * kb, 0.6 * up); R.lean(-0.7 * seq([[2.3, 0], [2.5, 1], [3.3, 1], [3.6, 0]], T));
        ctx.roarRing(new THREE.Vector3(0, 1.1, RZ + 0.1), (T - 2.3) / 0.35);
        ctx.get('impacto', () => DBFX.glow([1.0, 0.9, 0.7])).at(new THREE.Vector3(0, 1.2, RZ + 0.25), 0.35 + 0.5 * FX.clamp01((T - 2.3) / 0.12), Math.max(0, 1 - (T - 2.3) / 0.2), T);
        ctx.shake(Math.max(0, 0.8 - (T - 2.3) / 0.4));
      }
      if (T >= 2.6) {
        // volta num pulo pra trás e cai na posição do lobo, rindo
        stance(r, P);
        const air = Math.sin(Math.PI * FX.clamp01((T - 2.6) / 0.5));
        obj.position.y += 0.6 * air;
        wolf(r, P, 0.6 * (1 - seq([[3.1, 0], [3.5, 1]], T)));
        r('pernaD', -0.5 * air, 0, 0); r('canelaD', 0.9 * air, 0, 0);
      }
      r('nos', 0.3 + 0.6 * dash * (1 - back), 0, 0.2 * Math.sin(T * 6)); r('faixa', 0.4 * dash, 0, 0);
      return;
    }

    if (mode === 'areia') {
      const T = t % 3.2;
      const R = ctx.ref; const RZ = -3.3; R.place(0, RZ, 0);
      stance(r, P);
      // agacha e pega um punhado com a esquerda, levanta jogando de baixo pra cima
      const dip = seq([[0, 0], [0.42, 1, 'out'], [0.52, 1], [0.62, 0, 'in']], T);
      const thr = seq([[0.52, 0], [0.62, 1, 'snap'], [0.85, 1], [1.3, 0]], T);
      P.quadril.obj.position.y += 2.6 * dip;
      r('pernaD', -0.8 * dip, 0, 0); r('canelaD', 1.3 * dip, 0, 0);
      r('pernaE', -0.2 * dip, 0, 0); r('canelaE', 1.4 * dip, 0, 0); r('peE', -0.8 * dip, 0, 0);
      r('tronco', 0.6 * dip - 0.15 * thr, 0.4 * dip - 0.3 * thr, 0);
      r('bracoE', -0.6 * dip - 2.3 * thr, 0, -0.2 * dip); r('anteE', -0.2 * dip - 0.1 * thr, 0, 0); r('maoE', -0.4 * dip + 0.6 * thr, 0, 0);
      r('bracoD', 0.5 * dip, 0, 0.3 * dip);
      r('cabeca', -0.3 * dip, 0, 0);
      // ri da cara do alvo: ombros chacoalhando, a mão no rosto
      const laugh = T > 1.4 && T < 2.9 ? Math.abs(Math.sin((T - 1.4) * 13)) : 0;
      const hand = seq([[1.3, 0], [1.55, 1, 'out'], [2.6, 1], [2.9, 0]], T);
      r('bracoD', -1.5 * hand, -0.4 * hand, 0); r('anteD', -1.5 * hand, 0, 0);
      r('tronco', 0.12 * laugh - 0.1 * hand, 0, 0); r('cabeca', -0.25 * hand + 0.08 * laugh, 0, 0);
      // grãos no punho e o leque de areia
      const hand0 = new THREE.Vector3(-0.42, 0.25, -0.55);
      if (T > 0.3 && T < 0.62) {
        ctx.get('punhado', () => DBFX.grains({
          n: 40, seed: 13, palette: [[214, 170, 110], [190, 140, 86], [236, 200, 140], [160, 110, 70]], size: [0.02, 0.045], drag: 3, grav: 6, life: 0.5, spread: 0.3,
          cone: Rr => [(Rr() - 0.5) * 0.6, 0.6 + Rr() * 0.8, (Rr() - 0.5) * 0.6],
        })).at(hand0, T - 0.3, 0.02);
      }
      if (T >= 0.6) {
        const age = T - 0.6;
        ctx.get('leque', () => DBFX.grains({
          n: 320, seed: 77, palette: [[220, 176, 116], [196, 146, 90], [240, 206, 150], [168, 118, 74], [130, 92, 60]], size: [0.02, 0.06], drag: 2.2, grav: 7, life: 1.2, spread: 0.12,
          cone: Rr => { const a = (Rr() - 0.5) * 1.0, b = (Rr() - 0.3) * 0.7, s = 5 + Rr() * 4; return [Math.sin(a) * s, 1.2 + Math.sin(b) * s * 0.6, -Math.cos(a) * s]; },
        })).at(new THREE.Vector3(-0.35, 1.25, -0.6), age, 0.02);
        for (let i = 0; i < 3; i++) {
          const ag = (age - 0.12 - i * 0.12) / 1.2;
          if (ag > 0 && ag < 1) ctx.get('poeira' + i, FX.volume).set(new THREE.Vector3(-0.2 + i * 0.25, 1.35 + i * 0.1, RZ + 0.6 - i * 0.3), 0.7 + i * 0.15, ag,
            { seed: 21 + i * 4.1, dens: 5, rise: 0.25, smoke: [0.82, 0.68, 0.5] });
        }
        // o alvo fica cego e tonto: vira o rosto, recua tropeçando
        const blind = seq([[0.18, 0], [0.3, 1, 'out'], [2.0, 1], [2.6, 0]], age);
        ctx.blur(0.6 * blind); ctx.dizzy(blind);
        R.lean(-0.35 * blind); R.spin(0.5 * blind + 0.15 * Math.sin(age * 6) * blind);
        R.armsOut(0.3 * blind);
        R.place(0.3 * Math.sin(age * 2) * blind, RZ - 0.7 * seq([[0.2, 0], [1.4, 1, 'out']], age), 0);
      }
      r('nos', sway + 0.3 * thr, 0, 0);
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(2), variant(15), variant(33)];
  const def = v => ({ def: rigDef(painters(v), v), low: rigLow(painters(v)) });
  window.MOBS.push({
    id: 'bandido_deserto', nome: 'Bandido do Deserto', bioma: 'Terras e Deserto', tier: 'Médio · tier 8',
    variants: fixed.map((v, i) => ({ nome: ['Mascarado', 'Barbudo', 'Lenço Azul'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['lobo', 'Punho do Lobo'], ['areia', 'Areia nos Olhos']],
    cam: 6, lookY: 1.1, lookZ: { lobo: -1.8, areia: -1.6 }, camFar: { lobo: 7.5, areia: 7 }, camYaw: { lobo: -1.2, areia: -1.15 },
    info: {
      'Tamanho': '1,85 bloco, do tamanho do jogador',
      'Vida': '60 (tier 8)',
      'Jeito': 'Hostil, sozinho ou em dupla nas rochas do deserto. Fica rodeando e provoca antes de entrar.',
      'Punho do Lobo': 'Agacha na posição do lobo, avança num risco e bate 6 vezes trocando de lado, com a mão em garra. O último golpe são as duas palmas no peito, que arremessam. O dano dos 6 golpes é somado em blocos, pra não sumir contra o Escudo de quem tem tier maior.',
      'Areia nos Olhos': 'Pega um punhado de areia e joga num leque à frente: quem é pego fica cego e tonto por 3 s. Gosta de usar quando você vem correndo.',
      'Solta': 'Couro, faca de bandido; raro: Cápsula (com um item de deserto dentro).',
      'Variações': 'Turbante, colete, faixa, calça, pele, máscara, barba, cicatriz e brinco sorteados.',
    },
  });
})();
