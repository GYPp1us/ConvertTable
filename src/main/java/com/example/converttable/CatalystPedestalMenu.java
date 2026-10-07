package com.example.converttable;

import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class CatalystPedestalMenu extends AbstractContainerMenu {
    private static final int DEVICE_SLOTS = CatalystPedestalBlockEntity.INVENTORY_SIZE, DATA_SIZE = 27 * GrowthData.SIZE;
    private final Container storage;
    private final CatalystPedestalBlockEntity table;
    private final ContainerData data = new SimpleContainerData(DATA_SIZE);

    public CatalystPedestalMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(DEVICE_SLOTS), null);
    }

    public CatalystPedestalMenu(int id, Inventory inventory, CatalystPedestalBlockEntity table) {
        this(id, inventory, table, table);
    }

    private CatalystPedestalMenu(int id, Inventory inventory, Container storage, CatalystPedestalBlockEntity table) {
        super(GrowthMenus.CATALYST, id);
        this.storage = storage;
        this.table = table;
        addSlot(new Slot(storage, 0, 8, 29) {
            @Override public boolean mayPlace(ItemStack stack) {
                return table == null ? GrowthRecipes.isDisplayCatalyst(stack) : GrowthRecipes.isCatalyst(stack);
            }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int i = 1; i <= 9; i++)
            addSlot(new Slot(storage, i, 8 + (i - 1) * 18, 126) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
            });
        addSlot(new Slot(storage, CatalystPedestalBlockEntity.SOURCE_SLOT, 40, 29) {
            @Override public boolean mayPlace(ItemStack stack) {
                return table == null ? GrowthRecipes.isDisplaySource(stack) : GrowthRecipes.isSource(stack);
            }
            @Override public int getMaxStackSize() { return 1; }
        });
        addStandardInventorySlots(inventory, 8, 156);
        addDataSlots(data);
        refresh();
    }

    private void set(int index, long value) { GrowthData.set(data, index * GrowthData.SIZE, value); }
    private long getLong(int index) { return GrowthData.get(data, index * GrowthData.SIZE); }
    private int get(int index) { return (int) getLong(index); }

    private void refresh() {
        if (table == null) return;
        set(0, table.running() ? 1 : 0);
        set(1, table.status());
        set(2, table.lastRate());
        set(3, table.lastAvailable());
        set(4, table.cost());
        var crystal = table.crystal();
        var network = crystal == null ? GrowthNetwork.Snapshot.empty() : crystal.snapshot();
        set(5, network.mothers());
        set(6, network.count(1));
        set(7, network.count(2));
        set(8, network.count(3));
        set(9, network.flags());
        int fill = 0;
        for (int i = 1; i <= 9; i++) fill += table.getItem(i).getCount();
        set(10, fill);
        set(11, table.credit());
        set(12, table.lastSpent());
        var recipe = table.selectedRecipe();
        // List.of()/List.copyOf() reject indexOf(null): empty and unsupported catalysts
        // are valid menu states, as is a multi-output catalyst awaiting a selection.
        set(13, recipe == null ? 0 : table.recipes().indexOf(recipe) + 1);
        var links = table.outputLinks();
        set(14, links.containers());
        set(15, links.freeSpace());
        set(16, links.directionMask());
        set(17, links.blockedMask());
        set(18, network.potential());
        set(19, network.count(4));
        int capacity = 0;
        ItemStack target = recipe == null ? ItemStack.EMPTY : new ItemStack(recipe.output());
        for (int i = 1; i <= 9; i++) {
            ItemStack current = table.getItem(i);
            capacity += current.isEmpty() ? (target.isEmpty() ? 64 : target.getMaxStackSize())
                : current.getMaxStackSize();
        }
        set(20, capacity);
        set(21, crystal == null ? 0 : 1);
        set(22, table.progressUnits());
        set(23, table.progressMaximum());
        set(24, table.progressRate());
        set(25, table.processing() ? 1 : 0);
        set(26, table.sourceReady() ? 1 : 0);
    }

    @Override public void broadcastChanges() { refresh(); super.broadcastChanges(); }
    public boolean running() { return get(0) != 0; }
    public int status() { return get(1); }
    public int producedPerSecond() { return get(2); }
    public long availablePerSecond() { return getLong(3); }
    public int cost() { return get(4); }
    public int mothers() { return get(5); }
    public int small() { return get(6); }
    public int medium() { return get(7); }
    public int large() { return get(8); }
    public int flags() { return get(9); }
    public int buffered() { return get(10); }
    public int credit() { return get(11); }
    public long spentPerSecond() { return getLong(12); }
    public int selectedIndex() { return get(13) - 1; }
    public int containerCount() { return get(14); }
    public int freeSpace() { return get(15); }
    public int directionMask() { return get(16); }
    public int blockedMask() { return get(17); }
    public long potential() { return getLong(18); }
    public int clusters() { return get(19); }
    public int bufferCapacity() { return get(20); }
    public boolean hasCrystal() { return get(21) != 0; }
    public long progressUnits() { return getLong(22); }
    public long progressMaximum() { return getLong(23); }
    public long progressRate() { return getLong(24); }
    public boolean processing() { return get(25) != 0; }
    public boolean sourceReady() { return get(26) != 0; }
    public List<GrowthRecipes.Recipe> recipes() {
        return table == null ? GrowthRecipes.displayRecipes(storage.getItem(0)) : table.recipes();
    }
    public GrowthRecipes.Recipe selectedRecipe() {
        var recipes = recipes();
        int selected = selectedIndex();
        return selected >= 0 && selected < recipes.size() ? recipes.get(selected) : null;
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (table == null || !stillValid(player)) return false;
        if (id == 0) table.toggleRunning(player);
        else if (id >= 1000 && id - 1000 < table.recipes().size()) {
            if (!table.selectRecipe(id - 1000)) return false;
        } else return false;
        refresh();
        broadcastChanges();
        return true;
    }

    @Override public boolean stillValid(Player player) {
        return table == null || (!table.isRemoved() && storage.stillValid(player));
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < DEVICE_SLOTS) {
            if (!moveItemStackTo(stack, DEVICE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            int destination;
            if (!storage.getItem(0).isEmpty() && GrowthRecipes.matchesSource(selectedRecipe(), stack)
                    && storage.getItem(CatalystPedestalBlockEntity.SOURCE_SLOT).isEmpty()) {
                destination = CatalystPedestalBlockEntity.SOURCE_SLOT;
            } else if (slots.getFirst().mayPlace(stack) && storage.getItem(0).isEmpty()) {
                destination = 0;
            } else if (slots.get(CatalystPedestalBlockEntity.SOURCE_SLOT).mayPlace(stack)
                    && storage.getItem(CatalystPedestalBlockEntity.SOURCE_SLOT).isEmpty()) {
                destination = CatalystPedestalBlockEntity.SOURCE_SLOT;
            } else return ItemStack.EMPTY;
            if (!moveItemStackTo(stack, destination, destination + 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
