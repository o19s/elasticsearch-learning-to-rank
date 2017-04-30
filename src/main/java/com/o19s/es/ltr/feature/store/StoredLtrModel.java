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

import com.o19s.es.ltr.feature.LtrModel;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.parser.LtrRankerParser;
import com.o19s.es.ltr.ranker.parser.LtrRankerParserFactory;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentParser;

import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_ARRAY_HEADER;
import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_OBJECT_HEADER;
import static org.elasticsearch.common.xcontent.ConstructingObjectParser.constructorArg;

public class StoredLtrModel implements LtrModel, Accountable {
    private static final long BASE_RAM_USED = RamUsageEstimator.shallowSizeOfInstance(StoredLtrModel.class);
    private final String name;
    private final StoredFeatureSet featureSet;
    private final LtrRanker ranker;

    private static final ObjectParser<ParsingState, Void> PARSER;
    static {
        PARSER = new ObjectParser<>("ltr_model", ParsingState::new);
        PARSER.declareString(ParsingState::setName, new ParseField("name"));
        PARSER.declareObject(ParsingState::setFeatureSet,
                (parser, ctx) -> StoredFeatureSet.parse(parser),
                new ParseField("feature_set"));
        PARSER.declareObject(ParsingState::setRankingModel, LtrModelDefinition.PARSER,
                new ParseField("model"));
    }

    public StoredLtrModel(String name, StoredFeatureSet featureSet, LtrRanker ranker) {
        this.name = name;
        this.featureSet = featureSet;
        this.ranker = ranker;
    }

    public static StoredLtrModel parse(XContentParser parser, LtrRankerParserFactory factory) {
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
            LtrRankerParser modelParser = factory.getParser(state.rankingModel.type);
            LtrRanker ranker = modelParser.parse(state.featureSet, state.rankingModel.definition);
            return new StoredLtrModel(state.name, state.featureSet, ranker);
        } catch (IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage(), iae);
        }
    }

    /**
     * Name of the model
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * Return the {@link LtrRanker} implementation used by this model
     */
    @Override
    public LtrRanker ranker() {
        return ranker;
    }

    /**
     * The set of features used by this model
     */
    @Override
    public StoredFeatureSet featureSet() {
        return featureSet;
    }

    /**
     * Return the memory usage of this object in bytes. Negative values are illegal.
     */
    @Override
    public long ramBytesUsed() {
        return BASE_RAM_USED + name.length() * Character.BYTES + NUM_BYTES_ARRAY_HEADER
                + featureSet.ramBytesUsed()
                + (ranker instanceof Accountable ?
                ((Accountable)ranker).ramBytesUsed() : featureSet.size() * NUM_BYTES_OBJECT_HEADER);
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
        static {
            PARSER = new ConstructingObjectParser<>("model",
                    x -> new LtrModelDefinition((String) x[0], (String) x[1]));
            PARSER.declareString(constructorArg(),
                    new ParseField("type"));
            PARSER.declareString(constructorArg(),
                    new ParseField("definition"));
        }

        LtrModelDefinition(String type, String definition) {
            this.type = type;
            this.definition = definition;
        }
    }
}
