// Olhos da Mata (Floresta Negra). Tier 7, grau 2. Um tronco morto que nasceu do medo de ser observado no escuro:
// de olhos fechados é só mais uma árvore; abertos, são nove, de tamanhos diferentes, e uma fenda de dentes no meio.
// Anda devagar nas raízes. Golpes: Raízes (uma fileira de raízes rompe o chão até você e te prende) e Olhar Fixo
// (todos os olhos abrem em você: fica paralisado enquanto olhar pra ela).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const EYES = [[-2.6, -19, 'north', 4.2], [2.6, -15.5, 'north', 3.0], [-1.6, -11, 'north', 5.0], [3.0, -6.5, 'north', 3.4], [-3.2, -4, 'north', 2.6],
    [0, -17, 'west', 3.4], [1.5, -8, 'west', 3.8], [-1, -13, 'east', 3.2], [1.4, -3.5, 'east', 4.2]];

  const BARKS = [[74, 62, 52], [62, 60, 58], [84, 66, 50]];
  function variant(seed) {
    const R = FX.rnd(seed);
    return { seed, bark: BARKS[Math.floor(R() * BARKS.length)], moss: R() < 0.6, iris: R() < 0.5 ? [196, 186, 60] : [140, 200, 90], size: 0.92 + R() * 0.16 };
  }
  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.78, hi = 1.14) => {
      const f = 0.62 - 0.4 * q.ny + 0.35 * (n3(q.ax * 0.25, q.ay * 0.25, q.az * 0.25, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    // casca: sulcos verticais tortos, nós, musgo do lado de baixo
    const bark = (q, s = 1) => {
      let c = shade(v.bark, q, s);
      const u = q.f === 'north' || q.f === 'south' ? q.ax : q.az;
      if (fr(u * 0.45 + n3(u * 0.2, q.ay * 0.08, 0, 3 + S) * 1.5) < 0.18) c = mul(c, 0.66);
      if (n3(q.ax * 0.4, q.ay * 0.4, q.az * 0.4, 4 + S) > 0.82) c = mul(c, 0.7);
      if (v.moss && n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, 5 + S) > 0.64 && q.ay > -4) c = mix(c, [70, 96, 46], 0.6);
      return c;
    };
    const P = {};
    P.bark = q => bark(q);
    P.root = q => { let c = bark(q, 6); if (q.f === 'bottom') c = mix(c, [50, 40, 30], 0.5); return c; };
    P.twig = q => mul(bark(q, 7), 0.86);
    P.eye = q => {
      if (q.f !== 'north') return mul(v.bark, 0.6);
      const x = q.nx - 0.5, y = q.ny - 0.5, d = Math.hypot(x, y);
      if (d > 0.5) return mul(v.bark, 0.55);
      let c = [232, 224, 206];
      if (n3(q.px * 1.2, q.py * 1.2, 0, 7 + S) > 0.7) c = [206, 110, 100];
      if (d < 0.27) c = v.iris;
      if (d < 0.13) c = [8, 6, 8];
      return c;
    };
    P.lid = q => mul(bark(q, 8), 0.9);
    P.slit = q => {
      if (q.f !== 'north') return [30, 10, 14];
      const y = q.py;
      return Math.floor(y) % 2 ? [220, 210, 180] : [60, 14, 20];
    };
    return P;
  }

  function rig(P, v, lo) {
    const faceZ = { north: [0, 0, -6.2], west: [-6.2, 0, 0], east: [6.2, 0, 0] };
    const rotY = { north: 0, west: Math.PI / 2, east: -Math.PI / 2 };
    const eyes = EYES.filter((_, i) => !lo || i % 2 === 0).map(([u, y, f, sz], i) => ({
      name: 'olho' + i, pivot: [faceZ[f][0] + (f === 'north' ? u : 0), y, faceZ[f][2] + (f === 'north' ? 0 : u)], rot: [0, rotY[f], 0],
      boxes: [{ x: -sz / 2, y: -sz / 2, z: -0.6, w: sz, h: sz, d: 0.8, p: P.eye }],
      kids: [{ name: 'palpebra' + i, pivot: [0, -sz / 2, -0.7], boxes: [{ x: -sz / 2 - 0.3, y: 0, z: -0.2, w: sz + 0.6, h: sz, d: 0.5, p: P.lid }] }],
    }));
    const branch = (i, ang, tilt, L) => ({
      name: 'galho' + i, pivot: [Math.cos(ang) * 3, -22, Math.sin(ang) * 3], rot: [Math.sin(ang) * tilt, 0, -Math.cos(ang) * tilt],
      boxes: [{ x: -1.3, y: -L, z: -1.3, w: 2.6, h: L, d: 2.6, p: P.bark }],
      kids: lo ? [] : [{ name: 'ponta' + i, pivot: [0, -L, 0], rot: [Math.sin(ang) * 0.6, 0, -Math.cos(ang) * 0.6], boxes: [{ x: -0.8, y: -L * 0.8, z: -0.8, w: 1.6, h: L * 0.8, d: 1.6, p: P.twig }, { x: -0.5, y: -L * 0.8 - 2, z: -0.5, w: 1, h: 2.4, d: 1, p: P.twig }] }],
    });
    const root = (i, ang) => ({
      name: 'raiz' + i, pivot: [Math.cos(ang) * 4.6, 6, Math.sin(ang) * 4.6], rot: [Math.sin(ang) * 0.75, 0, -Math.cos(ang) * 0.75],
      boxes: [{ x: -1.8, y: 0, z: -1.8, w: 3.6, h: 8, d: 3.6, p: P.root }],
      kids: [{ name: 'raizP' + i, pivot: [0, 7.6, 0], rot: [-Math.sin(ang) * 0.5, 0, Math.cos(ang) * 0.5], boxes: [{ x: -1.3, y: 0, z: -1.3, w: 2.6, h: 7, d: 2.6, p: P.root }] }],
    });
    return {
      name: 'raiz_', pivot: [0, 0, 0], kids: [{
        name: 'tronco', pivot: [0, 8, 0], boxes: [
          { x: -6, y: -22, z: -6, w: 12, h: 30, d: 12, p: P.bark },
          { x: -4, y: -25, z: -4, w: 8, h: 3.2, d: 8, p: P.bark },
          ...(lo ? [] : [{ x: -6.8, y: 2, z: -6.8, w: 13.6, h: 6, d: 13.6, p: P.root }]),
        ],
        kids: [
          ...eyes,
          { name: 'fenda', pivot: [0.6, -9, -6.2], boxes: [{ x: -0.6, y: -6, z: -0.5, w: 1.2, h: 12, d: 0.6, p: P.slit }] },
          branch(0, -2.2, 0.5, 9), branch(1, -0.6, 0.55, 8), branch(2, 1.0, 0.45, 10), ...(lo ? [] : [branch(3, 2.6, 0.6, 7)]),
          ...[0.6, 2.2, 3.8, 5.4].map((a, i) => root(i, a)),
        ],
      }],
    };
  }

  // fileira de raízes rompendo o chão: cada uma com grossura, inclinação e hora próprias
  function rootLine(seed, bark) {
    const g = new THREE.Group(), R = FX.rnd(seed), it = [];
    const mat = new THREE.MeshLambertMaterial({ color: new THREE.Color(bark[0] / 255 * 0.8, bark[1] / 255 * 0.8, bark[2] / 255 * 0.8) });
    for (let i = 0; i < 16; i++) {
      const f = i / 15, w = 0.08 + R() * 0.1, L = 0.5 + R() * 0.9 + (i > 12 ? 0.6 : 0);
      const geo = new THREE.CylinderGeometry(w * 0.25, w, L, 5); geo.translate(0, L / 2, 0);
      const m = new THREE.Mesh(geo, mat); g.add(m);
      it.push({ m, f, side: (R() - 0.5) * 0.7, tilt: (R() - 0.5) * 0.9, wrap: i > 11, L, a: R() * 6.28 });
    }
    g.at = (from, to, age) => {
      g.visible = age > 0;
      for (const p of it) {
        const k = FX.EASE.out(FX.clamp01((age - p.f * 0.5) / 0.18));
        const pos = from.clone().lerp(to, p.f);
        if (p.wrap) { pos.x = to.x + Math.cos(p.a) * 0.35; pos.z = to.z + Math.sin(p.a) * 0.35; }
        p.m.position.set(pos.x + (p.wrap ? 0 : p.side), -p.L * (1 - k), pos.z);
        p.m.rotation.set(p.wrap ? -Math.sin(p.a) * 0.35 : p.tilt * 0.6, 0, p.wrap ? Math.cos(p.a) * 0.35 : p.tilt);
        p.m.visible = age > p.f * 0.5;
      }
    };
    return g;
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size * 1.35; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    // pálpebras: 1 = fechada. Cada olho com seu jeito de abrir
    const lid = (i, k) => { if (P['palpebra' + i]) P['palpebra' + i].obj.scale.y = Math.max(0.05, k); };
    const sway = Math.sin(t * 0.5) * 0.03;
    const branches = (k = 1) => { for (let i = 0; i < 4; i++) { r('galho' + i, 0.06 * k * Math.sin(t * 0.7 + i), 0, 0.06 * k * Math.sin(t * 0.6 + i * 2)); r('ponta' + i, 0.12 * k * Math.sin(t * 1.1 + i), 0, 0); } };

    if (mode === 'parado') {
      r('tronco', sway, 0, sway * 0.6); branches();
      // disfarce: todos fechados; de vez em quando um abre, olha em volta e fecha
      for (let i = 0; i < EYES.length; i++) {
        const ph = fr(t / 5.3 + i * 0.37);
        const op = seq([[0, 0], [0.04, 1, 'snap'], [0.22, 1], [0.26, 0]], ph) * (i % 3 === Math.floor(t / 5.3) % 3 ? 1 : 0);
        lid(i, 1 - op);
        r('olho' + i, 0, 0.3 * Math.sin(t * 2 + i) * op, 0);
      }
      P.fenda && (P.fenda.obj.scale.x = 0.4);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 0.6;
      for (let i = 0; i < 4; i++) {
        const p = ph + i * 0.25, lift = Math.max(0, Math.sin(p * Math.PI * 2));
        r('raiz' + i, 0.25 * lift, 0, 0); r('raizP' + i, -0.3 * lift, 0, 0);
      }
      P.tronco.obj.position.y -= 0.8 * Math.abs(Math.sin(ph * Math.PI * 4));
      r('tronco', 0.06 * Math.sin(ph * Math.PI * 2), 0, 0.05 * Math.sin(ph * Math.PI * 4)); branches(1.5);
      for (let i = 0; i < EYES.length; i++) lid(i, i % 2 ? 1 : 0.3);
      P.fenda && (P.fenda.obj.scale.x = 0.5);
      return;
    }
    if (mode === 'raizes') {
      const T = t % 4.6;
      const R = ctx.ref; const RZ = -6.0; R.place(0, RZ, 0);
      // os olhos abrem, o tronco afunda as raízes no chão (agacha), e a fileira corre até o alvo
      const open = seq([[0.1, 0], [0.4, 1, 'snap']], T) * (1 - seq([[3.8, 0], [4.2, 1]], T));
      for (let i = 0; i < EYES.length; i++) lid(i, 1 - open);
      const dig = seq([[0.3, 0], [0.8, 1, 'out'], [3.6, 1], [4.2, 0]], T);
      P.tronco.obj.position.y += 2.2 * dig; r('tronco', 0.12 * dig, 0, 0);
      for (let i = 0; i < 4; i++) { r('raiz' + i, -0.4 * dig, 0, 0); r('raizP' + i, 0.6 * dig, 0, 0); }
      r('galho0', -0.4 * dig, 0, 0); r('galho2', -0.4 * dig, 0, 0); branches();
      P.fenda && (P.fenda.obj.scale.x = 0.4 + 2.4 * dig);
      const age = T - 0.8;
      if (age > 0) {
        ctx.get('linha', () => rootLine(13, e.v.bark)).at(new THREE.Vector3(0, 0, -1.3), new THREE.Vector3(0, 0, RZ), age);
        ctx.get('terra', () => FX.debris(26, 5, [[40, 34, 30], [60, 50, 40], [70, 90, 40], [80, 66, 50]])).at(new THREE.Vector3(0, 0.1, RZ), age - 0.45);
        if (age > 0.45) {
          const hold = seq([[0.45, 0], [0.6, 1, 'snap'], [2.6, 1], [3.0, 0]], age);
          R.place(0, RZ, 0.35 * hold); R.armsOut(0.5 * hold + 0.2 * Math.sin(T * 9) * hold); R.lean(0.1 * Math.sin(T * 7) * hold);
          ctx.shake(Math.max(0, 0.5 - (age - 0.45)));
        }
      }
      return;
    }
    if (mode === 'olhar') {
      const T = t % 4.8;
      const R = ctx.ref; const RZ = -4.5; R.place(0, RZ, 0);
      // abre um por um, de baixo pra cima (estalos), e todos viram pro alvo; a fenda escancara
      for (let i = 0; i < EYES.length; i++) {
        const st = 0.2 + (EYES.length - 1 - i) * 0.09;
        const op = seq([[st, 0], [st + 0.06, 1.15, 'snap'], [st + 0.2, 1]], T) * (1 - seq([[3.6 + i * 0.05, 0], [3.75 + i * 0.05, 1, 'snap']], T));
        lid(i, 1 - Math.min(1, op));
        if (P['olho' + i]) P['olho' + i].obj.scale.set(1 + 0.15 * (op - 1 > 0 ? 1 : 0), 1 + 0.15 * (op > 1 ? 1 : 0), 1);
        const f = EYES[i][2];
        r('olho' + i, -0.1 * op, f === 'west' ? -0.45 * op : f === 'east' ? 0.45 * op : 0, 0);
      }
      const stare = seq([[1.0, 0], [1.2, 1, 'snap'], [3.6, 1], [3.8, 0]], T);
      P.fenda && (P.fenda.obj.scale.x = 0.4 + 3 * stare);
      r('tronco', -0.06 * stare, 0, 0); branches(1 - stare);
      // o alvo trava (cada parte para onde estava) e a tela vai fechando de roxo
      R.lean(-0.08 * stare); R.armsOut(0.15 * stare);
      ctx.cursed(0.75 * stare); ctx.dizzy(0.4 * stare);
      ctx.get('brilho', () => DBFX.light(0xc8ff60)).at(new THREE.Vector3(0, 2.4, -1.2), 2.2 * stare, 6);
      return;
    }
    if (mode === 'morte') {
      // a árvore tomba de costas e fica deitada; as raízes de trás dobram contra o chão em vez de escorar o tronco
      const { fall } = JJFX.exorcize(t, obj, e, ctx, { h: 3.4, refZ: -2.4, rotX: 0, back: 0.2, ashY: 0.6, ashZ: 1.4, n: 240 });
      const ang = 1.52 * fall;
      obj.rotation.x = ang;
      for (let i = 0; i < 4; i++) { r('raiz' + i, -0.5 * fall, 0, 0); r('raizP' + i, 0.7 * fall, 0, 0); }
      JJFX.noSlide(obj, P.tronco.obj, 'z', () => { obj.rotation.x = 0; }, () => { obj.rotation.x = ang; }, 1.2 * fall);
      for (let i = 0; i < EYES.length; i++) lid(i, 1 - 0.8 * Math.max(0, 1 - fall * 2));
      branches(1 + 2 * fall);
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(6), variant(30), variant(72)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'olhos', nome: 'Olhos da Mata', bioma: 'Floresta Negra', tier: 'Grau 2 · tier 7',
    variants: fixed.map((v, i) => ({ nome: ['Casca escura', 'Cinza', 'Musgo'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Disfarçado'], ['andar', 'Andar'], ['raizes', 'Raízes'], ['olhar', 'Olhar Fixo'], ['morte', 'Exorcizado']],
    cam: 8, lookY: 1.8, lookZ: { raizes: -2.6, olhar: -2.0, morte: -0.8 }, camFar: { raizes: 10, olhar: 8.5, morte: 8 }, camYaw: { raizes: -1.25, olhar: -0.95, morte: -1.0 },
    info: {
      'Tamanho': '3 blocos de altura, igual a um tronco grosso',
      'Vida': '110 (grau 2)',
      'Jeito': 'Fica parado no meio das árvores de olhos fechados (parece uma árvore morta). 1 ou 2 por mata. Abre um olho de vez em quando: se você ver um olho piscando no tronco, é ele. Só anda quando já te viu.',
      'Raízes': 'Afunda as raízes no chão e uma fileira de raízes rompe a terra até você; as últimas te enrolam (preso 2 s). Correr de lado escapa da fileira.',
      'Olhar Fixo': 'Os nove olhos abrem um por um e viram pra você: paralisado e com a tela fechando de roxo enquanto estiver de frente pra ele (2,5 s). Virar a câmera de costas quebra o olhar.',
      'Solta': 'Resíduo; madeira amaldiçoada (bloco de construção); raro: olho da mata (item: mostra mobs escondidos por 30 s).',
      'Variações': 'Cor da casca, musgo, cor da íris.',
    },
  });
})();
