package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.ranker.normalizer.Normalizer;

public interface FeatureNormDefinition extends StorableElement {

    Normalizer createFeatureNorm();

    String featureName();

    StoredFeatureNormalizerSet.Type normType();
}
