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

    public enum PerformanceMode { BALANCED, PERFORMANCE, EXTREME, CUSTOM }

    public PerformanceMode mode = PerformanceMode.BALANCED;
    public boolean beCulling = true;
    public boolean entityCulling = true;
    public boolean particleThrottling = true;
    public boolean showOverlay = false;

    // Usati dai preset; in modalità CUSTOM valgono i tuoi numeri
    public int beCullingDistance = 64;
    public int entityCullingDistance = 128;
    public int particleDistance = 48;
    /** 1 = nessuna riduzione; N = oltre metà distanza viene mostrata 1 particella su N. */
    public int particleThinning = 1;

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
        sanitize();
        applyPreset();
        RenderingConfig.apply(this);
        save();
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

    private void copyFrom(ModSettings o) {
        this.mode = o.mode;
        this.beCulling = o.beCulling;
        this.entityCulling = o.entityCulling;
        this.particleThrottling = o.particleThrottling;
        this.showOverlay = o.showOverlay;
        this.beCullingDistance = o.beCullingDistance;
        this.entityCullingDistance = o.entityCullingDistance;
        this.particleDistance = o.particleDistance;
        this.particleThinning = o.particleThinning;
    }

    private void sanitize() {
        if (mode == null) {
            mode = PerformanceMode.BALANCED;
        }
        beCullingDistance = clamp(beCullingDistance, 16, 256);
        entityCullingDistance = clamp(entityCullingDistance, 16, 512);
        particleDistance = clamp(particleDistance, 8, 128);
        particleThinning = clamp(particleThinning, 1, 8);
    }

    /** Sovrascrive le distanze in base al preset scelto (tranne CUSTOM). */
    private void applyPreset() {
        switch (mode) {
            case BALANCED -> setValues(64, 128, 48, 1);
            case PERFORMANCE -> setValues(48, 96, 32, 2);
            case EXTREME -> setValues(32, 64, 24, 3);
            case CUSTOM -> { }
        }
    }

    private void setValues(int be, int entity, int particle, int thinning) {
        beCullingDistance = be;
        entityCullingDistance = entity;
        particleDistance = particle;
        particleThinning = thinning;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
