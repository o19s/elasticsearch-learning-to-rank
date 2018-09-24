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

package com.o19s.es.ltr.feature;

import com.o19s.es.ltr.LtrQueryContext;
import org.apache.lucene.search.Query;

import java.util.List;
import java.util.Map;

/**
 * A set of features.
 * Features can be identified by their name or ordinal.
 * A FeatureSet is dense and ordinals start at 0.
 */
public interface FeatureSet {

    /**
     * Name of the feature set
     */
    String name();

    /**
     * Parse and build lucene queries
     */
    List<Query> toQueries(LtrQueryContext context, Map<String, Object> params);

    /**
     * Retrieve feature ordinal by its name.
     * If the feature does not exist the behavior of this method is
     * undefined.
     */
    int featureOrdinal(String featureName);

    /**
     * Retrieve feature by its ordinal.
     * May produce unexpected results if called with unknown ordinal
     */
    Feature feature(int ord);

    /**
     * Access a feature by its name.
     * May produce unexpected results if called with unknown feature
     */
    Feature feature(String featureName);

    /**
     * Check if this set supports this feature
     * @return true if supported false otherwise
     */
    boolean hasFeature(String featureName);

    /**
     *
     * Number of features in the set.
     */
    int size();

    default FeatureSet optimize() {
        return this;
    }

    default void validate() {
    }
}
