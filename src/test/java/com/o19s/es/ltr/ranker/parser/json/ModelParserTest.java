/*
 * Copyright [2017] OpenSource Connections
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
 */
package com.o19s.es.ltr.ranker.parser.json;

import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.ranker.DenseFeatureVector;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTree;
import com.o19s.es.ltr.ranker.linear.LinearRanker;
import org.junit.Test;

import java.io.IOException;

import static junit.framework.Assert.assertEquals;
import static junit.framework.TestCase.assertNotNull;

/**
 * Created by doug on 5/29/17.
 */
public class ModelParserTest extends JsonModelParsingTest {

    public void testLinearParsing() throws IOException {
        String basicLinearModel = "{\n" +
                "  \"linear\": {\n" +
                "    \"y-intercept\": 0.5,\n" +
                "    \"weights\": [{\n" +
                "        \"feature\": \"foo\",\n" +
                "        \"weight\": 2\n" +
                "      },\n" +
                "      {\n" +
                "        \"feature\": \"bar\",\n" +
                "        \"weight\": 10\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}";
        FeatureSet fs = new MockFeatureSet();
        int fooOrd = fs.featureOrdinal("foo");
        int barOrd = fs.featureOrdinal("bar");


        Model parsedModel = Model.parse(makeXContent(basicLinearModel), fs);

        LtrRanker ranker = parsedModel.model();
        LinearRanker linRanker = (LinearRanker)ranker;
        assertNotNull(linRanker);


        LtrRanker.FeatureVector fv  = new DenseFeatureVector(2);
        fv.setFeatureScore(fooOrd, 0.5f);
        fv.setFeatureScore(barOrd, 12.5f);

        float score = ranker.score(fv);
        assertEquals(score, 0.5 + (0.5f * 2.0f) + (12.5f*10), 0.1);
    }


    public void testMartParsing() throws IOException {
        String split1 = "{" +
                " \"feature\": \"foo\"," +
                " \"threshold\": 0.5,  " +
                " \"lhs\": {\"split\":  " +
                "           {\"feature\": \"foo\"," +
                "            \"threshold\": 1.0," +
                "            \"lhs\": " +
                "               {\"output\": 20.0}," +
                "            \"rhs\": " +
                "               {\"output\": 30.0}" +
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

        FeatureSet fs = new MockFeatureSet();
        Model parsedModel = Model.parse(makeXContent(mart), fs);

        LtrRanker ranker = parsedModel.model();
        NaiveAdditiveDecisionTree martRanker = (NaiveAdditiveDecisionTree)ranker;
        assertNotNull(martRanker);

        int fooOrd = fs.featureOrdinal("foo");
        int barOrd = fs.featureOrdinal("bar");


        LtrRanker.FeatureVector fv  = new DenseFeatureVector(2);
        fv.setFeatureScore(fooOrd, 0.5f);
        fv.setFeatureScore(barOrd, 12.5f);

        float score = ranker.score(fv);
        assertEquals(score, 20.0f*0.5f + 500.0f*0.1f);



    }


}
