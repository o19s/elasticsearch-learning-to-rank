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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LtrQueryBuilder extends AbstractQueryBuilder<LtrQueryBuilder> {
    public static final String NAME = "ltr";

    Ranker _rankLibModel;
    List<QueryBuilder> _features;
    String initialModel = null;

    public LtrQueryBuilder() {
    }

    public LtrQueryBuilder(StreamInput in) throws IOException {
        super(in);
        RankerFactory rf = new RankerFactory();

        _features = new ArrayList<QueryBuilder>();
        _features.addAll(readQueries(in));
        _rankLibModel = rf.loadRankerFromString(in.readString());

    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        // only the superclass has state
        writeQueries(out, _features);
        out.writeString(_rankLibModel.model());
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME).endObject();
    }


    public static LtrQueryBuilder fromXContent(QueryParseContext parseContext, RankerFactory rankerFactory) throws IOException {
        XContentParser parser = parseContext.parser();

        Ranker ranker = null;
        final List<QueryBuilder> features = new ArrayList<>();

        String queryName = null;


        float boost = AbstractQueryBuilder.DEFAULT_BOOST;
        String currentFieldName = null;
        XContentParser.Token token = parseContext.parser().nextToken();
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.VALUE_STRING) {
                if (parser.currentName() == "model") {
                    ranker = rankerFactory.loadRankerFromString(parser.text());
                }
            }
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            }
            else if (token == XContentParser.Token.START_ARRAY) {
                while ((token = parser.nextToken()) != XContentParser.Token.END_ARRAY) {
                    switch (currentFieldName) {
                        case "features": {
                            features.add(parseContext.parseInnerQueryBuilder().get());
                        }
                    }
                }
            }
        }

        if (ranker == null) {
            throw new ParsingException(parser.getTokenLocation(),
                    "[ltr] query requires a model, none specified");
        }
        assert token == XContentParser.Token.END_OBJECT;
        LtrQueryBuilder rVal = new LtrQueryBuilder();
        rVal.queryName(queryName).features(features).ranker(ranker);
        return rVal;
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        if (_features == null || _rankLibModel == null) {
            return new MatchAllDocsQuery();
        }
        List<Query> asLQueries = new ArrayList<Query>();
        for (QueryBuilder query : _features) {
            asLQueries.add(query.toQuery(context));
        }
        return new LtrQuery(asLQueries, _rankLibModel);
    }

    @Override
    protected int doHashCode() {
        return 0;
    }

    @Override
    protected boolean doEquals(LtrQueryBuilder other) {
        return true;
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    public final Ranker ranker() {
        return _rankLibModel;
    }
    public final LtrQueryBuilder ranker(Ranker rankLibModel) {
         _rankLibModel = rankLibModel;
         return this;
    }

    public List<QueryBuilder> features() {return _features;}
    public final LtrQueryBuilder features(List<QueryBuilder> features) {
        _features = features;
        return this;
    }


}