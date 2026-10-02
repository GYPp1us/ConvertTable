package com.example.converttable;

import com.google.gson.JsonParser;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.Items;

/** Uses the isolated UI test's server; no user config or world is edited. */
final class ConversionRecipeGameTest {
    static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    static void verifyConfig() {
        RecipeConfigMigrationGameTest.run();
        var raw=JsonParser.parseString(RecipeConfig.defaults()).getAsJsonObject();
        check(raw.getAsJsonArray("groups").size()>=107,"Missing Farmer's Delight groups");
        check(raw.getAsJsonArray("advanced").size()>=83,"Missing new advanced recipes");
        var catalog=RecipeCatalog.parse(raw.toString());
        var legacy=raw.deepCopy();
        legacy.getAsJsonObject("settings").getAsJsonObject("sculk").remove("ordinary_souls_per_batch");
        check(RecipeCatalog.parse(legacy.toString()).advanced().size()==catalog.advanced().size(),
            "A legacy configuration without the ordinary soul fee was rejected");
        var freeSouls=raw.deepCopy();
        freeSouls.getAsJsonObject("settings").getAsJsonObject("sculk").addProperty("ordinary_souls_per_batch",0);
        rejected(freeSouls.toString(),"zero ordinary soul cost");
        check(catalog.unavailable().stream().noneMatch(id->id.startsWith("minecraft:")),"Bundled recipe references missing vanilla item IDs");
        check(catalog.groups().size()>80 && catalog.advanced().size()>40,"Too many recipes unexpectedly lost");
        var coral=catalog.advanced().stream().filter(r->r.source().matches("ADV-0(09|10|11)")).toList();
        check(coral.size()==15,"Missing coral variants");
        for(var r:coral) {
            var from=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(r.input().getItem()).getPath();
            var to=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(r.output().getItem()).getPath();
            check(from.equals("dead_"+to),"Cross-species coral mapping");
            check(r.returns().size()==1 && r.returns().getFirst().is(Items.BUCKET),"Water bucket remainder missing");
        }
        var duplicate=raw.deepCopy();duplicate.getAsJsonArray("groups").add(duplicate.getAsJsonArray("groups").get(0).deepCopy());
        rejected(duplicate.toString(),"duplicate group");
        var negative=raw.deepCopy();negative.getAsJsonArray("advanced").get(0).getAsJsonObject().addProperty("deaths",-1);
        rejected(negative.toString(),"negative death cost");
        var badPool=raw.deepCopy();
        var badRecipe=badPool.getAsJsonArray("advanced").get(0).getAsJsonObject();
        var outputs=new com.google.gson.JsonArray();outputs.add("minecraft:dirt");outputs.add("minecraft:stone");
        badRecipe.addProperty("output","minecraft:diamond");badRecipe.add("outputs",outputs);
        rejected(badPool.toString(),"primary output outside random pool");
        var missingPool=raw.deepCopy();
        var missingRecipe=missingPool.getAsJsonArray("advanced").get(0).getAsJsonObject();
        var missingOutputs=new com.google.gson.JsonArray();
        missingOutputs.add("test_missing:first");missingOutputs.add("test_missing:second");
        missingRecipe.addProperty("output","test_missing:first");missingRecipe.add("outputs",missingOutputs);
        check(RecipeCatalog.parse(missingPool.toString()).advanced().size()==catalog.advanced().size()-1,
            "Completely unavailable random pool must be omitted");
        var xp=raw.deepCopy();xp.getAsJsonObject("settings").getAsJsonObject("sculk").addProperty("consume_player_xp",true);
        rejected(xp.toString(),"experience cost");
        var unknown=raw.deepCopy();unknown.getAsJsonArray("groups").get(0).getAsJsonObject().getAsJsonArray("items").add("minecraft:missing_test_item");
        check(RecipeCatalog.parse(unknown.toString()).unavailable().contains("minecraft:missing_test_item"),"Unknown items not reported");
        var disabled=raw.deepCopy();disabled.getAsJsonArray("groups").get(0).getAsJsonObject().addProperty("enabled",false);
        check(RecipeCatalog.parse(disabled.toString()).groups().size()==catalog.groups().size()-1,"Disabled group still available");
        check(catalog.advanced().stream().anyMatch(r->r.id().equals("life-cod")),"Fish revival missing");
        boolean farmers=FabricLoader.getInstance().isModLoaded("farmersdelight");
        check(catalog.advanced().stream().anyMatch(r->r.id().equals("fd-draw-01"))==farmers,"Optional seed pool availability wrong");
        ConvertTable.LOGGER.info("RECIPE_CONFIG_TEST_PASS: {} groups, {} plans, optional Farmers={}, validation/coral/remainders",catalog.groups().size(),catalog.advanced().size(),farmers);
    }
    private static void rejected(String json,String reason) {
        try {RecipeCatalog.parse(json);}catch(IllegalArgumentException expected){return;}
        throw new AssertionError("Accepted invalid "+reason);
    }
    static void verifyClient(ClientGameTestContext context) {
        context.waitFor(mc->!ClientRecipeCatalog.current().groups().isEmpty(),100);
        context.runOnClient(mc-> {
            check(ClientRecipeCatalog.current().groups().size()==RecipeConfig.server().groups().size(),"Server catalogue not synchronized");
            check(ClientRecipeCatalog.current().advanced().size()==RecipeConfig.server().advanced().size(),"Advanced catalogue not synchronized");
            var all=ViewerRecipe.all();Set<String> ids=new HashSet<>();
            for(var r:all) {
                check(ids.add(r.id()),"Duplicate viewer recipe");
                if(r.category()<3)check(r.outputs().stream().noneMatch(s->s.is(r.input().getItem())),"Ordinary conversion includes unchanged output");
                if(r.category()==0)check(r.reagent().is(Items.GOLD_NUGGET),"Piglin cost missing");
                if(r.category()==2)check(r.deaths()==RecipeConfig.setting("sculk","ordinary_souls_per_batch")
                    &&r.phase()==0&&r.reagent().isEmpty(),"Ordinary viewer soul fee or removed fuel is incorrect");
            }
            check(all.stream().anyMatch(r->r.category()==3 && r.deaths()>0),"Death-cost recipes missing");
            ConvertTable.LOGGER.info("RECIPE_SYNC_TEST_PASS: {} viewer recipes; {} unavailable IDs",all.size(),ClientRecipeCatalog.current().unavailable().size());
        });
    }
    static void viewers(ClientGameTestContext context) {
        if(FabricLoader.getInstance().isModLoaded("jei"))JeiTest.run(context);
        if(FabricLoader.getInstance().isModLoaded("roughlyenoughitems"))ReiTest.run(context);
    }
    private static final class JeiTest {
        static void run(ClientGameTestContext context) {
            context.waitFor(mc->com.example.converttable.compat.ConversionJeiPlugin.activeRuntime()!=null,600);
            context.runOnClient(mc->{
                var runtime=com.example.converttable.compat.ConversionJeiPlugin.activeRuntime();
                for(int c=0;c<4;c++) {
                    final int category=c;
                    long expected=ViewerRecipe.all().stream().filter(r->r.category()==category).count();
                    long actual=runtime.getRecipeManager().createRecipeLookup(com.example.converttable.compat.ConversionJeiPlugin.TYPES.get(c)).get().count();
                    check(actual==expected,"JEI recipe count mismatch: "+c+" / "+actual+" != "+expected);
                }
                runtime.getRecipesGui().showTypes(List.of(com.example.converttable.compat.ConversionJeiPlugin.TYPES.get(3)));
            });
            context.waitTicks(15);
            context.takeScreenshot(TestScreenshotOptions.of("jei-sculk-recipes"));
            ConvertTable.LOGGER.info("JEI_RECIPE_TEST_PASS: four categories and indexed server catalogue");
        }
    }
    private static final class ReiTest {
        static void run(ClientGameTestContext context) {
            context.waitTicks(60);
            context.runOnClient(mc-> {
                var registry=me.shedaniel.rei.api.client.registry.category.CategoryRegistry.getInstance();
                for(var type:com.example.converttable.compat.ConversionReiPlugin.TYPES)
                    check(registry.tryGet(type).isPresent(),"REI category missing");
                var generators=me.shedaniel.rei.api.client.registry.display.DisplayRegistry.getInstance()
                    .getCategoryDisplayGenerators(com.example.converttable.compat.ConversionReiPlugin.TYPES.get(3));
                long eggs=generators.stream().mapToLong(g->g.getUsageFor(me.shedaniel.rei.api.common.util.EntryStacks.of(Items.EGG))
                    .map(List::size).orElse(0)).sum();
                check(eggs==24,"REI egg usage index must contain 24 spawn egg plans, got "+eggs);
                check(me.shedaniel.rei.api.client.view.ViewSearchBuilder.builder()
                    .addCategory(com.example.converttable.compat.ConversionReiPlugin.TYPES.get(3))
                    .setPreferredOpenedCategory(com.example.converttable.compat.ConversionReiPlugin.TYPES.get(3)).open(),"REI failed to open custom recipes");
            });
            context.waitTicks(15);
            context.takeScreenshot(TestScreenshotOptions.of("rei-sculk-recipes"));
            ConvertTable.LOGGER.info("REI_RECIPE_TEST_PASS: four categories, dynamic server catalogue and recipe screen");
        }
    }
}
