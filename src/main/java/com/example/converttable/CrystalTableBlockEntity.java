package com.example.converttable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Owns a physical crystal array and distributes its export budget once per second. */
public final class CrystalTableBlockEntity extends BlockEntity implements MenuProvider {
    private long lastScan = Long.MIN_VALUE;
    private GrowthNetwork.Snapshot snapshot = GrowthNetwork.Snapshot.empty();
    private int lastSpent;

    public CrystalTableBlockEntity(BlockPos pos, BlockState state) {
        super(GrowthBlocks.CRYSTAL_ENTITY, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CrystalTableBlockEntity table) {
        if (!level.isClientSide() && level.getGameTime() % 20 == 0) table.produce();
    }

    public GrowthNetwork.Snapshot snapshot() {
        if (level != null && !level.isClientSide()
                && (lastScan == Long.MIN_VALUE || level.getGameTime() - lastScan >= 20)) {
            snapshot = GrowthNetwork.scan(level, worldPosition);
            lastScan = level.getGameTime();
        }
        return snapshot;
    }

    public void invalidateNetwork() { lastScan = Long.MIN_VALUE; }
    public int lastSpent() { return lastSpent; }
    public int pedestalCount() { return snapshot().pedestals().size(); }

    private void produce() {
        GrowthNetwork.Snapshot network = snapshot();
        List<CatalystPedestalBlockEntity> active = new ArrayList<>();
        for (BlockPos pos : network.pedestals()) {
            if (level.getBlockEntity(pos) instanceof CatalystPedestalBlockEntity pedestal
                    && pedestal.prepareCycle(this, network)) active.add(pedestal);
        }
        if (!active.isEmpty() && network.usable() && network.count(4) > 0) {
            for (GrowthNetwork.Bud bud : network.buds()) if (bud.stage() == 4) {
                BlockState old = level.getBlockState(bud.pos());
                if (!old.is(Blocks.AMETHYST_CLUSTER)) continue;
                level.setBlockAndUpdate(bud.pos(), Blocks.SMALL_AMETHYST_BUD.defaultBlockState()
                    .setValue(AmethystClusterBlock.FACING, bud.face())
                    .setValue(AmethystClusterBlock.WATERLOGGED, old.getValue(AmethystClusterBlock.WATERLOGGED)));
            }
            invalidateNetwork();
            network = snapshot();
        }
        int[] allocation = new int[active.size()];
        int budget = network.usable() ? network.available() : 0;
        if (!active.isEmpty()) {
            int cursor = (int) (level.getGameTime() / 20 % active.size());
            while (budget > 0) {
                boolean moved = false;
                for (int offset = 0; offset < active.size() && budget > 0; offset++) {
                    int index = (cursor + offset) % active.size();
                    if (allocation[index] < active.get(index).demand()) {
                        allocation[index]++;
                        budget--;
                        moved = true;
                    }
                }
                if (!moved) break;
            }
        }
        lastSpent = 0;
        for (int i = 0; i < active.size(); i++)
            lastSpent += active.get(i).acceptGrowth(allocation[i], network.available());
        // Even an idle network applies the physical calcite growth slowdown.
        GrowthDrain.publish(level, network, lastSpent);
    }

    public boolean owns(CatalystPedestalBlockEntity candidate) {
        return level != null && snapshot().pedestals().contains(candidate.getBlockPos())
            && GrowthNetwork.findSource(level, candidate.getBlockPos()) == this;
    }

    public boolean ownsAnyPedestal() {
        return pedestalCount() > 0;
    }

    @Override public Component getDisplayName() {
        return Component.translatable("block.convert_table.crystal_table");
    }

    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CrystalTableMenu(id, inventory, this);
    }
}
