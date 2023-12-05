/*
 * Copyright [2017] Doug Turnbull, Wikimedia Foundation
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

package com.o19s.es.ltr.ranker.ranklib;

import ciir.umass.edu.learning.Ranker;
import ciir.umass.edu.learning.RankerFactory;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.parser.LtrRankerParser;

/**
 * Load a ranklib model from a script file, mostly a wrapper around the existing script that
 * complies with the {@link LtrRankerParser} interface
 */
public class RanklibModelParser implements LtrRankerParser {
  public static final String TYPE = "model/ranklib";
  private final RankerFactory factory;

  public RanklibModelParser(RankerFactory factory) {
    this.factory = factory;
  }

  @Override
  public LtrRanker parse(FeatureSet set, String model) {
    Ranker ranklibRanker = factory.loadRankerFromString(model);
    int numFeatures = ranklibRanker.getFeatures().length;
    if (set != null) {
      numFeatures = set.size();
    }
    return new RanklibRanker(ranklibRanker, numFeatures);
  }
}
