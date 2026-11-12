// Gerador de Shibuya (prévia = jogo). Fonte da verdade do port em Java, como os biomas do Naruto (forja/naruto-*):
// mesmo sorteio (xorshift32), mesmo hash, tudo saindo das coordenadas do mundo.
// Shibuya: grade de ruas tortinha (avenidas largas com canteiro de árvores e grade de proteção, ruas estreitas com
// poste de luz e fiação), becos que cortam as quadras, prédios colados uns nos outros, cada um sorteado inteiro:
// fachada (vidro, faixas de concreto, pastilha, prédio-lápis), cor, altura, recuo no alto, loja no térreo com toldo e
// letreiro, placas verticais com "letras", tanques, ar-condicionado, outdoors e antenas no telhado, janelas acesas de
// noite. O cruzamento grande tem faixas na diagonal e telões. Uns prédios são amaldiçoados: janelas quebradas,
// manchas pretas escorrendo, veias roxas acesas, entrada tapada com tábuas, fita e talismãs. Embaixo da avenida
// principal passa o metrô: entradas na calçada, saguão com catracas, plataforma central com trem parado e túneis
// que seguem pros dois lados, um deles tomado pelas maldições.
'use strict';

const NAMES = [
  'AIR', 'STONE', 'SOIL', 'ASPHALT', 'LANE', 'YELLOW', 'ZEBRA', 'SIDEWALK', 'SIDEWALK2', 'TACTILE', 'CURB', 'LOT',
  'ALLEY', 'GRAVEL', 'PARK_LINE',
  'WALL0', 'WALL1', 'WALL2', 'WALL3', 'WALL4', 'WALL5', 'WALL6', 'WALL7',
  'GLASS0', 'GLASS1', 'GLASS2', 'GLASS3', 'WIN', 'MULLION', 'SLAB', 'CORNER', 'ROOF', 'PARAPET',
  'SHOP', 'DOOR', 'AWNING0', 'AWNING1', 'AWNING2', 'AWNING3',
  'SIGN0', 'SIGN1', 'SIGN2', 'SIGN3', 'SIGN4', 'SIGN5', 'SIGN6', 'SIGN7', 'GLYPH', 'SIGN_FRAME', 'SCREEN',
  'METAL', 'METAL_DARK', 'TANK', 'AVIATION', 'POLE', 'CABLE', 'LAMP', 'TRAFFIC_BOX', 'TRAFFIC_R', 'TRAFFIC_G', 'RAILING',
  'PLANTER', 'TRUNK', 'LEAVES', 'LEAVES2', 'VENDING', 'VEND0', 'VEND1', 'VEND2', 'VEND3',
  'TORII', 'TORII_TOP', 'SHRINE_WOOD', 'SHRINE_ROOF', 'LANTERN',
  'CURSE_STAIN', 'CURSE_VEIN', 'CURSE_GLOW', 'CURSE_FLOOR', 'BOARD', 'TAPE', 'TALISMAN', 'DEAD_SIGN',
  'TILE_WALL', 'TILE_LINE', 'TILE_FLOOR', 'CEIL', 'CEIL_LIGHT', 'PILLAR', 'GATE', 'EDGE', 'STAIR', 'BENCH', 'METRO_SIGN',
  'SIGN_DARK', 'BALLAST', 'RAIL', 'TUNNEL', 'TUNNEL_LIGHT', 'CROWN_LED', 'FIN', 'DECK_GLASS', 'LOUVER',
  'TRAIN', 'TRAIN_STRIPE', 'TRAIN_WIN', 'TRAIN_DOOR', 'TRAIN_ROOF', 'TRAIN_END', 'TRAIN_UNDER', 'TRAIN_FRONT', 'HEADLIGHT',
];
const B = {};
NAMES.forEach((n, i) => { B[n] = i; });
// Janela acesa de noite: o tipo da janela + LIT (e COOL quando a luz é branca fria de escritório). De dia é vidro.
const LIT = 256, COOL = 512;
const base = (t) => t & 255;

function Rng(seed) {
  let s = (seed | 0) || 0x9E3779B9;
  const next = () => { s ^= s << 13; s ^= s >>> 17; s ^= s << 5; return (s >>> 0) / 4294967296; };
  return {
    next,
    range: (a, b) => a + (b - a) * next(),
    int: (a, b) => a + Math.floor(next() * (b - a + 1)),
  };
}

function hash3(x, y, z, seed) {
  let h = (Math.imul(x, 73856093) ^ Math.imul(y, 19349663) ^ Math.imul(z, 83492791) ^ Math.imul(seed, 2654435761)) | 0;
  h ^= h >>> 13; h = Math.imul(h, 0x5bd1e995); h ^= h >>> 15;
  return (h >>> 0) / 4294967296;
}
const hashInt = (x, y, z, seed) => Math.floor(hash3(x, y, z, seed) * 2147483646) + 1;

// Voxels; fora de [lo, hi] (x e z) nada é gravado (o pedaço da prévia; no jogo, o chunk).
class Vox {
  constructor(lo, hi) { this.m = new Map(); this.lo = lo === undefined ? -1e9 : lo; this.hi = hi === undefined ? 1e9 : hi; }
  key(x, y, z) { return ((x + 2048) * 4096 + (y + 256)) * 4096 + (z + 2048); }
  in(x, z) { return x >= this.lo && x <= this.hi && z >= this.lo && z <= this.hi; }
  get(x, y, z) { return this.m.get(this.key(x, y, z)) || 0; }
  set(x, y, z, t) { if (this.in(x, z) && !this.m.has(this.key(x, y, z))) this.m.set(this.key(x, y, z), t); }
  force(x, y, z, t) { if (!this.in(x, z)) return; if (t === 0) this.m.delete(this.key(x, y, z)); else this.m.set(this.key(x, y, z), t); }
  each(f) { for (const [k, t] of this.m) { const z = k % 4096 - 2048, r = (k - (z + 2048)) / 4096, y = r % 4096 - 256, x = (r - (y + 256)) / 4096 - 2048; f(x, y, z, t); } }
}

const smooth = (a) => a * a * (3 - 2 * a);

function valueNoise3(x, y, z, seed, scale) {
  const fx = x / scale, fy = y / scale, fz = z / scale;
  const ix = Math.floor(fx), iy = Math.floor(fy), iz = Math.floor(fz);
  const tx = smooth(fx - ix), ty = smooth(fy - iy), tz = smooth(fz - iz);
  const l = (a, b, t) => a + (b - a) * t;
  const h = (a, b, c) => hash3(a, b, c, seed);
  const x00 = l(h(ix, iy, iz), h(ix + 1, iy, iz), tx), x10 = l(h(ix, iy + 1, iz), h(ix + 1, iy + 1, iz), tx);
  const x01 = l(h(ix, iy, iz + 1), h(ix + 1, iy, iz + 1), tx), x11 = l(h(ix, iy + 1, iz + 1), h(ix + 1, iy + 1, iz + 1), tx);
  return l(l(x00, x10, ty), l(x01, x11, ty), tz);
}

// ---------------------------------------------------------------- ruas

// Ruas a cada ~64 blocos nos dois eixos, cada uma deslocada um pouco. A rua 'x' corre ao longo de x e fica em z = c;
// a rua 'z' corre ao longo de z e fica em x = c. A rua 0 dos dois eixos é sempre avenida (o cruzamento grande e o
// metrô ficam nelas). Faixas em relação a c: pista [r0, r1], calçadas [s0, r0 - 1] e [r1 + 1, s1].
const CELL = 64;
function street(axis, i, seed) {
  const a = axis === 'x' ? 1 : 2;
  const avenue = i === 0 || hash3(i, a, 8, seed) < 0.34;
  const c = i * CELL + (i === 0 ? 0 : Math.round((hash3(i, a, 7, seed) - 0.5) * 18));
  return avenue ? { i, c, avenue, r0: -6, r1: 5, s0: -11, s1: 10 } : { i, c, avenue, r0: -3, r1: 2, s0: -6, s1: 5 };
}

// A rua do eixo que pega a coordenada v (ou null), com d = v - c.
function streetAt(axis, v, seed) {
  const k = Math.round(v / CELL);
  for (let i = k - 1; i <= k + 1; i++) {
    const s = street(axis, i, seed), d = v - s.c;
    if (d >= s.s0 && d <= s.s1) return { s, d };
  }
  return null;
}
const inRoad = (r) => r && r.d >= r.s.r0 && r.d <= r.s.r1;
// Fileira da calçada contando do meio-fio (0) pra dentro.
const sideRow = (r) => (r.d > r.s.r1 ? r.d - r.s.r1 - 1 : r.s.r0 - 1 - r.d);

