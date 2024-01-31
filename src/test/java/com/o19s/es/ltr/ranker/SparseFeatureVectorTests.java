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

import org.apache.lucene.tests.util.LuceneTestCase;

public class SparseFeatureVectorTests extends LuceneTestCase {
    public void testConstructor() {
        int size = 10;
        SparseFeatureVector featureVector = new SparseFeatureVector(size);
        for (float score : featureVector.scores) {
            assertTrue(Float.isNaN(score));
        }
    }

    public void testSetGetReset() {
        int size = 10;
        SparseFeatureVector featureVector = new SparseFeatureVector(size);
        featureVector.setFeatureScore(5, 3.15F);

        assertEquals(3.15F, featureVector.getFeatureScore(5), Math.ulp(3.15F));
        assertTrue(Float.isNaN(featureVector.getFeatureScore(0)));

        featureVector.reset();

        for (int featureId = 0; featureId < size; featureId++) {
            assertTrue(Float.isNaN(featureVector.getFeatureScore(featureId)));
        }
    }

    public void testGetDefaultValue() {
        assertTrue(Float.isNaN(new SparseFeatureVector(10).getDefaultScore()));
    }

}