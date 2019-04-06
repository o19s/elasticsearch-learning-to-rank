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

import com.o19s.es.ltr.action.CreateModelFromSetAction.CreateModelFromSetRequest;
import com.o19s.es.ltr.action.CreateModelFromSetAction.CreateModelFromSetResponse;
import com.o19s.es.ltr.action.FeatureStoreAction.FeatureStoreRequest;
import com.o19s.es.ltr.feature.store.StorableElement;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.TransportGetAction;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.HandledTransportAction;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;

public class TransportCreateModelFromSetAction extends HandledTransportAction<CreateModelFromSetRequest, CreateModelFromSetResponse> {
    private final ClusterService clusterService;
    private final TransportGetAction getAction;
    private final TransportFeatureStoreAction featureStoreAction;

    @Inject
    public TransportCreateModelFromSetAction(Settings settings, ThreadPool threadPool,
                                                TransportService transportService, ActionFilters actionFilters,
                                                IndexNameExpressionResolver indexNameExpressionResolver,
                                                ClusterService clusterService, TransportGetAction getAction,
                                                TransportFeatureStoreAction featureStoreAction) {
        super(CreateModelFromSetAction.NAME, transportService, actionFilters, CreateModelFromSetRequest::new);
        this.clusterService = clusterService;
        this.getAction = getAction;
        this.featureStoreAction = featureStoreAction;
    }

    @Override
    protected void doExecute(Task task, CreateModelFromSetRequest request, ActionListener<CreateModelFromSetResponse> listener) {
        if (!clusterService.state().routingTable().hasIndex(request.getStore())) {
            throw new IllegalArgumentException("Store [" + request.getStore() + "] does not exist, please create it first.");
        }
        GetRequest getRequest = new GetRequest(request.getStore())
                .type(IndexFeatureStore.ES_TYPE)
                .id(StorableElement.generateId(StoredFeatureSet.TYPE, request.getFeatureSetName()));
        getRequest.setParentTask(clusterService.localNode().getId(), task.getId());
        getAction.execute(getRequest, ActionListener.wrap((r) -> this.doStore(task, r, request, listener), listener::onFailure));
    }

    private void doStore(Task parentTask, GetResponse response, CreateModelFromSetRequest request,
                         ActionListener<CreateModelFromSetResponse> listener) {
        if (!response.isExists()) {
            throw new IllegalArgumentException("Stored feature set [" + request.getFeatureSetName() + "] does not exist");
        }
        if (request.getExpectedSetVersion() != null && request.getExpectedSetVersion() != response.getVersion()) {
            throw new IllegalArgumentException("Stored feature set [" + request.getFeatureSetName() + "]" +
                    " has version [" + response.getVersion() + "] but [" + request.getExpectedSetVersion() + "] was expected.");
        }
        final StoredFeatureSet set;
        try {
            set = IndexFeatureStore.parse(StoredFeatureSet.class, StoredFeatureSet.TYPE, response.getSourceAsBytesRef());
        } catch(IOException ioe) {
            throw new IllegalStateException("Cannot parse stored feature set [" + request.getFeatureSetName() + "]", ioe);
        }
        // Model will be parsed & checked by TransportFeatureStoreAction
        StoredLtrModel model = new StoredLtrModel(request.getModelName(), set, request.getDefinition());
        FeatureStoreRequest featureStoreRequest = new FeatureStoreRequest(request.getStore(), model, FeatureStoreRequest.Action.CREATE);
        featureStoreRequest.setRouting(request.getRouting());
        featureStoreRequest.setParentTask(clusterService.localNode().getId(), parentTask.getId());
        featureStoreRequest.setValidation(request.getValidation());
        featureStoreAction.execute(featureStoreRequest, ActionListener.wrap(
                (r) -> listener.onResponse(new CreateModelFromSetResponse(r.getResponse())),
                listener::onFailure));

    }
}
