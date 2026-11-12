// node dump.js seed half out.json  -> {cross, station, vox:[[x,y,z,name,lit]...]}
const G=require('./gen.js');const [,,seed,half,out]=process.argv;
const p=G.shibuyaPiece(+seed,+half);const a=[];
p.w.each((x,y,z,t)=>{a.push([x,y,z,G.NAMES[G.base(t)],(t&G.LIT)?((t&G.COOL)?2:1):0]);});
require('fs').writeFileSync(out,JSON.stringify({cross:{x:p.cross.x,z:p.cross.z},station:p.station,n:a.length,vox:a}));
console.log(a.length,JSON.stringify(p.cross.x),p.cross.z,JSON.stringify(p.station).slice(0,300));
