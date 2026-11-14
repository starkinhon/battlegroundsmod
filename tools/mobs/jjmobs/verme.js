// Verme de Armazenamento (Floresta Negra; raro em Shibuya). Tier 9, grau 4. A maldição-minhoca que o Toji carregava
// enrolada no corpo pra guardar armas: corpo de anéis pálidos, cabecinha de olhos miúdos e uma boca redonda de beiço
// que engole qualquer coisa. Selvagem, sem dono. Golpes: Engolir (rouba o item da sua mão e foge com ele na
// barriga) e Enrolar (dá voltas nas suas pernas e te deixa lento).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const NS = 13;

  const SKINS = [[172, 168, 144], [150, 166, 142], [180, 156, 150], [160, 150, 172]];
  function variant(seed) {
    const R = FX.rnd(seed);
    return { seed, skin: SKINS[Math.floor(R() * SKINS.length)], band: R() < 0.5, size: 0.9 + R() * 0.2 };
  }
  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.8, hi = 1.12) => {
      const f = 0.62 - 0.4 * q.ny + 0.35 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const P = {};
    P.seg = q => {
      let c = shade(v.skin, q, 1);
      if (q.nz < 0.14 || q.nz > 0.86) c = mul(c, 0.78);                                                      // sulco entre anéis
      if (v.band && q.f === 'top' && Math.abs(q.nx - 0.5) < 0.16) c = mix(c, [120, 100, 130], 0.5);           // listra escura no dorso
      if (q.f === 'bottom') c = mix(c, [230, 210, 200], 0.4);
      if (n3(q.ax * 0.8, q.ay * 0.8, q.az * 0.8, 3 + S) > 0.78) c = mul(c, 0.86);
      return c;
    };
    P.head = q => {
      let c = shade(v.skin, q, 2);
      if (q.f !== 'north') return c;
      const x = Math.floor(q.px), y = Math.floor(q.py);
      if (y === 0 && (x === 1 || x === 4)) c = [20, 14, 18];                                                    // olhinhos lá em cima
      if (y === 0 && (x === 2 || x === 3)) c = mul(c, 0.86);
      return c;
    };
    P.lip = q => { let c = shade(mix(v.skin, [190, 110, 120], 0.6), q, 4); if (q.f === 'north' && q.nx > 0.25 && q.nx < 0.75 && q.ny > 0.25 && q.ny < 0.75) c = [40, 10, 18]; return c; };
    P.tooth = q => [226, 216, 186];
    return P;
  }
  function rig(P, v, lo) {
    let tail = null;
    for (let i = NS - 1; i >= 1; i--) {
      const k = 1 - i * 0.035, w = 4.6 * k;
      tail = { name: 'seg' + i, pivot: [0, 0, i === 1 ? 3 : 3.6], boxes: [{ x: -w / 2, y: -w / 2, z: 0, w, h: w, d: 3.8, p: P.seg }], kids: tail ? [tail] : [] };
    }
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'cabeca', pivot: [0, 21.6, 0], boxes: [
          { x: -2.8, y: -2.6, z: -3, w: 5.6, h: 5, d: 6, p: P.head },
          { x: -2.2, y: -1.4, z: -3.8, w: 4.4, h: 3.8, d: 1, p: P.lip },
          ...(lo ? [] : [-1.2, 0.3].map(x => ({ x, y: -1.0, z: -3.4, w: 0.9, h: 0.9, d: 0.6, p: P.tooth }))),
        ],
        kids: [{ name: 'beico', pivot: [0, 2.4, -3.0], boxes: [{ x: -2.2, y: -0.6, z: -0.8, w: 4.4, h: 1, d: 1, p: P.lip }] }, ...(tail ? [tail] : [])],
      }],
    };
  }

  // caminho da minhoca: cada anel segue o da frente com atraso (ondas laterais)
  function wave(r, t, amp, f = 1, up = 0) {
    for (let i = 1; i < NS; i++) {
      r('seg' + i, up * Math.sin(t * 2 * f - i * 0.7) * 0.25, amp * (Math.sin(t * 2 * f - i * 0.7) - Math.sin(t * 2 * f - (i - 1) * 0.7)) * 1.6, 0);
    }
  }
  // espada do jogador (pra mostrar o roubo): lâmina de cubinhos
  function sword() {
    const g = new THREE.Group();
    const m = (w, h, d, c, y) => { const b = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), new THREE.MeshLambertMaterial({ color: c })); b.position.y = y; g.add(b); };
    m(0.06, 0.62, 0.02, 0xd8dde6, 0.42); m(0.03, 0.62, 0.025, 0x9aa2ae, 0.42); m(0.2, 0.04, 0.05, 0x5a4a2e, 0.1); m(0.05, 0.16, 0.05, 0x6a4a2a, 0.0);
    return g;
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size * 1.2; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const gulp = loop([[0, 0], [0.15, 1], [0.3, 0]], t / 2.2);

    if (mode === 'parado') {
      wave(r, t * 0.4, 0.12);
      // deitado no chão, cabeça um pouco erguida farejando; o resto do corpo fica rente ao chão
      r('cabeca', -0.3 + 0.08 * Math.sin(t * 0.8), 0.35 * Math.sin(t * 0.6), 0); P.cabeca.obj.position.y -= 1.2;
      r('seg1', 0.3 - 0.08 * Math.sin(t * 0.8), 0, 0);
      r('beico', 0.3 * gulp, 0, 0);
      return;
    }
    if (mode === 'andar') {
      wave(r, t, 0.3, 1.4);
      obj.position.x += 0.12 * Math.sin(t * 2.8);
      r('cabeca', 0, -0.3 * Math.sin(t * 2.8), 0);
      r('beico', 0.15, 0, 0);
      return;
    }
    if (mode === 'engolir') {
      const T = t % 4.4;
      const R = ctx.ref; const RZ = -2.8; R.place(0, RZ, 0);
      // a espada do jogador, na mão direita
      const sw = ctx.get('espada', sword);
      R.face(0); R.punch(0);
      const hand = new THREE.Vector3(-0.375, 0.72, RZ + 0.12);
      const lunge = seq([[0.5, 0], [0.85, 1, 'in'], [1.05, 1], [1.4, 0, 'out']], T);
      const rear = seq([[0.1, 0], [0.5, 1, 'out']], T) * (1 - seq([[0.5, 0], [0.7, 1]], T));
      wave(r, t * 0.6, 0.12 * (1 - lunge));
      obj.position.z += -1.9 * lunge;
      r('cabeca', -0.6 * rear + 0.15 * lunge, 0, 0); P.cabeca.obj.position.y -= 4 * rear + 3.2 * lunge;
      r('seg1', 0.6 * rear + 0.3 * lunge, 0, 0); r('seg2', 0.3 * rear, 0, 0);
      r('beico', 0.9 * seq([[0.6, 0], [0.85, 1, 'out'], [0.95, 0, 'snap']], T), 0, 0);
      const stolen = T > 0.92;
      // engole: a espada some boca adentro e o calombo dela desce pelos anéis
      if (!stolen) { sw.visible = true; sw.position.copy(hand); sw.rotation.set(1.3, 0, 0); }
      else {
        const f = FX.clamp01((T - 0.92) / 0.25); sw.visible = f < 1;
        obj.updateMatrixWorld(true); const m = new THREE.Vector3(); P.cabeca.obj.getWorldPosition(m);
        sw.position.copy(hand.clone().lerp(m, f)); sw.rotation.set(1.3 + 0.3 * f, 0, 0); sw.scale.setScalar(1 - 0.6 * f);
        const bump = (T - 1.1) / 0.9 * NS;
        for (let i = 1; i < NS; i++) { const k = Math.max(0, 1 - Math.abs(bump - i)); P['seg' + i] && P['seg' + i].obj.scale.set(1 + 0.45 * k, 1 + 0.45 * k, 1); }
        R.face(seq([[0, 0], [0.1, 0.6, 'snap'], [0.6, 0]], T - 0.92));
      }
      // foge: dá meia volta e sai serpenteando rápido
      if (T > 1.9) {
        const f = FX.clamp01((T - 1.9) / 1.6);
        obj.rotation.y = Math.PI * FX.EASE.io(Math.min(1, f * 3));
        obj.position.z += 3.5 * FX.EASE.in(f);
        wave(r, t * 2, 0.35, 1.8);
      }
      return;
    }
    if (mode === 'enrolar') {
      // a minhoca segue o próprio caminho como cobra de verdade: chega rastejando, sobe pelas pernas em espiral
      // (uma volta inteira, das canelas até a cintura), põe a cabeça na cara do alvo e chia; aperta em pulsos;
      // o alvo bate e ela desenrola de ré.
      const T = t % 5.0;
      const R = ctx.ref; const RZ = 0; R.place(0, RZ, 0); R.spin(Math.PI * 0.15);
      const sc = e.v.size * 1.45; e.obj.children[0].scale.set(sc, sc, sc);
      const U = sc / 16, Lh = 3 * U, L = 3.6 * U;
      const path = ctx.get('vermePath', () => {
        // amostra o caminho em pontos densos e guarda o comprimento acumulado
        const pts = [], rr = 0.42, y0 = 0.17, y1 = 0.92, th0 = -0.6;
        const helixStart = new THREE.Vector3(Math.cos(th0) * rr, y0, RZ + Math.sin(th0) * rr);
        const g0 = new THREE.Vector3(2.6, 0.2, RZ - 1.9);
        for (let i = 0; i <= 60; i++) { const f = i / 60; const p = g0.clone().lerp(helixStart, f); p.x += 0.12 * Math.sin(f * 9) * (1 - f); pts.push(p); }
        for (let i = 1; i <= 120; i++) { const f = i / 120, th = th0 + f * Math.PI * 2.0; pts.push(new THREE.Vector3(Math.cos(th) * rr, y0 + (y1 - y0) * f, RZ + Math.sin(th) * rr)); }
        // sai da espiral pela frente e curva pra cima, terminando com a cabeça apontada pra cara do alvo
        const end = pts[pts.length - 1].clone(), face = new THREE.Vector3(0, 1.5, RZ);
        const B = new THREE.Vector3(0.45, 1.25, RZ - 0.6), A = B.clone().add(face.clone().sub(B).normalize().multiplyScalar(0.3));
        for (let i = 1; i <= 24; i++) { const f = i / 24, u = 1 - f; pts.push(end.clone().multiplyScalar(u * u).add(B.clone().multiplyScalar(2 * u * f)).add(A.clone().multiplyScalar(f * f))); }
        const acc = [0]; for (let i = 1; i < pts.length; i++) acc.push(acc[i - 1] + pts[i].distanceTo(pts[i - 1]));
        const g = new THREE.Group(); g.pts = pts; g.acc = acc; g.len = acc[acc.length - 1];
        g.at = u => {
          if (u <= 0) return pts[0].clone().add(pts[0].clone().sub(pts[1]).normalize().multiplyScalar(-u));
          if (u >= g.len) return pts[pts.length - 1].clone();
          let lo = 0, hi = acc.length - 1; while (hi - lo > 1) { const m = (lo + hi) >> 1; if (acc[m] < u) lo = m; else hi = m; }
          return pts[lo].clone().lerp(pts[hi], (u - acc[lo]) / (acc[hi] - acc[lo]));
        };
        return g;
      });
      const full = path.len, ground = path.acc[60];
      const climb = seq([[0, ground - 1.4], [0.9, ground + 0.15], [2.1, full, 'io'], [3.5, full], [3.62, full - 0.12, 'out'], [4.1, full - 0.12], [4.9, ground - 0.6, 'io']], T);
      const hold = seq([[1.9, 0], [2.2, 1], [3.5, 1], [3.65, 0]], T);
      // raiz zerada; desloca o corpo inteiro pra cabeça cair no ponto do caminho
      obj.rotation.set(0, 0, 0);
      const W = new THREE.Vector3(), Q = new THREE.Quaternion(), M = new THREE.Matrix4(), UP = new THREE.Vector3(0, 1, 0);
      const aim = (part, from, to, gaze) => {
        const d = to.clone().sub(from).normalize();
        M.lookAt(d, new THREE.Vector3(), Math.abs(d.y) > 0.97 ? new THREE.Vector3(0, 0, 1) : UP);
        const qw = new THREE.Quaternion().setFromRotationMatrix(M);
        part.obj.parent.getWorldQuaternion(Q);
        part.obj.quaternion.copy(Q.invert().multiply(qw));
      };
      obj.updateMatrixWorld(true);
      P.cabeca.obj.getWorldPosition(W);
      const H = path.at(climb); obj.position.add(H.clone().sub(W));
      obj.updateMatrixWorld(true);
      // cabeça: na escalada olha pra onde vai; parada, vira pra cara do alvo
      const back = path.at(climb - Lh);
      const tailDir = back.clone().sub(H).normalize();
      aim(P.cabeca, H, H.clone().add(tailDir));
      obj.updateMatrixWorld(true);
      for (let i = 1; i < NS; i++) {
        const part = P['seg' + i]; if (!part) break;
        part.obj.getWorldPosition(W);
        aim(part, W, path.at(climb - Lh - i * L));
        part.obj.updateMatrixWorld(true);
      }
      // aperta: os anéis enrolados afinam em pulsos que correm de baixo pra cima
      for (let i = 1; i < NS; i++) { const k = hold * Math.max(0, Math.sin(T * 7 + i * 0.7)) * 0.16; P['seg' + i] && P['seg' + i].obj.scale.set(1 - k, 1 - k, 1); }
      // chiado na cara: boca abre e fecha rápido, cabeça dá botinhos
      const hiss = hold * (0.5 + 0.5 * Math.sin(T * 22));
      r('beico', 0.9 * hiss, 0, 0);
      if (hold > 0.5) P.cabeca.obj.position.z -= 0.6 * Math.max(0, Math.sin(T * 5));
      // alvo: se debate preso (sem andar), depois soca a cabeça e ela recua de ré
      R.lean(0.08 * Math.sin(T * 6) * hold); R.armsOut(0.35 * hold * (0.6 + 0.4 * Math.sin(T * 5)));
      const pun = seq([[3.35, 0], [3.5, 1, 'out'], [3.75, 0]], T);
      if (pun > 0) R.punch(pun);
      if (T > 3.5 && T < 3.62) ctx.flash(new THREE.Vector3(0.1, 1.4, RZ - 0.5), 1 - (T - 3.5) / 0.12);
      if (hold > 0.3) ctx.blur(0.1 * hold * (0.5 + 0.5 * Math.sin(T * 7)));
      return;
    }
    if (mode === 'morte') {
      const { fall } = JJFX.exorcize(t, obj, e, ctx, { h: 0.7, refZ: -2.0, rotX: 0, back: 0.3, ashY: 0.1, ashZ: 0.8, n: 90 });
      wave(r, t * 3, 0.4 * (1 - fall), 2);
      P.cabeca.obj.position.y += 0;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(2), variant(26), variant(50)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'verme', nome: 'Verme de Armazenamento', bioma: 'Floresta Negra', tier: 'Grau 4 · tier 9',
    variants: fixed.map((v, i) => ({ nome: ['Pálido', 'Esverdeado', 'Rosado'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Rastejar'], ['engolir', 'Engolir'], ['enrolar', 'Enrolar'], ['morte', 'Exorcizado']],
    cam: 4.6, lookY: 0.6, lookZ: { parado: 0.8, andar: 0.8, engolir: -0.6, enrolar: -0.3, morte: 0.4 }, camFar: { engolir: 6.5, enrolar: 4.2 }, camYaw: { engolir: -1.2, enrolar: -0.7 },
    info: {
      'Tamanho': 'Uns 2 blocos de comprimento, cabeça na altura do joelho',
      'Vida': '14',
      'Jeito': 'Ladrão, foge em vez de lutar. Sozinho, enrolado em raiz na Floresta Negra; raro em Shibuya. Vem atrás de quem segura arma.',
      'Engolir': 'Empina, dá o bote na sua mão e engole o item que você segura (dá pra ver o calombo descendo pelos anéis). Depois foge serpenteando. Matar devolve o item; se ele sumir no buraco, volta com ele depois.',
      'Enrolar': 'Rasteja até você e sobe pelas pernas em espiral até a cintura, com a cabeça chiando na sua cara: preso 3 s (não anda nem pula), tela embaça a cada aperto. Um soco na cabeça solta e ela desenrola de ré.',
      'Solta': 'O que tiver engolido (às vezes um item aleatório que ele já carregava); raro: Verme Domado (guarda 9 itens, igual ao do Toji).',
      'Variações': 'Cor da pele, listra no dorso, tamanho.',
    },
  });
})();
