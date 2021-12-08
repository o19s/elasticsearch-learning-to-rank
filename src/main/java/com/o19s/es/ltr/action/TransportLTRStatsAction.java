package com.o19s.es.ltr.action;

import com.o19s.es.ltr.action.LTRStatsAction.LTRStatsNodeRequest;
import com.o19s.es.ltr.action.LTRStatsAction.LTRStatsNodeResponse;
import com.o19s.es.ltr.action.LTRStatsAction.LTRStatsNodesRequest;
import com.o19s.es.ltr.action.LTRStatsAction.LTRStatsNodesResponse;
import com.o19s.es.ltr.stats.LTRStats;
import org.elasticsearch.action.FailedNodeException;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.nodes.TransportNodesAction;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TransportLTRStatsAction extends
        TransportNodesAction<LTRStatsNodesRequest, LTRStatsNodesResponse, LTRStatsNodeRequest, LTRStatsNodeResponse> {

    private final LTRStats ltrStats;

    @Inject
    public TransportLTRStatsAction(ThreadPool threadPool,
                                   ClusterService clusterService,
                                   TransportService transportService,
                                   ActionFilters actionFilters,
                                   LTRStats ltrStats) {
        super(LTRStatsAction.NAME, threadPool, clusterService, transportService,
                actionFilters, LTRStatsNodesRequest::new, LTRStatsNodeRequest::new,
                ThreadPool.Names.MANAGEMENT, LTRStatsNodeResponse.class);
        this.ltrStats = ltrStats;
    }

    @Override
    protected LTRStatsNodesResponse newResponse(LTRStatsNodesRequest request,
                                                List<LTRStatsNodeResponse> nodeResponses,
                                                List<FailedNodeException> failures) {
        Set<String> statsToBeRetrieved = request.getStatsToBeRetrieved();
        Map<String, Object> clusterStats =
                ltrStats.getClusterStats()
                        .entrySet()
                        .stream()
                        .filter(e -> statsToBeRetrieved.contains(e.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getStatValue()));

        return new LTRStatsNodesResponse(clusterService.getClusterName(), nodeResponses, failures, clusterStats);
    }

    @Override
    protected LTRStatsNodeRequest newNodeRequest(LTRStatsNodesRequest request) {
        return new LTRStatsNodeRequest(request);
    }

    @Override
    protected LTRStatsNodeResponse newNodeResponse(StreamInput in, DiscoveryNode node) throws IOException {
        return new LTRStatsNodeResponse(in);
    }

    @Override
    protected LTRStatsNodeResponse nodeOperation(LTRStatsNodeRequest request) {
        LTRStatsNodesRequest nodesRequest = request.getLTRStatsNodesRequest();
        Set<String> statsToBeRetrieved = nodesRequest.getStatsToBeRetrieved();

        Map<String, Object> statValues =
                ltrStats.getNodeStats()
                        .entrySet()
                        .stream()
                        .filter(e -> statsToBeRetrieved.contains(e.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getStatValue()));
        return new LTRStatsNodeResponse(clusterService.localNode(), statValues);
    }
}
