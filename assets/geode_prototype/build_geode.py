"""Build an editable, integer-grid 16 px geode table. Run with Blender Python."""
import bpy
import base64
import collections
import json
import math
from pathlib import Path
import random
import struct
import uuid
import zlib
import sys
from mathutils import Vector

OUT = Path(__file__).resolve().parent
PALETTE = {
    'geode_calcite': '#DFDFD7', 'geode_calcite_light': '#EAEAE3', 'geode_calcite_shadow': '#BABDB9',
    'geode_basalt': '#35363D', 'geode_basalt_light': '#484A52', 'geode_basalt_dark': '#272930',
    'geode_lining': '#8D8B98', 'geode_lining_light': '#ADAAB6',
    'bud_lilac': '#9D60D6', 'bud_pink': '#DDA0F4', 'bud_silver': '#F4DEFF',
    'vein': '#B67CE6',
    'side_backing': '#7B758B', 'side_relief': '#716C80',
    'engraved_amethyst': '#9D56D3', 'engraved_glint': '#DAB1F7',
}
voxels = {}
rng = random.Random(904)


def material_id(name):
    # Dedicated stone IDs keep the catalyst pedestal's shared legacy maps intact.
    return 'geode_'+name if name.startswith(('calcite','basalt','lining')) else name

def stone_tone(mat):
    if mat == 'calcite':
        return rng.choices(['calcite', 'calcite_light', 'calcite_shadow'], [78, 12, 10])[0]
    if mat == 'basalt':
        return rng.choices(['basalt', 'basalt_light', 'basalt_dark'], [82, 9, 9])[0]
    return mat

def box(name, start, end, material, mottled=False):
    for x in range(start[0], end[0]):
        for y in range(start[1], end[1]):
            for z in range(start[2], end[2]):
                assert all(0 <= a < 16 for a in (x, y, z))
                voxels[x, y, z] = (name, material_id(stone_tone(material) if mottled else material))

# Three front planes, measured inward from each outer face: guards 0, rails /
# projecting crystal relief 1, mineral backing / flat stone engraving 2.
# Keep the established texture IDs; side_relief is now a flush, quiet ground pattern.
SIDE_CARVING = (
    '........G...',
    '...G...rgg..',
    '..ggG..ggg..',
    '.rggg..ggr..',
    '.Ggg.....r..',
    '..ggr.G.....',
    '...g..gG....',
)
# Independently composed pockets: different tips, heights and gaps on every face.
# Their roots never meet in a central point or form a bilateral V-shaped emblem.
SIDE_CARVING_VARIANTS = (
    ('..G.........', '..gg....G...', '..ggg..rgg..', '...gg..ggG..',
     '.G....ggg...', '.gg...rg....', '..gr........'),
    ('.....G......', '..G..gg.....', '..gg.ggG....', '..ggg.gg....',
     '...gr.....G.', '........rgg.', '.Gg......gr.'),
    ('.G..........', '.gg.....G...', '..gg...ggg..', '..r....Ggg..',
     '.....G..gg..', '...rgg......', '....ggG.....'),
)

# A closed lower cradle and four dark corner shoes carry the calcite shell.
# Broad texture clusters supply mineral grain without noisy per-voxel mottling.
box('01_basalt_foundation', (1, 0, 1), (15, 1, 15), 'basalt_dark')
box('02_calcite_lower_shell', (2, 1, 2), (14, 3, 14), 'calcite_shadow')
for x in (0, 13):
    for z in (0, 13):
        box('01_basalt_corner_guards', (x, 0, z), (x+3, 3, z+3), 'basalt_dark')
for x in (2, 12):
    for z in (2, 12):
        box('03_recessed_corner_supports', (x, 3, z), (x+2, 10, z+2), 'basalt_dark')

