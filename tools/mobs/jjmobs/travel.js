// Gera animal/jjmob/JjTravel.java: o caminho do corpo (x, z do '@') de cada golpe que desloca o mob na prévia,
// um valor por tick, relativo ao começo do golpe. O servidor anda o mob por esse caminho (o cliente tira x/z da
// animação assada), então no jogo o mob vai exatamente aonde ia na prévia. Golpes que somem (hide) teleportam e não
// entram aqui. Uso: node travel.js (depois do export.js)
const fs = require('fs'), path = require('path');
const M = path.join(__dirname, '..', '..', '..', 'src/main/resources/assets/battlegroundsmod/blocky/jj');
const OUT = path.join(__dirname, '..', '..', '..', 'src/main/java/com/starkinhon/battlegroundsmod/animal/jjmob/JjTravel.java');
const SKIP = new Set([]);
const rows = [];
for (const f of fs.readdirSync(M).filter(f => f.endsWith('.anim.json') && !f.includes('_low')).sort()) {
  const id = f.replace('.anim.json', '');
  const a = JSON.parse(fs.readFileSync(path.join(M, f)));
  for (const [md, d] of Object.entries(a.modes)) {
    if (md === 'parado' || md === 'andar' || md === 'morte' || id === 'jj_kogane' || d.ch['@.hide'] || SKIP.has(id + '/' + md)) continue;
    const x = d.ch['@.x'] || new Array(d.frames).fill(0), z = d.ch['@.z'] || new Array(d.frames).fill(0);
    let far = 0;
    for (let i = 0; i < d.frames; i++) far = Math.max(far, Math.hypot(x[i] - x[0], z[i] - z[0]));
    if (far < 0.3) continue;
    const r = v => (Math.round(v * 100) / 100).toString().replace(/^(-?)0\./, '$10.') + 'f';
    rows.push(`        TABLE.put("${id}/${md}", new float[][] {\n                {${x.map(v => r(v - x[0])).join(', ')}},\n                {${z.map(v => r(v - z[0])).join(', ')}}});`);
  }
}
fs.writeFileSync(OUT, `package com.starkinhon.battlegroundsmod.animal.jjmob;

import java.util.HashMap;
import java.util.Map;

// GERADO por tools/mobs/jjmobs/travel.js a partir das animações assadas da prévia: o caminho do corpo (x, z em blocos,
// frente = -z) de cada golpe que desloca o mob, um valor por tick a partir do começo do golpe. Não editar à mão.
public final class JjTravel {
    private static final Map<String, float[][]> TABLE = new HashMap<>();

    static {
${rows.join('\n')}
    }

    private JjTravel() {
    }

    // [0] = x, [1] = z, or null when this blow does not move the body
    public static float[][] of(String id, String mode) {
        return TABLE.get(id + "/" + mode);
    }
}
`);
console.log('golpes com deslocamento:', rows.length);
