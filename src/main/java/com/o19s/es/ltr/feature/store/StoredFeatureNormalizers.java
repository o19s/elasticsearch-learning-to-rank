package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.feature.FeatureNormalizerSet;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.feature.NoOpFeatureNormalizerSet;
import com.o19s.es.ltr.ranker.normalizer.NoOpNormalizer;
import com.o19s.es.ltr.ranker.normalizer.Normalizer;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class StoredFeatureNormalizers {


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


    private Map<String, FeatureNormDefinition> featureNormalizers;

    public StoredFeatureNormalizers() {
        this.featureNormalizers = new HashMap<>();
    }

    public StoredFeatureNormalizers(final List<FeatureNormDefinition> ftrNormDefs) {
        this.featureNormalizers = new HashMap<>();
        for (FeatureNormDefinition ftrNorm: ftrNormDefs) {
            this.featureNormalizers.put(ftrNorm.featureName(), ftrNorm);
        }
    }

    public StoredFeatureNormalizers(StreamInput input) throws IOException {
        this.featureNormalizers = new HashMap<>();
        if (input.available() > 0) {
            int numFeatureNorms = input.readInt();
            for (int i = numFeatureNorms; i > 0; i--) {
                FeatureNormDefinition norm = this.createFromStreamInput(input);
                this.featureNormalizers.put(norm.featureName(), norm);
            }
        }
    }

    public Normalizer getNormalizer(String featureName) {
        return this.featureNormalizers.get(featureName).createFeatureNorm();
    }

    public static final String TYPE = "feature_normalizers";


    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject(); // begin feature norms
        for (Map.Entry<String, FeatureNormDefinition> ftrNormDefEntry: featureNormalizers.entrySet()) {
            builder.field(ftrNormDefEntry.getKey());
            ftrNormDefEntry.getValue().toXContent(builder, params);
        }
        builder.endObject(); // feature norms
        return builder;
    }

    public FeatureNormalizerSet compile(FeatureSet featureSet) {
        if (featureNormalizers.size() == 0) {
            return new NoOpFeatureNormalizerSet();
        }
        List<Normalizer> ftrNorms = new ArrayList<>(featureSet.size());

        for (int i = 0; i < featureSet.size(); i++) {
            ftrNorms.add(new NoOpNormalizer());
        }

        for (Map.Entry<String, FeatureNormDefinition> ftrNormDefEntry: featureNormalizers.entrySet()) {
            String featureName = ftrNormDefEntry.getValue().featureName();
            Normalizer ftrNorm = ftrNormDefEntry.getValue().createFeatureNorm();

            if (!featureSet.hasFeature(featureName)) {
                throw new ElasticsearchException("Feature " + featureName +
                                                 " not found in feature set " + featureSet.name());
            }

            int ord = featureSet.featureOrdinal(featureName);
            ftrNorms.set(ord, ftrNorm);
        }
        return new CompiledFeatureNormalizerSet(ftrNorms);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StoredFeatureNormalizers)) return false;
        StoredFeatureNormalizers that = (StoredFeatureNormalizers) o;

        return that.featureNormalizers.equals(this.featureNormalizers);
    }

    @Override
    public int hashCode() {
        return this.featureNormalizers.hashCode();
    }

    public int numNormalizers() {
        return this.featureNormalizers.size();
    }


    private  FeatureNormDefinition createFromStreamInput(StreamInput input) throws IOException {
        Type normType = Type.readFromStream(input);
        if (normType == Type.STANDARD) {
            return new StandardFeatureNormDefinition(input);
        } else if (normType == Type.MIN_MAX) {
            return new MinMaxFeatureNormDefinition(input);
        }
        // note the Type constructor throws on this condition as well
        throw new ElasticsearchException("unknown normalizer type during deserialization");
    }

    public void writeTo(StreamOutput output) throws IOException {
        output.writeInt(this.featureNormalizers.size());
        for (Map.Entry<String, FeatureNormDefinition> featureNormEntry : this.featureNormalizers.entrySet()) {
            featureNormEntry.getValue().normType().writeTo(output);
            featureNormEntry.getValue().writeTo(output);
        }
    }

}
