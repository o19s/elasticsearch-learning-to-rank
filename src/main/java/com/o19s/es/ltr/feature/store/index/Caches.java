/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.o19s.es.ltr.feature.store.index;

import com.o19s.es.ltr.feature.Feature;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.feature.store.CompiledLtrModel;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;
import org.elasticsearch.core.CheckedFunction;
import org.elasticsearch.common.cache.Cache;
import org.elasticsearch.common.cache.CacheBuilder;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.monitor.jvm.JvmInfo;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Store various caches used by the plugin
 */
public class Caches {
    public static final Setting<ByteSizeValue> LTR_CACHE_MEM_SETTING;
    public static final Setting<TimeValue> LTR_CACHE_EXPIRE_AFTER_WRITE = Setting.timeSetting("ltr.caches.expire_after_write",
            TimeValue.timeValueHours(1),
            TimeValue.timeValueNanos(0),
            Setting.Property.NodeScope);
    public static final Setting<TimeValue> LTR_CACHE_EXPIRE_AFTER_READ = Setting.timeSetting("ltr.caches.expire_after_read",
            TimeValue.timeValueHours(1),
            TimeValue.timeValueNanos(0),
            Setting.Property.NodeScope);

    private final Cache<CacheKey, Feature> featureCache;
    private final Cache<CacheKey, FeatureSet> featureSetCache;
    private final Cache<CacheKey, CompiledLtrModel> modelCache;

    static {
        LTR_CACHE_MEM_SETTING = Setting.memorySizeSetting("ltr.caches.max_mem",
                (s) -> new ByteSizeValue(Math.min(RamUsageEstimator.ONE_MB*10,
                        JvmInfo.jvmInfo().getMem().getHeapMax().getBytes()/10)).toString(),
                Setting.Property.NodeScope);
    }
    private final Map<String, PerStoreStats> perStoreStats = new ConcurrentHashMap<>();
    private final long maxWeight;

    public Caches(TimeValue expAfterWrite, TimeValue expAfterAccess, ByteSizeValue maxWeight) {
        this.featureCache = configCache(CacheBuilder.<CacheKey, Feature>builder(), expAfterWrite, expAfterAccess, maxWeight)
                .weigher(Caches::weigther)
                .removalListener((l) -> this.onRemove(l.getKey(), l.getValue()))
                .build();
        this.featureSetCache = configCache(CacheBuilder.<CacheKey, FeatureSet>builder(), expAfterWrite, expAfterAccess, maxWeight)
                .weigher(Caches::weigther)
                .removalListener((l) -> this.onRemove(l.getKey(), l.getValue()))
                .build();
        this.modelCache = configCache(CacheBuilder.<CacheKey, CompiledLtrModel>builder(), expAfterWrite, expAfterAccess, maxWeight)
                .weigher((s, w) -> w.ramBytesUsed())
                .removalListener((l) -> this.onRemove(l.getKey(), l.getValue()))
                .build();
        this.maxWeight = maxWeight.getBytes();
    }

    public static long weigther(CacheKey key, Object data) {
        if (data instanceof Accountable) {
            return ((Accountable)data).ramBytesUsed();
        }
        return 1;
    }

    private <K, V> CacheBuilder<K, V> configCache(CacheBuilder<K, V> builder, TimeValue expireAfterWrite,
                                                  TimeValue expireAfterAccess, ByteSizeValue maxWeight) {
        if (expireAfterWrite.nanos() > 0) {
            builder.setExpireAfterWrite(expireAfterWrite);
        }
        if (expireAfterAccess.nanos() > 0) {
            builder.setExpireAfterAccess(expireAfterAccess);
        }
        builder.setMaximumWeight(maxWeight.getBytes());
        return builder;
    }

    public Caches(Settings settings) {
        this(LTR_CACHE_EXPIRE_AFTER_WRITE.get(settings),
                LTR_CACHE_EXPIRE_AFTER_READ.get(settings),
                LTR_CACHE_MEM_SETTING.get(settings));
    }

    private void onAdd(CacheKey k, Object acc) {
        perStoreStats.compute(k.getStoreName(), (k2, v) -> v != null ? v.add(acc) : new PerStoreStats(acc));
    }

    private void onRemove(CacheKey k, Object acc) {
        perStoreStats.compute(k.getStoreName(), (k2, v) -> {
            assert v != null;
            // return null should remove the entry
            return v.remove(acc) > 0 ? v : null;
        });
    }

    Feature loadFeature(CacheKey key, CheckedFunction<String, Feature, IOException> loader) throws IOException {
        return cacheLoad(key, featureCache, loader);
    }

    FeatureSet loadFeatureSet(CacheKey key, CheckedFunction<String, FeatureSet, IOException> loader) throws IOException {
        return cacheLoad(key, featureSetCache, loader);
    }

    CompiledLtrModel loadModel(CacheKey key, CheckedFunction<String, CompiledLtrModel, IOException> loader) throws IOException {
        return cacheLoad(key, modelCache, loader);
    }

