// Sapo Ninja (Monte Myoboku). Fraco, tier 10, neutro. Cada um sai diferente: cor, manchas, verrugas e acessório sorteados.
// Sapo Gigante (Monte Myoboku). Forte, tier 5, só no Pico do Sábio (estrutura). Monge guerreiro com bastão de argolas.
// Sapo Ninja: Bala d'Água (incha as bochechas e cospe uma bola d'água que empurra) e Língua (chicoteia e puxa).
// Sapo Gigante: Salto Esmagador (pula alto e cai com o bastão: onda de choque) e Canhão d'Água (bola d'água do tamanho de uma casa).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const hash = (a, b) => fr(Math.sin(a * 127.1 + b * 311.7) * 43758.5453);

  function variant(seed) {
    const R = FX.rnd(seed);
    const pick = a => a[Math.floor(R() * a.length)];
    const [skin, spot, belly] = pick([
      [[222, 110, 46], [150, 50, 30], [244, 214, 160]],
      [[96, 150, 70], [50, 84, 40], [220, 226, 170]],
      [[230, 190, 70], [170, 110, 40], [250, 236, 190]],
      [[84, 120, 170], [40, 60, 104], [214, 224, 236]],
      [[176, 70, 70], [104, 30, 40], [236, 200, 180]],
      [[150, 130, 96], [90, 74, 52], [226, 210, 176]],
    ]);
    return { seed, skin, spot, belly, spots: 0.55 + R() * 0.25, warts: R() < 0.6, acc: pick(['bandana', 'colete', 'pergaminho', 'nada']), cloth: pick([[50, 60, 120], [130, 40, 40], [60, 90, 60], [70, 60, 50]]), eye: pick([[240, 200, 60], [240, 140, 50]]) };
  }

  function painters(v) {
    const S = v.seed;
    const skin = (s, k = 1) => q => {
      let c = cel(mul(v.skin, k), n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S), 0.86, 1.1);
      const sp = n3(q.ax * 0.18 + 3, q.ay * 0.18, q.az * 0.18, s + 20 + S);
      if (sp > v.spots && q.f !== 'bottom') c = mix(c, v.spot, 0.75);
      if (v.warts && q.f !== 'bottom' && hash(Math.floor(q.ax * 0.8), Math.floor(q.az * 0.8) + Math.floor(q.ay * 0.8) * 7 + s) > 0.93) c = mul(c, 1.18);
      if (q.f === 'bottom' || (q.f !== 'top' && q.ny > 0.7 && s === 1)) c = cel(v.belly, n3(q.ax, q.ay, q.az, s + 30), 0.9, 1.05);
      if (q.f === 'top') c = mul(c, 1.06);
      return c;
    };
    const P = { body: skin(1), leg: skin(2), thigh: skin(3), hand: skin(4, 0.95) };
    P.head = q => {
      let c = skin(5)(q);
      // boca larga: linha escura em volta da frente e dos lados, logo abaixo da metade
      if ((q.f === 'north' && Math.abs(q.ny - 0.78) < 0.06) || ((q.f === 'west' || q.f === 'east') && Math.abs(q.ny - 0.78) < 0.06 && q.nz < 0.7)) c = mul(v.spot, 0.6);
      if (q.f === 'north' && q.ny < 0.35 && (Math.abs(q.nx - 0.4) < 0.03 || Math.abs(q.nx - 0.6) < 0.03) && q.ny > 0.2) c = mul(c, 0.6); // narinas
      return c;
    };
    P.jaw = q => (q.f === 'top' ? [196, 90, 100] : skin(6)(q));
    // olho saltado: íris amarela com pupila em barra deitada
    P.eye = q => {
      if (q.f === 'bottom') return skin(7)(q);
      if (q.f === 'top' && (q.nx < 0.15 || q.nx > 0.85 || q.nz < 0.15 || q.nz > 0.85)) return skin(7)(q);
      if (q.f === 'south') return skin(7)(q);
      let c = v.eye;
      if (q.f === 'north' || q.f === 'west' || q.f === 'east') {
        const u = q.f === 'north' ? q.nx : q.nz;
        if (Math.abs(q.ny - 0.55) < 0.07 && Math.abs(u - 0.5) < 0.22) c = [20, 16, 14];
        if (q.ny < 0.2) c = skin(7)(q);
      }
      if (q.f === 'top') c = mix(skin(7)(q), v.eye, 0.4);
      return c;
    };
    const clothP = s => q => { let c = cel(v.cloth, n3(q.ax * 0.7, q.ay * 0.7, q.az * 0.7, s), 0.82, 1.12); if (fr(q.ax * 0.4 + q.ay * 0.4) < 0.1) c = mul(c, 0.86); return c; };
    P.band = q => { let c = clothP(40)(q); if (q.f === 'north' && Math.abs(q.nx - 0.5) < 0.2) c = mix([160, 166, 176], [220, 224, 232], n3(q.ax, q.ay, 0, 41)); return c; };
    P.vest = q => { const c = clothP(42)(q); if (q.f === 'top' || q.f === 'bottom') return c; if (q.f === 'north' && Math.abs(q.nx - 0.5) < 0.08) return skin(1)(q); return c; };
    P.scroll = q => {
      if (q.f === 'west' || q.f === 'east') { const rr = Math.hypot(q.nz - 0.5, q.ny - 0.5); return rr < 0.18 ? [120, 90, 60] : fr(rr * 6) < 0.3 ? [200, 180, 140] : [232, 216, 180]; }
      let c = mix([226, 210, 170], [244, 232, 200], n3(q.ax, q.ay, q.az, 43));
      if (Math.abs(q.nx - 0.5) < 0.05) c = [150, 40, 36];                                           // fita
      return c;
    };
    // gigante: armadura de placas laqueadas, corda sagrada, bastão de argolas
    P.armor = q => { let c = cel([120, 36, 34], n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, 44), 0.8, 1.15); if (fr(q.ay * 0.25) < 0.12) c = [40, 30, 28]; if (fr(q.ay * 0.25) < 0.12 && fr(q.ax * 0.12) < 0.1) c = [200, 170, 80]; return c; };
    P.rope = q => { let c = mix([220, 200, 150], [246, 234, 196], n3(q.ax, q.ay, q.az, 45)); if (fr(q.ax * 0.3 + q.ay * 0.3 + q.az * 0.3) < 0.3) c = mul(c, 0.82); return c; };
    P.paper = q => { let c = [246, 244, 236]; if (q.ny > 0.92) c = [210, 206, 196]; return c; };
    P.staff = q => cel([86, 58, 36], n3(q.ax * 0.5, q.ay * 0.1, q.az * 0.5, 46), 0.8, 1.15);
    P.gold = q => cel([200, 164, 70], n3(q.ax * 1.4, q.ay * 1.4, q.az * 1.4, 47), 0.7, 1.3);
    return P;
  }

  // sapo em escala K (1 = sapo ninja de 0,6 bloco). Pés sempre em y=24.
  function toadDef(v, K, low, giant) {
    const P = painters(v);
    const s = (x, y, z, w, h, d, p) => ({ x: x * K, y: y * K, z: z * K, w: w * K, h: h * K, d: d * K, p });
    const pv = (x, y, z) => [x * K, y * K, z * K];
    const arm = sd => ({
      name: sd < 0 ? 'bracoD' : 'bracoE', pivot: pv(sd * 4, 1, -3.4), rot: [0.15, 0, sd * 0.1],
      boxes: [s(-1.1, 0, -1.1, 2.2, 5.4, 2.2, P.leg)],
      kids: [{ name: sd < 0 ? 'maoD' : 'maoE', pivot: pv(0, 5.2, 0), boxes: [s(-1.6, 0, -2.4, 3.2, 0.8, 3.2, P.hand)], kids: [] }],
    });
    const thigh = sd => ({
      name: sd < 0 ? 'coxaD' : 'coxaE', pivot: pv(sd * 4.6, 1, 2.6), rot: [0, 0, 0],
      boxes: [s(sd < 0 ? -2.8 : -1.2, -1.5, -3.5, 4, 4.4, 7.4, P.thigh)],
      kids: [{ name: sd < 0 ? 'peD' : 'peE', pivot: pv(sd * 0.4, 2.9, -3.0), boxes: [s(-1.8, 0, -3.2, 3.6, 0.8, 4, P.hand)] }],
    });
    const headKids = [
      { name: 'mandibula', pivot: pv(0, 0.6, -1), boxes: [s(-5.2, 0, -5.4, 10.4, 1.8, 5.8, P.jaw)] },
      { name: 'olhoD', pivot: pv(-2.8, -3.6, -3.2), boxes: [s(-1.6, -2.4, -1.6, 3.2, 2.6, 3.2, P.eye)] },
      { name: 'olhoE', pivot: pv(2.8, -3.6, -3.2), boxes: [s(-1.6, -2.4, -1.6, 3.2, 2.6, 3.2, P.eye)] },
    ];
    const headBoxes = [s(-5.5, -4, -6.4, 11, 4.6, 7, P.head)];
    if (!giant && v.acc === 'bandana') headBoxes.push(s(-5.7, -3.9, -6.6, 11.4, 1.2, 7.4, P.band));
    const bodyBoxes = [s(-5, -4, -4.6, 10, 7.4, 10.6, P.body)];
    if (!giant && v.acc === 'colete') bodyBoxes.push(s(-5.3, -4.3, -4.4, 10.6, 5.2, 10.2, P.vest));
    if (!giant && v.acc === 'pergaminho') bodyBoxes.push(s(-6.5, -6.3, 0.5, 13, 2.6, 2.6, P.scroll));
    const kids = [arm(-1), arm(1), thigh(-1), thigh(1), { name: 'cabeca', pivot: pv(0, -1.6, -4.4), boxes: headBoxes, kids: low ? headKids.slice(0, 1) : headKids }];
    if (giant) {
      bodyBoxes.push(s(-5.2, -1.2, -4.8, 10.4, 1.4, 11, P.rope));                                      // corda sagrada na cintura
      if (!low) for (const x of [-3, 0, 3]) kids.push({ name: 'papel' + x, pivot: pv(x, 0.2, -4.9), boxes: [s(-0.6, 0, -0.1, 1.2, 2.6, 0.2, P.paper)] });
      for (const sd of [-1, 1]) kids.push({ name: sd < 0 ? 'ombD' : 'ombE', pivot: pv(sd * 4.2, -3.8, -2.6), rot: [0, 0, sd * 0.35], boxes: [s(sd < 0 ? -3.4 : -0.6, -0.8, -2.4, 4, 1.4, 5.6, P.armor)] });
      // bastão de argolas na mão direita, em pé
      const staff = { name: 'bastao', pivot: pv(0, 0.4, -1), boxes: [s(-0.35, -14, -0.35, 0.7, 20, 0.7, P.staff), s(-0.6, -15.5, -0.6, 1.2, 1.6, 1.2, P.gold), s(-1.9, -17.6, -0.15, 3.8, 0.3, 0.3, P.gold), s(-1.9, -17.6, -0.15, 0.3, 2.4, 0.3, P.gold), s(1.6, -17.6, -0.15, 0.3, 2.4, 0.3, P.gold), s(-0.15, -18.6, -0.15, 0.3, 1.2, 0.3, P.gold)],
        kids: low ? [] : [-1.75, -1.2, 1.2, 1.75].map((x, i) => ({ name: 'argola' + i, pivot: pv(x, -15.4 + (i % 2) * 0.4, 0), boxes: [s(-0.15, 0, -0.5, 0.3, 1.0, 1.0, P.gold)] })) };
      kids[0].kids[0].kids.push(staff);
    }
    return { name: 'raiz', pivot: [0, 0, 0], kids: [{ name: 'corpo', pivot: [0, 24 - 4.6 * K, 0], boxes: bodyBoxes, kids }] };
  }

  // língua: tira rosa esticada da boca até o alvo, ponta grossa e pegajosa
  function tongue() {
    const g = new THREE.Group();
    const mat = new THREE.MeshLambertMaterial({ color: 0xd8607a });
    const band = new THREE.Mesh(new THREE.BoxGeometry(0.07, 0.025, 1), mat); g.add(band);
    const tip = new THREE.Mesh(new THREE.BoxGeometry(0.16, 0.07, 0.14), new THREE.MeshLambertMaterial({ color: 0xe8788c })); g.add(tip);
    g.at = (a, b, k, sag) => {
      g.visible = k > 0.01;
      const end = a.clone().lerp(b, k);
      const mid = a.clone().lerp(end, 0.5); mid.y -= sag;
      band.position.copy(mid); band.scale.z = a.distanceTo(end); band.lookAt(end);
      tip.position.copy(end); tip.lookAt(a);
    };
    return g;
  }
  // bola d'água: casca com borda clara (fresnel) e ondas na superfície
  function waterBall() {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, uniforms: { uT: { value: 0 }, uA: { value: 1 } },
      vertexShader: `uniform float uT; varying vec3 vN, vV;
        void main(){ vec3 p=position; p+=normal*0.06*sin(p.y*9.0+uT*14.0)*sin(p.x*7.0-uT*9.0);
          vec4 mv=modelViewMatrix*vec4(p,1.0); vN=normalize(normalMatrix*normal); vV=normalize(-mv.xyz); gl_Position=projectionMatrix*mv; }`,
      fragmentShader: `uniform float uA; varying vec3 vN, vV;
        void main(){ float f=pow(1.0-abs(dot(vN,vV)),2.2); vec3 c=mix(vec3(0.16,0.42,0.7),vec3(0.82,0.94,1.0),f);
          float spec=pow(max(dot(reflect(-vV,vN),normalize(vec3(-0.4,0.8,0.4))),0.0),24.0);
          gl_FragColor=vec4(c+spec, (0.38+0.55*f)*uA); }`,
    });
    const m = new THREE.Mesh(new THREE.SphereGeometry(1, 32, 20), mat);
    m.at = (p, r, t, a = 1, stretch = 1) => { m.visible = r > 0.001; m.position.copy(p); m.scale.set(r / Math.sqrt(stretch), r / Math.sqrt(stretch), r * stretch); mat.uniforms.uT.value = t; mat.uniforms.uA.value = a; };
    return m;
  }
  const spray = seed => FX.debris(46, seed, [[150, 200, 240], [200, 230, 250], [110, 170, 220], [240, 250, 255]]);

  function anim(P, t, mode, a, obj, ent, ctx) {
    const r = (k, x, y, z) => { if (!P[k]) return; const o = P[k].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const K = ent.meta.v.K || 1;
    const giant = K > 2;
    const sc = K / 16;
    // olhos piscam em dois tempos (afunda e volta)
    const blink = seq([[0, 0], [0.9, 0], [0.92, 1], [0.97, 1], [0.99, 0]], fr(t * 0.27 + (ent.meta.v.seed % 7) * 0.1));
    if (P.olhoD) { P.olhoD.obj.position.y += 1.3 * K * blink; P.olhoE.obj.position.y += 1.3 * K * blink; }
    const throat = loop([[0, 0], [0.3, 1], [0.6, 0]], t * 1.1);                           // papo respirando
    if (mode === 'parado') {
      P.mandibula.obj.position.y += 0.25 * K * throat;
      if (giant) { r('bracoD', -0.55, 0, 0.1); r('corpo', -0.42, 0, 0); r('cabeca', 0.3, 0, 0); r('coxaD', 0.4, 0, 0); r('coxaE', 0.4, 0, 0); r('peD', -0.4, 0, 0); r('peE', -0.4, 0, 0); r('bastao', 0.95, 0, 0); r('bracoE', 0.25, 0, -0.05); }
      return;
    }
    if (mode === 'andar') {
      // pulo de sapo: junta, empurra com as coxas, voa baixo, pousa nas mãos
      const ph = fr(t * (giant ? 0.5 : 0.9));
      const push = seq([[0, 0], [0.18, 1, 'snap'], [0.5, 0.2], [0.7, 0]], ph);
      const air = seq([[0.12, 0], [0.35, 1, 'out'], [0.6, 0, 'in']], ph);
      const land = seq([[0.58, 0], [0.64, 1, 'snap'], [0.85, 0]], ph);
      obj.position.y += air * (giant ? 1.4 : 0.35);
      obj.position.z -= (giant ? 3.2 : 0.8) * (seq([[0.12, 0], [0.6, 1]], ph) + (Math.floor(t * (giant ? 0.5 : 0.9)) % 3)) - (giant ? 4 : 1.2);
      r('corpo', -0.35 * air + 0.15 * land, 0, 0);
      r('coxaD', 0.9 * push, 0, 0); r('coxaE', 0.9 * push, 0, 0); r('peD', 0.6 * push, 0, 0); r('peE', 0.6 * push, 0, 0);
      r('bracoD', -0.8 * air + 0.3 * land, 0, 0); r('bracoE', -0.8 * air + 0.3 * land, 0, 0);
      if (giant) r('bastao', 0.95, 0, 0);
      return;
    }
    const R = ctx.ref;
    if (mode === 'bala') {
      const T = t % 3.2;
      R.place(0, -4.2, 0);
      // levanta a cabeça e infla o papo (aviso), abaixa e cospe
      const inflate = seq([[0.1, 0], [0.7, 1, 'in'], [0.78, 0, 'snap']], T);
      const spit = seq([[0.72, 0], [0.8, 1, 'snap'], [1.1, 1], [1.4, 0]], T);
      r('cabeca', -0.35 * inflate + 0.12 * spit, 0, 0); r('corpo', -0.2 * inflate + 0.1 * spit, 0, 0);
      P.cabeca.obj.scale.set(1 + 0.25 * inflate, 1 + 0.12 * inflate, 1);
      r('mandibula', 0.9 * spit, 0, 0);
      P.mandibula.obj.position.y += 0.6 * inflate;
      r('coxaD', 0.3 * inflate, 0, 0); r('coxaE', 0.3 * inflate, 0, 0);
      const f = (T - 0.78) / 0.3;
      if (f > 0 && f < 1) ctx.get('bola', waterBall).at(new THREE.Vector3(0, 0.45 + 0.6 * f * (1 - f), -0.6 - 3.4 * f), 0.22, T, 1, 1.4);
      if (f >= 1 && f < 4) {
        const h = T - 1.08;
        ctx.get('respingo', () => spray(191)).at(new THREE.Vector3(0, 0.9, -3.9), h);
        ctx.get('nevoa', FX.volume).set(new THREE.Vector3(0, 0.9, -4.0), 0.55, h / 0.9, { seed: 6.1, dens: 2.2, rise: 0.2, smoke: [0.7, 0.82, 0.9] });
        const k = seq([[1.08, 0], [1.4, 1, 'out']], T);
        R.place(0, -4.2 - 1.6 * k, 0); R.lean(-0.5 * Math.sin(Math.PI * FX.clamp01(h / 0.8)));
        if (h < 0.2) ctx.shake(0.4);
      }
      return;
    }
    if (mode === 'lingua') {
      const T = t % 3.0;
      // abre a boca (0,2 s), a língua sai, gruda e puxa você até a frente dele
      const open = seq([[0.15, 0], [0.3, 1, 'snap'], [1.6, 1], [1.8, 0]], T);
      const out = seq([[0.3, 0], [0.45, 1, 'out']], T);
      const pull = seq([[0.6, 0], [1.2, 1, 'in']], T);
      r('mandibula', 0.8 * open, 0, 0); r('cabeca', -0.15 * open, 0, 0);
      r('bracoD', 0.25 * open, 0, 0); r('bracoE', 0.25 * open, 0, 0);
      const refZ = -3.6 + 2.5 * pull;
      R.place(0, refZ, 0);
      obj.updateMatrixWorld(true);
      const mouth = P.mandibula.obj.localToWorld(new THREE.Vector3(0, 0.5, -4));
      const tgt = new THREE.Vector3(0, 1.0 - 0.1 * pull, refZ + 0.15);
      const k = T < 1.5 ? out : 1 - seq([[1.5, 0], [1.65, 1, 'in']], T);
      ctx.get('lingua', tongue).at(mouth, tgt, k, 0.06 * (1 - out) + 0.02);
      if (pull > 0 && T < 1.8) { R.lean(0.4 * pull); R.armsOut(0.3); }
      if (T > 1.6 && T < 1.9) ctx.slash(new THREE.Vector3(0, 1.0, refZ + 0.2), (T - 1.6) / 0.3);    // tapa de língua no fim
      return;
    }
    if (mode === 'salto') {
      const T = t % 4.4;
      // agacha fundo (aviso: 0,6 s), pula alto, a sombra cresce no chão, cai batendo o bastão; onda de choque em anel
      const crouch = seq([[0.1, 0], [0.6, 1, 'in'], [0.7, 0, 'snap'], [1.75, 0], [1.8, 1, 'snap'], [2.4, 0, 'io']], T);
      const air = seq([[0.68, 0], [1.2, 1, 'out'], [1.35, 1], [1.8, 0, 'in']], T);
      const fwd = seq([[0.68, 0], [1.8, 1]], T);
      obj.position.y += 7 * air - 0.3 * crouch;
      obj.position.z -= 6 * fwd;
      r('corpo', -0.42 + 0.3 * crouch - 0.3 * air, 0, 0); r('cabeca', 0.3, 0, 0);
      r('coxaD', 0.4 + 0.5 * crouch - 0.6 * air, 0, 0); r('coxaE', 0.4 + 0.5 * crouch - 0.6 * air, 0, 0); r('peD', -0.4, 0, 0); r('peE', -0.4, 0, 0);
      const lift = seq([[0.9, 0], [1.3, 1], [1.75, 1], [1.82, 0, 'snap']], T);
      r('bracoD', -0.55 - 1.9 * lift + 0.4 * crouch * (T > 1.7 ? 1 : 0), 0, 0.1); r('bastao', 0.95 - 0.3 * lift, 0, 0);
      r('bracoE', 0.25 - 1.4 * air, 0, -0.3 * air);
      R.place(0, -9.4, 0);
      if (T < 1.8 && T > 0.7) ctx.get('sombra', () => { const m = new THREE.Mesh(new THREE.CircleGeometry(1, 40), new THREE.MeshBasicMaterial({ color: 0x000000, transparent: true, opacity: 0.35, depthWrite: false })); m.rotation.x = -Math.PI / 2; m.at = (p, s2) => { m.visible = true; m.position.copy(p); m.scale.setScalar(s2); }; return m; }).at(new THREE.Vector3(0, 0.02, -6), 1 + 2.5 * fwd);
      if (T > 1.8) {
        const h = T - 1.8;
        ctx.roarRing(new THREE.Vector3(0, 0.05, -6.5), h * 1.6);
        ctx.get('poeira', FX.volume).set(new THREE.Vector3(0, 0.6, -6.5), 3.2, h / 2.2, { seed: 7.3, dens: 2.6, rise: 0.25, squash: 0.35, freq: 1.4, smoke: [0.56, 0.5, 0.38] });
        ctx.get('pedras', () => FX.debris(60, 192, [[110, 100, 84], [90, 80, 66], [140, 128, 104]])).at(new THREE.Vector3(0, 0.2, -6.5), h);
        if (h < 0.5) ctx.shake(1 - h / 0.5);
        ctx.flash(new THREE.Vector3(0, 1, -6.5), Math.max(0, 0.5 - h));
        const k = seq([[1.8, 0], [2.4, 1, 'out']], T);
        R.place(0, -9.4 - 3 * k, 1.6 * Math.sin(Math.PI * k));
        R.lean(-0.8 * Math.sin(Math.PI * FX.clamp01(h / 1.0))); R.spin(1.2 * k);
      }
      return;
    }
    if (mode === 'canhao') {
      const T = t % 5.0;
      R.place(0, -12, 0);
      // planta o bastão, puxa ar fundo (o papo incha enorme: 1 s de aviso) e cospe uma bola d'água do tamanho de uma casa
      const inflate = seq([[0.2, 0], [1.2, 1, 'in'], [1.3, 0, 'snap']], T);
      const spit = seq([[1.25, 0], [1.33, 1, 'snap'], [2.0, 1], [2.4, 0]], T);
      r('corpo', -0.42 - 0.2 * inflate + 0.2 * spit, 0, 0); r('cabeca', 0.3 - 0.35 * inflate + 0.15 * spit, 0, 0);
      P.cabeca.obj.scale.set(1 + 0.3 * inflate, 1 + 0.15 * inflate, 1);
      P.mandibula.obj.position.y += 0.7 * K * inflate;
      r('mandibula', 0.9 * spit, 0, 0);
      r('bracoD', -0.55, 0, 0.1); r('bastao', 0.95, 0, 0); r('bracoE', 0.25 - 0.4 * spit, 0, -0.2 * spit);
      r('coxaD', 0.4 + 0.25 * inflate, 0, 0); r('coxaE', 0.4 + 0.25 * inflate, 0, 0); r('peD', -0.4, 0, 0); r('peE', -0.4, 0, 0);
      const f = (T - 1.3) / 0.55;
      if (f > 0 && f < 1) {
        ctx.get('bola', waterBall).at(new THREE.Vector3(0, 2.6 - 0.9 * f, -3.6 - 7.6 * f), 1.5 + 0.2 * f, T, 1, 1.25);
        ctx.get('rastro', FX.volume).set(new THREE.Vector3(0, 2.2, -3.6 - 7.6 * f * 0.7), 1.0, f * 0.5, { seed: 8.1, dens: 1.5, rise: 0.1, smoke: [0.72, 0.84, 0.92] });
      }
      if (f >= 1) {
        const h = T - 1.85;
        ctx.get('respingo', () => FX.debris(90, 194, [[150, 200, 240], [200, 230, 250], [110, 170, 220], [240, 250, 255]])).at(new THREE.Vector3(0, 1.4, -11.4), h);
        ctx.get('onda', FX.volume).set(new THREE.Vector3(0, 1.0, -12), 3.0, h / 1.8, { seed: 8.7, dens: 2.4, rise: 0.15, squash: 0.45, smoke: [0.66, 0.8, 0.9] });
        ctx.drown(Math.max(0, 0.8 - h));
        if (h < 0.4) ctx.shake(0.9);
        const k = seq([[1.85, 0], [2.6, 1, 'out']], T);
        R.place(0, -12 - 4.5 * k, 1.0 * Math.sin(Math.PI * k));
        R.lean(-0.9 * Math.sin(Math.PI * FX.clamp01(h / 1.2))); R.spin(-1.6 * k);
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(4), variant(9), variant(17)];
  fixed[0].acc = 'bandana'; fixed[1].acc = 'pergaminho'; fixed[2].acc = 'colete';
  const mkT = v => ({ v, def: toadDef(v, 1, false, false), low: toadDef(v, 1, true, false) });
  window.MOBS.push({
    id: 'sapo_ninja', nome: 'Sapo Ninja', bioma: 'Monte Myoboku', tier: 'Fraco · tier 10',
    variants: fixed.map((v, i) => ({ nome: ['Bandana', 'Pergaminho', 'Colete'][i], ...mkT(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', ...mkT(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Pular'], ['bala', "Bala d'Água"], ['lingua', 'Língua']],
    cam: 3.4, lookY: 0.45, lookYs: { bala: 0.8, lingua: 0.8 }, lookZ: { bala: -2, lingua: -1.6, andar: -1 }, camFar: { bala: 6.5, lingua: 5.5, andar: 4.5 }, camYaw: { bala: -1.25, lingua: -1.3 }, refX: 1.6,
    info: {
      'Tamanho': '0,6 bloco (sentado)',
      'Vida': '25 (tier 10)',
      'Jeito': 'Neutro, em grupos de 2 a 5 nas folhas gigantes e na beira do óleo. Cada um sai diferente: cor, manchas, verrugas, cor dos olhos e acessório (bandana, colete ou pergaminho nas costas).',
      "Bala d'Água": 'Levanta a cabeça e infla o papo (0,6 s de aviso) e cospe uma bola d\'água que te empurra 1,5 bloco.',
      'Língua': 'Abre a boca (0,2 s) e a língua gruda em você a até 4 blocos: te puxa até ele e dá um tapa.',
      'Solta': 'Óleo de Sapo (combustível; com fogo vira Óleo em Chamas), pele de sapo.',
    },
  });
  const G = [{ ...variant(60), skin: [196, 100, 52], spot: [120, 44, 30], belly: [236, 206, 156], spots: 0.62, warts: true, acc: 'nada', K: 5.2 },
    { ...variant(61), skin: [104, 120, 80], spot: [60, 70, 44], belly: [214, 210, 170], spots: 0.6, warts: true, acc: 'nada', K: 5.2 }];
  window.MOBS.push({
    id: 'sapo_gigante', nome: 'Sapo Gigante', bioma: 'Monte Myoboku', tier: 'Forte, só no Pico · tier 5',
    variants: G.map((v, i) => ({ nome: ['Monge laranja', 'Monge musgo'][i], v, def: toadDef(v, v.K, false, true), low: toadDef(v, v.K, true, true) })),
    anim, modes: [['parado', 'Parado'], ['andar', 'Pular'], ['salto', 'Salto Esmagador'], ['canhao', "Canhão d'Água"]],
    cam: 13, lookY: 2.2, lookYs: { salto: 3 }, lookZ: { salto: -5, canhao: -6, andar: -3 }, camFar: { salto: 20, canhao: 22, andar: 18 }, camYaw: { parado: -0.7, salto: -1.25, canhao: -1.3 }, refX: 5,
    info: {
      'Tamanho': '3,5 blocos de altura, 4 de largura',
      'Vida': '650 (tier 5)',
      'Jeito': 'Só no Pico do Sábio (estrutura), um por mundo. Medita de olhos fechados; acorda se você chegar no topo ou bater num sapo ninja perto dele. Volta 3 dias depois de morrer.',
      'Salto Esmagador': 'Agacha fundo (0,6 s de aviso), pula 7 blocos e cai até 6 blocos à frente batendo o bastão: a sombra no chão mostra onde. Onda de choque de 5 blocos que joga longe e quebra blocos fracos.',
      "Canhão d'Água": 'Planta o bastão e infla o papo enorme (1 s de aviso), cospe uma bola d\'água de 3 blocos que voa 8 blocos e estoura: joga 4,5 blocos e apaga fogo.',
      'Solta': 'Bastão de argolas (arma), muito Óleo de Sapo, pergaminho de invocação dos sapos.',
    },
  });
})();
