// Útero Amaldiçoado (Floresta Negra; cap. 6, o do reformatório). Tier 5, grau 1. Uma bolsa de carne do tamanho de
// um carro pequeno, membrana esticada com veias e uma cria encolhida aparecendo por dentro, andando em tocos de
// carne, com três cordões umbilicais balançando. Ainda não nasceu: quando nascer vira grau especial.
// Golpes: Parir (contrai e põe pra fora uma cria que vira Mosca Amaldiçoada) e Cordão (o cordão gruda no seu
// peito e suga sua vida pra ela, que se cura).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  const TONES = [[156, 66, 96], [128, 64, 116], [168, 78, 84]];
  function variant(seed) {
    const R = FX.rnd(seed);
    return { seed, tone: TONES[Math.floor(R() * TONES.length)], fetusX: (R() - 0.5) * 0.15, size: 0.95 + R() * 0.1 };
  }
  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.8, hi = 1.12) => {
      const f = 0.62 - 0.4 * q.ny + 0.35 * (n3(q.ax * 0.25, q.ay * 0.25, q.az * 0.25, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const vein = q => {
      const w = n3(q.ax * 0.18, q.ay * 0.18, q.az * 0.18, 3 + S) * 3.2 + n3(q.ax * 0.5, q.ay * 0.5, q.az * 0.5, 4 + S) * 0.6;
      return Math.abs(fr(w) - 0.5) < 0.05;
    };
    const P = {};
    // membrana: rosa esticado, mais claro onde é fino, veias roxas, e a cria vista por dentro (sombra escura)
    P.sac = q => {
      let c = shade(v.tone, q, 1, 0.8, 1.14);
      if (vein(q)) c = mix(c, [90, 40, 90], 0.6);
      if (q.f === 'north' || q.f === 'west' || q.f === 'east') {
        // a cria encolhida: cabeça grande, corpo curvado, lido em coordenadas do modelo (fica no lugar mesmo girando)
        const x = q.ax / 8 - v.fetusX, y = (q.ay - 6) / 8;
        const head = Math.hypot(x - 0.15, y + 0.2) < 0.32, body = Math.hypot((x + 0.12) / 1.2, (y - 0.25) / 0.75) < 0.38 && Math.hypot((x + 0.25) / 1, (y - 0.15)) > 0.18;
        if (head || body) {
          c = mix(c, [54, 14, 30], 0.7);
          if (Math.hypot(x - 0.25, y + 0.25) < 0.08) c = [24, 10, 16];                                         // o olho fechado
        } else if (Math.hypot(x, y) < 0.8) c = mix(c, [236, 160, 176], 0.35);                                  // onde a membrana é fina
      }
      if (q.f === 'bottom') c = mul(c, 0.6);
      return c;
    };
    P.stump = q => { let c = shade(mul(v.tone, 0.85), q, 5); if (fr(q.ay * 0.5) < 0.15) c = mul(c, 0.8); if (q.f === 'bottom') c = [70, 30, 36]; return c; };
    P.cord = q => { let c = shade([200, 150, 160], q, 6); if (fr(q.ay * 0.35 + q.ax * 0.2) < 0.25) c = mul(c, 0.8); return c; };
    P.lips = q => { if (q.f === 'north' && Math.abs(q.nx - 0.5) < 0.18) return [50, 10, 20]; return shade([150, 60, 80], q, 7); };
    return P;
  }

  function rig(P, v, lo) {
    const stumps = [];
    const n = lo ? 4 : 6;
    for (let i = 0; i < n; i++) {
      const a = (i / n) * Math.PI * 2 + 0.3, x = Math.cos(a) * 6.2, z = Math.sin(a) * 6.2;
      stumps.push({ name: 'toco' + i, pivot: [x, 16, z], rot: [Math.sin(a) * 0.3, 0, -Math.cos(a) * 0.3], boxes: [{ x: -1.9, y: 0, z: -1.9, w: 3.8, h: 7.6, d: 3.8, p: P.stump }] });
    }
    const cord = (i, x, z, ry) => {
      const segs = lo ? 2 : 4;
      let k = null;
      for (let j = segs - 1; j >= 0; j--) k = { name: `cordao${i}_${j}`, pivot: [0, j === 0 ? 0 : -4.2, 0], rot: [j === 0 ? 0 : 0.55, 0, 0], boxes: [{ x: -0.8 + j * 0.1, y: -4.4, z: -0.8 + j * 0.1, w: 1.6 - j * 0.2, h: 4.4, d: 1.6 - j * 0.2, p: P.cord }], kids: k ? [k] : [] };
      k.pivot = [x, -12, z]; k.rot = [-0.3, ry, 0.2]; return k;
    };
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'bolsa', pivot: [0, 8, 0], boxes: [
          { x: -8, y: -9, z: -7.5, w: 16, h: 16, d: 15, p: P.sac },
          { x: -6.6, y: -12, z: -6.2, w: 13.2, h: 3.2, d: 12.4, p: P.sac },
          { x: -4.4, y: -14, z: -4.2, w: 8.8, h: 2.2, d: 8.4, p: P.sac },
          { x: -9.2, y: -5, z: -6.4, w: 18.4, h: 9, d: 12.8, p: P.sac },
          { x: -6.6, y: -5.5, z: -8.6, w: 13.2, h: 10, d: 17.2, p: P.sac },
          ...(lo ? [] : [{ x: -6.6, y: 6.8, z: -6.2, w: 13.2, h: 2.4, d: 12.4, p: P.sac }]),
        ],
        kids: [
          { name: 'boca', pivot: [0, 4.4, -8.7], boxes: [{ x: -2.4, y: -3.6, z: -0.6, w: 4.8, h: 5, d: 1, p: P.lips }] },
          cord(0, -3, 0, -0.6), cord(1, 3.5, 2, 2.2), ...(lo ? [] : [cord(2, 0.5, -3, 1.0)]),
        ],
      }, ...stumps],
    };
  }

  // cria recém-nascida: um bolinho de carne com dois olhinhos, cai, se debate e abre asas
  function cria() {
    const g = new THREE.Group(), flesh = new THREE.MeshLambertMaterial({ color: 0xb8a4a8 }), dark = new THREE.MeshLambertMaterial({ color: 0x1a1014 });
    const lip = new THREE.MeshLambertMaterial({ color: 0x8a3a50 }), wing = new THREE.MeshLambertMaterial({ color: 0xd8dce4, transparent: true, opacity: 0.7, side: THREE.DoubleSide });
    const b = new THREE.Mesh(new THREE.BoxGeometry(0.34, 0.32, 0.34), flesh); g.add(b);
    for (const x of [-0.08, 0.08]) { const e = new THREE.Mesh(new THREE.BoxGeometry(0.06, 0.06, 0.02), dark); e.position.set(x, 0.05, -0.175); g.add(e); }
    const m = new THREE.Mesh(new THREE.BoxGeometry(0.14, 0.05, 0.03), lip); m.position.set(0, -0.07, -0.18); g.add(m);
    const w1 = new THREE.Mesh(new THREE.PlaneGeometry(0.4, 0.2), wing), w2 = w1.clone(); w1.position.set(-0.22, 0.17, 0.05); w2.position.set(0.22, 0.17, 0.05);
    w1.rotation.x = w2.rotation.x = -Math.PI / 2; g.add(w1, w2); g.wings = [w1, w2];
    g.slime = new THREE.Mesh(new THREE.BoxGeometry(0.36, 0.05, 0.36), new THREE.MeshLambertMaterial({ color: 0x9a2a40 })); g.slime.position.y = -0.17; g.add(g.slime);
    return g;
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size * 1.25; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    // a bolsa pulsa como coração lento; a cria dentro dá chutes (calombos de repente)
    const beat = Math.pow(Math.max(0, Math.sin(t * 2.4)), 6);
    const kick = seq([[0, 0], [0.04, 1, 'snap'], [0.2, 0]], fr(t / 3.1));
    const sac = (k = 0) => P.bolsa.obj.scale.set(1 + 0.04 * beat + 0.06 * kick + k, 1 - 0.03 * beat + k * 0.5, 1 + 0.04 * beat - 0.04 * kick + k);
    const cords = (k = 1) => { for (let i = 0; i < 3; i++) for (let j = 0; j < 4; j++) r(`cordao${i}_${j}`, 0.25 * k * Math.sin(t * 1.3 + i * 2 + j * 0.8), 0, 0.2 * k * Math.sin(t * 1.1 + i + j * 0.7)); };

    if (mode === 'parado') {
      sac(); cords();
      r('bolsa', 0.04 * kick, 0, 0.05 * kick);
      for (let i = 0; i < 6; i++) r('toco' + i, 0.05 * Math.sin(t * 0.9 + i), 0, 0);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 0.8;
      sac(); cords(1.6);
      for (let i = 0; i < 6; i++) { const p = ph + i / 6; r('toco' + i, 0.4 * Math.sin(p * Math.PI * 2), 0, 0); }
      P.bolsa.obj.position.y -= 0.9 * Math.abs(Math.sin(ph * Math.PI * 3));
      r('bolsa', 0.05 * Math.sin(ph * Math.PI * 2), 0, 0.06 * Math.sin(ph * Math.PI * 3));
      return;
    }
    if (mode === 'parir') {
      const T = t % 5.0;
      // três contrações cada vez mais fortes; a boca dilata, a cria escorrega pra fora num jato de sangue
      const c = Math.max(0, Math.sin(Math.PI * 2 * FX.clamp01(T / 2.1) * 1.5)) * (0.4 + 0.6 * FX.clamp01(T / 2.1));
      const push = seq([[1.9, 0], [2.2, 1, 'snap'], [2.6, 0.3], [3.2, 0]], T);
      sac(-0.06 * c - 0.1 * push); cords(1 + 2 * c);
      r('bolsa', 0.15 * push, 0, 0);
      for (let i = 0; i < 6; i++) r('toco' + i, 0.1 * c * Math.sin(T * 20 + i), 0, 0);
      if (P.boca) P.boca.obj.scale.set(1 + 0.6 * Math.max(c * 0.5, push), 1 + 1.0 * Math.max(c * 0.5, push), 1);
      const born = T - 2.15;
      if (born > 0) {
        const g = ctx.get('cria', cria);
        g.visible = true;
        const f = FX.clamp01(born / 0.45);
        const fly = seq([[1.4, 0], [2.4, 1, 'io']], born);
        g.position.set(0.1, 0.9 * (1 - f) + 0.17 + 1.3 * fly, -1.4 - 1.4 * FX.EASE.out(f) - 1.2 * fly);
        g.rotation.set(0.3 * Math.sin(born * 14) * (1 - fly), 0.4 * Math.sin(born * 3), 0);
        g.slime.visible = fly < 0.2;
        g.wings.forEach((w, i) => { w.visible = born > 0.9; w.rotation.y = (i ? 1 : -1) * 0.6 * Math.sin(born * 40) * FX.clamp01(born - 0.9); });
        g.scale.setScalar(0.8 + 0.6 * FX.clamp01((born - 0.9) / 1.2));
        ctx.get('sangue', () => DBFX.grains({
          n: 60, seed: 5, palette: [[150, 16, 24], [110, 8, 16], [190, 40, 60]], size: [0.03, 0.08], drag: 1.2, grav: 9.8, life: 1.0,
          cone: Rr => { const a = (Rr() - 0.5) * 1.6; return [Math.sin(a) * 1.4, 0.5 + Rr() * 1.4, -2 - Rr() * 2]; },
        })).at(new THREE.Vector3(0, 0.9, -1.4), born, 0.02);
        ctx.get('poca', () => DBFX.puddle([0.6, 0.08, 0.14], [0.25, 0.02, 0.05])).at(new THREE.Vector3(0, 0.02, -2.4), 0.9, FX.clamp01(born / 0.4), 1 - FX.clamp01((born - 2.2) / 0.6), T, 2.5);
      }
      return;
    }
    if (mode === 'cordao') {
      const T = t % 5.0;
      const R = ctx.ref; const RZ = -4.4; R.place(0, RZ, 0);
      sac(); cords(1);
      // o cordão da frente estica num chicote e gruda no peito; pulsos de luz descem do alvo pra bolsa
      obj.updateMatrixWorld(true);
      const root = new THREE.Vector3(); P.bolsa.obj.localToWorld(root.set(-3, -9, -6));
      const chest = new THREE.Vector3(0, 1.25, RZ + 0.15);
      const shoot = seq([[0.3, 0], [0.62, 1, 'in']], T), back = seq([[4.0, 0], [4.4, 1, 'in']], T);
      const tb = ctx.get('cordaoLongo', () => JJFX.tube(0xc89aa4, 0.09, 0.06));
      const drain = T > 0.7 && T < 4.0;
      tb.at(root, chest, Math.max(0.001, shoot * (1 - back)), { arc: 0.5, wiggle: drain ? 0.04 : 0.2, t: T, pulse: drain ? 1 - fr((T - 0.7) * 1.4) : -1 });
      if (drain) {
        const k = FX.clamp01((T - 0.7) / 0.3);
        R.lean(0.25 * k + 0.05 * Math.sin(T * 9)); R.face(0.5 * k);
        ctx.cursed(0.5 * k);
        // ela se cura: incha e brilha a cada pulso que chega
        const arrive = Math.pow(Math.max(0, Math.sin((T - 0.7) * 1.4 * Math.PI * 2 - 1)), 4);
        sac(0.06 * arrive);
        ctx.get('luz', () => DBFX.light(0xff6080)).at(root.clone().add(new THREE.Vector3(0, 0.5, 0)), 2.5 * arrive, 5);
        ctx.get('vida', () => DBFX.grains({
          n: 40, seed: 8, palette: [[255, 120, 140], [255, 180, 190], [220, 60, 90]], size: [0.02, 0.05], drag: 2, grav: -0.5, life: 0.6, spread: 2.5, add: true,
          cone: Rr => { const a = Rr() * 6.28; return [Math.cos(a) * 0.6, 0.4 + Rr() * 0.6, Math.sin(a) * 0.6]; },
        })).at(chest, T - 0.7, 0);
      }
      return;
    }
    if (mode === 'morte') {
      const { fall } = JJFX.exorcize(t, obj, e, ctx, { h: 2.6, refZ: -3.0, rotX: 0, back: 0.2, ashY: 0.6, ashZ: 0.2, n: 260 });
      P.bolsa.obj.scale.set(1 + 0.2 * fall, 1 - 0.4 * fall, 1 + 0.2 * fall); P.bolsa.obj.position.y += 4 * fall;
      cords(1 - fall);
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(8), variant(33), variant(61)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'utero', nome: 'Útero Amaldiçoado', bioma: 'Floresta Negra', tier: 'Grau 1 · tier 5',
    variants: fixed.map((v, i) => ({ nome: ['Rosa', 'Roxo', 'Carne'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['parir', 'Parir'], ['cordao', 'Cordão'], ['morte', 'Exorcizado']],
    cam: 7, lookY: 1.4, lookZ: { parir: -1.4, cordao: -2.0, morte: -0.5 }, camFar: { parir: 7, cordao: 8.5 }, camYaw: { parir: -1.0, cordao: -1.25 },
    info: {
      'Tamanho': '2,3 blocos de altura, 2,5 de largura',
      'Vida': '260 (grau 1)',
      'Jeito': 'Lento e covarde: anda pra longe e deixa as crias lutarem. 1 por Floresta Negra, perto de água parada. Se ficar 5 minutos sem ser morto, ele NASCE (vira um chefe grau especial; fica pra outra prévia).',
      'Parir': 'Três contrações e a boca de baixo põe uma cria pra fora num jato de sangue; em 2 s ela abre as asas e vira uma Mosca Amaldiçoada. Até 4 crias vivas por vez.',
      'Cordão': 'Um cordão umbilical estica até você e gruda no peito: suga vida a cada pulso (você vê os caroços descendo) e ele se cura o mesmo tanto. Cortar o cordão (um golpe nele) para.',
      'Solta': 'Muito resíduo; raro: Útero Vazio (item: guarda 1 mob pequeno e solta depois).',
      'Variações': 'Tom da membrana e posição da cria dentro.',
    },
  });
})();
