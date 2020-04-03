package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.feature.FeatureNormalizer;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.search.aggregations.metrics.Min;

import java.io.IOException;
import java.util.function.BiConsumer;

// Factory & parsing for feature norms -> make to regular factory class?
public class FeatureNormalizerFactory {

    public enum Type implements Writeable {
        STANDARD,
        MIN_MAX;


        public static Type readFromStream(StreamInput in) throws IOException {
            int ord = in.readVInt();
            for (Type type: Type.values()) {
                if (type.ordinal() == ord) {
                    return type;
                }
            }
            throw new ElasticsearchException("unknown normalizer type during serialization [" + ord + "]");
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeVInt(this.ordinal());
        }
    }

    public static final ObjectParser.NamedObjectParser<FeatureNormalizer, Void> PARSER;
    private static final ParseField STANDARD = new ParseField("standard");
    private static final ParseField MIN_MAX = new ParseField("min_max");

    static {

        PARSER = (XContentParser p, Void c, String featureName) -> {
            // this seems really intended for switching on the key (here featureName) and making
            // a decision, when in reality, we want to look a layer deeper and switch on that
            ObjectParser<FeatureNormConsumer, Void> parser = new ObjectParser<>("feature_normalizers",
                                                                                  FeatureNormConsumer::new);

            BiConsumer<FeatureNormConsumer, StandardFeatureNormalizer> setStd = (s, v) -> {
                v.setFeatureName(featureName);
                s.setFeatureNormalizer(v);
            };

            BiConsumer<FeatureNormConsumer, MinMaxFeatureNormalizer> setMinMax = (s, v) -> {
                v.setFeatureName(featureName);
                s.setFeatureNormalizer(v);
            };

            parser.declareObject(setStd, StandardFeatureNormalizer.PARSER, STANDARD);
            parser.declareObject(setMinMax, MinMaxFeatureNormalizer.PARSER, MIN_MAX);

            FeatureNormConsumer parsedNorm  = parser.parse(p, c);
            return parsedNorm.featureNormalizer;

        };

    }

    // TODO revisit deleting this dumb class needed for a passthrough
    private static class FeatureNormConsumer {
        String featureName;
        FeatureNormalizer featureNormalizer;

        public FeatureNormConsumer() {
        }

        public FeatureNormalizer getFeatureNormalizer() {
            return this.featureNormalizer;
        }

        public void setFeatureNormalizer(FeatureNormalizer featureNormalizer) {
            this.featureNormalizer = featureNormalizer;
        }
    }



    public  FeatureNormalizer createFromStreamInput(StreamInput input) throws IOException {
        Type normType = Type.readFromStream(input);
        if (normType == Type.STANDARD) {
            return new StandardFeatureNormalizer(input);
        } else if (normType == Type.MIN_MAX) {
            //return new MinMaxNormalizer(in);
        }
        throw new ElasticsearchException("unknown normalizer type during deserialization"); // note the Type constructor throws on this condition as well
    }

    public void writeTo(StreamOutput output, FeatureNormalizer ftrNorm) throws IOException {
        ftrNorm.getType().writeTo(output);
        ftrNorm.writeTo(output);
    }

}