// O cruzamento das ruas i ('x') e j ('z') é o cruzamento grande, com faixas na diagonal e telões?
function scramble(i, j, seed) {
  if (i === 0 && j === 0) return true;
  return street('x', i, seed).avenue && street('z', j, seed).avenue && hash3(i, j, 9, seed) < 0.3;
}

// O topo do chão (y = 0) na coluna, ou null se é lote (dentro de uma quadra).
function groundAt(x, z, seed) {
  const X = streetAt('x', z, seed), Z = streetAt('z', x, seed);
  const rx = inRoad(X), rz = inRoad(Z);
  if (rx && rz) {
    if (scramble(X.s.i, Z.s.i, seed)) {
      // faixas na diagonal: duas bandas em X de 3 blocos, listradas
      const p = X.d - (X.s.r0 + X.s.r1) / 2, q = Z.d - (Z.s.r0 + Z.s.r1) / 2;
      if ((Math.abs(p - q) <= 1.5 || Math.abs(p + q) <= 1.5) && ((Math.floor(p + q) & 1) === 0)) return B.ZEBRA;
    }
    return B.ASPHALT;
  }
  if (rx || rz) {
    const R = rx ? X : Z, O = rx ? Z : X, along = rx ? x : z;
    // a rua passando pela calçada da rua que cruza: faixa de pedestre (listras paralelas ao trânsito)
    if (O) {
      const k = sideRow(O);
      if (k >= 1) return ((R.d - R.s.r0) & 1) === 0 ? B.ZEBRA : B.ASPHALT;
      return B.LANE; // linha de parada
    }
    const d = R.d;
    if (R.s.avenue) {
      if (d === 0) return B.YELLOW;
      if ((d === -3 || d === 3) && (((along % 8) + 8) % 8) < 4) return B.LANE;
      if (d === R.s.r0 || d === R.s.r1) return B.LANE;
    } else if (d === 0 && (((along % 6) + 6) % 6) < 3) return B.LANE;
    return B.ASPHALT;
  }
  if (X || Z) {
    const k = Math.min(X ? sideRow(X) : 99, Z ? sideRow(Z) : 99);
    const av = (X && X.s.avenue) || (Z && Z.s.avenue);
    if (k === 0) return B.CURB;
    if (av && k === 2 && !(X && Z)) return B.TACTILE;
    return hash3(x, 0, z, seed + 3) < 0.28 ? B.SIDEWALK2 : B.SIDEWALK;
  }
  return null;
}

// ---------------------------------------------------------------- quadras e lotes

// A quadra entre as ruas 'x' i e i + 1 e as ruas 'z' j e j + 1: o retângulo de dentro, os lados que dão pra
// avenida, um beco às vezes, e os lotes (divididos ao meio até ficarem do tamanho de um prédio).
function blockPlan(i, j, seed) {
  const n = street('x', i, seed), s = street('x', i + 1, seed), w = street('z', j, seed), e = street('z', j + 1, seed);
  const rect = { x0: w.c + w.s1 + 1, x1: e.c + e.s0 - 1, z0: n.c + n.s1 + 1, z1: s.c + s.s0 - 1 };
  const av = { n: n.avenue, s: s.avenue, w: w.avenue, e: e.avenue };
  const W = rect.x1 - rect.x0 + 1, D = rect.z1 - rect.z0 + 1;
  // arranha-céu raro: a quadra inteira vira base + torre (Stark 2026-11-12: "um bem altão, raro, Shibuya vibes")
  if (towerBlock(i, j, seed) && W >= 30 && D >= 30) return { rect, alleys: [], lots: [], tower: { av, seed: hashInt(i, j, 29, seed) } };
  const rng = Rng(hashInt(i, j, 11, seed));
  const alleys = [], lots = [];
  let parts = [{ ...rect, f: { n: true, s: true, w: true, e: true }, alley: {} }];
  if (rng.next() < 0.5 && Math.max(W, D) >= 32) {
    // beco de 3 cortando a quadra de rua a rua
    if (W >= D) {
      const ax = rect.x0 + Math.floor(W * rng.range(0.35, 0.65));
      alleys.push({ x0: ax, x1: ax + 2, z0: rect.z0, z1: rect.z1 });
      parts = [{ ...rect, x1: ax - 1, f: { n: true, s: true, w: true, e: true }, alley: { e: true } },
        { ...rect, x0: ax + 3, f: { n: true, s: true, w: true, e: true }, alley: { w: true } }];
    } else {
      const az = rect.z0 + Math.floor(D * rng.range(0.35, 0.65));
      alleys.push({ x0: rect.x0, x1: rect.x1, z0: az, z1: az + 2 });
      parts = [{ ...rect, z1: az - 1, f: { n: true, s: true, w: true, e: true }, alley: { s: true } },
        { ...rect, z0: az + 3, f: { n: true, s: true, w: true, e: true }, alley: { n: true } }];
    }
  }
  const split = (p) => {
    const pw = p.x1 - p.x0 + 1, pd = p.z1 - p.z0 + 1;
    if (Math.max(pw, pd) <= 20 || Math.min(pw, pd) < 8) { lots.push(p); return; }
    if (pw >= pd) {
      const cx = p.x0 + Math.max(7, Math.min(pw - 8, Math.floor(pw * rng.range(0.35, 0.65))));
      split({ ...p, x1: cx - 1, f: { ...p.f, e: false }, alley: { ...p.alley, e: false } });
      split({ ...p, x0: cx, f: { ...p.f, w: false }, alley: { ...p.alley, w: false } });
    } else {
      const cz = p.z0 + Math.max(7, Math.min(pd - 8, Math.floor(pd * rng.range(0.35, 0.65))));
      split({ ...p, z1: cz - 1, f: { ...p.f, s: false }, alley: { ...p.alley, s: false } });
      split({ ...p, z0: cz, f: { ...p.f, n: false }, alley: { ...p.alley, n: false } });
    }
  };
  for (const p of parts) split(p);
  // um lado é frente se dá pra rua (borda da quadra) ou pro beco
  for (const l of lots) {
    l.front = {
      n: (l.z0 === rect.z0) || !!l.alley.n, s: (l.z1 === rect.z1) || !!l.alley.s,
      w: (l.x0 === rect.x0) || !!l.alley.w, e: (l.x1 === rect.x1) || !!l.alley.e,
    };
    l.avenue = (l.z0 === rect.z0 && av.n) || (l.z1 === rect.z1 && av.s) || (l.x0 === rect.x0 && av.w) || (l.x1 === rect.x1 && av.e);
    l.seed = hashInt(l.x0, l.z0, 13, seed);
    const r = Rng(l.seed), area = (l.x1 - l.x0 + 1) * (l.z1 - l.z0 + 1);
    l.kind = area <= 150 && r.next() < 0.16 ? 'shrine' : area <= 260 && r.next() < 0.07 ? 'parking' : 'building';
  }
  // os quatro lotes de canto de um cruzamento grande ganham telão
  const corners = [[n, w, 'n', 'w'], [n, e, 'n', 'e'], [s, w, 's', 'w'], [s, e, 's', 'e']];
  for (const [zs, xs, a, b] of corners) {
    if (!scramble(zs.i, xs.i, seed)) continue;
    const cx = b === 'w' ? rect.x0 : rect.x1, cz = a === 'n' ? rect.z0 : rect.z1;
    const lot = lots.find((l) => (b === 'w' ? l.x0 === cx : l.x1 === cx) && (a === 'n' ? l.z0 === cz : l.z1 === cz));
    if (lot) { lot.kind = 'building'; lot.screen = { [a]: true, [b]: true }; lot.avenue = true; }
  }
  return { rect, alleys, lots };
}

// Uma quadra a cada 3 x 3 (num lugar sorteado dela), com 55% de chance, e nunca num canto de cruzamento grande (lá
// ficam os telões): ~1 arranha-céu a cada 190 x 190 blocos de cidade, uns 6 num bioma de 1.000 a 1.500 blocos.
function towerBlock(i, j, seed) {
  const si = Math.floor(i / 3), sj = Math.floor(j / 3);
  if (hash3(si, sj, 27, seed) >= 0.55) return false;
  const ti = si * 3 + Math.floor(hash3(si, sj, 28, seed) * 3), tj = sj * 3 + Math.floor(hash3(sj, si, 28, seed) * 3);
  if (ti !== i || tj !== j) return false;
  return !scramble(i, j, seed) && !scramble(i + 1, j, seed) && !scramble(i, j + 1, seed) && !scramble(i + 1, j + 1, seed);
}

