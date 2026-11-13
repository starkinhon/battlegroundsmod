// Bakes the approved Hashirama page (forja/hashirama-boss) into src/main/resources/assets/battlegroundsmod/hashirama/*.bin.gz.
// bake.html = hashirama-boss.html with window.__hs = {seed, rnd, V3, woodTube, ... } exported before its last "})();".
// node bake.js out_dir; then gzip -9 each .bin into the assets folder.
// bake out_dir: exports the static wood of the approved preview (bake.html = hashirama-boss.html + window.__hs) to binary files
const { chromium } = require('playwright'); const path = require('path'); const fs = require('fs');
const out = process.argv[2];
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await b.newPage({ viewport: { width: 400, height: 300 } });
  const errs = []; p.on('pageerror', e => errs.push(e.message));
  await p.route(/cdn\.jsdelivr\.net\/npm\/three@0\.128\.0\/(.*)/, r => { const rel = r.request().url().split('three@0.128.0/')[1]; r.fulfill({ path: path.join(__dirname, 'npm/package', rel), contentType: 'application/javascript' }); });
  await p.route(/fonts\.googleapis/, r => r.fulfill({ body: '', contentType: 'text/css' }));
  await p.goto('file://' + path.join(__dirname, 'bake.html')); await p.waitForTimeout(1500);
  const files = await p.evaluate(() => {
    const H = window.__hs, V3 = H.V3, rnd = H.rnd;
    const hex = c => '#' + c.getHexString();
    // one part: geometry baked with matrix M (relative to the exported root), material numbers, extra meta
    function part(mesh, M, meta) {
      const g = mesh.geometry, mat = mesh.material, A = g.attributes;
      let mode = 2;
      if (mat.vertexShader === H.WOOD_V) mode = 0; else if (mat.vertexShader === H.WOODS_V) mode = 1; else if (mat.fragmentShader === H.EYE_F) mode = 3;
      const nv = A.position.count, idx = g.index ? Array.from(g.index.array) : Array.from({ length: nv }, (_, i) => i);
      const nm = new THREE.Matrix3().getNormalMatrix(M), v = new THREE.Vector3();
      const P = new Float32Array(nv * 3), N = new Float32Array(nv * 3);
      for (let i = 0; i < nv; i++) { v.fromBufferAttribute(A.position, i).applyMatrix4(M); P.set([v.x, v.y, v.z], i * 3); v.fromBufferAttribute(A.normal, i).applyMatrix3(nm).normalize(); N.set([v.x, v.y, v.z], i * 3); }
      const o = { nv, ni: idx.length, mode, P, N, I: idx };
      if (mode === 0) { const C = new Float32Array(nv * 3); for (let i = 0; i < nv; i++) { v.fromBufferAttribute(A.aC, i).applyMatrix4(M); C.set([v.x, v.y, v.z], i * 3); } o.C = C; o.U = Float32Array.from(A.aU.array); o.B = Float32Array.from(A.aB.array); }
      if (A.aD) { const D = new Float32Array(nv * 3), m3 = new THREE.Matrix3().setFromMatrix4(M); for (let i = 0; i < nv; i++) { v.fromBufferAttribute(A.aD, i); if (v.lengthSq() > 1e-6) v.applyMatrix3(m3).normalize(); D.set([v.x, v.y, v.z], i * 3); } o.D = D; }
      if (A.aO) o.O = Float32Array.from(A.aO.array);
      if (A.color) o.K = Float32Array.from(A.color.array);
      const U = mat.uniforms || {}, h = { mode };
      if (U.uSeed) h.seed = U.uSeed.value; if (U.uWood) h.wood = hex(U.uWood.value); if (U.uDark) h.dark = hex(U.uDark.value);
      ['uTip', 'uGr', 'uFine', 'uRing', 'uCav', 'uGrain'].forEach(k => { if (U[k]) h[k.slice(1).toLowerCase()] = U[k].value; });
      if (mode === 2) { h.color = mat.color ? hex(mat.color) : '#ffffff'; h.emissive = mat.emissive ? hex(mat.emissive) : '#000000'; h.vcol = !!A.color; }
      o.h = Object.assign(h, meta || {}); return o;
    }
    // every visible opaque mesh under root, relative to root
    function all(root, meta, skip) { root.updateMatrixWorld(true); const inv = root.matrixWorld.clone().invert(), L = [];
      root.traverse(m => { if (!m.isMesh || !m.geometry || (m.material && m.material.transparent)) return; let vis = true; for (let q = m; q && q !== root; q = q.parent) if (!q.visible || (skip && skip(q))) vis = false; if (!vis) return;
        L.push(part(m, inv.clone().multiply(m.matrixWorld), typeof meta === 'function' ? meta(m) : meta)); }); return L; }
    function pack(parts) { // 'HSW1', count, then per part: json, arrays (float32 little endian), indices int32
      const chunks = [], enc = new TextEncoder(); let size = 8;
      parts.forEach(o => { const h = Object.assign({}, o.h, { nv: o.nv, ni: o.ni, has: ['C', 'U', 'B', 'D', 'O', 'K'].filter(k => o[k]).join('') }); const js = enc.encode(JSON.stringify(h));
        chunks.push([js, o]); size += 4 + js.length + o.nv * 24 + o.ni * 4 + (o.C ? o.nv * 24 : 0) + (o.D ? o.nv * 12 : 0) + (o.O ? o.nv * 4 : 0) + (o.K ? o.nv * 12 : 0); });
      const buf = new ArrayBuffer(size), dv = new DataView(buf), u8 = new Uint8Array(buf); let q = 0;
      [72, 83, 87, 49].forEach(c => dv.setUint8(q++, c)); dv.setInt32(q, parts.length, true); q += 4;
      const fl = a => { for (let i = 0; i < a.length; i++) { dv.setFloat32(q, a[i], true); q += 4; } };
      chunks.forEach(([js, o]) => { dv.setInt32(q, js.length, true); q += 4; u8.set(js, q); q += js.length; fl(o.P); fl(o.N); if (o.C) { fl(o.C); fl(o.U); fl(o.B); } if (o.D) fl(o.D); if (o.O) fl(o.O); if (o.K) fl(o.K);
        for (let i = 0; i < o.ni; i++) { dv.setInt32(q, o.I[i], true); q += 4; } });
      let s = ''; for (let i = 0; i < u8.length; i += 0x8000) s += String.fromCharCode.apply(null, u8.subarray(i, i + 0x8000)); return btoa(s); }
    const F = {};

    // ---- floresta (aim = -z, boss at origin)
    { const aim = V3(0, 0, -1), SPEED = 15, LEAD = 0.55; H.seed(4242); const list = []; let d = 2.4, s = 1; const side = V3(-aim.z, 0, aim.x);
      while (d < 23) { const rows = 1 + Math.floor(d / 6 + rnd() * 1.2), spread = d * 0.3 + 0.6;
        for (let r = 0; r < rows; r++) { const lat = rows === 1 ? (rnd() - 0.5) * 1.2 : (r / (rows - 1) - 0.5) * 2 * spread * (0.8 + 0.3 * rnd());
          const base = aim.clone().multiplyScalar(d + (rnd() - 0.5) * 0.8).addScaledVector(side, lat);
          const lean = aim.clone().multiplyScalar(0.25 + 0.35 * rnd()).addScaledVector(side, lat / Math.max(spread, 1) * 0.35);
          list.push({ seed: s++, base, H: 3.8 + d * 0.2 + rnd() * 2.4, R: 0.55 + 0.35 * rnd() + d * 0.03, lean, t0: LEAD + d / SPEED + rnd() * 0.06, d }); }
        d += 1.0 + rnd() * 0.9; }
      const parts = []; list.forEach((pl, ti) => { const tr = H.makeTree(pl.seed, pl.base, pl.H, pl.R, pl.lean); const root = new THREE.Group(); root.add(tr.g);
        tr.parts.forEach(pp => { parts.push(part(pp.m, new THREE.Matrix4(), { tree: ti, at: pp.at, dur: pp.dur, t0: pl.t0, bx: pl.base.x, bz: pl.base.z, R: pl.R, d: pl.d, H: pl.H })); }); });
      F.forest = pack(parts); }

    // ---- prisão (C = origin)
    { const C = V3(0, 0, 0), HALF = 1.9, beams = []; H.seed(777);
      const o = (s, w) => ({ seed: s, wood: w || '#a9804f', dark: '#543620', tip: 0.1 });
      const corner = [V3(-1, 0, -1), V3(1, 0, -1), V3(1, 0, 1), V3(-1, 0, 1)].map(v => v.multiplyScalar(HALF).add(C));
      const tops = corner.map((c, i) => c.clone().add(V3((C.x - c.x) * 0.08, 4.6 + rnd() * 0.3, (C.z - c.z) * 0.08)));
      corner.forEach((c, i) => { const pts = H.bentPath(c.clone().setY(-1), tops[i].clone().sub(c.clone().setY(-1)), tops[i].y + 1, V3(0, 0, 0), 10, i * 3.7, 0.035);
        const m = H.woodTube(pts, Array.from({ length: 10 }, (_, k) => 0.5 - 0.06 * k / 9 + 0.03 * Math.sin(k * 1.9 + i)), Object.assign(o(i * 5.1, '#9c7448'), { seg: 4, circ: 2.4 })); beams.push({ m, at: 0, dur: 0.3 + 0.05 * i, pillar: true }); });
      for (let side = 0; side < 4; side++) { const a = corner[side], b = corner[(side + 1) % 4];
        for (let h = 0; h < 3; h++) { const y = 1.0 + h * 1.45 + (rnd() - 0.5) * 0.2, A = a.clone().setY(y), B = b.clone().setY(y + (rnd() - 0.5) * 0.3);
          const pts = Array.from({ length: 8 }, (_, k) => { const u = k / 7; return A.clone().lerp(B, u).add(V3(0, Math.sin(u * Math.PI) * (0.12 + rnd() * 0.15), 0)); });
          beams.push({ m: H.woodTube(pts, Array.from({ length: 8 }, (_, k) => 0.2 + 0.05 * Math.sin(k * 2.3 + side + h)), Object.assign(o(side * 9 + h, '#b08655'), { seg: 6 })), at: 0.28 + h * 0.09 + side * 0.03, dur: 0.22 }); }
        for (let v = 1; v <= 2; v++) { const u = v / 3 + (rnd() - 0.5) * 0.06, base = a.clone().lerp(b, u).setY(-0.6), top = base.clone().setY(4.4 + rnd() * 0.3);
          const pts = H.bentPath(base, top.clone().sub(base), top.y + 0.6, V3(0, 0, 0), 8, side * 13 + v, 0.08);
          beams.push({ m: H.woodTube(pts, H.taper(8, 0.22 + 0.04 * rnd(), 0.17), Object.assign(o(side * 17 + v * 3, '#a77d4c'), { seg: 6 })), at: 0.2 + 0.08 * v + side * 0.04, dur: 0.3 }); } }
      const apex = C.clone().setY(6.4);
      tops.forEach((tp, i) => { const pts = Array.from({ length: 8 }, (_, k) => { const u = k / 7; return tp.clone().lerp(apex, u).add(V3(0, Math.sin(u * Math.PI) * 0.35, 0)); });
        beams.push({ m: H.woodTube(pts, H.taper(8, 0.34, 0.2), Object.assign(o(i * 23, '#9c7448'), { seg: 5 })), at: 0.5 + 0.04 * i, dur: 0.25 }); });
      F.prison = pack(beams.map(b => part(b.m, new THREE.Matrix4(), { at: b.at, dur: b.dur, pillar: !!b.pillar }))); }

    // ---- flores (C = origin)
    { const C = V3(0, 0, 0); H.seed(9191); const L = [[0, 0]]; for (let i = 0; i < 8; i++) { const a = i / 8 * 6.283 + rnd() * 0.5, r = 3.6 + 2.6 * rnd(); L.push([Math.cos(a) * r, Math.sin(a) * r]); }
      const parts = [];
      L.forEach(([x, z], i) => { const b = C.clone().add(V3(x, 0, z)); if (i === 0) b.add(V3(2.2, 0, 1.4)); const out = V3(x, 0, z).multiplyScalar(0.04);
        const f = H.makeFlower(31 + i * 7, b, 3.6 + 2.4 * rnd() + (i === 0 ? 1.2 : 0), out); const t0 = rnd() * 0.25;
        const I = new THREE.Matrix4(), hm = new THREE.Matrix4().compose(f.head.position, f.head.quaternion, new THREE.Vector3(1, 1, 1));
        const fm = { flower: i, t0, hx: f.head.position.x, hy: f.head.position.y, hz: f.head.position.z, hq: [f.head.quaternion.x, f.head.quaternion.y, f.head.quaternion.z, f.head.quaternion.w], bx: b.x, bz: b.z };
        parts.push(part(f.stem, I, Object.assign({ role: 'stem' }, fm)));
        f.leaves.forEach((l, li) => parts.push(part(l, I, Object.assign({ role: 'leaf', li }, fm))));
        f.head.scale.setScalar(1); f.head.updateMatrixWorld(true); const hinv = f.head.matrixWorld.clone().invert();
        const petalSet = new Set(f.petals.map(q => q.p));
        f.petals.forEach(q => { const piv = q.p.parent; parts.push(part(q.p, new THREE.Matrix4(), Object.assign({ role: 'petal', rotY: piv.rotation.y, py: q.p.position.y, open: q.open, closed: q.closed, lag: q.lag }, fm))); });
        f.head.children.forEach(c => { if (c.isMesh) parts.push(part(c, hinv.clone().multiply(c.matrixWorld), Object.assign({ role: 'head' }, fm))); }); });
      F.flowers = pack(parts); }

    // ---- cúpula (weave, rng 5150)
    { const RD = 1.9, WD = H.WD, parts = []; H.seed(5150); const N = 18;
      for (let i = 0; i < N; i++) { const a0 = i / N * 6.283 + rnd() * 0.15, dir = i % 2 ? 1 : -1, tw = (0.8 + 0.4 * rnd()) * dir, pts = []; const rr = RD + (i % 2) * 0.12 + rnd() * 0.08;
        for (let k = 0; k < 16; k++) { const u = k / 15, el = u * Math.PI * (0.55 + 0.08 * rnd()), az = a0 + tw * u; const r = rr * (1 + 0.04 * Math.sin(u * 9 + i));
          pts.push(V3(Math.cos(az) * Math.cos(el) * r, Math.sin(el) * r * 1.08 - 0.5 * (1 - u) * (k === 0 ? 1 : 0), Math.sin(az) * Math.cos(el) * r)); }
        pts[0].y = -0.8;
        const w = 0.24 + 0.1 * rnd(), m = H.woodTube(pts, Array.from({ length: 16 }, (_, k) => k === 15 ? 0 : w * (1 - 0.35 * k / 15) * (1 + 0.1 * Math.sin(k * 1.9 + i))),
          { seed: i * 3.1, wood: new THREE.Color(WD.wood).offsetHSL(0, (rnd() - 0.5) * 0.1, (rnd() - 0.5) * 0.08).getStyle().replace(/rgb\((\d+),(\d+),(\d+)\)/, (s, r, g, b) => '#' + [r, g, b].map(x => (+x).toString(16).padStart(2, '0')).join('')), dark: WD.dark, seg: 9, fine: 32, side: pts[8].clone().setY(0).normalize(), fl: 0.6 });
        parts.push(part(m, new THREE.Matrix4(), { role: 'strand', at: 0.02 * i + 0.05 * rnd(), dur: 0.2 + 0.05 * rnd() })); }
      const core = new THREE.Mesh(new THREE.SphereGeometry(RD - 0.12, 28, 14, 0, 6.283, 0, Math.PI / 2), new THREE.MeshLambertMaterial({ color: '#3b2614', side: THREE.DoubleSide })); core.scale.y = 1.05; core.updateMatrix();
      parts.push(part(core, core.matrix, { role: 'core' })); F.dome = pack(parts); }

    // ---- dragão: cabeça, mandíbula, pele e braços no espaço do corpo
    { const head = H.makeDragonHead(91), jaw = head.jaw, parts = []; head.updateMatrixWorld(true);
      parts.push(...all(head, { role: 'head' }, q => q === jaw));
      const jinv = jaw.matrixWorld.clone().invert(); jaw.traverse(m => { if (m.isMesh && !m.material.transparent) parts.push(part(m, jinv.clone().multiply(m.matrixWorld), { role: 'jaw' })); });
      const jp = new THREE.Vector3().setFromMatrixPosition(jaw.matrixWorld);
      const NB = 64, SP = 0.4, BK = 1.45, BL = (NB - 1) * SP;
      const radiusAt = u => BK * (u < 0.06 ? 0.78 + 0.18 * u / 0.06 : 0.96 - 0.08 * Math.sin(u * 9.0) * 0.5) * (u > 0.6 ? Math.max(0.06, 1 - Math.pow((u - 0.6) / 0.4, 1.6) * 0.94) : 1);
      const sp = H.dragonSkinParts(BL, radiusAt, 1234, BK);
      const mk = (list, h) => { const geos = list; let V = 0, I = 0; geos.forEach(g => { V += g.attributes.position.count; I += g.index.count; });
        const P = new Float32Array(V * 3), N = new Float32Array(V * 3), B = new Float32Array(V * 2), idx = []; let v0 = 0;
        geos.forEach(g => { P.set(g.attributes.position.array, v0 * 3); N.set(g.attributes.normal.array, v0 * 3); B.set(g.attributes.aB.array, v0 * 2); g.index.array.forEach(k => idx.push(k + v0)); v0 += g.attributes.position.count; });
        return { nv: V, ni: idx.length, mode: 0, P, N, I: idx, C: P, U: new Float32Array(V), B, h: Object.assign({ mode: 0, tip: 0.08 }, h) }; };
      parts.push(mk(sp, { role: 'skin', seed: 23, wood: '#a3b8a2', dark: '#3f5443', gr: 0.5, fine: 26, cav: 0.85, ring: 0, grain: 7, bodyLen: BL }));
      parts.push(mk(sp.arms, { role: 'arms', seed: 29, wood: '#a8bca6', dark: '#4a5f4d', gr: 0.5, fine: 30, cav: 0, ring: 0, grain: 7, bodyLen: BL }));
      parts[0].h.jx = jp.x; parts[0].h.jy = jp.y; parts[0].h.jz = jp.z; parts[0].h.js = head.scale.x;
      F.dragon = pack(parts); }

    // ---- Buda: estátua + leque (fileiras e braços de ataque)
    { const statue = H.makeBuddhaStatue(), parts = all(statue, { role: 'statue' }); parts[0].h.topY = statue.topY; parts[0].h.headZ = statue.headZ;
      const fan = H.makeThousandArms(V3(0, 10, 0.4));
      fan.rows.forEach(r => { r.g.updateMatrixWorld(true); const inv = r.g.matrixWorld.clone().invert();
        parts.push(part(r.mesh, inv.clone().multiply(r.mesh.matrixWorld), { role: 'row', k: r.k, cx: r.g.position.x, cy: r.g.position.y, cz: r.g.position.z }));
        r.att.forEach(a => { const f = H.makeFanAttacker(r, a); f.hand.updateMatrixWorld(true); const hinv = f.hand.matrixWorld.clone().invert(), hm = f.hand.children[0];
          parts.push(part(hm, hinv.clone().multiply(hm.matrixWorld), { role: 'hand', k: r.k, th: a.th, hs: a.hs, seed: a.seed, pts: a.pts.flatMap(q => [q.x, q.y, q.z]), rad: a.rad, hp: [f.hPos.x, f.hPos.y, f.hPos.z], hq: [f.hQ.x, f.hQ.y, f.hQ.z, f.hQ.w] })); }); });
      F.buddha = pack(parts); }
    return F;
  });
  fs.mkdirSync(out, { recursive: true });
  for (const [k, v] of Object.entries(files)) { const buf = Buffer.from(v, 'base64'); fs.writeFileSync(path.join(out, k + '.bin'), buf); console.log(k, (buf.length / 1e6).toFixed(2) + ' MB'); }
  console.log('errors:', errs.slice(0, 5).join(' | ') || 'none'); await b.close();
})();
