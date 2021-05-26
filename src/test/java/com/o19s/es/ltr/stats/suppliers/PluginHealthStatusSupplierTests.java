package com.o19s.es.ltr.stats.suppliers;

import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.indices.EmptySystemIndices;
import org.elasticsearch.test.ESIntegTestCase;
import org.junit.Before;

public class PluginHealthStatusSupplierTests extends ESIntegTestCase {
    private PluginHealthStatusSupplier pluginHealthStatusSupplier;

    @Before
    public void setup() {
        pluginHealthStatusSupplier =
                new PluginHealthStatusSupplier(clusterService(), new IndexNameExpressionResolver(new ThreadContext(Settings.EMPTY),
                        EmptySystemIndices.INSTANCE));
    }

    public void testPluginHealthStatusNoLtrStore() {
        assertEquals("green", pluginHealthStatusSupplier.get());
    }

    public void testPluginHealthStatus() {
        createIndex(IndexFeatureStore.DEFAULT_STORE,
                IndexFeatureStore.DEFAULT_STORE + "_custom1",
                IndexFeatureStore.DEFAULT_STORE + "_custom2");
        flush();
        String status = pluginHealthStatusSupplier.get();
        assertTrue(status.equals("green") || status.equals("yellow"));
    }
}
