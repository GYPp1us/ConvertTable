"""Run in a new Blender process opening the saved blend file."""
import bpy
import json
from pathlib import Path
from mathutils import Vector

OUT = Path(__file__).resolve().parent
bb = json.loads((OUT/'mother_rock_geode_16px.bbmodel').read_text(encoding='utf-8'))
parts = list(bpy.data.collections['GEODE_MODEL_16PX'].objects)
assert len(parts) == len(bb['elements'])
occupied = set()
for part in bb['elements']:
    lo,hi = part['from'],part['to']
    assert part['rotation'] == [0,0,0]
    assert all(type(a) is int and 0 <= a <= 16 for a in lo+hi)
    assert all(lo[i]<hi[i] for i in range(3))
    for x in range(lo[0],hi[0]):
        for y in range(lo[1],hi[1]):
            for z in range(lo[2],hi[2]):
                assert (x,y,z) not in occupied, 'Overlapping voxels'
                occupied.add((x,y,z))
# Every crystal and stone component must connect to the one block assembly.
todo = [next(iter(occupied))]
connected = {todo[0]}
while todo:
    x,y,z = todo.pop()
    for p in [(x+1,y,z),(x-1,y,z),(x,y+1,z),(x,y-1,z),(x,y,z+1),(x,y,z-1)]:
        if p in occupied and p not in connected:
            connected.add(p)
            todo.append(p)
assert connected == occupied, 'Floating component'
points = [ob.matrix_world @ Vector(v) for ob in parts for v in ob.bound_box]
minimum = [min(p[i] for p in points) for i in range(3)]
maximum = [max(p[i] for p in points) for i in range(3)]
assert all(abs(maximum[i]-minimum[i]-1)<1e-6 for i in range(3))
assert all(abs(v*16-round(v*16))<1e-6 for p in points for v in p)
scene = bpy.context.scene
camera = scene.camera
views = [('front',(0,-4,.5),1.35),('side',(4,0,.5),1.35),('three_quarter',(2.9,-3.5,2.8),1.85)]
renders = []
for name,location,ortho in views:
    camera.location = location
    camera.data.ortho_scale = ortho
    camera.rotation_euler = (Vector((0,0,.5))-camera.location).to_track_quat('-Z','Y').to_euler()
    scene.render.filepath = str(OUT/f'geode_{name}.png')
    bpy.ops.render.render(write_still=True)
    renders.append(str(OUT/f'geode_{name}.png'))
result = {'status':'PASS','blender_version':bpy.app.version_string,'reopened_blend':bpy.data.filepath,
          'editable_parts':len(parts),'connected_voxels':len(connected),'world_min':minimum,'world_max':maximum,
          'integer_pixel_grid':True,'overlapping_voxels':False,'floating_components':False,'rendered':renders}
(OUT/'validation.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print('GEODE_VERIFY_OK',json.dumps(result))
