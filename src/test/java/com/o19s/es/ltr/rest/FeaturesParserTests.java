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

package com.o19s.es.ltr.rest;

import org.apache.lucene.util.LuceneTestCase;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.xcontent.NamedXContentRegistry;
import org.elasticsearch.xcontent.XContentParser;

import java.io.IOException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.o19s.es.ltr.feature.store.StoredFeatureParserTests.generateTestFeature;
import static org.elasticsearch.xcontent.json.JsonXContent.jsonXContent;

public class FeaturesParserTests extends LuceneTestCase {
    public void testParseArray() throws IOException {
        RestAddFeatureToSet.FeaturesParserState fparser = new RestAddFeatureToSet.FeaturesParserState();
        int nFeat = random().nextInt(18)+1;
        String featuresArray = IntStream.range(0, nFeat)
                .mapToObj((i) -> generateTestFeature("feat" + i))
                .collect(Collectors.joining(","));
        XContentParser parser = jsonXContent.createParser(NamedXContentRegistry.EMPTY,
                LoggingDeprecationHandler.INSTANCE, "{\"features\":[" + featuresArray + "]}");
        fparser.parse(parser);
        assertEquals(nFeat, fparser.getFeatures().size());
        assertEquals("feat0", fparser.getFeatures().get(0).name());
    }
}