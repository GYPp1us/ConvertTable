package com.example.converttable;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** Registered crafting materials used by gameplay loot and recipes. */
public final class CraftMaterials {
    public static final Item BOUGHBOUND_REVERIE = register("boughbound_reverie");
    public static final Item STILLWATER_PALIMPSEST = register("stillwater_palimpsest");
    public static final Item UNBROKEN_COGNIZANCE = register("unbroken_cognizance");
    public static final Item UNWROUGHT_FACET = register("unwrought_facet");

    private CraftMaterials() { }
    public static void initialize() { }

    private static Item register(String path) {
        var id = ConvertTable.id(path);
        var key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, key,
            new Item(ModItemPresentation.apply(new Item.Properties().setId(key).stacksTo(64), path,
                "item.convert_table." + path)) {
                @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                                       Consumer<Component> tooltip, TooltipFlag flag) {
                    super.appendHoverText(stack, context, display, tooltip, flag);
                    tooltip.accept(Component.translatable("tooltip.convert_table." + path)
                        .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xAB9BC5).withItalic(true)));
                    tooltip.accept(Component.translatable("tooltip.convert_table." + path + ".use")
                        .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xB9BAC8)));
                }
            });
    }
}
