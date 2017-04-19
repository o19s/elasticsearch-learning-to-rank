/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.o19s.es.ltr.feature;

import com.o19s.es.ltr.ranker.LtrRanker;

/**
 * Prebuilt model
 */
public class PrebuiltLtrModel implements LtrModel {
    private final String name;
    private final LtrRanker ranker;
    private final PrebuiltFeatureSet featureSet;

    public PrebuiltLtrModel(String name, LtrRanker ranker, PrebuiltFeatureSet featureSet) {
        this.name = name;
        this.ranker = ranker;
        this.featureSet = featureSet;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public LtrRanker ranker() {
        return ranker;
    }

    @Override
    public FeatureSet featureSet() {
        return featureSet;
    }
}
