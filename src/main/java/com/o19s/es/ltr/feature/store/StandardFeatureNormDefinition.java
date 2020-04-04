package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.ranker.normalizer.FeatureNormalizer;
import com.o19s.es.ltr.ranker.normalizer.StandardFeatureNormalizer;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

public class StandardFeatureNormDefinition implements FeatureNormDefinition {

    private double mean;
    private double stdDeviation;
    private String featureName;

    public static final ObjectParser<StandardFeatureNormDefinition, Void> PARSER;
    private static final ParseField STD_DEVIATION = new ParseField("standard_deviation");
    private static final ParseField MEAN = new ParseField("mean");


    static {
        PARSER = new ObjectParser<>("standard", StandardFeatureNormDefinition::new);
        PARSER.declareDouble(StandardFeatureNormDefinition::setMean, MEAN);
        PARSER.declareDouble(StandardFeatureNormDefinition::setStdDeviation, STD_DEVIATION);
    }

    public StandardFeatureNormDefinition() {
        this.mean = 0.0;
        this.stdDeviation = 0.0;
    }

    StandardFeatureNormDefinition(StreamInput input) throws IOException {
        this.featureName = input.readString();
        this.mean = input.readDouble();
        this.stdDeviation = input.readDouble();
    }

    public void setMean(double mean) {
        this.mean = mean;
    }

    public void setStdDeviation(double stdDeviation) {
        this.stdDeviation = stdDeviation;
    }

    public void setFeatureName(String featureName) {
        this.featureName = featureName;
    }

    @Override
    public String name() {
        return "standard";
    }

    @Override
    public String type() {
        return null;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(this.featureName);
        out.writeDouble(this.mean);
        out.writeDouble(this.stdDeviation);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(this.name());
        builder.startObject();
        builder.field(this.MEAN.getPreferredName(), this.mean);
        builder.field(this.STD_DEVIATION.getPreferredName(), this.stdDeviation);
        builder.endObject();
        builder.endObject();
        return builder;    }

    @Override
    public FeatureNormalizer createFeatureNorm() {
        return new StandardFeatureNormalizer(this.featureName, this.mean, this.stdDeviation);
    }

    @Override
    public String featureName() {
        return this.featureName;
    }

    @Override
    public FeatureNormalizerFactory.Type normType() {
        return FeatureNormalizerFactory.Type.STANDARD;
    }
}
