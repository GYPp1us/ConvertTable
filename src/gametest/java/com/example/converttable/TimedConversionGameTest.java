package com.example.converttable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Real server block entities, with exact deterministic ticker calls and no open menu. */
final class TimedConversionGameTest {
    private static final BlockPos ORIGIN = new BlockPos(24, 104, -24);
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    private static ConversionTableBlockEntity place(ServerLevel level,ConversionTableBlock block) {
        return ConversionExecutionGameTest.place(level,ORIGIN,block);
    }
    private static void ticks(ConversionTableBlockEntity table,int count) {
        for(int i=0;i<count;i++)ConversionTableBlockEntity.tick(table.getLevel(),table.getBlockPos(),table.getBlockState(),table);
    }
    private static void ordinary(ConversionTableBlockEntity table,int count) {
        table.target=BuiltInRegistries.ITEM.getKey(Items.BIRCH_PLANKS);
        table.setItem(0,new ItemStack(Items.OAK_PLANKS,count));
        if(table.variantIndex()<2)table.setItem(1,new ItemStack(RecipeConfig.fuelItem(table.variantIndex()),64));
        table.deaths=10;
    }
    private static int count(Container container,net.minecraft.world.item.Item item) {
        int result=0;for(int i=0;i<container.getContainerSize();i++)if(container.getItem(i).is(item))result+=container.getItem(i).getCount();
        return result;
    }
    static void run(MinecraftServer server) {
        var level=server.overworld();
        for(var block:new ConversionTableBlock[]{ConversionTables.BLACK_GOLD,ConversionTables.END,ConversionTables.SCULK}) {
            var table=place(level,block);ordinary(table,4);
            int duration=table.totalTicks(),fuel=table.getItem(1).getCount(),souls=table.deaths;
            check(duration==(table.variantIndex()==0?80:table.variantIndex()==1?40:20),"Wrong variant duration");
            check(table.convert(false)&&table.processing()&&table.progressTicks()==0,"Manual request did not stage work");
            for(int tick=0;tick<duration-1;tick++) {
                check(table.convert(false),"Repeated start rejected active job");ticks(table,1);
                check(table.getItem(0).getCount()==4&&table.getItem(2).isEmpty()&&table.getItem(1).getCount()==fuel
                    &&table.deaths==souls&&table.phase==0,"Timed work consumed before tick "+duration);
                check(table.progressTicks()==tick+1,"Repeated click accelerated/reset progress");
            }
            ticks(table,1);
            check(table.getItem(0).isEmpty()&&table.getItem(2).getCount()==4&&!table.processing()&&table.status==1,"Cycle did not commit at exact deadline");
            if(table.variantIndex()==0)check(table.getItem(1).getCount()==fuel-RecipeConfig.setting("piglin","cost_n"),"Piglin fee charged twice");
            if(table.variantIndex()==1)check(table.getItem(1).getCount()==fuel-1&&table.phase==RecipeConfig.setting("end","fuel_charge")-RecipeConfig.setting("end","charge_per_batch"),"End fee/phase accounting changed");
            if(table.variantIndex()==2)check(table.deaths==souls-RecipeConfig.setting("sculk","ordinary_souls_per_batch"),"Ordinary soul fee incorrect");
            ticks(table,duration);check(table.getItem(2).getCount()==4,"Manual cycle repeated automatically");
        }
        var table=place(level,ConversionTables.END);ordinary(table,4);table.setRunning(true);ticks(table,20);
        table.setRunning(false);ticks(table,80);
        check(!table.processing()&&table.getItem(0).getCount()==4&&table.getItem(2).isEmpty()&&table.getItem(1).getCount()==64,"Stop committed partially completed work");
        check(table.convert(false),"Restart request failed");ticks(table,20);
        table.target=BuiltInRegistries.ITEM.getKey(Items.SPRUCE_PLANKS);ticks(table,40);
        check(!table.processing()&&table.getItem(2).isEmpty()&&table.getItem(0).getCount()==4,"New target inherited old timer");
        check(table.convert(false),"Changed target could not restart");ticks(table,20);
        table.setItem(0,new ItemStack(Items.OAK_PLANKS,3));ticks(table,40);
        check(table.getItem(2).isEmpty()&&table.getItem(0).getCount()==3,"Removed input inherited old timer");
        table=place(level,ConversionTables.END);ordinary(table,4);table.setItem(1,ItemStack.EMPTY);
        check(!table.convert(false)&&!table.processing()&&table.status==4&&table.getItem(0).getCount()==4,"Missing fuel started/consumed work");
        table=place(level,ConversionTables.SCULK);ordinary(table,4);table.deaths=0;
        check(table.soulCost()==RecipeConfig.setting("sculk","ordinary_souls_per_batch")&&!table.processing()
            &&table.progressTicks()==0&&table.pendingInput==null&&table.pendingOutput==null,"Idle ordinary fee preview started/rolled work");
        table.inputMode=1;table.matchMode=0;
        check(table.soulCost()==RecipeConfig.setting("sculk","ordinary_souls_per_batch"),"Linked sample fee preview missing");
        table.setItem(0,ItemStack.EMPTY);
        check(table.soulCost()==RecipeConfig.setting("sculk","ordinary_souls_per_batch")&&!table.processing()
            &&table.pendingOutput==null,"Unambiguous selected-target fee preview missing/started work");
        table.target=BuiltInRegistries.ITEM.getKey(Items.AIR);check(table.soulCost()==0,"No target should have unknown fee");
        ordinary(table,4);table.deaths=0;table.inputMode=0;
        check(!table.convert(false)&&!table.processing()&&table.status==5&&table.soulCost()==RecipeConfig.setting("sculk","ordinary_souls_per_batch"),"Missing souls not gated before start");

        // The amount accepted by a partial output remains fixed even when more room/stock appears.
        table=place(level,ConversionTables.END);ordinary(table,8);table.setItem(2,new ItemStack(Items.BIRCH_PLANKS,60));
        check(table.convert(false),"Partial-output job did not start");ticks(table,20);
        table.setItem(0,new ItemStack(Items.OAK_PLANKS,12));table.setItem(2,new ItemStack(Items.BIRCH_PLANKS,56));ticks(table,20);
        check(table.getItem(0).getCount()==8&&table.getItem(2).getCount()==60&&table.getItem(1).getCount()==63,"Partial-output cycle enlarged or restarted when space/stock changed");

        // Output blockage retains a stable random choice and completed elapsed work, spending nothing.
        table=place(level,ConversionTables.BLACK_GOLD);ordinary(table,4);table.setItem(2,new ItemStack(Items.STONE,64));
        check(table.convert(false),"Blocked output could not stage a waiting job");var chosen=table.pendingOutput;
        ticks(table,table.totalTicks()+8);
        check(table.processing()&&table.progressTicks()==80&&table.status==6&&table.pendingOutput.equals(chosen)
            &&table.getItem(0).getCount()==4&&table.getItem(1).getCount()==64,"Blocked output rerolled or spent resources");
        var restored=(ConversionTableBlockEntity)BlockEntity.loadStatic(ORIGIN,table.getBlockState(),table.saveWithFullMetadata(server.registryAccess()),server.registryAccess());
        restored.setLevel(level);check(restored.progressTicks()==0&&chosen.equals(restored.pendingOutput),"Reload did not restart elapsed work/retain random output");
        restored.setItem(2,ItemStack.EMPTY);ticks(restored,79);
        check(restored.getItem(0).getCount()==4&&restored.getItem(1).getCount()==64,"Reloaded manual work consumed early");ticks(restored,1);
        check(BuiltInRegistries.ITEM.getKey(restored.getItem(2).getItem()).equals(chosen)&&restored.getItem(1).getCount()==64-RecipeConfig.setting("piglin","cost_n"),"Reload rerolled/charged incorrectly");
        table.setItem(2,ItemStack.EMPTY);ticks(table,1);
        check(!table.processing()&&BuiltInRegistries.ITEM.getKey(table.getItem(2).getItem()).equals(chosen),"Completed waiting job could not export when space opened");

        // Linked inputs run solely from the server ticker; added stock cannot reset or enlarge a cycle.
        table=place(level,ConversionTables.END);ordinary(table,1);table.inputMode=1;table.matchMode=0;
        level.setBlockAndUpdate(ORIGIN.east(),Blocks.BARREL.defaultBlockState());
        level.setBlockAndUpdate(ORIGIN.west(),Blocks.BARREL.defaultBlockState());
        var source=(Container)level.getBlockEntity(ORIGIN.east());var destination=(Container)level.getBlockEntity(ORIGIN.west());
        source.setItem(0,new ItemStack(Items.OAK_PLANKS,4));
        table.links.toggle(level,ORIGIN,ORIGIN.east(),Direction.UP,true);table.links.toggle(level,ORIGIN,ORIGIN.west(),Direction.UP,false);
        table.setRunning(true);ticks(table,20);
        source.setItem(0,new ItemStack(Items.OAK_PLANKS,8));destination.setItem(26,new ItemStack(Items.DIAMOND,3));ticks(table,19);
        check(count(destination,Items.BIRCH_PLANKS)==0&&source.getItem(0).getCount()==8,"Closed-menu linked cycle consumed early");ticks(table,1);
        check(count(destination,Items.BIRCH_PLANKS)==4&&source.getItem(0).getCount()==4&&destination.getItem(26).getCount()==3&&table.getItem(0).getCount()==1,"Added stock reset/enlarged cycle or stale snapshot erased unrelated slots");
        ticks(table,40);check(count(destination,Items.BIRCH_PLANKS)==8&&source.getItem(0).isEmpty(),"Continuous mode required another manual click");
        source.setItem(0,new ItemStack(Items.OAK_PLANKS,4));ticks(table,20);table.setItem(0,new ItemStack(Items.SPRUCE_PLANKS));ticks(table,1);
        check(table.progressTicks()==0&&source.getItem(0).getCount()==4,"Changed linked sample retained elapsed work");table.setRunning(false);

        // Rebuild nested inventory on completion, preserving unrelated live edits and outer metadata.
        table=place(level,ConversionTables.END);ordinary(table,1);table.inputMode=2;table.matchMode=1;
        var box=new ItemStack(BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("minecraft:blue_shulker_box")));
        var contents=NonNullList.withSize(27,ItemStack.EMPTY);
        contents.set(3,new ItemStack(Items.OAK_PLANKS,4));box.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(contents));table.setItem(0,box);
        check(table.convert(false),"Nested job did not start");ticks(table,20);
        contents.set(26,new ItemStack(Items.DIAMOND,5));table.getItem(0).set(DataComponents.CONTAINER,ItemContainerContents.fromItems(contents));ticks(table,20);
        var actual=table.getItem(0).get(DataComponents.CONTAINER).itemCopies().toList();
        check(actual.get(3).is(Items.BIRCH_PLANKS)&&actual.get(26).getCount()==5,"Nested completion overwrote new unrelated payload");

        // Advanced costs remain the recipe death count; ordinary's one soul is never added to it.
        table=place(level,ConversionTables.SCULK);var recipe=RecipeConfig.server().advanced().stream().filter(r->!r.catalyst().isEmpty()).findFirst().orElseThrow();
        table.target=BuiltInRegistries.ITEM.getKey(recipe.output().getItem());table.deaths=recipe.deaths()+3;
        table.setItem(0,recipe.input().copy());table.setItem(3,recipe.catalyst().copy());
        var previousInput=table.pendingInput;var previousOutput=table.pendingOutput;
        check(table.soulCost()==recipe.deaths()&&!table.processing()&&table.progressTicks()==0
            &&table.pendingInput==previousInput&&table.pendingOutput==previousOutput,"Idle advanced fee preview started/rolled work");
        check(table.convert(false)&&table.soulCost()==recipe.deaths(),"Advanced job cost/start changed");ticks(table,10);
        table.setItem(3,ItemStack.EMPTY);ticks(table,20);
        check(table.getItem(0).getCount()==recipe.input().getCount()&&table.deaths==recipe.deaths()+3&&table.getItem(2).isEmpty(),"Changed catalyst committed old timer");
        table.setItem(3,recipe.catalyst().copy());check(table.convert(false),"Advanced restart failed");ticks(table,19);
        check(table.deaths==recipe.deaths()+3&&!table.getItem(3).isEmpty(),"Advanced ingredients/souls consumed early");ticks(table,1);
        check(table.deaths==3&&table.getItem(0).isEmpty()&&table.getItem(3).isEmpty(),"Advanced cost incorrectly included ordinary soul fee");
        level.setBlockAndUpdate(ORIGIN,Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(ORIGIN.east(),Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(ORIGIN.west(),Blocks.AIR.defaultBlockState());
        ConvertTable.LOGGER.info("TIMED_CONVERSION_TEST_PASS: exact80/40/20 ticks, no early costs, cancellation, blocked output/random persistence, closed-menu linked continuation, live nested payloads and ordinary/advanced soul fees");
    }
}
