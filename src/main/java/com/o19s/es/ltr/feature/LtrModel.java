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

import com.o19s.es.ltr.ranker.LtrRanker;

/**
 * Represents a self contained LTR model
 */
public interface LtrModel {
    /**
     * Name of the model
     */
    String name();

    /**
     * Return the {@link LtrRanker} implementation used by this model
     */
    LtrRanker ranker();

    /**
     * The set of features used by this model
     */
    FeatureSet featureSet();
}
