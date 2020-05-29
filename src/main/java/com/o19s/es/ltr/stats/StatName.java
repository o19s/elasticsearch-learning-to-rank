package com.o19s.es.ltr.stats;

public enum StatName {
    PLUGIN_STATUS("status"),
    STORES("stores"),
    CACHE("cache"),

    STORE_STATUS("status"),
    STORE_FEATURE_COUNT("feature_count"),
    STORE_FEATURE_SET_COUNT("featureset_count"),
    STORE_MODEL_COUNT("model_count"),

    CACHE_FEATURE("feature"),
    CACHE_FEATURE_SET("featureset"),
    CACHE_MODEL("model"),

    CACHE_HIT_COUNT("hit_count"),
    CACHE_MISS_COUNT("miss_count"),
    CACHE_EVICTION_COUNT("eviction_count"),
    CACHE_ENTRY_COUNT("entry_count"),
    CACHE_MEMORY_USAGE_IN_BYTES("memory_usage_in_bytes");

    private final String name;

    StatName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
