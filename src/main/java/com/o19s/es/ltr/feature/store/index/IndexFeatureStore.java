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

import com.o19s.es.ltr.feature.store.FeatureStore;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.ranker.parser.LtrRankerParserFactory;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public class IndexFeatureStore implements FeatureStore {
    private static final String FEATURE_TYPE = "features";
    private static final String FEATURE_SET_TYPE = "featureset";
    private static final String MODEL_TYPE = "model";

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
    public StoredFeature load(String id) throws IOException {
        return getAndParse(FEATURE_TYPE, id, StoredFeature::parse);
    }

    @Override
    public StoredFeatureSet loadSet(String id) throws IOException {
        return getAndParse(id, FEATURE_SET_TYPE, StoredFeatureSet::parse);
    }

    @Override
    public StoredLtrModel loadModel(String id) throws IOException {
        return getAndParse(id, FEATURE_SET_TYPE, (parser) -> StoredLtrModel.parse(parser, parserFactory));
    }

    private <T> T getAndParse(String type, String id, Function<XContentParser, T> parser) throws IOException {
        return parser.apply(json(internalGet(type, id).get().getSourceAsBytes()));
    }

    public GetResponse getFeature(String id) {
        return internalGet(id, FEATURE_TYPE).get();
    }

    public GetResponse getFeatureSet(String id) {
        return internalGet(id, FEATURE_SET_TYPE).get();
    }

    public GetResponse getModel(String id) {
        return internalGet(id, MODEL_TYPE).get();
    }

    private Supplier<GetResponse> internalGet(String type, String id) {
        return () -> client.prepareGet(index, type, id).get();
    }

    private XContentParser json(byte[] json) throws IOException {
        // No need to have a full featured {@link NamedXContentRegistry}, we use
        // {@link org.elasticsearch.common.xcontent.XContentBuilder#copyCurrentStructure(org.elasticsearch.common.xcontent.XContentParser)}
        // which simply copy the content structure without parsing it
        return XContentFactory.xContent(json).createParser(NamedXContentRegistry.EMPTY, json);
    }
}
