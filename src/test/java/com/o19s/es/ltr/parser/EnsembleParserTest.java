package com.o19s.es.ltr.parser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.o19s.es.ltr.ranker.parser.tree.ParsedEnsemble;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContentParser;
import org.junit.Test;

import java.io.IOException;

/**
 * Created by doug on 5/26/17.
 */
public class EnsembleParserTest {

    JsonFactory jsonFactory = new JsonFactory();

    XContentParser makeXContent(String jsonStr) throws IOException {
        JsonParser jsonParser = jsonFactory.createParser(jsonStr);
        return new JsonXContentParser(NamedXContentRegistry.EMPTY, jsonParser);
    }

    @Test
    public void testEnsembleParsing() throws IOException {
        String split1 = "{" +
                " \"feature\": \"foo\"," +
                " \"threshold\": 0.5,  " +
                " \"lhs\": {\"split\":  " +
                "           {\"feature\": \"bar\"," +
                "            \"threshold\": 12.0," +
                "            \"lhs\": " +
                "               {\"output\": 100.0}," +
                "            \"rhs\": " +
                "               {\"output\": 500.0}" +
                "       " +
                "    }},"+
                "    \"rhs\": {\"output\": 1.0}"+
                "}";

        String split2 = "{" +
                " \"feature\": \"foo\"," +
                " \"threshold\": 0.5,  " +
                " \"lhs\": {\"split\":  " +
                "           {\"feature\": \"bar\"," +
                "            \"threshold\": 12.0," +
                "            \"lhs\": " +
                "               {\"output\": 100.0}," +
                "            \"rhs\": " +
                "               {\"output\": 500.0}" +
                "       " +
                "    }},"+
                "    \"rhs\": {\"output\": 1.0}"+
                "}";


        String ensemble = "{" +
                " \"ensemble\": [" +
                         split1 + "," +
                         split2 + "]}";

        ParsedEnsemble ens = ParsedEnsemble.parseEnsemble(makeXContent(ensemble));
        assert(ens.splits().get(0).getThreshold() == 0.5);
    }

}
