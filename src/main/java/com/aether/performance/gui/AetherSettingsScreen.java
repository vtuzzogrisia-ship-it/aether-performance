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
import net.minecraft.client.network.ServerInfo;
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
 * Schermata impostazioni (tasto K): barra laterale con 6 schede, header con stato,
 * footer con Ripristina / Annulla / Applica. Tutti i controlli sono disegnati a mano,
 * con piccole animazioni (hover, interruttori, indicatore della scheda, ingresso del pannello).
 */
public class AetherSettingsScreen extends Screen {

    // --- Palette -------------------------------------------------------------------------
    private static final int C_DARK = 0xFF0E1016;
    private static final int C_PANEL = 0xFF151822;
    private static final int C_CARD2 = 0xFF121520;
    private static final int C_BORDER = 0xFF282D3B;
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
    private static final int C_DIM = 0xFF6B7280;

    private static final int SIDEBAR_W = 112;
    private static final int HEADER_H = 36;
    private static final int FOOTER_H = 30;

    private enum Tab {
        GENERAL("Generale", "Impostazioni generali", "Distanze, limite FPS, V-Sync e preset grafici rapidi."),
        GRAPHICS("Grafica", "Grafica ed effetti", "Bilancia qualità visiva e carico di rendering."),
        BOOST("Boost FPS", "Ottimizzazioni Aether", "Cosa smettere di disegnare per guadagnare FPS."),
        SERVER("Server", "Giocatore su server", "Impostazioni dedicate al multigiocatore."),
        DISTANCES("Distanze", "Distanze di taglio", "Valori precisi per il profilo Personalizzato."),
        DIAGNOSTICS("Diagnostica", "Diagnostica in tempo reale", "FPS, frame time, memoria, ping e oggetti tagliati.");

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

    private record VanillaSnap(int view, int sim, int fps, boolean vsync, boolean ao, boolean shadows,
                               Object particles, Object clouds, int biome, double entityScale) { }

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
    private int tabX;
    private int tabW;
    private int tabH;
    private int[] tabYs = new int[0];

    private float selY = -1f;
    private float frameDt = 0.016f;
    private long lastNanos;
    private final long openedAt = System.currentTimeMillis();

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

        pw = Math.min(this.width - 16, 520);
        ph = Math.min(this.height - 12, 292);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        footerTop = py + ph - FOOTER_H;
        mainX = px + SIDEBAR_W + 12;
        mainW = px + pw - 12 - mainX;
        mainY = py + HEADER_H + 10;
        contentTop = mainY + 28;
        contentBottom = footerTop - 14;

        // Barra laterale
        tabX = px + 8;
        tabW = SIDEBAR_W - 16;
        tabH = ph >= 280 ? 22 : 18;
        int by = py + HEADER_H + 10;
        Tab[] all = Tab.values();
        tabYs = new int[all.length];
        for (int i = 0; i < all.length; i++) {
            tabYs[i] = by;
            ctls.add(new TabCtl(all[i], i, tabX, by, tabW, tabH));
            by += tabH + 4;
        }
        int presetY = footerTop - 8 - 44;
        if (by <= presetY + 2) {
            ctls.add(new PresetCtl(tabX, presetY, tabW, 44).tip("Clic per cambiare profilo di prestazioni"));
        }

        switch (tab) {
            case GENERAL -> buildGeneral();
            case GRAPHICS -> buildGraphics();
            case BOOST -> buildBoost();
            case SERVER -> buildServer();
            case DISTANCES -> buildDistances();
            case DIAGNOSTICS -> buildDiagnostics();
        }

        // Footer
        int fy = footerTop + 6;
        ctls.add(new Btn("Ripristina valori", px + 10, fy, 104, 18, 0, this::resetDefaults)
                .tip("Torna ai valori iniziali di Aether Performance"));
        int applyW = 112;
        int discardW = 76;
        int applyX = px + pw - 10 - applyW;
        ctls.add(new Btn("Applica modifiche", applyX, fy, applyW, 18, 2, this::apply)
                .tip("Salva le modifiche (anche le opzioni di gioco)"));
        ctls.add(new Btn("Annulla", applyX - 6 - discardW, fy, discardW, 18, 1, this::discard)
                .tip("Torna ai valori di quando hai aperto il menu"));

        // Chiudi
        ctls.add(new Btn("x", px + pw - 22, py + 11, 14, 14, 0, this::close).tip("Chiudi (Esc)"));
    }

    private void buildGeneral() {
        GameOptions o = opts();
        int gap = 6;
        int hw = (mainW - gap) / 2;
        int x2 = mainX + hw + gap;
        int y = contentTop;
        ctls.add(new SliderCard(mainX, y, hw, 36, "Distanza di rendering", "Chunk visibili intorno a te",
                2, 32, 1, () -> o.getViewDistance().getValue(), v -> o.getViewDistance().setValue(v),
                v -> v + " chunk", false).tip("Più chunk = più bello ma più pesante (si applica al rilascio)"));
        ctls.add(new SliderCard(x2, y, hw, 36, "Distanza di simulazione", "Dove il mondo resta attivo",
                5, 32, 1, () -> o.getSimulationDistance().getValue(), v -> o.getSimulationDistance().setValue(v),
                v -> v + " chunk", false).tip("Quanto lontano mob e coltivazioni restano attivi"));
        y += 40;
        ctls.add(new SliderCard(mainX, y, hw, 36, "Limite FPS", "Meno calore e consumi",
                10, 260, 10, () -> o.getMaxFps().getValue(), v -> o.getMaxFps().setValue(v),
                v -> v >= 260 ? "Illimitati" : v + " FPS", false).tip("Al massimo = illimitati"));
        ctls.add(new ToggleCard(x2, y, hw, 36, "V-Sync", "Sincronizza col monitor",
                () -> o.getEnableVsync().getValue(), v -> o.getEnableVsync().setValue(v))
                .tip("Elimina lo strappo dell'immagine, ma può aggiungere ritardo"));
        y += 46;
        labels.add(new Label("PRESET GRAFICI RAPIDI  -  cambiano distanze ed effetti di Minecraft in un clic", mainX, y, mainW));
        y += 13;
        int bw = (mainW - 3 * 5) / 4;
        String[] names = {"Qualità", "Bilanciato", "Prestazioni", "Massimi FPS"};
        String[] tips = {
                "Grafica bella: 16 chunk, particelle complete, ombre e luce fluida",
                "Compromesso: 12 chunk, particelle ridotte, nuvole veloci",
                "Per PC modesti: 8 chunk, niente ombre né luce fluida",
                "Il massimo degli FPS: 6 chunk, niente nuvole, particelle minime"
        };
        for (int i = 0; i < 4; i++) {
            final int level = i;
            ctls.add(new Btn(names[i], mainX + i * (bw + 5), y, bw, 20, i == 3 ? 2 : 1, () -> quickGraphics(level))
                    .tip(tips[i]));
        }
    }