// ---------------------------------------------------------------- arranha-céu

// Arranha-céu: base comercial larga (lojas, telão, letreiros) e, saindo dela, uma torre de vidro de 120 a 175 blocos,
// sorteada inteira: planta (quadrada de cantos chanfrados, com recuos em degrau, ou afinando), tom do vidro, aletas
// brancas verticais, faixas de andar técnico com venezianas, escritórios acesos de noite, e no topo o mirante a céu
// aberto com guarda-corpo de vidro, a coroa de LED, o heliponto com o H e a antena com luzes vermelhas.
function skyscraper(w, plan, seed) {
  const r = plan.rect, rng = Rng(plan.tower.seed), put = (x, y, z, t) => w.force(x, y, z, t);
  const W = r.x1 - r.x0 + 1, D = r.z1 - r.z0 + 1;
  // base: 3 a 5 andares, ocupando a quadra toda menos 1
  const podFloors = rng.int(3, 5), podTop = 1 + 5 + podFloors * 4 - 1;
  const podWall = B.WALL0 + rng.int(0, 7), podSign = B.SIGN0 + rng.int(0, 7);
  const bx0 = r.x0 + 1, bx1 = r.x1 - 1, bz0 = r.z0 + 1, bz1 = r.z1 - 1;
  for (let x = r.x0; x <= r.x1; x++) for (let z = r.z0; z <= r.z1; z++) put(x, 0, z, B.SIDEWALK2);
  const faceOf = (x, z) => (z === bz0 ? 0 : z === bz1 ? 1 : x === bx0 ? 2 : 3);
  for (let y = 1; y <= podTop; y++) {
    const yRel = y - 1;
    for (let x = bx0; x <= bx1; x++) for (let z = bz0; z <= bz1; z++) {
      const edge = x === bx0 || x === bx1 || z === bz0 || z === bz1;
      if (!edge) { if (y === podTop) put(x, y, z, B.ROOF); continue; }
      const f = faceOf(x, z), u = f < 2 ? x - bx0 : z - bz0, len = f < 2 ? bx1 - bx0 + 1 : bz1 - bz0 + 1;
      const corner = u === 0 || u === len - 1;
      let t;
      if (corner) t = B.CORNER;
      else if (yRel < 5) t = yRel === 4 ? (glyph(u, 1, plan.tower.seed + f) && u > 2 && u < len - 3 ? B.GLYPH : podSign)
        : (Math.abs(u - Math.floor(len / 2)) <= 1 && yRel <= 2 ? B.DOOR : B.SHOP);
      else {
        const rr = (yRel - 5) & 3;
        t = rr === 0 ? podWall : (u % 6 === 0 ? B.FIN : B.SHOP);
      }
      if (y === podTop) t = B.PARAPET;
      put(x, y, z, t);
    }
  }
  // telão grande numa das frentes da base que dá pra avenida (ou na primeira)
  const avs = ['n', 's', 'w', 'e'].filter((k) => plan.tower.av[k]);
  const sf = avs.length ? avs[rng.int(0, avs.length - 1)] : 'n', horiz = sf === 'n' || sf === 's';
  const len = horiz ? W - 2 : D - 2, sw = Math.min(22, len - 6), sh = Math.min(podTop - 7, 12), u0 = rng.int(3, len - 3 - sw);
  for (let u = u0; u < u0 + sw; u++) for (let y = 7; y < 7 + sh; y++) {
    const x = horiz ? bx0 + u : (sf === 'w' ? bx0 - 1 : bx1 + 1), z = horiz ? (sf === 'n' ? bz0 - 1 : bz1 + 1) : bz0 + u;
    put(x, y, z, u === u0 || u === u0 + sw - 1 || y === 7 || y === 6 + sh ? B.SIGN_FRAME : B.SCREEN);
  }

  // torre
  const S = Math.max(16, Math.min(26, Math.min(W, D) - 8));
  const hx0 = S / 2, hz0 = (S - 2 * rng.int(0, 2)) / 2;
  const cx = Math.floor((bx0 + bx1) / 2) + rng.int(-2, 2), cz = Math.floor((bz0 + bz1) / 2) + rng.int(-2, 2);
  const top = rng.int(120, 175), deck = top - rng.int(8, 11);
  const shape = ['chamfer', 'steps', 'taper'][rng.int(0, 2)];
  const cham = rng.int(3, 5), glass = B.GLASS0 + rng.int(0, 3);
  const steps = [podTop + Math.floor((top - podTop) * rng.range(0.45, 0.6)), podTop + Math.floor((top - podTop) * rng.range(0.72, 0.85))];
  const finEvery = rng.int(4, 6), mech = rng.int(26, 34);
  const half = (y) => {
    let k = 0;
    if (shape === 'steps') k = (y >= steps[0] ? 2 : 0) + (y >= steps[1] ? 2 : 0);
    else if (shape === 'taper') k = Math.floor(3 * (y - podTop) / (top - podTop));
    return [hx0 - k, hz0 - k];
  };
  const inside = (dx, dz, y) => {
    const [hx, hz] = half(y), c = shape === 'steps' ? 2 : cham;
    const ax = Math.abs(dx + 0.5), az = Math.abs(dz + 0.5);
    return ax <= hx && az <= hz && ax + az <= hx + hz - c;
  };
  const R = Math.ceil(Math.max(hx0, hz0)) + 1;
  for (let y = podTop + 1; y <= top; y++) {
    const yRel = y - podTop - 1, f = yRel >> 2, rr = yRel & 3;
    const mechBand = yRel % mech >= mech - 2 && y < deck - 2;
    for (let dx = -R; dx <= R; dx++) for (let dz = -R; dz <= R; dz++) {
      if (!inside(dx, dz, y)) continue;
      const shell = !inside(dx + 1, dz, y) || !inside(dx - 1, dz, y) || !inside(dx, dz + 1, y) || !inside(dx, dz - 1, y);
      const x = cx + dx, z = cz + dz;
      if (!shell) {
        // laje de cima de cada degrau / telhado, e o piso do mirante
        if (y === top || y === deck || !inside(dx, dz, y + 1)) put(x, y, z, B.ROOF);
        continue;
      }
      // coordenada ao longo da fachada pras aletas
      const u = Math.abs(dx) >= Math.abs(dz) ? dz + 100 : dx + 100;
      let t;
      if (y > deck) t = y === top ? B.CROWN_LED : (y === deck + 1 ? B.SLAB : B.DECK_GLASS);
      else if (y === deck) t = B.CROWN_LED;
      else if (mechBand) t = B.LOUVER;
      else if (u % finEvery === 0) t = B.FIN;
      else if (rr === 0) t = B.SLAB;
      else {
        t = glass;
        const hh = hash3(Math.floor(u / finEvery) + 7 * (dx > 0) + 13 * (dz > 0), f, 5, plan.tower.seed);
        if (hh < 0.55) t |= LIT | (hash3(u, f, 7, plan.tower.seed) < 0.85 ? COOL : 0);
      }
      put(x, y, z, t);
    }
  }
  // mirante: só a borda de vidro sobe acima do piso do deck; o meio fica aberto até o topo (coroa)
  for (let dx = -R; dx <= R; dx++) for (let dz = -R; dz <= R; dz++) {
    if (!inside(dx, dz, deck)) continue;
    const shell = !inside(dx + 1, dz, deck) || !inside(dx - 1, dz, deck) || !inside(dx, dz + 1, deck) || !inside(dx, dz - 1, deck);
    for (let y = deck + 1; y <= top; y++) {
      if (shell) continue;
      // o miolo abre a céu aberto, com o heliponto no telhado da casa de máquinas no meio
      const [hx, hz] = half(deck);
      const core = Math.abs(dx + 0.5) <= hx - 4 && Math.abs(dz + 0.5) <= hz - 4;
      if (!core) w.force(cx + dx, y, cz + dz, 0);
      else if (y === top) {
        const hX = Math.abs(dx + 0.5) <= 2.5 && Math.abs(dz + 0.5) <= 3 && (Math.abs(dx + 0.5) >= 2 || Math.abs(dz + 0.5) <= 0.5);
        const ring = Math.abs(Math.hypot(dx + 0.5, dz + 0.5) - (Math.min(hx, hz) - 5)) < 0.6;
        w.force(cx + dx, y, cz + dz, hX || ring ? B.PARK_LINE : B.ROOF);
      } else w.force(cx + dx, y, cz + dz, Math.abs(dx + 0.5) >= hx - 4.5 || Math.abs(dz + 0.5) >= hz - 4.5 ? B.DECK_GLASS : B.WALL4);
    }
    // guarda-corpo de vidro em volta da borda do deck aberto
    if (!shell) {
      const [hx, hz] = half(deck);
      const core = Math.abs(dx + 0.5) <= hx - 4 && Math.abs(dz + 0.5) <= hz - 4;
      const nearShell = !inside(dx + 2, dz, deck) || !inside(dx - 2, dz, deck) || !inside(dx, dz + 2, deck) || !inside(dx, dz - 2, deck);
      if (!core && nearShell) w.force(cx + dx, deck + 1, cz + dz, B.DECK_GLASS);
    }
  }
  // tira a casca de vidro acima do deck: o mirante é aberto, só o guarda-corpo e a coroa de LED no topo da casa central
  for (let dx = -R; dx <= R; dx++) for (let dz = -R; dz <= R; dz++) {
    if (!inside(dx, dz, deck)) continue;
    const shell = !inside(dx + 1, dz, deck) || !inside(dx - 1, dz, deck) || !inside(dx, dz + 1, deck) || !inside(dx, dz - 1, deck);
    if (!shell) continue;
    for (let y = deck + 2; y <= top; y++) w.force(cx + dx, y, cz + dz, 0);
    w.force(cx + dx, deck + 1, cz + dz, B.DECK_GLASS);
  }
  // antena no topo da casa central, com luzes vermelhas, e luzes nos cantos do deck
  const mast = rng.int(14, 26);
  for (let y = top + 1; y <= top + mast; y++) w.force(cx, y, cz, y % 6 === 0 ? B.AVIATION : B.POLE);
  w.force(cx, top + mast + 1, cz, B.AVIATION);
  const [hx, hz] = half(deck);
  for (const [sx, sz] of [[1, 1], [1, -1], [-1, 1], [-1, -1]]) {
    // canto chanfrado: a luz vai no meio do chanfro
    const ex = Math.round(sx * (hx - (shape === 'steps' ? 1 : cham / 2)) - 0.5), ez = Math.round(sz * (hz - (shape === 'steps' ? 1 : cham / 2)) - 0.5);
    w.force(cx + ex, deck + 2, cz + ez, B.AVIATION);
  }
  return { x0: r.x0, z0: r.z0, x1: r.x1, z1: r.z1, top: top + mast + 1, cursed: false, floors: Math.floor((top - 5) / 4), screen: true, tower: true, cx, cz };
}

