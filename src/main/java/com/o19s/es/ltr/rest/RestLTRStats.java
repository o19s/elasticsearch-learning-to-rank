package com.o19s.es.ltr.rest;

import com.o19s.es.ltr.action.LTRStatsAction;
import com.o19s.es.ltr.action.LTRStatsAction.LTRStatsNodesRequest;
import com.o19s.es.ltr.stats.LTRStats;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.Strings;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestActions;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

/**
 * APIs to retrieve stats on the plugin usage and performance.
 */
public class RestLTRStats extends BaseRestHandler {
    public static final String LTR_STATS_BASE_URI = "/_ltr/stats";
    private static final String NAME = "learning_to_rank_stats";
    private final LTRStats ltrStats;

    public RestLTRStats(LTRStats ltrStats) {
        this.ltrStats = ltrStats;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public List<Route> routes() {
        return unmodifiableList(asList(
                new Route(RestRequest.Method.GET, LTR_STATS_BASE_URI),
                new Route(RestRequest.Method.GET, LTR_STATS_BASE_URI + "/{stat}"),
                new Route(RestRequest.Method.GET, LTR_STATS_BASE_URI + "/nodes/{nodeId}"),
                new Route(RestRequest.Method.GET, LTR_STATS_BASE_URI + "/{stat}/nodes/{nodeId}")
        ));
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client)
            throws IOException {
        LTRStatsNodesRequest ltrStatsRequest = getRequest(request);
        return (channel) -> client.execute(LTRStatsAction.INSTANCE,
                ltrStatsRequest,
                new RestActions.NodesResponseRestListener(channel));
    }

    private LTRStatsNodesRequest getRequest(RestRequest request) {
        Set<String> validStats = ltrStats.getStats().keySet();
        LTRStatsNodesRequest ltrStatsRequest =
                new LTRStatsNodesRequest(splitCommaSeparatedParam(request, "nodeId"));
        ltrStatsRequest.timeout(request.param("timeout"));

        String[] stats = splitCommaSeparatedParam(request, "stat");
        Set<String> statsSet = stats != null ? new HashSet<>(Arrays.asList(stats)) : Collections.emptySet();

        if (isAllStatsRequested(statsSet)) {
            ltrStatsRequest.addAll(validStats);
        } else {
            if (statsSet.contains(LTRStatsNodesRequest.ALL_STATS_KEY)) {
                throw new IllegalArgumentException(
                        "Request " + request.path() + " contains " + LTRStatsNodesRequest.ALL_STATS_KEY + " and individual stats");
            }
            Set<String> invalidStats = new HashSet<>();
            for (String stat : statsSet) {
                if (validStats.contains(stat)) {
                    ltrStatsRequest.addStat(stat);
                } else {
                    invalidStats.add(stat);
                }
            }
            if (!invalidStats.isEmpty()) {
                throw new IllegalArgumentException(
                        unrecognized(request, invalidStats, ltrStatsRequest.getStatsToBeRetrieved(), "stat"));
            }
        }
        return ltrStatsRequest;
    }

    private boolean isAllStatsRequested(Set<String> statsSet) {
        return statsSet.isEmpty()
                || (statsSet.size() == 1 && statsSet.contains(LTRStatsNodesRequest.ALL_STATS_KEY));
    }

    private String[] splitCommaSeparatedParam(RestRequest request, String paramName) {
        String[] arr = null;
        String str = request.param(paramName);
        if (!Strings.isEmpty(str)) {
            arr = str.split(",");
        }
        return arr;
    }
}
