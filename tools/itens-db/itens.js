// Os 3 itens de Dragon Ball que sobraram no mod (as Esferas já foram refeitas): Senzu, Scouter, Radar do Dragão.
// Alta = 2 texels por pixel de modelo (32 px por bloco), Low poly = 1 texel (16 px por bloco, menos cubos).
(function () {
  const D = window.ITEMS_DB;
  const { SDF, hash3, vnoise, hex, mix, mul, norm, dot, clamp } = D;
  const L = norm([-0.35, 0.85, -0.45]); // luz "pintada" (cima, frente, esquerda): cel em 3 faixas + brilho

  // cel em faixas pela normal da forma (SDF), com leve mancha de ruído
  function cel(pal, sn, p, extra, seed) {
    const k = dot(sn, L) + (extra || 0) + (vnoise(p[0] * 0.9, p[1] * 0.9, p[2] * 0.9, seed || 1) - 0.5) * 0.18;
    if (k < -0.15) return pal[0];
    if (k < 0.42) return pal[1];
    if (k < 0.82) return pal[2];
    return pal[3];
  }
  function jitter(c, p, dens, amt, seed) {
    if (D.iconMode) return c;
    const h = hash3(Math.floor(p[0] * dens + 0.001), Math.floor(p[1] * dens + 0.001), Math.floor(p[2] * dens + 0.001), seed || 7);
    const k = 1 + (h - 0.5) * amt;
    return [clamp(c[0] * k, 0, 255), clamp(c[1] * k, 0, 255), clamp(c[2] * k, 0, 255)];
  }
  const P = s => s.map(hex);

  // ======================= SENZU =======================
  // Feijão verde em forma de rim (curvo), com o "olho" do grão no lado côncavo. Pequeno na mão, como no anime.
  const senzuSdf = p => {
    const x = p[0] - 8, xc = Math.max(-5, Math.min(5, x));
    const z = p[2] - 8 + 0.14 * xc * xc - 0.85;
    // meio levemente mais fino no lado côncavo (+z)
    const pinch = z > 0 ? 0.22 * Math.exp(-x * x / 3) : 0;
    return SDF.ellipsoid([x, p[1] - 8, z + pinch], [0, 0, 0], [3.7, 1.8, 1.75]);
  };
  const SENZU_PAL = P(['#2f5a1c', '#58922f', '#86c143', '#c2e878']);
  D.ITEMS.push({
    id: 'senzu', name: 'Senzu', desc: 'Feijão em forma de rim, verde com o olho do grão no lado de dentro. Cura tudo de uma vez.',
    density: { hi: 2, lo: 1 },
    parts: q => [{ sdf: senzuSdf, bounds: [3, 5, 3, 13, 11, 13], tag: 'grao', bias: q === 'lo' ? 0.15 : 0 }],
    paint(c) {
      const { p, sn, dens } = c;
      const x = p[0] - 8;
      // casca lisa: 3 faixas pela normal da forma, sem mancha forte (no anime o grão é liso)
      const k = dot(sn, L) + (vnoise(p[0] * 0.7, p[1] * 0.7, p[2] * 0.7, 3) - 0.5) * 0.08;
      let col = k < -0.2 ? SENZU_PAL[0] : k < 0.45 ? SENZU_PAL[1] : SENZU_PAL[2];
      // brilho pequeno e único no dorso, perto da ponta da frente
      if (k > 0.9 && x < 0.5 && x > -2.5) col = SENZU_PAL[3];
      // olho do grão (hilo) no lado côncavo
      if (sn[2] > 0.45 && Math.abs(x) < 1.05 && Math.abs(p[1] - 8) < 0.7) {
        const e = (x / 1.05) ** 2 + ((p[1] - 8) / 0.7) ** 2;
        col = e < 0.45 ? hex('#e3cf86') : hex('#3f5f1f');
      }
      return jitter(col, p, dens, 0.05, 5);
    },
    icon: { yaw: 15, pitch: 55, roll: -28 },
    display: {
      gui: { rotation: [30, 28, 0], scale: [1.4, 1.4, 1.4] },
      ground: { translation: [0, 3, 0], scale: [0.7, 0.7, 0.7] },
      fixed: { scale: [1.6, 1.6, 1.6] },
      head: { translation: [0, 10, 0], scale: [1, 1, 1] },
      thirdperson_righthand: { rotation: [10, 0, 0], translation: [0, 2.5, 1.5], scale: [0.75, 0.75, 0.75] },
      thirdperson_lefthand: { rotation: [10, 0, 0], translation: [0, 2.5, 1.5], scale: [0.75, 0.75, 0.75] },
      firstperson_righthand: { rotation: [0, -25, 15], translation: [1.5, 4, 0], scale: [0.9, 0.9, 0.9] },
      firstperson_lefthand: { rotation: [0, -25, 15], translation: [1.5, 4, 0], scale: [0.9, 0.9, 0.9] },
    },
  });

  // ======================= SCOUTER =======================
  // Feito no espaço da cabeça (cubo 0..16 = cabeça do jogador, frente em -z, lado esquerdo do jogador em x baixo):
  // caixa sobre a orelha esquerda com tampa escura e LED, braço pela têmpora até a lente verde translúcida no olho.
  const CREAM = P(['#8a8576', '#c7c1ae', '#e8e3d2', '#faf8ef']);
  const SLATE = P(['#2c3138', '#434a53', '#5f6772', '#838c98']);
  D.ITEMS.push({
    id: 'scouter', name: 'Scouter', desc: 'Usado na cabeça: caixa na orelha esquerda, braço pela têmpora e lente verde translúcida na frente do olho.',
    density: { hi: 2, lo: 1 },
    translucent: true, translucentTags: ['lente'],
    parts(q) {
      if (q === 'lo') return [
        { sdf: p => SDF.roundBox(p, [-0.5, 8.5, 8.5], [1.5, 3, 3], 0.9), bounds: [-2, 5, 5, 1, 12, 12], tag: 'casa' },
        { box: [-3, 7, 7, -2, 10, 10], tag: 'tampa' },
        { box: [-1, 11, 9, 0, 12, 10], tag: 'botao' },
        { box: [-1.5, 9, -1.5, -0.5, 10, 6], tag: 'braco' },
        { box: [-1.5, 6, -2.5, -0.5, 11, -1.5], tag: 'moldura' },
        { box: [-0.5, 6, -2.25, 7, 11, -1.75], tag: 'lente' },
      ];
      return [
        { sdf: p => SDF.roundBox(p, [-0.5, 8.5, 8.5], [1.5, 3.25, 2.75], 1.0), bounds: [-2.5, 5, 5.5, 1.5, 12, 11.5], tag: 'casa' },
        { sdf: p => SDF.cyl(p, [-2.25, 8.5, 8.5], 0, 2.0, 0.4), bounds: [-3, 6, 6, -1.5, 11, 11], tag: 'tampa' },
        { box: [-0.75, 11.25, 8.5, 0.25, 12.25, 10.0], tag: 'botao' },
        { box: [-0.25, 11.25, 6.5, 0.25, 11.75, 7.0], tag: 'botao' },
        { box: [-1.25, 9.0, -1.25, -0.25, 10.5, 6.5], tag: 'braco' },
        { box: [-1.25, 6.0, -1.75, -0.25, 11.0, -0.75], tag: 'moldura' },
        { box: [-0.5, 6.0, -1.5, 7.0, 11.0, -1.0], tag: 'lente' },
      ];
    },
    paint(c) {
      const { p, sn, n, dens, tag, face } = c;
      if (tag === 'casa') {
        let col = cel(CREAM, sn, p, 0, 21);
        if (Math.abs(p[1] - 7.0) < 0.5 / dens) col = hex('#5c594f');           // vinco da carcaça
        if (face === 'north' && Math.hypot(p[0] + 0.4, p[1] - 10.2) < 0.55) col = Math.hypot(p[0] + 0.4, p[1] - 10.2) < 0.3 ? hex('#ffd2c4') : hex('#e2382c'); // LED
        return jitter(col, p, dens, 0.05, 9);
      }
      if (tag === 'tampa') {
        if (n[0] > -0.5) return jitter(SLATE[0], p, dens, 0.05, 3);
        const r = Math.hypot(p[1] - 8.5, p[2] - 8.5);
        let col = cel(SLATE, norm([-0.6, (p[1] - 8.5) * 0.35, (p[2] - 8.5) * -0.35]), p, 0, 4);
        if (Math.abs(r - 1.2) < 0.5 / dens) col = SLATE[0];
        if (r < 0.55) col = hex('#9ff7b0');
        return col;
      }
      if (tag === 'botao') return mul(hex('#a3a8ae'), n[1] > 0.5 ? 1.12 : (n[1] < -0.5 ? 0.6 : 0.85));
      if (tag === 'braco') {
        let col = n[1] > 0.5 ? CREAM[3] : n[1] < -0.5 ? CREAM[0] : CREAM[1];
        if (n[0] < -0.5 && Math.abs(p[1] - 9.75) < 0.26) col = CREAM[2];
        return jitter(col, p, dens, 0.05, 13);
      }
      if (tag === 'moldura') {
        let col = n[1] > 0.5 ? SLATE[3] : n[0] < -0.5 ? SLATE[1] : SLATE[0];
        if (face === 'north' && p[1] > 10.4) col = SLATE[2];
        return col;
      }
      // lente: verde translúcido, mais clara no alto, linhas de varredura, cantos do visor marcados
      if (face !== 'north' && face !== 'south') return [40, 150, 70, 225];
      const u = (p[0] + 0.5) / 7.5, v = (p[1] - 6.0) / 5;
      let col = mix(hex('#1fa64a'), hex('#7af59a'), clamp(v, 0, 1) * 0.8);
      let a = 140 + v * 20;
      const tx = Math.floor((p[0] + 0.5) * dens), ty = Math.floor((p[1] - 6.0) * dens);
      if (ty % 2 === 0) a -= 14;
      const W = Math.round(7.5 * dens), H = Math.round(5 * dens);
      const edge = tx === 0 || ty === 0 || tx === W - 1 || ty === H - 1;
      if (edge) { a = 200; col = mix(col, hex('#0d5a26'), 0.4); }
      const br = (dens >= 2 ? 3 : 2);
      const corner = (x0, y0, sx, sy) => { const dx = (tx - x0) * sx, dy = (ty - y0) * sy; return (dx >= 0 && dy >= 0) && ((dy === 0 && dx < br) || (dx === 0 && dy < br)); };
      if (corner(2, 2, 1, 1) || corner(W - 3, 2, -1, 1) || corner(2, H - 3, 1, -1) || corner(W - 3, H - 3, -1, -1)) { col = hex('#d6ffe0'); a = 230; }
      // retícula pequena perto do olho
      const cx = Math.round(4 * dens), cy = Math.round(2.5 * dens);
      if ((tx === cx && Math.abs(ty - cy) <= (dens >= 2 ? 2 : 1) && ty !== cy) || (ty === cy && Math.abs(tx - cx) <= (dens >= 2 ? 2 : 1) && tx !== cx)) { col = hex('#ff5a4a'); a = 235; }
      void u;
      return [col[0], col[1], col[2], a];
    },
    icon: { yaw: 230, pitch: 15, roll: -8 },
    display: {
      head: { scale: [0.8, 0.8, 0.8] },
      gui: { rotation: [15, 212, 0], translation: [2, 0, 0], scale: [0.8, 0.8, 0.8] },
      ground: { translation: [1, 3, 0], scale: [0.45, 0.45, 0.45] },
      fixed: { rotation: [0, 180, 0], translation: [2, 0, 0], scale: [0.8, 0.8, 0.8] },
      thirdperson_righthand: { rotation: [0, 90, 0], translation: [0, 1, 2], scale: [0.45, 0.45, 0.45] },
      thirdperson_lefthand: { rotation: [0, 90, 0], translation: [0, 1, 2], scale: [0.45, 0.45, 0.45] },
      firstperson_righthand: { rotation: [0, 200, 0], translation: [2, 3, 0], scale: [0.55, 0.55, 0.55] },
      firstperson_lefthand: { rotation: [0, 200, 0], translation: [2, 3, 0], scale: [0.55, 0.55, 0.55] },
    },
  });

  // ======================= RADAR DO DRAGÃO =======================
  // Disco branco com aro metálico e tela verde em grade; botão em cima. A tela pisca as esferas (textura animada).
  const WHITE = P(['#a6a59b', '#d8d7cc', '#f1f0e8', '#ffffff']);
  const STEEL = P(['#434a55', '#68717e', '#9aa4b1', '#cfd6df']);
  const BLIPS = [[5.4, 10.2, 0], [10.6, 6.4, 1], [9.4, 11.4, 2], [4.6, 6.2, 3]];
  const rcyl = (p, c, ax, r, h, rd) => SDF.cyl(p, c, ax, r - rd, h - rd) - rd;
  D.ITEMS.push({
    id: 'radar_dragao', name: 'Radar do Dragão', desc: 'Disco branco com aro de metal e tela em grade. As esferas piscam na tela (textura animada, 4 quadros).',
    density: { hi: 2, lo: 1 }, frames: 4,
    parts(q) {
      if (q === 'lo') return [
        { sdf: p => rcyl(p, [8, 8, 8.5], 2, 6, 1, 0.5), bounds: [2, 2, 7, 14, 14, 10], tag: 'carcaca' },
        { sdf: p => Math.max(SDF.cyl(p, [8, 8, 7], 2, 5.6, 0.5), -SDF.cyl(p, [8, 8, 7], 2, 4.4, 1)), bounds: [2, 2, 6, 14, 14, 8], tag: 'aro' },
        { sdf: p => SDF.cyl(p, [8, 8, 7], 2, 4.7, 0.5), bounds: [3, 3, 6.5, 13, 13, 7.5], tag: 'tela' },
        { box: [7, 13.5, 8, 9, 15.5, 9], tag: 'botao' },
      ];
      return [
        { sdf: p => rcyl(p, [8, 8, 8.5], 2, 6, 1.0, 0.5), bounds: [1.5, 1.5, 7.5, 14.5, 14.5, 9.5], tag: 'carcaca' },
        { sdf: p => Math.max(SDF.cyl(p, [8, 8, 7], 2, 5.75, 0.5), -SDF.cyl(p, [8, 8, 7], 2, 4.5, 1)), bounds: [1.5, 1.5, 6.5, 14.5, 14.5, 7.5], tag: 'aro' },
        { sdf: p => SDF.cyl(p, [8, 8, 7.5], 2, 4.75, 0.5), bounds: [3, 3, 7, 13, 13, 8], tag: 'tela' },
        { sdf: p => SDF.cyl(p, [8, 14.5, 8.5], 1, 0.75, 1.0), bounds: [7, 13.5, 7.5, 9, 15.5, 9.5], tag: 'botao' },
        { sdf: p => SDF.cyl(p, [8, 15.6, 8.5], 1, 1.3, 0.45), bounds: [6.5, 15, 7, 9.5, 16, 10], tag: 'botao' },
      ];
    },
    paint(c) {
      const { p, sn, n, dens, tag, face, frame } = c;
      if (tag === 'carcaca') {
        let col = cel(WHITE, sn, p, 0, 31);
        if (Math.abs(sn[2]) < 0.6 && Math.abs(p[2] - 8.5) < 0.5 / dens) col = hex('#8f8e86');   // emenda lateral
        if (n[2] > 0.5) { // costas: anel rebaixado + 4 parafusos
          const r = Math.hypot(p[0] - 8, p[1] - 8);
          if (Math.abs(r - 3.6) < 0.5 / dens) col = WHITE[0];
          for (let k = 0; k < 4; k++) {
            const a = Math.PI / 4 + k * Math.PI / 2, sx = 8 + Math.cos(a) * 4.6, sy = 8 + Math.sin(a) * 4.6;
            if (Math.hypot(p[0] - sx, p[1] - sy) < 0.5) col = hex('#7e7d76');
          }
        }
        return jitter(col, p, dens, 0.04, 17);
      }
      if (tag === 'aro') return jitter(cel(STEEL, sn[2] < -0.5 ? norm([-(p[0] - 8), -(p[1] - 8) * -1, -6]) : sn, p, 0.05, 41), p, dens, 0.06, 19);
      if (tag === 'botao') {
        let col = cel(WHITE, sn, p, 0.1, 51);
        if (n[1] > 0.5 && Math.abs(Math.hypot(p[0] - 8, p[2] - 8.5) - 0.8) < 0.5 / dens) col = WHITE[0];
        return col;
      }
      // tela
      if (face !== 'north') return [24, 70, 34];
      const x = p[0] - 8, y = p[1] - 8, r = Math.hypot(x, y);
      let col = mix(hex('#2a8a3e'), hex('#13502a'), clamp(r / 4.8, 0, 1) ** 1.5);
      const step = c.icon ? 3 : (dens >= 2 ? 1.5 : 2);
      const gx = Math.abs(((x + 100 * step) % step)) < 0.5 / dens + 0.01, gy = Math.abs(((y + 100 * step) % step)) < 0.5 / dens + 0.01;
      if (gx || gy) col = mix(col, hex('#6fd77c'), 0.55);
      // seta central
      const tx = x * dens, ty = y * dens;
      if (dens >= 2 ? (ty >= -1 && ty < 1.2 && Math.abs(tx) < (1.2 - ty) * 0.75) : (Math.abs(tx) < 0.6 && Math.abs(ty) < 0.6)) col = hex('#f6ffd8');
      // esferas piscando
      for (const [bx, by, ph] of BLIPS) {
        const d = Math.hypot(p[0] - bx, p[1] - by);
        const pulse = [1, 0.7, 0.3, 0.7][(frame + ph) % 4];
        if (d < 0.45) col = mix(hex('#c46a00'), hex('#fff3a0'), pulse);
        else if (d < 1.0 && pulse > 0.6) col = mix(col, hex('#ffb21e'), 0.45 * pulse);
      }
      return col;
    },
    icon: { yaw: 196, pitch: 12, roll: -14, margin: 1.02 },
    display: {
      gui: { rotation: [10, 196, -14], scale: [0.85, 0.85, 0.85] },
      ground: { translation: [0, 3, 0], scale: [0.4, 0.4, 0.4] },
      fixed: { rotation: [0, 180, 0], scale: [0.9, 0.9, 0.9] },
      head: { translation: [0, 14, 0] },
      thirdperson_righthand: { rotation: [70, 0, 0], translation: [0, 1, 1], scale: [0.45, 0.45, 0.45] },
      thirdperson_lefthand: { rotation: [70, 0, 0], translation: [0, 1, 1], scale: [0.45, 0.45, 0.45] },
      firstperson_righthand: { rotation: [-10, 160, 0], translation: [0, 4, -2], scale: [0.6, 0.6, 0.6] },
      firstperson_lefthand: { rotation: [-10, 160, 0], translation: [0, 4, -2], scale: [0.6, 0.6, 0.6] },
    },
  });
})();