// ---------------------------------------------------------------- prédio

const GLYPH_ROWS = 3;
// "Letra" de placa: pixels acesos numa grade de 3 x 3 por caractere, sorteados por caractere (nenhuma placa repete).
function glyph(col, row, seed) {
  const ch = Math.floor(row / GLYPH_ROWS), cy = row % GLYPH_ROWS;
  if (cy === GLYPH_ROWS - 1) return false; // espaço entre caracteres
  return hash3(col, ch * 7 + cy, 3, seed) < 0.55;
}

function building(w, lot, seed) {
  const rng = Rng(lot.seed);
  const W = lot.x1 - lot.x0 + 1, D = lot.z1 - lot.z0 + 1;
  const frontLen = Math.min(lot.front.n || lot.front.s ? W : 99, lot.front.w || lot.front.e ? D : 99);
  const pencil = frontLen <= 10 && rng.next() < 0.75;
  let floors = lot.avenue ? rng.int(5, 16) : rng.int(2, 9);
  if (pencil) floors = rng.int(4, 10);
  if (lot.screen) floors = Math.max(floors, rng.int(8, 13));
  const style = pencil ? 'pencil' : ['glass', 'band', 'tile', 'band', 'tile'][rng.int(0, 4)];
  const wall = B.WALL0 + rng.int(0, 7);
  const glass = B.GLASS0 + rng.int(0, 3);
  const lit = rng.range(0.18, 0.6), cool = style === 'glass' ? 0.75 : rng.range(0.1, 0.5);
  const signColor = B.SIGN0 + rng.int(0, 7);
  const awning = rng.next() < 0.5 ? B.AWNING0 + rng.int(0, 3) : 0;
  const cursed = !lot.screen && rng.next() < 0.16;
  const H = 5 + floors * 4; // térreo de 5 + andares de 4
  const y0 = 1, top = y0 + H - 1;
  // recuo: prédio alto e largo afina 2 blocos dos últimos andares pra cima
  const setFloor = floors >= 9 && W >= 14 && D >= 14 && rng.next() < 0.55 ? rng.int(Math.floor(floors * 0.55), floors - 2) : -1;
  const setY = setFloor < 0 ? 1e9 : y0 + 5 + setFloor * 4;
  const info = { x0: lot.x0, z0: lot.z0, x1: lot.x1, z1: lot.z1, top, cursed, floors, screen: !!lot.screen };

  const sides = [
    { id: 'n', nx: 0, nz: -1 }, { id: 's', nx: 0, nz: 1 }, { id: 'w', nx: -1, nz: 0 }, { id: 'e', nx: 1, nz: 0 },
  ];
  const window = (sideIdx, bay, f) => {
    const hh = hash3(sideIdx * 97 + bay, f, 5, lot.seed);
    let t = style === 'glass' ? glass : B.WIN;
    if (cursed) {
      if (hash3(sideIdx * 97 + bay, f, 6, lot.seed) < 0.45) return 0; // quebrada
      return hh < 0.12 ? B.CURSE_GLOW : t;
    }
    if (hh < lit) t |= LIT | (hash3(sideIdx * 97 + bay, f, 7, lot.seed) < cool ? COOL : 0);
    return t;
  };
  const face = (sideIdx, side, u, len, yRel, front) => {
    const corner = u === 0 || u === len - 1;
    if (yRel < 5) {
      if (!front) return corner ? wall : (yRel === 4 ? wall : (pencil ? wall : wall));
      if (corner) return style === 'glass' ? B.CORNER : wall;
      if (yRel === 4) {
        if (cursed) return B.DEAD_SIGN;
        return glyph(u, 1, lot.seed + sideIdx) && u > 1 && u < len - 2 ? B.GLYPH : signColor;
      }
      if (cursed) {
        if (yRel === 1 && ((u + sideIdx) % 3) !== 0) return B.TAPE;
        return hash3(u, yRel, sideIdx, lot.seed) < 0.12 ? B.TALISMAN : B.BOARD;
      }
      const door = Math.abs(u - Math.floor(len / 2)) <= 0 || (len > 12 && Math.abs(u - Math.floor(len / 2)) === 1);
      if (door && yRel <= 2) return B.DOOR;
      if (yRel === 3) return awning ? wall : B.SHOP;
      return B.SHOP;
    }
    const yy = yRel - 5, f = yy >> 2, r = yy & 3;
    if (corner) return style === 'glass' ? B.CORNER : wall;
    if (style === 'glass') {
      if (r === 0) return B.SLAB;
      if (u % 3 === 0) return B.MULLION;
      return window(sideIdx, Math.floor(u / 3), f);
    }
    if (style === 'band') {
      if (r <= 1) return wall;
      if (u % 4 === 0) return B.MULLION;
      return window(sideIdx, Math.floor(u / 4), f);
    }
    if (style === 'tile') {
      const m = u % 4;
      if ((m === 1 || m === 2) && (r === 1 || r === 2)) return window(sideIdx, u >> 2, f);
      return wall;
    }
    // prédio-lápis: a frente é quase toda janela, os lados são parede cega
    if (!front) return wall;
    if (r === 0) return wall;
    return window(sideIdx, 0, f);
  };

  const put = (x, y, z, t) => w.force(x, y, z, t);
  for (let y = y0; y <= top; y++) {
    const inset = y >= setY ? 2 : 0;
    const bx0 = lot.x0 + inset, bx1 = lot.x1 - inset, bz0 = lot.z0 + inset, bz1 = lot.z1 - inset;
    const yRel = y - y0;
    sides.forEach((sd, si) => {
      const horiz = sd.nz !== 0, len = horiz ? bx1 - bx0 + 1 : bz1 - bz0 + 1;
      const front = !!lot.front[sd.id] && inset === 0;
      for (let u = 0; u < len; u++) {
        const x = horiz ? bx0 + u : (sd.id === 'w' ? bx0 : bx1);
        const z = horiz ? (sd.id === 'n' ? bz0 : bz1) : bz0 + u;
        const t = face(si, sd, u, len, yRel, front || (pencil && !!lot.front[sd.id]));
        put(x, y, z, t);
      }
    });
    // laje no fim de cada parte: telhado do bloco de baixo no recuo, telhado de cima no topo
    if (y === setY - 1 || y === top) {
      for (let x = lot.x0 + (y === top ? inset : 0); x <= lot.x1 - (y === top ? inset : 0); x++) {
        for (let z = lot.z0 + (y === top ? inset : 0); z <= lot.z1 - (y === top ? inset : 0); z++) {
          if (y === setY - 1 && x >= lot.x0 + 2 && x <= lot.x1 - 2 && z >= lot.z0 + 2 && z <= lot.z1 - 2) continue;
          const edge = x === lot.x0 + (y === top ? inset : 0) || x === lot.x1 - (y === top ? inset : 0)
            || z === lot.z0 + (y === top ? inset : 0) || z === lot.z1 - (y === top ? inset : 0);
          if (!edge || y === setY - 1) put(x, y, z, cursed && hash3(x, y, z, lot.seed) < 0.3 ? B.CURSE_STAIN : B.ROOF);
          if (y === setY - 1) {
            const e2 = x === lot.x0 || x === lot.x1 || z === lot.z0 || z === lot.z1;
            if (e2) put(x, y + 1, z, B.PARAPET);
          }
        }
      }
    }
    // pisos por dentro do prédio amaldiçoado (aparecem pelas janelas quebradas)
    if (cursed && yRel >= 5 && ((yRel - 5) & 3) === 0) {
      for (let x = bx0 + 1; x < bx1; x++) for (let z = bz0 + 1; z < bz1; z++) put(x, y, z, B.CURSE_FLOOR);
    }
  }
  // parapeito no topo
  const ti = top >= setY ? 2 : 0;
  for (let x = lot.x0 + ti; x <= lot.x1 - ti; x++) for (let z = lot.z0 + ti; z <= lot.z1 - ti; z++) {
    if (x === lot.x0 + ti || x === lot.x1 - ti || z === lot.z0 + ti || z === lot.z1 - ti) {
      put(x, top, z, style === 'glass' ? B.CORNER : wall);
      put(x, top + 1, z, B.PARAPET);
    }
  }

  // toldo: 1 bloco pra fora acima das vitrines
  if (awning && !cursed) {
    for (const sd of sides) {
      if (!lot.front[sd.id]) continue;
      const horiz = sd.nz !== 0, a0 = horiz ? lot.x0 : lot.z0, a1 = horiz ? lot.x1 : lot.z1;
      for (let a = a0 + 1; a < a1; a++) {
        const x = horiz ? a : (sd.id === 'w' ? lot.x0 - 1 : lot.x1 + 1), z = horiz ? (sd.id === 'n' ? lot.z0 - 1 : lot.z1 + 1) : a;
        put(x, y0 + 3, z, awning);
      }
    }
  }

  // telão no cruzamento grande
  if (lot.screen) {
    for (const sd of sides) {
      if (!lot.screen[sd.id]) continue;
      const horiz = sd.nz !== 0, len = horiz ? W : D;
      const sw = Math.min(16, len - 3), sh = Math.min(11, Math.floor(sw * 0.7));
      // encostado no canto do cruzamento
      const nearLow = horiz ? !!lot.screen.w : !!lot.screen.n;
      const u0 = nearLow ? 1 : len - 1 - sw;
      for (let u = u0; u < u0 + sw; u++) for (let y = y0 + 8; y < y0 + 8 + sh; y++) {
        const x = horiz ? lot.x0 + u : (sd.id === 'w' ? lot.x0 - 1 : lot.x1 + 1);
        const z = horiz ? (sd.id === 'n' ? lot.z0 - 1 : lot.z1 + 1) : lot.z0 + u;
        const rim = u === u0 || u === u0 + sw - 1 || y === y0 + 8 || y === y0 + 8 + sh - 1;
        put(x, y, z, rim ? B.SIGN_FRAME : B.SCREEN);
      }
    }
  }

  // placa vertical (tate-kanban): painel de 1 de espessura saindo perpendicular da fachada, com "letras"
  // (até duas: uma em cada ponta da fachada; Shibuya é coberta delas)
  const nSigns = cursed || floors < 3 ? 0 : pencil ? (rng.next() < 0.9 ? 1 : 0) : (rng.next() < 0.75 ? (rng.next() < 0.35 ? 2 : 1) : 0);
  const usedSign = new Set();
  for (let sg = 0; sg < nSigns; sg++) {
    const opts = sides.filter((sd) => lot.front[sd.id] && !(lot.screen && lot.screen[sd.id]));
    if (opts.length) {
      const sd = opts[rng.int(0, opts.length - 1)], horiz = sd.nz !== 0, len = horiz ? W : D;
      let u = rng.next() < 0.5 ? 1 : len - 2;
      if (usedSign.has(sd.id + u)) u = len - 1 - u;
      if (usedSign.has(sd.id + u)) continue;
      usedSign.add(sd.id + u);
      const out = rng.int(2, 3), hgt = Math.min(top - y0 - 7, rng.int(9, 24));
      const color = B.SIGN0 + rng.int(0, 7), ps = hashInt(lot.x0 + sg * 31, lot.z0, 17, seed);
      for (let k = 1; k <= out; k++) for (let y = y0 + 6; y < y0 + 6 + hgt; y++) {
        const x = horiz ? lot.x0 + u : (sd.id === 'w' ? lot.x0 - k : lot.x1 + k);
        const z = horiz ? (sd.id === 'n' ? lot.z0 - k : lot.z1 + k) : lot.z0 + u;
        const rim = k === out || y === y0 + 6 || y === y0 + 6 + hgt - 1;
        put(x, y, z, rim ? B.SIGN_FRAME : (glyph(k, y - y0 - 7, ps) ? B.GLYPH : color));
      }
    }
  }

  // telhado: casa de máquinas, ar-condicionado, caixa d'água, outdoor, antena com luz vermelha
  const rx0 = lot.x0 + ti + 1, rx1 = lot.x1 - ti - 1, rz0 = lot.z0 + ti + 1, rz1 = lot.z1 - ti - 1;
  if (rx1 - rx0 >= 3 && rz1 - rz0 >= 3) {
    const ry = top + 1;
    const free = (x, z) => w.get(x, ry, z) === 0;
    if (rx1 - rx0 >= 6 && rz1 - rz0 >= 6 && rng.next() < 0.6) {
      const mx = rng.int(rx0, rx1 - 3), mz = rng.int(rz0, rz1 - 3), mh = rng.int(2, 3);
      for (let x = mx; x < mx + 3; x++) for (let z = mz; z < mz + 3; z++) for (let y = ry; y < ry + mh; y++) put(x, y, z, wall);
    }
    const acs = Math.floor((rx1 - rx0) * (rz1 - rz0) / 18);
    for (let k = 0; k < acs; k++) {
      const x = rng.int(rx0, rx1), z = rng.int(rz0, rz1);
      if (free(x, z)) put(x, ry, z, rng.next() < 0.5 ? B.METAL : B.METAL_DARK);
    }
    if (rng.next() < 0.45) {
      const cx = rng.int(rx0 + 1, rx1 - 1), cz = rng.int(rz0 + 1, rz1 - 1), legs = rng.int(1, 2);
      for (const [dx, dz] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) for (let y = ry; y < ry + legs; y++) put(cx + dx, y, cz + dz, B.POLE);
      for (let y = ry + legs; y < ry + legs + 3; y++) for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) {
        if (Math.abs(dx) + Math.abs(dz) <= 1 || y < ry + legs + 2) put(cx + dx, y, cz + dz, B.TANK);
      }
    }
    if (floors >= 4 && !lot.screen && rng.next() < (lot.avenue ? 0.55 : 0.25)) {
      // outdoor virado pra frente de avenida
      const sd = sides.find((s) => lot.front[s.id]) || sides[0], horiz = sd.nz !== 0;
      const len = horiz ? rx1 - rx0 + 1 : rz1 - rz0 + 1, bw = Math.min(len, rng.int(9, 14)), bh = rng.int(4, 6);
      const u0 = Math.floor((len - bw) / 2), color = B.SIGN0 + rng.int(0, 7), ps = hashInt(lot.x0, lot.z0, 19, seed);
      for (let u = 0; u < bw; u++) {
        const x = horiz ? rx0 + u0 + u : (sd.id === 'w' ? rx0 : rx1);
        const z = horiz ? (sd.id === 'n' ? rz0 : rz1) : rz0 + u0 + u;
        for (let y = ry; y < ry + 2; y++) if (u % 4 === 0 || u === bw - 1) put(x, y, z, B.METAL_DARK);
        for (let y = ry + 2; y < ry + 2 + bh; y++) {
          const rim = u === 0 || u === bw - 1 || y === ry + 2 || y === ry + 1 + bh;
          put(x, y, z, cursed ? B.DEAD_SIGN : rim ? B.SIGN_FRAME : (glyph(u, y - ry - 3, ps) ? B.GLYPH : color));
        }
      }
    }
    if (floors >= 7 && rng.next() < 0.5) {
      const x = rng.int(rx0, rx1), z = rng.int(rz0, rz1), hh = rng.int(5, 11);
      if (free(x, z)) { for (let y = ry; y < ry + hh; y++) put(x, y, z, B.POLE); put(x, ry + hh, z, B.AVIATION); }
    }
  }

  // maldição: manchas pretas escorrendo, veias roxas, mancha no pé do prédio
  if (cursed) {
    for (let y = y0; y <= top + 1; y++) {
      for (let x = lot.x0 - 1; x <= lot.x1 + 1; x++) for (let z = lot.z0 - 1; z <= lot.z1 + 1; z++) {
        const t = w.get(x, y, z);
        if (!t || base(t) === B.CURSE_FLOOR) continue;
        const onFace = x <= lot.x0 + 2 || x >= lot.x1 - 2 || z <= lot.z0 + 2 || z >= lot.z1 - 2;
        if (!onFace) continue;
        const col = hash3(x, 0, z, lot.seed + 31);
        const dripTop = y0 + 5 + Math.floor(hash3(x, 1, z, lot.seed + 31) * (top - y0));
        const dripLen = 4 + Math.floor(hash3(x, 2, z, lot.seed + 31) * 16);
        let s = col < 0.24 && y <= dripTop && y >= dripTop - dripLen;
        if (y < y0 + 3 && hash3(x, y, z, lot.seed + 33) < 0.55) s = true;
        const v = valueNoise3(x, y, z, lot.seed + 77, 7);
        if (Math.abs(v - 0.5) < 0.022) put(x, y, z, B.CURSE_VEIN);
        else if (s) put(x, y, z, B.CURSE_STAIN);
      }
    }
  }

  // máquinas de bebida encostadas na fachada, na calçada
  if (!cursed && rng.next() < 0.4) {
    const sd = sides.find((s) => lot.front[s.id] && !lot.alley[s.id]);
    if (sd) {
      const horiz = sd.nz !== 0, len = horiz ? W : D, n = rng.int(1, 3), u0 = rng.int(2, Math.max(2, len - 2 - n));
      for (let k = 0; k < n; k++) {
        const u = u0 + k;
        if (u >= len - 1) break;
        const x = horiz ? lot.x0 + u : (sd.id === 'w' ? lot.x0 - 1 : lot.x1 + 1);
        const z = horiz ? (sd.id === 'n' ? lot.z0 - 1 : lot.z1 + 1) : lot.z0 + u;
        if (w.get(x, 0, z) === 0 || w.get(x, 1, z) !== 0) continue;
        put(x, 1, z, B.VEND0 + rng.int(0, 3));
        put(x, 2, z, B.VENDING);
      }
    }
  }
  return info;
}

