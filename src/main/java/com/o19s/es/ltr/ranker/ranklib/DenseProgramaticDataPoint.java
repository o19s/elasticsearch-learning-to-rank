/*
 * Copyright [2016] Doug Turnbull
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
 *
 */
package com.o19s.es.ltr.ranker.ranklib;

import ciir.umass.edu.learning.DataPoint;
import ciir.umass.edu.utilities.RankLibError;
import com.o19s.es.ltr.ranker.LtrRanker;

import java.util.Arrays;

/**
 * Implements FeatureVector but without needing to pass in a stirng
 * to be parsed
 */
public class DenseProgramaticDataPoint extends DataPoint implements LtrRanker.FeatureVector {
    private static final int RANKLIB_FEATURE_INDEX_OFFSET = 1;
    public DenseProgramaticDataPoint(int numFeatures) {
        this.fVals = new float[numFeatures+RANKLIB_FEATURE_INDEX_OFFSET]; // add 1 because RankLib features 1 based
    }

    public float getFeatureValue(int fid) {
        if(fid > 0 && fid < this.fVals.length) {
            return isUnknown(this.fVals[fid])?0.0F:this.fVals[fid];
        } else {
            throw RankLibError.create("Error in DenseDataPoint::getFeatureValue(): requesting unspecified feature, fid=" + fid);
        }
    }

    public void setFeatureValue(int fid, float fval) {
        if(fid > 0 && fid < this.fVals.length) {
            this.fVals[fid] = fval;
        } else {
            throw RankLibError.create("Error in DenseDataPoint::setFeatureValue(): feature (id=" + fid + ") not found.");
        }
    }

    public void setFeatureVector(float[] dfVals) {
        this.fVals = dfVals;
    }

    public float[] getFeatureVector() {
        return this.fVals;
    }

    @Override
    public void setFeatureScore(int featureIdx, float score) {
        // add 1 because RankLib features 1 based
        this.setFeatureValue(featureIdx+1, score);
    }

    @Override
    public float getFeatureScore(int featureIdx) {
        // add 1 because RankLib features 1 based
        return this.getFeatureValue(featureIdx+1);
    }

    public void reset() {
        Arrays.fill(fVals, 0F);
    }
}
