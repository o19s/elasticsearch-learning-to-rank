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

import java.util.Arrays;

/**
 * Simple array-backed feature vector
 */
public class DenseFeatureVector implements LtrRanker.FeatureVector {
    public final float[] scores;

    /**
     * New simple array-backed datapoint
     *
     * @param size size of the internal array
     */
    public DenseFeatureVector(int size) {
        this.scores = new float[size];
    }

    @Override
    public void setFeatureScore(int featureIdx, float score) {
        scores[featureIdx] = score;
    }

    @Override
    public float getFeatureScore(int featureIdx) {
        return scores[featureIdx];
    }

    public void reset() {
        Arrays.fill(scores, 0F);
    }
}
