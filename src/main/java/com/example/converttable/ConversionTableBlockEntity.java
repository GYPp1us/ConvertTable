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
    private NonNullList<ItemStack> items = NonNullList.withSize(5, ItemStack.EMPTY);
    int inputMode, matchMode, phase, deaths, status;
    boolean running;
    Identifier pendingInput, pendingOutput, filterSource;
    public boolean convert(boolean automatic) {
        status=ConversionEngine.execute(this,automatic);return status==1;
    }
    public static void tick(net.minecraft.world.level.Level level,BlockPos pos,BlockState state,ConversionTableBlockEntity table) {
        if(table.running && level.getGameTime()%10==0)table.convert(true);
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
            snapshot = TableRangeScanner.scan(level, worldPosition, variantIndex() == 2);
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
        return variantIndex()==2?new int[]{1,3}:new int[]{1};
    }
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,net.minecraft.core.Direction side) {
        return canPlaceItem(slot,stack) && java.util.Arrays.stream(getSlotsForFace(side)).anyMatch(i->i==slot)
            && (slot!=3 || !stack.is(RecipeConfig.fuelItem(variantIndex())));
    }
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,net.minecraft.core.Direction side) {
        return side==net.minecraft.core.Direction.DOWN&&(slot==2||slot==4);
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == 2 || slot == 4) return false;
        if(slot==3)return variantIndex()==2;
        return slot != 1 || stack.is(RecipeConfig.fuelItem(variantIndex()));
    }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Phase",phase);output.putInt("Deaths",deaths);output.putBoolean("Running",running);
        if(filterSource!=null)output.putString("FilterSource",filterSource.toString());
        if(pendingInput!=null)output.putString("PendingInput",pendingInput.toString());
        if(pendingOutput!=null)output.putString("PendingOutput",pendingOutput.toString());
        output.putInt("InputMode", inputMode); output.putInt("MatchMode", matchMode);
        output.putString("PreviewTarget", target.toString());
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(5, ItemStack.EMPTY); ContainerHelper.loadAllItems(input, items);
        phase=Math.clamp(input.getIntOr("Phase",0),0,8192);deaths=Math.clamp(input.getIntOr("Deaths",0),0,4096);
        running=input.getBooleanOr("Running",false);
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
