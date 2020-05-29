package com.o19s.es.ltr.stats.suppliers;

import com.o19s.es.ltr.feature.store.index.Caches;
import com.o19s.es.ltr.stats.StatName;
import org.elasticsearch.common.cache.Cache;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Aggregate stats on the cache used by the plugin per node.
 */
public class CacheStatsOnNodeSupplier implements Supplier<Map<String, Map<String, Object>>> {
    private final Caches caches;

    public CacheStatsOnNodeSupplier(Caches caches) {
        this.caches = caches;
    }

    @Override
    public Map<String, Map<String, Object>> get() {
        Map<String, Map<String, Object>> values = new HashMap<>();
        values.put(StatName.CACHE_FEATURE.getName(), getCacheStats(caches.featureCache()));
        values.put(StatName.CACHE_FEATURE_SET.getName(), getCacheStats(caches.featureSetCache()));
        values.put(StatName.CACHE_MODEL.getName(), getCacheStats(caches.modelCache()));
        return Collections.unmodifiableMap(values);
    }

    private Map<String, Object> getCacheStats(Cache<Caches.CacheKey, ?> cache) {
        Map<String, Object> stat = new HashMap<>();
        stat.put(StatName.CACHE_HIT_COUNT.getName(), cache.stats().getHits());
        stat.put(StatName.CACHE_MISS_COUNT.getName(), cache.stats().getMisses());
        stat.put(StatName.CACHE_EVICTION_COUNT.getName(), cache.stats().getEvictions());
        stat.put(StatName.CACHE_ENTRY_COUNT.getName(), cache.count());
        stat.put(StatName.CACHE_MEMORY_USAGE_IN_BYTES.getName(), cache.weight());
        return Collections.unmodifiableMap(stat);
    }
}
