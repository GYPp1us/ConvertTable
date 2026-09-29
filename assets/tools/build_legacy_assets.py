"""Deterministic, code-native Minecraft geometry and technical material-map authoring."""
from pathlib import Path
import base64
import json
import math
import uuid
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'assets'
RES = ROOT / 'src/main/resources'
OUT = RES / 'assets/convert_table'
SIZE = (1024, 512)
TILE = 256
MATERIALS = ['stone', 'trim', 'band', 'inset', 'crystal', 'rune', 'accent', 'pool']
VARIANTS = {
    'black_gold': {'stone': (39, 43, 52), 'trim': (181, 128, 48), 'band': (146, 83, 52), 'inset': (22, 20, 31), 'crystal': (153, 65, 233), 'rune': (181, 128, 48), 'accent': (80, 78, 83), 'pool': (93, 30, 161)},
    'end': {'stone': (211, 211, 159), 'trim': (154, 107, 166), 'band': (177, 137, 181), 'inset': (27, 21, 40), 'crystal': (198, 93, 251), 'rune': (167, 76, 206), 'accent': (232, 225, 175), 'pool': (107, 31, 167)},
    'sculk': {'stone': (32, 44, 53), 'trim': (165, 169, 142), 'band': (21, 70, 76), 'inset': (12, 28, 33), 'crystal': (20, 223, 229), 'rune': (16, 186, 197), 'accent': (51, 65, 73), 'pool': (8, 114, 134)},
}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'convert_table/' + name))


