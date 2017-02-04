package com.o19s.es.ltr.query;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.common.ParseFieldMatcher;
import org.elasticsearch.common.bytes.BytesArray;
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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
        return new String(Files.readAllBytes(Paths.get("build/resources/test/model.txt")));
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
