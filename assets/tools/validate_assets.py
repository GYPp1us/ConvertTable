"""Validate shipped 16x geometry, immutable albedo and labPBR material semantics."""
import json
from pathlib import Path
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT/'src/main/resources/assets/convert_table'
manifest = json.loads((ROOT/'assets/runtime/manifest.json').read_text())
total_faces = total_textures = 0

def rgba(path):
    with Image.open(path) as im:
        assert im.size == (16, 16), f'Not native 16x: {path}'
        return np.array(im.convert('RGBA'))

def surface_texels(faces):
    """Compare solid surfaces independently of rectangle merging and UV splits."""
    result=set()
    for face in faces:
        p=np.array(face['vertices']);normal=np.cross(p[1]-p[0],p[2]-p[0])
        normal=np.rint(normal/np.linalg.norm(normal)).astype(int)
        axis=int(np.flatnonzero(normal)[0]);other=[i for i in range(3) if i!=axis]
        lo=np.rint(p.min(0)).astype(int);hi=np.rint(p.max(0)).astype(int)
        for a in range(lo[other[0]],hi[other[0]]):
            for b in range(lo[other[1]],hi[other[1]]):
                result.add((tuple(normal),int(lo[axis]),a,b))
    return result


for variant, details in manifest['variants'].items():
    source = ROOT/details['source']
    scene = json.loads((source/'scene.json').read_text(encoding='utf-8'))
    if variant=='black_gold':
        # Every emitted source face is a surface of a solid voxel. Transparent
        # samples on these returns would silently discard geometry at publish.
        alphas={name:rgba(source/'textures'/f'{name}.png')[:,:,3] for name in scene['textures']}
        for part in scene['parts'].values():
            for face in part['faces']:
                uv=np.array(face['uv']);lo=np.rint(uv.min(0)).astype(int);hi=np.rint(uv.max(0)).astype(int)
                alpha=alphas[face['texture']]
                assert all(alpha[v%16,u%16]>127 for v in range(lo[1],hi[1]) for u in range(lo[0],hi[0])), \
                    f'Black-gold return samples transparency: {face["texture"]} {face["vertices"]}'
    if variant=='end':
        for side,normal in enumerate([(0,0,-1),(1,0,0),(0,0,1),(-1,0,0)]):
            for f in scene['parts'][f'side_{side}_three_layers']['faces']:
                if not f['texture'].startswith('side_inset'):continue
                p=np.array(f['vertices'])
                assert np.dot(np.cross(p[1]-p[0],p[2]-p[0]),normal)>0, 'Carved backing faces inward'
    for name in scene['textures']:
        path = RES/'textures/block'/variant
        a = rgba(path/f'{name}.png')
        assert np.array_equal(a, rgba(source/'textures'/f'{name}.png')), 'Approved albedo changed'
        n, s = rgba(path/f'{name}_n.png'), rgba(path/f'{name}_s.png')
        visible = a[:, :, 3] > 127
        xy=n[:,:,:2].astype(float)/255*2-1
        assert np.all((xy*xy).sum(2)<1), 'Invalid tangent normal'
        assert n[:,:,2].min()>=232 and n[:,:,3].min()>=235, 'Excessive material relief'
        assert s[:, :, 3].max() <= 254, '255 is not labPBR emission'
        assert set(np.unique(s[:, :, 1])) <= {8, 10, 12, 14, 231}, 'Unknown F0/metal'
        if np.any(s[:,:,2]>64):
            assert name in {'pool','crystal','soul_crystal','soul_tip','sculk','side_inset_front','side_inset_side','resonance_bed'}, 'Unintended SSS material'
        rough = np.array(Image.open(ROOT/'assets/labpbr'/variant/f'{name}_roughness.png')) / 255
        decoded = (1 - s[:, :, 0].astype(float) / 255) ** 2
        assert np.max(np.abs(rough[visible] - decoded[visible])) < .007, 'Incorrect perceptual packing'
        gold = s[:, :, 1] == 231
        if variant != 'black_gold': assert not gold.any(), 'Nonmetal became gold'
        if variant == 'black_gold' and name == 'side_carved_frame':
            assert not gold[4:11, 4:12].any(), 'Pig snout must stay blackstone'
            assert not visible[4:11,4:12].all(), 'Snout regressed into a rectangular tablet'
        if variant == 'black_gold' and name == 'amethyst_core':
            assert np.all(a[:,:,0]>a[:,:,2]*2), 'Core must remain orange-red'
        if variant == 'end' and name == 'side_midframe':
            assert not visible[2:10,3:13].any(), 'Central raised window frame returned'
        total_textures += 1
    moving = json.loads((RES/'conversion_table'/f'{variant}.json').read_text())
    assert moving['format'] == 2
    assert len(moving['groups']) == (5 if variant == 'sculk' else 1)
    for group in moving['groups']:
        if variant=='black_gold':
            expected=[f for part in scene['parts'].values() if part['group']==group['name'] for f in part['faces']]
            assert surface_texels(expected)==surface_texels(group['quads']), 'Black-gold animation lost exposed surfaces'
        if variant=='end' and group['name']=='rotor':
            ring=[face for face in group['quads'] if face['texture'].endswith('/ring_glow')]
            assert ring and all(face['light_emission']==15 for face in ring), 'End ring must emit at full brightness'
            assert len({tuple(face['normal']) for face in ring})==6, 'End ring emission must cover every orientation'
            assert np.all(rgba(RES/'textures/block/end/ring_glow_s.png')[:,:,3]==170), 'Missing ring shader emission'
        keys = np.array(group['keyframes'])
        assert keys[0, 0] == 0 and keys[-1, 0] == group['duration']
        assert np.all(np.diff(keys[:, 0]) > 0)
        for face in group['quads']:
            p, uv = np.array(face['vertices']), np.array(face['uv'])
            assert np.all((uv >= 0) & (uv <= 1))
            if group['kind'] == 'position':
                assert p[:, 1].min() + keys[:, 1].min() >= 11, 'Crystal hit pit floor'
                assert p[:, 1].max() + keys[:, 1].max() <= 16, 'Crystal exceeds one block'
            else:
                radius = np.linalg.norm(p[:, [0, 2]] - np.array(group['pivot'])[[0, 2]], axis=1)
                assert radius.max() < 7, 'Rotating core exceeds socket'
    for kind in ('block', 'item'):
        model = json.loads((RES/'models'/kind/f'{variant}.json').read_text())
        faces = json.loads((ROOT/'assets/runtime'/f'{variant}_{kind}_faces.json').read_text())
        if variant=='black_gold':
            expected=[f for part in scene['parts'].values() if kind=='item' or part['group']=='static' for f in part['faces']]
            assert surface_texels(expected)==surface_texels(faces), f'Black-gold {kind} lost exposed surfaces'
        assert len(model['elements']) == len(faces)
        for f in faces:
            p, uv = np.array(f['vertices']), np.array(f['uv'])
            assert np.allclose(p, np.round(p)), 'Fractional geometry'
            assert np.all((p >= 0) & (p <= 16)), 'Geometry outside block'
            assert np.all((uv >= 0) & (uv <= 16)), 'Wrapped UV leaked into model'
            cross = np.cross(p[1]-p[0], p[2]-p[0])
            assert np.dot(cross, f['normal']) > 0, 'Backwards face'
            assert 0 <= f['light_emission'] <= 15
        total_faces += len(faces)
    blockstates = json.loads((RES/'blockstates'/f'{variant}_conversion_table.json').read_text())
    assert len(blockstates['variants']) == 4
    print(f'{variant}: native 16x albedo, materials, winding, bounds and animation groups OK')

