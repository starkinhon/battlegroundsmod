// Motor da prévia: modelos em cubos iguais ao ModelPart do Minecraft (y pra baixo, pés em y=24, frente = -Z,
// rotação ZYX), UV no desenho vanilla, textura pintada pela posição 3D de cada texel (nada carimbado).
'use strict';
const ENG = (() => {
  // ---------- ruído ----------
  function h3(x, y, z, s) {
    let n = Math.sin(x * 127.1 + y * 311.7 + z * 74.7 + s * 19.19) * 43758.5453;
    return n - Math.floor(n);
  }
  function n3(x, y, z, s = 0) {
    const X = Math.floor(x), Y = Math.floor(y), Z = Math.floor(z);
    let fx = x - X, fy = y - Y, fz = z - Z;
    fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy); fz = fz * fz * (3 - 2 * fz);
    const l = (a, b, t) => a + (b - a) * t;
    const c = (i, j, k) => h3(X + i, Y + j, Z + k, s);
    return l(l(l(c(0, 0, 0), c(1, 0, 0), fx), l(c(0, 1, 0), c(1, 1, 0), fx), fy),
      l(l(c(0, 0, 1), c(1, 0, 1), fx), l(c(0, 1, 1), c(1, 1, 1), fx), fy), fz);
  }
  function fbm(x, y, z, s = 0) {
    return n3(x, y, z, s) * 0.5 + n3(x * 2.03, y * 2.03, z * 2.03, s + 7) * 0.3 + n3(x * 4.07, y * 4.07, z * 4.07, s + 13) * 0.2;
  }
  const mix = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
  const mul = (a, k) => [a[0] * k, a[1] * k, a[2] * k];
  const clamp01 = v => Math.max(0, Math.min(1, v));
  const smooth = (a, b, v) => { const t = clamp01((v - a) / (b - a)); return t * t * (3 - 2 * t); };

  // ---------- UV ----------
  // vanilla: top(u+d,v,w,d) bottom(u+d+w,v,w,d) west(u,v+d,d,h) north(u+d,v+d,w,h) east(u+d+w,v+d,d,h) south(u+2d+w,v+d,w,h)
  function unwrapSize(b) {
    return [Math.ceil(2 * (b.d + b.w)), Math.ceil(b.d + b.h)];
  }
  function pack(boxes) {
    const sorted = boxes.slice().sort((a, b) => unwrapSize(b)[1] - unwrapSize(a)[1] || unwrapSize(b)[0] - unwrapSize(a)[0]);
    for (const W of [64, 128, 256, 512]) {
      let x = 0, y = 0, rowH = 0, ok = true;
      for (const b of sorted) {
        const [w, h] = unwrapSize(b);
        if (w > W) { ok = false; break; }
        if (x + w > W) { x = 0; y += rowH; rowH = 0; }
        b.u = x; b.v = y; x += w; rowH = Math.max(rowH, h);
      }
      const H = y + rowH;
      if (ok && H <= W) return [W, W];
    }
    throw new Error('textura grande demais');
  }

  // Corners exactly as ModelPart.Cube; each polygon: vertices + (u0,v0,u1,v1); vertex i gets
  // [ (u1,v0), (u0,v0), (u0,v1), (u1,v1) ][i].
  function polys(b) {
    let x0 = b.x - (b.g || 0), y0 = b.y - (b.g || 0), z0 = b.z - (b.g || 0);
    let x1 = b.x + b.w + (b.g || 0), y1 = b.y + b.h + (b.g || 0), z1 = b.z + b.d + (b.g || 0);
    if (b.mirror) [x0, x1] = [x1, x0];
    const V = [[x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0], [x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1]];
    const { u, v, w, h, d } = b;
    const f4 = u, f5 = u + d, f6 = u + d + w, f7 = u + d + w + w, f8 = u + d + w + d, f9 = u + d + w + d + w;
    const f10 = v, f11 = v + d, f12 = v + d + h;
    return [
      { f: 'top', vs: [V[5], V[4], V[0], V[1]], uv: [f5, f10, f6, f11] },
      { f: 'bottom', vs: [V[2], V[3], V[7], V[6]], uv: [f6, f11, f7, f10] },
      { f: 'west', vs: [V[0], V[4], V[7], V[3]], uv: [f4, f11, f5, f12] },
      { f: 'north', vs: [V[1], V[0], V[3], V[2]], uv: [f5, f11, f6, f12] },
      { f: 'east', vs: [V[5], V[1], V[2], V[6]], uv: [f6, f11, f8, f12] },
      { f: 'south', vs: [V[4], V[5], V[6], V[7]], uv: [f8, f11, f9, f12] },
    ];
  }
  const QUV = p => { const [u0, v0, u1, v1] = p.uv; return [[u1, v0], [u0, v0], [u0, v1], [u1, v1]]; };

  // ---------- pintura ----------
  // Para cada texel do retângulo de cada face, acha o ponto 3D da face (bilinear nos 4 cantos) e chama o pintor
  // com: nx,ny,nz (0..1 dentro da caixa, y 0 = topo), ax,ay,az (posição no modelo em repouso), f (face), b (caixa).
  function paint(model, W, H) {
    const cv = document.createElement('canvas');
    cv.width = W; cv.height = H;
    const g = cv.getContext('2d');
    const img = g.createImageData(W, H);
    for (const b of model.allBoxes) {
      for (const p of polys(b)) {
        const q = QUV(p);
        const us = [q[0][0], q[1][0], q[2][0], q[3][0]], vs = [q[0][1], q[1][1], q[2][1], q[3][1]];
        const umin = Math.min(...us), umax = Math.max(...us), vmin = Math.min(...vs), vmax = Math.max(...vs);
        for (let ty = Math.floor(vmin); ty < Math.ceil(vmax); ty++) {
          for (let tx = Math.floor(umin); tx < Math.ceil(umax); tx++) {
            const cu = tx + 0.5, cvv = ty + 0.5;
            // parameters: s along u from u0 to u1 (vertex1 -> vertex0), t along v
            const u0 = q[1][0], u1 = q[0][0], v0 = q[1][1], v1 = q[2][1];
            const s = (cu - u0) / (u1 - u0), t = (cvv - v0) / (v1 - v0);
            if (s < 0 || s > 1 || t < 0 || t > 1) continue;
            const L = (a, c, k) => [a[0] + (c[0] - a[0]) * k, a[1] + (c[1] - a[1]) * k, a[2] + (c[2] - a[2]) * k];
            const P = L(L(p.vs[1], p.vs[0], s), L(p.vs[2], p.vs[3], s), t);
            const lx = Math.min(b.x, b.x + b.w), nx = (P[0] - b.x) / b.w, ny = (P[1] - b.y) / b.h, nz = (P[2] - b.z) / b.d;
            const c = b.p({ nx, ny, nz, ax: P[0] + b.ox, ay: P[1] + b.oy, az: P[2] + b.oz, f: p.f, b, px: P[0] - lx, py: P[1] - b.y, pz: P[2] - b.z });
            const i = (ty * W + tx) * 4;
            if (c) {
              img.data[i] = Math.max(0, Math.min(255, c[0]));
              img.data[i + 1] = Math.max(0, Math.min(255, c[1]));
              img.data[i + 2] = Math.max(0, Math.min(255, c[2]));
              img.data[i + 3] = 255;
            }
          }
        }
      }
    }
    g.putImageData(img, 0, 0);
    return cv;
  }

  // ---------- montagem ----------
  // def: { name, pivot:[x,y,z], rot:[x,y,z], boxes:[{x,y,z,w,h,d,p,g?}], kids:[...] }
  // Faces que coincidem piscam (z-fighting: Stark 2026-11-14 viu "faces sobrepostas piscando" no jogo). Duas caixas
  // na mesma parte (ou em partes sem giro nenhum até a raiz) com uma face no mesmo plano, virada pro mesmo lado e
  // cobrindo a mesma área: a menor cresce 0,03 px (b.g, o mesmo inflar do jogo: CubeDeformation), até não sobrar par.
  function antiZ(def) {
    const groups = [];
    const flat = [];
    (function walk(d, ox, oy, oz, still) {
      const px = ox + d.pivot[0], py = oy + d.pivot[1], pz = oz + d.pivot[2];
      const r = d.rot || [0, 0, 0], st = still && !r[0] && !r[1] && !r[2];
      const own = (d.boxes || []).map(b => ({ b, o: st ? [px, py, pz] : [0, 0, 0] }));
      if (st) flat.push(...own); else groups.push(own);
      for (const k of d.kids || []) walk(k, px, py, pz, st);
    })(def, 0, 0, 0, true);
    groups.push(flat);
    const E = 1e-3;
    const box = ({ b, o }) => {
      const g = b.g || 0;
      return [Math.min(b.x, b.x + b.w) - g + o[0], Math.min(b.y, b.y + b.h) - g + o[1], Math.min(b.z, b.z + b.d) - g + o[2],
        Math.max(b.x, b.x + b.w) + g + o[0], Math.max(b.y, b.y + b.h) + g + o[1], Math.max(b.z, b.z + b.d) + g + o[2]];
    };
    const vol = ({ b }) => Math.abs(b.w * b.h * b.d);
    function clash(A, B) {
      for (let ax = 0; ax < 3; ax++) {
        const u = (ax + 1) % 3, v = (ax + 2) % 3;
        const ou = Math.min(A[u + 3], B[u + 3]) - Math.max(A[u], B[u]), ov = Math.min(A[v + 3], B[v + 3]) - Math.max(A[v], B[v]);
        if (ou <= E || ov <= E) continue;
        if (Math.abs(A[ax] - B[ax]) < E || Math.abs(A[ax + 3] - B[ax + 3]) < E) return true;
      }
      return false;
    }
    for (const list of groups) {
      for (let pass = 0; pass < 4; pass++) {
        let any = false;
        for (let i = 0; i < list.length; i++) for (let j = i + 1; j < list.length; j++) {
          const A = list[i], B = list[j];
          if (!A.b.w || !A.b.h || !A.b.d || !B.b.w || !B.b.h || !B.b.d) continue;
          if (!clash(box(A), box(B))) continue;
          const small = vol(A) <= vol(B) ? A : B;
          small.b.g = (small.b.g || 0) + 0.03;
          any = true;
        }
        if (!any) break;
      }
    }
  }

  function build(def) {
    const parts = {}, allBoxes = [];
    function walk(d, ox, oy, oz) {
      const px = ox + d.pivot[0], py = oy + d.pivot[1], pz = oz + d.pivot[2];
      for (const b of d.boxes || []) { b.ox = px; b.oy = py; b.oz = pz; allBoxes.push(b); }
      parts[d.name] = d;
      for (const k of d.kids || []) walk(k, px, py, pz);
    }
    walk(def, 0, 0, 0);
    antiZ(def);
    const [W, H] = pack(allBoxes);
    return { def, parts, allBoxes, W, H };
  }

  function toThree(model, canvas) {
    const tex = new THREE.CanvasTexture(canvas);
    tex.magFilter = THREE.NearestFilter; tex.minFilter = THREE.NearestFilter; tex.flipY = false;
    tex.generateMipmaps = false;
    const mat = new THREE.MeshLambertMaterial({ map: tex, alphaTest: 0.5, side: THREE.DoubleSide });
    const { W, H } = model;
    function mk(d) {
      const g = new THREE.Group();
      g.rotation.order = 'ZYX';
      d.obj = g;
      d.rest = { pos: d.pivot.slice(), rot: (d.rot || [0, 0, 0]).slice() };
      for (const b of d.boxes || []) {
        const pos = [], uv = [], idx = [];
        let n = 0;
        for (const p of polys(b)) {
          const q = QUV(p);
          for (let i = 0; i < 4; i++) { pos.push(...p.vs[i]); uv.push(q[i][0] / W, q[i][1] / H); }
          idx.push(n, n + 1, n + 2, n, n + 2, n + 3);
          n += 4;
        }
        const geo = new THREE.BufferGeometry();
        geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
        geo.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
        geo.setIndex(idx);
        geo.computeVertexNormals();
        g.add(new THREE.Mesh(geo, mat));
      }
      for (const k of d.kids || []) g.add(mk(k));
      return g;
    }
    const inner = mk(model.def);
    const outer = new THREE.Group();
    outer.scale.set(-1 / 16, -1 / 16, 1 / 16);
    outer.position.y = 1.5;
    outer.add(inner);
    return outer;
  }

  // reset every part to rest (lição: resetar posição por quadro)
  function reset(model) {
    for (const d of Object.values(model.parts)) {
      d.obj.position.set(...d.rest.pos);
      d.obj.rotation.set(...d.rest.rot);
      d.obj.scale.set(1, 1, 1);
    }
  }

  // sombreado de anime: 3 faixas chapadas, a fronteira desenhada pelo ruído (nada de chuvisco de pixel)
  function cel(c, v, lo = 0.8, hi = 1.12) { return v < 0.36 ? mul(c, lo) : v > 0.7 ? mul(c, hi) : c; }
  return { n3, fbm, mix, mul, clamp01, smooth, cel, build, paint, toThree, reset, polys };
})();
