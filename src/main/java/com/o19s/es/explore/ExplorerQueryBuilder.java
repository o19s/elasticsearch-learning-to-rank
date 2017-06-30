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

import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.NamedWriteable;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryShardContext;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public class ExplorerQueryBuilder extends AbstractQueryBuilder<ExplorerQueryBuilder> implements NamedWriteable{
    public static final String NAME = "match_explorer";

    private static final ParseField QUERY_NAME = new ParseField("query");
    private static final ParseField TYPE_NAME = new ParseField("type");

    private QueryBuilder query;
    private String type;

    private static final ObjectParser<ExplorerQueryBuilder, QueryParseContext> PARSER;

    static {
        PARSER = new ObjectParser<>(NAME, ExplorerQueryBuilder::new);
        PARSER.declareObject(
                ExplorerQueryBuilder::query,
                (parser, context) -> context.parseInnerQueryBuilder().get(),
                QUERY_NAME
        );
        PARSER.declareString(ExplorerQueryBuilder::statsType, TYPE_NAME);
        declareStandardFields(PARSER);
    }


    public ExplorerQueryBuilder() {
    }

    public ExplorerQueryBuilder(StreamInput in) throws IOException {
        super(in);
        query = in.readNamedWriteable(QueryBuilder.class);
        type = in.readString();
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeNamedWriteable(query);
        out.writeString(type);
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        printBoostAndQueryName(builder);
        builder.field(QUERY_NAME.getPreferredName(), query);
        builder.field(TYPE_NAME.getPreferredName(), type);
        builder.endObject();
    }

    public static Optional<ExplorerQueryBuilder> fromXContent(QueryParseContext context) throws IOException {
        XContentParser parser = context.parser();
        final ExplorerQueryBuilder builder;

        try {
            builder = PARSER.parse(context.parser(), context);
        } catch (IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage(), iae);
        }

        if (builder.query == null) {
            throw new ParsingException(parser.getTokenLocation(), "Field [" + QUERY_NAME + "] is mandatory.");
        }
        if (builder.statsType() == null) {
            throw new ParsingException(parser.getTokenLocation(), "Field [" + TYPE_NAME + "] is mandatory.");
        }
        return Optional.of(builder);
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        return new ExplorerQuery(query.toQuery(context), type);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(query, type);
    }

    @Override
    protected boolean doEquals(ExplorerQueryBuilder other) {
        return Objects.equals(query, other.query)
                && Objects.equals(type, other.type);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    public QueryBuilder query() {
        return query;
    }
    public ExplorerQueryBuilder query(QueryBuilder query) {
        this.query = query;
        return this;
    }

    public String statsType() {
        return type;
    }
    public ExplorerQueryBuilder statsType(String type) {
        this.type = type;
        return this;
    }
}
