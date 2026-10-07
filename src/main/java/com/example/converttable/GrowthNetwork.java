package com.example.converttable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Loaded crystal components, indexed once and invalidated only by their physical dependencies. */
public final class GrowthNetwork {
    public static final int UNKNOWN = 1;
    private static final Direction[] FACES = Direction.values();
    private static final Comparator<BlockPos> POSITION_ORDER = Comparator.comparingLong(BlockPos::asLong);
    private static final Map<Level, Cache> BY_LEVEL = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Snapshot EMPTY = new Snapshot(List.of(), 0, 0, 0, 0, 0,
        Set.of(), List.of(), List.of(), 0, 0, 0, 0, 0, 0);
    private static boolean initialized;

    /** Contents and extraction use microfactors: 1000 microfactors equal one growth factor. */
    public record Bud(BlockPos mother, BlockPos pos, Direction face, int stage,
                      long potential, long available, boolean calcite, boolean basalt) { }
    /** Stable topology identity with incrementally sampled buds and mineral contacts. */
    public static final class Snapshot {
        private final ArrayList<Bud> buds;
        private final List<Bud> budView;
        private final Map<BlockPos,Integer> budSlots = new HashMap<>();
        private final int mothers, conductors, flags;
        private int calciteMothers, basaltMothers;
        private final Set<BlockPos> nodes;
        private final ArrayList<BlockPos> pedestals;
        private final List<BlockPos> sources;
        private long available, potential;
        private long growthMean=1_000_000, growthMin=1_000_000, growthMax=1_000_000;
        private final long[] stageGrowth={1_000_000,1_000_000,1_000_000};
        private long coordinatorInterval=Long.MIN_VALUE;
        private BlockPos effectiveCoordinator;
        private final int[] stages;
        Snapshot(List<Bud> buds, int mothers, int conductors, int flags, int calciteMothers, int basaltMothers,
                 Set<BlockPos> nodes, List<BlockPos> pedestals, List<BlockPos> sources,
                 long available, long potential, int small, int medium, int large, int clusters) {
            this.buds = new ArrayList<>(buds);
            this.budView = Collections.unmodifiableList(this.buds);
            for (int index=0;index<buds.size();index++) budSlots.put(buds.get(index).pos(),index);
            this.mothers=mothers; this.conductors=conductors; this.flags=flags;
            this.calciteMothers=calciteMothers; this.basaltMothers=basaltMothers;
            this.nodes=nodes; this.pedestals=new ArrayList<>(pedestals); this.sources=sources;
            this.available=available; this.potential=potential;
            this.effectiveCoordinator=sources.isEmpty()?null:sources.getFirst();
            this.stages=new int[]{small,medium,large,clusters};
        }
        public List<Bud> buds() { return budView; }
        public int mothers() { return mothers; }
        public int conductors() { return conductors; }
        public int flags() { return flags; }
        public int calciteMothers() { return calciteMothers; }
        public int basaltMothers() { return basaltMothers; }
        public Set<BlockPos> nodes() { return nodes; }
        public List<BlockPos> pedestals() { return Collections.unmodifiableList(pedestals); }
        public List<BlockPos> sources() { return sources; }
        public long available() { return available; }
        public long potential() { return potential; }
        public long growthMean() { return growthMean; }
        public long growthMin() { return growthMin; }
        public long growthMax() { return growthMax; }
        public long stageGrowth(int stage) { return stage>=1 && stage<=3 ? stageGrowth[stage-1] : 0; }
        void recordGrowth(double sum,int count,double min,double max,double[] sums,int[] counts) {
            growthMean=count==0?0:Math.round(sum/count*1_000_000);
            growthMin=count==0?0:Math.round(min*1_000_000);
            growthMax=count==0?0:Math.round(max*1_000_000);
            for(int stage=0;stage<3;stage++) stageGrowth[stage]=counts[stage]==0?0:Math.round(sums[stage]/counts[stage]*1_000_000);
        }
        private void bud(Bud next) {
            Integer index=budSlots.get(next.pos());
            if (index == null) {
                if (next.stage()==0) return;
                budSlots.put(next.pos(),buds.size());
                buds.add(next);
            } else {
                Bud old=buds.set(index,next);
                available-=old.available(); potential-=old.potential();
                if(old.stage()>0) stages[old.stage()-1]--;
            }
            available+=next.available(); potential+=next.potential();
            if(next.stage()>0) stages[next.stage()-1]++;
        }
        private void pedestal(BlockPos pos, boolean present) {
            if (present && !pedestals.contains(pos)) { pedestals.add(pos); pedestals.sort(POSITION_ORDER); }
            else if (!present) pedestals.remove(pos);
        }
        public static Snapshot empty() {
            return EMPTY;
        }
        public int count(int stage) {
            return stage>=1 && stage<=4 ? stages[stage-1] : 0;
        }
        public boolean usable() { return effectiveCoordinator!=null; }
        public BlockPos coordinator() { return effectiveCoordinator; }
        private void sampleCoordinator(Level level) {
            if(level.isClientSide()) return;
            long interval=Math.floorDiv(level.getGameTime(),20);
            if(coordinatorInterval==interval) return;
            coordinatorInterval=interval;
            effectiveCoordinator=null;
            for(BlockPos source:sources) if(level.shouldTickBlocksAt(source)) { effectiveCoordinator=source;break; }
        }
    }
    private record Contact(Set<BlockPos> calcite, Set<BlockPos> basalt) { }
    private static long contactCount(Set<BlockPos> local, Set<BlockPos> global) {
        long count=global.size();
        for(BlockPos pos:local) if(!global.contains(pos)) count++;
        return count;
    }
    // Identity hashing is essential: hashing a component's whole node set at every dependency
    // insertion would turn the linear rebuild into quadratic work.
    private static final class Component {
        private final Snapshot snapshot;
        private final Set<BlockPos> dependencies;
        private final Set<Long> chunks;
        private final Map<BlockPos,Contact> localContacts;
        private final Set<BlockPos> conductors, calciteTouches, basaltTouches;
        Component(Snapshot snapshot, Set<BlockPos> dependencies, Set<Long> chunks,
                  Map<BlockPos,Contact> localContacts, Set<BlockPos> conductors,
                  Set<BlockPos> calciteTouches, Set<BlockPos> basaltTouches) {
            this.snapshot = snapshot;
            this.dependencies = dependencies;
            this.chunks = chunks;
            this.localContacts=localContacts; this.conductors=conductors;
            this.calciteTouches=calciteTouches; this.basaltTouches=basaltTouches;
        }
        Snapshot snapshot() { return snapshot; }
        Set<BlockPos> dependencies() { return dependencies; }
        Set<Long> chunks() { return chunks; }
        void update(Level level, BlockPos pos, BlockState before, BlockState after) {
            if (isBud(before) || isBud(after)) {
                for(Direction side:FACES) {
                    BlockPos mother=pos.relative(side);
                    Contact contact=localContacts.get(mother);
                    if(contact!=null) refreshBud(level,mother,side.getOpposite(),contact);
                }
            }
            if (before.is(GrowthBlocks.CATALYST) || after.is(GrowthBlocks.CATALYST))
                snapshot.pedestal(pos.immutable(),after.is(GrowthBlocks.CATALYST));
            if (!(mineral(before) || mineral(after))) return;
            boolean oldCalcite=!calciteTouches.isEmpty(), oldBasalt=!basaltTouches.isEmpty();
            boolean hadCalcite=calciteTouches.contains(pos), hadBasalt=basaltTouches.contains(pos);
            boolean touches=false;
            for(Direction side:FACES) touches|=conductors.contains(pos.relative(side));
            if(touches && after.is(Blocks.CALCITE)) calciteTouches.add(pos.immutable()); else calciteTouches.remove(pos);
            if(touches && after.is(Blocks.SMOOTH_BASALT)) basaltTouches.add(pos.immutable()); else basaltTouches.remove(pos);
            boolean globalChanged=hadCalcite!=calciteTouches.contains(pos) || hadBasalt!=basaltTouches.contains(pos);
            if(globalChanged) {
                for(BlockPos mother:localContacts.keySet()) refreshMother(level,mother,oldCalcite,oldBasalt);
            } else {
                for(Direction side:FACES) {
                    BlockPos mother=pos.relative(side);
                    if(localContacts.containsKey(mother)) refreshMother(level,mother,oldCalcite,oldBasalt);
                }
            }
        }
        private void refreshMother(Level level, BlockPos mother, boolean oldCalcite, boolean oldBasalt) {
            Contact old=localContacts.get(mother), next=localContact(level,mother);
            boolean beforeCalcite=!old.calcite().isEmpty()||oldCalcite, beforeBasalt=!old.basalt().isEmpty()||oldBasalt;
            boolean afterCalcite=!next.calcite().isEmpty()||!calciteTouches.isEmpty(), afterBasalt=!next.basalt().isEmpty()||!basaltTouches.isEmpty();
            snapshot.calciteMothers+=(afterCalcite?1:0)-(beforeCalcite?1:0);
            snapshot.basaltMothers+=(afterBasalt?1:0)-(beforeBasalt?1:0);
            localContacts.put(mother,next);
            for(Direction side:FACES) refreshBud(level,mother,side,next);
        }
        private void refreshBud(Level level, BlockPos mother, Direction face, Contact local) {
            BlockPos pos=mother.relative(face).immutable();
            int stage=level.hasChunkAt(pos)?stage(level.getBlockState(pos),face):0;
            Integer priorSlot=snapshot.budSlots.get(pos);
            if(stage==0 && priorSlot!=null && !snapshot.buds.get(priorSlot).mother().equals(mother)) return;
            long calcite=contactCount(local.calcite(),calciteTouches), basalt=contactCount(local.basalt(),basaltTouches);
            long potential=GrowthBudFactors.contained(stage,basalt), available=GrowthBudFactors.extractionLimit(stage,calcite);
            snapshot.bud(new Bud(mother,pos,face,stage,potential,available,calcite>0,basalt>0));
        }
    }
    private static final class Cache {
        final Map<BlockPos, Component> nodes = new HashMap<>();
        final Map<BlockPos, Set<Component>> dependencies = new HashMap<>();
        final Map<Long, Set<Component>> chunks = new HashMap<>();
        final Set<BlockPos> issuedSources = new HashSet<>(), issuedBuds = new HashSet<>();
        long budgetInterval = Long.MIN_VALUE;
        long rebuilds;

