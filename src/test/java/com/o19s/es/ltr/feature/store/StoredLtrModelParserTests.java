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
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;

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

    public String getTestModel() throws IOException {
        return "{\n" +
                " \"name\":\"my_model\",\n" +
                " \"feature_set\":" +
                StoredFeatureSetParserTests.generateRandomFeatureSet() +
                "," +
                " \"model\": {\n" +
                "   \"type\": \"model/dummy\",\n" +
                "   \"definition\": \"completely ignored\"\n"+
                " }" +
                "}";
    }
    public void testParse() throws IOException {
        StoredLtrModel model = parse(getTestModel());
        assertTestModel(model);
    }

    private void assertTestModel(StoredLtrModel model) {
        assertEquals("my_model", model.name());
        assertEquals("model/dummy", model.rankingModelType());
        assertEquals("completely ignored", model.rankingModel());
        assertSame(ranker, model.compile(factory).ranker());
        assertTrue(model.featureSet().size() > 0);
    }

    public void testToXContent() throws IOException {
        StoredLtrModel model = parse(getTestModel());

        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        String modelString = model.toXContent(builder, ToXContent.EMPTY_PARAMS).bytes().utf8ToString();
        StoredLtrModel modelReparsed = parse(modelString);
        assertTestModel(modelReparsed);
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
        return StoredLtrModel.parse(jsonXContent.createParser(EMPTY, missingName));
    }

}