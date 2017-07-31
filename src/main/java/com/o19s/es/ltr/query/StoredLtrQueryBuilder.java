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

package com.o19s.es.ltr.query;

import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.feature.store.CompiledLtrModel;
import com.o19s.es.ltr.feature.store.FeatureStore;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import com.o19s.es.ltr.ranker.linear.LinearRanker;
import com.o19s.es.ltr.utils.FeatureStoreLoader;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.NamedWriteable;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryShardContext;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * sltr query, build a ltr query based on a stored model.
 */
public class StoredLtrQueryBuilder extends AbstractQueryBuilder<StoredLtrQueryBuilder> implements NamedWriteable {
    public static final String NAME = "sltr";
    public static final ParseField MODEL_NAME = new ParseField("model");
    public static final ParseField FEATURESET_NAME = new ParseField("featureset");
    public static final ParseField STORE_NAME = new ParseField("store");
    public static final ParseField PARAMS = new ParseField("params");

    /**
     * Injected context used to load a {@link FeatureStore} when running {@link #doToQuery(QueryShardContext)}
     */
    private final transient FeatureStoreLoader storeLoader;
    private String modelName;
    private String featureSetName;
    private String storeName;
    private Map<String, Object> params;

    private static final ObjectParser<StoredLtrQueryBuilder, Void> PARSER;

    static {
        PARSER = new ObjectParser<>(NAME);
        PARSER.declareString(StoredLtrQueryBuilder::modelName, MODEL_NAME);
        PARSER.declareString(StoredLtrQueryBuilder::featureSetName, FEATURESET_NAME);
        PARSER.declareString(StoredLtrQueryBuilder::storeName, STORE_NAME);
        PARSER.declareField(StoredLtrQueryBuilder::params, XContentParser::map,
                PARAMS, ObjectParser.ValueType.OBJECT);
        declareStandardFields(PARSER);
    }

    public StoredLtrQueryBuilder(FeatureStoreLoader storeLoader) {
        this.storeLoader = storeLoader;
    }

    public StoredLtrQueryBuilder(FeatureStoreLoader storeLoader, StreamInput input) throws IOException {
        super(input);
        this.storeLoader = Objects.requireNonNull(storeLoader);
        modelName = input.readOptionalString();
        featureSetName = input.readOptionalString();
        params = input.readMap();
        storeName = input.readOptionalString();
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeOptionalString(modelName);
        out.writeOptionalString(featureSetName);
        out.writeMap(params);
        out.writeOptionalString(storeName);
    }

    public static Optional<StoredLtrQueryBuilder> fromXContent(FeatureStoreLoader storeLoader,
                                                               QueryParseContext context) throws IOException {
        storeLoader = Objects.requireNonNull(storeLoader);
        XContentParser parser = context.parser();
        final StoredLtrQueryBuilder builder =  new StoredLtrQueryBuilder(storeLoader);
        try {
            PARSER.parse(context.parser(), builder, null);
        } catch (IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage(), iae);
        }
        if (builder.modelName() == null && builder.featureSetName() == null) {
            throw new ParsingException(parser.getTokenLocation(), "Either [" + MODEL_NAME + "] or [" + FEATURESET_NAME + "] must be set.");
        }
        if (builder.params() == null) {
            throw new ParsingException(parser.getTokenLocation(), "Field [" + PARAMS + "] is mandatory.");
        }
        return Optional.of(builder);
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params p) throws IOException {
        builder.startObject(NAME);
        if (modelName != null) {
            builder.field(MODEL_NAME.getPreferredName(), modelName);
        }
        if (featureSetName != null) {
            builder.field(FEATURESET_NAME.getPreferredName(), featureSetName);
        }
        if (storeName != null) {
            builder.field(STORE_NAME.getPreferredName(), storeName);
        }
        if (this.params != null && !this.params.isEmpty()) {
            builder.field(PARAMS.getPreferredName(), this.params);
        }
        printBoostAndQueryName(builder);
        builder.endObject();
    }

    @Override
    protected RankerQuery doToQuery(QueryShardContext context) throws IOException {
        String indexName = storeName != null ? IndexFeatureStore.indexName(storeName) : IndexFeatureStore.DEFAULT_STORE;
        FeatureStore store = storeLoader.load(indexName, context.getClient());
        if (modelName != null) {
            CompiledLtrModel model = store.loadModel(modelName);
            return RankerQuery.build(model, context, params);
        } else {
            assert featureSetName != null;
            FeatureSet set = store.loadSet(featureSetName);
            float[] weitghs = new float[set.size()];
            Arrays.fill(weitghs, 1F);
            LinearRanker ranker = new LinearRanker(weitghs);
            CompiledLtrModel model = new CompiledLtrModel("linear", set, ranker);
            return RankerQuery.build(model, context, params);
        }
    }

    @Override
    protected boolean doEquals(StoredLtrQueryBuilder other) {
        return Objects.equals(modelName, other.modelName) &&
                Objects.equals(featureSetName, other.featureSetName) &&
                Objects.equals(storeName, other.storeName) &&
                Objects.equals(params, other.params);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(modelName, featureSetName, storeName, params);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    public String modelName() {
        return modelName;
    }

    public StoredLtrQueryBuilder modelName(String modelName) {
        this.modelName = Objects.requireNonNull(modelName);
        return this;
    }

    public String featureSetName() {
        return featureSetName;
    }

    public StoredLtrQueryBuilder featureSetName(String featureSetName) {
        this.featureSetName = featureSetName;
        return this;
    }

    public String storeName() {
        return storeName;
    }

    public StoredLtrQueryBuilder storeName(String storeName) {
        this.storeName = storeName;
        return this;
    }

    public Map<String, Object> params() {
        return params;
    }

    public StoredLtrQueryBuilder params(Map<String, Object> params) {
        this.params = Objects.requireNonNull(params);
        return this;
    }
}
