package com.o19s.es.ltr.stats.suppliers;

import com.o19s.es.ltr.feature.store.index.Caches;
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

    public enum Stat {
        CACHE_FEATURE("feature"),
        CACHE_FEATURE_SET("featureset"),
        CACHE_MODEL("model"),

        CACHE_HIT_COUNT("hit_count"),
        CACHE_MISS_COUNT("miss_count"),
        CACHE_EVICTION_COUNT("eviction_count"),
        CACHE_ENTRY_COUNT("entry_count"),
        CACHE_MEMORY_USAGE_IN_BYTES("memory_usage_in_bytes");

        private final String name;

        Stat(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public CacheStatsOnNodeSupplier(Caches caches) {
        this.caches = caches;
    }

    @Override
    public Map<String, Map<String, Object>> get() {
        Map<String, Map<String, Object>> values = new HashMap<>();
        values.put(Stat.CACHE_FEATURE.getName(), getCacheStats(caches.featureCache()));
        values.put(Stat.CACHE_FEATURE_SET.getName(), getCacheStats(caches.featureSetCache()));
        values.put(Stat.CACHE_MODEL.getName(), getCacheStats(caches.modelCache()));
        return Collections.unmodifiableMap(values);
    }

    private Map<String, Object> getCacheStats(Cache<Caches.CacheKey, ?> cache) {
        Map<String, Object> stat = new HashMap<>();
        stat.put(Stat.CACHE_HIT_COUNT.getName(), cache.stats().getHits());
        stat.put(Stat.CACHE_MISS_COUNT.getName(), cache.stats().getMisses());
        stat.put(Stat.CACHE_EVICTION_COUNT.getName(), cache.stats().getEvictions());
        stat.put(Stat.CACHE_ENTRY_COUNT.getName(), cache.count());
        stat.put(Stat.CACHE_MEMORY_USAGE_IN_BYTES.getName(), cache.weight());
        return Collections.unmodifiableMap(stat);
    }
}