    private void buildGraphics() {
        GameOptions o = opts();
        int gap = 6;
        int hw = (mainW - gap) / 2;
        int x2 = mainX + hw + gap;
        int y = contentTop;
        ctls.add(new ToggleCard(mainX, y, hw, 32, "Illuminazione fluida", "Luce morbida tra i blocchi",
                () -> o.getAo().getValue(), v -> o.getAo().setValue(v))
                .tip("Luce più morbida, costo piccolo"));
        ctls.add(new ToggleCard(x2, y, hw, 32, "Ombre delle entità", "Ombra sotto mob e oggetti",
                () -> o.getEntityShadows().getValue(), v -> o.getEntityShadows().setValue(v))
                .tip("Spegnerle fa guadagnare qualche FPS con molti mob"));
        y += 36;
        ctls.add(new CycleCard(mainX, y, hw, 32, "Particelle", "Fumo, pioggia, magia",
                () -> particlesLabel(o), () -> cycle(o.getParticles()))
                .tip("Clic per cambiare: Tutte / Ridotte / Minime"));
        ctls.add(new CycleCard(x2, y, hw, 32, "Nuvole", "Costano molto in alto",
                () -> cloudsLabel(o), () -> cycle(o.getCloudRenderMode()))
                .tip("Clic per cambiare: Dettagliate / Veloci / Nessuna (le nuvole spente danno FPS)"));
        y += 36;
        ctls.add(new SliderCard(mainX, y, hw, 36, "Fusione dei biomi", "Sfumatura dei colori",
                0, 7, 1, () -> o.getBiomeBlendRadius().getValue(), v -> o.getBiomeBlendRadius().setValue(v),
                v -> v == 0 ? "Spenta" : v + " bl", false).tip("Valori bassi alleggeriscono la costruzione dei chunk"));
        ctls.add(new SliderCard(x2, y, hw, 36, "Distanza entità", "Quanto lontano si vedono i mob",
                50, 500, 25, () -> (int) Math.round(o.getEntityDistanceScaling().getValue() * 100),
                v -> o.getEntityDistanceScaling().setValue(v / 100.0),
                v -> v + "%", false).tip("Opzione di Minecraft: meno % = meno entità disegnate"));
        y += 44;
        labels.add(new Label("Queste sono opzioni di Minecraft: restano salvate anche senza la mod.", mainX, y, mainW));
    }

    private void buildBoost() {
        int gap = 6;
        int cw = (mainW - 2 * gap) / 3;
        int hw = (mainW - gap) / 2;
        int y = contentTop;
        labels.add(new Label("COSA NON DISEGNARE QUANDO È LONTANO", mainX, y - 11, mainW));
        ctls.add(new ToggleCard(mainX, y, cw, 26, "Entità", null,
                () -> s.entityCulling, v -> { s.entityCulling = v; commit(); })
                .tip("Non disegna i mob lontani (boss e mob con nome restano)"));
        ctls.add(new ToggleCard(mainX + cw + gap, y, cw, 26, "Block entity", null,
                () -> s.beCulling, v -> { s.beCulling = v; commit(); })
                .tip("Non disegna casse, cartelli, banner ecc. lontani"));
        ctls.add(new ToggleCard(mainX + 2 * (cw + gap), y, cw, 26, "Particelle", null,
                () -> s.particleThrottling, v -> { s.particleThrottling = v; commit(); })
                .tip("Scarta le particelle lontane e dirada quelle a media distanza"));
        y += 30;
        ctls.add(new ToggleCard(mainX, y, hw, 32, "Oggetti e XP lontani", "Drop e sfere di esperienza",
                () -> s.smallEntityCulling, v -> { s.smallEntityCulling = v; commit(); })
                .tip("Salta oggetti a terra e sfere di XP oltre la distanza impostata"));
        ctls.add(new ToggleCard(mainX + hw + gap, y, hw, 32, "Distanza di rendering dinamica", "Solo in profilo Auto",
                () -> s.dynamicViewDistance, v -> { s.dynamicViewDistance = v; commit(); })
                .tip("In profilo Auto abbassa e rialza i chunk visibili (min. 8) per tenere gli FPS obiettivo"));
        y += 36;
        ctls.add(new BoostInfo(mainX, y, mainW, Math.min(40, contentBottom - y)));
    }

    private void buildServer() {
        int gap = 6;
        int hw = (mainW - gap) / 2;
        int x2 = mainX + hw + gap;
        int y = contentTop;
        ctls.add(new ConnCard(mainX, y, mainW, 38));
        y += 42;
        ctls.add(new ToggleCard(mainX, y, hw, 32, "Profilo per i server", "Cambia profilo da solo",
                () -> s.useServerProfile, v -> { s.useServerProfile = v; RenderingConfig.autoScale = 1.0; commit(); })
                .tip("Quando entri in un server usa il profilo scelto qui a fianco"));
        ctls.add(new CycleCard(x2, y, hw, 32, "Profilo sui server", "Usato in multigiocatore",
                () -> modeName(s.serverMode), this::cycleServerMode)
                .tip("Clic per cambiare: Bilanciato / Prestazioni / Estremo / Auto / Personalizzato")
                .when(() -> s.useServerProfile, "Attiva prima 'Profilo per i server'"));
        y += 36;
        ctls.add(new ToggleCard(mainX, y, hw, 32, "Nascondi giocatori lontani", "Server affollati",
                () -> s.distantPlayerCulling, v -> { s.distantPlayerCulling = v; commit(); })
                .tip("Non disegna i giocatori oltre la distanza (mai quelli che cavalchi o con passeggeri)"));
        ctls.add(new SliderCard(x2, y, hw, 32, "Distanza giocatori", null,
                32, 256, 8, () -> s.playerCullingDistance, v -> { s.playerCullingDistance = v; RenderingConfig.apply(s); },
                v -> v + " bl", true).tip("Oltre questa distanza i giocatori non vengono disegnati")
                .when(() -> s.distantPlayerCulling, "Attiva prima 'Nascondi giocatori lontani'"));
        y += 36;
        ctls.add(new ToggleCard(mainX, y, hw, 28, "Ping nell'overlay", null,
                () -> s.showPing, v -> { s.showPing = v; commit(); })
                .tip("Mostra la latenza nell'overlay (tasto J) quando sei su un server"));
        ctls.add(new ToggleCard(x2, y, hw, 28, "Overlay sullo schermo", null,
                () -> s.showOverlay, v -> { s.showOverlay = v; commit(); })
                .tip("FPS, RAM e ping sempre visibili (tasto J)"));
    }

