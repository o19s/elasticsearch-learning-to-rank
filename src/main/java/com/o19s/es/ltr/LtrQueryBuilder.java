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

package com.o19s.es.ltr;

import ciir.umass.edu.learning.Ranker;
import ciir.umass.edu.learning.RankerFactory;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryShardContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LtrQueryBuilder extends AbstractQueryBuilder<LtrQueryBuilder> {
    public static final String NAME = "ltr";

    private Ranker _rankLibModel;

    public LtrQueryBuilder(Ranker rankLibModel) {
        _rankLibModel = rankLibModel;
    }

    public LtrQueryBuilder() {
    }

    public LtrQueryBuilder(StreamInput in) throws IOException {
        super(in);
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        // only the superclass has state
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME).endObject();
    }

    public static LtrQueryBuilder fromXContent(QueryParseContext parseContext) throws IOException {
        XContentParser parser = parseContext.parser();

        RankerFactory rankerFactory = new RankerFactory();
        Ranker ranker = null;
        final List<Optional<QueryBuilder>> features = new ArrayList<>();


        String queryName = null;


        float boost = AbstractQueryBuilder.DEFAULT_BOOST;
        String currentFieldName = null;
        XContentParser.Token token = parseContext.parser().nextToken();
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            }
            else if (token == XContentParser.Token.START_ARRAY) {
                while ((token = parser.nextToken()) != XContentParser.Token.END_ARRAY) {
                    switch (currentFieldName) {
                        case "features": {
                            features.add(parseContext.parseInnerQueryBuilder());
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
        rVal.queryName(queryName);
        return rVal;
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        //return new LtrQuery();
        return null;
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
}