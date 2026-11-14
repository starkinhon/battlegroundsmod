// Dagon Embrião (Mar de Dagon). Tier 7. O Dagon antes de amadurecer: o medo do mar ainda dentro do ovo. Cabeça
// enorme e mole de polvo, olhinhos pequenos e pesados, uma barba de tentáculos no lugar da boca, corpo baixinho de
// barriga redonda, mãos e pés de membrana. Ainda com restos da casca e da gosma do ovo grudados.
// Golpes: Cardume (abre os braços, o chão vira água embaixo dele e os peixes-shikigami saem nadando pra te morder)
// e Maré Amaldiçoada (enche a barriga e cospe um jato de água do mar pela boca de tentáculos).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  // [nome, cabeça, corpo, olho]
  const KINDS = [
    ['Vermelho do Fundo', [150, 62, 56], [206, 170, 150], [236, 196, 70]],
    ['Pálido', [214, 150, 150], [226, 204, 190], [40, 40, 50]],
    ['Abissal', [76, 58, 110], [150, 140, 170], [120, 230, 220]],
  ];
  function variant(seed, kind) {
    const R = FX.rnd(seed);
    const k = kind ?? Math.floor(R() * KINDS.length);
    return { seed, kind: k, head: KINDS[k][1], body: KINDS[k][2], iris: KINDS[k][3], size: 0.95 + R() * 0.1, shell: R() < 0.7, tl: [0, 1, 2, 3, 4, 5].map(() => 0.85 + R() * 0.35) };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.74, hi = 1.18) => {
      const f = 0.62 - 0.3 * q.ny + 0.3 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.22 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const P = {};
    // cabeça de polvo: manchas que mudam de cor (cromatóforos), veias por baixo da pele, brilho de gosma
    const mantle = (q, s) => {
      let c = shade(v.head, q, s);
      const sp = n3(q.ax * 0.32, q.ay * 0.32, q.az * 0.32, 30 + S);
      if (sp > 0.66) c = mul(c, 0.8); else if (sp < 0.3) c = mix(c, [240, 200, 190], 0.16);
      if (Math.abs(n3(q.ax * 0.35, q.ay * 0.35, q.az * 0.35, 31 + S) - 0.5) < 0.025) c = mix(c, [90, 30, 60], 0.5);
      if (n3(q.ax * 0.7, q.ay * 0.7, q.az * 0.7, 32 + S) > 0.82 && q.f !== 'bottom' && q.ny < 0.5) c = mix(c, [250, 240, 240], 0.4);   // gosma brilhando
      return c;
    };
    P.mantle = q => mantle(q, 1);
    P.face = q => {
      let c = mantle(q, 2);
      if (q.f !== 'north') return c;
      // olhinhos pequenos com pálpebra pesada caída por cima (meio fechados, cara de sono/tédio)
      for (const ex of [-2.7, 2.7]) {
        const x = q.ax - ex, y = q.ay - 2.6;
        if (Math.abs(x) < 1.4 && Math.abs(y) < 1.1) {
          if (y < -0.2) return mul(v.head, 0.62);                                                  // pálpebra
          if (Math.abs(x) < 0.5 && y < 0.6) return [10, 6, 10];
          return y > 0.7 ? mul(v.head, 0.5) : v.iris;
        }
        if (Math.abs(x) < 1.7 && y > -1.6 && y < -1.1) return mul(v.head, 0.5);                     // ruga em cima
      }
      return c;
    };
    // pescoço/corpo: pele clara de peixe, escamas miúdas nos ombros, barriga lisa com o umbigo do ovo
    P.body = q => {
      let c = shade(v.body, q, 3);
      if (q.ay < 9.5 && fr(q.ax * 0.6 + (Math.floor(q.ay * 0.6) % 2) * 0.5) < 0.2) c = mul(c, 0.86);
      if (n3(q.ax * 0.9, q.ay * 0.9, q.az * 0.9, 33 + S) > 0.74) c = mix(c, v.head, 0.25);
      return c;
    };
    P.belly = q => {
      let c = shade(mix(v.body, [240, 230, 220], 0.2), q, 4);
      if (q.f === 'north' && Math.hypot(q.ax, q.ay - 15.6) < 0.8) c = [120, 60, 70];                   // umbigo (onde o cordão do ovo prendia)
      if (q.f === 'north' && fr(q.ay * 0.5) < 0.08) c = mul(c, 0.9);
      return c;
    };
    // tentáculos: escuros em cima, ventosas claras embaixo (lado de dentro)
    P.tent = q => {
      let c = shade(mix(v.head, [80, 30, 40], 0.15), q, 5);
      if (q.f === 'south' && fr(q.ay * 0.6) < 0.5) { c = [236, 200, 196]; if (fr(q.ay * 0.6) < 0.2) c = [150, 90, 100]; }
      return c;
    };
    P.limb = q => { let c = shade(v.body, q, 6); if (q.ny > 0.75) c = mix(c, v.head, 0.3); return c; };
    // membrana entre os dedos: translúcida-escura
    P.web = q => { let c = shade(mix(v.body, v.head, 0.4), q, 7); if (q.f === 'north' || q.f === 'south') { if (fr(q.px * 0.7) < 0.2) c = mul(c, 0.75); } return c; };
    P.claw = () => [40, 30, 40];
    // casca do ovo: branca-esverdeada com rachaduras e lodo
    P.shell = q => {
      let c = shade([200, 210, 190], q, 8, 0.8, 1.1);
      if (Math.abs(n3(q.ax * 0.5, q.ay * 0.5, q.az * 0.5, 34 + S) - 0.5) < 0.03) c = [60, 70, 50];
      if (n3(q.ax * 1.2, q.ay * 1.2, q.az * 1.2, 35 + S) > 0.72) c = mix(c, [110, 140, 90], 0.5);
      return c;
    };
    return P;
  }

  function rig(P, v, lo) {
    const tentX = [-3.5, -2.1, -0.7, 0.7, 2.1, 3.5];
    const tents = tentX.map((x, i) => {
      const L = 4.2 * v.tl[i] * (i === 2 || i === 3 ? 1.2 : 1);
      return {
        name: 'tent' + i, pivot: [x, -0.6, -5.2], rot: [-0.12, 0, x * -0.05], boxes: [{ x: -0.75, y: 0, z: -0.75, w: 1.5, h: L, d: 1.5, p: P.tent }],
        kids: [{ name: 'tentb' + i, pivot: [0, L, 0], boxes: [{ x: -0.55, y: 0, z: -0.55, w: 1.1, h: L * 0.9, d: 1.1, p: P.tent }, ...(lo ? [] : [{ x: -0.35, y: L * 0.9, z: -0.35, w: 0.7, h: 1.2, d: 0.7, p: P.tent }])] }],
      };
    });
    const arm = s => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * 5.2, -6, 0], rot: [0, 0, -s * 0.18], boxes: [{ x: -1.4, y: -0.8, z: -1.4, w: 2.8, h: 6, d: 2.8, p: P.limb }],
      kids: [{
        name: s < 0 ? 'maoD' : 'maoE', pivot: [0, 5.2, 0], boxes: [
          { x: -1.6, y: 0, z: -1.6, w: 3.2, h: 2.2, d: 3.2, p: P.limb },
          { x: -1.9, y: 2.2, z: -0.5, w: 3.8, h: 2.4, d: 1, p: P.web },
          ...(lo ? [] : [-1.5, 0, 1.5].map(x => ({ x: x - 0.3, y: 4.6, z: -0.4, w: 0.6, h: 0.8, d: 0.6, p: P.claw }))),
        ],
      }],
    });
    const leg = s => ({
      name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * 2.8, 17.5, 0.5], boxes: [{ x: -1.6, y: 0, z: -1.6, w: 3.2, h: 5.4, d: 3.2, p: P.limb }],
      kids: [{ name: s < 0 ? 'peD' : 'peE', pivot: [0, 5.4, 0], boxes: [{ x: -1.9, y: 0, z: -4.4, w: 3.8, h: 1.1, d: 5.6, p: P.web }, ...(lo ? [] : [{ x: -1.6, y: 0.2, z: -4.9, w: 3.2, h: 0.7, d: 0.6, p: P.claw }])] }],
    });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'quadril', pivot: [0, 15, 0], boxes: [{ x: -5, y: -2, z: -4.2, w: 10, h: 5, d: 8.4, p: P.belly }],
        kids: [
          {
            name: 'tronco', pivot: [0, -1.5, 0], boxes: [{ x: -4.4, y: -6.5, z: -3.6, w: 8.8, h: 6.5, d: 7.2, p: P.body }, ...(v.shell && !lo ? [{ x: 2.2, y: -7, z: -2.8, w: 3.4, h: 1.6, d: 4.6, p: P.shell }] : [])],
            kids: [
              {
                name: 'cabeca', pivot: [0, -6.5, 0], boxes: [
                  // manto de polvo: grande, mole, cai pra trás
                  { x: -5.6, y: -11, z: -5, w: 11.2, h: 11, d: 10, p: P.face },
                  { x: -4.6, y: -13.6, z: -4, w: 9.2, h: 2.6, d: 8, p: P.mantle },
                  ...(lo ? [] : [{ x: -3.4, y: -14.6, z: -2.8, w: 6.8, h: 1, d: 5.6, p: P.mantle }]),
                  ...(v.shell && !lo ? [{ x: -4.8, y: -14.4, z: -1.6, w: 4.4, h: 1.2, d: 4.4, p: P.shell }, { x: -4.2, y: -15.2, z: -1, w: 2.6, h: 0.8, d: 2.6, p: P.shell }] : []),
                ],
                kids: [
                  { name: 'manto', pivot: [0, -8.5, 4.6], rot: [0.35, 0, 0], boxes: [{ x: -4, y: -3.2, z: 0, w: 8, h: 7, d: 5, p: P.mantle }, ...(lo ? [] : [{ x: -2.6, y: -2, z: 5, w: 5.2, h: 5, d: 2.4, p: P.mantle }])] },
                  ...tents,
                ],
              },
              arm(-1), arm(1),
            ],
          },
          leg(-1), leg(1),
        ],
      }],
    };
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size * 1.05; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    // tentáculos sempre vivos: cada um com fase e amplitude próprias, a ponta atrasada em relação à base
    const writhe = (k = 1, spread = 0, curl = 0) => {
      for (let i = 0; i < 6; i++) {
        const ph = t * (2.1 + 0.3 * i) + i * 1.9, sx = i - 2.5;
        r('tent' + i, (0.18 * Math.sin(ph) - spread * 0.9 - curl * 0.4) * k, 0, (0.12 * Math.sin(ph * 0.7 + 1) - sx * 0.14 * spread) * k);
        r('tentb' + i, (0.35 * Math.sin(ph - 1.2) - spread * 0.5 + curl * 1.1) * k, 0, 0.18 * Math.sin(ph * 0.8 - 0.4) * k);
      }
    };
    // cabeça mole: o manto infla e murcha (respiração), balança atrasado
    const breathe = (k = 1) => { const b = Math.sin(t * 1.6); if (P.cabeca) P.cabeca.obj.scale.set(1 + 0.03 * b * k, 1 + 0.04 * b * k, 1 + 0.03 * b * k); r('manto', 0.08 * Math.sin(t * 1.6 - 0.8) * k, 0, 0); };
    const mouth = () => { obj.updateMatrixWorld(true); return new THREE.Vector3(0, -2.2, -5.6).applyMatrix4(P.cabeca.obj.matrixWorld); };

    if (mode === 'parado') {
      // parado balançando de um pé pro outro, como criança pequena; cabeça respira, tentáculos mexem
      writhe(1); breathe(1);
      const sw = Math.sin(t * 1.1);
      r('quadril', 0, 0, 0.05 * sw); r('cabeca', 0.05 * Math.sin(t * 0.7), 0.15 * Math.sin(t * 0.4), -0.06 * sw);
      r('bracoD', 0.1 * Math.sin(t * 1.3), 0, 0.05); r('bracoE', -0.1 * Math.sin(t * 1.3 + 1), 0, -0.05);
      r('maoD', 0.3, 0, 0); r('maoE', 0.3, 0, 0);
      return;
    }
    if (mode === 'andar') {
      // gingado de bebê: perna dura, corpo vira de um lado pro outro, braços abertos pra equilibrar, cabeça pesada atrasada
      const ph = t * 4.2;
      r('pernaD', 0.5 * Math.sin(ph), 0, 0); r('pernaE', -0.5 * Math.sin(ph), 0, 0);
      r('peD', -0.3 * Math.max(0, Math.sin(ph)), 0, 0); r('peE', -0.3 * Math.max(0, -Math.sin(ph)), 0, 0);
      r('quadril', 0, 0.1 * Math.sin(ph), 0.1 * Math.sin(ph)); obj.position.y += 0.04 * Math.abs(Math.cos(ph));
      r('bracoD', 0, 0, 0.45 + 0.1 * Math.sin(ph)); r('bracoE', 0, 0, -0.45 + 0.1 * Math.sin(ph));
      r('cabeca', 0.08, 0, -0.12 * Math.sin(ph - 0.6));
      writhe(1.2); breathe(0.6);
      return;
    }
    if (mode === 'cardume') {
      const T = t % 5.6;
      const R = ctx.ref; const RZ = -3.4; R.place(0, RZ, 0); R.spin(Math.PI);
      // abre os braços, o chão vira água embaixo dele, e os peixes saem nadando dela; mordem e mergulham na areia
      const open = seq([[0.1, 0], [0.7, 1, 'out'], [3.6, 1], [4.4, 0, 'io']], T);
      r('bracoD', -0.4 * open, 0, 1.3 * open); r('bracoE', -0.4 * open, 0, -1.3 * open); r('maoD', 0.6 * open, 0, 0); r('maoE', 0.6 * open, 0, 0);
      r('cabeca', -0.25 * open, 0, 0); r('tronco', -0.1 * open, 0, 0);
      writhe(1, 0.7 * open); breathe(1 + open);
      const pool = FX.clamp01((T - 0.3) / 0.6) * (1 - FX.clamp01((T - 4.6) / 0.8));
      ctx.get('poca', () => JJFX.wet()).at(new THREE.Vector3(0, 0.015, 0), 1.6, 0.25 + 0.75 * pool, pool, t, 5);
      ctx.get('pocaR', () => JJFX.wet()).at(new THREE.Vector3(0, 0.016, RZ), 0.9, FX.clamp01((T - 3.5) / 0.4), FX.clamp01((T - 3.5) / 0.3) * (1 - FX.clamp01((T - 4.8) / 0.6)), t, 8);
      const pk = built.find(b => b.m.id === 'peixe');
      const C = new THREE.Vector3(0, 0, RZ);
      let hit = 0;
      for (let i = 0; i < 5; i++) {
        const src = pk.vs[i % pk.vs.length].hi;
        const f = ctx.get('px' + i + ':' + src.obj.uuid, () => JJFX.posable(src));
        JJFX.dissolve(src, 0);
        f.reset(); f.position.set(0, 1.5, 0); f.rotation.set(0, 0, 0); f.scale.copy(obj.scale);
        const fs = src.v.size * (0.85 + 0.06 * i); f.children[0].scale.setScalar(fs); f.position.y += 1.5 * (fs - 1);
        const born = 0.75 + i * 0.12, dive = 3.55 + i * 0.1;
        f.visible = T > born && T < dive + 0.4;
        if (!f.visible) continue;
        // 1) sai da poça em volta dele subindo; 2) voa em arco até o alvo; 3) roda mordendo; 4) mergulha na areia ao lado do alvo
        const a0 = i * 1.257 + 0.4, up = FX.clamp01((T - born) / 0.35), go = FX.clamp01((T - born - 0.35) / 0.55);
        const ang = a0 + (T - born) * (2.2 + 0.2 * i) * (i % 2 ? 1 : -1);
        const ring = new THREE.Vector3(C.x + Math.cos(ang) * 0.8, 0, C.z + Math.sin(ang) * 0.8);
        const start = new THREE.Vector3(Math.cos(a0) * 0.9, -1.0 + up * 1.0, Math.sin(a0) * 0.9 - 0.2);
        const p = start.clone().lerp(ring, FX.EASE.io(go));
        p.y += 0.6 * Math.sin(Math.PI * go) + (0.2 * Math.sin(T * 2 + i)) * go - 0.1 * (i % 3);
        // mordida na vez de cada um
        const t0 = 1.9 + i * 0.32, b = seq([[t0, 0], [t0 + 0.15, 1, 'in'], [t0 + 0.4, 1], [t0 + 0.6, 0, 'out']], T);
        p.lerp(new THREE.Vector3(C.x, p.y, C.z), 0.68 * b);
        const dv = FX.clamp01((T - dive) / 0.4);
        p.y -= 1.2 * FX.EASE.in(dv);
        f.position.x += p.x; f.position.z += p.z; f.position.y += p.y;
        // olha pra onde vai
        const prev = start.clone().lerp(ring, FX.EASE.io(Math.max(0, go - 0.05)));
        let yaw = go < 1 ? Math.atan2(-(p.x - prev.x), -(p.z - prev.z)) : Math.PI - ang - (i % 2 ? 0 : Math.PI);
        const toC = Math.atan2(p.x - C.x, p.z - C.z);
        if (b > 0) yaw = yaw + Math.atan2(Math.sin(toC - yaw), Math.cos(toC - yaw)) * b;
        f.rotation.y = yaw;
        f.r('corpo', up < 1 ? -1.2 * (1 - up) : (dv > 0 ? 1.2 * dv : 0), 0, 0);
        PEIXE.swim(f.r, T + i, 1.2, 1.5, i);
        f.r('mandibula', 1.0 * seq([[t0, 0], [t0 + 0.12, 1], [t0 + 0.18, 0, 'snap'], [t0 + 0.6, 0]], T), 0, 0);
        if (T > t0 + 0.15 && T < t0 + 0.45) hit = Math.max(hit, 1 - (T - t0 - 0.15) / 0.3);
        if (up < 1 && T > born) ctx.get('sai' + i, () => JJFX.drops(50 + i, 20, { size: [0.02, 0.045], life: 0.6, cone: R2 => { const a2 = R2() * 6.28; return [Math.cos(a2) * 0.7, 1 + R2() * 1.6, Math.sin(a2) * 0.7]; } })).at(new THREE.Vector3(start.x, 0.05, start.z), T - born, 0.02);
        if (dv > 0.5 && dv < 1) ctx.get('entra' + i, () => JJFX.drops(60 + i, 16, { size: [0.02, 0.04], life: 0.5 })).at(new THREE.Vector3(p.x, 0.05, p.z), (dv - 0.5) * 0.8, 0.02);
      }
      R.armsOut(0.25 + 0.5 * hit); R.lean(-0.15 * hit); R.spin(Math.PI + 0.5 * Math.sin(T * 2.4) * FX.clamp01((T - 1.6) * 2) * (1 - FX.clamp01((T - 4) * 2)));
      ctx.shake(0.3 * hit);
      return;
    }
    if (mode === 'mare') {
      const T = t % 4.6;
      const R = ctx.ref; const RZ = -3.2; R.place(0, RZ, 0); R.spin(Math.PI);
      // enche: barriga e cabeça incham, tentáculos se fecham; abre a barba e cospe o jato; depois tosse e murcha
      const fill = seq([[0.2, 0], [1.3, 1, 'io'], [1.45, 1.08], [1.6, 0.5, 'out'], [3.0, 0.3], [3.6, 0, 'io']], T);
      if (P.quadril) { const b = 1 + 0.16 * fill; P.quadril.obj.scale.set(b, 1 + 0.05 * fill, b); }
      if (P.cabeca) P.cabeca.obj.scale.set(1 + 0.1 * fill, 1 + 0.08 * fill, 1 + 0.1 * fill);
      r('tronco', 0.25 * seq([[0.2, 0], [1.3, -1, 'io'], [1.5, 0.6, 'snap'], [3.0, 0.4], [3.6, 0]], T), 0, 0);
      r('cabeca', 0.2 * seq([[0.2, 0], [1.3, -1], [1.5, 0.3, 'snap'], [3.0, 0.2], [3.6, 0]], T), 0, 0);
      const spread = seq([[1.3, 0], [1.5, 1, 'snap'], [3.0, 1], [3.5, 0]], T);
      writhe(1, spread, seq([[0.3, 0], [1.2, 1], [1.4, 0]], T));
      r('bracoD', -0.6 * spread, 0, 0.4 * spread); r('bracoE', -0.6 * spread, 0, -0.4 * spread);
      r('pernaD', -0.2 * spread, 0, 0); r('pernaE', 0.25 * spread, 0, 0);
      // jato: tubo de água grosso (miolo claro + casca escura), cada um ondulando no seu ritmo
      const shoot = seq([[1.45, 0], [1.7, 1, 'out'], [2.9, 1], [3.25, 0, 'in']], T);
      const m = mouth(), tgt = new THREE.Vector3(0.05 * Math.sin(T * 7), 1.15 + 0.06 * Math.sin(T * 5), RZ + 0.1);
      const outer = ctx.get('jatoA', () => { const tb = JJFX.tube(0x2f7fb0, 0.13, 0.32); tb.material.transparent = true; tb.material.opacity = 0.78; return tb; });
      const inner = ctx.get('jatoB', () => { const tb = JJFX.tube(0xc8ecff, 0.07, 0.2); tb.material.transparent = true; tb.material.opacity = 0.85; tb.material.emissive = new THREE.Color(0x1a4a66); return tb; });
      if (shoot > 0) { outer.at(m, tgt, shoot, { arc: 0.12, wiggle: 0.06, t: T, pulse: fr(T * 2.2) }); inner.at(m, tgt.clone().add(new THREE.Vector3(0, 0.02, 0)), Math.min(1, shoot * 1.05), { arc: 0.12, wiggle: 0.08, t: T + 0.4, pulse: fr(T * 2.2 + 0.5) }); }
      if (shoot > 0.95) {
        ctx.get('spray', () => JJFX.drops(71, 90, { life: 0.7, spread: 1.2, cone: R2 => [(R2() - 0.5) * 3, R2() * 2.4, (R2() - 0.1) * -2.6] })).at(tgt.clone().add(new THREE.Vector3(0, 0, 0.12)), T - 1.7, 0.02);
        ctx.drown(0.9); ctx.shake(0.35);
      }
      // alvo empurrado pra trás pelo jato, escorregando
      const push = seq([[1.7, 0], [2.9, 1, 'out']], T);
      if (T > 1.7) { R.place(0, RZ - 1.4 * push * (T < 3.6 ? 1 : 1), 0); R.lean(-0.3 * shoot); R.face(0.8 * shoot, true); }
      ctx.get('poca', () => JJFX.wet()).at(new THREE.Vector3(0, 0.015, RZ - 0.8), 1.3, FX.clamp01((T - 1.8) / 0.8), FX.clamp01((T - 1.8) / 0.4) * (1 - FX.clamp01((T - 4.0) / 0.6)), t, 2);
      if (T > 3.25 && T < 3.9) { const k = (T - 3.25) / 0.65; r('tronco', 0.15 * Math.sin(k * 18) * (1 - k), 0, 0); }
      return;
    }
    if (mode === 'morte') {
      const { T, fall } = JJFX.exorcize(t, obj, e, ctx, { h: 1.8, refZ: -2.2, rotX: 1.35, back: 0.4, ashY: 0.3, ashZ: 0.9, n: 170 });
      // a cabeça murcha (perde o ar), tentáculos caem moles, a água do corpo escorre
      if (P.cabeca) P.cabeca.obj.scale.set(1 + 0.05 * fall, 1 - 0.25 * fall, 1 + 0.05 * fall);
      writhe(1 - fall, 0, 0); for (let i = 0; i < 6; i++) r('tent' + i, 0.4 * fall, 0, 0);
      r('bracoD', -0.4 * fall, 0, 0.4 * fall); r('bracoE', -0.4 * fall, 0, -0.4 * fall);
      if (T > 0.8) ctx.get('agua', () => JJFX.wet()).at(new THREE.Vector3(0, 0.015, 0.9), 1.0, FX.clamp01((T - 0.8) / 1.0), 1 - FX.clamp01((T - 3.0) / 0.4), t, 4);
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(12, 0), variant(38, 1), variant(64, 2)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'embriao', nome: 'Dagon Embrião', bioma: 'Mar de Dagon', tier: 'Grau 2 · tier 7',
    variants: fixed.map(v => ({ nome: KINDS[v.kind][0], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado (' + KINDS[v.kind][0] + ')', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['cardume', 'Cardume'], ['mare', 'Maré Amaldiçoada'], ['morte', 'Exorcizado']],
    cam: 4.4, lookY: 1.1, lookZ: { cardume: -1.7, mare: -1.8, morte: 0.3 }, camFar: { cardume: 7.0, mare: 6.6 }, camYaw: { cardume: -1.1, mare: -1.35 },
    info: {
      'Tamanho': '1,6 bloco (metade é cabeça)',
      'Vida': '55',
      'Jeito': 'Anda gingando como criança pequena, sempre com 2 ou 3 peixes em volta. Foge de fogo. Se ficar em água, regenera 1 de vida por segundo.',
      'Cardume': 'Abre os braços e o chão em volta dele vira água: 5 peixes-shikigami saem nadando, rodam em você e mordem um de cada vez, depois mergulham na areia (somem). Bater nele enquanto chama cancela.',
      'Maré Amaldiçoada': 'Enche a barriga (dá pra ver inchando, 1 s de aviso) e cospe um jato de água do mar: empurra 3 blocos, apaga fogo e deixa a tela de afogado por 2 s. Sair da linha da boca desvia.',
      'Amadurecer': 'Se ficar vivo 5 minutos perto da água, a cabeça racha e nasce o Dagon de verdade (chefe, outra prévia).',
      'Fraqueza': 'Fogo e golpe na cabeça (mole, leva dano dobrado). Longe da água fica lento.',
      'Solta': 'Tinta Amaldiçoada, Casca do Ovo; raro: Coração da Maré (item: 1 vez por dia, chama 3 peixes-shikigami do seu lado).',
      'Variações': 'Vermelho do fundo, pálido ou abissal; com ou sem pedaço de casca do ovo na cabeça e no ombro; tentáculos de tamanhos diferentes.',
    },
  });
})();
