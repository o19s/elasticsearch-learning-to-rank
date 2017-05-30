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

import com.o19s.es.ltr.ranker.parser.json.tree.ParsedEnsemble;
import org.junit.Test;

import java.io.IOException;

import static junit.framework.TestCase.assertEquals;

/**
 * Created by doug on 5/26/17.
 */
public class EnsembleParserTest extends JsonModelParsingTest {

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
                " \"ensemble\": [ " +
                        "{\"split\": " +    split1 + "," +
                        " \"weight\": 0.5, " +
                         "\"id\": \"1\"}, " +
                        "{\"split\": " +    split2 + "," +
                        " \"weight\": 0.1, " +
                         "\"id\": \"fuzzy-kittehs\"}]} ";

        ParsedEnsemble ens = ParsedEnsemble.parse(makeXContent(ensemble));
        assertEquals(ens.trees().get(0).weight(), 0.5, 0.01);
        assertEquals(ens.trees().get(0).id(), "1");

        assertEquals(ens.trees().get(1).weight(), 0.1, 0.01);
        assertEquals(ens.trees().get(1).id(), "fuzzy-kittehs");

    }

}
