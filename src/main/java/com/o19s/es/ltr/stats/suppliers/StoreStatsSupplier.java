package com.o19s.es.ltr.stats.suppliers;

import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.admin.cluster.state.ClusterStateRequest;
import org.elasticsearch.action.search.MultiSearchRequestBuilder;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.health.ClusterIndexHealth;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A supplier which provides information on all feature stores. It provides basic
 * information such as the index health and count of feature sets, features and
 * models in the store.
 */
public class StoreStatsSupplier implements Supplier<Map<String, Map<String, Object>>> {
    private static final Logger LOG = LogManager.getLogger(StoreStatsSupplier.class);
    private static final String AGG_FIELD = "type";
    private final Client client;
    private final ClusterService clusterService;
    private final IndexNameExpressionResolver indexNameExpressionResolver;

    public enum Stat {
        STORE_STATUS("status"),
        STORE_FEATURE_COUNT("feature_count"),
        STORE_FEATURE_SET_COUNT("featureset_count"),
        STORE_MODEL_COUNT("model_count");

        private final String name;

        Stat(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public StoreStatsSupplier(Client client, ClusterService clusterService, IndexNameExpressionResolver indexNameExpressionResolver) {
        this.client = client;
        this.clusterService = clusterService;
        this.indexNameExpressionResolver = indexNameExpressionResolver;
    }

    @Override
    public Map<String, Map<String, Object>> get() {
        String[] names = indexNameExpressionResolver.concreteIndexNames(clusterService.state(),
                new ClusterStateRequest().indices(
                        IndexFeatureStore.DEFAULT_STORE, IndexFeatureStore.STORE_PREFIX + "*"));
        final MultiSearchRequestBuilder requestBuilder = client.prepareMultiSearch();
        List<String> indices = new ArrayList<>();
        Stream.of(names)
                .filter(IndexFeatureStore::isIndexStore)
                .map(s -> clusterService.state().metadata().index(s))
                .filter(Objects::nonNull)
                .map(IndexMetadata::getIndex)
                .map(Index::getName)
                .forEach(idx -> {
                    indices.add(idx);
                    requestBuilder.add(countSearchRequest(idx));
                });
        return createStoreStatsResponse(requestBuilder, indices);
    }

    private Map<String, Map<String, Object>> createStoreStatsResponse(MultiSearchRequestBuilder requestBuilder,
                                                                      List<String> indices) {
        try {
            MultiSearchResponse msr = requestBuilder.execute().get();
            assert indices.size() == msr.getResponses().length;
            Map<String, Map<String, Object>> stats = new HashMap<>(indices.size());

            Iterator<String> indicesItr = indices.iterator();
            Iterator<MultiSearchResponse.Item> responseItr = msr.iterator();
            while (indicesItr.hasNext() && responseItr.hasNext()) {
                MultiSearchResponse.Item it = responseItr.next();
                String index = indicesItr.next();
                Map<String, Object> storeStat = initStoreStat(index);
                stats.put(IndexFeatureStore.storeName(index), storeStat);
                if (!it.isFailure()) {
                    Terms aggs = it.getResponse()
                            .getAggregations()
                            .get(AGG_FIELD);
                    aggs.getBuckets()
                            .stream()
                            .filter(Objects::nonNull)
                            .forEach(bucket -> updateCount(bucket, storeStat));
                }
            }
            return stats;
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error retrieving store stats", e);
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> initStoreStat(String index) {
        Map<String, Object> storeStat = new HashMap<>();
        storeStat.put(Stat.STORE_STATUS.getName(), getLtrStoreHealthStatus(index));
        storeStat.put(Stat.STORE_FEATURE_COUNT.getName(), 0L);
        storeStat.put(Stat.STORE_FEATURE_SET_COUNT.getName(), 0L);
        storeStat.put(Stat.STORE_MODEL_COUNT.getName(), 0L);
        return storeStat;
    }

    private void updateCount(Terms.Bucket bucket, Map<String, Object> storeStat) {
        storeStat.computeIfPresent(
                typeToStatName(bucket.getKeyAsString()),
                (k, v) -> bucket.getDocCount() + (long) v);
    }

    private String typeToStatName(String type) {
        return type + "_count";
    }

    public String getLtrStoreHealthStatus(String storeName) {
        ClusterIndexHealth indexHealth =
                new ClusterIndexHealth(
                        clusterService.state().metadata().index(storeName),
                        clusterService.state().getRoutingTable().index(storeName));

        return indexHealth.getStatus().name().toLowerCase(Locale.ROOT);
    }

    private SearchRequestBuilder countSearchRequest(String index) {
        return client.prepareSearch(index)
                .setQuery(QueryBuilders.matchAllQuery())
                .setSize(0)
                .addAggregation(
                        AggregationBuilders.terms(AGG_FIELD).field(AGG_FIELD).size(100));
    }
}
