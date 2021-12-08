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

import org.apache.lucene.util.LuceneTestCase;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.hamcrest.CoreMatchers;

import java.io.IOException;
import java.util.Arrays;

import static org.elasticsearch.xcontent.NamedXContentRegistry.EMPTY;
import static org.elasticsearch.xcontent.json.JsonXContent.jsonXContent;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.lessThan;

public class StoredFeatureParserTests extends LuceneTestCase {
    public void testParseFeatureAsJson() throws IOException {
        String featureString = generateTestFeature();

        StoredFeature feature = parse(featureString);
        assertTestFeature(feature);
    }

    public static String generateTestFeature(String name) {
        return "{\n" +
                "\"name\": \""+name+"\",\n" +
                "\"params\": [\"param1\", \"param2\"],\n" +
                "\"template_language\": \"mustache\",\n" +
                "\"template\": \n" +
                new MatchQueryBuilder("match_field", "match_word").toString() +
                "\n}\n";
    }

    public static String generateTestFeature() {
        return generateTestFeature("testFeature");
    }

    public void assertTestFeature(StoredFeature feature) {
        assertEquals("testFeature", feature.name());
        assertArrayEquals(Arrays.asList("param1", "param2").toArray(), feature.queryParams().toArray());
        assertEquals("mustache", feature.templateLanguage());
        assertEquals(writeAsNonFormattedString(new MatchQueryBuilder("match_field", "match_word")),
                feature.template());
        assertFalse(feature.templateAsString());
    }

    public void testParseFeatureAsString() throws IOException {

        String featureString = "{\n" +
                "\"name\": \"testFeature\",\n" +
                "\"params\": [\"param1\", \"param2\"],\n" +
                "\"template_language\": \"mustache\",\n" +
                "\"template\": \"" +
                writeAsNonFormattedString(new MatchQueryBuilder("match_field", "match_word"))
                        .replace("\"", "\\\"") +
                "\"\n}\n";


        StoredFeature feature = parse(featureString);
        assertEquals("testFeature", feature.name());
        assertArrayEquals(Arrays.asList("param1", "param2").toArray(), feature.queryParams().toArray());
        assertEquals("mustache", feature.templateLanguage());
        assertEquals(writeAsNonFormattedString(new MatchQueryBuilder("match_field", "match_word")),
                feature.template());
        assertTrue(feature.templateAsString());
    }

    public void testToXContent() throws IOException {
        String featureString = generateTestFeature();
        StoredFeature feature = parse(featureString);
        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        featureString = Strings.toString(feature.toXContent(builder, ToXContent.EMPTY_PARAMS));
        StoredFeature featureReparsed = parse(featureString);
        assertTestFeature(featureReparsed);
    }

    public void testParseErrorOnMissingName() throws IOException {
        String featureString = "{\n" +
                "\"params\":[\"param1\",\"param2\"]," +
                "\"template_language\":\"mustache\",\n" +
                "\"template\": \n" +
                writeAsNonFormattedString(new MatchQueryBuilder("match_field", "match_word")) +
                "}";
        assertThat(expectThrows(ParsingException.class, () -> parse(featureString)).getMessage(),
                equalTo("Field [name] is mandatory"));
    }

    public void testParseWithExternalName() throws IOException {
        String featureString = "{\n" +
                "\"params\":[\"param1\",\"param2\"]," +
                "\"template_language\":\"mustache\",\n" +
                "\"template\": \n" +
                writeAsNonFormattedString(new MatchQueryBuilder("match_field", "match_word")) +
                "}";
        StoredFeature set = parse(featureString, "my_feature");
        assertEquals("my_feature", set.name());
    }

    public void testParseWithInconsistentExternalName() throws IOException {
        String featureString = "{\n" +
                "\"name\": \"testFeature\",\n" +
                "\"params\":[\"param1\",\"param2\"]," +
                "\"template_language\":\"mustache\",\n" +
                "\"template\": \n" +
                writeAsNonFormattedString(new MatchQueryBuilder("match_field", "match_word")) +
                "}";
        assertThat(expectThrows(ParsingException.class,
                () -> parse(featureString, "testFeature2")).getMessage(),
                CoreMatchers.equalTo("Invalid [name], expected [testFeature2] but got [testFeature]"));
    }

    public void testParseErrorOnBadTemplate() throws IOException {
        String featureString = "{\n" +
                "\"name\": \"testFeature\",\n" +
                "\"params\":[\"param1\",\"param2\"]," +
                "\"template_language\":\"mustache\",\n" +
                "\"template\": \"{{hop\"" +
                "}";
        assertThat(expectThrows(IllegalArgumentException.class, () -> parse(featureString).optimize()).getMessage(),
                containsString("Improperly closed variable"));
    }