    private void buildDistances() {
        int gap = 6;
        int hw = (mainW - gap) / 2;
        int x2 = mainX + hw + gap;
        int y = contentTop;
        BooleanSupplier custom = () -> s.mode == PerformanceMode.CUSTOM
                || (s.useServerProfile && s.serverMode == PerformanceMode.CUSTOM);
        String offTip = "Disponibile solo nel profilo Personalizzato";
        labels.add(new Label("Scegli il profilo 'Personalizzato' (barra a sinistra) per modificarli.", mainX, y - 11, mainW));

        ctls.add(new SliderCard(mainX, y, hw, 34, "Block entity", "Casse, cartelli, banner", 16, 256, 8,
                () -> s.beCullingDistance, v -> { s.beCullingDistance = v; RenderingConfig.apply(s); },
                v -> v + " bl", true).tip("Oltre questa distanza le block entity spariscono").when(custom, offTip));
        ctls.add(new SliderCard(x2, y, hw, 34, "Entità", "Mob e animali", 32, 512, 16,
                () -> s.entityCullingDistance, v -> { s.entityCullingDistance = v; RenderingConfig.apply(s); },
                v -> v + " bl", true).tip("Oltre questa distanza i mob non vengono disegnati").when(custom, offTip));
        y += 38;
        ctls.add(new SliderCard(mainX, y, hw, 34, "Particelle", "Distanza massima", 8, 128, 4,
                () -> s.particleDistance, v -> { s.particleDistance = v; RenderingConfig.apply(s); },
                v -> v + " bl", true).tip("Oltre questa distanza le particelle sono scartate").when(custom, offTip));
        ctls.add(new SliderCard(x2, y, hw, 34, "Diradamento particelle", "A media distanza", 1, 8, 1,
                () -> s.particleThinning, v -> { s.particleThinning = v; RenderingConfig.apply(s); },
                v -> "1/" + v, true).tip("A media distanza si vede 1 particella su N").when(custom, offTip));
        y += 38;
        ctls.add(new SliderCard(mainX, y, hw, 34, "Oggetti e XP", "Drop a terra", 8, 128, 4,
                () -> s.smallEntityDistance, v -> { s.smallEntityDistance = v; RenderingConfig.apply(s); },
                v -> v + " bl", true).tip("Oltre questa distanza oggetti e sfere XP non vengono disegnati").when(custom, offTip));
        ctls.add(new SliderCard(x2, y, hw, 34, "FPS obiettivo", "Profilo Auto", 30, 240, 10,
                () -> s.targetFps, v -> s.targetFps = v,
                v -> v + " FPS", true).tip("Gli FPS che il profilo Auto cerca di mantenere")
                .when(() -> s.effectiveMode() == PerformanceMode.AUTO, "Disponibile solo nel profilo Auto"));
    }

    private void buildDiagnostics() {
        int h = contentBottom - contentTop;
        ctls.add(new DiagCtl(mainX, contentTop, mainW, h));

        int chartY = contentTop + 78;
        MiniToggle ping = new MiniToggle(0, chartY + 4, "Ping", () -> s.showPing,
                v -> { s.showPing = v; commit(); });
        ping.x = mainX + mainW - 8 - ping.w;
        ping.tip("Mostra il ping nell'overlay quando sei su un server");
        MiniToggle graph = new MiniToggle(0, chartY + 4, "Grafico", () -> s.overlayGraph,
                v -> { s.overlayGraph = v; commit(); });
        graph.x = ping.x - 10 - graph.w;
        graph.tip("Mostra il grafico dei frame nell'overlay sullo schermo");
        MiniToggle overlay = new MiniToggle(0, chartY + 4, "Overlay", () -> s.showOverlay,
                v -> { s.showOverlay = v; commit(); });
        overlay.x = graph.x - 10 - overlay.w;
        overlay.tip("Mostra FPS, RAM e grafico sullo schermo di gioco (tasto J)");
        ctls.add(overlay);
        ctls.add(graph);
        ctls.add(ping);
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
        RenderingConfig.autoScale = 1.0;
        commit();
    }

