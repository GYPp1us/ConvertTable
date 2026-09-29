package com.example.converttable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Adjacent export inventory discovery and transfer share the same sided rules. */
public final class GrowthOutputLinks {
    /** Masks use Direction.ordinal(): down, up, north, south, west, east. */
    public record Snapshot(int containers, int freeSpace, int directionMask, int blockedMask) {
        public static Snapshot empty() { return new Snapshot(0, 0, 0, 0); }
    }
    private record Entry(BlockPos key, Container container, Direction direction, boolean accessible) { }
    private static final Direction[] ORDER = {Direction.DOWN, Direction.NORTH, Direction.SOUTH,
        Direction.WEST, Direction.EAST, Direction.UP};

    private GrowthOutputLinks() { }

    private static List<Entry> discover(Level level, BlockPos origin) {
        if (level == null) return List.of();
        List<Entry> result = new ArrayList<>();
        for (Direction direction : ORDER) {
            BlockPos pos = origin.relative(direction);
            if (!level.hasChunkAt(pos)) continue;
            var be = level.getBlockEntity(pos);
            if (!(be instanceof Container container) || be instanceof CatalystPedestalBlockEntity
                    || be instanceof ConversionTableBlockEntity) continue;
            boolean accessible = accessible(container);
            BlockPos key = pos;
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                BlockPos partner = ChestBlock.getConnectedBlockPos(pos, state);
                if (!level.hasChunkAt(partner)) accessible = false;
                else {
                    var other = level.getBlockState(partner);
                    if (other.is(state.getBlock()) && other.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                            && other.getValue(ChestBlock.TYPE) != state.getValue(ChestBlock.TYPE)
                            && other.getValue(ChestBlock.FACING) == state.getValue(ChestBlock.FACING)
                            && level.getBlockEntity(partner) instanceof Container second) {
                        accessible &= accessible(second);
                        if (partner.asLong() < pos.asLong()) {
                            key = partner;
                            container = new CompoundContainer(second, container);
                        } else container = new CompoundContainer(container, second);
                    }
                }
            }
            result.add(new Entry(key.immutable(), container, direction, accessible));
        }
        return result;
    }

    private static boolean accessible(Container target) {
        return !(target instanceof BaseContainerBlockEntity locked && locked.isLocked())
            && !(target instanceof RandomizableContainer loot && loot.getLootTable() != null);
    }

    public static Snapshot scan(Level level, BlockPos origin, ItemStack output) {
        int directions = 0, blocked = 0, free = 0;
        Set<BlockPos> counted = new HashSet<>();
        for (Entry entry : discover(level, origin)) {
            int bit = 1 << entry.direction().ordinal();
            directions |= bit;
            int room = entry.accessible() && !output.isEmpty()
                ? room(entry.container(), output, entry.direction().getOpposite()) : 0;
            if (!entry.accessible() || (!output.isEmpty() && room == 0)) blocked |= bit;
            if (counted.add(entry.key())) free += room;
        }
        return new Snapshot(counted.size(), free, directions, blocked);
    }

    public static void flush(Level level, BlockPos origin, CatalystPedestalBlockEntity pedestal) {
        Set<BlockPos> visited = new HashSet<>();
        for (Entry entry : discover(level, origin)) {
            if (!entry.accessible() || !visited.add(entry.key())) continue;
            for (int slot = CatalystPedestalBlockEntity.OUTPUT_FIRST;
                    slot <= CatalystPedestalBlockEntity.OUTPUT_LAST; slot++) {
                ItemStack stack = pedestal.getItem(slot);
                if (stack.isEmpty()) continue;
                int moved = insert(entry.container(), stack, entry.direction().getOpposite());
                if (moved > 0) pedestal.setItem(slot, stack.copyWithCount(stack.getCount() - moved));
            }
        }
    }

    private static boolean accepts(Container target, int slot, ItemStack stack, Direction side) {
        if (!target.canPlaceItem(slot, stack)) return false;
        if (target instanceof WorldlyContainer sided) {
            boolean found = false;
            for (int available : sided.getSlotsForFace(side)) found |= available == slot;
            if (!found || !sided.canPlaceItemThroughFace(slot, stack, side)) return false;
        }
        return true;
    }

    private static int room(Container target, ItemStack stack, Direction side) {
        int result = 0;
        for (int slot = 0; slot < target.getContainerSize(); slot++) {
            if (!accepts(target, slot, stack, side)) continue;
            ItemStack current = target.getItem(slot);
            if (current.isEmpty() || ItemStack.isSameItemSameComponents(current, stack))
                result += Math.max(0, Math.min(target.getMaxStackSize(), stack.getMaxStackSize()) - current.getCount());
        }
        return result;
    }

    private static int insert(Container target, ItemStack stack, Direction side) {
        int remaining = stack.getCount();
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int slot = 0; slot < target.getContainerSize() && remaining > 0; slot++) {
                if (!accepts(target, slot, stack, side)) continue;
                ItemStack current = target.getItem(slot);
                if ((pass == 0 && current.isEmpty()) || (pass == 1 && !current.isEmpty())) continue;
                if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, stack)) continue;
                int move = Math.min(remaining,
                    Math.min(target.getMaxStackSize(), stack.getMaxStackSize()) - current.getCount());
                if (move <= 0) continue;
                target.setItem(slot, current.isEmpty() ? stack.copyWithCount(move)
                    : current.copyWithCount(current.getCount() + move));
                target.setChanged();
                remaining -= move;
            }
        }
        return stack.getCount() - remaining;
    }
}
