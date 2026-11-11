package com.aether.performance.diagnostics;

import com.aether.performance.AetherPerformance;
import com.aether.performance.rendering.RenderingConfig;
import com.aether.performance.settings.ModSettings;
import com.aether.performance.utils.FailSafe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.List;

/**
 * Modalità PvP (disattivata di default): se individua altri giocatori vicini, non li taglia
 * mai dal rendering (anche con "Culling giocatori" attivo) e stringe un po' di più la
 * distanza di mob e particelle, per darti più FPS proprio durante i combattimenti.
 * Funziona con qualunque profilo, non solo Auto.
 */
public final class PvpMode {
    private static final double RANGE = 24.0;
    private static final int CHECK_INTERVAL_TICKS = 10; // due controlli al secondo bastano

    private static int ticks;
    private static boolean wasActive;

    private PvpMode() {
    }

    public static void tick(MinecraftClient client) {
        if (FailSafe.isDisabled("pvp-mode")) {
            return;
        }
        try {
            tick0(client);
        } catch (Throwable t) {
            FailSafe.guard("pvp-mode", t);
        }
    }

    private static void tick0(MinecraftClient client) {
        ModSettings s = AetherPerformance.settings();

        if (!s.pvpMode || client.player == null || client.world == null) {
            if (wasActive) {
                wasActive = false;
                RenderingConfig.pvpActive = false;
                RenderingConfig.apply(s);
            }
            return;
        }

        if (++ticks < CHECK_INTERVAL_TICKS) {
            return;
        }
        ticks = 0;

        PlayerEntity me = client.player;
        List<PlayerEntity> nearby = client.world.getEntitiesByClass(
                PlayerEntity.class, me.getBoundingBox().expand(RANGE),
                p -> p != me && !p.isSpectator());
        boolean active = !nearby.isEmpty();

        if (active != wasActive) {
            wasActive = active;
            RenderingConfig.pvpActive = active;
            RenderingConfig.apply(s);
        }
    }
}
