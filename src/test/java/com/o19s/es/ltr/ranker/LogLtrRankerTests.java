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

package com.o19s.es.ltr.ranker;

import com.o19s.es.ltr.ranker.linear.LinearRankerTests;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;

public class LogLtrRankerTests extends LuceneTestCase {
    public void testNewFeatureVector() throws Exception {
        int modelSize = TestUtil.nextInt(random(), 1, 20);

        final float[] expectedScores = new float[modelSize];
        LinearRankerTests.fillRandomWeights(expectedScores);

        final float[] actualScores = new float[modelSize];
        LogLtrRanker ranker = new LogLtrRanker(new NullRanker(modelSize), (i, s) -> actualScores[i] = s);
        LtrRanker.FeatureVector vector = ranker.newFeatureVector(null);
        for (int i = 0; i < expectedScores.length; i++) {
            vector.setFeatureScore(i, expectedScores[i]);
        }
        assertArrayEquals(expectedScores, actualScores, 0F);
    }

    public void score() throws Exception {
        int modelSize = TestUtil.nextInt(random(), 1, 20);
        LtrRanker ranker = new NullRanker(modelSize);
        LogLtrRanker logRanker = new LogLtrRanker(new NullRanker(modelSize), (i, s) -> {});
        int nPass = TestUtil.nextInt(random(), 100, 200);
        float[] scores = new float[modelSize];

        while (nPass-- > 0) {
            LinearRankerTests.fillRandomWeights(scores);
            LtrRanker.FeatureVector vect1 = ranker.newFeatureVector(null);
            LtrRanker.FeatureVector vect2 = logRanker.newFeatureVector(null);
        }

    }

}