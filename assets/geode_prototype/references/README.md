# Native stone references

`calcite.png`, `smooth_basalt.png` and `deepslate.png` were read from the locally installed Minecraft Java 26.3 client, under `assets/minecraft/textures/block/`. The originals belong to Mojang/Microsoft and are local authoring references; this reference folder is not packaged in the mod JAR.

`author_materials.py` preserves their native 16×16 cluster layout, recenters the palette for the geode's white / gray / black materials and authors the labPBR channels. Calcite contrast is scaled by 1.08, basalt by 1.05, and gray stone by 1.00. Crystal textures remain separately authored.
