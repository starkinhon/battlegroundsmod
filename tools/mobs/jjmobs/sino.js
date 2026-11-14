// Maldição do Sino (Templos e Escola). Tier 7. O sino de bronze do templo (bonshō) que guardou séculos de medo das
// badaladas de enterro e virou maldição: o assento onde a tora bate virou um olho enorme, a boca do sino é uma boca
// de verdade com dentes na borda, e a tora de bater (shumoku) ficou presa numa corda viva que ele usa de braço.
// Anda pulando e batendo no chão. Golpes: Badalada (bate a tora no próprio olho: ondas de som que deixam tonto) e
// Prender (pula, cai por cima de você e te tranca dentro dele; depois toca com você lá dentro).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  // [nome, bronze, azinhavre, olho]
  const KINDS = [
    ['Bronze Velho', [128, 92, 56], [70, 140, 120], [230, 200, 60]],
    ['Verde de Azinhavre', [96, 112, 88], [90, 170, 140], [220, 60, 50]],
    ['Ferro Preto', [62, 58, 60], [120, 110, 100], [180, 220, 240]],
  ];
  function variant(seed, kind) {
    const R = FX.rnd(seed);
    const k = kind ?? Math.floor(R() * KINDS.length);
    return { seed, kind: k, metal: KINDS[k][1], rust: KINDS[k][2], iris: KINDS[k][3], crack: R() < 0.6, size: 0.95 + R() * 0.12 };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.74, hi = 1.18) => {
      const f = 0.62 - 0.35 * q.ny + 0.3 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    // metal fundido: manchas de azinhavre que escorrem pra baixo, brilho gasto onde a mão das pessoas passou
    const metal = (q, s) => {
      let c = shade(v.metal, q, s);
      const drip = n3(q.ax * 0.35, q.ay * 0.08, q.az * 0.35, 50 + S) + 0.25 * (q.ay / 28);
      if (drip > 0.72) c = mix(c, shade(v.rust, q, s + 1, 0.8, 1.1), 0.75);
      else if (drip > 0.64) c = mix(c, v.rust, 0.3);
      if (n3(q.ax * 1.4, q.ay * 1.4, q.az * 1.4, 51 + S) > 0.8) c = mul(c, 1.15);
      // rachadura escura descendo torta (variação)
      if (v.crack && (q.f === 'east') && Math.abs(q.nz - 0.4 - 0.12 * Math.sin(q.ay * 0.7)) < 0.03 && q.ny > 0.25) c = [20, 12, 16];
      return c;
    };
    // faixas horizontais em relevo e a grade de painéis (como os sinos de verdade)
    const P = {};
    P.cap = q => metal(q, 1);
    P.body = q => {
      let c = metal(q, 2);
      if (q.f === 'top' || q.f === 'bottom') return c;
      const y = q.py, h = q.b.h, u = q.f === 'north' || q.f === 'south' ? q.nx : q.nz;
      if (Math.abs(y - h * 0.42) < 0.6 || Math.abs(y - h * 0.44) < 0.3) c = mul(c, 1.18);                              // faixa do meio
      if (Math.abs(u - 0.5) < 0.03 && y > h * 0.44) c = mul(c, 0.8);                                                     // divisão vertical
      if (y > h * 0.5 && (u < 0.06 || u > 0.94)) c = mul(c, 0.8);
      // inscrição (sutra) no painel de baixo: risquinhos verticais
      if (y > h * 0.55 && y < h * 0.92 && u > 0.12 && u < 0.88 && Math.abs(u - 0.5) > 0.06) {
        const col = Math.floor(u * 14), R = FX.rnd(col * 13 + Math.floor(y) * 5 + S);
        if (fr(u * 14) < 0.45 && R() < 0.5) c = mul(c, 0.66);
      }
      return c;
    };
    P.boss = q => { let c = metal(q, 3); if (q.f === 'top') c = mul(c, 1.15); return c; };                              // botões (chi)
    P.lip = q => {
      let c = metal(q, 4);
      // por baixo: boca escura, gengiva roxa e um anel de dentes amarelados na borda
      if (q.f === 'bottom') {
        const dx = Math.abs(q.nx - 0.5), dz = Math.abs(q.nz - 0.5), d = Math.max(dx, dz);
        if (d > 0.44) return c;
        if (d > 0.36) return (Math.floor(q.px) + Math.floor(q.pz)) % 2 ? [224, 210, 160] : [120, 40, 60];
        if (d > 0.31) return [110, 30, 54];
        return d > 0.2 ? [40, 10, 20] : [14, 4, 8];
      }
      return c;
    };
    P.handle = q => { let c = metal(q, 5); if (fr(q.ay * 0.7 + q.ax * 0.3) < 0.3) c = mul(c, 0.82); return c; };      // escamas do dragão
    // olho no lugar do assento da batida (era uma flor de lótus em relevo; as pétalas viraram pálpebra)
    P.eye = q => {
      if (q.f !== 'north') return metal(q, 6);
      const W = q.b.w, H = q.b.h, x = (q.px - W / 2) / (W / 2), y = (q.py - H / 2) / (H / 2), d = Math.hypot(x, y);
      if (d > 0.92) return metal(q, 6);
      if (d > 0.8) return mix(mul(v.metal, 0.45), [60, 10, 20], 0.5);                                                   // órbita funda (carne escura em volta)
      const ix = x - 0.06, iy = y + 0.04, di = Math.hypot(ix, iy);
      if (Math.abs(ix) < 0.11 && Math.abs(iy) < 0.42 - ix * ix * 4) return [8, 4, 6];                                   // pupila em fenda
      if (di < 0.5) return mix(mul(v.iris, 0.7), v.iris, di * 2);
      if (di < 0.56) return [40, 20, 20];
      if (ix < -0.2 && iy < -0.15 && ix > -0.36 && iy > -0.3) return [255, 255, 255];
      let c = mix([214, 200, 170], [190, 120, 110], Math.max(0, d - 0.5) * 2);                                          // branco amarelado, avermelha na borda
      if (n3(x * 7, y * 7, 0, 60 + S) > 0.66 && d > 0.5) c = [170, 30, 44];                                               // veias
      return c;
    };
    P.lid = q => { let c = mix(metal(q, 7), [110, 60, 70], 0.35); if (q.f === 'north' && (q.b.y >= 0 ? q.ny > 0.8 : q.ny < 0.2)) c = [20, 10, 14]; return c; };
    P.rope = q => { let c = shade([150, 40, 50], q, 8, 0.78, 1.12); if (fr((q.ay + q.ax) * 0.8) < 0.4) c = mul(c, 0.72); if (n3(q.ax, q.ay, q.az, 9 + S) > 0.75) c = mix(c, [80, 30, 60], 0.5); return c; };
    P.log = q => {
      let c = shade([150, 112, 72], q, 10, 0.78, 1.12);
      if (q.f === 'north' || q.f === 'south') { const d = Math.hypot(q.nx - 0.5, q.ny - 0.5); if (fr(d * 6) < 0.3) c = mul(c, 0.8); if (q.f === 'north' && d < 0.4) c = mix(c, [70, 30, 30], 0.35); return c; }  // anéis + a ponta gasta de tanto bater
      if (fr(q.nx * 3 + n3(q.ax, q.ay * 0.2, q.az, 11 + S)) < 0.15) c = mul(c, 0.8);
      return c;
    };
    return P;
  }

  // caixa cruzada: a w x (w-2c) e (w-2c) x w, quinas chanfradas
  function oct(w, y, h, p, c) { return [{ x: -w / 2, y, z: -w / 2 + c, w, h, d: w - 2 * c, p }, { x: -w / 2 + c, y, z: -w / 2, w: w - 2 * c, h, d: w, p }]; }
  function rig(P, v, lo) {
    // sino em camadas, base no chão (y 24), topo y -4; o pivô 'sino' fica na borda de baixo pra ele balançar apoiado nela
    const bosses = [];
    if (!lo) for (const f of [[0, -1], [0, 1], [-1, 0], [1, 0]]) for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) {
      if (f[1] === -1 && c === 1 && r === 2) continue;                                                                               // espaço do olho na frente
      const u = (c - 1) * 2.6, y = -23.6 + r * 2.4;
      const x = f[0] ? f[0] * 7.1 : u, z = f[1] ? f[1] * 7.1 : u;
      bosses.push({ x: x - 0.7 + f[0] * 0.3, y, z: z - 0.7 + f[1] * 0.3, w: 1.4, h: 1.4, d: 1.4, p: P.boss });
    }
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'sino', pivot: [0, 24, 0], boxes: [
          // ombro arredondado em degraus e corpo "octogonal" (duas caixas cruzadas cortam as quinas)
          ...oct(10, -28.4, 1.4, P.cap, 2), ...oct(12.6, -27, 1.4, P.cap, 2.4), ...oct(13.8, -25.6, 1.2, P.body, 2.6),
          ...oct(14, -24.4, 17, P.body, 2.8), ...oct(15.2, -7.4, 4, P.body, 3), ...oct(16.4, -3.6, 3.6, P.lip, 3.2),
          ...bosses,
          // ryūzu: alça de dragão de duas cabeças em cima
          { x: -4.4, y: -31.4, z: -1.4, w: 2.4, h: 3.6, d: 2.8, p: P.handle }, { x: 2, y: -31.4, z: -1.4, w: 2.4, h: 3.6, d: 2.8, p: P.handle },
          { x: -2.4, y: -32.4, z: -1.2, w: 4.8, h: 2, d: 2.4, p: P.handle },
          ...(lo ? [] : [{ x: -5.4, y: -30.6, z: -2.2, w: 1.6, h: 1.6, d: 1.4, p: P.handle }, { x: 3.8, y: -30.6, z: -2.2, w: 1.6, h: 1.6, d: 1.4, p: P.handle }]),
        ],
        kids: [
          { name: 'olho', pivot: [0, -13, -7.05], boxes: [{ x: -4.6, y: -4.6, z: -0.6, w: 9.2, h: 9.2, d: 0.7, p: P.eye }] },
          { name: 'palpebraC', pivot: [0, -17.6, -7.6], boxes: [{ x: -4.4, y: 0, z: -0.3, w: 8.8, h: 4.6, d: 0.5, p: P.lid }] },
          { name: 'palpebraB', pivot: [0, -8.4, -7.6], boxes: [{ x: -4.4, y: -4.6, z: -0.3, w: 8.8, h: 4.6, d: 0.5, p: P.lid }] },
          {
            // corda viva saindo da alça; a tora pendurada na ponta, deitada e apontada pro olho
            name: 'corda', pivot: [0, -32, 0], rot: [-0.6, 0, 0], boxes: [{ x: -0.6, y: 0, z: -0.6, w: 1.2, h: 20.2, d: 1.2, p: P.rope }],
            kids: [{ name: 'tora', pivot: [0, 20.2, 0], rot: [0.6, 0, 0], boxes: [{ x: -2.1, y: -2.1, z: -9.5, w: 4.2, h: 4.2, d: 10, p: P.log }, { x: -0.8, y: -2.6, z: -6.5, w: 1.6, h: 1, d: 1.6, p: P.rope }] }],
          },
        ],
      }],
    };
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size * 1.15; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const lids = k => { P.palpebraC.obj.scale.y = Math.max(0.02, k); P.palpebraB.obj.scale.y = Math.max(0.02, k); };
    const blink = () => loop([[0, 0], [0.03, 1], [0.08, 0]], t / 3.7);
    // vibração do metal depois de bater: o sino treme e "zumbe"
    const hum = (k) => { if (k <= 0) return; r('sino', 0.02 * k * Math.sin(t * 70), 0, 0.02 * k * Math.sin(t * 63)); const sc = 1 + 0.025 * k * Math.sin(t * 90); P.sino.obj.scale.set(sc, 1, sc); };
    const eyeW = () => { obj.updateMatrixWorld(true); const w = new THREE.Vector3(); P.olho.obj.getWorldPosition(w); return w; };
    // ondas de som: anéis que saem do olho, mais de um por batida
    const rings = (age, k = 1) => {
      for (let i = 0; i < 3; i++) {
        const a2 = age - i * 0.12; if (a2 <= 0 || a2 > 0.9) continue;
        ctx.get('onda' + i, () => soundRing()).at(eyeW(), a2 / 0.9, k);
      }
    };

    const side = k => r('corda', 0, 0, 0.6 * k);
    if (mode === 'parado') {
      side(1);
      // fica quieto como sino de verdade; só o olho mexe (procura), pisca devagar, e a tora balança na corda
      lids(0.35 + 0.65 * blink());
      r('olho', 0.12 * Math.sin(t * 0.6), 0.25 * Math.sin(t * 0.37), 0);
      r('corda', 0.06 * Math.sin(t * 1.1), 0, 0.05 * Math.sin(t * 0.8));
      r('tora', -0.06 * Math.sin(t * 1.1), 0.1 * Math.sin(t * 0.5), 0);
      return;
    }
    if (mode === 'andar') {
      // pula: agacha (achata), sobe girando um pouco, cai com tudo, quica
      const ph = (t * 0.9) % 1;
      const up = ph < 0.55 ? Math.sin(Math.PI * ph / 0.55) : 0;
      const squash = ph < 0.08 ? ph / 0.08 : (ph > 0.55 && ph < 0.7 ? 1 - (ph - 0.55) / 0.15 : 0);
      obj.position.y += 0.9 * up; side(1);
      P.sino.obj.scale.set(1 + 0.08 * squash, 1 - 0.1 * squash, 1 + 0.08 * squash);
      r('sino', 0.12 * Math.sin(ph * Math.PI * 2), 0, 0);
      r('corda', -0.4 * up, 0, 0); r('tora', 0.3 * up, 0, 0);
      lids(0.5);
      const land = ph - 0.55;
      if (land > 0 && land < 0.3) { ctx.roarRing(new THREE.Vector3(0, 0.02, 0), land / 0.3); ctx.shake(0.4 * (1 - land / 0.3)); hum(1 - land / 0.3); }
      return;
    }
    if (mode === 'badalada') {
      const T = t % 4.0;
      const R = ctx.ref; const RZ = -3.2; R.place(0, RZ, 0); R.spin(Math.PI);
      // puxa a tora pra trás bem alto, solta, e ela bate no próprio olho: BONG. O olho fecha no impacto e abre vermelho.
      const pull = seq([[0.2, 0], [1.0, 1, 'io'], [1.25, 1.05], [1.42, -0.27, 'in'], [1.6, 0.15, 'out'], [2.2, 0, 'io']], T);
      r('corda', -0.9 * pull, 0, 0); r('tora', 0.9 * pull, 0, 0);
      side(seq([[0, 1], [0.6, 0, 'io'], [2.4, 0], [3.6, 1, 'io']], T));
      r('sino', 0.08 * pull, 0, 0);
      const hit = T - 1.42;
      lids(hit > -0.15 && hit < 0.1 ? 1 : 0.15 + 0.25 * seq([[1.6, 0], [3.4, 1]], T));
      r('olho', 0.2 * seq([[1.0, 0], [1.2, 1], [1.42, 0]], T), 0, 0);
      hum(hit > 0 ? Math.max(0, 1 - hit / 1.6) : 0);
      if (hit > 0 && hit < 1.2) {
        rings(hit);
        if (hit < 0.15) ctx.flash(eyeW(), 1 - hit / 0.15);
        ctx.shake(0.8 * Math.max(0, 1 - hit / 0.6));
        // som chega no alvo: cambaleia, tela gira
        const arrive = hit - 0.18;
        if (arrive > 0) { R.lean(-0.18 * Math.sin(arrive * 9) * Math.max(0, 1 - arrive)); R.armsOut(0.5 * Math.max(0, 1 - arrive)); ctx.dizzy(Math.max(0, 1.2 - arrive)); }
      }
      return;
    }
    if (mode === 'prender') {
      const T = t % 5.6;
      const RZ = -2.4; const R = ctx.ref; R.place(0, RZ, 0); R.spin(Math.PI);
      // pulo alto por cima do alvo, cai e tranca. Badala duas vezes com ele dentro. Depois pula fora e o alvo cambaleia.
      const jump = seq([[0.3, 0], [1.05, 1, 'io']], T);
      const out = seq([[4.2, 0], [4.9, 1, 'io']], T);
      const air = Math.sin(Math.PI * jump) * 2.2 + Math.sin(Math.PI * out) * 1.4;
      obj.position.z += RZ * (jump - out) + 1.2 * out;
      obj.position.y += air;
      r('sino', -0.6 * Math.sin(Math.PI * jump), 0, 0);
      const crouch = seq([[0, 0], [0.25, 1, 'out'], [0.32, 0]], T);
      P.sino.obj.scale.set(1 + 0.1 * crouch, 1 - 0.14 * crouch, 1 + 0.1 * crouch);
      lids(T < 1.05 ? 0.1 : 0.4);
      r('corda', -0.5 * Math.sin(Math.PI * jump), 0, 0);
      const landA = T - 1.05;
      if (landA > 0 && landA < 0.35) { ctx.roarRing(new THREE.Vector3(0, 0.02, RZ), landA / 0.35); ctx.shake(1 - landA / 0.35); }
      side(seq([[0, 1], [1.2, 0, 'io'], [3.6, 0], [4.8, 1, 'io']], T));
      const inside = T > 1.02 && T < 4.35;
      R.place(0, RZ, 0); if (inside) R.armsOut(0.2);
      if (inside) ctx.dark(0.85);
      // duas badaladas com o alvo lá dentro (do lado de dentro é ensurdecedor: tela gira e borra)
      for (const t0 of [1.9, 3.1]) {
        const pull = seq([[t0 - 0.6, 0], [t0 - 0.15, 1, 'io'], [t0, -0.27, 'in'], [t0 + 0.3, 0, 'out']], T);
        r('corda', -0.8 * pull, 0, 0); r('tora', 0.8 * pull, 0, 0);
        const hit = T - t0;
        if (hit > 0 && hit < 1.1) { rings(hit, 0.8); hum(1 - hit / 1.1); ctx.dizzy(1.4 * (1 - hit / 1.1)); ctx.blur(0.5 * (1 - hit / 1.1)); ctx.shake(0.7 * (1 - hit / 0.5)); }
      }
      if (T > 4.4) { const k = Math.max(0, 1 - (T - 4.4) / 1.2); ctx.dizzy(k); R.lean(0.25 * Math.sin((T - 4.4) * 7) * k); R.armsOut(0.6 * k); }
      return;
    }
    if (mode === 'morte') {
      // racha de cima a baixo: as duas metades caem pros lados e o olho fecha; depois vira cinza
      const { T, fall } = JJFX.exorcize(t, obj, e, ctx, { h: 2.2, refZ: -2.4, rotX: 0.35, back: 0.25, ashY: 0.6, ashZ: 0, n: 200 });
      lids(Math.min(1, 0.2 + fall));
      hum(Math.max(0, 1 - T / 1.2));
      if (T > 0.25 && T < 1.1) rings(T - 0.25, 0.6);
      r('corda', 0.5 * fall, 0, 0.3 * fall); r('tora', -0.3 * fall, 0, 0);
    }
  }

  // anel de som: casca fina e ondulada que cresce, um pouco amarela/branca
  function soundRing() {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, side: THREE.DoubleSide, uniforms: { uA: { value: 0 } },
      vertexShader: 'varying vec2 vP; void main(){ vP=position.xy; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: `varying vec2 vP; uniform float uA;
        void main(){ float r=length(vP), a=atan(vP.y,vP.x); float e=0.86+0.04*sin(a*7.0)+0.03*sin(a*13.0+2.0);
          float k=smoothstep(0.09,0.0,abs(r-e))*uA; if(k<0.01) discard; gl_FragColor=vec4(mix(vec3(1.0,0.92,0.6),vec3(1.0),k),k*0.8); }`,
    });
    const m = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), mat);
    m.frustumCulled = false;
    m.at = (p, f, k = 1) => { m.visible = f > 0 && f < 1; m.position.copy(p); m.position.z -= f * 1.8; m.scale.setScalar(0.3 + f * 2.4); mat.uniforms.uA.value = (1 - f) * k; };
    return m;
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(8, 0), variant(33, 1), variant(52, 2)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'sino', nome: 'Maldição do Sino', bioma: 'Templos e Escola', tier: 'Grau 2 · tier 7',
    variants: fixed.map((v, i) => ({ nome: KINDS[v.kind][0], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado (' + KINDS[v.kind][0] + ')', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Pular'], ['badalada', 'Badalada'], ['prender', 'Prender'], ['morte', 'Exorcizada']],
    cam: 5.4, lookY: 1.2, lookZ: { badalada: -1.3, prender: -1.4, morte: -0.4 }, camFar: { badalada: 6.8, prender: 7.2 }, camYaw: { badalada: -0.9, prender: -1.2 },
    info: {
      'Tamanho': '2 blocos de altura, 1 de largura (mais a corda com a tora)',
      'Vida': '60 (metal: espada faz metade do dano, golpe de impacto e técnica fazem dano cheio)',
      'Jeito': 'Fica parado no campanário igual um sino normal; só o olho se mexe. Quando você chega perto, pula do suporte e vem quicando. Lento, mas cada pulo faz o chão tremer.',
      'Badalada': 'Puxa a tora pra trás e bate no próprio olho: três ondas de som saem pela frente. Quem pega fica tonto por 3 s (tela girando, mira tremendo). Dá pra se proteger atrás de parede.',
      'Prender': 'Pula alto e cai por cima de você: fica preso dentro do sino no escuro (não sai nem com pulo) e ele badala duas vezes com você lá dentro (dano + tontura forte). Bater por dentro faz ele soltar mais cedo.',
      'Fraqueza': 'De costas não tem olho: golpe nas costas faz o dobro. Com o olho fechado (logo depois de bater) também.',
      'Solta': 'Lasca de Bronze, Corda Sagrada; raro: Sino Amaldiçoado (item: toca e deixa tontas as maldições em volta).',
      'Variações': 'Bronze velho, verde de azinhavre ou ferro preto; olho de cor diferente; rachadura de lado.',
    },
  });
})();
