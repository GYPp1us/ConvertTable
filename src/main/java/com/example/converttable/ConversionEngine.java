package com.example.converttable;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;

/** Plans timed work without spending resources, then replans one atomic server-thread commit. */
public final class ConversionEngine {
    // 0 idle, 1 success, 2 input/target, 3 no recipe, 4 fuel, 5 deaths,
    // 6 output full, 7 catalyst, 8 manual only, 9 disabled, 10 sample needed, 11 protected data, 12 working.
    record Preparation(int status, Job job, int soulCost, boolean invalidated) { }
    /** Only identity and quantities survive between ticks; container mutation plans never do. */
    static final class Job {
        final boolean automatic;
        final int mode, match, slot, units, soulCost, phaseCost;
        final Identifier target, filter;
        final BlockPos sourceKey;
        final Direction sourceFace;
        final Object sourceAnchor;
        final String recipe;
        final ItemStack input, sample, catalyst, boxIdentity, output, remainder;
        Job(ConversionTableBlockEntity t, boolean automatic, Container source, int slot,
            ContainerLinks.Entry entry, ItemStack box, String recipe, int units, int soulCost,
            int phaseCost, ItemStack output, ItemStack remainder) {
            this.automatic=automatic;mode=t.inputMode;match=t.matchMode;target=t.target;filter=t.filterSource;
            this.slot=slot;this.units=units;this.soulCost=soulCost;this.phaseCost=phaseCost;this.recipe=recipe;
            sourceKey=entry==null?null:entry.key();sourceFace=entry==null?null:entry.face();
            sourceAnchor=entry==null?null:t.getLevel().getBlockEntity(entry.key());
            input=source.getItem(slot).copy();sample=t.getItem(0).copy();catalyst=t.getItem(3).copy();
            boxIdentity=box==null?ItemStack.EMPTY:box.copy();if(!boxIdentity.isEmpty())boxIdentity.remove(DataComponents.CONTAINER);
            this.output=output.copy();this.remainder=remainder.copy();
        }
        boolean controlsMatch(ConversionTableBlockEntity t) {
            return mode==t.inputMode&&match==t.matchMode&&target.equals(t.target)&&Objects.equals(filter,t.filterSource)
                &&(mode!=1||sameComponents(sample,t.getItem(0)))
                &&sameComponents(catalyst,t.getItem(3))&&t.getItem(3).getCount()>=catalyst.getCount();
        }
        boolean recipeMatches(Job other) {
            return recipe.equals(other.recipe)&&units==other.units&&soulCost==other.soulCost&&phaseCost==other.phaseCost
                &&ItemStack.matches(output,other.output)&&ItemStack.matches(remainder,other.remainder);
        }
    }
    private static boolean sameComponents(ItemStack first,ItemStack second) {
        return first.isEmpty()||second.isEmpty()?first.isEmpty()&&second.isEmpty():ItemStack.isSameItemSameComponents(first,second);
    }
    private static Preparation failure(int status) { return new Preparation(status,null,0,false); }
    private static Preparation failure(int status,int souls) { return new Preparation(status,null,souls,false); }
    private static Preparation invalid(int status,int souls) { return new Preparation(status,null,souls,true); }
    static Preparation prepare(ConversionTableBlockEntity table,boolean automatic) { return select(table,automatic,false); }
    /** Read the selected recipe's fee without choosing a random result or resolving linked inventories. */
    static int previewSoulCost(ConversionTableBlockEntity table) {
        if(table.variantIndex()!=2)return 0;
        Item target=BuiltInRegistries.ITEM.getValue(table.target);if(target==Items.AIR)return 0;
        var catalog=RecipeConfig.server();Item input=null;
        if(table.inputMode==0||table.inputMode==1&&table.matchMode==0) {
            if(!table.getItem(0).isEmpty())input=table.getItem(0).getItem();
        } else if(table.inputMode==2&&table.matchMode==0&&table.filterSource!=null) {
            input=BuiltInRegistries.ITEM.getValue(table.filterSource);
        }
        int cost=-1;
        for(var group:catalog.groups())if(group.items().contains(target)
            &&(input==null||input!=target&&group.items().contains(input))) {
            cost=RecipeConfig.setting("sculk","ordinary_souls_per_batch");
            if(input!=null)return cost; // Execution gives ordinary groups priority for a known input.
            break;
        }
        int matches=0;
        for(var advanced:catalog.advanced())if(advanced.matchesTarget(target)
            &&(input==null||advanced.input().is(input))&&(advanced.auto()||table.inputMode==0&&!table.running)) {
            if(input!=null&&++matches>1)return 0;
            if(cost!=-1&&cost!=advanced.deaths())return 0;
            cost=advanced.deaths();
        }
        return Math.max(0,cost);
    }
    /** Low-level immediate transaction retained for transaction verification; production uses timed jobs. */
    static int execute(ConversionTableBlockEntity table,boolean automatic) {
        return select(table,automatic,true).status();
    }
    private static Preparation select(ConversionTableBlockEntity table,boolean automatic,boolean commit) {
        if(!RecipeConfig.enabled()||table.getLevel()==null||table.getLevel().isClientSide())return failure(9);
        if(table.inputMode==0)return attempt(table,table,0,true,automatic,null,null,null,commit,null);
        if(table.variantIndex()>0 && table.target.equals(BuiltInRegistries.ITEM.getKey(Items.AIR)))return failure(2);
        if(table.inputMode==1) {
            if(table.matchMode==0&&table.getItem(0).isEmpty())return failure(10);
            Preparation result=failure(3);
            for(var entry:table.inputLinks()) {
                Container source=entry.container();
                for(int slot=0;slot<source.getContainerSize();slot++) {
                    ItemStack stack=source.getItem(slot);
                    if(stack.isEmpty() || table.matchMode==0&&!stack.is(table.getItem(0).getItem()))continue;
                    if(!ContainerLinks.canExtract(entry,slot,stack,table))continue;
                    var candidate=attempt(table,source,slot,false,automatic,null,table.outputLinks(),entry,commit,null);
                    if(candidate.status()==1)return candidate;if(candidate.status()!=3)result=candidate;
                }
            }
            return result;
        }
        ItemStack box=table.getItem(0);
        var contents=box.get(DataComponents.CONTAINER);
        if(contents==null||box.getCount()!=1)return failure(2);
        // Only real container items; don't manufacture a container component on arbitrary items.
        if(!(box.getItem() instanceof BlockItem blockItem)
            ||!(blockItem.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock))return failure(11);
        var inventory=new SimpleContainer(27);
        var saved=contents.itemCopies().toList();
        if(saved.size()>27)return failure(11);
        for(int i=0;i<saved.size();i++)inventory.setItem(i,saved.get(i));
        Preparation result=failure(3);
        for(int i=0;i<inventory.getContainerSize();i++) {
            ItemStack stack=inventory.getItem(i);
            if(stack.isEmpty()||table.matchMode==0&&table.filterSource!=null
                &&!BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(table.filterSource))continue;
            var candidate=attempt(table,inventory,i,false,automatic,box,null,null,commit,null);
            if(candidate.status()==1)return candidate;if(candidate.status()!=3)result=candidate;
        }
        return result;
    }
    /** Locate the same source afresh; a new earlier stack cannot inherit another stack's timer. */
    static Preparation validate(ConversionTableBlockEntity t,Job job,boolean commit) {
        if(!RecipeConfig.enabled()||t.getLevel()==null||t.getLevel().isClientSide())return failure(9,job.soulCost);
        if(!job.controlsMatch(t))return invalid(t.getItem(3).isEmpty()&&!job.catalyst.isEmpty()?7:2,job.soulCost);
        Container source=t;ItemStack box=null;ContainerLinks.Entry entry=null;
        if(job.mode==1) {
            for(var candidate:t.inputLinks())if(candidate.key().equals(job.sourceKey)&&candidate.face()==job.sourceFace
                &&t.getLevel().getBlockEntity(candidate.key())==job.sourceAnchor){entry=candidate;break;}
            if(entry==null)return invalid(2,job.soulCost);
            source=entry.container();
            if(job.slot>=source.getContainerSize()||!ContainerLinks.canExtract(entry,job.slot,source.getItem(job.slot),t))return invalid(2,job.soulCost);
        } else if(job.mode==2) {
            box=t.getItem(0);var identity=box.copy();if(!identity.isEmpty())identity.remove(DataComponents.CONTAINER);
            var contents=box.get(DataComponents.CONTAINER);
            if(box.getCount()!=1||contents==null||!sameComponents(job.boxIdentity,identity))return invalid(2,job.soulCost);
            var saved=contents.itemCopies().toList();if(saved.size()>27)return invalid(11,job.soulCost);
            source=new SimpleContainer(27);for(int i=0;i<saved.size();i++)source.setItem(i,saved.get(i));
        }
        ItemStack input=source.getItem(job.slot);
        if(!sameComponents(job.input,input)||input.getCount()<job.input.getCount())return invalid(2,job.soulCost);
        var result=attempt(t,source,job.slot,job.mode==0,job.automatic,box,
            job.mode==1?t.outputLinks():null,entry,commit,job);
        return result;
    }
    private static Preparation attempt(ConversionTableBlockEntity t,Container source,int slot,boolean device,boolean automatic,
        ItemStack box,List<ContainerLinks.Entry> linked,ContainerLinks.Entry entry,boolean commit,Job expected) {
        ItemStack input=source.getItem(slot);
        if(input.isEmpty())return failure(2);
        if(!device&&!source.canTakeItem(t,slot,input))return failure(3);
        var catalog=RecipeConfig.server();int variant=t.variantIndex();
        Item target=BuiltInRegistries.ITEM.getValue(t.target);
        if(variant>0&&target==Items.AIR)return failure(3);
        RecipeCatalog.Group group=null;
        for(var candidate:catalog.groups())if(candidate.tier()<=variant&&candidate.items().contains(input.getItem())
            &&(variant==0||!input.is(target)&&candidate.items().contains(target))) {group=candidate;break;}
        RecipeCatalog.Advanced advanced=null;
        if(group==null&&variant==2) {
            for(var candidate:catalog.advanced())if(input.is(candidate.input().getItem())&&candidate.matchesTarget(target)) {
                if(advanced!=null)return failure(3); // An ambiguous custom config cannot silently choose a recipe.
                advanced=candidate;
            }
        }
        if(group==null&&advanced==null)return expected==null?failure(3):invalid(3,expected.soulCost);
        int soulCost=variant==2?(advanced==null?RecipeConfig.setting("sculk","ordinary_souls_per_batch"):advanced.deaths()):0;
        if(expected!=null&&(advanced==null?(expected.units>group.batch()||expected.output.is(input.getItem())||!group.items().contains(expected.output.getItem())):
            expected.units!=advanced.input().getCount()||!advanced.matchesTarget(expected.output.getItem())))return invalid(3,soulCost);
        if(advanced!=null) {
            if(!advanced.auto()&&(automatic||t.inputMode!=0))return failure(8,soulCost);
            if(input.getCount()<advanced.input().getCount())return failure(2,soulCost);
            if(!advanced.catalyst().isEmpty()&&(!t.getItem(3).is(advanced.catalyst().getItem())
                ||t.getItem(3).getCount()<advanced.catalyst().getCount()))return failure(7,soulCost);
            // Advanced transformations must never consume stored items or block-entity payloads.
            if(hasPayload(input)||hasPayload(t.getItem(3)))return failure(11,soulCost);
        }
        if(t.deaths<soulCost)return failure(5,soulCost);
        if(variant==0) {
            var key=BuiltInRegistries.ITEM.getKey(input.getItem());
            if(expected!=null)target=expected.output.getItem();
            else if(t.pendingInput==null||!t.pendingInput.equals(key)||t.pendingOutput==null||t.pendingOutput.equals(key)
                ||!group.items().contains(BuiltInRegistries.ITEM.getValue(t.pendingOutput))) {
                var options=group.items().stream().filter(i->i!=input.getItem()).toList();
                target=options.get(t.getLevel().getRandom().nextInt(options.size()));
                t.pendingInput=key;t.pendingOutput=BuiltInRegistries.ITEM.getKey(target);t.setChanged();
            } else target=BuiltInRegistries.ITEM.getValue(t.pendingOutput);
        }
        int used=expected!=null?expected.units:advanced==null?Math.min(input.getCount(),group.batch()):advanced.input().getCount();
        if (advanced!=null && advanced.random()) {
            var key=BuiltInRegistries.ITEM.getKey(input.getItem());
            if(expected!=null)target=expected.output.getItem();
            else if (!key.equals(t.pendingInput) || t.pendingOutput==null || !advanced.matchesTarget(BuiltInRegistries.ITEM.getValue(t.pendingOutput))) {
                target=advanced.outputs().get(t.getLevel().getRandom().nextInt(advanced.outputs().size())).getItem();
                t.pendingInput=key; t.pendingOutput=BuiltInRegistries.ITEM.getKey(target); t.setChanged();
            } else target=BuiltInRegistries.ITEM.getValue(t.pendingOutput);
        }
        ItemStack output=advanced==null?input.transmuteCopy(target,used):advanced.output().copy();
        if (advanced!=null && advanced.random()) output = new ItemStack(target, advanced.output().getCount());
        if(advanced!=null)output.copyFrom(DataComponents.CUSTOM_NAME,input);
        int phaseCost=variant==1?RecipeConfig.setting("end","charge_per_batch"):0;
        int fuelCount=variant==2?0:variant==0?RecipeConfig.setting("piglin","cost_n"):
            Math.max(0,(phaseCost-t.phase+RecipeConfig.setting("end","fuel_charge")-1)/RecipeConfig.setting("end","fuel_charge"));
        if(fuelCount>0&&(!t.getItem(1).is(RecipeConfig.fuelItem(variant))||t.getItem(1).getCount()<fuelCount))return failure(4,soulCost);
        ItemStack remainder=advanced==null||advanced.returns().isEmpty()?ItemStack.EMPTY:advanced.returns().getFirst();
        String recipe=advanced==null?group.id():advanced.id();
        var identity=new Job(t,automatic,source,slot,entry,box,recipe,used,soulCost,phaseCost,output,remainder);
        if(expected!=null&&!expected.recipeMatches(identity))return invalid(3,soulCost);
        if(!fits(t.getItem(4),remainder,t.getMaxStackSize()))return new Preparation(6,identity,soulCost,false);
        List<ItemStack> changed=null;
        ContainerLinks.OutputPlan linkedPlan = null;
        // Explicit outputs can export slot-mode batches as well as linked inputs.
        if (device && !t.outputLinks().isEmpty()) linked = t.outputLinks();
        if (linked != null) {
            if (advanced==null) {
                int room=ContainerLinks.room(linked,output);
                if(expected!=null&&room<used)return new Preparation(6,identity,soulCost,false);
                used=Math.min(used,room);
                if (used==0) return new Preparation(6,identity,soulCost,false); output.setCount(used);
            }
            linkedPlan=ContainerLinks.plan(linked,output);
            if(linkedPlan==null)return new Preparation(6,identity,soulCost,false);
        } else if(device) {
            if(advanced==null) {
                int room=room(t.getItem(2),output,t.getMaxStackSize());
                if(expected!=null&&room<used)return new Preparation(6,identity,soulCost,false);
                used=Math.min(used,room);
                if(used==0)return new Preparation(6,identity,soulCost,false);output.setCount(used);
            } else if(!fits(t.getItem(2),output,t.getMaxStackSize()))return new Preparation(6,identity,soulCost,false);
        } else {
            // Work on a private snapshot; replace/merge in the same connected container.
            // Try smaller ordinary batches when only part of the output fits.
            changed=plan(source,slot,used,output,box!=null);
            if(changed==null&&advanced==null&&expected==null) {
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
            if(changed==null)return new Preparation(6,identity,soulCost,false);
        }
        identity=new Job(t,automatic,source,slot,entry,box,recipe,used,soulCost,phaseCost,output,remainder);
        if(!commit)return new Preparation(1,identity,soulCost,false);
        // Commit: server ticks and menu packets cannot interleave on this thread.
        if (linkedPlan != null) {
            source.setItem(slot,input.copyWithCount(input.getCount()-used)); source.setChanged(); linkedPlan.commit();
        } else if(device) {
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
        if(variant==1)t.phase+=fuelCount*RecipeConfig.setting("end","fuel_charge")-phaseCost;
        if(variant==2)t.phase=0;
        t.deaths-=soulCost;
        if(advanced!=null) {
            if(!advanced.catalyst().isEmpty())t.removeItem(3,advanced.catalyst().getCount());
            if(!remainder.isEmpty())t.setItem(4,merge(t.getItem(4),remainder));
        }
        t.pendingInput=null;t.pendingOutput=null;t.setChanged();return new Preparation(1,identity,soulCost,false);
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
