import numpy as np
from PIL import Image
from scipy import ndimage as nd
SRC='/mnt/project-files/referencias/mapa-dragonball-600.png'
full=Image.open(SRC).convert('RGB')
CROP=(12,12,1283,1070)
im=np.asarray(full.crop(CROP)).astype(int)
Hs,Ws=im.shape[:2]
# --- labels: near-black boxes
dark=im.max(-1)<48
white=(im.min(-1)>170)&((im.max(-1)-im.min(-1))<40)
comb=dark|(white&nd.binary_dilation(dark,iterations=4))
closed=nd.binary_closing(comb,np.ones((3,3)),iterations=2)
closed=nd.binary_opening(closed,np.ones((5,5)))
lab,n=nd.label(closed)
mask=np.zeros(dark.shape,bool)
kept=[]
for i,sl in enumerate(nd.find_objects(lab)):
    h=sl[0].stop-sl[0].start; w=sl[1].stop-sl[1].start
    comp=lab[sl]==i+1
    fill=comp.mean()
    if 12<=h<=60 and w>=35 and fill>0.8 and w>h*1.5:
        kept.append((sl[1].start+CROP[0],sl[0].start+CROP[1],w,h))
        y0,y1=max(0,sl[0].start-3),sl[0].stop+3; x0,x1=max(0,sl[1].start-3),sl[1].stop+3
        mask[y0:y1,x0:x1]=True
LAB=[(45,25,155,62),(405,25,530,45),(245,65,340,98),(620,77,765,97),(828,115,898,148),(1025,145,1112,165),(1190,113,1258,135),(215,200,270,230),(445,230,515,262),(668,205,728,235),(842,236,913,268),(1123,246,1202,280),(55,288,158,318),(370,320,440,352),(973,383,1046,415),(560,468,728,494),(812,480,876,498),(1190,483,1255,514),(78,585,218,604),(1095,598,1155,630),(488,672,556,705),(850,712,930,742),(1120,796,1218,830),(113,836,250,858),(585,825,668,858),(100,937,195,968),(1140,946,1222,980)]
for x0,y0,x1,y1 in LAB:
    mask[max(0,y0-CROP[1]-3):y1-CROP[1]+3,max(0,x0-CROP[0]-3):x1-CROP[0]+3]=True
print('labels',len(kept))
# manual removals (orig coords) -> deep water
def rect(x0,y0,x1,y1):
    m=np.zeros(dark.shape,bool); m[max(0,y0-CROP[1]):y1-CROP[1],max(0,x0-CROP[0]):x1-CROP[0]]=True; return m
water_force=rect(1180,40,1265,140)|rect(1180,110,1270,140)|rect(40,960,250,1065)|rect(240,975,280,1010)|rect(430,40,500,100)|rect(830,975,945,1050)|rect(60,955,100,995)
# inpaint labels by nearest valid
_,ind=nd.distance_transform_edt(mask,return_indices=True)
im2=im[ind[0],ind[1]]
img=Image.fromarray(im2.astype(np.uint8))
W,H=600,500
small=np.asarray(img.resize((W,H),Image.BOX)).astype(int)
wf=np.asarray(Image.fromarray(water_force.astype(np.uint8)*255).resize((W,H),Image.BOX))>100
P={
 'deep':[(0,82,166),(1,91,174),(0,70,150)],
 'shallow':[(30,150,220),(60,175,235),(90,200,240)],
 'sand':[(240,200,100),(230,190,90),(250,215,120),(220,170,80)],
 'grass':[(90,170,40),(110,185,50),(70,150,30)],
 'forest':[(30,100,30),(40,120,35),(20,80,25)],
 'snow':[(235,240,245),(210,220,235),(190,200,215)],
 'rock':[(150,100,60),(120,80,50),(170,120,75),(100,65,40)],
 'redrock':[(190,90,90),(170,70,80),(210,120,110)],
 'lava':[(240,80,20),(220,50,10),(255,140,30)],
 'dark':[(40,40,45),(60,60,65),(25,25,30)],
}
names=list(P); cols=[];idx=[]
for i,nm in enumerate(names):
  for c in P[nm]: cols.append(c); idx.append(i)
