"""Two individually authored 16x conversion tables. Asset draft; no runtime writes.

Side relief is a three-depth texture mask. The masks are joined into one shell,
then only exposed faces are emitted. Painted carvings are not separate cuboids.
"""
from pathlib import Path
from dataclasses import dataclass
from collections import defaultdict
import base64
import json
import uuid
import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
REF = HERE / 'references'


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')


def source(name):
    return np.array(Image.open(REF/f'{name}.png').convert('RGBA'))[:16,:16].copy()


def tint(a, factors):
    b=a.copy();b[:,:,:3]=np.clip(a[:,:,:3]*np.array(factors),0,255).astype('uint8')
    return b


def opaque(rgb):
    a=np.zeros((16,16,4),dtype=np.uint8);a[:]=[*rgb,255];return a


def mask_texture(a, mask):
    b=a.copy();b[~mask]=0;return b


def rows_mask(rows, x=0, y=0):
    a=np.zeros((16,16),dtype=bool)
    for j,row in enumerate(rows):
        for i,c in enumerate(row):
            if c not in '.0 ':a[y+j,x+i]=True
    return a


def font(size):
    return ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',size)


STONE=source('polished_blackstone')
GILDED=source('gilded_blackstone')
CARVED=source('chiseled_polished_blackstone')
DEEPSLATE=tint(source('polished_deepslate'),(.73,.82,.92))


def corner_patch(shadow, mid, highlight):
    """Vanilla enchanting-table diamond's 3x3 corner, using only its grayscale.

    Its continuous bright outer edge and broad inner fill replace a dark inset
    dot. Colour stays material-specific; all samples remain whole native texels.
    """
    reference=source('enchanting_table_side')[4:7,:3,:3]@[.2126,.7152,.0722]
    values=np.unique(reference)
    palette=np.array([shadow,mid,highlight])
    rgb=np.stack([np.interp(reference,values,palette[:,c]) for c in range(3)],axis=2)
    return np.concatenate([np.rint(rgb).astype('uint8'),np.full((3,3,1),255,dtype='uint8')],axis=2)


SLATE_CORNER=corner_patch((55,65,76),(66,76,87),(80,90,101))
# Retain the polished stone's clusters on return/spire faces, with a narrower
# value range than the old 28% darkening. Caps remain distinct from uprights.
slate_value=source('polished_deepslate')[:,:,0].astype(float)
slate_value=(slate_value-slate_value.min())/(slate_value.max()-slate_value.min())
CORNER_SLATE=opaque((0,0,0))
CORNER_SLATE[:,:,:3]=np.rint(np.array([55,65,76])+slate_value[:,:,None]*[16,16,16]).astype('uint8')
for z in (0,13):
    for x in (0,13):
        CORNER_SLATE[z:z+3,x:x+3]=SLATE_CORNER[::1 if z==0 else -1,::1 if x==0 else -1]
SCULK=source('sculk')
CATALYST=source('sculk_catalyst_side')
SHRIEKER=source('sculk_shrieker_side')

# Original catalyst bone clusters are extended by nearest palette-region, not noise.
BONE=CATALYST.copy()
bone_pixels=np.argwhere((CATALYST[:,:,0]>100)&(CATALYST[:,:,1]>110))
for r in range(16):
    for c in range(16):
        if CATALYST[r,c,0]<100:
            rr,cc=bone_pixels[np.argmin(np.abs(bone_pixels-[r,c]).sum(axis=1))]
            BONE[r,c]=CATALYST[rr,cc]

# Bright ore-yellow midtones and pale-gold clusters, with narrow ochre shadows.
# Preserve authored vanilla cluster boundaries; do not introduce surface noise.
gold_src=source('gold_block')[:,:,:3].mean(axis=2)
g_min,g_max=gold_src.min(),gold_src.max()
GOLD=opaque((0,0,0))
gold_level=(gold_src-g_min)/(g_max-g_min)
GOLD[:]=[255,224,62,255]
GOLD[gold_level<.30]=[236,178,25,255]
GOLD[gold_level<.10]=[180,113,17,255]
GOLD[gold_level>.65]=[255,247,121,255]
BRONZE=tint(GOLD,(.88,.82,.72))
GOLD_CORNER=corner_patch((242,204,57),(255,231,91),(255,248,155))


