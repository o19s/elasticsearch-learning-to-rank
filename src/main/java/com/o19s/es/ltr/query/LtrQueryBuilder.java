/*
 * Copyright [2016] Doug Turnbull
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.o19s.es.ltr.query;

import ciir.umass.edu.learning.Ranker;
import com.o19s.es.ltr.feature.PrebuiltFeature;
import com.o19s.es.ltr.feature.PrebuiltFeatureSet;
import com.o19s.es.ltr.feature.PrebuiltLtrModel;
import com.o19s.es.ltr.ranker.ranklib.RankLibScriptEngine;
import com.o19s.es.ltr.ranker.ranklib.RanklibRanker;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class LtrQueryBuilder extends AbstractQueryBuilder<LtrQueryBuilder> {
    public static final String NAME = "ltr";
    private static final ObjectParser<LtrQueryBuilder, QueryParseContext> PARSER;

    Script _rankLibScript;
    List<QueryBuilder> _features;

    static {
        PARSER = new ObjectParser<>(NAME, LtrQueryBuilder::new);
        declareStandardFields(PARSER);
        PARSER.declareObjectArray(
                (ltr, features) -> ltr.features(features),
                (parser, context) -> context.parseInnerQueryBuilder().get(),
                new ParseField("features"));
        PARSER.declareField(
                (parser, ltr, context) -> ltr.rankerScript(Script.parse(parser, "ranklib")),
                new ParseField("model"), ObjectParser.ValueType.OBJECT_OR_STRING);
    }


    public LtrQueryBuilder() {
    }

    public LtrQueryBuilder(StreamInput in) throws IOException {
        super(in);
        _features = readQueries(in);
        _rankLibScript = new Script(in);
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        // only the superclass has state
        writeQueries(out, _features);
        _rankLibScript.writeTo(out);
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        printBoostAndQueryName(builder);
        doXArrayContent("features", this._features, builder, params);
        builder.field("model", _rankLibScript);
        builder.endObject();
    }

    private static void doXArrayContent(String field, List<QueryBuilder> clauses, XContentBuilder builder, Params params)
            throws IOException {
        if (clauses.isEmpty()) {
            return;
        }
        builder.startArray(field);
        for (QueryBuilder clause : clauses) {
            clause.toXContent(builder, params);
        }
        builder.endArray();
    }

    public static Optional<LtrQueryBuilder> fromXContent(QueryParseContext parseContext) throws IOException {
        final LtrQueryBuilder builder;
        try {
            builder = PARSER.apply(parseContext.parser(), parseContext);
        } catch (IllegalArgumentException e) {
            throw new ParsingException(parseContext.parser().getTokenLocation(), e.getMessage(), e);
        }
        if (builder._rankLibScript == null) {
            throw new ParsingException(parseContext.parser().getTokenLocation(),
                    "[ltr] query requires a model, none specified");
        }
        return Optional.of(builder);
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        if (_features == null || _rankLibScript == null) {
            return new MatchAllDocsQuery();
        }
        List<PrebuiltFeature> features = new ArrayList<PrebuiltFeature>(_features.size());
        for(QueryBuilder builder: _features) {
            features.add(new PrebuiltFeature(builder.queryName(), builder.toQuery(context)));
        }
        features = Collections.unmodifiableList(features);
        // pull model out of script
        RankLibScriptEngine.RankLibExecutableScript rankerScript =
                (RankLibScriptEngine.RankLibExecutableScript)context.getExecutableScript(_rankLibScript, ScriptContext.Standard.SEARCH);
        RanklibRanker ranker = new RanklibRanker((Ranker)rankerScript.run());
        PrebuiltLtrModel model = new PrebuiltLtrModel(ranker.name(), ranker, new PrebuiltFeatureSet(queryName(), features));
        return RankerQuery.build(model);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(_rankLibScript, _features);
    }

    @Override
    protected boolean doEquals(LtrQueryBuilder other) {
        return Objects.equals(_rankLibScript, other._rankLibScript) &&
                Objects.equals(_features, other._features);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    public final Script rankerScript() {
        return _rankLibScript;
    }
    public final LtrQueryBuilder rankerScript(Script rankLibModel) {
         _rankLibScript = rankLibModel;
         return this;
    }

    public List<QueryBuilder> features() {return _features;}
    public final LtrQueryBuilder features(List<QueryBuilder> features) {
        _features = features;
        return this;
    }


}
