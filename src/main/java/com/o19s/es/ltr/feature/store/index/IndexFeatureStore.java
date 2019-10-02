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

import com.o19s.es.ltr.feature.Feature;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.feature.store.CompiledLtrModel;
import com.o19s.es.ltr.feature.store.FeatureStore;
import com.o19s.es.ltr.feature.store.StorableElement;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.ranker.parser.LtrRankerParserFactory;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MetaDataCreateIndexService;
import org.elasticsearch.common.CheckedFunction;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.Streams;
import org.apache.logging.log4j.LogManager;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.o19s.es.ltr.feature.store.StorableElement.generateId;

public class IndexFeatureStore implements FeatureStore {
    public static final int VERSION = 2;
    public static final Setting<Integer> STORE_VERSION_PROP = Setting.intSetting("index.ltrstore_version",
            VERSION, -1, Integer.MAX_VALUE, Setting.Property.IndexScope);
    public static final String DEFAULT_STORE = ".ltrstore";
    public static final String STORE_PREFIX = DEFAULT_STORE + "_";
    private static final String MAPPING_FILE = "fstore-index-mapping.json";
    private static final String ANALYSIS_FILE = "fstore-index-analysis.json";

    public static final Logger LOGGER = LogManager.getLogger(IndexFeatureStore.class);

    public static final String ES_TYPE = "store";

    /**
     * List of invalid for a feature store name:
     * feature, features, featureSet, featureSets, feature_Set, feature_Sets,
     * featureset, featuresets, feature_set, feature_sets, model, models
     */
    private static final Pattern INVALID_NAMES = Pattern.compile("^(features?[*]?|feature_[sS]ets?|models?)$");

    private static final ObjectParser<ParserState, Void> SOURCE_PARSER;
    static {
        SOURCE_PARSER = new ObjectParser<>("", true, ParserState::new);
        SOURCE_PARSER.declareField(ParserState::setElement,
                (CheckedFunction<XContentParser, StoredFeature, IOException>) StoredFeature::parse,
                new ParseField(StoredFeature.TYPE), ObjectParser.ValueType.OBJECT);
        SOURCE_PARSER.declareField(ParserState::setElement,
                (CheckedFunction<XContentParser, StoredFeatureSet, IOException>) StoredFeatureSet::parse,
                new ParseField(StoredFeatureSet.TYPE), ObjectParser.ValueType.OBJECT);
        SOURCE_PARSER.declareField(ParserState::setElement,
                (CheckedFunction<XContentParser, StoredLtrModel, IOException>) StoredLtrModel::parse,
                new ParseField(StoredLtrModel.TYPE), ObjectParser.ValueType.OBJECT);
    }

    private final String index;
    private final Client client;
    private final LtrRankerParserFactory parserFactory;

    public IndexFeatureStore(String index, Client client, LtrRankerParserFactory factory) {
        this.index = Objects.requireNonNull(index);
        this.client = Objects.requireNonNull(client);
        this.parserFactory = Objects.requireNonNull(factory);
    }

    @Override
    public String getStoreName() {
        return index;
    }

    @Override
    public Feature load(String name) throws IOException {
        return getAndParse(name, StoredFeature.class, StoredFeature.TYPE).optimize();
    }

    @Override
    public FeatureSet loadSet(String name) throws IOException {
        return getAndParse(name, StoredFeatureSet.class, StoredFeatureSet.TYPE).optimize();
    }

    /**
     * Construct the elasticsearch index name based on a store name
     */
    public static String indexName(String storeName) {
        if (Objects.requireNonNull(storeName).isEmpty()) {
            throw new IllegalArgumentException("Store name cannot be empty");
        }
        return STORE_PREFIX + storeName;
    }

    /**
     * Infer the store name based on an elasticsearch index name
     * This function is only meant for user display, _default_ is returned
     * in case indexName equals to DEFAULT_STORE.
     *
     * @throws IllegalArgumentException if indexName is not a valid
     * index name.
     * @see IndexFeatureStore#isIndexStore(String)
     */
    public static String storeName(String indexName) {
        if (!isIndexStore(indexName)) {
            throw new IllegalArgumentException("[" + indexName + "] is not a valid index name for a feature store");
        }
        if (DEFAULT_STORE.equals(indexName)) {
            return "_default_";
        }
        assert indexName.length() > STORE_PREFIX.length();
        return indexName.substring(STORE_PREFIX.length());
    }

    /**
     * Returns true if this index name is a possible index store
     * false otherwise.
     * The index must be {@link #DEFAULT_STORE} or starts with {@link #STORE_PREFIX}
     */
    public static boolean isIndexStore(String indexName) {
        return Objects.requireNonNull(indexName).equals(DEFAULT_STORE) ||
                (indexName.startsWith(STORE_PREFIX) && indexName.length() > STORE_PREFIX.length());
    }

    @Override
    public CompiledLtrModel loadModel(String name) throws IOException {
        StoredLtrModel model = getAndParse(name, StoredLtrModel.class, StoredLtrModel.TYPE);
        if (model == null) {
            throw new IllegalArgumentException("Unknown model [" + name + "]");
        }
        return model.compile(parserFactory);
    }

