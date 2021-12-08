/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.normalizer.FeatureNormalizingRanker;
import com.o19s.es.ltr.ranker.normalizer.Normalizer;
import com.o19s.es.ltr.ranker.parser.LtrRankerParser;
import com.o19s.es.ltr.ranker.parser.LtrRankerParserFactory;
import org.elasticsearch.Version;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.xcontent.ObjectParser;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.json.JsonXContent;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.elasticsearch.xcontent.NamedXContentRegistry.EMPTY;

public class StoredLtrModel implements StorableElement {
    public static final String TYPE = "model";

    private static final ObjectParser<ParsingState, Void> PARSER;
    private static final ParseField NAME = new ParseField("name");
    private static final ParseField FEATURE_SET = new ParseField("feature_set");
    private static final ParseField MODEL = new ParseField("model");

    private final String name;
    private final StoredFeatureSet featureSet;
    private final String rankingModelType;
    private final String rankingModel;
    private final boolean modelAsString;
    private final StoredFeatureNormalizers parsedFtrNorms;

    static {
        PARSER = new ObjectParser<>(TYPE, ParsingState::new);
        PARSER.declareString(ParsingState::setName, NAME);
        PARSER.declareObject(ParsingState::setFeatureSet,
                (parser, ctx) -> StoredFeatureSet.parse(parser),
                FEATURE_SET);
        PARSER.declareObject(ParsingState::setRankingModel, LtrModelDefinition.PARSER,
                MODEL);
    }

    public StoredLtrModel(String name, StoredFeatureSet featureSet, LtrModelDefinition definition) {
        this(name, featureSet, definition.type, definition.definition, definition.modelAsString, definition.featureNormalizers);
    }

    public StoredLtrModel(String name, StoredFeatureSet featureSet, String rankingModelType, String rankingModel,
                          boolean modelAsString, StoredFeatureNormalizers featureNormalizerSet) {
        this.name = Objects.requireNonNull(name);
        this.featureSet = Objects.requireNonNull(featureSet);
        this.rankingModelType = Objects.requireNonNull(rankingModelType);
        this.rankingModel = Objects.requireNonNull(rankingModel);
        this.modelAsString = modelAsString;
        this.parsedFtrNorms = featureNormalizerSet;
    }

    public StoredLtrModel(StreamInput input) throws IOException {
        name = input.readString();
        featureSet = new StoredFeatureSet(input);
        rankingModelType = input.readString();
        rankingModel = input.readString();
        modelAsString = input.readBoolean();
        if (input.getVersion().onOrAfter(Version.V_7_7_0)) {
            this.parsedFtrNorms = new StoredFeatureNormalizers(input);
        } else {
            this.parsedFtrNorms = new StoredFeatureNormalizers();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        featureSet.writeTo(out);
        out.writeString(rankingModelType);
        out.writeString(rankingModel);
        out.writeBoolean(modelAsString);
        if (out.getVersion().onOrAfter(Version.V_7_7_0)) {
            parsedFtrNorms.writeTo(out);
        }
    }

    public static StoredLtrModel parse(XContentParser parser) {
        return parse(parser, null);
    }

    public static StoredLtrModel parse(XContentParser parser, String name) {
        try {
            ParsingState state = PARSER.apply(parser, null);
            state.resolveName(parser, name);
            if (state.featureSet == null) {
                throw new ParsingException(parser.getTokenLocation(), "Field [feature_set] is mandatory");
            }
            if (state.rankingModel == null) {
                throw new ParsingException(parser.getTokenLocation(), "Field [model] is mandatory");
            }
            return new StoredLtrModel(state.getName(), state.featureSet, state.rankingModel);
        } catch (IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage(), iae);
        }
    }

