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

import com.o19s.es.ltr.action.ClearCachesAction;
import com.o19s.es.ltr.action.FeatureStoreAction;
import com.o19s.es.ltr.action.ListStoresAction;
import com.o19s.es.ltr.feature.FeatureValidation;
import com.o19s.es.ltr.feature.store.StorableElement;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.action.RestBuilderListener;
import org.elasticsearch.rest.action.RestStatusToXContentListener;
import org.elasticsearch.rest.action.RestToXContentListener;

import java.io.IOException;
import java.util.List;

import static com.o19s.es.ltr.feature.store.StorableElement.generateId;
import static com.o19s.es.ltr.feature.store.index.IndexFeatureStore.ES_TYPE;
import static com.o19s.es.ltr.query.ValidatingLtrQueryBuilder.SUPPORTED_TYPES;
import static java.util.stream.Collectors.joining;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.rest.RestStatus.NOT_FOUND;
import static org.elasticsearch.rest.RestStatus.OK;

/**
 * Simple CRUD operation for the feature store elements.
 */
public abstract class RestSimpleFeatureStore extends FeatureStoreBaseRestHandler {
    private RestSimpleFeatureStore(Settings settings) {
        super(settings);
    }

    public static void register(List<RestHandler> list, Settings settings, RestController restController) {
        for (String type : SUPPORTED_TYPES) {
            list.add(new RestAddOrUpdateFeature(settings, restController, type));
            list.add(new RestSearchStoreElements(settings, restController, type));
        }
        list.add(new RestStoreManager(settings, restController));
    }

    static class RestAddOrUpdateFeature extends RestSimpleFeatureStore {
        private final String type;
        RestAddOrUpdateFeature(Settings settings, RestController controller, String type) {
            super(settings);
            this.type = type;
            controller.registerHandler(RestRequest.Method.PUT, "/_ltr/{store}/_" + type + "/{name}", this);
            controller.registerHandler(RestRequest.Method.PUT, "/_ltr/_" + type + "/{name}", this);
            controller.registerHandler(RestRequest.Method.POST, "/_ltr/{store}/_" + type + "/{name}", this);
            controller.registerHandler(RestRequest.Method.POST, "/_ltr/_" + type + "/{name}", this);
            controller.registerHandler(RestRequest.Method.DELETE, "/_ltr/{store}/_" + type + "/{name}", this);
            controller.registerHandler(RestRequest.Method.DELETE, "/_ltr/_" + type + "/{name}", this);
            controller.registerHandler(RestRequest.Method.GET, "/_ltr/{store}/_" + type + "/{name}", this);
            controller.registerHandler(RestRequest.Method.GET, "/_ltr/_" + type + "/{name}", this);
            controller.registerHandler(RestRequest.Method.HEAD, "/_ltr/{store}/_" + type + "/{name}", this);
            controller.registerHandler(RestRequest.Method.HEAD, "/_ltr/_" + type + "/{name}", this);
        }

        @Override
        public String getName() {
            return "Add or update a feature";
        }

