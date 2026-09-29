# ConvertTable

Three animated conversion tables and a two-block crystal growth system for **Minecraft Java 26.3**, Fabric Loader **0.19.5+**, Fabric API **0.161.0+26.3**, and Java **25**.

## Play

Put `build/libs/convert-table-1.4.0.jar` and Fabric API in the instance's `mods` directory. Install the mod on both client and server for multiplayer. Keep only one ConvertTable release in `mods`; the `-sources.jar` is for development and should not be installed.

All three tables appear in **Functional Blocks**, have one-block collision and selection bounds, face the player on placement, emit light level 6, and drop themselves when mined with a pickaxe. Pistons cannot move them. Right-click opens the conversion UI. Recipes, batch execution, connected-container processing, chorus-fuel consumption and sculk death charging are active. Click Convert batch, or enable continuous processing (off by default).

```mcfunction
/give @s convert_table:black_gold_conversion_table
/give @s convert_table:end_conversion_table
/give @s convert_table:sculk_conversion_table
/give @s convert_table:crystal_table
/give @s convert_table:catalyst_pedestal
```

| Variant | Appearance and motion |
| --- | --- |
| Black gold | Bright gold clamps, rough blackstone pig-snout carving, stepped diamond pit; recessed core rotates every 12 seconds. |
| End | Endstone rails, obsidian posts, purpur corner guards; floating voxel ring and energy bed rotate every 12 seconds. |
| Sculk | Dark deepslate caps, one lower bone fan, sculk insets; five crystal columns float independently on a 12-second repeating track. |
| Crystal | Pale calcite, basalt foundation, glowing crystal growth and visible recessed geode. |
| Catalyst pedestal | Low calcite/basalt plinth with four crystal tips; the selected full-size output block floats above its reusable catalyst. |

The approved rest geometry is 16 × 16 × 16 model units with native 16×16 textures. Animation remains smooth, while modeled details stay on the integer voxel grid. Fixed shells use chunk rendering; only moving parts use the block entity renderer. Animation needs no server ticker, packets, or saved counter.

## Crafting

Each recipe yields one table. Patterns below read top to bottom.

| Variant | Pattern | Materials |
| --- | --- | --- |
| Black gold | `GTG / BAB / BCB` | G: gold ingot; T: snout armor trim smithing template; B: polished blackstone; A: amethyst shard; C: chiseled polished blackstone |
| End | `PHP / OAO / PEP` | P: purpur block; H: dragon head; E: end stone; O: obsidian; A: amethyst shard |
| Sculk | `DKD / SCS / DED` | D: polished deepslate; K: sculk shrieker; S: sculk; C: sculk catalyst; E: echo shard |
| Crystal | `CAC / BXB / CSC` | C: calcite; A: amethyst block; B: budding amethyst; X: end crystal; S: smooth basalt |
| Catalyst pedestal | `   / CAC / SSS` | C: calcite; A: amethyst block; S: smooth basalt |

## Materials

The JAR includes albedo and **labPBR 1.3** `_n`/`_s` textures. Smoothness, dielectric F0, gold material masks, porosity and selective emission follow the existing pixel clusters. The pig snout is sculpted stone, not metal. Fine normal/AO/height detail stays subtle beside the modeled voxel relief.

Both stationary and moving geometry use the block texture atlas. Standard Minecraft shows the color textures and lit energy surfaces. Reflections, metal response and bloom require a shader pack that supports labPBR and its moving-block render path. No specific shader pack is bundled; shader appearance needs verification with the selected pack.

## Develop and verify

```powershell
python assets/tools/build_assets.py
python assets/tools/validate_assets.py
./gradlew.bat build
./gradlew.bat runClientGameTest
./gradlew.bat runClient
```

Python 3 with Pillow and NumPy is needed only to regenerate/inspect assets. Generated game resources are included, so Java/Gradle can build the mod without Python. The Gradle wrapper is 9.5.1; the local build also works with cached Gradle 9.6.0.

`build` runs the Minecraft model parser against all ten shipped models and verifies exported conversion-table faces plus animation interpolation. The separate client game test creates an isolated world, exercises registered entities/facings/drops/recipes, takes screenshots at two animation phases, and reloads resources. It is excluded from the release JAR and does not open user saves. Screenshots are under `build/run/clientGameTest/screenshots/`.

## 1.5 crystal growth and catalysts

Connect budding amethyst to a crystal table with amethyst blocks. Each bud exports at most **1 point/second**, or **2 with calcite**; its internal growth potential is **24/16/8/0**, increased by one with smooth basalt. Place catalyst pedestals against any connected conductor or the crystal table. Multiple pedestals fairly share the one network budget.

Insert one reusable catalyst, then choose a target and start. Oak saplings unlock oak logs, stripped logs, wood and planks; the bundled catalog has 53 catalyst groups and 225 outputs. Catalysts are never consumed. The selected block rotates at full block size above the physical catalyst. Nine output slots flush to adjacent containers, with double chests and sided insertion rules respected. Changing the target pauses production and clears unspent credit; existing output stays.

