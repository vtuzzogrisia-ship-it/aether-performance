package com.aether.performance.diagnostics;

import com.aether.performance.AetherPerformance;
import com.aether.performance.rendering.RenderingConfig;
import com.aether.performance.settings.ModSettings;
import net.minecraft.client.MinecraftClient;

/**
 * Modalità AUTO: ogni 2 secondi confronta gli FPS medi con l'obiettivo e stringe o
 * allarga le distanze di taglio. Le distanze non scendono mai sotto il 35% del valore base.
 */
public final class AutoTuner {
    private static final int INTERVAL_TICKS = 40;
    private static final double MIN_SCALE = 0.35;

    private static int ticks;

    private AutoTuner() {
    }

    public static void tick(MinecraftClient client) {
        ModSettings s = AetherPerformance.settings();
        if (s.mode != ModSettings.PerformanceMode.AUTO || client.world == null) {
            return;
        }
        if (++ticks < INTERVAL_TICKS) {
            return;
        }
        ticks = 0;

        double fps = AetherPerformance.monitor().averageFps();
        if (fps <= 0) {
            return;
        }

        double scale = RenderingConfig.autoScale;
        if (fps < s.targetFps * 0.92) {
            scale = Math.max(MIN_SCALE, scale * 0.92);
        } else if (fps > s.targetFps * 1.20) {
            scale = Math.min(1.0, scale * 1.05);
        } else {
            return;
        }
        RenderingConfig.autoScale = scale;
        RenderingConfig.apply(s);
    }
}
