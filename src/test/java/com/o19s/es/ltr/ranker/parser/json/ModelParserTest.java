package com.o19s.es.ltr.ranker.parser.json;

import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTree;
import org.junit.Test;

import java.io.IOException;

import static junit.framework.TestCase.assertNotNull;

/**
 * Created by doug on 5/29/17.
 */
public class ModelParserTest extends JsonModelParsingTest {

    @Test
    public void testMartParsing() throws IOException {
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
                "    }}," +
                "    \"rhs\": {\"output\": 1.0}" +
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
                "    }}," +
                "    \"rhs\": {\"output\": 1.0}" +
                "}";


        String mart = "{\"mart\": {" +
                " \"ensemble\": [ " +
                "{\"split\": " + split1 + "," +
                " \"weight\": 0.5, " +
                "\"id\": \"1\"}, " +
                "{\"split\": " + split2 + "," +
                " \"weight\": 0.1, " +
                "\"id\": \"fuzzy-kittehs\"}]}} ";

        Model parsedModel = Model.parse(makeXContent(mart));
        LtrRanker ranker = parsedModel.toModel(new MockFeatureSet());
        NaiveAdditiveDecisionTree martRanker = (NaiveAdditiveDecisionTree)ranker;
        assertNotNull(martRanker);
    }


}
