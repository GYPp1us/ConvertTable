package com.example.converttable;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A reusable catalyst and selected output; its source owns the shared production budget. */
public final class CatalystPedestalBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int OUTPUT_FIRST = 1, OUTPUT_LAST = 9;
    // 0 idle, 1 growing, 2 catalyst missing, 3 crystal missing, 4 network conflict,
    // 5 output full, 6 buds missing, 7 unsupported catalyst, 8 target missing.
    private NonNullList<ItemStack> items = NonNullList.withSize(10, ItemStack.EMPTY);
    private ItemStack catalystState = ItemStack.EMPTY;
    private boolean running;
    private Identifier selectedId;
    private int credit, lastRate, lastAvailable, lastSpent, status;
    private long producedTotal;

    public CatalystPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(GrowthBlocks.CATALYST_ENTITY, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CatalystPedestalBlockEntity table) {
        if (level.isClientSide()) return;
        if (!ItemStack.matches(table.catalystState, table.getItem(0))) table.catalystChanged();
        if (level.getGameTime() % 5 == 0) GrowthOutputLinks.flush(level, pos, table);
        if (level.getGameTime() % 20 == 0 && table.crystal() == null) {
            table.lastRate = table.lastSpent = table.lastAvailable = 0;
            table.status = !table.running ? 0 : table.getItem(0).isEmpty() ? 2
                : !GrowthRecipes.isCatalyst(table.getItem(0)) ? 7
                : table.selectedRecipe() == null ? 8 : 3;
        }
    }

    public boolean running() { return running; }
    public int status() { return status; }
    public int lastRate() { return lastRate; }
    public int lastAvailable() { return lastAvailable; }
    public int lastSpent() { return lastSpent; }
    public int credit() { return credit; }
    public int cost() { var recipe = selectedRecipe(); return recipe == null ? 0 : recipe.cost(); }
    public long producedTotal() { return producedTotal; }
    public List<GrowthRecipes.Recipe> recipes() { return GrowthRecipes.recipes(getItem(0)); }
    public GrowthRecipes.Recipe selectedRecipe() {
        if (selectedId == null) return null;
        for (var recipe : recipes()) if (recipe.id().equals(selectedId)) return recipe;
        return null;
    }
    public ItemStack selectedOutput() {
        var recipe = selectedRecipe();
        return recipe == null ? ItemStack.EMPTY : new ItemStack(recipe.output());
    }
    public GrowthOutputLinks.Snapshot outputLinks() {
        return GrowthOutputLinks.scan(level, worldPosition, selectedOutput());
    }

    public boolean selectRecipe(int index) {
        List<GrowthRecipes.Recipe> options = recipes();
        if (index < 0 || index >= options.size()) return false;
        Identifier target = options.get(index).id();
        if (target.equals(selectedId)) return true;
        selectedId = target;
        running = false;
        credit = lastRate = lastSpent = 0;
        status = 0;
        syncDisplay();
        return true;
    }

    public void toggleRunning() {
        running = !running;
        if (!running) { status = 0; lastRate = lastSpent = 0; }
        setChanged();
    }

    public CrystalTableBlockEntity crystal() { return GrowthNetwork.findSource(level, worldPosition); }

    boolean prepareCycle(CrystalTableBlockEntity owner, GrowthNetwork.Snapshot network) {
        lastRate = lastSpent = 0;
        lastAvailable = network.available();
        if (!running) { status = 0; return false; }
        if (getItem(0).isEmpty()) { status = 2; return false; }
        if (!GrowthRecipes.isCatalyst(getItem(0))) { status = 7; return false; }
        if (selectedRecipe() == null) { status = 8; return false; }
        if (!network.usable() || crystal() != owner) { status = 4; return false; }
        if (outputRoom(selectedOutput()) == 0) { status = 5; return false; }
        status = network.available() == 0 ? 6 : 1;
        return true;
    }

    int demand() { return Math.max(0, outputRoom(selectedOutput()) * cost() - credit); }

    int acceptGrowth(int allocation, int available) {
        lastAvailable = available;
        var recipe = selectedRecipe();
        if (recipe == null || !running) return 0;
        ItemStack output = new ItemStack(recipe.output());
        int room = outputRoom(output);
        int spent = Math.min(Math.max(0, allocation), Math.max(0, room * recipe.cost() - credit));
        int total = credit + spent;
        int copies = Math.min(total / recipe.cost(), room);
        credit = total - copies * recipe.cost();
        if (copies > 0) {
            insertOutput(output, copies);
            producedTotal += copies;
        }
        lastRate = copies;
        lastSpent = spent;
        status = available == 0 ? 6 : 1;
        if (spent > 0) setChanged();
        return spent;
    }

    private int outputRoom(ItemStack sample) {
        if (sample.isEmpty()) return 0;
        int result = 0;
        for (int i = OUTPUT_FIRST; i <= OUTPUT_LAST; i++) {
            ItemStack current = getItem(i);
            if (current.isEmpty() || ItemStack.isSameItemSameComponents(current, sample))
                result += Math.max(0, Math.min(getMaxStackSize(), sample.getMaxStackSize()) - current.getCount());
        }
        return result;
    }

    private void insertOutput(ItemStack sample, int copies) {
        int remaining = copies;
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int i = OUTPUT_FIRST; i <= OUTPUT_LAST && remaining > 0; i++) {
                ItemStack current = getItem(i);
                if ((pass == 0 && current.isEmpty()) || (pass == 1 && !current.isEmpty())) continue;
                if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, sample)) continue;
                int move = Math.min(remaining,
                    Math.min(getMaxStackSize(), sample.getMaxStackSize()) - current.getCount());
                if (move <= 0) continue;
                setItem(i, current.isEmpty() ? sample.copyWithCount(move)
                    : current.copyWithCount(current.getCount() + move));
                remaining -= move;
            }
        }
        if (remaining != 0) throw new IllegalStateException("Output capacity changed during crystal transaction");
    }

    private void catalystChanged() {
        catalystState = getItem(0).copy();
        running = false;
        credit = lastRate = lastSpent = 0;
        status = 0;
        List<GrowthRecipes.Recipe> choices = recipes();
        selectedId = choices.size() == 1 ? choices.getFirst().id() : null;
        syncDisplay();
    }
    private void syncDisplay() {
        setChanged();
        if (level != null && !level.isClientSide())
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override public int getContainerSize() { return 10; }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> value) { items = value; }
    @Override protected Component getDefaultName() {
        return Component.translatable("block.convert_table.catalyst_pedestal");
    }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new CatalystPedestalMenu(id, inventory, this);
    }
    @Override public void setItem(int slot, ItemStack stack) {
        super.setItem(slot, slot == 0 && !stack.isEmpty() ? stack.copyWithCount(1) : stack);
        if (slot == 0 && !ItemStack.matches(catalystState, getItem(0))) catalystChanged();
    }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack result = super.removeItem(slot, amount);
        if (slot == 0 && !result.isEmpty()) catalystChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = super.removeItemNoUpdate(slot);
        if (slot == 0 && !result.isEmpty()) catalystChanged();
        return result;
    }
    @Override public void clearContent() {
        super.clearContent();
        catalystChanged();
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0 && GrowthRecipes.isCatalyst(stack);
    }
    @Override public int[] getSlotsForFace(Direction side) {
        return side == Direction.UP ? new int[]{0} : new int[]{1,2,3,4,5,6,7,8,9};
    }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side == Direction.UP && slot == 0 && getItem(0).isEmpty() && canPlaceItem(slot, stack);
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side != Direction.UP && slot >= OUTPUT_FIRST && slot <= OUTPUT_LAST;
    }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putBoolean("Running", running);
        output.putInt("Credit", credit);
        output.putLong("ProducedTotal", producedTotal);
        if (selectedId != null) output.putString("SelectedRecipe", selectedId.toString());
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(10, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        catalystState = getItem(0).copy();
        selectedId = Identifier.tryParse(input.getStringOr("SelectedRecipe", ""));
        var recipe = selectedRecipe();
        running = input.getBooleanOr("Running", false) && recipe != null;
        credit = recipe == null ? 0 : Math.clamp(input.getIntOr("Credit", 0), 0, recipe.cost() - 1);
        producedTotal = Math.max(0L, input.getLongOr("ProducedTotal", 0L));
    }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return saveWithoutMetadata(provider);
    }
}
