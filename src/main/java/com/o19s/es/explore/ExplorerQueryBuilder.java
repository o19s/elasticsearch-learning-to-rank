/*
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

package com.o19s.es.explore;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryShardContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ExplorerQueryBuilder extends AbstractQueryBuilder<ExplorerQueryBuilder> {
    public static final String NAME = "match_explorer";

    private static final ParseField QUERY_FIELD = new ParseField("query");
    private static final ParseField FIELD_FIELD = new ParseField("field");
    private static final ParseField TYPE_FIELD = new ParseField("type");

    QueryBuilder query;
    String field;
    String type;

    public ExplorerQueryBuilder() {
    }

    public ExplorerQueryBuilder(StreamInput in) throws IOException {
        super(in);
        query = in.readNamedWriteable(QueryBuilder.class);
        field = in.readString();
        type = in.readString();
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeNamedWriteable(query);
        out.writeString(field);
        out.writeString(type);
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);

        printBoostAndQueryName(builder);

        query.toXContent(builder, params);

        builder.field(FIELD_FIELD.getPreferredName(), field);
        builder.field(TYPE_FIELD.getPreferredName(), type);

        builder.endObject();
    }

    public static Optional<ExplorerQueryBuilder> fromXContent(QueryParseContext parseContext) throws IOException {
        XContentParser parser = parseContext.parser();

        final List<QueryBuilder> queries = new ArrayList<>();

        String field = "";
        String type = "";

        String currentFieldName = null;
        XContentParser.Token token;

        while((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if(token == XContentParser.Token.START_OBJECT) {
                if(QUERY_FIELD.match(currentFieldName)) {
                    parseContext.parseInnerQueryBuilder().ifPresent(queries::add);
                }
            } else {
                if(FIELD_FIELD.match(currentFieldName)) {
                    field = parser.text();
                } else if(TYPE_FIELD.match(currentFieldName)) {
                    type = parser.text();
                }
            }
        }

        ExplorerQueryBuilder builder = new ExplorerQueryBuilder();
        builder.query = queries.get(0);
        builder.field = field;
        builder.type = type;

        return Optional.of(builder);
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        if (query == null) {
            return new MatchAllDocsQuery();
        }

        return new ExplorerQuery(query.toQuery(context), field, type);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(query, field, type);
    }

    @Override
    protected boolean doEquals(ExplorerQueryBuilder other) {
        return Objects.equals(query, other.query)
                && Objects.equals(field, other.field)
                && Objects.equals(type, other.type);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }


    public QueryBuilder query() {
        return query;
    }

    public String field() {
        return field;
    }

    public String statsType() {
        return type;
    }
}
