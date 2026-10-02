package com.example.converttable;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Real client/server input, conversion, timing and viewer tests in an isolated world. */
final class ConversionTableUiGameTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    static void run(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            String requested=System.getProperty("convert_table.testBackend","");
            String active=com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().backendName();
            check(requested.isEmpty() || active.equalsIgnoreCase(requested),"Unexpected graphics backend: "+active);
            ConvertTable.LOGGER.info("CONVERSION_TABLE_UI_BACKEND: {}",active);

            mc.options.guiScale().set(3); mc.options.languageCode="zh_cn"; mc.getLanguageManager().setSelected("zh_cn"); });
        var reload=context.computeOnClient(mc->mc.reloadResourcePacks());
        context.waitFor(mc->reload.isDone(),600); reload.join();
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runOnServer(game->ConversionRecipeGameTest.verifyConfig());
            ConversionRecipeGameTest.verifyClient(context);
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("fill -20 98 -20 20 106 20 air");
            server.runCommand("fill -20 98 -20 20 99 20 polished_deepslate");
            server.runCommand("tp @a 0.5 100 3.5 180 20");
            var origin=new BlockPos(0,100,0);
            server.runOnServer(game->{
                var level=game.overworld();
                level.setBlockAndUpdate(origin,ConversionTables.SCULK.defaultBlockState());
                level.setBlockAndUpdate(origin.offset(1,0,0),Blocks.SCULK.defaultBlockState());
                level.setBlockAndUpdate(origin.offset(2,0,0),Blocks.SCULK.defaultBlockState());
                level.setBlockAndUpdate(origin.offset(2,1,0),Blocks.SCULK_CATALYST.defaultBlockState());
                level.setBlockAndUpdate(origin.offset(1,0,1),Blocks.SCULK.defaultBlockState());
                level.setBlockAndUpdate(origin.offset(-3,0,0),Blocks.SCULK.defaultBlockState());
                var vein=Blocks.SCULK_VEIN.defaultBlockState().setValue(net.minecraft.world.level.block.MultifaceBlock.getFaceProperty(Direction.DOWN),true);
                check(TableRangeScanner.connects(vein,vein,Direction.EAST),"Coplanar veins should connect");
                check(!TableRangeScanner.connects(vein,Blocks.SCULK_VEIN.defaultBlockState().setValue(net.minecraft.world.level.block.MultifaceBlock.getFaceProperty(Direction.UP),true),Direction.EAST),"Disjoint vein planes connected");
                check(TableRangeScanner.connects(vein,Blocks.SCULK.defaultBlockState(),Direction.DOWN),"Attached vein face disconnected");
                var dropPos=origin.offset(10,0,-5);
                level.setBlockAndUpdate(dropPos,ConversionTables.END.defaultBlockState());
                var dropTable=(ConversionTableBlockEntity)level.getBlockEntity(dropPos);
                dropTable.setItem(0,new ItemStack(Items.DIAMOND,7));
                level.setBlockAndUpdate(dropPos,Blocks.AIR.defaultBlockState());
                int dropped=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(dropPos).inflate(1))
                    .stream().filter(e->e.getItem().is(Items.DIAMOND)).mapToInt(e->e.getItem().getCount()).sum();
                check(dropped==7,"Container contents dropped incorrectly: "+dropped);
                var scan=TableRangeScanner.scan(level,origin,true);
                check(scan.nodes()==4,"Disconnected sculk counted: "+scan.nodes());
                check(scan.ground()==2,"Covered sculk top counted: "+scan.ground());
                check(scan.pixels()[16*33+17]==2,"XZ projection missing X=1,Z=0");
                check(scan.pixels()[16*33+18]==1,"XZ projection did not collapse covered vertical nodes");
                check(scan.pixels()[17*33+17]==2,"XZ projection did not preserve positive Z");
                check(scan.pixels()[16*33+13]==0,"Disconnected node projected");

                // Physical endpoints used by the later explicit input/output bindings.
                var left=Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.NORTH).setValue(ChestBlock.TYPE,ChestType.LEFT);
                BlockPos chest=origin.offset(-1,0,-1), partner=ChestBlock.getConnectedBlockPos(chest,left);
                level.setBlock(chest,left,2);
                level.setBlock(partner,left.setValue(ChestBlock.TYPE,ChestType.RIGHT),2);
                level.setBlockAndUpdate(origin.offset(0,0,4),Blocks.BARREL.defaultBlockState());
                level.setBlockAndUpdate(origin.offset(0,0,5),Blocks.BARREL.defaultBlockState());
                // Add a visible branching network for the screenshot.
                for(int x=3;x<=9;x++) level.setBlockAndUpdate(origin.offset(x,0,0),Blocks.SCULK.defaultBlockState());
                for(int y=1;y<=5;y++) level.setBlockAndUpdate(origin.offset(8,y,0),Blocks.SCULK.defaultBlockState());
                for(int z=1;z<=5;z++) level.setBlockAndUpdate(origin.offset(5,0,z),Blocks.SCULK.defaultBlockState());
                for(int z=-1;z>=-5;z--) level.setBlockAndUpdate(origin.offset(4,0,z),Blocks.SCULK.defaultBlockState());
            });
            var variants=new ConversionTableBlock[]{ConversionTables.BLACK_GOLD,ConversionTables.END,ConversionTables.SCULK};
            var names=new String[]{"piglin","end","sculk"};
            for(int i=0;i<3;i++) {
                final int variant=i;
                context.runOnClient(mc-> {if(mc.gui.screen()!=null)mc.gui.screen().onClose();});
                context.waitTicks(5);
                server.runOnServer(game-> {
                    var level=game.overworld(); var player=game.getPlayerList().getPlayers().getFirst();
                    level.setBlockAndUpdate(origin,variants[variant].defaultBlockState());
                    var table=(ConversionTableBlockEntity)level.getBlockEntity(origin);
                    var input=new ItemStack(Items.OAK_PLANKS,32);
                    input.set(DataComponents.CUSTOM_NAME,Component.literal("Stored safely"));
                    table.setItem(0,input);
                    if (variant < 2) table.setItem(1,new ItemStack(variant==0?Items.GOLD_NUGGET:Items.CHORUS_FRUIT,8));
                    check(table.links.toggle(level, origin, origin.offset(-1,0,-1), Direction.UP, true)==1,"Input link setup failed");
                    check(table.links.toggle(level, origin, origin.offset(0,0,4), Direction.UP, false)==1,"Output link setup failed");
                    variants[variant].useWithoutItem(level.getBlockState(origin),level,origin,player,
                        new BlockHitResult(Vec3.atCenterOf(origin),Direction.SOUTH,origin,false));
                    check(player.containerMenu instanceof ConversionTableMenu,"Right click did not open menu");
                    var menu=(ConversionTableMenu)player.containerMenu;
                    check(!menu.getSlot(2).mayPlace(input),"Output accepts insertion");
                    check(!menu.getSlot(1).mayPlace(input),"Fuel accepts invalid item");
                    check(!menu.clickMenuButton(player,Integer.MAX_VALUE),"Invalid button accepted");
                    if (variant < 2) {
                        player.getInventory().setItem(9,new ItemStack(variant==0?Items.GOLD_NUGGET:Items.CHORUS_FRUIT,4));
                        check(!menu.quickMoveStack(player,menu.deviceSlots()).isEmpty() && table.getItem(1).getCount()==12
                            && player.getInventory().getItem(9).isEmpty(),"Shift-click fuel routing failed");
                        table.removeItem(1,4);
                    } else {
                        check(!menu.getSlot(1).isActive() && !menu.getSlot(1).mayPlace(new ItemStack(Items.CHORUS_FRUIT)),"Sculk fuel slot remained usable");
                    }
                    if(variant==0) check(!menu.clickMenuButton(player,1),"Piglin has mode controls");
                    else {
                        check(!menu.clickMenuButton(player,1000+BuiltInRegistries.ITEM.getId(Items.AIR)),"Air target accepted");
                        check(!menu.clickMenuButton(player,1000+BuiltInRegistries.ITEM.getId(Items.DIAMOND)),"Unrelated target accepted");
                        check(menu.clickMenuButton(player,1000+BuiltInRegistries.ITEM.getId(Items.BIRCH_PLANKS)),"Target not accepted");
                        check(menu.clickMenuButton(player,3),"Match toggle rejected");
                    }
                    var saved=table.saveWithFullMetadata(game.registryAccess());
                    var restored=(ConversionTableBlockEntity)BlockEntity.loadStatic(origin,level.getBlockState(origin),saved,game.registryAccess());
                    check(restored!=null && restored.getItem(0).getCount()==32 && restored.getItem(0).getHoverName().getString().equals("Stored safely"),"Inventory components lost on save");
                    check(restored.matchMode==table.matchMode && restored.target.equals(table.target),"Preferences lost on save");
                });
                context.waitFor(mc->mc.gui.screen() instanceof ConversionTableScreen,100);
                context.waitTicks(25);
                context.runOnClient(mc->{
                    var menu=(ConversionTableMenu)mc.player.containerMenu;
                    check(menu.variant==variant,"Wrong client variant");
                    check(menu.getSlot(0).getItem().getCount()==32 && menu.getSlot(1).getItem().getCount()==(variant<2?8:0),"Input/fuel changed");
                    check(menu.inputContainerCount()==1 && menu.outputContainerCount()==1,"Input/output role counts not synchronized");
                    if(variant>0) {
                        check(menu.containerCount()==2,"Container count not synchronized");
                        check(menu.previewTarget()==Items.BIRCH_PLANKS && menu.matchMode()==1,"Preferences not synchronized");
                    }
                    if(variant==2) {
                        check(menu.pixel(18,16)==1 && menu.pixel(17,17)==2,"XZ projection not synchronized");
                        check(menu.pixel(23,16)==2,"Projection bit 15 was lost during signed-short synchronization");
                        check(menu.pixel(20,11)==2 && menu.pixel(21,21)==2,"North/south XZ branches are mirrored or collapsed");
                    }
                });
                if (variant > 0) {
                    if (variant == 2) UiGameTestInput.clickPanel(context,320,238,220,34);
                    UiGameTestInput.clickTarget(context,Items.SPRUCE_PLANKS);
                    context.waitFor(mc->((ConversionTableMenu)mc.player.containerMenu).previewTarget()==Items.SPRUCE_PLANKS,100);
                    server.runOnServer(game->{
                        var table=(ConversionTableBlockEntity)game.overworld().getBlockEntity(origin);
                        check(table.target.equals(BuiltInRegistries.ITEM.getKey(Items.SPRUCE_PLANKS)),"Mouse target click did not reach the server");
                        check(table.getItem(0).getCount()==32,"Target selection consumed input");
                    });
                    UiGameTestInput.clickTarget(context,Items.BIRCH_PLANKS);
                    context.waitFor(mc->((ConversionTableMenu)mc.player.containerMenu).previewTarget()==Items.BIRCH_PLANKS,100);
                }
                if(variant==2) {
                    UiGameTestInput.clickPanel(context,320,238,152,34);
                    context.waitFor(mc->((ConversionTableMenu)mc.player.containerMenu).inputMode()==2,100);
                    UiGameTestInput.clickPanel(context,320,238,280,34);
                    var expected=context.computeOnClient(mc->new int[33*33]);
                    server.runOnServer(game->{
                        int[] pixels=TableRangeScanner.scan(game.overworld(),origin,true).pixels();
                        System.arraycopy(pixels,0,expected,0,pixels.length);
                    });
                    context.runOnClient(mc->{
                        var menu=(ConversionTableMenu)mc.player.containerMenu;
                        for (int at=0;at<expected.length;at++) check(menu.pixel(at%33,at/33)==expected[at],"Projection cell differs from server at "+at);
                    });
                }
                context.runOnClient(mc->{mc.gui.toastManager().clear();});
                context.waitTicks(4);
                context.takeScreenshot(TestScreenshotOptions.of("ui-"+names[i]));
                if(variant==2) {
                    server.runOnServer(game->game.overworld().setBlockAndUpdate(origin.offset(0,0,4),Blocks.AIR.defaultBlockState()));
                    context.waitFor(mc->((ConversionTableMenu)mc.player.containerMenu).containerCount()==1,100);
                    server.runOnServer(game->game.overworld().setBlockAndUpdate(origin,Blocks.AIR.defaultBlockState()));
                    context.waitFor(mc->!(mc.gui.screen() instanceof ConversionTableScreen),100);
                }
            }
            server.runOnServer(GrowthAllocationGameTest::verify);
            server.runOnServer(ConnectionRodGameTest::run);
            server.runOnServer(TimedConversionGameTest::run);
            server.runOnServer(RecipeCoverageGameTest::run);
            TimedConversionUiGameTest.run(context, server);
            server.runOnServer(SculkExperienceGameTest::run);
            server.runOnServer(game->ConversionExecutionGameTest.run(game));
            context.waitTicks(25);
            server.runOnServer(game->ConversionExecutionGameTest.verifyAutomatic(game));
            ConversionRecipeGameTest.viewers(context);
            GrowthJeiGameTest.run(context);
            ConvertTable.LOGGER.info("CONVERSION_TABLE_UI_TEST_PASS: right-click, 3 screens, inventory persistence, no consumption, real mouse target/mode packets, explicit links, no sculk fuel slot, live removal, XZ projection with signed-short high bits");
        }
    }
}
