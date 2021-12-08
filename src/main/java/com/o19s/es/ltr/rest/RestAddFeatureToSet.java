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

import com.o19s.es.ltr.action.AddFeaturesToSetAction.AddFeaturesToSetRequestBuilder;
import com.o19s.es.ltr.feature.FeatureValidation;
import com.o19s.es.ltr.feature.store.StoredFeature;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.ObjectParser;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestStatusToXContentListener;

import java.io.IOException;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

public class RestAddFeatureToSet extends FeatureStoreBaseRestHandler {

    @Override
    public String getName() {
        return "Add a feature to the set of features";
    }

    @Override
    public List<Route> routes() {
        return unmodifiableList(asList(
                new Route(RestRequest.Method.POST, "/_ltr/_featureset/{name}/_addfeatures/{query}"),
                new Route(RestRequest.Method.POST, "/_ltr/{store}/_featureset/{name}/_addfeatures/{query}"),
                new Route(RestRequest.Method.POST, "/_ltr/_featureset/{name}/_addfeatures"),
                new Route(RestRequest.Method.POST, "/_ltr/{store}/_featureset/{name}/_addfeatures")
        ));
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
        FeatureValidation validation = null;
        if (request.hasContentOrSourceParam()) {
            FeaturesParserState featuresParser = new FeaturesParserState();
            request.applyContentParser(featuresParser::parse);
            features = featuresParser.features;
            validation = featuresParser.validation;
        }
        if (featureQuery == null && (features == null || features.isEmpty())) {
            throw new IllegalArgumentException("features must be provided as a query for the feature store " +
                    "or in the body, none provided");
        }

        if (featureQuery != null && (features != null && !features.isEmpty())) {
            throw new IllegalArgumentException("features must be provided as a query for the feature store " +
                    "or directly in the body not both");
        }

        AddFeaturesToSetRequestBuilder builder = new AddFeaturesToSetRequestBuilder(client);
        builder.request().setStore(store);
        builder.request().setFeatureSet(setName);
        builder.request().setFeatureNameQuery(featureQuery);
        builder.request().setRouting(routing);
        builder.request().setFeatures(features);
        builder.request().setMerge(merge);
        builder.request().setValidation(validation);
        return (channel) -> builder.execute(new RestStatusToXContentListener<>(channel, (r) -> r.getResponse().getLocation(routing)));
    }

    static class FeaturesParserState {
        public static final ObjectParser<FeaturesParserState, Void> PARSER = new ObjectParser<>("features");
        private List<StoredFeature> features;
        private FeatureValidation validation;
        static {
            PARSER.declareObjectArray(
                    FeaturesParserState::setFeatures,
                    (parser, context) -> StoredFeature.parse(parser),
                    new ParseField("features"));
            PARSER.declareObject(
                    FeaturesParserState::setValidation,
                    FeatureValidation.PARSER::apply,
                    new ParseField("validation"));
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

        public FeatureValidation getValidation() {
            return validation;
        }

        public void setValidation(FeatureValidation validation) {
            this.validation = validation;
        }
    }
}