def textures(variant, palette):
    rng = np.random.default_rng(719 + list(VARIANTS).index(variant))
    color = np.zeros((512, 1024, 4), dtype=np.uint8)
    normal = np.zeros_like(color)
    spec = np.zeros_like(color)
    yy, xx = np.mgrid[:256, :256]
    for i, mat in enumerate(MATERIALS):
        coarse = np.repeat(np.repeat(rng.uniform(-1, 1, (32, 32)), 8, 0), 8, 1)
        fine = rng.normal(0, .6, (256, 256))
        stone = mat in ('stone', 'accent', 'inset')
        metal = variant == 'black_gold' and mat in ('trim', 'band', 'rune')
        glow = mat in ('crystal', 'pool') or mat == 'rune' and variant != 'black_gold'
        base = np.array(palette[mat])
        h = 220 + coarse * (13 if stone else 4) + fine
        rgb = base + coarse[..., None] * (12 if stone else 17) + fine[..., None]
        smooth = np.full((256, 256), 185 if metal else 60 if stone else 112, dtype=float)
        if stone:
            # Staggered masonry seams; fine pits remain subordinate to the pixel-art scale.
            seam = (yy % 64 < 3) | ((xx + (yy // 64 % 2) * 48) % 96 < 3)
            pits = rng.random((256, 256)) < .035
            h -= seam * 27 + pits * 9
            rgb -= seam[..., None] * 10 + pits[..., None] * 3
        elif metal:
            scratch = (rng.random((256, 256)) < .008) | ((xx % 61 == 1) & (yy % 83 < 32))
            h -= scratch * 9
            rgb += scratch[..., None] * 12
            smooth -= scratch * 45
        if glow:
            if mat == 'pool':
                radius = np.hypot(xx - 127.5, yy - 127.5) / 128
                angular = np.arctan2(yy - 127.5, xx - 127.5)
                energy = np.clip(1-radius, 0, 1) ** 2 + .22 * (np.sin(radius * 35 + angular * 4) > .6)
            else:
                energy = .4 + .45 * (np.sin(xx // 8 * .6 + yy // 8 * .35) + 1) / 2
            rgb = rgb * (.6 + energy[..., None] * .7)
            rgb += energy[..., None] * np.array([38, 40, 35])
            smooth[:] = 150
            h = 229 + coarse * 2
        gy, gx = np.gradient(h / 255)
        nx, ny = -gx * 2.3, gy * 2.3  # DirectX Y- convention
        nz = np.ones_like(nx)
        length = np.sqrt(nx*nx + ny*ny + nz*nz)
        n = np.stack(((nx/length*.5+.5)*255, (ny/length*.5+.5)*255,
                      np.clip(245 - (230-h)*.6, 185, 255), np.clip(h, 1, 255)), -1)
        s = np.zeros((256, 256, 4))
        s[..., 0] = np.clip(smooth + coarse * 8, 0, 255)
        s[..., 1] = (234 if mat == 'band' else 231) if metal else 10
        s[..., 2] = 0 if metal or glow else 35 if variant == 'end' else 16
        s[..., 3] = np.clip(100 + energy * 135, 0, 254) if glow else 0
        rgba = np.concatenate((np.clip(rgb, 0, 255), np.full((256, 256, 1), 255)), -1)
        y, x = i // 4 * 256, i % 4 * 256
        color[y:y+256, x:x+256] = rgba.astype('uint8')
        normal[y:y+256, x:x+256] = n.astype('uint8')
        spec[y:y+256, x:x+256] = s.astype('uint8')
    for suffix, pixels in [('', color), ('_n', normal), ('_s', spec)]:
        for folder in [ASSETS/'textures', OUT/'textures/block']:
            folder.mkdir(parents=True, exist_ok=True)
            Image.fromarray(pixels).save(folder/f'{variant}{suffix}.png')
    return color


class Model:
    def __init__(self, variant):
        self.variant = variant
        self.cubes = []

    def box(self, name, a, b, mat='stone', group='static', angle=0, pivot=(8, 13.6, 8)):
        assert all(b[i] > a[i] for i in range(3)), name
        self.cubes.append(dict(name=name, **{'from': list(a), 'to': list(b)}, material=mat,
                               group=group, angle=angle, pivot=list(pivot)))

    def side(self, name, a, b, mat='stone'):
        for n in range(4):
            self.box(name+f'_{n}', a, b, mat, angle=n*90)

    def build(self):
        v = self.variant
        # Full lower platform, hollow upper cavity and inset side walls.
        self.box('foundation', (.3, 0, .3), (15.7, 1.3, 15.7))
        self.box('cavity_floor', (2, 1.3, 2), (14, 11.8, 14), 'inset')
        self.side('bottom_rail', (2, .4, .15), (14, 2, 1.7))
        self.side('bottom_inlay', (3, .65, .08), (13, 1.05, .16), 'band')
        self.side('recessed_panel', (2.4, 2, .8), (13.6, 12.9, 1.6), 'inset')
        self.side('top_rail', (2, 12.8, .25), (14, 14.4, 2.6))
        self.side('top_strap', (3.2, 14.4, .7), (12.8, 14.7, 1.65), 'band')
        # Top frame has an 9.2-unit aperture; rotor's swept radius is below 4.25.
        self.side('socket_lip', (3.0, 12.2, 2.0), (13.0, 13.6, 3.4))
        for x in (.25, 12.75):
            for z in (.25, 12.75):
                k=f'{x}_{z}'
                self.box('foot_'+k, (x, 0, z), (x+3, 2.4, z+3), 'accent' if v=='sculk' else 'band' if v=='black_gold' else 'stone')
                self.box('pillar_'+k, (x+.3, 2.4, z+.3), (x+2.7, 12.7, z+2.7))
                self.box('capital_'+k, (x-.1, 12.4, z-.1), (x+3.1, 15.0, z+3.1), 'stone' if v=='sculk' else 'trim')
                self.box('cap_inlay_'+k, (x+.6, 15.0, z+.6), (x+2.4, 15.25, z+2.4), 'band')
                self.box('cap_jewel_'+k, (x+1, 15.25, z+1), (x+2, 15.5 if v=='black_gold' else 16.3, z+2), 'trim' if v=='black_gold' else 'crystal')
                if v=='sculk':
                    self.box('spire_'+k, (x+.7, 15, z+.7), (x+2.3, 16.6, z+2.3))
                    self.box('spire_tip_'+k, (x+1.1, 16.6, z+1.1), (x+1.9, 17.8, z+1.9), 'crystal')
        # Deep framed vertical energy windows, bracket shoulders and readable glyphs.
        self.side('window_recess', (6.6, 3.0, .55), (9.4, 12.8, 1.0), 'band')
        self.side('window_core', (7.35, 3.7, .39), (8.65, 11.8, .65), 'crystal')
        for x in (6.65, 8.95):
            self.side('window_frame', (x, 3, .15), (x+.4, 12.8, .7), 'trim')
        for y in (3, 12.3):
            self.side('window_lintel', (6.65, y, .1), (9.35, y+.5, .8), 'trim')
        self.side('keystone', (7, 13.2, 0), (9, 14.9, 1.2), 'trim')
        if v=='sculk':
            for x in (3.2, 11.9):
                self.side('bone_tine', (x, 3.5, .35), (x+.9, 7.8, .9), 'trim')
                start,end = (x,6.9) if x < 8 else (9.1,x+.9)
                self.side('bone_branch', (start, 3.1, .3), (end, 4, .9), 'trim')
        for side_x in (4.2, 10.7):
            glyph=[(0,0),(1,0),(0,1),(0,2),(1,2),(1,3),(0,4),(1,4)]
            for j, (dx, dy) in enumerate(glyph):
                self.side(f'glyph_{side_x}_{j}', (side_x+dx*.4, 7+dy*.48, .72), (side_x+dx*.4+.38, 7+dy*.48+.44, .83), 'rune')
        for x in (4, 5.5, 7, 8.5, 10, 11.5):
            self.side('status_light', (x, 1.35, .06), (x+.45, 1.75, .2), 'rune')
        group='static' if v=='sculk' else 'rotor'
        # Tessellated cuboid ring: large circular recess is actual geometry.
        for n in range(24):
            self.box(f'basin_segment_{n}', (11.2, 12.0, 7.5), (12.05, 13.75, 8.5),
                     'band' if v=='black_gold' else 'trim' if v=='end' else 'accent', group, n*15)
        # Mosaic pool disk; horizontal strips follow the ring instead of filling its corners.
        for i in range(-7, 8):
            z = i*.42
            half = math.sqrt(max(0, 3.18**2-(abs(z)+.21)**2))
            self.box(f'pool_strip_{i}', (8-half, 12.12, 8+z-.21), (8+half, 12.22, 8+z+.21), 'pool', group)
        for n in range(4):
            self.box('ring_clasp_'+str(n), (10.75, 13.65, 7.65), (12.1, 14.15, 8.35), 'trim', group, n*90)
        if v=='sculk':
            for i, (x,z,height) in enumerate([(8,8,5),(6.3,8.8,2.5),(9.7,8.7,3),(8.3,6.3,2)]):
                g='floating_crystals'
                self.box('crystal_body_'+str(i), (x-.42,14.3,z-.42), (x+.42,14.3+height,z+.42), 'band', g)
                self.box('crystal_light_'+str(i), (x-.29,15.3,z-.44), (x+.29,14.3+height,z+.44), 'crystal', g)
                self.box('crystal_tip_'+str(i), (x-.24,14.3+height,z-.24), (x+.24,14.8+height,z+.24), 'crystal', g)
        return self


def face_data(cube):
    x0,y0,z0=cube['from']; x1,y1,z1=cube['to']
    # Outward CCW winding, viewed from outside.
    faces={
        'north': ([(x1,y1,z0),(x1,y0,z0),(x0,y0,z0),(x0,y1,z0)], (0,0,-1), (x1-x0,y1-y0)),
        'south': ([(x0,y1,z1),(x0,y0,z1),(x1,y0,z1),(x1,y1,z1)], (0,0,1), (x1-x0,y1-y0)),
        'west': ([(x0,y1,z0),(x0,y0,z0),(x0,y0,z1),(x0,y1,z1)], (-1,0,0), (z1-z0,y1-y0)),
        'east': ([(x1,y1,z1),(x1,y0,z1),(x1,y0,z0),(x1,y1,z0)], (1,0,0), (z1-z0,y1-y0)),
        'up': ([(x0,y1,z0),(x0,y1,z1),(x1,y1,z1),(x1,y1,z0)], (0,1,0), (x1-x0,z1-z0)),
        'down': ([(x0,y0,z1),(x0,y0,z0),(x1,y0,z0),(x1,y0,z1)], (0,-1,0), (x1-x0,z1-z0))}
    idx=MATERIALS.index(cube['material'])
    for direction,(vertices, normal, dimensions) in faces.items():
        w,h=[max(1,min(252,d*16)) for d in dimensions]
        # Stable per-face offset gives non-identical surfaces while preserving texel density.
        seed=uuid.UUID(uid(cube['name']+direction)).int
        u=idx%4*256 + 2 + seed % max(1,int(252-w))
        v=idx//4*256 + 2 + (seed//31) % max(1,int(252-h))
        if cube['material']=='pool' and direction in ('up','down'):
            # Continuous radial UV coordinates across every strip.
            u=idx%4*256+(x0-4.82)/6.36*256
            v=idx//4*256+(z0-4.82)/6.36*256
            w=(x1-x0)/6.36*256; h=(z1-z0)/6.36*256
        yield direction, vertices, normal, [u,v,u+w,v+h]


def rotate(points, angle, pivot):
    a=math.radians(angle); co,si=math.cos(a),math.sin(a)
    p=np.asarray(points,dtype=float)-pivot
    return p @ np.array([[co,0,-si],[0,1,0],[si,0,co]]) + pivot


def mesh(model, groups=None):
    result=[]
    for cube in model.cubes:
        if groups is not None and cube['group'] not in groups: continue
        for direction,vertices,normal,uv in face_data(cube):
            u,v,U,V=uv
            result.append(dict(vertices=rotate(vertices,cube['angle'],cube['pivot']).round(6).tolist(),
                normal=rotate([normal],cube['angle'],[0,0,0])[0].round(6).tolist(),
                uv=[[u/1024,v/512],[u/1024,V/512],[U/1024,V/512],[U/1024,v/512]],
                emissive=cube['material'] in ('crystal','pool') or cube['material']=='rune' and model.variant!='black_gold',
                group=cube['group']))
    return result


def block_json(model, include_dynamic=False):
    elements=[]
    for c in model.cubes:
        if not include_dynamic and c['group']!='static': continue
        faces={d:{'uv':[u/64,v/32,U/64,V/32], 'texture':'#atlas'} for d,_,_,(u,v,U,V) in face_data(c)}
        el={'from':c['from'],'to':c['to'],'faces':faces}
        if c['angle']: el['rotation']={'origin':c['pivot'],'axis':'y','angle':c['angle']}
        elements.append(el)
    return {'parent':'minecraft:block/block','ambientocclusion':True,
        'textures':{'atlas':f'convert_table:block/{model.variant}','particle':f'convert_table:block/{model.variant}'},'elements':elements}


def bbmodel(model):
    variant=model.variant
    elements=[]; groups={g:[] for g in ('static','rotor','floating_crystals')}
    for i,c in enumerate(model.cubes):
        ident=uid(variant+'/cube/'+str(i)); groups[c['group']].append(ident)
        faces={d:{'uv':uv,'texture':0} for d,_,_,uv in face_data(c)}
        elements.append({'name':c['name'],'uuid':ident,'type':'cube','from':c['from'],'to':c['to'],
            'origin':c['pivot'],'rotation':[0,c['angle'],0],'faces':faces,'box_uv':False,'rescale':False})
    outliner=[{'name':g,'uuid':uid(variant+'/'+g),'origin':[8,13.6,8], 'rotation':[0,0,0], 'children':children} for g,children in groups.items() if children]
    g='floating_crystals' if variant=='sculk' else 'rotor'
    duration=4 if variant=='sculk' else 12 if variant=='end' else 16
    keys=[]
    samples=[(0,0),(1,.45),(2,0),(3,-.45),(4,0)] if variant=='sculk' else [(0,0),(duration/4,90),(duration/2,180),(duration*3/4,270),(duration,360)]
    for time,value in samples:
        keys.append({'channel':'position' if variant=='sculk' else 'rotation','data_points':[{'x':'0','y':str(value),'z':'0'}], 'uuid':uid(variant+'/key/'+str(time)), 'time':time,'interpolation':'catmullrom' if variant=='sculk' else 'linear'})
    data={'meta':{'format_version':'4.10','model_format':'free','box_uv':False}, 'name':variant,
        'resolution':{'width':1024,'height':512},'elements':elements,'outliner':outliner,
        'textures':[{'name':variant+'.png','id':'0','uuid':uid(variant+'/texture'),'mode':'bitmap','uv_width':1024,'uv_height':512,
                     'source':'data:image/png;base64,'+base64.b64encode((ASSETS/'textures'/f'{variant}.png').read_bytes()).decode()}],
        'animations':[{'uuid':uid(variant+'/idle'),'name':'idle','loop':'loop','length':duration,'snapping':20,
            'animators':{uid(variant+'/'+g):{'name':g,'type':'bone','keyframes':keys}}}]}
    write_json(ASSETS/'blockbench'/f'{variant}.bbmodel',data)


def preview(model, atlas, size=700, time=0):
    """Orthographic triangle rasterizer; previews the actual exported mesh and UVs."""
    img=np.zeros((size,size,3),dtype=np.uint8); img[:]=[19,25,35]
    depth=np.full((size,size),-np.inf)
    right=np.array([.7071,0,-.7071]); up=np.array([-.4082,.8165,-.4082]); view=np.cross(right,up)
    light=np.array([-.25,.9,-.5]); light/=np.linalg.norm(light)
    scale=size/32
    for face in mesh(model):
        p=np.array(face['vertices']); n=np.array(face['normal']); uv=np.array(face['uv'])
        if face['group']=='rotor':
            angle=time*360/(12 if model.variant=='end' else 16)
            p=rotate(p,angle,[8,13.6,8]); n=rotate([n],angle,[0,0,0])[0]
        if face['group']=='floating_crystals': p[:,1]+=.45*math.sin(time*math.pi/2)
        if n@view<=0: continue
        p-=np.array([8,8.8,8]); xy=np.stack([p@right*scale+size/2,-p@up*scale+size/2],-1); z=p@view
        for ids in ([0,1,2],[0,2,3]):
            tri=xy[ids]; zz=z[ids]; tt=uv[ids]
            lo=np.maximum(np.floor(tri.min(0)).astype(int),0); hi=np.minimum(np.ceil(tri.max(0)).astype(int),size-1)
            if np.any(hi<lo):continue
            Y,X=np.mgrid[lo[1]:hi[1]+1,lo[0]:hi[0]+1]
            a,b,c=tri; den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
            if abs(den)<1e-8:continue
            w0=((b[1]-c[1])*(X+.5-c[0])+(c[0]-b[0])*(Y+.5-c[1]))/den
            w1=((c[1]-a[1])*(X+.5-c[0])+(a[0]-c[0])*(Y+.5-c[1]))/den
            w2=1-w0-w1; dz=w0*zz[0]+w1*zz[1]+w2*zz[2]
            target=depth[lo[1]:hi[1]+1,lo[0]:hi[0]+1]
            mask=(w0>=-1e-5)&(w1>=-1e-5)&(w2>=-1e-5)&(dz>target)
            tex=w0[...,None]*tt[0]+w1[...,None]*tt[1]+w2[...,None]*tt[2]
            tx=np.clip((tex[...,0]*1024).astype(int),0,1023); ty=np.clip((tex[...,1]*512).astype(int),0,511)
            shade=1.12 if face['emissive'] else .57+.43*max(0,n@light)
            colors=np.clip(atlas[ty,tx,:3]*shade,0,255).astype('uint8')
            dest=img[lo[1]:hi[1]+1,lo[0]:hi[0]+1]; dest[mask]=colors[mask]; target[mask]=dz[mask]
    return Image.fromarray(img)


def main():
    manifest={'format':'labPBR 1.3','atlas_size':SIZE,'material_tiles':MATERIALS,'variants':{}}
    cards=[]
    for variant,palette in VARIANTS.items():
        atlas=textures(variant,palette); model=Model(variant).build(); bbmodel(model)
        write_json(OUT/'models/block'/f'{variant}.json',block_json(model))
        write_json(OUT/'models/item'/f'{variant}.json',block_json(model,True))
        write_json(OUT/'items'/f'{variant}_conversion_table.json',{'model':{'type':'minecraft:model','model':f'convert_table:item/{variant}'}})
        write_json(OUT/'blockstates'/f'{variant}_conversion_table.json',{'variants':{'':{'model':f'convert_table:block/{variant}'}}})
        moving=mesh(model,{'rotor','floating_crystals'})
        write_json(OUT/'conversion_table'/f'{variant}.json',{'quads':moving})
        block=f'convert_table:{variant}_conversion_table'
        write_json(RES/'data/convert_table/loot_table/blocks'/f'{variant}_conversion_table.json',{
            'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':block}], 'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
        duration=4 if variant=='sculk' else 12 if variant=='end' else 16
        manifest['variants'][variant]={'cuboids':len(model.cubes),'dynamic_quads':len(moving),'period_seconds':duration,'pivot':[8,13.6,8], 'float_amplitude_units':.45 if variant=='sculk' else 0}
        folder=ASSETS/'previews';folder.mkdir(parents=True,exist_ok=True)
        card=preview(model,atlas);card.save(folder/f'{variant}.png');cards.append(card)
        frames=[preview(model,atlas,360,t*duration/24) for t in range(24)]
        frames[0].save(folder/f'{variant}_idle.gif',save_all=True,append_images=frames[1:],duration=round(duration*1000/24),loop=0)
    sheet=Image.new('RGB',(2100,780),(19,25,35)); draw=ImageDraw.Draw(sheet)
    try:font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',27)
    except OSError:font=ImageFont.load_default()
    for i,(variant,card) in enumerate(zip(VARIANTS,cards)):
        sheet.paste(card,(i*700,65));draw.text((i*700+35,24),variant.upper().replace('_',' '),font=font,fill=(220,220,215))
    draw.text((35,740),'GEOMETRY + ALBEDO / FIRST PASS / PBR REQUIRES IN-GAME VALIDATION',font=font,fill=(136,150,169))
    sheet.save(ASSETS/'previews/overview.png')
    write_json(ASSETS/'manifest.json',manifest)
    names={'black_gold':('Black Gold Conversion Table','黑金转换台'),'end':('End Conversion Table','末地转换台'),'sculk':('Sculk Conversion Table','幽匿转换台')}
    for i,lang in enumerate(('en_us','zh_cn')):
        write_json(OUT/'lang'/f'{lang}.json',{f'block.convert_table.{v}_conversion_table':n[i] for v,n in names.items()})
    write_json(RES/'data/minecraft/tags/block/mineable/pickaxe.json',{'replace':False,'values':[f'convert_table:{v}_conversion_table' for v in VARIANTS]})
    print(json.dumps(manifest,indent=2))


if __name__=='__main__':main()
