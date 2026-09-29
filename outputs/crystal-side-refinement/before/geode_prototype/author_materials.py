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
out=HERE/'textures';out.mkdir(exist_ok=True)
# Broad authored mineral facets; no procedural white noise.
facets=np.array([[0,0,1,1,1,0,-1,-1],[0,1,1,2,1,0,-1,0],[-1,0,1,1,0,-1,0,1],
                 [-1,-1,0,0,-1,0,1,1],[0,0,-1,-1,0,1,2,1],[1,1,0,-1,0,1,1,0],
                 [1,2,1,0,0,-1,0,0],[0,1,1,0,-1,-1,0,1]],dtype=float)
facets=np.repeat(np.repeat(facets,2,0),2,1)
for name,hexcolor in palette.items():
    rgb=np.array([int(hexcolor[i:i+2],16) for i in (1,3,5)])
    crystal=name.startswith('bud_') or name=='vein'
    a=np.zeros((16,16,4),dtype='uint8');a[:,:,:3]=np.clip(rgb+facets[:,:,None]*(9 if crystal else 3),0,255);a[:,:,3]=255
    seed=np.zeros_like(a);seed[:,:,1]=10
    if crystal:
        base={'bud_lilac':50,'bud_pink':80,'bud_silver':115,'vein':45}[name]
        seed[:,:,3]=np.clip(base+facets*12,20,160)
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