// Santuário de bairro: chão de cascalho, torii vermelho na frente, casinha de madeira no fundo e duas lanternas.
function shrine(w, lot, seed) {
  const rng = Rng(lot.seed);
  for (let x = lot.x0; x <= lot.x1; x++) for (let z = lot.z0; z <= lot.z1; z++) w.force(x, 0, z, B.GRAVEL);
  const sides = ['n', 's', 'w', 'e'].filter((s) => lot.front[s]);
  const sd = sides.length ? sides[rng.int(0, sides.length - 1)] : 'n';
  const horiz = sd === 'n' || sd === 's';
  const mid = horiz ? Math.floor((lot.x0 + lot.x1) / 2) : Math.floor((lot.z0 + lot.z1) / 2);
  const front = sd === 'n' ? lot.z0 + 1 : sd === 's' ? lot.z1 - 1 : sd === 'w' ? lot.x0 + 1 : lot.x1 - 1;
  const back = sd === 'n' ? lot.z1 - 1 : sd === 's' ? lot.z0 + 1 : sd === 'w' ? lot.x1 - 1 : lot.x0 + 1;
  const P = (a, b) => (horiz ? [a, b] : [b, a]); // a ao longo da frente, b do fundo pra frente
  const th = rng.int(5, 6);
  for (const a of [mid - 2, mid + 2]) for (let y = 1; y <= th; y++) { const [x, z] = P(a, front); w.force(x, y, z, B.TORII); }
  for (let a = mid - 3; a <= mid + 3; a++) { const [x, z] = P(a, front); w.force(x, th + 1, z, B.TORII_TOP); if (Math.abs(a - mid) <= 2) w.force(x, th - 1, z, B.TORII); }
  const dir = back > front ? 1 : -1;
  for (let a = mid - 2; a <= mid + 2; a++) for (let b = 0; b < 3; b++) for (let y = 1; y <= 3; y++) {
    const [x, z] = P(a, back - dir * b);
    w.force(x, y, z, (a === mid && b === 2 && y <= 2) ? B.DOOR : B.SHRINE_WOOD);
  }
  for (let a = mid - 3; a <= mid + 3; a++) for (let b = -1; b < 4; b++) {
    const [x, z] = P(a, back - dir * b);
    w.force(x, 4, z, B.SHRINE_ROOF);
    if (Math.abs(a - mid) <= 1 && b >= 0 && b <= 2) w.force(x, 5, z, B.SHRINE_ROOF);
  }
  for (const a of [mid - 3, mid + 3]) { const [x, z] = P(a, front + dir * 2); w.force(x, 1, z, B.LANTERN); w.force(x, 2, z, B.LANTERN); }
}

