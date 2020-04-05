package com.o19s.es.ltr.ranker.normalizer;

import org.elasticsearch.ElasticsearchException;

public class MinMaxFeatureNormalizer implements Normalizer  {
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

}
