# PBR reference and current implementation

Reference inspected locally: `D:/Desktop/Document/MC/材质/00n/!江原绘景 主体包 2609A.zip` (G2F1SH pack, 128px material textures). Only channel statistics were inspected; none of its artwork is redistributed or resampled.

| Reference material | `_s` R range | `_n` RG range | Observed use |
| --- | --- | --- | --- |
| gold_block | 200 | R 60–191, G 59–189 | Smooth metal with gentle normals |
| polished_blackstone | 222 | R/G 65–189 | Polished dielectric stone |
| amethyst_block | 157–196 | R 36–222, G 37–215 | Crystal facets, AO/height and SSS |
| calcite | 158–242 | R 42–201, G 38–205 | Mineral texture and varying material response |
| obsidian | 187–238 | R 31–206, G 31–216 | Glossy stone with deep faceted normals |
| sculk | 150 | R/G 20–234 | Surface texture, SSS and selective emission |

The conversion tables keep their native **16×16** pixel density. Each existing color cluster now guides shallow normal, AO and height detail. Gold uses a smoother response than stone, crystal facets have tight highlights and intentional SSS, and white stone retains a readable broad highlight. Surface depth remains small (height 236–255) because the major relief is modeled in whole voxels.

Packing follows [labPBR 1.3](https://shaderlabs.org/wiki/LabPBR_Material_Standard): `_n` = DirectX normal XY, AO, height; `_s` = perceptual smoothness, F0/metal ID, porosity/SSS, emission. Gold stays ID **231** (the local pack uses 238, which is not the predefined gold ID). Emission is 0–254; 255 remains reserved. Only crystal/sculk energy uses SSS values over 64. Gold and ordinary stone do not emit.

`blender_materials.py` previews the authored roughness, metal, converted tangent normal, SSS and emission maps. Blender previews verify structure and authored channels; they do not establish identical shader-pack output. Runtime maps are copied directly from these sources.
