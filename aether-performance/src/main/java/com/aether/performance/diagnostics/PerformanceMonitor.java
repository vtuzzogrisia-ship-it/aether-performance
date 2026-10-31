package com.aether.performance.diagnostics;

import net.minecraft.client.MinecraftClient;
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
    private static final long REFRESH_NANOS = 500_000_000L;

    private static final MemoryMXBean MEMORY = ManagementFactory.getMemoryMXBean();
    private static final List<GarbageCollectorMXBean> GC_BEANS = ManagementFactory.getGarbageCollectorMXBeans();

    private final double[] frameTimes = new double[WINDOW];
    private final double[] scratch = new double[WINDOW];
    private int index;
    private int count;

    private long lastFrame = System.nanoTime();
    private long lastRefresh = lastFrame;
    private long lastGcTotal = totalGcMillis();

    private String line1 = "Aether Perf ...";
    private String line2 = "";

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
            refreshText(elapsed);
        }
    }

    private void refreshText(long elapsedNanos) {
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
        double low1Fps = 1000.0 / (worstSum / worstCount);

        long gcTotal = totalGcMillis();
        long gcDelta = Math.max(0, gcTotal - lastGcTotal);
        lastGcTotal = gcTotal;
        double gcPercent = gcDelta / (elapsedNanos / 1_000_000.0) * 100.0;

        long used = MEMORY.getHeapMemoryUsage().getUsed() >> 20;
        long max = MEMORY.getHeapMemoryUsage().getMax() >> 20;

        line1 = String.format("Aether Perf | FPS: %.0f | 1%% low: %.0f | FT: %.2f ms (ultimo %.2f)",
                1000.0 / avgMs, low1Fps, avgMs, lastMs);
        line2 = String.format("Heap: %d/%d MB | GC: %.1f%% (%d ms totali)", used, max, gcPercent, gcTotal);
    }

    public void renderOverlay(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        // Alpha obbligatorio (0xFF......) nelle versioni recenti, altrimenti il testo è invisibile
        context.drawTextWithShadow(client.textRenderer, line1, 5, 5, 0xFFFFFFFF);
        context.drawTextWithShadow(client.textRenderer, line2, 5, 16, 0xFFAAAAAA);
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