The crystal screen shows stage-count charts and separate internal/export/used quantities. The pedestal screen includes paged target icons, last-cycle production, absorbed growth, fractional credit, buffer occupancy and six-direction container status. A flat pixel glyph (plinth, paired export arrows or growth arrow) appears beside the crosshair when placing a pedestal, calcite or smooth basalt against a usable crystal conductor.

See the [Chinese interaction guide](规划/母岩触媒_双方块交互与生长势方案.md) and [catalyst recipe design](规划/触媒增殖配方设计.md). Recipes are shipped in `src/main/resources/data/convert_table/growth_recipes.json`; both sides load the same stable ordered catalog. Run `./gradlew.bat -PgrowthTest runClientGameTest` for the isolated integration test and screenshots.

Editable models, textures, channel previews and the authoring workflow are documented in [assets/README.md](assets/README.md).
## 1.3.1 conversion and death charging

- Piglin: material + gold nuggets produce a random different item in the configured group. One cost per batch, including partial batches. Blocked output retains the random choice across saves. No target selector or progress bar.
- End: select a target; chorus fruit supplies 16 phase charge by default, spent only on successful batches. No idle drain. Sculk inherits ordinary conversions and adds the configured advanced recipes.
- Sculk: advanced recipes use the new reagent and remainder slots, phase charge and stored mob-death counts. All costs are checked before any inventory changes. Recipes marked `auto: false` require a manual click in input-slot mode.
- Input modes: device slots; connected containers (in-place, no separate input/output roles); shulker-box contents (modify inside the original box). Exact linked mode uses the device input as a sample without consuming it; group mode accepts compatible inputs. Exact shulker mode locks the first successfully processed material until mode/target is changed. Tooltips explain each mode.
- Continuous mode attempts one batch every 10 server ticks, even with the UI closed. It starts disabled and is saved with the table. Hopper insertion respects fuel/reagent/output rules.
- Sculk charging uses real mob deaths with feet supported on an exposed connected sculk block. It excludes players, armor stands and airborne deaths. The nearest loaded table by network path receives one credit (coordinate tie-break); a shared network never duplicates credits. Default capacity is 4096. Vanilla loot, XP and catalyst behaviour remain intact.
- Charge, contents and preferences survive saving/chunk unloading. Disconnecting the surface does not erase charge. Breaking the table drops its inventory through vanilla behaviour; internal phase/death charge is not carried by the dropped table item.
- Network discovery follows full nodes and actual vein faces, including shared edges around solid sculk. Radius 16 / 1024 nodes, loaded chunks only. Container radius 4 / maximum 8; double chests count once. Locked and unresolved-loot containers are counted but not processed.
- The map still projects **along Z onto X–Y**, preserving height. It now zooms to the occupied bounds, keeps one grid cell per block and explicitly labels the collapsed Z axis. Overlapping depths share a pixel. Yellow = table; teal = nodes; light teal = exposed sculk.

The server generates `config/convert_table/recipes.json` on first start, retains edits, and syncs recipes to clients. Defaults contain 99 groups / 52 concrete advanced recipes. On 26.3, all 99 planned groups and all 52 advanced recipes resolve in the tested registry. `execution_enabled: false` provides a catalogue-only switch. Restart the world/server after edits.

JEI and REI are optional adapters; 26.3-specific viewer builds must be installed separately. The source adapters expose inputs, outputs, costs, remainders and usage lookup. They do not perform automatic recipe transfers. The human-readable `unlock` field remains informational; execution checks the actual recipe ingredients and costs, not a separate advancement unlock tree. See [configuration guide](config/convert_table/README.md).

Run `./gradlew.bat -PuiTest runClientGameTest` for the isolated client/server suite: screen and inventory sync, all 52 advanced recipes, ordinary batch accounting, blocked-output conservation, component preservation, linked and nested processing, real death events, shared ownership, disconnection, vein bridges and continuous server ticking. The compatibility and asset-cache options below also apply.

## 1.1.1 renderer fix

Corrects a crash when rendering an animated table: `Invalid atlas id: minecraft:textures/atlas/blocks.png`. Minecraft 26.3 distinguishes atlas definition IDs from texture locations. The renderer now requests `SpriteId` entries through the sprite lookup, preserving the shared block atlas for Iris materials. No Sodium or Iris dependency is added to the release.

The optional compatibility runner copies the specified instance's mods/config/shaders into the project's disposable test environment and reads its complete asset cache:

```powershell
./assets/tools/test_compat.ps1 -Instance 'D:/path/to/.minecraft/versions/your-test-instance' -Backend opengl
```

Use `-Backend vulkan` to request Vulkan, or `-Gradle` to select a cached Gradle executable. The test-only backend mixin prevents Fabric's test defaults from silently replacing the requested graphics API. It is excluded from the mod JAR. The original instance's saves and settings are not opened or edited by this runner.

