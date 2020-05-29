package com.o19s.es.ltr.stats.suppliers;

import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * A supplier which provides an increment-only counter.
 */
public class CounterSupplier implements Supplier<Long> {
    private final LongAdder counter;

    public CounterSupplier() {
        this.counter = new LongAdder();
    }

    @Override
    public Long get() {
        return counter.longValue();
    }

    public void increment() {
        counter.increment();
    }
}
