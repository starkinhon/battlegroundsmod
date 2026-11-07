// Bakes the skill poses (skills.js) into keyframe animations the mod reads, with the sword moves' rig:
//   cd tools/poses && npm i three@0.160.0 && node bake_skills.js
// writes src/main/resources/assets/battlegroundsmod/animations/<skill>.json ("segueMira": the head keeps following
// the aim; "soltar": where a held skill waits for the key to be let go). Never writes madara.katon or gojo.dominio
// (Stark's own, from his editor).
const fs = require('fs');
const path = require('path');
const Rig = require('../espada/rig.js');
const S = require('./skills.js');
const out = path.join(__dirname, '../../src/main/resources/assets/battlegroundsmod/animations');
const STARK = new Set(['madara.katon', 'gojo.dominio']);
for (const name of Object.keys(S)) {
  if (STARK.has(name)) throw new Error(name + ' is Stark\'s own animation');
  const json = Rig.bake(S[name].keys, 0.5);
  json.segueMira = true;
  if (S[name].soltar != null) json.soltar = +(S[name].soltar / 20).toFixed(4);
  fs.writeFileSync(path.join(out, name + '.json'), JSON.stringify(json));
  console.log(name, json.length, json.soltar != null ? 'soltar ' + json.soltar : '');
}