cols=np.array(cols); idx=np.array(idx)
d=((small[:,:,None,:]-cols[None,None,:,:])**2).sum(-1)
cls=idx[d.argmin(-1)]
cls[wf]=0
cls[:4,:]=0;cls[-4:,:]=0;cls[:,:4]=0;cls[:,-4:]=0
# majority filter 3x3
def mode(c,k=3):
    cnt=np.stack([nd.uniform_filter((c==i).astype(float),k) for i in range(len(names))])
    return cnt.argmax(0)
cls=mode(cls,3)
N={n:i for i,n in enumerate(names)}
water=(cls==N['deep'])|(cls==N['shallow'])
# remove tiny water specks inside land and tiny land specks
water=nd.binary_opening(water,iterations=1)|((cls==N['deep']))
lab,nl=nd.label(~water)
sizes=nd.sum(np.ones_like(lab),lab,range(1,nl+1))
for i,s in enumerate(sizes):
    if s<12: water[lab==i+1]=True
# small enclosed water (drawn city glass, ponds) -> land
labw,nw=nd.label(water)
szw=nd.sum(np.ones_like(labw),labw,range(1,nw+1))
for i,s_ in enumerate(szw):
    if s_<60: water[labw==i+1]=False
cls[water&(cls!=N['deep'])&(cls!=N['shallow'])]=N['shallow']
cls[~water&((cls==N['deep'])|(cls==N['shallow']))]=N['grass']
land=~water
# heights
rng=np.random.default_rng(7)
def noise(scale,amp):
    g=rng.random((H//scale+3,W//scale+3))
    z=nd.zoom(g,scale,order=3)[:H,:W]
    return (z-0.5)*2*amp
dist=nd.distance_transform_edt(land)
wdist=nd.distance_transform_edt(water)
def blur(m,s): return nd.gaussian_filter(m.astype(float),s)
h=np.full((H,W),63.0)
h+=np.minimum(dist,12)*0.45
h+=blur(cls==N['grass'],2)*1.5+blur(cls==N['forest'],2)*3
h+=blur(cls==N['rock'],4)**1.2*30
h+=blur(cls==N['snow'],5)**1.1*42
mesa=blur(cls==N['redrock'],2)>0.45
h+=blur(mesa,1)*14
volc=blur((cls==N['lava'])|(cls==N['dark']),6)
h+=np.clip(volc-0.15,0,None)*45
h+=(noise(24,4)+noise(9,1.5))*np.clip(dist/6,0,1)
h=np.where(land,h,0)
fl=np.where(cls==N['shallow'],60-np.minimum(wdist,6)*0.6,0)+np.where(cls==N['deep'],54-np.minimum(wdist,20)*0.5,0)
fl+=noise(16,2)
h=np.where(land,np.maximum(h,63),np.minimum(fl,61))
h=np.round(h).astype(int)
# pad to 600x600 with ocean
PAD=50
Hf=np.full((600,600),44); Cf=np.zeros((600,600),int)
Hf[PAD:PAD+H]=h; Cf[PAD:PAD+H]=cls
# ocean beyond map edge deepens toward border
np.save('H.npy',Hf); np.save('C.npy',Cf)
print('land frac',land.mean(),'hmax',h.max(),'hmin',h.min())
# previews
show=np.array([(20,60,160),(60,160,230),(235,205,110),(100,180,50),(30,100,30),(240,245,250),(140,95,55),(190,90,90),(255,90,0),(70,70,70)],float)
base=show[Cf]
gy,gx=np.gradient(Hf.astype(float))
shade=np.clip(1+(-gx-gy)*0.08,0.5,1.4)[...,None]
Image.fromarray(np.clip(base*shade,0,255).astype(np.uint8)).resize((1200,1200),Image.NEAREST).save('relief.png')
hm=((Hf-Hf.min())/(Hf.max()-Hf.min())*255).astype(np.uint8)
Image.fromarray(hm).save('height.png')
# beaches: grass/forest land touching water, low -> sand
landF=~((Cf==N['deep'])|(Cf==N['shallow']))
near=nd.binary_dilation(~landF,iterations=2)&landF&(Hf<=65)&((Cf==N['grass'])|(Cf==N['forest'])|(Cf==N['rock']))
Cf[near]=N['sand']
def orig2map(x,y):
    c=(x-CROP[0])*W/(CROP[2]-CROP[0]); r=(y-CROP[1])*H/(CROP[3]-CROP[1])+PAD
    return int(round(c)),int(round(r))
def nearest(x,y,ok):
    c,r=orig2map(x,y)
    best=None
    for rr in range(600):
        for cc in range(600):
            pass
    ys,xs=np.nonzero(ok)
    i=((xs-c)**2+(ys-r)**2).argmin()
    return xs[i],ys[i]
# inland sand = desert (cacti, dead bushes); sand by the sea = beach
wd=nd.distance_transform_edt(landF)
Cf[(Cf==N['sand'])&(wd>7)]=10
names.append('desert'); N['desert']=10
ok=((Cf==N['grass']))&(nd.uniform_filter((Cf==N['grass']).astype(float),7)>0.8)
ac,ar=nearest(560,560,ok)
print('arrival world',ac-300,Hf[ar,ac]+1,ar-300,'class',names[Cf[ar,ac]])
import gzip,struct
with gzip.open('dragonball.bin','wb') as f:
    f.write(b'SMAP');f.write(struct.pack('>iiiii',1,600,600,-300,-300))
    f.write(struct.pack('>ii',ac-300,ar-300))
    f.write(Hf.astype('>i2').tobytes()); f.write(Cf.astype(np.uint8).tobytes())
np.save('C.npy',Cf)
print(names)
# --- building sites: the picture draws houses/castles as blobs; flatten a plateau so the mod builds them there
SITES={ # name: (orig x, orig y, radius in blocks, keep kind or None)
 'capsula':(110,140,24,'grass'),'kame':(295,125,9,'sand'),'pilaf':(240,255,11,'grass'),'satan':(110,345,11,'grass'),
 'redribbon':(1065,110,13,'snow'),'cidade_norte':(1145,320,20,'grass'),'piccolo':(700,250,11,'dark'),
 'palacio':(650,410,15,'desert'),'saiyajin':(1010,440,13,'grass'),'goku':(1220,560,9,'grass'),'arco':(1105,665,10,'grass'),
 'cidade_sul':(160,790,24,'grass'),'yajirobe':(530,720,9,'grass'),'buu':(1140,1020,11,'grass'),
}
import json
out={}
yy,xx=np.mgrid[0:600,0:600]
for k,(x,y,R,kind) in SITES.items():
    c,r=orig2map(x,y)
    dd=np.hypot(xx-c,yy-r)
    landS=~((Cf==N['deep'])|(Cf==N['shallow']))
    ring=(dd>=R)&(dd<R+8)&landS
    target=int(np.clip(np.percentile(Hf[ring],30) if ring.any() else 65,64,90))
    if k in ('capsula','kame','goku','arco','buu','satan','pilaf','yajirobe','cidade_sul','cidade_norte'): target=min(target,67)
    inner=(dd<R)
    Hf[inner]=target
    Cf[inner]=N[kind]
    edge=(dd>=R)&(dd<R+6)&landS
    t=((dd-R)/6)[edge]
    Hf[edge]=np.round(target*(1-t)+Hf[edge]*t).astype(int)
    out[k]=[int(c-300),int(target+1),int(r-300),R]
json.dump(out,open('sites.json','w'))
print(out)
with gzip.open('dragonball.bin','wb') as f:
    f.write(b'SMAP');f.write(struct.pack('>iiiii',1,600,600,-300,-300))
    f.write(struct.pack('>ii',ac-300,ar-300))
    f.write(Hf.astype('>i2').tobytes()); f.write(Cf.astype(np.uint8).tobytes())
np.save('H.npy',Hf);np.save('C.npy',Cf)
show=np.array([(20,60,160),(60,160,230),(235,205,110),(100,180,50),(30,100,30),(240,245,250),(140,95,55),(190,90,90),(255,90,0),(70,70,70),(225,190,95)],float)
base=show[Cf]; gy,gx=np.gradient(Hf.astype(float)); shade=np.clip(1+(-gx-gy)*0.08,0.5,1.4)[...,None]
Image.fromarray(np.clip(base*shade,0,255).astype(np.uint8)).resize((1200,1200),Image.NEAREST).save('relief.png')
