package com.example.converttable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Flat 12px HUD glyphs: a crystal on a plinth, paired export arrows, and a growing shoot. */
final class GrowthPlacementIcons {
    private static final String[] PEDESTAL = {
        "............",
        ".....pp.....",
        "....pppp....",
        "...ppwwpp...",
        "....pppp....",
        ".....pp.....",
        "............",
        "..w......w..",
        "..wwwwwwww..",
        "...wwwwww...",
        "..ssssssss..",
        "............"
    };
    private static final String[] CALCITE = {
        "............",
        ".......w....",
        ".......ww...",
        "..wwwwwwww..",
        ".......ww...",
        ".......w....",
        "............",
        ".......w....",
        ".......ww...",
        "..wwwwwwww..",
        ".......ww...",
        ".......w...."
    };
    private static final String[] BASALT = {
        "............",
        ".....pp.....",
        "....pppp....",
        "...pppppp...",
        "..pp.pp.pp..",
        ".....pp.....",
        ".....pp.....",
        ".....pp.....",
        "...ssssss...",
        "..ssssssss..",
        "...ssssss...",
        "............"
    };

    private GrowthPlacementIcons() { }

    static void draw(GuiGraphicsExtractor g, ItemStack held, int x, int y) {
        var block = ((BlockItem) held.getItem()).getBlock();
        String[] pixels = block == Blocks.CALCITE ? CALCITE : block == Blocks.SMOOTH_BASALT ? BASALT : PEDESTAL;
        for (int row = 0; row < pixels.length; row++) {
            for (int column = 0; column < pixels[row].length(); column++) {
                int color = switch (pixels[row].charAt(column)) {
                    case 'p' -> 0xffc19bdf;
                    case 'w' -> 0xffeee8d7;
                    case 's' -> 0xffa4adb9;
                    default -> 0;
                };
                if (color != 0) g.fill(x + column, y + row, x + column + 1, y + row + 1, color);
            }
        }
    }
}
