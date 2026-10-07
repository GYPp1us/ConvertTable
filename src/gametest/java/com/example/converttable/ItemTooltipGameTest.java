package com.example.converttable;

import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Render native stack tooltips, including styles inherited by existing stacks. */
final class ItemTooltipGameTest {
    static void runStandalone(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            mc.options.guiScale().set(3);
            mc.options.languageCode = "zh_cn";
            mc.getLanguageManager().setSelected("zh_cn");
        });
        var reload = context.computeOnClient(mc -> mc.reloadResourcePacks());
        context.waitFor(mc -> reload.isDone(), 600);
        reload.join();
        context.getInput().resizeWindow(1280, 720);
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            context.waitTicks(20);
            context.runOnClient(mc -> {
                for (long number : new long[]{0, 1, 63, 64, 63999, 64000, 65536, 4_000_000_001L, Long.MAX_VALUE}) {
                    if (mc.font.width(GrowthNumbers.rate(number)) > 94 || mc.font.width(GrowthNumbers.factors(number)) > 94)
                        throw new AssertionError("Growth quantity exceeds its fixed display width: " + number);
                }
                var advancements = mc.player.connection.getAdvancements();
                if (advancements.get(ConvertTable.id("black_gold")) == null || advancements.get(ConvertTable.id("end")) == null
                        || advancements.get(ConvertTable.id("sculk")) == null || advancements.get(ConvertTable.id("amethyst")) == null)
                    throw new AssertionError("Initial advancement guide or visible conversion achievements were hidden");
                if (advancements.get(ConvertTable.id("crystal")) != null)
                    throw new AssertionError("Secret crystal achievement was revealed before discovery");
                mc.gui.setScreen(new net.minecraft.client.gui.screens.advancements.AdvancementsScreen(advancements));
                advancements.setSelectedTab(advancements.get(ConvertTable.id("root")), true);
            });
            context.waitTicks(3);
            context.takeScreenshot(TestScreenshotOptions.of("advancement-guide-initial"));
            context.runOnClient(mc -> mc.gui.screen().onClose());
            server.runCommand("give @a minecraft:amethyst_shard");
            context.waitFor(mc -> {
                var adv = mc.player.connection.getAdvancements();
                var guide = adv.get(ConvertTable.id("amethyst"));
                var progress = guide == null ? null : adv.progress().get(guide);
                return progress != null && progress.isDone();
            }, 100);
            server.runOnServer(TableAdvancementsGameTest::verify);
            context.waitTicks(5);
            context.runOnClient(mc -> {
                var adv = mc.player.connection.getAdvancements();
                mc.gui.setScreen(new net.minecraft.client.gui.screens.advancements.AdvancementsScreen(adv));
                adv.setSelectedTab(adv.get(ConvertTable.id("root")), true);
            });
            context.waitTicks(3);
            context.takeScreenshot(TestScreenshotOptions.of("advancement-guide-completed"));
            context.runOnClient(mc -> mc.gui.screen().onClose());
            run(context);
        }
    }

    static void run(ClientGameTestContext context) {
        var items = context.computeOnClient(mc -> BuiltInRegistries.ITEM.stream()
            .filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(ConvertTable.MOD_ID))
            .map(ItemStack::new).toList());
        if (items.size() != 10) throw new AssertionError("Unexpected mod item count: " + items.size());
        for (ItemStack stack : items) {
            var style = stack.get(DataComponents.TOOLTIP_STYLE);
            if (style == null || !style.getNamespace().equals(ConvertTable.MOD_ID))
                throw new AssertionError("Mod item has no themed tooltip: " + stack);
        }
        context.runOnClient(mc -> mc.gui.setScreen(new Gallery(items)));
        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            context.runOnClient(mc -> ((Gallery) mc.gui.screen()).selected = index);
            context.waitTicks(3);
            context.takeScreenshot(TestScreenshotOptions.of("item-tooltip-"
                + BuiltInRegistries.ITEM.getKey(items.get(i).getItem()).getPath()));
        }
        context.runOnClient(mc -> mc.gui.screen().onClose());
        ConvertTable.LOGGER.info("ITEM_TOOLTIP_TEST_PASS: all {} registered mod items use native themed tooltips", items.size());
    }

    private static final class Gallery extends Screen {
        private final List<ItemStack> items;
        int selected;
        Gallery(List<ItemStack> items) { super(Component.literal("ConvertTable")); this.items = items; }
        @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
            g.fill(0, 0, width, height, 0xEE15131D);
            int start = Math.max(12, (width - items.size() * 24) / 2);
            for (int i = 0; i < items.size(); i++) {
                int left = start + i * 24;
                if (i == selected) g.fill(left - 2, 32, left + 18, 52, 0xFF514363);
                g.item(items.get(i), left, 34);
            }
            g.setTooltipForNextFrame(font, items.get(selected), 32, 95);
        }
        @Override public boolean isPauseScreen() { return false; }
    }
}
