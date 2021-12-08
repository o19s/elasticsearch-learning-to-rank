package com.o19s.es.ltr.rest;

import com.o19s.es.ltr.action.ListStoresAction;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.action.RestBuilderListener;
import org.elasticsearch.rest.action.RestToXContentListener;
import org.elasticsearch.rest.BaseRestHandler;

import java.io.IOException;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

public class RestStoreManager extends FeatureStoreBaseRestHandler {
    @Override
    public String getName() {
        return "Manage the LtR store";
    }

    @Override
    public List<Route> routes() {
        return unmodifiableList(asList(
                new Route(RestRequest.Method.PUT, "/_ltr/{store}"),
                new Route(RestRequest.Method.PUT, "/_ltr"),
                new Route(RestRequest.Method.POST, "/_ltr/{store}"),
                new Route(RestRequest.Method.POST, "/_ltr"),
                new Route(RestRequest.Method.DELETE, "/_ltr/{store}"),
                new Route(RestRequest.Method.DELETE, "/_ltr"),
                new Route(RestRequest.Method.GET, "/_ltr"),
                new Route(RestRequest.Method.GET, "/_ltr/{store}")
        ));
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
     * @throws IOException if an I/O exception occurred parsing the request and preparing for execution
     */
    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        String indexName = indexName(request);

        if (request.method() == RestRequest.Method.PUT) {
            if (request.hasParam("store")) {
                IndexFeatureStore.validateFeatureStoreName(request.param("store"));
            }
            return createIndex(client, indexName);
        } else if (request.method() == RestRequest.Method.POST) {
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

    RestChannelConsumer listStores(NodeClient client) {
        return (channel) -> new ListStoresAction.ListStoresActionBuilder(client).execute(
                new RestToXContentListener<>(channel)
        );
    }

    RestChannelConsumer getStore(NodeClient client, String indexName) {
        return (channel) -> client.admin().indices().prepareExists(indexName)
                .execute(new RestBuilderListener<IndicesExistsResponse>(channel) {
                    @Override
                    public RestResponse buildResponse(
                            IndicesExistsResponse indicesExistsResponse,
                            XContentBuilder builder
                    ) throws Exception {
                        builder.startObject()
                                .field("exists", indicesExistsResponse.isExists())
                                .endObject()
                                .close();
                        return new BytesRestResponse(
                                indicesExistsResponse.isExists() ? RestStatus.OK : RestStatus.NOT_FOUND,
                                builder
                        );
                    }
                });
    }

    RestChannelConsumer createIndex(NodeClient client, String indexName) {
        return (channel) -> client.admin().indices()
                .create(IndexFeatureStore.buildIndexRequest(indexName), new RestToXContentListener<>(channel));
    }

    RestChannelConsumer deleteIndex(NodeClient client, String indexName) {
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(indexName);
        return (channel) -> client.admin().indices().delete(deleteIndexRequest, new RestToXContentListener<>(channel));
    }
}
