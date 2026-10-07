package com.example.converttable;

import static com.example.converttable.GrowthScreenGraphics.*;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Physical catalyst, explicit target choice, and server measured production/transfer state. */
public final class CatalystPedestalScreen extends AbstractContainerScreen<CatalystPedestalMenu> {
    public static final int WIDTH = 320, HEIGHT = 238;
    public static final int TARGET_X = 192, TARGET_Y = 38, TARGET_COLUMNS = 5, PAGE_SIZE = 10;
    private final RecipeButton[] targets = new RecipeButton[PAGE_SIZE];
    private Button run, previous, next;
    private int page;
    private Item lastCatalyst;

    public CatalystPedestalScreen(CatalystPedestalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        inventoryLabelX = 8;
        inventoryLabelY = 145;
    }

    private void send(int id) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override protected void init() {
        super.init();
        run = addRenderableWidget(Button.builder(tr("start"), b -> send(0))
            .bounds(leftPos + 8, topPos + 96, 162, 18).build());
        run.setTooltip(Tooltip.create(tr("run_help")));
        for (int i = 0; i < PAGE_SIZE; i++) {
            final int cell = i;
            targets[i] = addRenderableWidget(new RecipeButton(leftPos + TARGET_X + i % 5 * 22,
                topPos + TARGET_Y + i / 5 * 22, b -> send(1000 + page * PAGE_SIZE + cell)));
        }
        previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; updateControls(); })
            .bounds(leftPos + 192, topPos + 83, 22, 16).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; updateControls(); })
            .bounds(leftPos + 278, topPos + 83, 22, 16).build());
        previous.setTooltip(Tooltip.create(Component.translatable("gui.convert_table.previous")));
        next.setTooltip(Tooltip.create(Component.translatable("gui.convert_table.next")));
        updateControls();
    }

    private void updateControls() {
        Item catalyst = menu.getSlot(0).getItem().getItem();
        if (catalyst != lastCatalyst) { page = 0; lastCatalyst = catalyst; }
        var recipes = menu.recipes();
        page = Math.clamp(page, 0, Math.max(0, (recipes.size() - 1) / PAGE_SIZE));
        run.setMessage(tr(menu.running() ? "pause" : "start"));
        run.active = menu.running() || menu.selectedRecipe() != null && menu.sourceReady();
        previous.active = page > 0;
        next.active = (page + 1) * PAGE_SIZE < recipes.size();
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = page * PAGE_SIZE + i;
            targets[i].recipe(index < recipes.size() ? recipes.get(index) : null, index == menu.selectedIndex());
        }
    }

    @Override protected void containerTick() { super.containerTick(); updateControls(); }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x88000000);
        frame(g, leftPos, topPos, imageWidth, imageHeight);
        g.fill(leftPos + 180, topPos + 25, leftPos + 181, topPos + 230, 0xff96919d);
        slots(g, menu, leftPos, topPos);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void label(GuiGraphicsExtractor g, Component value, int x, int y, int maxWidth, int color,
                       int mouseX, int mouseY) {
        g.text(font, font.plainSubstrByWidth(value.getString(), maxWidth), x, y, color, false);
        if (contains(mouseX - leftPos, mouseY - topPos, x, y, maxWidth, 10))
            g.setTooltipForNextFrame(font, value, mouseX, mouseY);
    }

    @Override protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 7, TEXT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        var recipe = menu.selectedRecipe();
        label(g, recipe == null ? tr("choose_target") : new ItemStack(recipe.output()).getHoverName(),
            69, 27, 69, TEXT, mouseX, mouseY);
        g.text(font, "+", 29, 33, TEXT, false);
        g.text(font, tr("cost", GrowthNumbers.count(menu.cost())), 69, 39, MUTED, false);
        if (recipe != null) g.item(new ItemStack(recipe.output()), 151, 29);
        if (contains(mouseX - leftPos, mouseY - topPos, 5, 26, 23, 23) && menu.getSlot(0).getItem().isEmpty())
            g.setTooltipForNextFrame(font, tr("catalyst_help"), mouseX, mouseY);
        if (contains(mouseX - leftPos, mouseY - topPos, 37, 26, 23, 23))
            g.setTooltipForNextFrame(font, tr("source_help"), mouseX, mouseY);
        String productionText=GrowthNumbers.production(menu.processing()?menu.progressRate():0,menu.cost());
        quantity(g,font,tr("production_label"),tr("item_rate_value",productionText).getString(),8,49,162,TEXT);
        if(contains(mouseX-leftPos,mouseY-topPos,8,49,162,10))
            g.setComponentTooltipForNextFrame(font,List.of(tr("production_help"),tr("completed_cycle",menu.producedPerSecond())),mouseX,mouseY);
        quantity(g,font,tr("export_label"),tr("factor_rate_value",GrowthNumbers.rate(menu.availablePerSecond())).getString(),8,60,162,MUTED);
        meter(g, 8, 70, 162, menu.availablePerSecond(), (menu.small() + menu.medium() + menu.large()) * 2, EXPORT);
        quantity(g,font,tr("used_label"),tr("factor_rate_value",GrowthNumbers.rate(menu.spentPerSecond())).getString(),8,78,162,MUTED);
        meter(g, 8, 88, 162, menu.spentPerSecond(), menu.availablePerSecond(), USED);
        if (contains(mouseX - leftPos, mouseY - topPos, 8, 60, 162, 34))
            g.setTooltipForNextFrame(font, tr("allocation_help"), mouseX, mouseY);
        g.text(font, tr("output_slots"), 8, 116, TEXT, false);
        g.text(font, tr("targets_heading"), 188, 26, TEXT, false);
        g.centeredText(font, (page + 1) + " / " + Math.max(1, (menu.recipes().size() + PAGE_SIZE - 1) / PAGE_SIZE), 246, 87, MUTED);
        if (menu.recipes().isEmpty()) g.textWithWordWrap(font, tr("catalyst_help"), 192, 44, 114, MUTED, false);
        drawConnections(g, mouseX, mouseY);
        Component nextProgress=recipe==null?tr("choose_target"):tr("next_item_short", factors(menu.progressUnits()),GrowthNumbers.count(menu.cost()));
        label(g, nextProgress, 188, 171, 124, TEXT, mouseX, mouseY);
        progress(g, 188, 183, 124, 5, menu.progressUnits(), menu.progressMaximum(), menu.status()==5?BLOCKED:PURPLE);
        if (contains(mouseX - leftPos, mouseY - topPos, 188, 170, 124, 20))
            g.setComponentTooltipForNextFrame(font,List.of(nextProgress,tr("next_item_progress",percent(menu.progressUnits(),menu.progressMaximum())),
                tr("growth_progress_help"),tr("credit_help")), mouseX, mouseY);
        label(g, tr("buffer_actual", GrowthNumbers.count(menu.buffered()), GrowthNumbers.count(menu.bufferCapacity())), 188, 194, 124, TEXT, mouseX, mouseY);
        meter(g, 188, 205, 124, menu.buffered(), menu.bufferCapacity(), menu.buffered() >= menu.bufferCapacity() ? BLOCKED : USED);
        String status = switch (menu.status()) {
            case 1 -> "copying";
            case 2 -> "no_catalyst";
            case 3 -> "no_crystal";
            case 4 -> "network_changed";
            case 5 -> "full";
            case 6 -> "no_buds";
            case 7 -> "invalid_catalyst";
            case 8 -> "choose_target";
            case 9 -> "waiting_factors";
            case 10 -> "no_source";
            default -> "idle";
        };
        label(g, tr(status), 188, 217, 124, menu.status() > 1 ? BLOCKED : MUTED, mouseX, mouseY);
    }

    private void drawConnections(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, tr("connections_heading"), 188, 106, TEXT, false);
        // Bits follow Direction.ordinal(): DOWN, UP, NORTH, SOUTH, WEST, EAST.
        String[] directions = {"down", "up", "north", "south", "west", "east"};
        for (int i = 0; i < directions.length; i++) {
            int x = 188 + i * 21;
            boolean linked = (menu.directionMask() & 1 << i) != 0;
            boolean blocked = (menu.blockedMask() & 1 << i) != 0;
            int color = blocked ? BLOCKED : linked ? OK : MUTED;
            g.fill(x, 119, x + 19, 140, 0xffaaa6af);
            g.centeredText(font, tr("direction_short." + directions[i]), x + 9, 122, TEXT);
            g.centeredText(font, blocked ? "!" : linked ? "+" : "-", x + 9, 131, color);
            if (contains(mouseX - leftPos, mouseY - topPos, x, 119, 19, 21))
                g.setComponentTooltipForNextFrame(font, List.of(tr("direction." + directions[i]),
                    tr(blocked ? "link_blocked" : linked ? "link_ready" : "link_absent")), mouseX, mouseY);
        }
        label(g, tr("containers_short", GrowthNumbers.count(menu.containerCount())), 188, 145, 124, TEXT, mouseX, mouseY);
        label(g, tr("space_short", GrowthNumbers.count(menu.freeSpace())), 188, 157, 124, MUTED, mouseX, mouseY);
        if(contains(mouseX-leftPos,mouseY-topPos,188,156,124,10))
            g.setTooltipForNextFrame(font,tr("space_short",menu.freeSpace()),mouseX,mouseY);
    }
}
