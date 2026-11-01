package com.aether.performance.diagnostics;

import com.aether.performance.AetherPerformance;
import com.aether.performance.rendering.RenderingConfig;
import com.aether.performance.settings.ModSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.Arrays;
import java.util.List;

/**
 * Overlay diagnostico. Misura i frame time in un buffer circolare e ricalcola
 * le statistiche ogni mezzo secondo, così non alloca nulla a ogni frame.
 */
public class PerformanceMonitor {
    private static final int WINDOW = 300;
    private static final int GRAPH_BARS = 60;
    private static final int GRAPH_HEIGHT = 26;
    private static final long REFRESH_NANOS = 500_000_000L;

    private static final int GREEN = 0xFF55FF7F;
    private static final int YELLOW = 0xFFFFD84D;
    private static final int RED = 0xFFFF5555;
    private static final int GREY = 0xFFAAB7C4;
    private static final int ACCENT = 0xFF3DDC97;

    private static final MemoryMXBean MEMORY = ManagementFactory.getMemoryMXBean();
    private static final List<GarbageCollectorMXBean> GC_BEANS = ManagementFactory.getGarbageCollectorMXBeans();

    private final double[] frameTimes = new double[WINDOW];
    private final double[] scratch = new double[WINDOW];
    private int index;
    private int count;

    private long lastFrame = System.nanoTime();
    private long lastRefresh = lastFrame;
    private long lastGcTotal = totalGcMillis();

    private volatile double avgFps;
    private volatile double low1Fps;
    private volatile double frameMsAvg;
    private volatile long usedMb;
    private volatile long maxMb;
    private volatile double gcPct;
    private volatile long culledEntities;
    private volatile long culledBlockEntities;
    private volatile long culledParticles;
    private volatile int pingMs = -1;

    private int fpsColor = GREEN;
    private String lineTitle = "Aether Performance";
    private String lineFps = "";
    private String lineLow = "";
    private String lineHeap = "";
    private String lineGc = "";
    private String lineCull = "";
    private String linePing = "";
    private int pingColor = GREY;
    private double heapRatio;

    /** FPS medi sugli ultimi frame (0 finché non ci sono dati). */
    public double averageFps() {
        return avgFps;
    }

    /** Media degli FPS sull'1% dei frame più lenti. */
    public double low1Fps() {
        return low1Fps;
    }

    public double frameMsAvg() {
        return frameMsAvg;
    }

    public long usedMb() {
        return usedMb;
    }

    public long maxMb() {
        return maxMb;
    }

    /** Percentuale di tempo passata nel garbage collector nell'ultimo intervallo. */
    public double gcPercent() {
        return gcPct;
    }

    public long culledEntities() {
        return culledEntities;
    }

    public long culledBlockEntities() {
        return culledBlockEntities;
    }

    public long culledParticles() {
        return culledParticles;
    }

    /** Ping verso il server in ms, oppure -1 in giocatore singolo / se non disponibile. */
    public int pingMs() {
        return pingMs;
    }

    /** Evita un frame time gigante dopo che la misura è rimasta ferma (es. schermata chiusa). */
    public void resync() {
        lastFrame = System.nanoTime();
    }

    /** Copia gli ultimi frame time (ms) in ordine cronologico; restituisce quanti ne ha scritti. */
    public int copyHistory(double[] out) {
        int n = Math.min(count, out.length);
        for (int i = 0; i < n; i++) {
            out[i] = frameTimes[(index - n + i + WINDOW * 2) % WINDOW];
        }
        return n;
    }

    /** Da chiamare una volta per frame. */
    public void update() {
        long now = System.nanoTime();
        double ms = (now - lastFrame) / 1_000_000.0;
        lastFrame = now;

        if (ms > 0 && ms < 10_000) {
            frameTimes[index] = ms;
            index = (index + 1) % WINDOW;
            count = Math.min(count + 1, WINDOW);
        }

        if (now - lastRefresh >= REFRESH_NANOS) {
            long elapsed = now - lastRefresh;
            lastRefresh = now;
            refresh(elapsed);
        }
    }

