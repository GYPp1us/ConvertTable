package com.example.converttable;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;

/** One server-thread transaction per batch. All capacity/cost checks precede mutation. */
public final class ConversionEngine {
    // 0 idle, 1 success, 2 input/target, 3 no recipe, 4 fuel, 5 deaths,
    // 6 output full, 7 catalyst, 8 manual only, 9 disabled, 10 sample needed, 11 protected data.
    static int execute(ConversionTableBlockEntity table,boolean automatic) {
        if(!RecipeConfig.enabled())return 9;
        if(table.getLevel()==null||table.getLevel().isClientSide())return 9;
        if(table.variantIndex()==0||table.inputMode==0)return attempt(table,table,0,true,automatic,null);
        if(table.target.equals(BuiltInRegistries.ITEM.getKey(Items.AIR)))return 2;
        if(table.inputMode==1) {
            if(table.matchMode==0&&table.getItem(0).isEmpty())return 10;
            int status=3;
            for(Container source:TableRangeScanner.containers(table.getLevel(),table.getBlockPos()))
                for(int slot=0;slot<source.getContainerSize();slot++) {
                    ItemStack stack=source.getItem(slot);
                    if(stack.isEmpty() || table.matchMode==0&&!stack.is(table.getItem(0).getItem()))continue;
                    int result=attempt(table,source,slot,false,automatic,null);
                    if(result==1)return 1;if(result!=3)status=result;
                }
            return status;
        }
        ItemStack box=table.getItem(0);
        var contents=box.get(DataComponents.CONTAINER);
        if(contents==null||box.getCount()!=1)return 2;
        // Only real container items; don't manufacture a container component on arbitrary items.
        if(!(box.getItem() instanceof BlockItem blockItem)
            ||!(blockItem.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock))return 11;
        var inventory=new SimpleContainer(27);
        var saved=contents.itemCopies().toList();
        if(saved.size()>27)return 11;
        for(int i=0;i<saved.size();i++)inventory.setItem(i,saved.get(i));
        int status=3;
        for(int i=0;i<inventory.getContainerSize();i++) {
            ItemStack stack=inventory.getItem(i);
            if(stack.isEmpty()||table.matchMode==0&&table.filterSource!=null
                &&!BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(table.filterSource))continue;
            int result=attempt(table,inventory,i,false,automatic,box);
            if(result==1)return 1;if(result!=3)status=result;
        }
        return status;
    }
    private static int attempt(ConversionTableBlockEntity t,Container source,int slot,boolean device,boolean automatic,ItemStack box) {
        ItemStack input=source.getItem(slot);
        if(input.isEmpty())return 2;
        if(!device&&!source.canTakeItem(t,slot,input))return 3;
        var catalog=RecipeConfig.server();int variant=t.variantIndex();
        Item target=BuiltInRegistries.ITEM.getValue(t.target);
        if(variant>0&&target==Items.AIR)return 3;
        RecipeCatalog.Group group=null;
        for(var candidate:catalog.groups())if(candidate.tier()<=variant&&candidate.items().contains(input.getItem())
            &&(variant==0||!input.is(target)&&candidate.items().contains(target))) {group=candidate;break;}
        RecipeCatalog.Advanced advanced=null;
        if(group==null&&variant==2) {
            for(var candidate:catalog.advanced())if(input.is(candidate.input().getItem())&&candidate.output().is(target)) {
                if(advanced!=null)return 3; // An ambiguous custom config cannot silently choose a recipe.
                advanced=candidate;
            }
        }
        if(group==null&&advanced==null)return 3;
        if(advanced!=null) {
            if(!advanced.auto()&&(automatic||t.inputMode!=0))return 8;
            if(input.getCount()<advanced.input().getCount())return 2;
            if(!advanced.catalyst().isEmpty()&&(!t.getItem(3).is(advanced.catalyst().getItem())
                ||t.getItem(3).getCount()<advanced.catalyst().getCount()))return 7;
            if(t.deaths<advanced.deaths())return 5;
            // Advanced transformations must never consume stored items or block-entity payloads.
            if(hasPayload(input)||hasPayload(t.getItem(3)))return 11;
        }
        if(variant==0) {
            var key=BuiltInRegistries.ITEM.getKey(input.getItem());
            if(t.pendingInput==null||!t.pendingInput.equals(key)||t.pendingOutput==null
                ||!group.items().contains(BuiltInRegistries.ITEM.getValue(t.pendingOutput))) {
                var options=group.items().stream().filter(i->i!=input.getItem()).toList();
                target=options.get(t.getLevel().getRandom().nextInt(options.size()));
                t.pendingInput=key;t.pendingOutput=BuiltInRegistries.ITEM.getKey(target);t.setChanged();
            } else target=BuiltInRegistries.ITEM.getValue(t.pendingOutput);
        }
        int used=advanced==null?Math.min(input.getCount(),group.batch()):advanced.input().getCount();
        ItemStack output=advanced==null?input.transmuteCopy(target,used):advanced.output().copy();
        if(advanced!=null)output.copyFrom(DataComponents.CUSTOM_NAME,input);
        int phaseCost=variant==0?0:RecipeConfig.setting(advanced==null?"end":"sculk",advanced==null?"charge_per_batch":"advanced_charge_per_operation");
        int fuelCount=variant==0?RecipeConfig.setting("piglin","cost_n"):
            Math.max(0,(phaseCost-t.phase+RecipeConfig.setting("end","fuel_charge")-1)/RecipeConfig.setting("end","fuel_charge"));
        if(fuelCount>0&&(!t.getItem(1).is(RecipeConfig.fuelItem(variant))||t.getItem(1).getCount()<fuelCount))return 4;
        ItemStack remainder=advanced==null||advanced.returns().isEmpty()?ItemStack.EMPTY:advanced.returns().getFirst();
        if(!fits(t.getItem(4),remainder,t.getMaxStackSize()))return 6;
        List<ItemStack> changed=null;
        if(device) {
            if(advanced==null) {
                int room=room(t.getItem(2),output,t.getMaxStackSize());used=Math.min(used,room);
                if(used==0)return 6;output.setCount(used);
            } else if(!fits(t.getItem(2),output,t.getMaxStackSize()))return 6;
        } else {
            // Work on a private snapshot; replace/merge in the same connected container.
            // Try smaller ordinary batches when only part of the output fits.
            changed=plan(source,slot,used,output,box!=null);
            if(changed==null&&advanced==null) {
                int available=0;
                for(int i=0;i<source.getContainerSize();i++)if(i!=slot&&(box!=null||source.canPlaceItem(i,output)))
                    available+=room(source.getItem(i),output,source.getMaxStackSize());
                int partial=Math.min(used-1,available);
                if(partial>0) {
                    var proposed=output.copyWithCount(partial);
                    changed=plan(source,slot,partial,proposed,box!=null);
                    if(changed!=null){used=partial;output=proposed;}
                }
            }
            if(changed==null)return 6;
        }
        // Commit: server ticks and menu packets cannot interleave on this thread.
        if(device) {
            t.setItem(0,input.copyWithCount(input.getCount()-used));
            t.setItem(2,merge(t.getItem(2),output));
        } else if(box!=null) {
            ItemStack updated=box.copy();updated.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(changed));t.setItem(0,updated);
            if(t.matchMode==0)t.filterSource=BuiltInRegistries.ITEM.getKey(input.getItem());
        } else {
            for(int i=0;i<changed.size();i++)if(!ItemStack.matches(source.getItem(i),changed.get(i)))source.setItem(i,changed.get(i));
            source.setChanged();
        }
        if(fuelCount>0)t.removeItem(1,fuelCount);
        if(variant>0)t.phase+=fuelCount*RecipeConfig.setting("end","fuel_charge")-phaseCost;
        if(advanced!=null) {
            t.deaths-=advanced.deaths();if(!advanced.catalyst().isEmpty())t.removeItem(3,advanced.catalyst().getCount());
            if(!remainder.isEmpty())t.setItem(4,merge(t.getItem(4),remainder));
        }
        t.pendingInput=null;t.pendingOutput=null;t.setChanged();return 1;
    }
    private static boolean hasPayload(ItemStack stack) {
        var contents=stack.get(DataComponents.CONTAINER);
        return contents!=null&&contents.nonEmptyItemCopyStream().findAny().isPresent()
            ||stack.has(DataComponents.BLOCK_ENTITY_DATA)||stack.has(DataComponents.ENTITY_DATA);
    }
    private static List<ItemStack> plan(Container source,int slot,int consumed,ItemStack output,boolean nested) {
        List<ItemStack> result=new ArrayList<>();
        for(int i=0;i<source.getContainerSize();i++)result.add(source.getItem(i).copy());
        result.get(slot).shrink(consumed);int left=output.getCount();
        // Prefer the original slot, then merge stacks, then empty slots.
        int[] order=new int[source.getContainerSize()+1];order[0]=slot;for(int i=0;i<source.getContainerSize();i++)order[i+1]=i;
        for(int pass=0;pass<2;pass++)for(int i:order) {
            ItemStack current=result.get(i);
            if(!nested&&!source.canPlaceItem(i,output))continue;
            if(nested&&!output.getItem().canFitInsideContainerItems())continue;
            if(pass==0&&current.isEmpty()&&i!=slot)continue;
            int move=Math.min(left,room(current,output,source.getMaxStackSize()));
            if(move>0){result.set(i,merge(current,output.copyWithCount(move)));left-=move;}
            if(left==0)return result;
        }
        return null;
    }
    private static int room(ItemStack current,ItemStack addition,int max) {
        if(addition.isEmpty())return 0;
        if(!current.isEmpty()&&!ItemStack.isSameItemSameComponents(current,addition))return 0;
        return Math.max(0,Math.min(max,addition.getMaxStackSize())-current.getCount());
    }
    private static boolean fits(ItemStack current,ItemStack addition,int max) {return addition.isEmpty()||room(current,addition,max)>=addition.getCount();}
    private static ItemStack merge(ItemStack current,ItemStack addition) {return current.isEmpty()?addition.copy():current.copyWithCount(current.getCount()+addition.getCount());}
}
