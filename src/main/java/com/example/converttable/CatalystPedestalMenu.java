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
    private static final int DEVICE_SLOTS = 10, DATA_SIZE = 26;
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
            @Override public boolean mayPlace(ItemStack stack) { return GrowthRecipes.isCatalyst(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int i = 1; i <= 9; i++)
            addSlot(new Slot(storage, i, 8 + (i - 1) * 18, 126) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
            });
        addStandardInventorySlots(inventory, 8, 156);
        addDataSlots(data);
        refresh();
    }

    private void refresh() {
        if (table == null) return;
        data.set(0, table.running() ? 1 : 0);
        data.set(1, table.status());
        data.set(2, table.lastRate());
        data.set(3, table.lastAvailable());
        data.set(4, table.cost());
        var crystal = table.crystal();
        var network = crystal == null ? GrowthNetwork.Snapshot.empty() : crystal.snapshot();
        data.set(5, network.mothers());
        data.set(6, network.count(1));
        data.set(7, network.count(2));
        data.set(8, network.count(3));
        data.set(9, network.flags());
        int fill = 0;
        for (int i = 1; i <= 9; i++) fill += table.getItem(i).getCount();
        data.set(10, fill);
        data.set(11, table.credit());
        data.set(12, table.lastSpent());
        var recipe = table.selectedRecipe();
        // List.of()/List.copyOf() reject indexOf(null): empty and unsupported catalysts
        // are valid menu states, as is a multi-output catalyst awaiting a selection.
        data.set(13, recipe == null ? 0 : table.recipes().indexOf(recipe) + 1);
        var links = table.outputLinks();
        data.set(14, links.containers());
        data.set(15, Math.min(65535, links.freeSpace()));
        data.set(16, links.directionMask());
        data.set(17, links.blockedMask());
        data.set(18, network.potential());
        data.set(19, network.count(4));
        int capacity = 0;
        ItemStack target = recipe == null ? ItemStack.EMPTY : new ItemStack(recipe.output());
        for (int i = 1; i <= 9; i++) {
            ItemStack current = table.getItem(i);
            capacity += current.isEmpty() ? (target.isEmpty() ? 64 : target.getMaxStackSize())
                : current.getMaxStackSize();
        }
        data.set(20, capacity);
        data.set(21, crystal == null ? 0 : 1);
        data.set(22, table.progressUnits() & 0xffff);
        data.set(23, table.progressUnits() >>> 16);
        data.set(24, table.progressRate());
        data.set(25, table.processing() ? 1 : 0);
    }

    @Override public void broadcastChanges() { refresh(); super.broadcastChanges(); }
    public boolean running() { return data.get(0) != 0; }
    public int status() { return data.get(1); }
    public int producedPerSecond() { return data.get(2); }
    public int availablePerSecond() { return data.get(3); }
    public int cost() { return data.get(4); }
    public int mothers() { return data.get(5); }
    public int small() { return data.get(6); }
    public int medium() { return data.get(7); }
    public int large() { return data.get(8); }
    public int flags() { return data.get(9); }
    public int buffered() { return data.get(10); }
    public int credit() { return data.get(11); }
    public int spentPerSecond() { return data.get(12); }
    public int selectedIndex() { return data.get(13) - 1; }
    public int containerCount() { return data.get(14); }
    public int freeSpace() { return data.get(15) & 0xffff; }
    public int directionMask() { return data.get(16); }
    public int blockedMask() { return data.get(17); }
    public int potential() { return data.get(18); }
    public int clusters() { return data.get(19); }
    public int bufferCapacity() { return data.get(20); }
    public boolean hasCrystal() { return data.get(21) != 0; }
    public int progressUnits() { return (data.get(22) & 0xffff) | ((data.get(23) & 0x7fff) << 16); }
    public int progressMaximum() { return (int) Math.min(Integer.MAX_VALUE, (long) cost() * 20); }
    public int progressRate() { return data.get(24); }
    public boolean processing() { return data.get(25) != 0; }
    public List<GrowthRecipes.Recipe> recipes() { return GrowthRecipes.recipes(storage.getItem(0)); }
    public GrowthRecipes.Recipe selectedRecipe() {
        var recipes = recipes();
        int selected = selectedIndex();
        return selected >= 0 && selected < recipes.size() ? recipes.get(selected) : null;
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (table == null || !stillValid(player)) return false;
        if (id == 0) table.toggleRunning();
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
            if (!GrowthRecipes.isCatalyst(stack) || !storage.getItem(0).isEmpty()
                    || !moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}

