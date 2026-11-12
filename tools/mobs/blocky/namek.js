// Namekianos (Namek). Referências: referencias/mobs-db/namekianos/.
// Três tipos (guerreiro, aldeão, ancião). Dentro do tipo, cada um nasce de uma semente: tom de verde, roupa,
// antenas, cicatriz, bigode e o jeito de parar mudam. No jogo a textura sai da mesma pintura com a semente da entidade.
'use strict';
(() => {
  const { n3, mix, mul, cel } = ENG;
  const fr = x => x - Math.floor(x);
  function rng(seed) { let a = seed * 2654435761 >>> 0; return () => { a = a + 0x6D2B79F5 >>> 0; let t = a; t = Math.imul(t ^ t >>> 15, t | 1); t ^= t + Math.imul(t ^ t >>> 7, t | 61); return ((t ^ t >>> 14) >>> 0) / 4294967296; }; }
  const pick = (R, l) => l[Math.floor(R() * l.length)];

  function makeNamek(type, seed, force) {
    const R = rng(seed);
    const SK = mix(pick(R, [[96, 178, 62], [78, 150, 64], [84, 160, 104], [108, 170, 58]]), [70, 140, 70], R() * 0.35);
    const SEG = type === 'anciao' ? [214, 150, 96] : mix([236, 150, 172], [226, 128, 150], R());   // gomos dos braços
    const PLATE = type === 'anciao' ? [214, 160, 104] : [222, 168, 124];                            // placas da barriga
    const VEST = type === 'guerreiro' ? pick(R, [[52, 50, 112], [92, 52, 132], [34, 74, 86], [96, 58, 42]])
      : type === 'aldeao' ? pick(R, [[52, 52, 58], [168, 138, 208], [124, 42, 58]]) : pick(R, [[152, 34, 42], [188, 48, 70], [120, 30, 40]]);
    const ROBE = type === 'aldeao' ? pick(R, [[236, 234, 228], [240, 172, 52], [236, 234, 228]]) : [238, 238, 234];
    const SASH = type === 'guerreiro' ? pick(R, [[62, 92, 196], [190, 54, 54], [220, 170, 50]])
      : type === 'aldeao' ? pick(R, [[190, 110, 130], [120, 80, 170], [70, 120, 90]]) : [132, 82, 172];
    const scar = type === 'guerreiro' && R() < 0.6;
    const stache = type === 'anciao' || R() < 0.15;
    const antL = 2.5 + R() * 1.6, antR = 2.5 + R() * 1.6, antBend = 0.4 + R() * 0.5;
    const eyeLook = R() < 0.5 ? 0 : 1;
    const elder = type === 'anciao';

    function skin(q, k = 0) {
      const v = 0.62 - 0.38 * q.ny + 0.3 * (n3(q.ax * 0.25, q.ay * 0.25, q.az * 0.25, seed) - 0.5) + (q.f === 'top' ? 0.22 : 0) + k;
      return cel(SK, v, 0.78, 1.12);
    }
    function head(q) {
      let c = skin(q, 0.06);
      // rugas da testa e nuca (sulcos que dão a volta na cabeça)
      if (q.f !== 'bottom' && q.ny < 0.32) {
        const ridge = fr(q.ay * 0.6 + n3(q.ax * 0.3, 0, q.az * 0.3, seed + 1) * 0.6);
        if (ridge < 0.22) c = mul(SK, 0.74);
      }
      if (q.f !== 'north') return c;
      const x = Math.floor(q.px), y = Math.floor(q.py), W = q.b.w;
      const L = [W / 2 - 2.5, W / 2 + 1.5];
      if (y === 4 && (x === Math.floor(L[0]) || x === Math.floor(L[0]) + 1 || x === Math.floor(L[1]) || x === Math.floor(L[1]) + 1)) c = mul(SK, 0.45); // sobrancelha
      if (y === 5) for (const ex of L) {
        if (x === Math.floor(ex) || x === Math.floor(ex) + 1) c = [248, 248, 240];
        if (x === Math.floor(ex) + (ex < W / 2 ? 1 : 0) - eyeLook * (ex < W / 2 ? 1 : 0) + eyeLook * (ex < W / 2 ? 0 : 1) && !elder) c = [24, 26, 20];
      }
      if (elder && y === 5) for (const ex of L) if (x === Math.floor(ex) || x === Math.floor(ex) + 1) c = mix([248, 248, 240], SK, 0.35);  // pálpebra caída
      if (y === 6 && (x === W / 2 - 1 || x === W / 2)) c = mul(SK, 0.8);
      if (y === 7 && x >= W / 2 - 2 && x <= W / 2 + 1) c = [98, 60, 110];
      if (elder && (y === 6 || y === 8) && (x === 1 || x === W - 2)) c = mul(SK, 0.72);          // rugas da bochecha
      if (stache && y === 7 && (x === W / 2 - 3 || x === W / 2 + 2)) c = [236, 236, 228];
      if (stache && y === 8 && (x === W / 2 - 3 || x === W / 2 + 2)) c = [220, 220, 212];
      if (scar && Math.abs((x - 1) - (y - 2) * 0.9) < 0.6 && y >= 2 && y <= 6 && x < W / 2) c = [150, 64, 70];
      return c;
    }
    function ear(q) {
      // orelha pontuda: triângulo que sai pro lado e sobe
      const out = q.nx, up = 1 - q.ny;
      const s = q.b.x < 0 ? 1 - out : out;
      if (up > 1 - s * 0.92 + 0.05) return null;
      let c = skin(q, 0.05);
      if ((q.f === 'north') && up < 0.6 - s * 0.5 && s < 0.7) c = mul(SK, 0.78);
      return c;
    }
    const ant = q => skin(q, 0.1);
    function scarf(q) {
      const fold = fr(q.ay * 0.45 + q.ax * 0.18 + n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, seed + 3) * 0.8);
      let c = cel(ROBE.length && type === 'aldeao' && ROBE[2] < 100 ? [240, 240, 236] : [240, 240, 236], 0.65 - 0.4 * q.ny, 0.82, 1.03);
      if (fold < 0.14) c = [196, 200, 212];
      return c;
    }
    function torso(q) {
      let c = skin(q, 0.04);
      if (q.f !== 'north' || type === 'aldeao') return c;
      // peito verde e placas da barriga no meio
      const cx = Math.abs(q.nx - 0.5);
      if (q.ny > 0.42 && cx < 0.22) {
        const row = fr(q.py / 1.4), mid = Math.abs(cx) < 0.03;
        c = row < 0.22 || mid ? mul(PLATE, 0.72) : cel(PLATE, 0.7 - q.ny * 0.3, 0.85, 1.05);
      }
      if (q.ny < 0.42 && q.ny > 0.36 && cx < 0.3) c = mul(SK, 0.72);       // linha de baixo do peitoral
      return c;
    }
    function vest(q, open) {
      if (open && q.f === 'north' && Math.abs(q.nx - 0.5) < 0.3) return null;
      if (open && q.f === 'top' && Math.abs(q.nx - 0.5) < 0.3 && q.nz < 0.6) return null;
      const v = 0.62 - 0.38 * q.ny + 0.3 * (n3(q.ax * 0.18, q.ay * 0.18, q.az * 0.18, seed + 4) - 0.5);
      let c = cel(VEST, v, 0.72, 1.18);
      if (open && q.f === 'north' && Math.abs(Math.abs(q.nx - 0.5) - 0.3) < 0.06) c = mul(VEST, 0.6);    // bainha da abertura
      return c;
    }
    function cloth(q, base, k = 0) {
      const v = 0.62 - 0.4 * q.ny + 0.32 * (n3(q.ax * 0.16, q.ay * 0.28, q.az * 0.16, seed + 5) - 0.5) + (q.f === 'top' ? 0.2 : 0) + k;
      let c = cel(base, v, 0.82, 1.04);
      if (Math.sin(q.ax * 0.9 + q.az * 0.9 + n3(q.ax * 0.2, q.ay * 0.2, q.az * 0.2, seed + 6) * 6) > 0.92) c = mul(base, 0.78);
      return c;
    }
    const sash = q => cel(SASH, 0.6 - 0.4 * q.ny + 0.3 * (n3(q.ax * 0.4, q.ay * 0.4, q.az * 0.4, seed + 7) - 0.5), 0.78, 1.12);
    function boot(q) {
      if (q.f === 'bottom' || q.ny > 0.85) return [70, 44, 26];
      let c = cel([150, 98, 54], 0.6 - 0.3 * q.ny + 0.3 * (n3(q.ax * 0.4, q.ay * 0.4, q.az * 0.4, seed + 8) - 0.5), 0.8, 1.12);
      if (q.ny < 0.22) c = mul(c, 1.1);
      return c;
    }
    function armSkin(q, bare) {
      let c = skin(q);
      if (!bare) return c;
      // gomos dos braços: faixas em volta do lado de fora, o lado de dentro fica verde
      const inner = (q.b.side < 0 && q.f === 'east') || (q.b.side > 0 && q.f === 'west');
      if (!inner && q.f !== 'top' && q.f !== 'bottom' && q.ny > 0.1 && q.ny < 0.9) {
        const g = fr(q.py / 1.15);
        c = g < 0.2 ? mul(SEG, 0.66) : cel(SEG, 0.72 - 0.25 * g, 0.86, 1.08);
      }
      return c;
    }
    function hand(q) {
      let c = skin(q, -0.02);
      if (q.f === 'north' && q.ny > 0.5 && fr(q.px / 1.0) < 0.2) c = mul(SK, 0.7);
      return c;
    }

    const bare = type !== 'aldeao';
    // corpo: altura (baixo/normal/alto) e largura (magro/normal/gordo) sorteadas e misturadas
    let alt = pick(R, ['baixo', 'normal', 'normal', 'alto']), larg = pick(R, ['magro', 'normal', 'gordo']);
    if (force) [alt, larg] = force;
    const LEG = { baixo: 9, normal: 12, alto: 15 }[alt] + (elder ? -1 : 0);
    const TH = { baixo: 6, normal: 7, alto: 9 }[alt] + (elder ? 1 : 0);
    const ARM = { baixo: 5, normal: 6, alto: 7 }[alt];
    const T = { magro: 0.75, normal: 1, gordo: 1.3 }[larg];
    const W = { magro: 7, normal: 9, gordo: 12 }[larg] + (elder ? 1 : 0);
    const D = { magro: 4, normal: 5, gordo: 7 }[larg];
    const HEAD = elder ? 9 : pick(R, [7, 8, 8, 9]);
    const build = { alt, larg };
    function arm(side) {
      const N = side < 0 ? 'D' : 'E';
      const a = 3.5 * T, f = 3.2 * T, hw = 3 * Math.max(0.85, T * 0.95);
      const up = { x: -a / 2, y: -1, z: -a / 2, w: a, h: ARM, d: a, side, p: q => armSkin(q, bare) };
      const fo = { x: -f / 2, y: 0, z: -f / 2, w: f, h: ARM, d: f, side, p: q => armSkin(q, bare) };
      const boxes = [up], fboxes = [fo, { x: -hw / 2, y: ARM - 0.4, z: -hw / 2 - 0.25, w: hw, h: 2.6, d: hw + 0.5, p: hand }];
      if (!bare) {
        boxes.push({ x: -a / 2 - 0.5, y: -1.5, z: -a / 2 - 0.5, w: a + 1, h: ARM + 1, d: a + 1, p: q => cloth(q, ROBE, 0.05) });
        fboxes.push({ x: -f / 2 - 0.8, y: 0, z: -f / 2 - 0.8, w: f + 1.6, h: ARM - 0.6, d: f + 1.6, p: q => cloth(q, ROBE) });
      } else {
        boxes.push({ x: -a / 2 - 0.35, y: -1.6, z: -a / 2 - 0.35, w: a + 0.7, h: 2.4, d: a + 0.7, p: q => vest(q, false) });
      }
      return {
        name: 'braco' + N, pivot: [side * (W / 2 + 1.75 * T), -4 - TH, 0], rot: [0, 0, -side * 0.08], boxes,
        kids: [{ name: 'ante' + N, pivot: [0, ARM - 1, 0], boxes: fboxes }],
      };
    }
    function leg(side) {
      const N = side < 0 ? 'D' : 'E';
      const boxes = type === 'aldeao'
        ? [{ x: -2 * T, y: 0, z: -2 * T, w: 4 * T, h: LEG - 3, d: 4 * T, p: q => cloth(q, ROBE, -0.1) }]
        : [{ x: -3 * T, y: 0, z: -3 * T, w: 6 * T, h: LEG - 3, d: 6 * T, p: q => cloth(q, ROBE) }];
      const bw = 5 * Math.max(0.85, T * 0.92);
      boxes.push({ x: -bw / 2, y: LEG - 3.5, z: -bw / 2 - 0.7, w: bw, h: 3.5, d: bw + 0.7, p: boot });
      return { name: 'perna' + N, pivot: [side * (W / 4 + 0.05), 24 - LEG, 0], boxes };
    }
    const hs = HEAD;
    const headKids = [
      { name: 'orelhaD', pivot: [-hs / 2 + 0.3, -4.5, -0.5], rot: [0, 0.25, 0.45], boxes: [{ x: -6, y: -5, z: -0.5, w: 6, h: 5, d: 1, p: ear }] },
      { name: 'orelhaE', pivot: [hs / 2 - 0.3, -4.5, -0.5], rot: [0, -0.25, -0.45], boxes: [{ x: 0, y: -5, z: -0.5, w: 6, h: 5, d: 1, p: ear }] },
    ];
    for (const [s, len] of [[-1, antR], [1, antL]]) headKids.push({
      name: s < 0 ? 'anteD_a' : 'anteE_a', pivot: [s * 1.8, -8.6, -2.4], rot: [-0.35, 0, s * 0.32], boxes: [{ x: -0.5, y: -len, z: -0.5, w: 1, h: len, d: 1, p: ant }],
      kids: [{ name: s < 0 ? 'anteD_b' : 'anteE_b', pivot: [0, -len, 0], rot: [-antBend, 0, 0], boxes: [
        { x: -0.5, y: -2.4, z: -0.5, w: 1, h: 2.4, d: 1, p: ant },
        { x: -0.6, y: -3.1, z: -0.6, w: 1.2, h: 0.8, d: 1.2, p: q => skin(q, 0.2) },
      ] }],
    });
    const corpoBoxes = [
      { x: -W / 2, y: -3, z: -D / 2, w: W, h: 3, d: D, p: q => cloth(q, ROBE) },
      { x: -W / 2 - 0.25, y: -5, z: -D / 2 - 0.25, w: W + 0.5, h: 2.5, d: D + 0.5, p: sash },
      { x: -W / 2, y: -5 - TH, z: -D / 2, w: W, h: TH, d: D, p: torso },
      { x: -Math.max(4.5, W / 2 - 0.5), y: -7 - TH, z: -D / 2 - 0.7, w: Math.max(9, W - 1), h: 3, d: D + 1.4, p: scarf },
    ];
    if (type === 'aldeao') {
      corpoBoxes.push({ x: -W / 2 - 0.3, y: -5 - TH - 0.2, z: -D / 2 - 0.3, w: W + 0.6, h: TH + 0.2, d: D + 0.6, p: q => cloth(q, ROBE, 0.05) });
    } else {
      corpoBoxes.push({ x: -W / 2 - 0.3, y: -5 - TH - 0.2, z: -D / 2 - 0.3, w: W + 0.6, h: TH + 0.4, d: D + 0.6, p: q => vest(q, true) });
    }
    if (larg === 'gordo') corpoBoxes.push({ x: -W / 2 + 1.5, y: -5 - TH * 0.75, z: -D / 2 - 2, w: W - 3, h: TH * 0.75 + 3, d: 2.5,
      p: q => type === 'aldeao' ? cloth(q, ROBE, 0.05) : torso({ ...q, ny: 0.45 + q.ny * 0.55 }) });
    const corpoKids = [
      {
        name: 'cabeca', pivot: [0, -6 - TH, elder ? -1.2 : -0.3], boxes: [{ x: -hs / 2, y: -9, z: -hs / 2, w: hs, h: 9, d: hs, p: head }],
        kids: headKids,
      },
      arm(-1), arm(1),
    ];
    if (type === 'aldeao') {
      corpoKids.push({ name: 'saia', pivot: [0, -0.5, 0], boxes: [{ x: -W / 2 - 0.5, y: 0, z: -D / 2 - 0.5 - (larg === 'gordo' ? 1.5 : 0), w: W + 1, h: LEG - 2.5, d: D + 1 + (larg === 'gordo' ? 1.5 : 0), p: q => cloth(q, ROBE, -0.02) }] });
      // colete longo: do ombro até o joelho, aberto na frente
      corpoKids.push({ name: 'abaD', pivot: [-W / 2 + 0.2, -5 - TH - 0.4, -D / 2 - 0.6 - (larg === 'gordo' ? 2 : 0)], boxes: [{ x: -0.6, y: 0, z: -0.5, w: 3.2, h: TH + LEG + 2, d: 0.8, p: q => vest(q, false) }] });
      corpoKids.push({ name: 'abaE', pivot: [W / 2 - 0.2, -5 - TH - 0.4, -D / 2 - 0.6 - (larg === 'gordo' ? 2 : 0)], boxes: [{ x: -2.6, y: 0, z: -0.5, w: 3.2, h: TH + LEG + 2, d: 0.8, p: q => vest(q, false) }] });
      corpoKids.push({ name: 'abaT', pivot: [0, -5 - TH - 0.4, D / 2 + 0.6], boxes: [{ x: -W / 2 - 0.4, y: 0, z: -0.3, w: W + 0.8, h: TH + LEG + 3, d: 0.8, p: q => vest(q, false) }] });
    }
    const def = {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        { name: 'corpo', pivot: [0, 24 - LEG, 0], rot: [elder ? 0.12 : 0, 0, 0], boxes: corpoBoxes, kids: corpoKids },
        leg(-1), leg(1),
      ],
    };
    return { def, seed, type, build, idleShift: R() };
  }

  function anim(P, t, mode, a, root, v) {
    const S = Math.sin, br = S(t * 1.5 + v.m.idleShift * 6);
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const elder = v.m.type === 'anciao';
    r('corpo', br * 0.015, 0, 0);
    P.corpo.obj.position.y += br * 0.18;
    // antenas: atrasadas em relação à cabeça, cada uma no seu ritmo
    const hy = S(t * 0.45 + v.m.idleShift * 3) * 0.25;
    r('cabeca', elder ? 0.1 : -0.03, hy, 0);
    r('anteD_a', S(t * 1.7 - 0.6) * 0.07, 0, S(t * 1.3) * 0.05);
    r('anteE_a', S(t * 1.5 - 0.2) * 0.07, 0, -S(t * 1.1 + 1) * 0.05);
    r('anteD_b', S(t * 1.7 - 1.3) * 0.12, 0, 0);
    r('anteE_b', S(t * 1.5 - 0.9) * 0.12, 0, 0);
    if (elder) { r('bracoD', -0.2, 0, 0); r('anteD', -0.6, 0, 0); r('bracoE', -0.2, 0, 0); r('anteE', -0.6, 0, 0); }
    if (mode === 'andar') {
      const w = t * (elder ? 3.5 : 5.2), sw = S(w);
      r('pernaD', sw * 0.55, 0, 0); r('pernaE', -sw * 0.55, 0, 0);
      r('bracoD', -sw * 0.45, 0, 0); r('bracoE', sw * 0.45, 0, 0);
      P.corpo.obj.position.y -= Math.abs(sw) * 0.4;
      r('anteD_b', -Math.abs(sw) * 0.2, 0, 0); r('anteE_b', -Math.abs(sw) * 0.2, 0, 0);
      r('saia', 0, 0, 0); r('abaD', -Math.abs(sw) * 0.15 - 0.05, 0, 0); r('abaE', -Math.abs(sw) * 0.15 - 0.05, 0, 0); r('abaT', Math.abs(sw) * 0.15, 0, 0);
    }
    if (mode === 'atacar') {
      // braço que estica: puxa (0..0.25), dispara esticando (0.25..0.38), segura longe (0.38..0.55), volta encolhendo
      const sm = ENG.smooth;
      const load = a < 0.25 ? sm(0, 0.25, a) : a < 0.38 ? 1 - sm(0.25, 0.38, a) : 0;
      const shot = a < 0.25 ? 0 : a < 0.38 ? sm(0.25, 0.38, a) : a < 0.55 ? 1 : 1 - sm(0.55, 0.9, a);
      r('bracoD', 0.5 * load - 1.6 * shot, 0.2 * load, 0);
      r('anteD', -0.8 * load + 0.0 * shot, 0, 0);
      r('corpo', 0.1 * shot, 0.4 * load - 0.35 * shot, 0);
      r('bracoE', -0.3 * shot, 0, 0.3 * shot);
      r('cabeca', 0, -0.3 * shot, 0);
      const st = 1 + 3.2 * shot;
      P.bracoD.obj.scale.y = 1 + 0.6 * shot;
      P.anteD.obj.scale.y = st;
      r('pernaD', -0.3 * shot, 0, 0); r('pernaE', 0.25 * shot, 0, 0);
    }
  }

  const TIPO = { guerreiro: 'Guerreiro', aldeao: 'Aldeão', anciao: 'Ancião' };
  function variant(type, seed, force) {
    const m = makeNamek(type, seed, force);
    return {
      nome: `${TIPO[type]} ${m.build.alt === 'normal' ? '' : m.build.alt} ${m.build.larg === 'normal' ? '' : m.build.larg}`.replace(/ +/g, ' ').trim(), def: m.def, m,
      info: {
        'Tipo': { guerreiro: 'Guerreiro: o mais forte, briga primeiro.', aldeao: 'Aldeão: foge e chama os guerreiros.', anciao: 'Ancião: devagar, cura os outros namekianos por perto.' }[type],
        'Este': `Semente ${seed}: ${m.build.alt}, ${m.build.larg}`,
      },
    };
  }
  const MK = [['guerreiro', 11, ['alto', 'magro']], ['guerreiro', 27, ['baixo', 'gordo']], ['guerreiro', 64, ['normal', 'normal']], ['aldeao', 5, ['alto', 'gordo']],
    ['aldeao', 42, ['baixo', 'magro']], ['aldeao', 90, ['normal', 'normal']], ['anciao', 8, ['baixo', 'gordo']], ['anciao', 33, ['alto', 'magro']]];
  const variants = MK.map(([type, seed, f]) => variant(type, seed, f));
  const randomVariant = () => { const seed = 1 + Math.floor(Math.random() * 99999); return variant(['guerreiro', 'guerreiro', 'aldeao', 'aldeao', 'anciao'][seed % 5], seed); };

  window.NAMEK = { variant, MK };
  window.MOBS = window.MOBS || [];
  window.MOBS.push({
    id: 'namekiano', nome: 'Namekiano', bioma: 'Namek', variants, randomVariant, anim, attackLen: 1.6, cam: 5.2, lookY: 1.2,
    info: {
      'Tamanho': '2,1 blocos (ancião um pouco maior e curvado)',
      'Vida': 'Guerreiro 80, aldeão 40, ancião 60',
      'Jeito': 'Neutros. Se você bater em um, os namekianos num raio de 16 blocos viram contra você. Odeiam soldados do Freeza e brigam com eles.',
      'Ataque': 'O braço estica até 5 blocos num soco (guerreiro e ancião). Aldeão não ataca.',
      'Cada um diferente': 'Altura (baixo, normal, alto), largura (magro, normal, gordo), tamanho da cabeça, tom de verde, cor de roupa, antenas, cicatriz e bigode saem da semente de cada namekiano e se misturam. Use Sortear outro pra ver novos.',
    },
  });
})();
