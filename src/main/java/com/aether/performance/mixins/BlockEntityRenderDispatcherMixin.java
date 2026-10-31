package com.aether.performance.mixins;

import com.aether.performance.rendering.RenderingConfig;
import com.aether.performance.utils.CameraUtil;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {

    /**
     * Il dispatcher ottiene il renderer con get(); se restituisce null il block entity
     * non viene disegnato. È il punto più stabile tra le versioni.
     */
    @Inject(method = "get", at = @At("HEAD"), cancellable = true, require = 0)
    private void aether$cullFarBlockEntities(BlockEntity blockEntity,
                                             CallbackInfoReturnable<BlockEntityRenderer<?>> cir) {
        if (!RenderingConfig.blockEntityCulling || blockEntity == null) {
            return;
        }
        BlockPos pos = blockEntity.getPos();
        double distSq = CameraUtil.distanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (distSq > RenderingConfig.blockEntityDistanceSq) {
            cir.setReturnValue(null);
        }
    }
}
