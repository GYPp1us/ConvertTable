package com.example.converttable;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;

/** Apply theme colors after vanilla has applied its rarity color. */
final class ModItemTooltips {
    private ModItemTooltips() { }
    static void initialize() {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!id.getNamespace().equals(ConvertTable.MOD_ID) || lines.isEmpty()
                    || stack.has(DataComponents.CUSTOM_NAME)) return;
            int color = switch (id.getPath()) {
                case "black_gold_conversion_table" -> 0xEAC56D;
                case "end_conversion_table" -> 0xD3ACF5;
                case "sculk_conversion_table" -> 0x7CE4D0;
                case "crystal_table", "catalyst_pedestal" -> 0xC9BAFF;
                default -> 0xB5D0F5;
            };
            lines.set(0, lines.getFirst().copy().withStyle(style -> style.withColor(color).withBold(true)));
        });
    }
}
