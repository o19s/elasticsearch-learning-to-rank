package com.o19s.es.ltr.rest;

import com.o19s.es.ltr.action.LTRStatsAction;
import com.o19s.es.ltr.action.LTRStatsAction.LTRStatsNodesRequest;
import com.o19s.es.ltr.stats.StatName;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestActions;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

/**
 * APIs to retrieve stats on the plugin usage and performance.
 */
public class RestLTRStats extends BaseRestHandler {
    public static final String LTR_STATS_BASE_URI = "/_ltr/_stats";
    private static final String NAME = "learning_to_rank_stats";

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
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client)
            throws IOException {
        LTRStatsNodesRequest ltrStatsRequest = getRequest(request);
        return (channel) -> client.execute(LTRStatsAction.INSTANCE,
                ltrStatsRequest,
                new RestActions.NodesResponseRestListener<>(channel));
    }

    private LTRStatsNodesRequest getRequest(RestRequest request) {
        LTRStatsNodesRequest ltrStatsRequest = new LTRStatsNodesRequest(
                splitCommaSeparatedParam(request, "nodeId").orElse(null));
        ltrStatsRequest.timeout(request.param("timeout"));

        List<String> requestedStats =
                splitCommaSeparatedParam(request, "stat")
                        .map(Arrays::asList)
                        .orElseGet(Collections::emptyList);

        Set<String> validStats = StatName.getTopLevelStatNames();
        if (isAllStatsRequested(requestedStats)) {
            ltrStatsRequest.setStatsToBeRetrieved(validStats);
        } else {
            ltrStatsRequest.setStatsToBeRetrieved(getStatsToBeRetrieved(request, validStats, requestedStats));
        }

        return ltrStatsRequest;
    }

    private Set<String> getStatsToBeRetrieved(
            RestRequest request, Set<String> validStats, List<String> requestedStats) {
        if (requestedStats.contains(LTRStatsNodesRequest.ALL_STATS_KEY)) {
            throw new IllegalArgumentException(
                    String.format(Locale.ROOT, "Request %s contains both %s and individual stats",
                            request.path(), LTRStatsNodesRequest.ALL_STATS_KEY));
        }

        Set<String> invalidStats =
                requestedStats.stream()
                        .filter(s -> !validStats.contains(s))
                        .collect(Collectors.toSet());

        if (!invalidStats.isEmpty()) {
            throw new IllegalArgumentException(
                    unrecognized(request, invalidStats, new HashSet<>(requestedStats), "stat"));
        }
        return new HashSet<>(requestedStats);
    }

    private boolean isAllStatsRequested(List<String> statsSet) {
        return statsSet.isEmpty()
                || (statsSet.size() == 1 && statsSet.contains(LTRStatsNodesRequest.ALL_STATS_KEY));
    }

    private Optional<String[]> splitCommaSeparatedParam(RestRequest request, String paramName) {
        return Optional.ofNullable(request.param(paramName))
                .map(s -> s.split(","));
    }
}
