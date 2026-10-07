package com.example.converttable.mixin;

import com.example.converttable.GrowthNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Includes vanilla random growth, player edits, pistons and mod-created block changes. */
@Mixin(LevelChunk.class)
public abstract class GrowthNetworkBlockMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void convertTable$invalidateGrowth(BlockPos pos, BlockState state, int flags,
                                               CallbackInfoReturnable<BlockState> result) {
        BlockState previous = result.getReturnValue();
        if (previous != null) GrowthNetwork.blockChanged(((LevelChunk) (Object) this).getLevel(), pos, previous, state);
    }
}
