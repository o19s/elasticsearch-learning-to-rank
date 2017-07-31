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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.elasticsearch.common.xcontent.NamedXContentRegistry.EMPTY;
import static org.elasticsearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

public class StoredDerivedFeatureParserTests extends LuceneTestCase {
    static final ToXContent.Params NOT_PRETTY;
    static {
        Map<String, String> params = new HashMap<>();
        params.put("pretty", "false");
        NOT_PRETTY = new ToXContent.MapParams(params);
    }

    public void testParseFeatureAsJson() throws IOException {
        String featureString = generateTestFeature();

        StoredDerivedFeature feature = parse(featureString);
        assertTestFeature(feature);
    }

    public static String generateTestFeature() {
        return "{\n" +
                    "\"name\": \"testDerivedFeature\",\n" +
                    "\"expr\": \"feature1 * feature2\"\n" +
                    "\n}\n";
    }

    public void assertTestFeature(StoredDerivedFeature feature) {
        assertEquals("testDerivedFeature", feature.name());
        assertEquals("feature1 * feature2", feature.expression().sourceText);
    }


    public void testParseFeatureAsString() throws IOException {
        String featureString = "{\n" +
                "\"name\": \"testDerivedFeature\",\n" +
                "\"expr\": \"feature1 * feature2\"\n" +
                "\n}\n";


        StoredDerivedFeature feature = parse(featureString);
        assertEquals("testDerivedFeature", feature.name());
        assertEquals("feature1 * feature2", feature.expression().sourceText);
    }

    public void testToXContent() throws IOException {
        String featureString = generateTestFeature();
        StoredDerivedFeature feature = parse(featureString);
        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        featureString = feature.toXContent(builder, ToXContent.EMPTY_PARAMS).bytes().utf8ToString();
        StoredDerivedFeature featureReparsed = parse(featureString);
        assertTestFeature(featureReparsed);
    }

    public void testParseErrorOnMissingName() throws IOException {
        String featureString = "{\n" +
                "\"expr\": \"feature1 * feature2\"\n" +
                "\n}\n";
        assertThat(expectThrows(ParsingException.class, () -> parse(featureString)).getMessage(),
                equalTo("Field [name] is mandatory"));
    }

    public void testParseErrorOnMissingExpression() throws IOException {
        String featureString = "{\n" +
                "\"name\": \"testDerivedFeature\"\n" +
                "\n}\n";
        assertThat(expectThrows(ParsingException.class, () -> parse(featureString)).getMessage(),
                equalTo("Field [expr] is mandatory"));
    }

    public void testRamBytesUsed() throws IOException, InterruptedException {
        String featureString = "{\n" +
                "\"name\": \"testDerivedFeature\",\n" +
                "\"expr\": \"feature1 * feature2\"\n" +
                "\n}\n";

        StoredDerivedFeature feature = parse(featureString);
        long approxSize = featureString.length()*Character.BYTES;
        assertThat(feature.ramBytesUsed(),
                allOf(greaterThan((long) (approxSize*0.66)),
                    lessThan((long) (approxSize*1.33))));
    }

    static StoredDerivedFeature parse(String featureString) throws IOException {
        return StoredDerivedFeature.parse(jsonXContent.createParser(EMPTY, featureString));
    }
}