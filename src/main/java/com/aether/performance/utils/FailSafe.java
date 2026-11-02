package com.aether.performance.utils;

import com.aether.performance.AetherPerformance;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rete di sicurezza: se una funzione della mod lancia un errore inatteso durante il gioco
 * (un mixin, l'overlay, la schermata impostazioni...), qui lo intercettiamo, lo scriviamo
 * una sola volta nel log e spegniamo SOLO quella funzione per il resto della sessione,
 * invece di lasciare che l'errore risalga e faccia crashare Minecraft.
 */
public final class FailSafe {
    private static final Set<String> DISABLED = ConcurrentHashMap.newKeySet();

    private FailSafe() {
    }

    /** true se questa funzione è stata disattivata perché ha già generato un errore. */
    public static boolean isDisabled(String feature) {
        return DISABLED.contains(feature);
    }

    /** Esegue l'azione; in caso di errore la disattiva permanentemente (per questa sessione). */
    public static void run(String feature, Runnable action) {
        if (DISABLED.contains(feature)) {
            return;
        }
        try {
            action.run();
        } catch (Throwable t) {
            guard(feature, t);
        }
    }

    /** Da chiamare dal blocco catch quando il codice protetto non può usare run(...) direttamente. */
    public static void guard(String feature, Throwable t) {
        if (DISABLED.add(feature)) {
            AetherPerformance.LOGGER.error(
                    "[Aether Performance] '{}' ha generato un errore ed è stata disattivata per evitare un crash. "
                            + "Il resto della mod continua a funzionare normalmente.", feature, t);
        }
    }
}
