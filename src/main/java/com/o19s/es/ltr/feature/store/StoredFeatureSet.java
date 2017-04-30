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

import com.o19s.es.ltr.feature.Feature;
import com.o19s.es.ltr.feature.FeatureSet;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.QueryShardContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.RandomAccess;

import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_ARRAY_HEADER;
import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_OBJECT_HEADER;
import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_OBJECT_REF;

public class StoredFeatureSet implements FeatureSet, Accountable {
    private final long BASE_RAM_USED = RamUsageEstimator.shallowSizeOfInstance(StoredFeatureSet.class);
    private final String name;
    private final Map<String, Integer> featureMap;
    private final List<StoredFeature> features;

    private static final ObjectParser<ParsingState, Void> PARSER;

    static {
        PARSER = new ObjectParser<>("ltr_feature", ParsingState::new);
        PARSER.declareString(ParsingState::setName, new ParseField("name"));
        PARSER.declareObjectArray(ParsingState::setFeatures,
                (p, c) -> StoredFeature.parse(p),
                new ParseField("features"));
    }

    public static StoredFeatureSet parse(XContentParser parser) {
        try {
            ParsingState state = PARSER.apply(parser, null);
            if (state.name == null) {
                throw new ParsingException(parser.getTokenLocation(), "Field [name] is mandatory");
            }
            if (state.features == null) {
                throw new ParsingException(parser.getTokenLocation(), "Field [features] is mandatory");
            }
            if (state.features.isEmpty()) {
                throw new ParsingException(parser.getTokenLocation(), "At least one feature must be defined in [features]");
            }
            return new StoredFeatureSet(state.name, state.features);
        } catch (IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage(), iae);
        }
    }

    public StoredFeatureSet(String name, List<StoredFeature> features) {
        this.name = Objects.requireNonNull(name);
        features = Objects.requireNonNull(features);
        if (!(features instanceof RandomAccess)) {
            features = new ArrayList<>(features);
        }
        this.features = features;
        featureMap = new HashMap<>();
        int ordinal = -1;
        for (StoredFeature feature : features) {
            ordinal++;
            if (featureMap.put(feature.name(), ordinal) != null) {
                throw new IllegalArgumentException("Feature [" + feature.name() + "] defined twice in this set: " +
                        "feature names must be unique in a set.");
            }
        }

    }
    @Override
    public String name() {
        return name;
    }

    @Override
    public List<Query> toQueries(QueryShardContext context, Map<String, Object> params) {
        List<Query> queries = new ArrayList<>(features.size());
        for(Feature feature : features) {
            queries.add(feature.doToQuery(context, params));
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
    public StoredFeature feature(int ord) {
        return features.get(ord);
    }

    @Override
    public StoredFeature feature(String featureName) {
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
    public long ramBytesUsed() {
        return BASE_RAM_USED +
                featureMap.size() * NUM_BYTES_OBJECT_REF + NUM_BYTES_OBJECT_HEADER + NUM_BYTES_ARRAY_HEADER +
                features.stream().mapToLong(StoredFeature::ramBytesUsed).sum();
    }

    private static class ParsingState {
        private String name;
        private List<StoredFeature> features;

        public void setName(String name) {
            this.name = name;
        }

        public void setFeatures(List<StoredFeature> features) {
            this.features = features;
        }
    }
}
