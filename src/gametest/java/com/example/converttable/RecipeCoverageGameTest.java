package com.example.converttable;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;

/** Exhaustive default-input coverage through menu selection and production block-entity tickers. */
final class RecipeCoverageGameTest {
    private static final BlockPos ORIGIN = new BlockPos(24, 120, 24);
    private final List<String> failures = new ArrayList<>();
    private int ordinaryCases, advancedCases, growthCases;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private void scenario(String description, Runnable test) {
        try {
            test.run();
        } catch (AssertionError | RuntimeException failure) {
            String message = description + ": " + failure.getMessage();
            failures.add(message);
            ConvertTable.LOGGER.error("RECIPE_COVERAGE_FAIL: {}", message);
        }
    }

    private static String itemId(Item item) { return BuiltInRegistries.ITEM.getKey(item).toString(); }

    private static void ticks(ConversionTableBlockEntity table, int count) {
        for (int tick = 0; tick < count; tick++)
            ConversionTableBlockEntity.tick(table.getLevel(), table.getBlockPos(), table.getBlockState(), table);
    }

    private static void reset(ConversionTableBlockEntity table) {
        table.setRunning(false);
        table.cancelProcessing();
        table.clearContent();
        table.links.clear(true);
        table.links.clear(false);
        table.inputMode = table.matchMode = table.phase = 0;
        table.filterSource = null;
        table.target = BuiltInRegistries.ITEM.getKey(Items.AIR);
        table.deaths = 4096;
        if (table.variantIndex() < 2) table.setItem(1, new ItemStack(RecipeConfig.fuelItem(table.variantIndex()), 64));
    }

    private static void select(ConversionTableBlockEntity table, ServerPlayer player, Item target) {
        var menu = new ConversionTableMenu(90, player.getInventory(), table);
        check(menu.clickMenuButton(player, 1000 + BuiltInRegistries.ITEM.getId(target)),
            "menu rejected target " + itemId(target) + " status=" + table.status);
        check(table.target.equals(BuiltInRegistries.ITEM.getKey(target)), "menu did not install target");
    }

    private static void finish(ConversionTableBlockEntity table, boolean automatic, int cost) {
        int souls = table.deaths;
        int fuel = table.getItem(1).getCount();
        ItemStack input = table.getItem(0).copy(), catalyst = table.getItem(3).copy();
        if (automatic) {
            table.setRunning(true);
            ticks(table, 1);
        } else {
            check(table.convert(false), "convert rejected start status=" + table.status);
        }
        check(table.processing(), "start has no job status=" + table.status);
        ticks(table, table.totalTicks() - (automatic ? 2 : 1));
        check(ItemStack.matches(input, table.getItem(0)) && ItemStack.matches(catalyst, table.getItem(3))
            && table.deaths == souls && table.getItem(1).getCount() == fuel && table.getItem(2).isEmpty(),
            "spent ingredients or produced before duration status=" + table.status + " progress=" + table.progressTicks());
        ticks(table, 1);
        check(table.status == 1 && !table.processing(),
            "did not finish at duration status=" + table.status + " progress=" + table.progressTicks());
        check(table.deaths == souls - cost, "wrong soul fee " + souls + " -> " + table.deaths + " expected=" + cost);
        if (table.variantIndex() == 0)
            check(table.getItem(1).getCount() == fuel - RecipeConfig.setting("piglin", "cost_n"), "wrong gold fee");
        if (table.variantIndex() == 1) {
            int phaseCost = RecipeConfig.setting("end", "charge_per_batch");
            int fuelCharge = RecipeConfig.setting("end", "fuel_charge");
            int fuelUsed = (phaseCost + fuelCharge - 1) / fuelCharge;
            check(table.getItem(1).getCount() == fuel - fuelUsed && table.phase == fuelUsed * fuelCharge - phaseCost,
                "wrong end fuel/phase fee");
        }
        if (table.variantIndex() == 2) check(table.phase == 0 && table.getItem(1).isEmpty(), "sculk consumed phase fuel");
    }

