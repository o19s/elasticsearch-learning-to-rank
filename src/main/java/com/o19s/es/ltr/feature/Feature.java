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

import java.util.Map;

/**
 * A feature that can be transformed into a lucene query
 */
public interface Feature {
    /**
     * The feature name
     */
    String name();

    /**
     * Transform this feature into a lucene query
     */
    Query doToQuery(LtrQueryContext context, FeatureSet set, Map<String, Object> params);

    /**
     * Optional optimization step
     *
     * @return an optimized version of the feature if applicable
     */
    default Feature optimize() {
        return this;
    }

    /**
     * Validate this feature against a featureset
     * Some feature may depend on other feature
     */
    default void validate(FeatureSet set) {
    }
}
