// Maldição do Metrô (Shibuya, túneis e plataformas). Tier 5, grau 1. Nasceu do medo de quem ficou preso no metrô na
// noite do Incidente: uma centopeia de vagões de carne e lata, rostos espremidos nas janelas, dezenas de pernas de
// gente por baixo, faróis de olho e uma boca na porta da frente. Golpes: Atropelar e Próxima Estação (as portas
// do vagão abrem como bocas, te engolem e ela te cospe pela frente).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const NC = 5;                                       // vagões

  const LINES = [[[98, 172, 64], 'verde'], [[236, 140, 40], 'laranja'], [[150, 60, 150], 'roxa'], [[60, 120, 200], 'azul']];
  function variant(seed, line) {
    const R = FX.rnd(seed);
    return { seed, stripe: LINES[line ?? Math.floor(R() * LINES.length)][0], rust: 0.3 + R() * 0.5, flesh: mix([176, 120, 120], [150, 110, 130], R()), size: 0.95 + R() * 0.1 };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.8, hi = 1.12) => {
      const f = 0.62 - 0.4 * q.ny + 0.35 * (n3(q.ax * 0.25, q.ay * 0.25, q.az * 0.25, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    // lata do vagão: chapa prateada com rebites, faixa da linha, ferrugem que vira carne viva nas bordas
    const tin = (q, s) => {
      let c = shade([142, 146, 156], q, s, 0.74, 1.1);
      if (fr(q.az * 0.25) < 0.06) c = mul(c, 0.8);                                                       // emenda das chapas
      const rot = n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, 5 + S);
      if (rot > 0.74 - v.rust * 0.12) c = mix([150, 80, 60], v.flesh, rot > 0.8 - v.rust * 0.1 ? 0.8 : 0.2);
      return c;
    };
    // rosto espremido no vidro: cada janela sorteia o seu (posição, olhos, boca aberta)
    const windowFace = (q, cell) => {
      const R = FX.rnd(cell * 131 + S);
      const cx = 0.35 + R() * 0.3, cy = 0.4 + R() * 0.2, rr = 0.26 + R() * 0.1;
      const x = fr(q.az / 3.4), y = (q.py - 1.2) / 3.4;
      const dx = (x - cx) / rr, dy = (y - cy) / (rr * 1.25), d = dx * dx + dy * dy;
      let c = mix([30, 34, 44], [56, 64, 80], y);                                                         // vidro escuro
      if (R() < 0.15) return c;                                                                            // janela vazia
      if (d < 1) {
        c = mix(v.flesh, [220, 200, 196], 0.45 + 0.3 * (1 - d));                                          // a cara amassada no vidro
        if (Math.abs(dy + 0.25) < 0.18 && Math.abs(Math.abs(dx) - 0.38) < 0.16) c = [24, 16, 20];
        if (Math.abs(dy - 0.4) < 0.16 + R() * 0.14 && Math.abs(dx) < 0.3) c = [70, 20, 28];
        if (Math.abs(dx) > 0.8 && Math.abs(dy) < 0.4) c = mul(c, 0.8);                                     // mão espalmada
      }
      return c;
    };
    const P = {};
    P.car = q => {
      if (q.f === 'west' || q.f === 'east') {
        const y = q.py;
        if (y > 6.6 && y < 7.6) return shade(v.stripe, q, 3);
        if (y > 1.1 && y < 4.7 && fr(q.az / 3.4) > 0.12 && fr(q.az / 3.4) < 0.88) return windowFace(q, Math.floor(q.az / 3.4) + (q.f === 'west' ? 50 : 0));
        if (y >= 8.4) return mul(tin(q, 2), 0.7);                                                          // saia escura de baixo
      }
      if (q.f === 'top') { let c = tin(q, 4); if (Math.abs(q.nx - 0.5) < 0.12) c = mul(c, 0.82); return c; }
      return tin(q, 2);
    };
    P.under = q => shade([60, 50, 54], q, 6);
    P.gap = q => {                                                                                         // sanfona entre vagões = carne
      let c = shade(v.flesh, q, 7, 0.75, 1.1);
      if (fr(q.az * 1.6) < 0.4) c = mul(c, 0.72);
      return c;
    };
    P.front = q => {
      if (q.f !== 'north') return P.car(q);
      const x = q.px / q.b.w, y = q.py / q.b.h;
      let c = tin(q, 8);
      if (y > 0.7 && y < 0.78) c = shade(v.stripe, q, 3);
      return c;
    };
    P.eye = q => {                                                                                         // para-brisa: um olho enorme cada
      if (q.f !== 'north') return [30, 30, 36];
      const x = q.px / q.b.w - 0.5, y = q.py / q.b.h - 0.5, d = Math.hypot(x / 0.5, y / 0.5);
      if (d > 0.98) return [30, 30, 36];
      let c = [226, 220, 204];
      if (n3(q.px * 0.8, q.py * 0.8, 0, 9 + S) > 0.72) c = [210, 120, 110];
      if (Math.hypot(x - 0.08, y + 0.02) < 0.18) c = [178, 40, 30];
      if (Math.hypot(x - 0.08, y + 0.02) < 0.09) c = [10, 6, 8];
      return c;
    };
    P.lamp = q => (q.f === 'north' ? [255, 244, 200] : [120, 120, 110]);
    P.door = q => {
      let c = tin(q, 10);
      if (q.f === 'west' || q.f === 'east' || q.f === 'north') {
        if (q.py > 1 && q.py < 4.6 && q.nz > 0.15 && q.nz < 0.85) c = mix([30, 34, 44], [56, 64, 80], q.ny);
        if (q.py > 6.6 && q.py < 7.6) c = shade(v.stripe, q, 3);
      }
      return c;
    };
    P.maw = q => {                                                                                         // dentro da porta: gengiva e dentes de gente
      const y = q.py / q.b.h;
      if (y < 0.18 || y > 0.82) return Math.floor(q.az * 1.2 + q.ax * 1.2) % 2 ? [228, 218, 184] : [70, 18, 30];
      return mix([90, 18, 30], [50, 8, 18], n3(q.ax * 0.5, q.ay * 0.5, q.az * 0.5, 12 + S));
    };
    P.skinLeg = q => shade(mix(v.flesh, [200, 170, 150], 0.4), q, 13);
    P.pants = q => shade([[40, 42, 54], [64, 60, 56], [30, 30, 34]][Math.floor(Math.abs(q.ax * 3 + q.az)) % 3], q, 14);
    P.shoe = q => [30, 26, 26];
    P.tooth = q => mix([232, 222, 186], [200, 180, 120], n3(q.ax * 3, q.ay * 3, q.az * 3, 15 + S));
    P.lip = q => shade([110, 40, 54], q, 16);
    return P;
  }

  function legsOf(P, i, z0, d, lo) {
    const out = [];
    const nz = lo ? 1 : 2;
    for (let k = 0; k < nz; k++) for (const s of [-1, 1]) {
      const z = z0 + (k + 0.5) * d / nz;
      out.push({
        name: `perna${i}_${k}${s < 0 ? 'D' : 'E'}`, pivot: [s * 4.2, 2.6, z], rot: [0, 0, s * -0.25],
        boxes: [{ x: -1.3, y: 0, z: -1.3, w: 2.6, h: 5.2, d: 2.6, p: P.pants }],
        kids: lo ? [] : [{
          name: `canela${i}_${k}${s < 0 ? 'D' : 'E'}`, pivot: [0, 5, 0], rot: [0, 0, s * 0.25], boxes: [{ x: -1.1, y: 0, z: -1.1, w: 2.2, h: 4.6, d: 2.2, p: P.skinLeg }],
          kids: [{ name: `pe${i}_${k}${s < 0 ? 'D' : 'E'}`, pivot: [0, 4.4, 0], boxes: [{ x: -1.3, y: 0, z: -2.4, w: 2.6, h: 1.2, d: 3.6, p: P.shoe }] }],
        }],
      });
    }
    return out;
  }
  function doors(P, i, z0, lo) {
    if (lo) return [];
    const out = [];
    for (const s of [-1, 1]) {
      const x = s < 0 ? -5.6 : 5.0;
      out.push({ name: `boca${i}${s < 0 ? 'D' : 'E'}`, pivot: [0, 0, 0], boxes: [{ x: s < 0 ? -5.2 : 4.6, y: -5.2, z: z0 + 4, w: 0.6, h: 8, d: 5, p: P.maw }] });
      out.push({ name: `portaA${i}${s < 0 ? 'D' : 'E'}`, pivot: [0, 0, z0 + 4], boxes: [{ x, y: -5.4, z: 0, w: 0.6, h: 8.4, d: 2.5, p: P.door }] });
      out.push({ name: `portaB${i}${s < 0 ? 'D' : 'E'}`, pivot: [0, 0, z0 + 6.5], boxes: [{ x, y: -5.4, z: 0, w: 0.6, h: 8.4, d: 2.5, p: P.door }] });
    }
    return out;
  }
  function rig(P, v, lo) {
    // cada vagão é filho do anterior: a cobra dobra vagão a vagão
    let tail = null;
    for (let i = NC - 1; i >= 1; i--) {
      const node = {
        name: 'vagao' + i, pivot: [0, 0, i === 1 ? 6.4 : 13.4], boxes: [
          { x: -5, y: -6, z: 0.6, w: 10, h: 9.4, d: 12.4, p: P.car },
          { x: -4.6, y: 3.4, z: 1, w: 9.2, h: 0.8, d: 11.6, p: P.under },
          ...(lo ? [] : [{ x: -3.6, y: -5, z: -0.6, w: 7.2, h: 7.6, d: 1.4, p: P.gap }]),
        ],
        kids: [...legsOf(P, i, 1.2, 11, lo), ...doors(P, i, 1.2, lo), ...(tail ? [tail] : [])],
      };
      tail = node;
    }
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'vagao0', pivot: [0, 10.4, 0], boxes: [
          { x: -5, y: -6, z: -7.2, w: 10, h: 9.4, d: 13.4, p: P.car },
          { x: -4.6, y: 3.4, z: -6.8, w: 9.2, h: 0.8, d: 12.6, p: P.under },
        ],
        kids: [
          {
            name: 'frente', pivot: [0, 0, -7.2], boxes: [
              { x: -5, y: -6, z: -1.6, w: 10, h: 4.6, d: 1.6, p: P.front },
              { x: -5.2, y: -6.6, z: -1.2, w: 10.4, h: 0.8, d: 2.6, p: P.front },
              { x: -4.6, y: -5.6, z: -1.9, w: 4.2, h: 3.8, d: 0.4, p: P.eye },
              { x: 0.4, y: -5.6, z: -1.9, w: 4.2, h: 3.8, d: 0.4, p: P.eye },
              ...(lo ? [] : [{ x: -4.6, y: 1.2, z: -1.9, w: 1.4, h: 1, d: 0.4, p: P.lamp }, { x: 3.2, y: 1.2, z: -1.9, w: 1.4, h: 1, d: 0.4, p: P.lamp }]),
              { x: -3.6, y: -1.4, z: -0.6, w: 7.2, h: 1.2, d: 0.6, p: P.lip },
              ...(lo ? [] : [-3, -1.8, -0.6, 0.6, 1.8].map(x => ({ x, y: -0.4, z: -1.0, w: 1.0, h: 1.4, d: 0.8, p: P.tooth }))),
            ],
            kids: [{
              name: 'mandibula', pivot: [0, -1.4, -0.4], boxes: [
                { x: -5, y: 0, z: -1.2, w: 10, h: 4.8, d: 1.6, p: P.front },
                { x: -3.6, y: 0, z: -0.2, w: 7.2, h: 3.6, d: 1.6, p: P.maw },
                ...(lo ? [] : [-3, -1.8, -0.6, 0.6, 1.8].map(x => ({ x, y: -0.1, z: -1.3, w: 1.0, h: 1.3, d: 0.8, p: P.tooth }))),
              ],
            }],
          },
          ...legsOf(P, 0, -6.2, 11.6, lo),
          tail,
        ],
      }],
    };
  }

  function legWalk(r, i, ph, k = 1) {
    // onda de centopeia: cada par entra atrasado em relação ao da frente
    for (let kk = 0; kk < 2; kk++) for (const sd of ['D', 'E']) {
      const off = (i * 2 + kk) * 0.11 + (sd === 'D' ? 0 : 0.5);
      r(`perna${i}_${kk}${sd}`, k * loop([[0, -0.6], [0.4, 0.5], [0.6, 0.55], [0.85, -0.3]], ph + off), 0, 0);
      r(`canela${i}_${kk}${sd}`, k * loop([[0, 0.1], [0.45, 0.2], [0.6, 0.9], [0.85, 0.4]], ph + off), 0, 0);
    }
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size * 1.6; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const lampK = 0.8 + 0.2 * (n3(t * 6, 0, 0, 2) > 0.3 ? 1 : 0);
    const lamps = k => {
      const L = ctx.get('farol', () => DBFX.light(0xfff0c0));
      obj.updateMatrixWorld(true);
      const p = new THREE.Vector3(); P.frente.obj.getWorldPosition(p);
      L.at(p.add(new THREE.Vector3(0, 0.1, -0.6)), 2.2 * k, 7);
    };
    const doorsOpen = k => {
      for (let i = 1; i < NC; i++) for (const sd of ['D', 'E']) {
        const kk = FX.clamp01(k * 1.4 - (i - 1) * 0.12);
        if (P['portaA' + i + sd]) { P['portaA' + i + sd].obj.position.z -= 2.4 * kk; P['portaB' + i + sd].obj.position.z += 2.4 * kk; }
      }
    };

    if (mode === 'parado') {
      for (let i = 1; i < NC; i++) r('vagao' + i, 0, 0.12 * Math.sin(t * 0.6 - i * 0.9), 0.02 * Math.sin(t * 0.8 - i));
      for (let i = 0; i < NC; i++) legWalk(r, i, t * 0.2, 0.25);
      r('mandibula', 0.15 + 0.1 * Math.sin(t * 1.1), 0, 0);
      P.vagao0.obj.position.y += 0.3 * Math.sin(t * 1.2);
      lamps(lampK);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 1.1;
      for (let i = 1; i < NC; i++) r('vagao' + i, 0, 0.28 * (Math.sin(ph * 2 - i * 0.9) - Math.sin(ph * 2 - (i - 1) * 0.9)) * 1.3, 0);
      obj.rotation.y = 0.18 * Math.sin(ph * 2);
      obj.position.x += 0.25 * Math.sin(ph * 2 - 0.5);
      for (let i = 0; i < NC; i++) legWalk(r, i, ph * 1.4);
      P.vagao0.obj.position.y += 0.35 * Math.abs(Math.sin(ph * 4));
      r('mandibula', 0.2, 0, 0);
      lamps(lampK);
      return;
    }

    if (mode === 'atropelar') {
      const T = t % 4.2;
      const R = ctx.ref; const RZ = -7.5;
      // trava: abaixa a frente, os faróis acendem forte, as pernas batem no chão; dispara em linha reta
      const brace = seq([[0.1, 0], [0.8, 1, 'out'], [0.95, 1]], T);
      const dash = FX.clamp01((T - 0.95) / 0.7);
      const runPh = T * 4.5;
      r('vagao0', 0.12 * brace, 0, 0);
      for (let i = 1; i < NC; i++) r('vagao' + i, -0.03 * brace, 0.05 * Math.sin(T * 30 + i) * brace * (1 - dash), 0);
      for (let i = 0; i < NC; i++) legWalk(r, i, T < 0.95 ? T * 3 : runPh, T < 0.95 ? 0.4 : 1.2);
      r('mandibula', 0.25 + 0.6 * dash * (T < 1.7 ? 1 : 0), 0, 0);
      obj.position.z += -9.5 * FX.EASE.in(dash) - (T > 1.65 ? 1.2 * FX.EASE.out(FX.clamp01((T - 1.65) / 0.5)) : 0);
      lamps(lampK + 2.5 * brace * (T < 2.2 ? 1 : 0.3));
      if (dash > 0 && dash < 1) {
        ctx.shake(0.35);
        ctx.get('poeira', () => DBFX.grains({
          n: 90, seed: 23, palette: [[110, 106, 100], [80, 78, 74], [140, 136, 128]], size: [0.04, 0.1], drag: 1.6, grav: 3, life: 1.0, spread: 0.7,
          cone: Rr => { const a = (Rr() - 0.5) * 2.4; return [Math.sin(a) * 2.5, 0.8 + Rr() * 1.4, 2 + Rr() * 2]; },
        })).at(new THREE.Vector3(0, 0.1, -9.5 * FX.EASE.in(dash) + 1.5), T - 0.95, 0.02);
      }
      const hitT = 0.95 + 0.62;
      if (T < hitT) R.place(0, RZ, 0);
      else {
        // o alvo voa alto por cima dos vagões
        const c = T - hitT, f = FX.clamp01(c / 1.1);
        R.place(0.6 * f, RZ + 5.5 * f, 3.6 * Math.sin(Math.PI * f) * (1 - 0.15 * f));
        R.spin(5 * f); R.lean(2.2 * f + (f >= 1 ? 1.57 - 2.2 : 0));
        ctx.shake(Math.max(0, 1 - c / 0.4));
        ctx.flash(new THREE.Vector3(0, 1.4, RZ + 1), Math.max(0, 1 - c / 0.25));
      }
      return;
    }

    if (mode === 'portas') {
      const T = t % 5.2;
      const R = ctx.ref; const RX = -2.3, RZ = 4.0;
      for (let i = 1; i < NC; i++) r('vagao' + i, 0, 0.06 * Math.sin(t * 0.6 - i * 0.9), 0);
      for (let i = 0; i < NC; i++) legWalk(r, i, t * 0.3, 0.3);
      // "próxima estação": as portas abrem todas de uma vez, com dente nas bordas; uma língua puxa
      const open = seq([[0.3, 0], [0.75, 1, 'out'], [1.9, 1], [2.05, 0, 'snap']], T) + seq([[3.6, 0], [3.65, 0]], T);
      doorsOpen(open);
      const pull = seq([[0.9, 0], [1.9, 1, 'in']], T);
      // porta do vagão 2, lado direito do modelo (x negativo no mundo é o lado esquerdo da tela): o alvo é puxado pra dentro
      obj.updateMatrixWorld(true);
      const door = new THREE.Vector3(); P.vagao2.obj.localToWorld(door.set(5.2, -1, 6.5));
      if (T < 1.95) {
        const from = new THREE.Vector3(RX, 0, RZ);
        const to = new THREE.Vector3(door.x, Math.max(0, door.y - 1.0), door.z);
        const p = from.clone().lerp(to, pull);
        R.place(p.x, p.z, p.y + 0.6 * Math.sin(Math.PI * pull)); R.lean(0.6 * pull); R.armsOut(0.8 * pull);
        if (pull > 0) ctx.get('lingua', () => tongue()).at(door, new THREE.Vector3(p.x, p.y + 1.1, p.z), pull < 0.98 ? 1 : 0);
      } else if (T < 3.6) {
        // dentro: o trem sacode, as janelas piscam, o barulho anda pelos vagões até a frente
        R.place(99, 0, 0);
        const c = T - 1.95;
        const wave = c / 1.6 * NC;
        for (let i = 0; i < NC; i++) { const k = Math.max(0, 1 - Math.abs(wave - (NC - 1 - i) * 1.0)); if (P['vagao' + i]) P['vagao' + i].obj.scale.set(1 + 0.12 * k, 1 + 0.12 * k, 1); }
        ctx.shake(0.25);
        ctx.dark(0.15);
        r('mandibula', 0.15 + 0.6 * seq([[1.2, 0], [1.6, 1, 'out']], c), 0, 0);
      } else {
        // cospe pela boca da frente
        const c = T - 3.6, f = FX.clamp01(c / 0.9);
        r('mandibula', 0.9 * (1 - seq([[0.2, 0], [0.6, 1]], c)), 0, 0);
        obj.updateMatrixWorld(true);
        const m = new THREE.Vector3(); P.frente.obj.getWorldPosition(m);
        R.place(m.x, m.z - 4.2 * FX.EASE.out(f), Math.max(0, m.y - 0.9 + 1.2 * Math.sin(Math.PI * f) - 0.9 * f));
        R.spin(4 * f); R.lean(1.57 * f);
        ctx.get('gosma', () => DBFX.grains({
          n: 60, seed: 77, palette: [[150, 30, 50], [110, 16, 30], [200, 160, 160]], size: [0.03, 0.08], drag: 1.5, grav: 9.8, life: 0.9,
          cone: Rr => { const a = (Rr() - 0.5) * 1.6; return [Math.sin(a) * 1.5, 0.5 + Rr() * 2, -3 - Rr() * 3]; },
        })).at(m, c, 0.02);
      }
      lamps(lampK);
      return;
    }

    if (mode === 'morte') {
      const { T, fall } = JJFX.exorcize(t, obj, e, ctx, { h: 1.8, refZ: -2.4, rotX: 0, back: 0.3, ashY: 0.5, ashZ: 3.0, n: 320, dur: 3.8 });
      // tomba de lado, vagão a vagão
      for (let i = 0; i < NC; i++) { const k = FX.clamp01(fall * 1.6 - i * 0.15); r('vagao' + i, 0, 0, (i === 0 ? 1.5 : 0.02) * k); }
      for (let i = 0; i < NC; i++) legWalk(r, i, t * 3, 1 - fall);
      obj.position.y -= 0.3 * fall;
      r('mandibula', 0.8 * fall, 0, 0);
      // nenhum membro escora o morto: o corpo encosta no chão (teste R5)
      { const L = []; for (let i = 0; i < NC; i++) for (const j of [0, 1]) for (const sd of ['D', 'E']) L.push('perna' + i + '_' + j + sd); const core = []; for (let i = 0; i < NC; i++) core.push('vagao' + i); JJFX.floorLimbs(obj, P, core, L, fall); }
      if (T < 1) lamps(lampK * (1 - fall));
    }
  }

  // língua comprida que sai da porta e enrola no alvo: tubo de carne afinando, com babas
  function tongue() {
    const N = 20, geo = new THREE.CylinderGeometry(1, 1, 1, 8, N, true);
    const base = geo.attributes.position.array.slice();
    const mat = new THREE.MeshLambertMaterial({ color: 0xb0465c });
    const m = new THREE.Mesh(geo, mat); m.frustumCulled = false;
    m.at = (a, b, k) => {
      m.visible = k > 0;
      const pos = geo.attributes.position;
      const d = b.clone().sub(a), L = d.length(), side = new THREE.Vector3(0, 1, 0).cross(d).normalize();
      for (let i = 0; i < pos.count; i++) {
        const bx = base[i * 3], by = base[i * 3 + 1], bz = base[i * 3 + 2];
        const f = by + 0.5, w = 0.11 * (1 - 0.6 * f);
        const p = a.clone().addScaledVector(d, f).addScaledVector(side, 0.3 * Math.sin(Math.PI * f)).add(new THREE.Vector3(0, 0.25 * Math.sin(Math.PI * f), 0));
        pos.setXYZ(i, p.x + bx * w, p.y + bz * w * 0.6, p.z + bz * w);
      }
      pos.needsUpdate = true; geo.computeVertexNormals();
    };
    return m;
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(9, 0), variant(27, 1), variant(63, 2)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'metro', nome: 'Maldição do Metrô', bioma: 'Shibuya', tier: 'Grau 1 · tier 5',
    variants: fixed.map((v, i) => ({ nome: ['Linha verde', 'Linha laranja', 'Linha roxa'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteada', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['atropelar', 'Atropelar'], ['portas', 'Próxima Estação'], ['morte', 'Exorcizada']],
    cam: 11, lookY: 1.2, lookZ: { parado: 3.5, andar: 3.5, atropelar: -1.5, portas: 3.5, morte: 3.5 }, camFar: { atropelar: 15, portas: 11, morte: 11 }, camYaw: { parado: -0.9, andar: -0.9, atropelar: -1.35, portas: -2.3, morte: -0.9 },
    info: {
      'Tamanho': '5 vagões, uns 8 blocos de comprimento e 1,7 de altura',
      'Vida': '220 (grau 1)',
      'Jeito': 'Hostil, sozinha. Mora nos túneis e plataformas do metrô de Shibuya; de noite sobe pras avenidas largas. No máximo 1 por estação. Os faróis acendem antes dela aparecer.',
      'Atropelar': 'Abaixa a frente, os faróis estouram de luz e ela dispara 10 blocos em linha reta. Quem é pego voa por cima dos vagões. Dá tempo de sair do trilho quando os faróis acendem.',
      'Próxima Estação': 'Todas as portas abrem de uma vez com dentes nas bordas; uma língua sai da porta mais perto, te puxa pra dentro, você passa vagão por vagão (tela escura, dano por segundo) e é cuspido pela boca da frente. Bater na língua corta e solta.',
      'Solta': 'Resíduo de energia amaldiçoada (muito); raro: bilhete amaldiçoado (teleporta pra estação mais perto).',
      'Variações': 'Cor da faixa da linha, quanto da lata já virou carne, rostos diferentes em cada janela.',
    },
  });
})();
