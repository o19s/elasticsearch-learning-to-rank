package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.feature.FeatureNormalizer;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ObjectParser;

import java.io.IOException;

public class StandardFeatureNormalizer implements FeatureNormalizer {

    private double mean;
    private double stdDeviation;
    private String featureName;

    public static final ObjectParser<StandardFeatureNormalizer, Void> PARSER;
    private static final ParseField STD_DEVIATION = new ParseField("standard_deviation");
    private static final ParseField MEAN = new ParseField("mean");


    static {
        PARSER = new ObjectParser<>("standard", StandardFeatureNormalizer::new);
        PARSER.declareDouble(StandardFeatureNormalizer::setMean, MEAN);
        PARSER.declareDouble(StandardFeatureNormalizer::setStdDeviation, STD_DEVIATION);
    }

    public StandardFeatureNormalizer() {
        this.mean = 0.0;
        this.stdDeviation = 0.0;
    }

    public StandardFeatureNormalizer(StreamInput input) throws IOException {
        this.mean = input.readDouble();
        this.stdDeviation = input.readDouble();
        this.featureName = input.readString();
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

    public void setMean(double mean) {
        this.mean = mean;
    }

    public double getMean() { // GRR!
        return this.mean;
    }

    public void setStdDeviation(double stdDeviation) {
        this.stdDeviation = stdDeviation;
    }

    public double getStdDeviation() { // GRR!
        return this.stdDeviation;
    }

    public void setFeatureName(String featureName) {
        this.featureName = featureName;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeDouble(this.mean);
        out.writeDouble(this.stdDeviation);
        out.writeString(this.featureName);
    }
}
