package com.example.converttable;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Physical mouse packets and natural block-entity ticks, with stable partial-work screenshots. */
final class TimedConversionUiGameTest {
    private static final BlockPos TABLE = new BlockPos(0, 100, 0);
    private static final BlockPos INPUT = TABLE.west(2), OUTPUT = TABLE.east(2);
    private static final ConversionTableBlock[] BLOCKS = {
        ConversionTables.BLACK_GOLD, ConversionTables.END, ConversionTables.SCULK
    };
    private static final String[] NAMES = {"piglin", "end", "sculk"};
    private static final int[] DURATIONS = {80, 40, 20};

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static ConversionTableBlockEntity table(MinecraftServer game) {
        return (ConversionTableBlockEntity) game.overworld().getBlockEntity(TABLE);
    }

    private static Container container(MinecraftServer game, BlockPos pos) {
        return (Container) game.overworld().getBlockEntity(pos);
    }

    private static ConversionTableMenu menu(net.minecraft.client.Minecraft mc) {
        return (ConversionTableMenu) mc.player.containerMenu;
    }

    private static int birchCount(Container inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
            if (inventory.getItem(slot).is(Items.BIRCH_PLANKS)) count += inventory.getItem(slot).getCount();
        return count;
    }

    private static String label(String key) {
        return Component.translatable("gui.convert_table." + key).getString();
    }

    private static void click(ClientGameTestContext context, String key) {
        UiGameTestInput.clickWidget(context, widget -> widget.getMessage().getString().equals(label(key)));
    }

