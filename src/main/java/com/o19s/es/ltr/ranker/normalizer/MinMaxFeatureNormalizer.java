package com.o19s.es.ltr.ranker.normalizer;

import org.apache.lucene.search.Explanation;

import java.util.Collections;
import java.util.List;

/**
 * MinMax Feature Normalization
 * Generally following the standard laid out by sklearn:
 *   (value / (max - min)) + min to give a normalized 0-1 feature value
 *
 * See
 * https://scikit-learn.org/stable/modules/generated/sklearn.preprocessing.MinMaxScaler.html
 */
public class MinMaxFeatureNormalizer implements Normalizer  {
    float maximum;
    float minimum;

    public MinMaxFeatureNormalizer(float minimum, float maximum) {
        if (minimum >= maximum) {
            throw new IllegalArgumentException("Minimum " + Double.toString(minimum) +
                                               " must be smaller than than maximum: " +
                                                Double.toString(maximum));
        }
        this.minimum = minimum;
        this.maximum = maximum;
    }

    @Override
    public float normalize(float value) {
        return  (value - minimum) / (maximum - minimum);
    }

    @Override
    public Explanation explain(Explanation wrappedQueryExplain) {
        float val = wrappedQueryExplain.getValue().floatValue();
        float normed = normalize(wrappedQueryExplain.getValue().floatValue());
        String numerator = "val:" + Float.toString(val) + " - min:" + Float.toString(this.minimum);
        String denominator = " max:" + Float.toString(maximum) + " - min:" + Float.toString(this.minimum);

        List<Explanation> subExplains = Collections.singletonList(wrappedQueryExplain);

        return Explanation.match(normed,
                "Min-Max Normalized LTR Feature: " + numerator + " / " + denominator,
                wrappedQueryExplain);
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
