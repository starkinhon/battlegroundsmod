// Inseto de Brasa (Terras do Jogo). Tier 10. Besouro do tamanho de um gato nascido do medo de vulcão: casco de
// rocha vulcânica rachada com magma correndo nas frestas, mandíbulas em brasa, abdômen que brilha por dentro igual
// carvão aceso. Quando voa, os élitros de pedra abrem e as asas de membrana fina ficam laranja contra a luz.
// Golpes: Picada (dá um rasante e morde: a mordida acende você) e Estouro (incha, fica branco de quente e se joga
// em você: explode em fogo de verdade, o inseto morre junto).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  // [nome, rocha, magma (quente), magma (morno)]
  const KINDS = [
    ['Brasa', [46, 36, 34], [255, 150, 40], [200, 50, 16]],
    ['Enxofre', [52, 48, 30], [250, 226, 70], [170, 140, 20]],
    ['Chama Azul', [30, 34, 46], [120, 210, 255], [40, 90, 220]],
  ];
  function variant(seed, kind) {
    const R = FX.rnd(seed);
    const k = kind ?? Math.floor(R() * KINDS.length);
    return { seed, kind: k, rock: KINDS[k][1], hot: KINDS[k][2], warm: KINDS[k][3], size: 0.92 + R() * 0.16, horn: R() < 0.5, spots: 3 + Math.floor(R() * 3) };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.7, hi = 1.18) => {
      const f = 0.6 - 0.3 * q.ny + 0.28 * (n3(q.ax * 0.5, q.ay * 0.5, q.az * 0.5, s + S) - 0.5) + (q.f === 'top' ? 0.22 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    // fresta de magma: onde dois ruídos se cruzam perto do meio vira uma rede de rachaduras
    const crack = (q, s, w = 0.028) => {
      const a = n3(q.ax * 0.55, q.ay * 0.55, q.az * 0.55, s + S), b = n3(q.ax * 1.3, q.ay * 1.3, q.az * 1.3, s + 7 + S);
      return Math.abs(a - 0.5) < w || (Math.abs(b - 0.5) < w * 0.6 && a > 0.45);
    };
    const P = {};
    P.shell = q => {
      let c = shade(v.rock, q, 1);
      if (n3(q.ax * 2, q.ay * 2, q.az * 2, 2 + S) > 0.72) c = mul(c, 1.25);   // grão da rocha
      if (crack(q, 3)) return n3(q.ax, q.ay, q.az, 4 + S) > 0.4 ? v.hot : v.warm;
      return c;
    };
    // élitros: rocha lisa com sulcos longitudinais e a rede de magma
    P.elytra = q => {
      let c = shade(mul(v.rock, 1.1), q, 5);
      if (q.f === 'top' && fr(q.ax * 0.7) < 0.12) c = mul(c, 0.72);
      if (crack(q, 6, 0.032)) return n3(q.ax, q.ay, q.az, 7 + S) > 0.45 ? v.hot : v.warm;
      return c;
    };
    // abdômen: anéis de carvão com o miolo brilhando entre eles, mais quente embaixo
    P.abd = q => {
      const ring = fr(q.az * 0.55);
      if (ring < 0.3) return shade(mul(v.rock, 0.8), q, 8);
      const k = 0.5 + 0.5 * n3(q.ax * 1.5, q.ay, q.az * 1.5, 9 + S);
      return mix(v.warm, v.hot, k * (q.f === 'bottom' ? 1 : 0.7));
    };
    P.head = q => {
      let c = shade(mul(v.rock, 0.92), q, 10);
      if (q.f === 'north') {
        const x = q.px - q.b.w / 2, y = q.py;
        // olhos compostos: dois blocos de facetas brilhando nos cantos
        for (const ex of [-1.6, 1.6]) {
          const dx = x - ex, dy = y - 1.3;
          if (Math.abs(dx) < 0.9 && Math.abs(dy) < 0.8) return (Math.floor(q.px * 2) + Math.floor(q.py * 2)) % 2 ? v.hot : mix(v.hot, [255, 255, 230], 0.6);
        }
      }
      if (crack(q, 11, 0.035)) return v.warm;
      return c;
    };
    P.mand = q => { let c = shade(mul(v.rock, 0.85), q, 12); if (q.nz < 0.45) c = mix(v.hot, [255, 250, 220], 0.3 * (1 - q.nz * 2)); return c; };
    P.leg = q => { let c = shade(mul(v.rock, 0.75), q, 13); if (fr(q.ay * 0.5) < 0.12) c = v.warm; return c; };
    // asa de membrana: veias escuras, luz de fogo atravessando; forma de gota (recortada em todas as faces)
    const wing = (q, u) => {
      const w = q.nz;
      const half = 0.5 * Math.sin(Math.PI * Math.min(1, u * 1.05)) * (0.6 + 0.4 * (1 - u));
      if (Math.abs(w - 0.5) > half) return null;
      const vein = fr(u * 5 + w * 1.2) < 0.08 || Math.abs(w - 0.5) < 0.03 || fr(w * 4 - u * 2) < 0.05;
      if (vein) return mul(v.rock, 1.2);
      return mix(mix(v.hot, [255, 240, 200], 0.4), v.warm, u * 0.6);
    };
    P.wingD = q => wing(q, 1 - q.nx);
    P.wingE = q => wing(q, q.nx);
    P.horn = q => { let c = shade(mul(v.rock, 1.05), q, 14); if (q.ny < 0.3) c = mix(c, v.hot, 0.6); return c; };
    return P;
  }

  function rig(P, v, lo) {
    const legs = [];
    for (let i = 0; i < 3; i++) for (const s of [-1, 1]) {
      const n = (s < 0 ? 'D' : 'E') + i;
      legs.push({
        name: 'perna' + n, pivot: [s * 2.6, 1.0, -2.6 + i * 2.8], rot: [(i - 1) * 0.35, 0, -s * 1.25],
        boxes: [{ x: -0.5, y: 0, z: -0.5, w: 1, h: 3.4, d: 1, p: P.leg }],
        kids: lo ? [] : [{ name: 'tarso' + n, pivot: [0, 3.2, 0], rot: [0, 0, s * 1.15], boxes: [{ x: -0.4, y: 0, z: -0.4, w: 0.8, h: 3.2, d: 0.8, p: P.leg }] }],
      });
    }
    const elytron = s => ({
      name: 'elitro' + (s < 0 ? 'D' : 'E'), pivot: [s * 0.1, -2.6, -1.8], rot: [0, 0, 0],
      boxes: [{ x: s < 0 ? -3.3 : 0, y: -0.6, z: 0, w: 3.3, h: 3.4, d: 8.2, p: P.elytra }],
    });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'corpo', pivot: [0, 19.2, 0], boxes: [
          // tórax (pronoto largo) e a barriga
          { x: -3.2, y: -2.6, z: -4.4, w: 6.4, h: 4.4, d: 4, p: P.shell },
          { x: -2.8, y: -1.4, z: -1.2, w: 5.6, h: 3.4, d: 7.6, p: P.abd },
          { x: -2, y: -0.8, z: 6.2, w: 4, h: 2.4, d: 1.8, p: P.abd },
        ],
        kids: [
          {
            name: 'cabeca', pivot: [0, -0.6, -4.4], boxes: [
              { x: -2.6, y: -1.4, z: -3.4, w: 5.2, h: 3.4, d: 3.4, p: P.head },
              ...(v.horn && !lo ? [{ x: -0.6, y: -3.8, z: -3, w: 1.2, h: 2.6, d: 1.2, p: P.horn }, { x: -0.45, y: -4.8, z: -4.0, w: 0.9, h: 1.4, d: 1.4, p: P.horn }] : []),
            ],
            kids: [
              { name: 'mandD', pivot: [-1.6, 1.0, -3.2], rot: [0, 0.35, 0], boxes: [{ x: -0.6, y: -0.5, z: -3, w: 1.2, h: 1.1, d: 3, p: P.mand }, ...(lo ? [] : [{ x: -0.1, y: -0.4, z: -3.2, w: 1.2, h: 0.9, d: 0.9, p: P.mand }])] },
              { name: 'mandE', pivot: [1.6, 1.0, -3.2], rot: [0, -0.35, 0], boxes: [{ x: -0.6, y: -0.5, z: -3, w: 1.2, h: 1.1, d: 3, p: P.mand }, ...(lo ? [] : [{ x: -1.1, y: -0.4, z: -3.2, w: 1.2, h: 0.9, d: 0.9, p: P.mand }])] },
              ...(lo ? [] : [-1, 1].map(s => ({ name: 'antena' + (s < 0 ? 'D' : 'E'), pivot: [s * 1.6, -1.2, -3.2], rot: [-0.7, 0, s * 0.5], boxes: [{ x: -0.25, y: -4, z: -0.25, w: 0.5, h: 4, d: 0.5, p: P.leg }, { x: -0.45, y: -4.8, z: -0.45, w: 0.9, h: 1, d: 0.9, p: P.mand }] }))),
            ],
          },
          elytron(-1), elytron(1),
          { name: 'asaD', pivot: [-0.8, -2.6, -0.6], rot: [0.05, 0.3, 0.15], boxes: [{ x: -10, y: 0, z: -1.6, w: 10, h: 0, d: 4.6, p: P.wingD }] },
          { name: 'asaE', pivot: [0.8, -2.6, -0.6], rot: [0.05, -0.3, -0.15], boxes: [{ x: 0, y: 0, z: -1.6, w: 10, h: 0, d: 4.6, p: P.wingE }] },
          ...legs,
        ],
      }],
    };
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const C = e.v.hot.map(c => c / 255);
    const W = (n, x, y, z) => { obj.updateMatrixWorld(true); return new THREE.Vector3(x, y, z).applyMatrix4(P[n].obj.matrixWorld); };
    const lift = k => obj.position.y += k;                      // altura do voo em blocos
    const tilt = (x, z = 0) => { P.corpo.obj.rotation.x += x; P.corpo.obj.rotation.z += z; };
    // voando: élitros abertos pra cima e pros lados, asas batendo rápido
    const fly = (k = 1, sp = 18) => {
      r('elitroD', -0.5 * k, 0.15 * k, -0.9 * k); r('elitroE', -0.5 * k, -0.15 * k, 0.9 * k);
      const w = loop([[0, -0.5], [0.45, 0.8], [0.55, 0.8]], t * sp) * k;
      r('asaD', 0, 0, -w); r('asaE', 0, 0, w);
      for (let i = 0; i < 3; i++) { r('pernaD' + i, 0.4 * k, 0, 0.5 * k); r('pernaE' + i, 0.4 * k, 0, -0.5 * k); }
    };
    const fold = () => { for (const n of ['asaD', 'asaE']) P[n].obj.scale.set(0.001, 0.001, 0.001); };   // asas guardadas embaixo dos élitros
    const mand = k => { r('mandD', 0, -0.6 * k, 0); r('mandE', 0, 0.6 * k, 0); };
    const belly = (k, rad = 0.2) => ctx.get('brilho', () => DBFX.glow(C)).at(W('corpo', 0, 1.2, 2.2), rad, k, t, 0.6);
    const embers = (key, at, age, n = 18) => ctx.get(key, () => DBFX.grains({ n, seed: key.length * 7 + 3, add: true, palette: [e.v.hot, mix(e.v.hot, [255, 255, 230], 0.5), e.v.warm], size: [0.015, 0.035], drag: 1.6, grav: -0.6, life: 1.1, spread: 1.0, cone: R2 => [(R2() - 0.5) * 0.5, 0.2 + R2() * 0.5, (R2() - 0.5) * 0.5] })).at(at, age, -9);

    if (mode === 'parado') {
      // no chão, igual besouro: as patas mexem uma de cada vez, antenas tateando, o abdômen "respira" brasa
      for (let i = 0; i < 3; i++) for (const sd of ['D', 'E']) { const ph = t * 1.5 + i * 0.33 + (sd === 'D' ? 0 : 0.5); r('perna' + sd + i, 0.15 * loop([[0, -1], [0.5, 1]], ph), 0, 0); }
      r('antenaD', 0.3 * Math.sin(t * 2.1), 0.2 * Math.sin(t * 1.3), 0); r('antenaE', 0.3 * Math.sin(t * 1.8 + 1), -0.2 * Math.sin(t * 1.1), 0);
      mand(0.25 * Math.max(0, Math.sin(t * 3)) * (fr(t / 2.6) < 0.4 ? 1 : 0));
      const br = 0.6 + 0.4 * Math.sin(t * 1.7) ** 2;
      // de vez em quando abre os élitros num estalo, sacode as asas e fecha
      const peek = seq([[0, 0], [0.04, 1, 'snap'], [0.3, 1], [0.4, 0, 'io']], fr(t / 4.2) * 2.4);
      if (peek > 0.05) { r('elitroD', -0.3 * peek, 0, -0.5 * peek); r('elitroE', -0.3 * peek, 0, 0.5 * peek); const w = 0.4 * Math.sin(t * 60) * peek; r('asaD', 0, 0, -w); r('asaE', 0, 0, w); for (const n of ['asaD', 'asaE']) P[n].obj.scale.set(peek, 1, peek); } else fold();
      belly(br * 0.8);
      embers('fagulha', W('corpo', 0, -1, 3), fr(t / 1.4) * 1.4, 10);
      return;
    }
    if (mode === 'andar') {
      // voa em zigue-zague de besouro pesado: sobe, perde altura, corrige; deixa rastro de fagulha
      lift(1.05 + 0.12 * Math.sin(t * 2.3));
      fly(1);
      obj.position.x += 0.35 * Math.sin(t * 1.1); tilt(-0.25 + 0.08 * Math.sin(t * 2.3), -0.25 * Math.cos(t * 1.1));
      belly(1);
      embers('rastro', W('corpo', 0, 0, 5), fr(t / 1.2) * 1.2, 24);
      return;
    }
    if (mode === 'picada') {
      const T = t % 3.2;
      const R = ctx.ref; const RZ = -1.7; R.place(0, RZ, 0); R.spin(Math.PI);
      // paira, recua no ar abrindo a mandíbula, dá o rasante no braço/peito e volta; onde mordeu fica pegando fogo
      const back = seq([[0.2, 0], [0.8, 1, 'io'], [1.0, 1]], T);
      const dash = seq([[1.0, 0], [1.16, 1, 'snap'], [1.5, 1], [2.1, 0, 'io']], T);
      lift(1.15 + 0.12 * Math.sin(t * 2) + 0.1 * back - 0.05 * dash);
      obj.position.z += 0.5 * back - 0.95 * dash;
      fly(1, dash > 0.1 ? 26 : 18);
      tilt(0.2 * back - 0.5 * dash);
      mand(seq([[0.4, 0], [0.9, 1.2], [1.18, 1.2], [1.24, -0.4, 'snap'], [1.5, -0.4], [1.8, 0]], T));
      const bite = seq([[1.18, 0], [1.24, 1, 'snap'], [1.6, 0.6], [2.4, 0]], T);
      R.punch(bite * 0.6); R.lean(0.12 * bite);
      if (T > 1.2 && T < 1.4) ctx.flash(W('cabeca', 0, 0, -6), 0.6 * (1 - (T - 1.2) / 0.2));
      // fogo na roupa: chama de volume pequena no peito + brasas soltas, e o brilho do ember do motor
      const burn = seq([[1.22, 0], [1.4, 1, 'out'], [2.8, 0.8], [3.15, 0]], T);
      if (burn > 0.01) {
        const at = new THREE.Vector3(0.05, 1.2, RZ + 0.2);
        ctx.get('fogoRef', FX.volume).set(at, 0.32 + 0.06 * burn, FX.clamp01(0.08 + 0.16 * fr(t * 2.2)), { fire: 1.4 * burn, seed: 5.1 + Math.floor(t * 2.2), dens: 9, rise: 0.9, squash: 1.4, smoke: [0.25, 0.22, 0.22] });
        ctx.ember(at.clone().add(new THREE.Vector3(0, 0.05, 0.06)), t);
        embers('fagRef', at, (T - 1.22) % 1, 14);
      }
      belly(1 + 0.4 * dash);
      return;
    }
    if (mode === 'estouro') {
      const T = t % 4.4;
      const R = ctx.ref; const RZ = -2.0; R.place(0, RZ, 0); R.spin(Math.PI);
      // fica parado no ar, o abdômen incha e clareia até ficar quase branco (o aviso), treme, e se joga:
      // explosão de fogo de verdade (volume) com fagulhas e um anel de calor no chão; ele some na explosão
      const swell = seq([[0.2, 0], [1.6, 1, 'in'], [1.9, 1.08]], T);
      const dive = seq([[1.9, 0], [2.12, 1, 'in']], T);
      const boom = T > 2.12;
      lift(1.25 + 0.05 * Math.sin(t * 3) - 0.3 * dive);
      obj.position.z += -2.0 * dive;
      fly(1, 18 + 14 * swell);
      if (swell > 0.4) { obj.position.x += 0.02 * Math.sin(t * 70) * swell; }
      tilt(-0.15 * swell - 0.6 * dive);
      P.corpo.obj.scale.set(1 + 0.18 * swell, 1 + 0.3 * swell, 1 + 0.12 * swell);
      if (!boom) {
        ctx.get('brilho', () => DBFX.glow(C)).at(W('corpo', 0, 1.2, 2.2), 0.2 + 0.18 * swell, 1 + 1.6 * swell, t, 0.7);
        ctx.get('branco', () => DBFX.glow([1, 0.96, 0.85])).at(W('corpo', 0, 1.2, 2.2), 0.12 + 0.1 * swell, swell * swell * (0.7 + 0.3 * Math.sin(t * 30)), t, 0.8);
        ctx.get('luz', () => DBFX.light(new THREE.Color(...C))).at(W('corpo', 0, 1, 2), 1.5 + 4 * swell, 4);
        embers('fagulha', W('corpo', 0, 0, 3), fr(t / 0.9) * 0.9, 20);
      } else {
        obj.visible = false;
        const age = (T - 2.12) / 2.0;
        const at = new THREE.Vector3(0, 1.0, RZ + 0.25);
        ctx.get('explosao', FX.volume).set(at, 1.15, Math.min(0.999, age), { fire: 1.6, seed: 8.4 + e.v.kind, dens: 9, rise: 0.8, squash: 0.95, smoke: [0.2, 0.18, 0.18] });
        ctx.get('fagBoom', () => DBFX.grains({ n: 90, seed: 77, add: true, palette: [e.v.hot, mix(e.v.hot, [255, 255, 230], 0.6), e.v.warm], size: [0.02, 0.06], drag: 1.4, grav: 5, life: 1.4, cone: R2 => { const a2 = R2() * 6.28, el = R2() * 1.3; return [Math.cos(a2) * Math.cos(el) * (3 + R2() * 4), Math.sin(el) * (3 + R2() * 4), Math.sin(a2) * Math.cos(el) * (3 + R2() * 4)]; } })).at(at, T - 2.12, 0.02);
        ctx.get('cacos', () => DBFX.grains({ n: 26, seed: 78, palette: [e.v.rock, mul(e.v.rock, 1.4), e.v.warm], size: [0.04, 0.08], drag: 0.6, grav: 9.8, life: 1.6, cone: R2 => { const a2 = R2() * 6.28; return [Math.cos(a2) * (1.5 + R2() * 3), 2 + R2() * 4, Math.sin(a2) * (1.5 + R2() * 3)]; } })).at(at, T - 2.12, 0.02);
        ctx.get('chao', JJFX.magma).at(new THREE.Vector3(0, 0.015, RZ + 0.2), 0.9, FX.EASE.out(FX.clamp01(age * 6)), 1 - FX.clamp01((age - 0.75) / 0.25), t, 1 - age, 3);
        ctx.roarRing(new THREE.Vector3(0, 0.03, RZ + 0.2), Math.min(0.999, age * 2.2));
        if (T < 2.4) { ctx.flash(at, 1.6 * (1 - (T - 2.12) / 0.28)); ctx.shake(1 - (T - 2.12) / 0.28); }
        const hit = seq([[2.12, 0], [2.2, 1, 'snap'], [3.6, 0.6], [4.3, 0]], T);
        R.place(0, RZ + 0.5 * FX.EASE.out(FX.clamp01((T - 2.12) / 0.4)), 0); R.lean(-0.35 * hit); R.armsOut(0.3 * hit);
        if (T < 3.8) ctx.get('fogoRef', FX.volume).set(new THREE.Vector3(0.05, 1.15, RZ + 0.4), 0.24, FX.clamp01(0.08 + 0.16 * fr(t * 2.2)), { fire: 1.3, seed: 9.1 + Math.floor(t * 2.2), dens: 9, rise: 0.9, squash: 1.4, smoke: [0.25, 0.22, 0.22] });
      }
      return;
    }
    if (mode === 'morte') {
      // leva o golpe no ar, cai de costas com as patas mexendo, a brasa apaga (magma vira rocha cinza) e vira cinza
      const T = t % 3.6;
      const R = ctx.ref; R.place(0, -1.7, 0); R.spin(Math.PI);
      R.punch(seq([[0.15, 0], [0.25, 1, 'snap'], [0.6, 1], [0.9, 0]], T));
      const fall = seq([[0.25, 0], [0.7, 1, 'in']], T);
      lift(1.1 * (1 - fall) + 0.1 * fall);
      obj.position.z += 0.4 * FX.EASE.out(FX.clamp01((T - 0.25) / 0.45));
      P.corpo.obj.rotation.z += Math.PI * fall;
      if (fall < 0.1) fly(1); else { fold(); for (let i = 0; i < 3; i++) for (const sd of ['D', 'E']) r('perna' + sd + i, 0.5 * Math.sin(t * 14 + i) * Math.max(0, 1 - (T - 0.7)), 0, 0); }
      const cool = FX.clamp01((T - 0.7) / 0.8);
      belly(1 - cool);
      ctx.get('fumaca', FX.volume).set(new THREE.Vector3(0, 0.3, 0.4), 0.35, FX.clamp01((T - 0.8) / 2.2), { seed: 2.2, dens: 4, rise: 1.2, squash: 1.2, smoke: [0.4, 0.38, 0.38] });
      const dis = FX.clamp01((T - 1.5) / 1.5);
      JJFX.dissolve(e, dis, 0, 1.4);
      if (T > 1.5) ctx.get('cinza', () => JJFX.ash(13, 80)).at(new THREE.Vector3(0, 0.15, 0.4), T - 1.5, 0.02);
      if (T > 0.22 && T < 0.4) ctx.flash(new THREE.Vector3(0, 1, -0.6), 1 - (T - 0.22) / 0.18);
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(4, 0), variant(21, 1), variant(66, 2)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'inseto', nome: 'Inseto de Brasa', bioma: 'Terras do Jogo', tier: 'Grau 4 · tier 10',
    variants: fixed.map(v => ({ nome: KINDS[v.kind][0], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado (' + KINDS[v.kind][0] + ')', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Voar'], ['picada', 'Picada'], ['estouro', 'Estouro'], ['morte', 'Exorcizado']],
    cam: 3.4, lookY: 0.7, lookZ: { picada: -0.9, estouro: -1.0, morte: -0.4 }, camFar: { picada: 5.0, estouro: 6.4, morte: 4.6 }, camYaw: { picada: -1.2, estouro: -1.1, morte: -1.1 },
    info: {
      'Tamanho': 'Do tamanho de um gato (meio bloco); voa na altura do peito',
      'Vida': '8 (grau 4: um golpe e some)',
      'Jeito': 'Bandos de 3 a 5 rondando poças de lava e fendas. Parado parece pedra quente; quando alguém chega perto, abre os élitros e levanta voo de uma vez.',
      'Picada': 'Recua no ar com a mandíbula aberta e dá um rasante: mordida fraca, mas te põe fogo (2 s queimando).',
      'Estouro': 'Fica parado no ar e o abdômen incha e clareia até ficar branco (2 s de aviso). Depois se joga em você e explode: fogo em área de 2 blocos e deixa magma no chão por uns segundos. Ele morre junto. Acertar ele inchado faz explodir onde está.',
      'Fraqueza': 'Água e neve apagam a brasa: vira pedra e cai (morre na hora).',
      'Solta': 'Brasa Amaldiçoada (acende fornalha por muito tempo), Casco de Rocha; raro: Élitro de Magma.',
      'Variações': 'Brasa (laranja), Enxofre (amarelo) ou Chama Azul; com ou sem chifre; rede de rachaduras diferente em cada um.',
    },
  });
})();
