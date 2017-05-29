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

import com.o19s.es.ltr.feature.store.CompiledLtrModel;
import com.o19s.es.ltr.feature.store.FeatureStore;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import org.elasticsearch.common.CheckedFunction;
import org.elasticsearch.common.cache.Cache;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * Cache layer on top of an {@link IndexFeatureStore}
 */
public class CachedFeatureStore implements FeatureStore {
    private final FeatureStore inner;
    private final Caches caches;

    public CachedFeatureStore(FeatureStore inner, Caches caches) {
        this.inner = inner;
        this.caches = caches;
    }

    @Override
    public String getStoreName() {
        return inner.getStoreName();
    }

    @Override
    public StoredFeature load(String id) throws IOException {
        return innerLoad(id, caches.featureCache(), inner::load);
    }

    @Override
    public StoredFeatureSet loadSet(String id) throws IOException {
        return innerLoad(id, caches.featureSetCache(), inner::loadSet);
    }

    @Override
    public CompiledLtrModel loadModel(String id) throws IOException {
        return innerLoad(id, caches.modelCache(), inner::loadModel);
    }

    StoredFeature getCachedFeature(String id) {
        return innerGet(id, caches.featureCache());
    }

    StoredFeatureSet getCachedFeatureSet(String id) {
        return innerGet(id, caches.featureSetCache());
    }

    CompiledLtrModel getCachedModel(String id) {
        return innerGet(id, caches.modelCache());
    }

    public long totalWeight() {
        return featuresWeight() + featureSetWeight() + modelWeight();
    }

    public long featuresWeight() {
        return caches.featureCache().weight();
    }

    public long featureSetWeight() {
        return caches.featureSetCache().weight();
    }

    public long modelWeight() {
        return caches.modelCache().weight();
    }

    private <T> T innerLoad(String id, Cache<Caches.CacheKey, T> cache, CheckedFunction<String, T, IOException> loader) throws IOException {
        try {
            return cache.computeIfAbsent(new Caches.CacheKey(inner.getStoreName(), id), (k) -> loader.apply(k.getId()));
        } catch (ExecutionException e) {
            throw new IOException(e.getMessage(), e.getCause());
        }
    }

    private <T> T innerGet(String id, Cache<Caches.CacheKey, T> cache) {
        return cache.get(new Caches.CacheKey(inner.getStoreName(), id));
    }
}