    private static void close(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            if (mc.gui.screen() instanceof ConversionTableScreen screen) screen.onClose();
        });
        context.waitTicks(2);
    }

    private static void capture(ClientGameTestContext context, String name) {
        // Hover help must not cover the actual progress/remaining-time text being inspected.
        context.getInput().setCursorPos(0, 0);
        context.runOnClient(mc -> mc.gui.toastManager().clear());
        context.waitTicks(3);
        context.runOnClient(mc -> mc.gui.toastManager().clear());
        context.takeScreenshot(TestScreenshotOptions.of(name));
    }

    /** Official tick stepping runs the actual world ticker; this test never calls convert/tick directly. */
    private static void step(ClientGameTestContext context, TestServerContext server, int ticks) {
        check(ticks > 0, "Cannot step an empty interval");
        server.runOnServer(game -> {
            check(game.tickRateManager().isFrozen(), "Timing fixture unexpectedly unfrozen");
            check(game.tickRateManager().stepGameIfPaused(ticks), "Could not start server tick stepping");
        });
        server.waitFor(game -> !game.tickRateManager().isSteppingForward(), ticks + 100);
        // Container packets and containerTick-dependent widgets may arrive on different client ticks.
        context.waitTicks(2);
    }

    private static void open(ClientGameTestContext context, TestServerContext server, int variant,
                             boolean linked, boolean blocked, int souls) {
        close(context);
        server.runOnServer(game -> {
            var level = game.overworld();
            var player = game.getPlayerList().getPlayers().getFirst();
            player.closeContainer();
            var current = ConversionExecutionGameTest.place(level, TABLE, BLOCKS[variant]);
            current.setItem(0, new ItemStack(Items.OAK_PLANKS, linked ? 1 : 4));
            if (variant < 2) current.setItem(1, new ItemStack(RecipeConfig.fuelItem(variant), 64));
            current.deaths = souls;
            if (linked) {
                for (var pos : new BlockPos[]{INPUT, OUTPUT}) {
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
                }
                container(game, INPUT).setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
                if (blocked) for (int slot = 0; slot < container(game, OUTPUT).getContainerSize(); slot++)
                    container(game, OUTPUT).setItem(slot, new ItemStack(Items.STONE, 64));
                check(current.links.toggle(level, TABLE, INPUT, Direction.UP, true) == 1, "Input link fixture failed");
                check(current.links.toggle(level, TABLE, OUTPUT, Direction.UP, false) == 1, "Output link fixture failed");
            }
            BLOCKS[variant].useWithoutItem(level.getBlockState(TABLE), level, TABLE, player,
                new BlockHitResult(Vec3.atCenterOf(TABLE), Direction.SOUTH, TABLE, false));
            check(player.containerMenu instanceof ConversionTableMenu, "Station did not open its actual menu");
        });
        context.waitFor(mc -> mc.gui.screen() instanceof ConversionTableScreen
            && menu(mc).variant == variant && menu(mc).totalTicks() == DURATIONS[variant]
            && menu(mc).getSlot(0).getItem().getCount() == (linked ? 1 : 4)
            && menu(mc).souls() == souls, 100);
        if (variant > 0) {
            if (variant == 2) UiGameTestInput.clickPanel(context, 320, 238, 220, 34);
            UiGameTestInput.clickTarget(context, Items.BIRCH_PLANKS);
            context.waitFor(mc -> menu(mc).previewTarget() == Items.BIRCH_PLANKS, 100);
            server.runOnServer(game -> check(table(game).target.equals(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.BIRCH_PLANKS)),
                "Target mouse click did not reach server"));
        }
        if (linked) {
            UiGameTestInput.clickPanel(context, 320, 238, 94, 34);
            context.waitFor(mc -> menu(mc).inputMode() == 1 && menu(mc).inputContainerCount() == 1
                && menu(mc).outputContainerCount() == 1, 100);
        }
        context.runOnClient(mc -> mc.gui.toastManager().clear());
    }

    private static void untouched(ConversionTableBlockEntity current, int input, int souls) {
        check(current.getItem(0).is(Items.OAK_PLANKS) && current.getItem(0).getCount() == input,
            "Input consumed before timed completion");
        check(current.getItem(2).isEmpty(), "Output appeared before timed completion");
        check(current.deaths == souls && current.phase == 0, "Souls/phase changed before timed completion");
        if (current.variantIndex() < 2)
            check(current.getItem(1).getCount() == 64, "Fuel consumed before timed completion");
    }

    private static void busy(ClientGameTestContext context) {
        context.waitFor(mc -> mc.gui.screen().children().stream().anyMatch(child ->
            child instanceof AbstractWidget widget && widget.visible && !widget.active
                && widget.getMessage().getString().equals(label("working"))), 100);
        context.runOnClient(mc -> check(mc.gui.screen().children().stream().noneMatch(child ->
            child instanceof AbstractWidget widget && widget.visible && widget.active
                && widget.getMessage().getString().equals(label("convert"))), "Manual start remained clickable during work"));
    }

    private static void manual(ClientGameTestContext context, TestServerContext server, int variant) {
        open(context, server, variant, false, false, 10);
        click(context, "convert");
        context.waitFor(mc -> menu(mc).processing() && menu(mc).progressTicks() == 0, 100);
        int half = DURATIONS[variant] / 2;
        step(context, server, half);
        context.waitFor(mc -> menu(mc).processing() && menu(mc).progressTicks() == half
            && menu(mc).progressTicks() < menu(mc).totalTicks(), 100);
        busy(context);
        server.runOnServer(game -> {
            var current = table(game);
            check(current.totalTicks() == DURATIONS[variant] && current.progressTicks() == half,
                "Wrong actual server batch duration/progress");
            untouched(current, 4, 10);
        });
        capture(context, "ui-timing-" + NAMES[variant]);
        // Physical input over the disabled button must neither restart nor fast-forward the job.
        UiGameTestInput.clickPanel(context, variant == 0 ? 176 : 320, variant == 0 ? 198 : 238,
            48, variant == 0 ? 74 : 117);
        server.runOnServer(game -> {
            check(table(game).progressTicks() == half && table(game).processing(), "Disabled button changed active work");
            untouched(table(game), 4, 10);
        });
        step(context, server, DURATIONS[variant] - half - 1);
        server.runOnServer(game -> {
            check(table(game).processing() && table(game).progressTicks() == DURATIONS[variant] - 1,
                "Batch completed before its final real tick");
            untouched(table(game), 4, 10);
        });
        step(context, server, 1);
        context.waitFor(mc -> !menu(mc).processing() && menu(mc).getSlot(0).getItem().isEmpty()
            && menu(mc).getSlot(2).getItem().getCount() == 4, 100);
        server.runOnServer(game -> {
            var current = table(game);
            check(!current.processing() && current.progressTicks() == 0, "Completed work retained a busy flag");
            check(current.getItem(0).isEmpty() && current.getItem(2).getCount() == 4, "Completed batch counts incorrect");
            if (variant == 0) {
                var group = RecipeConfig.server().groups().stream().filter(g -> g.tier() == 0
                    && g.items().contains(Items.OAK_PLANKS)).findFirst().orElseThrow();
                check(!current.getItem(2).is(Items.OAK_PLANKS) && group.items().contains(current.getItem(2).getItem()),
                    "Piglin random result outside its ordinary group");
                check(current.getItem(1).getCount() == 64 - RecipeConfig.setting("piglin", "cost_n"), "Piglin fee incorrect");
            } else {
                check(current.getItem(2).is(Items.BIRCH_PLANKS), "Selected output was ignored");
                if (variant == 1) check(current.getItem(1).getCount() == 63
                    && current.phase == RecipeConfig.setting("end", "fuel_charge") - RecipeConfig.setting("end", "charge_per_batch"),
                    "End completion fee/phase incorrect");
                else check(current.deaths == 9 && current.getItem(1).isEmpty(), "Sculk did not charge exactly one soul without fuel");
            }
        });
    }

    private static void continuousBlocked(ClientGameTestContext context, TestServerContext server) {
        open(context, server, 1, true, true, 0);
        click(context, "run.0");
        context.waitFor(mc -> menu(mc).running(), 100);
        step(context, server, 40);
        context.waitFor(mc -> menu(mc).running() && menu(mc).processing() && menu(mc).status() == 6
            && menu(mc).progressTicks() == 40, 100);
        busy(context);
        server.runOnServer(game -> {
            untouched(table(game), 1, 0);
            check(container(game, INPUT).getItem(0).getCount() == 8 && birchCount(container(game, OUTPUT)) == 0,
                "Blocked continuous job consumed linked resources");
        });
        capture(context, "ui-timing-output-blocked");
        step(context, server, 5);
        server.runOnServer(game -> {
            check(table(game).progressTicks() == 40 && table(game).status == 6, "Blocked completion did not wait at 100 percent");
            container(game, OUTPUT).clearContent();
        });
        step(context, server, 1);
        context.waitFor(mc -> !menu(mc).processing() && menu(mc).running(), 100);
        server.runOnServer(game -> {
            check(container(game, INPUT).getItem(0).isEmpty() && birchCount(container(game, OUTPUT)) == 8,
                "Completed continuous job did not resume after output room opened");
            check(table(game).getItem(0).getCount() == 1 && table(game).getItem(1).getCount() == 63,
                "Linked sample was consumed or completion fee incorrect");
            container(game, INPUT).setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
        });
        step(context, server, 20);
        context.waitFor(mc -> menu(mc).running() && menu(mc).processing() && menu(mc).progressTicks() == 20, 100);
        server.runOnServer(game -> check(container(game, INPUT).getItem(0).getCount() == 8
            && birchCount(container(game, OUTPUT)) == 8, "Next automatic batch consumed early"));
        step(context, server, 20);
        server.runOnServer(game -> check(container(game, INPUT).getItem(0).isEmpty()
            && birchCount(container(game, OUTPUT)) == 16 && table(game).running
            && table(game).getItem(0).getCount() == 1, "Second batch required another start click"));
        click(context, "run.1");
        context.waitFor(mc -> !menu(mc).running() && !menu(mc).processing(), 100);
    }

    private static void continuousSouls(ClientGameTestContext context, TestServerContext server) {
        open(context, server, 2, true, false, 0);
        click(context, "run.0");
        context.waitFor(mc -> menu(mc).running(), 100);
        step(context, server, 1);
        context.waitFor(mc -> menu(mc).running() && !menu(mc).processing() && menu(mc).status() == 5
            && menu(mc).souls() == 0 && menu(mc).soulCost() == 1, 100);
        capture(context, "ui-timing-souls-waiting");
        step(context, server, 25);
        server.runOnServer(game -> {
            check(!table(game).processing() && table(game).progressTicks() == 0 && table(game).deaths == 0,
                "Missing souls advanced or charged work");
            check(container(game, INPUT).getItem(0).getCount() == 8 && birchCount(container(game, OUTPUT)) == 0,
                "Missing souls consumed linked stock");
            table(game).deaths = 10;
        });
        step(context, server, 10);
        context.waitFor(mc -> menu(mc).processing() && menu(mc).progressTicks() == 10 && menu(mc).souls() == 10, 100);
        server.runOnServer(game -> check(container(game, INPUT).getItem(0).getCount() == 8,
            "Replenished souls caused premature conversion"));
        step(context, server, 10);
        context.waitFor(mc -> menu(mc).running() && !menu(mc).processing() && menu(mc).souls() == 9, 100);
        server.runOnServer(game -> check(container(game, INPUT).getItem(0).isEmpty()
            && birchCount(container(game, OUTPUT)) == 8 && table(game).getItem(1).isEmpty(),
            "Soul replenishment did not resume continuous work without a start click"));
        click(context, "run.1");
        context.waitFor(mc -> !menu(mc).running(), 100);
    }

    static void run(ClientGameTestContext context, TestServerContext server) {
        float previousRate = server.computeOnServer(game -> game.tickRateManager().tickrate());
        boolean previousFrozen = server.computeOnServer(game -> game.tickRateManager().isFrozen());
        server.runOnServer(game -> {
            check(RecipeConfig.enabled() && RecipeConfig.setting("sculk", "ordinary_souls_per_batch") == 1,
                "Timing UI test requires enabled execution and the default one-soul ordinary fee");
            game.tickRateManager().setTickRate(20);
            game.tickRateManager().setFrozen(true);
        });
        try {
            for (int variant = 0; variant < 3; variant++) manual(context, server, variant);
            continuousBlocked(context, server);
            continuousSouls(context, server);
            ConvertTable.LOGGER.info("TIMED_CONVERSION_UI_TEST_PASS: real mouse target/start/auto packets, natural80/40/20 server ticks, partial screenshots, disabled busy button, atomic completion fees, automatic linked repeat, 100-percent output wait and soul replenishment");
        } finally {
            close(context);
            server.runOnServer(game -> {
                game.tickRateManager().stopStepping();
                game.tickRateManager().setTickRate(previousRate);
                game.tickRateManager().setFrozen(previousFrozen);
            });
        }
    }
}
