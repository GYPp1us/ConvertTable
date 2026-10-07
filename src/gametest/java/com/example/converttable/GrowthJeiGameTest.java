package com.example.converttable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

/** The outer class runs without JEI; optional API references stay in the nested test. */
final class GrowthJeiGameTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void run(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            var catalog = GrowthRecipes.allRecipes();
            var view = GrowthViewerRecipe.all();
            check(!view.isEmpty() && view.size() == catalog.size(), "Growth viewer lost bundled recipes");
            Set<net.minecraft.resources.Identifier> ids = new HashSet<>();
            for (var recipe : view) {
                var source = GrowthRecipes.recipe(recipe.identifier());
                check(ids.add(recipe.identifier()) && source != null, "Growth viewer recipe has a duplicate/unknown ID");
                check(recipe.catalyst().is(source.catalyst()) && recipe.catalyst().getCount() == 1
                    && recipe.source().is(source.source()) && recipe.source().getCount() == 1
                    && recipe.output().is(source.output()) && recipe.output().getCount() == 1
                    && recipe.cost() == source.cost(), "Growth viewer differs from its source recipe");
            }
        });
        if (FabricLoader.getInstance().isModLoaded("jei")) Jei.run(context);
        else ConvertTable.LOGGER.info("GROWTH_NO_JEI_TEST_PASS: {} recipes usable without JEI", GrowthViewerRecipe.all().size());
    }

    static void verifyServerSync(ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        String original=GrowthRecipes.bundledJson();
        var document=com.google.gson.JsonParser.parseString(original).getAsJsonObject();
        var only=new com.google.gson.JsonArray();
        var row=document.getAsJsonArray("recipes").get(0).deepCopy().getAsJsonObject();
        row.addProperty("catalyst","minecraft:oak_boat");
        only.add(row); document.add("recipes",only);
        String changed=document.toString();
        try {
            server.runOnServer(game->net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                game.getPlayerList().getPlayers().getFirst(),new GrowthSync(changed)));
            context.waitFor(mc->GrowthViewerRecipe.all().size()==1,100);
            context.runOnClient(mc->{
                var menu=new CatalystPedestalMenu(92,mc.player.getInventory());
                check(menu.getSlot(0).mayPlace(new ItemStack(net.minecraft.world.item.Items.OAK_BOAT))
                    && !menu.getSlot(0).mayPlace(new ItemStack(net.minecraft.world.item.Items.OAK_SAPLING)),
                    "Client menu ignored the received server growth catalogue");
            });
            if(FabricLoader.getInstance().isModLoaded("jei")) Jei.checkSyncedCount(context,1);
        } finally {
            server.runOnServer(game->net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                game.getPlayerList().getPlayers().getFirst(),new GrowthSync(original)));
            context.waitFor(mc->GrowthViewerRecipe.all().size()==GrowthRecipes.allRecipes().size(),100);
            if(FabricLoader.getInstance().isModLoaded("jei")) Jei.checkSyncedCount(context,GrowthRecipes.allRecipes().size());
        }
        ConvertTable.LOGGER.info("GROWTH_SERVER_SYNC_TEST_PASS: actual server packets replace client catalyst/menu/JEI independently of ordinary recipes, then restore all 105 entries");
    }

    private static final class Jei {
        static void checkSyncedCount(ClientGameTestContext context,int expected) {
            context.waitFor(mc->com.example.converttable.compat.ConversionJeiPlugin.activeRuntime()!=null,600);
            context.runOnClient(mc->check(com.example.converttable.compat.ConversionJeiPlugin.activeRuntime().getRecipeManager()
                .createRecipeLookup(com.example.converttable.compat.ConversionJeiPlugin.GROWTH_TYPE).get().count()==expected,
                "JEI kept stale growth recipes after a server catalogue packet"));
        }
        static void run(ClientGameTestContext context) {
            context.waitFor(mc -> com.example.converttable.compat.ConversionJeiPlugin.activeRuntime() != null, 600);
            context.runOnClient(mc -> {
                var runtime = com.example.converttable.compat.ConversionJeiPlugin.activeRuntime();
                var manager = runtime.getRecipeManager();
                var type = com.example.converttable.compat.ConversionJeiPlugin.GROWTH_TYPE;
                var category = manager.getRecipeCategory(type);
                check(category != null, "JEI growth category is missing");
                var all = GrowthViewerRecipe.all();
                check(manager.createRecipeLookup(type).get().count() == all.size(), "JEI growth count differs from the bundled catalog");
                var focuses = runtime.getJeiHelpers().getFocusFactory();
                Set<net.minecraft.world.item.Item> catalysts = new HashSet<>(), outputs = new HashSet<>();
                for (var recipe : all) {
                    for(ItemStack input:List.of(recipe.catalyst(),recipe.source())) if (catalysts.add(input.getItem())) {
                        var focus = focuses.createFocus(mezz.jei.api.recipe.RecipeIngredientRole.INPUT,
                            mezz.jei.api.constants.VanillaTypes.ITEM_STACK, input);
                        Set<net.minecraft.resources.Identifier> actual = new HashSet<>();
                        manager.createRecipeLookup(type).limitFocus(List.of(focus)).get()
                            .forEach(found -> actual.add(found.identifier()));
                        var expected = all.stream().filter(found -> found.catalyst().is(input.getItem()) || found.source().is(input.getItem()))
                            .map(GrowthViewerRecipe::identifier).collect(java.util.stream.Collectors.toSet());
                        check(actual.equals(expected), "JEI U index omitted catalyst/original targets: " + input);
                    }
                    if (outputs.add(recipe.output().getItem())) {
                        var focus = focuses.createFocus(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT,
                            mezz.jei.api.constants.VanillaTypes.ITEM_STACK, recipe.output());
                        Set<net.minecraft.resources.Identifier> actual = new HashSet<>();
                        manager.createRecipeLookup(type).limitFocus(List.of(focus)).get()
                            .forEach(found -> actual.add(found.identifier()));
                        var expected = all.stream().filter(found -> found.output().is(recipe.output().getItem()))
                            .map(GrowthViewerRecipe::identifier).collect(java.util.stream.Collectors.toSet());
                        check(actual.equals(expected), "JEI R index omitted growth outputs: " + recipe.output());
                    }
                    var layout = manager.createRecipeLayoutDrawable(category, recipe, focuses.getEmptyFocusGroup()).orElseThrow();
                    var slots = layout.getRecipeSlotsView();
                    check(slots.findSlotByName("catalyst").orElseThrow().getRole() == mezz.jei.api.recipe.RecipeIngredientRole.INPUT
                        && slots.findSlotByName("source").orElseThrow().getRole() == mezz.jei.api.recipe.RecipeIngredientRole.INPUT
                        && slots.findSlotByName("source").orElseThrow().getDisplayedItemStack().orElseThrow().is(recipe.output().getItem())
                        && slots.findSlotByName("output").orElseThrow().getDisplayedItemStack().orElseThrow().getCount() == 1,
                        "Growth catalyst/output layout or amount is incorrect");
                }
                var stations = manager.createCraftingStationLookup(type).getItemStack().map(ItemStack::getItem).toList();
                check(stations.contains(GrowthBlocks.CATALYST.asItem()) && stations.contains(GrowthBlocks.CRYSTAL.asItem()),
                    "JEI growth is missing a required workstation");
                for (var station : List.of(GrowthBlocks.CATALYST, GrowthBlocks.CRYSTAL)) {
                    // JEI's U key queries both roles; a workstation is not a consumed recipe input.
                    var useFocus = focuses.createFocus(mezz.jei.api.recipe.RecipeIngredientRole.INPUT,
                        mezz.jei.api.constants.VanillaTypes.ITEM_STACK, new ItemStack(station));
                    var stationFocus = focuses.createFocus(mezz.jei.api.recipe.RecipeIngredientRole.CRAFTING_STATION,
                        mezz.jei.api.constants.VanillaTypes.ITEM_STACK, new ItemStack(station));
                    var queries = List.of(useFocus, stationFocus);
                    check(manager.createRecipeCategoryLookup().limitFocus(queries).get()
                        .anyMatch(found -> found.getRecipeType().equals(type)),
                        "JEI U query on workstation omitted the growth category: " + station);
                    check(manager.createRecipeLookup(type).limitFocus(queries).get().count() == all.size(),
                        "JEI workstation query omitted growth recipes: " + station);
                }
                runtime.getRecipesGui().showTypes(List.of(type));
            });
            context.waitTicks(15);
            context.takeScreenshot(TestScreenshotOptions.of("jei-growth-recipes"));
            context.runOnClient(mc -> mc.gui.screen().onClose());
            ConvertTable.LOGGER.info("GROWTH_JEI_TEST_PASS: bundled recipes, catalyst U/output R indexes, reusable catalyst layout and both workstations");
        }
    }
}
