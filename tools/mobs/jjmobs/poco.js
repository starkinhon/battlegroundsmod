// Mulher do Poço (Aldeia Amaldiçoada). Tier 6. A maldição da criada que morreu no poço por causa de um prato
// quebrado (a história da Okiku, contada em toda aldeia): quimono branco de enterro, cabelo preto até a cintura
// cobrindo a cara, sem pés (flutua), braços compridos demais. Mora no poço e conta pratos à noite: "um... dois...".
// Golpes: Contar Pratos (joga os pratos um por um; no nono, que não existe, ela grita) e Puxar pro Poço (o cabelo
// agarra você e arrasta até a beira).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  function variant(seed, force) {
    const R = FX.rnd(seed);
    return Object.assign({ seed, kimono: mix([232, 230, 222], [206, 214, 216], R()), skin: mix([214, 216, 220], [190, 200, 196], R()), wet: R() < 0.6, eye: R() < 0.5, size: 0.97 + R() * 0.08 }, force || {});
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.8, hi = 1.1) => {
      const f = 0.62 - 0.4 * q.ny + 0.3 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const P = {};
    // quimono de morto: branco, dobra esquerda por cima (do jeito que se veste defunto), molhado embaixo, lodo verde
    P.kimono = q => {
      let c = shade(v.kimono, q, 1);
      if (fr(q.ax * 0.45 + n3(q.ax * 0.2, 0, q.az * 0.2, 2 + S)) < 0.08) c = mul(c, 0.88);                              // pregas
      if (v.wet && q.ay > 14 + 4 * n3(q.ax * 0.3, 0, q.az * 0.3, 3 + S)) c = mix(c, [120, 136, 120], 0.45);              // barra molhada de lodo
      if (n3(q.ax * 0.5, q.ay * 0.5, q.az * 0.5, 4 + S) > 0.78) c = mix(c, [150, 160, 140], 0.35);
      return c;
    };
    P.torso = q => {
      let c = P.kimono(q);
      if (q.f === 'north') {
        const x = q.nx - 0.5, y = q.ny;
        if (Math.abs(x + (0.3 - y * 0.5)) < 0.05 && y < 0.62) c = mul(c, 0.7);                                          // gola cruzada
        if (y > 0.62 && y < 0.74) c = shade([200, 196, 186], q, 5);                                                        // faixa (obi) simples
      }
      return c;
    };
    P.skin = q => {
      let c = shade(v.skin, q, 6, 0.82, 1.06);
      if (n3(q.ax * 0.6, q.ay * 0.6, q.az * 0.6, 7 + S) > 0.72) c = mix(c, [150, 160, 170], 0.4);                        // pele de afogada, manchada
      return c;
    };
    P.hand = q => { let c = P.skin(q); if (q.ny > 0.7 && fr(q.ax * 1.1) < 0.45) c = mul(c, 0.8); if (q.ny > 0.9) c = [70, 60, 66]; return c; };  // dedos e unhas sujas
    // cabelo molhado: fios grossos pretos com brilho azulado, pontas pingando
    const hairC = (q, s) => {
      const strand = fr(q.ax * 1.1 + q.az * 1.1 + 0.4 * n3(q.ax * 0.3, q.ay * 0.1, q.az * 0.3, s + S));
      let c = cel(strand < 0.5 ? [16, 14, 20] : [26, 24, 32], 0.6 - 0.3 * q.ny + (q.f === 'top' ? 0.2 : 0), 0.8, 1.3);
      if (strand > 0.86 && q.ny < 0.6) c = [52, 56, 72];
      return c;
    };
    P.hair = q => hairC(q, 8);
    // franja da frente: no meio, por uma fresta, aparece um olho (variação)
    P.hairFront = q => {
      let c = hairC(q, 9);
      if (v.eye && q.f === 'north') {
        const x = Math.floor(q.px), y = Math.floor(q.py);
        if (y === 4 && (x === 5 || x === 6)) return x === 5 ? [230, 226, 220] : [12, 8, 10];
        if (y >= 3 && y <= 5 && (x === 4 || x === 7)) return mul(v.skin, 0.75);
      }
      return c;
    };
    P.stone = q => {
      let c = shade([120, 116, 108], q, 10, 0.72, 1.16);
      const bx = Math.floor(q.ax / 3 + (Math.floor(q.ay / 2.5) % 2) * 0.5), by = Math.floor(q.ay / 2.5);
      if (fr(q.ax / 3 + (by % 2) * 0.5) < 0.12 || fr(q.ay / 2.5) < 0.16) c = mul(c, 0.62);                                // rejunte
      if (n3(bx * 3.1, by * 2.7, 0, 11 + S) > 0.6) c = mul(c, 0.88);
      if (n3(q.ax * 0.4, q.ay * 0.4, q.az * 0.4, 12 + S) > 0.66) c = mix(c, [70, 100, 60], 0.5);                          // musgo
      return c;
    };
    P.water = q => {
      if (q.f !== 'top') return [10, 14, 18];
      const d = Math.hypot(q.nx - 0.5, q.nz - 0.5);
      let c = mix([6, 10, 14], [24, 34, 40], fr(d * 5) < 0.2 ? 1 : 0);
      return c;
    };
    P.wood = q => { let c = shade([92, 70, 50], q, 13); if (fr(q.ay * 0.4 + q.ax * 0.1) < 0.2) c = mul(c, 0.8); return c; };
    return P;
  }

  function rig(P, v, lo) {
    const arm = s => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * 4.6, -10.4, 0], rot: [0, 0, s * -0.05], boxes: [{ x: -2, y: -1, z: -2.4, w: 4, h: 8, d: 4.8, p: P.kimono }],
      kids: [{
        name: s < 0 ? 'anteD' : 'anteE', pivot: [0, 6.2, 0], boxes: [{ x: -0.9, y: 0, z: -0.9, w: 1.8, h: 9, d: 1.8, p: P.skin }],
        kids: [{ name: s < 0 ? 'maoD' : 'maoE', pivot: [0, 9, 0], boxes: [{ x: -1.1, y: 0, z: -1.2, w: 2.2, h: 3.6, d: 2.4, p: P.hand }] }],
      }],
    });
    // poço de pedra (fica parado no chão: a raiz dela se move por cima)
    const well = lo ? [
      { x: -10, y: -11, z: -10, w: 20, h: 11, d: 20, p: P.stone },
    ] : [
      { x: -10, y: -11, z: -10, w: 20, h: 11, d: 3.4, p: P.stone }, { x: -10, y: -11, z: 6.6, w: 20, h: 11, d: 3.4, p: P.stone },
      { x: -10, y: -11, z: -6.6, w: 3.4, h: 11, d: 13.2, p: P.stone }, { x: 6.6, y: -11, z: -6.6, w: 3.4, h: 11, d: 13.2, p: P.stone },
      { x: -6.6, y: -5, z: -6.6, w: 13.2, h: 1, d: 13.2, p: P.water },
      { x: -10.4, y: -12.2, z: -10.4, w: 20.8, h: 1.4, d: 3.8, p: P.stone }, { x: -10.4, y: -12.2, z: 6.6, w: 20.8, h: 1.4, d: 3.8, p: P.stone },
      { x: -9, y: -36, z: -1, w: 2.2, h: 24, d: 2.2, p: P.wood }, { x: 6.8, y: -36, z: -1, w: 2.2, h: 24, d: 2.2, p: P.wood },
      { x: -10, y: -37.6, z: -1.3, w: 20, h: 2.2, d: 2.8, p: P.wood },
      { x: -11, y: -39.4, z: -4, w: 22, h: 1.4, d: 8, p: P.wood },
    ];
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        { name: 'poco', pivot: [0, 24, 0], boxes: well },
        {
          name: 'quadril', pivot: [0, 12, 0], boxes: [
            // saia do quimono afunilando, termina no ar (não tem pés)
            { x: -4.2, y: 0, z: -2.6, w: 8.4, h: 6, d: 5.2, p: P.kimono },
            { x: -3.8, y: 6, z: -2.3, w: 7.6, h: 3.4, d: 4.6, p: P.kimono },
            ...(lo ? [] : [{ x: -3.2, y: 9.4, z: -1.9, w: 6.4, h: 1.6, d: 3.8, p: P.kimono }]),
          ],
          kids: [{
            name: 'tronco', pivot: [0, 0, 0], boxes: [{ x: -4, y: -11, z: -2.2, w: 8, h: 11, d: 4.4, p: P.torso }],
            kids: [
              arm(-1), arm(1),
              {
                name: 'cabeca', pivot: [0, -11, 0], rot: [0.35, 0, 0], boxes: [{ x: -3.6, y: -7.6, z: -3.6, w: 7.2, h: 7.6, d: 7.2, p: P.skin }],
                kids: [{
                  name: 'cabelo', pivot: [0, -7.6, 0], boxes: [
                    { x: -4, y: -0.8, z: -4, w: 8, h: 1.4, d: 8, p: P.hair },
                    { x: -4, y: -0.4, z: -4.3, w: 8, h: 15, d: 0.8, p: P.hairFront },                                    // cortina na cara
                    { x: -4.2, y: -0.4, z: -4, w: 0.9, h: 17, d: 8, p: P.hair }, { x: 3.3, y: -0.4, z: -4, w: 0.9, h: 17, d: 8, p: P.hair },
                    { x: -4, y: -0.4, z: 3.3, w: 8, h: 22, d: 0.9, p: P.hair },
                    ...(lo ? [] : [{ x: -3, y: 14.4, z: -4.2, w: 2, h: 3, d: 0.6, p: P.hair }, { x: 1.4, y: 14.2, z: -4.2, w: 1.6, h: 4, d: 0.6, p: P.hair }]),
                  ],
                }],
              },
            ],
          }],
        },
      ],
    };
  }

  // prato de porcelana: disco raso branco com borda azul e um desenho azul no meio (tudo em cubos)
  function plate(i) {
    const g = new THREE.Group();
    const white = new THREE.MeshLambertMaterial({ color: 0xf0eee6 }), blue = new THREE.MeshLambertMaterial({ color: 0x3050a0 });
    const add = (w, h, d, x, y, z, m) => { const b = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), m); b.position.set(x, y, z); g.add(b); };
    add(0.34, 0.025, 0.34, 0, 0, 0, white); add(0.26, 0.03, 0.26, 0, 0.012, 0, white);
    for (const [x, z, w, d] of [[0, 0.165, 0.34, 0.02], [0, -0.165, 0.34, 0.02], [0.165, 0, 0.02, 0.34], [-0.165, 0, 0.02, 0.34]]) add(w, 0.03, d, x, 0.01, z, blue);
    add(0.08, 0.035, 0.03, 0.02 * i % 0.04, 0.015, 0, blue); add(0.03, 0.035, 0.08, 0, 0.015, 0.01, blue);
    return g;
  }

  function animIn(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    // o poço não anda com ela: some nos modos em que ela já saiu
    // dy = quanto a mulher foi pra baixo no mundo: o poço sobe o mesmo tanto pra ficar no chão
    const well = (on) => { P.poco.obj.visible = on; };
    const float = (k = 1) => { obj.position.y += 0.06 * k * Math.sin(t * 1.3); r('cabelo', 0.03 * Math.sin(t * 1.1), 0, 0.02 * Math.sin(t * 0.8)); };
    // braços pendurados mortos, balançando um pouco atrasados
    const limp = (k = 1) => { r('bracoD', 0.06 * Math.sin(t * 1.3 - 0.6) * k, 0, 0.04); r('bracoE', 0.06 * Math.sin(t * 1.3 - 0.9) * k, 0, -0.04); };
    const glitch = () => {};                                                                                              // o glitch agora é feito no anim() (ver glitchAt)

    if (mode === 'parado') {
      // dentro do poço até a cintura, cabeça baixa; levanta devagar o rosto (escondido) e conta com os dedos
      well(true, 0.55);
      obj.position.y -= 0.55;
      float(0.5); limp(); glitch();
      const look = loop([[0, 0], [0.4, 0], [0.6, 1], [0.85, 1], [1, 0]], t / 8);
      r('cabeca', -0.35 * look, 0.2 * Math.sin(t * 0.3), 0.25 * look);
      // mãos apoiadas na borda do poço, dedos caídos pra fora; uma bate devagar na pedra contando
      r('bracoD', -1.6, 0.2, 0.1); r('anteD', 0.25, 0, 0); r('maoD', 1.1, 0, 0);
      r('bracoE', -1.6 - 0.1 * Math.max(0, Math.sin(t * 2.6)), -0.2, -0.1); r('anteE', 0.25, 0, 0); r('maoE', 1.1, 0, 0);
      return;
    }
    if (mode === 'andar') {
      // flutua pra frente sem passo: o corpo inclina, os braços ficam pra trás, o cabelo arrasta
      well(false);
      float(1); glitch();
      obj.position.z -= (t * 0.6) % 2.4 - 1.2;
      r('tronco', 0.25, 0, 0); r('cabeca', -0.1, 0, 0);
      r('bracoD', 0.45, 0, 0.05); r('bracoE', 0.45, 0, -0.05);
      r('cabelo', -0.15 + 0.05 * Math.sin(t * 3), 0, 0);
      return;
    }
    if (mode === 'pratos') {
      const T = t % 6.4;
      const R = ctx.ref; const RZ = -3.0; R.place(0.2, RZ, 0); R.spin(Math.PI);
      well(true, 0.35); obj.position.y -= 0.35;
      float(0.4); glitch();
      // pega um prato de dentro do poço, olha, conta ("um... dois..."), joga girando; oito pratos, depois procura o nono
      const N = 8, dt = 0.5;
      const T0 = 0.3;
      const k = Math.floor((T - T0) / dt), f = ((T - T0) / dt) - k;
      const throwing = T > T0 && k < N;
      const arm = throwing ? seq([[0, 0], [0.45, 1, 'io'], [0.65, -0.4, 'out'], [1, 0]], f) : 0;
      r('bracoD', -1.3 * Math.max(0, arm) + 0.5 * Math.min(0, arm) - 0.6, -0.3, 0.2); r('anteD', -0.3, 0, 0);
      r('bracoE', -0.6, 0, 0.3); r('anteE', -0.6, 0, 0);
      r('cabeca', 0.1 * Math.sin(T * 6) * (throwing ? 1 : 0), 0, 0);
      obj.updateMatrixWorld(true);
      const hand = new THREE.Vector3(0, 3, 0).applyMatrix4(P.maoD.obj.matrixWorld);
      for (let i = 0; i < N; i++) {
        const pl = ctx.get('prato' + i, () => plate(i));
        const ft = (T - T0) / dt - i;                                                                                     // 0..1 = na mão; 0.6.. = voando
        if (ft < 0 || ft > 2.2) { pl.visible = false; continue; }
        pl.visible = true;
        const aim = new THREE.Vector3(0.2 + 0.25 * Math.sin(i * 2.3), 0.9 + 0.5 * fr(i * 0.37), RZ);
        if (ft < 0.62) { pl.position.copy(hand); pl.rotation.set(1.3, 0, 0.2); }
        else {
          const fl = FX.clamp01((ft - 0.62) / 0.55);
          pl.position.copy(hand.clone().lerp(aim, fl)); pl.position.y += 0.4 * Math.sin(Math.PI * fl);
          pl.rotation.set(0.2, ft * 18, 0.1);
          if (fl >= 1) {
            pl.visible = false;
            const age = (ft - 1.17) * dt;
            ctx.get('caco' + i, () => DBFX.grains({ n: 24, seed: 30 + i, palette: [[240, 238, 230], [230, 228, 220], [48, 80, 160]], size: [0.025, 0.06], drag: 2, grav: 7, life: 0.6, cone: R2 => { const a2 = R2() * 6.28; return [Math.cos(a2) * 2, 0.5 + R2() * 2, Math.sin(a2) * 2 + 1]; } })).at(aim, age, 0);
            if (age < 0.12) { R.lean(-0.18); ctx.shake(0.25); }
          }
        }
      }
      // o nono não existe: as duas mãos procuram dentro do poço, a cabeça treme e ela grita (onda que escurece tudo)
      const search = seq([[T0 + N * dt + 0.2, 0], [T0 + N * dt + 0.5, 1, 'io'], [5.2, 1], [5.4, 0]], T);
      r('tronco', 0.6 * search, 0, 0); r('bracoD', 0.5 * search, 0, 0); r('bracoE', 0.5 * search, 0, 0);
      const scream = seq([[5.2, 0], [5.35, 1, 'snap'], [6.1, 1], [6.4, 0]], T);
      r('tronco', -0.75 * scream, 0, 0); r('cabeca', -0.6 * scream + 0.08 * Math.sin(T * 60) * scream, 0, 0);
      r('bracoD', -0.4 * scream, 0, 0.6 * scream); r('bracoE', -0.4 * scream, 0, -0.6 * scream);
      if (scream > 0) {
        obj.updateMatrixWorld(true); const h = new THREE.Vector3(); P.cabeca.obj.getWorldPosition(h);
        ctx.roarRing(new THREE.Vector3(0, 0.02, 0), FX.clamp01((T - 5.25) / 0.7));
        ctx.dark(0.55 * scream); ctx.shake(0.6 * scream); ctx.cursed(scream);
        R.face(scream, true);
      }
      return;
    }
    if (mode === 'puxar') {
      const T = t % 5.6;
      const R = ctx.ref; let RZ = -4.0;
      well(true, 0.35); obj.position.y -= 0.35;
      float(0.4); limp(0.5);
      // levanta a cabeça; o cabelo da frente se abre e voa como corda até o alvo, enrola e puxa até a beira do poço;
      // ela abraça o alvo por trás e afunda com ele (escurece). Depois sobe sozinha.
      const lift = seq([[0.1, 0], [0.5, 1, 'out']], T);
      r('cabeca', -0.55 * lift, 0, 0);
      obj.updateMatrixWorld(true);
      const head = new THREE.Vector3(0, -4, -4).applyMatrix4(P.cabeca.obj.matrixWorld);
      const shoot = seq([[0.5, 0], [0.85, 1, 'out']], T);
      const drag = seq([[1.2, 0], [2.6, 1, 'io']], T);
      const sink = seq([[3.0, 0], [3.4, 1, 'out'], [3.6, 1], [4.4, 1.6, 'in']], T);
      const refPos = new THREE.Vector3(0.05 * (1 - drag), 0, RZ + (RZ + 0.12) * -drag);                                    // termina no meio do buraco
      if (sink > 1) refPos.y -= (sink - 1) * 2.4;
      R.place(refPos.x, refPos.z, refPos.y); R.spin(Math.PI);
      R.lean(-0.35 * drag * (1 - Math.min(1, sink))); R.armsOut(0.4 * (drag > 0 ? 1 : 0) + 0.2 * Math.sin(T * 8) * drag);
      const tgt = refPos.clone().add(new THREE.Vector3(0, 1.1, 0));
      for (let i = 0; i < 4; i++) {
        const tb = ctx.get('fio' + i, () => JJFX.tube(0x141218, 0.07 - i * 0.008, 0.035));
        const off = new THREE.Vector3((i - 1.5) * 0.06, (i % 2) * 0.05, 0);
        tb.at(head.clone().add(off), tgt.clone().add(new THREE.Vector3(Math.cos(T * 9 + i * 1.6) * 0.3, (i - 1.5) * 0.12, Math.sin(T * 9 + i * 1.6) * 0.3 * (1 - drag) + 0.25)), shoot * (1 - FX.clamp01((sink - 0.2) * 3)), { arc: 0.25 * (1 - drag), wiggle: 0.12 * (1 - drag), t: T });
      }
      // abraça e afunda
      // abraça por trás e, já afundando, os braços sobem (afunda puxando pelos ombros); tudo cabe no buraco do poço
      const hug = Math.min(1, sink), down = FX.clamp01(sink - 1) > 0 ? 1 : 0;
      r('bracoD', -1.4 * hug * (1 - down) - 2.9 * down, 0.5 * hug * (1 - down), 0); r('anteD', -1.1 * hug * (1 - down), 0, 0);
      r('bracoE', -1.4 * hug * (1 - down) - 2.9 * down, -0.5 * hug * (1 - down), 0); r('anteE', -1.1 * hug * (1 - down), 0, 0);
      obj.position.z += 0.1 * hug;
      if (sink > 1) obj.position.y -= (sink - 1) * 2.4;
      if (sink > 1.05 && sink < 1.4) ctx.get('respingo', () => DBFX.grains({ n: 60, seed: 41, palette: [[40, 60, 70], [90, 120, 130], [20, 30, 34]], size: [0.03, 0.07], drag: 2, grav: 8, life: 0.8, cone: R2 => { const a2 = R2() * 6.28; return [Math.cos(a2) * 1.2, 2 + R2() * 2, Math.sin(a2) * 1.2]; } })).at(new THREE.Vector3(0, 0.5, 0), (sink - 1.05) * 2, 0);
      if (sink > 1.2) ctx.drown(Math.min(1, (sink - 1.2) * 3));
      // sobe sozinha de novo
      const back = seq([[4.6, 0], [5.4, 1, 'io']], T);
      if (back > 0) { obj.position.y += (sink - 1) * 2.4 * back; R.place(99, 0, 0); }
      return;
    }
    if (mode === 'morte') {
      well(false);
      // grita sem som, se dobra pra trás e cai de volta no poço; o cabelo é a última coisa a sumir
      const { T, fall } = JJFX.exorcize(t, obj, e, ctx, { h: 2.4, refZ: -2.4, rotX: 0, back: 0, ashY: 1.0, ashZ: 0, n: 160 });
      r('cabeca', -0.7 * fall, 0, 0); r('bracoD', -0.3 * fall, 0, 0.9 * fall); r('bracoE', -0.3 * fall, 0, -0.9 * fall);
      obj.position.y -= 0.6 * fall;
    }
  }

  // o poço é filho da raiz dela: depois de animar, desfaz no poço o quanto ela subiu/desceu, pra ele ficar no chão
  // ---------- glitch de fantasma ----------
  // De tempos em tempos (intervalo sorteado) ela "falha" por ~0,45 s, igual fita VHS de filme de terror:
  //  - a animação perde quadros (trava e pula, 8 quadros por segundo);
  //  - o corpo salta pra um lugar ao lado por 2 ou 3 quadros e volta (como se tivesse se mexido sem ninguém ver);
  //  - dois ecos dela (ciano e vermelho) aparecem deslocados atrás, cada um num quadro diferente;
  //  - num quadro ela some de vez.
  const hash = n => { const x = Math.sin(n * 127.1 + 311.7) * 43758.5453; return x - Math.floor(x); };
  function glitchAt(t) {
    const slot = Math.floor(t / 2.6), st = slot * 2.6 + 0.4 + hash(slot) * 1.6, len = 0.32 + hash(slot + 9) * 0.18;
    const u = t - st;
    if (u < 0 || u > len) return null;
    const fr8 = Math.floor(u * 12);                                                                                       // quadro do glitch (12 por segundo)
    return { u, len, slot, fr8, k: u / len };
  }
  // caminho de índices de um filho dentro da árvore (pra achar o mesmo objeto no eco clonado)
  const pathOf = (root, o) => { const p = []; while (o && o !== root) { p.unshift(o.parent.children.indexOf(o)); o = o.parent; } return p; };
  const byPath = (root, p) => p.reduce((o, i) => o && o.children[i], root);
  function sync(a, b) { b.position.copy(a.position); b.quaternion.copy(a.quaternion); b.scale.copy(a.scale); b.visible = a.visible; for (let i = 0; i < a.children.length && i < b.children.length; i++) sync(a.children[i], b.children[i]); }
  function echo(e, color) {
    const c = e.obj.clone(true);
    const src = e.obj.children[0] && e.obj.children[0].children[0];
    c.traverse(o => { if (o.isMesh) o.material = new THREE.MeshBasicMaterial({ map: o.material.map, color, transparent: true, opacity: 0.55, blending: THREE.AdditiveBlending, depthWrite: false, alphaTest: 0.5 }); });
    c.wellPath = null;
    return c;
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const G = mode === 'morte' ? null : glitchAt(t);
    // perde quadros: o tempo anda em degraus durante a falha
    const tt = G ? Math.floor(t * 8) / 8 : t;
    animIn(P, tt, mode, a, obj, e, ctx);
    const s = e.v.size, base = 1.5 + 1.5 * (s - 1);
    if (P.poco.obj.visible) P.poco.obj.position.set(obj.position.x * 16 / s, 24 + (obj.position.y - base) * 16 / s, obj.position.z * 16 / s);
    const eco = [ctx.get('ecoC' + e.obj.uuid, () => echo(e, 0x30e8ff)), ctx.get('ecoV' + e.obj.uuid, () => echo(e, 0xff2848))];
    eco.forEach(c => { c.visible = false; });
    if (!G) return;
    // salto: o corpo dela (não o poço) aparece em outro ponto por alguns quadros
    const jumpOn = G.fr8 >= 1 && G.fr8 <= 2 + Math.floor(hash(G.slot + 3) * 2);
    const room = P.poco.obj.visible ? 0.4 : 1;                                                                            // dentro do poço não pode atravessar a pedra
    const jx = (hash(G.slot + 5) < 0.5 ? -1 : 1) * (5 + 3 * hash(G.slot + 8)) * room, jz = (hash(G.slot + 6) - 0.5) * 5 * room;
    if (jumpOn) { P.quadril.obj.position.x += jx; P.quadril.obj.position.z += jz; }
    // um quadro em que ela some
    if (G.fr8 === 3 + Math.floor(hash(G.slot + 7) * 2)) P.quadril.obj.visible = false;
    // ecos: copiam a pose, cada um deslocado pra um lado e aceso em quadros alternados
    obj.updateMatrixWorld(true);
    const wp = pathOf(obj, P.poco.obj);
    eco.forEach((c, i) => {
      sync(obj, c);
      const w = byPath(c, wp); if (w) w.visible = false;
      const on = (G.fr8 + i) % 2 === 0 || G.fr8 === 0;
      c.visible = on;
      const side = i ? 1 : -1, amp = 0.06 + 0.1 * hash(G.slot * 3 + G.fr8 + i);
      c.position.x += side * amp; c.position.y += (hash(G.fr8 + i * 7) - 0.5) * 0.04;
      const q = byPath(c, pathOf(obj, P.quadril.obj)); if (q) { q.visible = true; if (jumpOn) q.position.x -= jx * (i ? 0.5 : 0.3); }
    });
    // chuvisco: lascas escuras soltas em volta, só durante a falha
    ctx.get('chuvisco', () => DBFX.grains({ n: 40, seed: 77, palette: [[10, 10, 14], [230, 230, 236], [40, 200, 230], [230, 40, 70]], size: [0.02, 0.06], drag: 6, grav: 0, life: 0.35, spread: 0.3,
      cone: R2 => [(R2() - 0.5) * 3, (R2() - 0.2) * 2.4, (R2() - 0.5) * 1.5] })).at(new THREE.Vector3(obj.position.x, obj.position.y + 0.2, obj.position.z), G.u, -9);
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(6, { eye: true }), variant(34, { eye: false }), variant(57, { eye: true, wet: false })];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'poco', nome: 'Mulher do Poço', bioma: 'Aldeia Amaldiçoada', tier: 'Grau 2 · tier 6',
    variants: fixed.map((v, i) => ({ nome: ['Olho na Fresta', 'Sem Rosto', 'Seca'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteada', v, ...def(v) }; },
    anim, modes: [['parado', 'No Poço'], ['andar', 'Flutuar'], ['pratos', 'Contar Pratos'], ['puxar', 'Puxar pro Poço'], ['morte', 'Exorcizada']],
    cam: 5.4, lookY: 1.3, lookZ: { pratos: -1.4, puxar: -1.8, morte: -0.4 }, camFar: { pratos: 6.8, puxar: 7.4 }, camYaw: { pratos: -1.05, puxar: -1.2 },
    info: {
      'Tamanho': '2 blocos (flutuando), braços compridos até o joelho',
      'Vida': '45',
      'Jeito': 'Fica no poço contando pratos baixinho (dá pra ouvir de longe: "um... dois..."). Se você olhar dentro do poço ou passar perto à noite, ela sobe. Flutua, atravessa cerca, de vez em quando "pula" uns quadros pra frente.',
      'Contar Pratos': 'Joga 8 pratos girando, um por um (cada prato quebra em cacos que cortam). No nono, procura e não acha: grita, a tela escurece e quem está perto toma dano de energia amaldiçoada.',
      'Puxar pro Poço': 'O cabelo voa como corda, te enrola e arrasta até a beira; ela te abraça e afunda junto: você fica embaixo d\'água no escuro (afogando) por 3 s. Cortar o cabelo antes de chegar na beira solta.',
      'Fraqueza': 'Devolver um prato (item Prato de Porcelana) na mão dela faz ela parar de contar por 10 s.',
      'Solta': 'Caco de Porcelana, Cabelo Molhado; raro: Prato da Okiku (o nono prato: segurar deixa as maldições da aldeia neutras por 1 min).',
      'Variações': 'Olho aparecendo por uma fresta do cabelo ou sem rosto nenhum; quimono molhado de lodo ou seco.',
    },
  });
})();
