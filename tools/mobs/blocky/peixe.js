// Peixe gigante (Mar do Sul): o peixão que o Goku pesca no começo da série. Corpo de carpa enorme, mandíbula de baixo
// pra fora com dentes, bigodes compridos, nadadeiras de raios.
'use strict';
(() => {
  const { n3, mix, mul, cel } = ENG;
  const fr = x => x - Math.floor(x);
  const BACK = [40, 96, 124], FLANK = [222, 164, 72], BELLY = [242, 230, 198], FIN = [226, 96, 62];

  function hide(q, scales) {
    // costas azul-petróleo, flanco dourado, barriga creme; fronteiras onduladas pelo ruído
    const t = q.ny + (n3(q.ax * 0.12, q.ay * 0.12, q.az * 0.12, 61) - 0.5) * 0.35 + (q.f === 'top' ? -1 : q.f === 'bottom' ? 1 : 0);
    let base = t < 0.36 ? BACK : t < 0.74 ? FLANK : BELLY;
    const v = 0.62 - 0.25 * q.ny + 0.3 * (n3(q.ax * 0.2, q.ay * 0.2, q.az * 0.2, 62) - 0.5);
    let c = cel(base, v, 0.8, 1.1);
    if (scales && q.f !== 'bottom') {
      // escamas em fileiras desencontradas, tamanho variando um pouco ao longo do corpo
      const sz = 2.4 + 0.5 * n3(q.az * 0.05, 0, 0, 63);
      const side = q.f === 'top' ? q.ax : q.ay;
      const vv = side / (sz * 0.8), row = Math.floor(vv);
      const uu = q.az / sz + row * 0.5;
      const lx = fr(uu) - 0.5, ly = fr(vv);
      const d = Math.sqrt(lx * lx + ly * ly * 0.8);
      if (Math.abs(d - 0.5) < 0.09) c = mul(c, 0.72);
      else if (d < 0.3 && t > 0.36 && t < 0.74) c = mix(c, [255, 222, 140], 0.25);
    }
    // pintas escuras nas costas, cada uma com forma própria
    if (t < 0.4 && n3(q.ax * 0.35, q.ay * 0.35, q.az * 0.35, 64) > 0.74) c = mul(BACK, 0.55);
    // linha lateral
    if ((q.f === 'west' || q.f === 'east') && Math.abs(q.ny - 0.48 - 0.04 * Math.sin(q.az * 0.2)) < 0.03) c = mix(c, [255, 240, 200], 0.5);
    return c;
  }
  const body = q => hide(q, true);
  function head(q) {
    let c = hide(q, false);
    // tampa da guelra: arco escuro perto da nuca
    if ((q.f === 'west' || q.f === 'east') && Math.abs(q.nz - 0.86 + 0.1 * Math.pow(q.ny - 0.5, 2) * 4) < 0.035) c = mul(c, 0.6);
    if (q.f === 'bottom') {
      // céu da boca com os dentes de cima na borda da frente e dos lados
      const edge = q.nz < 0.1 || q.nx < 0.07 || q.nx > 0.93;
      if (q.nz < 0.65) c = edge ? (n3(q.ax * 1.8, 0, q.az * 1.8, 65) > 0.35 ? [250, 248, 236] : [120, 30, 40]) : [128, 32, 44];
    }
    return c;
  }
  function jaw(q) {
    let c = hide(q, false);
    if (q.f === 'top') {
      const edge = q.nz < 0.12 || q.nx < 0.08 || q.nx > 0.92;
      // dentes de baixo, de tamanhos diferentes, e a língua
      if (edge) c = n3(q.ax * 1.6, 0, q.az * 1.6, 66) > 0.3 ? [252, 250, 238] : [120, 26, 36];
      else c = Math.abs(q.nx - 0.5) < 0.2 && q.nz > 0.3 ? [222, 112, 120] : [136, 34, 46];
    }
    if (q.f === 'north' && q.ny < 0.35) c = [252, 250, 238];
    return c;
  }
  const lip = q => cel([224, 132, 92], 0.65 - 0.4 * q.ny + 0.2 * (n3(q.ax * 0.3, q.ay * 0.3, q.az * 0.3, 67) - 0.5), 0.82, 1.1);
  function eye(q) {
    if (q.f !== 'west' && q.f !== 'east') return hide(q, false);
    const dx = q.nz - 0.5, dy = q.ny - 0.5, d = Math.sqrt(dx * dx + dy * dy);
    if (d < 0.22) return dx > 0.05 && dy < -0.05 ? [255, 255, 255] : [16, 14, 12];
    if (d < 0.5) return [246, 206, 60];
    return hide(q, false);
  }
  function rays(q, u, v) {
    // membrana com raios: cada raio com um tom
    const k = Math.floor(u);
    const ray = fr(u) < 0.22;
    let c = cel(FIN, 0.7 - 0.3 * v + 0.2 * n3(k * 1.3, 0, 0, 68), 0.85, 1.12);
    if (ray) c = mul(FIN, 0.62);
    return c;
  }
  function dorsal(q) {
    // espinhos com alturas próprias, mais altos na frente
    const k = Math.floor(q.pz / 1.6);
    const h = (0.45 + 0.55 * (1 - q.nz)) * (0.75 + 0.25 * n3(k * 2.7, 0, 0, 69));
    const tipCut = fr(q.pz / 1.6) * 0.18;
    if (1 - q.ny > h - tipCut) return null;
    return rays(q, q.pz / 1.6, q.ny);
  }
  function caudal(q) {
    // cauda em forquilha: abre a partir da base, com o recorte no meio
    const half = Math.abs(q.ny - 0.5) * 2, z = q.nz;
    if (half > 0.2 + 0.85 * z) return null;
    if (half < (z - 0.5) * 1.5) return null;
    return rays(q, (Math.atan2(q.ny - 0.5, z + 0.05) + 1.6) * 6, z);
  }
  function pect(q) {
    const s = q.b.x < 0 ? 1 - q.nx : q.nx;                       // 0 encostado no corpo, 1 na ponta
    if (Math.abs(q.nz - 0.4) > 0.15 + 0.4 * s) return null;
    if (s > 0.95) return null;
    return rays(q, (q.nz - 0.4) * 9 / (s + 0.3) + 20, s);
  }
  function barbel(q) {
    return cel([206, 140, 96], 0.6 - 0.2 * q.ny + 0.3 * (n3(q.ax, q.ay, q.az, 70) - 0.5), 0.85, 1.08);
  }

  const def = {
    name: 'raiz', pivot: [0, 0, 0], kids: [{
      name: 'corpo', pivot: [0, 6, 0], boxes: [{ x: -9, y: -9, z: -12, w: 18, h: 19, d: 24, p: body }],
      kids: [
        { name: 'dorsal', pivot: [0, -9, -4], boxes: [{ x: -0.5, y: -9, z: -8, w: 1, h: 9, d: 18, p: dorsal }] },
        { name: 'peitD', pivot: [-9, 6, -8], rot: [0, 0, 0.35], boxes: [{ x: -8, y: -0.5, z: -1, w: 8, h: 1, d: 8, p: pect }] },
        { name: 'peitE', pivot: [9, 6, -8], rot: [0, 0, -0.35], boxes: [{ x: 0, y: -0.5, z: -1, w: 8, h: 1, d: 8, p: pect }] },
        {
          name: 'cabeca', pivot: [0, -1, -12], boxes: [
            { x: -8, y: -7, z: -14, w: 16, h: 13, d: 14, p: head },
            { x: -6.5, y: 3, z: -16, w: 13, h: 3, d: 3, p: lip },
            { x: -8.6, y: -4, z: -10, w: 17.2, h: 3.5, d: 3.5, p: eye },
          ], kids: [
            { name: 'mandibula', pivot: [0, 6, -1], boxes: [{ x: -7.5, y: 0, z: -15.5, w: 15, h: 5, d: 15, p: jaw }] },
            { name: 'bigodeD', pivot: [-6, 5, -15], rot: [0.5, 0, 0.35], boxes: [{ x: -0.5, y: 0, z: -0.5, w: 1, h: 8, d: 1, p: barbel }],
              kids: [{ name: 'bigodeD2', pivot: [0, 8, 0], rot: [0.3, 0, 0.1], boxes: [{ x: -0.5, y: 0, z: -0.5, w: 1, h: 8, d: 1, p: barbel }] }] },
            { name: 'bigodeE', pivot: [6, 5, -15], rot: [0.45, 0, -0.3], boxes: [{ x: -0.5, y: 0, z: -0.5, w: 1, h: 6, d: 1, p: barbel }],
              kids: [{ name: 'bigodeE2', pivot: [0, 6, 0], rot: [0.35, 0, -0.15], boxes: [{ x: -0.5, y: 0, z: -0.5, w: 1, h: 5, d: 1, p: barbel }] }] },
          ],
        },
        {
          name: 'cauda1', pivot: [0, 0, 12], boxes: [{ x: -7, y: -7, z: 0, w: 14, h: 15, d: 14, p: body }],
          kids: [{
            name: 'cauda2', pivot: [0, 0, 14], boxes: [{ x: -4.5, y: -5, z: 0, w: 9, h: 10, d: 11, p: body }],
            kids: [{ name: 'caudal', pivot: [0, 0, 10], boxes: [{ x: -0.5, y: -15, z: -1, w: 1, h: 30, d: 15, p: caudal }] }],
          }],
        },
      ],
    }],
  };

  function anim(P, t, mode, a, root) {
    const S = Math.sin;
    const r = (n, x, y, z) => { const o = P[n].obj.rotation; o.x += x; o.y += y; o.z += z; };
    const fast = mode === 'andar' ? 1 : 0;
    const f = 2 + fast * 2.6, amp = 1 + fast * 0.5;
    const w = t * f;
    // nado: a onda corre do corpo pra ponta da cauda, cada pedaço atrasado
    r('corpo', 0, S(w + 0.8) * 0.05 * amp, 0);
    r('cabeca', 0, -S(w + 1.2) * 0.04 * amp, 0);
    r('cauda1', 0, S(w) * 0.16 * amp, 0);
    r('cauda2', 0, S(w - 0.9) * 0.3 * amp, 0);
    r('caudal', 0, S(w - 1.8) * 0.4 * amp, 0);
    r('peitD', 0, S(t * 2.4) * 0.15, S(t * 2.4) * 0.25);
    r('peitE', 0, -S(t * 2.4 + 0.5) * 0.15, -S(t * 2.4 + 0.5) * 0.25);
    r('mandibula', (S(t * 1.1) * 0.5 + 0.5) * 0.12, 0, 0);
    r('bigodeD', S(t * 1.6) * 0.15, 0, S(t * 1.2) * 0.1); r('bigodeD2', S(t * 1.6 - 0.7) * 0.25, 0, 0);
    r('bigodeE', S(t * 1.4 + 1) * 0.15, 0, -S(t * 1.1) * 0.1); r('bigodeE2', S(t * 1.4 + 0.3) * 0.25, 0, 0);
    root.position.y += S(t * 0.9) * 0.06;
    if (mode === 'atacar') {
      // salto: sai da água de boca aberta, morde no alto e cai de volta
      const k = ENG.smooth(0.05, 0.85, a);
      const arc = Math.sin(Math.PI * k);
      root.position.y += 3.4 * arc;
      root.position.z -= 4 * (k - 0.5);
      root.rotation.x += 0.9 * Math.cos(Math.PI * k);
      const open = a < 0.4 ? ENG.smooth(0.1, 0.4, a) : a < 0.5 ? 1 - ENG.smooth(0.44, 0.5, a) : 0;
      r('mandibula', 0.85 * open, 0, 0);
      r('cabeca', -0.18 * open, 0, 0);
      r('cauda1', 0.3 * arc, S(t * 9) * 0.12, 0); r('cauda2', 0.35 * arc, 0, 0); r('caudal', 0.2 * arc, 0, 0);
      r('peitD', 0, 0, -0.6 * arc); r('peitE', 0, 0, 0.6 * arc);
    }
  }

  window.MOBS = window.MOBS || [];
  window.MOBS.push({
    id: 'peixe_gigante', nome: 'Peixe gigante', bioma: 'Mar do Sul', def, anim, attackLen: 2.0, cam: 11, lookY: 2.2, camYaw: -1.15, camPitch: 0.3, refX: 3.8, water: 2.7,
    info: {
      'Tamanho': '5 blocos de comprimento',
      'Vida': '120',
      'Jeito': 'Fica nadando em água funda. Hostil com quem está na água, num barco ou na beira.',
      'Ataque': 'Salta até 4 blocos fora da água de boca aberta e morde (puxa pra água se acertar). Na água, dá rabada que empurra.',
      'Solta': 'Carne de peixe gigante: cozida, enche a fome toda e dá Regeneração I por 10 s.',
    },
  });
})();
