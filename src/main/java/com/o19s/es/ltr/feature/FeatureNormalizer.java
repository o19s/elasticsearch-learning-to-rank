package com.o19s.es.ltr.feature;

import com.o19s.es.ltr.feature.store.FeatureNormalizerFactory;
import org.elasticsearch.common.io.stream.Writeable;

public interface FeatureNormalizer extends Writeable {
    double normalize(double value);

    String featureName();

    FeatureNormalizerFactory.Type getType();
}