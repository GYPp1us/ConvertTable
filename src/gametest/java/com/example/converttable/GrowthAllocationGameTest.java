package com.example.converttable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Exercises the real source transaction in one server callback, including save/reload. */
final class GrowthAllocationGameTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void configure(CatalystPedestalBlockEntity pedestal, GrowthRecipes.Recipe recipe) {
        pedestal.setItem(0, new ItemStack(recipe.catalyst()));
        int index = pedestal.recipes().indexOf(recipe);
        check(pedestal.selectRecipe(index), "Growth target could not be selected: " + recipe.id());
        if (!pedestal.running()) pedestal.toggleRunning();
    }

    private static void fullCycle(CrystalTableBlockEntity source, List<CatalystPedestalBlockEntity> pedestals) {
        source.produce();
        for (var pedestal : pedestals) pedestal.advanceGrowth(source.getLevel().getGameTime() + 20);
    }

    static void verify(MinecraftServer game) {
        var catalog = GrowthRecipes.allRecipes();
        check(!catalog.isEmpty(), "Bundled growth catalog is empty");
        try (var resource = GrowthAllocationGameTest.class.getResourceAsStream("/data/convert_table/growth_recipes.json")) {
            if (resource == null) throw new AssertionError("Growth source is missing");
            var raw = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(resource, java.nio.charset.StandardCharsets.UTF_8));
            check(raw.getAsJsonObject().getAsJsonArray("recipes").size() == catalog.size(),
                "Bundled growth recipes contain an invalid ID/item; runtime silently dropped a source row");
        } catch (java.io.IOException e) { throw new AssertionError("Could not verify the bundled growth source", e); }
        var cheap = catalog.stream().min(Comparator.comparingInt(GrowthRecipes.Recipe::cost)).orElseThrow();
        var costly = catalog.stream().filter(recipe -> recipe.cost() > cheap.cost()).findFirst().orElseThrow();
        var level = game.overworld();
        var sourcePos = new BlockPos(6, 100, 6);
        var conductor = sourcePos.north();
        var mother = conductor.north();
        var positions = List.of(conductor.west(), conductor.above(), conductor.east());
        List<BlockPos> placed = new ArrayList<>(positions);
        placed.addAll(List.of(sourcePos, conductor, mother, mother.north(), mother.above(),
            mother.west(), mother.east()));
        try {
            for (BlockPos pos : placed) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(sourcePos, GrowthBlocks.CRYSTAL.defaultBlockState());
            level.setBlockAndUpdate(conductor, Blocks.AMETHYST_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(mother, Blocks.BUDDING_AMETHYST.defaultBlockState());
            List<CatalystPedestalBlockEntity> pedestals = new ArrayList<>();
            for (BlockPos pos : positions) {
                level.setBlockAndUpdate(pos, GrowthBlocks.CATALYST.defaultBlockState());
                var pedestal = (CatalystPedestalBlockEntity) level.getBlockEntity(pos);
                configure(pedestal, costly);
                pedestals.add(pedestal);
            }
            var source = (CrystalTableBlockEntity) level.getBlockEntity(sourcePos);
            bud(level, mother, Direction.NORTH);
            source.invalidateNetwork();
            source.produce();
            var growing = pedestals.getFirst();
            check(growing.credit() == 0 && growing.progressUnits() == 0 && growing.producedTotal() == 0,
                "Allocating factors produced an item instantly");
            growing.advanceGrowth(level.getGameTime() + 10);
            check(growing.progressUnits() == 10 && growing.credit() == 0 && growing.producedTotal() == 0,
                "A one-factor/second supply did not advance by half a factor in ten ticks");
            var halfSave = growing.saveWithFullMetadata(game.registryAccess());
            var halfCopy = (CatalystPedestalBlockEntity) BlockEntity.loadStatic(positions.getFirst(),
                level.getBlockState(positions.getFirst()), halfSave, game.registryAccess());
            check(halfCopy != null && halfCopy.progressUnits() == 10 && !halfCopy.processing(),
                "Reload lost earned fractional progress or recreated unearned factors");
            growing.advanceGrowth(level.getGameTime() + 20);
            check(source.lastSpent() == 1 && pedestals.getFirst().credit() == 1,
                "One factor was not assigned to the first capable pedestal");

            var pedestalSave = pedestals.getFirst().saveWithFullMetadata(game.registryAccess());
            var pedestalCopy = (CatalystPedestalBlockEntity) BlockEntity.loadStatic(positions.getFirst(),
                level.getBlockState(positions.getFirst()), pedestalSave, game.registryAccess());
            check(pedestalCopy != null && pedestalCopy.credit() == 1 && pedestalCopy.running()
                && pedestalCopy.selectedRecipe().id().equals(costly.id()), "Partial growth credit did not survive save/load");

            // Empty seconds must not advance the next recipient; reloading must retain it.
            level.setBlockAndUpdate(mother.north(), Blocks.AIR.defaultBlockState());
            source.invalidateNetwork();
            for (int i = 0; i < 4; i++) fullCycle(source, pedestals);
            var saved = source.saveWithFullMetadata(game.registryAccess());
            var restored = (CrystalTableBlockEntity) BlockEntity.loadStatic(sourcePos,
                level.getBlockState(sourcePos), saved, game.registryAccess());
            check(restored != null, "Crystal source could not be restored");
            level.setBlockEntity(restored);
            source = restored;
            bud(level, mother, Direction.NORTH);
            source.invalidateNetwork();
            fullCycle(source, pedestals);
            fullCycle(source, pedestals);
            for (var pedestal : pedestals)
                check(pedestal.credit() == 1 && pedestal.producedTotal() == 0,
                    "Intermittent factors or a reloaded source repeatedly favored one pedestal");

            configure(pedestals.getFirst(), cheap);
            long[] produced = pedestals.stream().mapToLong(CatalystPedestalBlockEntity::producedTotal).toArray();
            int[] credit = pedestals.stream().mapToInt(CatalystPedestalBlockEntity::credit).toArray();
            for (int i = 0; i < 6; i++) fullCycle(source, pedestals);
            for (int i = 0; i < pedestals.size(); i++) {
                var pedestal = pedestals.get(i);
                long gained = (pedestal.producedTotal() - produced[i]) * pedestal.cost()
                    + pedestal.credit() - credit[i];
                check(gained == 2, "Different item costs changed the factor share: " + gained);
            }

            var first = pedestals.getFirst();
            fill(first);
            level.setBlockAndUpdate(mother.east(), Blocks.CALCITE.defaultBlockState());
            source.invalidateNetwork();
            fullCycle(source, pedestals);
            check(first.status() == 5 && first.lastSpent() == 0
                && pedestals.get(1).lastSpent() == 1 && pedestals.get(2).lastSpent() == 1,
                "A full pedestal withheld factors from the other recipients");
            pedestals.get(1).toggleRunning();
            fullCycle(source, pedestals);
            check(pedestals.get(1).lastSpent() == 0 && pedestals.get(2).lastSpent() == 2,
                "A stopped pedestal retained a share of the network budget");

            // A recipient with room for one final item releases the rest of its fair share.
            first.setItem(1, new ItemStack(cheap.output(), cheap.output().getDefaultMaxStackSize() - 1));
            pedestals.get(1).toggleRunning();
            bud(level, mother, Direction.UP);
            bud(level, mother, Direction.WEST);
            source.invalidateNetwork();
            fullCycle(source, pedestals);
            check(source.lastSpent() == 6 && first.lastSpent() == cheap.cost()
                && Math.abs(pedestals.get(1).lastSpent() - pedestals.get(2).lastSpent()) <= 1,
                "Output demand did not cap and redistribute the fair share");

            // Pausing retains earned progress, but never finishes the rest of an allocated second.
            source.produce();
            var paused = pedestals.get(2);
            paused.advanceGrowth(level.getGameTime() + 10);
            int earned = paused.progressUnits();
            long completed = paused.producedTotal();
            paused.toggleRunning();
            paused.advanceGrowth(level.getGameTime() + 20);
            check(paused.progressUnits() == earned && paused.producedTotal() == completed && !paused.processing(),
                "Paused growth continued earning factors or discarded earned progress");
            paused.toggleRunning();
            paused.advanceGrowth(level.getGameTime() + 20);
            check(paused.progressUnits() == earned && paused.producedTotal() == completed,
                "Resuming resurrected the unearned remainder of an old allocation");

            source.produce();
            completed = paused.producedTotal();
            level.setBlockAndUpdate(conductor, Blocks.AIR.defaultBlockState());
            source.invalidateNetwork();
            paused.advanceGrowth(level.getGameTime() + 20);
            check(paused.producedTotal() == completed && !paused.processing(),
                "A detached pedestal produced from a stale allocated budget");
            ConvertTable.LOGGER.info("GROWTH_ALLOCATION_TEST_PASS: real tick growth, saved fractional progress, equal factors across costs, rotating remainder, idle seconds, saved cursor/credit, full/stopped exclusion and demand redistribution");
        } finally {
            for (BlockPos pos : placed) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    private static void fill(CatalystPedestalBlockEntity pedestal) {
        for (int slot = CatalystPedestalBlockEntity.OUTPUT_FIRST; slot <= CatalystPedestalBlockEntity.OUTPUT_LAST; slot++)
            pedestal.setItem(slot, new ItemStack(Items.DIRT, 64));
    }

    private static void bud(net.minecraft.world.level.Level level, BlockPos mother, Direction face) {
        level.setBlockAndUpdate(mother.relative(face), Blocks.SMALL_AMETHYST_BUD.defaultBlockState()
            .setValue(AmethystClusterBlock.FACING, face));
    }
}
