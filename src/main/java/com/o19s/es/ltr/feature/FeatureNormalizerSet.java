package com.o19s.es.ltr.feature;

import com.o19s.es.ltr.ranker.normalizer.FeatureNormalizer;

public interface FeatureNormalizerSet  {

    FeatureNormalizer getNormalizer(String featureName);
}
