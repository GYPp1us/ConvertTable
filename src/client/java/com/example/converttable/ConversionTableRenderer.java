package com.example.converttable;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Static shells are chunk models; independent animation groups use the block atlas. */
public final class ConversionTableRenderer implements BlockEntityRenderer<ConversionTableBlockEntity, ConversionTableRenderer.State> {
    private final Map<String, Mesh> resources;
    private Map<String, Mesh> stitched;

    public ConversionTableRenderer(BlockEntityRendererProvider.Context context) {
        resources = Map.of("black_gold", load("black_gold"), "end", load("end"), "sculk", load("sculk"));
    }

    public static final class State extends BlockEntityRenderState {
        private Mesh mesh;
        private float[] animationValues = new float[0];
        private float facingYaw;
    }

    private record Quad(Identifier sprite, float[][] positions, float[][] uv, float[] normal, int emission) { }
    private record Group(float[] pivot, AnimationTrack track, List<Quad> quads) { }
    private record Mesh(List<Group> groups) { }

    @Override
    public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(ConversionTableBlockEntity entity, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTick, cameraPos, crumbling);
        ensureStitched();
        state.mesh = stitched.get(entity.variant());
        state.facingYaw = switch (entity.getBlockState().getValue(ConversionTableBlock.FACING)) {
            case EAST -> -90F;
            case SOUTH -> 180F;
            case WEST -> 90F;
            default -> 0F;
        };
        long ticks = entity.getLevel() == null ? 0L : entity.getLevel().getGameTime();
        List<Group> groups = state.mesh.groups();
        if (state.animationValues.length != groups.size()) state.animationValues = new float[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            AnimationTrack track = groups.get(i).track();
            double seconds = ((ticks % Math.round(track.duration() * 20)) + partialTick) / 20.0;
            state.animationValues[i] = track.sample(seconds);
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.mesh == null) return;
        pose.pushPose();
        pose.translate(.5F, 0, .5F);
        pose.rotateDegrees(Axis.YP, state.facingYaw);
        pose.translate(-.5F, 0, -.5F);
        for (int i = 0; i < state.mesh.groups().size(); i++) {
            Group group = state.mesh.groups().get(i);
            float value = state.animationValues[i];
            pose.pushPose();
            if (group.track().rotates()) {
                float[] p = group.pivot();
                pose.translate(p[0] / 16F, p[1] / 16F, p[2] / 16F);
                pose.rotateDegrees(Axis.YP, value);
                pose.translate(-p[0] / 16F, -p[1] / 16F, -p[2] / 16F);
            } else {
                pose.translate(0, value / 16F, 0);
            }
            int light = state.lightCoords;
            float yaw = state.facingYaw + (group.track().rotates() ? value : 0);
            // Capture immutable geometry and values, never mutable state or the live entity.
            collector.submitCustomGeometry(pose, RenderTypes.cutoutMovingBlock(),
                    (matrix, consumer) -> draw(group, matrix, consumer, light, yaw));
            pose.popPose();
        }
        pose.popPose();
    }

    private static void draw(Group group, PoseStack.Pose pose, VertexConsumer consumer, int light, float yaw) {
        double radians = Math.toRadians(yaw), c = Math.cos(radians), s = Math.sin(radians);
        for (Quad quad : group.quads()) {
            int blockLight = Math.max((light >> 4) & 15, quad.emission());
            int packedLight = (light & 0x00F00000) | (blockLight << 4);
            float[] n = quad.normal();
            double x = n[0] * c + n[2] * s, z = -n[0] * s + n[2] * c;
            float shade = quad.emission() > 0 ? 1F : (float) (.6 * x * x + .8 * z * z + (n[1] > 0 ? 1 : .5) * n[1] * n[1]);
            int channel = Math.round(shade * 255);
            int color = 0xFF000000 | channel << 16 | channel << 8 | channel;
            for (int i = 0; i < 4; i++) {
                float[] p = quad.positions()[i];
                consumer.addVertex(pose, p[0] / 16F, p[1] / 16F, p[2] / 16F)
                        .setColor(color).setUv(quad.uv()[i][0], quad.uv()[i][1]).setLight(packedLight)
                        .setNormal(pose, n[0], n[1], n[2]);
            }
        }
    }

    /** New renderer instances on reload; resolve sprite UVs after stitching completes. */
    private void ensureStitched() {
        if (stitched != null) return;
        var sprites = Minecraft.getInstance().getAtlasManager();
        Map<String, Mesh> result = new HashMap<>();
        resources.forEach((variant, mesh) -> {
            List<Group> groups = new ArrayList<>();
            for (Group group : mesh.groups()) {
                List<Quad> quads = new ArrayList<>();
                for (Quad q : group.quads()) {
                    // SpriteId uses the texture location; getAtlasOrThrow instead expects
                    // the atlas definition ID ("blocks"), not "textures/atlas/blocks.png".
                    var sprite = sprites.get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, q.sprite()));
                    float[][] uv = new float[4][2];
                    for (int i = 0; i < 4; i++) {
                        uv[i][0] = sprite.getU(q.uv()[i][0]);
                        uv[i][1] = sprite.getV(q.uv()[i][1]);
                    }
                    quads.add(new Quad(q.sprite(), q.positions(), uv, q.normal(), q.emission()));
                }
                groups.add(new Group(group.pivot(), group.track(), List.copyOf(quads)));
            }
            result.put(variant, new Mesh(List.copyOf(groups)));
        });
        stitched = Map.copyOf(result);
        ConvertTable.LOGGER.info("Stitched conversion-table animation sprites: {} variants", stitched.size());
    }

    private static Mesh load(String variant) {
        Identifier source = ConvertTable.id("conversion_table/" + variant + ".json");
        List<Group> groups = new ArrayList<>();
        try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(source)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("format").getAsInt() != 2) throw new IllegalArgumentException("Unsupported mesh format");
            for (var element : root.getAsJsonArray("groups")) {
                var object = element.getAsJsonObject();
                boolean rotates = switch (object.get("kind").getAsString()) {
                    case "rotation" -> true;
                    case "position" -> false;
                    default -> throw new IllegalArgumentException("Unsupported animation channel");
                };
                var keys = object.getAsJsonArray("keyframes");
                var track = new AnimationTrack(rotates, object.get("duration").getAsFloat(), rows(keys, keys.size(), 2));
                List<Quad> quads = new ArrayList<>();
                for (var q : object.getAsJsonArray("quads")) {
                    var face = q.getAsJsonObject();
                    int emission = face.get("light_emission").getAsInt();
                    if (emission < 0 || emission > 15) throw new IllegalArgumentException("Invalid emitted light");
                    quads.add(new Quad(Identifier.parse(face.get("texture").getAsString()),
                            rows(face.getAsJsonArray("vertices"), 4, 3), rows(face.getAsJsonArray("uv"), 4, 2),
                            values(face.getAsJsonArray("normal"), 3), emission));
                }
                groups.add(new Group(values(object.getAsJsonArray("pivot"), 3), track, List.copyOf(quads)));
            }
        } catch (IOException | RuntimeException exception) {
            ConvertTable.LOGGER.error("Could not load animated conversion-table mesh {}", source, exception);
            groups.clear();
        }
        return new Mesh(List.copyOf(groups));
    }

    private static float[][] rows(JsonArray array, int count, int width) {
        if (array.size() != count) throw new IllegalArgumentException("Unexpected mesh row count");
        float[][] result = new float[count][];
        for (int i = 0; i < count; i++) result[i] = values(array.get(i).getAsJsonArray(), width);
        return result;
    }

    private static float[] values(JsonArray array, int count) {
        if (array.size() != count) throw new IllegalArgumentException("Unexpected mesh component count");
        float[] result = new float[count];
        for (int i = 0; i < count; i++) {
            result[i] = array.get(i).getAsFloat();
            if (!Float.isFinite(result[i])) throw new IllegalArgumentException("Non-finite mesh component");
        }
        return result;
    }
}
