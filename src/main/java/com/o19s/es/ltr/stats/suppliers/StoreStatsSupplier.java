package com.o19s.es.ltr.stats.suppliers;

import com.o19s.es.ltr.stats.StatName;
import com.o19s.es.ltr.utils.StoreUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A supplier which provides information on all feature stores. It provides basic
 * information such as the index health and count of feature sets, features and
 * models in the store.
 */
public class StoreStatsSupplier implements Supplier<Map<String, Map<String, Object>>> {
    private final StoreUtils storeUtils;

    public StoreStatsSupplier(StoreUtils storeUtils) {
        this.storeUtils = storeUtils;
    }

    @Override
    public Map<String, Map<String, Object>> get() {
        Map<String, Map<String, Object>> storeStats = new ConcurrentHashMap<>();
        List<String> storeNames = storeUtils.getAllLtrStoreNames();
        storeNames.forEach(s -> storeStats.put(s, getStoreStat(s)));
        return storeStats;
    }

    private Map<String, Object> getStoreStat(String storeName) {
        if (!storeUtils.checkLtrStoreExists(storeName)) {
            throw new IllegalArgumentException("LTR Store [" + storeName + "] doesn't exist.");
        }
        Map<String, Object> storeStat = new HashMap<>();
        storeStat.put(StatName.STORE_STATUS.getName(), storeUtils.getLtrStoreHealthStatus(storeName));
        Map<String, Integer> featureSets = storeUtils.getFeatureCountPerFeatureSet(storeName);
        storeStat.put(StatName.STORE_FEATURE_COUNT.getName(), featureSets.values().stream().reduce(0, Integer::sum));
        storeStat.put(StatName.STORE_FEATURE_SET_COUNT.getName(), featureSets.size());
        storeStat.put(StatName.STORE_MODEL_COUNT.getName(), storeUtils.getModelCount(storeName));
        return storeStat;
    }
}
