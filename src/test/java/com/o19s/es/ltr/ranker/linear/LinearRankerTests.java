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

import com.o19s.es.ltr.ranker.LtrRanker;
import org.apache.lucene.util.LuceneTestCase;

public class LinearRankerTests extends LuceneTestCase {
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
}