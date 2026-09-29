package com.example.converttable;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class GrowthMenus {
    public static final MenuType<CrystalTableMenu> CRYSTAL = Registry.register(
        BuiltInRegistries.MENU, ConvertTable.id("crystal_table"),
        new MenuType<>(CrystalTableMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final MenuType<CatalystPedestalMenu> CATALYST = Registry.register(
        BuiltInRegistries.MENU, ConvertTable.id("catalyst_pedestal"),
        new MenuType<>(CatalystPedestalMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private GrowthMenus() { }
    public static void initialize() { }
}
