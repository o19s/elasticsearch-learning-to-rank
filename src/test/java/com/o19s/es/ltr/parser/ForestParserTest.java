package com.o19s.es.ltr.parser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.o19s.es.ltr.ranker.parser.tree.ParsedForest;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContentParser;
import org.junit.Test;

import java.io.IOException;

import static junit.framework.TestCase.assertEquals;

/**
 * Created by doug on 5/28/17.
 */
public class ForestParserTest extends JsonModelParsingTest {

    @Test
    public void testParsingForest() throws IOException {
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


        String ensemble1 = "{" +
                " \"ensemble\": [" +
                split1 + "," +
                split2 + "]}";

        String ensemble2 = "{" +
                " \"ensemble\": [" +
                split2 + "," +
                split1 + "]}";

        String forest = "{\"forest\": [ " + ensemble1 +"," + ensemble2 + "]}";

        ParsedForest parsedForest = ParsedForest.parse(makeXContent(forest));
        assertEquals(parsedForest.ensembles().size(), 2);


    }

}
