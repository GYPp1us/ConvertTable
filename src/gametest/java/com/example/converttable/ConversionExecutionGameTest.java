package com.example.converttable;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.entity.*;

final class ConversionExecutionGameTest {
    /** Transaction checks deliberately call the commit primitive; the timed public path is tested separately. */
    static boolean commit(ConversionTableBlockEntity table, boolean automatic) {
        table.status = ConversionEngine.execute(table, automatic);
        return table.status == 1;
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static ConversionTableBlockEntity place(ServerLevel level,BlockPos pos,ConversionTableBlock block) {
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(pos,block.defaultBlockState());
        return (ConversionTableBlockEntity)level.getBlockEntity(pos);
    }
    static void target(ConversionTableBlockEntity table,Item item){table.target=BuiltInRegistries.ITEM.getKey(item);}
    static void verifyAutomatic(MinecraftServer server) {
        var table=(ConversionTableBlockEntity)server.overworld().getBlockEntity(new BlockPos(-8,101,-8));
        check(table.getItem(2).is(Items.BIRCH_PLANKS)&&table.phase==0&&table.deaths==0,"Closed-menu automatic ticking or soul accounting failed");table.running=false;
        ConvertTable.LOGGER.info("CONVERSION_AUTOMATION_TEST_PASS: actual server ticking with menu closed");
    }
    static void run(MinecraftServer server) {
        check(RecipeConfig.enabled(),"Test config did not enable execution");
        var level=server.overworld();var pos=new BlockPos(8,102,-8);
        var table=place(level,pos,ConversionTables.END);
        check(Arrays.equals(table.getSlotsForFace(Direction.DOWN),new int[]{2}),"Hopper can extract inputs/fuel");
        check(table.canPlaceItemThroughFace(0,new ItemStack(Items.OAK_PLANKS),Direction.UP)
            &&table.canPlaceItemThroughFace(1,new ItemStack(Items.CHORUS_FRUIT),Direction.WEST)
            &&!table.canPlaceItemThroughFace(1,new ItemStack(Items.OAK_PLANKS),Direction.WEST),"Hopper insertion routing failed");
        target(table,Items.BIRCH_PLANKS);table.setItem(0,new ItemStack(Items.OAK_PLANKS,32));table.setItem(1,new ItemStack(Items.CHORUS_FRUIT,2));
        table.setItem(2,new ItemStack(Items.STONE,64));
        check(!ConversionExecutionGameTest.commit(table,false)&&table.status==6&&table.phase==0&&table.getItem(1).getCount()==2&&table.getItem(0).getCount()==32,"Blocked output charged or consumed");
        table.setItem(2,ItemStack.EMPTY);table.getItem(0).set(DataComponents.CUSTOM_NAME,Component.literal("Keep my name"));
        check(ConversionExecutionGameTest.commit(table,false),"End conversion failed: "+table.status);
        check(table.getItem(2).is(Items.BIRCH_PLANKS)&&table.getItem(2).getHoverName().getString().equals("Keep my name"),"Output or custom name incorrect");
        check(table.getItem(0).getCount()+table.getItem(2).getCount()==32&&table.getItem(1).getCount()==1&&table.phase==15,"Conservation/fuel accounting failed");
        var restore=(ConversionTableBlockEntity)BlockEntity.loadStatic(pos,table.getBlockState(),table.saveWithFullMetadata(server.registryAccess()),server.registryAccess());
        check(restore.phase==15,"Phase charge did not persist");
        // Linked containers: separate explicit roles, no sample consumption.
        table.clearContent();table.inputMode=1;table.matchMode=0;table.setItem(0,new ItemStack(Items.OAK_PLANKS));
        level.setBlockAndUpdate(pos.east(),Blocks.BARREL.defaultBlockState());var barrel=(BarrelBlockEntity)level.getBlockEntity(pos.east());
        barrel.setItem(0,new ItemStack(Items.OAK_PLANKS,16));barrel.setItem(1,new ItemStack(Items.SPRUCE_PLANKS,16));
        level.setBlockAndUpdate(pos.west(),Blocks.BARREL.defaultBlockState());var destination=(BarrelBlockEntity)level.getBlockEntity(pos.west());
        table.links.toggle(level,pos,pos.east(),Direction.WEST,true);
        table.links.toggle(level,pos,pos.west(),Direction.EAST,false);
        check(ConversionExecutionGameTest.commit(table,false)&&barrel.getItem(0).isEmpty()&&destination.getItem(0).is(Items.BIRCH_PLANKS)&&barrel.getItem(1).is(Items.SPRUCE_PLANKS)&&table.getItem(0).getCount()==1,"Linked exact conversion/sample failed");
        table.matchMode=1;check(ConversionExecutionGameTest.commit(table,false)&&barrel.getItem(1).isEmpty()&&destination.getItem(0).getCount()==32,"Group matching failed");
        var linkedRestore=(ConversionTableBlockEntity)BlockEntity.loadStatic(pos,table.getBlockState(),table.saveWithFullMetadata(server.registryAccess()),server.registryAccess());
        linkedRestore.setLevel(level);check(linkedRestore.inputLinks().size()==1&&linkedRestore.outputLinks().size()==1,"Links did not persist");
        table.links.clear(true);table.links.clear(false);
        table.matchMode=0;table.clearContent();check(!ConversionExecutionGameTest.commit(table,false)&&table.status==10,"Exact mode ran without a sample");
        // Nested contents: sparse slot indices, names and unrelated contents survive.
        table.inputMode=2;table.matchMode=1;var box=new ItemStack(BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("minecraft:blue_shulker_box")));
        box.set(DataComponents.CUSTOM_NAME,Component.literal("My box"));
        var items=NonNullList.withSize(27,ItemStack.EMPTY);items.set(3,new ItemStack(Items.OAK_PLANKS,16));items.set(26,new ItemStack(Items.DIAMOND,3));
        box.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(items));table.setItem(0,box);
        check(ConversionExecutionGameTest.commit(table,false),"Nested conversion failed: "+table.status);
        var inner=table.getItem(0).get(DataComponents.CONTAINER).itemCopies().toList();
        check(inner.size()==27&&inner.get(3).is(Items.BIRCH_PLANKS)&&inner.get(26).getCount()==3&&table.getItem(0).getHoverName().getString().equals("My box"),"Nested component/slot data lost");
        // Ordinary shulker recolouring retains its content component.
        table.inputMode=0;table.clearContent();table.setItem(0,box);target(table,BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("minecraft:red_shulker_box")));
        check(ConversionExecutionGameTest.commit(table,false)&&table.getItem(2).is(BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("minecraft:red_shulker_box")))&&table.getItem(2).get(DataComponents.CONTAINER).equals(box.get(DataComponents.CONTAINER)),"Recolouring lost container contents");
        // Piglin random target persists while blocked, and charges exactly once per batch.
        table=place(level,pos,ConversionTables.BLACK_GOLD);table.setItem(0,new ItemStack(Items.OAK_PLANKS,7));table.setItem(1,new ItemStack(Items.GOLD_NUGGET,2));table.setItem(2,new ItemStack(Items.STONE,64));
        check(!ConversionExecutionGameTest.commit(table,false)&&table.status==6,"Piglin should be blocked");var pending=table.pendingOutput;
        check(pending!=null&&!pending.equals(BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS)),"Invalid random target");
        for(int i=0;i<8;i++)check(!ConversionExecutionGameTest.commit(table,false)&&pending.equals(table.pendingOutput)&&table.getItem(1).getCount()==2,"Blocked reroll/charge");
        restore=(ConversionTableBlockEntity)BlockEntity.loadStatic(pos,table.getBlockState(),table.saveWithFullMetadata(server.registryAccess()),server.registryAccess());
        check(pending.equals(restore.pendingOutput),"Pending random output not saved");
        table.setItem(2,ItemStack.EMPTY);check(ConversionExecutionGameTest.commit(table,false)&&table.getItem(1).getCount()==1&&BuiltInRegistries.ITEM.getKey(table.getItem(2).getItem()).equals(pending),"Piglin commit failed");
        // Every configured advanced recipe gets its actual input, catalyst, phase and death costs.
        table=place(level,pos,ConversionTables.SCULK);
        for(var recipe:RecipeConfig.server().advanced()) {
            table.clearContent();table.inputMode=0;table.deaths=recipe.deaths();table.phase=0;
            target(table,recipe.output().getItem());table.setItem(0,recipe.input().copy());table.setItem(3,recipe.catalyst().copy());
            if(!recipe.auto())check(!ConversionExecutionGameTest.commit(table,true)&&table.status==8&&table.deaths==recipe.deaths(),"Manual recipe automated: "+recipe.id());
            check(ConversionExecutionGameTest.commit(table,false),"Advanced recipe failed: "+recipe.id()+" status="+table.status);
            var actual=table.getItem(2);
            check(recipe.outputs().stream().anyMatch(s->ItemStack.matches(s,actual)),"Advanced output wrong: "+recipe.id());
            check(table.deaths==0&&table.phase==0&&table.getItem(0).isEmpty()&&table.getItem(3).isEmpty()&&table.getItem(1).isEmpty(),"Advanced costs wrong: "+recipe.id());
            if(!recipe.returns().isEmpty())check(ItemStack.matches(table.getItem(4),recipe.returns().getFirst()),"Missing remainder: "+recipe.id());
        }
        var coral=RecipeConfig.server().advanced().stream().filter(r->!r.returns().isEmpty()).findFirst().orElseThrow();
        table.clearContent();table.deaths=100;table.phase=2;target(table,coral.output().getItem());table.setItem(0,coral.input().copy());table.setItem(3,coral.catalyst().copy());table.setItem(4,new ItemStack(Items.STONE,64));
        check(!ConversionExecutionGameTest.commit(table,false)&&table.status==6&&table.deaths==100&&table.phase==2&&table.getItem(3).is(Items.WATER_BUCKET),"Blocked remainder consumed ingredients");
        table.setItem(4,ItemStack.EMPTY);table.deaths=0;
        check(!ConversionExecutionGameTest.commit(table,false)&&table.status==5&&table.getItem(3).is(Items.WATER_BUCKET),"Missing death charge consumed catalyst");
        // Actual event, shared ownership and edge-of-solid -> flat vein -> sculk bridge.
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.AIR.defaultBlockState());
        var base=new BlockPos(-8,101,-8);
        var first=place(level,base,ConversionTables.SCULK);var second=place(level,base.offset(4,0,0),ConversionTables.SCULK);
        for(int x=0;x<=4;x++)level.setBlockAndUpdate(base.offset(x,-1,0),Blocks.SCULK.defaultBlockState());
        var ground=base.offset(2,-1,0);var mob=(Mob)BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.parse("minecraft:pig")).create(level,EntitySpawnReason.COMMAND);
        mob.setPos(ground.getX()+0.5,ground.getY()+1,ground.getZ()+0.5);mob.setNoAi(true);level.addFreshEntity(mob);
        mob.hurtServer(level,level.damageSources().genericKill(),1000);
        int firstSouls=((DeathExperienceReward)mob).convertTable$deathExperienceReward()*32;
        check(firstSouls>0&&first.deaths==firstSouls&&second.deaths==0,"Shared network did not credit XP-derived souls to one nearest table: "+first.deaths+" / "+second.deaths);
        SculkDeathCharging.afterDeath(mob,level.damageSources().genericKill());check(first.deaths==firstSouls,"Death counted twice");
        var airborne=(Mob)BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.parse("minecraft:pig")).create(level,EntitySpawnReason.COMMAND);airborne.setPos(ground.getX()+.5,ground.getY()+3,ground.getZ()+.5);level.addFreshEntity(airborne);airborne.hurtServer(level,level.damageSources().genericKill(),1000);
        check(first.deaths+second.deaths==firstSouls,"Airborne death counted");
        level.setBlockAndUpdate(base.offset(1,-1,0),Blocks.AIR.defaultBlockState());
        var nearSecond=(Mob)BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.parse("minecraft:pig")).create(level,EntitySpawnReason.COMMAND);nearSecond.setPos(ground.getX()+.5,ground.getY()+1,ground.getZ()+.5);level.addFreshEntity(nearSecond);nearSecond.hurtServer(level,level.damageSources().genericKill(),1000);
        int secondSouls=((DeathExperienceReward)nearSecond).convertTable$deathExperienceReward()*32;
        check(secondSouls>0&&first.deaths==firstSouls&&second.deaths==secondSouls,"Broken path cached during death / charge reset");
        restore=(ConversionTableBlockEntity)BlockEntity.loadStatic(base,first.getBlockState(),first.saveWithFullMetadata(server.registryAccess()),server.registryAccess());
        check(restore.deaths==firstSouls,"Soul charge not saved");
        // Reproduce the screenshot's floor-level vein bridge across a normal supporting block.
        level.setBlockAndUpdate(base.offset(1,-1,0),Blocks.STONE.defaultBlockState());
        var down=Blocks.SCULK_VEIN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.DOWN),true);
        level.setBlockAndUpdate(base.offset(1,0,0),down);
        var network=TableRangeScanner.scan(level,base,true);
        check(network.surfaces().containsKey(ground),"Flat vein bridge failed to connect sculk top edge");
        check(network.pixels()[16*33+18]==2,"Ground surface absent from X-Z projection");
        // Ticker performs real automatic conversion without requiring an open menu.
        first.clearContent();first.phase=1;first.deaths=RecipeConfig.setting("sculk","ordinary_souls_per_batch");first.inputMode=0;target(first,Items.BIRCH_PLANKS);first.setItem(0,new ItemStack(Items.OAK_PLANKS));first.running=true;
        ConvertTable.LOGGER.info("CONVERSION_EXECUTION_TEST_PASS: atomic costs, {} advanced recipes without phase fuel, remainders, random lock, nested data, explicit linked samples, real deaths, ownership, XZ projection and ticker",RecipeConfig.server().advanced().size());
    }
}
