package com.aether.performance.diagnostics;

import com.aether.performance.AetherPerformance;
import com.aether.performance.rendering.RenderingConfig;
import com.aether.performance.settings.ModSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

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

    private int fpsColor = GREEN;
    private String lineTitle = "Aether Performance";
    private String lineFps = "";
    private String lineLow = "";
    private String lineHeap = "";
    private String lineGc = "";
    private String lineCull = "";
    private double heapRatio;

    /** FPS medi sugli ultimi frame (0 finché non ci sono dati). */
    public double averageFps() {
        return avgFps;
    }

    /** Media degli FPS sull'1% dei frame più lenti. */
    public double low1Fps() {
        return low1Fps;
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

        ModSettings s = AetherPerformance.settings();
        String mode = s.mode.name();
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
    }

    public void renderOverlay(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;

        boolean graph = RenderingConfig.overlayGraph;
        int x = 4;
        int y = 4;
        int w = 196;
        int h = graph ? 112 : 80;

        // Pannello e barra colorata a sinistra
        context.fill(x, y, x + w, y + h, 0xB00B0F14);
        context.fill(x, y, x + 2, y + h, ACCENT);

        int tx = x + 8;
        context.drawTextWithShadow(tr, lineTitle, tx, y + 4, ACCENT);
        context.drawTextWithShadow(tr, lineFps, tx, y + 16, fpsColor);
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
