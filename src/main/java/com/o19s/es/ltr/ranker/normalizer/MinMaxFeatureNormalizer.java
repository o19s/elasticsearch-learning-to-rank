package com.o19s.es.ltr.ranker.normalizer;

import org.elasticsearch.ElasticsearchException;

public class MinMaxFeatureNormalizer implements Normalizer  {
    float maximum;
    float minimum;

    public MinMaxFeatureNormalizer(float minimum, float maximum) {
        if (minimum >= maximum) {
            throw new ElasticsearchException("Minimum " + Double.toString(minimum) +
                                             " must be smaller than than maximum: " +
                                              Double.toString(maximum));
        }
        this.minimum = minimum;
        this.maximum = maximum;
    }

    @Override
    public float normalize(float value) {
        return value / (maximum - minimum);
    }


    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MinMaxFeatureNormalizer)) return false;
        MinMaxFeatureNormalizer that = (MinMaxFeatureNormalizer) other;

        if (this.minimum != that.minimum) return false;
        if (this.maximum != that.maximum) return false;

        return true;

    }

    @Override
    public int hashCode() {
        int hashCode = Float.hashCode(this.minimum);
        hashCode += 31 * Float.hashCode(this.maximum);
        return hashCode;
    }

}
