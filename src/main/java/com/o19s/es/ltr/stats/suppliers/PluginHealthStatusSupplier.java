package com.o19s.es.ltr.stats.suppliers;

import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import org.elasticsearch.action.admin.cluster.state.ClusterStateRequest;
import org.elasticsearch.cluster.health.ClusterIndexHealth;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Supplier for an overall plugin health status.
 */
public class PluginHealthStatusSupplier implements Supplier<String> {
    private static final String STATUS_GREEN = "green";
    private static final String STATUS_YELLOW = "yellow";
    private static final String STATUS_RED = "red";

    private final ClusterService clusterService;
    private final IndexNameExpressionResolver indexNameExpressionResolver;

    public PluginHealthStatusSupplier(ClusterService clusterService) {
        this.clusterService = clusterService;
        this.indexNameExpressionResolver = new IndexNameExpressionResolver();
    }

    // currently it combines the store statuses to get the overall health
    // this may be enhanced to monitor other aspects of the plugin, such as,
    // if we implement the circuit breaker and if the breaker is open.
    @Override
    public String get() {
        return getAggregateStoresStatus();
    }

    private String getAggregateStoresStatus() {
        String[] names = indexNameExpressionResolver.concreteIndexNames(clusterService.state(),
                new ClusterStateRequest().indices(
                        IndexFeatureStore.DEFAULT_STORE, IndexFeatureStore.STORE_PREFIX + "*"));
        return Arrays.stream(names)
                .filter(IndexFeatureStore::isIndexStore)
                .map(this::getLtrStoreHealthStatus)
                .reduce(STATUS_GREEN, this::combineStatuses);
    }

    private String combineStatuses(String s1, String s2) {
        if (STATUS_RED.equals(s1) || STATUS_RED.equals(s2)) {
            return STATUS_RED;
        } else if (STATUS_YELLOW.equals(s1) || STATUS_YELLOW.equals(s2)) {
            return STATUS_YELLOW;
        } else {
            return STATUS_GREEN;
        }
    }

    public String getLtrStoreHealthStatus(String storeName) {
        ClusterIndexHealth indexHealth = new ClusterIndexHealth(
                clusterService.state().metadata().index(storeName),
                clusterService.state().getRoutingTable().index(storeName)
        );

        return indexHealth.getStatus().name().toLowerCase(Locale.ROOT);
    }
}