# Interior floor and thick calcite walls leave a true open-topped cavity.
box('04_geode_inner_lining', (3, 3, 3), (13, 8, 13), 'lining')
box('04_geode_inner_lining', (4, 8, 4), (12, 9, 12), 'lining_light')
for side in range(4):
    def wall_rect(name, u0, y0, d0, u1, y1, d1, mat, mottled=False):
        for u in range(u0, u1):
            for y in range(y0, y1):
                for d in range(d0, d1):
                    pos = [(u,y,d), (15-d,y,u), (15-u,y,15-d), (d,y,15-u)][side]
                    voxels[pos] = (name, material_id(stone_tone(mat) if mottled else mat))
    wall_rect('05_layer_3_mineral_backing', 2,3,2,14,10,4,'side_backing')
    # Mineral pockets have off-center silver tips and irregular lilac branches.
    for row, pattern in enumerate((SIDE_CARVING,*SIDE_CARVING_VARIANTS)[side]):
        assert len(pattern) == 12
        for col, mark in enumerate(pattern):
            u, y = col+2, 9-row
            if mark == 'r':
                wall_rect('06_layer_3_stone_ground_pattern', u,y,2,u+1,y+1,3,'side_relief')
            elif mark in 'gG':
                wall_rect('07_layer_2_crystal_relief', u,y,1,u+1,y+1,3,
                          'engraved_glint' if mark == 'G' else 'engraved_amethyst')
    # Horizontal members sit one voxel behind the corner shoes / shoulders.
    wall_rect('08_layer_2_lower_rail', 3,0,1,13,3,3,'calcite')
    wall_rect('08_layer_2_lower_rail', 3,2,1,13,3,3,'calcite_light')
    wall_rect('09_layer_2_crown_rim', 3,10,1,13,12,4,'calcite')
    wall_rect('09_calcite_crown_rim', 4,12,1,12,13,3,'calcite_light')

# Black vertical sleeves wrap both exposed faces of each side pillar. They sit
# one voxel inside the outside corner envelope and join the upper/lower guards.
for x in (1,13):
    for z in (1,13):
        box('03_black_wrapped_corner_pillars', (x,3,z), (x+2,9,z+2), 'basalt')

# All four upper corner shoulders and lower corner shoes are black stone.
for x in (0, 12):
    for z in (0, 12):
        box('10_black_corner_shoulders', (x,9,z), (x+4,13,z+4), 'basalt_dark')
        box('10_black_corner_shoulders', (x,12,z), (x+4,13,z+4), 'basalt')
        box('11_corner_crystal_buds', (x+1,13,z+1), (x+3,14,z+3), 'bud_lilac')
        box('11_corner_crystal_buds', (x+1,14,z+1), (x+3,15,z+2), 'bud_pink')
        box('11_corner_crystal_buds', (x+1,15,z+1), (x+2,16,z+2), 'bud_silver')

# Irregular attached, stepped crystal cluster inside the cavity; no floating pieces.
box('12_geode_growth_core', (6,9,6), (10,10,10), 'bud_lilac')
box('12_geode_growth_core', (7,10,7), (9,13,9), 'bud_lilac')
box('12_geode_growth_core', (7,13,7), (9,14,8), 'bud_pink')
box('12_geode_growth_core', (7,14,7), (8,15,8), 'bud_silver')
for x,z,height in [(5,7,11),(9,9,12),(6,10,11),(10,6,11),(8,5,12)]:
    box('12_geode_growth_core', (x,9,z), (x+1,height,z+1), 'bud_lilac')
    box('12_geode_growth_core', (x,height,z), (x+1,height+1,z+1), 'bud_pink')
box('12_geode_growth_core', (8,10,6), (9,12,7), 'bud_silver')

# Greedy cuboid compression retains editable named cube parts and integer bounds.
pending = dict(voxels)
cuboids = []
while pending:
    x,y,z = min(pending)
    value = pending[x,y,z]
    ex = x+1
    while pending.get((ex,y,z)) == value:
        ex += 1
    ez = z+1
    while all(pending.get((xx,y,ez)) == value for xx in range(x,ex)):
        ez += 1
    ey = y+1
    while all(pending.get((xx,ey,zz)) == value for xx in range(x,ex) for zz in range(z,ez)):
        ey += 1
    for xx in range(x,ex):
        for yy in range(y,ey):
            for zz in range(z,ez):
                del pending[xx,yy,zz]
    cuboids.append((value[0], value[1], (x,y,z), (ex,ey,ez)))

