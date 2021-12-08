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
import com.o19s.es.ltr.ranker.normalizer.FeatureNormalizingRanker;
import com.o19s.es.ltr.ranker.normalizer.MinMaxFeatureNormalizer;
import com.o19s.es.ltr.ranker.normalizer.StandardFeatureNormalizer;
import com.o19s.es.ltr.ranker.parser.LtrRankerParserFactory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.LuceneTestCase;
import org.elasticsearch.Version;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.Randomness;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.ByteBufferStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentType;

import java.io.IOException;
import java.util.Base64;

import static org.elasticsearch.xcontent.NamedXContentRegistry.EMPTY;
import static org.elasticsearch.xcontent.json.JsonXContent.jsonXContent;
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

    public String getSimpleFeatureSet() {
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
        return inlineFeatureSet;
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

    public void testCompileFeatureNorms() throws IOException {
        String modelJson = "{\n" +
                " \"name\":\"my_model\",\n" +
                " \"feature_set\":" + getSimpleFeatureSet() +
                "," +
                " \"model\": {\n" +
                "   \"type\": \"model/dummy\",\n" +
                "   \"definition\": \"completely ignored\",\n" +
                "   \"feature_normalizers\": {\n" +
                "     \"feature_1\": { \"standard\":" +
                "           {\"mean\": 1.25," +
                "            \"standard_deviation\": 0.25}}}" +
                " }" +
                "}";
        StoredLtrModel model = parse(modelJson);
        CompiledLtrModel compiledModel = model.compile(factory);

        LtrRanker ranker = compiledModel.ranker();
        assertEquals(ranker.getClass(), FeatureNormalizingRanker.class);

        FeatureNormalizingRanker normRanker = (FeatureNormalizingRanker)ranker;

        LtrRanker.FeatureVector ftrVector = normRanker.newFeatureVector(null);

        ftrVector.setFeatureScore(0, 1.25f);
        ftrVector.setFeatureScore(1, 1.25f);

        float ftr0Before = ftrVector.getFeatureScore(0);
        float ftr1Before = ftrVector.getFeatureScore(1);

        normRanker.score(ftrVector);

    }

    public void testFeatureStdNormParsing() throws IOException {
        String modelJson = "{\n" +
                " \"name\":\"my_model\",\n" +
                " \"feature_set\":" + getSimpleFeatureSet() +
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

        StoredLtrModel model = parse(modelJson);

        StoredFeatureNormalizers ftrNormSet = model.getFeatureNormalizers();
        assertNotNull(ftrNormSet);

        StandardFeatureNormalizer stdFtrNorm = (StandardFeatureNormalizer)ftrNormSet.getNormalizer("feature_1");
        assertNotNull(stdFtrNorm);

        float expectedMean = 1.25f;
        float expectedStdDev = 0.25f;

        float testVal = Randomness.get().nextFloat();
        float expectedNormalized = (testVal - expectedMean) / expectedStdDev;
        assertEquals(expectedNormalized, stdFtrNorm.normalize(testVal), 0.01);

        StoredLtrModel reparsedModel = reparseModel(model);
        ftrNormSet = reparsedModel.getFeatureNormalizers();
        stdFtrNorm = (StandardFeatureNormalizer)ftrNormSet.getNormalizer("feature_1");

        testVal = Randomness.get().nextFloat();
        expectedNormalized = (testVal - expectedMean) / expectedStdDev;
        assertEquals(expectedNormalized, stdFtrNorm.normalize(testVal), 0.01);
        assertEquals(reparsedModel, model);
        assertEquals(reparsedModel.hashCode(), model.hashCode());
    }

    public void testFeatureMinMaxParsing() throws IOException {
        String modelJson = "{\n" +
                " \"name\":\"my_model\",\n" +
                " \"feature_set\":" + getSimpleFeatureSet() +
                "," +
                " \"model\": {\n" +
                "   \"type\": \"model/dummy\",\n" +
                "   \"definition\": \"completely ignored\",\n"+
                "   \"feature_normalizers\": {\n"+
                "     \"feature_2\": { \"min_max\":" +
                "           {\"minimum\": 0.05," +
                "            \"maximum\": 1.25}}}" +
                " }" +
                "}";

        StoredLtrModel model = parse(modelJson);

        StoredFeatureNormalizers ftrNormSet = model.getFeatureNormalizers();
        assertNotNull(ftrNormSet);

        MinMaxFeatureNormalizer minMaxFtrNorm = (MinMaxFeatureNormalizer)ftrNormSet.getNormalizer("feature_2");
        float expectedMin = 0.05f;
        float expectedMax = 1.25f;

        float testVal = Randomness.get().nextFloat();
        float expectedNormalized = (testVal - expectedMin) / (expectedMax - expectedMin);
        assertEquals(expectedNormalized, minMaxFtrNorm.normalize(testVal), 0.01);

        StoredLtrModel reparsedModel = reparseModel(model);
        ftrNormSet = reparsedModel.getFeatureNormalizers();
        minMaxFtrNorm = (MinMaxFeatureNormalizer)ftrNormSet.getNormalizer("feature_2");

        testVal = Randomness.get().nextFloat();
        expectedNormalized = (testVal - expectedMin) / (expectedMax - expectedMin);
        assertEquals(expectedNormalized, minMaxFtrNorm.normalize(testVal), 0.01);
        assertEquals(reparsedModel, model);
        assertEquals(reparsedModel.hashCode(), model.hashCode());
    }

    public StoredLtrModel reparseModel(StoredLtrModel srcModel) throws IOException {
        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        String modelString = Strings.toString(srcModel.toXContent(builder, ToXContent.EMPTY_PARAMS));
        StoredLtrModel modelReparsed = parse(modelString);
        return modelReparsed;
    }

    public void testSerialization() throws IOException {
        String modelJson = "{\n" +
                " \"name\":\"my_model\",\n" +
                " \"feature_set\":" + getSimpleFeatureSet() +
                "," +
                " \"model\": {\n" +
                "   \"type\": \"model/dummy\",\n" +
                "   \"definition\": \"completely ignored\",\n"+
                "   \"feature_normalizers\": {\n"+
                "     \"feature_2\": { \"min_max\":" +
                "           {\"minimum\": 1.0," +
                "            \"maximum\": 1.25}}}" +
                " }" +
                "}";

        StoredLtrModel model = parse(modelJson);

        BytesStreamOutput out = new BytesStreamOutput();
        model.writeTo(out);
        out.close();

        BytesRef ref = out.bytes().toBytesRef();
        StreamInput input = ByteBufferStreamInput.wrap(ref.bytes, ref.offset, ref.length);

        StoredLtrModel modelUnserialized = new StoredLtrModel(input);
        assertEquals(model, modelUnserialized);

        // Confirm model def serialization itself works

    }

    public void testSerializationModelDef() throws IOException {
        String modelDefnJson = "{\n" +
                "   \"type\": \"model/dummy\",\n" +
                "   \"definition\": \"completely ignored\",\n"+
                "   \"feature_normalizers\": {\n"+
                "     \"feature_2\": { \"min_max\":" +
                "           {\"minimum\": 1.0," +
                "            \"maximum\": 1.25}}}}";

        XContentParser xContent = jsonXContent.createParser(EMPTY,
                LoggingDeprecationHandler.INSTANCE, modelDefnJson);
        StoredLtrModel.LtrModelDefinition modelDef = StoredLtrModel.LtrModelDefinition.parse(xContent, null);

        BytesStreamOutput out = new BytesStreamOutput();
        modelDef.writeTo(out);
        out.close();

        BytesRef ref = out.bytes().toBytesRef();
        StreamInput input = ByteBufferStreamInput.wrap(ref.bytes, ref.offset, ref.length);

        StoredLtrModel.LtrModelDefinition modelUnserialized = new StoredLtrModel.LtrModelDefinition(input);
        assertEquals(modelUnserialized.getDefinition(), modelDef.getDefinition());
        assertEquals(modelUnserialized.getType(), modelDef.getType());
        assertEquals(modelUnserialized.getFtrNorms(), modelDef.getFtrNorms());

    }


    public void testSerializationUpgradeBinaryStream() throws IOException {
        // Below is base64 encoded a model with no feature norm data
        // to ensure proper parsing of a binary stream missing ftr norms
        //
        //        String modelDefnJson = "{\n" +
        //                "   \"type\": \"model/dummy\",\n" +
        //                "   \"definition\": \"completely ignored\"}";
        String base64Encoded = "C21vZGVsL2R1bW15EmNvbXBsZXRlbHkgaWdub3JlZAE=";
        byte[] bytes = Base64.getDecoder().decode(base64Encoded);
        StreamInput input = ByteBufferStreamInput.wrap(bytes, 0, bytes.length);
        input.setVersion(Version.V_7_6_0);

        StoredLtrModel.LtrModelDefinition modelUnserialized = new StoredLtrModel.LtrModelDefinition(input);
        assertEquals(modelUnserialized.getDefinition(), "completely ignored");
        assertEquals(modelUnserialized.getType(), "model/dummy");
        assertEquals(modelUnserialized.getFtrNorms().numNormalizers(), 0);

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