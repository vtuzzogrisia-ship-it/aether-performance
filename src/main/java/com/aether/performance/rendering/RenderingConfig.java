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

    /** true quando la modalità PvP è attiva e ha rilevato altri giocatori vicini (vedi {@link com.aether.performance.diagnostics.PvpMode}). */
    public static volatile boolean pvpActive = false;
    /** Quanto si stringono ulteriormente mob/particelle mentre {@link #pvpActive} è vero. */
    private static final double PVP_TIGHTEN = 0.7;

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
        // In modalità PvP i giocatori non vengono mai tagliati, qualunque sia questa impostazione.
        playerCulling = s.distantPlayerCulling && !pvpActive;
        overlay = s.showOverlay;
        overlayGraph = s.overlayGraph;
        showPing = s.showPing;
        auto = eff == ModSettings.PerformanceMode.AUTO;

        double k = auto ? autoScale : 1.0;
        if (pvpActive) {
            // Libera un po' di budget frame su mob/particelle (mai sui giocatori) proprio
            // quando serve di più: durante un combattimento con altri giocatori vicini.
            k *= PVP_TIGHTEN;
        }
        blockEntityDistanceSq = square(Math.max(16.0, d[0] * k));
        entityDistanceSq = square(Math.max(32.0, d[1] * k));
        particleDistanceSq = square(Math.max(8.0, d[2] * k));
        particleThinning = Math.max(1, pvpActive ? d[3] + 1 : d[3]);
        smallEntityDistanceSq = square(Math.max(8.0, d[4] * k));
        playerDistanceSq = square(Math.max(48.0, s.playerCullingDistance * k));
    }

    private static final String[] KNOWN_RENDER_MODS = {
            "sodium", "embeddium", "iris", "lithium", "entityculling", "badoptimizations"
    };
    private static final String[] KNOWN_RENDER_NAMES = {
            "Sodium", "Embeddium", "Iris", "Lithium", "EntityCulling", "BadOptimizations"
    };

    /** Segnala nel log la presenza di mod di rendering note. Nessuna funzione viene disattivata. */
    public static void detectConflicts() {
        FabricLoader loader = FabricLoader.getInstance();
        for (String id : KNOWN_RENDER_MODS) {
            if (loader.isModLoaded(id)) {
                AetherPerformance.LOGGER.info("Rilevata mod compatibile/affine: {}", id);
            }
        }
        if (loader.isModLoaded("entityculling") || loader.isModLoaded("badoptimizations")) {
            AetherPerformance.LOGGER.info("Hai già mod che tagliano il rendering: puoi disattivare 'entityCulling' se noti artefatti.");
        }
    }

    /**
     * Testo pronto per la GUI con le mod di rendering note che sono installate insieme a questa,
     * cosi chi gioca sa che convivono bene senza dover guardare i log.
     */
    public static String detectedModsText() {
        FabricLoader loader = FabricLoader.getInstance();
        StringBuilder found = new StringBuilder();
        for (int i = 0; i < KNOWN_RENDER_MODS.length; i++) {
            if (loader.isModLoaded(KNOWN_RENDER_MODS[i])) {
                if (found.length() > 0) {
                    found.append(", ");
                }
                found.append(KNOWN_RENDER_NAMES[i]);
            }
        }
        if (found.length() == 0) {
            return "Nessun'altra mod di rendering rilevata: funzioni di Aether Performance tutte attive.";
        }
        return "Mod compatibili rilevate: " + found + ". Vanno d'accordo con Aether Performance.";
    }

    private static double square(double v) {
        return v * v;
    }
}
