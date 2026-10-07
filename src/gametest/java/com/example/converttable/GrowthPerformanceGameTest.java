package com.example.converttable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;

/** Paired real server ticks and targeted timings in a loaded 1,000-node physical component. */
final class GrowthPerformanceGameTest {
    private static volatile TickSample collecting;
    private static volatile long blackhole;
    private static final class TickSample {
        final MinecraftServer server;
        final List<Long> values = new ArrayList<>();
        long started;
        TickSample(MinecraftServer server) { this.server = server; }
    }
    private record Stats(int samples, double meanMs, double p50Ms, double p95Ms, double p99Ms, double maxMs) { }
    private static Stats stats(List<Long> values) {
        long[] sorted = values.stream().mapToLong(Long::longValue).sorted().toArray();
        if (sorted.length == 0) throw new AssertionError("No performance samples");
        return new Stats(sorted.length, Arrays.stream(sorted).average().orElseThrow() / 1e6,
            sorted[(int) Math.ceil(sorted.length * .50) - 1] / 1e6,
            sorted[(int) Math.ceil(sorted.length * .95) - 1] / 1e6,
            sorted[(int) Math.ceil(sorted.length * .99) - 1] / 1e6, sorted[sorted.length - 1] / 1e6);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    static void run(ClientGameTestContext context) {
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            var sample = collecting;
            if (sample != null && sample.server == server) sample.started = System.nanoTime();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var sample = collecting;
            if (sample != null && sample.server == server && sample.started != 0)
                sample.values.add(System.nanoTime() - sample.started);
        });
        context.runOnClient(mc -> {
            mc.options.renderDistance().set(4);
            mc.options.simulationDistance().set(4);
            mc.options.framerateLimit().set(30);
        });
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            server.runCommand("time set noon");
            server.runCommand("forceload add -16 -16 48 32");
            server.runCommand("tp @a 19.5 135 12.5 0 80");
            Fixture fixture = new Fixture();
            server.runOnServer(game -> fixture.create(game.overworld()));
            world.getConnection().waitForChunksRender();
            context.waitTicks(120);
            List<Long> baseline = new ArrayList<>(), active = new ArrayList<>();
            // ABBA reduces one-way JIT, world warmup and background-load bias.
            for (boolean enabled : new boolean[]{false, true, true, false}) {
                server.runOnServer(game -> fixture.enabled(game.overworld(), enabled));
                server.runCommand("kill @e[type=minecraft:item]");
                context.waitTicks(60);
                server.runOnServer(game -> collecting = new TickSample(game));
                context.waitTicks(220);
                server.runOnServer(game -> {
                    (enabled ? active : baseline).addAll(collecting.values);
                    collecting = null;
                });
            }
            var baselineStats = stats(baseline);
            var activeStats = stats(active);
            server.runOnServer(game -> {
                var level = game.overworld();
                fixture.enabled(level, true);
                var source = fixture.source(level);
                var network = source.snapshot();
                check(network.nodes().size() == 1000 && network.mothers() == 240 && network.sources().size() == 4
                    && network.pedestals().size() == 16 && network.count(1) == 480,
                    "Performance fixture does not match the requested 1k indexed nodes");
                long rebuilds = GrowthNetwork.rebuildCount(level);
                for (int warm = 0; warm < 20_000; warm++) blackhole = source.snapshot().potential();
                var queries = new ArrayList<Long>();
                for (int batch = 0; batch < 100; batch++) {
                    long start = System.nanoTime(), sum = 0;
                    for (int query = 0; query < 1000; query++) {
                        sum += source.snapshot().potential();
                        sum += GrowthNetwork.findSource(level, fixture.pedestals.get(query % 16)).getBlockPos().asLong();
                    }
                    queries.add(System.nanoTime() - start); blackhole = sum;
                }
                check(GrowthNetwork.rebuildCount(level) == rebuilds, "Stable queries rebuilt a 1k network");
                var bud = fixture.buds.getFirst();
                var updates = new ArrayList<Long>();
                for (int update = 0; update < 600; update++) {
                    var block = update % 2 == 0 ? Blocks.MEDIUM_AMETHYST_BUD : Blocks.SMALL_AMETHYST_BUD;
                    long start = System.nanoTime();
                    level.setBlockAndUpdate(bud, block.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP));
                    blackhole = source.snapshot().potential();
                    if (update >= 100) updates.add(System.nanoTime() - start);
                }
                check(source.snapshot() == network && GrowthNetwork.rebuildCount(level) == rebuilds,
                    "Ordinary bud growth rebuilt the indexed topology");
                var mineral = new BlockPos(4, 130, -1);
                var mineralUpdates = new ArrayList<Long>();
                for (int update = 0; update < 600; update++) {
                    long start = System.nanoTime();
                    level.setBlockAndUpdate(mineral, (update % 2 == 0 ? Blocks.CALCITE : Blocks.AIR).defaultBlockState());
                    check(source.snapshot().available() == (update % 2 == 0 ? 1440 : 960),
                        "Stacked mineral change did not refresh the whole vein");
                    if (update >= 100) mineralUpdates.add(System.nanoTime() - start);
                }
                check(source.snapshot() == network && GrowthNetwork.rebuildCount(level) == rebuilds,
                    "Global mineral count changes rebuilt topology");
                var rebuildTimings = new ArrayList<Long>();
                for (int rebuild = 0; rebuild < 100; rebuild++) {
                    long start = System.nanoTime();
                    GrowthNetwork.invalidate(level, fixture.sources.getFirst());
                    blackhole = source.snapshot().potential();
                    if (rebuild >= 20) rebuildTimings.add(System.nanoTime() - start);
                }
                var body = new com.google.gson.JsonObject();
                var gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
                body.addProperty("modVersion", VersionGate.version());
                body.addProperty("indexedNodes", 1000); body.addProperty("mothers", 240);
                body.addProperty("buds", 480); body.addProperty("controllers", 4); body.addProperty("activePedestals", 16);
                body.addProperty("tickMeasurement", "START_SERVER_TICK to END_SERVER_TICK; excludes idle sleep");
                body.addProperty("sampleOrder", "baseline-active-active-baseline; 60-tick warmup each, 220 requested ticks per window");
                body.add("baselineTick", gson.toJsonTree(baselineStats));
                body.add("activeTick", gson.toJsonTree(activeStats));
                body.addProperty("meanDeltaMspt", activeStats.meanMs() - baselineStats.meanMs());
                body.add("oneThousandCachedQueryPairs", gson.toJsonTree(stats(queries)));
                body.add("physicalBudStageUpdateIncludingVanilla", gson.toJsonTree(stats(updates)));
                body.add("stackedGlobalMineralUpdateIncludingVanilla", gson.toJsonTree(stats(mineralUpdates)));
                body.add("invalidateAndFullRebuild", gson.toJsonTree(stats(rebuildTimings)));
                body.addProperty("stableRebuilds", 0);
                try {
                    Path report = Path.of("../../reports/0.3.2/growth-performance.json").toAbsolutePath().normalize();
                    Files.createDirectories(report.getParent()); Files.writeString(report, gson.toJson(body));
                    ConvertTable.LOGGER.info("GROWTH_PERFORMANCE_1K_RESULT: {}", gson.toJson(body).replace('\n', ' '));
                } catch (java.io.IOException error) { throw new AssertionError("Cannot save performance report", error); }
            });
            ConvertTable.LOGGER.info("GROWTH_PERFORMANCE_1K_TEST_PASS: 1000 indexed nodes, 240 mothers, 480 buds, 4 controllers, 16 pedestals; paired real tick measurements and warmed cache/update/rebuild timings");
        } finally { collecting = null; }
    }

    private static final class Fixture {
        final List<BlockPos> sources = List.of(new BlockPos(0,130,0),new BlockPos(39,130,0),
            new BlockPos(0,130,24),new BlockPos(39,130,24));
        final List<BlockPos> pedestals = new ArrayList<>(), buds = new ArrayList<>();
        void create(ServerLevel level) {
            for (int x = 0; x < 40; x++) for (int z = 0; z < 25; z++) {
                var pos = new BlockPos(x, 130, z);
                boolean mother = x % 2 == 1 && z % 2 == 1;
                level.setBlockAndUpdate(pos, (mother ? Blocks.BUDDING_AMETHYST : Blocks.AMETHYST_BLOCK).defaultBlockState());
                if (mother) for (Direction face : new Direction[]{Direction.UP, Direction.DOWN}) {
                    buds.add(pos.relative(face));
                    level.setBlockAndUpdate(pos.relative(face), Blocks.SMALL_AMETHYST_BUD.defaultBlockState()
                        .setValue(AmethystClusterBlock.FACING, face));
                }
            }
            for (int x = 0; x < 8; x++) for (int z : new int[]{0,24}) pedestals.add(new BlockPos(2+x*5,131,z));
            level.setBlockAndUpdate(new BlockPos(2,130,-1), Blocks.CALCITE.defaultBlockState());
        }
        CrystalTableBlockEntity source(ServerLevel level) {
            return (CrystalTableBlockEntity) level.getBlockEntity(sources.getFirst());
        }
        void enabled(ServerLevel level, boolean active) {
            for (var pos : sources) level.setBlockAndUpdate(pos,
                (active ? GrowthBlocks.CRYSTAL : Blocks.AMETHYST_BLOCK).defaultBlockState());
            for (var pos : pedestals) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                if (!active) continue;
                level.setBlockAndUpdate(pos, GrowthBlocks.CATALYST.defaultBlockState());
                var pedestal = (CatalystPedestalBlockEntity) level.getBlockEntity(pos);
                pedestal.setItem(0, new ItemStack(Items.OAK_SAPLING));
                pedestal.setItem(10, new ItemStack(Items.OAK_LOG));
                for (int index = 0; index < pedestal.recipes().size(); index++)
                    if (pedestal.recipes().get(index).output() == Items.OAK_LOG) pedestal.selectRecipe(index);
                pedestal.toggleRunning();
            }
            if (active) {
                var network = source(level).snapshot();
                check(network.nodes().size() == 1000 && network.available() == 960,
                    "Active performance fixture was not fully loaded or calcite-boosted");
            }
        }
    }
}
