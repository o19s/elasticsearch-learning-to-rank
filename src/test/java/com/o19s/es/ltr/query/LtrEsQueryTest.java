package com.o19s.es.ltr.query;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESIntegTestCase;

import java.util.Collection;
import java.util.Collections;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;

/**
 * Created by doug on 12/29/16.
 */
public class LtrEsQueryTest extends ESIntegTestCase {

    String simpleModel = "## LambdaMART\n" +
            "## No. of trees = 1\n" +
            "## No. of leaves = 10\n" +
            "## No. of threshold candidates = 256\n" +
            "## Learning rate = 0.1\n" +
            "## Stop early = 100\n" +
            "\n" +
            "<ensemble>\n" +
            "\t<tree id=\"1\" weight=\"0.1\">\n" +
            "\t\t<split>\n" +
            "\t\t\t<feature> 1 </feature>\n" +
            "\t\t\t<threshold> 0.45867884 </threshold>\n" +
            "\t\t\t<split pos=\"left\">\n" +
            "\t\t\t\t<feature> 1 </feature>\n" +
            "\t\t\t\t<threshold> 0.0 </threshold>\n" +
            "\t\t\t\t<split pos=\"left\">\n" +
            "\t\t\t\t\t<output> -2.0 </output>\n" +
            "\t\t\t\t</split>\n" +
            "\t\t\t\t<split pos=\"right\">\n" +
            "\t\t\t\t\t<output> -1.3413081169128418 </output>\n" +
            "\t\t\t\t</split>\n" +
            "\t\t\t</split>\n" +
            "\t\t\t<split pos=\"right\">\n" +
            "\t\t\t\t<feature> 1 </feature>\n" +
            "\t\t\t\t<threshold> 0.6115718 </threshold>\n" +
            "\t\t\t\t<split pos=\"left\">\n" +
            "\t\t\t\t\t<output> 0.3089442849159241 </output>\n" +
            "\t\t\t\t</split>\n" +
            "\t\t\t\t<split pos=\"right\">\n" +
            "\t\t\t\t\t<output> 2.0 </output>\n" +
            "\t\t\t\t</split>\n" +
            "\t\t\t</split>\n" +
            "\t\t</split>\n" +
            "\t</tree>" +
            "</ensemble>";

    /**
     * Returns a collection of plugins that should be loaded on each node.
     */
    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.singletonList(LtrQueryParserPlugin.class);
    }

    @Override
    public Settings indexSettings() {
        Settings.Builder builder = Settings.builder();
        Settings parentSettings = super.indexSettings();
        builder.put(parentSettings);
        builder.put("index.ltr.models.orange", simpleModel);
        return builder.build();
    }

    public void testSetupModel() {
        assertAcked(prepareCreate("test1").setSettings(indexSettings()));

    }

}
