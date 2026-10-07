package com.example.converttable;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;

/** Native advancement loading, real timed work, output blockage and explicit operator credit. */
final class TableAdvancementsGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static boolean done(ServerPlayer player, String name) {
        var advancement = player.level().getServer().getAdvancements().get(ConvertTable.id(name));
        check(advancement != null, "Native advancement failed to load: " + name);
        return player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    static void verify(MinecraftServer server) {
        var level = server.overworld();
        var player = server.getPlayerList().getPlayers().getFirst();
        for (String name : new String[]{"root", "amethyst", "black_gold", "end", "sculk", "crystal"}) {
            var advancement = server.getAdvancements().get(ConvertTable.id(name));
            check(advancement != null && advancement.value().display().isPresent(), "Missing native advancement " + name);
            if (name.equals("crystal")) {
                check(advancement.value().display().get().hidden(), "Crystal discovery must be hidden");
                check(ConvertTable.id("advancement/crystal").equals(advancement.value().display().get().icon().create().get(DataComponents.ITEM_MODEL)),
                    "Crystal advancement did not use its original illustrated icon");
            }
            if (!name.equals("root") && !name.equals("amethyst")) player.getAdvancements().revoke(advancement, "complete");
        }
        var machines = new ConversionTableBlock[]{ConversionTables.BLACK_GOLD, ConversionTables.END, ConversionTables.SCULK};
        String[] names = {"black_gold", "end", "sculk"};
        for (int i = 0; i < machines.length; i++) {
            var pos = new BlockPos(0, 170 + i * 2, 0);
            var table = ConversionExecutionGameTest.place(level, pos, machines[i]);
            try {
                new ConversionTableMenu(98, player.getInventory(), table);
                check(table.advancementOperator() == null, "Viewing a station took operator credit");
                table.target = BuiltInRegistries.ITEM.getKey(Items.BIRCH_PLANKS);
                check(!table.convert(false, player) && !done(player, names[i]), "Missing input earned an advancement");
                table.cancelProcessing();
                table.setItem(0, new ItemStack(Items.OAK_PLANKS, 16));
                if (i < 2) table.setItem(1, new ItemStack(RecipeConfig.fuelItem(i), 64));
                else table.deaths = 4096;
                table.setItem(2, new ItemStack(Items.STONE, 64));
                check(table.convert(false, player), "Blocked output did not begin valid timed work");
                for (int tick = 0; tick < table.totalTicks() - 1; tick++)
                    ConversionTableBlockEntity.tick(level, pos, table.getBlockState(), table);
                check(!done(player, names[i]), "Advancement fired before the synthesis timer completed");
                ConversionTableBlockEntity.tick(level, pos, table.getBlockState(), table);
                check(done(player, names[i]) && table.status == 6 && table.getItem(0).getCount() == 16
                    && table.getItem(2).is(Items.STONE), "Completed synthesis waited for output delivery or consumed blocked input");
                var saved = table.saveWithFullMetadata(server.registryAccess());
                var copy = (ConversionTableBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                    pos, table.getBlockState(), saved, server.registryAccess());
                check(copy.advancementOperator().equals(player.getUUID()), "Explicit operator was not persisted");
                table.setItem(2, ItemStack.EMPTY);
                ConversionTableBlockEntity.tick(level, pos, table.getBlockState(), table);
                check(!table.getItem(2).isEmpty() && table.getItem(0).isEmpty(), "Completed work failed to deliver after clearing output");
            } finally { level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); }
        }

        var placed = new ArrayList<BlockPos>();
        var origin = new BlockPos(0, 180, 0);
        long growthStart = level.getGameTime();
        try {
            placed.add(origin);
            level.setBlockAndUpdate(origin, GrowthBlocks.CRYSTAL.defaultBlockState());
            for (int x = 1; x <= 8; x++) for (int z = 0; z < 8; z++) {
                var mother = origin.offset(x, 0, z);
                placed.add(mother); placed.add(mother.above());
                level.setBlockAndUpdate(mother, Blocks.BUDDING_AMETHYST.defaultBlockState());
                level.setBlockAndUpdate(mother.above(), Blocks.SMALL_AMETHYST_BUD.defaultBlockState()
                    .setValue(AmethystClusterBlock.FACING, Direction.UP));
            }
            placed.add(origin.north()); placed.add(origin.north(2)); placed.add(origin.above());
            level.setBlockAndUpdate(origin.north(), Blocks.AMETHYST_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(origin.north(2), Blocks.CALCITE.defaultBlockState());
            level.setBlockAndUpdate(origin.above(), GrowthBlocks.CATALYST.defaultBlockState());
            var source = (CrystalTableBlockEntity) level.getBlockEntity(origin);
            var pedestal = (CatalystPedestalBlockEntity) level.getBlockEntity(origin.above());
            pedestal.setItem(0, new ItemStack(Items.OAK_SAPLING));
            pedestal.setItem(CatalystPedestalBlockEntity.SOURCE_SLOT, new ItemStack(Items.OAK_LOG));
            for (int index = 0; index < pedestal.recipes().size(); index++)
                if (pedestal.recipes().get(index).output() == Items.OAK_LOG) pedestal.selectRecipe(index);
            pedestal.toggleRunning(player);
            check(source.snapshot().available() == 128 && pedestal.cost() == 2,
                "Growth fixture must provide 128 microfactors per second from physical calcite-boosted buds");
            for (int cycle = 0; cycle < 15; cycle++) {
                server.getWorldData().overworldData().setGameTime(growthStart + cycle * 20);
                source.produce();
                pedestal.advanceGrowth(level.getGameTime() + 20);
                check(!done(player, "crystal") && pedestal.producedTotal() == 0, "Partial factors earned an advancement");
            }
            server.getWorldData().overworldData().setGameTime(growthStart + 300);
            source.produce();
            pedestal.advanceGrowth(level.getGameTime() + 12);
            check(!done(player, "crystal") && pedestal.producedTotal() == 0, "Growth achievement fired before an item was synthesized");
            pedestal.advanceGrowth(level.getGameTime() + 13);
            check(done(player, "crystal") && pedestal.producedTotal() == 1 && pedestal.getItem(1).is(Items.OAK_LOG),
                "Actual growth synthesis failed to earn credit before container extraction");
        } finally {
            for (var pos : placed) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            server.getWorldData().overworldData().setGameTime(growthStart);
        }
        ConvertTable.LOGGER.info("TABLE_ADVANCEMENT_TEST_PASS: six native entries, hidden discovery and illustrated icon; three validated timed syntheses before blocked delivery; real physical crystal production; explicit persistent operators");
    }
}
