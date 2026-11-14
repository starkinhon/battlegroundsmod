// Kuchisake-onna (Aldeia Amaldiçoada). Tier 6. A "mulher da boca cortada" da lenda urbana, maldição de medo tão
// conhecida que tem forma fixa: alta demais, sobretudo comprido, cabelo preto liso com franja, máscara cirúrgica
// e uma tesoura enorme. Pergunta "eu sou bonita?" e tira a máscara: a boca é rasgada de orelha a orelha.
// Golpes: Eu Sou Bonita? (aparece na sua frente mesmo se você correr, pergunta e corta a sua boca) e
// Tesouradas (três cortes abrindo e fechando a tesoura, o último te joga longe).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  const COATS = [[168, 140, 104], [150, 40, 46], [60, 58, 64]];
  function variant(seed, force) {
    const R = FX.rnd(seed);
    return Object.assign({ seed, coat: COATS[Math.floor(R() * COATS.length)], skin: mix([236, 220, 210], [214, 200, 196], R()), lips: R() < 0.7, size: 1.02 + R() * 0.06 }, force || {});
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.8, hi = 1.12) => {
      const f = 0.62 - 0.4 * q.ny + 0.3 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const P = {};
    P.coat = q => {
      let c = shade(v.coat, q, 1, 0.76, 1.14);
      if (fr(q.ax * 0.4 + n3(q.ax * 0.15, 0, q.az * 0.15, 2 + S) * 0.8) < 0.09) c = mul(c, 0.86);                          // vincos
      return c;
    };
    P.coatFront = q => {
      let c = P.coat(q);
      if (q.f !== 'north') return c;
      const x = q.nx - 0.5;
      if (Math.abs(x - 0.06) < 0.03) c = mul(c, 0.66);                                                                  // abertura (traspasse)
      if (q.ny < 0.24 && Math.abs(Math.abs(x) - (0.3 - q.ny)) < 0.06) c = mul(c, 0.72);                                   // lapela
      if ((Math.abs(x + 0.18) < 0.035 || Math.abs(x - 0.3) < 0.035) && fr(q.ny * 4.2) < 0.2 && q.ny < 0.85) c = [40, 30, 26]; // botões
      if (q.ny > 0.44 && q.ny < 0.5) c = mul(c, 0.7);                                                                    // cinto
      return c;
    };
    P.skin = q => shade(v.skin, q, 3, 0.84, 1.06);
    P.leg = q => { let c = shade([40, 36, 40], q, 4); if (q.ny > 0.86) c = shade([26, 20, 22], q, 5); return c; };      // meia escura e salto
    P.hair = q => {
      const strand = fr(q.ax * 1.2 + q.az * 1.2 + 0.3 * n3(q.ax * 0.3, q.ay * 0.1, q.az * 0.3, 6 + S));
      let c = cel(strand < 0.5 ? [18, 16, 20] : [30, 26, 34], 0.6 - 0.3 * q.ny + (q.f === 'top' ? 0.25 : 0), 0.8, 1.35);
      if (strand > 0.9 && q.ny < 0.5) c = [64, 60, 80];
      return c;
    };
    // rosto: olhos grandes e bonitos (é isso que dá medo), boca rasgada costurada mal por baixo da máscara
    P.face = q => {
      let c = P.skin(q);
      if (q.f !== 'north') return c;
      const x = Math.floor(q.px), y = Math.floor(q.py);
      // olhos (linhas 2-3): cílio em cima, íris escura, brilho
      const eye = (cx) => {
        if (y === 2 && x >= cx - 1 && x <= cx + 1) return [20, 16, 20];
        if (y === 3 && x === cx) return [40, 20, 30];
        if (y === 3 && x === cx - 1) return [250, 250, 250];
        if (y === 3 && x === cx + 1) return [200, 196, 196];
        return null;
      };
      const e1 = eye(2), e2 = eye(5);
      if (e1) return e1; if (e2) return e2;
      // a boca vai de um lado ao outro do rosto, subindo nas pontas (sorriso de corte), com pontos de costura
      const my = 5.6 - 0.5 * Math.abs(x - 3.5) * 0.6;
      if (Math.abs(y - my) < 0.6) return x === 0 || x === 7 ? [120, 20, 30] : [70, 8, 18];
      if (Math.abs(y - my) < 1.2 && (x === 1 || x === 6)) return [150, 40, 50];
      return c;
    };
    // boca aberta (mandíbula): dentes em cima, goela, língua
    P.mouthIn = q => { if (q.f === 'top') return (Math.floor(q.px) % 2 ? [230, 222, 200] : [210, 200, 180]); if (q.f === 'north') return [60, 6, 16]; return [100, 20, 30]; };
    P.jaw = q => {
      let c = P.skin(q);
      if (q.f === 'top') return Math.floor(q.px) % 2 ? [226, 216, 196] : [120, 30, 44];
      if (q.f === 'north' && q.ny < 0.3) c = [80, 10, 20];
      return c;
    };
    P.mask = q => {
      let c = shade([236, 238, 236], q, 7, 0.86, 1.04);
      if (q.f === 'north' && fr(q.py * 0.5) < 0.2) c = mul(c, 0.9);                                                      // pregas da máscara
      return c;
    };
    P.strap = q => [230, 232, 228];
    P.blade = q => { let c = shade([196, 202, 212], q, 8, 0.7, 1.25); if (q.f === 'top' || (q.f === 'north' && q.nx < 0.2)) c = mul(c, 1.15); if (n3(q.ax * 0.6, q.ay * 0.6, q.az * 0.6, 9 + S) > 0.75) c = mix(c, [120, 30, 30], 0.6); return c; };
    P.handle = q => shade([40, 34, 36], q, 10);
    P.pivot = q => [160, 150, 120];
    return P;
  }

  function rig(P, v, lo) {
    // tesoura: duas lâminas que giram no parafuso; cabo em argolas
    const blade = (n, s) => ({
      name: n, pivot: [0, 0, 0], boxes: [
        { x: -0.4 + s * 0.2, y: -0.4, z: -16, w: 0.8, h: s > 0 ? 1.4 : -1.4, d: 16, p: P.blade },
        { x: -0.4 + s * 0.2, y: s > 0 ? 0.6 : -1.6, z: -12, w: 0.8, h: 1, d: 10, p: P.blade },
        { x: -0.6 + s * 0.2, y: s > 0 ? -0.4 : -2.6, z: 0.4, w: 1.2, h: 3, d: 5, p: P.handle },
        ...(lo ? [] : [{ x: -0.7 + s * 0.2, y: s > 0 ? 2.6 : -3.8, z: 0.4, w: 1.4, h: 1.2, d: 5.2, p: P.handle }]),
      ],
    });
    const arm = (s) => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * 4.6, -11.4, 0], rot: [0, 0, s * -0.04], boxes: [{ x: -1.6, y: -1, z: -1.8, w: 3.2, h: 7.6, d: 3.6, p: P.coat }],
      kids: [{
        name: s < 0 ? 'anteD' : 'anteE', pivot: [0, 6.6, 0], boxes: [{ x: -1.5, y: 0, z: -1.7, w: 3, h: 6.6, d: 3.4, p: P.coat }],
        kids: [{
          name: s < 0 ? 'maoD' : 'maoE', pivot: [0, 6.4, 0], boxes: [{ x: -1.1, y: 0, z: -1.2, w: 2.2, h: 2.8, d: 2.4, p: P.skin }],
          kids: s < 0 ? [{ name: 'tesoura', pivot: [0, 1.6, 0], rot: [1.45, 0, 0], kids: [blade('laminaA', 1), blade('laminaB', -1)], boxes: lo ? [] : [{ x: -0.6, y: -0.6, z: -0.6, w: 1.2, h: 1.2, d: 1.2, p: P.pivot }] }] : [],
        }],
      }],
    });
    const leg = s => ({
      name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * 1.7, 9.5, 0], boxes: [{ x: -1.3, y: 0, z: -1.3, w: 2.6, h: 14.5, d: 2.6, p: P.leg }],
      kids: lo ? [] : [{ name: s < 0 ? 'saltoD' : 'saltoE', pivot: [0, 14.5, 0], boxes: [{ x: -1.3, y: -1, z: -2.6, w: 2.6, h: 1, d: 3.2, p: P.leg }] }],
    });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'quadril', pivot: [0, 9.5, 0], boxes: [
            // aba do sobretudo até a canela, abrindo um pouco embaixo
            { x: -4.4, y: -1, z: -2.6, w: 8.8, h: 6, d: 5.2, p: P.coatFront },
            { x: -4.8, y: 5, z: -2.9, w: 9.6, h: 5, d: 5.8, p: P.coatFront },
          ],
          kids: [{
            name: 'tronco', pivot: [0, 0, 0], boxes: [{ x: -4.2, y: -12, z: -2.4, w: 8.4, h: 12, d: 4.8, p: P.coatFront }],
            kids: [
              arm(-1), arm(1),
              {
                name: 'cabeca', pivot: [0, -12, 0], boxes: [
                  { x: -3.6, y: -8, z: -3.6, w: 7.2, h: 8, d: 7.2, p: P.face },
                  { x: -3.6, y: -2.4, z: -3.65, w: 7.2, h: 2.4, d: 0.2, p: P.mouthIn },
                ],
                kids: [
                  { name: 'mandibula', pivot: [0, -1.6, -1], boxes: [{ x: -3.2, y: 0, z: -2.6, w: 6.4, h: 1.6, d: 5, p: P.jaw }] },
                  { name: 'mascara', pivot: [0, -2.6, -3.6], boxes: [{ x: -3.4, y: -2.2, z: -0.7, w: 6.8, h: 4.2, d: 0.8, p: P.mask }, ...(lo ? [] : [{ x: -3.8, y: -1.6, z: 0, w: 0.4, h: 0.5, d: 3.2, p: P.strap }, { x: 3.4, y: -1.6, z: 0, w: 0.4, h: 0.5, d: 3.2, p: P.strap }])] },
                  {
                    name: 'cabelo', pivot: [0, -8, 0], boxes: [
                      { x: -4, y: -0.8, z: -4, w: 8, h: 1.6, d: 8, p: P.hair },
                      { x: -4, y: 0, z: -4.1, w: 8, h: 1.9, d: 0.8, p: P.hair },                                            // franja reta
                      { x: -4.2, y: 0, z: -4, w: 0.9, h: 16, d: 8, p: P.hair }, { x: 3.3, y: 0, z: -4, w: 0.9, h: 16, d: 8, p: P.hair },
                      { x: -4, y: 0, z: 3.3, w: 8, h: 18, d: 0.9, p: P.hair },
                    ],
                  },
                ],
              },
            ],
          }],
        },
        leg(-1), leg(1),
      ],
    };
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const scis = k => { r('laminaA', k * 0.5, 0, 0); r('laminaB', -k * 0.5, 0, 0); };                                    // 0 = fechada, 1 = aberta
    const maskOff = k => { if (!P.mascara) return; P.mascara.obj.position.y += 7 * k; P.mascara.obj.position.z -= 1.2 * k; r('mascara', 0.6 * k, 0, 0); };
    const gape = k => r('mandibula', 0.9 * k, 0, 0);
    const blade = () => { obj.updateMatrixWorld(true); return new THREE.Vector3(0, 0, -12).applyMatrix4(P.laminaA.obj.matrixWorld); };

    if (mode === 'parado') {
      // em pé, rígida, a tesoura pendurada abrindo e fechando devagar (tic... tic...); a cabeça inclina como se reparasse em você
      r('bracoD', 0.05, 0, 0.05); r('anteD', -0.15, 0, 0);
      scis(0.4 * Math.max(0, Math.sin(t * 1.4)) ** 3);
      r('cabeca', 0, 0.3 * Math.sin(t * 0.25), 0.22 * loop([[0, 0], [0.5, 0], [0.6, 1], [0.9, 1], [1, 0]], t / 7));
      r('tronco', 0.02 * Math.sin(t * 0.8), 0, 0);
      return;
    }
    if (mode === 'andar') {
      // passos curtos e rápidos de salto alto, tronco parado, a cabeça não balança nada (anda como boneca)
      const ph = t * 6;
      r('pernaD', 0.45 * Math.sin(ph), 0, 0); r('pernaE', -0.45 * Math.sin(ph), 0, 0);
      r('bracoE', -0.3 * Math.sin(ph), 0, 0); r('bracoD', 0.15, 0, 0); r('anteD', -0.25, 0, 0);
      r('quadril', 0, 0.08 * Math.sin(ph), 0); r('tronco', 0, -0.08 * Math.sin(ph), 0);
      obj.position.y += 0.03 * Math.abs(Math.cos(ph));
      scis(0.2);
      return;
    }
    if (mode === 'pergunta') {
      const T = t % 6.2;
      const R = ctx.ref; const RZ = -1.3;
      // o alvo dá as costas e corre; ela some (pisca) e aparece colada na frente dele. Inclina, pergunta, tira a
      // máscara... a boca rasga aberta, e a tesoura fecha nos cantos da boca dele.
      const run = seq([[0, 0], [1.1, 1]], T);
      const tele = T > 1.0 && T < 1.35;
      if (T < 1.2) { R.place(0, RZ - 2.2 * run, 0); R.spin(0); R.lean(0.25 * run); R.armsOut(-0.2); }
      else { R.place(0, RZ - 2.5, 0); R.spin(Math.PI * seq([[1.2, 0], [1.6, 1, 'out']], T)); R.lean(-0.25 * seq([[1.35, 0], [1.6, 1, 'snap'], [2.2, 0.4]], T)); }
      obj.visible = !(tele && Math.floor(T * 40) % 2 === 0) && !(T > 1.05 && T < 1.3);
      obj.position.z += T < 1.2 ? 0 : -2.2 - 0.4;
      obj.rotation.y = T < 1.2 ? 0 : 0;
      if (T > 1.0 && T < 1.4) ctx.get('fumaca', () => DBFX.grains({ n: 50, seed: 3, palette: [[30, 20, 30], [60, 30, 50], [16, 10, 14]], size: [0.05, 0.12], drag: 3, grav: -0.5, life: 0.6, cone: R2 => { const a2 = R2() * 6.28; return [Math.cos(a2) * 1.4, R2() * 1.6, Math.sin(a2) * 1.4]; } })).at(new THREE.Vector3(0, 1, T < 1.2 ? 0 : -2.6), (T - 1.0) * 1.5, 0);
      const lean = seq([[1.6, 0], [2.4, 1, 'io'], [5.6, 1], [6.1, 0]], T);
      r('tronco', 0.35 * lean, 0, 0); r('cabeca', -0.2 * lean, 0, 0.35 * seq([[2.4, 0], [2.8, 1, 'io'], [3.4, 1], [3.6, 0]], T));
      // mão esquerda puxa a máscara pra baixo
      const pull = seq([[3.4, 0], [3.7, 1, 'io'], [5.6, 1], [6.0, 0]], T);
      r('bracoE', -1.9 * seq([[3.2, 0], [3.45, 1], [3.75, 0.4], [5.6, 0.4], [6.0, 0]], T), 0, -0.3 * pull); r('anteE', -0.6 * pull, 0, 0);
      maskOff(seq([[3.45, 0], [3.75, 1, 'in'], [5.8, 1], [6.1, 0]], T));
      const open = seq([[3.8, 0], [4.1, 1, 'out'], [4.9, 1], [5.2, 0.4], [5.8, 0]], T);
      gape(open); r('cabeca', -0.25 * open, 0, 0);
      // tesoura sobe aberta até a cara dele e fecha
      const raise = seq([[3.9, 0], [4.4, 1, 'io'], [5.2, 1], [5.6, 0]], T);
      r('bracoD', -1.55 * raise, 0.25 * raise, 0); r('anteD', -0.2 * raise, 0, 0);
      const snip = seq([[4.4, 0], [4.6, 1, 'out'], [4.85, 1], [4.92, 0, 'snap']], T);
      scis(snip);
      if (T > 4.92 && T < 5.1) { ctx.flash(new THREE.Vector3(0, 1.45, RZ - 2.5 + 0.2), 1 - (T - 4.92) / 0.18); ctx.shake(0.7); }
      if (T > 4.92 && T < 5.6) ctx.get('sangue', () => DBFX.grains({ n: 40, seed: 12, palette: [[150, 10, 20], [110, 6, 14], [190, 30, 40]], size: [0.025, 0.06], drag: 2, grav: 7, life: 0.7, cone: R2 => [(R2() - 0.5) * 2.6, R2() * 1.2, -R2() * 1.4] })).at(new THREE.Vector3(0, 1.45, RZ - 2.5 + 0.15), T - 4.92, 0);
      // o alvo fica paralisado durante a pergunta (véu escuro nas bordas)
      if (T > 1.6 && T < 5.1) ctx.dark(0.35 * seq([[1.6, 0], [2.2, 1], [4.9, 1], [5.1, 0]], T));
      if (T > 4.92) { R.face(seq([[4.95, 0], [5.15, 1, 'out'], [6.0, 1]], T)); R.lean(-0.2); }
      return;
    }
    if (mode === 'tesouradas') {
      const T = t % 3.6;
      const R = ctx.ref; const RZ = -1.8; R.place(0, RZ, 0); R.spin(Math.PI);
      // corte horizontal da direita, corte de volta, estocada aberta que fecha dentro (o terceiro arremessa)
      const c1 = seq([[0.2, 0], [0.45, 1, 'out'], [0.6, 1]], T), c2 = seq([[0.7, 0], [0.95, 1, 'out'], [1.1, 1]], T);
      const st = seq([[1.4, 0], [1.75, 1, 'io'], [1.9, 1]], T), ret = seq([[2.6, 0], [3.2, 1, 'io']], T);
      const k = 1 - ret;
      r('bracoD', (-1.4 * (c1 + c2 > 0 ? 1 : 0) * (1 - st) - 1.5 * st) * k, (0.9 - 1.8 * c1 + 1.8 * c2) * (1 - st) * (c1 > 0 ? 1 : 0) * k, 0);
      r('tronco', 0.15 * st * k, (-0.5 * c1 + 0.9 * c2 - 0.4) * (c1 > 0 ? 1 : 0) * (1 - st) * k, 0);
      obj.position.z -= 0.5 * st * k;
      r('pernaD', -0.4 * st * k, 0, 0); r('pernaE', 0.3 * st * k, 0, 0);
      scis((T < 0.3 || (T > 0.6 && T < 0.75) ? 1 : 0) * 0.9 + (T > 1.2 && T < 1.8 ? 1 : 0) * (1 - seq([[1.75, 0], [1.82, 1, 'snap']], T)));
      r('cabeca', 0, 0, 0.3 * Math.sin(T * 4) * k);
      const tip = blade();
      if (T > 0.3 && T < 0.6) ctx.slash(new THREE.Vector3(0.1, 1.25, RZ + 0.35), (T - 0.3) / 0.3);
      if (T > 0.8 && T < 1.1) ctx.slash(new THREE.Vector3(-0.1, 1.05, RZ + 0.35), (T - 0.8) / 0.3);
      for (const [t0, lean] of [[0.45, -0.15], [0.95, 0.15]]) if (T > t0 && T < t0 + 0.2) { R.lean(-0.15); R.spin(Math.PI + lean); }
      const fly = FX.clamp01((T - 1.8) / 0.7);
      if (T > 1.8) { R.place(0, RZ - 2.4 * FX.EASE.out(fly), 0.7 * Math.sin(Math.PI * fly)); R.lean(-0.7 * Math.min(1, fly * 3) * (1 - ret)); }
      if (T > 1.78 && T < 1.95) { ctx.flash(tip, 1 - (T - 1.78) / 0.17); ctx.shake(0.9); }
      if (T > 1.8 && T < 2.3) ctx.claw(new THREE.Vector3(0, 1.2, RZ + 0.2), (T - 1.8) / 0.5);
      return;
    }
    if (mode === 'morte') {
      const { T, fall } = JJFX.exorcize(t, obj, e, ctx, { h: 2.6, refZ: -2.0, rotX: 1.4, back: 0.5, ashY: 0.3, ashZ: 1.2, n: 170 });
      // a máscara cai antes; a boca abre num grito mudo; a tesoura fica no chão (some por último)
      maskOff(Math.min(1, T * 2)); gape(Math.min(1, T * 1.5));
      r('bracoD', -0.4 * fall, 0, 0.6 * fall); scis(0.6 * fall);
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(9, { coat: COATS[0] }), variant(31, { coat: COATS[1] }), variant(55, { coat: COATS[2] })];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'kuchisake', nome: 'Kuchisake-onna', bioma: 'Aldeia Amaldiçoada', tier: 'Grau 2 · tier 6',
    variants: fixed.map((v, i) => ({ nome: ['Sobretudo Bege', 'Sobretudo Vermelho', 'Sobretudo Preto'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteada', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['pergunta', 'Eu Sou Bonita?'], ['tesouradas', 'Tesouradas'], ['morte', 'Exorcizada']],
    cam: 4.6, lookY: 1.6, lookZ: { pergunta: -2.6, tesouradas: -1.0, morte: 0 }, camFar: { pergunta: 4.6, tesouradas: 5.6 }, camYaw: { pergunta: -1.25, tesouradas: -1.2 },
    info: {
      'Tamanho': '2,2 blocos (mais alta que o jogador)',
      'Vida': '50',
      'Jeito': 'Anda pelas vielas de noite, de máscara, abrindo e fechando a tesoura. Não corre: quando você foge, ela some e aparece na sua frente.',
      'Eu Sou Bonita?': 'Aparece colada na sua frente e pergunta. Você fica paralisado 2 s; ela tira a máscara, a boca rasga e a tesoura fecha nos cantos da sua boca (dano alto + sangramento). Se você "responder" segurando um Doce Amaldiçoado (bala de mel, a resposta da lenda), ela fica confusa e vai embora.',
      'Tesouradas': 'Dois cortes de lado e uma estocada com a tesoura aberta que fecha dentro de você e te joga 2 blocos.',
      'Fraqueza': 'Correr não adianta (ela teleporta). Ficar parado de frente e bater quando ela tira a máscara cancela o corte.',
      'Solta': 'Máscara Cirúrgica (cosmético); raro: Tesoura da Boca Rasgada (arma: o terceiro golpe seguido corta duas vezes).',
      'Variações': 'Cor do sobretudo (bege, vermelho ou preto).',
    },
  });
})();
