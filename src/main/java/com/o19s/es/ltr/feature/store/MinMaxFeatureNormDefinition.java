package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.ranker.normalizer.FeatureNormalizer;
import com.o19s.es.ltr.ranker.normalizer.MinMaxFeatureNormalizer;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

public class MinMaxFeatureNormDefinition implements FeatureNormDefinition {

    private double minimum;
    private double maximum;
    private String featureName;

    public static final ObjectParser<MinMaxFeatureNormDefinition, Void> PARSER;
    private static final ParseField MINIMUM = new ParseField("minimum");
    private static final ParseField MAXIMUM = new ParseField("maximum");

    static {
        PARSER = new ObjectParser<>("min_max", MinMaxFeatureNormDefinition::new);
        PARSER.declareDouble(MinMaxFeatureNormDefinition::setMinimum, MINIMUM);
        PARSER.declareDouble(MinMaxFeatureNormDefinition::setMaximum, MAXIMUM);
    }

    MinMaxFeatureNormDefinition(StreamInput input) throws IOException {
        this.featureName = input.readString();
        this.minimum = input.readDouble();
        this.maximum = input.readDouble();
    }

    MinMaxFeatureNormDefinition() {
        this.maximum = Double.MAX_VALUE;
        this.minimum = Double.MIN_VALUE;
    }

    @Override
    public FeatureNormalizer createFeatureNorm() {
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
    public FeatureNormalizerFactory.Type normType() {
        return FeatureNormalizerFactory.Type.MIN_MAX;
    }

    @Override
    public String name() {
        return "min_max";
    }

    @Override
    public String type() {
        return null;
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

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(this.featureName);
        out.writeDouble(this.minimum);
        out.writeDouble(this.maximum);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(this.name());
        builder.startObject();
        builder.field(this.MINIMUM.getPreferredName(), this.minimum);
        builder.field(this.MAXIMUM.getPreferredName(), this.maximum);
        builder.endObject();
        builder.endObject();
        return builder;
    }
}
