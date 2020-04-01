package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.feature.FeatureNormalizer;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.function.BiConsumer;


public class FeatureNormNamed implements Writeable {
    static final ObjectParser.NamedObjectParser<FeatureNormNamed, Void> PARSER;

    private static final ParseField STANDARD = new ParseField("standard");


    static {

        PARSER = (XContentParser p, Void c, String featureName) -> {
            ObjectParser<FeatureNormNamed, Void> parser = new ObjectParser<>("feature_normalizers");

            FeatureNormNamed rVal =  new FeatureNormNamed(featureName);

            BiConsumer<FeatureNormNamed, StandardFeatureNormalizer> setStd = (s, v) -> {
              rVal.setToStandardNormalizer(v);
            };

            parser.declareObject(setStd, StandardFeatureNormalizer.PARSER, STANDARD);
            return rVal;

        };

    }

    private String featureName;
    private FeatureNormalizer normalizer;

    public FeatureNormNamed(String featureName) {
        this.featureName = featureName;
    }

    public String getFeatureName() {
        return null;
    }

    public void setToStandardNormalizer(StandardFeatureNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
    }

    // TODO move out..
    public static class StandardFeatureNormalizer implements FeatureNormalizer {

        public static final ObjectParser<StandardFeatureNormalizer, Void> PARSER;

        static {
            PARSER = new ObjectParser<>("standard", StandardFeatureNormalizer::new);

        }

        @Override
        public double normalize(double value) {
            return 0;
        }

        public StandardFeatureNormalizer parse(XContentParser xcontent) throws IOException {
            return PARSER.parse(xcontent, null);
        }
    }
}
