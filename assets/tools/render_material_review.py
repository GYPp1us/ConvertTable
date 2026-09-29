"""Render the saved models under dim neutral light to inspect authored emission."""
from pathlib import Path
import bpy

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'refinement_review';OUT.mkdir(exist_ok=True)
models={'black_gold':ROOT/'style_refinement/black_gold/black_gold_16x_refined.blend',
        'end':ROOT/'end_refinement/end_16x_refined.blend',
        'sculk':ROOT/'style_refinement/sculk/sculk_16x_refined.blend',
        'crystal':ROOT/'geode_prototype/mother_rock_geode_16px.blend'}
for name,path in models.items():
    bpy.ops.wm.open_mainfile(filepath=str(path));scene=bpy.context.scene
    for light in bpy.data.lights:light.energy*=.025
    scene.world.use_nodes=True
    scene.world.node_tree.nodes['Background'].inputs['Color'].default_value=(.20,.22,.30,1)
    scene.world.node_tree.nodes['Background'].inputs['Strength'].default_value=.05
    scene.render.resolution_x=800;scene.render.resolution_y=800
    scene.cycles.samples=20;scene.cycles.use_denoising=True
    scene.render.filepath=str(OUT/f'{name}_dark.png')
    bpy.ops.render.render(write_still=True)
print('MATERIAL_DARK_REVIEW_OK')
