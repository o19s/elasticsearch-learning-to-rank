package com.o19s.es.ltr.stats;

import java.util.function.Supplier;

/**
 * A container for a stat provided by the plugin. Each instance is associated with
 * an underlying supplier. The stat instance also stores a flag to indicate whether
 * this is a cluster level or a node level stat.
 */
public class LTRStat {
    private final boolean clusterLevel;
    private final Supplier<?> supplier;

    public LTRStat(boolean clusterLevel, Supplier<?> supplier) {
        this.clusterLevel = clusterLevel;
        this.supplier = supplier;
    }

    public boolean isClusterLevel() {
        return clusterLevel;
    }

    public Object getStatValue() {
        return supplier.get();
    }
}
