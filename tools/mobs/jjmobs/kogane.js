// Kogane (Colônia). NPC. O shikigami de crânio branco com asinhas que acompanha cada jogador do Jogo do Abate: anuncia regras novas,
// mostra os pontos e registra quando alguém pontua. Não luta e não morre (golpe atravessa). Fica flutuando do
// lado do jogador, pisca, fala com uma placa em cima da cabeça.
// Ações: Anunciar (pula na sua frente e mostra "NOVA REGRA!") e Pontos (gira feliz e solta "+5 PTS" que voa pra você).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  // fonte 3x5 em pixel (só o que a Kogane fala)
  const F = {
    A: ['.#.', '#.#', '###', '#.#', '#.#'], E: ['###', '#..', '##.', '#..', '###'], G: ['.##', '#..', '#.#', '#.#', '.##'], N: ['#..#', '##.#', '#.##', '#..#', '#..#'],
    O: ['.#.', '#.#', '#.#', '#.#', '.#.'], R: ['##.', '#.#', '##.', '#.#', '#.#'], V: ['#.#', '#.#', '#.#', '#.#', '.#.'], P: ['###', '#.#', '###', '#..', '#..'],
    T: ['###', '.#.', '.#.', '.#.', '.#.'], S: ['.##', '#..', '.#.', '..#', '##.'], '!': ['.#.', '.#.', '.#.', '...', '.#.'], '+': ['...', '.#.', '###', '.#.', '...'],
    '5': ['###', '#..', '##.', '..#', '##.'], ' ': ['...', '...', '...', '...', '...'],
  };
  function plate(text, fg, bg) {
    const gl = [...text].map(ch => F[ch] || F[' ']), xs = []; let cx = 2; gl.forEach(G => { xs.push(cx); cx += G[0].length + 1; });
    const cw = cx + 1, chh = 9, cv = document.createElement('canvas'); cv.width = cw; cv.height = chh;
    const g = cv.getContext('2d'); g.fillStyle = bg; g.fillRect(0, 0, cw, chh); g.fillStyle = 'rgba(0,0,0,0.4)'; g.fillRect(0, chh - 1, cw, 1);
    gl.forEach((G, i) => G.forEach((row, y) => [...row].forEach((c, x) => { if (c === '#') { g.fillStyle = fg; g.fillRect(xs[i] + x, 2 + y, 1, 1); } })));
    const tex = new THREE.CanvasTexture(cv); tex.magFilter = tex.minFilter = THREE.NearestFilter; tex.generateMipmaps = false;
    const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, transparent: true, depthTest: false }));
    sp.scale.set(cw / 26, chh / 26, 1); sp.renderOrder = 10; return sp;
  }

  // Kogane: crânio branco com olhos ocos, asinhas de anjo, corpo de larva em 3 gomos com as mãozinhas cruzadas,
  // rosca cinza no fim e o rabo preto de diabinho enrolado. Cada jogador tem a sua (variações do mangá).
  // [nome, olhos, ponta do rabo, chifre, língua]
  const KINDS = [['Do Yuta', 'oco', 'espada', false, false], ['Do Reggie', 'redondo', 'flecha', true, true], ['Do Hazenoki', 'xis', 'bola', true, false], ['Do Uro', 'fenda', 'flecha', true, false], ['Do Ishigoori', 'coracao', 'espada', true, false]];
  function variant(seed, kind) { const R = FX.rnd(seed); const k = kind ?? Math.floor(R() * KINDS.length); const K = KINDS[k]; return { seed, kind: k, eyes: K[1], tip: K[2], horn: K[3], tongue: K[4], size: 1 }; }

  // rosto 11x8 (1 texel por unidade, igual Minecraft). K = buraco preto, g = borda cinza do buraco, d = narina
  const EYES = {
    oco: ['...........', '.KKK...KKK.', '.KKKK.KKKK.', '.KKKK.KKKK.', '.gKKK.KKKg.', '..KKg.gKK..'],
    redondo: ['...........', '.gKKg.gKKg.', '.KKKK.KKKK.', '.KKKK.KKKK.', '.gKKg.gKKg.', '...........'],
    xis: ['...........', '.gKKg.gKKg.', '.KgKK.KgKK.', '.KKgK.KKgK.', '.gKKg.gKKg.', '...........'],
    fenda: ['...........', '.gggg.gggg.', '.KKKK.KKKK.', '.gggg.gggg.', '...........', '...........'],
    coracao: ['...........', '.......K.K.', '.gggg.KKKKK', '.KKKK..KKK.', '........K..', '...........'],
  };
  const FACE = v => [...EYES[v.eyes], '....d.d....', '...........'];
  const SC = 0.62; // ela é pequena: some na altura da cabeça do jogador
  const WHITE = [242, 240, 232], GREY = [168, 176, 170], LINE = [118, 124, 120], BLACK = [40, 40, 44];

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.84, hi = 1.06) => { const f = 0.66 - 0.26 * q.ny + 0.06 * (n3(q.ax * 0.5, q.ay * 0.5, q.az * 0.5, s + S) - 0.5) + (q.f === 'top' ? 0.18 : 0) - (q.f === 'bottom' ? 0.3 : 0); return cel(base, f, lo, hi); };
    const P = {};
    // osso branco com sombra cinza-esverdeada embaixo, como no anime
    P.bone = q => { const c = shade(WHITE, q, 1); return q.f !== 'top' && q.ny > 0.78 ? mix(c, GREY, 0.7) : c; };
    P.face = q => {
      if (q.f !== 'north') return P.bone(q);
      const F = FACE(v), n = F[0].length, col = n - 1 - Math.min(n - 1, Math.floor(q.px * n / q.b.w)), row = Math.min(F.length - 1, Math.floor(q.py * F.length / q.b.h));
      const ch = F[row][col];
      if (ch === 'K') return BLACK;
      if (ch === 'g') return GREY;
      if (ch === 'W') return [230, 230, 230];
      if (ch === 'd') return [70, 70, 74];
      return P.bone(q);
    };
    // gomo: branco com contorno cinza na borda de baixo (a "linha" que separa os gomos)
    P.seg = q => { const c = shade(WHITE, q, 2); if (q.f === 'bottom') return LINE; return q.py > q.b.h - 0.6 ? mix(c, GREY, 0.75) : c; };
    // dentinhos: alterna osso e risco cinza
    P.teeth = q => { if (q.f !== 'north') return P.bone(q); const c = Math.floor(q.px); return (c % 2 === 1 || q.py > q.b.h - 0.5) ? LINE : shade(WHITE, q, 3, 0.95, 1.08); };
    P.hand = q => { const c = shade(WHITE, q, 4, 0.92, 1.08); return q.f === 'bottom' ? GREY : c; };
    P.feather = q => { const c = shade(WHITE, q, 5, 0.86, 1.08); return q.f === 'bottom' || (q.f !== 'top' && q.ny > 0.7) ? mix(c, GREY, 0.6) : c; };
    P.screw = q => { const c = shade([150, 154, 152], q, 6, 0.8, 1.1); return q.f === 'bottom' ? mul(c, 0.6) : c; };
    P.tail = q => shade(BLACK, q, 7, 0.75, 1.5);
    P.tongue = q => shade([236, 120, 140], q, 8, 0.85, 1.1);
    P.horn = q => shade([236, 232, 220], q, 9, 0.85, 1.08);
    return P;
  }

  // rabo: caminho (x,y) no plano da frente: desce, faz um laço por cima e sai pro lado; a ponta é uma parte própria
  function tailPath() {
    // desce, curva pra direita por baixo, sobe dando a volta (espiral, sai um pouco pra frente) e sai pra direita
    const pts = [];
    for (let i = 0; i <= 8; i++) pts.push([0, i * 0.6, 0]);
    const cx = 2.4, cy = 6.6, A0 = Math.PI, A1 = -1.5 * Math.PI;
    for (let a = A0; a >= A1; a -= 0.15) { const k = (A0 - a) / (A0 - A1), r = 2.4 - 0.4 * k; pts.push([cx + r * Math.cos(a), cy + r * Math.sin(a), -1.1 * k]); }
    const last = pts[pts.length - 1];
    for (let i = 1; i <= 7; i++) pts.push([last[0] + i * 0.5, last[1] - i * (0.02 + 0.035 * i), last[2]]);
    return pts;
  }
  // rabo contínuo: cada trecho é um bloco comprido virado na direção do caminho (emenda sobrando nas pontas),
  // então o rabo é um tubo só, sem degrau nem cubo solto; a ponta fica presa no último trecho
  function tailParts(P, v, lo) {
    const pts = tailPath(), step = lo ? 3 : 2, kids = [];
    for (let i = 0; i + step < pts.length; i += step) {
      const a = pts[i], b = pts[Math.min(pts.length - 1, i + step)], dx = b[0] - a[0], dy = b[1] - a[1], L = Math.hypot(dx, dy);
      kids.push({ name: 'rabo' + kids.length, pivot: [a[0], a[1], a[2]], rot: [0, 0, Math.atan2(dy, dx)], boxes: [{ x: -0.55, y: -0.6, z: -0.6, w: L + 1.1, h: 1.2, d: 1.2, p: P.tail }] });
    }
    kids.push(tipPart(P, v));
    return kids;
  }
  function tipPart(P, v) {
    const pts = tailPath(), e = pts[pts.length - 1], q = pts[pts.length - 3], ang = Math.atan2(e[1] - q[1], e[0] - q[0]);
    // espada = triângulo em degraus (base larga presa no rabo); flecha = chevron fino; bola = bolinha. Tudo encostado.
    const B = v.tip === 'bola' ? [{ x: -0.3, y: -1.0, z: -0.9, w: 2.2, h: 2.0, d: 1.8, p: P.tail }, { x: 0.1, y: -1.3, z: -0.6, w: 1.4, h: 2.6, d: 1.2, p: P.tail }]
      : v.tip === 'flecha' ? [{ x: -0.4, y: -0.6, z: -0.6, w: 3.2, h: 1.2, d: 1.2, p: P.tail }, { x: 0.4, y: -1.5, z: -0.45, w: 0.9, h: 3.0, d: 0.9, p: P.tail }, { x: 1.2, y: -1.0, z: -0.45, w: 0.9, h: 2.0, d: 0.9, p: P.tail }]
      : [{ x: -0.4, y: -1.5, z: -0.5, w: 0.7, h: 1.3, d: 1.0, p: P.tail }, { x: -0.4, y: 0.2, z: -0.5, w: 0.7, h: 1.3, d: 1.0, p: P.tail }, ...[3.0, 2.6, 2.0, 1.4, 0.7].map((h, i) => ({ x: 0.25 + i * 0.6, y: -h / 2, z: -0.5, w: 0.65, h, d: 1.0, p: P.tail }))];
    return { name: 'ponta', pivot: [e[0], e[1], e[2]], rot: [0, 0, ang], boxes: B };
  }

  function wing(s, P, lo) {
    // asa de anjo: braço grosso em cima (sai da cabeça pra fora) e penas em "dedos" pendurados embaixo,
    // as do meio mais compridas (recorte redondinho), e uma fileira curta de cobertura na frente (s = lado)
    const X = (a, w) => (s > 0 ? a : -a - w);
    const B = [{ x: X(0, 12.5), y: -1.4, z: -0.7, w: 12.5, h: 1.8, d: 1.4, p: P.feather }, { x: X(-1.2, 7), y: -2.2, z: -0.8, w: 7, h: 1.0, d: 1.6, p: P.feather }];
    const H = lo ? [3, 3.8, 4, 3.6, 2.8, 2] : [2.6, 3.4, 4.0, 4.3, 4.2, 3.8, 3.2, 2.4];
    const fw = lo ? 1.9 : 1.45;
    H.forEach((h, i) => B.push({ x: X(0.6 + i * fw, fw), y: 0.1, z: -0.5, w: fw, h, d: 1.0, p: P.feather }));
    if (!lo) [1.8, 2.1, 2.3, 2.2, 1.9, 1.5].forEach((h, i) => B.push({ x: X(0.9 + i * 1.75, 1.75), y: 0.1, z: -0.8, w: 1.75, h, d: 0.5, p: P.feather }));
    return B;
  }

  function rig(P, v, lo) {
    const kids = [
      { name: 'asaD', pivot: [-5.2, -5, 1], rot: [0, -0.25, 0.4], boxes: wing(-1, P, lo) },
      { name: 'asaE', pivot: [5.2, -5, 1], rot: [0, 0.25, -0.4], boxes: wing(1, P, lo) },
      { name: 'cauda', pivot: [0, 13.6, 0], kids: tailParts(P, v, lo) },
      { name: 'maoD', pivot: [-1.6, 3.8, -3.6], boxes: [{ x: -1.4, y: -0.7, z: -0.6, w: 2.6, h: 1.4, d: 1.2, p: P.hand }] },
      { name: 'maoE', pivot: [1.6, 3.8, -3.6], boxes: [{ x: -1.2, y: -0.7, z: -0.6, w: 2.6, h: 1.4, d: 1.2, p: P.hand }] },
    ];
    if (v.horn) kids.push({ name: 'chifre', pivot: [-2, -8, -1], rot: [0, 0, 0.25], boxes: [{ x: -0.8, y: -1.5, z: -0.8, w: 1.6, h: 1.5, d: 1.6, p: P.horn }, { x: -0.5, y: -3.2, z: -0.5, w: 1, h: 1.7, d: 1, p: P.horn }, { x: -0.25, y: -4.4, z: -0.25, w: 0.5, h: 1.2, d: 0.5, p: P.horn }] });
    if (v.tongue) kids.push({ name: 'lingua', pivot: [0, 1.6, -3.8], boxes: [{ x: -1, y: 0, z: -0.4, w: 2, h: 1.6, d: 0.6, p: P.tongue }, { x: -0.5, y: 1.6, z: -0.4, w: 1, h: 0.8, d: 0.6, p: P.tongue }] });
    const screw = [];
    screw.push({ x: -1.15, y: 11.2, z: -1.15, w: 2.3, h: 3.0, d: 2.3, p: P.screw });
    for (let i = 0; i < 5; i++) screw.push({ x: -1.4 + (i % 2) * 0.25, y: 11.4 + i * 0.5, z: -1.4 + (i % 2) * 0.25, w: 2.8 - (i % 2) * 0.5, h: 0.5, d: 2.8 - (i % 2) * 0.5, p: P.screw });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'corpo', pivot: [0, 6, 0], boxes: [
          // crânio: bloco do rosto 11x8 + topo arredondado + bochechas
          { x: -5.5, y: -7, z: -4.5, w: 11, h: 8, d: 8.5, p: P.face },
          { x: -4.5, y: -8, z: -3.8, w: 9, h: 1, d: 7.2, p: P.bone },
          { x: -3.2, y: -8.7, z: -3, w: 6.4, h: 0.7, d: 5.6, p: P.bone },
          { x: -6.2, y: -6.2, z: -3.8, w: 12.4, h: 4.6, d: 7.2, p: P.bone },
          { x: -4.6, y: -6.4, z: 4, w: 9.2, h: 6, d: 0.8, p: P.bone },
          // focinho com os dentinhos
          { x: -2.6, y: 0.6, z: -4.6, w: 5.2, h: 1.0, d: 2.4, p: P.bone },
          { x: -2.4, y: 1.6, z: -4.5, w: 1.4, h: 1.1, d: 1.2, p: P.hand },
          { x: -0.7, y: 1.6, z: -4.5, w: 1.4, h: 1.3, d: 1.2, p: P.hand },
          { x: 1.0, y: 1.6, z: -4.5, w: 1.4, h: 1.1, d: 1.2, p: P.hand },
          // gomos do corpo (afinando) + ponta + rosca
          { x: -4.2, y: 1, z: -3.4, w: 8.4, h: 3.6, d: 6.6, p: P.seg },
          { x: -4.6, y: 1.8, z: -3.0, w: 9.2, h: 2.0, d: 5.8, p: P.bone },
          { x: -3.6, y: 4.6, z: -2.9, w: 7.2, h: 3, d: 5.8, p: P.seg },
          { x: -4.0, y: 5.3, z: -2.5, w: 8.0, h: 1.6, d: 5.0, p: P.bone },
          { x: -2.8, y: 7.6, z: -2.3, w: 5.6, h: 2.6, d: 4.6, p: P.seg },
          { x: -3.1, y: 8.1, z: -2.0, w: 6.2, h: 1.4, d: 4.0, p: P.bone },
          { x: -1.8, y: 10.2, z: -1.7, w: 3.6, h: 1.2, d: 3.4, p: P.seg },
          ...screw,
        ],
        kids,
      }],
    };
  }

  // balão de papel recortado (como o da Kogane no anime): cinza claro, borda irregular, letra de mangá
  function bubble(lines, seed) {
    const FONT = 'italic 900 38px "Arial Black", Impact, sans-serif', mc = document.createElement('canvas').getContext('2d'); mc.font = FONT;
    const W = Math.ceil(Math.max(...lines.map(l => mc.measureText(l).width)) + 70), H = 70 + lines.length * 46, cv = document.createElement('canvas'); cv.width = W; cv.height = H;
    const g = cv.getContext('2d'), R = FX.rnd(seed);
    const pts = [], N = 26;
    for (let i = 0; i < N; i++) {
      const t = i / N, side = Math.floor(t * 4), f = (t * 4) % 1, j = () => (R() - 0.5) * 9;
      const m = 12;
      const p = side === 0 ? [m + f * (W - 2 * m), m] : side === 1 ? [W - m, m + f * (H - 2 * m)] : side === 2 ? [W - m - f * (W - 2 * m), H - m] : [m, H - m - f * (H - 2 * m)];
      pts.push([p[0] + j(), p[1] + j()]);
    }
    const path = () => { g.beginPath(); pts.forEach(([x, y], i) => (i ? g.lineTo(x, y) : g.moveTo(x, y))); g.closePath(); };
    g.save(); g.translate(5, 6); path(); g.fillStyle = 'rgba(0,0,0,0.35)'; g.fill(); g.restore();
    path(); g.fillStyle = '#d9dad6'; g.fill(); g.lineWidth = 5; g.strokeStyle = '#3c3e40'; g.stroke();
    g.fillStyle = '#26282a'; g.textAlign = 'center'; g.textBaseline = 'middle';
    g.font = FONT;
    lines.forEach((l, i) => g.fillText(l, W / 2, 35 + 23 + i * 46));
    const tex = new THREE.CanvasTexture(cv); tex.anisotropy = 4;
    const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, transparent: true, depthTest: false }));
    sp.userData.ar = H / W; sp.userData.wr = W / 320; sp.renderOrder = 10; return sp;
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    JJFX.dissolve(e, 0);
    const W = (n, x, y, z) => { obj.updateMatrixWorld(true); return new THREE.Vector3(x, y, z).applyMatrix4(P[n].obj.matrixWorld); };
    // asas batendo (fase, amplitude); rabo balança com atraso; mãozinhas mexendo
    const flap = (sp, amp, base = 0) => { const f = Math.sin(t * sp); r('asaD', 0, 0.15 * f, base + amp * f); r('asaE', 0, -0.15 * f, -base - amp * f); };
    const tailSway = (k = 1) => { r('cauda', 0, 0, 0.12 * k * Math.sin(t * 1.7 - 0.8)); };
    const hands = k => { r('maoD', 0, 0, 0.25 * k); r('maoE', 0, 0, -0.25 * k); };
    const sparkle = (key, at, age, n = 20, sp = 1) => ctx.get(key, () => DBFX.grains({ n, seed: key.length * 13 + 7, add: true, palette: [[255, 255, 255], [230, 236, 240], [255, 240, 200]], size: [0.015, 0.035], drag: 1.6, grav: -0.2, life: 1.0, spread: 0.6, cone: R2 => { const a2 = R2() * 6.28; return [Math.cos(a2) * sp, (R2() - 0.3) * sp, Math.sin(a2) * sp]; } })).at(at, age, -9);
    // balão: sai da cabeça com estalo, fica do lado de cima
    const say = (key, lines, k, w = 1.5, off = [0.9, 0.75]) => {
      const p = ctx.get(key, () => bubble(lines, key.length * 31));
      p.visible = k > 0.01; p.material.opacity = Math.min(1, k * 1.5);
      const c = W('corpo', 0, -4, 0), s2 = w * Math.min(1, 0.4 + 0.6 * k);
      p.position.set(c.x + off[0] * 0.5, c.y + off[1] - 0.12 * (1 - Math.min(1, k)), c.z); p.scale.set(s2 * p.userData.wr, s2 * p.userData.wr * p.userData.ar, 1);
    };
    const bob = (amp = 0.08) => { obj.position.y += 0.3 + amp * Math.sin(t * 2.2); };
    const size = (k = 1) => e.obj.children[0].scale.setScalar(Math.max(0.001, SC * k));
    size(1);

    if (mode === 'parado') {
      // flutua do lado do jogador; asinhas batendo leve, rabo enrolado balançando, inclina a cabeça curiosa
      bob();
      flap(7, 0.22, 0);
      r('corpo', 0.04 * Math.sin(t * 2.2 + 1), 0.3 * Math.sin(t * 0.5), 0.12 * Math.sin(t * 0.9) * (Math.sin(t * 0.3) > 0.4 ? 1 : 0.2));
      tailSway(1); hands(0.2 * Math.sin(t * 1.4));
      return;
    }
    if (mode === 'andar') {
      // segue o jogador voando, asas batendo rápido e corpo inclinado pra frente
      bob(0.15);
      obj.position.z += -0.4 * Math.sin(t * 0.6);
      flap(14, 0.35, 0.1);
      r('corpo', -0.22, 0, 0.05 * Math.sin(t * 3));
      r('cauda', 0.35, 0, 0.18 * Math.sin(t * 3 - 0.8));
      return;
    }
    if (mode === 'oi') {
      // "OI, EU SOU UMA KOGANE!": chega perto, dá uma voltinha e se apresenta
      const T = t % 4.4;
      const R = ctx.ref; R.place(0, -2.0, 0); R.spin(Math.PI);
      bob(0.06); obj.position.z += -0.5;
      const hop = seq([[0.3, 0], [0.55, 1, 'snap'], [0.8, 0, 'io']], T);
      obj.position.y += 0.2 * hop; flap(10, 0.28 + 0.25 * hop, 0);
      r('corpo', 0, 0, 0.16 * Math.sin(T * 3) * FX.clamp01(T - 0.6));
      tailSway(1.5); hands(0.4 * Math.sin(T * 5));
      say('oi', ['OI, EU SOU', 'UMA KOGANE!'], seq([[0.6, 0], [0.8, 1.15, 'snap'], [0.95, 1], [3.9, 1], [4.2, 0]], T), 1.25);
      return;
    }
    if (mode === 'anuncio') {
      const T = t % 5.0;
      const R = ctx.ref; R.place(0, -2.2, 0); R.spin(Math.PI);
      // aparece num estalo na sua frente, abre as asas inteiras, chacoalha e o balão do anúncio sai da cabeça
      const pop = seq([[0.2, 0], [0.35, 1.15, 'snap'], [0.5, 1, 'io'], [4.6, 1], [4.9, 0]], T);
      size(pop);
      bob(0.05); obj.position.z += -0.6;
      const open = seq([[0.5, 0], [0.7, 1, 'snap'], [4.2, 1], [4.5, 0]], T);
      r('asaD', 0, 0, 0.5 * open); r('asaE', 0, 0, -0.5 * open); flap(16, 0.25 * open + 0.1, 0);
      r('corpo', -0.08 * open, 0, 0.08 * Math.sin(T * 9) * open);
      tailSway(2); hands(open * 0.5);
      if (T > 0.2 && T < 1.2) sparkle('estalo', W('corpo', 0, -3.5, 0), T - 0.2, 40, 1.4);
      say('regra', ['UM JOGADOR', 'ADICIONOU UMA', 'REGRA AO JOGO', 'DO ABATE!'], seq([[0.8, 0], [1.0, 1.15, 'snap'], [1.15, 1], [4.4, 1], [4.7, 0]], T), 1.4, [1.0, 1.05]);
      return;
    }
    if (mode === 'pontos') {
      const T = t % 4.2;
      const R = ctx.ref; R.place(0, -2.0, 0); R.spin(Math.PI);
      // gira no ar feliz e anuncia os pontos; um brilho voa até o peito do jogador
      bob(0.06);
      const spin = seq([[0.2, 0], [1.0, 1, 'io']], T);
      P.corpo.obj.rotation.y += Math.PI * 2 * spin;
      flap(12, 0.3 + 0.3 * Math.sin(Math.PI * spin), 0);
      tailSway(1.5); hands(0.5 * Math.sin(T * 6));
      say('ganho', ['+5 PONTOS!'], seq([[1.0, 0], [1.2, 1.15, 'snap'], [1.35, 1], [3.6, 1], [3.9, 0]], T), 1.1, [0.9, 0.7]);
      const k = FX.clamp01((T - 1.2) / 1.2);
      if (T > 1.2 && T < 2.4) {
        const from = W('corpo', 0, 0, 0), to = new THREE.Vector3(0, 1.3, -2.0), p = from.clone().lerp(to, FX.EASE.io(k)); p.y += 0.4 * Math.sin(Math.PI * k);
        ctx.get('bola', () => DBFX.glow([1, 0.95, 0.75])).at(p, 0.1, 1.2, t);
        sparkle('rastro', p, fr(T * 2) * 0.5, 16, 0.4);
      }
      if (T > 2.4 && T < 3.4) { sparkle('chega', new THREE.Vector3(0, 1.3, -2.0), T - 2.4, 40, 1.2); ctx.get('peito', () => DBFX.glow([1, 0.95, 0.75])).at(new THREE.Vector3(0, 1.25, -2.05), 0.25, 1.4 * (1 - (T - 2.4)), t); }
      return;
    }
    if (mode === 'some') {
      // não morre: golpe atravessa, ela some num punhado de brilho e reaparece do outro lado
      const T = t % 3.4;
      bob(0.04); flap(10, 0.25, 0); tailSway(1);
      const out = seq([[0.4, 1], [0.7, 0, 'in'], [2.2, 0], [2.5, 1.15, 'snap'], [2.7, 1, 'io']], T);
      size(out);
      obj.position.x += T > 1.4 ? 1.2 : 0;
      if (T > 0.6 && T < 1.6) sparkle('sumiu', new THREE.Vector3(0, 1.2, 0), T - 0.6, 50, 1.6);
      if (T > 2.2 && T < 3.2) sparkle('voltou', new THREE.Vector3(1.2, 1.2, 0), T - 2.2, 50, 1.6);
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(1, 0), variant(2, 1), variant(3, 2), variant(4, 3), variant(5, 4)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'kogane', nome: 'Kogane', bioma: 'Colônia', tier: 'NPC (não luta)', refX: 1.4,
    variants: fixed.map(v => ({ nome: KINDS[v.kind][0], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: KINDS[v.kind][0], v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Seguir'], ['oi', 'Se Apresentar'], ['anuncio', 'Anunciar Regra'], ['pontos', 'Pontos'], ['some', 'Sumir']],
    cam: 3.4, lookY: 1.25, lookZ: { anuncio: -1.0, pontos: -1.0, oi: -1.0 }, camFar: { anuncio: 4.6, pontos: 4.6, oi: 4.4 }, camYaw: { anuncio: -0.8, pontos: -1.0, oi: -0.8 },
    info: {
      'Tamanho': 'Um bloco e meio do chifre à ponta do rabo, flutua na altura da cabeça',
      'Vida': 'Não morre (golpe atravessa)',
      'Jeito': 'Aparece do seu lado quando você entra numa Colônia e fica te seguindo. Cada jogador do abate tem a sua. Fala com placas em cima da cabeça.',
      'Anunciar Regra': 'Quando alguém adiciona uma regra nova ao Jogo do Abate (evento do bioma), ela aparece na sua frente e mostra a regra.',
      'Pontos': 'Quando você derrota um Jogador do Abate, ela gira e os pontos dele voam pra você. Com 100 pontos dá pra adicionar uma regra (ex.: "maldições dão o dobro de pontos por 1 dia").',
      'Loja': 'Clicar nela abre a troca de pontos: Pontos do Abate por itens raros das colônias.',
      'Variações': 'Uma por jogador, como no mangá: do Yuta (olhos ocos, rabo de espada), do Reggie (língua de fora, rabo de flecha), do Hazenoki (olhos em X, rabo de bola), do Uro (olhos em fenda) e do Ishigoori (olho de coração). As do mangá têm chifrinho.',
    },
  });
})();
