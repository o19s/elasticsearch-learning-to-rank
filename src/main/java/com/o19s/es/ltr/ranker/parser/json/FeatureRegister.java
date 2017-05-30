/*
 * Copyright [2017] OpenSource Connections
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
package com.o19s.es.ltr.ranker.parser.json;

import com.o19s.es.ltr.feature.FeatureSet;

import java.util.HashSet;
import java.util.Set;

// Used to track which features are used
// by a given model by tracking how models
// access features
public class FeatureRegister
{
    public FeatureRegister (FeatureSet set) {
        _featureSet = set;
    }

    public int useFeature(String featureName) {
        int featureOrd = _featureSet.featureOrdinal(featureName);
        _usedFeatures.add(featureOrd);
        return featureOrd;
    }

    public int numFeaturesUsed() {
        return _usedFeatures.size();
    }

    public int numFeaturesAvail() {return _featureSet.size();}

    private Set<Integer> _usedFeatures = new HashSet<Integer>();
    private FeatureSet _featureSet;
}

