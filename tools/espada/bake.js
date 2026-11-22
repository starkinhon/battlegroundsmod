// Bakes the sword moves (anims.js) into keyframe animations the mod reads:
//   cd tools/espada && npm i three@0.160.0 && node bake.js
// writes src/main/resources/assets/battlegroundsmod/animations/espada.<move>.json. rig.js has the same transforms
// the game uses (arm, held item, Aizen's sword), so the blade ends up where anims.js says it points.
// "segueMira": the head follows the aim on top of the move (KeyframeAnimation), instead of a baked head turn.
const fs = require('fs');
const path = require('path');
const Rig = require('./rig.js');
const A = require('./anims.js');
const out = path.join(__dirname, '../../src/main/resources/assets/battlegroundsmod/animations');
for (const name of Object.keys(A)) {
  fs.writeFileSync(path.join(out, 'espada.' + name + '.json'), JSON.stringify({ ...Rig.bake(A[name], 0.5), segueMira: true }));
  console.log('espada.' + name);
}
