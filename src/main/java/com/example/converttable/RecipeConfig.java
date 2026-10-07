package com.example.converttable;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;

/** Server-owned config. Restart to reload; only complete known release defaults may be upgraded. */
public final class RecipeConfig {
    public static final Path PATH=FabricLoader.getInstance().getConfigDir().resolve("convert_table/recipes.json");
    private static volatile RecipeCatalog server=RecipeCatalog.empty();
    private static String serverJson="";
    public static boolean enabled() {return server.settings().has("execution_enabled") && server.settings().get("execution_enabled").getAsBoolean();}
    public static int setting(String section,String key) {
        var values=server.settings().getAsJsonObject(section);
        // Existing worlds retain their configuration; the new soul fee also applies there.
        if(section.equals("sculk")&&key.equals("ordinary_souls_per_batch")&&!values.has(key))return 1;
        return values.get(key).getAsInt();
    }
    public static RecipeCatalog server() {return server;}
    public static net.minecraft.world.item.Item fuelItem(int variant) {
        var settings=server.settings();
        if(settings.isEmpty())return variant==0?net.minecraft.world.item.Items.GOLD_NUGGET:net.minecraft.world.item.Items.CHORUS_FRUIT;
        String id=settings.getAsJsonObject(variant==0?"piglin":"end").get(variant==0?"cost":"fuel").getAsString();
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id));
    }
    public static String defaults() {
        try(var in=RecipeConfig.class.getResourceAsStream("/convert_table/default_recipes.json")) {
            if(in==null)throw new IOException("Missing bundled recipe defaults");
            return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        } catch(IOException e) {throw new IllegalStateException(e);}
    }
    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(RecipeSync.TYPE,RecipeSync.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GrowthSync.TYPE,GrowthSync.CODEC);
        ServerLifecycleEvents.SERVER_STARTING.register(game->load());
        ServerPlayConnectionEvents.JOIN.register((handler,sender,game)->{
            sender.sendPacket(new RecipeSync(serverJson));
            sender.sendPacket(new GrowthSync(GrowthRecipes.bundledJson()));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(game->{server=RecipeCatalog.empty();serverJson="";});
    }
    public static void load() {
        try {
            Files.createDirectories(PATH.getParent());
            if(!Files.exists(PATH)) Files.writeString(PATH,defaults(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            if(Files.size(PATH)>750_000)throw new IllegalArgumentException("Recipe config exceeds 750KB");
            String json=Files.readString(PATH,StandardCharsets.UTF_8);
            var migration=RecipeConfigMigration.upgrade(PATH,json,defaults());
            json=migration.json();
            RecipeCatalog parsed=RecipeCatalog.parse(json);
            server=parsed; serverJson=json;
            if(migration.migrated()) {
                String version=FabricLoader.getInstance().getModContainer("convert_table")
                    .map(mod->mod.getMetadata().getVersion().getFriendlyString()).orElse("bundled defaults");
                ConvertTable.LOGGER.info("Upgraded untouched {} recipe defaults to {}; backup: {}; source JAR SHA-256: {}; source JSON SHA-256: {}; canonical SHA-256: {}",
                    migration.sourceVersion(),version,migration.backup(),migration.sourceJarHash(),migration.sourceJsonHash(),migration.fingerprint());
            }
            ConvertTable.LOGGER.info("Conversion catalogue: {} groups, {} advanced entries; execution enabled: {}",parsed.groups().size(),parsed.advanced().size(),enabled());
            if(!parsed.unavailable().isEmpty())ConvertTable.LOGGER.warn("Unavailable item IDs excluded: {}",parsed.unavailable());
            Files.writeString(PATH.resolveSibling("unavailable-items.txt"),String.join("\n",parsed.unavailable()),StandardCharsets.UTF_8);
        } catch(Exception e) {throw new IllegalStateException("Invalid conversion config at "+PATH+" (file retained): "+e.getMessage(),e);}
    }
}
