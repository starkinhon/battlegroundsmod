// Marionetista de Suna + Marionete (Deserto do Vento). Médio, tier 7. Sempre em par.
// O marionetista fica atrás e mexe a marionete com fios de chakra; matar ele derruba a marionete.
// Marionete: Lâminas Envenenadas (abre os braços das costas e os antebraços soltam lâminas: veneno 4 s) e
// Jaula (abre o peito, te puxa pra dentro e tranca por 2 s). Marionetista: Agulhas (rajada da manga, quando você chega perto).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const N = window.NINJA, SU = window.SUNA;

  // ---------- marionete ----------
  function puppetPainters(v) {
    const S = v.seed;
    const grain = (q, s) => fr(q.ay * 0.7 + q.ax * 0.08 + 0.9 * n3(q.ax * 0.2, q.ay * 0.1, q.az * 0.2, s + S));
    const wood = s => q => {
      let c = cel(v.wood, n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S), 0.8, 1.12);
      if (grain(q, s) < 0.18) c = mul(c, 0.84);
      if (q.f === 'bottom') c = mul(c, 0.7);
      return c;
    };
    // laca: tinta por cima da madeira, gasta nas quinas (onde a mão do marionetista e as lutas pegam)
    const lacq = s => q => {
      const base = wood(s)(q);
      if (!v.lacq) return base;
      const edge = Math.min(q.nx, 1 - q.nx, q.ny, 1 - q.ny, q.nz, 1 - q.nz);
      const worn = n3(q.ax * 0.9, q.ay * 0.9, q.az * 0.9, s + 7 + S) * 0.5 + (edge < 0.1 ? 0.45 : 0);
      if (worn > 0.6) return base;
      let c = cel(v.lacq, n3(q.ax * 0.4, q.ay * 0.4, q.az * 0.4, s + 9), 0.86, 1.08);
      if (q.f === 'bottom') c = mul(c, 0.72);
      return c;
    };
    const joint = q => cel([60, 56, 58], n3(q.ax, q.ay, q.az, 90), 0.78, 1.2);
    const P = {};
    P.wood = wood(1); P.limb = lacq(2); P.chest = lacq(3);
    P.joint = joint;
    P.door = q => {
      let c = lacq(4)(q);
      if (q.f === 'north') {
        // marcas pintadas nas portas do peito: três faixas que viram uma boca fechada
        const y = q.ny;
        if (v.lacq && (Math.abs(y - 0.3) < 0.04 || Math.abs(y - 0.5) < 0.04 || Math.abs(y - 0.7) < 0.04)) c = v.paint;
        if (!v.lacq && (Math.abs(y - 0.15) < 0.06 || Math.abs(y - 0.85) < 0.06)) c = joint(q);   // cintas de ferro
        if (Math.abs(q.nx - 0.5) < 0.06 && Math.abs(y - 0.5) < 0.06) c = [40, 36, 38];        // trinco
      }
      return c;
    };
    P.inside = q => {
      // dentro do peito: escuro, com as pontas das estacas brilhando
      let c = [26, 20, 18];
      if (fr(q.ax * 0.5) < 0.18 && q.f === 'north') c = [150, 150, 160];
      return c;
    };
    P.head = q => {
      let c = lacq(5)(q);
      if (q.f !== 'north') return c;
      const x = q.nx, y = q.ny;
      // máscara: olhos fundos, um terceiro olho fechado na testa, faixa pintada descendo de cada olho
      if (Math.abs(Math.abs(x - 0.5) - 0.2) < 0.08 && Math.abs(y - 0.42) < 0.07) c = [14, 10, 10];
      if (Math.abs(Math.abs(x - 0.5) - 0.2) < 0.03 && Math.abs(y - 0.42) < 0.025) c = [210, 190, 120];
      if (Math.abs(x - 0.5) < 0.1 && Math.abs(y - 0.2) < 0.025) c = [30, 24, 24];
      if (Math.abs(Math.abs(x - 0.5) - 0.2) < 0.03 && y > 0.5 && y < 0.8) c = v.paint;
      return c;
    };
    P.jaw = q => {
      let c = lacq(6)(q);
      if (q.f === 'north' && q.ny < 0.35) { const k = fr(q.nx * 6); c = k < 0.5 ? [220, 210, 186] : [30, 24, 22]; }  // dentes
      return c;
    };
    P.cloth = q => {
      if (q.ny > 0.7 + 0.3 * n3(Math.floor(q.px * 0.8) * 1.7, Math.floor(q.pz * 0.8), 0, 91 + S)) return null;  // barra rasgada
      let c = cel(v.cloth, n3(q.ax * 0.3, q.ay * 0.5, q.az * 0.3, 92 + S), 0.76, 1.12);
      if (fr(q.ax * 0.35 + q.az * 0.35) < 0.15) c = mul(c, 0.82);
      return c;
    };
    // capuz: aberto na frente, deixa a máscara aparecer
    P.hood = q => (q.f === 'north' && Math.abs(q.nx - 0.5) < 0.4 && q.ny > 0.18 ? null : q.f === 'bottom' ? null : P.cloth({ ...q, ny: q.ny * 0.5 }));
    P.blade = q => {
      let c = mix([150, 156, 168], [220, 226, 236], n3(q.ax * 2, q.ay * 0.5, q.az * 2, 93));
      if (q.ny > 0.55) c = mix(c, [120, 60, 160], (q.ny - 0.55) * 1.8);                            // veneno escorrendo pra ponta
      if (q.f === 'east' || q.f === 'west') c = mul(c, 0.8);
      return c;
    };
    return P;
  }
  function puppetDef(v, low) {
    const P = puppetPainters(v);
    const arm = (s, back) => {
      const nm = back ? (s < 0 ? 'costasD' : 'costasE') : (s < 0 ? 'bracoD' : 'bracoE');
      const tag = back ? 'C' : '';
      const len = back ? 6 : 7;
      return {
        name: nm, pivot: back ? [s * 3, -11, 3.4] : [s * 7, -12, 0], rot: back ? [0.5, 0, s * -0.15] : [0, 0, 0],
        boxes: [{ x: -1, y: -1, z: -1, w: 2, h: len, d: 2, p: P.limb }].concat(low ? [] : [{ x: -1.5, y: -1.5, z: -1.5, w: 3, h: 2.4, d: 3, p: P.joint }]),
        kids: [{
          name: 'ante' + (s < 0 ? 'D' : 'E') + tag, pivot: [0, len, 0], rot: back ? [-1.6, 0, 0] : [0, 0, 0],
          boxes: [{ x: -1, y: 0, z: -1, w: 2, h: len, d: 2, p: P.limb }].concat(low ? [] : [{ x: -1.3, y: -1.1, z: -1.3, w: 2.6, h: 2.2, d: 2.6, p: P.joint }]),
          kids: [
            { name: 'mao' + (s < 0 ? 'D' : 'E') + tag, pivot: [0, len, 0], boxes: [{ x: -1.2, y: 0, z: -0.8, w: 2.4, h: 3.2, d: 1.6, p: P.wood }] },
            // a lâmina dobrada fica deitada ao longo do antebraço; abre girando 180 graus no pulso
            { name: 'lamina' + (s < 0 ? 'D' : 'E') + tag, pivot: [0, len - 0.5, -1.2], rot: [Math.PI, 0, 0], boxes: [{ x: -0.3, y: 0, z: -0.9, w: 0.6, h: back ? 8 : 9, d: 1.8, p: P.blade }] },
          ],
        }],
      };
    };
    const leg = s => ({
      name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * 2.6, 0, 0], boxes: [{ x: -1.1, y: 0, z: -1.1, w: 2.2, h: 8, d: 2.2, p: P.limb }],
      kids: [{
        name: s < 0 ? 'canelaD' : 'canelaE', pivot: [0, 8, 0],
        boxes: [{ x: -1, y: 0, z: -1, w: 2, h: 8, d: 2, p: P.limb }].concat(low ? [] : [{ x: -1.4, y: -1.2, z: -1.4, w: 2.8, h: 2.4, d: 2.8, p: P.joint }]),
        kids: [{ name: s < 0 ? 'peD' : 'peE', pivot: [0, 8, 0], boxes: [{ x: -1.3, y: 0, z: -2.8, w: 2.6, h: 1.4, d: 4.2, p: P.wood }] }],
      }],
    });
    const door = s => ({
      name: s < 0 ? 'portaD' : 'portaE', pivot: [s * 5.6, 0, -3.7],
      boxes: [{ x: s < 0 ? 0 : -5.6, y: -12.6, z: -0.8, w: 5.6, h: 11.2, d: 0.8, p: P.door }],
    });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'quadril', pivot: [0, 6.6, 0], boxes: [{ x: -4, y: -1, z: -2.4, w: 8, h: 3, d: 4.8, p: P.wood }],
        kids: [leg(-1), leg(1), {
          name: 'tronco', pivot: [0, -1, 0],
          boxes: [
            { x: -5.6, y: -13, z: -3.4, w: 11.2, h: 12, d: 7, p: P.chest },
            { x: -4.8, y: -12.2, z: -3.2, w: 9.6, h: 10.4, d: 0.4, p: P.inside },
            { x: -6.8, y: -14.4, z: -4.2, w: 13.6, h: 4.2, d: 8.8, p: P.cloth },
          ],
          kids: [door(-1), door(1), arm(-1), arm(1)].concat(low ? [] : [arm(-1, true), arm(1, true)]).concat([{
            name: 'cabeca', pivot: [0, -14.2, -0.4], rot: [0.12, 0, 0.1],
            boxes: [{ x: -3.2, y: -7, z: -3.4, w: 6.4, h: 6.4, d: 6.4, p: P.head }, { x: -3.8, y: -8, z: -3.8, w: 7.6, h: 6, d: 7.8, p: P.hood }],
            kids: low ? [] : [{ name: 'mandibula', pivot: [0, -1.2, -1], boxes: [{ x: -2.6, y: 0, z: -2.5, w: 5.2, h: 1.8, d: 5, p: P.jaw }] }],
          }]),
        }],
      }],
    };
  }

  // ---------- marionetista ----------
  function pmVariant(seed, pv) {
    const v = SU.variant(seed);
    v.hood = true; v.mask = false;
    v.facePaint = pv.paint;
    return v;
  }
  function pmPainters(v) {
    const P = SU.painters(v);
    const base = P.head;
    P.head = q => {
      let c = base(q);
      if (q.f === 'north') {
        // pintura de guerra do marionetista: um traço de cada olho até o queixo e um no nariz
        const x = Math.floor(q.px), y = Math.floor(q.py);
        if (y >= 3 && y <= 5 && (x === 1 || x === 5)) c = v.facePaint;
        if (y === 4 && x === 3) c = v.facePaint;
      }
      return c;
    };
    return P;
  }
  const pmDef = v => N.rigDef(pmPainters(v), v, { noTails: true });
  const pmLow = v => N.rigLow(pmPainters(v), v);
  function sideModel(def) {
    const model = ENG.build(def), tex = ENG.paint(model, model.W, model.H), obj = ENG.toThree(model, tex);
    obj.model = model;
    return obj;
  }

  // ---------- fios de chakra: cada fio com sua curva, brilho e tremor ----------
  function threads(n) {
    const g = new THREE.Group(), list = [];
    for (let i = 0; i < n; i++) {
      const geo = new THREE.BufferGeometry(), pos = new Float32Array(13 * 3);
      geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
      const m = new THREE.Line(geo, new THREE.LineBasicMaterial({ color: 0x4fb8ff, transparent: true, depthWrite: false }));
      m.frustumCulled = false;
      g.add(m); list.push({ m, pos, ph: i * 1.7 });
    }
    g.at = (pairs, t, k) => {
      g.visible = true;
      list.forEach((L, i) => {
        const pr = pairs[i];
        L.m.visible = !!pr && k > 0.02;
        if (!L.m.visible) return;
        const [a, b] = pr;
        for (let j = 0; j <= 12; j++) {
          const f = j / 12, sag = Math.sin(Math.PI * f) * (0.06 + 0.03 * Math.sin(t * 7 + L.ph));
          L.pos.set([a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f - sag, a.z + (b.z - a.z) * f], j * 3);
        }
        L.m.geometry.attributes.position.needsUpdate = true;
        L.m.material.opacity = k * (0.55 + 0.45 * Math.max(0, Math.sin(t * 13 + L.ph * 3)));
      });
    };
    return g;
  }
  // agulhas: cada uma com desvio, atraso e giro próprios
  function needles(n, seed) {
    const R = FX.rnd(seed), g = new THREE.Group(), list = [];
    for (let i = 0; i < n; i++) {
      const m = new THREE.Mesh(new THREE.BoxGeometry(0.018, 0.018, 0.32), new THREE.MeshBasicMaterial({ color: 0xd8dce6 }));
      g.add(m); list.push({ m, dx: (R() - 0.5) * 0.5, dy: (R() - 0.5) * 0.45, lag: R() * 0.25 });
    }
    g.at = (a, b, age) => {
      g.visible = true;
      for (const p of list) {
        const f = (age - p.lag) / 0.22;
        p.m.visible = f > 0 && f < 1.6;
        if (!p.m.visible) continue;
        const ff = Math.min(1, f);
        p.m.position.set(a.x + (b.x + p.dx - a.x) * ff, a.y + (b.y + p.dy - a.y) * ff, a.z + (b.z - a.z) * ff);
        p.m.lookAt(b.x + p.dx, b.y + p.dy, b.z - 1);
      }
    };
    return g;
  }

  const PM_POS = new THREE.Vector3(0.9, 0, 3.4);
  function anim(P, t, mode, a, obj, ent, ctx) {
    const r = (k, x, y, z) => { if (!P[k]) return; const o = P[k].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const low = !P.mandibula;
    // o marionetista, atrás
    const vi = ent.meta.idx;
    const pm = ctx.get('marionetista' + vi + (low ? 'b' : 'a'), () => sideModel(low ? ent.meta.pmLow : ent.meta.pmDef));
    pm.visible = true;
    ENG.reset(pm.model);
    pm.position.set(PM_POS.x, 1.5, PM_POS.z); pm.rotation.set(0, 0, 0);
    const Q = pm.model.parts;
    const q = (k, x, y, z) => { if (!Q[k]) return; const o = Q[k].obj.rotation; o.x += x; o.y += y; o.z += z; };
    // pose de comando: os dois braços pra frente, dedos abertos, cada mão puxando uma parte
    const command = (k, jerk) => {
      N.guard(q, Q);
      q('bracoD', -1.2 * k - 0.3 * jerk, 0.25 * k, 0); q('bracoE', -1.2 * k - 0.3 * jerk, -0.25 * k, 0);
      if (Q.anteD) { q('anteD', 0.25 * k, 0, 0); q('anteE', 0.25 * k, 0, 0); }
      if (Q.maoD) { q('maoD', -0.6 * k + 0.2 * jerk, 0, 0); q('maoE', -0.6 * k + 0.2 * jerk, 0, 0); }
      q('tronco', 0.05 * jerk, 0, 0);
    };
    // pose base da marionete: pendurada nos fios, o peso nas pontas dos pés, a cabeça caída
    const hang = () => {
      P.quadril.obj.position.y += -0.6;
      r('cabeca', 0.25, 0, 0.15);
      r('bracoD', 0.1, 0, 0.18); r('bracoE', 0.1, 0, -0.18);
      r('anteD', -0.3, 0, 0); r('anteE', -0.3, 0, 0);
      r('pernaD', 0.05, 0, 0.05); r('pernaE', -0.08, 0, -0.04); r('canelaD', 0.1, 0, 0); r('canelaE', 0.18, 0, 0);
    };
    const doThreads = (t, k) => {
      obj.updateMatrixWorld(true); pm.updateMatrixWorld(true);
      const W = (part, x, y, z) => part ? part.obj.localToWorld(new THREE.Vector3(x, y, z)) : null;
      const hD = W(Q.maoD || Q.bracoD, 0, Q.maoD ? 2 : 10, 0), hE = W(Q.maoE || Q.bracoE, 0, Q.maoE ? 2 : 10, 0);
      const tg = [W(P.cabeca, 0, -6, 0), W(P.maoD, 0, 1.5, 0), W(P.maoE, 0, 1.5, 0), W(P.tronco, -3, -12, 0), W(P.tronco, 3, -12, 0),
        W(P.canelaD, 0, 2, 0), W(P.canelaE, 0, 2, 0), W(P.anteDC, 0, 3, 0), W(P.anteEC, 0, 3, 0)];
      const pairs = tg.map((p, i) => (p ? [i % 2 ? hD : hE, p] : null));
      ctx.get('fios', () => threads(9)).at(pairs, t, k);
    };
    if (mode === 'parado') {
      hang();
      // um tranco seco de vez em quando, como se o dedo dele tivesse puxado sem querer
      const tw = loop([[0, 0], [0.7, 0], [0.74, 1], [0.8, 0.2], [0.9, 0]], t * 0.4);
      r('cabeca', -0.25 * tw, 0.2 * tw, 0); r('bracoD', -0.3 * tw, 0, 0);
      command(1, tw);
      doThreads(t, 1);
      return;
    }
    if (mode === 'andar') {
      // a marionete não anda: é carregada. passos duros, joelho alto, pé arrastando no fim
      const ph = t * 0.9;
      hang();
      r('pernaD', loop([[0, 0.3], [0.2, -0.7], [0.35, -0.6], [0.5, 0.25], [1, 0.3]], ph), 0, 0);
      r('canelaD', loop([[0, 0], [0.2, 0.9], [0.4, 0.1], [0.6, 0]], ph), 0, 0);
      r('pernaE', loop([[0, 0.3], [0.2, -0.7], [0.35, -0.6], [0.5, 0.25], [1, 0.3]], ph + 0.5), 0, 0);
      r('canelaE', loop([[0, 0], [0.2, 0.9], [0.4, 0.1], [0.6, 0]], ph + 0.5), 0, 0);
      P.quadril.obj.position.y += loop([[0, 0], [0.2, -0.8], [0.5, 0], [0.7, -0.8]], ph);
      command(1, 0);
      for (const [s, off] of [['D', 0], ['E', 0.5]]) { q('perna' + s, loop(N.WALK_T, ph + off), 0, 0); q('canela' + s, loop(N.WALK_K, ph + off), 0, 0); }
      Q.quadril.obj.position.y += loop(N.WALK_B, ph);
      doThreads(t, 1);
      return;
    }
    const R = ctx.ref;
    if (mode === 'laminas') {
      const T = t % 3.6;
      R.place(0, -2.6, 0);
      // 1) os braços das costas sobem por cima dos ombros  2) as lâminas abrem num estalo  3) bote em X
      const unfold = seq([[0.15, 0], [0.6, 1, 'out'], [2.9, 1], [3.4, 0, 'io']], T);
      const flick = seq([[0.6, 0], [0.68, 1, 'snap'], [2.9, 1], [3.3, 0]], T);
      const lunge = seq([[0.95, 0], [1.08, 1, 'snap'], [1.6, 1], [2.2, 0, 'io']], T);
      const cut = seq([[1.0, 0], [1.1, 1, 'snap']], T);
      hang();
      r('cabeca', -0.3 * unfold, 0, -0.15 * unfold); r('mandibula', 0.5 * flick * (1 - lunge * 0.5), 0, 0);
      r('costasD', -2.2 * unfold, 0, 0.5 * unfold); r('costasE', -2.2 * unfold, 0, -0.5 * unfold);
      r('anteDC', 1.2 * unfold - 0.8 * cut, 0, 0); r('anteEC', 1.2 * unfold - 0.8 * cut, 0, 0);
      r('bracoD', -1.6 * unfold + 0.9 * cut, 0, 0.9 * unfold - 1.4 * cut); r('bracoE', -1.6 * unfold + 0.9 * cut, 0, -0.9 * unfold + 1.4 * cut);
      for (const k of ['laminaD', 'laminaE', 'laminaDC', 'laminaEC']) r(k, -Math.PI * flick, 0, 0);
      r('tronco', 0.3 * lunge, 0, 0);
      obj.position.z -= 1.4 * lunge; obj.position.y += 0.15 * Math.sin(Math.PI * FX.clamp01((T - 0.95) / 0.2));
      command(1, cut - 0.6 * seq([[0.9, 0], [0.98, 1, 'snap'], [1.3, 0]], T));
      doThreads(t, 1);
      if (T > 1.06) {
        const h = T - 1.06;
        ctx.slash(new THREE.Vector3(0.2, 1.4, -2.45), h / 0.32);
        ctx.slash(new THREE.Vector3(-0.2, 1.0, -2.5), (h - 0.08) / 0.32);
        const pois = ctx.get('veneno-mar', FX.volume);
        pois.set(new THREE.Vector3(0, 1.2, -2.6), 0.5, h / 1.4, { seed: 2.9, dens: 5, rise: 0.4, freq: 2, smoke: [0.45, 0.25, 0.6] });
        R.lean(-0.35 * Math.sin(Math.PI * FX.clamp01(h / 0.6)));
        ctx.poison(seq([[1.1, 0], [1.3, 1], [2.8, 1], [3.2, 0]], T) * 0.9);
        if (h < 0.2) ctx.shake(1 - h / 0.2);
      }
      return;
    }
    if (mode === 'jaula') {
      const T = t % 4.4;
      // abre o peito, avança, os braços puxam o alvo pra dentro, tranca 2 s e cospe
      const open = seq([[0.15, 0], [0.55, 1, 'out'], [1.25, 1], [1.4, 0, 'snap'], [3.4, 0], [3.55, 1, 'snap'], [4.1, 1], [4.4, 0]], T);
      const go = seq([[0.5, 0], [0.8, 1, 'snap'], [3.6, 1], [4.3, 0, 'io']], T);
      const pull = seq([[0.8, 0], [1.3, 1, 'in']], T);
      const shut = T > 1.38 && T < 3.5;
      hang();
      r('portaD', 0, -1.9 * open, 0); r('portaE', 0, 1.9 * open, 0);
      r('bracoD', -1.4 * go * (1 - pull) - 0.2 * pull, 0, 0.6 * go * (1 - pull)); r('bracoE', -1.4 * go * (1 - pull) - 0.2 * pull, 0, -0.6 * go * (1 - pull));
      r('anteD', -0.9 * pull, 0, 0); r('anteE', -0.9 * pull, 0, 0);
      r('cabeca', -0.2 * go, 0, 0); r('mandibula', 0.4 * open, 0, 0);
      obj.position.z -= 1.2 * go;
      command(1, pull * (1 - FX.clamp01((T - 1.3) / 0.3)));
      doThreads(t, 1);
      if (!shut && T < 1.38) {
        const k = seq([[0.8, 0], [1.36, 1, 'in']], T);
        R.place(0, -2.7 + 1.1 * k, 0); R.lean(0.5 * k);
      } else if (shut) {
        R.place(0, 99, 0);
        // preso no escuro: o veil escurece e uma frestinha de luz pisca entre as portas
        ctx.dark(seq([[1.38, 0], [1.5, 1], [3.3, 1], [3.5, 0]], T));
        if (T < 1.55) ctx.shake(1 - (T - 1.38) / 0.17);
      } else {
        const k = seq([[3.5, 0], [4.0, 1, 'out']], T);
        R.place(0, -1.6 - 1.4 * k, 0.4 * Math.sin(Math.PI * k)); R.lean(-0.8 * Math.sin(Math.PI * FX.clamp01((T - 3.5) / 0.8)));
      }
      return;
    }
    if (mode === 'agulhas') {
      const T = t % 3.0;
      // você chegou perto do marionetista: ele vira a manga e cospe agulhas
      const ref = new THREE.Vector3(PM_POS.x, 0, PM_POS.z - 2.4);
      R.place(ref.x, ref.z, 0); R.spin(Math.PI);
      hang();
      obj.position.x -= 1.3;
      r('cabeca', 0, -0.4, 0);
      command(0.5, 0);
      const aim = seq([[0.1, 0], [0.45, 1, 'out'], [1.6, 1], [2.0, 0, 'io']], T);
      q('bracoD', -0.9 * aim, 0, 0); if (Q.anteD) q('anteD', -0.6 * aim, 0, 0);
      q('tronco', 0, 0.35 * aim, 0); q('cabeca', 0, -0.3 * aim, 0);
      q('pernaD', -0.3 * aim, 0, 0); q('pernaE', 0.25 * aim, 0, 0);
      doThreads(t, 1 - 0.5 * aim);
      if (T > 0.5) {
        pm.updateMatrixWorld(true);
        const sleeve = (Q.anteD || Q.bracoD).obj.localToWorld(new THREE.Vector3(0, Q.anteD ? 3 : 8, -1));
        ctx.get('agulhas', () => needles(14, 31)).at(sleeve, new THREE.Vector3(ref.x, 1.25, ref.z), T - 0.5);
        if (T > 0.68) R.lean(0.2 * Math.sin(Math.PI * FX.clamp01((T - 0.68) / 0.5)));
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const SK = [
    { nome: 'Laca', seed: 1, wood: [150, 104, 64], lacq: [226, 218, 200], paint: [168, 34, 40], cloth: [50, 44, 52] },
    { nome: 'Madeira crua', seed: 2, wood: [128, 84, 50], lacq: null, paint: [70, 40, 30], cloth: [148, 118, 80] },
  ];
  const vars = SK.map((v, i) => {
    const pv = pmVariant(40 + i * 11, { paint: i ? [72, 54, 120] : [150, 40, 60] });
    return { nome: v.nome, idx: i, v, def: puppetDef(v, false), low: puppetDef(v, true), pmDef: pmDef(pv), pmLow: pmLow(pv) };
  });
  window.MOBS.push({
    id: 'marionetista', nome: 'Marionetista + Marionete', bioma: 'Deserto do Vento', tier: 'Médio · tier 7',
    variants: vars,
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['laminas', 'Lâminas'], ['jaula', 'Jaula'], ['agulhas', 'Agulhas']],
    cam: 7, lookY: 1.3, lookZ: { parado: 1.4, andar: 1.4, laminas: -0.8, jaula: -1, agulhas: 2.2 }, camFar: { laminas: 8.5, jaula: 8, agulhas: 6.5 },
    camYaw: { parado: -0.75, laminas: -1.25, jaula: -1.2, agulhas: -0.9 }, refX: 2.6,
    info: {
      'Tamanho': 'Marionete 2,2 blocos; marionetista 1,85 bloco, sempre 3 a 4 blocos atrás dela',
      'Vida': 'Marionete 100, marionetista 90 (tier 7). Bater na marionete só atrasa; matar o marionetista derruba ela na hora. Um golpe forte nos fios deixa ela parada 3 s.',
      'Jeito': 'Hostil, aparece na tempestade de areia. O marionetista se esconde atrás das rochas e das ossadas; os fios azuis entregam onde ele está.',
      'Lâminas': 'Os braços das costas sobem por cima dos ombros (0,45 s de aviso) e quatro lâminas abrem num estalo; bote em X de 2 blocos: corte e veneno por 4 s.',
      'Jaula': 'Abre o peito (0,4 s de aviso), avança e te puxa pra dentro: 2 s preso no escuro, depois te cospe. Bater nos fios durante a puxada solta você.',
      'Agulhas': 'Do marionetista, quando você chega a 3 blocos dele: vira a manga e solta uma rajada de agulhas (dano pequeno, empurra de leve). Enquanto mira, a marionete fica mole.',
      'Solta': 'Fio de chakra, peça de marionete; raro: pergaminho de marionete.',
      'Variações': 'Marionete de laca branca com faixas vermelhas ou de madeira crua com cintas de ferro; o marionetista tem capuz e pintura de guerra.',
    },
  });
})();
