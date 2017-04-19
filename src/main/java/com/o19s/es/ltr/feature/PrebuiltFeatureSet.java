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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class PrebuiltFeatureSet implements FeatureSet {
    private final PrebuiltFeature[] features;
    private final String name;

    public PrebuiltFeatureSet(String name, PrebuiltFeature[] features) {
        this.name = name;
        this.features = features;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<Feature> features() {
        return Arrays.asList(features);
    }

    @Override
    public int featureOrdinal(String featureName) {
        // slow, not meant for prod usage
        for (int i = 0; i < features.length; i++) {
            if(Objects.equals(features[i].getName(), featureName)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public PrebuiltFeature feature(String name) {
        return features[featureOrdinal(name)];
    }

    /**
     * Check if this set supports this feature
     *
     * @param name the name of the feature
     * @return true if suported false otherwise
     */
    @Override
    public boolean hasFeature(String name) {
        return feature(name) != null;
    }
}
