package com.o19s.es.ltr.ranker.normalizer;

public class StandardFeatureNormalizer implements FeatureNormalizer {

    private float mean;
    private float stdDeviation;
    private String featureName;


    public StandardFeatureNormalizer(String featureName, float mean, float stdDeviation) {
        this.mean = mean;
        this.stdDeviation = stdDeviation;
        this.featureName = featureName;
    }


    @Override
    public float normalize(float value) {
        return (value - this.mean) / this.stdDeviation;
    }

}
