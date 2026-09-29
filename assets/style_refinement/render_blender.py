"""Actual Blender renders of the two new art drafts; never imports game resources."""
from pathlib import Path
import json
import math
import sys
import bpy
from mathutils import Vector

HERE=Path(__file__).resolve().parent
args=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
if '--source-root' in args:HERE=Path(args[args.index('--source-root')+1]).resolve()
variants=[a for a in args if a in ('black_gold','sculk')] or ['black_gold','sculk']
draft='--draft' in args


def build_scene(variant):
    root=HERE/variant
    data=json.loads((root/'scene.json').read_text(encoding='utf-8'))
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene=bpy.context.scene
    materials={}
    sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
    from blender_materials import material
    for name in data['textures']:
        materials[name]=material(name,root/'textures')
    for name,part in data['parts'].items():
        verts=[];faces=[];uvs=[]
        for face in part['faces']:
            start=len(verts)
            verts.extend([((x-8)/16,(z-8)/16,y/16) for x,y,z in face['vertices']])
            faces.append([start+3,start+2,start+1,start]);uvs.extend([(u/16,1-v/16) for u,v in reversed(face['uv'])])
        mesh=bpy.data.meshes.new(name);mesh.from_pydata(verts,[],faces);mesh.update()
        obj=bpy.data.objects.new(name,mesh);bpy.context.collection.objects.link(obj)
        for mat in materials.values():mesh.materials.append(mat)
        layer=mesh.uv_layers.new(name='Native 16x')
        for loop,uv in zip(layer.data,uvs):loop.uv=uv
        for poly,face in zip(mesh.polygons,part['faces']):poly.material_index=data['textures'].index(face['texture'])
        anim=data['animations'].get(name)
        if anim:
            # All rotor/float pivots are already centred in Blender space (X,Z=8).
            channel='rotation_euler' if anim['kind']=='rotation' else 'location'
            for time,value in anim['keys']:
                if channel=='rotation_euler':obj.rotation_euler[2]=math.radians(value)
                else:obj.location.z=value/16
                obj.keyframe_insert(channel,frame=1+time*24)
            for curve in obj.animation_data.action.fcurves:
                for key in curve.keyframe_points:
                    key.interpolation='LINEAR' if channel=='rotation_euler' else 'BEZIER'
                    if channel=='location':key.handle_left_type='AUTO_CLAMPED';key.handle_right_type='AUTO_CLAMPED'
    scene.frame_set(1);scene.render.fps=24;scene.frame_end=288

    ground=bpy.data.materials.new('studio slate');ground.use_nodes=True
    p=ground.node_tree.nodes.get('Principled BSDF');p.inputs['Base Color'].default_value=(.055,.060,.08,1);p.inputs['Roughness'].default_value=.95
    bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.005));bpy.context.object.name='Studio floor';bpy.context.object.data.materials.append(ground)
    world=bpy.data.worlds.new('neutral studio');scene.world=world;world.use_nodes=True
    world.node_tree.nodes['Background'].inputs[0].default_value=(.42,.43,.48,1);world.node_tree.nodes['Background'].inputs[1].default_value=.55
    def light(name,loc,power,size,color):
        ld=bpy.data.lights.new(name,'AREA');ld.energy=power;ld.shape='DISK';ld.size=size;ld.color=color
        obj=bpy.data.objects.new(name,ld);bpy.context.collection.objects.link(obj);obj.location=loc
        obj.rotation_euler=(Vector((0,0,.5))-obj.location).to_track_quat('-Z','Y').to_euler()
    light('soft key',(-3,-4,6),260,4,(1,.97,.92))
    light('neutral fill',(4,-1,3),120,3,(.93,.94,1))
    light('rim',(1,4,5),200,3,(.91,.86,1))
    light('broad front bounce',(1,-4,2.5),190,5,(1,.97,.93))
    if variant=='black_gold':
        # Low studio reflectors give the conductor a readable highlight at the hero angle.
        # Gold itself has no emission; the texture and material maps carry the colour change.
        light('low front reflection',(-3,-4,.2),180,4,(1,.98,.93))
        light('low side reflection',(4,3,.2),180,4,(1,.98,.93))
    cd=bpy.data.cameras.new('Camera');camera=bpy.data.objects.new('Camera',cd);bpy.context.collection.objects.link(camera);scene.camera=camera;cd.type='ORTHO'
    def camera_at(loc,target,scale):
        camera.location=loc;camera.rotation_euler=(Vector(target)-camera.location).to_track_quat('-Z','Y').to_euler();cd.ortho_scale=scale
    scene.render.engine='CYCLES';scene.cycles.samples=16 if draft else 48;scene.cycles.use_denoising=True
    scene.cycles.max_bounces=6;scene.cycles.transparent_max_bounces=12
    scene.render.image_settings.file_format='PNG';scene.render.image_settings.color_mode='RGBA';scene.render.resolution_percentage=100
    scene.view_settings.view_transform='Standard';scene.view_settings.look='Medium High Contrast';scene.view_settings.exposure=-.5;scene.view_settings.gamma=1
    output=root/'renders';output.mkdir(exist_ok=True)
    camera_at((3,-4,3.0),(0,0,.48),1.95)
    scene.render.resolution_x=900 if draft else 1500;scene.render.resolution_y=scene.render.resolution_x
    scene.render.filepath=str(output/f'{variant}_hero.png')
    if not draft:bpy.ops.wm.save_as_mainfile(filepath=str(root/f'{variant}_16x_refined.blend'))
    bpy.ops.render.render(write_still=True)
    if not draft:
        scene.cycles.samples=32;scene.render.resolution_x=720;scene.render.resolution_y=720
        for name,loc,target,scale in [('front',(0,-4,.5),(0,0,.5),1.5),('side',(4,0,.5),(0,0,.5),1.5),('top',(0,0,5),(0,0,0),1.35)]:
            camera_at(loc,target,scale);scene.render.filepath=str(output/f'{variant}_{name}.png');bpy.ops.render.render(write_still=True)
    print(f'Rendered {variant}.')


for variant in variants:build_scene(variant)
