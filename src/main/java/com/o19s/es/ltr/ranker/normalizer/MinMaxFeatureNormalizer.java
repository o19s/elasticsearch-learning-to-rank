package com.o19s.es.ltr.ranker.normalizer;

import com.o19s.es.ltr.feature.store.FeatureNormalizerFactory;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ObjectParser;

import java.io.IOException;

public class MinMaxFeatureNormalizer implements FeatureNormalizer  {
    double maximum;
    double minimum;
    String featureName;

    public MinMaxFeatureNormalizer(String featureName, double minimum, double maximum) {
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
    public double normalize(double value) {
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
