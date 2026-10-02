package com.example.converttable;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/** Upgrades only the complete, untouched defaults from the published 1.5.2 artifact. */
final class RecipeConfigMigration {
    // Extracted from convert-table-1.5.2.jar / convert_table/default_recipes.json.
    // The old document is deliberately not another production recipe resource.
    static final String SOURCE_JAR_SHA256 = "13a82aa85bd2d36bdf4137f41c51485298bd2ca5217e2ebc02767b8f5e3a6179";
    static final String SOURCE_JSON_SHA256 = "01ebcd4f5dd4e76c2a7091ae16929285700b5d569c6a6dbc831d8778705599d3";
    static final String RELEASED_DEFAULT_SHA256 = "f7d8137aedd2b7cd8304b9711ed0537af2a61aa6ca7e10fa35cf948d5c3f44f8";

    record Result(String json, Path backup) {
        boolean migrated() { return backup != null; }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /** Object key order and JSON whitespace do not matter; every value and array order does. */
    static String fingerprint(String json) {
        var canonical = new StringBuilder();
        canonicalize(JsonParser.parseString(json), canonical);
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void canonicalize(JsonElement value, StringBuilder output) {
        if (value.isJsonObject()) {
            output.append('{');
            boolean first = true;
            for (String key : value.getAsJsonObject().keySet().stream().sorted().toList()) {
                if (!first) output.append(',');
                first = false;
                output.append(new JsonPrimitive(key)).append(':');
                canonicalize(value.getAsJsonObject().get(key), output);
            }
            output.append('}');
        } else if (value.isJsonArray()) {
            output.append('[');
            boolean first = true;
            for (JsonElement entry : value.getAsJsonArray()) {
                if (!first) output.append(',');
                first = false;
                canonicalize(entry, output);
            }
            output.append(']');
        } else output.append(value);
    }

    static Result upgrade(Path path, String currentJson, String bundledJson) throws IOException {
        if (!RELEASED_DEFAULT_SHA256.equals(fingerprint(currentJson))) return new Result(currentJson, null);
        if (RELEASED_DEFAULT_SHA256.equals(fingerprint(bundledJson))) return new Result(currentJson, null);
        // Registry and recipe validation must succeed before creating a backup or changing the file.
        RecipeCatalog.parse(bundledJson);
        Path absolute = path.toAbsolutePath();
        byte[] original = Files.readAllBytes(absolute);
        if (!new String(original, StandardCharsets.UTF_8).equals(currentJson))
            throw new IOException("Recipe config changed while checking the released default; upgrade cancelled");

        Path backup = Files.createTempFile(absolute.getParent(), "recipes-1.5.2-", ".json.bak");
        Files.write(backup, original, StandardOpenOption.TRUNCATE_EXISTING);
        force(backup);
        Path replacement = null;
        try {
            replacement = Files.createTempFile(absolute.getParent(), ".recipes-upgrade-", ".tmp");
            Files.writeString(replacement, bundledJson, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            force(replacement);
            if (!Arrays.equals(original, Files.readAllBytes(absolute)))
                throw new IOException("Recipe config changed during backup; upgrade cancelled (backup: " + backup + ")");
            // Keep the original on filesystems that cannot replace it atomically.
            Files.move(replacement, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (replacement != null) Files.deleteIfExists(replacement);
        }
        return new Result(bundledJson, backup);
    }

    private static void force(Path path) throws IOException {
        try (var channel = FileChannel.open(path, StandardOpenOption.WRITE)) { channel.force(true); }
    }
}
