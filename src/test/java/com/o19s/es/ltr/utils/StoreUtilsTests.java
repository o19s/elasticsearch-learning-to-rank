package com.o19s.es.ltr.utils;

import com.o19s.es.ltr.LtrTestUtils;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import org.elasticsearch.test.ESIntegTestCase;
import org.junit.Before;

import java.util.Map;

public class StoreUtilsTests extends ESIntegTestCase {
    private StoreUtils storeUtils;

    @Before
    public void setup() {
        storeUtils = new StoreUtils(client(), clusterService());
    }

    public void testCheckLtrStoreExists() {
        createIndex(IndexFeatureStore.DEFAULT_STORE);
        flush();
        assertTrue(storeUtils.checkLtrStoreExists(IndexFeatureStore.DEFAULT_STORE));
    }

    public void testGetAllLtrStoreNamesNoLtrStores() {
        assertTrue(storeUtils.getAllLtrStoreNames().isEmpty());
    }

    public void testGetAllLtrStoreNames() {
        createIndex(IndexFeatureStore.DEFAULT_STORE);
        flush();
        assertEquals(1, storeUtils.getAllLtrStoreNames().size());
        assertEquals(IndexFeatureStore.DEFAULT_STORE, storeUtils.getAllLtrStoreNames().get(0));
    }

    public void testGetLtrStoreHealthStatus() {
        createIndex(IndexFeatureStore.DEFAULT_STORE);
        flush();
        String status = storeUtils.getLtrStoreHealthStatus(IndexFeatureStore.DEFAULT_STORE);
        assertTrue(status.equals("green") || status.equals("yellow"));
    }

    public void testGetFeatureSets() {
        createIndex(IndexFeatureStore.DEFAULT_STORE);
        flush();
        index(IndexFeatureStore.DEFAULT_STORE, "_doc", "featureset_1", LtrTestUtils.testFeatureSetString());
        flushAndRefresh(IndexFeatureStore.DEFAULT_STORE);
        Map<String, Integer> featureset = storeUtils.getFeatureCountPerFeatureSet(IndexFeatureStore.DEFAULT_STORE);

        assertEquals(1, featureset.size());
        assertEquals(2, (int) featureset.values().stream().reduce(Integer::sum).get());
    }

    public void testGetModelCount() {
        createIndex(IndexFeatureStore.DEFAULT_STORE);
        flush();
        index(IndexFeatureStore.DEFAULT_STORE, "_doc", "model_1", LtrTestUtils.testModelString());
        flushAndRefresh(IndexFeatureStore.DEFAULT_STORE);
        assertEquals(1, storeUtils.getModelCount(IndexFeatureStore.DEFAULT_STORE));
    }
}
