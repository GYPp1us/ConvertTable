package com.example.converttable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;

/** Linked export inventory discovery and transfer share the same sided rules. */
public final class GrowthOutputLinks {
    /** Direction masks describe spatial main directions and use ordinal(): down, up, north, south, west, east. */
    public record Snapshot(int containers, int freeSpace, int directionMask, int blockedMask) {
        public static Snapshot empty() { return new Snapshot(0, 0, 0, 0); }
    }
    /** direction is the dominant spatial axis; accessFace is the linked container's clicked sided-access face. */
    private record Entry(BlockPos key, Container container, Direction direction, Direction accessFace,
            boolean accessible) { }

    private GrowthOutputLinks() { }

    private static List<Entry> discover(Level level, BlockPos origin) {
        if (level == null || !level.hasChunkAt(origin)) return List.of();
        var blockEntity = level.getBlockEntity(origin);
        if (!(blockEntity instanceof CatalystPedestalBlockEntity pedestal)) return List.of();
        List<Entry> result = new ArrayList<>();
        for (ContainerLinks.Entry binding : pedestal.outputContainers()) {
            result.add(new Entry(binding.key(), binding.container(), mainDirection(origin, binding.key()),
                binding.face(), accessible(binding.container())));
        }
        return result;
    }

    /** Ties resolve in Y, X, Z order for a stable UI mask. */
    private static Direction mainDirection(BlockPos origin, BlockPos pos) {
        int dx = pos.getX() - origin.getX(), dy = pos.getY() - origin.getY(), dz = pos.getZ() - origin.getZ();
        int ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        if (ay > 0 && ay >= ax && ay >= az) return dy < 0 ? Direction.DOWN : Direction.UP;
        if (ax > 0 && ax >= az) return dx < 0 ? Direction.WEST : Direction.EAST;
        return dz < 0 ? Direction.NORTH : Direction.SOUTH;
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
                ? room(entry.container(), output, entry.accessFace()) : 0;
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
                int moved = insert(entry.container(), stack, entry.accessFace());
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
