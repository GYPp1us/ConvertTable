# End conversion table — 16x art revision

The authoring scripts produce the editable model; runtime geometry and PBR are published through `assets/tools/export_refined_assets.py`.

- `end_16x_refined.bbmodel`: editable Blockbench mesh project, with the existing 12-second rotor animation.
- `end_16x_refined.blend`: editable studio scene, using the same geometry and texel-aligned UVs.
- `textures/`: native 16×16 color and material maps. Exactly one color texel per model voxel; the shortened side uses 13 active texture rows, with three padding rows.
- `renders/`: actual Blender renders of the model, not generated concept art or a game screenshot.
- `texture_sheet.png`: nearest-neighbour enlargement of the three side layers.
- `references/`: original textures extracted from the locally cached Minecraft client for this review; those original textures belong to Mojang/Microsoft.

Four side surfaces each contain three aligned 16×16 texture layers, at depths 0, 1 and 2. The outer layer contains the purple corner capitals and four-voxel-high foot guards (extended upward by two voxels). The middle layer contains the obsidian vertical posts/window surround and endstone upper/lower rails. The inner layer contains the rune panels and energy windows. Narrow return faces close each one-voxel step. There are no individual glyph, column, trim-strip or pixel cuboids on the sides.

The top basin is a single exposed-face mesh built from an integer-voxel mask. It has square steps, no fitted circle, no bevels and no sub-voxel model coordinates in its rest pose. Rotation is retained in its own group. Shallow normal/AO/height detail follows the authored pixel clusters, with material response and emission packed in labPBR.

The one-voxel-wide annulus has an eight-voxel outer diameter, contracted from ten voxels (radius reduced by one). It remains independent of the carved energy bed: ring underside Y=14, top Y=15, tabletop Y=13. There is an externally visible one-voxel air gap above the tabletop, with no supporting walls. The energy bed keeps its footprint and sits at Y=12. Both retain the rotating animation group. The endstone tabletop rails are two voxels wide and two voxels high, contain no purple inlays, and meet four-voxel-wide corner caps. A one-voxel purpur collar surrounds each crystal's root at Y=13–14; crystals extend to Y=16. Purple stone cluster contrast and purple rune/energy palette contrast have been reduced.

Two voxels of height were removed from the black body in the previous revision: the energy window uses six pixel rows instead of eight, and rune pixels are repositioned without scaling. This revision removes one additional row from the upper endstone rail, reducing it from three to two voxels high. The lower black body, base, foot guards and energy bed keep their world heights. The roof, upper purple guards, ring and crystals translate down one voxel without scaling. Overall dimensions, including the crystals, are now **16 × 16 × 16 voxels**; the tabletop is at Y=13.

Authoring: `python assets/end_refinement/design.py`

Render: `D:\Blender\blender.exe --background --python assets/end_refinement/render_blender.py`

This approved mesh is now integrated into the Fabric mod. Run `python assets/tools/build_assets.py` after authoring to republish the geometry and final labPBR maps; see `assets/README.md` for the runtime workflow.

### Current side construction

The central projecting window frame and tall intermediate posts have been removed. The three structural tiers are the obsidian backing at depth 2, upper/lower endstone rails at depth 1, and outer corner guards at depth 0. Purple runes and the energy slit are carved one voxel into the backing with closed stone returns. Foot guards have full inward top closures. Purple material saturation has been increased while retaining the original pixel clusters.
