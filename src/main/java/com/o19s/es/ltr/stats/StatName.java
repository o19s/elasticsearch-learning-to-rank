package com.o19s.es.ltr.stats;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public enum StatName {
    PLUGIN_STATUS("status"),
    STORES("stores"),
    CACHE("cache");

    private final String name;

    StatName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static Set<String> getTopLevelStatNames() {
        Set<String> statNames = new HashSet<>();
        statNames.add(PLUGIN_STATUS.name);
        statNames.add(STORES.name);
        statNames.add(CACHE.name);
        return Collections.unmodifiableSet(statNames);
    }
}
