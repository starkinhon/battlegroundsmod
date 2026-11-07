// Bakes the fist moves (anims.js) into keyframe animations the mod reads, with the sword moves' rig:
//   cd tools/poses && npm i three@0.160.0 && node bake.js
// writes src/main/resources/assets/battlegroundsmod/animations/soco.<move>.json.
const fs = require('fs');
const path = require('path');
const Rig = require('../espada/rig.js');
const A = require('./anims.js');
const out = path.join(__dirname, '../../src/main/resources/assets/battlegroundsmod/animations');
for (const name of Object.keys(A)) {
  fs.writeFileSync(path.join(out, 'soco.' + name + '.json'), JSON.stringify(Rig.bake(A[name], 0.5)));
  console.log('soco.' + name);
}
