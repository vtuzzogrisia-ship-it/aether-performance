package com.aether.performance.mixins;

import com.aether.performance.AetherPerformance;
import com.aether.performance.utils.FailSafe;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scala solo il modello dell'oggetto tenuto in mano in prima persona (come il mod "Item Size"
 * di Lunar/Badlion Client): puro effetto visivo, 100% = come in vanilla. Non cambia hitbox,
 * portata o altro: la scala si applica alla matrice prima che l'oggetto venga disegnato.
 */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;"
            + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;"
            + "Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At("HEAD"), require = 0)
    private void aether$scaleHeldItem(float tickProgress, MatrixStack matrices,
                                       OrderedRenderCommandQueue queue, ClientPlayerEntity player, int light,
                                       CallbackInfo ci) {
        if (FailSafe.isDisabled("pvp.item-size")) {
            return;
        }
        try {
            int percent = AetherPerformance.settings().itemSizePercent;
            if (percent == 100) {
                return;
            }
            float scale = percent / 100.0f;
            matrices.scale(scale, scale, scale);
        } catch (Throwable t) {
            FailSafe.guard("pvp.item-size", t);
        }
    }
}
