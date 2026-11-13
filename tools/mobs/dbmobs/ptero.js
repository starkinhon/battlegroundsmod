// Pterodáctilo, o dino que voa (céu de Paozu, Terras do Dragão e Deserto). Como o do desenho: bicão comprido,
// crista pra trás, olho bravo, asa de pele com nervuras. Fica rodando no céu e desce pra pegar.
// Golpes: Mergulho (fecha as asas, mergulha, agarra o alvo com os pés, sobe e solta lá de cima) e Ventania (para no
// ar e bate as asas com força: um vendaval de areia empurra o alvo pra trás).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const V3 = (x, y, z) => new THREE.Vector3(x, y, z);

  const SKINS = [[140, 96, 150], [160, 110, 70], [96, 120, 150], [170, 80, 70], [120, 130, 90]];
  const WINGS = [[200, 150, 170], [214, 170, 120], [170, 190, 210], [220, 140, 110], [190, 196, 150]];
  function variant(seed) {
    const R = FX.rnd(seed);
    const i = Math.floor(R() * SKINS.length);
    return { seed, skin: SKINS[i].map(c => c * (0.92 + R() * 0.12)), wing: WINGS[i], crest: R() < 0.5 ? [220, 70, 50] : [240, 190, 60], torn: R() < 0.3, big: R() < 0.3 };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.84, hi = 1.1) => cel(base, 0.62 - 0.38 * q.ny + 0.12 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0), lo, hi);
    const belly = mix(v.skin, [240, 222, 190], 0.6);
    const hide = (q, s = 1) => shade(q.f === 'bottom' || (q.f !== 'top' && q.ny > 0.65) ? belly : v.skin, q, s);
    const P = { hide };
    P.head = q => {
      let c = hide(q, 2);
      if (q.f === 'west' || q.f === 'east') {
        const x = q.pz, y = q.py, ex = 2.4, ey = 1.3;
        if (Math.hypot((x - ex) / 1.1, y - ey) < 0.9) { c = [246, 240, 210]; if (Math.hypot(x - (ex - 0.35), y - ey) < 0.45) c = [16, 14, 12]; }
        if (y < ey - 0.6 && y > ey - 1.2 && Math.abs(x - ex) < 1.4) c = mul(v.skin, 0.55);
      }
      return c;
    };
    P.beak = q => { let c = cel([228, 196, 110], 0.5 + 0.4 * (1 - q.ny) + (q.f === 'top' ? 0.2 : 0), 0.82, 1.12); if (q.nz < 0.12) c = mul(c, 0.8); return c; };
    P.crest = q => {
      // crista em lâmina: a borda de trás é curva e afina na ponta
      const k = q.pz / 7, top = 5.5 * (1 - k * k) + 0.4;
      if ((5.6 - q.py) > top) return null;
      if (q.f === 'top' || q.f === 'bottom') return null;
      let c = shade(v.crest, q, 3);
      if (fr(q.pz * 0.5) < 0.18) c = mul(c, 0.85);
      return c;
    };
    // pele da asa: borda de trás em arcos entre os dedos, nervuras que saem do braço, rasgo em algumas
    P.wing = (len, depth, inner, D) => q => {
      if (q.f !== 'top' && q.f !== 'bottom') return null;
      const x = D ? 1 - q.px / len : q.px / len;
            const edge = depth * (inner ? 1 - 0.15 * x : (1 - x) * 0.95 + 0.05) - 0.9 * Math.abs(Math.sin(x * Math.PI * (inner ? 2 : 1.5)));
      if (q.pz > edge) return null;
      if (v.torn && !inner && x > 0.45 && x < 0.55 && q.pz > edge * 0.5) return null;
      let c = cel(v.wing, 0.62 + (q.f === 'top' ? 0.2 : -0.15) + 0.08 * (n3(q.ax * 0.4, 0, q.az * 0.4, 5 + S) - 0.5), 0.84, 1.08);
      const vein = Math.abs(fr(q.pz / Math.max(1, edge) * 3 + x * 0.6) - 0.5);
      if (vein < 0.04) c = mul(c, 0.82);
      if (q.pz > edge - 0.6) c = mul(c, 0.88);
      if (q.pz < 0.6) c = shade(v.skin, q, 4);
      return c;
    };
    P.claw = q => cel([50, 44, 40], 0.5 + 0.4 * (1 - q.ny), 0.8, 1.4);
    return P;
  }

  function rigDef(P, v, low) {
    const k = v.big ? 1.15 : 1;
    const wing = s => {
      const D = s < 0;
      const L1 = 11 * k, L2 = 14 * k;
      return {
        name: D ? 'asaD' : 'asaE', pivot: [s * 2.4, -1.6, -2.4], boxes: [
          { x: D ? -L1 : 0, y: -0.7, z: -0.9, w: L1, h: 1.4, d: 1.8, p: P.hide },
          ...(low ? [] : [{ x: D ? -L1 : 0, y: -0.15, z: 0.4, w: L1, h: 0.3, d: 9 * k, p: P.wing(L1, 9 * k, true, D) }]),
        ],
        kids: [{
          name: D ? 'pontaD' : 'pontaE', pivot: [s * L1, 0, 0], boxes: [
            { x: D ? -L2 : 0, y: -0.5, z: -0.7, w: L2, h: 1, d: 1.4, p: P.hide },
            { x: D ? -L2 : 0, y: -0.15, z: 0.4, w: L2, h: 0.3, d: 8.6 * k, p: P.wing(L2, 8.6 * k, false, D) },
            { x: D ? -0.6 : -0.6, y: -0.4, z: -2.4, w: 1.2, h: 1, d: 1.8, p: P.claw },
          ],
        }],
      };
    };
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'corpo', pivot: [0, 8, 0], boxes: [{ x: -2.6, y: -2.6, z: -5.2, w: 5.2, h: 5.4, d: 9.6, p: P.hide }], kids: [
          {
            name: 'pescoco', pivot: [0, -1, -5], rot: [0.9, 0, 0], boxes: [{ x: -1.2, y: -5.4, z: -1.2, w: 2.4, h: 5.8, d: 2.4, p: P.hide }],
            kids: [{
              name: 'cabeca', pivot: [0, -5, 0], rot: [-0.9, 0, 0], boxes: [
                { x: -2, y: -2.4, z: -4, w: 4, h: 3.6, d: 6, p: P.head },
                { x: -1.3, y: -1.6, z: -13.5, w: 2.6, h: 2, d: 9.6, p: P.beak },
                { x: -0.3, y: -6.2, z: 0.4, w: 0.6, h: 5.6, d: 7, p: P.crest },
              ],
              kids: [{ name: 'mandibula', pivot: [0, 0.4, -3], boxes: [{ x: -1.1, y: 0, z: -9.8, w: 2.2, h: 1, d: 10, p: P.beak }] }],
            }],
          },
          wing(-1), wing(1),
          { name: 'pernaD', pivot: [-1.5, 2.2, 3], rot: [1.2, 0, 0], boxes: [{ x: -0.6, y: 0, z: -0.6, w: 1.2, h: 4.4, d: 1.2, p: P.hide }], kids: [{ name: 'peD', pivot: [0, 4.2, 0], boxes: [{ x: -1, y: 0, z: -1.6, w: 2, h: 0.8, d: 2.2, p: P.claw }] }] },
          { name: 'pernaE', pivot: [1.5, 2.2, 3], rot: [1.2, 0, 0], boxes: [{ x: -0.6, y: 0, z: -0.6, w: 1.2, h: 4.4, d: 1.2, p: P.hide }], kids: [{ name: 'peE', pivot: [0, 4.2, 0], boxes: [{ x: -1, y: 0, z: -1.6, w: 2, h: 0.8, d: 2.2, p: P.claw }] }] },
          { name: 'cauda', pivot: [0, -0.5, 4.2], rot: [-0.1, 0, 0], boxes: [{ x: -0.5, y: -0.5, z: 0, w: 1, h: 1, d: 5, p: P.hide }] },
        ],
      }],
    };
  }

  // vendaval: listras de areia que correm do pterodáctilo até o alvo, cada uma com altura e atraso próprios
  function gust() {
    const N = 26, SEG = 14;
    const pos = new Float32Array(N * (SEG + 1) * 2 * 3), al = new Float32Array(N * (SEG + 1) * 2), idx = [];
    for (let l = 0; l < N; l++) for (let s = 0; s < SEG; s++) { const a = (l * (SEG + 1) + s) * 2; idx.push(a, a + 1, a + 2, a + 1, a + 3, a + 2); }
    const geo = new THREE.BufferGeometry(); geo.setIndex(idx);
    geo.setAttribute('position', new THREE.BufferAttribute(pos, 3)); geo.setAttribute('al', new THREE.BufferAttribute(al, 1));
    const m = new THREE.Mesh(geo, new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, side: THREE.DoubleSide,
      vertexShader: 'attribute float al; varying float vA; void main(){ vA=al; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: 'varying float vA; void main(){ gl_FragColor = vec4(vec3(0.93,0.84,0.66), vA); }',
    }));
    m.frustumCulled = false;
    const R = FX.rnd(404), L = [];
    for (let l = 0; l < N; l++) L.push({ x: (R() - 0.5) * 2.6, y: 0.2 + R() * 2.2, dl: R() * 0.8, len: 1.2 + R() * 2.2, sp: 7 + R() * 5, wob: R() * 6, w: 0.015 + R() * 0.03 });
    m.at = (from, to, age) => {
      m.visible = true;
      L.forEach((l, i) => {
        const a = age - l.dl;
        const head = a * l.sp;
        for (let s = 0; s <= SEG; s++) {
          const f = s / SEG, d = head - (1 - f) * l.len;
          const z = from.z + (to.z - from.z) * Math.min(1.2, d / Math.abs(to.z - from.z));
          const y = l.y + 0.12 * Math.sin(d * 2 + l.wob), x = l.x * (0.6 + 0.4 * Math.min(1, d / 6)) + 0.08 * Math.sin(d * 3 + l.wob);
          const k = (i * (SEG + 1) + s) * 2;
          pos.set([x, y - l.w, z, x, y + l.w, z], k * 3);
          const vis = a > 0 && d > 0 && d < Math.abs(to.z - from.z) + 2 ? Math.sin(Math.PI * f) * 0.7 * (1 - Math.max(0, age - 1.6) / 0.6) : 0;
          al[k] = al[k + 1] = Math.max(0, vis);
        }
      });
      geo.attributes.position.needsUpdate = true; geo.attributes.al.needsUpdate = true;
    };
    return m;
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const flap = (ph, amp) => {
      const w = Math.sin(ph * Math.PI * 2), w2 = Math.sin(ph * Math.PI * 2 - 0.9);
      r('asaD', 0, 0, -0.75 * w * amp); r('asaE', 0, 0, 0.75 * w * amp);
      r('pontaD', 0, 0, -0.45 * w2 * amp); r('pontaE', 0, 0, 0.45 * w2 * amp);
      P.corpo.obj.position.y += 1.2 * w * amp;
    };
    const glide = k => { r('asaD', 0, 0, 0.12 * k); r('asaE', 0, 0, -0.12 * k); };

    if (mode === 'parado') {
      // plana em círculo devagar, batendo a asa de vez em quando
      obj.position.y += 2.2;
      const ph = fr(t / 3.2);
      const fl = ph < 0.35 ? 1 : 0;
      if (fl) flap(ph / 0.35 * 2, 0.8); else glide(1);
      r('corpo', 0, 0, 0.18 * Math.sin(t * 0.6));
      r('cabeca', 0, 0.25 * Math.sin(t * 0.9), 0);
      r('mandibula', 0.25 * seq([[0.6, 0], [0.64, 1], [0.7, 0], [0.74, 1], [0.8, 0]], fr(t / 4.6)), 0, 0);
      return;
    }
    if (mode === 'andar') {
      obj.position.y += 2.2;
      flap(t * 1.4, 1);
      r('corpo', 0.08, 0, 0);
      r('pescoco', -0.05 * Math.sin(t * 1.4 * Math.PI * 2), 0, 0);
      return;
    }

    if (mode === 'mergulho') {
      const T = t % 4.4;
      const R = ctx.ref; const RZ = -6; R.place(0, RZ, 0);
      // grita, fecha as asas e mergulha em cima do alvo; pega com os pés, sobe batendo as asas e solta
      const scream = seq([[0, 0], [0.15, 1], [0.6, 1], [0.7, 0]], T);
      const dive = seq([[0.65, 0], [1.25, 1, 'in']], T);
      const grab = T > 1.25 && T < 2.9 ? 1 : 0;
      const climb = seq([[1.3, 0], [2.9, 1, 'out']], T);
      const away = seq([[2.9, 0], [3.8, 1, 'out']], T);
      r('mandibula', 0.6 * scream * Math.max(0, Math.sin(T * 30)) + 0.5 * scream, 0, 0);
      r('pescoco', -0.3 * scream, 0, 0);
      const start = V3(0, 5.5, 0), low = V3(0, 0.9, RZ + 0.1), top = V3(0, 6.5, RZ - 2.5), far = V3(0, 7.5, RZ - 9);
      let p;
      if (T < 0.65) p = start.clone();
      else if (T < 1.25) p = start.clone().lerp(low, dive);
      else if (T < 2.9) p = low.clone().lerp(top, climb);
      else p = top.clone().lerp(far, away);
      obj.position.set(p.x, p.y + 1.5, p.z);
      if (T < 0.65) flap(T * 1.6, 0.8);
      else if (T < 1.25) { r('asaD', 0, 0.9 * dive, 0.6 * dive); r('asaE', 0, -0.9 * dive, -0.6 * dive); r('corpo', 0.75 * dive, 0, 0); r('cabeca', -0.4 * dive, 0, 0); }
      else flap(T * 1.8, 1.1);
      r('pernaD', -1.6 * (grab + dive * 0.6), 0, 0); r('pernaE', -1.6 * (grab + dive * 0.6), 0, 0);
      // o alvo pendurado nos pés, depois cai
      if (grab) { R.place(p.x, p.z + 0.15, p.y - 2.0); R.armsOut(0.8); R.lean(0.15 * Math.sin(T * 6)); ctx.shake(T < 1.4 ? 0.6 : 0); }
      if (T >= 2.9) {
        const f = seq([[2.9, 0], [3.55, 1, 'in']], T);
        R.place(top.x, top.z + 0.15 - 0.6 * f, (top.y - 2.0) * (1 - f)); R.armsOut(0.8 * (1 - f)); R.lean(-1.3 * f);
        if (T > 3.55 && T < 4.3) { ctx.shake(Math.max(0, 0.7 - (T - 3.55))); ctx.get('queda', FX.volume).set(V3(0, 0.3, RZ - 2.4), 0.7, (T - 3.55) / 0.75, { seed: 6.6, dens: 6, rise: 0.3, smoke: [0.84, 0.72, 0.54] }); }
      }
      if (T > 1.2 && T < 2.2) ctx.get('poAgarra', FX.volume).set(V3(0, 0.3, RZ), 0.8, (T - 1.2) / 1.0, { seed: 2.2, dens: 5, rise: 0.4, smoke: [0.84, 0.72, 0.54] });
      return;
    }

    if (mode === 'ventania') {
      const T = t % 3.8;
      const R = ctx.ref; const RZ = -5.5; R.place(0, RZ, 0);
      // para no ar, abre as asas pra trás e bate três vezes com força
      obj.position.y += 1.8;
      const open = seq([[0, 0], [0.5, 1, 'out']], T);
      const beats = T > 0.5 && T < 2.3 ? (T - 0.5) / 0.6 : 0;
      r('corpo', -0.55 * open, 0, 0); r('pescoco', 0.3 * open, 0, 0); r('cabeca', 0.25 * open, 0, 0);
      if (beats > 0) flap(beats + 0.25, 1.5); else { r('asaD', 0, -0.5 * open, -0.4 * open); r('asaE', 0, 0.5 * open, 0.4 * open); }
      r('mandibula', 0.5 * open, 0, 0);
      r('pernaD', -0.6 * open, 0, 0); r('pernaE', -0.6 * open, 0, 0);
      if (T > 0.6) {
        ctx.get('vento', gust).at(V3(0, 0, -0.5), V3(0, 0, RZ), T - 0.6);
        [0.62, 1.22, 1.82].forEach((bt, i) => {
          const age = (T - bt) / 1.3; if (age <= 0 || age >= 1) return;
          ctx.get('areia' + i, FX.volume).set(V3((i - 1) * 0.5, 0.4, -0.8 + (RZ + 1.5) * Math.min(1, age * 1.6)), 0.6 + 0.2 * i, age, { seed: 12.5 + i * 3.9, dens: 5, rise: 0.15, squash: 0.8, smoke: [0.86, 0.76, 0.58] });
          if (age < 0.15) ctx.shake(0.3);
        });
        // empurrado pra trás aos trancos, cobrindo o rosto
        const push = seq([[0.75, 0], [1.0, 0.35], [1.35, 0.4], [1.6, 0.75], [1.95, 0.8], [2.2, 1.2], [2.6, 1.3]], T);
        R.place(0, RZ - 2.2 * push, 0); R.lean(-0.25 * Math.min(1, push)); R.punch(-0.7 * Math.min(1, push * 2) * (1 - seq([[2.8, 0], [3.4, 1]], T)));
        ctx.blur(0.08 * (T < 2.6 ? 1 : 0));
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(3), variant(17), variant(44)];
  fixed[0].skin = SKINS[0]; fixed[0].wing = WINGS[0]; fixed[1].skin = SKINS[1]; fixed[1].wing = WINGS[1]; fixed[1].torn = true; fixed[2].skin = SKINS[2]; fixed[2].wing = WINGS[2]; fixed[2].big = true;
  const def = v => ({ def: rigDef(painters(v), v, false), low: rigDef(painters(v), v, true) });
  window.MOBS.push({
    id: 'pterodactilo', nome: 'Pterodáctilo', bioma: 'Dinossauros', tier: 'Voador · tier 8', refX: 3,
    variants: fixed.map((v, i) => ({ nome: ['Roxo', 'Pardo (asa rasgada)', 'Azul (grande)'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Planando'], ['andar', 'Voando'], ['mergulho', 'Mergulho'], ['ventania', 'Ventania']],
    cam: 8, lookY: 2.6, lookZ: { mergulho: -4, ventania: -2.6 }, camFar: { mergulho: 14, ventania: 10 }, camYaw: { mergulho: -1.4, ventania: -1.1 },
    info: {
      'Tamanho': 'Corpo de 1 bloco, asa aberta de 3,5 a 4 blocos',
      'Vida': '30 (tier 8)',
      'Jeito': 'Em dupla ou trio rodando alto. Ataca quem está sozinho ou voando perto. Pousa em pico de rocha e árvore alta.',
      'Mergulho': 'Grita (aviso), fecha as asas e mergulha. Se pegar, sobe uns 6 blocos com você nos pés e solta. Bater nele enquanto carrega faz soltar mais baixo.',
      'Ventania': 'Para no ar e bate as asas três vezes: um vendaval de areia empurra pra trás e atrapalha a visão. Quem está voando é jogado longe.',
      'Solta': 'Couro de asa (planador simples), carne; raro: crista de pterodáctilo.',
      'Variações': 'Cor do corpo e da asa, cor da crista, asa rasgada e tamanho sorteados.',
    },
  });
})();