geode=ROOT/'assets/geode_prototype'
if (geode/'runtime_faces.json').exists():
    scene=json.loads((geode/'scene.json').read_text())
    for name in scene['textures']:
        source=geode/'textures';target=RES/'textures/block/crystal_table'
        for suffix in ('','_n','_s'):
            assert np.array_equal(rgba(source/f'{name}{suffix}.png'),rgba(target/f'{name}{suffix}.png'))
        a,n,s=[rgba(target/f'{name}{suffix}.png') for suffix in ('','_n','_s')]
        crystal=name.startswith(('bud_','engraved_')) or name=='vein'
        assert bool((s[:,:,3]>0).any())==crystal, 'Geode emission must stay on crystals'
        assert bool((s[:,:,2]>64).any())==crystal, 'Geode SSS must stay on crystals'
        assert not (s[:,:,1]>=230).any(), 'Geode must remain nonmetal'
        total_textures+=1
    faces=json.loads((geode/'runtime_faces.json').read_text())
    for f in faces:
        p=np.array(f['vertices']);uv=np.array(f['uv'])
        assert np.allclose(p,np.round(p)) and p.min()>=0 and p.max()<=16
        assert uv.min()>=0 and uv.max()<=16
        assert np.dot(np.cross(p[1]-p[0],p[2]-p[0]),f['normal'])>0
    total_faces+=len(faces)
    print('crystal_table: native 16x materials, emissive crystals, integer bounds and winding OK')
    crystal_model=json.loads((RES/'models/block/crystal_table.json').read_text())
    pedestal=json.loads((RES/'models/block/catalyst_pedestal.json').read_text())
    shared=set(crystal_model['textures'].values())
    assert set(pedestal['textures'].values())<=shared, 'Pedestal must share the current crystal-table materials'
    for element in pedestal['elements']:
        for face in element['faces'].values():
            name=face['texture'].removeprefix('#')
            crystal=name.startswith('bud_') or name=='vein'
            assert (element.get('light_emission',0)>0)==crystal, 'Pedestal crystal emission differs from the geode'
        assert len(element['faces'])==1, 'Pedestal must use the common per-face material exporter'
    # Geometric quarter-turns must move the four asymmetric tips, not just UVs.
    for label,model,texture,height,wanted in (
            ('crystal_table',crystal_model,'#bud_silver',16,{(1,1),(14,1),(14,14),(1,14)}),
            ('catalyst_pedestal',pedestal,'#bud_pink',8,{(1,1),(15,1),(15,15),(1,15)})):
        tips=set()
        for element in model['elements']:
            face=element['faces'].get('up')
            if face is None or face['texture']!=texture or element['to'][1]!=height:continue
            assert element.get('light_emission',0)>0, f'{label}: corner tip lost emission'
            for x in range(int(element['from'][0]),int(element['to'][0])):
                for z in range(int(element['from'][2]),int(element['to'][2])):tips.add((x,z))
        assert tips==wanted, f'{label}: corner buds must point to four different quadrants'
    print('catalyst_pedestal: shared geode materials and crystal emission OK')
print(f'PASS: {total_textures} material sets and {total_faces} exported faces.')
