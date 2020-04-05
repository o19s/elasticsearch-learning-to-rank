package com.o19s.es.ltr.feature;

import com.o19s.es.ltr.ranker.normalizer.Normalizer;

public class NoOpFeatureNormalizerSet implements FeatureNormalizerSet {
    @Override
    public Normalizer getNormalizer(String featureName) {
        return new Normalizer() {
            @Override
            public float normalize(float val) {
                return val;
            }
        };
    }
}
