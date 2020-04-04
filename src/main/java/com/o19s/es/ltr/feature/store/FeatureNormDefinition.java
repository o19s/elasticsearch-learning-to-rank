package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.ranker.normalizer.FeatureNormalizer;

public interface FeatureNormDefinition extends StorableElement {

    FeatureNormalizer createFeatureNorm();

    String featureName();

    FeatureNormalizerFactory.Type normType();
}
