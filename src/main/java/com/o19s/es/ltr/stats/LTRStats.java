package com.o19s.es.ltr.stats;

import java.util.Map;
import java.util.stream.Collectors;


/**
 * This class is the main entry-point for access to the stats that the LTR plugin keeps track of.
 */
public class LTRStats {
    private final Map<String, LTRStat> stats;


    public LTRStats(Map<String, LTRStat> stats) {
        this.stats = stats;
    }

    public Map<String, LTRStat> getStats() {
        return stats;
    }

    public LTRStat getStat(String key) throws IllegalArgumentException {
        LTRStat stat = stats.get(key);
        if (stat == null) {
            throw new IllegalArgumentException("Stat=\"" + key + "\" does not exist");
        }
        return stat;
    }

    public Map<String, LTRStat> getNodeStats() {
        return getClusterOrNodeStats(false);
    }

    public Map<String, LTRStat> getClusterStats() {
        return getClusterOrNodeStats(true);
    }

    private Map<String, LTRStat> getClusterOrNodeStats(Boolean isClusterStats) {
        return stats.entrySet()
                .stream()
                .filter(e -> e.getValue().isClusterLevel() == isClusterStats)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
