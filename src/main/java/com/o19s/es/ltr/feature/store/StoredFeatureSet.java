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
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.QueryShardContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_ARRAY_HEADER;
import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_OBJECT_HEADER;
import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_OBJECT_REF;

public class StoredFeatureSet implements FeatureSet, Accountable, StorableElement {
    public static final int MAX_FEATURES = 10000;
    public static final String TYPE = "featureset";
    private final long BASE_RAM_USED = RamUsageEstimator.shallowSizeOfInstance(StoredFeatureSet.class);
    private final String name;
    private final Map<String, Integer> derivedFeatureMap;
    private final Map<String, Integer> featureMap;
    private final List<StoredFeature> features;
    private final List<StoredDerivedFeature> derivedFeatures;

    private static final ObjectParser<ParsingState, Void> PARSER;

    static final ParseField NAME = new ParseField("name");
    static final ParseField FEATURES = new ParseField("features");
    static final ParseField DERIVED_FEATURES = new ParseField("derived_features");

    static {
        PARSER = new ObjectParser<>(TYPE, ParsingState::new);
        PARSER.declareString(ParsingState::setName, NAME);
        PARSER.declareObjectArray(ParsingState::setFeatures,
                (p, c) -> StoredFeature.parse(p),
                FEATURES);
        PARSER.declareObjectArray(ParsingState::setDerivedFeatures,
                (p, c) -> StoredDerivedFeature.parse(p),
                DERIVED_FEATURES);
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
            if (state.derivedFeatures == null) {
                state.derivedFeatures = Collections.emptyList();
            }
            return new StoredFeatureSet(state.name, state.features, state.derivedFeatures);
        } catch (IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage(), iae);
        }
    }

    public StoredFeatureSet(String name, List<StoredFeature> features, List<StoredDerivedFeature> derivedFeatures) {
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

        this.derivedFeatures = derivedFeatures;
        derivedFeatureMap = new HashMap<>();
        for (StoredDerivedFeature feature : derivedFeatures) {
            ordinal++;

            if (derivedFeatureMap.put(feature.name(), ordinal) != null) {
                throw new IllegalArgumentException("Derived Feature [" + feature.name() + "] defined twice in this set: " +
                        "feature names must be unique in a set.");
            }

            for(String var: feature.expression().variables) {
                // Exception will be triggered if an unknown variable is encountered
                feature(var);
            }
        }
    }

    public StoredFeatureSet(String name, List<StoredFeature> features) {
        this(name, features, Collections.emptyList());
    }

    public StoredFeatureSet(StreamInput input) throws IOException {
        this(input.readString(), input.readList(StoredFeature::new), input.readList(StoredDerivedFeature::new));
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeList(features);
        out.writeList(derivedFeatures);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(NAME.getPreferredName(), name);
        builder.startArray(FEATURES.getPreferredName());
        for (StoredFeature feature : features) {
            feature.toXContent(builder, params);
        }
        builder.endArray();

        builder.startArray(DERIVED_FEATURES.getPreferredName());
        for (StoredDerivedFeature derived: derivedFeatures) {
            derived.toXContent(builder, params);
        }
        builder.endArray();

        builder.endObject();
        return builder;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * Generates a new StoredFeatureSet by adding extra features.
     * The name is kept, features provided here are added at the end
     * of existing features.
     * @param features new features to append
     * @return a new StoredFeatureSet
     * @throws IllegalArgumentException if the resulting size of the set exceed MAX_FEATURES
     * or if uniqueness of feature names is not met.
     */
    public StoredFeatureSet append(List<StoredFeature> features) {
        int nFeature = this.features.size() + features.size();
        if (nFeature > MAX_FEATURES) {
            throw new IllegalArgumentException("The resulting feature set would be too large");
        }
        List<StoredFeature> newFeatures = new ArrayList<>(nFeature);
        newFeatures.addAll(this.features);
        newFeatures.addAll(features);
        return new StoredFeatureSet(name, newFeatures, derivedFeatures);
    }

    public StoredFeatureSet appendDerived(List<StoredDerivedFeature> derivedFeatures) {
        int nFeature = this.derivedFeatures.size() + derivedFeatures.size();

        if(nFeature > MAX_FEATURES) {
            throw new IllegalArgumentException("The resulting feature set would be too large");
        }
        List<StoredDerivedFeature> newDerived = new ArrayList<>(nFeature);
        newDerived.addAll(this.derivedFeatures);
        newDerived.addAll(derivedFeatures);
        return new StoredFeatureSet(name, features, newDerived);
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
    public List<StoredDerivedFeature> derivedFeatures() { return derivedFeatures; }

    @Override
    public int featureOrdinal(String featureName) {
        Integer ordinal = featureMap.get(featureName);

        // Check derived feature map if feature ordinal was null
        if(ordinal == null ) {
            ordinal = derivedFeatureMap.get(featureName);
        }

        // If still null, throw an exception
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
        return featureMap.containsKey(featureName) || derivedFeatureMap.containsKey(featureName);
    }

    @Override
    public int size() {
        return features.size() + derivedFeatures.size();
    }

    @Override
    public long ramBytesUsed() {
        return BASE_RAM_USED +
                derivedFeatureMap.size() * NUM_BYTES_OBJECT_REF + NUM_BYTES_ARRAY_HEADER + NUM_BYTES_ARRAY_HEADER +
                derivedFeatures.stream().mapToLong(StoredDerivedFeature::ramBytesUsed).sum() +
                featureMap.size() * NUM_BYTES_OBJECT_REF + NUM_BYTES_OBJECT_HEADER + NUM_BYTES_ARRAY_HEADER +
                features.stream().mapToLong(StoredFeature::ramBytesUsed).sum();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StoredFeatureSet)) return false;

        StoredFeatureSet that = (StoredFeatureSet) o;

        if (!name.equals(that.name)) return false;
        return features.equals(that.features);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + features.hashCode();
        return result;
    }

    private static class ParsingState {
        private String name;
        private List<StoredDerivedFeature> derivedFeatures;
        private List<StoredFeature> features;

        public void setName(String name) {
            this.name = name;
        }

        public void setFeatures(List<StoredFeature> features) {
            this.features = features;
        }

        public void setDerivedFeatures(List<StoredDerivedFeature> features) { this.derivedFeatures = features; }
    }
}
