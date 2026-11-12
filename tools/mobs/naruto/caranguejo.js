// Caranguejo Gigante (Mar da Névoa). Fraco, tier 10. Neutro, fica na praia; só briga se você mexer com ele.
// Golpes: Pinçada (a pinça grande prende sua perna 1 s) e Bolhas (espuma na sua cara: a vista embaça 1,5 s).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  function painters(v) {
    const shell = s => q => {
      let c = cel(v.shell, n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + v.seed), 0.8, 1.14);
      if (n3(q.ax * 0.7, q.ay * 0.7, q.az * 0.7, s + 5) > 0.7) c = mix(c, v.spot, 0.7);                 // pintas
      if (q.f === 'bottom') c = v.under;
      if (q.f === 'top' && n3(q.ax * 1.4, 0, q.az * 1.4, s + 9) > 0.8) c = mul(c, 1.18);                 // granulado da carapaça
      return c;
    };
    const body = shell(1), claw = shell(2);
    const leg = q => cel(mix(v.shell, v.under, 0.35), n3(q.ax, q.ay, q.az, 30 + v.seed), 0.78, 1.12);
    const tip = q => (q.ny > 0.5 ? [40, 30, 30] : claw(q));
    const eye = q => (q.ny < 0.4 ? [16, 14, 18] : leg(q));
    return { body, claw, leg, tip, eye };
  }
  function defOf(v, low) {
    const P = painters(v);
    const legs = [];
    [-3.2, 0, 3.2].forEach((z, i) => {
      if (low && i === 1) return;
      for (const s of [-1, 1]) legs.push({
        name: 'p' + i + (s < 0 ? 'D' : 'E'), pivot: [s * 7.6, 0.5, z], rot: [0, s * (i - 1) * 0.3, s * 0.55],
        boxes: [{ x: s < 0 ? -5.5 : 0, y: -0.7, z: -0.7, w: 5.5, h: 1.4, d: 1.4, p: P.leg }],
        kids: low ? [] : [{ name: 'q' + i + (s < 0 ? 'D' : 'E'), pivot: [s * 5.3, 0, 0], rot: [0, 0, -s * 1.5], boxes: [{ x: s < 0 ? -6 : 0, y: -0.55, z: -0.55, w: 6, h: 1.1, d: 1.1, p: P.leg }] }],
      });
    });
    // pinças: a direita é a grande (como os chama-maré)
    const claw = (s, big) => {
      const k = big ? 1.5 : 1;
      return {
        name: 'braco' + (s < 0 ? 'D' : 'E'), pivot: [s * 6, 0.5, -5], rot: [0.2, s * 0.6, 0],
        boxes: [{ x: -1.2 * k, y: -1.2 * k, z: -5 * k, w: 2.4 * k, h: 2.4 * k, d: 5 * k, p: P.claw }],
        kids: [{
          name: 'pinca' + (s < 0 ? 'D' : 'E'), pivot: [0, 0, -5 * k], rot: [0, -s * 1.1, 0],
          boxes: [{ x: -2 * k, y: -1.8 * k, z: -4 * k, w: 4 * k, h: 3.6 * k, d: 4 * k, p: P.claw }, { x: -1.6 * k, y: -1.2 * k, z: -8.4 * k, w: 1.8 * k, h: 2.4 * k, d: 4.4 * k, p: P.tip }],
          kids: low ? [] : [{ name: 'dedo' + (s < 0 ? 'D' : 'E'), pivot: [0.9 * k, 0, -4 * k], rot: [0, s * 0.3, 0], boxes: [{ x: -0.7 * k, y: -1 * k, z: -4.2 * k, w: 1.4 * k, h: 2 * k, d: 4.2 * k, p: P.tip }] }],
        }],
      };
    };
    const stalk = s => ({ name: 'olho' + (s < 0 ? 'D' : 'E'), pivot: [s * 2.2, -2.6, -5.4], rot: [-0.2, 0, s * 0.15], boxes: [{ x: -0.5, y: -4, z: -0.5, w: 1, h: 4, d: 1, p: P.leg }, { x: -0.8, y: -5.4, z: -0.8, w: 1.6, h: 1.6, d: 1.6, p: P.eye }] });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'corpo', pivot: [0, 19.5, 0], boxes: [{ x: -8, y: -3, z: -6, w: 16, h: 5.2, d: 12, p: P.body }, { x: -6.4, y: -4.2, z: -4.8, w: 12.8, h: 1.4, d: 9.6, p: P.body }],
        kids: legs.concat([claw(-1, true), claw(1, false)]).concat(low ? [] : [stalk(-1), stalk(1), { name: 'boca', pivot: [0, 0.4, -6], boxes: [{ x: -2, y: -0.8, z: -0.8, w: 4, h: 1.8, d: 0.8, p: P.leg }] }]),
      }],
    };
  }

  const STEP = [[0, -0.25], [0.3, 0.05], [0.55, 0.3], [0.75, 0]];
  const LIFT = [[0, 0], [0.55, 0], [0.65, -0.45], [0.85, 0]];
  function legs(r, ph, amt) {
    for (let i = 0; i < 3; i++) for (const s of ['D', 'E']) {
      const o = ph + (i === 1 ? 0.5 : 0) + (s === 'E' ? 0.5 : 0), sg = s === 'D' ? -1 : 1;
      r('p' + i + s, 0, 0, (loop(STEP, o) + loop(LIFT, o)) * amt * sg);
    }
  }
  // bolhas: cada uma com tamanho, caminho e estouro próprios
  function bubbles(n, seed) {
    const R = FX.rnd(seed), g = new THREE.Group(), list = [];
    const mat = new THREE.MeshPhongMaterial({ color: 0xdff4ff, transparent: true, opacity: 0.45, shininess: 120, specular: 0xffffff, depthWrite: false });
    for (let i = 0; i < n; i++) {
      const m = new THREE.Mesh(new THREE.SphereGeometry(1, 10, 8), mat);
      g.add(m); list.push({ m, s: 0.04 + R() * 0.09, dx: (R() - 0.5) * 0.7, dy: (R() - 0.5) * 0.5, lag: R() * 0.5, life: 0.5 + R() * 0.6, wob: R() * 6.28 });
    }
    g.at = (a, b, age) => {
      g.visible = true;
      for (const p of list) {
        const f = (age - p.lag) / p.life;
        p.m.visible = f > 0 && f < 1.3;
        if (!p.m.visible) continue;
        const ff = Math.min(1, f);
        p.m.position.set(a.x + (b.x + p.dx - a.x) * ff + 0.05 * Math.sin(p.wob + age * 8), a.y + (b.y + p.dy - a.y) * ff, a.z + (b.z - a.z) * ff);
        p.m.scale.setScalar(p.s * (f > 1 ? 1 + (f - 1) * 4 : 1));
      }
    };
    return g;
  }

  function anim(P, t, mode, a, obj, ent, ctx) {
    const r = (k, x, y, z) => { if (!P[k]) return; const o = P[k].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const eyes = T => { const k = loop([[0, 0], [0.45, 0], [0.5, 1], [0.6, 1], [0.65, 0]], T); r('olhoD', 0.7 * k, 0, 0); r('olhoE', 0.7 * k, 0, 0); };
    if (mode === 'parado') {
      legs(r, t * 0.12, 0.2);
      eyes(t * 0.3);
      // limpa a boca com a pinça pequena, de vez em quando
      const k = loop([[0, 0], [0.6, 0], [0.68, 1], [0.8, 1], [0.86, 0]], t * 0.25);
      r('bracoE', -0.3 * k, -0.5 * k, 0); r('pincaE', 0, 0.4 * k * (0.5 + 0.5 * Math.sign(Math.sin(t * 12))), 0);
      return;
    }
    if (mode === 'andar') {
      // anda de lado, como todo caranguejo
      legs(r, t * 1.6, 1);
      obj.position.x += 0.9 * fr(t * 0.25) - 0.45;
      r('corpo', 0, 0, 0.03 * loop([[0, -1], [0.5, 1]], t * 1.6));
      return;
    }
    const R = ctx.ref;
    if (mode === 'pinca') {
      const T = t % 3.0;
      R.place(0, -1.7, 0);
      // ergue a pinça grande aberta (aviso), desce e fecha na perna, segura 1 s e solta
      const lift = seq([[0.1, 0], [0.55, 1, 'out'], [0.68, 1]], T);
      const hit = seq([[0.68, 0], [0.78, 1, 'snap'], [1.9, 1], [2.3, 0, 'io']], T);
      const shut = seq([[0.76, 0], [0.82, 1, 'snap'], [1.8, 1], [1.95, 0]], T);
      r('bracoD', -0.9 * lift * (1 - hit) + 0.3 * hit, -0.4 * lift * (1 - hit) - 0.5 * hit, 0);
      r('pincaD', 0, 0.5 * lift * (1 - shut), 0); r('dedoD', 0, -0.8 * lift * (1 - shut) + 0.25 * shut, 0);
      r('corpo', -0.12 * lift * (1 - hit) + 0.08 * hit, 0, 0);
      obj.position.z -= 0.35 * hit;
      legs(r, T * 0.4, 0.3);
      eyes(T * 0.5);
      if (T > 0.8 && T < 1.9) { R.lean(0.18 + 0.03 * Math.sin(T * 20)); if (T < 0.95) ctx.shake(1 - (T - 0.8) / 0.15); }
      if (T > 0.8 && T < 1.2) ctx.slash(new THREE.Vector3(0.2, 0.35, -1.7), (T - 0.8) / 0.35);
      return;
    }
    if (mode === 'bolhas') {
      const T = t % 3.2;
      R.place(0, -2.6, 0);
      // infla, as pinças abrem pra trás e solta uma golfada de espuma na sua cara
      const puff = seq([[0.1, 0], [0.6, 1, 'out'], [0.75, 1], [0.85, 0, 'snap']], T);
      r('corpo', -0.15 * puff, 0, 0);
      P.corpo.obj.position.y -= 0.8 * puff;
      r('bracoD', 0, -0.4 * puff, 0); r('bracoE', 0, 0.4 * puff, 0);
      legs(r, T * 0.3, 0.2);
      eyes(T * 0.9);
      if (T > 0.8) {
        ctx.get('bolhas', () => bubbles(46, 71)).at(new THREE.Vector3(0, 0.45, -0.5), new THREE.Vector3(0, 1.6, -2.55), T - 0.8);
        // a espuma estoura no rosto: a vista embaça
        const k = seq([[1.15, 0], [1.3, 1], [2.6, 1], [3.1, 0]], T);
        if (k > 0) ctx.get('espuma', FX.volume).set(new THREE.Vector3(0, 1.6, -2.5), 0.45, 0.3 + 0.4 * (1 - k), { seed: 5.5, dens: 4, rise: 0.1, freq: 3, smoke: [0.92, 0.97, 1.0] });
        ctx.blur && ctx.blur(k);
        R.lean(0.15 * k);
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const SK = [
    { nome: 'Vermelho', seed: 1, shell: [178, 70, 50], spot: [220, 150, 96], under: [228, 200, 170] },
    { nome: 'Rochoso', seed: 2, shell: [92, 98, 104], spot: [64, 120, 116], under: [196, 190, 176] },
  ];
  window.MOBS.push({
    id: 'caranguejo_gigante', nome: 'Caranguejo Gigante', bioma: 'Mar da Névoa', tier: 'Fraco · tier 10',
    variants: SK.map(v => ({ nome: v.nome, v, def: defOf(v, false), low: defOf(v, true) })),
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar de lado'], ['pinca', 'Pinçada'], ['bolhas', 'Bolhas']],
    cam: 4.5, lookY: 0.5, lookZ: { pinca: -0.8, bolhas: -1.3 }, camFar: { pinca: 4.8, bolhas: 5.5 }, camYaw: { pinca: -1.1, bolhas: -1.2 }, refX: 2.2,
    info: {
      'Tamanho': '1,3 bloco de largura, 0,6 de altura',
      'Vida': '40 (tier 10)',
      'Jeito': 'Neutro, em grupos de 3 a 6 na praia e nas pedras. Só ataca se você bater em um deles.',
      'Pinçada': 'Ergue a pinça grande aberta (0,5 s de aviso) e fecha na sua perna: preso 1 s.',
      'Bolhas': 'Infla e solta uma golfada de espuma na sua cara: a vista embaça por 1,5 s.',
      'Solta': 'Carne de caranguejo (cozida cura bem), casca.',
    },
  });
})();
