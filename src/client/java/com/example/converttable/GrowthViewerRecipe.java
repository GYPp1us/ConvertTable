package com.example.converttable;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** A viewer-neutral snapshot of a bundled growth recipe; safe when no viewer is installed. */
public record GrowthViewerRecipe(Identifier identifier, ItemStack catalyst, ItemStack output, int cost) {
    public static List<GrowthViewerRecipe> all() {
        return GrowthRecipes.allRecipes().stream().map(recipe -> new GrowthViewerRecipe(
            recipe.id(), new ItemStack(recipe.catalyst()), new ItemStack(recipe.output()), recipe.cost())).toList();
    }

    public List<Component> lines() {
        return List.of(
            Component.translatable("jei.convert_table.growth.cost", cost),
            Component.translatable("jei.convert_table.growth.catalyst_not_consumed"),
            Component.translatable("jei.convert_table.growth.shared_network"),
            Component.translatable("jei.convert_table.growth.stations"));
    }
}
