"""Export exposed geode surfaces to the registered crystal_table block/item models."""
from pathlib import Path
from collections import defaultdict
import json
import shutil
import sys
import numpy as np
from PIL import Image

HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[1]
sys.path.insert(0,str(HERE.parent/'tools'))
from export_refined_assets import clipped_faces,model_element,write_json
RES=ROOT/'src/main/resources/assets/convert_table'
bb=json.loads((HERE/'mother_rock_geode_16px.bbmodel').read_text(encoding='utf-8'))
names=[t['name'][:-4] for t in bb['textures']]
volume={}
for element in bb['elements']:
    lo,hi=element['from'],element['to'];name=names[element['faces']['north']['texture']]
    for x in range(lo[0],hi[0]):
        for y in range(lo[1],hi[1]):
            for z in range(lo[2],hi[2]):volume[x,y,z]=name
buckets=defaultdict(set)
for (x,y,z),name in volume.items():
    for dx,dy,dz in [(0,0,-1),(0,0,1),(-1,0,0),(1,0,0),(0,1,0),(0,-1,0)]:
        if (x+dx,y+dy,z+dz) in volume:continue
        plane=y+(dy>0) if dy else z+(dz>0) if dz else x+(dx>0)
        a,b=(x,z) if dy else (x,y) if dz else (z,y)
        buckets[(name,(dx,dy,dz),plane)].add((a,b))
faces=[]
for (name,normal,plane),remaining in buckets.items():
    while remaining:
        a,b=min(remaining);A=a+1;B=b+1
        while (A,b) in remaining:A+=1
        while all((u,B) in remaining for u in range(a,A)):B+=1
        for u in range(a,A):
            for v in range(b,B):remaining.remove((u,v))
        dx,dy,dz=normal
        if dy:points=[(a,plane,b),(a,plane,B),(A,plane,B),(A,plane,b)]
        elif dz:points=[(a,b,plane),(A,b,plane),(A,B,plane),(a,B,plane)]
        else:points=[(plane,b,a),(plane,B,a),(plane,B,A),(plane,b,A)]
        if np.dot(np.cross(np.subtract(points[1],points[0]),np.subtract(points[2],points[0])),normal)<0:points.reverse()
        uv=[]
        for x,y,z in points:
            if dy:u,v=x,z
            elif dz:u,v=(x if dz<0 else 16-x),16-y
            else:u,v=(z if dx>0 else 16-z),16-y
            uv.append((u,v))
        faces.append({'vertices':points,'uv':uv,'texture':name})
stats=json.loads((HERE/'model_stats.json').read_text(encoding='utf-8'))
write_json(HERE/'scene.json',{'variant':'geode','overall_dimensions':[16,16,16],'textures':names,
                            'side_layers':stats['side_layers'],'carving_pattern':stats['carving_pattern'],
                            'parts':{'static':{'group':'static','faces':faces}},'animations':{}})
maps={n:(np.array(Image.open(HERE/'textures'/f'{n}.png').convert('RGBA')),
         np.array(Image.open(HERE/'textures'/f'{n}_s.png').convert('RGBA'))) for n in names}
exported=[q for f in faces for q in clipped_faces(f,*maps[f['texture']])]
model={'parent':'minecraft:block/block','ambientocclusion':True,
       'textures':{n:f'convert_table:block/crystal_table/{n}' for n in names},
       'elements':[model_element(f) for f in exported]}
model['textures']['particle']='convert_table:block/crystal_table/calcite'
write_json(RES/'models/block/crystal_table.json',model)
write_json(RES/'models/item/crystal_table.json',{'parent':'convert_table:block/crystal_table'})
write_json(HERE/'runtime_faces.json',exported)
out=RES/'textures/block/crystal_table';out.mkdir(parents=True,exist_ok=True)
for name in names:
    for suffix in ('','_n','_s'):shutil.copy2(HERE/'textures'/f'{name}{suffix}.png',out/f'{name}{suffix}.png')
write_json(HERE/'runtime_export.json',{'block_model':'convert_table:block/crystal_table','materials':len(names),
                                   'surface_quads':len(faces),'runtime_quads':len(exported),'voxel_count':len(volume)})
print('GEODE_RUNTIME_OK',len(exported),'quads,',len(names),'material sets')
