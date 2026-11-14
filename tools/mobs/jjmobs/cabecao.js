// Cabeção do Telhado (grau 3, Shibuya e telhados das escolas). Tier 8. A maldição do cap. 1: corpo de barril corcunda,
// a cabeça é quase só boca (dentes de gente, amarelos), olhinhos miúdos em fileira, braços compridos que arrastam.
// Golpes: Braço Comprido (estica, agarra, leva até a boca e morde) e Bote do Telhado (pula alto e cai de queixo).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  const SKINS = [[148, 140, 160], [138, 150, 144], [160, 146, 138], [126, 128, 150], [150, 150, 126]];
  function variant(seed) {
    const R = FX.rnd(seed);
    const skin = SKINS[Math.floor(R() * SKINS.length)].map(c => c * (0.92 + R() * 0.12));
    return { seed, skin, belly: mix(skin, [214, 206, 196], 0.45), eyes: 3 + Math.floor(R() * 4), gap: Math.floor(R() * 6), size: 0.92 + R() * 0.16, drool: R() < 0.6 };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.78, hi = 1.12) => {
      const f = 0.62 - 0.4 * q.ny + 0.35 * (n3(q.ax * 0.25, q.ay * 0.25, q.az * 0.25, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    // couro grosso de maldição: dobras horizontais que caem, manchas escuras e algumas verrugas
    const hide = (q, s = 1) => {
      let c = shade(v.skin, q, s);
      const fold = fr(q.ay * 0.32 + n3(q.ax * 0.2, 0, q.az * 0.2, 3 + S) * 1.4);
      if (fold < 0.09) c = mul(c, 0.72);
      else if (fold < 0.16) c = mul(c, 1.1);
      if (n3(q.ax * 0.22, q.ay * 0.22, q.az * 0.22, 5 + S) > 0.7) c = mix(c, [86, 70, 98], 0.4);
      if (n3(Math.floor(q.ax), Math.floor(q.ay), Math.floor(q.az), 6 + S) > 0.93) c = mul(c, 0.8);
      return c;
    };
    const P = {};
    P.hide = q => hide(q);
    P.belly = q => {
      let c = shade(v.belly, q, 7, 0.84, 1.08);
      if (fr(q.ay * 0.4) < 0.12) c = mul(c, 0.82);
      return c;
    };
    P.head = q => {
      let c = hide(q, 2);
      if (q.f !== 'north') return c;
      const x = Math.floor(q.px), y = Math.floor(q.py);
      // fileira de olhinhos amarelos, sem pálpebra, cada um num tamanho de cova
      const n = v.eyes, span = 12, x0 = 1 + (span - (n * 2 - 1)) / 2;
      for (let i = 0; i < n; i++) {
        const ex = Math.floor(x0 + i * 2), ey = 2 + ((i * 7 + S) % 2);
        if (x === ex && y === ey) return [236, 210, 70];
        if (x === ex && y === ey + 1) return [24, 14, 10];
        if (Math.abs(x - ex) <= 1 && Math.abs(y - ey - 0.5) <= 1.2) c = mul(v.skin, 0.55);
      }
      if (y >= 6) c = mix(c, [120, 60, 70], 0.35);                                                      // gengiva aparecendo
      return c;
    };
    P.mouth = q => (q.f === 'north' || q.f === 'top' || q.f === 'bottom' ? mix([64, 14, 22], [110, 30, 40], n3(q.ax * 0.5, q.ay * 0.5, q.az * 0.5, 8 + S)) : hide(q, 3));
    P.gum = q => shade([150, 70, 84], q, 9, 0.8, 1.1);
    P.tooth = q => {
      const k = n3(q.ax * 2, q.ay * 2, q.az * 2, 10 + S);
      let c = mix([232, 222, 186], [190, 170, 110], k * 0.8);
      if (q.ny > 0.75 && q.f !== 'top') c = mul(c, 0.86);
      return c;
    };
    P.tongue = q => {
      let c = shade([176, 70, 92], q, 11, 0.8, 1.15);
      if (q.f === 'top' && Math.abs(q.nx - 0.5) < 0.1) c = mul(c, 0.8);
      return c;
    };
    P.nail = q => [54, 44, 50];
    P.foot = q => (q.f === 'bottom' ? mul(v.skin, 0.5) : hide(q, 12));
    P.drool = q => [196, 210, 200];
    return P;
  }

  function rigDef(P, v) {
    const fingers = s => [-0.9, 0.1, 1.1].map((x, i) => ({
      name: 'dedo' + (s < 0 ? 'D' : 'E') + i, pivot: [x * 1.0, 2.2, -0.4 - i * 0.2], rot: [0.25 - i * 0.05, 0, (x) * 0.12],
      boxes: [{ x: -0.45, y: 0, z: -0.45, w: 0.9, h: 3.6 + (i === 1 ? 0.8 : 0), d: 0.9, p: P.hide }, { x: -0.4, y: 3.4 + (i === 1 ? 0.8 : 0), z: -0.6, w: 0.8, h: 1.1, d: 0.6, p: P.nail }],
    }));
    const arm = s => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * 6.6, -9.5, 0], rot: [-0.1, 0, s * -0.18], boxes: [{ x: -1.4, y: -1, z: -1.4, w: 2.8, h: 11.5, d: 2.8, p: P.hide }],
      kids: [{
        name: s < 0 ? 'anteD' : 'anteE', pivot: [0, 10.8, 0], rot: [-0.2, 0, 0], boxes: [{ x: -1.15, y: 0, z: -1.15, w: 2.3, h: 11, d: 2.3, p: P.hide }],
        kids: [{ name: s < 0 ? 'maoD' : 'maoE', pivot: [0, 10.6, 0], boxes: [{ x: -1.6, y: 0, z: -1.5, w: 3.2, h: 2.4, d: 2.6, p: P.hide }], kids: fingers(s) }],
      }],
    });
    const leg = s => ({
      name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * 3.4, 13.5, 0.5], boxes: [{ x: -2, y: 0, z: -2, w: 4, h: 5.5, d: 4.2, p: P.hide }],
      kids: [{
        name: s < 0 ? 'canelaD' : 'canelaE', pivot: [0, 5.2, 0], rot: [0, 0, 0], boxes: [{ x: -1.7, y: 0, z: -1.6, w: 3.4, h: 4.6, d: 3.4, p: P.hide }],
        kids: [{ name: s < 0 ? 'peD' : 'peE', pivot: [0, 4.4, 0], boxes: [{ x: -2.1, y: 0, z: -3.4, w: 4.2, h: 0.9, d: 5, p: P.foot }, ...[-1.6, -0.3, 1.0].map(x => ({ x, y: 0.1, z: -4.1, w: 0.8, h: 0.7, d: 0.8, p: P.nail }))] }],
      }],
    });
    const teethUp = [], teethDn = [];
    for (let i = 0; i < 9; i++) {
      if (i === v.gap) continue;
      const x = -6 + i * 1.45, h = 1.6 + ((i * 5 + v.seed) % 3) * 0.35;
      teethUp.push({ x, y: -0.1, z: -8.6, w: 1.2, h, d: 1.0, p: P.tooth });
    }
    for (let i = 0; i < 8; i++) { const x = -5.4 + i * 1.5, h = 1.3 + ((i * 3 + v.seed) % 3) * 0.3; teethDn.push({ x, y: -h, z: -7.7, w: 1.2, h, d: 1.0, p: P.tooth }); }
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'quadril', pivot: [0, 13.5, 0], boxes: [{ x: -5, y: -2.4, z: -3.6, w: 10, h: 3.6, d: 7.6, p: P.hide }],
          kids: [{
            name: 'tronco', pivot: [0, -2, 0.4], boxes: [
              { x: -6.2, y: -11, z: -4.8, w: 12.4, h: 11.2, d: 9.4, p: P.hide },
              { x: -4.6, y: -9.6, z: -5.4, w: 9.2, h: 8.6, d: 0.8, p: P.belly },
              { x: -5.4, y: -12.6, z: -3.6, w: 10.8, h: 2, d: 8, p: P.hide },
              { x: -3.6, y: -13.4, z: 0.2, w: 7.2, h: 2, d: 4.6, p: P.hide },                                   // corcova
            ],
            kids: [
              {
                name: 'cabeca', pivot: [0, -11.5, -1.2], boxes: [
                  { x: -7.2, y: -7.6, z: -8.6, w: 14.4, h: 7.6, d: 12, p: P.head },
                  { x: -6.4, y: -9, z: -7.4, w: 12.8, h: 1.6, d: 9.8, p: P.hide },
                  { x: -6.6, y: -0.4, z: -8.2, w: 13.2, h: 0.8, d: 1.8, p: P.gum },
                  { x: -6.6, y: -0.1, z: -6.6, w: 13.2, h: 0.5, d: 9.6, p: P.mouth },
                  ...teethUp,
                ],
                kids: [{
                  name: 'mandibula', pivot: [0, 0.2, 2.8], boxes: [
                    { x: -6.8, y: 0, z: -11.2, w: 13.6, h: 4.2, d: 11.6, p: P.hide },
                    { x: -6.2, y: -0.3, z: -10.6, w: 12.4, h: 0.6, d: 10.6, p: P.mouth },
                    ...teethDn.map(b => ({ ...b, z: b.z - 2.9 })),
                    ...(v.drool ? [{ x: 2.2, y: 4.2, z: -11, w: 0.6, h: 2.6, d: 0.6, p: P.drool }] : []),
                  ],
                  kids: [{ name: 'lingua', pivot: [0, -0.6, -3], boxes: [{ x: -2.6, y: -1.2, z: -6.4, w: 5.2, h: 1.6, d: 7, p: P.tongue }] }],
                }],
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
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * 6.6, -9.5, 0], rot: [-0.1, 0, s * -0.18], boxes: [{ x: -1.3, y: -1, z: -1.3, w: 2.6, h: 22, d: 2.6, p: P.hide }],
      kids: [{ name: s < 0 ? 'maoD' : 'maoE', pivot: [0, 21, 0], boxes: [{ x: -1.6, y: 0, z: -1.6, w: 3.2, h: 4.5, d: 2.8, p: P.hide }] }],
    });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'quadril', pivot: [0, 13.5, 0], boxes: [], kids: [{
            name: 'tronco', pivot: [0, -2, 0.4], boxes: [{ x: -6.2, y: -11, z: -4.8, w: 12.4, h: 13.6, d: 9.4, p: P.hide }],
            kids: [
              {
                name: 'cabeca', pivot: [0, -11.5, -1.2], boxes: [{ x: -7.2, y: -8.6, z: -8.6, w: 14.4, h: 8.6, d: 12, p: P.head }, { x: -6.6, y: -0.1, z: -8.4, w: 13.2, h: 1.8, d: 1.2, p: P.tooth }],
                kids: [{ name: 'mandibula', pivot: [0, 0.2, 2.8], boxes: [{ x: -6.8, y: 0, z: -11.2, w: 13.6, h: 4.2, d: 11.6, p: P.hide }, { x: -6.2, y: -1.4, z: -11, w: 12.4, h: 1.4, d: 1.2, p: P.tooth }] }],
              },
              arm(-1), arm(1),
            ],
          }],
        },
        { name: 'pernaD', pivot: [-3.4, 13.5, 0.5], boxes: [{ x: -2, y: 0, z: -2, w: 4, h: 10.5, d: 4.2, p: P.foot }] },
        { name: 'pernaE', pivot: [3.4, 13.5, 0.5], boxes: [{ x: -2, y: 0, z: -2, w: 4, h: 10.5, d: 4.2, p: P.foot }] },
      ],
    };
  }

  function hunch(r, P, k = 1) {
    r('quadril', 0.18 * k, 0, 0); P.quadril.obj.position.y += 0.8 * k;
    r('tronco', 0.38 * k, 0, 0); r('cabeca', -0.42 * k, 0, 0);
    r('pernaD', -0.3 * k, 0.08, 0.1); r('canelaD', 0.5 * k, 0, 0); r('peD', -0.2 * k, 0, 0);
    r('pernaE', -0.3 * k, -0.08, -0.1); r('canelaE', 0.5 * k, 0, 0); r('peE', -0.2 * k, 0, 0);
    // braços pendurados quase no chão, dedos arrastando
    r('bracoD', 0.12 * k, 0, 0.08 * k); r('anteD', -0.12 * k, 0, 0); r('bracoE', 0.12 * k, 0, -0.08 * k); r('anteE', -0.12 * k, 0, 0);
  }
  const WALK_T = [[0, -0.45], [0.25, -0.1], [0.5, 0.35], [0.75, 0.05]];
  const WALK_K = [[0, 0.1], [0.3, 0.1], [0.55, 0.3], [0.75, 0.9], [0.9, 0.4]];

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size * 1.3; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const breath = loop([[0, 0], [0.45, 1], [0.999, 0]], t / 2.6);
    // a boca nunca fecha de verdade: mastiga o ar, ofega
    const pant = loop([[0, 0.22], [0.2, 0.45], [0.5, 0.28], [0.7, 0.42]], t / 1.7);

    if (mode === 'parado') {
      hunch(r, P);
      r('tronco', -0.05 * breath, 0, 0); r('cabeca', 0.06 * breath, 0.25 * Math.sin(t * 0.4), 0);
      r('mandibula', pant, 0, 0); r('lingua', 0.15 * Math.sin(t * 1.3), 0, 0);
      r('bracoD', 0.06 * breath, 0, 0); r('bracoE', 0.06 * breath, 0, 0);
      for (let i = 0; i < 3; i++) { r('dedoD' + i, 0.25 * loop([[0, 0], [0.5, 1]], t * 0.7 + i * 0.2), 0, 0); r('dedoE' + i, 0.25 * loop([[0, 0], [0.5, 1]], t * 0.6 + i * 0.3), 0, 0); }
      return;
    }
    if (mode === 'andar') {
      const ph = t * 0.85;
      hunch(r, P, 1.1);
      for (const [sd, off] of [['D', 0], ['E', 0.5]]) {
        r('perna' + sd, loop(WALK_T, ph + off), 0, 0);
        r('canela' + sd, loop(WALK_K, ph + off), 0, 0);
        r('pe' + sd, -0.25 * loop(WALK_K, ph + off - 0.1), 0, 0);
        // braço arrasta atrasado, balança pesado
        r('braco' + sd, 0.25 * loop([[0, 1], [0.5, -1]], ph + off + 0.15), 0, 0);
        r('ante' + sd, -0.15 + 0.15 * loop([[0, 1], [0.5, -1]], ph + off + 0.3), 0, 0);
      }
      const sway = loop([[0, 1], [0.5, -1]], ph);
      P.quadril.obj.position.y += 0.5 * Math.abs(sway);
      r('quadril', 0, 0.1 * sway, 0.08 * sway); r('tronco', 0, -0.06 * sway, -0.1 * sway);
      r('cabeca', 0.05, 0.1 * sway, 0.08 * sway); r('mandibula', pant, 0, 0);
      return;
    }

    if (mode === 'agarrao') {
      const T = t % 4.6;
      const R = ctx.ref; const RZ = -4.4;
      hunch(r, P);
      // 1) aponta o braço, 2) o braço estica (estica o próprio braço, osso que estala), 3) agarra e puxa até a boca, 4) morde, 5) cospe longe
      const aim = seq([[0.2, 0], [0.6, 1, 'out']], T);
      const reach = seq([[0.6, 0], [0.82, 1, 'snap'], [1.2, 1], [1.75, 0, 'io']], T);
      const lift = seq([[1.0, 0], [1.75, 1, 'io']], T);
      const bite = seq([[1.9, 0], [2.0, 1, 'snap'], [2.5, 1], [2.6, 0.2]], T) * (T < 3.2 ? 1 : 0);
      const toss = seq([[3.0, 0], [3.2, 1, 'snap'], [3.6, 0.3]], T);
      r('bracoD', -1.9 * aim - 0.55 * lift - 0.6 * toss, 0, -0.18 * aim);
      r('anteD', 0.12 * aim - 0.5 * lift, 0, 0);
      P.bracoD.obj.scale.y = 1 + 0.8 * reach; P.anteD.obj.scale.y = 1 + 0.75 * reach;
      r('tronco', -0.15 * aim + 0.25 * bite, 0.2 * aim, 0);
      r('cabeca', -0.25 * lift + 0.3 * bite, 0.1 * aim, 0);
      const open = Math.max(seq([[1.3, 0], [1.85, 1, 'out']], T) * (1 - bite), 0);
      r('mandibula', pant * (1 - open) + 0.75 * open, 0, 0);
      for (let i = 0; i < 3; i++) r('dedoD' + i, -0.4 * aim + 1.1 * seq([[0.8, 0], [0.9, 1, 'snap']], T) * (1 - toss), 0, 0);
      // o alvo vai junto com a mão
      obj.updateMatrixWorld(true);
      const hand = new THREE.Vector3(); P.maoD.obj.getWorldPosition(hand);
      const held = T > 0.85 && T < 3.2;
      if (held) {
        const v = new THREE.Vector3(hand.x, Math.max(0, hand.y - 1.1), hand.z - 0.15);
        R.place(v.x, v.z, v.y); R.armsOut(0.6 + 0.3 * Math.sin(T * 9)); R.lean(0.2 * Math.sin(T * 7));
        if (T > 2.0 && T < 2.5) { ctx.shake(0.5); ctx.dark(0.25); }
      } else if (T >= 3.2) {
        const f = FX.clamp01((T - 3.2) / 0.55);
        R.place(-2.4 * f, -1.2 - 3.0 * f, Math.max(0, 1.6 * Math.sin(Math.PI * f) + 1.2 * (1 - f)));
        R.spin(4 * f); R.lean(1.2 * f);
      } else R.place(1.25, RZ, 0);
      if (T > 2.0) {
        const age = T - 2.0;
        ctx.get('sangue', () => DBFX.grains({
          n: 50, seed: 33, palette: [[150, 16, 24], [110, 8, 16], [190, 30, 36]], size: [0.03, 0.07], drag: 1.2, grav: 9.8, life: 1.0,
          cone: Rr => { const a = Rr() * Math.PI * 2, o = 0.4 + Rr() * 1.8; return [Math.cos(a) * o, 0.8 + Rr() * 2, Math.sin(a) * o]; },
        })).at(new THREE.Vector3(hand.x, 2.3, hand.z), age, 0.02);
      }
      return;
    }

    if (mode === 'bote') {
      const T = t % 3.8;
      const R = ctx.ref; const RZ = -4.2; R.place(0, RZ, 0);
      const crouch = seq([[0.1, 0], [0.6, 1, 'out'], [0.7, 1], [0.76, 0, 'in']], T);
      const jt = FX.clamp01((T - 0.74) / 0.75);
      hunch(r, P, 1 - 0.5 * (jt > 0 && jt < 1 ? 1 : 0));
      if (T < 0.76) {
        P.quadril.obj.position.y += 3.0 * crouch;
        r('pernaD', -0.8 * crouch, 0, 0); r('canelaD', 1.4 * crouch, 0, 0); r('peD', -0.5 * crouch, 0, 0);
        r('pernaE', -0.8 * crouch, 0, 0); r('canelaE', 1.4 * crouch, 0, 0); r('peE', -0.5 * crouch, 0, 0);
        r('bracoD', 0.6 * crouch, 0, 0.4 * crouch); r('bracoE', 0.6 * crouch, 0, -0.4 * crouch);
        r('mandibula', pant + 0.3 * crouch, 0, 0); r('cabeca', 0.2 * crouch, 0, 0);
      } else if (jt < 1) {
        // no ar: sobe alto, vira de queixo pra baixo, boca escancarada
        obj.position.z += (RZ + 1.0) * FX.EASE.io(jt);
        obj.position.y += 3.4 * Math.sin(Math.PI * jt);
        obj.rotation.x = -0.9 * FX.EASE.in(jt);
        r('pernaD', 0.6, 0, 0.2); r('canelaD', 0.5, 0, 0); r('pernaE', 0.6, 0, -0.2); r('canelaE', 0.5, 0, 0);
        r('bracoD', -2.4, 0, 0.6); r('bracoE', -2.4, 0, -0.6);
        r('mandibula', 0.9, 0, 0); r('lingua', -0.3, 0, 0);
      } else {
        const c = T - 1.49;
        const getUp = seq([[0.7, 0], [1.4, 1, 'io']], c);
        obj.position.z += RZ + 1.0;
        obj.rotation.x = -0.9 * (1 - getUp);
        obj.position.y += -0.2 * (1 - getUp);
        r('bracoD', -1.6 * (1 - getUp), 0, 0.9 * (1 - getUp)); r('bracoE', -1.6 * (1 - getUp), 0, -0.9 * (1 - getUp));
        r('mandibula', 0.9 * (1 - seq([[0, 0], [0.12, 1, 'snap']], c)) + pant * getUp + 0.3 * seq([[0.12, 1], [0.6, 0]], c), 0, 0);
        const at = new THREE.Vector3(0, 0.05, RZ + 0.3);
        ctx.roarRing(at, c / 0.55);
        ctx.shake(Math.max(0, 1 - c / 0.5));
        ctx.get('poeira', FX.volume).set(at.clone().add(new THREE.Vector3(0, 0.5, 0)), 1.7, Math.min(0.999, c / 1.4), { seed: 4.2, dens: 7, rise: 0.4, squash: 0.6, smoke: [0.55, 0.53, 0.56] });
        ctx.get('cacos', () => FX.debris(22, 41, [[90, 92, 100], [70, 70, 78], [120, 118, 112], [58, 56, 60]])).at(at, c);
        // o alvo foi esmagado: some debaixo do queixo e sai rolando quando ele levanta
        const out = seq([[0.75, 0], [1.2, 1, 'out']], c);
        R.place(-1.8 * out, RZ - 0.6 - 1.2 * out, 0); R.lean(-1.5 * (1 - out) * (c < 1.5 ? 1 : 0)); R.spin(-1.2 * out);
      }
      return;
    }

    if (mode === 'morte') {
      // leva o golpe e recua o tronco; os joelhos cedem (ajoelha), e a cabeça pesada puxa tudo de cara no chão,
      // com um quique; braços e boca moles. Nada fica de pé em cima das pernas.
      const { T } = JJFX.exorcize(t, obj, e, ctx, { h: 2.0, refZ: -2.6, rotX: 0, back: 0.25, ashY: 0.4, ashZ: -0.9, n: 220 });
      const { seq } = FX;
      const recoil = seq([[0.2, 0], [0.32, 1, 'snap'], [0.6, 0.35], [0.85, 0]], T);
      const buckle = seq([[0.32, 0], [0.7, 1, 'in']], T);
      const tip = seq([[0.55, 0], [1.0, 1, 'in'], [1.1, 0.9], [1.2, 1]], T);
      const limp = seq([[0.8, 0], [1.3, 1]], T);
      hunch(r, P, 1 - 0.6 * buckle);
      r('tronco', -0.55 * recoil + 0.35 * buckle - 0.25 * tip, 0, 0);
      r('cabeca', 0.35 * recoil + 0.15 * tip, 0.25 * limp, 0.15 * limp);
      r('mandibula', 0.25 + 0.4 * recoil + 0.3 * limp, 0, 0);
      for (const [sd, sg] of [['D', 1], ['E', -1]]) {
        r('perna' + sd, -1.35 * buckle + 0.5 * tip, 0, 0.12 * sg * buckle);
        r('canela' + sd, 2.3 * buckle - 0.7 * tip, 0, 0);
        r('pe' + sd, -0.9 * buckle + 0.5 * tip, 0, 0);
        // braços: jogam pra trás no golpe, depois caem moles pra frente e espalham no chão
        r('braco' + sd, -0.7 * recoil + 0.4 * buckle - 0.35 * tip, 0, sg * (0.25 * recoil + 0.3 * limp));
        r('ante' + sd, 0.3 * limp, 0, 0);
      }
      const ang = -1.3 * tip;
      obj.rotation.x = ang;
      JJFX.noSlide(obj, P.tronco.obj, 'z', () => { obj.rotation.x = 0; }, () => { obj.rotation.x = ang; }, 0.6 * tip);
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(5), variant(21), variant(44)];
  const def = v => ({ def: rigDef(painters(v), v), low: rigLow(painters(v), v) });
  window.MOBS.push({
    id: 'cabecao', nome: 'Cabeção do Telhado', bioma: 'Shibuya', tier: 'Grau 3 · tier 8',
    variants: fixed.map((v, i) => ({ nome: ['Cinza', 'Musgo', 'Lilás'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['agarrao', 'Braço Comprido'], ['bote', 'Bote do Telhado'], ['morte', 'Exorcizado']],
    cam: 6.5, lookY: 1.5, lookZ: { agarrao: -2.4, bote: -2.2, morte: -1.0 }, camFar: { agarrao: 10, bote: 10.5, morte: 8.5 }, camYaw: { agarrao: -1.2, bote: -1.3, morte: -1.1 },
    info: {
      'Tamanho': '2,5 blocos curvado (bem maior que o jogador)',
      'Vida': '60 (grau 3)',
      'Jeito': 'Hostil, sozinho. Fica de cócoras em cima dos prédios baixos e das escolas de Shibuya e desce quando você passa embaixo. De noite anda pelas ruas.',
      'Braço Comprido': 'Aponta, e o braço estica 6 blocos num estalo. Agarra, te levanta até a boca, morde (dano + escurece a tela) e te cospe longe. Bater no braço esticado faz soltar.',
      'Bote do Telhado': 'Agacha, pula 3 blocos de altura e cai de queixo onde você estava: onda no chão, poeira e cacos. Quem fica embaixo é esmagado e sai rolando. Dá pra desviar vendo a sombra.',
      'Solta': 'Resíduo de energia amaldiçoada; raro: dente amaldiçoado.',
      'Variações': 'Cor do couro, quantos olhos (3 a 6), dente faltando, baba.',
    },
  });
})();
