package com.example.converttable.mixin;

import com.example.converttable.DeathExperienceReward;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes vanilla's reward; does not replace it or suppress XP drops/catalyst behaviour. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityExperienceMixin implements DeathExperienceReward {
    @Unique private int convertTable$deathExperience = -1;

    @Inject(method = "die", at = @At("HEAD"))
    private void convertTable$resetDeathReward(DamageSource source, CallbackInfo ci) {
        convertTable$deathExperience = -1;
    }

    @Inject(method = "getExperienceReward", at = @At("RETURN"))
    private void convertTable$observeDeathReward(ServerLevel level, Entity attacker,
                                                CallbackInfoReturnable<Integer> cir) {
        if (((LivingEntity) (Object) this).isDeadOrDying() && convertTable$deathExperience < 0)
            convertTable$deathExperience = Math.max(0, cir.getReturnValueI());
    }

    @Override public int convertTable$deathExperienceReward() {
        return convertTable$deathExperience;
    }
}
