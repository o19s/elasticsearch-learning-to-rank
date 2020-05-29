package com.o19s.es.ltr.stats.suppliers;

import org.elasticsearch.test.ESTestCase;

public class CounterSupplierTests extends ESTestCase {
    public void testGetAndIncrement() {
        CounterSupplier counterSupplier = new CounterSupplier();
        assertEquals((Long) 0L, counterSupplier.get());
        counterSupplier.increment();
        assertEquals((Long) 1L, counterSupplier.get());
    }
}
