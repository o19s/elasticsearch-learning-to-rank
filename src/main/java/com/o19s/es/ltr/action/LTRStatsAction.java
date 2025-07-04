package com.o19s.es.ltr.action;

import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.ActionType;
import org.elasticsearch.action.FailedNodeException;
import org.elasticsearch.action.support.nodes.BaseNodeResponse;
import org.elasticsearch.action.support.nodes.BaseNodesRequest;
import org.elasticsearch.action.support.nodes.BaseNodesResponse;
import org.elasticsearch.client.internal.ElasticsearchClient;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.transport.TransportRequest;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.ToXContentFragment;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LTRStatsAction extends ActionType<LTRStatsAction.LTRStatsNodesResponse> {
    public static final String NAME = "cluster:admin/ltr/stats";
    public static final LTRStatsAction INSTANCE = new LTRStatsAction();

    public LTRStatsAction() {
        super(NAME);
    }

    public static class LTRStatsRequestBuilder
            extends ActionRequestBuilder<LTRStatsNodesRequest, LTRStatsNodesResponse> {
        private static final String[] nodeIds = null;

        public LTRStatsRequestBuilder(ElasticsearchClient client) {
            super(client, INSTANCE, new LTRStatsNodesRequest(nodeIds));
        }
    }

    public static class LTRStatsNodeRequest extends TransportRequest {
        private final LTRStatsNodesRequest nodesRequest;

        public LTRStatsNodeRequest(StreamInput in) throws IOException {
            super(in);
            this.nodesRequest = new LTRStatsNodesRequest(in);
        }

        public LTRStatsNodeRequest(LTRStatsNodesRequest nodesRequest) {
            this.nodesRequest = nodesRequest;
        }

        public LTRStatsNodesRequest getLTRStatsNodesRequest() {
            return nodesRequest;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            nodesRequest.writeToStream(out);
        }
    }

    public static class LTRStatsNodeResponse extends BaseNodeResponse implements ToXContentFragment {

        private final Map<String, Object> statsMap;

        LTRStatsNodeResponse(StreamInput in) throws IOException {
            super(in);
            this.statsMap = in.readMap(StreamInput::readString, StreamInput::readGenericValue);
        }

        LTRStatsNodeResponse(DiscoveryNode node, Map<String, Object> statsToValues) {
            super(node);
            this.statsMap = statsToValues;
        }

        public Map<String, Object> getStatsMap() {
            return statsMap;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeMap(statsMap, StreamOutput::writeString, StreamOutput::writeGenericValue);
        }

        public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
            for (Map.Entry<String, Object> stat : statsMap.entrySet()) {
                builder.field(stat.getKey(), stat.getValue());
            }

            return builder;
        }
    }

    public static class LTRStatsNodesRequest extends BaseNodesRequest {

        public static final String ALL_STATS_KEY = "_all";

        private Set<String> statsToBeRetrieved;

        public LTRStatsNodesRequest(StreamInput in) throws IOException {
            super((String[])null);
            statsToBeRetrieved = in.readCollectionAsSet(StreamInput::readString);
        }

        public LTRStatsNodesRequest(String[] nodeIds) {
            super(nodeIds);
            statsToBeRetrieved = new HashSet<>();
        }

        public void setStatsToBeRetrieved(Set<String> statsToBeRetrieved) {
            this.statsToBeRetrieved = statsToBeRetrieved;
        }

        public Set<String> getStatsToBeRetrieved() {
            return statsToBeRetrieved;
        }

        public void writeToStream(StreamOutput out) throws IOException {
            out.writeStringCollection(statsToBeRetrieved);
        }
    }

    public static class LTRStatsNodesResponse extends BaseNodesResponse<LTRStatsNodeResponse> implements ToXContent {
        private static final String NODES_KEY = "nodes";
        private final Map<String, Object> clusterStats;

        public LTRStatsNodesResponse(StreamInput in) throws IOException {
            super(in);
            clusterStats = in.readGenericMap();
        }

        public LTRStatsNodesResponse(ClusterName clusterName, List<LTRStatsNodeResponse> nodeResponses,
                                     List<FailedNodeException> failures, Map<String, Object> clusterStats) {
            super(clusterName, nodeResponses, failures);
            this.clusterStats = clusterStats;
        }

        public Map<String, Object> getClusterStats() {
            return clusterStats;
        }

        @Override
        protected List<LTRStatsNodeResponse> readNodesFrom(StreamInput in) throws IOException {
            return in.readCollectionAsList(LTRStatsNodeResponse::new);
        }

        @Override
        protected void writeNodesTo(StreamOutput out, List<LTRStatsNodeResponse> nodeResponses) throws IOException {
            out.writeCollection(nodeResponses);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeGenericMap(clusterStats);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            for (Map.Entry<String, Object> clusterStat : clusterStats.entrySet()) {
                builder.field(clusterStat.getKey(), clusterStat.getValue());
            }

            builder.startObject(NODES_KEY);
            for (LTRStatsNodeResponse ltrStats : getNodes()) {
                builder.startObject(ltrStats.getNode().getId());
                ltrStats.toXContent(builder, params);
                builder.endObject();
            }
            builder.endObject();
            return builder;
        }
    }
}
