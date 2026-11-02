package com.aether.performance.diagnostics;

import com.aether.performance.AetherPerformance;
import com.aether.performance.rendering.RenderingConfig;
import com.aether.performance.settings.ModSettings;
import com.aether.performance.utils.FailSafe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;

/**
 * Modalità AUTO: ogni 2 secondi confronta gli FPS medi con l'obiettivo e stringe o
 * allarga le distanze di taglio (mai sotto il 35% del valore base).
 * Opzionalmente regola anche la distanza di rendering, a passi di 1 chunk ogni 10 secondi.
 * Non interviene nei primi 10 secondi dopo l'ingresso in un mondo né con la finestra in background.
 */
public final class AutoTuner {
    private static final int INTERVAL_TICKS = 40;
    private static final int VIEW_INTERVAL_TICKS = 200;
    private static final int JOIN_GRACE_TICKS = 200;
    private static final double MIN_SCALE = 0.35;
    private static final int MIN_VIEW = 8;

    private static int ticks;
    private static int viewTicks;
    private static int grace;
    private static Object lastWorld;
    /** Distanza di rendering scelta dall'utente; -1 = nessuna salvata. */
    private static int baseView = -1;

    private AutoTuner() {
    }

    public static void tick(MinecraftClient client) {
        if (FailSafe.isDisabled("auto-tuner")) {
            return;
        }
        try {
            tick0(client);
        } catch (Throwable t) {
            FailSafe.guard("auto-tuner", t);
        }
    }

    private static void tick0(MinecraftClient client) {
        ModSettings s = AetherPerformance.settings();

        if (client.world != lastWorld) {
            lastWorld = client.world;
            grace = JOIN_GRACE_TICKS;
            ticks = 0;
            viewTicks = 0;
        }

        // Stato del collegamento: cambia profilo quando entri/esci da un server
        boolean onServer = client.world != null && !client.isInSingleplayer();
        if (onServer != RenderingConfig.onServer) {
            RenderingConfig.onServer = onServer;
            RenderingConfig.autoScale = 1.0;
            RenderingConfig.apply(s);
        }

        boolean auto = s.effectiveMode() == ModSettings.PerformanceMode.AUTO;
        boolean dynamicView = auto && s.dynamicViewDistance;

        if (!dynamicView && baseView >= 0) {
            restoreView(client);
        }
        if (!auto || client.world == null) {
            return;
        }
        if (grace > 0) {
            grace--;
            return;
        }
        if (!client.isWindowFocused()) {
            return;
        }

        double fps = AetherPerformance.monitor().averageFps();
        if (fps <= 0) {
            return;
        }

        if (++ticks >= INTERVAL_TICKS) {
            ticks = 0;
            double scale = RenderingConfig.autoScale;
            if (fps < s.targetFps * 0.92) {
                scale = Math.max(MIN_SCALE, scale * 0.92);
            } else if (fps > s.targetFps * 1.20) {
                scale = Math.min(1.0, scale * 1.05);
            }
            if (scale != RenderingConfig.autoScale) {
                RenderingConfig.autoScale = scale;
                RenderingConfig.apply(s);
            }
        }

        if (dynamicView) {
            tuneViewDistance(client, s, fps);
        }
    }

    private static void tuneViewDistance(MinecraftClient client, ModSettings s, double fps) {
        SimpleOption<Integer> option = client.options.getViewDistance();
        int current = option.getValue();
        if (baseView < 0 || current > baseView) {
            baseView = current; // l'utente l'ha alzata a mano: diventa la nuova base
        }
        if (++viewTicks < VIEW_INTERVAL_TICKS) {
            return;
        }
        viewTicks = 0;

        double scale = RenderingConfig.autoScale;
        if (fps < s.targetFps * 0.85 && scale <= 0.6 && current > MIN_VIEW) {
            option.setValue(current - 1);
        } else if (fps > s.targetFps * 1.5 && scale >= 0.95 && current < baseView) {
            option.setValue(current + 1);
        }
    }

    private static void restoreView(MinecraftClient client) {
        SimpleOption<Integer> option = client.options.getViewDistance();
        if (option.getValue() != baseView) {
            option.setValue(baseView);
            client.options.write();
        }
        baseView = -1;
    }

    /** Alla chiusura del gioco rimette la distanza di rendering scelta dall'utente. */
    public static void shutdown(MinecraftClient client) {
        try {
            if (baseView >= 0) {
                restoreView(client);
            }
        } catch (Throwable t) {
            AetherPerformance.LOGGER.warn("[Aether Performance] Impossibile ripristinare la distanza di rendering alla chiusura.", t);
        }
    }
}
