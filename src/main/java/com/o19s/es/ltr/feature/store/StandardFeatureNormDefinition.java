package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.ranker.normalizer.Normalizer;
import com.o19s.es.ltr.ranker.normalizer.StandardFeatureNormalizer;
import org.elasticsearch.ElasticsearchException;
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
        this.setStdDeviation(input.readFloat());
    }

    public void setMean(float mean) {
        this.mean = mean;
    }

    public void setStdDeviation(float stdDeviation) {
        if (stdDeviation <= 0.0f) {
            throw new ElasticsearchException("Standard Deviation Must Be Positive. " +
                                             " You passed: " + Float.toString(stdDeviation));
        }
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
    public Normalizer createFeatureNorm() {
        return new StandardFeatureNormalizer(this.mean, this.stdDeviation);
    }

    @Override
    public String featureName() {
        return this.featureName;
    }

    @Override
    public StoredFeatureNormalizers.Type normType() {
        return StoredFeatureNormalizers.Type.STANDARD;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StandardFeatureNormDefinition)) return false;
        StandardFeatureNormDefinition that = (StandardFeatureNormDefinition) o;

        if (!this.featureName.equals(that.featureName)) return false;
        if (this.stdDeviation != that.stdDeviation) return false;
        if (this.mean != that.mean) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int hash = this.featureName.hashCode();
        hash = (hash * 31) + Float.hashCode(this.stdDeviation);
        hash = (hash * 31) + Float.hashCode(this.mean);

        return hash;
    }
}
