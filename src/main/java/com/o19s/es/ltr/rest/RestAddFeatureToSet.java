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

package com.o19s.es.ltr.rest;

import com.o19s.es.ltr.action.AddFeaturesToSetAction;
import com.o19s.es.ltr.action.AddFeaturesToSetAction.AddFeaturesToSetRequestBuilder;
import com.o19s.es.ltr.feature.store.StoredFeature;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestStatusToXContentListener;

import java.io.IOException;
import java.util.List;

public class RestAddFeatureToSet extends FeatureStoreBaseRestHandler {

    public RestAddFeatureToSet(Settings settings, RestController controller) {
        super(settings);
        controller.registerHandler(RestRequest.Method.POST, "/_ltr/_featureset/{name}/_addfeatures/{query}", this);
        controller.registerHandler(RestRequest.Method.POST, "/_ltr/{store}/_featureset/{name}/_addfeatures/{query}", this);
        controller.registerHandler(RestRequest.Method.POST, "/_ltr/_featureset/{name}/_addfeatures", this);
        controller.registerHandler(RestRequest.Method.POST, "/_ltr/{store}/_featureset/{name}/_addfeatures", this);
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        String store = indexName(request);
        String setName = request.param("name");
        String routing = request.param("routing");
        String featureQuery = null;
        List<StoredFeature> features = null;
        boolean merge = request.paramAsBoolean("merge", false);
        if (request.hasParam("query")) {
            featureQuery = request.param("query");
        }
        if (request.hasContentOrSourceParam()) {
            if (featureQuery != null) {
                throw new IllegalArgumentException("features must be provided as a query for the feature store " +
                        "or directly in the body not both");
            }
            FeaturesParserState featuresParser = new FeaturesParserState();
            request.applyContentParser(featuresParser::parse);
            features = featuresParser.features;
        } else if (featureQuery == null) {
            throw new IllegalArgumentException("features must be provided as a query for the feature store " +
                    "or in the body, none provided");
        }

        AddFeaturesToSetRequestBuilder builder = AddFeaturesToSetAction.INSTANCE.newRequestBuilder(client);
        builder.request().setStore(store);
        builder.request().setFeatureSet(setName);
        builder.request().setFeatureNameQuery(featureQuery);
        builder.request().setRouting(routing);
        builder.request().setFeatures(features);
        builder.request().setMerge(merge);
        return (channel) -> builder.execute(new RestStatusToXContentListener<>(channel, (r) -> r.getResponse().getLocation(routing)));
    }

    static class FeaturesParserState {
        public static final ObjectParser<FeaturesParserState, Void> PARSER = new ObjectParser<>("features");
        private List<StoredFeature> features;
        static {
            PARSER.declareObjectArray(
                    FeaturesParserState::setFeatures,
                    (parser, context) -> StoredFeature.parse(parser),
                    new ParseField("features"));
        }

        public void parse(XContentParser parser) throws IOException {
            PARSER.parse(parser, this, null);
        }

        List<StoredFeature> getFeatures() {
            return features;
        }

        public void setFeatures(List<StoredFeature> features) {
            this.features = features;
        }
    }
}
