package com.aether.performance.mixins;

import com.aether.performance.diagnostics.CullingStats;
import com.aether.performance.rendering.RenderingConfig;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderManager.class)
public class EntityRenderDispatcherMixin {

    /**
     * shouldRender decide se l'entità viene disegnata; x, y, z sono la posizione della camera.
     * Restituire false salta tutto il rendering di quell'entità.
     */
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void aether$cullFarEntities(Entity entity, Frustum frustum, double x, double y, double z,
                                        CallbackInfoReturnable<Boolean> cir) {
        boolean entities = RenderingConfig.entityCulling;
        boolean small = RenderingConfig.smallEntityCulling;
        boolean players = RenderingConfig.playerCulling;
        if (!entities && !small && !players) {
            return;
        }

        double distSq = entity.squaredDistanceTo(x, y, z);

        if (entity instanceof PlayerEntity) {
            // Solo se l'utente lo ha chiesto; il proprio giocatore (camera) non viene mai toccato
            if (players && distSq > RenderingConfig.playerDistanceSq && !aether$isCameraRider(entity)) {
                CullingStats.ENTITIES.increment();
                cir.setReturnValue(false);
            }
            return;
        }

        if (small && distSq > RenderingConfig.smallEntityDistanceSq
                && (entity instanceof ItemEntity || entity instanceof ExperienceOrbEntity)) {
            CullingStats.ENTITIES.increment();
            cir.setReturnValue(false);
            return;
        }

        if (!entities || distSq <= RenderingConfig.entityDistanceSq) {
            return;
        }
        if (aether$isCritical(entity)) {
            return;
        }
        CullingStats.ENTITIES.increment();
        cir.setReturnValue(false);
    }

    @Unique
    private static boolean aether$isCameraRider(Entity e) {
        return e.hasPassengers() || e.hasVehicle();
    }

    @Unique
    private static boolean aether$isCritical(Entity e) {
        return e instanceof EnderDragonEntity
                || e instanceof WitherEntity
                || e.hasCustomName()
                || e.hasPassengers()
                || e.hasVehicle();
    }
}
