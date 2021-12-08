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

import com.o19s.es.ltr.action.ClearCachesAction.ClearCachesNodeResponse;
import com.o19s.es.ltr.action.ClearCachesAction.ClearCachesNodesRequest;
import com.o19s.es.ltr.action.ClearCachesAction.ClearCachesNodesResponse;
import com.o19s.es.ltr.feature.store.index.Caches;
import org.elasticsearch.action.FailedNodeException;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.nodes.BaseNodeRequest;
import org.elasticsearch.action.support.nodes.TransportNodesAction;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;
import java.util.List;

public class TransportClearCachesAction extends TransportNodesAction<ClearCachesNodesRequest, ClearCachesNodesResponse,
        TransportClearCachesAction.ClearCachesNodeRequest, ClearCachesNodeResponse> {
    private final Caches caches;

    @Inject
    public TransportClearCachesAction(Settings settings, ThreadPool threadPool,
                                         ClusterService clusterService, TransportService transportService,
                                         ActionFilters actionFilters, IndexNameExpressionResolver indexNameExpressionResolver,
                                         Caches caches) {
        super(ClearCachesAction.NAME, threadPool, clusterService, transportService, actionFilters,
                ClearCachesNodesRequest::new, ClearCachesNodeRequest::new, ThreadPool.Names.MANAGEMENT, ClearCachesNodeResponse.class);
        this.caches = caches;
    }

    @Override
    protected ClearCachesNodesResponse newResponse(ClearCachesNodesRequest request, List<ClearCachesNodeResponse> responses,
                                                   List<FailedNodeException> failures) {
        return new ClearCachesNodesResponse(clusterService.getClusterName(), responses, failures);
    }

    @Override
    protected ClearCachesNodeRequest newNodeRequest(ClearCachesNodesRequest request) {
        return new ClearCachesNodeRequest(request);
    }

    @Override
    protected ClearCachesNodeResponse newNodeResponse(StreamInput in, DiscoveryNode node) throws IOException {
        return null; // TODO
    }

    @Override
    protected ClearCachesNodeResponse nodeOperation(ClearCachesNodeRequest request) {
        ClearCachesNodesRequest r = request.request;
        switch (r.getOperation()) {
        case ClearStore:
            caches.evict(r.getStore());
            break;
        case ClearFeature:
            caches.evictFeature(r.getStore(), r.getName());
            break;
        case ClearFeatureSet:
            caches.evictFeatureSet(r.getStore(), r.getName());
            break;
        case ClearModel:
            caches.evictModel(r.getStore(), r.getName());
            break;
        default:
            throw new RuntimeException("Unsupported operation [" + r.getOperation() + "]");
        }
        return new ClearCachesNodeResponse(clusterService.localNode());
    }

    public static class ClearCachesNodeRequest extends BaseNodeRequest {
        private ClearCachesNodesRequest request;

        public ClearCachesNodeRequest() {}

        public ClearCachesNodeRequest(ClearCachesNodesRequest req) {
            this.request = req;
        }

        ClearCachesNodeRequest(StreamInput in) throws IOException {
            super(in);
            request = new ClearCachesNodesRequest(in);
        }


        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            request.writeTo(out);
        }
    }
}
