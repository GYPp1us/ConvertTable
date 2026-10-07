package com.example.converttable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ServerLevelData;

/** Real production transactions, fractional save/reload, bulk fairness and indexed network boundaries. */
final class GrowthAllocationGameTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void configure(CatalystPedestalBlockEntity pedestal, GrowthRecipes.Recipe recipe) {
        pedestal.setItem(0, new ItemStack(recipe.catalyst()));
        pedestal.setItem(10, new ItemStack(recipe.output()));
        int index = pedestal.recipes().indexOf(recipe);
        check(pedestal.selectRecipe(index), "Growth target could not be selected: " + recipe.id());
        if (!pedestal.running()) pedestal.toggleRunning();
    }

    private static void fullCycle(CrystalTableBlockEntity source, List<CatalystPedestalBlockEntity> pedestals) {
        source.produce();
        long end = source.getLevel().getGameTime() + 20;
        for (var pedestal : pedestals) pedestal.advanceGrowth(end);
        ((ServerLevelData) source.getLevel().getLevelData()).setGameTime(end);
    }

    static void verify(MinecraftServer game) {
        verifyBulkAllocation();
        verifyMenuNumbers();
        var catalog = GrowthRecipes.allRecipes();
        check(!catalog.isEmpty(), "Bundled growth catalog is empty");
        var cheap = catalog.stream().min(Comparator.comparingInt(GrowthRecipes.Recipe::cost)).orElseThrow();
        var costly = catalog.stream().filter(recipe -> recipe.cost() > cheap.cost()).findFirst().orElseThrow();
        var level = game.overworld();
        long originalTime = level.getGameTime();
        var sourcePos = new BlockPos(6, 100, 6);
        var conductor = sourcePos.north();
        var mother = conductor.north();
        var positions = List.of(conductor.west(), conductor.above(), conductor.east());
        List<BlockPos> placed = new ArrayList<>(positions);
        placed.addAll(List.of(sourcePos, conductor, mother, mother.north(), mother.above(), mother.west(), mother.east()));
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
            source.produce();
            var growing = pedestals.getFirst();
            check(growing.progressUnits() == 0 && growing.producedTotal() == 0,
                "Allocating microfactors produced an item instantly");
            growing.advanceGrowth(level.getGameTime() + 10);
            check(growing.progressUnits() == 10 * GrowthUnits.UNITS_PER_MICRO_TICK && growing.credit() == 0 && growing.producedTotal() == 0,
                "One microfactor/second did not earn exactly 1/2000 factor in ten ticks");
            var halfSave = growing.saveWithFullMetadata(game.registryAccess());
            var halfCopy = (CatalystPedestalBlockEntity) BlockEntity.loadStatic(positions.getFirst(),
                level.getBlockState(positions.getFirst()), halfSave, game.registryAccess());
            check(halfCopy != null && halfCopy.progressUnits() == 10 * GrowthUnits.UNITS_PER_MICRO_TICK && !halfCopy.processing(),
                "Reload lost fractional microfactor progress or recreated unearned factors");
            growing.advanceGrowth(level.getGameTime() + 20);
            check(source.lastSpent() == 1 && growing.progressUnits() == 20 * GrowthUnits.UNITS_PER_MICRO_TICK && growing.credit() == 0,
                "One microfactor was not assigned intact or incorrectly became a whole factor");
            source.produce();
            check(growing.progressUnits() == 20 * GrowthUnits.UNITS_PER_MICRO_TICK, "A second controller transaction reused the same physical second");
            ((ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + 20);

            var pedestalSave = growing.saveWithFullMetadata(game.registryAccess());
            var pedestalCopy = (CatalystPedestalBlockEntity) BlockEntity.loadStatic(positions.getFirst(),
                level.getBlockState(positions.getFirst()), pedestalSave, game.registryAccess());
            check(pedestalCopy != null && pedestalCopy.progressUnits() == 20 * GrowthUnits.UNITS_PER_MICRO_TICK && pedestalCopy.running()
                && pedestalCopy.selectedRecipe().id().equals(costly.id()), "Partial credit did not survive save/load");
            for (int scale : new int[]{1, 64}) {
                var legacy = pedestalSave.copy();
                legacy.putInt("GrowthProgressScale", scale);
                legacy.putInt("GrowthFraction", scale * 20 - 1);
                legacy.putInt("Credit", 1);
                var legacyCopy = (CatalystPedestalBlockEntity) BlockEntity.loadStatic(positions.getFirst(),
                    level.getBlockState(positions.getFirst()), legacy, game.registryAccess());
                check(legacyCopy.progressUnits() == GrowthUnits.TICK_UNITS
                    + GrowthUnits.restoreFraction(scale * 20 - 1, scale), "Native legacy save lost earned credit");
            }

            level.setBlockAndUpdate(mother.north(), Blocks.AIR.defaultBlockState());
            for (int i = 0; i < 4; i++) fullCycle(source, pedestals);
            var saved = source.saveWithFullMetadata(game.registryAccess());
            var restored = (CrystalTableBlockEntity) BlockEntity.loadStatic(sourcePos,
                level.getBlockState(sourcePos), saved, game.registryAccess());
            check(restored != null, "Crystal source could not be restored");
            level.setBlockEntity(restored);
            source = restored;
            bud(level, mother, Direction.NORTH);
            fullCycle(source, pedestals);
            fullCycle(source, pedestals);
            for (var pedestal : pedestals)
                check(pedestal.progressUnits() == 20 * GrowthUnits.UNITS_PER_MICRO_TICK && pedestal.producedTotal() == 0,
                    "Intermittent factors or a reloaded source repeatedly favored one pedestal");

            configure(pedestals.getFirst(), cheap);
            long[] produced = pedestals.stream().mapToLong(CatalystPedestalBlockEntity::producedTotal).toArray();
            long[] progress = pedestals.stream().mapToLong(CatalystPedestalBlockEntity::progressUnits).toArray();
            for (int i = 0; i < 6; i++) fullCycle(source, pedestals);
            for (int i = 0; i < pedestals.size(); i++) {
                var pedestal = pedestals.get(i);
                long gained = (pedestal.producedTotal() - produced[i]) * pedestal.cost() * GrowthUnits.TICK_UNITS
                    + pedestal.progressUnits() - progress[i];
                check(gained == 40 * GrowthUnits.UNITS_PER_MICRO_TICK, "Different item costs changed the microfactor share: " + gained);
            }

            var first = pedestals.getFirst();
            fill(first);
            level.setBlockAndUpdate(mother.east(), Blocks.CALCITE.defaultBlockState());
            fullCycle(source, pedestals);
            check(first.status() == 5 && first.lastSpent() == 0
                && pedestals.get(1).lastSpent() == 1 && pedestals.get(2).lastSpent() == 1,
                "A full pedestal withheld microfactors from other recipients");
            pedestals.get(1).toggleRunning();
            fullCycle(source, pedestals);
            check(pedestals.get(1).lastSpent() == 0 && pedestals.get(2).lastSpent() == 2,
                "A stopped pedestal retained a share of the network budget");

            source.produce();
            var paused = pedestals.get(2);
            paused.advanceGrowth(level.getGameTime() + 10);
            long earned = paused.progressUnits(), completed = paused.producedTotal();
            paused.toggleRunning();
            paused.advanceGrowth(level.getGameTime() + 20);
            check(paused.progressUnits() == earned && paused.producedTotal() == completed && !paused.processing(),
                "Pausing continued earning microfactors or discarded earned progress");
            paused.toggleRunning();
            paused.advanceGrowth(level.getGameTime() + 20);
            check(paused.progressUnits() == earned && paused.producedTotal() == completed,
                "Resuming resurrected the unearned remainder of an old allocation");
            ((ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + 20);
            source.produce();
            earned = paused.progressUnits();
            level.setBlockAndUpdate(conductor, Blocks.AIR.defaultBlockState());
            paused.advanceGrowth(level.getGameTime() + 20);
            check(paused.progressUnits() == earned && !paused.processing(),
                "A detached pedestal earned from a stale allocated budget");

            // The actual ticker threshold is cost * TICK_UNITS: all fractional shares survive.
            level.setBlockAndUpdate(conductor, Blocks.AMETHYST_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(mother.east(), Blocks.AIR.defaultBlockState());
            configure(paused, cheap);
            paused.setItem(0, ItemStack.EMPTY);
            configure(paused, cheap); // Explicitly reset prior earned credit.
            long before = paused.producedTotal();
            ((ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + 20);
            for (int interval = 0; interval < cheap.cost() * GrowthUnits.DIVISOR; interval++) {
                fullCycle(source, pedestals);
                if (interval + 1 < cheap.cost() * GrowthUnits.DIVISOR)
                    check(paused.producedTotal() == before, "A 1/1000 source completed before its full factor cost");
            }
            check(paused.producedTotal() == before + 1 && paused.progressUnits() == 0,
                "Exact 1/1000 production failed to accumulate into one output without rounding loss");
            verifyIndexedNetwork(level, cheap);
            GrowthStackGameTest.verify(game);
            ConvertTable.LOGGER.info("GROWTH_ALLOCATION_TEST_PASS: exact 1/1000 production, bulk fairness, rotating saved cursor, pause/detach, unbounded loaded component cache, multi-controller budget and long menu data");
        } finally {
            for (BlockPos pos : placed) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            ((ServerLevelData) level.getLevelData()).setGameTime(originalTime);
        }
    }

    private static void verifyBulkAllocation() {
        Random random = new Random(4128);
        for (int sample = 0; sample < 2_000; sample++) {
            int count = 1 + random.nextInt(15), cursor = random.nextInt(count), budget = random.nextInt(250);
            long[] demand = new long[count], expected = new long[count];
            for (int i = 0; i < count; i++) demand[i] = random.nextInt(30);
            int next = cursor, remaining = budget;
            while (remaining > 0) {
                int found = -1;
                for (int offset = 0; offset < count; offset++) {
                    int i = (next + offset) % count;
                    if (expected[i] < demand[i]) { found = i; break; }
                }
                if (found < 0) break;
                expected[found]++;
                remaining--;
                next = (found + 1) % count;
            }
            var actual = GrowthAllocation.divide(budget, demand, cursor);
            check(Arrays.equals(actual.amounts(), expected) && actual.nextIndex() == next && actual.spent() == budget - remaining,
                "Water fill diverged from rotating unit fairness sample=" + sample);
        }
        var huge = GrowthAllocation.divide(Long.MAX_VALUE, new long[]{Long.MAX_VALUE, Long.MAX_VALUE, 1}, 1);
        check(huge.spent() == Long.MAX_VALUE && huge.amounts()[2] == 1
            && Math.abs(huge.amounts()[0] - huge.amounts()[1]) <= 1,
            "Long-scale budget overflowed or could not be shared in bulk");
        var capped = GrowthAllocation.divide(1_000_000_000_000L, new long[]{3, 5, Long.MAX_VALUE}, 0);
        check(Arrays.equals(capped.amounts(), new long[]{3, 5, 999_999_999_992L}),
            "Saturated recipients failed to redistribute their large-budget remainder");
    }

    private static void verifyMenuNumbers() {
        check(GrowthUnits.restoreFraction(19, 1) == 152000
            && GrowthUnits.restoreFraction(1, 64) == 125
            && GrowthUnits.restoreFraction(1279, 64) == 159875
            && GrowthUnits.restoreFraction(19999, 1000) == 159992
            && GrowthUnits.restoreFraction(159999, 8000) == 159999,
            "Released fractional progress was not migrated exactly");
        check(GrowthDrain.share(Long.MAX_VALUE - 1, Long.MAX_VALUE - 2, Long.MAX_VALUE) == Long.MAX_VALUE - 3,
            "Large proportional drain overflowed");
        SimpleContainerData values = new SimpleContainerData(4);
        long[] cases = {0, 32768, 65535, 65536, Integer.MAX_VALUE, 4_000_000_001L, Long.MAX_VALUE};
        for (long number : cases) {
            GrowthData.set(values, 0, number);
            // Vanilla serializes each property as a signed short.
            for (int i = 0; i < 4; i++) values.set(i, (short) values.get(i));
            check(GrowthData.get(values, 0) == number, "Menu clipped a long growth number: " + number);
        }
        check(GrowthUnits.rate(1).equals("0.001") && GrowthUnits.factors(10).equals("0.0000625"),
            "Microfactor display rounded away real fractional growth");
    }

    private static void verifyIndexedNetwork(ServerLevel level, GrowthRecipes.Recipe recipe) {
        GrowthNetworkGameTest.verify(level, recipe);
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
