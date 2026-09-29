# Corner material refinement — 2026-09-16

Both materials borrow only the grayscale arrangement of the native enchanting-table side diamond corner (3×3 crop at x=0, y=4), with separate gold and blue-gray stone palettes.

- Gold caps: remove isolated bronze centre pixels; continuous pale edge, broad yellow fill. Only the corner areas of side_gold_clamps and tabletop are repainted. Gold metal ID remains 231; roughness maps are regenerated from the edited colour clusters.
- Sculk: replace the previous 28% darkening with a quieter blue-gray range and a continuous edge. Update caps, shoes and the matching spire root material. Stone remains rough and nonmetallic.
- Geometry, UVs, opacity, emission masks and animation tracks are preserved; rest size remains 16³. The End art is unchanged.

`before/` stores source scenes and final material textures from 1.1.1, with matching studio preview renders. `before_after.png` compares them against the updated assets (16 versus 48 Cycles samples, identical lights/camera/exposure). These are Blender previews, not in-game shader screenshots.

Updated authoring PNGs, Blockbench files, Blender files and runtime resource textures are in the normal project locations. Existing installed 1.1.1 JAR is not replaced by this asset-only pass.

Validation: original/new source parts and animations compare equal; the existing asset validator passes all 34 material sets and 4836 faces.
