package com.aether.performance.mixins;

import com.aether.performance.diagnostics.CullingStats;
import com.aether.performance.rendering.RenderingConfig;
import com.aether.performance.utils.CameraUtil;
import com.aether.performance.utils.FailSafe;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleManager.class)
public class ParticleManagerMixin {

    @Unique
    private static int aether$counter;

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void aether$throttleParticles(ParticleEffect parameters, double x, double y, double z,
                                          double velocityX, double velocityY, double velocityZ,
                                          CallbackInfoReturnable<Particle> cir) {
        if (!RenderingConfig.particleThrottling || FailSafe.isDisabled("culling.particle")) {
            return;
        }
        try {
            double distSq = CameraUtil.distanceSq(x, y, z);
            double maxSq = RenderingConfig.particleDistanceSq;

            if (distSq > maxSq) {
                CullingStats.PARTICLES.increment();
                cir.setReturnValue(null);
                return;
            }

            // Oltre un quarto della distanza (metà in blocchi) mostra solo 1 particella su N
            int thinning = RenderingConfig.particleThinning;
            if (thinning > 1 && distSq > maxSq * 0.25 && (++aether$counter % thinning) != 0) {
                CullingStats.PARTICLES.increment();
                cir.setReturnValue(null);
            }
        } catch (Throwable t) {
            FailSafe.guard("culling.particle", t);
        }
    }
}
