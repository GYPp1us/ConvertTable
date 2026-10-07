package com.example.converttable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;

/** Component growth beyond former limits and physical invalidation of the shared budget. */
final class GrowthNetworkGameTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void verify(ServerLevel level, GrowthRecipes.Recipe recipe) {
        List<BlockPos> placed = new ArrayList<>(), mothers = new ArrayList<>();
        BlockPos first = new BlockPos(4, 140, 4), second = new BlockPos(19, 140, 19);
        BlockPos tail = new BlockPos(-20, 140, 4), bridge = new BlockPos(3, 140, 4);
        BlockPos pedestalPos = first.above(), calcite = new BlockPos(6, 140, 3), basalt = new BlockPos(5, 140, 3);
        long originalTime = level.getGameTime();
        try {
            // Test setup loads the explicitly constructed region. The network may never load more.
            for (int x = -2; x <= 1; x++) for (int z = 0; z <= 1; z++) level.getChunk(x, z);
            for (int x = 4; x < 20; x++) for (int z = 4; z < 20; z++) {
                BlockPos pos = new BlockPos(x, 140, z);
                placed.add(pos);
                placed.add(pos.above());
                level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
                boolean mother = x % 3 == 0 && z % 3 == 0;
                level.setBlockAndUpdate(pos, (mother ? Blocks.BUDDING_AMETHYST : Blocks.AMETHYST_BLOCK).defaultBlockState());
                if (mother) {
                    mothers.add(pos);
                    level.setBlockAndUpdate(pos.above(), Blocks.SMALL_AMETHYST_BUD.defaultBlockState()
                        .setValue(AmethystClusterBlock.FACING, Direction.UP));
                }
            }
            for (int x = -20; x <= 3; x++) {
                BlockPos pos = new BlockPos(x, 140, 4);
                placed.add(pos);
                level.setBlockAndUpdate(pos, Blocks.AMETHYST_BLOCK.defaultBlockState());
            }
            level.setBlockAndUpdate(first, GrowthBlocks.CRYSTAL.defaultBlockState());
            level.setBlockAndUpdate(second, GrowthBlocks.CRYSTAL.defaultBlockState());
            level.setBlockAndUpdate(tail, GrowthBlocks.CRYSTAL.defaultBlockState());
            placed.add(calcite);
            placed.add(basalt);
            level.setBlockAndUpdate(calcite, Blocks.CALCITE.defaultBlockState());
            level.setBlockAndUpdate(basalt, Blocks.SMOOTH_BASALT.defaultBlockState());
            level.setBlockAndUpdate(pedestalPos, GrowthBlocks.CATALYST.defaultBlockState());
            var pedestal = (CatalystPedestalBlockEntity) level.getBlockEntity(pedestalPos);
            pedestal.setItem(0, new ItemStack(recipe.catalyst()));
            pedestal.setItem(10, new ItemStack(recipe.output()));
            check(pedestal.selectRecipe(pedestal.recipes().indexOf(recipe)), "Could not select a large-network target");
            if (!pedestal.running()) pedestal.toggleRunning();
            var source = (CrystalTableBlockEntity) level.getBlockEntity(first);
            var other = (CrystalTableBlockEntity) level.getBlockEntity(second);
            var coordinator = (CrystalTableBlockEntity) level.getBlockEntity(tail);
            int loaded = level.getChunkSource().getLoadedChunksCount();
            var network = source.snapshot();
            check(network.nodes().size() == 280 && network.mothers() == mothers.size() && network.mothers() > 8
                && network.usable() && network.sources().size() == 3 && network.coordinator().equals(tail),
                "Former node/range/mother/single-controller limit remained in the loaded component");
            check(network.available() == mothers.size() * 2L && network.potential() == mothers.size() * 17L,
                "Linear mineral contacts changed the per-mother amplification ratios");
            long rebuilt = GrowthNetwork.rebuildCount(level);
            for (int query = 0; query < 2_000; query++) {
                check(source.snapshot() == network && other.snapshot() == network && coordinator.snapshot() == network,
                    "Stable controllers rebuilt or copied a shared snapshot");
                check(GrowthNetwork.findSource(level, pedestalPos) == coordinator,
                    "Pairing disagreed with deterministic shared coordination");
            }
            check(GrowthNetwork.rebuildCount(level) == rebuilt && level.getChunkSource().getLoadedChunksCount() == loaded,
                "Stable pairing rescanned a component or proactively loaded a chunk");
            check(GrowthNetwork.findSource(level, new BlockPos(2_000_000, 140, 2_000_000)) == null
                && level.getChunkSource().getLoadedChunksCount() == loaded, "An unloaded attachment caused chunk loading");

            BlockPos unrelated = first.above(5);
            placed.add(unrelated);
            level.setBlockAndUpdate(unrelated, Blocks.CALCITE.defaultBlockState());
            check(source.snapshot() == network, "An unrelated mineral invalidated a distant component");
            source.produce();
            other.produce();
            coordinator.produce();
            long assigned = pedestal.lastSpent();
            check(assigned == network.available() && source.lastSpent() == assigned && other.lastSpent() == assigned,
                "Multiple controllers multiplied the shared mother budget or showed conflicting budgets");
            pedestal.advanceGrowth(level.getGameTime() + 10);
            long earned = pedestal.progressUnits();

            level.setBlockAndUpdate(bridge, Blocks.AIR.defaultBlockState());
            check(source.snapshot() != network && GrowthNetwork.findSource(level, pedestalPos) == source,
                "Removing the only bridge left an old component/source index");
            pedestal.advanceGrowth(level.getGameTime() + 20);
            check(pedestal.progressUnits() == earned && !pedestal.processing(),
                "A split component continued spending the detached coordinator's budget");
            source.produce();
            check(pedestal.lastSpent() == 0, "Replacement coordinator reissued already exported buds in the same interval");
            ((ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + 20);
            source.produce();
            check(pedestal.lastSpent() == network.available(), "A split network failed to resume in the next interval");

            var split = source.snapshot();
            GrowthNetwork.invalidateChunk(level, new ChunkPos(0, 0));
            check(source.snapshot() != split && coordinator.snapshot().nodes().size() == 23,
                "Chunk invalidation failed to evict intersecting components or rejoined a split tail");
            var refreshed = source.snapshot();
            GrowthNetwork.invalidateChunk(level, new ChunkPos(12000, 12000));
            check(source.snapshot() == refreshed, "An unrelated chunk event invalidated a stable component");
            level.setBlockAndUpdate(bridge, Blocks.AMETHYST_BLOCK.defaultBlockState());
            check(source.snapshot().nodes().size() == 280 && GrowthNetwork.findSource(level, pedestalPos) == coordinator,
                "Reconnecting a bridge failed to merge both indexed components");
            long previousPotential=network.potential(),previousRebuilds=GrowthNetwork.rebuildCount(level);
            var beforeBudChange=source.snapshot();
            level.setBlockAndUpdate(mothers.getFirst().above(), Blocks.MEDIUM_AMETHYST_BUD.defaultBlockState()
                .setValue(AmethystClusterBlock.FACING, Direction.UP));
            check(source.snapshot().count(2) == 1 && source.snapshot().potential() == previousPotential - 8
                && source.snapshot()==beforeBudChange && GrowthNetwork.rebuildCount(level)==previousRebuilds,
                "Vanilla bud-stage changes did not refresh physical quantities");
            // Exercise actual block changes: contents fall with maturation, while extraction
            // stays independent until the mature cluster stops supplying factors entirely.
            var stageBlocks = new net.minecraft.world.level.block.Block[]{Blocks.SMALL_AMETHYST_BUD,
                Blocks.MEDIUM_AMETHYST_BUD, Blocks.LARGE_AMETHYST_BUD, Blocks.AMETHYST_CLUSTER};
            long[] expectedContents = {17, 9, 5, 1};
            for (int stage = 0; stage < stageBlocks.length; stage++) {
                level.setBlockAndUpdate(mothers.getFirst().above(), stageBlocks[stage].defaultBlockState()
                    .setValue(AmethystClusterBlock.FACING, Direction.UP));
                var changed = source.snapshot();
                check(changed == beforeBudChange && changed.potential() == previousPotential - 17 + expectedContents[stage]
                    && changed.available() == (mothers.size() - 1) * 2L + (stage == 3 ? 0 : 2),
                    "Physical crystal stage disagrees with contents/extraction: " + (stage + 1));
            }
            level.setBlockAndUpdate(basalt, Blocks.AIR.defaultBlockState());
            check(source.snapshot().potential() == (mothers.size() - 1) * 16L + 1
                && source.snapshot().available() == (mothers.size() - 1) * 2L,
                "Removing basalt altered extraction or the mature cluster's natural contents");
            level.setBlockAndUpdate(calcite, Blocks.AIR.defaultBlockState());
            check(source.snapshot().potential() == (mothers.size() - 1) * 16L + 1
                && source.snapshot().available() == mothers.size() - 1,
                "Removing calcite altered contents or kept the boosted extraction limit");
            ConvertTable.LOGGER.info("GROWTH_NETWORK_TEST_PASS: 280 nodes, {} mothers, 3 sources, cached pairing, selective block/chunk invalidation, split/merge and interval budget ledger", mothers.size());
        } finally {
            for (BlockPos pos : placed) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            ((ServerLevelData) level.getLevelData()).setGameTime(originalTime);
        }
    }
}
