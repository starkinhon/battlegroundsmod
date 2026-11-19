// Mosca Amaldiçoada (grau 4, todos os biomas de Jujutsu, mais em Shibuya). Tier 10. As cabeças-mosca do cap. 1:
// uma cabeça humana careca e flácida com olhos esbugalhados e beiço grosso, corpo e asas de mosca.
// Golpes: Mordida (recua, dispara e arranca um pedaço) e Grudar na Cara (abraça sua cabeça e tapa a visão).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  const SKINS = [[188, 168, 176], [176, 170, 140], [158, 170, 150], [196, 160, 158], [168, 156, 184]];
  function variant(seed) {
    const R = FX.rnd(seed);
    const skin = SKINS[Math.floor(R() * SKINS.length)].map(c => c * (0.92 + R() * 0.12));
    return { seed, skin, lip: mix(skin, [130, 52, 74], 0.55 + R() * 0.2), size: 0.85 + R() * 0.3, cyclops: R() < 0.22, look: R() < 0.5 ? 1 : -1, veins: 0.5 + R() * 0.5 };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.8, hi = 1.12) => {
      const f = 0.62 - 0.4 * (q.ny || 0) + 0.35 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    // pele flácida: manchas roxas e veias finas que correm sem rumo
    const flesh = (q, s = 1) => {
      let c = shade(v.skin, q, s, 0.8, 1.1);
      const bl = n3(q.ax * 0.35, q.ay * 0.35, q.az * 0.35, 20 + S);
      if (bl > 0.68) c = mix(c, [120, 84, 110], 0.35);
      const w = n3(q.ax * 0.5, q.ay * 0.5, q.az * 0.5, 21 + S) * 4;
      if (Math.abs(fr(w) - 0.5) < 0.05 * v.veins) c = mix(c, [110, 60, 90], 0.45);
      return c;
    };
    const P = {};
    P.head = q => {
      let c = flesh(q, 2);
      if (q.f === 'top' && n3(q.ax * 1.4, 0, q.az * 1.4, 22 + S) > 0.72) c = mul(c, 0.82);                // pelos ralos
      if (q.f !== 'north') return c;
      const x = Math.floor(q.px), y = Math.floor(q.py);
      // testa enrugada, sobrancelha nenhuma, narinas de porco
      if (y === 1 && (x + S) % 3 !== 0) c = mul(c, 0.86);
      if (y === 2 && x >= 2 && x <= 4) c = mul(c, 0.9);
      if (y === 4 && (x === 2 || x === 4)) c = [70, 40, 50];
      if (y === 4 && x === 3) c = mul(c, 1.08);
      if (y >= 6) c = mul(c, 0.9);                                                                         // papada
      return c;
    };
    P.eye = q => {
      if (q.f !== 'north') return flesh(q, 3);
      const x = Math.floor(q.px), y = Math.floor(q.py);
      let c = [236, 230, 214];
      if ((x + y + S) % 4 === 0) c = [222, 150, 140];                                                     // vasinho
      const px = v.look > 0 ? 2 : 1;
      if (x === px && y === 1) c = [20, 14, 18];
      if (x === px && y === 2) c = [70, 40, 30];
      if (y === 0) c = mul(v.skin, 0.75);                                                                  // pálpebra caída
      return c;
    };
    P.bigEye = q => {
      if (q.f !== 'north') return flesh(q, 3);
      const x = Math.floor(q.px), y = Math.floor(q.py);
      let c = [236, 230, 214];
      if (n3(x * 1.3, y * 1.3, 0, 30 + S) > 0.72) c = [214, 132, 124];
      if ((x === 2 || x === 3) && (y === 2 || y === 3)) c = [26, 16, 18];
      if (x === 2 && y === 2) c = [70, 40, 30];
      if (y === 0) c = mul(v.skin, 0.72);
      return c;
    };
    P.lip = q => {
      let c = shade(v.lip, q, 4, 0.78, 1.14);
      if (q.f === 'bottom') c = mul(v.lip, 0.6);
      if (q.f === 'north' && fr(q.px * 0.9) < 0.15) c = mul(c, 0.85);                                     // rachaduras do beiço
      return c;
    };
    P.mouthIn = q => {
      const y = Math.floor(q.py), x = Math.floor(q.px);
      if (q.f === 'north' && y === 0) return x % 2 === 0 ? [228, 220, 190] : [200, 190, 160];           // dentes de gente
      return [70, 18, 30];
    };
    P.bristle = q => [40, 30, 34];
    P.thorax = q => {
      let c = shade([66, 58, 60], q, 5, 0.75, 1.2);
      if (n3(q.ax * 1.2, q.ay * 1.2, q.az * 1.2, 23 + S) > 0.66) c = [96, 86, 80];
      if (q.f === 'top' && Math.abs(q.nx - 0.5) < 0.1) c = [40, 34, 36];
      return c;
    };
    P.abd = q => {
      let c = shade(mix(v.skin, [96, 84, 90], 0.55), q, 6, 0.78, 1.14);
      if (fr(q.az * 0.45 + 0.2) < 0.32) c = mul(c, 0.62);                                                 // listras
      if (q.f === 'bottom') c = mix(c, v.skin, 0.4);
      return c;
    };
    P.leg = q => (fr(q.ay * 0.7) < 0.2 ? [70, 58, 56] : [38, 30, 32]);
    P.wingD = q => wing(q, 1 - q.nx);
    P.wingE = q => wing(q, q.nx);
    function wing(q, u) {
      if (q.f !== 'top' && q.f !== 'bottom') return null;
      const vv = q.nz, vc = 0.42 + 0.08 * u;
      const hw = 0.5 * Math.pow(Math.max(0, Math.sin(Math.PI * Math.min(1, u * 0.92 + 0.08))), 0.6);
      const dv = Math.abs(vv - vc);
      if (dv > hw) return null;
      const edge = hw - dv < 0.04 || u > 0.97;
      const vein = Math.floor(q.pz) === 2 && u < 0.8;
      if (edge || vein) return [74, 58, 54];
      if ((Math.floor(q.px) + Math.floor(q.pz)) % 2 === 0) return null;                                    // membrana rala (dá pra ver através)
      return mix([214, 222, 232], [196, 206, 186], 0.5 + 0.5 * Math.sin(u * 7 + q.pz)); // brilho de óleo
    }
    P.lowBody = q => (q.az < 1 ? P.thorax(q) : P.abd(q));
    return P;
  }

  function rigDef(P, v) {
    const legs = [];
    for (const s of [-1, 1]) for (const [i, z] of [[0, -0.6], [1, 1.0], [2, 2.6]].map((a, k) => [k, a[1]])) {
      legs.push({
        name: 'perna' + (s < 0 ? 'D' : 'E') + i, pivot: [s * 1.7, 2.2, z], rot: [0.35 - i * 0.25, 0, s * -0.35],
        boxes: [{ x: -0.35, y: 0, z: -0.35, w: 0.7, h: 2.8, d: 0.7, p: P.leg }],
        kids: [{ name: 'tarso' + (s < 0 ? 'D' : 'E') + i, pivot: [0, 2.6, 0], rot: [0.7, 0, 0], boxes: [{ x: -0.3, y: 0, z: -0.3, w: 0.6, h: 2.6, d: 0.6, p: P.leg }] }],
      });
    }
    const eyes = v.cyclops
      ? [{ x: -2.2, y: -3.0, z: -7.9, w: 4.4, h: 4.2, d: 1.3, p: P.bigEye }]
      : [{ x: -3.5, y: -2.7, z: -7.8, w: 3, h: 3, d: 1.1, p: P.eye }, { x: 0.5, y: -2.7, z: -7.8, w: 3, h: 3, d: 1.1, p: P.eye }];
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'corpo', pivot: [0, 12, 0], boxes: [{ x: -2.6, y: -2.6, z: -1, w: 5.2, h: 5, d: 4.4, p: P.thorax }],
        kids: [
          {
            name: 'cabeca', pivot: [0, -0.4, -1], boxes: [
              { x: -3.6, y: -3.8, z: -7, w: 7.2, h: 7.2, d: 7, p: P.head },
              { x: -3.0, y: -4.6, z: -6.2, w: 6, h: 0.8, d: 5.6, p: P.head },
              { x: -3.2, y: 3.3, z: -6.6, w: 6.4, h: 1, d: 5, p: P.head },
              ...eyes,
              { x: -2.5, y: 0.9, z: -7.9, w: 5, h: 1.5, d: 1.2, p: P.lip },
              { x: -2.2, y: 0.9, z: -7.15, w: 4.4, h: 2.6, d: 0.3, p: P.mouthIn },
              ...[[-2.2, -1.2], [-0.6, -3.2], [1.0, -0.6], [2.2, -2.6], [0.2, 1.8]].map(([x, z], i) => ({ x, y: -5.4 - (i % 2) * 0.4, z, w: 0.4, h: 1.2 + (i % 3) * 0.3, d: 0.4, p: P.bristle })),
            ],
            kids: [{ name: 'boca', pivot: [0, 2.4, -7.1], boxes: [{ x: -2.4, y: 0, z: -0.8, w: 4.8, h: 1.4, d: 1.2, p: P.lip }] }],
          },
          { name: 'abdomen', pivot: [0, 0.4, 3.2], rot: [0.25, 0, 0], boxes: [{ x: -2.3, y: -2.1, z: 0, w: 4.6, h: 4.2, d: 5.2, p: P.abd }, { x: -1.4, y: -1.3, z: 5, w: 2.8, h: 2.8, d: 1.6, p: P.abd }] },
          { name: 'asaD', pivot: [-1.6, -2.6, 0.8], rot: [0.1, 0.35, 0.2], boxes: [{ x: -8.5, y: 0, z: -1.4, w: 8.5, h: 0, d: 4.8, p: P.wingD }] },
          { name: 'asaE', pivot: [1.6, -2.6, 0.8], rot: [0.1, -0.35, -0.2], boxes: [{ x: 0, y: 0, z: -1.4, w: 8.5, h: 0, d: 4.8, p: P.wingE }] },
          ...legs,
        ],
      }],
    };
  }
  function rigLow(P, v) {
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'corpo', pivot: [0, 12, 0], boxes: [{ x: -2.4, y: -2.4, z: -1, w: 4.8, h: 4.8, d: 10, p: P.lowBody }],
        kids: [
          {
            name: 'cabeca', pivot: [0, -0.4, -1], boxes: [
              { x: -3.6, y: -3.8, z: -7, w: 7.2, h: 7.2, d: 7, p: P.head },
              ...(v.cyclops ? [{ x: -2.2, y: -3.0, z: -7.9, w: 4.4, h: 4.2, d: 1.3, p: P.bigEye }] : [{ x: -3.5, y: -2.7, z: -7.8, w: 7, h: 3, d: 1.1, p: P.eye }]),
              { x: -2.5, y: 0.9, z: -7.9, w: 5, h: 1.5, d: 1.2, p: P.lip },
            ],
            kids: [{ name: 'boca', pivot: [0, 2.4, -7.1], boxes: [{ x: -2.4, y: 0, z: -0.8, w: 4.8, h: 1.4, d: 1.2, p: P.lip }] }],
          },
          { name: 'asaD', pivot: [-1.6, -2.4, 0.8], rot: [0.1, 0.35, 0.2], boxes: [{ x: -8.5, y: 0, z: -1.4, w: 8.5, h: 0, d: 4.8, p: P.wingD }] },
          { name: 'asaE', pivot: [1.6, -2.4, 0.8], rot: [0.1, -0.35, -0.2], boxes: [{ x: 0, y: 0, z: -1.4, w: 8.5, h: 0, d: 4.8, p: P.wingE }] },
          { name: 'pernaD0', pivot: [-1.4, 2.2, 1], rot: [0.4, 0, 0.3], boxes: [{ x: -0.4, y: 0, z: -0.4, w: 0.8, h: 4, d: 0.8, p: P.leg }] },
          { name: 'pernaE0', pivot: [1.4, 2.2, 1], rot: [0.4, 0, -0.3], boxes: [{ x: -0.4, y: 0, z: -0.4, w: 0.8, h: 4, d: 0.8, p: P.leg }] },
        ],
      }],
    };
  }

  // asas: batida rápida (no jogo vira borrão de 2 quadros), em cima e embaixo
  function flap(r, t, k = 1, speed = 16) {
    const a = loop([[0, -0.55], [0.45, 0.85], [0.55, 0.85]], t * speed) * k;
    r('asaD', 0, 0, -a); r('asaE', 0, 0, a);
  }
  function legsIdle(r, t, k = 1) {
    for (let i = 0; i < 3; i++) for (const sd of ['D', 'E']) {
      const ph = t * 1.3 + i * 0.31 + (sd === 'D' ? 0 : 0.5);
      r('perna' + sd + i, 0.12 * loop([[0, -1], [0.5, 1]], ph) * k, 0, 0);
      r('tarso' + sd + i, 0.18 * loop([[0, 1], [0.5, -1]], ph + 0.1) * k, 0, 0);
    }
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const bob = loop([[0, 0], [0.3, 1], [0.7, -0.6]], t / 1.3);
    // tique de mosca: a cabeça vira num estalo, para, vira de novo
    const tic = seq([[0, 0], [0.03, 1, 'snap'], [0.4, 1], [0.43, -0.6, 'snap'], [0.75, -0.6], [0.8, 0]], fr(t / 2.3));
    const chew = 0.25 * Math.max(0, Math.sin(t * 9)) * (fr(t / 3.3) < 0.5 ? 1 : 0);

    if (mode === 'parado') {
      obj.position.y += 0.6 + 0.12 * bob;
      flap(r, t); legsIdle(r, t);
      r('corpo', 0.08 * bob, 0, 0.05 * Math.sin(t * 0.9));
      r('cabeca', -0.05 * bob, 0.4 * tic, 0.15 * tic);
      r('boca', chew, 0, 0);
      r('abdomen', 0.08 * loop([[0, 0], [0.5, 1]], t * 1.1), 0, 0);
      return;
    }
    if (mode === 'andar') {
      // voo em zigue-zague: corre um trecho, para no ar, muda de rumo
      const zz = loop([[0, 0], [0.18, 1], [0.32, 1], [0.5, 0], [0.68, -1], [0.82, -1]], t / 2.4);
      obj.position.x += 0.6 * zz;
      obj.position.y += 0.7 + 0.2 * loop([[0, 0], [0.25, 1], [0.6, -0.5]], t / 0.9);
      obj.rotation.y = 0.35 * loop([[0, 0], [0.09, 1], [0.32, 0.3], [0.41, 0], [0.59, -1], [0.82, -0.3]], t / 2.4);
      obj.rotation.z = -0.25 * zz;
      flap(r, t, 1.15, 20);
      r('corpo', 0.35, 0, 0); r('cabeca', -0.25, 0.3 * tic, 0);
      for (let i = 0; i < 3; i++) for (const sd of ['D', 'E']) { r('perna' + sd + i, 0.55, 0, 0); r('tarso' + sd + i, 0.3, 0, 0); }
      r('boca', chew, 0, 0);
      return;
    }

    if (mode === 'mordida') {
      const T = t % 2.8;
      const R = ctx.ref; const RZ = -2.4; R.place(0, RZ, 0);
      const back = seq([[0.45, 0], [0.85, 1, 'out'], [0.92, 1]], T) * (1 - seq([[0.92, 0], [1.02, 1, 'snap']], T));
      const dart = seq([[0.92, 0], [1.02, 1, 'snap'], [1.42, 1], [1.95, 0, 'io']], T);
      obj.position.y += 1.0 + 0.1 * bob;
      obj.position.z += 0.45 * back - 1.62 * dart;
      flap(r, t, 1 + 0.4 * back, 16 + 10 * back);
      legsIdle(r, t, 0.6);
      // mira: inclina pra trás e escancara o beiço
      r('corpo', -0.3 * back + 0.25 * dart, 0, 0);
      r('cabeca', -0.35 * back, 0.3 * tic * (1 - back - dart), 0);
      const open = Math.max(back * 0.9, seq([[1.0, 0.9], [1.05, 0, 'snap']], T) * dart);
      r('boca', 0.08 + 0.95 * open, 0, 0);
      P.cabeca.obj.position.y -= 0.6 * back;
      // depois da mordida: sacode a cabeça arrancando o pedaço
      const tear = T > 1.05 && T < 1.42 ? Math.sin((T - 1.05) * 46) * (1 - (T - 1.05) / 0.37) : 0;
      r('cabeca', 0, 0.45 * tear, 0.3 * tear); obj.position.x += 0.06 * tear;
      // volta lambendo o beiço
      if (T > 1.95) r('boca', 0.25 * Math.abs(Math.sin((T - 1.95) * 10)), 0, 0);
      if (T > 1.02) {
        const age = T - 1.02;
        const hit = new THREE.Vector3(0.05, 1.72, RZ + 0.28);
        ctx.get('sangue', () => DBFX.grains({
          n: 40, seed: 61, palette: [[150, 16, 24], [110, 8, 16], [190, 30, 36]], size: [0.025, 0.06], drag: 1.2, grav: 9.8, life: 0.9,
          cone: Rr => { const a = Rr() * Math.PI * 2, o = 0.4 + Rr() * 1.6; return [Math.cos(a) * o, 0.6 + Rr() * 2.2, 0.8 + Rr() * 1.5]; },
        })).at(hit, age, 0.02);
        ctx.shake(Math.max(0, 0.6 - age * 2));
        // o alvo leva a mão no rosto e recua
        const rec = seq([[0, 0], [0.12, 1, 'out'], [0.8, 1], [1.2, 0]], age);
        R.lean(-0.25 * rec); R.place(0, RZ - 0.35 * rec, 0);
        R.face(rec);
      }
      return;
    }

    if (mode === 'cara') {
      const T = t % 4.4;
      const R = ctx.ref; const RZ = -2.4; R.place(0, RZ, 0);
      const go = seq([[0.5, 0], [0.9, 1, 'in']], T);
      const off = seq([[3.0, 0], [3.1, 1, 'snap']], T);                                                    // o tapa
      if (T < 3.0) {
        obj.position.y += 0.6 + 0.4 * go + 0.12 * bob * (1 - go);
        obj.position.z += -1.62 * go;
        flap(r, t, 1.2, T > 0.9 ? 26 : 16);
        if (T < 0.9) { legsIdle(r, t); r('cabeca', 0, 0.3 * tic, 0); }
        else {
          // agarrado: as pernas abraçam a cabeça, o abdômen pulsa, ele morde e treme
          const c = T - 0.9;
          const land = seq([[0, 1], [0.15, 0, 'out']], c);
          for (let i = 0; i < 3; i++) for (const sd of ['D', 'E']) {
            const sg = sd === 'D' ? 1 : -1;
            r('perna' + sd + i, -1.0 + 0.25 * i + 0.4 * land, 0, sg * 0.9);
            r('tarso' + sd + i, 1.1 + 0.1 * Math.sin(T * 20 + i), 0, 0);
          }
          r('abdomen', 0.25 * Math.sin(T * 12), 0, 0);
          r('boca', 0.3 + 0.3 * Math.abs(Math.sin(T * 13)), 0, 0);
          obj.position.x += 0.04 * Math.sin(T * 31); obj.position.y += 0.03 * Math.sin(T * 27);
          R.spin(0.3 * Math.sin(T * 3.4));
          R.lean(-0.2 - 0.1 * Math.sin(T * 6));
          // o alvo tenta arrancar: mãos na cara, puxando
          const grab = 0.6 + 0.4 * Math.sin(T * 7);
          R.face(grab, true);
          ctx.blind(0.55 * Math.min(1, c * 4));
          ctx.get('zumbido', () => DBFX.grains({
            n: 24, seed: 71, palette: [[40, 30, 40], [70, 60, 70]], size: [0.015, 0.03], drag: 3, grav: 0, life: 0.5, spread: 1,
            cone: Rr => { const a = Rr() * Math.PI * 2; return [Math.cos(a) * 0.8, (Rr() - 0.5) * 0.6, Math.sin(a) * 0.8]; },
          })).at(new THREE.Vector3(0, 1.75, RZ + 0.3), fr(T * 2) + 0.01);
        }
      } else {
        // tapa: voa girando e cai de costas, pernas pro alto, depois se vira e levanta voo
        const c = T - 3.0;
        const fall = FX.clamp01(c / 0.45);
        const lie = T > 3.45 && T < 4.05;
        const up = seq([[1.05, 0], [1.4, 1, 'out']], c);
        obj.position.x += -1.4 * FX.EASE.out(fall);
        obj.position.z += -1.6 + 0.6 * FX.EASE.out(fall);
        obj.position.y += (1.0 - 0.75) * (1 - fall) + 1.1 * Math.sin(Math.PI * fall) * 0.4 - 0.62 * fall * (1 - up) + 0.6 * up;
        obj.rotation.z = Math.PI * FX.EASE.out(fall) * (1 - up);
        obj.rotation.y = 3.0 * fall;
        if (lie) for (let i = 0; i < 3; i++) for (const sd of ['D', 'E']) r('tarso' + sd + i, 0.6 * Math.sin(T * 25 + i * 2), 0, 0);
        flap(r, t, lie ? 0.2 : 1, lie ? 4 : 18);
        r('boca', 0.5, 0, 0);
        R.punch(seq([[0, 1, 'snap'], [0.25, 1], [0.5, 0]], c));
        R.lean(-0.2 * (1 - fall));
      }
      return;
    }

    if (mode === 'morte') { const { fall } = JJFX.exorcize(t, obj, e, ctx, { h: 1.6, rotX: 0, back: 0.5, drop: 0.1, ashY: 0.5, n: 90 }); obj.position.y += 0.7 * (1 - fall) - 0.55 * fall; P.raiz.obj.rotation.z = 1.4 * fall; /* na peça 'raiz': o jogo gira a raiz do corpo num quadro espelhado */ if (fall < 0.05) flap(r, t); r('boca', 0.7 * fall, 0, 0);
      // nenhum membro escora o morto (teste R5)
      JJFX.floorLimbs(obj, P, ['corpo', 'cabeca', 'abdomen'], ['asaD', 'asaE', 'pernaD0', 'pernaD1', 'pernaD2', 'pernaE0', 'pernaE1', 'pernaE2'], fall); }
  }


  // morte de maldição: leva o golpe, cai e vira cinza preta, de cima pra baixo
  function exorcize(P, r, t, obj, e, ctx, h) {
    const T = t % 3.2;
    const R = ctx.ref; R.place(0, -1.6, 0);
    R.punch(seq([[0.15, 0], [0.25, 1, 'snap'], [0.6, 1], [0.9, 0]], T));
    const fall = seq([[0.25, 0], [0.75, 1, 'in']], T);
    obj.position.y += 0.6 * (1 - fall) - 0.5 * fall;
    obj.position.z += 0.5 * FX.EASE.out(FX.clamp01((T - 0.25) / 0.5));
    obj.rotation.z = 1.2 * fall; obj.rotation.x = -0.4 * fall;
    if (T < 0.25) flap(r, t);
    r('boca', 0.7 * fall, 0, 0);
    const dis = FX.clamp01((T - 0.85) / 1.5);
    JJFX.dissolve(e, dis, 0, h * 1.6);
    if (T > 0.85) ctx.get('cinza', () => JJFX.ash(9, 110)).at(new THREE.Vector3(0, 0.25, 0.4), T - 0.85, 0.02);
    if (T > 0.22 && T < 0.4) ctx.flash(new THREE.Vector3(0, 1, -0.6), 1 - (T - 0.22) / 0.18);
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(3), variant(12), variant(40)];
  const def = v => ({ def: rigDef(painters(v), v), low: rigLow(painters(v), v) });
  window.MOBS.push({
    id: 'mosca', nome: 'Mosca Amaldiçoada', bioma: 'Shibuya', tier: 'Grau 4 · tier 10',
    variants: fixed.map((v, i) => ({ nome: ['Pálida', 'Amarelada', 'Ciclope'][i], v: i === 2 ? Object.assign(v, { cyclops: true }) : v, ...def(i === 2 ? Object.assign(v, { cyclops: true }) : v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteada', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Voar'], ['mordida', 'Mordida'], ['cara', 'Grudar na Cara'], ['morte', 'Exorcizada']],
    cam: 3.6, lookY: 1.2, lookZ: { mordida: -1.2, cara: -1.4, morte: -0.6 }, camFar: { mordida: 5.2, cara: 5.2, morte: 5 }, camYaw: { mordida: -1.2, cara: -1.0, morte: -1.1 },
    info: {
      'Tamanho': 'Meio bloco de cabeça, voa na altura do seu rosto',
      'Vida': '6 (grau 4: um golpe e some)',
      'Jeito': 'Hostil e burra. Enxames de 3 a 6, zumbindo em volta de postes, lixo e gente com medo. Fica em qualquer bioma de Jujutsu, mais em Shibuya.',
      'Mordida': 'Recua no ar escancarando o beiço e dispara num estalo; arranca um pedaço (dano pequeno + sangramento curto).',
      'Grudar na Cara': 'Abraça sua cabeça com as 6 pernas: a tela escurece e você anda devagar até bater nela (qualquer golpe derruba). Em enxame, várias tentam grudar.',
      'Exorcizada': 'Toda maldição morta se desfaz em cinza preta, de cima pra baixo, sem deixar corpo.',
      'Solta': 'Resíduo de energia amaldiçoada (pouco); raro: asa de mosca (ingrediente).',
      'Variações': 'Cor da pele, veias, pra onde os olhos olham, tamanho; 1 em 5 nasce ciclope.',
    },
  });
})();
