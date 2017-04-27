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

/**
 * A ranker used by {@link com.o19s.es.ltr.query.RankerQuery}
 * to compute a score based on a set of feature scores.
 */
public interface LtrRanker {

    /**
     * LtrRanker name
     */
    String name();

    /**
     * Data point implementation used by this ranker
     * A data point is used to store feature scores.
     * A single instance will be created for every Scorer.
     * The implementation does not have to be thread-safe and meant
     * to be reused.
     * The ranker must always provide a new instance of the data point when this method is called.
     */
    DataPoint newDataPoint();

    /**
     * Score the data point.
     * At this point all feature scores are set.
     * features that did not match are set with a score to 0
     */
    float score(DataPoint point);

    /**
     * The number of features supported by this ranker
     */
    int size();

    /**
     * A DataPoint used to store individual feature scores
     */
    interface DataPoint {
        /**
         * Set the feature score.
         */
        void setFeatureScore(int featureOrdinal, float score);

        /**
         * Get the feature score
         */
        float getFeatureScore(int featureOrdinal);
    }
}
