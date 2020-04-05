package com.o19s.es.ltr.feature;

import com.o19s.es.ltr.ranker.normalizer.NoOpNormalizer;
import com.o19s.es.ltr.ranker.normalizer.Normalizer;

public class NoOpFeatureNormalizerSet implements FeatureNormalizerSet {

    private Normalizer noopNorm;

    public NoOpFeatureNormalizerSet() {
        this.noopNorm = new NoOpNormalizer();
    }

    @Override
    public Normalizer getNomalizer(int featureOrd) {
        return noopNorm;
    }
}
