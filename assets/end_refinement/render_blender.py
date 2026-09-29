"""Render the authored model. No game/resource integration, testing or packaging."""
import bpy
from pathlib import Path
import json
import math
from mathutils import Vector

HERE=Path(__file__).resolve().parent
scene_data=json.loads((HERE/'scene.json').read_text(encoding='utf-8'))
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)

import sys
sys.path.insert(0,str(HERE.parent/'tools'))
from blender_materials import material
materials={name:material(name,HERE/'textures') for name in scene_data['textures']}

rotor=None
for name,part in scene_data['parts'].items():
    verts=[];indices=[];uvs=[]
    for face in part['faces']:
        start=len(verts)
        # Minecraft Y-up -> Blender Z-up via a coordinate-axis swap.
        verts.extend([((x-8)/16,(z-8)/16,y/16) for x,y,z in face['vertices']])
        # The coordinate swap reverses winding: reorder corners as well as their UVs.
        indices.append([start+3,start+2,start+1,start]);uvs.extend([(u/16,1-v/16) for u,v in reversed(face['uv'])])
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(verts,[],indices);mesh.update()
    obj=bpy.data.objects.new(name,mesh);bpy.context.collection.objects.link(obj)
    for mat in materials.values():mesh.materials.append(mat)
    uv_layer=mesh.uv_layers.new(name='16x')
    for loop,uv in zip(uv_layer.data,uvs):loop.uv=uv
    for poly,face in zip(mesh.polygons,part['faces']):poly.material_index=scene_data['textures'].index(face['texture'])
    if part['group']=='rotor':rotor=obj

scene=bpy.context.scene
# Keep the retained rotation animation in the editable scene. Render its voxel-aligned rest pose.
if rotor:
    for frame,angle in [(1,0),(289,math.tau)]:
        rotor.rotation_euler[2]=angle;rotor.keyframe_insert('rotation_euler',frame=frame)
    for curve in rotor.animation_data.action.fcurves:
        for point in curve.keyframe_points:point.interpolation='LINEAR'
scene.frame_set(1);scene.render.fps=24;scene.frame_end=288

ground=bpy.data.materials.new('studio_slate');ground.diffuse_color=(.055,.060,.08,1);ground.use_nodes=True
ground.node_tree.nodes.get('Principled BSDF').inputs['Base Color'].default_value=(.055,.060,.08,1)
ground.node_tree.nodes.get('Principled BSDF').inputs['Roughness'].default_value=.95
bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.005));floor=bpy.context.object;floor.name='Studio floor';floor.data.materials.append(ground)

world=bpy.data.worlds.new('neutral studio');scene.world=world;world.use_nodes=True
world.node_tree.nodes['Background'].inputs[0].default_value=(.28,.30,.37,1)
world.node_tree.nodes['Background'].inputs[1].default_value=.4

def light(name,location,power,size,color):
    data=bpy.data.lights.new(name,'AREA');data.energy=power;data.shape='DISK';data.size=size;data.color=color
    obj=bpy.data.objects.new(name,data);bpy.context.collection.objects.link(obj);obj.location=location
    obj.rotation_euler=(Vector((0,0,.5))-obj.location).to_track_quat('-Z','Y').to_euler()
light('soft key',(-3,-4,6),260,4,(1,.97,.92))
light('neutral fill',(4,-1,3),120,3,(.93,.94,1))
light('rim',(1,4,5),200,3,(.91,.86,1))

cam_data=bpy.data.cameras.new('Camera');camera=bpy.data.objects.new('Camera',cam_data);bpy.context.collection.objects.link(camera);scene.camera=camera
cam_data.type='ORTHO'

def camera_at(location,target,scale):
    camera.location=location;camera.rotation_euler=(Vector(target)-camera.location).to_track_quat('-Z','Y').to_euler();cam_data.ortho_scale=scale

scene.render.engine='CYCLES';scene.cycles.samples=20 if '--draft' in sys.argv else 48;scene.cycles.use_denoising=True
scene.cycles.max_bounces=6;scene.cycles.transparent_max_bounces=12
scene.render.image_settings.file_format='PNG';scene.render.image_settings.color_mode='RGBA'
scene.render.resolution_percentage=100
scene.view_settings.view_transform='Standard';scene.view_settings.look='Medium High Contrast';scene.view_settings.exposure=-.5;scene.view_settings.gamma=1
scene.render.film_transparent=False

output=HERE/'renders';output.mkdir(exist_ok=True)
camera_at((3,-4,3.0),(0,0,.48),1.95)
scene.render.resolution_x=900 if '--draft' in sys.argv else 1500;scene.render.resolution_y=scene.render.resolution_x
scene.render.filepath=str(output/'end_hero.png')
bpy.ops.wm.save_as_mainfile(filepath=str(HERE/'end_16x_refined.blend'))
bpy.ops.render.render(write_still=True)
scene.cycles.samples=32;scene.render.resolution_x=720;scene.render.resolution_y=720
for name,location,target,scale in ([] if '--draft' in sys.argv else [
    ('front',(0,-4,.5),(0,0,.5),1.5),
    ('side',(4,0,.5),(0,0,.5),1.5),
    ('top',(0,0,5),(0,0,0),1.35),
]):
    camera_at(location,target,scale);scene.render.filepath=str(output/f'end_{name}.png');bpy.ops.render.render(write_still=True)
print('Rendered End revision: hero + front / side / top.')
