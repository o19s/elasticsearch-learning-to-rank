package com.o19s.es.ltr.ranker.normalizer;

import com.o19s.es.ltr.feature.store.FeatureNormalizerFactory;

public interface FeatureNormalizer {
    float normalize(float value);

    String featureName();

    FeatureNormalizerFactory.Type getType();
}