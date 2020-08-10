package com.o19s.es.ltr.stats.suppliers;

import com.o19s.es.ltr.LtrTestUtils;
import com.o19s.es.ltr.feature.store.CompiledLtrModel;
import com.o19s.es.ltr.feature.store.MemStore;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.index.CachedFeatureStore;
import com.o19s.es.ltr.feature.store.index.Caches;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.test.ESTestCase;
import org.hamcrest.collection.IsMapContaining;
import org.junit.Before;

import java.io.IOException;
import java.util.Map;

import static com.o19s.es.ltr.stats.suppliers.CacheStatsOnNodeSupplier.Stat;

public class CacheStatsOnNodeSupplierTests extends ESTestCase {
    private MemStore memStore;
    private Caches caches;

    private CacheStatsOnNodeSupplier cacheStatsOnNodeSupplier;

    @Before
    public void setup() {
        memStore = new MemStore();
        caches = new Caches(Settings.EMPTY);
        cacheStatsOnNodeSupplier = new CacheStatsOnNodeSupplier(caches);
    }

    public void testGetCacheStatsInitialState() {
        Map<String, Map<String, Object>> stats = cacheStatsOnNodeSupplier.get();
        assertCacheStats(stats.get(Stat.CACHE_FEATURE.getName()), 0, 0, 0, 0);
        assertCacheStats(stats.get(Stat.CACHE_FEATURE_SET.getName()), 0, 0, 0, 0);
        assertCacheStats(stats.get(Stat.CACHE_MODEL.getName()), 0, 0, 0, 0);
    }

    public void testGetCacheStats() throws IOException {
        CachedFeatureStore store = new CachedFeatureStore(memStore, caches);

        StoredFeature feat1 = LtrTestUtils.randomFeature();
        StoredFeature feat2 = LtrTestUtils.randomFeature();
        memStore.add(feat1);
        memStore.add(feat2);

        StoredFeatureSet set = LtrTestUtils.randomFeatureSet();
        memStore.add(set);

        CompiledLtrModel model1 = LtrTestUtils.buildRandomModel();
        CompiledLtrModel model2 = LtrTestUtils.buildRandomModel();
        memStore.add(model1);
        memStore.add(model2);

        store.load(feat1.name());
        store.load(feat1.name());
        store.load(feat2.name());

        store.loadSet(set.name());

        for (int i = 0; i < 2; i++) {
            store.loadModel(model1.name());
            store.loadModel(model2.name());
        }

        caches.evictModel(memStore.getStoreName(), model1.name());

        Map<String, Map<String, Object>> stats = cacheStatsOnNodeSupplier.get();

        assertCacheStats(stats.get(Stat.CACHE_FEATURE.getName()), 1, 2, 0, 2);
        assertCacheStats(stats.get(Stat.CACHE_FEATURE_SET.getName()), 0, 1, 0, 1);
        assertCacheStats(stats.get(Stat.CACHE_MODEL.getName()), 2, 2, 1, 1);
        assertMemoryUsage(stats);
    }

    private void assertCacheStats(Map<String, Object> stat, long hits,
                                  long misses, long evictions, int entries) {
        assertThat(stat, IsMapContaining.hasEntry(Stat.CACHE_HIT_COUNT.getName(), hits));
        assertThat(stat, IsMapContaining.hasEntry(Stat.CACHE_MISS_COUNT.getName(), misses));
        assertThat(stat, IsMapContaining.hasEntry(Stat.CACHE_EVICTION_COUNT.getName(), evictions));
        assertThat(stat, IsMapContaining.hasEntry(Stat.CACHE_ENTRY_COUNT.getName(), entries));
    }

    private void assertMemoryUsage(Map<String, Map<String, Object>> stats) {
        assertTrue((long) stats.get(Stat.CACHE_FEATURE.getName())
                .get(Stat.CACHE_MEMORY_USAGE_IN_BYTES.getName()) > 0);
        assertTrue((long) stats.get(Stat.CACHE_FEATURE_SET.getName())
                .get(Stat.CACHE_MEMORY_USAGE_IN_BYTES.getName()) > 0);
        assertTrue((long) stats.get(Stat.CACHE_MODEL.getName())
                .get(Stat.CACHE_MEMORY_USAGE_IN_BYTES.getName()) > 0);
    }
}
