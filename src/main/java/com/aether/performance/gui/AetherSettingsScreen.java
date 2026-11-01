package com.aether.performance.gui;

import com.aether.performance.AetherPerformance;
import com.aether.performance.rendering.RenderingConfig;
import com.aether.performance.settings.ModSettings;
import com.aether.performance.settings.ModSettings.PerformanceMode;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Schermata impostazioni, si apre con il tasto K (configurabile in Opzioni > Comandi). */
public class AetherSettingsScreen extends Screen {
    private static final int PANEL_W = 340;
    private static final int PANEL_H = 252;
    private static final int PAD = 10;

    private static final int ACCENT = 0xFF3DDC97;
    private static final int BORDER = 0xFF2A3440;
    private static final int HEADER_COLOR = 0xFF8FA3B5;
    private static final int HINT_COLOR = 0xFFAAB7C4;
    private static final String DEFAULT_HINT = "Passa il mouse su un'opzione per vedere cosa fa.";

    private record HoverHint(ClickableWidget widget, String text) { }

    private record Header(String text, int y) { }

    private final Screen parent;
    private final ModSettings s = AetherPerformance.settings();

    private final List<Runnable> refreshers = new ArrayList<>();
    private final List<HoverHint> hints = new ArrayList<>();
    private final List<Header> headers = new ArrayList<>();
    private final List<ValueSlider> customSliders = new ArrayList<>();
    private final List<ValueSlider> allSliders = new ArrayList<>();
    private ValueSlider targetFpsSlider;

    private int panelX;
    private int panelY;