    private void ordinary(RecipeCatalog.Group group, ConversionTableBlockEntity table,
                          ServerPlayer player, Item input, Item target, int amount, boolean automatic) {
        ordinaryCases++;
        String description = "ordinary id=" + group.id() + " variant=" + table.variant()
            + " input=" + itemId(input) + " output=" + itemId(target) + " count=" + amount + " auto=" + automatic;
        scenario(description, () -> {
            reset(table);
            ItemStack before = input.getDefaultInstance().copyWithCount(amount);
            table.setItem(0, before.copy());
            if (table.variantIndex() > 0) select(table, player, target);
            int cost = table.variantIndex() == 2 ? RecipeConfig.setting("sculk", "ordinary_souls_per_batch") : 0;
            finish(table, automatic, cost);
            ItemStack actual = table.getItem(2);
            Item expected = table.variantIndex() == 0 ? actual.getItem() : target;
            check(expected != input && group.items().contains(expected), "output escaped ordinary group " + itemId(expected));
            check(ItemStack.matches(before.transmuteCopy(expected, amount), actual), "output count/default components wrong");
            check(table.getItem(0).isEmpty(), "ordinary input not fully consumed");
        });
    }

    private void ordinaryCoverage(RecipeCatalog catalog, ConversionTableBlockEntity[] tables, ServerPlayer player) {
        for (var group : catalog.groups()) {
            for (int variant = group.tier(); variant < tables.length; variant++) {
                var table = tables[variant];
                for (Item input : group.items()) {
                    Set<Item> targets = new LinkedHashSet<>();
                    for (Item target : group.items()) if (target != input) { targets.add(target); break; }
                    if (variant > 0) for (int index = group.items().size() - 1; index >= 0; index--)
                        if (group.items().get(index) != input) { targets.add(group.items().get(index)); break; }
                    for (Item target : targets) ordinary(group, table, player, input, target, 1, false);
                }
                // A representative full legal stack also enters continuous mode from its ticker alone.
                Item input = group.items().getFirst(), target = group.items().get(1);
                int amount = Math.min(group.batch(), Math.min(input.getDefaultInstance().getMaxStackSize(),
                    target.getDefaultInstance().getMaxStackSize()));
                ordinary(group, table, player, input, target, amount, true);
            }
        }
    }

    private void advanced(RecipeCatalog.Advanced recipe, ConversionTableBlockEntity table, ServerPlayer player,
                          Item target, int mode, boolean automatic, Container source, Container destination) {
        advancedCases++;
        String description = "advanced id=" + recipe.id() + " input=" + itemId(recipe.input().getItem())
            + " output=" + itemId(target) + " mode=" + mode + " auto=" + automatic;
        scenario(description, () -> {
            reset(table);
            source.clearContent(); destination.clearContent();
            table.inputMode = mode;
            table.setItem(3, recipe.catalyst().copy());
            if (mode == 0) table.setItem(0, recipe.input().copy());
            else if (mode == 1) {
                table.setItem(0, recipe.input().copyWithCount(1));
                source.setItem(0, recipe.input().copy());
                BlockPos pos = table.getBlockPos();
                check(table.links.toggle(table.getLevel(), pos, pos.east(), Direction.UP, true) == 1, "source link failed");
                check(table.links.toggle(table.getLevel(), pos, pos.west(), Direction.UP, false) == 1, "output link failed");
            } else {
                var contents = NonNullList.withSize(27, ItemStack.EMPTY);
                contents.set(0, recipe.input().copy());
                ItemStack box = new ItemStack(Items.SHULKER_BOX);
                box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
                table.setItem(0, box);
            }
            select(table, player, target);
            if (!recipe.auto() && (mode != 0 || automatic)) {
                if (automatic) { table.setRunning(true); ticks(table, table.totalTicks()); }
                else { check(!table.convert(false), "manual-only recipe started in container mode"); ticks(table, table.totalTicks()); }
                check(table.status == 8 && !table.processing() && table.deaths == 4096
                    && ItemStack.matches(table.getItem(3), recipe.catalyst()), "manual-only restriction/fee wrong status=" + table.status);
                return;
            }
            finish(table, automatic, recipe.deaths());
            ItemStack actual;
            if (mode == 0) { actual = table.getItem(2); check(table.getItem(0).isEmpty(), "advanced input remains"); }
            else if (mode == 1) {
                actual = destination.getItem(0);
                check(source.getItem(0).isEmpty() && table.getItem(0).getCount() == 1, "linked input/sample accounting wrong");
            } else {
                var contents = table.getItem(0).get(DataComponents.CONTAINER).itemCopies().toList();
                actual = contents.getFirst();
                check(contents.stream().filter(stack -> !stack.isEmpty()).mapToInt(ItemStack::getCount).sum()
                    == recipe.output().getCount(), "nested input/output accounting wrong");
            }
            check(recipe.outputs().stream().anyMatch(output -> ItemStack.matches(output, actual)),
                "advanced output count/default components wrong: " + actual);
            check(table.getItem(3).isEmpty(), "catalyst remains");
            if (!recipe.returns().isEmpty()) check(ItemStack.matches(table.getItem(4), recipe.returns().getFirst()), "missing remainder");
        });
    }

