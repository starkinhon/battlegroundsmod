// Espantalho de Palha (Aldeia Amaldiçoada). Tier 9. O medo de plantação abandonada à noite: espantalho de saco de
// pano com o rosto riscado a carvão (o "henohenomoheji" que as crianças desenham), chapéu de palha, quimono de
// trapo amarrado numa trave e um peito oco cheio de corvos. Anda pulando no próprio poste.
// Golpes: Revoada (os corvos saem do peito e bicam a sua cara) e Abraço de Palha (fecha a trave em você e espeta).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  const RAGS = [[120, 60, 50], [62, 76, 110], [96, 104, 70], [140, 120, 90]];
  function variant(seed, force) {
    const R = FX.rnd(seed);
    return Object.assign({ seed, rag: RAGS[Math.floor(R() * RAGS.length)], sack: mix([206, 186, 146], [170, 150, 120], R()), hat: R() < 0.75, patch: R() < 0.6, tear: R() < 0.4, tilt: (R() - 0.5) * 0.5, size: 0.95 + R() * 0.1 }, force || {});
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.78, hi = 1.12) => {
      const f = 0.62 - 0.4 * q.ny + 0.3 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    // palha: fios verticais de amarelos diferentes, pontas escuras
    const straw = (q, s, horiz) => {
      const k = horiz ? q.ax + q.az * 0.3 : q.ax * 0.9 + q.az * 0.9;
      const strand = fr(k * 1.3 + n3(q.ax * 0.2, q.ay * 0.2, q.az * 0.2, s + S));
      let c = shade(strand < 0.33 ? [214, 180, 92] : strand < 0.66 ? [190, 150, 70] : [230, 204, 120], q, s, 0.8, 1.12);
      if (n3(q.ax * 2, q.ay * 0.4, q.az * 2, s + 3 + S) > 0.78) c = mul(c, 0.7);
      return c;
    };
    // pano grosso: trama em xadrez fininho + manchas de chuva
    const cloth = (base, q, s) => {
      let c = shade(base, q, s);
      if ((Math.floor(q.ax * 2) + Math.floor(q.ay * 2)) % 2 === 0) c = mul(c, 0.95);
      if (n3(q.ax * 0.4, q.ay * 0.15, q.az * 0.4, s + 7 + S) > 0.7) c = mul(c, 0.78);
      return c;
    };
    const P = {};
    P.pole = q => { let c = shade([96, 70, 46], q, 1); if (fr(q.ay * 0.35 + n3(q.ax, q.ay * 0.1, q.az, 2 + S)) < 0.2) c = mul(c, 0.78); return c; };
    P.straw = q => straw(q, 3);
    P.strawH = q => straw(q, 4, true);
    P.rag = q => {
      let c = cloth(v.rag, q, 5);
      // gola cruzada do quimono, remendo e o buraco do peito (escuro, com palha saindo da borda)
      if (q.f === 'north') {
        const x = q.nx - 0.5, y = q.ny;
        if (Math.abs(Math.abs(x) - (0.38 - y * 0.55)) < 0.06 && y < 0.6) c = mul(c, 0.6);
        const hx = x + 0.05, hy = y - 0.5, d = hx * hx / 0.05 + hy * hy / 0.06;
        if (d < 1) return d < 0.55 ? [16, 10, 12] : straw(q, 6);
        if (v.patch && q.nx > 0.66 && q.nx < 0.9 && q.ny > 0.12 && q.ny < 0.34) c = cloth(mix(v.rag, [200, 190, 160], 0.6), q, 8);
      }
      if (q.ny > 0.9 && fr(q.ax * 0.7) < 0.5) c = mul(c, 0.7);                                                         // barra esfiapada
      return c;
    };
    P.sleeve = q => { let c = cloth(v.rag, q, 14); if (q.f === 'east' || q.f === 'west') { if (fr(q.az * 0.8) < 0.5) c = mul(c, 0.7); } return c; };
    P.sack = q => {
      let c = cloth(v.sack, q, 9);
      if (q.f !== 'north') { if (q.ny > 0.82) c = straw(q, 10); return c; }
      // rosto a carvão: sobrancelhas "へ", olhos "の", nariz "も", boca "へ", contorno "じ" (simplificado no pixel)
      const x = Math.floor(q.px), y = Math.floor(q.py);
      const FACE = ['.#....#.', '#.#..#.#', '........', '.##..##.', '.##..##.', '....#...', '.#....#.', '..####..'];
      const ch = (FACE[y] || '')[x];
      if (ch === '#') return n3(x, y, S, 15) > 0.8 ? [50, 40, 40] : [22, 18, 20];                                         // carvão, falhado aqui e ali
      // lágrima de sangue velho escorrendo do olho direito (variação)
      if (v.tear && x === 5 && (y === 5 || y === 6)) return [110, 34, 34];
      return c;
    };
    P.hat = q => {
      let c = straw(q, 12, true);
      if (q.f === 'top') { const d = Math.hypot(q.nx - 0.5, q.nz - 0.5); if (fr(d * 9) < 0.3) c = mul(c, 0.82); }
      return c;
    };
    P.rope = q => { let c = shade([150, 120, 70], q, 13); if (fr((q.ax + q.ay) * 0.9) < 0.4) c = mul(c, 0.75); return c; };
    return P;
  }

  function rig(P, v, lo) {
    const arm = s => ({
      // meia trave de cada lado: manga de trapo e tufo de palha no lugar da mão
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * 3.5, -8.6, 0], boxes: [
        { x: s < 0 ? -9.5 : 0, y: -1, z: -1, w: 9.5, h: 2, d: 2, p: P.pole },
        { x: s < 0 ? -8 : 0.5, y: -2.2, z: -2.4, w: 7.5, h: 4.4, d: 4.8, p: P.sleeve },
        { x: s < 0 ? -12.6 : 9.6, y: -2.4, z: -1.8, w: 3, h: 5.6, d: 3.6, p: P.straw },
        ...(lo ? [] : [{ x: s < 0 ? -8.6 : 7.4, y: -2.6, z: -2.6, w: 1.2, h: 5.2, d: 5.2, p: P.rope }]),
      ],
    });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'poste', pivot: [0, 24, 0], boxes: [{ x: -1, y: -16, z: -1, w: 2, h: 16, d: 2, p: P.pole }],
        kids: [{
          name: 'corpo', pivot: [0, -14, 0], rot: [0, 0, v.tilt * 0.2], boxes: [
            { x: -4.4, y: -11, z: -2.6, w: 8.8, h: 11.4, d: 5.2, p: P.rag },
            { x: -5, y: 0, z: -3, w: 10, h: 4.6, d: 6, p: P.straw },                                                        // saia de palha
            { x: -4.6, y: -1.2, z: -2.8, w: 9.2, h: 1.4, d: 5.6, p: P.rope },
          ],
          kids: [
            arm(-1), arm(1),
            {
              name: 'cabeca', pivot: [0, -11, 0], rot: [0, 0, v.tilt], boxes: [
                { x: -4, y: -8.4, z: -4, w: 8, h: 8, d: 8, p: P.sack },
                { x: -2, y: -0.8, z: -2, w: 4, h: 1.4, d: 4, p: P.rope },                                                   // amarração do pescoço
                ...(lo ? [] : [{ x: -2.6, y: 0.4, z: -2.6, w: 5.2, h: 2, d: 5.2, p: P.straw }]),
              ],
              kids: v.hat ? [{
                name: 'chapeu', pivot: [0, -8, 0], rot: [0.1, 0, -0.08], boxes: [
                  { x: -8.4, y: -0.6, z: -8.4, w: 16.8, h: 1, d: 16.8, p: P.hat },
                  { x: -5.8, y: -1.8, z: -5.8, w: 11.6, h: 1.4, d: 11.6, p: P.hat },
                  { x: -3.2, y: -3, z: -3.2, w: 6.4, h: 1.4, d: 6.4, p: P.hat },
                ],
              }] : [],
            },
          ],
        }],
      }],
    };
  }

  // corvo: corpo, cabeça, bico e duas asas que batem. Cada um com tamanho e tom de preto próprios.
  function crow(i) {
    const R = FX.rnd(400 + i * 13), g = new THREE.Group(), k = 0.75 + R() * 0.5;
    const tone = 0.08 + R() * 0.08, mat = new THREE.MeshLambertMaterial({ color: new THREE.Color(tone, tone * 0.95, tone * 1.2) });
    const box = (w, h, d, x, y, z, m = mat) => { const b = new THREE.Mesh(new THREE.BoxGeometry(w * k, h * k, d * k), m); b.position.set(x * k, y * k, z * k); g.add(b); return b; };
    box(0.12, 0.1, 0.22, 0, 0, 0); box(0.09, 0.09, 0.09, 0, 0.06, -0.14);
    box(0.03, 0.03, 0.08, 0, 0.05, -0.22, new THREE.MeshLambertMaterial({ color: 0x3a3236 }));
    box(0.025, 0.025, 0.02, 0.035, 0.08, -0.17, new THREE.MeshBasicMaterial({ color: 0xc8b040 }));
    box(0.025, 0.025, 0.02, -0.035, 0.08, -0.17, new THREE.MeshBasicMaterial({ color: 0xc8b040 }));
    box(0.1, 0.02, 0.12, 0, 0, 0.16);                                                                                     // cauda
    g.wings = [-1, 1].map(s => { const p = new THREE.Group(); p.position.set(s * 0.06 * k, 0.03 * k, 0); const w = new THREE.Mesh(new THREE.BoxGeometry(0.2 * k, 0.015, 0.14 * k), mat); w.position.x = s * 0.1 * k; p.add(w); g.add(p); p.s = s; return p; });
    g.flap = (t) => { for (const w of g.wings) w.rotation.z = w.s * (0.2 + 0.8 * Math.sin(t * 28 + i)); };
    g.ph = R() * 6.28; g.rad = 0.3 + R() * 0.25; g.sp = 3 + R() * 2.5; g.hy = (R() - 0.5) * 0.4;
    return g;
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const wind = (k = 1) => {
      r('corpo', 0, 0.05 * Math.sin(t * 0.7), 0.04 * Math.sin(t * 0.9) * k);
      r('bracoD', 0, 0, 0.05 * Math.sin(t * 1.3) * k); r('bracoE', 0, 0, -0.05 * Math.sin(t * 1.1 + 1) * k);
      r('chapeu', 0.04 * Math.sin(t * 1.7) * k, 0, 0.03 * Math.sin(t * 1.4));
    };
    const chest = () => { obj.updateMatrixWorld(true); return new THREE.Vector3(-0.05, -6, -2.8).applyMatrix4(P.corpo.obj.matrixWorld); };
    const crows = (n) => { const out = []; for (let i = 0; i < n; i++) { const c = ctx.get('corvo' + i, () => crow(i)); c.visible = true; out.push(c); } return out; };

    if (mode === 'parado') {
      // fica plantado balançando no vento... e de tempos em tempos a cabeça vira de estalo pra você, devagar volta
      wind(1);
      const snap = seq([[3.3, 0], [3.4, 1, 'snap'], [4.8, 1], [5.9, 0, 'io']], t % 6);
      r('cabeca', 0.1 * snap, -0.9 * snap + 0.05 * Math.sin(t * 0.5), 0.35 * snap);
      // um corvo pousado no braço, bicando
      const c = crows(1)[0]; obj.updateMatrixWorld(true);
      c.position.copy(new THREE.Vector3(9, -10.6, 0).applyMatrix4(P.corpo.obj.matrixWorld)); c.position.y += 0.02;
      c.rotation.set(0.3 * Math.max(0, Math.sin(t * 5)) * (Math.sin(t * 0.7) > 0 ? 1 : 0), 1.2 + 0.4 * Math.sin(t * 0.6), 0);
      for (const w of c.wings) w.rotation.z = w.s * -0.1;
      return;
    }
    if (mode === 'andar') {
      // pula no poste: agacha (o poste entorta), sobe, as mangas sacodem, cai e balança até parar
      const ph = (t * 1.3) % 1;
      const up = ph < 0.5 ? Math.sin(Math.PI * ph / 0.5) : 0;
      obj.position.y += 0.55 * up; obj.position.z -= 0.6 * ((t * 1.3) % 1) - 0.3;
      const land = Math.max(0, ph - 0.5);
      r('poste', 0.12 * Math.sin(land * 18) * Math.exp(-land * 6) - 0.08 * up, 0, 0);
      r('bracoD', 0, 0, -0.3 * up + 0.15 * Math.sin(land * 20) * Math.exp(-land * 5)); r('bracoE', 0, 0, 0.3 * up - 0.15 * Math.sin(land * 20) * Math.exp(-land * 5));
      r('cabeca', 0.15 * up, 0, 0.1 * Math.sin(land * 14) * Math.exp(-land * 5));
      r('chapeu', -0.25 * up, 0, 0);
      if (land > 0 && land < 0.25) ctx.get('poeira', () => DBFX.grains({ n: 30, seed: 7, palette: [[150, 130, 96], [120, 104, 80], [214, 180, 92]], size: [0.03, 0.07], drag: 4, grav: 1, life: 0.6, cone: R2 => { const a2 = R2() * 6.28; return [Math.cos(a2) * 1.6, 0.4 + R2() * 0.8, Math.sin(a2) * 1.6]; } })).at(new THREE.Vector3(0, 0.02, obj.position.z), land, 0);
      return;
    }
    if (mode === 'revoada') {
      const T = t % 4.6;
      const R = ctx.ref; const RZ = -2.6; R.place(0, RZ, 0); R.spin(Math.PI);
      // abre os braços, o peito estufa, a cabeça cai pra trás e os corvos explodem do buraco; rodam na cabeça do alvo
      const open = seq([[0.1, 0], [0.6, 1, 'out'], [3.6, 1], [4.2, 0, 'io']], T);
      r('bracoD', 0, 0, -0.35 * open); r('bracoE', 0, 0, 0.35 * open);
      r('cabeca', -0.6 * open, 0, 0.2 * open);
      r('corpo', -0.15 * open, 0, 0);
      const burst = seq([[0.55, 0], [0.7, 1, 'out'], [0.9, 0]], T);
      P.corpo.obj.scale.set(1 + 0.08 * burst, 1, 1 + 0.12 * burst);
      const head = new THREE.Vector3(0, 1.75, RZ), from = chest();
      const N = 7, cs = crows(N);
      cs.forEach((c, i) => {
        const go = FX.clamp01((T - 0.65 - i * 0.06) / 0.5);
        const back = FX.clamp01((T - 3.3 - i * 0.05) / 0.7);
        const ang = c.ph + t * c.sp;
        const ring = head.clone().add(new THREE.Vector3(Math.cos(ang) * c.rad, c.hy + 0.08 * Math.sin(t * 7 + i), Math.sin(ang) * c.rad));
        // de vez em quando um mergulha e bica (encosta na cara)
        const peck = Math.max(0, Math.sin(t * 4 + i * 2.1)) ** 6;
        ring.lerp(head.clone().add(new THREE.Vector3(0, 0, -0.28)), peck * 0.8 * (back > 0 ? 0 : 1));
        let p = from.clone().lerp(ring, FX.EASE.out(go)); p.y += 0.6 * Math.sin(Math.PI * go);
        p = p.lerp(from, FX.EASE.in(back));
        c.position.copy(p); c.visible = go > 0 && back < 1;
        c.lookAt(ring.clone().add(new THREE.Vector3(-Math.sin(ang), 0, Math.cos(ang))));
        c.rotateY(Math.PI);
        c.flap(t);
        c.scale.setScalar(Math.min(1, go * 3));
      });
      if (T > 0.6 && T < 1.6) ctx.get('penas', () => DBFX.grains({ n: 50, seed: 11, palette: [[20, 18, 24], [34, 30, 40], [214, 180, 92], [190, 150, 70]], size: [0.03, 0.08], drag: 2.5, grav: 0.6, life: 1.4, spread: 0.2, cone: R2 => [(R2() - 0.5) * 2, R2() * 1.5, -R2() * 2] })).at(from, T - 0.6, 0.02);
      // alvo: braços na cara se protegendo, cego (véu escuro com penas)
      const cover = seq([[0.9, 0], [1.2, 1, 'out'], [3.4, 1], [3.8, 0]], T);
      R.face(cover, true); R.lean(-0.1 * cover + 0.05 * Math.sin(t * 9) * cover);
      ctx.blind(0.55 * cover * (0.7 + 0.3 * Math.sin(t * 6)));
      return;
    }
    if (mode === 'abraco') {
      const T = t % 4.0;
      const R = ctx.ref; const RZ = -1.1; R.place(0, RZ, 0); R.spin(Math.PI);
      // pula até colado no alvo, fecha as duas meias-traves na frente dele como uma porta e aperta; palha espeta
      const hop = seq([[0, 0], [0.5, 1, 'io']], T);
      obj.position.z += 1.2 * (1 - hop); obj.position.y += 0.5 * Math.sin(Math.PI * hop);
      const close = seq([[0.6, 0], [0.85, 1, 'in'], [2.9, 1], [3.4, 0, 'io']], T);
      r('bracoD', 0, -1.35 * close, 0); r('bracoE', 0, 1.35 * close, 0);
      const sq = close * Math.max(0, Math.sin(T * 9)) * (T > 1 && T < 2.9 ? 1 : 0);
      r('bracoD', 0, -0.08 * sq, 0); r('bracoE', 0, 0.08 * sq, 0);
      r('cabeca', 0.35 * close, 0, -0.25 * close);
      R.armsOut(-0.15 * close); R.lean(0.06 * Math.sin(T * 9) * close);
      if (T > 0.85 && T < 1.0) { ctx.flash(new THREE.Vector3(0, 1.1, RZ), 1 - (T - 0.85) / 0.15); ctx.shake(0.5); }
      // espetadas: lascas de palha voando pra fora a cada aperto
      if (T > 1 && T < 2.9) ctx.get('lasca', () => DBFX.grains({ n: 40, seed: 21, palette: [[214, 180, 92], [190, 150, 70], [230, 204, 120], [140, 30, 30]], size: [0.02, 0.06], drag: 3, grav: 3, life: 0.5, spread: 1.6, cone: R2 => { const a2 = R2() * 6.28; return [Math.cos(a2) * 2, R2() * 1.5, Math.sin(a2) * 2]; } })).at(new THREE.Vector3(0, 1.0, RZ), (T - 1) * 0.9, 0);
      return;
    }
    if (mode === 'morte') {
      const { T, fall } = JJFX.exorcize(t, obj, e, ctx, { h: 2.2, refZ: -2.0, rotX: 1.4, back: 0.4, ashY: 0.3, ashZ: 1.0, n: 150 });
      r('bracoD', 0, 0, 0.3 * fall); r('bracoE', 0, 0, -0.5 * fall); r('cabeca', 0.3 * fall, 0, 0.4 * fall);
      r('chapeu', -0.8 * fall, 0, 0.4 * fall); if (P.chapeu) P.chapeu.obj.position.y -= 6 * fall;
      // os corvos que moravam no peito fogem
      if (T > 0.4) crows(4).forEach((c, i) => { const f = (T - 0.4 - i * 0.1) * 1.2; c.visible = f > 0 && f < 2.2; c.position.set(-0.3 + i * 0.25 + f * (i - 1.5) * 0.6, 0.4 + f * 1.4, 0.5 + f * 0.8); c.rotation.set(-0.5, (i - 1.5) * 0.5 + Math.PI, 0); c.flap(t); c.scale.setScalar(1); });
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(4, { hat: true, rag: RAGS[0], tear: true }), variant(23, { hat: true, rag: RAGS[1] }), variant(48, { hat: false, rag: RAGS[2] })];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'espantalho', nome: 'Espantalho de Palha', bioma: 'Aldeia Amaldiçoada', tier: 'Grau 4 · tier 9',
    variants: fixed.map((v, i) => ({ nome: ['Quimono Vermelho', 'Quimono Azul', 'Sem Chapéu'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Pular'], ['revoada', 'Revoada'], ['abraco', 'Abraço de Palha'], ['morte', 'Exorcizado']],
    cam: 5.0, lookY: 1.2, lookZ: { revoada: -1.2, abraco: -0.5, morte: 0 }, camFar: { revoada: 5.8, abraco: 4.8 }, camYaw: { revoada: -1.1, abraco: -1.2 },
    info: {
      'Tamanho': '2 blocos (com o chapéu), trave de 1,5 bloco de ponta a ponta',
      'Vida': '18 (palha: fogo faz o triplo)',
      'Jeito': 'Fica fincado nas plantações da aldeia igual espantalho de verdade. A cabeça vira de estalo pra você quando você não está olhando direto. Anda pulando no poste.',
      'Revoada': 'Abre os braços e uns 7 corvos saem do buraco do peito, rodam na sua cabeça e bicam: tela escurece e piscando por 3 s, dano baixo a cada bicada.',
      'Abraço de Palha': 'Pula colado em você e fecha as duas metades da trave na sua frente: preso 2 s levando espetada de palha. Fogo solta na hora.',
      'Solta': 'Palha, Pano Velho; raro: Corvo Amaldiçoado (pet que avisa quando tem maldição perto).',
      'Variações': 'Cor do quimono, com ou sem chapéu, remendo, cabeça mais torta ou menos.',
    },
  });
})();
