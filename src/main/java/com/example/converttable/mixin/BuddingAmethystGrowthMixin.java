package com.example.converttable.mixin;

import com.example.converttable.GrowthDrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Only connected, actively measured crystal faces get a slower vanilla growth event. */
@Mixin(BuddingAmethystBlock.class)
public abstract class BuddingAmethystGrowthMixin {
    @Redirect(method = "randomTick",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean convertTable$growthShare(ServerLevel level, BlockPos budPos, BlockState nextStage) {
        Direction face = nextStage.getValue(AmethystClusterBlock.FACING);
        BlockPos mother = budPos.relative(face.getOpposite());
        float chance = GrowthDrain.growthChance(level, mother, face);
        return (chance >= 1F || level.getRandom().nextFloat() < chance)
            && level.setBlockAndUpdate(budPos, nextStage);
    }
}
