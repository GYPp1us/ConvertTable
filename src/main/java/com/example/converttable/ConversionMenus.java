package com.example.converttable;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ConversionMenus {
    public static final MenuType<ConversionTableMenu> PIGLIN = register("piglin", 0);
    public static final MenuType<ConversionTableMenu> END = register("end", 1);
    public static final MenuType<ConversionTableMenu> SCULK = register("sculk", 2);
    private static MenuType<ConversionTableMenu> register(String name, int variant) {
        return Registry.register(BuiltInRegistries.MENU, ConvertTable.id(name),
            new MenuType<>((id, inventory) -> new ConversionTableMenu(id, inventory, variant), FeatureFlags.DEFAULT_FLAGS));
    }
    public static MenuType<ConversionTableMenu> type(int variant) {
        return variant == 0 ? PIGLIN : variant == 1 ? END : SCULK;
    }
    public static void initialize() { }
}
