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
    public static volatile boolean smallEntityCulling = true;
    public static volatile boolean playerCulling = false;
    public static volatile boolean overlay = false;
    public static volatile boolean overlayGraph = true;
    public static volatile boolean showPing = true;
    public static volatile boolean auto = false;

    /** true mentre sei dentro a un mondo multigiocatore (aggiornato dal tick del client). */
    public static volatile boolean onServer = false;

    /** Fattore di riduzione scelto dalla modalità AUTO (1.0 = distanze piene, 0.35 = minimo). */
    public static volatile double autoScale = 1.0;

    // Distanze al quadrato (evitano la radice quadrata nei test)
    public static volatile double blockEntityDistanceSq = square(64);
    public static volatile double entityDistanceSq = square(128);
    public static volatile double particleDistanceSq = square(48);
    public static volatile double smallEntityDistanceSq = square(48);
    public static volatile double playerDistanceSq = square(96);
    public static volatile int particleThinning = 1;

    private RenderingConfig() {
    }

    public static void apply(ModSettings s) {
        ModSettings.PerformanceMode eff = s.effectiveMode();
        int[] d = s.distancesFor(eff);

        blockEntityCulling = s.beCulling;
        entityCulling = s.entityCulling;
        particleThrottling = s.particleThrottling;
        smallEntityCulling = s.smallEntityCulling;
        playerCulling = s.distantPlayerCulling;
        overlay = s.showOverlay;
        overlayGraph = s.overlayGraph;
        showPing = s.showPing;
        auto = eff == ModSettings.PerformanceMode.AUTO;

        double k = auto ? autoScale : 1.0;
        blockEntityDistanceSq = square(Math.max(16.0, d[0] * k));
        entityDistanceSq = square(Math.max(32.0, d[1] * k));
        particleDistanceSq = square(Math.max(8.0, d[2] * k));
        particleThinning = Math.max(1, d[3]);
        smallEntityDistanceSq = square(Math.max(8.0, d[4] * k));
        playerDistanceSq = square(Math.max(48.0, s.playerCullingDistance * k));
    }

    /** Segnala nel log la presenza di mod di rendering note. Nessuna funzione viene disattivata. */
    public static void detectConflicts() {
        FabricLoader loader = FabricLoader.getInstance();
        for (String id : new String[] {"sodium", "embeddium", "iris", "lithium", "entityculling", "badoptimizations"}) {
            if (loader.isModLoaded(id)) {
                AetherPerformance.LOGGER.info("Rilevata mod compatibile/affine: {}", id);
            }
        }
        if (loader.isModLoaded("entityculling") || loader.isModLoaded("badoptimizations")) {
            AetherPerformance.LOGGER.info("Hai già mod che tagliano il rendering: puoi disattivare 'entityCulling' se noti artefatti.");
        }
    }

    private static double square(double v) {
        return v * v;
    }
}
