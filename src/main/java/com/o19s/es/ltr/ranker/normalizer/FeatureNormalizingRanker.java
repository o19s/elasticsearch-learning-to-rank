package com.o19s.es.ltr.ranker.normalizer;

import com.o19s.es.ltr.feature.FeatureNormalizerSet;
import com.o19s.es.ltr.ranker.LtrRanker;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;

public class FeatureNormalizingRanker implements LtrRanker, Accountable {

    private LtrRanker wrapped;
    private FeatureNormalizerSet ftrNormSet;

    public FeatureNormalizingRanker(LtrRanker wrapped, FeatureNormalizerSet ftrNormSet) {
        this.wrapped = wrapped;
        this.ftrNormSet = ftrNormSet;
    }

    public FeatureNormalizerSet getFtrNormSet() {
        return this.ftrNormSet;
    }

    @Override
    public String name() {
        return wrapped.name();
    }

    @Override
    public FeatureVector newFeatureVector(FeatureVector reuse) {
        return wrapped.newFeatureVector(reuse);
    }

    @Override
    public float score(FeatureVector point) {
        for (int i: this.ftrNormSet.getNormalizedOrds()) {
            Normalizer norm = this.ftrNormSet.getNomalizer(i);
            point.setFeatureScore(i, norm.normalize(point.getFeatureScore(i)));
        }
        return wrapped.score(point);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) return false;
        FeatureNormalizingRanker that = (FeatureNormalizingRanker)(other);
        if (that == null) return false;

        if (!that.ftrNormSet.equals(this.ftrNormSet)) return false;
        if (!that.wrapped.equals(this.wrapped)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return this.wrapped.hashCode() +
                (31 * this.ftrNormSet.hashCode());
    }

    @Override
    public long ramBytesUsed() {
        Accountable accountable = (Accountable)this.wrapped;
        if (accountable != null) {
            return RamUsageEstimator.NUM_BYTES_OBJECT_HEADER + accountable.ramBytesUsed();
        } else {
            return RamUsageEstimator.NUM_BYTES_OBJECT_HEADER;
        }
    }
}