def png_bytes():
    colors = [bytes.fromhex(v[1:]) + b'\xff' for v in PALETTE.values()]
    colors += [colors[0]] * (16-len(colors))
    data = b''.join(b'\x00' + b''.join(colors) for _ in range(16))
    def chunk(typ, payload):
        return struct.pack('>I',len(payload))+typ+payload+struct.pack('>I',zlib.crc32(typ+payload)&0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(data))+chunk(b'IEND',b'')

texture = png_bytes()
(OUT/'geode_palette.png').write_bytes(texture)
palette_index = {name:i for i,name in enumerate(PALETTE)}
elements = []
groups = collections.defaultdict(list)
for index,(group,mat,start,end) in enumerate(cuboids):
    uid = str(uuid.uuid5(uuid.NAMESPACE_URL,f'geode/{index}/{start}/{end}'))
    x,y,z=start;X,Y,Z=end
    face_uv={'north':[x,16-Y,X,16-y],'south':[16-X,16-Y,16-x,16-y],
             'east':[z,16-Y,Z,16-y],'west':[16-Z,16-Y,16-z,16-y],
             'up':[x,z,X,Z],'down':[x,z,X,Z]}
    elements.append({'name':f'{group}_{index:04d}', 'uuid':uid, 'type':'cube', 'from':start, 'to':end,
                     'origin':[8,8,8], 'rotation':[0,0,0], 'box_uv':False, 'rescale':False,
                     'faces':{side:{'uv':face_uv[side], 'texture':palette_index[mat]} for side in face_uv}})
    groups[group].append(uid)
bb = {'meta':{'format_version':'4.10','model_format':'free','box_uv':False},
      'name':'Mother Rock Proliferation Table - 16px Geode Prototype',
      'resolution':{'width':16,'height':16}, 'elements':elements,
      'outliner':[{'name':g,'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'geode/group/'+g)), 'origin':[8,8,8], 'children':ids} for g,ids in sorted(groups.items())],
      'textures':[{'name':name+'.png','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'geode/'+name)), 'id':str(i),
                   'uv_width':16,'uv_height':16,'mode':'bitmap','source':'data:image/png;base64,'+base64.b64encode((OUT/'textures'/f'{name}.png').read_bytes()).decode()}
                   for i,name in enumerate(PALETTE)], 'animations':[]}
(OUT/'mother_rock_geode_16px.bbmodel').write_text(json.dumps(bb,ensure_ascii=False,indent=2),encoding='utf-8')

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
scene = bpy.context.scene
model_collection = bpy.data.collections.new('GEODE_MODEL_16PX')
scene.collection.children.link(model_collection)
sys.path.insert(0,str(OUT.parent/'tools'))
from blender_materials import material
materials={name:material(name,OUT/'textures',1.4) for name in PALETTE}

# Build the Blender meshes directly from the exported Blockbench file.
bb_disk = json.loads((OUT/'mother_rock_geode_16px.bbmodel').read_text(encoding='utf-8'))
for index,part in enumerate(bb_disk['elements']):
    start,end = part['from'],part['to']
    name,mat,_,_ = cuboids[index]
    bpy.ops.mesh.primitive_cube_add(size=1,location=((start[0]+end[0])/32-.5, (start[2]+end[2])/32-.5, (start[1]+end[1])/32))
    ob = bpy.context.object
    ob.name = part['name']
    ob.dimensions = ((end[0]-start[0])/16,(end[2]-start[2])/16,(end[1]-start[1])/16)
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    for collection in list(ob.users_collection):
        collection.objects.unlink(ob)
    model_collection.objects.link(ob)
    ob.data.materials.append(materials[mat])
    uv=ob.data.uv_layers.active or ob.data.uv_layers.new(name='Native 16px')
    for poly in ob.data.polygons:
        n=poly.normal
        for loop_index in poly.loop_indices:
            pos=ob.matrix_world @ ob.data.vertices[ob.data.loops[loop_index].vertex_index].co
            px,py,pz=(pos.x+.5)*16,pos.z*16,(pos.y+.5)*16
            if abs(n.z)>.5:u,v=px,pz
            elif abs(n.y)>.5:u,v=(px if n.y<0 else 16-px),16-py
            else:u,v=(pz if n.x>0 else 16-pz),16-py
            uv.data[loop_index].uv=(u/16,1-v/16)
    ob['blockbench_uuid'] = part['uuid']
    ob['pixel_from'] = start
    ob['pixel_to'] = end

studio = bpy.data.collections.new('RENDER_STUDIO_NON_MODEL')
scene.collection.children.link(studio)
def to_studio(ob):
    for collection in list(ob.users_collection):
        collection.objects.unlink(ob)
    studio.objects.link(ob)

bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.004))
floor = bpy.context.object
floor.name = 'Studio floor - excluded from game model'
to_studio(floor)
floor_mat = bpy.data.materials.new('studio_gray')
floor_mat.diffuse_color = (.115,.13,.16,1)
floor.data.materials.append(floor_mat)
for name,loc,power,size in [('Key',(-2,-3,5),450,3.5),('Fill',(3,-1,2.5),250,3),('Rim',(1,3,4),350,3)]:
    data = bpy.data.lights.new(name,'AREA')
    data.energy = power
    data.shape = 'DISK'
    data.size = size
    ob = bpy.data.objects.new(name,data)
    studio.objects.link(ob)
    ob.location = loc
    ob.rotation_euler = (Vector((0,0,.5))-ob.location).to_track_quat('-Z','Y').to_euler()
