package com.aether.performance.mixins;

import com.aether.performance.AetherPerformance;
import com.aether.performance.rendering.RenderingConfig;
import com.aether.performance.utils.FailSafe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void aether$renderOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (FailSafe.isDisabled("overlay")) {
            return;
        }
        try {
            // La misura dei frame serve sia all'overlay sia alla modalità AUTO
            if (!RenderingConfig.overlay && !RenderingConfig.auto) {
                return;
            }
            AetherPerformance.monitor().update();

            if (RenderingConfig.overlay && !MinecraftClient.getInstance().options.hudHidden) {
                AetherPerformance.monitor().renderOverlay(context);
            }
        } catch (Throwable t) {
            FailSafe.guard("overlay", t);
        }
    }
}
