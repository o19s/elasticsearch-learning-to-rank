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
 * A dense ranker base class to work with {@link DenseFeatureVector}
 * where missing feature scores are set to 0.
 */
public abstract class DenseLtrRanker implements LtrRanker {
    @Override
    public DenseFeatureVector newFeatureVector(FeatureVector reuse) {
        if (reuse != null) {
            assert reuse instanceof DenseFeatureVector;
            DenseFeatureVector vector = (DenseFeatureVector) reuse;
            vector.reset();
            return vector;
        }
        return new DenseFeatureVector(size());
    }

    @Override
    public float score(FeatureVector vector) {
        assert vector instanceof DenseFeatureVector;
        return this.score((DenseFeatureVector) vector);
    }

    protected abstract float score(DenseFeatureVector vector);

    /**
     * The number of features supported by this ranker
     */
    protected abstract int size();
}
