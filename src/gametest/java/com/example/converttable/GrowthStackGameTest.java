package com.example.converttable;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ServerLevelData;

/** Physical stacking, duplicate contacts and a reproducible 100k factors/hour layout. */
final class GrowthStackGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void place(ServerLevel level, Set<BlockPos> placed, BlockPos pos, BlockState state) {
        placed.add(pos.immutable()); level.setBlockAndUpdate(pos, state);
    }
    private static void bud(ServerLevel level, Set<BlockPos> placed, BlockPos mother, Direction face) {
        place(level, placed, mother.relative(face), Blocks.SMALL_AMETHYST_BUD.defaultBlockState()
            .setValue(AmethystClusterBlock.FACING, face));
    }
    static void verify(MinecraftServer server) {
        var level = server.overworld();
        long time = level.getGameTime();
        var placed = new LinkedHashSet<BlockPos>();
        try {
            for (int x = -3; x <= 3; x++) for (int z = -1; z <= 0; z++) level.getChunk(x, z);
            var sourcePos = new BlockPos(0, 205, 0);
            var conductor = sourcePos.east(); var mother = conductor.east();
            place(level, placed, sourcePos, GrowthBlocks.CRYSTAL.defaultBlockState());
            place(level, placed, conductor, Blocks.AMETHYST_BLOCK.defaultBlockState());
            place(level, placed, mother, Blocks.BUDDING_AMETHYST.defaultBlockState());
            bud(level, placed, mother, Direction.EAST);
            for (var face : new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH})
                place(level, placed, conductor.relative(face), Blocks.CALCITE.defaultBlockState());
            var source = (CrystalTableBlockEntity) level.getBlockEntity(sourcePos);
            var network = source.snapshot();
            check(network.available() == 5 && network.potential() == 16, "Four global calcites must produce 5/1000/s");
            place(level, placed, conductor.north(), Blocks.AMETHYST_BLOCK.defaultBlockState());
            place(level, placed, mother.north(), Blocks.CALCITE.defaultBlockState());
            for (var face : new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH})
                place(level, placed, conductor.north().relative(face), Blocks.SMOOTH_BASALT.defaultBlockState());
            network = source.snapshot();
            long rebuilds = GrowthNetwork.rebuildCount(level);
            check(network.available() == 5 && network.potential() == 19,
                "Shared global/local contact counted twice or three basalts did not stack");
            place(level, placed, mother.above(), Blocks.CALCITE.defaultBlockState());
            check(source.snapshot() == network && network.available() == 6 && network.potential() == 19,
                "Local calcite did not stack with four distinct global calcites");
            place(level, placed, conductor.above(), Blocks.AIR.defaultBlockState());
            check(source.snapshot() == network && network.available() == 5,
                "Removing one of several global calcites did not decrement the budget");
            place(level, placed, conductor.north().above(), Blocks.AIR.defaultBlockState());
            check(network.potential() == 18 && network.available() == 5
                && GrowthNetwork.rebuildCount(level) == rebuilds, "Mineral changes rebuilt topology or changed the wrong quantity");
            GrowthDrain.publish(level, network, 5);
            check(Math.abs(network.growthMean() - 650000) <= 1 && network.growthMin() == network.growthMax(),
                "Original extraction slowdown or cached speed is incorrect");
            place(level, placed, mother.east(), Blocks.AMETHYST_CLUSTER.defaultBlockState()
                .setValue(AmethystClusterBlock.FACING, Direction.EAST));
            check(network.available() == 0 && network.potential() == 1, "Minerals boosted a mature cluster");
            for (var pos : placed) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            placed.clear();

            var origin = new BlockPos(0, 225, 0);
            place(level, placed, origin, GrowthBlocks.CRYSTAL.defaultBlockState());
            for (int x = 1; x <= 41; x++) {
                var m = origin.east(x);
                place(level, placed, m, Blocks.BUDDING_AMETHYST.defaultBlockState());
                for (var face : new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH}) bud(level, placed, m, face);
            }
            bud(level, placed, origin.east(41), Direction.EAST);
            for (int x = 1; x <= 42; x++) {
                var c = origin.west(x);
                place(level, placed, c, Blocks.AMETHYST_BLOCK.defaultBlockState());
                for (var face : new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH})
                    place(level, placed, c.relative(face), Blocks.CALCITE.defaultBlockState());
            }
            var pedestalPos = origin.west(43);
            place(level, placed, pedestalPos, GrowthBlocks.CATALYST.defaultBlockState());
            source = (CrystalTableBlockEntity) level.getBlockEntity(origin);
            network = source.snapshot();
            check(placed.size() == 418 && network.mothers() == 41 && network.conductors() == 42 && network.count(1) == 165
                && network.available() == 27885 && network.potential() == 2640,
                "Physical 418-block layout disagrees with the 100386 factors/hour calculation");
            var pedestal = (CatalystPedestalBlockEntity) level.getBlockEntity(pedestalPos);
            pedestal.setItem(0, new ItemStack(Items.OAK_SAPLING));
            pedestal.setItem(CatalystPedestalBlockEntity.SOURCE_SLOT, new ItemStack(Items.OAK_LOG));
            for (int index = 0; index < pedestal.recipes().size(); index++)
                if (pedestal.recipes().get(index).output() == Items.OAK_LOG) pedestal.selectRecipe(index);
            pedestal.toggleRunning();
            source.produce();
            check(source.lastSpent() == 27885 && Math.abs(network.growthMean() - 150000) <= 1,
                "Real production did not allocate the full 100k budget or retain the 15% growth floor");
            pedestal.advanceGrowth(level.getGameTime() + 20);
            check(pedestal.producedTotal() == 13 && pedestal.progressUnits() == 301600,
                "High-rate production lost the 1.885-factor remainder");
            var saved = pedestal.saveWithFullMetadata(server.registryAccess());
            var copy = (CatalystPedestalBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                pedestalPos, pedestal.getBlockState(), saved, server.registryAccess());
            check(copy.progressUnits() == 301600 && !copy.processing(), "High-rate progress did not survive save/reload");
            ((ServerLevelData) level.getLevelData()).setGameTime(time + 20);
            place(level, placed, origin.east(42), Blocks.AMETHYST_CLUSTER.defaultBlockState()
                .setValue(AmethystClusterBlock.FACING, Direction.EAST));
            source.produce();
            check(network.count(1) == 165 && network.count(4) == 0 && source.lastSpent() == 27885,
                "Mature reseeding failed to restore the budget without manual replacement");
            pedestal.advanceGrowth(level.getGameTime() + 20);
            check(pedestal.producedTotal() == 27 && pedestal.progressUnits() == 283200,
                "Continuous high-rate synthesis disagrees with exact factors");
            ConvertTable.LOGGER.info("GROWTH_STACK_TEST_PASS: distinct global/local stacking, duplicate contacts, incremental removal, mature exclusion, speed cache; 418 physical blocks, 165 buds, 168 calcites, 27885 microfactors/s = 100386 factors/hour, actual production and automatic reseeding");
        } finally {
            for (var pos : placed) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            ((ServerLevelData) level.getLevelData()).setGameTime(time);
        }
    }
}
