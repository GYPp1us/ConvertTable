package com.example.converttable;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Preserves vanilla pools and adds category-specific shaping materials and a crystal catalyst. */
public final class FishingLoot {
    private FishingLoot() { }
    public record Entry(String table, Item item, int weight, int min, int max) { }
    private static List<Entry> entries = List.of();
    public static List<Entry> entries() { return entries; }

    private static List<Entry> readEntries() {
        try (var stream = FishingLoot.class.getResourceAsStream("/data/convert_table/fishing_materials.json")) {
            if (stream == null) throw new IllegalStateException("Missing fishing material catalogue");
            var document = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (document.get("schema_version").getAsInt() != 1) throw new IllegalArgumentException("Unknown fishing material schema");
            var result = new ArrayList<Entry>();
            for (var value : document.getAsJsonArray("entries")) {
                var row = value.getAsJsonObject();
                String table = row.get("table").getAsString();
                Identifier id = Identifier.parse(row.get("item").getAsString());
                int weight = row.get("weight").getAsInt(), min = row.get("min").getAsInt(), max = row.get("max").getAsInt();
                if ((!table.equals("fish") && !table.equals("treasure")) || !BuiltInRegistries.ITEM.containsKey(id)
                        || weight < 1 || min < 1 || max < min || max > 64)
                    throw new IllegalArgumentException("Invalid fishing material entry: " + row);
                result.add(new Entry(table, BuiltInRegistries.ITEM.getValue(id), weight, min, max));
            }
            return List.copyOf(result);
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot read fishing materials", error); }
    }

    public static void initialize() {
        // Resolve and register the item during mod initialization, before registries freeze.
        CraftMaterials.initialize();
        entries = readEntries();

        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (!source.isBuiltin()) return;

            String target = key.equals(BuiltInLootTables.FISHING_FISH) ? "fish"
                : key.equals(BuiltInLootTables.FISHING_TREASURE) ? "treasure" : null;
            if (target == null) return;
            int[] index = {0};
            tableBuilder.modifyPools(pool -> {
                // Vanilla has one pool. Additional pools from another mod retain their own contents.
                if (index[0]++ != 0) return;
                for (Entry entry : entries) if (entry.table().equals(target)) {
                    pool.add(LootItem.lootTableItem(entry.item()).setWeight(entry.weight())
                        .apply(SetItemCountFunction.setCount(ContextIntProviders.between(entry.min(), entry.max()))));
                }
            });
        });
    }
}
