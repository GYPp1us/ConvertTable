"""Blender 3.x preview of the same authored labPBR channels shipped to Minecraft."""
import bpy


def material(name, folder, emission_power=1.7):
    mat=bpy.data.materials.new(name);mat.use_nodes=True
    mat.blend_method='CLIP';mat.alpha_threshold=.5
    nodes=mat.node_tree.nodes;links=mat.node_tree.links;p=nodes.get('Principled BSDF')
    def tex(suffix,space):
        t=nodes.new('ShaderNodeTexImage')
        t.image=bpy.data.images.load(str(folder/f'{name}{suffix}.png'),check_existing=True)
        t.interpolation='Closest';t.extension='REPEAT';t.image.colorspace_settings.name=space
        return t
    color=tex('','sRGB')
    links.new(color.outputs['Color'],p.inputs['Base Color']);links.new(color.outputs['Alpha'],p.inputs['Alpha'])
    for suffix,socket in [('_roughness','Roughness'),('_metal','Metallic')]:
        links.new(tex(suffix,'Non-Color').outputs['Color'],p.inputs[socket])
    p.inputs['Specular'].default_value=.45
    normal=nodes.new('ShaderNodeNormalMap');normal.inputs['Strength'].default_value=.7
    links.new(tex('_normal_preview','Non-Color').outputs['Color'],normal.inputs['Color'])
    links.new(normal.outputs['Normal'],p.inputs['Normal'])
    ss=nodes.new('ShaderNodeMath');ss.operation='MULTIPLY';ss.inputs[1].default_value=.12
    links.new(tex('_sss','Non-Color').outputs['Color'],ss.inputs[0]);links.new(ss.outputs[0],p.inputs['Subsurface'])
    links.new(color.outputs['Color'],p.inputs['Subsurface Color'])
    p.inputs['Subsurface Radius'].default_value=(.12,.08,.16)
    e=tex('_emission','Non-Color');power=nodes.new('ShaderNodeMath');power.operation='MULTIPLY';power.inputs[1].default_value=emission_power
    links.new(e.outputs['Color'],power.inputs[0]);links.new(power.outputs[0],p.inputs['Emission Strength'])
    links.new(color.outputs['Color'],p.inputs['Emission'])
    return mat
