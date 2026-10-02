# Conversion Table assets

The current Fabric resources contain three conversion tables plus the crystal table and catalyst pedestal:

| Variant | Authoring source | Blockbench project |
| --- | --- | --- |
| End | `end_refinement/scene.json` | `end_refinement/end_16x_refined.bbmodel` |
| Black gold | `style_refinement/black_gold/scene.json` | `style_refinement/black_gold/black_gold_16x_refined.bbmodel` |
| Sculk | `style_refinement/sculk/scene.json` | `style_refinement/sculk/sculk_16x_refined.bbmodel` |
| Crystal | `geode_prototype/scene.json` | `geode_prototype/mother_rock_geode_16px.bbmodel` |
| Catalyst pedestal | `concepts/catalyst_table/build_pedestal_model.py` | Low, code-native Minecraft cuboid model |

The three conversion tables and crystal table have native 16×16 textures, editable Blender/Blockbench projects and renders. The catalyst pedestal reuses the crystal table's material set and is generated as a 28-cuboid low plinth. These renders document the art; they are not shader-pack validation. Older `blockbench/`, `textures/`, `previews/` and `manifest.json` are archived first-pass assets and are not used by the game.

## Publish current art

```powershell
python assets/tools/build_assets.py
python assets/tools/validate_assets.py
./gradlew.bat build
./gradlew.bat runClientGameTest
```

`build_assets.py` delegates to `export_refined_assets.py`. It preserves approved albedo, authors semantic material channels, clips side-layer transparency on the 16-texel grid, retains exact UV orientation, and emits static block models, full item rest poses and independent animated meshes. It also generates blockstates, recipes, unlock advancements, mining tags, loot tables and translations.

The same publish run exports the crystal table and rebuilds the catalyst pedestal model. It preserves the manually authored two-block registration, menus, localization, crafting recipes and loot. `concepts/catalyst_table/render_growth_interaction.py` rebuilds the illustrated interaction sheet from the latest isolated client screenshots.

The archived `tools/build_legacy_assets.py` is retained only for historical reference; do not run it against current resources. The model-design scripts regenerate the source art, including preliminary materials, so republish afterward to restore the final labPBR maps. Preserve any hand edits before regeneration.

## labPBR 1.3

Runtime files: `src/main/resources/assets/convert_table/textures/block/<variant>/`.

| File/channel | Meaning |
| --- | --- |
| `<name>.png` | Unchanged native 16×16 RGBA color texture |
| `<name>_n.png` | RG DirectX normal XY, B AO, A height; cluster-aligned shallow relief; neutral only outside alpha masks |
| `<name>_s.png` R | Perceptual smoothness, encoded as `1 − sqrt(linear roughness)` |
| `_s` G | Linear dielectric F0 (8–14) or predefined gold code 231 |
| `_s` B | Porosity 0–55 for stone; deliberate SSS 110–155 for crystal/sculk energy |
| `_s` A | Emission 0–254; 255 is reserved |

Editable single-channel roughness, smoothness, metal mask and F0 images are in `labpbr/<variant>/`, alongside `material_channels.png`. Dark in a roughness preview means smoother; white in the metal mask marks actual gold. Bright gold grains are smoother than weathered edges. Endstone and bone are porous/matte, deepslate and blackstone are rough, obsidian and crystals are smoother. Existing color clusters guide variation; no random micro-noise is added. Normals, AO and shallow height follow the existing 16px color clusters. Final values and local resource-pack observations are documented in `labpbr/REFERENCE.md`.

Specification: https://shaderlabs.org/wiki/LabPBR_Material_Standard

Materials share the block atlas across static and animated geometry. Rendering these channels depends on the chosen shader pack, particularly its moving-block path. No POM, tiny modeled relief or colored-light claim is implied by providing the maps.

## Runtime and checks

