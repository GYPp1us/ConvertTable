package com.example.converttable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/** A half-size output projection rotates above the reusable physical catalyst. */
public final class CatalystPedestalRenderer implements
        BlockEntityRenderer<CatalystPedestalBlockEntity, CatalystPedestalRenderer.State> {
    private final ItemModelResolver resolver;

    public CatalystPedestalRenderer(BlockEntityRendererProvider.Context context) {
        resolver = context.itemModelResolver();
    }

    public static final class State extends BlockEntityRenderState {
        private ItemStackRenderState sample = new ItemStackRenderState();
        private ItemStackRenderState catalyst = new ItemStackRenderState();
        private float yaw;
    }

    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(CatalystPedestalBlockEntity entity, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTick, cameraPos, crumbling);
        state.sample = new ItemStackRenderState();
        resolver.updateForTopItem(state.sample, entity.selectedOutput(), ItemDisplayContext.FIXED,
            entity.getLevel(), null, (int) entity.getBlockPos().asLong());
        state.catalyst = new ItemStackRenderState();
        resolver.updateForTopItem(state.catalyst, entity.getItem(0), ItemDisplayContext.FIXED,
            entity.getLevel(), null, (int) entity.getBlockPos().asLong());
        long tick = entity.getLevel() == null ? 0L : entity.getLevel().getGameTime();
        state.yaw = 42F + (tick + partialTick) * 0.8F;
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (!state.catalyst.isEmpty()) {
            pose.pushPose();
            pose.translate(.5F, .43F, .25F);
            pose.rotateDegrees(Axis.XP, 70F);
            pose.scale(.6F, .6F, .6F);
            state.catalyst.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        if (state.sample.isEmpty()) return;
        pose.pushPose();
        pose.translate(.5F, 1.25F, .5F);
        pose.rotateDegrees(Axis.YP, state.yaw);
        pose.rotateDegrees(Axis.XP, 15F);
        pose.rotateDegrees(Axis.ZP, 20F);
        // Keep the vanilla FIXED scale: an 8px block, half the former projection size.
        state.sample.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    @Override public boolean shouldRenderOffScreen() { return true; }
}
