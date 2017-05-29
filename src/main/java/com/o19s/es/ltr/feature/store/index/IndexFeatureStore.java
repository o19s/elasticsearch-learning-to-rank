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

import com.o19s.es.ltr.feature.store.CompiledLtrModel;
import com.o19s.es.ltr.feature.store.FeatureStore;
import com.o19s.es.ltr.feature.store.StorableElement;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.ranker.parser.LtrRankerParserFactory;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.lucene.util.IOUtils;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.function.Supplier;

import static com.o19s.es.ltr.feature.store.StorableElement.generateId;

public class IndexFeatureStore implements FeatureStore {
    public static final String DEFAULT_STORE = ".ltrstore";
    public static final String STORE_PREFIX = DEFAULT_STORE + "_";
    private static final String MAPPING_FILE = "fstore-index-mapping.json";
    private static final String ANALYSIS_FILE = "fstore-index-analysis.json";

    public static final Logger LOGGER = ESLoggerFactory.getLogger(IndexFeatureStore.class);

    public static final String ES_TYPE = "store";

    private static final ObjectParser<ParserState, Void> SOURCE_PARSER;
    static {
        SOURCE_PARSER = new ObjectParser<>("", true, ParserState::new);
        SOURCE_PARSER.declareField(ParserState::setElement, StoredFeature::parse,
                new ParseField(StoredFeature.TYPE), ObjectParser.ValueType.OBJECT);
        SOURCE_PARSER.declareField(ParserState::setElement, StoredFeatureSet::parse,
                new ParseField(StoredFeatureSet.TYPE), ObjectParser.ValueType.OBJECT);
        SOURCE_PARSER.declareField(ParserState::setElement, StoredLtrModel::parse,
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
    public StoredFeature load(String name) throws IOException {
        return getAndParse(name, StoredFeature.class, StoredFeature.TYPE);
    }

    @Override
    public StoredFeatureSet loadSet(String name) throws IOException {
        return getAndParse(name, StoredFeatureSet.class, StoredFeatureSet.TYPE);
    }

    @Override
    public CompiledLtrModel loadModel(String name) throws IOException {
        StoredLtrModel model = getAndParse(name, StoredLtrModel.class, StoredLtrModel.TYPE);
        if (model == null) {
            throw new IllegalArgumentException("Unkown model [" + name + "]");
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
        try (XContentParser parser = XContentFactory.xContent(bytes)
                .createParser(NamedXContentRegistry.EMPTY, bytes)) {
            return parse(eltClass, type, parser);
        }
    }

    public static <E extends StorableElement> E parse(Class<E> eltClass, String type, BytesReference bytes) throws IOException {
        try (XContentParser parser = XContentFactory.xContent(bytes)
                .createParser(NamedXContentRegistry.EMPTY, bytes)) {
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
            return out.toString(IOUtils.UTF_8);
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
                .put(IndexMetaData.INDEX_AUTO_EXPAND_REPLICAS_SETTING.getKey(), "0-1")
                .put(IndexMetaData.SETTING_PRIORITY, Integer.MAX_VALUE)
                .put(Settings.builder()
                        .loadFromSource(readResourceFile(indexName, ANALYSIS_FILE), XContentType.JSON)
                        .build())
                .build();
    }
}
