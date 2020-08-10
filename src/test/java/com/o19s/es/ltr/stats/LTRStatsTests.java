package com.o19s.es.ltr.stats;

import org.elasticsearch.test.ESTestCase;
import org.junit.Before;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LTRStatsTests extends ESTestCase {

    private Map<String, LTRStat> statsMap;
    private LTRStats ltrStats;

    @Before
    public void setup() {
        statsMap = new HashMap<>();
        statsMap.put(StatName.PLUGIN_STATUS.getName(), new LTRStat(true, () -> "test"));
        statsMap.put(StatName.CACHE.getName(), new LTRStat(false, () -> "test"));
        ltrStats = new LTRStats(statsMap);
    }

    public void testGetStats() {
        Map<String, LTRStat> stats = ltrStats.getStats();
        assertEquals(stats.size(), statsMap.size());

        for (Map.Entry<String, LTRStat> stat : stats.entrySet()) {
            assertStatPresence(stat.getKey(), stat.getValue());
        }
    }

    public void testGetStat() {
        LTRStat stat = ltrStats.getStat(StatName.PLUGIN_STATUS.getName());
        assertStatPresence(StatName.PLUGIN_STATUS.getName(), stat);
    }

    private void assertStatPresence(String statName, LTRStat stat) {
        assertTrue(ltrStats.getStats().containsKey(statName));
        assertSame(ltrStats.getStats().get(statName), stat);
    }

    public void testGetNodeStats() {
        Map<String, LTRStat> stats = ltrStats.getStats();
        Set<LTRStat> nodeStats = new HashSet<>(ltrStats.getNodeStats().values());

        for (LTRStat stat : stats.values()) {
            assertTrue((stat.isClusterLevel() && !nodeStats.contains(stat)) ||
                    (!stat.isClusterLevel() && nodeStats.contains(stat)));
        }
    }

    public void testGetClusterStats() {
        Map<String, LTRStat> stats = ltrStats.getStats();
        Set<LTRStat> clusterStats = new HashSet<>(ltrStats.getClusterStats().values());

        for (LTRStat stat : stats.values()) {
            assertTrue((stat.isClusterLevel() && clusterStats.contains(stat)) ||
                    (!stat.isClusterLevel() && !clusterStats.contains(stat)));
        }
    }
}
