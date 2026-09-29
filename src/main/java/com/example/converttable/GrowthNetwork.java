package com.example.converttable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Bounded, loaded-chunk-only crystal network. Connected crystal tables disable a shared network. */
public final class GrowthNetwork {
    public static final int RADIUS = 8, NODE_LIMIT = 128, MOTHER_LIMIT = 8;
    public static final int UNKNOWN = 1, LIMIT = 2, SHARED = 4;

    public record Bud(BlockPos mother, BlockPos pos, Direction face, int stage,
                      int potential, int available, boolean calcite, boolean basalt) { }
    public record Snapshot(List<Bud> buds, int mothers, int conductors, int flags,
                           int calciteMothers, int basaltMothers, Set<BlockPos> nodes,
                           List<BlockPos> pedestals) {
        public static Snapshot empty() { return new Snapshot(List.of(), 0, 0, 0, 0, 0, Set.of(), List.of()); }
        public int available() { return buds.stream().mapToInt(Bud::available).sum(); }
        public int potential() { return buds.stream().mapToInt(Bud::potential).sum(); }
        public int count(int stage) { return (int) buds.stream().filter(b -> b.stage() == stage).count(); }
        public boolean usable() { return (flags & (LIMIT | SHARED)) == 0; }
    }
    private record Contact(boolean calcite, boolean basalt) { }

    private GrowthNetwork() { }

    public static Snapshot scan(Level level, BlockPos table) {
        Map<BlockPos, Integer> distance = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> mothers = new ArrayList<>();
        Set<BlockPos> conductors = new HashSet<>();
        Set<BlockPos> pedestals = new HashSet<>();
        distance.put(table, 0);
        queue.add(table);
        int flags = 0;
        while (!queue.isEmpty()) {
            BlockPos from = queue.removeFirst();
            for (Direction side : Direction.values()) {
                BlockPos next = from.relative(side);
                if (!level.hasChunkAt(next)) { flags |= UNKNOWN; continue; }
                BlockState state = level.getBlockState(next);
                if (state.is(GrowthBlocks.CATALYST)) pedestals.add(next.immutable());
                if (state.is(GrowthBlocks.CRYSTAL) && !next.equals(table)) {
                    flags |= SHARED;
                    continue;
                }
                if (distance.containsKey(next)
                        || !(state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.BUDDING_AMETHYST))) continue;
                if (next.distManhattan(table) > RADIUS || distance.size() >= NODE_LIMIT) {
                    flags |= LIMIT;
                    continue;
                }
                if (state.is(Blocks.BUDDING_AMETHYST) && mothers.size() >= MOTHER_LIMIT) {
                    flags |= LIMIT;
                    continue;
                }
                distance.put(next, distance.get(from) + 1);
                queue.addLast(next);
                if (state.is(Blocks.BUDDING_AMETHYST)) mothers.add(next.immutable());
                else conductors.add(next.immutable());
            }
        }
        mothers.sort(Comparator.comparingLong(BlockPos::asLong));
        List<Bud> buds = new ArrayList<>();
        int calciteCount = 0, basaltCount = 0;
        for (BlockPos mother : mothers) {
            Contact contact = contact(level, mother, conductors);
            if (contact.calcite()) calciteCount++;
            if (contact.basalt()) basaltCount++;
            for (Direction side : Direction.values()) {
                BlockPos budPos = mother.relative(side);
                if (!level.hasChunkAt(budPos)) { flags |= UNKNOWN; continue; }
                BlockState state = level.getBlockState(budPos);
                int stage = stage(state, side);
                if (stage == 0) continue;
                int potential = switch (stage) {
                    case 1 -> 24;
                    case 2 -> 16;
                    case 3 -> 8;
                    default -> 0;
                };
                if (potential > 0 && contact.basalt()) potential++;
                // Growth potential is internal. A mother exports at most one
                // element per bud per second, or two when touching calcite.
                int available = Math.min(potential, contact.calcite() ? 2 : 1);
                buds.add(new Bud(mother, budPos, side, stage, potential,
                    available, contact.calcite(), contact.basalt()));
            }
        }
        Set<BlockPos> nodes = new HashSet<>(conductors);
        nodes.addAll(mothers);
        return new Snapshot(List.copyOf(buds), mothers.size(), conductors.size(),
            flags, calciteCount, basaltCount, Set.copyOf(nodes),
            pedestals.stream().sorted(Comparator.comparingLong(BlockPos::asLong)).toList());
    }

    public static boolean isConductor(BlockState state) {
        return state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.BUDDING_AMETHYST);
    }

    /** Finds a unique loaded source through face-connected crystals, never through a pedestal. */
    public static CrystalTableBlockEntity findSource(Level level, BlockPos attachment) {
        if (level == null || !level.hasChunkAt(attachment)) return null;
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> sources = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(attachment);
        queue.add(attachment);
        while (!queue.isEmpty()) {
            BlockPos from = queue.removeFirst();
            for (Direction side : Direction.values()) {
                BlockPos next = from.relative(side);
                if (!level.hasChunkAt(next)) continue;
                BlockState state = level.getBlockState(next);
                if (state.is(GrowthBlocks.CRYSTAL)) {
                    sources.add(next.immutable());
                    if (sources.size() > 1) return null;
                }
                if (!isConductor(state) || visited.contains(next)) continue;
                if (next.distManhattan(attachment) > RADIUS * 2 + 1 || visited.size() >= NODE_LIMIT)
                    return null;
                visited.add(next.immutable());
                queue.addLast(next);
            }
        }
        if (sources.size() != 1) return null;
        BlockPos source = sources.iterator().next();
        if (!(level.getBlockEntity(source) instanceof CrystalTableBlockEntity crystal)) return null;
        Snapshot network = level.isClientSide() ? scan(level, source) : crystal.snapshot();
        boolean connected = attachment.equals(source) || network.nodes().contains(attachment);
        for (Direction side : Direction.values())
            connected |= attachment.relative(side).equals(source) || network.nodes().contains(attachment.relative(side));
        return connected ? crystal : null;
    }

    private static int stage(BlockState state, Direction face) {
        if (!(state.is(Blocks.SMALL_AMETHYST_BUD) || state.is(Blocks.MEDIUM_AMETHYST_BUD)
                || state.is(Blocks.LARGE_AMETHYST_BUD) || state.is(Blocks.AMETHYST_CLUSTER))
                || state.getValue(AmethystClusterBlock.FACING) != face) return 0;
        if (state.is(Blocks.SMALL_AMETHYST_BUD)) return 1;
        if (state.is(Blocks.MEDIUM_AMETHYST_BUD)) return 2;
        if (state.is(Blocks.LARGE_AMETHYST_BUD)) return 3;
        return 4;
    }

    private static Contact contact(Level level, BlockPos mother, Set<BlockPos> conductors) {
        boolean calcite = false, basalt = false;
        List<BlockPos> bases = new ArrayList<>();
        bases.add(mother);
        // A mineral touching any conductor in the component affects its connected mothers.
        bases.addAll(conductors);
        for (BlockPos base : bases) for (Direction side : Direction.values()) {
            BlockPos neighbor = base.relative(side);
            if (!level.hasChunkAt(neighbor)) continue;
            BlockState state = level.getBlockState(neighbor);
            calcite |= state.is(Blocks.CALCITE);
            basalt |= state.is(Blocks.SMOOTH_BASALT);
        }
        return new Contact(calcite, basalt);
    }
}
