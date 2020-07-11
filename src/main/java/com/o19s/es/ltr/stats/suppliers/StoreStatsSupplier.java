package com.o19s.es.ltr.stats.suppliers;

import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import com.o19s.es.ltr.stats.StatName;
import org.elasticsearch.action.admin.cluster.state.ClusterStateRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.health.ClusterIndexHealth;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A supplier which provides information on all feature stores. It provides basic
 * information such as the index health and count of feature sets, features and
 * models in the store.
 */
public class StoreStatsSupplier implements Supplier<Map<String, Map<String, Object>>> {
    private static final String FEATURE_SET_KEY = "featureset";
    private static final String FEATURE_SET_NAME_KEY = "name";
    private static final String FEATURES_KEY = "features";

    private final Client client;
    private final ClusterService clusterService;
    private final IndexNameExpressionResolver indexNameExpressionResolver;

    public StoreStatsSupplier(Client client, ClusterService clusterService) {
        this.client = client;
        this.clusterService = clusterService;
        this.indexNameExpressionResolver = new IndexNameExpressionResolver();
    }

    @Override
    public Map<String, Map<String, Object>> get() {
        String[] names = indexNameExpressionResolver.concreteIndexNames(clusterService.state(),
                new ClusterStateRequest().indices(
                        IndexFeatureStore.DEFAULT_STORE, IndexFeatureStore.STORE_PREFIX + "*"));

        return Arrays.stream(names)
                .filter(IndexFeatureStore::isIndexStore)
                .collect(Collectors
                        .collectingAndThen(
                                Collectors.toMap(Function.identity(), this::getStoreStat),
                                Collections::unmodifiableMap));
    }

    private Map<String, Object> getStoreStat(String storeName) {
        Map<String, Object> storeStat = new HashMap<>();
        storeStat.put(StatName.STORE_STATUS.getName(), getLtrStoreHealthStatus(storeName));

        //-----------------------------------
        //TODO OPTIMIZE
        Map<String, Integer> featureSets = getFeatureCountPerFeatureSet(storeName);
        storeStat.put(StatName.STORE_FEATURE_COUNT.getName(), featureSets.values().stream().reduce(0, Integer::sum));
        storeStat.put(StatName.STORE_FEATURE_SET_COUNT.getName(), featureSets.size());
        storeStat.put(StatName.STORE_MODEL_COUNT.getName(), getModelCount(storeName));
        //-------------------------------------
        return storeStat;
    }

    public String getLtrStoreHealthStatus(String storeName) {
        ClusterIndexHealth indexHealth =
                new ClusterIndexHealth(
                        clusterService.state().metadata().index(storeName),
                        clusterService.state().getRoutingTable().index(storeName));

        return indexHealth.getStatus().name().toLowerCase(Locale.getDefault());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Integer> getFeatureCountPerFeatureSet(String storeName) {
        Map<String, Integer> featureSets = new HashMap<>();
        SearchResponse response = searchStore(storeName, StoredFeatureSet.TYPE);
        for (SearchHit hit : response.getHits()) {
            Map<String, Object> sourceMap = hit.getSourceAsMap();
            if (sourceMap != null && sourceMap.containsKey(FEATURE_SET_KEY)) {
                Map<String, Object> fs = (Map<String, Object>) sourceMap.get(FEATURE_SET_KEY);
                if (fs != null && fs.containsKey(FEATURES_KEY)) {
                    List<String> features = (List<String>) fs.get(FEATURES_KEY);
                    featureSets.put((String) fs.get(FEATURE_SET_NAME_KEY), features.size());
                }
            }
        }
        return featureSets;
    }

    public long getModelCount(String storeName) {
        return searchStore(storeName, StoredLtrModel.TYPE).getHits().getTotalHits().value;
    }

    private SearchResponse searchStore(String storeName, String docType) {
        return client.prepareSearch(storeName)
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setQuery(QueryBuilders.termQuery("type", docType))
                .get();
    }

}
