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
import com.o19s.es.ltr.feature.store.StoredDerivedFeature;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import org.elasticsearch.common.cache.Cache;

import java.io.IOException;

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
        return caches.loadFeature(key(id), inner::load);
    }


    @Override
    public StoredDerivedFeature loadDerived(String id) throws IOException {
        return caches.loadDerivedFeature(key(id), inner::loadDerived);
    }

    @Override
    public StoredFeatureSet loadSet(String id) throws IOException {
        return caches.loadFeatureSet(key(id), inner::loadSet);
    }

    @Override
    public CompiledLtrModel loadModel(String id) throws IOException {
        return caches.loadModel(key(id), inner::loadModel);
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

    private <T> T innerGet(String id, Cache<Caches.CacheKey, T> cache) {
        return cache.get(key(id));
    }

    private Caches.CacheKey key(String id) {
        return new Caches.CacheKey(inner.getStoreName(), id);
    }
}
