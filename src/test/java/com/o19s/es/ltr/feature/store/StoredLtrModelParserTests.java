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
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
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

    public String getTestModelAsXContent() throws IOException {
        return "{\n" +
                " \"name\":\"my_model\",\n" +
                " \"feature_set\":" +
                StoredFeatureSetParserTests.generateRandomFeatureSet() +
                "," +
                " \"model\": {\n" +
                "   \"type\": \"model/dummy\",\n" +
                "   \"definition\": [\"completely ignored\"]\n"+
                " }" +
                "}";
    }

    public String getTestModelWithFeatureNorms() {
        String inlineFeatureSet = "{" +
                "\"name\": \"normed_model\"," +
                "  \"features\": [{" +
                "      \"name\": \"feature_1\"," +
                "      \"params\": [\"keywords\"]," +
                "      \"template\": {" +
                "        \"match\": {" +
                "          \"a_field\": {" +
                "            \"query\": \"test1\"" +
                "          }" +
                "        }" +
                "      }" +
                "    }," +
                "    {" +
                "      \"name\": \"feature_2\"," +
                "      \"params\": [\"keywords\"]," +
                "      \"template\": {" +
                "        \"match\": {" +
                "          \"esyww\": {" +
                "            \"query\": \"test1\"" +
                "    }}}}]}";

        String modelJson = "{\n" +
                " \"name\":\"my_model\",\n" +
                " \"feature_set\":" + inlineFeatureSet +
                "," +
                " \"model\": {\n" +
                "   \"type\": \"model/dummy\",\n" +
                "   \"definition\": \"completely ignored\",\n"+
                "   \"feature_normalizers\": {\n"+
                "     \"feature_1\": { \"standard\":" +
                "           {\"mean\": 1.25," +
                "            \"standard_deviation\": 0.25}}}" +
                " }" +
                "}";
        return modelJson;
    }

    public void testParse() throws IOException {
        StoredLtrModel model = parse(getTestModel());
        assertTestModel(model);
    }

    private void assertTestModel(StoredLtrModel model) throws IOException {
        assertEquals("my_model", model.name());
        assertEquals("model/dummy", model.rankingModelType());
        assertEquals("completely ignored", model.rankingModel());
        assertSame(ranker, model.compile(factory).ranker());
        assertTrue(model.featureSet().size() > 0);
    }

    private void assertTestModelAsXContent(StoredLtrModel model) throws IOException {
        assertEquals("my_model", model.name());
        assertEquals("model/dummy", model.rankingModelType());
        assertEquals("[\"completely ignored\"]", model.rankingModel());
        assertSame(ranker, model.compile(factory).ranker());
        assertTrue(model.featureSet().size() > 0);
    }

    public void testFeatureNormParsing() throws IOException {
        String modelJson = getTestModelWithFeatureNorms();
        StoredLtrModel model = parse(modelJson);

        assertEquals(model.getFeatureNormalizers().size(), 1);
        StandardFeatureNormalizer stdFtrNorm = (StandardFeatureNormalizer)model.getFeatureNormalizer("feature_1");
        assertNotNull(stdFtrNorm);
        assertEquals(stdFtrNorm.getStdDeviation(), 0.25, 0.001);
        assertEquals(stdFtrNorm.getMean(), 1.25, 0.001);

    }


    public void testToXContent() throws IOException {
        StoredLtrModel model = parse(getTestModel());

        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        String modelString = Strings.toString(model.toXContent(builder, ToXContent.EMPTY_PARAMS));
        StoredLtrModel modelReparsed = parse(modelString);
        assertTestModel(modelReparsed);

        model = parse(getTestModelAsXContent());
        builder = XContentFactory.contentBuilder(XContentType.JSON);
        modelString = Strings.toString(model.toXContent(builder, ToXContent.EMPTY_PARAMS));
        modelReparsed = parse(modelString);
        assertTestModelAsXContent(modelReparsed);
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

    public void testParseWithExternalName() throws IOException {
        String modelString = "{\n" +
                " \"feature_set\":" +
                StoredFeatureSetParserTests.generateRandomFeatureSet() +
                "," +
                " \"model\": {\n" +
                "   \"type\": \"model/dummy\",\n" +
                "   \"definition\": \"completely ignored\"\n"+
                " }" +
                "}";
        StoredLtrModel model = parse(modelString, "myModel");
        assertEquals("myModel", model.name());
    }

    public void testParseWithInconsistentName() throws IOException {
        String modelString = "{\n" +
                " \"name\": \"myModel\"," +
                " \"feature_set\":" +
                StoredFeatureSetParserTests.generateRandomFeatureSet() +
                "," +
                " \"model\": {\n" +
                "   \"type\": \"model/dummy\",\n" +
                "   \"definition\": \"completely ignored\"\n"+
                " }" +
                "}";
        assertThat(expectThrows(ParsingException.class, () -> parse(modelString, "myModel2")).getMessage(),
                equalTo("Invalid [name], expected [myModel2] but got [myModel]"));
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

    private StoredLtrModel parse(String jsonString) throws IOException {
        return parse(jsonString, null);
    }

    private StoredLtrModel parse(String jsonString, String name) throws IOException {
        return StoredLtrModel.parse(jsonXContent.createParser(EMPTY,
                LoggingDeprecationHandler.INSTANCE, jsonString), name);
    }
}