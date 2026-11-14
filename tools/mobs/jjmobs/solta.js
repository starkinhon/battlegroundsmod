// Maldição Solta (Colônia). Tier 5. Uma das maldições que o Kenjaku soltou no Japão quando abriu o Jogo do Abate:
// uma massa de carne cinza-arroxeada costurada de vários pedaços (a mesma costura da testa dele), andando em seis
// braços humanos como uma aranha. Em cima, a tampa do crânio fica aberta e mostra um cérebro com a costura;
// no rosto principal e nos dois lados do corpo, caras humanas que não combinam entre si. A barriga é uma boca
// vertical que abre como porta.
// Golpes: Mil Unhas (empina nos braços de trás e os quatro da frente rasgam em sequência) e Grito das Caras
// (a barriga abre, todas as caras gritam juntas: tela escurece e roda).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  // [nome, carne, carne 2, costura]
  const KINDS = [
    ['Costurada', [140, 124, 140], [176, 150, 150], [40, 24, 30]],
    ['Afogada', [110, 134, 140], [150, 170, 168], [30, 40, 44]],
    ['Queimada', [120, 88, 80], [170, 120, 96], [30, 18, 14]],
  ];
  const FACE_A = ['.BB..BB.', '........', '.WK..KW.', '........', '...NN...', '........', '.MMMMMM.', '..MMMM..'];
  const FACE_B = ['......', '.K..K.', '......', '..MM..', '.M..M.', '..MM..'];
  const FACE_C = ['......', 'KK..KK', '......', '......', 'MMMMMM', '......'];
  function variant(seed, kind) {
    const R = FX.rnd(seed);
    const k = kind ?? Math.floor(R() * KINDS.length);
    const stitches = []; for (let i = 0; i < 5; i++) stitches.push({ a: R() * Math.PI, o: (R() - 0.5) * 0.8, s: 9 + i * 13 });
    const skins = [[232, 196, 166], [206, 160, 130], [150, 110, 86]].map(c => mix(c, KINDS[k][1], 0.35));
    return { seed, kind: k, flesh: KINDS[k][1], flesh2: KINDS[k][2], stitch: KINDS[k][3], stitches, skins, size: 0.95 + R() * 0.1 };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.7, hi = 1.16) => {
      const f = 0.6 - 0.3 * q.ny + 0.28 * (n3(q.ax * 0.35, q.ay * 0.35, q.az * 0.35, s + S) - 0.5) + (q.f === 'top' ? 0.2 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const P = {};
    // carne: retalhos de tons diferentes separados por costuras em zigue-zague (pontos cruzados)
    const flesh = (q, s) => {
      const patch = n3(q.ax * 0.18, q.ay * 0.18, q.az * 0.18, s + S);
      let c = shade(patch > 0.5 ? v.flesh : v.flesh2, q, s + 1);
      const edge = Math.abs(patch - 0.5);
      if (edge < 0.025) return mul(v.stitch, 1.3);
      if (edge < 0.05 && fr((q.ax + q.ay + q.az) * 0.9) < 0.35) return [210, 200, 180];   // pontos da linha
      if (n3(q.ax * 1.2, q.ay * 1.2, q.az * 1.2, s + 2 + S) > 0.76) c = mix(c, [120, 50, 70], 0.35);
      return c;
    };
    P.flesh = q => flesh(q, 1);
    P.arm = q => { let c = flesh(q, 3); if (q.ny > 0.82) c = mix(c, v.skins[1], 0.6); return c; };
    P.hand = q => { let c = shade(v.skins[(q.ax > 0) ? 0 : 2], q, 4); if (q.f === 'bottom') c = mul(c, 0.8); if (q.nz < 0.15 && q.f !== 'top') c = mix(c, [60, 40, 40], 0.5); return c; };
    // cérebro com a costura do Kenjaku atravessando
    P.brain = q => {
      let c = shade([226, 150, 156], q, 5, 0.8, 1.12);
      const g = n3(q.ax * 0.9, q.ay * 0.9, q.az * 0.9, 6 + S);
      if (Math.abs(g - 0.5) < 0.06) c = mul(c, 0.72);
      if (q.f === 'top' && Math.abs(q.nx - 0.5) < 0.05) return [50, 20, 26];
      if (q.f === 'top' && Math.abs(q.nx - 0.5) < 0.12 && fr(q.nz * 6) < 0.3) return [230, 220, 200];
      return c;
    };
    // tampa do crânio aberta: por fora carne com a costura grande, por dentro osso
    P.cap = q => { if (q.f === 'bottom') return shade([220, 206, 180], q, 7); let c = flesh(q, 8); if (q.f === 'north' && Math.abs(q.ny - 0.7) < 0.08) return mul(v.stitch, 1.2); return c; };
    const face = (map, skin, q, s) => {
      if (q.f !== 'north') return flesh(q, s);
      const n = map.length, col = n - 1 - Math.min(n - 1, Math.floor(q.px * n / q.b.w)), row = Math.min(n - 1, Math.floor(q.py * n / q.b.h));
      const ch = map[row][col];
      let c = shade(skin, q, s + 1, 0.82, 1.1);
      if (ch === 'B') c = [50, 40, 40];
      if (ch === 'W') c = [236, 232, 220];
      if (ch === 'K') c = [16, 10, 10];
      if (ch === 'N') c = mul(skin, 0.82);
      if (ch === 'M') c = [70, 16, 24];
      if (q.px < 0.6 || q.px > q.b.w - 0.6 || q.py < 0.4 || q.py > q.b.h - 0.4) c = mul(v.stitch, 1.3);   // costurada na borda
      return c;
    };
    P.faceA = q => face(FACE_A, v.skins[0], q, 10);
    P.faceB = q => face(FACE_B, v.skins[1], q, 12);
    P.faceC = q => face(FACE_C, v.skins[2], q, 14);
    // portas da boca da barriga: por fora carne, na borda dentes
    P.door = q => { if (q.f === 'east' || q.f === 'west') { if (fr(q.py * 0.6) < 0.5) return [236, 228, 200]; return [60, 10, 18]; } return flesh(q, 16); };
    P.gullet = q => mix([30, 4, 10], [120, 20, 40], n3(q.ax, q.ay, q.az, 17 + S));
    P.tooth = q => shade([236, 228, 200], q, 18, 0.85, 1.1);
    return P;
  }

  function rig(P, v, lo) {
    // seis braços de pernas: três de cada lado, ombro sobe e o antebraço desce até o chão com a mão espalmada
    const arms = [];
    for (let i = 0; i < 3; i++) for (const s of [-1, 1]) {
      const n = (s < 0 ? 'D' : 'E') + i, z = -5 + i * 5;
      arms.push({
        name: 'braco' + n, pivot: [s * 8.4, -5, z], rot: [(i - 1) * -0.35, 0, -s * 1.7],   // achado por busca: mão no chão a 1,45 bloco do centro
        boxes: [{ x: -1.6, y: -0.5, z: -1.6, w: 3.2, h: 12.5, d: 3.2, p: P.arm }],
        kids: [{
          name: 'ante' + n, pivot: [0, 12, 0], rot: [0, 0, s * 1.55],
          boxes: [{ x: -1.4, y: 0, z: -1.4, w: 2.8, h: 17.5, d: 2.8, p: P.arm }],
          kids: [{ name: 'mao' + n, pivot: [0, 17.4, 0], rot: [0, 0, s * 0.15], boxes: [{ x: -1.8, y: 0, z: -2.6, w: 3.6, h: 1.4, d: 5.2, p: P.hand }, ...(lo ? [] : [{ x: -1.6, y: 0.2, z: -4.2, w: 3.2, h: 1, d: 1.8, p: P.hand }])] }],
        }],
      });
    }
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'corpo', pivot: [0, 13, 1], boxes: [
          { x: -9, y: -11, z: -7.4, w: 18, h: 13, d: 15, p: P.flesh },
          { x: -7, y: 2, z: -5.4, w: 14, h: 2.6, d: 11, p: P.flesh },
          { x: -2.8, y: -8.6, z: -7.0, w: 5.6, h: 9.6, d: 0.6, p: P.gullet },
          // caras dos lados do corpo
          { x: -9.6, y: -7.6, z: -3, w: 0.8, h: 5.4, d: 5.4, p: P.flesh },
          { x: 8.8, y: -8.2, z: 1, w: 0.8, h: 5.4, d: 5.4, p: P.flesh },
        ],
        kids: [
          // as duas caras dos lados ficam em grupos virados pra fora (assim a pintura "frente" fica pro lado)
          { name: 'caraB', pivot: [-9.7, -5, -0.3], rot: [0, Math.PI / 2, 0], boxes: [{ x: -2.7, y: -2.7, z: -0.8, w: 5.4, h: 5.4, d: 0.8, p: P.faceB }] },
          { name: 'caraC', pivot: [9.7, -5.5, 3.7], rot: [0, -Math.PI / 2, 0], boxes: [{ x: -2.7, y: -2.7, z: -0.8, w: 5.4, h: 5.4, d: 0.8, p: P.faceC }] },
          { name: 'portaD', pivot: [-3.0, -4, -7.4], boxes: [{ x: -0.2, y: -5.2, z: -0.8, w: 3.2, h: 10.4, d: 1.4, p: P.door }] },
          { name: 'portaE', pivot: [3.0, -4, -7.4], boxes: [{ x: -3.0, y: -5.2, z: -0.8, w: 3.2, h: 10.4, d: 1.4, p: P.door }] },
          {
            name: 'cabeca', pivot: [0, -11, -4.4], boxes: [
              { x: -5, y: -7.6, z: -4.6, w: 10, h: 7.6, d: 9, p: P.flesh },
              { x: -4.2, y: -7.2, z: -5.4, w: 8.4, h: 7.0, d: 1.0, p: P.faceA },
              { x: -3.8, y: -10.4, z: -3.6, w: 7.6, h: 3.0, d: 7.2, p: P.brain },
            ],
            kids: [{ name: 'tampa', pivot: [0, -10.2, 3.8], rot: [0.9, 0, 0], boxes: [{ x: -4.2, y: -1.8, z: -8, w: 8.4, h: 1.8, d: 8, p: P.cap }] }],
          },
          ...arms,
        ],
      }],
    };
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const W = (n, x, y, z) => { obj.updateMatrixWorld(true); return new THREE.Vector3(x, y, z).applyMatrix4(P[n].obj.matrixWorld); };
    // passo de aranha: braços em dois trios alternados (D0,E1,D2) e (E0,D1,E2)
    const gait = (ph, k) => {
      for (let i = 0; i < 3; i++) for (const sd of ['D', 'E']) {
        const grp = ((i + (sd === 'D' ? 0 : 1)) % 2) ? Math.PI : 0, sg = sd === 'D' ? -1 : 1;
        const up = Math.max(0, Math.sin(ph + grp));
        r('braco' + sd + i, 0.35 * k * Math.cos(ph + grp), 0, -sg * 0.35 * up * k);
        r('ante' + sd + i, 0, 0, sg * 0.2 * up * k);
      }
    };
    const brain = k => { if (P.tampa) P.tampa.obj.rotation.x += 0.12 * k * Math.sin(t * 2.4); };
    const doors = k => { r('portaD', 0, -1.3 * k, 0); r('portaE', 0, 1.3 * k, 0); };

    if (mode === 'parado') {
      // os braços trocam de apoio sozinhos, uma cara pisca enquanto a outra mexe a boca, o cérebro pulsa
      const br = loop([[0, 0], [0.5, 1, 'io'], [1, 0, 'io']], t / 2.6);
      obj.position.y += 0.03 * br;
      r('cabeca', 0.05 * br, 0.25 * Math.sin(t * 0.5), 0.1 * Math.sin(t * 0.7));
      for (let i = 0; i < 3; i++) { const k = Math.max(0, Math.sin(t * 1.3 + i * 2.1)) ** 6; r('bracoD' + i, 0, 0, 0.3 * k); }
      for (let i = 0; i < 3; i++) { const k = Math.max(0, Math.sin(t * 1.1 + i * 1.7 + 1)) ** 6; r('bracoE' + i, 0, 0, -0.3 * k); }
      doors(0.08 + 0.08 * Math.sin(t * 3));
      brain(1);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 5;
      gait(ph, 1);
      r('corpo', 0.05 * Math.sin(ph * 2), 0.06 * Math.sin(ph), 0.04 * Math.sin(ph));
      obj.position.y += 0.03 * Math.abs(Math.sin(ph));
      brain(1);
      return;
    }
    if (mode === 'garras') {
      const T = t % 4.6;
      const R = ctx.ref; const RZ = -2.8; R.place(0, RZ, 0); R.spin(Math.PI);
      // empina nos dois braços de trás, os outros quatro abrem pro alto e descem rasgando um de cada vez
      const rear = seq([[0.2, 0], [0.9, 1, 'io'], [3.4, 1], [4.1, 0, 'io']], T);
      P.corpo.obj.rotation.x += -0.75 * rear; obj.position.y += 0.5 * rear; obj.position.z += -0.15 * rear;
      r('bracoD2', 0.9 * rear, 0, 0); r('bracoE2', 0.9 * rear, 0, 0);
      const order = [['D', 0], ['E', 0], ['D', 1], ['E', 1], ['D', 0], ['E', 0]];
      order.forEach(([sd, i], j) => {
        const t0 = 1.0 + j * 0.3, sg = sd === 'D' ? -1 : 1;
        const sw = seq([[t0 - 0.25, 0], [t0, 1, 'in'], [t0 + 0.08, -0.6, 'snap'], [t0 + 0.4, 0, 'io']], T);
        r('braco' + sd + i, -1.6 * rear + 0.6 * Math.max(0, sw) - 1.2 * Math.max(0, -sw), 0, sg * 0.5 * rear);
        r('ante' + sd + i, 0, 0, -sg * 1.1 * rear);
        if (T > t0 && T < t0 + 0.3) ctx.claw(new THREE.Vector3(0.25 * sg, 1.3 - 0.1 * j, RZ + 0.2), (T - t0) / 0.3);
        if (T > t0 && T < t0 + 0.08) { ctx.flash(new THREE.Vector3(0, 1.2, RZ), 0.6); ctx.shake(0.35); }
      });
      const hit = Math.max(...order.map((_, j) => seq([[1.0 + j * 0.3, 0], [1.05 + j * 0.3, 1, 'snap'], [1.3 + j * 0.3, 0]], T)));
      R.punch(0.6 * hit); R.lean(-0.15 * hit); R.spin(Math.PI + 0.25 * hit * (Math.floor(T / 0.3) % 2 ? 1 : -1));
      r('cabeca', -0.2 * rear, 0, 0); brain(2);
      return;
    }
    if (mode === 'grito') {
      const T = t % 5.0;
      const R = ctx.ref; const RZ = -3.2; R.place(0, RZ, 0); R.spin(Math.PI);
      // agacha nos braços, as portas da barriga abrem devagar (fios de saliva), a tampa do crânio levanta e todas as
      // caras gritam: ondas saem da barriga e das caras, a tela escurece e roda
      const crouch = seq([[0.2, 0], [0.8, 1, 'io'], [1.2, 1], [1.4, -0.3, 'snap'], [3.6, -0.3], [4.3, 0, 'io']], T);
      obj.position.y -= 0.18 * Math.max(0, crouch); P.corpo.obj.rotation.x += 0.1 * Math.max(0, crouch) - 0.2 * Math.max(0, -crouch) / 0.3;
      const open = seq([[0.6, 0], [1.3, 0.5, 'in'], [1.4, 1, 'snap'], [3.6, 1], [4.2, 0]], T);
      doors(open); if (P.tampa) P.tampa.obj.rotation.x += 0.6 * open;
      for (let i = 0; i < 3; i++) for (const sd of ['D', 'E']) r('braco' + sd + i, 0, 0, (sd === 'D' ? 1 : -1) * 0.25 * Math.max(0, crouch));
      const scream = T > 1.4 && T < 3.6;
      if (scream) {
        const ph = T - 1.4;
        r('cabeca', -0.25 + 0.05 * Math.sin(t * 40), 0, 0); ctx.shake(0.45);
        for (let j = 0; j < 4; j++) {
          const ag = fr(ph * 1.6 + j * 0.25), src = [W('corpo', 0, -4, -8), W('cabeca', 0, -3.5, -5.5), W('caraB', 0, 0, -1), W('caraC', 0, 0, -1)][j];
          const ring = ctx.get('anel' + j, () => { const m = new THREE.Mesh(new THREE.RingGeometry(0.85, 1, 40), new THREE.MeshBasicMaterial({ color: 0xd8c8e8, transparent: true, opacity: 0.5, side: THREE.DoubleSide, depthWrite: false })); m.frustumCulled = false; return m; });   // onda do grito ring.visible = true; ring.position.copy(src);
          const dir = j < 2 ? new THREE.Vector3(0, 0, -1) : j === 2 ? new THREE.Vector3(1, 0, 0) : new THREE.Vector3(-1, 0, 0);
          ring.position.addScaledVector(dir, ag * 1.6); ring.lookAt(ring.position.clone().add(dir)); ring.scale.setScalar(0.2 + ag * 1.4); ring.material.opacity = 0.55 * (1 - ag);
        }
        ctx.get('saliva', () => DBFX.grains({ n: 26, seed: 71, palette: [[200, 190, 200], [150, 40, 60]], size: [0.02, 0.04], drag: 1, grav: 6, life: 0.6, spread: 1.4, cone: R2 => [(R2() - 0.5) * 1.4, R2(), -1 - R2() * 1.5] })).at(W('corpo', 0, -4, -8), ph % 1.4, 0.02);
      }
      const k = seq([[1.4, 0], [1.8, 1, 'in'], [3.6, 1], [4.4, 0]], T);
      ctx.dark(0.55 * k); ctx.dizzy(0.9 * k); ctx.cursed(0.7 * k);
      R.armsOut(-0.4 * k); R.lean(0.25 * k); R.punch(0.3 * k);
      brain(1);
      return;
    }
    if (mode === 'morte') {
      // os braços cedem um por um, a massa desaba, as caras fecham os olhos e tudo vira cinza
      const T = t % 4.0;
      const R = ctx.ref; R.place(0, -3.0, 0); R.spin(Math.PI);
      R.punch(seq([[0.15, 0], [0.25, 1, 'snap'], [0.6, 1], [0.9, 0]], T));
      for (let i = 0; i < 3; i++) for (const sd of ['D', 'E']) {
        const t0 = 0.4 + (i * 2 + (sd === 'D' ? 0 : 1)) * 0.12, g = seq([[t0, 0], [t0 + 0.25, 1, 'in']], T), sg = sd === 'D' ? -1 : 1;
        r('braco' + sd + i, 0, 0, sg * 0.9 * g); r('ante' + sd + i, 0, 0, -sg * 0.8 * g);
      }
      const fall = seq([[0.6, 0], [1.3, 1, 'in']], T);
      obj.position.y -= 0.45 * fall; r('cabeca', 0.5 * fall, 0, 0.2 * fall);
      if (T > 1.25 && T < 1.45) ctx.shake(0.8);
      if (T > 0.22 && T < 0.4) ctx.flash(new THREE.Vector3(0, 1.2, -1.6), 1 - (T - 0.22) / 0.18);
      const dis = FX.clamp01((T - 1.6) / 1.8);
      JJFX.dissolve(e, dis, 0, 2.0);
      if (T > 1.6) ctx.get('cinza', () => JJFX.ash(37, 220)).at(new THREE.Vector3(0, 0.4, 0), T - 1.6, 0.02);
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(17, 0), variant(42, 1), variant(73, 2)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'solta', nome: 'Maldição Solta', bioma: 'Colônia', tier: 'Grau 1 · tier 5',
    variants: fixed.map(v => ({ nome: KINDS[v.kind][0], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteada (' + KINDS[v.kind][0] + ')', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['garras', 'Mil Unhas'], ['grito', 'Grito das Caras'], ['morte', 'Exorcizada']],
    cam: 6.0, lookY: 1.3, lookZ: { garras: -1.2, grito: -1.4, morte: -0.4 }, camFar: { garras: 7.6, grito: 8.0, morte: 7.0 }, camYaw: { garras: -1.1, grito: -1.0, morte: -1.0 },
    info: {
      'Tamanho': '2,4 blocos de altura, 2,5 de largura com os braços',
      'Vida': '150',
      'Jeito': 'Uma por colônia, rondando os prédios altos e os túneis. Rápida pra o tamanho (anda como aranha) e sobe em parede. Ignora as Larvas e os Jogadores com mais pontos que você.',
      'Mil Unhas': 'Empina nos braços de trás (0,7 s de aviso) e os quatro da frente descem rasgando, seis golpes seguidos. Cada um empurra um pouco: se ficar parado, leva todos.',
      'Grito das Caras': 'A barriga abre devagar e a tampa do crânio levanta (1,2 s), aí todas as caras gritam juntas por 2 s: tela escura girando, você fica lento e não consegue usar técnica.',
      'Fraqueza': 'O cérebro exposto: golpe de cima (caindo) faz dano triplo e atordoa 2 s.',
      'Solta': 'Carne Costurada, Linha do Kenjaku; raro: Cérebro Costurado (troféu: mostra o mapa da colônia).',
      'Variações': 'Costurada (cinza-roxa), afogada (azulada) ou queimada; caras e retalhos diferentes em cada uma.',
    },
  });
})();