    public <E extends StorableElement> E getAndParse(String name, Class<E> eltClass, String type) throws IOException {
        GetResponse response = internalGet(generateId(type, name)).get();
        if (response.isExists()) {
            return parse(eltClass, type, response.getSourceAsBytes());
        } else {
            return null;
        }
    }

    public GetResponse getFeature(String name) {
        return internalGet(generateId(StoredFeature.TYPE, name)).get();
    }

    public GetResponse getFeatureSet(String name) {
        return internalGet(generateId(StoredFeatureSet.TYPE, name)).get();
    }

    public GetResponse getModel(String name) {
        return internalGet(generateId(StoredLtrModel.TYPE, name)).get();
    }

    private Supplier<GetResponse> internalGet(String id) {
        return () -> client.prepareGet(index, ES_TYPE, id).get();
    }

    /**
     * Generate the source doc ready to be indexed in the store
     */
    public static XContentBuilder toSource(StorableElement elt) throws IOException {
        XContentBuilder source = XContentFactory.contentBuilder(Requests.INDEX_CONTENT_TYPE);
        source.startObject();
        source.field("name", elt.name());
        source.field("type", elt.type());
        source.field(elt.type(), elt);
        source.endObject();
        return source;
    }

    public static <E extends StorableElement> E parse(Class<E> eltClass, String type, byte[] bytes) throws IOException {
        return parse(eltClass, type, bytes, 0, bytes.length);
    }

    public static <E extends StorableElement> E parse(Class<E> eltClass, String type, byte[] bytes,
                                                      int offset, int length) throws IOException {
        try (XContentParser parser = XContentFactory.xContent(bytes)
                .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, bytes)) {
            return parse(eltClass, type, parser);
        }
    }

    public static <E extends StorableElement> E parse(Class<E> eltClass, String type, BytesReference bytesReference) throws IOException {
        BytesRef ref = bytesReference.toBytesRef();
        try (XContentParser parser = XContentFactory.xContent(ref.bytes, ref.offset, ref.length)
                .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE,
                        ref.bytes, ref.offset, ref.length)
        ) {
            return parse(eltClass, type, parser);
        }
    }

    public static <E extends StorableElement> E parse(Class<E> eltClass, String type, XContentParser parser) throws IOException {
        StorableElement elt = SOURCE_PARSER.parse(parser, null).element;
        if (elt == null) {
            throw new IllegalArgumentException("No StorableElement found.");
        }
        if (!elt.type().equals(type)) {
            throw new IllegalArgumentException("Expected an element of type [" + type + "] but got [" + elt.type() + "].");
        }
        if (!eltClass.isAssignableFrom(elt.getClass())) {
            throw new RuntimeException("Cannot cast " + elt.getClass() + " to " + eltClass + " ( requested type [" + type + "])");
        }
        return eltClass.cast(elt);
    }

    private static class ParserState {
        StorableElement element;

        void setElement(StorableElement element) {
            if (this.element != null) {
                throw new IllegalArgumentException("Element already set");
            }
            this.element = element;
        }
    }

    public static CreateIndexRequest buildIndexRequest(String indexName) {
        return new CreateIndexRequest(indexName)
                .mapping(ES_TYPE, readResourceFile(indexName, MAPPING_FILE), XContentType.JSON)
                .settings(storeIndexSettings(indexName));
    }

    private static String readResourceFile(String indexName, String resource) {
        try (InputStream is = IndexFeatureStore.class.getResourceAsStream(resource)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Streams.copy(is, out);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            LOGGER.error(
                    (org.apache.logging.log4j.util.Supplier<?>) () -> new ParameterizedMessage(
                            "failed to create ltr feature store index [{}] with resource [{}]",
                            indexName, resource), e);
            throw new IllegalStateException("failed to create ltr feature store index with resource [" + resource + "]", e);
        }
    }

    private static Settings storeIndexSettings(String indexName) {
        return Settings.builder()
                .put(IndexMetaData.INDEX_NUMBER_OF_SHARDS_SETTING.getKey(), 1)
                .put(IndexMetaData.INDEX_AUTO_EXPAND_REPLICAS_SETTING.getKey(), "0-2")
                .put(STORE_VERSION_PROP.getKey(), VERSION)
                .put(IndexMetaData.SETTING_PRIORITY, Integer.MAX_VALUE)
                .put(Settings.builder()
                        .loadFromSource(readResourceFile(indexName, ANALYSIS_FILE), XContentType.JSON)
                        .build())
                .build();
    }

    /**
     * Validate the feature store name.
     * Must not bear an ambiguous name such as feature/featureset/model
     * and be a valid indexName.
     *
     * @throws IllegalArgumentException if the name is invalid
     */
    public static void validateFeatureStoreName(String storeName) {
        if (INVALID_NAMES.matcher(storeName).matches()) {
            throw new IllegalArgumentException("A featurestore name cannot be based on the words [feature], [featureset] and [model]");
        }
        MetaDataCreateIndexService.validateIndexOrAliasName(storeName,
                (name, error) -> new IllegalArgumentException("Invalid feature store name [" + name + "]: " + error));
    }
}
