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
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;
import org.elasticsearch.common.CheckedFunction;
import org.elasticsearch.common.cache.Cache;
import org.elasticsearch.common.cache.CacheBuilder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
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
    private final Cache<CacheKey, StoredFeature> featureCache;
    private final Cache<CacheKey, StoredFeatureSet> featureSetCache;
    private final Cache<CacheKey, CompiledLtrModel> modelCache;
    private final Map<String, PerStoreStats> perStoreStats = new ConcurrentHashMap<>();
    private final long maxWeight;

    public Caches(TimeValue expireAfterWrite, TimeValue expireAfterAccess, long maxWeight) {
        this.featureCache = CacheBuilder.<CacheKey, StoredFeature>builder()
                .setExpireAfterWrite(expireAfterWrite)
                .setExpireAfterAccess(expireAfterAccess)
                .setMaximumWeight(maxWeight)
                .weigher((s, w) -> w.ramBytesUsed())
                .removalListener((l) -> this.onRemove(l.getKey(), l.getValue()))
                .build();
        this.featureSetCache = CacheBuilder.<CacheKey, StoredFeatureSet>builder()
                .setExpireAfterWrite(expireAfterWrite)
                .setExpireAfterAccess(expireAfterAccess)
                .weigher((s, w) -> w.ramBytesUsed())
                .setMaximumWeight(maxWeight)
                .removalListener((l) -> this.onRemove(l.getKey(), l.getValue()))
                .build();
        this.modelCache = CacheBuilder.<CacheKey, CompiledLtrModel>builder()
                .setExpireAfterWrite(expireAfterWrite)
                .setExpireAfterAccess(expireAfterAccess)
                .weigher((s, w) -> w.ramBytesUsed())
                .setMaximumWeight(maxWeight)
                .removalListener((l) -> this.onRemove(l.getKey(), l.getValue()))
                .build();
        this.maxWeight = maxWeight;
    }

    public Caches(Settings settings) {
        // TODO: use settings
        this(TimeValue.timeValueMinutes(10),
                TimeValue.timeValueMinutes(2),
                Math.min(JvmInfo.jvmInfo().getMem().getHeapMax().getBytes()/10, RamUsageEstimator.ONE_MB*10));
    }

    private void onAdd(CacheKey k, Accountable acc) {
        perStoreStats.compute(k.getStoreName(), (k2, v) -> v != null ? v.add(acc) : new PerStoreStats(acc));
    }

    private void onRemove(CacheKey k, Accountable acc) {
        perStoreStats.compute(k.getStoreName(), (k2, v) -> {
            assert v != null;
            // return null should remove the entry
            return v.remove(acc) > 0 ? v : null;
        });
    }

    StoredFeature loadFeature(CacheKey key, CheckedFunction<String, StoredFeature, IOException> loader) throws IOException {
        return cacheLoad(key, featureCache, loader);
    }

    StoredFeatureSet loadFeatureSet(CacheKey key, CheckedFunction<String, StoredFeatureSet, IOException> loader) throws IOException {
        return cacheLoad(key, featureSetCache, loader);
    }

    CompiledLtrModel loadModel(CacheKey key, CheckedFunction<String, CompiledLtrModel, IOException> loader) throws IOException {
        return cacheLoad(key, modelCache, loader);
    }

    private <E extends Accountable> E cacheLoad(CacheKey key, Cache<CacheKey, E> cache,
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

    public Cache<CacheKey, StoredFeature> featureCache() {
        return featureCache;
    }

    public Cache<CacheKey, StoredFeatureSet> featureSetCache() {
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

        PerStoreStats(Accountable acc) {
            add(Objects.requireNonNull(acc));
        }

        public PerStoreStats add(Accountable elt) {
            int nb = update(true, elt);
            assert nb > 0;
            return this;
        }

        private long remove(Accountable elt) {
            return update(false, elt);
        }

        private int update(boolean add, Accountable elt) {
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
            long ramUsed = elt.ramBytesUsed();

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
