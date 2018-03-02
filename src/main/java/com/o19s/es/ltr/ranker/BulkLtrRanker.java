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

/**
 * A ranker that is able to rank multiple docs at once
 */
public interface BulkLtrRanker {
    /**
     * A new feature matrix that can hold nfeature*nDocs floats
     * @param reuse a reusable matrix
     * @param nDocs number of docs in the matrix
     * @return the matrix
     */
    FeatureMatrix newMatrix(FeatureMatrix reuse, int nDocs);

    /**
     * Compute the scores using the values stored in the matrix
     *
     * @param matrix the input matrix with feature values populated
     * @param from score from this base
     * @param to score up to this doc
     * @param consumer callback to receive scores
     */
    void bulkScore(FeatureMatrix matrix, int from, int to, BulkScoreConsumer consumer);

    /**
     * Tell the elastic engine how many docs are to be scored at once
     * (it will be the value used to create the matrix using {@link #newMatrix(FeatureMatrix, int)})
     *
     * @param windowSize the number of document to be rescored
     * @return prefered number of docs to score in a single batch
     */
    default int getPreferedBatchSize(int windowSize) {
        return windowSize;
    }

    /**
     * Call
     */
    interface BulkScoreConsumer {
        void consume(int matrixIndex, float score);
    }
}
