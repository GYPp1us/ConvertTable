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
    private static volatile Catalog remoteCatalog;
    private static volatile long displayRevision;

    private GrowthRecipes() { }

    /** One recipe creates one output item after spending {@code cost} growth points. */
    public record Recipe(Identifier id, Item catalyst, Item source, Item output, int cost) {
        public Recipe {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(catalyst, "catalyst");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(output, "output");
            if (catalyst == Items.AIR || source == Items.AIR || output == Items.AIR)
                throw new IllegalArgumentException("Air cannot be a growth recipe item");
            if (source != output) throw new IllegalArgumentException("Growth originals must match their output item");
            if (cost < 1) throw new IllegalArgumentException("Growth recipe cost must be at least 1");
        }
    }

    /** Returns this catalyst's targets in the same order as the bundled catalog. */
    public static List<Recipe> recipes(ItemStack catalyst) {
        return recipes(catalyst, CATALOG);
    }

    /** Client menus and viewers use the server's catalogue after joining. */
    public static List<Recipe> displayRecipes(ItemStack catalyst) {
        return recipes(catalyst, remoteCatalog == null ? CATALOG : remoteCatalog);
    }

    private static List<Recipe> recipes(ItemStack catalyst, Catalog catalog) {
        if (!bare(catalyst)) return List.of();
        return catalog.byCatalyst().getOrDefault(catalyst.getItem(), List.of());
    }

    /** Component-bearing stacks are intentionally rejected to prevent copying custom items. */
    public static boolean isCatalyst(ItemStack catalyst) {
        return bare(catalyst) && CATALOG.byCatalyst().containsKey(catalyst.getItem());
    }
    public static boolean isDisplayCatalyst(ItemStack catalyst) { return !displayRecipes(catalyst).isEmpty(); }
    private static boolean bare(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() != Items.AIR && stack.getComponentsPatch().isEmpty();
    }
    public static boolean matchesSource(Recipe recipe, ItemStack source) {
        return recipe != null && bare(source) && source.is(recipe.source());
    }
    public static boolean isSource(ItemStack stack) { return isSource(stack, CATALOG); }
    public static boolean isDisplaySource(ItemStack stack) {
        return isSource(stack, remoteCatalog == null ? CATALOG : remoteCatalog);
    }
    private static boolean isSource(ItemStack stack, Catalog catalog) {
        return bare(stack) && catalog.byId().values().stream().anyMatch(recipe -> stack.is(recipe.source()));
    }

    /** Returns the catalog entry with this stable identifier, or {@code null} when absent. */
    public static Recipe recipe(Identifier id) {
        return id == null ? null : CATALOG.byId().get(id);
    }

    /** Returns every valid recipe in bundled order for optional recipe viewers. */
    public static List<Recipe> allRecipes() {
        return List.copyOf(CATALOG.byId().values());
    }
    public static List<Recipe> allDisplayRecipes() {
        return List.copyOf((remoteCatalog == null ? CATALOG : remoteCatalog).byId().values());
    }
    public static void applyRemoteCatalog(String json) { remoteCatalog = parseCatalog(json); displayRevision++; }
    public static void clearRemoteCatalog() { remoteCatalog = null; displayRevision++; }
    public static long displayRevision() { return displayRevision; }
    public static String bundledJson() {
        try (InputStream stream = GrowthRecipes.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing bundled growth recipes");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) { throw new IllegalStateException("Cannot read growth recipes", error); }
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
        return parseCatalog(bundledJson());
    }
    private static Catalog parseCatalog(String json) {
        Map<Item, List<Recipe>> byCatalyst = new LinkedHashMap<>();
        Map<Identifier, Recipe> byId = new LinkedHashMap<>();
        Set<Identifier> seenIds = new HashSet<>();

        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                ConvertTable.LOGGER.error("Growth recipe catalog must be a JSON object: {}", RESOURCE);
                return new Catalog(Map.of(), Map.of());
            }

            JsonObject document = root.getAsJsonObject();
            int schema = readInteger(document.get("schema_version"));
            if (schema != 1 && schema != 2) {
                ConvertTable.LOGGER.error("Unsupported growth recipe schema {} in {} (expected 1 or 2)",
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
                    Item source = schema == 1 ? output : resolveItem(readIdentifier(object.get("source")));
                    if (source != output) {
                        warn(index, id.toString(), "original must be the output item");
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

                    Recipe recipe = new Recipe(id, catalyst, source, output, cost);
                    options.add(recipe);
                    byId.put(id, recipe);
                } catch (IllegalArgumentException exception) {
                    warn(index, id.toString(), exception.getMessage());
                }
            }
        } catch (RuntimeException exception) {
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
