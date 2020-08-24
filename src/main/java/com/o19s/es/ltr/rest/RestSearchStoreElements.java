package com.o19s.es.ltr.rest;

import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestStatusToXContentListener;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class RestSearchStoreElements extends FeatureStoreBaseRestHandler {
    private final String type;

    public RestSearchStoreElements(String type) {
        this.type = type;
    }

    @Override
    public String getName() {
        return "Search for " + type + " elements in the LTR feature store";
    }

    @Override
    public List<Route> routes() {
        return unmodifiableList(asList(
                new Route(RestRequest.Method.GET, "/_ltr/{store}/_" + type),
                new Route(RestRequest.Method.GET, "/_ltr/_" + type)
        ));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) {
        return search(client, type, indexName(request), request);
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
