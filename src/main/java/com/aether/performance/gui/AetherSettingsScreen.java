package com.aether.performance.gui;

import com.aether.performance.AetherPerformance;
import com.aether.performance.settings.ModSettings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Schermata impostazioni, si apre con il tasto K (configurabile in Opzioni > Comandi). */
public class AetherSettingsScreen extends Screen {
    private static final int[] BE_DISTANCES = {16, 32, 48, 64, 96, 128, 192, 256};
    private static final int[] ENTITY_DISTANCES = {32, 48, 64, 96, 128, 192, 256, 384, 512};
    private static final int[] PARTICLE_DISTANCES = {8, 16, 24, 32, 48, 64, 96, 128};
    private static final int[] THINNING = {1, 2, 3, 4, 6, 8};

    private final Screen parent;
    private final ModSettings s = AetherPerformance.settings();

    private ButtonWidget modeBtn;
    private ButtonWidget beBtn;
    private ButtonWidget entityBtn;
    private ButtonWidget particleBtn;
    private ButtonWidget overlayBtn;
    private ButtonWidget beDistBtn;
    private ButtonWidget entityDistBtn;
    private ButtonWidget particleDistBtn;
    private ButtonWidget thinningBtn;

    public AetherSettingsScreen(Screen parent) {
        super(Text.literal("Aether Performance"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 155;
        int right = this.width / 2 + 5;
        int y = 40;
        int step = 24;

        modeBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            ModSettings.PerformanceMode[] modes = ModSettings.PerformanceMode.values();
            s.mode = modes[(s.mode.ordinal() + 1) % modes.length];
            commit();
        }).dimensions(left, y, 310, 20).build());

        y += step;
        beBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            s.beCulling = !s.beCulling;
            commit();
        }).dimensions(left, y, 150, 20).build());
        entityBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            s.entityCulling = !s.entityCulling;
            commit();
        }).dimensions(right, y, 150, 20).build());

        y += step;
        particleBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            s.particleThrottling = !s.particleThrottling;
            commit();
        }).dimensions(left, y, 150, 20).build());
        overlayBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            s.showOverlay = !s.showOverlay;
            commit();
        }).dimensions(right, y, 150, 20).build());

        y += step;
        beDistBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            s.beCullingDistance = next(s.beCullingDistance, BE_DISTANCES);
            commit();
        }).dimensions(left, y, 150, 20).build());
        entityDistBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            s.entityCullingDistance = next(s.entityCullingDistance, ENTITY_DISTANCES);
            commit();
        }).dimensions(right, y, 150, 20).build());

        y += step;
        particleDistBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            s.particleDistance = next(s.particleDistance, PARTICLE_DISTANCES);
            commit();
        }).dimensions(left, y, 150, 20).build());
        thinningBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            s.particleThinning = next(s.particleThinning, THINNING);
            commit();
        }).dimensions(right, y, 150, 20).build());

        y += step + 8;
        addDrawableChild(ButtonWidget.builder(Text.literal("Fatto"), b -> close())
                .dimensions(left, y, 310, 20).build());

        refresh();
    }

    private void commit() {
        s.applyAndSave();
        refresh();
    }

    private void refresh() {
        modeBtn.setMessage(Text.literal("Modalità: " + s.mode + modeHint()));
        beBtn.setMessage(Text.literal("Block entity lontane: " + onOff(s.beCulling)));
        entityBtn.setMessage(Text.literal("Entità lontane: " + onOff(s.entityCulling)));
        particleBtn.setMessage(Text.literal("Particelle lontane: " + onOff(s.particleThrottling)));
        overlayBtn.setMessage(Text.literal("Overlay FPS: " + onOff(s.showOverlay)));

        boolean custom = s.mode == ModSettings.PerformanceMode.CUSTOM;
        beDistBtn.setMessage(Text.literal("Dist. block entity: " + s.beCullingDistance));
        entityDistBtn.setMessage(Text.literal("Dist. entità: " + s.entityCullingDistance));
        particleDistBtn.setMessage(Text.literal("Dist. particelle: " + s.particleDistance));
        thinningBtn.setMessage(Text.literal("Diradamento particelle: 1/" + s.particleThinning));
        beDistBtn.active = custom;
        entityDistBtn.active = custom;
        particleDistBtn.active = custom;
        thinningBtn.active = custom;
    }

    private String modeHint() {
        return s.mode == ModSettings.PerformanceMode.CUSTOM ? "" : " (clic per cambiare)";
    }

    private static String onOff(boolean v) {
        return v ? "SI" : "NO";
    }

    /** Passa al valore successivo della lista, tornando al primo dopo l'ultimo. */
    private static int next(int current, int[] values) {
        for (int v : values) {
            if (v > current) {
                return v;
            }
        }
        return values[0];
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 18, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Le distanze si cambiano solo in modalità CUSTOM"),
                this.width / 2, this.height - 24, 0xFFAAAAAA);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
