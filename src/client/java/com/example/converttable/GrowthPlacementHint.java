package com.example.converttable;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** A small placement acknowledgement next to the crosshair, only on usable crystal veins. */
public final class GrowthPlacementHint {
    private static Level cachedLevel;
    private static BlockPos cachedPos;
    private static Direction cachedFace;
    private static Item cachedItem;
    private static long cachedTick;
    private static boolean cachedValid;

    private GrowthPlacementHint() { }

    public static void initialize() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, ConvertTable.id("growth_placement"),
            (g, delta) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null || mc.player == null || mc.gui.screen() != null || mc.gui.hud.isHidden()
                        || mc.player.isSpectator() || !(mc.hitResult instanceof BlockHitResult hit)
                        || hit.getType() != HitResult.Type.BLOCK) return;
                ItemStack held = mc.player.getMainHandItem();
                if (!supported(held)) held = mc.player.getOffhandItem();
                if (!supported(held)) return;
                long tick = mc.level.getGameTime();
                if (cachedLevel != mc.level || !hit.getBlockPos().equals(cachedPos)
                        || hit.getDirection() != cachedFace || held.getItem() != cachedItem
                        || tick < cachedTick || tick - cachedTick >= 5) {
                    cachedValid = canPlaceHint(mc.level, hit.getBlockPos(), hit.getDirection(), held);
                    cachedLevel = mc.level;
                    cachedPos = hit.getBlockPos().immutable();
                    cachedFace = hit.getDirection();
                    cachedItem = held.getItem();
                    cachedTick = tick;
                }
                if (!cachedValid) return;
                int x = g.guiWidth() / 2 + 10, y = g.guiHeight() / 2 + 5;
                g.fill(x - 1, y - 1, x + 18, y + 18, 0xa8201929);
                GrowthPlacementIcons.draw(g, held, x + 2, y + 2);
                // Two links are the common symbol: the held mineral/pedestal joins this vein.
                int color = 0xffb3e4b1;
                g.fill(x + 16, y + 12, x + 21, y + 14, color);
                g.fill(x + 19, y + 10, x + 21, y + 14, color);
                g.fill(x + 19, y + 8, x + 24, y + 10, color);
                g.fill(x + 22, y + 8, x + 24, y + 12, color);
            });
    }

    private static boolean supported(ItemStack held) {
        if (!(held.getItem() instanceof BlockItem item)) return false;
        return item.getBlock() == GrowthBlocks.CATALYST || item.getBlock() == Blocks.CALCITE
            || item.getBlock() == Blocks.SMOOTH_BASALT;
    }

    public static boolean canPlaceHint(Level level, BlockPos conductor, Direction face, ItemStack held) {
        if (!supported(held) || !level.hasChunkAt(conductor)
                || !GrowthNetwork.isConductor(level.getBlockState(conductor))) return false;
        BlockPos placement = conductor.relative(face);
        if (!level.hasChunkAt(placement) || !level.getBlockState(placement).canBeReplaced()) return false;
        var source = GrowthNetwork.findSource(level, conductor);
        if (source == null) return false;
        GrowthNetwork.Snapshot network = level.isClientSide()
            ? GrowthNetwork.scan(level, source.getBlockPos()) : source.snapshot();
        return network.usable() && network.nodes().contains(conductor);
    }
}
