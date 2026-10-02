package com.example.converttable;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Explicit, persistent endpoints. The clicked container face determines sided access. */
public final class ContainerLinks {
    public static final int RADIUS = 16, LIMIT = 8;
    public record Endpoint(BlockPos pos, Direction face) { }
    public record Entry(BlockPos key, Container container, Direction face) { }
    private final List<Endpoint> inputs = new ArrayList<>(), outputs = new ArrayList<>();
    public List<Entry> resolve(Level level, BlockPos origin, boolean input) {
        Map<BlockPos, Entry> result = new LinkedHashMap<>();
        for (Endpoint endpoint : input ? inputs : outputs) {
            if (endpoint.pos().distManhattan(origin) > RADIUS) continue;
            Entry entry = resolve(level, endpoint.pos(), endpoint.face());
            if (entry != null) result.putIfAbsent(entry.key(), entry);
        }
        return List.copyOf(result.values());
    }
    public static Entry resolve(Level level, BlockPos pos, Direction face) {
        if (level == null || !level.hasChunkAt(pos)) return null;
        var be = level.getBlockEntity(pos);
        if (!(be instanceof Container container) || be instanceof ConversionTableBlockEntity
                || be instanceof CatalystPedestalBlockEntity || !accessible(container)) return null;
        BlockPos key = pos;
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos partner = ChestBlock.getConnectedBlockPos(pos, state);
            if (!level.hasChunkAt(partner)) return null;
            var other = level.getBlockState(partner);
            if (other.is(state.getBlock()) && other.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                    && other.getValue(ChestBlock.TYPE) != state.getValue(ChestBlock.TYPE)
                    && other.getValue(ChestBlock.FACING) == state.getValue(ChestBlock.FACING)
                    && level.getBlockEntity(partner) instanceof Container second) {
                if (!accessible(second)) return null;
                if (partner.asLong() < pos.asLong()) { key = partner; container = new CompoundContainer(second, container); }
                else container = new CompoundContainer(container, second);
            }
        }
        return new Entry(key.immutable(), container, face);
    }
    private static boolean accessible(Container container) {
        return !(container instanceof BaseContainerBlockEntity locked && locked.isLocked())
            && !(container instanceof RandomizableContainer loot && loot.getLootTable() != null);
    }
    /** 1 added, 2 removed, 3 too many, 4 conflicts with the other role. */
    public int toggle(Level level, BlockPos origin, BlockPos pos, Direction face, boolean input) {
        Entry endpoint = resolve(level, pos, face);
        if (endpoint == null || pos.distManhattan(origin) > RADIUS) return 0;
        List<Endpoint> own = input ? inputs : outputs, other = input ? outputs : inputs;
        for (var iterator = own.iterator(); iterator.hasNext();) {
            var stored = iterator.next(); var entry = resolve(level, stored.pos(), stored.face());
            if (stored.pos().equals(pos) || entry != null && entry.key().equals(endpoint.key())) {
                iterator.remove(); return 2;
            }
        }
        for (var stored : other) {
            var entry = resolve(level, stored.pos(), stored.face());
            if (stored.pos().equals(pos) || entry != null && entry.key().equals(endpoint.key())) return 4;
        }
        if (inputs.size() + outputs.size() >= LIMIT) return 3;
        own.add(new Endpoint(pos.immutable(), face)); return 1;
    }
    public void clear(boolean input) { (input ? inputs : outputs).clear(); }
    public boolean hasInputs() { return !inputs.isEmpty(); }
    public void save(ValueOutput output) {
        save(output, "Input", inputs); save(output, "Output", outputs);
    }
    private static void save(ValueOutput output, String role, List<Endpoint> values) {
        output.putInt(role + "Links", values.size());
        for (int i = 0; i < values.size(); i++) {
            output.putLong(role + "Link" + i, values.get(i).pos().asLong());
            output.putString(role + "Face" + i, values.get(i).face().name());
        }
    }
    public void load(ValueInput input) { inputs.clear(); outputs.clear(); load(input, "Input", inputs); load(input, "Output", outputs); }
    private void load(ValueInput input, String role, List<Endpoint> values) {
        int size = Math.clamp(input.getIntOr(role + "Links", 0), 0, LIMIT - inputs.size() - outputs.size());
        for (int i = 0; i < size; i++) {
            Direction face;
            try { face = Direction.valueOf(input.getStringOr(role + "Face" + i, "UP")); }
            catch (IllegalArgumentException ignored) { face = Direction.UP; }
            BlockPos pos = BlockPos.of(input.getLongOr(role + "Link" + i, 0));
            if (values.stream().noneMatch(e -> e.pos().equals(pos))) values.add(new Endpoint(pos, face));
        }
    }
    public static boolean canExtract(Entry entry, int slot, ItemStack stack, Container destination) {
        if (!entry.container().canTakeItem(destination, slot, stack)) return false;
        return !(entry.container() instanceof WorldlyContainer sided)
            || contains(sided.getSlotsForFace(entry.face()), slot) && sided.canTakeItemThroughFace(slot, stack, entry.face());
    }
    public static boolean accepts(Entry entry, int slot, ItemStack stack) {
        if (!entry.container().canPlaceItem(slot, stack)) return false;
        return !(entry.container() instanceof WorldlyContainer sided)
            || contains(sided.getSlotsForFace(entry.face()), slot) && sided.canPlaceItemThroughFace(slot, stack, entry.face());
    }
    private static boolean contains(int[] slots, int slot) { for (int i : slots) if (i == slot) return true; return false; }
    public static int room(List<Entry> entries, ItemStack stack) {
        long result = 0;
        for (Entry entry : entries) for (int slot = 0; slot < entry.container().getContainerSize(); slot++) {
            if (!accepts(entry, slot, stack)) continue;
            result += room(entry.container(), entry.container().getItem(slot), stack);
        }
        return (int) Math.min(Integer.MAX_VALUE, result);
    }
    private static int room(Container container, ItemStack current, ItemStack stack) {
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, stack)) return 0;
        return Math.max(0, Math.min(container.getMaxStackSize(stack), stack.getMaxStackSize()) - current.getCount());
    }
    /** The entire insertion is planned on copies before any input or cost is consumed. */
    public static OutputPlan plan(List<Entry> entries, ItemStack output) {
        Map<Entry, List<ItemStack>> values = new LinkedHashMap<>();
        for (Entry entry : entries) {
            List<ItemStack> stacks = new ArrayList<>();
            for (int i = 0; i < entry.container().getContainerSize(); i++) stacks.add(entry.container().getItem(i).copy());
            values.put(entry, stacks);
        }
        int remaining = output.getCount();
        for (int pass = 0; pass < 2; pass++) for (var pair : values.entrySet()) {
            Entry entry = pair.getKey(); List<ItemStack> stacks = pair.getValue();
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack current = stacks.get(i);
                if (!accepts(entry, i, output) || (pass == 0) == current.isEmpty()) continue;
                int moved = Math.min(remaining, room(entry.container(), current, output));
                if (moved > 0) stacks.set(i, current.isEmpty() ? output.copyWithCount(moved) : current.copyWithCount(current.getCount() + moved));
                remaining -= moved;
                if (remaining == 0) return new OutputPlan(values);
            }
        }
        return null;
    }
    public record OutputPlan(Map<Entry, List<ItemStack>> values) {
        public void commit() {
            values.forEach((entry, stacks) -> {
                for (int i = 0; i < stacks.size(); i++) if (!ItemStack.matches(entry.container().getItem(i), stacks.get(i)))
                    entry.container().setItem(i, stacks.get(i));
                entry.container().setChanged();
            });
        }
    }
}
