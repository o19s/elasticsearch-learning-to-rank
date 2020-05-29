package com.o19s.es.ltr.utils;

import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import org.elasticsearch.action.admin.cluster.state.ClusterStateRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.health.ClusterIndexHealth;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StoreUtils {
    private static final String FEATURE_SET_KEY = "featureset";
    private static final String FEATURE_SET_NAME_KEY = "name";
    private static final String FEATURES_KEY = "features";

    private final Client client;
    private final ClusterService clusterService;
    private final IndexNameExpressionResolver indexNameExpressionResolver;

    public StoreUtils(Client client, ClusterService clusterService) {
        this.client = client;
        this.clusterService = clusterService;
        this.indexNameExpressionResolver = new IndexNameExpressionResolver();
    }

    public boolean checkLtrStoreExists(String storeName) {
        return clusterService.state().getRoutingTable().hasIndex(storeName);
    }

    public List<String> getAllLtrStoreNames() {
        String[] names = indexNameExpressionResolver.concreteIndexNames(clusterService.state(),
                new ClusterStateRequest().indices(
                        IndexFeatureStore.DEFAULT_STORE, IndexFeatureStore.STORE_PREFIX + "*"));
        return Arrays.asList(names);
    }

    public String getLtrStoreHealthStatus(String storeName) {
        if (!checkLtrStoreExists(storeName)) {
            throw new IndexNotFoundException(storeName);
        }
        ClusterIndexHealth indexHealth = new ClusterIndexHealth(
                clusterService.state().metaData().index(storeName),
                clusterService.state().getRoutingTable().index(storeName)
        );

        return indexHealth.getStatus().name().toLowerCase(Locale.getDefault());
    }

    /**
     * Query the feature sets in this store. The returned map contains the name
     * of each feature set as the key and the number of features in it as the value.
     *
     * @param storeName the name of the index for the store.
     * @return a map of feature set names and the feature count per feature set.
     */
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
