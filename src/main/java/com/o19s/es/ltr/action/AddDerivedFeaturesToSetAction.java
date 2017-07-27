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
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.StatusToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.rest.RestStatus;

import java.io.IOException;

import static org.elasticsearch.action.ValidateActions.addValidationError;

public class AddDerivedFeaturesToSetAction extends Action<AddDerivedFeaturesToSetAction.AddDerivedFeaturesToSetRequest,
        AddDerivedFeaturesToSetAction.AddDerivedFeaturesToSetResponse,
        AddDerivedFeaturesToSetAction.AddDerivedFeaturesToSetRequestBuilder> {
    public static final AddDerivedFeaturesToSetAction INSTANCE = new AddDerivedFeaturesToSetAction();
    public static final String NAME = "ltr:store/add-derived-features-to-set";

    protected AddDerivedFeaturesToSetAction() {
        super(NAME);
    }

    @Override
    public AddDerivedFeaturesToSetRequestBuilder newRequestBuilder(ElasticsearchClient client) {
        return new AddDerivedFeaturesToSetRequestBuilder(client);
    }

    @Override
    public AddDerivedFeaturesToSetResponse newResponse() {
        return new AddDerivedFeaturesToSetResponse();
    }

    public static class AddDerivedFeaturesToSetRequestBuilder extends ActionRequestBuilder<AddDerivedFeaturesToSetRequest,
            AddDerivedFeaturesToSetResponse, AddDerivedFeaturesToSetRequestBuilder> {
        protected AddDerivedFeaturesToSetRequestBuilder(ElasticsearchClient client) {
            super(client, INSTANCE, new AddDerivedFeaturesToSetRequest());
        }
    }

    public static class AddDerivedFeaturesToSetRequest extends ActionRequest {
        private String store;
        private String dfName;
        private String featureSet;
        private String routing;
        @Override
        public ActionRequestValidationException validate() {
            ActionRequestValidationException arve = null;
            if (store == null) {
                arve = addValidationError("store must be set", null);
            }
            if (dfName == null) {
                arve = addValidationError("Derived Feature name must be set", arve);
            }
            if (featureSet == null) {
                arve = addValidationError("featureSet must be set", arve);
            }
            return arve;
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            super.readFrom(in);
            store = in.readString();
            dfName = in.readString();
            featureSet = in.readString();
            routing = in.readOptionalString();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeString(store);
            out.writeString(dfName);
            out.writeString(featureSet);
            out.writeOptionalString(routing);
        }

        public String getStore() {
            return store;
        }

        public String getDerivedName() { return dfName; }

        public String getFeatureSet() {
            return featureSet;
        }

        public void setStore(String store) {
            this.store = store;
        }

        public void setDerivedName(String dfName) { this.dfName = dfName; }

        public void setFeatureSet(String featureSet) {
            this.featureSet = featureSet;
        }

        public String getRouting() {
            return routing;
        }

        public void setRouting(String routing) {
            this.routing = routing;
        }
    }

    public static class AddDerivedFeaturesToSetResponse extends ActionResponse implements StatusToXContentObject {
        private IndexResponse response;

        public AddDerivedFeaturesToSetResponse() {
        }

        public AddDerivedFeaturesToSetResponse(IndexResponse response) {
            this.response = response;
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            super.readFrom(in);
            response = new IndexResponse();
            response.readFrom(in);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
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

        public IndexResponse getResponse() {
            return response;
        }
    }
}
