package com.o19s.es.ltr.stats.suppliers;

import com.o19s.es.ltr.LtrTestUtils;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import com.o19s.es.ltr.stats.StatName;
import org.elasticsearch.test.ESIntegTestCase;
import org.junit.Before;

import java.util.Map;

public class StoreStatsSupplierTests extends ESIntegTestCase {
    private StoreStatsSupplier storeStatsSupplier;

    @Before
    public void setup() {
        storeStatsSupplier = new StoreStatsSupplier(client(), clusterService());
    }

    public void testGetStoreStatsNoLtrStore() {
        Map<String, Map<String, Object>> stats = storeStatsSupplier.get();
        assertTrue(stats.isEmpty());
    }

    public void testGetStoreStatsSuccess() {
        createIndex(IndexFeatureStore.DEFAULT_STORE);
        flush();
        index(IndexFeatureStore.DEFAULT_STORE, "_doc", "featureset_1", LtrTestUtils.testFeatureSetString());
        index(IndexFeatureStore.DEFAULT_STORE, "_doc", "model_1", LtrTestUtils.testModelString());
        flushAndRefresh(IndexFeatureStore.DEFAULT_STORE);

        Map<String, Map<String, Object>> stats = storeStatsSupplier.get();
        Map<String, Object> ltrStoreStats = stats.get(IndexFeatureStore.DEFAULT_STORE);

        assertNotNull(ltrStoreStats);
        String status = (String) ltrStoreStats.get(StatName.STORE_STATUS.getName());
        assertTrue(status.equals("green") || status.equals("yellow"));
        assertEquals(2, ltrStoreStats.get(StatName.STORE_FEATURE_COUNT.getName()));
        assertEquals(1, ltrStoreStats.get(StatName.STORE_FEATURE_SET_COUNT.getName()));
        assertEquals(1L, ltrStoreStats.get(StatName.STORE_MODEL_COUNT.getName()));
    }
}
