package com.o19s.es.ltr.ranker.normalizer;

import com.o19s.es.ltr.feature.store.FeatureNormalizerFactory;

public interface FeatureNormalizer {
    double normalize(double value);

    String featureName();

    FeatureNormalizerFactory.Type getType();
}