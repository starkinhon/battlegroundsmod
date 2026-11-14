// Mini Mechamaru (Kyoto). Tier 9. Bonecos de treino que o Mechamaru montou e largou na escola de Kyoto: um robô
// pequeno de armadura, cabeça de capacete com viseira e dois olhos de luz, mochila de propulsores e um canhão
// embutido no antebraço. Andam duros, com estalo de servo a cada passo; a viseira varre o lugar procurando alvo.
// Golpes: Canhão (o braço abre, as aletas giram, carrega e dispara um raio de energia amaldiçoada em linha) e
// Modo Espada (uma lâmina sai do antebraço; ele liga os propulsores, avança num tranco e corta).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;

  // [nome, armadura, juntas, detalhe, luz]
  const KINDS = [
    ['Branco de Treino', [222, 222, 214], [70, 72, 80], [196, 56, 46], [120, 230, 255]],
    ['Camuflado', [104, 118, 82], [52, 54, 46], [214, 170, 60], [170, 255, 120]],
    ['Protótipo de Cobre', [170, 104, 66], [60, 46, 40], [60, 60, 66], [255, 170, 80]],
  ];
  function variant(seed, kind) {
    const R = FX.rnd(seed);
    const k = kind ?? Math.floor(R() * KINDS.length);
    return { seed, kind: k, armor: KINDS[k][1], joint: KINDS[k][2], accent: KINDS[k][3], light: KINDS[k][4], num: 1 + Math.floor(R() * 9), dents: R(), size: 0.92 + R() * 0.12, antenna: R() < 0.6 };
  }

  // viseira 8x3: dois olhos de luz e a grade da boca embaixo
  const VISOR = ['KEEKKEEK', 'KEEKKEEK', 'KKKKKKKK'];
  const DIG = { 1: ['.#.', '##.', '.#.', '.#.', '###'], 2: ['##.', '..#', '.#.', '#..', '###'], 3: ['##.', '..#', '.#.', '..#', '##.'], 4: ['#.#', '#.#', '###', '..#', '..#'], 5: ['###', '#..', '##.', '..#', '##.'], 6: ['.##', '#..', '###', '#.#', '###'], 7: ['###', '..#', '.#.', '.#.', '.#.'], 8: ['###', '#.#', '###', '#.#', '###'], 9: ['###', '#.#', '###', '..#', '##.'] };
  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.72, hi = 1.14) => {
      const f = 0.62 - 0.3 * q.ny + 0.18 * (n3(q.ax * 0.5, q.ay * 0.5, q.az * 0.5, s + S) - 0.5) + (q.f === 'top' ? 0.22 : 0) - (q.f === 'bottom' ? 0.3 : 0);
      return cel(base, f, lo, hi);
    };
    const P = {};
    // armadura: placas com friso escuro na borda, rebites nos cantos, arranhões e amassados
    const armor = (q, s) => {
      let c = shade(v.armor, q, s);
      const u = q.f === 'north' || q.f === 'south' ? q.nx : q.f === 'east' || q.f === 'west' ? q.nz : q.nx, w = q.f === 'top' || q.f === 'bottom' ? q.nz : q.ny;
      if (u < 0.07 || u > 0.93 || w < 0.07 || w > 0.93) c = mul(c, 0.72);
      if ((u < 0.16 || u > 0.84) && (w < 0.16 || w > 0.84) && q.b.w > 2.5) c = mul(c, 1.15);
      if (n3(q.ax * 3, q.ay * 3, q.az * 3, s + 1 + S) > 0.82) c = mix(c, [120, 116, 110], 0.5);
      if (n3(q.ax * 0.4, q.ay * 0.4, q.az * 0.4, s + 2 + S) > 0.7 + 0.2 * v.dents) c = mul(c, 0.86);
      return c;
    };
    P.armor = q => armor(q, 1);
    P.joint = q => { let c = shade(v.joint, q, 3); if (fr(q.ay * 0.9) < 0.3) c = mul(c, 0.75); return c; };
    // peito: número de série pintado e uma faixa de cor
    P.chest = q => {
      let c = armor(q, 4);
      if (q.f !== 'north') return c;
      const x = q.px, y = q.py;
      if (y > q.b.h - 2.2 && y < q.b.h - 1.2) return v.accent;
      const g = DIG[v.num], gx = 2 - Math.floor(x - q.b.w / 2 + 1.5), gy = Math.floor(y - 1.4);   // x espelhado pela raiz
      if (gx >= 0 && gx < 3 && gy >= 0 && gy < 5 && g[gy][gx] === '#') return mul(v.joint, 0.8);
      return c;
    };
    P.head = q => {
      let c = armor(q, 5);
      if (q.f !== 'north') return c;
      const col = Math.floor(q.px * 8 / q.b.w), row = Math.floor((q.py - 1.4) / 1.1);
      if (row >= 0 && row < 3) { const ch = VISOR[row][col]; if (ch === 'E') return mix(v.light, [255, 255, 255], 0.35); return [20, 22, 28]; }
      if (q.py > 4.9 && Math.abs(q.px - q.b.w / 2) < 2 && fr(q.px * 0.9) < 0.45) return mul(v.joint, 0.7);   // grade da boca
      return c;
    };
    P.accent = q => { let c = shade(v.accent, q, 6); if (fr((q.ax + q.ay) * 0.35) < 0.5 && v.kind === 0) c = mix(c, [30, 30, 34], 0.85); return c; };   // faixa de aviso no ombro
    P.barrel = q => { let c = shade([60, 62, 70], q, 7); if (q.f === 'north') { const d = Math.hypot(q.nx - 0.5, q.ny - 0.5); if (d < 0.32) return d < 0.18 ? mix(v.light, [255, 255, 255], 0.3) : [16, 16, 20]; } return c; };
    P.blade = q => { let c = shade([200, 214, 226], q, 8, 0.85, 1.2); if (q.nz < 0.15) c = [250, 252, 255]; if (fr(q.az * 0.6) < 0.1) c = mul(c, 0.85); return c; };
    P.thruster = q => { let c = shade([80, 82, 90], q, 9); if (q.f === 'bottom') { const d = Math.hypot(q.nx - 0.5, q.nz - 0.5); if (d < 0.3) return [20, 18, 18]; } return c; };
    return P;
  }

  function rig(P, v, lo) {
    const arm = s => {
      const n = s < 0 ? 'D' : 'E', gun = s < 0;     // canhão no braço direito, espada no esquerdo
      return {
        name: 'braco' + n, pivot: [s * 6.2, -7.6, 0], rot: [0, 0, -s * 0.12], boxes: [
          { x: -1.4, y: 0, z: -1.4, w: 2.8, h: 4.6, d: 2.8, p: P.joint },
          { x: -2.4, y: -1.4, z: -2.2, w: 4.8, h: 3.2, d: 4.4, p: P.accent },   // ombreira
        ],
        kids: [{
          name: 'ante' + n, pivot: [0, 4.4, 0], boxes: [{ x: -1.8, y: 0, z: -1.8, w: 3.6, h: 5.2, d: 3.6, p: P.armor }, { x: -1.2, y: 5.2, z: -1.2, w: 2.4, h: 1.4, d: 2.4, p: P.joint }],
          kids: gun ? [{
            name: 'canhao', pivot: [0, 5.6, 0], boxes: [{ x: -1.3, y: -0.6, z: -1.3, w: 2.6, h: 2.6, d: 2.6, p: P.barrel }],
            kids: lo ? [] : [0, 1, 2, 3].map(i => ({ name: 'aleta' + i, pivot: [0, 1, 0], rot: [0, i * Math.PI / 2, 0], boxes: [{ x: 1.2, y: -2.6, z: -0.3, w: 1.2, h: 3.6, d: 0.6, p: P.armor }] })),
          }] : [{
            name: 'lamina', pivot: [0, 1.0, -1.9], boxes: [{ x: -0.3, y: 0, z: -0.8, w: 0.6, h: 9, d: 1.6, p: P.blade }, { x: -0.3, y: 9, z: -0.8, w: 0.6, h: 1.2, d: 1.0, p: P.blade }],
          }],
        }],
      };
    };
    const leg = s => {
      const n = s < 0 ? 'D' : 'E';
      return {
        name: 'perna' + n, pivot: [s * 2.5, 15.6, 0], boxes: [{ x: -1.6, y: 0, z: -1.6, w: 3.2, h: 4.2, d: 3.2, p: P.joint }],
        kids: [{ name: 'canela' + n, pivot: [0, 4, 0], boxes: [{ x: -2, y: 0, z: -2, w: 4, h: 3.6, d: 4, p: P.armor }, { x: -2.2, y: 3.4, z: -3.2, w: 4.4, h: 1, d: 5.4, p: P.joint }] }],
      };
    };
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [
        {
          name: 'tronco', pivot: [0, 15.6, 0], boxes: [
            { x: -3.2, y: -2.4, z: -2.4, w: 6.4, h: 2.8, d: 4.8, p: P.joint },
            { x: -4.6, y: -10, z: -3.2, w: 9.2, h: 7.8, d: 6.4, p: P.chest },
            // mochila com dois propulsores
            { x: -3.6, y: -9.4, z: 3.0, w: 7.2, h: 6.4, d: 2.6, p: P.armor },
            { x: -3.4, y: -4.2, z: 3.4, w: 2.4, h: 2.6, d: 2.4, p: P.thruster }, { x: 1.0, y: -4.2, z: 3.4, w: 2.4, h: 2.6, d: 2.4, p: P.thruster },
          ],
          kids: [
            {
              name: 'cabeca', pivot: [0, -10, -0.2], boxes: [
                { x: -3.6, y: -6.4, z: -3.6, w: 7.2, h: 6.4, d: 7.2, p: P.head },
                { x: -4.0, y: -7.0, z: -3.0, w: 8.0, h: 1.0, d: 6.4, p: P.armor },
                ...(v.antenna && !lo ? [{ x: 2.6, y: -10.4, z: 1.4, w: 0.6, h: 3.6, d: 0.6, p: P.joint }, { x: 2.4, y: -11.0, z: 1.2, w: 1, h: 1, d: 1, p: P.accent }] : []),
              ],
            },
            arm(-1), arm(1),
          ],
        },
        leg(-1), leg(1),
      ],
    };
  }

  // raio do canhão: cilindro aditivo com miolo branco, borda da cor da luz e anéis que correm
  function beam(color) {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide,
      uniforms: { uC: { value: new THREE.Color(...color) }, uK: { value: 0 }, uT: { value: 0 } },
      vertexShader: 'varying vec2 vU; varying vec3 vN; varying vec3 vV; void main(){ vU=uv; vN=normalize(normalMatrix*normal); vec4 mv=modelViewMatrix*vec4(position,1.0); vV=normalize(-mv.xyz); gl_Position=projectionMatrix*mv; }',
      fragmentShader: `varying vec2 vU; varying vec3 vN; varying vec3 vV; uniform vec3 uC; uniform float uK, uT;
        void main(){ float f = abs(dot(vN, vV)); float ring = 0.75 + 0.25*sin(vU.y*60.0 - uT*40.0);
          vec3 c = mix(uC, vec3(1.0), pow(f, 3.0)); float a = pow(f, 1.4) * uK * ring; gl_FragColor = vec4(c*a*1.6, a); }`,
    });
    const m = new THREE.Mesh(new THREE.CylinderGeometry(1, 1, 1, 16, 1, true), mat);
    m.frustumCulled = false;
    m.at = (a, b, r, k, t) => {
      m.visible = k > 0.01; const d = b.clone().sub(a);
      m.position.copy(a).addScaledVector(d, 0.5); m.scale.set(r, d.length(), r);
      m.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), d.normalize());
      mat.uniforms.uK.value = k; mat.uniforms.uT.value = t;
    };
    return m;
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const s = e.v.size; e.obj.children[0].scale.set(s, s, s); obj.position.y += 1.5 * (s - 1);
    JJFX.dissolve(e, 0);
    const L = e.v.light.map(c => c / 255);
    const W = (n, x, y, z) => { obj.updateMatrixWorld(true); return new THREE.Vector3(x, y, z).applyMatrix4(P[n].obj.matrixWorld); };
    const eyes = (k = 1) => { for (const [i, ex] of [[0, -2.0], [1, 2.0]]) ctx.get('olho' + i, () => DBFX.glow(L)).at(W('cabeca', ex, -3.4, -3.9), 0.05, 0.9 * k, t, 0.7); };
    // servo: o movimento é em degraus (posição arredondada), não liso
    const step = (x, n = 6) => Math.round(x * n) / n;
    const fold = (k = 1) => { if (P.lamina) P.lamina.obj.scale.set(1, Math.max(0.02, 1 - k), 1); };    // lâmina recolhida dentro do antebraço
    const thrust = (k, key = 'jato') => { for (const [i, x] of [[0, -2.2], [1, 2.2]]) { const p = W('tronco', x, -1.4, 4.6); if (k > 0.02) ctx.get(key + i, FX.volume).set(p.clone().add(new THREE.Vector3(0, -0.12, 0.12)), 0.12 * k + 0.04, FX.clamp01(0.1 + 0.25 * fr(t * 6 + i * 0.5)), { fire: 1.2, seed: 33 + i + Math.floor(t * 6 + i * 0.5), dens: 8, rise: -1.2, squash: 1.4, smoke: L.map(c => c * 0.6) }); } };

    if (mode === 'parado') {
      // fica em guarda, parado demais; a cabeça vira em estalos varrendo o lugar, um braço mexe e trava
      const scan = step(Math.sin(t * 0.7), 4);
      r('cabeca', 0, 0.6 * scan, 0);
      r('bracoD', -0.15, 0, 0); r('anteD', -0.4, 0, 0); r('bracoE', -0.1, 0, 0); r('anteE', -0.3, 0, 0);
      const twitch = seq([[0, 0], [0.04, 1, 'snap'], [0.3, 1], [0.34, 0, 'snap']], fr(t / 3.1) * 1.6);
      r('anteE', -0.4 * twitch, 0, 0);
      fold(); eyes(0.8 + 0.2 * (fr(t * 0.9) < 0.92 ? 1 : 0));
      return;
    }
    if (mode === 'andar') {
      // marcha dura de boneco: perna e braço retos, em degraus, cada passo dá um "clac"
      const ph = t * 4.5, sw = step(Math.sin(ph), 3);
      r('pernaD', 0.5 * sw, 0, 0); r('pernaE', -0.5 * sw, 0, 0);
      r('bracoD', -0.45 * sw, 0, 0); r('bracoE', 0.45 * sw, 0, 0);
      r('tronco', 0.06, 0, 0); obj.position.y += 0.03 * Math.abs(sw);
      fold(); eyes(1);
      return;
    }
    if (mode === 'canhao') {
      const T = t % 4.8;
      const R = ctx.ref; const RZ = -4.2; R.place(0, RZ, 0); R.spin(Math.PI);
      // firma as pernas, levanta o braço direito reto, as aletas abrem e giram, a boca do canhão carrega e
      // dispara um raio grosso; o recuo empurra ele pra trás
      const aim = seq([[0.2, 0], [0.7, 1, 'io'], [3.4, 1], [4.1, 0, 'io']], T);
      r('bracoD', -1.55 * aim, 0, 0.12 * aim); r('anteD', 0, 0, 0); r('tronco', 0, 0.25 * aim, 0); r('cabeca', 0, -0.25 * aim, 0);
      r('pernaD', -0.3 * aim, 0, 0); r('pernaE', 0.3 * aim, 0, 0); r('canelaE', 0.3 * aim, 0, 0);
      const open = seq([[0.7, 0], [1.0, 1, 'out'], [3.3, 1], [3.7, 0]], T);
      for (let i = 0; i < 4; i++) r('aleta' + i, 0, 0, -0.9 * open);
      if (P.canhao) P.canhao.obj.rotation.y += (T > 0.9 && T < 3.4) ? (T - 0.9) * (T - 0.9) * 6 : 0;
      const charge = FX.clamp01((T - 1.0) / 1.0);
      const muzzle = W('canhao', 0, 2.4, 0);
      if (T > 1.0 && T < 2.0) {
        ctx.get('carga', () => DBFX.glow(L)).at(muzzle, 0.06 + 0.16 * charge, 1.2 * charge, t);
        ctx.get('suga', () => DBFX.grains({ n: 30, seed: 88, add: true, palette: [e.v.light, [255, 255, 255]], size: [0.012, 0.025], drag: 0, grav: 0, life: 0.5, spread: 0.6, cone: R2 => { const a2 = R2() * 6.28, el = (R2() - 0.5) * 2; return [-Math.cos(a2) * 1.6, -el * 0.8, -Math.sin(a2) * 1.6]; } })).at(muzzle.clone().add(new THREE.Vector3(0, 0, 0)), fr(T * 2) * 0.5, -9);
      }
      const fire = seq([[2.0, 0], [2.06, 1, 'snap'], [2.8, 0.85], [3.2, 0, 'in']], T);
      if (fire > 0) {
        const end = new THREE.Vector3(muzzle.x, muzzle.y, RZ - 3.5);
        ctx.get('raio', () => beam(L)).at(muzzle, end, 0.11 + 0.05 * fire, fire, t);
        ctx.get('raio2', () => beam([1, 1, 1])).at(muzzle, end, 0.04, fire, t + 1);
        ctx.get('boca', () => DBFX.glow(L)).at(muzzle, 0.22, 1.5 * fire, t);
        ctx.get('luzR', () => DBFX.light(new THREE.Color(...L))).at(muzzle.clone().lerp(end, 0.3), 3 * fire, 5);
        obj.position.z += 0.25 * Math.min(1, (T - 2.0) * 8) * (1 - FX.clamp01((T - 3.0) / 0.8));
        if (T < 2.25) { ctx.flash(muzzle, 1.4); ctx.shake(0.5); }
        ctx.get('faiscas', () => DBFX.grains({ n: 40, seed: 89, add: true, palette: [e.v.light, [255, 255, 255]], size: [0.015, 0.035], drag: 1.5, grav: 3, life: 0.5, spread: 0.8, cone: R2 => { const a2 = R2() * 6.28; return [Math.cos(a2) * 2.5, 1 + R2() * 2, 2 + R2() * 2]; } })).at(new THREE.Vector3(0, 1.2, RZ - 0.1), fr(T * 2.5) * 0.4, 0.02);
      }
      const hit = seq([[2.05, 0], [2.1, 1, 'snap'], [3.0, 1], [3.6, 0]], T);
      R.place(0, RZ - 0.9 * FX.EASE.out(FX.clamp01((T - 2.05) / 0.8)), 0); R.lean(-0.35 * hit); R.armsOut(0.3 * hit);
      eyes(1 + charge * (T < 3.2 ? 1 : 0)); fold();
      return;
    }
    if (mode === 'espada') {
      const T = t % 4.2;
      const R = ctx.ref; const RZ = -3.4; R.place(0, RZ, 0); R.spin(Math.PI);
      // a lâmina vira pra fora num estalo, ele agacha, os propulsores acendem e ele avança num tranco até
      // você, corta na diagonal, passa e para de costas; depois volta andando
      const flip = seq([[0.3, 0], [0.45, 1, 'snap']], T);
      fold(1 - flip);
      const crouch = seq([[0.5, 0], [0.9, 1, 'io'], [1.0, 0.5, 'snap'], [1.4, 0.2], [2.0, 0, 'io']], T);
      r('pernaD', -0.5 * crouch, 0, 0); r('pernaE', -0.5 * crouch, 0, 0); r('canelaD', 0.8 * crouch, 0, 0); r('canelaE', 0.8 * crouch, 0, 0);
      obj.position.y -= 0.12 * crouch;
      const dash = seq([[1.0, 0], [1.22, 1, 'out'], [2.4, 1], [3.6, 0, 'io']], T);
      obj.position.z += (RZ + 0.85) * dash;
      r('tronco', 0.3 * crouch + 0.3 * seq([[1.0, 0], [1.1, 1], [1.4, 0]], T), 0, 0);
      thrust(seq([[0.85, 0], [0.95, 1, 'snap'], [1.3, 0.6], [1.5, 0]], T));
      const cut = seq([[1.12, 0], [1.26, 1, 'snap'], [2.2, 1], [2.8, 0, 'io']], T);
      r('bracoE', -2.2 * (1 - cut) * flip + 0.2 * cut, 0, -0.9 * cut); r('anteE', -0.4 * flip, 0, 0);
      if (T > 1.12 && T < 1.5) ctx.slash(new THREE.Vector3(0.1, 1.2, RZ), (T - 1.12) / 0.38);
      const hit = seq([[1.2, 0], [1.26, 1, 'snap'], [2.4, 0.6], [3.2, 0]], T);
      R.lean(0.35 * hit); R.punch(0.6 * hit); R.spin(Math.PI + 0.4 * hit);
      if (T > 1.2 && T < 1.4) { ctx.flash(new THREE.Vector3(0, 1.2, RZ), 1.1); ctx.shake(0.5); }
      if (T > 2.4) { const ph = (T - 2.4) * 6, sw = step(Math.sin(ph), 3) * (1 - FX.clamp01((T - 3.5) / 0.1)); r('pernaD', 0.4 * sw, 0, 0); r('pernaE', -0.4 * sw, 0, 0); }
      eyes(1 + 0.6 * cut);
      return;
    }
    if (mode === 'morte') {
      // leva o golpe, solta faíscas pelas juntas, os olhos falham, ajoelha travando em degraus e cai de cara;
      // o boneco vira cinza (a energia que movia ele se desfaz)
      const T = t % 4.0;
      const R = ctx.ref; R.place(0, -1.9, 0); R.spin(Math.PI);
      R.punch(seq([[0.15, 0], [0.25, 1, 'snap'], [0.6, 1], [0.9, 0]], T));
      const knee = step(seq([[0.4, 0], [1.2, 1]], T), 4), fall = seq([[1.4, 0], [1.8, 1, 'in']], T);
      r('pernaD', -1.3 * knee, 0, 0); r('pernaE', -1.3 * knee, 0, 0); r('canelaD', 1.6 * knee, 0, 0); r('canelaE', 1.6 * knee, 0, 0);
      obj.position.y -= 0.3 * knee;
      P.tronco.obj.rotation.x += 1.35 * fall;
      obj.position.y -= 0.15 * fall; obj.position.z -= 0.25 * fall;
      r('bracoD', 0.3 * fall, 0, 0.4); r('bracoE', 0.3 * fall, 0, -0.4); r('cabeca', 0, 0, 0.3 * knee);
      fold();
      // nenhum membro escora o morto: o corpo encosta no chão (teste R5)
      JJFX.floorLimbs(obj, P, ['tronco'], ['bracoD', 'bracoE', 'pernaD', 'pernaE'], fall);
      const blinkE = fr(t * 7) < 0.5 ? 1 : 0.2;
      eyes(Math.max(0, 1 - T * 0.5) * blinkE);
      if (T > 0.25 && T < 2.4) ctx.get('faisca', () => DBFX.grains({ n: 36, seed: 95, add: true, palette: [e.v.light, [255, 240, 200], [255, 255, 255]], size: [0.012, 0.028], drag: 1, grav: 7, life: 0.5, spread: 1.6, cone: R2 => [(R2() - 0.5) * 2.4, 1 + R2() * 2, (R2() - 0.5) * 2.4] })).at(W('tronco', 0, -6, 0), T - 0.25, 0.02);
      if (T > 0.22 && T < 0.4) ctx.flash(new THREE.Vector3(0, 1, -0.8), 1 - (T - 0.22) / 0.18);
      const dis = FX.clamp01((T - 2.0) / 1.5);
      JJFX.dissolve(e, dis, 0, 1.5);
      if (T > 2.0) ctx.get('cinza', () => JJFX.ash(29, 90)).at(new THREE.Vector3(0, 0.2, -0.4), T - 2.0, 0.02);
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(5, 0), variant(23, 1), variant(61, 2)];
  const def = v => ({ def: rig(painters(v), v, false), low: rig(painters(v), v, true) });
  window.MOBS.push({
    id: 'mechamaru', nome: 'Mini Mechamaru', bioma: 'Kyoto', tier: 'Grau 4 · tier 9',
    variants: fixed.map(v => ({ nome: KINDS[v.kind][0], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado (' + KINDS[v.kind][0] + ')', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Marchar'], ['canhao', 'Canhão'], ['espada', 'Modo Espada'], ['morte', 'Desligado']],
    cam: 3.8, lookY: 0.9, lookZ: { canhao: -1.8, espada: -2.0, morte: -0.6 }, camFar: { canhao: 7.6, espada: 6.4, morte: 4.8 }, camYaw: { canhao: -1.15, espada: -1.1, morte: -1.0 },
    info: {
      'Tamanho': '1,4 bloco de altura (meio jogador)',
      'Vida': '24 (armadura: flecha e soco fazem pouco; machado e explosão fazem dobrado)',
      'Jeito': 'Grupos de 2 ou 3 no pátio da escola de Kyoto e nos telhados, de guarda. Não fogem nunca: o Mechamaru controla de longe e não sente dor.',
      'Canhão': 'Levanta o braço, as aletas abrem e giram (1 s carregando: dá pra ver a luz juntando) e solta um raio em linha reta de 6 blocos que empurra você pra trás. Sair da linha basta.',
      'Modo Espada': 'Abre a lâmina do antebraço, liga os propulsores e avança num tranco de 3 blocos cortando. Depois do tranco fica 1 s parado de costas (a hora de bater).',
      'Fraqueza': 'A mochila: golpe nos propulsores desliga ele 3 s. Água dá curto (atordoado).',
      'Solta': 'Sucata Amaldiçoada, Engrenagem; raro: Núcleo de Marionete (faz um Mini Mechamaru seguir você).',
      'Variações': 'Branco de treino, camuflado ou protótipo de cobre; número de série no peito; com ou sem antena.',
    },
  });
})();
