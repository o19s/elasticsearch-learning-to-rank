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

import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.parser.LtrRankerParser;
import com.o19s.es.ltr.ranker.parser.LtrRankerParserFactory;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.Objects;

import static org.elasticsearch.common.xcontent.ConstructingObjectParser.constructorArg;

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

    static {
        PARSER = new ObjectParser<>(TYPE, ParsingState::new);
        PARSER.declareString(ParsingState::setName, NAME);
        PARSER.declareObject(ParsingState::setFeatureSet,
                (parser, ctx) -> StoredFeatureSet.parse(parser),
                FEATURE_SET);
        PARSER.declareObject(ParsingState::setRankingModel, LtrModelDefinition.PARSER,
                MODEL);
    }

    public StoredLtrModel(String name, StoredFeatureSet featureSet, String rankingModelType, String rankingModel) {
        this.name = Objects.requireNonNull(name);
        this.featureSet = Objects.requireNonNull(featureSet);
        this.rankingModelType = Objects.requireNonNull(rankingModelType);
        this.rankingModel = Objects.requireNonNull(rankingModel);
    }

    public StoredLtrModel(StreamInput input) throws IOException {
        name = input.readString();
        featureSet = new StoredFeatureSet(input);
        rankingModelType = input.readString();
        rankingModel = input.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        featureSet.writeTo(out);
        out.writeString(rankingModelType);
        out.writeString(rankingModel);
    }

    public static StoredLtrModel parse(XContentParser parser) {
        try {
            ParsingState state = PARSER.apply(parser, null);
            if (state.name == null) {
                throw new ParsingException(parser.getTokenLocation(), "Field [name] is mandatory");
            }
            if (state.featureSet == null) {
                throw new ParsingException(parser.getTokenLocation(), "Field [feature_set] is mandatory");
            }
            if (state.rankingModel == null) {
                throw new ParsingException(parser.getTokenLocation(), "Field [model] is mandatory");
            }
            return new StoredLtrModel(state.name, state.featureSet, state.rankingModel.type, state.rankingModel.definition);
        } catch (IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage(), iae);
        }
    }

    public CompiledLtrModel compile(LtrRankerParserFactory factory) {
        LtrRankerParser modelParser = factory.getParser(rankingModelType);
        LtrRanker ranker = modelParser.parse(featureSet, rankingModel);
        return new CompiledLtrModel(name, featureSet, ranker);
    }

    /**
     * Name of the model
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * Type of the element
     */
    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public boolean updatable() {
        return false;
    }

    /**
     * The set of features used by this model
     */
    public StoredFeatureSet featureSet() {
        return featureSet;
    }

    public String rankingModelType() {
        return rankingModelType;
    }

    public String rankingModel() {
        return rankingModel;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(NAME.getPreferredName(), name);
        builder.field(FEATURE_SET.getPreferredName());
        featureSet.toXContent(builder, params);
        builder.startObject(MODEL.getPreferredName());
        builder.field(LtrModelDefinition.MODEL_TYPE.getPreferredName(), rankingModelType);
        builder.field(LtrModelDefinition.MODEL_DEFINITION.getPreferredName(), rankingModel);
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
        return rankingModel.equals(that.rankingModel);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + featureSet.hashCode();
        result = 31 * result + rankingModelType.hashCode();
        result = 31 * result + rankingModel.hashCode();
        return result;
    }

    private static class ParsingState {
        String name;
        StoredFeatureSet featureSet;
        LtrModelDefinition rankingModel;

        void setName(String name) {
            this.name = name;
        }

        void setFeatureSet(StoredFeatureSet featureSet) {
            this.featureSet = featureSet;
        }

        void setRankingModel(LtrModelDefinition rankingModel) {
            this.rankingModel = rankingModel;
        }
    }

    private static class LtrModelDefinition {
        final String type;
        final String definition;

        private static final ConstructingObjectParser<LtrModelDefinition, Void> PARSER;

        public static final ParseField MODEL_TYPE = new ParseField("type");
        public static final ParseField MODEL_DEFINITION = new ParseField("definition");

        static {
            PARSER = new ConstructingObjectParser<>("model",
                    x -> new LtrModelDefinition((String) x[0], (String) x[1]));
            PARSER.declareString(constructorArg(),
                    MODEL_TYPE);
            PARSER.declareString(constructorArg(),
                    MODEL_DEFINITION);
        }

        LtrModelDefinition(String type, String definition) {
            this.type = type;
            this.definition = definition;
        }
    }
}
