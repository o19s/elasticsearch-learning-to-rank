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

import com.o19s.es.ltr.action.CreateModelFromSetAction.CreateModelFromSetResponse;
import com.o19s.es.ltr.feature.FeatureValidation;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.ActionType;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.StatusToXContentObject;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.rest.RestStatus;

import java.io.IOException;

import static org.elasticsearch.action.ValidateActions.addValidationError;

public class CreateModelFromSetAction extends ActionType<CreateModelFromSetResponse> {
    public static final String NAME = "cluster:admin/ltr/store/create-model-from-set";
    public static final CreateModelFromSetAction INSTANCE = new CreateModelFromSetAction();

    protected CreateModelFromSetAction() {
        super(NAME, CreateModelFromSetResponse::new);
    }


    public static class CreateModelFromSetRequestBuilder extends ActionRequestBuilder<CreateModelFromSetRequest,
        CreateModelFromSetResponse> {

        public CreateModelFromSetRequestBuilder(ElasticsearchClient client) {
            super(client, INSTANCE, new CreateModelFromSetRequest());
        }

        public CreateModelFromSetRequestBuilder withVersion(String store, String featureSetName, long expectedSetVersion,
                                                            String modelName, StoredLtrModel.LtrModelDefinition definition) {
            request.store = store;
            request.featureSetName = featureSetName;
            request.expectedSetVersion = expectedSetVersion;
            request.modelName = modelName;
            request.definition = definition;
            return this;
        }

        public CreateModelFromSetRequestBuilder withoutVersion(String store, String featureSetName, String modelName,
                                                               StoredLtrModel.LtrModelDefinition definition) {
            request.store = store;
            request.featureSetName = featureSetName;
            request.expectedSetVersion = null;
            request.modelName = modelName;
            request.definition = definition;
            return this;
        }

        public CreateModelFromSetRequestBuilder routing(String routing) {
            request.setRouting(routing);
            return this;
        }
    }

    public static class CreateModelFromSetRequest extends ActionRequest {
        private String store;
        private String featureSetName;
        private Long expectedSetVersion;
        private String modelName;
        private StoredLtrModel.LtrModelDefinition definition;
        private String routing;
        private FeatureValidation validation;

        public CreateModelFromSetRequest() {

        }

        public CreateModelFromSetRequest(StreamInput in) throws IOException {
            super(in);
            store = in.readString();
            featureSetName = in.readString();
            expectedSetVersion = in.readOptionalLong();
            modelName = in.readString();
            definition = new StoredLtrModel.LtrModelDefinition(in);
            routing = in.readOptionalString();
            validation = in.readOptionalWriteable(FeatureValidation::new);
        }

        @Override
        public ActionRequestValidationException validate() {
            ActionRequestValidationException arve = null;
            if (store == null) {
                arve = addValidationError("store must be set", null);
            }
            if (featureSetName == null) {
                arve = addValidationError("featureSetName must be set", arve);
            }
            if (modelName == null) {
                arve = addValidationError("modelName must be set", arve);
            }
            if (definition == null) {
                arve = addValidationError("defition must be set", arve);
            }
            return arve;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeString(store);
            out.writeString(featureSetName);
            out.writeOptionalLong(expectedSetVersion);
            out.writeString(modelName);
            definition.writeTo(out);
            out.writeOptionalString(routing);
            out.writeOptionalWriteable(validation);
        }

        public String getStore() {
            return store;
        }

        public String getFeatureSetName() {
            return featureSetName;
        }

        public String getModelName() {
            return modelName;
        }

        public StoredLtrModel.LtrModelDefinition getDefinition() {
            return definition;
        }

        public Long getExpectedSetVersion() {
            return expectedSetVersion;
        }

        public String getRouting() {
            return routing;
        }

        public void setRouting(String routing) {
            this.routing = routing;
        }

        public FeatureValidation getValidation() {
            return validation;
        }

        public void setValidation(FeatureValidation validation) {
            this.validation = validation;
        }
    }

    public static class CreateModelFromSetResponse extends ActionResponse implements StatusToXContentObject {
        private static final int VERSION = 1;
        private IndexResponse response;

        public CreateModelFromSetResponse(StreamInput in) throws IOException {
            super(in);
            int version = in.readVInt();
            assert version == VERSION;
            response = new IndexResponse(in);
        }

        public CreateModelFromSetResponse(IndexResponse response) {
            this.response = response;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeVInt(VERSION);
            response.writeTo(out);
        }

        public IndexResponse getResponse() {
            return response;
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            return response.toXContent(builder, params);
        }

        @Override
        public RestStatus status() {
            return response.status();
        }
    }
}
