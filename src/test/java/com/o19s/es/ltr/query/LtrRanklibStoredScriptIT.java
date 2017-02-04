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

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.common.ParseFieldMatcher;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.io.PathUtils;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.script.MockScriptEngine;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.test.ESIntegTestCase;

import javax.management.Query;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static jdk.nashorn.internal.runtime.regexp.joni.Syntax.Java;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;

/**
 * Created by doug on 2/3/17.
 */
public class LtrRanklibStoredScriptIT extends ESIntegTestCase {
    private static final int SCRIPT_MAX_SIZE_IN_BYTES = 2000000000;
    private static final String LANG = MockScriptEngine.NAME;

    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        return Settings.builder().put(super.nodeSettings(nodeOrdinal))
                .put(ScriptService.SCRIPT_MAX_SIZE_IN_BYTES.getKey(), SCRIPT_MAX_SIZE_IN_BYTES)
                .build();
    }

    protected String readLargeScript() throws IOException {
        //Path path = PathUtils.get("build/resources/test/model.txt");
        Path path = FileSystems.getDefault().getPath("build/resources/test/model.txt");
        //Path path = PathUtils.getDefaultFileSystem().getPath("build/resources/test/model.txt");
        System.out.println(path.toAbsolutePath());
        return new String(Files.readAllBytes(path), "UTF-8");
    }


    public void testLargeScript() throws IOException {

        String largeScript = readLargeScript();
        String storedScriptName = "foobar";

        JsonStringEncoder e = JsonStringEncoder.getInstance();
        largeScript = new String(e.quoteAsString(largeScript));


        assertAcked(client().admin().cluster().preparePutStoredScript()
                .setScriptLang("ranklib")
                .setId(storedScriptName)
                .setSource(new BytesArray("{\"script\":\"" + largeScript +  "\"}")));


        String scriptSpec = "{\"stored\": \"" + storedScriptName + "\"}";

        String ltrQuery =       "{  " +
                "   \"ltr\": {" +
                "      \"model\": " + scriptSpec + "," +
                "      \"features\": [        " +
                "         {\"match\": {         " +
                "            \"full_name\": \"bar\"     " +
                "         }},                   " +
                "         {\"match\": {         " +
                "            \"full_name\": \"foo\"     " +
                "         }}                   " +
                "      ]                      " +
                "   } " +
                "}";

        client().prepareIndex("test", "test", "1").setSource(
                "full_name", "marvel foo").get();


        client().prepareSearch("test").setQuery(QueryBuilders.wrapperQuery(ltrQuery)).get();

    }


    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Arrays.asList(LtrQueryParserPlugin.class);
    }



}
