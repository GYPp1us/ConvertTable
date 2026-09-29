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

for variant, details in manifest['variants'].items():
    source = ROOT/details['source']
    scene = json.loads((source/'scene.json').read_text(encoding='utf-8'))
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
print(f'PASS: {total_textures} material sets and {total_faces} exported faces.')
