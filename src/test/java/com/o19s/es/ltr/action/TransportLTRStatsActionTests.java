package com.o19s.es.ltr.action;

import com.o19s.es.ltr.action.LTRStatsAction.LTRStatsNodeRequest;
import com.o19s.es.ltr.action.LTRStatsAction.LTRStatsNodeResponse;
import com.o19s.es.ltr.action.LTRStatsAction.LTRStatsNodesRequest;
import com.o19s.es.ltr.action.LTRStatsAction.LTRStatsNodesResponse;
import com.o19s.es.ltr.stats.LTRStat;
import com.o19s.es.ltr.stats.LTRStats;
import com.o19s.es.ltr.stats.StatName;
import com.o19s.es.ltr.stats.suppliers.CounterSupplier;
import org.elasticsearch.action.FailedNodeException;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.transport.TransportService;
import org.junit.Before;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;

public class TransportLTRStatsActionTests extends ESIntegTestCase {

    private TransportLTRStatsAction action;
    private LTRStats ltrStats;
    private Map<String, LTRStat<?>> statsMap;
    private StatName clusterStatName;
    private StatName nodeStatName;

    @Before
    public void setup() throws Exception {
        super.setUp();

        clusterStatName = StatName.PLUGIN_STATUS;
        nodeStatName = StatName.CACHE;

        statsMap = new HashMap<>();
        statsMap.put(clusterStatName.getName(), new LTRStat<>(false, new CounterSupplier()));
        statsMap.put(nodeStatName.getName(), new LTRStat<>(true, () -> "test"));

        ltrStats = new LTRStats(statsMap);

        action = new TransportLTRStatsAction(
                client().threadPool(),
                clusterService(),
                mock(TransportService.class),
                mock(ActionFilters.class),
                ltrStats
        );
    }

    public void testNewResponse() {
        String[] nodeIds = null;
        LTRStatsNodesRequest ltrStatsRequest = new LTRStatsNodesRequest(nodeIds);
        ltrStatsRequest.addAll(ltrStats.getStats().keySet());

        List<LTRStatsNodeResponse> responses = new ArrayList<>();
        List<FailedNodeException> failures = new ArrayList<>();

        LTRStatsNodesResponse ltrStatsResponse = action.newResponse(ltrStatsRequest, responses, failures);
        assertEquals(1, ltrStatsResponse.getClusterStats().size());
    }

    public void testNodeOperation() {
        String[] nodeIds = null;
        LTRStatsNodesRequest ltrStatsRequest = new LTRStatsNodesRequest(nodeIds);
        ltrStatsRequest.addAll(ltrStats.getStats().keySet());

        LTRStatsNodeResponse response = action.nodeOperation(new LTRStatsNodeRequest(ltrStatsRequest));

        Map<String, Object> stats = response.getStatsMap();

        assertEquals(1, stats.size());
    }
}
