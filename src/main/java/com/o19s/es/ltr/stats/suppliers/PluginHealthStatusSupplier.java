package com.o19s.es.ltr.stats.suppliers;

import com.o19s.es.ltr.utils.StoreUtils;

import java.util.List;
import java.util.function.Supplier;

/**
 * Supplier for an overall plugin health status.
 */
public class PluginHealthStatusSupplier implements Supplier<String> {
    private static final String STATUS_GREEN = "green";
    private static final String STATUS_YELLOW = "yellow";
    private static final String STATUS_RED = "red";

    private final StoreUtils storeUtils;

    public PluginHealthStatusSupplier(StoreUtils storeUtils) {
        this.storeUtils = storeUtils;
    }

    // currently it combines the store statuses to get the overall health
    // this may be enhanced to monitor other aspects of the plugin, such as,
    // if we implement the circuit breaker and if the breaker is open.
    @Override
    public String get() {
        return getAggregateStoresStatus();
    }

    private String getAggregateStoresStatus() {
        List<String> storeNames = storeUtils.getAllLtrStoreNames();
        return storeNames.stream()
                .map(storeUtils::getLtrStoreHealthStatus)
                .reduce(STATUS_GREEN, this::combineStatuses);
    }

    private String combineStatuses(String s1, String s2) {
        if (s2 == null || STATUS_RED.equals(s1) || STATUS_RED.equals(s2)) {
            return STATUS_RED;
        } else if (STATUS_YELLOW.equals(s1) || STATUS_YELLOW.equals(s2)) {
            return STATUS_YELLOW;
        } else {
            return STATUS_GREEN;
        }
    }
}
