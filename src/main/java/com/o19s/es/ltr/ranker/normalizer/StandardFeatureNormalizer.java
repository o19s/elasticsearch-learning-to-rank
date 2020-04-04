package com.o19s.es.ltr.ranker.normalizer;

import com.o19s.es.ltr.feature.store.FeatureNormalizerFactory;
import com.o19s.es.ltr.ranker.normalizer.FeatureNormalizer;

public class StandardFeatureNormalizer implements FeatureNormalizer {

    private double mean;
    private double stdDeviation;
    private String featureName;


    public StandardFeatureNormalizer(String featureName, double mean, double stdDeviation) {
        this.mean = mean;
        this.stdDeviation = stdDeviation;
        this.featureName = featureName;
    }


    @Override
    public double normalize(double value) {
        return (value - this.mean) / this.stdDeviation;
    }

    @Override
    public String featureName() {
        return this.featureName;
    }

    @Override
    public FeatureNormalizerFactory.Type getType() {
        return FeatureNormalizerFactory.Type.STANDARD;
    }

}
