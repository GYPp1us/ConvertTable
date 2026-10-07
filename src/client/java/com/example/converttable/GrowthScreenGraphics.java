package com.example.converttable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Vanilla pixel panels and honest, labelled snapshot meters shared by the growth screens. */
final class GrowthScreenGraphics {
    static final int TEXT = 0xff303136, MUTED = 0xff626570, PURPLE = 0xffa77abd;
    static final int EXPORT = 0xff6b92a0, USED = 0xff70609b, OK = 0xff537b59, BLOCKED = 0xffa37337;
    private GrowthScreenGraphics() { }

    static Component tr(String key, Object... args) {
        return Component.translatable("gui.convert_table.growth." + key, args);
    }

    static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xffc6c6c6);
        g.horizontalLine(x, x + w - 1, y, 0xffeeeeee);
        g.verticalLine(x, y, y + h - 1, 0xffeeeeee);
        g.horizontalLine(x, x + w - 1, y + h - 1, 0xff39353f);
        g.verticalLine(x + w - 1, y, y + h - 1, 0xff39353f);
        g.outline(x + 2, y + 2, w - 4, h - 4, 0xff77747d);
        g.fill(x + 8, y + 19, x + w - 8, y + 21, PURPLE);
    }

    static void slots(GuiGraphicsExtractor g, AbstractContainerMenu menu, int left, int top) {
        for (var slot : menu.slots) {
            int x = left + slot.x - 1, y = top + slot.y - 1;
            g.fill(x, y, x + 18, y + 18, 0xff8b8b8b);
            g.horizontalLine(x, x + 17, y, 0xff373737);
            g.verticalLine(x, y, y + 17, 0xff373737);
            g.horizontalLine(x, x + 17, y + 17, 0xffeeeeee);
            g.verticalLine(x + 17, y, y + 17, 0xffeeeeee);
        }
    }

    static void meter(GuiGraphicsExtractor g, int x, int y, int width, long value, long maximum, int color) {
        g.fill(x, y, x + width, y + 5, 0xff85818c);
        int fill = maximum <= 0 ? 0 : (int) Math.round(width * Math.clamp(value / (double) maximum, 0.0, 1.0));
        if (fill > 0) g.fill(x, y, x + fill, y + 5, color);
        // A visible baseline makes an empty meter distinguishable from a missing control.
        g.horizontalLine(x, x + width - 1, y + 5, 0xffe1dfe4);
    }

    /** The moving leading edge follows synchronized work; it never advances on a client timer. */
    static void progress(GuiGraphicsExtractor g, int x, int y, int width, int height,
                         long value, long maximum, int color) {
        g.fill(x, y, x + width, y + height, 0xffb2b0b6);
        int fill = maximum <= 0 ? 0 : (int) Math.round(width * Math.clamp(value / (double) maximum, 0.0, 1.0));
        if (fill > 0) g.fill(x, y, x + fill, y + height, color);
        if (fill > 0 && fill < width) g.fill(x + fill - 1, y, x + fill, y + height, 0xfff4f0f8);
        g.horizontalLine(x, x + width - 1, y + height, 0xffe1dfe4);
    }

    static int percent(long value, long maximum) {
        return maximum <= 0 ? 0 : (int) Math.floor(100.0 * Math.clamp(value / (double) maximum, 0.0, 1.0));
    }

    static String factors(long units) {
        return GrowthNumbers.factors(units);
    }

    static void quantity(GuiGraphicsExtractor g,net.minecraft.client.gui.Font font,Component label,String value,
                         int x,int y,int width,int color) {
        int labelWidth=font.width(label);
        g.text(font,label,x,y,color,false);
        String bounded=font.plainSubstrByWidth(value,Math.max(0,width-labelWidth-5));
        g.text(font,bounded,x+width-font.width(bounded),y,color,false);
    }

    static boolean contains(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** A real button keeps icon selection keyboard navigable and narratable. */
    static final class RecipeButton extends Button {
        private ItemStack item = ItemStack.EMPTY;
        private boolean selected;

        RecipeButton(int x, int y, OnPress action) {
            super(x, y, 20, 20, Component.empty(), action, DEFAULT_NARRATION);
        }

        void recipe(GrowthRecipes.Recipe recipe, boolean selected) {
            this.selected = selected;
            visible = active = recipe != null;
            item = recipe == null ? ItemStack.EMPTY : new ItemStack(recipe.output());
            if (recipe != null) {
                Component name = tr(selected ? "target_selected" : "target_option", item.getHoverName(), recipe.cost());
                setMessage(name);
                setTooltip(Tooltip.create(name));
            } else {
                setMessage(Component.empty());
                setTooltip(null);
                setFocused(false);
            }
        }

        @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY();
            g.fill(x, y, x + 20, y + 20, isHoveredOrFocused() ? 0xffded4e7 : 0xff9c99a3);
            g.outline(x, y, 20, 20, selected ? USED : 0xff66616c);
            if (selected) g.outline(x + 1, y + 1, 18, 18, PURPLE);
            if (isFocused()) g.outline(x - 1, y - 1, 22, 22, 0xfffaf6ff);
            g.item(item, x + 2, y + 2);
        }
    }
}
