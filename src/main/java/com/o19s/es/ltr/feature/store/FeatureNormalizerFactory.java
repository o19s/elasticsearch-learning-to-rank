package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.ranker.normalizer.FeatureNormalizer;
import com.o19s.es.ltr.ranker.normalizer.MinMaxFeatureNormalizer;
import com.o19s.es.ltr.ranker.normalizer.StandardFeatureNormalizer;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentParser;

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

    public static final ObjectParser.NamedObjectParser<FeatureNormDefinition, Void> PARSER;
    private static final ParseField STANDARD = new ParseField("standard");
    private static final ParseField MIN_MAX = new ParseField("min_max");

    static {

        PARSER = (XContentParser p, Void c, String featureName) -> {
            // this seems really intended for switching on the key (here featureName) and making
            // a decision, when in reality, we want to look a layer deeper and switch on that
            ObjectParser<FeatureNormConsumer, Void> parser = new ObjectParser<>("feature_normalizers",
                                                                                  FeatureNormConsumer::new);

            BiConsumer<FeatureNormConsumer, StandardFeatureNormDefinition> setStd = (s, v) -> {
                v.setFeatureName(featureName);
                s.setFtrNormDefn(v);
            };

            BiConsumer<FeatureNormConsumer, MinMaxFeatureNormDefinition> setMinMax = (s, v) -> {
                v.setFeatureName(featureName);
                s.setFtrNormDefn(v);
            };

            parser.declareObject(setStd, StandardFeatureNormDefinition.PARSER, STANDARD);
            parser.declareObject(setMinMax, MinMaxFeatureNormDefinition.PARSER, MIN_MAX);

            FeatureNormConsumer parsedNorm  = parser.parse(p, c);
            return parsedNorm.ftrNormDefn;

        };

    }

    // A temp holder for parsing out of xcontent
    private static class FeatureNormConsumer {
        String featureName;
        FeatureNormDefinition ftrNormDefn;

        FeatureNormConsumer() {
        }

        public FeatureNormDefinition getFtrNormDefn() {
            return this.ftrNormDefn;
        }

        public void setFtrNormDefn(FeatureNormDefinition ftrNormDefn) {
            this.ftrNormDefn = ftrNormDefn;
        }
    }



    public  FeatureNormDefinition createFromStreamInput(StreamInput input) throws IOException {
        Type normType = Type.readFromStream(input);
        String featureName = input.readString();
        if (normType == Type.STANDARD) {
            return new StandardFeatureNormDefinition(input);
        } else if (normType == Type.MIN_MAX) {
            return new MinMaxFeatureNormDefinition(input);
        }
        // note the Type constructor throws on this condition as well
        throw new ElasticsearchException("unknown normalizer type during deserialization");
    }

    public void writeTo(StreamOutput output, FeatureNormDefinition ftrNorm) throws IOException {
        ftrNorm.normType().writeTo(output);
        ftrNorm.writeTo(output);
    }

}
