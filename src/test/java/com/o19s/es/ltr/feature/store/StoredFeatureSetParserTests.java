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
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.MatchQueryBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.apache.lucene.util.TestUtil.randomRealisticUnicodeString;
import static org.apache.lucene.util.TestUtil.randomSimpleString;
import static org.elasticsearch.common.xcontent.NamedXContentRegistry.EMPTY;
import static org.elasticsearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

public class StoredFeatureSetParserTests extends LuceneTestCase {

    public void testParse() throws IOException {
        List<StoredFeature> features = new ArrayList<>();
        String setString = generateRandomFeatureSet("my_set", features::add);
        StoredFeatureSet set = parse(setString);
        assertFeatureSet(set, features);
    }

    private void assertFeatureSet(StoredFeatureSet set, List<StoredFeature> features) {
        assertEquals("my_set", set.name());
        assertEquals(features.size(), set.size());
        long ramSize = 0;
        for (int i = 0; i < features.size(); i++) {
            StoredFeature expected = features.get(i);
            StoredFeature actual = set.feature(i);
            assertEquals(expected.name(), actual.name());
            assertEquals(expected.templateLanguage(), actual.templateLanguage());
            assertArrayEquals(expected.queryParams().toArray(), actual.queryParams().toArray());
            assertEquals(expected.template(), actual.template());
            assertEquals(expected.ramBytesUsed(), actual.ramBytesUsed());
            assertEquals(i, set.featureOrdinal(actual.name()));
            assertTrue(set.hasFeature(actual.name()));
            assertSame(actual, set.feature(actual.name()));
            ramSize += actual.ramBytesUsed();
        }
        assertFalse(set.hasFeature(unkownName()));
        assertThat(expectThrows(IllegalArgumentException.class,
                () -> set.feature(unkownName())).getMessage(),
                containsString("Unknown feature"));

        assertThat(set.ramBytesUsed(), allOf(greaterThan((long) (ramSize*0.66)), lessThan((long) (ramSize*1.33))));
    }

    public void testToXContent() throws IOException {
        List<StoredFeature> features = new ArrayList<>();
        String featureSetString = generateRandomFeatureSet("my_set", features::add);
        StoredFeatureSet featureSet = parse(featureSetString);

        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        featureSetString = featureSet.toXContent(builder, ToXContent.EMPTY_PARAMS).bytes().utf8ToString();
        StoredFeatureSet featureSetReparsed = parse(featureSetString);
        assertFeatureSet(featureSetReparsed, features);
    }

    public void testParseErrorOnDups() throws IOException {
        String set = "{\"name\" : \"my_set\",\n" +
                "\"features\": [\n" +
                StoredFeatureParserTests.generateTestFeature() + "," +
                StoredFeatureParserTests.generateTestFeature() +
                "]}";
        assertThat(expectThrows(ParsingException.class,
                () -> parse(set)).getMessage(),
                containsString("feature names must be unique in a set"));
    }

    public void testParseErrorOnMissingName() throws IOException {
        String missingName = "{" +
                "\"features\": [\n" +
                StoredFeatureParserTests.generateTestFeature() +
                "]}";
        assertThat(expectThrows(ParsingException.class,
                () -> parse(missingName)).getMessage(),
                equalTo("Field [name] is mandatory"));
    }

    public void testParseErrorOnMissingSet() throws IOException {
        String missingName = "{ \"name\": \"my_set\"}";
        assertThat(expectThrows(ParsingException.class,
                () -> parse(missingName)).getMessage(),
                equalTo("Field [features] is mandatory"));
    }

    public void testParseErrorOnEmptySet() throws IOException {
        String missingName = "{ \"name\": \"my_set\"," +
                "\"features\": []}";

        assertThat(expectThrows(ParsingException.class,
                () -> parse(missingName)).getMessage(),
                equalTo("At least one feature must be defined in [features]"));
    }

    public void testParseErrorOnExtraField() throws IOException {
        String set = "{\"name\" : \"my_set\",\n" +
                "\"random_field\": \"oops\"," +
                "\"features\": [\n" +
                StoredFeatureParserTests.generateTestFeature() +
                "]}";
        assertThat(expectThrows(ParsingException.class,
                () -> parse(set)).getMessage(),
                containsString("unknown field [random_field], parser not found"));
    }

    private static StoredFeatureSet parse(String missingName) throws IOException {
        return StoredFeatureSet.parse(jsonXContent.createParser(EMPTY, missingName));
    }

    public static StoredFeature buildRandomFeature() throws IOException {
        return buildRandomFeature(rName());
    }

    public static StoredFeature buildRandomFeature(String name) throws IOException {
        return StoredFeatureParserTests.parse(generateRandomFeature(name));
    }
    private static String generateRandomFeature() {
        return generateRandomFeature(rName());
    }

    private static String generateRandomFeature(String name) {
        return "{\n" +
                "\"name\": \"" + name + "\",\n" +
                "\"params\": [\"" + rName() + "\", \"" + rName() + "\"],\n" +
                "\"template_language\": \"" + rName() + "\",\n" +
                "\"template\": \n" +
                new MatchQueryBuilder(rName(), randomRealisticUnicodeString(random())).toString() +
                "\n}\n";
    }

    private static String rName() {
        return randomSimpleString(random(), 5, 10);
    }

    private static String unkownName() {
        // cannot be known, size is out [5,10] generated by rName()
        return randomSimpleString(random(), 4);
    }

    public static StoredFeatureSet buildRandomFeatureSet() throws IOException {
        return buildRandomFeatureSet(rName());
    }

    public static StoredFeatureSet buildRandomFeatureSet(String name) throws IOException {
        return parse(generateRandomFeatureSet(name, null));
    }

    public static String generateRandomFeatureSet() throws IOException {
        return generateRandomFeatureSet(null);
    }

    public static String generateRandomFeatureSet(Consumer<StoredFeature> features) throws IOException {
        return generateRandomFeatureSet(rName(), features);
    }
    public static String generateRandomFeatureSet(String name, Consumer<StoredFeature> features) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"name\" : \"")
                .append(name)
                .append("\",\n");
        sb.append("\"features\":[");
        int nbFeat = random().nextInt(20)+1;
        boolean first = true;
        // Simply avoid adding the same feature twice because of random string
        Set<String> addedFeatures = new HashSet<>();
        while(nbFeat-->0) {
            String featureString = generateRandomFeature();
            StoredFeature feature = StoredFeature.parse(jsonXContent.createParser(EMPTY, featureString));
            if (!addedFeatures.add(feature.name())) {
                continue;
            }
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append(featureString);
            if (features != null) {
                features.accept(feature);
            }
        }
        sb.append("]}");
        return sb.toString();
    }
}