// Estacionamento: asfalto com as vagas pintadas e a placa amarela no poste.
function parking(w, lot) {
  for (let x = lot.x0; x <= lot.x1; x++) for (let z = lot.z0; z <= lot.z1; z++) {
    const line = (lot.x1 - lot.x0 >= lot.z1 - lot.z0) ? ((x - lot.x0) % 3 === 0 && (z === lot.z0 + 1 || z === lot.z1 - 1 || z === lot.z0 + 2 || z === lot.z1 - 2))
      : ((z - lot.z0) % 3 === 0 && (x === lot.x0 + 1 || x === lot.x1 - 1 || x === lot.x0 + 2 || x === lot.x1 - 2));
    w.force(x, 0, z, line ? B.PARK_LINE : B.ASPHALT);
  }
  for (let y = 1; y <= 4; y++) w.force(lot.x0, y, lot.z0, B.POLE);
  w.force(lot.x0, 5, lot.z0, B.SIGN2);
}

// ---------------------------------------------------------------- metrô

// Estação sob a avenida 'x' 0, em volta de x = 0: duas entradas de escada nas calçadas, saguão com catracas,
// plataforma central (ilha) com escadas, trilhos dos dois lados com trem parado num deles, e túneis seguindo a
// avenida; o túnel do lado oeste está tomado pelas maldições (sem luz, manchas e veias nas paredes).
const METRO = {
  floorC: -8, ceilC: -3, floorP: -16, ceilP: -9, bed: -17,
  cz0: -11, cz1: 10, cx0: -38, cx1: 38, // saguão (z em relação a c)
  px0: -52, px1: 52, // plataforma
  wall: 8, track: [5, 7], island: 4, // |z - c| das paredes, trilhos e plataforma (c = centro da avenida, entre -1 e 0)
};
function metro(w, seed, half) {
  const c = street('x', 0, seed).c;
  // centro da avenida fica entre c - 1 e c; zz = deslocamento simétrico (-.5 .. +.5 nas duas fileiras do meio)
  const zz = (z) => z - c + 0.5;
  const M = METRO;
  for (let x = -half; x <= half; x++) for (let z = c - 15; z <= c + 14; z++) for (let y = -26; y <= -1; y++) w.force(x, y, z, B.STONE);
  const carve = (x, y, z) => w.force(x, y, z, 0);
  // saguão
  for (let x = M.cx0; x <= M.cx1; x++) for (let z = c + M.cz0; z <= c + M.cz1; z++) for (let y = M.floorC + 1; y < M.ceilC; y++) carve(x, y, z);
  // plataforma, trilhos e túneis (a avenida inteira do pedaço)
  for (let x = -half; x <= half; x++) for (let z = c - M.wall + 1; z <= c + M.wall - 2; z++) {
    const a = Math.abs(zz(z));
    const platform = x >= M.px0 && x <= M.px1;
    const top = platform && a <= M.island ? M.floorP : M.bed;
    for (let y = top + 1; y < M.ceilP; y++) carve(x, y, z);
  }
  // escadas da plataforma: do saguão (x = ±4) descendo pra fora até a plataforma (x = ±12)
  const stairP = (sgn) => {
    for (let k = 0; k < 8; k++) {
      const x = sgn * (4 + k), topY = M.floorC - 1 - k;
      for (let z = c - 1; z <= c; z++) {
        for (let y = M.floorP; y <= topY; y++) w.force(x, y, z, B.STAIR);
        for (let y = topY + 1; y < M.ceilC; y++) carve(x, y, z);
      }
    }
  };
  stairP(-1); stairP(1);
  // entradas na calçada: aberturas de 3 nas fileiras de dentro, descendo 8 degraus até o saguão
  const entrance = (zs, sgn) => {
    for (let k = 0; k < 8; k++) {
      const x = sgn * (29 - k), topY = -1 - k;
      for (const z of zs) {
        for (let y = M.floorC; y <= topY; y++) w.force(x, y, z, B.STAIR);
        for (let y = topY + 1; y <= 0; y++) carve(x, y, z);
      }
    }
  };
  const south = [c + 8, c + 9, c + 10], north = [c - 11, c - 10, c - 9];
  entrance(south, 1); entrance(north, -1);

  // revestimento: tudo que é pedra encostada no vazio vira azulejo, piso, teto ou concreto de túnel
  const lining = [];
  for (let x = -half; x <= half; x++) for (let z = c - 15; z <= c + 14; z++) for (let y = -21; y <= 0; y++) {
    if (w.get(x, y, z) !== 0) continue;
    if (y === 0 && !(south.includes(z) || north.includes(z))) continue;
    for (const [dx, dy, dz] of [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]]) {
      const nx = x + dx, ny = y + dy, nz = z + dz;
      if (ny > -1 || w.get(nx, ny, nz) !== B.STONE) continue;
      lining.push([nx, ny, nz, dy]);
    }
  }
  for (const [x, y, z, dy] of lining) {
    const station = x >= M.px0 && x <= M.px1;
    const concourse = y >= M.floorC && y <= M.ceilC && station;
    let t;
    if (!station) t = dy === -1 ? B.BALLAST : B.TUNNEL;
    else if (dy === 1) t = B.CEIL;
    else if (dy === -1) t = B.TILE_FLOOR;
    else if (concourse) t = y === -6 ? B.TILE_LINE : B.TILE_WALL;
    else t = y === -11 ? B.TILE_LINE : B.TILE_WALL;
    w.force(x, y, z, t);
  }
  // borda da plataforma (concreto claro) e piso tátil amarelo logo depois
  for (let x = M.px0; x <= M.px1; x++) for (let z = c - M.island; z <= c + M.island - 1; z++) {
    const a = Math.abs(zz(z));
    if (w.get(x, M.floorP, z) === B.STAIR) continue;
    if (a > M.island - 1) w.force(x, M.floorP, z, B.EDGE);
    else if (a > M.island - 2) w.force(x, M.floorP, z, B.TACTILE);
  }
  // trilhos
  for (let x = -half; x <= half; x++) for (const s of [-1, 1]) for (const d of [M.track[0], M.track[1]]) {
    const z = s < 0 ? c - d : c + d - 1;
    w.force(x, M.bed, z, B.BALLAST);
    w.force(x, M.bed + 1, z, B.RAIL);
  }
  // luzes no teto: faixas no saguão e sobre a plataforma
  for (let x = M.cx0; x <= M.cx1; x++) for (const z of [c - 6, c + 5]) if ((((x % 7) + 7) % 7) < 4 && w.get(x, M.ceilC, z) === B.CEIL) w.force(x, M.ceilC, z, B.CEIL_LIGHT);
  for (let x = M.px0; x <= M.px1; x++) for (const z of [c - 3, c + 2]) if ((((x % 6) + 6) % 6) < 4 && w.get(x, M.ceilP, z) === B.CEIL) w.force(x, M.ceilP, z, B.CEIL_LIGHT);
  // pilares: plataforma (no meio, fora das escadas) e saguão
  for (let x = M.px0 + 4; x <= M.px1 - 4; x += 8) {
    if (Math.abs(x) >= 3 && Math.abs(x) <= 13) continue;
    for (let y = M.floorP + 1; y < M.ceilP; y++) for (const z of [c - 1, c]) w.force(x, y, z, B.PILLAR);
    // banco entre um pilar e o próximo
    for (const z of [c - 1, c]) if (Math.abs(x + 4) > 13 && x + 4 <= M.px1 - 2) w.force(x + 4, M.floorP + 1, z, B.BENCH);
  }
  for (let x = M.cx0 + 4; x <= M.cx1 - 4; x += 8) for (const z of [c - 5, c + 4]) {
    if (Math.abs(x) >= 3 && Math.abs(x) <= 13 && Math.abs(zz(z)) < 3) continue;
    for (let y = M.floorC + 1; y < M.ceilC; y++) w.force(x, y, z, B.PILLAR);
  }
  // catracas em x = ±16 (blocos alternados com passagem)
  for (const x of [-16, 16]) for (let z = c + M.cz0; z <= c + M.cz1; z++) {
    if (w.get(x, M.floorC + 1, z) !== 0) continue;
    const a = Math.floor(Math.abs(zz(z)));
    if (a >= 8) { for (let y = M.floorC + 1; y < M.ceilC; y++) w.force(x, y, z, B.TILE_WALL); continue; }
    if ((a & 1) === 0) w.force(x, M.floorC + 1, z, B.GATE);
  }
  // guarda-corpo em volta das aberturas das escadas no saguão
  for (const sgn of [-1, 1]) {
    for (let k = 0; k < 8; k++) for (const z of [c - 2, c + 1]) w.force(sgn * (4 + k), M.floorC + 1, z, B.RAILING);
    for (let z = c - 1; z <= c; z++) w.force(sgn * 12, M.floorC + 1, z, B.RAILING);
  }
  // placas: nome da estação nas paredes dos trilhos (branca com letras escuras) e placas penduradas no saguão
  for (let x = M.px0 + 6; x <= M.px1 - 12; x += 22) for (const s of [-1, 1]) {
    const z = s < 0 ? c - M.wall : c + M.wall - 1, ps = hashInt(x, s, 23, seed);
    for (let u = 0; u < 7; u++) for (let y = -14; y <= -13; y++) w.force(x + u, y, z, glyph(u, (y + 14) + 3, ps) && u > 0 && u < 6 ? B.SIGN_DARK : B.METRO_SIGN);
  }
  for (let x = M.cx0 + 8; x <= M.cx1 - 8; x += 12) for (let z = c - 2; z <= c + 1; z++) {
    if (Math.abs(x) <= 13) continue;
    w.force(x, M.ceilC - 1, z, B.METRO_SIGN);
  }
  // trem parado no trilho norte: 3 carros de 20 com 1 de folga
  const tz = [c - M.track[1], c - M.track[1] + 1, c - M.track[0]];
  for (let car = 0; car < 3; car++) {
    const x0 = -48 + car * 21;
    for (let k = 0; k < 20; k++) {
      const x = x0 + k, end = k === 0 || k === 19, door = [3, 4, 9, 10, 15, 16].includes(k);
      for (const z of tz) {
        const outer = z !== tz[1];
        w.force(x, M.bed + 1, z, B.TRAIN_UNDER);
        for (let y = M.bed + 2; y <= M.bed + 5; y++) {
          let t = B.TRAIN;
          if (!outer && !end) t = B.TRAIN; // o miolo não aparece
          else if (end) t = car === 0 && k === 0 ? ((y === M.bed + 4 || y === M.bed + 3) && z === tz[1] ? B.TRAIN_FRONT : y === M.bed + 2 && z !== tz[1] ? B.HEADLIGHT : B.TRAIN_END) : B.TRAIN_END;
          else if (y === M.bed + 2) t = door ? B.TRAIN_DOOR : B.TRAIN_STRIPE;
          else if (y === M.bed + 3 || y === M.bed + 4) t = door ? (y === M.bed + 4 ? B.TRAIN_WIN : B.TRAIN_DOOR) : B.TRAIN_WIN;
          else t = B.TRAIN;
          w.force(x, y, z, t);
        }
        w.force(x, M.bed + 6, z, B.TRAIN_ROOF);
      }
    }
  }
  // luzes do túnel leste; o oeste (x < px0) é das maldições
  for (let x = -half; x <= half; x++) {
    if (x >= M.px0 && x <= M.px1) continue;
    for (const z of [c - M.wall, c + M.wall - 1]) {
      if (w.get(x, -12, z) !== B.TUNNEL) continue;
      if (x > M.px1) { if (((x % 12) + 12) % 12 === 0) w.force(x, -12, z, B.TUNNEL_LIGHT); continue; }
    }
    if (x < M.px0) {
      for (let z = c - M.wall; z <= c + M.wall - 1; z++) for (let y = M.bed; y <= M.ceilP; y++) {
        const t = w.get(x, y, z);
        if (t !== B.TUNNEL && t !== B.BALLAST && t !== B.CEIL) continue;
        const deep = Math.min(1, (M.px0 - x) / 30);
        const v = valueNoise3(x, y, z, seed + 91, 6);
        if (Math.abs(v - 0.5) < 0.03 * deep) w.force(x, y, z, B.CURSE_VEIN);
        else if (hash3(x, y, z, seed + 92) < 0.55 * deep && valueNoise3(x, y, z, seed + 93, 9) > 0.45) w.force(x, y, z, B.CURSE_STAIN);
      }
    }
  }
  return { c, south, north };
}

