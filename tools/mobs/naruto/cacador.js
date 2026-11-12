// Ninja Caçador da Névoa (Mar da Névoa). Médio, tier 8. Caçador de renegados de Kiri, máscara de bicho, nunca fala.
// Golpes: Prisão de Água (te prende numa bolha de água com a mão dentro dela: sem ar, 2,5 s; acertar ele solta) e
// Senbon (três agulhas no pescoço: paralisa 1 s).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const N = window.NINJA;

  const MASKS = ['raposa', 'gato', 'pássaro'];
  function variant(seed) {
    const v = N.variant(seed);
    const R = FX.rnd(seed + 777);
    v.scarf = null; v.mask = false;
    v.vest = [[70, 78, 92], [84, 92, 88], [60, 66, 76]][Math.floor(R() * 3)];
    v.maskKind = MASKS[Math.floor(R() * MASKS.length)];
    v.maskCol = [[178, 40, 48], [44, 70, 140], [40, 110, 90]][Math.floor(R() * 3)];
    return v;
  }
  function painters(v) {
    const P = N.painters(v);
    const S = v.seed;
    const shade = (base, q, s, lo = 0.8, hi = 1.12) => {
      const f = 0.62 - 0.4 * q.ny + 0.35 * (n3(q.ax * 0.25, q.ay * 0.25, q.az * 0.25, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    // máscara de bicho: branca, marcas pintadas de cada espécie, fendas pros olhos
    const base = P.head;
    P.head = q => {
      if (q.f !== 'north') return base(q);
      const x = Math.floor(q.px), y = Math.floor(q.py);
      if (y <= 0) return base(q);                                                                    // franja aparece por cima
      let c = cel([236, 232, 222], n3(q.ax * 0.6, q.ay * 0.6, q.az, 130 + S), 0.9, 1.04);
      if (y === 3 && (x === 1 || x === 2 || x === 4 || x === 5)) c = [16, 14, 18];                  // fendas
      if (v.maskKind === 'raposa' && ((y === 2 && (x === 1 || x === 5)) || (y >= 4 && y <= 5 && (x === 2 || x === 4)))) c = v.maskCol;
      if (v.maskKind === 'gato' && (y === 5 && x !== 3 || y === 1 && (x === 0 || x === 6))) c = v.maskCol;
      if (v.maskKind === 'pássaro' && x === 3 && y >= 2) c = v.maskCol;
      return c;
    };
    // túnica longa de caçador, faixa na cintura
    P.torso = q => {
      if ((q.f === 'west' || q.f === 'east') && q.ny < 0.3) return shade([28, 30, 36], q, 131);
      let c = shade(v.vest, q, 132, 0.8, 1.12);
      if (q.f === 'north' && Math.abs((q.nx - 0.5) + (q.ny - 0.4) * 0.6) < 0.05) c = mul(c, 0.7);   // gola trespassada
      return c;
    };
    P.pants = q => shade(mul(v.vest, 0.8), q, 133);
    P.wrapArm = q => shade(v.vest, q, 134);
    return P;
  }
  const defOf = v => N.rigDef(painters(v), v, {});
  const lowOf = v => N.rigLow(painters(v), v);

  // ---------- a bolha da prisão: borda que brilha (fresnel), cáusticas correndo por dentro ----------
  function prison() {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, side: THREE.DoubleSide, uniforms: { uT: { value: 0 }, uA: { value: 0 } },
      vertexShader: `varying vec3 vN, vV, vP; void main(){ vec4 w=modelMatrix*vec4(position,1.0); vP=position; vN=normalize(mat3(modelMatrix)*normal); vV=normalize(cameraPosition-w.xyz); gl_Position=projectionMatrix*viewMatrix*w; }`,
      fragmentShader: `varying vec3 vN, vV, vP; uniform float uT, uA;
        float h(vec3 p){ return fract(sin(dot(p, vec3(127.1,311.7,74.7)))*43758.5); }
        float n(vec3 p){ vec3 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
          return mix(mix(mix(h(i),h(i+vec3(1,0,0)),f.x),mix(h(i+vec3(0,1,0)),h(i+vec3(1,1,0)),f.x),f.y),
                     mix(mix(h(i+vec3(0,0,1)),h(i+vec3(1,0,1)),f.x),mix(h(i+vec3(0,1,1)),h(i+vec3(1,1,1)),f.x),f.y),f.z); }
        void main(){
          float fres = pow(1.0-abs(dot(normalize(vN), vV)), 2.2);
          float c = n(vP*3.0+vec3(0.0,uT*0.8,uT*0.5)); c = smoothstep(0.55,0.62,c)-smoothstep(0.62,0.72,c);
          vec3 col = mix(vec3(0.22,0.5,0.78), vec3(0.85,0.95,1.0), fres*0.9+c*0.6);
          float a = (0.22 + fres*0.6 + c*0.3)*uA;
          gl_FragColor = vec4(col, a);
        }`,
    });
    const m = new THREE.Mesh(new THREE.SphereGeometry(1, 40, 28), mat);
    m.at = (p, s, a, t) => { m.visible = a > 0.01; m.position.copy(p); m.scale.set(s, s * 1.08, s); mat.uniforms.uA.value = a; mat.uniforms.uT.value = t; };
    return m;
  }
  // bolhas de ar escapando de quem está preso: cada uma com tamanho e subida próprios
  function airBubbles(n, seed) {
    const R = FX.rnd(seed), g = new THREE.Group(), list = [];
    const mat = new THREE.MeshPhongMaterial({ color: 0xeaf6ff, transparent: true, opacity: 0.6, shininess: 120, depthWrite: false });
    for (let i = 0; i < n; i++) { const m = new THREE.Mesh(new THREE.SphereGeometry(1, 8, 6), mat); g.add(m); list.push({ m, s: 0.02 + R() * 0.04, dx: (R() - 0.5) * 0.2, lag: R(), sp: 0.6 + R() * 0.6 }); }
    g.at = (o, t, k) => {
      g.visible = k > 0.01;
      for (const b of list) { const f = fr(t * b.sp + b.lag); b.m.position.set(o.x + b.dx + 0.03 * Math.sin(t * 6 + b.lag * 9), o.y + f * 0.9, o.z + b.dx * 0.5); b.m.scale.setScalar(b.s * (0.6 + f)); b.m.visible = k > 0.01 && f < 0.95; }
    };
    return g;
  }
  function senbon(n, seed) {
    const R = FX.rnd(seed), g = new THREE.Group(), list = [];
    for (let i = 0; i < n; i++) {
      const m = new THREE.Mesh(new THREE.BoxGeometry(0.015, 0.015, 0.3), new THREE.MeshBasicMaterial({ color: 0xe4e8f0 }));
      g.add(m); list.push({ m, dx: (R() - 0.5) * 0.14, dy: (R() - 0.5) * 0.1, lag: i * 0.05 });
    }
    g.at = (a, b, age) => {
      g.visible = true;
      for (const p of list) {
        const f = (age - p.lag) / 0.16;
        p.m.visible = f > 0;
        if (!p.m.visible) continue;
        const ff = Math.min(1, f), tx = b.x + p.dx, ty = b.y + p.dy;
        p.m.position.set(a.x + (tx - a.x) * ff, a.y + (ty - a.y) * ff, a.z + (b.z - a.z) * ff - (f >= 1 ? -0.1 : 0));
        p.m.lookAt(tx, ty, b.z - 2);
      }
    };
    return g;
  }

  function anim(P, t, mode, a, obj, ent, ctx) {
    const r = (k, x, y, z) => { if (!P[k]) return; const o = P[k].obj.rotation; o.x += x; o.y += y; o.z += z; };
    if (mode === 'parado') {
      // parado como estátua, o peso todo numa perna, braços cruzados atrás
      N.guard(r, P);
      r('bracoD', 0.35, 0, 0.1); r('bracoE', 0.35, 0, -0.1); if (P.anteD) { r('anteD', -0.3, 0.6, 0); r('anteE', 0.05, -0.6, 0); }
      r('cabeca', 0.08, 0.25 * loop([[0, 0], [0.4, 0], [0.45, 1], [0.8, 1], [0.85, 0]], t * 0.2), 0);   // vira a cabeça seco, sem balançar
      return;
    }
    if (mode === 'andar') {
      const ph = t * 1.0;
      for (const [s, off] of [['D', 0], ['E', 0.5]]) { r('perna' + s, 0.8 * loop(N.WALK_T, ph + off), 0, 0); r('canela' + s, 0.8 * loop(N.WALK_K, ph + off), 0, 0); }
      P.quadril.obj.position.y += 0.5 * loop(N.WALK_B, ph);
      r('bracoD', 0.35, 0, 0.1); r('bracoE', 0.35, 0, -0.1);
      return;
    }
    const R = ctx.ref;
    if (mode === 'prisao') {
      const T = t % 4.6;
      R.place(0, -1.5, 0);
      // corre até você, enfia a mão na água que sobe do chão e fecha a bolha; você fica sem ar
      const go = seq([[0.1, 0], [0.45, 1, 'snap']], T);
      const reach = seq([[0.45, 0], [0.7, 1, 'out'], [3.6, 1], [4.0, 0, 'io']], T);
      const form = seq([[0.6, 0], [0.95, 1, 'out'], [3.5, 1], [3.75, 0, 'in']], T);
      N.guard(r, P);
      obj.position.z -= 0.3 * go;
      r('bracoD', -1.5 * reach, 0.2 * reach, 0); if (P.anteD) r('anteD', -0.1 * reach, 0, 0);
      r('bracoE', -0.9 * reach, -0.6 * reach, 0); if (P.anteE) r('anteE', -1.2 * reach, 0, 0);    // selo de uma mão só
      r('tronco', 0.15 * reach, 0, 0); r('cabeca', -0.1 * reach, 0, 0);
      const c = new THREE.Vector3(0, 1.0, -1.5);
      ctx.get('bolha', prison).at(c, 0.25 + 0.75 * form, form, T);
      if (T > 0.6 && T < 1.0) ctx.get('subida', FX.volume).set(new THREE.Vector3(0, 0.3, -1.5), 0.8, (T - 0.6) / 0.7, { seed: 3.1, dens: 3, rise: 0.8, freq: 2, smoke: [0.6, 0.8, 0.95] });
      if (form > 0.5) {
        R.place(0, -1.5, 0.15 * form);
        R.armsOut(0.7 * form);
        R.lean(0.1 * Math.sin(T * 2.2) * form);
        ctx.get('ar', () => airBubbles(12, 81)).at(new THREE.Vector3(0, 1.5, -1.6), T, form);
        ctx.drown && ctx.drown(form);
      }
      if (T > 3.6 && T < 4.2) ctx.get('estouro', () => FX.debris(30, 82, [[150, 210, 250], [210, 238, 255], [110, 176, 226]])).at(c, T - 3.6);
      return;
    }
    if (mode === 'senbon') {
      const T = t % 3.0;
      R.place(0, -4.2, 0);
      // salta pra trás e solta as três agulhas num movimento só, com a mão de baixo pra cima
      const hop = seq([[0.05, 0], [0.3, 1, 'out'], [0.5, 0, 'in']], T);
      const flick = seq([[0.42, 0], [0.5, 1, 'snap'], [0.9, 1], [1.3, 0, 'io']], T);
      N.guard(r, P);
      obj.position.y += 0.6 * hop; obj.position.z += 0.5 * seq([[0.05, 0], [0.5, 1, 'out']], T);
      r('pernaD', -0.6 * hop, 0, 0); r('canelaD', 1.0 * hop, 0, 0); r('pernaE', -0.3 * hop, 0, 0); r('canelaE', 0.8 * hop, 0, 0);
      r('bracoD', 0.4 * (1 - flick) * seq([[0.1, 0], [0.4, 1]], T) - 1.6 * flick, 0.3 * flick, 0);
      r('tronco', -0.1 * flick, -0.3 * flick, 0); r('cabeca', 0.1 * flick, 0.3 * flick, 0);
      if (T > 0.5) {
        ctx.get('agulhas', () => senbon(3, 91)).at(new THREE.Vector3(-0.3, 1.4, -0.4), new THREE.Vector3(0, 1.5, -4.05), T - 0.5);
        if (T > 0.66) {
          // paralisado: duro, um tremor só, e o mundo gira um pouco
          const k = seq([[0.66, 0], [0.72, 1, 'snap'], [1.9, 1], [2.3, 0]], T);
          R.lean(-0.12 * k); R.armsOut(0.15 * k * (1 + 0.2 * Math.sin(T * 40)));
          ctx.dizzy(0.4 * k);
        }
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(2), variant(13), variant(27)];
  fixed[0].maskKind = 'raposa'; fixed[1].maskKind = 'gato'; fixed[2].maskKind = 'pássaro';
  window.MOBS.push({
    id: 'cacador_nevoa', nome: 'Caçador da Névoa', bioma: 'Mar da Névoa', tier: 'Médio · tier 8',
    variants: fixed.map((v, i) => ({ nome: ['Raposa', 'Gato', 'Pássaro'][i], v, def: defOf(v), low: lowOf(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, def: defOf(v), low: lowOf(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['prisao', 'Prisão de água'], ['senbon', 'Senbon']],
    cam: 6, lookY: 1.2, lookZ: { prisao: -0.9, senbon: -2 }, camFar: { prisao: 6, senbon: 8 }, camYaw: { prisao: -1.0, senbon: -1.25 },
    info: {
      'Tamanho': '1,8 bloco',
      'Vida': '110 (tier 8)',
      'Jeito': 'Hostil, sozinho. Persegue quem está com alma roubada (qualquer SubAlma): é o caçador de renegados da Névoa.',
      'Prisão de água': 'Corre até você, a água sobe do chão e fecha numa bolha com a mão dele dentro: 2,5 s sem ar (perde vida aos poucos). Acertar ele ou outro jogador bater nele solta.',
      'Senbon': 'Salta pra trás e solta três agulhas de baixo pra cima, rápidas: no pescoço, paralisa 1 s.',
      'Solta': 'Senbon, máscara de caçador (raro, decorativa).',
      'Variações': 'Máscara de raposa, gato ou pássaro, com a cor das marcas sorteada.',
    },
  });
})();
