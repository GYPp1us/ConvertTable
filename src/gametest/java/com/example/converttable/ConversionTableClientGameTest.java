package com.example.converttable;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Runs only in an isolated generated test world; never included in the release jar. */
public final class ConversionTableClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("convert_table.growthTest")) { GrowthClientGameTest.run(context); return; }
        if (Boolean.getBoolean("convert_table.uiTest")) { ConversionTableUiGameTest.run(context); return; }
        context.runOnClient(mc -> {
            String requested = System.getProperty("convert_table.testBackend", "");
            String active = com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().backendName();
            if (!requested.isEmpty() && !active.equalsIgnoreCase(requested))
                throw new AssertionError("Requested backend " + requested + " but running " + active);
            ConvertTable.LOGGER.info("CONVERSION_TABLE_TEST_BACKEND: {}", active);
            mc.options.renderDistance().set(4);
            mc.options.simulationDistance().set(8);
        });
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set noon");
            server.runCommand("fill -12 98 -8 12 99 8 minecraft:polished_deepslate");
            var blocks = new ConversionTableBlock[]{ConversionTables.BLACK_GOLD, ConversionTables.END, ConversionTables.SCULK};
            var names = new String[]{"black_gold", "end", "sculk"};
            server.runOnServer(game -> {
                var level = game.overworld();
                for (int i = 0; i < blocks.length; i++) {
                    var pos = new BlockPos(i * 3 - 3, 100, 0);
                    level.setBlockAndUpdate(pos, blocks[i].defaultBlockState());
                    if (!(level.getBlockEntity(pos) instanceof ConversionTableBlockEntity be) || !be.variant().equals(names[i]))
                        throw new AssertionError("Wrong placed block entity: " + names[i]);
                    var saved = level.getBlockEntity(pos).saveWithFullMetadata(game.registryAccess());
                    var restored = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos, level.getBlockState(pos), saved, game.registryAccess());
                    if (!(restored instanceof ConversionTableBlockEntity restoredTable) || !restoredTable.variant().equals(names[i]))
                        throw new AssertionError("Block entity changed after save/load: " + names[i]);
                    var drops = Block.getDrops(level.getBlockState(pos), level, pos, level.getBlockEntity(pos), null, new ItemStack(Items.DIAMOND_PICKAXE));
                    if (drops.size() != 1 || !drops.getFirst().is(blocks[i].asItem()))
                        throw new AssertionError("Wrong survival drop: " + names[i]);
                    if (game.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, ConvertTable.id(names[i] + "_conversion_table"))).isEmpty())
                        throw new AssertionError("Missing recipe: " + names[i]);
                    // Exercise all four state variants and entity cleanup outside the shot.
                    for (Direction facing : Direction.Plane.HORIZONTAL) {
                        var testPos = new BlockPos(8, 100, i + facing.get2DDataValue());
                        level.setBlockAndUpdate(testPos, blocks[i].defaultBlockState().setValue(ConversionTableBlock.FACING, facing));
                        if (level.getBlockEntity(testPos) == null) throw new AssertionError("Facing lost block entity");
                        level.setBlockAndUpdate(testPos, Blocks.AIR.defaultBlockState());
                        if (level.getBlockEntity(testPos) != null) throw new AssertionError("Removed entity survived");
                    }
                }
            });
            server.runCommand("tp @a 4.4 101.8 6.5 147 22");
            context.runOnClient(mc -> {
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                mc.options.fov().set(45);
            });
            context.waitTicks(30);
            // Exercise the production renderer even if a chunk-render wait later times out.
            context.runOnClient(mc -> {
                for (int i = 0; i < blocks.length; i++) {
                    var be = (ConversionTableBlockEntity) mc.level.getBlockEntity(new BlockPos(i * 3 - 3, 100, 0));
                    if (be == null) throw new AssertionError("Client did not receive block entity");
                    var renderer = mc.getBlockEntityRenderDispatcher().getRenderer(be);
                    if (renderer == null) throw new AssertionError("Missing block entity renderer");
                    var state = renderer.createRenderState();
                    renderer.extractRenderState(be, state, .5F, mc.player.position(), null);
                }
            });
            world.getConnection().waitForChunksRender();
            context.takeScreenshot(TestScreenshotOptions.of("conversion-tables-in-game").withSize(1280, 720));
            for (int i = 0; i < blocks.length; i++) {
                server.runCommand("tp @a " + (i * 3 - 3 + 2.3) + " 100.6 3.3 147 27");
                context.waitTicks(12);
                world.getConnection().waitForChunksRender();
                context.takeScreenshot(TestScreenshotOptions.of(names[i] + "-phase-a").withSize(960, 960));
                context.waitTicks(50);
                context.takeScreenshot(TestScreenshotOptions.of(names[i] + "-phase-b").withSize(960, 960));
            }
            var reload = context.computeOnClient(mc -> mc.reloadResourcePacks());
            context.waitFor(mc -> reload.isDone(), 600);
            reload.join();
            context.waitTicks(15);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot(TestScreenshotOptions.of("sculk-after-resource-reload").withSize(960, 960));
            ConvertTable.LOGGER.info("CONVERSION_TABLE_GAME_TEST_PASS: entities, facings, drops, recipes, animation render and resource reload");
        }
    }
}
