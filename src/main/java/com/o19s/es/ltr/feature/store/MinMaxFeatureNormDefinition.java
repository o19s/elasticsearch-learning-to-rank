package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.ranker.normalizer.MinMaxFeatureNormalizer;
import com.o19s.es.ltr.ranker.normalizer.Normalizer;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

public class MinMaxFeatureNormDefinition implements FeatureNormDefinition {

    private float minimum;
    private float maximum;
    private String featureName;

    public static final ObjectParser<MinMaxFeatureNormDefinition, Void> PARSER;
    private static final ParseField MINIMUM = new ParseField("minimum");
    private static final ParseField MAXIMUM = new ParseField("maximum");

    static {
        PARSER = new ObjectParser<>("min_max", MinMaxFeatureNormDefinition::new);
        PARSER.declareFloat(MinMaxFeatureNormDefinition::setMinimum, MINIMUM);
        PARSER.declareFloat(MinMaxFeatureNormDefinition::setMaximum, MAXIMUM);
    }

    MinMaxFeatureNormDefinition(StreamInput input) throws IOException {
        this.featureName = input.readString();
        this.minimum = input.readFloat();
        this.maximum = input.readFloat();
    }

    MinMaxFeatureNormDefinition() {
        this.maximum = Float.MAX_VALUE;
        this.minimum = Float.MIN_VALUE;
    }

    @Override
    public Normalizer createFeatureNorm() {
        return new MinMaxFeatureNormalizer(this.featureName, this.minimum, this.maximum);
    }

    @Override
    public String featureName() {
        return this.featureName;
    }

    public void setFeatureName(String featureName) {
        this.featureName = featureName;
    }

    @Override
    public StoredFeatureNormalizers.Type normType() {
        return StoredFeatureNormalizers.Type.MIN_MAX;
    }

    @Override
    public String name() {
        return "min_max";
    }

    @Override
    public String type() {
        return null;
    }


    public void setMinimum(float min) {
        if (min >= this.maximum) {
            throw new ElasticsearchException("Minimum " + Float.toString(min) + " must be smaller than than maximum");
        }
        this.minimum = min;
    }

    public void setMaximum(float max) {
        if (max <= this.minimum) {
            throw new ElasticsearchException("Maximum " + Float.toString(max) + " must be larger than minimum");
        }
        this.maximum = max;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(this.featureName);
        out.writeFloat(this.minimum);
        out.writeFloat(this.maximum);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(this.name());
        builder.startObject();
        builder.field(MINIMUM.getPreferredName(), this.minimum);
        builder.field(MAXIMUM.getPreferredName(), this.maximum);
        builder.endObject();
        builder.endObject();
        return builder;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MinMaxFeatureNormDefinition)) return false;
        MinMaxFeatureNormDefinition that = (MinMaxFeatureNormDefinition) o;

        if (!this.featureName.equals(that.featureName)) return false;
        if (this.minimum != that.minimum) return false;
        if (this.maximum != that.maximum) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int hash = this.featureName.hashCode();
        hash = (hash * 31) + Float.hashCode(this.minimum);
        hash = (hash * 31) + Float.hashCode(this.maximum);

        return hash;
    }
}
