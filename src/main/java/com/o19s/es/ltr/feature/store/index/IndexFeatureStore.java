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
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.MetadataCreateIndexService;
import org.elasticsearch.core.CheckedFunction;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.common.bytes.BytesReference;
import org.apache.logging.log4j.LogManager;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.xcontent.NamedXContentRegistry;
import org.elasticsearch.xcontent.ObjectParser;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.core.internal.io.Streams;

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
    private final Supplier<Client> clientSupplier;
    private final LtrRankerParserFactory parserFactory;

    public IndexFeatureStore(String index, Supplier<Client> clientSupplier, LtrRankerParserFactory factory) {
        this.index = Objects.requireNonNull(index);
        this.clientSupplier = Objects.requireNonNull(clientSupplier);
        this.parserFactory = Objects.requireNonNull(factory);
    }

    @Override
    public String getStoreName() {
        return index;
    }

    @Override
    public Feature load(String name) throws IOException {
        Feature feature = getAndParse(name, StoredFeature.class, StoredFeature.TYPE);
        if (feature == null) {
            throw new IllegalArgumentException("Unknown feature [" + name + "]");
        }
        return feature.optimize();
    }

    @Override
    public FeatureSet loadSet(String name) throws IOException {
        FeatureSet featureSet = getAndParse(name, StoredFeatureSet.class, StoredFeatureSet.TYPE);
        if (featureSet == null) {
            throw new IllegalArgumentException("Unknown featureset [" + name + "]");
        }
        return featureSet.optimize();
    }

    /**
     * Construct the elasticsearch index name based on a store name
     *
     * @param storeName the store name
     * @return the name of the elasticsearch index based on the given store name
     */
    public static String indexName(String storeName) {
        if (Objects.requireNonNull(storeName).isEmpty()) {
            throw new IllegalArgumentException("Store name cannot be empty");
        }
        return STORE_PREFIX + storeName;
    }

    /**
     * Infer the store name based on an elasticsearch index name
     * This function is only meant for user display, _default_ is returned in case indexName equals to DEFAULT_STORE.
     * @see IndexFeatureStore#isIndexStore(String)
     *
     * @param indexName the index name to infer the store name from
     * @return the store name inferred from the index name
     *
     * @throws IllegalArgumentException if indexName is not a valid index name,
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
     * Returns if this index name is a possible index store
     * The index must be {@link #DEFAULT_STORE} or starts with {@link #STORE_PREFIX}
     *
     * @param indexName the index name to check
     * @return true if this index name is a possible index store, false otherwise.
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
        return () -> clientSupplier.get().prepareGet(index, ES_TYPE, id).get();
    }

    /**
     * Generate the source doc ready to be indexed in the store
     *
     * @param elt the storable element to build the source document for
     * @return the source-doc to be indexed by the store
     * @throws IOException in case of failures
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
                .put(IndexMetadata.INDEX_NUMBER_OF_SHARDS_SETTING.getKey(), 1)
                .put(IndexMetadata.INDEX_AUTO_EXPAND_REPLICAS_SETTING.getKey(), "0-2")
                .put(STORE_VERSION_PROP.getKey(), VERSION)
                .put(IndexMetadata.SETTING_PRIORITY, Integer.MAX_VALUE)
                .put(IndexMetadata.SETTING_INDEX_HIDDEN, true)
                .put(Settings.builder()
                        .loadFromSource(readResourceFile(indexName, ANALYSIS_FILE), XContentType.JSON)
                        .build())
                .build();
    }

    /**
     * Validate the feature store name
     * Must not bear an ambiguous name such as feature/featureset/model and be a valid indexName
     *
     * @param storeName the store name to validate
     * @throws IllegalArgumentException if the name is invalid
     */
    public static void validateFeatureStoreName(String storeName) {
        if (INVALID_NAMES.matcher(storeName).matches()) {
            throw new IllegalArgumentException("A featurestore name cannot be based on the words [feature], [featureset] and [model]");
        }
        MetadataCreateIndexService.validateIndexOrAliasName(storeName,
                (name, error) -> new IllegalArgumentException("Invalid feature store name [" + name + "]: " + error));
    }
}
