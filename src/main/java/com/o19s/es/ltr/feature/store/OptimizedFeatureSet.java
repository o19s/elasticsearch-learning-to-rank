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

import com.o19s.es.ltr.LtrQueryContext;
import com.o19s.es.ltr.feature.Feature;
import com.o19s.es.ltr.feature.FeatureSet;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_ARRAY_HEADER;
import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_OBJECT_HEADER;
import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_OBJECT_REF;

public class OptimizedFeatureSet implements FeatureSet, Accountable {
    private final long BASE_RAM_USED = RamUsageEstimator.shallowSizeOfInstance(StoredFeatureSet.class);

    private final String name;
    private final List<Feature> features;
    private final Map<String, Integer> featureMap;

    public OptimizedFeatureSet(String name, List<Feature> features, Map<String, Integer> featureMap) {
        this.name = name;
        this.features = features;
        this.featureMap = featureMap;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<Query> toQueries(LtrQueryContext context, Map<String, Object> params) {
        List<Query> queries = new ArrayList<>(features.size());
        for(Feature feature : features) {
            if(context.isFeatureActive(feature.name())) {
                queries.add(feature.doToQuery(context, this, params));
            } else {
                queries.add(new MatchNoDocsQuery( "Feature " + feature.name() + " deactivated" ));
            }
        }
        return queries;
    }

    @Override
    public int featureOrdinal(String featureName) {
        Integer ordinal = featureMap.get(featureName);
        if (ordinal == null) {
            throw new IllegalArgumentException("Unknown feature [" + featureName + "]");
        }
        return ordinal;
    }

    @Override
    public Feature feature(int ord) {
        return features.get(ord);
    }

    @Override
    public Feature feature(String featureName) {
        return features.get(featureOrdinal(featureName));
    }

    @Override
    public boolean hasFeature(String featureName) {
        return featureMap.containsKey(featureName);
    }

    @Override
    public int size() {
        return features.size();
    }

    @Override
    public void validate() {
        for (Feature feature : features) {
            feature.validate(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OptimizedFeatureSet that = (OptimizedFeatureSet) o;

        if (!name.equals(that.name)) return false;
        return features.equals(that.features);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + features.hashCode();
        return result;
    }

    /**
     * Return the memory usage of this object in bytes. Negative values are illegal.
     */
    @Override
    public long ramBytesUsed() {
        return BASE_RAM_USED +
                featureMap.size() * NUM_BYTES_OBJECT_REF + NUM_BYTES_OBJECT_HEADER + NUM_BYTES_ARRAY_HEADER +
                features.stream().mapToLong((f) -> f instanceof Accountable ? ((Accountable)f).ramBytesUsed() : 1).sum();
    }
}
