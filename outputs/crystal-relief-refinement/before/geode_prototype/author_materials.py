"""Author native crystal/stone albedo, emission and labPBR, then build with Blender."""
import ast
from pathlib import Path
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE.parent/'tools'))
from export_refined_assets import material_maps
tree=ast.parse((HERE/'build_geode.py').read_text(encoding='utf-8'))
palette=next(ast.literal_eval(n.value) for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='PALETTE' for t in n.targets))
carving=next(ast.literal_eval(n.value) for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='SIDE_CARVING' for t in n.targets))
out=HERE/'textures';out.mkdir(exist_ok=True)
# Broad authored mineral facets; no procedural white noise.
facets=np.array([[0,0,1,1,1,0,-1,-1],[0,1,1,2,1,0,-1,0],[-1,0,1,1,0,-1,0,1],
                 [-1,-1,0,0,-1,0,1,1],[0,0,-1,-1,0,1,2,1],[1,1,0,-1,0,1,1,0],
                 [1,2,1,0,0,-1,0,0],[0,1,1,0,-1,-1,0,1]],dtype=float)
facets=np.repeat(np.repeat(facets,2,0),2,1)


def side_material(name, rgba, seed):
    """Carve broad mineral planes and restrained crystal glints on the 16px grid.

    All four sides share this projection. The stone stays non-emissive; depth
    comes from the modeled returns and shallow cluster-aligned material relief.
    """
    rgb=rgba[:,:,:3].astype(float)
    rows,cols=np.indices((16,16))
    if name=='side_backing':
        # Quiet layered mineral grain, with two interrupted seams. No speckles.
        rgb += np.where((rows+cols//3)%7<2,-4,2)[:,:,None]
        for x,y in [(3,7),(3,8),(12,7),(12,8),(4,11),(11,11)]:
            rgb[y,x]-=8
    elif name=='side_relief':
        mask=np.zeros((16,16),dtype=bool)
        for row,pattern in enumerate(carving):
            for col,mark in enumerate(pattern):mask[row+6,col+2]=mark=='r'
        for y,x in zip(*np.where(mask)):
            # Cut faces catch light on the upper/left lip and darken below.
            rgb[y,x]+=12 if not mask[y-1,x] else -7
            if not mask[y,x-1]:rgb[y,x]+=4
            if not mask[y+1,x]:rgb[y,x]-=5
    elif name=='engraved_amethyst':
        # Dark violet roots, saturated facets and only a few lavender highlights.
        facet=(cols+2*rows)%7
        rgb=np.empty_like(rgb)
        rgb[:]=[125,64,179]
        rgb[facet<3]=[151,79,205]
        rgb[facet==3]=[182,112,226]
        rgb[facet==6]=[111,55,157]
        seed[:,:,3]=np.select([facet==3,facet<3,facet==6],[70,48,24],default=34)
    elif name=='engraved_glint':
        rgb[:]=[212,161,241]
        rgb[:,8:9]=[234,196,251]
        seed[:,:,3]=105
        seed[:,8:9,3]=138
    rgba[:,:,:3]=np.clip(rgb,0,255).astype('uint8')


for name,hexcolor in palette.items():
    rgb=np.array([int(hexcolor[i:i+2],16) for i in (1,3,5)])
    crystal=name.startswith(('bud_','engraved_')) or name=='vein'
    a=np.zeros((16,16,4),dtype='uint8');a[:,:,:3]=np.clip(rgb+facets[:,:,None]*(9 if crystal else 3),0,255);a[:,:,3]=255
    seed=np.zeros_like(a);seed[:,:,1]=10
    if name.startswith('bud_') or name=='vein':
        base={'bud_lilac':50,'bud_pink':80,'bud_silver':115,'vein':45}[name]
        seed[:,:,3]=np.clip(base+facets*12,20,160)
    if name.startswith(('side_','engraved_')):side_material(name,a,seed)
    normal,spec,rough,metal=material_maps('geode',name,a,seed)
    xy=normal[:,:,:2].astype(float)/255*2-1
    xyz=np.dstack([xy[:,:,0],-xy[:,:,1],np.sqrt(np.clip(1-(xy*xy).sum(2),0,1))])
    maps={'':a,'_n':normal,'_s':spec,'_emission':spec[:,:,3],'_roughness':rough,'_metal':metal,
          '_normal_preview':np.rint((xyz*.5+.5)*255).astype('uint8'),'_ao':normal[:,:,2],'_height':normal[:,:,3],
          '_sss':np.where(spec[:,:,2]>64,(spec[:,:,2].astype(float)-65)/190*255,0).astype('uint8')}
    for suffix,data in maps.items():Image.fromarray(data).save(out/f'{name}{suffix}.png')
sheet=Image.new('RGB',(1040,len(palette)*115+60),(27,29,36));d=ImageDraw.Draw(sheet)
font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',16)
for i,label in enumerate(['Albedo','Normal / AO / Height','Specular / SSS / Emission','Emission','Height']):d.text((200+i*165,16),label,fill='white',font=font)
for y,name in enumerate(palette):
    d.text((12,70+y*115),name,fill='white',font=font)
    for x,suffix in enumerate(['','_n','_s','_emission','_height']):
        im=Image.open(out/f'{name}{suffix}.png').convert('RGB').resize((96,96),Image.Resampling.NEAREST)
        sheet.paste(im,(215+x*165,50+y*115))
sheet.save(HERE/'material_channels.png')
print('Authored',len(palette),'geode materials with crystal emission and PBR')
