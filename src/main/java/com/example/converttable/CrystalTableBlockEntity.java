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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Owns a physical crystal array and distributes its export budget once per second. */
public final class CrystalTableBlockEntity extends BlockEntity implements MenuProvider {
    private long lastScan = Long.MIN_VALUE;
    private GrowthNetwork.Snapshot snapshot = GrowthNetwork.Snapshot.empty();
    private int lastSpent;
    private long cycleStart = Long.MIN_VALUE;
    private BlockPos nextPedestal;

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
    public int progressTicks() { return level == null || cycleStart == Long.MIN_VALUE ? 0
        : (int) Math.clamp(level.getGameTime() - cycleStart, 0L, 20L); }
    public boolean processing() {
        return lastSpent > 0 && progressTicks() < 20 && snapshot().usable()
            && snapshot().pedestals().stream().anyMatch(pos -> level.getBlockEntity(pos)
                instanceof CatalystPedestalBlockEntity pedestal && pedestal.processing());
    }
    public int pedestalCount() { return snapshot().pedestals().size(); }

    void produce() {
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
        int budget = network.usable() ? network.available() : 0;
        int cursor = 0;
        if (nextPedestal != null) {
            for (int i = 0; i < active.size(); i++) {
                if (active.get(i).getBlockPos().asLong() >= nextPedestal.asLong()) { cursor = i; break; }
            }
        }
        var allocation = GrowthAllocation.divide(budget,
            active.stream().mapToInt(CatalystPedestalBlockEntity::demand).toArray(), cursor);
        if (allocation.spent() > 0) {
            // Advance only when a factor was assigned. Intermittent supply, idle seconds,
            // and a save/reload must not repeatedly favor the same first pedestal.
            nextPedestal = active.get(allocation.nextIndex()).getBlockPos().immutable();
            setChanged();
        }
        lastSpent = 0;
        for (int i = 0; i < active.size(); i++)
            lastSpent += active.get(i).acceptGrowth(allocation.amounts()[i], network.available());
        cycleStart = level.getGameTime();
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

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (nextPedestal != null) output.putLong("NextPedestal", nextPedestal.asLong());
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        long next = input.getLongOr("NextPedestal", Long.MIN_VALUE);
        nextPedestal = next == Long.MIN_VALUE ? null : BlockPos.of(next);
    }

    @Override public Component getDisplayName() {
        return Component.translatable("block.convert_table.crystal_table");
    }

    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CrystalTableMenu(id, inventory, this);
    }
}
