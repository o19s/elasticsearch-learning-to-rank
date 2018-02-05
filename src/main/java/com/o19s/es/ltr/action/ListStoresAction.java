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

import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import org.elasticsearch.action.Action;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.support.master.MasterNodeReadRequest;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ListStoresAction extends Action<ListStoresAction.ListStoresActionRequest,
        ListStoresAction.ListStoresActionResponse,ListStoresAction.ListStoresActionRequestBuilder> {
    public static final String NAME = "cluster:admin/ltr/featurestore/list";
    public static final ListStoresAction INSTANCE = new ListStoresAction();

    private ListStoresAction() {
        super(NAME);
    }

    @Override
    public ListStoresActionResponse newResponse() {
        return new ListStoresActionResponse();
    }

    @Override
    public ListStoresActionRequestBuilder newRequestBuilder(ElasticsearchClient client) {
        return new ListStoresActionRequestBuilder(client);
    }

    public static class ListStoresActionRequestBuilder extends ActionRequestBuilder<ListStoresActionRequest,
            ListStoresActionResponse, ListStoresActionRequestBuilder> {
        protected ListStoresActionRequestBuilder(ElasticsearchClient client) {
            super(client, INSTANCE, new ListStoresActionRequest());
        }
    }

    public static class ListStoresActionRequest extends MasterNodeReadRequest<ListStoresActionRequest> {
        @Override
        public ActionRequestValidationException validate() {
            return null;
        }
    }

    public static class ListStoresActionResponse extends ActionResponse implements ToXContentObject {
        private Map<String, IndexStoreInfo> stores;

        ListStoresActionResponse() {}

        public ListStoresActionResponse(List<IndexStoreInfo> info) {
            stores = info.stream().collect(Collectors.toMap((i) -> i.storeName, (i) -> i));
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            return builder.startObject()
                    .field("stores", stores)
                    .endObject();
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            super.readFrom(in);
            stores = in.readMap(StreamInput::readString, IndexStoreInfo::new);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeMap(stores, StreamOutput::writeString, (w, i) -> i.writeTo(w));
        }

        public Map<String, IndexStoreInfo> getStores() {
            return stores;
        }
    }

    public static class IndexStoreInfo implements Writeable, ToXContent {
        private String storeName;
        private String indexName;
        private int version;
        private Map<String, Integer> counts;

        public IndexStoreInfo(String indexName, int version, Map<String, Integer> counts) {
            this.indexName = Objects.requireNonNull(indexName);
            this.storeName = IndexFeatureStore.storeName(indexName);
            this.version = version;
            this.counts = counts;
        }
        public IndexStoreInfo(StreamInput in) throws IOException {
            storeName = in.readString();
            indexName = in.readString();
            version = in.readVInt();
            counts = in.readMap(StreamInput::readString, StreamInput::readVInt);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(storeName);
            out.writeString(indexName);
            out.writeVInt(version);
            out.writeMap(counts, StreamOutput::writeString, StreamOutput::writeVInt);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            return builder.startObject()
                .field("store", storeName)
                .field("index", indexName)
                .field("version", version)
                .field("counts", counts)
                .endObject();
        }

        public String getStoreName() {
            return storeName;
        }

        public String getIndexName() {
            return indexName;
        }

        public int getVersion() {
            return version;
        }

        public Map<String, Integer> getCounts() {
            return counts;
        }
    }
}
