package com.example.converttable;

import static com.example.converttable.GrowthScreenGraphics.*;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Stage distribution and distinct internal, exportable, and consumed growth quantities. */
public final class CrystalTableScreen extends AbstractContainerScreen<CrystalTableMenu> {
    public static final int WIDTH = 268, HEIGHT = 238;
    private static final Item[] STAGES = {Items.SMALL_AMETHYST_BUD, Items.MEDIUM_AMETHYST_BUD,
        Items.LARGE_AMETHYST_BUD, Items.AMETHYST_CLUSTER};

    public CrystalTableScreen(CrystalTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        inventoryLabelX = 53;
        inventoryLabelY = 145;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x88000000);
        frame(g, leftPos, topPos, imageWidth, imageHeight);
        g.fill(leftPos + 159, topPos + 26, leftPos + 160, topPos + 131, 0xff96919d);
        slots(g, menu, leftPos, topPos);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 7, TEXT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        g.text(font, tr("stages_heading"), 8, 27, TEXT, false);
        int[] counts = {menu.small(), menu.medium(), menu.large(), menu.clusters()};
        int maximum = 1;
        for (int count : counts) maximum = Math.max(maximum, count);
        // All four bars share a scale. Counts remain visible even when all buds are absent.
        g.horizontalLine(12, 148, 98, 0xff74707c);
        for (int i = 0; i < counts.length; i++) {
            int x = 15 + i * 35, h = 46 * counts[i] / maximum;
            g.fill(x, 49, x + 23, 98, 0xffb0aab8);
            if (h > 0) g.fill(x, 98 - h, x + 23, 98, i == 3 ? 0xff8d8896 : PURPLE);
            g.centeredText(font, Integer.toString(counts[i]), x + 11, 38, TEXT);
            g.item(new ItemStack(STAGES[i]), x + 3, 102);
            g.centeredText(font, tr("stage_short." + i), x + 11, 121, MUTED);
            if (contains(mouseX - leftPos, mouseY - topPos, x - 2, 37, 29, 95))
                g.setComponentTooltipForNextFrame(font, List.of(new ItemStack(STAGES[i]).getHoverName(),
                    tr("stage_count", counts[i]), tr(i == 3 ? "cluster_help" : "stage_help")), mouseX, mouseY);
        }
        text(g, tr("mothers_short", menu.mothers()), 166, 27, mouseX, mouseY);
        text(g, tr("conductors_short", menu.conductors()), 166, 39, mouseX, mouseY);
        text(g, tr("pedestals_short", menu.pedestals()), 166, 51, mouseX, mouseY);
        text(g, tr("intrinsic_short", menu.potential()), 166, 66, mouseX, mouseY);
        int growingBuds = menu.small() + menu.medium() + menu.large();
        meter(g, 166, 77, 94, menu.potential(), growingBuds * 25, PURPLE);
        text(g, tr("export_short", menu.rate()), 166, 86, mouseX, mouseY);
        meter(g, 166, 97, 94, menu.rate(), growingBuds * 2, EXPORT);
        text(g, tr("used_short", menu.spent()), 166, 106, mouseX, mouseY);
        meter(g, 166, 117, 94, menu.spent(), menu.rate(), USED);
        if (contains(mouseX - leftPos, mouseY - topPos, 166, 65, 94, 62))
            g.setComponentTooltipForNextFrame(font, List.of(tr("flow_help"),
                tr("boosts", menu.calciteMothers(), menu.basaltMothers())), mouseX, mouseY);
        String status = (menu.flags() & GrowthNetwork.SHARED) != 0 ? "shared"
            : (menu.flags() & GrowthNetwork.LIMIT) != 0 ? "limit"
            : (menu.flags() & GrowthNetwork.UNKNOWN) != 0 ? "unknown"
            : menu.hasPedestal() ? "linked" : "need_pedestal";
        Component statusText = tr(status);
        boolean delivering=menu.processing();
        progress(g,8,132,252,10,delivering?menu.progressTicks():0,menu.totalTicks(),0xffa8c4cf);
        Component cycle=delivering?tr("transfer_progress",percent(menu.progressTicks(),menu.totalTicks()),menu.progressRate()):statusText;
        g.centeredText(font,font.plainSubstrByWidth(cycle.getString(),248),134,133,TEXT);
        if (contains(mouseX - leftPos, mouseY - topPos, 8, 132, 252, 12))
            g.setComponentTooltipForNextFrame(font,List.of(statusText,tr("transfer_progress_help")),mouseX,mouseY);
    }

    private void text(GuiGraphicsExtractor g, Component text, int x, int y, int mx, int my) {
        g.text(font, font.plainSubstrByWidth(text.getString(), 94), x, y, TEXT, false);
        if (contains(mx - leftPos, my - topPos, x, y, 94, 10))
            g.setTooltipForNextFrame(font, text, mx, my);
    }
}

