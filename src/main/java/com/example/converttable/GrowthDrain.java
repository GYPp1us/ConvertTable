package com.example.converttable;

import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.math.BigInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Actual extraction preserves the existing natural-growth slowdown, independent of factor units. */
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
                long weight=buds.get(i).available();
                assigned[i] = share(spent,weight,available);
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
        double sum=0,min=1,max=0;
        int active=0;
        double[] stageSums=new double[3];
        int[] stageCounts=new int[3];
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
            sum+=chance;active++;min=Math.min(min,chance);max=Math.max(max,chance);
            stageSums[bud.stage()-1]+=chance;stageCounts[bud.stage()-1]++;
        }
        network.recordGrowth(sum,active,min,max,stageSums,stageCounts);
    }

    static long share(long spent,long weight,long total) {
        if(spent==0 || weight==0)return 0;
        if(spent==total)return weight;
        if(spent<=Long.MAX_VALUE/weight)return spent*weight/total;
        return BigInteger.valueOf(spent).multiply(BigInteger.valueOf(weight))
            .divide(BigInteger.valueOf(total)).longValueExact();
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