@dataclass(frozen=True)
class Cell:
    texture: str
    side: int = -1
    u: int = 0
    v: int = 0
    top: str = ''
    role: str = 'body'


class Model:
    def __init__(self,name,height,label):
        self.name=name;self.height=height;self.label=label
        self.root=HERE/name;self.tex=self.root/'textures';self.tex.mkdir(parents=True,exist_ok=True)
        self.textures={};self.materials={};self.cells={};self.groups={'static':self.cells}
        self.animations={};self.parts={}

    def texture(self,name,a,emission=None,metal=None,roughness=.78):
        a=a.copy();self.textures[name]=a
        if emission is None:emission=np.zeros((16,16),dtype=np.uint8)
        if metal is None:metal=np.zeros((16,16),dtype=bool)
        visible=a[:,:,3]>0
        emission=np.where(visible,emission,0).astype('uint8')
        metal=metal&visible
        Image.fromarray(a).save(self.tex/f'{name}.png')
        n=np.zeros((16,16,4),dtype=np.uint8);n[:]=[128,128,255,255]
        Image.fromarray(n).save(self.tex/f'{name}_n.png')
        s=np.zeros_like(n);s[:]=[round((1-roughness**.5)*255),10,30,0]
        s[metal]=[round((1-.48**.5)*255),231,0,0]
        s[:,:,3]=emission
        Image.fromarray(s).save(self.tex/f'{name}_s.png')
        Image.fromarray(emission).save(self.tex/f'{name}_emission.png')
        Image.fromarray((metal*255).astype('uint8')).save(self.tex/f'{name}_metal.png')
        rough=np.full((16,16),round(roughness*255),dtype=np.uint8);rough[metal]=round(.48*255)
        Image.fromarray(rough).save(self.tex/f'{name}_roughness.png')
        self.materials[name]={'roughness':roughness,'has_gold':bool(metal.any())}

    def box(self,a,b,texture,group='static',top='',role='body'):
        volume=self.groups.setdefault(group,{})
        for x in range(a[0],b[0]):
            for y in range(a[1],b[1]):
                for z in range(a[2],b[2]):volume[x,y,z]=Cell(texture,top=top,role=role)

    def shell(self,outer,middle,front,side,roof):
        # One continuous backboard volume with two shallow masked relief layers.
        self.back=[front,side,front,side]
        self.box((2,0,2),(14,self.height,14),front,top=roof)
        for depth,texture in [(1,middle),(0,outer)]:
            mask=self.textures[texture][:,:,3]>0
            for s in range(4):
                for v in range(self.height):
                    for u in range(16):
                        if not mask[v,u]:continue
                        p=(u,self.height-1-v,depth)
                        for _ in range(s):p=(15-p[2],p[1],p[0])
                        self.cells[p]=Cell(texture,s,u,v,roof,'layer')

    def carve(self,x,z,height):
        for y in range(height,self.height):self.cells.pop((x,y,z),None)

    def cap(self,x,z,height,texture,side_texture=None):
        p=(x,height-1,z)
        if p in self.cells:
            old=self.cells[p]
            if side_texture:self.cells[p]=Cell(side_texture,top=texture,role='deck')
            else:self.cells[p]=Cell(old.texture,old.side,old.u,old.v,texture,old.role)

    def animate(self,group,kind,keys,pivot=(8,0,8)):
        self.animations[group]={'kind':kind,'keys':keys,'pivot':pivot}

    def export_geometry(self):
        # Rectangle merging leaves editable planar panels, plus one-voxel returns.
        # No hidden voxel cube faces, alpha sorting, duplicate corner walls or bevels.
        directions=[(0,0,-1),(0,0,1),(-1,0,0),(1,0,0),(0,1,0),(0,-1,0)]
        normal_side={(0,0,-1):0,(1,0,0):1,(0,0,1):2,(-1,0,0):3}
        for group,volume in self.groups.items():
            buckets=defaultdict(dict)
            for (x,y,z),cell in volume.items():
                for dx,dy,dz in directions:
                    if (x+dx,y+dy,z+dz) in volume:continue
                    if dy:
                        plane=y+(dy>0);a,b=x,z
                        texture=cell.top or cell.texture
                        if cell.role=='layer' and (not cell.top or dy<0 or plane<self.height-1):
                            # Lower relief returns belong to the side material, not the roof inlay.
                            texture=cell.texture;mode=('pixel',cell.u,cell.v)
                        else:mode=('top',)
                    else:
                        s=normal_side[(dx,dy,dz)]
                        if dz:plane=z+(dz>0);a,b=x,y
                        else:plane=x+(dx>0);a,b=z,y
                        if cell.role=='layer':
                            texture=cell.texture
                            if s==cell.side:mode=('side',s,self.height)
                            elif [z,15-x,15-z,x][s]==0 and (s-cell.side)%2==1:
                                # The meeting outer corner uses the other side's projection.
                                # A raised snout also reaches the outer depth, but its
                                # perpendicular returns must keep their opaque source texel.
                                mode=('side',s,self.height)
                            else:mode=('pixel',cell.u,cell.v)
                        elif cell.role=='body':
                            depth=[z,15-x,15-z,x][s]
                            texture=self.back[s] if depth<=2 else self.cavity_texture
                            mode=('side',s,self.height if depth<=2 else 16)
                        else:
                            texture=cell.texture;mode=('side',s,16)
                    buckets[((dx,dy,dz),plane,texture,mode)][a,b]=True
            faces=[]
            for (normal,plane,texture,mode),surface in buckets.items():
                remaining=set(surface)
                while remaining:
                    a,b=min(remaining,key=lambda p:(p[1],p[0]));A=a+1;B=b+1
                    # A return samples a single original texel; do not stretch it.
                    if mode[0]!='pixel':
                        while (A,b) in remaining:A+=1
                        while all((u,B) in remaining for u in range(a,A)):B+=1
                    for u in range(a,A):
                        for v in range(b,B):remaining.remove((u,v))
                    dx,dy,dz=normal
                    if dy:points=[(a,plane,b),(a,plane,B),(A,plane,B),(A,plane,b)]
                    elif dz:points=[(a,b,plane),(A,b,plane),(A,B,plane),(a,B,plane)]
                    else:points=[(plane,b,a),(plane,B,a),(plane,B,A),(plane,b,A)]
                    n=np.cross(np.subtract(points[1],points[0]),np.subtract(points[2],points[0]))
                    if np.dot(n,normal)<0:points.reverse()
                    if mode[0]=='top':uv=[(p[0],p[2]) for p in points]
                    elif mode[0]=='side':
                        s,h=mode[1:];uv=[]
                        for px,py,pz in points:
                            u=[px,pz,16-px,16-pz][s];uv.append((u,h-py))
                    else:
                        u,v=mode[1:];uv=[(u,v),(u+1,v),(u+1,v+1),(u,v+1)]
                    faces.append({'vertices':points,'uv':uv,'texture':texture})
            self.parts[group]={'group':group,'faces':faces}

    def save(self,description):
        self.export_geometry()
        names=list(self.textures)
        ident=lambda n:str(uuid.uuid5(uuid.NAMESPACE_URL,f'convert_table/16x/{self.name}/{n}'))
        data={'variant':self.name,'label':self.label,'texel_density':16,'minimum_feature_voxels':1,
              'overall_dimensions':[16,16,16],'body_height':self.height,'side_layer_depths':[0,1,2],
              'textures':names,'materials':self.materials,'parts':self.parts,'animations':self.animations,
              'design':description}
        write_json(self.root/'scene.json',data)
        elements=[];outliner=[];animators={}
        for name,part in self.parts.items():
            vertices={};faces={}
            for i,face in enumerate(part['faces']):
                ids=[];uv={}
                for j,(pos,coord) in enumerate(zip(face['vertices'],face['uv'])):
                    key=f'v{i}_{j}';vertices[key]=pos;ids.append(key);uv[key]=coord
                faces[f'f{i}']={'vertices':ids,'uv':uv,'texture':names.index(face['texture'])}
            eid=ident(name+'_mesh');bid=ident(name+'_bone')
            elements.append({'name':name,'uuid':eid,'type':'mesh','origin':[0,0,0],
                             'rotation':[0,0,0],'vertices':vertices,'faces':faces})
            anim=self.animations.get(name);pivot=anim['pivot'] if anim else [8,0,8]
            outliner.append({'name':name,'uuid':bid,'origin':pivot,'children':[eid]})
            if anim:
                k=[]
                for i,(time,value) in enumerate(anim['keys']):
                    vec={'x':'0','y':str(value),'z':'0'}
                    k.append({'channel':anim['kind'],'time':time,'data_points':[vec],
                              'uuid':ident(f'{name}_key{i}'),'interpolation':'linear' if anim['kind']=='rotation' else 'catmullrom'})
                animators[bid]={'name':name,'type':'bone','keyframes':k}
        project={'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':self.label,
                 'resolution':{'width':16,'height':16},'elements':elements,'outliner':outliner,
                 'textures':[{'name':n+'.png','uuid':ident(n+'_texture'),'id':str(i),'uv_width':16,'uv_height':16,
                              'mode':'bitmap','source':'data:image/png;base64,'+base64.b64encode((self.tex/f'{n}.png').read_bytes()).decode()}
                             for i,n in enumerate(names)],
                 'animations':[{'name':'idle','uuid':ident('idle'),'loop':'loop','length':12,'snapping':24,'animators':animators}]}
        write_json(self.root/f'{self.name}_16x_refined.bbmodel',project)
        self.swatches()
        print(f'Authored {self.name}: 16 x 16 x 16; {len(self.parts)} mesh groups.')

    def swatches(self):
        picks=[n for n in self.textures if n.startswith('side_')]+['tabletop']
        sheet=Image.new('RGB',(len(picks)*284,390),(26,29,38));d=ImageDraw.Draw(sheet)
        for i,n in enumerate(picks):
            a=Image.fromarray(self.textures[n]).resize((256,256),Image.Resampling.NEAREST)
            sheet.paste(a,(14+i*284,66),a);d.text((14+i*284,24),n,font=font(17),fill=(215,218,224))
        d.text((20,347),self.label+'  ·  原生 16×16 贴图 / 三层侧面 / 整体素结构',font=font(21),fill=(186,193,204))
        sheet.save(self.root/'texture_sheet.png')


