// Efeitos dos mobs de Dragon Ball (por cima do FX dos mobs de Naruto): brilho de ki, grãos/faíscas soltos (cada um
// com caminho próprio), rastro de bala, poça de ácido em shader, gosma voando. Nada de sprite repetido.
'use strict';
const DBFX = (() => {
  const clamp01 = v => Math.max(0, Math.min(1, v));

  // Brilho esférico aditivo (ki, pulsar de quem vai explodir): borda mais forte, miolo branco, ruído que corre.
  function glow(color = [0.4, 1.0, 0.35]) {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.FrontSide,
      uniforms: { uC: { value: new THREE.Color(...color) }, uK: { value: 0 }, uT: { value: 0 } },
      vertexShader: 'varying vec3 vN; varying vec3 vV; varying vec3 vP; void main(){ vN=normalize(normalMatrix*normal); vec4 mv=modelViewMatrix*vec4(position,1.0); vV=normalize(-mv.xyz); vP=position; gl_Position=projectionMatrix*mv; }',
      fragmentShader: `varying vec3 vN; varying vec3 vV; varying vec3 vP; uniform vec3 uC; uniform float uK, uT;
        float h(vec3 p){ p=fract(p*0.3183+vec3(.71,.11,.42)); p*=17.0; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }
        float n(vec3 x){ vec3 i=floor(x), f=fract(x); f=f*f*(3.0-2.0*f);
          return mix(mix(mix(h(i),h(i+vec3(1,0,0)),f.x),mix(h(i+vec3(0,1,0)),h(i+vec3(1,1,0)),f.x),f.y),
                     mix(mix(h(i+vec3(0,0,1)),h(i+vec3(1,0,1)),f.x),mix(h(i+vec3(0,1,1)),h(i+vec3(1,1,1)),f.x),f.y),f.z); }
        void main(){
          float face = abs(dot(vN, vV));
          float fl = n(vP*3.0 + vec3(0.0, -uT*4.0, uT*1.3))*0.6 + n(vP*7.0 + vec3(uT*3.0,0.0,0.0))*0.4;
          // miolo forte que some suave até a borda (sem contorno de bolha)
          float a = pow(face, 1.6) * (0.7 + 0.6*fl) * uK;
          vec3 c = mix(uC, vec3(1.0), clamp(pow(face, 4.0)*0.8 + fl*0.15, 0.0, 1.0));
          gl_FragColor = vec4(c*a*1.6, a);
        }`,
    });
    const m = new THREE.Mesh(new THREE.SphereGeometry(1, 28, 20), mat);
    m.frustumCulled = false;
    m.at = (p, r, k, t, sy = 1) => { m.visible = k > 0.01; m.position.copy(p); m.scale.set(r, r * sy, r); mat.uniforms.uK.value = k; mat.uniforms.uT.value = t; };
    return m;
  }

  // Punhado de grãos (areia, pedrinhas, faíscas): cada grão com cor, tamanho, velocidade e arrasto próprios.
  // spec: { n, seed, palette:[[r,g,b]..], size:[a,b], cone(R) -> [vx,vy,vz], drag, grav }
  function grains(spec) {
    const R = FX.rnd(spec.seed || 1), n = spec.n;
    const pos = new Float32Array(n * 3), col = new Float32Array(n * 3), sz = new Float32Array(n), it = [];
    for (let i = 0; i < n; i++) {
      const c = spec.palette[Math.floor(R() * spec.palette.length)], k = 0.8 + R() * 0.4;
      col.set([c[0] / 255 * k, c[1] / 255 * k, c[2] / 255 * k], i * 3);
      sz[i] = spec.size[0] + R() * (spec.size[1] - spec.size[0]);
      it.push({ v: spec.cone(R), d: (spec.drag || 2) * (0.6 + R() * 0.8), delay: R() * (spec.spread || 0), life: (spec.life || 1) * (0.6 + R() * 0.6) });
    }
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    geo.setAttribute('color', new THREE.BufferAttribute(col, 3));
    geo.setAttribute('sz', new THREE.BufferAttribute(sz, 1));
    const al = new Float32Array(n); geo.setAttribute('al', new THREE.BufferAttribute(al, 1));
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, vertexColors: true, blending: spec.add ? THREE.AdditiveBlending : THREE.NormalBlending,
      vertexShader: 'attribute float sz; attribute float al; varying vec3 vC; varying float vA; void main(){ vC=color; vA=al; vec4 mv=modelViewMatrix*vec4(position,1.0); gl_PointSize = sz*420.0/-mv.z; gl_Position=projectionMatrix*mv; }',
      fragmentShader: 'varying vec3 vC; varying float vA; void main(){ vec2 d=gl_PointCoord-0.5; if(max(abs(d.x),abs(d.y))>0.5) discard; gl_FragColor=vec4(vC, vA); }',
    });
    const pts = new THREE.Points(geo, mat);
    pts.frustumCulled = false;
    const g = spec.grav ?? 9.8;
    // origem, direção base (frente), idade em s, chão
    pts.at = (o, age, floorY = 0, basis = null) => {
      pts.visible = age > 0;
      for (let i = 0; i < n; i++) {
        const p = it[i], a = Math.max(0, age - p.delay), d = p.d;
        const k = (1 - Math.exp(-d * a)) / d;                                                  // arrasto do ar
        let v = p.v;
        if (basis) v = [basis.r.x * v[0] + basis.u.x * v[1] + basis.f.x * v[2], basis.r.y * v[0] + basis.u.y * v[1] + basis.f.y * v[2], basis.r.z * v[0] + basis.u.z * v[1] + basis.f.z * v[2]];
        let x = o.x + v[0] * k, y = o.y + v[1] * k - g * a * a / 2 * 0.6, z = o.z + v[2] * k;
        if (y < floorY + 0.01) y = floorY + 0.01;
        pos.set([x, y, z], i * 3);
        al[i] = age > p.delay ? clamp01(1 - (a / p.life)) : 0;
      }
      geo.attributes.position.needsUpdate = true; geo.attributes.al.needsUpdate = true;
    };
    return pts;
  }

  // Risco de bala/traçante: linha fina que corre da boca do cano até o ponto, com a cauda sumindo.
  function tracer(color = [1.0, 0.85, 0.45]) {
    const geo = new THREE.BufferGeometry(), pos = new Float32Array(6), al = new Float32Array([0, 0]);
    geo.setAttribute('position', new THREE.BufferAttribute(pos, 3)); geo.setAttribute('al', new THREE.BufferAttribute(al, 1));
    const m = new THREE.LineSegments(geo, new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, uniforms: { uC: { value: new THREE.Color(...color) } },
      vertexShader: 'attribute float al; varying float vA; void main(){ vA=al; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: 'uniform vec3 uC; varying float vA; void main(){ gl_FragColor=vec4(uC*1.5*vA, vA); }',
    }));
    m.frustumCulled = false;
    m.at = (a, b, f, len = 0.9) => {
      m.visible = f >= 0 && f <= 1;
      const d = b.clone().sub(a), L = d.length(); d.normalize();
      const head = a.clone().addScaledVector(d, L * f), tail = a.clone().addScaledVector(d, Math.max(0, L * f - len));
      pos.set([tail.x, tail.y, tail.z, head.x, head.y, head.z]); al[0] = 0; al[1] = 1;
      geo.attributes.position.needsUpdate = true; geo.attributes.al.needsUpdate = true;
    };
    return m;
  }

  // Clarão da boca do cano: estrela de 4 a 6 pontas com tamanhos sorteados por tiro (cada tiro um clarão).
  function muzzle() {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide, uniforms: { uS: { value: 0 }, uK: { value: 0 } },
      vertexShader: 'varying vec2 vP; void main(){ vP=position.xy; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: `varying vec2 vP; uniform float uS, uK;
        float h(float x){ return fract(sin(x*91.7+uS*13.1)*43758.5); }
        void main(){ float r=length(vP), a=atan(vP.y,vP.x); float np=4.0+floor(h(1.0)*3.0);
          float spike = pow(abs(cos(a*np*0.5 + h(2.0)*6.28)), 6.0)*(0.5+0.5*h(floor((a+3.1416)/6.2832*np)+3.0));
          float k = smoothstep(0.32+spike*0.6, 0.0, r)*uK; if(k<0.01) discard;
          vec3 c = mix(vec3(1.0,0.55,0.12), vec3(1.0,0.97,0.8), smoothstep(0.1,0.6,k));
          gl_FragColor=vec4(c*k*1.8, k); }`,
    });
    const m = new THREE.Mesh(new THREE.PlaneGeometry(1, 1), mat);
    m.frustumCulled = false;
    m.at = (p, camera, k, seed, s = 0.6) => { m.visible = k > 0.01; m.position.copy(p); m.quaternion.copy(camera.quaternion); m.scale.setScalar(s); mat.uniforms.uK.value = k; mat.uniforms.uS.value = seed; };
    return m;
  }

  // Poça de ácido: mancha de borda irregular, bolhas que nascem e estouram em células, brilho verde e chiado.
  function puddle(c1 = [0.42, 0.9, 0.18], c2 = [0.1, 0.32, 0.04]) {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, side: THREE.DoubleSide,
      uniforms: { uT: { value: 0 }, uK: { value: 0 }, uG: { value: 0 }, uA: { value: new THREE.Color(...c1) }, uB: { value: new THREE.Color(...c2) }, uS: { value: 0 } },
      vertexShader: 'varying vec2 vP; void main(){ vP=position.xy; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: `varying vec2 vP; uniform float uT, uK, uG, uS; uniform vec3 uA, uB;
        float h(vec2 p){ return fract(sin(dot(p,vec2(127.1,311.7))+uS)*43758.5453); }
        float n(vec2 p){ vec2 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f); return mix(mix(h(i),h(i+vec2(1,0)),f.x),mix(h(i+vec2(0,1)),h(i+vec2(1,1)),f.x),f.y); }
        void main(){
          vec2 p = vP;
          float r = length(p), a = atan(p.y,p.x);
          float edge = uG*(0.78 + 0.16*(n(vec2(a*2.2, uS))-0.5)*2.0 + 0.08*(n(vec2(a*7.0, uS+4.0))-0.5)*2.0);
          if (r > edge) discard;
          float depth = smoothstep(edge, edge*0.4, r);
          float sw = n(p*3.0 + vec2(uT*0.6, -uT*0.4)) * 0.6 + n(p*7.0 - vec2(uT*0.9, 0.0))*0.4;
          vec3 c = mix(uB, uA, depth*0.7 + sw*0.4);
          // bolhas: em cada célula uma bolha com tempo próprio, cresce e estoura
          vec2 cell = floor(p*5.0), f = fract(p*5.0)-0.5;
          float ph = fract(uT*(0.6+h(cell)*0.9) + h(cell+3.1));
          vec2 o = (vec2(h(cell+1.7), h(cell+5.3))-0.5)*0.5;
          float br = 0.08 + 0.22*ph; float d = length(f-o);
          if (h(cell+9.0) > 0.45 && d < br && ph < 0.92) c = mix(c, vec3(0.75,1.0,0.5), 0.35 + 0.5*smoothstep(br-0.06, br, d));
          float rim = smoothstep(edge*0.86, edge, r);
          c = mix(c, uB*0.6, rim);
          gl_FragColor = vec4(c*(1.0+0.25*sw), uK*(0.82+0.18*depth));
        }`,
    });
    const m = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), mat);
    m.rotation.x = -Math.PI / 2;
    m.frustumCulled = false;
    m.at = (p, r, grow, k, t, seed = 1) => { m.visible = k > 0.01; m.position.copy(p); m.scale.setScalar(r); const u = mat.uniforms; u.uG.value = grow; u.uK.value = k; u.uT.value = t; u.uS.value = seed; };
    return m;
  }

  // Gosma voando: uma bola que treme (vértices empurrados por ruído a cada quadro) com gotas atrás.
  function blob(color = 0x7be03a) {
    const g = new THREE.Group();
    const geo = new THREE.SphereGeometry(1, 14, 10), base = geo.attributes.position.array.slice();
    const mat = new THREE.MeshLambertMaterial({ color, emissive: new THREE.Color(color).multiplyScalar(0.35) });
    const main = new THREE.Mesh(geo, mat); g.add(main);
    const drops = [];
    for (let i = 0; i < 6; i++) { const d = new THREE.Mesh(new THREE.SphereGeometry(1, 8, 6), mat); g.add(d); drops.push({ m: d, lag: 0.03 + i * 0.025, s: 0.05 - i * 0.006 }); }
    g.frustumCulled = false;
    g.at = (path, f, r, t) => {
      g.visible = f >= 0 && f <= 1;
      const p = path(f);
      main.position.copy(p); main.scale.setScalar(r);
      const a = geo.attributes.position.array;
      for (let i = 0; i < a.length; i += 3) {
        const x = base[i], y = base[i + 1], z = base[i + 2];
        const k = 1 + 0.22 * (ENG.n3(x * 2 + t * 6, y * 2, z * 2, 9) - 0.5) * 2;
        a[i] = x * k; a[i + 1] = y * k; a[i + 2] = z * k;
      }
      geo.attributes.position.needsUpdate = true; geo.computeVertexNormals();
      for (const d of drops) { const q = path(Math.max(0, f - d.lag)); d.m.position.copy(q); d.m.scale.setScalar(Math.max(0.01, d.s * (f > d.lag ? 1 : 0))); }
    };
    return g;
  }

  // Luz pontual de cor própria (o FX do Naruto só tem a laranja).
  function light(color) { const l = new THREE.PointLight(color, 0, 6, 2); l.at = (p, k, d = 6) => { l.position.copy(p); l.intensity = k; l.distance = d; l.visible = true; }; return l; }

  return { glow, grains, tracer, muzzle, puddle, blob, light, clamp01 };
})();
