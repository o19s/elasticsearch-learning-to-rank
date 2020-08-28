package com.o19s.es.ltr.action;

import com.o19s.es.ltr.LtrTestUtils;
import com.o19s.es.ltr.action.LTRStatsAction.LTRStatsNodesResponse;
import com.o19s.es.ltr.action.LTRStatsAction.LTRStatsRequestBuilder;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import com.o19s.es.ltr.stats.StatName;
import com.o19s.es.ltr.stats.suppliers.CacheStatsOnNodeSupplier;
import com.o19s.es.ltr.stats.suppliers.StoreStatsSupplier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static com.o19s.es.ltr.feature.store.index.IndexFeatureStore.DEFAULT_STORE;
import static com.o19s.es.ltr.feature.store.index.IndexFeatureStore.indexName;

public class LTRStatsActionIT extends BaseIntegrationTest {
    private static final String DEFAULT_STORE_NAME = IndexFeatureStore.storeName(DEFAULT_STORE);

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
        assertEquals(0L, storeStat.get(StoreStatsSupplier.Stat.STORE_FEATURE_COUNT.getName()));
        assertEquals(0L, storeStat.get(StoreStatsSupplier.Stat.STORE_FEATURE_SET_COUNT.getName()));
        assertEquals(0L, storeStat.get(StoreStatsSupplier.Stat.STORE_MODEL_COUNT.getName()));

        Map<String, Object> nodeStats = response.getNodes().get(0).getStatsMap();
        assertFalse(nodeStats.isEmpty());
        assertTrue(nodeStats.containsKey(StatName.CACHE.getName()));

        Map<String, Object> cacheStats = (Map<String, Object>) nodeStats.get(StatName.CACHE.getName());
        assertEquals(3, cacheStats.size());
        assertTrue(cacheStats.containsKey(CacheStatsOnNodeSupplier.Stat.CACHE_FEATURE.getName()));
        assertTrue(cacheStats.containsKey(CacheStatsOnNodeSupplier.Stat.CACHE_FEATURE_SET.getName()));
        assertTrue(cacheStats.containsKey(CacheStatsOnNodeSupplier.Stat.CACHE_MODEL.getName()));

        Map<String, Object> featureCacheStats =
                (Map<String, Object>) cacheStats.get(CacheStatsOnNodeSupplier.Stat.CACHE_FEATURE.getName());
        assertEquals(5, featureCacheStats.size());
        assertTrue(featureCacheStats.containsKey(CacheStatsOnNodeSupplier.Stat.CACHE_HIT_COUNT.getName()));
        assertTrue(featureCacheStats.containsKey(CacheStatsOnNodeSupplier.Stat.CACHE_MISS_COUNT.getName()));
        assertTrue(featureCacheStats.containsKey(CacheStatsOnNodeSupplier.Stat.CACHE_EVICTION_COUNT.getName()));
        assertTrue(featureCacheStats.containsKey(CacheStatsOnNodeSupplier.Stat.CACHE_ENTRY_COUNT.getName()));
        assertTrue(featureCacheStats.containsKey(CacheStatsOnNodeSupplier.Stat.CACHE_MEMORY_USAGE_IN_BYTES.getName()));
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
    public void testStoreStats() throws Exception {
        String customStoreName = "test";
        String customStore = indexName("test");
        createStore(customStore);

        Map<String, Map<String, Long>> infos = new HashMap<>();
        infos.put(DEFAULT_STORE_NAME, addElements(DEFAULT_STORE));
        infos.put(customStoreName, addElements(customStore));

        LTRStatsNodesResponse response = executeRequest();
        assertFalse(response.hasFailures());

        Map<String, Object> clusterStats = response.getClusterStats();
        Map<String, Object> stores = (Map<String, Object>) clusterStats.get(StatName.STORES.getName());

        assertStoreStats((Map<String, Object>) stores.get(DEFAULT_STORE_NAME), infos.get(DEFAULT_STORE_NAME));
        assertStoreStats((Map<String, Object>) stores.get(customStoreName), infos.get(customStoreName));
    }

    private void assertStoreStats(Map<String, Object> storeStat, Map<String, Long> expected) {
        assertEquals(expected.get(StoredFeatureSet.TYPE),
                storeStat.get(StoreStatsSupplier.Stat.STORE_FEATURE_SET_COUNT.getName()));

        assertEquals(expected.get(StoredFeature.TYPE),
                storeStat.get(StoreStatsSupplier.Stat.STORE_FEATURE_COUNT.getName()));

        assertEquals(expected.get(StoredLtrModel.TYPE),
                storeStat.get(StoreStatsSupplier.Stat.STORE_MODEL_COUNT.getName()));
    }

    private Map<String, Long> addElements(String store) throws Exception {
        Map<String, Long> counts = new HashMap<>();
        int nFeats = randomInt(20) + 1;
        int nSets = randomInt(20) + 1;
        int nModels = randomInt(20) + 1;
        counts.put(StoredFeature.TYPE, (long) nFeats);
        counts.put(StoredFeatureSet.TYPE, (long) nSets);
        counts.put(StoredLtrModel.TYPE, (long) nModels);
        addElements(store, nFeats, nSets, nModels);
        return counts;
    }

    private void addElements(String store, int nFeatures, int nSets, int nModels) throws Exception {
        for (int i = 0; i < nFeatures; i++) {
            StoredFeature feat = LtrTestUtils.randomFeature("feature" + i);
            addElement(feat, store);
        }

        List<StoredFeatureSet> sets = new ArrayList<>(nSets);
        for (int i = 0; i < nSets; i++) {
            StoredFeatureSet set = LtrTestUtils.randomFeatureSet("set" + i);
            addElement(set, store);
            sets.add(set);
        }

        for (int i = 0; i < nModels; i++) {
            addElement(LtrTestUtils.randomLinearModel("model" + i, sets.get(random().nextInt(sets.size()))), store);
        }
    }

    private LTRStatsNodesResponse executeRequest() throws ExecutionException, InterruptedException {
        LTRStatsRequestBuilder builder = new LTRStatsRequestBuilder(client());
        Set<String> statsToBeRetrieved = new HashSet<>(Arrays.asList(
                StatName.PLUGIN_STATUS.getName(), StatName.CACHE.getName(), StatName.STORES.getName()));
        builder.request().setStatsToBeRetrieved(statsToBeRetrieved);
        return builder.execute().get();
    }
}
