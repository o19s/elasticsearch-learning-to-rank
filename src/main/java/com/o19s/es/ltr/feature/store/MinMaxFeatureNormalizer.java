package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.feature.FeatureNormalizer;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ObjectParser;

import java.io.IOException;

public class MinMaxFeatureNormalizer implements FeatureNormalizer  {
    double maximum;
    double minimum;
    String featureName;

    public static final ObjectParser<MinMaxFeatureNormalizer, Void> PARSER;
    private static final ParseField MINIMUM = new ParseField("minimum");
    private static final ParseField MAXIMUM = new ParseField("maximum");

    static {
        PARSER = new ObjectParser<>("min_max", MinMaxFeatureNormalizer::new);
        PARSER.declareDouble(MinMaxFeatureNormalizer::setMinimum, MINIMUM);
        PARSER.declareDouble(MinMaxFeatureNormalizer::setMaximum, MAXIMUM);
    }


    public MinMaxFeatureNormalizer() {
        maximum = Double.MAX_VALUE;
        minimum = Double.MIN_VALUE;
    }

    public MinMaxFeatureNormalizer(StreamInput input) throws IOException {
        this.minimum = input.readDouble();
        this.maximum = input.readDouble();
        this.featureName = input.readString();
    }


    public void setMinimum(double min) {
        if (min >= this.maximum) {
            throw new ElasticsearchException("Minimum " + Double.toString(min) + " must be smaller than than maximum");
        }
        this.minimum = min;
    }

    public void setMaximum(double max) {
        if (max <= this.minimum) {
            throw new ElasticsearchException("Maximum " + Double.toString(max) + " must be larger than minimum");
        }
        this.maximum = max;
    }

    public void setFeatureName(String featureName) {
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

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeDouble(this.minimum);
        out.writeDouble(this.maximum);
        out.writeString(this.featureName);

    }
}
