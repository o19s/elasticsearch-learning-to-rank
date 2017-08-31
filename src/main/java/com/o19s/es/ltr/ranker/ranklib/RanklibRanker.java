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

package com.o19s.es.ltr.ranker.ranklib;

import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.ranklib.learning.Ranker;

public class RanklibRanker implements LtrRanker {
    private final Ranker ranker;

    public RanklibRanker(Ranker ranker) {
        this.ranker = ranker;
    }

    /**
     * LtrModel name
     *
     * @return the name of the model
     */
    @Override
    public String name() {
        return ranker.name();
    }

    /**
     * data point implementation used by this ranker
     * A data point is used to store feature scores.
     * A single instance will be created for every Scorer.
     * The implementation must not be thread-safe.
     *
     * @return LtrRanker data point implementation
     */
    @Override
    public DenseProgramaticDataPoint newFeatureVector(FeatureVector reuse) {
        if (reuse != null) {
            assert reuse instanceof DenseProgramaticDataPoint;
            DenseProgramaticDataPoint vector = (DenseProgramaticDataPoint) reuse;
            vector.reset();
            return vector;
        }
        // ranklib models are 1-based
        return new DenseProgramaticDataPoint(ranker.getFeatures().length+1);
    }

    /**
     * Score the data point.
     * At this point all feature scores are set.
     * features that did not match are set with a score to 0
     *
     * @param point the populated data point
     * @return the score
     */
    @Override
    public float score(FeatureVector point) {
        assert point instanceof DenseProgramaticDataPoint;
        return (float) ranker.eval((DenseProgramaticDataPoint) point);
    }
}
