package com.o19s.es.ltr.feature;

import com.o19s.es.ltr.ranker.normalizer.NoOpNormalizer;
import com.o19s.es.ltr.ranker.normalizer.Normalizer;

/**
 * A Feature Norm set that does nothing to the features
 */
public class NoOpFeatureNormalizerSet implements FeatureNormalizerSet {

    private Normalizer noopNorm;

    public NoOpFeatureNormalizerSet() {
        this.noopNorm = new NoOpNormalizer();
    }

    @Override
    public Normalizer getNomalizer(int featureOrd) {
        return noopNorm;
    }

    @Override
    public int[] getNormalizedOrds() {
        return new int[0];
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof NoOpFeatureNormalizerSet)) return false;

        NoOpFeatureNormalizerSet that = (NoOpFeatureNormalizerSet)(other);

        if (!noopNorm.equals(((NoOpFeatureNormalizerSet) other).noopNorm)) return false;

        return true;
    }

    public int hashCode() {
        return this.noopNorm.hashCode();
    }
}
