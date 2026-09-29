package com.example.converttable;

import java.util.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

/** Runs after real deaths, never cancels vanilla loot, XP or catalyst behaviour. */
public final class SculkDeathCharging {
    private static final Map<ServerLevel,Set<ConversionTableBlockEntity>> TABLES=new IdentityHashMap<>();
    private static final Map<ServerLevel,Map<UUID,Long>> SEEN=new IdentityHashMap<>();
    public static void initialize() {
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((be,level)->{
            if(be instanceof ConversionTableBlockEntity table&&table.variantIndex()==2)
                TABLES.computeIfAbsent(level,k->new HashSet<>()).add(table);
        });
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((be,level)->{
            if(be instanceof ConversionTableBlockEntity table && TABLES.containsKey(level))TABLES.get(level).remove(table);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{TABLES.clear();SEEN.clear();});
        ServerLivingEntityEvents.AFTER_DEATH.register(SculkDeathCharging::afterDeath);
    }
    static void afterDeath(LivingEntity entity,DamageSource source) {
        if(!RecipeConfig.enabled()||!(entity instanceof Mob)||!(entity.level() instanceof ServerLevel level))return;
        if(RecipeConfig.server().settings().getAsJsonObject("sculk").get("requires_player_kill").getAsBoolean()
            &&!(source.getEntity() instanceof Player))return;
        var seen=SEEN.computeIfAbsent(level,k->new HashMap<>());long now=level.getGameTime();
        seen.entrySet().removeIf(e->now-e.getValue()>1200);
        if(seen.putIfAbsent(entity.getUUID(),now)!=null)return;
        // The feet must actually be on the exposed sculk top, not floating in its vicinity.
        BlockPos ground=BlockPos.containing(entity.getX(),entity.getY()-0.01,entity.getZ());
        if(Math.abs(entity.getY()-(ground.getY()+1))>0.08||!level.hasChunkAt(ground)
            ||!level.getBlockState(ground).is(Blocks.SCULK))return;
        ConversionTableBlockEntity winner=null;int shortest=Integer.MAX_VALUE;
        for(var table:TABLES.getOrDefault(level,Set.of())) {
            if(table.isRemoved()||!level.hasChunkAt(table.getBlockPos())
                ||level.getBlockEntity(table.getBlockPos())!=table
                ||table.getBlockPos().distManhattan(ground)>TableRangeScanner.NETWORK_RADIUS)continue;
            // A fresh scan avoids granting charge through a bridge just broken this tick.
            int distance=TableRangeScanner.scan(level,table.getBlockPos(),true).surfaces().getOrDefault(ground,Integer.MAX_VALUE);
            if(distance<shortest || distance==shortest&&distance<Integer.MAX_VALUE&&before(table,winner)) {
                shortest=distance;winner=table;
            }
        }
        if(winner!=null) {
            winner.deaths=Math.min(RecipeConfig.setting("sculk","death_count_capacity"),winner.deaths+RecipeConfig.setting("sculk","death_count_per_mob"));
            winner.setChanged();
        }
    }
    private static boolean before(ConversionTableBlockEntity a,ConversionTableBlockEntity b) {
        if(b==null)return true;BlockPos x=a.getBlockPos(),y=b.getBlockPos();
        return x.getX()!=y.getX()?x.getX()<y.getX():x.getY()!=y.getY()?x.getY()<y.getY():x.getZ()<y.getZ();
    }
}
