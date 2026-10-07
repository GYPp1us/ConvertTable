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

/** A reusable catalyst and physical original; its crystal network owns the production budget. */
public final class CatalystPedestalBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public final ContainerLinks links = new ContainerLinks();
    public List<ContainerLinks.Entry> outputContainers() { return links.resolve(level, worldPosition, false); }
    public static final int OUTPUT_FIRST = 1, OUTPUT_LAST = 9;
    // Append the original slot so existing worlds keep all nine output slots unchanged.
    public static final int SOURCE_SLOT = 10, INVENTORY_SIZE = 11;
    // 0 idle, 1 growing, 2 catalyst missing, 3 crystal missing, 4 network changed,
    // 5 full, 6 no buds, 7 invalid catalyst, 8 no target, 9 waiting for a share, 10 no matching original.
    private NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private ItemStack catalystState = ItemStack.EMPTY;
    private ItemStack sourceState = ItemStack.EMPTY;
    private boolean running;
    private Identifier selectedId;
    private int credit, lastRate, status;
    private long lastAvailable, lastSpent, cycleAllocation;
    // 64 microfactors/factor and twenty ticks/second retain even the smallest supply.
    private int fraction, cycleElapsed;
    private long cycleStart = Long.MIN_VALUE;
    private CrystalTableBlockEntity cycleOwner;
    private GrowthNetwork.Snapshot cycleNetwork;
    private long producedTotal;
    private final TableAdvancements.Credit advancementCredit = new TableAdvancements.Credit();

    public CatalystPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(GrowthBlocks.CATALYST_ENTITY, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CatalystPedestalBlockEntity table) {
        if (level.isClientSide()) return;
        if (level.getGameTime() % 20 == 0 && table.advancementCredit.deliverPending(level, ConvertTable.id("crystal")))
            table.setChanged();
        if (!ItemStack.matches(table.catalystState, table.getItem(0))) table.catalystChanged();
        if (!ItemStack.matches(table.sourceState, table.getItem(SOURCE_SLOT))) table.sourceChanged();
        table.advanceGrowth(level.getGameTime());
        if (level.getGameTime() % 5 == 0) GrowthOutputLinks.flush(level, pos, table);
        if (level.getGameTime() % 20 == 0 && table.crystal() == null) {
            table.lastRate = 0;
            table.lastSpent = table.lastAvailable = 0;
            table.stopCycle();
            table.status = !table.running ? 0 : table.getItem(0).isEmpty() ? 2
                : !GrowthRecipes.isCatalyst(table.getItem(0)) ? 7
                : table.selectedRecipe() == null ? 8 : !table.sourceReady() ? 10 : 3;
        }
    }

    public boolean running() { return running; }
    public int status() { return selectedRecipe()!=null && !sourceReady() ? 10 : status; }
    public int lastRate() { return lastRate; }
    public long lastAvailable() { return lastAvailable; }
    public long lastSpent() { return lastSpent; }
    public int credit() { return credit; }
    public long progressUnits() { return (long) credit * GrowthUnits.TICK_UNITS + fraction; }
    public long progressMaximum() { return (long) cost() * GrowthUnits.TICK_UNITS; }
    public long progressRate() { return running && status == 1 ? lastSpent : 0; }
    public boolean processing() { return running && status == 1 && cycleAllocation > 0 && cycleElapsed < 20; }
    public int cost() { var recipe = selectedRecipe(); return recipe == null ? 0 : recipe.cost(); }
    public long producedTotal() { return producedTotal; }
    public List<GrowthRecipes.Recipe> recipes() {
        return level != null && level.isClientSide() ? GrowthRecipes.displayRecipes(getItem(0)) : GrowthRecipes.recipes(getItem(0));
    }
    public boolean sourceReady() { return GrowthRecipes.matchesSource(selectedRecipe(), getItem(SOURCE_SLOT)); }
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
        credit = fraction = lastRate = 0;
        lastSpent = 0;
        stopCycle();
        status = sourceReady() ? 0 : 10;
        syncDisplay();
        return true;
    }

    public void toggleRunning() {
        if (!running && !sourceReady()) { status = 10; syncDisplay(); return; }
        running = !running;
        if (!running) { status = 0; lastRate = 0; lastSpent = 0; stopCycle(); }
        setChanged();
    }
    public void toggleRunning(net.minecraft.world.entity.player.Player player) {
        boolean wasRunning = running;
        toggleRunning();
        if (!wasRunning && running && level != null && !level.isClientSide()) {
            advancementCredit.startedBy(player);
            setChanged();
        }
    }
    java.util.UUID advancementOperator() { return advancementCredit.operator(); }

    public CrystalTableBlockEntity crystal() { return GrowthNetwork.findSource(level, worldPosition); }

    boolean prepareCycle(CrystalTableBlockEntity owner, GrowthNetwork.Snapshot network) {
        // Settle the previous second before installing a new budget, independent of BE tick order.
        advanceGrowth(level.getGameTime());
        stopCycle();
        lastRate = 0;
        lastSpent = 0;
        lastAvailable = network.available();
        if (!running) { status = 0; return false; }
        if (getItem(0).isEmpty()) { status = 2; return false; }
        if (!GrowthRecipes.isCatalyst(getItem(0))) { status = 7; return false; }
        if (selectedRecipe() == null) { status = 8; return false; }
        if (!sourceReady()) { status = 10; return false; }
        if (!network.usable() || crystal() != owner) { status = 4; return false; }
        if (outputRoom(selectedOutput()) == 0) { status = 5; return false; }
        status = network.available() == 0 ? 6 : 1;
        cycleOwner = owner;
        cycleNetwork = network;
        return true;
    }

    long demand() { return sourceReady() ? remainingDemand(outputRoom(selectedOutput()), cost()) : 0; }

    private long remainingDemand(int room, int cost) {
        long units = (long) room * cost * GrowthUnits.TICK_UNITS - progressUnits();
        return Math.max(0L, (units + 19) / 20);
    }

    long acceptGrowth(long allocation, long available) {
        lastAvailable = available;
        var recipe = selectedRecipe();
        if (recipe == null || !running || !sourceReady()) return 0;
        ItemStack output = new ItemStack(recipe.output());
        int room = outputRoom(output);
        long spent = Math.min(Math.max(0L, allocation), remainingDemand(room, recipe.cost()));
        cycleAllocation = spent;
        cycleElapsed = 0;
        cycleStart = level.getGameTime();
        lastSpent = spent;
        status = available == 0 ? 6 : spent > 0 ? 1 : 9;
        if (spent > 0) setChanged();
        return spent;
    }

    /** Earn an allocated second's factors over its twenty real server ticks. */
    void advanceGrowth(long now) {
        if (cycleAllocation <= 0 || cycleElapsed >= 20) return;
        var recipe = selectedRecipe();
        if (!running || recipe == null || !sourceReady()) { status = 10; stopCycle(); return; }
        if (cycleOwner == null || crystal() != cycleOwner || cycleOwner.snapshot() != cycleNetwork || !cycleNetwork.usable()) {
            status = crystal() == null ? 3 : 4;
            stopCycle();
            return;
        }
        ItemStack output = new ItemStack(recipe.output());
        int room = outputRoom(output);
        if (room == 0) { status = 5; stopCycle(); return; }
        int elapsed = (int) Math.clamp(now - cycleStart, 0L, 20L);
        int delta = elapsed - cycleElapsed;
        if (delta <= 0) return;
        long units = progressUnits() + cycleAllocation * delta;
        long costUnits = (long) recipe.cost() * GrowthUnits.TICK_UNITS;
        int copies = (int) Math.min(units / costUnits, room);
        // Check the physical network again at the actual output transaction, never copy after disconnection.
        if (copies > 0 && (cycleOwner == null || crystal() != cycleOwner || !cycleOwner.snapshot().usable())) {
            status = crystal() == null ? 3 : 4;
            stopCycle();
            return;
        }
        cycleElapsed = elapsed;
        units -= (long) copies * costUnits;
        credit = (int) (units / GrowthUnits.TICK_UNITS);
        fraction = (int) (units % GrowthUnits.TICK_UNITS);
        if (copies > 0) {
            insertOutput(output, copies);
            producedTotal += copies;
            lastRate += copies;
            advancementCredit.completed(level, ConvertTable.id("crystal"));
        }
        setChanged();
    }

    private void stopCycle() {
        cycleAllocation = cycleElapsed = 0;
        cycleStart = Long.MIN_VALUE;
        cycleOwner = null;
        cycleNetwork = null;
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
        credit = fraction = lastRate = 0;
        lastSpent = 0;
        stopCycle();
        status = 0;
        List<GrowthRecipes.Recipe> choices = recipes();
        selectedId = choices.size() == 1 ? choices.getFirst().id() : null;
        selectSourceTarget();
        syncDisplay();
    }
    private void selectSourceTarget() {
        for (var recipe : recipes()) if (GrowthRecipes.matchesSource(recipe, getItem(SOURCE_SLOT))) {
            selectedId = recipe.id();
            return;
        }
    }
    private void sourceChanged() {
        sourceState = getItem(SOURCE_SLOT).copy();
        running = false;
        credit = fraction = lastRate = 0;
        lastSpent = 0;
        stopCycle();
        selectSourceTarget();
        status = sourceReady() ? 0 : 10;
        syncDisplay();
    }
    private void syncDisplay() {
        setChanged();
        if (level != null && !level.isClientSide())
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override public int getContainerSize() { return INVENTORY_SIZE; }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> value) { items = value; }
    @Override protected Component getDefaultName() {
        return Component.translatable("block.convert_table.catalyst_pedestal");
    }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new CatalystPedestalMenu(id, inventory, this);
    }
    @Override public void setItem(int slot, ItemStack stack) {
        super.setItem(slot, (slot == 0 || slot == SOURCE_SLOT) && !stack.isEmpty() ? stack.copyWithCount(1) : stack);
        if (slot == 0 && !ItemStack.matches(catalystState, getItem(0))) catalystChanged();
        if (slot == SOURCE_SLOT && !ItemStack.matches(sourceState, getItem(SOURCE_SLOT))) sourceChanged();
    }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack result = super.removeItem(slot, amount);
        if (slot == 0 && !result.isEmpty()) catalystChanged();
        if (slot == SOURCE_SLOT && !result.isEmpty()) sourceChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = super.removeItemNoUpdate(slot);
        if (slot == 0 && !result.isEmpty()) catalystChanged();
        if (slot == SOURCE_SLOT && !result.isEmpty()) sourceChanged();
        return result;
    }
    @Override public void clearContent() {
        super.clearContent();
        catalystChanged();
        sourceChanged();
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0 && GrowthRecipes.isCatalyst(stack) || slot == SOURCE_SLOT && GrowthRecipes.isSource(stack);
    }
    @Override public int[] getSlotsForFace(Direction side) {
        return side == Direction.UP ? new int[]{0,SOURCE_SLOT} : new int[]{1,2,3,4,5,6,7,8,9};
    }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side == Direction.UP && (slot == 0 || slot == SOURCE_SLOT) && getItem(slot).isEmpty() && canPlaceItem(slot, stack);
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side != Direction.UP && slot >= OUTPUT_FIRST && slot <= OUTPUT_LAST;
    }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        links.save(output);
        advancementCredit.save(output);
        output.putBoolean("Running", running);
        output.putInt("Credit", credit);
        output.putInt("GrowthFraction", fraction);
        output.putInt("GrowthProgressScale", GrowthUnits.DIVISOR);
        output.putLong("ProducedTotal", producedTotal);
        if (selectedId != null) output.putString("SelectedRecipe", selectedId.toString());
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        links.load(input);
        advancementCredit.load(input);
        catalystState = getItem(0).copy();
        sourceState = getItem(SOURCE_SLOT).copy();
        selectedId = Identifier.tryParse(input.getStringOr("SelectedRecipe", ""));
        var recipe = selectedRecipe();
        running = input.getBooleanOr("Running", false) && recipe != null && sourceReady();
        credit = recipe == null ? 0 : Math.clamp(input.getIntOr("Credit", 0), 0, recipe.cost() - 1);
        int savedFraction = Math.clamp(input.getIntOr("GrowthFraction", 0), 0, GrowthUnits.TICK_UNITS - 1);
        fraction = recipe == null ? 0 : input.getIntOr("GrowthProgressScale", 1) == 1
            ? Math.min(GrowthUnits.TICK_UNITS - 1, savedFraction * GrowthUnits.DIVISOR) : savedFraction;
        stopCycle(); // Future factors have not been earned yet and must be allocated again after reload.
        producedTotal = Math.max(0L, input.getLongOr("ProducedTotal", 0L));
    }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return saveWithoutMetadata(provider);
    }
}
