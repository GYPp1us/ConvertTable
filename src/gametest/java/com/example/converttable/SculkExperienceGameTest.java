package com.example.converttable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/** Real deaths and vanilla XP rewards, including an intentionally variable reward to detect rerolls. */
final class SculkExperienceGameTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static Zombie rewardMob(ServerLevel level, int reward, int[] calls) {
        return new Zombie(level) {
            @Override protected int getBaseExperienceReward(ServerLevel game) {
                calls[0]++;
                return reward;
            }
        };
    }

    private static void die(ServerLevel level, Mob mob, BlockPos ground) {
        mob.setNoAi(true);
        mob.setPos(ground.getX() + .5, ground.getY() + 1, ground.getZ() + .5);
        level.addFreshEntity(mob);
        mob.hurtServer(level, level.damageSources().genericKill(), 1000);
        check(mob.isDeadOrDying(), "XP test did not cause a real death");
    }

    static void run(MinecraftServer game) {
        var level = game.overworld();
        var origin = new BlockPos(-12, 101, 8);
        var other = origin.east(4);
        var ground = origin.east(2).below();
        var area = new AABB(origin).inflate(7);
        var first = ConversionExecutionGameTest.place(level, origin, ConversionTables.SCULK);
        var second = ConversionExecutionGameTest.place(level, other, ConversionTables.SCULK);
        for (int x = 0; x <= 4; x++) level.setBlockAndUpdate(origin.east(x).below(), Blocks.SCULK.defaultBlockState());
        try {
            int[] calls = {0};
            var one = rewardMob(level, 1, calls);
            die(level, one, ground);
            check(first.deaths == 32 && second.deaths == 0 && calls[0] == 1,
                "One XP did not provide 32 souls to just the nearest table");
            SculkDeathCharging.afterDeath(one, level.damageSources().genericKill());
            check(first.deaths == 32 && calls[0] == 1, "Repeated death processing recharged or rerolled XP");
            calls[0] = 0;
            die(level, rewardMob(level, 5, calls), ground);
            check(first.deaths == 192 && second.deaths == 0 && calls[0] == 1,
                "Five XP did not provide 160 additional souls from an automatic kill");

            die(level, rewardMob(level, 0, new int[1]), ground);
            var baby = (Pig) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(
                net.minecraft.resources.Identifier.parse("minecraft:pig")).create(level, EntitySpawnReason.COMMAND);
            baby.setBaby(true);
            die(level, baby, ground);
            check(first.deaths == 192, "Zero-XP or non-XP baby mobs supplied souls");

            // Vanilla already evaluates the reward for a player kill. A second call would return 14.
            int[] variableCalls = {0};
            var variable = new Zombie(level) {
                @Override protected int getBaseExperienceReward(ServerLevel server) {
                    return ++variableCalls[0] * 7;
                }
            };
            variable.setLastHurtByPlayer(game.getPlayerList().getPlayers().getFirst(), 100);
            int before = level.getEntitiesOfClass(ExperienceOrb.class, area).stream().mapToInt(ExperienceOrb::getValue).sum();
            die(level, variable, ground);
            int after = level.getEntitiesOfClass(ExperienceOrb.class, area).stream().mapToInt(ExperienceOrb::getValue).sum();
            check(variableCalls[0] == 1 && first.deaths == 416 && after - before == 7
                && !variable.wasExperienceConsumed(), "Soul calculation rerolled or consumed vanilla death XP");

            // The catalyst may consume vanilla XP for spread; observing the same reward must not interfere.
            var catalystPos = ground.south(2);
            level.setBlockAndUpdate(catalystPos, Blocks.SCULK_CATALYST.defaultBlockState());
            int[] catalystCalls = {0};
            var consumed = rewardMob(level, 5, catalystCalls);
            die(level, consumed, ground);
            check(catalystCalls[0] == 1 && consumed.wasExperienceConsumed() && first.deaths == 576,
                "Vanilla catalyst reward was rerolled, suppressed or double charged");
            level.setBlockAndUpdate(catalystPos, Blocks.AIR.defaultBlockState());

            first.deaths = RecipeConfig.setting("sculk", "death_count_capacity") - 1;
            die(level, rewardMob(level, Integer.MAX_VALUE, new int[1]), ground);
            check(first.deaths == RecipeConfig.setting("sculk", "death_count_capacity"),
                "Large XP rewards overflowed the stored soul capacity");
            var copy = (ConversionTableBlockEntity) BlockEntity.loadStatic(origin, first.getBlockState(),
                first.saveWithFullMetadata(game.registryAccess()), game.registryAccess());
            check(copy != null && copy.deaths == first.deaths, "XP-derived souls did not survive save/load");
            ConvertTable.LOGGER.info("SCULK_EXPERIENCE_TEST_PASS: 1XP=32 souls, automatic kills, zero-XP/baby exclusion, single ownership, duplicate guard, exact vanilla random reward, preserved XP/catalyst, overflow-safe capacity and save/load");
        } finally {
            level.setBlockAndUpdate(origin, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(other, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(ground.south(2), Blocks.AIR.defaultBlockState());
            for (int x = 0; x <= 4; x++) level.setBlockAndUpdate(origin.east(x).below(), Blocks.AIR.defaultBlockState());
            for (var mob : level.getEntitiesOfClass(Mob.class, area)) mob.discard();
            for (var orb : level.getEntitiesOfClass(ExperienceOrb.class, area)) orb.discard();
        }
    }
}