    public CompiledLtrModel compile(LtrRankerParserFactory factory) throws IOException {
        LtrRankerParser modelParser = factory.getParser(rankingModelType);
        FeatureSet optimized = featureSet.optimize();
        LtrRanker ranker = modelParser.parse(optimized, rankingModel);
        Map<Integer, Normalizer> ordToNorms = parsedFtrNorms.compileOrdToNorms(optimized);
        if (ordToNorms.size() > 0) {
            ranker = new FeatureNormalizingRanker(ranker, ordToNorms);
        }
        return new CompiledLtrModel(name, optimized, ranker);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public boolean updatable() {
        return false;
    }

    /**
     * @return the set of features used by the stored model
     */
    public StoredFeatureSet featureSet() {
        return featureSet;
    }

    /**
     * @return the type of the stored ranking model
     */
    public String rankingModelType() {
        return rankingModelType;
    }

    /**
     * @return the stored ranking model
     */
    public String rankingModel() {
        return rankingModel;
    }

    /**
     * @return the stored set of feature normalizers
     */
    public StoredFeatureNormalizers getFeatureNormalizers() { return this.parsedFtrNorms; }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(NAME.getPreferredName(), name);
        builder.field(FEATURE_SET.getPreferredName());
        featureSet.toXContent(builder, params);
        builder.startObject(MODEL.getPreferredName());
        builder.field(LtrModelDefinition.MODEL_TYPE.getPreferredName(), rankingModelType);
        builder.field(LtrModelDefinition.MODEL_DEFINITION.getPreferredName());
        if (modelAsString) {
            builder.value(rankingModel);
        } else {
            try (XContentParser parser = JsonXContent.jsonXContent.createParser(EMPTY,
                    LoggingDeprecationHandler.INSTANCE, rankingModel)
            ) {
                builder.copyCurrentStructure(parser);
            }
        }
        builder.field(LtrModelDefinition.FEATURE_NORMALIZERS.getPreferredName());
        this.parsedFtrNorms.toXContent(builder, params);
        builder.endObject();
        builder.endObject();
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StoredLtrModel)) return false;

        StoredLtrModel that = (StoredLtrModel) o;

        if (!name.equals(that.name)) return false;
        if (!featureSet.equals(that.featureSet)) return false;
        if (!rankingModelType.equals(that.rankingModelType)) return false;
        if (!parsedFtrNorms.equals(that.parsedFtrNorms)) return false;
        return rankingModel.equals(that.rankingModel);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + featureSet.hashCode();
        result = 31 * result + rankingModelType.hashCode();
        result = 31 * result + rankingModel.hashCode();
        result = 31 * result + parsedFtrNorms.hashCode();
        return result;
    }

    private static class ParsingState extends StorableElementParserState {
        StoredFeatureSet featureSet;
        LtrModelDefinition rankingModel;

        void setFeatureSet(StoredFeatureSet featureSet) {
            this.featureSet = featureSet;
        }

        void setRankingModel(LtrModelDefinition rankingModel) {
            this.rankingModel = rankingModel;
        }
    }

    public static class LtrModelDefinition implements Writeable {
        private String type;
        private String definition;
        private StoredFeatureNormalizers featureNormalizers;
        private boolean modelAsString;

        public static final ObjectParser<LtrModelDefinition, Void> PARSER;

        private static final ParseField MODEL_TYPE = new ParseField("type");
        private static final ParseField MODEL_DEFINITION = new ParseField("definition");
        private static final ParseField FEATURE_NORMALIZERS = new ParseField("feature_normalizers");

        static {
            PARSER = new ObjectParser<>("model", LtrModelDefinition::new);
            PARSER.declareString(LtrModelDefinition::setType,
                    MODEL_TYPE);
            PARSER.declareField((p, d, c) -> d.parseModel(p),
                    MODEL_DEFINITION,
                    ObjectParser.ValueType.OBJECT_ARRAY_OR_STRING);

            PARSER.declareNamedObjects(LtrModelDefinition::setNamedFeatureNormalizers,
                    StoredFeatureNormalizers.PARSER,
                    FEATURE_NORMALIZERS);
        }


        private LtrModelDefinition() {
            this.featureNormalizers = new StoredFeatureNormalizers();
        }

        public LtrModelDefinition(String type, String definition, boolean modelAsString) {
            this.type = type;
            this.definition = definition;
            this.modelAsString = modelAsString;
            this.featureNormalizers = new StoredFeatureNormalizers();
        }

        public LtrModelDefinition(StreamInput in) throws IOException {
            type = in.readString();
            definition = in.readString();
            modelAsString = in.readBoolean();
            if (in.getVersion().onOrAfter(Version.V_7_7_0)) {
                this.featureNormalizers = new StoredFeatureNormalizers(in);
            } else {
                this.featureNormalizers = new StoredFeatureNormalizers();
            }
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(type);
            out.writeString(definition);
            out.writeBoolean(modelAsString);
            if (out.getVersion().onOrAfter(Version.V_7_7_0)) {
                this.featureNormalizers.writeTo(out);
            }
        }


        private void setType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        public void setNamedFeatureNormalizers(List<FeatureNormDefinition> featureNormalizers) {
            this.featureNormalizers = new StoredFeatureNormalizers(featureNormalizers);
        }

        public String getDefinition() {
            return definition;
        }

        public boolean isModelAsString() {
            return modelAsString;
        }

        public StoredFeatureNormalizers getFtrNorms() {return this.featureNormalizers;}

        public static LtrModelDefinition parse(XContentParser parser, Void ctx) throws IOException {
            LtrModelDefinition def = PARSER.parse(parser, ctx);
            if (def.type == null) {
                throw new ParsingException(parser.getTokenLocation(), "Field [model.type] is mandatory");
            }
            if (def.definition == null) {
                throw new ParsingException(parser.getTokenLocation(), "Field [model.definition] is mandatory");
            }
            return def;
        }

        private void parseModel(XContentParser parser) throws IOException {
                if (parser.currentToken() == XContentParser.Token.VALUE_STRING) {
                    modelAsString = true;
                    definition = parser.text();
                } else {
                    try (XContentBuilder builder = JsonXContent.contentBuilder()) {
                        builder.copyCurrentStructure(parser);
                        modelAsString = false;
                        definition = Strings.toString(builder);
                    }
                }
        }
    }
}
