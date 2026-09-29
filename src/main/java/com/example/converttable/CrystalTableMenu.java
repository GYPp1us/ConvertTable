package com.example.converttable;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public final class CrystalTableMenu extends AbstractContainerMenu {
    private static final int DATA_SIZE = 13;
    private final CrystalTableBlockEntity table;
    private final ContainerData data = new SimpleContainerData(DATA_SIZE);

    public CrystalTableMenu(int id, Inventory inventory) { this(id, inventory, null); }

    public CrystalTableMenu(int id, Inventory inventory, CrystalTableBlockEntity table) {
        super(GrowthMenus.CRYSTAL, id);
        this.table = table;
        addStandardInventorySlots(inventory, 53, 156);
        addDataSlots(data);
        refresh();
    }

    private void refresh() {
        if (table == null) return;
        GrowthNetwork.Snapshot snapshot = table.snapshot();
        data.set(0, snapshot.mothers());
        data.set(1, snapshot.conductors());
        data.set(2, snapshot.count(1));
        data.set(3, snapshot.count(2));
        data.set(4, snapshot.count(3));
        data.set(5, snapshot.count(4));
        data.set(6, snapshot.available());
        data.set(7, snapshot.flags());
        data.set(8, table.pedestalCount());
        data.set(9, snapshot.potential());
        data.set(10, table.lastSpent());
        data.set(11, snapshot.calciteMothers());
        data.set(12, snapshot.basaltMothers());
    }

    @Override public void broadcastChanges() { refresh(); super.broadcastChanges(); }
    public int mothers() { return data.get(0); }
    public int conductors() { return data.get(1); }
    public int small() { return data.get(2); }
    public int medium() { return data.get(3); }
    public int large() { return data.get(4); }
    public int clusters() { return data.get(5); }
    public int rate() { return data.get(6); }
    public int flags() { return data.get(7); }
    public boolean hasPedestal() { return data.get(8) != 0; }
    public int pedestals() { return data.get(8); }
    public int potential() { return data.get(9); }
    public int spent() { return data.get(10); }
    public int calciteMothers() { return data.get(11); }
    public int basaltMothers() { return data.get(12); }

    @Override public boolean stillValid(Player player) {
        return table == null || (!table.isRemoved()
            && player.distanceToSqr(table.getBlockPos().getX() + .5,
                table.getBlockPos().getY() + .5, table.getBlockPos().getZ() + .5) <= 64.0);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