- `runtime/manifest.json`: current geometry/material inventory.
- `runtime/*_faces.json`: source-space face fixtures for the Java UV/geometry check, excluded from the game JAR.
- `src/main/resources/.../conversion_table/*.json`: version 2 animated mesh data; only the rotor/columns, with independent tracks.
- `tools/validate_assets.py`: native resolution, albedo equality, material packing, gold masks, winding, integer bounds and animation clearance.
- `tools/java/AssetModelCheck.java`: actual Minecraft parser and face/UV ordering, animation bounds and wrap checks.
- `src/gametest/`: isolated client world test and real game screenshots, excluded from distribution.

## README block renders

`tools/render_readme.py` renders the five displayed blocks from the actual packaged
vanilla block models, format-2 conversion-table meshes and their runtime color
textures. It applies texture UVs and transparent cutouts, poses the three animated
groups, and writes five transparent PNGs, three looping GIFs and the README banner
to `assets/readme/`. Each run records SHA-256 hashes for every model, animation and
texture it read in `assets/readme/manifest.json`. GIF playback uses the animation
tracks' seconds-based period, matching the client renderer; frame delays are rounded
to GIF's 10 ms resolution and distributed so each loop keeps the full period.

After building, render from the exact JAR that will be distributed:

```powershell
python assets/tools/render_readme.py --jar build/libs/convert-table-0.2.0.jar
```

For a quick render from the current source resources before a build, omit `--jar`.
The renderer does not regenerate or modify the model or texture resources.
When only animation timing or tracks change, `--animations-only` reuses the static
PNG and banner only if their hashes and all prior packaged resource hashes still
match the supplied JAR.

Source reference textures extracted from the locally cached Minecraft client remain local authoring references owned by Mojang/Microsoft. They are not distributed as a standalone reference pack.

## September material / model refinement

Black gold has solid gold inward returns, a snout-shaped two-depth relief and orange-red lava energy. End has three structural tiers (carved backing, horizontal rails, corner guards), recessed rune pixels and stronger violet saturation. Geode has black upper/lower corner guards joined by wrapped vertical pillars, calcite rails and a mineral backing with four independently composed, irregular crystal growth patterns. Its 16px white, gray and black stone textures follow native calcite, smooth basalt and deepslate clusters with stronger contrast. The catalyst pedestal now shares the crystal table's current `geode_*` stone textures and crystal emission. Geode materials and runtime models are authored with `python assets/geode_prototype/author_materials.py`, Blender `build_geode.py`, then `python assets/geode_prototype/publish_geode.py`. This exports `models/block/crystal_table.json` and `textures/block/crystal_table/` without modifying registration, localization, recipes or loot. Blender `verify_render.py` checks the saved model and renders front, side, three-quarter and dim-light views.

## 1.5 catalyst interaction

`concepts/catalyst_table/growth_interaction_ui_v3.png` combines actual 26.3 crystal and catalyst screens with the world placement hint. The crosshair hint uses code-defined flat 12px glyphs: a crystal plinth, paired export arrows and a growth arrow. The updated crystal model and aligned pedestal materials are republished by `tools/build_assets.py`. Pedestal rendering shows the selected output at full block size, with the nonconsuming catalyst in the mineral well. The growth recipe catalog is bundled under `data/convert_table/growth_recipes.json`.

## 1.6 material corrections

Black-gold relief returns use the original opaque pixel unless that face lies on the actual outer block boundary. Validation compares every exposed source texel with static, item and animated exports. End uses `ring_glow` for its moving ring, with labPBR emission and full block/sky light in the dynamic renderer. The catalyst pedestal shares `geode_*` materials with the crystal table and splits its crystal faces by pixel emission.

The crystal table and pedestal corner buds use 0/90/180/270-degree baked geometry rotations, matching the sculk table's four-quadrant arrangement. Each tip points toward its own outer corner; root positions, stone bodies and shared crystal materials remain fixed. The crystal-table generator rotates the northwest voxel template around the block center, while the pedestal rotates each bud around its existing local 2×2 root center.