// ---------------------------------------------------------------- ruas: postes, árvores, grade, fiação, semáforos

function furniture(w, seed, half) {
  const put = (x, y, z, t) => { if (w.get(x, y, z) === 0) w.force(x, y, z, t); };
  const lo = Math.floor((-half - 20) / CELL), hi = Math.ceil((half + 20) / CELL);
  // P(eixo, ao longo, através): coordenadas (x, z)
  const P = (axis, a, b) => (axis === 'x' ? [a, b] : [b, a]);
  for (const axis of ['x', 'z']) {
    const other = axis === 'x' ? 'z' : 'x';
    for (let i = lo; i <= hi; i++) {
      const s = street(axis, i, seed);
      const off = Math.floor(hash3(i, axis === 'x' ? 1 : 2, 40, seed) * 16);
      const curbs = [{ b: s.c + s.r1 + 1, out: 1 }, { b: s.c + s.r0 - 1, out: -1 }];
      for (const cb of curbs) {
        let lastPole = null;
        for (let a = -half; a <= half; a++) {
          const [x, z] = P(axis, a, cb.b);
          if (w.get(x, 0, z) !== B.CURB) continue;
          // longe de cruzamento
          const near = [-3, 3].some((d) => streetAt(other, a + d, seed)) || streetAt(other, a, seed);
          const m = (((a + off) % 16) + 16) % 16;
          if (s.avenue) {
            if (near) continue;
            if (m === 0) {
              // poste de luz com braço sobre a pista
              for (let y = 1; y <= 7; y++) put(x, y, z, B.POLE);
              const [ax, az] = P(axis, a, cb.b - cb.out), [lx, lz] = P(axis, a, cb.b - cb.out * 2);
              put(ax, 7, az, B.POLE); put(lx, 7, lz, B.LAMP);
            } else if (m === 8) {
              // árvore no canteiro: copa sorteada, nenhuma igual
              w.force(x, 0, z, B.PLANTER);
              const th = 2 + Math.floor(hash3(a, i, 41, seed) * 2);
              for (let y = 1; y <= th; y++) put(x, y, z, B.TRUNK);
              const rr = 2.1 + hash3(a, i, 42, seed) * 0.9, cy = th + 2;
              for (let dx = -3; dx <= 3; dx++) for (let dy = -2; dy <= 2; dy++) for (let dz = -3; dz <= 3; dz++) {
                const d = Math.sqrt(dx * dx + (dy * 1.4) ** 2 + dz * dz) + (hash3(x + dx, cy + dy, z + dz, seed + 43) - 0.5) * 1.1;
                if (d <= rr) put(x + dx, cy + dy, z + dz, hash3(x + dx, cy + dy, z + dz, seed + 44) < 0.3 ? B.LEAVES2 : B.LEAVES);
              }
            } else if (m !== 7 && m !== 9 && m !== 15 && m !== 1) put(x, 1, z, B.RAILING);
          } else if (cb.out < 0) {
            // rua estreita: poste de concreto com fiação de um lado só, a cada 20
            const m2 = (((a + off) % 20) + 20) % 20;
            if (m2 !== 0 || near) continue;
            for (let y = 1; y <= 9; y++) put(x, y, z, B.POLE);
            const [c1x, c1z] = P(axis, a, cb.b - 1), [c2x, c2z] = P(axis, a, cb.b + 1);
            put(c1x, 8, c1z, B.METAL_DARK); put(c2x, 8, c2z, B.METAL_DARK);
            const [tx, tz] = P(axis, a, cb.b + 1); put(tx, 6, tz, B.METAL);
            const [lx, lz] = P(axis, a, cb.b - 1); put(lx, 5, lz, B.LAMP);
            if (lastPole !== null && a - lastPole <= 24) {
              for (let k = lastPole + 1; k < a; k++) {
                const t = (k - lastPole) / (a - lastPole), sag = Math.round(Math.sin(Math.PI * t) * 1.3);
                for (const bb of [cb.b - 1, cb.b + 1]) { const [cx, cz] = P(axis, k, bb); put(cx, 8 - sag, cz, B.CABLE); }
              }
            }
            lastPole = a;
          }
        }
      }
    }
  }
  // semáforos: um poste em cada canto do cruzamento, braço por cima da pista
  for (let i = lo; i <= hi; i++) for (let j = lo; j <= hi; j++) {
    const X = street('x', i, seed), Z = street('z', j, seed);
    for (const [sx, sz] of [[1, 1], [-1, 1], [1, -1], [-1, -1]]) {
      const x = sx > 0 ? Z.c + Z.r1 + 1 : Z.c + Z.r0 - 1, z = sz > 0 ? X.c + X.r1 + 1 : X.c + X.r0 - 1;
      if (Math.abs(x) > half || Math.abs(z) > half) continue;
      for (let y = 1; y <= 6; y++) put(x, y, z, B.POLE);
      // braço sobre a rua 'x' (vai no sentido -sz)
      for (let k = 1; k <= 3; k++) put(x, 6, z - sz * k, B.POLE);
      put(x, 5, z - sz * 3, B.TRAFFIC_BOX); put(x - sx, 5, z - sz * 3, (i + j) & 1 ? B.TRAFFIC_R : B.TRAFFIC_G);
    }
  }
}

