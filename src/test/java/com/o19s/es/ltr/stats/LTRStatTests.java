package com.o19s.es.ltr.stats;

import com.o19s.es.ltr.stats.suppliers.CounterSupplier;
import org.elasticsearch.test.ESTestCase;

public class LTRStatTests extends ESTestCase {
    public void testIsClusterLevel() {
        LTRStat<String> stat1 = new LTRStat<>(true, () -> "test");
        assertTrue(stat1.isClusterLevel());

        LTRStat<String> stat2 = new LTRStat<>(false, () -> "test");
        assertFalse(stat2.isClusterLevel());
    }

    public void testGetValue() {
        LTRStat<Long> stat1 = new LTRStat<>(false, new CounterSupplier());
        assertEquals(0L, stat1.getValue().longValue());

        LTRStat<String> stat2 = new LTRStat<>(false, () -> "test");
        assertEquals("test", stat2.getValue());
    }

    public void testIncrementCounterSupplier() {
        LTRStat<Long> incrementStat = new LTRStat<>(false, new CounterSupplier());

        for (long i = 0L; i < 100; i++) {
            assertEquals(i, incrementStat.getValue().longValue());
            incrementStat.increment();
        }
    }
}
