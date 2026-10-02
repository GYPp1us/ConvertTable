"""Publish the approved 16x meshes and authored labPBR materials to Fabric resources.

No albedo is repainted here. Whole-texel material masks, vanilla model faces, item
rest poses and animated atlas meshes all come from the approved scene.json files.
"""
from pathlib import Path
from collections import defaultdict
import json
import math
import shutil
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'assets'
RES=ROOT/'src/main/resources'
OUT=RES/'assets/convert_table'
RUNTIME=ASSETS/'runtime'
SOURCES={'black_gold':ASSETS/'style_refinement/black_gold',
         'end':ASSETS/'end_refinement','sculk':ASSETS/'style_refinement/sculk'}
NAMES={'black_gold':('Black Gold Conversion Table','黑金转换台'),
       'end':('End Conversion Table','末地转换台'),'sculk':('Sculk Conversion Table','幽匿转换台')}


def write_json(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


def rgba(path):return np.array(Image.open(path).convert('RGBA'))


def material_maps(variant,name,albedo,original_s):
    """Paint material response by surface identity and the existing pixel clusters.

    _s: perceptual smoothness, F0/metal ID, porosity, emission.
    Cluster-aligned shallow relief, inspired by the local Jiangyuan pack's material separation.
    Reference: https://shaderlabs.org/wiki/LabPBR_Material_Standard
    """
    if variant=='geode':name=name.removeprefix('geode_')
    rgb=albedo[:,:,:3].astype(float);visible=albedo[:,:,3]>127
    lum=rgb@np.array([.2126,.7152,.0722])/255
    gold=original_s[:,:,1]==231
    emission=np.where(visible,original_s[:,:,3],0).astype('uint8')
    emission=np.minimum(emission,254)
    rough=np.full((16,16),.38);f0=np.full((16,16),10,dtype='uint8')
    porosity=np.full((16,16),26,dtype='uint8')
    variation=(lum-lum[visible].mean()) if visible.any() else np.zeros((16,16))

    if variant=='end':
        if name in ('end_stone','top_frame'):
            rough[:]=.64;porosity[:]=55;f0[:]=8
        elif name in ('purpur','side_purpur_guards','ring_glow'):
            rough[:]=.28;porosity[:]=24
        elif name in ('obsidian','side_inset_front','side_inset_side'):
            rough[:]=.12;porosity[:]=2;f0[:]=12
        elif name=='side_midframe':
            ivory=(rgb[:,:,0]>105)&(rgb[:,:,1]>100)
            rough[:]=.12;porosity[:]=2;f0[:]=12
            rough[ivory]=.64;porosity[ivory]=55;f0[ivory]=8
        if name in ('pool','crystal'):
            rough[:]=.07;porosity[:]=135;f0[:]=14
        elif 'inset' in name:
            rough[emission>0]=.19;porosity[emission>0]=0
    elif variant=='sculk':
        rough[:]=.42;porosity[:]=25
        if name in ('sculk','side_inset_front','side_inset_side','resonance_bed'):
            rough[:]=.50;porosity[:]=38;f0[:]=8
            rough[emission>0]=.15;porosity[emission>0]=110
        if name in ('bone','side_bone_frame'):
            bone=(rgb[:,:,0]>85)&(rgb[:,:,1]>85)&(rgb[:,:,0]>rgb[:,:,1]*.72)
            rough[bone]=.62;porosity[bone]=46
        if name in ('corner_deepslate','side_deepslate_caps'):
            rough[:]=.36;porosity[:]=28
        if name in ('soul_crystal','soul_tip'):
            rough[:]=.07 if name=='soul_crystal' else .045
            porosity[:]=145;f0[:]=14
    elif variant=='geode':
        rough[:]=.56;porosity[:]=44;f0[:]=10
        if name.startswith('basalt'):
            rough[:]=.48;porosity[:]=32
        elif name.startswith('lining'):
            rough[:]=.51;porosity[:]=38
        elif name=='side_backing':
            rough[:]=.48;porosity[:]=36;f0[:]=10
        elif name=='side_relief':
            rough[:]=.37;porosity[:]=28;f0[:]=10
        elif name.startswith(('bud_','engraved_')) or name=='vein':
            rough[:]=.065;porosity[:]=155;f0[:]=14
            if name=='engraved_amethyst':rough[:]=.13;porosity[:]=120
    else:
        rough[:]=.32;porosity[:]=25
        if name=='side_carved_frame':
            rough[4:11,4:12]=.43  # Chiseled snout is rough blackstone, never metal.
        if name in ('pit_wall','pit_steps'):
            rough[:]=.56;porosity[:]=30
        if name=='amethyst_core':
            rough[:]=.10;porosity[:]=0;f0[:]=14
        # Gold grains have smooth bright faces and rougher weathered edges.
        rough[gold]=.045+.13*(1-lum[gold])
        porosity[gold]=0;f0[gold]=231

    rough=np.clip(rough-variation*.12,.035,.90)
    smooth=np.rint((1-np.sqrt(rough))*255).astype('uint8')
    # Reuse the authored 16px clusters; never sample or redistribute pack artwork.
    # The local reference has relief in _n RG and AO/height in BA; retain that layout.
    low=float(lum[visible].min()) if visible.any() else 0
    high=float(lum[visible].max()) if visible.any() else 1
    cluster=(lum-low)/max(high-low,.04)
    amplitude=np.full((16,16),.075)
    amplitude[gold]=.024
    crystal=name in ('pool','crystal','soul_crystal','soul_tip','amethyst_core','vein') or name.startswith(('bud_','engraved_'))
    if crystal:amplitude[:]=.045
    if variant=='geode' and name.startswith('side_'):
        amplitude[:]=.018 if name=='side_backing' else .028
    height=1-amplitude*(1-cluster)
    if variant=='geode' and name=='side_relief':
        # The former raised gray ornament is now a shallow engraved ground pattern.
        height-=.016
    # Neighbour samples outside an alpha mask use the surface's own height.
    def neighbour(dy,dx):
        h=np.roll(height,(dy,dx),(0,1));v=np.roll(visible,(dy,dx),(0,1))
        return np.where(v,h,height)
    nx=(neighbour(0,-1)-neighbour(0,1))*2.5
    ny=(neighbour(-1,0)-neighbour(1,0))*2.5
    scale=np.sqrt(1+nx*nx+ny*ny)
    normal=np.empty_like(albedo)
    normal[:,:,0]=np.rint((nx/scale*.5+.5)*255).astype('uint8')
    normal[:,:,1]=np.rint((ny/scale*.5+.5)*255).astype('uint8')
    normal[:,:,2]=np.rint(232+cluster*23).astype('uint8')
    normal[:,:,3]=np.rint(height*255).astype('uint8')
    normal[~visible]=[128,128,255,255]
    spec=np.stack([smooth,f0,porosity,emission],axis=2)
    spec[~visible]=[0,10,0,0]
    return normal,spec,np.rint(rough*255).astype('uint8'),(gold&visible).astype('uint8')*255


def emission_level(value):
    return min(15,round(int(value)*15/170))


def clipped_faces(face,albedo,spec):
    """Clip cutouts and split differing light levels at texel boundaries, then merge.

    This also resolves wrapped editor UVs (e.g. the End crystal) into native 0..16
    UVs. Each face keeps the same affine texture mapping; no texture resampling.
    """
    p=np.array(face['vertices'],dtype=float);uv=np.array(face['uv'],dtype=float)
    normal=np.cross(p[1]-p[0],p[2]-p[0]);length=np.linalg.norm(normal)
    if length<1e-8:return []
    normal/=length
    transform=np.linalg.lstsq(np.column_stack([uv,np.ones(4)]),p,rcond=None)[0]
    lo=np.rint(uv.min(axis=0)).astype(int);hi=np.rint(uv.max(axis=0)).astype(int)
    buckets=defaultdict(set)
    for v in range(lo[1],hi[1]):
        for u in range(lo[0],hi[0]):
            x,y=u%16,v%16
            if albedo[y,x,3]>127:
                buckets[(u//16,v//16,emission_level(spec[y,x,3]))].add((u,v))
    result=[]
    for (tile_u,tile_v,light),remaining in buckets.items():
        while remaining:
            u,v=min(remaining,key=lambda q:(q[1],q[0]));U=u+1;V=v+1
            while (U,v) in remaining:U+=1
            while all((x,V) in remaining for x in range(u,U)):V+=1
            for x in range(u,U):
                for y in range(v,V):remaining.remove((x,y))
            coords=np.array([(u,v),(U,v),(U,V),(u,V)],dtype=float)
            points=np.column_stack([coords,np.ones(4)])@transform
            # Restore exact authoring coordinates after affine arithmetic.
            points=np.round(points,6)
            if np.dot(np.cross(points[1]-points[0],points[2]-points[0]),normal)<0:
                points=points[::-1];coords=coords[::-1]
            coords-=np.array([tile_u*16,tile_v*16])
            result.append({'vertices':points.tolist(),'uv':coords.tolist(),
                           'normal':np.rint(normal).tolist(),'texture':face['texture'],
                           'light_emission':light})
    return result


# Vanilla FaceInfo corner order, checked independently with the game's Java classes.
FACE_CORNERS={
 'down':[(0,0,1),(0,0,0),(1,0,0),(1,0,1)],
 'up':[(0,1,0),(0,1,1),(1,1,1),(1,1,0)],
 'north':[(1,1,0),(1,0,0),(0,0,0),(0,1,0)],
 'south':[(0,1,1),(0,0,1),(1,0,1),(1,1,1)],
 'west':[(0,1,0),(0,0,0),(0,0,1),(0,1,1)],
 'east':[(1,1,1),(1,0,1),(1,0,0),(1,1,0)]}
DIRECTIONS={(0,-1,0):'down',(0,1,0):'up',(0,0,-1):'north',
            (0,0,1):'south',(-1,0,0):'west',(1,0,0):'east'}


def model_element(face):
    p=np.array(face['vertices']);uv=np.array(face['uv']);lo=p.min(0);hi=p.max(0)
    direction=DIRECTIONS[tuple(face['normal'])]
    canonical=np.array([np.where(c,hi,lo) for c in FACE_CORNERS[direction]])
    wanted=np.array([uv[np.argmin(np.linalg.norm(p-point,axis=1))] for point in canonical])
    u,v=uv.min(0);U,V=uv.max(0)
    for left,right in ((u,U),(U,u)):
        for top,bottom in ((v,V),(V,v)):
            base=np.array([(left,top),(left,bottom),(right,bottom),(right,top)])
            for quarter in range(4):
                if np.allclose(base[(np.arange(4)+quarter)%4],wanted,atol=1e-6):
                    entry={'uv':[float(left),float(top),float(right),float(bottom)],'texture':'#'+face['texture']}
                    if quarter:entry['rotation']=quarter*90
                    return {'from':lo.tolist(),'to':hi.tolist(),'shade':face['light_emission']==0,
                            'light_emission':face['light_emission'],'faces':{direction:entry}}
    raise ValueError('Cannot preserve authoring UV orientation')


def export(variant,folder):
    scene=json.loads((folder/'scene.json').read_text(encoding='utf-8'))
    texdir=OUT/'textures/block'/variant;texdir.mkdir(parents=True,exist_ok=True)
    proof=ASSETS/'labpbr'/variant;proof.mkdir(parents=True,exist_ok=True)
    textures={};summary={}
    for name in scene['textures']:
        src=folder/'textures';color=rgba(src/f'{name}.png');seed=rgba(src/f'{name}_s.png')
        n,s,rough,metal=material_maps(variant,name,color,seed)
        for suffix,data in [('',color),('_n',n),('_s',s)]:
            Image.fromarray(data).save(texdir/f'{name}{suffix}.png')
            if suffix:Image.fromarray(data).save(src/f'{name}{suffix}.png')
        # Editable channels beside the authoring textures, without runtime atlas clutter.
        xy=n[:,:,:2].astype(float)/255*2-1
        xyz=np.dstack([xy[:,:,0],-xy[:,:,1],np.sqrt(np.clip(1-(xy*xy).sum(2),0,1))])
        normal_preview=np.rint((xyz*.5+.5)*255).astype('uint8')
        for suffix,data in [('_roughness',rough),('_metal',metal),('_f0',s[:,:,1]),('_smoothness',s[:,:,0]),
                            ('_normal_preview',normal_preview),('_ao',n[:,:,2]),('_height',n[:,:,3]),
                            ('_sss',np.where(s[:,:,2]>64,(s[:,:,2].astype(float)-65)/190*255,0).astype('uint8'))]:
            Image.fromarray(data).save(proof/f'{name}{suffix}.png')
            Image.fromarray(data).save(src/f'{name}{suffix}.png')
        textures[name]=(color,s)
        visible=color[:,:,3]>127
        summary[name]={'roughness_range':[round(float(rough[visible].min()/255),3),round(float(rough[visible].max()/255),3)],
                       'gold_texels':int((metal>0).sum()),'emissive_texels':int(((s[:,:,3]>0)&visible).sum())}

    groups=defaultdict(list)
    seen=set()
    for part in scene['parts'].values():
        for f in part['faces']:
            color,spec=textures[f['texture']]
            for q in clipped_faces(f,color,spec):
                key=(part['group'],q['texture'],q['light_emission'],tuple(sorted(tuple(p+uv) for p,uv in zip(q['vertices'],q['uv']))),tuple(q['normal']))
                if key in seen:continue
                seen.add(key);groups[part['group']].append(q)
    if variant=='end':
        animations={'rotor':{'kind':'rotation','pivot':scene['pivot'],'keys':[[t,t*30] for t in (0,3,6,9,12)]}}
    else:animations=scene['animations']
    all_faces=[q for faces in groups.values() for q in faces]
    for kind,faces in [('block',groups['static']),('item',all_faces)]:
        model={'parent':'minecraft:block/block','ambientocclusion':True,
               'textures':{name:f'convert_table:block/{variant}/{name}' for name in textures},
               'elements':[model_element(face) for face in faces]}
        particle={'end':'end_stone','black_gold':'blackstone','sculk':'deepslate'}[variant]
        model['textures']['particle']=f'convert_table:block/{variant}/{particle}'
        write_json(OUT/'models'/kind/f'{variant}.json',model)
        write_json(RUNTIME/f'{variant}_{kind}_faces.json',faces)
    moving=[]
    for group,faces in groups.items():
        if group=='static':continue
        anim=animations[group]
        moving.append({'name':group,'kind':anim['kind'],'pivot':anim['pivot'],
                       'duration':12,'keyframes':anim['keys'],
                       'quads':[dict(q,texture=f'convert_table:block/{variant}/{q["texture"]}',
                                     uv=[[u/16,v/16] for u,v in q['uv']]) for q in faces]})
    write_json(OUT/'conversion_table'/f'{variant}.json',{'format':2,'groups':moving})
    write_json(OUT/'items'/f'{variant}_conversion_table.json',{'model':{'type':'minecraft:model','model':f'convert_table:item/{variant}'}})
    write_json(OUT/'blockstates'/f'{variant}_conversion_table.json',{'variants':{
        f'facing={direction}':{'model':f'convert_table:block/{variant}','y':angle}
        for direction,angle in [('north',0),('east',90),('south',180),('west',270)]}})
    block=f'convert_table:{variant}_conversion_table'
    write_json(RES/'data/convert_table/loot_table/blocks'/f'{variant}_conversion_table.json',{
        'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':block}],
        'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    # Remove obsolete first-pass runtime atlases only; source art remains archived.
    for suffix in ('','_n','_s'):
        old=OUT/'textures/block'/f'{variant}{suffix}.png'
        if old.exists():old.unlink()
    material_sheet(variant,textures,proof)
    return {'source':str(folder.relative_to(ROOT)).replace('\\','/'),'dimensions':[16,16,16],
            'static_quads':len(groups['static']),'item_quads':len(all_faces),
            'animated_groups':{g:len(q) for g,q in groups.items() if g!='static'},'materials':summary}


def material_sheet(variant,textures,folder):
    names=list(textures);cell=116
    sheet=Image.new('RGB',(1145,85+len(names)*cell),(26,28,35));d=ImageDraw.Draw(sheet)
    font=lambda size:ImageFont.truetype('C:/Windows/Fonts/arial.ttf',size)
    d.text((18,18),variant.upper()+' / NATIVE 16x MATERIAL CHANNELS',font=font(22),fill='white')
    for i,label in enumerate(['ALBEDO','ROUGHNESS','SMOOTHNESS','METAL','F0 / METAL ID','EMISSION','NORMAL','HEIGHT','AO']):
        d.text((12+126*i,57),label,font=font(13),fill=(200,205,216))
    for j,name in enumerate(names):
        color,s=textures[name]
        rough=np.array(Image.open(folder/f'{name}_roughness.png'))
        maps=[Image.fromarray(color),Image.fromarray(rough),Image.fromarray(s[:,:,0]),
              Image.fromarray(((s[:,:,1]==231)*255).astype('uint8')),Image.fromarray(s[:,:,1]),Image.fromarray(s[:,:,3]),
              Image.open(folder/f'{name}_normal_preview.png'),Image.open(folder/f'{name}_height.png'),Image.open(folder/f'{name}_ao.png')]
        for i,im in enumerate(maps):
            im=im.convert('RGBA').resize((88,88),Image.Resampling.NEAREST)
            sheet.paste(im,(12+126*i,84+j*cell),im)
        d.text((12,174+j*cell),name,font=font(13),fill=(168,179,193))
    sheet.save(folder/'material_channels.png')


def recipes():
    definitions={
        'black_gold':(['GBG','BAB','BCB'],{'G':'gold_ingot','B':'polished_blackstone','A':'amethyst_shard','C':'chiseled_polished_blackstone'}),
        'end':(['PEP','OAO','PEP'],{'P':'purpur_block','E':'end_stone','O':'obsidian','A':'amethyst_shard'}),
        'sculk':(['DSD','SCS','DED'],{'D':'polished_deepslate','S':'sculk','C':'sculk_catalyst','E':'echo_shard'})}
    for variant,(pattern,keys) in definitions.items():
        name=variant+'_conversion_table'
        write_json(RES/'data/convert_table/recipe'/f'{name}.json',{
            'type':'minecraft:crafting_shaped','category':'building','pattern':pattern,
            'key':{k:'minecraft:'+v for k,v in keys.items()},'result':{'id':'convert_table:'+name,'count':1}})
        ingredients=list(keys.values())
        write_json(RES/'data/convert_table/advancement/recipes/decorations'/f'{name}.json',{
            'parent':'minecraft:recipes/root','criteria':{
                'has_material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':'minecraft:'+ingredients[0]}]}},
                'has_the_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipes':['convert_table:'+name]}}},
            'requirements':[['has_material','has_the_recipe']],
            'rewards':{'recipes':['convert_table:'+name]}})