        @Override
        protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
            String indexName = indexName(request);
            if (request.method() == RestRequest.Method.DELETE) {
                return delete(client, type, indexName, request);
            } else if (request.method() == RestRequest.Method.HEAD || request.method() == RestRequest.Method.GET) {
                return get(client, type, indexName, request);
            } else {
                return addOrUpdate(client, type, indexName, request);
            }
        }
    }

    static class RestSearchStoreElements extends RestSimpleFeatureStore {
        private final String type;

        RestSearchStoreElements(Settings settings, RestController controller, String type) {
            super(settings);
            this.type = type;
            controller.registerHandler(RestRequest.Method.GET, "/_ltr/{store}/_" + type, this);
            controller.registerHandler(RestRequest.Method.GET, "/_ltr/_" + type, this);
        }

        @Override
        public String getName() {
            return "Obtain ltr store";
        }

        @Override
        protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
            return search(client, type, indexName(request), request);
        }
    }

    static class RestStoreManager extends RestSimpleFeatureStore {
        RestStoreManager(Settings settings, RestController controller) {
            super(settings);
            controller.registerHandler(RestRequest.Method.PUT, "/_ltr/{store}", this);
            controller.registerHandler(RestRequest.Method.PUT, "/_ltr", this);
            controller.registerHandler(RestRequest.Method.POST, "/_ltr/{store}", this);
            controller.registerHandler(RestRequest.Method.POST, "/_ltr", this);
            controller.registerHandler(RestRequest.Method.DELETE, "/_ltr/{store}", this);
            controller.registerHandler(RestRequest.Method.DELETE, "/_ltr", this);
            controller.registerHandler(RestRequest.Method.GET, "/_ltr", this);
            controller.registerHandler(RestRequest.Method.GET, "/_ltr/{store}", this);
        }

        @Override
        public String getName() {
            return "Manage the ltr store";
        }

        /**
         * Prepare the request for execution. Implementations should consume all request params before
         * returning the runnable for actual execution. Unconsumed params will immediately terminate
         * execution of the request. However, some params are only used in processing the response;
         * implementations can override {@link BaseRestHandler#responseParams()} to indicate such
         * params.
         *
         * @param request the request to execute
         * @param client  client for executing actions on the local node
         * @return the action to execute
         * @throws IOException if an I/O exception occurred parsing the request and preparing for
         *                     execution
         */
        @Override
        protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
            String indexName = indexName(request);
            if (request.method() == RestRequest.Method.PUT) {
                if (request.hasParam("store")) {
                    IndexFeatureStore.validateFeatureStoreName(request.param("store"));
                }
                return createIndex(client, indexName);
            } else if (request.method() == RestRequest.Method.POST ) {
                if (request.hasParam("store")) {
                    IndexFeatureStore.validateFeatureStoreName(request.param("store"));
                }
                throw new IllegalArgumentException("Updating a feature store is not yet supported.");
            } else if (request.method() == RestRequest.Method.DELETE) {
                return deleteIndex(client, indexName);
            } else {
                assert request.method() == RestRequest.Method.GET;
                // XXX: ambiguous api
                if (request.hasParam("store")) {
                    return getStore(client, indexName);
                }
                return listStores(client);
            }
        }
    }

    RestChannelConsumer getStore(NodeClient client, String indexName) {
        return (channel) -> client.admin().indices().prepareExists(indexName)
                .execute(new RestBuilderListener<IndicesExistsResponse>(channel) {
                    @Override
                    public RestResponse buildResponse(IndicesExistsResponse indicesExistsResponse,
                                                      XContentBuilder builder) throws Exception {
                        builder.startObject()
                                .field("exists", indicesExistsResponse.isExists())
                                .endObject()
                                .close();
                        return new BytesRestResponse(indicesExistsResponse.isExists() ? RestStatus.OK : RestStatus.NOT_FOUND,
                                builder);
                    }
                });
    }

    RestChannelConsumer listStores(NodeClient client) {
        return (channel) -> new ListStoresAction.ListStoresActionBuilder(client).execute(
                new RestToXContentListener<>(channel));
    }

    RestChannelConsumer createIndex(NodeClient client, String indexName) {
        return (channel) -> client.admin().indices()
                .create(IndexFeatureStore.buildIndexRequest(indexName), new RestToXContentListener<>(channel));
    }

    RestChannelConsumer deleteIndex(NodeClient client, String indexName) {
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(indexName);
        return (channel) -> client.admin().indices().delete(deleteIndexRequest, new RestToXContentListener<>(channel));
    }

    RestChannelConsumer addOrUpdate(NodeClient client, String type, String indexName, RestRequest request) throws IOException {
        assert SUPPORTED_TYPES.contains(type);
        String routing = request.param("routing");
        if (!request.hasContentOrSourceParam()) {
            throw new IllegalArgumentException("Missing content or source param.");
        }
        String name = request.param("name");
        AutoDetectParser parserState = new AutoDetectParser(name);
        request.applyContentParser(parserState::parse);
        StorableElement elt = parserState.element;
        if (!type.equals(elt.type())) {
            throw new IllegalArgumentException("Excepted a [" + type + "] but encountered [" + elt.type() + "]");
        }

        // Validation happens here when parsing the stored element.
        if (!elt.name().equals(name)) {
            throw new IllegalArgumentException("Name mismatch, send request with [" + elt.name() + "] but [" + name + "] used in the URL");
        }
        if (request.method() == RestRequest.Method.POST && !elt.updatable()) {
            try {
                throw new IllegalArgumentException("Element of type [" + elt.type() + "] are not updatable, " +
                        "please create a new one instead.");
            } catch (IllegalArgumentException iae) {
                return (channel) -> channel.sendResponse(new BytesRestResponse(channel, RestStatus.METHOD_NOT_ALLOWED, iae));
            }
        }
        FeatureStoreAction.FeatureStoreRequestBuilder builder = new FeatureStoreAction.FeatureStoreRequestBuilder(
            client, FeatureStoreAction.INSTANCE);
        if (request.method() == RestRequest.Method.PUT) {
            builder.request().setAction(FeatureStoreAction.FeatureStoreRequest.Action.CREATE);
        } else {
            builder.request().setAction(FeatureStoreAction.FeatureStoreRequest.Action.UPDATE);
        }
        builder.request().setStorableElement(elt);
        builder.request().setRouting(routing);
        builder.request().setStore(indexName);
        builder.request().setValidation(parserState.validation);
        return (channel) -> builder.execute(new RestStatusToXContentListener<>(channel, (r) -> r.getResponse().getLocation(routing)));
    }


    RestChannelConsumer delete(NodeClient client, String type, String indexName, RestRequest request) {
        assert SUPPORTED_TYPES.contains(type);
        String name = request.param("name");
        String id = generateId(type, name);
        String routing = request.param("routing");
        return (channel) ->  {
            RestStatusToXContentListener<DeleteResponse> restR = new RestStatusToXContentListener<>(channel, (r) -> r.getLocation(routing));
            client.prepareDelete(indexName, ES_TYPE, id)
                    .setRouting(routing)
                    .execute(ActionListener.wrap((deleteResponse) -> {
                            // wrap the response so we can send another request to clear the cache
                            // usually we send only one transport request from the rest layer
                            // it's still unclear which direction we should take (thick or thin REST layer?)
                            ClearCachesAction.RequestBuilder clearCache = new ClearCachesAction.RequestBuilder(client);
                            switch (type) {
                            case StoredFeature.TYPE:
                                clearCache.request().clearFeature(indexName, name);
                                break;
                            case StoredFeatureSet.TYPE:
                                clearCache.request().clearFeatureSet(indexName, name);
                                break;
                            case StoredLtrModel.TYPE:
                                clearCache.request().clearModel(indexName, name);
                                break;
                            }
                            clearCache.execute(ActionListener.wrap(
                                    (r) -> restR.onResponse(deleteResponse),
                                    // Is it good to fail the whole request if cache invalidation failed?
                                    restR::onFailure
                            ));
                        },
                        restR::onFailure
                    ));
        };
    }

    RestChannelConsumer get(NodeClient client, String type, String indexName, RestRequest request) {
        assert SUPPORTED_TYPES.contains(type);
        String name = request.param("name");
        String routing = request.param("routing");
        String id = generateId(type, name);
        return (channel) -> client.prepareGet(indexName, ES_TYPE, id)
                .setRouting(routing)
                .execute(new RestToXContentListener<GetResponse>(channel) {
                    @Override
                    protected RestStatus getStatus(final GetResponse response) {
                        return response.isExists() ? OK : NOT_FOUND;
                    }
                });
    }

    RestChannelConsumer search(NodeClient client, String type, String indexName, RestRequest request) {
        String prefix = request.param("prefix");
        int from = request.paramAsInt("from", 0);
        int size = request.paramAsInt("size", 20);
        BoolQueryBuilder qb = boolQuery().filter(termQuery("type", type));
        if (prefix != null && !prefix.isEmpty()) {
            qb.must(matchQuery("name.prefix", prefix));
        }
        return (channel) -> client.prepareSearch(indexName)
                .setTypes(IndexFeatureStore.ES_TYPE)
                .setQuery(qb)
                .setSize(size)
                .setFrom(from)
                .execute(new RestStatusToXContentListener<>(channel));
    }

    static class AutoDetectParser {
        private String expectedName;
        private StorableElement element;
        private FeatureValidation validation;

        private static final ObjectParser<AutoDetectParser, String> PARSER = new ObjectParser<>("storable_elements");

        static {
            PARSER.declareObject(AutoDetectParser::setElement,
                    StoredFeature::parse,
                    new ParseField(StoredFeature.TYPE));
            PARSER.declareObject(AutoDetectParser::setElement,
                    StoredFeatureSet::parse,
                    new ParseField(StoredFeatureSet.TYPE));
            PARSER.declareObject(AutoDetectParser::setElement,
                    StoredLtrModel::parse,
                    new ParseField(StoredLtrModel.TYPE));
            PARSER.declareObject((b, v) -> b.validation = v,
                    (p, c) -> FeatureValidation.PARSER.apply(p, null),
                    new ParseField("validation"));
        }

        AutoDetectParser(String name) {
            this.expectedName = name;
        }
        public void parse(XContentParser parser) throws IOException {
            PARSER.parse(parser, this, expectedName);
            if (element == null) {
                throw new ParsingException(parser.getTokenLocation(), "Element of type [" + SUPPORTED_TYPES.stream().collect(joining(",")) +
                        "] is mandatory.");
            }
        }

        public void setElement(StorableElement element) {
            if (this.element != null) {
                throw new IllegalArgumentException("[" + element.type() + "] already set, only one element can be set at a time (" +
                        SUPPORTED_TYPES.stream().collect(joining(",")) + ").");
            }
            this.element = element;
        }

        public void setValidation(FeatureValidation validation) {
            this.validation = validation;
        }
    }
}
