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
import java.util.Map;

/** Upgrades only complete, untouched defaults from explicitly fingerprinted published artifacts. */
final class RecipeConfigMigration {
    // Extracted from convert-table-1.5.2.jar / convert_table/default_recipes.json.
    // The old document is deliberately not another production recipe resource.
    static final String SOURCE_JAR_SHA256 = "13a82aa85bd2d36bdf4137f41c51485298bd2ca5217e2ebc02767b8f5e3a6179";
    static final String SOURCE_JSON_SHA256 = "01ebcd4f5dd4e76c2a7091ae16929285700b5d569c6a6dbc831d8778705599d3";
    static final String RELEASED_DEFAULT_SHA256 = "f7d8137aedd2b7cd8304b9711ed0537af2a61aa6ca7e10fa35cf948d5c3f44f8";
    static final String V020_JSON_SHA256 = "bd18233af0cc0d018ca240f32cbe2684e8508bf358cbe76ce2a74e88ea2e8967";
    static final String V020_DEFAULT_SHA256 = "f6fed35a32dc62fc7281faf368da305f8f79b77c89f3ef2a663328edb85e8f9d";
    static final String V030_JSON_SHA256 = "79d4e2a5ffb7c4badf520dd4be0dbd80caa2354537287dcb146a10953a4fbcd1";
    static final String V030_DEFAULT_SHA256 = "f018be335fbe2a7a2e33c160a8b3c7bbfbc91c691e5dac1ae342b3279bc1d47f";
    static final String V031_JSON_SHA256 = "25edb7646654e6b87aca0b0676e0f2c7b05c1bdd9d919cbcd0e4235288db986f";
    static final String V031_DEFAULT_SHA256 = "02e078401c300a4e17501ebd3c7b5cde6a3fd80ad2a41239dd31d6b8fec0364b";
    private record Released(String version, String jarHash, String jsonHash) { }
    private static final Map<String, Released> RELEASES = Map.of(
        RELEASED_DEFAULT_SHA256, new Released("1.5.2", SOURCE_JAR_SHA256, SOURCE_JSON_SHA256),
        V020_DEFAULT_SHA256, new Released("0.2.0", "463a4b86e819ea1dbedc79c61a96a42c8e8aabeaf1017a49ff0c6f6d86bf7152", V020_JSON_SHA256),
        V030_DEFAULT_SHA256, new Released("0.3.0", "aca60a0633b861267bbd920ffc62db6c9fac7365f49d670c3d9e1a3eda530af5", V030_JSON_SHA256),
        V031_DEFAULT_SHA256, new Released("0.3.1", "00aeae4277cf3cecc2d704f1b2a77176d0a6cc3ea8822fdbab236943510c8302", V031_JSON_SHA256));

    record Result(String json, Path backup, String sourceVersion, String sourceJarHash, String sourceJsonHash, String fingerprint) {
        Result(String json, Path backup) { this(json,backup,null,null,null,null); }
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
        String currentFingerprint = fingerprint(currentJson);
        Released source = RELEASES.get(currentFingerprint);
        if (source == null || currentFingerprint.equals(fingerprint(bundledJson))) return new Result(currentJson, null);
        // Registry and recipe validation must succeed before creating a backup or changing the file.
        RecipeCatalog.parse(bundledJson);
        Path absolute = path.toAbsolutePath();
        byte[] original = Files.readAllBytes(absolute);
        if (!new String(original, StandardCharsets.UTF_8).equals(currentJson))
            throw new IOException("Recipe config changed while checking the released default; upgrade cancelled");

        Path backup = Files.createTempFile(absolute.getParent(), "recipes-"+source.version()+"-", ".json.bak");
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
        return new Result(bundledJson, backup, source.version(), source.jarHash(), source.jsonHash(), currentFingerprint);
    }

    private static void force(Path path) throws IOException {
        try (var channel = FileChannel.open(path, StandardOpenOption.WRITE)) { channel.force(true); }
    }
}
