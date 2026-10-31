package com.aether.performance.mixins;

import com.aether.performance.AetherPerformance;
import com.aether.performance.rendering.RenderingConfig;
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
        if (!RenderingConfig.overlay) {
            return;
        }
        if (MinecraftClient.getInstance().options.hudHidden) {
            return;
        }
        AetherPerformance.monitor().update();
        AetherPerformance.monitor().renderOverlay(context);
    }
}
