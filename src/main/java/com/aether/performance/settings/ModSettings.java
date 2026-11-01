package com.aether.performance.settings;

import com.aether.performance.AetherPerformance;
import com.aether.performance.rendering.RenderingConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * BALANCED/PERFORMANCE/EXTREME: valori fissi. AUTO: parte dai valori di BALANCED e li
     * restringe o allarga da sola per mantenere {@link #targetFps}. CUSTOM: usi i tuoi numeri.
     */
    public enum PerformanceMode { BALANCED, PERFORMANCE, EXTREME, AUTO, CUSTOM }

    public PerformanceMode mode = PerformanceMode.BALANCED;
    public boolean beCulling = true;
    public boolean entityCulling = true;
    public boolean particleThrottling = true;
    /** Oggetti a terra e sfere di esperienza lontani non vengono disegnati. */
    public boolean smallEntityCulling = true;
    public boolean showOverlay = false;
    public boolean overlayGraph = true;
    /** Mostra il ping nell'overlay quando sei su un server. */
    public boolean showPing = true;

    // Usati dai preset; in modalità CUSTOM valgono i tuoi numeri
    public int beCullingDistance = 64;
    public int entityCullingDistance = 128;
    public int particleDistance = 48;
    /** 1 = nessuna riduzione; N = oltre un quarto della distanza viene mostrata 1 particella su N. */
    public int particleThinning = 1;
    public int smallEntityDistance = 48;

    /** FPS che la modalità AUTO cerca di mantenere. */
    public int targetFps = 60;
    /** In modalità AUTO abbassa (e rialza) anche la distanza di rendering se gli FPS restano bassi. */
    public boolean dynamicViewDistance = false;

    // --- Server --------------------------------------------------------------------------
    /** Su un server multigiocatore usa {@link #serverMode} al posto di {@link #mode}. */
    public boolean useServerProfile = false;
    public PerformanceMode serverMode = PerformanceMode.PERFORMANCE;
    /** Non disegna i giocatori oltre {@link #playerCullingDistance} (utile nei server affollati). */
    public boolean distantPlayerCulling = false;
    public int playerCullingDistance = 96;

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("aether_performance.json");
    }

    public void load() {
        Path path = configPath();
        try {
            if (Files.exists(path)) {
                ModSettings loaded = GSON.fromJson(Files.readString(path), ModSettings.class);
                if (loaded != null) {
                    copyFrom(loaded);
                }
            }
        } catch (IOException | JsonParseException e) {
            AetherPerformance.LOGGER.warn("Config non leggibile, uso i valori predefiniti: {}", e.toString());
        }
        applyAndSave();
    }

    /** Controlla i valori, applica il preset, aggiorna i valori usati dal gioco e salva il file. */
    public void applyAndSave() {
        sanitize();
        applyPreset();
        RenderingConfig.apply(this);
        save();
    }

    /** Riporta tutto ai valori iniziali. */
    public void resetToDefaults() {
        copyFrom(new ModSettings());
        RenderingConfig.autoScale = 1.0;
        applyAndSave();
    }

    /** Copia indipendente delle impostazioni (serve per "Annulla modifiche"). */
    public ModSettings copy() {
        return GSON.fromJson(GSON.toJson(this), ModSettings.class);
    }

    /** Ripristina i valori da una copia fatta con {@link #copy()}. */
    public void restore(ModSettings o) {
        copyFrom(o);
        applyAndSave();
    }

    public void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
        } catch (IOException e) {
            AetherPerformance.LOGGER.warn("Impossibile salvare il config: {}", e.toString());
        }
    }

    /** Il profilo davvero in uso: quello del server se sei connesso a uno e l'opzione è attiva. */
    public PerformanceMode effectiveMode() {
        if (useServerProfile && RenderingConfig.onServer && serverMode != null) {
            return serverMode;
        }
        return mode;
    }

    /**
     * Distanze di un profilo: {blockEntity, entità, particelle, diradamento, oggetti piccoli}.
     * Per CUSTOM restituisce i valori scelti dall'utente.
     */
    public int[] distancesFor(PerformanceMode m) {
        return switch (m) {
            case BALANCED, AUTO -> new int[] {64, 128, 48, 1, 48};
            case PERFORMANCE -> new int[] {48, 96, 32, 2, 32};
            case EXTREME -> new int[] {32, 64, 24, 3, 24};
            case CUSTOM -> new int[] {beCullingDistance, entityCullingDistance, particleDistance,
                    particleThinning, smallEntityDistance};
        };
    }

    private void copyFrom(ModSettings o) {
        this.mode = o.mode;
        this.beCulling = o.beCulling;
        this.entityCulling = o.entityCulling;
        this.particleThrottling = o.particleThrottling;
        this.smallEntityCulling = o.smallEntityCulling;
        this.showOverlay = o.showOverlay;
        this.overlayGraph = o.overlayGraph;
        this.showPing = o.showPing;
        this.beCullingDistance = o.beCullingDistance;
        this.entityCullingDistance = o.entityCullingDistance;
        this.particleDistance = o.particleDistance;
        this.particleThinning = o.particleThinning;
        this.smallEntityDistance = o.smallEntityDistance;
        this.targetFps = o.targetFps;
        this.dynamicViewDistance = o.dynamicViewDistance;
        this.useServerProfile = o.useServerProfile;
        this.serverMode = o.serverMode;
        this.distantPlayerCulling = o.distantPlayerCulling;
        this.playerCullingDistance = o.playerCullingDistance;
    }

    private void sanitize() {
        if (mode == null) {
            mode = PerformanceMode.BALANCED;
        }
        if (serverMode == null) {
            serverMode = PerformanceMode.PERFORMANCE;
        }
        beCullingDistance = clamp(beCullingDistance, 16, 256);
        entityCullingDistance = clamp(entityCullingDistance, 16, 512);
        particleDistance = clamp(particleDistance, 8, 128);
        particleThinning = clamp(particleThinning, 1, 8);
        smallEntityDistance = clamp(smallEntityDistance, 8, 128);
        playerCullingDistance = clamp(playerCullingDistance, 32, 256);
        targetFps = clamp(targetFps, 20, 360);
    }

    /** Sovrascrive le distanze in base al preset scelto (tranne CUSTOM). */
    private void applyPreset() {
        // I numeri scelti dall'utente non vanno sovrascritti se un profilo in uso è Personalizzato
        if (mode == PerformanceMode.CUSTOM || (useServerProfile && serverMode == PerformanceMode.CUSTOM)) {
            return;
        }
        int[] d = distancesFor(mode);
        beCullingDistance = d[0];
        entityCullingDistance = d[1];
        particleDistance = d[2];
        particleThinning = d[3];
        smallEntityDistance = d[4];
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
