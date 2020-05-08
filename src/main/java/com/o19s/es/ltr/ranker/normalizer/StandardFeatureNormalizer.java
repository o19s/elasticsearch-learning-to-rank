package com.o19s.es.ltr.ranker.normalizer;

public class StandardFeatureNormalizer implements Normalizer {

    private float mean;
    private float stdDeviation;


    public StandardFeatureNormalizer(float mean, float stdDeviation) {
        this.mean = mean;
        this.stdDeviation = stdDeviation;
    }


    @Override
    public float normalize(float value) {
        return (value - this.mean) / this.stdDeviation;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof StandardFeatureNormalizer)) return false;
        StandardFeatureNormalizer that = (StandardFeatureNormalizer) other;

        if (this.mean != that.mean) return false;
        if (this.stdDeviation != that.stdDeviation) return false;

        return true;

    }

    @Override
    public int hashCode() {
        int hashCode = Float.hashCode(this.mean);
        hashCode += 31 * Float.hashCode(this.stdDeviation);
        return hashCode;
    }

}