    private void refresh(long elapsedNanos) {
        if (count == 0) {
            return;
        }
        System.arraycopy(frameTimes, 0, scratch, 0, count);
        Arrays.sort(scratch, 0, count);

        double sum = 0;
        for (int i = 0; i < count; i++) {
            sum += scratch[i];
        }
        double avgMs = sum / count;
        double lastMs = frameTimes[(index - 1 + WINDOW) % WINDOW];

        // 1% low: media dei frame più lenti (1% del campione, almeno 1 frame)
        int worstCount = Math.max(1, count / 100);
        double worstSum = 0;
        for (int i = count - worstCount; i < count; i++) {
            worstSum += scratch[i];
        }
        avgFps = 1000.0 / avgMs;
        low1Fps = 1000.0 / (worstSum / worstCount);

        double seconds = elapsedNanos / 1_000_000_000.0;
        long gcTotal = totalGcMillis();
        long gcDelta = Math.max(0, gcTotal - lastGcTotal);
        lastGcTotal = gcTotal;
        double gcPercent = gcDelta / (seconds * 1000.0) * 100.0;

        long used = MEMORY.getHeapMemoryUsage().getUsed() >> 20;
        long max = MEMORY.getHeapMemoryUsage().getMax() >> 20;
        heapRatio = max > 0 ? Math.min(1.0, (double) used / max) : 0;

        long culledEntities = (long) (CullingStats.ENTITIES.sumThenReset() / seconds);
        long culledBlockEntities = (long) (CullingStats.BLOCK_ENTITIES.sumThenReset() / seconds);
        long culledParticles = (long) (CullingStats.PARTICLES.sumThenReset() / seconds);

        this.frameMsAvg = avgMs;
        this.usedMb = used;
        this.maxMb = max;
        this.gcPct = gcPercent;
        this.culledEntities = culledEntities;
        this.culledBlockEntities = culledBlockEntities;
        this.culledParticles = culledParticles;

        this.pingMs = readPing();

        ModSettings s = AetherPerformance.settings();
        String mode = s.effectiveMode().name();
        if (RenderingConfig.auto) {
            mode += String.format(" x%.2f", RenderingConfig.autoScale);
        }
        fpsColor = colorForFps(avgFps);
        lineTitle = "Aether Performance - " + mode;
        lineFps = String.format("%.0f FPS", avgFps);
        lineLow = String.format("1%% low %.0f   %.1f ms (ultimo %.1f)", low1Fps, avgMs, lastMs);
        lineHeap = String.format("RAM %d / %d MB", used, max);
        lineGc = String.format("GC %.1f%%", gcPercent);
        lineCull = String.format("Tagliati/s  E %d   B %d   P %d", culledEntities, culledBlockEntities, culledParticles);
        if (pingMs >= 0) {
            linePing = "Ping " + pingMs + " ms";
            pingColor = pingMs < 60 ? GREEN : (pingMs < 120 ? YELLOW : RED);
        } else {
            linePing = "";
        }
    }

    /** Latenza del giocatore locale dalla lista giocatori del server (-1 se non c'è). */
    private static int readPing() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null || mc.isInSingleplayer()) {
            return -1;
        }
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry == null ? -1 : Math.max(0, entry.getLatency());
    }

    public void renderOverlay(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;

        boolean graph = RenderingConfig.overlayGraph;
        boolean ping = RenderingConfig.showPing && !linePing.isEmpty();
        int x = 4;
        int y = 4;
        int w = 196;
        int h = graph ? 102 : 72;

        // Ombra, pannello sfumato e barra colorata a sinistra
        context.fill(x + 1, y + 2, x + w + 1, y + h + 2, 0x40000000);
        context.fillGradient(x, y, x + w, y + h, 0xC0111722, 0xC00A0E14);
        context.fillGradient(x, y, x + 2, y + h, ACCENT, 0xFF3B82F6);
        context.fill(x + 2, y, x + w, y + 1, 0x30FFFFFF);

        int tx = x + 8;
        context.drawTextWithShadow(tr, lineTitle, tx, y + 4, ACCENT);
        context.drawTextWithShadow(tr, lineFps, tx, y + 16, fpsColor);
        if (ping) {
            int pw = tr.getWidth(linePing);
            context.drawTextWithShadow(tr, linePing, x + w - 6 - pw, y + 16, pingColor);
        }
        context.drawTextWithShadow(tr, lineLow, tx, y + 27, GREY);
        context.drawTextWithShadow(tr, lineHeap, tx, y + 38, GREY);
        context.drawTextWithShadow(tr, lineGc, tx + 110, y + 38, GREY);

        // Barra della RAM
        int barW = w - 16;
        context.fill(tx, y + 49, tx + barW, y + 53, 0xFF2A3440);
        int used = (int) (barW * heapRatio);
        context.fill(tx, y + 49, tx + used, y + 53, heapRatio > 0.85 ? RED : (heapRatio > 0.65 ? YELLOW : GREEN));

        context.drawTextWithShadow(tr, lineCull, tx, y + 58, GREY);

        if (graph) {
            drawGraph(context, tx, y + 70, y + 70 + GRAPH_HEIGHT);
        }
    }

    /** Grafico a barre degli ultimi frame: verde sotto i 60 FPS, giallo fino a 30, rosso oltre. */
    private void drawGraph(DrawContext context, int x, int top, int baseY) {
        int barW = 3;
        context.fill(x, top, x + GRAPH_BARS * barW, baseY, 0x60000000);
        for (int i = 0; i < GRAPH_BARS; i++) {
            int offset = GRAPH_BARS - i;
            if (offset > count) {
                continue;
            }
            double ms = frameTimes[(index - offset + WINDOW * 2) % WINDOW];
            int barH = (int) Math.min(GRAPH_HEIGHT, Math.max(1, ms / 50.0 * GRAPH_HEIGHT));
            int color = ms <= 16.8 ? GREEN : (ms <= 33.4 ? YELLOW : RED);
            int bx = x + i * barW;
            context.fill(bx, baseY - barH, bx + barW - 1, baseY, color);
        }
    }

    private static int colorForFps(double fps) {
        return fps >= 55 ? GREEN : (fps >= 30 ? YELLOW : RED);
    }

    private static long totalGcMillis() {
        long total = 0;
        for (GarbageCollectorMXBean bean : GC_BEANS) {
            long t = bean.getCollectionTime();
            if (t > 0) {
                total += t;
            }
        }
        return total;
    }
}