    public void testParseErrorOnMissingTemplate() throws IOException {
        String featureString = "{\n" +
                "\"name\":\"testFeature\"," +
                "\"params\":[\"param1\",\"param2\"]," +
                "\"template_language\":\"mustache\"\n" +
                "}";
        assertThat(expectThrows(ParsingException.class, () -> parse(featureString)).getMessage(),
                equalTo("Field [template] is mandatory"));
    }

    public void testParseErrorOnUnknownField() throws IOException {
        String featureString = "{\n" +
                "\"name\":\"testFeature\"," +
                "\"params\":[\"param1\",\"param2\"]," +
                "\"template_language\":\"mustache\",\n" +
                "\n\"bogusField\":\"oops\"," +
                "\"template\": \n" +
                writeAsNonFormattedString(new MatchQueryBuilder("match_field", "match_word")) +
                "}";
        assertThat(expectThrows(ParsingException.class, () -> parse(featureString)).getMessage(),
                containsString("bogusField"));
    }

    public void testParseWithoutParams() throws IOException {
        String featureString = "{\n" +
                "\"name\":\"testFeature\"," +
                "\"template_language\":\"mustache\",\n" +
                "\"template\": \n" +
                writeAsNonFormattedString(new MatchQueryBuilder("match_field", "match_word")) +
                "}";
        StoredFeature feat = parse(featureString);
        assertTrue(feat.queryParams().isEmpty());
    }

    public void testParseWithEmptyParams() throws IOException {
        String featureString = "{\n" +
                "\"name\":\"testFeature\"," +
                "\"params\":[]," +
                "\"template_language\":\"mustache\",\n" +
                "\"template\": \n" +
                writeAsNonFormattedString(new MatchQueryBuilder("match_field", "match_word")) +
                "}";
        StoredFeature feat = parse(featureString);
        assertTrue(feat.queryParams().isEmpty());
    }

    public void testRamBytesUsed() throws IOException, InterruptedException {
        String featureString = "{\n" +
                "\"name\":\"testFeature\"," +
                "\"params\":[\"param1\",\"param2\"]," +
                "\"template_language\":\"mustache\",\n" +
                "\"template\":\"" +
                writeAsNonFormattedString(new MatchQueryBuilder("match_field", "match_word"))
                        .replace("\"", "\\\"") +
                "\"}";
        StoredFeature feature = parse(featureString);
        long approxSize = featureString.length()*Character.BYTES;
        assertThat(feature.ramBytesUsed(),
                allOf(greaterThan((long) (approxSize*0.66)),
                    lessThan((long) (approxSize*1.33))));
    }

    public void testExpressionOptimization() throws IOException {
        String featureString = "{\n" +
                "\"name\":\"testFeature\"," +
                "\"template_language\":\"derived_expression\",\n" +
                "\"template\":\"Math.random()" +
                "\"}";
        StoredFeature feature = parse(featureString);
        assertThat(feature.optimize(), instanceOf(PrecompiledExpressionFeature.class));
    }

    public void testMustacheOptimization() throws IOException {
        String featureString = "{\n" +
                "\"name\":\"testFeature\"," +
                "\"params\":[\"param1\",\"param2\"]," +
                "\"template_language\":\"mustache\",\n" +
                "\"template\":\"" +
                writeAsNonFormattedString(new MatchQueryBuilder("match_field", "match_word"))
                        .replace("\"", "\\\"") +
                "\"}";
        StoredFeature feature = parse(featureString);
        assertThat(feature.optimize(), instanceOf(PrecompiledTemplateFeature.class));
    }

    public void testDontOptimizeOnThirdPartyTemplateEngine() throws IOException {
        String featureString = "{\n" +
                "\"name\":\"testFeature\"," +
                "\"params\":[\"param1\",\"param2\"]," +
                "\"template_language\":\"third_party_template_engine\",\n" +
                "\"template\":\"" +
                writeAsNonFormattedString(new MatchQueryBuilder("match_field", "match_word"))
                        .replace("\"", "\\\"") +
                "\"}";
        StoredFeature feature = parse(featureString);
        assertSame(feature, feature.optimize());
    }

    static StoredFeature parse(String featureString) throws IOException {
        return parse(featureString, null);
    }

    static StoredFeature parse(String featureString, String defaultName) throws IOException {
        return StoredFeature.parse(jsonXContent.createParser(EMPTY,
                LoggingDeprecationHandler.INSTANCE, featureString), defaultName);
    }

    private String writeAsNonFormattedString(AbstractQueryBuilder<?> builder) {
        return Strings.toString(builder, false, false);
    }
}