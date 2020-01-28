package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.ranker.LogLtrRanker;
import org.apache.lucene.util.LuceneTestCase;

import java.util.HashMap;
import java.util.Map;

public class ExtraLoggingSupplierTests extends LuceneTestCase {
    public void testGetWithConsumerNotSet() {
        ExtraLoggingSupplier supplier = new ExtraLoggingSupplier();
        assertNull(supplier.get());
    }

    public void testGetWillNullConsumerSet() {
        ExtraLoggingSupplier supplier = new ExtraLoggingSupplier();
        supplier.setSupplier(null);
        assertNull(supplier.get());
    }

    public void testGetWithSuppliedNull() {
        ExtraLoggingSupplier supplier = new ExtraLoggingSupplier();
        supplier.setSupplier(() -> null);
        assertNull(supplier.get());
    }

    public void testGetWithSuppliedMap() {
        Map<String,Object> extraLoggingMap = new HashMap<>();

        LogLtrRanker.LogConsumer consumer = new LogLtrRanker.LogConsumer() {
            @Override
            public void accept(int featureOrdinal, float score) {}

            @Override
            public Map<String,Object> getExtraLoggingMap() {
                return extraLoggingMap;
            }
        };

        ExtraLoggingSupplier supplier = new ExtraLoggingSupplier();
        supplier.setSupplier(consumer::getExtraLoggingMap);
        assertTrue(supplier.get() == extraLoggingMap);
    }
}
