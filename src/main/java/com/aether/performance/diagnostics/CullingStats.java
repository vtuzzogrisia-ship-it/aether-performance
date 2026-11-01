package com.aether.performance.diagnostics;

import java.util.concurrent.atomic.LongAdder;

/** Contatori di quante cose sono state tagliate. Li legge l'overlay ogni mezzo secondo. */
public final class CullingStats {
    public static final LongAdder ENTITIES = new LongAdder();
    public static final LongAdder BLOCK_ENTITIES = new LongAdder();
    public static final LongAdder PARTICLES = new LongAdder();

    private CullingStats() {
    }
}
