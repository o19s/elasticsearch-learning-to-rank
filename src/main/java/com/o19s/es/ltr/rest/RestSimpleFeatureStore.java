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

import com.o19s.es.ltr.action.FeatureStoreAction;
import com.o19s.es.ltr.action.ListStoresAction;
import com.o19s.es.ltr.feature.store.StorableElement;
import com.o19s.es.ltr.feature.store.StoredDerivedFeature;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.action.AcknowledgedRestListener;
import org.elasticsearch.rest.action.RestStatusToXContentListener;
import org.elasticsearch.rest.action.RestToXContentListener;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.o19s.es.ltr.feature.store.StorableElement.generateId;
import static com.o19s.es.ltr.feature.store.index.IndexFeatureStore.ES_TYPE;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.rest.RestStatus.NOT_FOUND;
import static org.elasticsearch.rest.RestStatus.OK;

/**
 * Simple CRUD operation for the feature store elements.
 */
public abstract class RestSimpleFeatureStore extends FeatureStoreBaseRestHandler {
    private static final Set<String> SUPPORTED_TYPES = unmodifiableSet(new HashSet<>(asList(
            StoredDerivedFeature.TYPE,
            StoredFeature.TYPE,
            StoredFeatureSet.TYPE,
            StoredLtrModel.TYPE)));

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
        protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
            return search(client, type, indexName(request), request);
        }
    }

    static class RestStoreManager extends RestSimpleFeatureStore {
        RestStoreManager(Settings settings, RestController controller) {
            super(settings);
            controller.registerHandler(RestRequest.Method.PUT, "/_ltr/{store}", this);
            controller.registerHandler(RestRequest.Method.PUT, "/_ltr", this);
            controller.registerHandler(RestRequest.Method.DELETE, "/_ltr/{store}", this);
            controller.registerHandler(RestRequest.Method.DELETE, "/_ltr", this);
            controller.registerHandler(RestRequest.Method.GET, "/_ltr", this);
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
                return createIndex(client, indexName);
            } else if (request.method() == RestRequest.Method.DELETE) {
                return deleteIndex(client, indexName);
            } else {
                assert request.method() == RestRequest.Method.GET;
                return listStores(client);
            }
        }
    }

    RestChannelConsumer listStores(NodeClient client) {
        return (channel) -> ListStoresAction.INSTANCE.newRequestBuilder(client).execute(
                new RestToXContentListener<ListStoresAction.ListStoresActionResponse>(channel));
    }

    RestChannelConsumer createIndex(NodeClient client, String indexName) {
        return (channel) -> client.admin().indices()
                .create(IndexFeatureStore.buildIndexRequest(indexName), new AcknowledgedRestListener<>(channel));
    }

    RestChannelConsumer deleteIndex(NodeClient client, String indexName) {
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(indexName);
        return (channel) -> client.admin().indices().delete(deleteIndexRequest, new AcknowledgedRestListener<>(channel));
    }

    RestChannelConsumer addOrUpdate(NodeClient client, String type, String indexName, RestRequest request) throws IOException {
        assert SUPPORTED_TYPES.contains(type);
        String routing = request.param("routing");
        if (!request.hasContentOrSourceParam()) {
            throw new IllegalArgumentException("Missing content or source param.");
        }
        String name = request.param("name");
        Tuple<XContentType, BytesReference> content = request.contentOrSourceParam();
        XContentParser parser = XContentFactory.xContent(content.v1()).createParser(NamedXContentRegistry.EMPTY, content.v2());
        StorableElement elt = request.getXContentRegistry().parseNamedObject(StorableElement.class, type, parser, null);
        // Validation happens here when parsing the stored element.
        if (!elt.name().equals(name)) {
            throw new IllegalArgumentException("Name mismatch, send request with [" + elt.name() + "] but [" + name + "] used in the URL");
        }
        if (request.method() == RestRequest.Method.POST && !elt.updatable()) {
            throw new IllegalArgumentException("Element of type [" + elt.type() + "] are not updatable.");
        }
        FeatureStoreAction.FeatureStoreRequestBuilder builder = FeatureStoreAction.INSTANCE.newRequestBuilder(client);
        if (request.method() == RestRequest.Method.PUT) {
            builder.request().setAction(FeatureStoreAction.FeatureStoreRequest.Action.CREATE);
        } else {
            builder.request().setAction(FeatureStoreAction.FeatureStoreRequest.Action.UPDATE);
        }
        builder.request().setStorableElement(elt);
        builder.request().setRouting(routing);
        builder.request().setStore(indexName);
        return (channel) -> builder.execute(new RestStatusToXContentListener<FeatureStoreAction.FeatureStoreResponse>(channel,
                (r) -> r.getResponse().getLocation(routing)));
    }


    RestChannelConsumer delete(NodeClient client, String type, String indexName, RestRequest request) {
        assert SUPPORTED_TYPES.contains(type);
        String name = request.param("name");
        String id = generateId(type, name);
        String routing = request.param("routing");
        return (channel) ->  client.prepareDelete(indexName, ES_TYPE, id).setRouting(routing)
                .execute(new RestStatusToXContentListener<>(channel, (r) -> r.getLocation(routing)));
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

}
