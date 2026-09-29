<div align="center">
  <img src="assets/previews/banner.png" alt="ConvertTable — a Fabric mod for Minecraft 26.3" width="100%">
</div>

<h1 align="center">ConvertTable</h1>

<p align="center">
  <a href="README.md">中文</a> · <b>English</b>
</p>

<p align="center">
  Three animated conversion tables, plus a crystal factory grown out of budding amethyst.<br>
  Dump your junk in one end. Get the thing you actually wanted out the other.
</p>

---

## Contents

- [What this is](#what-this-is)
- [Install](#install)
- [Conversion tables](#conversion-tables)
- [Crystal table and catalyst pedestal](#crystal-table-and-catalyst-pedestal)
- [Crafting](#crafting)
- [Configuring recipes](#configuring-recipes)
- [FAQ](#faq)

---

## What this is

Five blocks, two systems.

**Conversion tables** turn junk into what you want. The Piglin table hands out random surprises, the End table spends chorus fruit to hit an exact target, and the Sculk table burns stored mob deaths on advanced reactions.

**The crystal table** grows instead of converting. Wire budding amethyst into a vein and it accumulates growth potential on its own. Hang a catalyst pedestal off it, insert a catalyst, pick a target — and it starts turning that potential into real blocks, feeding them straight into your chests.

| Block | In one line |
| :--- | :--- |
| Black gold conversion table | Piglin style, random output, gold nuggets as fuel |
| End conversion table | End style, chosen target, chorus fruit as phase fuel |
| Sculk conversion table | Sculk style, advanced recipes, spends mob death counts |
| Crystal table | The geode exports growth potential for the whole vein |
| Catalyst pedestal | Spends potential to copy blocks; the catalyst is never consumed |

---

## Install

Requires **Minecraft Java 26.3** with **Fabric**, on Java **25**.

| Component | Version |
| :--- | :--- |
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | 0.161.0+26.3 or newer |
| Java | 25 |

1. Grab `convert-table-x.y.z.jar` from [Releases](https://github.com/GYPp1us/ConvertTable/releases/latest).
2. Drop it in `mods` alongside [Fabric API](https://modrinth.com/mod/fabric-api).
3. **For multiplayer, install it on both client and server** — the versions must match.
4. Keep exactly one build in `mods`. The `-sources` jar is for development; don't install it.

All five blocks live in the **Functional Blocks** tab:

```mcfunction
/give @s convert_table:black_gold_conversion_table
/give @s convert_table:end_conversion_table
/give @s convert_table:sculk_conversion_table
/give @s convert_table:crystal_table
/give @s convert_table:catalyst_pedestal
```

They take up one block, face you when placed, emit light level 6, drop themselves when mined with a pickaxe, and cannot be pushed by pistons.

---

## Conversion tables

Right-click to open the UI. Put material on the left, pick a target on the right, hit **Convert batch**. If clicking gets old, flip **Continuous** on — it starts off.

<div align="center">

| Black gold | End | Sculk |
| :---: | :---: | :---: |
| ![Black gold conversion table](assets/previews/black_gold_idle.gif) | ![End conversion table](assets/previews/end_idle.gif) | ![Sculk conversion table](assets/previews/sculk_idle.gif) |

</div>

### Three input modes

| Mode | What it does |
| :--- | :--- |
| **Device slots** | Material is consumed from the input slot, output lands in the output slots. |
| **Connected containers** | Converts items inside containers touching the table, in place. |
| **Shulker contents** | Put a shulker box in the input slot; the contents change, the box stays. |

The last two also offer **exact** and **group** matching. Exact uses the input slot as a sample and does not consume it; group accepts anything that converts to the target.

### How the three differ

**Black gold** — material plus gold nuggets produces a *random* different item from the configured group. One cost per batch, even a partial one. If the output backs up, the rolled result is kept and survives a save. No target selector, no progress bar.

**End** — you pick the target. Chorus fruit supplies phase charge, 16 per fruit by default, and it is **only spent on a successful batch**. No idle drain. Continuous mode tries one batch every 10 server ticks (0.5 s), even with the UI closed.

**Sculk** — does ordinary conversions plus advanced recipes. Those use the reagent and remainder slots and spend phase charge and **stored mob death counts**. Anything marked `auto: false` needs a manual click and will not run automatically.

Every cost is checked before anything changes. If you can't afford it, nothing is consumed.

### Sculk charging

A mob that dies with its feet on an **exposed connected sculk block** grants 1 credit to the nearest Sculk table. Players, armour stands and airborne deaths don't count. Default capacity is 4096.

A shared network never duplicates credits between tables. Disconnecting the surface does not erase what you stored.

---

## Crystal table and catalyst pedestal

Added in 1.5. This system is completely independent of the conversion tables.

<div align="center">
  <img src="assets/geode_prototype/geode_dark.png" alt="The crystal geode" width="420">
</div>

### Step 1 — run a vein

Connect **budding amethyst** to a crystal table using **amethyst blocks**. The buds along the vein are the generators:

- Each immature bud exports at most **1 point/second**, doubled to **2** with **calcite** nearby.
- Each bud's internal growth potential is **24 / 16 / 8 / 0** for small / medium / large / cluster, raised by one with **smooth basalt**.
- Clusters stop exporting. While production runs, mature clusters can be reseeded into small buds.

Discovery reaches radius 16 and up to 1024 nodes, loaded chunks only.

### Step 2 — hang a pedestal

Place a **catalyst pedestal** against any connected conductor or the crystal table. Put one **catalyst** in, choose a target, and start.

- **The catalyst is never consumed.** Insert it once and keep using it.
- The selected block rotates at full block size above the catalyst.
- Multiple pedestals **fairly share** the one vein's budget.
- Changing the target pauses production and clears unspent credit; output you already earned stays.

### Step 3 — pipe it into storage

The pedestal has **9 output slots** and flushes them into adjacent containers. Double chests count once, and sided insertion rules are respected.

### What the screens show you

The **crystal screen** charts bud counts per stage and separates internal, exported and used potential.

The **pedestal screen** has paged target icons, last-cycle production, absorbed growth, fractional credit, buffer occupancy, and container status for all six directions.

Hold a catalyst pedestal, calcite or smooth basalt against a usable crystal conductor and a **flat pixel glyph** appears next to your crosshair (plinth, paired export arrows, or growth arrow) telling you the spot works.

### Recipe scale

**53 catalyst groups** and **225 outputs** ship in the box. Oak saplings unlock oak logs, stripped logs, wood and planks, among much else.

---

## Crafting

Each recipe yields one block.

### Conversion tables

| Block | Pattern | Materials |
| :--- | :--- | :--- |
| Black gold | `GTG`<br>`BAB`<br>`BCB` | G gold ingot · T **snout armour trim smithing template** · B polished blackstone · A amethyst shard · C chiseled polished blackstone |
| End | `PHP`<br>`OAO`<br>`PEP` | P purpur block · H **dragon head** · E end stone · O obsidian · A amethyst shard |
| Sculk | `DKD`<br>`SCS`<br>`DED` | D polished deepslate · K **sculk shrieker** · S sculk · C **sculk catalyst** · E echo shard |

### Growth blocks

| Block | Pattern | Materials |
| :--- | :--- | :--- |
| Crystal table | `CAC`<br>`BXB`<br>`CSC` | C calcite · A amethyst block · B budding amethyst · X **end crystal** · S smooth basalt |
| Catalyst pedestal | `···`<br>`CAC`<br>`SSS` | C calcite · A amethyst block · S smooth basalt |

> **Budding amethyst is now obtainable.** This mod overrides the vanilla loot table: mine budding amethyst with a **Silk Touch** pickaxe to get the block itself (vanilla just destroys it). Explosions still destroy it. Without this the crystal table could never be crafted or relocated.

---

## Configuring recipes

The server writes `config/convert_table/recipes.json` on first start. **Restart the world or server after editing it.**

- Defaults hold **99 conversion groups / 52 advanced recipes**.
- Your edits are kept and synced to clients.
- Want a catalogue-only setup with no actual conversion? Set `execution_enabled` to `false`.
- Field-by-field docs live in [`config/convert_table/README.md`](config/convert_table/README.md).
- Growth recipes are in `src/main/resources/data/convert_table/growth_recipes.json`; both sides load the same ordered catalogue.

**JEI / REI** are optional. With one installed you can look up inputs, outputs, costs, remainders and usages — but the adapters do **not** move items for you. 26.3-specific builds must be installed separately.

---

## FAQ

**Nothing happens after installing.**
Check Minecraft is 26.3, Java is 25, and that only one ConvertTable version sits in `mods`.

**Multiplayer errors or desync.**
Client and server versions must match exactly, Fabric API included.

**I don't see reflections, metal response or bloom.**
The textures ship labPBR 1.3 `_n` / `_s` maps. Vanilla Minecraft only shows the colour textures and lit energy surfaces. Reflections, metal response and bloom need a shader pack that supports **labPBR and its moving-block render path**. No shader pack is bundled.

**I broke the table — where's my stuff?**
It drops by vanilla rules. Phase charge and death counts are **not** carried by the dropped item.

**Do phase charge and death counts survive?**
Yes, through saving and chunk unloading. Disconnecting the sculk surface does not erase charge either.

**The pedestal isn't producing.**
Check in order: is the vein connected, are there immature buds, is a catalyst inserted, is a target selected, and are the output slots or adjacent containers full?

**How do I get budding amethyst?**
Mine it with a **Silk Touch** pickaxe. Vanilla destroys it outright; this mod makes it collectable, otherwise the crystal table could neither be crafted nor moved.

---

<div align="center">
  <sub>MIT License · Built for Minecraft 26.3 with Fabric</sub>
</div>