    private void cycleServerMode() {
        PerformanceMode[] all = PerformanceMode.values();
        s.serverMode = all[(s.serverMode.ordinal() + 1) % all.length];
        RenderingConfig.autoScale = 1.0;
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

    private static int modeColor(PerformanceMode m) {
        return switch (m) {
            case BALANCED -> C_ACCENT_L;
            case PERFORMANCE -> C_GREEN_L;
            case EXTREME -> C_RED;
            case AUTO -> C_PURPLE;
            case CUSTOM -> C_YELLOW;
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
                o.getEntityShadows().getValue(), o.getParticles().getValue(), o.getCloudRenderMode().getValue(),
                o.getBiomeBlendRadius().getValue(), o.getEntityDistanceScaling().getValue());
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
        setRaw(o.getCloudRenderMode(), v.clouds());
        o.getBiomeBlendRadius().setValue(v.biome());
        o.getEntityDistanceScaling().setValue(v.entityScale());
    }

    /** Preset grafici rapidi: 0 Qualità, 1 Bilanciato, 2 Prestazioni, 3 Massimi FPS. */
    private void quickGraphics(int level) {
        GameOptions o = opts();
        int[] view = {16, 12, 8, 6};
        int[] sim = {12, 10, 8, 5};
        String[] particles = {"ALL", "DECREASED", "DECREASED", "MINIMAL"};
        boolean[] ao = {true, true, false, false};
        boolean[] shadows = {true, true, false, false};
        String[] clouds = {"FANCY", "FAST", "FAST", "OFF"};
        int[] biome = {5, 3, 1, 0};
        double[] entity = {1.0, 1.0, 0.75, 0.5};

        o.getViewDistance().setValue(view[level]);
        o.getSimulationDistance().setValue(sim[level]);
        setByName(o.getParticles(), particles[level]);
        o.getAo().setValue(ao[level]);
        o.getEntityShadows().setValue(shadows[level]);
        setByName(o.getCloudRenderMode(), clouds[level]);
        o.getBiomeBlendRadius().setValue(biome[level]);
        o.getEntityDistanceScaling().setValue(entity[level]);
        toast("Preset '" + new String[] {"Qualità", "Bilanciato", "Prestazioni", "Massimi FPS"}[level]
                + "' pronto: premi Applica per salvarlo.");
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

    private static <E extends Enum<E>> void setByName(SimpleOption<E> option, String name) {
        for (E e : option.getValue().getDeclaringClass().getEnumConstants()) {
            if (e.name().equals(name)) {
                option.setValue(e);
                return;
            }
        }
    }

    private static String particlesLabel(GameOptions o) {
        String n = o.getParticles().getValue().name();
        return switch (n) {
            case "ALL" -> "Tutte";
            case "DECREASED" -> "Ridotte";
            case "MINIMAL" -> "Minime";
            default -> n;
        };
    }

    private static String cloudsLabel(GameOptions o) {
        String n = o.getCloudRenderMode().getValue().name();
        return switch (n) {
            case "OFF" -> "Nessuna";
            case "FAST" -> "Veloci";
            case "FANCY" -> "Dettagliate";
            default -> n;
        };
    }

    // =========================================================================================
    // Helper di disegno

    private static int lerp(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((c1 >> 16) & 255) + (((c2 >> 16) & 255) - ((c1 >> 16) & 255)) * t);
        int g = (int) (((c1 >> 8) & 255) + (((c2 >> 8) & 255) - ((c1 >> 8) & 255)) * t);
        int b = (int) ((c1 & 255) + ((c2 & 255) - (c1 & 255)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int alpha(int rgb, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    /** Rettangolo con gli angoli arrotondati (raggio 2 pixel). */
    private static void rr(DrawContext c, int x1, int y1, int x2, int y2, int color) {
        if (x2 - x1 < 6 || y2 - y1 < 6) {
            c.fill(x1, y1, x2, y2, color);
            return;
        }
        c.fill(x1 + 2, y1, x2 - 2, y2, color);
        c.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, color);
        c.fill(x1, y1 + 2, x2, y2 - 2, color);
    }

    private static void box(DrawContext c, int x1, int y1, int x2, int y2, int fill, int border) {
        rr(c, x1, y1, x2, y2, border);
        rr(c, x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);
    }

    /** Sfondo di una scheda: bordo che si illumina al passaggio del mouse e riflesso in alto. */
    private static void card(DrawContext c, int x, int y, int w, int h, float hover, float on) {
        rr(c, x, y, x + w, y + h, lerp(C_BORDER, C_ACCENT, hover * 0.6f));
        rr(c, x + 1, y + 1, x + w - 1, y + h - 1, lerp(C_CARD2, 0xFF171B27, hover));
        c.fillGradient(x + 2, y + 1, x + w - 2, y + 1 + Math.max(2, h / 2), 0x12FFFFFF, 0x00FFFFFF);
        if (on > 0.02f) {
            c.fill(x + 1, y + 4, x + 3, y + h - 4, alpha(C_ACCENT_L, (int) (230 * on)));
        }
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

    private void text(DrawContext c, String t, int x, int y, int color) {
        c.drawTextWithShadow(this.textRenderer, t, x, y, color);
    }

    private void bold(DrawContext c, String text, int x, int y, int color) {
        c.drawTextWithShadow(this.textRenderer, Text.literal(text).formatted(Formatting.BOLD), x, y, color);
    }

    private int boldWidth(String text) {
        return this.textRenderer.getWidth(Text.literal(text).formatted(Formatting.BOLD));
    }

    /** Testo ingrandito (scale 2 = doppio). Restituisce la larghezza occupata. */
    private int big(DrawContext c, String t, int x, int y, int color, int scale) {
        var m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        c.drawTextWithShadow(this.textRenderer, t, 0, 0, color);
        m.popMatrix();
        return this.textRenderer.getWidth(t) * scale;
    }

    private static float pulse(double speed) {
        return (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / speed));
    }

    private void drawSwitch(DrawContext c, int x, int y, float t, boolean enabled) {
        int off = 0xFF2B3140;
        int on = enabled ? C_ACCENT_D : 0xFF3A4766;
        if (t > 0.05f && enabled) {
            c.fill(x - 1, y - 1, x + 25, y + 13, alpha(C_ACCENT, (int) (36 * t)));
        }
        rr(c, x, y, x + 24, y + 12, lerp(off, on, t));
        int kx = x + 2 + Math.round(t * 12);
        rr(c, kx, y + 2, kx + 8, y + 10, enabled ? lerp(0xFFB4BBC8, C_TEXT, t) : 0xFF8A93A3);
    }

    private void drawIcon(DrawContext c, int index, int x, int y, int color) {
        switch (index) {
            case 0 -> { // regolatori
                for (int i = 0; i < 3; i++) {
                    c.fill(x, y + 1 + i * 3, x + 8, y + 2 + i * 3, alpha(color, 150));
                }
                c.fill(x + 1, y, x + 3, y + 3, color);
                c.fill(x + 5, y + 3, x + 7, y + 6, color);
                c.fill(x + 2, y + 6, x + 4, y + 9, color);
            }
            case 1 -> { // sole
                c.fill(x + 2, y + 2, x + 6, y + 6, color);
                c.fill(x + 3, y, x + 5, y + 1, color);
                c.fill(x + 3, y + 7, x + 5, y + 8, color);
                c.fill(x, y + 3, x + 1, y + 5, color);
                c.fill(x + 7, y + 3, x + 8, y + 5, color);
                c.fill(x + 1, y + 1, x + 2, y + 2, color);
                c.fill(x + 6, y + 1, x + 7, y + 2, color);
                c.fill(x + 1, y + 6, x + 2, y + 7, color);
                c.fill(x + 6, y + 6, x + 7, y + 7, color);
            }
            case 2 -> bitmap(c, new String[] {
                    "....###.", "...###..", "..###...", ".#####..", "...###..", "..###...", ".###....", ".##....."}, x, y, color);
            case 3 -> bitmap(c, new String[] {
                    "########", "#.....##", "########", "........", "########", "#.....##", "########", "........"}, x, y, color);
            case 4 -> bitmap(c, new String[] {
                    "........", "..#..#..", ".##..##.", "########", ".##..##.", "..#..#..", "........", "........"}, x, y, color);
            default -> bitmap(c, new String[] {
                    "......#.", "..#...#.", "..#.#.#.", "#.#.#.#.", "#.#.#.#.", "########", "........", "........"}, x, y, color);
        }
    }

    private static void bitmap(DrawContext c, String[] rows, int x, int y, int color) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int col = 0; col < row.length(); col++) {
                if (row.charAt(col) == '#') {
                    c.fill(x + col, y + r, x + col + 1, y + r + 1, color);
                }
            }
        }
    }

    // =========================================================================================
    // Disegno

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Lo sfondo lo disegna render(): niente sfocatura di Minecraft.
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        long nowN = System.nanoTime();
        frameDt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (nowN - lastNanos) / 1_000_000_000f);
        lastNanos = nowN;

        PerformanceMonitor monitor = AetherPerformance.monitor();
        if (!RenderingConfig.overlay && !RenderingConfig.auto) {
            monitor.update(); // altrimenti i frame li misura già l'HUD
        }

        // Sfondo scurito con puntini luminosi che salgono lentamente
        c.fillGradient(0, 0, this.width, this.height, 0xC0060910, 0xE0090C14);
        drawDots(c);

        // Animazione d'ingresso: il pannello scivola in alto in 0,25 secondi
        float open = Math.min(1f, (System.currentTimeMillis() - openedAt) / 250f);
        float ease = 1f - (1f - open) * (1f - open) * (1f - open);
        var m = c.getMatrices();
        m.pushMatrix();
        m.translate(0f, (1f - ease) * 14f);

        // Ombra e pannello
        for (int k = 1; k <= 7; k++) {
            rr(c, px - k, py - k + 3, px + pw + k, py + ph + k + 3, 0x0B000000);
        }
        rr(c, px - 1, py - 1, px + pw + 1, py + ph + 1, C_BORDER);
        rr(c, px, py, px + pw, py + ph, C_PANEL);
        c.fillGradient(px + 1, py + HEADER_H, px + pw - 1, py + ph - 2, 0xFF171B26, 0xFF11141C);
        drawHeader(c, monitor);

