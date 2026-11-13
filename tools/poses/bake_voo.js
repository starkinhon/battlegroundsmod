// Bakes the combat flight's moves (voo.js) into keyframe animations the mod reads, with the sword moves' rig:
//   cd tools/poses && npm i three@0.160.0 && node bake_voo.js
// writes src/main/resources/assets/battlegroundsmod/animations/voo.<move>.json.
const fs = require('fs');
const path = require('path');
const Rig = require('../espada/rig.js');
const A = require('./voo.js');
const out = path.join(__dirname, '../../src/main/resources/assets/battlegroundsmod/animations');
for (const name of Object.keys(A)) {
  fs.writeFileSync(path.join(out, 'voo.' + name + '.json'), JSON.stringify(Rig.bake(A[name], 0.5)));
  console.log('voo.' + name);
}