def make_black_gold():
    m=Model('black_gold',16,'黑金转换台')
    m.cavity_texture='pit_wall'
    gold_mask=(GILDED[:,:,0]>GILDED[:,:,2]*1.8)&(GILDED[:,:,0]>75)
    # Retain gilded blackstone's dark mineral edges and its original bright-grain hierarchy.
    gilded=GILDED.copy()
    gilded[gold_mask&(GILDED[:,:,0]>=200)]=[246,191,29,255]
    gilded[gold_mask&(GILDED[:,:,0]>=245)]=[255,247,110,255]
    m.texture('blackstone',STONE)
    m.texture('gilded_blackstone',gilded,metal=gold_mask)
    m.texture('gold',GOLD,metal=np.ones((16,16),dtype=bool),roughness=.42)
    m.texture('bronze',BRONZE,metal=np.ones((16,16),dtype=bool),roughness=.5)

    outer=np.zeros((16,16),dtype=bool)
    for x in (0,13):outer[:3,x:x+3]=True;outer[14:,x:x+3]=True
    # Gold shoulder clasp; compact corner shoes, no End-style long capitals.
    outer[:2,6:10]=True
    guards=GOLD.copy()
    for x in (0,13):
        patch=GOLD_CORNER[:,::1 if x==0 else -1]
        guards[:3,x:x+3]=patch
        guards[14:,x:x+3]=patch[:2]
    guards[0,7:9]=GOLD[3,3]
    m.texture('side_gold_clamps',mask_texture(guards,outer),metal=outer)

    frame_mask=np.zeros((16,16),dtype=bool)
    frame_mask[:3,1:15]=True;frame_mask[13:,1:15]=True
    frame_mask[:,1:3]=True;frame_mask[:,13:15]=True
    # The relief follows the snout silhouette; nostrils remain recessed in the backboard.
    pig=['00111100','01222210','12322321','12022021','12022021','01222210','00111100']
    for r,row in enumerate(pig,4):
        for c,key in enumerate(row,4):frame_mask[r,c]=key!='0'
    frame=source('polished_blackstone_bricks')
    frame[4:11,4:12]=CARVED[4:11,4:12]
    metal=np.zeros((16,16),dtype=bool)
    # Expose recognisable mineral seams on the stone uprights, not only behind the tablet.
    for x0,x1 in ((1,3),(13,15)):
        frame[3:13,x0:x1]=gilded[3:13,x0:x1]
        metal[3:13,x0:x1]=gold_mask[3:13,x0:x1]
    # Four flat bronze straps, borrowed from the paired runs in the snout trim.
    for x in (3,12):
        frame[:3,x]=BRONZE[:3,x];metal[:3,x]=True
        frame[13:,x]=BRONZE[13:,x];metal[13:,x]=True
    # Chiseled-blackstone faceplate: dark stone cheeks, lighter carved edges and black nostrils.
    palette={1:(29,24,33,255),2:(53,47,60,255),3:(77,69,84,255)}
    for r,row in enumerate(pig,4):
        for c,key in enumerate(row,4):
            if key!='0':frame[r,c]=palette[int(key)];metal[r,c]=False
            elif 6<=r<=8 and c in (6,9):frame[r,c]=(22,19,25,255)
    m.texture('side_carved_frame',mask_texture(frame,frame_mask),metal=metal&frame_mask)

    for is_side,n in [(False,'side_inset_front'),(True,'side_inset_side')]:
        panel=gilded.copy();em=np.zeros((16,16),dtype=np.uint8);pm=gold_mask.copy()
        # Snout armour's parallel hooks, moved to the flanks of the carved faceplate.
        for x in (3,12):
            for r in (5,6,8,9):panel[r,x]=BRONZE[r,x];pm[r,x]=True
        # A short, lower energy slot makes the snout tablet the main side feature.
        palette=[(135,29,12,255),(210,48,13,255),(251,92,17,255),(255,161,44,255)]
        for r in range(11,13):
            for x in range(6,10):
                k=(x-6+r-11)%4;panel[r,x]=palette[k];em[r,x]=[45,90,145,195][k];pm[r,x]=False
        # Two shadowed nostrils and a dark outline form actual recesses behind the relief.
        for r,row in enumerate(pig,4):
            for c,key in enumerate(row,4):
                panel[r,c]=(18,15,20,255) if key=='0' else (33,27,38,255);pm[r,c]=False
        if is_side:
            # Side flanks use paired gold tabs, echoing the leggings/arm trim.
            for r in (4,7,10):
                for x in (3,12):panel[r,x]=GOLD[r,x];pm[r,x]=True
        m.texture(n,panel,em,pm)

    top=STONE.copy();tm=np.zeros((16,16),dtype=bool)
    for x0 in (0,13):
        for z0 in (0,13):
            top[z0:z0+3,x0:x0+3]=GOLD_CORNER[::1 if z0==0 else -1,::1 if x0==0 else -1]
            tm[z0:z0+3,x0:x0+3]=True
    # Solid gold shoulder clamps wrap both their outer and cavity-facing returns.
    for x,z in [(x,z) for x in range(6,10) for z in range(2)]:
        for s in range(4):
            xx,zz=x,z
            for _ in range(s):xx,zz=15-zz,xx
            top[zz,xx]=GOLD[zz,xx];tm[zz,xx]=True
    for x,z in [(i,i) for i in range(3,6)]+[(15-i,i) for i in range(3,6)]+[(i,15-i) for i in range(3,6)]+[(15-i,15-i) for i in range(3,6)]:
        top[z,x]=BRONZE[z,x];tm[z,x]=True
    # An explicitly pixel-stepped diamond, inset into a continuous stone deck.
    diamond=rows_mask(['0000110000','0001111000','0011111100','0111111110','1111111111','1111111111','0111111110','0011111100','0001111000','0000110000'],3,3)
    def inset(mask):
        out=np.zeros_like(mask)
        for z in range(1,15):
            for x in range(1,15):out[z,x]=mask[z,x] and all(mask[z+dz,x+dx] for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)))
        return out
    d8=inset(diamond);d6=inset(d8);d4=inset(d6)
    rim=diamond&~d8;top[rim]=GOLD[rim];tm[rim]=True
    m.texture('tabletop',top,metal=tm)
    carved=STONE.copy();carved[:,:,:3]=(carved[:,:,:3]*.77).astype('uint8')
    m.texture('pit_wall',carved,roughness=.8)
    step=carved.copy()
    for z in range(16):
        for x in range(16):
            if d6[z,x]:step[z,x]=(67+((x+z)%3)*9,24+((x+z)%3)*4,17,255)
    m.texture('pit_steps',step)
    core=opaque((190,36,9))
    for r in range(16):
        for c in range(16):
            core[r,c]=[(157,26,8,255),(230,49,8,255),(255,128,18,255),(249,79,10,255)][(r//2+c//2)%4]
    m.texture('amethyst_core',core,np.where(core[:,:,1]>100,225,175).astype('uint8'),roughness=.18)
    m.shell('side_gold_clamps','side_carved_frame','side_inset_front','side_inset_side','tabletop')
    for z in range(16):
        for x in range(16):
            corner=(x<3 or x>=13) and (z<3 or z>=13)
            shoulder=(6<=x<10 and (z<2 or z>=14)) or (6<=z<10 and (x<2 or x>=14))
            # Only the substantial corner clamps and shoulder pads stand one voxel proud.
            # The thin diagonal inlays and the pit lip remain painted into the Y=15 deck.
            height=13 if d6[z,x] else 14 if d8[z,x] else 16 if corner or shoulder else 15
            m.carve(x,z,height)
            if d8[z,x]:m.cap(x,z,height,'pit_steps','pit_wall')
            else:m.cap(x,z,height,'tabletop')
            # A gold cap is a gold volume: every exposed return uses solid gold.
            if corner or shoulder or rim[z,x]:
                low=13 if corner else 14
                for y in range(low,height):
                    if (x,y,z) in m.cells:m.cells[x,y,z]=Cell('gold',top='tabletop',role='metal')
    # Raise cheeks and bridge one extra voxel; retain open nostril holes and stepped silhouette.
    for s in range(4):
        for r,row in enumerate(pig,4):
            for u,key in enumerate(row,4):
                if key not in '23':continue
                p=(u,15-r,0)
                for _ in range(s):p=(15-p[2],p[1],p[0])
                m.cells[p]=Cell('side_carved_frame',s,u,r,'','layer')
    # Fixed stepped walls with a four-voxel diamond rotor recessed one voxel below the deck.
    # Its circumradius sqrt(5) fits inside the six-voxel pocket's inscribed radius.
    for z in range(16):
        for x in range(16):
            if d4[z,x]:m.box((x,13,z),(x+1,14,z+1),'amethyst_core','recessed_rotor',top='amethyst_core',role='crystal')
    m.animate('recessed_rotor','rotation',[(t,t*30) for t in (0,3,6,9,12)],(8,13,8))
    m.save('Solid gold corner/shoulder clamps and diamond lip with gold inward returns. Snout-shaped two-depth blackstone relief with recessed nostrils, no rectangular tablet. Orange-red lava core and side slots; deck Y=15, shoulders Y=16, pit steps Y=14/13, rotating core Y=14.')


def make_sculk():
    m=Model('sculk',13,'幽匿转换台')
    m.cavity_texture='sculk'
    m.texture('deepslate',DEEPSLATE)
    m.texture('corner_deepslate',CORNER_SLATE)
    m.texture('bone',BONE,roughness=.9)
    m.texture('sculk',SCULK,roughness=.86)

    outer=np.zeros((16,16),dtype=bool)
    for x in (0,13):outer[:3,x:x+3]=True;outer[11:13,x:x+3]=True
    guards=CORNER_SLATE.copy()
    # Quiet inner fill and a continuous edge follow the vanilla diamond cap's
    # grayscale hierarchy, recoloured as polished deepslate rather than gems.
    for x in (0,13):
        patch=SLATE_CORNER[:,::1 if x==0 else -1]
        guards[:3,x:x+3]=patch
        guards[11:13,x:x+3]=patch[:2]
    # Remove the isolated cyan cap inlays, including front (2, 11) in voxel coordinates.
    em=np.zeros((16,16),dtype=np.uint8)
    m.texture('side_deepslate_caps',mask_texture(guards,outer),em)

    mask=np.zeros((16,16),dtype=bool)
    mask[:2,1:15]=True;mask[11:13,1:15]=True
    mask[:13,1:3]=True;mask[:13,13:15]=True
    frame=DEEPSLATE.copy()
    # Keep one lower catalyst-like bone fan. Remove the separate upper forked motif.
    bones=rows_mask([
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
        '...BB......BB...',
        '...BB......BB...',
        '...BB..BB..BB...',
        '...BBBBBBBBBB...',
        '....BBB..BBB....',
        '................',
        '................',
    ])
    mask|=bones;frame[bones]=BONE[bones]
    # Use the ivory source's cool shaded edge against the recess.
    for r in range(13):
        for c in range(16):
            if bones[r,c] and c<15 and not bones[r,c+1]:
                frame[r,c,:3]=np.maximum(frame[r,c,:3].astype(int)-[22,22,18],0)
    fe=np.zeros((16,16),dtype=np.uint8)
    for x in (4,6,9,11):frame[12,x]=(21,135,141,255);fe[12,x]=60
    m.texture('side_bone_frame',mask_texture(frame,mask),fe)

    for is_side,n in [(False,'side_inset_front'),(True,'side_inset_side')]:
        panel=SCULK.copy();e=np.zeros((16,16),dtype=np.uint8)
        # Keep catalyst/shrieker's dark clusters at their native size; ivory is on its own depth.
        donor=SHRIEKER if is_side else CATALYST
        dark=(donor[:,:,0]<45)&(donor[:,:,1]<110)&(donor[:,:,3]>0)
        panel[dark]=donor[dark]
        # Shrieker sides contain cutout air between their horns. The new inset is a
        # solid backboard: importing those transparent texels would open pale gaps.
        panel[:,:,3]=255
        # A connected central vein replaces the scattered decorative side twigs.
        branch=[(7,2),(7,3),(8,3),(8,4),(7,4),(7,5),(7,6),(8,6),(8,7),(7,7),(7,8)]
        shades=[(10,67,76,255),(10,99,111,255),(23,143,150,255),(49,192,194,255)]
        for i,(x,y) in enumerate(branch):
            k=i%4 if x in (7,8) else i%2;panel[y,x]=shades[k];e[y,x]=[10,30,65,110][k]
        m.texture(n,panel,e)

    top=DEEPSLATE.copy()
    # Sculk growth on the inward edge, preserving the broad slate seams outside.
    for z in range(16):
        for x in range(16):
            if x in (2,13) or z in (2,13):top[z,x]=SCULK[z,x]
            if (x<3 or x>=13) and (z<3 or z>=13):top[z,x]=CORNER_SLATE[z,x]
    m.texture('tabletop',top)
    pool=source('sculk_shrieker_inner_top');pe=np.zeros((16,16),dtype=np.uint8)
    # Recompose the shrieker's inner marks as squared branching channels, not an annulus.
    pool=SCULK.copy()
    channels=rows_mask(['............','..11....11..','..121..121..','...121121...','....1221....','...123321...','...123321...','....1221....','...121121...','..121..121..','..11....11..','............'],2,2)
    for z in range(2,14):
        for x in range(2,14):
            if channels[z,x]:
                k=(x+z)%3;pool[z,x]=[(7,72,81,255),(12,115,126,255),(27,166,174,255)][k];pe[z,x]=[20,45,85][k]
    m.texture('resonance_bed',pool,pe,roughness=.7)
    crystal=opaque((19,138,149))
    for r in range(16):
        for c in range(16):
            crystal[r,c]=[(13,116,129,255),(25,165,174,255),(65,208,206,255),(23,155,168,255)][(c+r//2)%4]
    m.texture('soul_crystal',crystal,np.full((16,16),145,dtype=np.uint8),roughness=.55)
    tip=opaque((67,205,209));tip[::2,::2]=[102,225,216,255]
    m.texture('soul_tip',tip,np.full((16,16),170,dtype=np.uint8),roughness=.5)
    m.shell('side_deepslate_caps','side_bone_frame','side_inset_front','side_inset_side','tabletop')
    for z in range(3,13):
        for x in range(3,13):
            m.carve(x,z,10);m.cap(x,z,10,'resonance_bed','sculk')
    # Four slate/sculk spires: broad dark roots taper by whole voxels to cyan tips.
    for s in range(4):
        for x,y,z,tex in [(1,13,1,'corner_deepslate'),(2,13,1,'corner_deepslate'),(1,13,2,'corner_deepslate'),(2,13,2,'sculk'),
                          (1,14,1,'sculk'),(1,14,2,'sculk'),(1,15,1,'soul_tip')]:
            p=(x,y,z)
            for _ in range(s):p=(15-p[2],p[1],p[0])
            m.box(p,(p[0]+1,p[1]+1,p[2]+1),tex,top=tex,role='spire')
        # The existing top prongs retain the shrieker silhouette above the quieter side.
        for x,y,z in [(4,13,2),(4,14,2),(4,13,3)]:
            p=(x,y,z)
            for _ in range(s):p=(15-p[2],p[1],p[0])
            m.box(p,(p[0]+1,p[1]+1,p[2]+1),'bone',top='bone',role='prong')
    m.box((7,12,7),(9,16,9),'soul_crystal','floating_center',top='soul_tip',role='crystal')
    m.animate('floating_center','position',[(t,v) for t,v in [(0,0),(1.5,-1),(3,0),(4.5,-1),(6,0),(7.5,-1),(9,0),(10.5,-1),(12,0)]])
    for i,(x,z,h) in enumerate([(5,5,3),(10,5,2),(10,10,3),(5,10,2)]):
        group=f'floating_satellite_{i+1}'
        m.box((x,12,z),(x+1,12+h,z+1),'soul_crystal',group,top='soul_tip',role='crystal')
        # Alternate phases while retaining a full voxel of clearance above the bed.
        # Keep the exported rest pose aligned; animation starts at the authored pose.
        keys=[(0,0),(1+i*.25,-1),(2+i*.25,0),(4+i*.25,-1),(5+i*.25,0),
              (7+i*.25,-1),(8+i*.25,0),(10+i*.25,-1),(11+i*.25,0),(12,0)]
        m.animate(group,'position',keys)
    m.save('Polished deepslate uprights with quiet blue-gray corner capitals and continuous light edges based on enchanting-table diamond grayscale. A single lower catalyst-like bone fan; upper side forks and cyan cap dots removed. One connected central vein in the sculk backboard. Square chamber Y=10, deck Y=13, shrieker top prongs and five separately bobbing crystals. Maximum static height Y=16.')


if __name__=='__main__':
    make_black_gold()
    make_sculk()