camera_data = bpy.data.cameras.new('Inspection camera')
camera = bpy.data.objects.new('Inspection camera',camera_data)
studio.objects.link(camera)
camera.data.type = 'ORTHO'
camera.data.ortho_scale = 1.85
camera.location = (2.9,-3.5,2.8)
camera.rotation_euler = (Vector((0,0,.5))-camera.location).to_track_quat('-Z','Y').to_euler()
scene.camera = camera
scene.render.engine = 'CYCLES'
scene.cycles.samples = 48
scene.cycles.use_denoising = True
scene.render.resolution_x = 1000
scene.render.resolution_y = 1000
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.world.color = (.22,.22,.22)
scene.view_settings.view_transform = 'Standard'
scene.view_settings.look = 'Medium High Contrast' if 'Medium High Contrast' in [i.name for i in scene.view_settings.bl_rna.properties['look'].enum_items] else 'None'
scene.view_settings.exposure = -1.65
scene.view_settings.gamma = 1
scene.unit_settings.system = 'METRIC'
scene.unit_settings.scale_length = 1
scene['model_pixel_bounds'] = '0..16 on X/Y/Z; 1 Blender unit = 16 Minecraft pixels'
scene['source_model'] = 'mother_rock_geode_16px.bbmodel'
bpy.ops.object.select_all(action='DESELECT')
for ob in model_collection.objects:
    ob.select_set(True)
bpy.context.view_layer.objects.active = next(iter(model_collection.objects))
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type == 'VIEW_3D':
            area.spaces.active.region_3d.view_distance = 2.3
            area.spaces.active.region_3d.view_location = (0,0,.5)
            area.spaces.active.shading.color_type = 'MATERIAL'

stats = {'voxel_count':len(voxels), 'editable_cuboids':len(cuboids),
         'pixel_bounds':{'min':[0,0,0],'max':[16,16,16]},
         'material_voxels':dict(collections.Counter(mat for _,mat in voxels.values())),
         'named_groups':list(sorted(groups)),
         'side_layers':{'1_corner_guards':0,'2_horizontal_rails_and_crystal_relief':1,'3_mineral_backing_and_ground_pattern':2},
         'carving_pattern':SIDE_CARVING,
         'carving_patterns':[SIDE_CARVING,*SIDE_CARVING_VARIANTS],
         'wrapped_corner_pillars':True,
         'crystal_projection_from_backing':1}
(OUT/'model_stats.json').write_text(json.dumps(stats,indent=2),encoding='utf-8')
bpy.context.preferences.filepaths.save_version = 0
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'mother_rock_geode_16px.blend'))
print('GEODE_BUILD_OK',json.dumps(stats))
