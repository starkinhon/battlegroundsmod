// Larva da Colônia (Colônia). Tier 8. Maldição que o Kenjaku soltou nas colônias do Jogo do Abate pra caçar quem não
// pontua: uma larva do tamanho de um porco, branca e mole, em gomos, com uma boca redonda de humano na frente
// (fileiras de dentes em espiral) e pontos pretos que parecem olhos ao longo do corpo. Por baixo, dezenas de
// perninhas curtas. Por dentro dos gomos dá pra ver alguma coisa escura mexendo.
// Golpes: Bote (encolhe como mola, pula e gruda em você mastigando) e Casulo (cospe fios de seda que prendem você
// no chão, depois vem rastejando).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  // [nome, pele, gomo escuro, boca]
  const KINDS = [
    ['Branca', [232, 224, 204], [150, 130, 116], [150, 40, 50]],
    ['Cinza de Esgoto', [170, 168, 150], [96, 92, 80], [110, 30, 40]],
    ['Rosada', [236, 190, 186], [170, 110, 110], [170, 30, 50]],
  ];
  const N = 6;
  function variant(seed, kind) {
    const R = FX.rnd(seed);
    const k = kind ?? Math.floor(R() * KINDS.length);
    const segs = []; for (let i = 0; i < N; i++) segs.push({ w: 8.4 - Math.abs(i - 1.6) * 0.9 + R() * 0.6, h: 7.2 - Math.abs(i - 1.6) * 0.75 + R() * 0.5, dots: R() < 0.7 });
    return { seed, kind: k, skin: KINDS[k][1], dark: KINDS[k][2], mouth: KINDS[k][3], segs, size: 0.9 + R() * 0.2 };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.74, hi = 1.16) => {
      const f = 0.62 - 0.3 * q.ny + 0.22 * (n3(q.ax * 0.4, q.ay * 0.4, q.az * 0.4, s + S) - 0.5) + (q.f === 'top' ? 0.22 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const P = {};
    // gomo: pele brilhante translúcida, dobras nas pontas, veias e a sombra de algo se mexendo por dentro;
    // "olhos" pretos (espiráculos) dos lados
    const seg = (q, s, dots) => {
      let c = shade(v.skin, q, s);
      if (q.nz < 0.1 || q.nz > 0.9) c = mul(c, 0.82);
      if (Math.abs(n3(q.ax * 0.8, q.ay * 0.8, q.az * 0.8, s + 1 + S) - 0.5) < 0.02) c = mix(c, [190, 90, 110], 0.5);
      if (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + 2 + S) > 0.66) c = mix(c, v.dark, 0.35);
      if (q.f === 'top' && n3(q.ax * 2, 0, q.az * 2, s + 3 + S) > 0.8) c = mix(c, [255, 255, 250], 0.5);   // brilho de pele molhada
      if (dots && (q.f === 'east' || q.f === 'west')) { const d = Math.hypot(q.nz - 0.5, q.ny - 0.4); if (d < 0.12) return d < 0.06 ? [10, 8, 8] : [60, 40, 40]; }
      return c;
    };
    v.segs.forEach((g, i) => { P['seg' + i] = q => seg(q, 10 + i, g.dots); });
    // boca: anéis de dentes em espiral entrando na garganta
    P.mouth = q => {
      if (q.f !== 'north') return seg(q, 30, false);
      const x = q.nx - 0.5, y = q.ny - 0.5, r = Math.hypot(x, y) * 2, a = Math.atan2(y, x);
      if (r > 0.92) return mul(v.skin, 0.75);
      if (r > 0.78) return mix(v.mouth, v.skin, 0.4);
      const ring = fr(r * 3.2 - a / (Math.PI * 2));
      if (ring < 0.3 && fr(a * 4 / Math.PI + r * 2) < 0.6) return [236, 228, 200];
      return mix([30, 6, 10], v.mouth, r);
    };
    P.leg = q => { let c = shade(mul(v.skin, 0.86), q, 40); if (q.ny > 0.7) c = mul(v.dark, 0.9); return c; };
    P.silk = q => shade([240, 238, 230], q, 41, 0.85, 1.1);
    return P;
  }

  function rig(P, v, lo) {
    // cadeia de gomos: cada um é filho do anterior (a larva ondula passando a rotação pra frente)
    let node = null;
    for (let i = N - 1; i >= 0; i--) {
      const g = v.segs[i];
      const legs = lo || i === 0 ? [] : [-1, 1].map(s => ({ name: 'pe' + i + (s < 0 ? 'D' : 'E'), pivot: [s * (g.w / 2 - 1), 0, -1.6], boxes: [{ x: -0.6, y: 0, z: -0.6, w: 1.2, h: 2.2, d: 1.2, p: P.leg }] }));
      const n = {
        name: 'seg' + i, pivot: i === 0 ? [0, 0, 0] : [0, 0, 4.6],
        boxes: [{ x: -g.w / 2, y: -g.h, z: -0.2, w: g.w, h: g.h, d: 5.0, p: P['seg' + i] }],
        kids: [...legs, ...(node ? [node] : [])],
      };
      node = n;
    }
    // cabeça: boca na frente do primeiro gomo
    const head = {
      name: 'cabeca', pivot: [0, -3.6, 0], boxes: [{ x: -3.8, y: -3.6, z: -2.8, w: 7.6, h: 7.2, d: 3.0, p: P.mouth }],
      kids: [{ name: 'boca', pivot: [0, 0, -2.8], boxes: lo ? [] : [{ x: -3.4, y: -3.2, z: -0.5, w: 6.8, h: 0.6, d: 0.5, p: P.mouth }] }],
    };
    node.kids.push(head);
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{ name: 'corpo', pivot: [0, 21.8, -13], boxes: [], kids: [node] }],
    };
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const W = (n, x, y, z) => { obj.updateMatrixWorld(true); return new THREE.Vector3(x, y, z).applyMatrix4(P[n].obj.matrixWorld); };
    // onda que passa do rabo pra boca (ondulação de larva), amplitudes em x (subir) e y (lado)
    const wave = (ph, ax, ay) => { for (let i = 1; i < N; i++) { r('seg' + i, ax * Math.sin(ph - i * 0.9), ay * Math.sin(ph * 0.5 - i * 0.7), 0); } };
    const legs = (ph, k) => { for (let i = 1; i < N; i++) for (const sd of ['D', 'E']) r('pe' + i + sd, 0.5 * k * Math.sin(ph * 2 + i * 1.3 + (sd === 'D' ? 0 : Math.PI)), 0, 0); };
    // levanta só a frente: o gomo 0 é pai de todos, então os seguintes desfazem a rotação
    const raise = k => { r('seg0', -k, 0, 0); r('seg1', 0.65 * k, 0, 0); r('seg2', 0.35 * k, 0, 0); };
    const chew = k => { if (P.boca) P.boca.obj.position.y += 0.8 * k; if (P.cabeca) P.cabeca.obj.scale.set(1 + 0.08 * k, 1 + 0.08 * k, 1); };
    const silkLine = (key, a2, b2, k, o) => ctx.get(key, () => JJFX.tube(0xeeeadf, 0.018, 0.012)).at(a2, b2, k, o);

    if (mode === 'parado') {
      // pulsa mole, a cabeça levanta e cheira o ar, as perninhas não param
      const br = loop([[0, 0], [0.5, 1, 'io'], [1, 0, 'io']], t / 2.2);
      for (let i = 0; i < N; i++) if (P['seg' + i]) P['seg' + i].obj.scale.set(1 + 0.05 * Math.sin(t * 3 - i), 1 + 0.05 * Math.sin(t * 3 - i), 1);
      raise(0.2 + 0.15 * br); r('seg0', 0, 0.3 * Math.sin(t * 0.8), 0);
      wave(t * 1.4, 0.04, 0.08); legs(t * 3, 0.6);
      chew(0.5 * Math.max(0, Math.sin(t * 5)) * (fr(t / 2.5) < 0.4 ? 1 : 0));
      return;
    }
    if (mode === 'andar') {
      // rasteja: o corpo estica e encolhe em onda (lagarta-mede-palmo baixo)
      const ph = t * 4;
      wave(ph, 0.22, 0.12); legs(ph, 1);
      obj.position.z += -0.3 * fr(t * 0.4) * 2;
      r('seg0', 0, 0.15 * Math.sin(ph * 0.5), 0);
      return;
    }
    if (mode === 'bote') {
      const T = t % 4.0;
      const R = ctx.ref; const RZ = -2.6; R.place(0, RZ, 0); R.spin(Math.PI);
      // encolhe pra trás como mola (gomos se espremem e a boca abre em flor), pula num arco e gruda no peito;
      // fica mastigando e balançando o corpo pendurado
      const coil = seq([[0.2, 0], [0.9, 1, 'in'], [1.0, 0, 'snap']], T);
      for (let i = 1; i < N; i++) if (P['seg' + i]) P['seg' + i].obj.position.z -= 1.4 * coil;
      raise(0.5 * coil);
      const jump = FX.clamp01((T - 1.0) / 0.35);
      const held = T > 1.35 && T < 3.3;
      if (T > 1.0 && T < 1.35) {
        obj.position.z += (RZ + 0.6) * FX.EASE.out(jump); obj.position.y += 1.0 * Math.sin(Math.PI * jump * 0.9) + 0.5 * jump;
        P.corpo.obj.rotation.x += -0.9 * jump;
        wave(t * 20, 0.1, 0);
      } else if (held) {
        // pendurado no peito: boca no alvo, rabo balançando pra baixo
        obj.position.z += RZ + 0.9; obj.position.y += 0.75;
        P.corpo.obj.rotation.x += -1.1;
        wave(t * 6, 0.18, 0.2);
        chew(Math.max(0, Math.sin(t * 14)));
        R.lean(0.2 + 0.05 * Math.sin(t * 14)); R.armsOut(0.6); R.punch(0.3 * Math.max(0, Math.sin(t * 14)));
      } else if (T >= 3.3) {
        const k = FX.clamp01((T - 3.3) / 0.5);
        obj.position.z += (RZ + 0.6) * (1 - k); obj.position.y += 0.52 * (1 - k) + 0.4 * Math.sin(Math.PI * k);
        P.corpo.obj.rotation.x += -1.1 * (1 - k);
      }
      if (T > 1.33 && T < 1.5) { ctx.flash(new THREE.Vector3(0, 1.2, RZ), 0.7); ctx.shake(0.4); }
      if (held) ctx.get('gosma', () => DBFX.grains({ n: 24, seed: 61, palette: [[230, 226, 200], [200, 210, 160], [150, 40, 50]], size: [0.02, 0.04], drag: 1, grav: 7, life: 0.6, spread: 1.0, cone: R2 => [(R2() - 0.5) * 1, R2() * 0.8, 0.4 + R2()] })).at(new THREE.Vector3(0, 1.15, RZ + 0.25), (T - 1.35) % 1.2, 0.02);
      chew(coil);
      return;
    }
    if (mode === 'casulo') {
      const T = t % 5.0;
      const R = ctx.ref; const RZ = -3.0; R.place(0, RZ, 0); R.spin(Math.PI);
      // levanta a frente, a boca se abre e solta jatos de seda que grudam no alvo e no chão em volta; o alvo
      // fica enrolado (fios cruzando o corpo) e a larva vem rastejando
      const rear = seq([[0.2, 0], [0.8, 1, 'io'], [2.2, 1], [2.7, 0, 'io']], T);
      raise(0.8 * rear); r('cabeca', 0.3 * rear, 0, 0);
      const spit = seq([[0.9, 0], [1.0, 1, 'snap'], [2.0, 1], [2.2, 0]], T);
      chew(spit);
      if (spit > 0) r('cabeca', 0.08 * Math.sin(t * 30), 0, 0);
      const mouth = W('cabeca', 0, 0, -3.2);
      const anchors = [[-0.5, 0, RZ - 0.4], [0.55, 0, RZ - 0.2], [-0.35, 0, RZ + 0.5], [0.45, 0, RZ + 0.55], [0, 0, RZ - 0.7]];
      anchors.forEach(([x, y, z], i) => {
        const t0 = 1.0 + i * 0.16, k = seq([[t0, 0], [t0 + 0.2, 1, 'out'], [4.2, 1], [4.7, 0]], T);
        const body = new THREE.Vector3(0.12 * (i % 2 ? 1 : -1), 0.5 + 0.25 * i, RZ);
        if (T < 2.2) silkLine('jato' + i, mouth, body, k, { arc: 0.25, wiggle: 0.04, t: T + i });
        silkLine('fio' + i, body, new THREE.Vector3(x, 0.02, z), k, { arc: 0.08, t: i });
      });
      // voltas em volta do corpo
      for (let j = 0; j < 4; j++) {
        const k = seq([[1.3 + j * 0.15, 0], [1.5 + j * 0.15, 1, 'out'], [4.2, 1], [4.7, 0]], T), y = 0.45 + j * 0.32;
        silkLine('volta' + j, new THREE.Vector3(-0.3, y, RZ - 0.05), new THREE.Vector3(0.3, y + 0.12, RZ - 0.05), k, { arc: -0.12, t: j });
        silkLine('voltaB' + j, new THREE.Vector3(-0.3, y + 0.06, RZ + 0.02), new THREE.Vector3(0.3, y + 0.16, RZ + 0.02), k, { arc: 0.12, t: j });
      }
      const stuck = seq([[1.3, 0], [1.7, 1], [4.2, 1], [4.6, 0]], T);
      R.armsOut(-0.25 * stuck); R.lean(0.06 * stuck * Math.sin(T * 11));
      const crawl = seq([[2.5, 0], [4.2, 1, 'io'], [4.9, 0, 'io']], T);
      obj.position.z += (RZ + 1.4) * crawl * 0.7;
      if (T > 2.5 && T < 4.2) { wave(t * 4, 0.2, 0.1); legs(t * 4, 1); }
      return;
    }
    if (mode === 'morte') {
      // leva o golpe, se contorce de costas (os pezinhos pro alto) e estoura numa gosma que vira cinza
      const T = t % 3.6;
      const R = ctx.ref; R.place(0, -2.2, 0); R.spin(Math.PI);
      R.punch(seq([[0.15, 0], [0.25, 1, 'snap'], [0.6, 1], [0.9, 0]], T));
      const flip = seq([[0.25, 0], [0.6, 1, 'out']], T);
      P.corpo.obj.rotation.z += Math.PI * flip; obj.position.y += 0.42 * flip;
      if (T > 0.3 && T < 1.6) { wave(t * 12, 0.35 * (1.6 - T), 0.3 * (1.6 - T)); legs(t * 8, 1.5); }
      const dis = FX.clamp01((T - 1.3) / 1.5);
      JJFX.dissolve(e, dis, 0, 1.0);
      if (T > 1.3) ctx.get('cinza', () => JJFX.ash(31, 90)).at(new THREE.Vector3(0, 0.2, -0.3), T - 1.3, 0.02);
      if (T > 0.22 && T < 0.4) ctx.flash(new THREE.Vector3(0, 0.6, -0.8), 1 - (T - 0.22) / 0.18);
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(9, 0), variant(30, 1), variant(52, 2)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'larva', nome: 'Larva da Colônia', bioma: 'Colônia', tier: 'Grau 3 · tier 8',
    variants: fixed.map(v => ({ nome: KINDS[v.kind][0], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteada (' + KINDS[v.kind][0] + ')', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Rastejar'], ['bote', 'Bote'], ['casulo', 'Casulo'], ['morte', 'Exorcizada']],
    cam: 4.0, lookY: 0.6, lookZ: { parado: 0.3, andar: 0.3, bote: -1.0, casulo: -1.4, morte: 0.2 }, camFar: { bote: 5.6, casulo: 6.4, morte: 4.6 }, camYaw: { bote: -1.2, casulo: -1.0, morte: -1.0 },
    info: {
      'Tamanho': '2 blocos de comprimento, meio de altura',
      'Vida': '30 (mole: soco e corte fazem dano normal, fogo faz dobrado)',
      'Jeito': 'Bandos de 3 a 6 nas ruas destruídas e nos prédios da Colônia, mais em volta de quem tem poucos pontos. Rasteja rápido pra o tamanho e sobe parede devagar.',
      'Bote': 'Encolhe como mola (0,8 s de aviso) e pula 2,5 blocos: gruda no seu peito e fica mastigando (dano contínuo) até você bater nela ou se jogar no chão.',
      'Casulo': 'Levanta a frente e cospe fios de seda: você fica preso no lugar 2 s (pular quebra os fios mais rápido). Depois ela vem rastejando.',
      'Fraqueza': 'De barriga pra cima não consegue virar rápido: golpe que derruba deixa ela 2 s indefesa.',
      'Solta': 'Seda Amaldiçoada (vira corda ou teia), Gosma; raro: Casulo da Colônia (abre e solta uma Larva amiga).',
      'Variações': 'Branca, cinza de esgoto ou rosada; tamanho dos gomos e quantos "olhos" tem dos lados.',
    },
  });
})();
