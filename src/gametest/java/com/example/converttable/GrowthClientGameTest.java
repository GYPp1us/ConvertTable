package com.example.converttable;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

/** Verifies the live shared growth budget, reusable catalysts, recipe packets and exported UI state. */
final class GrowthClientGameTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static int target(CatalystPedestalBlockEntity pedestal, Item output) {
        var choices = pedestal.recipes();
        for (int i = 0; i < choices.size(); i++) if (choices.get(i).output() == output) return i;
        throw new AssertionError("Missing catalyst target: " + output);
    }
    private static void bud(net.minecraft.world.level.Level level, BlockPos mother, Direction face,
                            net.minecraft.world.level.block.Block block) {
        level.setBlockAndUpdate(mother.relative(face), block.defaultBlockState()
            .setValue(AmethystClusterBlock.FACING, face));
    }

    static void run(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            mc.options.guiScale().set(3);
            mc.options.languageCode = "zh_cn";
            mc.getLanguageManager().setSelected("zh_cn");
        });
        var reload = context.computeOnClient(mc -> mc.reloadResourcePacks());
        context.waitFor(mc -> reload.isDone(), 600);
        reload.join();
        context.getInput().resizeWindow(1280, 720);
        context.waitTicks(3);
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            server.runCommand("time set noon");
            server.runCommand("fill -10 98 -10 10 99 10 polished_deepslate");
            server.runCommand("tp @a 4.3 101 5.6 150 15");
            context.runOnClient(mc -> {
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                mc.options.fov().set(50);
            });
            var crystalPos = new BlockPos(0, 100, 0);
            var conductor = crystalPos.north();
            var pedestalPos = conductor.east();
            var secondPos = conductor.west();
            var mother = crystalPos.north(2);
            server.runOnServer(game -> {
                var level = game.overworld();
                level.setBlockAndUpdate(crystalPos, GrowthBlocks.CRYSTAL.defaultBlockState());
                level.setBlockAndUpdate(conductor, Blocks.AMETHYST_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(mother, Blocks.BUDDING_AMETHYST.defaultBlockState());
                level.setBlockAndUpdate(pedestalPos, GrowthBlocks.CATALYST.defaultBlockState());
                bud(level, mother, Direction.NORTH, Blocks.SMALL_AMETHYST_BUD);
                var crystal = (CrystalTableBlockEntity) level.getBlockEntity(crystalPos);
                check(crystal.snapshot().available() == 1, "Normal bud should export 1/s, not its internal 24");
                level.setBlockAndUpdate(mother.east(), Blocks.CALCITE.defaultBlockState());
                level.setBlockAndUpdate(conductor.below(), Blocks.SMOOTH_BASALT.defaultBlockState());
                crystal.invalidateNetwork();
                var network = crystal.snapshot();
                check(network.available() == 2 && network.potential() == 25,
                    "Calcite must export 2/s; basalt must add one internal point");
                var pedestal = (CatalystPedestalBlockEntity) level.getBlockEntity(pedestalPos);
                check(pedestal.crystal() == crystal, "Pedestal did not link through an amethyst conductor");
                pedestal.setItem(0, new ItemStack(Items.OAK_SAPLING));
                check(pedestal.recipes().size() > 1 && pedestal.selectedRecipe() == null,
                    "A multi-output catalyst needs explicit target selection");
                check(!pedestal.selectRecipe(99999), "Invalid target index was accepted");
                check(pedestal.selectRecipe(target(pedestal, Items.OAK_LOG)), "Oak target was rejected");
                var state = level.getBlockState(pedestalPos);
                check(state.getShape(level, pedestalPos).max(Direction.Axis.Y) > 1.5
                    && state.getCollisionShape(level, pedestalPos).max(Direction.Axis.Y) == .5,
                    "Full block projection should be targetable above a low base");
                pedestal.toggleRunning();
                level.setBlockAndUpdate(pedestalPos.east(), Blocks.BARREL.defaultBlockState());
                for (String name : new String[]{"crystal_table", "catalyst_pedestal"})
                    check(game.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,
                        ConvertTable.id(name))).isPresent(), "Missing craft recipe: " + name);
            });
            context.waitTicks(45);
            server.runOnServer(game -> {
                var level = game.overworld();
                var pedestal = (CatalystPedestalBlockEntity) level.getBlockEntity(pedestalPos);
                check(pedestal.producedTotal() >= 2 && pedestal.getItem(0).is(Items.OAK_SAPLING)
                    && pedestal.getItem(0).getCount() == 1, "Production consumed the catalyst or produced no logs");
                check(((BarrelBlockEntity) level.getBlockEntity(pedestalPos.east())).getItem(0).is(Items.OAK_LOG),
                    "Adjacent output container did not receive logs");
                var links = pedestal.outputLinks();
                check(links.containers() == 1 && links.freeSpace() > 0
                    && (links.directionMask() & 1 << Direction.EAST.ordinal()) != 0 && links.blockedMask() == 0,
                    "Container connection snapshot disagrees with transfer");
                var saved = pedestal.saveWithFullMetadata(game.registryAccess());
                var restored = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                    pedestalPos, level.getBlockState(pedestalPos), saved, game.registryAccess());
                check(restored instanceof CatalystPedestalBlockEntity copy && copy.running()
                    && copy.getItem(0).is(Items.OAK_SAPLING) && copy.selectedRecipe().output() == Items.OAK_LOG
                    && copy.producedTotal() == pedestal.producedTotal(), "Catalyst/target did not survive save/load");
                check(GrowthDrain.growthChance(level, mother, Direction.NORTH) < 1F,
                    "Extraction did not reduce bud growth chance");
                level.setBlockAndUpdate(secondPos, GrowthBlocks.CATALYST.defaultBlockState());
                var second = (CatalystPedestalBlockEntity) level.getBlockEntity(secondPos);
                second.setItem(0, new ItemStack(Items.BIRCH_SAPLING));
                second.selectRecipe(target(second, Items.BIRCH_LOG));
                second.toggleRunning();
                ((CrystalTableBlockEntity) level.getBlockEntity(crystalPos)).invalidateNetwork();
            });
            context.waitTicks(42);
            server.runOnServer(game -> {
                var level = game.overworld();
                var first = (CatalystPedestalBlockEntity) level.getBlockEntity(pedestalPos);
                var second = (CatalystPedestalBlockEntity) level.getBlockEntity(secondPos);
                var crystal = (CrystalTableBlockEntity) level.getBlockEntity(crystalPos);
                check(crystal.pedestalCount() == 2 && second.producedTotal() >= 1, "Multiple pedestals did not run");
                check(first.lastSpent() == 1 && second.lastSpent() == 1 && crystal.lastSpent() == 2,
                    "Pedestals duplicated the shared two-point budget");
                second.toggleRunning();
                bud(level, mother, Direction.UP, Blocks.MEDIUM_AMETHYST_BUD);
                bud(level, mother, Direction.WEST, Blocks.LARGE_AMETHYST_BUD);
                crystal.invalidateNetwork();
            });
            context.waitTicks(25);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot(TestScreenshotOptions.of("growth-crystal-and-pedestal"));
            server.runOnServer(game -> game.getPlayerList().getPlayers().getFirst().openMenu(
                (CrystalTableBlockEntity) game.overworld().getBlockEntity(crystalPos)));
            context.waitFor(mc -> mc.gui.screen() instanceof CrystalTableScreen
                && ((CrystalTableMenu) mc.player.containerMenu).rate() == 6, 100);
            context.getInput().setCursorPos(0, 0);
            context.waitTicks(3);
            context.takeScreenshot(TestScreenshotOptions.of("growth-crystal-ui"));
            context.runOnClient(mc -> mc.gui.screen().onClose());
            context.waitTicks(4);
            server.runOnServer(game -> game.getPlayerList().getPlayers().getFirst().openMenu(
                (CatalystPedestalBlockEntity) game.overworld().getBlockEntity(pedestalPos)));
            context.waitFor(mc -> mc.gui.screen() instanceof CatalystPedestalScreen
                && ((CatalystPedestalMenu) mc.player.containerMenu).running()
                && ((CatalystPedestalMenu) mc.player.containerMenu).containerCount() == 1, 100);
            context.waitTicks(3);
            context.takeScreenshot(TestScreenshotOptions.of("growth-catalyst-ui"));
            context.runOnClient(mc -> {
                var menu = (CatalystPedestalMenu) mc.player.containerMenu;
                int index = -1;
                for (int i = 0; i < menu.recipes().size(); i++)
                    if (menu.recipes().get(i).output() == Items.OAK_PLANKS) index = i;
                check(index >= 0, "Sapling should offer multiple target recipes");
                mc.gameMode.handleInventoryButtonClick(menu.containerId, 1000 + index);
            });
            context.waitFor(mc -> ((CatalystPedestalMenu) mc.player.containerMenu).selectedRecipe() != null
                && ((CatalystPedestalMenu) mc.player.containerMenu).selectedRecipe().output() == Items.OAK_PLANKS
                && !((CatalystPedestalMenu) mc.player.containerMenu).running(), 100);
            server.runOnServer(game -> {
                var level = game.overworld();
                var first = (CatalystPedestalBlockEntity) level.getBlockEntity(pedestalPos);
                check(first.credit() == 0, "Switching target retained credit");
                var barrel = (BarrelBlockEntity) level.getBlockEntity(pedestalPos.east());
                for (int i = 0; i < barrel.getContainerSize(); i++) barrel.setItem(i, new ItemStack(Items.DIRT, 64));
            });
            context.waitFor(mc -> (((CatalystPedestalMenu) mc.player.containerMenu).blockedMask()
                & 1 << Direction.EAST.ordinal()) != 0, 100);
            server.runOnServer(game -> {
                var level = game.overworld();
                var first = (CatalystPedestalBlockEntity) level.getBlockEntity(pedestalPos);
                var player = game.getPlayerList().getPlayers().getFirst();
                first.toggleRunning();
                ((CatalystPedestalMenu) player.containerMenu).quickMoveStack(player, 0);
                check(first.getItem(0).isEmpty() && !first.running() && first.credit() == 0
                    && first.selectedRecipe() == null, "Shift-removing the catalyst did not pause and clear selection");
                first.setItem(0, new ItemStack(Items.OAK_SAPLING));
                first.selectRecipe(target(first, Items.OAK_LOG));
                first.toggleRunning();
                bud(level, mother, Direction.UP, Blocks.AMETHYST_CLUSTER);
                ((CrystalTableBlockEntity) level.getBlockEntity(crystalPos)).invalidateNetwork();
            });
            context.waitTicks(25);
            server.runOnServer(game -> {
                var level = game.overworld();
                check(level.getBlockState(mother.above()).is(Blocks.SMALL_AMETHYST_BUD),
                    "Active shared source did not reseed the mature cluster");
                ((CatalystPedestalBlockEntity) level.getBlockEntity(pedestalPos)).toggleRunning();
            });
            context.runOnClient(mc -> mc.gui.screen().onClose());
            context.waitTicks(25);
            server.runOnServer(game -> {
                var level = game.overworld();
                float chance = GrowthDrain.growthChance(level, mother, Direction.NORTH);
                check(chance > .80F && chance < 1F, "Calcite should still slow idle growth: " + chance);
                check(GrowthPlacementHint.canPlaceHint(level, conductor, Direction.SOUTH,
                    new ItemStack(Items.CALCITE)) == false, "Occupied placement advertised as usable");
                check(GrowthPlacementHint.canPlaceHint(level, conductor, Direction.WEST,
                    new ItemStack(GrowthBlocks.CATALYST.asItem())) == false, "Occupied pedestal hint");
                check(GrowthPlacementHint.canPlaceHint(level, mother, Direction.SOUTH,
                    new ItemStack(Items.CALCITE)) == false, "Occupied conductor hint");
                check(GrowthPlacementHint.canPlaceHint(level, mother, Direction.DOWN,
                    new ItemStack(Items.SMOOTH_BASALT)) == false, "Ground cannot be replaced");
                check(GrowthPlacementHint.canPlaceHint(level, conductor, Direction.EAST,
                    new ItemStack(Items.CALCITE)) == false, "Existing pedestal cannot be replaced");
                level.setBlockAndUpdate(secondPos, Blocks.AIR.defaultBlockState());
                ((CrystalTableBlockEntity) level.getBlockEntity(crystalPos)).invalidateNetwork();
                check(GrowthPlacementHint.canPlaceHint(level, conductor, Direction.UP,
                    new ItemStack(GrowthBlocks.CATALYST.asItem())), "Valid conductor did not offer the placement hint");
            });
            server.runCommand("kill @e[type=minecraft:item]");
            server.runCommand("tp @a 0.5 103 0.8 180 70");
            server.runCommand("item replace entity @a weapon.mainhand with convert_table:catalyst_pedestal");
            server.runOnServer(game -> {
                var player = game.getPlayerList().getPlayers().getFirst();
                player.getAbilities().flying = true;
                player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                player.onUpdateAbilities();
            });
            context.runOnClient(mc -> {
                if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                mc.gui.hud.getChat().clearMessages(false);
            });
            context.waitFor(mc -> mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                && hit.getBlockPos().equals(conductor) && hit.getDirection() == Direction.UP
                && GrowthPlacementHint.canPlaceHint(mc.level, conductor, Direction.UP, mc.player.getMainHandItem()), 100);
            context.waitTicks(6);
            context.takeScreenshot(TestScreenshotOptions.of("growth-placement-hint"));
            for (Item material : new Item[]{Items.CALCITE, Items.SMOOTH_BASALT}) {
                String name = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(material).toString();
                server.runCommand("item replace entity @a weapon.mainhand with " + name);
                context.waitFor(mc -> mc.player.getMainHandItem().is(material)
                    && GrowthPlacementHint.canPlaceHint(mc.level, conductor, Direction.UP,
                        mc.player.getMainHandItem()), 100);
                context.waitTicks(6);
                context.takeScreenshot(TestScreenshotOptions.of(material == Items.CALCITE
                    ? "growth-calcite-flat-hint" : "growth-basalt-flat-hint"));
            }
            server.runOnServer(game -> {
                var level = game.overworld();
                level.setBlockAndUpdate(conductor, Blocks.AIR.defaultBlockState());
                ((CrystalTableBlockEntity) level.getBlockEntity(crystalPos)).invalidateNetwork();
                check(firstSource(level, pedestalPos) == null, "Detached pedestal retained a crystal source");
                check(!GrowthPlacementHint.canPlaceHint(level, mother, Direction.UP,
                    new ItemStack(Items.CALCITE)), "Detached mother advertised a connection");
            });
            ConvertTable.LOGGER.info("GROWTH_CLIENT_GAME_TEST_PASS: 1/2 export, internal potential, nonconsuming catalysts, shared budgets, recipe selection packets, save/load, sided output and blocked mask, placement hint, growth drain and UI");
        }
    }
    private static CrystalTableBlockEntity firstSource(net.minecraft.world.level.Level level, BlockPos pos) {
        return ((CatalystPedestalBlockEntity) level.getBlockEntity(pos)).crystal();
    }
}