        // Barra laterale e footer
        c.fillGradient(px + 1, py + HEADER_H + 1, px + SIDEBAR_W, footerTop, 0xFF131724, 0xFF0F121B);
        c.fill(px + SIDEBAR_W, py + HEADER_H + 1, px + SIDEBAR_W + 1, footerTop, C_BORDER);
        c.fill(px + 1, footerTop, px + pw - 1, py + ph - 2, 0xFF0C0F15);
        c.fill(px + 1, footerTop, px + pw - 1, footerTop + 1, C_BORDER);

        // Indicatore della scheda attiva (scivola fra le voci)
        int target = tabYs.length > tab.ordinal() ? tabYs[tab.ordinal()] : 0;
        selY = selY < 0 ? target : selY + (target - selY) * Math.min(1f, frameDt * 14f);
        int sy = Math.round(selY);
        rr(c, tabX, sy, tabX + tabW, sy + tabH, 0xFF2A4170);
        rr(c, tabX + 1, sy + 1, tabX + tabW - 1, sy + tabH - 1, 0xFF1C2B4C);
        c.fillGradient(tabX + 2, sy + 1, tabX + tabW - 2, sy + tabH / 2, 0x22FFFFFF, 0x00FFFFFF);
        c.fill(tabX + 1, sy + 4, tabX + 3, sy + tabH - 4, C_ACCENT_L);

        // Titolo della scheda
        bold(c, tab.title, mainX, mainY, C_TEXT);
        c.fillGradient(mainX, mainY + 10, mainX + 28, mainY + 11, C_ACCENT_L, alpha(C_ACCENT_L, 0));
        c.drawTextWithShadow(this.textRenderer, fit(tab.subtitle, mainW), mainX, mainY + 14, C_MUTED);

        // Intestazioni di sezione
        for (Label l : labels) {
            c.drawTextWithShadow(this.textRenderer, fit(l.text(), l.w()), l.x(), l.y(), C_DIM);
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
        c.fill(mainX, hintY - 3, mainX + mainW, hintY - 2, 0xFF222736);
        c.drawTextWithShadow(this.textRenderer, fit(hint, mainW), mainX, hintY, 0xFF7C8798);

        // Notifica
        long left = toastUntil - System.currentTimeMillis();
        if (left > 0) {
            float in = Math.min(1f, (2500L - left) / 180f);
            int tw = this.textRenderer.getWidth(toastText) + 24;
            int tx = px + pw - 10 - tw;
            int ty = footerTop - 40 + Math.round((1f - in) * 10f);
            rr(c, tx - 1, ty - 1, tx + tw + 1, ty + 19, 0x4034D399);
            box(c, tx, ty, tx + tw, ty + 18, 0xFF059669, 0xFF34D399);
            c.drawTextWithShadow(this.textRenderer, toastText, tx + 12, ty + 5, C_TEXT);
        }

        m.popMatrix();
    }

    private void drawDots(DrawContext c) {
        double t = System.currentTimeMillis() / 1000.0;
        for (int i = 0; i < 34; i++) {
            double speed = 4.0 + (i * 7 % 9);
            int sx = (int) ((i * 97L + 13) % Math.max(1, this.width));
            double yy = ((i * 53L + 29) % Math.max(1, this.height)) - t * speed;
            yy = yy % this.height;
            if (yy < 0) {
                yy += this.height;
            }
            int a = 14 + (i * 13 % 26);
            int sz = (i % 4 == 0) ? 2 : 1;
            c.fill(sx, (int) yy, sx + sz, (int) yy + sz, alpha(i % 3 == 0 ? 0x34D399 : 0x60A5FA, a));
        }
    }

