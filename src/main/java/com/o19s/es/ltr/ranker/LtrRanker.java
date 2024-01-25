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

import org.elasticsearch.core.Nullable;

/**
 * A ranker used by {@link com.o19s.es.ltr.query.RankerQuery}
 * to compute a score based on a set of feature scores.
 */
public interface LtrRanker {

    /**
     * LtrRanker name
     *
     * @return the ranker name
     */
    String name();

    /**
     * Data point implementation used by this ranker
     * A data point is used to store feature scores.
     * A single instance will be created for every Scorer.
     * The implementation does not have to be thread-safe and meant to be reused.
     * The ranker must always provide a new instance of the data point when this method is called with null.
     *
     * @param reuse allows Ranker to reuse a feature vector instance
     * @return the new feature vector to be used by this ranker
     */
    FeatureVector newFeatureVector(@Nullable FeatureVector reuse);

    /**
     * Score the data point.
     * At this point all feature scores are set.
     * features that did not match are set with a default score
     *
     * @param point the feature vector point to compute the score for
     * @return the score computed for the given point
     */
    float score(FeatureVector point);

    /**
     * A FeatureVector used to store individual feature scores
     */
    interface FeatureVector {
        /**
         * Set the score for the given featureId
         *
         * @param featureId the feature-id to set the score for
         * @param score the feature's score
         */
        void setFeatureScore(int featureId, float score);

        /**
         * Retrieve the score for the given feature-id
         *
         * @param featureId the feature-id to retrieve the score for
         * @return the score computed for the given feature
         */
        float getFeatureScore(int featureId);

        /**
         * Retrieve the default score
         * @return the score computed for the given feature
         */
        float getDefaultScore();

    }
}
