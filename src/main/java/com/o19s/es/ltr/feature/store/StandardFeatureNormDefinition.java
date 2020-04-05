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

    private float mean;
    private float stdDeviation;
    private String featureName;

    public static final ObjectParser<StandardFeatureNormDefinition, Void> PARSER;
    private static final ParseField STD_DEVIATION = new ParseField("standard_deviation");
    private static final ParseField MEAN = new ParseField("mean");


    static {
        PARSER = new ObjectParser<>("standard", StandardFeatureNormDefinition::new);
        PARSER.declareFloat(StandardFeatureNormDefinition::setMean, MEAN);
        PARSER.declareFloat(StandardFeatureNormDefinition::setStdDeviation, STD_DEVIATION);
    }

    public StandardFeatureNormDefinition() {
        this.mean = 0.0f;
        this.stdDeviation = 0.0f;
    }

    StandardFeatureNormDefinition(StreamInput input) throws IOException {
        this.featureName = input.readString();
        this.mean = input.readFloat();
        this.stdDeviation = input.readFloat();
    }

    public void setMean(float mean) {
        this.mean = mean;
    }

    public void setStdDeviation(float stdDeviation) {
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
        out.writeFloat(this.mean);
        out.writeFloat(this.stdDeviation);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(this.name());
        builder.startObject();
        builder.field(MEAN.getPreferredName(), this.mean);
        builder.field(STD_DEVIATION.getPreferredName(), this.stdDeviation);
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
    public StoredFeatureNormalizerSet.Type normType() {
        return StoredFeatureNormalizerSet.Type.STANDARD;
    }
}
