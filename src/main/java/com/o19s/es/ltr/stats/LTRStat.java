package com.o19s.es.ltr.stats;

import com.o19s.es.ltr.stats.suppliers.CounterSupplier;

import java.util.function.Supplier;

/**
 * A container for a stat provided by the plugin. Each instance is associated with
 * an underlying supplier. The supplier can be a counter in which case it can be
 * incremented. The stat instance also stores a flag to indicate whether this is
 * a cluster level or node level stat.
 *
 * @param <T> the type of the value returned by the stat.
 */
public class LTRStat<T> {
    private final boolean clusterLevel;
    private final Supplier<T> supplier;

    public LTRStat(boolean clusterLevel, Supplier<T> supplier) {
        this.clusterLevel = clusterLevel;
        this.supplier = supplier;
    }

    public boolean isClusterLevel() {
        return clusterLevel;
    }

    public T getValue() {
        return supplier.get();
    }

    public void increment() {
        if (!(supplier instanceof CounterSupplier)) {
            throw new UnsupportedOperationException(
                    "cannot increment the supplier: " + supplier.getClass().getName());
        }
        ((CounterSupplier) supplier).increment();
    }
}
