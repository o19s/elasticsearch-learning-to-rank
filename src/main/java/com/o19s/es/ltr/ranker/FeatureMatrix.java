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

public interface FeatureMatrix {
    /**
     * Set the feature score.
     */
    void setFeatureScoreForDoc(int doc, int featureId, float score);

    /**
     * Get the feature score
     */
    float getFeatureScoreForDoc(int doc, int featureId);

    /**
     * A view of the matrix pointing at this specific doc.
     */
    default LtrRanker.FeatureVector vector(int doc) {
        return new LtrRanker.FeatureVector() {
            @Override
            public void setFeatureScore(int featureId, float score) {
                throw new UnsupportedOperationException("Read only vector");
            }

            @Override
            public float getFeatureScore(int featureId) {
                return getFeatureScoreForDoc(doc, featureId);
            }
        };
    }

    /**
     * @return number of docs in this matrix
     */
    int docSize();
}
