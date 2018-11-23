/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.o19s.es.ltr.ranker.linear;

import com.o19s.es.ltr.ranker.DenseFeatureVector;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTreeTests;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;

import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_ARRAY_HEADER;
import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_OBJECT_HEADER;
import static org.apache.lucene.util.TestUtil.nextInt;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.AllOf.allOf;

public class LinearRankerTests extends LuceneTestCase {
    private static final Logger LOG = LogManager.getLogger(NaiveAdditiveDecisionTreeTests.class);

    public void testName() throws Exception {
        LinearRanker ranker = new LinearRanker(new float[]{1,2});
        assertEquals("linear", ranker.name());
    }

    public void testScore() {
        LinearRanker ranker = new LinearRanker(new float[]{1,2,3});
        LtrRanker.FeatureVector point = ranker.newFeatureVector(null);
        point.setFeatureScore(0, 2);
        point.setFeatureScore(1, 3);
        point.setFeatureScore(2, 4);
        float expected = 1F*2F + 2F*3F + 3F*4F;
        assertEquals(expected, ranker.score(point), Math.ulp(expected));
    }

    public void testSize() {
        LinearRanker ranker = new LinearRanker(new float[]{1,2,3});
        assertEquals(ranker.size(), 3);
    }

    public void testRamSize() {
        LinearRanker ranker = generateRandomRanker(1, 1000);
        int expectedSize = ranker.size()*Float.BYTES + NUM_BYTES_ARRAY_HEADER + NUM_BYTES_OBJECT_HEADER;
        assertThat(ranker.ramBytesUsed(),
                allOf(greaterThan((long) (expectedSize*0.66F)),
                lessThan((long) (expectedSize*1.33F))));
    }

    public void testPerfAndRobustness() {
        LinearRanker ranker = generateRandomRanker(10, 1000);

        DenseFeatureVector vector = ranker.newFeatureVector(null);
        int nPass = TestUtil.nextInt(random(), 10, 8916);
        LinearRankerTests.fillRandomWeights(vector.scores);
        ranker.score(vector); // warmup

        long time = -System.currentTimeMillis();
        for (int i = 0; i < nPass; i++) {
            vector = ranker.newFeatureVector(vector);
            LinearRankerTests.fillRandomWeights(vector.scores);
            ranker.score(vector);
        }
        time += System.currentTimeMillis();
        LOG.info("Scored {} docs with {} features within {}ms ({} ms/doc)",
                nPass, ranker.size(), time, (float) time / (float) nPass);
    }

    public static LinearRanker generateRandomRanker(int minsize, int maxsize) {
        return generateRandomRanker(nextInt(random(), minsize, maxsize));
    }
    public static LinearRanker generateRandomRanker(int size) {
        return new LinearRanker(generateRandomWeights(size));
    }

    public static float[] generateRandomWeights(int s) {
        float[] weights = new float[s];
        fillRandomWeights(weights);
        return weights;
    }

    public static void fillRandomWeights(float[] weights) {
        for (int i = 0; i < weights.length; i++) {
            weights[i] = (float) nextInt(random(),1, 100000) / (float) nextInt(random(), 1, 100000);
        }
    }
}