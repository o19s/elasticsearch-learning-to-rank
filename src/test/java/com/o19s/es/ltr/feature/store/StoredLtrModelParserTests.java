/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.linear.LinearRanker;
import com.o19s.es.ltr.ranker.parser.LtrRankerParserFactory;
import org.apache.lucene.util.LuceneTestCase;
import org.elasticsearch.common.ParsingException;

import java.io.IOException;

import static org.elasticsearch.common.xcontent.NamedXContentRegistry.EMPTY;
import static org.elasticsearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

public class StoredLtrModelParserTests extends LuceneTestCase {
    private LtrRanker ranker;
    private LtrRankerParserFactory factory;

    public void setUp() throws Exception {
        super.setUp();
        ranker = new LinearRanker(new float[]{1F,2F,3F});
        factory = new LtrRankerParserFactory.Builder()
                .register("model/dummy", () -> (set, model) -> ranker)
                .build();
    }

    public void testParse() throws IOException {
        String modelString = "{\n" +
                " \"name\":\"my_model\",\n" +
                " \"feature_set\":" +
                StoredFeatureSetParserTests.generateRandomFeatureSet() +
                "," +
                " \"model\": {\n" +
                "   \"type\": \"model/dummy\",\n" +
                "   \"definition\": \"completely ignored\"\n"+
                " }" +
                "}";
        StoredLtrModel model = parse(modelString);
        assertEquals("my_model", model.name());
        assertSame(ranker, model.ranker());
        assertTrue(model.featureSet().size() > 0);
    }

    public void testParseFailureOnMissingName() throws IOException {
        String modelString = "{\n" +
                " \"feature_set\":" +
                StoredFeatureSetParserTests.generateRandomFeatureSet() +
                "," +
                " \"model\": {\n" +
                "   \"type\": \"model/dummy\",\n" +
                "   \"definition\": \"completely ignored\"\n"+
                " }" +
                "}";
        assertThat(expectThrows(ParsingException.class, () -> parse(modelString)).getMessage(),
            equalTo("Field [name] is mandatory"));
    }

    public void testParseFailureOnMissingModel() throws IOException {
        String modelString = "{\n" +
                " \"name\":\"my_model\",\n" +
                " \"feature_set\":" +
                StoredFeatureSetParserTests.generateRandomFeatureSet() +
                "}";
        assertThat(expectThrows(ParsingException.class, () -> parse(modelString)).getMessage(),
                equalTo("Field [model] is mandatory"));
    }

    public void testParseFailureOnMissingFeatureSet() throws IOException {
        String modelString = "{\n" +
                " \"name\":\"my_model\",\n" +
                " \"model\": {\n" +
                "   \"type\": \"model/dummy\",\n" +
                "   \"definition\": \"completely ignored\"\n"+
                " }" +
                "}";
        assertThat(expectThrows(ParsingException.class, () -> parse(modelString)).getMessage(),
                equalTo("Field [feature_set] is mandatory"));
    }

    public void testParseFailureOnBogusField() throws IOException {
        String modelString = "{\n" +
                " \"name\":\"my_model\",\n" +
                " \"bogusField\": \"foo\",\n" +
                " \"feature_set\":" +
                StoredFeatureSetParserTests.generateRandomFeatureSet() +
                "," +
                " \"model\": {\n" +
                "   \"type\": \"model/dummy\",\n" +
                "   \"definition\": \"completely ignored\"\n"+
                " }" +
                "}";
        assertThat(expectThrows(ParsingException.class, () -> parse(modelString)).getMessage(),
                containsString("bogusField"));
    }

    private StoredLtrModel parse(String missingName) throws IOException {
        return StoredLtrModel.parse(jsonXContent.createParser(EMPTY, missingName),
                factory);
    }

}