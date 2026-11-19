// Kitsune de Ofuda (Templos e Escola). Tier 8. Maldição nascida do medo das raposas dos santuários: raposa magra de
// pelo claro com pintura vermelha de máscara de festival no rosto, o corpo todo amarrado de ofuda (talismã de papel)
// que deveria selá-la, corda sagrada no pescoço e 2 a 5 caudas com fogo-fátuo azul na ponta.
// Golpes: Fogo-Fátuo (as chamas das caudas sobem, rodam e caem em você uma por uma) e Ilusão de Papel (vira três;
// as falsas, quando apanham, se desfazem em talismãs, enquanto a verdadeira morde pelo lado).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  // [nome, pelo, pintura do rosto, fogo (rgb 0..1)]
  const KINDS = [
    ['Branca', [236, 228, 214], [200, 30, 40], [0.45, 0.7, 1.0]],
    ['Negra', [52, 46, 58], [230, 180, 60], [0.55, 0.45, 1.0]],
    ['Dourada', [222, 162, 82], [150, 20, 40], [0.4, 0.85, 1.0]],
  ];
  function variant(seed, kind, tails) {
    const R = FX.rnd(seed);
    const k = kind ?? Math.floor(R() * KINDS.length);
    return { seed, kind: k, fur: KINDS[k][1], paint: KINDS[k][2], fire: KINDS[k][3], tails: tails ?? 2 + Math.floor(R() * 4), size: 0.92 + R() * 0.16 };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.8, hi = 1.12) => {
      const f = 0.62 - 0.4 * q.ny + 0.3 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const fur = (q, s) => {
      let c = shade(v.fur, q, s);
      const st = n3(q.ax * 0.9, q.ay * 1.8, q.az * 0.9, 30 + s + S);                                                 // mecha de pelo
      if (st > 0.7) c = mul(c, 1.07); else if (st < 0.28) c = mul(c, 0.9);
      if (q.f === 'bottom') c = mix(c, [250, 246, 238], 0.35);
      return c;
    };
    const P = {};
    P.body = q => {
      let c = fur(q, 1);
      // listra de pintura vermelha correndo pelo dorso
      if (q.f === 'top' && Math.abs(q.nx - 0.5) < 0.09 && fr(q.nz * 4) < 0.6) c = shade(v.paint, q, 2, 0.9, 1.1);
      return c;
    };
    P.leg = q => {
      let c = fur(q, 3);
      if (q.ny > 0.78) c = shade(mul(v.fur, 0.35), q, 4);                                                             // patinha escura
      return c;
    };
    P.head = q => {
      let c = fur(q, 5);
      if (q.f !== 'north') {
        if (q.f === 'top' && Math.abs(q.nx - 0.5) < 0.12 && q.nz < 0.5) c = shade(v.paint, q, 6, 0.9, 1.1);             // risca da testa continua
        return c;
      }
      const W = q.b.w, H = q.b.h, x = Math.floor(q.px), y = Math.floor(q.py);
      // olhos puxados: risco preto inclinado com íris dourada no meio, pintura vermelha subindo do canto
      const eye = (cx, dir) => {
        const ex = x - cx, ey = y - 2;
        if (ey === 0 && ex * dir >= 0 && ex * dir <= 1) return ex * dir === 0 ? [240, 190, 60] : [20, 14, 16];
        if (ey === -1 && ex * dir === 2) return [20, 14, 16];
        if ((ey === -1 && ex * dir === 1) || (ey === -2 && ex * dir === 2)) return shade(v.paint, q, 7, 0.9, 1.1);
        return null;
      };
      const a = eye(1, 1), b = eye(W - 2, -1);
      if (a) return a; if (b) return b;
      // joia de chama na testa
      if ((x === Math.floor(W / 2) || x === Math.floor(W / 2) - 1) && (y === 0 || y === 1)) return y === 0 ? shade(v.paint, q, 8) : mix(v.paint, [255, 255, 255], 0.3);
      return c;
    };
    P.snout = q => {
      let c = fur(q, 9);
      c = mix(c, [250, 246, 238], 0.3);
      if (q.f === 'north' && q.ny < 0.5 && q.nx > 0.25 && q.nx < 0.75) c = [24, 18, 20];                                 // focinho
      if ((q.f === 'east' || q.f === 'west') && q.ny > 0.75) c = mul(c, 0.6);                                          // risco da boca
      return c;
    };
    P.jaw = q => (q.f === 'top' ? [120, 30, 44] : mix(fur(q, 10), [250, 246, 238], 0.4));
    P.tooth = q => [240, 236, 220];
    P.ear = q => {
      let c = fur(q, 11);
      if (q.f === 'north') c = q.nx > 0.2 && q.nx < 0.8 && q.ny > 0.25 ? shade([60, 30, 36], q, 12) : c;
      if (q.ny < 0.25) c = shade(mul(v.fur, 0.35), q, 13);                                                           // ponta escura
      return c;
    };
    P.tail = j => q => {
      let c = fur(q, 14 + j);
      // ponta da cauda clareia até o branco-azulado do fogo
      if (j === 3) c = mix(c, [210, 230, 255], 0.35 + 0.5 * q.nz);
      return c;
    };
    // talismã: papel creme, faixa vermelha em cima, "escrita" preta que muda em cada um (pinceladas por linha)
    P.ofuda = i => q => {
      let c = shade([236, 226, 196], q, 20 + i, 0.86, 1.06);
      if (q.f === 'east' || q.f === 'west' || q.f === 'top' || q.f === 'bottom') return mul(c, 0.9);
      const x = Math.floor(q.px), y = Math.floor(q.py);
      if (y === 0) return [180, 24, 30];
      const R = FX.rnd(i * 31 + y * 7 + S);
      if (y >= 1 && y < Math.floor(q.b.h) - 1 && R() < 0.55 && (x === Math.floor(R() * q.b.w))) c = [26, 20, 24];
      if (y === Math.floor(q.b.h) - 1 && x === 0) c = [180, 24, 30];                                                 // carimbo
      return c;
    };
    // corda sagrada: palha trançada em diagonal
    P.rope = q => { let c = shade([210, 186, 120], q, 40, 0.8, 1.1); if (fr((q.ax + q.ay + q.az) * 0.6) < 0.35) c = mul(c, 0.78); return c; };
    P.shide = q => { let c = [246, 244, 236]; if (q.f !== 'north' && q.f !== 'south') c = mul(c, 0.85); return c; };
    return P;
  }

  function rig(P, v, lo) {
    const leg = (s, front) => ({
      name: 'perna' + (front ? 'F' : 'T') + (s < 0 ? 'D' : 'E'), pivot: [s * 1.9, 2.2, front ? -4.4 : 4.2],
      boxes: [{ x: -1.1, y: 0, z: -1.1, w: 2.2, h: 5.2, d: 2.2, p: P.leg }],
    });
    const tails = [];
    const n = v.tails;
    for (let k = 0; k < n; k++) {
      const spread = n === 1 ? 0 : (k / (n - 1) - 0.5) * 2;
      let seg = null;
      const W = [2.4, 3.2, 3.4, 2.6];
      for (let j = 3; j >= 0; j--) {
        const w = W[j];
        seg = { name: 'cauda' + k + '_' + j, pivot: [0, 0, j === 0 ? 0 : 3.4], boxes: [{ x: -w / 2, y: -w / 2, z: 0, w, h: w, d: 3.8, p: P.tail(j) }], kids: seg ? [seg] : [] };
      }
      seg.rot = [0.62, spread * 0.6, 0];
      if (!lo) seg.kids.push({ name: 'ofudaC' + k, pivot: [0, 1.2, 1.4], rot: [0.2, 0, 0], boxes: [{ x: -1, y: 0, z: -0.1, w: 2, h: 5, d: 0.2, p: P.ofuda(10 + k) }] });
      tails.push(seg);
    }
    const ofudas = [];
    if (!lo) {
      // presos nos lados do corpo, pendurados (balançam soltos)
      [[-1, -3.5], [-1, 0.5], [-1, 3.5], [1, -2.5], [1, 1.5], [1, 4.4]].forEach(([s, z], i) => ofudas.push({
        name: 'ofuda' + i, pivot: [s * 3.12, -2.6, z], rot: [0, 0, s * -0.12], boxes: [{ x: -1, y: 0, z: -0.1, w: 2, h: 6, d: 0.2, p: P.ofuda(i) }],
      }));
    }
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'corpo', pivot: [0, 16.6, 0], boxes: [
          { x: -3, y: -3, z: -6, w: 6, h: 5.4, d: 12, p: P.body },
          { x: -2.6, y: -3.4, z: -5.4, w: 5.2, h: 1, d: 5, p: P.body },
        ],
        kids: [
          {
            name: 'pescoco', pivot: [0, -0.6, -5.2], rot: [0.95, 0, 0], boxes: [
              { x: -2.2, y: -3.8, z: -2.4, w: 4.4, h: 4.4, d: 4.4, p: P.body },
              { x: -2.4, y: -2.2, z: -2.8, w: 4.8, h: 1.4, d: 4.8, p: P.rope },
              ...(lo ? [] : [{ x: -1.8, y: -0.8, z: -2.95, w: 1.2, h: 2.2, d: 0.2, p: P.shide }, { x: 0.6, y: -0.8, z: -2.95, w: 1.2, h: 2.2, d: 0.2, p: P.shide }]),
            ],
            kids: [{
              name: 'cabeca', pivot: [0, -3.4, -0.6], rot: [-0.95, 0, 0], boxes: [
                { x: -3.4, y: -4.8, z: -4.2, w: 6.8, h: 5.4, d: 5.6, p: P.head },
                { x: -1.7, y: -2.3, z: -7.8, w: 3.4, h: 2.3, d: 3.8, p: P.snout },
                ...(lo ? [] : [{ x: -4.2, y: -2.2, z: -2.4, w: 1, h: 2.4, d: 2.6, p: P.body }, { x: 3.2, y: -2.2, z: -2.4, w: 1, h: 2.4, d: 2.6, p: P.body }]),
              ],
              kids: [
                { name: 'mandibula', pivot: [0, 0, -4.2], boxes: [{ x: -1.4, y: 0, z: -3.2, w: 2.8, h: 0.9, d: 3.4, p: P.jaw }, ...(lo ? [] : [{ x: -1.2, y: -0.4, z: -3.1, w: 0.5, h: 0.5, d: 0.5, p: P.tooth }, { x: 0.7, y: -0.4, z: -3.1, w: 0.5, h: 0.5, d: 0.5, p: P.tooth }])] },
                { name: 'orelhaD', pivot: [-2.1, -4.6, -1.6], rot: [0, 0, 0.22], boxes: [{ x: -1.3, y: -4, z: -0.6, w: 2.6, h: 4, d: 1.2, p: P.ear }] },
                { name: 'orelhaE', pivot: [2.1, -4.6, -1.6], rot: [0, 0, -0.22], boxes: [{ x: -1.3, y: -4, z: -0.6, w: 2.6, h: 4, d: 1.2, p: P.ear }] },
              ],
            }],
          },
          { name: 'caudas', pivot: [0, -1.4, 5.6], kids: tails },
          leg(-1, true), leg(1, true), leg(-1, false), leg(1, false),
          ...ofudas,
        ],
      }],
    };
  }

  // pontas das caudas no mundo (onde nascem as chamas)
  function tips(P, obj, n) {
    obj.updateMatrixWorld(true);
    const out = [];
    for (let k = 0; k < n; k++) { const p = P['cauda' + k + '_3']; if (!p) continue; const w = new THREE.Vector3(0, 0, 3.6).applyMatrix4(p.obj.matrixWorld); out.push(w); }
    return out;
  }
  // copia a pose do corpo verdadeiro pra um clone (mesma árvore de objetos)
  function sync(a, b) { b.position.copy(a.position); b.quaternion.copy(a.quaternion); b.scale.copy(a.scale); for (let i = 0; i < a.children.length && i < b.children.length; i++) sync(a.children[i], b.children[i]); }

  // fogo-fátuo: halo azul fundo, miolo claro e línguas de fogo subindo (grãos opacos, pra ler em céu claro)
  function fire(ctx, key, FC, p, r, k, t) {
    if (k <= 0.01) return;
    const deep = [FC[0] * 0.35, FC[1] * 0.45, FC[2] * 0.9];
    ctx.get(key + 'h', () => DBFX.glow(deep)).at(p, r * 1.9, k * 0.9, t, 1.3);
    ctx.get(key + 'm', () => DBFX.glow(FC)).at(p, r, k, t * 1.3, 1.4);
    for (let j = 0; j < 2; j++) {
      const age = (t * 1.6 + j * 0.5) % 1;
      ctx.get(key + 'l' + j, () => DBFX.grains({ n: 18, seed: 300 + j * 7 + key.length, palette: [[60, 110, 235], [110, 170, 255], [40, 70, 200], [180, 215, 255]], size: [0.025, 0.06], drag: 3, grav: -1.6, life: 0.55, spread: 0.5,
        cone: R2 => [(R2() - 0.5) * 0.5, 0.2 + R2() * 0.4, (R2() - 0.5) * 0.5] })).at(p.clone().add(new THREE.Vector3(0, -r * 0.3, 0)), age * k, -9);
    }
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const n = e.v.tails, FC = e.v.fire;
    // caudas: cada uma no seu tempo, onda correndo até a ponta
    const tailSway = (amp = 1, up = 0) => {
      for (let k = 0; k < n; k++) for (let j = 0; j < 4; j++) r('cauda' + k + '_' + j, (j ? 0.12 : 0) * Math.sin(t * 1.6 + k * 1.3 - j * 0.6) * amp - up * (j === 0 ? 0.4 : 0.1), 0.22 * amp * Math.sin(t * 1.2 + k * 2.1 - j * 0.7), 0);
    };
    const flutter = (k = 1) => { for (let i = 0; i < 6; i++) r('ofuda' + i, 0.25 * k * Math.sin(t * 5 + i * 1.7) + 0.1 * k, 0, 0.08 * Math.sin(t * 3 + i)); for (let i = 0; i < n; i++) r('ofudaC' + i, 0.3 * k * Math.sin(t * 4 + i * 2), 0, 0); };
    const flames = (k = 1, grow = 0.07) => {
      const tp = tips(P, obj, n);
      tp.forEach((p, i) => fire(ctx, 'chama' + i, FC, p, grow * (1 + 0.25 * Math.sin(t * 9 + i * 2)) * e.v.size, k * (0.75 + 0.25 * Math.sin(t * 13 + i)), t + i));
      if (tp.length) ctx.get('luzAzul', () => DBFX.light(new THREE.Color(...FC))).at(tp[0], 1.2 * k, 3);
      return tp;
    };

    if (mode === 'parado') {
      // em pé alerta: cabeça vira devagar procurando, orelhas giram, caudas ondulam cada uma num ritmo
      r('cabeca', 0.08 * Math.sin(t * 0.7), 0.5 * Math.sin(t * 0.45), 0.12 * Math.sin(t * 0.9));
      r('pescoco', 0.05 * Math.sin(t * 1.4), 0, 0);
      const tw = loop([[0, 0], [0.04, 1], [0.1, 0]], t / 2.3);
      r('orelhaD', 0, 0.5 * tw, 0); r('orelhaE', 0, -0.3 * loop([[0, 0], [0.04, 1], [0.1, 0]], t / 3.1 + 0.4), 0);
      r('corpo', 0.02 * Math.sin(t * 2), 0, 0);
      tailSway(1);
      flutter(0.6);
      flames(1);
      return;
    }
    if (mode === 'andar') {
      // trote: pares cruzados, corpo baixo, cabeça firme
      const ph = t * 7;
      r('pernaFD', 0.7 * Math.sin(ph), 0, 0); r('pernaTE', 0.7 * Math.sin(ph), 0, 0);
      r('pernaFE', -0.7 * Math.sin(ph), 0, 0); r('pernaTD', -0.7 * Math.sin(ph), 0, 0);
      obj.position.y += 0.04 * Math.abs(Math.sin(ph));
      r('corpo', 0.04 * Math.sin(ph * 2), 0, 0); r('cabeca', -0.05 * Math.sin(ph * 2), 0, 0);
      tailSway(0.6, -0.3);
      flutter(1.2);
      flames(0.8);
      return;
    }
    if (mode === 'fogo') {
      const T = t % 4.4;
      const R = ctx.ref; const RZ = -3.4; R.place(0.4, RZ, 0); R.spin(Math.PI);
      // senta nas patas de trás e levanta as caudas em leque; as chamas crescem, sobem, rodam na cara dela e caem no alvo
      const sit = seq([[0, 0], [0.4, 1, 'out'], [3.6, 1], [4.2, 0, 'io']], T);
      r('corpo', -0.35 * sit, 0, 0); obj.position.y -= 0.08 * sit; obj.position.z += 0.1 * sit;
      r('pernaTD', 0.35 * sit, 0, 0); r('pernaTE', 0.35 * sit, 0, 0); r('pernaFD', 0.35 * sit, 0, 0); r('pernaFE', 0.35 * sit, 0, 0);
      r('pescoco', 0.15 * sit, 0, 0); r('cabeca', 0.2 * sit, 0, 0);
      for (let k = 0; k < n; k++) { const sp = n === 1 ? 0 : (k / (n - 1) - 0.5) * 2; r('cauda' + k + '_0', -0.5 * sit, 0.35 * sp * sit, 0); }
      tailSway(0.4);
      flutter(1);
      const open = seq([[0.5, 0], [0.7, 1, 'out'], [0.95, 0.3], [3.6, 0.3], [3.9, 0]], T);
      r('mandibula', 0.6 * open, 0, 0);
      const tp = tips(P, obj, n);
      obj.updateMatrixWorld(true);
      const head = new THREE.Vector3(); P.cabeca.obj.getWorldPosition(head);
      const N = Math.min(3, n), aims = [new THREE.Vector3(0.2, 1.5, RZ), new THREE.Vector3(0.55, 1.1, RZ), new THREE.Vector3(0.4, 0.4, RZ)];
      for (let i = 0; i < N; i++) {
        const born = seq([[0.3, 0], [0.8, 1, 'out']], T);
        const orbit = FX.clamp01((T - 0.8) / 0.9);
        const shoot = FX.clamp01((T - (1.8 + i * 0.45)) / 0.28);
        let p;
        const ang = t * 3.5 + i * Math.PI * 2 / N;
        const ring = head.clone().add(new THREE.Vector3(Math.cos(ang) * 0.75, 0.55 + 0.15 * Math.sin(ang * 2), Math.sin(ang) * 0.75));
        const from = tp[i] || head;
        if (shoot <= 0) p = from.clone().lerp(ring, FX.EASE.io(orbit));
        else { const st = from.clone().lerp(ring, 1); p = st.clone().lerp(aims[i], FX.EASE.in(shoot)); p.y += 0.4 * Math.sin(Math.PI * shoot); }
        const alive = shoot < 1;
        fire(ctx, 'fogo' + i, FC, p, (0.06 + 0.1 * born) * (shoot > 0 ? 1.2 : 1), alive ? born : 0, t * 1.4 + i);
        // rastro: fagulhas azuis soltas atrás da chama em voo
        if (shoot > 0) ctx.get('rastro' + i, () => DBFX.grains({ n: 40, seed: 70 + i, add: true, palette: [[120, 170, 255], [200, 230, 255], [90, 110, 240]], size: [0.02, 0.05], drag: 2.5, grav: -0.6, life: 0.5, spread: 0.3, cone: R2 => [(R2() - 0.5) * 0.6, (R2() - 0.3) * 0.6, (R2() - 0.5) * 0.6] })).at(p, Math.min(shoot, 1) * 0.3 + 0.02, 0);
        // impacto: clarão azul, alvo sacode, véu de energia amaldiçoada
        const hitAge = T - (1.8 + i * 0.45 + 0.28);
        if (hitAge > 0 && hitAge < 0.5) {
          ctx.get('estouro' + i, () => DBFX.grains({ n: 60, seed: 90 + i, add: true, palette: [[140, 190, 255], [230, 240, 255], [80, 100, 230]], size: [0.03, 0.07], drag: 4, grav: 0.4, life: 0.45, cone: R2 => { const a2 = R2() * 6.28, b2 = R2() * 3.14; return [Math.cos(a2) * Math.sin(b2) * 3, Math.cos(b2) * 3, Math.sin(a2) * Math.sin(b2) * 3]; } })).at(aims[i], hitAge, 0);
          ctx.flash(aims[i], 1 - hitAge / 0.5);
          R.lean(-0.25 * (1 - hitAge / 0.5)); ctx.shake(0.4 * (1 - hitAge / 0.5)); ctx.cursed(0.7 * (1 - hitAge / 0.5));
        }
      }
      // as pontas ficam sem fogo enquanto ele está solto e reacendem no fim
      const relit = seq([[3.4, 0], [3.9, 1]], T);
      tp.forEach((p, i) => { if (i >= N || relit > 0) fire(ctx, 'chama' + i, FC, p, 0.07 * e.v.size, i >= N ? 0.9 : relit, t + i); });
      ctx.get('luzAzul', () => DBFX.light(new THREE.Color(...FC))).at(head.clone().add(new THREE.Vector3(0, 0.6, 0)), 2.2 * seq([[0.3, 0], [0.8, 1], [3.2, 1], [3.6, 0]], T), 4);
      return;
    }
    if (mode === 'ilusao') {
      const T = t % 5.0;
      const R = ctx.ref; const RZ = -2.6; R.place(0, RZ, 0); R.spin(Math.PI);
      // gira no lugar e estoura em papel; quando o papel baixa, são três raposas. O alvo soca a do meio (falsa):
      // ela vira talismã. A verdadeira (esquerda) já está mordendo o braço dele.
      const spin = seq([[0.2, 0], [0.75, 1, 'in']], T);
      const split = seq([[0.75, 0], [1.3, 1, 'out']], T);
      const lunge = seq([[2.6, 0], [2.9, 1, 'out'], [3.5, 1], [3.9, 0, 'io']], T);
      const crouch = seq([[1.3, 0], [1.6, 1, 'out'], [2.6, 1], [2.7, 0]], T);
      const pose = (rr, k) => {
        rr('corpo', 0.12 * k, 0, 0); rr('pescoco', -0.2 * k, 0, 0); rr('cabeca', 0.25 * k, 0, 0);
        rr('pernaFD', -0.4 * k, 0, 0); rr('pernaFE', -0.4 * k, 0, 0); rr('pernaTD', 0.3 * k, 0, 0); rr('pernaTE', 0.3 * k, 0, 0);
        rr('orelhaD', -0.4 * k, 0, 0.2 * k); rr('orelhaE', -0.4 * k, 0, -0.2 * k);
      };
      obj.rotation.y = spin * Math.PI * 4 * (1 - split * 0);
      pose(r, crouch);
      tailSway(1);
      flutter(1.4);
      // a verdadeira vai pra esquerda do alvo e dá o bote no braço
      obj.position.x += -1.3 * split + 1.1 * lunge; obj.position.z += -0.4 * split + -1.2 * lunge;
      obj.rotation.y += -0.5 * lunge;
      r('corpo', -0.15 * lunge, 0, 0); r('pescoco', -0.3 * lunge, 0, 0);
      r('mandibula', 0.7 * seq([[2.6, 0], [2.8, 1], [2.92, 0.1, 'snap'], [3.5, 0.1], [3.7, 0]], T), 0, 0);
      flames(1);
      // clones: copiam a pose da verdadeira com um atraso nas caudas, ficam no meio e na direita
      const clones = [];
      for (let i = 0; i < 2; i++) {
        const c = ctx.get('clone' + i + e.obj.uuid, () => e.obj.clone(true));
        obj.updateMatrixWorld(true);
        sync(obj, c);
        c.rotation.y = obj.rotation.y - (-0.5 * lunge) + (i ? 0.25 : 0) * split;
        c.position.set(obj.position.x - (-1.3 * split + 1.1 * lunge) + (i ? 1.3 : 0) * split, obj.position.y, obj.position.z - (-1.2 * lunge) + (i ? -0.4 : -0.9) * split);
        const pop = i === 0 ? seq([[2.25, 0], [2.4, 1, 'snap']], T) : seq([[3.6, 0], [3.9, 1, 'snap']], T);
        c.visible = split > 0.02 && pop < 1;
        c.scale.copy(obj.scale).multiplyScalar(1 + 0.15 * pop);
        clones.push(c);
        if (pop > 0 || (T > 0.7 && T < 1.5)) {
          const age = pop > 0 ? T - (i === 0 ? 2.25 : 3.6) : T - 0.7;
          ctx.get('papel' + i + (pop > 0 ? 'b' : 'a'), () => DBFX.grains({
            n: 70, seed: 50 + i * 3 + (pop > 0 ? 1 : 0), palette: [[240, 230, 200], [236, 226, 196], [190, 30, 36], [250, 246, 230], [30, 24, 26]], size: [0.05, 0.12], drag: 2.4, grav: 1.2, life: 1.4, spread: 0.15,
            cone: R2 => { const a2 = R2() * 6.28; return [Math.cos(a2) * (1 + R2() * 2), 0.8 + R2() * 2.4, Math.sin(a2) * (1 + R2() * 2)]; },
          })).at(pop > 0 ? c.position.clone().add(new THREE.Vector3(0, -0.8, 0)) : new THREE.Vector3(0, 0.6, 0), age, 0.02);
        }
      }
      // o alvo soca a do meio (que vira papel) e leva a mordida no braço
      R.punch(seq([[1.95, 0], [2.25, 1, 'out'], [2.5, 0]], T));
      const bit = seq([[2.85, 0], [2.95, 1, 'snap'], [3.5, 1], [3.7, 0]], T);
      if (bit > 0) { R.armsOut(-0.3 * bit); R.lean(0.15 * bit); R.spin(Math.PI - 0.5 * bit); }
      if (T > 2.9 && T < 3.05) ctx.flash(new THREE.Vector3(-0.4, 1.2, RZ), 1 - (T - 2.9) / 0.15);
      if (T > 0.65 && T < 0.85) ctx.flash(new THREE.Vector3(0, 0.6, 0), 1 - (T - 0.65) / 0.2);
      return;
    }
    if (mode === 'morte') {
      const { T, fall } = JJFX.exorcize(t, obj, e, ctx, { h: 1.2, refZ: -2.2, rotX: 0, back: 0.12, ashY: 0.25, ashZ: 0.2, n: 140 });
      // morte com peso: as pernas cedem primeiro (deita de barriga), depois rola pro lado sobre a borda das patas
      // e quica uma vez; cabeça, pernas e caudas ficam moles e caem no chão, nada fica duro no ar
      const { seq } = FX;
      const buckle = seq([[0.25, 0], [0.55, 1, 'in']], T);
      const roll = seq([[0.48, 0], [0.92, 1, 'in'], [1.02, 0.9], [1.12, 1]], T);
      const limp = seq([[0.6, 0], [1.2, 1]], T);
      // a rolagem vai na peça 'raiz', não na raiz do corpo: o jogo gira a raiz num quadro espelhado, e lá a raposa
      // deitava do lado errado, flutuando meio bloco (teste R5, 2026-11-14)
      const ang = 1.5 * roll, rz = P.raiz.obj;
      rz.rotation.z = ang;
      for (const sd of ['D', 'E']) {
        r('pernaF' + sd, 1.1 * buckle - 0.5 * limp, 0, 0);
        r('pernaT' + sd, -1.0 * buckle + 0.4 * limp, 0, 0);
      }
      // de lado, as pernas de cima caem por cima das de baixo
      r('pernaFE', 0, 0, -0.35 * limp); r('pernaTE', 0, 0, -0.3 * limp);
      r('pernaFD', 0, 0, 0.1 * limp); r('pernaTD', 0, 0, 0.15 * limp);
      r('pescoco', 0.35 * buckle, 0, 0);
      r('cabeca', 0.25 * buckle, 0, 0.45 * limp);
      r('mandibula', 0.35 * limp, 0, 0);
      r('orelhaD', 0, 0, 0.3 * limp); r('orelhaE', 0, 0, -0.3 * limp);
      for (let k = 0; k < n; k++) {
        r('cauda' + k + '_0', -0.62 * buckle, 0, 0.9 * limp);
        for (let j = 1; j < 4; j++) r('cauda' + k + '_' + j, 0.06 * j * limp, 0.05 * Math.sin(k * 2 + j), 0.25 * limp);
      }
      // o tronco cai quase reto pra baixo: no máximo 0,2 bloco pro lado, sem escorregar
      JJFX.noSlide(obj, P.corpo.obj, 'x', () => { rz.rotation.z = 0; }, () => { rz.rotation.z = ang; }, 0.2 * roll, rz);
      // nada escora o corpo deitado: caudas, pescoço e pernas deitam no chão junto com ele (antes uma cauda ficava
      // por baixo e a raposa morria 0,3 bloco no ar, teste R5)
      const limbs = ['pescoco', 'pernaFD', 'pernaFE', 'pernaTD', 'pernaTE', 'ofuda0', 'ofuda1', 'ofuda2', 'ofuda3', 'ofuda4', 'ofuda5'];
      for (let k = 0; k < n; k++) limbs.push('cauda' + k + '_0');
      JJFX.floorLimbs(obj, P, ['corpo'], limbs, roll);
      flutter(1 - fall);
      const tp = tips(P, obj, n);
      tp.forEach((p, i) => fire(ctx, 'chama' + i, FC, p, 0.07, 1 - FX.clamp01((T - 0.3) / 0.6), t + i));
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(5, 0, 3), variant(29, 1, 5), variant(44, 2, 2)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'kitsune', nome: 'Kitsune de Ofuda', bioma: 'Templos e Escola', tier: 'Grau 3 · tier 8',
    variants: fixed.map((v, i) => ({ nome: KINDS[v.kind][0] + ' (' + v.tails + ' caudas)', v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteada (' + KINDS[v.kind][0] + ', ' + v.tails + ' caudas)', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Trotar'], ['fogo', 'Fogo-Fátuo'], ['ilusao', 'Ilusão de Papel'], ['morte', 'Exorcizada']],
    cam: 4.4, lookY: 0.7, lookZ: { fogo: -1.4, ilusao: -1.0, morte: 0 }, camFar: { fogo: 6.4, ilusao: 6.6 }, camYaw: { fogo: -1.15, ilusao: -0.5 },
    info: {
      'Tamanho': 'Raposa de 1 bloco de comprimento, caudas compridas',
      'Vida': '24',
      'Jeito': 'Esperta: rodeia, nunca vem reto. Prefere atacar de longe com o fogo e fugir pra neblina. Quanto mais caudas, mais forte (5 caudas = quase grau 2).',
      'Fogo-Fátuo': 'Senta e abre as caudas em leque; até 3 chamas azuis sobem, rodam em volta da cabeça e caem em você uma por uma (cada uma dá dano e tira um pouco de energia amaldiçoada). Dá pra desviar andando de lado.',
      'Ilusão de Papel': 'Gira, estoura em talismãs e vira três raposas iguais. As falsas morrem com 1 golpe e viram papel; a verdadeira morde pelo lado. Dica: as falsas não fazem sombra no chão.',
      'Solta': 'Ofuda Usado (papel de selamento), Fagulha de Fogo-Fátuo; raro: Máscara de Kitsune (cosmético que esconde o nome do jogador).',
      'Variações': 'Branca, Negra ou Dourada; de 2 a 5 caudas.',
    },
  });
})();
