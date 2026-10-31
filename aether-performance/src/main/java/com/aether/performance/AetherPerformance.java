package com.aether.performance;

import com.aether.performance.diagnostics.PerformanceMonitor;
import com.aether.performance.rendering.RenderingConfig;
import com.aether.performance.settings.ModSettings;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AetherPerformance implements ModInitializer {
    public static final String MODID = "aether-performance";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    private static final ModSettings SETTINGS = new ModSettings();
    private static final PerformanceMonitor MONITOR = new PerformanceMonitor();

    public static ModSettings settings() {
        return SETTINGS;
    }

    public static PerformanceMonitor monitor() {
        return MONITOR;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Aether Performance in avvio...");

        // 1. Impostazioni (carica, applica il preset, salva la versione normalizzata)
        SETTINGS.load();
        LOGGER.info("Modalità prestazioni: {}", SETTINGS.mode);

        // 2. Compatibilità con altre mod di rendering
        RenderingConfig.detectConflicts();

        LOGGER.info("Aether Performance pronta.");
    }
}
