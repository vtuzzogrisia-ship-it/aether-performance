package com.aether.performance.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

/** Utilità client-only per le distanze dalla telecamera. */
public final class CameraUtil {
    private CameraUtil() {
    }

    /** Distanza al quadrato dalla camera; 0 se non c'è ancora un mondo (così non si taglia nulla). */
    public static double distanceSq(double x, double y, double z) {
        Entity cam = MinecraftClient.getInstance().getCameraEntity();
        if (cam == null) {
            return 0.0;
        }
        double dx = x - cam.getX();
        double dy = y - cam.getY();
        double dz = z - cam.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