    private <E> E cacheLoad(CacheKey key, Cache<CacheKey, E> cache,
                                                CheckedFunction<String, E, IOException> loader) throws IOException {
        try {
            return cache.computeIfAbsent(key, (k) -> {
                E elt = loader.apply(k.getId());
                if (elt != null) {
                    onAdd(k, elt);
                }
                return elt;
            });
        } catch (ExecutionException e) {
            throw new IOException(e.getMessage(), e.getCause());
        }
    }

    public void evict(String index) {
        evict(index, featureCache);
        evict(index, featureSetCache);
        evict(index, modelCache);
    }

    public void evictFeature(String index, String name) {
        featureCache.invalidate(new CacheKey(index, name));
    }

    public void evictFeatureSet(String index, String name) {
        featureSetCache.invalidate(new CacheKey(index, name));
    }

    public void evictModel(String index, String name) {
        modelCache.invalidate(new CacheKey(index, name));
    }

    private void evict(String index, Cache<CacheKey, ?> cache) {
        Iterator<CacheKey> ite = cache.keys().iterator();
        while(ite.hasNext()) {
            if(ite.next().storeName.equals(index)) {
                ite.remove();
            }
        }
    }

    public Cache<CacheKey, Feature> featureCache() {
        return featureCache;
    }

    public Cache<CacheKey, FeatureSet> featureSetCache() {
        return featureSetCache;
    }

    public Cache<CacheKey, CompiledLtrModel> modelCache() {
        return modelCache;
    }

    public Set<String> getCachedStoreNames() {
        return perStoreStats.keySet();
    }

    public Stream<Map.Entry<String, PerStoreStats>> perStoreStatsStream() {
        return perStoreStats.entrySet().stream();
    }

    public PerStoreStats getPerStoreStats(String store) {
        PerStoreStats stats = perStoreStats.get(store);
        if (stats != null) {
            return stats;
        }
        return PerStoreStats.EMPTY;
    }

    public long getMaxWeight() {
        return maxWeight;
    }

    public static class CacheKey {
        private final String storeName;
        private final String id;

        public CacheKey(String storeName, String id) {
            this.storeName = Objects.requireNonNull(storeName);
            this.id = Objects.requireNonNull(id);
        }

        public String getStoreName() {
            return storeName;
        }

        public String getId() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CacheKey cacheKey = (CacheKey) o;

            if (!storeName.equals(cacheKey.storeName)) return false;
            return id.equals(cacheKey.id);
        }

        @Override
        public int hashCode() {
            int result = storeName.hashCode();
            result = 31 * result + id.hashCode();
            return result;
        }
    }

    public static class PerStoreStats {
        public static final PerStoreStats EMPTY = new PerStoreStats();
        private final AtomicLong ramAll = new AtomicLong();
        private final AtomicInteger countAll = new AtomicInteger();

        private final AtomicLong featureRam = new AtomicLong();
        private final AtomicInteger featureCount = new AtomicInteger();
        private final AtomicLong featureSetRam = new AtomicLong();
        private final AtomicInteger featureSetCount = new AtomicInteger();
        private final AtomicLong modelRam = new AtomicLong();
        private final AtomicInteger modelCount = new AtomicInteger();

        PerStoreStats() {}

        PerStoreStats(Object acc) {
            add(Objects.requireNonNull(acc));
        }

        public PerStoreStats add(Object elt) {
            int nb = update(true, elt);
            assert nb > 0;
            return this;
        }

        private long remove(Object elt) {
            return update(false, elt);
        }

        private int update(boolean add, Object elt) {
            Objects.requireNonNull(elt);
            final AtomicInteger count;
            final AtomicLong ram;
            final int factor = add ? 1 : -1;
            if (elt instanceof Feature) {
                count = featureCount;
                ram = featureRam;
            } else if (elt instanceof FeatureSet) {
                count = featureSetCount;
                ram = featureSetRam;
            } else if (elt instanceof CompiledLtrModel) {
                count = modelCount;
                ram = modelRam;
            } else {
                throw new IllegalArgumentException("Unsupported class " + elt.getClass());
            }
            long ramUsed = 1;
            if (elt instanceof Accountable) {
                ramUsed = ((Accountable)elt).ramBytesUsed();
            }

            ram.addAndGet(factor * ramUsed);
            assert ram.get() >= 0;
            count.addAndGet(factor);
            assert count.get() >= 0;

            ramAll.addAndGet(factor * ramUsed);
            assert ramAll.get() >= 0;
            return countAll.addAndGet(factor);
        }

        public long totalRam() {
            return ramAll.get();
        }

        public int totalCount() {
            return countAll.get();
        }

        public long featureRam() {
            return featureRam.get();
        }

        public int featureCount() {
            return featureCount.get();
        }

        public long featureSetRam() {
            return featureSetRam.get();
        }

        public int featureSetCount() {
            return featureSetCount.get();
        }

        public long modelRam() {
            return modelRam.get();
        }

        public int modelCount() {
            return modelCount.get();
        }
    }
}
