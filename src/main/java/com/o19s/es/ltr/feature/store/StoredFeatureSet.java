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
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final Map<String, Integer> featureMap;
    private final List<StoredFeature> features;

    private static final ObjectParser<ParsingState, Void> PARSER;

    static final ParseField NAME = new ParseField("name");
    static final ParseField FEATURES = new ParseField("features");

    static {
        PARSER = new ObjectParser<>(TYPE, ParsingState::new);
        PARSER.declareString(ParsingState::setName, NAME);
        PARSER.declareObjectArray(ParsingState::setFeatures,
                (p, c) -> StoredFeature.parse(p),
                FEATURES);
    }

    public static StoredFeatureSet parse(XContentParser parser) {
        return parse(parser, null);
    }

    public static StoredFeatureSet parse(XContentParser parser, String name) {
        try {
            ParsingState state = PARSER.apply(parser, null);
            state.resolveName(parser, name);
            if (state.features == null) {
                state.features = Collections.emptyList();
            }
            return new StoredFeatureSet(state.getName(), state.features);
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
    public FeatureSet optimize() {
        List<Feature> optimizedFeatures = new ArrayList<>(this.features.size());
        boolean optimized = false;
        for (StoredFeature feature : this.features) {
            Feature optimizedFeature = feature.optimize();
            optimized |= optimizedFeature != feature;
            optimizedFeatures.add(optimizedFeature);
        }
        if (optimized) {
            return new OptimizedFeatureSet(this.name, optimizedFeatures, Collections.unmodifiableMap(featureMap));
        }
        return this;
    }

    @Override
    public void validate() {
        for (StoredFeature feature : features) {
            feature.validate(this);
        }
    }

    public StoredFeatureSet(StreamInput input) throws IOException {
        this(input.readString(), input.readList(StoredFeature::new));
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeList(features);
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
     *
     * @param features new features to append
     * @return a new StoredFeatureSet
     * @throws IllegalArgumentException if the resulting size of the set exceed MAX_FEATURES
     *                                  or if uniqueness of feature names is not met.
     */
    public StoredFeatureSet append(List<StoredFeature> features) {
        int nFeature = features.size() + this.features.size();
        if (nFeature > MAX_FEATURES) {
            throw new IllegalArgumentException("The resulting feature set would be too large");
        }
        List<StoredFeature> newFeatures = new ArrayList<>(nFeature);
        newFeatures.addAll(this.features);
        newFeatures.addAll(features);
        return new StoredFeatureSet(name, newFeatures);
    }

    /**
     * Generate a new set by merging features with this set, feature with same names are updated new features are appended.
     * Ordinals of existing features are kept.
     *
     * @param mergedFeatures the list of features to merge
     * @return the new set
     * @throws IllegalArgumentException if the resulting size of the set exceed MAX_FEATURES
     */
    public StoredFeatureSet merge(List<StoredFeature> mergedFeatures) {
        int merged = (int) mergedFeatures.stream()
                .map(StoredFeature::name)
                .filter(this::hasFeature)
                .count();

        if (size() + (mergedFeatures.size() - merged) > MAX_FEATURES) {
            throw new IllegalArgumentException("The resulting feature set would be too large");
        }
        List<StoredFeature> newFeatures = new ArrayList<>(this.features.size() + mergedFeatures.size() - merged);
        newFeatures.addAll(this.features);
        for (StoredFeature f : mergedFeatures) {
            if (hasFeature(f.name())) {
                newFeatures.set(featureOrdinal(f.name()), f);
            } else {
                newFeatures.add(f);
            }
        }
        assert newFeatures.size() <= MAX_FEATURES;
        return new StoredFeatureSet(name, newFeatures);
    }

    @Override
    public List<Query> toQueries(LtrQueryContext context, Map<String, Object> params) {
        List<Query> queries = new ArrayList<>(features.size());
        for (Feature feature : features) {
            if (context.isFeatureActive(feature.name())) {
                queries.add(feature.doToQuery(context, this, params));
            } else {
                queries.add(new MatchNoDocsQuery("Feature " + feature.name() + " deactivated"));
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StoredFeatureSet)) {
            return false;
        }

        StoredFeatureSet that = (StoredFeatureSet) o;

        if (!name.equals(that.name)) {
            return false;
        }
        return features.equals(that.features);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + features.hashCode();
        return result;
    }

    private static class ParsingState extends StorableElementParserState {
        private List<StoredFeature> features;

        public void setFeatures(List<StoredFeature> features) {
            this.features = features;
        }
    }
}
