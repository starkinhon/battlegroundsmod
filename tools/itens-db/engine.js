// Itens DB: motor compartilhado entre a prévia e o bake do jogo.
// Cada item vira um modelo de cubos no formato do Minecraft (elements from/to 0..16, faces com uv) e UMA textura
// pintada texel a texel pela posição 3D de cada texel (nada de PNG repetido). A prévia desenha exatamente esses
// elementos com a mesma luz de item do Minecraft (2 luzes direcionais + ambiente 0.4), e o bake exporta os mesmos
// JSON/PNG pro jogo. O ícone do inventário é gerado renderizando o próprio modelo.
(function () {
  'use strict';
  const ITEMS = [];
  let ICON_MODE = false;
  const FACES = ['north', 'south', 'east', 'west', 'up', 'down'];
  const NORMAL = { north: [0, 0, -1], south: [0, 0, 1], east: [1, 0, 0], west: [-1, 0, 0], up: [0, 1, 0], down: [0, -1, 0] };

  // ---------- util ----------
  function hash3(x, y, z, s) {
    let h = (Math.imul(x | 0, 374761393) + Math.imul(y | 0, 668265263) + Math.imul(z | 0, 1274126177) + Math.imul(s | 0, 974634721)) | 0;
    h = Math.imul(h ^ (h >>> 13), 1274126177);
    h ^= h >>> 16;
    return (h >>> 0) / 4294967295;
  }
  // ruído de valor 3D suave (pra manchas que não se repetem)
  function vnoise(x, y, z, s) {
    const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z);
    const xf = x - xi, yf = y - yi, zf = z - zi;
    const u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf), w = zf * zf * (3 - 2 * zf);
    let r = 0;
    for (let dz = 0; dz < 2; dz++) for (let dy = 0; dy < 2; dy++) for (let dx = 0; dx < 2; dx++) {
      const k = (dx ? u : 1 - u) * (dy ? v : 1 - v) * (dz ? w : 1 - w);
      r += k * hash3(xi + dx, yi + dy, zi + dz, s);
    }
    return r;
  }
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  function hex(h) { return [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)]; }
  function mix(a, b, t) { return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t]; }
  function mul(a, k) { return [a[0] * k, a[1] * k, a[2] * k]; }
  function norm(v) { const l = Math.hypot(v[0], v[1], v[2]) || 1; return [v[0] / l, v[1] / l, v[2] / l]; }
  function dot(a, b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

  // SDFs simples
  const SDF = {
    ellipsoid(p, c, r) {
      const q = [(p[0] - c[0]) / r[0], (p[1] - c[1]) / r[1], (p[2] - c[2]) / r[2]];
      return (Math.hypot(q[0], q[1], q[2]) - 1) * Math.min(r[0], r[1], r[2]);
    },
    roundBox(p, c, h, rad) {
      const q = [Math.abs(p[0] - c[0]) - h[0] + rad, Math.abs(p[1] - c[1]) - h[1] + rad, Math.abs(p[2] - c[2]) - h[2] + rad];
      return Math.hypot(Math.max(q[0], 0), Math.max(q[1], 0), Math.max(q[2], 0)) + Math.min(Math.max(q[0], q[1], q[2]), 0) - rad;
    },
    // cilindro com eixo em 'axis' (0 x, 1 y, 2 z), raio r, meia-altura h
    cyl(p, c, axis, r, h) {
      const a = [0, 1, 2].filter(i => i !== axis);
      const d0 = Math.hypot(p[a[0]] - c[a[0]], p[a[1]] - c[a[1]]) - r;
      const d1 = Math.abs(p[axis] - c[axis]) - h;
      return Math.min(Math.max(d0, d1), 0) + Math.hypot(Math.max(d0, 0), Math.max(d1, 0));
    },
  };
  const SDFX = SDF;
  function gradient(f, p) {
    const e = 0.05;
    return norm([
      f([p[0] + e, p[1], p[2]]) - f([p[0] - e, p[1], p[2]]),
      f([p[0], p[1] + e, p[2]]) - f([p[0], p[1] - e, p[2]]),
      f([p[0], p[1], p[2] + e]) - f([p[0], p[1], p[2] - e]),
    ]);
  }

  // ---------- geometria: partes -> caixas ----------
  // SDF -> voxels de lado res -> fusão gulosa em caixas (cada voxel = 1 texel quando res = 1/densidade)
  function voxelize(part) {
    const [x1, y1, z1, x2, y2, z2] = part.bounds, r = part.res;
    const nx = Math.round((x2 - x1) / r), ny = Math.round((y2 - y1) / r), nz = Math.round((z2 - z1) / r);
    const grid = new Uint8Array(nx * ny * nz), idx = (i, j, k) => (k * ny + j) * nx + i;
    for (let k = 0; k < nz; k++) for (let j = 0; j < ny; j++) for (let i = 0; i < nx; i++) {
      const p = [x1 + (i + 0.5) * r, y1 + (j + 0.5) * r, z1 + (k + 0.5) * r];
      if (part.sdf(p) <= (part.bias || 0)) grid[idx(i, j, k)] = 1;
    }
    const boxes = [];
    const order = part.mergeOrder || [0, 1, 2];
    for (let k = 0; k < nz; k++) for (let j = 0; j < ny; j++) for (let i = 0; i < nx; i++) {
      if (grid[idx(i, j, k)] !== 1) continue;
      // cresce em x, depois y, depois z
      let w = 1; while (i + w < nx && grid[idx(i + w, j, k)] === 1) w++;
      let h = 1, ok = true;
      while (ok && j + h < ny) { for (let a = 0; a < w; a++) if (grid[idx(i + a, j + h, k)] !== 1) { ok = false; break; } if (ok) h++; }
      let d = 1; ok = true;
      while (ok && k + d < nz) {
        for (let b = 0; b < h && ok; b++) for (let a = 0; a < w; a++) if (grid[idx(i + a, j + b, k + d)] !== 1) { ok = false; break; }
        if (ok) d++;
      }
      for (let c = 0; c < d; c++) for (let b = 0; b < h; b++) for (let a = 0; a < w; a++) grid[idx(i + a, j + b, k + c)] = 2;
      boxes.push({ from: [x1 + i * r, y1 + j * r, z1 + k * r], to: [x1 + (i + w) * r, y1 + (j + h) * r, z1 + (k + d) * r], tag: part.tag, part });
    }
    void order;
    return boxes;
  }

  function rotPoint(p, rot) {
    if (!rot) return p;
    const a = rot.angle * Math.PI / 180, c = Math.cos(a), s = Math.sin(a), o = rot.origin;
    const x = p[0] - o[0], y = p[1] - o[1], z = p[2] - o[2];
    if (rot.axis === 'x') return [x + o[0], y * c - z * s + o[1], y * s + z * c + o[2]];
    if (rot.axis === 'y') return [x * c + z * s + o[0], y + o[1], -x * s + z * c + o[2]];
    return [x * c - y * s + o[0], x * s + y * c + o[1], z + o[2]];
  }

  // cantos TL,TR,BR,BL vistos de fora, na convenção de uv do Minecraft
  function corners(b, face) {
    const [x1, y1, z1] = b.from, [x2, y2, z2] = b.to;
    switch (face) {
      case 'north': return [[x2, y2, z1], [x1, y2, z1], [x1, y1, z1], [x2, y1, z1]];
      case 'south': return [[x1, y2, z2], [x2, y2, z2], [x2, y1, z2], [x1, y1, z2]];
      case 'east': return [[x2, y2, z2], [x2, y2, z1], [x2, y1, z1], [x2, y1, z2]];
      case 'west': return [[x1, y2, z1], [x1, y2, z2], [x1, y1, z2], [x1, y1, z1]];
      case 'up': return [[x1, y2, z1], [x2, y2, z1], [x2, y2, z2], [x1, y2, z2]];
      default: return [[x1, y1, z2], [x2, y1, z2], [x2, y1, z1], [x1, y1, z1]];
    }
  }
  const lerp3 = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
  function facePoint(c, u, v) { return lerp3(lerp3(c[0], c[1], u), lerp3(c[3], c[2], u), v); }

  function inside(b, p, eps) {
    if (b.rot) {
      // desfaz a rotação
      p = rotPoint(p, { axis: b.rot.axis, angle: -b.rot.angle, origin: b.rot.origin });
    }
    return p[0] > b.from[0] + eps && p[0] < b.to[0] - eps && p[1] > b.from[1] + eps && p[1] < b.to[1] - eps && p[2] > b.from[2] + eps && p[2] < b.to[2] - eps;
  }

  // ---------- construção do modelo ----------
  function build(item, q) {
    const dens = item.density[q];
    const parts = item.parts(q);
    let boxes = [];
    for (const part of parts) {
      if (part.sdf) {
        part.res = part.res || 1 / dens;
        boxes = boxes.concat(voxelize(part).map(b => Object.assign(b, { rot: part.rot })));
      } else {
        boxes.push({ from: part.box.slice(0, 3), to: part.box.slice(3, 6), tag: part.tag, part, rot: part.rot });
      }
    }
    // faces: tira as totalmente escondidas dentro de outra caixa
    const faces = [];
    for (const b of boxes) {
      for (const f of FACES) {
        const c0 = corners(b, f).map(p => rotPoint(p, b.rot));
        const n = b.rot ? norm(lerp3([0, 0, 0], rotPoint(NORMAL[f], { axis: b.rot.axis, angle: b.rot.angle, origin: [0, 0, 0] }), 1)) : NORMAL[f];
        const du = Math.hypot(c0[1][0] - c0[0][0], c0[1][1] - c0[0][1], c0[1][2] - c0[0][2]);
        const dv = Math.hypot(c0[3][0] - c0[0][0], c0[3][1] - c0[0][1], c0[3][2] - c0[0][2]);
        const w = Math.max(1, Math.round(du * dens)), h = Math.max(1, Math.round(dv * dens));
        let hidden = true;
        for (let j = 0; j < h && hidden; j++) for (let i = 0; i < w; i++) {
          const p = facePoint(c0, (i + 0.5) / w, (j + 0.5) / h);
          const t = [p[0] + n[0] * 0.02, p[1] + n[1] * 0.02, p[2] + n[2] * 0.02];
          if (!boxes.some(o => o !== b && inside(o, t, 0.001))) { hidden = false; break; }
        }
        if (b.part.noFace && b.part.noFace.includes(f)) hidden = true;
        if (!hidden) faces.push({ box: b, face: f, c: c0, n, w, h });
      }
    }
    // empacota o atlas (prateleiras)
    faces.sort((a, b) => b.h - a.h || b.w - a.w);
    let size = 16;
    for (; ; size *= 2) {
      let x = 0, y = 0, row = 0, fits = true;
      for (const f of faces) {
        if (f.w > size) { fits = false; break; }
        if (x + f.w > size) { x = 0; y += row; row = 0; }
        f.ax = x; f.ay = y; x += f.w; row = Math.max(row, f.h);
        if (y + row > size) { fits = false; break; }
      }
      if (fits) break;
    }
    const frames = item.frames || 1;
    const atlas = [];
    for (let fr = 0; fr < frames; fr++) {
      const px = new Uint8ClampedArray(size * size * 4);
      for (const f of faces) {
        for (let j = 0; j < f.h; j++) for (let i = 0; i < f.w; i++) {
          const p = facePoint(f.c, (i + 0.5) / f.w, (j + 0.5) / f.h);
          const part = f.box.part;
          const sn = part.sdf ? gradient(part.sdf, p) : f.n;
          const col = item.paint({ p, n: f.n, sn, tag: f.box.tag, q, frame: fr, frames, dens, face: f.face, part, box: f.box });
          const o = ((f.ay + j) * size + f.ax + i) * 4;
          px[o] = col[0]; px[o + 1] = col[1]; px[o + 2] = col[2]; px[o + 3] = col.length > 3 ? col[3] : 255;
        }
      }
      atlas.push(px);
    }
    return { item, q, boxes, faces, size, atlas, frames };
  }

  // ---------- export pro Minecraft ----------
  function r4(v) { return Math.round(v * 10000) / 10000; }
  function modelJson(m, texName) {
    const els = [];
    const byBox = new Map();
    for (const f of m.faces) {
      if (!byBox.has(f.box)) byBox.set(f.box, {});
      const s = m.size;
      byBox.get(f.box)[f.face] = { uv: [r4(f.ax / s * 16), r4(f.ay / s * 16), r4((f.ax + f.w) / s * 16), r4((f.ay + f.h) / s * 16)], texture: '#0' };
    }
    for (const [b, faces] of byBox) {
      const e = { from: b.from.map(r4), to: b.to.map(r4), faces };
      if (b.rot) e.rotation = { angle: b.rot.angle, axis: b.rot.axis, origin: b.rot.origin };
      els.push(e);
    }
    const j = { textures: { 0: texName, particle: texName }, elements: els, display: m.item.display };
    if (m.item.translucent) j.render_type = 'minecraft:translucent';
    j.gui_light = 'front';
    return j;
  }

  // ---------- render three.js ----------
  const VS = `varying vec2 vUv; varying vec3 vN; void main(){ vUv=uv; vN=normalize(mat3(modelMatrix)*normal); gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0);} `;
  // luz de item do Minecraft (Lighting.setupFor3DItems): duas direcionais + ambiente 0.4, difusa 0.6
  const FS = `uniform sampler2D map; uniform float lit; varying vec2 vUv; varying vec3 vN;
    void main(){ vec4 c=texture2D(map,vUv); if(c.a<0.02) discard;
      vec3 n=normalize(vN); vec3 L0=normalize(vec3(0.2,1.0,-0.7)); vec3 L1=normalize(vec3(-0.2,1.0,0.7));
      float l=min(1.0,0.4+0.6*(max(dot(n,L0),0.0)+max(dot(n,L1),0.0)));
      l=mix(1.0,l,lit); gl_FragColor=vec4(c.rgb*l,c.a);} `;

  function textureFor(m, frame) {
    const t = new THREE.DataTexture(m.atlas[frame], m.size, m.size, THREE.RGBAFormat);
    t.magFilter = THREE.NearestFilter; t.minFilter = THREE.NearestFilter; t.flipY = false; t.needsUpdate = true;
    return t;
  }
  function meshFor(m) {
    const pos = [], nor = [], uv = [], idx = [];
    const opaque = { pos: [], nor: [], uv: [], idx: [] }, trans = { pos: [], nor: [], uv: [], idx: [] };
    for (const f of m.faces) {
      const tgt = (m.item.translucentTags || []).includes(f.box.tag) ? trans : opaque;
      const base = tgt.pos.length / 3, s = m.size;
      const u1 = f.ax / s, v1 = f.ay / s, u2 = (f.ax + f.w) / s, v2 = (f.ay + f.h) / s;
      const uvs = [[u1, v1], [u2, v1], [u2, v2], [u1, v2]];
      for (let k = 0; k < 4; k++) { tgt.pos.push(...f.c[k]); tgt.nor.push(...f.n); tgt.uv.push(...uvs[k]); }
      tgt.idx.push(base, base + 3, base + 2, base, base + 2, base + 1);
    }
    void pos; void nor; void uv; void idx;
    const group = new THREE.Group();
    const tex = textureFor(m, 0);
    const mats = [];
    for (const [g, transparent] of [[opaque, false], [trans, true]]) {
      if (!g.idx.length) continue;
      const geo = new THREE.BufferGeometry();
      geo.setAttribute('position', new THREE.Float32BufferAttribute(g.pos, 3));
      geo.setAttribute('normal', new THREE.Float32BufferAttribute(g.nor, 3));
      geo.setAttribute('uv', new THREE.Float32BufferAttribute(g.uv, 2));
      geo.setIndex(g.idx);
      const mat = new THREE.ShaderMaterial({ uniforms: { map: { value: tex }, lit: { value: 1 } }, vertexShader: VS, fragmentShader: FS, transparent, depthWrite: !transparent, side: THREE.FrontSide });
      mats.push(mat);
      const mesh = new THREE.Mesh(geo, mat);
      mesh.renderOrder = transparent ? 2 : 1;
      group.add(mesh);
    }
    group.userData = { m, mats, frame: 0, tex: [tex] };
    for (let fr = 1; fr < m.frames; fr++) group.userData.tex.push(textureFor(m, fr));
    return group;
  }
  function setFrame(group, fr) {
    const u = group.userData; fr %= u.tex.length;
    if (u.frame === fr) return; u.frame = fr;
    for (const mat of u.mats) mat.uniforms.map.value = u.tex[fr];
  }

  // ---------- ícone: renderiza o modelo e reduz pra pixel art ----------
  let iconRenderer = null;
  function iconFrames(m, N) {
    const S = N * 8;
    if (!iconRenderer) iconRenderer = new THREE.WebGLRenderer({ antialias: false, alpha: true, preserveDrawingBuffer: true });
    iconRenderer.setPixelRatio(1);
    iconRenderer.setSize(S, S, false);
    iconRenderer.setClearColor(0x000000, 0);
    const scene = new THREE.Scene();
    const g = meshFor(m);
    const ic = m.item.icon;
    const piv = new THREE.Group();
    g.position.set(-8, -8, -8);
    piv.add(g);
    piv.rotation.order = 'YXZ';
    piv.rotation.set(ic.pitch * Math.PI / 180, ic.yaw * Math.PI / 180, (ic.roll || 0) * Math.PI / 180);
    const outer = new THREE.Group(); outer.add(piv); outer.position.set(ic.dx || 0, ic.dy || 0, 0);
    scene.add(outer);
    // enquadra sozinho: caixa do modelo já girado, centralizada, com margem
    outer.updateMatrixWorld(true);
    const bb = new THREE.Box3().setFromObject(outer);
    const cx = (bb.min.x + bb.max.x) / 2, cy = (bb.min.y + bb.max.y) / 2;
    outer.position.x -= cx; outer.position.y -= cy;
    const z = Math.max(bb.max.x - bb.min.x, bb.max.y - bb.min.y) / 2 * (ic.margin || 1.08); // meia-largura em unidades
    const cam = new THREE.OrthographicCamera(-z, z, z, -z, -100, 100);
    cam.position.set(0, 0, 50); cam.lookAt(0, 0, 0);
    const out = [];
    const gl = iconRenderer.getContext();
    for (let fr = 0; fr < m.frames; fr++) {
      setFrame(g, fr);
      iconRenderer.render(scene, cam);
      const raw = new Uint8Array(S * S * 4);
      gl.readPixels(0, 0, S, S, gl.RGBA, gl.UNSIGNED_BYTE, raw);
      out.push(pixelate(raw, S, N, m.item.iconOutline || 0.55));
    }
    return out;
  }
  // reduz S->N: cobertura > 45% vira pixel opaco com a cor média (linear) dos subpixels cobertos; contorno escuro no tom do item
  function pixelate(raw, S, N, outline) {
    const k = S / N, img = new Uint8ClampedArray(N * N * 4);
    const toL = c => Math.pow(c / 255, 2.2), toS = c => Math.round(Math.pow(c, 1 / 2.2) * 255);
    for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
      let a = 0, r = 0, g = 0, b = 0, cnt = 0;
      for (let j = 0; j < k; j++) for (let i = 0; i < k; i++) {
        const sy = S - 1 - (y * k + j), sx = x * k + i, o = (sy * S + sx) * 4;
        cnt++;
        if (raw[o + 3] > 8) { a += raw[o + 3] / 255; const w = raw[o + 3] / 255; r += toL(raw[o]) * w; g += toL(raw[o + 1]) * w; b += toL(raw[o + 2]) * w; }
      }
      const o = (y * N + x) * 4;
      if (a / cnt > 0.45) {
        // cor = o subpixel real mais perto da média (fica nítido, só cores que existem na textura)
        const mr = r / a, mg = g / a, mb = b / a; let bd = 1e9, bo = -1;
        for (let j = 0; j < k; j++) for (let i = 0; i < k; i++) {
          const sy = S - 1 - (y * k + j), sx = x * k + i, q = (sy * S + sx) * 4;
          if (raw[q + 3] < 128) continue;
          const dd = (toL(raw[q]) - mr) ** 2 + (toL(raw[q + 1]) - mg) ** 2 + (toL(raw[q + 2]) - mb) ** 2;
          if (dd < bd) { bd = dd; bo = q; }
        }
        if (bo >= 0) { img[o] = raw[bo]; img[o + 1] = raw[bo + 1]; img[o + 2] = raw[bo + 2]; } else { img[o] = toS(mr); img[o + 1] = toS(mg); img[o + 2] = toS(mb); }
        img[o + 3] = 255;
      }
    }
    // contorno: pixel vazio colado num cheio recebe a cor do vizinho escurecida
    const res = new Uint8ClampedArray(img);
    if (outline > 0) {
      for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
        const o = (y * N + x) * 4;
        if (img[o + 3] > 0) continue;
        let best = -1;
        for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
          const xx = x + dx, yy = y + dy;
          if (xx < 0 || yy < 0 || xx >= N || yy >= N) continue;
          const p = (yy * N + xx) * 4;
          if (img[p + 3] > 128) { best = p; break; }
        }
        if (best >= 0) { res[o] = img[best] * outline * 0.45; res[o + 1] = img[best + 1] * outline * 0.45; res[o + 2] = img[best + 2] * outline * 0.45; res[o + 3] = 255; }
      }
    }
    return res;
  }


  // ---------- ícone por raymarch das mesmas formas (silhueta lisa, sem degrau de voxel) ----------
  // Usa as MESMAS partes (SDF/caixas) e a MESMA pintura do modelo; só troca o degrau dos voxels pela forma contínua.
  function partSdf(part) {
    if (part.sdf) return part.sdf;
    const b = part.box, c = [(b[0] + b[3]) / 2, (b[1] + b[4]) / 2, (b[2] + b[5]) / 2], h = [(b[3] - b[0]) / 2, (b[4] - b[1]) / 2, (b[5] - b[2]) / 2];
    return p => SDFX.roundBox(p, c, h, 0.0001);
  }
  function iconRay(item, q, N, frame) {
    const parts = item.parts(q).map(pt => ({ pt, f: partSdf(pt) }));
    const ic = item.icon;
    const e = new THREE.Euler(ic.pitch * Math.PI / 180, ic.yaw * Math.PI / 180, (ic.roll || 0) * Math.PI / 180, 'YXZ');
    const R = new THREE.Matrix4().makeRotationFromEuler(e), Ri = R.clone().invert();
    const toView = p => { const v = new THREE.Vector3(p[0] - 8, p[1] - 8, p[2] - 8).applyMatrix4(R); return [v.x, v.y, v.z]; };
    const toModel = (x, y, z) => { const v = new THREE.Vector3(x, y, z).applyMatrix4(Ri); return [v.x + 8, v.y + 8, v.z + 8]; };
    // enquadramento: amostra a superfície grosseiramente
    let mnx = 1e9, mxx = -1e9, mny = 1e9, mxy = -1e9;
    for (const { pt, f } of parts) {
      const bb = pt.bounds || pt.box;
      const st = 0.25;
      for (let x = bb[0]; x <= bb[3]; x += st) for (let y = bb[1]; y <= bb[4]; y += st) for (let z = bb[2]; z <= bb[5]; z += st) {
        if (f([x, y, z]) > 0) continue;
        const v = toView([x, y, z]);
        mnx = Math.min(mnx, v[0]); mxx = Math.max(mxx, v[0]); mny = Math.min(mny, v[1]); mxy = Math.max(mxy, v[1]);
      }
    }
    const cx = (mnx + mxx) / 2 + (ic.dx || 0), cy = (mny + mxy) / 2 + (ic.dy || 0);
    const half = Math.max(mxx - mnx, mxy - mny) / 2 * (ic.margin || 1.06) * (N / (N - 2));
    const SS = 4, S = N * SS, dir = toModel(0, 0, -1).map((v, i) => v - 8), raw = new Uint8Array(S * S * 4);
    const Lv = norm([-0.45, 0.75, 0.5]);
    ICON_MODE = true;
    for (let j = 0; j < S; j++) for (let i = 0; i < S; i++) {
      const vx = cx - half + (i + 0.5) / S * 2 * half, vy = cy + half - (j + 0.5) / S * 2 * half;
      let p = toModel(vx, vy, 40);
      let acc = [0, 0, 0], accA = 0, skip = null, t = 0;
      for (let it = 0; it < 200 && t < 90 && accA < 0.99; it++) {
        let d = 1e9, hit = null;
        for (const P of parts) { if (P === skip) continue; const v = P.f(p); if (v < d) { d = v; hit = P; } }
        if (d < 0.01) {
          const sn = gradient(hit.f, p);
          const ax = Math.abs(sn[0]) > Math.abs(sn[1]) && Math.abs(sn[0]) > Math.abs(sn[2]) ? 0 : Math.abs(sn[1]) > Math.abs(sn[2]) ? 1 : 2;
          const face = ax === 0 ? (sn[0] > 0 ? 'east' : 'west') : ax === 1 ? (sn[1] > 0 ? 'up' : 'down') : (sn[2] > 0 ? 'south' : 'north');
          const col = item.paint({ p, n: NORMAL[face], sn, tag: hit.pt.tag, q, frame, frames: item.frames || 1, dens: item.density[q], face, part: hit.pt, icon: true });
          const nv = toView([sn[0] + 8, sn[1] + 8, sn[2] + 8]);
          const l = 0.78 + 0.3 * Math.max(-0.4, dot(norm(nv), Lv));
          const a = (col.length > 3 ? col[3] : 255) / 255;
          for (let k = 0; k < 3; k++) acc[k] += (1 - accA) * a * Math.min(255, col[k] * l);
          accA += (1 - accA) * a;
          if (a < 0.99) { skip = hit; p = [p[0] + dir[0] * 0.6, p[1] + dir[1] * 0.6, p[2] + dir[2] * 0.6]; t += 0.6; continue; }
          break;
        }
        const st = Math.min(1.5, Math.max(d * 0.9, 0.004));
        p = [p[0] + dir[0] * st, p[1] + dir[1] * st, p[2] + dir[2] * st]; t += st;
      }
      const o = ((S - 1 - j) * S + i) * 4;
      if (accA > 0) { raw[o] = acc[0] / accA; raw[o + 1] = acc[1] / accA; raw[o + 2] = acc[2] / accA; raw[o + 3] = 255; }
    }
    ICON_MODE = false;
    return pixelate(raw, S, N, item.iconOutline || 0.55);
  }
  function iconRayFrames(item, q, N) { const out = []; for (let f = 0; f < (item.frames || 1); f++) out.push(iconRay(item, q, N, f)); return out; }

  function toCanvas(px, w, h) {
    const c = document.createElement('canvas'); c.width = w; c.height = h;
    const ctx = c.getContext('2d'); const id = ctx.createImageData(w, h); id.data.set(px); ctx.putImageData(id, 0, 0);
    return c;
  }
  function stackFrames(list, w, h) {
    const out = new Uint8ClampedArray(w * h * 4 * list.length);
    list.forEach((f, i) => out.set(f, i * w * h * 4));
    return out;
  }

  window.ITEMS_DB = { get iconMode() { return ICON_MODE; }, iconRayFrames, ITEMS, SDF, hash3, vnoise, clamp, hex, mix, mul, norm, dot, build, modelJson, meshFor, setFrame, iconFrames, toCanvas, stackFrames };
})();
