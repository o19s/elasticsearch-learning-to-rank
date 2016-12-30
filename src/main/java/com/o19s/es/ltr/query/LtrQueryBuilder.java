/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.o19s.es.ltr.query;

import ciir.umass.edu.learning.Ranker;
import ciir.umass.edu.learning.RankerFactory;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParseFieldMatcher;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class LtrQueryBuilder extends AbstractQueryBuilder<LtrQueryBuilder> {
    public static final String NAME = "ltr";

    Script _rankLibScript;
    List<QueryBuilder> _features;
    String initialModel = null;

    public LtrQueryBuilder() {
    }

    public LtrQueryBuilder(StreamInput in) throws IOException {
        super(in);
        RankerFactory rf = new RankerFactory();

        _features = new ArrayList<QueryBuilder>();
        _features.addAll(readQueries(in));
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

    public static LtrQueryBuilder fromXContent(QueryParseContext parseContext) throws IOException {
        XContentParser parser = parseContext.parser();

        Script rankLibScript = null;

        final List<QueryBuilder> features = new ArrayList<>();

        String queryName = null;
        float boost = AbstractQueryBuilder.DEFAULT_BOOST;


        String currentFieldName = null;
        XContentParser.Token token = null;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
                if (!currentFieldName.equals("model") && !currentFieldName.equals("features") && !currentFieldName.equals("_name")
                        && !currentFieldName.equals("boost")) {
                    throw new ParsingException(parser.getTokenLocation(),
                            "[ltr] does not regocnize parameter: " + currentFieldName);
                }
            }
            else if (token == XContentParser.Token.START_OBJECT) {
                if (currentFieldName.equals("model")) {
                    rankLibScript = Script.parse(parser, parseContext.getParseFieldMatcher(), "ranklib");
                }
            }
            else if (token == XContentParser.Token.START_ARRAY) {
                while ((token = parser.nextToken()) != XContentParser.Token.END_ARRAY) {
                    switch (currentFieldName) {
                        case "features": {
                            features.add(parseContext.parseInnerQueryBuilder().get());
                        }
                    }
                }
            } else if (token.isValue()) {
                if (parseContext.getParseFieldMatcher().match(currentFieldName, AbstractQueryBuilder.NAME_FIELD)) {
                    queryName = parser.text();
                } else if (parseContext.getParseFieldMatcher().match(currentFieldName, AbstractQueryBuilder.BOOST_FIELD)) {
                    boost = parser.floatValue();
                } else {
                    throw new ParsingException(parser.getTokenLocation(), "[ltr] query does not support [" + currentFieldName + "]");
                }
            }
        }

        if (rankLibScript == null) {
            throw new ParsingException(parser.getTokenLocation(),
                    "[ltr] query requires a model, none specified");
        }
        assert token == XContentParser.Token.END_OBJECT;
        LtrQueryBuilder rVal = new LtrQueryBuilder();
        rVal.queryName(queryName).features(features).rankerScript(rankLibScript).boost(boost);
        return rVal;
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        if (_features == null || _rankLibScript == null) {
            return new MatchAllDocsQuery();
        }
        List<Query> asLQueries = new ArrayList<Query>();
        for (QueryBuilder query : _features) {
            asLQueries.add(query.toQuery(context));
        }
        // pull model out of script
        RankLibScriptEngine.RankLibExecutableScript rankerScript =
                (RankLibScriptEngine.RankLibExecutableScript)context.getExecutableScript(_rankLibScript, ScriptContext.Standard.SEARCH);

        return new LtrQuery(asLQueries, (Ranker)rankerScript.run());
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