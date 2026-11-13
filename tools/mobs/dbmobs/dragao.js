// Dragão (Paozu, Terras do Dragão e Deserto). Grandão e bonzinho, como os dragões que as crianças montam no desenho:
// cara redonda, olho grande, dois chifres pra trás, asas de morcego, barriga creme em gomos e cauda com ponta de folha.
// Dá pra domar e MONTAR: voa com você nas costas. Selvagem, só ataca se apanhar.
// Golpes: Bafo de Fogo (enche o peito e solta um jato de fogo) e, montado, você manda ele soltar o bafo também.
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const V3 = (x, y, z) => new THREE.Vector3(x, y, z);

  const SKINS = [[150, 120, 176], [80, 150, 130], [200, 110, 70], [110, 140, 200], [170, 160, 90]];
  const WINGS = [[214, 170, 200], [170, 214, 180], [240, 190, 140], [190, 210, 240], [230, 214, 160]];
  function variant(seed) {
    const R = FX.rnd(seed);
    const i = Math.floor(R() * SKINS.length);
    const skin = SKINS[i].map(c => c * (0.94 + R() * 0.1));
    return { seed, skin, belly: mix(skin, [248, 232, 190], 0.74), wing: WINGS[i], horn: R() < 0.5 ? [236, 222, 190] : [120, 96, 80], spikes: 6 + Math.floor(R() * 3), young: R() < 0.3 };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.85, hi = 1.1) => cel(base, 0.62 - 0.38 * q.ny + 0.1 * (n3(q.ax * 0.12, q.ay * 0.12, q.az * 0.12, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0), lo, hi);
    const hide = (q, s = 1) => {
      const under = q.f === 'bottom' || (q.f !== 'top' && q.ny > 0.62);
      let c = shade(under ? v.belly : v.skin, q, s);
      if (under && fr(q.az * 0.22) < 0.12) c = mul(c, 0.86);
      return c;
    };
    const limb = (q, s = 6) => { let c = shade(v.skin, q, s); if (q.ny > 0.8 && q.f !== 'top') c = mix(c, v.belly, 0.25); return c; };
    const P = { hide, limb };
    P.head = q => {
      let c = hide(q, 2);
      if (q.f === 'west' || q.f === 'east') {
        // olho grande e redondo, brilho em cima: cara de amigo
        const x = q.pz, y = q.py, ex = 4.8, ey = 3.2;
        const d = Math.hypot((x - ex) / 1.6, (y - ey) / 1.5);
        if (d < 1) {
          c = [248, 246, 236];
          if (Math.hypot(x - (ex - 0.6), y - ey - 0.1) < 1.0) c = [30, 24, 40];
          if (Math.hypot(x - (ex - 0.9), y - ey + 0.5) < 0.35) c = [255, 255, 255];
        }
        if (Math.abs(d - 1.15) < 0.15 && y < ey) c = mul(v.skin, 0.6);
      }
      return c;
    };
    P.snout = q => {
      let c = hide(q, 3);
      if (q.f === 'north' && q.py < 2 && (Math.floor(q.px) === 1 || Math.floor(q.px) === 5)) c = [40, 30, 40];
      if ((q.f === 'west' || q.f === 'east') && q.py > 4.2) { const k = fr(q.pz / 1.6); if (k < 0.4 && q.py - 4.2 < 0.8 * (1 - Math.abs(k - 0.2) * 5)) c = [248, 244, 232]; }
      return c;
    };
    P.jaw = q => (q.f === 'top' ? [170, 80, 96] : hide(q, 4));
    P.horn = q => { let c = cel(v.horn, 0.4 + 0.6 * (1 - q.ny) + (q.f === 'top' ? 0.15 : 0), 0.82, 1.12); if (fr(q.ay * 0.7) < 0.15) c = mul(c, 0.86); return c; };
    P.spike = q => shade(mix(v.skin, v.wing, 0.5), q, 8);
    P.claw = q => cel([60, 52, 50], 0.5 + 0.4 * (1 - q.ny), 0.8, 1.4);
    P.wing = (len, depth, inner, D) => q => {
      if (q.f !== 'top' && q.f !== 'bottom') return null;
      const x = D ? 1 - q.px / len : q.px / len;
      const edge = depth * (inner ? 1 - 0.1 * x : (1 - x) * 0.9 + 0.1) - 1.6 * Math.abs(Math.sin(x * Math.PI * (inner ? 2 : 2.5)));
      if (q.pz > edge) return null;
      let c = cel(v.wing, 0.64 + (q.f === 'top' ? 0.16 : -0.14) + 0.06 * (n3(q.ax * 0.3, 0, q.az * 0.3, 7 + S) - 0.5), 0.86, 1.08);
      if (Math.abs(fr(q.pz / Math.max(1, edge) * 3 + x * 0.8) - 0.5) < 0.035) c = mul(c, 0.82);
      if (q.pz < 0.9) c = shade(v.skin, q, 5);
      return c;
    };
    P.fin = q => {
      // ponta da cauda em folha
      const k = q.pz / 7, half = 3.6 * Math.sin(Math.PI * Math.min(1, k)) * (1 - 0.3 * k);
      if (Math.abs(q.px - 3.6) > half || q.f === 'top' || q.f === 'bottom') return null;
      let c = shade(mix(v.skin, v.wing, 0.5), q, 9);
      if (Math.abs(q.px - 3.6) < 0.3) c = mul(c, 0.8);
      return c;
    };
    return P;
  }

  function rigDef(P, v, low) {
    const k = v.young ? 0.85 : 1;
    const wing = s => {
      const D = s < 0, L1 = 15 * k, L2 = 17 * k;
      return {
        name: D ? 'asaD' : 'asaE', pivot: [s * 7.5, -7.5, -6], boxes: [{ x: D ? -L1 : 0, y: -1, z: -1.2, w: L1, h: 2, d: 2.4, p: P.limb }, { x: D ? -L1 : 0, y: -0.2, z: 0.6, w: L1, h: 0.4, d: 14 * k, p: P.wing(L1, 14 * k, true, D) }],
        kids: [{ name: D ? 'pontaD' : 'pontaE', pivot: [s * L1, 0, 0], boxes: [{ x: D ? -L2 : 0, y: -0.7, z: -0.9, w: L2, h: 1.4, d: 1.8, p: P.limb }, { x: D ? -L2 : 0, y: -0.2, z: 0.6, w: L2, h: 0.4, d: 12 * k, p: P.wing(L2, 12 * k, false, D) }, { x: -0.8, y: -0.8, z: -3, w: 1.6, h: 1.6, d: 2.4, p: P.claw }] }],
      };
    };
    const leg = (s, front) => {
      const kk = (front ? 'F' : 'T') + (s < 0 ? 'D' : 'E'), w = front ? 6 : 8;
      if (low) return { name: 'perna' + kk, pivot: [s * 7.4, front ? 4 : 3, front ? -9 : 8], boxes: [{ x: -w / 2, y: -2, z: -w / 2, w, h: 19, d: w, p: P.limb }, { x: -w / 2 - 0.5, y: 15.5, z: -w / 2 - 2, w: w + 1, h: 3, d: w + 2.5, p: P.limb }] };
      return {
        name: 'perna' + kk, pivot: [s * 7.4, front ? 4 : 3, front ? -9 : 8], boxes: [{ x: -w / 2, y: -2, z: -w / 2, w, h: 11, d: w + (front ? 0 : 1), p: P.limb }],
        kids: [{
          name: 'canela' + kk, pivot: [0, 9, 0], boxes: [{ x: -w / 2 + 0.6, y: 0, z: -w / 2 + 0.6, w: w - 1.2, h: 7.5, d: w - 1.2, p: P.limb }],
          kids: [{ name: 'pe' + kk, pivot: [0, 7.5, 0], boxes: [{ x: -w / 2 - 0.5, y: 0, z: -w / 2 - 2, w: w + 1, h: 3, d: w + 2.5, p: P.limb }, { x: -w / 2, y: 1.2, z: -w / 2 - 3.2, w: 1.6, h: 1.6, d: 1.4, p: P.claw }, { x: -0.8, y: 1.2, z: -w / 2 - 3.4, w: 1.6, h: 1.6, d: 1.6, p: P.claw }, { x: w / 2 - 1.6, y: 1.2, z: -w / 2 - 3.2, w: 1.6, h: 1.6, d: 1.4, p: P.claw }] }],
        }],
      };
    };
    const R = FX.rnd(v.seed + 9);
    const spikes = low ? [] : Array.from({ length: v.spikes }, (_, i) => { const h = 2 + R() * 1.6; return { x: -0.9, y: -9 - h, z: -12 + i * (24 / v.spikes), w: 1.8, h: h + 0.2, d: 2.6, p: P.spike }; });
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'corpo', pivot: [0, 0.5, 2], boxes: [{ x: -9, y: -9, z: -14, w: 18, h: 18, d: 28, p: P.hide }, { x: -8, y: -7.5, z: -16, w: 16, h: 15, d: 3, p: P.hide }, ...spikes],
        kids: [
          {
            name: 'pescoco1', pivot: [0, -4, -14], rot: [0.55, 0, 0], boxes: [{ x: -4.2, y: -11, z: -4.2, w: 8.4, h: 12, d: 8.4, p: P.hide }],
            kids: [{
              name: 'pescoco2', pivot: [0, -10.5, 0], rot: [-0.2, 0, 0], boxes: [{ x: -3.6, y: -10, z: -3.6, w: 7.2, h: 10.6, d: 7.2, p: P.hide }],
              kids: [{
                name: 'cabeca', pivot: [0, -9.5, 0], rot: [-0.35, 0, 0], boxes: [
                  { x: -5, y: -7.4, z: -9, w: 10, h: 8.4, d: 12, p: P.head },
                  { x: -3.6, y: -4.6, z: -15.4, w: 7.2, h: 5.6, d: 6.6, p: P.snout },
                ],
                kids: [
                  { name: 'chifreD', pivot: [-3, -6.4, 0.5], rot: [-1.0, 0, -0.15], boxes: [{ x: -0.9, y: -6, z: -0.9, w: 1.8, h: 6.4, d: 1.8, p: P.horn }, { x: -0.6, y: -9, z: 0.2, w: 1.2, h: 3.4, d: 1.2, p: P.horn }] },
                  { name: 'chifreE', pivot: [3, -6.4, 0.5], rot: [-1.0, 0, 0.15], boxes: [{ x: -0.9, y: -6, z: -0.9, w: 1.8, h: 6.4, d: 1.8, p: P.horn }, { x: -0.6, y: -9, z: 0.2, w: 1.2, h: 3.4, d: 1.2, p: P.horn }] },
                  { name: 'mandibula', pivot: [0, 1, -2], boxes: [{ x: -3.4, y: 0, z: -13.2, w: 6.8, h: 2.6, d: 14, p: P.jaw }] },
                ],
              }],
            }],
          },
          wing(-1), wing(1),
          leg(-1, true), leg(1, true), leg(-1, false), leg(1, false),
          {
            name: 'cauda1', pivot: [0, -2, 13.5], rot: [-0.1, 0, 0], boxes: [{ x: -5, y: -5, z: 0, w: 10, h: 10, d: 14, p: P.hide }],
            kids: [{
              name: 'cauda2', pivot: [0, 0, 13.5], rot: [0.06, 0, 0], boxes: [{ x: -3.4, y: -3.4, z: 0, w: 6.8, h: 6.8, d: 14, p: P.hide }],
              kids: [{
                name: 'cauda3', pivot: [0, 0, 13.5], rot: [0.06, 0, 0], boxes: [{ x: -2.2, y: -2.2, z: 0, w: 4.4, h: 4.4, d: 13, p: P.hide }],
                kids: [{ name: 'cauda4', pivot: [0, 0, 12.5], rot: [0.06, 0, 0], boxes: [{ x: -1.3, y: -1.3, z: 0, w: 2.6, h: 2.6, d: 9, p: P.hide }, ...(low ? [] : [{ x: -3.6, y: -0.2, z: 6, w: 7.2, h: 0.4, d: 7, p: P.fin }, { x: -0.2, y: -3.6, z: 6, w: 0.4, h: 7.2, d: 7, p: P.fin }])] }],
              }],
            }],
          },
        ],
      }],
    };
  }

  // quem monta: jogador genérico sentado nas costas, mãos no pescoço
  function riderModel() {
    const sk = [214, 170, 130], shirt = [60, 96, 170], pants = [50, 50, 70], hair = [70, 46, 30];
    const sh = (c, q) => cel(c, 0.6 - 0.35 * q.ny + (q.f === 'top' ? 0.2 : 0), 0.85, 1.1);
    const head = q => {
      if (q.f === 'top' || q.py < 2) return sh(hair, q);
      if (q.f !== 'north') return q.f === 'south' ? sh(hair, q) : sh(sk, q);
      const x = Math.floor(q.px), y = Math.floor(q.py);
      if (y === 4 && (x === 2 || x === 5)) return [40, 40, 60];
      if (y === 6 && x >= 3 && x <= 4) return [150, 90, 80];
      return sh(sk, q);
    };
    const def = {
      name: 'rraiz', pivot: [0, -9.6, -3], kids: [{
        name: 'rcorpo', pivot: [0, 0, 0], boxes: [{ x: -4, y: -12, z: -2, w: 8, h: 12, d: 4, p: q => sh(shirt, q) }], kids: [
          { name: 'rcabeca', pivot: [0, -12, 0], boxes: [{ x: -4, y: -8, z: -4, w: 8, h: 8, d: 8, p: head }] },
          { name: 'rbracoD', pivot: [-6, -11, 0], rot: [-1.0, 0, 0.15], boxes: [{ x: -2, y: -1, z: -2, w: 4, h: 12, d: 4, p: q => (q.py > 8 ? sh(sk, q) : sh(shirt, q)) }] },
          { name: 'rbracoE', pivot: [6, -11, 0], rot: [-1.0, 0, -0.15], boxes: [{ x: -2, y: -1, z: -2, w: 4, h: 12, d: 4, p: q => (q.py > 8 ? sh(sk, q) : sh(shirt, q)) }] },
          { name: 'rpernaD', pivot: [-2, 0, 0], rot: [-1.35, 0, 0.75], boxes: [{ x: -2, y: 0, z: -2, w: 4, h: 12, d: 4, p: q => (q.py > 9 ? sh([60, 40, 30], q) : sh(pants, q)) }] },
          { name: 'rpernaE', pivot: [2, 0, 0], rot: [-1.35, 0, -0.75], boxes: [{ x: -2, y: 0, z: -2, w: 4, h: 12, d: 4, p: q => (q.py > 9 ? sh([60, 40, 30], q) : sh(pants, q)) }] },
        ],
      }],
    };
    const m = ENG.build(def);
    const outer = ENG.toThree(m, ENG.paint(m, m.W, m.H));
    const o = outer.children[0]; outer.remove(o);             // sem a escala de fora: já está dentro do dragão
    o.model = m;
    return o;
  }

  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    // dragãozão: o modelo inteiro 40% maior que os cubos de 1/16 (o filhote 20%); quem monta fica no tamanho normal
    const K = e.v.young ? 1.2 : 1.4;
    obj.scale.set(-K / 16, -K / 16, K / 16); obj.position.y += 1.5 * (K - 1);
    const breath = loop([[0, 0], [0.45, 1], [0.999, 0]], t / 3.4);
    const tail = (k, ph) => { r('cauda1', 0, 0.08 * k * Math.sin(ph), 0); r('cauda2', 0, 0.12 * k * Math.sin(ph - 0.7), 0); r('cauda3', 0, 0.16 * k * Math.sin(ph - 1.4), 0); r('cauda4', 0, 0.2 * k * Math.sin(ph - 2.1), 0); };
    const fold = k => { r('asaD', 0, 1.35 * k, -0.3 * k); r('asaE', 0, -1.35 * k, 0.3 * k); r('pontaD', 0, -2.7 * k, 0.1 * k); r('pontaE', 0, 2.7 * k, -0.1 * k); };
    const flap = (ph, amp) => {
      const w = Math.sin(ph * Math.PI * 2), w2 = Math.sin(ph * Math.PI * 2 - 1);
      r('asaD', 0, 0, -0.85 * w * amp); r('asaE', 0, 0, 0.85 * w * amp); r('pontaD', 0, 0, -0.5 * w2 * amp); r('pontaE', 0, 0, 0.5 * w2 * amp);
      P.corpo.obj.position.y += 1.6 * w * amp;
    };
    const legsTuck = k => { for (const s of ['FD', 'FE']) { r('perna' + s, -0.9 * k, 0, 0); r('canela' + s, 1.3 * k, 0, 0); } for (const s of ['TD', 'TE']) { r('perna' + s, 1.0 * k, 0, 0); r('canela' + s, 0.5 * k, 0, 0); } };
    const rider = on => {
      const o = ctx.attach('cavaleiro', P.corpo, riderModel);
      o.visible = on;
      if (on) { ENG.reset(o.model); o.scale.setScalar(1 / K); }
      return o.model.parts;
    };
    const fire = (T0, T, mouthObj, dir, key, len) => {
      // jato de fogo: nuvens de fogo em volume que saem da boca, crescem e viram fumaça
      const mouth = mouthObj;
      for (let i = 0; i < 9; i++) {
        const st = T0 + i * 0.09, age = (T - st) / 1.0;
        if (age <= 0 || age >= 1) continue;
        const travel = Math.min(1, age * 2.2);
        const c = mouth.clone().add(dir.clone().multiplyScalar(0.4 + len * travel * (0.75 + 0.05 * i)));
        c.y = Math.max(c.y, 0.4);
        ctx.get(key + i, FX.volume).set(c, 0.35 + 0.65 * travel + 0.05 * i, age, { fire: 1, seed: 3.3 + i * 4.71, dens: 9, rise: 0.3, smoke: [0.22, 0.2, 0.2] });
      }
      ctx.flash(mouth.clone().add(dir.clone().multiplyScalar(1.5)), 0.6);
      ctx.ember(mouth, T);
    };
    const mouthOf = () => { obj.updateMatrixWorld(true); return P.mandibula.obj.localToWorld(V3(0, 0, -14)); };

    if (mode === 'parado') {
      rider(false);
      fold(1);
      r('corpo', -0.02 * breath, 0, 0);
      r('pescoco1', 0.03 * breath, 0.12 * Math.sin(t * 0.4), 0); r('cabeca', 0.05 * Math.sin(t * 0.7), 0.15 * Math.sin(t * 0.4 - 0.4), 0.06 * Math.sin(t * 0.3));
      r('mandibula', 0.05 * breath, 0, 0);
      r('asaD', 0, 0, 0.05 * breath); r('asaE', 0, 0, -0.05 * breath);
      tail(0.8, t * 0.8);
      // fumacinha no nariz de vez em quando
      const k = fr(t / 4.0);
      if (k > 0.6) ctx.get('narina', FX.volume).set(mouthOf().add(V3(0, 0.25, 0.3)), 0.18, (k - 0.6) / 0.4, { seed: Math.floor(t / 4) * 2.3, dens: 4, rise: 0.6, smoke: [0.6, 0.58, 0.56] });
      return;
    }
    if (mode === 'andar') {
      rider(false);
      fold(1);
      const ph = t * 0.65;
      for (const [s, off] of [['FD', 0], ['TE', 0.05], ['FE', 0.5], ['TD', 0.55]]) {
        const sw = Math.sin((ph + off) * Math.PI * 2), lift = Math.max(0, Math.sin((ph + off) * Math.PI * 2 + 1.2));
        r('perna' + s, 0.35 * sw, 0, 0); r('canela' + s, 0.45 * lift, 0, 0); r('pe' + s, -0.3 * lift, 0, 0);
      }
      P.corpo.obj.position.y += 0.6 * Math.abs(Math.sin(ph * Math.PI * 2));
      r('corpo', 0, 0.04 * Math.sin(ph * Math.PI * 2), 0.03 * Math.sin(ph * Math.PI * 2));
      r('pescoco1', 0.05 * Math.sin(ph * Math.PI * 4), -0.06 * Math.sin(ph * Math.PI * 2), 0);
      tail(1, ph * Math.PI * 2 + 1);
      return;
    }

    if (mode === 'voar') {
      // voando com você nas costas: asas batendo, pernas recolhidas, cauda de leme; você segura no pescoço
      const R2 = rider(true);
      obj.position.y += 3.2;
      const ph = t * 0.75;
      flap(ph, 1);
      legsTuck(1);
      r('corpo', 0.05 + 0.05 * Math.sin(ph * Math.PI * 2), 0, 0.12 * Math.sin(t * 0.5));
      r('pescoco1', 0.55, 0, 0); r('pescoco2', 0.2, 0.1 * Math.sin(t * 0.5), 0); r('cabeca', -0.5, 0, 0);
      r('cauda1', 0.1, 0, 0); tail(0.5, ph * Math.PI * 2);
      // o corpo do cavaleiro acompanha a batida da asa
      R2.rcorpo.obj.rotation.x += -0.12 - 0.08 * Math.sin(ph * Math.PI * 2 + 0.6);
      R2.rcabeca.obj.rotation.y += 0.25 * Math.sin(t * 0.4);
      // vento passando: riscos finos de ar
      ctx.get('ventoVoo', () => DBFX.grains({ n: 60, seed: 515, palette: [[255, 255, 255], [230, 240, 250]], size: [0.012, 0.025], drag: 0.1, grav: 0, life: 0.6, spread: 2.0, cone: Rr => [(Rr() - 0.5) * 2, (Rr() - 0.5) * 1.2, 9 + Rr() * 4] })).at(V3(0, 4.6, -3), fr(t / 2) * 2, -9);
      return;
    }

    if (mode === 'decolar') {
      // você monta, ele agacha, abre as asas e pula: a primeira batida levanta poeira em roda
      const T = t % 4.6;
      const R2 = rider(true);
      const crouch = seq([[0.3, 0], [0.9, 1, 'out'], [1.1, 1], [1.2, 0]], T);
      const open = seq([[0.5, 0], [1.1, 1, 'out']], T);
      const up = seq([[1.15, 0], [3.4, 1, 'io']], T);
      fold(1 - open);
      if (T > 1.15) flap((T - 1.15) * 1.2, 1.15); else { r('asaD', 0, 0, 0.5 * open); r('asaE', 0, 0, -0.5 * open); }
      P.corpo.obj.position.y += 4 * crouch;
      for (const s of ['FD', 'FE', 'TD', 'TE']) { r('perna' + s, (s[0] === 'F' ? -0.4 : 0.5) * crouch, 0, 0); r('canela' + s, 0.7 * crouch, 0, 0); }
      legsTuck(seq([[1.3, 0], [1.9, 1]], T));
      r('corpo', -0.25 * seq([[1.1, 0], [1.4, 1], [2.5, 0.2]], T), 0, 0);
      r('pescoco1', -0.3 * open, 0, 0); r('cabeca', 0.3 * open, 0, 0);
      r('mandibula', 0.5 * seq([[1.1, 0], [1.2, 1], [1.7, 0]], T), 0, 0);
      obj.position.y += 4.2 * up; obj.position.z -= 2.5 * up;
      R2.rcorpo.obj.rotation.x += -0.25 * crouch - 0.15 * up;
      R2.rbracoE.obj.rotation.x += -1.6 * seq([[2.6, 0], [2.9, 1], [3.6, 1], [3.9, 0]], T);       // você levanta o braço lá em cima
      [1.2, 1.97, 2.75].forEach((bt, i) => {
        const age = (T - bt) / 1.4; if (age <= 0 || age >= 1) return;
        ctx.roarRing(V3(0, 0.03, -0.6 - 0.8 * i), age);
        ctx.get('poAsa' + i, FX.volume).set(V3((i - 1) * 1.6, 0.5, -0.5 - 0.6 * i), 1.1 + 0.2 * i, age, { seed: 4.5 + i * 3.1, dens: 5, rise: 0.25, squash: 0.7, smoke: [0.86, 0.74, 0.56] });
        if (age < 0.12) ctx.shake(0.4);
      });
      return;
    }

    if (mode === 'fogo') {
      const T = t % 3.8;
      rider(false);
      const R = ctx.ref; const RZ = -9; R.place(0, RZ, 0);
      // enche o peito (a barriga incha), estica o pescoço e solta o jato de fogo
      fold(1 - 0.5 * seq([[0.2, 0], [0.7, 1], [2.6, 1], [3.2, 0]], T));
      const inh = seq([[0, 0], [0.75, 1, 'out'], [0.85, 1]], T);
      const blow = seq([[0.8, 0], [0.92, 1, 'snap'], [2.3, 1], [2.8, 0, 'io']], T);
      const pre = inh * (1 - blow);
      r('corpo', -0.12 * pre + 0.06 * blow, 0, 0);
      r('pescoco1', -0.45 * pre + 0.25 * blow, 0, 0); r('pescoco2', -0.2 * pre + 0.15 * blow, 0, 0); r('cabeca', 0.25 * pre - 0.1 * blow, 0, 0);
      r('mandibula', 0.15 * pre + 0.7 * blow, 0, 0);
      tail(0.6, T * 2);
      if (pre > 0.3) ctx.get('brasa', () => DBFX.light(0xff7a30)).at(mouthOf(), 1.5 * pre, 3);
      if (T > 0.85 && T < 3.6) {
        const m = mouthOf(), dir = V3(0, 1.0, RZ).sub(m).normalize();
        fire(0.85, T, m, dir, 'chama', 6.5);
        if (T < 2.4) ctx.shake(0.35);
        ctx.get('faiscas', () => DBFX.grains({ n: 90, seed: 77, palette: [[255, 220, 120], [255, 160, 60], [255, 110, 40]], size: [0.02, 0.05], drag: 1.2, grav: -0.6, life: 0.8, spread: 1.4, add: true, cone: Rr => [(Rr() - 0.5) * 3, (Rr() - 0.3) * 2, 7 + Rr() * 3] })).at(m, T - 0.85, 0, { f: dir, r: V3(-dir.z, 0, dir.x).normalize(), u: V3(0, 1, 0) });
      }
      if (T > 1.3) { const burn = seq([[1.3, 0], [1.5, 1], [2.6, 1], [3.2, 0]], T); R.lean(-0.3 * burn); R.punch(-0.8 * burn); R.place(0, RZ - 0.8 * seq([[1.3, 0], [2.4, 1]], T), 0); }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const fixed = [variant(9), variant(35), variant(70)];
  fixed[0].skin = SKINS[0]; fixed[0].wing = WINGS[0]; fixed[1].skin = SKINS[1]; fixed[1].wing = WINGS[1]; fixed[1].horn = [120, 96, 80]; fixed[2].skin = SKINS[2]; fixed[2].wing = WINGS[2]; fixed[2].young = true;
  for (const v of fixed) v.belly = mix(v.skin, [248, 232, 190], 0.74);
  const def = v => ({ def: rigDef(painters(v), v, false), low: rigDef(painters(v), v, true) });
  window.MOBS.push({
    id: 'dragao', nome: 'Dragão', bioma: 'Dinossauros', tier: 'Montaria · tier 5', refX: 6,
    variants: fixed.map((v, i) => ({ nome: ['Lilás', 'Verde', 'Filhote laranja'][i], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado', v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['decolar', 'Decolar com você'], ['voar', 'Voando com você'], ['fogo', 'Bafo de Fogo']],
    cam: 14, lookY: 2.4, lookZ: { fogo: -4, decolar: -1.5 }, camFar: { fogo: 18, decolar: 17, voar: 13 }, camYaw: { fogo: -1.3, decolar: -0.7, voar: -0.6 },
    info: {
      'Tamanho': '4 blocos de altura (cabeça), 9 de comprimento, asa aberta de 10 blocos: cabe você sentado no pescoço. O filhote é menor (3,5 de altura) e já aguenta uma pessoa.',
      'Vida': '200 (tier 5)',
      'Jeito': 'Neutro e raro: um por vez, pousado em pico de rocha, oásis ou perto de água. Só ataca se apanhar.',
      'Montar': 'Dá carne de dinossauro assada pra ele (3 a 5 vezes, cada vez um coração aparece) até ele abaixar a cabeça: ficou seu. Clique direito pra montar. No chão anda; Espaço decola e sobe, Shift desce, ele voa rápido e longe. Cansa depois de 3 minutos no ar e precisa pousar pra descansar um pouco.',
      'Bafo de Fogo': 'Selvagem, enche o peito e solta um jato de fogo de 7 blocos que põe fogo no chão. Montado, você aperta o ataque e ele solta o bafo pra onde você olha.',
      'Solta': 'Selvagem: escama de dragão (armadura leve, resiste a fogo). Domado não solta nada.',
      'Variações': 'Cor do corpo e da asa, cor dos chifres, quantos espinhos nas costas e filhote sorteados.',
    },
  });
})();
