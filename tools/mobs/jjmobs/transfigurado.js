// Transfigurado (Shibuya, perto da estação). Tier 8. Gente comum que o Mahito tocou e remodelou a alma: cada um
// deformado de um jeito (cabeça inchada, braço comprido arrastando, pescoço torto, boca a mais), pele cinza
// remendada com pontos, resto de roupa do dia a dia. Golpes: Investida Desengonçada e Abraço que Morde.
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  const SKINS = [[150, 156, 168], [146, 148, 138], [162, 150, 152], [130, 140, 152], [154, 160, 144]];
  const CLOTH = [[54, 60, 78], [214, 210, 200], [40, 40, 44], [112, 36, 40], [92, 104, 70], [168, 150, 120]];
  const PANTS = [[34, 36, 46], [60, 58, 66], [74, 68, 58], [40, 50, 74]];
  const HAIR = [[30, 26, 30], [80, 54, 36], [140, 120, 90], [20, 20, 24]];
  function variant(seed, force) {
    const R = FX.rnd(seed);
    const pick = a => a[Math.floor(R() * a.length)];
    const v = {
      seed, skin: pick(SKINS), shirt: pick(CLOTH), pants: pick(PANTS), hair: pick(HAIR),
      head: 1 + R() * 0.8, armD: 1 + R() * 0.9, armE: 0.7 + R() * 0.4, legs: 0.88 + R() * 0.22,
      tilt: (R() - 0.5) * 1.1, bellyMouth: R() < 0.45, eyes3: R() < 0.35, hair0: R() < 0.6, size: 0.9 + R() * 0.2,
    };
    return Object.assign(v, force || {});
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.8, hi = 1.12) => {
      const f = 0.62 - 0.4 * q.ny + 0.35 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    // costura: linha escura com pontos claros atravessando, corre torta pelo corpo (a alma foi remendada)
    const stitch = q => {
      const w = q.ax * 0.18 + q.ay * 0.11 + n3(q.ax * 0.15, q.ay * 0.15, q.az * 0.15, 4 + S) * 2.2;
      const d = Math.abs(fr(w) - 0.5);
      if (d < 0.03) return 2;
      if (d < 0.06 && fr(q.ay * 0.9 + q.ax * 0.4) < 0.25) return 1;
      return 0;
    };
    const skin = (q, s = 1) => {
      let c = shade(v.skin, q, s, 0.78, 1.1);
      if (n3(q.ax * 0.25, q.ay * 0.25, q.az * 0.25, 7 + S) > 0.66) c = mix(c, [130, 140, 160], 0.4);   // retalho de outra cor
      const st = stitch(q);
      if (st === 2) c = [60, 54, 66]; else if (st === 1) c = [214, 210, 214];
      return c;
    };
    const P = {};
    P.skin = q => skin(q);
    P.head = q => {
      let c = skin(q, 2);
      if (!v.hair0 && (q.f === 'top' || (q.f !== 'north' && q.ny < 0.25) || q.f === 'south')) {
        if (n3(q.ax * 0.8, q.ay * 0.8, q.az * 0.8, 9 + S) > 0.4) c = shade(v.hair, q, 3);                // tufos que sobraram
      }
      if (q.f !== 'north') return c;
      // rosto: olhos de tamanhos diferentes, um caído; boca torta de orelha a orelha
      const W = q.b.w, H = q.b.h, x = q.px / W, y = q.py / H;
      const eye = (cx, cy, rx, ry) => { const dx = (x - cx) / rx, dy = (y - cy) / ry; return dx * dx + dy * dy; };
      const e1 = eye(0.3, 0.36, 0.13, 0.11), e2 = eye(0.7, 0.44, 0.09, 0.08), e3 = v.eyes3 ? eye(0.5, 0.2, 0.08, 0.07) : 9;
      for (const ee of [e1, e2, e3]) { if (ee < 0.35) return [16, 12, 16]; if (ee < 1) return [226, 222, 210]; }
      const my = 0.7 + 0.08 * (x - 0.5) + 0.05 * Math.sin(x * 9 + S);
      if (Math.abs(y - my) < 0.045 && x > 0.12 && x < 0.9) return [50, 20, 26];
      if (Math.abs(y - my) < 0.1 && x > 0.12 && x < 0.9 && fr(x * W * 0.5) < 0.5) return mul(v.skin, 0.75);  // lábio remendado
      return c;
    };
    P.shirt = q => {
      let c = shade(v.shirt, q, 5, 0.76, 1.12);
      if (fr(q.ay * 0.3 + n3(q.ax * 0.3, 0, q.az * 0.3, 11 + S)) < 0.1) c = mul(c, 0.8);                // amassado
      // rasgado: buracos mostrando pele
      if (n3(q.ax * 0.4, q.ay * 0.4, q.az * 0.4, 12 + S) > 0.68) return skin(q, 6);
      return c;
    };
    P.pants = q => {
      let c = shade(v.pants, q, 13, 0.78, 1.12);
      if (q.ny > 0.85 && n3(q.ax, q.ay, q.az, 14 + S) > 0.5) return skin(q, 8);                           // barra rasgada
      return c;
    };
    P.shoe = q => shade([34, 30, 30], q, 15);
    P.bellyMouth = q => {
      if (q.f !== 'north') return skin(q, 9);
      const x = q.px / q.b.w, y = q.py / q.b.h;
      if (Math.abs(y - 0.5 - 0.1 * Math.sin(x * 6)) < 0.18 && x > 0.08 && x < 0.92) return Math.floor(q.px) % 2 ? [222, 214, 190] : [60, 16, 26];
      if (Math.abs(y - 0.5) < 0.34) return [150, 80, 92];
      return skin(q, 9);
    };
    return P;
  }

  function rigDef(P, v) {
    const hs = v.head, hw = 7 * hs, hh = 7 * hs, hd = 7 * hs;
    const arm = (s, L) => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * 4.8, -10.4, 0], rot: [0, 0, s * -0.08], boxes: [{ x: -1.5, y: -1, z: -1.5, w: 3, h: 6.2 * L, d: 3, p: P.shirt }],
      kids: [{
        name: s < 0 ? 'anteD' : 'anteE', pivot: [0, 5.6 * L, 0], boxes: [{ x: -1.3, y: 0, z: -1.3, w: 2.6, h: 5.6 * L, d: 2.6, p: P.skin }],
        kids: [{ name: s < 0 ? 'maoD' : 'maoE', pivot: [0, 5.4 * L, 0], boxes: [{ x: -1.5, y: 0, z: -1.5, w: 3, h: 2.6 * Math.sqrt(L), d: 3, p: P.skin }] }],
      }],
    });
    const lg = v.legs, legH = 12 * lg, hipY = 24 - legH;
    const leg = s => ({
      name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * 2, hipY, 0], boxes: [{ x: -1.9, y: 0, z: -1.9, w: 3.8, h: legH * 0.5, d: 3.8, p: P.pants }],
      kids: [{
        name: s < 0 ? 'canelaD' : 'canelaE', pivot: [0, legH * 0.5, 0], boxes: [{ x: -1.8, y: 0, z: -1.8, w: 3.6, h: legH * 0.5 - 1, d: 3.6, p: P.pants }],
        kids: [{ name: s < 0 ? 'peD' : 'peE', pivot: [0, legH * 0.5 - 1, 0], boxes: [{ x: -1.9, y: 0, z: -2.6, w: 3.8, h: 1, d: 4.4, p: P.shoe }] }],
      }],
    });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'quadril', pivot: [0, hipY, 0], boxes: [{ x: -4, y: -2, z: -2.2, w: 8, h: 2.6, d: 4.4, p: P.pants }],
          kids: [{
            name: 'tronco', pivot: [0, -1.6, 0], boxes: [
              { x: -4.2, y: -10.8, z: -2.3, w: 8.4, h: 10.8, d: 4.6, p: P.shirt },
              ...(v.bellyMouth ? [{ x: -2.6, y: -6.6, z: -2.75, w: 5.2, h: 3, d: 0.6, p: P.bellyMouth }] : []),
            ],
            kids: [
              {
                name: 'pescoco', pivot: [0, -10.6, 0], rot: [0, 0, v.tilt * 0.4], boxes: [{ x: -1.4, y: -2.2, z: -1.4, w: 2.8, h: 2.4, d: 2.8, p: P.skin }],
                kids: [{ name: 'cabeca', pivot: [0, -2, 0], rot: [0, 0, v.tilt], boxes: [{ x: -hw / 2, y: -hh, z: -hd / 2 - 0.4, w: hw, h: hh, d: hd, p: P.head }] }],
              },
              arm(-1, v.armD), arm(1, v.armE),
            ],
          }],
        },
        leg(-1), leg(1),
      ],
    };
  }
  function rigLow(P, v) {
    const d = rigDef(P, v);
    // low poly: tira mão e pé, junta antebraço no braço
    const strip = n => { if (!n.kids) return; n.kids = n.kids.filter(k => !/^(mao|pe)/.test(k.name)); n.kids.forEach(strip); };
    strip(d);
    return d;
  }

  function slouch(r, P, v, k = 1) {
    r('quadril', 0.1 * k, 0, 0); r('tronco', 0.3 * k, 0, 0.12 * k * Math.sign(v.tilt));
    r('pescoco', 0.35 * k, 0, 0); r('cabeca', -0.25 * k, 0, 0);
    // o braço comprido pesa e puxa o ombro pra baixo
    r('bracoD', 0.15 * k, 0, 0.1 * (v.armD - 1)); r('anteD', -0.1 * k, 0, 0);
    r('bracoE', -0.1 * k, 0, -0.05); r('anteE', -0.5 * k, 0, 0);
    r('pernaD', -0.1 * k, 0.15, 0); r('canelaD', 0.25 * k, 0, 0);
    r('pernaE', 0.05 * k, -0.25, 0); r('canelaE', 0.1 * k, 0, 0);
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const v = e.v, s = v.size; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    // espasmo: o corpo dá trancos sem motivo, como quem não manda mais nele
    const sp = Math.pow(Math.max(0, n3(t * 2.2, 0, 0, v.seed) - 0.55) * 2.2, 2);
    const jerk = (n3(t * 14, 1, 0, v.seed) - 0.5) * sp;

    if (mode === 'parado') {
      slouch(r, P, v);
      r('cabeca', 0.4 * jerk, 0.8 * jerk, 0.3 * Math.sin(t * 0.5));
      r('tronco', 0.15 * jerk, 0.2 * jerk, 0);
      r('bracoD', 0.12 * Math.sin(t * 0.9), 0, 0); r('anteE', 0.6 * jerk, 0, 0);
      r('maoD', 0.4 * loop([[0, 0], [0.5, 1]], t * 0.8), 0, 0);
      return;
    }
    if (mode === 'andar') {
      // manca: a perna boa dá o passo, a outra arrasta; o braço comprido vai arrastando atrás
      const ph = t * 0.75;
      slouch(r, P, v, 1.2);
      r('pernaD', loop([[0, -0.55], [0.35, 0.4], [0.55, 0.45], [0.8, -0.2]], ph), 0, 0);
      r('canelaD', loop([[0, 0.2], [0.2, 0.9], [0.45, 0.1], [0.8, 0.15]], ph), 0, 0);
      r('pernaE', loop([[0, 0.25], [0.5, 0.05], [0.7, -0.35]], ph), 0, 0.05);
      r('canelaE', 0.1, 0, 0); r('peE', 0.3, 0, 0);
      const lurch = loop([[0, 0], [0.3, 1], [0.6, 0.2], [0.85, -0.3]], ph);
      r('quadril', 0, 0.12 * lurch, 0.12 * lurch); r('tronco', 0.1 * lurch, 0, -0.18 * lurch);
      P.quadril.obj.position.y += 0.8 * lurch;
      r('bracoD', 0.3 + 0.1 * lurch, 0, 0); r('anteD', 0.2, 0, 0);
      r('bracoE', -0.3 * lurch, 0, 0);
      r('cabeca', 0.3 * jerk, 0.5 * jerk - 0.2 * lurch, 0.25 * lurch);
      return;
    }

    if (mode === 'investida') {
      const T = t % 3.4;
      const R = ctx.ref; const RZ = -4.6; R.place(0, RZ, 0);
      // grita de boca aberta (cabeça pra trás), dispara desengonçado, abana os braços igual hélice e tromba
      const scream = seq([[0.1, 0], [0.5, 1, 'out'], [0.75, 1], [0.85, 0]], T);
      const run = FX.clamp01((T - 0.8) / 0.9);
      slouch(r, P, v, 1 - scream * 0.6);
      r('pescoco', -0.6 * scream, 0, 0); r('cabeca', -0.3 * scream, 0, 0); r('tronco', -0.3 * scream, 0, 0);
      r('bracoD', -0.4 * scream, 0, 0.6 * scream); r('bracoE', -0.4 * scream, 0, -0.6 * scream);
      if (T > 0.8 && T < 1.7) {
        const ph = (T - 0.8) * 3.2;
        obj.position.z += (RZ + 1.3) * FX.EASE.in(run * 0.6 + 0.4 * run * run);
        r('tronco', 0.55, 0, 0);
        r('pernaD', loop([[0, -0.9], [0.5, 0.7]], ph), 0, 0); r('canelaD', loop([[0, 0.3], [0.25, 1.3], [0.5, 0.2]], ph), 0, 0);
        r('pernaE', loop([[0, 0.7], [0.5, -0.9]], ph), 0, 0); r('canelaE', loop([[0, 0.2], [0.75, 1.3]], ph), 0, 0);
        P.quadril.obj.position.y += 0.9 * Math.abs(Math.sin(ph * Math.PI * 2));
        // braços girando soltos
        r('bracoD', -1.4 + 2.6 * fr(ph * 0.9), 0, 0.5); r('bracoE', -1.4 + 2.6 * fr(ph * 0.9 + 0.5), 0, -0.5);
        r('cabeca', 0.5 * Math.sin(ph * 7), 0.4 * Math.sin(ph * 5), 0);
      }
      if (T >= 1.7) {
        const c = T - 1.7;
        obj.position.z += RZ + 1.3;
        const rec = seq([[0, 1], [0.12, 0.6, 'out'], [0.9, 0.3], [1.5, 0]], c);
        r('tronco', 0.7 * rec, 0, 0); r('bracoD', -1.2 * rec, 0, 0.2); r('bracoE', -1.0 * rec, 0, -0.2);
        r('cabeca', 0.4 * jerk, 0.6 * jerk, 0);
        const kb = seq([[0, 0], [0.4, 1, 'out']], c), up = Math.sin(Math.PI * FX.clamp01(c / 0.4));
        R.place(0, RZ - 2.2 * kb, 0.8 * up); R.lean(-1.3 * seq([[0, 0], [0.45, 1, 'in'], [1.3, 1], [1.6, 0]], c));
        ctx.shake(Math.max(0, 0.8 - c * 2));
        ctx.get('poeira', FX.volume).set(new THREE.Vector3(0, 0.4, RZ + 0.4), 0.9, Math.min(0.999, c / 1.1), { seed: 3.3, dens: 6, rise: 0.3, squash: 0.7, smoke: [0.6, 0.6, 0.62] });
      }
      return;
    }

    if (mode === 'abraco') {
      const T = t % 4.0;
      const R = ctx.ref; const RZ = -1.55; R.place(0, RZ, 0);
      // abre os braços desiguais, fecha num abraço, e morde o ombro várias vezes; o alvo empurra e se solta
      const open = seq([[0.1, 0], [0.6, 1, 'out'], [0.7, 1]], T) * (1 - seq([[0.7, 0], [0.82, 1, 'snap']], T));
      const hug = seq([[0.7, 0], [0.82, 1, 'snap'], [2.9, 1], [3.1, 0, 'out']], T);
      const push = seq([[2.9, 0], [3.1, 1, 'snap'], [3.6, 0]], T);
      slouch(r, P, v, 1 - hug * 0.5);
      r('bracoD', -1.3 * open - 1.35 * hug, -0.2 * hug, 0.9 * open - 0.55 * hug); r('anteD', -0.2 * open - 0.9 * hug, 0, 0);
      r('bracoE', -1.3 * open - 1.35 * hug, 0.2 * hug, -0.9 * open + 0.55 * hug); r('anteE', -0.2 * open - 0.9 * hug, 0, 0);
      r('tronco', 0.25 * hug, 0, 0);
      const chomp = T > 0.9 && T < 2.9 ? Math.max(0, Math.sin((T - 0.9) * 9)) : 0;
      r('pescoco', 0.3 * hug + 0.25 * chomp, 0.4 * hug, 0); r('cabeca', 0.2 * chomp, 0.3 * hug, 0.3 * jerk);
      obj.position.z += -0.35 * hug + 0.6 * push; obj.position.y += 0;
      R.armsOut(0.5 * hug); R.lean(-0.15 * hug + 0.3 * push * 0); R.spin(0.15 * Math.sin(T * 6) * hug);
      R.punch(push);
      if (chomp > 0.9) { ctx.shake(0.25); }
      if (T > 0.9 && T < 2.9) {
        ctx.get('sangue', () => DBFX.grains({
          n: 36, seed: 17, palette: [[150, 16, 24], [110, 8, 16], [190, 30, 36]], size: [0.025, 0.06], drag: 1.3, grav: 9.8, life: 0.7, spread: 1.6,
          cone: Rr => { const a = Rr() * Math.PI * 2, o = 0.3 + Rr() * 1.2; return [Math.cos(a) * o, 0.5 + Rr() * 1.8, Math.sin(a) * o]; },
        })).at(new THREE.Vector3(0.2, 1.5, RZ + 0.3), T - 0.9, 0.02);
      }
      if (push > 0) { obj.rotation.x = -0.25 * push; }
      return;
    }

    if (mode === 'morte') {
      const { fall } = JJFX.exorcize(t, obj, e, ctx, { h: 2.2, refZ: -2.0, rotX: -1.45, back: 0.5, ashY: 0.3, ashZ: 1.0, n: 180 });
      slouch(r, P, v, 1 - fall);
      r('bracoD', -0.6 * fall, 0, 0.6 * fall); r('bracoE', -0.6 * fall, 0, -0.6 * fall); r('cabeca', 0, 0.5 * fall, 0);
      // nenhum membro escora o morto: o corpo encosta no chão (teste R5)
      JJFX.floorLimbs(obj, P, ['tronco', 'quadril'], ['bracoD', 'bracoE', 'pernaD', 'pernaE', 'pescoco'], fall);
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [
    variant(7, { head: 1.75, armD: 1.1, armE: 0.9, tilt: 0.5, bellyMouth: false, eyes3: true }),
    variant(31, { head: 1.05, armD: 1.85, armE: 0.75, tilt: -0.3, bellyMouth: true }),
    variant(58, { head: 1.3, armD: 1.4, armE: 1.0, legs: 0.86, tilt: 0.9, bellyMouth: true, eyes3: false }),
  ];
  const def = v => ({ def: rigDef(painters(v), v), low: rigLow(painters(v), v) });
  window.MOBS.push({
    id: 'transfigurado', nome: 'Transfigurado', bioma: 'Shibuya', tier: 'Grau 3 · tier 8',
    variants: fixed.map((v, i) => ({ nome: ['Cabeçudo', 'Braço Comprido', 'Torto'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['investida', 'Investida Desengonçada'], ['abraco', 'Abraço que Morde'], ['morte', 'Exorcizado']],
    cam: 5.2, lookY: 1.2, lookZ: { investida: -2.4, abraco: -0.8, morte: -1.0 }, camFar: { investida: 8.5, abraco: 5.2, morte: 6 }, camYaw: { investida: -1.25, abraco: -1.1, morte: -1.1 },
    info: {
      'Tamanho': 'Do tamanho do jogador, mas cada um com uma proporção errada',
      'Vida': '40',
      'Jeito': 'Hostil, em bandos de 2 a 4. Só de noite pelas ruas e no metrô de Shibuya; de dia ficam parados, tremendo, nas plataformas. Não fogem nunca.',
      'Investida Desengonçada': 'Grita de cabeça pra trás e dispara cambaleando, braços girando soltos; tromba e te joga 2 blocos. Erra fácil se você sai do caminho.',
      'Abraço que Morde': 'Abre os braços desiguais, te prende e morde o ombro várias vezes (sangramento). Bater 3 vezes ou empurrar solta.',
      'Solta': 'Resíduo de energia amaldiçoada; raro: alma remendada (ingrediente pra técnicas de alma).',
      'Variações': 'Tamanho da cabeça, comprimento de cada braço, pernas curtas, pescoço torto, terceiro olho, boca na barriga, roupa e cabelo que sobrou: nenhum igual ao outro.',
    },
  });
})();