        void interval(long tick) {
            long interval = Math.floorDiv(tick, 20);
            if (budgetInterval != interval) {
                issuedSources.clear();
                issuedBuds.clear();
                budgetInterval = interval;
            }
        }

        void add(Component component) {
            for (BlockPos node : component.snapshot().nodes()) nodes.put(node, component);
            for (BlockPos pos : component.dependencies())
                dependencies.computeIfAbsent(pos, ignored -> new HashSet<>()).add(component);
            for (long chunk : component.chunks())
                chunks.computeIfAbsent(chunk, ignored -> new HashSet<>()).add(component);
            rebuilds++;
        }
        void remove(Level level, Component component) {
            for (BlockPos node : component.snapshot().nodes()) nodes.remove(node, component);
            for (BlockPos pos : component.dependencies()) {
                Set<Component> entries = dependencies.get(pos);
                if (entries != null && entries.remove(component) && entries.isEmpty()) dependencies.remove(pos);
            }
            for (long chunk : component.chunks()) {
                Set<Component> entries = chunks.get(chunk);
                if (entries != null && entries.remove(component) && entries.isEmpty()) chunks.remove(chunk);
            }
            GrowthDrain.clear(level, component.snapshot());
        }
    }

    private GrowthNetwork() { }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> invalidateChunk(level, chunk.getPos()));
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> invalidateChunk(level, chunk.getPos()));
    }

    public static Snapshot scan(Level level, BlockPos node) {
        if (level == null || !level.hasChunkAt(node)) return Snapshot.empty();
        Cache cache = BY_LEVEL.computeIfAbsent(level, ignored -> new Cache());
        Component cached = cache.nodes.get(node);
        if (cached != null) { cached.snapshot().sampleCoordinator(level); return cached.snapshot(); }
        if (!isNode(level.getBlockState(node))) return Snapshot.empty();
        Component component = rebuild(level, node);
        cache.add(component);
        component.snapshot().sampleCoordinator(level);
        return component.snapshot();
    }

    /** A block mutation invalidates only components which read this exact position. */
    public static void blockChanged(Level level, BlockPos pos, BlockState before, BlockState after) {
        if (before == after || !relevant(before) && !relevant(after)) return;
        if (isNode(before) || isNode(after)) {
            if (before.getBlock()!=after.getBlock()) invalidate(level,pos);
            return;
        }
        Cache cache=BY_LEVEL.get(level);
        if(cache==null) return;
        Set<Component> affected=cache.dependencies.get(pos);
        if(affected!=null) for(Component component:List.copyOf(affected)) component.update(level,pos,before,after);
    }

    public static void invalidate(Level level, BlockPos pos) {
        Cache cache = BY_LEVEL.get(level);
        if (cache == null) return;
        Set<Component> affected = cache.dependencies.get(pos);
        if (affected != null) for (Component component : List.copyOf(affected)) cache.remove(level, component);
    }

    /** Includes unloaded boundary chunks: a new neighbor can join a previously partial component. */
    public static void invalidateChunk(Level level, ChunkPos chunk) {
        Cache cache = BY_LEVEL.get(level);
        if (cache == null) return;
        Set<Component> affected = cache.chunks.get(chunk.pack());
        if (affected != null) for (Component component : List.copyOf(affected)) cache.remove(level, component);
    }

    static long rebuildCount(Level level) {
        Cache cache = BY_LEVEL.get(level);
        return cache == null ? 0 : cache.rebuilds;
    }

    /** A coordinator can settle at most once in a physical twenty-tick interval. */
    static boolean claimCycle(Level level, BlockPos source) {
        Cache cache = BY_LEVEL.computeIfAbsent(level, ignored -> new Cache());
        cache.interval(level.getGameTime());
        return cache.issuedSources.add(source);
    }

    /** A split, merge, unload or replacement coordinator cannot mint the same face's budget twice. */
    static long takeBudget(Level level, Snapshot network) {
        Cache cache = BY_LEVEL.computeIfAbsent(level, ignored -> new Cache());
        cache.interval(level.getGameTime());
        long budget = 0;
        for (Bud bud : network.buds()) if (cache.issuedBuds.add(bud.pos())) budget += bud.available();
        return budget;
    }

    private static Component rebuild(Level level, BlockPos start) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> nodes = new HashSet<>(), dependencies = new HashSet<>(), conductors = new HashSet<>();
        List<BlockPos> mothers = new ArrayList<>(), sources = new ArrayList<>();
        Set<BlockPos> pedestals = new HashSet<>();
        Map<BlockPos, Contact> motherContacts = new HashMap<>();
        Set<BlockPos> calciteTouches=new HashSet<>(), basaltTouches=new HashSet<>();
        int flags = 0;
        nodes.add(start.immutable());
        queue.add(start.immutable());
        while (!queue.isEmpty()) {
            BlockPos from = queue.removeFirst();
            dependencies.add(from);
            BlockState current = level.getBlockState(from);
            boolean mother = current.is(Blocks.BUDDING_AMETHYST);
            boolean conductor = current.is(Blocks.AMETHYST_BLOCK);
            if (mother) mothers.add(from);
            else if (conductor) conductors.add(from);
            else if (current.is(GrowthBlocks.CRYSTAL)) sources.add(from);
            Set<BlockPos> localCalcite = new HashSet<>(), localBasalt = new HashSet<>();
            for (Direction side : FACES) {
                BlockPos next = from.relative(side).immutable();
                dependencies.add(next);
                if (!level.hasChunkAt(next)) { flags |= UNKNOWN; continue; }
                BlockState state = level.getBlockState(next);
                if(state.is(Blocks.CALCITE)) localCalcite.add(next);
                if(state.is(Blocks.SMOOTH_BASALT)) localBasalt.add(next);
                if(conductor && state.is(Blocks.CALCITE)) calciteTouches.add(next);
                if(conductor && state.is(Blocks.SMOOTH_BASALT)) basaltTouches.add(next);
                if (state.is(GrowthBlocks.CATALYST)) pedestals.add(next);
                if (isNode(state) && nodes.add(next)) queue.addLast(next);
            }
            if (mother) motherContacts.put(from, new Contact(Set.copyOf(localCalcite), Set.copyOf(localBasalt)));
        }
        mothers.sort(POSITION_ORDER);
        sources.sort(POSITION_ORDER);
        List<Bud> buds = new ArrayList<>();
        long availableTotal = 0, potentialTotal = 0;
        int calciteCount = 0, basaltCount = 0;
        int[] counts = new int[4];
        for (BlockPos mother : mothers) {
            Contact local = motherContacts.get(mother);
            long calcite = contactCount(local.calcite(), calciteTouches);
            long basalt = contactCount(local.basalt(), basaltTouches);
            if (calcite>0) calciteCount++;
            if (basalt>0) basaltCount++;
            for (Direction side : FACES) {
                BlockPos budPos = mother.relative(side);
                if (!level.hasChunkAt(budPos)) continue;
                int stage = stage(level.getBlockState(budPos), side);
                if (stage == 0) continue;
                long potential = GrowthBudFactors.contained(stage,basalt);
                long available = GrowthBudFactors.extractionLimit(stage,calcite);
                buds.add(new Bud(mother, budPos.immutable(), side, stage, potential, available, calcite>0, basalt>0));
                counts[stage - 1]++;
                potentialTotal += potential;
                availableTotal += available;
            }
        }
        Snapshot snapshot = new Snapshot(List.copyOf(buds), mothers.size(), conductors.size(), flags,
            calciteCount, basaltCount, Set.copyOf(nodes), pedestals.stream().sorted(POSITION_ORDER).toList(),
            List.copyOf(sources), availableTotal, potentialTotal, counts[0], counts[1], counts[2], counts[3]);
        Set<Long> chunks = new HashSet<>();
        for (BlockPos dependency : dependencies) chunks.add(ChunkPos.pack(dependency.getX() >> 4, dependency.getZ() >> 4));
        return new Component(snapshot, Set.copyOf(dependencies), Set.copyOf(chunks),motherContacts,
            Set.copyOf(conductors),calciteTouches,basaltTouches);
    }

    public static boolean isConductor(BlockState state) {
        return state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.BUDDING_AMETHYST);
    }
    private static boolean isNode(BlockState state) { return isConductor(state) || state.is(GrowthBlocks.CRYSTAL); }
    private static boolean isBud(BlockState state) {
        return state.is(Blocks.SMALL_AMETHYST_BUD)||state.is(Blocks.MEDIUM_AMETHYST_BUD)
            ||state.is(Blocks.LARGE_AMETHYST_BUD)||state.is(Blocks.AMETHYST_CLUSTER);
    }
    private static boolean mineral(BlockState state) { return state.is(Blocks.CALCITE)||state.is(Blocks.SMOOTH_BASALT); }
    private static Contact localContact(Level level,BlockPos mother) {
        Set<BlockPos> calcite=new HashSet<>(),basalt=new HashSet<>();
        for(Direction face:FACES) {
            BlockPos pos=mother.relative(face);
            if(!level.hasChunkAt(pos)) continue;
            BlockState state=level.getBlockState(pos);
            if(state.is(Blocks.CALCITE)) calcite.add(pos.immutable());
            if(state.is(Blocks.SMOOTH_BASALT)) basalt.add(pos.immutable());
        }
        return new Contact(Set.copyOf(calcite),Set.copyOf(basalt));
    }
    private static boolean relevant(BlockState state) {
        return isNode(state) || state.is(GrowthBlocks.CATALYST) || state.is(Blocks.CALCITE)
            || state.is(Blocks.SMOOTH_BASALT) || state.is(Blocks.SMALL_AMETHYST_BUD)
            || state.is(Blocks.MEDIUM_AMETHYST_BUD) || state.is(Blocks.LARGE_AMETHYST_BUD)
            || state.is(Blocks.AMETHYST_CLUSTER);
    }

    /** Constant-size index lookups in a stable vein; a pedestal never bridges distinct veins. */
    public static CrystalTableBlockEntity findSource(Level level, BlockPos attachment) {
        if (level == null || !level.hasChunkAt(attachment)) return null;
        Snapshot selected = scan(level, attachment);
        for (Direction side : FACES) {
            BlockPos next = attachment.relative(side);
            if (!level.hasChunkAt(next)) continue;
            Snapshot candidate = scan(level, next);
            if (candidate.coordinator() != null && (selected.coordinator() == null
                    || POSITION_ORDER.compare(candidate.coordinator(), selected.coordinator()) < 0)) selected = candidate;
        }
        BlockPos coordinator = selected.coordinator();
        return coordinator != null && level.hasChunkAt(coordinator)
            && level.getBlockEntity(coordinator) instanceof CrystalTableBlockEntity crystal ? crystal : null;
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
}
