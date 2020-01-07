package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.ranker.LogLtrRanker;

import java.util.Map;
import java.util.function.Supplier;


public class ExtraLoggingSupplier implements Supplier<Map<String,Object>> {
    protected LogLtrRanker.LogConsumer consumer;

    public void setConsumer(LogLtrRanker.LogConsumer consumer) {
        this.consumer = consumer;
    }

    /**
     * Return a Map to add additional information to be returned when logging feature values.
     *
     * This Map will only be non-null during the LoggingFetchSubPhase.
     */
    @Override
    public Map<String, Object> get() {
        if (consumer != null) {
            return consumer.getExtraLoggingMap();
        }
        return null;
    }
}