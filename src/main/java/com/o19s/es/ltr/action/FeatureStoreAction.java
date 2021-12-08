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

import com.o19s.es.ltr.action.FeatureStoreAction.FeatureStoreResponse;
import com.o19s.es.ltr.feature.FeatureValidation;
import com.o19s.es.ltr.feature.store.StorableElement;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.ActionType;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable.Reader;
import org.elasticsearch.common.xcontent.StatusToXContentObject;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.rest.RestStatus;

import java.io.IOException;
import java.util.Objects;

import static org.elasticsearch.action.ValidateActions.addValidationError;

public class FeatureStoreAction extends ActionType<FeatureStoreResponse> {
    public static final String NAME = "cluster:admin/ltr/featurestore/data";
    public static final FeatureStoreAction INSTANCE = new FeatureStoreAction();

    protected FeatureStoreAction() {
        super(NAME, FeatureStoreResponse::new);
    }

    @Override
    public Reader<FeatureStoreResponse> getResponseReader() {
        return FeatureStoreResponse::new;
    }

    public static class FeatureStoreRequestBuilder
            extends ActionRequestBuilder<FeatureStoreRequest, FeatureStoreResponse> {
        public FeatureStoreRequestBuilder(ElasticsearchClient client, FeatureStoreAction action) {
            super(client, action, new FeatureStoreRequest());
        }
    }

    public static class FeatureStoreRequest extends ActionRequest {
        private String store;
        private Action action;
        private StorableElement storableElement;
        private Long updatedVersion;
        private String routing;

        private FeatureValidation validation;

        public FeatureStoreRequest() {}

        public FeatureStoreRequest(StreamInput in) throws IOException {
            super(in);
            store = in.readString();
            routing = in.readOptionalString();
            action = Action.values()[in.readVInt()];
            storableElement = in.readNamedWriteable(StorableElement.class);
            validation = in.readOptionalWriteable(FeatureValidation::new);
        }

        public FeatureStoreRequest(String store, StorableElement storableElement, Action action) {
            this.store = Objects.requireNonNull(store);
            this.storableElement = Objects.requireNonNull(storableElement);
            this.action = Objects.requireNonNull(action);
        }

        public FeatureStoreRequest(String store, StorableElement storableElement, long updatedVersion) {
            this.store = Objects.requireNonNull(store);
            this.storableElement = Objects.requireNonNull(storableElement);
            this.action = Action.UPDATE;
            this.updatedVersion = updatedVersion;
        }

        @Override
        public ActionRequestValidationException validate() {
            ActionRequestValidationException arve = null;
            if (store == null) {
                arve = addValidationError("store must be set", null);
            }
            if (!store.equals(IndexFeatureStore.DEFAULT_STORE) && !store.startsWith(IndexFeatureStore.STORE_PREFIX)) {
                arve = addValidationError("Store name [" + store + "] is invalid.", arve);
            }
            if (storableElement == null) {
                arve = addValidationError("storable element must be set", arve);
            }
            if (action == Action.UPDATE && !storableElement.updatable()) {
                arve = addValidationError("Elements of type [" + storableElement.type() + "] are not updatable.", arve);
            }
            if (updatedVersion != null && action != Action.UPDATE) {
                arve = addValidationError("Only UPDATE supports a version.", arve);
            }
            return arve;
        }

        public String getStore() {
            return store;
        }

        public void setStore(String store) {
            this.store = store;
        }

        public Action getAction() {
            return action;
        }

        public void setAction(Action action) {
            this.action = action;
        }

        public StorableElement getStorableElement() {
            return storableElement;
        }

        public void setStorableElement(StorableElement storableElement) {
            this.storableElement = storableElement;
        }

        public String getRouting() {
            return routing;
        }

        public void setRouting(String routing) {
            this.routing = routing;
        }

        public Long getUpdatedVersion() {
            return updatedVersion;
        }

        public FeatureValidation getValidation() {
            return validation;
        }

        public void setValidation(FeatureValidation validation) {
            this.validation = validation;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeString(store);
            out.writeOptionalString(routing);
            out.writeVInt(action.ordinal());
            out.writeNamedWriteable(storableElement);
            out.writeOptionalWriteable(validation);
        }

        public enum Action {
            CREATE,
            UPDATE
        }
    }

    public static class FeatureStoreResponse extends ActionResponse implements StatusToXContentObject {
        private IndexResponse response;

        public FeatureStoreResponse(StreamInput in) throws IOException {
            super(in);
            response = new IndexResponse(in);
        }

        public FeatureStoreResponse(IndexResponse response) {
            this.response = response;
        }

        public IndexResponse getResponse() {
            return response;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            response.writeTo(out);
        }

        /**
         * Returns the REST status to make sure it is returned correctly
         */
        @Override
        public RestStatus status() {
            return response.status();
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            return response.toXContent(builder, params);
        }
    }
}
