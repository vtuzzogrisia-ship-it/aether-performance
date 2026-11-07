package com.aether.performance.diagnostics;

import com.aether.performance.AetherPerformance;
import com.aether.performance.settings.ModSettings;
import com.aether.performance.utils.FailSafe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;

/**
 * Quando la finestra di Minecraft non è in primo piano (es. sei passato al browser o a Discord
 * mentre il gioco resta aperto dietro), abbassa il limite FPS per non consumare CPU/GPU per
 * niente. Appena torni sul gioco, rimette subito il limite scelto da te. Non tocca il file di
 * configurazione: è una modifica temporanea, mai salvata su disco.
 *
 * Disattivato di default perché chi registra lo schermo con un programma che cattura anche le
 * finestre non attive (es. "Catture finestra" in OBS) potrebbe notare un calo di fluidità nella
 * registrazione: va attivato a mano da chi lo desidera.
 */
public final class IdleFpsLimiter {
    /** Limite FPS scelto dall'utente prima di entrare in modalità "finestra in background"; -1 = nessuno salvato. */
    private static int baseFps = -1;
    private static boolean wasIdle;

    private IdleFpsLimiter() {
    }

    public static void tick(MinecraftClient client) {
        if (FailSafe.isDisabled("idle-fps")) {
            return;
        }
        try {
            tick0(client);
        } catch (Throwable t) {
            FailSafe.guard("idle-fps", t);
        }
    }

    private static void tick0(MinecraftClient client) {
        ModSettings s = AetherPerformance.settings();
        if (!s.idleFpsLimiter) {
            if (baseFps >= 0) {
                restore(client);
            }
            wasIdle = false;
            return;
        }

        boolean idle = !client.isWindowFocused();
        if (idle == wasIdle) {
            return;
        }
        wasIdle = idle;

        if (idle) {
            SimpleOption<Integer> option = client.options.getMaxFps();
            baseFps = option.getValue();
            int limit = Math.max(5, Math.min(baseFps, s.idleFps));
            option.setValue(limit);
        } else {
            restore(client);
        }
    }

    private static void restore(MinecraftClient client) {
        if (baseFps < 0) {
            return;
        }
        SimpleOption<Integer> option = client.options.getMaxFps();
        if (option.getValue() != baseFps) {
            option.setValue(baseFps);
            client.options.write();
        }
        baseFps = -1;
    }

    /** Alla chiusura del gioco rimette il limite FPS scelto dall'utente, se era stato abbassato. */
    public static void shutdown(MinecraftClient client) {
        try {
            if (baseFps >= 0) {
                restore(client);
            }
        } catch (Throwable t) {
            AetherPerformance.LOGGER.warn("[Aether Performance] Impossibile ripristinare il limite FPS alla chiusura.", t);
        }
    }
}
