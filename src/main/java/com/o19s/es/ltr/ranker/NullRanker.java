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

public class NullRanker extends DenseLtrRanker {
  private final int modelSize;

  public NullRanker(int modelSize) {
    this.modelSize = modelSize;
  }

  @Override
  public String name() {
    return "null_ranker";
  }

  @Override
  public float score(FeatureVector point) {
    return 0F;
  }

  @Override
  protected float score(DenseFeatureVector vector) {
    return 0F;
  }

  @Override
  protected int size() {
    return modelSize;
  }
}
