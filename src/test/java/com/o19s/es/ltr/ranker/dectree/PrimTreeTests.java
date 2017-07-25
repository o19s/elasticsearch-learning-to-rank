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

package com.o19s.es.ltr.ranker.dectree;

import com.o19s.es.ltr.LtrTestUtils;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTreeTests.SimpleCountRandomTreeGeneratorStatsCollector;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;

import java.io.IOException;

public class PrimTreeTests extends LuceneTestCase {
    public void testScore() throws Exception {
        SimpleCountRandomTreeGeneratorStatsCollector counts = new SimpleCountRandomTreeGeneratorStatsCollector();
        NaiveAdditiveDecisionTree naive = NaiveAdditiveDecisionTreeTests.generateRandomDecTree(1, 1000,
                1, 1000,
                1, 8, counts);
        PrimTree primTree = naive.toPrimTree();
        assertEquals(naive.size(), primTree.size());
        assertTrue(naive.ramBytesUsed() > primTree.ramBytesUsed());
        int nPass = TestUtil.nextInt(random(), 10, 8916);
        LtrTestUtils.assertRankersHaveSameScores(naive, primTree, nPass);
    }

    public void testSimplScore() throws IOException {
        PrimTree ranker = NaiveAdditiveDecisionTreeTests.parseTreeModel("simple_tree.txt").toPrimTree();
        LtrRanker.FeatureVector vector = ranker.newFeatureVector(null);
        vector.setFeatureScore(0, 1F);
        vector.setFeatureScore(1, 2F);
        vector.setFeatureScore(2, 3F);

        float expected = 1.2F*3.4F + 3.2F*2.8F;
        assertEquals(expected, ranker.score(vector), Math.ulp(expected));
    }
}