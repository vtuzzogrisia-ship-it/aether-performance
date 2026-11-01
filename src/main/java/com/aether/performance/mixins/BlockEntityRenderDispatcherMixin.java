package com.aether.performance.mixins;

import com.aether.performance.diagnostics.CullingStats;
import com.aether.performance.rendering.RenderingConfig;
import com.aether.performance.utils.CameraUtil;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderManager.class)
public class BlockEntityRenderDispatcherMixin {

    /**
     * getRenderState viene chiamato solo quando il block entity sta per essere disegnato
     * (thread di rendering). Restituire null significa "non disegnare".
     * Non si interviene su get(): lo usa anche il thread che costruisce i chunk, e
     * tagliare lì farebbe sparire i block entity anche quando ci si avvicina.
     */
    @Inject(method = "getRenderState", at = @At("HEAD"), cancellable = true, require = 0)
    private void aether$cullFarBlockEntities(BlockEntity blockEntity, float tickProgress,
                                             @Coerce Object crumblingOverlay,
                                             CallbackInfoReturnable<BlockEntityRenderState> cir) {
        if (!RenderingConfig.blockEntityCulling || blockEntity == null) {
            return;
        }
        BlockPos pos = blockEntity.getPos();
        double distSq = CameraUtil.distanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (distSq > RenderingConfig.blockEntityDistanceSq) {
            CullingStats.BLOCK_ENTITIES.increment();
            cir.setReturnValue(null);
        }
    }
}
