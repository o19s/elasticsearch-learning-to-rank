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

package com.o19s.es.ltr.ranker.parser;

import com.o19s.es.ltr.LtrTestUtils;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.ranker.DenseFeatureVector;
import com.o19s.es.ltr.ranker.linear.LinearRanker;
import com.o19s.es.ltr.ranker.linear.LinearRankerTests;
import org.apache.lucene.util.LuceneTestCase;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.json.JsonXContent;
import org.junit.Assert;

import java.io.IOException;

import static java.util.Collections.singletonList;

public class LinearRankerParserTests extends LuceneTestCase {
    public void testParse() throws IOException {
        XContentBuilder builder = JsonXContent.contentBuilder();
        StoredFeatureSet set = LtrTestUtils.randomFeatureSet();
        float[] expectedWeights = new float[set.size()];
        builder.startObject();
        for (int i = 0; i < set.size(); i++) {
            float weight = random().nextFloat();
            expectedWeights[i] = weight;
            builder.field(set.feature(i).name(), weight);
        }
        builder.endObject();
        String json = Strings.toString(builder);
        LinearRankerParser parser = new LinearRankerParser();
        LinearRanker ranker = parser.parse(set, json);
        DenseFeatureVector v = ranker.newFeatureVector(null);
        LinearRankerTests.fillRandomWeights(v.scores);
        LinearRanker expectedRanker = new LinearRanker(expectedWeights);
        Assert.assertEquals(expectedRanker.score(v), ranker.score(v), Math.ulp(expectedRanker.score(v)));
    }

    public void testBadJson() throws IOException {
        StoredFeatureSet set = LtrTestUtils.randomFeatureSet();
        LinearRankerParser parser = new LinearRankerParser();
        expectThrows(IllegalArgumentException.class, () -> parser.parse(set, "{ \"hmm\": }"));
    }

    public void testBadStructure() throws IOException {
        StoredFeatureSet set = new StoredFeatureSet("test", singletonList(LtrTestUtils.randomFeature("feature")));
        LinearRankerParser parser = new LinearRankerParser();
        expectThrows(ParsingException.class, () -> parser.parse(set, "{ \"feature\": {} }"));
        expectThrows(ParsingException.class, () -> parser.parse(set, "{ \"feature\": [] }"));
        expectThrows(ParsingException.class, () -> parser.parse(set, "{ \"feature\": null }"));
        expectThrows(ParsingException.class, () -> parser.parse(set, "{ \"feature\": \"hmm\" }"));
        expectThrows(ParsingException.class, () -> parser.parse(set, "{ \"feature\": \"1.2\" }"));
        expectThrows(ParsingException.class, () -> parser.parse(set, "[]"));
    }

    public void testEmptyModelOK() throws IOException {
        StoredFeatureSet set = LtrTestUtils.randomFeatureSet();
        LinearRankerParser parser = new LinearRankerParser();
        LinearRanker ranker = parser.parse(set, "{}");
        DenseFeatureVector v = ranker.newFeatureVector(null);
        LinearRankerTests.fillRandomWeights(v.scores);
        assertEquals(0F, ranker.score(v), Math.ulp(0));
    }

    public void testUnknownFeature() throws IOException {
        StoredFeatureSet set = new StoredFeatureSet("test", singletonList(LtrTestUtils.randomFeature("feature")));
        LinearRankerParser parser = new LinearRankerParser();
        expectThrows(ParsingException.class, () -> parser.parse(set, "{ \"features\": 1.5 }"));
    }

    public static String generateRandomModelString(FeatureSet set) throws IOException {
        XContentBuilder builder = JsonXContent.contentBuilder();
        builder.startObject();
        for (int i = 0; i < set.size(); i++) {
            builder.field(set.feature(i).name(), random().nextFloat());
        }
        builder.endObject().close();
        return Strings.toString(builder);
    }
}