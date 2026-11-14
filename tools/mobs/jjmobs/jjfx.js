// Efeitos dos mobs de Jujutsu (por cima de FX e DBFX): maldição sendo exorcizada (o corpo se desfaz em cinza preta,
// texel por texel, do jeito que as maldições somem no mangá), névoa de energia amaldiçoada e resíduo no chão.
'use strict';
const JJFX = (() => {
  const clamp01 = v => Math.max(0, Math.min(1, v));
  // ---------- exorcizar: o corpo escurece e evapora (como a morte por Katon do Madara, sem fogo) ----------
  // Primeiro a energia sai: o couro escurece pro preto-índigo da maldição, com veios mais claros (frios, sem brilho).
  // Depois uma borda irregular desce de cima pra baixo pelo corpo do jeito que ele está (deitado ou em pé); logo antes
  // dela os texels vão se soltando um a um, e da borda sobe vapor escuro em volume (não partícula).
  const NOISE = `
  float jh(vec3 p){ p=fract(p*0.3183+vec3(.71,.11,.42)); p*=17.0; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }
  float jn(vec3 x){ vec3 i=floor(x), f=fract(x); f=f*f*(3.0-2.0*f);
    return mix(mix(mix(jh(i),jh(i+vec3(1,0,0)),f.x),mix(jh(i+vec3(0,1,0)),jh(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(jh(i+vec3(0,0,1)),jh(i+vec3(1,0,1)),f.x),mix(jh(i+vec3(0,1,1)),jh(i+vec3(1,1,1)),f.x),f.y),f.z); }
  float jf(vec3 p){ float a=0.5, s=0.0; for(int i=0;i<3;i++){ s+=a*jn(p); p=p*2.03+vec3(1.7,9.2,3.1); a*=0.5; } return s; }`;
  // progresso total k (0..1) dividido em: escurecer (0..0.22) e evaporar (0.22..1)
  const burnOf = k => clamp01(k / 0.22), frontOf = k => clamp01((k - 0.22) / 0.78);
  function patch(e) {
    if (e._dis) return e._dis;
    const U = { uDis: { value: 0 }, uFr: { value: 0 }, uBurn: { value: 0 }, uY0: { value: 0 }, uY1: { value: 2 }, uT: { value: 0 } };
    e.obj.traverse(o => {
      if (!o.material || o.material._jj || o._vap) return;
      o.material._jj = true;
      o.material.onBeforeCompile = sh => {
        Object.assign(sh.uniforms, U);
        sh.vertexShader = 'varying vec3 vWp;\n' + sh.vertexShader.replace('#include <project_vertex>', '#include <project_vertex>\n vWp = (modelMatrix*vec4(transformed,1.0)).xyz;');
        sh.fragmentShader = 'varying vec3 vWp; uniform float uFr, uBurn, uY0, uY1, uT;\n' + NOISE + '\n' +
          sh.fragmentShader.replace('#include <map_fragment>', `#include <map_fragment>
          if (uBurn > 0.0) {
            // a energia saindo: escurece pro preto-índigo; veios frios mais claros correndo pra cima
            float vein = 1.0 - smoothstep(0.0, 0.07, abs(jn(vWp*5.0 + vec3(0.0, -uT*0.6, 0.0)) - 0.5));
            vec3 dark = vec3(0.05, 0.04, 0.07) + diffuseColor.rgb * 0.08;
            diffuseColor.rgb = mix(diffuseColor.rgb, dark, uBurn * 0.88) + vec3(0.16, 0.13, 0.22) * vein * uBurn * 0.6;
          }
          if (uFr > 0.0) {
            float h = clamp((vWp.y - uY0) / max(uY1 - uY0, 0.05), 0.0, 1.0);
            float n = jn(vWp*6.0)*0.6 + jn(vWp*17.0)*0.4;
            float d = h + (n - 0.5)*0.4 - (1.08 - uFr*1.4);   // > 0: já evaporou
            if (d > 0.0) discard;
            vec3 c = floor(vWp*16.0 + 0.001);
            if (d > -0.18 && jh(c) < smoothstep(-0.18, 0.0, d)) discard;   // texels se soltando antes da borda
            float rim = smoothstep(-0.07, 0.0, d);
            diffuseColor.rgb = mix(diffuseColor.rgb, vec3(0.30, 0.27, 0.36), rim * 0.75);   // borda cor de vapor, sem brilho
          }`);
      };
      o.material.needsUpdate = true;
    });
    e._dis = U;
    vapor(e, U);
    return U;
  }
  // vapor em volume (raymarch numa caixa que cobre o corpo): sobe da borda, se abre e some; cinza-índigo escuro
  function vapor(e, U) {
    const VU = { uFront: { value: 0 }, uK: { value: 0 }, uT: { value: 0 }, uC: { value: new THREE.Vector3() }, uH: { value: new THREE.Vector3(1, 1, 1) },
      uLo: { value: new THREE.Vector3() }, uHi: { value: new THREE.Vector3(1, 1, 1) }, uTop: { value: 1 } };
    const mat = new THREE.ShaderMaterial({
      uniforms: VU, transparent: true, depthWrite: false, side: THREE.BackSide,
      vertexShader: 'varying vec3 vW; void main(){ vec4 w = modelMatrix*vec4(position,1.0); vW = w.xyz; gl_Position = projectionMatrix*viewMatrix*w; }',
      fragmentShader: `varying vec3 vW; uniform float uFront, uK, uT, uTop; uniform vec3 uC, uH, uLo, uHi;
      ${NOISE}
      void main(){
        vec3 ro = cameraPosition, rd = normalize(vW - ro);
        vec3 t0 = (uLo - ro)/rd, t1 = (uHi - ro)/rd; vec3 tn = min(t0,t1), tf = max(t0,t1);
        float a = max(max(max(tn.x,tn.y),tn.z), 0.0), b = min(min(tf.x,tf.y),tf.z);
        if (b <= a) discard;
        const int N = 22; float st = (b - a)/float(N);
        vec4 acc = vec4(0.0);
        for (int i = 0; i < N; i++) {
          vec3 p = ro + rd*(a + st*(float(i) + jh(vW*91.0 + float(i))));
          float rel = p.y - uFront;
          vec2 q = (p.xz - uC.xz) / (uH.xz * (1.0 + max(rel, 0.0)*0.5));
          float foot = 1.0 - smoothstep(0.45, 1.0, length(q));
          vec3 dw = min(p - uLo, uHi - p); foot *= smoothstep(0.0, 0.25, min(min(dw.x, dw.z), dw.y));
          float band = smoothstep(-0.1, 0.03, rel) * exp(-max(rel, 0.0)*2.6);
          vec3 w = vec3(p.x*2.6, p.y*1.8 - uT*1.3, p.z*2.6);
          w.xz += 0.5*vec2(sin(w.y*1.7 + w.z), cos(w.y*1.3 + w.x));
          float den = max(jf(w) - 0.44, 0.0) * 1.7 * band * foot * uK;
          if (den > 0.001) {
            vec3 col = mix(vec3(0.44, 0.41, 0.52), vec3(0.12, 0.10, 0.16), clamp(den*2.0, 0.0, 1.0));
            float al = 1.0 - exp(-den * st * 7.0);
            acc.rgb += (1.0 - acc.a) * col * al; acc.a += (1.0 - acc.a) * al;
            if (acc.a > 0.97) break;
          }
        }
        if (acc.a < 0.004) discard;
        gl_FragColor = vec4(acc.rgb / max(acc.a, 0.001), acc.a * 0.85);
      }`,
    });
    const box = new THREE.Mesh(new THREE.BoxGeometry(1, 1, 1), mat);
    box._vap = true; box.frustumCulled = false; box.matrixAutoUpdate = false; box.visible = false;
    // ajudante invisível que roda antes de tudo (opaco, ordem -1e4): mede o corpo do jeito que ele está nesse quadro
    const probe = new THREE.Mesh(new THREE.BoxGeometry(0.001, 0.001, 0.001), new THREE.MeshBasicMaterial({ colorWrite: false, depthWrite: false }));
    probe._vap = true; probe.frustumCulled = false; probe.renderOrder = -1e4;
    const bb = new THREE.Box3(), tmp = new THREE.Box3(), inv = new THREE.Matrix4(), M = new THREE.Matrix4();
    probe.onBeforeRender = () => {
      const k = U.uDis.value, on = k > 0;
      U.uT.value = VU.uT.value = window.__T != null ? window.__T : performance.now() / 1000;
      if (!on) { U.uBurn.value = 0; U.uFr.value = 0; box.visible = false; return; }
      bb.makeEmpty();
      e.obj.traverse(o => { if (o.isMesh && !o._vap && o.geometry) { if (!o.geometry.boundingBox) o.geometry.computeBoundingBox(); let v = true; for (let q = o; q; q = q.parent) if (!q.visible) { v = false; break; } if (v) bb.union(tmp.copy(o.geometry.boundingBox).applyMatrix4(o.matrixWorld)); } });
      if (bb.isEmpty()) return;
      const burn = burnOf(k), fr = frontOf(k);
      U.uBurn.value = burn; U.uY0.value = bb.min.y; U.uY1.value = bb.max.y;
      U.uFr.value = fr;
      const H = bb.max.y - bb.min.y;
      VU.uFront.value = bb.min.y + H * Math.max(0, Math.min(1, 1.08 - fr * 1.4));   // a mesma borda do corpo
      VU.uK.value = clamp01(fr / 0.06) * (1 - clamp01((fr - 0.88) / 0.12));
      bb.getCenter(VU.uC.value); bb.getSize(VU.uH.value).multiplyScalar(0.5).addScalar(0.05);
      VU.uLo.value.set(bb.min.x - 0.5, bb.min.y - 0.1, bb.min.z - 0.5);
      VU.uHi.value.set(bb.max.x + 0.5, bb.max.y + 1.0, bb.max.z + 0.5); VU.uTop.value = VU.uHi.value.y;
      const sz = new THREE.Vector3().subVectors(VU.uHi.value, VU.uLo.value), c = new THREE.Vector3().addVectors(VU.uHi.value, VU.uLo.value).multiplyScalar(0.5);
      M.compose(c, new THREE.Quaternion(), sz);
      box.matrixWorld.copy(M);
      box.visible = fr > 0;
    };
    // o box é filho do corpo só pra aparecer/sumir junto; a matriz de mundo é escrita direto acima
    box.updateMatrixWorld = () => {};
    // cópias do corpo (eco do poço, ilusões da kitsune) não levam o vapor nem o medidor
    probe.clone = box.clone = () => { const g = new THREE.Object3D(); g._vap = true; return g; };
    e.obj.add(probe); e.obj.add(box);
  }
  function dissolve(e, k) {
    const U = patch(e);
    U.uDis.value = k;
  }
  // a cinza em partícula saiu (Stark, 2026-11-14): o vapor em volume do dissolve() faz esse papel
  function ash() { const g = new THREE.Group(); g.at = () => {}; return g; }

  // morte de maldição (todas usam): leva o golpe, cai, vira cinza preta de cima pra baixo. Devolve {T, fall} pro mob posar.
  function exorcize(t, obj, e, ctx, o = {}) {
    const { seq } = FX, h = o.h || 1.6, dur = o.dur || 3.4;
    const T = t % dur;
    const R = ctx.ref; R.place(0, o.refZ ?? -1.8, 0);
    R.punch(seq([[0.15, 0], [0.25, 1, 'snap'], [0.6, 1], [0.9, 0]], T));
    const fall = seq([[0.25, 0], [0.8, 1, 'in']], T);
    obj.rotation.x = (o.rotX ?? 1.35) * fall;
    obj.position.z += (o.back ?? 0.5) * FX.EASE.out(clamp01((T - 0.25) / 0.5));
    obj.position.y += (o.drop ?? 0) * fall;
    const dis = clamp01((T - 0.95) / 1.7);
    dissolve(e, dis, 0, h);
    if (T > 0.95) ctx.get('cinza', () => ash(9, o.n || 160)).at(new THREE.Vector3(0, o.ashY ?? 0.3, o.ashZ ?? 0.6), T - 0.95, 0.02);
    if (T > 0.22 && T < 0.4) ctx.flash(new THREE.Vector3(0, 1, -0.6), 1 - (T - 0.22) / 0.18);
    return { T, fall, dis };
  }
  // tubo de carne (cordão, língua, tentáculo): de a até b, afinando, com arco e caroços que correm (pulso)
  function tube(color = 0xa04058, r0 = 0.1, r1 = 0.05) {
    const N = 28, geo = new THREE.CylinderGeometry(1, 1, 1, 8, N, true);
    const base = geo.attributes.position.array.slice();
    const m = new THREE.Mesh(geo, new THREE.MeshLambertMaterial({ color })); m.frustumCulled = false;
    m.at = (a, b, k, o = {}) => {
      m.visible = k > 0;
      const pos = geo.attributes.position, d = b.clone().sub(a);
      const side = new THREE.Vector3(0, 1, 0).cross(d).normalize();
      const arc = o.arc ?? 0.4, pulse = o.pulse ?? -1, wig = o.wiggle ?? 0, t = o.t ?? 0;
      for (let i = 0; i < pos.count; i++) {
        const bx = base[i * 3], by = base[i * 3 + 1], bz = base[i * 3 + 2];
        const f = (by + 0.5) * k;
        let w = r0 + (r1 - r0) * f;
        if (pulse >= 0) w *= 1 + 0.9 * Math.exp(-Math.pow((f - pulse) * 9, 2));
        const p = a.clone().addScaledVector(d, f)
          .addScaledVector(side, (arc * 0.6 + wig * Math.sin(f * 9 - t * 7)) * Math.sin(Math.PI * f))
          .add(new THREE.Vector3(0, arc * Math.sin(Math.PI * f), 0));
        pos.setXYZ(i, p.x + bx * w, p.y + bz * w * 0.7, p.z + bz * w);
      }
      pos.needsUpdate = true; geo.computeVertexNormals();
    };
    return m;
  }

  // ---------- água (Mar de Dagon) ----------
  // gotas/respingo: cada gota com cor, tamanho e caminho próprios (azul do mar, espuma branca)
  function drops(seed = 5, n = 60, o = {}) {
    return DBFX.grains({
      n, seed, palette: o.palette || [[150, 210, 235], [90, 170, 215], [230, 248, 255], [60, 130, 190]], size: o.size || [0.025, 0.07],
      drag: o.drag ?? 1.2, grav: o.grav ?? 9, life: o.life ?? 0.9, spread: o.spread ?? 0,
      cone: o.cone || (R => { const a = R() * Math.PI * 2, s = 0.6 + R() * 1.8; return [Math.cos(a) * s, 1.2 + R() * 2.4, Math.sin(a) * s]; }),
    });
  }
  // poça d'água: borda irregular, anéis de ondinha que nascem em pontos diferentes, brilho do céu, sem bolhas de ácido
  function wet(c1 = [0.42, 0.72, 0.86], c2 = [0.12, 0.3, 0.42]) {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, side: THREE.DoubleSide,
      uniforms: { uT: { value: 0 }, uK: { value: 0 }, uG: { value: 0 }, uA: { value: new THREE.Color(...c1) }, uB: { value: new THREE.Color(...c2) }, uS: { value: 0 } },
      vertexShader: 'varying vec2 vP; void main(){ vP=position.xy; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: `varying vec2 vP; uniform float uT, uK, uG, uS; uniform vec3 uA, uB;
        float h(vec2 p){ return fract(sin(dot(p,vec2(127.1,311.7))+uS)*43758.5453); }
        float n(vec2 p){ vec2 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f); return mix(mix(h(i),h(i+vec2(1,0)),f.x),mix(h(i+vec2(0,1)),h(i+vec2(1,1)),f.x),f.y); }
        void main(){
          vec2 p = vP; float r = length(p), a = atan(p.y,p.x);
          float edge = uG*(0.8 + 0.14*(n(vec2(a*2.0, uS))-0.5)*2.0 + 0.07*(n(vec2(a*6.0, uS+4.0))-0.5)*2.0);
          if (r > edge) discard;
          float sw = n(p*4.0 + vec2(uT*0.5, -uT*0.3))*0.6 + n(p*9.0 - vec2(uT*0.8, 0.0))*0.4;
          vec3 c = mix(uB, uA, 0.35 + sw*0.5);
          // anéis: 3 pontos de pingo, cada um no seu tempo
          for (int i=0;i<3;i++){ float fi=float(i); vec2 o=(vec2(h(vec2(fi,1.0)),h(vec2(fi,7.0)))-0.5)*edge*1.1;
            float ph=fract(uT*0.7+h(vec2(fi,3.0))); float d=length(p-o); float ring=smoothstep(0.035,0.0,abs(d-ph*0.6))*(1.0-ph);
            c = mix(c, vec3(0.92,0.98,1.0), ring*0.7); }
          float rim = smoothstep(edge*0.82, edge, r);
          c = mix(c, vec3(0.85,0.95,1.0), rim*0.45);
          gl_FragColor = vec4(c, uK*(0.62 + 0.25*(1.0-rim)));
        }`,
    });
    const m = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), mat);
    m.rotation.x = -Math.PI / 2; m.frustumCulled = false;
    m.at = (p, r, grow, k, t, seed = 1) => { m.visible = k > 0.01; m.position.copy(p); m.scale.setScalar(r); const u = mat.uniforms; u.uG.value = grow; u.uK.value = k; u.uT.value = t; u.uS.value = seed; };
    return m;
  }
  // coluna/redemoinho de água: cilindro aberto que sobe girando, borda de cima ondulada e espumando, cáusticas
  function column(c1 = [0.3, 0.62, 0.8], c2 = [0.06, 0.2, 0.34]) {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, side: THREE.DoubleSide,
      uniforms: { uT: { value: 0 }, uK: { value: 0 }, uH: { value: 1 }, uA: { value: new THREE.Color(...c1) }, uB: { value: new THREE.Color(...c2) } },
      vertexShader: 'varying vec3 vP; uniform float uT, uH; void main(){ vP=position; vec3 p=position; float a=atan(p.z,p.x); p.y=(p.y+0.5)*uH; p.y+= (p.y>uH*0.98? 0.06*sin(a*5.0+uT*6.0):0.0); gl_Position=projectionMatrix*modelViewMatrix*vec4(p,1.0); }',
      fragmentShader: `varying vec3 vP; uniform float uT, uK, uH; uniform vec3 uA, uB;
        float h(vec2 p){ return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }
        float n(vec2 p){ vec2 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f); return mix(mix(h(i),h(i+vec2(1,0)),f.x),mix(h(i+vec2(0,1)),h(i+vec2(1,1)),f.x),f.y); }
        void main(){
          float a = atan(vP.z, vP.x), y = vP.y + 0.5;
          vec2 q = vec2(a*1.6 + uT*2.2 + y*1.5, y*uH*3.0 - uT*1.5);
          float c1 = n(q*2.0), c2 = n(q*5.0 + 3.0);
          vec3 c = mix(uB, uA, 0.3 + 0.7*c1);
          float caus = smoothstep(0.62, 0.7, c2) * 0.6;                              // cáusticas claras
          c = mix(c, vec3(0.85,0.97,1.0), caus);
          float foam = smoothstep(0.86, 1.0, y) * (0.6 + 0.4*c2);                      // espuma na borda de cima
          c = mix(c, vec3(0.95,1.0,1.0), foam);
          float al = uK * (0.38 + 0.22*c1 + 0.3*foam + 0.25*caus);
          gl_FragColor = vec4(c, al);
        }`,
    });
    const m = new THREE.Mesh(new THREE.CylinderGeometry(1, 1, 1, 28, 6, true), mat);
    m.frustumCulled = false;
    m.at = (p, r, hgt, k, t) => { m.visible = k > 0.01 && hgt > 0.01; m.position.copy(p); m.scale.set(r, 1, r); mat.uniforms.uH.value = hgt; mat.uniforms.uK.value = k; mat.uniforms.uT.value = t; };
    return m;
  }
  // clone posável de um mob (mesma árvore): mapa nome -> grupo do clone, com a pose de repouso de cada parte.
  // Serve pra cardume (vários peixes, cada um com a sua pose) sem copiar a pose do original.
  function posable(src) {
    const pathOf = (root, o) => { const p = []; while (o && o !== root) { p.unshift(o.parent.children.indexOf(o)); o = o.parent; } return p; };
    const byPath = (root, p) => p.reduce((o, i) => o && o.children[i], root);
    const obj = src.obj.clone(true), map = {};
    for (const [n, d] of Object.entries(src.model.parts)) { const g = byPath(obj, pathOf(src.obj, d.obj)); if (g) map[n] = { g, rest: d.rest }; }
    obj.map = map; obj.v = src.v;
    obj.reset = () => { for (const k in map) { const { g, rest } = map[k]; g.position.set(...rest.pos); g.rotation.set(...rest.rot); g.scale.set(1, 1, 1); } };
    obj.r = (n, x, y, z) => { const m = map[n]; if (m) { m.g.rotation.x += x; m.g.rotation.y += y; m.g.rotation.z += z; } };
    obj.part = n => map[n] && map[n].g;
    return obj;
  }

  // Magma no chão: crosta preta em placas (voronoi) com rachaduras brilhando, miolo líquido que esfria com o tempo
  // (heat 1 = laranja-branco, 0 = crosta apagada com brasa só nas frestas). Sem bolha de ácido.
  const NOISE2 = `float h(vec2 p){ return fract(sin(dot(p,vec2(127.1,311.7))+uS)*43758.5453); }
        float n(vec2 p){ vec2 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f); return mix(mix(h(i),h(i+vec2(1,0)),f.x),mix(h(i+vec2(0,1)),h(i+vec2(1,1)),f.x),f.y); }
        vec2 vor(vec2 p){ vec2 i=floor(p), f=fract(p); float d1=8.0, d2=8.0;
          for(int y=-1;y<=1;y++) for(int x=-1;x<=1;x++){ vec2 g=vec2(x,y); vec2 o=vec2(h(i+g),h(i+g+7.3)); float d=length(g+o-f); if(d<d1){d2=d1;d1=d;} else if(d<d2) d2=d; }
          return vec2(d1,d2); }
        vec3 heatC(float t){ vec3 c=mix(vec3(0.35,0.03,0.0),vec3(0.95,0.25,0.03),smoothstep(0.0,0.4,t)); c=mix(c,vec3(1.0,0.66,0.15),smoothstep(0.4,0.75,t)); return mix(c,vec3(1.0,0.95,0.7),smoothstep(0.8,1.0,t)); }`;
  function magma() {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, side: THREE.DoubleSide,
      uniforms: { uT: { value: 0 }, uK: { value: 0 }, uG: { value: 0 }, uH: { value: 1 }, uS: { value: 0 } },
      vertexShader: 'varying vec2 vP; void main(){ vP=position.xy; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: `varying vec2 vP; uniform float uT, uK, uG, uH, uS;
        ${NOISE2}
        void main(){
          vec2 p = vP; float r = length(p), a = atan(p.y,p.x);
          float edge = uG*(0.8 + 0.18*(n(vec2(a*2.3, uS))-0.5)*2.0 + 0.08*(n(vec2(a*8.0, uS+4.0))-0.5)*2.0);
          if (r > edge) discard;
          vec2 w = p*4.0 + vec2(n(p*3.0+uT*0.2), n(p*3.0-uT*0.2+5.0))*0.6;
          vec2 v = vor(w); float crack = smoothstep(0.12, 0.0, v.y - v.x);
          float flow = n(p*5.0 + vec2(uT*0.5, -uT*0.3));
          float rim = smoothstep(edge*0.6, edge, r);
          float hot = uH * (1.0 - rim*0.8);
          // placas de crosta: escuras com cinza clara nas bordas; quanto mais quente, menos crosta
          float crust = smoothstep(0.25 + hot*0.6, 0.55 + hot*0.6, v.x*0.6 + flow*0.6 + rim*0.6);
          vec3 molten = heatC(clamp(hot*(0.65 + 0.45*flow), 0.0, 1.0));
          vec3 rock = mix(vec3(0.08,0.06,0.06), vec3(0.22,0.19,0.18), n(p*14.0));
          vec3 c = mix(molten*(1.0+hot), rock, crust);
          c = mix(c, heatC(0.35 + 0.6*uH)*(1.2+uH), crack*crust*(0.3+0.7*max(uH,0.3)));
          gl_FragColor = vec4(c, uK*(0.92 - rim*0.25));
        }`,
    });
    const m = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), mat);
    m.rotation.x = -Math.PI / 2; m.frustumCulled = false;
    m.at = (p, r, grow, k, t, heat = 1, seed = 1) => { m.visible = k > 0.01; m.position.copy(p); m.scale.setScalar(r); const u = mat.uniforms; u.uG.value = grow; u.uK.value = k; u.uT.value = t; u.uH.value = heat; u.uS.value = seed; };
    return m;
  }
  // Rachadura de magma que corre pelo chão de a até b: fenda principal em zigue-zague + galhos, abre no comprimento len (0..1)
  function crack() {
    const mat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, side: THREE.DoubleSide, blending: THREE.NormalBlending,
      uniforms: { uT: { value: 0 }, uK: { value: 0 }, uL: { value: 0 }, uH: { value: 1 }, uS: { value: 0 }, uR: { value: 6 } },
      vertexShader: 'varying vec2 vP; void main(){ vP=position.xy; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }',
      fragmentShader: `varying vec2 vP; uniform float uT, uK, uL, uH, uS, uR;
        ${NOISE2}
        void main(){
          float x = vP.x + 0.5, y = vP.y;   // x: 0 (origem) .. 1 (ponta); y: -0.5..0.5 de lado (já escalado pela razão)
          if (x > uL) discard;
          float zig = (n(vec2(x*uR*1.2, uS))-0.5)*0.32 + (n(vec2(x*uR*4.0, uS+3.0))-0.5)*0.1;
          float wdt = 0.11 * (1.0 - 0.65*x) * smoothstep(0.0, 0.08, uL - x + 0.08);
          float d = abs(y - zig);
          // galhos: em alguns trechos sai uma fenda fina pra um lado
          float seg = floor(x*uR*1.5); float sx = fract(x*uR*1.5); float side = h(vec2(seg, 2.0)) > 0.5 ? 1.0 : -1.0;
          float br = h(vec2(seg, 9.0)) > 0.45 ? abs(y - zig - side*sx*0.35*(1.0-sx*0.3)) : 9.0;
          float dd = min(d, br*1.6);
          if (dd > wdt + 0.09) discard;
          float core = smoothstep(wdt*0.75, wdt*0.15, dd);
          float lip = smoothstep(wdt + 0.09, wdt, dd);
          vec3 c = mix(vec3(0.06,0.04,0.04), heatC(uH*(0.45+0.4*n(vec2(x*20.0 - uT*3.0, 1.0))))*(1.0+0.5*uH), core);
          float al = uK * max(core, lip*0.9);
          gl_FragColor = vec4(c, al);
        }`,
    });
    const m = new THREE.Mesh(new THREE.PlaneGeometry(1, 1), mat);
    m.frustumCulled = false;
    m.at = (a, b, width, len, k, t, heat = 1, seed = 1) => {
      m.visible = k > 0.01 && len > 0.001;
      const d = new THREE.Vector3().subVectors(b, a); const L = d.length();
      m.position.copy(a).add(b).multiplyScalar(0.5); m.position.y += 0.012;
      m.rotation.set(-Math.PI / 2, 0, Math.atan2(-d.z, d.x));
      m.scale.set(L, width, 1);
      const u = mat.uniforms; u.uK.value = k; u.uL.value = len; u.uT.value = t; u.uH.value = heat; u.uS.value = seed; u.uR.value = L / width * 0.5;
    };
    return m;
  }
  // coluna de basalto: prisma de 6 lados com faixas horizontais (juntas de resfriamento), arestas claras e brasa nas
  // juntas enquanto está quente
  function basalt(seed = 1) {
    const mat = new THREE.ShaderMaterial({
      uniforms: { uH: { value: 1 }, uS: { value: seed }, uHt: { value: 1 } },
      vertexShader: 'varying vec3 vP; varying vec3 vN; varying vec3 vW; void main(){ vP=position; vN=normalize(mat3(modelMatrix)*normal); vW=(modelMatrix*vec4(position,1.0)).xyz; gl_Position=projectionMatrix*viewMatrix*vec4(vW,1.0); }',
      fragmentShader: `varying vec3 vP; varying vec3 vN; varying vec3 vW; uniform float uH, uS, uHt;
        ${NOISE2.replace(/\+uS\)/, '+uS)')}
        void main(){
          float a = atan(vP.z, vP.x); float fa = fract(a/6.2832*6.0 + 0.0833);
          float y = (vP.y + 0.5) * uHt;
          float top = step(0.99, vN.y);
          float L = 0.45 + 0.55*clamp(dot(vN, normalize(vec3(0.4,1.0,0.3))), 0.0, 1.0);
          vec3 c = mix(vec3(0.13,0.12,0.13), vec3(0.24,0.22,0.23), n(vec2(fa*6.0 + uS, y*6.0)));
          float edge = smoothstep(0.06, 0.0, min(fa, 1.0-fa));
          c = mix(c, vec3(0.34,0.32,0.32), edge*(1.0-top));
          float fy = fract(y*1.4 + h(vec2(floor(fa*6.0+0.5),uS))*0.5); float band = smoothstep(0.05, 0.0, min(fy, 1.0-fy));
          vec3 hot = mix(vec3(1.0,0.35,0.05), vec3(1.0,0.75,0.3), n(vec2(a*3.0, y*4.0)));
          c = mix(c, hot*(1.0+uH), band*uH*(1.0-top));
          if (top > 0.5) { vec2 q = vP.xz*3.0; float cr = smoothstep(0.1, 0.0, abs(n(q*2.0)-0.5)); c = mix(vec3(0.22,0.2,0.2), hot*(1.2+uH), cr*uH); }
          gl_FragColor = vec4(c*L + hot*0.35*uH*(1.0-top)*smoothstep(0.25,0.0,y), 1.0);
        }`,
    });
    const m = new THREE.Mesh(new THREE.CylinderGeometry(1, 1, 1, 6, 1), mat);
    m.frustumCulled = false;
    m.at = (p, r, hgt, heat, rotY = 0) => { m.visible = hgt > 0.01; m.position.set(p.x, p.y + hgt / 2, p.z); m.scale.set(r, hgt, r); m.rotation.set(0, rotY, 0); mat.uniforms.uH.value = heat; mat.uniforms.uHt.value = hgt; };
    return m;
  }
  // morte com física (Stark 2026-11-14: "bicho que morre não fica flutuando"): o corpo deitado encosta no chão. Mede o
  // ponto mais baixo do modelo já posado e desce (ou sobe) o corpo até ele tocar o chão: quem anda tomba apoiado na
  // própria borda o tempo todo; quem voa cai junto com a queda da morte (T 0,2 a 1,0 s).
  const FLY = { mosca: 1, peixe: 1, inseto: 1, lanterna: 1 };
  // quem já fica com uma parte dentro do chão parado (raízes, cabelo arrastando, o embrião meio enterrado): o chão dele é
  // essa altura no começo da morte (medida no 'parado' com lowest), indo pro chão de verdade enquanto tomba: deitado
  // morto, ele fica por cima do chão
  const REST = { cabecao: -0.46, embriao: -0.96, kuchisake: -0.29, olhos: -0.23, rastejante: -0.22, larva: -0.16 };
  const _box = new THREE.Box3(), _v = new THREE.Vector3();
  function lowest(obj) {
    obj.updateMatrixWorld(true);
    let lo = Infinity;
    obj.traverse(o => {
      if (!o.isMesh || !o.visible || !o.geometry || o._vap) return;
      for (let p = o.parent; p; p = p.parent) if (!p.visible) return;
      const pos = o.geometry.attributes.position;
      for (let i = 0; i < pos.count; i++) { _v.fromBufferAttribute(pos, i).applyMatrix4(o.matrixWorld); if (_v.y < lo) lo = _v.y; }
    });
    return lo;
  }
  const DUR = {};
  function deathDur(m) {
    if (DUR[m.id] != null) return DUR[m.id];
    const src = m.anim.toString(), i = src.indexOf("mode === 'morte'");
    let d = 3.4;
    if (i >= 0) { const blk = src.slice(i, i + 1500), a = /t % ([\d.]+)/.exec(blk), b = /dur: ([\d.]+)/.exec(blk); d = a ? +a[1] : b ? +b[1] : 3.4; }
    return (DUR[m.id] = d);
  }
  // quem morre entrando num buraco (a Mulher do Poço volta pro poço) não é empurrada pra cima do chão
  const SINKS = { poco: 1 };
  function settle(m, mode, t, obj) {
    if (mode !== 'morte' || SINKS[m.id]) return;
    const lo = lowest(obj);
    if (!isFinite(lo)) return;
    const T = t % deathDur(m), k = clamp01((T - 0.2) / 0.8);
    const e2 = k * k * (3 - 2 * k);
    obj.position.y -= (lo - (REST[m.id] || 0) * (1 - e2)) * e2;   // entra aos poucos: sem pulos quando um membro troca de lugar com outro como ponto mais baixo
  }
  // tombo sem escorregar: compara onde o tronco (part) fica sem e com a rotação da queda e puxa o corpo de volta,
  // deixando só 'max' de deslocamento pro lado da queda (como um bicho de verdade que desaba onde estava)
  function noSlide(obj, part, ax, unset, set, max, mover) {
    const V = THREE.Vector3;
    unset(); obj.updateMatrixWorld(true); const a = part.getWorldPosition(new V());
    set(); obj.updateMatrixWorld(true); const b = part.getWorldPosition(new V());
    const d = b[ax] - a[ax], keep = Math.sign(d) * Math.min(Math.abs(d), max);
    if (!mover || mover === obj) { obj.position[ax] += keep - d; return; }
    // uma peça se move em vez do corpo todo (o jogo gira e desliza a raiz num quadro espelhado, as peças não):
    // o deslocamento do mundo vai pro espaço do pai da peça
    const par = mover.parent, w0 = mover.getWorldPosition(new V()), w1 = w0.clone();
    w1[ax] += keep - d;
    par.worldToLocal(w0); par.worldToLocal(w1);
    mover.position.add(w1.sub(w0));
  }
  // membro que escora o corpo morto: um morto não fica de pé na ponta de um rabo ou de uma mão. Cada membro (a peça
  // raiz da cadeia) gira aos poucos pro lado que mais o levanta, até não passar do fundo do corpo ('core'); w é o
  // quanto a queda já andou (no começo o membro pode descer à vontade, no fim nada fica abaixo do corpo)
  const _q = new THREE.Vector3();
  function chainLow(o) {
    let lo = Infinity;
    o.updateMatrixWorld(true);
    o.traverse(c => {
      if (!c.isMesh || !c.visible || !c.geometry || c._vap) return;
      const pos = c.geometry.attributes.position;
      for (let i = 0; i < pos.count; i++) { _q.fromBufferAttribute(pos, i).applyMatrix4(c.matrixWorld); if (_q.y < lo) lo = _q.y; }
    });
    return lo;
  }
  function floorLimbs(obj, P, core, limbs, w, slack = 0.01) {
    if (w <= 0) return;
    obj.updateMatrixWorld(true);
    let base = Infinity;
    for (const n of core) if (P[n]) {
      const o = P[n].obj;
      o.children.forEach(c => {
        if (!c.isMesh || !c.visible || !c.geometry) return;
        const pos = c.geometry.attributes.position;
        for (let i = 0; i < pos.count; i++) { _q.fromBufferAttribute(pos, i).applyMatrix4(c.matrixWorld); if (_q.y < base) base = _q.y; }
      });
    }
    if (!isFinite(base)) return;
    for (const n of limbs) {
      if (!P[n]) continue;
      const o = P[n].obj;
      let lo = chainLow(o);
      const need = base - slack - (1 - w) * 1.2;
      for (let k = 0; k < 60 && lo < need; k++) {
        let best = null, bestLo = lo;
        for (const [ax, sg] of [['x', 1], ['x', -1], ['z', 1], ['z', -1]]) {
          o.rotation[ax] += sg * 0.03;
          const l = chainLow(o);
          if (l > bestLo + 1e-5) { bestLo = l; best = [ax, sg]; }
          o.rotation[ax] -= sg * 0.03;
        }
        if (!best) break;
        o.rotation[best[0]] += best[1] * 0.03;
        lo = bestLo;
      }
    }
  }
  return { noSlide, floorLimbs, settle, lowest, dissolve, ash, exorcize, tube, clamp01, drops, wet, column, posable, magma, crack, basalt };
})();
