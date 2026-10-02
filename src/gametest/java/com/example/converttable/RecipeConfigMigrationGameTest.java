package com.example.converttable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Uses real files under a new temporary directory; never touches the loaded world's config. */
final class RecipeConfigMigrationGameTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static JsonElement reordered(JsonElement original) {
        if (original.isJsonObject()) {
            var result = new JsonObject();
            for (String key : original.getAsJsonObject().keySet().stream().sorted(Comparator.reverseOrder()).toList())
                result.add(key, reordered(original.getAsJsonObject().get(key)));
            return result;
        }
        if (original.isJsonArray()) {
            var result = new com.google.gson.JsonArray();
            for (var entry : original.getAsJsonArray()) result.add(reordered(entry));
            return result;
        }
        return original.deepCopy();
    }

    private static long files(Path directory) throws IOException {
        try (var entries = Files.list(directory)) { return entries.count(); }
    }

    private static void preserves(Path root, String name, String json, String bundled) throws IOException {
        Path directory = Files.createDirectory(root.resolve(name));
        Path path = directory.resolve("recipes.json");
        byte[] original = json.getBytes(StandardCharsets.UTF_8);
        Files.write(path, original);
        var result = RecipeConfigMigration.upgrade(path, json, bundled);
        check(!result.migrated() && result.json().equals(json), "Custom config was upgraded: " + name);
        check(Arrays.equals(original, Files.readAllBytes(path)) && files(directory) == 1,
            "Custom config bytes changed or received a backup: " + name);
    }

    private static void altered(Path root, String name, JsonObject legacy, String bundled, Consumer<JsonObject> change)
            throws IOException {
        var json = legacy.deepCopy();
        change.accept(json);
        preserves(root, name, "\r\n  " + json + "\n", bundled);
    }

    private static void upgrades(Path root, String name, String legacy, String bundled) throws IOException {
        Path directory = Files.createDirectory(root.resolve(name));
        Path path = directory.resolve("recipes.json");
        byte[] original = legacy.getBytes(StandardCharsets.UTF_8);
        Files.write(path, original);
        var result = RecipeConfigMigration.upgrade(path, legacy, bundled);
        check(result.migrated() && result.backup().getParent().equals(directory.toAbsolutePath()), "Backup missing/outside config directory");
        check(Arrays.equals(original, Files.readAllBytes(result.backup())), "Backup did not preserve exact original bytes");
        check(Files.readString(path, StandardCharsets.UTF_8).equals(bundled) && result.json().equals(bundled),
            "Upgraded config differs from bundled defaults");
        check(files(directory) == 2, "Migration left a temporary replacement file");
        var again = RecipeConfigMigration.upgrade(path, result.json(), bundled);
        check(!again.migrated() && files(directory) == 2, "Migration repeated or rewrote its backup");

        // Reinstalling the recognized original must create a new backup, preserving the first one.
        Files.write(path, original);
        var repeat = RecipeConfigMigration.upgrade(path, legacy, bundled);
        check(repeat.migrated() && !repeat.backup().equals(result.backup()) && files(directory) == 3,
            "A previous backup was overwritten");
        check(Arrays.equals(original, Files.readAllBytes(result.backup()))
            && Arrays.equals(original, Files.readAllBytes(repeat.backup())), "Repeated migration damaged a backup");
    }

    static void run() {
        Path temporary = null;
        try {
            byte[] fixture;
            try (var input = RecipeConfigMigrationGameTest.class.getResourceAsStream("/convert_table/recipes-1.5.2.fixture.json")) {
                check(input != null, "Released default migration fixture is missing");
                fixture = input.readAllBytes();
            }
            check(RecipeConfigMigration.sha256(fixture).equals(RecipeConfigMigration.SOURCE_JSON_SHA256),
                "Migration fixture differs from the published JAR resource");
            String legacyText = new String(fixture, StandardCharsets.UTF_8);
            check(RecipeConfigMigration.fingerprint(legacyText).equals(RecipeConfigMigration.RELEASED_DEFAULT_SHA256),
                "Published default canonical fingerprint differs");
            var legacy = JsonParser.parseString(legacyText).getAsJsonObject();
            check(legacy.getAsJsonArray("groups").size() == 99 && legacy.getAsJsonArray("advanced").size() == 52,
                "Migration fixture is not the published 1.5.2 catalog");
            String bundled = RecipeConfig.defaults();
            temporary = Files.createTempDirectory("convert-table-config-migration-");
            upgrades(temporary, "released-default", legacyText, bundled);
            upgrades(temporary, "format-and-key-order", "\r\n   " + reordered(legacy) + "\n\n", bundled);
            preserves(temporary, "already-current", bundled, bundled);
            altered(temporary, "custom-setting", legacy, bundled,
                json -> json.getAsJsonObject("settings").getAsJsonObject("piglin").addProperty("cost_n", 2));
            altered(temporary, "execution-disabled", legacy, bundled, json -> json.addProperty("execution_enabled", false));
            altered(temporary, "group-disabled", legacy, bundled,
                json -> json.getAsJsonArray("groups").get(0).getAsJsonObject().addProperty("enabled", false));
            altered(temporary, "recipe-disabled", legacy, bundled,
                json -> json.getAsJsonArray("advanced").get(0).getAsJsonObject().addProperty("enabled", false));
            altered(temporary, "group-removed", legacy, bundled, json -> json.getAsJsonArray("groups").remove(0));
            altered(temporary, "recipe-removed", legacy, bundled, json -> json.getAsJsonArray("advanced").remove(0));
            altered(temporary, "recipe-edited", legacy, bundled,
                json -> json.getAsJsonArray("advanced").get(0).getAsJsonObject().addProperty("deaths", 7));
            altered(temporary, "metadata-edited", legacy, bundled, json -> json.addProperty("description", "Player-edited"));
            altered(temporary, "array-reordered", legacy, bundled, json -> {
                var groups = json.getAsJsonArray("groups");
                var first = groups.get(0);
                groups.set(0, groups.get(1));
                groups.set(1, first);
            });

            Path rejected = Files.createDirectory(temporary.resolve("invalid-new-default"));
            Path path = rejected.resolve("recipes.json");
            Files.write(path, fixture);
            boolean failedValidation = false;
            try { RecipeConfigMigration.upgrade(path, legacyText, "{\"schema_version\":1}"); }
            catch (IllegalArgumentException expected) { failedValidation = true; }
            check(failedValidation && Arrays.equals(fixture, Files.readAllBytes(path)) && files(rejected) == 1,
                "Invalid bundled default was installed or received a backup before validation");

            Path changed = Files.createDirectory(temporary.resolve("changed-since-read"));
            path = changed.resolve("recipes.json");
            String custom = "{\"player_edit\":true}";
            Files.writeString(path, custom, StandardCharsets.UTF_8);
            boolean detectedEdit = false;
            try { RecipeConfigMigration.upgrade(path, legacyText, bundled); }
            catch (IOException expected) { detectedEdit = true; }
            check(detectedEdit && Files.readString(path).equals(custom) && files(changed) == 1,
                "Migration overwrote an edit made after the initial read");
            ConvertTable.LOGGER.info("RECIPE_CONFIG_MIGRATION_TEST_PASS: published source SHA-256 {}, exact/reformatted defaults upgrade with byte-exact unique backups; current/custom/disabled/removed/edited/reordered configs preserved; invalid defaults and stale reads never replace files",
                RecipeConfigMigration.SOURCE_JSON_SHA256);
        } catch (IOException exception) {
            throw new AssertionError("Migration temporary-file test failed", exception);
        } finally {
            if (temporary != null) {
                try (var paths = Files.walk(temporary)) {
                    List<Path> cleanup = new ArrayList<>(paths.sorted(Comparator.reverseOrder()).toList());
                    for (Path path : cleanup) {
                        check(path.toAbsolutePath().startsWith(temporary.toAbsolutePath()), "Cleanup escaped test directory");
                        Files.deleteIfExists(path);
                    }
                } catch (IOException exception) { throw new AssertionError("Migration test cleanup failed", exception); }
            }
        }
    }
}
