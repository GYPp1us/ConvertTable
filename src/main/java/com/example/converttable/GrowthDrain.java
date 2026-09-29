package com.example.converttable;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Last production interval's extraction, indexed by the actual growth face.
 * This is transient world state; the next production tick recomputes it.
 */
public final class GrowthDrain {
    private record Face(BlockPos mother, Direction direction) { }
    private record Entry(float chance, long untilTick) { }
    private static final Map<Level, Map<Face, Entry>> BY_LEVEL = new WeakHashMap<>();

    private GrowthDrain() { }

    public static void publish(Level level, GrowthNetwork.Snapshot network, int spent) {
        if (level == null || level.isClientSide()) return;
        var buds = network.buds();
        int available = network.available();
        int[] assigned = new int[buds.size()];
        if (spent > 0 && available > 0) {
            int counted = 0;
            for (int i = 0; i < buds.size(); i++) {
                assigned[i] = (int) ((long) spent * buds.get(i).available() / available);
                counted += assigned[i];
            }
            int cursor = (int) (level.getGameTime() / 20 % Math.max(1, buds.size()));
            while (counted < spent) {
                int i = cursor++ % buds.size();
                if (assigned[i] < buds.get(i).available()) { assigned[i]++; counted++; }
            }
        }
        synchronized (BY_LEVEL) {
            Map<Face, Entry> entries = BY_LEVEL.computeIfAbsent(level, ignored -> new HashMap<>());
            long now = level.getGameTime();
            entries.entrySet().removeIf(e -> e.getValue().untilTick() < now);
            for (int i = 0; i < buds.size(); i++) {
                GrowthNetwork.Bud bud = buds.get(i);
                if (bud.stage() == 4 || bud.potential() <= 0) continue;
                int basePotential = switch (bud.stage()) {
                    case 1 -> 24;
                    case 2 -> 16;
                    case 3 -> 8;
                    default -> 1;
                };
                float remainder = (bud.potential() - assigned[i]) / (float) basePotential;
                float chance = Math.clamp(remainder * (bud.calcite() ? .8F : 1F), .15F, 1F);
                Face face = new Face(bud.mother(), bud.face());
                Entry sameTick = entries.get(face);
                if (sameTick != null && sameTick.untilTick() == now + 30)
                    chance = Math.min(chance, sameTick.chance());
                entries.put(face, new Entry(chance, now + 30));
            }
        }
    }

    public static float growthChance(Level level, BlockPos mother, Direction face) {
        synchronized (BY_LEVEL) {
            Map<Face, Entry> entries = BY_LEVEL.get(level);
            if (entries == null) return 1F;
            Entry entry = entries.get(new Face(mother, face));
            return entry == null || entry.untilTick() < level.getGameTime() ? 1F : entry.chance();
        }
    }
}
