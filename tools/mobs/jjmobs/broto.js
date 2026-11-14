// Broto Andante (Hanami). Tier 9. Maldição nascida do medo de mato que cresce sozinho: um bulbo do tamanho de uma
// abóbora andando em quatro raízes, um olho só no meio do bulbo, uma boca de sementes embaixo dele e um broto com
// duas folhas e um botão de flor em cima. Corre de lado igual bicho pequeno.
// Golpes: Cuspe de Sementes (cospe três sementes em volta de você; cada uma brota um cipó que agarra seu pé) e
// Enraizar (afunda na terra, anda por baixo levantando o chão e brota embaixo de você, te jogando pra cima).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  // [nome, bulbo, folha, botão, olho]
  const KINDS = [
    ['Broto Verde', [176, 196, 120], [76, 150, 60], [236, 220, 90], [200, 40, 40]],
    ['Broto de Cerejeira', [214, 190, 170], [96, 150, 70], [244, 160, 190], [60, 140, 60]],
    ['Broto Seco', [170, 140, 96], [130, 120, 60], [200, 120, 60], [240, 200, 60]],
  ];
  function variant(seed, kind) {
    const R = FX.rnd(seed);
    const k = kind ?? Math.floor(R() * KINDS.length);
    return { seed, kind: k, bulb: KINDS[k][1], leaf: KINDS[k][2], bud: KINDS[k][3], iris: KINDS[k][4], size: 0.9 + R() * 0.2, leafTilt: (R() - 0.5) * 0.4, three: R() < 0.4 };
  }

  const EYE = [
    'oWWWWWo',
    'WWiiiWW',
    'WiPPhiW',
    'WWiPiWW',
    'oWWWWWo',
  ];
  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.74, hi = 1.18) => {
      const f = 0.62 - 0.32 * q.ny + 0.3 * (n3(q.ax * 0.35, q.ay * 0.35, q.az * 0.35, s + S) - 0.5) + (q.f === 'top' ? 0.22 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const P = {};
    // bulbo: gomos verticais de cebola (listras claras/escuras), raiz fina por baixo, terra grudada na base
    P.bulb = q => {
      let c = shade(v.bulb, q, 1);
      const u = q.f === 'north' || q.f === 'south' ? q.nx : q.nz;
      if (q.f !== 'top' && q.f !== 'bottom' && fr(u * 3.5) < 0.12) c = mul(c, 0.82);
      if (q.f !== 'top' && q.f !== 'bottom' && fr(u * 3.5 + 0.5) < 0.08) c = mul(c, 1.1);
      if (q.ny > 0.8 && n3(q.ax, q.ay, q.az, 2 + S) > 0.5) c = mix(c, [90, 66, 44], 0.6);
      if (q.f === 'bottom') c = [90, 66, 44];
      return c;
    };
    // frente do bulbo: olho grande com veias verdes; boca de sementes embaixo
    P.face = q => {
      if (q.f !== 'north') return P.bulb(q);
      // olho em bitmap 7x5 (o bulbo é pequeno: pixel a pixel lê melhor que círculo)
      const col = Math.min(6, Math.floor(q.px * 7 / q.b.w)), row = Math.min(4, Math.floor(q.py * 5 / q.b.h));
      const ch = EYE[row][col];
      if (ch === 'W') return n3(q.px * 2, q.py * 2, 0, 3 + S) > 0.72 ? [150, 190, 110] : [238, 234, 214];
      if (ch === 'i') return v.iris;
      if (ch === 'P') return [12, 10, 10];
      if (ch === 'h') return [255, 255, 255];
      if (ch === 'o') return mul(v.bulb, 0.55);
      return P.bulb(q);
    };
    P.lip = q => { let c = shade(mul(v.bulb, 0.85), q, 4); if (q.f === 'top') { c = [70, 40, 30]; if (fr(q.px * 0.9) < 0.4 && q.nz < 0.5) c = [236, 220, 170]; } return c; };   // sementes no lugar dos dentes
    P.stem = q => { let c = shade(mix(v.leaf, [180, 200, 120], 0.3), q, 5); if (fr(q.ay * 0.8) < 0.15) c = mul(c, 0.85); return c; };
    // folha: forma de gota, nervura no meio e nervuras laterais (fora da forma = vazio)
    P.leaf = q => {
      const along = q.b.x < 0 ? 1 - q.nx : q.nx, w = Math.abs(q.nz - 0.5) * 2;
      const lim = Math.sin(Math.PI * Math.min(1, along * 1.05)) * (0.4 + 0.6 * (1 - along));
      if (w > lim + 0.05) return null;
      let c = shade(v.leaf, q, 6, 0.78, 1.18);
      if (q.f === 'bottom') c = mul(c, 1.3);
      if (w < 0.1) c = mix(c, [210, 220, 150], 0.5);
      else if (fr(along * 5 - w * 1.2) < 0.12) c = mul(c, 0.86);
      if (n3(q.ax * 1.4, 0, q.az * 1.4, 7 + S) > 0.76) c = mix(c, [150, 120, 50], 0.4);                                            // mordida de bicho
      return c;
    };
    P.bud = q => { let c = shade(v.bud, q, 8, 0.8, 1.15); if (q.ny > 0.6) c = mix(c, v.leaf, 0.7); if (fr(q.nx * 3 + q.nz * 3) < 0.15) c = mul(c, 0.85); return c; };
    P.petal = q => { let c = shade(mix(v.bud, [255, 255, 255], 0.2), q, 9, 0.84, 1.12); if (q.ny > 0.7) c = mul(c, 0.86); return c; };
    P.root = q => { let c = shade([150, 116, 80], q, 10); if (fr(q.ay * 0.9) < 0.2) c = mul(c, 0.82); if (q.ny > 0.85) c = [96, 72, 50]; return c; };
    return P;
  }

  function rig(P, v, lo) {
    // raiz-perna: sai do canto do bulbo pra fora e desce; ponta com cabelinhos
    const leg = (sx, sz) => {
      const n = (sx < 0 ? 'D' : 'E') + (sz < 0 ? 'F' : 'T');
      return {
        name: 'raiz' + n, pivot: [sx * 2.4, 18.8, sz * 1.9], rot: [sz * 0.35, 0, -sx * 0.55], boxes: [{ x: -0.6, y: 0, z: -0.6, w: 1.2, h: 3.4, d: 1.2, p: P.root }],
        kids: [{ name: 'ponta' + n, pivot: [0, 3.4, 0], rot: [-sz * 0.35, 0, sx * 0.55], boxes: [{ x: -0.5, y: 0, z: -0.5, w: 1, h: 2.4, d: 1, p: P.root }, ...(lo ? [] : [{ x: -0.9, y: 2.0, z: -0.15, w: 1.8, h: 0.4, d: 0.3, p: P.root }])] }],
      };
    };
    const leaf = s => ({
      name: s < 0 ? 'folhaD' : 'folhaE', pivot: [0, -3.4, 0], rot: [0, s * 0.3, -s * (0.45 + v.leafTilt)], boxes: [{ x: s > 0 ? 0 : -7.6, y: -0.2, z: -2.2, w: 7.6, h: 0.4, d: 4.4, p: P.leaf }],
    });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'bulbo', pivot: [0, 19.0, 0], boxes: [
            { x: -3.6, y: -7, z: -3.6, w: 7.2, h: 7, d: 7.2, p: P.bulb },
            { x: -3.6, y: -6.2, z: -3.7, w: 7.2, h: 5.2, d: 0.2, p: P.face },
            { x: -2.6, y: -8.6, z: -2.6, w: 5.2, h: 1.6, d: 5.2, p: P.bulb },
            { x: -1.5, y: -9.6, z: -1.5, w: 3, h: 1, d: 3, p: P.bulb },
            { x: -2.6, y: -1.6, z: -3.85, w: 5.2, h: 1.2, d: 0.2, p: () => [50, 28, 24] },
          ],
          kids: [
            { name: 'boca', pivot: [0, -0.6, -2.8], boxes: [{ x: -2.8, y: -0.2, z: -1.2, w: 5.6, h: 1.2, d: 2.2, p: P.lip }] },
            {
              name: 'caule', pivot: [0, -9.6, 0], boxes: [{ x: -0.6, y: -4.6, z: -0.6, w: 1.2, h: 4.6, d: 1.2, p: P.stem }],
              kids: [
                leaf(-1), leaf(1),
                ...(v.three && !lo ? [{ name: 'folhaT', pivot: [0, -2.2, 0], rot: [0.6, 0, 0], boxes: [{ x: -1.4, y: -0.2, z: 0, w: 2.8, h: 0.4, d: 4.2, p: P.leaf }] }] : []),
                {
                  name: 'botao', pivot: [0, -4.6, 0], boxes: [{ x: -1.2, y: -2.4, z: -1.2, w: 2.4, h: 2.4, d: 2.4, p: P.bud }],
                  kids: lo ? [] : [0, 1, 2, 3].map(i => ({ name: 'petala' + i, pivot: [0, -1.6, 0], rot: [0, i * Math.PI / 2, 0], boxes: [{ x: -0.9, y: -0.2, z: -0.4, w: 1.8, h: 0.4, d: 2.2, p: P.petal }] })),
                },
              ],
            },
          ],
        },
        leg(-1, -1), leg(1, -1), leg(-1, 1), leg(1, 1),
      ],
    };
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const bloom = k => { for (let i = 0; i < 4; i++) r('petala' + i, -1.2 * k, 0, 0); if (P.botao) { const b = 1 + 0.25 * k; P.botao.obj.scale.set(b, 1, b); } };
    bloom(0);
    const legs = (ph, amp) => {
      [['DF', 0], ['ET', 0], ['EF', Math.PI], ['DT', Math.PI]].forEach(([n, o]) => {
        const l = Math.max(0, Math.sin(ph + o));
        r('raiz' + n, 0.35 * Math.cos(ph + o) * amp, 0, 0); r('ponta' + n, -0.5 * l * amp, 0, 0);
      });
    };
    const leaves = (k = 1, sp = 1) => { r('folhaD', 0, 0, 0.18 * Math.sin(t * 2.2 * sp) * k); r('folhaE', 0, 0, -0.18 * Math.sin(t * 2.2 * sp + 0.8) * k); r('folhaT', 0.12 * Math.sin(t * 1.7 * sp), 0, 0); };
    const mouth = k => r('boca', 0.8 * k, 0, 0);
    const W = (n, x, y, z) => { obj.updateMatrixWorld(true); return new THREE.Vector3(x, y, z).applyMatrix4(P[n].obj.matrixWorld); };

    if (mode === 'parado') {
      // quietinho, o bulbo respira, o olho procura (o bulbo inteiro gira devagar), folhas balançam com vento
      const br = 1 + 0.03 * Math.sin(t * 2); if (P.bulbo) P.bulbo.obj.scale.set(br, 1 / br, br);
      r('bulbo', 0.05 * Math.sin(t * 0.8), 0.4 * loop([[0, 0], [0.25, 0.5], [0.45, 0.5], [0.65, -0.5], [0.85, -0.5], [1, 0]], t / 7), 0);
      r('caule', 0.08 * Math.sin(t * 1.3), 0, 0.06 * Math.sin(t * 0.9));
      leaves(1); legs(t * 0.8, 0.12); mouth(0.1 * Math.max(0, Math.sin(t * 3)));
      return;
    }
    if (mode === 'andar') {
      // corre miudinho nas quatro raízes, o bulbo quica e o broto chicoteia atrasado
      const ph = t * 14;
      legs(ph, 1); obj.position.y += 0.04 * Math.abs(Math.sin(ph));
      r('bulbo', 0.08, 0, 0.05 * Math.sin(ph / 2)); r('caule', -0.25 - 0.1 * Math.sin(ph), 0, 0.1 * Math.sin(ph / 2));
      leaves(1.6, 3); obj.position.z -= 0.6 * Math.sin(t * 1.1);
      return;
    }
    if (mode === 'sementes') {
      const T = t % 4.8;
      const R = ctx.ref; const RZ = -2.6; R.place(0, RZ, 0); R.spin(Math.PI);
      // enche (bulbo incha, flor fecha), cospe três sementes em arco; cada uma cai, racha e solta um cipó que sobe e
      // enrola no pé do alvo
      const fill = seq([[0.2, 0], [0.9, 1, 'io'], [1.0, 0, 'snap']], T);
      if (P.bulbo) P.bulbo.obj.scale.set(1 + 0.12 * fill, 1 - 0.06 * fill, 1 + 0.12 * fill);
      r('bulbo', -0.3 * fill + 0.25 * seq([[0.9, 0], [1.05, 1, 'snap'], [1.6, 0]], T), 0, 0);
      r('caule', 0.3 * fill, 0, 0);
      for (const t0 of [1.0, 1.25, 1.5]) { const k = T - t0; if (k > 0 && k < 0.18) { mouth(1); r('bulbo', 0.15, 0, 0); } }
      if (T > 0.95 && T < 1.7) mouth(0.6);
      const aims = [new THREE.Vector3(-0.28, 0, RZ - 0.05), new THREE.Vector3(0.3, 0, RZ + 0.08), new THREE.Vector3(0.02, 0, RZ - 0.32)];
      aims.forEach((to, i) => {
        const t0 = 1.0 + i * 0.25, f = FX.clamp01((T - t0) / 0.5);
        const seed = ctx.get('semente' + i, seedMesh);
        if (T < t0 || T > 4.6) { seed.visible = false; return; }
        const from = W('boca', 0, 0, -1.6);
        seed.visible = true; seed.position.copy(from).lerp(to, f); seed.position.y += 0.7 * Math.sin(Math.PI * f) + 0.04;
        seed.rotation.set(T * 10 + i, T * 7, 0);
        if (f >= 1) { seed.rotation.set(0, i, 0.3); seed.scale.setScalar(1 - FX.clamp01((T - t0 - 0.5) / 0.2)); } else seed.scale.setScalar(1);
        // cipó: sai da semente, sobe e enrola na canela do alvo
        const g = FX.clamp01((T - t0 - 0.55) / 0.5) * (1 - FX.clamp01((T - 4.0) / 0.5));
        const vine = ctx.get('cipo' + i, () => JJFX.tube(0x4e8a3a, 0.05, 0.022));
        const ankle = new THREE.Vector3((i - 1) * 0.06, 0.25 + 0.12 * i, RZ);
        vine.at(to.clone().add(new THREE.Vector3(0, 0.02, 0)), ankle, g, { arc: 0.25, wiggle: 0.1, t: T + i });
        if (f >= 1 && T - t0 - 0.5 < 0.25) ctx.get('terra' + i, () => DBFX.grains({ n: 10, seed: 70 + i, palette: [[90, 66, 44], [60, 44, 30], [120, 160, 70]], size: [0.02, 0.04], drag: 3, grav: 6, life: 0.4, cone: R2 => [(R2() - 0.5) * 1.2, R2() * 1.2, (R2() - 0.5) * 1.2] })).at(to, T - t0 - 0.5, 0);
      });
      const caught = FX.clamp01((T - 1.9) / 0.4) * (1 - FX.clamp01((T - 4.0) / 0.4));
      R.armsOut(0.5 * caught); R.lean(0.1 * caught * Math.sin(T * 5)); R.spin(Math.PI + 0.15 * caught * Math.sin(T * 3));
      leaves(1); legs(0, 0);
      bloom(seq([[1.6, 0], [2.0, 1, 'out'], [3.6, 1], [4.2, 0]], T));
      return;
    }
    if (mode === 'enraizar') {
      const T = t % 4.6;
      const R = ctx.ref; const RZ = -2.8; R.place(0, RZ, 0); R.spin(Math.PI);
      // gira no lugar cavando, afunda; a terra levanta numa linha até o alvo; brota embaixo dele mordendo e jogando
      const dig = seq([[0.2, 0], [1.0, 1, 'in']], T), go = seq([[1.0, 0], [2.0, 1, 'io']], T), pop = seq([[2.0, 0], [2.2, 1, 'out'], [3.2, 1], [3.7, 0, 'in']], T);
      const back = seq([[3.7, 0], [4.4, 1, 'out']], T);
      let z = RZ * go, y = -0.9 * dig;
      if (T > 2.0) { z = RZ; y = -0.9 + 1.25 * pop - 0.35 * Math.min(1, pop * 3) * 0; }
      if (T > 3.7) { z = RZ * (1 - back); y = -0.9 * (1 - back); }
      obj.position.z += z; obj.position.y += y;
      r('bulbo', 0, T < 1.0 ? T * 14 * dig : 0, 0);
      legs(T * 20, T < 1.0 ? 1 : 0.2);
      const under = (T > 0.9 && T < 2.0) || (T > 3.6 && T < 4.4);
      obj.visible = !(T > 1.0 && T < 2.0) && !(T > 3.75 && T < 4.35);
      // terra levantando: um calombo que corre
      if (T > 0.3 && T < 1.1) ctx.get('cava', () => DBFX.grains({ n: 40, seed: 77, palette: [[90, 66, 44], [60, 44, 30], [70, 110, 50]], size: [0.025, 0.06], drag: 2.5, grav: 6, life: 0.6, spread: 0.7, cone: R2 => { const a2 = R2() * 6.28; return [Math.cos(a2) * 1.5, 0.8 + R2() * 1.2, Math.sin(a2) * 1.5]; } })).at(new THREE.Vector3(0, 0.05, 0), T - 0.3, 0);
      if (T > 1.0 && T < 2.05) {
        const zz = RZ * go;
        ctx.get('calombo', () => DBFX.grains({ n: 30, seed: 78, palette: [[90, 66, 44], [60, 44, 30]], size: [0.03, 0.06], drag: 3, grav: 7, life: 0.35, spread: 0.3, cone: R2 => [(R2() - 0.5) * 1.2, 0.6 + R2(), (R2() - 0.5) * 1.2] })).at(new THREE.Vector3(0, 0.03, zz), (T - 1.0) % 0.35, 0);
        ctx.shake(0.15);
      }
      if (T > 2.0 && T < 2.6) { ctx.get('estouro', () => DBFX.grains({ n: 60, seed: 79, palette: [[90, 66, 44], [60, 44, 30], [70, 110, 50]], size: [0.03, 0.08], drag: 1.6, grav: 8, life: 0.8, cone: R2 => { const a2 = R2() * 6.28; return [Math.cos(a2) * 1.6, 2 + R2() * 2, Math.sin(a2) * 1.6]; } })).at(new THREE.Vector3(0, 0.05, RZ), T - 2.0, 0); ctx.shake(0.6 * (1 - (T - 2.0) / 0.6)); }
      if (T > 2.0) mouth(seq([[2.0, 1], [2.3, 1], [2.35, 0, 'snap'], [3.0, 0], [3.2, 0.6]], T));
      // alvo: jogado pra cima no estouro e cai de lado
      const fl = FX.clamp01((T - 2.02) / 0.9);
      if (T > 2.02 && fl < 1) { R.place(0.5 * fl, RZ - 0.6 * fl, 1.4 * Math.sin(Math.PI * fl)); R.lean(-1.2 * fl); R.armsOut(0.8); }
      else if (fl >= 1) { R.place(0.5, RZ - 0.6, 0); R.lean(-1.2 * (1 - FX.clamp01((T - 3.0) / 0.8))); }
      leaves(1.4, 2);
      return;
    }
    if (mode === 'morte') {
      // murcha: folhas caem moles, o caule dobra, a flor perde as pétalas, o bulbo enruga e vira cinza
      const { T, fall } = JJFX.exorcize(t, obj, e, ctx, { h: 1.0, refZ: -1.6, rotX: 0.25, back: 0.2, ashY: 0.25, ashZ: 0, n: 110 });
      r('folhaD', 0, 0, 0.9 * fall); r('folhaE', 0, 0, -0.9 * fall); r('caule', 0.9 * fall, 0, 0.3 * fall);
      if (P.bulbo) P.bulbo.obj.scale.set(1 - 0.12 * fall, 1 - 0.2 * fall, 1 - 0.12 * fall);
      for (const n of ['DF', 'EF', 'DT', 'ET']) r('ponta' + n, 0.6 * fall, 0, 0);
      obj.position.y -= 0.08 * fall;
      if (T > 0.4 && T < 1.8) ctx.get('petalas', () => DBFX.grains({ n: 14, seed: 81, palette: [e.v.bud, mix(e.v.bud, [255, 255, 255], 0.3)], size: [0.03, 0.05], drag: 2.5, grav: 0.9, life: 1.4, cone: R2 => [(R2() - 0.5) * 0.8, R2() * 0.5, (R2() - 0.5) * 0.8] })).at(W('caule', 0, -5.5, 0), T - 0.4, 0.02);
    }
  }

  // semente voando: caroço oval com a casca listrada
  function seedMesh() {
    const g = new THREE.Group();
    const a = new THREE.Mesh(new THREE.BoxGeometry(0.09, 0.07, 0.13), new THREE.MeshLambertMaterial({ color: 0x8a6a3a }));
    const b = new THREE.Mesh(new THREE.BoxGeometry(0.095, 0.02, 0.135), new THREE.MeshLambertMaterial({ color: 0x4a3420 }));
    b.position.y = 0.02; g.add(a, b); g.frustumCulled = false;
    return g;
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(5, 0), variant(19, 1), variant(37, 2)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'broto', nome: 'Broto Andante', bioma: 'Hanami', tier: 'Grau 4 · tier 9',
    variants: fixed.map(v => ({ nome: KINDS[v.kind][0], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado (' + KINDS[v.kind][0] + ')', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Correr'], ['sementes', 'Cuspe de Sementes'], ['enraizar', 'Enraizar'], ['morte', 'Exorcizado']],
    cam: 3.2, lookY: 0.6, lookZ: { sementes: -1.3, enraizar: -1.4, morte: 0 }, camFar: { andar: 4.2, sementes: 5.2, enraizar: 5.6 }, camYaw: { sementes: -1.2, enraizar: -1.3 },
    info: {
      'Tamanho': '1 bloco de altura com o broto (o bulbo é do tamanho de uma abóbora)',
      'Vida': '16',
      'Jeito': 'Fica plantado parado no meio do mato, igual planta. Quando você passa, abre o olho e corre de lado nas raízes. Anda em grupinhos de 2 a 4.',
      'Cuspe de Sementes': 'Cospe 3 sementes em volta de você; cada uma brota um cipó que agarra o pé: lento 50% por 2 s (cada cipó quebra com um golpe).',
      'Enraizar': 'Afunda na terra (dá pra ver o calombo correndo no chão) e brota embaixo de você mordendo: te joga pra cima. Pular na hora que o calombo chega desvia.',
      'Fraqueza': 'Fogo (dano triplo) e pá: bater com pá arranca ele do chão quando está enterrado.',
      'Solta': 'Semente Amaldiçoada (planta um broto que te ajuda por 1 min), Folha; raro: Bulbo Vivo.',
      'Variações': 'Verde, de cerejeira (botão rosa) ou seco; às vezes com uma terceira folha.',
    },
  });
})();
