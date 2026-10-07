package com.example.converttable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

public final class CatalystPedestalBlock extends BaseEntityBlock {
    private static final VoxelShape BASE_SHAPE = box(2, 0, 2, 14, 8, 14);
    // Enclose the tilted 8px projection throughout its rotation around (8, 20, 8).
    private static final VoxelShape WITH_SAMPLE = Shapes.or(BASE_SHAPE, box(1, 14, 1, 15, 26, 15));
    public CatalystPedestalBlock(Properties properties) { super(properties); }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return level.getBlockEntity(pos) instanceof CatalystPedestalBlockEntity table
            && table.selectedRecipe() != null ? WITH_SAMPLE : BASE_SHAPE;
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level,
            BlockPos pos, CollisionContext context) {
        return BASE_SHAPE;
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CatalystPedestalBlockEntity(pos, state);
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
            : createTickerHelper(type, GrowthBlocks.CATALYST_ENTITY, CatalystPedestalBlockEntity::tick);
    }

    @Override protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CatalystPedestalBlockEntity table)
            player.openMenu(table);
        return InteractionResult.SUCCESS;
    }

    @Override protected InteractionResult useItemOn(
            ItemStack held, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof CatalystPedestalBlockEntity table
                && table.getItem(0).isEmpty() && GrowthRecipes.isCatalyst(held)) {
            if (!level.isClientSide()) {
                ItemStack sample = held.copyWithCount(1);
                table.setItem(0, sample);
                if (!player.isCreative()) held.shrink(1);
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CatalystPedestalBlockEntity table)
            player.openMenu(table);
        return InteractionResult.SUCCESS;
    }
}
