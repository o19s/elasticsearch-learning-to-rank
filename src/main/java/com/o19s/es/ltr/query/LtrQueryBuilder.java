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

import com.o19s.es.ltr.feature.PrebuiltFeature;
import com.o19s.es.ltr.feature.PrebuiltFeatureSet;
import com.o19s.es.ltr.feature.PrebuiltLtrModel;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.ranklib.RankLibScriptEngine;
import com.o19s.es.ltr.utils.AbstractQueryBuilderUtils;
import org.apache.lucene.search.Query;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.xcontent.ObjectParser;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryRewriteContext;
import org.elasticsearch.index.query.SearchExecutionContext;
import org.elasticsearch.index.query.Rewriteable;
import org.elasticsearch.script.Script;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class LtrQueryBuilder extends AbstractQueryBuilder<LtrQueryBuilder> {
    public static final String NAME = "ltr";
    private static final ObjectParser<LtrQueryBuilder, Void> PARSER;
    private static final String DEFAULT_SCRIPT_LANG = "ranklib";

    static {
        PARSER = new ObjectParser<>(NAME, LtrQueryBuilder::new);
        declareStandardFields(PARSER);
        PARSER.declareObjectArray(
                LtrQueryBuilder::features,
                (parser, context) -> parseInnerQueryBuilder(parser),
                new ParseField("features"));
        PARSER.declareField(
                (parser, ltr, context) -> ltr.rankerScript(Script.parse(parser, DEFAULT_SCRIPT_LANG)),
                new ParseField("model"), ObjectParser.ValueType.OBJECT_OR_STRING);
    }

    private Script _rankLibScript;
    private List<QueryBuilder> _features;

    public LtrQueryBuilder() {
    }

    public LtrQueryBuilder(Script _rankLibScript, List<QueryBuilder> features) {
        this._rankLibScript = _rankLibScript;
        this._features = features;
    }

    public LtrQueryBuilder(StreamInput in) throws IOException {
        super(in);
        _features = AbstractQueryBuilderUtils.readQueries(in);
        _rankLibScript = new Script(in);
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

    public static LtrQueryBuilder fromXContent(XContentParser parser) throws IOException {
        final LtrQueryBuilder builder;
        try {
            builder = PARSER.apply(parser, null);
        } catch (IllegalArgumentException e) {
            throw new ParsingException(parser.getTokenLocation(), e.getMessage(), e);
        }
        if (builder._rankLibScript == null) {
            throw new ParsingException(parser.getTokenLocation(),
                    "[ltr] query requires a model, none specified");
        }
        return builder;
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        // only the superclass has state
        AbstractQueryBuilderUtils.writeQueries(out, _features);
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

    @Override
    protected Query doToQuery(SearchExecutionContext context) throws IOException {
        List<PrebuiltFeature> features = new ArrayList<>(_features.size());
        for (QueryBuilder builder : _features) {
            features.add(new PrebuiltFeature(builder.queryName(), builder.toQuery(context)));
        }
        features = Collections.unmodifiableList(features);

        RankLibScriptEngine.RankLibModelContainer.Factory factory = context.compile(
                _rankLibScript,
                RankLibScriptEngine.CONTEXT);
        RankLibScriptEngine.RankLibModelContainer executableScript = factory.newInstance();
        LtrRanker ranker = (LtrRanker) executableScript.run();

        PrebuiltFeatureSet featureSet = new PrebuiltFeatureSet(queryName(), features);
        PrebuiltLtrModel model = new PrebuiltLtrModel(ranker.name(), ranker, featureSet);
        return RankerQuery.build(model);
    }

    @Override
    public QueryBuilder doRewrite(QueryRewriteContext ctx) throws IOException {
        if (_features == null || _rankLibScript == null || _features.isEmpty()) {
            return new MatchAllQueryBuilder();
        }

        List<QueryBuilder> newFeatures = null;
        boolean changed = false;

        int i = 0;
        for (QueryBuilder qb : _features) {
            QueryBuilder newQuery = Rewriteable.rewrite(qb, ctx);
            changed |= newQuery != qb;
            if (changed) {
                if (newFeatures == null) {
                    newFeatures = new ArrayList<>(_features.size());
                    newFeatures.addAll(_features.subList(0, i));
                }
                newFeatures.add(newQuery);
            }
            i++;
        }
        if (changed) {
            assert newFeatures.size() == _features.size();
            return new LtrQueryBuilder(_rankLibScript, newFeatures);
        }
        return this;
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

    public List<QueryBuilder> features() {
        return _features;
    }

    public final LtrQueryBuilder features(List<QueryBuilder> features) {
        _features = features;
        return this;
    }
}
