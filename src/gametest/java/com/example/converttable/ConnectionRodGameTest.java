package com.example.converttable;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Real registered rod callbacks and the server transaction they configure. */
final class ConnectionRodGameTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static InteractionResult click(ServerPlayer player, ServerLevel level, BlockPos pos,
                                           Direction face, boolean input) {
        var before = level.getBlockState(pos);
        var menu = player.containerMenu;
        var result = input ? AttackBlockCallback.EVENT.invoker().interact(player, level,
            InteractionHand.MAIN_HAND, pos, face) : UseBlockCallback.EVENT.invoker().interact(player, level,
            InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false));
        check(level.getBlockState(pos).equals(before), "Rod callback changed/broke its endpoint");
        check(player.containerMenu == menu, "Rod callback opened a container GUI");
        return result;
    }

    private static InteractionResult pair(ServerPlayer player, ServerLevel level, BlockPos machine,
                                          BlockPos container, Direction containerFace, boolean input,
                                          boolean containerFirst) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ConnectionRod.ITEM));
        var first = click(player, level, containerFirst ? container : machine,
            containerFirst ? containerFace : Direction.UP, input);
        check(first.consumesAction(), "First rod click did not cancel vanilla attack/use");
        return click(player, level, containerFirst ? machine : container,
            containerFirst ? Direction.UP : containerFace, input);
    }

    private static void bind(ServerPlayer player, ServerLevel level, BlockPos machine, BlockPos container,
                             Direction face, boolean input, boolean containerFirst) {
        check(pair(player, level, machine, container, face, input, containerFirst).consumesAction(),
            "Rod pair failed: " + machine + " -> " + container + " input=" + input);
    }

    private static void clear(ServerPlayer player, ServerLevel level, BlockPos machine, boolean input) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ConnectionRod.ITEM));
        player.setShiftKeyDown(true);
        try { check(click(player, level, machine, Direction.UP, input).consumesAction(), "Shift rod clear failed"); }
        finally { player.setShiftKeyDown(false); }
    }

    private static Container barrel(ServerLevel level, BlockPos pos, List<BlockPos> placed) {
        placed.add(pos);
        level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
        return (Container) level.getBlockEntity(pos);
    }

    private static ConversionTableBlockEntity table(ServerLevel level, BlockPos pos,
                                                    ConversionTableBlock block, List<BlockPos> placed) {
        placed.add(pos);
        return ConversionExecutionGameTest.place(level, pos, block);
    }

    private static void fill(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) container.setItem(i, new ItemStack(Items.STONE, 64));
    }

    private static int count(Container container, Item item) {
        int result = 0;
        for (int i = 0; i < container.getContainerSize(); i++)
            if (container.getItem(i).is(item)) result += container.getItem(i).getCount();
        return result;
    }

    static void run(MinecraftServer game) {
        var level = game.overworld();
        var player = game.getPlayerList().getPlayers().getFirst();
        ItemStack hand = player.getMainHandItem().copy();
        boolean shift = player.isShiftKeyDown();
        List<BlockPos> placed = new ArrayList<>();
        try {
            player.setShiftKeyDown(false);
            rolesAndLimits(game, level, player, placed);
            atomicExports(game, level, player, placed);
            ConvertTable.LOGGER.info("CONNECTION_ROD_GAME_TEST_PASS: registered left/right callbacks, both endpoint orders, no vanilla break/GUI, double chest roles, 8 total/16 range, toggle/clear, NBT faces, atomic linked and slot exports, no sculk fuel and legacy fuel return; random-pool coverage reported separately");
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, hand);
            player.setShiftKeyDown(shift);
            for (BlockPos pos : placed.reversed()) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    private static void rolesAndLimits(MinecraftServer game, ServerLevel level, ServerPlayer player,
                                       List<BlockPos> placed) {
        var origin = new BlockPos(24, 104, 24);
        var machine = table(level, origin, ConversionTables.END, placed);
        var inputPos = origin.east(2);
        var outputPos = origin.west(2);
        barrel(level, inputPos, placed);
        barrel(level, outputPos, placed);
        bind(player, level, origin, inputPos, Direction.DOWN, true, true);
        check(machine.inputMode == 1 && machine.inputLinks().size() == 1, "Left rod pair did not enable linked input");
        bind(player, level, origin, outputPos, Direction.WEST, false, false);
        check(machine.outputLinks().size() == 1, "Right rod pair did not add output");
        var restored = (ConversionTableBlockEntity) BlockEntity.loadStatic(origin, machine.getBlockState(),
            machine.saveWithFullMetadata(game.registryAccess()), game.registryAccess());
        check(restored != null, "Linked table did not reload");
        restored.setLevel(level);
        check(restored.inputMode == 1 && restored.inputLinks().size() == 1 && restored.outputLinks().size() == 1
            && restored.inputLinks().getFirst().face() == Direction.DOWN
            && restored.outputLinks().getFirst().face() == Direction.WEST, "Saved links lost roles or clicked faces");
        check(pair(player, level, origin, inputPos, Direction.UP, false, false) == InteractionResult.FAIL,
            "Same container was accepted for input and output");
        check(machine.inputLinks().size() == 1 && machine.outputLinks().size() == 1, "Role conflict mutated valid links");
        bind(player, level, origin, inputPos, Direction.DOWN, true, false);
        check(machine.inputLinks().isEmpty() && machine.inputMode == 0, "Toggling last input failed to restore slot mode");
        bind(player, level, origin, inputPos, Direction.DOWN, true, false);
        clear(player, level, origin, true);
        check(machine.inputLinks().isEmpty() && machine.inputMode == 0 && machine.outputLinks().size() == 1,
            "Input clear removed output links or retained linked mode");
        clear(player, level, origin, false);
        check(machine.outputLinks().isEmpty(), "Output clear failed");

        // A double chest is one endpoint, including when the opposite half is clicked.
        var leftPos = origin.north(3);
        var left = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH)
            .setValue(ChestBlock.TYPE, ChestType.LEFT);
        var rightPos = ChestBlock.getConnectedBlockPos(leftPos, left);
        placed.add(leftPos); placed.add(rightPos);
        level.setBlock(leftPos, left, 2);
        level.setBlock(rightPos, left.setValue(ChestBlock.TYPE, ChestType.RIGHT), 2);
        bind(player, level, origin, leftPos, Direction.UP, true, false);
        check(machine.inputLinks().size() == 1 && machine.inputLinks().getFirst().container().getContainerSize() == 54,
            "Double chest was split into two inventories");
        check(pair(player, level, origin, rightPos, Direction.UP, false, true) == InteractionResult.FAIL,
            "Other double-chest half bypassed input/output conflict");
        bind(player, level, origin, rightPos, Direction.UP, true, true);
        check(machine.inputLinks().isEmpty(), "Clicking the second chest half duplicated instead of toggling the link");
        clear(player, level, origin, true);

        var atLimit = origin.east(16);
        var tooFar = origin.east(17);
        barrel(level, atLimit, placed); barrel(level, tooFar, placed);
        bind(player, level, origin, atLimit, Direction.WEST, false, true);
        check(pair(player, level, origin, tooFar, Direction.WEST, false, false) == InteractionResult.FAIL,
            "Rod accepted a container outside distance 16");
        check(machine.outputLinks().size() == 1, "Range rejection changed valid output links");
        clear(player, level, origin, false);
        for (int i = 1; i <= 8; i++) {
            var pos = origin.south(i);
            barrel(level, pos, placed);
            bind(player, level, origin, pos, Direction.UP, i <= 4, (i & 1) == 0);
        }
        var ninth = origin.west(3);
        barrel(level, ninth, placed);
        check(pair(player, level, origin, ninth, Direction.UP, false, true) == InteractionResult.FAIL,
            "Rod accepted a ninth link across combined roles");
        check(machine.inputLinks().size() == 4 && machine.outputLinks().size() == 4, "Total link limit changed valid endpoints");
        clear(player, level, origin, true); clear(player, level, origin, false);

        // Black-gold uses its input link automatically; the pedestal supports output only.
        var piglinPos = origin.above(3);
        var piglin = table(level, piglinPos, ConversionTables.BLACK_GOLD, placed);
        bind(player, level, piglinPos, inputPos, Direction.UP, true, false);
        check(piglin.inputMode == 1 && piglin.inputLinks().size() == 1, "Black-gold linked input was not enabled");
        clear(player, level, piglinPos, true);
        check(piglin.inputMode == 0, "Black-gold input clear did not restore its slot");
        var pedestalPos = origin.above(5);
        placed.add(pedestalPos);
        level.setBlockAndUpdate(pedestalPos, GrowthBlocks.CATALYST.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ConnectionRod.ITEM));
        check(click(player, level, pedestalPos, Direction.UP, true) == InteractionResult.FAIL,
            "Pedestal accepted an input-role rod click");
        bind(player, level, pedestalPos, outputPos, Direction.UP, false, true);
        check(((CatalystPedestalBlockEntity)level.getBlockEntity(pedestalPos)).outputContainers().size() == 1,
            "Pedestal output pair failed");
    }

    private static void atomicExports(MinecraftServer game, ServerLevel level, ServerPlayer player,
                                      List<BlockPos> placed) {
        var origin = new BlockPos(24, 104, -24);
        var machine = table(level, origin, ConversionTables.END, placed);
        var sourcePos = origin.east(2);
        var destinationPos = origin.west(2);
        var source = barrel(level, sourcePos, placed);
        var destination = barrel(level, destinationPos, placed);
        bind(player, level, origin, sourcePos, Direction.WEST, true, false);
        bind(player, level, origin, destinationPos, Direction.EAST, false, true);
        ConversionExecutionGameTest.target(machine, Items.BIRCH_PLANKS);
        machine.matchMode = 0;
        machine.setItem(0, new ItemStack(Items.OAK_PLANKS));
        machine.setItem(1, new ItemStack(Items.CHORUS_FRUIT, 2));
        source.setItem(0, new ItemStack(Items.OAK_PLANKS, 12));
        fill(destination);
        check(!ConversionExecutionGameTest.commit(machine,false) && machine.status == 6 && source.getItem(0).getCount() == 12
            && machine.getItem(0).getCount() == 1 && machine.getItem(1).getCount() == 2 && machine.phase == 0,
            "Full linked output consumed source/sample/fuel/phase");
        destination.clearContent();
        check(ConversionExecutionGameTest.commit(machine,false) && source.getItem(0).isEmpty() && machine.getItem(0).getCount() == 1
            && count(destination, Items.BIRCH_PLANKS) == 12, "Linked output commit lost or duplicated materials");
        int charge = RecipeConfig.setting("end", "charge_per_batch");
        int fuelCharge = RecipeConfig.setting("end", "fuel_charge");
        check(machine.getItem(1).getCount() == 1 && machine.phase == fuelCharge - charge,
            "Linked batch fuel was charged incorrectly");
        machine.clearContent(); machine.inputMode = 0; machine.phase = 0;
        destination.clearContent(); source.setItem(0, new ItemStack(Items.OAK_PLANKS, 3));
        machine.setItem(0, new ItemStack(Items.OAK_PLANKS, 5));
        machine.setItem(1, new ItemStack(Items.CHORUS_FRUIT));
        check(ConversionExecutionGameTest.commit(machine,false) && count(destination, Items.BIRCH_PLANKS) == 5 && machine.getItem(2).isEmpty()
            && source.getItem(0).getCount() == 3, "Slot-mode output binding did not export only its device input");

        machine = table(level, origin, ConversionTables.SCULK, placed);
        bind(player, level, origin, destinationPos, Direction.EAST, false, false);
        machine.inputMode = 0; machine.phase = 0; machine.deaths = 7;
        destination.clearContent();
        ConversionExecutionGameTest.target(machine, Items.BIRCH_PLANKS);
        machine.setItem(0, new ItemStack(Items.OAK_PLANKS, 5));
        check(ConversionExecutionGameTest.commit(machine,false) && count(destination, Items.BIRCH_PLANKS) == 5 && machine.phase == 0
            && machine.deaths == 6 && machine.getItem(1).isEmpty(), "Sculk ordinary export did not consume exactly one soul or required fuel");

        var random = RecipeConfig.server().advanced().stream().filter(RecipeCatalog.Advanced::random).findFirst().orElse(null);
        if (random == null) {
            ConvertTable.LOGGER.info("CONNECTION_ROD_RANDOM_POOL_TEST_SKIP: no enabled random output pool; optional recipe items are unavailable in this instance");
        } else {
            machine.clearContent(); machine.deaths = random.deaths() + 7;
            ConversionExecutionGameTest.target(machine, random.outputs().getLast().getItem());
            machine.setItem(0, random.input().copy()); machine.setItem(3, random.catalyst().copy());
            fill(destination);
            check(!ConversionExecutionGameTest.commit(machine,false) && machine.status == 6 && machine.pendingOutput != null,
                "Random advanced output was not locked before blocked export");
            var pending = machine.pendingOutput;
            for (int i = 0; i < 8; i++) {
                check(!ConversionExecutionGameTest.commit(machine,false) && machine.status == 6 && pending.equals(machine.pendingOutput)
                    && ItemStack.matches(machine.getItem(0), random.input())
                    && ItemStack.matches(machine.getItem(3), random.catalyst())
                    && machine.deaths == random.deaths() + 7 && machine.phase == 0 && machine.getItem(1).isEmpty(),
                    "Blocked random export rerolled or spent ingredients/deaths/fuel");
            }
            var restored = (ConversionTableBlockEntity) BlockEntity.loadStatic(origin, machine.getBlockState(),
                machine.saveWithFullMetadata(game.registryAccess()), game.registryAccess());
            check(restored != null && pending.equals(restored.pendingOutput), "Blocked random choice did not survive save/load");
            destination.clearContent();
            check(ConversionExecutionGameTest.commit(machine,false) && count(destination, BuiltInRegistries.ITEM.getValue(pending)) == random.output().getCount()
                && machine.getItem(0).isEmpty() && machine.getItem(3).isEmpty() && machine.getItem(2).isEmpty()
                && machine.deaths == 7 && machine.phase == 0 && machine.pendingOutput == null,
                "Random export commit changed its locked output or charged incorrectly");
            ConvertTable.LOGGER.info("CONNECTION_ROD_RANDOM_POOL_TEST_PASS: {} output pool, eight blocked retries, save/load lock, exact ingredients/deaths and linked export",random.id());
        }

        // Upgrading an old save returns the hidden fuel stack rather than deleting or stranding it.
        machine.phase = 9; machine.setItem(1, new ItemStack(Items.CHORUS_FRUIT, 7));
        int before = dropped(level, origin, Items.CHORUS_FRUIT);
        ConversionTableBlockEntity.tick(level, origin, machine.getBlockState(), machine);
        check(machine.getItem(1).isEmpty() && machine.phase == 0 && dropped(level, origin, Items.CHORUS_FRUIT) - before == 7,
            "Legacy sculk fuel was not returned on tick");
    }

    private static int dropped(ServerLevel level, BlockPos pos, Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
            .filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum();
    }
}
