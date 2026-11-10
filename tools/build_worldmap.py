# Dragon Ball map v2 (Stark 2026-11-10 14:28): no structures drawn, red circles = do NOT build (sea), 1000x1000.
import numpy as np, gzip, struct, json
from PIL import Image
from scipy import ndimage as nd
import sys
# usage: python3 tools/build_worldmap.py <drawing.png> <out.bin>   (needs numpy, pillow, scipy)
SRC=sys.argv[1] if len(sys.argv)>1 else 'mapa-dragonball-v2.png'
OUT=sys.argv[2] if len(sys.argv)>2 else 'dragonball.bin'
im=np.asarray(Image.open(SRC).convert('RGB')).astype(int)
Hs,Ws=im.shape[:2]
W=H=1000
yy,xx=np.mgrid[0:Hs,0:Ws]
# red annotation strokes -> inpaint; the two circled areas -> sea
red=(im[...,0]>170)&(im[...,1]<90)&(im[...,2]<90)
red=nd.binary_dilation(red,iterations=3)
skip=(((xx-118)/125.0)**2+((yy-110)/105.0)**2<=1)|(((xx-1150)/92.0)**2+((yy-1162)/55.0)**2<=1)
_,ind=nd.distance_transform_edt(red,return_indices=True)
im2=im[ind[0],ind[1]]
small=np.asarray(Image.fromarray(im2.astype(np.uint8)).resize((W,H),Image.BOX)).astype(int)
skipS=np.asarray(Image.fromarray(skip.astype(np.uint8)*255).resize((W,H),Image.BOX))>60
names=['deep','shallow','sand','grass','forest','snow','rock','redrock','lava','dark','desert','canyon','crater']
N={n:i for i,n in enumerate(names)}
P={'deep':[(0,82,166),(1,66,174),(0,70,150),(1,58,166)],'shallow':[(30,150,220),(60,175,235),(90,200,240),(40,170,230)],
 'sand':[(240,200,100),(230,190,90),(250,215,120),(248,210,84)],'grass':[(90,170,40),(110,185,50),(70,150,30),(63,158,70),(89,148,15)],
 'forest':[(30,100,30),(40,120,35),(20,80,25),(31,107,43)],'snow':[(235,240,245),(210,220,235),(199,218,244),(180,195,225),(160,175,210)],
 'rock':[(150,100,60),(120,80,50),(137,89,37),(100,65,40)],
 'canyon':[(194,170,83),(210,185,115),(170,140,80),(200,165,95)],
 'redrock':[(157,85,42),(152,77,74),(175,119,101),(168,116,104),(190,90,90)],'lava':[(240,80,20)],'dark':[(40,40,45),(60,60,65),(25,25,30)]}
cols=[];idx=[]
for nm,cs in P.items():
    for c in cs: cols.append(c); idx.append(N[nm])
cols=np.array(cols);idx=np.array(idx)
d=((small[:,:,None,:]-cols[None,None,:,:])**2).sum(-1)
cls=idx[d.argmin(-1)]
cls[skipS]=N['deep']
cls[cls==N['lava']]=N['redrock']
def mode(c,k):
    cnt=np.stack([nd.uniform_filter((c==i).astype(float),k) for i in range(len(names))]); return cnt.argmax(0)
cls=mode(cls,5)
cls[skipS]=N['deep']
water=(cls==N['deep'])|(cls==N['shallow'])
lab,nl=nd.label(~water); sz=nd.sum(np.ones_like(lab),lab,range(1,nl+1))
for i,s in enumerate(sz):
    if s<30: water[lab==i+1]=True
labw,nw=nd.label(water); szw=nd.sum(np.ones_like(labw),labw,range(1,nw+1))
# only the open sea is water: lakes and streams drawn inland would become pits in the mountains
big=np.argmax(szw)+1
water=labw==big
cls[water&(cls!=N['deep'])&(cls!=N['shallow'])]=N['shallow']
fill=~water&((cls==N['deep'])|(cls==N['shallow']))
idx=nd.distance_transform_edt(fill|water,return_distances=False,return_indices=True)
cls[fill]=cls[idx[0][fill],idx[1][fill]]
for cname,minsize in (('dark',150),('rock',80),('canyon',80),('snow',80),('redrock',50),('forest',20),('grass',20),('sand',20)):
    labc,nc=nd.label(cls==N[cname])
    if nc==0: continue
    sz=nd.sum(np.ones_like(labc),labc,range(1,nc+1))
    sm=np.isin(labc,np.nonzero(sz<minsize)[0]+1)
    if sm.any():
        other=np.where(sm|water,-1,cls)
        votes=np.stack([nd.uniform_filter((other==i).astype(float),13) for i in range(len(names))])
        cls[sm]=votes.argmax(0)[sm]
