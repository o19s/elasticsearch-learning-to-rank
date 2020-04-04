package com.o19s.es.ltr.ranker.normalizer;

import com.o19s.es.ltr.feature.store.FeatureNormalizerFactory;
import org.elasticsearch.ElasticsearchException;

public class MinMaxFeatureNormalizer implements FeatureNormalizer  {
    float maximum;
    float minimum;
    String featureName;

    public MinMaxFeatureNormalizer(String featureName, float minimum, float maximum) {
        if (minimum >= maximum) {
            throw new ElasticsearchException("Minimum " + Double.toString(minimum) +
                                             " must be smaller than than maximum: " +
                                              Double.toString(maximum));
        }
        this.minimum = minimum;
        this.maximum = maximum;
        this.featureName = featureName;
    }

    @Override
    public float normalize(float value) {
        return value / (maximum - minimum);
    }

    @Override
    public String featureName() {
        return this.featureName;
    }

    @Override
    public FeatureNormalizerFactory.Type getType() {
        return FeatureNormalizerFactory.Type.MIN_MAX;
    }
}
