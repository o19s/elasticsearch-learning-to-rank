/*
 *  Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.feature.Feature;
import com.o19s.es.ltr.feature.FeatureSet;
import java.io.IOException;

/** A feature store */
public interface FeatureStore {
  /**
   * @return the store name
   */
  String getStoreName();

  /**
   * @param name the name of the feature to load
   * @return the loaded feature
   * @throws IOException if the given feature store can not be loaded
   */
  Feature load(String name) throws IOException;

  /**
   * @param name the feature-set name to load
   * @return the loaded feature-set
   * @throws IOException if the given feature-set can not be loaded
   */
  FeatureSet loadSet(String name) throws IOException;

  /**
   * @param name the model name to be compiled
   * @return the compiled model
   * @throws IOException if the model can not be loaded and compiled
   */
  CompiledLtrModel loadModel(String name) throws IOException;
}
