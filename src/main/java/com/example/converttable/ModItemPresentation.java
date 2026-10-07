package com.example.converttable;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemLore;

/** Native tooltip skins apply in every inventory and recipe viewer. */
final class ModItemPresentation {
    private ModItemPresentation() { }

    static Item.Properties apply(Item.Properties properties, String path, String nameKey) {
        String theme = switch (path) {
            case "black_gold_conversion_table" -> "black_gold";
            case "end_conversion_table" -> "end";
            case "sculk_conversion_table" -> "sculk";
            case "crystal_table", "catalyst_pedestal" -> "crystal";
            default -> "arcane";
        };
        int color = switch (theme) {
            case "black_gold" -> 0xEAC56D;
            case "end" -> 0xD3ACF5;
            case "sculk" -> 0x7CE4D0;
            case "crystal" -> 0xC9BAFF;
            default -> 0xB5D0F5;
        };
        properties.component(DataComponents.TOOLTIP_STYLE, ConvertTable.id(theme))
            .component(DataComponents.ITEM_NAME, Component.translatable(nameKey)
                .withStyle(Style.EMPTY.withColor(color).withBold(true)));
        if (path.endsWith("_conversion_table") || path.equals("crystal_table") || path.equals("catalyst_pedestal"))
            properties.component(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("tooltip.convert_table." + path)
                    .withStyle(Style.EMPTY.withColor(0xB9BAC8).withItalic(false)))));
        return properties;
    }
}
