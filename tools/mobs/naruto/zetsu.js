// Zetsu Branco (Vale do Fim). Médio, tier 8. Corpo branco de planta, sai e entra no chão, anda em bando.
// Golpes: Agarrão do chão (some na terra, brota na sua frente e suga a estamina segurando você pelos ombros)
// e Esporos (solta clones-esporo pequenos que grudam em você, incham e sugam chakra até estourar).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const N = window.NINJA;

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.84, hi = 1.06) => {
      const f = 0.62 - 0.4 * q.ny + 0.35 * (n3(q.ax * 0.25, q.ay * 0.25, q.az * 0.25, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    // pele de planta: branco com fibras finas verticais e manchas esverdeadas bem leves
    const skin = (q, s = 1) => {
      let c = shade(v.skin, q, s);
      if (fr(q.ax * 0.9 + q.az * 0.9 + 0.5 * n3(q.ax * 0.3, q.ay * 0.05, q.az * 0.3, s + 70)) < 0.07) c = mul(c, 0.92);
      if (n3(q.ax * 0.4, q.ay * 0.4, q.az * 0.4, s + 71) > 0.72) c = mix(c, v.tint, 0.35);
      return c;
    };
    const P = v.disguise ? N.painters(v) : {};
    const none = () => null;
    P.skin = q => skin(q);
    // rosto: olhos redondos sem brilho, sorriso largo com dentes
    P.head = q => {
      if (q.f === 'top') { let c = skin(q, 2); if (Math.abs(q.nx - 0.5) < 0.06) c = mul(c, 0.9); return c; }  // vinco no alto da cabeça
      if (q.f !== 'north') return skin(q, 2);
      const x = q.px, y = q.py;
      let c = skin(q, 2);
      for (const ex of [1.9, 5.1]) {
        const d = Math.hypot(x - ex, (y - 3.4) * 1.1);
        if (d < 1.05) c = [24, 22, 20];
        if (d < 1.05 && d > 0.8) c = [70, 66, 58];
      }
      if (y > 4.9 && y < 5.9 && x > 1.1 && x < 5.9) {
        c = [40, 30, 30];
        if (y < 5.45 && fr(x * 1.4) < 0.6) c = [236, 232, 214];                                     // dentes de cima
        if (y > 5.5 && fr(x * 1.4 + 0.3) < 0.45) c = [220, 214, 196];                                // de baixo
      }
      if (y > 4.5 && y < 4.9 && (Math.abs(x - 1.1) < 0.35 || Math.abs(x - 5.9) < 0.35)) c = mul(c, 0.8); // canto da boca
      return c;
    };
    P.upperArm = q => skin(q, 3); P.wrapArm = q => skin(q, 3); P.glove = q => skin(q, 4);
    P.shinWrap = q => skin(q, 5); P.sandal = q => { let c = skin(q, 6); if (q.f === 'north' && fr(q.px * 1.2) < 0.18) c = mul(c, 0.82); return c; }; // dedos
    P.lowArm = q => skin(q, 3);
    if (!v.disguise) {
      P.torso = q => { let c = skin(q, 7); if (q.f === 'north' && Math.abs(q.ny - 0.35) < 0.03 && Math.abs(q.nx - 0.5) < 0.3) c = mul(c, 0.9); return c; };
      P.pants = q => skin(q, 8); P.belt = q => skin(q, 8); P.collar = q => skin(q, 7); P.sleeve = q => skin(q, 3);
      P.hairTop = none; P.hairBack = none; P.band = none; P.plate = none; P.pouch = none; P.pouchV = none; P.holster = none;
      P.scarfTail = none; P.scarf = q => skin(q, 7);
      P.lowTorso = q => skin(q, 7); P.lowLeg = q => (q.ay > 23.2 ? P.sandal(q) : skin(q, 8)); P.lowHair = none;
    } else {
      // disfarçado de shinobi da Aliança: roupa copiada, mas esqueceu de copiar a cara
      P.hairTop = none; P.hairBack = none;
      P.lowHair = none;
    }
    return P;
  }
  const defOf = v => N.rigDef(painters(v), v, { noTails: true });
  const lowOf = v => N.rigLow(painters(v), v);

  // fios de chakra verde saindo do alvo e entrando no Zetsu
  function chakraFlow(n, seed) {
    const R = FX.rnd(seed), g = new THREE.Group(), list = [];
    const mat = new THREE.MeshBasicMaterial({ color: 0x8cf0a0, transparent: true, opacity: 0.85, blending: THREE.AdditiveBlending, depthWrite: false });
    for (let i = 0; i < n; i++) {
      const m = new THREE.Mesh(new THREE.BoxGeometry(0.035, 0.035, 0.035), mat);
      g.add(m); list.push({ m, o: R(), w: R() * 6.28, amp: 0.05 + R() * 0.12, sp: 0.8 + R() * 0.6 });
    }
    g.at = (a, b, t, k) => {
      g.visible = k > 0.01;
      const mid = a.clone().lerp(b, 0.5); mid.y += 0.25;
      for (const p of list) {
        const f = fr(t * p.sp + p.o);
        const u = 1 - f;
        const pos = a.clone().multiplyScalar(u * u).add(mid.clone().multiplyScalar(2 * u * f)).add(b.clone().multiplyScalar(f * f));
        pos.x += Math.sin(p.w + f * 9) * p.amp * (1 - f); pos.y += Math.cos(p.w + f * 7) * p.amp * (1 - f);
        p.m.position.copy(pos); p.m.scale.setScalar(k * (0.6 + Math.sin(Math.PI * f)));
      }
    };
    return g;
  }
  // esporos: bolinhas brancas com cara, voam em arco até o alvo, grudam, incham e estouram
  function spores(n, seed) {
    const R = FX.rnd(seed), g = new THREE.Group(), list = [];
    const white = new THREE.MeshLambertMaterial({ color: 0xe8e8de }), black = new THREE.MeshBasicMaterial({ color: 0x181614 });
    for (let i = 0; i < n; i++) {
      const s = new THREE.Group();
      const b = new THREE.Mesh(new THREE.BoxGeometry(0.15, 0.15, 0.15), white); s.add(b);
      for (const ex of [-0.025, 0.025]) { const e = new THREE.Mesh(new THREE.BoxGeometry(0.018, 0.018, 0.01), black); e.position.set(ex * 1.4, 0.016, -0.076); s.add(e); }
      g.add(s);
      // onde gruda no corpo do alvo (frente, ombros, braços)
      const spot = new THREE.Vector3((R() - 0.5) * 0.5, 0.75 + R() * 0.75, 0.24 + R() * 0.05);
      list.push({ s, spot, lag: i * 0.06 + R() * 0.05, arc: 0.4 + R() * 0.6, side: (R() - 0.5) * 1.2, swell: 0.8 + R() * 0.6 });
    }
    g.at = (from, refPos, age, pop) => {
      g.visible = true;
      for (const p of list) {
        const f = FX.clamp01((age - p.lag) / 0.45);
        const tgt = refPos.clone().add(p.spot);
        p.s.visible = f > 0 && pop < 1;
        if (!p.s.visible) continue;
        const pos = from.clone().lerp(tgt, f);
        pos.y += Math.sin(Math.PI * f) * p.arc; pos.x += Math.sin(Math.PI * f) * p.side;
        p.s.position.copy(pos);
        p.s.lookAt(f < 1 ? tgt : refPos.clone().add(new THREE.Vector3(p.spot.x, p.spot.y, -2)));
        const sw = f < 1 ? 1 : 1 + p.swell * FX.clamp01((age - p.lag - 0.45) / 1.6);
        p.s.scale.setScalar(sw);
      }
    };
    return g;
  }

  function anim(P, t, mode, a, obj, ent, ctx) {
    const r = (k, x, y, z) => { if (!P[k]) return; const o = P[k].obj.rotation; o.x += x; o.y += y; o.z += z; };
    // postura: meio curvado, braços soltos, cabeça um pouco de lado (fixa, sem balançar)
    const slouch = () => {
      r('quadril', 0.06, 0, 0); r('tronco', 0.22, 0, 0); r('cabeca', -0.3, 0, 0.18);
      r('bracoD', 0.15, 0, 0.08); r('bracoE', 0.15, 0, -0.08); r('anteD', -0.2, 0, 0); r('anteE', -0.2, 0, 0);
      r('pernaD', -0.12, 0, 0); r('canelaD', 0.2, 0, 0); r('pernaE', -0.05, 0, 0); r('canelaE', 0.12, 0, 0);
    };
    if (mode === 'parado') {
      slouch();
      // de vez em quando vira a cabeça num estalo e segura olhando
      const look = seq([[0, 0], [0.42, 0], [0.45, 1, 'snap'], [0.8, 1], [0.83, 0, 'snap']], fr(t * 0.22));
      r('cabeca', 0, 0.55 * look, -0.1 * look);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 0.9;
      slouch();
      for (const [s, off] of [['D', 0], ['E', 0.5]]) { r('perna' + s, loop(N.WALK_T, ph + off), 0, 0); r('canela' + s, loop(N.WALK_K, ph + off), 0, 0); }
      P.quadril.obj.position.y += loop(N.WALK_B, ph);
      r('bracoD', 0.6 * loop(N.WALK_A, ph), 0, 0); r('bracoE', 0.6 * loop(N.WALK_A, ph + 0.5), 0, 0);
      return;
    }
    const R = ctx.ref;
    if (mode === 'agarrao') {
      const T = t % 4.0;
      // 1) afunda na terra  2) a terra borbulha na sua frente  3) brota com os braços pra cima e agarra  4) suga  5) solta e afunda
      const sink = seq([[0, 0], [0.35, 1, 'in']], T);
      const rise = seq([[1.0, 0], [1.25, 1, 'out']], T);
      const grab = seq([[1.2, 0], [1.35, 1, 'snap'], [3.0, 1], [3.15, 0]], T);
      const back = seq([[3.2, 0], [3.7, 1, 'in']], T);
      const under = T < 1.0 ? sink : 1 - rise + back;
      const zPos = T < 0.6 ? 0 : -1.1;
      obj.position.z += zPos;
      obj.position.y -= 2.0 * under;
      R.place(0, -2.15, 0);
      slouch();
      r('tronco', -0.2 * grab, 0, 0);
      r('bracoD', -2.5 * (1 - grab) * rise + -1.35 * grab, 0, 0.1); r('bracoE', -2.5 * (1 - grab) * rise + -1.35 * grab, 0, -0.1);
      r('anteD', -0.4 * grab, 0, 0); r('anteE', -0.4 * grab, 0, 0);
      r('cabeca', 0.25 * grab, 0, -0.18 * grab);
      if (T > 0.55 && T < 1.25) ctx.get('borbulha', FX.volume).set(new THREE.Vector3(0, 0.05, -1.1), 0.5, (T - 0.55) / 0.7 * 0.6, { seed: 4.1, dens: 2.4, rise: 0.15, squash: 0.35, freq: 2.4, smoke: [0.36, 0.32, 0.26] });
      if (T > 1.0 && T < 1.5) ctx.get('terra', () => FX.debris(26, 141, [[86, 72, 56], [64, 54, 42], [110, 94, 72]])).at(new THREE.Vector3(0, 0.1, -1.1), T - 1.0);
      if (T > 1.0 && T < 1.2) ctx.shake(0.5);
      if (grab > 0.5) {
        // sugando: o alvo afunda os ombros, o chakra verde sai dele e entra pelas mãos
        const drain = seq([[1.35, 0], [2.9, 1]], T);
        R.lean(0.12 + 0.3 * drain); R.armsOut(0.15);
        ctx.get('fluxo', () => chakraFlow(40, 142)).at(new THREE.Vector3(0, 1.35, -2.05), new THREE.Vector3(0, 1.25, -1.35), T, grab);
        ctx.dizzy(0.25 * drain);
      }
      return;
    }
    if (mode === 'esporos') {
      const T = t % 4.4;
      R.place(0, -3.4, 0);
      // braços abrem, o corpo racha em bolinhas que voam em você e incham sugando; no fim estouram em fibra
      const open = seq([[0.15, 0], [0.55, 1, 'out'], [1.2, 1], [1.5, 0.2]], T);
      slouch();
      r('tronco', -0.4 * open, 0, 0); r('cabeca', 0.25 * open, 0, 0);
      r('bracoD', -0.3 * open, 0, 1.3 * open); r('bracoE', -0.3 * open, 0, -1.3 * open);
      const age = T - 0.55;
      const pop = seq([[3.6, 0], [3.65, 1]], T);
      if (age > 0) {
        ctx.get('esporos', () => spores(12, 143)).at(new THREE.Vector3(0, 1.4, -0.3), new THREE.Vector3(0, 0, -3.4), age, pop);
        const stuck = seq([[1.1, 0], [1.4, 1], [3.6, 1], [3.7, 0]], T);
        if (stuck > 0) {
          ctx.get('fluxo', () => chakraFlow(30, 144)).at(new THREE.Vector3(0, 1.2, -3.3), new THREE.Vector3(0, 1.3, -0.4), T, stuck * 0.8);
          R.lean(0.25 * stuck); R.armsOut(0.3 * stuck + 0.05 * Math.sin(T * 13));
        }
      }
      if (T > 3.6) {
        ctx.get('fibra', () => FX.debris(40, 145, [[236, 234, 222], [214, 216, 200], [190, 200, 176]])).at(new THREE.Vector3(0, 1.2, -3.3), T - 3.6);
        ctx.get('po', FX.volume).set(new THREE.Vector3(0, 1.2, -3.3), 0.6, (T - 3.6) / 0.8, { seed: 5.2, dens: 3, rise: 0.3, smoke: [0.9, 0.9, 0.84] });
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const mk = (seed, disguise) => {
    const v = disguise ? N.variant(seed) : { seed };
    v.seed = seed; v.disguise = disguise; v.scarf = null; v.mask = false;
    v.skin = [232, 232, 222]; v.tint = [196, 214, 184];
    return v;
  };
  const fixed = [mk(31, false), mk(32, true)];
  window.MOBS.push({
    id: 'zetsu_branco', nome: 'Zetsu Branco', bioma: 'Vale do Fim', tier: 'Médio · tier 8',
    variants: fixed.map((v, i) => ({ nome: ['Branco', 'Disfarçado'][i], v, def: defOf(v), low: lowOf(v) })),
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['agarrao', 'Agarrão do chão'], ['esporos', 'Esporos']],
    cam: 6, lookY: 1.2, lookZ: { agarrao: -1.1, esporos: -1.7 }, camFar: { agarrao: 6.5, esporos: 8 }, camYaw: { agarrao: -1.25, esporos: -1.2 },
    info: {
      'Tamanho': '1,85 bloco',
      'Vida': '110 (tier 8)',
      'Jeito': 'Hostil, em bando de 3 a 6. De dia ficam dentro da terra (dá pra ver o chão borbulhar); de noite andam pela superfície.',
      'Agarrão do chão': 'Afunda, a terra borbulha 0,6 s na sua frente e ele brota agarrando seus ombros: 1,6 s preso, a estamina escorre pra ele. Bater nele solta.',
      'Esporos': 'Abre os braços (0,4 s de aviso) e solta 12 esporos que grudam em você: ficam lento e cada esporo grudado suga chakra até estourar. Correr pra água ou girar o corpo (esquiva) derruba eles.',
      'Disfarçado': 'Alguns copiam a roupa de shinobi da Aliança e chegam perto como se fossem amigos. A cara branca entrega.',
      'Solta': 'Fibra de Zetsu (alquimia/ervas), raro: esporo vivo.',
    },
  });
})();
