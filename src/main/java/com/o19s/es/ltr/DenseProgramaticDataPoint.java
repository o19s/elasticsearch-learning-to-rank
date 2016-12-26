package com.o19s.es.ltr;

import ciir.umass.edu.learning.DataPoint;
import ciir.umass.edu.utilities.RankLibError;

/**
 * Implements DataPoint but without needing to pass in a stirng
 * to be parsed
 */
public class DenseProgramaticDataPoint extends DataPoint {

    public DenseProgramaticDataPoint(int numFeatures) {
        super();
        this.fVals = new float[numFeatures+1]; // add 1 because RankLib features 1 based
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
}
