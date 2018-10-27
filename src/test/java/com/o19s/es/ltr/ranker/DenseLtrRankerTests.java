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

package com.o19s.es.ltr.ranker;

import org.apache.lucene.util.LuceneTestCase;

public class DenseLtrRankerTests extends LuceneTestCase {
    public void newFeatureVector() throws Exception {
        int modelSize = random().nextInt(10);
        DummyDenseRanker ranker = new DummyDenseRanker(modelSize);
        DenseFeatureVector vector = ranker.newFeatureVector(null);
        assertNotNull(vector);
        for(int i = 0; i < modelSize; i++) {
            assertEquals(0, vector.getFeatureScore(0), Math.ulp(0));
        }
        float[] points = vector.scores;
        assertEquals(points.length, 2);

        for(int i = 0; i < modelSize; i++) {
            vector.setFeatureScore(0, random().nextFloat());
        }
        LtrRanker.FeatureVector vector2 = ranker.newFeatureVector(vector);
        assertSame(vector, vector2);
        for(int i = 0; i < modelSize; i++) {
            assertEquals(0, vector.getFeatureScore(0), Math.ulp(0));
        }
    }

    private static class DummyDenseRanker extends DenseLtrRanker {
        private final int modelSize;

        private DummyDenseRanker(int modelSize) {
            this.modelSize = modelSize;
        }

        @Override
        protected float score(DenseFeatureVector vector) {
            return 0;
        }

        @Override
        protected int size() {
            return modelSize;
        }

        @Override
        public String name() {
            return "dummy";
        }
    }
}