    private void advancedCoverage(RecipeCatalog catalog, ConversionTableBlockEntity table, ServerPlayer player, ServerLevel level) {
        level.setBlockAndUpdate(table.getBlockPos().east(), Blocks.BARREL.defaultBlockState());
        level.setBlockAndUpdate(table.getBlockPos().west(), Blocks.BARREL.defaultBlockState());
        var source = (Container) level.getBlockEntity(table.getBlockPos().east());
        var destination = (Container) level.getBlockEntity(table.getBlockPos().west());
        for (var recipe : catalog.advanced()) {
            for (ItemStack output : recipe.outputs()) advanced(recipe, table, player, output.getItem(), 0, false, source, destination);
            Item target = recipe.output().getItem();
            advanced(recipe, table, player, target, 0, true, source, destination);
            advanced(recipe, table, player, target, 1, true, source, destination);
            advanced(recipe, table, player, target, 2, true, source, destination);
        }
    }

    private void growthCoverage(MinecraftServer server, ServerPlayer player) {
        var level = server.overworld();
        BlockPos sourcePos = ORIGIN.south(6), conductor = sourcePos.north(), mother = conductor.north();
        BlockPos pedestalPos = sourcePos.east(), bud = mother.north();
        List<BlockPos> placed = List.of(sourcePos, conductor, mother, pedestalPos, bud);
        long originalTime = level.getGameTime();
        try {
            for (BlockPos pos : placed) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(sourcePos, GrowthBlocks.CRYSTAL.defaultBlockState());
            level.setBlockAndUpdate(conductor, Blocks.AMETHYST_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(mother, Blocks.BUDDING_AMETHYST.defaultBlockState());
            level.setBlockAndUpdate(bud, Blocks.SMALL_AMETHYST_BUD.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.NORTH));
            level.setBlockAndUpdate(pedestalPos, GrowthBlocks.CATALYST.defaultBlockState());
            var source = (CrystalTableBlockEntity) level.getBlockEntity(sourcePos);
            var pedestal = (CatalystPedestalBlockEntity) level.getBlockEntity(pedestalPos);
            var worldData = server.getWorldData().overworldData();
            for (var recipe : GrowthRecipes.allRecipes()) {
                growthCases++;
                scenario("growth id=" + recipe.id() + " input=" + itemId(recipe.catalyst()) + " output=" + itemId(recipe.output()), () -> {
                    pedestal.clearContent();
                    pedestal.setItem(0, recipe.catalyst().getDefaultInstance().copy());
                    pedestal.setItem(CatalystPedestalBlockEntity.SOURCE_SLOT, new ItemStack(recipe.source()));
                    source.invalidateNetwork();
                    var network = source.snapshot();
                    check(network.usable() && network.available() == 1 && pedestal.crystal() == source,
                        "test network invalid available=" + network.available() + " flags=" + network.flags());
                    var menu = new CatalystPedestalMenu(91, player.getInventory(), pedestal);
                    check(menu.clickMenuButton(player, 1000 + pedestal.recipes().indexOf(recipe)), "growth menu rejected target status=" + pedestal.status());
                    check(pedestal.selectedRecipe() == recipe && menu.selectedRecipe() == recipe, "growth menu selected wrong recipe");
                    check(menu.clickMenuButton(player, 0) && pedestal.running(), "growth menu did not start");
                    long produced = pedestal.producedTotal();
                    long start = (level.getGameTime() / 20 + 1) * 20;
                    int duration = Math.multiplyExact(recipe.cost(), GrowthUnits.TICK_UNITS);
                    for (int tick = 0; tick <= duration; tick++) {
                        worldData.setGameTime(start + tick);
                        // Both production tickers run; no produce/acceptGrowth/advanceGrowth shortcuts.
                        CatalystPedestalBlockEntity.tick(level, pedestalPos, pedestal.getBlockState(), pedestal);
                        if (tick < duration) CrystalTableBlockEntity.tick(level, sourcePos, source.getBlockState(), source);
                        if (tick < duration) check(pedestal.producedTotal() == produced, "growth completed early tick=" + tick);
                    }
                    check(pedestal.producedTotal() == produced + 1 && pedestal.progressUnits() == 0,
                        "growth failed at actual cost status=" + pedestal.status() + " progress=" + pedestal.progressUnits());
                    check(ItemStack.matches(new ItemStack(recipe.output()), pedestal.getItem(1)), "growth output wrong");
                    check(ItemStack.matches(recipe.catalyst().getDefaultInstance(), pedestal.getItem(0)), "growth consumed catalyst");
                    check(ItemStack.matches(new ItemStack(recipe.source()), pedestal.getItem(CatalystPedestalBlockEntity.SOURCE_SLOT)), "growth consumed original");
                });
            }
        } finally {
            server.getWorldData().overworldData().setGameTime(originalTime);
            for (BlockPos pos : placed) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    static void run(MinecraftServer server) {
        var coverage = new RecipeCoverageGameTest();
        var level = server.overworld();
        var player = server.getPlayerList().getPlayers().getFirst();
        var originalPosition = player.position();
        var catalog = RecipeConfig.server();
        var defaults = RecipeCatalog.parse(RecipeConfig.defaults());
        var raw = JsonParser.parseString(RecipeConfig.defaults()).getAsJsonObject();
        check(RecipeConfig.enabled(), "Coverage requires enabled recipes");
        check(catalog.groups().equals(defaults.groups())
            && catalog.advanced().stream().map(RecipeCatalog.Advanced::id).toList().equals(defaults.advanced().stream().map(RecipeCatalog.Advanced::id).toList()),
            "Coverage world is using stale config instead of bundled defaults");
        check(defaults.unavailable().stream().noneMatch(id -> id.startsWith("minecraft:")), "Unavailable default vanilla item");
        for (var entry : raw.getAsJsonArray("advanced")) {
            String id = entry.getAsJsonObject().get("id").getAsString().toLowerCase(java.util.Locale.ROOT);
            if (catalog.advanced().stream().noneMatch(recipe -> recipe.id().equals(id)))
                ConvertTable.LOGGER.info("RECIPE_COVERAGE_SKIP: advanced id={} unavailable optional mod items", id);
        }
        var blocks = new ConversionTableBlock[]{ConversionTables.BLACK_GOLD, ConversionTables.END, ConversionTables.SCULK};
        var tables = new ConversionTableBlockEntity[blocks.length];
        try {
            player.setPos(ORIGIN.getX() + .5, ORIGIN.getY(), ORIGIN.getZ() + .5);
            for (int variant = 0; variant < blocks.length; variant++)
                tables[variant] = ConversionExecutionGameTest.place(level, ORIGIN.east(variant * 3), blocks[variant]);
            coverage.ordinaryCoverage(catalog, tables, player);
            coverage.advancedCoverage(catalog, tables[2], player, level);
            coverage.growthCoverage(server, player);
        } finally {
            player.setPos(originalPosition);
            for (var table : tables) if (table != null) {
                table.cancelProcessing(); table.clearContent();
                level.setBlockAndUpdate(table.getBlockPos(), Blocks.AIR.defaultBlockState());
            }
            level.setBlockAndUpdate(ORIGIN.east(5), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(ORIGIN.east(7), Blocks.AIR.defaultBlockState());
        }
        ConvertTable.LOGGER.info("RECIPE_COVERAGE_RESULT: sourceGroups={}, runtimeGroups={}, sourceAdvanced={}, runtimeAdvanced={}, growth={}, ordinaryCases={}, advancedCases={}, growthCases={}, failures={}",
            raw.getAsJsonArray("groups").size(), catalog.groups().size(), raw.getAsJsonArray("advanced").size(), catalog.advanced().size(),
            GrowthRecipes.allRecipes().size(), coverage.ordinaryCases, coverage.advancedCases, coverage.growthCases, coverage.failures.size());
        check(coverage.failures.isEmpty(), "Recipe coverage failed " + coverage.failures.size() + " cases; all IDs logged.\n"
            + String.join("\n", coverage.failures.subList(0, Math.min(30, coverage.failures.size()))));
        ConvertTable.LOGGER.info("RECIPE_COVERAGE_TEST_PASS: every ordinary input at each tier, first/last directed targets, legal batches with continuous ticker, every advanced/random target and mode restrictions, all growth recipes via both tickers and menus");
    }
}
