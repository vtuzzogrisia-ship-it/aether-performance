package com.aether.performance.mixins;

import com.aether.performance.AetherPerformance;
import com.aether.performance.diagnostics.CombatHud;
import com.aether.performance.rendering.RenderingConfig;
import com.aether.performance.settings.ModSettings;
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
        boolean hudHidden = MinecraftClient.getInstance().options.hudHidden;

        if (!FailSafe.isDisabled("overlay")) {
            try {
                ModSettings s = AetherPerformance.settings();
                // La misura dei frame serve all'overlay, alla modalità AUTO e all'HUD compatto PvP
                if (RenderingConfig.overlay || RenderingConfig.auto || s.pvpCompactHud) {
                    AetherPerformance.monitor().update();
                }
                if (RenderingConfig.overlay && !hudHidden) {
                    AetherPerformance.monitor().renderOverlay(context);
                }
            } catch (Throwable t) {
                FailSafe.guard("overlay", t);
            }
        }

        if (!hudHidden) {
            CombatHud.render(context, MinecraftClient.getInstance());
        }
    }
}
