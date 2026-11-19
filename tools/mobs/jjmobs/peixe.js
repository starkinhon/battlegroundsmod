// Peixe-Shikigami (Mar de Dagon). Tier 10. Os peixes que o Dagon solta no domínio dele: nadam no ar como se o ar
// fosse água, em cardume, e mordem até arrancar pedaço. Três espécies: piranha (dentes pra fora, a que mais morde),
// atum (rápido, vem em linha reta) e peixe-lanterna (do fundo do mar: boca enorme de agulhas e uma luz na testa).
// Golpes: Mordida (recua, enrola o rabo, dispara e sacode a cabeça presa em você) e Cardume (o bando roda em volta
// e cada um entra mordendo na sua vez). Quando morrem caem no chão, se debatem e viram água do mar.
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  // forma de cada espécie: meia altura, frente, trás, largura, boca, queixo pra fora, rabo, altura da barbatana
  const KINDS = [
    { nome: 'Piranha', back: [92, 104, 120], belly: [206, 70, 52], fin: [150, 60, 52], iris: [230, 60, 40], hh: 4, zf: -5, zb: 1.6, w: 4, mL: 3, under: 0.9, tail: 3.4, finH: 9, finD: 3.6, teeth: 'serra' },
    { nome: 'Atum', back: [36, 62, 120], belly: [214, 222, 228], fin: [230, 196, 60], iris: [30, 30, 40], hh: 3.1, zf: -6.2, zb: 2.6, w: 4.4, mL: 2, under: 0, tail: 4.8, finH: 11.5, finD: 3.8, teeth: 'nenhum' },
    { nome: 'Peixe-Lanterna', back: [50, 40, 54], belly: [86, 70, 80], fin: [40, 30, 44], iris: [200, 230, 240], hh: 3.8, zf: -5.4, zb: 1.2, w: 4.6, mL: 3.6, under: 0.5, tail: 2.8, finH: 7, finD: 3, teeth: 'agulha', lure: true },
  ];
  function variant(seed, kind) {
    const R = FX.rnd(seed);
    const k = kind ?? Math.floor(R() * KINDS.length);
    return { seed, kind: k, K: KINDS[k], size: 0.92 + R() * 0.16, scar: R() < 0.5, hue: R() };
  }

  function painters(v) {
    const S = v.seed, K = v.K;
    const shade = (base, q, s, lo = 0.76, hi = 1.16) => {
      const f = 0.6 - 0.32 * q.ny + 0.3 * (n3(q.ax * 0.4, q.ay * 0.4, q.az * 0.4, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    // corpo: costas escuras que descem em degrau pra barriga clara (contra-sombra de peixe), escamas em losango
    const skin = (q, s) => {
      const yb = (q.ay - 6 + K.hh) / (2 * K.hh);                                       // 0 = costas, 1 = barriga
      const edge = 0.5 + 0.08 * (n3(q.az * 0.5, 0, q.ax * 0.5, 3 + S) - 0.5) * 2;
      let c = shade(yb < edge ? K.back : K.belly, q, s);
      if (yb > edge - 0.06 && yb < edge) c = mix(c, K.belly, 0.4);
      if (q.f !== 'top' && q.f !== 'bottom' && fr((q.az + q.ay) * 0.45) < 0.12 && fr((q.az - q.ay) * 0.45) < 0.5) c = mul(c, 0.86);
      if (q.f !== 'top' && q.f !== 'bottom' && Math.abs(yb - 0.42) < 0.03) c = mul(c, 1.12);  // linha lateral
      if (K.nome === 'Piranha' && yb < edge && n3(q.ax * 1.3, q.ay * 1.3, q.az * 1.3, 4 + S) > 0.72) c = mix(c, [190, 200, 210], 0.35); // pintas prateadas
      if (K.nome === 'Peixe-Lanterna' && n3(q.ax * 0.9, q.ay * 0.9, q.az * 0.9, 5 + S) > 0.74) c = mix(c, [120, 200, 220], 0.25);       // fotóforos
      if (v.scar && (q.f === 'east') && Math.abs(q.az - (K.zf + 3.8) - 0.6 * (q.ay - 6)) < 0.45 && Math.abs(q.ay - 6) < K.hh * 0.6) c = [230, 200, 196];       // cicatriz de mordida
      return c;
    };
    const P = {};
    P.body = q => {
      let c = skin(q, 1);
      // olho redondo dos dois lados, perto da frente
      if (q.f === 'east' || q.f === 'west') {
        const ez = K.zf + (K.nome === 'Peixe-Lanterna' ? 1.3 : 1.5), ey = 6 - K.hh * 0.38;
        const d = Math.hypot(q.az - ez, q.ay - ey);
        if (d < 1.25) {
          if (d < 0.62) c = [8, 6, 10];
          else c = K.nome === 'Atum' ? [196, 196, 200] : mix(K.iris, mul(K.iris, 0.6), (d - 0.62) / 0.6);
          if (Math.abs(q.az - ez + 0.3) < 0.3 && Math.abs(q.ay - ey + 0.3) < 0.3) c = [255, 255, 255];
        } else if (d < 1.55) c = mul(c, 0.7);
        // opérculo (tampa da guelra): risco curvo atrás do olho
        const gz = ez + 1.9 + 1.5 * Math.pow((q.ay - 6) / K.hh, 2);
        if (Math.abs(q.az - gz) < 0.32 && q.ay > 6 - K.hh * 0.7) c = mul(c, 0.68);
      }
      // boca por dentro (embaixo da frente): carne escura
      if (q.f === 'bottom' && q.az < K.zf + K.mL) c = q.nx < 0.1 || q.nx > 0.9 ? c : [70, 20, 30];
      return c;
    };
    P.belly = q => skin(q, 2);
    P.jaw = q => {
      let c = skin(q, 3);
      if (q.f === 'top') { const edge = q.nx < 0.1 || q.nx > 0.9 || q.pz < 0.5; c = edge ? [120, 40, 50] : [64, 16, 26]; if (q.pz > 0.5 && q.pz < 1.4 && q.nx > 0.3 && q.nx < 0.7) c = [150, 70, 90]; } // língua
      if (q.f === 'north' && q.ny < 0.35 && K.teeth === 'serra') c = fr(q.px * 0.9) < 0.6 && q.ny < 0.35 * (1 - Math.abs(fr(q.px * 0.9) - 0.3) / 0.3) ? [236, 230, 210] : c;
      return c;
    };
    P.tooth = q => { const c = [236, 232, 214]; return q.ny > 0.7 ? mul(c, 0.86) : c; };
    P.fin = q => {
      // nadadeira fina: raios em leque (mais escuros), bordas rasgadas
      let c = shade(K.fin, q, 6, 0.8, 1.12);
      if (fr(q.nx * 5 + q.nz * 3) < 0.2) c = mul(c, 0.78);
      return c;
    };
    // rabo: corte em forquilha (atum) ou leque reto (piranha, lanterna). Fora da forma = vazio.
    P.tailfin = q => {
      const u = q.pz / q.b.d, vv = Math.abs(q.py - q.b.h / 2) / (q.b.h / 2);
      const fork = K.nome === 'Atum';
      const w = fork ? 0.25 + 0.75 * Math.pow(u, 0.6) : 0.35 + 0.65 * u;
      if (vv > w) return null;
      if (fork && u > 0.55 && vv < (u - 0.55) * 1.5) return null;                                        // forquilha
      if (!fork && u > 0.82 && vv < 0.5 && fr(q.py * 0.7) < 0.35) return null;                              // borda rasgada
      let c = shade(K.fin, q, 7, 0.8, 1.15);
      if (fr(Math.atan2(q.py - q.b.h / 2, q.pz + 1) * 6) < 0.25) c = mul(c, 0.8);
      if (fork && u > 0.7) c = mix(c, K.back, 0.35);
      return c;
    };
    P.dorsal = q => {
      const u = q.pz / q.b.d, hgt = 1 - q.py / q.b.h;                                                       // hgt: 0 base, 1 topo
      const lim = K.nome === 'Atum' ? 1 - u * 1.1 : 1 - Math.pow(u, 1.4);
      if (hgt > lim) return null;
      if (K.nome === 'Piranha' && fr(u * 5) > 0.55 && hgt > lim - 0.25) return null;                        // espinhos
      let c = shade(K.fin, q, 8);
      if (fr(u * 5) < 0.18) c = mul(c, 0.7);
      return c;
    };
    P.pec = q => {
      const u = Math.abs(q.nx - (q.b.x < 0 ? 1 : 0)), z = q.nz;                                            // u: 0 junto do corpo, 1 ponta
      if (u > 0.95 - 0.5 * Math.abs(z - 0.35)) return null;
      let c = shade(K.fin, q, 9);
      if (fr(z * 4 + u) < 0.22) c = mul(c, 0.76);
      return c;
    };
    P.stalk = q => shade(mul(K.back, 1.2), q, 10);
    P.lure = q => { const d = Math.hypot(q.nx - 0.5, q.ny - 0.5, q.nz - 0.5); return d < 0.3 ? [250, 255, 230] : [150, 240, 220]; };
    return P;
  }

  function rig(P, v, lo) {
    const K = v.K, w = K.w, hh = K.hh, hinge = hh * 0.15, mL = K.mL;
    const CY = 6;                                                                                            // altura do meio do peixe (1,1 bloco)
    const teeth = [];
    if (!lo && K.teeth === 'serra') for (let i = 0; i < 5; i++) teeth.push({ x: -w / 2 + 0.6 + i * (w - 1.2) / 4 - 0.3, y: -1.1, z: -mL - K.under + 0.1, w: 0.6, h: 1.2, d: 0.6, p: P.tooth });
    const upTeeth = [];
    if (!lo && K.teeth === 'agulha') for (let i = 0; i < 4; i++) {
      upTeeth.push({ x: -w / 2 + 0.5 + i * (w - 1) / 3 - 0.2, y: hinge, z: K.zf + 0.2 + (i % 2) * 0.5, w: 0.4, h: 2.2, d: 0.4, p: P.tooth });
    }
    const loTeeth = [];
    if (!lo && K.teeth === 'agulha') for (let i = 0; i < 3; i++) loTeeth.push({ x: -w / 2 + 0.9 + i * (w - 1.8) / 2 - 0.2, y: -2.0, z: -mL - K.under + 0.3, w: 0.4, h: 2.0, d: 0.4, p: P.tooth });
    const pec = s => ({ name: s < 0 ? 'peitD' : 'peitE', pivot: [s * w / 2, hinge + 0.2, K.zf + mL + 1.0], rot: [0, -s * 0.6, s * 0.35], boxes: [{ x: s > 0 ? 0 : -2.6, y: -0.2, z: -0.6, w: 2.6, h: 0.4, d: 2.2, p: P.pec }] });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'corpo', pivot: [0, CY, 0], boxes: [
          // cabeça um pouco mais estreita e baixa que o corpo (testa em degrau), corpo atrás
          { x: -w / 2 + 0.3, y: -hh + 1, z: K.zf, w: w - 0.6, h: hh + hinge - 1, d: 2.6, p: P.body },
          { x: -w / 2, y: -hh, z: K.zf + 2.6, w, h: hh + hinge, d: K.zb - K.zf - 2.6, p: P.body },
          { x: -w / 2 + 0.2, y: hinge, z: K.zf + mL, w: w - 0.4, h: hh - hinge, d: K.zb - K.zf - mL, p: P.belly },
          // dorsal em cima, nadadeira anal embaixo
          { x: -0.3, y: -hh - (K.nome === 'Atum' ? 3 : 3.4), z: K.zf + 2, w: 0.6, h: K.nome === 'Atum' ? 3 : 3.4, d: K.nome === 'Atum' ? 3 : 4.4, p: P.dorsal },
          ...(lo ? [] : [{ x: -0.3, y: hh, z: K.zb - 2.6, w: 0.6, h: 1.8, d: 2.4, p: P.dorsal }]),
          ...upTeeth,
        ],
        kids: [
          { name: 'mandibula', pivot: [0, hinge, K.zf + mL], boxes: [{ x: -w / 2 + 0.4, y: 0, z: -mL - K.under, w: w - 0.8, h: hh - hinge - 0.3, d: mL + K.under, p: P.jaw }, ...teeth, ...loTeeth] },
          pec(-1), pec(1),
          {
            name: 'cauda', pivot: [0, 0, K.zb], boxes: [{ x: -w * 0.33, y: -hh * 0.6, z: 0, w: w * 0.66, h: hh * 1.2, d: K.tail, p: P.belly }],
            kids: [{ name: 'barbatana', pivot: [0, 0, K.tail], boxes: [{ x: -0.3, y: -K.finH / 2, z: -0.4, w: 0.6, h: K.finH, d: K.finD, p: P.tailfin }] }],
          },
          ...(K.lure ? [{
            name: 'isca', pivot: [0, -hh, K.zf + 1.4], rot: [0.75, 0, 0], boxes: [{ x: -0.3, y: -5.2, z: -0.3, w: 0.6, h: 5.2, d: 0.6, p: P.stalk }],
            kids: [{ name: 'luz', pivot: [0, -5.2, 0], rot: [0.9, 0, 0], boxes: [{ x: -0.3, y: -2, z: -0.3, w: 0.6, h: 2, d: 0.6, p: P.stalk }, { x: -0.9, y: -3.6, z: -0.9, w: 1.8, h: 1.8, d: 1.8, p: P.lure }] }],
          }] : []),
        ],
      }],
    };
  }

  // nado: o rabo leva o corpo (onda que passa da cauda pra barbatana), peitorais remando, boca respirando
  function swim(r, t, amp = 1, speed = 1, ph0 = 0) {
    const ph = t * 7 * speed + ph0;
    r('corpo', 0, -0.12 * amp * Math.sin(ph + 0.6), 0);
    r('cauda', 0, 0.45 * amp * Math.sin(ph), 0);
    r('barbatana', 0, 0.6 * amp * Math.sin(ph - 1.1), 0);
    r('peitD', 0, 0.2 * Math.sin(ph * 0.5 + ph0), -0.35 * Math.sin(ph * 0.5 + ph0));
    r('peitE', 0, -0.2 * Math.sin(ph * 0.5 + ph0 + 0.5), 0.35 * Math.sin(ph * 0.5 + ph0 + 0.5));
    r('mandibula', 0.12 + 0.1 * Math.sin(t * 3.1 + ph0), 0, 0);
    r('isca', 0.08 * Math.sin(t * 2 + ph0), 0, 0.05 * Math.sin(t * 1.3)); r('luz', 0.15 * Math.sin(t * 2 - 0.8 + ph0), 0, 0);
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const K = e.v.K;
    const lure = (k = 1) => {
      if (!P.luz) return;
      obj.updateMatrixWorld(true);
      const p = new THREE.Vector3(0, -2.7, 0).applyMatrix4(P.luz.obj.matrixWorld);
      ctx.get('isca', () => DBFX.glow([0.5, 1.0, 0.85])).at(p, 0.16, 0.9 * k * (0.8 + 0.2 * Math.sin(t * 5)), t);
      ctx.get('iscaL', () => DBFX.light(0x9fffe0)).at(p, 0.8 * k, 3);
    };
    const bob = (k = 1) => { obj.position.y += 0.08 * k * Math.sin(t * 1.7); r('corpo', 0.06 * k * Math.sin(t * 1.7 + 1), 0, 0); };

    if (mode === 'parado') {
      // parado no ar como peixe no aquário: rabo devagar, peitorais segurando, vira a cara pros lados
      swim(r, t, 0.35, 0.5); bob(1); lure(1);
      obj.rotation.y += 0.5 * loop([[0, 0], [0.3, 0.6], [0.55, 0.6], [0.8, -0.4], [1, 0]], t / 6);
      return;
    }
    if (mode === 'andar') {
      // nada em roda no ar, inclinando pra dentro da curva
      const w = 0.9, ph = t * w, Rc = 1.4;
      obj.position.x += Rc * Math.cos(ph); obj.position.z += Rc * Math.sin(ph);
      obj.rotation.y = Math.PI - ph; r('corpo', 0, 0, 0.25);
      obj.position.y += 0.15 * Math.sin(t * 1.3);
      swim(r, t, 1, 1.4); lure(1);
      return;
    }
    if (mode === 'mordida') {
      const T = t % 3.2;
      const R = ctx.ref; const RZ = -1.9; R.place(0, RZ, 0); R.spin(Math.PI);
      // recua enrolando o rabo (mola), dispara de boca aberta, CRAU, sacode a cabeça presa, solta e volta
      const coil = seq([[0.1, 0], [0.55, 1, 'out'], [0.62, 1], [0.7, -0.6, 'snap'], [0.9, 0]], T);
      const dash = seq([[0.6, 0], [0.78, 1, 'in'], [1.75, 1], [2.3, 0, 'io']], T);
      const tremble = T > 0.3 && T < 0.62 ? Math.sin(t * 70) * 0.03 : 0;
      obj.position.z += 0.35 * seq([[0.1, 0], [0.55, 1, 'out'], [0.62, 1], [0.7, 0]], T) + (RZ + 0.42 - 0) * dash;
      obj.position.x += tremble;
      r('cauda', 0, 0.9 * coil, 0); r('barbatana', 0, -0.8 * coil, 0); r('corpo', 0, -0.25 * coil, 0);
      const open = seq([[0.45, 0], [0.7, 1, 'out'], [0.79, 1], [0.83, 0, 'snap'], [1.75, 0], [1.85, 0.7, 'out'], [2.3, 0.15]], T);
      r('mandibula', 1.1 * open, 0, 0);
      const bite = T > 0.83 && T < 1.75;
      if (bite) {
        // sacudida de piranha: a cabeça presa e o corpo todo chicoteando
        const sh = Math.sin((T - 0.83) * 34) * Math.max(0, 1 - (T - 0.83) / 0.95);
        obj.rotation.y += 0.5 * sh; r('corpo', 0, 0, 0.3 * sh);
        r('cauda', 0, 0.8 * sh, 0); r('barbatana', 0, -0.9 * sh, 0);
        R.lean(-0.12 - 0.08 * sh); R.spin(Math.PI + 0.12 * sh); R.armsOut(0.35);
        ctx.shake(0.45 * Math.max(0, 1 - (T - 0.83) / 0.5));
        ctx.get('sangue', () => DBFX.grains({ n: 36, seed: 21, palette: [[160, 14, 24], [110, 6, 14], [200, 40, 50]], size: [0.02, 0.05], drag: 2, grav: 7, life: 0.6, spread: 0.6, cone: R2 => [(R2() - 0.5) * 2.4, R2() * 1.4, (R2() - 0.2) * 1.4] })).at(new THREE.Vector3(0, 1.1, RZ + 0.18), T - 0.83, 0);
      }
      if (T > 0.82 && T < 0.98) ctx.flash(new THREE.Vector3(0, 1.1, RZ + 0.25), 1 - (T - 0.82) / 0.16);
      if (T > 1.75 && T < 2.3) { const k = (T - 1.75) / 0.55; r('corpo', -0.4 * Math.sin(Math.PI * k), 0, 0); obj.position.y += 0.3 * Math.sin(Math.PI * k); }
      if (T > 1.75) R.lean(-0.1 * Math.max(0, 1 - (T - 1.75) * 2));
      if (!bite && T > 0.62) swim(r, t, T < 1.0 ? 1.4 : 0.6, 1.6);
      if (T < 0.1 || T > 2.3) swim(r, t, 0.4, 0.6);
      lure(1);
      return;
    }
    if (mode === 'cardume') {
      const T = t % 6.4;
      const R = ctx.ref; const RZ = -1.8; R.place(0, RZ, 0); R.spin(Math.PI);
      const C = new THREE.Vector3(0, 0, RZ);
      // cinco peixes rodando em volta do alvo, cada um na sua altura e velocidade; um de cada vez entra mordendo
      const fish = [{ obj, r, v: e.v }];
      const pool = built.find(b => b.m.id === 'peixe');
      for (let i = 0; i < 4; i++) {
        const src = pool.vs[(curV + 1 + i) % pool.vs.length].hi;
        const c = ctx.get('cl' + i + ':' + src.obj.uuid, () => JJFX.posable(src));
        c.reset(); c.visible = true;
        c.position.set(0, 1.5, 0); c.rotation.set(0, 0, 0); c.scale.copy(obj.scale);
        const cs = src.v.size * (0.9 + 0.05 * i); c.children[0].scale.setScalar(cs); c.position.y += 1.5 * (cs - 1);
        JJFX.dissolve(src, 0);
        fish.push({ obj: c, r: c.r, v: src.v });
      }
      const gather = seq([[0, 0], [1.0, 1, 'out']], T), leave = seq([[5.6, 0], [6.4, 1, 'in']], T);
      let hit = 0;
      fish.forEach((f, i) => {
        const sp = 1.7 + 0.25 * i, dir = i % 2 ? 1 : -1, rad = 0.85 + 0.12 * (i % 3), hy = 0.25 * Math.sin(T * 1.3 + i * 1.7) + (i - 2) * 0.12;
        let ang = dir * T * sp + i * 1.256;
        // entrada: cada um sai de um lado longe
        const far = 1 + 2.4 * (1 - gather) + 2.4 * leave;
        // mordida: um por vez (1,4 / 2,2 / 3,0 / 3,8 / 4,6)
        const t0 = 1.4 + i * 0.8, b = seq([[t0, 0], [t0 + 0.2, 1, 'in'], [t0 + 0.55, 1], [t0 + 0.8, 0, 'out']], T);
        const rr = rad * far * (1 - 0.72 * b);
        const x = Math.cos(ang) * rr, z = Math.sin(ang) * rr;
        f.obj.position.x += C.x + x; f.obj.position.z += C.z + z; f.obj.position.y += hy * (1 - b) - 0.05;
        // olha pra onde nada (tangente), e pro alvo quando morde
        const tang = Math.PI - ang - (dir > 0 ? 0 : Math.PI);
        let toC = Math.atan2(x, z); toC = tang + Math.atan2(Math.sin(toC - tang), Math.cos(toC - tang));
        f.obj.rotation.y = tang * (1 - b) + toC * b + (b > 0.5 && T < t0 + 0.55 ? 0.4 * Math.sin(T * 40) : 0);
        f.r('corpo', 0, 0, 0.3 * dir * (1 - b));
        swim(f.r, T + i, 1.2 - 0.6 * b, 1.6, i * 1.3);
        f.r('mandibula', 1.0 * seq([[t0, 0], [t0 + 0.15, 1], [t0 + 0.22, 0, 'snap'], [t0 + 0.8, 0]], T), 0, 0);
        if (T > t0 + 0.2 && T < t0 + 0.55) hit = Math.max(hit, 1 - (T - t0 - 0.2) / 0.35);
        if (T > t0 + 0.2 && T < t0 + 0.75) ctx.get('sg' + i, () => DBFX.grains({ n: 18, seed: 30 + i, palette: [[160, 14, 24], [110, 6, 14]], size: [0.02, 0.045], drag: 2, grav: 7, life: 0.5, cone: R2 => [(R2() - 0.5) * 2, R2() * 1.2, (R2() - 0.5) * 2] })).at(new THREE.Vector3(C.x + x * 0.4, 1.1 + hy * 0.3, C.z + z * 0.4), T - t0 - 0.2, 0);
      });
      R.armsOut(0.3 + 0.5 * hit); R.lean(-0.15 * hit); R.spin(Math.PI + 0.6 * Math.sin(T * 2.2) * Math.min(1, T));
      ctx.shake(0.35 * hit);
      if (hit > 0.8) ctx.flash(new THREE.Vector3(0, 1.1, RZ), hit - 0.8);
      lure(1);
      return;
    }
    if (mode === 'morte') {
      // cai do "ar" no chão de lado, se debate duas vezes e vira água: poça e respingo
      const T = t % 3.6;
      const R = ctx.ref; R.place(0, -1.6, 0); R.spin(Math.PI);
      R.punch(seq([[0.05, 0], [0.15, 1, 'snap'], [0.45, 1], [0.7, 0]], T));
      const fall = seq([[0.15, 0], [0.55, 1, 'in']], T);
      const cy = 1.1 * s;
      obj.position.y -= (cy - 0.14) * fall;
      const side = 1.45 * seq([[0.15, 0], [0.6, 1, 'out']], T);
      // pulos de peixe fora d'água
      for (const [t0, hgt] of [[0.75, 0.22], [1.25, 0.12], [1.62, 0.05]]) {
        const k = FX.clamp01((T - t0) / 0.32);
        if (k > 0 && k < 1) { obj.position.y += hgt * Math.sin(Math.PI * k); r('corpo', 0, 0, -0.25 * Math.sin(Math.PI * k)); r('cauda', 0, 0.8 * Math.sin(k * 9), 0); r('barbatana', 0, -0.8 * Math.sin(k * 9), 0); }
      }
      r('corpo', 0, 0, side);
      // a barbatana não escora o peixe deitado (teste R5)
      JJFX.floorLimbs(obj, P, ['corpo'], ['peitD', 'peitE'], fall);
      r('mandibula', 0.6 * fall * (0.6 + 0.4 * Math.sin(T * 6)), 0, 0);
      const dis = FX.clamp01((T - 1.95) / 0.7);
      JJFX.dissolve(e, dis, 0, 0.45);
      const ground = new THREE.Vector3(0, 0.02, 0);
      if (T > 0.55) ctx.get('poca', () => JJFX.wet()).at(ground, 0.55, FX.clamp01((T - 0.55) / 0.4) * 0.7 + 0.3 * dis, 1 - FX.clamp01((T - 3.0) / 0.6), T, 3);
      if (T > 1.95) ctx.get('agua', () => JJFX.drops(11, 70, { life: 0.8 })).at(new THREE.Vector3(0, 0.15, 0), T - 1.95, 0.02);
      if (T > 0.55 && T < 0.75) ctx.get('splash', () => JJFX.drops(12, 30, { size: [0.02, 0.05], cone: R2 => { const a2 = R2() * 6.28; return [Math.cos(a2) * 1.1, 1 + R2() * 1.5, Math.sin(a2) * 1.1]; } })).at(new THREE.Vector3(0, 0.1, 0), T - 0.55, 0.02);
      lure(1 - fall * 0.7 - dis);
    }
  }

  window.PEIXE = { swim };                                                                      // o Dagon Embrião chama o cardume com o mesmo nado
  window.MOBS = window.MOBS || [];
  const fixed = [variant(4, 0), variant(17, 1), variant(29, 2)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'peixe', nome: 'Peixe-Shikigami', bioma: 'Mar de Dagon', tier: 'Grau 4 · tier 10',
    variants: fixed.map(v => ({ nome: v.K.nome, v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado (' + v.K.nome + ')', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Nadar no ar'], ['mordida', 'Mordida'], ['cardume', 'Cardume'], ['morte', 'Virar água']],
    cam: 3.2, lookY: 1.05, lookZ: { mordida: -1.0, cardume: -1.8, morte: 0 }, camFar: { andar: 5.6, mordida: 4.4, cardume: 6.4 }, camYaw: { mordida: -1.2, cardume: -0.7 },
    info: {
      'Tamanho': 'Meio bloco a 0,7 bloco de comprimento; nada no ar a 1 bloco do chão',
      'Vida': '6 (morre com um golpe de espada)',
      'Jeito': 'Andam em cardume de 4 a 8 e nadam no ar como se fosse água. Não precisam de água, mas fora do Mar de Dagon ficam lentos.',
      'Mordida': 'Recua, enrola o rabo e dispara: crava os dentes e sacode a cabeça (3 mordidas de dano baixo + sangramento curto). A piranha morde mais forte; o atum vem de mais longe.',
      'Cardume': 'Com 3 ou mais perto, rodam em volta de você e entram mordendo um de cada vez, de lados diferentes. Girar batendo pega vários.',
      'Peixe-Lanterna': 'A luz da testa deixa a noite clara em volta dele e atrai os outros peixes (e você).',
      'Fraqueza': 'Golpe de área; fogo; no chão (depois de derrubado) não consegue voltar a nadar por 2 s.',
      'Solta': 'Escama Amaldiçoada; às vezes Peixe Cru Amaldiçoado (comida: cura pouco e dá Respiração na Água por 30 s).',
      'Variações': 'Piranha, atum ou peixe-lanterna; cicatriz de mordida de lado.',
    },
  });
})();
