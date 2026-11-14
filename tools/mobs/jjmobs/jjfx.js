// Efeitos dos mobs de Jujutsu (por cima de FX e DBFX): maldição sendo exorcizada (o corpo se desfaz em cinza preta,
// texel por texel, do jeito que as maldições somem no mangá), névoa de energia amaldiçoada e resíduo no chão.
'use strict';
const JJFX = (() => {
  const clamp01 = v => Math.max(0, Math.min(1, v));
  // ---------- desfazer: remenda o material do modelo (um por entidade) com um corte por texel ----------
  function patch(e) {
    if (e._dis) return e._dis;
    const U = { uDis: { value: 0 }, uY0: { value: 0 }, uY1: { value: 2 } };
    e.obj.traverse(o => {
      if (!o.material || o.material._jj) return;
      o.material._jj = true;
      o.material.onBeforeCompile = sh => {
        Object.assign(sh.uniforms, U);
        sh.vertexShader = 'varying vec3 vWp;\n' + sh.vertexShader.replace('#include <project_vertex>', '#include <project_vertex>\n vWp = (modelMatrix*vec4(transformed,1.0)).xyz;');
        sh.fragmentShader = 'varying vec3 vWp; uniform float uDis, uY0, uY1;\nfloat jh(vec3 p){ p=fract(p*0.3183+vec3(.71,.11,.42)); p*=17.0; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }\n' +
          sh.fragmentShader.replace('#include <map_fragment>', `#include <map_fragment>
          if (uDis > 0.0) {
            vec3 c = floor(vWp*16.0+0.001);
            float hy = clamp((vWp.y-uY0)/(uY1-uY0), 0.0, 1.0);
            float v = jh(c)*0.62 + (1.0-hy)*0.38;            // de cima pra baixo, mas cada texel no seu tempo
            float edge = v - (uDis*1.25 - 0.12);
            if (edge < 0.0) discard;
            if (edge < 0.05) diffuseColor.rgb = vec3(0.02,0.01,0.03);
            else if (edge < 0.1) diffuseColor.rgb = mix(diffuseColor.rgb, vec3(0.05,0.03,0.07), 0.7);
          }`);
      };
      o.material.needsUpdate = true;
    });
    e._dis = U;
    return U;
  }
  function dissolve(e, k, y0 = 0, y1 = 2) { const U = patch(e); U.uDis.value = k; U.uY0.value = y0; U.uY1.value = y1; }

  // cinza preta subindo: cada floco com tamanho, giro de caminho e tempo próprios
  function ash(seed = 3, n = 140) {
    return DBFX.grains({
      n, seed, palette: [[12, 8, 16], [26, 20, 30], [40, 30, 46], [8, 6, 10], [70, 40, 90]], size: [0.02, 0.07], drag: 1.4, grav: -1.1, life: 1.6, spread: 1.2,
      cone: R => { const a = R() * Math.PI * 2, o = 0.2 + R() * 0.9; return [Math.cos(a) * o, 0.3 + R() * 1.2, Math.sin(a) * o]; },
    });
  }
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
      if (!o.isMesh || !o.visible || !o.geometry) return;
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
  function settle(m, mode, t, obj) {
    if (mode !== 'morte') return;
    const lo = lowest(obj);
    if (!isFinite(lo)) return;
    const T = t % deathDur(m), k = clamp01((T - 0.2) / 0.8);
    const e2 = k * k * (3 - 2 * k);
    obj.position.y -= (lo - (REST[m.id] || 0) * (1 - e2)) * (FLY[m.id] ? e2 : 1);
  }
  return { settle, lowest, dissolve, ash, exorcize, tube, clamp01, drops, wet, column, posable, magma, crack, basalt };
})();