cls[cls==N['dark']]=N['rock']
# white surf drawn along the shores was read as snow: only the big snowy mountains are snow, the rest is beach
snowy=nd.uniform_filter((cls==N['snow']).astype(float),41)
cls[(cls==N['snow'])&(snowy<0.45)]=N['sand']
gy,gx=np.mgrid[0:H,0:W]
# south-west continent (Babidi's ship, Baba's palace): all desert (Stark 2026-11-10)
edge=500+nd.gaussian_filter(np.random.default_rng(5).random((H,W)),12)*0+28*np.sin(gx/23.0)*np.cos(gx/57.0)
sw=(gy>edge)&(gx<360)&(gy<790+18*np.sin(gx/19.0))
labs,_=nd.label(sw&~((cls==N['deep'])|(cls==N['shallow'])))
sw&=labs==labs[600,180]
cls[sw&np.isin(cls,[N['grass'],N['forest']])]=N['sand']
cls[sw&(cls==N['rock'])]=N['canyon']
# Red Ribbon's dense forest on the neck north of the desert
rr=np.hypot(gx-85,gy-435)<70
cls[rr&(cls==N['grass'])]=N['forest']
# east island: the pink mountain drawn there is a structure, not terrain -> plain island (Stark 2026-11-10)
ex,ey=1180*W/Ws,725*H/Hs
isle=((gx-ex)/(70*W/Ws))**2+((gy-ey)/(55*H/Hs))**2<1
cls[isle&np.isin(cls,[N['rock'],N['redrock'],N['snow'],N['canyon']])]=N['grass']
land=~water
rng=np.random.default_rng(11)
def noise(scale,amp):
    g=rng.random((H//scale+3,W//scale+3)); z=nd.zoom(g,scale,order=3)[:H,:W]; return (z-0.5)*2*amp
dist=nd.distance_transform_edt(land); wdist=nd.distance_transform_edt(water)
def blur(m,s): return nd.gaussian_filter(m.astype(float),s)
# shore ramp (Stark 2026-11-10): the first blocks of land sit flush with the water (y 62), then rise slowly
h=np.full((H,W),62.0)
h+=np.clip((dist-1.5)*0.28,0,None).clip(0,5)+np.clip(dist-20,0,40)*0.05
h+=blur(cls==N['grass'],3)*1.5+blur(cls==N['forest'],3)*3
h+=blur(cls==N['rock'],6)**1.2*32+blur(cls==N['canyon'],5)**1.2*28
h+=blur(cls==N['snow'],8)**1.1*45
mesa=blur(cls==N['redrock'],3)>0.45
h+=blur(mesa,1.5)*16
h+=(noise(36,4)+noise(14,1.5))*np.clip(dist/8,0,1)
h=np.where(land,nd.gaussian_filter(h,2.0),0)
fl=np.where(cls==N['shallow'],61.6-np.minimum(wdist,10)*0.45,0)+np.where(cls==N['deep'],54-np.minimum(wdist,30)*0.4,0)
fl+=noise(24,2)*np.clip((wdist-3)/6,0,1)
h=np.minimum(h,62+np.clip(dist-1.5,0,None)*0.7)
h=np.where(land,np.maximum(h,62),np.minimum(fl,61))
h=np.round(h).astype(int)
Hf=h;Cf=cls.copy()
landF=~((Cf==N['deep'])|(Cf==N['shallow']))
near=nd.binary_dilation(~landF,iterations=3)&landF&(Hf<=64)&((Cf==N['grass'])|(Cf==N['forest'])|(Cf==N['rock']))
Cf[near]=N['sand']
wd=nd.distance_transform_edt(landF)
Cf[(Cf==N['sand'])&(wd>10)]=N['desert']
# building sites (world x, z, radius, ground kind): spots read off the real Dragon Ball map, moved to the flattest
# dry ground nearby, then flattened so the buildings stand right (Stark 2026-11-10: no terrain where buildings go)
SITES=[('kame',412,88,11,'grass'),('goku',300,-25,11,'grass'),('capsula',-175,-170,24,'grass'),('redribbon',-415,-65,15,'forest'),
 ('pilaf',75,-193,14,'desert'),('satan',318,-142,12,'grass'),('cidade_norte',103,-402,19,'snow'),('cidade_sul',-44,250,22,'grass'),
('buu',-313,-284,10,'grass'),('yajirobe',-447,-229,8,'grass'),('karin',-397,-234,7,'grass'),
 ('babidi',-319,57,18,'desert')]
yy2,xx2=np.mgrid[0:H,0:W]
sites={}
for nm,wx,wz,rad,kind in SITES:
    cx,cz=wx+500,wz+500; best=None
    for ox in range(-30,31,3):
        for oz in range(-30,31,3):
            x,z=cx+ox,cz+oz
            disc=(xx2[z-rad-3:z+rad+4,x-rad-3:x+rad+4]-x)**2+(yy2[z-rad-3:z+rad+4,x-rad-3:x+rad+4]-z)**2<=(rad+3)**2
            hs=Hf[z-rad-3:z+rad+4,x-rad-3:x+rad+4][disc]; cs=Cf[z-rad-3:z+rad+4,x-rad-3:x+rad+4][disc]
            if ((cs==N['deep'])|(cs==N['shallow'])).any(): continue
            score=hs.std()+0.08*np.hypot(ox,oz)
            if best is None or score<best[0]: best=(score,x,z,int(np.median(hs)))
    if best is None: print('NO SPOT',nm); continue
    _,x,z,t=best
    d=np.hypot(xx2-x,yy2-z)
    inner=d<=rad; ring=(d>rad)&(d<=rad+10)&~((Cf==N['deep'])|(Cf==N['shallow']))
    wgt=np.clip((d-rad)/10,0,1)
    Hf=np.where(inner,t,np.where(ring,np.round(t*(1-wgt)+Hf*wgt),Hf)).astype(int)
    Cf[inner]=N[kind]
    sites[nm]=(x-500,t,z-500)
    print('site',nm,x-500,t,z-500)
# Raditz's landing (Stark 2026-11-10): a real crater where the pod fell, bowl with a raised rim, in the drawn ring
ccx,ccz,R=750,305,30
d=np.hypot(xx2-ccx,yy2-ccz)
base=int(np.median(Hf[(d>44)&(d<56)]))
bowl=base+2-min(12,base-62)*np.clip(1-(d/R)**2,0,1)
rim=base+2+4*np.exp(-((d-R-2)/3.0)**2)
target=np.where(d<R,bowl,rim)
wgt=np.clip((d-R-8)/10,0,1)
zone=d<R+18
Hf=np.where(zone,np.round(target*(1-wgt)+Hf*wgt),Hf).astype(int)
Cf[d<R+3]=N['crater']
pod=(ccx-500,int(Hf[ccz,ccx])+1,ccz-500)
print('crater',pod,'base',base)
# arrival: big grass area nearest the middle of the main continent
def o2m(x,y): return int(x*W/Ws),int(y*H/Hs)
ok=(Cf==N['grass'])&(nd.uniform_filter((Cf==N['grass']).astype(float),11)>0.85)
c,r=o2m(520,520); ys,xs=np.nonzero(ok); i=((xs-c)**2+(ys-r)**2).argmin(); ac,ar=xs[i],ys[i]
print('arrival',ac-500,Hf[ar,ac]+1,ar-500)
# Namekusei over open sea, south-west
c,r=o2m(140,1120); deepok=(Cf==N['deep'])&(nd.distance_transform_edt(Cf==N['deep'])>30)
ys,xs=np.nonzero(deepok); i=((xs-c)**2+(ys-r)**2).argmin(); print('namek',xs[i]-500,ys[i]-500)
with gzip.open(OUT,'wb') as f:
    f.write(b'SMAP');f.write(struct.pack('>iiiii',1,W,H,-500,-500)); f.write(struct.pack('>ii',ac-500,ar-500))
    f.write(Hf.astype('>i2').tobytes()); f.write(Cf.astype(np.uint8).tobytes())
show=np.array([(38,72,160),(64,150,205),(219,207,163),(110,170,60),(50,110,40),(240,245,250),(130,110,90),(170,85,60),(230,90,20),(60,60,65),(225,200,130),(205,160,70),(120,85,55)],float)
gy,gx=np.gradient(Hf.astype(float)); shade=np.clip(1+(-gx-gy)*0.06,0.55,1.35)[...,None]
Image.fromarray(np.clip(show[Cf]*shade,0,255).astype(np.uint8)).save(OUT.replace('.bin','_relief.png'))
print('hmax',Hf.max(),'land',landF.mean())
