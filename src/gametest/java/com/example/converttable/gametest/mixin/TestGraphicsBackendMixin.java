package com.example.converttable.gametest.mixin;

import net.minecraft.client.Options;
import net.minecraft.client.PreferredGraphicsApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric resets all game options in its test harness, including the graphics API.
 * Restore an explicitly requested test backend after that reset, before GPU creation.
 * This mixin is test-only and is never shipped with the mod.
 */
@Mixin(value = Options.class, priority = 900)
abstract class TestGraphicsBackendMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void selectTestBackend(CallbackInfo ci) {
        String backend = System.getProperty("convert_table.testBackend", "");
        if (!backend.isEmpty()) {
            ((Options)(Object)this).preferredGraphicsBackend().set(
                    backend.equals("vulkan") ? PreferredGraphicsApi.VULKAN : PreferredGraphicsApi.OPENGL);
        }
    }
}
