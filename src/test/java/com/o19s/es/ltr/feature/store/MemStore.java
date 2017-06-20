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

package com.o19s.es.ltr.feature.store;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * in memory test store
 */
public class MemStore implements FeatureStore {
    private final Map<String, StoredFeature> features = new HashMap<>();
    private final Map<String, StoredFeatureSet> sets = new HashMap<>();
    private final Map<String, CompiledLtrModel> models = new HashMap<>();

    private final String storeName;

    public MemStore(String storeName) {
        this.storeName = storeName;
    }

    public MemStore() {
        this("memstore");
    }

    @Override
    public String getStoreName() {
        return storeName;
    }

    @Override
    public StoredFeature load(String id) throws IOException {
        StoredFeature feature = features.get(id);
        if (feature == null) {
            throw new IllegalArgumentException("Feature [" + id + "] not found");
        }
        return feature;
    }

    @Override
    public StoredFeatureSet loadSet(String id) throws IOException {
        StoredFeatureSet set = sets.get(id);
        if (set == null) {
            throw new IllegalArgumentException("Feature [" + id + "] not found");
        }
        return set;
    }

    @Override
    public CompiledLtrModel loadModel(String id) throws IOException {
        CompiledLtrModel model = models.get(id);
        if (model == null) {
            throw new IllegalArgumentException("Feature [" + id + "] not found");
        }
        return model;
    }

    public void add(StoredFeature feature) {
        features.put(feature.name(), feature);
    }

    public void add(StoredFeatureSet set) {
        sets.put(set.name(), set);
    }

    public void add(CompiledLtrModel model) {
        models.put(model.name(), model);
    }

    public void clear() {
        features.clear();
        sets.clear();
        models.clear();
    }
}
