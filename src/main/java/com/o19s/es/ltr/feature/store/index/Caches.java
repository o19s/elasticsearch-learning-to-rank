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

import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import org.apache.lucene.util.RamUsageEstimator;
import org.elasticsearch.common.cache.Cache;
import org.elasticsearch.common.cache.CacheBuilder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.monitor.jvm.JvmInfo;

import java.util.Iterator;
import java.util.Objects;

/**
 * Store various caches used by the plugin
 */
public class Caches {
    private final Cache<CacheKey, StoredFeature> featureCache;
    private final Cache<CacheKey, StoredFeatureSet> featureSetCache;
    private final Cache<CacheKey, StoredLtrModel> modelCache;
    private final long maxWeight;

    public Caches(TimeValue expireAfterWrite, TimeValue expireAfterAccess, long maxWeight) {
        this.featureCache = CacheBuilder.<CacheKey, StoredFeature>builder()
                .setExpireAfterWrite(expireAfterWrite)
                .setExpireAfterAccess(expireAfterAccess)
                .setMaximumWeight(maxWeight)
                .weigher((s, w) -> w.ramBytesUsed())
                .build();
        this.featureSetCache = CacheBuilder.<CacheKey, StoredFeatureSet>builder()
                .setExpireAfterWrite(expireAfterWrite)
                .setExpireAfterAccess(expireAfterAccess)
                .weigher((s, w) -> w.ramBytesUsed())
                .setMaximumWeight(maxWeight)
                .build();
        this.modelCache = CacheBuilder.<CacheKey, StoredLtrModel>builder()
                .setExpireAfterWrite(expireAfterWrite)
                .setExpireAfterAccess(expireAfterAccess)
                .weigher((s, w) -> w.ramBytesUsed())
                .setMaximumWeight(maxWeight)
                .build();
        this.maxWeight = maxWeight;
    }

    public Caches(Settings settings) {
        // TODO: use settings
        this(TimeValue.timeValueMinutes(10),
                TimeValue.timeValueMinutes(2),
                Math.min(JvmInfo.jvmInfo().getMem().getHeapMax().getBytes()/10, RamUsageEstimator.ONE_MB*10));
    }

    public void evict(String index) {
        evict(index, featureCache);
        evict(index, featureSetCache);
        evict(index, modelCache);
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

    public Cache<CacheKey, StoredLtrModel> modelCache() {
        return modelCache;
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
}
