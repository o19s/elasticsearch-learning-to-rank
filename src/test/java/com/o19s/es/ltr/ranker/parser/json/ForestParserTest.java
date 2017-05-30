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

import com.o19s.es.ltr.ranker.parser.json.tree.ParsedForest;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static junit.framework.TestCase.assertEquals;

/**
 * Created by doug on 5/28/17.
 */
public class ForestParserTest extends JsonModelParsingTest {

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
                " \"ensemble\": [ " +
                "{\"split\": " +    split1 + "," +
                " \"weight\": 0.5, " +
                "\"id\": \"1\"}, " +
                "{\"split\": " +    split2 + "," +
                " \"weight\": 0.1, " +
                "\"id\": \"fuzzy-doggos\"}]} ";

        String ensemble2 = "{" +
                " \"ensemble\": [ " +
                "{\"split\": " +    split1 + "," +
                " \"weight\": 0.5, " +
                "\"id\": \"1\"}, " +
                "{\"split\": " +    split2 + "," +
                " \"weight\": 0.1, " +
                "\"id\": \"fuzzy-kittehs\"}]} ";



        String forest = "{\"forest\": [ " + ensemble1 +"," + ensemble2 + "]}";

        ParsedForest parsedForest = ParsedForest.parse(makeXContent(forest));
        assertEquals(parsedForest.ensembles().size(), 2);
        assertEquals(parsedForest.ensembles().get(0).trees().get(1).id(), "fuzzy-doggos");
        assertEquals(parsedForest.ensembles().get(1).trees().get(1).id(), "fuzzy-kittehs");


    }

//    @Ignore
//    public void testBigModel() throws IOException {
//        String contents = new String(Files.readAllBytes(Paths.get("/home/doug/ws/es-ltr/big-model.json")));
//        ParsedForest parsedForest = ParsedForest.parse(makeXContent(contents));
//        assertEquals(parsedForest.ensembles().size(), 10);
//    }

}
