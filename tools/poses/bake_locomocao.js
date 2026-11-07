// Bakes the standing / walking / running poses (locomocao.js) into keyframe animations the mod reads:
//   cd tools/poses && npm i three@0.160.0 && node bake_locomocao.js
// writes src/main/resources/assets/battlegroundsmod/animations/pose.<character>.<parado|andar|correr>.json.
// client/StancePoses plays them as loops: parado on the clock, andar and correr on the distance walked.
// Characters marked vanilla (Gojo) are not baked: they keep Minecraft's own walk.
const fs = require('fs');
const path = require('path');
const Rig = require('../espada/rig.js');
const L = require('./locomocao.js');
const out = path.join(__dirname, '../../src/main/resources/assets/battlegroundsmod/animations');
for (const key of Object.keys(L)) {
  if (L[key].vanilla) continue;
  for (const state of ['parado', 'andar', 'correr']) {
    const keys = L[key][state].keys;
    // the slow breathing of parado needs fewer frames
    const json = Rig.bake(keys, state === 'parado' ? 2 : 0.5);
    // the loop's own length in ticks at normal speed (the last key is the first one again)
    json.length = +(keys[keys.length - 1].t / 20).toFixed(4);
    fs.writeFileSync(path.join(out, 'pose.' + key + '.' + state + '.json'), JSON.stringify(json));
    console.log('pose.' + key + '.' + state, json.length);
  }
}
