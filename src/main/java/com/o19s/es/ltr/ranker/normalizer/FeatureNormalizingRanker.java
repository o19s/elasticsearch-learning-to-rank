package com.o19s.es.ltr.ranker.normalizer;

import com.o19s.es.ltr.ranker.LtrRanker;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;

import java.util.Map;
import java.util.Objects;

public class FeatureNormalizingRanker implements LtrRanker, Accountable {

    private final LtrRanker wrapped;
    private final Map<Integer, Normalizer> ftrNorms;
    private static final long BASE_RAM_USED;

    private static final long PER_FTR_NORM_RAM_USED = 8;
    static {
        BASE_RAM_USED = RamUsageEstimator.shallowSizeOfInstance(FeatureNormalizingRanker.class);
    }

    public FeatureNormalizingRanker(LtrRanker wrapped, Map<Integer, Normalizer> ftrNorms) {
        this.wrapped = Objects.requireNonNull(wrapped);
        this.ftrNorms = Objects.requireNonNull(ftrNorms);
    }

    public Map<Integer, Normalizer> getFtrNorms() {
        return this.ftrNorms;
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
        for (Map.Entry<Integer, Normalizer> ordToNorm: this.ftrNorms.entrySet()) {
            int ord = ordToNorm.getKey();
            float origFtrScore = point.getFeatureScore(ord);
            float normed = ordToNorm.getValue().normalize(origFtrScore);
            point.setFeatureScore(ord, normed);
        }
        return wrapped.score(point);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) return false;
        if (!(other instanceof  FeatureNormalizingRanker)) {
            return false;
        }
        final FeatureNormalizingRanker that = (FeatureNormalizingRanker)(other);
        if (that == null) return false;

        if (!that.ftrNorms.equals(this.ftrNorms)) return false;
        if (!that.wrapped.equals(this.wrapped)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return this.wrapped.hashCode() +
                (31 * this.ftrNorms.hashCode());
    }

    @Override
    public long ramBytesUsed() {

        long ftrNormSize = ftrNorms.size() * (PER_FTR_NORM_RAM_USED);

        if (this.wrapped instanceof Accountable) {
            Accountable accountable = (Accountable)this.wrapped;
            return BASE_RAM_USED + accountable.ramBytesUsed() + ftrNormSize;
        } else {
            return BASE_RAM_USED + ftrNormSize;
        }
    }
}