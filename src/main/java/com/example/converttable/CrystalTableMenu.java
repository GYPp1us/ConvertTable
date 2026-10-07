package com.example.converttable;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public final class CrystalTableMenu extends AbstractContainerMenu {
    private static final int DATA_SIZE = 15 * GrowthData.SIZE;
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

    private void put(int field, long value) { GrowthData.set(data, field * GrowthData.SIZE, value); }
    private long get(int field) { return GrowthData.get(data, field * GrowthData.SIZE); }
    private void refresh() {
        if (table == null) return;
        GrowthNetwork.Snapshot snapshot = table.snapshot();
        put(0, snapshot.mothers());
        put(1, snapshot.conductors());
        put(2, snapshot.count(1));
        put(3, snapshot.count(2));
        put(4, snapshot.count(3));
        put(5, snapshot.count(4));
        put(6, snapshot.available());
        put(7, snapshot.flags());
        put(8, table.pedestalCount());
        put(9, snapshot.potential());
        put(10, table.lastSpent());
        put(11, snapshot.calciteMothers());
        put(12, snapshot.basaltMothers());
        put(13, table.progressTicks());
        put(14, table.processing() ? 1 : 0);
    }

    @Override public void broadcastChanges() { refresh(); super.broadcastChanges(); }
    public int mothers() { return (int) get(0); }
    public int conductors() { return (int) get(1); }
    public int small() { return (int) get(2); }
    public int medium() { return (int) get(3); }
    public int large() { return (int) get(4); }
    public int clusters() { return (int) get(5); }
    public long rate() { return get(6); }
    public int flags() { return (int) get(7); }
    public boolean hasPedestal() { return get(8) != 0; }
    public int pedestals() { return (int) get(8); }
    public long potential() { return get(9); }
    public long spent() { return get(10); }
    public int calciteMothers() { return (int) get(11); }
    public int basaltMothers() { return (int) get(12); }
    public int progressTicks() { return (int) get(13); }
    public int totalTicks() { return 20; }
    public long progressRate() { return spent(); }
    public boolean processing() { return get(14) != 0; }

    @Override public boolean stillValid(Player player) {
        return table == null || (!table.isRemoved()
            && player.distanceToSqr(table.getBlockPos().getX() + .5,
                table.getBlockPos().getY() + .5, table.getBlockPos().getZ() + .5) <= 64.0);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
