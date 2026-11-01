package com.aether.performance.gui;

import com.aether.performance.AetherPerformance;
import com.aether.performance.diagnostics.PerformanceMonitor;
import com.aether.performance.rendering.RenderingConfig;
import com.aether.performance.settings.ModSettings;
import com.aether.performance.settings.ModSettings.PerformanceMode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Schermata impostazioni (tasto K): barra laterale con 4 schede, header con stato,
 * footer con Ripristina / Annulla / Applica. Tutti i controlli sono disegnati a mano.
 */
public class AetherSettingsScreen extends Screen {

    // --- Palette -------------------------------------------------------------------------
    private static final int C_DARK = 0xFF0F1117;
    private static final int C_CARD = 0xFF181B24;
    private static final int C_CARD2 = 0xFF141720;
    private static final int C_BORDER = 0xFF2A2F3D;
    private static final int C_BORDER_HOT = 0xFF4B5263;
    private static final int C_ACCENT = 0xFF3B82F6;
    private static final int C_ACCENT_L = 0xFF60A5FA;
    private static final int C_ACCENT_D = 0xFF2563EB;
    private static final int C_GREEN = 0xFF10B981;
    private static final int C_GREEN_L = 0xFF34D399;
    private static final int C_YELLOW = 0xFFFBBF24;
    private static final int C_RED = 0xFFEF4444;
    private static final int C_PURPLE = 0xFFA78BFA;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_MUTED = 0xFF9CA3AF;

    private static final int SIDEBAR_W = 104;
    private static final int HEADER_H = 34;
    private static final int FOOTER_H = 30;

    private enum Tab {
        GENERAL("Generale", "Impostazioni generali", "Distanze, limite FPS e sincronizzazione verticale."),
        GRAPHICS("Grafica", "Grafica ed effetti", "Bilancia qualità visiva e carico di rendering."),
        PERFORMANCE("Prestazioni", "Ottimizzazioni Aether", "Culling di entità, block entity e particelle."),
        DIAGNOSTICS("Diagnostica", "Diagnostica in tempo reale", "FPS, frame time, memoria e oggetti tagliati.");

        final String label;
        final String title;
        final String subtitle;

