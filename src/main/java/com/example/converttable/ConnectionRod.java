package com.example.converttable;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Two left clicks bind input; two right clicks bind output. */
public final class ConnectionRod {
    public static final Item ITEM = Registry.register(BuiltInRegistries.ITEM, ConvertTable.id("connection_rod"),
        new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ConvertTable.id("connection_rod"))).stacksTo(1)
            .component(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                Component.translatable("item.convert_table.connection_rod.input_help").withStyle(net.minecraft.ChatFormatting.GREEN),
                Component.translatable("item.convert_table.connection_rod.output_help").withStyle(net.minecraft.ChatFormatting.GOLD),
                Component.translatable("item.convert_table.connection_rod.clear_help"))))));
    private ConnectionRod() { }
    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(ITEM));
        AttackBlockCallback.EVENT.register((p, l, h, pos, face) -> click(p, l, h, pos, face, true));
        UseBlockCallback.EVENT.register((p, l, h, hit) -> click(p, l, h, hit.getBlockPos(), hit.getDirection(), false));
    }
    public static InteractionResult click(Player player, Level level, InteractionHand hand, BlockPos pos, Direction face, boolean input) {
        ItemStack rod = player.getItemInHand(hand);
        if (!rod.is(ITEM)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player.isSpectator() || !player.mayBuild() || !level.mayInteract(player, pos)) return InteractionResult.FAIL;
        String key = input ? "RodInput" : "RodOutput";
        var data = rod.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var machine = level.getBlockEntity(pos); var links = links(machine);
        if (input && machine instanceof CatalystPedestalBlockEntity) return feedback(player, "output_only", false);
        if (player.isShiftKeyDown() && links != null) {
            links.clear(input); forget(rod, key);
            if (input && machine instanceof ConversionTableBlockEntity table) table.inputMode = 0;
            machine.setChanged(); particles((ServerLevel) level, pos, pos, input);
            return feedback(player, input ? "clear_input" : "clear_output", true);
        }
        if (links == null && ContainerLinks.resolve(level, pos, face) == null) return feedback(player, "invalid", false);
        String dimension = level.dimension().identifier().toString();
        if (!dimension.equals(data.getStringOr(key+"Dimension", ""))) {
            remember(rod, key, dimension, pos, face); particles((ServerLevel) level, pos, pos, input);
            return feedback(player, input ? "first_input" : "first_output", true);
        }
        BlockPos first = BlockPos.of(data.getLongOr(key+"Pos", pos.asLong()));
        Direction firstFace;
        try { firstFace = Direction.valueOf(data.getStringOr(key+"Face", "UP")); }
        catch (IllegalArgumentException ignored) { firstFace = Direction.UP; }
        if (first.equals(pos)) { remember(rod, key, dimension, pos, face); return feedback(player, input ? "first_input" : "first_output", true); }
        if (!level.hasChunkAt(first)) return feedback(player, "unloaded", false);
        if (!level.mayInteract(player, first)) return InteractionResult.FAIL;
        var firstMachine = level.getBlockEntity(first); var firstLinks = links(firstMachine);
        int result;
        BlockPos storage;
        if (links != null && firstLinks == null) { result = links.toggle(level, pos, first, firstFace, input); storage = first; }
        else if (links == null && firstLinks != null && !(input && firstMachine instanceof CatalystPedestalBlockEntity)) {
            result = firstLinks.toggle(level, first, pos, face, input); storage = pos; machine = firstMachine; pos = first;
        } else { remember(rod, key, dimension, pos, face); return feedback(player, "pair", false); }
        if (result != 1 && result != 2) return feedback(player, result == 3 ? "limit" : result == 4 ? "conflict" : "range", false);
        forget(rod, key);
        if (input && machine instanceof ConversionTableBlockEntity table) table.inputMode = table.links.hasInputs() ? 1 : 0;
        machine.setChanged(); particles((ServerLevel) level, pos, storage, input);
        return feedback(player, result == 2 ? "removed" : input ? "connected_input" : "connected_output", true);
    }
    private static ContainerLinks links(BlockEntity be) {
        return be instanceof ConversionTableBlockEntity t ? t.links : be instanceof CatalystPedestalBlockEntity p ? p.links : null;
    }
    private static void remember(ItemStack rod, String key, String dimension, BlockPos pos, Direction face) {
        CustomData.update(DataComponents.CUSTOM_DATA, rod, tag -> {
            tag.putString(key+"Dimension", dimension); tag.putLong(key+"Pos", pos.asLong()); tag.putString(key+"Face", face.name());
        });
    }
    private static void forget(ItemStack rod, String key) {
        CustomData.update(DataComponents.CUSTOM_DATA, rod, tag -> { tag.remove(key+"Dimension"); tag.remove(key+"Pos"); tag.remove(key+"Face"); });
    }
    private static InteractionResult feedback(Player p, String key, boolean success) {
        if (p instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
            serverPlayer.sendSystemMessage(Component.translatable("item.convert_table.connection_rod."+key), true);
        return success ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
    }
    private static void particles(ServerLevel level, BlockPos a, BlockPos b, boolean input) {
        var particle = new DustParticleOptions(input ? 0x55e8b0 : 0xffb45c, 1.1f);
        int steps = Math.max(8, Math.min(64, a.distManhattan(b)*4));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i/steps;
            level.sendParticles(particle, a.getX()+.5+(b.getX()-a.getX())*t, a.getY()+.6+(b.getY()-a.getY())*t,
                a.getZ()+.5+(b.getZ()-a.getZ())*t, 1, .02, .02, .02, 0);
        }
    }
}
