/*
 * Copyright [2016] Doug Turnbull
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.o19s.es.ltr.query;

import com.o19s.es.ltr.LtrQueryParserPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESIntegTestCase;

import java.util.Collection;
import java.util.Collections;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;

/**
 * Created by doug on 12/29/16.
 */
public class LtrEsQueryTests extends ESIntegTestCase {

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

    public void testSetupModel() {
        //assertAcked(prepareCreate("test1").setSettings(indexSettings()));
        assert(true);

    }

}
