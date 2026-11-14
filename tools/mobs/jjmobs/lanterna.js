// Lanterna Amaldiçoada (Kyoto). Tier 9. Maldição do medo das ruas de festival à noite: uma lanterna de papel
// (chōchin) velha que rasgou na horizontal e virou boca, com um olho só no papel e uma língua comprida. A chama de
// dentro ainda acende: o papel brilha quando ela respira. Flutua presa a nada, balançando como se estivesse
// pendurada num varal; no chão anda aos pulinhos.
// Golpes: Língua (estica a língua, enrola no seu pescoço e te puxa) e Fogo-Fátuo (cospe três chamas azuis que
// rodam em volta dela e depois vão em você).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  // [nome, papel, costela/tampa, tinta, chama]
  const KINDS = [
    ['Vermelha de Festival', [214, 58, 46], [34, 24, 22], [250, 236, 210], [255, 190, 90]],
    ['Branca de Funeral', [232, 226, 210], [40, 36, 34], [30, 24, 24], [170, 210, 255]],
    ['Amarela Rasgada', [226, 186, 90], [70, 44, 30], [120, 30, 24], [255, 150, 60]],
  ];
  function variant(seed, kind) {
    const R = FX.rnd(seed);
    const k = kind ?? Math.floor(R() * KINDS.length);
    // "letra" pintada do lado: traços de pincel sorteados em grade 7x9
    const glyph = []; for (let i = 0; i < 6; i++) { const vert = R() < 0.5; glyph.push({ x: 1 + Math.floor(R() * 5), y: 1 + Math.floor(R() * 7), l: 2 + Math.floor(R() * 3), vert }); }
    const holes = []; for (let i = 0; i < 4; i++) holes.push({ a: R() * Math.PI * 2, y: -2 - R() * 10, r: 0.6 + R() * 0.9 });
    return { seed, kind: k, paper: KINDS[k][1], rib: KINDS[k][2], ink: KINDS[k][3], flame: KINDS[k][4], glyph, holes, size: 0.92 + R() * 0.16, torn: k === 2 || R() < 0.3 };
  }

  const EYE = [
    '.KKKK.',
    'KWiiWK',
    'KiPPiK',
    'KiPhiK',
    'KWiiWK',
    '.KKKK.',
  ];
  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.74, hi = 1.16) => {
      const f = 0.62 - 0.28 * q.ny + 0.24 * (n3(q.ax * 0.4, q.ay * 0.4, q.az * 0.4, s + S) - 0.5) + (q.f === 'top' ? 0.2 : 0) - (q.f === 'bottom' ? 0.25 : 0);
      return cel(base, f, lo, hi);
    };
    const P = {};
    // papel: fibras, costelas de bambu horizontais, manchas de queimado e furos (por onde a luz vaza)
    const paper = (q, s) => {
      let c = shade(v.paper, q, s);
      if (n3(q.ax * 1.4, q.ay * 0.3, q.az * 1.4, s + 1 + S) > 0.7) c = mul(c, 0.9);
      if (fr(q.ay * 0.34 + 0.5) < 0.1 && q.f !== 'top' && q.f !== 'bottom') c = mix(c, v.rib, 0.55);
      if (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + 2 + S) > 0.72) c = mix(c, [60, 40, 30], 0.45);   // fuligem
      const ang = Math.atan2(q.az, q.ax);
      for (const h of v.holes) { const da = Math.abs(((ang - h.a + Math.PI * 3) % (Math.PI * 2)) - Math.PI); if (Math.hypot(da * 5, q.ay - h.y) < h.r) return mix(v.flame, [255, 250, 230], 0.4); }
      return c;
    };
    P.paper = q => paper(q, 1);
    // frente: olho grande em bitmap no meio do papel
    P.front = q => {
      if (q.f !== 'north') return P.side(q);
      const col = Math.floor((q.px - q.b.w / 2 + 3.3) / 1.1), row = Math.floor(q.py * 6 / q.b.h);
      const ch = row >= 0 && row < 6 && col >= 0 && col < 6 ? EYE[row][col] : '.';
      if (ch === 'h') return [255, 255, 255];
      if (ch === 'K') return mul(v.rib, 1.2);
      if (ch === 'W') return [248, 242, 226];
      if (ch === 'i') return v.kind === 1 ? [90, 120, 170] : [200, 140, 30];
      if (ch === 'P') return [14, 8, 8];
      return paper(q, 3);
    };
    // lado: a "letra" pintada a pincel
    P.side = q => {
      let c = paper(q, 4);
      if (q.f === 'east' || q.f === 'west') {
        const gx = Math.floor(q.pz * 7 / q.b.d), gy = Math.floor(q.py * 9 / q.b.h);
        for (const g of v.glyph) { if (g.vert ? (gx === g.x && gy >= g.y && gy < g.y + g.l) : (gy === g.y && gx >= g.x && gx < g.x + g.l)) return v.ink; }
      }
      return c;
    };
    P.cap = q => { let c = shade(v.rib, q, 5); if (fr(q.ax * 0.8) < 0.1) c = mul(c, 1.5); return c; };
    P.mouthIn = q => mix([40, 8, 10], v.flame, 0.25 * (1 - q.ny));
    P.tongue = q => { let c = shade([200, 70, 90], q, 6, 0.8, 1.15); if (q.f === 'top' && Math.abs(q.nx - 0.5) < 0.1) c = mul(c, 0.8); return c; };
    P.tooth = q => shade([236, 228, 200], q, 7);
    return P;
  }

  function rig(P, v, lo) {
    const ring = (w, y, h, p) => ({ x: -w / 2, y, z: -w / 2, w, h, d: w, p });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'corpo', pivot: [0, 15, 0], boxes: [
          // parte de cima da lanterna (acima da boca)
          ring(6.4, -9.6, 1.6, P.cap),
          ring(8.6, -8.0, 2.0, P.paper),
          ring(10.4, -6.0, 6.0, P.front),
          ring(11.0, -0.4, 1.2, P.paper),
          // alça de pendurar
          { x: -0.5, y: -12.4, z: -0.5, w: 1, h: 2.8, d: 1, p: P.cap },
          ...(lo ? [] : [{ x: -2.2, y: -13.2, z: -0.5, w: 4.4, h: 0.9, d: 1, p: P.cap }, { x: -2.2, y: -13.0, z: -0.5, w: 0.9, h: 2.6, d: 1, p: P.cap }, { x: 1.3, y: -13.0, z: -0.5, w: 0.9, h: 2.6, d: 1, p: P.cap }]),
          // dentro da boca (escuro com o brilho da chama)
          { x: -4.8, y: 0.6, z: -4.8, w: 9.6, h: 1, d: 9.6, p: P.mouthIn },
          ...(v.torn && !lo ? [{ x: 3.6, y: -4, z: -5.6, w: 1.6, h: 2.6, d: 0.3, p: P.paper }] : []),
        ],
        kids: [{
          // queixo: metade de baixo da lanterna, abre pela rachadura
          name: 'queixo', pivot: [0, 0.8, 4.6], boxes: [
            { x: -5.5, y: 0, z: -10.1, w: 11, h: 1.2, d: 11, p: P.paper },
            { x: -5.2, y: 1.2, z: -9.8, w: 10.4, h: 4.0, d: 10.4, p: P.side },
            { x: -4.3, y: 5.2, z: -8.9, w: 8.6, h: 1.8, d: 8.6, p: P.paper },
            { x: -3.2, y: 7.0, z: -7.8, w: 6.4, h: 1.4, d: 6.4, p: P.cap },
            ...(lo ? [] : [-3, -1, 1, 3].map((x, i) => ({ x: x - 0.45, y: -0.9 - (i % 2) * 0.3, z: -10.0, w: 0.9, h: 1.2 + (i % 2) * 0.3, d: 0.6, p: P.tooth }))),
          ],
          kids: [{
            name: 'lingua', pivot: [0, 0.2, -6.4], rot: [0.2, 0, 0], boxes: [{ x: -1.6, y: -0.6, z: -4.4, w: 3.2, h: 1, d: 4.6, p: P.tongue }],
            kids: [{ name: 'lingua2', pivot: [0, 0, -4.2], rot: [0.5, 0, 0], boxes: [{ x: -1.3, y: -0.5, z: -3.6, w: 2.6, h: 0.9, d: 3.8, p: P.tongue }] }],
          }],
        }],
      }],
    };
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const C = e.v.flame.map(c => c / 255);
    const W = (n, x, y, z) => { obj.updateMatrixWorld(true); return new THREE.Vector3(x, y, z).applyMatrix4(P[n].obj.matrixWorld); };
    const flame = (k = 1) => {
      const fl = 0.8 + 0.2 * n3(t * 9, 0, 0, 3);
      const p = W('corpo', 0, -3, 0);
      ctx.get('chama', () => DBFX.glow(C)).at(p, 0.22, 0.55 * k * fl, t, 1.2);
      ctx.get('luz', () => DBFX.light(new THREE.Color(...C))).at(p, 1.6 * k * fl, 4);
    };
    const sway = (k = 1) => { P.corpo.obj.rotation.z += 0.12 * k * Math.sin(t * 1.3); P.corpo.obj.rotation.x += 0.06 * k * Math.sin(t * 0.9 + 1); };
    // língua comprida (tubo) de a até b, com ponta grossa
    const tongueTube = (a2, b2, k, o) => ctx.get('linguaLonga', () => JJFX.tube(0xc84860, 0.07, 0.05)).at(a2, b2, k, o);

    if (mode === 'parado') {
      // pendurada no nada: balança como se tivesse vento, a chama respira (o papel acende junto), o olho
      // olha em volta; de vez em quando a boca abre e a língua lambe o papel
      obj.position.y += 0.7 + 0.08 * Math.sin(t * 1.6);
      sway(1);
      const lick = seq([[0, 0], [0.08, 1, 'out'], [0.4, 1], [0.5, 0]], fr(t / 3.6) * 1.6);
      r('queixo', 0.35 * lick, 0, 0); r('lingua', -0.4 * lick, 0.6 * Math.sin(t * 6) * lick, 0); r('lingua2', -0.6 * lick, 0, 0);
      flame(0.85 + 0.25 * Math.sin(t * 2.2));
      return;
    }
    if (mode === 'andar') {
      // pulinhos no chão: amassa ao cair, estica ao subir, a boca bate
      const ph = fr(t * 1.4);
      const up = Math.sin(Math.PI * ph);
      obj.position.y += 0.55 * up;
      const sq = ph < 0.1 ? 1 - ph / 0.1 : ph > 0.9 ? (ph - 0.9) / 0.1 : 0;
      P.corpo.obj.scale.set(1 + 0.18 * sq, 1 - 0.22 * sq + 0.08 * up, 1 + 0.18 * sq);
      obj.position.z += 0.35 * Math.sin(t * 0.7);
      r('queixo', 0.25 * sq, 0, 0);
      P.corpo.obj.rotation.x += -0.12 * up;
      flame(1);
      return;
    }
    if (mode === 'lingua') {
      const T = t % 4.4;
      const R = ctx.ref; const RZ = -2.8; R.place(0, RZ, 0); R.spin(Math.PI);
      // recua, escancara a boca, a língua estica (tubo) e enrola no pescoço; puxa você até ela e solta
      obj.position.y += 0.75 + 0.06 * Math.sin(t * 2);
      const back = seq([[0.2, 0], [0.8, 1, 'io'], [1.0, 1], [1.2, -0.3, 'snap'], [3.2, -0.3], [3.8, 0, 'io']], T);
      obj.position.z += 0.4 * back;
      P.corpo.obj.rotation.x += -0.25 * back;
      const open = seq([[0.6, 0], [1.0, 1, 'snap'], [3.4, 1], [3.8, 0]], T);
      r('queixo', 0.9 * open, 0, 0);
      r('lingua', -0.2 * open, 0, 0);
      const out = seq([[1.0, 0], [1.2, 1, 'out'], [3.0, 1], [3.4, 0, 'in']], T);
      const pull = seq([[1.6, 0], [2.6, 1, 'io'], [3.0, 1]], T);
      const neckZ = RZ + 1.2 * pull;
      R.place(0, neckZ, 0.08 * pull); R.lean(-0.35 * pull + 0.1 * Math.sin(T * 14) * (T > 1.2 && T < 3) * 1); R.armsOut(0.35 * out);
      const mouth = W('lingua', 0, 0, -3.8);
      const neck = new THREE.Vector3(0, 1.5, neckZ + 0.02);
      if (out > 0) {
        tongueTube(mouth, neck, out, { arc: 0.15, wiggle: 0.05 * (1 - pull), t: T });
        // laço em volta do pescoço: segunda volta curta
        if (out > 0.95) ctx.get('laco', () => JJFX.tube(0xc84860, 0.055, 0.055)).at(neck.clone().add(new THREE.Vector3(-0.17, 0, 0.05)), neck.clone().add(new THREE.Vector3(0.17, 0.02, 0.05)), 1, { arc: -0.12, t: 0 });
      }
      if (T > 1.18 && T < 1.35) ctx.flash(neck, 0.5);
      flame(1 + 0.4 * open);
      return;
    }
    if (mode === 'onibi') {
      const T = t % 5.2;
      const R = ctx.ref; const RZ = -3.2; R.place(0, RZ, 0); R.spin(Math.PI);
      // enche (o papel fica claro), cospe três chamas azuis uma atrás da outra; elas rodam em volta da lanterna
      // e depois vão em você uma de cada vez
      obj.position.y += 0.8 + 0.06 * Math.sin(t * 2);
      sway(0.4);
      const fill = seq([[0.2, 0], [0.9, 1, 'in'], [1.6, 1], [1.8, 0]], T);
      P.corpo.obj.scale.set(1 + 0.1 * fill, 1 + 0.06 * fill, 1 + 0.1 * fill);
      const spit = k => seq([[0, 0], [0.06, 1, 'snap'], [0.25, 0]], k);
      let sp = 0; for (let i = 0; i < 3; i++) sp = Math.max(sp, spit(T - 1.0 - i * 0.25));
      r('queixo', 0.15 + 0.6 * sp, 0, 0);
      flame(1 + 1.2 * fill);
      const BL = [0.2, 0.45, 1.0];
      const center = W('corpo', 0, -3, 0);
      for (let i = 0; i < 3; i++) {
        const t0 = 1.0 + i * 0.25, tGo = 2.6 + i * 0.35;
        if (T < t0) continue;
        let p;
        const a2 = (T - t0) * 3.2 + i * 2.094;
        const orbit = center.clone().add(new THREE.Vector3(Math.cos(a2) * 0.85, 0.1 * Math.sin(a2 * 2), Math.sin(a2) * 0.85));
        const born = FX.clamp01((T - t0) / 0.3);
        const mouth = W('queixo', 0, 0, -10);
        if (T < tGo) p = mouth.clone().lerp(orbit, FX.EASE.out(born));
        else {
          const k = FX.clamp01((T - tGo) / 0.35);
          const tgt = new THREE.Vector3(0.2 * (i - 1), 1.2 + 0.15 * i, RZ + 0.15);
          p = orbit.clone().lerp(tgt, FX.EASE.in(k));
          if (k >= 1) {
            const ag = T - tGo - 0.35;
            if (ag < 0.5) ctx.get('estoura' + i, FX.volume).set(tgt, 0.28, Math.min(0.999, ag / 0.5 + 0.05), { fire: 1.2, seed: 50 + i, dens: 8, rise: 0.8, squash: 1.3, smoke: [0.5, 0.6, 0.75] });
            if (ag < 0.15) ctx.flash(tgt, 0.8);
            continue;
          }
        }
        // a chama: miolo branco, volta azul, e um rabo de faíscas
        ctx.get('onibi' + i, () => DBFX.glow(BL)).at(p, 0.16, 0.6, t + i, 1.5);
        ctx.get('miolo' + i, () => DBFX.glow([0.6, 0.85, 1])).at(p.clone().add(new THREE.Vector3(0, -0.04, 0)), 0.07, 0.8, t, 1.2);
        ctx.get('fogoA' + i, FX.volume).set(p.clone().add(new THREE.Vector3(0, 0.07, 0)), 0.19, FX.clamp01(0.1 + 0.2 * fr(t * 3 + i * 0.3)), { fire: 0.4, seed: 80 + i + Math.floor(t * 3 + i * 0.3), dens: 6, rise: 1.2, squash: 1.6, smoke: [0.35, 0.55, 1.0] });
        ctx.get('rabo' + i, () => DBFX.grains({ n: 20, seed: 60 + i, add: true, palette: [[120, 180, 255], [200, 230, 255], [80, 120, 255]], size: [0.015, 0.03], drag: 2, grav: -1, life: 0.5, spread: 0.5, cone: R2 => [(R2() - 0.5) * 0.3, R2() * 0.4, (R2() - 0.5) * 0.3] })).at(p, fr(T * 2 + i * 0.3) * 0.5, -9);
      }
      const hit = Math.max(...[0, 1, 2].map(i => seq([[2.95 + i * 0.35, 0], [3.0 + i * 0.35, 1, 'snap'], [3.4 + i * 0.35, 0]], T)));
      R.punch(hit * 0.7); R.lean(-0.18 * hit);
      if (T > 3.0 && T < 4.6) ctx.ember(new THREE.Vector3(0.05, 1.25, RZ + 0.2), t);
      return;
    }
    if (mode === 'morte') {
      // leva o golpe, a chama de dentro vira fogo de verdade e o papel queima de baixo pra cima; cai a armação
      const T = t % 3.8;
      const R = ctx.ref; R.place(0, -1.9, 0); R.spin(Math.PI);
      R.punch(seq([[0.15, 0], [0.25, 1, 'snap'], [0.6, 1], [0.9, 0]], T));
      const fall = seq([[0.25, 0], [1.4, 1, 'in']], T);
      obj.position.y += 0.7 * (1 - fall) + 0.02;
      P.corpo.obj.rotation.x += 0.6 * Math.sin(T * 7) * (1 - fall) * Math.min(1, T * 3);
      P.corpo.obj.rotation.z += 1.4 * fall;
      r('queixo', 0.8 * fall, 0, 0);
      const burn = FX.clamp01((T - 0.4) / 1.6);
      const c = W('corpo', 0, -3, 0);
      if (T > 0.35 && T < 3.2) ctx.get('incendio', FX.volume).set(c.clone().add(new THREE.Vector3(0, 0.15, 0)), 0.5, Math.min(0.999, (T - 0.35) / 2.85), { fire: 1.5, seed: 70, dens: 9, rise: 1.0, squash: 1.4, smoke: [0.2, 0.18, 0.18] });
      flame(1 - burn);
      JJFX.dissolve(e, burn, 0, 1.6);
      if (T > 0.9) ctx.get('cinza', () => JJFX.ash(23, 90)).at(new THREE.Vector3(0, 0.4, 0), T - 0.9, 0.02);
      if (T > 0.22 && T < 0.4) ctx.flash(new THREE.Vector3(0, 1, -0.8), 1 - (T - 0.22) / 0.18);
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(2, 0), variant(19, 1), variant(44, 2)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'lanterna', nome: 'Lanterna Amaldiçoada', bioma: 'Kyoto', tier: 'Grau 4 · tier 9',
    variants: fixed.map(v => ({ nome: KINDS[v.kind][0], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteada (' + KINDS[v.kind][0] + ')', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Pular'], ['lingua', 'Língua'], ['onibi', 'Fogo-Fátuo'], ['morte', 'Exorcizada']],
    cam: 3.6, lookY: 1.0, lookZ: { lingua: -1.2, onibi: -1.3, morte: -0.6 }, camFar: { lingua: 5.4, onibi: 6.0, morte: 4.8 }, camYaw: { lingua: -1.2, onibi: -1.0, morte: -1.1 },
    info: {
      'Tamanho': '1 bloco de altura, flutua na altura do peito',
      'Vida': '14 (papel: fogo e corte fazem dobrado)',
      'Jeito': 'Fica nas ruas de Kyoto à noite misturada com as lanternas de verdade dos festivais (só a que balança sem vento é maldição). Em grupos de 2 a 4 ao longo das vielas.',
      'Língua': 'Abre a boca rasgada e estica a língua até 3 blocos: enrola no seu pescoço e te puxa até ela (preso 1,5 s). Cortar a língua solta na hora.',
      'Fogo-Fátuo': 'Cospe três chamas azuis que rodam em volta dela por 1,5 s e depois vêm em você uma por vez (cada uma queima um pouco). Dá pra rebater com a mão vazia na hora certa.',
      'Fraqueza': 'A chama de dentro: água ou golpe na boca aberta apaga e ela cai.',
      'Solta': 'Papel Amaldiçoado, Óleo de Lanterna; raro: Lanterna do Festival (bloco que espanta maldições fracas).',
      'Variações': 'Vermelha de festival, branca de funeral (chama azul) ou amarela rasgada; letra pintada e furos diferentes em cada uma.',
    },
  });
})();
