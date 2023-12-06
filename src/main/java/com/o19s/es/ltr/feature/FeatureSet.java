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
import java.util.List;
import java.util.Map;
import org.apache.lucene.search.Query;

/**
 * A set of features. Features can be identified by their name or ordinal. A FeatureSet is dense and
 * ordinals start at 0.
 */
public interface FeatureSet {

  /**
   * @return the name of the feature set
   */
  String name();

  /**
   * Parse and build lucene queries from the features in this set
   *
   * @param context the LtRQuery context on which the lucene queries are going to be build on
   * @param params additional parameters to be used in the building of the lucene queries
   * @return the list of queries build for the current feature-set and the given parameters
   */
  List<Query> toQueries(LtrQueryContext context, Map<String, Object> params);

  /**
   * Retrieve feature ordinal by its name. If the feature does not exist the behavior of this method
   * is undefined.
   *
   * @param featureName the name of the feature to retrieve its ordinal position
   * @return the ordinal of the given feature in the current set
   */
  int featureOrdinal(String featureName);

  /**
   * Retrieve feature by its ordinal. May produce unexpected results if called with unknown ordinal
   *
   * @param ord the ordinal place of the feature to retrieve
   * @return the feature at the given ordinal position
   */
  Feature feature(int ord);

  /**
   * Access a feature by its name. May produce unexpected results if called with unknown feature
   *
   * @param featureName the name of the feature to retrieve
   * @return the feature found with the given name
   */
  Feature feature(String featureName);

  /**
   * Check if this set supports this feature
   *
   * @param featureName the name of the feature check
   * @return true if supported false otherwise
   */
  boolean hasFeature(String featureName);

  /**
   * @return the number of features in the set
   */
  int size();

  default FeatureSet optimize() {
    return this;
  }

  default void validate() {}
}