// ---------------------------------------------------------------- pedaço (só a prévia)

function shibuyaPiece(seed, half) {
  const w = new Vox(-half, half - 1);
  for (let x = -half; x < half; x++) for (let z = -half; z < half; z++) {
    const g = groundAt(x, z, seed);
    w.force(x, 0, z, g === null ? B.LOT : g);
    w.force(x, -1, z, B.SOIL);
    w.force(x, -2, z, B.STONE);
  }
  const lo = Math.floor((-half - 20) / CELL), hi = Math.ceil((half + 20) / CELL);
  const buildings = [], blocks = [];
  for (let i = lo; i < hi; i++) for (let j = lo; j < hi; j++) {
    const plan = blockPlan(i, j, seed);
    const r = plan.rect;
    if (r.x1 < -half || r.x0 >= half || r.z1 < -half || r.z0 >= half) continue;
    blocks.push(plan);
    for (const a of plan.alleys) for (let x = a.x0; x <= a.x1; x++) for (let z = a.z0; z <= a.z1; z++) w.force(x, 0, z, B.ALLEY);
  }
  const st = metro(w, seed, half);
  for (const plan of blocks) if (plan.tower) buildings.push(skyscraper(w, plan, seed));
  for (const plan of blocks) for (const lot of plan.lots) {
    if (lot.kind === 'shrine') shrine(w, lot, seed);
    else if (lot.kind === 'parking') parking(w, lot);
    else buildings.push(building(w, lot, seed));
  }
  furniture(w, seed, half);
  // caminhos dos pedestres (só a prévia): a fileira da calçada logo depois do meio-fio, dos dois lados de cada rua
  const walks = [];
  for (const axis of ['x', 'z']) for (let i = lo; i <= hi; i++) {
    const s = street(axis, i, seed);
    for (const b of [s.c + s.r1 + 2, s.c + s.r0 - 2]) if (Math.abs(b) < half - 2) walks.push({ axis, b });
  }
  const X0 = street('x', 0, seed), Z0 = street('z', 0, seed);
  return { w, buildings, walks, station: st, cross: { x: Z0.c, z: X0.c, X: X0, Z: Z0 } };
}

if (typeof module !== 'undefined') module.exports = { towerBlock, skyscraper, B, NAMES, LIT, COOL, base, Rng, hash3, hashInt, Vox, valueNoise3, CELL, street, streetAt, scramble, groundAt, blockPlan, glyph, building, shrine, parking, METRO, metro, furniture, shibuyaPiece };
