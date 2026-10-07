package com.example.converttable.compat;

import com.example.converttable.GrowthViewerRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** JEI input indexing lets U discover a reusable catalyst without claiming it is consumed. */
public final class GrowthJeiCategory implements IRecipeCategory<GrowthViewerRecipe> {
    private final IDrawable icon;

    public GrowthJeiCategory(IDrawable icon) { this.icon = icon; }
    @Override public IRecipeType<GrowthViewerRecipe> getRecipeType() { return ConversionJeiPlugin.GROWTH_TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.convert_table.growth.title"); }
    @Override public int getWidth() { return 180; }
    @Override public int getHeight() { return 138; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public Identifier getIdentifier(GrowthViewerRecipe recipe) { return recipe.identifier(); }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, GrowthViewerRecipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(22, 7).setSlotName("catalyst").setStandardSlotBackground().add(recipe.catalyst())
            .addRichTooltipCallback((slot, tooltip) -> tooltip.add(
                Component.translatable("jei.convert_table.growth.catalyst_not_consumed")));
        builder.addInputSlot(66, 7).setSlotName("source").setStandardSlotBackground().add(recipe.source())
            .addRichTooltipCallback((slot, tooltip) -> tooltip.add(
                Component.translatable("jei.convert_table.growth.source_not_consumed")));
        builder.addOutputSlot(128, 7).setSlotName("output").setOutputSlotBackground().add(recipe.output());
    }

    @Override public void draw(GrowthViewerRecipe recipe, IRecipeSlotsView slots,
                               GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.text(font, "+", 48, 11, 0xff505050, false);
        graphics.text(font, ">", 99, 11, 0xff505050, false);
        int y = 36;
        for (Component line : recipe.lines()) {
            for (var wrapped : font.split(line, 176)) {
                graphics.text(font, wrapped, 2, y, 0xff454545, false);
                y += 11;
            }
        }
    }
}
