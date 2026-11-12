// Corvo Uchiha (Vale do Fim). Fraco, tier 10. Corvo preto com Sharingan, pousa nas cabeças das estátuas.
// Golpes: Genjutsu (encara você, o Sharingan gira e o mundo fica vermelho e cheio de pena: você fica tonto)
// e Revoada (o corvo vira um bando que atravessa você bicando e se junta de novo atrás).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  function painters(v) {
    // pena preta azulada: brilho azul/roxo nas faces de cima, listras das penas
    const feather = s => q => {
      let c = cel(v.black, n3(q.ax * 0.8, q.ay * 0.8, q.az * 0.8, s + v.seed), 0.86, 1.18);
      if (q.f === 'top') c = mix(c, v.sheen, 0.35);
      if (q.f === 'bottom') c = mul(c, 0.8);
      return c;
    };
    const body = feather(1);
    const wing = q => {
      let c = feather(2)(q);
      if (fr(q.nx * 5 + 0.3 * q.nz) < 0.12) c = mul(c, 0.72);                                     // separação das penas
      if (q.nx > 0.7 && fr(q.nz * 4) < 0.15) c = mul(c, 0.8);                                      // pontas abertas
      return c;
    };
    const tail = q => { let c = feather(3)(q); if (fr(q.nx * 4) < 0.15) c = mul(c, 0.75); return c; };
    const beak = q => cel([44, 44, 48], n3(q.ax * 2, q.ay * 2, q.az * 2, 9), 0.8, 1.25);
    const leg = q => cel([40, 38, 40], n3(q.ax, q.ay * 3, q.az, 10), 0.8, 1.2);
    // olho: Sharingan de três tomoe nas laterais da cabeça
    const head = q => {
      let c = body(q);
      if (q.f !== 'west' && q.f !== 'east') return c;
      const x = q.f === 'west' ? q.pz : 2.6 - q.pz, y = q.py;
      const dx = x - 0.85, dy = y - 0.95, d = Math.hypot(dx, dy);
      if (d < 0.62) {
        c = [196, 20, 26];
        if (d < 0.13) c = [16, 10, 10];
        const a = Math.atan2(dy, dx);
        if (Math.abs(d - 0.36) < 0.05) c = [120, 10, 14];
        for (let i = 0; i < 3; i++) { const ta = a - i * 2.094; if (Math.abs(d - 0.36) < 0.12 && Math.abs(Math.atan2(Math.sin(ta), Math.cos(ta))) < 0.35) c = [16, 10, 10]; }
        if (d > 0.54) c = [16, 10, 10];
      }
      return c;
    };
    return { body, wing, tail, beak, leg, head };
  }
  function defOf(v, low) {
    const P = painters(v);
    // asa: placa aberta (envergadura no x); abre/fecha girando 'asaX' (fora) e 'asaXi' (dentro)
    const wing = s => ({
      name: s < 0 ? 'asaD' : 'asaE', pivot: [s * 1.5, -1.2, -1.2], kids: [{
        name: s < 0 ? 'asaDi' : 'asaEi', pivot: [0, 0, 0],
        boxes: [{ x: s < 0 ? -3.4 : 0, y: -0.15, z: -0.4, w: 3.4, h: 0.3, d: 3.2, p: P.wing }],
        kids: low ? [] : [{ name: s < 0 ? 'pontaD' : 'pontaE', pivot: [s * 3.4, 0, 0], boxes: [{ x: s < 0 ? -3.2 : 0, y: -0.12, z: -0.2, w: 3.2, h: 0.24, d: 2.6, p: P.wing }] }],
      }],
    });
    const legK = s => ({ name: s < 0 ? 'patD' : 'patE', pivot: [s * 0.7, 1.4, 0.2], boxes: [{ x: -0.25, y: 0, z: -0.25, w: 0.5, h: 3.4, d: 0.5, p: P.leg }, { x: -0.5, y: 3.2, z: -1.1, w: 1, h: 0.3, d: 1.6, p: P.leg }] });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'corpo', pivot: [0, 19.2, 0], boxes: [{ x: -1.5, y: -1.6, z: -2.2, w: 3, h: 3, d: 4.6, p: P.body }],
        kids: [wing(-1), wing(1), legK(-1), legK(1),
          { name: 'cabeca', pivot: [0, -1.0, -2.0], boxes: [{ x: -1.3, y: -2.2, z: -2.4, w: 2.6, h: 2.4, d: 2.6, p: P.head }],
            kids: [{ name: 'bico', pivot: [0, -1.0, -2.4], boxes: [{ x: -0.45, y: -0.45, z: -1.7, w: 0.9, h: 0.85, d: 1.7, p: P.beak }, { x: -0.3, y: 0.05, z: -1.3, w: 0.6, h: 0.4, d: 1.2, p: P.beak }] }] },
          { name: 'cauda', pivot: [0, -0.8, 2.3], rot: [0.25, 0, 0], boxes: [{ x: -1.2, y: -0.2, z: 0, w: 2.4, h: 0.4, d: 3, p: P.tail }] }],
      }],
    };
  }

  // fechado: asa vira de pé e deita pra trás ao longo do corpo
  function fold(r, k) {
    r('asaDi', 0, 0, 1.57 * k); r('asaEi', 0, 0, -1.57 * k);
    r('asaD', -1.57 * k, 0, 0); r('asaE', -1.57 * k, 0, 0);
  }
  const FLAP = [[0, -0.6], [0.3, 0.7], [0.4, 0.75], [0.92, -0.45]];
  function flap(r, ph, amt) {
    const k = loop(FLAP, ph) * amt;
    r('asaDi', 0, 0, k); r('asaEi', 0, 0, -k);
    r('pontaD', 0, 0, 0.5 * loop(FLAP, ph - 0.08) * amt); r('pontaE', 0, 0, -0.5 * loop(FLAP, ph - 0.08) * amt);
  }

  // bando: corvinhos simples (corpo + duas asas batendo), cada um com sua rota
  function flock(n, seed) {
    const R = FX.rnd(seed), g = new THREE.Group(), list = [];
    const mat = new THREE.MeshLambertMaterial({ color: 0x14141c }), eye = new THREE.MeshBasicMaterial({ color: 0xd01820 });
    for (let i = 0; i < n; i++) {
      const b = new THREE.Group();
      b.add(new THREE.Mesh(new THREE.BoxGeometry(0.09, 0.08, 0.24), mat));
      const h = new THREE.Mesh(new THREE.BoxGeometry(0.08, 0.08, 0.08), mat); h.position.set(0, 0.03, -0.14); b.add(h);
      if (R() < 0.5) { const e = new THREE.Mesh(new THREE.BoxGeometry(0.084, 0.02, 0.02), eye); e.position.set(0, 0.045, -0.15); b.add(e); }
      const wl = [];
      for (const s of [-1, 1]) {
        const p = new THREE.Group(); p.position.set(s * 0.045, 0.02, 0);
        const w = new THREE.Mesh(new THREE.BoxGeometry(0.26, 0.01, 0.13), mat); w.position.x = s * 0.13; p.add(w); b.add(p); wl.push([p, s]);
      }
      g.add(b);
      list.push({ b, wl, a0: R() * 6.28, rr: 0.3 + R() * 0.9, h: 0.6 + R() * 1.4, lag: R() * 0.25, fl: 3 + R() * 2, sp: R() * 6.28, dz: (R() - 0.5) * 0.6 });
    }
    // k: 0 = junto no corvo, 1 = espalhado atravessando o alvo
    g.at = (from, to, f, t) => {
      g.visible = true;
      for (const p of list) {
        const u = FX.clamp01((f - p.lag) / (1 - p.lag));
        const spread = Math.sin(Math.PI * u);
        const base = from.clone().lerp(to, u);
        const a = p.a0 + u * 5;
        const pos = base.add(new THREE.Vector3(Math.cos(a) * p.rr * spread, (p.h - 1.2) * spread * 0.8, Math.sin(a) * p.rr * spread * 0.5 + p.dz * spread));
        const ahead = from.clone().lerp(to, Math.min(1, u + 0.02)).add(new THREE.Vector3(Math.cos(a + 0.1) * p.rr * spread, (p.h - 1.2) * spread * 0.8, Math.sin(a + 0.1) * p.rr * spread * 0.5 + p.dz * spread));
        p.b.position.copy(pos); p.b.lookAt(ahead); p.b.rotateY(Math.PI);
        p.b.visible = u > 0 && u < 1;
        const k = Math.sin(t * p.fl * 6.28 + p.sp);
        for (const [w, s] of p.wl) w.rotation.z = s * 0.8 * k;
      }
    };
    return g;
  }
  // penas pretas caindo, cada uma balançando no seu ritmo
  function feathers(n, seed) {
    const R = FX.rnd(seed), g = new THREE.Group(), list = [];
    const mat = new THREE.MeshLambertMaterial({ color: 0x18181f, side: THREE.DoubleSide });
    for (let i = 0; i < n; i++) {
      const m = new THREE.Mesh(new THREE.PlaneGeometry(0.05, 0.16), mat); g.add(m);
      list.push({ m, x: (R() - 0.5) * 3, z: (R() - 0.5) * 3, y0: 1.5 + R() * 1.8, sp: 0.35 + R() * 0.3, w: R() * 6.28, f: 1.5 + R() * 1.5 });
    }
    g.at = (c, age) => {
      g.visible = true;
      for (const p of list) {
        const y = p.y0 - age * p.sp;
        p.m.visible = y > 0.02 && age > 0;
        p.m.position.set(c.x + p.x + Math.sin(age * p.f + p.w) * 0.15, y, c.z + p.z);
        p.m.rotation.set(0.5 * Math.sin(age * p.f + p.w), p.w, 0.7 * Math.cos(age * p.f + p.w));
      }
    };
    return g;
  }
  // tomoe girando no ar entre o corvo e você (o "olhar")
  function tomoeRing() {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, side: THREE.DoubleSide, uniforms: { uA: { value: 0 }, uR: { value: 0 } },
      vertexShader: 'varying vec2 vP; void main(){ vP=position.xy; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: `varying vec2 vP; uniform float uA, uR;
        void main(){ float r=length(vP); float a=atan(vP.y,vP.x)+uR;
          float ring=smoothstep(0.05,0.0,abs(r-0.55));
          float tm=0.0; for(int i=0;i<3;i++){ float ta=a-float(i)*2.0944; vec2 c=vec2(cos(ta),sin(ta))*0.55; float d=length(vec2(cos(a),sin(a))*r-c);
            tm=max(tm, smoothstep(0.13,0.1,length(vP-vec2(cos(float(i)*2.0944-uR),sin(float(i)*2.0944-uR))*0.55))); }
          float core=smoothstep(0.12,0.1,r);
          float k=max(max(ring,tm),core);
          float glow=exp(-abs(r-0.55)*6.0)*0.35;
          gl_FragColor=vec4(mix(vec3(0.85,0.05,0.08),vec3(0.05,0.0,0.0),k), (k*0.95+glow)*uA); }`,
    });
    const m = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), mat);
    m.at = (p, s, a, rot, cam) => { m.visible = a > 0.01; m.position.copy(p); m.scale.setScalar(s); mat.uniforms.uA.value = a; mat.uniforms.uR.value = rot; if (cam) m.quaternion.copy(cam.quaternion); };
    return m;
  }

  function anim(P, t, mode, a, obj, ent, ctx) {
    const r = (k, x, y, z) => { if (!P[k]) return; const o = P[k].obj.rotation; o.x += x; o.y += y; o.z += z; };
    if (mode === 'parado') {
      fold(r, 1);
      // cabeça em estalos: vira, segura, inclina, segura (corvo não mexe a cabeça devagar)
      const hy = seq([[0, 0], [0.18, 0], [0.2, 0.6], [0.45, 0.6], [0.47, -0.45], [0.7, -0.45], [0.72, 0]], fr(t * 0.3));
      const hz = seq([[0, 0], [0.5, 0], [0.52, 0.35], [0.66, 0.35], [0.68, 0]], fr(t * 0.3));
      r('cabeca', 0, hy, hz);
      r('cauda', 0.15 * seq([[0, 0], [0.3, 0], [0.32, 1], [0.36, 0]], fr(t * 0.5)), 0, 0);  // abana o rabo de repente
      r('corpo', -0.1, 0, 0);
      return;
    }
    if (mode === 'andar') {
      // pulinhos: agacha, salta, pousa
      fold(r, 1);
      const ph = fr(t * 1.4);
      const up = seq([[0.2, 0], [0.35, 1, 'out'], [0.55, 0, 'in']], ph);
      const crouch = seq([[0, 0], [0.15, 1], [0.22, 0], [0.55, 0], [0.62, 1], [0.8, 0]], ph);
      obj.position.y += 0.2 * up - 0.03 * crouch;
      obj.position.z -= 0.35 * (Math.floor(t * 1.4) % 4 + seq([[0.2, 0], [0.55, 1]], ph)) - 0.7;
      r('patD', 0.5 * up, 0, 0); r('patE', 0.5 * up, 0, 0);
      r('corpo', -0.15 + 0.15 * crouch, 0, 0);
      r('asaDi', 0, 0, -0.3 * up); r('asaEi', 0, 0, 0.3 * up);
      return;
    }
    const R = ctx.ref;
    if (mode === 'genjutsu') {
      const T = t % 4.4;
      R.place(0, -2.6, 0);
      // pousado, encara: asas meio abertas, o Sharingan acende, o tomoe gira entre vocês e o mundo fica vermelho
      const open = seq([[0.1, 0], [0.4, 0.55, 'out'], [3.5, 0.55], [3.9, 0]], T);
      const stare = seq([[0.35, 0], [0.6, 1], [3.4, 1], [3.8, 0]], T);
      fold(r, 1 - open);
      r('corpo', -0.25 * stare, 0, 0); r('cabeca', 0.2 * stare, 0, 0);
      r('cauda', 0.35 * open, 0, 0);
      const spin = Math.max(0, T - 0.6) * 4;
      const ring = seq([[0.55, 0], [0.9, 1, 'out'], [3.3, 1], [3.7, 0]], T);
      ctx.get('tomoe', tomoeRing).at(new THREE.Vector3(0, 1.4, -1.3), 0.5 + 0.25 * ring, ring * 0.85, spin, ctx.camera);
      const g = seq([[0.9, 0], [1.4, 1], [3.2, 1], [3.8, 0]], T);
      if (g > 0) {
        ctx.dark(0.6 * g); ctx.dizzy(g);
        ctx.get('penas', () => feathers(46, 151)).at(new THREE.Vector3(0, 0, -2.6), T - 0.9);
        R.lean(0.12 * g + 0.04 * Math.sin(T * 3)); R.armsOut(0.15 * g);
      }
      return;
    }
    if (mode === 'revoada') {
      const T = t % 3.6;
      R.place(0, -3.2, 0);
      // abre as asas, explode num bando que voa em espiral atravessando você, e se junta de novo atrás
      const open = seq([[0.05, 0], [0.3, 1, 'snap']], T);
      const f = seq([[0.45, 0], [2.4, 1, 'io']], T);
      const out = T > 0.45 && T < 2.5;
      obj.visible = !out;
      if (!out) {
        fold(r, T < 0.45 ? 1 - open : 0);
        flap(r, T * 2.4, T < 0.45 ? open * 0.8 : 0.6);
        if (T >= 2.4) { obj.position.z -= 6.2; obj.rotation.y = Math.PI; fold(r, seq([[2.5, 0], [2.9, 1]], T)); }
        r('corpo', -0.3 * open, 0, 0);
      }
      if (T > 0.4 && T < 2.6) {
        ctx.get('bando', () => flock(30, 152)).at(new THREE.Vector3(0, 1.6, 0), new THREE.Vector3(0, 1.6, -6.2), f, T);
        ctx.get('penas', () => feathers(28, 153)).at(new THREE.Vector3(0, 0, -1.5), T - 0.4);
      }
      if (T > 0.45 && T < 0.6) ctx.get('estalo', () => FX.debris(20, 154, [[20, 20, 28], [30, 28, 40], [12, 12, 16]])).at(new THREE.Vector3(0, 1.5, 0), T - 0.45);
      const hit = seq([[1.0, 0], [1.25, 1], [1.8, 1], [2.1, 0]], T);
      if (hit > 0) {
        R.armsOut(0.6 * hit); R.lean(-0.25 * hit + 0.06 * Math.sin(T * 25) * hit);
        if (T > 1.1 && T < 1.9) ctx.slash(new THREE.Vector3(0.15 * Math.sin(T * 9), 1.4, -3.1), fr((T - 1.1) / 0.25) * 1.3);
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const SK = [
    { nome: 'Preto', seed: 1, black: [30, 30, 38], sheen: [70, 80, 130] },
    { nome: 'Roxo', seed: 2, black: [34, 28, 40], sheen: [110, 70, 130] },
  ];
  window.MOBS.push({
    id: 'corvo_uchiha', nome: 'Corvo Uchiha', bioma: 'Vale do Fim', tier: 'Fraco · tier 10',
    variants: SK.map(v => ({ nome: v.nome, v, def: defOf(v, false), low: defOf(v, true) })),
    anim, modes: [['parado', 'Pousado'], ['andar', 'Pular'], ['genjutsu', 'Genjutsu'], ['revoada', 'Revoada']],
    cam: 3.2, lookY: 0.5, lookYs: { genjutsu: 1.1, revoada: 1.3 }, lookZ: { genjutsu: -1.3, revoada: -3 }, camFar: { genjutsu: 6, revoada: 9 }, camYaw: { genjutsu: -1.0, revoada: -1.35 }, refX: 1.6,
    info: {
      'Tamanho': '0,5 bloco (1,3 de asa aberta)',
      'Vida': '15 (tier 10)',
      'Jeito': 'Neutro. Pousa nas cabeças das estátuas e nas pedras perto da cachoeira, sempre olhando você. Ataca se você chegar a 4 blocos ou atacar outro corvo.',
      'Genjutsu': 'Encara 0,5 s (o olho acende) e o tomoe gira entre vocês: 2 s tonto, a tela escurece em vermelho e caem penas. Olhar pra longe dele no aviso evita.',
      'Revoada': 'Vira um bando de 30 corvos que atravessa você bicando (dano pequeno várias vezes) e se junta de novo 6 blocos atrás.',
      'Solta': 'Pena de corvo; muito raro: olho do corvo (item de genjutsu).',
    },
  });
})();