        Tab(String label, String title, String subtitle) {
            this.label = label;
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private record Label(String text, int x, int y, int w) { }

    private record VanillaSnap(int view, int sim, int fps, boolean vsync, boolean ao, boolean shadows, Object particles) { }

    private static Tab lastTab = Tab.GENERAL;

    private final Screen parent;
    private final ModSettings s = AetherPerformance.settings();
    private final String version;

    private final List<Ctl> ctls = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private final double[] hist = new double[300];
    private Ctl active;

    private Tab tab = lastTab;
    private ModSettings snapshot;
    private VanillaSnap vSnap;

    private int px;
    private int py;
    private int pw;
    private int ph;
    private int footerTop;
    private int mainX;
    private int mainY;
    private int mainW;
    private int contentTop;
    private int contentBottom;

    private String toastText = "";
    private long toastUntil;

    public AetherSettingsScreen(Screen parent) {
        super(Text.literal("Aether Performance"));
        this.parent = parent;
        this.version = FabricLoader.getInstance().getModContainer(AetherPerformance.MODID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("");
    }

    // =========================================================================================
    // Costruzione

    @Override
    protected void init() {
        ctls.clear();
        labels.clear();
        active = null;

        if (snapshot == null) {
            snapshot = s.copy();
            vSnap = takeVanilla();
            AetherPerformance.monitor().resync();
        }

        pw = Math.min(this.width - 16, 470);
        ph = Math.min(this.height - 12, 268);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        footerTop = py + ph - FOOTER_H;
        mainX = px + SIDEBAR_W + 10;
        mainW = px + pw - 10 - mainX;
        mainY = py + HEADER_H + 10;
        contentTop = mainY + 26;
        contentBottom = footerTop - 14;

        // Sidebar
        int bx = px + 8;
        int bw = SIDEBAR_W - 16;
        int by = py + HEADER_H + 10;
        for (Tab t : Tab.values()) {
            ctls.add(new TabCtl(t, bx, by, bw, 22));
            by += 26;
        }
        ctls.add(new PresetCtl(bx, footerTop - 8 - 40, bw, 40).tip("Clic per cambiare profilo di prestazioni"));

        switch (tab) {
            case GENERAL -> buildGeneral();
            case GRAPHICS -> buildGraphics();
            case PERFORMANCE -> buildPerformance();
            case DIAGNOSTICS -> buildDiagnostics();
        }

        // Footer
        int fy = footerTop + 6;
        ctls.add(new Btn("Ripristina valori", px + 10, fy, 100, 18, 0, this::resetDefaults)
                .tip("Torna ai valori iniziali di Aether Performance"));
        int applyW = 108;
        int discardW = 76;
        int applyX = px + pw - 10 - applyW;
        ctls.add(new Btn("Applica modifiche", applyX, fy, applyW, 18, 2, this::apply)
                .tip("Salva le modifiche (anche le opzioni di gioco)"));
        ctls.add(new Btn("Annulla", applyX - 6 - discardW, fy, discardW, 18, 1, this::discard)
                .tip("Torna ai valori di quando hai aperto il menu"));

        // Chiudi
        ctls.add(new Btn("x", px + pw - 22, py + 10, 14, 14, 0, this::close).tip("Chiudi (Esc)"));
    }

    private void buildGeneral() {
        GameOptions o = opts();
        int y = contentTop;
        ctls.add(new SliderCard(mainX, y, mainW, 38, "Distanza di rendering", "Raggio massimo dei chunk visibili.",
                2, 32, 1, () -> o.getViewDistance().getValue(), v -> o.getViewDistance().setValue(v),
                v -> v + " chunk", false).tip("Più chunk = più bello ma più pesante (si applica al rilascio)"));
        y += 41;
        ctls.add(new SliderCard(mainX, y, mainW, 38, "Distanza di simulazione", "Raggio in cui il mondo viene aggiornato.",
                5, 32, 1, () -> o.getSimulationDistance().getValue(), v -> o.getSimulationDistance().setValue(v),
                v -> v + " chunk", false).tip("Quanto lontano mob e coltivazioni restano attivi"));
        y += 41;
        ctls.add(new SliderCard(mainX, y, mainW, 38, "Limite FPS", "Limita gli FPS per ridurre calore e consumi.",
                10, 260, 10, () -> o.getMaxFps().getValue(), v -> o.getMaxFps().setValue(v),
                v -> v >= 260 ? "Illimitati" : v + " FPS", false).tip("Al massimo = illimitati"));
        y += 41;
        ctls.add(new ToggleCard(mainX, y, mainW, 26, "V-Sync", "Sincronizza gli FPS con il monitor.",
                () -> o.getEnableVsync().getValue(), v -> o.getEnableVsync().setValue(v))
                .tip("Elimina lo strappo dell'immagine, ma può aggiungere ritardo"));
    }

    private void buildGraphics() {
        GameOptions o = opts();
        int y = contentTop;
        ctls.add(new ToggleCard(mainX, y, mainW, 30, "Illuminazione fluida", "Sfuma la luce tra i blocchi adiacenti.",
                () -> o.getAo().getValue(), v -> o.getAo().setValue(v))
                .tip("Impostazione di gioco: luce più morbida, costo piccolo"));
        y += 34;
        ctls.add(new CycleCard(mainX, y, mainW, 30, "Effetti particelle", "Quantità di fumo, pioggia e magia.",
                () -> particlesLabel(o), () -> cycle(o.getParticles()))
                .tip("Clic per cambiare: Tutte / Ridotte / Minime"));
        y += 34;
        ctls.add(new ToggleCard(mainX, y, mainW, 30, "Ombre delle entità", "Disegna l'ombra sotto mob e oggetti.",
                () -> o.getEntityShadows().getValue(), v -> o.getEntityShadows().setValue(v))
                .tip("Spegnerle fa guadagnare qualche FPS con molti mob"));
        y += 38;
        labels.add(new Label("Queste sono le opzioni di Minecraft: restano salvate anche senza la mod.", mainX, y, mainW));
    }

    private void buildPerformance() {
        int gap = 6;
        int y = contentTop;
        int cw = (mainW - 2 * gap) / 3;
        ctls.add(new ToggleCard(mainX, y, cw, 24, "Entità", null,
                () -> s.entityCulling, v -> { s.entityCulling = v; commit(); })
                .tip("Non disegna i mob lontani (giocatori, boss e mob con nome restano)"));
        ctls.add(new ToggleCard(mainX + cw + gap, y, cw, 24, "Block entity", null,
                () -> s.beCulling, v -> { s.beCulling = v; commit(); })
                .tip("Non disegna casse, cartelli, banner ecc. lontani"));
        ctls.add(new ToggleCard(mainX + 2 * (cw + gap), y, cw, 24, "Particelle", null,
                () -> s.particleThrottling, v -> { s.particleThrottling = v; commit(); })
                .tip("Scarta le particelle lontane e dirada quelle a media distanza"));

        y += 28;
        labels.add(new Label("DISTANZE (modalità Personalizzato) E OBIETTIVO AUTO", mainX, y, mainW));
        y += 14;

        int hw = (mainW - gap) / 2;
        BooleanSupplier custom = () -> s.mode == PerformanceMode.CUSTOM;
        String offTip = "Disponibile solo nel profilo Personalizzato";
        ctls.add(new SliderCard(mainX, y, hw, 30, "Block entity", null, 16, 256, 8,
                () -> s.beCullingDistance, v -> { s.beCullingDistance = v; RenderingConfig.apply(s); },
                v -> v + " bl", true).tip("Oltre questa distanza le block entity spariscono").when(custom, offTip));
        ctls.add(new SliderCard(mainX + hw + gap, y, hw, 30, "Entità", null, 32, 512, 16,
                () -> s.entityCullingDistance, v -> { s.entityCullingDistance = v; RenderingConfig.apply(s); },
                v -> v + " bl", true).tip("Oltre questa distanza i mob non vengono disegnati").when(custom, offTip));
        y += 34;
        ctls.add(new SliderCard(mainX, y, hw, 30, "Particelle", null, 8, 128, 4,
                () -> s.particleDistance, v -> { s.particleDistance = v; RenderingConfig.apply(s); },
                v -> v + " bl", true).tip("Oltre questa distanza le particelle sono scartate").when(custom, offTip));
        ctls.add(new SliderCard(mainX + hw + gap, y, hw, 30, "Diradamento", null, 1, 8, 1,
                () -> s.particleThinning, v -> { s.particleThinning = v; RenderingConfig.apply(s); },
                v -> "1/" + v, true).tip("A media distanza si vede 1 particella su N").when(custom, offTip));
        y += 34;
        ctls.add(new SliderCard(mainX, y, hw, 30, "FPS obiettivo", null, 30, 240, 10,
                () -> s.targetFps, v -> s.targetFps = v,
                v -> v + " FPS", true).tip("Gli FPS che il profilo Auto cerca di mantenere")
                .when(() -> s.mode == PerformanceMode.AUTO, "Disponibile solo nel profilo Auto"));
    }

    private void buildDiagnostics() {
        int h = contentBottom - contentTop;
        ctls.add(new DiagCtl(mainX, contentTop, mainW, h));

        int chartY = contentTop + 66;
        MiniToggle graph = new MiniToggle(0, chartY + 4, "Grafico", () -> s.overlayGraph,
                v -> { s.overlayGraph = v; commit(); });
        graph.x = mainX + mainW - 8 - graph.w;
        graph.tip("Mostra il grafico dei frame nell'overlay sullo schermo");
        MiniToggle overlay = new MiniToggle(0, chartY + 4, "Overlay", () -> s.showOverlay,
                v -> { s.showOverlay = v; commit(); });
        overlay.x = graph.x - 10 - overlay.w;
        overlay.tip("Mostra FPS, RAM e grafico sullo schermo di gioco (tasto J)");
        ctls.add(overlay);
        ctls.add(graph);
    }

    // =========================================================================================
    // Azioni

    private void commit() {
        s.applyAndSave();
    }

    private void switchTab(Tab t) {
        tab = t;
        lastTab = t;
        init();
    }

    private void apply() {
        s.applyAndSave();
        opts().write();
        snapshot = s.copy();
        vSnap = takeVanilla();
        toast("Modifiche salvate e applicate!");
    }

    private void discard() {
        s.restore(snapshot);
        restoreVanilla(vSnap);
        toast("Modifiche annullate.");
    }

    private void resetDefaults() {
        s.resetToDefaults();
        toast("Aether Performance ripristinata ai valori iniziali.");
    }

    private void toast(String msg) {
        toastText = msg;
        toastUntil = System.currentTimeMillis() + 2500L;
    }

    private void cycleMode() {
        PerformanceMode[] all = PerformanceMode.values();
        s.mode = all[(s.mode.ordinal() + 1) % all.length];
        if (s.mode == PerformanceMode.AUTO) {
            RenderingConfig.autoScale = 1.0;
        }
        commit();
    }

    private static String modeName(PerformanceMode m) {
        return switch (m) {
            case BALANCED -> "Bilanciato";
            case PERFORMANCE -> "Prestazioni";
            case EXTREME -> "Estremo";
            case AUTO -> "Auto";
            case CUSTOM -> "Personalizzato";
        };
    }

    // --- Opzioni di Minecraft -------------------------------------------------------------

    private static GameOptions opts() {
        return MinecraftClient.getInstance().options;
    }

    private VanillaSnap takeVanilla() {
        GameOptions o = opts();
        return new VanillaSnap(o.getViewDistance().getValue(), o.getSimulationDistance().getValue(),
                o.getMaxFps().getValue(), o.getEnableVsync().getValue(), o.getAo().getValue(),
                o.getEntityShadows().getValue(), o.getParticles().getValue());
    }

    private void restoreVanilla(VanillaSnap v) {
        GameOptions o = opts();
        o.getViewDistance().setValue(v.view());
        o.getSimulationDistance().setValue(v.sim());
        o.getMaxFps().setValue(v.fps());
        o.getEnableVsync().setValue(v.vsync());
        o.getAo().setValue(v.ao());
        o.getEntityShadows().setValue(v.shadows());
        setRaw(o.getParticles(), v.particles());
    }

    @SuppressWarnings("unchecked")
    private static <E> void setRaw(SimpleOption<E> option, Object value) {
        option.setValue((E) value);
    }

    private static <E extends Enum<E>> void cycle(SimpleOption<E> option) {
        E current = option.getValue();
        E[] all = current.getDeclaringClass().getEnumConstants();
        option.setValue(all[(current.ordinal() + 1) % all.length]);
    }

    private static String particlesLabel(GameOptions o) {
        return switch (o.getParticles().getValue().name()) {
            case "ALL" -> "Tutte";
            case "DECREASED" -> "Ridotte";
            case "MINIMAL" -> "Minime";
            default -> o.getParticles().getValue().name();
        };
    }

    // =========================================================================================
    // Helper di disegno

    private static int lerp(int c1, int c2, float t) {
        int r = (int) (((c1 >> 16) & 255) + (((c2 >> 16) & 255) - ((c1 >> 16) & 255)) * t);
        int g = (int) (((c1 >> 8) & 255) + (((c2 >> 8) & 255) - ((c1 >> 8) & 255)) * t);
        int b = (int) ((c1 & 255) + ((c2 & 255) - (c1 & 255)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Rettangolo con gli angoli smussati di 1 pixel. */
    private static void rr(DrawContext c, int x1, int y1, int x2, int y2, int color) {
        c.fill(x1 + 1, y1, x2 - 1, y2, color);
        c.fill(x1, y1 + 1, x2, y2 - 1, color);
    }

    private static void box(DrawContext c, int x1, int y1, int x2, int y2, int fill, int border) {
        rr(c, x1, y1, x2, y2, border);
        rr(c, x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);
    }

    private String fit(String t, int maxW) {
        if (maxW <= 0) {
            return "";
        }
        if (this.textRenderer.getWidth(t) <= maxW) {
            return t;
        }
        String cut = t;
        while (!cut.isEmpty() && this.textRenderer.getWidth(cut + "...") > maxW) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    private void bold(DrawContext c, String text, int x, int y, int color) {
        c.drawTextWithShadow(this.textRenderer, Text.literal(text).formatted(Formatting.BOLD), x, y, color);
    }

    private int boldWidth(String text) {
        return this.textRenderer.getWidth(Text.literal(text).formatted(Formatting.BOLD));
    }

    private void drawSwitch(DrawContext c, int x, int y, boolean on, boolean enabled) {
        int track = on ? (enabled ? C_ACCENT_D : 0xFF3A4766) : C_BORDER;
        rr(c, x, y, x + 22, y + 12, track);
        int kx = on ? x + 12 : x + 2;
        rr(c, kx, y + 2, kx + 8, y + 10, enabled ? C_TEXT : 0xFF9CA3AF);
    }

    // =========================================================================================
    // Disegno

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Lo sfondo lo disegna render(): niente sfocatura di Minecraft.
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        PerformanceMonitor monitor = AetherPerformance.monitor();
        if (!RenderingConfig.overlay && !RenderingConfig.auto) {
            monitor.update(); // altrimenti i frame li misura già l'HUD
        }

        c.fillGradient(0, 0, this.width, this.height, 0xB0070A10, 0xD0070A10);

        // Pannello
        box(c, px - 1, py - 1, px + pw + 1, py + ph + 1, C_BORDER, C_BORDER);
        rr(c, px, py, px + pw, py + ph, C_CARD);
        drawHeader(c, monitor);

        // Barra laterale e footer
        c.fill(px, py + HEADER_H, px + SIDEBAR_W, footerTop, 0xFF121520);
        c.fill(px + SIDEBAR_W, py + HEADER_H, px + SIDEBAR_W + 1, footerTop, C_BORDER);
        c.fill(px, footerTop, px + pw, py + ph - 1, 0xFF0D1016);
        c.fill(px, footerTop, px + pw, footerTop + 1, C_BORDER);

        // Titolo della scheda
        bold(c, tab.title, mainX, mainY, C_TEXT);
        c.drawTextWithShadow(this.textRenderer, fit(tab.subtitle, mainW), mainX, mainY + 11, C_MUTED);

        // Intestazioni di sezione
        for (Label l : labels) {
            c.drawTextWithShadow(this.textRenderer, fit(l.text(), l.w()), l.x(), l.y(), C_MUTED);
        }

        // Controlli e suggerimento sotto il puntatore
        String hint = "";
        for (Ctl ct : ctls) {
            ct.draw(c, mouseX, mouseY);
            if (!ct.hint.isEmpty() && ct.contains(mouseX, mouseY)) {
                hint = ct.on() ? ct.hint : ct.offHint;
            }
        }
        int hintY = footerTop - 11;
        c.fill(mainX, hintY - 3, mainX + mainW, hintY - 2, C_BORDER);
        c.drawTextWithShadow(this.textRenderer, fit(hint, mainW), mainX, hintY, 0xFF7C8798);

        // Notifica
        if (System.currentTimeMillis() < toastUntil) {
            int tw = this.textRenderer.getWidth(toastText) + 24;
            int tx = px + pw - 10 - tw;
            int ty = footerTop - 38;
            box(c, tx, ty, tx + tw, ty + 18, 0xFF059669, 0xFF34D399);
            c.drawTextWithShadow(this.textRenderer, toastText, tx + 12, ty + 5, C_TEXT);
        }
    }

    private void drawHeader(DrawContext c, PerformanceMonitor m) {
        int x1 = px + 1;
        int x2 = px + pw - 1;
        for (int x = x1; x < x2; x += 4) {
            float t = (x - x1) / (float) (x2 - x1);
            int col = t < 0.5f ? lerp(0xFF1A274D, C_CARD, t * 2f) : lerp(C_CARD, 0xFF132A2B, (t - 0.5f) * 2f);
            c.fill(x, py + 1, Math.min(x + 4, x2), py + HEADER_H, col);
        }
        c.fill(px, py + HEADER_H, px + pw, py + HEADER_H + 1, C_BORDER);

        // Logo
        box(c, px + 10, py + 7, px + 34, py + 29, 0xFF1B2A4A, 0xFF284274);
        bold(c, "A", px + 19, py + 14, C_ACCENT_L);

        // Titolo e versione
        bold(c, "AETHER PERFORMANCE", px + 42, py + 7, C_TEXT);
        int vx = px + 42 + boldWidth("AETHER PERFORMANCE") + 6;
        if (!version.isEmpty()) {
            String v = "v" + version;
            int vw = this.textRenderer.getWidth(v) + 8;
            box(c, vx, py + 6, vx + vw, py + 17, 0xFF1B2A4A, 0xFF284274);
            c.drawTextWithShadow(this.textRenderer, v, vx + 4, py + 8, 0xFF93C5FD);
        }
        c.drawTextWithShadow(this.textRenderer, "Fabric - Minecraft 1.21.11", px + 42, py + 19, C_MUTED);

        // Stato a destra (se c'è spazio)
        double fps = m.averageFps();
        String status = "Attivo";
        String fpsText = fps > 0 ? String.format(Locale.ROOT, "FPS %.0f", fps) : "FPS --";
        String lowText = fps > 0 ? String.format(Locale.ROOT, "1%% LOW %.0f", m.low1Fps()) : "1% LOW --";
        int sw = 10 + this.textRenderer.getWidth(status) + 12 + this.textRenderer.getWidth(fpsText)
                + 12 + this.textRenderer.getWidth(lowText) + 10;
        int sx2 = px + pw - 30;
        int sx1 = sx2 - sw;
        if (sx1 > px + 42 + this.textRenderer.getWidth("Fabric - Minecraft 1.21.11") + 12) {
            box(c, sx1, py + 8, sx2, py + 26, C_DARK, C_BORDER);
            int dotAlpha = 130 + (int) (125 * (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 350.0)));
            c.fill(sx1 + 8, py + 15, sx1 + 12, py + 19, (dotAlpha << 24) | 0x10B981);
            int tx = sx1 + 16;
            c.drawTextWithShadow(this.textRenderer, status, tx, py + 14, C_GREEN_L);
            tx += this.textRenderer.getWidth(status) + 6;
            c.fill(tx, py + 12, tx + 1, py + 22, C_BORDER);
            tx += 6;
            c.drawTextWithShadow(this.textRenderer, fpsText, tx, py + 14, C_ACCENT_L);
            tx += this.textRenderer.getWidth(fpsText) + 6;
            c.fill(tx, py + 12, tx + 1, py + 22, C_BORDER);
            tx += 6;
            c.drawTextWithShadow(this.textRenderer, lowText, tx, py + 14, C_MUTED);
        }
    }

    // =========================================================================================
    // Input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) {
            return false;
        }
        double mx = click.x();
        double my = click.y();
        for (int i = ctls.size() - 1; i >= 0; i--) {
            Ctl ct = ctls.get(i);
            if (ct.contains(mx, my) && ct.on()) {
                if (ct.click(mx, my)) {
                    active = ct;
                }
                return true; // la lista può essere cambiata dall'azione: esci subito
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (active != null) {
            active.drag(click.x(), click.y());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (active != null) {
            active.release();
            active = null;
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        s.save();
        opts().write();
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    // =========================================================================================
    // Controlli

    private abstract class Ctl {
        int x;
        int y;
        int w;
        int h;
        String hint = "";
        String offHint = "";
        BooleanSupplier enabled = () -> true;

        Ctl(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        Ctl tip(String t) {
            this.hint = t;
            return this;
        }

        Ctl when(BooleanSupplier e, String off) {
            this.enabled = e;
            this.offHint = off;
            return this;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        boolean on() {
            return enabled.getAsBoolean();
        }

        abstract void draw(DrawContext c, int mx, int my);

        /** Restituisce true se il controllo vuole ricevere anche trascinamento e rilascio. */
        boolean click(double mx, double my) {
            return false;
        }

        void drag(double mx, double my) { }

        void release() { }
    }

    /** Pulsante: stile 0 = discreto, 1 = secondario, 2 = principale. */
    private class Btn extends Ctl {
        final String label;
        final int style;
        final Runnable action;

        Btn(String label, int x, int y, int w, int h, int style, Runnable action) {
            super(x, y, w, h);
            this.label = label;
            this.style = style;
            this.action = action;
        }

        @Override
        void draw(DrawContext c, int mx, int my) {
            boolean hov = contains(mx, my);
            int fill;
            int border;
            int text;
            if (style == 2) {
                fill = hov ? C_ACCENT : C_ACCENT_D;
                border = C_ACCENT_L;
                text = C_TEXT;
            } else if (style == 1) {
                fill = hov ? 0xFF2A2F3D : 0xFF222733;
                border = hov ? C_BORDER_HOT : C_BORDER;
                text = hov ? C_TEXT : 0xFFD1D5DB;
            } else {
                fill = hov ? 0xFF222733 : C_CARD2;
                border = hov ? C_BORDER_HOT : C_BORDER;
                text = hov ? C_TEXT : C_MUTED;
            }
            box(c, x, y, x + w, y + h, fill, border);
            int tw = AetherSettingsScreen.this.textRenderer.getWidth(label);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, label, x + (w - tw) / 2, y + (h - 8) / 2, text);
        }

        @Override
        boolean click(double mx, double my) {
            action.run();
            return false;
        }
    }

    private class TabCtl extends Ctl {
        final Tab t;

        TabCtl(Tab t, int x, int y, int w, int h) {
            super(x, y, w, h);
            this.t = t;
            this.hint = t.subtitle;
        }

        @Override
        void draw(DrawContext c, int mx, int my) {
            boolean sel = t == tab;
            boolean hov = contains(mx, my);
            if (sel) {
                box(c, x, y, x + w, y + h, 0xFF1D2B4D, 0xFF2F4F8F);
            } else if (hov) {
                box(c, x, y, x + w, y + h, 0xFF222733, 0xFF222733);
            }
            c.fill(x + 7, y + h / 2 - 3, x + 13, y + h / 2 + 3, sel ? C_ACCENT_L : (hov ? C_MUTED : 0xFF4B5263));
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, t.label, x + 19, y + (h - 8) / 2,
                    sel ? C_TEXT : (hov ? C_TEXT : C_MUTED));
        }

        @Override
        boolean click(double mx, double my) {
            switchTab(t);
            return false;
        }
    }

    private class PresetCtl extends Ctl {
        PresetCtl(int x, int y, int w, int h) {
            super(x, y, w, h);
        }

        @Override
        void draw(DrawContext c, int mx, int my) {
            box(c, x, y, x + w, y + h, C_DARK, C_BORDER);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, "PROFILO", x + 6, y + 5, C_MUTED);
            boolean hov = contains(mx, my);
            box(c, x + 5, y + 18, x + w - 5, y + h - 5, C_CARD, hov ? C_ACCENT : C_BORDER);
            String name = fit(modeName(s.mode), w - 28);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, name, x + 10, y + 21, C_TEXT);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, "v", x + w - 14, y + 21, C_MUTED);
        }

        @Override
        boolean click(double mx, double my) {
            cycleMode();
            return false;
        }
    }

    private class ToggleCard extends Ctl {
        final String title;
        final String desc;
        final BooleanSupplier get;
        final Consumer<Boolean> set;

        ToggleCard(int x, int y, int w, int h, String title, String desc, BooleanSupplier get, Consumer<Boolean> set) {
            super(x, y, w, h);
            this.title = title;
            this.desc = desc;
            this.get = get;
            this.set = set;
        }

        @Override
        void draw(DrawContext c, int mx, int my) {
            boolean hov = contains(mx, my);
            boolean value = get.getAsBoolean();
            box(c, x, y, x + w, y + h, C_CARD2, hov ? C_BORDER_HOT : C_BORDER);
            int avail = w - 8 - 22 - 12 - 8;
            if (desc == null) {
                c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, fit(title, avail), x + 8, y + (h - 8) / 2, C_TEXT);
            } else {
                c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, fit(title, avail), x + 8, y + 5, C_TEXT);
                c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, fit(desc, avail), x + 8, y + 16, C_MUTED);
            }
            drawSwitch(c, x + w - 8 - 22, y + (h - 12) / 2, value, true);
        }

        @Override
        boolean click(double mx, double my) {
            set.accept(!get.getAsBoolean());
            return false;
        }
    }

    private class MiniToggle extends Ctl {
        final String label;
        final BooleanSupplier get;
        final Consumer<Boolean> set;

        MiniToggle(int x, int y, String label, BooleanSupplier get, Consumer<Boolean> set) {
            super(x, y, AetherSettingsScreen.this.textRenderer.getWidth(label) + 6 + 22, 12);
            this.label = label;
            this.get = get;
            this.set = set;
        }

        @Override
        void draw(DrawContext c, int mx, int my) {
            boolean hov = contains(mx, my);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, label, x, y + 2, hov ? C_TEXT : C_MUTED);
            drawSwitch(c, x + w - 22, y, get.getAsBoolean(), true);
        }

        @Override
        boolean click(double mx, double my) {
            set.accept(!get.getAsBoolean());
            return false;
        }
    }

