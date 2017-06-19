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

package com.o19s.es.ltr;

import com.o19s.es.ltr.feature.store.CompiledLtrModel;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredFeatureSetParserTests;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.ranker.DenseFeatureVector;
import com.o19s.es.ltr.ranker.DenseLtrRanker;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTreeTests;
import com.o19s.es.ltr.ranker.linear.LinearRankerTests;
import com.o19s.es.ltr.ranker.parser.LinearRankerParser;
import org.apache.lucene.util.TestUtil;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;

import java.io.IOException;

import static org.apache.lucene.util.LuceneTestCase.random;
import static org.junit.Assert.assertEquals;

public class LtrTestUtils {

    public static StoredFeature randomFeature() throws IOException {
        return StoredFeatureSetParserTests.buildRandomFeature();
    }

    public static StoredFeature randomFeature(String name) throws IOException {
        return StoredFeatureSetParserTests.buildRandomFeature(name);
    }

    public static StoredFeatureSet randomFeatureSet() throws IOException {
        return StoredFeatureSetParserTests.buildRandomFeatureSet();
    }

    public static StoredFeatureSet randomFeatureSet(int nbFeature) throws IOException {
        return StoredFeatureSetParserTests.buildRandomFeatureSet(nbFeature);
    }

    public static StoredFeatureSet randomFeatureSet(String name) throws IOException {
        return StoredFeatureSetParserTests.buildRandomFeatureSet(name);
    }

    public static CompiledLtrModel buildRandomModel() throws IOException {
        StoredFeatureSet set = StoredFeatureSetParserTests.buildRandomFeatureSet();
        LtrRanker ranker;
        ranker = buildRandomRanker(set.size());
        return new CompiledLtrModel(TestUtil.randomSimpleString(random(), 5, 10), set, ranker);
    }

    public static StoredLtrModel randomLinearModel(String name, StoredFeatureSet set) throws IOException {
        XContentBuilder builder = JsonXContent.contentBuilder();
        builder.startObject();
        for (int i = 0; i < set.size(); i++) {
            builder.field(set.feature(i).name(), random().nextFloat());
        }
        builder.endObject();
        return new StoredLtrModel(name, set, LinearRankerParser.TYPE, builder.string(), false);
    }

    public static LtrRanker buildRandomRanker(int fSize) {
        LtrRanker ranker;
        if (random().nextBoolean()) {
            ranker = LinearRankerTests.generateRandomRanker(fSize);
        } else {
            ranker = NaiveAdditiveDecisionTreeTests.generateRandomDecTree(fSize, TestUtil.nextInt(random(), 1, 50),
                    5, 50, null);
        }
        return ranker;
    }

    public static void assertRankersHaveSameScores(DenseLtrRanker one, DenseLtrRanker two, int nPass) {
        DenseFeatureVector vectorOne = one.newFeatureVector(null);
        DenseFeatureVector vectorTwo = two.newFeatureVector(null);
        assertEquals(vectorOne.scores.length, vectorTwo.scores.length);
        float[][] scores = new float[100][vectorOne.scores.length];
        for (float[] s : scores) {
            LinearRankerTests.fillRandomWeights(s);
        }
        for (int i = 0; i < nPass; i++) {
            vectorOne = one.newFeatureVector(vectorOne);
            vectorTwo = one.newFeatureVector(vectorTwo);
            System.arraycopy(scores[i%100], 0, vectorOne.scores, 0, vectorOne.scores.length);
            System.arraycopy(scores[i%100], 0, vectorTwo.scores, 0, vectorTwo.scores.length);
            float scoreOne = one.score(vectorOne);
            float scoreTwo = two.score(vectorTwo);
            assertEquals(scoreOne, scoreTwo, Math.ulp(scoreOne));
        }
    }
}
