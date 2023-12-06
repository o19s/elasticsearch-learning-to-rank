package com.o19s.es.ltr.rest;

import static com.o19s.es.ltr.feature.store.StorableElement.generateId;
import static com.o19s.es.ltr.query.ValidatingLtrQueryBuilder.SUPPORTED_TYPES;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import com.o19s.es.ltr.action.ClearCachesAction;
import com.o19s.es.ltr.action.FeatureStoreAction;
import com.o19s.es.ltr.feature.store.StorableElement;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.client.internal.node.NodeClient;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.action.RestToXContentListener;

public class RestFeatureManager extends FeatureStoreBaseRestHandler {

  private final String type;

  public RestFeatureManager(String type) {
    this.type = type;
  }

  @Override
  public String getName() {
    return "Add, update or delete a " + type;
  }

  @Override
  public List<Route> routes() {
    return unmodifiableList(
        asList(
            new Route(RestRequest.Method.PUT, "/_ltr/{store}/_" + this.type + "/{name}"),
            new Route(RestRequest.Method.PUT, "/_ltr/_" + this.type + "/{name}"),
            new Route(RestRequest.Method.POST, "/_ltr/{store}/_" + this.type + "/{name}"),
            new Route(RestRequest.Method.POST, "/_ltr/_" + this.type + "/{name}"),
            new Route(RestRequest.Method.DELETE, "/_ltr/{store}/_" + this.type + "/{name}"),
            new Route(RestRequest.Method.DELETE, "/_ltr/_" + this.type + "/{name}"),
            new Route(RestRequest.Method.GET, "/_ltr/{store}/_" + this.type + "/{name}"),
            new Route(RestRequest.Method.GET, "/_ltr/_" + this.type + "/{name}"),
            new Route(RestRequest.Method.HEAD, "/_ltr/{store}/_" + this.type + "/{name}"),
            new Route(RestRequest.Method.HEAD, "/_ltr/_" + this.type + "/{name}")));
  }

  @Override
  protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client)
      throws IOException {
    String indexName = indexName(request);
    if (request.method() == RestRequest.Method.DELETE) {
      return delete(client, type, indexName, request);
    } else if (request.method() == RestRequest.Method.HEAD
        || request.method() == RestRequest.Method.GET) {
      return get(client, type, indexName, request);
    } else {
      return addOrUpdate(client, type, indexName, request);
    }
  }

  RestChannelConsumer delete(
      NodeClient client, String type, String indexName, RestRequest request) {
    assert SUPPORTED_TYPES.contains(type);
    String name = request.param("name");
    String id = generateId(type, name);
    String routing = request.param("routing");
    return (channel) -> {
      RestToXContentListener<DeleteResponse> restR =
          new RestToXContentListener<>(channel, DeleteResponse::status);
      client
          .prepareDelete(indexName, id)
          .setRouting(routing)
          .execute(
              ActionListener.wrap(
                  (deleteResponse) -> {
                    // wrap the response so we can send another request to clear the cache
                    // usually we send only one transport request from the rest layer
                    // it's still unclear which direction we should take (thick or thin REST layer?)
                    ClearCachesAction.ClearCachesNodesRequest clearCache =
                        new ClearCachesAction.ClearCachesNodesRequest();
                    switch (type) {
                      case StoredFeature.TYPE:
                        clearCache.clearFeature(indexName, name);
                        break;
                      case StoredFeatureSet.TYPE:
                        clearCache.clearFeatureSet(indexName, name);
                        break;
                      case StoredLtrModel.TYPE:
                        clearCache.clearModel(indexName, name);
                        break;
                    }
                    client.execute(
                        ClearCachesAction.INSTANCE,
                        clearCache,
                        ActionListener.wrap(
                            (r) -> restR.onResponse(deleteResponse),
                            // Is it good to fail the whole request if cache invalidation failed?
                            restR::onFailure));
                  },
                  restR::onFailure));
    };
  }

  RestChannelConsumer get(NodeClient client, String type, String indexName, RestRequest request) {
    assert SUPPORTED_TYPES.contains(type);
    String name = request.param("name");
    String routing = request.param("routing");
    String id = generateId(type, name);
    return (channel) ->
        client
            .prepareGet(indexName, id)
            .setRouting(routing)
            .execute(
                new RestToXContentListener<>(
                    channel, (r) -> r.isExists() ? RestStatus.OK : RestStatus.NOT_FOUND));
  }

  RestChannelConsumer addOrUpdate(
      NodeClient client, String type, String indexName, RestRequest request) throws IOException {
    assert SUPPORTED_TYPES.contains(type);
    String routing = request.param("routing");
    if (!request.hasContentOrSourceParam()) {
      throw new IllegalArgumentException("Missing content or source param.");
    }
    String name = request.param("name");
    AutoDetectParser parserState = new AutoDetectParser(name);
    request.applyContentParser(parserState::parse);
    StorableElement elt = parserState.getElement();
    if (!type.equals(elt.type())) {
      throw new IllegalArgumentException(
          "Excepted a [" + type + "] but encountered [" + elt.type() + "]");
    }

    // Validation happens here when parsing the stored element.
    if (!elt.name().equals(name)) {
      throw new IllegalArgumentException(
          "Name mismatch, send request with ["
              + elt.name()
              + "] but ["
              + name
              + "] used in the URL");
    }
    if (request.method() == RestRequest.Method.POST && !elt.updatable()) {
      try {
        throw new IllegalArgumentException(
            "Element of type ["
                + elt.type()
                + "] are not updatable, "
                + "please create a new one instead.");
      } catch (IllegalArgumentException iae) {
        return (channel) ->
            channel.sendResponse(new RestResponse(channel, RestStatus.METHOD_NOT_ALLOWED, iae));
      }
    }
    FeatureStoreAction.FeatureStoreRequestBuilder builder =
        new FeatureStoreAction.FeatureStoreRequestBuilder(client, FeatureStoreAction.INSTANCE);
    if (request.method() == RestRequest.Method.PUT) {
      builder.request().setAction(FeatureStoreAction.FeatureStoreRequest.Action.CREATE);
    } else {
      builder.request().setAction(FeatureStoreAction.FeatureStoreRequest.Action.UPDATE);
    }
    builder.request().setStorableElement(elt);
    builder.request().setRouting(routing);
    builder.request().setStore(indexName);
    builder.request().setValidation(parserState.getValidation());
    return (channel) ->
        builder.execute(
            new RestToXContentListener<>(
                channel,
                (r) -> r.getResponse().status(),
                (r) -> r.getResponse().getLocation(routing)));
  }
}