    private void drawHeader(DrawContext c, PerformanceMonitor m) {
        int x1 = px + 1;
        int x2 = px + pw - 1;
        for (int x = x1; x < x2; x += 4) {
            float t = (x - x1) / (float) (x2 - x1);
            int col = t < 0.5f ? lerp(0xFF1B2A55, 0xFF161A27, t * 2f) : lerp(0xFF161A27, 0xFF11302F, (t - 0.5f) * 2f);
            c.fillGradient(x, py + 1, Math.min(x + 4, x2), py + HEADER_H, col, lerp(col, 0xFF11141C, 0.55f));
        }
        // Linea d'accento animata in alto
        double phase = System.currentTimeMillis() / 1400.0;
        for (int x = x1 + 2; x < x2 - 2; x += 4) {
            float t = (float) (0.5 + 0.5 * Math.sin((x - x1) / 90.0 + phase));
            c.fill(x, py, Math.min(x + 4, x2 - 2), py + 2, lerp(C_ACCENT, C_GREEN_L, t));
        }
        c.fill(px + 1, py + HEADER_H, px + pw - 1, py + HEADER_H + 1, C_BORDER);

        // Logo con alone
        int glow = 20 + (int) (30 * pulse(700));
        rr(c, px + 8, py + 6, px + 38, py + 32, alpha(C_ACCENT, glow));
        box(c, px + 10, py + 8, px + 36, py + 30, 0xFF1B2A4A, 0xFF3A5FA8);
        c.fillGradient(px + 11, py + 9, px + 35, py + 19, 0x30FFFFFF, 0x00FFFFFF);
        int aw = boldWidth("A");
        bold(c, "A", px + 23 - aw / 2, py + 15, C_ACCENT_L);

        // Titolo e versione
        bold(c, "AETHER PERFORMANCE", px + 46, py + 8, C_TEXT);
        int vx = px + 46 + boldWidth("AETHER PERFORMANCE") + 6;
        if (!version.isEmpty()) {
            String v = "v" + version;
            int vw = this.textRenderer.getWidth(v) + 8;
            box(c, vx, py + 7, vx + vw, py + 18, 0xFF1B2A4A, 0xFF284274);
            c.drawTextWithShadow(this.textRenderer, v, vx + 4, py + 9, 0xFF93C5FD);
        }
        String sub = "Fabric - Minecraft 1.21.11";
        c.drawTextWithShadow(this.textRenderer, sub, px + 46, py + 21, C_MUTED);

        // Stato a destra (se c'è spazio)
        double fps = m.averageFps();
        String status = "Attivo";
        String fpsText = fps > 0 ? String.format(Locale.ROOT, "FPS %.0f", fps) : "FPS --";
        String lowText = fps > 0 ? String.format(Locale.ROOT, "1%% LOW %.0f", m.low1Fps()) : "1% LOW --";
        String pingText = m.pingMs() >= 0 ? "PING " + m.pingMs() : null;
        int sw = 10 + this.textRenderer.getWidth(status) + 12 + this.textRenderer.getWidth(fpsText)
                + 12 + this.textRenderer.getWidth(lowText) + 10;
        if (pingText != null) {
            sw += 12 + this.textRenderer.getWidth(pingText);
        }
        int sx2 = px + pw - 30;
        int sx1 = sx2 - sw;
        if (sx1 > px + 46 + this.textRenderer.getWidth(sub) + 12) {
            box(c, sx1, py + 9, sx2, py + 27, C_DARK, C_BORDER);
            int dotAlpha = 110 + (int) (145 * pulse(350));
            c.fill(sx1 + 8, py + 16, sx1 + 12, py + 20, alpha(C_GREEN, dotAlpha));
            c.fill(sx1 + 7, py + 15, sx1 + 13, py + 21, alpha(C_GREEN, dotAlpha / 5));
            int tx = sx1 + 16;
            int ty = py + 15;
            c.drawTextWithShadow(this.textRenderer, status, tx, ty, C_GREEN_L);
            tx += this.textRenderer.getWidth(status) + 6;
            c.fill(tx, py + 13, tx + 1, py + 23, C_BORDER);
            tx += 6;
            c.drawTextWithShadow(this.textRenderer, fpsText, tx, ty, C_ACCENT_L);
            tx += this.textRenderer.getWidth(fpsText) + 6;
            c.fill(tx, py + 13, tx + 1, py + 23, C_BORDER);
            tx += 6;
            c.drawTextWithShadow(this.textRenderer, lowText, tx, ty, C_MUTED);
            if (pingText != null) {
                tx += this.textRenderer.getWidth(lowText) + 6;
                c.fill(tx, py + 13, tx + 1, py + 23, C_BORDER);
                tx += 6;
                int p = m.pingMs();
                c.drawTextWithShadow(this.textRenderer, pingText, tx, ty, p < 60 ? C_GREEN_L : (p < 120 ? C_YELLOW : C_RED));
            }
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
        float hov;
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

        /** Valore 0..1 che segue il mouse con dolcezza (per le animazioni di hover). */
        float hover(boolean target) {
            hov += ((target ? 1f : 0f) - hov) * Math.min(1f, frameDt * 14f);
            return hov;
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
            float t = hover(contains(mx, my));
            int fill;
            int border;
            int text;
            if (style == 2) {
                fill = lerp(C_ACCENT_D, C_ACCENT, t);
                border = lerp(0xFF3B6FD8, C_ACCENT_L, t);
                text = C_TEXT;
                rr(c, x - 1, y - 1, x + w + 1, y + h + 1, alpha(C_ACCENT, (int) (24 + 50 * t)));
            } else if (style == 1) {
                fill = lerp(0xFF1E2330, 0xFF2A3040, t);
                border = lerp(C_BORDER, C_BORDER_HOT, t);
                text = lerp(0xFFD1D5DB, C_TEXT, t);
            } else {
                fill = lerp(C_CARD2, 0xFF222733, t);
                border = lerp(C_BORDER, C_BORDER_HOT, t);
                text = lerp(C_MUTED, C_TEXT, t);
            }
            box(c, x, y, x + w, y + h, fill, border);
            c.fillGradient(x + 2, y + 1, x + w - 2, y + 1 + Math.max(2, h / 2), 0x16FFFFFF, 0x00FFFFFF);
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
        final int index;

        TabCtl(Tab t, int index, int x, int y, int w, int h) {
            super(x, y, w, h);
            this.t = t;
            this.index = index;
            this.hint = t.subtitle;
        }

        @Override
        void draw(DrawContext c, int mx, int my) {
            boolean sel = t == tab;
            float hv = hover(contains(mx, my));
            if (!sel && hv > 0.02f) {
                rr(c, x, y, x + w, y + h, alpha(0xFFFFFF, (int) (14 * hv)));
            }
            int col = sel ? C_ACCENT_L : lerp(0xFF6B7280, C_TEXT, hv);
            drawIcon(c, index, x + 8, y + (h - 8) / 2, col);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, fit(t.label, w - 26), x + 21, y + (h - 8) / 2,
                    sel ? C_TEXT : lerp(C_MUTED, C_TEXT, hv));
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
            float hv = hover(contains(mx, my));
            box(c, x, y, x + w, y + h, C_DARK, C_BORDER);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, "PROFILO", x + 6, y + 5, C_DIM);

            PerformanceMode eff = s.effectiveMode();
            boolean serverActive = eff != s.mode;
            if (serverActive) {
                String tag = "SERVER";
                int tw = AetherSettingsScreen.this.textRenderer.getWidth(tag);
                c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, tag, x + w - 6 - tw, y + 5, C_GREEN_L);
            }

            rr(c, x + 5, y + 17, x + w - 5, y + h - 5, lerp(C_BORDER, C_ACCENT, hv));
            rr(c, x + 6, y + 18, x + w - 6, y + h - 6, 0xFF171B27);
            int dot = modeColor(s.mode);
            c.fill(x + 10, y + 22, x + 14, y + 26, dot);
            c.fill(x + 9, y + 21, x + 15, y + 27, alpha(dot, 50));
            String name = fit(modeName(s.mode), w - 38);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, name, x + 19, y + 21, C_TEXT);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, ">", x + w - 14, y + 21, lerp(C_DIM, C_TEXT, hv));

            if (s.mode == PerformanceMode.AUTO && !serverActive) {
                int pct = (int) Math.round(RenderingConfig.autoScale * 100);
                int bx1 = x + 10;
                int bx2 = x + w - 10;
                c.fill(bx1, y + h - 4, bx2, y + h - 3, 0xFF2A3040);
                c.fill(bx1, y + h - 4, bx1 + (bx2 - bx1) * pct / 100, y + h - 3, C_PURPLE);
            }
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
        float knob = -1f;

        ToggleCard(int x, int y, int w, int h, String title, String desc, BooleanSupplier get, Consumer<Boolean> set) {
            super(x, y, w, h);
            this.title = title;
            this.desc = desc;
            this.get = get;
            this.set = set;
        }

        @Override
        void draw(DrawContext c, int mx, int my) {
            boolean value = get.getAsBoolean();
            if (knob < 0f) {
                knob = value ? 1f : 0f;
            }
            knob += ((value ? 1f : 0f) - knob) * Math.min(1f, frameDt * 16f);
            float hv = hover(contains(mx, my));
            card(c, x, y, w, h, hv, knob);
            int avail = w - 10 - 24 - 14;
            if (desc == null) {
                c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, fit(title, avail), x + 9, y + (h - 8) / 2, C_TEXT);
            } else {
                c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, fit(title, avail), x + 9, y + h / 2 - 9, C_TEXT);
                c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, fit(desc, avail), x + 9, y + h / 2 + 2, C_MUTED);
            }
            drawSwitch(c, x + w - 8 - 24, y + (h - 12) / 2, knob, true);
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
        float knob = -1f;

        MiniToggle(int x, int y, String label, BooleanSupplier get, Consumer<Boolean> set) {
            super(x, y, AetherSettingsScreen.this.textRenderer.getWidth(label) + 6 + 24, 12);
            this.label = label;
            this.get = get;
            this.set = set;
        }

        @Override
        void draw(DrawContext c, int mx, int my) {
            boolean value = get.getAsBoolean();
            if (knob < 0f) {
                knob = value ? 1f : 0f;
            }
            knob += ((value ? 1f : 0f) - knob) * Math.min(1f, frameDt * 16f);
            float hv = hover(contains(mx, my));
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, label, x, y + 2, lerp(C_MUTED, C_TEXT, hv));
            drawSwitch(c, x + w - 24, y, knob, true);
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
            boolean enabledNow = on();
            float hv = hover(contains(mx, my) && enabledNow);
            card(c, x, y, w, h, hv, 0f);
            String v = value.get();
            int bw = AetherSettingsScreen.this.textRenderer.getWidth(v) + 18;
            int bx2 = x + w - 8;
            int by1 = y + (h - 16) / 2;
            box(c, bx2 - bw, by1, bx2, by1 + 16, enabledNow ? 0xFF1B2A4A : 0xFF1C202B,
                    enabledNow ? lerp(0xFF284274, C_ACCENT, hv) : C_BORDER);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, v, bx2 - bw + 6, by1 + 4,
                    enabledNow ? C_ACCENT_L : C_DIM);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, ">", bx2 - 10, by1 + 4,
                    enabledNow ? lerp(C_DIM, C_TEXT, hv) : C_DIM);
            int avail = w - 18 - bw - 6;
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, fit(title, avail), x + 9, y + h / 2 - 9,
                    enabledNow ? C_TEXT : C_MUTED);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, fit(desc, avail), x + 9, y + h / 2 + 2, C_MUTED);
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
        float shown = -1f;

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
            float hv = hover((contains(mx, my) && enabledNow) || dragging);
            card(c, x, y, w, h, hv, 0f);

            int val = dragging ? pending : get.getAsInt();
            float goal = Math.max(0f, Math.min(1f, (val - min) / (float) (max - min)));
            shown = shown < 0f || dragging ? goal : shown + (goal - shown) * Math.min(1f, frameDt * 16f);

            String v = fmt.apply(val);
            int bw = AetherSettingsScreen.this.textRenderer.getWidth(v) + 10;
            int bx2 = x + w - 8;
            box(c, bx2 - bw, y + 4, bx2, y + 16, enabledNow ? 0xFF1B2A4A : 0xFF1C202B,
                    enabledNow ? 0xFF284274 : C_BORDER);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, v, bx2 - bw + 5, y + 6,
                    enabledNow ? C_ACCENT_L : C_DIM);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, fit(title, w - 18 - bw - 4), x + 9, y + 6,
                    enabledNow ? C_TEXT : C_MUTED);
            if (desc != null && h >= 34) {
                c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, fit(desc, w - 18), x + 9, y + 17, C_DIM);
            }

            int tx1 = x + 9;
            int tx2 = x + w - 9;
            int ty = y + h - 9;
            int thumb = tx1 + Math.round(shown * (tx2 - tx1));
            rr(c, tx1, ty, tx2, ty + 4, 0xFF2A3040);
            for (int px2 = tx1; px2 < thumb; px2 += 3) {
                float t = (px2 - tx1) / (float) Math.max(1, tx2 - tx1);
                int col = enabledNow ? lerp(C_ACCENT_D, C_ACCENT_L, t) : 0xFF4B5263;
                c.fill(px2, ty, Math.min(px2 + 3, thumb), ty + 4, col);
            }
            if (enabledNow) {
                c.fill(thumb - 6, ty - 3, thumb + 6, ty + 7, alpha(C_ACCENT, (int) (28 + 40 * hv)));
            }
            rr(c, thumb - 3, ty - 2, thumb + 3, ty + 6,
                    enabledNow ? lerp(C_TEXT, C_ACCENT_L, dragging ? 1f : 0f) : 0xFF6B7280);
        }

        @Override
        boolean click(double mx, double my) {
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
            float f = (float) ((mx - (x + 9)) / (double) (w - 18));
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

    /** Riquadro di sola lettura nella scheda Boost: profilo attivo, fattore Auto e cosa viene tagliato. */
    private class BoostInfo extends Ctl {
        BoostInfo(int x, int y, int w, int h) {
            super(x, y, w, h);
        }

        @Override
        boolean contains(double mx, double my) {
            return false;
        }

        @Override
        void draw(DrawContext c, int mx, int my) {
            if (h < 24) {
                return;
            }
            PerformanceMonitor m = AetherPerformance.monitor();
            PerformanceMode eff = s.effectiveMode();
            card(c, x, y, w, h, 0f, 0f);
            int dot = modeColor(eff);
            c.fill(x + 10, y + 7, x + 14, y + 11, dot);
            c.fill(x + 9, y + 6, x + 15, y + 12, alpha(dot, 60));
            String mode = "Profilo attivo: " + modeName(eff) + (eff != s.mode ? " (server)" : "");
            bold(c, fit(mode, w / 2), x + 20, y + 6, C_TEXT);

            String line2 = eff == PerformanceMode.AUTO
                    ? String.format(Locale.ROOT, "Auto: distanze al %d%%  -  obiettivo %d FPS",
                    (int) Math.round(RenderingConfig.autoScale * 100), s.targetFps)
                    : "Distanze fisse del profilo";
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, fit(line2, w - 20), x + 10, y + 19, C_MUTED);
            if (h >= 38) {
                String line3 = String.format(Locale.ROOT, "Tagliati ora: %d entità/s  %d block entity/s  %d particelle/s",
                        m.culledEntities(), m.culledBlockEntities(), m.culledParticles());
                c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, fit(line3, w - 20), x + 10, y + 29, C_DIM);
            }
        }
    }

    /** Riquadro di stato del collegamento nella scheda Server. */
    private class ConnCard extends Ctl {
        ConnCard(int x, int y, int w, int h) {
            super(x, y, w, h);
        }

        @Override
        boolean contains(double mx, double my) {
            return false;
        }

        @Override
        void draw(DrawContext c, int mx, int my) {
            MinecraftClient mc = MinecraftClient.getInstance();
            PerformanceMonitor m = AetherPerformance.monitor();
            boolean online = RenderingConfig.onServer;
            card(c, x, y, w, h, 0f, online ? 1f : 0f);

            int dot = online ? C_GREEN : C_DIM;
            int a = online ? 110 + (int) (145 * pulse(350)) : 255;
            c.fill(x + 11, y + 8, x + 16, y + 13, alpha(dot, a));
            c.fill(x + 10, y + 7, x + 17, y + 14, alpha(dot, a / 5));

            ServerInfo info = mc.getCurrentServerEntry();
            String title;
            String detail;
            if (online) {
                title = info != null && info.name != null && !info.name.isEmpty() ? "Connesso a " + info.name
                        : "Connesso a un server multigiocatore";
                detail = info != null && info.address != null ? info.address : "Server locale (LAN) o Realms";
            } else {
                title = "Giocatore singolo";
                detail = "Entra in un server per usare queste impostazioni";
            }
            int rightW = 70;
            bold(c, fit(title, w - 24 - rightW), x + 22, y + 6, online ? C_TEXT : C_MUTED);
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, fit(detail, w - 24 - rightW), x + 22, y + 17,
                    C_DIM);

            PerformanceMode eff = s.effectiveMode();
            String profile = "Profilo in uso: " + modeName(eff) + (eff != s.mode ? " (profilo server)" : "");
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, fit(profile, w - 24 - rightW), x + 22, y + 27,
                    modeColor(eff));

            int ping = m.pingMs();
            String pv = ping >= 0 ? ping + " ms" : "-- ms";
            int pc = ping < 0 ? C_DIM : (ping < 60 ? C_GREEN_L : (ping < 120 ? C_YELLOW : C_RED));
            int pwid = this.textWidthBig(pv);
            big(c, pv, x + w - 10 - pwid, y + 8, pc, 1);
            String lab = "PING";
            c.drawTextWithShadow(AetherSettingsScreen.this.textRenderer, lab,
                    x + w - 10 - AetherSettingsScreen.this.textRenderer.getWidth(lab), y + 22, C_DIM);
        }

        private int textWidthBig(String t) {
            return AetherSettingsScreen.this.textRenderer.getWidth(t);
        }
    }

    /** Scheda Diagnostica: 3 misure principali, 4 contatori e il grafico dei frame time. */
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
            metric(c, x, y, cw, "FPS ATTUALI", has ? String.format(Locale.ROOT, "%.0f", fps) : "--", fpsColor, stab, -1);

            metric(c, x + cw + gap, y, cw, "FRAME TIME",
                    has ? String.format(Locale.ROOT, "%.1f", m.frameMsAvg()) : "--", C_ACCENT_L,
                    "ms  -  obiettivo < 16.6", -1);

            long max = m.maxMb();
            String ram = max >= 1024
                    ? String.format(Locale.ROOT, "%.1f", m.usedMb() / 1024.0)
                    : String.valueOf(m.usedMb());
            String unit = max >= 1024 ? String.format(Locale.ROOT, "GB di %.1f  -  GC ", max / 1024.0)
                    : "MB di " + max + "  -  GC ";
            double gc = m.gcPercent();
            String gcLabel = gc < 3 ? "bassa" : (gc < 10 ? "media" : "alta");
            metric(c, x + 2 * (cw + gap), y, cw, "RAM USATA", ram, C_PURPLE, unit + gcLabel,
                    max > 0 ? (float) m.usedMb() / max : 0f);

            int y2 = y + 52;
            int sw = (w - 3 * gap) / 4;
            stat(c, x, y2, sw, "Entità/s", m.culledEntities());
            stat(c, x + sw + gap, y2, sw, "Block entity/s", m.culledBlockEntities());
            stat(c, x + 2 * (sw + gap), y2, sw, "Particelle/s", m.culledParticles());
            int ping = m.pingMs();
            card(c, x + 3 * (sw + gap), y2, sw, 22, 0f, 0f);
            String pv = ping >= 0 ? ping + " ms" : "--";
            int pc = ping < 0 ? C_DIM : (ping < 60 ? C_GREEN_L : (ping < 120 ? C_YELLOW : C_RED));
            int pvw = boldWidth(pv);
            bold(c, pv, x + 3 * (sw + gap) + sw - 6 - pvw, y2 + 7, pc);
            text(c, fit("Ping", sw - 18 - pvw), x + 3 * (sw + gap) + 6, y2 + 7, C_MUTED);

            drawChart(c, m, y + 78, h - 78);
        }

        private void metric(DrawContext c, int cx, int cy, int cw, String label, String value, int color, String sub,
                            float bar) {
            card(c, cx, cy, cw, 48, 0f, 0f);
            text(c, fit(label, cw - 12), cx + 7, cy + 5, C_DIM);
            int vw = big(c, value, cx + 7, cy + 16, color, 2);
            if (bar >= 0f) {
                int bx1 = cx + 7;
                int bx2 = cx + cw - 7;
                rr(c, bx1, cy + 43, bx2, cy + 46, 0xFF2A3040);
                int fill = Math.max(2, Math.round((bx2 - bx1) * Math.min(1f, bar)));
                c.fill(bx1, cy + 43, bx1 + fill, cy + 46, bar > 0.85f ? C_RED : (bar > 0.65f ? C_YELLOW : C_PURPLE));
            }
            text(c, fit(sub, cw - 14 - Math.max(0, vw - cw / 2)), cx + 7, cy + 34, 0xFF7C8798);
        }

        private void stat(DrawContext c, int cx, int cy, int cw, String label, long value) {
            card(c, cx, cy, cw, 22, 0f, 0f);
            String v = String.valueOf(value);
            int vw = boldWidth(v);
            bold(c, v, cx + cw - 6 - vw, cy + 7, value > 0 ? C_GREEN_L : C_DIM);
            text(c, fit(label, cw - 18 - vw), cx + 6, cy + 7, C_MUTED);
        }

        private void drawChart(DrawContext c, PerformanceMonitor m, int cy, int ch) {
            card(c, x, cy, w, ch, 0f, 0f);
            text(c, "FRAME TIME (ms)", x + 8, cy + 6, C_TEXT);

            int ax1 = x + 8;
            int ax2 = x + w - 8;
            int ay1 = cy + 21;
            int ay2 = cy + ch - 7;
            int cwid = ax2 - ax1;
            int chei = ay2 - ay1;
            if (chei < 10) {
                return;
            }

            int n = m.copyHistory(hist);
            int colW = 2;
            int cols = Math.min(n, cwid / colW);
            double maxMs = 16.7;
            for (int i = n - cols; i < n; i++) {
                maxMs = Math.max(maxMs, hist[i]);
            }
            maxMs = Math.min(66.7, Math.ceil(maxMs / 8.0) * 8.0);

            for (int g = 1; g <= 3; g++) {
                int gy = ay2 - chei * g / 4;
                c.fill(ax1, gy, ax2, gy + 1, 0xFF1F2430);
            }
            // Riga dei 60 FPS (16,7 ms)
            int y60 = ay2 - (int) Math.min(chei, 16.7 / maxMs * chei);
            for (int dx = ax1; dx < ax2; dx += 6) {
                c.fill(dx, y60, Math.min(dx + 3, ax2), y60 + 1, alpha(C_GREEN, 110));
            }
            c.fill(ax1, ay2, ax2, ay2 + 1, C_BORDER);

            for (int i = 0; i < cols; i++) {
                double ms = hist[n - cols + i];
                int bh = (int) Math.max(1, Math.min(chei, ms / maxMs * chei));
                int bx = ax2 - (cols - i) * colW;
                int top = ay2 - bh;
                int lineCol = ms > 33.4 ? C_RED : (ms > 16.8 ? C_YELLOW : C_ACCENT_L);
                c.fillGradient(bx, top, bx + colW, ay2, alpha(lineCol, 120), alpha(lineCol, 8));
                c.fill(bx, top, bx + colW, Math.min(top + 2, ay2), lineCol);
            }

            text(c, String.format(Locale.ROOT, "%.0f ms", maxMs), ax1 + 2, ay1 + 1, C_DIM);
            text(c, "60 FPS", ax1 + 2, Math.max(ay1 + 10, y60 - 10), alpha(C_GREEN_L, 170));
        }
    }
}
