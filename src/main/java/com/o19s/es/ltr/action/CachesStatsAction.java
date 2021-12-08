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

import com.o19s.es.ltr.action.CachesStatsAction.CachesStatsNodesResponse;
import com.o19s.es.ltr.feature.store.index.Caches;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.ActionType;
import org.elasticsearch.action.FailedNodeException;
import org.elasticsearch.action.support.nodes.BaseNodeResponse;
import org.elasticsearch.action.support.nodes.BaseNodesRequest;
import org.elasticsearch.action.support.nodes.BaseNodesResponse;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CachesStatsAction extends ActionType<CachesStatsNodesResponse> {
    public static final String NAME = "cluster:admin/ltr/caches/stats";
    public static final CachesStatsAction INSTANCE = new CachesStatsAction();

    protected CachesStatsAction() {
        super(NAME, CachesStatsNodesResponse::new);
    }

    public static class CachesStatsNodesRequest extends BaseNodesRequest<CachesStatsNodesRequest> {
        public CachesStatsNodesRequest(StreamInput in) throws IOException {
            super(in);
        }

        public CachesStatsNodesRequest() {
            super((String[]) null);
        }
    }

    public static class CachesStatsNodesResponse extends BaseNodesResponse<CachesStatsNodeResponse> implements ToXContent {
        private StatDetails allStores;
        private Map<String, StatDetails> byStore;

        public CachesStatsNodesResponse(StreamInput in) throws IOException {
            super(in);
            allStores = new StatDetails(in);
            byStore = in.readMap(StreamInput::readString, StatDetails::new);
        }

        public CachesStatsNodesResponse(ClusterName clusterName, List<CachesStatsNodeResponse> nodes, List<FailedNodeException> failures) {
            super(clusterName, nodes, failures);
            allStores = new StatDetails();
            byStore = new HashMap<>();
            nodes.forEach((n) -> {
                allStores.doSum(n.allStores);
                n.byStore.forEach((k, v) -> byStore.merge(k, v, StatDetails::sum));
            });
        }

        @Override
        protected List<CachesStatsNodeResponse> readNodesFrom(StreamInput in) throws IOException {
            return in.readList(CachesStatsNodeResponse::new);
        }

        @Override
        protected void writeNodesTo(StreamOutput out, List<CachesStatsNodeResponse> nodes) throws IOException {
            out.writeList(nodes);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            allStores.writeTo(out);
            out.writeMap(byStore, StreamOutput::writeString, (o, s) -> s.writeTo(o));
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.field("all", allStores);
            builder.startObject("stores");
            for (Map.Entry<String, StatDetails> entry : byStore.entrySet()) {
                builder.field(entry.getKey(), entry.getValue());
            }
            builder.endObject();
            builder.startObject("nodes");
            for (CachesStatsNodeResponse resp : super.getNodes()) {
                builder.startObject(resp.getNode().getId());
                builder.field("name", resp.getNode().getName());
                builder.field("hostname", resp.getNode().getHostName());
                builder.field("stats", resp.allStores);
                builder.endObject();
            }
            builder.endObject();
            return builder;
        }

        public StatDetails getAll() {
            return allStores;
        }
    }
    public static class CachesStatsNodeResponse extends BaseNodeResponse {
        private StatDetails allStores;
        private Map<String, StatDetails> byStore;

        CachesStatsNodeResponse(DiscoveryNode node) {
            super(node);
            empty();
        }

        CachesStatsNodeResponse(StreamInput in) throws IOException {
            super(in);
            allStores = new StatDetails(in);
            byStore = in.readMap(StreamInput::readString, StatDetails::new);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            allStores.writeTo(out);
            out.writeMap(byStore, StreamOutput::writeString, (o, s) -> s.writeTo(o));
        }

        public void empty() {
            allStores = new StatDetails();
            byStore = new HashMap<>();
        }

        public CachesStatsNodeResponse initFromCaches(Caches caches) {
            allStores = new StatDetails();
            byStore = new HashMap<>();
            caches.perStoreStatsStream().forEach((en) -> {
                StatDetails details = new StatDetails(en.getValue());
                allStores.doSum(details);
                byStore.compute(en.getKey(), (k, v) -> StatDetails.sum(v, details));
            });
            return this;
        }

        public StatDetails getAllStores() {
            return allStores;
        }
    }
    public static class StatDetails implements Writeable, ToXContent {
        private Stat total;
        private Stat features;
        private Stat featuresets;
        private Stat models;

        StatDetails() {
            empty();
        }

        public StatDetails(Caches.PerStoreStats stats) {
            total = new Stat(stats.totalRam(), stats.totalCount());
            features = new Stat(stats.featureRam(), stats.featureCount());
            featuresets = new Stat(stats.featureSetRam(), stats.featureSetCount());
            models = new Stat(stats.modelRam(), stats.modelCount());
        }

        StatDetails(StreamInput in) throws IOException {
            readFrom(in);
        }

        public void readFrom(StreamInput in) throws IOException {
            total = new Stat(in);
            features = new Stat(in);
            featuresets = new Stat(in);
            models = new Stat(in);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            total.writeTo(out);
            features.writeTo(out);
            featuresets.writeTo(out);
            models.writeTo(out);
        }

        public void empty() {
            total = new Stat(0, 0);
            features = new Stat(0, 0);
            featuresets = new Stat(0, 0);
            models = new Stat(0, 0);
        }

        public static StatDetails sum(StatDetails one, StatDetails two) {
            if (one != null && two != null) {
                one.doSum(two);
                return one;
            } else if (one != null) {
                return one;
            } else if (two != null) {
                return two;
            }
            return null;
        }

        public void doSum(StatDetails other) {
            total.sum(other.total);
            features.sum(other.features);
            featuresets.sum(other.featuresets);
            models.sum(other.models);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            return builder.startObject()
                    .field("total", total)
                    .field("features", features)
                    .field("featuresets", featuresets)
                    .field("models", models)
                    .endObject();
        }

        public Stat getTotal() {
            return total;
        }

        public Stat getFeatures() {
            return features;
        }

        public Stat getFeaturesets() {
            return featuresets;
        }

        public Stat getModels() {
            return models;
        }

        public static class Stat implements Writeable, ToXContent {
            private long ram;
            private int count;

            public Stat(StreamInput in) throws IOException {
                ram = in.readVLong();
                count = in.readVInt();
            }

            public Stat(long ram, int count) {
                this.ram = ram;
                this.count = count;
            }

            public void sum(Stat other) {
                ram += other.ram;
                count += other.count;
            }

            public long getRam() {
                return ram;
            }

            public int getCount() {
                return count;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                out.writeVLong(ram);
                out.writeVInt(count);
            }

            @Override
            public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
                return builder.startObject()
                        .field("ram", ram)
                        .field("count", count)
                        .endObject();
            }
        }
    }

    public static class CachesStatsActionBuilder extends
        ActionRequestBuilder<CachesStatsNodesRequest, CachesStatsNodesResponse> {
        public CachesStatsActionBuilder(ElasticsearchClient client){
            super(client, INSTANCE, new CachesStatsNodesRequest());
        }
    }

}
