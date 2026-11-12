// Ferramentas da prévia dos mobs de Naruto: chaves de animação (pose a pose, nada de seno), fumaça/fogo em volume
// (raymarch de ruído 3D, igual em espírito ao shader do jogo), papel-bomba pintado e queimando, e detritos.
'use strict';
const FX = (() => {
  const clamp01 = v => Math.max(0, Math.min(1, v));
  const EASE = {
    io: v => 0.5 - 0.5 * Math.cos(Math.PI * v),
    in: v => v * v * v,               // acelera (golpe saindo)
    out: v => 1 - Math.pow(1 - v, 3), // freia (chegando na pose)
    snap: v => 1 - Math.pow(1 - v, 6),// golpe em 1-2 ticks e segura
    lin: v => v,
    hold: v => (v < 1 ? 0 : 1),
  };
  // keys: [[t, valor, easing do trecho que CHEGA nesta chave]]; valor número ou array
  function seq(keys, t) {
    if (t <= keys[0][0]) return keys[0][1];
    for (let i = 1; i < keys.length; i++) {
      if (t <= keys[i][0]) {
        const a = keys[i - 1], b = keys[i];
        const f = (EASE[b[2] || 'io'])(clamp01((t - a[0]) / (b[0] - a[0])));
        if (Array.isArray(a[1])) return a[1].map((x, k) => x + (b[1][k] - x) * f);
        return a[1] + (b[1] - a[1]) * f;
      }
    }
    return keys[keys.length - 1][1];
  }

  // spline cúbica periódica (mesma do Poses de Arma): ciclos de andar com chaves de tempos desiguais
  const SPL = new WeakMap();
  function splineOf(keys) {
    let c = SPL.get(keys); if (c) return c;
    const kk = keys.filter(k => k[0] < 1);                                                     // chave em 1 = a mesma de 0 (ciclo)
    const n = kk.length, t = kk.map(k => k[0]), y = kk.map(k => k[1]);
    const h = i => (i + 1 < n ? t[i + 1] : t[0] + 1) - t[i];
    const A = Array.from({ length: n }, () => new Array(n).fill(0)), r = new Array(n).fill(0);
    for (let i = 0; i < n; i++) {
      const ip = (i - 1 + n) % n, inx = (i + 1) % n, h0 = h(ip), h1 = h(i);
      A[i][ip] += h0; A[i][i] += 2 * (h0 + h1); A[i][inx] += h1; r[i] = 6 * ((y[inx] - y[i]) / h1 - (y[i] - y[ip]) / h0);
    }
    for (let i = 0; i < n; i++) {
      let pv = i; for (let k = i + 1; k < n; k++) if (Math.abs(A[k][i]) > Math.abs(A[pv][i])) pv = k;
      [A[i], A[pv]] = [A[pv], A[i]]; [r[i], r[pv]] = [r[pv], r[i]];
      for (let k = i + 1; k < n; k++) { const f = A[k][i] / A[i][i]; for (let j = i; j < n; j++) A[k][j] -= f * A[i][j]; r[k] -= f * r[i]; }
    }
    const m = new Array(n).fill(0);
    for (let i = n - 1; i >= 0; i--) { let v = r[i]; for (let j = i + 1; j < n; j++) v -= A[i][j] * m[j]; m[i] = v / A[i][i]; }
    c = { t, y, m, h }; SPL.set(keys, c); return c;
  }
  function loop(keys, ph) {
    ph = ((ph % 1) + 1) % 1; const { t, y, m, h } = splineOf(keys), n = t.length;
    let i = n - 1; for (let k = 0; k < n; k++) if (ph >= t[k]) i = k;
    const x = (ph < t[i] ? ph + 1 : ph) - t[i], H = h(i), j = (i + 1) % n, u = x / H;
    return (1 - u) * y[i] + u * y[j] + ((Math.pow(1 - u, 3) - (1 - u)) * m[i] + (u * u * u - u) * m[j]) * H * H / 6;
  }

  // ---------- volume: fumaça e fogo ----------
  const VS = `varying vec3 vW; void main(){ vec4 w = modelMatrix*vec4(position,1.0); vW = w.xyz; gl_Position = projectionMatrix*viewMatrix*w; }`;
  const FS = `
  precision highp float;
  varying vec3 vW;
  uniform vec3 uC; uniform float uR, uAge, uFire, uSeed, uDens, uRise, uSquash, uFreq;
  uniform vec3 uSmoke;
  float h(vec3 p){ p = fract(p*0.3183099+vec3(.71,.113,.419)); p*=17.0; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }
  float n3(vec3 x){ vec3 i=floor(x), f=fract(x); f=f*f*(3.0-2.0*f);
    return mix(mix(mix(h(i),h(i+vec3(1,0,0)),f.x),mix(h(i+vec3(0,1,0)),h(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(h(i+vec3(0,0,1)),h(i+vec3(1,0,1)),f.x),mix(h(i+vec3(0,1,1)),h(i+vec3(1,1,1)),f.x),f.y),f.z); }
  float fbm(vec3 p){ float a=0.5, s=0.0; for(int i=0;i<4;i++){ s+=a*n3(p); p=p*2.03+vec3(1.7,9.2,3.1); a*=0.5; } return s; }
  float field(vec3 q, out float T){
    vec3 s = q; s.y = (s.y - uRise*uAge) / uSquash;
    float n = fbm(s*2.1*uFreq + vec3(uSeed, uSeed*0.37 - uAge*1.3, uSeed*0.71));
    float n2 = fbm(s*5.3*uFreq + vec3(-uAge*2.0, uSeed, 0.0));
    float r = length(s) - (n-0.5)*0.75 - (n2-0.5)*0.22;
    float grow = 0.35 + 0.65*(1.0-pow(1.0-min(uAge*2.6,1.0),3.0));
    float d = smoothstep(grow, grow*0.55, r);
    // se desfaz no fim: o ruído come a nuvem de fora pra dentro
    d *= smoothstep(uAge*0.9-0.05, uAge*0.9+0.2, n*0.6+n2*0.4+0.15) ;
    d *= 1.0 - smoothstep(0.75, 1.0, uAge);
    T = uFire * smoothstep(grow*0.95, grow*0.15, r) * (1.0 - smoothstep(0.04, 0.5, uAge)) * (0.55+0.9*n2);
    return d;
  }
  vec3 ramp(float t){
    vec3 c = mix(vec3(0.55,0.06,0.03), vec3(0.95,0.22,0.05), smoothstep(0.05,0.35,t));
    c = mix(c, vec3(1.0,0.62,0.16), smoothstep(0.35,0.6,t));
    c = mix(c, vec3(1.0,0.96,0.78), smoothstep(0.62,0.9,t));
    return c;
  }
  void main(){
    vec3 ro = cameraPosition, rd = normalize(vW - cameraPosition);
    float R = uR*1.35; vec3 oc = ro-uC; float b = dot(oc,rd), c = dot(oc,oc)-R*R, disc = b*b-c;
    if(disc<0.0) discard;
    float sq = sqrt(disc), t0 = max(-b-sq, 0.0), t1 = -b+sq;
    const int N = 44; float dt = (t1-t0)/float(N);
    vec3 col = vec3(0.0); float a = 0.0;
    for(int i=0;i<N;i++){
      float t = t0 + (float(i)+0.5)*dt;
      vec3 q = (ro+rd*t-uC)/uR;
      float T; float d = field(q, T);
      if(d>0.002){
        float T2; float dl = field(q+vec3(-0.12,0.22,-0.1), T2);
        float lit = clamp(1.0 - (dl-d)*2.2, 0.35, 1.25);
        vec3 sm = uSmoke*lit*(0.75+0.35*clamp(q.y+0.5,0.0,1.0));
        vec3 c2 = mix(sm, ramp(T)*(1.0+2.4*T), clamp(T*1.6,0.0,1.0));
        float al = 1.0-exp(-d*uDens*dt/uR);
        col += (1.0-a)*al*c2; a += (1.0-a)*al;
        if(a>0.98) break;
      }
    }
    if(a<0.004) discard;
    gl_FragColor = vec4(col/max(a,1e-3), a);
  }`;
  function volume() {
    const mat = new THREE.ShaderMaterial({
      vertexShader: VS, fragmentShader: FS, transparent: true, depthWrite: false, side: THREE.BackSide,
      uniforms: {
        uC: { value: new THREE.Vector3() }, uR: { value: 1 }, uAge: { value: 0 }, uFire: { value: 0 }, uSeed: { value: 0 },
        uDens: { value: 7 }, uRise: { value: 0.5 }, uSquash: { value: 1 }, uFreq: { value: 1 }, uSmoke: { value: new THREE.Color(0.9, 0.9, 0.9) },
      },
    });
    const m = new THREE.Mesh(new THREE.SphereGeometry(1, 24, 16), mat);
    m.frustumCulled = false;
    m.set = (c, r, age, o = {}) => {
      const u = mat.uniforms;
      u.uC.value.copy(c); u.uR.value = r; u.uAge.value = age; u.uFire.value = o.fire || 0; u.uSeed.value = o.seed || 0;
      u.uDens.value = o.dens || 7; u.uFreq.value = o.freq || 1; u.uRise.value = o.rise ?? 0.5; u.uSquash.value = o.squash || 1;
      u.uSmoke.value.setRGB(...(o.smoke || [0.9, 0.9, 0.9]));
      m.position.copy(c); m.scale.setScalar(r * 1.4 + 0.05 + (o.rise || 0) * r);
      m.visible = age > 0 && age < 1;
    };
    return m;
  }

  // ---------- papel-bomba ----------
  function rnd(seed) { // mulberry32: sementes vizinhas dão sequências bem diferentes
    let a = (Math.floor(seed) * 2654435761) >>> 0;
    return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
  }
  function tagTexture(seed) {
    const W = 16, H = 48, cv = document.createElement('canvas'); cv.width = W; cv.height = H;
    const g = cv.getContext('2d'), img = g.createImageData(W, H), R = rnd(seed);
    const ink = new Float32Array(W * H);
    // traços de pincel: cada "caractere" é um punhado de traços com espessura que varia ao longo do traço
    for (let ch = 0; ch < 4; ch++) {
      const cy = 9 + ch * 10, strokes = 3 + Math.floor(R() * 3);
      for (let s = 0; s < strokes; s++) {
        let x = 3 + R() * 10, y = cy - 4 + R() * 8; const ang = R() < 0.5 ? (R() - 0.5) * 0.6 : Math.PI / 2 + (R() - 0.5) * 0.6;
        const len = 3 + R() * 6, wd = 0.6 + R() * 0.6;
        for (let k = 0; k <= 12; k++) {
          const f = k / 12, px = x + Math.cos(ang) * len * f, py = y + Math.sin(ang) * len * f;
          const w = wd * (1.2 - f * 0.7);
          for (let yy = Math.floor(py - 2); yy <= py + 2; yy++) for (let xx = Math.floor(px - 2); xx <= px + 2; xx++) {
            if (xx < 0 || yy < 0 || xx >= W || yy >= H) continue;
            const dd = Math.hypot(xx + 0.5 - px, yy + 0.5 - py);
            if (dd < w) ink[yy * W + xx] = 1;
          }
        }
      }
    }
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const i = (y * W + x) * 4, fib = ENG.n3(x * 0.9, y * 0.25, seed, 3);
      let c = [236 - fib * 22, 222 - fib * 26, 186 - fib * 30];
      if (x === 0 || x === W - 1 || y === 0 || y === H - 1) c = [204, 186, 146];
      if (ink[y * W + x]) c = ENG.n3(x, y, seed, 5) > 0.75 ? [150, 20, 24] : [196, 30, 32];
      img.data[i] = c[0]; img.data[i + 1] = c[1]; img.data[i + 2] = c[2]; img.data[i + 3] = 255;
    }
    g.putImageData(img, 0, 0);
    const tex = new THREE.CanvasTexture(cv); tex.magFilter = tex.minFilter = THREE.NearestFilter; tex.generateMipmaps = false;
    return tex;
  }
  function tag(seed) {
    const mat = new THREE.ShaderMaterial({
      side: THREE.DoubleSide, transparent: true,
      uniforms: { map: { value: tagTexture(seed) }, uBurn: { value: 0 }, uSeed: { value: seed } },
      vertexShader: 'varying vec2 vUv; void main(){ vUv=uv; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: `varying vec2 vUv; uniform sampler2D map; uniform float uBurn, uSeed;
        float h(vec2 p){ return fract(sin(dot(p,vec2(127.1,311.7))+uSeed)*43758.5453); }
        float n(vec2 p){ vec2 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f); return mix(mix(h(i),h(i+vec2(1,0)),f.x),mix(h(i+vec2(0,1)),h(i+vec2(1,1)),f.x),f.y); }
        void main(){
          vec2 px = floor(vUv*vec2(16.0,48.0))+0.5;
          float edge = (1.0 - vUv.y) - (uBurn*1.25 - 0.12) + (n(px*0.35)-0.5)*0.22;
          if(edge < 0.0) discard;
          vec3 c = texture2D(map, vUv).rgb;
          // borda queimando: preto carvão, depois brasa laranja viva
          if(edge < 0.07) c = mix(vec3(1.0,0.55,0.12)*1.6, vec3(1.0,0.9,0.5)*1.8, step(0.035, 0.07-edge));
          else if(edge < 0.12) c = vec3(0.12,0.08,0.06);
          gl_FragColor = vec4(c,1.0);
        }`,
    });
    const m = new THREE.Mesh(new THREE.PlaneGeometry(0.12, 0.36), mat);
    m.burn = v => { mat.uniforms.uBurn.value = v; };
    return m;
  }

  // ---------- detritos: cada pedaço com tamanho, cor, giro e caminho próprios ----------
  function debris(n, seed, palette) {
    const R = rnd(seed), g = new THREE.Group(), items = [];
    for (let i = 0; i < n; i++) {
      const s = 0.04 + R() * 0.11;
      const col = palette[Math.floor(R() * palette.length)];
      const k = 0.8 + R() * 0.4;
      const mesh = new THREE.Mesh(new THREE.BoxGeometry(s * (0.6 + R()), s * (0.6 + R()), s * (0.6 + R())),
        new THREE.MeshLambertMaterial({ color: new THREE.Color(col[0] / 255 * k, col[1] / 255 * k, col[2] / 255 * k) }));
      const a = R() * Math.PI * 2, up = 2.5 + R() * 5.5, out = 1 + R() * 4;
      items.push({ mesh, v: [Math.cos(a) * out, up, Math.sin(a) * out], w: [R() * 14 - 7, R() * 14 - 7, R() * 14 - 7] });
      g.add(mesh);
    }
    g.at = (origin, age, floorY = 0) => {
      g.visible = age > 0;
      for (const it of items) {
        let tt = age, x = origin.x + it.v[0] * tt, z = origin.z + it.v[2] * tt, y = origin.y + it.v[1] * tt - 9.8 * tt * tt / 2;
        if (y < floorY + 0.03) { // quica uma vez e para
          const tl = (it.v[1] + Math.sqrt(it.v[1] * it.v[1] + 2 * 9.8 * (origin.y - floorY))) / 9.8;
          x = origin.x + it.v[0] * tl; z = origin.z + it.v[2] * tl; y = floorY + 0.03; tt = tl;
        }
        it.mesh.position.set(x, y, z);
        it.mesh.rotation.set(it.w[0] * tt, it.w[1] * tt, it.w[2] * tt);
        it.mesh.visible = age < 2.6;
      }
    };
    return g;
  }

  return { seq, loop, EASE, volume, tag, debris, rnd, clamp01 };
})();
