package com.o19s.es.ltr.action;

import com.o19s.es.ltr.action.LTRStatsAction.LTRStatsNodesResponse;
import com.o19s.es.ltr.action.LTRStatsAction.LTRStatsRequestBuilder;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import com.o19s.es.ltr.stats.StatName;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static com.o19s.es.ltr.LtrTestUtils.randomFeature;
import static com.o19s.es.ltr.LtrTestUtils.randomFeatureSet;
import static com.o19s.es.ltr.LtrTestUtils.randomLinearModel;
import static com.o19s.es.ltr.feature.store.index.IndexFeatureStore.indexName;

public class LTRStatsActionIT extends BaseIntegrationTest {
    private static final String DEFAULT_STORE_NAME =
            IndexFeatureStore.storeName(IndexFeatureStore.DEFAULT_STORE);

    @SuppressWarnings("unchecked")
    public void testStatsNoStore() throws Exception {
        deleteDefaultStore();
        LTRStatsNodesResponse response = executeRequest();
        assertFalse(response.hasFailures());

        Map<String, Object> clusterStats = response.getClusterStats();
        assertEquals("green", clusterStats.get(StatName.PLUGIN_STATUS.getName()));

        Map<String, Object> stores = (Map<String, Object>) clusterStats.get(StatName.STORES.getName());
        assertTrue(stores.isEmpty());
    }

    @SuppressWarnings("unchecked")
    public void testAllStatsDefaultEmptyStore() throws ExecutionException, InterruptedException {
        LTRStatsNodesResponse response = executeRequest();
        assertFalse(response.hasFailures());

        Map<String, Object> clusterStats = response.getClusterStats();
        assertEquals("green", clusterStats.get(StatName.PLUGIN_STATUS.getName()));

        Map<String, Object> stores = (Map<String, Object>) clusterStats.get(StatName.STORES.getName());
        assertEquals(1, stores.size());
        assertTrue(stores.containsKey(DEFAULT_STORE_NAME));
        Map<String, Object> storeStat = (Map<String, Object>) stores.get(DEFAULT_STORE_NAME);
        assertEquals(0, storeStat.get(StatName.STORE_FEATURE_COUNT.getName()));
        assertEquals(0, storeStat.get(StatName.STORE_FEATURE_SET_COUNT.getName()));
        assertEquals(0, storeStat.get(StatName.STORE_MODEL_COUNT.getName()));

        Map<String, Object> nodeStats = response.getNodes().get(0).getStatsMap();
        assertFalse(nodeStats.isEmpty());
        assertTrue(nodeStats.containsKey(StatName.CACHE.getName()));

        Map<String, Object> cacheStats = (Map<String, Object>) nodeStats.get(StatName.CACHE.getName());
        assertEquals(3, cacheStats.size());
        assertTrue(cacheStats.containsKey(StatName.CACHE_FEATURE.getName()));
        assertTrue(cacheStats.containsKey(StatName.CACHE_FEATURE_SET.getName()));
        assertTrue(cacheStats.containsKey(StatName.CACHE_MODEL.getName()));

        Map<String, Object> featureCacheStats = (Map<String, Object>) cacheStats.get(StatName.CACHE_FEATURE.getName());
        assertEquals(5, featureCacheStats.size());
        assertTrue(featureCacheStats.containsKey(StatName.CACHE_HIT_COUNT.getName()));
        assertTrue(featureCacheStats.containsKey(StatName.CACHE_MISS_COUNT.getName()));
        assertTrue(featureCacheStats.containsKey(StatName.CACHE_EVICTION_COUNT.getName()));
        assertTrue(featureCacheStats.containsKey(StatName.CACHE_ENTRY_COUNT.getName()));
        assertTrue(featureCacheStats.containsKey(StatName.CACHE_MEMORY_USAGE_IN_BYTES.getName()));
    }


    @SuppressWarnings("unchecked")
    public void testMultipleFeatureStores() throws Exception {
        createStore(indexName("test1"));

        LTRStatsNodesResponse response = executeRequest();
        assertFalse(response.hasFailures());

        Map<String, Object> clusterStats = response.getClusterStats();
        assertEquals("green", clusterStats.get(StatName.PLUGIN_STATUS.getName()));

        Map<String, Object> stores = (Map<String, Object>) clusterStats.get(StatName.STORES.getName());
        assertEquals(2, stores.size());
        assertTrue(stores.containsKey(DEFAULT_STORE_NAME));
        assertTrue(stores.containsKey(IndexFeatureStore.storeName(indexName("test1"))));
    }

    @SuppressWarnings("unchecked")
    public void testStoreStats() throws ExecutionException, InterruptedException, IOException {
        StoredFeatureSet featureSet = randomFeatureSet("featureset1");
        addElement(featureSet);
        addElement(randomFeature("feature1"));
        addElement(randomLinearModel("model1", featureSet));

        LTRStatsNodesResponse response = executeRequest();
        assertFalse(response.hasFailures());

        Map<String, Object> clusterStats = response.getClusterStats();
        Map<String, Object> stores = (Map<String, Object>) clusterStats.get(StatName.STORES.getName());

        Map<String, Object> storeStat = (Map<String, Object>) stores.get(DEFAULT_STORE_NAME);
        assertEquals(1L, storeStat.get(StatName.STORE_FEATURE_SET_COUNT.getName()));
        assertEquals(1L, storeStat.get(StatName.STORE_FEATURE_COUNT.getName()));
        assertEquals(1L, storeStat.get(StatName.STORE_MODEL_COUNT.getName()));
    }

    private LTRStatsNodesResponse executeRequest() throws ExecutionException, InterruptedException {
        LTRStatsRequestBuilder builder = new LTRStatsRequestBuilder(client());
        Set<String> statsToBeRetrieved = new HashSet<>(Arrays.asList(
                StatName.PLUGIN_STATUS.getName(), StatName.CACHE.getName(), StatName.STORES.getName()));
        builder.request().setStatsToBeRetrieved(statsToBeRetrieved);
        return builder.execute().get();
    }
}
