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

import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.FeatureValidation;
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
import java.util.List;

import static org.elasticsearch.action.ValidateActions.addValidationError;

public class AddFeaturesToSetAction extends Action<AddFeaturesToSetAction.AddFeaturesToSetRequest,
        AddFeaturesToSetAction.AddFeaturesToSetResponse, AddFeaturesToSetAction.AddFeaturesToSetRequestBuilder> {
    public static final AddFeaturesToSetAction INSTANCE = new AddFeaturesToSetAction();
    public static final String NAME = "ltr:store/add-features-to-set";

    protected AddFeaturesToSetAction() {
        super(NAME);
    }

    @Override
    public AddFeaturesToSetRequestBuilder newRequestBuilder(ElasticsearchClient client) {
        return new AddFeaturesToSetRequestBuilder(client);
    }

    @Override
    public AddFeaturesToSetResponse newResponse() {
        return new AddFeaturesToSetResponse();
    }

    public static class AddFeaturesToSetRequestBuilder extends ActionRequestBuilder<AddFeaturesToSetRequest,
            AddFeaturesToSetResponse, AddFeaturesToSetRequestBuilder> {
        protected AddFeaturesToSetRequestBuilder(ElasticsearchClient client) {
            super(client, INSTANCE, new AddFeaturesToSetRequest());
        }
    }

    public static class AddFeaturesToSetRequest extends ActionRequest {
        private String store;
        private String featureNameQuery;
        private List<StoredFeature> features;
        private boolean merge;
        private String featureSet;
        private String routing;
        private FeatureValidation validation;

        @Override
        public ActionRequestValidationException validate() {
            ActionRequestValidationException arve = null;
            if (store == null) {
                arve = addValidationError("store must be set", null);
            }
            if (featureNameQuery != null && features != null && !features.isEmpty()) {
                arve = addValidationError("if featureNameQuery is set features cannot", arve);
            } else if (featureNameQuery == null && (features == null || features.isEmpty())) {
                arve = addValidationError("either featureNameQuery or features must be set", arve);
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
            features = in.readList(StoredFeature::new);
            if (in.readBoolean()) {
                featureNameQuery = in.readOptionalString();
            }
            merge = in.readBoolean();
            featureSet = in.readString();
            routing = in.readOptionalString();
            validation = in.readOptionalWriteable(FeatureValidation::new);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeString(store);
            out.writeOptionalString(featureNameQuery);
            if (features != null) {
                out.writeList(features);
            }
            out.writeBoolean(merge);
            out.writeString(featureSet);
            out.writeOptionalString(routing);
            out.writeOptionalWriteable(validation);
        }

        public String getStore() {
            return store;
        }

        public String getFeatureNameQuery() {
            return featureNameQuery;
        }

        public String getFeatureSet() {
            return featureSet;
        }

        public void setStore(String store) {
            this.store = store;
        }

        public void setFeatureNameQuery(String featureNameQuery) {
            this.featureNameQuery = featureNameQuery;
        }

        public void setFeatureSet(String featureSet) {
            this.featureSet = featureSet;
        }

        public String getRouting() {
            return routing;
        }

        public void setRouting(String routing) {
            this.routing = routing;
        }

        public List<StoredFeature> getFeatures() {
            return features;
        }

        public void setFeatures(List<StoredFeature> features) {
            this.features = features;
        }

        public boolean isMerge() {
            return merge;
        }

        public void setMerge(boolean merge) {
            this.merge = merge;
        }

        public FeatureValidation getValidation() {
            return validation;
        }

        public void setValidation(FeatureValidation validation) {
            this.validation = validation;
        }
    }

    public static class AddFeaturesToSetResponse extends ActionResponse implements StatusToXContentObject {
        private IndexResponse response;

        public AddFeaturesToSetResponse() {
        }

        public AddFeaturesToSetResponse(IndexResponse response) {
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
