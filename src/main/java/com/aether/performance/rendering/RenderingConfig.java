package com.aether.performance.rendering;

import com.aether.performance.AetherPerformance;
import com.aether.performance.settings.ModSettings;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Valori "caldi" letti dai mixin a ogni frame. Sono campi statici volatili
 * così i mixin non devono mai toccare il file di configurazione.
 */
public final class RenderingConfig {
    public static volatile boolean blockEntityCulling = true;
    public static volatile boolean entityCulling = true;
    public static volatile boolean particleThrottling = true;
    public static volatile boolean overlay = false;

    // Distanze al quadrato (evitano la radice quadrata nei test)
    public static volatile double blockEntityDistanceSq = 64.0 * 64.0;
    public static volatile double entityDistanceSq = 128.0 * 128.0;
    public static volatile double particleDistanceSq = 48.0 * 48.0;
    public static volatile int particleThinning = 1;

    private RenderingConfig() {
    }

    public static void apply(ModSettings s) {
        blockEntityCulling = s.beCulling;
        entityCulling = s.entityCulling;
        particleThrottling = s.particleThrottling;
        overlay = s.showOverlay;

        blockEntityDistanceSq = square(s.beCullingDistance);
        entityDistanceSq = square(s.entityCullingDistance);
        particleDistanceSq = square(s.particleDistance);
        particleThinning = Math.max(1, s.particleThinning);
    }

    /** Segnala nel log la presenza di mod di rendering note. Nessuna funzione viene disattivata. */
    public static void detectConflicts() {
        FabricLoader loader = FabricLoader.getInstance();
        for (String id : new String[] {"sodium", "embeddium", "iris", "lithium", "entityculling"}) {
            if (loader.isModLoaded(id)) {
                AetherPerformance.LOGGER.info("Rilevata mod compatibile/affine: {}", id);
            }
        }
        if (loader.isModLoaded("entityculling")) {
            AetherPerformance.LOGGER.info("Con Entity Culling installata puoi disattivare 'entityCulling' nel config.");
        }
    }

    private static double square(int v) {
        return (double) v * v;
    }
}
