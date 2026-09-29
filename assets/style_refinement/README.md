# Black gold and sculk — separate 16x art drafts

These assets follow the approved End table's vanilla pixel density and whole-voxel relief. They use separately authored compositions and structures, not recoloured End geometry. The End design is preserved; the approved texture changes are also published to game resources.

Both rest poses fit **16 × 16 × 16 model units**, one model unit per colour texel. Side decorations use three texture-mask depths (0, 1, 2); exposed surfaces are joined and merged into planar mesh rectangles. All static surfaces lie on the integer grid. There are no bevels, continuous circular profiles, noise overlays or sub-voxel grooves.

- **Black gold:** a continuous roof and gold diamond lip at Y=15, with corner clamps and broad shoulder pads at Y=16. This gives one voxel of roof relief, while thin diagonal inlays stay painted into the lower deck. The pit steps down to Y=14 and Y=13; the independently rotating core reaches Y=14. Gold uses much brighter ore-yellow midtones and pale highlights, retaining the dark mineral borders and bright-grain hierarchy of gilded blackstone. The side pig-snout tablet is dark chiseled blackstone with lighter carved edges and black nostrils; its pixels use stone material response, not gold. The short orange-red energy slot remains below it; the recessed rotor shares the lava palette. Corner clamps use a continuous pale-gold edge and an even yellow inner fill, without the former dark bronze centre dots. Their native 3×3 value pattern also follows the enchanting-table diamond corners. Gold has no emission; the Blender studio includes low reflection lights so the metal can be read at the hero angle.
- **Sculk:** a polished deepslate shell with softly shaded corner capitals and shoes. Their continuous light edge and quiet inner fill follow the grayscale structure of the vanilla enchanting-table diamond corners, using a restrained blue-gray stone palette. Only the lower catalyst-like bone fan remains on the sides; the upper forked motif is removed. The isolated blue corner inlays, including front (2, 11), are removed on all four sides, and the central blue vein is connected. The square resonance chamber, top shrieker prongs and five independently floating crystals remain.

Each variant directory contains:

- `*_16x_refined.bbmodel`: editable Blockbench mesh project with embedded textures and a 12-second looping idle animation.
- `*_16x_refined.blend`: editable Blender scene, matching geometry and UVs, with animation and studio lighting.
- `scene.json`: authoring mesh and animation source shared with the renderer.
- `textures/`: native 16×16 colour, cluster-aligned normal/height, labPBR specular and preview material maps. Normal/height maps add shallow material response along the existing pixel clusters. The per-pixel gold mask is encoded as labPBR gold (G=231); emission avoids the reserved 255 value.
- `renders/`: actual Blender perspective and orthographic renders.
- `texture_sheet.png`: nearest-neighbour enlargement for texture review.

Lower side-layer return faces use their own side texels. They no longer borrow the roof's gold inlays or dark stone when closing a bone edge. This keeps the painted material consistent around each one-voxel step.

Sculk inset textures are fully opaque. Only opaque dark clusters are taken from the shrieker reference; the original air cutouts between its horns are excluded so that removing the upper bone motif does not leave holes in the backboard.

The local `references/` contains original textures extracted from this project's cached Minecraft client. The original textures belong to Mojang/Microsoft; reference files are local authoring sources, not a distributable resource pack.

Author: `python assets/style_refinement/design.py`

Render: `D:\Blender\blender.exe --background --python assets/style_refinement/render_blender.py`

Compose: `python assets/style_refinement/compose_review.py`

These approved meshes are now integrated into the Fabric mod. Run `python assets/tools/build_assets.py` after authoring to republish the geometry and final labPBR maps; see `assets/README.md` for the runtime workflow.

### Current revision

Gold corner clamps, shoulder clamps and the pit lip now have solid gold return faces. The snout relief follows its actual silhouette, with an outer cheek/bridge layer and recessed nostrils; it is no longer a rectangular tablet. The rotor and side energy slots use orange-red lava colors. All three source models and runtime resources now share the revised labPBR materials and Blender previews. The PBR implementation reference is `../labpbr/REFERENCE.md`.
