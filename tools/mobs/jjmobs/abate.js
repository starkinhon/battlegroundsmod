// Jogador do Abate (Colônia). Tier 7. Gente comum (ou feiticeiro antigo reencarnado) que o Kenjaku despertou e jogou
// no Jogo do Abate: precisa tirar pontos de outros jogadores pra não perder a técnica. Não é maldição: é humano, com
// um contador de pontos flutuando em cima da cabeça (a Kogane mostra). Anda armado e desconfiado.
// Golpes: Corte (avança e dá dois cortes com a ferramenta amaldiçoada, rastro azul de energia) e Golpe de Energia
// (concentra energia amaldiçoada no punho, que fica envolto numa chama azul, e soca: onda de choque).
// Derrotado: cai de joelhos e desmaia; os pontos dele sobem pra quem bateu.
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  // [nome, roupa, roupa 2, pele, cabelo, arma]
  const KINDS = [
    ['Feiticeiro de Moletom', [44, 46, 58], [196, 196, 200], [232, 196, 166], [30, 26, 24], 'katana'],
    ['Reencarnado da Era Heian', [120, 40, 44], [220, 206, 170], [214, 176, 140], [18, 16, 16], 'katana'],
    ['Estudante Desperto', [30, 34, 44], [210, 200, 160], [240, 206, 178], [96, 60, 36], 'cano'],
  ];
  const FACES = [
    ['HHHHHHHH', 'HHHHHHHH', 'H......H', '.BB..BB.', '.WK..KW.', '........', '..MMMM..', '........'],
    ['HHHHHHHH', 'H.HHHH.H', '........', 'BB....BB', '.KW..WK.', '...NN...', '.MMMMMM.', '..MMMM..'],
    ['HHHHHHHH', 'HHHHHHHH', 'HHH..HHH', '.B....B.', '.WKWWKW.', '........', '...MM...', '........'],
  ];
  function variant(seed, kind) {
    const R = FX.rnd(seed);
    const k = kind ?? Math.floor(R() * KINDS.length);
    return { seed, kind: k, cloth: KINDS[k][1], cloth2: KINDS[k][2], skin: KINDS[k][3], hair: KINDS[k][4], weapon: KINDS[k][5], pts: [1, 5, 7, 12, 19][Math.floor(R() * 5)], size: 0.96 + R() * 0.08, scarf: R() < 0.5 };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.72, hi = 1.16) => {
      const f = 0.62 - 0.3 * q.ny + 0.2 * (n3(q.ax * 0.5, q.ay * 0.5, q.az * 0.5, s + S) - 0.5) + (q.f === 'top' ? 0.2 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const P = {};
    P.skin = q => shade(v.skin, q, 1, 0.8, 1.1);
    P.head = q => {
      if (q.f === 'top' || q.f === 'south') return shade(v.hair, q, 2);
      if (q.f !== 'north') return q.py < 3 ? shade(v.hair, q, 2) : shade(v.skin, q, 1, 0.8, 1.1);
      const col = 7 - Math.min(7, Math.floor(q.px)), row = Math.min(7, Math.floor(q.py));
      const ch = FACES[v.kind][row][col];
      if (ch === 'H') return shade(v.hair, q, 2);
      if (ch === 'B') return mul(v.hair, 0.9);
      if (ch === 'W') return [240, 240, 236];
      if (ch === 'K') return v.kind === 1 ? [140, 30, 30] : [30, 40, 70];
      if (ch === 'N') return mul(v.skin, 0.85);
      if (ch === 'M') return v.kind === 1 ? [120, 50, 50] : [150, 80, 70];
      return shade(v.skin, q, 1, 0.8, 1.1);
    };
    // roupa: dobras, costura no meio, sujeira e um rasgo (a colônia é guerra)
    P.cloth = q => {
      let c = shade(v.cloth, q, 3);
      if (fr(q.ay * 0.45 + 0.4 * n3(q.ax * 0.3, 0, q.az * 0.3, 4 + S)) < 0.12) c = mul(c, 0.82);
      if (n3(q.ax * 0.6, q.ay * 0.6, q.az * 0.6, 5 + S) > 0.74) c = mix(c, [110, 96, 80], 0.35);
      if (v.kind === 0 && q.f === 'north' && Math.abs(q.px - q.b.w / 2) < 0.4) c = shade(v.cloth2, q, 6);  // zíper do moletom
      if (v.kind === 1 && q.f === 'north' && Math.abs(q.px - q.b.w / 2 + (q.py - 6) * 0.35) < 1.1) c = shade(v.cloth2, q, 6); // gola cruzada
      if (v.kind === 2 && q.f === 'north' && q.py < 1.4) c = shade(v.cloth2, q, 6);  // gola da camisa
      return c;
    };
    P.pants = q => { let c = shade(mul(v.cloth, 0.8), q, 7); if (q.ny > 0.86) c = shade([40, 36, 34], q, 8); return c; };
    P.scarf = q => { let c = shade([170, 40, 40], q, 9); if (fr(q.ax * 0.5 + q.az * 0.5) < 0.2) c = mul(c, 0.8); return c; };
    P.blade = q => { let c = shade([214, 222, 232], q, 10, 0.85, 1.2); if (q.nx < 0.25) c = [250, 252, 255]; if (Math.abs(n3(q.ax * 2, q.ay * 0.4, 0, 11 + S) - 0.5) < 0.03) c = mix(c, [120, 150, 220], 0.6); return c; };
    P.hilt = q => { let c = shade([30, 26, 30], q, 12); if (fr(q.ay * 1.2) < 0.4) c = shade([170, 140, 70], q, 13); return c; };
    P.guard = q => shade([170, 140, 70], q, 14);
    P.pipe = q => { let c = shade([140, 140, 146], q, 15); if (n3(q.ax, q.ay, q.az, 16 + S) > 0.6) c = mix(c, [150, 80, 40], 0.5); if (q.ny > 0.85) c = shade([40, 40, 44], q, 17); return c; };
    return P;
  }

  function rig(P, v, lo) {
    const weapon = v.weapon === 'katana'
      ? [{ x: -0.6, y: -1.2, z: -0.6, w: 1.2, h: 4.4, d: 1.2, p: P.hilt }, { x: -1.6, y: 3.0, z: -1.6, w: 3.2, h: 0.6, d: 3.2, p: P.guard }, { x: -0.3, y: 3.6, z: -0.8, w: 0.6, h: 15, d: 1.6, p: P.blade }, { x: -0.3, y: 18.6, z: -0.5, w: 0.6, h: 1.2, d: 1.0, p: P.blade }]
      : [{ x: -0.8, y: -1.2, z: -0.8, w: 1.6, h: 16, d: 1.6, p: P.pipe }, { x: -1.2, y: 13.6, z: -1.2, w: 2.4, h: 2.4, d: 2.4, p: P.pipe }];
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'tronco', pivot: [0, 12, 0], boxes: [
            { x: -4, y: -12, z: -2, w: 8, h: 12, d: 4, p: P.cloth },
            ...(v.scarf && !lo ? [{ x: -4.3, y: -12.4, z: -2.3, w: 8.6, h: 2, d: 4.6, p: P.scarf }, { x: 1.4, y: -11, z: 2.1, w: 2, h: 6, d: 0.6, p: P.scarf }] : []),
          ],
          kids: [
            { name: 'cabeca', pivot: [0, -12, 0], boxes: [{ x: -4, y: -8, z: -4, w: 8, h: 8, d: 8, p: P.head }, ...(v.kind === 1 && !lo ? [{ x: -1.2, y: -10, z: 0, w: 2.4, h: 2.2, d: 2.4, p: P.head }] : [])] },
            {
              name: 'bracoD', pivot: [-5, -10, 0], boxes: [{ x: -2, y: -2, z: -2, w: 4, h: 6, d: 4, p: P.cloth }],
              kids: [{ name: 'anteD', pivot: [0, 4, 0], boxes: [{ x: -1.9, y: 0, z: -1.9, w: 3.8, h: 4, d: 3.8, p: P.cloth }, { x: -1.8, y: 4, z: -1.8, w: 3.6, h: 2.2, d: 3.6, p: P.skin }], kids: [{ name: 'arma', pivot: [0, 5.2, -0.4], rot: [1.57, 0, 0], boxes: weapon }] }],
            },
            {
              name: 'bracoE', pivot: [5, -10, 0], boxes: [{ x: -2, y: -2, z: -2, w: 4, h: 6, d: 4, p: P.cloth }],
              kids: [{ name: 'anteE', pivot: [0, 4, 0], boxes: [{ x: -1.9, y: 0, z: -1.9, w: 3.8, h: 4, d: 3.8, p: P.cloth }, { x: -1.8, y: 4, z: -1.8, w: 3.6, h: 2.2, d: 3.6, p: P.skin }] }],
            },
          ],
        },
        ...[-1, 1].map(s => ({ name: 'perna' + (s < 0 ? 'D' : 'E'), pivot: [s * 2, 12, 0], boxes: [{ x: -2, y: 0, z: -2, w: 4, h: 6, d: 4, p: P.pants }], kids: [{ name: 'canela' + (s < 0 ? 'D' : 'E'), pivot: [0, 6, 0], boxes: [{ x: -1.95, y: 0, z: -1.95, w: 3.9, h: 6, d: 3.9, p: P.pants }] }] })),
      ],
    };
  }

  // placa de pontos flutuando: texto em pixel desenhado num canvas (sem fonte externa)
  const GLYPH = { '0': ['###', '#.#', '#.#', '#.#', '###'], '1': ['.#.', '##.', '.#.', '.#.', '###'], '2': ['##.', '..#', '.#.', '#..', '###'], '5': ['###', '#..', '##.', '..#', '##.'], '7': ['###', '..#', '.#.', '.#.', '.#.'], '9': ['###', '#.#', '###', '..#', '##.'], '+': ['...', '.#.', '###', '.#.', '...'], 'P': ['###', '#.#', '###', '#..', '#..'], 'T': ['###', '.#.', '.#.', '.#.', '.#.'], 'S': ['.##', '#..', '.#.', '..#', '##.'], ' ': ['...', '...', '...', '...', '...'] };
  function plate(text, fg, bg) {
    const cw = text.length * 4 + 3, chh = 9, cv = document.createElement('canvas'); cv.width = cw; cv.height = chh;
    const g = cv.getContext('2d'); g.fillStyle = bg; g.fillRect(0, 0, cw, chh); g.fillStyle = 'rgba(0,0,0,0.5)'; g.fillRect(0, chh - 1, cw, 1);
    [...text].forEach((ch, i) => { const gl = GLYPH[ch] || GLYPH[' ']; gl.forEach((row, y) => [...row].forEach((c, x) => { if (c === '#') { g.fillStyle = fg; g.fillRect(2 + i * 4 + x, 2 + y, 1, 1); } })); });
    const tex = new THREE.CanvasTexture(cv); tex.magFilter = tex.minFilter = THREE.NearestFilter; tex.generateMipmaps = false;
    const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, transparent: true, depthTest: false }));
    sp.scale.set(cw / 28, chh / 28, 1); sp.renderOrder = 10; return sp;
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const W = (n, x, y, z) => { obj.updateMatrixWorld(true); return new THREE.Vector3(x, y, z).applyMatrix4(P[n].obj.matrixWorld); };
    const CE = [0.3, 0.55, 1.0];
    const label = (k = 1, txt) => { const key = 'placa' + e.v.kind + (txt || ''); const p = ctx.get(key, () => plate(txt || (String(e.v.pts) + ' PTS'), '#ffe27a', 'rgba(20,16,30,0.75)')); p.visible = k > 0.01; p.position.copy(W('cabeca', 0, -11, 0)); p.material.opacity = k; };
    const guard = () => { r('bracoD', -0.5, 0, 0.1); r('anteD', -0.9, 0, 0); r('arma', -0.4, 0, 0); r('bracoE', -0.3, 0, -0.1); r('anteE', -1.2, 0, 0); r('pernaD', -0.15, 0, 0); r('pernaE', 0.15, 0, 0); };
    const aura = (key, p, rad, k) => { if (k <= 0.02) return; ctx.get(key, FX.volume).set(p, rad, FX.clamp01(0.12 + 0.25 * fr(t * 2.5)), { fire: 0.15, seed: 90 + Math.floor(t * 2.5), dens: 6 * k, rise: 1.3, squash: 1.5, smoke: [0.35, 0.6, 1.0] }); };

    if (mode === 'parado') {
      // em guarda e desconfiado: olha pros lados em giros curtos, troca o peso de perna, aperta o cabo da arma
      guard();
      const look = seq([[0, 0], [0.1, 1, 'io'], [0.4, 1], [0.5, -1, 'io'], [0.8, -1], [0.9, 0, 'io']], fr(t / 5));
      r('cabeca', 0, 0.6 * look, 0); r('tronco', 0, 0.15 * look, 0);
      obj.position.x += 0.04 * Math.sin(t * 0.9);
      label(1);
      return;
    }
    if (mode === 'andar') {
      // corre abaixado, arma pra trás
      const ph = t * 7;
      r('pernaD', 0.8 * Math.sin(ph), 0, 0); r('pernaE', -0.8 * Math.sin(ph), 0, 0);
      r('canelaD', 0.9 * Math.max(0, -Math.sin(ph)), 0, 0); r('canelaE', 0.9 * Math.max(0, Math.sin(ph)), 0, 0);
      r('tronco', 0.3, 0, 0); r('cabeca', -0.25, 0, 0);
      r('bracoD', 0.6, 0, 0.2); r('arma', 0.6, 0, 0); r('bracoE', -0.8 * Math.sin(ph), 0, 0);
      obj.position.y += 0.06 * Math.abs(Math.sin(ph));
      label(1);
      return;
    }
    if (mode === 'corte') {
      const T = t % 3.8;
      const R = ctx.ref; const RZ = -3.2; R.place(0, RZ, 0); R.spin(Math.PI);
      // passa energia na lâmina (fica azul), avança e corta duas vezes: diagonal de cima e horizontal de volta
      const charge = seq([[0.2, 0], [0.7, 1, 'io'], [2.4, 1], [2.9, 0]], T);
      const step = seq([[0.7, 0], [0.9, 1, 'out'], [2.4, 1], [3.4, 0, 'io']], T);
      obj.position.z += (RZ + 1.4) * step;
      const c1 = seq([[0.85, 0], [1.0, 1, 'snap']], T), c2 = seq([[1.35, 0], [1.5, 1, 'snap']], T);
      // corte 1: arma de cima/trás pra baixo/frente; corte 2: horizontal da esquerda pra direita
      r('bracoD', -2.6 * (1 - c1) * charge - 0.6 * c1 * (1 - c2) - 1.4 * c2, 0.9 * c2, 0.2);
      r('anteD', -0.3, 0, 0); r('arma', -0.5 * (1 - c1) + 0.6 * c1 * (1 - c2), 0, 1.2 * c2);
      r('tronco', 0.15 * c1 - 0.2 * c2, -0.5 * c2 + 0.3 * c1 * (1 - c2), 0);
      r('pernaD', -0.6 * step, 0, 0); r('canelaD', 0.4 * step, 0, 0); r('pernaE', 0.4 * step, 0, 0);
      guard();
      if (charge > 0.05) ctx.get('lamina', () => DBFX.glow(CE)).at(W('arma', 0, 11, 0), 0.12, 0.8 * charge, t, 3.0);
      const z = RZ + 0.1;
      if (T > 0.88 && T < 1.3) ctx.slash(new THREE.Vector3(0.05, 1.2, z), (T - 0.88) / 0.42);
      if (T > 1.38 && T < 1.8) ctx.claw(new THREE.Vector3(0, 1.05, z), (T - 1.38) / 0.42);
      const h1 = seq([[0.95, 0], [1.0, 1, 'snap'], [1.3, 0.3]], T), h2 = seq([[1.45, 0], [1.5, 1, 'snap'], [2.2, 0.4], [2.8, 0]], T);
      R.lean(0.25 * h1 - 0.35 * h2); R.spin(Math.PI + 0.4 * h2); R.place(0, RZ - 0.6 * FX.clamp01((T - 1.5) / 0.5), 0);
      if ((T > 0.98 && T < 1.1) || (T > 1.48 && T < 1.6)) ctx.flash(new THREE.Vector3(0, 1.2, z), 0.8);
      label(1);
      return;
    }
    if (mode === 'energia') {
      const T = t % 4.0;
      const R = ctx.ref; const RZ = -1.9; R.place(0, RZ, 0); R.spin(Math.PI);
      // crava a arma no chão, puxa o punho esquerdo pra trás e junta energia (chama azul em volta do braço),
      // avança um passo e soca; anel de choque e o alvo voa
      r('bracoD', -0.3, 0, 0.3); r('arma', -1.3, 0, 0);
      const pull = seq([[0.2, 0], [0.8, 1, 'io'], [1.4, 1], [1.5, -0.4, 'snap'], [2.6, -0.4], [3.4, 0, 'io']], T);
      const pP = Math.max(0, pull), pn = Math.max(0, -pull) / 0.4;
      r('bracoE', 0.6 * pP - 1.6 * pn, 0, -0.2 * pP); r('anteE', -1.6 * pP - 0.1 * pn, 0, 0);
      r('tronco', 0.1 * pn, 0.5 * pP - 0.3 * pn, 0); r('pernaE', -0.5 * pn, 0, 0); r('canelaE', 0.3 * pn, 0, 0); r('pernaD', 0.3 * pn, 0, 0);
      obj.position.z -= 0.35 * pn;
      const charge = seq([[0.4, 0], [1.4, 1, 'in'], [1.7, 0.6], [2.6, 0]], T);
      const fist = W('anteE', 0, 5.4, 0);
      aura('auraP', fist, 0.22 + 0.12 * charge, charge);
      if (charge > 0) ctx.get('punho', () => DBFX.glow(CE)).at(fist, 0.12 + 0.08 * charge, 1.2 * charge, t);
      if (charge > 0.2) aura('auraC', W('tronco', 0, -6, 0), 0.55, charge * 0.6);
      const ag = T - 1.5;
      if (ag > 0 && ag < 1) {
        const at = new THREE.Vector3(0, 1.2, RZ + 0.25);
        ctx.get('choque', () => DBFX.glow(CE)).at(at, 0.2 + 0.9 * ag, 1.6 * (1 - ag), t, 1);
        ctx.roarRing(new THREE.Vector3(0, 0.03, RZ + 0.25), Math.min(0.999, ag * 1.5));
        if (ag < 0.2) { ctx.flash(at, 1.4); ctx.shake(0.8); }
      }
      const fly = FX.clamp01((T - 1.52) / 0.7);
      if (T > 1.52) { R.place(0, RZ - 3.0 * FX.EASE.out(fly), 0.6 * Math.sin(Math.PI * fly)); R.lean(-0.6 * (1 - FX.clamp01((T - 2.6) / 1))); R.armsOut(0.6); }
      label(1);
      return;
    }
    if (mode === 'morte') {
      // é gente: leva o golpe, cambaleia, cai de joelhos e desmaia de cara; a placa vira "+N" e sobe pra você
      const T = t % 4.4;
      const R = ctx.ref; R.place(0, -1.9, 0); R.spin(Math.PI);
      R.punch(seq([[0.15, 0], [0.25, 1, 'snap'], [0.6, 1], [0.9, 0]], T));
      const knee = seq([[0.5, 0], [1.1, 1, 'in']], T), fall = seq([[1.4, 0], [1.9, 1, 'in']], T);
      r('tronco', -0.2 * seq([[0.25, 0], [0.4, 1], [0.7, 0]], T), 0, 0);
      r('pernaD', -1.5 * knee, 0, 0); r('pernaE', -1.5 * knee, 0, 0); r('canelaD', 1.6 * knee, 0, 0); r('canelaE', 1.6 * knee, 0, 0);
      obj.position.y -= 0.38 * knee;
      P.tronco.obj.rotation.x += 1.4 * fall; obj.position.y -= 0.2 * fall; obj.position.z -= 0.3 * fall;
      r('bracoD', -0.4 * fall, 0, 0.2); r('bracoE', -0.4 * fall, 0, -0.2); r('arma', 0.5 * knee, 0, 0);
      // nenhum membro escora o morto: o corpo encosta no chão (teste R5)
      JJFX.floorLimbs(obj, P, ['tronco', 'cabeca'], ['bracoD', 'bracoE', 'pernaD', 'pernaE'], fall);
      if (T > 1.85 && T < 2.0) ctx.shake(0.4);
      label(1 - FX.clamp01((T - 0.8) / 0.3));
      if (T > 1.1) {
        const k = FX.clamp01((T - 1.1) / 1.6);
        const p = ctx.get('ganho' + e.v.kind, () => plate('+' + e.v.pts + ' PTS', '#ffe27a', 'rgba(60,40,0,0.8)'));
        p.visible = true; p.position.set(0, 2.2 + 0.4 * k - 0.1, -0.2 - 1.6 * FX.EASE.io(FX.clamp01((T - 2.4) / 1.2)));
        p.material.opacity = 1 - FX.clamp01((T - 3.6) / 0.6);
        ctx.get('brilhoP', () => DBFX.grains({ n: 26, seed: 99, add: true, palette: [[255, 226, 122], [255, 250, 210]], size: [0.015, 0.03], drag: 1.5, grav: -0.3, life: 1, spread: 1.4, cone: R2 => [(R2() - 0.5) * 0.8, R2() * 0.6, (R2() - 0.5) * 0.8] })).at(p.position, T - 1.1, -9);
      }
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(11, 0), variant(32, 1), variant(58, 2)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'abate', nome: 'Jogador do Abate', bioma: 'Colônia', tier: 'Humano · tier 7',
    variants: fixed.map(v => ({ nome: KINDS[v.kind][0], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado (' + KINDS[v.kind][0] + ')', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Correr'], ['corte', 'Corte'], ['energia', 'Golpe de Energia'], ['morte', 'Derrotado']],
    cam: 5.0, lookY: 1.3, lookZ: { corte: -1.4, energia: -1.4, morte: -0.6 }, camFar: { corte: 6.6, energia: 7.0, morte: 5.8 }, camYaw: { corte: -1.15, energia: -1.1, morte: -1.0 },
    info: {
      'Tamanho': 'Igual um jogador (2 blocos)',
      'Vida': '50 (humano: sangra; leva dano normal de tudo)',
      'Jeito': 'Sozinho ou em dupla pelas ruas da Colônia. Ataca qualquer um que tenha pontos e foge quando está perdendo (vai atrás de um alvo mais fraco). A placa em cima da cabeça mostra quantos pontos ele vale.',
      'Corte': 'Passa energia na lâmina (fica azul: aviso), avança 2 blocos e dá dois cortes seguidos: diagonal e horizontal. Bloquear o primeiro quebra o segundo.',
      'Golpe de Energia': 'Crava a arma no chão e junta energia no punho (1 s: o braço pega fogo azul), avança e soca: dano alto e te joga 3 blocos.',
      'Derrotado': 'Não vira cinza: desmaia. Os pontos dele vão pra você (a Kogane anuncia). Volta a acordar depois de um tempo se ninguém mexer.',
      'Solta': 'Pontos do Abate (moeda das colônias, troca com a Kogane), a arma que estava usando; raro: Ferramenta Amaldiçoada.',
      'Variações': 'Feiticeiro de moletom (katana), reencarnado da era Heian (kimono, coque, katana) ou estudante desperto (cano de ferro); com ou sem cachecol; vale de 1 a 19 pontos.',
    },
  });
})();
