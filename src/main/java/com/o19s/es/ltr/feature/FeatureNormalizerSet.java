package com.o19s.es.ltr.feature;

import com.o19s.es.ltr.ranker.normalizer.Normalizer;

public interface FeatureNormalizerSet  {

    Normalizer getNormalizer(String featureName);
}
