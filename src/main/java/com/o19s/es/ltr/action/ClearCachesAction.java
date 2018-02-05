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

import org.elasticsearch.action.Action;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.FailedNodeException;
import org.elasticsearch.action.support.nodes.BaseNodeResponse;
import org.elasticsearch.action.support.nodes.BaseNodesRequest;
import org.elasticsearch.action.support.nodes.BaseNodesResponse;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static org.elasticsearch.action.ValidateActions.addValidationError;

public class ClearCachesAction extends Action<ClearCachesAction.ClearCachesNodesRequest,
        ClearCachesAction.ClearCachesNodesResponse, ClearCachesAction.RequestBuilder> {
    public static final String NAME = "cluster:admin/ltr/caches";
    public static final ClearCachesAction INSTANCE = new ClearCachesAction();

    private ClearCachesAction() {
        super(NAME);
    }

    @Override
    public RequestBuilder newRequestBuilder(ElasticsearchClient client) {
        return new RequestBuilder(client);
    }

    @Override
    public ClearCachesNodesResponse newResponse() {
        return new ClearCachesNodesResponse();
    }

    public static class RequestBuilder extends ActionRequestBuilder<ClearCachesNodesRequest, ClearCachesNodesResponse,
            ClearCachesAction.RequestBuilder> {
        RequestBuilder(ElasticsearchClient client) {
            super(client, ClearCachesAction.INSTANCE, new ClearCachesNodesRequest());
        }
    }

    public static class ClearCachesNodesRequest extends BaseNodesRequest<ClearCachesNodesRequest> {
        private String store;
        private Operation operation;
        private String name;

        @Override
        public ActionRequestValidationException validate() {
            ActionRequestValidationException arve = null;
            if (store == null) {
                arve = addValidationError("store cannot be null", null);
            }

            if (operation == null) {
                arve = addValidationError("no operation provided", arve);
            }

            if (operation != null && operation != Operation.ClearStore && name == null) {
                arve = addValidationError("name must be provided if clearing a specific element", arve);
            }
            return arve;
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            super.readFrom(in);
            store = in.readString();
            operation = Operation.values()[in.readVInt()];
            name = in.readOptionalString();
        }

        public void clearStore(String storeName) {
            operation = ClearCachesNodesRequest.Operation.ClearStore;
            store = Objects.requireNonNull(storeName);
        }

        public void clearFeature(String storeName, String name) {
            clearElement(storeName, name, ClearCachesNodesRequest.Operation.ClearFeature);
        }

        public void clearFeatureSet(String storeName, String name) {
            clearElement(storeName, name, ClearCachesNodesRequest.Operation.ClearFeatureSet);
        }

        public void clearModel(String storeName, String name) {
            clearElement(storeName, name, ClearCachesNodesRequest.Operation.ClearModel);
        }

        private void clearElement(String storeName, String name, ClearCachesNodesRequest.Operation op) {
            operation = op;
            store = Objects.requireNonNull(storeName);
            this.name = Objects.requireNonNull(name);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeString(store);
            out.writeVInt(operation.ordinal());
            out.writeOptionalString(name);
        }

        public enum Operation {
            ClearStore,
            ClearFeature,
            ClearFeatureSet,
            ClearModel
        }

        public String getStore() {
            return store;
        }

        public Operation getOperation() {
            return operation;
        }

        public String getName() {
            return name;
        }
    }

    public static class ClearCachesNodesResponse extends BaseNodesResponse<ClearCachesNodeResponse> {
        public ClearCachesNodesResponse() {}

        public ClearCachesNodesResponse(ClusterName clusterName, List<ClearCachesNodeResponse> responses,
                                        List<FailedNodeException> failures) {
            super(clusterName, responses, failures);
        }

        @Override
        protected List<ClearCachesNodeResponse> readNodesFrom(StreamInput in) throws IOException {
            return in.readList(ClearCachesNodeResponse::new);
        }

        @Override
        protected void writeNodesTo(StreamOutput out, List<ClearCachesNodeResponse> nodes) throws IOException {
            out.writeStreamableList(nodes);
        }
    }

    // NOOP response
    public static class ClearCachesNodeResponse extends BaseNodeResponse {
        public ClearCachesNodeResponse() {
        }

        public ClearCachesNodeResponse(DiscoveryNode node) {
            super(node);
        }

        public ClearCachesNodeResponse(StreamInput in) throws IOException {
            readFrom(in);
        }
    }
}