    public AetherSettingsScreen(Screen parent) {
        super(Text.literal("Aether Performance"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        refreshers.clear();
        hints.clear();
        headers.clear();
        customSliders.clear();
        allSliders.clear();

        panelX = (this.width - PANEL_W) / 2;
        panelY = Math.max(6, (this.height - PANEL_H) / 2);

        int x = panelX + PAD;
        int inner = PANEL_W - 2 * PAD;
        int half = (inner - 10) / 2;
        int right = x + half + 10;

        // --- Profilo ---------------------------------------------------------------
        headers.add(new Header("PROFILO", panelY + 30));
        int y = panelY + 40;
        PerformanceMode[] modes = PerformanceMode.values();
        String[] modeHints = {
                "Equilibrio tra qualità e FPS (consigliata)",
                "Più FPS: distanze di rendering ridotte",
                "Massimi FPS: ciò che è lontano sparisce",
                "Regola le distanze da sola per l'FPS obiettivo",
                "Scegli tu le distanze con i cursori"
        };
        int modeW = (inner - 4 * 5) / 5;
        for (int i = 0; i < modes.length; i++) {
            final PerformanceMode m = modes[i];
            final ButtonWidget b = addDrawableChild(ButtonWidget.builder(Text.empty(), btn -> {
                s.mode = m;
                commit();
            }).dimensions(x + i * (modeW + 5), y, modeW, 20).build());
            hints.add(new HoverHint(b, modeHints[i]));
            refreshers.add(() -> b.setMessage(modeLabel(m)));
        }

        // --- Ottimizzazioni ----------------------------------------------------------
        headers.add(new Header("OTTIMIZZAZIONI", panelY + 64));
        y = panelY + 74;
        int tw = (inner - 2 * 7) / 3;
        toggle(x, y, tw, "Block entity", () -> s.beCulling, v -> s.beCulling = v,
                "Non disegna casse, cartelli ecc. lontani");
        toggle(x + tw + 7, y, tw, "Entità", () -> s.entityCulling, v -> s.entityCulling = v,
                "Non disegna mob lontani (giocatori e boss restano)");
        toggle(x + 2 * (tw + 7), y, tw, "Particelle", () -> s.particleThrottling, v -> s.particleThrottling = v,
                "Scarta o dirada le particelle lontane");

        // --- Distanze ----------------------------------------------------------------
        headers.add(new Header("DISTANZE (solo modalità Custom)", panelY + 98));
        y = panelY + 108;
        slider(x, y, half, 16, 256, 8, () -> s.beCullingDistance,
                v -> { s.beCullingDistance = v; RenderingConfig.apply(s); },
                v -> "Block entity: " + v + " bl", true,
                "Oltre questa distanza le block entity spariscono");
        slider(right, y, half, 32, 512, 16, () -> s.entityCullingDistance,
                v -> { s.entityCullingDistance = v; RenderingConfig.apply(s); },
                v -> "Entità: " + v + " bl", true,
                "Oltre questa distanza i mob non vengono disegnati");
        y += 22;
        slider(x, y, half, 8, 128, 4, () -> s.particleDistance,
                v -> { s.particleDistance = v; RenderingConfig.apply(s); },
                v -> "Particelle: " + v + " bl", true,
                "Oltre questa distanza le particelle sono scartate");
        slider(right, y, half, 1, 8, 1, () -> s.particleThinning,
                v -> { s.particleThinning = v; RenderingConfig.apply(s); },
                v -> "Diradamento: 1/" + v, true,
                "A media distanza si vede 1 particella su N");

        // --- Auto e diagnostica --------------------------------------------------------
        headers.add(new Header("AUTO E DIAGNOSTICA", panelY + 154));
        y = panelY + 164;
        targetFpsSlider = slider(x, y, half, 30, 240, 10, () -> s.targetFps,
                v -> s.targetFps = v,
                v -> "FPS obiettivo: " + v, false,
                "FPS che la modalità Auto cerca di mantenere");
        toggle(right, y, half, "Overlay", () -> s.showOverlay, v -> s.showOverlay = v,
                "Mostra FPS, 1% low, RAM e grafico (tasto J)");
        y += 22;
        toggle(x, y, half, "Grafico frame", () -> s.overlayGraph, v -> s.overlayGraph = v,
                "Mostra il grafico dei frame nell'overlay");
        ButtonWidget reset = addDrawableChild(ButtonWidget.builder(Text.literal("Ripristina valori"), btn -> {
            s.resetToDefaults();
            refreshAll();
        }).dimensions(right, y, half, 20).build());
        hints.add(new HoverHint(reset, "Torna ai valori iniziali"));

        // --- Chiudi --------------------------------------------------------------------
        y += 26;
        ButtonWidget done = addDrawableChild(ButtonWidget.builder(Text.literal("Fatto"), btn -> close())
                .dimensions(x, y, inner, 20).build());
        hints.add(new HoverHint(done, "Salva e chiudi"));

        refreshAll();
    }

    // -------------------------------------------------------------------------------------
    // Costruzione widget

    private void toggle(int x, int y, int w, String label, BooleanSupplier get, Consumer<Boolean> set, String hint) {
        final ButtonWidget b = addDrawableChild(ButtonWidget.builder(Text.empty(), btn -> {
            set.accept(!get.getAsBoolean());
            commit();
        }).dimensions(x, y, w, 20).build());
        hints.add(new HoverHint(b, hint));
        refreshers.add(() -> {
            boolean on = get.getAsBoolean();
            b.setMessage(Text.literal(label + ": ")
                    .append(Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED)));
        });
    }

    private ValueSlider slider(int x, int y, int w, int min, int max, int step,
                               java.util.function.IntSupplier get, java.util.function.IntConsumer set,
                               java.util.function.IntFunction<String> format, boolean customOnly, String hint) {
        ValueSlider sl = addDrawableChild(new ValueSlider(x, y, w, 20, min, max, step, get, set, format));
        hints.add(new HoverHint(sl, hint));
        allSliders.add(sl);
        if (customOnly) {
            customSliders.add(sl);
        }
        return sl;
    }

    private Text modeLabel(PerformanceMode m) {
        String name = switch (m) {
            case BALANCED -> "Balanced";
            case PERFORMANCE -> "Perform";
            case EXTREME -> "Extreme";
            case AUTO -> "Auto";
            case CUSTOM -> "Custom";
        };
        if (s.mode == m) {
            return Text.literal(name).formatted(Formatting.BOLD, Formatting.GREEN);
        }
        return Text.literal(name);
    }

    // -------------------------------------------------------------------------------------
    // Aggiornamento

    private void commit() {
        s.applyAndSave();
        refreshAll();
    }

    private void refreshAll() {
        for (Runnable r : refreshers) {
            r.run();
        }
        for (ValueSlider sl : allSliders) {
            sl.sync();
        }
        boolean custom = s.mode == PerformanceMode.CUSTOM;
        for (ValueSlider sl : customSliders) {
            sl.active = custom;
        }
        if (targetFpsSlider != null) {
            targetFpsSlider.active = s.mode == PerformanceMode.AUTO;
        }
    }

    // -------------------------------------------------------------------------------------
    // Disegno

    /** Lo sfondo lo disegniamo noi in render(): qui niente sfocatura di Minecraft. */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Sfondo scurito
        context.fillGradient(0, 0, this.width, this.height, 0xA0080C12, 0xD0080C12);

        // Pannello con bordo e barra colorata in alto
        context.fill(panelX - 1, panelY - 1, panelX + PANEL_W + 1, panelY + PANEL_H + 1, BORDER);
        context.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xF0121820);
        context.fillGradient(panelX, panelY + 2, panelX + PANEL_W, panelY + 26, 0x403DDC97, 0x00121820);
        context.fill(panelX, panelY, panelX + PANEL_W, panelY + 2, ACCENT);

        // Titolo e versione
        context.drawTextWithShadow(this.textRenderer, "Aether Performance", panelX + PAD, panelY + 10, 0xFFFFFFFF);
        String version = "v1.2.0";
        context.drawTextWithShadow(this.textRenderer, version,
                panelX + PANEL_W - PAD - this.textRenderer.getWidth(version), panelY + 10, HEADER_COLOR);

        // Intestazioni di sezione con linea sottile
        for (Header h : headers) {
            context.drawTextWithShadow(this.textRenderer, h.text(), panelX + PAD, h.y(), HEADER_COLOR);
            int lineX = panelX + PAD + this.textRenderer.getWidth(h.text()) + 6;
            context.fill(lineX, h.y() + 4, panelX + PANEL_W - PAD, h.y() + 5, BORDER);
        }

        super.render(context, mouseX, mouseY, delta);

        // Riga di spiegazione in basso
        String hint = DEFAULT_HINT;
        for (HoverHint h : hints) {
            if (h.widget().isHovered()) {
                hint = h.text();
                break;
            }
        }
        context.fill(panelX + PAD, panelY + PANEL_H - 20, panelX + PANEL_W - PAD, panelY + PANEL_H - 19, BORDER);
        context.drawTextWithShadow(this.textRenderer, hint, panelX + PAD, panelY + PANEL_H - 14, HINT_COLOR);
    }

    @Override
    public void removed() {
        s.save();
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
