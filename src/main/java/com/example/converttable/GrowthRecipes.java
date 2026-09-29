package com.example.converttable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Ordered, bundled catalyst recipes for the crystal growth pedestal. */
public final class GrowthRecipes {
    private static final String RESOURCE = "/data/convert_table/growth_recipes.json";
    private static final Catalog CATALOG = loadCatalog();

    private GrowthRecipes() { }

    /** One recipe creates one output item after spending {@code cost} growth points. */
    public record Recipe(Identifier id, Item catalyst, Item output, int cost) {
        public Recipe {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(catalyst, "catalyst");
            Objects.requireNonNull(output, "output");
            if (catalyst == Items.AIR || output == Items.AIR)
                throw new IllegalArgumentException("Air cannot be a growth recipe item");
            if (cost < 1) throw new IllegalArgumentException("Growth recipe cost must be at least 1");
        }
    }

    /** Returns this catalyst's targets in the same order as the bundled catalog. */
    public static List<Recipe> recipes(ItemStack catalyst) {
        if (!isCatalyst(catalyst)) return List.of();
        return CATALOG.byCatalyst().getOrDefault(catalyst.getItem(), List.of());
    }

    /** Component-bearing stacks are intentionally rejected to prevent copying custom items. */
    public static boolean isCatalyst(ItemStack catalyst) {
        return catalyst != null
            && !catalyst.isEmpty()
            && catalyst.getItem() != Items.AIR
            && catalyst.getComponentsPatch().isEmpty()
            && CATALOG.byCatalyst().containsKey(catalyst.getItem());
    }

    /** Returns the catalog entry with this stable identifier, or {@code null} when absent. */
    public static Recipe recipe(Identifier id) {
        return id == null ? null : CATALOG.byId().get(id);
    }

    /**
     * Compatibility helper for older callers. New production code should use the selected
     * recipe's cost; this returns the first target's cost for a valid catalyst.
     */
    @Deprecated
    public static int cost(ItemStack catalyst) {
        List<Recipe> options = recipes(catalyst);
        return options.isEmpty() ? 0 : options.getFirst().cost();
    }

    private static Catalog loadCatalog() {
        Map<Item, List<Recipe>> byCatalyst = new LinkedHashMap<>();
        Map<Identifier, Recipe> byId = new LinkedHashMap<>();
        Set<Identifier> seenIds = new HashSet<>();

        try (InputStream stream = GrowthRecipes.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                ConvertTable.LOGGER.error("Growth recipe catalog resource is missing: {}", RESOURCE);
                return new Catalog(Map.of(), Map.of());
            }

            JsonElement root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                ConvertTable.LOGGER.error("Growth recipe catalog must be a JSON object: {}", RESOURCE);
                return new Catalog(Map.of(), Map.of());
            }

            JsonObject document = root.getAsJsonObject();
            int schema = readInteger(document.get("schema_version"));
            if (schema != 1) {
                ConvertTable.LOGGER.error("Unsupported growth recipe schema {} in {} (expected 1)",
                    schema, RESOURCE);
                return new Catalog(Map.of(), Map.of());
            }
            JsonElement recipeElement = document.get("recipes");
            if (recipeElement == null || !recipeElement.isJsonArray()) {
                ConvertTable.LOGGER.error("Growth recipe catalog has no recipes array: {}", RESOURCE);
                return new Catalog(Map.of(), Map.of());
            }

            JsonArray entries = recipeElement.getAsJsonArray();
            for (int index = 0; index < entries.size(); index++) {
                JsonElement entry = entries.get(index);
                if (!entry.isJsonObject()) {
                    warn(index, "<missing>", "entry must be an object");
                    continue;
                }

                JsonObject object = entry.getAsJsonObject();
                String rawId = describe(object.get("id"));
                Identifier id;
                try {
                    id = readIdentifier(object.get("id"));
                } catch (IllegalArgumentException exception) {
                    warn(index, rawId, "invalid recipe id: " + exception.getMessage());
                    continue;
                }
                if (!seenIds.add(id)) {
                    warn(index, id.toString(), "duplicate recipe id");
                    continue;
                }

                try {
                    Item catalyst = resolveItem(readIdentifier(object.get("catalyst")));
                    if (catalyst == null) {
                        warn(index, id.toString(), "unknown catalyst item '" + describe(object.get("catalyst")) + "'");
                        continue;
                    }
                    Item output = resolveItem(readIdentifier(object.get("output")));
                    if (output == null) {
                        warn(index, id.toString(), "unknown output item '" + describe(object.get("output")) + "'");
                        continue;
                    }
                    int cost = readInteger(object.get("cost"));
                    if (cost < 1) {
                        warn(index, id.toString(), "cost must be at least 1 (got " + cost + ")");
                        continue;
                    }
                    List<Recipe> options = byCatalyst.computeIfAbsent(catalyst, ignored -> new ArrayList<>());
                    if (options.stream().anyMatch(recipe -> recipe.output() == output)) {
                        warn(index, id.toString(), "duplicate output for catalyst '"
                            + BuiltInRegistries.ITEM.getKey(catalyst) + "'");
                        continue;
                    }

                    Recipe recipe = new Recipe(id, catalyst, output, cost);
                    options.add(recipe);
                    byId.put(id, recipe);
                } catch (IllegalArgumentException exception) {
                    warn(index, id.toString(), exception.getMessage());
                }
            }
        } catch (IOException | RuntimeException exception) {
            ConvertTable.LOGGER.error("Could not load growth recipe catalog from {}", RESOURCE, exception);
            return new Catalog(Map.of(), Map.of());
        }

        Map<Item, List<Recipe>> immutableByCatalyst = new LinkedHashMap<>();
        byCatalyst.forEach((catalyst, options) -> immutableByCatalyst.put(catalyst, List.copyOf(options)));
        ConvertTable.LOGGER.info("Loaded {} growth recipes across {} catalysts",
            byId.size(), immutableByCatalyst.size());
        return new Catalog(
            Collections.unmodifiableMap(immutableByCatalyst),
            Collections.unmodifiableMap(new LinkedHashMap<>(byId)));
    }

    private static Identifier readIdentifier(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("expected a namespaced identifier string");
        String value = element.getAsString();
        int colon = value.indexOf(':');
        if (colon <= 0 || colon == value.length() - 1 || value.indexOf(':', colon + 1) >= 0)
            throw new IllegalArgumentException("expected namespace:path, got '" + value + "'");
        return Identifier.fromNamespaceAndPath(value.substring(0, colon), value.substring(colon + 1));
    }

    private static int readInteger(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("expected an integer");
        String value = element.getAsString();
        if (!value.matches("-?[0-9]+")) throw new IllegalArgumentException("expected an integer, got '" + value + "'");
        return Integer.parseInt(value);
    }

    private static Item resolveItem(Identifier id) {
        if (!BuiltInRegistries.ITEM.containsKey(id)) return null;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == Items.AIR ? null : item;
    }

    private static String describe(JsonElement element) {
        if (element == null) return "<missing>";
        return element.isJsonPrimitive() ? element.getAsString() : "<invalid>";
    }

    private static void warn(int index, String id, String reason) {
        ConvertTable.LOGGER.warn("Skipping growth recipe at recipes[{}] id='{}': {}", index, id, reason);
    }

    private record Catalog(Map<Item, List<Recipe>> byCatalyst, Map<Identifier, Recipe> byId) { }
}