def main():
    manifest={'format':'refined-16x-v2','material_standard':'labPBR 1.3','variants':{}}
    for variant,folder in SOURCES.items():
        manifest['variants'][variant]=export(variant,folder)
        print(variant+': '+str({k:v for k,v in manifest['variants'][variant].items() if k!='materials'}))
    recipes()
    for i,lang in enumerate(('en_us','zh_cn')):
        path=OUT/'lang'/f'{lang}.json'
        translations=json.loads(path.read_text(encoding='utf-8')) if path.exists() else {}
        translations.update({f'block.convert_table.{v}_conversion_table':n[i] for v,n in NAMES.items()})
        write_json(path,translations)
    tag=RES/'data/minecraft/tags/block/mineable/pickaxe.json'
    values=json.loads(tag.read_text(encoding='utf-8')).get('values',[]) if tag.exists() else []
    values=list(dict.fromkeys(values+[f'convert_table:{v}_conversion_table' for v in SOURCES]))
    write_json(tag,{'replace':False,'values':values})
    write_json(ASSETS/'runtime/manifest.json',manifest)
    if (ASSETS/'geode_prototype/mother_rock_geode_16px.bbmodel').exists():
        import runpy
        runpy.run_path(str(ASSETS/'geode_prototype/publish_geode.py'),run_name='__main__')
    pedestal_builder=ASSETS/'concepts/catalyst_table/build_pedestal_model.py'
    if pedestal_builder.exists():
        import runpy
        runpy.run_path(str(pedestal_builder),run_name='__main__')


if __name__=='__main__':main()
