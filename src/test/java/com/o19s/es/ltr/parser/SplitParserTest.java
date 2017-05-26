package com.o19s.es.ltr.parser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.o19s.es.ltr.ranker.parser.tree.ParsedSplit;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.common.xcontent.json.JsonXContentParser;
import org.junit.Test;

import java.io.IOException;

/**
 * Created by doug on 5/26/17.
 */
public class SplitParserTest {

    JsonFactory jsonFactory = new JsonFactory();

    XContentParser makeXContent(String jsonStr) throws IOException {
        JsonParser jsonParser = jsonFactory.createParser(jsonStr);
        return new JsonXContentParser(NamedXContentRegistry.EMPTY, jsonParser);
    }

    @Test
    public void testBasicSplit() throws IOException {
        String split = "{" +
                " \"feature\": \"foo\"," +
                " \"threshold\": 0.5,  " +
                " \"splits\": [   " +
                "    {\"output\": 5.0},"+
                "    {\"output\": 1.0}"+
                "]}";


        ParsedSplit ps = ParsedSplit.parse(makeXContent(split));
        assert(ps.getFeature().equals("foo"));
        assert(ps.getThreshold() == 0.5);
        assert(ps.getLhs() != null);
        assert(ps.getRhs() != null);
        assert(ps.getLhs().getOutput() == 5.0);
        assert(ps.getRhs().getOutput() == 1.0);

    }

    @Test
    public void testNestedSplit() throws IOException {
        String split = "{" +
                " \"feature\": \"foo\"," +
                " \"threshold\": 0.5,  " +
                " \"splits\": [   " +
                "    {\"split\":  " +
                "       {\"feature\": \"bar\"," +
                "        \"threshold\": 12.0," +
                "        \"splits\": [" +
                "           {\"output\": 100.0}," +
                "           {\"output\": 500.0}" +
                "       ]" +
                "    }},"+
                "    {\"output\": 1.0}"+
                "]}";


        ParsedSplit ps = ParsedSplit.parse(makeXContent(split));
        assert(ps.getFeature().equals("foo"));
        assert(ps.getThreshold() == 0.5);
        assert(ps.getLhs() != null);
        assert(ps.getRhs() != null);
        assert(ps.getLhs().getThreshold() == 12.0);
        assert(ps.getLhs().getLhs() != null);
        assert(ps.getLhs().getRhs() != null);
        assert(ps.getLhs().getLhs().getOutput() == 100.0);
        assert(ps.getLhs().getRhs().getOutput() == 500.0);
        assert(ps.getRhs().getOutput() == 1.0);

    }
}
