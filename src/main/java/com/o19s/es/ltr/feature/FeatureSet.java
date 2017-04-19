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

import java.util.Iterator;
import java.util.List;

/**
 * A set of features.
 * Features can be identified by their name or ordinal.
 * A FeatureSet is dense and ordinals start at 0.
 * Time critical processes will most likely use ordinals so that they can
 * represent the set as a flat Feature[] array.
 */
public interface FeatureSet extends Iterable<Feature> {

    /**
     * Name of the feature set
     * @return the name
     */
    String name();

    /**
     * List of feature present in this model.
     * This list must be properly ordered to that {@link #featureOrdinal(String)}
     * matches features().get(ordinal)
     * @return the list of features in the set.
     */
    List<Feature> features();

    /**
     * Retrieve feature ordinal by its name.
     * If the feature does not exist the behavior of this method is
     * undefined.
     * @param featureName the feature name
     * @return the feature ordinal
     */
    int featureOrdinal(String featureName);

    /**
     * Retrieve feature by its ordinal.
     * May produce unexpected results if called with unknown ordinal
     * @param ord feature ordinal
     * @return the feature
     */
    default Feature feature(int ord) {
        assert ord < features().size();
        return features().get(ord);
    }

    /**
     * Access a feature by its name.
     * May produce unexpected results if called with unknown feature
     * @param name name of the feature
     * @return the feature
     */
    Feature feature(String name);

    /**
     * Check if this set supports this feature
     * @param name the name of the feature
     * @return true if suported false otherwise
     */
    boolean hasFeature(String name);

    /**
     *
     * @return number of features in the set.
     */
    default int size() {
        return features().size();
    }

    default Iterator<Feature> iterator() {
        return features().iterator();
    }
}
