// Bakes the skill poses (skills.js) into keyframe animations the mod reads, with the sword moves' rig:
//   cd tools/poses && npm i three@0.160.0 && node bake_skills.js
// writes src/main/resources/assets/battlegroundsmod/animations/<skill>.json ("segueMira": the head keeps following
// the aim; "soltar": where a held skill waits for the key to be let go). Never writes madara.katon or gojo.dominio
// (Stark's own, from his editor).
//
// To go back:
// - To the OLD pose (Forja, before PR #159): add the skill to KEEP below, delete its .json and rebake. With no .json,
//   the mod falls back to the code pose. (bills.hakai and bills.destruction_energy are already like this.)
// - To the previous version of a pose: earlier versions are kept in antigas/ (skills_arremesso.js = the throw/lunge
//   version, before Stark asked for each character's own style). Copy the entry from there into skills.js and rebake.
//   Every version is also in git history (git log -- tools/poses/skills.js).
const fs = require('fs');
const path = require('path');
const Rig = require('../espada/rig.js');
const S = require('./skills.js');
const out = path.join(__dirname, '../../src/main/resources/assets/battlegroundsmod/animations');
const STARK = new Set(['madara.katon', 'gojo.dominio']);
// Kept on their Forja poses, by Stark's choice.
const KEEP = new Set(['bills.hakai', 'bills.destruction_energy']);
for (const name of Object.keys(S)) {
  if (STARK.has(name)) throw new Error(name + ' is Stark\'s own animation');
  if (KEEP.has(name)) { fs.rmSync(path.join(out, name + '.json'), { force: true }); console.log(name, 'kept on its Forja pose'); continue; }
  const json = Rig.bake(S[name].keys, 0.5);
  json.segueMira = true;
  if (S[name].soltar != null) json.soltar = +(S[name].soltar / 20).toFixed(4);
  fs.writeFileSync(path.join(out, name + '.json'), JSON.stringify(json));
  console.log(name, json.length, json.soltar != null ? 'soltar ' + json.soltar : '');
}
