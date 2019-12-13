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

package com.o19s.es.ltr.action;

import com.o19s.es.ltr.action.ClearCachesAction.ClearCachesNodesRequest;
import com.o19s.es.ltr.action.FeatureStoreAction.FeatureStoreRequest;
import com.o19s.es.ltr.action.FeatureStoreAction.FeatureStoreResponse;
import com.o19s.es.ltr.feature.FeatureValidation;
import com.o19s.es.ltr.feature.store.StorableElement;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import com.o19s.es.ltr.query.ValidatingLtrQueryBuilder;
import com.o19s.es.ltr.ranker.parser.LtrRankerParserFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.index.IndexAction;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.ShardSearchFailure;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.HandledTransportAction;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;
import java.util.Optional;

import static org.elasticsearch.action.ActionListener.wrap;

public class TransportFeatureStoreAction extends HandledTransportAction<FeatureStoreRequest, FeatureStoreResponse> {
    private final LtrRankerParserFactory factory;
    private final ClusterService clusterService;
    private final TransportClearCachesAction clearCachesAction;
    private final Client client;
    private final Logger logger = LogManager.getLogger(getClass());

    @Inject
    public TransportFeatureStoreAction(TransportService transportService,
                                       ActionFilters actionFilters,
                                       ClusterService clusterService, Client client,
                                       LtrRankerParserFactory factory,
                                       TransportClearCachesAction clearCachesAction) {
        super(FeatureStoreAction.NAME, false, transportService, actionFilters, FeatureStoreRequest::new);
        this.factory = factory;
        this.clusterService = clusterService;
        this.clearCachesAction = clearCachesAction;
        this.client = client;
    }

    @Override
    protected void doExecute(Task task, FeatureStoreRequest request, ActionListener<FeatureStoreResponse> listener) {
        if (!clusterService.state().routingTable().hasIndex(request.getStore())) {
            // To prevent index auto creation
            throw new IllegalArgumentException("Store [" + request.getStore() + "] does not exist, please create it first.");
        }
        // some synchronous pre-checks that require the parser factory
        precheck(request);
        if (request.getValidation() != null) {
            // validate and then store
            validate(request.getValidation(), request.getStorableElement(), task, listener,
                    () -> store(request, task, listener));
        } else {
            store(request, task, listener);
        }
    }

    private Optional<ClearCachesNodesRequest> buildClearCache(FeatureStoreRequest request) throws IOException {
        if (request.getAction() == FeatureStoreRequest.Action.UPDATE) {
             ClearCachesAction.ClearCachesNodesRequest clearCachesNodesRequest = new ClearCachesAction.ClearCachesNodesRequest();
             switch (request.getStorableElement().type()) {
             case StoredFeature.TYPE:
                 clearCachesNodesRequest.clearFeature(request.getStore(), request.getStorableElement().name());
                 return Optional.of(clearCachesNodesRequest);
             case StoredFeatureSet.TYPE:
                 clearCachesNodesRequest.clearFeatureSet(request.getStore(), request.getStorableElement().name());
                 return Optional.of(clearCachesNodesRequest);
             }
        }
        return Optional.empty();
    }

    private IndexRequest buildIndexRequest(Task parentTask, FeatureStoreRequest request) throws IOException {
        StorableElement elt = request.getStorableElement();

        IndexRequest indexRequest = client.prepareIndex(request.getStore(), IndexFeatureStore.ES_TYPE, elt.id())
                .setCreate(request.getAction() == FeatureStoreRequest.Action.CREATE)
                .setRouting(request.getRouting())
                .setSource(IndexFeatureStore.toSource(elt))
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                .request();
        indexRequest.setParentTask(clusterService.localNode().getId(), parentTask.getId());
        return indexRequest;
    }

    /**
     * Will throw an exception if it fails.
     */
    private void precheck(FeatureStoreRequest request) {
        if (request.getStorableElement() instanceof StoredLtrModel) {
            StoredLtrModel model = (StoredLtrModel) request.getStorableElement();
            try {
                model.compile(factory);
            } catch (Exception e) {
                throw new IllegalArgumentException("Error while parsing model [" + model.name() + "]" +
                        " with type [" + model.rankingModelType() + "]", e);
            }
        } else if (request.getStorableElement() instanceof StoredFeatureSet) {
            StoredFeatureSet set = (StoredFeatureSet) request.getStorableElement();
            set.optimize().validate();
        } else if (request.getStorableElement() instanceof StoredFeature) {
            StoredFeature feature = (StoredFeature) request.getStorableElement();
            feature.optimize();
        }
    }

    /**
     * Perform a test search request to validate the element prior to storing it.
     *
     * @param validation validation info
     * @param element the element stored
     * @param task the parent task
     * @param listener the action listener to write to
     * @param onSuccess action ro run when the validation is successfull
     */
    private void validate(FeatureValidation validation,
                          StorableElement element,
                          Task task,
                          ActionListener<FeatureStoreResponse> listener,
                          Runnable onSuccess) {
        ValidatingLtrQueryBuilder ltrBuilder = new ValidatingLtrQueryBuilder(element,
                validation, factory);
        SearchRequestBuilder builder = new SearchRequestBuilder(client, SearchAction.INSTANCE);
        builder.setIndices(validation.getIndex());
        builder.setQuery(ltrBuilder);
        builder.setFrom(0);
        builder.setSize(20);
        // Bail out early and don't score the whole index.
        builder.setTerminateAfter(1000);
        builder.request().setParentTask(clusterService.localNode().getId(), task.getId());
        builder.execute(wrap((r) -> {
                if (r.getFailedShards() > 0) {
                    ShardSearchFailure failure = r.getShardFailures()[0];
                    throw new IllegalArgumentException("Validating the element caused " + r.getFailedShards() +
                            " shard failures, see root cause: " + failure.reason(), failure.getCause());
                }
                onSuccess.run();
            },
            (e) -> listener.onFailure(new IllegalArgumentException("Cannot store element, validation failed.", e))));
    }

    /**
     * Prepare a Runnable to send an index request to store the element, invalidates the cache on success
     */
    private void store(FeatureStoreRequest request, Task task, ActionListener<FeatureStoreResponse> listener) {

        try {
            Optional<ClearCachesNodesRequest> clearCachesNodesRequest = buildClearCache(request);
            IndexRequest indexRequest = buildIndexRequest(task, request);
            client.execute(IndexAction.INSTANCE, indexRequest, wrap(
                    (r) -> {
                        // Run and forget, log only if something bad happens
                        // but don't wait for the action to be done nor set the parent task.
                        clearCachesNodesRequest.ifPresent((req) -> clearCachesAction.execute(req, wrap(
                                (r2) -> {
                                },
                                (e) -> logger.error("Failed to clear cache", e))));
                        listener.onResponse(new FeatureStoreResponse(r));
                    },
                    listener::onFailure));
        } catch (IOException ioe) {
            listener.onFailure(ioe);
        }
    }
}
