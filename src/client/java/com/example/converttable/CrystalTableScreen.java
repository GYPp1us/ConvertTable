package com.example.converttable;

import static com.example.converttable.GrowthScreenGraphics.*;
import java.util.ArrayList;
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
            int x = 15 + i * 35, h = (int) (46L * counts[i] / maximum);
            g.fill(x, 49, x + 23, 98, 0xffb0aab8);
            if (h > 0) g.fill(x, 98 - h, x + 23, 98, i == 3 ? 0xff8d8896 : PURPLE);
            g.centeredText(font, GrowthNumbers.count(counts[i]), x + 11, 38, TEXT);
            g.item(new ItemStack(STAGES[i]), x + 3, 102);
            g.centeredText(font, tr("stage_short." + i), x + 11, 121, MUTED);
            if (contains(mouseX - leftPos, mouseY - topPos, x - 2, 37, 29, 95))
                tip(g, stageHelp(i, counts[i]), mouseX, mouseY);
        }
        quantity(g,font,tr("mothers_label"),GrowthNumbers.count(menu.mothers()),166,27,94,TEXT);
        if (contains(mouseX-leftPos, mouseY-topPos, 166, 26, 94, 10))
            tip(g, List.of(tr("mothers_short", menu.mothers()), tr("mother_help")), mouseX, mouseY);
        quantity(g,font,tr("conductors_label"),GrowthNumbers.count(menu.conductors()),166,37,94,TEXT);
        if (contains(mouseX-leftPos, mouseY-topPos, 166, 36, 94, 10))
            tip(g, List.of(tr("conductors_short", menu.conductors()), tr("conductor_help")), mouseX, mouseY);
        quantity(g,font,tr("pedestals_label"),GrowthNumbers.count(menu.pedestals()),166,47,94,TEXT);
        if (contains(mouseX-leftPos, mouseY-topPos, 166, 46, 94, 10))
            tip(g, List.of(tr("pedestals_short", menu.pedestals()), tr("pedestal_help")), mouseX, mouseY);
        quantity(g,font,tr("growth_label"),activeBuds()==0?"—":GrowthNumbers.speed(menu.growthMean()),166,57,94,TEXT);
        if(contains(mouseX-leftPos,mouseY-topPos,166,56,94,10)) tip(g,List.of(
            tr("growth_current",GrowthNumbers.speed(menu.growthMean())),
            tr("growth_range",GrowthNumbers.speed(menu.growthMin()),GrowthNumbers.speed(menu.growthMax())),
            tr("growth_speed_help")),mouseX,mouseY);
        quantity(g,font,tr("intrinsic_label"),GrowthNumbers.rate(menu.potential()),166,69,94,TEXT);
        long growingBuds = (long) menu.small() + menu.medium() + menu.large();
        meter(g,166,80,94,menu.potential(),growingBuds*GrowthBudFactors.natural(1)
            +menu.clusters()*GrowthBudFactors.natural(4)+basaltUses(),PURPLE);
        quantity(g,font,tr("export_label"),GrowthNumbers.rate(menu.rate()),166,89,94,TEXT);
        meter(g, 166, 100, 94, menu.rate(), Math.max(growingBuds,menu.rate()), EXPORT);
        quantity(g,font,tr("used_label"),GrowthNumbers.rate(menu.spent()),166,109,94,TEXT);
        meter(g, 166, 120, 94, menu.spent(), menu.rate(), USED);
        if (contains(mouseX-leftPos, mouseY-topPos, 166, 68, 94, 18))
            tip(g, intrinsicHelp(), mouseX, mouseY);
        if (contains(mouseX-leftPos, mouseY-topPos, 166, 88, 94, 18))
            tip(g, supplyHelp(), mouseX, mouseY);
        if (contains(mouseX-leftPos, mouseY-topPos, 166, 108, 94, 18))
            tip(g, List.of(tr("used_short", GrowthNumbers.rate(menu.spent())),
                tr("allocation_source"), tr("allocation_unused", GrowthNumbers.rate(Math.max(0,menu.rate()-menu.spent()))),
                tr("allocation_recipients")), mouseX, mouseY);
        arrayIcon(g, Items.CALCITE, 214, calciteHelp(), mouseX, mouseY);
        arrayIcon(g, Items.SMOOTH_BASALT, 238, basaltHelp(), mouseX, mouseY);
        String status = (menu.flags() & GrowthNetwork.UNKNOWN) != 0 ? "unknown"
            : menu.hasPedestal() ? "linked" : "need_pedestal";
        Component statusText = tr(status);
        boolean delivering=menu.processing();
        progress(g,8,132,252,10,delivering?menu.progressTicks():0,menu.totalTicks(),0xffa8c4cf);
        Component cycle=delivering?tr("transfer_progress",percent(menu.progressTicks(),menu.totalTicks()),GrowthNumbers.rate(menu.progressRate())):statusText;
        g.centeredText(font,font.plainSubstrByWidth(cycle.getString(),248),134,133,TEXT);
        if (contains(mouseX - leftPos, mouseY - topPos, 8, 132, 252, 12))
            tip(g,List.of(statusText,tr("transfer_progress_help")),mouseX,mouseY);
    }

    private long activeBuds() { return (long)menu.small()+menu.medium()+menu.large(); }
    private long calciteUses() { return Math.max(0L,menu.rate()-activeBuds()); }
    private long baseFactors() {
        return menu.small()*GrowthBudFactors.natural(1)+menu.medium()*GrowthBudFactors.natural(2)
            +menu.large()*GrowthBudFactors.natural(3)+menu.clusters()*GrowthBudFactors.natural(4);
    }
    private long basaltUses() { return Math.max(0L,menu.potential()-baseFactors()); }

    private List<Component> stageHelp(int stage,int count) {
        long natural=GrowthBudFactors.natural(stage+1);
        if(stage==3) return List.of(new ItemStack(STAGES[stage]).getHoverName(),tr("stage_count",count),
            tr("stage_contents",natural,GrowthUnits.DIVISOR),tr("cluster_help"));
        return List.of(new ItemStack(STAGES[stage]).getHoverName(),tr("stage_count",count),
            tr("stage_factors",natural,GrowthUnits.DIVISOR,GrowthUnits.DIVISOR),tr("stage_boosts"),
            tr("stage_growth",GrowthNumbers.speed(menu.stageGrowth(stage+1))));
    }
    private List<Component> intrinsicHelp() {
        return List.of(tr("intrinsic_short",GrowthNumbers.rate(menu.potential())),
            tr("intrinsic_source"),tr("intrinsic_base",menu.small(),GrowthBudFactors.natural(1),
                menu.medium(),GrowthBudFactors.natural(2),menu.large(),GrowthBudFactors.natural(3),
                menu.clusters(),GrowthBudFactors.natural(4),GrowthUnits.DIVISOR),
            tr("intrinsic_basalt",basaltUses(),GrowthUnits.DIVISOR));
    }
    private List<Component> supplyHelp() {
        return List.of(tr("export_short",GrowthNumbers.rate(menu.rate())),tr("supply_source"),
            tr("supply_formula",activeBuds(),calciteUses(),GrowthUnits.DIVISOR,GrowthNumbers.rate(menu.rate())),
            tr("supply_loaded"));
    }
    private List<Component> calciteHelp() {
        return List.of(tr("calcite_title"),tr("calcite_effect",GrowthUnits.DIVISOR),tr("boost_placement"),
            tr("boost_coverage",menu.calciteMothers(),calciteUses()));
    }
    private List<Component> basaltHelp() {
        return List.of(tr("basalt_title"),tr("basalt_effect",GrowthUnits.DIVISOR),tr("boost_placement"),
            tr("boost_coverage",menu.basaltMothers(),basaltUses()));
    }
    private void arrayIcon(GuiGraphicsExtractor g,Item item,int x,List<Component> help,int mx,int my) {
        g.fill(x-1,1,x+17,19,0xffaaa6af);
        g.item(new ItemStack(item),x,2);
        if(contains(mx-leftPos,my-topPos,x-1,1,18,18)) tip(g,help,mx,my);
    }
    private void tip(GuiGraphicsExtractor g,List<Component> lines,int mx,int my) {
        var wrapped=new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for(Component line:lines) wrapped.addAll(font.split(line,Math.min(220,width-24)));
        g.setTooltipForNextFrame(font,wrapped,mx,my);
    }

    private static void meter(GuiGraphicsExtractor g, int x, int y, int width, long value, long maximum, int color) {
        GrowthScreenGraphics.meter(g, x, y, width,
            maximum <= 0 ? 0 : (int) Math.round(1_000_000 * Math.clamp(value / (double) maximum, 0.0, 1.0)),
            1_000_000, color);
    }
}
