// Soldado do Freeza (Namek, naves e bases do Freeza). Fraco, tier 8. Armadura de batalha com ombreiras, scouter no
// olho, e cada um de uma raça: pele de cor diferente, chifrinhos, barbatanas, careca com calombos ou orelhão.
// Golpes: Rajada Tripla (estica o braço e solta três tiros de ki em sequência) e Bomba de Ki (junta uma bola roxa
// acima da cabeça e arremessa: explode em área).
'use strict';
(() => {
  const { n3, mix, mul } = ENG;
  const { seq, loop } = FX;
  const fr = x => x - Math.floor(x);
  const cel = ENG.cel;
  const V3 = (x, y, z) => new THREE.Vector3(x, y, z);

  // Raças que o Freeza escravizou: cada uma muda cabeça, rosto, pele e às vezes o corpo. Nenhuma é cópia de outra.
  const RACES = {
    comum:    { skins: [[236, 190, 160], [200, 150, 120], [150, 110, 86], [240, 214, 190]], eyes: 'normal' },
    chifrudo: { skins: [[230, 150, 170], [200, 120, 150], [240, 170, 120]], eyes: 'normal' },
    barbatana:{ skins: [[120, 170, 220], [100, 150, 200], [150, 120, 210]], eyes: 'normal' },
    calombo:  { skins: [[240, 170, 90], [210, 150, 80], [220, 200, 150]], eyes: 'grande' },
    orelhao:  { skins: [[170, 140, 210], [140, 190, 150], [200, 180, 220]], eyes: 'normal' },
    peixe:    { skins: [[90, 170, 160], [110, 150, 190], [150, 190, 120]], eyes: 'lado' },
    reptil:   { skins: [[110, 150, 80], [150, 130, 70], [90, 120, 110]], eyes: 'fenda' },
    inseto:   { skins: [[90, 110, 60], [140, 90, 50], [70, 90, 110]], eyes: 'composto' },
    espinho:  { skins: [[220, 120, 150], [200, 100, 110], [180, 140, 100]], eyes: 'pequeno', fat: true },
    pedra:    { skins: [[150, 140, 130], [130, 120, 120], [160, 130, 110]], eyes: 'brilho' },
    bestial:  { skins: [[150, 100, 60], [100, 80, 60], [200, 190, 170]], eyes: 'normal' },
    cone:     { skins: [[200, 220, 140], [160, 210, 200], [230, 200, 220]], eyes: 'grande', thin: true },
  };
  const RACE_NAMES = Object.keys(RACES);
  const SUITS = [[40, 46, 70], [60, 40, 70], [30, 60, 60], [50, 50, 54], [70, 50, 34]];
  const PADS = [[200, 170, 80], [150, 110, 70], [90, 130, 170], [200, 200, 200], [170, 80, 70]];
  const LENS = [[90, 230, 120], [240, 90, 90], [90, 160, 250], [250, 200, 70]];
  function variant(seed, race) {
    const R = FX.rnd(seed);
    const pick = a => a[Math.floor(R() * a.length)];
    race = race || pick(RACE_NAMES);
    const RC = RACES[race];
    return {
      seed, race, skin: pick(RC.skins), suit: pick(SUITS), pad: pick(PADS), lens: pick(LENS), scar: R() < 0.3,
      fat: RC.fat || (!RC.thin && R() < 0.2), tall: RC.thin || R() < 0.2, pads: pick(['duas', 'duas', 'uma', 'gola']), scouter: R() < 0.8,
    };
  }

  function painters(v) {
    const S = v.seed;
    const shade = (base, q, s, lo = 0.84, hi = 1.12) => cel(base, 0.62 - 0.38 * q.ny + 0.12 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, s + S) - 0.5) + (q.f === 'top' ? 0.25 : 0) - (q.f === 'bottom' ? 0.3 : 0), lo, hi);
    const P = {};
    // textura da pele muda com a raça: escama, rachadura de pedra, pelo, quitina
    const skinTex = (q, c) => {
      if (v.race === 'reptil' && fr(q.ax * 0.7 + (Math.floor(q.ay * 0.7) % 2) * 0.5) < 0.2) return mul(c, 0.82);
      if (v.race === 'pedra') { const k = n3(q.ax * 0.8, q.ay * 0.8, q.az * 0.8, 77 + S); if (Math.abs(k - 0.5) < 0.04) return mul(c, 0.6); if (k > 0.72) return mul(c, 1.1); }
      if (v.race === 'bestial' && n3(q.ax * 1.5, q.ay * 3, q.az * 1.5, 79 + S) > 0.62) return mul(c, 0.85);
      if (v.race === 'inseto' && fr(q.ay * 0.5) < 0.12) return mul(c, 0.7);
      if (v.race === 'peixe' && fr(q.az * 0.6 + (Math.floor(q.ay * 0.6) % 2) * 0.5) < 0.15) return mul(c, 0.88);
      return c;
    };
    P.skin = q => skinTex(q, shade(v.skin, q, 1));
    P.suit = q => { let c = shade(v.suit, q, 2); if (q.f !== 'top' && fr(q.ay * 0.25) < 0.06) c = mul(c, 0.86); return c; };
    P.armor = q => {
      let c = shade([236, 230, 214], q, 3, 0.86, 1.08);
      if (q.py > q.b.h - 1.2) c = shade(v.pad, q, 4);
      if (q.f === 'north' && Math.abs(q.px - q.b.w / 2) < 0.25 && q.py < q.b.h - 1.2) c = mul(c, 0.86);
      if (q.f === 'north' && fr(q.py / 3.2) < 0.08) c = mul(c, 0.9);
      if (q.f === 'north' && v.scar && Math.abs(q.px - q.py * 0.7 - 1) < 0.35) c = mul(c, 0.7);   // armadura riscada
      return c;
    };
    P.pad = q => { let c = shade(v.pad, q, 5); if (q.f === 'top' && q.nx > 0.85) c = mul(c, 0.85); return c; };
    P.boot = q => shade([236, 230, 214], q, 6);
    P.glove = q => shade([236, 230, 214], q, 7);
    P.head = q => {
      let c = skinTex(q, shade(v.skin, q, 8, 0.86, 1.08));
      const eyes = RACES[v.race].eyes;
      if (v.race === 'calombo' && q.f === 'top' && n3(q.ax * 0.9, 0, q.az * 0.9, 30 + S) > 0.6) c = mul(c, 0.86);
      // peixe: olho grande de cada lado da cabeça
      if (eyes === 'lado' && (q.f === 'west' || q.f === 'east')) { const d = Math.hypot(q.pz - 2.4, q.py - 3); if (d < 1.4) c = [240, 240, 220]; if (d < 0.7) c = [20, 20, 26]; }
      if (q.f !== 'north') return c;
      const x = Math.min(6, Math.floor(q.px)), y = Math.min(6, Math.floor(q.py)), m = Math.min(x, 6 - x);
      const W = [240, 240, 236], K = [24, 24, 30];
      if (eyes === 'normal') { if (y === 3 && m === 1) c = W; if (y === 3 && m === 2) c = K; if (y === 2 && m >= 1 && m <= 2) c = mul(v.skin, 0.7); }
      if (eyes === 'grande') { if ((y === 2 || y === 3) && (m === 1 || m === 2)) c = (y === 2 && m === 1) ? W : K; }
      if (eyes === 'fenda') { if (y === 3 && (m === 1 || m === 2)) c = m === 2 ? [250, 210, 40] : [30, 30, 20]; if (y === 2 && m <= 2 && m >= 1) c = mul(v.skin, 0.75); }
      if (eyes === 'composto') { if ((y === 2 || y === 3) && m >= 0 && m <= 2) c = (y === 2 && m === 1) ? [120, 200, 120] : [30, 60, 40]; }
      if (eyes === 'pequeno') { if (y === 3 && m === 2) c = K; if (y === 2 && m === 2) c = mul(v.skin, 0.6); }
      if (eyes === 'brilho') { if (y === 3 && (m === 1 || m === 2)) c = [255, 190, 90]; }
      // boca
      if (v.race === 'peixe') { if (y === 5 && m >= 1) c = [70, 30, 40]; if (y === 4 && m >= 1) c = mul(v.skin, 1.15); }
      else if (v.race === 'inseto') { if (y === 5 && m === 2) c = [40, 30, 20]; }
      else if (v.race === 'bestial') { if (y >= 4 && m >= 2) c = shade([226, 210, 190], q, 12); if (y === 4 && m === 3) c = K; if (y === 6 && m >= 2) c = [60, 30, 30]; }
      else if (y === 5 && m >= 2) c = mul(v.skin, 0.6);
      if (v.scar && x === 5 && y >= 1 && y <= 4) c = mix(c, [130, 60, 60], 0.5);
      return c;
    };
    P.horn = q => cel([236, 226, 200], 0.4 + 0.6 * (1 - q.ny), 0.84, 1.1);
    P.fin = q => { if (q.f === 'top' || q.f === 'bottom') return null; const top = 4 * (1 - q.pz / q.b.d) + 0.6; if ((q.b.h - q.py) > top) return null; return shade(mul(v.skin, 0.85), q, 9); };
    P.gill = q => { if (q.f === 'top' || q.f === 'bottom') return null; if (fr(q.py * 0.9) > 0.7) return null; return shade(mix(v.skin, [220, 120, 140], 0.4), q, 13); };
    P.spike = q => cel(mix(v.skin, [250, 240, 220], 0.4), 0.4 + 0.6 * (1 - q.ny), 0.8, 1.15);
    P.fur = q => { if (q.f === 'bottom') return null; if (q.f !== 'top' && q.py > q.b.h * (0.6 + 0.4 * n3(q.ax * 2, 0, q.az * 2, 81 + S))) return null; return skinTex(q, shade(mul(v.skin, 0.85), q, 14)); };
    P.antenna = q => shade(mul(v.skin, 0.7), q, 15);
    P.bulb = q => [140, 230, 120];
    P.scouterBand = q => shade([70, 70, 76], q, 10);
    P.lens = q => mix(v.lens, [255, 255, 255], q.f === 'west' || q.f === 'east' ? 0.1 : 0.25);
    P.gun = q => {
      let c = shade([70, 74, 84], q, 16, 0.8, 1.25);
      if (q.f !== 'top' && q.f !== 'bottom' && fr(q.py * 0.8) < 0.12) c = mul(c, 0.75);
      return c;
    };
    P.cell = q => mix([255, 120, 200], [255, 255, 255], q.f === 'north' ? 0.35 : 0.1);
    return P;
  }

  function rigDef(P, v, low) {
    const w = v.fat ? 10 : v.tall ? 7.4 : 8;
    const L = v.tall ? 1.2 : 0;   // alto: perna e tronco mais compridos
    const arm = s => ({
      name: s < 0 ? 'bracoD' : 'bracoE', pivot: [s * (w / 2 + 1.5), -9 - L, 0], boxes: [{ x: -1.5, y: -1, z: -1.5, w: 3, h: 5 + L, d: 3, p: P.suit }],
      kids: [{
        name: s < 0 ? 'anteD' : 'anteE', pivot: [0, 4 + L, 0], boxes: [{ x: -1.4, y: 0, z: -1.4, w: 2.8, h: 4, d: 2.8, p: v.race === 'pedra' || v.race === 'reptil' ? P.skin : P.suit }],
        kids: [{
          name: s < 0 ? 'maoD' : 'maoE', pivot: [0, 4, 0], boxes: [{ x: -1.5, y: -1, z: -1.5, w: 3, h: 3.4, d: 3, p: P.glove }],
          // pistola de ki na mão direita: corpo, cano e a célula de energia rosa
          kids: s > 0 ? [{ name: 'arma', pivot: [0, 1.6, -0.2], boxes: [{ x: -0.8, y: -0.4, z: -0.8, w: 1.6, h: 4.6, d: 1.6, p: P.gun }, { x: -0.45, y: 4.2, z: -0.45, w: 0.9, h: 2.0, d: 0.9, p: P.gun }, ...(low ? [] : [{ x: -0.5, y: 0.8, z: -1.3, w: 1, h: 2, d: 0.5, p: P.cell }, { x: -0.6, y: -0.4, z: 0.8, w: 1.2, h: 1.2, d: 1.6, p: P.gun }])] }] : [],
        }],
      }],
    });
    const leg = s => ({
      name: s < 0 ? 'pernaD' : 'pernaE', pivot: [s * 2, 12 - L, 0], boxes: [{ x: -2, y: 0, z: -2, w: 4, h: 6 + L / 2, d: 4, p: P.suit }],
      kids: [{ name: s < 0 ? 'canelaD' : 'canelaE', pivot: [0, 6 + L / 2, 0], boxes: [{ x: -2.1, y: 0, z: -2.1, w: 4.2, h: 6 + L / 2, d: 4.2, p: P.boot }] }],
    });
    const ex = [], kids = [];
    const race = v.race;
    if (race === 'chifrudo') ex.push({ x: -3.4, y: -9.4, z: -1, w: 1.2, h: 2.6, d: 1.2, p: P.horn }, { x: 2.2, y: -9.4, z: -1, w: 1.2, h: 2.6, d: 1.2, p: P.horn });
    if (race === 'barbatana' && !low) ex.push({ x: -4.2, y: -8, z: -2, w: 0.4, h: 5, d: 6, p: P.fin }, { x: 3.8, y: -8, z: -2, w: 0.4, h: 5, d: 6, p: P.fin }, { x: -0.2, y: -11, z: -3, w: 0.4, h: 4, d: 7, p: P.fin });
    if (race === 'orelhao') ex.push({ x: -5.6, y: -6, z: -0.6, w: 2, h: 3.4, d: 1.2, p: P.skin }, { x: 3.6, y: -6, z: -0.6, w: 2, h: 3.4, d: 1.2, p: P.skin });
    if (race === 'calombo') ex.push({ x: -2, y: -7.9, z: -2, w: 4, h: 1, d: 4, p: P.head }, { x: -3.2, y: -7.5, z: 0.6, w: 1.8, h: 0.6, d: 1.8, p: P.head });
    if (race === 'peixe') { ex.push({ x: -3.8, y: -2.2, z: -3.9, w: 7.6, h: 1.6, d: 1, p: P.skin }); if (!low) ex.push({ x: -4.1, y: -5, z: -1, w: 0.4, h: 4, d: 3.4, p: P.gill }, { x: 3.7, y: -5, z: -1, w: 0.4, h: 4, d: 3.4, p: P.gill }, { x: -0.2, y: -9.4, z: -2.6, w: 0.4, h: 3, d: 6, p: P.fin }); }
    if (race === 'reptil') { ex.push({ x: -2.2, y: -3.6, z: -6, w: 4.4, h: 3, d: 2.6, p: P.skin }); if (!low) for (let i = 0; i < 4; i++) ex.push({ x: -0.5, y: -8.2 + i * 0.4, z: -3 + i * 1.8, w: 1, h: 1.6 - i * 0.2, d: 1, p: P.spike }); }
    if (race === 'inseto') { ex.push({ x: -2.4, y: -1.2, z: -4.4, w: 1.2, h: 1.8, d: 1.2, p: P.horn }, { x: 1.2, y: -1.2, z: -4.4, w: 1.2, h: 1.8, d: 1.2, p: P.horn }); if (!low) kids.push(...[-1, 1].map(s => ({ name: s < 0 ? 'antD' : 'antE', pivot: [s * 1.8, -7, -2], rot: [-0.4, 0, s * 0.35], boxes: [{ x: -0.3, y: -4.5, z: -0.3, w: 0.6, h: 4.5, d: 0.6, p: P.antenna }, { x: -0.6, y: -5.6, z: -0.6, w: 1.2, h: 1.2, d: 1.2, p: P.bulb }] }))); }
    if (race === 'espinho') { const sp = low ? [[-2, -1], [1, 0.5]] : [[-2.6, -2], [-0.6, -1], [1.4, -2.4], [2.4, 0.6], [-1.6, 1.6], [0.4, 1.8]]; sp.forEach(([x, z], i) => ex.push({ x, y: -8.6 - (i % 2) * 0.6, z, w: 1.2, h: 1.6 + (i % 2) * 0.6, d: 1.2, p: P.spike })); }
    if (race === 'pedra' && !low) ex.push({ x: -3.9, y: -7.6, z: -1, w: 2.4, h: 1.4, d: 3, p: P.head }, { x: 1.5, y: -7.4, z: 0, w: 2.2, h: 1.2, d: 2.6, p: P.head });
    if (race === 'bestial') { ex.push({ x: -1.8, y: -3, z: -5, w: 3.6, h: 2.6, d: 1.6, p: P.skin }, { x: -3.6, y: -9, z: -0.4, w: 1.8, h: 2.4, d: 1.2, p: P.skin }, { x: 1.8, y: -9, z: -0.4, w: 1.8, h: 2.4, d: 1.2, p: P.skin }); if (!low) ex.push({ x: -4, y: -7.4, z: -2, w: 8, h: 4, d: 6, p: P.fur }); }
    if (race === 'cone') { ex.push({ x: -2.8, y: -9, z: -2.8, w: 5.6, h: 2, d: 5.6, p: P.head }, { x: -1.8, y: -10.8, z: -1.8, w: 3.6, h: 1.8, d: 3.6, p: P.head }); if (!low) ex.push({ x: -0.9, y: -12.2, z: -0.9, w: 1.8, h: 1.4, d: 1.8, p: P.head }); }
    const scouter = v.scouter ? [{ x: -3.8, y: -5.6, z: -2, w: 0.6, h: 1.2, d: 4.4, p: P.scouterBand }, { x: -4.1, y: -6.1, z: -2.6, w: 1.2, h: 2, d: 1.4, p: P.scouterBand }, ...(low ? [] : [{ x: -3.9, y: -5.4, z: -4.0, w: 3, h: 2.2, d: 0.4, p: P.lens }])] : [];
    const padBox = s => [{ x: s < 0 ? -5.4 : 0, y: -1.2, z: -2.9, w: 5.4, h: 1.6, d: 5.8, p: P.pad }, ...(low ? [] : [{ x: s < 0 ? -5.0 : 0.4, y: 0.4, z: -2.6, w: 4.6, h: 1.2, d: 5.2, p: P.pad }])];
    const pads = v.pads === 'gola' ? [{ name: 'gola', pivot: [0, -10, 0], boxes: [{ x: -w / 2 + 0.6, y: -1.6, z: -2.6, w: w - 1.2, h: 1.8, d: 5.2, p: P.pad }] }]
      : (v.pads === 'uma' ? [-1] : [-1, 1]).map(s => ({ name: s < 0 ? 'ombD' : 'ombE', pivot: [s * (w / 2 - 0.6), -9.6 - L, 0], rot: [0, 0, -s * 0.32], boxes: padBox(s) }));
    return {
      name: 'raiz', pivot: [0, 0, 0], kids: [{
        name: 'quadril', pivot: [0, 12 - L, 0], boxes: [{ x: -w / 2, y: -2.4, z: -2.1, w, h: 2.6, d: 4.2, p: P.suit }], kids: [{
          name: 'tronco', pivot: [0, -2.2, 0], boxes: [{ x: -w / 2, y: -10 - L, z: -2, w, h: 10 + L, d: 4, p: P.suit }, { x: -w / 2 - 0.4, y: -10.2 - L, z: v.fat ? -3.2 : -2.5, w: w + 0.8, h: 8.8, d: v.fat ? 6.2 : 5, p: P.armor }], kids: [
            { name: 'cabeca', pivot: [0, -10 - L, 0], boxes: [{ x: -3.5, y: -7, z: -3.5, w: 7, h: 7, d: 7, p: P.head }, ...ex, ...scouter], kids },
            arm(-1), arm(1),
            ...pads,
          ],
        }],
      }, leg(-1), leg(1)],
    };
  }

  function stance(r) {
    r('pernaD', -0.15, 0.2, 0.06); r('canelaD', 0.2, 0, 0); r('pernaE', 0.1, -0.1, -0.06); r('canelaE', 0.1, 0, 0);
    r('bracoD', -0.15, 0, 0.12); r('anteD', -0.35, 0, 0); r('bracoE', -0.1, 0, -0.12); r('anteE', -0.3, 0, 0);
  }
  const KI = [1.0, 0.42, 0.85];
  // bola de energia opaca (não estoura em branco contra céu claro): cor cheia na borda, miolo claro, faixas que giram
  function kiBall(c1, c2) {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false,
      uniforms: { uA: { value: new THREE.Color(...c1) }, uB: { value: new THREE.Color(...c2) }, uK: { value: 1 }, uT: { value: 0 } },
      vertexShader: 'varying vec3 vN; varying vec3 vV; varying vec3 vP; void main(){ vN=normalize(normalMatrix*normal); vec4 mv=modelViewMatrix*vec4(position,1.0); vV=normalize(-mv.xyz); vP=position; gl_Position=projectionMatrix*mv; }',
      fragmentShader: `varying vec3 vN; varying vec3 vV; varying vec3 vP; uniform vec3 uA, uB; uniform float uK, uT;
        float h(vec3 p){ p=fract(p*0.3183+vec3(.71,.11,.42)); p*=17.0; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }
        float n(vec3 x){ vec3 i=floor(x), f=fract(x); f=f*f*(3.0-2.0*f);
          return mix(mix(mix(h(i),h(i+vec3(1,0,0)),f.x),mix(h(i+vec3(0,1,0)),h(i+vec3(1,1,0)),f.x),f.y),
                     mix(mix(h(i+vec3(0,0,1)),h(i+vec3(1,0,1)),f.x),mix(h(i+vec3(0,1,1)),h(i+vec3(1,1,1)),f.x),f.y),f.z); }
        void main(){
          float face = abs(dot(vN, vV));
          float a1 = atan(vP.z, vP.x) + uT*5.0 + vP.y*2.0;
          float sw = n(vec3(cos(a1)*2.0, vP.y*3.0 - uT*3.0, sin(a1)*2.0) + uT);
          float core = smoothstep(0.55, 1.0, face + 0.25*sw);
          vec3 c = mix(uA*0.75, uA, smoothstep(0.2, 0.6, face));
          c = mix(c, uB, core);
          c += uA * 0.35 * smoothstep(0.55, 0.85, sw);
          float a = smoothstep(0.0, 0.25, face) * uK;
          gl_FragColor = vec4(c, a);
        }`,
    });
    const m = new THREE.Mesh(new THREE.SphereGeometry(1, 28, 20), mat);
    m.frustumCulled = false;
    m.at = (p, r, k, t) => { m.visible = k > 0.01; m.position.copy(p); m.scale.setScalar(r); mat.uniforms.uK.value = k; mat.uniforms.uT.value = t; };
    return m;
  }
  // tiro da pistola: cápsula comprida de ki (miolo branco, borda rosa) apontada na direção do voo
  function bolt() {
    const b = kiBall(...[[1.0, 0.35, 0.75], [1.0, 0.95, 0.98]]);
    const at0 = b.at;
    b.at = (a, p, r, t) => { at0(p, r, 1, t); const L = Math.max(r * 2, a.distanceTo(p)); b.position.copy(a).add(p).multiplyScalar(0.5); b.lookAt(p); b.scale.set(r, r, L / 2); };
    return b;
  }
  const PURP = [[0.62, 0.22, 0.95], [1.0, 0.85, 1.0]], PINK = [[1.0, 0.35, 0.75], [1.0, 0.92, 0.97]];
  function anim(P, t, mode, a, obj, e, ctx) {
    const r = (n, x, y, z) => { if (!P[n]) return; const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const breath = loop([[0, 0], [0.45, 1], [0.999, 0]], t / 3);
    const handOf = (n, off = V3(0, 3.6, 0)) => { obj.updateMatrixWorld(true); return P[n].obj.localToWorld(off.clone()); };

    if (mode === 'parado') {
      stance(r);
      r('tronco', -0.03 * breath, 0, 0);
      // aperta o botão do scouter de vez em quando
      const scan = seq([[0.6, 0], [0.68, 1, 'out'], [0.85, 1], [0.92, 0]], fr(t / 5));
      r('bracoD', -2.1 * scan, 0, 0.5 * scan); r('anteD', -1.4 * scan, 0, 0); r('cabeca', -0.1 * scan, 0.2 * scan, 0);
      if (scan > 0.8) ctx.get('scan', () => DBFX.glow(e.v.lens.map(c => c / 255))).at(handOf('cabeca', V3(-2.4, -4.3, -4.2)), 0.12, 0.6 + 0.4 * Math.sin(t * 40), t);
      return;
    }
    if (mode === 'andar') {
      const ph = t * 1.1;
      for (const [s, off] of [['D', 0], ['E', 0.5]]) {
        r('perna' + s, 0.5 * Math.sin((ph + off) * Math.PI * 2), 0, 0); r('canela' + s, 0.4 * Math.max(0, Math.sin((ph + off) * Math.PI * 2 + 1.4)), 0, 0);
        r('braco' + s, -0.4 * Math.sin((ph + off) * Math.PI * 2), 0, 0); r('ante' + s, -0.3, 0, 0);
      }
      P.quadril.obj.position.y += 0.5 * Math.abs(Math.sin(ph * Math.PI * 2));
      return;
    }

    if (mode === 'pistola') {
      const T = t % 3.6;
      const R = ctx.ref; const RZ = -7; R.place(0, RZ, 0);
      stance(r);
      // saca a pistola de ki, mira de braço esticado e vai de lado (passo cruzado) atirando seis vezes;
      // cada tiro dá um tranco no braço; o último é carregado (mais grosso)
      const aim = seq([[0, 0], [0.3, 1, 'out'], [2.5, 1], [2.9, 0, 'io']], T);
      const SH = [0.45, 0.68, 0.9, 1.12, 1.34, 1.85];
      const kick = SH.reduce((k, st, i) => k + seq([[st, 0], [st + 0.03, i === 5 ? 1.6 : 1], [st + 0.14, 0]], T), 0);
      const strafe = seq([[0.35, 0], [1.6, 1, 'io']], T) * (1 - seq([[2.5, 0], [3.2, 1, 'io']], T));
      obj.position.x += 1.4 * strafe;
      const step = Math.sin(seq([[0.35, 0], [1.6, 1]], T) * Math.PI * 4) * (T > 0.35 && T < 1.6 ? 1 : 0);
      r('pernaD', 0.4 * step, 0, 0.15 * Math.abs(step)); r('pernaE', -0.4 * step, 0, -0.15 * Math.abs(step)); r('canelaD', 0.3 * Math.max(0, step), 0, 0); r('canelaE', 0.3 * Math.max(0, -step), 0, 0);
      P.quadril.obj.position.y += 0.4 * Math.abs(step);
      r('tronco', 0, -0.35 * aim, 0); r('cabeca', 0, 0.3 * aim, 0);
      // mira: o tronco gira e o braço acompanha o alvo enquanto anda de lado
      const yawTo = Math.atan2(-obj.position.x, -(RZ - obj.position.z)) * aim;
      r('quadril', 0, -yawTo, 0);
      r('bracoE', -1.6 * aim + 0.3 * kick, 0.35 * aim, 0); r('anteE', 0.05 * aim, 0, 0); r('maoE', 0.05 * aim, 0, 0);
      r('bracoD', -0.9 * aim, -0.7 * aim, 0); r('anteD', -0.9 * aim, 0, 0);      // a outra mão apoia
      const muzzle = handOf('maoE', V3(0, 6.6, -0.2));
      if (T > 1.5 && T < 1.85) { const ch = (T - 1.5) / 0.35; ctx.get('carga', () => kiBall(...PINK)).at(muzzle, 0.03 + 0.07 * ch, 1, T); ctx.get('cargaH', () => DBFX.glow(KI)).at(muzzle, 0.1 + 0.15 * ch, 0.3, T); }
      SH.forEach((st, i) => {
        const age = T - st; if (age < 0 || age > 0.9) return;
        const big = i === 5;
        const tgt = V3(0.2 * Math.sin(i * 2.3), 1.0 + 0.25 * Math.cos(i * 1.7), RZ + 0.2);
        const fly = big ? 0.2 : 0.14;
        const f = Math.min(1, age / fly);
        if (f < 1) {
          const p = muzzle.clone().lerp(tgt, f), back = muzzle.clone().lerp(tgt, Math.max(0, f - 0.12));
          ctx.get('bala' + i, bolt).at(back, p, big ? 0.09 : 0.045, T);
        }
        if (age < 0.05) ctx.get('boca' + i, () => DBFX.muzzle()).at(muzzle, ctx.camera, 1 - age / 0.05, st * 9, big ? 0.8 : 0.45);
        if (f >= 1) {
          const k = (age - fly) / 0.5;
          if (k < 1) ctx.get('faisca' + i, () => DBFX.grains({ n: big ? 40 : 18, seed: 140 + i, palette: [[255, 150, 220], [255, 230, 250], [255, 120, 200]], size: [0.012, 0.03], drag: 3, grav: 5, life: 0.4, spread: 0.05, add: true, cone: Rr => { const a = Rr() * 6.28, b = Rr() * 1.5; return [Math.cos(a) * Math.sin(b) * 2.5, Math.cos(b) * 2, 1.5 + Math.sin(a) * Math.sin(b) * 2.5]; } })).at(tgt, age - fly);
          if (big && k < 1) ctx.get('estouro', FX.volume).set(tgt, 0.55, k, { fire: 0.6, seed: 5.5, dens: 9, rise: 0.4, smoke: [0.42, 0.3, 0.48] });
          if (k < 0.15) { ctx.flash(tgt, big ? 0.9 : 0.4); ctx.shake(big ? 0.5 : 0.15); }
        }
      });
      const hitK = SH.reduce((k, st, i) => k + seq([[st + 0.14, 0], [st + 0.18, i === 5 ? 2 : 0.6], [st + 0.4, 0]], T), 0);
      R.lean(-0.15 * hitK - 0.6 * seq([[2.05, 0], [2.3, 1, 'out'], [3.0, 1], [3.5, 0]], T));
      return;
    }

    if (mode === 'rajada') {
      const T = t % 2.8;
      const R = ctx.ref; const RZ = -6.5; R.place(0, RZ, 0);
      stance(r);
      // vira de lado, estica o braço direito com a palma aberta e solta três tiros, o braço dá um tranco a cada um
      const aim = seq([[0, 0], [0.35, 1, 'out'], [1.6, 1], [2.0, 0, 'io']], T);
      const SH = [0.6, 0.86, 1.12];
      const kick = SH.reduce((k, st) => k + seq([[st, 0], [st + 0.03, 1], [st + 0.16, 0]], T), 0);
      r('tronco', 0, 0.55 * aim, 0); r('quadril', 0, 0.3 * aim, 0);
      r('bracoD', -1.55 * aim + 0.25 * kick, -0.5 * aim, 0); r('anteD', 0.05 * aim, 0, 0); r('maoD', -0.9 * aim, 0, 0);
      r('bracoE', 0.2 * aim, 0, -0.3 * aim);
      r('cabeca', 0, -0.45 * aim, 0);
      const palm = handOf('maoD', V3(0, 3, -1.2));
      if (T > 0.35 && T < 1.3) ctx.get('palma', () => kiBall(...PINK)).at(palm, 0.09 + 0.03 * Math.sin(T * 30), 1, T); ctx.get('palmaH', () => DBFX.glow(KI)).at(palm, 0.22, 0.3, T);
      SH.forEach((st, i) => {
        const age = T - st; if (age < 0 || age > 0.9) return;
        const tgt = V3((i - 1) * 0.25, 1.1 + (i === 2 ? 0.3 : 0), RZ + 0.2);
        const f = Math.min(1, age / 0.22);
        const p = palm.clone().lerp(tgt, f);
        if (f < 1) {
          ctx.get('tiro' + i, () => kiBall(...PINK)).at(p, 0.13, 1, T + i);
          ctx.get('halo' + i, () => DBFX.glow(KI)).at(p, 0.3, 0.35, T + i);
          ctx.get('rastro' + i, () => DBFX.tracer(KI)).at(palm, tgt, f, 1.2);
        } else {
          const k = (age - 0.22) / 0.6;
          if (k < 1) ctx.get('boom' + i, FX.volume).set(tgt, 0.45, k, { fire: 0.6, seed: 4.4 + i * 3.1, dens: 9, rise: 0.4, smoke: [0.42, 0.3, 0.48] });
          if (k < 0.2) { ctx.flash(tgt, 0.8); ctx.shake(0.35); }
        }
        if (age < 0.06) ctx.get('clarao', () => DBFX.muzzle()).at(palm, ctx.camera, 1 - age / 0.06, st * 7, 0.7);
      });
      const fl = SH.reduce((k, st) => k + seq([[st + 0.22, 0], [st + 0.28, 1], [st + 0.5, 0]], T), 0);
      R.lean(-0.25 * fl); R.place(0, RZ - 0.25 * seq([[0.8, 0], [1.5, 1]], T), 0);
      return;
    }

    if (mode === 'bomba') {
      const T = t % 3.6;
      const R = ctx.ref; const RZ = -7; R.place(0, RZ, 0);
      stance(r);
      // ergue as duas mãos, a bola roxa cresce estalando, e ele arremessa por cima do ombro
      const raise = seq([[0, 0], [0.4, 1, 'out'], [1.45, 1], [1.55, 0]], T);
      const thr = seq([[1.45, 0], [1.58, 1, 'snap'], [2.0, 1], [2.5, 0, 'io']], T);
      const grow = seq([[0.35, 0], [1.4, 1, 'in']], T);
      r('bracoD', -2.9 * raise - 1.4 * thr, 0, 0.25 * raise); r('bracoE', -2.9 * raise - 1.4 * thr, 0, -0.25 * raise);
      r('anteD', -0.4 * raise, 0, 0); r('anteE', -0.4 * raise, 0, 0);
      r('tronco', -0.25 * raise + 0.4 * thr, 0, 0); r('cabeca', -0.3 * raise + 0.2 * thr, 0, 0);
      r('pernaD', -0.3 * thr, 0, 0); r('canelaD', 0.3 * thr, 0, 0);
      const top = V3(0, 3.0 + 0.2 * grow, 0.1);
      const land = V3(0, 0.5, RZ + 0.3);
      if (T > 0.35 && T < 1.55) {
        ctx.get('bola', () => kiBall(...PURP)).at(top, 0.08 + 0.4 * grow, 1, T);
        ctx.get('miolo', () => DBFX.glow([0.75, 0.3, 1.0])).at(top, 0.15 + 0.75 * grow, 0.3, T * 1.7);
        ctx.get('estalos', () => DBFX.grains({ n: 50, seed: 17, palette: [[230, 150, 255], [255, 220, 255], [180, 90, 250]], size: [0.015, 0.035], drag: 4, grav: 0, life: 0.25, spread: 1.1, add: true, cone: Rr => { const a = Rr() * 6.28, b = Rr() * 3.14; return [Math.sin(b) * Math.cos(a) * 3, Math.cos(b) * 3, Math.sin(b) * Math.sin(a) * 3]; } })).at(top, T - 0.35);
        ctx.get('luzBola', () => DBFX.light(0xb060ff)).at(top, 2.5 * grow, 5);
      }
      if (T >= 1.55 && T < 2.1) {
        const f = (T - 1.55) / 0.55;
        const p = top.clone().lerp(land, f); p.y += 1.6 * Math.sin(Math.PI * f);
        ctx.get('bola', () => kiBall(...PURP)).at(p, 0.48, 1, T);
        ctx.get('miolo', () => DBFX.glow([0.75, 0.3, 1.0])).at(p, 0.9, 0.3, T * 1.7);
      }
      if (T >= 2.1) {
        const k = (T - 2.1) / 1.3;
        ctx.get('explosao', FX.volume).set(land.clone().add(V3(0, 0.4, 0)), 1.5, k, { fire: 0.8, seed: 7.7, dens: 11, rise: 0.5, smoke: [0.4, 0.28, 0.5] });
        ctx.flash(land, Math.max(0, 1 - k * 4)); ctx.shake(Math.max(0, 0.9 - k * 1.5));
        ctx.roarRing(V3(land.x, 0.03, land.z), k * 1.6);
        ctx.get('terra', () => FX.debris(24, 41, [[110, 160, 130], [80, 130, 100], [140, 120, 100], [60, 90, 70]])).at(land.clone(), T - 2.1);
        const fly = seq([[2.1, 0], [2.6, 1, 'out']], T);
        R.place(0, RZ - 2 * fly, 1.0 * Math.sin(Math.PI * fly)); R.lean(-1.2 * Math.min(1, fly * 2) * (1 - seq([[3.1, 0], [3.6, 1]], T)));
      }
      return;
    }
  }

  window.MOBS = window.MOBS || [];
  const ORDER = ['comum', 'chifrudo', 'peixe', 'reptil', 'inseto', 'espinho', 'pedra', 'bestial', 'cone', 'barbatana', 'calombo', 'orelhao'];
  const NAMES = { comum: 'Humano', chifrudo: 'Chifrudo', peixe: 'Peixe', reptil: 'Réptil', inseto: 'Inseto', espinho: 'Gordo de espinhos', pedra: 'Pedra', bestial: 'Bestial', cone: 'Cabeça de cone', barbatana: 'Barbatana', calombo: 'Calombos', orelhao: 'Orelhão' };
  const fixed = ORDER.map((race, i) => variant(7 + i * 13, race));
  const def = v => ({ def: rigDef(painters(v), v, false), low: rigDef(painters(v), v, true) });
  window.MOBS.push({
    id: 'soldado_freeza', nome: 'Soldado do Freeza', bioma: 'Namek e estruturas', tier: 'Fraco · tier 8',
    variants: fixed.map(v => ({ nome: NAMES[v.race], v, ...def(v) })),
    randomVariant: () => { const v = variant(Math.floor(Math.random() * 1e5)); return { nome: 'Sorteado: ' + NAMES[v.race], v, ...def(v) }; },
    anim, modes: [['parado', 'Parado'], ['andar', 'Andar'], ['pistola', 'Pistola de Ki'], ['rajada', 'Rajada Tripla'], ['bomba', 'Bomba de Ki']],
    cam: 6, lookY: 1.1, lookZ: { pistola: -3, rajada: -3, bomba: -3.2 }, camFar: { pistola: 9.5, rajada: 9, bomba: 10 }, camYaw: { pistola: -1.3, rajada: -1.25, bomba: -1.2 },
    info: {
      'Tamanho': '1,85 bloco (chifre e barbatana passam um pouco)',
      'Vida': '34 (tier 8)',
      'Jeito': 'Hostil, em esquadrão de 3 a 4 em Namek e em volta das naves. Um aperta o scouter e chama os outros.',
      'Pistola de Ki': 'Saca a pistola de ki e anda de lado atirando: 5 tiros rápidos e o 6º carregado (a ponta acende rosa antes: é o aviso). Dano dos tiros em pacotes no jogo (Escudo de tier). Atira de longe e mantém distância.',
      'Rajada Tripla': 'Estica o braço e solta três tiros rosados seguidos. No jogo o dano dos três é somado em pacotes (o Escudo de tier não come cada tirinho).',
      'Bomba de Ki': 'Ergue as mãos, junta uma bola roxa por 1 s (dá pra ver e correr) e arremessa: explode em área e abre um buraco.',
      'Solta': 'Scouter quebrado (conserta na bancada), pedaço de armadura; raro: cápsula de nave.',
      'Raças': '12 raças que o Freeza escravizou: humano, chifrudo, peixe (olho do lado e guelras), réptil (focinho, escamas e espinhos), inseto (antenas, olho composto e pinça), gordo de espinhos, pedra (pele rachada), bestial (peludo com focinho), cabeça de cone, barbatana, calombos e orelhão.',
      'Variações': 'Além da raça: cor da pele, corpo gordo ou alto, ombreira dupla, simples ou gola, cor do macacão e da ombreira, lente do scouter (alguns sem scouter) e armadura riscada sorteados.',
    },
  });
})();
