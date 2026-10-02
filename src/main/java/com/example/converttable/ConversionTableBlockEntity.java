package com.example.converttable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class ConversionTableBlockEntity extends BaseContainerBlockEntity implements net.minecraft.world.WorldlyContainer {
    public final ContainerLinks links = new ContainerLinks();
    public java.util.List<ContainerLinks.Entry> inputLinks() { return links.resolve(level, worldPosition, true); }
    public java.util.List<ContainerLinks.Entry> outputLinks() { return links.resolve(level, worldPosition, false); }
    private NonNullList<ItemStack> items = NonNullList.withSize(5, ItemStack.EMPTY);
    int inputMode, matchMode, phase, deaths, status;
    boolean running;
    private boolean manualRequested;
    private ConversionEngine.Job job;
    private int progressTicks, soulCost;
    Identifier pendingInput, pendingOutput, filterSource;
    /** Request one cycle; clicking again cannot complete or accelerate an existing job. */
    public boolean convert(boolean automatic) {
        if(level==null||level.isClientSide())return false;
        if(job!=null)return true;
        if(!automatic) {manualRequested=true;setChanged();}
        return begin(automatic||running);
    }
    public int progressTicks() { return progressTicks; }
    public int totalTicks() { return variantIndex()==0?80:variantIndex()==1?40:20; }
    public boolean processing() { return job!=null; }
    public int soulCost() { return job!=null?soulCost:ConversionEngine.previewSoulCost(this); }
    public void cancelProcessing() {
        job=null;manualRequested=false;progressTicks=0;soulCost=0;status=0;
        pendingInput=null;pendingOutput=null;setChanged();
    }
    public void setRunning(boolean value) {
        if(running==value)return;
        running=value;
        cancelProcessing();
    }
    private boolean begin(boolean automatic) {
        var prepared=ConversionEngine.prepare(this,automatic);
        status=prepared.status();soulCost=prepared.soulCost();
        if((status==1||status==6)&&prepared.job()!=null) {
            job=prepared.job();progressTicks=0;
            if(status==1)status=12;
            setChanged();return true;
        }
        return false;
    }
    private void advance() {
        if(job!=null&&job.automatic&&!running) {cancelProcessing();return;}
        if(job==null) {
            if(!running&&!manualRequested)return;
            if(!begin(running))return;
        }
        var prepared=ConversionEngine.validate(this,job,false);
        soulCost=prepared.soulCost();
        if(prepared.invalidated()) {
            cancelProcessing();status=prepared.status();return;
        }
        if(prepared.status()!=1&&prepared.status()!=6) {
            // Missing resources reset elapsed work. A manual request may wait until replenished.
            job=null;progressTicks=0;status=prepared.status();setChanged();return;
        }
        progressTicks=Math.min(totalTicks(),progressTicks+1);
        status=prepared.status()==6?6:12;
        if(progressTicks==totalTicks()&&prepared.status()==1) {
            // No snapshot from a previous tick is committed. Re-resolve inventories immediately.
            var completed=ConversionEngine.validate(this,job,true);
            status=completed.status();soulCost=completed.soulCost();
            if(status==1) {job=null;manualRequested=false;progressTicks=0;}
            else if(completed.invalidated()) {cancelProcessing();status=completed.status();}
        }
        setChanged();
    }
    public static void tick(net.minecraft.world.level.Level level,BlockPos pos,BlockState state,ConversionTableBlockEntity table) {
        if(level.isClientSide())return;
        if (!level.isClientSide() && table.variantIndex() == 2 && !table.getItem(1).isEmpty()) {
            // Return fuel left in worlds upgraded from the phase-powered version.
            var fuel = table.removeItemNoUpdate(1);
            net.minecraft.world.Containers.dropItemStack(level, pos.getX()+.5, pos.getY()+1, pos.getZ()+.5, fuel);
            table.phase = 0; table.setChanged();
        }
        table.advance();
    }
    Identifier target = BuiltInRegistries.ITEM.getKey(Items.AIR);
    private long lastScan = Long.MIN_VALUE;
    private TableRangeScanner.Snapshot snapshot = TableRangeScanner.Snapshot.empty();
    public ConversionTableBlockEntity(BlockPos pos, BlockState state) { super(ConversionTables.BLOCK_ENTITY, pos, state); }
    public String variant() { return variantIndex() == 2 ? "sculk" : variantIndex() == 1 ? "end" : "black_gold"; }
    public int variantIndex() {
        if (getBlockState().is(ConversionTables.SCULK)) return 2;
        return getBlockState().is(ConversionTables.END) ? 1 : 0;
    }
    public TableRangeScanner.Snapshot rangeSnapshot() {
        if (level != null && !level.isClientSide() && variantIndex() > 0
            && (lastScan == Long.MIN_VALUE || level.getGameTime() - lastScan >= 20)) {
            var scanned = TableRangeScanner.scan(level, worldPosition, variantIndex() == 2);
            snapshot = new TableRangeScanner.Snapshot(inputLinks().size()+outputLinks().size(), scanned.nodes(),
                scanned.ground(), scanned.flags(), scanned.pixels(), scanned.surfaces());
            lastScan = level.getGameTime();
        }
        return snapshot;
    }
    @Override public int getContainerSize() { return 5; }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> value) { items = value; }
    @Override protected Component getDefaultName() { return Component.translatable("block.convert_table." + variant() + "_conversion_table"); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return new ConversionTableMenu(id, inventory, this); }
    @Override public int[] getSlotsForFace(net.minecraft.core.Direction side) {
        if(side==net.minecraft.core.Direction.UP)return new int[]{0};
        if(side==net.minecraft.core.Direction.DOWN)return variantIndex()==2?new int[]{2,4}:new int[]{2};
        return variantIndex()==2?new int[]{3}:new int[]{1};
    }
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,net.minecraft.core.Direction side) {
        return canPlaceItem(slot,stack) && java.util.Arrays.stream(getSlotsForFace(side)).anyMatch(i->i==slot);
    }
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,net.minecraft.core.Direction side) {
        return side==net.minecraft.core.Direction.DOWN&&(slot==2||slot==4);
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == 2 || slot == 4) return false;
        if(slot==3)return variantIndex()==2;
        return slot != 1 || variantIndex()!=2 && stack.is(RecipeConfig.fuelItem(variantIndex()));
    }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        links.save(output);
        output.putInt("Phase",phase);output.putInt("Deaths",deaths);output.putBoolean("Running",running);
        // Incomplete manual work restarts safely after load; its ingredients were never reserved/spent.
        output.putBoolean("ManualRequested",manualRequested);
        if(filterSource!=null)output.putString("FilterSource",filterSource.toString());
        if(pendingInput!=null)output.putString("PendingInput",pendingInput.toString());
        if(pendingOutput!=null)output.putString("PendingOutput",pendingOutput.toString());
        output.putInt("InputMode", inputMode); output.putInt("MatchMode", matchMode);
        output.putString("PreviewTarget", target.toString());
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(5, ItemStack.EMPTY); ContainerHelper.loadAllItems(input, items);
        links.load(input);
        phase=Math.clamp(input.getIntOr("Phase",0),0,8192);deaths=Math.clamp(input.getIntOr("Deaths",0),0,4096);
        running=input.getBooleanOr("Running",false);
        manualRequested=input.getBooleanOr("ManualRequested",false);job=null;progressTicks=0;soulCost=0;status=0;
        filterSource=Identifier.tryParse(input.getStringOr("FilterSource",""));
        pendingInput=Identifier.tryParse(input.getStringOr("PendingInput",""));
        pendingOutput=Identifier.tryParse(input.getStringOr("PendingOutput",""));
        inputMode = Math.clamp(input.getIntOr("InputMode", 0), 0, 2);
        matchMode = Math.clamp(input.getIntOr("MatchMode", 0), 0, 1);
        Identifier value = Identifier.tryParse(input.getStringOr("PreviewTarget", "minecraft:air"));
        target = value != null && BuiltInRegistries.ITEM.containsKey(value) ? value : BuiltInRegistries.ITEM.getKey(Items.AIR);
        lastScan = Long.MIN_VALUE;
    }
}