    private class CycleCard extends Ctl {
        final String title;
        final String desc;
        final Supplier<String> value;
        final Runnable action;

        CycleCard(int x, int y, int w, int h, String title, String desc, Supplier<String> value, Runnable action) {
            super(x, y, w, h);
            this.title = title;
            this.desc = desc;
            this.value = value;
            this.action = action;
        }

        @Override
        void draw(DrawContext c, int mx, int my) {
            boolean hov = contains(mx, my);
            box(c, x, y, x + w, y + h, C_CARD2, hov ? C_BORDER_HOT : C_BORDER);
            String v = value.get();
            int bw = AetherSettingsScreen.this.textRenderer.getWidth(v) + 16;
            int bx2 = x + w - 8;
            box(c, bx2 - bw, y + (h - 14) / 2, bx2, y + (h + 14) / 2, 0xFF1B2A4A, hov ? C_ACCENT : 0xFF284274);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, v, bx2 - bw + 8, y + (h - 8) / 2, C_ACCENT_L);
            int avail = w - 16 - bw - 6;
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, fit(title, avail), x + 8, y + 5, C_TEXT);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, fit(desc, avail), x + 8, y + 16, C_MUTED);
        }

        @Override
        boolean click(double mx, double my) {
            action.run();
            return false;
        }
    }

    private class SliderCard extends Ctl {
        final String title;
        final String desc;
        final int min;
        final int max;
        final int step;
        final IntSupplier get;
        final IntConsumer set;
        final IntFunction<String> fmt;
        final boolean live;
        boolean dragging;
        int pending;

        SliderCard(int x, int y, int w, int h, String title, String desc, int min, int max, int step,
                   IntSupplier get, IntConsumer set, IntFunction<String> fmt, boolean live) {
            super(x, y, w, h);
            this.title = title;
            this.desc = desc;
            this.min = min;
            this.max = max;
            this.step = step;
            this.get = get;
            this.set = set;
            this.fmt = fmt;
            this.live = live;
        }

        @Override
        void draw(DrawContext c, int mx, int my) {
            boolean enabledNow = on();
            boolean hov = contains(mx, my);
            box(c, x, y, x + w, y + h, C_CARD2, hov ? C_BORDER_HOT : C_BORDER);

            int val = dragging ? pending : get.getAsInt();
            String v = fmt.apply(val);
            int bw = AetherSettingsScreen.this.textRenderer.getWidth(v) + 10;
            int bx2 = x + w - 8;
            box(c, bx2 - bw, y + 3, bx2, y + 15, enabledNow ? 0xFF1B2A4A : 0xFF1C202B,
                    enabledNow ? 0xFF284274 : C_BORDER);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, v, bx2 - bw + 5, y + 5,
                    enabledNow ? C_ACCENT_L : C_MUTED);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, fit(title, w - 16 - bw - 6), x + 8, y + 5,
                    enabledNow ? C_TEXT : C_MUTED);
            if (desc != null) {
                c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, fit(desc, w - 16), x + 8, y + 16, C_MUTED);
            }

            int tx1 = x + 8;
            int tx2 = x + w - 8;
            int ty = y + h - 9;
            float f = Math.max(0f, Math.min(1f, (val - min) / (float) (max - min)));
            int thumb = tx1 + Math.round(f * (tx2 - tx1));
            c.fill(tx1, ty, tx2, ty + 3, C_BORDER);
            c.fill(tx1, ty, thumb, ty + 3, enabledNow ? C_ACCENT : 0xFF4B5263);
            if (enabledNow) {
                c.fill(thumb - 5, ty - 3, thumb + 5, ty + 6, 0x333B82F6);
            }
            c.fill(thumb - 3, ty - 2, thumb + 3, ty + 5,
                    enabledNow ? (dragging || hov ? C_ACCENT_L : C_TEXT) : 0xFF6B7280);
        }

        @Override
        boolean click(double mx, double my) {
            if (my < y + h - 16) {
                return false;
            }
            dragging = true;
            pending = get.getAsInt();
            update(mx);
            return true;
        }

        @Override
        void drag(double mx, double my) {
            update(mx);
        }

        @Override
        void release() {
            if (dragging) {
                dragging = false;
                if (!live) {
                    set.accept(pending);
                }
            }
        }

        private void update(double mx) {
            float f = (float) ((mx - (x + 8)) / (double) (w - 16));
            f = Math.max(0f, Math.min(1f, f));
            int raw = min + Math.round(f * (max - min) / (float) step) * step;
            raw = Math.max(min, Math.min(max, raw));
            if (raw != pending) {
                pending = raw;
                if (live) {
                    set.accept(raw);
                }
            }
        }
    }

    /** Scheda Diagnostica: 3 misure principali, 3 contatori di taglio e il grafico dei frame time. */
    private class DiagCtl extends Ctl {
        DiagCtl(int x, int y, int w, int h) {
            super(x, y, w, h);
        }

        @Override
        boolean contains(double mx, double my) {
            return false; // solo disegno
        }

        @Override
        void draw(DrawContext c, int mx, int my) {
            PerformanceMonitor m = AetherPerformance.monitor();
            int gap = 6;
            int cw = (w - 2 * gap) / 3;
            double fps = m.averageFps();
            boolean has = fps > 0;

            int fpsColor = fps >= 55 ? C_GREEN_L : (fps >= 30 ? C_YELLOW : C_RED);
            String stab = !has ? "In attesa di dati" : (m.low1Fps() >= fps * 0.7 ? "Frame pacing stabile" : "Cali di frame rilevati");
            metric(c, x, y, cw, "FPS ATTUALI", has ? String.format(Locale.ROOT, "%.0f", fps) : "--", fpsColor, stab);

            metric(c, x + cw + gap, y, cw, "FRAME TIME",
                    has ? String.format(Locale.ROOT, "%.1f ms", m.frameMsAvg()) : "--", C_ACCENT_L, "Obiettivo: < 16.6 ms");

            long max = m.maxMb();
            String ram = max >= 1024
                    ? String.format(Locale.ROOT, "%.1f / %.1f GB", m.usedMb() / 1024.0, max / 1024.0)
                    : m.usedMb() + " / " + max + " MB";
            double gc = m.gcPercent();
            String gcLabel = gc < 3 ? "Bassa" : (gc < 10 ? "Media" : "Alta");
            metric(c, x + 2 * (cw + gap), y, cw, "RAM USATA", ram, C_PURPLE, "Pressione GC: " + gcLabel);

            int y2 = y + 38;
            stat(c, x, y2, cw, "Entità tagliate/s", m.culledEntities());
            stat(c, x + cw + gap, y2, cw, "Block entity/s", m.culledBlockEntities());
            stat(c, x + 2 * (cw + gap), y2, cw, "Particelle/s", m.culledParticles());

            drawChart(c, m, y + 66, h - 66);
        }

        private void metric(DrawContext c, int cx, int cy, int cw, String label, String value, int color, String sub) {
            box(c, cx, cy, cx + cw, cy + 34, C_CARD2, C_BORDER);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, fit(label, cw - 12), cx + 6, cy + 4, C_MUTED);
            bold(c, fit(value, cw - 12), cx + 6, cy + 14, color);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, fit(sub, cw - 12), cx + 6, cy + 24, 0xFF6B7280);
        }

        private void stat(DrawContext c, int cx, int cy, int cw, String label, long value) {
            box(c, cx, cy, cx + cw, cy + 24, C_CARD2, C_BORDER);
            String v = String.valueOf(value);
            int vw = boldWidth(v);
            bold(c, v, cx + cw - 6 - vw, cy + 8, value > 0 ? C_GREEN_L : C_MUTED);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, fit(label, cw - 18 - vw), cx + 6, cy + 8, C_MUTED);
        }

        private void drawChart(DrawContext c, PerformanceMonitor m, int cy, int ch) {
            box(c, x, cy, x + w, cy + ch, C_CARD2, C_BORDER);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, "CRONOLOGIA FRAME TIME (ms)", x + 8, cy + 6, C_TEXT);

            int ax1 = x + 8;
            int ax2 = x + w - 8;
            int ay1 = cy + 20;
            int ay2 = cy + ch - 7;
            int cwid = ax2 - ax1;
            int chei = ay2 - ay1;
            if (chei < 10) {
                return;
            }

            int n = m.copyHistory(hist);
            int cols = Math.min(n, cwid);
            double maxMs = 16.7;
            for (int i = n - cols; i < n; i++) {
                maxMs = Math.max(maxMs, hist[i]);
            }
            maxMs = Math.min(66.7, Math.ceil(maxMs / 8.0) * 8.0);

            for (int g = 1; g <= 3; g++) {
                int gy = ay2 - chei * g / 4;
                c.fill(ax1, gy, ax2, gy + 1, 0xFF20252F);
            }
            c.fill(ax1, ay2, ax2, ay2 + 1, C_BORDER);

            for (int i = 0; i < cols; i++) {
                double ms = hist[n - cols + i];
                int bh = (int) Math.max(1, Math.min(chei, ms / maxMs * chei));
                int bx = ax2 - cols + i;
                int top = ay2 - bh;
                int lineCol = ms > 33.4 ? C_RED : (ms > 16.8 ? C_YELLOW : C_ACCENT_L);
                c.fill(bx, top, bx + 1, ay2, 0x553B82F6);
                c.fill(bx, top, bx + 1, Math.min(top + 2, ay2), lineCol);
            }

            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer,
                    String.format(Locale.ROOT, "%.0f ms", maxMs), ax1 + 2, ay1 + 1, 0xFF6B7280);
        }
    }
}
