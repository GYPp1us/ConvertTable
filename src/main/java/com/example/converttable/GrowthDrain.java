package com.example.converttable;

import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** A face's extracted microfactors preserve the same physical growth ratios after the 1/64 change. */
public final class GrowthDrain {
    private record Face(BlockPos mother, Direction direction) { }
    private record Entry(float chance, long untilTick) { }
    private static final class Drains {
        final Map<Face, Entry> entries = new HashMap<>();
        long lastPrune = Long.MIN_VALUE;
    }
    private static final Map<Level, Drains> BY_LEVEL = Collections.synchronizedMap(new WeakHashMap<>());
    private GrowthDrain() { }

    public static void publish(Level level, GrowthNetwork.Snapshot network, long spent) {
        if (level == null || level.isClientSide()) return;
        var buds = network.buds();
        long available = network.available();
        long[] assigned = new long[buds.size()];
        spent = Math.clamp(spent, 0L, available);
        if (spent > 0 && available > 0) {
            long counted = 0;
            for (int i = 0; i < buds.size(); i++) {
                // available per face is at most two microfactors; a Java list bounds this product.
                assigned[i] = spent * buds.get(i).available() / available;
                counted += assigned[i];
            }
            int start = (int) (level.getGameTime() / 20 % buds.size());
            // Proportional flooring leaves fewer than one remainder per face, independent of budget.
            for (int offset = 0; counted < spent && offset < buds.size(); offset++) {
                int i = (start + offset) % buds.size();
                if (assigned[i] < buds.get(i).available()) { assigned[i]++; counted++; }
            }
        }
        Drains drains = BY_LEVEL.computeIfAbsent(level, ignored -> new Drains());
        long now = level.getGameTime();
        if (drains.lastPrune != now) {
            drains.entries.entrySet().removeIf(e -> e.getValue().untilTick() < now);
            drains.lastPrune = now;
        }
        for (int i = 0; i < buds.size(); i++) {
            GrowthNetwork.Bud bud = buds.get(i);
            if (bud.stage() == 4 || bud.potential() <= 0) continue;
            long basePotential = GrowthBudFactors.natural(bud.stage());
            float remainder = (bud.potential() - assigned[i]) / (float) basePotential;
            float chance = Math.clamp(remainder * (bud.calcite() ? .8F : 1F), .15F, 1F);
            Face face = new Face(bud.mother(), bud.face());
            Entry sameTick = drains.entries.get(face);
            if (sameTick != null && sameTick.untilTick() == now + 30) chance = Math.min(chance, sameTick.chance());
            drains.entries.put(face, new Entry(chance, now + 30));
        }
    }

    static void clear(Level level, GrowthNetwork.Snapshot network) {
        Drains drains = BY_LEVEL.get(level);
        if (drains == null) return;
        for (GrowthNetwork.Bud bud : network.buds()) drains.entries.remove(new Face(bud.mother(), bud.face()));
    }

    public static float growthChance(Level level, BlockPos mother, Direction face) {
        Drains drains = BY_LEVEL.get(level);
        if (drains == null) return 1F;
        Face key = new Face(mother, face);
        Entry entry = drains.entries.get(key);
        if (entry == null) return 1F;
        if (entry.untilTick() < level.getGameTime()) { drains.entries.remove(key); return 1F; }
        return entry.chance();
    }
}
